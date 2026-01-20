package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInstanceId,
  ComponentSpace
}
import org.goldenport.cncf.component.ComponentLocator.NameLocator
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.protocol.{Request, Response}

import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.{AliasResolver, PathPreNormalizer}

/*
 * @since   Jan.  7, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class Subsystem(
  val name: String,
  val version: Option[String] = None,
  scopeContext: Option[ScopeContext] = None, // TODO
  httpdriver: Option[HttpDriver] = None,
  val configuration: ResolvedConfiguration
) {
  private var _component_space: ComponentSpace = ComponentSpace()
  private var _resolver: OperationResolver = OperationResolver.empty
  private val _http_driver: Option[HttpDriver] = httpdriver

  def httpDriver: Option[HttpDriver] = _http_driver

  def add(comps: Seq[Component]): Subsystem = {
    val injected = comps.map(x => _inject_context(x.name, x))
    _component_space = _component_space.add(injected)
    _rebuildResolver()
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
      componentOrigin = comp.origin
    )
    comp.withScopeContext(sc)
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

  def resolver: OperationResolver = _resolver

  def operationResolver: OperationResolver = _resolver

  def configurationValue(key: String): Option[org.goldenport.configuration.ConfigurationValue] =
    configuration.configuration.values.get(key)

  def configurationOrEmpty: org.goldenport.configuration.Configuration =
    configuration.configuration

  private def _rebuildResolver(): Unit =
    _resolver = OperationResolver.build(_component_space.components)

  def executeHttp(req: HttpRequest): HttpResponse = {
    _resolve_route(req) match {
      case Some((component, service, operation)) =>
        _execute_http(component, service, operation, req)
      case None =>
        _not_found()
    }
  }

  def execute(request: Request): Consequence[Response] = {
    val r: Consequence[Response] = for {
      route <- _resolve_route(request) match {
        case Some(r) =>
          Consequence.success(r)
        case None =>
          Consequence.failure("Operation route not found")
      }
      response <- {
        val (component, _, _) = route
        component.logic.makeOperationRequest(request).flatMap { r =>
          r match {
            case action: Action =>
              val call = component.logic.createActionCall(action)
              component.logic.execute(call).flatMap(opres => Consequence.success(opres.toResponse))
            case _ =>
              Consequence.failure("OperationRequest must be Action")
          }
        }
      }
    } yield response
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
    r
  }

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
          Request.of(
            component = component.name,
            service = service.name,
            operation = operation.name,
            arguments = request0.arguments,
            switches = request0.switches,
            properties = request0.properties
          )
      }
      response <- component.service.invokeRequest(request)
    } yield response
    r match {
      case Consequence.Success(res) =>
        _egress(component).encode(operation, res)
      case Consequence.Failure(_) =>
        _internal_error()
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

  private def _alias_resolver: AliasResolver =
    GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)

  private def _http_run_mode: RunMode =
    GlobalRuntimeContext.current
      .map(_.runtimeMode)
      .getOrElse(RunMode.Server)

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

  final case class Config(
    httpDriver: String,
    mode: RunMode
  )

  object Config {
    def from(conf: ResolvedConfiguration): Consequence[Config] = {
      val httpDriver =
        conf.get[String]("cncf.subsystem.http.driver").flatMap {
          case Some(value) => Consequence.success(value)
          case None        => Consequence.failure("cncf.subsystem.http.driver is required")
        }

      val mode =
        conf
          .get[String]("cncf.subsystem.mode")
          .map(_.getOrElse("normal"))
          .flatMap { value =>
            RunMode.from(value) match {
              case Some(runMode) => Consequence.success(runMode)
              case None          => Consequence.failure(s"invalid run mode: ${value}")
            }
          }

      (httpDriver, mode).mapN(Config.apply)
    }
  }
}
