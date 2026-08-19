package org.goldenport.cncf.cli

import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.cli.parser.ArgsParser
import org.goldenport.configuration.ConfigurationBindingCandidates
import org.goldenport.configuration.ConfigurationBindingCollection
import org.goldenport.configuration.ConfigurationBindingResolver
import org.goldenport.configuration.ConfigurationResolutionSnapshot
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.configuration.source.ConfigurationSource
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.config.CncfAssemblyConfigurationProjection
import org.goldenport.cncf.config.CncfConfigurationArgumentBindingAssignment
import org.goldenport.cncf.config.CncfConfigurationEnvironmentBindingAssignment
import org.goldenport.cncf.config.CncfConfigurationParameterCatalog
import org.goldenport.cncf.config.CncfConfigurationResolutionContext
import org.goldenport.cncf.config.CncfConfigurationTarget
import org.goldenport.cncf.config.CncfRuntimeConfigurationProjection
import org.goldenport.cncf.config.RepositoryBootstrapPolicy
import org.goldenport.cncf.config.ResolvedStandaloneUserProfile
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.config.RuntimeExecutionProfileConfiguration
import org.goldenport.cncf.config.StandaloneUserProfileBindingProjection
import org.goldenport.cncf.config.StandaloneUserProfileResolver
import org.goldenport.cncf.config.SubsystemInstanceId
import org.goldenport.cncf.config.SystemNodeShutdownConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.context.ExecutionProfileActivation
import org.goldenport.cncf.context.ExecutionProfileMode
import org.goldenport.cncf.context.ExecutionProfileResolver
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.context.ScopeKind
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.ProtocolEngine
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.protocol.spec.ResponseDefinition
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.GenericSubsystemFactory
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.SubsystemExecutionProfile
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.cncf.subsystem.SystemNodeConstruction
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.bootstrap.BootstrapConfig
import org.goldenport.cncf.bootstrap.CncfHandle
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.importer.StartupImport
import org.goldenport.cncf.path.AliasLoader
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.cli.help.CommandProtocolHelp
import org.goldenport.cncf.cli.help.HelpOperation
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.observability.VisibilityPolicy
import org.goldenport.cncf.observability.global.GlobalObservability
import org.goldenport.cncf.observability.global.GlobalObservabilityGate
import org.goldenport.cncf.observability.global.ObservabilityRoot
import org.goldenport.cncf.observability.global.GlobalObservable
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.spi.SpiResolver

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
private[cli] trait CncfRuntimeInstanceLifecyclePart {
  this: GlobalObservable & CncfRuntimeInstanceClientPart & CncfRuntimeInstanceCommandPart =>
  private[cli] final case class RuntimeConfigurationPreflight(
    identity: SubsystemInstanceId,
    candidates: ConfigurationBindingCandidates[CncfConfigurationTarget],
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget],
    admittedprofiles: Option[Vector[StandaloneUserProfileResolver.Admitted]] = None
  )

  private[cli] val _configuration_application_name = "textus"
  private[cli] val _runtime_service_name = "runtime"
  private[cli] val _help_flags = Set("--help", "-h")
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

  private[cli] def _create_global_context(config: RuntimeConfig): Unit =
    GlobalContext.set(
      GlobalContext(
        WorkAreaSpace.create(config)
      )
    )

  private[cli] var _global_runtime_context: Option[GlobalRuntimeContext] = None

  private[cli] def _reset_global_runtime_context(): Unit =
    _global_runtime_context = None

  private[cli] def _create_global_runtime_context(
    // httpDriver: HttpDriver,
    // mode: RunMode,
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
    observe_info(
      s"runtime clock mode=${if (runconfig.executionClock.isVirtual) "offset" else "system"}" +
        s" current=${execution.clock.instant()}" +
        s" virtual-start=${runconfig.executionClock.virtualStartAt.map(_.toString).getOrElse("none")}"
    )
    context
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

  def run(args: Array[String]): Int =
    run(args, (_: Subsystem) => Nil)

  def initializeForEmbedding(
    cwd: Path = Paths.get("").toAbsolutePath.normalize,
    args: Array[String] = Array.empty,
    modeHint: Option[RunMode] = None,
    extraComponents: Subsystem => Seq[Component] = (_: Subsystem) => Nil
  ): Consequence[Subsystem] =
    _initialize_consequence(_initialize(cwd, args, modeHint, extraComponents))

  def initializeHandle(
    config: BootstrapConfig
  ): Consequence[CncfHandle] =
    initializeForEmbedding(
      cwd = config.cwd,
      args = config.args,
      modeHint = config.modeHint,
      extraComponents = config.extraComponents
    ).map { initializedsubsystem =>
      new CncfHandle {
        @volatile private var _is_closed: Boolean = false

        def subsystem: Subsystem = initializedsubsystem

        def executeCommand(args: Array[String]): Consequence[Response] =
          if (_is_closed)
            Consequence.stateConflict("CncfHandle is already closed")
          else
            executeCommandResponse(initializedsubsystem, args)

        def executeAction(action: org.goldenport.cncf.action.Action): Consequence[OperationResponse] =
          if (_is_closed)
            Consequence.stateConflict("CncfHandle is already closed")
          else
            executeActionResponse(initializedsubsystem, action)

        def close(): Unit = {
          val owner = synchronized {
            if (_is_closed)
              false
            else {
              _is_closed = true
              true
            }
          }
          if (owner)
            try
              Subsystem.shutdownOwned(initializedsubsystem)
            finally
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
    val residualargs = args.headOption.flatMap(RunMode.from) match {
      case Some(_) =>
        val admitted = CncfRuntime._admit_configuration_binding_arguments(args.tail).residualArguments.toArray
        val stripped = CncfRuntime._strip_configuration_args(admitted)
        (args.head +: stripped).toArray
      case None =>
        val admitted = CncfRuntime._admit_configuration_binding_arguments(args).residualArguments.toArray
        CncfRuntime._strip_configuration_args(admitted)
    }
    val normalizedargs = _normalize_help_aliases(residualargs)
    _execute_top_level_help(normalizedargs) match {
      case Some(code) => return code
      case None => ()
    }
    if (normalizedargs.isEmpty) {
      _print_usage()
      return 2
    }
    _initialize_consequence(_initialize(args, extraComponents)) match {
      case Consequence.Success(subsystem) =>
        try {
          normalizedargs.headOption.flatMap(RunMode.from) match {
            case Some(RunMode.Command) =>
              _execute_command_args(subsystem, normalizedargs.drop(1))
            case _ =>
              _runtime_protocol_engine.makeOperationRequest(normalizedargs) match {
                case Consequence.Success(req) =>
                  _run(subsystem, req)
                case Consequence.Failure(conclusion) =>
                  _print_error(conclusion)
                  _exit_code(Consequence.Failure(conclusion))
              }
          }
        } finally {
          Subsystem.shutdownOwned(subsystem)
        }
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  private[cli] def _initialize_consequence(body: => Subsystem): Consequence[Subsystem] =
    CncfRuntime._capture_consequence(body)

  private[cli] def _execute_command_args(
    subsystem: Subsystem,
    args: Array[String]
  ): Int = {
    val normalizedargs = CommandProtocolHelp.normalizeArgs(args) match {
      case Left(code) => return code
      case Right(xs) => xs
    }
    val result = _to_request(subsystem, normalizedargs).flatMap { req =>
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

  private[cli] def _initialize(
    args: Array[String],
    componentextras: Subsystem => Seq[Component]
  ): Subsystem =
    _initialize(
      Paths.get("").toAbsolutePath.normalize,
      args,
      modehint = None,
      componentextras
    )

  private[cli] def _initialize(
    cwd: Path,
    args: Array[String],
    modehint: Option[RunMode],
    componentextras: Subsystem => Seq[Component]
  ): Subsystem = {
    val bootstrap0 = CncfRuntime.bootstrap(cwd, args)
    val searchspecs = bootstrap0.repositories.searchRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val activespecs = bootstrap0.repositories.activeRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val invocation = CncfRuntime.resolveSubsystemInvocation(bootstrap0.invocation, searchspecs, activespecs)
    val bootstrap =
      if (invocation.actualArgs.sameElements(bootstrap0.invocation.actualArgs)) bootstrap0
      else CncfRuntime.bootstrap(
        cwd,
        CncfRuntime.restoreConfigurationArgumentBindings(
          invocation.actualArgs,
          bootstrap0.configurationargumentbindings
        )
      )
    val configuration = bootstrap.configuration
    val resolvedsearchspecs = bootstrap.repositories.searchRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    val resolvedactivespecs = bootstrap.repositories.activeRepositories match {
      case Left(message) =>
        throw new IllegalArgumentException(message)
      case Right(specs) =>
        specs
    }
    CncfRuntime.configure_slf4j_simple(configuration)
    val runtimehint = modehint.orElse(invocation.actualArgs.headOption.flatMap(RunMode.from))
    val runtimeconfigurationpreflight = bootstrap.configurationsnapshot.map { snapshot =>
      _runtime_configuration_preflight(
        snapshot,
        bootstrap.assemblyconfiguration,
        configuration,
        bootstrap.repositorybootstrappolicy,
        bootstrap.configurationargumentbindings,
        bootstrap.configurationenvironmentbindings
      ).TAKE
    }
    val executionprofile = runtimeconfigurationpreflight.fold(RuntimeConfig.DEFAULT_EXECUTION_PROFILE) { preflight =>
      RuntimeExecutionProfileConfiguration.from(preflight.bindings).flatMap { typed =>
        _execution_profile_activation(configuration, preflight.bindings).flatMap { activation =>
          ExecutionProfileResolver.resolve(typed, activation, sys.env)
        }
      }.TAKE
    }
    val runconfig = RuntimeConfig.from(configuration, runtimehint, executionprofile)
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
    val systemnodeshutdownconfiguration = runtimeconfigurationpreflight.fold(SystemNodeShutdownConfiguration.default) { preflight =>
      SystemNodeShutdownConfiguration.from(preflight.bindings).TAKE
    }
    observe_info(systemnodeshutdownconfiguration.observationMessage)
    val systemnode = SystemNode.create(systemnodeshutdownconfiguration)
    val subsystem0 = try {
      SystemNodeConstruction.withNode(systemnode) {
        DefaultSubsystemFactory.runtimeDefaultWithScopeC(
          _runtime_scope_context(),
          Some(mode),
          configuration,
          aliasresolver,
          Some(bootstrap.repositorybootstrappolicy)
        ).TAKE
      }
    } catch {
      case e: Throwable =>
        systemnode.shutdownC()
        throw e
    }
    _with_startup_cleanup(subsystem0) {
      val subsystem =
        if (_is_test_runtime || runconfig.executionProfile.identity.mode == ExecutionProfileMode.Controlled)
          subsystem0.enableControlledTestExecution()
        else
          subsystem0
      bootstrap.configurationsnapshot match {
        case Some(_) => _admit_runtime_configuration_preflight(
          runtimeconfigurationpreflight.getOrElse(throw new IllegalStateException("runtime configuration preflight is required")),
          bootstrap.assemblyconfiguration,
          subsystem,
          StandaloneUserProfileResolver.resolve,
        ).TAKE
        case None => RuntimeStandaloneUserProfileAdmission.admit(subsystem, serverExecution = mode == RunMode.Server).TAKE
      }
      observe_trace(
        s"[subsytem] buildSubsystem start mode=${mode.name} componentCount=${subsystem.components.size}"
      )
      GlobalRuntimeContext.current.foreach(_.updateSubsystemVersion(subsystem.version.getOrElse(CncfVersion.current)))
      val colfactory = CncfRuntime.collaboratorFactory(bootstrap)
      val compfactory = ComponentFactory.create(
        subsystem,
        configuration,
        colfactory,
        resolvedactivespecs
      )
      if (subsystem.descriptor.nonEmpty && subsystem.components.nonEmpty) {
        subsystem.components.foreach(compfactory.bootstrap)
      } else {
        subsystem.setup(compfactory)
      }
      val runtimespecs =
        if (subsystem.descriptor.nonEmpty)
          _merge_component_specs(resolvedactivespecs, resolvedsearchspecs)
        else
          resolvedactivespecs
      val runtimeextras = CncfRuntime.componentExtraFunction(runtimespecs, bootstrap.front)
      val extras = _collapse_component_duplicates(
        subsystem.components.toVector,
        (runtimeextras(subsystem) ++ componentextras(subsystem)).map(compfactory.bootstrap)
      )
      if (extras.nonEmpty) {
        subsystem.upsert(extras)
      }
      if (_apply_component_assembly_defaults(subsystem)) {
        val inheritedextras = _collapse_component_duplicates(
          subsystem.components.toVector,
          runtimeextras(subsystem).map(compfactory.bootstrap)
        )
        if (inheritedextras.nonEmpty) {
          subsystem.upsert(inheritedextras)
        }
      }
      _verify_descriptor_components_available(subsystem, runtimespecs)
      _resolve_runtime_spi(subsystem)
      StartupImport.run(
        cwd,
        subsystem.runtimeStartupImportConfigurationC.TAKE,
        runconfig,
        subsystem.startupImportEntityCollectionResolver
      ) match {
        case Consequence.Success(_) =>
          ()
        case Consequence.Failure(conclusion) =>
          throw new IllegalStateException(conclusion.show)
      }
      subsystem
    }
  }

  private[cli] def _admit_runtime_configuration_snapshot(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: Subsystem
  ): Consequence[Unit] =
    _admit_runtime_configuration_snapshot(snapshot, CncfRuntime.AssemblyConfigurationContribution(subsystem.configuration, Map.empty, None), subsystem, StandaloneUserProfileResolver.resolve)

  private[cli] def _admit_runtime_configuration_snapshot(
    snapshot: ConfigurationResolutionSnapshot,
    subsystem: Subsystem,
    profileadmission: RuntimeStandaloneUserProfileAdmission.ProfileAdmission
  ): Consequence[Unit] =
    _admit_runtime_configuration_snapshot(snapshot, CncfRuntime.AssemblyConfigurationContribution(subsystem.configuration, Map.empty, None), subsystem, profileadmission)

  private[cli] def _admit_runtime_configuration_snapshot(
    snapshot: ConfigurationResolutionSnapshot,
    assemblyconfiguration: CncfRuntime.AssemblyConfigurationContribution,
    subsystem: Subsystem,
    profileadmission: RuntimeStandaloneUserProfileAdmission.ProfileAdmission
  ): Consequence[Unit] =
    _admit_runtime_configuration_snapshot(
      snapshot,
      assemblyconfiguration,
      subsystem,
      profileadmission,
      Vector.empty
    )

  private[cli] def _admit_runtime_configuration_snapshot(
    snapshot: ConfigurationResolutionSnapshot,
    assemblyconfiguration: CncfRuntime.AssemblyConfigurationContribution,
    subsystem: Subsystem,
    profileadmission: RuntimeStandaloneUserProfileAdmission.ProfileAdmission,
    configurationargumentbindings: Vector[CncfConfigurationArgumentBindingAssignment],
    configurationenvironmentbindings: Vector[CncfConfigurationEnvironmentBindingAssignment] = Vector.empty
  ): Consequence[Unit] =
    SubsystemInstanceId.default(subsystem.name).flatMap { identity =>
      for {
        runtimecandidates <- CncfRuntimeConfigurationProjection.candidates(snapshot, identity, configurationargumentbindings, configurationenvironmentbindings)
        assemblycandidates <- CncfAssemblyConfigurationProjection.candidates(assemblyconfiguration.values, assemblyconfiguration.sourceidentity, identity)
        context <- CncfConfigurationResolutionContext.forSubsystem(identity)
        preprofilecandidates <- ConfigurationBindingCandidates.from(runtimecandidates.bindings ++ assemblycandidates.bindings)
        preprofilecollection <- ConfigurationBindingResolver.resolve(preprofilecandidates, context.generic)
        _ <- _admit_runtime_configuration_preflight(
          RuntimeConfigurationPreflight(identity, preprofilecandidates, preprofilecollection),
          assemblyconfiguration,
          subsystem,
          profileadmission
        )
      } yield ()
    }

  private[cli] def _admit_runtime_configuration_preflight(
    preflight: RuntimeConfigurationPreflight,
    assemblyconfiguration: CncfRuntime.AssemblyConfigurationContribution,
    subsystem: Subsystem,
    profileadmission: RuntimeStandaloneUserProfileAdmission.ProfileAdmission
  ): Consequence[Unit] =
    if (preflight == null || subsystem == null)
      Consequence.configurationInvalid("runtime configuration preflight is invalid")
    else preflight.admittedprofiles match {
      case Some(_) =>
        subsystem.executionProfileForRuntimeConfigurationBindingsC(preflight.bindings).flatMap { profile =>
          subsystem.admitRuntimeConfigurationBindingsC(preflight.bindings, profile)
        }
      case None =>
        for {
          profile <- subsystem.executionProfileForRuntimeConfigurationBindingsC(preflight.bindings)
          admitted <- RuntimeStandaloneUserProfileAdmission.admit(subsystem, profile, profileadmission)
          profilecandidates <- StandaloneUserProfileBindingProjection.runtimeCandidates(
            admitted,
            preflight.identity,
            _fixed_profile_local_subject_id(profile, subsystem.descriptor)
          )
          candidates <- ConfigurationBindingCandidates.from(preflight.candidates.bindings ++ profilecandidates.bindings)
          context <- CncfConfigurationResolutionContext.forSubsystem(preflight.identity)
          collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
          _ <- subsystem.admitRuntimeConfigurationBindingsC(collection, profile)
        } yield ()
    }

  private[cli] def _runtime_configuration_preflight(
    snapshot: ConfigurationResolutionSnapshot,
    assemblyconfiguration: CncfRuntime.AssemblyConfigurationContribution,
    configuration: ResolvedConfiguration,
    repositorybootstrappolicy: RepositoryBootstrapPolicy,
    configurationargumentbindings: Vector[CncfConfigurationArgumentBindingAssignment],
    configurationenvironmentbindings: Vector[CncfConfigurationEnvironmentBindingAssignment]
  ): Consequence[RuntimeConfigurationPreflight] =
    for {
      descriptor <- GenericSubsystemFactory.runtimeResolveDescriptorC(
        configuration,
        Some(repositorybootstrappolicy)
      )
      selectedname = descriptor.map(_.subsystemName).getOrElse(
        RuntimeConfig.getString(configuration, RuntimeConfig.subsystemNameKey)
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse(DefaultSubsystemFactory.subsystemName)
      )
      identity <- SubsystemInstanceId.default(selectedname)
      runtimecandidates <- CncfRuntimeConfigurationProjection.candidates(
        snapshot,
        identity,
        configurationargumentbindings,
        configurationenvironmentbindings
      )
      assemblyconfigurationcandidates <- CncfDiscoveredAssemblyConfigurationProjection.candidates(assemblyconfiguration.values, assemblyconfiguration.sourceidentity, descriptor, identity)
      candidates <- ConfigurationBindingCandidates.from(runtimecandidates.bindings ++ assemblyconfigurationcandidates.bindings)
      context <- CncfConfigurationResolutionContext.forSubsystem(identity)
      bindings <- ConfigurationBindingResolver.resolve(candidates, context.generic)
      preflight <- _admit_runtime_configuration_preflight_for_launch(
        RuntimeConfigurationPreflight(identity, candidates, bindings),
        descriptor
      )
    } yield preflight

  private[cli] def _admit_runtime_configuration_preflight_for_launch(
    preflight: RuntimeConfigurationPreflight,
    descriptor: Option[GenericSubsystemDescriptor]
  ): Consequence[RuntimeConfigurationPreflight] =
    _fixed_profile_for_launch(preflight, descriptor).flatMap {
      case Some(SubsystemExecutionProfile.Fixed) =>
        for {
          admitted <- StandaloneUserProfileResolver.resolve(SubsystemExecutionProfile.Fixed)
          profilecandidates <- StandaloneUserProfileBindingProjection.runtimeCandidates(
            admitted,
            preflight.identity,
            _local_subject_id(descriptor)
          )
          candidates <- ConfigurationBindingCandidates.from(preflight.candidates.bindings ++ profilecandidates.bindings)
          context <- CncfConfigurationResolutionContext.forSubsystem(preflight.identity)
          bindings <- ConfigurationBindingResolver.resolve(candidates, context.generic)
          _ <- ResolvedStandaloneUserProfile.resolve(bindings)
        } yield preflight.copy(
          candidates = candidates,
          bindings = bindings,
          admittedprofiles = Some(admitted)
        )
      case None => Consequence.success(preflight)
    }

  private[cli] def _fixed_profile_for_launch(
    preflight: RuntimeConfigurationPreflight,
    descriptor: Option[GenericSubsystemDescriptor]
  ): Consequence[Option[SubsystemExecutionProfile]] =
    descriptor.flatMap(_.security).flatMap(_.authentication).flatMap(_.localSubject) match {
      case Some(_) =>
        preflight.bindings.binding(CncfConfigurationParameterCatalog.subsystemUserMode).map {
          case Some(binding) if binding.value == SubsystemUserMode.Standalone =>
            Some(SubsystemExecutionProfile.Fixed)
          case _ => None
        }
      case None => Consequence.success(None)
    }

  private[cli] def _local_subject_id(
    descriptor: Option[GenericSubsystemDescriptor]
  ): Option[String] =
    descriptor
      .flatMap(_.security)
      .flatMap(_.authentication)
      .flatMap(_.localSubject)
      .map(_.id.trim)
      .filter(_.nonEmpty)

  private[cli] def _fixed_profile_local_subject_id(
    profile: SubsystemExecutionProfile,
    descriptor: Option[GenericSubsystemDescriptor]
  ): Option[String] =
    if (profile == SubsystemExecutionProfile.Fixed)
      _local_subject_id(descriptor)
    else
      None

  private[cli] def _execution_profile_activation(
    configuration: ResolvedConfiguration,
    bindings: ConfigurationBindingCollection[CncfConfigurationTarget]
  ): Consequence[ExecutionProfileActivation] =
    if (RuntimeConfig.getString(configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY).exists(_.trim.nonEmpty))
      Consequence.success(ExecutionProfileActivation.ExplicitTestDescriptor)
    else
      bindings.value(CncfConfigurationParameterCatalog.operationMode).map { operationmode =>
        if (operationmode.contains(org.goldenport.cncf.config.OperationMode.Test) || _is_test_runtime)
          ExecutionProfileActivation.TestCommand
        else
          ExecutionProfileActivation.Ordinary
      }

  private[cli] def _with_startup_cleanup[A](subsystem: Subsystem)(f: => A): A =
    try {
      f
    } catch {
      case e: Throwable =>
        try {
          Subsystem.shutdownOwned(subsystem)
        } catch {
          case cleanup: Throwable =>
            e.addSuppressed(cleanup)
        }
        throw e
    }

  private[cli] def _verify_descriptor_components_available(
    subsystem: Subsystem,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Unit =
    subsystem.descriptor.foreach { descriptor =>
      val missing = descriptor.componentBindings.filterNot(binding =>
        _has_descriptor_component(subsystem, binding.componentName)
      )
      if (missing.nonEmpty) {
        val components = missing.map { binding =>
          binding.componentVersion
            .map(version => s"${binding.componentName}:${version}")
            .getOrElse(binding.componentName)
        }
        val repositories = repositoryspecs.map(_repository_spec_label)
        val requestedby = descriptor.path
        val assemblydescriptor = descriptor.assemblyDescriptor.flatMap(_.path)
        val message =
          Vector(
            s"assembly component dependency not resolved: ${components.mkString(", ")}",
            s"subsystem=${descriptor.subsystemName}",
            s"requestedBy=${requestedby}",
            assemblydescriptor.map(path => s"assemblyDescriptor=${path}").getOrElse(""),
            s"repositories=${repositories.mkString(",")}"
          ).filter(_.nonEmpty).mkString(" ")
        Consequence.resourceNotFound[Unit](
          message,
          Vector(
            Facet.Component(components.mkString(",")),
            Facet.Properties(Map(
              "subsystem" -> descriptor.subsystemName,
              "requestedBy" -> requestedby.toString,
              "assemblyDescriptor" -> assemblydescriptor.map(_.toString).getOrElse(""),
              "repositories" -> repositories.mkString(",")
            ))
          )
        ).RAISE
      }
    }

  private[cli] def _has_descriptor_component(
    subsystem: Subsystem,
    componentname: String
  ): Boolean = {
    subsystem.components.exists { component =>
      val candidates =
        Vector(
          Some(component.core.name),
          component.artifactMetadata.flatMap(_.component),
          component.artifactMetadata.map(_.name)
        ).flatten
      candidates.exists(name => _matches_runtime_component_name(name, componentname))
    }
  }

  private[cli] def _matches_runtime_component_name(
    runtimecomponentname: String,
    descriptorcomponentname: String
  ): Boolean = {
    val descriptor = descriptorcomponentname.trim
    val withouttextus =
      if (descriptor.startsWith("textus-")) descriptor.stripPrefix("textus-")
      else if (descriptor.startsWith("textus_")) descriptor.stripPrefix("textus_")
      else descriptor
    NamingConventions.equivalentByNormalized(runtimecomponentname, descriptor) ||
      NamingConventions.equivalentByNormalized(runtimecomponentname, withouttextus)
  }

  private[cli] def _repository_spec_label(
    spec: ComponentRepository.Specification
  ): String =
    spec match {
      case ComponentRepository.ComponentDirRepository.Specification(base) =>
        s"component-dir:${base}"
      case ComponentRepository.ComponentFileRepository.Specification(file) =>
        s"component-file:${file}"
      case ComponentRepository.ComponentDevDirRepository.Specification(base) =>
        s"component-dev-dir:${base}"
      case ComponentRepository.SubsystemDevDirRepository.Specification(base) =>
        s"subsystem-dev-dir:${base}"
      case ComponentRepository.StandardRepository.Specification(kind, baseurl, cache) =>
        s"standard-repository:${kind}:${baseurl}:${cache}"
      case ComponentRepository.ScalaCliRepository.Specification(base) =>
        s"scala-cli:${base}"
    }

  private[cli] def _resolve_runtime_spi(
    subsystem: Subsystem
  ): Unit = {
    given ExecutionContext = ExecutionContext.create()
    val bindings = subsystem.descriptor
      .map { descriptor =>
        GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor) match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) =>
            throw new IllegalStateException(conclusion.display)
        }
      }
      .getOrElse(Vector.empty)
    val resolution = SpiResolver.resolveAssemblyOrRaise(subsystem.components.toVector, bindings)
    subsystem.withComponentApiResolver(resolution.componentApiResolver)
  }

  private[cli] def _apply_component_assembly_defaults(
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

  private[cli] def _primary_component_assembly_defaults(
    subsystem: Subsystem,
    descriptor: GenericSubsystemDescriptor
  ): Option[GenericSubsystemDescriptor] = {
    val primaryname = descriptor.componentBindings.headOption.map(_.componentName)
    primaryname.flatMap { name =>
      subsystem.components.find { component =>
        val runtimename =
          component.artifactMetadata.flatMap(_.component)
            .orElse(component.artifactMetadata.map(_.name))
            .getOrElse(component.name)
        _component_key(runtimename) == _component_key(name)
      }.flatMap { component =>
        component.artifactMetadata.flatMap(_.archivePath).flatMap { path =>
          val p = java.nio.file.Paths.get(path)
          if (_same_path(Some(p), descriptor.path)) None
          else GenericSubsystemDescriptor.loadComponentArchive(p).toOption
        }
      }
    }
  }

  private[cli] def _same_path(
    lhs: Option[java.nio.file.Path],
    rhs: java.nio.file.Path
  ): Boolean =
    lhs.exists(p => p.toAbsolutePath.normalize == rhs.toAbsolutePath.normalize)

  private[cli] def _component_key(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase.replace("_", "").replace("-", "")

  private[cli] def _collapse_component_duplicates(
    existing: Vector[Component],
    candidates: Seq[Component]
  ): Vector[Component] = {
    val existingkeys = existing.map(x => NamingConventions.toComparisonKey(x.core.name)).toSet
    val seen = mutable.LinkedHashMap.empty[String, Component]
    candidates.foreach { component =>
      val key = NamingConventions.toComparisonKey(component.core.name)
      if (existingkeys.contains(key)) {
        existing.find(x => NamingConventions.toComparisonKey(x.core.name) == key).foreach { original =>
          val current = seen.getOrElse(key, original)
          val selection = AssemblyReport.selectPreferred(current, component)
          if (selection.selected ne current) {
            seen.update(key, selection.selected)
          }
          if (!AssemblyReport.isSameAssemblySource(current, component)) {
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
        }
      } else {
        seen.get(key) match {
          case Some(current) =>
            val selection = AssemblyReport.selectPreferred(current, component)
            seen.update(key, selection.selected)
            if (!AssemblyReport.isSameAssemblySource(current, component)) {
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
          case None =>
            seen += key -> component
        }
      }
    }
    seen.values.toVector
  }

  private[cli] def _merge_component_specs(
    activespecs: Vector[ComponentRepository.Specification],
    searchspecs: Vector[ComponentRepository.Specification]
  ): Vector[ComponentRepository.Specification] =
    (activespecs ++ searchspecs).foldLeft(Vector.empty[ComponentRepository.Specification]) { (z, x) =>
      if (z.contains(x)) z else z :+ x
    }

  private[cli] def _resolve_configuration(
    cwd: Path,
    args: Array[String] = Array.empty
  ): ResolvedConfiguration = {
    CncfRuntime._resolve_configuration(cwd, args)
  }

  private[cli] def _explicit_config_sources(
    cwd: Path,
    configargs: Map[String, String]
  ): Vector[ConfigurationSource] = {
    CncfRuntime._explicit_config_sources(cwd, configargs)
  }

  private[cli] def _split_config_paths(
    value: Option[String]
  ): Vector[String] =
    CncfRuntime._split_config_paths(value)

  private[cli] def _normalize_config_path(
    cwd: Path,
    path: String
  ): Path = {
    CncfRuntime._normalize_config_path(cwd, path)
  }

  private[cli] def _config_args(
    args: Array[String]
  ): Map[String, String] = {
    CncfRuntime._config_args(args)
  }

  private[cli] def _runtime_scope_context(): ScopeContext =
    _global_runtime_context.getOrElse {
      ScopeContext(
        kind = ScopeKind.Subsystem,
        name = DefaultSubsystemFactory.subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )
    }

  private[cli] def _run(
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

  private[cli] def _make_args(req: OperationRequest): Array[String] =
    _make_args(req.request)

  private[cli] def _make_args(req: Request): Array[String] = req.toArgs

  // legacy
  private[cli] def _run0(
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
}
