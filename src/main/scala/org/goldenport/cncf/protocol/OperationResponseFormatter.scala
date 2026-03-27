package org.goldenport.cncf.protocol

import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeDefaults

/*
 * @since   Mar. 13, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationResponseFormatter {
  private val _supported_formats = Set("json", "yaml", "text")

  def toResponse(
    request: Request,
    response: OperationResponse,
    mode: RunMode
  ): Response =
    response match {
      case OperationResponse.RecordResponse(record) =>
        _record_response(_resolve_format(request, mode), record)
      case _ =>
        response.toResponse
    }

  private def _record_response(
    format: String,
    record: Record
  ): Response =
    format match {
      case "json" =>
        Response.Json(RecordEncoder.json(record))
      case "yaml" =>
        Response.Yaml(RecordEncoder.yaml(record))
      case "text" =>
        Response.Scalar(record.print)
      case _ =>
        Response.Yaml(RecordEncoder.yaml(record))
    }

  private def _resolve_format(
    request: Request,
    mode: RunMode
  ): String = {
    val fromrequest = request.properties.reverseIterator.collectFirst {
      case prop if prop.name.equalsIgnoreCase("cncf.format") =>
        Option(prop.value).map(_.toString.trim.toLowerCase).getOrElse("")
    }.filter(_.nonEmpty)
    fromrequest match {
      case Some(value) if _supported_formats.contains(value) =>
        value
      case Some(_) =>
        "yaml"
      case None =>
        RuntimeDefaults.defaultFormat(mode)
    }
  }
}
