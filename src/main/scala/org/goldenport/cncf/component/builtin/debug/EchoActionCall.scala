package org.goldenport.cncf.component.builtin.debug

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.ActionCall
import org.goldenport.datatype.MimeBody
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpRequest, HttpStatus}
import org.goldenport.protocol.{Argument, Request, Switch}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.text.Presentable

/*
 * @since   Jan. 21, 2026
 * @version Jan. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EchoActionCall(
  core: ActionCall.Core,
  request: Request,
  methodOverride: Option[String] = None
) extends ActionCall {
  override def execute(): Consequence[OperationResponse] =
    response_yaml(_build_yaml())

  private def _build_yaml(): String = {
    val builder = new StringBuilder
    _append_cncf_section(builder)
    request.source match {
      case Some(Request.Source.Http(httpRequest)) =>
        _append_http_section(builder, httpRequest)
      case _ =>
        builder.append("http: null\n")
    }
    builder.toString()
  }

  private def _append_cncf_section(sb: StringBuilder): Unit = {
    val indent = "  "
    sb.append("cncf:\n")
    _append_scalar(sb, indent, "component", request.component)
    _append_scalar(sb, indent, "service", request.service)
    _append_scalar(sb, indent, "operation", Some(request.operation))
    _append_arguments_section(sb, indent)
    _append_switches_section(sb, indent)
    _append_properties_section(sb, indent)
  }

  private def _append_scalar(
    sb: StringBuilder,
    indent: String,
    name: String,
    value: Option[String]
  ): Unit = {
    val rendered = value.fold("null")(_quote)
    sb.append(s"$indent$name: $rendered\n")
  }

  private def _append_arguments_section(sb: StringBuilder, indent: String): Unit = {
    if (request.arguments.isEmpty) {
      sb.append(s"${indent}arguments: []\n")
    } else {
      sb.append(s"${indent}arguments:\n")
      request.arguments.foreach { arg =>
        sb.append(s"$indent  - name: ${_quote(arg.name)}\n")
        sb.append(s"$indent    value: ${_quote(_render_value(arg.value))}\n")
      }
    }
  }

  private def _append_switches_section(sb: StringBuilder, indent: String): Unit = {
    if (request.switches.isEmpty) {
      sb.append(s"${indent}switches: []\n")
    } else {
      sb.append(s"${indent}switches:\n")
      request.switches.foreach { sw =>
        sb.append(s"$indent  - name: ${_quote(sw.name)}\n")
        sb.append(s"$indent    value: ${sw.value}\n")
      }
    }
  }

  private def _append_properties_section(sb: StringBuilder, indent: String): Unit = {
    if (request.properties.isEmpty) {
      sb.append(s"${indent}properties: []\n")
    } else {
      sb.append(s"${indent}properties:\n")
      request.properties.foreach { prop =>
        sb.append(s"$indent  - name: ${_quote(prop.name)}\n")
        sb.append(s"$indent    value: ${_quote(_render_value(prop.value))}\n")
      }
    }
  }

  private def _append_http_section(sb: StringBuilder, http: HttpRequest): Unit = {
    val indent = "  "
    sb.append("http:\n")
    _append_scalar(sb, indent, "method", Some(_resolve_method(http)))
    _append_scalar(sb, indent, "path", Some(http.path.asString))
    _append_record_map(sb, indent, "query", http.query)
    _append_record_map(sb, indent, "headers", http.header)
    _append_body_section(sb, indent, http)
  }

  private def _append_record_map(
    sb: StringBuilder,
    indent: String,
    name: String,
    record: Record
  ): Unit = {
    val entries = record.asNameStringVector.sortBy(_._1)
    if (entries.isEmpty) {
      sb.append(s"$indent$name: {}\n")
    } else {
      sb.append(s"$indent$name:\n")
      entries.foreach { case (key, value) =>
        sb.append(s"$indent  $key: ${_quote(value)}\n")
      }
    }
  }

  private def _append_body_section(sb: StringBuilder, indent: String, http: HttpRequest): Unit = {
    val details = _http_body_details(http)
    sb.append(s"${indent}body:\n")
    sb.append(s"$indent  present: ${details.present}\n")
    sb.append(s"$indent  contentType: ${details.contentType.fold("null")(_quote)}\n")
    sb.append(s"$indent  size: ${details.size.fold("null")(_.toString)}\n")
    sb.append(s"$indent  preview: ${details.preview.fold("null")(_quote)}\n")
  }

  private def _http_body_details(http: HttpRequest): HttpBodyDetails =
    http.body match {
      case Some(bag) =>
        val bytes = _read_bag(bag)
        HttpBodyDetails(
          present = true,
          contentType = _find_content_type(http),
          size = Some(bytes.length.toLong),
          preview = Some(Base64.getEncoder.encodeToString(bytes.take(256)))
        )
      case None =>
        HttpBodyDetails(
          present = false,
          contentType = None,
          size = None,
          preview = None
        )
    }

  private case class HttpBodyDetails(
    present: Boolean,
    contentType: Option[String],
    size: Option[Long],
    preview: Option[String]
  )

  private def _find_content_type(http: HttpRequest): Option[String] =
    http.header.asMap.collectFirst {
      case (key, value) if key.equalsIgnoreCase("content-type") => value.toString
    }

  private def _read_bag(bag: Bag): Array[Byte] = {
    val in = bag.openInputStream()
    try {
      in.readAllBytes()
    } finally {
      in.close()
    }
  }

  private def _render_value(value: Any): String =
    value match {
      case mb: MimeBody => Presentable.print(mb)
      case other => Presentable.print(other)
    }

  private def _quote(value: String): String =
    s""""${_escape(value)}""""

  private def _escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n")

  private def _resolve_method(http: HttpRequest): String =
    methodOverride.map(_.toUpperCase).getOrElse(http.method.name)
}
