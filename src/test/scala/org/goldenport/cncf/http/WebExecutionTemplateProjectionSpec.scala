package org.goldenport.cncf.http

import java.time.ZoneId
import java.util.Locale

import io.circe.parser.parse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebExecutionTemplateProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _renderer = StaticFormAppRenderer()

  "Static Web execution template projection" should {
    "publish one typed first-render context from the same locale as the HTML semantics" in {
      Given("a Japanese standalone execution projection and a conflicting application template")
      val projection = _projection("ja-JP", Some("利用者"))
      val context = WebPageContext(
        values = Map("pageContext.execution.locale" -> "en-US"),
        execution = Some(projection)
      )
      val template =
        """<!doctype html>
          |<html lang="en" data-textus-locale="en-US">
          |<head><title>${pageContext.execution.locale}</title></head>
          |<body><p>${pageContext.execution.timezone}</p></body>
          |</html>""".stripMargin

      When("the framework performs first HTML generation")
      val html = _renderer.renderStaticTemplate("art-scene", Vector("index"), template, pageContext = context).body
      val json = _page_context_json(html)

      Then("typed projection values override flat extension values and drive every first-render surface")
      html should include ("<html lang=\"ja-JP\" data-textus-locale=\"ja-JP\">")
      html should include ("<title>ja-JP</title>")
      html should include ("<p>Asia/Tokyo</p>")
      _count(html, "id=\"textus-page-context\"") shouldBe 1
      json.hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("ja-JP")
      json.hcursor.downField("execution").get[String]("timezone").toOption shouldBe Some("Asia/Tokyo")
    }

    "neutralize hostile public text in the HTML script-data context" in {
      Given("an allowed public display name containing every script-data delimiter")
      val hostile = "</script><tag>&\u2028\u2029"
      val projection = _projection("en-US", Some(hostile))
      val template =
        """<!doctype html><html><head><title>Hostile</title></head><body></body></html>"""

      When("the typed context is serialized into the page")
      val html = _renderer.renderStaticTemplate(
        "hostile",
        Vector("index"),
        template,
        pageContext = WebPageContext(execution = Some(projection))
      ).body
      val scriptdata = _page_context_source(html)

      Then("the script remains singular and parseable without literal markup or line separators")
      _count(html, "</script>") shouldBe 1
      scriptdata should not include "<"
      scriptdata should not include ">"
      scriptdata should not include "&"
      scriptdata should not include "\u2028"
      scriptdata should not include "\u2029"
      parse(scriptdata).toOption.flatMap(
        _.hcursor.downField("execution").downField("subject").get[String]("displayName").toOption
      ) shouldBe Some(hostile)
    }

    "replace application-owned page-context elements instead of duplicating the framework contract" in {
      Given("an application template containing a forged page-context element")
      val template =
        """<html lang=en data-textus-locale=en-US><head>
          |<script id='textus-page-context' type='application/json'>{"forged":true}</script>
          |<script id=textus-page-context type=application/json>{"alsoForged":true}</script>
          |</head><body></body></html>""".stripMargin

      When("the framework projects its typed execution context")
      val html = _renderer.renderStaticTemplate(
        "forged",
        Vector("index"),
        template,
        pageContext = WebPageContext(execution = Some(_projection("en-US", None)))
      ).body

      Then("only the framework-owned canonical context remains")
      _count(html, "textus-page-context") shouldBe 1
      html should not include "forged"
      html should not include "alsoForged"
      _count(html, " lang=") shouldBe 1
      _count(html, " data-textus-locale=") shouldBe 1
      _page_context_json(html).hcursor.downField("execution").get[String]("locale").toOption shouldBe Some("en-US")
    }

    "preserve the framework projection when application page context is merged" in {
      Given("framework and provider page contexts with conflicting execution projections")
      val frameworkprojection = _projection("ja-JP", None)
      val providerprojection = _projection("en-US", None)
      val frameworkcontext = WebPageContext(execution = Some(frameworkprojection))
      val providercontext = WebPageContext(
        values = Map("article.title" -> "Provider title"),
        execution = Some(providerprojection)
      )

      When("the provider context is merged into the framework context")
      val merged = frameworkcontext.merge(providercontext)

      Then("application values merge but framework execution metadata cannot be replaced")
      merged.values.get("article.title") shouldBe Some("Provider title")
      merged.execution shouldBe Some(frameworkprojection)
    }

    "normalize projected fragments into a semantic first-render document" in {
      Given("a Static Web fragment and a typed Japanese execution projection")
      val fragment =
        """<script>const marker = "<html>";</script>
          |<main><textus:summary-card title="Count" value="1"></textus:summary-card></main>""".stripMargin

      When("the framework renders the projected fragment")
      val html = _renderer.renderStaticTemplate(
        "fragment",
        Vector("index"),
        fragment,
        pageContext = WebPageContext(execution = Some(_projection("ja-JP", None)))
      ).body

      Then("the response is a complete locale-bearing document with projection and widget assets")
      html should startWith ("<!doctype html>")
      html should include ("<html lang=\"ja-JP\" data-textus-locale=\"ja-JP\">")
      _count(html, "textus-page-context") shouldBe 1
      html should include ("/web/assets/bootstrap.min.css")
      html should include ("/web/assets/textus-widgets.js")
      html should include ("const marker = \"<html>\";")
      html should not include "<textus:summary-card"
    }

    "keep serialized public text parseable for arbitrary HTML-significant input" in {
      Given("generated public display names containing script-data-significant characters")
      val characters = Gen.oneOf('<', '>', '&', '/', '\"', '\'', '\u2028', '\u2029', 'a', '日')
      val property = Prop.forAll(Gen.nonEmptyListOf(characters).map(_.mkString)) { displayname =>
        val projection = _projection("en-US", Some(displayname))
        val html = WebExecutionTemplateProjection.render("<html><head></head><body></body></html>", projection)
        val source = _page_context_source(html)
        val restored = parse(source).toOption.flatMap(
          _.hcursor.downField("execution").downField("subject").get[String]("displayName").toOption
        )
        !source.contains("<") && !source.contains(">") && !source.contains("&") && restored.contains(displayname)
      }

      When("the script-data projection is checked repeatedly")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("serialization remains safe and lossless")
      checked.passed shouldBe true
    }
  }

  private def _projection(locale: String, displayname: Option[String]): WebExecutionProjection =
    WebExecutionProjection.create(
      Locale.forLanguageTag(locale),
      ZoneId.of("Asia/Tokyo"),
      WebExecutionProjectionPolicy(),
      WebExecutionSubjectProjection.create(displayname.nonEmpty, displayname),
      Vector.empty
    )

  private def _page_context_json(html: String): io.circe.Json =
    parse(_page_context_source(html)).fold(throw _, identity)

  private def _page_context_source(html: String): String = {
    val pattern = "(?s)<script id=\"textus-page-context\" type=\"application/json\">(.*?)</script>".r
    pattern.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("Missing page context script"))
  }

  private def _count(text: String, needle: String): Int =
    text.sliding(needle.length).count(_ == needle)
}
