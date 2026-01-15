package org.goldenport.cncf.client

import org.goldenport.cncf.action.{Action, ActionCall, Command, FunctionalActionCall, OperationCallHttpPart, Query, ResourceAccess}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.operation.OperationResponse
import cats.syntax.functor.*
import cats.Functor
import org.goldenport.bag.TextBag

/*
 * @since   Jan. 10, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class HttpCommand(
) extends Command()

abstract class HttpQuery(
) extends Query()

final case class PostCommand(
  name: String,
  request: HttpRequest
) extends HttpCommand() {
  override def createCall(core: ActionCall.Core): ActionCall =
    ClientHttpPostCall(core, request)
}

final case class GetQuery(
  name: String,
  request: HttpRequest
) extends HttpQuery() {
  override def createCall(core: ActionCall.Core): ActionCall =
    ClientHttpGetCall(core, request)
}

sealed trait ClientHttpActionCall extends FunctionalActionCall with OperationCallHttpPart {
  def request: HttpRequest

  protected final def build_Program: ExecUowM[OperationResponse] = {
    Functor[ExecUowM].map(_uow(request))(OperationResponse.Http.apply)
  }

  private def _uow(req: HttpRequest): ExecUowM[HttpResponse] = {
    val url = req.urlStringWithQuery
    val headers = req.header.asMap.map { case (k, v) => k -> v.toString }
    req.method match {
      case HttpRequest.POST =>
        http_post(url, _body(req), headers)
      case _ =>
        http_get(url)
    }
  }

  private def _body(req: HttpRequest): Option[String] =
    req.body match {
      case Some(t: TextBag) => Some(t.toText)
      case _ => None
    }
}

final case class ClientHttpPostCall(
  core: ActionCall.Core,
  request: HttpRequest
) extends ClientHttpActionCall

final case class ClientHttpGetCall(
  core: ActionCall.Core,
  request: HttpRequest
) extends ClientHttpActionCall
