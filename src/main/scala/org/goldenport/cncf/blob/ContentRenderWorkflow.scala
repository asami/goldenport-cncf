package org.goldenport.cncf.blob

import java.util.Arrays
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.value.{ContentAttributes, ContentMarkup}

/*
 * Render SimpleEntity ContentAttributes to browser-facing HTML.
 *
 * CT-01 supports stored HTML fragments and GFM-compatible Markdown. SmartDox is
 * reserved for the later SmartDox Textus profile slice.
 *
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ContentRenderResult(
  html: String,
  markup: ContentMarkup
)

final class ContentRenderWorkflow(
  component: Component,
  blobWorkflow: Option[BlobInlineImageWorkflow] = None
) {
  private lazy val _blob_workflow =
    blobWorkflow.getOrElse(BlobInlineImageWorkflow(component))

  def renderHtml(
    content: ContentAttributes
  )(using ExecutionContext): Consequence[ContentRenderResult] = {
    val markup = _markup(content)
    val text = content.contentText.getOrElse("")
    markup match {
      case ContentMarkup.HtmlFragment =>
        _render_fragment(text).map(ContentRenderResult(_, markup))
      case ContentMarkup.MarkdownGfm =>
        _render_markdown(text).flatMap(_render_fragment).map(ContentRenderResult(_, markup))
      case ContentMarkup.SmartDox =>
        Consequence.operationInvalid("SmartDox content rendering is reserved for the SmartDox Textus profile slice")
    }
  }

  private def _render_fragment(
    html: String
  )(using ExecutionContext): Consequence[String] =
    _blob_workflow.renderTextusBlobUrns(html).map { rendered =>
      s"""<article class="textus-content">$rendered</article>"""
    }

  private def _render_markdown(
    markdown: String
  ): Consequence[String] =
    Consequence.success(ContentRenderWorkflow.markdownToHtml(markdown))

  private def _markup(content: ContentAttributes): ContentMarkup =
    content.markup.orElse {
      val mime = content.mimeType.map(_.value.toLowerCase(java.util.Locale.ROOT))
      mime.collect {
        case x if x == "text/markdown" || x == "text/x-markdown" => ContentMarkup.MarkdownGfm
        case x if x == "text/html" || x == "application/xhtml+xml" => ContentMarkup.HtmlFragment
      }
    }.getOrElse(ContentMarkup.HtmlFragment)
}

object ContentRenderWorkflow {
  private lazy val _markdown_options = {
    val options = new MutableDataSet()
    options.set(
      Parser.EXTENSIONS,
      Arrays.asList(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListExtension.create()
      )
    )
    options
  }

  private lazy val _parser =
    Parser.builder(_markdown_options).build()

  private lazy val _renderer =
    HtmlRenderer.builder(_markdown_options).build()

  def markdownToHtml(markdown: String): String =
    _renderer.render(_parser.parse(markdown))
}
