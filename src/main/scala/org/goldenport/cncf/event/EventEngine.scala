package org.goldenport.cncf.event

import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{CommitParticipant, CommitRecorder, PrepareResult, TransactionContext}

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventEngine extends CommitParticipant {
  def stage(events: Seq[DomainEvent]): Unit
  def stagedEvents: Seq[DomainEvent]
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
    private var _events: Vector[DomainEvent] = Vector.empty

    def stage(events: Seq[DomainEvent]): Unit =
      _events = events.toVector

    def stagedEvents: Seq[DomainEvent] =
      _events

    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("EventEngine.prepare")
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit = {
      recorder.record("EventEngine.commit")
      dataStore.commit(tx)
    }

    def abort(tx: TransactionContext): Unit =
      recorder.record("EventEngine.abort")
  }
}
