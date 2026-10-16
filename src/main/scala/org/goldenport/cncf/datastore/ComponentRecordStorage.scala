package org.goldenport.cncf.datastore

import cats.free.Free
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.record.Record

/** Fixed-identity storage capability; not actor or Operation authorization.
  * Each call verifies its issuing managed lease before provider access.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class ComponentRecordStorage private[cncf] (
  store: DataStore, collection: DataStore.CollectionId, entryId: DataStore.StringEntryId,
  context: ExecutionContext, verify: () => Consequence[Unit]
) {
  private val _space = new DataStoreSpace().useDataStore(store)
  private val _collection = collection
  private val _entry_id = entryId
  private val _context = context
  private val _verify = verify

  def loadC(): Consequence[Option[Record]] = {
    given ExecutionContext = _context
    _verify().flatMap(_ => _space.dataStore(_collection))
      .flatMap(_.load(_collection, _entry_id))
  }

  /** Create result alone is not a committed-readback publication receipt. */
  def createC(record: Record): Consequence[Unit] = {
    given ExecutionContext = _context
    _verify().flatMap { _ =>
      if (record == null || record.fields.exists(_.key == "id"))
        Consequence.stateConflict("Component record payload cannot select its physical identity")
      else _space.dataStore(_collection).flatMap(_.create(_collection, _entry_id, record))
    }
  }

  /** Freeze a fixed-target Create for canonical Workflow completion only.
    * Construction performs no write. The program is data, not a retained
    * storage grant or a committed-publication receipt; its executing host
    * must independently admit this collection in the same completion store.
    */
  def stageCreateC(record: Record): Consequence[ExecUowM[Unit]] =
    _verify().flatMap { _ =>
      if (record == null || record.fields.exists(_.key == "id"))
        Consequence.stateConflict("Component record payload cannot select its physical identity")
      else WorkflowClosingLocalIntent.createC(
        EntityVersionedSideEffect.Create(_collection, _entry_id, record)).map { intent =>
          ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent)))
        }
    }
}

object ComponentRecordStorage {
  def bindForCallC(core: ActionCall.Core, storeName: String,
    collection: DataStore.CollectionId, entryId: DataStore.StringEntryId): Consequence[ComponentRecordStorage] =
    if (core == null || core.executionContext == null || core.component.isEmpty ||
        core.component.get.subsystem.isEmpty)
      Consequence.stateInvalid("Component record storage requires an owning managed call")
    else core.component.get.subsystem.get.bindComponentRecordStorageC(
      core.component.get.componentId, storeName, collection, entryId, core.executionContext)
}
