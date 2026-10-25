package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, QueryDirective, QueryLimit, QueryOrder,
  QueryProjection, OrderDirection, Query as StoreQuery}
import org.goldenport.cncf.directive.Query as Predicate
import org.goldenport.record.Record

/** Dispatch-bound generic history reader; no write, replay or dispatch methods.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
final class ManagedEventHistory private[cncf] (
  context: ExecutionContext,
  verifyLease: () => Consequence[Unit],
  readRows: ManagedEventHistory.Query => Consequence[Vector[Record]]
) {
  private val _context = context
  private val _verify_lease = verifyLease
  private val _read_rows = readRows

  def readC(query: ManagedEventHistory.Query): Consequence[ManagedEventHistory.Snapshot] = {
    given ExecutionContext = _context
    for {
      _ <- ManagedEventHistory.validateQueryC(query)
      _ <- EventPolicyEngine.default.authorizeIntrospection
      _ <- _verify_lease()
      rows <- _read_rows(query)
      _ <- _verify_lease()
      snapshot <- ManagedEventHistory.acceptRowsC(query, rows, _context.clock.instant())
    } yield snapshot
  }
}

object ManagedEventHistory {
  final case class Query(correlationNamespace: String, correlationKey: String,
    names: Vector[String], limit: Int = 20)
  final case class Snapshot private[cncf] (events: Vector[EventRecord], observedAt: Instant,
    truncated: Boolean, coverage: String = "retained-correlated-window")

  /** Implemented only by the owning managed provider, never by a caller adapter.
    * Must perform a single bounded data read without schema mutation or fallback.
    */
  private[cncf] trait Provider {
    private[cncf] def readCommittedEventHistoryC(collection: DataStore.CollectionId,
      query: Query): Consequence[Vector[Record]]
  }

  def bindForCallC(core: ActionCall.Core): Consequence[ManagedEventHistory] =
    if (core == null || core.executionContext == null || core.component.isEmpty ||
        core.component.get.subsystem.isEmpty)
      Consequence.stateInvalid("Event history requires its owning managed call")
    else core.component.get.subsystem.get.bindEventHistoryC(core.executionContext)

  def validateQueryC(query: Query): Consequence[Unit] =
    if (query == null || query.names == null || query.names.isEmpty || query.names.size > 32 ||
        query.names.distinct.size != query.names.size || query.limit < 1 || query.limit > 100 ||
        !query.names.forall(name => EventHistoryCorrelation.validText(name, 512) && name.length <= 128))
      Consequence.valueInvalid("Event history query is invalid")
    else EventHistoryCorrelation(query.correlationNamespace, query.correlationKey).validateC

  private[cncf] def directiveC(query: Query): Consequence[QueryDirective] =
    validateQueryC(query).map { _ =>
      QueryDirective(StoreQuery.Expr(Predicate.And(Vector(
        Predicate.Eq(EventHistoryCorrelation.namespaceField, query.correlationNamespace),
        Predicate.Eq(EventHistoryCorrelation.keyField, query.correlationKey),
        Predicate.Or(query.names.map(name => Predicate.Eq(EventHistoryCorrelation.nameField, name))),
        Predicate.Gt(EventHistoryCorrelation.sequenceField, 0L)))),
        projection = QueryProjection.Fields(Vector("id", "event_json", EventHistoryCorrelation.namespaceField,
          EventHistoryCorrelation.keyField, EventHistoryCorrelation.nameField, EventHistoryCorrelation.sequenceField)),
        order = QueryOrder.By(EventHistoryCorrelation.sequenceField, OrderDirection.Desc),
        limit = QueryLimit.Limit(query.limit + 1))
    }

  /** Validates the provider page, including the look-ahead row; never filters/sorts it. */
  private[cncf] def acceptRowsC(query: Query, rows: Vector[Record], observedAt: Instant): Consequence[Snapshot] =
    validateQueryC(query).flatMap { _ =>
      if (rows == null || observedAt == null || rows.size > query.limit + 1)
        Consequence.stateConflict("Event history provider exceeded its bounded page")
      else rows.foldLeft(Consequence.success(Vector.empty[EventRecord])) { (previous, row) =>
        previous.flatMap(events => _decode_row_c(query, row).map(events :+ _))
      }.flatMap { events =>
        if (events.map(_.id.value).distinct.size != events.size ||
            events.map(_.sequence).sliding(2).exists(pair => pair.size == 2 && pair.head <= pair.last))
          Consequence.stateConflict("Event history provider returned duplicate or unordered facts")
        else Consequence.success(Snapshot(events.take(query.limit), observedAt, events.size > query.limit))
      }
    }

  private def _decode_row_c(query: Query, row: Record): Consequence[EventRecord] = {
    def _field_(name: String): Option[Any] = Option(row).flatMap { value =>
      val fields = value.fields.filter(_.key == name)
      if (fields.size == 1) Some(fields.head.value.single) else None
    }
    val decoded = _field_("event_json") match {
      case Some(bytes: String) if bytes.startsWith(EventRecordJsonV1.schemaVersion + ":") =>
        EventRecordJsonV1.decodeStorageC(bytes)
      case Some(bytes: String) if bytes.startsWith(EventRecordJsonV2.schemaVersion + ":") =>
        EventRecordJsonV2.decodeStorageC(bytes)
      case _ => Consequence.stateConflict("Event history canonical storage is missing or unsupported")
    }
    decoded.flatMap { event =>
      val sequence = _field_(EventHistoryCorrelation.sequenceField) match {
        case Some(value: Long) => Some(value)
        case Some(value: Int) => Some(value.toLong)
        case _ => None
      }
      val matches = _field_("id").contains(event.id.value) && event.persistent &&
        event.lane == EventLane.Transactional && query.names.contains(event.name) &&
        _field_(EventHistoryCorrelation.namespaceField).contains(query.correlationNamespace) &&
        _field_(EventHistoryCorrelation.keyField).contains(query.correlationKey) &&
        _field_(EventHistoryCorrelation.nameField).contains(event.name) && sequence.contains(event.sequence)
      EventHistoryCorrelation.fromAttributesC(event.attributes).flatMap { correlation =>
        if (matches && correlation.contains(EventHistoryCorrelation(query.correlationNamespace, query.correlationKey)))
          Consequence.success(event)
        else Consequence.stateConflict("Event history row differs from its canonical correlated event")
      }
    }
  }
}
