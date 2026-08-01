package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, ExecutionContext, PrincipalId, SessionContext}
import org.goldenport.cncf.security.AuthenticationResult
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebExecutionRuntimeProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:static-web-execution-context-projection, example:PM-53-01, rules:SWEP-2,SWEP-3, phase:53")

  "Web execution runtime projection" must _in_phase53_spec {
    "project authenticated-user formatting and only explicitly public security state" in {
      Given("an authenticated user with Japanese preferences, internal security data, and a hostile public display name")
      val internalprincipal = "internal-principal-7981"
      val internaltoken = "internal-token-4b8f"
      val internalsession = "internal-session-52c4"
      val internalconfiguration = "internal-datastore-secret"
      val hostile = "</script><hostile>&\u2028\u2029"
      val authentication = AuthenticationResult(
        principalId = PrincipalId(internalprincipal),
        attributes = Map(
          "locale" -> "ja-JP",
          "timeZone" -> "Asia/Tokyo",
          "access_token" -> internaltoken,
          "component.configuration" -> internalconfiguration,
          "debug.calltree" -> "internal-calltree"
        ),
        capabilities = Set(Capability("knowledge:read"), Capability("internal:root")),
        session = Some(SessionContext(
          sessionId = Some(internalsession),
          tokenId = Some("internal-token-id"),
          attributes = Map("csrf" -> "internal-csrf")
        ))
      )
      val executioncontext = ExecutionContext.withSecurityContext(
        _execution_context(Locale.US, ZoneId.of("UTC")),
        authentication.toSecurityContext
      )
      val configuration = _configuration(Map(
        WebExecutionResolutionPolicy.LOCALE_KEY -> "en-US",
        WebExecutionResolutionPolicy.TIMEZONE_KEY -> "UTC",
        WebExecutionResolutionPolicy.HTTP_LANGUAGE_NEGOTIATION_ENABLED_KEY -> "true",
        WebExecutionResolutionPolicy.PUBLIC_CAPABILITIES_KEY -> "knowledge:read"
      ))

      When("runtime projection and first-render template generation execute before application JavaScript")
      val result = WebExecutionRuntimeProjection.resolve(
        WebExecutionPolicyResolution(
          WebExecutionResolutionPolicy.fromConfiguration(configuration).toOption.get,
          WebApplicationMode.MultiUser,
          None
        ),
        executioncontext,
        WebExecutionRuntimeRequest(
          acceptLanguage = Some("fr-FR,fr;q=0.9"),
          publicDisplayName = Some(hostile)
        )
      )
      val projection = result.toOption.getOrElse(fail(result.toString))
      val html = WebExecutionTemplateProjection.render(
        """<!doctype html><html><head><meta charset="UTF-8"><title>Application</title><script id="application-start">start()</script></head><body></body></html>""",
        projection
      )
      val source = _page_context_source(html)
      val json = parse(source).fold(throw _, identity)

      Then("user formatting wins while the public context is safe, allowlisted, escaped, and available first")
      result shouldBe a[Consequence.Success[_]]
      projection.locale shouldBe "ja-JP"
      projection.timezone shouldBe "Asia/Tokyo"
      projection.subject.authenticated shouldBe true
      projection.capabilities shouldBe Vector("knowledge:read")
      json.hcursor.downField("execution").downField("subject").get[String]("displayName").toOption shouldBe Some(hostile)
      html.indexOf("<meta charset=\"UTF-8\">") should be < html.indexOf(WebExecutionTemplateProjection.PAGE_CONTEXT_ELEMENT_ID)
      html.indexOf(WebExecutionTemplateProjection.PAGE_CONTEXT_ELEMENT_ID) should be < html.indexOf("application-start")
      source should not include "<"
      source should not include ">"
      source should not include "&"
      Vector(
        internalprincipal,
        internaltoken,
        internalsession,
        internalconfiguration,
        "internal-token-id",
        "internal-csrf",
        "internal-calltree",
        "internal:root"
      ).foreach(source should not include _)
    }

    "fall back deterministically to execution-owned runtime formatting" in {
      Given("a runtime locale and timezone without application, user, or request overrides")
      val executioncontext = _execution_context(Locale.CANADA_FRENCH, ZoneId.of("America/Toronto"))
      val request = WebExecutionRuntimeRequest(acceptLanguage = Some("ja-JP"))

      When("the same runtime projection is resolved repeatedly")
      val configuration = _configuration(Map.empty)
      val policy = WebExecutionResolutionPolicy.fromConfiguration(configuration).toOption.get
      val first = WebExecutionResolver.resolve(policy, WebExecutionResolutionInput(
        runtimeLocale = Some(Locale.CANADA_FRENCH),
        runtimeTimezone = Some(ZoneId.of("America/Toronto")),
        acceptLanguage = Some("ja-JP")
      ))
      val second = WebExecutionResolver.resolve(policy, WebExecutionResolutionInput(
        runtimeLocale = Some(Locale.CANADA_FRENCH),
        runtimeTimezone = Some(ZoneId.of("America/Toronto")),
        acceptLanguage = Some("ja-JP")
      ))

      Then("both projections retain the same execution-owned fallback values")
      first shouldBe a[Consequence.Success[_]]
      second shouldBe first
      first.toOption.map(_.locale) shouldBe Some(Locale.CANADA_FRENCH)
      first.toOption.map(_.timezone) shouldBe Some(ZoneId.of("America/Toronto"))
    }

    "require authenticated-user wiring for canonical multi-user projection" in {
      Given("a canonical multi-user Subsystem without provider wiring")
      val configuration = _configuration(Map(
        SubsystemUserMode.CONFIGURATION_KEY -> "multi-user",
        WebExecutionResolutionPolicy.LOCALE_KEY -> "en-US"
      ))
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val executioncontext = ExecutionContext.withSecurityContext(
        _execution_context(Locale.US, ZoneId.of("UTC")),
        AuthenticationResult(
          principalId = PrincipalId("projection-user"),
          attributes = Map("locale" -> "ja-JP")
        ).toSecurityContext
      )

      When("the supported owning-Subsystem projection overload is used")
      val result = WebExecutionRuntimeProjection.resolve(
        configuration,
        subsystem,
        executioncontext,
        WebExecutionRuntimeRequest()
      )

      Then("projection fails closed rather than manufacturing a Web-owned mode")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _execution_context(
    locale: Locale,
    timezone: ZoneId
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    val formatting = base.runtime.context.formatting
      .withLocale(locale)
      .withTimezone(timezone)
    ExecutionContext.withRuntimeContextContext(
      base,
      base.runtime.context.copy(formatting = formatting)
    )
  }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )

  private def _page_context_source(html: String): String = {
    val pattern = "(?s)<script id=\"textus-page-context\" type=\"application/json\">(.*?)</script>".r
    pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("Missing page context script"))
  }
}
