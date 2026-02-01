package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan. 31, 2026
 *  version Jan. 31, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class HelpOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.ServerEmulator // TODO

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    val result = ???
    exit_code(result)
  }
}

object HelpOperation {
}
