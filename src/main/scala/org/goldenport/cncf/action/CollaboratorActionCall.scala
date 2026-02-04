package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.collaborator.Collaborator

/*
 * @since   Jan. 30, 2026


 * @version Feb.  4, 2026


 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class CollaboratorActionCall(
) extends ProcedureActionCall with CollaboratorActionCall.Core.Holder {
  override def execute(): Consequence[OperationResponse] = {
    val req = collaborator_request
    val res = collaborator.execute(executionContext, req)
    res.flatMap(to_Operation_Response)
  }

  protected def collaborator_request: Request =
    action.request.withOperation(operationName)

  protected def to_Operation_Response(response: Response): Consequence[OperationResponse] =
    Consequence(OperationResponse.from(response))
}

object CollaboratorActionCall {
  case class Core(
    collaborator: Collaborator,
    operationName: String
  )
  object Core {
    trait Holder {
      def collaboratorCore: Core

      def collaborator = collaboratorCore.collaborator
      def operationName = collaboratorCore.operationName
    }
  }

  case class Instance(
    core: ActionCall.Core,
    collaboratorCore: Core
  ) extends Core.Holder {
  }
}
