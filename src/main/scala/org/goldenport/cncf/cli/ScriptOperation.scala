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
class ScriptOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Script

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    val result = _to_request_script(args).flatMap { req =>
      subsystem.execute(req)
    }
    exit_code(result)
  }

  private def _to_request_script(args: Array[String]) = {
    parse_command_args(args) match {
      case success @ Consequence.Success(_) => success
      case _ =>
        val in = args.toVector
        (in.lift(0), in.lift(1), in.lift(2)) match {
          case (Some("SCRIPT"), Some("DEFAULT"), Some("RUN")) =>
            parse_command_args(args)
          case _ =>
            val xs = Vector("SCRIPT", "DEFAULT", "RUN") ++ in
            parse_command_args(xs.toArray)
        }
    }
  }
}

object ScriptOperation {
}
