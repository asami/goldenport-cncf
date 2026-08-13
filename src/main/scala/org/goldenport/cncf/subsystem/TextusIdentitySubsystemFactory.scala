package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.RepositoryBootstrapPolicy
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Mar. 26, 2026
 * @version Aug. 13, 2026
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
    GenericSubsystemFactory.defaultWithScope(
      descriptor = _descriptor.toGenericDescriptor,
      context = context,
      mode = mode,
      configuration = configuration,
      aliasResolver = aliasResolver
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
        GenericSubsystemFactory.runtimeDefaultWithScopeC(
          descriptor = _descriptor.toGenericDescriptor,
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver,
          repositoryBootstrapPolicy = Some(policy)
        )
      case None =>
        Consequence.configurationInvalid(
          "runtime repository bootstrap policy has not been admitted"
        )
    }

}
