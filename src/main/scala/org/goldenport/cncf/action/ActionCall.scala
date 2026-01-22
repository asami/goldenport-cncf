package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.util.StringUtils.objectToSnakeName
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.text.Presentable

/*
 * @since   Apr. 11, 2025
 *  version Apr. 14, 2025
 *  version Dec. 31, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 * @version Jan. 22, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ActionCall()
  extends ActionCall.Core.Holder
  with ActionCallDataStorePart
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
    correlationId: Option[CorrelationId]
  )
  object Core {
    trait Holder {
      def core: Core

      def action: Action = core.action
      def executionContext: ExecutionContext = core.executionContext
      def correlationId: Option[CorrelationId] = core.correlationId
    }
  }
}
