package org.goldenport.cncf.action

import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.text.Presentable

/*
 * @since   Apr. 12, 2025
 *  version Jan.  1, 2026
 * @version Jan. 20, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Action extends OperationRequest with Presentable {
  def name: String = request.name
  def createCall(core: ActionCall.Core): ActionCall

  override def print: String = s"Action(${request.name})"
  override def display: String = request.name
  override def show: String = display
}

abstract class Command(
) extends Action {
}
object Command {
  // case class Instance(
  //   name: String,
  //   core: OperationRequest.Core
  // ) extends Command() {
  //   def createCall(core: ActionCall.Core): ActionCall = ProcedureActionCall
  // }
}

abstract class Query(
) extends Action {
}
