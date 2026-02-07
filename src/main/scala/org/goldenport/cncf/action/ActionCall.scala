package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.text.Presentable
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.CollaboratorComponent
import org.goldenport.cncf.backend.collaborator.Collaborator

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 *  version Dec. 31, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 *  version Jan. 22, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall()
  extends ActionCall.Core.Holder
  with ActionCallDataStorePart
  with ActionCallHttpPart
  with ActionCallShellCommandPart
  with Presentable {
  def name: String = objectToSnakeName("ActionCall", this)
  def accesses: Vector[ResourceAccess] = Vector.empty

  def execute(): Consequence[OperationResponse]

  def commit(): Consequence[UnitOfWork.CommitResult] = {
    val uow = executionContext.runtime.unitOfWork
    uow.record("ActionCall.commit")
    uow.commit()
  }

  override def print: String = s"ActionCall(${action.display})"
  override def display: String = action.display
  override def show: String = correlationId.fold(display)(cid => s"$display@${cid.show}")

  def request = action.request
  def arguments: List[Argument] = action.arguments
  def switches: List[Switch] = action.switches
  def properties: List[Property] = action.properties
  def args: List[String] = action.args
}

abstract class FunctionalActionCall extends ActionCall {
  protected def build_Program: ExecUowM[OperationResponse]

  final override def execute(): Consequence[OperationResponse] =
    build_Program.value.foldMap(executionContext.runtime.unitOfWorkInterpreter)
}

abstract class ProcedureActionCall extends ActionCall {
  override def execute(): Consequence[OperationResponse]
}

object ActionCall {
  final case class Core(
    action: Action,
    executionContext: ExecutionContext,
    component: Option[Component],
    correlationId: Option[CorrelationId]
  ) {
    def getCollaborator: Option[Collaborator] = component.flatMap {
      case m: CollaboratorComponent => Some(m.collaborator)
      case _ => None
    }

    inline def collaborator: Collaborator = getCollaborator getOrElse {
      Consequence.failUninitializedState.RAISE
    }

    def createCollaboratorActionCallCore(opname: String): CollaboratorActionCall.Core =
      CollaboratorActionCall.Core(collaborator, opname)
  }
  object Core {
    trait Holder {
      def core: Core

      def action: Action = core.action
      def executionContext: ExecutionContext = core.executionContext
      def component: Option[Component] = core.component
      def correlationId: Option[CorrelationId] = core.correlationId
    }
  }
}
