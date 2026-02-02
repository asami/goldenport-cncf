package org.goldenport.cncf.collaborator

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Jan. 30, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Collaborator() {
  def execute(
    ctx: ExecutionContext,
    request: Request
  ): Consequence[Response]
}

object Collaborator {
  case class Core(
    collaborator: api.Collaborator
  )
  object Core {
    trait Holder {
      def core: Core

      def collaborator = core.collaborator
    }
  }

  case class Instance(core: Core) extends Core.Holder {
    def execute(ctx: ExecutionContext, request: Request): Consequence[Response] = {
      val ccall = toCollaborator(request)
      try {
        val r = collaborator.invoke(ccall)
        fromCollaborator(r)
      } catch {
        case NonFatal(e) => Consequence.failure(e) // TODO
      }
    }
  }

  def toCollaborator(p: Request): api.ActionCall = ???
  def fromCollaborator(p: api.Consequence): Consequence[Response] = ???

/*
    val call: JActionCall = new DefaultActionCall(operationName, arguments.mapValues(_.asInstanceOf[Object]).asJava)
    val consequence: JConsequence = resolveCollaborator().invoke(call)
    if (consequence.isSuccess) {
      Consequence.success(OperationResponse.Scalar(consequence.value().toString))
    } else {
      Consequence.failure(new RuntimeException("collaborator failure"))
    }
 */ 
}
