package org.goldenport.cncf.http

import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class WebDemoAssistManifestSpec extends AnyWordSpec with Matchers {
  "WebDemoAssistManifest" should {
    "extract semantic selectors without copying raw values" in {
      val html =
        """<article data-textus-page="static-form-operation" data-textus-ux-profile="bootstrap">
          |  <section data-textus-section="form-controls">
          |    <form data-textus-form="debug.http.echo">
          |      <input type="hidden" name="token" value="secret-token" data-textus-field="token">
          |      <div data-textus-field="body"><input name="body" value="secret-body"></div>
          |      <button data-textus-action="submit">Run</button>
          |      <ul data-textus-widget="textus:line-list"></ul>
          |      <div data-textus-validation-summary="form" data-textus-issue-scope="form">Invalid</div>
          |      <div data-textus-empty-state="true">Nothing here</div>
          |      <a data-textus-capability="book:edit" data-textus-capability-policy="authenticated">Edit</a>
          |    </form>
          |  </section>
          |</article>""".stripMargin

      val json = WebDemoAssistManifest.fromHtml(html).toJson
      val text = json.noSpaces
      val entries = json.hcursor.downField("entries").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val kinds = entries.flatMap(_.hcursor.get[String]("kind").toOption).toSet
      val selectors = entries.flatMap(_.hcursor.get[String]("selector").toOption).toSet

      json.hcursor.get[Int]("version") shouldBe Right(1)
      kinds should contain allOf (
        "page",
        "section",
        "form",
        "field",
        "action",
        "widget",
        "validation",
        "issue",
        "empty-state",
        "capability",
        "ux-profile"
      )
      selectors should contain ("""[data-textus-widget="textus:line-list"]""")
      selectors should contain ("""[data-textus-validation-summary="form"]""")
      selectors should contain ("""[data-textus-capability="book:edit"]""")
      text should not include ("secret-token")
      text should not include ("secret-body")
      text should not include ("<article")
      parse(text).isRight shouldBe true
    }
  }
}
