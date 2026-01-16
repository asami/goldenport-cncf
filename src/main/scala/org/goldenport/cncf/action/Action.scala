package org.goldenport.cncf.action

import org.goldenport.protocol.operation.OperationRequest

/*
 * @since   Apr. 12, 2025
 *  version Jan.  1, 2026
 * @version Jan. 17, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Action extends OperationRequest {
  def name: String = request.name
  def createCall(core: ActionCall.Core): ActionCall
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
