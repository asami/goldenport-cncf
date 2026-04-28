package org.goldenport.cncf.html

import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.Consequence

/*
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class HtmlTreeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "HtmlTree" should {
    "parse full HTML and extract head metadata" in {
      Given("a full HTML document with title, description, and canonical link")
      val html =
        """<!doctype html>
          |<html>
          |<head>
          |  <title>HTML title</title>
          |  <meta name="description" content="HTML description">
          |  <link rel="canonical" href="/blog/html-title">
          |</head>
          |<body><article><p>Hello</p></article></body>
          |</html>""".stripMargin

      When("parsing the document")
      val document = _success(HtmlTree.parse(html))

      Then("the metadata is available as structured values")
      document.title shouldBe Some("HTML title")
      document.description shouldBe Some("HTML description")
      document.canonical shouldBe Some("/blog/html-title")
    }

    "select the configured article element before generic article elements" in {
      Given("a document with multiple article candidates")
      val html =
        """<html><body>
          |  <article><p>teaser</p></article>
          |  <main><article data-blog-content><p>body</p><img src="./images/a.jpg" alt="A"></article></main>
          |</body></html>""".stripMargin

      When("extracting the article fragment")
      val fragment = _success(HtmlTree.parse(html).flatMap(_.articleFragment))

      Then("the data-blog-content article is selected")
      fragment.render should include ("<p>body</p>")
      fragment.render should not include ("teaser")
      fragment.images.map(_.src) shouldBe Vector("./images/a.jpg")
      fragment.images.map(_.alt) shouldBe Vector(Some("A"))
    }

    "rewrite image sources in an extracted fragment" in {
      Given("an article fragment with two image tags")
      val html =
        """<html><body>
          |  <article>
          |    <p>body</p>
          |    <img src="images/a.jpg" alt="A">
          |    <img src="images/b.jpg" title="B">
          |  </article>
          |</body></html>""".stripMargin
      val fragment = _success(HtmlTree.parse(html).flatMap(_.articleFragment))

      When("rewriting each image source")
      val rewritten = fragment.rewriteImageSources { occurrence =>
        Some(s"/web/blob/content/${occurrence.index}")
      }

      Then("the rendered fragment contains the rewritten URLs and keeps metadata")
      rewritten.images.map(_.src) shouldBe Vector("/web/blob/content/0", "/web/blob/content/1")
      rewritten.render should include ("""alt="A"""")
      rewritten.render should include ("""title="B"""")
    }

    "decode common HTML entities and render them once" in {
      Given("an article containing escaped text and attributes")
      val html =
        """<html><body>
          |  <article><p>Tom &amp; Jerry &lt;Archive&gt;</p><img src="a&amp;b.jpg" alt="A &amp; B"></article>
          |</body></html>""".stripMargin

      When("extracting and rendering the article fragment")
      val fragment = _success(HtmlTree.parse(html).flatMap(_.articleFragment))

      Then("metadata inspection sees decoded values and rendering does not double escape")
      fragment.images.map(_.src) shouldBe Vector("a&b.jpg")
      fragment.images.map(_.alt) shouldBe Vector(Some("A & B"))
      fragment.render should include ("Tom &amp; Jerry &lt;Archive&gt;")
      fragment.render should include ("""alt="A &amp; B"""")
      fragment.render should include ("""src="a&amp;b.jpg"""")
      fragment.render should not include ("&amp;amp;")
    }

    "fail deterministically when no article is available" in {
      Given("a document without an article element")
      val document = _success(HtmlTree.parse("<html><body><p>No article</p></body></html>"))

      When("extracting an article")
      val result = document.articleFragment

      Then("a validation failure is returned")
      result shouldBe a[Consequence.Failure[_]]
    }

    "return a deterministic failure when parseFile cannot read the path" in {
      Given("a missing HTML file")
      val path = Paths.get("/tmp/cncf-html-tree-missing-file.html")

      When("parsing the missing file")
      val result = HtmlTree.parseFile(path)

      Then("the error is represented as Consequence failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _success[A](p: Consequence[A]): A = p match {
    case Consequence.Success(value) => value
    case m: Consequence.Failure[_] => fail(m.toString)
  }
}
