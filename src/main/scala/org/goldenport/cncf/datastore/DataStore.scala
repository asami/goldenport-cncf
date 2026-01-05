package org.goldenport.cncf.datastore

import org.goldenport.cncf.unitofwork.{CommitParticipant, CommitRecorder, PrepareResult, TransactionContext}

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait DataStore extends CommitParticipant

object DataStore {
  def noop(
    recorder: CommitRecorder = CommitRecorder.noop
  ): DataStore =
    new NoopDataStore(recorder)

  private final class NoopDataStore(
    recorder: CommitRecorder
  ) extends DataStore {
    def prepare(tx: TransactionContext): PrepareResult = {
      recorder.record("DataStore.prepare")
      PrepareResult.Prepared
    }

    def commit(tx: TransactionContext): Unit =
      recorder.record("DataStore.commit")

    def abort(tx: TransactionContext): Unit =
      recorder.record("DataStore.abort")
  }
}
