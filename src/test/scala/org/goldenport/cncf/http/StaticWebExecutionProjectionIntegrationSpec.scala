package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.goldenport.protocol.Protocol
import org.http4s.{Header, Method, Request, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * @since   Jul. 17, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticWebExecutionProjectionIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Static Web runtime execution projection" should {
    "project configured standalone execution state before the first application render" in {
      Given("a Japanese standalone Static Web app and a conflicting English request language")
      val root = Files.createTempDirectory("static-web-execution-projection-")
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
          WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> ConfigurationValue.StringValue("standalone"),
          WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("ja-JP"),
          WebExecutionResolutionPolicy.TIMEZONE_KEY -> ConfigurationValue.StringValue("Asia/Tokyo"),
          WebExecutionResolutionPolicy.PUBLIC_CAPABILITIES_KEY -> ConfigurationValue.StringValue("debug:read")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
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
      pagecontext.hcursor.downField("execution").downField("subject").get[Boolean]("authenticated").toOption shouldBe Some(false)
      pagecontext.hcursor.downField("execution").get[Vector[String]]("capabilities").toOption shouldBe Some(Vector.empty)
      pagecontext.hcursor.downField("view").downField("items").downArray.get[String]("title").toOption shouldBe Some("展示A")
    }

    "ignore arbitrary request formatting headers when display override is disabled" in {
      Given("a Static Web app whose execution runtime owns the default locale")
      val root = Files.createTempDirectory("static-web-execution-header-policy-")
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
          WebExecutionResolutionPolicy.LOCALE_KEY -> ConfigurationValue.StringValue("en-US")
        )),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
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

    "pass page query values to component page-context providers" in {
      Given("a Static Web page request with application filter values")
      val root = Files.createTempDirectory("static-web-page-query-context-")
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
          RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        )),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      subsystem.add(_static_page_view_component(subsystem))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
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

    "keep unrelated runtime messages out of the selected locale catalog" in {
      Given("Japanese application catalogs and an English runtime message map")
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

  private def _page_context(html: String): io.circe.Json = {
    val pattern = "(?s)<script id=\"textus-page-context\" type=\"application/json\">(.*?)</script>".r
    val source = pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("Missing page context"))
    parse(source).fold(throw _, identity)
  }

  private final class StaticPageViewComponent extends Component {
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
              "query" -> Record.data(request.values.toSeq*)
            )))
          else
            Consequence.success(WebPageContext.empty)
      })
  }

  private def _static_page_view_component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem
  ): Component = {
    val id = ComponentId("static_page_view")
    new StaticPageViewComponent().initialize(ComponentInit(
      subsystem,
      Component.Core.create("static_page_view", id, ComponentInstanceId.default(id), Protocol.empty),
      ComponentOrigin.Main
    ))
  }
}
