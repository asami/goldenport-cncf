package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class Subsystem(
  name: String,
  components: Map[String, Component],
  scopeContext: Option[ScopeContext] = None
) {
  private val _subsystem_scope_context: ScopeContext =
    scopeContext.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = name,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )
    }

  private val _components: Map[String, Component] =
    components.map { case (componentname, component) =>
      val sc = _subsystem_scope_context.createChildScope(
        ScopeKind.Component,
        componentname
      )
      component.withScopeContext(sc)
      componentname -> component
    }

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
    req.pathParts match {
      case Vector(componentname, servicename, operationname) =>
        for {
          component <- _components.get(componentname)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- service.operations.operations.find(_.name == operationname)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _resolve_route(
    request: Request
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    request.service match {
      case Some(serviceid) =>
        _resolve_route(serviceid, request.operation)
      case None =>
        None
    }
  }

  private def _resolve_route(
    serviceid: String,
    operationname: String
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    serviceid.split("\\.") match {
      case Array(componentname, servicename) =>
        for {
          component <- _components.get(componentname)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- service.operations.operations.find(_.name == operationname)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _execute_http(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition,
    req: HttpRequest
  ): HttpResponse = {
    val _ = service
    val r: Consequence[Response] = for {
      ingress <- Consequence.fromOption(
        component.protocol.handler.ingresses
          .findByInput(classOf[HttpRequest]),
        "HTTP ingress not configured"
      )
      request <- ingress.encode(operation, req)
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
}
