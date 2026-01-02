package org.goldenport.cncf.service

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Protocol, Request, Response}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.Engine

/*
 * @since   Apr. 11, 2025
 *  version Dec. 31, 2025
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Service {
  def executeCli(args: Array[String]): Consequence[Response]
  def executeHttp(req: HttpRequest): Consequence[HttpResponse]
}

object Service {
}

class InteractionEngine(
  val protocol: Protocol,
  val engine: Engine
) {
  def executeCli(args: Array[String]): Consequence[Response] = {
    ???
  }

  def executeHttp(req: HttpRequest): Consequence[HttpResponse] = {
    ???
  }

  def execute(req: Request): Consequence[Response] = {
    ???
  }

  def execute(req: OperationRequest): Consequence[OperationResponse] = {
    ???
  }
}
