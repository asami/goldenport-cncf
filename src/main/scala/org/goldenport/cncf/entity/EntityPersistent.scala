package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.id.Identified
import org.goldenport.id.Identifiable
import org.goldenport.record.Record
import org.goldenport.record.RecordEncoder
import org.goldenport.record.RecordCodex
import org.goldenport.record.RecordPresentable
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Feb. 22, 2026
 *  version Feb. 27, 2026
 *  version Mar. 24, 2026
 * @version Apr. 17, 2026
 * @author  ASAMI, Tomoharu
 */
trait EntityPersistent[E] extends RecordCodex[E]
    with Identified[E, EntityId]

object EntityPersistent {
  def derived[E <: EntityPersistable](
    from: Record => Consequence[E]
  ): EntityPersistent[E] = new EntityPersistent[E] {
    def id(e: E) = e.id
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
  }
}

trait EntityPersistentCreate[E] extends RecordEncoder[E]
    with Identifiable[E, EntityId] {
  def collection(e: E): EntityCollectionId
}

object EntityPersistentCreate {
  def derived[E <: EntityPersistableCreate](
    collectionid: EntityCollectionId
  ): EntityPersistentCreate[E] = new EntityPersistentCreate[E] {
    def id(e: E): Option[EntityId] = e.id
    def toRecord(e: E) = e.toRecord()
    def collection(e: E): EntityCollectionId = collectionid
  }
}

trait EntityPersistentQuery[E] extends RecordCodex[E] {
  def collection(e: E): EntityCollectionId
}

object EntityPersistentQuery {
  def derived[E <: EntityPersistableQuery](
    from: Record => Consequence[E],
    collectionid: EntityCollectionId
  ): EntityPersistentQuery[E] = new EntityPersistentQuery[E] {
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
    def collection(e: E): EntityCollectionId = collectionid
  }
}

trait EntityPersistable extends RecordPresentable {
  def id: EntityId
}

trait EntityDisplayable extends RecordPresentable {
  def toDisplayRecord(view: String, fields: Vector[String]): Record
}

trait EntityPersistableCreate extends RecordPresentable {
  def id: Option[EntityId]
}
object EntityPersistableCreate {
  // given entityPersistentCreate: EntityPersistentCreate[EntityPersistableCreate] = new EntityPersistentCreate[EntityPersistableCreate] {
  //   def id(e: EntityPersistableCreate): Option[EntityId] = e.id
  //   def collection(e: EntityPersistableCreate): CollectionId = e.collecionId
  //   def toRecord(e: EntityPersistableCreate) = e.toRecord
  // }
}

trait EntityPersistableQuery extends RecordPresentable

trait EntityPersistentUpdate[E] extends RecordCodex[E] {
  def collection(e: E): EntityCollectionId
}

object EntityPersistentUpdate {
  def derived[E <: EntityPersistableUpdate](
    from: Record => Consequence[E],
    collectionid: EntityCollectionId
  ): EntityPersistentUpdate[E] = new EntityPersistentUpdate[E] {
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
    def collection(e: E): EntityCollectionId = collectionid
  }
}

trait EntityPersistableUpdate extends RecordPresentable
