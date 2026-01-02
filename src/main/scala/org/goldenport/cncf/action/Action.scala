package org.goldenport.cncf.action

import org.goldenport.protocol.operation.OperationRequest

/*
 * @since   Apr. 12, 2025
 *  version Jan.  1, 2026
 * @version Jan.  2, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Action extends OperationRequest {
  def name: String
  def createCall(core: ActionCall.Core): ActionCall
}

abstract class Command(
  val name: String
) extends Action {
}

abstract class Query(
  val name: String
) extends Action {
}
