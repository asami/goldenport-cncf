package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.cncf.component.Component

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class Subsystem(
  name: String,
  components: Map[String, Component]
) {

  def executeHttp(req: HttpRequest): HttpResponse = {
    _resolve_route(req) match {
      case Some((component, service, operation)) =>
        _execute_http(component, service, operation, req)
      case None =>
        _not_found()
    }
  }

  private def _resolve_route(
    req: HttpRequest
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    req.pathParts match {
      case Vector(componentname, servicename, operationname) =>
        for {
          component <- components.get(componentname)
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
