package org.goldenport.cncf.job

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  DataStore, EntityMutationProviderFeature, EntityVersionedMutationDataStore,
  OrderDirection, Query, QueryDirective, QueryLimit, QueryOrder, ResultRange, SearchableDataStore
}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.record.Record

/*
 * Complete native metadata census. Only bounded immutable accounting facts survive.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOwnedBlobQuotaInventory {
  import BlobRepository.given
  import DurableJobOwnedBlobResourcePolicy.*
  import DurableJobOwnedBlobPayloads.{ownershipAttribute, ownerAttribute, bindingAttribute, bindingFingerprint}

  def admitProvider()(using ctx: ExecutionContext): Consequence[Unit] = protect {
    (for {
      jobcollection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStoreEntity.Collection)
      blobcollection <- ctx.entityStoreSpace.dataStoreCollection(BlobRepository.CollectionId)
      quotacollection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobOwnedBlobQuotaEntity.Collection)
      jobprovider <- ctx.dataStoreSpace.dataStore(jobcollection)
      blobprovider <- ctx.dataStoreSpace.dataStore(blobcollection)
      quotaprovider <- ctx.dataStoreSpace.dataStore(quotacollection)
      _ <- jobprovider match {
        case provider: SearchableDataStore if (provider eq blobprovider) && (provider eq quotaprovider) =>
          provider match {
            case native: EntityVersionedMutationDataStore if native.entityMutationProviderCapabilities.supportsAll(Set(
                EntityMutationProviderFeature.OptimisticCompareAndSet,
                EntityMutationProviderFeature.AuthoritativeRecordResult)) => Consequence.unit
            case _ => unavailable
          }
        case _ => unavailable
      }
    } yield ()).recoverWith(_ => unavailable)
  }

  def scan()(using ctx: ExecutionContext): Consequence[Vector[DurableJobOwnedBlobQuotaMetadata]] = protect {
    for {
      _ <- admitProvider()
      collection <- ctx.entityStoreSpace.dataStoreCollection(BlobRepository.CollectionId)
      facts <- _pages(collection)
    } yield facts
  }

  def metadata(blob: Blob): Consequence[Option[DurableJobOwnedBlobQuotaMetadata]] = protect {
    if (blob == null || blob.attributes == null) invalid
    else {
      val attributes = blob.attributes
      val reserved = Vector(ownershipAttribute, ownerAttribute, bindingAttribute)
      if (!reserved.exists(attributes.contains)) Consequence.success(None)
      else {
        val valid = attributes.get(ownershipAttribute).contains("exclusive-v1") &&
          attributes.get(ownerAttribute).exists(hex(_, 64)) && attributes.get(bindingAttribute).exists(hex(_, 64)) &&
          blob.sourceMode == BlobSourceMode.Managed && blob.externalUrl.isEmpty &&
          blob.contentType.exists(validContent) && blob.storageRef.exists(validRef) &&
          blob.filename.forall(boundedText(_, maximumFilenameBytes, false)) &&
          blob.byteSize.exists(size => size >= 0L && size <= maximumPayloadBytes) && blob.digest.exists(digest)
        if (!valid) invalid
        else canonicalId(blob.id.value).flatMap { id =>
          val binding = bindingFingerprint(id, blob.storageRef.get, blob.contentType.get, blob.byteSize.get, blob.digest.get)
          if (!attributes.get(bindingAttribute).contains(binding)) invalid
          else Consequence.success(Some(DurableJobOwnedBlobQuotaMetadata(id.value, attributes(ownerAttribute),
            blob.contentType.get.header, blob.byteSize.get, blob.digest.get.toLowerCase(Locale.ROOT), binding)))
        }
      }
    }
  }

  private def _pages(collection: DataStore.CollectionId)
      (using ctx: ExecutionContext): Consequence[Vector[DurableJobOwnedBlobQuotaMetadata]] = {
    var offset = 0
    var previousid: Option[String] = None
    var accumulated = Vector.empty[DurableJobOwnedBlobQuotaMetadata]
    var complete = false
    while (!complete) {
      val remaining = maximumCensusRows + 1 - offset
      if (remaining <= 0) return unavailable
      val limit = math.min(pageSize, remaining)
      ctx.dataStoreSpace.search(collection, QueryDirective(Query.Empty,
        order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(limit), offset = offset)) match {
        case Consequence.Failure(_) => return unavailable
        case Consequence.Success(page) =>
          val rangevalid = page.range match {
            case ResultRange.Exact => true
            case ResultRange.Limited(value) => value == limit
          }
          if (page.records.size > limit || page.cursor.nonEmpty || !rangevalid || offset + page.records.size > maximumCensusRows)
            return unavailable
          val decoded = page.records.foldLeft[Consequence[(Option[String], Vector[DurableJobOwnedBlobQuotaMetadata])]](
            Consequence.success(previousid -> accumulated)) { (result, row) => result.flatMap { (previous, entries) =>
              for {
                fact <- _row(row)
                (id, metadata) = fact
                _ <- if (previous.forall(_ < id)) Consequence.unit else unavailable
                next = entries ++ metadata.toVector
                _ <- if (next.size <= maximumEntries) Consequence.unit else capacity
              } yield Some(id) -> next
            }
          }
          decoded match {
            case Consequence.Failure(_) => return unavailable
            case Consequence.Success((previous, entries)) =>
              previousid = previous
              accumulated = entries
          }
          offset += page.records.size
          complete = page.records.size < limit
      }
    }
    Consequence.success(accumulated)
  }

  private def _row(row: Record): Consequence[(String, Option[DurableJobOwnedBlobQuotaMetadata])] = for {
    stored <- row.getAny("id") match {
      case Some(value: String) => Consequence.success(value)
      case _ => invalid
    }
    id <- canonicalId(stored)
    blob <- summon[EntityPersistent[Blob]].fromStoreRecord(row)
    _ <- if (blob.id == id && blob.id.value == stored) Consequence.unit else invalid
    fact <- metadata(blob)
  } yield stored -> fact
}
