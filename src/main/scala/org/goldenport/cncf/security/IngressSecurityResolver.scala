package org.goldenport.cncf.security

import cats.free.Free
import cats.~>
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.CommandExecutionMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, GlobalRuntimeContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SpanId, TraceId}
import org.goldenport.cncf.event.EventReception
import org.goldenport.cncf.job.{ActionId, JobContext, JobId, TaskId}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.protocol.Request

/*
 * Common ingress security resolver for external entry points.
 *
 * - Operation request ingress
 * - Reception ingress
 *
 * @since   Mar. 20, 2026
 * @version Mar. 30, 2026
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
      val ctx0 = ExecutionContext.create(p)
      val ctx1 = _production_runtime_context(ctx0)
      val ctx = _bind_context(attributes, ctx1)
      if (caps.isEmpty || ctx.security.hasAnyCapability(caps))
        Consequence.success(ResolvedIngressSecurity(ctx, p, caps))
      else
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal),
          Facet.Operation("security.resolve"),
          Facet.Message(s"required capability: ${caps.toVector.sorted.mkString("|")}")
        )
    }
  }

  def resolve(base: ExecutionContext, attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] = {
    val privilege = _resolve_privilege(attributes)
    val caps = _resolve_requested_capabilities(attributes)
    privilege.flatMap { p =>
      val ctx0 = ExecutionContext.withSecurityContext(base, _security_context(p))
      val ctx = _bind_context(attributes, ctx0)
      if (caps.isEmpty || ctx.security.hasAnyCapability(caps))
        Consequence.success(ResolvedIngressSecurity(ctx, p, caps))
      else
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal),
          Facet.Operation("security.resolve"),
          Facet.Message(s"required capability: ${caps.toVector.sorted.mkString("|")}")
        )
    }
  }

  private def _bind_context(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): ExecutionContext = {
    val withobservability = _restore_observability(attributes, ctx).getOrElse(ctx)
    val withjob = _restore_job_context(attributes, withobservability).getOrElse(withobservability)
    _restore_command_execution_mode(attributes, withjob).getOrElse(withjob)
  }

  private def _restore_command_execution_mode(
    attributes: Map[String, String],
    ctx: ExecutionContext
  ): Option[ExecutionContext] =
    _find_first(
      attributes,
      Vector(
        "textus.runtime.command.execution-mode",
        "cncf.runtime.command.execution-mode"
      )
    ).flatMap(RuntimeConfig.parseCommandExecutionMode).map { mode =>
      ExecutionContext.withFrameworkCommandExecutionMode(ctx, mode)
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
    privilege: SecurityContext.Privilege
  ): SecurityContext =
    SecurityContext(
      principal = new Principal {
        val id: PrincipalId = privilege.principalId
        val attributes: Map[String, String] = privilege.attributes
      },
      capabilities = privilege.capabilities,
      level = privilege.level
    )

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
            httpDriverOption = Some(global.config.httpDriver)
          ),
          unitOfWorkSupplier = () => new UnitOfWork(context),
          unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
            def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
              new UnitOfWorkInterpreter(new UnitOfWork(context)).run(
                ConsequenceT.liftF(Free.liftF(fa))
              )
          },
          commitAction = _ => (),
          abortAction = _ => (),
          disposeAction = _ => (),
          token = "ingress-security"
        )
        context
      case None =>
        ctx
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
        Consequence.success(SecurityContext.Privilege.User)
      case Some("user") =>
        Consequence.success(SecurityContext.Privilege.User)
      case Some("applicationcontentmanager") | Some("contentmanager") | Some("contentadmin") =>
        Consequence.success(SecurityContext.Privilege.ApplicationContentManager)
      case Some(other) =>
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Invalid),
          Facet.Operation("security.resolve"),
          Facet.Message(s"invalid privilege: $other")
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
