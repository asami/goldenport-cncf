package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}

/**
 * Captured storage admission only. Subsystem retains resource/lease ownership.
 * @since   Oct.  1, 2026
 *  version Oct.  8, 2026
 * @version Nov. 17, 2026
 */
private[cncf] final class ComponentRecordStorageBinding(
  declaration: Option[ComponentRecordStorageDeclaration], reserved: Vector[DataStore.CollectionId],
  activeLease: () => Option[SystemNodeResourceLease],
  inheritLease: SystemNodeResourceLease => Consequence[Unit],
  resolveStore: ComponentDataStore.Request => Consequence[DataStore]
) {
  private val _declaration = declaration
  private val _reserved = reserved
  private val _active_lease = activeLease
  private val _inherit_lease = inheritLease
  private val _resolve_store = resolveStore

  def bindC(componentId: ComponentId, storeName: String, collection: DataStore.CollectionId,
    entryId: DataStore.StringEntryId, context: ExecutionContext): Consequence[ComponentRecordStorage] =
    if (context == null || entryId == null || entryId.print == null ||
        entryId.print.isEmpty || entryId.print.trim != entryId.print)
      Consequence.stateConflict("Component record storage identity is incomplete")
    else _with_store_c(componentId, storeName, collection) { (store, lease) =>
      val thread = Thread.currentThread()
      Consequence.success(new ComponentRecordStorage(store, collection, entryId, context, () =>
        if (Thread.currentThread() != thread || !_active_lease().contains(lease))
          Consequence.stateInvalid("Component record capability escaped its issuing dispatch lease")
        else _inherit_lease(lease)))
    }

  private[cncf] def _with_startup_store_c(
    componentid: ComponentId,
    storename: String,
    collection: DataStore.CollectionId
  )(use: DataStore => Consequence[Unit]): Consequence[Unit] =
    _with_store_c(componentid, storename, collection)((store, _) => use(store))

  private def _with_store_c[A](
    componentid: ComponentId,
    storename: String,
    collection: DataStore.CollectionId
  )(use: (DataStore, SystemNodeResourceLease) => Consequence[A]): Consequence[A] =
    for {
      declaration <- _declaration.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Subsystem has no component record Create declaration"))
      _ <- declaration.validateReservedC(_reserved)
      _ <- declaration.admitC(componentid, storename, collection)
      lease <- _active_lease().map(Consequence.success).getOrElse(
        Consequence.stateInvalid("Component record storage requires an active managed dispatch lease"))
      _ <- _inherit_lease(lease)
      store <- _resolve_store(ComponentDataStore.Request.forComponent(componentid, storename))
      _ <- store match {
        case sql: org.goldenport.cncf.datastore.sql.SqlDataStore if sql.managedResourceOption.nonEmpty => Consequence.unit
        case _ => Consequence.configurationInvalid("Component record storage requires a managed SQL resource")
      }
      result <- use(store, lease)
    } yield result
}
