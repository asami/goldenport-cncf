package org.goldenport.cncf.observability

import java.util.Locale
import java.time.Instant

import io.circe.Json
import io.circe.parser
import org.goldenport.Conclusion
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.observation.calltree.CallTree

/*
 * @since   Jan.  7, 2026
 *  version Jan. 29, 2026
 *  version Apr. 25, 2026
 *  version May. 11, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationContext(
  operationFqn: String
)

object ObservabilityEngine {
  def callTreeRecord(
    calltree: CallTree,
    jobId: Option[String] = None
  ): Record =
    Record.data(
      "job_id" -> jobId.getOrElse(""),
      "calltree" -> _calltree_nodes(calltree.toRecord)
    )

  final case class ExecutionHistoryFilter(
    operationContains: Option[String] = None,
    originSlot: Option[String] = None
  ) {
    def matches(entry: ExecutionHistoryEntry): Boolean =
      operationContains.forall(entry.operation.contains) &&
        originSlot.forall(_ == entry.originSlot)
  }

  final case class ExecutionHistoryConfig(
    recentLimit: Int = 100,
    filteredLimit: Int = 10000,
    filters: Vector[ExecutionHistoryFilter] = Vector.empty
  ) {
    def shouldRetainFiltered(entry: ExecutionHistoryEntry): Boolean =
      filters.exists(_.matches(entry))
  }

  final case class ExecutionHistoryEntry(
    id: Long,
    operation: String,
    parameters: Record,
    parametersText: String,
    outcome: String,
    resultType: String,
    resultSummary: String,
    resultSummaryRecord: Record,
    capturedAt: Instant,
    jobId: Option[String],
    traceId: Option[String],
    executionId: Option[String],
    originSlot: String,
    calltree: Option[CallTree]
  ) {
    def toRecord: Record =
      Record.createFull(
        Vector(
        "id" -> id,
        "operation" -> operation,
        "parameters" -> parameters,
        "parameters_text" -> parametersText,
        "outcome" -> outcome,
        "result_type" -> resultType,
        "result_summary" -> resultSummary,
        "result_summary_record" -> resultSummaryRecord,
        "captured_at" -> capturedAt.toString,
        "job_id" -> jobId.getOrElse(""),
        "trace_id" -> traceId.getOrElse(""),
        "execution_id" -> executionId.getOrElse(""),
        "origin_slot" -> originSlot
        )
      ) ++ calltree.map(tree => Record.data("calltree" -> _calltree_nodes(tree.toRecord))).getOrElse(Record.empty)

    def calltreeRecord: Record =
      Record.createFull(
        Vector(
        "id" -> id,
        "operation" -> operation,
        "parameters" -> parameters,
        "parameters_text" -> parametersText,
        "outcome" -> outcome,
        "result_type" -> resultType,
        "result_summary" -> resultSummary,
        "result_summary_record" -> resultSummaryRecord,
        "captured_at" -> capturedAt.toString,
        "job_id" -> jobId.getOrElse(""),
        "trace_id" -> traceId.getOrElse(""),
        "execution_id" -> executionId.getOrElse(""),
        "origin_slot" -> originSlot,
        "calltree" -> calltree.map(tree => _calltree_nodes(tree.toRecord)).getOrElse(Vector.empty)
        )
      )

    private def _calltree_nodes(record: Record): Vector[Record] =
      ObservabilityEngine._calltree_nodes(record)
  }

  private final case class CallTreeEvent(
    kind: String,
    label: String,
    attributes: Map[String, String],
    startedAtNanos: Long,
    endedAtNanos: Option[Long]
  )

  private final case class CallTreeNode(
    label: String,
    enterAttributes: Map[String, String],
    leaveAttributes: Map[String, String],
    startedAtNanos: Long,
    endedAtNanos: Option[Long],
    children: Vector[CallTreeNode],
    observations: Vector[CallTreeObservation] = Vector.empty
  ) {
    def attributes: Map[String, String] =
      enterAttributes ++ leaveAttributes

    private def _has_real_io: Boolean =
      attributes.get("real_io").exists(_.equalsIgnoreCase("true")) ||
        _semantic_kind(attributes) == "io" ||
        children.exists(_._has_real_io) ||
        observations.exists(_.hasRealIo)

    def toRecord: Record = {
      val highlights = _node_highlights(attributes, if (_has_real_io) Vector("real_io") else Vector.empty)
      val merged = attributes - "real_io"
      val scalarFields = _ordered_scalar_fields(merged)
      val fields =
        Vector(
          "label" -> label,
          "kind" -> _semantic_kind(merged)
        ) ++
          _display_label_field(merged, label) ++
          scalarFields ++
          (if (highlights.nonEmpty) Vector("highlights" -> highlights.mkString(",")) else Vector.empty) ++
          _json_object_field("request", merged.get("request")) ++
          _json_object_field("response", merged.get("response")) ++
          _json_object_field("result", merged.get("result")) ++
          _json_object_field("web_parameters", merged.get("web_parameters")) ++
          _json_object_field("query", merged.get("query")) ++
          (if (children.nonEmpty) Vector("flow" -> children.map(_.toRecord)) else Vector.empty) ++
          (if (observations.nonEmpty) Vector("observations" -> observations.map(_.toRecord)) else Vector.empty)
      Record.dataAuto(fields*)
    }
  }

  private final case class CallTreeObservation(
    label: String,
    attributes: Map[String, String]
  ) {
    def hasRealIo: Boolean =
      attributes.get("real_io").exists(_.equalsIgnoreCase("true")) ||
        _semantic_kind(attributes) == "io"

    def toRecord: Record = {
      val highlights = _node_highlights(attributes, if (hasRealIo) Vector("real_io") else Vector.empty)
      val merged = attributes - "real_io"
      val scalarFields = _ordered_scalar_fields(merged)
      val fields = Vector(
        "label" -> label,
        "kind" -> _semantic_kind(merged)
      ) ++
        _display_label_field(merged, label) ++
        scalarFields ++
        (if (highlights.nonEmpty) Vector("highlights" -> highlights.mkString(",")) else Vector.empty) ++
        _json_object_field("request", merged.get("request")) ++
        _json_object_field("response", merged.get("response")) ++
        _json_object_field("web_parameters", merged.get("web_parameters")) ++
        _json_object_field("query", merged.get("query"))
      Record.dataAuto(fields*)
    }
  }

  private def _json_object_field(
    key: String,
    value: Option[String]
  ): Vector[(String, Any)] =
    value.filter(_.trim.nonEmpty).map { text =>
      key -> _parse_json_value(text).getOrElse(text)
    }.toVector

  private val _reserved_payload_keys: Set[String] =
    Set("request", "response", "result", "web_parameters", "resolved_parameters", "query", "calltree_kind", "display_label", "highlights")

  private val _calltree_scalar_order: Vector[String] =
    Vector(
      "component",
      "service",
      "operation",
      "value_type",
      "started_at_nanos",
      "ended_at_nanos",
      "duration_nanos",
      "duration_micros",
      "duration_millis",
      "outcome",
      "status",
      "response_type",
      "source",
      "target",
      "entity",
      "collection",
      "datastore",
      "error",
      "message"
    )

  private def _node_highlights(
    attributes: Map[String, String],
    derived: Vector[String]
  ): Vector[String] = {
    val explicit = attributes.get("highlights")
      .toVector
      .flatMap(_.split("[,\\s]+").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
    (explicit ++ derived).distinct
  }

  private def _ordered_scalar_fields(
    attributes: Map[String, String]
  ): Vector[(String, String)] = {
    val scalar = attributes.filterNot { case (key, _) => _reserved_payload_keys.contains(key) }
    val fixed = _calltree_scalar_order.flatMap(key => scalar.get(key).map(key -> _))
    val rest = scalar.toVector
      .filterNot { case (key, _) => _calltree_scalar_order.contains(key) }
      .sortBy(_._1)
    fixed ++ rest
  }

  private def _parse_json_value(
    text: String
  ): Option[Any] =
    parser.parse(text).toOption.map { json =>
      json.asString.flatMap { s =>
        val trimmed = s.trim
        if (trimmed.startsWith("{") || trimmed.startsWith("["))
          parser.parse(trimmed).toOption
        else
          None
      }.map(_json_to_value).getOrElse(_json_to_value(json))
    }

  private def _json_to_value(
    json: Json
  ): Any =
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toLong.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(_json_to_value).toVector,
      jsonObject = obj => Record.dataAuto(obj.toVector.map { case (k, v) => k -> _json_to_value(v) }*)
    )

  private def _semantic_kind(
    attributes: Map[String, String]
  ): String =
    attributes.get("calltree_kind").filter(_.nonEmpty).getOrElse("step")

  private def _display_label_field(
    attributes: Map[String, String],
    fallback: String
  ): Vector[(String, String)] =
    attributes.get("display_label")
      .filter(_.nonEmpty)
      .filter(_ != fallback)
      .map("display_label" -> _)
      .toVector

  private def _calltree_nodes(record: Record): Vector[Record] =
    _calltree_node_models(record).map(_.toRecord)

  private def _calltree_node_models(record: Record): Vector[CallTreeNode] = {
    val events = _calltree_events(record)
    if (events.isEmpty)
      Vector.empty
    else {
      val leaves = events.filter(_is_leave_event).groupBy(x => (x.label, x.startedAtNanos))
      val spanNodes = events.zipWithIndex.collect { case (entry, index) if entry.kind == "enter" =>
        val leave = leaves.get((entry.label, entry.startedAtNanos)).flatMap(_.headOption)
        index -> CallTreeNode(
          entry.label,
          entry.attributes,
          leave.map(_.attributes).getOrElse(Map.empty),
          entry.startedAtNanos,
          leave.flatMap(_.endedAtNanos).orElse(entry.endedAtNanos),
          Vector.empty
        )
      }
      val markerNodes = events.zipWithIndex.collect {
        case (event, index) if event.kind != "enter" && !_is_leave_event(event) =>
          index -> CallTreeNode(
            event.label,
            event.attributes,
            Map.empty,
            event.startedAtNanos,
            event.endedAtNanos.orElse(Some(event.startedAtNanos)),
            Vector.empty
          )
      }
      val nodes = spanNodes ++ markerNodes
      val byindex = nodes.toMap
      val parentCandidates = spanNodes.toMap
      val parentByIndex = nodes.map { case (index, node) =>
        index -> parentCandidates.collect {
          case (parentIndex, parent) if parentIndex != index && _calltree_contains(parent, node) =>
            val span = parent.endedAtNanos.getOrElse(Long.MaxValue) - parent.startedAtNanos
            (parentIndex, span, parent.startedAtNanos)
        }.toVector.sortBy { case (_, span, start) => (span, -start) }.headOption.map(_._1)
      }.toMap
      def build(index: Int): CallTreeNode = {
        val node = byindex(index)
        val childindices = parentByIndex.collect { case (childIndex, Some(parentIndex)) if parentIndex == index => childIndex }
          .toVector
          .sortBy(child => byindex(child).startedAtNanos)
        val (observationindices, spanindices) = childindices.partition(child => _is_observation_node(byindex(child)))
        val observations = observationindices.map { child =>
          val n = byindex(child)
          CallTreeObservation(n.label, n.attributes)
        }
        node.copy(
          children = spanindices.map(build),
          observations = observations
        )
      }
      parentByIndex.collect { case (index, None) => index }
        .toVector
        .sortBy(index => byindex(index).startedAtNanos)
        .map(build)
    }
  }

  private def _is_leave_event(
    event: CallTreeEvent
  ): Boolean =
    event.kind == "leave" || event.kind == "exit"

  private def _is_observation_node(
    node: CallTreeNode
  ): Boolean = {
    _semantic_kind(node.attributes) == "metric"
  }

  private def _calltree_contains(
    parent: CallTreeNode,
    child: CallTreeNode
  ): Boolean = {
    val parentEnd = parent.endedAtNanos.getOrElse(Long.MaxValue)
    val childEnd = child.endedAtNanos.getOrElse(child.startedAtNanos)
    parent.startedAtNanos <= child.startedAtNanos && childEnd <= parentEnd &&
      (parent.startedAtNanos < child.startedAtNanos || parentEnd > childEnd)
  }

  private def _calltree_events(record: Record): Vector[CallTreeEvent] =
    record.asMap.toVector
      .sortBy { case (k, _) => scala.util.Try(k.toString.toInt).toOption.getOrElse(Int.MaxValue) }
      .collect { case (_, r: Record) => _calltree_event(r) }
      .flatten

  private def _calltree_event(record: Record): Option[CallTreeEvent] = {
    val attrs = _calltree_attributes(record.asMap.get("attributes")) ++
      record.asMap.get("message").map(x => Map("message" -> x.toString)).getOrElse(Map.empty)
    for {
      kind <- record.asMap.get("kind").map(_.toString.toLowerCase(Locale.ROOT))
      label <- record.asMap.get("label").map(_.toString).filter(_.nonEmpty)
      start <- attrs.get("started_at_nanos")
        .orElse(attrs.get("ended_at_nanos"))
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(_to_long_option)
    } yield CallTreeEvent(
      kind,
      label,
      attrs,
      start,
      attrs.get("ended_at_nanos")
        .orElse(attrs.get("failed_at_nanos"))
        .orElse(attrs.get("sampled_at_nanos"))
        .flatMap(_to_long_option)
    )
  }

  private def _calltree_attributes(
    value: Option[Any]
  ): Map[String, String] =
    value match {
      case Some(r: Record) => r.asMap.map { case (k, v) => k -> v.toString }
      case Some(m: Map[?, ?]) => m.map { case (k, v) => k.toString -> v.toString }
      case _ => Map.empty
    }

  private def _to_long_option(
    value: String
  ): Option[Long] =
    scala.util.Try(value.toLong).toOption

  @volatile private var _execution_history_config: ExecutionHistoryConfig = ExecutionHistoryConfig()
  private var _execution_history_sequence: Long = 0L
  private var _recent_execution_history: Vector[ExecutionHistoryEntry] = Vector.empty
  private var _filtered_execution_history: Vector[ExecutionHistoryEntry] = Vector.empty

  def executionHistoryConfig: ExecutionHistoryConfig =
    _execution_history_config

  def updateExecutionHistoryConfig(
    config: ExecutionHistoryConfig
  ): Unit =
    synchronized {
      _execution_history_config = config
      _filtered_execution_history = _filtered_execution_history.filter(config.shouldRetainFiltered).takeRight(config.filteredLimit)
      _recent_execution_history = _recent_execution_history.takeRight(config.recentLimit)
    }

  def clearExecutionHistory(): Unit =
    synchronized {
      _execution_history_sequence = 0L
      _recent_execution_history = Vector.empty
      _filtered_execution_history = Vector.empty
    }

  def executionHistory: Vector[ExecutionHistoryEntry] =
    executionHistory(None)

  def executionHistory(
    operationContains: Option[String]
  ): Vector[ExecutionHistoryEntry] =
    executionHistory(operationContains, None)

  def executionHistory(
    operationContains: Option[String],
    originSlot: Option[String]
  ): Vector[ExecutionHistoryEntry] =
    synchronized {
      val normalizedslot = originSlot.map(_normalize_origin_slot)
      (_recent_execution_history ++ _filtered_execution_history)
        .groupBy(_.id)
        .values
        .flatMap(_.headOption)
        .toVector
        .filter(entry => operationContains.forall(entry.operation.contains))
        .filter(entry => normalizedslot.forall(_ == entry.originSlot))
        .sortBy(_.id)
    }

  def latestExecution: Option[ExecutionHistoryEntry] =
    latestExecution(None)

  def latestExecution(
    originSlot: Option[String]
  ): Option[ExecutionHistoryEntry] =
    synchronized {
      val normalizedslot = originSlot.map(_normalize_origin_slot)
      val entries = (_recent_execution_history ++ _filtered_execution_history)
        .groupBy(_.id)
        .values
        .flatMap(_.headOption)
        .toVector
        .filter(entry => normalizedslot.forall(_ == entry.originSlot))
        .filter(entry => normalizedslot.nonEmpty || !_is_background_origin_slot(entry.originSlot))
        .sortBy(_.id)
      entries.lastOption
    }

  def findExecution(
    executionId: Option[String],
    traceId: Option[String],
    originSlot: Option[String] = None
  ): Option[ExecutionHistoryEntry] =
    synchronized {
      val normalizedslot = originSlot.map(_normalize_origin_slot)
      val entries = (_recent_execution_history ++ _filtered_execution_history)
        .groupBy(_.id)
        .values
        .flatMap(_.headOption)
        .toVector
        .filter(entry => normalizedslot.forall(_ == entry.originSlot))
        .filter { entry =>
          executionId.exists(id => entry.executionId.contains(id)) ||
            traceId.exists(id => entry.traceId.contains(id))
        }
        .sortBy(_.id)
      entries.lastOption
    }

  private def _is_background_origin_slot(value: String): Boolean =
    value == "background-js" || value == "page-context"

  def recordActionExecution(
    operation: String,
    parameters: Record,
    parametersText: String,
    outcome: Either[Conclusion, OperationResponse],
    resultConfidentiality: Map[String, DataConfidentiality] = Map.empty,
    jobId: Option[String] = None,
    traceId: Option[String] = None,
    executionId: Option[String] = None,
    originSlot: String = "operation",
    calltree: Option[CallTree] = None
  ): Unit =
    synchronized {
      if (!_is_execution_admin_operation(operation)) {
        _execution_history_sequence += 1
        val entry = ExecutionHistoryEntry(
          id = _execution_history_sequence,
          operation = operation,
          parameters = parameters,
          parametersText = parametersText,
          outcome = outcome.fold(_ => "failure", _ => "success"),
          resultType = outcome.fold(_ => "Conclusion", _result_type),
          resultSummary = outcome.fold(_failure_summary, _result_summary_text(_, resultConfidentiality)),
          resultSummaryRecord = outcome.fold(_failure_summary_record, _result_summary_record(_, resultConfidentiality)),
          capturedAt = Instant.now(),
          jobId = jobId,
          traceId = traceId,
          executionId = executionId,
          originSlot = _normalize_origin_slot(originSlot),
          calltree = calltree
        )
        val config = _execution_history_config
        _recent_execution_history = (_recent_execution_history :+ entry).takeRight(config.recentLimit)
        if (config.shouldRetainFiltered(entry))
          _filtered_execution_history = (_filtered_execution_history :+ entry).takeRight(config.filteredLimit)
      }
    }

  private def _is_execution_admin_operation(operation: String): Boolean =
    operation == "admin.execution.history" || operation == "admin.execution.calltree"

  private def _normalize_origin_slot(value: String): String = {
    val normalized = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
      .map {
        case c if c.isLetterOrDigit => c
        case '-' | '_' | '.' => '-'
        case _ => '-'
      }
      .mkString
      .replaceAll("-+", "-")
      .stripPrefix("-")
      .stripSuffix("-")
    if (normalized.isEmpty) "operation" else normalized
  }

  private def _result_type(response: OperationResponse): String =
    response.getClass.getSimpleName.stripSuffix("$")

  private def _result_summary(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    _result_summary_record(response, confidentiality)

  private def _result_summary_record(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record = {
    val base = DiagnosticPayloadSummary.operationResponse(response, confidentiality)
    response match {
      case OperationResponse.RecordResponse(record) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeRecordSummary(
            DiagnosticPayloadExternalizer.currentOperation.getOrElse(""),
            "result",
            record,
            base,
            confidentiality
          )
          .toRecord
      case OperationResponse.Json(json) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(
            DiagnosticPayloadExternalizer.currentOperation.getOrElse(""),
            "result",
            "application/json",
            "json",
            json.spaces2,
            base
          )
          .toRecord
      case OperationResponse.Yaml(yaml) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(
            DiagnosticPayloadExternalizer.currentOperation.getOrElse(""),
            "result",
            "application/yaml",
            "yaml",
            yaml,
            base
          )
          .toRecord
      case OperationResponse.Scalar(value) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(
            DiagnosticPayloadExternalizer.currentOperation.getOrElse(""),
            "result",
            "text/plain",
            "txt",
            String.valueOf(value),
            base
          )
          .toRecord
      case OperationResponse.Opaque(value) =>
        DiagnosticPayloadExternalizer.fromGlobal
          .externalizeUnsafeTextSummary(
            DiagnosticPayloadExternalizer.currentOperation.getOrElse(""),
            "result",
            "text/plain",
            "txt",
            String.valueOf(value),
            base
          )
          .toRecord
      case _ =>
        base.toRecord
    }
  }

  private def _result_summary_text(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String =
    _payload_display_text(_result_summary_record(response, confidentiality))

  private def _failure_summary(conclusion: Conclusion): String =
    _payload_display_text(_failure_summary_record(conclusion))

  private def _failure_summary_record(conclusion: Conclusion): Record =
    DiagnosticPayloadSummary.textSummary("conclusion", conclusion.display, includeInline = false).toRecord ++
      Record.dataAuto(
        "status" -> conclusion.status.webCode.code.toString,
        "outcome" -> "failure"
      )

  private def _payload_display_text(record: Record): String = {
    val kind = record.getString("kind").getOrElse("payload")
    val parts = Vector(
      Some(kind),
      record.getString("record_count").map(x => s"records=$x"),
      record.getString("element_count").map(x => s"elements=$x"),
      record.getString("field_count").map(x => s"fields=$x"),
      record.getString("fetched_count").map(x => s"fetched=$x"),
      record.getString("total_count").map(x => s"total=$x"),
      record.getString("size_bytes").map(x => s"${x} bytes"),
      record.getString("char_count").map(x => s"${x} chars"),
      record.getString("truncated").filter(_.equalsIgnoreCase("true")).map(_ => "truncated"),
      record.getString("payload_href").orElse(record.getString("external_href")).map(x => s"ref=$x")
    ).flatten
    _truncate(parts.mkString(" "), 1000)
  }

  private def _truncate(s: String, limit: Int): String =
    if (s.length <= limit) s else s.take(limit) + "..."

  private def _sanitize_record(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    Record.dataAuto(
      record.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) => key -> _sanitize_value(key, value, confidentiality) }*
    )

  private def _sanitize_value(
    key: String,
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    if (_is_sensitive_key(key, confidentiality))
      "***"
    else
      value match {
        case r: Record =>
          _sanitize_record(r, confidentiality)
        case xs: Seq[?] =>
          xs.map(_sanitize_nested_value(_, confidentiality))
        case xs: Array[?] =>
          xs.toVector.map(_sanitize_nested_value(_, confidentiality))
        case m: scala.collection.Map[?, ?] =>
          m.toVector.map {
            case (k, v) =>
              val childKey = Option(k).map(_.toString).getOrElse("")
              childKey -> _sanitize_value(childKey, v, confidentiality)
          }.toMap
        case _ =>
          value
      }

  private def _sanitize_nested_value(
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    value match {
      case r: Record =>
        _sanitize_record(r, confidentiality)
      case xs: Seq[?] =>
        xs.map(_sanitize_nested_value(_, confidentiality))
      case xs: Array[?] =>
        xs.toVector.map(_sanitize_nested_value(_, confidentiality))
      case m: scala.collection.Map[?, ?] =>
        m.toVector.map {
          case (k, v) =>
            val childKey = Option(k).map(_.toString).getOrElse("")
            childKey -> _sanitize_value(childKey, v, confidentiality)
        }.toMap
      case _ =>
        value
    }

  private def _is_sensitive_key(
    key: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Boolean = {
    val normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
    confidentiality.get(key)
      .orElse(confidentiality.find { case (k, _) => k.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") == normalized }.map(_._2))
      .exists(_.shouldRedactByDefault) ||
    normalized.contains("password") ||
      normalized.contains("passwd") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie") ||
      normalized.contains("credential") ||
      normalized.contains("apikey") ||
      normalized.contains("privatekey")
  }

  private def _redact_sensitive_text(value: String): String = {
    val sensitive = """password|passwd|secret|token|access[-_]?session[-_]?id|refresh[-_]?session[-_]?id|session[-_]?id|session|authorization|cookie|credential|api[-_]?key|private[-_]?key"""
    val jsonLike = s"""(?i)("(?:$sensitive)"\\s*:\\s*)"[^"]*"""".r
    val formLike = s"""(?i)(^|[?&\\s,;])($sensitive)(\\s*[=:]\\s*)([^&\\s,;]+)""".r
    val jsonRedacted = jsonLike.replaceAllIn(value, m => s"""${m.group(1)}"***"""")
    formLike.replaceAllIn(jsonRedacted, m => s"${m.group(1)}${m.group(2)}${m.group(3)}***")
  }

  def build(
    scope: ScopeContext,
    http: Option[HttpRequest],
    operation: Option[OperationContext],
    outcome: Either[Conclusion, Unit]
  ): Record = {
    val scopeRecord = Record.data(
      "scope.subsystem" -> scope.name,
      "scope.ingress" -> scope.kind.toString
    )
    val httpRecord = http.map { req =>
      Record.data(
        "http.method" -> req.method.toString,
        "http.path" -> req.path.asString
      )
    }.getOrElse(Record.empty)
    val operationRecord = operation.map { op =>
      Record.data(
        "operation.fqn" -> op.operationFqn
      )
    }.getOrElse(Record.empty)
    val outcomeRecord = outcome match {
      case Right(_) =>
        Record.data(
          "result.success" -> true
        )
      case Left(conclusion) =>
        val classification = ConclusionDiagnostics.classify(conclusion)
        Record.data(
          "result.success" -> false,
          "error.kind" -> conclusion.observation.taxonomy.print,
          "error.taxonomy.category" -> classification.taxonomyCategory,
          "error.taxonomy.symptom" -> classification.taxonomySymptom,
          "error.cause.kind" -> classification.causeKind,
          "error.interpretation" -> classification.interpretation,
          "error.user_action" -> classification.userAction,
          "error.responsibility" -> classification.responsibility,
          "error.status" -> conclusion.status.webCode.code,
          "error.status_text" -> conclusion.status.webCode.statusText,
          "error.diagnostic_key" -> classification.diagnosticKey
        ) ++ Record.dataOption(
          "error.detail_code" -> conclusion.status.detailCode.map(_.code),
          "error.app_code" -> conclusion.status.appCode,
          "error.app_status" -> conclusion.status.appStatus
        )
    }
    Record(
      scopeRecord.fields ++
        httpRecord.fields ++
        operationRecord.fields ++
        outcomeRecord.fields
    )
  }

  def emitInfo(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("info", context, scope, name, attributes, None)

  def emitWarn(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("warn", context, scope, name, attributes, None)

  def emitDebug(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("debug", context, scope, name, attributes, None)

  def emitTrace(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("trace", context, scope, name, attributes, None)

  def emitError(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("error", context, scope, name, attributes, None)

  private var _policy: VisibilityPolicy = VisibilityPolicy()

  // TODO Phase 2.85: configure visibility policy from --log-* CLI flags and runtime config.
  def visibilityPolicy: VisibilityPolicy = _policy

  def updateVisibilityPolicy(policy: VisibilityPolicy): Unit =
    _policy = policy

  def shouldEmit(
    level: String,
    scope: ScopeContext,
    packageName: String,
    className: String,
    backend: LogBackend
  ): Boolean =
    _policy.allows(level, scope.name, packageName, className, backend)

  private def _emit(
    level: String,
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record,
    cause: Option[Throwable]
  ): Unit = {
    val text = _message(context, name)
    _log_backend(level, scope, text, attributes)
    val _ = cause
  }

  private def _log_backend(
    level: String,
    scope: ScopeContext,
    message: String,
    attributes: Record
  ): Unit = {
    val _ = attributes
    LogBackendHolder.backend.foreach { backend =>
      if (shouldEmit(level, scope, "org.goldenport.cncf", "ObservabilityEngine", backend)) {
        val prefix = s"event=$level scope=${scope.kind} name=${scope.name} "
        backend.log(level, s"$prefix$message")
      }
    }
  }

  private def _message(
    context: ObservabilityContext,
    name: String
  ): String = {
    val traceid = s"traceId=${context.traceId.value}"
    val correlationid =
      context.correlationId.map(id => s"correlationId=${id.value}")
    val parts = traceid +: correlationid.toVector
    if (parts.isEmpty) name else s"$name ${parts.mkString("[", " ", "]")}"
  }
}

/**
 * Placeholder for the future visibility policy that responds to --log-* CLI flags and config values.
 */
enum LogLevel(val priority: Int) {
  case Error extends LogLevel(50)
  case Warn extends LogLevel(40)
  case Info extends LogLevel(30)
  case Debug extends LogLevel(20)
  case Trace extends LogLevel(10)
}

object LogLevel {
  private val _locale: Locale = Locale.ROOT

  def from(value: String): Option[LogLevel] =
    Option(value)
      .map(_.trim.toLowerCase(_locale))
      .flatMap {
        case "error"  => Some(LogLevel.Error)
        case "warn" | "warning" => Some(LogLevel.Warn)
        case "info"   => Some(LogLevel.Info)
        case "debug"  => Some(LogLevel.Debug)
        case "trace"  => Some(LogLevel.Trace)
        case _        => None
      }
}

final case class VisibilityPolicy(
  minLevel: LogLevel = LogLevel.Info,
  scopes: Option[Set[String]] = None,
  packages: Option[Set[String]] = None,
  classes: Option[Set[String]] = None,
  backend: Option[String] = None
) {
  def allows(
    levelValue: String,
    scope: String,
    packageName: String,
    className: String,
    backendValue: LogBackend
  ): Boolean = {
    val candidate = LogLevel.from(levelValue).getOrElse(LogLevel.Info)
    candidate.priority >= minLevel.priority
  }
}

object VisibilityPolicy {
  val AllowAll: VisibilityPolicy = VisibilityPolicy()
}
