package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Operational lifetime evidence, separate from the signed durable Job record.
 *
 * @since   Oct.  1, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobLifetimeQuotaRootState {
  case Pending, Published
}

private[job] final case class DurableJobLifetimeQuotaEntry(
  jobid: String,
  ownersha256: String,
  rootsha256: String,
  roottoken: String,
  rootstate: DurableJobLifetimeQuotaRootState,
  paid: JobLifetimeSnapshot,
  remaining: Long,
  normalids: Vector[String],
  claims: Vector[String],
  cancelclaimed: Boolean
) {
  def snapshot: DurableJobLifetimeQuotaSnapshot = DurableJobLifetimeQuotaSnapshot(
    JobLifetimeBudget.Seed(paid, remaining, normalids.toSet, claims.toSet), cancelclaimed)

  def heldBytes: Long = Math.addExact(DurableJobLifetimeQuotaPolicy.entryOverheadBytes,
    Math.multiplyExact(DurableJobLifetimeQuotaPolicy.bytesPerChargedTask, paid.chargedtasks))
}

private[job] final case class DurableJobLifetimeQuotaRecord(
  policy: DurableJobLifetimeQuotaPolicy,
  entries: Vector[DurableJobLifetimeQuotaEntry]
)

private[job] final case class DurableJobLifetimeQuotaLease(
  jobid: String,
  roottoken: String,
  rootsha256: String,
  ownersha256: String
)

private[job] final case class DurableJobLifetimeQuotaSnapshot(
  seed: JobLifetimeBudget.Seed,
  cancelclaimed: Boolean
)

/* Numeric only: no native record, identity, token, owner or provider escapes. */
private[job] final case class DurableJobLifetimeQuotaResourceSnapshot(
  ledgerjobs: Int,
  heldbytes: Long,
  chargedtasks: Long,
  historycredits: Long,
  admissions: Long,
  cancelcredits: Long,
  maxchargedtasks: Long,
  maxhistorycredits: Long,
  maxadmissions: Long,
  maxledgerjobs: Int,
  maxledgerbytes: Long
)

private[job] final case class DurableJobLifetimeQuotaEntity(
  id: EntityId,
  canonicaljson: String
) {
  def toRecord(): Record = Record.dataAuto("id" -> id.value, "canonicalJson" -> canonicaljson)
}

private[job] object DurableJobLifetimeQuotaEntity {
  private val _store_row_mapping = DurableJobStoreMapping.text(Vector("canonicalJson"))

  val Collection: EntityCollectionId = EntityCollectionId("cncf", "builtin", "durableJobLifetimeQuota")

  def entityId: Consequence[EntityId] = EntityId.bridgeFromParts(
    major = Collection.major, minor = Collection.minor, collection = Collection,
    timestamp = Instant.EPOCH, entropy = "lifetime_quota_v1")

  given EntityPersistent[DurableJobLifetimeQuotaEntity] with {
    override def storeRowMapping: Option[org.goldenport.cncf.entity.EntityStoreRowMapping] = Some(_store_row_mapping)
    def id(entity: DurableJobLifetimeQuotaEntity): EntityId = entity.id
    def toRecord(entity: DurableJobLifetimeQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobLifetimeQuotaEntity): Record = entity.toRecord()
    def fromRecord(record: Record): Consequence[DurableJobLifetimeQuotaEntity] =
      DurableJobLifetimeQuotaPolicy.pure {
        (for {
          id <- EntityId.createC(record)
          expected <- entityId
          _ <- if (id == expected) Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
          canonical <- record.getString("canonicalJson").map(Consequence.success)
            .getOrElse(DurableJobLifetimeQuotaPolicy.invalid)
          _ <- DurableJobLifetimeQuotaCodec.validateWire(canonical)
        } yield DurableJobLifetimeQuotaEntity(id, canonical))
          .recoverWith(_ => DurableJobLifetimeQuotaPolicy.invalid)
      }
    override def fromStoreRecord(record: Record): Consequence[DurableJobLifetimeQuotaEntity] = fromRecord(record)
  }

  given EntityPersistentCreate[DurableJobLifetimeQuotaEntity] with {
    override def storeRowMapping: Option[org.goldenport.cncf.entity.EntityStoreRowMapping] = Some(_store_row_mapping)
    def id(entity: DurableJobLifetimeQuotaEntity): Option[EntityId] = Some(entity.id)
    def collection(entity: DurableJobLifetimeQuotaEntity): EntityCollectionId = Collection
    def toRecord(entity: DurableJobLifetimeQuotaEntity): Record = entity.toRecord()
    override def toStoreRecord(entity: DurableJobLifetimeQuotaEntity): Record = entity.toRecord()
  }
}
