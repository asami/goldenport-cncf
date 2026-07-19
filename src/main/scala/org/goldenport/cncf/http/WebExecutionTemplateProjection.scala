package org.goldenport.cncf.http

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder

/*
 * @since   Jul. 17, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object WebExecutionTemplateProjection {
  val PAGE_CONTEXT_ELEMENT_ID = "textus-page-context"

  private val _html_open_pattern = "(?is)<html\\b([^>]*)>".r
  private val _html_document_pattern =
    "(?is)^\\s*(?:<!doctype\\s+html[^>]*>\\s*)?<html\\b".r
  private val _head_open_pattern = "(?is)<head\\b[^>]*>".r
  private val _head_close_pattern = "(?is)</head\\s*>".r
  private val _script_open_pattern = "(?is)<script\\b".r
  private val _existing_context_pattern =
    "(?is)<script\\b(?=[^>]*\\bid\\s*=\\s*(?:(['\"])textus-page-context\\1|textus-page-context(?=\\s|>)))[^>]*>.*?</script\\s*>".r
  private val _lang_attribute_pattern =
    "(?is)\\s+lang\\s*=\\s*(?:(['\"])[^'\"]*\\1|[^\\s>]+)".r
  private val _locale_attribute_pattern =
    "(?is)\\s+data-textus-locale\\s*=\\s*(?:(['\"])[^'\"]*\\1|[^\\s>]+)".r

  def render(html: String, projection: WebExecutionProjection): String = {
    render(html, WebPageContext(execution = Some(projection)))
  }

  def render(html: String, context: WebPageContext): String =
    context.execution.fold(html) { projection =>
    val withoutcontext = _existing_context_pattern.replaceAllIn(html, "")
    val script = _page_context_script(projection, context.view)
    _html_document_pattern.findPrefixOf(withoutcontext) match {
      case Some(_) =>
        val withattributes = _set_html_attributes(withoutcontext, projection.locale)
        _head_open_pattern.findFirstMatchIn(withattributes) match {
          case Some(m) =>
            _insert_page_context_in_head(withattributes, m, script)
          case None =>
            _html_open_pattern.findFirstMatchIn(withattributes).map { m =>
              withattributes.substring(0, m.end) + "\n" + script + withattributes.substring(m.end)
            }.getOrElse(withattributes)
        }
      case None =>
        val escapedlocale = _escape_attribute(projection.locale)
        s"""<!doctype html>
           |<html lang="${escapedlocale}" data-textus-locale="${escapedlocale}">
           |<head>${script}</head>
           |<body>${withoutcontext}</body>
           |</html>""".stripMargin
    }
    }

  private def _insert_page_context_in_head(
    html: String,
    headopen: scala.util.matching.Regex.Match,
    script: String
  ): String = {
    val afterhead = html.substring(headopen.end)
    val insertion = _head_close_pattern.findFirstMatchIn(afterhead) match {
      case Some(headclose) =>
        val headcontent = afterhead.substring(0, headclose.start)
        _script_open_pattern.findFirstMatchIn(headcontent)
          .map(headscript => headopen.end + headscript.start)
          .getOrElse(headopen.end + headclose.start)
      case None =>
        headopen.end
    }
    html.substring(0, insertion) + script + "\n" + html.substring(insertion)
  }

  private def _set_html_attributes(html: String, locale: String): String =
    _html_open_pattern.findFirstMatchIn(html).map { m =>
      val attributes = _locale_attribute_pattern.replaceAllIn(
        _lang_attribute_pattern.replaceAllIn(m.group(1), ""),
        ""
      )
      val escapedlocale = _escape_attribute(locale)
      val replacement = s"<html${attributes} lang=\"${escapedlocale}\" data-textus-locale=\"${escapedlocale}\">"
      html.substring(0, m.start) + replacement + html.substring(m.end)
    }.getOrElse(html)

  private def _page_context_script(projection: WebExecutionProjection, view: Record): String = {
    val json = _escape_script_data(Json.obj(
      "execution" -> projection.toJson,
      "view" -> _record_json(view)
    ).noSpaces)
    s"<script id=\"${PAGE_CONTEXT_ELEMENT_ID}\" type=\"application/json\">${json}</script>"
  }

  private def _record_json(record: Record): Json =
    parse(RecordEncoder.json(record)).fold(
      error => throw new IllegalArgumentException("Unable to encode Static Web page view", error),
      identity
    )

  private def _escape_script_data(value: String): String =
    value
      .replace("&", "\\u0026")
      .replace("<", "\\u003c")
      .replace(">", "\\u003e")
      .replace("\u2028", "\\u2028")
      .replace("\u2029", "\\u2029")

  private def _escape_attribute(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("\"", "&quot;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
}
