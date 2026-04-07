package org.goldenport.cncf.subsystem

import java.nio.file.{Path, Paths}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Apr.  7, 2026
 * @version Apr.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object GenericSubsystemFactory {
  def subsystemName(
    configuration: ResolvedConfiguration
  ): Option[String] =
    ConfigurationAccess
      .getString(configuration, RuntimeConfig.SubsystemNameKey)
      .map(_.trim)
      .filter(_.nonEmpty)

  def descriptorPath(
    configuration: ResolvedConfiguration
  ): Option[Path] =
    ConfigurationAccess
      .getString(configuration, RuntimeConfig.SubsystemDescriptorKey)
      .orElse(ConfigurationAccess.getString(configuration, RuntimeConfig.SubsystemFileKey))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Paths.get(_))

  def loadDescriptor(
    configuration: ResolvedConfiguration
  ): Option[GenericSubsystemDescriptor] =
    descriptorPath(configuration).flatMap { path =>
      GenericSubsystemDescriptor.load(path).toOption
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
    val components =
      _repository_specs(configuration).flatMap(_.build(params).discover())
        .filter(_matches_named_subsystem(_, subsystemName))
        .distinctBy(_.name)
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
    val components =
      _repository_specs(configuration).flatMap(_.build(params).discover())
        .filter(component => descriptor.componentBindings.exists(binding => _matches_descriptor_component(component, binding.componentName)))
        .distinctBy(_.name)
    subsystem.add(components)
    subsystem
  }

  private def _repository_specs(
    configuration: ResolvedConfiguration
  ): Vector[ComponentRepository.Specification] = {
    val values =
      ConfigurationAccess
        .getString(configuration, RuntimeConfig.ComponentRepositoryKey)
    values match {
      case Some(value) =>
        _parse_repository_specs(value)
          .getOrElse(Vector.empty)
      case None =>
        Vector.empty
    }
  }

  private def _parse_repository_specs(
    value: String
  ): Option[Vector[ComponentRepository.Specification]] =
    ComponentRepository.parseSpecs(_normalize_repository_spec_value(value), Paths.get("").toAbsolutePath.normalize).toOption

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
    component.name == runtimeName ||
      component.artifactMetadata.flatMap(_.component).contains(descriptorComponentName)
  }

  private def _runtime_component_name(
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
}
