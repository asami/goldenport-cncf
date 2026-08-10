package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.http.{HttpExecutionEngine, Http4sHttpServer, ServerEndpointPolicy}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  1, 2026
 * @version Aug. 10, 2026
 * @author  ASAMI, Tomoharu
 */
class ServerOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Server

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    (for {
      endpoint <- ServerEndpointPolicy.resolve(subsystem)
      engine <- HttpExecutionEngine.Factory.forRuntime(subsystem)
    } yield (endpoint, engine)) match {
      case Consequence.Success((endpoint, engine)) =>
        val server = Http4sHttpServer.forEndpoint(engine, endpoint)
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
