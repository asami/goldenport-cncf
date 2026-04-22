package org.goldenport.cncf.subsystem

import java.nio.file.Paths
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Mar. 26, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
object TextusIdentitySubsystemFactory {
  private val _descriptor_path = TextusIdentitySubsystemDescriptor.DefaultPath

  private lazy val _descriptor: TextusIdentitySubsystemDescriptor =
    TextusIdentitySubsystemDescriptor.load(_descriptor_path).toOption
      .getOrElse(TextusIdentitySubsystemDescriptor.default(_descriptor_path))

  def subsystemName: String =
    _descriptor.subsystemName

  def default(
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem =
    defaultWithScope(
      context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = subsystemName,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      ),
      mode = mode.flatMap(RunMode.from),
      configuration = configuration
    )

  def defaultWithScope(
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem = {
    val descriptor = _descriptor
    val runtimeConfig = RuntimeConfig.from(configuration)
    val runMode = mode.getOrElse(runtimeConfig.mode)
    val subsystem =
      Subsystem(
        name = descriptor.subsystemName,
        version = descriptor.componentVersionOption,
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
    val params = ComponentCreate(subsystem, ComponentOrigin.Repository("textus-identity"))
    val components =
      _repository_specs(configuration).flatMap(_.build(params).discover())
        .filter(_matches_descriptor_component(_, descriptor.componentName))
        .distinctBy(_.name)
    subsystem.add(components)
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
  ): Option[Vector[ComponentRepository.Specification]] = {
    val normalized = _normalize_repository_spec_value(value)
    ComponentRepository.parseSpecs(normalized, Paths.get("").toAbsolutePath.normalize).toOption
  }

  private def _normalize_repository_spec_value(
    value: String
  ): String =
    value
      .split(",")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { spec =>
        if (spec.contains(':')) spec else s"component-dir:${spec}"
      }
      .mkString(",")

  private def _default_repository_spec: ComponentRepository.Specification =
    ComponentRepository.ComponentDirRepository.Specification(ComponentRepository.defaultStandardRepositoryDir())

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
