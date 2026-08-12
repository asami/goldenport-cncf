package org.goldenport.cncf.protocol

import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeDefaults
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.RuntimeContext

/*
 * @since   Mar. 13, 2026
 *  version Mar. 28, 2026
 *  version Apr. 30, 2026
 *  version May. 31, 2026
 *  version Jun. 29, 2026
 * @version Aug. 12, 2026
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
  ): Response =
    toResponse(request, response, mode, metadata, None)

  def toResponse(
    request: Request,
    response: OperationResponse,
    mode: RunMode,
    metadata: RuntimeContext.ExecutionMetadata,
    executionResponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Response = {
    val format = _resolve_format(request, mode)
    val shape = _resolve_shape(request)
    response match {
      case OperationResponse.Http(http) =>
        Response.Content(http.contentType, http.bag)
      case OperationResponse.RecordResponse(record) =>
        val data = _output_record(record)
        val payload =
          _with_inline_debug(request, _envelope_data(data, executionResponse), metadata, executionResponse).getOrElse {
            if (shape == "envelope") _envelope_record(request, _envelope_data(data, executionResponse), metadata, _execution_record_for_record(request, executionResponse), executionResponse)
            else data
          }
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] if shape == "envelope" =>
        val payload = _with_inline_debug(request, _envelope_data(scalar.print, executionResponse), metadata, executionResponse)
          .getOrElse(_envelope_scalar(request, scalar.print, metadata, executionResponse))
        _record_response(_structured_format(format, shape), payload)
      case scalar: OperationResponse.Scalar[?] =>
        _with_inline_debug(request, scalar.print, metadata, executionResponse) match {
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
        Response.Xml(RecordEncoder.xml(record))
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
    GlobalRuntimeContext.current.map { global =>
      val assumptions = global.executionProfile.environmentAssumptions
      RuntimeContext.Context.default.copy(
        formatting = RuntimeContext.FormattingContext.default
          .withLocale(assumptions.locale)
          .withTimezone(assumptions.timezone)
      )
    }.getOrElse(RuntimeContext.Context.default)
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

  private def _envelope_scalar(
    request: Request,
    value: String,
    metadata: RuntimeContext.ExecutionMetadata,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record =
    _response_envelope(
      request,
      _envelope_data(value, executionresponse),
      metadata,
      _execution_record_for_scalar(request, executionresponse),
      None,
      executionresponse
    )

  private def _envelope_record(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    execution: Record,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record =
    _response_envelope(request, data, metadata, execution, None, executionresponse)

  private def _with_inline_debug(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Option[Record] =
    metadata.inlineCallTree.map { calltree =>
      _response_envelope(
        request,
        data,
        metadata,
        _execution_record_for_data(request, data, executionresponse),
        Some(Record.data("calltree" -> _debug_calltree_payload(calltree))),
        executionresponse
      )
    }

  private def _response_envelope(
    request: Request,
    data: Any,
    metadata: RuntimeContext.ExecutionMetadata,
    execution: Record,
    debug: Option[Record],
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record = {
    val roots =
      Vector(
        Some("execution" -> execution),
        _job_record(metadata, executionresponse).map("job" -> _),
        _continuation_record(executionresponse).map("continuation" -> _),
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
    request: Request,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record =
    _execution_record(request, "record", executionresponse)

  private def _execution_record_for_scalar(
    request: Request,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record =
    _execution_record(request, "scalar", executionresponse)

  private def _execution_record_for_data(
    request: Request,
    data: Any,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record = {
    val interfaceshape = data match {
      case _: Record => "record"
      case null => "none"
      case _ => "scalar"
    }
    _execution_record(request, interfaceshape, executionresponse)
  }

  private def _execution_record(
    request: Request,
    interfaceshape: String,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Record = {
    val base =
      Vector(
        "interface-shape" -> interfaceshape,
        "operation" -> request.operation
      ) ++ request.component.map("component" -> _) ++ request.service.map("service" -> _)
    val requestedmode = _requested_execution_mode_record(request)
    val response = executionresponse
    Record.dataAuto(
      (base ++ Vector(
        "requested-mode" -> requestedmode,
        "admitted-mode" -> response.map(_.admittedMode),
        "effective-mode" -> response.map(_.effectiveMode.toString),
        "interface" -> response.map(_.interfaceMode.toString.toLowerCase(java.util.Locale.ROOT)),
        "managed-by-job" -> response.map(_.managedByJob),
        "async-continuation" -> response.map(_.asyncContinuation),
        "response-kind" -> response.map(_.responseKind.transportValue)
      ))*
    )
  }

  private def _requested_execution_mode_record(
    request: Request
  ): Option[String] =
    request.properties.reverseIterator.collectFirst {
      case prop if
          prop.name.equalsIgnoreCase(RuntimeConfig.commandExecutionModeKey) ||
          prop.name.equalsIgnoreCase(RuntimeConfig.runtimeCommandExecutionModeKey) ||
          prop.name.equalsIgnoreCase("cncf.command.execution-mode") ||
          prop.name.equalsIgnoreCase("cncf.runtime.command.execution-mode") =>
        Option(prop.value).map(_.toString.trim).getOrElse("")
    }.filter(_.nonEmpty)
      .orElse(_configuration_string(RuntimeConfig.commandExecutionModeKey))
      .orElse(GlobalRuntimeContext.current.flatMap(_.commandExecutionMode).map(_.toString))

  private def _job_record(
    metadata: RuntimeContext.ExecutionMetadata,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Option[Record] =
    executionresponse.flatMap { response =>
      response.responseKind match {
        case RuntimeContext.ExecutionResponseKind.Direct => None
        case RuntimeContext.ExecutionResponseKind.AcceptedJob |
            RuntimeContext.ExecutionResponseKind.JobResult =>
          metadata.responseJobId.orElse(metadata.debugJobId).map { jobid =>
            Record.dataAuto(
              "id" -> jobid,
              "status" -> (if (response.responseKind == RuntimeContext.ExecutionResponseKind.AcceptedJob) Some("accepted") else None)
            )
          }
      }
    }

  private def _continuation_record(
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Option[Record] =
    executionresponse
      .filter(_.asyncContinuation)
      .map(_ => Record.data("mode" -> "event-async-same-job-task", "policy" -> "async-same-job"))

  private def _envelope_data(
    data: Any,
    executionresponse: Option[RuntimeContext.ExecutionResponseMetadata]
  ): Any =
    if (executionresponse.exists(_.responseKind == RuntimeContext.ExecutionResponseKind.AcceptedJob))
      null
    else
      data

  private def _configuration_string(
    key: String
  ): Option[String] =
    GlobalRuntimeContext.current.flatMap { global =>
      RuntimeConfig.getString(global.resolvedConfiguration, key)
    }
}
