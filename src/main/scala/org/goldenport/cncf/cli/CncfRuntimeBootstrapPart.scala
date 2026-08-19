package org.goldenport.cncf.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.ConsequenceException
import org.goldenport.Conclusion
import org.goldenport.cli.parser.ArgsParser
import org.goldenport.configuration.Configuration
import org.goldenport.configuration.ConfigurationBindingCandidates
import org.goldenport.configuration.ConfigurationBindingResolver
import org.goldenport.configuration.ConfigurationOrigin
import org.goldenport.configuration.ConfigurationResolution
import org.goldenport.configuration.ConfigurationResolutionSnapshot
import org.goldenport.configuration.ConfigurationTrace
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentDescriptorLoader
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.config.CncfAssemblyConfigurationProjection
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingAdmission
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingAssignment
import org.goldenport.cncf.config.CncfConfigurationEnvironmentBindingAdmission
import org.goldenport.cncf.config.CncfConfigurationEnvironmentBindingAssignment
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.config.CncfConfigurationResolutionContext
import org.goldenport.cncf.config.CncfRuntimeConfigurationProjection
import org.goldenport.cncf.config.RepositoryBootstrapPolicy
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeProcessExitPolicy
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.context.ScopeKind
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.ProtocolEngine
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.protocol.spec.ResponseDefinition
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.http.HttpExecutionEngine
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.backend.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.path.AliasLoader
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.cli.RuntimeParameterParser
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.observability.global.GlobalObservability
import org.goldenport.cncf.observability.global.GlobalObservabilityGate
import org.goldenport.cncf.observability.global.ObservabilityRoot
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
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
private[cli] trait CncfRuntimeBootstrapPart {
  this: GlobalObservable & CncfRuntimeDiscoveryPart & CncfRuntimeInteractionPart & CncfRuntimeConfigurationPart =>
  private[cncf] final case class RuntimeFrontParameters(
    factoryClasses: Vector[String],
    discoverClasses: Boolean,
    workspace: Option[Path],
    processExitPolicy: RuntimeProcessExitPolicy,
    residualArgs: Array[String]
  )

  private[cncf] final case class RuntimeBootstrap(
    configuration: ResolvedConfiguration,
    configurationsnapshot: Option[ConfigurationResolutionSnapshot],
    assemblyconfiguration: AssemblyConfigurationContribution,
    configurationargumentbindings: Vector[CncfConfigurationArgumentBindingAssignment],
    configurationenvironmentbindings: Vector[CncfConfigurationEnvironmentBindingAssignment],
    repositorybootstrappolicy: RepositoryBootstrapPolicy,
    front: RuntimeFrontParameters,
    invocation: RuntimeInvocationParameters,
    repositories: RuntimeRepositoryParameters
  ) {
    def configurationSnapshot: Option[ConfigurationResolutionSnapshot] = configurationsnapshot
    def assemblyConfiguration: AssemblyConfigurationContribution = assemblyconfiguration
    def configurationArgumentBindings: Vector[CncfConfigurationArgumentBindingAssignment] = configurationargumentbindings
    def configurationEnvironmentBindings: Vector[CncfConfigurationEnvironmentBindingAssignment] = configurationenvironmentbindings
    def repositoryBootstrapPolicy: RepositoryBootstrapPolicy = repositorybootstrappolicy
  }

  /** Runtime collaborator construction accepts only the typed bootstrap path projection. */
  private[cncf] def collaboratorFactory(
    bootstrap: RuntimeBootstrap
  ): CollaboratorFactory =
    CollaboratorFactory.create(bootstrap.repositorybootstrappolicy.collaboratorRepositoryPaths)

  private[cncf] final case class AssemblyConfigurationContribution(
    configuration: ResolvedConfiguration,
    values: Map[String, String],
    sourceidentity: Option[String]
  ) {
    def sourceIdentity: Option[String] = sourceidentity
  }

  private[cncf] final case class RuntimeRepositoryParameters(
    activeRepositories: Either[String, Vector[ComponentRepository.Specification]],
    searchRepositories: Either[String, Vector[ComponentRepository.Specification]]
  )

  private[cncf] final case class RuntimeInvocationParameters(
    actualArgs: Array[String],
    subsystemName: Option[String],
    componentName: Option[String],
    componentVersion: Option[String] = None
  )

  private[cli] final case class RuntimeLaunch(
    cwd: Path,
    configuration: ResolvedConfiguration,
    activespecifications: Vector[org.goldenport.cncf.component.repository.ComponentRepository.Specification],
    logbackendoption: Option[String],
    logleveloption: Option[String],
    actualargs: Array[String],
    runtimeparse: RuntimeParameterParseResult,
    domainargs: Array[String],
    mode: RunMode,
    runtimeconfig: RuntimeConfig,
    aliasresolver: AliasResolver
  )

  private[cli] final case class RuntimeGlobalConfigurationPolicies(
    repositorybootstrappolicy: RepositoryBootstrapPolicy,
    processexitpolicy: RuntimeProcessExitPolicy
  ) {
    def repositoryBootstrapPolicy: RepositoryBootstrapPolicy = repositorybootstrappolicy
    def processExitPolicy: RuntimeProcessExitPolicy = processexitpolicy
  }

  /** Package-visible observation seam for bootstrap integration specifications. */
  private[cncf] trait RuntimeBootstrapObservation {
    def onGlobalPolicyResolution(): Unit
  }

  private[cncf] object RuntimeBootstrapObservation {
    private[cli] val _noop = new RuntimeBootstrapObservation {
      override def onGlobalPolicyResolution(): Unit = ()
    }

    def noop: RuntimeBootstrapObservation = _noop
  }

  private[cli] val _configuration_application_name = "textus"
  private[cli] val _help_flags = Set("--help", "-h")
  private[cli] val _runtime_service_name = "runtime"
  private[cli] lazy val _runtime_parameter_parser = new RuntimeParameterParser()
  private[cli] val _args_parser = new ArgsParser(ArgsParser.Config())

  private[cli] val _runtime_protocol: Protocol =
    Protocol.Builder()
      .addOperation(_runtime_service_name, RunMode.Server.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Client.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Command.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.ServerEmulator.name, RequestDefinition(), ResponseDefinition.void)
      .addOperation(_runtime_service_name, RunMode.Script.name, RequestDefinition(), ResponseDefinition.void)
      .build()

  private[cli] val _runtime_protocol_engine = ProtocolEngine.create(_runtime_protocol)

  private[cli] var _global_runtime_context: Option[GlobalRuntimeContext] = None

  private[cli] def _reset_global_runtime_context(): Unit =
    _global_runtime_context = None

  private[cli] def _create_global_runtime_context(
    runconfig: RuntimeConfig,
    configuration: ResolvedConfiguration,
    aliasresolver: AliasResolver
  ): GlobalRuntimeContext = {
    val execution = ExecutionContext.create(
      runconfig.idNamespace,
      runconfig.executionClock.clock
    )
    val context = GlobalRuntimeContext.create(
      name = "runtime",
      runconfig,
      configuration,
      observabilityContext = execution.observability,
      aliasresolver
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
    observe_info(
      s"runtime clock mode=${if (runconfig.executionClock.isVirtual) "offset" else "system"}" +
        s" current=${execution.clock.instant()}" +
        s" virtual-start=${runconfig.executionClock.virtualStartAt.map(_.toString).getOrElse("none")}"
    )
    context
  }

  private[cli] def _runtime_scope_context(): ScopeContext =
    _global_runtime_context.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = DefaultSubsystemFactory.subsystemName,
        parent = None,
        observabilitycontext = ExecutionContext.create().observability
      )
    }

  private[cli] def _mark_used_parameters(
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

  private[cli] def _initialize_global_observability(): Unit = {
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

  private[cli] def _alias_resolver(
    configuration: ResolvedConfiguration
  ): AliasResolver =
    AliasLoader.load(configuration.configuration)

  private[cli] def configure_slf4j_simple(
    configuration: ResolvedConfiguration
  ): Unit = {
    def _get_(key: String): Option[String] =
      ConfigurationAccess.getString(configuration, key)
        .orElse(_legacy_framework_key(key).flatMap(ConfigurationAccess.getString(configuration, _)))

    val logfile = _get_("textus.logging.slf4j.file.path")
      .orElse(_get_("textus.runtime.logging.slf4j.file.path"))
      .orElse(_get_("textus.logging.external.file.path"))
      .getOrElse("target/cncf.d/external.log")
    val level = _get_("textus.logging.slf4j.level")
      .orElse(_get_("textus.runtime.logging.slf4j.level"))
      .orElse(_get_("textus.logging.level"))
      .getOrElse("warn")
    val hikarilevel = _get_("textus.logging.slf4j.hikari.level").getOrElse("warn")
    val sqlitelevel = _get_("textus.logging.slf4j.sqlite.level").getOrElse("warn")

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

    private[cli] def _config_string(
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

  private[cli] def _legacy_framework_key(
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

  def startServer(args: Array[String]): Unit = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Server), args = args)
    try
      new CncfRuntime().startServer(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def startServer(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Unit = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Server), args)
    try
      new CncfRuntime().startServer(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def executeClient(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Client), args = args)
    try
      new CncfRuntime().executeClient(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def executeClient(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client), args)
    try
      new CncfRuntime().executeClient(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def executeCommand(args: Array[String]): Int = {
    val subsystem = buildSubsystem(mode = Some(RunMode.Command), args = args)
    try
      new CncfRuntime().executeCommand(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def executeCommand(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command), args)
    try
      new CncfRuntime().executeCommand(subsystem, args)
    finally
      Subsystem.shutdownOwned(subsystem)
  }

  def executeServerEmulator(args: Array[String]): Int =
    _execute_server_emulator(args, args, _ => Nil)

  def executeServerEmulator(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int =
    _execute_server_emulator(args, args, extraComponents)

  private[cli] def _execute_server_emulator(
    requestargs: Array[String],
    subsystemargs: Array[String],
    extras: Subsystem => Seq[Component]
  ): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val runtimebootstrap = CncfRuntime.bootstrap(cwd, subsystemargs)
    val configuration = runtimebootstrap.configuration
    val runtimeconfig = _runtime_config(configuration)
    val residualargs = _admit_configuration_binding_arguments(requestargs).residualArguments.toArray
    val (includeheader, rest) = _include_header(residualargs)
    val result = normalizeServerEmulatorArgs(rest, runtimeconfig.serverEmulatorBaseUrl) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val subsystem = buildSubsystem(extras, Some(RunMode.ServerEmulator), subsystemargs)
            try {
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
            } finally {
              Subsystem.shutdownOwned(subsystem)
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

  def executeScript(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Consequence[Response] = {
    val requestargs = _admit_configuration_binding_arguments(args).residualArguments.toArray
    _execute_script(requestargs, args, extraComponents)
  }

  private[cli] def _execute_script(
    requestargs: Array[String],
    subsystemargs: Array[String],
    extras: Subsystem => Seq[Component]
  ): Consequence[Response] = {
    val subsystem = buildSubsystem(extras, Some(RunMode.Script), subsystemargs)
    try {
      _to_request_script(subsystem, requestargs).flatMap { req =>
        subsystem.execute(req)
      }
    } finally {
      Subsystem.shutdownOwned(subsystem)
    }
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

  def runExitCode(args: Array[String]): Int =
    run(args)

  private[cncf] def frontParameters(
    configuration: ResolvedConfiguration,
    processExitPolicy: RuntimeProcessExitPolicy,
    args: Array[String]
  ): RuntimeFrontParameters = {
    val (factoryclasses, args2) = _take_component_factory_classes(configuration, args)
    val (discover, args3) = _take_discover_classes(configuration, args2)
    val (workspace, args4) = _take_workspace(configuration, args3)
    val (exitpolicy, rest) = _take_process_exit_policy(processExitPolicy, args4)
    RuntimeFrontParameters(factoryclasses, discover, workspace, exitpolicy, rest)
  }

  private[cncf] def bootstrap(
    cwd: Path,
    args: Array[String]
  ): RuntimeBootstrap =
    bootstrapC(cwd, args, sys.env).TAKE

  private[cncf] def bootstrap(
    cwd: Path,
    args: Array[String],
    environment: Map[String, String]
  ): RuntimeBootstrap =
    bootstrapC(cwd, args, environment).TAKE

  private[cncf] def bootstrapC(
    cwd: Path,
    args: Array[String],
    environment: Map[String, String] = sys.env
  ): Consequence[RuntimeBootstrap] =
    bootstrapC(cwd, args, environment, RuntimeBootstrapObservation.noop)

  private[cncf] def bootstrapC(
    cwd: Path,
    args: Array[String],
    environment: Map[String, String],
    observation: RuntimeBootstrapObservation
  ): Consequence[RuntimeBootstrap] =
    CncfConfigurationEnvironmentBindingAdmission
      .admit(environment, CncfConfigurationParameterCatalog.closed)
      .flatMap(_bootstrap(cwd, args, environment, observation, _))

  private[cli] def _bootstrap(
    cwd: Path,
    args: Array[String],
    environment: Map[String, String],
    observation: RuntimeBootstrapObservation,
    environmentadmission: CncfConfigurationEnvironmentBindingAdmission
  ): Consequence[RuntimeBootstrap] = {
    val testargs = _normalize_test_mode_args(cwd, args)
    if (!testargs.sameElements(args))
      return _bootstrap(cwd, testargs, environment, observation, environmentadmission)
    val normalizedargs = _normalize_source_args(args)
    if (!normalizedargs.sameElements(args))
      return _bootstrap(cwd, normalizedargs, environment, observation, environmentadmission)
    for {
      argumentadmission <- _admit_configuration_binding_arguments_c(args)
      bootstrap <- {
        val residualargs = argumentadmission.residualArguments.toArray
        val configurationsnapshot = _resolve_configuration_snapshot(cwd, residualargs, environmentadmission.residualEnvironment)
        val assemblyconfiguration = _assembly_configuration_contribution(
          configurationsnapshot.map(_.resolved).getOrElse(ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
        )
        observation.onGlobalPolicyResolution()
        _global_configuration_policies(
          cwd,
          configurationsnapshot,
          assemblyconfiguration,
          argumentadmission.assignments,
          environmentadmission.assignments
        ).flatMap { globalpolicies =>
          _bootstrap_with_global_policies(
            cwd,
            args,
            configuration = assemblyconfiguration.configuration,
            configurationsnapshot,
            assemblyconfiguration,
            argumentadmission,
            environmentadmission,
            globalpolicies
          )
        }
      }
    } yield bootstrap
  }

  /**
   * Auto archive discovery may add only a repository CLI argument. Reapply
   * that adapter against the already resolved Global policies rather than
   * rereading sources or resolving Global candidates a second time.
   */
  private[cli] def _bootstrap_with_global_policies(
    cwd: Path,
    args: Array[String],
    configuration: ResolvedConfiguration,
    configurationsnapshot: Option[ConfigurationResolutionSnapshot],
    assemblyconfiguration: AssemblyConfigurationContribution,
    argumentadmission: CncfConfigurationArgumentBindingAdmission,
    environmentadmission: CncfConfigurationEnvironmentBindingAdmission,
    globalpolicies: RuntimeGlobalConfigurationPolicies
  ): Consequence[RuntimeBootstrap] =
    // The normalized auto-archive continuation changes only repository
    // arguments. Re-admit its envelope so the repository policy sees it,
    // while retaining the already resolved Global binding collection.
    _admit_configuration_binding_arguments_c(args).flatMap { effectiveargumentadmission =>
    val residualargs = effectiveargumentadmission.residualArguments.toArray
    val (frameworkargs, commandtail) = _split_command_args(residualargs)
    val (repositorypolicy, repositoryargs) =
      RepositoryBootstrapPolicy.admitArguments(globalpolicies.repositoryBootstrapPolicy, frameworkargs)
    val front0 = frontParameters(configuration, globalpolicies.processExitPolicy, repositoryargs)
    val front = front0.copy(residualArgs = front0.residualArgs ++ commandtail)
    val invocation0 = canonicalInvocationParameters(configuration, front0.residualArgs)
      .copy(actualArgs = front0.residualArgs ++ _repository_policy_invocation_arguments(repositorypolicy))
    val invocation = invocation0.copy(actualArgs = invocation0.actualArgs ++ commandtail)
    val (rawframeworkargs, rawcommandtail) = _split_command_args(args)
    val withcomponentfile = _with_auto_component_file(cwd, rawframeworkargs, configuration, repositorypolicy, invocation) ++ rawcommandtail
    if (!withcomponentfile.sameElements(args))
      _bootstrap_with_global_policies(
        cwd,
        withcomponentfile,
        configuration,
        configurationsnapshot,
        assemblyconfiguration,
        effectiveargumentadmission,
        environmentadmission,
        globalpolicies
      )
    else {
      val repositories = repositoryParameters(repositorypolicy, configuration, repositoryargs, cwd)
      Consequence.success(RuntimeBootstrap(configuration, configurationsnapshot, assemblyconfiguration, effectiveargumentadmission.assignments, environmentadmission.assignments, repositorypolicy, front, invocation, repositories))
    }
    }

  private[cli] def _with_configured_assembly_descriptor_configuration(
    configuration: ResolvedConfiguration
  ): ResolvedConfiguration =
    _assembly_configuration_contribution(configuration).configuration

  private[cli] def _assembly_configuration_contribution(
    configuration: ResolvedConfiguration
  ): AssemblyConfigurationContribution = {
    val source = RuntimeConfig
      .getString(configuration, RuntimeConfig.assemblyDescriptorKey)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .flatMap(GenericSubsystemDescriptor.loadAssemblyDescriptor)
    source match {
      case Some(assembly) =>
        val descriptor = GenericSubsystemDescriptor(
          path = assembly.path.getOrElse(Paths.get(".")),
          subsystemName = "configured-assembly"
        )
        val assemblyconfig = GenericSubsystemDescriptor.applyAssemblyOverride(descriptor, assembly).config
        val sourceid = assembly.path.map(_.toString)
        val missing = assemblyconfig.filterNot { case (key, _) =>
          configuration.configuration.values.contains(key)
        }
        if (missing.isEmpty) {
          AssemblyConfigurationContribution(configuration, Map.empty, sourceid)
        } else {
          val values = missing.map { case (key, value) =>
            key -> ConfigurationValue.StringValue(value)
          }
          val entries = missing.map { case (key, value) =>
            key -> ConfigurationResolution(
              key = key,
              finalValue = ConfigurationValue.StringValue(value),
              origin = ConfigurationOrigin.Resource,
              history = Nil,
              sourceType = Some("assembly-descriptor"),
              sourceId = sourceid
            )
          }
          AssemblyConfigurationContribution(
            ResolvedConfiguration(
              Configuration(configuration.configuration.values ++ values),
              ConfigurationTrace(configuration.trace.entries ++ entries)
            ),
            missing,
            sourceid
          )
        }
      case None =>
        AssemblyConfigurationContribution(configuration, Map.empty, None)
    }
  }

  private[cli] def _repository_policy_invocation_arguments(
    policy: RepositoryBootstrapPolicy
  ): Array[String] = {
    def _arguments_(key: String, values: Vector[String]): Vector[String] =
      values.map(value => s"--${key}=$value")

    (
      _arguments_(RuntimeConfig.repositoryDirKey, policy.repositoryDirs) ++
        _arguments_(RuntimeConfig.repositoryComponentDevDirKey, policy.repositoryComponentDevDirs) ++
        _arguments_(RuntimeConfig.componentDirKey, policy.componentDirs) ++
        _arguments_(RuntimeConfig.componentDevDirKey, policy.componentDevDirs) ++
        _arguments_(RuntimeConfig.componentCarDirKey, policy.componentCarDirs) ++
        _arguments_(RuntimeConfig.componentFileKey, policy.componentFiles) ++
        _arguments_(RuntimeConfig.subsystemDevDirKey, policy.subsystemDevDirs) ++
        _arguments_(RuntimeConfig.subsystemSarDirKey, policy.subsystemSarDirs)
    ).toArray
  }

  private[cli] def _with_auto_component_file(
    cwd: Path,
    args: Array[String],
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy,
    invocation: RuntimeInvocationParameters
  ): Array[String] = {
    val hascomponentfile =
      policy.componentDirs.nonEmpty ||
        policy.componentFiles.nonEmpty ||
        policy.componentDevDirs.nonEmpty ||
        policy.repositoryComponentDevDirs.nonEmpty ||
        policy.componentCarDirs.nonEmpty ||
        args.exists(_.startsWith("--component-file=")) ||
        args.contains("--component-file") ||
        args.exists(_.startsWith("--component-dev-dir=")) ||
        args.contains("--component-dev-dir") ||
        args.exists(_.startsWith("--component-car-dir=")) ||
        args.contains("--component-car-dir") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.componentFileKey}=")) ||
        args.contains(s"--${RuntimeConfig.componentFileKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.runtimeComponentFileKey}=")) ||
        args.contains(s"--${RuntimeConfig.runtimeComponentFileKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.componentDevDirKey}=")) ||
        args.contains(s"--${RuntimeConfig.componentDevDirKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.repositoryComponentDevDirKey}=")) ||
        args.contains(s"--${RuntimeConfig.repositoryComponentDevDirKey}") ||
        args.exists(_.startsWith(s"--${RuntimeConfig.componentCarDirKey}=")) ||
        args.contains(s"--${RuntimeConfig.componentCarDirKey}")
    val hassubsystem =
      policy.subsystemDevDirs.nonEmpty ||
        policy.subsystemSarDirs.nonEmpty ||
      RuntimeConfig.getString(configuration, RuntimeConfig.subsystemFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.runtimeSubsystemFileKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.subsystemDescriptorKey).nonEmpty ||
        RuntimeConfig.getString(configuration, RuntimeConfig.runtimeSubsystemDescriptorKey).nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.descriptor").nonEmpty ||
        ConfigurationAccess.getString(configuration, "cncf.subsystem.file").nonEmpty
    if (hascomponentfile || hassubsystem) {
      args
    } else {
      val archive = invocation.componentName match {
        case Some(name) => _detect_component_archive(cwd, _require_qualified_component_id(name), invocation.componentVersion)
        case None => _detect_latest_component_archive(cwd)
      }
      archive.map { path =>
        args ++ Array(s"--${RuntimeConfig.componentFileKey}=${path.toString}")
      }.getOrElse(args)
    }
  }

  private[cli] def _detect_component_archive(
    cwd: Path,
    componentid: ComponentId,
    version: Option[String]
  ): Option[Path] = {
    val roots = Vector(
      cwd.resolve("component").resolve("target"),
      cwd.resolve("target")
    ).map(_.normalize)
    roots.iterator.flatMap(_latest_component_archive(_, componentid, version)).toSeq.headOption
  }

  private[cli] def _detect_latest_component_archive(
    cwd: Path
  ): Option[Path] = {
    val roots = Vector(
      cwd.resolve("component").resolve("target"),
      cwd.resolve("target")
    ).map(_.normalize)
    roots.iterator.flatMap(_latest_component_archive(_)).toSeq.headOption
  }

  private[cli] def _latest_component_archive(
    root: Path,
    componentid: ComponentId,
    version: Option[String]
  ): Option[Path] =
    _component_archives(root)
      .filter(path => ComponentDescriptorLoader.loadArchive(path).toOption.exists { descriptor =>
        descriptor.requireCanonicalIdentityC.toOption.exists { case (id, release) =>
          id == componentid && version.forall(_ == release)
        }
      })
      .headOption

  private[cli] def _latest_component_archive(
    root: Path
  ): Option[Path] =
    _component_archives(root).headOption

  private[cli] def _component_archives(
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
    policy: RepositoryBootstrapPolicy,
    configuration: ResolvedConfiguration,
    args: Array[String],
    cwd: Path
  ): RuntimeRepositoryParameters = {
    val extracted =
      ComponentRepositorySpace.extractAdmittedRepositoryArgs(policy, args)
    val nodefault = extracted.noDefault || _test_home_repository_inheritance_disabled(configuration)
    val activerepositories =
      ComponentRepositorySpace.appendDefaultActiveRepositories(
        ComponentRepositorySpace.resolveSpecifications(extracted.active, policy.baseDirectory, nodefault),
        cwd,
        nodefault
      )
    val searchrepositories =
      ComponentRepositorySpace.appendDefaultSearchRepositories(
        ComponentRepositorySpace.resolveSpecifications(extracted.search, policy.baseDirectory, nodefault),
        activerepositories.getOrElse(Vector.empty),
        cwd,
        nodefault
      )
    RuntimeRepositoryParameters(
      activeRepositories = activerepositories,
      searchRepositories = searchrepositories
    )
  }

  private[cli] def _global_configuration_policies(
    cwd: Path,
    snapshot: Option[ConfigurationResolutionSnapshot],
    assemblyconfiguration: AssemblyConfigurationContribution,
    argumentbindings: Vector[CncfConfigurationArgumentBindingAssignment],
    environmentbindings: Vector[CncfConfigurationEnvironmentBindingAssignment]
  ): Consequence[RuntimeGlobalConfigurationPolicies] =
    snapshot match {
      case None => Consequence.success(RuntimeGlobalConfigurationPolicies(
        RepositoryBootstrapPolicy(baseDirectory = cwd.toAbsolutePath.normalize),
        RuntimeProcessExitPolicy.default
      ))
      case Some(value) =>
        for {
          runtime <- CncfRuntimeConfigurationProjection.globalCandidates(value, argumentbindings, environmentbindings)
          assembly <- CncfAssemblyConfigurationProjection.globalCandidates(
            assemblyconfiguration.values,
            assemblyconfiguration.sourceidentity
          )
          candidates <- ConfigurationBindingCandidates.from(runtime.bindings ++ assembly.bindings)
          context <- CncfConfigurationResolutionContext.globalOnly
          bindings <- ConfigurationBindingResolver.resolve(candidates, context.generic)
          repositorypolicy <- RepositoryBootstrapPolicy.resolve(bindings)
          processexitpolicy <- RuntimeProcessExitPolicy.from(bindings)
        } yield RuntimeGlobalConfigurationPolicies(repositorypolicy, processexitpolicy).copy(
            repositorybootstrappolicy = repositorypolicy.copy(
              baseDirectory = cwd.toAbsolutePath.normalize
            )
          )
    }

  private[cli] def _test_home_repository_inheritance_disabled(
    configuration: ResolvedConfiguration
  ): Boolean =
    RuntimeConfig.getString(configuration, RuntimeConfig.TEST_HOME_PATH_KEY).nonEmpty &&
      RuntimeConfig.getString(configuration, RuntimeConfig.TEST_HOME_INHERIT_REPOSITORIES_KEY)
        .exists(value => !_truthy(value))

  private[cncf] def canonicalInvocationParameters(
    configuration: ResolvedConfiguration,
    args: Array[String]
  ): RuntimeInvocationParameters = {
    val (frameworkargs, commandtail) = _split_command_args(args)
    val actualargs = _strip_invocation_selection_args(_normalize_help_aliases(frameworkargs)) ++ commandtail
    RuntimeInvocationParameters(
      actualArgs = actualargs,
      subsystemName = _subsystem_name(configuration, frameworkargs),
      componentName = _component_name(configuration, frameworkargs),
      componentVersion = _component_version(configuration, frameworkargs)
    )
  }

  private[cncf] def resolveSubsystemInvocation(
    invocation: RuntimeInvocationParameters,
    searchspecs: Vector[ComponentRepository.Specification],
    activespecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): RuntimeInvocationParameters = {
    val componentresolved = resolveComponentInvocation(invocation, searchspecs, activespecs)
    val args = componentresolved.actualArgs
    val (frameworkargs, _) = _split_command_args(args)
    val alreadyspecified =
      frameworkargs.exists(_has_option_value(_, _subsystem_descriptor_keys)) ||
        frameworkargs.sliding(2).exists {
          case Array(k, _) => _is_option_name(k, _subsystem_descriptor_keys)
          case _ => false
        }
    if (alreadyspecified) {
      componentresolved
    } else {
      val lookupspecs =
        (activespecs ++ searchspecs).foldLeft(Vector.empty[ComponentRepository.Specification]) { (z, spec) =>
          if (z.contains(spec)) z else z :+ spec
        }
      componentresolved.subsystemName
        .flatMap(name => _resolve_subsystem_descriptor_entry(lookupspecs, name))
        .map { case (spec, descriptor) =>
          val repoargs =
            _active_spec_argument(spec)
              .filterNot {
                case (RuntimeConfig.componentDirKey, value) =>
                  _has_component_dir_config_arg(frameworkargs, value)
                case _ =>
                  false
              }
              .map { case (key, value) => Array(s"--${key}=${value}") }
              .getOrElse(Array.empty[String])
          componentresolved.copy(actualArgs = _insert_framework_args(args, repoargs ++ Array(s"--${RuntimeConfig.subsystemFileKey}=${descriptor.path}")))
        }
        .getOrElse(componentresolved)
    }
  }

  private[cncf] def resolveComponentInvocation(
    invocation: RuntimeInvocationParameters,
    searchspecs: Vector[ComponentRepository.Specification],
    activespecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): RuntimeInvocationParameters = {
    val args = invocation.actualArgs
    val (frameworkargs, _) = _split_command_args(args)
    val alreadyspecified =
      _has_component_activation_arg(frameworkargs) ||
        frameworkargs.sliding(2).exists {
          case Array(k, _) =>
            _is_component_activation_arg(k)
          case _ =>
            false
        }
    if (alreadyspecified) {
      invocation
    } else {
      invocation.componentName
        .map(_require_qualified_component_id)
        .flatMap { componentid =>
          val name = componentid.name
          _resolve_component_archive_entry(searchspecs, name, invocation.componentVersion)
            .map { path =>
              invocation.copy(actualArgs = _insert_framework_args(args, Array(s"--${RuntimeConfig.componentFileKey}=${path}")))
            }
            .orElse {
              _resolve_component_descriptor_entry(searchspecs, name)
                .flatMap { case (spec, _) =>
                  _active_spec_argument(spec)
                    .filterNot {
                      case (RuntimeConfig.componentDirKey, value) =>
                        _has_component_dir_config_arg(frameworkargs, value)
                      case _ =>
                        false
                    }
                    .map { case (key, value) =>
                      invocation.copy(actualArgs = _insert_framework_args(args, Array(s"--${key}=${value}")))
                    }
                }
            }
        }
        .getOrElse(invocation)
    }
  }

  private[cli] def _require_qualified_component_id(componentname: String): ComponentId =
    ComponentId.parseC(componentname).TAKE

  private[cncf] def componentExtraFunction(
    specs: Vector[ComponentRepository.Specification],
    front: RuntimeFrontParameters,
    assemblysearchspecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): Subsystem => Seq[Component] =
    _trace_component_dir_extras(
      _component_extra_function(
        specs,
        assemblysearchspecs,
        front.discoverClasses,
        front.workspace,
        front.factoryClasses
      )
    )

  private[cncf] def developmentAssemblySearchSpecifications(
    active: Vector[ComponentRepository.Specification],
    search: Vector[ComponentRepository.Specification]
  ): Vector[ComponentRepository.Specification] = {
    val hasdevelopmenttarget = active.exists {
      case _: ComponentRepository.ComponentDevDirRepository.Specification => true
      case _: ComponentRepository.SubsystemDevDirRepository.Specification => true
      case _ => false
    }
    if (hasdevelopmenttarget) search.filterNot(active.contains).distinct else Vector.empty
  }

  private[cli] def _prepare_launch(
    cwd: Path,
    args: Array[String]
  ): Either[Int, RuntimeLaunch] = {
    val bootstrap = this.bootstrap(cwd, args)
    val configuration = bootstrap.configuration
    configure_slf4j_simple(configuration)
    val residualargs = bootstrap.front.residualArgs
    val extracted =
      ComponentRepositorySpace.extractAdmittedRepositoryArgs(bootstrap.repositorybootstrappolicy, residualargs)
    val (backendoption, logleveloption, actualargs0) =
      _extract_log_options(_split_command_args(extracted.residual)._1)
    val commandtail = _split_command_args(extracted.residual)._2
    val invocation0 = canonicalInvocationParameters(configuration, actualargs0)
    val invocation = invocation0.copy(actualArgs = invocation0.actualArgs ++ commandtail)
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
        val (runtimeparameterargs, commandtail) = _split_command_args(actualargs)
        val runtimeparse0 = _runtime_parameter_parser.parse(runtimeparameterargs.toIndexedSeq)
        val runtimeparse = runtimeparse0.copy(residual = runtimeparse0.residual ++ commandtail)
        val domainargs = _strip_configuration_args(runtimeparse.residual.toArray)
        val mode = _mode_from_args(domainargs)
        val runtimeconfig = _runtime_config(configuration)
        val aliasresolver = _alias_resolver(configuration)
        Right(
          RuntimeLaunch(
            cwd = cwd,
            configuration = configuration,
            activespecifications = specs,
            logbackendoption = backendoption,
            logleveloption = logleveloption,
            actualargs = actualargs,
            runtimeparse = runtimeparse,
            domainargs = domainargs,
            mode = mode,
            runtimeconfig = runtimeconfig,
            aliasresolver = aliasresolver
          )
        )
    }
  }

  private[cli] def _capture_consequence[A](body: => A): Consequence[A] =
    try {
      Consequence.success(body)
    } catch {
      case error: ConsequenceException =>
        error.consequence match {
          case Consequence.Failure(conclusion) => Consequence.Failure(conclusion)
          case Consequence.Success(_) => Consequence.Failure(Conclusion.from(error))
        }
      case NonFatal(error) =>
        Consequence.Failure(Conclusion.from(error))
    }

  private[cli] def _prepare_launch_consequence(
    cwd: Path,
    args: Array[String]
  ): Consequence[Either[Int, RuntimeLaunch]] =
    _capture_consequence(_prepare_launch(cwd, args))

  private[cli] def _prepare_runtime(
    launch: RuntimeLaunch
  ): Unit = {
    val logbackend = _decide_backend(
      launch.logbackendoption,
      _logging_backend_from_configuration(launch.configuration),
      launch.mode
    )
    _install_log_backend(logbackend)
    _update_visibility_policy(launch.logleveloption, launch.configuration, launch.mode)
    _reset_global_runtime_context()
    _create_global_runtime_context(
      launch.runtimeconfig,
      launch.configuration,
      launch.aliasresolver
    )
    _mark_used_parameters(launch.configuration)
  }

  // legacy: see run
  def runWithExtraComponents(
    args: Array[String],
    extraComponents: Subsystem => Seq[Component]
  ): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    _prepare_launch_consequence(cwd, args) match {
      case Consequence.Success(Left(code)) =>
        code
      case Consequence.Success(Right(launch)) =>
        _capture_consequence {
          _prepare_runtime(launch)
          val r: Consequence[OperationRequest] =
            _runtime_protocol_engine.makeOperationRequest(launch.domainargs)
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
                s"[subsytem] runWithExtraComponents dispatching mode args=${_trace_safe_args(launch.domainargs.drop(1).toIndexedSeq).mkString(" ")}"
              )
              requestmode match {
                case Some(RunMode.Server) =>
                  val subsystem = buildSubsystem(extraComponents, Some(RunMode.Server), args)
                  try {
                    new CncfRuntime().startServer(subsystem, launch.domainargs.drop(1))
                    0
                  } finally {
                    Subsystem.shutdownOwned(subsystem)
                  }
                case Some(RunMode.Client) =>
                  val subsystem = buildSubsystem(extraComponents, Some(RunMode.Client), args)
                  try
                    new CncfRuntime().executeClient(subsystem, launch.domainargs.drop(1))
                  finally
                    Subsystem.shutdownOwned(subsystem)
                case Some(RunMode.Command) =>
                  val subsystem = buildSubsystem(extraComponents, Some(RunMode.Command), args)
                  try
                    new CncfRuntime().executeCommand(subsystem, launch.domainargs.drop(1))
                  finally
                    Subsystem.shutdownOwned(subsystem)
                case Some(RunMode.ServerEmulator) =>
                  _execute_server_emulator(launch.domainargs.drop(1), args, extraComponents)
                case Some(RunMode.Script) =>
                  _run_script(launch.domainargs.drop(1), extraComponents, args)
                case _ =>
                  _print_usage()
                  2
              }
            case Consequence.Failure(conclusion) =>
              _print_error(conclusion)
              _exit_code(Consequence.Failure(conclusion))
          }
        } match {
          case Consequence.Success(code) => code
          case Consequence.Failure(conclusion) =>
            _print_error(conclusion)
            _exit_code(Consequence.Failure(conclusion))
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  def run(args: Array[String]): Int = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    _prepare_launch_consequence(cwd, args) match {
      case Consequence.Success(Left(code)) =>
        if (code == 2) {
          val normalizedargs = _normalize_help_aliases(args)
          if (normalizedargs.nonEmpty) {
            ()
          }
        }
        code
      case Consequence.Success(Right(launch)) =>
        _prepare_runtime(launch)
        val r: Consequence[OperationRequest] =
          _runtime_protocol_engine.makeOperationRequest(launch.domainargs)
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
                val subsystem = buildSubsystem(mode = Some(RunMode.Server), args = args)
                try {
                  new CncfRuntime().startServer(subsystem, launch.domainargs.drop(1))
                  0
                } finally {
                  Subsystem.shutdownOwned(subsystem)
                }
              case Some(RunMode.Client) =>
                observe_trace(
                  s"[client:trace] run dispatching to client mode args=${_trace_safe_args(launch.domainargs.drop(1).toIndexedSeq).mkString(" ")}"
                )
                val subsystem = buildSubsystem(mode = Some(RunMode.Client), args = args)
                try
                  new CncfRuntime().executeClient(subsystem, (launch.runtimeparse.consumed ++ launch.domainargs.drop(1)).toArray)
                finally
                  Subsystem.shutdownOwned(subsystem)
              case Some(RunMode.Command) =>
                val subsystem = buildSubsystem(mode = Some(RunMode.Command), args = args)
                try
                  new CncfRuntime().executeCommand(subsystem, launch.domainargs.drop(1))
                finally
                  Subsystem.shutdownOwned(subsystem)
              case Some(RunMode.ServerEmulator) =>
                _execute_server_emulator(launch.domainargs.drop(1), args, _ => Nil)
              case Some(RunMode.Script) =>
                _run_script(launch.domainargs.drop(1), _ => Nil, args)
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
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        _exit_code(Consequence.Failure(conclusion))
    }
  }
}
