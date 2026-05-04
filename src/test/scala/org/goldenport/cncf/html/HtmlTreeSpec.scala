package org.goldenport.cncf.html

import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.Consequence

/*
 * @since   Apr. 29, 2026
 * @version May.  5, 2026
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

    "extract and rewrite media and download references in document order" in {
      Given("an article fragment with video, source, image, and download references")
      val html =
        """<html><body>
          |  <article>
          |    <video src="videos/a.mp4" title="Video"></video>
          |    <video><source src="videos/b.webm" type="video/webm"></video>
          |    <picture><source src="images/a.webp" type="image/webp"><img src="images/a.png" alt="A"></picture>
          |    <a href="docs/a.pdf" download title="PDF">Download</a>
          |  </article>
          |</body></html>""".stripMargin
      val fragment = _success(HtmlTree.parse(html).flatMap(_.articleFragment))

      When("extracting generalized attribute references")
      val refs = fragment.references
      val rewritten = fragment.rewriteAttributeReferencesWithComments { ref =>
        if (ref.elementKind == "a")
          HtmlAttributeReferenceRewrite(Some("urn:textus:attachment:pdf"))
        else
          HtmlAttributeReferenceRewrite(Some(s"urn:textus:${ref.elementKind}:${ref.index}"))
      }

      Then("references preserve element metadata and can be rewritten")
      refs.map(x => (x.elementKind, x.attributeName, x.value, x.parentElementKind, x.mediaType, x.download.isDefined)) shouldBe Vector(
        ("video", "src", "videos/a.mp4", None, None, false),
        ("source", "src", "videos/b.webm", Some("video"), Some("video/webm"), false),
        ("source", "src", "images/a.webp", Some("picture"), Some("image/webp"), false),
        ("img", "src", "images/a.png", Some("picture"), None, false),
        ("a", "href", "docs/a.pdf", None, None, true)
      )
      refs.last.label shouldBe Some("Download")
      rewritten.render should include ("""src="urn:textus:video:0"""")
      rewritten.render should include ("""src="urn:textus:source:1"""")
      rewritten.render should include ("""href="urn:textus:attachment:pdf"""")
    }

    "rewrite nested references using the same pre-order indexes as extraction" in {
      Given("an anchor wrapping an inline image")
      val html =
        """<html><body>
          |  <article><a href="docs/a.pdf"><img src="images/a.png" alt="A">Download</a></article>
          |</body></html>""".stripMargin
      val fragment = _success(HtmlTree.parse(html).flatMap(_.articleFragment))

      When("extracting and rewriting generalized references")
      val refs = fragment.references
      val rewritten = fragment.rewriteAttributeReferencesWithComments { ref =>
        ref.elementKind match {
          case "a" => HtmlAttributeReferenceRewrite(Some(s"urn:textus:attachment:${ref.index}"))
          case "img" => HtmlAttributeReferenceRewrite(Some(s"urn:textus:image:${ref.index}"))
          case _ => HtmlAttributeReferenceRewrite(None)
        }
      }

      Then("the parent link and child image keep their extracted indexes")
      refs.map(x => (x.index, x.elementKind, x.attributeName, x.value, x.parentElementKind)) shouldBe Vector(
        (0, "a", "href", "docs/a.pdf", None),
        (1, "img", "src", "images/a.png", Some("a"))
      )
      rewritten.render should include ("""href="urn:textus:attachment:0"""")
      rewritten.render should include ("""src="urn:textus:image:1"""")
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
