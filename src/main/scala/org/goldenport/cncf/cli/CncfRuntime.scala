package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ingress.DefaultArgsIngress
import org.goldenport.protocol.spec.ServiceDefinitionGroup
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime {
  def run(args: Array[String]): Unit = {
    val (backendoption, actualargs) = _log_backend(args)
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
            _install_log_backend(_decide_backend(backendoption, RunMode.Server))
            ServerLauncher.start(actualargs.drop(1))
          case Some(RunMode.Client) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Client))
            ClientLauncher.execute(actualargs.drop(1))
          case Some(RunMode.Command) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Command))
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
        |
        |Log backend behavior:
        |  command / client : no logs by default
        |  server           : SLF4J logging enabled
        |  --log-backend=stdout|stderr|nop|slf4j overrides defaults
        |""".stripMargin
    Console.err.println(text)
  }

  private def _log_backend(
    args: Array[String]
  ): (Option[String], Array[String]) = {
    var logBackendOption: Option[String] = None
    val rest = args.filter { arg =>
      if (arg.startsWith("--log-backend=")) {
        val value = arg.stripPrefix("--log-backend=")
        logBackendOption = Some(value)
        false
      } else {
        true
      }
    }
    (logBackendOption, rest)
  }

  private def _decide_backend(
    backend: Option[String],
    mode: RunMode
  ): LogBackend = {
    backend match {
      case Some(p) =>
        LogBackend.fromString(p).getOrElse {
          _print_error(s"Unknown log backend: $p")
          _print_usage()
          LogBackend.NopLogBackend
        }
      case None =>
        mode match {
          case RunMode.Command => LogBackend.NopLogBackend
          case RunMode.Client => LogBackend.NopLogBackend
          case RunMode.Server => LogBackend.Slf4jLogBackend
        }
    }
  }

  private def _install_log_backend(
    backend: LogBackend
  ): Unit = {
    LogBackendHolder.install(backend)
  }
}
