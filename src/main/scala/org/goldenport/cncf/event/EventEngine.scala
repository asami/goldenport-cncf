package org.goldenport.cncf.event

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitParticipant, CommitRecorder, PrepareResult, TransactionContext}

/**
 * EventEngine.prepare fixes the set of events to be committed
 * for the given transaction.
 *
 * Events staged before prepare are treated as pending.
 * Calling prepare transitions them into a fixed, immutable set
 * associated with the transaction.
 *
 * After prepare:
 * - no events may be added, removed, or modified for the transaction
 * - commit operates only on the prepared events
 * - abort discards the prepared events
 *
 * prepare performs no external side effects such as persistence
 * or publication; it only validates and fixes the event set.
 */
/*
 * @since   Jan.  6, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventEngine extends CommitParticipant {
  def stage(events: Seq[DomainEvent]): Unit
  def stage(events: Seq[DomainEvent], factory: EventRecordFactory): Unit
  def emit(events: Seq[DomainEvent]): Consequence[Vector[EventRecord]]
  def emit(events: Seq[DomainEvent], factory: EventRecordFactory): Consequence[Vector[EventRecord]]
  def eventStore: EventStore
  def stagedEvents: Seq[DomainEvent]
  private[event] def preparedEvents: Seq[DomainEvent]
  private[event] def committedEvents: Seq[DomainEvent]
}

object EventEngine {
  def noop(
    datastore: DataStore,
    recorder: CommitRecorder = CommitRecorder.noop,
    eventstore: EventStore = EventStore.inMemory,
    recordfactory: EventRecordFactory = EventRecordFactory.standard
  ): EventEngine =
    new NoopEventEngine(datastore, recorder, eventstore, recordfactory)

  private final class NoopEventEngine(
    datastore: DataStore,
    recorder: CommitRecorder,
    val eventStore: EventStore,
    recordfactory: EventRecordFactory
  ) extends EventEngine {
    private var _staged: Vector[DomainEvent] = Vector.empty
    private var _staged_records: Vector[EventRecord] = Vector.empty
    private var _prepared: Option[Vector[DomainEvent]] = None
    private var _prepared_records: Option[Vector[EventRecord]] = None
    private var _committed: Vector[DomainEvent] = Vector.empty

    def stage(events: Seq[DomainEvent]): Unit =
      stage(events, recordfactory)

    def stage(events: Seq[DomainEvent], factory: EventRecordFactory): Unit =
      if (_prepared.isEmpty) {
        _staged = events.toVector
        _staged_records = _staged.map(factory.create(_, EventLane.Transactional))
      }

    def emit(events: Seq[DomainEvent]): Consequence[Vector[EventRecord]] =
      emit(events, recordfactory)

    def emit(
      events: Seq[DomainEvent],
      factory: EventRecordFactory
    ): Consequence[Vector[EventRecord]] = {
      val records = events.toVector.map(e =>
        factory.create(e, EventLane.NonTransactional)
      )
      eventStore.append(records)
    }

    def stagedEvents: Seq[DomainEvent] =
      _staged

    private[event] def preparedEvents: Seq[DomainEvent] =
      _prepared.getOrElse(Vector.empty)

    private[event] def committedEvents: Seq[DomainEvent] =
      _committed

    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("EventEngine.prepare")
      _prepared = Some(_staged)
      _prepared_records = Some(_staged_records)
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit = {
      recorder.record("EventEngine.commit")
      _committed = _prepared.getOrElse(_staged)
      if (_committed.nonEmpty) {
        val records = _prepared_records.getOrElse(_staged_records)
        val _ = eventStore.append(records)
      }
      datastore.commit(tx)
      _prepared = None
      _prepared_records = None
      _staged = Vector.empty
      _staged_records = Vector.empty
    }

    def abort(tx: TransactionContext): Unit = {
      recorder.record("EventEngine.abort")
      _prepared = None
      _prepared_records = None
      _staged = Vector.empty
      _staged_records = Vector.empty
      _committed = Vector.empty
    }
  }
}
