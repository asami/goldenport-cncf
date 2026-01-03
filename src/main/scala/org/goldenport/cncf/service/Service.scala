package org.goldenport.cncf.service

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Protocol, Request, Response}
import org.goldenport.protocol.service.{Service as ProtocolService}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.action.ActionEngine
import org.goldenport.cncf.component.{Component, ComponentLogic}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 31, 2025
 * @version Jan.  3, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Service extends ProtocolService with Service.CCore.Holder {
  def executeCli(args: Array[String]): Consequence[String] =
    for {
      opreq <- logic.makeOperationRequest(args)
      res <- _execute(opreq)
      r <- logic.makeStringOperationResponse(res)
    } yield r

  def executeHttp(req: HttpRequest): Consequence[HttpResponse] = ???

  private def _execute(p: OperationRequest) = p match {
    case action: Action =>
      val ac = logic.createActionCall(action)
      logic.execute(ac)
    case m => ???
  }
}

object Service {
  final case class CCore(
    logic: ComponentLogic
  )
  object CCore {
    trait Holder {
      def ccore: CCore

      def logic: ComponentLogic = ccore.logic
    }
  }

  case class Instance(core: ProtocolService.Core, ccore: CCore)
      extends Service with CCore.Holder {
  }

  def apply(core: ProtocolService.Core, ccore: CCore): Service =
    Instance(core, ccore)
}

case class ServiceGroup(services: Vector[Service] = Vector.empty)

object ServiceGroup {
  val empty = new ServiceGroup()

  def apply(): ServiceGroup = empty
}

// class InteractionEngine(
//   val protocol: Protocol,
//   val engine: ActionEngine
// ) {
//   def executeCli(args: Array[String]): Consequence[Response] = {
//     ???
//   }

//   def executeHttp(req: HttpRequest): Consequence[HttpResponse] = {
//     ???
//   }

//   def execute(req: Request): Consequence[Response] = {
//     ???
//   }

//   def execute(req: OperationRequest): Consequence[OperationResponse] = {
//     ???
//   }
// }
