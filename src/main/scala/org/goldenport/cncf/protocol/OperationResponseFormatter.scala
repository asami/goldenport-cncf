package org.goldenport.cncf.protocol

import java.util.Locale
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.RuntimeContext
import scala.xml.{Elem, NodeSeq, Text}

/*
 * @since   Mar. 13, 2026
 *  version Mar. 28, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationResponseFormatter {
  private val _supported_formats = Set("json", "yaml", "xml", "text")
  private val _supported_shapes = Set("data", "envelope")

  def toResponse(
    request: Request,
    response: OperationResponse,
    mode: RunMode
  ): Response =
    toResponse(request, response, mode, RuntimeContext.ExecutionMetadata.empty)

  def toResponse(
    request: Request,
    response: OperationResponse,
    mode: RunMode,
    metadata: RuntimeContext.ExecutionMetadata
  ): Response = {
    val format = _resolve_format(request, mode)
    val shape = _resolve_shape(request)
    response match {
      case OperationResponse.RecordResponse(record) =>
        val data = _output_record(record)
        val payload =
          _with_inline_debug(data, metadata).getOrElse {
            if (shape == "envelope") _envelope_record(request, _execution_record_for_record, data)
            else data
          }
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] if shape == "envelope" =>
        val payload = _with_inline_debug(_scalar_data(scalar.print), metadata).getOrElse(_envelope_scalar(request, scalar.print))
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] =>
        _with_inline_debug(scalar.print, metadata) match {
          case Some(payload) =>
            _record_response(_structured_format(format, shape), payload)
          case None =>
            response.toResponse
        }
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
      case "xml" =>
        Response.Xml(_record_to_xml(record))
      case "text" =>
        Response.Scalar(record.print)
      case _ =>
        Response.Yaml(RecordEncoder.yaml(record))
    }

  private def _output_record(
    record: Record
  ): Record =
    _runtime_context.transformRecord(record)

  private def _runtime_context: RuntimeContext.Context =
    _configuration_string("textus.locale")
      .orElse(_configuration_string("cncf.locale"))
      .map(locale => RuntimeContext.Context.default.copy(
        formatting = RuntimeContext.Context.default.formatting.copy(locale = _locale(locale))
      ))
      .getOrElse(RuntimeContext.Context.default)

  private def _locale(value: String): Locale =
    Locale.forLanguageTag(value.trim.replace('_', '-'))

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
      .orElse(_configuration_string("cncf.format"))
      .orElse(_configuration_string("cncf.output.format"))
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
      .orElse(_configuration_string("cncf.output.shape"))
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

  private def _record_to_xml(
    record: Record
  ): String = {
    val nodes = record.fields.map { field =>
      _field_to_node(field.key, field.value.single)
    }
    _elem("record", nodes).toString
  }

  private def _field_to_node(
    name: String,
    value: Any
  ): scala.xml.Node =
    value match {
      case r: Record =>
        _elem(name, r.fields.map(f => _field_to_node(f.key, f.value.single)))
      case xs: Iterable[?] =>
        _elem(name, xs.toVector.zipWithIndex.map { case (v, i) =>
          _field_to_node(s"item", v)
        })
      case null =>
        _elem(name, NodeSeq.Empty)
      case other =>
        _elem(name, Text(other.toString))
    }

  private def _elem(
    name: String,
    children: Seq[scala.xml.Node]
  ): Elem =
    Elem(null, name, scala.xml.Null, scala.xml.TopScope, minimizeEmpty = children.isEmpty, children*)

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

  private def _with_inline_debug(
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata
  ): Option[Record] =
    metadata.inlineCallTree.map { calltree =>
      Record.data(
        "data" -> data,
        "debug" -> Record.data(
          "calltree" -> calltree
        )
      )
    }

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
          prop.name.equalsIgnoreCase(RuntimeConfig.CommandExecutionModeKey) ||
          prop.name.equalsIgnoreCase(RuntimeConfig.RuntimeCommandExecutionModeKey) ||
          prop.name.equalsIgnoreCase("cncf.command.execution-mode") ||
          prop.name.equalsIgnoreCase("cncf.runtime.command.execution-mode") =>
        Option(prop.value).map(_.toString.trim).getOrElse("")
    }.filter(_.nonEmpty)
      .orElse(_configuration_string(RuntimeConfig.CommandExecutionModeKey))
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
      RuntimeConfig.getString(global.resolvedConfiguration, key)
    }
}
