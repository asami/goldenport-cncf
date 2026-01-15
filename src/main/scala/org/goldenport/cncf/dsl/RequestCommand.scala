package org.goldenport.cncf.dsl

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, Command, ProcedureActionCall, ResourceAccess}

/**
 * Minimal bridge command for DSL Level 1.
 *
 * It wraps a Request handler and executes it via the existing ActionCall flow.
 */
/*
 * @since   Jan. 11, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RequestCommand(
  name: String,
  request: Request,
  handler: Request => Consequence[OperationResponse]
) extends Command() {
  override def createCall(core: ActionCall.Core): ActionCall = {
    RequestActionCall(core, this)
  }
}

final case class RequestActionCall(
  core: ActionCall.Core,
  cmd: RequestCommand
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] = {
    cmd.handler(cmd.request)
  }
}
