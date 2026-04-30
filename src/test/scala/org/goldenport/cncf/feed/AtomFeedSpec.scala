package org.goldenport.cncf.feed

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class AtomFeedSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AtomFeed" should {
    "render Atom XML with feed and entry fields" in {
      val feed = AtomFeed(
        id = "https://example.test/blog/atom",
        title = "Blog",
        updated = Instant.parse("2026-04-30T10:00:00Z"),
        links = Vector(AtomLink("https://example.test/blog/atom", Some("self"), Some("application/atom+xml"))),
        entries = Vector(
          AtomEntry(
            id = "https://example.test/blog/phase-19",
            title = "Phase 19",
            updated = Instant.parse("2026-04-30T10:00:00Z"),
            links = Vector(AtomLink("https://example.test/blog/phase-19", Some("alternate"), Some("text/html"))),
            contentHtml = Some("""<article><p>Body & image</p></article>""")
          )
        )
      )

      val xml = AtomFeedRenderer.render(feed)

      xml should include ("""xmlns="http://www.w3.org/2005/Atom"""")
      xml should include ("<title>Blog</title>")
      xml should include ("""href="https://example.test/blog/atom"""")
      xml should include ("""type="html"""")
      xml should include ("&lt;article&gt;&lt;p&gt;Body &amp; image&lt;/p&gt;&lt;/article&gt;")
    }

    "project records into Atom entries with URL and updated fallback rules" in {
      val config = AtomFeedProjection.Config(
        title = "Blog",
        baseUrl = "https://example.test",
        selfPath = "/blog/atom",
        entryPathPrefix = "/blog"
      )
      val rows = Vector(
        Record.dataAuto(
          "id" -> "2",
          "slug" -> "newer",
          "title" -> "Newer",
          "content" -> "<article>Newer</article>",
          "updated_at" -> "2026-04-30T12:00:00Z",
          "created_at" -> "2026-04-30T11:00:00Z"
        ),
        Record.dataAuto(
          "id" -> "1",
          "slug" -> "older",
          "title" -> "Older",
          "content" -> "<article>Older</article>",
          "created_at" -> "2026-04-29T10:00:00Z"
        )
      )

      val feed = _success(AtomFeedProjection.project(config, rows))

      feed.id shouldBe "https://example.test/blog/atom"
      feed.updated shouldBe Instant.parse("2026-04-30T12:00:00Z")
      feed.entries.map(_.id) shouldBe Vector(
        "https://example.test/blog/newer",
        "https://example.test/blog/older"
      )
      feed.entries.last.updated shouldBe Instant.parse("2026-04-29T10:00:00Z")
    }

    "order records by updated then created then id" in {
      val config = AtomFeedProjection.Config(
        title = "Blog",
        baseUrl = "https://example.test",
        selfPath = "/blog/atom",
        entryPathPrefix = "/blog"
      )
      val rows = Vector(
        Record.dataAuto(
          "id" -> "b",
          "slug" -> "older-created",
          "title" -> "Older created",
          "updated_at" -> "2026-04-30T12:00:00Z",
          "created_at" -> "2026-04-30T10:00:00Z"
        ),
        Record.dataAuto(
          "id" -> "a",
          "slug" -> "newer-created",
          "title" -> "Newer created",
          "updated_at" -> "2026-04-30T12:00:00Z",
          "created_at" -> "2026-04-30T11:00:00Z"
        )
      )

      val feed = _success(AtomFeedProjection.project(config, rows))

      feed.entries.map(_.id) shouldBe Vector(
        "https://example.test/blog/newer-created",
        "https://example.test/blog/older-created"
      )
    }

    "format HTTP operation responses without dropping body or content type" in {
      val http = HttpResponse.Text(
        HttpStatus.Ok,
        ContentType.parse("application/atom+xml; charset=utf-8"),
        Bag.text("<feed/>")
      )

      val response = OperationResponseFormatter.toResponse(
        Request.of("blog", "blog", "atomFeed"),
        OperationResponse.Http(http),
        RunMode.Command
      )

      response shouldBe a[Response.Content]
      response.contentType.header shouldBe "application/atom+xml; charset=UTF-8"
      response.print shouldBe "<feed/>"
    }

    "fail deterministically when site base URL is missing" in {
      val request = Request.of("blog", "blog", "atomFeed")

      AtomFeedProjection.resolveSiteBaseUrl(
        request,
        org.goldenport.cncf.config.ResolvedParameters.empty()
      ) shouldBe a[Consequence.Failure[?]]
    }

    "resolve site base URL from request properties" in {
      val request = Request.of("blog", "blog", "atomFeed").copy(
        properties = List(Property("cncf.site.base-url", "https://example.test/", None))
      )

      _success(AtomFeedProjection.resolveSiteBaseUrl(
        request,
        org.goldenport.cncf.config.ResolvedParameters.empty()
      )) shouldBe "https://example.test"
    }

    "fail deterministically when entry timestamps are missing" in {
      val config = AtomFeedProjection.Config("Blog", "https://example.test", "/blog/atom", "/blog")

      AtomFeedProjection.project(
        config,
        Vector(Record.dataAuto("slug" -> "missing-time", "title" -> "Missing time"))
      ) shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(v) => v
      case Consequence.Failure(c) => fail(s"unexpected failure: $c")
    }
}
