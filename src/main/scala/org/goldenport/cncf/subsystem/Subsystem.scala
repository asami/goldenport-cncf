package org.goldenport.cncf.subsystem

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInstanceId,
  ComponentSpace
}
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.component.ComponentLocator.NameLocator
import org.goldenport.cncf.component.builtin.debug.DebugComponent
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventBus, EventEngine, EventReception, EventStore}
import org.goldenport.cncf.workflow.WorkflowEngine
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.protocol.{Property, Request, Response}

import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.{AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.cncf.security.{IngressSecurityResolver, OperationAuthorization, OperationAuthorizationProvider}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  4, 2026
 *  version Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class Subsystem(
  val name: String,
  val version: Option[String] = None,
  scopeContext: Option[ScopeContext] = None, // TODO
  httpdriver: Option[HttpDriver] = None,
  val configuration: ResolvedConfiguration,
  val aliasResolver: AliasResolver = GlobalRuntimeContext.current.map(_.aliasResolver).getOrElse(AliasResolver.empty),
  runMode: RunMode = GlobalRuntimeContext.current.map(_.runtimeMode).getOrElse(RunMode.Server)
) {
  private var _component_factory: ComponentFactory = ComponentFactory()
  private var _component_space: ComponentSpace = ComponentSpace()
  private var _resolver: OperationResolver = OperationResolver.empty
  private val _http_driver: Option[HttpDriver] = httpdriver
  private val _job_engine: JobEngine = InMemoryJobEngine.create()
  private val _event_store: EventStore = EventStore.inMemory
  private lazy val _event_bus: EventBus =
    EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = _event_store))
  private lazy val _workflow_engine: WorkflowEngine = WorkflowEngine.inMemory(this)
  private val _event_receptions = mutable.LinkedHashMap.empty[String, EventReception]
  private val _entity_access_metrics: EntityAccessMetricsRegistry = EntityAccessMetricsRegistry.shared
  private var _descriptor: Option[GenericSubsystemDescriptor] = None
  private var _resolved_security_wiring: ResolvedSecurityWiring = ResolvedSecurityWiring.empty

  def globalRuntimeContext: GlobalRuntimeContext = {
    val a = _find_global_runtime_context(scopeContext)
    a orElse GlobalRuntimeContext.current getOrElse {
      Consequence.RAISE.UnreachableReached
    }
  }

  @annotation.tailrec
  private def _find_global_runtime_context(p: Option[ScopeContext]): Option[GlobalRuntimeContext] =
    p match {
      case Some(s) => s match {
        case m: GlobalRuntimeContext => Some(m)
        case m => _find_global_runtime_context(m.parent)
      }
      case None => None
    }

  def httpDriver: Option[HttpDriver] = _http_driver
  def jobEngine: JobEngine = _job_engine
  def eventStore: EventStore = _event_store
  def eventBus: EventBus = _event_bus
  def workflowEngine: WorkflowEngine = _workflow_engine
  def eventReceptions: Map[String, EventReception] = _event_receptions.toMap
  def entityAccessMetrics: EntityAccessMetricsRegistry = _entity_access_metrics
  def serverEmulatorBaseUrl: String = globalRuntimeContext.serverEmulatorBaseUrl
  def descriptor: Option[GenericSubsystemDescriptor] = _descriptor
  def resolvedSecurityWiring: ResolvedSecurityWiring = _resolved_security_wiring

  def withDescriptor(descriptor: GenericSubsystemDescriptor): Subsystem = {
    _descriptor = Some(descriptor)
    _resolved_security_wiring = ResolvedSecurityWiring.resolve(_descriptor, components)
    this
  }

  def setup(cf: ComponentFactory): Subsystem = {
    _component_factory = cf
    val comps = cf.discover()
    add(comps)
  }

  def add(comps: Seq[Component]): Subsystem = {
    val bootstrapped = comps.map(_component_factory.bootstrap)
    val injected = bootstrapped.map(x => _inject_context(x.name, x))
    injected.foreach(_bind_runtime_services)
    _component_space = _component_space.add(injected)
    _rebuildResolver()
    this
  }

  def add(bundle: Component.Bundle): Subsystem =
    add(bundle.participants)

  def add(component: Component): Subsystem =
    add(Vector(component))

  def registerEventReception(componentName: String, reception: EventReception): Subsystem = {
    _event_receptions.update(componentName, reception)
    this
  }

  // def addComponent(name: String, comp: Component): Subsystem = {
  //   val c = _inject_context(name, comp)
  //   _component_space = _component_space.add(c)
  //   this
  // }

  // TODO SubsystemContext extends ScopeContext
  private val _subsystem_scope_context: ScopeContext =
    scopeContext.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = name,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )
    }

  private def _inject_context(name: String, comp: Component): Component = {
    val sc = Component.Context(
      name = name,
      parent = _subsystem_scope_context,
      comp,
      componentOrigin = comp.origin
    )
    comp.withScopeContext(sc)
  }

  private def _bind_runtime_services(component: Component): Unit = {
    val _ = component.withEventStore(_event_store)
    component.jobEngine match {
      case m: InMemoryJobEngine =>
        m.withEventStore(_event_store)
      case _ =>
        ()
    }
    component.eventReception.foreach(registerEventReception(component.name, _))
  }

  // private val _components: Map[String, Component] =
  //   components.map { case (componentname, component) =>
  //     val sc = _subsystem_scope_context.createChildScope(
  //       ScopeKind.Component,
  //       componentname
  //     )
  //     component.withScopeContext(sc)
  //     componentname -> component
  //   }

  // private val _component_ids: Map[String, ComponentId] =
  //   _components.keys.map { name =>
  //     name -> _component_id(name)
  //   }.toMap

  // private val _component_instance_ids: Map[String, ComponentInstanceId] =
  //   _component_ids.map { case (name, id) =>
  //     name -> _component_instance_id(id)
  //   }

  // private val _component_space: ComponentSpace =
  //   new ComponentSpace(
  //     _component_instance_ids.map { case (name, id) =>
  //       id -> _components(name)
  //     },
  //     _component_ids.map { case (name, id) =>
  //       id -> _component_instance_ids(name)
  //     }
  //   )

  def components: Vector[Component] = _component_space.components

  def findComponent(name: String): Option[Component] =
    _component_space.find(NameLocator(name))

  def resolver: OperationResolver = _resolver

  def operationResolver: OperationResolver = _resolver

  def configurationValue(key: String): Option[org.goldenport.configuration.ConfigurationValue] =
    configuration.configuration.values.get(key)

  def configurationOrEmpty: org.goldenport.configuration.Configuration =
    configuration.configuration

  private def _rebuildResolver(): Unit =
    _resolver = OperationResolver.build(_component_space.components)
    _resolved_security_wiring = ResolvedSecurityWiring.resolve(_descriptor, _component_space.components)

  def executeHttp(req: HttpRequest): HttpResponse = {
    _resolve_route(req) match {
      case Some((component, service, operation)) =>
        _execute_http(component, service, operation, req)
      case None =>
        _not_found()
    }
  }

  def execute(request: Request): Consequence[Response] = {
    executeOperationResponse(request).map(_to_response(request, _))
  }

  def executeOperationResponse(request: Request): Consequence[OperationResponse] = {
    val domainRequest = _domain_request(request)
    val r: Consequence[OperationResponse] = for {
      route <- _resolve_route(request) match {
        case Some(r) =>
          Consequence.success(r)
        case None =>
          Consequence.operationNotFound("operation route")
      }
      response <- {
        val (component, _, _) = route
        IngressSecurityResolver.resolve(component.logic.executionContext(), request).flatMap { security =>
          given ExecutionContext = security.executionContext
          _authorize_operation(route, security.executionContext).flatMap { _ =>
          component.logic.makeOperationRequest(domainRequest).flatMap { r =>
          r match {
            case action: Action =>
              component.logic.executeAction(action, security.executionContext)
            case _ =>
              Consequence.argumentInvalid("OperationRequest must be Action")
          }
        }
          }
        }
      }
    } yield response
    _observe_execute_failure(request, r)
    r
  }

  private def _observe_execute_failure[A](
    request: Request,
    r: Consequence[A]
  ): Unit =
    r match {
      case Consequence.Failure(c) =>
        val _ = _subsystem_scope_context.observe_error(
          "execute_failed",
          attributes = Record.data(
            "reason" -> c.status.toString,
            "request" -> request.toString
          )
        )
      case _ =>
        ()
    }

  def executeWired(
    binding: GenericSubsystemResolvedWiringBinding,
    request: Request
  ): Consequence[Subsystem.WiredExecutionResult] =
    executeWired(Some(binding), request)

  def executeWired(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request
  ): Consequence[Subsystem.WiredExecutionResult] =
    for {
      mediatedRequest <- _apply_request_glue(binding, request)
      response <- execute(mediatedRequest)
      mediatedResponse <- _apply_response_glue(binding, mediatedRequest, response)
    } yield mediatedResponse

  def executeAction(action: Action): Consequence[OperationResponse] =
    _resolve_route(action.request) match {
      case Some((component, service, operation)) =>
        IngressSecurityResolver.resolve(action.request).flatMap { security =>
          given ExecutionContext = security.executionContext
          _authorize_operation((component, service, operation), security.executionContext).flatMap { _ =>
          component.logic.executeAction(action, security.executionContext)
          }
        }
      case None =>
        Consequence.operationNotFound("operation route")
    }

  private def _authorize_operation(
    route: (Component, ServiceDefinition, OperationDefinition),
    ctx: ExecutionContext
  ): Consequence[Unit] = {
    val (component, service, operation) = route
    val selector = s"${component.name}.${service.name}.${operation.name}"
    val runtimeConfig = RuntimeConfig.from(configuration)
    val rule = operation match {
      case provider: OperationAuthorizationProvider =>
        Some(provider.operationAuthorization(runtimeConfig))
      case _ =>
        _cml_operation_authorization_rule(component, operation.name)
          .orElse(descriptor.flatMap(_.operationAuthorizationRule(selector)))
    }
    rule match {
      case Some(r) =>
        given ExecutionContext = _operation_authorization_context(ctx, runtimeConfig)
        OperationAuthorization.authorize(selector, r)
      case _ =>
        Consequence.unit
    }
  }

  private def _cml_operation_authorization_rule(
    component: Component,
    operationName: String
  ): Option[org.goldenport.cncf.security.OperationAuthorizationRule] =
    component.operationDefinitions
      .find(x => _normalize_operation_name(x.name) == _normalize_operation_name(operationName))
      .flatMap(_.operationAuthorization)

  private def _normalize_operation_name(name: String): String =
    Option(name).getOrElse("").replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT)

  private def _operation_authorization_context(
    ctx: ExecutionContext,
    runtimeConfig: RuntimeConfig
  ): ExecutionContext = {
    val runtime = new RuntimeContext(
      core = ctx.runtime.core,
      unitOfWorkSupplier = () => ctx.unitOfWork,
      unitOfWorkInterpreterFn = ctx.runtime.unitOfWorkInterpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "operation-authorization",
      context = ctx.runtime.context,
      operationMode = runtimeConfig.operationMode,
      transitionValidationHook = ctx.runtime.transitionValidationHook,
      entityCreateDefaultsPolicy = ctx.runtime.entityCreateDefaultsPolicy
    )
    ExecutionContext.withRuntimeContext(ctx, runtime)
  }

  private def _to_response(
    request: Request,
    response: OperationResponse
  ): Response =
    OperationResponseFormatter.toResponse(request, response, _http_run_mode)

  private def _apply_request_glue(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request
  ): Consequence[Request] =
    _glue_mode(binding, "request/mode") match {
      case "passthrough" =>
        Consequence.success(request)
      case other =>
        Consequence.operationInvalid(s"unsupported request glue mode: ${other}")
    }

  private def _apply_response_glue(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request,
    response: Response
  ): Consequence[Subsystem.WiredExecutionResult] = {
    val requestMode = _glue_mode(binding, "request/mode")
    val responseMode = _glue_mode(binding, "response/mode")
    responseMode match {
      case "passthrough" =>
        Consequence.success(
          Subsystem.WiredExecutionResult(
            request = request,
            response = response,
            glueApplied = Record.data(
              "request_mode" -> requestMode,
              "response_mode" -> responseMode
            )
          )
        )
      case other =>
        Consequence.operationInvalid(s"unsupported response glue mode: ${other}")
    }
  }

  private def _glue_mode(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    key: String
  ): String =
    binding
      .map(_.glue)
      .flatMap(_.asMap.get(key))
      .map(_.toString)
      .filter(_.nonEmpty)
      .getOrElse("passthrough")

  private def _domain_request(
    request: Request
  ): Request =
    request.copy(
      properties = request.properties.filterNot(p => _is_framework_or_query_property(p.name)),
      arguments = request.arguments.filterNot(p => _is_framework_or_security_argument(p.name))
    )

  private def _is_framework_or_query_property(
    name: String
  ): Boolean =
    name != null && (
      name.startsWith("textus.") ||
      name.startsWith("cncf.")
    )

  private def _is_framework_or_security_argument(
    name: String
  ): Boolean =
    name != null && (
      name.startsWith("textus.") ||
      name.startsWith("cncf.") ||
      name.startsWith("security.") ||
      name.startsWith("crud.") ||
      name == "principalId" ||
      name == "principal_id" ||
      name == "subjectId" ||
      name == "subject_id" ||
      name == "privilege" ||
      name == "capability" ||
      name == "capabilities"
    )

  private def _resolve_route(
    req: HttpRequest
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val segments = req.pathParts
    val spec = if (_is_spec_route(segments)) _resolve_spec_route(segments) else None
    spec.orElse {
      val normalizedSegments =
        PathPreNormalizer.rewriteSegments(segments, _http_run_mode, _alias_resolver)
      normalizedSegments match {
        case Vector(componentname, servicename, operationname) =>
          _resolve_route_via_resolver(componentname, servicename, operationname)
        case _ =>
          None
      }
    }
  }

  private def _resolve_route_via_resolver(
    componentname: String,
    servicename: String,
    operationname: String
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val selector = s"$componentname.$servicename.$operationname"
    _resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        val locator = NameLocator(component)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == service)
          operation <- _find_operation(service, operation)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _find_operation(
    service: ServiceDefinition,
    name: String
  ): Option[OperationDefinition] =
    service.operations.operations.find(_.name == name)

  private def _resolve_route(
    request: Request
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    (request.component, request.service) match {
      case (Some(componentname), Some(servicename)) =>
        val locator = NameLocator(componentname)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- _find_operation(service, request.operation)
        } yield (component, service, operation)
      case (None, Some(serviceid)) =>
        _resolve_route(serviceid, request.operation)
      case _ =>
        None
    }
  }

  private def _resolve_route(
    serviceid: String,
    operationname: String
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    serviceid.split("\\.") match {
      case Array(componentname, servicename) =>
        val locator = NameLocator(componentname)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- _find_operation(service, operationname)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _resolve_spec_route(
    segments: Vector[String]
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val specSegments = segments match {
      case Vector("spec-old", rest @ _*) =>
        rest.toVector
      case _ =>
        segments
    }
    val opsegment = specSegments match {
      case Vector("spec", "export", op) =>
        Some(op)
      case Vector("spec", "current", op) =>
        Some(op)
      case Vector("export", op) =>
        Some(op)
      case Vector("current", op) =>
        Some(op)
      case Vector("spec", op) =>
        Some(op)
      case Vector("openapi") =>
        Some("openapi")
      case Vector("openapi.json") =>
        Some("openapi.json")
      case Vector("openapi.html") =>
        Some("openapi.html")
      case _ =>
        None
    }
    opsegment.flatMap { op =>
      val operationname = op match {
        case "openapi" | "openapi.json" => "openapi"
        case other => other
      }
      val locator = NameLocator("spec")
      for {
        component <- _component_space.find(locator)
        service <- component.protocol.services.services.find(_.name == "export")
        operation <- service.operations.operations.find(_.name == operationname)
      } yield (component, service, operation)
    }
  }

  private def _is_spec_route(
    segments: Vector[String]
  ): Boolean =
    segments match {
      case Vector("spec-old", _*) => true
      case Vector("spec", _*) => true
      case Vector("export", _*) => true
      case Vector("current", _*) => true
      case Vector("openapi") => true
      case Vector("openapi.json") => true
      case Vector("openapi.html") => true
      case _ => false
    }

  private def _execute_http(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition,
    req: HttpRequest
  ): HttpResponse = {
//    _ensure_system_context(component)
    val _ = service
    val r: Consequence[Response] = for {
      ingress <- Consequence.fromOption(
        component.protocol.handler.ingresses
          .findByInput(classOf[HttpRequest]),
        "HTTP ingress not configured"
      )
      request0 <- ingress.encode(operation, req)
      request = request0.component match {
        case Some(_) => request0
        case None =>
          Request.ofHttpRequest(
            req,
            component = component.name,
            service = service.name,
            operation = operation.name,
            arguments = request0.arguments,
            switches = request0.switches,
            properties = request0.properties
          )
      }
      // enrichedRequest = if (component.name == DebugComponent.name) {
      //   val metadata = List(
      //     Property("http.method", req.method.name, None),
      //     Property("http.path", req.path.asString, None)
      //   )
      //   request.copy(properties = request.properties ++ metadata)
      // } else {
      //   request
      // }
      // Route HTTP ingress through the standard request execution path so
      // request-derived execution mode, security, and other ingress context
      // are applied consistently with command/client execution.
      response <- execute(request)
    } yield response
    r match {
      case Consequence.Success(res) =>
        _egress(component).encode(operation, res)
      case Consequence.Failure(c) =>
        _failure_response(c)
    }
  }

  private def _egress(
    component: Component
  ): Egress[HttpResponse] =
    component.protocol.handler.egresses
      .findByOutput(classOf[HttpResponse])
      .getOrElse {
        throw new IllegalStateException("HTTP egress not configured")
      }

  private def _not_found(): HttpResponse =
    HttpResponse.notFound()

  private def _internal_error(): HttpResponse =
    HttpResponse.internalServerError()

  private def _failure_response(c: org.goldenport.Conclusion): HttpResponse =
    HttpResponse.text(_http_status(c), c.displayMessage)

  private def _http_status(c: org.goldenport.Conclusion): HttpStatus =
    HttpStatus.fromInt(c.status.webCode.code).getOrElse(HttpStatus.InternalServerError)

  private val _alias_resolver: AliasResolver = aliasResolver
  private val _http_run_mode: RunMode = runMode

  // private def _ensure_system_context(
  //   component: Component
  // ): Unit = {
  //   val system = component.systemContext
  //   val snapshot = system.configSnapshot
  //   val mode = snapshot.get("cncf.mode")
  //   if (!mode.contains("server")) {
  //     val runtimeVersion = CncfVersion.current
  //     val subsystemVersion = version.getOrElse(runtimeVersion)
  //     val updated = snapshot ++ Map(
  //       "cncf.mode" -> "server",
  //       "cncf.subsystem" -> name,
  //       "cncf.runtime.version" -> runtimeVersion,
  //       "cncf.subsystem.version" -> subsystemVersion
  //     )
  //     component.withSystemContext(system.copy(configSnapshot = updated))
  //   }
  // }

  private def _component_id(name: String): ComponentId =
    ComponentId(name)

  private def _component_instance_id(
    id: ComponentId
  ): ComponentInstanceId =
    ComponentInstanceId.default(id)

  private def _header_value(
    header: Record,
    key: String
  ): Option[String] =
    header.asMap.collectFirst {
      case (name, value) if name.equalsIgnoreCase(key) => value.toString
    }
}

object Subsystem {
  import cats.syntax.all.*
  import org.goldenport.Consequence
  import org.goldenport.configuration.ResolvedConfiguration
  import org.goldenport.cncf.cli.RunMode

  final case class WiredExecutionResult(
    request: Request,
    response: Response,
    glueApplied: Record
  )

  // Unused
  final case class Config(
    httpDriver: String,
    mode: RunMode
  )

  object Config {
    def from(conf: ResolvedConfiguration): Consequence[Config] = {
      val httpDriver =
        conf.get[String]("cncf.subsystem.http.driver").flatMap {
          case Some(value) => Consequence.success(value)
          case None        => Consequence.argumentMissing("cncf.subsystem.http.driver")
        }

      val mode =
        conf
          .get[String]("cncf.subsystem.mode")
          .map(_.getOrElse("normal"))
          .flatMap { value =>
            RunMode.from(value) match {
              case Some(runMode) => Consequence.success(runMode)
              case None          => Consequence.argumentInvalid(s"invalid run mode: ${value}")
            }
          }

      (httpDriver, mode).mapN(Config.apply)
    }
  }
}
