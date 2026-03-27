package org.goldenport.cncf.protocol

import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.config.ConfigurationAccess

/*
 * @since   Mar. 13, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationResponseFormatter {
  private val _supported_formats = Set("json", "yaml", "text")
  private val _supported_shapes = Set("data", "envelope")

  def toResponse(
    request: Request,
    response: OperationResponse,
    mode: RunMode
  ): Response = {
    val format = _resolve_format(request, mode)
    val shape = _resolve_shape(request)
    response match {
      case OperationResponse.RecordResponse(record) =>
        val payload =
          if (shape == "envelope") _envelope_record(request, _execution_record_for_record, record)
          else record
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] if shape == "envelope" =>
        val payload = _envelope_scalar(request, scalar.print)
        _record_response(_structured_format(format, shape), payload)
      case _ =>
        response.toResponse
    }
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
      case prop if
          prop.name.equalsIgnoreCase("textus.format") ||
          prop.name.equalsIgnoreCase("textus.output.format") ||
          prop.name.equalsIgnoreCase("cncf.format") ||
          prop.name.equalsIgnoreCase("cncf.output.format") =>
        Option(prop.value).map(_.toString.trim.toLowerCase).getOrElse("")
    }.filter(_.nonEmpty)
    val fromconfig = _configuration_string("textus.format")
      .orElse(_configuration_string("textus.output.format"))
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
    fromrequest.orElse(fromconfig) match {
      case Some(value) if _supported_formats.contains(value) =>
        value
      case Some(_) =>
        "yaml"
      case None =>
        RuntimeDefaults.defaultFormat(mode)
    }
  }

  private def _resolve_shape(
    request: Request
  ): String = {
    val fromrequest = request.properties.reverseIterator.collectFirst {
      case prop if
          prop.name.equalsIgnoreCase("textus.output.shape") ||
          prop.name.equalsIgnoreCase("cncf.output.shape") =>
        Option(prop.value).map(_.toString.trim.toLowerCase).getOrElse("")
    }.filter(_.nonEmpty)
    val fromconfig = _configuration_string("textus.output.shape")
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
    fromrequest.orElse(fromconfig) match {
      case Some(value) if _supported_shapes.contains(value) =>
        value
      case _ =>
        "data"
    }
  }

  private def _structured_format(
    format: String,
    shape: String
  ): String =
    if (shape == "envelope" && format == "text") "yaml" else format

  private def _envelope_scalar(
    request: Request,
    value: String
  ): Record =
    _envelope_record(request, _execution_record_for_scalar(request, value), _scalar_data(value))

  private def _envelope_record(
    request: Request,
    execution: Record,
    data: Any
  ): Record =
    Record.data(
      "textus-execution" -> execution,
      "data" -> data
    )

  private def _execution_record_for_record: Record =
    Record.data(
      "interface-shape" -> "record"
    )

  private def _execution_record_for_scalar(
    request: Request,
    value: String
  ): Record = {
    val interfaceShape =
      if (_is_job_id(value))
        "job"
      else
        "scalar"
    _requested_execution_mode_record(request) match {
      case Some(mode) =>
        Record.data(
          "interface-shape" -> interfaceShape,
          "requested-mode" -> mode
        )
      case None =>
        Record.data(
          "interface-shape" -> interfaceShape
        )
    }
  }

  private def _requested_execution_mode_record(
    request: Request
  ): Option[String] =
    request.properties.reverseIterator.collectFirst {
      case prop if
          prop.name.equalsIgnoreCase("textus.runtime.command.execution-mode") ||
          prop.name.equalsIgnoreCase("cncf.runtime.command.execution-mode") =>
        Option(prop.value).map(_.toString.trim).getOrElse("")
    }.filter(_.nonEmpty)
      .orElse(_configuration_string("textus.runtime.command.execution-mode"))
      .orElse(GlobalRuntimeContext.current.flatMap(_.commandExecutionMode).map(_.toString))

  private def _scalar_data(
    value: String
  ): Any =
    if (_is_job_id(value))
      Record.data("job-id" -> value)
    else
      value

  private def _is_job_id(
    value: String
  ): Boolean =
    value != null && value.startsWith("cncf-job-")

  private def _configuration_string(
    key: String
  ): Option[String] =
    GlobalRuntimeContext.current.flatMap { global =>
      ConfigurationAccess.getString(global.resolvedConfiguration, key)
        .orElse(_legacy_alias(key).flatMap(ConfigurationAccess.getString(global.resolvedConfiguration, _)))
    }

  private def _legacy_alias(
    key: String
  ): Option[String] =
    if (key.startsWith("textus.")) Some("cncf." + key.stripPrefix("textus."))
    else None
}
