package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.provisional.observation.Observation
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.directive.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp.*

/*
 * @since   Feb. 24, 2026
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class EntityStoreSpace {
  private var _entity_stores: Vector[EntityStore] = Vector.empty

  def dataStoreCollection(id: EntityId): Consequence[DataStore.CollectionId] = ???

  def dataStoreCollection(id: EntityCollectionId): Consequence[DataStore.CollectionId] = ???

  def dataStoreEntryId(id: EntityId): Consequence[DataStore.EntryId] =
    Consequence(DataStore.EntryId(id))

  private def _by_collection(cid: EntityCollectionId): Consequence[EntityStore] =
    Consequence.successOrFail(_entity_stores.find(_.isAccept(cid)))(
      Observation.referenceNotFound(cid)
    )

  def create[T](op: EntityStoreCreate[T])(using ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    given EntityPersistentCreate[T] = op.tc
    val cid = op.tc.collection(op.entity)
    for {
      entitystore <- _by_collection(cid)
      r <- entitystore.create(op.entity)
    } yield r
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

  def delete(op: EntityStoreDelete)(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      entitystore <- _by_collection(op.id.collection)
      r <- entitystore.delete(op.id)
    } yield r

  def search[T](op: EntityStoreSearch[T])(using ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    for {
      entitystore <- _by_collection(op.query.collection)
      r <- entitystore.search(op.query)
    } yield r
  }
}
