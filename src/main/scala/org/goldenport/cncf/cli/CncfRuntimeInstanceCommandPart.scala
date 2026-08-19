package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.conclusion.cli.CliConclusionRenderer
import org.goldenport.conclusion.presentation.PresentationContext
import org.goldenport.conclusion.presentation.SimpleConclusionPresenter
import org.goldenport.cncf.component.ComponentIdentityCompatibilityObserver
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.http.HttpRequest
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Property
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.http.HttpExecutionEngine
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.path.PathPreNormalizer
import org.goldenport.cncf.cli.help.CommandProtocolHelp
import org.goldenport.cncf.cli.help.CliHelpOperation
import org.goldenport.cncf.cli.help.ClientCommandHelp
import org.goldenport.cncf.cli.help.ServerCommandHelp
import org.goldenport.record.Record
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] trait CncfRuntimeInstanceCommandPart {
  this: GlobalObservable & CncfRuntimeInstanceLifecyclePart & CncfRuntimeInstanceClientPart =>
  def executeCommand(subsystem: Subsystem, req: Request): Int = {
    val primaryargs = _recover_command_args(_command_args_from_request(req), req)
    val args =
      if (primaryargs.nonEmpty) primaryargs
      else _recover_command_args(req.toSubCommand.toArgs, req)
    executeCommand(subsystem, args)
  }

  def executeCommand(subsystem: Subsystem, args: Array[String]): Int = {
    val normalizedargs = CommandProtocolHelp.normalizeArgs(args) match {
      case Left(code) => return code
      case Right(xs) => xs
    }
    val result = _to_request(subsystem, normalizedargs).flatMap { req =>
        subsystem.executeResponseWithMetadata(req)
      }
      result match {
        case Consequence.Success(res) =>
          _print_response(res.response)
          _print_debug_job_reference(res.metadata)
        case Consequence.Failure(conclusion) =>
          _print_error(conclusion)
      }
    _exit_code(result)
  }

  private[cli] def _command_args_from_request(req: Request): Array[String] = {
    val raw = req.toArgs
    raw.toVector match {
      case Vector(head, tail @ _*) if head == RunMode.Command.name =>
        tail.toArray
      case Vector(_, command, tail @ _*) if command == RunMode.Command.name =>
        tail.toArray
      case _ =>
        raw
    }
  }

  private[cli] def _recover_command_args(
    args: Array[String],
    req: Request
  ): Array[String] = {
    val haspathresolutionflag = args.contains("--path-resolution")
    val hasselector = args.exists(x => !x.startsWith("-"))
    if (haspathresolutionflag && !hasselector) {
      req.properties.find(p =>
        p.name == "textus.path-resolution" ||
          p.name == "cncf.path-resolution" ||
          p.name == "path-resolution"
      )
        .map(_.value.toString)
        .filter { x =>
          val lowered = x.trim.toLowerCase
          x.nonEmpty && lowered != "true" && lowered != "yes" && lowered != "on" && lowered != "1"
        }
        .map(x => args :+ x)
        .getOrElse(args)
    } else {
      args
    }
  }

  def executeCommandResponse(
    subsystem: Subsystem,
    args: Array[String]
  ): Consequence[Response] = {
    val normalized = args.toVector match {
      case Vector("command", tail @ _*) => tail.toArray
      case _ => args
    }
    _to_request(subsystem, normalized).flatMap { req =>
      _prepare_filebundle_parameters(subsystem, req).flatMap(subsystem.execute)
    }
  }

  def executeActionResponse(
    subsystem: Subsystem,
    action: org.goldenport.cncf.action.Action
  ): Consequence[OperationResponse] =
    subsystem.executeAction(action)

  private[cli] def _to_request(
    subsystem: Subsystem,
    args: Array[String],
    mode: RunMode = RunMode.Command
  ): Consequence[Request] =
    parseCommandArgs(subsystem, args, mode)

  private[cli] def parseCommandArgs(
    subsystem: Subsystem,
    args: Array[String],
    mode: RunMode = RunMode.Command
  ): Consequence[Request] =
    _extract_runtime_options(args.toIndexedSeq) match { case (runtimeoptions, clean) =>
    _selector_and_arguments(clean, mode).flatMap { case (selector0, tail) =>
      val normalized = _normalize_meta_selector(subsystem, selector0, tail.toVector)
      val aliasresolver =
        if (subsystem.aliasResolver ne AliasResolver.empty) subsystem.aliasResolver
        else _alias_resolver
      val rewritten = PathPreNormalizer.rewriteSelector(normalized._1, mode, aliasresolver)
      val (selector, suffixformat) = _extract_selector_format(rewritten)
      _resolve_selector(subsystem, selector, runtimeoptions, mode) match {
        case Consequence.Success((component, service, operation)) =>
          val parsed = for {
            comp <- subsystem.components.find(_.name == component)
            svc <- comp.protocol.services.services.find(_.name == service)
            opdef <- svc.operations.operations.find(_.name == operation)
          } yield opdef -> _args_parser.parse(opdef, normalized._2.toList)
          val (arguments, switches, properties) = parsed.map { case (opdef, req) =>
            (req.arguments, req.switches, _normalize_nested_properties(opdef, req.properties))
          }.getOrElse {
            (normalized._2.zipWithIndex.map { case (value, index) =>
              Argument(s"arg${index + 1}", value, None)
            }.toList, Nil, Nil)
          }
          val runtimeproperties = _runtime_properties(runtimeoptions, mode).filterNot(p =>
            p.name.equalsIgnoreCase("textus.format") ||
              p.name.equalsIgnoreCase("cncf.format")
          )
          val allproperties = _with_format_property(
            properties ++ runtimeproperties,
            _resolve_format(runtimeoptions, suffixformat, mode)
          )
          Consequence.success(
            Request.of(
              component = component,
              service = service,
              operation = operation,
              arguments = arguments,
              switches = switches,
              properties = allproperties
            )
          )
        case Consequence.Failure(conclusion) =>
          Consequence.Failure(conclusion)
      }
    }}

  private[cli] def _normalize_nested_properties(
    opdef: org.goldenport.protocol.spec.OperationDefinition,
    properties: List[Property]
  ): List[Property] = {
    val parameternames = opdef.specification.request.parameters.map(_.name).toSet
    val directnames = properties.map(_.name).toSet
    val nested = properties.flatMap { p =>
      _split_nested_property_name(p.name).collect {
        case (root, path) if parameternames.contains(root) && !directnames.contains(root) =>
          root -> (p.name, path, p.value)
      }
    }
    if (nested.isEmpty) {
      properties
    } else {
      val foldednames = nested.map(_._2._1).toSet
      val folded = nested.groupBy(_._1).toVector.sortBy(_._1).map { case (root, values) =>
        Property(root, _nested_record(values.map { case (_, (_, path, value)) => path -> value }.toVector), None)
      }
      properties.filterNot(p => foldednames.contains(p.name)) ++ folded
    }
  }

  private[cli] def _split_nested_property_name(
    name: String
  ): Option[(String, Vector[String])] =
    name.split("\\.").toVector.filter(_.nonEmpty) match {
      case root +: rest if rest.nonEmpty => Some(root -> rest)
      case _ => None
    }

  private[cli] def _nested_record(
    entries: Vector[(Vector[String], Any)]
  ): Record =
    Record.data(
      entries.groupBy(_._1.head).toVector.sortBy(_._1).map { case (key, values) =>
        val tails = values.map { case (path, value) => path.tail -> value }
        val value =
          if (tails.forall(_._1.isEmpty)) tails.last._2
          else _nested_record(tails.filter(_._1.nonEmpty))
        key -> value
      }*
    )

  private[cli] def _resolve_selector(
    subsystem: Subsystem,
    selector: String,
    options: RuntimeOptionsParser.Options,
    mode: RunMode
  ): Consequence[(String, String, String)] = {
    val usepathresolution = mode == RunMode.Command && options.pathResolutionCommand
    if (usepathresolution) {
      _resolve_selector_with_path_resolution(subsystem, selector)
    } else {
      _resolve_selector_with_operation_resolver(subsystem, selector)
    }
  }

  private[cli] def _resolve_selector_with_path_resolution(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    CncfRuntime._resolve_selector_with_path_resolution_impl(subsystem, selector)
  }

  private[cli] def _resolve_selector_with_operation_resolver(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    val resolved = subsystem.resolver.resolveWithNotices(selector)
    subsystem.globalRuntimeContextOption.foreach(
      context => ComponentIdentityCompatibilityObserver.observe(context.assemblyReport, resolved.notices)
    )
    resolved.result match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Consequence.success((component, service, operation))
      case ResolutionResult.NotFound(stage, input) =>
        Consequence.operationNotFound(s"${stage.toString.toLowerCase}:$input")
      case ResolutionResult.Ambiguous(input, candidates) =>
        Consequence.argumentInvalid(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
      case ResolutionResult.Invalid(reason) =>
        Consequence.argumentInvalid(s"invalid selector: $reason")
    }
  }

  private[cli] def _extract_selector_format(
    selector: String
  ): (String, Option[String]) = {
    val normalized = selector.trim
    if (normalized.isEmpty) {
      (normalized, None)
    } else {
      val lower = normalized.toLowerCase
      if (lower.endsWith(".json")) {
        (normalized.dropRight(5), Some("json"))
      } else if (lower.endsWith(".yaml")) {
        (normalized.dropRight(5), Some("yaml"))
      } else if (lower.endsWith(".text")) {
        (normalized.dropRight(5), Some("text"))
      } else {
        (normalized, None)
      }
    }
  }

  private[cli] def _with_format_property(
    properties: List[Property],
    format: String
  ): List[Property] = {
    val withoutformat = properties.filterNot(p =>
      p.name.equalsIgnoreCase("textus.format") || p.name.equalsIgnoreCase("cncf.format")
    )
    withoutformat :+ Property("textus.format", format, None)
  }

  private[cli] def _resolve_format(
    options: RuntimeOptionsParser.Options,
    suffixformat: Option[String],
    mode: RunMode
  ): String =
    options.format
      .orElse(if (options.json) Some("json") else None)
      .orElse(suffixformat)
      .getOrElse(RuntimeDefaults.defaultFormat(mode))

  private[cli] def _normalize_meta_selector(
    subsystem: Subsystem,
    selector: String,
    tail: Vector[String]
  ): (String, Vector[String]) = {
    val segments = selector.split("\\.").toVector.filter(_.nonEmpty)
    segments match {
      case Vector("help") =>
        _normalize_meta_selector(subsystem, "meta.help", tail)
      case head +: _ if head == "help" =>
        (selector, tail)
      case Vector("meta", operation) =>
        _default_meta_component_name(subsystem) match {
          case Some(componentname) =>
            (s"$componentname.meta.$operation", tail)
          case None =>
            (selector, tail)
        }
      case Vector(component, "meta", operation) =>
        if (operation == "help")
          (s"$component.meta.help", component +: tail)
        else
          (s"$component.meta.$operation", tail)
      case Vector(component, service, "meta", operation) =>
        (s"$component.meta.$operation", s"$component.$service" +: tail)
      case _ =>
        (selector, tail)
    }
  }

  private[cli] def _default_meta_component_name(
    subsystem: Subsystem
  ): Option[String] =
    subsystem.components.sortBy(_.name).headOption.map(_.name)

  private[cli] def _extract_runtime_options(
    args: Seq[String]
  ): (RuntimeOptionsParser.Options, Seq[String]) =
    RuntimeOptionsParser.extract(args)

  private[cli] def _runtime_properties(
    options: RuntimeOptionsParser.Options,
    mode: RunMode = RunMode.Command
  ): List[Property] =
    RuntimeOptionsParser.properties(options, mode)

  private[cli] def _alias_resolver: AliasResolver =
    GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)

  private[cli] def _selector_and_arguments(
    args: Seq[String],
    mode: RunMode
  ): Consequence[(String, Seq[String])] = {
    args.toVector match {
      case Vector() =>
        Consequence.argumentMissing("command")
      case Vector(single, rest @ _*) if single.contains("/") =>
        _selector_from_path(single, "/").map(_ -> rest.toVector)
      case Vector(single, rest @ _*) if single.contains(".") =>
        Consequence.success((single, rest.toVector))
      case Vector(component, service, operation, rest @ _*) if mode != RunMode.Command =>
        Consequence.success((s"$component.$service.$operation", rest.toVector))
      case Vector(single, rest @ _*) =>
        Consequence.success((single, rest.toVector))
    }
  }

  private[cli] def _selector_from_path(
    value: String,
    delimiter: String
  ): Consequence[String] = {
    val segments = value.split(delimiter).toVector.filter(_.nonEmpty)
    if (segments.size == 3) {
      Consequence.success(segments.mkString("."))
    } else {
      delimiter match {
        case "/" => Consequence.argumentInvalid("command path must be /component/service/operation")
        case "." => Consequence.argumentInvalid("command must be component.service.operation")
        case _ => Consequence.argumentInvalid("command selector is invalid")
      }
    }
  }

  def executeServerEmulator(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    val runtimeconfig = RuntimeConfig.from(subsystem.configuration)
    val (includeheader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest, runtimeconfig.serverEmulatorBaseUrl) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            HttpExecutionEngine.Factory.forRuntime(subsystem) match {
              case Consequence.Success(engine) =>
                val res = engine.execute(req)
                if (includeheader) {
                  _print_with_header(res)
                } else {
                  _print_body(res)
                }
                Consequence.success(res)
              case Consequence.Failure(conclusion) =>
                _print_error(conclusion)
                Consequence.Failure(conclusion)
            }
          case Consequence.Failure(conclusion) =>
            _print_error(conclusion)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        Consequence.Failure(conclusion)
    }
    _exit_code(result)
  }

  private[cli] def _include_header(
    args: Array[String]
  ): (Boolean, Seq[String]) = {
    var includeheader = false
    val rest = args.filter { arg =>
      if (arg == "-i" || arg == "--include") {
        includeheader = true
        false
      } else {
        true
      }
    }
    (includeheader, rest.toIndexedSeq)
  }

  private[cli] def normalizeServerEmulatorArgs(
    args: Seq[String],
    baseurl: String
  ): Consequence[Seq[String]] = {
    if (args.isEmpty) {
      Consequence.argumentMissing("server-emulator path/url")
    } else if (args.exists(_.contains("://"))) {
      Consequence.success(args)
    } else {
      _parse_component_service_operation(args).map {
        case (component, service, operation) =>
          Seq(_server_emulator_url(baseurl, component, service, operation))
      }
    }
  }

  private[cli] def _server_emulator_url(
    baseurl: String,
    component: String,
    service: String,
    operation: String
  ): String = {
    val trimmed = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    s"${trimmed}/${component}/${service}/${operation}"
  }

  private[cli] def _parse_component_service_operation(
    args: Seq[String]
  ): Consequence[(String, String, String)] = {
    args.toVector match {
      case Vector(component, service, operation, _*) =>
        Consequence.success((component, service, operation))
      case Vector(single) if single.contains("/") || single.contains(".") =>
        _parse_component_service_operation_string(single)
      case _ =>
        Consequence.argumentInvalid("command must be component service operation or component.service.operation")
    }
  }

  private[cli] def _parse_component_service_operation_string(
    s: String
  ): Consequence[(String, String, String)] = {
    if (s.contains("/")) {
      s.split("/").toVector.filter(_.nonEmpty) match {
        case Vector(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.argumentInvalid("command path must be /component/service/operation")
      }
    } else {
      s.split("\\.").toVector match {
        case parts if parts.length >= 3 =>
          Consequence.success((
            parts.dropRight(2).mkString("."),
            parts(parts.length - 2),
            parts.last
          ))
        case _ =>
          Consequence.argumentInvalid("command must be component.service.operation")
      }
    }
  }

  private[cli] def _print_with_header(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val statusline = s"HTTP ${res.code}"
    val contenttype = s"Content-Type: ${res.contentType}"
    Console.out.println(statusline)
    Console.out.println(contenttype)
    Console.out.println()
    _print_body(res)
  }

  private[cli] def _print_body(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val body = res.getString.getOrElse(res.show)
    Console.out.println(body)
  }

  def executeScript(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    val result = _to_request_script(subsystem, args).flatMap { req =>
      subsystem.execute(req)
    }
    result match {
      case Consequence.Success(res) =>
        _print_response(res)
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
    _exit_code(result)
  }

  private[cli] def _to_request_script(
    subsystem: Subsystem,
    args: Array[String]
  ) = {
    parseCommandArgs(subsystem, args, RunMode.Script) match {
      case success @ Consequence.Success(_) => success
      case _ =>
        val in = args.toVector
        (in.lift(0), in.lift(1), in.lift(2)) match {
          case (Some(a), Some(b), Some(c))
              if a.equalsIgnoreCase("script") &&
                b.equalsIgnoreCase("default") &&
                c.equalsIgnoreCase("run") =>
            _to_request(subsystem, args, RunMode.Script)
          case _ =>
            val xs = Vector("org.goldenport.cncf.Script/DEFAULT/RUN") ++ in
            _to_request(subsystem, xs.toArray, RunMode.Script)
        }
    }
  }

  private[cli] def _exit_code(c: Consequence[_]): Int =
    c match {
      case Consequence.Success(_) => 0
      case Consequence.Failure(conclusion) =>
        val _ = conclusion
        // TODO When Conclusion/Status supports Long (or an explicit exit/detail code),
        // map it here and return that value.
        1
    }

  private[cli] def _execute_top_level_help(args: Array[String]): Option[Int] =
    args.toVector match {
      case Vector("help") => Some(CliHelpOperation.execute())
      case Vector("server", "help") => Some(ServerCommandHelp.execute())
      case Vector("client", "help") => Some(ClientCommandHelp.execute())
      case _ => None
    }

  private[cli] def _normalize_help_aliases(args: Array[String]): Array[String] =
    args.toVector match {
      case Vector(flag) if _help_flags.contains(flag) =>
        Array("help")
      case Vector(command, flag) if _help_flags.contains(flag) =>
        Array(command, "help")
      case _ =>
        args
    }

  private[cli] def _print_usage(): Unit = {
    val text =
      """Usage:
        |  cncf server
        |  cncf client
        |  cncf command <cmd>
        |
        |Examples:
        |  cncf server
        |  cncf client http get
        |  cncf command org.goldenport.cncf.Admin.system.ping
        |  cncf command org.goldenport.cncf.Admin.deployment.securityMermaid
        |  cncf command org.goldenport.cncf.Admin.deployment.securityMarkdown
        |
        |Log backend behavior:
        |  command / client : no logs by default
        |  server           : SLF4J logging enabled
        |  --log-backend=stdout|stderr|nop|slf4j overrides defaults
        |
        |Run 'cncf help' for more information.
        |""".stripMargin
    _print_error(text)
  }
  private[cli] def _print_response(res: Response): Unit =
    println(res.print)

  private[cli] def _print_debug_job_reference(
    metadata: RuntimeContext.ExecutionMetadata
  ): Unit =
    metadata.debugJobId
      .filter(_.nonEmpty)
      .foreach(_print_debug_job_reference)

  private[cli] def _print_debug_job_reference(
    response: OperationResponse
  ): Unit =
    response match {
      case OperationResponse.Http(http) =>
        http.headerValue("X-Textus-Job-Id").foreach(_print_debug_job_reference)
      case _ =>
        ()
    }

  private[cli] def _print_debug_job_reference(
    jobid: String
  ): Unit =
    Console.err.println(s"Debug job: ${jobid} (/web/system/admin/jobs/${jobid})")

  private[cli] def _request_debug_trace_job(
    req: Request
  ): Boolean =
    req.properties.exists { p =>
      _is_debug_trace_job_key(p.name) && _is_truthy(p.value.toString)
    }

  private[cli] def _is_debug_trace_job_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.debugTraceJobKey ||
      key == RuntimeConfig.runtimeDebugTraceJobKey ||
      key == "cncf.debug.trace-job" ||
      key == "cncf.runtime.debug.trace-job"

  private[cli] def _is_client_debug_passthrough_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.debugCallTreeKey ||
      key == RuntimeConfig.runtimeDebugCallTreeKey ||
      key == RuntimeConfig.debugSaveCallTreeKey ||
      key == RuntimeConfig.runtimeDebugSaveCallTreeKey ||
      key == "cncf.debug.calltree" ||
      key == "cncf.runtime.debug.calltree" ||
      key == "cncf.debug.save-calltree" ||
      key == "cncf.runtime.debug.save-calltree" ||
      _is_debug_trace_job_key(key)

  private[cli] def _is_truthy(
    value: String
  ): Boolean = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
  }

  private[cli] def _is_test_runtime: Boolean =
    sys.props.get("textus.test").exists(_is_truthy)

  private[cli] def _print_operation_response(res: OperationResponse): Unit = {
    res match {
      case OperationResponse.Http(http) =>
        val body = http.getString.getOrElse(http.print)
        Console.out.println(body)
      case OperationResponse.RecordResponse(record) =>
        _print_response(Response.Yaml(RuntimeContext.Context.default.transformRecord(record).toYamlString))
      case _ =>
        _print_response(res.toResponse)
    }
    ()
  }

  private[cli] def _print_error(c: Conclusion): Unit = {
    val presented = new SimpleConclusionPresenter().present(c, PresentationContext("en"))
    Console.err.println(CliConclusionRenderer.render(presented)._2)
  }

  private[cli] def _print_error(message: String): Unit = {
    Console.err.println(message)
  }
}
