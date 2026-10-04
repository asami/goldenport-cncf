package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  EntityMutationProviderFeature, EntityVersionedMutationDataStore, OrderDirection,
  Query, QueryDirective, QueryLimit, QueryOrder, ResultRange, SearchableDataStore
}
import org.goldenport.cncf.entity.{EntityPersistent, EntityRevisionBinding, EntityRevisionRepresentation}
import org.goldenport.record.Record

/*
 * Complete bounded native census. Only identity/byte/hash/revision facts survive a page.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobStorageQuotaInventory {
  def admitProvider()(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobStorageResourcePolicy.protect {
      (for {
        jobcollection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStoreEntity.Collection)
        quotacollection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStorageQuotaEntity.Collection)
        jobprovider <- ctx.dataStoreSpace.dataStore(jobcollection)
        quotaprovider <- ctx.dataStoreSpace.dataStore(quotacollection)
        _ <- jobprovider match {
          case provider: SearchableDataStore if provider eq quotaprovider =>
            provider match {
              case native: EntityVersionedMutationDataStore
                  if native.entityMutationProviderCapabilities.supportsAll(Set(
                    EntityMutationProviderFeature.OptimisticCompareAndSet,
                    EntityMutationProviderFeature.AuthoritativeRecordResult
                  )) => Consequence.unit
              case _ => DurableJobStorageResourcePolicy.unavailable
            }
          case _ => DurableJobStorageResourcePolicy.unavailable
        }
      } yield ()).recoverWith(_ => DurableJobStorageResourcePolicy.unavailable)
    }

  def scan(policy: DurableJobStorageResourcePolicy)(using ctx: ExecutionContext)
      : Consequence[Vector[DurableJobStorageQuotaMetadata]] =
    DurableJobStorageResourcePolicy.protect {
      (for {
        _ <- policy.validate
        _ <- admitProvider()
        collection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStoreEntity.Collection)
        binding <- ctx.entitySpace.entityOption(DurableJobStoreEntity.Collection)
          .flatMap(_.descriptor.revisionBinding)
          .filter(_.representation == EntityRevisionRepresentation.Detached)
          .map(Consequence.success).getOrElse(DurableJobStorageResourcePolicy.unavailable)
        result <- _pages(policy, collection, binding, 0, None, Vector.empty, 0L)
      } yield result).recoverWith(_ => DurableJobStorageResourcePolicy.unavailable)
    }

  def metadata(jobId: String, canonicalJson: String, providerRevision: Long)
      : Consequence[DurableJobStorageQuotaMetadata] = {
    val maximum = DurableJobStorageResourcePolicy.maximumRecordBytes
    if (!DurableJobStorageResourcePolicy.validId(jobId) || canonicalJson == null ||
        canonicalJson.isEmpty || canonicalJson.length.toLong > maximum || providerRevision <= 0L)
      DurableJobStorageResourcePolicy.unavailable
    else {
      val bytes = canonicalJson.getBytes(StandardCharsets.UTF_8)
      if (bytes.length.toLong > maximum) DurableJobStorageResourcePolicy.unavailable
      else {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
          .map(value => f"${value & 0xff}%02x").mkString
        Consequence.success(DurableJobStorageQuotaMetadata(jobId, bytes.length.toLong, hash, providerRevision))
      }
    }
  }

  private def _pages(
    policy: DurableJobStorageResourcePolicy,
    collection: org.goldenport.cncf.datastore.DataStore.CollectionId,
    binding: EntityRevisionBinding,
    offset: Int,
    previousid: Option[String],
    accumulated: Vector[DurableJobStorageQuotaMetadata],
    totalbytes: Long
  )(using ctx: ExecutionContext): Consequence[Vector[DurableJobStorageQuotaMetadata]] = {
    val remaining = policy.maxstoredjobs + 1 - offset
    val limit = math.min(DurableJobStorageResourcePolicy.pageSize, remaining)
    val directive = QueryDirective(Query.Empty, order = QueryOrder.By("id", OrderDirection.Asc),
      limit = QueryLimit.Limit(limit), offset = offset)
    ctx.dataStoreSpace.search(collection, directive).flatMap { page =>
      val rangevalid = page.range match {
        case ResultRange.Exact => true
        case ResultRange.Limited(value) => value == limit
      }
      if (page.records.size > limit || page.cursor.nonEmpty || !rangevalid)
        DurableJobStorageResourcePolicy.unavailable
      else {
        val decoded = page.records.foldLeft[Consequence[(Option[String], Vector[DurableJobStorageQuotaMetadata], Long)]](
          Consequence.success((previousid, accumulated, totalbytes))) { (result, row) =>
          result.flatMap { case (previous, entries, bytes) =>
            for {
              fact <- _row(binding, row)
              (id, metadata) = fact
              _ <- if (previous.forall(_ < id) && !entries.exists(_.jobid == metadata.jobid))
                Consequence.unit else DurableJobStorageResourcePolicy.unavailable
              nextbytes <- DurableJobStorageResourcePolicy.protect(Consequence.success(Math.addExact(bytes, metadata.canonicalbytes)))
              _ <- if (entries.size < policy.maxstoredjobs && nextbytes <= policy.maxcanonicalbytes)
                Consequence.unit else DurableJobStorageResourcePolicy.capacity
            } yield (Some(id), entries :+ metadata, nextbytes)
          }
        }
        decoded.flatMap { case (previous, entries, bytes) =>
          if (page.records.size < limit) Consequence.success(entries.sortBy(_.jobid))
          else _pages(policy, collection, binding, offset + page.records.size, previous, entries, bytes)
        }
      }
    }
  }

  private def _row(binding: EntityRevisionBinding, row: Record)
      : Consequence[(String, DurableJobStorageQuotaMetadata)] =
    for {
      revision <- binding.revision(row)
      /* Bound the identity before the existing decoder derives stable entropy. */
      jobid <- row.getString("jobId").filter(DurableJobStorageResourcePolicy.validId)
        .map(Consequence.success).getOrElse(DurableJobStorageResourcePolicy.unavailable)
      entity <- binding.decodeEntity(row)(summon[EntityPersistent[DurableJobStoreEntity]].fromStoreRecord)
      fact <- metadata(jobid, entity.canonicalJson, revision.value)
    } yield entity.id.value -> fact
}
