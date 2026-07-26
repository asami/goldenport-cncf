package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.id.Identified
import org.goldenport.id.Identifiable
import org.goldenport.record.Record
import org.goldenport.record.RecordEncoder
import org.goldenport.record.RecordCodex
import org.goldenport.record.RecordPresentable
import org.goldenport.observation.Descriptor
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Feb. 22, 2026
 *  version Feb. 27, 2026
 *  version Mar. 24, 2026
 *  version Apr. 26, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
trait EntityPersistent[E] extends RecordCodex[E]
    with Identified[E, EntityId] {
  def toStoreRecord(e: E): Record =
    toRecord(e)

  def fromStoreRecord(r: Record): Consequence[E] =
    fromRecord(r)

  def storeFieldName(logicalName: String): String =
    logicalName

  def toViewRecord(e: E, view: String, fields: Vector[String]): Record = {
    val fallback = toRecord(e)
    if (fields.isEmpty)
      fallback
    else
      e match {
        case displayable: EntityDisplayable => displayable.toDisplayRecord(view, fields)
        case _ => EntityPersistent.filterViewRecord(fallback, fields)
      }
  }

  def securityAttributes(e: E): Option[SecurityAttributes] =
    SimpleEntityStorageShapePolicy.securityAttributesFromRecord(toStoreRecord(e))

  def authorizationRecord(e: E): Record = {
    val base = toRecord(e)
    authorizationRecord(e, base)
  }

  def authorizationRecord(e: E, base: Record): Record =
    securityAttributes(e) match {
      case Some(attributes) =>
        EntityPersistent.withoutSecurityAttributes(base) ++ Record.dataAuto(
          "owner_id" -> attributes.ownerId.id.value,
          "group_id" -> attributes.groupId.id.value,
          "privilege_id" -> attributes.privilegeId.id.value,
          "permission" -> SimpleEntityStorageShapePolicy.permissionJson(attributes.rights)
        )
      case None =>
        base
    }
}

object EntityPersistent {
  private[cncf] def _decode_store_record[E](
    persistent: EntityPersistent[E],
    collectionid: EntityCollectionId,
    record: Record
  ): Consequence[E] =
    persistent.fromStoreRecord(record).flatMap { entity =>
      val actual = persistent.id(entity).collection
      if (actual.name == collectionid.name)
        Consequence.success(entity)
      else
        _collection_mismatch(actual, collectionid)
    }

  private[cncf] def _collection_mismatch[E](
    actual: EntityCollectionId,
    expected: EntityCollectionId
  ): Consequence[E] =
    Consequence.stateInvalid(
      "Entity codec collection does not match the requested collection",
      Vector(
        Descriptor.Facet.Policy("entity.persistence.collection"),
        Descriptor.Facet.Reason("entity-codec-collection-mismatch"),
        Descriptor.Facet.Expected(expected.print),
        Descriptor.Facet.Actual(actual.print)
      )
    )

  def withoutSecurityAttributes(record: Record): Record =
    SimpleEntityStorageShapePolicy.withoutSecurityFields(record)

  def derived[E <: EntityPersistable](
    from: Record => Consequence[E]
  ): EntityPersistent[E] = new EntityPersistent[E] {
    def id(e: E) = e.id
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
  }

  def filterViewRecord(
    record: Record,
    fields: Vector[String]
  ): Record = {
    val source = record.asMap
    val rows = fields.flatMap { field =>
      source.find { case (key, _) => _normalized_field_name(key) == _normalized_field_name(field) }
        .map { case (_, value) => field -> value }
    }
    Record.dataAuto(rows*)
  }

  private def _normalized_field_name(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}

trait EntityPersistentCreate[E] extends RecordEncoder[E]
    with Identifiable[E, EntityId] {
  def collection(e: E): EntityCollectionId

  def toStoreRecord(e: E): Record =
    toRecord(e)

  def storeFieldName(logicalName: String): String =
    logicalName
}

object EntityPersistentCreate {
  def derived[E <: EntityPersistableCreate](
      collectionId: EntityCollectionId
  ): EntityPersistentCreate[E] = new EntityPersistentCreate[E] {
    def id(e: E): Option[EntityId] = e.id
    def toRecord(e: E) = e.toRecord()
    def collection(e: E): EntityCollectionId = collectionId
  }

  def fromPersistent[E](
      persistent: EntityPersistent[E]
  ): EntityPersistentCreate[E] =
    new EntityPersistentCreate[E] {
      def id(entity: E): Option[EntityId] =
        Some(persistent.id(entity))
      def collection(entity: E): EntityCollectionId =
        persistent.id(entity).collection
      def toRecord(entity: E): Record =
        persistent.toRecord(entity)
      override def toStoreRecord(entity: E): Record =
        persistent.toStoreRecord(entity)
  }
}

trait EntityPersistentQuery[E] extends RecordCodex[E] {
  def collection(e: E): EntityCollectionId

  def toStoreRecord(e: E): Record =
    toRecord(e)

  def fromStoreRecord(r: Record): Consequence[E] =
    fromRecord(r)

  def storeFieldName(logicalName: String): String =
    logicalName
}

object EntityPersistentQuery {
  def derived[E <: EntityPersistableQuery](
    from: Record => Consequence[E],
      collectionId: EntityCollectionId
  ): EntityPersistentQuery[E] = new EntityPersistentQuery[E] {
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
    def collection(e: E): EntityCollectionId = collectionId
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

  def toStoreRecord(e: E): Record =
    toRecord(e)

  def fromStoreRecord(r: Record): Consequence[E] =
    fromRecord(r)

  def storeFieldName(logicalName: String): String =
    logicalName
}

object EntityPersistentUpdate {
  def derived[E <: EntityPersistableUpdate](
    from: Record => Consequence[E],
      collectionId: EntityCollectionId
  ): EntityPersistentUpdate[E] = new EntityPersistentUpdate[E] {
    def toRecord(e: E) = e.toRecord()
    def fromRecord(r: Record) = from(r)
    def collection(e: E): EntityCollectionId = collectionId
  }
}

trait EntityPersistableUpdate extends RecordPresentable
