package org.goldenport.cncf.http

import io.circe.Json

/*
 * @since   Jun. 19, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WebDemoAssistManifest(
  version: Int,
  entries: Vector[WebDemoAssistManifest.Entry]
) {
  def toJson: Json =
    Json.obj(
      "version" -> Json.fromInt(version),
      "entries" -> Json.fromValues(entries.map(_.toJson))
    )
}

object WebDemoAssistManifest {
  final case class Entry(
    kind: String,
    name: String,
    selector: String,
    scope: Option[String],
    label: Option[String]
  ) {
    def toJson: Json =
      Json.obj(
        "kind" -> Json.fromString(kind),
        "name" -> Json.fromString(name),
        "selector" -> Json.fromString(selector),
        "scope" -> scope.fold(Json.Null)(Json.fromString),
        "label" -> label.fold(Json.Null)(Json.fromString)
      )
  }

  private val _textus_attributes: Vector[String] = Vector(
    "data-textus-page",
    "data-textus-section",
    "data-textus-form",
    "data-textus-field",
    "data-textus-action",
    "data-textus-widget",
    "data-textus-ux-profile",
    "data-textus-validation-summary",
    "data-textus-validation-message",
    "data-textus-validation-field",
    "data-textus-issue-scope",
    "data-textus-empty-state",
    "data-textus-capability"
  )

  private val _tag_pattern =
    "(?is)<([a-z][a-z0-9:-]*)(\\s+[^<>]*?)?>".r
  private val _attribute_pattern =
    """(?is)([a-z_:][-a-z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""".r

  def fromHtml(html: String): WebDemoAssistManifest =
    WebDemoAssistManifest(1, _entries(html))

  private def _entries(html: String): Vector[Entry] = {
    val entries = Vector.newBuilder[Entry]
    var page: Option[String] = None
    var section: Option[String] = None
    var form: Option[String] = None
    _tag_pattern.findAllMatchIn(html).foreach { m =>
      val tag = m.group(1).toLowerCase(java.util.Locale.ROOT)
      val attrs = _attributes(Option(m.group(2)).getOrElse(""))
      val label = _safe_label(tag, html, m.end)
      _textus_attributes.foreach { attr =>
        attrs.get(attr).foreach { value =>
          val entry = Entry(
            _kind(attr),
            value,
            _selector(attr, value),
            _scope(attr, page, section, form),
            label
          )
          entries += entry
          attr match {
            case "data-textus-page" => page = Some(value)
            case "data-textus-section" => section = Some(value)
            case "data-textus-form" => form = Some(value)
            case _ =>
          }
        }
      }
    }
    entries.result()
  }

  private def _attributes(attrs: String): Map[String, String] =
    _attribute_pattern.findAllMatchIn(attrs).map { m =>
      val key = m.group(1).toLowerCase(java.util.Locale.ROOT)
      val value = Option(m.group(3))
        .orElse(Option(m.group(4)))
        .orElse(Option(m.group(5)))
        .getOrElse("")
      key -> _decode_entities(value)
    }.toMap

  private def _kind(attribute: String): String =
    attribute match {
      case "data-textus-page" => "page"
      case "data-textus-section" => "section"
      case "data-textus-form" => "form"
      case "data-textus-field" => "field"
      case "data-textus-action" => "action"
      case "data-textus-widget" => "widget"
      case "data-textus-ux-profile" => "ux-profile"
      case x if x.startsWith("data-textus-validation-") => "validation"
      case x if x.startsWith("data-textus-issue-") => "issue"
      case "data-textus-empty-state" => "empty-state"
      case "data-textus-capability" => "capability"
      case _ => "unknown"
    }

  private def _selector(attribute: String, value: String): String =
    s"""[${attribute}="${_css_attr_value(value)}"]"""

  private def _scope(
    attribute: String,
    page: Option[String],
    section: Option[String],
    form: Option[String]
  ): Option[String] =
    attribute match {
      case "data-textus-page" => None
      case "data-textus-section" => page
      case "data-textus-form" => section.orElse(page)
      case _ => form.orElse(section).orElse(page)
    }

  private def _safe_label(
    tag: String,
    html: String,
    start: Int
  ): Option[String] =
    if (!_label_tag(tag))
      None
    else {
      val text = html
        .slice(start, math.min(html.length, start + 240))
        .takeWhile(_ != '<')
      val normalized = _decode_entities(text).replaceAll("\\s+", " ").trim
      Option.when(normalized.nonEmpty)(normalized.take(120))
    }

  private def _label_tag(tag: String): Boolean =
    tag == "a" ||
      tag == "button" ||
      tag == "summary" ||
      tag == "label" ||
      tag.matches("h[1-6]")

  private def _css_attr_value(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")

  private def _decode_entities(value: String): String =
    value
      .replace("&quot;", "\"")
      .replace("&#34;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")
}
