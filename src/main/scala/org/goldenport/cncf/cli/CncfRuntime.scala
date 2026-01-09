package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.http.{Http4sHttpServer, HttpExecutionEngine}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}

/*
 * @since   Jan.  7, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime {
  def buildSubsystem(): Subsystem = {
    DefaultSubsystemFactory.default()
  }

  def startServer(args: Array[String]): Unit = {
    val engine = new HttpExecutionEngine(buildSubsystem())
    val server = new Http4sHttpServer(engine)
    server.start(args)
  }

  def executeClient(args: Array[String]): Unit = {
    val subsystem = buildSubsystem()
    _to_request(args).flatMap { req =>
      subsystem.execute(req)
    } match {
      case Consequence.Success(res) =>
        _print_response(res)
        sys.exit(0)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        sys.exit(1)
    }
  }

  def executeCommand(args: Array[String]): Unit = {
    val subsystem = buildSubsystem()
    _to_request(args).flatMap { req =>
      subsystem.execute(req)
    } match {
      case Consequence.Success(res) =>
        _print_response(res)
        sys.exit(0)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        sys.exit(1)
    }
  }

  def executeServerEmulator(args: Array[String]): Unit = {
    val (includeHeader, rest) = _include_header(args)
    normalizeServerEmulatorArgs(rest) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val engine = new HttpExecutionEngine(buildSubsystem())
            val res = engine.execute(req)
            if (includeHeader) {
              _print_with_header(res)
            } else {
              _print_body(res)
            }
            sys.exit(0)
          case Consequence.Failure(conclusion) =>
            Console.err.println(conclusion.message)
            sys.exit(1)
        }
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        sys.exit(1)
    }
  }

  def run(args: Array[String]): Unit = {
    // TODO use Request.parseArgs
    val (backendoption, actualargs) = _log_backend(args)
    if (actualargs.isEmpty) {
      _print_usage()
      return
    }
    val r: Consequence[Request] =
      Request.parseArgs(RequestDefinition(), actualargs)
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
            startServer(actualargs.drop(1))
          case Some(RunMode.Client) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Client))
            executeClient(actualargs.drop(1))
          case Some(RunMode.Command) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Command))
            executeCommand(actualargs.drop(1))
          case Some(RunMode.ServerEmulator) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.ServerEmulator))
            executeServerEmulator(actualargs.drop(1))
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
          case RunMode.ServerEmulator => LogBackend.NopLogBackend
        }
    }
  }

  private def _install_log_backend(
    backend: LogBackend
  ): Unit = {
    LogBackendHolder.install(backend)
  }

  private def _print_response(res: Response): Unit = {
    res match {
      case Response.Scalar(value) => println(value.toString)
      case other => println(other.toString)
    }
  }

  private def _to_request(args: Array[String]): Consequence[Request] = {
    parseCommandArgs(args)
  }

  private[cli] def parseCommandArgs(
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.failure("command name is required")
    } else {
      _parse_component_service_operation(args.toIndexedSeq).map {
        case (component, service, operation) =>
          Request.of(
            component = component,
            service = service,
            operation = operation,
            arguments = Nil,
            switches = Nil,
            properties = Nil
          )
      }
    }
  }

  private def _parse_component_service_operation(
    args: Seq[String]
  ): Consequence[(String, String, String)] = {
    args.toVector match {
      case Vector(component, service, operation, _*) =>
        Consequence.success((component, service, operation))
      case Vector(single) =>
        _parse_component_service_operation_string(single)
      case _ =>
        Consequence.failure("command must be component service operation or component.service.operation")
    }
  }

  private def _parse_component_service_operation_string(
    s: String
  ): Consequence[(String, String, String)] = {
    if (s.contains("/")) {
      s.split("/").toVector.filter(_.nonEmpty) match {
        case Vector(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.failure("command path must be /component/service/operation")
      }
    } else {
      s.split("\\.") match {
        case Array(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.failure("command must be component.service.operation")
      }
    }
  }

  private def _include_header(
    args: Array[String]
  ): (Boolean, Seq[String]) = {
    var includeHeader = false
    val rest = args.filter { arg =>
      if (arg == "-i" || arg == "--include") {
        includeHeader = true
        false
      } else {
        true
      }
    }
    (includeHeader, rest.toIndexedSeq)
  }

  private[cli] def normalizeServerEmulatorArgs(
    args: Seq[String]
  ): Consequence[Seq[String]] = {
    if (args.isEmpty) {
      Consequence.failure("server-emulator requires a path or URL")
    } else if (args.exists(_.contains("://"))) {
      Consequence.success(args)
    } else {
      _parse_component_service_operation(args).map {
        case (component, service, operation) =>
          Seq(s"http://localhost/${component}/${service}/${operation}")
      }
    }
  }

  private def _print_with_header(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val statusLine = s"HTTP ${res.code}"
    val contentType = s"Content-Type: ${res.contentType}"
    Console.out.println(statusLine)
    Console.out.println(contentType)
    Console.out.println()
    _print_body(res)
  }

  private def _print_body(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val body = res.getString.getOrElse(res.show)
    Console.out.println(body)
  }
}

sealed trait RunMode

object RunMode {
  case object Server extends RunMode
  case object Client extends RunMode
  case object Command extends RunMode
  case object ServerEmulator extends RunMode

  def from(p: String): Option[RunMode] = p match {
    case "server" => Some(Server)
    case "client" => Some(Client)
    case "command" => Some(Command)
    case "server-emulator" => Some(ServerEmulator)
    case _ => None
  }
}
