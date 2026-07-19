package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files

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
 * @version Jul. 19, 2026
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
        """<!doctype html><html lang="en"><head><title>Debug</title></head><body><main id="application"><textus:line-list source="pageContext.view.items" columns="title,status"></textus:line-list></main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString),
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
      html should include ("<main id=\"application\">")
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
        """<!doctype html><html lang="en"><head><title>Debug</title></head><body><main id="application">Ready</main></body></html>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.WebDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
        )),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
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
      html should include ("<html lang=\"und\" data-textus-locale=\"und\">")
      pagecontext.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("und")
    }
  }

  private def _page_context(html: String): io.circe.Json = {
    val pattern = "(?s)<script id=\"textus-page-context\" type=\"application/json\">(.*?)</script>".r
    val source = pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("Missing page context"))
    parse(source).fold(throw _, identity)
  }

  private final class StaticPageViewComponent extends Component {
    override def webPageContextProviders: Vector[WebPageContextProvider] =
      Vector(new WebPageContextProvider {
        override def resolve(
          request: WebPageContextRequest
        )(using ExecutionContext): Consequence[WebPageContext] =
          if (request.app == "debug-app" && request.page.isEmpty)
            Consequence.success(WebPageContext(view = Record.data(
              "items" -> Vector(Record.data("title" -> "展示A", "status" -> "開催中"))
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
