package org.goldenport.cncf.protocol

import java.time.ZoneId
import java.util.Locale
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.action.CommandExecutionPolicy
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.RuntimeContext
import scala.xml.{Elem, NodeSeq, Text}

/*
 * @since   Mar. 13, 2026
 *  version Mar. 28, 2026
 *  version Apr. 30, 2026
 * @version May. 31, 2026
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
      case OperationResponse.Http(http) =>
        Response.Content(http.contentType, http.bag)
      case OperationResponse.RecordResponse(record) =>
        val data = _output_record(record)
        val payload =
          _with_inline_debug(request, data, metadata).getOrElse {
            if (shape == "envelope") _envelope_record(request, data, metadata, _execution_record_for_record(request))
            else data
          }
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] if shape == "envelope" =>
        val payload = _with_inline_debug(request, _scalar_data(scalar.print, envelope = true), metadata)
          .getOrElse(_envelope_scalar(request, scalar.print, metadata))
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] =>
        _with_inline_debug(request, scalar.print, metadata) match {
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

  private def _runtime_context: RuntimeContext.Context = {
    val base = RuntimeContext.Context.default
    val formatting0 = base.formatting
    val formatting1 = _configuration_string("textus.locale")
      .orElse(_configuration_string("cncf.locale"))
      .flatMap(_locale)
      .map(formatting0.withLocale)
      .getOrElse(formatting0)
    val formatting2 = _configuration_string("textus.timeZone")
      .orElse(_configuration_string("textus.timezone"))
      .orElse(_configuration_string("cncf.timeZone"))
      .orElse(_configuration_string("cncf.timezone"))
      .flatMap(_timezone)
      .map(formatting1.withTimezone)
      .getOrElse(formatting1)
    base.copy(formatting = formatting2)
  }

  private def _locale(value: String): Option[Locale] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(x => Locale.forLanguageTag(x.replace('_', '-')))

  private def _timezone(value: String): Option[ZoneId] =
    scala.util.Try(ZoneId.of(value.trim)).toOption

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
    value: String,
    metadata: RuntimeContext.ExecutionMetadata
  ): Record =
    _response_envelope(
      request,
      _scalar_data(value, envelope = true),
      metadata,
      _execution_record_for_scalar(request, value),
      None,
      if (_is_job_id(value)) Some(value) else None
    )

  private def _envelope_record(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    execution: Record
  ): Record =
    _response_envelope(request, data, metadata, execution, None)

  private def _with_inline_debug(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata
  ): Option[Record] =
    metadata.inlineCallTree.map { calltree =>
      _response_envelope(
        request,
        data,
        metadata,
        _execution_record_for_data(request, data),
        Some(Record.data("calltree" -> _debug_calltree_payload(calltree)))
      )
    }

  private def _response_envelope(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    execution: Record,
    debug: Option[Record],
    acceptedjobid: Option[String] = None
  ): Record = {
    val roots =
      Vector(
        Some("execution" -> execution),
        _job_record(data, metadata, acceptedjobid).map("job" -> _),
        _continuation_record(request).map("continuation" -> _),
        debug.map("debug" -> _)
      ).flatten
    Record.createFull(Vector("data" -> data) ++ roots)
  }

  private def _debug_calltree_payload(
    calltree: Record
  ): Record = {
    val values = calltree.asMap
    val nodes = values.get("nodes")
      .orElse(values.get("calltree"))
      .getOrElse(Vector.empty)
    val fields =
      Vector(
        "job_id" -> values.getOrElse("job_id", ""),
        "nodes" -> nodes
      ) ++ values.toVector.filterNot { case (key, _) => key == "job_id" || key == "calltree" || key == "nodes" }
    Record.dataAuto(fields*)
  }

  private def _execution_record_for_record(
    request: Request
  ): Record =
    _execution_record(request, "record")

  private def _execution_record_for_scalar(
    request: Request,
    value: String
  ): Record = {
    val interfaceshape =
      if (_is_job_id(value))
        "job"
      else
        "scalar"
    _execution_record(request, interfaceshape)
  }

  private def _execution_record_for_data(
    request: Request,
    data: Any
  ): Record = {
    val interfaceshape = data match {
      case _: Record => "record"
      case null => "none"
      case _ => "scalar"
    }
    _execution_record(request, interfaceshape)
  }

  private def _execution_record(
    request: Request,
    interfaceshape: String
  ): Record = {
    val base =
      Vector(
        "interfaceShape" -> interfaceshape,
        "operation" -> request.operation
      ) ++ request.component.map("component" -> _) ++ request.service.map("service" -> _)
    val policy = _requested_execution_policy(request)
    Record.dataAuto(
      (base ++ Vector(
        "mode" -> policy.map(_.modeLabel),
        "interface" -> policy.map(_.interfaceMode.toString.toLowerCase(java.util.Locale.ROOT)),
        "managedByJob" -> policy.map(_.managedByJob),
        "asyncContinuation" -> policy.map(_.asyncContinuation)
      ))*
    )
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

  private def _requested_execution_policy(
    request: Request
  ): Option[CommandExecutionPolicy] =
    _requested_execution_mode_record(request).flatMap(CommandExecutionPolicy.parse)

  private def _job_record(
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    acceptedjobid: Option[String]
  ): Option[Record] = {
    val datajobid = acceptedjobid.orElse(data match {
      case value: String if _is_job_id(value) => Some(value)
      case _ => None
    })
    metadata.responseJobId.orElse(metadata.debugJobId).orElse(datajobid).map { jobid =>
      Record.dataAuto(
        "id" -> jobid,
        "status" -> acceptedjobid.map(_ => "accepted")
      )
    }
  }

  private def _continuation_record(
    request: Request
  ): Option[Record] =
    _requested_execution_policy(request)
      .filter(_.asyncContinuation)
      .map(_ => Record.data("mode" -> "event-async-same-job-task", "policy" -> "async-same-job"))

  private def _scalar_data(
    value: String,
    envelope: Boolean = false
  ): Any =
    if (envelope && _is_job_id(value))
      null
    else if (_is_job_id(value))
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
