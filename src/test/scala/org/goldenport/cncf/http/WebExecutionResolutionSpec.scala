package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentStyleCatalog, ComponentStyleId}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemAuthenticationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebExecutionResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:static-web-execution-context-projection, example:PM-53-01, rules:SWEP-3, phase:53")

  "Web execution formatting resolution" must _in_phase53_spec {
    "formatting precedence" which {
    "prefer standalone application formatting over user and browser values" in {
      Given("a standalone application policy and conflicting user and browser values")
      val policy = WebExecutionResolutionPolicy(
        applicationLocale = Some(Locale.JAPAN),
        applicationTimezone = Some(ZoneId.of("Asia/Tokyo")),
        httpLanguageNegotiationEnabled = true
      )
      val input = WebExecutionResolutionInput(
        runtimeLocale = Some(Locale.US),
        runtimeTimezone = Some(ZoneId.of("UTC")),
        authenticated = true,
        userLocale = Some("fr-FR"),
        userTimezone = Some("Europe/Paris"),
        acceptLanguage = Some("de-DE")
      )

      When("the first-render formatting is resolved")
      val result = WebExecutionResolver.resolve(policy, input)

      Then("the configured standalone application values remain authoritative")
      result shouldBe a[Consequence.Success[_]]
      result.toOption.get.locale shouldBe Locale.JAPAN
      result.toOption.get.timezone shouldBe ZoneId.of("Asia/Tokyo")
    }

    "prefer authenticated-user formatting in multi-user mode" in {
      Given("a multi-user policy with authenticated-user preferences")
      val policy = WebExecutionResolutionPolicy(
        applicationLocale = Some(Locale.US),
        applicationTimezone = Some(ZoneId.of("UTC")),
        httpLanguageNegotiationEnabled = true
      )
      val input = WebExecutionResolutionInput(
        authenticated = true,
        userLocale = Some("ja-JP"),
        userTimezone = Some("Asia/Tokyo"),
        acceptLanguage = Some("fr-FR")
      )

      When("the first-render formatting is resolved")
      val result = WebExecutionResolver.resolve(policy, WebApplicationMode.MultiUser, input)

      Then("the authenticated-user values precede application fallback and browser negotiation")
      result.toOption.get.locale shouldBe Locale.JAPAN
      result.toOption.get.timezone shouldBe ZoneId.of("Asia/Tokyo")
    }

    "apply display overrides only when the Web policy enables them" in {
      Given("the same display override under disabled and enabled policies")
      val input = WebExecutionResolutionInput(
        runtimeLocale = Some(Locale.US),
        runtimeTimezone = Some(ZoneId.of("UTC")),
        displayLocale = Some("ja-JP"),
        displayTimezone = Some("Asia/Tokyo")
      )

      When("both policies resolve the request")
      val disabled = WebExecutionResolver.resolve(WebExecutionResolutionPolicy(), input).toOption.get
      val enabled = WebExecutionResolver.resolve(
        WebExecutionResolutionPolicy(displayOverrideEnabled = true),
        input
      ).toOption.get

      Then("only the enabled policy accepts request-owned display values")
      disabled.locale shouldBe Locale.US
      disabled.timezone shouldBe ZoneId.of("UTC")
      enabled.locale shouldBe Locale.JAPAN
      enabled.timezone shouldBe ZoneId.of("Asia/Tokyo")
    }

    "negotiate Accept-Language only after execution-owned locale sources are exhausted" in {
      Given("an explicitly enabled HTTP negotiation policy")
      val policy = WebExecutionResolutionPolicy(httpLanguageNegotiationEnabled = true)

      When("runtime locale is present and when it is absent")
      val owned = WebExecutionResolver.resolve(
        policy,
        WebExecutionResolutionInput(runtimeLocale = Some(Locale.US), acceptLanguage = Some("ja-JP"))
      ).toOption.get
      val negotiated = WebExecutionResolver.resolve(
        policy,
        WebExecutionResolutionInput(runtimeLocale = None, acceptLanguage = Some("ja-JP,fr-FR;q=0.8"))
      ).toOption.get
      val wildcard = WebExecutionResolver.resolve(
        policy,
        WebExecutionResolutionInput(runtimeLocale = None, acceptLanguage = Some("*"))
      ).toOption.get

      Then("browser negotiation cannot supersede the runtime but can supply the otherwise missing locale")
      owned.locale shouldBe Locale.US
      negotiated.locale shouldBe Locale.JAPAN
      wildcard.locale shouldBe Locale.ROOT
    }

    "map execution format policy to stable public identifiers" in {
      Given("runtime and explicit application display-format policies")
      val runtimeinput = WebExecutionResolutionInput(runtimeDateTimeFormatPolicy = Some("localized"))
      val explicitpolicy = WebExecutionResolutionPolicy(
        dateFormat = WebDisplayFormatPolicyId.parse("localized-short"),
        dateTimeFormat = WebDisplayFormatPolicyId.parse("localized-long")
      )

      When("the policies are resolved")
      val runtime = WebExecutionResolver.resolve(WebExecutionResolutionPolicy(), runtimeinput).toOption.get
      val explicit = WebExecutionResolver.resolve(explicitpolicy, runtimeinput).toOption.get

      Then("public identifiers are deterministic and do not expose formatter implementation strings")
      runtime.projectionPolicy.dateFormat shouldBe WebDisplayFormatPolicyId.LOCALIZED_MEDIUM
      runtime.projectionPolicy.dateTimeFormat shouldBe WebDisplayFormatPolicyId.LOCALIZED_MEDIUM
      explicit.projectionPolicy.dateFormat.name shouldBe "localized-short"
      explicit.projectionPolicy.dateTimeFormat.name shouldBe "localized-long"
    }
    }

    "configuration decoding" which {
    "decode presentation keys without selecting a Subsystem user mode" in {
      Given("a presentation-only runtime configuration with legacy formatting aliases")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "cncf.runtime.web.execution.locale" -> ConfigurationValue.StringValue("ja-JP"),
          "cncf.runtime.web.execution.timezone" -> ConfigurationValue.StringValue("Asia/Tokyo"),
          "cncf.runtime.web.execution.http-language-negotiation.enabled" -> ConfigurationValue.BooleanValue(true),
          "cncf.runtime.web.execution.public-capabilities" -> ConfigurationValue.StringValue("knowledge:read, page_admin")
        )),
        ConfigurationTrace.empty
      )

      When("the Web execution policy is decoded")
      val result = WebExecutionResolutionPolicy.fromConfiguration(configuration)

      Then("only presentation keys produce the typed Web policy")
      result shouldBe a[Consequence.Success[_]]
      val policy = result.toOption.get
      policy.applicationLocale shouldBe Some(Locale.JAPAN)
      policy.applicationTimezone shouldBe Some(ZoneId.of("Asia/Tokyo"))
      policy.httpLanguageNegotiationEnabled shouldBe true
      policy.publicCapabilities shouldBe Vector("knowledge:read", "page_admin")
    }

    "ignore obsolete Web mode keys while decoding Web presentation policy" in {
      Given("obsolete Web values with conflicting spellings")
      val standalone = _configuration(Map(
        "cncf.web.application-mode" -> "multi-user",
        "cncf.runtime.web.execution.application-mode" -> "multi-user",
        "textus.web.execution.application-mode" -> "multi-user"
      ))
      val multiuser = _configuration(Map(
        "cncf.web.application-mode" -> "standalone",
        "cncf.runtime.web.execution.application-mode" -> "standalone",
        "textus.web.execution.application-mode" -> "standalone"
      ))

      When("Web presentation policy is decoded")
      val resolvedstandalone = WebExecutionResolutionPolicy.fromConfiguration(standalone).toOption.get
      val resolvedmultiuser = WebExecutionResolutionPolicy.fromConfiguration(multiuser).toOption.get

      Then("no configuration value selects the Web projection mode")
      resolvedstandalone shouldBe resolvedmultiuser
    }

    "ignore noncanonical WebApplicationMode spellings when the Subsystem key is absent" in {
      Given("only former CNCF and execution-scoped spellings")
      val noncanonical = _configuration(Map(
        "cncf.web.application-mode" -> "multi-user",
        "cncf.runtime.web.execution.application-mode" -> "multi-user",
        "textus.web.execution.application-mode" -> "multi-user"
      ))

      When("Web presentation policy is decoded without a Subsystem")
      val resolved = WebExecutionResolutionPolicy.fromConfiguration(noncanonical)

      Then("former spellings cannot create a Web mode selector")
      resolved shouldBe a[Consequence.Success[_]]
    }

    "derive standalone only from fixed-user direct-Component capability evidence" in {
      Given("eligible and ineligible direct Component Subsystems with no canonical user-mode value")
      val eligible = _direct_component_subsystem(fixedcontextcompatible = true, fixeduser = true)
      val incapable = _direct_component_subsystem(fixedcontextcompatible = false, fixeduser = true)
      val explicit = _direct_component_subsystem(fixedcontextcompatible = true, fixeduser = true, implicitlaunch = false)
      val notfixed = _direct_component_subsystem(fixedcontextcompatible = true, fixeduser = false)
      val controlled = DefaultSubsystemFactory.default(mode = None, configuration = _configuration(Map.empty))

      When("the canonical Subsystem user-mode key is absent")
      val derived = WebExecutionResolutionPolicy.resolveForSubsystem(_configuration(Map.empty), eligible)
      val missingcapability = WebExecutionResolutionPolicy.resolveForSubsystem(_configuration(Map.empty), incapable)
      val explicitlaunch = WebExecutionResolutionPolicy.resolveForSubsystem(_configuration(Map.empty), explicit)
      val missingfixeduser = WebExecutionResolutionPolicy.resolveForSubsystem(_configuration(Map.empty), notfixed)
      val controlledresult = WebExecutionResolutionPolicy.resolveForSubsystem(_configuration(Map.empty), controlled)

      Then("only an eligible direct Component receives the traceable standalone contribution")
      derived shouldBe a[Consequence.Success[_]]
      val resolution = derived.toOption.get
      resolution.applicationMode shouldBe WebApplicationMode.Standalone
      resolution.applicationModeTrace.map(_.origin) shouldBe Some(org.goldenport.configuration.ConfigurationOrigin.Default)
      resolution.applicationModeTrace.flatMap(_.sourceType) shouldBe Some("derived-default")
      resolution.applicationModeTrace.flatMap(_.sourceId) shouldBe Some("textus-direct-component-standalone")
      missingcapability shouldBe a[Consequence.Failure[_]]
      explicitlaunch shouldBe a[Consequence.Failure[_]]
      missingfixeduser shouldBe a[Consequence.Failure[_]]
      controlledresult shouldBe a[Consequence.Success[_]]
      controlledresult.toOption.get.applicationModeTrace.flatMap(_.sourceType) shouldBe Some("controlled-test")
    }

    "reject malformed selected locale timezone format and policy values structurally" in {
      Given("invalid request and configuration values")
      When("resolution validates the selected source")
      val badlocale = WebExecutionResolver.resolve(
        WebExecutionResolutionPolicy(displayOverrideEnabled = true),
        WebExecutionResolutionInput(displayLocale = Some("!!!"))
      )
      val badtimezone = WebExecutionResolver.resolve(
        WebExecutionResolutionPolicy(displayOverrideEnabled = true),
        WebExecutionResolutionInput(displayTimezone = Some("Mars/Olympus"))
      )
      val badformat = WebExecutionResolver.resolve(
        WebExecutionResolutionPolicy(),
        WebExecutionResolutionInput(runtimeDateTimeFormatPolicy = Some("java.time.Format@123"))
      )

      Then("each invalid value remains a structured Consequence failure")
      badlocale shouldBe a[Consequence.Failure[_]]
      badtimezone shouldBe a[Consequence.Failure[_]]
      badformat shouldBe a[Consequence.Failure[_]]
    }

    "reject Subsystem user-mode input from the configuration-only presentation API" in {
      Given("configuration with a Subsystem value and non-boolean negotiation value")
      val badmode = _configuration(Map(
        org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> "shared"
      ))
      val badnegotiation = _configuration(Map(
        WebExecutionResolutionPolicy.HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY -> "sometimes"
      ))

      When("the configurations are decoded")
      val moderesult = WebExecutionResolutionPolicy.fromConfiguration(badmode)
      val negotiationresult = WebExecutionResolutionPolicy.fromConfiguration(badnegotiation)

      Then("canonical Subsystem input and malformed Web policy are structured failures")
      moderesult shouldBe a[Consequence.Failure[_]]
      negotiationresult shouldBe a[Consequence.Failure[_]]
    }

    "reject composite and null canonical input from the configuration-only presentation API" in {
      Given("a canonical key with composite or null configuration values")
      val values = Vector[ConfigurationValue](
        ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("standalone"))),
        ConfigurationValue.ObjectValue(Map("value" -> ConfigurationValue.StringValue("multi-user"))),
        ConfigurationValue.NullValue
      )

      When("presentation-only configuration decoding is attempted")
      val results = values.map { value =>
        WebExecutionResolutionPolicy.fromConfiguration(ResolvedConfiguration(
          Configuration(Map(org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> value)),
          ConfigurationTrace.empty
        ))
      }

      Then("every present canonical value requires owning Subsystem resolution")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
    }

    "use admitted typed Web policy values rather than legacy configuration and keep an admitted absence authoritative" in {
      Given("two fixed-user Subsystems with conflicting legacy Web configuration")
      val typed = _web_subsystem("en-US")
      val absent = _web_subsystem("fr-FR")
      val typedcollection = _web_collection(includeLocale = true)
      val absentcollection = _web_collection(includeLocale = false)

      When("a final typed collection is admitted before Web policy resolution")
      typed.admitRuntimeConfigurationBindingsC(typedcollection).isSuccess shouldBe true
      absent.admitRuntimeConfigurationBindingsC(absentcollection).isSuccess shouldBe true
      val typedresult = WebExecutionResolutionPolicy.resolveForRuntimeSubsystem(typed)
      val absentresult = WebExecutionResolutionPolicy.resolveForRuntimeSubsystem(absent)

      Then("typed values win and an admitted absent value does not revive legacy text")
      typedresult shouldBe a[Consequence.Success[_]]
      typedresult.toOption.get.policy.applicationLocale shouldBe Some(Locale.JAPAN)
      absentresult shouldBe a[Consequence.Success[_]]
      absentresult.toOption.get.policy.applicationLocale shouldBe None
    }

    "fail closed for an unadmitted runtime Subsystem rather than reviving legacy Web text" in {
      Given("a Subsystem with legacy Web configuration but no final typed collection")
      val subsystem = _web_subsystem("fr-FR")

      When("the runtime-only Web resolver is called")
      val result = WebExecutionResolutionPolicy.resolveForRuntimeSubsystem(subsystem)

      Then("runtime resolution fails structurally without consulting the legacy locale")
      result shouldBe a[Consequence.Failure[_]]
    }
    }

    "validation and precedence invariants" which {
    "keep configured application locale authoritative for arbitrary conflicting browser languages" in {
      Given("generated application and browser locale pairs")
      val locales = Gen.oneOf("ja-JP", "en-US", "fr-FR", "de-DE")
      val property = Prop.forAll(locales, locales) { (application, browser) =>
        val expected = WebExecutionResolver.parseLocale(application).get
        val policy = WebExecutionResolutionPolicy(
          applicationLocale = Some(expected),
          httpLanguageNegotiationEnabled = true
        )
        WebExecutionResolver.resolve(
          policy,
          WebExecutionResolutionInput(runtimeLocale = None, acceptLanguage = Some(browser))
        ).toOption.exists(_.locale == expected)
      }

      When("the precedence property is checked repeatedly")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("Accept-Language never overrides configured application locale")
      checked.passed shouldBe true
    }
    }
  }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )

  private def _web_subsystem(legacyLocale: String): Subsystem =
    Subsystem("web-policy", configuration = _configuration(Map(
      SubsystemUserMode.CONFIGURATION_KEY -> "standalone",
      WebExecutionResolutionPolicy.LOCALE_KEY -> legacyLocale
    ))).withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("web-policy.car"),
      subsystemName = "web-policy",
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(
        GenericSubsystemAuthenticationBinding(localSubject = Some(GenericSubsystemLocalSubjectBinding("web-policy-user")))
      )))
    ))

  private def _web_collection(includeLocale: Boolean): ConfigurationBindingCollection[CncfConfigurationTarget] = {
    val identity = SubsystemInstanceId.default("web-policy").getOrElse(fail("identity is required"))
    val target = CncfConfigurationTarget.SubsystemInstance.create(identity).getOrElse(fail("target is required"))
    val candidates = Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]](
      _candidate(CncfConfigurationParameterCatalog.subsystemUserMode, SubsystemUserMode.Standalone, target)
    ) ++ Option.when(includeLocale)(_candidate(CncfConfigurationParameterCatalog.webExecutionLocale, Locale.JAPAN, target)).toVector
    val batch = ConfigurationBindingCandidates.from(candidates).getOrElse(fail("candidates are required"))
    val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("context is required"))
    ConfigurationBindingResolver.resolve(batch, context.generic).getOrElse(fail("collection is required"))
  }

  private def _candidate[A](
    parameter: org.goldenport.configuration.ConfigurationParameter[A],
    value: A,
    target: CncfConfigurationTarget.SubsystemInstance
  ): ConfigurationBindingCandidate[A, CncfConfigurationTarget] = {
    val provenance = ConfigurationProvenance.create(
      ConfigurationOrigin.Cwd, "textus", "web-policy-typed", Some(parameter.id.value),
      Some(parameter.id.value), 30, 1, Vector("phase-55: gcf07h"), false, Some("spec")
    ).getOrElse(fail("provenance is required"))
    ConfigurationBindingCandidate.create(parameter, target, value, provenance).getOrElse(fail("candidate is required"))
  }

  private def _direct_component_subsystem(
    fixedcontextcompatible: Boolean,
    fixeduser: Boolean,
    implicitlaunch: Boolean = true
  ) = {
    val snapshot = ComponentStyleCatalog.default
      .resolveC(ComponentStyleId.parseC("full-fledged-with-standalone@1").toOption.get)
      .flatMap(ComponentStyleCatalog.default.expandC)
      .toOption
      .get
    val descriptor = ComponentDescriptor(
      name = Some("direct-app"),
      componentName = Some("direct-app"),
      schemaVersion = Some(2),
      componentStyleSnapshot = Some(
        if (fixedcontextcompatible) snapshot
        else snapshot.copy(effectiveCapabilities = Vector.empty)
      )
    )
    val security =
      if (fixeduser)
        Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
          localSubject = Some(GenericSubsystemLocalSubjectBinding("direct-user"))
        ))))
      else
        None
    DefaultSubsystemFactory
      .default(mode = None, configuration = _configuration(Map.empty))
      .withDescriptor(GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("direct-app.car"),
        subsystemName = "direct-app",
        componentBindings = Vector(GenericSubsystemComponentBinding("direct-app")),
        security = security,
        componentDescriptorOverrides = Vector(descriptor),
        implicitRootComponentName = Option.when(implicitlaunch)("direct-app")
      ))
  }
}
