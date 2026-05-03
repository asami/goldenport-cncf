package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.MimeType
import org.goldenport.value.{ContentAttributes, ContentBody, ContentMarkup}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class ContentRenderWorkflowSpec extends AnyWordSpec with Matchers {
  "ContentRenderWorkflow" should {
    "wrap stored HTML fragments in a content article" in {
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentRenderWorkflow(_blob_component(InMemoryBlobStore()))
      val content = ContentAttributes(
        content = Some(ContentBody("<p>Hello</p>")),
        mimeType = Some(MimeType.TEXT_HTML),
        charset = Some(StandardCharsets.UTF_8),
        markup = Some(ContentMarkup.HtmlFragment)
      )

      val result = _success(workflow.renderHtml(content))

      result.markup shouldBe ContentMarkup.HtmlFragment
      result.html shouldBe """<article class="textus-content"><p>Hello</p></article>"""
    }

    "render GFM Markdown tables to HTML before wrapping" in {
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentRenderWorkflow(_blob_component(InMemoryBlobStore()))
      val content = ContentAttributes(
        content = Some(ContentBody("| A | B |\n|---|---|\n| 1 | 2 |")),
        mimeType = Some(MimeType("text/markdown")),
        charset = Some(StandardCharsets.UTF_8),
        markup = Some(ContentMarkup.MarkdownGfm)
      )

      val result = _success(workflow.renderHtml(content))

      result.markup shouldBe ContentMarkup.MarkdownGfm
      result.html should include ("""<article class="textus-content">""")
      result.html should include ("<table>")
      result.html should include ("<td>1</td>")
    }

    "render SmartDox content through the simplemodeling-lib parser" in {
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentRenderWorkflow(_blob_component(InMemoryBlobStore()))
      val content = ContentAttributes(
        content = Some(ContentBody("# Title\n\nSmartDox *bold* text.")),
        markup = Some(ContentMarkup.SmartDox)
      )

      val result = _success(workflow.renderHtml(content))

      result.markup shouldBe ContentMarkup.SmartDox
      result.html should include ("""<article class="textus-content">""")
      result.html should include ("<h1>Title</h1>")
      result.html should include ("<strong>bold</strong>")
    }
  }

  private def _blob_component(
    store: BlobStore,
    maxsize: Long = 1024 * 1024
  ): Component = {
    val subsystem = DefaultSubsystemFactory.default(Some("command"))
    val component = subsystem.findComponent("blob").getOrElse(fail("missing Blob component"))
    component.withPort(Component.Port.of(new _BlobService(store, maxsize)))
    component
  }

  private final class _BlobService(
    store: BlobStore,
    maxsize: Long
  ) extends BlobComponent.BlobService {
    def blobStore: BlobStore = store
    def maxByteSize: Long = maxsize
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(c) => fail(c.toString)
    }
}
