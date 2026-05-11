package org.goldenport.cncf.http

import java.time.Instant
import org.goldenport.cncf.metrics.{EntityAccessMetricEntry, EntityAccessMetricsRegistry, RuntimeMetricPoint, RuntimeMetricsCatalog, RuntimeMetricsSnapshot}
import org.goldenport.record.Record

/*
 * @since   Apr. 12, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object RuntimeDashboardMetrics {
  final case class RequestEntry(
    observedAt: Long,
    method: String,
    path: String,
    status: Int,
    elapsedMillis: Long
  )

  final case class RequestBucket(
    period: Long,
    count: Long,
    errors: Long
  )

  final case class CountWindow(
    total: Long,
    errors: Long
  )

  final case class CountSummary(
    cumulative: CountWindow,
    day: CountWindow,
    hour: CountWindow,
    minute: CountWindow
  )

  final case class Snapshot(
    summary: CountSummary,
    bucketsByMinute: Vector[RequestBucket],
    bucketsByHour: Vector[RequestBucket],
    bucketsByDay: Vector[RequestBucket],
    recent: Vector[RequestEntry]
  )

  final case class DiagnosticExample(
    observedAt: Long,
    diagnosticKey: String,
    operation: Option[String],
    kind: Option[String],
    sourceMode: Option[String],
    backend: Option[String],
    diagnosticRecord: Option[Record]
  )

  final case class DiagnosticGroup(
    scope: String,
    label: String,
    diagnosticKey: String,
    count: Long,
    latestRecord: Option[Record],
    recentExamples: Vector[DiagnosticExample]
  )

  final case class DiagnosticScope(
    scope: String,
    label: String,
    groups: Vector[DiagnosticGroup]
  ) {
    def totalCount: Long = groups.map(_.count).sum
  }

  private final case class Event(
    observedAt: Long,
    error: Boolean,
    diagnosticKey: Option[String] = None,
    diagnosticRecord: Option[Record] = None,
    operation: Option[String] = None,
    kind: Option[String] = None,
    sourceMode: Option[String] = None,
    backend: Option[String] = None,
    elapsedMillis: Option[Long] = None,
    labels: Map[String, String] = Map.empty
  )

  private final case class PayloadExternalizationEvent(
    observedAt: Long,
    status: String,
    payloadKind: String,
    destination: String
  )

  private final case class OpenTelemetryExportEvent(
    observedAt: Long,
    signal: String,
    status: String
  )

  private var _htmlEvents = Vector.empty[Event]
  private var _actionEvents = Vector.empty[Event]
  private var _authorizationEvents = Vector.empty[Event]
  private var _dslEvents = Vector.empty[Event]
  private var _validationEvents = Vector.empty[Event]
  private var _operationRequestValidationEvents = Vector.empty[Event]
  private var _blobEvents = Vector.empty[Event]
  private var _payloadExternalizationEvents = Vector.empty[PayloadExternalizationEvent]
  private var _openTelemetryExportEvents = Vector.empty[OpenTelemetryExportEvent]
  private var _recent = Vector.empty[RequestEntry]

  private val DiagnosticScopeLabels: Map[String, String] = Map(
    "authorization" -> "Authorization",
    "validation" -> "Validation",
    "operation-request-validation" -> "Operation Request Validation",
    "blob" -> "Blob"
  )

  def recordHtmlRequest(
    method: String,
    path: String,
    status: Int,
    elapsedMillis: Long
  ): Unit = synchronized {
    val now = java.time.Instant.now.toEpochMilli
    _htmlEvents = (_htmlEvents :+ Event(
      observedAt = now,
      error = status >= 400,
      elapsedMillis = Some(elapsedMillis),
      labels = Map("status" -> _status_class(status))
    )).takeRight(10000)
    _recent = (_recent :+ RequestEntry(now, method, path, status, elapsedMillis)).takeRight(12)
  }

  def recordActionCall(
    error: Boolean,
    elapsedMillis: Option[Long] = None
  ): Unit = synchronized {
    _actionEvents = (_actionEvents :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      elapsedMillis = elapsedMillis
    )).takeRight(10000)
  }

  def recordAuthorizationDecision(denied: Boolean): Unit = synchronized {
    recordAuthorizationDecision(denied, None)
  }

  def recordAuthorizationDecision(
    denied: Boolean,
    diagnosticKey: Option[String],
    diagnosticRecord: Option[Record] = None
  ): Unit = synchronized {
    val kind = if (denied) diagnosticKey.filter(_.nonEmpty) else None
    _authorizationEvents = (_authorizationEvents :+ Event(java.time.Instant.now.toEpochMilli, denied, kind, if (denied) diagnosticRecord else None)).takeRight(10000)
  }

  def recordDslChokepoint(error: Boolean): Unit = synchronized {
    _dslEvents = (_dslEvents :+ Event(java.time.Instant.now.toEpochMilli, error)).takeRight(10000)
  }

  def recordValidation(
    operation: String,
    diagnosticKey: Option[String],
    diagnosticRecord: Option[Record] = None
  ): Unit = synchronized {
    _validationEvents = (_validationEvents :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = true,
      diagnosticKey = diagnosticKey.filter(_.nonEmpty),
      diagnosticRecord = diagnosticRecord,
      operation = Some(operation).filter(_.nonEmpty)
    )).takeRight(10000)
  }

  def recordOperationRequestValidation(
    operation: String,
    diagnosticKey: Option[String],
    diagnosticRecord: Option[Record] = None
  ): Unit = synchronized {
    _operationRequestValidationEvents = (_operationRequestValidationEvents :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = true,
      diagnosticKey = diagnosticKey.filter(_.nonEmpty),
      diagnosticRecord = diagnosticRecord,
      operation = Some(operation).filter(_.nonEmpty)
    )).takeRight(10000)
  }

  def recordBlobOperation(
    operation: String,
    error: Boolean,
    diagnosticKey: Option[String] = None,
    diagnosticRecord: Option[Record] = None,
    kind: Option[String] = None,
    sourceMode: Option[String] = None,
    backend: Option[String] = None
  ): Unit = synchronized {
    val cleanDiagnosticKey = if (error) diagnosticKey.filter(_.nonEmpty) else None
    _blobEvents = (_blobEvents :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleanDiagnosticKey,
      diagnosticRecord = if (error) diagnosticRecord else None,
      operation = Some(operation).filter(_.nonEmpty),
      kind = kind.filter(_.nonEmpty),
      sourceMode = sourceMode.filter(_.nonEmpty),
      backend = backend.filter(_.nonEmpty),
      labels = _clean_labels(Map(
        "kind" -> kind.getOrElse(""),
        "source" -> sourceMode.getOrElse(""),
        "backend" -> backend.getOrElse(""),
        "diagnostic_key" -> cleanDiagnosticKey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordDiagnosticPayloadExternalization(
    payloadKind: String,
    status: String,
    destination: String
  ): Unit = synchronized {
    _payloadExternalizationEvents = (_payloadExternalizationEvents :+ PayloadExternalizationEvent(
      observedAt = java.time.Instant.now.toEpochMilli,
      status = _normalize_label(status),
      payloadKind = _normalize_label(payloadKind),
      destination = _normalize_label(destination)
    )).takeRight(10000)
  }

  def recordOpenTelemetryExport(
    signal: String,
    status: String
  ): Unit = synchronized {
    _openTelemetryExportEvents = (_openTelemetryExportEvents :+ OpenTelemetryExportEvent(
      observedAt = java.time.Instant.now.toEpochMilli,
      signal = _normalize_label(signal),
      status = _normalize_label(status)
    )).takeRight(10000)
  }

  def htmlSnapshot: Snapshot = synchronized {
    _snapshot(_htmlEvents, _recent)
  }

  def actionCallSnapshot: Snapshot = synchronized {
    _snapshot(_actionEvents, Vector.empty)
  }

  def authorizationDecisionSnapshot: Snapshot = synchronized {
    _snapshot(_authorizationEvents, Vector.empty)
  }

  def authorizationDiagnosticCounts: Map[String, Long] = synchronized {
    _authorizationEvents
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def authorizationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_authorizationEvents)
  }

  def dslChokepointSnapshot: Snapshot = synchronized {
    _snapshot(_dslEvents, Vector.empty)
  }

  def validationSnapshot: Snapshot = synchronized {
    _snapshot(_validationEvents, Vector.empty)
  }

  def validationDiagnosticCounts: Map[String, Long] = synchronized {
    _validationEvents
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def validationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_validationEvents)
  }

  def operationRequestValidationSnapshot: Snapshot = synchronized {
    _snapshot(_operationRequestValidationEvents, Vector.empty)
  }

  def operationRequestValidationDiagnosticCounts: Map[String, Long] = synchronized {
    _operationRequestValidationEvents
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def operationRequestValidationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_operationRequestValidationEvents)
  }

  def blobOperationSnapshot: Snapshot = synchronized {
    _snapshot(_blobEvents, Vector.empty)
  }

  def blobDiagnosticCounts: Map[String, Long] = synchronized {
    _blobEvents
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def blobDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_blobEvents)
  }

  def diagnosticScopes: Vector[DiagnosticScope] = synchronized {
    Vector(
      _diagnostic_scope("authorization", _authorizationEvents),
      _diagnostic_scope("validation", _validationEvents),
      _diagnostic_scope("operation-request-validation", _operationRequestValidationEvents),
      _diagnostic_scope("blob", _blobEvents)
    )
  }

  def diagnosticScope(scope: String): Option[DiagnosticScope] = synchronized {
    val normalized = _normalize_scope(scope)
    diagnosticScopes.find(_.scope == normalized)
  }

  def diagnosticDetail(
    scope: String,
    diagnosticKey: String
  ): Option[DiagnosticGroup] = synchronized {
    diagnosticScope(scope).flatMap(_.groups.find(_.diagnosticKey == diagnosticKey))
  }

  def runtimeMetricsSnapshot(
    entityAccessMetrics: EntityAccessMetricsRegistry
  ): RuntimeMetricsSnapshot = synchronized {
    RuntimeMetricsSnapshot(
      generatedAt = Instant.now(),
      points = _runtime_metric_points(entityAccessMetrics.snapshot()),
      catalog = RuntimeMetricsCatalog.scopes
    )
  }

  def metricsCatalogRecord: Record =
    RuntimeMetricsCatalog.toRecord

  private def _diagnostic_records(events: Vector[Event]): Map[String, Record] =
    events
      .filter(_.error)
      .flatMap(e => e.diagnosticKey.map(_ -> e.diagnosticRecord))
      .groupBy(_._1)
      .flatMap { case (key, values) => values.reverse.collectFirst { case (_, Some(record)) => key -> record } }

  private def _diagnostic_scope(
    scope: String,
    events: Vector[Event]
  ): DiagnosticScope = {
    val label = DiagnosticScopeLabels.getOrElse(scope, scope)
    val groups = events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .toVector
      .sortBy(_._1)
      .map {
        case (key, xs) =>
          val latest = xs.reverse.collectFirst { case event if event.diagnosticRecord.nonEmpty => event.diagnosticRecord.get }
          DiagnosticGroup(
            scope = scope,
            label = label,
            diagnosticKey = key,
            count = xs.size.toLong,
            latestRecord = latest,
            recentExamples = xs.takeRight(20).reverse.map(_diagnostic_example(key, _))
          )
      }
    DiagnosticScope(scope, label, groups)
  }

  private def _diagnostic_example(
    diagnosticKey: String,
    event: Event
  ): DiagnosticExample =
    DiagnosticExample(
      observedAt = event.observedAt,
      diagnosticKey = diagnosticKey,
      operation = event.operation,
      kind = event.kind,
      sourceMode = event.sourceMode,
      backend = event.backend,
      diagnosticRecord = event.diagnosticRecord
    )

  private def _normalize_scope(scope: String): String =
    scope.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-')

  private def _runtime_metric_points(
    entityAccessMetrics: Vector[EntityAccessMetricEntry]
  ): Vector[RuntimeMetricPoint] =
    Vector(
      _event_points("web.request", "requests", _htmlEvents, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("action.execution", "executions", _actionEvents, _outcome_label),
      _event_points("authorization.decision", "decisions", _authorizationEvents, event =>
        Map("outcome" -> (if (event.error) "denied" else "allowed")) ++
          event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("dsl.chokepoint", "chokepoints", _dslEvents, _outcome_label),
      _event_points("validation", "failures", _validationEvents, event =>
        event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("operation-request-validation", "failures", _operationRequestValidationEvents, event =>
        event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("blob.operation", "operations", _blobEvents, event =>
        event.labels ++ _outcome_label(event)
      ),
      _payload_externalization_points,
      _open_telemetry_export_points,
      _entity_access_points(entityAccessMetrics)
    ).flatten

  private def _event_points(
    scope: String,
    name: String,
    events: Vector[Event],
    labels: Event => Map[String, String]
  ): Vector[RuntimeMetricPoint] =
    events
      .groupBy(event => _clean_labels(labels(event)))
      .toVector
      .sortBy(_._1.toVector.sortBy(_._1).mkString("|"))
      .map {
        case (labelset, xs) =>
          val durations = xs.flatMap(_.elapsedMillis)
          RuntimeMetricPoint(
            scope = scope,
            name = name,
            labels = labelset,
            count = xs.size.toLong,
            errorCount = xs.count(_.error).toLong,
            durationCount = durations.size.toLong,
            durationTotalMillis = durations.sum,
            durationMinMillis = durations.minOption,
            durationMaxMillis = durations.maxOption
          )
      }

  private def _payload_externalization_points: Vector[RuntimeMetricPoint] =
    _payloadExternalizationEvents
      .groupBy(x => Map("status" -> x.status, "payload_kind" -> x.payloadKind, "destination" -> x.destination))
      .toVector
      .sortBy(_._1.toVector.sortBy(_._1).mkString("|"))
      .map {
        case (labels, xs) =>
          RuntimeMetricPoint(
            scope = "diagnostic-payload.externalization",
            name = "payloads",
            labels = labels,
            count = xs.size.toLong,
            errorCount = xs.count(x => Set("failed", "unavailable", "not_supported").contains(x.status)).toLong
          )
      }

  private def _open_telemetry_export_points: Vector[RuntimeMetricPoint] =
    _openTelemetryExportEvents
      .groupBy(x => Map("status" -> x.status, "signal" -> x.signal))
      .toVector
      .sortBy(_._1.toVector.sortBy(_._1).mkString("|"))
      .map {
        case (labels, xs) =>
          RuntimeMetricPoint(
            scope = "otel.export",
            name = "exports",
            labels = labels,
            count = xs.size.toLong,
            errorCount = xs.count(x => Set("failed", "unavailable").contains(x.status)).toLong
          )
      }

  private def _entity_access_points(
    entries: Vector[EntityAccessMetricEntry]
  ): Vector[RuntimeMetricPoint] =
    entries.map { entry =>
      RuntimeMetricPoint(
        scope = "entity-access",
        name = entry.name,
        labels = _clean_labels(Map(
          "entity" -> entry.entity.getOrElse(""),
          "source" -> entry.source.getOrElse(""),
          "outcome" -> entry.outcome.getOrElse(""),
          "reason" -> entry.reason.getOrElse(""),
          "working_set_state" -> entry.workingSetState.getOrElse("")
        )),
        count = entry.count,
        errorCount = if (entry.outcome.exists(x => x == "failure" || x == "denied")) entry.count else 0L
      )
    }

  private def _outcome_label(event: Event): Map[String, String] =
    Map("outcome" -> (if (event.error) "failure" else "success"))

  private def _status_class(status: Int): String =
    s"${status / 100}xx"

  private def _clean_labels(values: Map[String, String]): Map[String, String] =
    values.map { case (k, v) => k -> _normalize_label(v) }.filter(_._2.nonEmpty)

  private def _normalize_label(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).replace(' ', '-')

  private def _snapshot(
    events: Vector[Event],
    recent: Vector[RequestEntry]
  ): Snapshot = {
    val now = java.time.Instant.now.toEpochMilli
    Snapshot(
      _summary(events, now),
      _buckets(events, now, 60 * 1000L, 60),
      _buckets(events, now, 60 * 60 * 1000L, 24),
      _buckets(events, now, 24 * 60 * 60 * 1000L, 30),
      recent
    )
  }

  private def _summary(
    events: Vector[Event],
    now: Long
  ): CountSummary =
    CountSummary(
      _count(events, Long.MinValue),
      _count(events, now - 24 * 60 * 60 * 1000L),
      _count(events, now - 60 * 60 * 1000L),
      _count(events, now - 60 * 1000L)
    )

  private def _count(
    events: Vector[Event],
    since: Long
  ): CountWindow = {
    val xs = events.filter(_.observedAt >= since)
    CountWindow(xs.size.toLong, xs.count(_.error).toLong)
  }

  private def _buckets(
    events: Vector[Event],
    now: Long,
    widthMillis: Long,
    size: Int
  ): Vector[RequestBucket] = {
    val current = now / widthMillis
    val byPeriod = events.groupBy(_.observedAt / widthMillis).map {
      case (period, xs) => period -> RequestBucket(period, xs.size.toLong, xs.count(_.error).toLong)
    }
    val start = current - (size - 1)
    (start to current).toVector.map { period =>
      byPeriod.getOrElse(period, RequestBucket(period, 0L, 0L))
    }
  }
}
