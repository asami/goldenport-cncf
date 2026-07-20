package org.goldenport.cncf.http

import java.time.Instant
import org.goldenport.cncf.metrics.{ComponentMetricEntry, ComponentMetricsRegistry, EntityAccessMetricEntry, EntityAccessMetricsRegistry, RuntimeMetricPoint, RuntimeMetricsCatalog, RuntimeMetricsSnapshot}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.record.Record

/*
 * @since   Apr. 12, 2026
 *  version May. 11, 2026
 * @version Jul. 20, 2026
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

  private var _html_events = Vector.empty[Event]
  private var _action_events = Vector.empty[Event]
  private var _authorization_events = Vector.empty[Event]
  private var _dsl_events = Vector.empty[Event]
  private var _validation_events = Vector.empty[Event]
  private var _operation_request_validation_events = Vector.empty[Event]
  private var _blob_events = Vector.empty[Event]
  private var _rule_events = Vector.empty[Event]
  private var _spi_events = Vector.empty[Event]
  private var _process_execution_events = Vector.empty[Event]
  private var _resource_tree_events = Vector.empty[Event]
  private var _resource_tree_query_events = Vector.empty[Event]
  private var _service_container_events = Vector.empty[Event]
  private var _payload_externalization_events = Vector.empty[PayloadExternalizationEvent]
  private var _open_telemetry_export_events = Vector.empty[OpenTelemetryExportEvent]
  private var _recent = Vector.empty[RequestEntry]

  private val _diagnostic_scope_labels: Map[String, String] = Map(
    "authorization" -> "Authorization",
    "validation" -> "Validation",
    "operation-request-validation" -> "Operation Request Validation",
    "blob" -> "Blob",
    "rule" -> "Rule",
    "spi" -> "SPI",
    "process-execution" -> "Process Execution",
    "resource-tree" -> "Resource Tree",
    "resource-tree-query" -> "Resource Tree Query",
    "service-container" -> "Service Container"
  )

  def recordHtmlRequest(
    method: String,
    path: String,
    status: Int,
    elapsedMillis: Long
  ): Unit = synchronized {
    val now = java.time.Instant.now.toEpochMilli
    _html_events = (_html_events :+ Event(
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
    _action_events = (_action_events :+ Event(
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
    _authorization_events = (_authorization_events :+ Event(java.time.Instant.now.toEpochMilli, denied, kind, if (denied) diagnosticRecord else None)).takeRight(10000)
  }

  def recordDslChokepoint(error: Boolean): Unit = synchronized {
    _dsl_events = (_dsl_events :+ Event(java.time.Instant.now.toEpochMilli, error)).takeRight(10000)
  }

  def recordValidation(
    operation: String,
    diagnosticKey: Option[String],
    diagnosticRecord: Option[Record] = None
  ): Unit = synchronized {
    _validation_events = (_validation_events :+ Event(
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
    _operation_request_validation_events = (_operation_request_validation_events :+ Event(
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
    val cleandiagnostickey = if (error) diagnosticKey.filter(_.nonEmpty) else None
    _blob_events = (_blob_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnosticRecord else None,
      operation = Some(operation).filter(_.nonEmpty),
      kind = kind.filter(_.nonEmpty),
      sourceMode = sourceMode.filter(_.nonEmpty),
      backend = backend.filter(_.nonEmpty),
      labels = _clean_labels(Map(
        "kind" -> kind.getOrElse(""),
        "source" -> sourceMode.getOrElse(""),
        "backend" -> backend.getOrElse(""),
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordRuleExecution(
    operation: String,
    ruleset: String,
    error: Boolean,
    diagnostickey: Option[String] = None,
    diagnosticrecord: Option[Record] = None,
    elapsedmillis: Option[Long] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnostickey.filter(_.nonEmpty) else None
    _rule_events = (_rule_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnosticrecord else None,
      operation = Some(operation).filter(_.nonEmpty),
      elapsedMillis = elapsedmillis,
      labels = _clean_labels(Map(
        "operation" -> operation,
        "rule_set" -> ruleset,
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordSpiInvocation(
    contract: String,
    operation: String,
    providerComponent: String,
    socketComponent: String,
    error: Boolean,
    selectionBasis: Option[String] = None,
    diagnosticKey: Option[String] = None,
    diagnosticRecord: Option[Record] = None,
    elapsedMillis: Option[Long] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnosticKey.filter(_.nonEmpty) else None
    _spi_events = (_spi_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnosticRecord else None,
      operation = Some(operation).filter(_.nonEmpty),
      elapsedMillis = elapsedMillis,
      labels = _clean_labels(Map(
        "contract" -> contract,
        "operation" -> operation,
        "provider_component" -> providerComponent,
        "socket_component" -> socketComponent,
        "selection_basis" -> selectionBasis.getOrElse(""),
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordProcessExecution(
    capability: String,
    driver: String,
    error: Boolean = false,
    diagnosticKey: Option[String] = None,
    diagnosticRecord: Option[Record] = None,
    termination: Option[String] = None,
    elapsedMillis: Option[Long] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnosticKey.filter(_.nonEmpty) else None
    _process_execution_events = (_process_execution_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnosticRecord else None,
      elapsedMillis = elapsedMillis,
      labels = _clean_labels(Map(
        "capability" -> capability,
        "driver" -> driver,
        "termination" -> termination.getOrElse(""),
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordResourceTreeSnapshot(
    tree: String,
    provider: String,
    error: Boolean,
    diagnostic: Option[ConclusionDiagnostics.Classification] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnostic.map(_.diagnosticKey).filter(_.nonEmpty) else None
    _resource_tree_events = (_resource_tree_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnostic.map(_.toRecord) else None,
      labels = _clean_labels(Map(
        "tree" -> tree,
        "provider" -> provider,
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordResourceTreeQuery(
    tree: String,
    provider: String,
    selector: String,
    limits: org.goldenport.cncf.resource.ResourceTreeQueryLimits,
    visiteddirectories: Option[Int] = None,
    matchedentries: Option[Int] = None,
    error: Boolean,
    diagnostic: Option[ConclusionDiagnostics.Classification] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnostic.map(_.diagnosticKey).filter(_.nonEmpty) else None
    _resource_tree_query_events = (_resource_tree_query_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnostic.map(_.toRecord) else None,
      labels = _clean_labels(Map(
        "tree" -> tree,
        "provider" -> provider,
        "selector" -> selector,
        "max_depth" -> limits.maxDepth.toString,
        "max_visited_directories" -> limits.maxVisitedDirectories.toString,
        "max_entries" -> limits.maxEntries.toString,
        "max_entry_bytes" -> limits.maxEntryBytes.toString,
        "max_total_bytes" -> limits.maxTotalBytes.toString,
        "visited_directories" -> visiteddirectories.map(_.toString).getOrElse(""),
        "matched_entries" -> matchedentries.map(_.toString).getOrElse(""),
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordServiceContainerLifecycle(
    operation: String,
    ownershipmode: Option[String],
    ownerkind: Option[String],
    ownerid: Option[String],
    serviceid: Option[String],
    cleanuppolicy: Option[String],
    status: Option[String],
    error: Boolean,
    diagnostic: Option[ConclusionDiagnostics.Classification] = None,
    elapsedmillis: Option[Long] = None
  ): Unit = synchronized {
    val cleandiagnostickey = if (error) diagnostic.map(_.diagnosticKey).filter(_.nonEmpty) else None
    _service_container_events = (_service_container_events :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = cleandiagnostickey,
      diagnosticRecord = if (error) diagnostic.map(_service_container_diagnostic_record) else None,
      operation = Some(operation).filter(_.nonEmpty),
      elapsedMillis = elapsedmillis,
      labels = _clean_labels(Map(
        "operation" -> operation,
        "ownership_mode" -> ownershipmode.getOrElse(""),
        "owner_kind" -> ownerkind.getOrElse(""),
        "owner_id" -> ownerid.getOrElse(""),
        "service_id" -> serviceid.getOrElse(""),
        "cleanup_policy" -> cleanuppolicy.getOrElse(""),
        "status" -> status.getOrElse(""),
        "diagnostic_key" -> cleandiagnostickey.getOrElse("")
      ))
    )).takeRight(10000)
  }

  def recordDiagnosticPayloadExternalization(
    payloadKind: String,
    status: String,
    destination: String
  ): Unit = synchronized {
    _payload_externalization_events = (_payload_externalization_events :+ PayloadExternalizationEvent(
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
    _open_telemetry_export_events = (_open_telemetry_export_events :+ OpenTelemetryExportEvent(
      observedAt = java.time.Instant.now.toEpochMilli,
      signal = _normalize_label(signal),
      status = _normalize_label(status)
    )).takeRight(10000)
  }

  def htmlSnapshot: Snapshot = synchronized {
    _snapshot(_html_events, _recent)
  }

  def actionCallSnapshot: Snapshot = synchronized {
    _snapshot(_action_events, Vector.empty)
  }

  def authorizationDecisionSnapshot: Snapshot = synchronized {
    _snapshot(_authorization_events, Vector.empty)
  }

  def authorizationDiagnosticCounts: Map[String, Long] = synchronized {
    _authorization_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def authorizationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_authorization_events)
  }

  def dslChokepointSnapshot: Snapshot = synchronized {
    _snapshot(_dsl_events, Vector.empty)
  }

  def validationSnapshot: Snapshot = synchronized {
    _snapshot(_validation_events, Vector.empty)
  }

  def validationDiagnosticCounts: Map[String, Long] = synchronized {
    _validation_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def validationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_validation_events)
  }

  def operationRequestValidationSnapshot: Snapshot = synchronized {
    _snapshot(_operation_request_validation_events, Vector.empty)
  }

  def operationRequestValidationDiagnosticCounts: Map[String, Long] = synchronized {
    _operation_request_validation_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def operationRequestValidationDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_operation_request_validation_events)
  }

  def blobOperationSnapshot: Snapshot = synchronized {
    _snapshot(_blob_events, Vector.empty)
  }

  def blobDiagnosticCounts: Map[String, Long] = synchronized {
    _blob_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def blobDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_blob_events)
  }

  def ruleExecutionSnapshot: Snapshot = synchronized {
    _snapshot(_rule_events, Vector.empty)
  }

  def ruleDiagnosticCounts: Map[String, Long] = synchronized {
    _rule_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def ruleDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_rule_events)
  }

  def spiInvocationSnapshot: Snapshot = synchronized {
    _snapshot(_spi_events, Vector.empty)
  }

  def spiDiagnosticCounts: Map[String, Long] = synchronized {
    _spi_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def spiDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_spi_events)
  }

  def processExecutionSnapshot: Snapshot = synchronized {
    _snapshot(_process_execution_events, Vector.empty)
  }

  def processExecutionDiagnosticCounts: Map[String, Long] = synchronized {
    _process_execution_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def processExecutionDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_process_execution_events)
  }

  def resourceTreeSnapshot: Snapshot = synchronized {
    _snapshot(_resource_tree_events, Vector.empty)
  }

  def resourceTreeDiagnosticCounts: Map[String, Long] = synchronized {
    _resource_tree_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def resourceTreeDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_resource_tree_events)
  }

  def resourceTreeQuerySnapshot: Snapshot = synchronized {
    _snapshot(_resource_tree_query_events, Vector.empty)
  }

  def resourceTreeQueryDiagnosticCounts: Map[String, Long] = synchronized {
    _resource_tree_query_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def resourceTreeQueryDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_resource_tree_query_events)
  }

  def serviceContainerLifecycleSnapshot: Snapshot = synchronized {
    _snapshot(_service_container_events, Vector.empty)
  }

  def serviceContainerDiagnosticCounts: Map[String, Long] = synchronized {
    _service_container_events
      .filter(_.error)
      .groupBy(_.diagnosticKey.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def serviceContainerDiagnosticRecords: Map[String, Record] = synchronized {
    _diagnostic_records(_service_container_events)
  }

  def diagnosticScopes: Vector[DiagnosticScope] = synchronized {
    Vector(
      _diagnostic_scope("authorization", _authorization_events),
      _diagnostic_scope("validation", _validation_events),
      _diagnostic_scope("operation-request-validation", _operation_request_validation_events),
      _diagnostic_scope("blob", _blob_events),
      _diagnostic_scope("rule", _rule_events),
      _diagnostic_scope("spi", _spi_events),
      _diagnostic_scope("process-execution", _process_execution_events),
      _diagnostic_scope("resource-tree", _resource_tree_events),
      _diagnostic_scope("resource-tree-query", _resource_tree_query_events),
      _diagnostic_scope("service-container", _service_container_events)
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
  ): RuntimeMetricsSnapshot =
    runtimeMetricsSnapshot(entityAccessMetrics, ComponentMetricsRegistry.shared)

  def runtimeMetricsSnapshot(
    entityAccessMetrics: EntityAccessMetricsRegistry,
    componentmetrics: ComponentMetricsRegistry
  ): RuntimeMetricsSnapshot = synchronized {
    RuntimeMetricsSnapshot(
      generatedAt = Instant.now(),
      points = _runtime_metric_points(entityAccessMetrics.snapshot(), componentmetrics.snapshot()),
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

  private def _service_container_diagnostic_record(
    diagnostic: ConclusionDiagnostics.Classification
  ): Record =
    Record.dataAuto(
      "diagnosticKey" -> diagnostic.diagnosticKey,
      "taxonomyCategory" -> diagnostic.taxonomyCategory,
      "taxonomySymptom" -> diagnostic.taxonomySymptom,
      "causeKind" -> diagnostic.causeKind,
      "webStatus" -> diagnostic.webStatus,
      "statusText" -> diagnostic.statusText,
      "policy" -> diagnostic.policy.filter(_.startsWith("service-container."))
    )

  private def _diagnostic_scope(
    scope: String,
    events: Vector[Event]
  ): DiagnosticScope = {
    val label = _diagnostic_scope_labels.getOrElse(scope, scope)
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
    entityAccessMetrics: Vector[EntityAccessMetricEntry],
    componentmetrics: Vector[ComponentMetricEntry]
  ): Vector[RuntimeMetricPoint] =
    Vector(
      _event_points("web.request", "requests", _html_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("action.execution", "executions", _action_events, _outcome_label),
      _event_points("authorization.decision", "decisions", _authorization_events, event =>
        Map("outcome" -> (if (event.error) "denied" else "allowed")) ++
          event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("dsl.chokepoint", "chokepoints", _dsl_events, _outcome_label),
      _event_points("validation", "failures", _validation_events, event =>
        event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("operation-request-validation", "failures", _operation_request_validation_events, event =>
        event.diagnosticKey.map("diagnostic_key" -> _).toMap
      ),
      _event_points("blob.operation", "operations", _blob_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("rule.execution", "executions", _rule_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("spi.invocation", "invocations", _spi_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("process.execution", "executions", _process_execution_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("resource-tree.snapshot", "snapshots", _resource_tree_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("resource-tree.query", "queries", _resource_tree_query_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _event_points("service-container.lifecycle", "operations", _service_container_events, event =>
        event.labels ++ _outcome_label(event)
      ),
      _payload_externalization_points,
      _open_telemetry_export_points,
      _entity_access_points(entityAccessMetrics),
      _component_points(componentmetrics)
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
    _payload_externalization_events
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
    _open_telemetry_export_events
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

  private def _component_points(
    entries: Vector[ComponentMetricEntry]
  ): Vector[RuntimeMetricPoint] =
    entries.map(_.toRuntimeMetricPoint)

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
    val byperiod = events.groupBy(_.observedAt / widthMillis).map {
      case (period, xs) => period -> RequestBucket(period, xs.size.toLong, xs.count(_.error).toLong)
    }
    val start = current - (size - 1)
    (start to current).toVector.map { period =>
      byperiod.getOrElse(period, RequestBucket(period, 0L, 0L))
    }
  }
}
