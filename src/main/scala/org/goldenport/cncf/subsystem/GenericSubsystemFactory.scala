package org.goldenport.cncf.subsystem

import java.nio.file.{Path, Paths}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentDescriptorLoader, ComponentOrigin}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Apr.  7, 2026
 *  version Apr. 23, 2026
 *  version Apr. 25, 2026
 * @version May.  1, 2026
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
    descriptorPath(configuration).flatMap { path =>
      GenericSubsystemDescriptor.load(path).toOption.map(_with_assembly_descriptor_override(_, configuration))
    }

  def resolveDescriptor(
    configuration: ResolvedConfiguration
  ): Option[GenericSubsystemDescriptor] =
    loadDescriptor(configuration).orElse {
      subsystemName(configuration).flatMap { name =>
        ComponentRepository.resolveSubsystemDescriptor(_repository_specs(configuration), name)
          .map(_with_assembly_descriptor_override(_, configuration))
      }
    }.orElse {
      componentArchivePath(configuration).flatMap { path =>
        GenericSubsystemDescriptor.loadComponentArchive(path).toOption
          .map(_with_assembly_descriptor_override(_, configuration))
      }
    }.orElse {
      componentCarDirPath(configuration).flatMap { path =>
        ComponentDescriptorLoader.loadArchive(path).toOption
          .map(_component_descriptor_to_subsystem(path, _))
          .map(_with_assembly_descriptor_override(_, configuration))
      }
    }.orElse {
      componentDevDirPath(configuration).map { path =>
        _component_descriptor_to_subsystem(
          path,
          _load_dev_component_descriptor(path)
            .getOrElse(_fallback_component_descriptor(path))
        )
      }.map(_with_assembly_descriptor_override(_, configuration))
    }.orElse {
      RuntimeConfig
        .getString(configuration, RuntimeConfig.ComponentNameKey)
        .orElse(RuntimeConfig.getString(configuration, RuntimeConfig.RuntimeComponentNameKey))
        .map(_.trim)
        .filter(_.nonEmpty)
        .map { name =>
          GenericSubsystemDescriptor(
            path = Paths.get(".").toAbsolutePath.normalize,
            subsystemName = name,
            componentBindings = Vector(GenericSubsystemComponentBinding(name))
          )
        }
    }

  private def _load_dev_component_descriptor(
    path: Path
  ): Option[ComponentDescriptor] =
    Vector(
      path.resolve("src").resolve("main").resolve("car"),
      path.resolve("car.d")
    ).iterator.flatMap { dir =>
      ComponentDescriptorLoader.load(dir).toOption.toVector.flatten
    }.toSeq.headOption

  private def _fallback_component_descriptor(
    path: Path
  ): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(path.getFileName.toString),
      version = Some("0.1.0"),
      componentName = Some(path.getFileName.toString)
    )

  private def _component_descriptor_to_subsystem(
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
    val runtimeConfig = RuntimeConfig.from(configuration)
    val runMode = mode.getOrElse(runtimeConfig.mode)
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
        httpdriver = Some(runtimeConfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasResolver,
        runMode = runMode
      )
    val params = ComponentCreate(subsystem, ComponentOrigin.Repository("subsystem-name"))
    val components0 =
      repos.flatMap(_.build(params).discover())
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
    val runtimeConfig = RuntimeConfig.from(configuration)
    val runMode = mode.getOrElse(runtimeConfig.mode)
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
        httpdriver = Some(runtimeConfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasResolver,
        runMode = runMode
      )
    val params = ComponentCreate(
      subsystem,
      ComponentOrigin.Repository("subsystem-descriptor"),
      descriptor.toComponentDescriptors
    )
    val components0 =
      _repository_specs(configuration).flatMap(_.build(params).discover())
        .filter(component => descriptor.componentBindings.exists(binding => _matches_descriptor_component(component, binding.componentName)))
    val builtins = _builtin_components(subsystem, descriptor)
    val components = _collapse_duplicate_components(builtins ++ components0)
    subsystem.add(components)
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
          .getOrElse(Vector(_default_repository_spec))
      case None =>
        Vector(_default_repository_spec)
    }
  }

  private def _parse_repository_specs(
    value: String
  ): Option[Vector[ComponentRepository.Specification]] =
    ComponentRepository.parseSpecs(_normalize_repository_spec_value(value), Paths.get("").toAbsolutePath.normalize).toOption

  private def _default_repository_spec: ComponentRepository.Specification =
    ComponentRepository.ComponentDirRepository.Specification(ComponentRepository.defaultStandardRepositoryDir())

  private def _with_assembly_descriptor_override(
    descriptor: GenericSubsystemDescriptor,
    configuration: ResolvedConfiguration
  ): GenericSubsystemDescriptor =
    _assembly_descriptor_path(configuration)
      .flatMap(GenericSubsystemDescriptor.loadAssemblyDescriptor)
      .map(record => descriptor.copy(assemblyDescriptor = Some(record)))
      .getOrElse(descriptor)

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
    subsystemName: String
  ): Boolean =
    component.artifactMetadata.flatMap(_.subsystem).contains(subsystemName)

  private def _matches_descriptor_component(
    component: Component,
    descriptorComponentName: String
  ): Boolean = {
    val runtimeName = _runtime_component_name(descriptorComponentName)
    val legacyRuntimeName = _legacy_runtime_component_name(descriptorComponentName)
    component.name == runtimeName ||
      component.name == legacyRuntimeName ||
      component.artifactMetadata.exists(metadata =>
        metadata.component.contains(descriptorComponentName) ||
          metadata.name == descriptorComponentName
      )
  }

  private def _runtime_component_name(
    descriptorComponentName: String
  ): String =
    descriptorComponentName.trim

  private def _legacy_runtime_component_name(
    descriptorComponentName: String
  ): String = {
    val normalized = descriptorComponentName.trim
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
      seen.get(component.name) match {
        case Some(existing) =>
          val selection = AssemblyReport.selectPreferred(existing, component)
          seen.update(component.name, selection.selected)
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
        case None =>
          seen += component.name -> component
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
