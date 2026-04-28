package org.goldenport.cncf.http

/*
 * @since   Apr. 12, 2026
 * @version Apr. 28, 2026
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
    failureKind: Option[String] = None
  )

  private var _htmlEvents = Vector.empty[Event]
  private var _actionEvents = Vector.empty[Event]
  private var _authorizationEvents = Vector.empty[Event]
  private var _dslEvents = Vector.empty[Event]
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
    failureKind: Option[String]
  ): Unit = synchronized {
    val kind = if (denied) failureKind.filter(_.nonEmpty) else None
    _authorizationEvents = (_authorizationEvents :+ Event(java.time.Instant.now.toEpochMilli, denied, kind)).takeRight(10000)
  }

  def recordDslChokepoint(error: Boolean): Unit = synchronized {
    _dslEvents = (_dslEvents :+ Event(java.time.Instant.now.toEpochMilli, error)).takeRight(10000)
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

  def authorizationFailureKindCounts: Map[String, Long] = synchronized {
    _authorizationEvents
      .filter(_.error)
      .groupBy(_.failureKind.getOrElse("unknown"))
      .view
      .mapValues(_.size.toLong)
      .toMap
  }

  def dslChokepointSnapshot: Snapshot = synchronized {
    _snapshot(_dslEvents, Vector.empty)
  }

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
