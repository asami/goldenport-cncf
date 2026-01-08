package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.handler.ingress.RestIngress
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.cncf.action.{Action, ActionEngine}

/*
 * @since   Jan.  8, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpExecutionEngine(
  router: HttpRouter,
  catalog: OperationCatalog,
  ingress: RestIngress,
  actionEngine: ActionEngine
) {
  def execute(req: HttpRequest): HttpResponse = {
    router.resolve(req) match {
      case HttpRouter.Resolved(route) =>
        _resolve_operation(route) match {
          case OperationCatalog.Found(op) =>
            _ingress(op, req) match {
              case Consequence.Success(protocolRequest) =>
                _validate(op, protocolRequest) match {
                  case Consequence.Success(opreq) =>
                    _execute(opreq)
                  case Consequence.Failure(_) =>
                    _bad_request()
                }
              case Consequence.Failure(_) =>
                _bad_request()
            }
          case OperationCatalog.NotFound =>
            HttpResponse.notFound()
          case OperationCatalog.Failure(_) =>
            HttpResponse.internalServerError()
        }
      case HttpRouter.RouteNotFound =>
        HttpResponse.notFound()
      case HttpRouter.InvalidRoute(_) =>
        _bad_request()
    }
  }

  private def _resolve_operation(
    route: HttpRouter.Route
  ): OperationCatalog.Result =
    catalog.resolve(route)

  private def _ingress(
    op: OperationDefinition,
    req: HttpRequest
  ): Consequence[Request] =
    ingress.encode(op, req)

  private def _validate(
    op: OperationDefinition,
    req: Request
  ): Consequence[OperationRequest] =
    op.createOperationRequest(req)

  private def _execute(
    opreq: OperationRequest
  ): HttpResponse = {
    opreq match {
      case action: Action =>
        val call = actionEngine.createActionCall(action)
        try {
          actionEngine.execute(call) match {
            case Consequence.Success(opres) =>
              _ok_response(opres)
            case Consequence.Failure(_) =>
              _bad_request()
          }
        } catch {
          case _: Throwable =>
            HttpResponse.internalServerError()
        }
      case _ =>
        _bad_request()
    }
  }

  private def _ok_response(
    opres: OperationResponse
  ): HttpResponse = {
    val res: Response = opres.toResponse
    val body = res match {
      case Response.Scalar(value) => value.toString
      case Response.Json(json) => json
      case Response.Void() => ""
      case other => other.toString
    }
    HttpResponse.text(HttpStatus.Ok, body)
  }

  private def _bad_request(): HttpResponse =
    HttpResponse.text(HttpStatus.BadRequest, "bad request")
}

trait HttpRouter {
  def resolve(req: HttpRequest): HttpRouter.Result
}

object HttpRouter {
  final case class Route(
    component: String,
    service: String,
    operation: String
  )

  sealed trait Result
  final case class Resolved(route: Route) extends Result
  case object RouteNotFound extends Result
  final case class InvalidRoute(message: String) extends Result
}

trait OperationCatalog {
  def resolve(route: HttpRouter.Route): OperationCatalog.Result
}

object OperationCatalog {
  sealed trait Result
  final case class Found(op: OperationDefinition) extends Result
  case object NotFound extends Result
  final case class Failure(message: String) extends Result
}

object DefaultHttpExecutionEngine {
  trait ComponentServiceResolver {
    def singleService(component: String): Option[String]
  }

  private final class DefaultHttpRouter(
    resolver: Option[ComponentServiceResolver]
  ) extends HttpRouter {
    def resolve(req: HttpRequest): HttpRouter.Result = {
      val segments: Vector[String] = req.path.segments
      segments match {
        case Vector(component, service, operation) =>
          HttpRouter.Resolved(
            HttpRouter.Route(component, service, operation)
          )
        case Vector(component, operation) =>
          resolver.flatMap(_.singleService(component)) match {
            case Some(service) =>
              HttpRouter.Resolved(
                HttpRouter.Route(component, service, operation)
              )
            case None =>
              HttpRouter.InvalidRoute("invalid route")
          }
        case Vector(operation) =>
          HttpRouter.InvalidRoute("invalid route")
        case _ =>
          HttpRouter.InvalidRoute("invalid route")
      }
    }
  }

  def apply(
    catalog: OperationCatalog,
    actionEngine: ActionEngine
  ): HttpExecutionEngine = {
    val resolver = catalog match {
      case r: ComponentServiceResolver => Some(r)
      case _ => None
    }
    new HttpExecutionEngine(
      new DefaultHttpRouter(resolver),
      catalog,
      RestIngress(),
      actionEngine
    )
  }
}
