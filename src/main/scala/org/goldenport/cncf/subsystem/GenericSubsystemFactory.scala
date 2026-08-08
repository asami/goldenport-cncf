package org.goldenport.cncf.subsystem

import java.nio.file.{Path, Paths}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentDescriptorLoader, ComponentInstanceMetadata, ComponentOrigin, DevelopmentCarRuntimeAdmission}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RepositoryBootstrapPolicy, RuntimeConfig, RuntimeTestDescriptor}
import org.goldenport.cncf.component.repository.ComponentRepositorySpace
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.Consequence
import org.goldenport.cncf.spi.SpiResolver

/*
 * @since   Apr.  7, 2026
 *  version Apr. 23, 2026
 *  version Apr. 25, 2026
 *  version May. 18, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object GenericSubsystemFactory {
  def subsystemName(
    configuration: ResolvedConfiguration
  ): Option[String] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.subsystemNameKey)
      .map(_.trim)
      .filter(_.nonEmpty)

  def descriptorPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.subsystemDescriptorKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.descriptor"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.subsystemFileKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.file"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.subsystemDevDirKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.dev.dir"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.subsystemSarDirKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.sar.dir"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentArchivePath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.componentFileKey)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.runtimeComponentFileKey))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentCarDirPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.componentCarDirKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.component.car.dir"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentDevDirPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.componentDevDirKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.component.dev.dir"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def loadDescriptor(
    configuration: ResolvedConfiguration
  ): Option[GenericSubsystemDescriptor] =
    loadDescriptorC(configuration).toOption.flatten

  def loadDescriptorC(
    configuration: ResolvedConfiguration
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    descriptorPath(configuration).flatMap { path =>
      Some(GenericSubsystemDescriptor.load(path).flatMap(_with_assembly_descriptor_override_c(_, configuration)).map(Some(_)))
    }.getOrElse(Consequence.success(None))

  def resolveDescriptor(
    configuration: ResolvedConfiguration
  ): Option[GenericSubsystemDescriptor] =
    resolveDescriptorC(configuration).toOption.flatten

  def resolveDescriptorC(
    configuration: ResolvedConfiguration
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _or_else(_component_dev_descriptor_c(configuration)) {
      _or_else(loadDescriptorC(configuration)) {
      _or_else(
        subsystemName(configuration) match {
          case Some(name) =>
            ComponentRepository.resolveSubsystemDescriptor(_repository_specs(configuration), name) match {
              case Some(descriptor) =>
                _with_assembly_descriptor_override_c(descriptor, configuration).map(Some(_))
              case None =>
                Consequence.success(None)
            }
          case None =>
            Consequence.success(None)
        }
      ) {
        _or_else(
          componentArchivePath(configuration) match {
            case Some(path) =>
              GenericSubsystemDescriptor.loadComponentArchive(path)
                .flatMap(d => _with_assembly_descriptor_override_c(d, configuration).map(Some(_)))
            case None => Consequence.success(None)
          }
        ) {
          _or_else(
            componentCarDirPath(configuration) match {
              case Some(path) =>
                for
                  descriptor <- ComponentDescriptorLoader.loadArchive(path)
                  subsystem <- _component_descriptor_to_subsystem_c(path, descriptor)
                  resolved <- _with_assembly_descriptor_override_c(subsystem, configuration)
                yield
                  Some(resolved)
              case None => Consequence.success(None)
            }
          ) {
            _or_else(
              componentDevDirPath(configuration) match {
                case Some(path) =>
                  ComponentRepository.ComponentDevDirRepository.validate(path).flatMap { _ =>
                    ComponentRepository.ComponentDevDirRepository.devComponentDescriptors(path).headOption match {
                      case Some(descriptor) =>
                        _development_component_descriptor_to_subsystem_c(path, descriptor)
                          .map(Some(_))
                      case None =>
                        Consequence.resourceInvalid(
                            s"[component-dev-dir] prepared component descriptor cannot be decoded: " +
                            s"${path.resolve(DevelopmentCarRuntimeAdmission.componentDescriptorIdentity(path))}. " +
                            s"Run 'sbt cozyPrepareRuntime' in $path, then restart the application server. " +
                            "CNCF will not fall back to a packaged CAR while component-dev-dir is explicit."
                        )
                    }
                  }
                case None => Consequence.success(None)
              }
            ) {
              _terminal_configured_component_or_assembly_descriptor_c(configuration)
            }
          }
        }
      }
      }
    }

  /**
   * Runtime-only descriptor resolution. The repository activation values are
   * consumed from the bootstrap policy, never reparsed from configuration.
   */
  private[cncf] def runtimeResolveDescriptorC(
    configuration: ResolvedConfiguration,
    repositoryBootstrapPolicy: Option[RepositoryBootstrapPolicy]
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    repositoryBootstrapPolicy match {
      case Some(policy) => _runtime_resolve_descriptor_c(configuration, policy)
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

  private def _runtime_resolve_descriptor_c(
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _or_else(_runtime_component_dev_descriptor_c(configuration, policy)) {
      _or_else(_runtime_load_descriptor_c(configuration, policy)) {
        _or_else(_runtime_named_descriptor_c(configuration, policy)) {
          _or_else(
            _runtime_component_archive_path(policy) match {
              case Some(path) =>
                GenericSubsystemDescriptor.loadComponentArchive(path)
                  .flatMap(d => _with_assembly_descriptor_override_c(d, configuration).map(Some(_)))
              case None => Consequence.success(None)
            }
          ) {
            _or_else(
              _runtime_component_car_dir_path(policy) match {
                case Some(path) =>
                  for
                    descriptor <- ComponentDescriptorLoader.loadArchive(path)
                    subsystem <- _component_descriptor_to_subsystem_c(path, descriptor)
                    resolved <- _with_assembly_descriptor_override_c(subsystem, configuration)
                  yield
                    Some(resolved)
                case None => Consequence.success(None)
              }
            ) {
              _terminal_configured_component_or_assembly_descriptor_c(configuration)
            }
          }
        }
      }
    }

  private def _runtime_load_descriptor_c(
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _runtime_descriptor_path(configuration, policy).flatMap { path =>
      Some(GenericSubsystemDescriptor.load(path).flatMap(_with_assembly_descriptor_override_c(_, configuration)).map(Some(_)))
    }.getOrElse(Consequence.success(None))

  private def _runtime_named_descriptor_c(
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    subsystemName(configuration) match {
      case Some(name) =>
        _runtime_search_repository_specs_c(policy).flatMap { repositories =>
          ComponentRepository.resolveSubsystemDescriptor(repositories, name) match {
            case Some(descriptor) =>
              _with_assembly_descriptor_override_c(descriptor, configuration).map(Some(_))
            case None =>
              Consequence.success(None)
          }
        }
      case None =>
        Consequence.success(None)
    }

  private def _runtime_component_dev_descriptor_c(
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _runtime_component_dev_dir_path(policy) match {
      case Some(path) =>
        ComponentRepository.ComponentDevDirRepository.validate(path).flatMap { _ =>
          ComponentRepository.ComponentDevDirRepository.devComponentDescriptors(path).headOption match {
            case Some(descriptor) =>
              _development_component_descriptor_to_subsystem_c(path, descriptor)
                .flatMap(_with_assembly_descriptor_override_c(_, configuration))
                .map(Some(_))
            case None =>
              Consequence.resourceInvalid(
                s"[component-dev-dir] prepared component descriptor cannot be decoded: " +
                  s"${path.resolve(DevelopmentCarRuntimeAdmission.componentDescriptorIdentity(path))}. " +
                  s"Run 'sbt cozyPrepareRuntime' in $path, then restart the application server. " +
                  "CNCF will not fall back to a packaged CAR while component-dev-dir is explicit."
              )
          }
        }
      case None => Consequence.success(None)
    }

  private def _component_dev_descriptor_c(
    configuration: ResolvedConfiguration
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    componentDevDirPath(configuration) match {
      case Some(path) =>
        ComponentRepository.ComponentDevDirRepository.validate(path).flatMap { _ =>
          ComponentRepository.ComponentDevDirRepository.devComponentDescriptors(path).headOption match {
            case Some(descriptor) =>
              _development_component_descriptor_to_subsystem_c(path, descriptor)
                .flatMap(_with_assembly_descriptor_override_c(_, configuration))
                .map(Some(_))
            case None =>
              Consequence.resourceInvalid(
                s"[component-dev-dir] prepared component descriptor cannot be decoded: " +
                  s"${path.resolve(DevelopmentCarRuntimeAdmission.componentDescriptorIdentity(path))}. " +
                  s"Run 'sbt cozyPrepareRuntime' in $path, then restart the application server. " +
                  "CNCF will not fall back to a packaged CAR while component-dev-dir is explicit."
              )
          }
        }
      case None => Consequence.success(None)
    }

  private def _assembly_descriptor_c(
    configuration: ResolvedConfiguration
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _assembly_descriptor_path(configuration) match {
      case Some(path) =>
        GenericSubsystemDescriptor.loadAssemblyDescriptorC(path).flatMap { source =>
          GenericSubsystemDescriptor.applyAssemblyOverrideC(
            GenericSubsystemDescriptor(
              path = path.toAbsolutePath.normalize,
              subsystemName = "configured-assembly"
            ),
            source
          ).flatMap { descriptor =>
            if (descriptor.componentBindings.nonEmpty)
              Consequence.success(Some(descriptor))
            else
              RuntimeTestDescriptor.load(configuration).flatMap {
                case Some(_) => Consequence.success(Some(descriptor))
                case None =>
                  Consequence.configurationInvalid(
                    "configured assembly descriptor requires a component name or non-empty components binding"
                  )
              }
          }
        }
      case None => Consequence.success(None)
    }

  private def _terminal_configured_component_or_assembly_descriptor_c(
    configuration: ResolvedConfiguration
  ): Consequence[Option[GenericSubsystemDescriptor]] =
    _configured_component_name(configuration) match {
      case Some(name) =>
        _with_assembly_descriptor_override_c(
          GenericSubsystemDescriptor(
            path = Paths.get(".").toAbsolutePath.normalize,
            subsystemName = name,
            componentBindings = Vector(GenericSubsystemComponentBinding(name))
          ),
          configuration
        ).map(Some(_))
      case None =>
        _assembly_descriptor_c(configuration)
    }

  private def _configured_component_name(
    configuration: ResolvedConfiguration
  ): Option[String] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.componentNameKey)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.runtimeComponentNameKey))
      .map(_.trim)
      .filter(_.nonEmpty)

  private def _or_else[A](
    lhs: Consequence[Option[A]]
  )(rhs: => Consequence[Option[A]]): Consequence[Option[A]] =
    lhs.flatMap {
      case s @ Some(_) => Consequence.success(s)
      case None => rhs
    }

  private def _component_descriptor_to_subsystem_c(
    path: Path,
    descriptor: ComponentDescriptor
  ): Consequence[GenericSubsystemDescriptor] =
    GenericSubsystemDescriptor.fromComponentDescriptor(path, descriptor)

  private def _development_component_descriptor_to_subsystem_c(
    path: Path,
    descriptor: ComponentDescriptor
  ): Consequence[GenericSubsystemDescriptor] =
    GenericSubsystemDescriptor.fromComponentDescriptor(
      path,
      descriptor,
      includeAssemblyDescriptor = false
    )

  def default(
    subsystemName: String,
    mode: Option[String],
    configuration: ResolvedConfiguration
  ): Subsystem = {
    val repositories = _repository_specs(configuration)
    ComponentRepository.resolveSubsystemDescriptor(repositories, subsystemName) match {
      case Some(descriptor) =>
        // Resolve and admit a descriptor before creating the default context;
        // the latter allocates test datastore/entity-store state.
        default(descriptor, mode, configuration)
      case None =>
        defaultWithScope(
          subsystemName,
          ScopeContext(
            kind = ScopeKind.Subsystem,
            name = subsystemName,
            parent = None,
            observabilityContext = ExecutionContext.create().observability
          ),
          mode.flatMap(RunMode.from),
          configuration,
          GlobalRuntimeContext.current
            .map(_.aliasResolver)
            .getOrElse(AliasResolver.empty)
        )
    }
  }

  def default(
    descriptor: GenericSubsystemDescriptor,
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem = {
    // Admission is deliberately before even the default scope construction:
    // ExecutionContext.create() installs test datastore/entity-store state.
    // An invalid descriptor must not cause that runtime state to exist.
    val admitteddescriptor = _admit_descriptor_or_raise(descriptor, configuration)
    defaultWithScope(
      descriptor = admitteddescriptor,
      context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = admitteddescriptor.subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      ),
      mode = mode.flatMap(RunMode.from),
      configuration = configuration
    )
  }

  def defaultWithScope(
    subsystemName: String,
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver
  ): Subsystem = {
    val repos = _repository_specs(configuration)
    ComponentRepository.resolveSubsystemDescriptor(repos, subsystemName) match {
      case Some(descriptor) =>
        return defaultWithScope(
          descriptor = descriptor,
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver
        )
      case None =>
        ()
    }
    _default_named_with_scope(subsystemName, context, mode, configuration, aliasResolver, repos)
  }

  private def _default_named_with_scope(
    subsystemname: String,
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasresolver: AliasResolver,
    repos: Vector[ComponentRepository.Specification]
  ): Subsystem = {
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
    val subsystem =
      Subsystem(
        name = subsystemname,
        scopeContext = Some(
          context.kind match {
            case ScopeKind.Runtime =>
              context.createChildScope(ScopeKind.Subsystem, subsystemname)
            case ScopeKind.Subsystem =>
              context
            case _ =>
              ScopeContext(
                kind = ScopeKind.Subsystem,
                name = subsystemname,
                parent = None,
                observabilityContext = context.observabilityContext
              )
          }
        ),
        httpdriver = Some(runtimeconfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasresolver,
        runMode = runmode
      )
    Subsystem.withStartupCleanup(subsystem) {
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("subsystem-name"))
      val repositories = repos.map(_.build(params)).toVector
      val components0 =
        ComponentRepository.discoverAssembly(repositories)
          .filter(_matches_named_subsystem(_, subsystemname))
      val builtins = DefaultSubsystemFactory.builtinComponents(subsystem)
      val components = _collapse_duplicate_components(builtins ++ components0)
      subsystem.add(components)
      _activate_tool_runtimes_or_raise(subsystem, runtimeconfig)
      subsystem
    }
  }

  private[cncf] def runtimeDefaultWithScopeC(
    subsystemName: String,
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver,
    repositoryBootstrapPolicy: Option[RepositoryBootstrapPolicy]
  ): Consequence[Subsystem] =
    repositoryBootstrapPolicy match {
      case Some(policy) =>
        _runtime_repository_specs_c(policy).flatMap { repos =>
          ComponentRepository.resolveSubsystemDescriptor(repos, subsystemName) match {
            case Some(descriptor) =>
              runtimeDefaultWithScopeC(
                descriptor = descriptor,
                context = context,
                mode = mode,
                configuration = configuration,
                aliasResolver = aliasResolver,
                repositoryBootstrapPolicy = Some(policy)
              )
            case None =>
              Consequence.success(
                _default_named_with_scope(
                  subsystemName,
                  context,
                  mode,
                  configuration,
                  aliasResolver,
                  repos
                )
              )
          }
        }
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

  private[cncf] def runtimeDefaultWithScopeC(
    descriptor: GenericSubsystemDescriptor,
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver,
    repositoryBootstrapPolicy: Option[RepositoryBootstrapPolicy]
  ): Consequence[Subsystem] =
    repositoryBootstrapPolicy match {
      case Some(policy) =>
        _runtime_repository_specs_for_descriptor_c(policy).map { repositoryspecs =>
          _default_with_scope(
            descriptor,
            context,
            mode,
            configuration,
            aliasResolver,
            repositoryspecs
          )
        }
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

  private[cncf] def runtimeAdmitDescriptorC(
    descriptor: GenericSubsystemDescriptor,
    configuration: ResolvedConfiguration,
    repositoryBootstrapPolicy: Option[RepositoryBootstrapPolicy]
  ): Consequence[GenericSubsystemDescriptor] =
    repositoryBootstrapPolicy match {
      case Some(policy) =>
        _runtime_repository_specs_for_descriptor_c(policy).flatMap { repositoryspecs =>
          _admit_descriptor_c(descriptor, configuration, repositoryspecs)
        }
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

  def defaultWithScope(
    descriptor: GenericSubsystemDescriptor,
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem =
    _default_with_scope(
      descriptor = descriptor,
      context = context,
      mode = mode,
      configuration = configuration,
      aliasresolver = aliasResolver,
      repositoryspecs = _repository_specs_for_descriptor(configuration, descriptor)
    )

  private def _default_with_scope(
    descriptor: GenericSubsystemDescriptor,
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasresolver: AliasResolver,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Subsystem = {
    val admitteddescriptor = _admit_descriptor_or_raise(descriptor, configuration, repositoryspecs)
    val admissionreport = _or_raise(SubsystemAssemblyAdmission.evaluateC(admitteddescriptor))
    val componentdescriptors = admitteddescriptor.toComponentDescriptors
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
    val subsystem =
      Subsystem(
        name = admitteddescriptor.subsystemName,
        version = admitteddescriptor.componentVersion,
        scopeContext = Some(
          context.kind match {
            case ScopeKind.Runtime =>
              context.createChildScope(ScopeKind.Subsystem, admitteddescriptor.subsystemName)
            case ScopeKind.Subsystem =>
              context
            case _ =>
              ScopeContext(
                kind = ScopeKind.Subsystem,
                name = admitteddescriptor.subsystemName,
                parent = None,
                observabilityContext = context.observabilityContext
              )
          }
        ),
        httpdriver = Some(runtimeconfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasresolver,
        runMode = runmode
      ).withDescriptor(admitteddescriptor)
        .withAssemblyAdmissionReport(admissionreport)
    Subsystem.withStartupCleanup(subsystem) {
      val params = ComponentCreate(
        subsystem,
        ComponentOrigin.Repository("subsystem-descriptor"),
        componentdescriptors
      )
      val developmentclaims = ComponentRepository.developmentComponentClaims(repositoryspecs)
      val repositories =
        repositoryspecs.zipWithIndex.flatMap { case (spec, index) =>
        val activedescriptors =
          ComponentRepository.descriptorsForSpecification(
            spec,
            repositoryspecs.take(index),
            componentdescriptors,
            developmentclaims
          )
        admitteddescriptor.componentBindings.zip(componentdescriptors).collect {
          case (binding, componentdescriptor) if activedescriptors.contains(componentdescriptor) =>
            spec.build(
              params
                .withComponentDescriptors(Vector(componentdescriptor))
                .withInstanceMetadata(binding.instanceMetadata)
            )
        }
        }.toVector
      val components0 = _or_raise(
        ComponentRepository.discoverAssemblyC(repositories).flatMap { discovered =>
        val selected = discovered.filter(component =>
          admitteddescriptor.componentBindings.exists(binding => _matches_descriptor_component(component, binding))
        )
        materializeComponentInstancesC(selected, admitteddescriptor, params)
        }
      )
      val builtins = _builtin_components(subsystem, admitteddescriptor)
      given ExecutionContext = ExecutionContext.create()
      val spibindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(admitteddescriptor) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
      }
      val resolution = SpiResolver.resolveAssemblyOrRaise(_collapse_duplicate_components(builtins ++ components0), spibindings)
      subsystem.add(resolution.components)
      subsystem.withComponentApiResolver(resolution.componentApiResolver)
      _activate_tool_runtimes_or_raise(subsystem, runtimeconfig)
      subsystem
    }
  }

  private def _activate_tool_runtimes_or_raise(
    subsystem: Subsystem,
    runtimeconfig: RuntimeConfig
  ): Unit =
    {
      given ExecutionContext = ExecutionContext.create()
      runtimeconfig.mcpClientPolicyPath.foreach { path =>
        subsystem.activateCodexMcpClientRuntimeC(path) match {
          case Consequence.Success(_) => ()
          case Consequence.Failure(conclusion) =>
            throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
        }
      }
      runtimeconfig.operationToolPolicyPath.foreach { path =>
        subsystem.activateOperationToolRuntimeC(path) match {
          case Consequence.Success(_) => ()
          case Consequence.Failure(conclusion) =>
            throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
        }
      }
    }

  private def _repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[ComponentRepository.Specification] = {
    val values =
      ConfigurationAccess
        .getString(configuration, RuntimeConfig.repositoryDirKey)
    values match {
      case Some(value) =>
        _parse_repository_specs(value)
          .getOrElse(_default_repository_specs)
      case None =>
        _default_repository_specs
    }
  }

  private def _repository_specs_for_descriptor(
    configuration: ResolvedConfiguration,
    descriptor: GenericSubsystemDescriptor
  ): Vector[ComponentRepository.Specification] = {
    val developmentrepositories = _component_dev_repository_paths(configuration)
    val base =
      if (developmentrepositories.nonEmpty)
        developmentrepositories.map(ComponentRepository.ComponentDevDirRepository.Specification.apply)
      else
        componentDevDirPath(configuration)
          .filter(path => _descriptor_components_are_from_dev_dir(path, descriptor))
          .map(path => Vector(ComponentRepository.ComponentDevDirRepository.Specification(path)))
          .getOrElse(_repository_specs(configuration))
    _merge_repository_specs(_active_component_repository_specs(configuration), base)
  }

  private def _runtime_repository_specs_for_descriptor_c(
    policy: RepositoryBootstrapPolicy
  ): Consequence[Vector[ComponentRepository.Specification]] =
    _runtime_repository_specs_c(policy)

  private def _runtime_repository_specs_c(
    policy: RepositoryBootstrapPolicy
  ): Consequence[Vector[ComponentRepository.Specification]] =
    for {
      active <- _runtime_policy_repository_specs_c(policy, active = true)
      search <- _runtime_search_repository_specs_c(policy)
    } yield _merge_repository_specs(active, search)

  private def _runtime_search_repository_specs_c(
    policy: RepositoryBootstrapPolicy
  ): Consequence[Vector[ComponentRepository.Specification]] =
    _runtime_policy_repository_specs_c(policy, active = false).map { repositories =>
      if (repositories.nonEmpty) repositories else _default_repository_specs
    }

  private def _runtime_policy_repository_specs_c(
    policy: RepositoryBootstrapPolicy,
    active: Boolean
  ): Consequence[Vector[ComponentRepository.Specification]] = {
    val extracted = ComponentRepositorySpace.extractAdmittedRepositoryArgs(policy, Array.empty[String])
    val values = if (active) extracted.active else extracted.search
    ComponentRepositorySpace.resolveSpecifications(
      values,
      policy.baseDirectory,
      noDefault = true
    ) match {
      case Right(specifications) =>
        Consequence.success(specifications)
      case Left(message) =>
        Consequence.configurationInvalid(
          s"runtime repository bootstrap policy is invalid: $message"
        )
    }
  }

  private def _runtime_descriptor_path(
    configuration: ResolvedConfiguration,
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.subsystemDescriptorKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.descriptor"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.subsystemFileKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.file"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .orElse(_runtime_subsystem_dev_dir_path(policy))
      .orElse(_runtime_subsystem_sar_dir_path(policy))

  private def _runtime_component_archive_path(
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    _runtime_path(policy, policy.componentFiles)

  private def _runtime_component_car_dir_path(
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    _runtime_path(policy, policy.componentCarDirs)

  private def _runtime_component_dev_dir_path(
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    _runtime_path(policy, policy.componentDevDirs)

  private def _runtime_subsystem_dev_dir_path(
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    _runtime_path(policy, policy.subsystemDevDirs)

  private def _runtime_subsystem_sar_dir_path(
    policy: RepositoryBootstrapPolicy
  ): Option[Path] =
    _runtime_path(policy, policy.subsystemSarDirs)

  private def _runtime_path(
    policy: RepositoryBootstrapPolicy,
    values: Vector[String]
  ): Option[Path] =
    values.iterator
      .map(_.trim)
      .find(_.nonEmpty)
      .map(Paths.get(_))
      .map { path =>
        if (path.isAbsolute) path.normalize
        else policy.baseDirectory.resolve(path).normalize
      }

  private def _component_dev_repository_paths(
    configuration: ResolvedConfiguration
  ): Vector[Path] =
    Vector(
      RuntimeConfig.repositoryComponentDevDirKey,
      "cncf.repository.component.dev.dir"
    ).flatMap(ConfigurationAccess.getString(configuration, _).toVector)
      .flatMap(_.split(",").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { value =>
        if (value.startsWith("component-dev-dir:")) value.stripPrefix("component-dev-dir:")
        else if (value.contains(":"))
          throw new IllegalArgumentException(
            "component development directory configuration must be a plain path or component-dev-dir:path; use component-dir/component-file settings for packaged CARs"
          )
        else value
      }
      .map(Paths.get(_))
      .distinct

  private def _active_component_repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[ComponentRepository.Specification] =
    Vector(
      componentArchivePath(configuration).map(ComponentRepository.ComponentFileRepository.Specification.apply),
      componentCarDirPath(configuration).map(ComponentRepository.ComponentDirRepository.Specification.apply),
      componentDevDirPath(configuration).map(ComponentRepository.ComponentDevDirRepository.Specification.apply)
    ).flatten

  private def _admission_repository_specs(
    configuration: ResolvedConfiguration,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Vector[ComponentRepository.Specification] =
    // Static descriptor resolution is side-effect free. Its search domain must
    // exactly match the repositories that the runtime will subsequently build.
    repositoryspecs

  private def _admit_descriptor_or_raise(
    descriptor: GenericSubsystemDescriptor,
    configuration: ResolvedConfiguration,
    repositoryspecs: Vector[ComponentRepository.Specification] = Vector.empty
  ): GenericSubsystemDescriptor =
    _or_raise(_admit_descriptor_c(descriptor, configuration, repositoryspecs))

  private def _admit_descriptor_c(
    descriptor: GenericSubsystemDescriptor,
    configuration: ResolvedConfiguration,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Consequence[GenericSubsystemDescriptor] = {
    val specs =
      if (repositoryspecs.nonEmpty) repositoryspecs
      else _repository_specs_for_descriptor(configuration, descriptor)
    _with_primary_component_defaults_c(descriptor, specs).flatMap { effective =>
      SubsystemAssemblyAdmission.resolveC(
        effective,
        _admission_repository_specs(configuration, specs)
      )
    }
  }

  private def _with_primary_component_defaults_c(
    descriptor: GenericSubsystemDescriptor,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Consequence[GenericSubsystemDescriptor] =
    descriptor.componentBindings.headOption
      .map { primary =>
        repositoryspecs.foldLeft(Consequence.success(Option.empty[GenericSubsystemDescriptor])) { (z, repository) =>
          z.flatMap {
            case some @ Some(_) => Consequence.success(some)
            case None => repository.resolveComponentSubsystemDefaultsC(primary.componentName, primary.version)
          }
        }.flatMap {
          defaults =>
            val base = defaults
              .map(GenericSubsystemDescriptor.mergeComponentDefaults(_, descriptor))
              .getOrElse(descriptor)
            descriptor.assemblyDescriptor
              .filterNot(source => defaults.exists(default => source.path.exists(
                _.toAbsolutePath.normalize == default.path.toAbsolutePath.normalize
              )))
              .map(GenericSubsystemDescriptor.applyAssemblyOverrideC(base, _))
              .getOrElse(Consequence.success(base))
        }
      }
      .getOrElse(Consequence.success(descriptor))

  private def _merge_repository_specs(
    primary: Vector[ComponentRepository.Specification],
    secondary: Vector[ComponentRepository.Specification]
  ): Vector[ComponentRepository.Specification] =
    (primary ++ secondary).foldLeft(Vector.empty[ComponentRepository.Specification]) { (z, spec) =>
      if (z.contains(spec)) z else z :+ spec
    }

  private def _descriptor_components_are_from_dev_dir(
    path: Path,
    descriptor: GenericSubsystemDescriptor
  ): Boolean = {
    val bindings = descriptor.componentBindings.map(_.componentName).toSet
    val descriptors = ComponentRepository.ComponentDevDirRepository.devComponentDescriptors(path)
    val names = descriptors.flatMap(x => x.componentName.orElse(x.name)).toSet
    bindings.nonEmpty && names.nonEmpty && bindings.subsetOf(names)
  }

  private def _parse_repository_specs(
    value: String
  ): Option[Vector[ComponentRepository.Specification]] =
    ComponentRepository.parseSpecs(_normalize_repository_spec_value(value), Paths.get("").toAbsolutePath.normalize).toOption

  private def _default_repository_specs: Vector[ComponentRepository.Specification] =
    Vector(
      ComponentRepository.defaultLocalComponentRepositoryDir(),
      ComponentRepository.defaultLocalSubsystemRepositoryDir(),
      ComponentRepository.defaultStandardRepositoryDir()
    ).map(ComponentRepository.ComponentDirRepository.Specification.apply)

  private def _with_assembly_descriptor_override_c(
    descriptor: GenericSubsystemDescriptor,
    configuration: ResolvedConfiguration
  ): Consequence[GenericSubsystemDescriptor] = {
    val assemblydescriptorc = _assembly_descriptor_path(configuration) match {
      case Some(path) =>
        GenericSubsystemDescriptor.loadAssemblyDescriptorC(path)
          .flatMap(GenericSubsystemDescriptor.applyAssemblyOverrideC(descriptor, _))
      case None =>
        Consequence.success(descriptor)
    }
    assemblydescriptorc.flatMap { effectiveassemblydescriptor =>
      RuntimeTestDescriptor.load(configuration).flatMap {
        case Some(testdescriptor) =>
          testdescriptor.assembly match {
            case Some(source) => GenericSubsystemDescriptor.applyAssemblyOverrideC(effectiveassemblydescriptor, source)
            case None => Consequence.success(effectiveassemblydescriptor)
          }
        case None =>
          Consequence.success(effectiveassemblydescriptor)
      }
    }
  }

  private def _assembly_descriptor_path(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.assemblyDescriptorKey)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  private def _normalize_repository_spec_value(
    value: String
  ): String =
    value
      .split(",")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { spec =>
        if (spec.contains(":")) spec else s"component-dir:${spec}"
      }
      .mkString(",")

  private def _matches_named_subsystem(
    component: Component,
    subsystemname: String
  ): Boolean =
    component.artifactMetadata.flatMap(_.subsystem).contains(subsystemname)

  private def _matches_descriptor_component(
    component: Component,
    binding: GenericSubsystemComponentBinding
  ): Boolean = {
    binding.componentId match {
      case Some(id) =>
        component.core.componentId == id &&
          component.artifactMetadata.flatMap(_.componentId).contains(id)
      case None =>
        val bindingpresentations = Vector(
          binding.componentName,
          _runtime_component_name(binding.componentName),
          _legacy_runtime_component_name(binding.componentName)
        ).distinct
        val presentations = Vector(
          component.name,
          component.core.componentId.name,
          component.core.componentId.localId.value()
        ).distinct
        presentations.exists(presentation =>
          bindingpresentations.exists(NamingConventions.equivalentByNormalized(presentation, _))
        ) ||
          component.artifactMetadata.exists(metadata =>
            metadata.component.exists(componentname =>
              bindingpresentations.exists(NamingConventions.equivalentByNormalized(componentname, _))
            ) || bindingpresentations.exists(NamingConventions.equivalentByNormalized(metadata.name, _))
          )
    }
  }

  private[cncf] def materializeComponentInstances(
    discovered: Seq[Component],
    descriptor: GenericSubsystemDescriptor,
    params: ComponentCreate
  ): Vector[Component] =
    _or_raise(materializeComponentInstancesC(discovered, descriptor, params))

  private[cncf] def materializeComponentInstancesC(
    discovered: Seq[Component],
    descriptor: GenericSubsystemDescriptor,
    params: ComponentCreate
  ): Consequence[Vector[Component]] = {
    val participants = descriptor.componentBindings.flatMap { binding =>
      val candidates = discovered.filter(_matches_descriptor_component(_, binding)).toVector
      val exact = candidates.filter { candidate =>
        candidate.instanceMetadata.contains(_binding_instance_metadata(binding, candidate))
      }
      binding.componentId match {
        case Some(componentid) =>
          candidates match {
            case Vector() =>
              Vector(Consequence.componentInvalid(
                s"canonical component binding has no exact Core/artifact identity match: ${componentid.name}"
              ))
            case Vector(candidate) =>
              exact.headOption.map(x => Vector(Consequence.success(x))).getOrElse(
                Vector(_create_component_participant_c(candidate, binding, params))
              )
            case _ =>
              Vector(Consequence.componentInvalid(
                s"canonical component binding has multiple exact Core/artifact identity matches: ${componentid.name}"
              ))
          }
        case None =>
          if (exact.nonEmpty)
            exact.map(Consequence.success)
          else
            candidates.map(_create_component_participant_c(_, binding, params))
      }
    }.toVector
    _sequence(participants)
  }

  private def _create_component_participant_c(
    prototype: Component,
    binding: GenericSubsystemComponentBinding,
    params: ComponentCreate
  ): Consequence[Component] =
    prototype.factoryOption match {
      case Some(factory) =>
        val instanceparams = params
          .withOrigin(prototype.origin)
          .withComponentDescriptors(prototype.componentDescriptors)
          .withInstanceMetadata(_binding_instance_metadata(binding, prototype))
        val componentc =
          if (prototype.isComponentletParticipant) factory.createComponentletC(instanceparams)
          else factory.createPrimaryC(instanceparams)
        componentc.map { component =>
          prototype.artifactMetadata.foreach(component.withArtifactMetadata)
          component.withCollaboratorClasspath(prototype.collaboratorClasspath)
        }
      case None =>
        if (binding.hasInstanceDeclaration) {
          Consequence.componentInvalid(
            s"component factory is required for named instance: ${binding.componentName}/${binding.instanceName}"
          )
        } else {
          Consequence.success(prototype)
        }
    }

  private def _binding_instance_metadata(
    binding: GenericSubsystemComponentBinding,
    prototype: Component
  ): ComponentInstanceMetadata =
    binding.componentId match {
      case Some(_) => binding.instanceMetadata
      case None => binding.instanceMetadata.copy(componentId = Some(prototype.core.componentId))
    }

  private def _sequence[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (acc, value) =>
      acc.flatMap(xs => value.map(xs :+ _))
    }

  private def _or_raise[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
    }

  private def _runtime_component_name(
    descriptorcomponentname: String
  ): String =
    descriptorcomponentname.trim

  private def _legacy_runtime_component_name(
    descriptorcomponentname: String
  ): String = {
    val normalized = descriptorcomponentname.trim
    val stripped =
      if (normalized.startsWith("textus-")) normalized.stripPrefix("textus-")
      else if (normalized.startsWith("textus_")) normalized.stripPrefix("textus_")
      else normalized
    if (stripped.exists(ch => ch == '-' || ch == '_')) {
      stripped
        .split("[-_]")
        .toVector
        .filter(_.nonEmpty)
        .map(_.toLowerCase.capitalize)
        .mkString
    } else {
      stripped
    }
  }

  private def _collapse_duplicate_components(
    components: Seq[Component]
  ): Vector[Component] = {
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, Component]
    components.foreach { component =>
      val key = component.instanceMetadata
        .map(metadata => s"instance:${metadata.componentName}/${metadata.instance}")
        .getOrElse(s"component:${component.name}")
      seen.get(key) match {
        case Some(existing) =>
          val selection = AssemblyReport.selectPreferred(existing, component)
          seen.update(key, selection.selected)
          if (!AssemblyReport.isSameAssemblySource(existing, component)) {
            GlobalRuntimeContext.current.foreach(
              _.assemblyReport.addWarning(
                AssemblyReport.duplicateComponentWarning(
                  componentName = component.name,
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
    seen.values.toVector
  }

  private def _builtin_components(
    subsystem: Subsystem,
    descriptor: GenericSubsystemDescriptor
  ): Vector[Component] = {
    val excluded = descriptor.builtin.map(_.exclude.map(_.trim.toLowerCase).toSet).getOrElse(Set.empty)
    DefaultSubsystemFactory.builtinComponents(subsystem).filterNot(c => excluded.contains(c.name.trim.toLowerCase))
  }
}
