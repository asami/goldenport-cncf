package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Command

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    val result = parse_command_args(args).flatMap { req =>
      subsystem.execute(req)
    }
    result match {
      case Consequence.Success(res) =>
        print_response(res)
      case Consequence.Failure(conclusion) =>
        print_error(conclusion)
    }
    exit_code(result)
  }
}

object CommandOperation {
}
