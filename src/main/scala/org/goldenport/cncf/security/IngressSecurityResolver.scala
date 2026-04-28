package org.goldenport.cncf.security

import cats.free.Free
import cats.~>
import java.time.ZoneId
import java.util.Locale
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.CommandExecutionMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, GlobalRuntimeContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SpanId, TraceId}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.event.EventReception
import org.goldenport.cncf.job.{ActionId, JobContext, JobId, TaskId}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.protocol.Request

/*
 * Common ingress security resolver for external entry points.
 *
 * - Operation request ingress
 * - Reception ingress
 *
 * @since   Mar. 20, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResolvedIngressSecurity(
  executionContext: ExecutionContext,
  privilege: SecurityContext.Privilege,
  requestedCapabilities: Set[String]
)

trait IngressSecurityResolver {
  def resolve(request: Request): Consequence[ResolvedIngressSecurity]
  def resolve(base: ExecutionContext, request: Request): Consequence[ResolvedIngressSecurity]
  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity]
  def resolve(base: ExecutionContext, attributes: Map[String, String]): Consequence[ResolvedIngressSecurity]
}

object IngressSecurityResolver {
  val default: IngressSecurityResolver = new DefaultIngressSecurityResolver

  def resolve(request: Request): Consequence[ResolvedIngressSecurity] =
    default.resolve(request)

  def resolve(base: ExecutionContext, request: Request): Consequence[ResolvedIngressSecurity] =
    default.resolve(base, request)

  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] =
    default.resolve(attributes)

  def resolve(base: ExecutionContext, attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] =
    default.resolve(base, attributes)
}

private final class DefaultIngressSecurityResolver extends IngressSecurityResolver {
  private val _privilege_keys = Vector(
    "cncf.security.privilege",
    "security.privilege",
    "privilege",
    EventReception.StandardAttribute.SecurityLevel
  )

  private val _capability_keys = Vector(
    "cncf.security.capabilities",
    "cncf.security.capability",
    "security.capabilities",
    "security.capability",
    "capabilities",
    "capability"
  )

  def resolve(request: Request): Consequence[ResolvedIngressSecurity] = {
    val fromProperties = request.properties.foldLeft(Map.empty[String, String]) { (z, p) =>
      val value = Option(p.value).map(_.toString).getOrElse("")
      if (p.name.nonEmpty && value.nonEmpty) z.updated(p.name, value) else z
    }
    val attrs = request.arguments.foldLeft(fromProperties) { (z, a) =>
      val value = Option(a.value).map(_.toString).getOrElse("")
      if (a.name.nonEmpty && value.nonEmpty) z.updated(a.name, value) else z
    }
    resolve(attrs)
  }

  def resolve(base: ExecutionContext, request: Request): Consequence[ResolvedIngressSecurity] = {
    val fromProperties = request.properties.foldLeft(Map.empty[String, String]) { (z, p) =>
      val value = Option(p.value).map(_.toString).getOrElse("")
      if (p.name.nonEmpty && value.nonEmpty) z.updated(p.name, value) else z
    }
    val attrs = request.arguments.foldLeft(fromProperties) { (z, a) =>
      val value = Option(a.value).map(_.toString).getOrElse("")
      if (a.name.nonEmpty && value.nonEmpty) z.updated(a.name, value) else z
    }
    resolve(base, attrs)
  }

  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] = {
    val privilege = _resolve_privilege(attributes)
    val caps = _resolve_requested_capabilities(attributes)
    privilege.flatMap { p =>
      val ctx0 = ExecutionContext.withSecurityContext(ExecutionContext.create(p), _security_context(p, attributes))
      val ctx1 = _production_runtime_context(ctx0)
      val ctx2 = _restore_formatting_context(ctx0.security, ctx1)
      val ctx = _bind_context(attributes, ctx2)
      if (caps.isEmpty || ctx.security.hasAnyCapability(caps))
        Consequence.success(ResolvedIngressSecurity(ctx, p, caps))
      else
        Consequence.operationIllegal(
          "security.resolve",
          s"required capability: ${caps.toVector.sorted.mkString("|")}"
        )
    }
  }

  def resolve(base: ExecutionContext, attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] = {
    val caps = _resolve_requested_capabilities(attributes)
    val request = AuthenticationRequest(attributes)
    val resolvedProviders = _resolved_authentication_providers(base)
    val security0 =
      if (resolvedProviders.nonEmpty)
        _resolve_authenticated_security(resolvedProviders, base, request)
      else
        Consequence.success(None)
    security0.flatMap {
      case Some(security) =>
        Consequence.success(security)
      case None =>
        if (_fallback_privilege_enabled(base))
          _resolve_privilege(attributes).map(_security_context(_, attributes))
        else if (_has_authentication_material(request))
          Consequence.securityPermissionDenied[SecurityContext]("Privilege fallback is disabled by resolved security wiring.")
        else
          _resolve_privilege(attributes).map(_security_context(_, attributes))
    }.flatMap { security =>
      val privilege = _resolve_privilege_from_security(security)
      val ctx0 = ExecutionContext.withSecurityContext(base, security)
      val ctx1 = _production_runtime_context_from_base(ctx0)
      val ctx1a = _rebind_runtime_unit_of_work(ctx1, "ingress-security")
      val ctx2 = _restore_formatting_context(security, ctx1a)
      val ctx = _bind_context(attributes, ctx2)
      if (caps.isEmpty || ctx.security.hasAnyCapability(caps))
        Consequence.success(ResolvedIngressSecurity(ctx, privilege, caps))
      else
        Consequence.operationIllegal(
          "security.resolve",
          s"required capability: ${caps.toVector.sorted.mkString("|")}"
        )
    }
  }

  private def _bind_context(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): ExecutionContext = {
    val withobservability = _restore_observability(attributes, ctx).getOrElse(ctx)
    val withjob = _restore_job_context(attributes, withobservability).getOrElse(withobservability)
    val withmode = _restore_command_execution_mode(attributes, withjob).getOrElse(withjob)
    val withcalltree = _restore_debug_calltree_mode(attributes, withmode).getOrElse(withmode)
    val withtrace = _restore_debug_trace_job_mode(attributes, withcalltree).getOrElse(withcalltree)
    _restore_debug_save_calltree_mode(attributes, withtrace).getOrElse(withtrace)
  }

  private def _restore_command_execution_mode(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] =
    _find_first(
      attributes,
      Vector(
        RuntimeConfig.CommandExecutionModeKey,
        RuntimeConfig.RuntimeCommandExecutionModeKey,
        "cncf.command.execution-mode",
        "cncf.runtime.command.execution-mode"
      )
    ).flatMap(RuntimeConfig.parseCommandExecutionMode).map { mode =>
      ExecutionContext.withFrameworkCommandExecutionMode(ctx, mode)
    }

  private def _restore_debug_calltree_mode(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] =
    _find_first(
      attributes,
      Vector(
        RuntimeConfig.DebugCallTreeKey,
        RuntimeConfig.RuntimeDebugCallTreeKey,
        "cncf.debug.calltree",
        "cncf.runtime.debug.calltree",
        "x-textus-debug-calltree",
        "X-Textus-Debug-Calltree"
      )
    ).map(_is_truthy).map { enabled =>
      ExecutionContext.withFrameworkInlineCallTreeEnabled(ctx, enabled)
    }

  private def _restore_debug_trace_job_mode(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] =
    _find_first(
      attributes,
      Vector(
        RuntimeConfig.DebugTraceJobKey,
        RuntimeConfig.RuntimeDebugTraceJobKey,
        "cncf.debug.trace-job",
        "cncf.runtime.debug.trace-job",
        "x-textus-debug-trace-job",
        "X-Textus-Debug-Trace-Job"
      )
    ).map(_is_truthy).map { enabled =>
      ExecutionContext.withFrameworkTraceJobEnabled(ctx, enabled)
    }

  private def _restore_debug_save_calltree_mode(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] =
    _find_first(
      attributes,
      Vector(
        RuntimeConfig.DebugSaveCallTreeKey,
        RuntimeConfig.RuntimeDebugSaveCallTreeKey,
        "cncf.debug.save-calltree",
        "cncf.runtime.debug.save-calltree",
        "x-textus-debug-save-calltree",
        "X-Textus-Debug-Save-Calltree"
      )
    ).map(_is_truthy).map { enabled =>
      ExecutionContext.withFrameworkSaveCallTreeEnabled(ctx, enabled)
    }

  private def _restore_formatting_context(
    security: SecurityContext,
    ctx: ExecutionContext
  ): ExecutionContext = {
    val attrs = security.principal.attributes
    val base = ctx.runtime.context
    val formatting0 = base.formatting
    val formatting1 = _find_first(attrs, Vector("locale", "user.locale", "textus.locale"))
      .flatMap(_parse_locale)
      .map(formatting0.withLocale)
      .getOrElse(formatting0)
    val formatting2 = _find_first(attrs, Vector("timeZone", "timezone", "time_zone", "user.timeZone", "user.timezone"))
      .flatMap(_parse_timezone)
      .map(formatting1.withTimezone)
      .getOrElse(formatting1)
    if (formatting2 == formatting0)
      ctx
    else
      ExecutionContext.withRuntimeContextContext(
        ctx,
        base.copy(formatting = formatting2)
      )
  }

  private def _parse_locale(p: String): Option[Locale] = {
    val value = Option(p).map(_.trim).getOrElse("")
    if (value.isEmpty)
      None
    else
      Some(Locale.forLanguageTag(value.replace('_', '-')))
  }

  private def _parse_timezone(p: String): Option[ZoneId] =
    scala.util.Try(ZoneId.of(p.trim)).toOption

  private def _runtime_context_from_config(
    global: GlobalRuntimeContext
  ): RuntimeContext.Context = {
    val base = RuntimeContext.Context.default
    val formatting0 = base.formatting
    val formatting1 = _config_string(global, Vector("textus.locale", "cncf.locale"))
      .flatMap(_parse_locale)
      .map(formatting0.withLocale)
      .getOrElse(formatting0)
    val formatting2 = _config_string(global, Vector("textus.timeZone", "textus.timezone", "cncf.timeZone", "cncf.timezone"))
      .flatMap(_parse_timezone)
      .map(formatting1.withTimezone)
      .getOrElse(formatting1)
    base.copy(formatting = formatting2)
  }

  private def _config_string(
    global: GlobalRuntimeContext,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator
      .flatMap(key => RuntimeConfig.getString(global.resolvedConfiguration, key))
      .find(_.trim.nonEmpty)

  private def _is_truthy(p: String): Boolean = {
    val lower = Option(p).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
    lower == "true" || lower == "1" || lower == "yes" || lower == "on"
  }

  private def _restore_observability(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] = {
    val trace = _read_trace_id(attributes)
    val span = _read_span_id(attributes)
    val correlation = _read_correlation_id(attributes)
    trace.map { traceid =>
      val ob = ObservabilityContext(
        traceId = traceid,
        spanId = span,
        correlationId = correlation.orElse(ctx.observability.correlationId),
        sagaId = attributes.get("cncf.event.sagaId").filter(_.nonEmpty).orElse(ctx.observability.sagaId),
        callTreeContext = ctx.observability.callTreeContext
      )
      ExecutionContext.withObservabilityContext(ctx, ob)
    }
  }

  private def _restore_job_context(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] = {
    val jobid = _read_job_id(attributes)
    val taskid = _read_task_id(attributes)
    val actionid = _read_action_id(attributes)
    val parentjobid = _read_parent_job_id(attributes)
    val causationid = _read_causation_id(attributes)
    if (jobid.isEmpty && taskid.isEmpty && actionid.isEmpty && parentjobid.isEmpty && causationid.isEmpty)
      None
    else
      Some(
      ExecutionContext.withJobContext(
          ctx,
          // Design update (2026-03-21):
          // - Restorable from event attributes: jobId/taskId/actionId/parentJobId/causationId
          // - Inferable: currentTask (from taskId), minimal traceMetadata (trace/span/correlation)
          // - Not restorable at ingress: taskStack/full traceMetadata
          // Future consideration:
          // - If cross-subsystem continuity requires exact stack replay, add explicit taskStack envelope attributes.
          JobContext(
            jobId = jobid,
            taskId = taskid,
            actionId = actionid,
            parentJobId = parentjobid,
            currentTask = taskid,
            taskStack = Vector.empty,
            causationId = causationid,
            traceMetadata = _minimal_trace_metadata(ctx)
          )
        )
      )
  }

  private def _security_context(
    privilege: SecurityContext.Privilege,
    ingressAttributes: Map[String, String] = Map.empty
  ): SecurityContext =
    SecurityContext(
      principal = new Principal {
        val id: PrincipalId = _resolve_principal_id(ingressAttributes).getOrElse(privilege.principalId)
        val attributes: Map[String, String] = privilege.attributes ++ _security_subject_attributes(ingressAttributes)
      },
      capabilities = privilege.capabilities,
      level = privilege.level,
      subjectKind = privilege.subjectKind
    )

  private def _resolve_authenticated_security(
    providers: Vector[AuthenticationProvider],
    base: ExecutionContext,
    request: AuthenticationRequest
  ): Consequence[Option[SecurityContext]] = {
    val _ = providers
    AuthenticationProviderRuntime.authenticate(base, request).map(_.map(x => _provider_authenticated(x.toSecurityContext)))
  }

  private def _provider_authenticated(
    security: SecurityContext
  ): SecurityContext =
    security.copy(principal = new Principal {
      val id: PrincipalId = security.principal.id
      val attributes: Map[String, String] =
        security.principal.attributes ++ Map(
          SecuritySubject.AuthenticationProvenanceAttribute ->
            SecuritySubject.ProviderAuthenticationProvenance
        )
    })

  private def _resolved_authentication_providers(base: ExecutionContext): Vector[AuthenticationProvider] =
    AuthenticationProviderRuntime.providers(base)

  private def _fallback_privilege_enabled(base: ExecutionContext): Boolean =
    _subsystem_from_scope(base.cncfCore.scope)
      .map(_.resolvedSecurityWiring.authentication.fallbackPrivilegeEnabled)
      .getOrElse(true)

  private def _has_authentication_material(request: AuthenticationRequest): Boolean =
    request.accessToken.exists(_.trim.nonEmpty) ||
      request.refreshToken.exists(_.trim.nonEmpty)

  @annotation.tailrec
  private def _subsystem_from_scope(
    scope: ScopeContext
  ): Option[org.goldenport.cncf.subsystem.Subsystem] =
    scope match {
      case cc: Component.Context =>
        cc.component.subsystem
      case other =>
        other.parent match {
          case Some(parent) => _subsystem_from_scope(parent)
          case None => None
        }
    }

  private def _resolve_privilege_from_security(
    security: SecurityContext
  ): SecurityContext.Privilege =
    if (security.capabilities.exists { c =>
      val n = _normalize_token(c.name)
      n == _normalize_token("content_manager") || n == _normalize_token("content_admin")
    })
      SecurityContext.Privilege.ApplicationContentManager
    else
      SecurityContext.Privilege.User

  private def _production_runtime_context(
    ctx: ExecutionContext
  ): ExecutionContext =
    GlobalRuntimeContext.current match {
      case Some(global) =>
        lazy val context0: ExecutionContext = ExecutionContext.withRuntimeContext(ctx, runtime)
        lazy val context: ExecutionContext =
          global.commandExecutionMode.orElse(global.config.commandExecutionMode) match {
            case Some(mode) =>
              ExecutionContext.withFrameworkCommandExecutionMode(context0, mode)
            case None =>
              context0
          }
        lazy val runtime: RuntimeContext = new RuntimeContext(
          core = ScopeContext.Core(
            kind = ScopeKind.Runtime,
            name = "ingress-security",
            parent = Some(global),
            observabilityContext = global.core.observabilityContext,
            httpDriverOption = Some(global.config.httpDriver),
            datastore = Some(DataStoreContext(global.config.dataStoreSpace)),
            entitystore = Some(EntityStoreContext(global.config.entityStoreSpace))
          ),
          unitOfWorkSupplier = () => new UnitOfWork(context),
          unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
            def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
              new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(fa)
          },
          commitAction = _ => (),
          abortAction = _ => (),
          disposeAction = _ => (),
          token = "ingress-security",
          context = _runtime_context_from_config(global),
          operationMode = global.config.operationMode
        )
        context
      case None =>
        ctx
    }

  private def _production_runtime_context_from_base(
    ctx: ExecutionContext
  ): ExecutionContext =
    _global_runtime_context(ctx.cncfCore.scope) match {
      case Some(global) =>
        val context = _runtime_context_from_config(global)
        val ctx0 =
          if (ctx.runtime.context == context)
            ctx
          else
            ExecutionContext.withRuntimeContextContext(ctx, context)
        global.commandExecutionMode.orElse(global.config.commandExecutionMode) match {
          case Some(mode) =>
            ExecutionContext.withFrameworkCommandExecutionMode(ctx0, mode)
          case None =>
            ctx0
        }
      case None =>
        ctx
    }

  private def _rebind_runtime_unit_of_work(
    ctx: ExecutionContext,
    token: String
  ): ExecutionContext = {
    lazy val context: ExecutionContext =
      ExecutionContext.withRuntimeContext(ctx, runtime)
    lazy val runtime: RuntimeContext =
      ctx.runtime.withUnitOfWorkContext(context, token)
    context
  }

  @annotation.tailrec
  private def _global_runtime_context(
    scope: ScopeContext
  ): Option[GlobalRuntimeContext] =
    scope match {
      case m: GlobalRuntimeContext =>
        Some(m)
      case other =>
        other.parent match {
          case Some(parent) => _global_runtime_context(parent)
          case None => None
        }
    }

  private def _minimal_trace_metadata(
    ctx: ExecutionContext
  ): Map[String, String] = {
    val ob = ctx.observability
    val pairs = Vector(
      Some("traceId" -> ob.traceId.print),
      ob.spanId.map(x => "spanId" -> x.print),
      ob.correlationId.map(x => "correlationId" -> x.print)
    )
    pairs.collect { case Some((k, v)) if k.nonEmpty && v.nonEmpty => k -> v }.toMap
  }

  private def _resolve_privilege(
    attributes: Map[String, String]
  ): Consequence[SecurityContext.Privilege] = {
    val value = _find_first(attributes, _privilege_keys).map(_normalize_token)
      value match {
      case None =>
        Consequence.success(SecurityContext.Privilege.Anonymous)
      case Some("anonymous") =>
        Consequence.success(SecurityContext.Privilege.Anonymous)
      case Some("user") =>
        Consequence.success(SecurityContext.Privilege.User)
      case Some("applicationcontentmanager") | Some("contentmanager") | Some("contentadmin") =>
        Consequence.success(SecurityContext.Privilege.ApplicationContentManager)
      case Some("operator") =>
        Consequence.success(SecurityContext.Privilege.Operator)
      case Some("system") | Some("systemoperator") | Some("systemadmin") =>
        Consequence.success(SecurityContext.Privilege.System)
      case Some("internal") | Some("subsystem") | Some("service") | Some("component") =>
        Consequence.success(SecurityContext.Privilege.Internal)
      case Some(other) =>
        Consequence.operationInvalid(
          "security.resolve",
          s"invalid privilege: $other"
        )
    }
  }

  private def _resolve_requested_capabilities(
    attributes: Map[String, String]
  ): Set[String] =
    _capability_keys
      .flatMap(attributes.get)
      .flatMap(_split_tokens)
      .map(_normalize_token)
      .filter(_.nonEmpty)
      .toSet

  private def _resolve_principal_id(
    attributes: Map[String, String]
  ): Option[PrincipalId] =
    _find_first(attributes, Vector(
      "cncf.security.principal_id",
      "cncf.security.principalId",
      "security.principal_id",
      "security.principalId",
      "principal_id",
      "principalId",
      "subject.id",
      "principal.id"
    )).map(PrincipalId(_))

  private def _security_subject_attributes(
    attributes: Map[String, String]
  ): Map[String, String] =
    attributes.filter { case (k, _) =>
      k.startsWith("subject.") ||
        k.startsWith("principal.") ||
        k == "subject_id" ||
        k == "subjectId" ||
        k == "principal_id" ||
        k == "principalId" ||
        k == "role" ||
        k == "roles" ||
        k == "scope" ||
        k == "scopes" ||
        k == "privilege" ||
        k == "privileges" ||
        k == "capability" ||
        k == "capabilities"
    }

  private def _find_first(
    attributes: Map[String, String],
    keys: Vector[String]
  ): Option[String] = {
    val normalized = attributes.map { case (k, v) => _normalize_token(k) -> v }
    keys.iterator.flatMap { key =>
      normalized.get(_normalize_token(key))
    }.find(_.trim.nonEmpty)
  }

  private def _split_tokens(p: String): Vector[String] =
    p.split("[,|\\s]+")
      .toVector

  private def _read_trace_id(
    attributes: Map[String, String]
  ): Option[TraceId] =
    _read_universal_id(attributes, Vector(
      EventReception.StandardAttribute.TraceId,
      EventReception.StandardAttribute.LegacyTraceId
    ), "trace").map(parts => TraceId(parts.major, parts.minor))

  private def _read_span_id(
    attributes: Map[String, String]
  ): Option[SpanId] =
    _read_universal_id(attributes, Vector(
      EventReception.StandardAttribute.SpanId
    ), "span").flatMap { parts =>
      parts.subkind.map(sk => SpanId(parts.major, parts.minor, sk))
    }

  private def _read_correlation_id(
    attributes: Map[String, String]
  ): Option[CorrelationId] =
    _read_universal_id(attributes, Vector(
      EventReception.StandardAttribute.CorrelationId,
      EventReception.StandardAttribute.LegacyCorrelationId
    ), "correlation").map(parts => CorrelationId(parts.major, parts.minor))

  private def _read_job_id(
    attributes: Map[String, String]
  ): Option[JobId] =
    _find_first(attributes, Vector(
      EventReception.StandardAttribute.JobId,
      EventReception.StandardAttribute.LegacyJobId
    )).flatMap(v => JobId.parse(v).toOption)

  private def _read_task_id(
    attributes: Map[String, String]
  ): Option[TaskId] =
    _find_first(attributes, Vector(
      EventReception.StandardAttribute.TaskId,
      EventReception.StandardAttribute.LegacyTaskId
    )).flatMap(v => TaskId.parse(v).toOption)

  private def _read_action_id(
    attributes: Map[String, String]
  ): Option[ActionId] =
    _find_first(attributes, Vector(
      EventReception.StandardAttribute.ActionId,
      EventReception.StandardAttribute.LegacyActionId
    )).flatMap(v => ActionId.parse(v).toOption)

  private def _read_parent_job_id(
    attributes: Map[String, String]
  ): Option[JobId] =
    _find_first(attributes, Vector(
      EventReception.StandardAttribute.ParentJobId,
      EventReception.StandardAttribute.LegacyParentJobId
    )).flatMap(v => JobId.parse(v).toOption)

  private def _read_causation_id(
    attributes: Map[String, String]
  ): Option[String] =
    _find_first(attributes, Vector(
      EventReception.StandardAttribute.CausationId,
      EventReception.StandardAttribute.LegacyCausationId
    ))

  private def _read_universal_id(
    attributes: Map[String, String],
    keys: Vector[String],
    expectedKind: String
  ): Option[org.goldenport.id.UniversalId.Parts] =
    _find_first(attributes, keys).flatMap(v =>
      org.goldenport.id.UniversalId.parseParts(v, expectedKind).toOption
    )

  private def _normalize_token(p: String): String =
    p.trim.toLowerCase.replace("_", "").replace("-", "")
}
