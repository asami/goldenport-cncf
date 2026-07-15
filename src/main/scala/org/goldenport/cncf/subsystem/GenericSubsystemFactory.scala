package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path, Paths}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentDescriptorLoader, ComponentOrigin}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig, RuntimeTestDescriptor}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.Consequence
import org.goldenport.cncf.spi.SpiResolver

/*
 * @since   Apr.  7, 2026
 *  version Apr. 23, 2026
 *  version Apr. 25, 2026
 *  version May. 18, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object GenericSubsystemFactory {
  def subsystemName(
    configuration: ResolvedConfiguration
  ): Option[String] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.SubsystemNameKey)
      .map(_.trim)
      .filter(_.nonEmpty)

  def descriptorPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.SubsystemDescriptorKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.descriptor"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemFileKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.file"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemDevDirKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.dev.dir"))
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.SubsystemSarDirKey))
      .orElse(ConfigurationAccess.getString(configuration, "cncf.subsystem.sar.dir"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentArchivePath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.ComponentFileKey)
      .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeComponentFileKey))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentCarDirPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.ComponentCarDirKey)
      .orElse(ConfigurationAccess.getString(configuration, "cncf.component.car.dir"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def componentDevDirPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    RuntimeConfig
      .getString(configuration, RuntimeConfig.ComponentDevDirKey)
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
                  _component_descriptor_to_subsystem_c(
                    _component_dev_car_root(path),
                    _load_dev_component_descriptor(path)
                      .getOrElse(_fallback_component_descriptor(path))
                  ).flatMap(d => _with_assembly_descriptor_override_c(d, configuration).map(Some(_)))
                case None => Consequence.success(None)
              }
            ) {
              RuntimeConfig
                .getString(configuration, RuntimeConfig.ComponentNameKey)
                .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeComponentNameKey))
                .map(_.trim)
                .filter(_.nonEmpty)
                .map { name =>
                  _with_assembly_descriptor_override_c(
                    GenericSubsystemDescriptor(
                      path = Paths.get(".").toAbsolutePath.normalize,
                      subsystemName = name,
                      componentBindings = Vector(GenericSubsystemComponentBinding(name))
                    ),
                    configuration
                  ).map(Some(_))
                }
                .getOrElse(Consequence.success(None))
            }
          }
        }
      }
    }

  private def _or_else[A](
    lhs: Consequence[Option[A]]
  )(rhs: => Consequence[Option[A]]): Consequence[Option[A]] =
    lhs.flatMap {
      case s @ Some(_) => Consequence.success(s)
      case None => rhs
    }

  private def _load_dev_component_descriptor(
    path: Path
  ): Option[ComponentDescriptor] =
    Vector(
      path.resolve("src").resolve("main").resolve("car"),
      path.resolve("car.d")
    ).iterator.flatMap { dir =>
      ComponentDescriptorLoader.load(dir).toOption.toVector.flatten
    }.toSeq.headOption.orElse(
      ComponentRepository.ComponentDevDirRepository.inferComponentDescriptors(path).headOption
    )

  private def _fallback_component_descriptor(
    path: Path
  ): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(path.getFileName.toString),
      version = Some("0.1.0"),
      componentName = Some(path.getFileName.toString)
    )

  private def _component_dev_car_root(
    path: Path
  ): Path =
    Vector(
      path.resolve("src").resolve("main").resolve("car"),
      path.resolve("car.d")
    ).find(Files.isDirectory(_)).getOrElse(path)

  private def _component_descriptor_to_subsystem(
    path: Path,
    descriptor: ComponentDescriptor
  ): GenericSubsystemDescriptor =
    _component_descriptor_to_subsystem_c(path, descriptor).TAKE

  private def _component_descriptor_to_subsystem_c(
    path: Path,
    descriptor: ComponentDescriptor
  ): Consequence[GenericSubsystemDescriptor] =
    GenericSubsystemDescriptor.fromComponentDescriptor(path, descriptor)

  private def _legacy_component_descriptor_to_subsystem(
    path: Path,
    descriptor: ComponentDescriptor
  ): GenericSubsystemDescriptor = {
    val componentname =
      descriptor.componentName.orElse(descriptor.name).getOrElse(path.getFileName.toString)
    GenericSubsystemDescriptor(
      path = path,
      subsystemName = descriptor.subsystemName.getOrElse(componentname),
      version = descriptor.version,
      componentBindings = Vector(GenericSubsystemComponentBinding(
        componentName = componentname,
        version = descriptor.version,
        coordinate = None,
        extensionBindings = descriptor.extensionBindings
      )),
      extensions = descriptor.extensions,
      config = descriptor.config
    )
  }

  def default(
    subsystemName: String,
    mode: Option[String],
    configuration: ResolvedConfiguration
  ): Subsystem =
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

  def default(
    descriptor: GenericSubsystemDescriptor,
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem =
    defaultWithScope(
      descriptor = descriptor,
      context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = descriptor.subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      ),
      mode = mode.flatMap(RunMode.from),
      configuration = configuration
    )

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
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
    val subsystem =
      Subsystem(
        name = subsystemName,
        scopeContext = Some(
          context.kind match {
            case ScopeKind.Runtime =>
              context.createChildScope(ScopeKind.Subsystem, subsystemName)
            case ScopeKind.Subsystem =>
              context
            case _ =>
              ScopeContext(
                kind = ScopeKind.Subsystem,
                name = subsystemName,
                parent = None,
                observabilityContext = context.observabilityContext
              )
          }
        ),
        httpdriver = Some(runtimeconfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasResolver,
        runMode = runmode
      )
    val params = ComponentCreate(subsystem, ComponentOrigin.Repository("subsystem-name"))
    val repositories = repos.map(_.build(params)).toVector
    val components0 =
      ComponentRepository.discoverAssembly(repositories)
        .filter(_matches_named_subsystem(_, subsystemName))
    val builtins = DefaultSubsystemFactory.builtinComponents(subsystem)
    val components = _collapse_duplicate_components(builtins ++ components0)
    subsystem.add(components)
    subsystem
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
  ): Subsystem = {
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
    val subsystem =
      Subsystem(
        name = descriptor.subsystemName,
        version = descriptor.componentVersion,
        scopeContext = Some(
          context.kind match {
            case ScopeKind.Runtime =>
              context.createChildScope(ScopeKind.Subsystem, descriptor.subsystemName)
            case ScopeKind.Subsystem =>
              context
            case _ =>
              ScopeContext(
                kind = ScopeKind.Subsystem,
                name = descriptor.subsystemName,
                parent = None,
                observabilityContext = context.observabilityContext
              )
          }
        ),
        httpdriver = Some(runtimeconfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasResolver,
        runMode = runmode
      )
    val params = ComponentCreate(
      subsystem,
      ComponentOrigin.Repository("subsystem-descriptor"),
      descriptor.toComponentDescriptors
    )
    val repositoryspecs = _repository_specs_for_descriptor(configuration, descriptor)
    val repositories =
      repositoryspecs.zipWithIndex.map { case (spec, index) =>
        val activedescriptors =
          ComponentRepository.descriptorsForSpecification(spec, repositoryspecs.take(index), descriptor.toComponentDescriptors)
        spec.build(params.withComponentDescriptors(activedescriptors))
      }.toVector
    val discoveredcomponents =
      ComponentRepository.discoverAssembly(repositories)
        .filter(component => descriptor.componentBindings.exists(binding => _matches_descriptor_component(component, binding.componentName)))
    val components0 = materializeComponentInstances(discoveredcomponents, descriptor, params)
    val builtins = _builtin_components(subsystem, descriptor)
    given ExecutionContext = ExecutionContext.create()
    val spibindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.display)
    }
    val resolution = SpiResolver.resolveAssemblyOrRaise(_collapse_duplicate_components(builtins ++ components0), spibindings)
    subsystem.add(resolution.components)
    subsystem.withComponentApiResolver(resolution.componentApiResolver)
    subsystem.withDescriptor(descriptor)
  }

  private def _repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[ComponentRepository.Specification] = {
    val values =
      ConfigurationAccess
        .getString(configuration, RuntimeConfig.RepositoryDirKey)
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
    val base = componentDevDirPath(configuration)
      .filter(path => _descriptor_components_are_from_dev_dir(path, descriptor))
      .map(path => Vector(ComponentRepository.ComponentDevDirRepository.Specification(path)))
      .getOrElse(_repository_specs(configuration))
    _merge_repository_specs(_active_component_repository_specs(configuration), base)
  }

  private def _active_component_repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[ComponentRepository.Specification] =
    Vector(
      componentArchivePath(configuration).map(ComponentRepository.ComponentFileRepository.Specification.apply),
      componentCarDirPath(configuration).map(ComponentRepository.ComponentDirRepository.Specification.apply),
      componentDevDirPath(configuration).map(ComponentRepository.ComponentDevDirRepository.Specification.apply)
    ).flatten

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
    val inferred = ComponentRepository.ComponentDevDirRepository.inferComponentNames(path).toSet
    bindings.nonEmpty && inferred.nonEmpty && bindings.subsetOf(inferred)
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
    val assemblydescriptor = _assembly_descriptor_path(configuration)
      .flatMap(GenericSubsystemDescriptor.loadAssemblyDescriptor)
    val assemblydescriptorc = assemblydescriptor match {
      case Some(record) => GenericSubsystemDescriptor.applyAssemblyOverrideC(descriptor, record)
      case None => Consequence.success(descriptor)
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
      .getString(configuration, RuntimeConfig.AssemblyDescriptorKey)
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
    descriptorcomponentname: String
  ): Boolean = {
    val runtimename = _runtime_component_name(descriptorcomponentname)
    val legacyruntimename = _legacy_runtime_component_name(descriptorcomponentname)
    component.name == runtimename ||
      component.name == legacyruntimename ||
      component.artifactMetadata.exists(metadata =>
        metadata.component.contains(descriptorcomponentname) ||
          metadata.name == descriptorcomponentname
      )
  }

  private[cncf] def materializeComponentInstances(
    discovered: Seq[Component],
    descriptor: GenericSubsystemDescriptor,
    params: ComponentCreate
  ): Vector[Component] = {
    val counts = descriptor.componentBindings
      .groupBy(binding => _runtime_component_name(binding.componentName))
      .view
      .mapValues(_.size)
      .toMap
    descriptor.componentBindings.flatMap { binding =>
      val prototypes = discovered.filter(_matches_descriptor_component(_, binding.componentName))
      prototypes.map { prototype =>
        val requiresmaterialization =
          counts.getOrElse(_runtime_component_name(binding.componentName), 0) > 1 ||
            binding.hasInstanceDeclaration
        if (requiresmaterialization) {
          _create_component_participant(prototype, binding, params)
        } else {
          prototype
        }
      }
    }
  }

  private def _create_component_participant(
    prototype: Component,
    binding: GenericSubsystemComponentBinding,
    params: ComponentCreate
  ): Component =
    prototype.factoryOption match {
      case Some(factory) =>
        val instanceparams = params
          .withOrigin(prototype.origin)
          .withInstanceMetadata(binding.instanceMetadata)
        val component =
          if (prototype.isComponentletParticipant) factory.createComponentlet(instanceparams)
          else factory.createPrimary(instanceparams)
        prototype.artifactMetadata.foreach(component.withArtifactMetadata)
        component.withCollaboratorClasspath(prototype.collaboratorClasspath)
      case None =>
        throw new IllegalStateException(
          s"component factory is required for named instance: ${binding.componentName}/${binding.instanceName}"
        )
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
