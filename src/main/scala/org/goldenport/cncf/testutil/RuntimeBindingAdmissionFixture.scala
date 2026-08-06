package org.goldenport.cncf.testutil

import java.time.Clock
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.{CncfConfigurationCandidateDecoder, CncfConfigurationDocumentBatch, CncfConfigurationDocumentLocation, CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, SecurityContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.servicecontainer.ServiceContainerRuntime
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.context.I18nContext

/*
 * @since   Aug.  4, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
/** Explicit downstream test support packaged in the main artifact; it never auto-admits production Subsystems. */
object RuntimeBindingAdmissionFixture {
  def withClock(
    context: ExecutionContext,
    clock: Clock
  ): ExecutionContext = context match {
    case instance: ExecutionContext.Instance =>
      instance.copy(core = ExecutionContext.create(clock).core)
  }

  def withLocalePolicy(
    context: ExecutionContext,
    locale: Locale,
    allowedLocales: Set[Locale]
  ): ExecutionContext = context match {
    case instance: ExecutionContext.Instance =>
      val i18n = I18nContext.Instant(instance.i18n.core.copy(
        locale = Some(locale),
        allowedLocales = Some(allowedLocales)
      ))
      instance.copy(core = instance.core.copy(i18n = i18n, locale = locale))
  }

  def withSecurityContext(
    context: ExecutionContext,
    security: SecurityContext,
    unitOfWorkToken: String
  ): ExecutionContext = {
    lazy val secured = ExecutionContext.withSecurityContext(context, security)
    lazy val rebound: ExecutionContext =
      ExecutionContext.withRuntimeContext(secured, runtime)
    lazy val runtime =
      secured.runtime.withUnitOfWorkContext(rebound, unitOfWorkToken)
    rebound
  }

  /**
    * Explicit controlled test support for downstream consumers; this does not
    * reopen production Subsystem runtime auto-admission.
    */
  def withServiceContainerRuntime(
    subsystem: Subsystem,
    runtime: ServiceContainerRuntime
  ): Subsystem = {
    _take(subsystem.installServiceContainerRuntimeC(runtime))
    subsystem
  }

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
    subsystem.enableControlledTestExecution()
    if (subsystem.runtimeOperationSecurityPolicyC.isFaillure) {
      val identity = _take(SubsystemInstanceId.create("platform", "default"))
      val target = _take(CncfConfigurationTarget.SubsystemInstance.create(identity))
      val fields = subsystem.configuration.configuration.values.toVector.collect {
        case (key, value) if _runtime_binding_keys.contains(key) =>
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

  private lazy val _runtime_binding_keys: Set[String] = {
    val canonical = Vector(
      SubsystemUserMode.CONFIGURATION_KEY,
      RuntimeConfig.operationModeKey,
      RuntimeConfig.webDevelopAnonymousAdminKey,
      RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY,
      RuntimeConfig.webProductionAdminEnabledKey,
      RuntimeConfig.webProductionAdminSystemRolesKey,
      RuntimeConfig.webProductionAdminComponentRolesKey,
      RuntimeConfig.webProductionAdminJobsRolesKey,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_LOCALE_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_TIMEZONE_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_DATE_FORMAT_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_DATE_TIME_FORMAT_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_DISPLAY_OVERRIDE_ENABLED_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY,
      CncfConfigurationParameterCatalog.WEB_EXECUTION_PUBLIC_CAPABILITIES_KEY
    )
    canonical.flatMap { key =>
      val suffix = key.stripPrefix("textus.")
      Vector(key, s"textus.runtime.$suffix", s"cncf.$suffix", s"cncf.runtime.$suffix")
    }.toSet
  }

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(throw new IllegalStateException(result.display))
}
