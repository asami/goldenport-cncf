package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{EntityMutationProviderFeature, EntityVersionedMutationDataStore,
  OrderDirection, Query, QueryDirective, QueryLimit, QueryOrder, ResultRange, SearchableDataStore}
import org.goldenport.cncf.entity.{CreateResult, EntityConcurrencyPolicy, EntityMutationExecutionPolicy,
  EntityPersistent, EntityRevisionBinding, EntityRevisionCarrier, EntityRevisionRepresentation,
  EntityStore, RevisionPreconditionPolicy}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader,
  EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Native routing, bounded empty-Job proof and acknowledged detached OCC.
 * No source census can reconstruct lifetime authority.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobLifetimeQuotaProvider(
  entitystore: EntityStore,
  policy: DurableJobLifetimeQuotaPolicy
) {
  def admit()(using ctx: ExecutionContext): Consequence[Unit] = DurableJobLifetimeQuotaPolicy.protect {
    (for {
      _ <- _ensure[DurableJobStoreEntity](DurableJobStoreEntity.Collection)
      _ <- _ensure[DurableJobLifetimeQuotaEntity](DurableJobLifetimeQuotaEntity.Collection)
      jobs <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStoreEntity.Collection)
      quota <- ctx.entityStoreSpace.dataStoreCollection(DurableJobLifetimeQuotaEntity.Collection)
      jobprovider <- ctx.dataStoreSpace.dataStore(jobs)
      quotaprovider <- ctx.dataStoreSpace.dataStore(quota)
      _ <- jobprovider match {
        case provider: SearchableDataStore if provider eq quotaprovider => provider match {
          case native: EntityVersionedMutationDataStore if native.entityMutationProviderCapabilities.supportsAll(Set(
              EntityMutationProviderFeature.OptimisticCompareAndSet,
              EntityMutationProviderFeature.AuthoritativeRecordResult)) => Consequence.unit
          case _ => DurableJobLifetimeQuotaPolicy.unavailable
        }
        case _ => DurableJobLifetimeQuotaPolicy.unavailable
      }
    } yield ()).recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)
  }

  def proveEmpty()(using ctx: ExecutionContext): Consequence[Unit] = DurableJobLifetimeQuotaPolicy.protect {
    (for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(DurableJobStoreEntity.Collection)
      page <- ctx.dataStoreSpace.search(collection, QueryDirective(Query.Empty,
        order = QueryOrder.By("id", OrderDirection.Asc), limit = QueryLimit.Limit(1), offset = 0))
      _ <- if (page != null && page.records != null && page.records.size <= 1 &&
          page.records.isEmpty && page.cursor != null && page.cursor.isEmpty && (page.range match {
            case ResultRange.Exact => true
            case ResultRange.Limited(limit) => limit == 1
            case null => false
          })) Consequence.unit else DurableJobLifetimeQuotaPolicy.unavailable
    } yield ()).recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)
  }

  def load()(using ctx: ExecutionContext)
      : Consequence[Option[(EntityRevisionCarrier[DurableJobLifetimeQuotaEntity], DurableJobLifetimeQuotaRecord)]] =
    DurableJobLifetimeQuotaPolicy.protect {
      for {
        id <- DurableJobLifetimeQuotaEntity.entityId
        loaded <- entitystore.loadDetached[DurableJobLifetimeQuotaEntity](id).recoverWith { conclusion =>
          if (conclusion.show.contains(DurableJobLifetimeQuotaPolicy.invalidLabel)) DurableJobLifetimeQuotaPolicy.invalid
          else DurableJobLifetimeQuotaPolicy.unavailable
        }
        result <- loaded match {
          case None => Consequence.success(None)
          case Some(carrier) => decode(carrier).map(record => Some(carrier -> record))
        }
      } yield result
    }

  def decode(carrier: EntityRevisionCarrier[DurableJobLifetimeQuotaEntity]): Consequence[DurableJobLifetimeQuotaRecord] =
    DurableJobLifetimeQuotaPolicy.pure {
      for {
        id <- DurableJobLifetimeQuotaEntity.entityId
        _ <- if (carrier != null && carrier.entity != null && carrier.entity.id == id &&
            carrier.revision != null && carrier.revision.value > 0L) Consequence.unit
          else DurableJobLifetimeQuotaPolicy.invalid
        record <- DurableJobLifetimeQuotaCodec.decode(carrier.entity.canonicaljson)
        _ <- if (record.policy == policy) Consequence.unit else DurableJobLifetimeQuotaPolicy.invalid
      } yield record
    }

  def create(record: DurableJobLifetimeQuotaRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      id <- DurableJobLifetimeQuotaEntity.entityId
      canonical <- DurableJobLifetimeQuotaCodec.encode(record)
      entity = DurableJobLifetimeQuotaEntity(id, canonical)
      acknowledged <- entitystore.create(entity)
      carrier <- _created_carrier(acknowledged)
      _ <- _acknowledged(carrier, entity, 0L)
    } yield ()

  def update(carrier: EntityRevisionCarrier[DurableJobLifetimeQuotaEntity], record: DurableJobLifetimeQuotaRecord)
      (using ctx: ExecutionContext): Consequence[Unit] =
    for {
      canonical <- DurableJobLifetimeQuotaCodec.encode(record)
      entity = DurableJobLifetimeQuotaEntity(carrier.entity.id, canonical)
      acknowledged <- entitystore.updateDetached(entity, Some(carrier.revision),
        EntityMutationExecutionPolicy(concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
          preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired, observedRevision = Some(carrier.revision)))
      _ <- _acknowledged(acknowledged, entity, carrier.revision.value)
    } yield ()

  def isStale(conclusion: org.goldenport.Conclusion): Boolean =
    conclusion.observation.cause.descriptor.facets.contains(Descriptor.Facet.Reason("stale-entity-revision"))

  def isDuplicate(conclusion: org.goldenport.Conclusion): Boolean =
    conclusion.observation.taxonomy == Taxonomy.dataStoreDuplicate

  private def _created_carrier(result: CreateResult[DurableJobLifetimeQuotaEntity])
      : Consequence[EntityRevisionCarrier[DurableJobLifetimeQuotaEntity]] =
    DurableJobLifetimeQuotaPolicy.pure {
      for {
        id <- DurableJobLifetimeQuotaEntity.entityId
        _ <- if (result != null && result.id == id && result.record != null) Consequence.unit
          else DurableJobLifetimeQuotaPolicy.unavailable
        record <- result.record.map(Consequence.success).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
        carrier <- EntityRevisionBinding(EntityRevisionRepresentation.Detached).detachedCarrier(record)(
          summon[EntityPersistent[DurableJobLifetimeQuotaEntity]].fromStoreRecord)
      } yield carrier
    }.recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)

  private def _acknowledged(carrier: EntityRevisionCarrier[DurableJobLifetimeQuotaEntity],
    expected: DurableJobLifetimeQuotaEntity, previous: Long): Consequence[Unit] =
    (for {
      _ <- decode(carrier)
      _ <- if (carrier.entity == expected && carrier.revision.value > previous) Consequence.unit
        else DurableJobLifetimeQuotaPolicy.unavailable
    } yield ()).recoverWith(_ => DurableJobLifetimeQuotaPolicy.unavailable)

  private def _ensure[A](collection: EntityCollectionId)(using persistent: EntityPersistent[A],
    ctx: ExecutionContext): Consequence[Unit] = ctx.entitySpace.synchronized {
    ctx.entitySpace.entityOption(collection) match {
      case Some(current) if current.descriptor.collectionId == collection &&
          current.descriptor.revisionBinding.exists(_.representation == EntityRevisionRepresentation.Detached) &&
          current.descriptor.plan.memoryPolicy == EntityMemoryPolicy.StoreOnly &&
          current.descriptor.plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic => Consequence.unit
      case Some(_) => DurableJobLifetimeQuotaPolicy.unavailable
      case None =>
        val realm = new EntityRealm[A](entityName = collection.name,
          loader = EntityLoader.fromEntityStore(entitystore),
          state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
        val descriptor = EntityDescriptor(collectionId = collection,
          plan = EntityRuntimePlan(entityName = collection.name, memoryPolicy = EntityMemoryPolicy.StoreOnly,
            workingSet = None, partitionStrategy = PartitionStrategy.byEntityId, maxPartitions = 1,
            maxEntitiesPerPartition = 1, concurrencyPolicy = EntityConcurrencyPolicy.Optimistic),
          persistent = persistent, revisionBinding = Some(EntityRevisionBinding(EntityRevisionRepresentation.Detached)))
        ctx.entitySpace.registerEntity(collection.name, new EntityCollection(descriptor, EntityStorage(realm)))
        Consequence.unit
    }
  }
}
