package org.goldenport.cncf.http

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

  private final case class Event(
    observedAt: Long,
    error: Boolean,
    diagnosticKey: Option[String] = None,
    diagnosticRecord: Option[Record] = None,
    operation: Option[String] = None,
    kind: Option[String] = None,
    sourceMode: Option[String] = None,
    backend: Option[String] = None
  )

  private var _htmlEvents = Vector.empty[Event]
  private var _actionEvents = Vector.empty[Event]
  private var _authorizationEvents = Vector.empty[Event]
  private var _dslEvents = Vector.empty[Event]
  private var _validationEvents = Vector.empty[Event]
  private var _operationRequestValidationEvents = Vector.empty[Event]
  private var _blobEvents = Vector.empty[Event]
  private var _recent = Vector.empty[RequestEntry]

  def recordHtmlRequest(
    method: String,
    path: String,
    status: Int,
    elapsedMillis: Long
  ): Unit = synchronized {
    val now = java.time.Instant.now.toEpochMilli
    _htmlEvents = (_htmlEvents :+ Event(now, status >= 400)).takeRight(10000)
    _recent = (_recent :+ RequestEntry(now, method, path, status, elapsedMillis)).takeRight(12)
  }

  def recordActionCall(error: Boolean): Unit = synchronized {
    _actionEvents = (_actionEvents :+ Event(java.time.Instant.now.toEpochMilli, error)).takeRight(10000)
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
    _blobEvents = (_blobEvents :+ Event(
      observedAt = java.time.Instant.now.toEpochMilli,
      error = error,
      diagnosticKey = if (error) diagnosticKey.filter(_.nonEmpty) else None,
      diagnosticRecord = if (error) diagnosticRecord else None,
      operation = Some(operation).filter(_.nonEmpty),
      kind = kind.filter(_.nonEmpty),
      sourceMode = sourceMode.filter(_.nonEmpty),
      backend = backend.filter(_.nonEmpty)
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

  private def _diagnostic_records(events: Vector[Event]): Map[String, Record] =
    events
      .filter(_.error)
      .flatMap(e => e.diagnosticKey.map(_ -> e.diagnosticRecord))
      .groupBy(_._1)
      .flatMap { case (key, values) => values.reverse.collectFirst { case (_, Some(record)) => key -> record } }

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
