package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Separate operational identity; retained release markers never grant ID reuse.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobOwnedBlobQuotaState {
  case Pending, Published, Released
}

private[job] final case class DurableJobOwnedBlobQuotaEntry(
  blobid: String,
  ownersha256: String,
  contenttype: String,
  bytesize: Long,
  payloadsha256: String,
  token: Option[String],
  state: DurableJobOwnedBlobQuotaState,
  bindingsha256: Option[String]
)

private[job] final case class DurableJobOwnedBlobQuotaRecord(
  policy: DurableJobOwnedBlobResourcePolicy,
  entries: Vector[DurableJobOwnedBlobQuotaEntry]
)

private[job] final case class DurableJobOwnedBlobQuotaMetadata(
  blobid: String, ownersha256: String, contenttype: String,
  bytesize: Long, payloadsha256: String, bindingsha256: String
) {
  def entry: DurableJobOwnedBlobQuotaEntry = DurableJobOwnedBlobQuotaEntry(
    blobid, ownersha256, contenttype, bytesize, payloadsha256, None,
    DurableJobOwnedBlobQuotaState.Published, Some(bindingsha256))

  def matches(entry: DurableJobOwnedBlobQuotaEntry): Boolean =
    blobid == entry.blobid && ownersha256 == entry.ownersha256 &&
      contenttype == entry.contenttype && bytesize == entry.bytesize &&
      payloadsha256 == entry.payloadsha256 && entry.bindingsha256.forall(_ == bindingsha256)
}

/* Issuance is evidence of one native reservation CAS. */
private[job] final case class DurableJobOwnedBlobQuotaLease(entry: DurableJobOwnedBlobQuotaEntry)

private[job] final case class DurableJobOwnedBlobQuotaSnapshot(
  occupiedblobs: Long, payloadbytes: Long, pendingblobs: Long,
  releasedblobs: Long, ledgerentries: Int,
  maxownedblobs: Int, maxownedbytes: Long, maxpayloadbytes: Int
)

private[job] final case class DurableJobOwnedBlobQuotaEntity(id: EntityId, canonicaljson: String) {
  def toRecord(): Record = Record.dataAuto("id" -> id.value, "canonicalJson" -> canonicaljson)
}

private[job] object DurableJobOwnedBlobQuotaEntity {
  val Collection: EntityCollectionId = EntityCollectionId("cncf", "builtin", "durableJobOwnedBlobStorageQuota")

  def entityId: Consequence[EntityId] = EntityId.bridgeFromParts(
    major = Collection.major, minor = Collection.minor, collection = Collection,
    timestamp = Instant.EPOCH, entropy = "owned_blob_quota_v1")

  given EntityPersistent[DurableJobOwnedBlobQuotaEntity] with {
    def id(entity: DurableJobOwnedBlobQuotaEntity): EntityId = entity.id
    def toRecord(entity: DurableJobOwnedBlobQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobOwnedBlobQuotaEntity): Record = entity.toRecord()
    def fromRecord(record: Record): Consequence[DurableJobOwnedBlobQuotaEntity] = for {
      id <- EntityId.createC(record)
      expected <- entityId
      _ <- if (id == expected) Consequence.unit else DurableJobOwnedBlobResourcePolicy.invalid
      canonical <- record.getString("canonicalJson").filter(_.nonEmpty)
        .map(Consequence.success).getOrElse(DurableJobOwnedBlobResourcePolicy.invalid)
    } yield DurableJobOwnedBlobQuotaEntity(id, canonical)
    override def fromStoreRecord(record: Record): Consequence[DurableJobOwnedBlobQuotaEntity] = fromRecord(record)
  }

  given EntityPersistentCreate[DurableJobOwnedBlobQuotaEntity] with {
    def id(entity: DurableJobOwnedBlobQuotaEntity): Option[EntityId] = Some(entity.id)
    def collection(entity: DurableJobOwnedBlobQuotaEntity): EntityCollectionId = Collection
    def toRecord(entity: DurableJobOwnedBlobQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobOwnedBlobQuotaEntity): Record = entity.toRecord()
  }
}
