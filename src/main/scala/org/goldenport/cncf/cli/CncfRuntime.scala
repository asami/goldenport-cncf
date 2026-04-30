package org.goldenport.cncf.cli

import java.io.ByteArrayOutputStream
import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.bag.Bag
import org.goldenport.cli.parser.ArgsParser
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolver, ConfigurationSources, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.configuration.source.ProjectRootFinder
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.builtin.client.{GetQuery, PostCommand}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentInit, ComponentOrigin}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.config.{ClientConfig, RuntimeConfig, RuntimeDefaults, RuntimeFileConfigLoader}
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Property, Protocol, ProtocolEngine, Request, Response, Switch}
import org.goldenport.datatype.{ContentType, FileBundle, MimeBody}
import org.goldenport.protocol.operation.{OperationResponse, OperationRequest}
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.{Multiplicity, ValueDomain, XFileBundle, XString}
import org.goldenport.value.BaseContent
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.http.{FakeHttpDriver, Http4sHttpServer, HttpDriver, HttpExecutionEngine, HttpDriverFactory}
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfHandle}
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.importer.StartupImport
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.resolver.{CanonicalPath, PathResolution, PathResolutionResult}
import org.goldenport.cncf.cli.RuntimeParameterParser
import org.goldenport.cncf.cli.help.{CliHelpOperation, ClientCommandHelp, CommandProtocolHelp, HelpOperation, ServerCommandHelp}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.goldenport.cncf.observability.global.{GlobalObservable, GlobalObservability, GlobalObservabilityGate, ObservabilityRoot}
import org.goldenport.record.Record
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 * @version May.  1, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRuntime extends GlobalObservable {
  private[cncf] final case class RuntimeFrontParameters(
    factoryClasses: Vector[String],
    discoverClasses: Boolean,
    workspace: Option[Path],
    forceExit: Boolean,
    noExit: Boolean,
    residualArgs: Array[String]
  )

  private[cncf] final case class RuntimeBootstrap(
    configuration: ResolvedConfiguration,
    front: RuntimeFrontParameters,
    invocation: RuntimeInvocationParameters,
    repositories: RuntimeRepositoryParameters
  )

  private[cncf] final case class RuntimeRepositoryParameters(
    activeRepositories: Either[String, Vector[ComponentRepository.Specification]],
    searchRepositories: Either[String, Vector[ComponentRepository.Specification]]
  )

  private[cncf] final case class RuntimeInvocationParameters(
    actualArgs: Array[String],
    subsystemName: Option[String],
    componentName: Option[String]
  )

  private final case class RuntimeLaunch(
    cwd: Path,
    configuration: ResolvedConfiguration,
    activeSpecifications: Vector[org.goldenport.cncf.component.repository.ComponentRepository.Specification],
    logBackendOption: Option[String],
    logLevelOption: Option[String],
    actualArgs: Array[String],
    runtimeParse: RuntimeParameterParseResult,
    domainArgs: Array[String],
    mode: RunMode,
    runtimeConfig: RuntimeConfig,
    aliasResolver: AliasResolver
  )

  private val _configuration_application_name = "textus"
  private val _help_flags = Set("--help", "-h")
  private val _runtime_service_name = "runtime"
  private lazy val _runtime_parameter_parser = new RuntimeParameterParser()
  private val _args_parser = new ArgsParser(ArgsParser.Config())

  private val _runtime_protocol: Protocol =
    Protocol.Builder()
      .addOperation(_runtime_service_name, RunMode.Server.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Client.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Command.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.ServerEmulator.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Script.name, RequestDefinition(), ResponseDefinition.void)
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
      "textus.logging.backend",
      "textus.logging.level",
      "textus.runtime.logging.backend",
      "textus.runtime.logging.level",
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

    val logfile = get("textus.logging.slf4j.file.path")
      .orElse(get("textus.runtime.logging.slf4j.file.path"))
      .orElse(get("textus.logging.external.file.path"))
      .getOrElse("target/cncf.d/external.log")
    val level = get("textus.logging.slf4j.level")
      .orElse(get("textus.runtime.logging.slf4j.level"))
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
        _config_string(
          conf,
          "textus.environment",
          "textus.runtime.environment",
          "cncf.environment",
          "cncf.runtime.environment"
        )
          .map(_.getOrElse("default"))

      val obs =
        _config_string(
          conf,
          "textus.observability",
          "textus.runtime.observability",
          "cncf.observability",
          "cncf.runtime.observability"
        )
          .map(_.map(v => Map("default" -> v)).getOrElse(Map.empty))

      (env, obs).mapN(Runtime.apply)
    }

    private def _config_string(
      conf: ResolvedConfiguration,
      primary: String,
      aliases: String*
    ): org.goldenport.Consequence[Option[String]] =
      conf.get[String](primary) match {
        case success @ Consequence.Success(Some(_)) => success
        case Consequence.Success(None) =>
          aliases.foldLeft(Consequence.success(None): Consequence[Option[String]]) {
            case (acc @ Consequence.Success(Some(_)), _) => acc
            case (Consequence.Success(None), alias) => conf.get[String](alias)
            case (failure @ Consequence.Failure(_), _) => failure
          }
        case failure @ Consequence.Failure(_) => failure
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

  private[cncf] def frontParameters(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): RuntimeFrontParameters = {
    val (factoryClasses, args2) = _take_component_factory_classes(configuration, args)
    val (discover, args3) = _take_discover_classes(configuration, args2)
    val (workspace, args4) = _take_workspace(configuration, args3)
    val (forceExit, args5) = _take_force_exit(configuration, args4)
    val (noExit, rest) = _take_no_exit(configuration, args5)
    RuntimeFrontParameters(factoryClasses, discover, workspace, forceExit, noExit, rest)
  }

  private[cncf] def bootstrap(
    cwd: Path,
    args: Array[String]
  ): RuntimeBootstrap = {
    val normalizedargs = _normalize_source_args(args)
    if (!normalizedargs.sameElements(args))
      return bootstrap(cwd, normalizedargs)
    val configuration = _resolve_configuration(cwd, args)
    val front = frontParameters(configuration, args)
    val invocation = canonicalInvocationParameters(configuration, front.residualArgs)
    val withcomponentfile = _with_auto_component_file(cwd, args, configuration, invocation)
    if (!withcomponentfile.sameElements(args))
      return bootstrap(cwd, withcomponentfile)
    val repositories = repositoryParameters(configuration, args, cwd)
    RuntimeBootstrap(configuration, front, invocation, repositories)
  }

  private def _with_auto_component_file(
    cwd: Path,
    args: Array[String],
    configuration: ResolvedConfiguration,
    invocation: RuntimeInvocationParameters
  ): Array[String] = {
    val hasComponentFile =
      RuntimeConfig.getString(configuration, RuntimeConfig.ComponentFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeComponentFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.ComponentDevDirKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.ComponentCarDirKey).nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.component.dev.dir").nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.component.car.dir").nonEmpty ||
        args.exists(_.startsWith("--component-file=")) ||
        args.contains("--component-file") ||
        args.exists(_.startsWith("--component-dev-dir=")) ||
        args.contains("--component-dev-dir") ||
        args.exists(_.startsWith("--component-car-dir=")) ||
        args.contains("--component-car-dir") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.ComponentFileKey}=")) ||
        args.contains(s"--${RuntimeConfig.ComponentFileKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.RuntimeComponentFileKey}=")) ||
        args.contains(s"--${RuntimeConfig.RuntimeComponentFileKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.ComponentDevDirKey}=")) ||
        args.contains(s"--${RuntimeConfig.ComponentDevDirKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.ComponentCarDirKey}=")) ||
        args.contains(s"--${RuntimeConfig.ComponentCarDirKey}")
    val hasSubsystem =
      RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeSubsystemFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemDescriptorKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeSubsystemDescriptorKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemDevDirKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeSubsystemDevDirKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemSarDirKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeSubsystemSarDirKey).nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.descriptor").nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.file").nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.dev.dir").nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.sar.dir").nonEmpty
    if (hasComponentFile || hasSubsystem) {
      args
    } else {
      val archive = invocation.componentName match {
        case Some(name) => _detect_component_archive(cwd, name)
        case None => _detect_latest_component_archive(cwd)
      }
      archive.map { path =>
        args ++ Array(s"--${RuntimeConfig.ComponentFileKey}=${path.toString}")
      }.getOrElse(args)
    }
  }

  private def _detect_component_archive(
    cwd: Path,
    componentname: String
  ): Option[Path] = {
    val roots = Vector(
      cwd.resolve("component").resolve("target"),
      cwd.resolve("target")
    ).map(_.normalize)
    roots.iterator.flatMap(_latest_component_archive(_, componentname)).toSeq.headOption
  }

  private def _detect_latest_component_archive(
    cwd: Path
  ): Option[Path] = {
    val roots = Vector(
      cwd.resolve("component").resolve("target"),
      cwd.resolve("target")
    ).map(_.normalize)
    roots.iterator.flatMap(_latest_component_archive(_)).toSeq.headOption
  }

  private def _latest_component_archive(
    root: Path,
    componentname: String
  ): Option[Path] =
    _component_archives(root)
      .filter { path =>
        val name = path.getFileName.toString
        name == s"${componentname}.car" || name.startsWith(s"${componentname}-")
      }
      .headOption

  private def _latest_component_archive(
    root: Path
  ): Option[Path] =
    _component_archives(root).headOption

  private def _component_archives(
    root: Path
  ): Vector[Path] =
    if (!Files.isDirectory(root)) {
      Vector.empty
    } else {
      val stream = Files.list(root)
      try {
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .filter(path => path.getFileName.toString.endsWith(".car"))
          .toVector
          .sortBy(path => Files.getLastModifiedTime(path).toMillis)(Ordering.Long.reverse)
      } finally {
        stream.close()
      }
    }

  private[cncf] def repositoryParameters(
    configuration: ResolvedConfiguration,
    args: Array[String],
    cwd: Path
  ): RuntimeRepositoryParameters = {
    val extracted =
      ComponentRepositorySpace.extractRepositoryArgs(configuration, args)
    val activeRepositories =
      ComponentRepositorySpace.appendDefaultActiveRepositories(
        ComponentRepositorySpace.resolveSpecifications(extracted.active, cwd, extracted.noDefault),
        cwd,
        extracted.noDefault
      )
    val searchRepositories =
      ComponentRepositorySpace.appendDefaultSearchRepositories(
        ComponentRepositorySpace.resolveSpecifications(extracted.search, cwd, extracted.noDefault),
        activeRepositories.getOrElse(Vector.empty),
        cwd,
        extracted.noDefault
      )
    RuntimeRepositoryParameters(
      activeRepositories = activeRepositories,
      searchRepositories = searchRepositories
    )
  }

  private[cncf] def canonicalInvocationParameters(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): RuntimeInvocationParameters = {
    val actualArgs = _strip_invocation_selection_args(_normalize_help_aliases(args))
    RuntimeInvocationParameters(
      actualArgs = actualArgs,
      subsystemName = _subsystem_name(configuration, args),
      componentName = _component_name(configuration, args)
    )
  }

  private[cncf] def resolveSubsystemInvocation(
    invocation: RuntimeInvocationParameters,
    searchSpecs: Vector[ComponentRepository.Specification],
    activeSpecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): RuntimeInvocationParameters = {
    val componentResolved = resolveComponentInvocation(invocation, searchSpecs, activeSpecs)
    val args = componentResolved.actualArgs
    val alreadySpecified =
      args.exists(_has_option_value(_, _subsystem_descriptor_keys)) ||
        args.sliding(2).exists {
          case Array(k, _) => _is_option_name(k, _subsystem_descriptor_keys)
          case _ => false
        }
    if (alreadySpecified) {
      componentResolved
    } else {
      componentResolved.subsystemName
        .flatMap(name => _resolve_subsystem_descriptor_entry(searchSpecs, name))
        .map { case (spec, descriptor) =>
          val repoArgs =
            _active_spec_argument(spec)
              .filterNot {
                case (RuntimeConfig.ComponentDirKey, value) =>
                  _has_component_dir_config_arg(args, value)
                case _ =>
                  false
              }
              .map { case (key, value) => Array(s"--${key}=${value}") }
              .getOrElse(Array.empty[String])
          componentResolved.copy(actualArgs = args ++ repoArgs ++ Array(s"--${RuntimeConfig.SubsystemFileKey}=${descriptor.path}"))
        }
        .getOrElse(componentResolved)
    }
  }

  private[cncf] def resolveComponentInvocation(
    invocation: RuntimeInvocationParameters,
    searchSpecs: Vector[ComponentRepository.Specification],
    activeSpecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): RuntimeInvocationParameters = {
    val args = invocation.actualArgs
    val alreadySpecified =
      _has_component_activation_arg(args) ||
        args.sliding(2).exists {
          case Array(k, _) =>
            _is_component_activation_arg(k)
          case _ =>
            false
        }
    if (alreadySpecified) {
      invocation
    } else {
      invocation.componentName
        .flatMap { name =>
          _resolve_component_archive_entry(searchSpecs, name)
            .map { path =>
              invocation.copy(actualArgs = args ++ Array(s"--${RuntimeConfig.ComponentFileKey}=${path}"))
            }
            .orElse {
              _resolve_component_descriptor_entry(searchSpecs, name)
                .flatMap { case (spec, _) =>
                  _active_spec_argument(spec)
                    .filterNot {
                      case (RuntimeConfig.ComponentDirKey, value) =>
                        _has_component_dir_config_arg(args, value)
                      case _ =>
                        false
                    }
                    .map { case (key, value) =>
                      invocation.copy(actualArgs = args ++ Array(s"--${key}=${value}"))
                    }
                }
            }
        }
        .getOrElse(invocation)
    }
  }

  private[cncf] def componentExtraFunction(
    specs: Vector[ComponentRepository.Specification],
    front: RuntimeFrontParameters
  ): Subsystem => Seq[Component] =
    _trace_component_dir_extras(
      _component_extra_function(
        specs,
        front.discoverClasses,
        front.workspace,
        front.factoryClasses
      )
    )

  private def _prepare_launch(
    cwd: Path,
    args: Array[String]
  ): Either[Int, RuntimeLaunch] = {
    val bootstrap = this.bootstrap(cwd, args)
    val configuration = bootstrap.configuration
    configure_slf4j_simple(configuration)
    val extracted =
      ComponentRepositorySpace.extractRepositoryArgs(configuration, args)
    val (backendoption, logLevelOption, actualargs0) =
      _extract_log_options(extracted.residual)
    val invocation = canonicalInvocationParameters(configuration, actualargs0)
    val actualargs = invocation.actualArgs
    _execute_top_level_help(actualargs) match {
      case Some(code) => return Left(code)
      case None => ()
    }
    if (actualargs.isEmpty) {
      _print_usage()
      return Left(2)
    }
    bootstrap.repositories.activeRepositories match {
      case Left(message) =>
        Console.err.println(message)
        Left(2)
      case Right(specs) =>
        val runtimeParse = _runtime_parameter_parser.parse(actualargs.toIndexedSeq)
        val domainArgs = _strip_configuration_args(runtimeParse.residual.toArray)
        val mode = _mode_from_args(domainArgs)
        val runtimeConfig = _runtime_config(configuration)
        val aliasResolver = _alias_resolver(configuration)
        Right(
          RuntimeLaunch(
            cwd = cwd,
            configuration = configuration,
            activeSpecifications = specs,
            logBackendOption = backendoption,
            logLevelOption = logLevelOption,
            actualArgs = actualargs,
            runtimeParse = runtimeParse,
            domainArgs = domainArgs,
            mode = mode,
            runtimeConfig = runtimeConfig,
            aliasResolver = aliasResolver
          )
        )
    }
  }

  private def _prepare_runtime(
    launch: RuntimeLaunch
  ): Unit = {
    val logBackend = _decide_backend(
      launch.logBackendOption,
      _logging_backend_from_configuration(launch.configuration),
      launch.mode
    )
    _install_log_backend(logBackend)
    _update_visibility_policy(launch.logLevelOption, launch.configuration, launch.mode)
    _reset_global_runtime_context()
    _create_global_runtime_context(
      launch.runtimeConfig,
      launch.configuration,
      launch.aliasResolver
    )
    _mark_used_parameters(launch.configuration)
  }

  // legacy: see run
  def runWithExtraComponents(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    _prepare_launch(cwd, args) match {
      case Left(code) =>
        code
      case Right(launch) =>
        _prepare_runtime(launch)
        val r: Consequence[OperationRequest] =
          _runtime_protocol_engine.makeOperationRequest(launch.domainArgs)
        r match {
          case Consequence.Success(req) =>
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
            observe_trace(
              s"[subsytem] runWithExtraComponents dispatching mode args=${launch.domainArgs.drop(1).mkString(" ")}"
            )
            requestmode match {
              case Some(RunMode.Server) =>
                val subsystem = buildSubsystem(extraComponents, Some(RunMode.Server), launch.actualArgs)
                new CncfRuntime().startServer(subsystem, launch.domainArgs.drop(1))
                0
              case Some(RunMode.Client) =>
                val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client), launch.actualArgs)
                new CncfRuntime().executeClient(subsystem, launch.domainArgs.drop(1))
              case Some(RunMode.Command) =>
                val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command), launch.actualArgs)
                new CncfRuntime().executeCommand(subsystem, launch.actualArgs.drop(1))
              case Some(RunMode.ServerEmulator) =>
                executeServerEmulator(launch.domainArgs.drop(1), extraComponents)
              case Some(RunMode.Script) =>
                _run_script(launch.domainArgs.drop(1), extraComponents)
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
    _prepare_launch(cwd, args) match {
      case Left(code) =>
        if (code == 2) {
          val normalizedArgs = _normalize_help_aliases(args)
          if (normalizedArgs.nonEmpty) {
            ()
          }
        }
        code
      case Right(launch) =>
        _prepare_runtime(launch)
        val r: Consequence[OperationRequest] =
          _runtime_protocol_engine.makeOperationRequest(launch.domainArgs)
        r match {
          case Consequence.Success(req) =>
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
                val subsystem = buildSubsystem(mode = Some(RunMode.Server), args = launch.actualArgs)
                new CncfRuntime().startServer(subsystem, launch.domainArgs.drop(1))
                0
              case Some(RunMode.Client) =>
                observe_trace(
                  s"[client:trace] run dispatching to client mode args=${launch.domainArgs.drop(1).mkString(" ")}"
                )
                val subsystem = buildSubsystem(mode = Some(RunMode.Client), args = launch.actualArgs)
                new CncfRuntime().executeClient(subsystem, (launch.runtimeParse.consumed ++ launch.domainArgs.drop(1)).toArray)
              case Some(RunMode.Command) =>
                val subsystem = buildSubsystem(mode = Some(RunMode.Command), args = launch.actualArgs)
                new CncfRuntime().executeCommand(subsystem, launch.actualArgs.drop(1))
              case Some(RunMode.ServerEmulator) =>
                executeServerEmulator(launch.domainArgs.drop(1))
              case Some(RunMode.Script) =>
                _run_script(launch.domainArgs.drop(1), _ => Nil)
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

  private def _subsystem_name(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _subsystem_name_from_args(args)
      .orElse(org.goldenport.cncf.subsystem.GenericSubsystemFactory.subsystemName(configuration))

  private def _component_name(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): Option[String] =
    _component_name_from_args(args)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.ComponentNameKey))

  private def _subsystem_name_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (_is_option_name(current, _subsystem_name_keys) && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else {
        _option_value(current, _subsystem_name_keys) match {
          case Some(value) => return Some(value)
          case None => ()
        }
      }
      i += 1
    }
    None
  }

  private def _component_name_from_args(
    args: Array[String]
  ): Option[String] = {
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (_is_option_name(current, _component_name_keys) && i + 1 < args.length) {
        return Option(args(i + 1)).map(_.trim).filter(_.nonEmpty)
      } else {
        _option_value(current, _component_name_keys) match {
          case Some(value) => return Some(value)
          case None => ()
        }
      }
      i += 1
    }
    None
  }

  private def _strip_invocation_selection_args(
    args: Array[String]
  ): Array[String] = {
    val buffer = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (
        _is_option_name(current, _subsystem_name_keys ++ _component_name_keys)
      ) {
        i += (if (i + 1 < args.length) 2 else 1)
      } else if (
        _has_option_value(current, _subsystem_name_keys ++ _component_name_keys)
      ) {
        i += 1
      } else {
        buffer += current
        i += 1
      }
    }
    buffer.result().toArray
  }

  private def _subsystem_name_keys: Vector[String] =
    Vector(
      RuntimeConfig.SubsystemNameKey,
      RuntimeConfig.RuntimeSubsystemNameKey,
      "cncf.subsystem",
      "cncf.runtime.subsystem"
    )

  private def _component_name_keys: Vector[String] =
    Vector(
      RuntimeConfig.ComponentNameKey,
      RuntimeConfig.RuntimeComponentNameKey,
      "cncf.component",
      "cncf.runtime.component"
    )

  private def _subsystem_descriptor_keys: Vector[String] =
    Vector(
      RuntimeConfig.SubsystemDescriptorKey,
      RuntimeConfig.SubsystemFileKey,
      RuntimeConfig.SubsystemDevDirKey,
      RuntimeConfig.SubsystemSarDirKey,
      RuntimeConfig.RuntimeSubsystemDescriptorKey,
      RuntimeConfig.RuntimeSubsystemFileKey,
      RuntimeConfig.RuntimeSubsystemDevDirKey,
      RuntimeConfig.RuntimeSubsystemSarDirKey,
      "cncf.subsystem.descriptor",
      "cncf.subsystem.file",
      "cncf.subsystem.dev.dir",
      "cncf.subsystem.sar.dir",
      "cncf.runtime.subsystem.descriptor",
      "cncf.runtime.subsystem.file",
      "cncf.runtime.subsystem.dev.dir",
      "cncf.runtime.subsystem.sar.dir"
    )

  private def _normalize_source_args(
    args: Array[String]
  ): Array[String] = {
    val buffer = Vector.newBuilder[String]
    var changed = false
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--subsystem-sar-dir=")) {
        buffer += s"--${RuntimeConfig.SubsystemSarDirKey}=${current.stripPrefix("--subsystem-sar-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--subsystem-dev-dir=")) {
        buffer += s"--${RuntimeConfig.SubsystemDevDirKey}=${current.stripPrefix("--subsystem-dev-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-car-dir=")) {
        buffer += s"--${RuntimeConfig.ComponentCarDirKey}=${current.stripPrefix("--component-car-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-dev-dir=")) {
        buffer += s"--${RuntimeConfig.ComponentDevDirKey}=${current.stripPrefix("--component-dev-dir=")}"
        changed = true
        i += 1
      } else if (current.startsWith("--component-file=")) {
        buffer += s"--${RuntimeConfig.ComponentFileKey}=${current.stripPrefix("--component-file=")}"
        changed = true
        i += 1
      } else if (current == "--subsystem-sar-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.SubsystemSarDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--subsystem-dev-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.SubsystemDevDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-car-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.ComponentCarDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-dev-dir" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.ComponentDevDirKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else if (current == "--component-file" && i + 1 < args.length) {
        buffer += s"--${RuntimeConfig.ComponentFileKey}=${args(i + 1)}"
        changed = true
        i += 2
      } else {
        buffer += current
        i += 1
      }
    }
    val result = buffer.result().toArray
    if (changed) result else args
  }

  private def _is_option_name(
    current: String,
    keys: Vector[String]
  ): Boolean =
    keys.exists(key => current == s"--${key}")

  private def _has_option_value(
    current: String,
    keys: Vector[String]
  ): Boolean =
    keys.exists(key => current.startsWith(s"--${key}="))

  private def _option_value(
    current: String,
    keys: Vector[String]
  ): Option[String] =
    keys.iterator.flatMap { key =>
      val prefix = s"--${key}="
      if (current.startsWith(prefix))
        Option(current.drop(prefix.length)).map(_.trim).filter(_.nonEmpty)
      else
        None
    }.toSeq.headOption

  private def _resolve_subsystem_descriptor_entry(
    specs: Vector[ComponentRepository.Specification],
    subsystemName: String
  ): Option[(ComponentRepository.Specification, GenericSubsystemDescriptor)] =
    specs.iterator.flatMap { spec =>
      spec.resolveSubsystemDescriptor(subsystemName).map(spec -> _)
    }.toSeq.headOption

  private def _resolve_component_descriptor_entry(
    specs: Vector[ComponentRepository.Specification],
    componentName: String
  ): Option[(ComponentRepository.Specification, org.goldenport.cncf.component.ComponentDescriptor)] =
    specs.iterator.flatMap { spec =>
      spec.resolveComponentDescriptor(componentName).map(spec -> _)
    }.toSeq.headOption

  private def _resolve_component_archive_entry(
    specs: Vector[ComponentRepository.Specification],
    componentName: String
  ): Option[java.nio.file.Path] =
    specs.iterator.flatMap(_.resolveComponentArchivePath(componentName)).toSeq.headOption

  private def _spec_argument(
    spec: ComponentRepository.Specification
  ): Option[String] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(baseDir) =>
        Some(s"component-dir:${baseDir}")
      case ComponentRepository.ComponentFileRepository.Specification(file) =>
        Some(s"component-file:${file}")
      case ComponentRepository.ComponentDevDirRepository.Specification(baseDir) =>
        Some(s"component-dev-dir:${baseDir}")
      case ComponentRepository.SubsystemDevDirRepository.Specification(baseDir) =>
        Some(s"subsystem-dev-dir:${baseDir}")
      case ComponentRepository.ScalaCliRepository.Specification(baseDir) =>
        Some(s"scala-cli:${baseDir}")
    }

  private def _has_component_dir_config_arg(
    args: Array[String],
    value: String
  ): Boolean =
    args.contains(s"--${RuntimeConfig.ComponentDirKey}=${value}") ||
      args.sliding(2).exists {
        case Array(currentKey, currentValue) =>
          currentKey == s"--${RuntimeConfig.ComponentDirKey}" && currentValue == value
        case _ => false
      }

  private def _component_activation_keys: Vector[String] =
    Vector(
      RuntimeConfig.ComponentDirKey,
      RuntimeConfig.ComponentFileKey,
      RuntimeConfig.RuntimeComponentFileKey,
      RuntimeConfig.ComponentDevDirKey,
      RuntimeConfig.ComponentCarDirKey,
      RuntimeConfig.SubsystemDevDirKey,
      RuntimeConfig.SubsystemSarDirKey,
      RuntimeConfig.RuntimeSubsystemDevDirKey,
      RuntimeConfig.RuntimeSubsystemSarDirKey,
      "cncf.component.dir",
      "cncf.component.file",
      "cncf.runtime.component.file",
      "cncf.component.dev.dir",
      "cncf.component.car.dir",
      "cncf.subsystem.dev.dir",
      "cncf.subsystem.sar.dir",
      "cncf.runtime.subsystem.dev.dir",
      "cncf.runtime.subsystem.sar.dir"
    )

  private def _has_component_activation_arg(
    args: Array[String]
  ): Boolean =
    args.exists { arg =>
      arg == "--component-file" ||
        arg == "--component-dev-dir" ||
        arg == "--component-car-dir" ||
        arg == "--subsystem-dev-dir" ||
        arg == "--subsystem-sar-dir" ||
        arg.startsWith("--component-dev-dir=") ||
        arg.startsWith("--component-car-dir=") ||
        arg.startsWith("--subsystem-dev-dir=") ||
        arg.startsWith("--subsystem-sar-dir=") ||
        _has_option_value(arg, _component_activation_keys)
    }

  private def _is_component_activation_arg(
    arg: String
  ): Boolean =
    arg == "--component-file" ||
      arg == "--component-dev-dir" ||
      arg == "--component-car-dir" ||
      arg == "--subsystem-dev-dir" ||
      arg == "--subsystem-sar-dir" ||
      _is_option_name(arg, _component_activation_keys)

  private def _active_spec_argument(
    spec: ComponentRepository.Specification
  ): Option[(String, String)] =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(baseDir) =>
        Some((RuntimeConfig.ComponentDirKey, baseDir.toString))
      case ComponentRepository.ComponentFileRepository.Specification(file) =>
        Some((RuntimeConfig.ComponentFileKey, file.toString))
      case ComponentRepository.ComponentDevDirRepository.Specification(baseDir) =>
        Some((RuntimeConfig.ComponentDevDirKey, baseDir.toString))
      case ComponentRepository.SubsystemDevDirRepository.Specification(baseDir) =>
        Some((RuntimeConfig.SubsystemDevDirKey, baseDir.toString))
      case _ =>
        None
    }

  private def _discover_components(
    workspace: Option[Path]
  ): Subsystem => Seq[Component] = {
    val classDirs = _class_dirs_(workspace)
    if (classDirs.isEmpty) {
      _ => Nil
    } else {
      (subsystem: Subsystem) => {
        val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
        _discover_from_class_dirs_(params, classDirs, _package_prefixes_())
      }
    }
  }

  private def _discover_from_repositories(
    specs: Seq[ComponentRepository.Specification]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val descriptors = subsystem.descriptor.map(_.toComponentDescriptors).getOrElse(Vector.empty)
      val params = ComponentCreate(subsystem, ComponentOrigin.Builtin, descriptors)
      specs.flatMap { spec =>
        val origin = _origin_for_spec(spec)
        spec.build(params.withOrigin(origin)).discover()
      }
    }

  private def _origin_for_spec(
    spec: ComponentRepository.Specification
  ): ComponentOrigin =
    spec match {
      case _: ComponentRepository.ComponentDirRepository.Specification =>
        ComponentOrigin.Repository("component-dir")
      case _: ComponentRepository.ComponentFileRepository.Specification =>
        ComponentOrigin.Repository("component-file")
      case _: ComponentRepository.ComponentDevDirRepository.Specification =>
        ComponentOrigin.Repository("component-dev-dir")
      case _: ComponentRepository.SubsystemDevDirRepository.Specification =>
        ComponentOrigin.Repository("subsystem-dev-dir")
      case _: ComponentRepository.ScalaCliRepository.Specification =>
        ComponentOrigin.Repository("scala-cli")
    }

  private def _component_extra_function(
    specs: Vector[ComponentRepository.Specification],
    enabled: Boolean,
    workspace: Option[Path],
    factoryClasses: Vector[String]
  ): Subsystem => Seq[Component] = {
    (subsystem: Subsystem) => {
      val components = Vector.newBuilder[Component]
      val seen = mutable.LinkedHashMap.empty[String, Component]
      def addAll(xs: Seq[Component]): Unit =
        xs.foreach { component =>
          val name = component.core.name
          val key = NamingConventions.toComparisonKey(name)
          seen.get(key) match {
            case Some(existing) =>
              val selection = AssemblyReport.selectPreferred(existing, component)
              seen.update(key, selection.selected)
              GlobalRuntimeContext.current.foreach(
                _.assemblyReport.addWarning(
                  AssemblyReport.duplicateComponentWarning(
                    componentName = name,
                    selected = selection.selected,
                    dropped = selection.dropped,
                    reason = selection.reason
                  )
                )
              )
              observe_warn(
                s"duplicate component collapsed name=${name} kept=${selection.selected.origin} dropped=${selection.dropped.map(_.origin).mkString(",")} reason=${selection.reason}"
              )
            case None =>
              seen += key -> component
          }
        }
      if (enabled) {
        addAll(_discover_components(workspace)(subsystem))
      }
      if (specs.nonEmpty) {
        addAll(_discover_from_repositories(specs)(subsystem))
      }
      if (factoryClasses.nonEmpty) {
        addAll(_discover_from_component_factories(factoryClasses)(subsystem))
      }
      components ++= seen.values
      components.result()
    }
  }

  private def _discover_from_component_factories(
    classNames: Seq[String]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val params = ComponentCreate(subsystem, ComponentOrigin.Main)
      classNames.flatMap { name =>
        _load_component_factory(name) match {
          case Left(message) =>
            observe_warn(message)
            Nil
          case Right(factory) =>
            try {
              factory.create(params).participants
            } catch {
              case NonFatal(e) =>
                observe_warn(s"component factory failed class=${name} message=${e.getMessage}")
                Nil
            }
        }
      }
    }

  private def _load_component_factory(
    className: String
  ): Either[String, Component.BundleFactory] =
    try {
      val loader = Thread.currentThread.getContextClassLoader
      val clazz = Class.forName(className, true, loader)
      if (!classOf[Component.BundleFactory].isAssignableFrom(clazz)) {
        Left(s"component factory class is not a Component.BundleFactory: ${className}")
      } else {
        val ctor = clazz.getDeclaredConstructor()
        ctor.setAccessible(true)
        Right(ctor.newInstance().asInstanceOf[Component.BundleFactory])
      }
    } catch {
      case NonFatal(e) =>
        Left(s"failed to load component factory class=${className} message=${e.getMessage}")
    }

  private def _trace_component_dir_extras(
    extras: Subsystem => Seq[Component]
  ): Subsystem => Seq[Component] =
    (subsystem: Subsystem) => {
      val components = extras(subsystem)
      if (components.nonEmpty) {
        val modeLabel = GlobalRuntimeContext.current
          .flatMap(ctx => Option(ctx.runtimeMode))
          .map(_.name)
          .getOrElse("unknown")
        observe_trace(
          s"[component-dir] mode=${modeLabel} loaded components=${components.map(_.core.name).mkString(",")}"
        )
      }
      components
    }

  private def _class_dirs_(
    workspace: Option[Path]
  ): Vector[Path] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val scalaCliRoot = workspace.getOrElse(cwd)
    val scalaCli = _scala_cli_classes_dirs_(scalaCliRoot)
    val sbt = _sbt_classes_dirs_(cwd)
    (scalaCli ++ sbt).distinct
  }

  private def _package_prefixes_(): Vector[String] =
    sys.env.get("TEXTUS_DISCOVER_PREFIX").orElse(sys.env.get("CNCF_DISCOVER_PREFIX")) match {
      case Some(value) =>
        value.split(",").map(_.trim).filter(_.nonEmpty).toVector
      case None =>
        Vector.empty
    }

  private def _scala_cli_classes_dirs_(workspace: Path): Vector[Path] = {
    val root = workspace.resolve(".scala-build")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.filter(p => Files.isDirectory(p)).filter(p => p.getFileName.toString == "classes").toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _sbt_classes_dirs_(baseDir: Path): Vector[Path] = {
    val root = baseDir.resolve("target")
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala
          .filter(p => Files.isDirectory(p))
          .filter(p => p.getFileName.toString == "classes")
          .filter(p => _is_scala_target_(p))
          .toVector
      } finally {
        stream.close()
      }
    }
  }

  private def _is_scala_target_(classesDir: Path): Boolean = {
    val parent = classesDir.getParent
    if (parent == null) false
    else parent.getFileName.toString.startsWith("scala-")
  }

  private def _discover_from_class_dirs_(
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String]
  ): Seq[Component] =
    if (classDirs.isEmpty) {
      Nil
    } else {
      val loader = _class_loader_(classDirs)
      val service = _discover_service_loader_(loader, params, classDirs)
      if (service.nonEmpty) service
      else _discover_by_scan_(loader, params, classDirs, packagePrefixes)
    }

  private def _class_loader_(
    classDirs: Seq[Path]
  ): URLClassLoader = {
    val urls = classDirs.map(_.toUri.toURL).toArray
    new URLClassLoader(urls, getClass.getClassLoader)
  }

  private def _discover_service_loader_(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path]
  ): Vector[Component] = {
    def isFromClassDirs(x: AnyRef): Boolean =
      Option(x.getClass.getProtectionDomain)
        .flatMap(pd => Option(pd.getCodeSource))
        .flatMap(cs => Option(cs.getLocation))
        .flatMap(url => scala.util.Try(Paths.get(url.toURI)).toOption)
        .exists(path => classDirs.exists(dir => path.normalize.startsWith(dir.normalize)))

    val components =
      ServiceLoader.load(classOf[Component], loader).iterator.asScala.toVector
        .filter(x => isFromClassDirs(x))
    val factories =
      ServiceLoader
        .load(classOf[Component.BundleFactory], loader)
        .iterator
        .asScala
        .toVector
        .filter(x => isFromClassDirs(x))
    val fromFactories = factories.flatMap(_.create(params).participants)
    val direct = components.map(_initialize_component_(params))
    if (fromFactories.nonEmpty)
      fromFactories ++ direct.filterNot(d => fromFactories.exists(f => NamingConventions.equivalentByNormalized(f.name, d.name)))
    else
      direct
  }

  private def _discover_by_scan_(
    loader: URLClassLoader,
    params: ComponentCreate,
    classDirs: Seq[Path],
    packagePrefixes: Seq[String]
  ): Vector[Component] = {
    val seen = mutable.Set.empty[String]
    val results = Vector.newBuilder[Component]
    classDirs.foreach { root =>
      _class_files_(root).foreach { classFile =>
        val className = _class_name_(root, classFile)
        if (_accept_class_(className, packagePrefixes) && !seen.contains(className)) {
          seen += className
          observe_trace(s"[discover:classes] considering $className")
          _load_component_(loader, className, params).foreach { comp =>
            observe_trace(s"[discover:classes] loaded component ${comp.core.name} from $className")
            results += comp
          }
        }
      }
    }
    results.result()
  }

  private def _initialize_component_(
    params: ComponentCreate
  )(
    comp: Component
  ): Component = {
    val core = Component.createScriptCore()
    val init = params.toInit(core)
    comp.initialize(init)
    comp
  }

  private def _class_files_(root: Path): Vector[Path] =
    if (!Files.exists(root)) {
      Vector.empty
    } else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala
          .filter(p => Files.isRegularFile(p))
          .filter(p => p.toString.endsWith(".class"))
          .toVector
      } finally {
        stream.close()
      }
    }

  private def _class_name_(root: Path, classFile: Path): String = {
    val relative = root.relativize(classFile).toString
    val noExt =
      if (relative.endsWith(".class"))
        relative.substring(0, relative.length - ".class".length)
      else
        relative
    noExt.replace('/', '.').replace('\\', '.')
  }

  private def _accept_class_(
    name: String,
    packagePrefixes: Seq[String]
  ): Boolean =
    if (packagePrefixes.isEmpty) true
    else packagePrefixes.exists(prefix => name.startsWith(prefix))

  private def _load_component_(
    loader: URLClassLoader,
    className: String,
    params: ComponentCreate
  ): Option[Component] =
    try {
      val clazz = Class.forName(className, false, loader)
      if (classOf[Component.BundleFactory].isAssignableFrom(clazz)) {
        None
      } else if (classOf[Component].isAssignableFrom(clazz)) {
        observe_trace(s"[discover:classes] instantiating class component $className")
        org.goldenport.cncf.component.repository.ComponentProvider
          .provide(
            org.goldenport.cncf.component.repository.ComponentSource.ClassDef(
              clazz.asInstanceOf[Class[_ <: Component]],
              className
            ),
            params.subsystem,
            params.origin
          )
          .toOption
      } else {
        None
      }
    } catch {
      case _: Throwable => None
    }

  private def _take_no_exit(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val noexit =
      args.contains("--no-exit") ||
        _config_truthy(configuration, RuntimeConfig.NoExitKey)
    val rest = args.filterNot(_ == "--no-exit")
    (noexit, rest)
  }

  private def _take_force_exit(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val forceexit =
      args.contains("--force-exit") ||
        _config_truthy(configuration, RuntimeConfig.ForceExitKey)
    val rest = args.filterNot(_ == "--force-exit")
    (forceexit, rest)
  }

  private def _take_discover_classes(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Boolean, Array[String]) = {
    val enabled =
      args.contains("--discover=classes") ||
        _config_truthy(configuration, RuntimeConfig.DiscoverClassesKey) ||
        _discover_env_enabled()
    val rest = args.filterNot(_ == "--discover=classes")
    (enabled, rest)
  }

  private def _take_component_factory_classes(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Vector[String], Array[String]) = {
    val classes = Vector.newBuilder[String]
    val rest = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current == "--component-factory-class" && i + 1 < args.length) {
        classes += args(i + 1)
        i = i + 2
      } else if (current.startsWith("--component-factory-class=")) {
        classes += current.drop("--component-factory-class=".length)
        i = i + 1
      } else {
        rest += current
        i = i + 1
      }
    }
    val configClasses =
      _config_string(configuration, RuntimeConfig.ComponentFactoryClassKey)
        .toVector
        .flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))
    ((configClasses ++ classes.result()).distinct, rest.result().toArray)
  }

  private def _take_workspace(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): (Option[Path], Array[String]) = {
    val buffer = Vector.newBuilder[String]
    var workspace: Option[Path] = None
    var i = 0
    while (i < args.length) {
      if (args(i) == "--workspace" && i + 1 < args.length) {
        workspace = Some(Paths.get(args(i + 1)))
        i = i + 2
      } else {
        buffer += args(i)
        i = i + 1
      }
    }
    val resolved =
      workspace.orElse(_config_string(configuration, RuntimeConfig.WorkspaceKey).map(Paths.get(_)))
    (resolved, buffer.result().toArray)
  }

  private def _discover_env_enabled(): Boolean =
    sys.env
      .get("TEXTUS_DISCOVER_CLASSES")
      .orElse(sys.env.get("CNCF_DISCOVER_CLASSES"))
      .exists(v => _truthy_(v))

  private def _truthy_(p: String): Boolean =
    p.equalsIgnoreCase("true") || p.equalsIgnoreCase("on") || p == "1"

  private def _config_truthy(
    configuration: ResolvedConfiguration,
    key: String
  ): Boolean =
    _config_string(configuration, key).exists(_truthy_)

  private def _config_string(
    configuration: ResolvedConfiguration,
    key: String
  ): Option[String] =
    configuration.get[String](key).toOption.flatten.orElse {
      if (key.startsWith("cncf.")) configuration.get[String](key.replaceFirst("^cncf\\.", "textus.")).toOption.flatten
      else None
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
        |  cncf command admin.deployment.securityMermaid
        |  cncf command admin.deployment.securityMarkdown
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
      } else if (current.startsWith("--textus.logging.backend=")) {
        logBackendOption = Some(current.stripPrefix("--textus.logging.backend="))
      } else if (current == "--textus.logging.backend" && i + 1 < args.length) {
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.backend=")) { // legacy alias
        logBackendOption = Some(current.stripPrefix("--textus.runtime.logging.backend="))
      } else if (current == "--textus.runtime.logging.backend" && i + 1 < args.length) { // legacy alias
        logBackendOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.backend=")) { // legacy
        logBackendOption = Some(current.stripPrefix("--cncf.runtime.logging.backend="))
      } else if (current == "--cncf.runtime.logging.backend" && i + 1 < args.length) { // legacy
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
      } else if (current.startsWith("--textus.logging.level=")) {
        logLevelOption = Some(current.stripPrefix("--textus.logging.level="))
      } else if (current == "--textus.logging.level" && i + 1 < args.length) {
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--textus.runtime.logging.level=")) { // legacy alias
        logLevelOption = Some(current.stripPrefix("--textus.runtime.logging.level="))
      } else if (current == "--textus.runtime.logging.level" && i + 1 < args.length) { // legacy alias
        logLevelOption = Some(args(i + 1))
        i = i + 1
      } else if (current.startsWith("--cncf.runtime.logging.level=")) { // legacy
        logLevelOption = Some(current.stripPrefix("--cncf.runtime.logging.level="))
      } else if (current == "--cncf.runtime.logging.level" && i + 1 < args.length) { // legacy
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
    RuntimeConfig.getString(configuration, RuntimeConfig.LogBackendKey)
  }

  private def _log_level_from_configuration(
    configuration: ResolvedConfiguration
  ): Option[String] = {
    RuntimeConfig.getString(configuration, RuntimeConfig.LogLevelKey)
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
    ()
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
        Consequence.operationNotFound("client component")
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
        Consequence.argumentInvalid(s"path-resolution failed: $reason")
    }
  }

  private def _resolve_selector_with_operation_resolver(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] =
    subsystem.resolver.resolve(selector) match {
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
        Consequence.argumentMissing("command")
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
        case "/" => Consequence.argumentInvalid("command path must be /component/service/operation")
        case "." => Consequence.argumentInvalid("command must be component.service.operation")
        case _ => Consequence.argumentInvalid("command selector is invalid")
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
        Consequence.argumentInvalid("command must be component service operation or component.service.operation")
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
          Consequence.argumentInvalid("command path must be /component/service/operation")
      }
    } else {
      s.split("\\.") match {
        case Array(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.argumentInvalid("command must be component.service.operation")
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
      Consequence.argumentMissing("server-emulator path/url")
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
    val basesources = _runtime_standard_config_sources(
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
    val files =
      _split_config_paths(configargs.get("cncf.config.file")) ++
        _split_config_paths(configargs.get("cncf.config.files")) ++
        _split_config_paths(configargs.get("textus.config.file")) ++
        _split_config_paths(configargs.get("textus.config.files"))
    files.distinct.map { path =>
      val p = _normalize_config_path(cwd, path)
      ConfigurationSource.File(
        origin = ConfigurationOrigin.Arguments,
        path = p,
        rank = ConfigurationSource.Rank.Arguments,
        loader = new RuntimeFileConfigLoader
      )
    }.toVector
  }

  private def _runtime_standard_config_sources(
    cwd: Path,
    applicationname: String,
    args: Map[String, String]
  ): ConfigurationSources = {
    val loader = new RuntimeFileConfigLoader
    val names = _configuration_application_names(applicationname)
    val home = sys.props.get("user.home").toVector.flatMap { home =>
      names.flatMap { name =>
        _runtime_standard_file_sources(
          Paths.get(home).resolve(_configuration_dir_name(name)),
          ConfigurationOrigin.Home,
          ConfigurationSource.Rank.Home,
          loader
        )
      }
    }
    val project = names.flatMap { name =>
      ProjectRootFinder.find(cwd, name).toVector.flatMap { root =>
        _runtime_standard_file_sources(
          root.resolve(_configuration_dir_name(name)),
          ConfigurationOrigin.Project,
          ConfigurationSource.Rank.Project,
          loader
        )
      }
    }
    val current = names.flatMap { name =>
      _runtime_standard_file_sources(
        cwd.resolve(_configuration_dir_name(name)),
        ConfigurationOrigin.Cwd,
        ConfigurationSource.Rank.Cwd,
        loader
      )
    }
    val envsource = ConfigurationSource.env(sys.env, applicationname).toVector
    val argsource = ConfigurationSource.args(args).toVector
    ConfigurationSources(home ++ project ++ current ++ envsource ++ argsource)
  }

  private def _configuration_application_names(
    applicationname: String
  ): Vector[String] =
    Vector("cncf", applicationname).distinct

  private def _runtime_standard_file_sources(
    dir: Path,
    origin: ConfigurationOrigin,
    rank: Int,
    loader: RuntimeFileConfigLoader
  ): Vector[ConfigurationSource] =
    Vector("conf", "props", "properties", "json", "yaml", "xml").map { ext =>
      ConfigurationSource.File(
        origin = origin,
        path = dir.resolve(s"config.$ext"),
        rank = rank,
        loader = loader
      )
    }

  private def _configuration_dir_name(applicationname: String): String = {
    val name = applicationname.trim.stripPrefix(".")
    if (name.isEmpty) ".cncf" else s".$name"
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
      if (current.startsWith("--cncf.") || current.startsWith("--textus.")) {
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
      case None => org.goldenport.Consequence.argumentInvalid(s"invalid run mode: ${p}")
    }
}

private[cli] object RuntimeOptionsParser {
  final case class Options(
    json: Boolean = false,
    debug: Boolean = false,
    noExit: Boolean = false,
    format: Option[String] = None,
    pathResolutionCommand: Boolean = false,
    commandExecutionMode: Option[String] = None,
    debugCalltree: Boolean = false,
    debugTraceJob: Boolean = false,
    debugSaveCalltree: Boolean = false
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
      } else if (_is_property(current, "--debug.calltree")) {
        options = options.copy(debugCalltree = true)
      } else if (_is_property(current, "--debug.trace-job")) {
        options = options.copy(debugTraceJob = true)
      } else if (_is_property(current, "--debug.save-calltree")) {
        options = options.copy(debugSaveCalltree = true)
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
          case Some((key, value)) if _is_debug_calltree_key(key) =>
            options = options.copy(debugCalltree = _is_truthy(value))
          case Some((key, value)) if _is_debug_trace_job_key(key) =>
            options = options.copy(debugTraceJob = _is_truthy(value))
          case Some((key, value)) if _is_debug_save_calltree_key(key) =>
            options = options.copy(debugSaveCalltree = _is_truthy(value))
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
          } else if (_is_debug_calltree_key(current.drop(2))) {
            options = options.copy(debugCalltree = _is_truthy(args(i + 1)))
          } else if (_is_debug_trace_job_key(current.drop(2))) {
            options = options.copy(debugTraceJob = _is_truthy(args(i + 1)))
          } else if (_is_debug_save_calltree_key(current.drop(2))) {
            options = options.copy(debugSaveCalltree = _is_truthy(args(i + 1)))
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
    if (options.debugCalltree) b += Property(RuntimeConfig.DebugCallTreeKey, "true", None)
    if (options.debugTraceJob) b += Property(RuntimeConfig.DebugTraceJobKey, "true", None)
    if (options.debugSaveCalltree) b += Property(RuntimeConfig.DebugSaveCallTreeKey, "true", None)
    if (options.noExit) b += Property("textus.no-exit", "true", None)
    options.commandExecutionMode.foreach { value =>
      b += Property(RuntimeConfig.CommandExecutionModeKey, value, None)
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
      (current.startsWith("-") && !current.startsWith("--") && current.contains("=")) ||
      ((current.startsWith("textus.") || current.startsWith("cncf.")) && current.contains("="))

  private def _extract_key_value(
    current: String
  ): Option[(String, String)] = {
    val raw =
      if (current.startsWith("--")) current.drop(2)
      else if (current.startsWith("-")) current.drop(1)
      else current
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
    key == RuntimeConfig.CommandExecutionModeKey ||
      key == RuntimeConfig.RuntimeCommandExecutionModeKey ||
      key == "cncf.command.execution-mode" ||
      key == "cncf.runtime.command.execution-mode" ||
      key == "runtime.command.execution-mode" ||
      key == "command.execution-mode"

  private def _is_debug_calltree_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.DebugCallTreeKey ||
      key == RuntimeConfig.RuntimeDebugCallTreeKey ||
      key == "cncf.debug.calltree" ||
      key == "cncf.runtime.debug.calltree"

  private def _is_debug_trace_job_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.DebugTraceJobKey ||
      key == RuntimeConfig.RuntimeDebugTraceJobKey ||
      key == "cncf.debug.trace-job" ||
      key == "cncf.runtime.debug.trace-job"

  private def _is_debug_save_calltree_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.DebugSaveCallTreeKey ||
      key == RuntimeConfig.RuntimeDebugSaveCallTreeKey ||
      key == "cncf.debug.save-calltree" ||
      key == "cncf.runtime.debug.save-calltree"

  private def _is_truthy(value: String): Boolean = {
    val normalized = value.trim.toLowerCase
    normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
  }
}

class CncfRuntime() extends GlobalObservable {
  private val _configuration_application_name = "textus"
  private val _runtime_service_name = "runtime"
  private val _help_flags = Set("--help", "-h")
  private val _args_parser = new ArgsParser(ArgsParser.Config())

  private val _runtime_protocol: Protocol =
    Protocol.Builder()
      .addOperation(_runtime_service_name, RunMode.Server.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Client.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Command.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.ServerEmulator.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Script.name, RequestDefinition(), ResponseDefinition.void)
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

  def initializeHandle(
    config: BootstrapConfig
  ): Consequence[CncfHandle] =
    initializeForEmbedding(
      cwd = config.cwd,
      args = config.args,
      modeHint = config.modeHint,
      extraComponents = config.extraComponents
    ).map { initializedSubsystem =>
      new CncfHandle {
        @volatile private var _is_closed: Boolean = false

        def subsystem: Subsystem = initializedSubsystem

        def executeCommand(args: Array[String]): Consequence[Response] =
          if (_is_closed)
            Consequence.stateConflict("CncfHandle is already closed")
          else
            executeCommandResponse(initializedSubsystem, args)

        def executeAction(action: org.goldenport.cncf.action.Action): Consequence[OperationResponse] =
          if (_is_closed)
            Consequence.stateConflict("CncfHandle is already closed")
          else
            executeActionResponse(initializedSubsystem, action)

        def close(): Unit =
          if (!_is_closed) {
            _is_closed = true
            closeEmbedding()
          }
      }
    }

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
      _prepare_filebundle_parameters(subsystem, req).flatMap(subsystem.executeResponseWithMetadata)
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
    val bootstrap0 = CncfRuntime.bootstrap(cwd, args)
    val searchSpecs = bootstrap0.repositories.searchRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val activeSpecs = bootstrap0.repositories.activeRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val invocation = CncfRuntime.resolveSubsystemInvocation(bootstrap0.invocation, searchSpecs, activeSpecs)
    val bootstrap =
      if (invocation.actualArgs.sameElements(args)) bootstrap0
      else CncfRuntime.bootstrap(cwd, invocation.actualArgs)
    val configuration = bootstrap.configuration
    val resolvedSearchSpecs = bootstrap.repositories.searchRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val resolvedActiveSpecs = bootstrap.repositories.activeRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    CncfRuntime.configure_slf4j_simple(configuration)
    val modehint = modeHint.orElse(invocation.actualArgs.headOption.flatMap(RunMode.from))
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
    if (subsystem.descriptor.nonEmpty && subsystem.components.nonEmpty) {
      subsystem.components.foreach(compfactory.bootstrap)
    } else {
      subsystem.setup(compfactory)
    }
    val runtimeSpecs =
      if (subsystem.descriptor.nonEmpty)
        _merge_component_specs(resolvedActiveSpecs, resolvedSearchSpecs)
      else
        resolvedActiveSpecs
    val runtimeExtras = CncfRuntime.componentExtraFunction(runtimeSpecs, bootstrap.front)
    val extras = _collapse_component_duplicates(
      subsystem.components.toVector,
      (runtimeExtras(subsystem) ++ extraComponents(subsystem)).map(compfactory.bootstrap)
    )
    if (extras.nonEmpty) {
      subsystem.add(extras)
    }
    if (_apply_component_assembly_defaults(subsystem)) {
      val inheritedExtras = _collapse_component_duplicates(
        subsystem.components.toVector,
        runtimeExtras(subsystem).map(compfactory.bootstrap)
      )
      if (inheritedExtras.nonEmpty) {
        subsystem.add(inheritedExtras)
      }
    }
    StartupImport.run(cwd, configuration, runconfig, subsystem) match {
      case Consequence.Success(_) =>
        ()
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.show)
    }
    subsystem
  }

  private def _apply_component_assembly_defaults(
    subsystem: Subsystem
  ): Boolean = {
    var changed = false
    subsystem.descriptor.foreach { descriptor =>
      _primary_component_assembly_defaults(subsystem, descriptor).foreach { defaults =>
        val merged = GenericSubsystemDescriptor.mergeComponentDefaults(defaults, descriptor)
        val effective =
          descriptor.assemblyDescriptor
            .filterNot(src => _same_path(src.path, defaults.path))
            .map(GenericSubsystemDescriptor.applyAssemblyOverride(merged, _))
            .getOrElse(merged)
        if (effective != descriptor) {
          subsystem.withDescriptor(effective)
          changed = true
        }
      }
    }
    changed
  }

  private def _primary_component_assembly_defaults(
    subsystem: Subsystem,
    descriptor: GenericSubsystemDescriptor
  ): Option[GenericSubsystemDescriptor] = {
    val primaryName = descriptor.componentBindings.headOption.map(_.componentName)
    primaryName.flatMap { name =>
      subsystem.components.find { component =>
        val runtimeName =
          component.artifactMetadata.flatMap(_.component)
            .orElse(component.artifactMetadata.map(_.name))
            .getOrElse(component.name)
        _component_key(runtimeName) == _component_key(name)
      }.flatMap { component =>
        component.artifactMetadata.flatMap(_.archivePath).flatMap { path =>
          val p = java.nio.file.Paths.get(path)
          if (_same_path(Some(p), descriptor.path)) None
          else GenericSubsystemDescriptor.loadComponentArchive(p).toOption
        }
      }
    }
  }

  private def _same_path(
    lhs: Option[java.nio.file.Path],
    rhs: java.nio.file.Path
  ): Boolean =
    lhs.exists(p => p.toAbsolutePath.normalize == rhs.toAbsolutePath.normalize)

  private def _component_key(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase.replace("_", "").replace("-", "")

  private def _collapse_component_duplicates(
    existing: Vector[Component],
    candidates: Seq[Component]
  ): Vector[Component] = {
    val existingKeys = existing.map(x => NamingConventions.toComparisonKey(x.core.name)).toSet
    val seen = mutable.LinkedHashMap.empty[String, Component]
    candidates.foreach { component =>
      val key = NamingConventions.toComparisonKey(component.core.name)
      if (existingKeys.contains(key)) {
        existing.find(x => NamingConventions.toComparisonKey(x.core.name) == key).foreach { current =>
          val selection = AssemblyReport.selectPreferred(current, component)
          if (selection.selected ne current) {
            seen.update(key, selection.selected)
          }
          GlobalRuntimeContext.current.foreach(
            _.assemblyReport.addWarning(
              AssemblyReport.duplicateComponentWarning(
                componentName = component.core.name,
                selected = selection.selected,
                dropped = selection.dropped,
                reason = selection.reason
              )
            )
          )
        }
      } else {
        seen.get(key) match {
          case Some(current) =>
            val selection = AssemblyReport.selectPreferred(current, component)
            seen.update(key, selection.selected)
            GlobalRuntimeContext.current.foreach(
              _.assemblyReport.addWarning(
                AssemblyReport.duplicateComponentWarning(
                  componentName = component.core.name,
                  selected = selection.selected,
                  dropped = selection.dropped,
                  reason = selection.reason
                )
              )
            )
          case None =>
            seen += key -> component
        }
      }
    }
    seen.iterator.collect {
      case (key, component) if !existingKeys.contains(key) => component
    }.toVector
  }

  private def _merge_component_specs(
    activeSpecs: Vector[ComponentRepository.Specification],
    searchSpecs: Vector[ComponentRepository.Specification]
  ): Vector[ComponentRepository.Specification] =
    (activeSpecs ++ searchSpecs).foldLeft(Vector.empty[ComponentRepository.Specification]) { (z, x) =>
      if (z.contains(x)) z else z :+ x
    }

  private def _resolve_configuration(
    cwd: Path,
    args: Array[String] = Array.empty
  ): ResolvedConfiguration = {
    CncfRuntime._resolve_configuration(cwd, args)
  }

  private def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ): Vector[ConfigurationSource] = {
    CncfRuntime._explicit_config_sources(cwd, configargs)
  }

  private def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    CncfRuntime._split_config_paths(value)

  private def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    CncfRuntime._normalize_config_path(cwd, path)
  }

  private def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    CncfRuntime._config_args(args)
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
    val (runtimeOptions, cleanArgs) = RuntimeOptionsParser.extract(args.toIndexedSeq)
    val runtimeProperties = RuntimeOptionsParser.properties(runtimeOptions)
      .filter(p => _is_client_debug_passthrough_key(p.name))
    observe_trace(
      s"executeClient start args=${args.mkString(" ")} componentCount=${subsystem.components.size} operations=${_operation_sample(operations)}"
    )
    val result = _client_component(subsystem).flatMap { component =>
        parseClientArgs(subsystem, cleanArgs.toArray).flatMap { req0 =>
        val req = req0.copy(properties = req0.properties ++ runtimeProperties)
        _client_action_from_request(req).flatMap { action =>
          component.execute(action).map(_ -> _request_debug_trace_job(req))
        }
      }
    }
    result match {
      case Consequence.Success((res, debugTraceJob)) =>
        _print_operation_response(res)
        if (debugTraceJob)
          _print_debug_job_reference(res)
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
        Consequence.operationNotFound("client component")
    }

  def parseClientArgs(
    subsystem: Subsystem,
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.argumentMissing("client command")
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
            Consequence.argumentMissing("client http path")
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
        Consequence.argumentMissing("client http operation/path")
      case _ =>
        _to_request(subsystem, args.toArray, RunMode.Command).flatMap(_command_request_to_client_request(subsystem, _))
    }
  }

  private[cli] def _command_request_to_client_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    for {
      operation <- _operation_request_definition(subsystem, req)
      normalized <- _prepare_filebundle_parameters(operation, req)
      transport <- _prepare_filebundle_transport_parameters(operation, normalized)
      action <- operation.createOperationRequest(normalized)
    } yield {
      val method = action match {
        case _: org.goldenport.cncf.action.QueryAction => "get"
        case _ => "post"
      }
      Request.of(
        component = "client",
        service = "http",
        operation = method,
        arguments = Argument("path", _request_path(req), None) :: transport.arguments,
        switches = transport.switches,
        properties = transport.properties
      )
    }

  private[cli] def _prepare_filebundle_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] = {
    val names = operation.specification.request.parameters
      .filter(_is_filebundle_parameter)
      .flatMap(_.names)
      .toSet
    if (names.isEmpty) {
      Consequence.success(req)
    } else {
      for {
        arguments <- Consequence.zipN(req.arguments.map { argument =>
          if (names.contains(argument.name))
            _filebundle_value(argument.name, argument.value).map(v => argument.copy(value = v))
          else
            Consequence.success(argument)
        })
        properties <- Consequence.zipN(req.properties.map { property =>
          if (names.contains(property.name))
            _filebundle_value(property.name, property.value).map(v => property.copy(value = v))
          else
            Consequence.success(property)
        })
      } yield req.copy(arguments = arguments.toList, properties = properties.toList)
    }
  }

  private[cli] def _prepare_filebundle_parameters(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    _operation_request_definition(subsystem, req).flatMap(_prepare_filebundle_parameters(_, req))

  private[cli] def _prepare_filebundle_transport_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] = {
    val names = operation.specification.request.parameters
      .filter(_is_filebundle_parameter)
      .flatMap(_.names)
      .toSet
    if (names.isEmpty) {
      Consequence.success(req)
    } else {
      for {
        arguments <- Consequence.zipN(req.arguments.map { argument =>
          if (names.contains(argument.name))
            _filebundle_transport_value(argument.name, argument.value).map(v => argument.copy(value = v))
          else
            Consequence.success(argument)
        })
        properties <- Consequence.zipN(req.properties.map { property =>
          if (names.contains(property.name))
            _filebundle_transport_value(property.name, property.value).map(v => property.copy(value = v))
          else
            Consequence.success(property)
        })
      } yield req.copy(arguments = arguments.toList, properties = properties.toList)
    }
  }

  private def _is_filebundle_parameter(
    parameter: ParameterDefinition
  ): Boolean =
    parameter.datatype == XFileBundle ||
      Option(parameter.datatype).map(_.name).exists { name =>
        _normalize_datatype_name(name) == "filebundle"
      }

  private def _normalize_datatype_name(
    name: String
  ): String =
    name.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)

  private def _filebundle_value(
    name: String,
    value: Any
  ): Consequence[FileBundle] =
    FileBundle.create(name, value)

  private def _filebundle_transport_value(
    name: String,
    value: Any
  ): Consequence[MimeBody] =
    value match {
      case bundle: FileBundle => bundle.toMimeBody
      case other => FileBundle.mimeBody(name, other)
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
      case None => Consequence.operationNotFound(s"client target operation:${req.name}")
    }

  private[cli] def _framework_option_passthrough(
    args: Seq[String]
  ): List[Property] = {
    CncfRuntime._framework_option_passthrough(args)
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
      case _ => Consequence.argumentInvalid("client http operation must be get or post")
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
            _canonical_http_properties(params, parsed.properties) ++ _http_tail_properties(parsed.arguments.drop(1))
          ))
        case None =>
          Consequence.argumentMissing("client http path")
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

  private def _canonical_http_properties(
    params: Seq[String],
    properties: List[Property]
  ): List[Property] =
    if (_has_short_http_body_option(params))
      properties.map {
        case Property("data", value, origin) => Property("http.body", value, origin)
        case other => other
      }
    else
      properties

  private def _has_short_http_body_option(
    params: Seq[String]
  ): Boolean =
    params.exists(p => p == "-d" || p.startsWith("-d="))

  private[cli] def _normalize_path(path: String): String = {
    val normalized = if (path.contains(".")) path.replace(".", "/") else path
    if (normalized.startsWith("/")) normalized else s"/${normalized}"
  }

  private[cli] def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    if (args.isEmpty) {
      Consequence.argumentMissing("client path")
    } else {
      val xs = args.toVector
      if (xs.length >= 3) {
        val component = xs(0)
        val service = xs(1)
        val operation = xs(2)
        val rest = xs.drop(3)
        Consequence.success((_normalize_path(s"/${component}/${service}/${operation}"), rest))
      } else {
        val single = xs.head
        val rest = xs.drop(1)
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
          _client_http_body_and_header(req).map { case (body, header) =>
            new PostCommand(
              req,
              // "system.ping", // TODO generic
              HttpRequest.fromUrl(
                method = HttpRequest.POST,
                url = URI.create(url).toURL,
                header = header,
                body = body.map(_.value)
              )
            )
          }
        case "get" =>
          _client_explicit_mime_body_from_request(req).flatMap {
            case Some(_) =>
              Consequence.argumentInvalid("client http get does not accept a body")
            case None =>
              Consequence.success(
                new GetQuery(
                  req,
                  // "system.ping",
                  HttpRequest.fromUrl(
                    method = HttpRequest.GET,
                    url = URI.create(url).toURL
                  )
                )
              )
          }
        case other =>
          Consequence.argumentInvalid(s"client http operation not supported: ${other}")
      }
      }
    } else {
      Consequence.argumentMissing("client http request")
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
      case Argument(name, value, _) if name.startsWith("arg") && !_is_multipart_value(value) =>
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedValue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedName}=${encodedValue}"
    }
    val propertyParams = req.properties.collect {
      case Property(name, value, _) if _is_http_parameter_property(name) && !_is_multipart_value(value) =>
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
      name != "http.body" &&
      name != "http.data" &&
      name != "-d"

  private[cli] def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.argumentMissing("client http path")
    }

  private[cli] def _client_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("http.body", "http.data", "-d")).flatMap {
      case Some(body) => Consequence.success(Some(body))
      case None =>
        _client_multipart_mime_body(req) match {
          case Some(body) => Consequence.success(Some(body))
          case None =>
            _client_form_mime_body(req) match {
              case Some(body) => Consequence.success(Some(body))
              case None => Consequence.success(_mime_body_from_arguments(req.arguments))
            }
        }
    }

  private[cli] def _client_explicit_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("http.body", "http.data", "-d")).flatMap {
      case some @ Some(_) => Consequence.success(some)
      case None => Consequence.success(_mime_body_from_arguments(req.arguments))
    }

  private[cli] def _client_http_body_and_header(
    req: Request
  ): Consequence[(Option[MimeBody], Record)] =
    _client_mime_body_from_request(req).map {
      case Some(body) =>
        (Some(body), Record.create(Vector("Content-Type" -> body.contentType.header)))
      case None =>
        (None, Record.empty)
    }

  private[cli] def _client_multipart_mime_body(
    req: Request
  ): Option[MimeBody] = {
    val fields = _client_multipart_fields(req)
    if (!fields.exists(_.isBinary)) {
      None
    } else {
      val boundary = s"cncf-${java.util.UUID.randomUUID().toString}"
      val bytes = _render_multipart(fields, boundary)
      Some(
        MimeBody(
          ContentType.MULTIPART_FORM_DATA.copy(parameters = Map("boundary" -> boundary)),
          Bag.binary(bytes)
        )
      )
    }
  }

  private final case class ClientMultipartField(
    name: String,
    value: Any
  ) {
    def isBinary: Boolean = _is_multipart_value(value)
  }

  private def _client_multipart_fields(
    req: Request
  ): Vector[ClientMultipartField] = {
    val argumentParams = req.arguments.collect {
      case Argument(name, value, _) if name != "path" =>
        ClientMultipartField(name, value)
    }
    val switchParams = req.switches.collect {
      case Switch(name, value, _) if value =>
        ClientMultipartField(name, "true")
    }
    val propertyParams = _client_http_parameter_properties(req).map { p =>
      ClientMultipartField(p.name, p.value)
    }
    (argumentParams ++ switchParams ++ propertyParams).toVector
  }

  private def _render_multipart(
    fields: Vector[ClientMultipartField],
    boundary: String
  ): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    fields.foreach { field =>
      _write_ascii(out, s"--${boundary}\r\n")
      field.value match {
        case body: MimeBody =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"; filename="${_quote_http_header(field.name)}.zip"\r\n""")
          _write_ascii(out, s"Content-Type: ${body.contentType.header}\r\n\r\n")
          _write_bag(out, body.value)
          _write_ascii(out, "\r\n")
        case bag: Bag =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"; filename="${_quote_http_header(field.name)}"\r\n""")
          _write_ascii(out, s"Content-Type: ${ContentType.APPLICATION_OCTET_STREAM.header}\r\n\r\n")
          _write_bag(out, bag)
          _write_ascii(out, "\r\n")
        case other =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"\r\n\r\n""")
          out.write(other.toString.getBytes(StandardCharsets.UTF_8))
          _write_ascii(out, "\r\n")
      }
    }
    _write_ascii(out, s"--${boundary}--\r\n")
    out.toByteArray
  }

  private def _write_ascii(
    out: ByteArrayOutputStream,
    text: String
  ): Unit =
    out.write(text.getBytes(StandardCharsets.US_ASCII))

  private def _write_bag(
    out: ByteArrayOutputStream,
    bag: Bag
  ): Unit = {
    val in = bag.openInputStream()
    try {
      val buffer = new Array[Byte](8192)
      var read = in.read(buffer)
      while (read != -1) {
        out.write(buffer, 0, read)
        read = in.read(buffer)
      }
    } finally {
      in.close()
    }
  }

  private def _quote_http_header(
    value: String
  ): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

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
    val propertyParams = _client_http_parameter_properties(req).filterNot(p => _is_multipart_value(p.value)).map { p =>
      s"${URLEncoder.encode(p.name, StandardCharsets.UTF_8)}=${URLEncoder.encode(p.value.toString, StandardCharsets.UTF_8)}"
    }
    val params = argumentParams ++ switchParams ++ propertyParams
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private[cli] def _client_http_parameter_properties(
    req: Request
  ): List[Property] =
    req.properties.filter(p => _is_http_parameter_property(p.name))

  private def _is_multipart_value(
    value: Any
  ): Boolean =
    value.isInstanceOf[MimeBody] || value.isInstanceOf[Bag]

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
        Consequence.argumentInvalid("client request body must be a MimeBody, Bag, or String")
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
      _prepare_filebundle_parameters(subsystem, req).flatMap(subsystem.execute)
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
        Consequence.argumentInvalid(s"path-resolution failed: $reason")
    }
  }

  private def _resolve_selector_with_operation_resolver(
    subsystem: Subsystem,
    selector: String
  ): Consequence[(String, String, String)] =
    subsystem.resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Consequence.success((component, service, operation))
      case ResolutionResult.NotFound(stage, input) =>
        Consequence.operationNotFound(s"${stage.toString.toLowerCase}:$input")
      case ResolutionResult.Ambiguous(input, candidates) =>
        Consequence.argumentInvalid(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
      case ResolutionResult.Invalid(reason) =>
        Consequence.argumentInvalid(s"invalid selector: $reason")
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
        Consequence.argumentMissing("command")
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
        case "/" => Consequence.argumentInvalid("command path must be /component/service/operation")
        case "." => Consequence.argumentInvalid("command must be component.service.operation")
        case _ => Consequence.argumentInvalid("command selector is invalid")
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
      Consequence.argumentMissing("server-emulator path/url")
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
        Consequence.argumentInvalid("command must be component service operation or component.service.operation")
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
          Consequence.argumentInvalid("command path must be /component/service/operation")
      }
    } else {
      s.split("\\.") match {
        case Array(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.argumentInvalid("command must be component.service.operation")
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
        |  cncf command admin.deployment.securityMermaid
        |  cncf command admin.deployment.securityMarkdown
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

  private def _print_debug_job_reference(
    metadata: RuntimeContext.ExecutionMetadata
  ): Unit =
    metadata.debugJobId
      .filter(_.nonEmpty)
      .foreach(_print_debug_job_reference)

  private def _print_debug_job_reference(
    response: OperationResponse
  ): Unit =
    response match {
      case OperationResponse.Http(http) =>
        http.headerValue("X-Textus-Job-Id").foreach(_print_debug_job_reference)
      case _ =>
        ()
    }

  private def _print_debug_job_reference(
    jobid: String
  ): Unit =
    Console.err.println(s"Debug job: ${jobid} (/web/system/admin/jobs/${jobid})")

  private def _request_debug_trace_job(
    req: Request
  ): Boolean =
    req.properties.exists { p =>
      _is_debug_trace_job_key(p.name) && _is_truthy(p.value.toString)
    }

  private def _is_debug_trace_job_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.DebugTraceJobKey ||
      key == RuntimeConfig.RuntimeDebugTraceJobKey ||
      key == "cncf.debug.trace-job" ||
      key == "cncf.runtime.debug.trace-job"

  private def _is_client_debug_passthrough_key(
    key: String
  ): Boolean =
    key == RuntimeConfig.DebugCallTreeKey ||
      key == RuntimeConfig.RuntimeDebugCallTreeKey ||
      key == RuntimeConfig.DebugSaveCallTreeKey ||
      key == RuntimeConfig.RuntimeDebugSaveCallTreeKey ||
      key == "cncf.debug.calltree" ||
      key == "cncf.runtime.debug.calltree" ||
      key == "cncf.debug.save-calltree" ||
      key == "cncf.runtime.debug.save-calltree" ||
      _is_debug_trace_job_key(key)

  private def _is_truthy(
    value: String
  ): Boolean = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
  }

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
    ()
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
