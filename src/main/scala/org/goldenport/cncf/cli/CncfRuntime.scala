package org.goldenport.cncf.cli

import java.net.{URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.bag.Bag
import org.goldenport.cli.parser.ArgsParser
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolver, ConfigurationSources, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.file.SimpleFileConfigLoader
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.builtin.client.{GetQuery, PostCommand}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{Component, ComponentInit}
import org.goldenport.cncf.config.{ClientConfig, RuntimeConfig, RuntimeDefaults}
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Property, Protocol, ProtocolEngine, Request, Response, Switch}
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.protocol.operation.{OperationResponse, OperationRequest}
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.http.{FakeHttpDriver, Http4sHttpServer, HttpDriver, HttpExecutionEngine, HttpDriverFactory}
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.importer.StartupImport
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.resolver.{CanonicalPath, PathResolution, PathResolutionResult}
import org.goldenport.cncf.cli.RuntimeParameterParser
import org.goldenport.cncf.cli.help.{CliHelpOperation, ClientCommandHelp, CommandProtocolHelp, HelpOperation, ServerCommandHelp}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.goldenport.cncf.observability.global.{GlobalObservable, GlobalObservability, GlobalObservabilityGate, ObservabilityRoot}
import org.goldenport.record.Record

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 * @version Apr.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime extends GlobalObservable {
  private val _configuration_application_name = "cncf"
  private val _help_flags = Set("--help", "-h")
  private val _runtime_service_name = "runtime"
  private lazy val _runtime_parameter_parser = new RuntimeParameterParser()
  private val _args_parser = new ArgsParser(ArgsParser.Config())

  private val _runtime_protocol: Protocol =
    Protocol.Builder()
      .addOperation(_runtime_service_name, RunMode.Server.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Client.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Command.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.ServerEmulator.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Script.name, RequestDefinition(), ResponseDefinition())
      .build()

  private val _runtime_protocol_engine = ProtocolEngine.create(_runtime_protocol)

  private var _global_runtime_context: Option[GlobalRuntimeContext] = None

  private def _reset_global_runtime_context(): Unit =
    _global_runtime_context = None

  private def _create_global_runtime_context(
    runconfig: RuntimeConfig,
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver
  ): GlobalRuntimeContext = {
    val execution = ExecutionContext.create()
    val context = GlobalRuntimeContext.create(
      name = "runtime",
      runconfig,
      configuration,
      observabilityContext = execution.observability,
      aliasResolver
      // httpDriver = runconfig.httpDriver,
      // aliasResolver = aliasResolver,
      // runtimeMode = runconfig.mode,
      // runtimeVersion = CncfVersion.current,
      // subsystemName = GlobalRuntimeContext.SubsystemName,
      // subsystemVersion = CncfVersion.current
    )
    _global_runtime_context = Some(context)
    GlobalRuntimeContext.current = Some(context)
    _initialize_global_observability()
    context
  }

  private def _runtime_scope_context(): ScopeContext =
    _global_runtime_context.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = DefaultSubsystemFactory.subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )
    }

  private def _mark_used_parameters(
    configuration: ResolvedConfiguration
  ): Unit = {
    val keys = Vector(
      "cncf.runtime.logging.backend",
      "cncf.logging.backend",
      "cncf.runtime.logging.level",
      "cncf.logging.level"
    )
    keys.foreach { key =>
      configuration.get[String](key) match {
        case Consequence.Success(Some(_)) =>
          GlobalRuntimeContext.current.foreach(_.resolvedParameters.get(key))
        case _ => ()
      }
    }
  }

  private def _initialize_global_observability(): Unit = {
    if (!GlobalObservability.isInitialized) {
      // Default behavior: rely on whatever backend (and bootstrap buffer) is already configured.
      val backend = LogBackendHolder.backend.getOrElse(LogBackend.StdoutBackend)
      val root =
        ObservabilityRoot(
          engine = ObservabilityEngine,
          gate = GlobalObservabilityGate.allowAll,
          backend = backend
        )
      GlobalObservability.initialize(root)
    }
  }

  private def _alias_resolver(
    configuration: ResolvedConfiguration
  ): AliasResolver =
    AliasLoader.load(configuration.configuration)

  private[cli] def configure_slf4j_simple(
    configuration: ResolvedConfiguration
  ): Unit = {
    def get(key: String): Option[String] =
      ConfigurationAccess.getString(configuration, key)
        .orElse(_legacy_framework_key(key).flatMap(ConfigurationAccess.getString(configuration, _)))

    val logfile = get("textus.runtime.logging.slf4j.file.path")
      .orElse(get("textus.logging.slf4j.file.path"))
      .orElse(get("textus.logging.external.file.path"))
      .getOrElse("target/cncf.d/external.log")
    val level = get("textus.runtime.logging.slf4j.level")
      .orElse(get("textus.logging.slf4j.level"))
      .orElse(get("textus.logging.level"))
      .getOrElse("warn")
    val hikarilevel = get("textus.logging.slf4j.hikari.level").getOrElse("warn")
    val sqlitelevel = get("textus.logging.slf4j.sqlite.level").getOrElse("warn")

    val p = Paths.get(logfile)
    Option(p.getParent).foreach(Files.createDirectories(_))

    System.setProperty("org.slf4j.simpleLogger.logFile", logfile)
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level)
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", hikarilevel)
    System.setProperty("org.slf4j.simpleLogger.log.org.sqlite", sqlitelevel)
  }

  // private def _http_driver_from_runtime_config(
  //   runtimeConfig: RuntimeConfig
  // ): HttpDriver = {
  //   HttpDriverFactory.create(runtimeConfig.httpDriver) match {
  //     case Consequence.Success(driver) =>
  //       driver
  //     case Consequence.Failure(conclusion) =>
  //       _print_error(conclusion)
  //       FakeHttpDriver.okText("nop")
  //   }
  // }

  object Config {
    // TODO Phase 2.9+: wire this runtime configuration into ExecutionContext / observability.
    // It is intentionally unused during Phase 2.8 CLI normalization and MUST NOT be referenced yet.
    final case class Runtime(
      environment: String,
      observability: Map[String, String]
    )

    def from(conf: ResolvedConfiguration)
        : org.goldenport.Consequence[Runtime] = {
      import cats.syntax.all.*

      val env =
        _config_string(conf, "textus.runtime.environment", "cncf.runtime.environment")
          .map(_.getOrElse("default"))

      val obs =
        _config_string(conf, "textus.runtime.observability", "cncf.runtime.observability")
          .map(_.map(v => Map("default" -> v)).getOrElse(Map.empty))

      (env, obs).mapN(Runtime.apply)
    }

    private def _config_string(
      conf: ResolvedConfiguration,
      primary: String,
      legacy: String
    ): org.goldenport.Consequence[Option[String]] =
      conf.get[String](primary) match {
        case success @ Consequence.Success(Some(_)) => success
        case _ => conf.get[String](legacy)
      }
  }

  private def _legacy_framework_key(
    key: String
  ): Option[String] =
    if (key.startsWith("textus.")) Some("cncf." + key.stripPrefix("textus."))
    else None

  // legacy entry points now delegate to the canonical initialization path
  // so command/client/server share the same component setup and startup import behavior.
  def buildSubsystem(
    extraComponents: Subsystem => Seq[Component] = _ => Nil,
    mode: Option[RunMode] = None,
    args: Array[String] = Array.empty[String]
  ): Subsystem =
    new CncfRuntime().initializeForEmbedding(
      cwd = Paths.get("").toAbsolutePath.normalize,
      args = args,
      modeHint = mode,
      extraComponents = extraComponents
    ).TAKE

  // private def _build_subsystem(
  //   cofiguration: ResolvedConfiguration,
  //   mode: Option[RunMode] = None
  // ): Subsystem = {
  //   val collaratorfactory = CollaboratorFactory.create(configuration)
  //   val componentfactory = ComponentFactory.create(subsytem, configuration, collaratorfactory)
  //   context.updateComponentFactory(componentfactory)
  // }

  def startServer(args: Array[String]): Unit = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Server), args = args)
    new CncfRuntime().startServer(subsystem, args)
  }

  def startServer(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Unit = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Server), args)
    new CncfRuntime().startServer(subsystem, args)
  }

  def executeClient(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Client), args = args)
    new CncfRuntime().executeClient(subsystem, args)
  }

  def executeClient(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client), args)
    new CncfRuntime().executeClient(subsystem, args)
  }

  def executeCommand(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Command), args = args)
    new CncfRuntime().executeCommand(subsystem, args)
  }

  def executeCommand(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command), args)
    new CncfRuntime().executeCommand(subsystem, args)
  }

  def executeServerEmulator(args: Array[String]): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val configuration = _resolve_configuration(cwd, args)
    val runtimeConfig = _runtime_config(configuration)
    val (includeHeader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest, runtimeConfig.serverEmulatorBaseUrl) match {
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
            _print_error(conclusion)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        Consequence.Failure(conclusion)
    }
    _exit_code(result)
  }

  def executeServerEmulator(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val configuration = _resolve_configuration(cwd, args)
    val runtimeConfig = _runtime_config(configuration)
    val (includeHeader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest, runtimeConfig.serverEmulatorBaseUrl) match {
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
            _print_error(conclusion)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        Consequence.Failure(conclusion)
    }
    _exit_code(result)
  }

  def executeScript(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Consequence[Response] = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Script))
    _to_request_script(subsystem, args).flatMap { req =>
      subsystem.execute(req)
    }
  }

  private def _to_request_script(
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
            val xs = Vector("SCRIPT", "DEFAULT", "RUN") ++ in
            _to_request(subsystem, xs.toArray, RunMode.Script)
        }
    }
  }

  def runExitCode(args: Array[String]): Int =
    run(args)

  // legacy: see run
  def runWithExtraComponents(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val configuration = _resolve_configuration(cwd, args)
    configure_slf4j_simple(configuration)
    val (reposResult, argsAfterRepos, noDefaultComponents) =
      ComponentRepositorySpace.extractArgs(configuration, args)
    val (backendoption, logLevelOption, actualargs0) =
      _extract_log_options(argsAfterRepos)
    val actualargs = _normalize_help_aliases(actualargs0)
    _execute_top_level_help(actualargs) match {
      case Some(code) => return code
      case None => ()
    }
    if (actualargs.isEmpty) {
      _print_usage()
      return 2
    }
    ComponentRepositorySpace.resolveSpecifications(
      reposResult,
      cwd,
      noDefaultComponents
    ) match {
      case Left(message) =>
        Console.err.println(message)
        2
      case Right(specs) =>
        val runtimeParse = _runtime_parameter_parser.parse(actualargs.toIndexedSeq)
        val domainArgs = _strip_configuration_args(runtimeParse.residual.toArray)
        val mode = _mode_from_args(domainArgs)
        val configuration = _resolve_configuration(cwd, argsAfterRepos)
        val runtimeConfig = _runtime_config(configuration)
        val logBackend = _decide_backend(backendoption, _logging_backend_from_configuration(configuration), mode)
        val httpDriver = runtimeConfig.httpDriver
        val aliasResolver = _alias_resolver(configuration)
        _install_log_backend(logBackend)
        _update_visibility_policy(logLevelOption, configuration, mode)
        _reset_global_runtime_context()
        val context = _create_global_runtime_context(
          runtimeConfig,
          configuration,
          // httpDriver,
          // runtimeConfig.mode,
          aliasResolver
        )
        _mark_used_parameters(configuration)
        val r: Consequence[OperationRequest] =
          _runtime_protocol_engine.makeOperationRequest(domainArgs)
        r match {
          case Consequence.Success(req) =>
            // TODO Phase 2.9+: bind mode-specific runtime configuration.
            // - Derive Config.Runtime from ResolvedConfiguration and bind into ExecutionContext / observability.
            // - Consider per-mode defaults (server/client/command/server-emulator/script) while keeping CLI normalization execution-free.
            val requestmode = RunMode.from(req.request.operation)
            if (requestmode.contains(RunMode.Server)) {
              LogBackendHolder.backend match {
                case Some(LogBackend.NopLogBackend) =>
                  LogBackendHolder.install(LogBackend.StdoutBackend)
                case _ => ()
              }
            }
            requestmode.foreach { m =>
              GlobalRuntimeContext.current.foreach(_.updateRuntimeMode(m))
            }
            requestmode.foreach { m =>
              GlobalRuntimeContext.current.foreach(_.updateRuntimeMode(m))
            }
            observe_trace(
              s"[subsytem] runWithExtraComponents dispatching to client mode args=${domainArgs.drop(1).mkString(" ")}"
            )
            requestmode match {
              case Some(RunMode.Server) =>
                startServer(domainArgs.drop(1), extraComponents)
                0
              case Some(RunMode.Client) =>
                executeClient(domainArgs.drop(1), extraComponents)
              case Some(RunMode.Command) =>
                executeCommand(actualargs.drop(1), extraComponents)
              case Some(RunMode.ServerEmulator) =>
                executeServerEmulator(domainArgs.drop(1), extraComponents)
              case Some(RunMode.Script) =>
                _run_script(domainArgs.drop(1), extraComponents)
              case _ =>
                _print_usage()
                2
            }
          case Consequence.Failure(conclusion) =>
            _print_error(conclusion)
            _exit_code(Consequence.Failure(conclusion))
        }
    }
  }

  def run(args: Array[String]): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val configuration = _resolve_configuration(cwd, args)
    configure_slf4j_simple(configuration)
    val (reposResult, argsAfterRepos, noDefaultComponents) =
      ComponentRepositorySpace.extractArgs(configuration, args)
    val (backendoption, logLevelOption, actualargs0) =
      _extract_log_options(argsAfterRepos)
    val actualargs = _normalize_help_aliases(actualargs0)
    _execute_top_level_help(actualargs) match {
      case Some(code) => return code
      case None => ()
    }
    if (actualargs.isEmpty) {
      _print_usage()
      return 2
    }
    ComponentRepositorySpace.resolveSpecifications(
      reposResult,
      cwd,
      noDefaultComponents
    ) match {
      case Left(message) =>
        Console.err.println(message)
        _print_usage()
        2
      case Right(specs) =>
        val runtimeParse = _runtime_parameter_parser.parse(actualargs.toIndexedSeq)
        val domainArgs = _strip_configuration_args(runtimeParse.residual.toArray)
        val mode = _mode_from_args(domainArgs)
        val runtimeConfig = _runtime_config(configuration)
        val logBackend = _decide_backend(backendoption, _logging_backend_from_configuration(configuration), mode)
        val httpDriver = runtimeConfig.httpDriver
        val aliasResolver = _alias_resolver(configuration)
        _install_log_backend(logBackend)
        _update_visibility_policy(logLevelOption, configuration, mode)
        _reset_global_runtime_context()
        val context = _create_global_runtime_context(
          runtimeConfig,
          configuration,
          // httpDriver,
          // runtimeConfig.mode,
          aliasResolver
        )
        _mark_used_parameters(configuration)
        val r: Consequence[OperationRequest] =
          _runtime_protocol_engine.makeOperationRequest(domainArgs)
        r match {
          case Consequence.Success(req) =>
            // TODO Phase 2.9+: bind mode-specific runtime configuration.
            // - Derive Config.Runtime from ResolvedConfiguration and bind into ExecutionContext / observability.
            // - Consider per-mode defaults (server/client/command/server-emulator/script) while keeping CLI normalization execution-free.
            val mode = RunMode.from(req.request.operation)
            if (mode.contains(RunMode.Server)) {
              LogBackendHolder.backend match {
                case Some(LogBackend.NopLogBackend) =>
                  LogBackendHolder.install(LogBackend.StdoutBackend)
                case _ => ()
              }
            }
            mode match {
              case Some(RunMode.Server) =>
                startServer(domainArgs.drop(1))
                0
              case Some(RunMode.Client) =>
                observe_trace(
                  s"[client:trace] run dispatching to client mode args=${domainArgs.drop(1).mkString(" ")}"
                )
                executeClient((runtimeParse.consumed ++ domainArgs.drop(1)).toArray)
              case Some(RunMode.Command) =>
                executeCommand(actualargs.drop(1))
              case Some(RunMode.ServerEmulator) =>
                executeServerEmulator(domainArgs.drop(1))
              case Some(RunMode.Script) =>
                _run_script(domainArgs.drop(1), _ => Nil)
              case None =>
                _print_error(s"Unknown mode: ${req.request.operation}")
                _print_usage()
                3
            }
          case Consequence.Failure(conclusion) =>
            _print_error(conclusion)
            _print_usage()
            _exit_code(Consequence.Failure(conclusion))
        }
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

  private def _execute_top_level_help(args: Array[String]): Option[Int] =
    args.toVector match {
      case Vector("help") => Some(CliHelpOperation.execute())
      case Vector("server", "help") => Some(ServerCommandHelp.execute())
      case Vector("client", "help") => Some(ClientCommandHelp.execute())
      case _ => None
    }

  private def _normalize_help_aliases(args: Array[String]): Array[String] =
    args.toVector match {
      case Vector(flag) if _help_flags.contains(flag) =>
        Array("help")
      case Vector(command, flag) if _help_flags.contains(flag) =>
        Array(command, "help")
      case _ =>
        args
    }

  private def _print_error(c: Conclusion): Unit = {
    val rec = c.toRecord
    val s = rec.toYamlString
    Console.err.print(s)
  }

  private def _print_error(message: String): Unit = {
    Console.err.println(message)
  }

  private def _request_record_exclude_property(name: String): Boolean =
    name.startsWith("textus.") ||
      name.startsWith("cncf.") ||
      name.startsWith("query.")

  private def _request_to_record(req: Request): Record =
    req.toRecord(excludeProperty = _request_record_exclude_property)

  private def _print_usage(): Unit = {
    val text =
      """Usage:
        |  cncf server
        |  cncf client
        |  cncf command <cmd>
        |
        |Examples:
        |  cncf server
        |  cncf client admin.system.ping
        |  cncf command admin.system.ping
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

  private def _extract_log_options(
    args: Array[String]
  ): (Option[String], Option[String], Array[String]) = {
    // Canonical CLI keys: --textus.logging.backend / --textus.logging.level
    // Other forms are experimental and kept for transitional use.
    var logBackendOption: Option[String] = None
    var logLevelOption: Option[String] = None
    val rest = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--log-backend=")) { // experimental
        logBackendOption = Some(current.stripPrefix("--log-backend="))
      } else if (current == "--log-backend" && i + 1 < args.length) { // experimental
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.backend=")) { // experimental
        logBackendOption = Some(current.stripPrefix("--textus.runtime.logging.backend="))
      } else if (current == "--textus.runtime.logging.backend" && i + 1 < args.length) { // experimental
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.backend=")) { // legacy
        logBackendOption = Some(current.stripPrefix("--cncf.runtime.logging.backend="))
      } else if (current == "--cncf.runtime.logging.backend" && i + 1 < args.length) { // legacy
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.logging.backend=")) {
        logBackendOption = Some(current.stripPrefix("--textus.logging.backend="))
      } else if (current == "--textus.logging.backend" && i + 1 < args.length) {
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.logging.backend=")) {
        logBackendOption = Some(current.stripPrefix("--cncf.logging.backend="))
      } else if (current == "--cncf.logging.backend" && i + 1 < args.length) {
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--log-level=")) { // experimental
        logLevelOption = Some(current.stripPrefix("--log-level="))
      } else if (current == "--log-level" && i + 1 < args.length) { // experimental
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.level=")) { // experimental
        logLevelOption = Some(current.stripPrefix("--textus.runtime.logging.level="))
      } else if (current == "--textus.runtime.logging.level" && i + 1 < args.length) { // experimental
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.level=")) { // legacy
        logLevelOption = Some(current.stripPrefix("--cncf.runtime.logging.level="))
      } else if (current == "--cncf.runtime.logging.level" && i + 1 < args.length) { // legacy
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.logging.level=")) {
        logLevelOption = Some(current.stripPrefix("--textus.logging.level="))
      } else if (current == "--textus.logging.level" && i + 1 < args.length) {
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.logging.level=")) {
        logLevelOption = Some(current.stripPrefix("--cncf.logging.level="))
      } else if (current == "--cncf.logging.level" && i + 1 < args.length) {
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else {
        rest += current
      }
      i = i + 1
    }
    (logBackendOption, logLevelOption, rest.result().toArray)
  }

  private def _decide_backend(
    overrideBackend: Option[String],
    configBackend: Option[String],
    mode: RunMode
  ): LogBackend =
    _backend_from_string(overrideBackend, "flag")
      .orElse(_backend_from_string(configBackend, "configuration"))
      .getOrElse(RuntimeDefaults.defaultLogBackend(mode))

  private def _backend_from_string(
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

  private def _logging_backend_from_configuration(
    configuration: ResolvedConfiguration
  ): Option[String] = {
    ConfigurationAccess.getString(configuration, "textus.runtime.logging.backend")
      .orElse(ConfigurationAccess.getString(configuration, "textus.logging.backend"))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.runtime.logging.backend"))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.logging.backend"))
  }

  private def _log_level_from_configuration(
    configuration: ResolvedConfiguration
  ): Option[String] = {
    ConfigurationAccess.getString(configuration, "textus.runtime.logging.level")
      .orElse(ConfigurationAccess.getString(configuration, "textus.logging.level"))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.runtime.logging.level"))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.logging.level"))
  }

  private def _update_visibility_policy(
    cliLogLevel: Option[String],
    configuration: ResolvedConfiguration,
    mode: RunMode
  ): Unit = {
    val levelOpt =
      cliLogLevel
        .orElse(_log_level_from_configuration(configuration))
        .flatMap(LogLevel.from)
    val level = levelOpt.getOrElse(RuntimeDefaults.defaultLogLevel(mode))
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = level))
  }

  private def _mode_from_args(
    args: Array[String]
  ): RunMode =
    args.headOption.flatMap(RunMode.from).getOrElse(RunMode.Command)

  private def _install_log_backend(
    backend: LogBackend
  ): Unit = {
    LogBackendHolder.install(backend)
  }

  private def _run_script(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val result = executeScript(args, extraComponents)
    result match {
      case Consequence.Success(res) =>
        _print_response(res)
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
    _exit_code(result)
  }

  private def _print_response(res: Response): Unit =
    println(res.print)

  private def _print_operation_response(res: OperationResponse): Unit = {
    res match {
      case OperationResponse.Http(http) =>
        val body = http.getString.getOrElse(http.print)
        Console.out.println(body)
      case OperationResponse.RecordResponse(record) =>
        _print_response(Response.Yaml(RuntimeContext.Context.default.transformRecord(record).toYamlString))
      case _ =>
        _print_response(res.toResponse)
    }
  }

  private def _client_component(
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
        Consequence.failure("client component not available")
    }

  private def _to_request(
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

  private def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] =
    new CncfRuntime()._client_action_from_request(req)

  private def _client_baseurl_from_request(
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

  private def _resolve_selector(
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

  private def _resolve_selector_with_path_resolution(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    val registry = _component_operation_fqns(subsystem).flatMap(_to_canonical_path)
    val builtins = subsystem.components.collect {
      case c if c.origin == org.goldenport.cncf.component.ComponentOrigin.Builtin => c.name
    }.toSet
    PathResolution.resolve(selector, registry, builtins) match {
      case PathResolutionResult.Success(path) =>
        Consequence.success((path.component, path.service, path.operation))
      case PathResolutionResult.Failure(reason) =>
        Consequence.failure(s"path-resolution failed: $reason")
    }
  }

  private def _resolve_selector_with_operation_resolver(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] =
    subsystem.resolver.resolve(selector, allowPrefix = false, allowImplicit = false) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Consequence.success((component, service, operation))
      case ResolutionResult.NotFound(stage, input) =>
        val symptom = Taxonomy.Symptom.NotFound
        stage match {
          case ResolutionStage.Component =>
            Consequence.fail(
              Taxonomy(Taxonomy.Category.Component, symptom),
              Facet.Component(input)
            )
          case ResolutionStage.Service =>
            Consequence.fail(
              Taxonomy(Taxonomy.Category.Service, symptom),
              Facet.Service(input)
            )
          case ResolutionStage.Operation =>
            Consequence.fail(
              Taxonomy(Taxonomy.Category.Operation, symptom),
              Facet.Operation(input)
            )
        }
      case ResolutionResult.Ambiguous(input, candidates) =>
        Consequence.failure(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
      case ResolutionResult.Invalid(reason) =>
        Consequence.failure(s"invalid selector: $reason")
    }

  private def _extract_selector_format(
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

  private def _with_format_property(
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

  private def _resolve_format(
    options: RuntimeOptionsParser.Options,
    suffixFormat: Option[String],
    mode: RunMode
  ): String =
    options.format
      .orElse(if (options.json) Some("json") else None)
      .orElse(suffixFormat)
      .getOrElse(RuntimeDefaults.defaultFormat(mode))

  private def _to_canonical_path(fqn: String): Option[CanonicalPath] =
    fqn.split("\\.", 3) match {
      case Array(component, service, operation) =>
        Some(CanonicalPath(component, service, operation))
      case _ =>
        None
    }

  private def _normalize_meta_selector(
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
          case Some(componentName) =>
            (s"$componentName.meta.$operation", tail)
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

  private def _default_meta_component_name(
    subsystem: Subsystem
  ): Option[String] =
    subsystem.components.sortBy(_.name).headOption.map(_.name)

  private def _extract_runtime_options(
    args: Seq[String]
  ): (RuntimeOptionsParser.Options, Seq[String]) =
    RuntimeOptionsParser.extract(args)

  private def _runtime_properties(
    options: RuntimeOptionsParser.Options,
    mode: RunMode = RunMode.Command
  ): List[Property] =
    RuntimeOptionsParser.properties(options, mode)

  private def _selectorAndArguments(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    args.toVector match {
      case Vector() =>
        Consequence.failure("command name is required")
      case Vector(single, rest @ _*) if single.contains("/") =>
        _selectorFromPath(single, "/").map(_ -> rest.toVector)
      case Vector(single, rest @ _*) if single.contains(".") =>
        Consequence.success((single, rest.toVector))
      case Vector(component, service, operation, rest @ _*) =>
        Consequence.success((s"$component.$service.$operation", rest.toVector))
      case Vector(single, rest @ _*) =>
        Consequence.success((single, rest.toVector))
    }
  }

  private def _alias_resolver: AliasResolver =
    GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)


  private def _selectorFromPath(
    value: String,
    delimiter: String
  ): Consequence[String] = {
    val segments = value.split(delimiter).toVector.filter(_.nonEmpty)
    if (segments.size == 3) {
      Consequence.success(segments.mkString("."))
    } else {
      delimiter match {
        case "/" => Consequence.failure("command path must be /component/service/operation")
        case "." => Consequence.failure("command must be component.service.operation")
        case _ => Consequence.failure("command selector is invalid")
      }
    }
  }

  private def _component_operation_fqns(subsystem: Subsystem): Vector[String] =
    subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => s"${comp.name}.${service.name}.${op.name}")
      }
    }.toVector

  private def _component_names(subsystem: Subsystem): String =
    subsystem.components.map(_.name).mkString(",")

  private def _operation_sample(operations: Vector[String]): String = {
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

  private[cli] def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] =
    new CncfRuntime()._parse_client_path(args)

  private val _client_http_request_definition: RequestDefinition = {
    val base = RequestDefinition.curlLike
    val baseurlParameter = ParameterDefinition(
      content = BaseContent.simple("baseurl"),
      kind = ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)
    )
    RequestDefinition(base.parameters :+ baseurlParameter)
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
    args: Seq[String],
    baseUrl: String
  ): Consequence[Seq[String]] = {
    if (args.isEmpty) {
      Consequence.failure("server-emulator requires a path or URL")
    } else if (args.exists(_.contains("://"))) {
      Consequence.success(args)
    } else {
      _parse_component_service_operation(args).map {
        case (component, service, operation) =>
          Seq(_serverEmulatorUrl(baseUrl, component, service, operation))
      }
    }
  }

  private def _serverEmulatorUrl(
    baseUrl: String,
    component: String,
    service: String,
    operation: String
  ): String = {
    val trimmed = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
    s"${trimmed}/${component}/${service}/${operation}"
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

  private def _resolve_configuration(
    cwd: Path,
    args: Array[String] = Array.empty
  ): ResolvedConfiguration = {
    val configargs = _config_args(args)
    val basesources = ConfigurationSources.standard(
      cwd,
      applicationname = _configuration_application_name,
      args = Map.empty
    )
    val explicitconfigs = _explicit_config_sources(cwd, configargs)
    val argsource = ConfigurationSource.args(configargs).toSeq
    val sources = ConfigurationSources(basesources.sources ++ explicitconfigs ++ argsource)
    // TODO Phase 2.9+: define failure policy for configuration resolution.
    // - Preserve/emit ConfigurationTrace and error details for observability.
    // - Decide whether CLI should fail-fast vs fallback to empty configuration.
    ConfigurationResolver.default.resolve(sources) match {
      case Consequence.Success(resolved) =>
        resolved
      case Consequence.Failure(_) =>
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    }
  }

  private def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ): Vector[ConfigurationSource] = {
    val files = _split_config_paths(configargs.get("cncf.config.file")) ++
      _split_config_paths(configargs.get("cncf.config.files"))
    files.distinct.map { path =>
      val p = _normalize_config_path(cwd, path)
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Arguments,
        path = p,
        rank = ConfigurationSource.Rank.Arguments,
        loader = new SimpleFileConfigLoader
      )
    }.toVector
  }

  private def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    val p = Paths.get(path)
    if (p.isAbsolute) p.normalize else cwd.resolve(p).normalize
  }

  private def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    val entries = scala.collection.mutable.Map.empty[String, String]
    val alias = Map(
      "log-level" -> "textus.logging.level",
      "log-backend" -> "textus.logging.backend",
      "format" -> "textus.output.format"
    )
    var i = 0
    var stop = false
    while (i < args.length && !stop) {
      val current = args(i)
      if (current == "--") {
        stop = true
      } else if (current.startsWith("--") && current.contains("=")) {
        val raw = current.drop(2)
        val parts = raw.split("=", 2)
        if (parts.length == 2) {
          val key = parts(0)
          val value = parts(1)
          if (key.startsWith("textus.") || key.startsWith("cncf.")) {
            entries.update(key, value)
          }
        }
      } else if (current.startsWith("--")) {
        val key = current.drop(2)
        if ((key.startsWith("textus.") || key.startsWith("cncf.")) && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          entries.update(key, args(i + 1))
          i = i + 1
        }
      } else if (current.startsWith("-") && !current.startsWith("--")) {
        val raw = current.drop(1)
        if (raw.contains("=")) {
          val parts = raw.split("=", 2)
          val key = parts(0)
          val value = if (parts.length > 1) parts(1) else ""
          alias.get(key).foreach(entries.update(_, value))
        } else {
          alias.get(raw).foreach { key =>
            if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
              entries.update(key, args(i + 1))
              i = i + 1
            }
          }
        }
      }
      i = i + 1
    }
    entries.toMap
  }

  private def _runtime_config(
    configuration: ResolvedConfiguration
  ): RuntimeConfig =
    RuntimeConfig.from(configuration)

  private def _strip_configuration_args(
    args: Array[String]
  ): Array[String] = {
    val builder = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--cncf.")) {
        if (!current.contains("=") && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          i = i + 1
        }
      } else {
        builder += current
      }
      i = i + 1
    }
    builder.result().toArray
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

  def parse(p: String): org.goldenport.Consequence[RunMode] =
    from(p) match {
      case Some(runMode) => org.goldenport.Consequence.success(runMode)
      case None => org.goldenport.Consequence.failure(s"invalid run mode: ${p}")
    }
}

private[cli] object RuntimeOptionsParser {
  final case class Options(
    json: Boolean = false,
    debug: Boolean = false,
    noExit: Boolean = false,
    format: Option[String] = None,
    pathResolutionCommand: Boolean = false,
    commandExecutionMode: Option[String] = None
  )

  def extract(
    args: Seq[String]
  ): (Options, Seq[String]) = {
    val clean = Vector.newBuilder[String]
    var options = Options()
    var i = 0
    var stop = false
    while (i < args.length && !stop) {
      val current = args(i)
      if (current == "--") {
        clean ++= args.drop(i + 1)
        stop = true
      } else if (_is_property(current, "--json")) {
        options = options.copy(json = true)
      } else if (_is_property(current, "--debug")) {
        options = options.copy(debug = true)
      } else if (current == "--no-exit") {
        options = options.copy(noExit = true)
      } else if (_is_property(current, "--format") || _is_property(current, "-format")) {
        _take_value(args, i).foreach { case (value, consumednext) =>
          options = options.copy(format = Some(value))
          if (consumednext) {
            i = i + 1
          }
        }
      } else if (_is_property(current, "--path-resolution")) {
        options = options.copy(pathResolutionCommand = true)
      } else if (_is_key_value(current)) {
        _extract_key_value(current) match {
          case Some((key, value)) if _is_format_key(key) =>
            options = options.copy(format = Some(value))
          case Some((key, value)) if _is_path_resolution_command_key(key) =>
            options = options.copy(pathResolutionCommand = _is_truthy(value))
          case Some((key, value)) if _is_command_execution_mode_key(key) =>
            options = options.copy(commandExecutionMode = Some(value))
          case Some((key, value)) if _is_framework_key(key) =>
            ()
          case Some((key, value)) if _is_framework_alias(key) =>
            ()
          case _ =>
            clean += current
        }
      } else if (_is_framework_key(current.drop(2))) {
        if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
          if (_is_format_key(current.drop(2))) {
            options = options.copy(format = Some(args(i + 1)))
          } else if (_is_path_resolution_command_key(current.drop(2))) {
            options = options.copy(pathResolutionCommand = _is_truthy(args(i + 1)))
          } else if (_is_command_execution_mode_key(current.drop(2))) {
            options = options.copy(commandExecutionMode = Some(args(i + 1)))
          }
          i = i + 1
        }
      } else if (_is_framework_alias(current.drop(1))) {
        if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
          i = i + 1
        }
      } else {
        clean += current
      }
      i = i + 1
    }
    if (!stop && i >= args.length) {
      ()
    }
    (options, clean.result())
  }

  def properties(
    options: Options,
    mode: RunMode = RunMode.Command
  ): List[Property] = {
    val b = List.newBuilder[Property]
    val formatvalue = options.format
      .orElse(if (options.json) Some("json") else None)
      .orElse(Some(RuntimeDefaults.defaultFormat(mode)))
    formatvalue.foreach { value =>
      b += Property("textus.format", value, None)
    }
    if (options.debug) b += Property("textus.debug", "true", None)
    if (options.noExit) b += Property("textus.no-exit", "true", None)
    options.commandExecutionMode.foreach { value =>
      b += Property("textus.runtime.command.execution-mode", value, None)
    }
    b.result()
  }

  private def _is_property(
    current: String,
    key: String
  ): Boolean =
    current == key || current.startsWith(s"${key}=")

  private def _is_key_value(
    current: String
  ): Boolean =
    current.startsWith("--") && current.contains("=") ||
      (current.startsWith("-") && !current.startsWith("--") && current.contains("="))

  private def _extract_key_value(
    current: String
  ): Option[(String, String)] = {
    val raw = if (current.startsWith("--")) current.drop(2) else current.drop(1)
    val parts = raw.split("=", 2)
    if (parts.length == 2) Some(parts(0) -> parts(1)) else None
  }

  private def _take_value(
    args: Seq[String],
    index: Int
  ): Option[(String, Boolean)] =
    if (args(index).contains("=")) {
      _extract_key_value(args(index)).map(v => v._2 -> false)
    } else if (index + 1 < args.length && !args(index + 1).startsWith("-")) {
      Some(args(index + 1) -> true)
    } else {
      None
    }

  private def _is_framework_key(
    key: String
  ): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.")

  private def _is_framework_alias(
    key: String
  ): Boolean =
    key == "log-level" ||
      key == "log-backend" ||
      key == "format" ||
      key == "path-resolution" ||
      key == "textus.format" ||
      key == "textus.path-resolution" ||
      key == "cncf.format" ||
      key == "cncf.path-resolution"

  private def _is_format_key(
    key: String
  ): Boolean =
    key == "textus.format" ||
      key == "textus.output.format" ||
      key == "cncf.format" ||
      key == "cncf.output.format"

  private def _is_path_resolution_command_key(
    key: String
  ): Boolean =
    key == "textus.path-resolution.command" ||
      key == "cncf.path-resolution.command"

  private def _is_command_execution_mode_key(
    key: String
  ): Boolean =
    key == "textus.runtime.command.execution-mode" ||
      key == "cncf.runtime.command.execution-mode" ||
      key == "runtime.command.execution-mode" ||
      key == "command.execution-mode"

  private def _is_truthy(value: String): Boolean = {
    val normalized = value.trim.toLowerCase
    normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
  }
}

class CncfRuntime() extends GlobalObservable {
  private val _configuration_application_name = "cncf"
  private val _runtime_service_name = "runtime"
  private val _help_flags = Set("--help", "-h")
  private val _args_parser = new ArgsParser(ArgsParser.Config())

  private val _runtime_protocol: Protocol =
    Protocol.Builder()
      .addOperation(_runtime_service_name, RunMode.Server.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Client.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Command.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.ServerEmulator.name, RequestDefinition(), ResponseDefinition())
      .addOperation(_runtime_service_name, RunMode.Script.name, RequestDefinition(), ResponseDefinition())
      .build()

  private val _runtime_protocol_engine = ProtocolEngine.create(_runtime_protocol)

  private def _create_global_context(config: RuntimeConfig): Unit =
    GlobalContext.set(
      GlobalContext(
        WorkAreaSpace.create(config)
      )
    )

  private var _global_runtime_context: Option[GlobalRuntimeContext] = None

  private def _reset_global_runtime_context(): Unit =
    _global_runtime_context = None

  private def _create_global_runtime_context(
    // httpDriver: HttpDriver,
    // mode: RunMode,
    runconfig: RuntimeConfig,
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver
  ): GlobalRuntimeContext = {
    val execution = ExecutionContext.create()
    val context = GlobalRuntimeContext.create(
      name = "runtime",
      runconfig,
      configuration,
      observabilityContext = execution.observability,
      aliasResolver
      // httpDriver = httpDriver,
      // aliasResolver = aliasResolver,
      // runtimeMode = mode,
      // runtimeVersion = CncfVersion.current,
      // subsystemName = GlobalRuntimeContext.SubsystemName,
      // subsystemVersion = CncfVersion.current
    )
    _global_runtime_context = Some(context)
    GlobalRuntimeContext.current = Some(context)
    _initialize_global_observability()
    context
  }

  private def _initialize_global_observability(): Unit = {
    if (!GlobalObservability.isInitialized) {
      // Default behavior: rely on whatever backend (and bootstrap buffer) is already configured.
      val backend = LogBackendHolder.backend.getOrElse(LogBackend.StdoutBackend)
      val root =
        ObservabilityRoot(
          engine = ObservabilityEngine,
          gate = GlobalObservabilityGate.allowAll,
          backend = backend
        )
      GlobalObservability.initialize(root)
    }
  }

  def run(args: Array[String]): Int =
    run(args, (_: Subsystem) => Nil)

  def initializeForEmbedding(
    cwd: Path = Paths.get("").toAbsolutePath.normalize,
    args: Array[String] = Array.empty,
    modeHint: Option[RunMode] = None,
    extraComponents: Subsystem => Seq[Component] = (_: Subsystem) => Nil
  ): Consequence[Subsystem] =
    Consequence(_initialize(cwd, args, modeHint, extraComponents))

  def closeEmbedding(): Unit = {
    _reset_global_runtime_context()
    GlobalRuntimeContext.current = None
    LogBackendHolder.reset()
  }

  def run(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val normalizedArgs = _normalize_help_aliases(args)
    _execute_top_level_help(normalizedArgs) match {
      case Some(code) => return code
      case None => ()
    }
    if (normalizedArgs.isEmpty) {
      _print_usage()
      return 2
    }
    Consequence(_initialize(normalizedArgs, extraComponents)) match {
      case Consequence.Success(subsystem) =>
        normalizedArgs.headOption.flatMap(RunMode.from) match {
          case Some(RunMode.Command) =>
            _execute_command_args(subsystem, normalizedArgs.drop(1))
          case _ =>
            _runtime_protocol_engine.makeOperationRequest(normalizedArgs) match {
              case Consequence.Success(req) =>
                _run(subsystem, req)
              case Consequence.Failure(conclusion) =>
                _print_error(conclusion)
                _exit_code(Consequence.Failure(conclusion))
            }
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  private def _execute_command_args(
    subsystem: Subsystem,
    args: Array[String]
  ): Int = {
    val normalizedArgs = CommandProtocolHelp.normalizeArgs(args) match {
      case Left(code) => return code
      case Right(xs) => xs
    }
    val result = _to_request(subsystem, normalizedArgs).flatMap { req =>
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

  private def _initialize(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Subsystem =
    _initialize(
      Paths.get("").toAbsolutePath.normalize,
      args,
      modeHint = None,
      extraComponents
    )

  private def _initialize(
    cwd: Path,
    args: Array[String],
    modeHint: Option[RunMode],
    extraComponents: Subsystem => Seq[Component]
  ): Subsystem = {
    val configuration = _resolve_configuration(cwd, args)
    CncfRuntime.configure_slf4j_simple(configuration)
    val modehint = modeHint.orElse(args.headOption.flatMap(RunMode.from))
    val runconfig = RuntimeConfig.from(configuration, modehint)
    val aliasresolver = AliasLoader.load(configuration.configuration)
    LogBackendHolder.install(runconfig.logBackend)
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = runconfig.logLevel))
    _create_global_context(runconfig)
    _reset_global_runtime_context() // TODO
    _create_global_runtime_context(
      // runconfig.httpDriver,
      // runconfig.mode,
      runconfig,
      configuration,
      aliasresolver
    )
    val mode = runconfig.mode
    val subsystem = DefaultSubsystemFactory.defaultWithScope(
      _runtime_scope_context(),
      Some(mode),
      configuration,
      aliasresolver
    )
    observe_trace(
      s"[subsytem] buildSubsystem start mode=${mode.name} componentCount=${subsystem.components.size}"
    )
    GlobalRuntimeContext.current.foreach(_.updateSubsystemVersion(subsystem.version.getOrElse(CncfVersion.current)))
    val colfactory = CollaboratorFactory.create(configuration)
    val compfactory = ComponentFactory.create(subsystem, colfactory, cwd, configuration)
    subsystem.setup(compfactory)
    val extras = extraComponents(subsystem).map(compfactory.bootstrap)
    if (extras.nonEmpty) {
      subsystem.add(extras)
    }
    StartupImport.run(cwd, configuration, runconfig, subsystem) match {
      case Consequence.Success(_) =>
        ()
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.show)
    }
    subsystem
  }

  private def _resolve_configuration(
    cwd: Path,
    args: Array[String] = Array.empty
  ): ResolvedConfiguration = {
    val configargs = _config_args(args)
    val basesources = ConfigurationSources.standard(
      cwd,
      applicationname = _configuration_application_name,
      args = Map.empty
    )
    val explicitconfigs = _explicit_config_sources(cwd, configargs)
    val argsource = ConfigurationSource.args(configargs).toSeq
    val sources = ConfigurationSources(basesources.sources ++ explicitconfigs ++ argsource)
    // TODO Phase 2.9+: define failure policy for configuration resolution.
    // - Preserve/emit ConfigurationTrace and error details for observability.
    // - Decide whether CLI should fail-fast vs fallback to empty configuration.
    ConfigurationResolver.default.resolve(sources) match {
      case Consequence.Success(resolved) =>
        resolved
      case Consequence.Failure(_) =>
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    }
  }

  private def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ): Vector[ConfigurationSource] = {
    val files = _split_config_paths(configargs.get("cncf.config.file")) ++
      _split_config_paths(configargs.get("cncf.config.files"))
    files.distinct.map { path =>
      val p = _normalize_config_path(cwd, path)
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Arguments,
        path = p,
        rank = ConfigurationSource.Rank.Arguments,
        loader = new SimpleFileConfigLoader
      )
    }.toVector
  }

  private def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    val p = Paths.get(path)
    if (p.isAbsolute) p.normalize else cwd.resolve(p).normalize
  }

  private def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    val entries = scala.collection.mutable.Map.empty[String, String]
    val alias = Map(
      "log-level" -> "textus.logging.level",
      "log-backend" -> "textus.logging.backend",
      "format" -> "textus.output.format"
    )
    var i = 0
    var stop = false
    while (i < args.length && !stop) {
      val current = args(i)
      if (current == "--") {
        stop = true
      } else if (current.startsWith("--") && current.contains("=")) {
        val raw = current.drop(2)
        val parts = raw.split("=", 2)
        if (parts.length == 2) {
          val key = parts(0)
          val value = parts(1)
          if (key.startsWith("textus.") || key.startsWith("cncf.")) {
            entries.update(key, value)
          }
        }
      } else if (current.startsWith("--")) {
        val key = current.drop(2)
        if ((key.startsWith("textus.") || key.startsWith("cncf.")) && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          entries.update(key, args(i + 1))
          i = i + 1
        }
      } else if (current.startsWith("-") && !current.startsWith("--")) {
        val raw = current.drop(1)
        if (raw.contains("=")) {
          val parts = raw.split("=", 2)
          val key = parts(0)
          val value = if (parts.length > 1) parts(1) else ""
          alias.get(key).foreach(entries.update(_, value))
        } else {
          alias.get(raw).foreach { key =>
            if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
              entries.update(key, args(i + 1))
              i = i + 1
            }
          }
        }
      }
      i = i + 1
    }
    entries.toMap
  }

  private def _runtime_scope_context(): ScopeContext =
    _global_runtime_context.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = DefaultSubsystemFactory.subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )
    }

  private def _run(
    subsystem: Subsystem,
    p: OperationRequest
  ): Int = {
    val args = _make_args(p)
    _runtime_protocol_engine.makeOperationRequest(args) match {
      case Consequence.Success(s) =>
        val req = s.request
        req.operation match {
          case RunMode.Server.`name` => ServerOperation(subsystem).execute(req)
          case RunMode.Client.`name` => ClientOperation(subsystem).execute(req)
          case RunMode.Command.`name` => executeCommand(subsystem, req)
          case RunMode.ServerEmulator.`name` => ServerEmulatorOperation(subsystem).execute(req)
          case RunMode.Script.`name` => ScriptOperation(subsystem).execute(req)
          case "help" => HelpOperation(subsystem).execute(req)
        }
      case Consequence.Failure(c) => Consequence.RAISE.NotImplemented
    }
  }

  private def _make_args(req: OperationRequest): Array[String] =
    _make_args(req.request)

  private def _make_args(req: Request): Array[String] = req.toArgs

  // legacy
  private def _run0(
    subsystem: Subsystem,
    p: OperationRequest
  ): Int = {
    val args = _make_args(p)
    _runtime_protocol_engine.makeOperationRequest(args) match {
      case Consequence.Success(s) =>
        val req = s.request
        req.operation match {
          case RunMode.Server.`name` => startServer(subsystem, req)
          case RunMode.Client.`name` => executeClient(subsystem, req)
          case RunMode.Command.`name` => executeCommand(subsystem, req)
          case RunMode.ServerEmulator.`name` => executeServerEmulator(subsystem, req)
          case RunMode.Script.`name` => executeScript(subsystem, req)
          case "help" => Consequence.RAISE.NotImplemented
        }
      case Consequence.Failure(c) => Consequence.RAISE.NotImplemented
    }
  }

  def startServer(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    val engine = new HttpExecutionEngine(subsystem)
    val server = new Http4sHttpServer(engine)
    server.start(args)
    0
  }

  def startServer(subsystem: Subsystem, args: Array[String]): Unit = {
    val engine = new HttpExecutionEngine(subsystem)
    val server = new Http4sHttpServer(engine)
    server.start(args)
  }

  def executeClient(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    executeClient(subsystem, args)
  }

  def executeClient(subsystem: Subsystem, args: Array[String]): Int = {
    val operations = _component_operation_fqns(subsystem)
    observe_trace(
      s"executeClient start args=${args.mkString(" ")} componentCount=${subsystem.components.size} operations=${_operation_sample(operations)}"
    )
    val result = _client_component(subsystem).flatMap { component =>
        parseClientArgs(subsystem, args).flatMap { req =>
        _client_action_from_request(req).flatMap { action =>
          component.execute(action)
        }
      }
    }
    result match {
      case Consequence.Success(res) =>
        _print_operation_response(res)
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
    _exit_code(result)
  }

  private def _component_operation_fqns(subsystem: Subsystem): Vector[String] =
    subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => s"${comp.name}.${service.name}.${op.name}")
      }
    }.toVector

  private def _operation_sample(operations: Vector[String]): String = {
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

  private def _client_component(
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
        Consequence.failure("client component not available")
    }

  def parseClientArgs(
    subsystem: Subsystem,
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.failure("client command is required")
    } else {
      _parse_client_command(subsystem, args.toIndexedSeq)
    }
  }

  private[cli] def _parse_client_command(
    subsystem: Subsystem,
    args: Seq[String]
  ): Consequence[Request] = {
    args.toVector match {
      case Vector("http", operation, rest @ _*) =>
        _parse_http_operation(operation).flatMap { op =>
          if (rest.isEmpty) {
            Consequence.failure("client http path is required")
          } else {
            _parse_client_http(op, rest).map { case (path, properties) =>
              Request.of(
                component = "client",
                service = "http",
                operation = op,
                arguments = List(Argument("path", path, None)),
                switches = Nil,
                properties = properties
              )
            }
          }
        }
      case Vector("http") =>
        Consequence.failure("client http requires operation and path")
      case _ =>
        _to_request(subsystem, args.toArray, RunMode.Command).flatMap(_command_request_to_client_request(subsystem, _))
    }
  }

  private[cli] def _command_request_to_client_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    _http_method_for_request(subsystem, req).map { method =>
      Request.of(
        component = "client",
        service = "http",
        operation = method,
        arguments = Argument("path", _request_path(req), None) :: req.arguments,
        switches = req.switches,
        properties = req.properties
      )
    }

  private[cli] def _request_path(req: Request): String =
    NamingConventions.toNormalizedPath(
      req.component.getOrElse(""),
      req.service.getOrElse(""),
      req.operation
    )

  private[cli] def _http_method_for_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[String] =
    _operation_request_definition(subsystem, req).flatMap(_.createOperationRequest(req)).map {
      case _: org.goldenport.cncf.action.QueryAction => "get"
      case _ => "post"
    }

  private[cli] def _operation_request_definition(
    subsystem: Subsystem,
    req: Request
  ): Consequence[org.goldenport.protocol.spec.OperationDefinition] =
    (for {
      componentName <- req.component
      serviceName <- req.service
      component <- subsystem.components.find(_.name == componentName)
      service <- component.protocol.services.services.find(_.name == serviceName)
      operation <- service.operations.operations.find(_.name == req.operation)
    } yield operation) match {
      case Some(op) => Consequence.success(op)
      case None => Consequence.failure(s"client target operation not found: ${req.name}")
    }

  private[cli] def _framework_option_passthrough(
    args: Seq[String]
  ): List[Property] = {
    val b = List.newBuilder[Property]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--")) {
        val key = current.drop(2)
        if (_is_client_passthrough_framework_key(key)) {
          if (current.contains("=")) {
            val eq = current.indexOf('=')
            val actualKey = current.substring(2, eq)
            val value = current.substring(eq + 1)
            b += Property(actualKey, value, None)
          } else if (i + 1 < args.length && !args(i + 1).startsWith("-")) {
            b += Property(key, args(i + 1), None)
            i = i + 1
          } else {
            b += Property(key, "true", None)
          }
        }
      }
      i = i + 1
    }
    b.result()
  }

  private[cli] def _is_client_passthrough_framework_key(
    key: String
  ): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.") || key.startsWith("query.")

  private[cli] def _parse_http_operation(
    operation: String
  ): Consequence[String] = {
    val lower = operation.toLowerCase
    lower match {
      case "get" | "post" => Consequence.success(lower)
      case _ => Consequence.failure("client http operation must be get or post")
    }
  }

  private[cli] def _parse_client_http(
    operation: String,
    params: Seq[String]
  ): Consequence[(String, List[Property])] = {
    val args = Array(operation) ++ params.toArray
    Request.parseArgs(_client_http_request_definition, args).flatMap { parsed =>
      parsed.arguments.headOption match {
        case Some(pathArgument) =>
          Consequence.success((
            _normalize_path(pathArgument.value.toString),
            parsed.properties ++ _http_tail_properties(parsed.arguments.drop(1))
          ))
        case None =>
          Consequence.failure("client http path is required")
      }
    }
  }

  private[cli] def _http_tail_properties(
    arguments: List[Argument]
  ): List[Property] =
    arguments.zipWithIndex.map { case (argument, index) =>
      val text = argument.value.toString
      text.split("=", 2).toList match {
        case key :: value :: Nil if key.nonEmpty =>
          Property(key, value, None)
        case _ =>
          Property(s"arg${index + 1}", text, None)
      }
    }

  private[cli] def _normalize_path(path: String): String = {
    val normalized = if (path.contains(".")) path.replace(".", "/") else path
    if (normalized.startsWith("/")) normalized else s"/${normalized}"
  }

  private[cli] def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    if (args.isEmpty) {
      Consequence.failure("client path is required")
    } else {
      args.toVector match {
        case Vector(component, service, operation, rest @ _*) =>
          Consequence.success((_normalize_path(s"/${component}/${service}/${operation}"), rest))
        case Vector(single, rest @ _*) =>
          _parse_component_service_operation_string(single).map { case (component, service, operation) =>
            (_normalize_path(s"/${component}/${service}/${operation}"), rest)
          }
      }
    }
  }

  private val _client_http_request_definition: RequestDefinition = {
    val base = RequestDefinition.curlLike
    val baseurlParameter = ParameterDefinition(
      content = BaseContent.simple("baseurl"),
      kind = ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)
    )
    RequestDefinition(base.parameters :+ baseurlParameter)
  }

  private[cli] def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] = {
      if (req.component.contains("client") && req.service.contains("http")) {
        _client_path_from_request(req).flatMap { path =>
          val baseurl = _client_baseurl_from_request(req)
          val rawUrl = _build_client_url(baseurl, path)
          val url = _append_client_query(rawUrl, req)
          observe_trace(
            s"[client:trace] client action request operation=${req.operation} path=${path} url=${url}"
          )
          req.operation match {
        case "post" =>
          _client_mime_body_from_request(req).map { body =>
            new PostCommand(
              req,
              // "system.ping", // TODO generic
              HttpRequest.fromUrl(
                method = HttpRequest.POST,
                url = new URL(url),
                body = body.map(_.value)
              )
            )
          }
        case "get" =>
          _client_explicit_mime_body_from_request(req).flatMap {
            case Some(_) =>
              Consequence.failure("client http get does not accept a body")
            case None =>
              Consequence.success(
                new GetQuery(
                  req,
                  // "system.ping",
                  HttpRequest.fromUrl(
                    method = HttpRequest.GET,
                    url = new URL(url)
                  )
                )
              )
          }
        case other =>
          Consequence.failure(s"client http operation not supported: ${other}")
      }
      }
    } else {
      Consequence.failure("client http request is required")
    }
  }

  private[cli] def _client_baseurl_from_request(
    req: Request
  ): String =
    req.properties.find(_.name == "baseurl").map(_.value.toString)
      .getOrElse(ClientConfig.DefaultBaseUrl)

  private[cli] def _build_client_url(
    baseurl: String,
    path: String
  ): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private[cli] def _append_client_query(
    url: String,
    req: Request
  ): String =
    _client_query_string(req) match {
      case Some(query) => s"${url}?${query}"
      case None => url
    }

  // TODO Phase 2.85: Replace this ad-hoc query parameter mapping with OperationDefinition-driven parameter handling.
  private[cli] def _client_query_string(
    req: Request
  ): Option[String] = {
    val argumentParams = req.arguments.collect {
      case Argument(name, value, _) if name.startsWith("arg") =>
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedValue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedName}=${encodedValue}"
    }
    val propertyParams = req.properties.collect {
      case Property(name, value, _) if _is_http_parameter_property(name) =>
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedValue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedName}=${encodedValue}"
    }
    val params = argumentParams ++ propertyParams
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private[cli] def _is_http_parameter_property(
    name: String
  ): Boolean =
    name != null &&
      name.nonEmpty &&
      name != "baseurl" &&
      name != "body" &&
      name != "data" &&
      name != "-d"

  private[cli] def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.failure("client http path is required")
    }

  private[cli] def _client_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("body", "data", "-d")).flatMap {
      case Some(body) => Consequence.success(Some(body))
      case None =>
        _client_form_mime_body(req) match {
          case Some(body) => Consequence.success(Some(body))
          case None => Consequence.success(_mime_body_from_arguments(req.arguments))
        }
    }

  private[cli] def _client_explicit_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("body", "data", "-d")).flatMap {
      case some @ Some(_) => Consequence.success(some)
      case None => Consequence.success(_mime_body_from_arguments(req.arguments))
    }

  private[cli] def _client_http_body_and_header(
    req: Request
  ): Consequence[(Option[MimeBody], Record)] =
    _client_mime_body_from_request(req).map {
      case Some(body) if _is_form_urlencoded(body) =>
        (Some(body), Record.create(Vector("Content-Type" -> "application/x-www-form-urlencoded")))
      case Some(body) =>
        (Some(body), Record.empty)
      case None =>
        (None, Record.empty)
    }

  private[cli] def _client_form_mime_body(
    req: Request
  ): Option[MimeBody] =
    _client_form_encoded_payload(req).map { payload =>
      MimeBody(
        ContentType.parse("application/x-www-form-urlencoded"),
        Bag.text(payload, StandardCharsets.UTF_8)
      )
    }

  private[cli] def _client_form_encoded_payload(
    req: Request
  ): Option[String] = {
    val argumentParams = req.arguments.collect {
      case Argument(name, value, _) if name != "path" && !value.isInstanceOf[MimeBody] =>
        s"${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${URLEncoder.encode(value.toString, StandardCharsets.UTF_8)}"
    }
    val switchParams = req.switches.collect {
      case Switch(name, value, _) if value =>
        s"${URLEncoder.encode(name, StandardCharsets.UTF_8)}=true"
    }
    val propertyParams = _client_http_parameter_properties(req).map { p =>
      s"${URLEncoder.encode(p.name, StandardCharsets.UTF_8)}=${URLEncoder.encode(p.value.toString, StandardCharsets.UTF_8)}"
    }
    val params = argumentParams ++ switchParams ++ propertyParams
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private[cli] def _client_http_parameter_properties(
    req: Request
  ): List[Property] =
    req.properties.filter(p => _is_http_parameter_property(p.name))

  private[cli] def _is_form_urlencoded(
    body: MimeBody
  ): Boolean =
    body.contentType.mimeType.value.equalsIgnoreCase("application/x-www-form-urlencoded")

  private[cli] def _mime_body_from_property_names(
    properties: List[Property],
    names: List[String]
  ): Consequence[Option[MimeBody]] =
    names match {
      case Nil => Consequence.success(None)
      case head :: tail =>
        properties.find(_.name == head) match {
          case Some(property) => _mime_body_from_value(property.value).map(Some(_))
          case None => _mime_body_from_property_names(properties, tail)
        }
    }

  private[cli] def _mime_body_from_value(
    value: Any
  ): Consequence[MimeBody] =
    value match {
      case mime: MimeBody => Consequence.success(mime)
      case bag: Bag => Consequence.success(MimeBody(ContentType.APPLICATION_OCTET_STREAM, bag))
      case text: String =>
        Consequence.success(
          MimeBody(ContentType.APPLICATION_OCTET_STREAM, Bag.text(text, StandardCharsets.UTF_8))
        )
      case _ =>
        Consequence.failure("client request body must be a MimeBody, Bag, or String")
    }

  private[cli] def _mime_body_from_arguments(
    arguments: List[Argument]
  ): Option[MimeBody] =
    arguments.collectFirst { case Argument(_, body: MimeBody, _) => body }

  def executeCommand(subsystem: Subsystem, req: Request): Int = {
    val primaryargs = _recover_command_args(_command_args_from_request(req), req)
    val args =
      if (primaryargs.nonEmpty) primaryargs
      else _recover_command_args(req.toSubCommand.toArgs, req)
    executeCommand(subsystem, args)
  }

  def executeCommand(subsystem: Subsystem, args: Array[String]): Int = {
    val normalizedArgs = CommandProtocolHelp.normalizeArgs(args) match {
      case Left(code) => return code
      case Right(xs) => xs
    }
    val result = _to_request(subsystem, normalizedArgs).flatMap { req =>
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

  private def _command_args_from_request(req: Request): Array[String] = {
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

  private def _recover_command_args(
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
      subsystem.execute(req)
    }
  }

  def executeActionResponse(
    subsystem: Subsystem,
    action: org.goldenport.cncf.action.Action
  ): Consequence[OperationResponse] =
    subsystem.executeAction(action)

  private def _to_request(
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
    _extract_runtime_options(args.toIndexedSeq) match { case (runtimeOptions, clean) =>
    _selectorAndArguments(clean).flatMap { case (selector0, tail) =>
      val normalized = _normalize_meta_selector(subsystem, selector0, tail.toVector)
      val aliasresolver =
        if (subsystem.aliasResolver ne AliasResolver.empty) subsystem.aliasResolver
        else _alias_resolver
      val rewritten = PathPreNormalizer.rewriteSelector(normalized._1, mode, aliasresolver)
      val (selector, suffixformat) = _extract_selector_format(rewritten)
      _resolve_selector(subsystem, selector, runtimeOptions, mode) match {
        case Consequence.Success((component, service, operation)) =>
          val parsed = for {
            comp <- subsystem.components.find(_.name == component)
            svc <- comp.protocol.services.services.find(_.name == service)
            opdef <- svc.operations.operations.find(_.name == operation)
          } yield _args_parser.parse(opdef, normalized._2.toList)
          val (arguments, switches, properties) = parsed.map { req =>
            (req.arguments, req.switches, req.properties)
          }.getOrElse {
            (normalized._2.zipWithIndex.map { case (value, index) =>
              Argument(s"arg${index + 1}", value, None)
            }.toList, Nil, Nil)
          }
          val runtimeproperties = _runtime_properties(runtimeOptions, mode).filterNot(p =>
            p.name.equalsIgnoreCase("textus.format") ||
              p.name.equalsIgnoreCase("cncf.format")
          )
          val allproperties = _with_format_property(
            properties ++ runtimeproperties,
            _resolve_format(runtimeOptions, suffixformat, mode)
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

  private def _resolve_selector(
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

  private def _resolve_selector_with_path_resolution(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] = {
    val registry = subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => CanonicalPath(comp.name, service.name, op.name))
      }
    }
    val builtins = subsystem.components.collect {
      case c if c.origin == org.goldenport.cncf.component.ComponentOrigin.Builtin => c.name
    }.toSet
    PathResolution.resolve(selector, registry, builtins) match {
      case PathResolutionResult.Success(path) =>
        Consequence.success((path.component, path.service, path.operation))
      case PathResolutionResult.Failure(reason) =>
        Consequence.failure(s"path-resolution failed: $reason")
    }
  }

  private def _resolve_selector_with_operation_resolver(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] =
    subsystem.resolver.resolve(selector, allowPrefix = false, allowImplicit = false) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Consequence.success((component, service, operation))
      case ResolutionResult.NotFound(stage, input) =>
        Consequence.failure(s"${stage.toString.toLowerCase} not found: $input")
      case ResolutionResult.Ambiguous(input, candidates) =>
        Consequence.failure(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
      case ResolutionResult.Invalid(reason) =>
        Consequence.failure(s"invalid selector: $reason")
    }

  private def _extract_selector_format(
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

  private def _with_format_property(
    properties: List[Property],
    format: String
  ): List[Property] = {
    val withoutformat = properties.filterNot(p =>
      p.name.equalsIgnoreCase("textus.format") || p.name.equalsIgnoreCase("cncf.format")
    )
    withoutformat :+ Property("textus.format", format, None)
  }

  private def _resolve_format(
    options: RuntimeOptionsParser.Options,
    suffixFormat: Option[String],
    mode: RunMode
  ): String =
    options.format
      .orElse(if (options.json) Some("json") else None)
      .orElse(suffixFormat)
      .getOrElse(RuntimeDefaults.defaultFormat(mode))

  private def _normalize_meta_selector(
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
          case Some(componentName) =>
            (s"$componentName.meta.$operation", tail)
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

  private def _default_meta_component_name(
    subsystem: Subsystem
  ): Option[String] =
    subsystem.components.sortBy(_.name).headOption.map(_.name)

  private def _extract_runtime_options(
    args: Seq[String]
  ): (RuntimeOptionsParser.Options, Seq[String]) =
    RuntimeOptionsParser.extract(args)

  private def _runtime_properties(
    options: RuntimeOptionsParser.Options,
    mode: RunMode = RunMode.Command
  ): List[Property] =
    RuntimeOptionsParser.properties(options, mode)

  private def _alias_resolver: AliasResolver =
    GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)

  private def _selectorAndArguments(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    args.toVector match {
      case Vector() =>
        Consequence.failure("command name is required")
      case Vector(single, rest @ _*) if single.contains("/") =>
        _selectorFromPath(single, "/").map(_ -> rest.toVector)
      case Vector(single, rest @ _*) if single.contains(".") =>
        Consequence.success((single, rest.toVector))
      case Vector(component, service, operation, rest @ _*) =>
        Consequence.success((s"$component.$service.$operation", rest.toVector))
      case Vector(single, rest @ _*) =>
        Consequence.success((single, rest.toVector))
    }
  }

  private def _selectorFromPath(
    value: String,
    delimiter: String
  ): Consequence[String] = {
    val segments = value.split(delimiter).toVector.filter(_.nonEmpty)
    if (segments.size == 3) {
      Consequence.success(segments.mkString("."))
    } else {
      delimiter match {
        case "/" => Consequence.failure("command path must be /component/service/operation")
        case "." => Consequence.failure("command must be component.service.operation")
        case _ => Consequence.failure("command selector is invalid")
      }
    }
  }

  def executeServerEmulator(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    // val cwd = Paths.get("").toAbsolutePath.normalize
    // val configuration = _resolve_configuration(cwd)
    // val runtimeConfig = _runtime_config(configuration)
    val runtimeConfig: RuntimeConfig = ???
    val (includeHeader, rest) = _include_header(args)
    val result = normalizeServerEmulatorArgs(rest, runtimeConfig.serverEmulatorBaseUrl) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val engine = new HttpExecutionEngine(subsystem)
            val res = engine.execute(req)
            if (includeHeader) {
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
    _exit_code(result)
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
    args: Seq[String],
    baseUrl: String
  ): Consequence[Seq[String]] = {
    if (args.isEmpty) {
      Consequence.failure("server-emulator requires a path or URL")
    } else if (args.exists(_.contains("://"))) {
      Consequence.success(args)
    } else {
      _parse_component_service_operation(args).map {
        case (component, service, operation) =>
          Seq(_serverEmulatorUrl(baseUrl, component, service, operation))
      }
    }
  }

  private def _serverEmulatorUrl(
    baseUrl: String,
    component: String,
    service: String,
    operation: String
  ): String = {
    val trimmed = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
    s"${trimmed}/${component}/${service}/${operation}"
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

  def executeScript(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    _to_request_script(subsystem, args).flatMap { req =>
      subsystem.execute(req)
    }
    ???
  }

  private def _to_request_script(
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
            val xs = Vector("SCRIPT", "DEFAULT", "RUN") ++ in
            _to_request(subsystem, xs.toArray, RunMode.Script)
        }
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

  private def _execute_top_level_help(args: Array[String]): Option[Int] =
    args.toVector match {
      case Vector("help") => Some(CliHelpOperation.execute())
      case Vector("server", "help") => Some(ServerCommandHelp.execute())
      case Vector("client", "help") => Some(ClientCommandHelp.execute())
      case _ => None
    }

  private def _normalize_help_aliases(args: Array[String]): Array[String] =
    args.toVector match {
      case Vector(flag) if _help_flags.contains(flag) =>
        Array("help")
      case Vector(command, flag) if _help_flags.contains(flag) =>
        Array(command, "help")
      case _ =>
        args
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
        |
        |Run 'cncf help' for more information.
        |""".stripMargin
    _print_error(text)
  }
  private def _print_response(res: Response): Unit =
    println(res.print)

  private def _print_operation_response(res: OperationResponse): Unit = {
    res match {
      case OperationResponse.Http(http) =>
        val body = http.getString.getOrElse(http.print)
        Console.out.println(body)
      case OperationResponse.RecordResponse(record) =>
        _print_response(Response.Yaml(RuntimeContext.Context.default.transformRecord(record).toYamlString))
      case _ =>
        _print_response(res.toResponse)
    }
  }

  private def _print_error(c: Conclusion): Unit = {
    val rec = c.toRecord
    val s = rec.toYamlString
    Console.err.print(s)
  }

  private def _print_error(message: String): Unit = {
    Console.err.println(message)
  }
}
