package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebExecutionResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Web execution formatting resolution" should {
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
        applicationMode = WebApplicationMode.MultiUser,
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
      val result = WebExecutionResolver.resolve(policy, input)

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

    "decode canonical and compatibility configuration keys with strict values" in {
      Given("a runtime configuration using CNCF compatibility aliases")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "cncf.runtime.web.execution.application-mode" -> ConfigurationValue.StringValue("multi-user"),
          "cncf.runtime.web.execution.locale" -> ConfigurationValue.StringValue("ja-JP"),
          "cncf.runtime.web.execution.timezone" -> ConfigurationValue.StringValue("Asia/Tokyo"),
          "cncf.runtime.web.execution.http-language-negotiation.enabled" -> ConfigurationValue.BooleanValue(true),
          "cncf.runtime.web.execution.public-capabilities" -> ConfigurationValue.StringValue("knowledge:read, page_admin")
        )),
        ConfigurationTrace.empty
      )

      When("the Web execution policy is decoded")
      val result = WebExecutionResolutionPolicy.fromConfiguration(configuration)

      Then("the aliases produce the canonical typed policy")
      result shouldBe a[Consequence.Success[_]]
      val policy = result.toOption.get
      policy.applicationMode shouldBe WebApplicationMode.MultiUser
      policy.applicationLocale shouldBe Some(Locale.JAPAN)
      policy.applicationTimezone shouldBe Some(ZoneId.of("Asia/Tokyo"))
      policy.httpLanguageNegotiationEnabled shouldBe true
      policy.publicCapabilities shouldBe Vector("knowledge:read", "page_admin")
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

    "reject malformed Web execution configuration instead of applying fallback values" in {
      Given("configuration with an unsupported application mode and non-boolean negotiation value")
      val badmode = _configuration(Map(
        WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "shared"
      ))
      val badnegotiation = _configuration(Map(
        WebExecutionResolutionPolicy.HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY -> "sometimes"
      ))

      When("the configurations are decoded")
      val moderesult = WebExecutionResolutionPolicy.fromConfiguration(badmode)
      val negotiationresult = WebExecutionResolutionPolicy.fromConfiguration(badnegotiation)

      Then("each malformed policy is a structured failure")
      moderesult shouldBe a[Consequence.Failure[_]]
      negotiationresult shouldBe a[Consequence.Failure[_]]
    }

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

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )
}
