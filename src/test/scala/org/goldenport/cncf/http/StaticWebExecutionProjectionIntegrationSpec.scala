package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.nio.file.{Files, Path}
import java.util.Locale
import java.util.Comparator
import scala.jdk.CollectionConverters._
import scala.util.Using

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.goldenport.protocol.Protocol
import org.http4s.{Header, Method, Request, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Jul. 17, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticWebExecutionProjectionIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:static-web-execution-context-projection, example:$example, rules:$rules, phase:53, slice:PM-53-01")

  "Static Web runtime execution projection" should {
    "standalone first-render execution metadata" which {
    "E1 project configured standalone execution state before the first application render" must _metadata("E1", "SWEP-3") {
      "when a Japanese standalone Static Web app receives a conflicting English request language" in {
      Given("Spec: docs/spec/static-web-execution-context-projection.md; Rules: SWEP-3; Example: E1; a Japanese standalone Static Web app and a conflicting English request language")
      _with_temp_directory("static-web-execution-projection-") { root =>
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  apps:
          |    - name: debug-app
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<!doctype html><html lang="en"><head><title>${message.page.title}</title></head><body><main id="application"><h1>${message.page.heading}</h1><textus:line-list source="pageContext.view.items" columns="title,status"></textus:line-list></main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone"),
          WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("ja-JP"),
          WebExecutionResolutionPolicy.TIMEZONE_KEY -> ConfigurationValue.StringValue("Asia/Tokyo"),
          WebExecutionResolutionPolicy.PUBLIC_CAPABILITIES_KEY -> ConfigurationValue.StringValue("debug:read")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = _static_subsystem(configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime HTTP engine is required")))
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/debug/debug-app")
      ).putHeaders(Header.Raw(CIString("Accept-Language"), "en-US,en;q=0.9"))

      When("the canonical component-owned Static Web route renders its first response")
      val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
      val html = response.as[String].unsafeRunSync()
      val pagecontext = _page_context(html)

      Then("the response keeps application content while execution-owned locale and timezone drive first-render metadata")
      response.status.code shouldBe 200
      response.headers.headers.find(_.name == CIString("Content-Language")).map(_.value) shouldBe Some("ja-JP")
      html should include ("<main id=\"application\">")
      html should include ("<title>展覧会</title>")
      html should include ("<h1>鑑賞計画</h1>")
      html should include ("展示A")
      html should include ("開催中")
      html should not include "<textus:line-list"
      html should include ("<html lang=\"ja-JP\" data-textus-locale=\"ja-JP\">")
      html.indexOf("textus-page-context") should be < html.indexOf("id=\"application\"")
      pagecontext.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("ja-JP")
      pagecontext.hcursor.downField("execution").get[String]("timezone").toOption shouldBe Some("Asia/Tokyo")
      pagecontext.hcursor.downField("execution").get[String]("applicationMode").toOption shouldBe Some("standalone")
      pagecontext.hcursor.downField("execution").downField("subject").get[Boolean]("authenticated").toOption shouldBe Some(true)
      pagecontext.hcursor.downField("execution").get[Vector[String]]("capabilities").toOption shouldBe Some(Vector.empty)
      pagecontext.hcursor.downField("view").downField("items").downArray.get[String]("title").toOption shouldBe Some("展示A")
      pagecontext.hcursor.downField("view").get[String]("provider_application_mode").toOption shouldBe Some("standalone")
      }
    }
    }
    }

    "anonymous multi-user route execution context" which {
    "E2 retain anonymous multi-user static-page formatting on the route component execution context" must _metadata("E2", "SWEP-3") {
      "when a Japanese multi-user runtime profile declines an anonymous static page request" in {
      Given("Spec: docs/spec/static-web-execution-context-projection.md; Rules: SWEP-3; Example: E2; a Japanese multi-user runtime profile, a declining authentication provider, and an anonymous static page request")
      _with_temp_directory("static-web-anonymous-multi-user-projection-") { root =>
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  apps:
          |    - name: debug-app
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<!doctype html><html><head><title>${message.page.title}</title></head><body><main id="application">${message.page.heading}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
          SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user"),
          RuntimeConfig.EXECUTION_LOCALE_KEY -> ConfigurationValue.StringValue("ja-JP"),
          RuntimeConfig.EXECUTION_TIMEZONE_KEY -> ConfigurationValue.StringValue("Asia/Tokyo")
        )),
        ConfigurationTrace.empty
      )

      _with_global_runtime(Locale.JAPAN, ZoneId.of("Asia/Tokyo")) { global =>
        val subsystem = _static_subsystem(
          configuration,
          SubsystemUserMode.MultiUser,
          Vector(_declining_authentication_provider),
          Some(global)
        )
        subsystem.add(_static_page_view_component(subsystem, Vector(_declining_authentication_provider)))
        val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime HTTP engine is required")))
        val request = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/debug-app"))

        When("the authenticated ingress declines the anonymous static request and the static fallback resolves")
        val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
        val html = response.as[String].unsafeRunSync()
        val pagecontext = _page_context(html)

        Then("the anonymous page retains multi-user mode and the route-context Japanese execution formatting")
        response.status.code shouldBe 200
        pagecontext.hcursor.downField("execution").get[String]("applicationMode").toOption shouldBe Some("multi-user")
        pagecontext.hcursor.downField("execution").downField("subject").get[Boolean]("authenticated").toOption shouldBe Some(false)
        pagecontext.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("ja-JP")
        pagecontext.hcursor.downField("execution").get[String]("timezone").toOption shouldBe Some("Asia/Tokyo")
        pagecontext.hcursor.downField("view").get[String]("provider_application_mode").toOption shouldBe Some("multi-user")
        html should include ("<title>展覧会</title>")
        html should include ("<main id=\"application\">鑑賞計画</main>")
      }
      }
    }
    }
    }

    "execution-owned formatting policy" which {
    "E3 ignore arbitrary request formatting headers when display override is disabled" must _metadata("E3", "SWEP-3") {
      "when a caller supplies an arbitrary locale header" in {
      Given("Spec: docs/spec/static-web-execution-context-projection.md; Rules: SWEP-3; Example: E3; a Static Web app whose execution runtime owns the default locale")
      _with_temp_directory("static-web-execution-header-policy-") { root =>
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  apps:
          |    - name: debug-app
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<!doctype html><html lang="ja"><head><title>${message.page.title}</title></head><body><main id="application">${message.page.heading}</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone"),
          WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("en-US")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = _static_subsystem(configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime HTTP engine is required")))
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/debug/debug-app")
      ).putHeaders(Header.Raw(CIString("Locale"), "en-US"))

      When("a caller attempts to replace the locale through an arbitrary HTTP header")
      val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
      val html = response.as[String].unsafeRunSync()
      val pagecontext = _page_context(html)

      Then("the first render retains the execution-owned runtime locale")
      response.status.code shouldBe 200
      response.headers.headers.find(_.name == CIString("Content-Language")).map(_.value) shouldBe Some("en-US")
      html should include ("<html lang=\"en-US\" data-textus-locale=\"en-US\">")
      html should include ("<title>Exhibitions</title>")
      html should include ("<main id=\"application\">Planning</main>")
      pagecontext.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("en-US")
      }
    }
    }
    }

    "provider query context" which {
    "E4 pass page query values to component page-context providers" must _metadata("E4", "SWEP-3") {
      "when a Static Web page request contains application filter values" in {
      Given("Spec: docs/spec/static-web-execution-context-projection.md; Rules: SWEP-3; Example: E4; a Static Web page request with application filter values")
      _with_temp_directory("static-web-page-query-context-") { root =>
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  apps:
          |    - name: debug-app
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<!doctype html><html><head></head><body><main>Query context</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = _static_subsystem(configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime HTTP engine is required")))
      val request = Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/debug/debug-app?date=2026-07-20&timeline_range=current_future")
      )

      When("the component page-context provider resolves the first document")
      val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
      val pagecontext = _page_context(response.as[String].unsafeRunSync())

      Then("the provider receives the canonical query values without a browser REST request")
      pagecontext.hcursor.downField("view").downField("query").get[String]("date").toOption shouldBe Some("2026-07-20")
      pagecontext.hcursor.downField("view").downField("query").get[String]("timeline_range").toOption shouldBe Some("current_future")
      }
    }
    }
    }

    "locale catalog layering" which {
    "E5 keep unrelated runtime messages out of the selected locale catalog" must _metadata("E5", "SWEP-3") {
      "when Japanese application catalogs are resolved with an English runtime message map" in {
      Given("Spec: docs/spec/static-web-execution-context-projection.md; Rules: SWEP-3; Example: E5; Japanese application catalogs and an English runtime message map")
      val subsystem = DefaultSubsystemFactory.default(
        None,
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      subsystem.add(_static_page_view_component(subsystem))

      When("the framework resolves the Japanese catalog layers")
      val messages = WebMessageCatalogRuntime.resolve(
        subsystem,
        "debug-app",
        Locale.JAPAN,
        Locale.US,
        Map("runtime.only" -> "English runtime text")
      )

      Then("root, language, and exact application layers resolve without the unrelated runtime text")
      messages.get("page.title") shouldBe Some("展覧会")
      messages.get("page.heading") shouldBe Some("鑑賞計画")
      messages should not contain key ("runtime.only")
    }
    }
    }
  }

  private def _page_context(html: String): io.circe.Json = {
    val pattern = "(?s)<script id=\"textus-page-context\" type=\"application/json\">(.*?)</script>".r
    val source = pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("Missing page context"))
    parse(source).fold(throw _, identity)
  }

  private final class StaticPageViewComponent(
    providers: Vector[AuthenticationProvider] = Vector.empty
  ) extends Component {
    override def authenticationProviders: Vector[AuthenticationProvider] = providers

    override def webMessageCatalogs: Vector[WebMessageCatalog] =
      Vector(
        WebMessageCatalog(
          "debug-app",
          Locale.ROOT,
          Map("page.title" -> "Application", "page.heading" -> "Application")
        ),
        WebMessageCatalog(
          "debug-app",
          Locale.JAPANESE,
          Map("page.heading" -> "鑑賞計画")
        ),
        WebMessageCatalog(
          "debug-app",
          Locale.JAPAN,
          Map("page.title" -> "展覧会")
        ),
        WebMessageCatalog(
          "debug-app",
          Locale.US,
          Map("page.title" -> "Exhibitions", "page.heading" -> "Planning")
        )
      )

    override def webPageContextProviders: Vector[WebPageContextProvider] =
      Vector(new WebPageContextProvider {
        override def resolve(
          request: WebPageContextRequest
        )(using ExecutionContext): Consequence[WebPageContext] =
          if (request.app == "debug-app" && request.page.isEmpty)
            Consequence.success(WebPageContext(view = Record.data(
              "items" -> Vector(Record.data("title" -> "展示A", "status" -> "開催中")),
              "query" -> Record.data(request.values.toSeq*),
              "provider_application_mode" -> request.execution.map(_.applicationMode.name).getOrElse("missing")
            )))
          else
            Consequence.success(WebPageContext.empty)
      })
  }

  private def _static_page_view_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    providers: Vector[AuthenticationProvider] = Vector.empty
  ): Component = {
    val id = ComponentId("static_page_view")
    new StaticPageViewComponent(providers).initialize(ComponentInit(
      subsystem,
      Component.Core.create("debug-app", id, ComponentInstanceId.default(id), Protocol.empty),
      ComponentOrigin.Main
    ))
  }

  private def _static_subsystem(
    configuration: ResolvedConfiguration,
    usermode: SubsystemUserMode = SubsystemUserMode.Standalone,
    providers: Vector[AuthenticationProvider] = Vector.empty,
    runtimecontext: Option[GlobalRuntimeContext] = None
  ) = {
    val base = runtimecontext.map { context =>
      DefaultSubsystemFactory.defaultWithScope(context, configuration = configuration)
    }.getOrElse(DefaultSubsystemFactory.default(None, configuration))
    val subsystem = base
      .withDescriptor(GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("static-web-test.yaml"),
        subsystemName = "static-web-test",
        security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
          localSubject = if (usermode == SubsystemUserMode.Standalone) Some(GenericSubsystemLocalSubjectBinding("static-user")) else None,
          providers = providers.map { provider =>
            GenericSubsystemAuthenticationProviderBinding(
              name = provider.name,
              component = "debug-app",
              enabled = Some(true)
            )
          }
        ))))
      ))
    val identity = SubsystemInstanceId.default(subsystem.name).getOrElse(fail("Subsystem identity is required"))
    val target = CncfConfigurationTarget.SubsystemInstance.create(identity).getOrElse(fail("Subsystem target is required"))
    val candidates = Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]](
      _candidate(CncfConfigurationParameterCatalog.subsystemUserMode, usermode, target)
    ) ++ Vector(
      CncfConfigurationParameterCatalog.webDescriptor,
      CncfConfigurationParameterCatalog.webExecutionLocale,
      CncfConfigurationParameterCatalog.webExecutionTimezone,
      CncfConfigurationParameterCatalog.webExecutionPublicCapabilities,
      CncfConfigurationParameterCatalog.executionLocale,
      CncfConfigurationParameterCatalog.executionTimezone
    ).flatMap(_configured_candidate(_, configuration, target))
    val batch = ConfigurationBindingCandidates.from(candidates).getOrElse(fail("Web candidates are required"))
    val context = CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("Web context is required"))
    val bindings = ConfigurationBindingResolver.resolve(batch, context.generic).getOrElse(fail("Web bindings are required"))
    subsystem.admitRuntimeConfigurationBindingsC(bindings).isSuccess shouldBe true
    subsystem
  }

  private val _declining_authentication_provider = new AuthenticationProvider {
    override val name: String = "static-page-view-authentication"

    override def authenticate(
      request: AuthenticationRequest
    )(using ExecutionContext): Consequence[Option[org.goldenport.cncf.security.AuthenticationResult]] = {
      val _ = request
      Consequence.success(None)
    }
  }

  private def _with_global_runtime[A](
    locale: Locale,
    timezone: ZoneId
  )(body: GlobalRuntimeContext => A): A = {
    val configuration = ResolvedConfiguration(
      Configuration(Map(
        "textus.execution.locale" -> ConfigurationValue.StringValue(locale.toLanguageTag),
        "textus.execution.timezone" -> ConfigurationValue.StringValue(timezone.getId)
      )),
      ConfigurationTrace.empty
    )
    val global = GlobalRuntimeContext.create(
      "static-web-anonymous-multi-user-projection",
      RuntimeConfig.from(configuration),
      configuration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    body(global)
  }

  private def _with_temp_directory[A](prefix: String)(body: Path => A): A = {
    val directory = Files.createTempDirectory(prefix)
    try body(directory)
    finally {
      Using.resource(Files.walk(directory)) { stream =>
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(Files.deleteIfExists(_))
      }
    }
  }

  private def _configured_candidate[A](
    parameter: org.goldenport.configuration.ConfigurationParameter[A],
    configuration: ResolvedConfiguration,
    target: CncfConfigurationTarget.SubsystemInstance
  ): Option[ConfigurationBindingCandidate[A, CncfConfigurationTarget]] =
    configuration.configuration.values.get(parameter.id.value).flatMap { value =>
      parameter.codec.decode(value).toOption.map(_candidate(parameter, _, target))
    }

  private def _candidate[A](
    parameter: org.goldenport.configuration.ConfigurationParameter[A],
    value: A,
    target: CncfConfigurationTarget.SubsystemInstance
  ): ConfigurationBindingCandidate[A, CncfConfigurationTarget] = {
    val provenance = ConfigurationProvenance.create(
      ConfigurationOrigin.Cwd,
      "textus",
      "static-web-integration",
      Some(parameter.id.value),
      Some(parameter.id.value),
      30,
      1,
      Vector("phase-55: gcf09b"),
      false,
      Some("spec")
    ).getOrElse(fail("Web provenance is required"))
    ConfigurationBindingCandidate.create(parameter, target, value, provenance).getOrElse(fail("Web candidate is required"))
  }
}
