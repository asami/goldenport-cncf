package org.goldenport.cncf.association

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntitySearchScope, EntityStore, EntityVisibilityScope}
import org.goldenport.record.Record
import org.goldenport.text.Presentable
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Generic entity-to-entity association runtime foundation.
 *
 * @since   Apr. 27, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AssociationDomain(value: String) extends Presentable {
  def print: String = value
}

object AssociationDomain {
  val BlobAttachment: AssociationDomain = AssociationDomain("blob_attachment")
}

final case class AssociationStoragePolicy(
  defaultCollection: EntityCollectionId = AssociationStoragePolicy.DefaultCollection,
  domainCollections: Map[String, EntityCollectionId] = Map.empty
) {
  def collection(domain: AssociationDomain): EntityCollectionId =
    domainCollections.getOrElse(domain.value, defaultCollection)
}

object AssociationStoragePolicy {
  val DefaultCollection: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "association")

  val BlobAttachmentCollection: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "association_blob_attachment")

  def shared: AssociationStoragePolicy =
    AssociationStoragePolicy()

  def blobAttachmentDefault: AssociationStoragePolicy =
    AssociationStoragePolicy(
      domainCollections = Map(AssociationDomain.BlobAttachment.value -> BlobAttachmentCollection)
    )
}

final case class Association(
  id: EntityId,
  associationId: String,
  sourceEntityId: String,
  targetEntityId: String,
  targetKind: Option[String],
  role: String,
  associationDomain: AssociationDomain,
  sortOrder: Option[Int],
  createdAt: Instant,
  updatedAt: Instant,
  attributes: Map[String, String] = Map.empty
)

final case class AssociationCreate(
  id: Option[EntityId],
  associationId: String,
  sourceEntityId: String,
  targetEntityId: String,
  targetKind: Option[String],
  role: String,
  associationDomain: AssociationDomain,
  sortOrder: Option[Int],
  attributes: Map[String, String] = Map.empty,
  collectionId: EntityCollectionId
)

final case class AssociationFilter(
  domain: AssociationDomain,
  sourceEntityId: Option[String] = None,
  targetEntityId: Option[String] = None,
  targetKind: Option[String] = None,
  role: Option[String] = None
)

trait AssociationRepository {
  def create(association: AssociationCreate)(using ExecutionContext): Consequence[Association]
  def delete(association: Association)(using ExecutionContext): Consequence[Unit]
  def list(filter: AssociationFilter, offset: Int = 0, limit: Option[Int] = None)(using ExecutionContext): Consequence[Vector[Association]]
}

object AssociationRepository {
  def entityStore(
    storagepolicy: AssociationStoragePolicy = AssociationStoragePolicy.shared
  ): AssociationRepository =
    new EntityStoreAssociationRepository(storagepolicy)

  given EntityPersistent[Association] with {
    def id(e: Association): EntityId = e.id
    def toRecord(e: Association): Record = AssociationRecordCodec.toRecord(e)
    override def toStoreRecord(e: Association): Record = AssociationRecordCodec.toStoreRecord(e)
    def fromRecord(r: Record): Consequence[Association] = AssociationRecordCodec.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[Association] = AssociationRecordCodec.fromStoreRecord(r)
  }

  given EntityPersistentCreate[AssociationCreate] with {
    def id(e: AssociationCreate): Option[EntityId] = e.id
    def collection(e: AssociationCreate): EntityCollectionId = e.collectionId
    def toRecord(e: AssociationCreate): Record = AssociationRecordCodec.toRecord(e)
    override def toStoreRecord(e: AssociationCreate): Record = AssociationRecordCodec.toStoreRecord(e)
  }
}

final class EntityStoreAssociationRepository(
  storagepolicy: AssociationStoragePolicy
) extends AssociationRepository {
  import AssociationRepository.given

  def create(association: AssociationCreate)(using ctx: ExecutionContext): Consequence[Association] =
    for {
      result <- EntityStore.standard().create(association)
      loaded <- EntityStore.standard().load[Association](result.id)
      created <- loaded match {
        case Some(value) => Consequence.success(_normalize_association_id(value))
        case None => Consequence.operationNotFound(s"association entity:${result.id.print}")
      }
    } yield created

  def delete(association: Association)(using ctx: ExecutionContext): Consequence[Unit] =
    EntityStore.standard().deleteHard(association.id)

  def list(filter: AssociationFilter, offset: Int = 0, limit: Option[Int] = None)(using ctx: ExecutionContext): Consequence[Vector[Association]] =
    _store_search(filter, offset, limit)

  private def _store_search(
    filter: AssociationFilter,
    offset: Int,
    limit: Option[Int]
  )(using ctx: ExecutionContext): Consequence[Vector[Association]] = {
    val collection = storagepolicy.collection(filter.domain)
    EntityStore.standard()
      .search[Association](EntityQuery(collection, Query.plan(Record.empty), EntitySearchScope.Store, Some(EntityVisibilityScope.Admin)))
      .map { values =>
        val sorted = values.data.map(_normalize_association_id).filter(_matches(filter)).sortBy(x => (x.sortOrder.getOrElse(Int.MaxValue), x.createdAt.toString, x.associationId))
        Query.sliceValues(sorted, Some(offset), limit)
      }
  }

  private def _normalize_association_id(association: Association): Association =
    association.copy(id = _with_collection(association.id, storagepolicy.collection(association.associationDomain)))

  private def _with_collection(
    id: EntityId,
    collection: EntityCollectionId
  ): EntityId =
    if (id.collection == collection)
      id
    else
      EntityId(id.major, id.minor, collection, id.timestamp, id.entropy)

  private def _matches(filter: AssociationFilter)(association: Association): Boolean =
    association.associationDomain == filter.domain &&
      filter.sourceEntityId.forall(_ == association.sourceEntityId) &&
      filter.targetEntityId.forall(_ == association.targetEntityId) &&
      filter.targetKind.forall(x => association.targetKind.contains(x)) &&
      filter.role.forall(_ == association.role)
}

object AssociationRecordCodec {
  def toRecord(association: Association): Record =
    _record(
      id = Some(association.id),
      associationid = association.associationId,
      sourceid = association.sourceEntityId,
      targetid = association.targetEntityId,
      targetkind = association.targetKind,
      role = association.role,
      domain = association.associationDomain,
      sortorder = association.sortOrder,
      createdat = Some(association.createdAt),
      updatedat = Some(association.updatedAt),
      attributes = association.attributes
    )

  def toStoreRecord(association: Association): Record =
    toRecord(association)

  def toRecord(association: AssociationCreate): Record =
    _record(
      id = association.id,
      associationid = association.associationId,
      sourceid = association.sourceEntityId,
      targetid = association.targetEntityId,
      targetkind = association.targetKind,
      role = association.role,
      domain = association.associationDomain,
      sortorder = association.sortOrder,
      createdat = None,
      updatedat = None,
      attributes = association.attributes
    )

  def toStoreRecord(association: AssociationCreate): Record =
    toRecord(association)

  def fromRecord(record: Record): Consequence[Association] =
    fromStoreRecord(record)

  def fromStoreRecord(record: Record): Consequence[Association] =
    for {
      id <- EntityId.createC(record)
      associationid <- _string(record, "associationId", "association_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("associationId"))
      sourceid <- _string(record, "sourceEntityId", "source_entity_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("sourceEntityId"))
      targetid <- _string(record, "targetEntityId", "target_entity_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("targetEntityId"))
      role <- _string(record, "role").map(Consequence.success).getOrElse(Consequence.argumentMissing("role"))
      domain <- _string(record, "associationDomain", "association_domain").map(x => Consequence.success(AssociationDomain(x))).getOrElse(Consequence.argumentMissing("associationDomain"))
      createdat <- _instant(record, "createdAt", "created_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("createdAt"))
      updatedat <- _instant(record, "updatedAt", "updated_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("updatedAt"))
    } yield Association(
      id = id,
      associationId = associationid,
      sourceEntityId = sourceid,
      targetEntityId = targetid,
      targetKind = _string(record, "targetKind", "target_kind"),
      role = role,
      associationDomain = domain,
      sortOrder = _int(record, "sortOrder", "sort_order"),
      createdAt = createdat,
      updatedAt = updatedat,
      attributes = _attributes(record)
    )

  private def _record(
    id: Option[EntityId],
    associationid: String,
    sourceid: String,
    targetid: String,
    targetkind: Option[String],
    role: String,
    domain: AssociationDomain,
    sortorder: Option[Int],
    createdat: Option[Instant],
    updatedat: Option[Instant],
    attributes: Map[String, String]
  ): Record =
    Record.dataAuto(
      "id" -> id.map(_.value),
      "associationId" -> associationid,
      "sourceEntityId" -> sourceid,
      "targetEntityId" -> targetid,
      "targetKind" -> targetkind,
      "role" -> role,
      "associationDomain" -> domain.value,
      "sortOrder" -> sortorder,
      "createdAt" -> createdat.map(_.toString),
      "updatedAt" -> updatedat.map(_.toString),
      "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
    )

  private def _string(record: Record, names: String*): Option[String] =
    names.iterator.flatMap(record.getAny).map(_.toString.trim).find(_.nonEmpty)

  private def _int(record: Record, names: String*): Option[Int] =
    names.iterator.flatMap(record.getAny).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case other => scala.util.Try(other.toString.trim.toInt).toOption
    }.nextOption()

  private def _instant(record: Record, names: String*): Option[Instant] =
    names.iterator.flatMap(record.getAny).flatMap {
      case m: Instant => Some(m)
      case s: String => scala.util.Try(Instant.parse(s.trim)).toOption
      case other => scala.util.Try(Instant.parse(other.toString.trim)).toOption
    }.nextOption()

  private def _attributes(record: Record): Map[String, String] =
    record.getAny("attributes") match {
      case Some(rec: Record) => rec.asMap.map { case (k, v) => k -> v.toString }
      case Some(map: Map[?, ?]) => map.iterator.collect { case (k: String, v) => k -> v.toString }.toMap
      case _ => Map.empty
    }
}
