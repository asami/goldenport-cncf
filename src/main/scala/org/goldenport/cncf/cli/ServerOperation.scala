package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.http.{HttpExecutionEngine, Http4sHttpServer}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  1, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
class ServerOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Server

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    HttpExecutionEngine.Factory.forRuntime(subsystem) match {
      case Consequence.Success(engine) =>
        val server = new Http4sHttpServer(engine)
        server.start(args)
        exit_success
      case Consequence.Failure(conclusion) =>
        print_error(conclusion)
        exit_code(Consequence.Failure(conclusion))
    }
  }
}

object ServerOperation {
}
