package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ingress.DefaultArgsIngress
import org.goldenport.protocol.spec.ServiceDefinitionGroup

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime {
  def run(args: Array[String]): Unit = {
    val (backend, actualargs) = _log_backend(args)
    backend.foreach(_install_log_backend)
    if (actualargs.isEmpty) {
      _print_usage()
      return
    }
    val ingress = DefaultArgsIngress()
    val r: Consequence[Request] =
      ingress.encode(ServiceDefinitionGroup(Vector.empty), actualargs)
    r match {
      case Consequence.Success(req) =>
        if (req.operation.isEmpty) {
          _print_usage()
          return
        }
        val mode = RunMode.from(req.operation)
        mode match {
          case Some(RunMode.Server) =>
            ServerLauncher.start(actualargs.drop(1))
          case Some(RunMode.Client) =>
            ClientLauncher.execute(actualargs.drop(1))
          case Some(RunMode.Command) =>
            CommandLauncher.execute(actualargs.drop(1))
          case None =>
            _print_error(s"Unknown mode: ${req.operation}")
            _print_usage()
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion.message)
        _print_usage()
    }
  }

  private def _print_error(message: String): Unit = {
    Console.err.println(message)
  }

  private def _print_usage(): Unit = {
    val text =
      """Usage:
        |  cncf server
        |  cncf client
        |  cncf command <cmd>
        |
        |Examples:
        |  cncf server
        |  cncf command admin.system.ping
        |""".stripMargin
    Console.err.println(text)
  }

  private def _log_backend(
    args: Array[String]
  ): (Option[LogBackend], Array[String]) = {
    var backend: Option[LogBackend] = None
    val rest = args.filter { arg =>
      if (arg.startsWith("--log-backend=")) {
        val value = arg.stripPrefix("--log-backend=")
        backend = LogBackend.from(value)
        false
      } else {
        true
      }
    }
    (backend, rest)
  }

  private def _install_log_backend(
    backend: LogBackend
  ): Unit = {
    LogBackendHolder.install(backend)
  }
}
