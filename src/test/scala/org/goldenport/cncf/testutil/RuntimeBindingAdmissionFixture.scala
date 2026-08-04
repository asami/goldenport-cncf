package org.goldenport.cncf.testutil

import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.{CncfConfigurationCandidateDecoder, CncfConfigurationDocumentBatch, CncfConfigurationDocumentLocation, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.context.{GlobalRuntimeContext, ScopeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationTrace, ResolvedConfiguration}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object RuntimeBindingAdmissionFixture {
  def default(
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem =
    admit(DefaultSubsystemFactory.default(mode, configuration))

  def default(
    extraComponents: Seq[Component],
    mode: Option[String]
  ): Subsystem =
    admit(DefaultSubsystemFactory.default(extraComponents, mode))

  def defaultWithScope(
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem =
    admit(DefaultSubsystemFactory.defaultWithScope(
      context,
      mode,
      configuration,
      aliasResolver
    ))

  def admit(subsystem: Subsystem): Subsystem = {
    if (subsystem.runtimeOperationSecurityPolicyC.isFaillure) {
      val identity = _take(SubsystemInstanceId.create("platform", "default"))
      val target = _take(CncfConfigurationTarget.SubsystemInstance.create(identity))
      val fields = subsystem.configuration.configuration.values.toVector.collect {
        case (key, value) if _operation_security_keys.contains(key) =>
          ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
      }
      val bindings =
        if (fields.isEmpty)
          ConfigurationBindingCollection.empty[CncfConfigurationTarget]
        else {
          val candidates = _take(CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
            CncfConfigurationDocumentBatch(
              new CncfConfigurationDocumentLocation.SubsystemInstance(target),
              _take(ConfigurationSourceAdmission.create(
                ConfigurationOrigin.Home,
                "home",
                "runtime-binding-admission-fixture",
                10,
                "runtime-binding-admission-fixture",
                () => Consequence.success(ConfigurationDocument.Object(fields))
              ))
            )
          )))
          val context = _take(CncfConfigurationResolutionContext.forSubsystem(identity))
          _take(ConfigurationBindingResolver.resolve(candidates, context.generic))
        }
      _take(subsystem.admitRuntimeConfigurationBindingsC(bindings))
      if (subsystem.runtimeOperationSecurityPolicyC.isFaillure)
        throw new IllegalStateException(subsystem.runtimeOperationSecurityPolicyC.display)
    }
    subsystem
  }

  private val _operation_security_keys: Set[String] = {
    val canonical = Vector(
      SubsystemUserMode.CONFIGURATION_KEY,
      RuntimeConfig.operationModeKey,
      RuntimeConfig.webDevelopAnonymousAdminKey,
      RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY,
      RuntimeConfig.webProductionAdminEnabledKey,
      RuntimeConfig.webProductionAdminSystemRolesKey,
      RuntimeConfig.webProductionAdminComponentRolesKey,
      RuntimeConfig.webProductionAdminJobsRolesKey
    )
    canonical.flatMap { key =>
      val suffix = key.stripPrefix("textus.")
      Vector(key, s"textus.runtime.$suffix", s"cncf.$suffix", s"cncf.runtime.$suffix")
    }.toSet
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(throw new IllegalStateException(result.display))
}
