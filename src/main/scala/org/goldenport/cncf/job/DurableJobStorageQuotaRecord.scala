package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Closed operational values. No signed Job schema or authorization is stored here.
 *
 * @since   Oct.  1, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobStorageQuotaPending(
  token: String,
  expectedrevision: Option[Long],
  canonicalbytes: Long,
  canonicalsha256: String
)

private[job] final case class DurableJobStorageQuotaEntry(
  jobid: String,
  committedbytes: Long,
  committedsha256: Option[String],
  committedrevision: Option[Long],
  pending: Vector[DurableJobStorageQuotaPending]
) {
  def heldBytes: Long = pending.foldLeft(committedbytes)((size, value) => math.max(size, value.canonicalbytes))
}

private[job] final case class DurableJobStorageQuotaRecord(
  policy: DurableJobStorageResourcePolicy,
  entries: Vector[DurableJobStorageQuotaEntry]
)

private[job] final case class DurableJobStorageQuotaMetadata(
  jobid: String,
  canonicalbytes: Long,
  canonicalsha256: String,
  providerrevision: Long
) {
  def entry: DurableJobStorageQuotaEntry =
    DurableJobStorageQuotaEntry(jobid, canonicalbytes, Some(canonicalsha256), Some(providerrevision), Vector.empty)
}

/* An issued token is evidence of one successful persisted CAS, never a counter. */
private[job] final case class DurableJobStorageQuotaLease(
  jobid: String,
  pending: DurableJobStorageQuotaPending
)

private[job] final case class DurableJobStorageQuotaSnapshot(
  retainedjobs: Int,
  canonicalbytes: Long,
  pendingreservations: Int,
  maxstoredjobs: Int,
  maxcanonicalbytes: Long
)

private[job] final case class DurableJobStorageQuotaEntity(
  id: EntityId,
  canonicaljson: String
) {
  def toRecord(): Record = Record.dataAuto("id" -> id.value, "canonicalJson" -> canonicaljson)
}

private[job] object DurableJobStorageQuotaEntity {
  private val _store_row_mapping = DurableJobStoreMapping.text(Vector("canonicalJson"))

  val Collection: EntityCollectionId = EntityCollectionId("cncf", "builtin", "durableJobStorageQuota")

  def entityId: Consequence[EntityId] =
    EntityId.bridgeFromParts(
      major = Collection.major,
      minor = Collection.minor,
      collection = Collection,
      timestamp = Instant.EPOCH,
      entropy = "quota_v1"
    )

  given EntityPersistent[DurableJobStorageQuotaEntity] with {
    override def storeRowMapping: Option[org.goldenport.cncf.entity.EntityStoreRowMapping] = Some(_store_row_mapping)
    def id(entity: DurableJobStorageQuotaEntity): EntityId = entity.id
    def toRecord(entity: DurableJobStorageQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobStorageQuotaEntity): Record = entity.toRecord()
    def fromRecord(record: Record): Consequence[DurableJobStorageQuotaEntity] =
      for {
        id <- EntityId.createC(record)
        expected <- entityId
        _ <- if (id == expected) Consequence.unit else DurableJobStorageResourcePolicy.invalid
        canonical <- record.getString("canonicalJson").filter(_.nonEmpty)
          .map(Consequence.success).getOrElse(DurableJobStorageResourcePolicy.invalid)
      } yield DurableJobStorageQuotaEntity(id, canonical)
    override def fromStoreRecord(record: Record): Consequence[DurableJobStorageQuotaEntity] = fromRecord(record)
  }

  given EntityPersistentCreate[DurableJobStorageQuotaEntity] with {
    override def storeRowMapping: Option[org.goldenport.cncf.entity.EntityStoreRowMapping] = Some(_store_row_mapping)
    def id(entity: DurableJobStorageQuotaEntity): Option[EntityId] = Some(entity.id)
    def collection(entity: DurableJobStorageQuotaEntity): EntityCollectionId = Collection
    def toRecord(entity: DurableJobStorageQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobStorageQuotaEntity): Record = entity.toRecord()
  }
}
