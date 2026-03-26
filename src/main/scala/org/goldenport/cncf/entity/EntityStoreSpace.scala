package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.provisional.observation.Observation
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.directive.*
import org.simplemodeling.model.directive.Update
import org.goldenport.cncf.unitofwork.UnitOfWorkOp.*

/*
 * @since   Feb. 24, 2026
 *  version Feb. 25, 2026
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
class EntityStoreSpace {
  private var _entity_stores: Vector[EntityStore] = Vector.empty

  def addEntityStore(es: EntityStore): EntityStoreSpace = {
    _entity_stores = _entity_stores :+ es
    this
  }

  def dataStoreCollection(id: EntityId): Consequence[DataStore.CollectionId] =
    dataStoreCollection(id.collection)

  def dataStoreCollection(id: EntityCollectionId): Consequence[DataStore.CollectionId] =
    Consequence.success(DataStore.CollectionId.EntityStore(id))

  def dataStoreEntryId(id: EntityId): Consequence[DataStore.EntryId] =
    Consequence(DataStore.EntryId(id))

  private def _by_collection(cid: EntityCollectionId): Consequence[EntityStore] =
    Consequence.successOrServiceProviderByKeyNotFound(
      _entity_stores.find(_.isAccept(cid))
    )("entitystore", cid.print)

  def create[T](op: EntityStoreCreate[T])(using ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    given EntityPersistentCreate[T] = op.tc
    val cid = op.tc.collection(op.entity)
    for {
      entitystore <- _by_collection(cid)
      r <- entitystore.create(op.entity)
    } yield r
  }

  def importSeed[T](
    seed: EntityStoreSeed[T]
  )(using ctx: ExecutionContext, tc: EntityPersistent[T]): Consequence[Unit] =
    seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
      z.flatMap { _ =>
        val createTc = new EntityPersistentCreate[T] {
          def id(e: T): Option[EntityId] = Some(tc.id(e))
          def toRecord(e: T): org.goldenport.record.Record = tc.toRecord(e)
          def collection(e: T): EntityCollectionId = tc.id(e).collection
        }
        create(EntityStoreCreate(entry.entity, createTc)).map(_ => ())
      }
    }

  def load[T](op: EntityStoreLoad[T])(using ctx: ExecutionContext): Consequence[Option[T]] = {
    given EntityPersistent[T] = op.tc
    for {
      entitystore <- _by_collection(op.id.collection)
      r <- entitystore.load(op.id)
    } yield r
  }

  def save[T](op: EntityStoreSave[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    for {
      entitystore <- _by_collection(op.tc.id(op.entity).collection)
      r <- entitystore.save(op.entity)
    } yield r
  }

  def update[T](op: EntityStoreUpdate[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    for {
      entitystore <- _by_collection(op.tc.id(op.entity).collection)
      r <- entitystore.update(op.entity)
    } yield r
  }

  def updateById[T](op: EntityStoreUpdateById[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    val changes = Update.toChangesRecord(op.tc.toRecord(op.patch))
    if (changes.isEmpty)
      Consequence.unit
    else
      for {
        cid <- dataStoreCollection(op.id)
        dsid <- dataStoreEntryId(op.id)
        ds <- ctx.dataStoreSpace.dataStore(cid)
        r <- ds.update(cid, dsid, changes)
      } yield r
  }

  def delete(op: EntityStoreDelete)(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      entitystore <- _by_collection(op.id.collection)
      r <- entitystore.delete(op.id)
    } yield r

  def deleteHard(op: EntityStoreDeleteHard)(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      entitystore <- _by_collection(op.id.collection)
      r <- entitystore.deleteHard(op.id)
    } yield r

  def search[T](op: EntityStoreSearch[T])(using ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    for {
      entitystore <- _by_collection(op.query.collection)
      r <- entitystore.search(op.query)
    } yield r
  }
}

object EntityStoreSpace {
  def create(conf: ResolvedConfiguration): EntityStoreSpace = {
    val ess = new EntityStoreSpace()
    val mes = EntityStore.standard()
    ess.addEntityStore(mes)
  }
}
