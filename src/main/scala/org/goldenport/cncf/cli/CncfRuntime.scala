package org.goldenport.cncf.cli

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.client.{ClientComponent, GetQuery, PostCommand}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{Component, ComponentInit, PingRuntime}
import org.goldenport.cncf.context.SystemContext
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.http.{Http4sHttpServer, HttpExecutionEngine}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}

/*
 * @since   Jan.  7, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime {
  def buildSubsystem(
    extraComponents: Subsystem => Seq[Component] = _ => Nil,
    mode: Option[RunMode] = None
  ): Subsystem = {
    val modeLabel = mode.map(_.name)
    val subsystem = DefaultSubsystemFactory.default(modeLabel)
    val extras = extraComponents(subsystem)
    if (extras.nonEmpty) {
      subsystem.add(extras)
    }
    modeLabel.foreach { label =>
      _apply_system_context(subsystem, label)
    }
    subsystem
  }

  def startServer(args: Array[String]): Unit = {
    val engine = new HttpExecutionEngine(buildSubsystem(mode = Some(RunMode.Server)))
    val server = new Http4sHttpServer(engine)
    server.start(args)
  }

  def startServer(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Unit = {
    val engine = new HttpExecutionEngine(buildSubsystem(extraComponents, Some(RunMode.Server)))
    val server = new Http4sHttpServer(engine)
    server.start(args)
  }

  def executeClient(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Client))
    val result = _client_component(subsystem).flatMap { component =>
      parseClientArgs(args).flatMap { req =>
        _client_action_from_request(req).flatMap { action =>
          component.execute(action)
        }
      }
    }
    result match {
      case Consequence.Success(res) =>
        _print_operation_response(res)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
    }
    _exit_code(result)
  }

  def executeClient(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client))
    val result = _client_component(subsystem).flatMap { component =>
      parseClientArgs(args).flatMap { req =>
        _client_action_from_request(req).flatMap { action =>
          component.execute(action)
        }
      }
    }
    result match {
      case Consequence.Success(res) =>
        _print_operation_response(res)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
    }
    _exit_code(result)
  }

  def executeCommand(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Command))
    val result = _to_request(args).flatMap { req =>
      subsystem.execute(req)
    }
    result match {
      case Consequence.Success(res) =>
        _print_response(res)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
    }
    _exit_code(result)
  }

  def executeCommand(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command))
    val result = _to_request(args).flatMap { req =>
      subsystem.execute(req)
    }
    result match {
      case Consequence.Success(res) =>
        _print_response(res)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
    }
    _exit_code(result)
  }

  def executeServerEmulator(args: Array[String]): Int = {
    val (includeHeader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val engine = new HttpExecutionEngine(buildSubsystem(mode = Some(RunMode.ServerEmulator)))
            val res = engine.execute(req)
            if (includeHeader) {
              _print_with_header(res)
            } else {
              _print_body(res)
            }
            Consequence.success(res)
          case Consequence.Failure(conclusion) =>
            Console.err.println(conclusion.message)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        Consequence.Failure(conclusion)
    }
    _exit_code(result)
  }

  def executeServerEmulator(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val (includeHeader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val engine = new HttpExecutionEngine(buildSubsystem(extraComponents, Some(RunMode.ServerEmulator)))
            val res = engine.execute(req)
            if (includeHeader) {
              _print_with_header(res)
            } else {
              _print_body(res)
            }
            Consequence.success(res)
          case Consequence.Failure(conclusion) =>
            Console.err.println(conclusion.message)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        Consequence.Failure(conclusion)
    }
    _exit_code(result)
  }

  def executeScript(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Consequence[Response] = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Script))
    _to_request_script(args).flatMap { req =>
      subsystem.execute(req)
    }
  }

  private def _to_request_script(args: Array[String]) = {
    val in = args.toVector
    (in.lift(0), in.lift(1), in.lift(2)) match {
      case (Some("SCRIPT"), Some("DEFAULT"), Some("RUN")) =>
        parseCommandArgs(args)
      case _ =>
        val xs = Vector("SCRIPT", "DEFAULT", "RUN") ++ in
        parseCommandArgs(xs.toArray)
    }
  }

  def runExitCode(args: Array[String]): Int =
    run(args)

  def runWithExtraComponents(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val (backendoption, actualargs) = _log_backend(args)
    if (actualargs.isEmpty) {
      _print_usage()
      return 2
    }
    val r: Consequence[Request] =
      Request.parseArgs(RequestDefinition(), actualargs)
    r match {
      case Consequence.Success(req) =>
        if (req.operation.isEmpty) {
          _print_usage()
          return 2
        }
        val mode = RunMode.from(req.operation)
        mode match {
          case Some(RunMode.Server) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Server))
            startServer(actualargs.drop(1), extraComponents)
            0
          case Some(RunMode.Client) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Client))
            executeClient(actualargs.drop(1), extraComponents)
          case Some(RunMode.Command) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Command))
            executeCommand(actualargs.drop(1), extraComponents)
          case Some(RunMode.ServerEmulator) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.ServerEmulator))
            executeServerEmulator(actualargs.drop(1), extraComponents)
          case _ =>
            _print_usage()
            2
        }
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  def run(args: Array[String]): Int = {
    // TODO use Request.parseArgs
    val (backendoption, actualargs) = _log_backend(args)
    if (actualargs.isEmpty) {
      _print_usage()
      return 2
    }
    val r: Consequence[Request] =
      Request.parseArgs(RequestDefinition(), actualargs)
    r match {
      case Consequence.Success(req) =>
        if (req.operation.isEmpty) {
          _print_usage()
          return 2
        }
        val mode = RunMode.from(req.operation)
        mode match {
          case Some(RunMode.Server) =>
            _install_log_backend(_decide_backend(backendoption, RunMode.Server))
            startServer(actualargs.drop(1))
            0
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
            3
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion.message)
        _print_usage()
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  private def _exit_code(c: Consequence[_]): Int =
    c match {
      case Consequence.Success(_) => 0
      case Consequence.Failure(conclusion) =>
        val _ = conclusion
        // TODO When Conclusion/Status supports Long (or an explicit exit/detail code),
        // map it here and return that value.
        1
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
        |  cncf client http get
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

  private def _print_operation_response(res: OperationResponse): Unit = {
    res match {
      case OperationResponse.Http(http) =>
        val body = http.getString.getOrElse(http.show)
        Console.out.println(body)
      case _ =>
        _print_response(res.toResponse)
    }
  }

  private def _client_component(
    subsystem: Subsystem
  ): Consequence[ClientComponent] =
    subsystem.components.collectFirst { case c: ClientComponent => c } match {
      case Some(component) => Consequence.success(component)
      case None => Consequence.failure("client component not available")
    }

  private def _to_request(args: Array[String]): Consequence[Request] = {
    parseCommandArgs(args)
  }

  private def _apply_system_context(
    subsystem: Subsystem,
    mode: String
  ): Unit = {
    val system = PingRuntime.systemContext(
      mode = mode,
      subsystem = subsystem.name,
      runtimeVersion = CncfVersion.current,
      subsystemVersion = subsystem.version
    )
    subsystem.components.foreach(_.withSystemContext(system))
  }

  def parseClientArgs(
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.failure("client command is required")
    } else {
      _extract_baseurl(args.toIndexedSeq).flatMap {
        case (baseurlopt, rest0) =>
          _extract_body(rest0).flatMap {
            case (bodyopt, rest1) =>
              _parse_client_command(rest1, bodyopt).map {
                case (operation, path) =>
                  val baseurl = baseurlopt.getOrElse("http://localhost:8080")
                  Request.of(
                    component = "client",
                    service = "http",
                    operation = operation,
                    arguments = _client_arguments(path),
                    switches = Nil,
                    properties = _client_properties(baseurl, bodyopt)
                  )
              }
          }
      }
    }
  }

  // TODO duplicate parseClientAction
  private def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] = {
    if (req.component.contains("client") && req.service.contains("http")) {
      _client_path_from_request(req).flatMap { path =>
        val baseurl = _client_baseurl_from_request(req)
        val url = _build_client_url(baseurl, path)
        req.operation match {
          case "post" =>
            _client_body_from_request(req).map { body =>
              new PostCommand(
                "system.ping", // TODO generic
                HttpRequest.fromUrl(
                  method = HttpRequest.POST,
                  url = new URL(url),
                  body = body
                )
              )
            }
          case "get" =>
            Consequence.success(
              new GetQuery(
                "system.ping",
                HttpRequest.fromUrl(
                  method = HttpRequest.GET,
                  url = new URL(url)
                )
              )
            )
          case other =>
            Consequence.failure(s"client http operation not supported: ${other}")
        }
      }
    } else {
      Consequence.failure("client http request is required")
    }
  }

  private def _client_baseurl_from_request(
    req: Request
  ): String =
    req.properties.find(_.name == "baseurl").map(_.value.toString)
      .getOrElse("http://localhost:8080")

  private def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.failure("client http path is required")
    }

  private def _client_body_from_request(
    req: Request
  ): Consequence[Option[Bag]] =
    req.properties.find(_.name == "-d") match {
      case Some(p) =>
        p.value match {
          case b: Bag => Consequence.success(Some(b))
          case s: String => Consequence.success(Some(Bag.text(s, StandardCharsets.UTF_8)))
          case _ => Consequence.failure("client -d must be a Bag or String")
        }
      case None =>
        Consequence.success(None)
    }

  // TODO duplicate _client_action_from_request
  def parseClientAction(
    args: Array[String]
  ): Consequence[org.goldenport.cncf.action.Action] = {
    if (args.isEmpty) {
      Consequence.failure("client command is required")
    } else {
      _extract_baseurl(args.toIndexedSeq).flatMap {
        case (baseurlopt, rest0) =>
          _extract_body(rest0).flatMap {
            case (bodyopt, rest1) =>
              _parse_client_command(rest1, bodyopt).map {
                case (operation, path) =>
                  val baseurl = baseurlopt.getOrElse("http://localhost:8080")
                  val url = _build_client_url(baseurl, path)
                  operation match {
                    case "post" =>
                      new PostCommand(
                        "system.ping", // TODO generic
                        HttpRequest.fromUrl(
                          method = HttpRequest.POST,
                          url = new URL(url),
                          body = bodyopt
                        )
                      )
                    case _ =>
                      new GetQuery(
                        "system.ping",
                        HttpRequest.fromUrl(
                          method = HttpRequest.GET,
                          url = new URL(url)
                        )
                      )
                  }
              }
          }
      }
    }
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
            arguments = args.toList.drop(3).zipWithIndex.map {
              (x, i) => Argument(s"arg${i + 1}", x)
            },
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
      case Vector(single) if single.contains("/") || single.contains(".") =>
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

  private def _parse_client_path(
    args: Seq[String]
  ): Consequence[String] = {
    if (args.isEmpty) {
      Consequence.failure("client path is required")
    } else {
      args.toVector match {
        case Vector(single) =>
          Consequence.success(_normalize_path(single))
        case multiple =>
          Consequence.success(_normalize_path(multiple.mkString("/")))
      }
    }
  }

  private def _extract_body(
    args: Seq[String]
  ): Consequence[(Option[Bag], Seq[String])] = {
    var body: Option[Bag] = None
    val buffer = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg.startsWith("-d=")) {
        _body_value_to_bag(arg.drop(3)) match {
          case Consequence.Success(b) => body = Some(b)
          case Consequence.Failure(c) => return Consequence.failure(c.message)
        }
      } else if (arg == "-d") {
        if (i + 1 >= args.length) {
          return Consequence.failure("client -d requires a value")
        }
        _body_value_to_bag(args(i + 1)) match {
          case Consequence.Success(b) => body = Some(b)
          case Consequence.Failure(c) => return Consequence.failure(c.message)
        }
        i += 1
      } else {
        buffer += arg
      }
      i += 1
    }
    Consequence.success((body, buffer.result()))
  }

  private def _extract_baseurl(
    args: Seq[String]
  ): Consequence[(Option[String], Seq[String])] = {
    var baseurl: Option[String] = None
    val buffer = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (arg.startsWith("--baseurl=")) {
        baseurl = Some(arg.stripPrefix("--baseurl="))
      } else if (arg == "--baseurl") {
        if (i + 1 >= args.length) {
          return Consequence.failure("client --baseurl requires a value")
        }
        baseurl = Some(args(i + 1))
        i += 1
      } else {
        buffer += arg
      }
      i += 1
    }
    Consequence.success((baseurl, buffer.result()))
  }

  private def _normalize_path(path: String): String = {
    val normalized = if (path.contains(".")) path.replace(".", "/") else path
    if (normalized.startsWith("/")) normalized else s"/${normalized}"
  }

  private def _build_client_url(
    baseurl: String,
    path: String
  ): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private def _parse_client_command(
    args: Seq[String],
    body: Option[Bag]
  ): Consequence[(String, String)] = {
    args.toVector match {
      case Vector("http", operation, rest @ _*) =>
        _parse_http_operation(operation).flatMap { op =>
          if (rest.isEmpty) {
            Consequence.failure("client http path is required")
          } else {
            _parse_client_path(rest).flatMap { path =>
              if (op == "get" && body.isDefined) {
                Consequence.failure("client http get does not accept -d")
              } else {
                Consequence.success((op, path))
              }
            }
          }
        }
      case Vector("http") =>
        Consequence.failure("client http requires operation and path")
      case _ =>
        _parse_client_path(args).map { path =>
          val operation = body.map(_ => "post").getOrElse("get")
          (operation, path)
        }
    }
  }

  private def _parse_http_operation(
    operation: String
  ): Consequence[String] = {
    val lower = operation.toLowerCase
    lower match {
      case "get" | "post" => Consequence.success(lower)
      case _ => Consequence.failure("client http operation must be get or post")
    }
  }

  private def _body_value_to_bag(
    value: String
  ): Consequence[Bag] = {
    if (value.startsWith("@")) {
      val path = value.drop(1)
      if (path.isEmpty) {
        Consequence.failure("client -d @ requires a file path")
      } else {
        _bag_from_file(path)
      }
    } else {
      Consequence.success(Bag.text(value, StandardCharsets.UTF_8))
    }
  }

  private def _bag_from_file(
    path: String
  ): Consequence[Bag] = {
    try {
      val bytes = Files.readAllBytes(Paths.get(path))
      val text = new String(bytes, StandardCharsets.UTF_8)
      Consequence.success(Bag.text(text, StandardCharsets.UTF_8))
    } catch {
      case e: Exception =>
        Consequence.failure(s"client -d file read failed: ${e.getMessage}")
    }
  }

  private def _client_properties(
    baseurl: String,
    body: Option[Bag]
  ): List[Property] =
    Property("baseurl", baseurl, None) ::
      body.map(v => Property("-d", v, None)).toList

  private def _client_arguments(
    path: String
  ): List[Argument] =
    List(Argument("path", path, None))

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

enum RunMode(val name: String) {
  case Server extends RunMode("server")
  case Client extends RunMode("client")
  case Command extends RunMode("command")
  case Script extends RunMode("script")
  case ServerEmulator extends RunMode("server-emulator")
}
object RunMode {
  def from(p: String): Option[RunMode] = values.find(_.name == p)
}
