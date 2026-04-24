package org.goldenport.cncf.cli

import org.goldenport.protocol.Request
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  1, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class ClientOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Client

  def execute(req: Request): Int =
    new CncfRuntime().executeClient(subsystem, make_component_args(req))
}

object ClientOperation {
  def apply(subsystem: Subsystem): ClientOperation =
    new ClientOperation(subsystem)
}
