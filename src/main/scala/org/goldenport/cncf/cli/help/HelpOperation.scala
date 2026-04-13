package org.goldenport.cncf.cli.help

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Argument
import org.goldenport.cncf.cli.{CliOperation, RunMode}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan. 31, 2026
 *  version Feb.  1, 2026
 *  version Mar.  5, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class HelpOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.ServerEmulator // TODO

  def execute(req: Request): Int = {
    val result = _to_meta_help_request(req).flatMap(subsystem.execute)
    result match {
      case Consequence.Success(res) =>
        print_response(res)
      case Consequence.Failure(conclusion) =>
        print_error(conclusion)
    }
    exit_code(result)
  }

  private def _to_meta_help_request(req: Request): Consequence[Request] = {
    val componentOpt = subsystem.components.sortBy(_.name).headOption.map(_.name)
    componentOpt match {
      case Some(componentName) =>
        val args = req.arguments.toVector.map(a => Option(a.value).map(_.toString).getOrElse("")).filter(_.nonEmpty)
        val arguments = args.zipWithIndex.map { case (value, index) =>
          Argument(s"arg${index + 1}", value)
        }.toList
        Consequence.success(
          Request.of(
            component = componentName,
            service = "meta",
            operation = "help",
            arguments = arguments,
            switches = Nil,
            properties = Nil
          )
        )
      case None =>
        Consequence.serviceUnavailable("component not available for meta.help delegation")
    }
  }
}

object HelpOperation {
}
