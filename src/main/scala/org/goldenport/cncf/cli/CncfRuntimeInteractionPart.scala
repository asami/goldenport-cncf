package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.conclusion.cli.CliConclusionRenderer
import org.goldenport.conclusion.presentation.PresentationContext
import org.goldenport.conclusion.presentation.SimpleConclusionPresenter
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentIdentityCompatibilityAdapter
import org.goldenport.cncf.component.ComponentIdentityCompatibilityObserver
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.http.HttpResponse
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Property
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.datatype.MimeBody
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.Multiplicity
import org.goldenport.schema.ValueDomain
import org.goldenport.schema.XString
import org.goldenport.value.BaseContent
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resolver.CanonicalPath
import org.goldenport.cncf.resolver.PathResolution
import org.goldenport.cncf.resolver.PathResolutionResult
import org.goldenport.cncf.cli.help.CliHelpOperation
import org.goldenport.cncf.cli.help.ClientCommandHelp
import org.goldenport.cncf.cli.help.ServerCommandHelp
import org.goldenport.cncf.observability.LogLevel
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.observability.VisibilityPolicy
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
private[cli] trait CncfRuntimeInteractionPart {
  this: GlobalObservable & CncfRuntimeBootstrapPart & CncfRuntimeDiscoveryPart =>
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

  private[cli] def _print_error(c: Conclusion): Unit = {
    val presented = new SimpleConclusionPresenter().present(c, PresentationContext("en"))
    Console.err.println(CliConclusionRenderer.render(presented)._2)
  }

  private[cli] def _print_error(message: String): Unit = {
    Console.err.println(message)
  }

  private[cli] def _request_record_exclude_property(name: String): Boolean =
    name.startsWith("textus.") ||
      name.startsWith("cncf.") ||
      name.startsWith("query.")

  private[cli] def _request_to_record(req: Request): Record =
    req.toRecord(excludeProperty = _request_record_exclude_property)

  private[cli] def _print_usage(): Unit = {
    val text =
      """Usage:
        |  cncf server
        |  cncf client
        |  cncf command <cmd>
        |
        |Examples:
        |  cncf server
        |  cncf client org.goldenport.cncf.Admin.system.ping
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

  private[cli] def _extract_log_options(
    args: Array[String]
  ): (Option[String], Option[String], Array[String]) = {
    // Canonical CLI keys: --textus.logging.backend / --textus.logging.level
    // Other forms are experimental and kept for transitional use.
    var logbackendoption: Option[String] = None
    var logleveloption: Option[String] = None
    val rest = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--log-backend=")) { // experimental
        logbackendoption = Some(current.stripPrefix("--log-backend="))
      } else if (current == "--log-backend" && i + 1 < args.length) { // experimental
        logbackendoption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.logging.backend=")) {
        logbackendoption = Some(current.stripPrefix("--textus.logging.backend="))
      } else if (current == "--textus.logging.backend" && i + 1 < args.length) {
        logbackendoption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.backend=")) { // legacy alias
        logbackendoption = Some(current.stripPrefix("--textus.runtime.logging.backend="))
      } else if (current == "--textus.runtime.logging.backend" && i + 1 < args.length) { // legacy alias
        logbackendoption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.backend=")) { // legacy
        logbackendoption = Some(current.stripPrefix("--cncf.runtime.logging.backend="))
      } else if (current == "--cncf.runtime.logging.backend" && i + 1 < args.length) { // legacy
        logbackendoption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.logging.backend=")) {
        logbackendoption = Some(current.stripPrefix("--cncf.logging.backend="))
      } else if (current == "--cncf.logging.backend" && i + 1 < args.length) {
        logbackendoption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--log-level=")) { // experimental
        logleveloption = Some(current.stripPrefix("--log-level="))
      } else if (current == "--log-level" && i + 1 < args.length) { // experimental
        logleveloption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.logging.level=")) {
        logleveloption = Some(current.stripPrefix("--textus.logging.level="))
      } else if (current == "--textus.logging.level" && i + 1 < args.length) {
        logleveloption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.level=")) { // legacy alias
        logleveloption = Some(current.stripPrefix("--textus.runtime.logging.level="))
      } else if (current == "--textus.runtime.logging.level" && i + 1 < args.length) { // legacy alias
        logleveloption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.level=")) { // legacy
        logleveloption = Some(current.stripPrefix("--cncf.runtime.logging.level="))
      } else if (current == "--cncf.runtime.logging.level" && i + 1 < args.length) { // legacy
        logleveloption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.logging.level=")) {
        logleveloption = Some(current.stripPrefix("--cncf.logging.level="))
      } else if (current == "--cncf.logging.level" && i + 1 < args.length) {
        logleveloption = Some(args(i + 1))
        i = i + 1
      } else {
        rest += current
      }
      i = i + 1
    }
    (logbackendoption, logleveloption, rest.result().toArray)
  }

  private[cli] def _decide_backend(
    overridebackend: Option[String],
    configbackend: Option[String],
    mode: RunMode
  ): LogBackend =
    _backend_from_string(overridebackend, "flag")
      .orElse(_backend_from_string(configbackend, "configuration"))
      .getOrElse(RuntimeDefaults.defaultLogBackend(mode))

  private[cli] def _backend_from_string(
    value: Option[String],
    source: String
  ): Option[LogBackend] =
    value.flatMap { v =>
      LogBackend.fromString(v) match {
        case Some(backend) => Some(backend)
        case None =>
          _print_error(s"Unknown log backend from ${source}: ${v}")
          _print_usage()
          None
      }
    }

  private[cli] def _logging_backend_from_configuration(
    configuration: ResolvedConfiguration
  ): Option[String] = {
    RuntimeConfig.getString(configuration, RuntimeConfig.logBackendKey)
  }

  private[cli] def _log_level_from_configuration(
    configuration: ResolvedConfiguration
  ): Option[String] = {
    RuntimeConfig.getString(configuration, RuntimeConfig.logLevelKey)
  }

  private[cli] def _update_visibility_policy(
    cliloglevel: Option[String],
    configuration: ResolvedConfiguration,
    mode: RunMode
  ): Unit = {
    val levelopt =
      cliloglevel
        .orElse(_log_level_from_configuration(configuration))
        .flatMap(LogLevel.from)
    val level = levelopt.getOrElse(RuntimeDefaults.defaultLogLevel(mode))
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = level))
  }

  private[cli] def _mode_from_args(
    args: Array[String]
  ): RunMode =
    args.headOption.flatMap(RunMode.from).getOrElse(RunMode.Command)

  private[cli] def _install_log_backend(
    backend: LogBackend
  ): Unit = {
    LogBackendHolder.install(backend)
  }

  private[cli] def _run_script(
    args: Array[String],
    extras: Subsystem => Seq[Component],
    subsystemargs: Array[String]
  ): Int = {
    val result = _execute_script(args, subsystemargs, extras)
    result match {
      case Consequence.Success(res) =>
        _print_response(res)
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
    _exit_code(result)
  }

  private[cli] def _print_response(res: Response): Unit =
    println(res.print)

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

  private[cli] def _client_component(
    subsystem: Subsystem
  ): Consequence[ClientComponent] =
    subsystem.components.collectFirst { case c: ClientComponent => c } match {
      case Some(component) =>
        val operations = _component_operation_fqns(subsystem)
        observe_trace(
          s"[client:trace] client component found=${component.name} operations=${_operation_sample(operations)}"
        )
        Consequence.success(component)
      case None =>
        observe_trace("[client:trace] client component not available")
        Consequence.operationNotFound("client component")
    }

  private[cli] def _to_request(
    subsystem: Subsystem,
    args: Array[String],
    mode: RunMode = RunMode.Command
  ): Consequence[Request] =
    parseCommandArgs(subsystem, args, mode)

  // private def _apply_system_context(
  //   subsystem: Subsystem,
  //   mode: String
  // ): Unit = {
  //   val system = SystemContext.empty
  //   subsystem.components.foreach(_.withSystemContext(system))
  // }

  def parseClientArgs(
    subsystem: Subsystem,
    args: Array[String]
  ): Consequence[Request] =
    new CncfRuntime().parseClientArgs(subsystem, args)

  private[cli] def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] =
    new CncfRuntime()._client_action_from_request(req)

  private[cli] def _client_baseurl_from_request(
    req: Request
  ): String =
    new CncfRuntime()._client_baseurl_from_request(req)

  private[cli] def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    new CncfRuntime()._client_path_from_request(req)

  private[cli] def _client_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    new CncfRuntime()._client_mime_body_from_request(req)

  private[cli] def _prepare_filebundle_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] =
    new CncfRuntime()._prepare_filebundle_parameters(operation, req)

  private[cli] def _prepare_filebundle_parameters(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    new CncfRuntime()._prepare_filebundle_parameters(subsystem, req)

  private[cli] def _prepare_filebundle_transport_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] =
    new CncfRuntime()._prepare_filebundle_transport_parameters(operation, req)

  private[cli] def _client_http_body_and_header(
    req: Request
  ): Consequence[(Option[MimeBody], Record)] =
    new CncfRuntime()._client_http_body_and_header(req)

  private[cli] def _client_form_mime_body(
    req: Request
  ): Option[MimeBody] =
    new CncfRuntime()._client_form_mime_body(req)

  private[cli] def _client_form_encoded_payload(
    req: Request
  ): Option[String] =
    new CncfRuntime()._client_form_encoded_payload(req)

  private[cli] def _client_http_parameter_properties(
    req: Request
  ): List[Property] =
    new CncfRuntime()._client_http_parameter_properties(req)

  private[cli] def _is_form_urlencoded(
    body: MimeBody
  ): Boolean =
    new CncfRuntime()._is_form_urlencoded(body)

  private[cli] def _mime_body_from_property_names(
    properties: List[Property],
    names: List[String]
  ): Consequence[Option[MimeBody]] =
    new CncfRuntime()._mime_body_from_property_names(properties, names)

  private[cli] def _mime_body_from_value(
    value: Any
  ): Consequence[MimeBody] =
    new CncfRuntime()._mime_body_from_value(value)

  private[cli] def _mime_body_from_arguments(
    arguments: List[Argument]
  ): Option[MimeBody] =
    new CncfRuntime()._mime_body_from_arguments(arguments)

  private[cli] def parseCommandArgs(
    subsystem: Subsystem,
    args: Array[String],
    mode: RunMode = RunMode.Command
  ): Consequence[Request] =
    new CncfRuntime().parseCommandArgs(subsystem, args, mode)

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
      // OperationResolver is the post-resolution lookup layer.
      _resolve_selector_with_operation_resolver(subsystem, selector)
    }
  }

  private[cli] def _resolve_selector_with_path_resolution(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    _resolve_selector_with_path_resolution_impl(subsystem, selector)
  }

  private[cli] def _resolve_selector_with_path_resolution_impl(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    val registry = subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map { operation =>
          CanonicalPath(comp.componentId.name, service.name, operation.name)
        }
      }
    }.toVector
    val componentids = registry.map(_.component).distinct
    _path_resolution_segments(selector, componentids) match {
      case Left(reason) =>
        Consequence.argumentInvalid(s"path-resolution failed: $reason")
      case Right(segments) =>
        val componentalias = segments.head
        ComponentIdentityCompatibilityAdapter.resolveAliases(
          componentalias,
          ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(subsystem.components),
          ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector
        ) match {
          case result: ComponentIdentityCompatibilityAdapter.Canonical =>
            _resolve_canonical_path(
              segments,
              result.componentid.name,
              registry,
              subsystem.components.collect {
                case comp if comp.origin == ComponentOrigin.Builtin => comp.componentId.name
              }.toSet
            )
          case result: ComponentIdentityCompatibilityAdapter.Adapted =>
            _resolve_canonical_path(
              segments,
              result.componentid.name,
              registry,
              subsystem.components.collect {
                case comp if comp.origin == ComponentOrigin.Builtin => comp.componentId.name
              }.toSet
            )
          case ComponentIdentityCompatibilityAdapter.Rejected(rejection) =>
            Consequence.argumentInvalid(
              s"path-resolution failed: ${rejection.diagnostic}"
            )
        }
    }
  }

  private[cli] def _path_resolution_segments(
    selector: String,
    componentids: Vector[String]
  ): Either[String, Vector[String]] = {
    val normalized = Option(selector).getOrElse("").trim
    if (normalized.isEmpty) {
      Left("selector is required")
    } else if (normalized.contains("/")) {
      val segments = normalized.split("/").toVector.map(_.trim).filter(_.nonEmpty)
      if (segments.isEmpty) Left("selector is required") else Right(segments)
    } else if (normalized.contains("\\")) {
      val segments = normalized.split("\\\\").toVector.map(_.trim).filter(_.nonEmpty)
      if (segments.isEmpty) Left("selector is required") else Right(segments)
    } else {
      val rawsegments = normalized.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)
      val knownprefix = componentids.sortBy(id => -id.length).find { id =>
        normalized == id || normalized.startsWith(s"$id.")
      }
      knownprefix match {
        case Some(componentid) =>
          val suffix = normalized.drop(componentid.length).stripPrefix(".")
          Right(componentid +: suffix.split("\\.").toVector.map(_.trim).filter(_.nonEmpty))
        case None if rawsegments.size >= 4 =>
          Right(Vector(rawsegments.dropRight(2).mkString("."), rawsegments(rawsegments.size - 2), rawsegments.last))
        case None if rawsegments.nonEmpty =>
          Right(rawsegments)
        case None =>
          Left("selector is required")
      }
    }
  }

  private[cli] def _resolve_canonical_path(
    segments: Vector[String],
    componentid: String,
    registry: Vector[CanonicalPath],
    builtins: Set[String]
  ): Consequence[(String, String, String)] = {
    segments match {
      case Vector(_) =>
        val matches = registry.filter(_.component == componentid)
        if (matches.isEmpty) {
          Consequence.argumentInvalid("path-resolution failed: component not found")
        } else if (builtins.exists(NamingConventions.equivalentByNormalized(componentid, _))) {
          Consequence.argumentInvalid("path-resolution failed: builtin components do not allow omission")
        } else {
          val services = matches.map(_.service).distinct
          val operations = matches.map(_.operation).distinct
          if (services.size == 1 && operations.size == 1) {
            val path = matches.head
            Consequence.success((path.component, path.service, path.operation))
          } else {
            Consequence.argumentInvalid("path-resolution failed: component has multiple services or operations")
          }
        }
      case _ =>
        val canonicalselector =
          (componentid +: segments.tail).mkString("/")
        PathResolution.resolve(canonicalselector, registry, builtins) match {
          case PathResolutionResult.Success(path) =>
            Consequence.success((path.component, path.service, path.operation))
          case PathResolutionResult.Failure(reason) =>
            Consequence.argumentInvalid(s"path-resolution failed: $reason")
        }
    }
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
        stage match {
          case ResolutionStage.Component =>
            Consequence.componentNotFound(input)
          case ResolutionStage.Service =>
            Consequence.serviceNotFound(input)
          case ResolutionStage.Operation =>
            Consequence.operationNotFound(input)
        }
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
      p.name.equalsIgnoreCase("textus.format") ||
        p.name.equalsIgnoreCase("textus.output.format") ||
        p.name.equalsIgnoreCase("cncf.format") ||
        p.name.equalsIgnoreCase("cncf.output.format")
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

  private[cli] def _alias_resolver: AliasResolver =
    GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)


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

  private[cli] def _component_operation_fqns(subsystem: Subsystem): Vector[String] =
    subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => s"${comp.name}.${service.name}.${op.name}")
      }
    }.toVector

  private[cli] def _component_names(subsystem: Subsystem): String =
    subsystem.components.map(_.name).mkString(",")

  private[cli] def _operation_sample(operations: Vector[String]): String = {
    if (operations.isEmpty) {
      "none"
    } else {
      val sample = operations.take(10).mkString(",")
      if (operations.size > 10) {
        s"$sample...(+${operations.size - 10})"
      } else {
        sample
      }
    }
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

  private[cli] def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] =
    new CncfRuntime()._parse_client_path(args)

  private[cli] val _client_http_request_definition: RequestDefinition = {
    val base = RequestDefinition.curlLike
    val baseurlparameter = ParameterDefinition(
      content = BaseContent.simple("baseurl"),
      kind = ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)
    )
    RequestDefinition(base.parameters :+ baseurlparameter)
  }

  private[cli] def _parse_client_http(
    operation: String,
    params: Seq[String]
  ): Consequence[(String, List[Property])] =
    new CncfRuntime()._parse_client_http(operation, params)

  private[cli] def _http_tail_properties(
    arguments: List[Argument]
  ): List[Property] =
    new CncfRuntime()._http_tail_properties(arguments)

  private[cli] def _normalize_path(path: String): String =
    new CncfRuntime()._normalize_path(path)

  private[cli] def _build_client_url(
    baseurl: String,
    path: String
  ): String =
    new CncfRuntime()._build_client_url(baseurl, path)

  private[cli] def _append_client_query(
    url: String,
    req: Request
  ): String =
    new CncfRuntime()._append_client_query(url, req)

  // TODO Phase 2.85: Replace this ad-hoc query parameter mapping with OperationDefinition-driven parameter handling.
  private[cli] def _client_query_string(
    req: Request
  ): Option[String] =
    new CncfRuntime()._client_query_string(req)

  private[cli] def _is_http_parameter_property(
    name: String
  ): Boolean =
    new CncfRuntime()._is_http_parameter_property(name)

  private[cli] def _parse_client_command(
    subsystem: Subsystem,
    args: Seq[String]
  ): Consequence[Request] =
    new CncfRuntime()._parse_client_command(subsystem, args)

  private[cli] def _parse_http_operation(
    operation: String
  ): Consequence[String] =
    new CncfRuntime()._parse_http_operation(operation)

  private[cli] def _command_request_to_client_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    new CncfRuntime()._command_request_to_client_request(subsystem, req)

  private[cli] def _request_path(req: Request): String =
    new CncfRuntime()._request_path(req)

  private[cli] def _http_method_for_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[String] =
    new CncfRuntime()._http_method_for_request(subsystem, req)

  private[cli] def _operation_request_definition(
    subsystem: Subsystem,
    req: Request
  ): Consequence[org.goldenport.protocol.spec.OperationDefinition] =
    new CncfRuntime()._operation_request_definition(subsystem, req)

  private[cli] def _framework_option_passthrough(
    args: Seq[String]
  ): List[Property] =
    new CncfRuntime()._framework_option_passthrough(args)

  private[cli] def _is_client_passthrough_framework_key(
    key: String
  ): Boolean =
    new CncfRuntime()._is_client_passthrough_framework_key(key)

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
}
