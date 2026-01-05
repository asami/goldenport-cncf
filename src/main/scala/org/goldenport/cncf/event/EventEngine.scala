package org.goldenport.cncf.event

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
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventEngine extends CommitParticipant {
  def stage(events: Seq[DomainEvent]): Unit
  def stagedEvents: Seq[DomainEvent]
  private[event] def preparedEvents: Seq[DomainEvent]
  private[event] def committedEvents: Seq[DomainEvent]
}

object EventEngine {
  def noop(
    dataStore: DataStore,
    recorder: CommitRecorder = CommitRecorder.noop
  ): EventEngine =
    new NoopEventEngine(dataStore, recorder)

  private final class NoopEventEngine(
    dataStore: DataStore,
    recorder: CommitRecorder
  ) extends EventEngine {
    private var _staged: Vector[DomainEvent] = Vector.empty
    private var _prepared: Option[Vector[DomainEvent]] = None
    private var _committed: Vector[DomainEvent] = Vector.empty

    def stage(events: Seq[DomainEvent]): Unit =
      if (_prepared.isEmpty) {
        _staged = events.toVector
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
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit = {
      recorder.record("EventEngine.commit")
      _committed = _prepared.getOrElse(_staged)
      dataStore.commit(tx)
    }

    def abort(tx: TransactionContext): Unit = {
      recorder.record("EventEngine.abort")
      _prepared = None
      _staged = Vector.empty
      _committed = Vector.empty
    }
  }
}
