package org.goldenport.cncf.action

import org.goldenport.protocol.*
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.text.Presentable

/*
 * @since   Apr. 12, 2025
 *  version Jan.  1, 2026
 *  version Jan. 22, 2026
 *  version Feb. 27, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Action extends OperationRequest with Presentable {
  def name: String = request.name
  def createCall(core: ActionCall.Core): ActionCall

  override def print: String = s"Action(${request.name})"
  override def display: String = request.name
  override def show: String = display

  def arguments: List[Argument] = request.arguments
  def switches: List[Switch] = request.switches
  def properties: List[Property] = request.properties
  def args: List[String] = request.args
}

abstract class CommandAction(
) extends Action {
  def commandExecutionMode: CommandExecutionMode =
    CommandExecutionMode.AsyncJob
}

// abstract class Command(
// ) extends CommandAction {
// }
object CommandAction {
  // case class Instance(
  //   name: String,
  //   core: OperationRequest.Core
  // ) extends Command() {
  //   def createCall(core: ActionCall.Core): ActionCall = ProcedureActionCall
  // }
}

enum CommandExecutionMode {
  case AsyncJob
  case AsyncJobAndAwait
  case SyncJob
  case SyncDirectNoJob
}

abstract class QueryAction(
) extends Action {
}

// abstract class Query(
// ) extends QueryAction {
// }
