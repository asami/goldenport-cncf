package org.goldenport.cncf.subsystem

import java.nio.file.Paths
import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{ConfigurationAccess, RepositoryBootstrapPolicy, RuntimeConfig}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Mar. 26, 2026
 * @version Aug. 11, 2026
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
  ): Subsystem =
    _default_with_scope(
      context,
      mode,
      configuration,
      aliasResolver,
      _repository_specs(configuration)
    )

  private[cncf] def runtimeDefaultWithScopeC(
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasResolver: AliasResolver,
    repositoryBootstrapPolicy: Option[RepositoryBootstrapPolicy]
  ): Consequence[Subsystem] =
    repositoryBootstrapPolicy match {
      case Some(policy) =>
        _runtime_repository_specs_c(policy).map { repositoryspecs =>
          _default_with_scope(context, mode, configuration, aliasresolver = aliasResolver, repositoryspecs)
        }
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

  private def _default_with_scope(
    context: ScopeContext,
    mode: Option[RunMode],
    configuration: ResolvedConfiguration,
    aliasresolver: AliasResolver,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Subsystem = {
    val descriptor = _descriptor
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
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
        httpDriver = Some(runtimeconfig.httpDriver),
        configuration = configuration,
        aliasResolver = aliasresolver,
        runMode = runmode
      )
    Subsystem.withStartupCleanup(subsystem) {
      val params = ComponentCreate(subsystem, ComponentOrigin.Repository("textus-identity"))
      val repositories = repositoryspecs.map(_.build(params))
      val components =
        ComponentRepository.discoverAssembly(repositories)
          .filter(_matches_descriptor_component(_, descriptor.componentName))
          .distinctBy(_.name)
      subsystem.add(components)
      subsystem
    }
  }

  private def _runtime_repository_specs_c(
    policy: RepositoryBootstrapPolicy
  ): Consequence[Vector[ComponentRepository.Specification]] = {
    val extracted = ComponentRepositorySpace.extractAdmittedRepositoryArgs(policy, Array.empty[String])
    val active = ComponentRepositorySpace.resolveSpecifications(
      extracted.active,
      policy.baseDirectory,
      noDefault = true
    )
    val search = ComponentRepositorySpace.resolveSpecifications(
      extracted.search,
      policy.baseDirectory,
      noDefault = true
    )
    (active, search) match {
      case (Right(activevalues), Right(searchvalues)) =>
        Consequence.success(
          (activevalues ++ (if (searchvalues.nonEmpty) searchvalues else _default_repository_specs)).distinct
        )
      case (Left(message), _) =>
        Consequence.configurationInvalid(
          s"runtime repository bootstrap policy is invalid: $message"
        )
      case (_, Left(message)) =>
        Consequence.configurationInvalid(
          s"runtime repository bootstrap policy is invalid: $message"
        )
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

  private def _default_repository_specs: Vector[ComponentRepository.Specification] =
    Vector(
      ComponentRepository.defaultLocalComponentRepositoryDir(),
      ComponentRepository.defaultLocalSubsystemRepositoryDir(),
      ComponentRepository.defaultStandardRepositoryDir()
    ).map(ComponentRepository.ComponentDirRepository.Specification.apply)

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
}
