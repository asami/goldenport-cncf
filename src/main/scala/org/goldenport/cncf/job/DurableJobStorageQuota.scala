package org.goldenport.cncf.job

import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy, EntityMutationExecutionPolicy, EntityPersistent,
  EntityRevisionBinding, EntityRevisionCarrier, EntityRevisionRepresentation,
  EntityStore, RevisionPreconditionPolicy
}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm,
  EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy
}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.simplemodeling.model.datatype.EntityRevision

/*
 * Provider-persisted admission. Native ledger CAS and original Job OCC are the
 * only mutation authorities; a missing/unchanged Job never releases a pending write.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobStorageQuota(
  entitystore: EntityStore,
  policy: DurableJobStorageResourcePolicy
) {
  def reserve(jobId: String, canonicalJson: String, expectedRevision: Option[EntityRevision])
      (using ctx: ExecutionContext): Consequence[DurableJobStorageQuotaLease] =
    DurableJobStorageResourcePolicy.protect {
      for {
        _ <- policy.validate
        _ <- DurableJobStorageResourcePolicy.validateId(jobId)
        proposal <- DurableJobStorageQuotaInventory.metadata(jobId, canonicalJson, 1L)
        _ <- if (expectedRevision.forall(_.value > 0L)) Consequence.unit
          else DurableJobStorageResourcePolicy.invalid
        _ <- _ensure_collection()
        _ <- DurableJobStorageQuotaInventory.admitProvider()
        pending = DurableJobStorageQuotaPending(UUID.randomUUID().toString.replace("-", ""),
          expectedRevision.map(_.value), proposal.canonicalbytes, proposal.canonicalsha256)
        lease = DurableJobStorageQuotaLease(jobId, pending)
        _ <- _change(DurableJobStorageResourcePolicy.maximumAttempts, Some(lease), None)
      } yield lease
    }

  def confirm(lease: DurableJobStorageQuotaLease, acknowledged: DurableJobStorageQuotaMetadata)
      (using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobStorageResourcePolicy.protect {
      (for {
        _ <- policy.validate
        _ <- _ensure_collection()
        _ <- DurableJobStorageQuotaInventory.admitProvider()
        _ <- _change(DurableJobStorageResourcePolicy.maximumAttempts, None, Some(lease -> acknowledged))
      } yield ()).recoverWith(_ => DurableJobStorageResourcePolicy.confirmation)
    }.recoverWith(_ => DurableJobStorageResourcePolicy.confirmation)

  def resourceSnapshot()(using ctx: ExecutionContext): Consequence[DurableJobStorageQuotaSnapshot] =
    DurableJobStorageResourcePolicy.protect {
      for {
        _ <- policy.validate
        _ <- _ensure_collection()
        current <- _load()
        result <- current match {
          case Some((_, record)) => DurableJobStorageQuotaCodec.usage(record)
          case None => DurableJobStorageResourcePolicy.unavailable
        }
      } yield result
    }

  private def _change(
    remaining: Int,
    reservation: Option[DurableJobStorageQuotaLease],
    confirmation: Option[(DurableJobStorageQuotaLease, DurableJobStorageQuotaMetadata)]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (remaining <= 0) DurableJobStorageResourcePolicy.unavailable
    else _load().flatMap {
      case None =>
        DurableJobStorageQuotaInventory.scan(policy).flatMap { inventory =>
          _bootstrap(inventory).flatMap(_ => _change(remaining - 1, reservation, confirmation))
        }
      case Some((carrier, current)) =>
        val prepared = for {
          inventory <- DurableJobStorageQuotaInventory.scan(policy)
          reconciled <- _reconcile(current, inventory)
          changed <- reservation match {
            case Some(lease) => _reserve(reconciled, lease)
            case None => confirmation match {
              case Some((lease, acknowledged)) => _confirm(reconciled, inventory, lease, acknowledged)
              case None => DurableJobStorageResourcePolicy.invalid
            }
          }
          canonical <- DurableJobStorageQuotaCodec.encode(changed)
        } yield DurableJobStorageQuotaEntity(carrier.entity.id, canonical)
        prepared match {
          case Consequence.Failure(conclusion) =>
            /* A concurrent ledger writer may have invalidated this census. */
            _load().flatMap {
              case Some((latest, _)) if latest.revision != carrier.revision =>
                _change(remaining - 1, reservation, confirmation)
              case _ => Consequence.Failure(conclusion)
            }
          case Consequence.Success(entity) =>
            val result = entitystore.updateDetached(entity, Some(carrier.revision), _policy(carrier.revision))
            result match {
              case Consequence.Success(acknowledged) =>
                _decode(acknowledged).flatMap { record =>
                  if (acknowledged.entity == entity && acknowledged.revision.value > carrier.revision.value &&
                      record.policy == policy) Consequence.unit
                  else DurableJobStorageResourcePolicy.unavailable
                }
              case Consequence.Failure(conclusion)
                  if conclusion.observation.cause.descriptor.facets.contains(Descriptor.Facet.Reason("stale-entity-revision")) =>
                _change(remaining - 1, reservation, confirmation)
              case Consequence.Failure(_) => DurableJobStorageResourcePolicy.unavailable
            }
        }
    }

  private def _load()(using ctx: ExecutionContext)
      : Consequence[Option[(EntityRevisionCarrier[DurableJobStorageQuotaEntity], DurableJobStorageQuotaRecord)]] =
    for {
      id <- DurableJobStorageQuotaEntity.entityId
      loaded <- entitystore.loadDetached[DurableJobStorageQuotaEntity](id)
        .recoverWith(_ => DurableJobStorageResourcePolicy.unavailable)
      result <- loaded match {
        case None => Consequence.success(None)
        case Some(carrier) => _decode(carrier).map(record => Some(carrier -> record))
      }
    } yield result

  private def _decode(carrier: EntityRevisionCarrier[DurableJobStorageQuotaEntity])
      : Consequence[DurableJobStorageQuotaRecord] =
    for {
      id <- DurableJobStorageQuotaEntity.entityId
      _ <- if (carrier.entity.id == id && carrier.revision.value > 0L) Consequence.unit
        else DurableJobStorageResourcePolicy.invalid
      record <- DurableJobStorageQuotaCodec.decode(carrier.entity.canonicaljson)
      _ <- if (record.policy == policy) Consequence.unit else DurableJobStorageResourcePolicy.invalid
    } yield record

  private def _bootstrap(inventory: Vector[DurableJobStorageQuotaMetadata])
      (using ctx: ExecutionContext): Consequence[Unit] =
    for {
      id <- DurableJobStorageQuotaEntity.entityId
      canonical <- DurableJobStorageQuotaCodec.encode(DurableJobStorageQuotaRecord(policy, inventory.map(_.entry)))
      _ <- entitystore.create(DurableJobStorageQuotaEntity(id, canonical)).map(_ => ())
        .recoverWith { conclusion =>
          if (conclusion.observation.taxonomy == Taxonomy.dataStoreDuplicate)
            _load().flatMap {
              case Some(_) => Consequence.unit
              case None => DurableJobStorageResourcePolicy.unavailable
            }
          else DurableJobStorageResourcePolicy.unavailable
        }
    } yield ()

  private def _reconcile(current: DurableJobStorageQuotaRecord, inventory: Vector[DurableJobStorageQuotaMetadata])
      : Consequence[DurableJobStorageQuotaRecord] = {
    val actual = inventory.map(value => value.jobid -> value).toMap
    val imported = inventory.filterNot(value => current.entries.exists(_.jobid == value.jobid)).map(_.entry)
    val merged = current.entries.foldLeft[Consequence[Vector[DurableJobStorageQuotaEntry]]](Consequence.success(Vector.empty)) {
      (result, entry) => result.flatMap { entries =>
        actual.get(entry.jobid) match {
          case None => Consequence.success(entries :+ entry)
          case Some(observed) if entry.committedrevision.exists(_ > observed.providerrevision) =>
            DurableJobStorageResourcePolicy.unavailable
          case Some(observed) =>
            val unresolved = entry.pending.filterNot(pending => _fenced(pending, observed))
            Consequence.success(entries :+ observed.entry.copy(pending = unresolved))
        }
      }
    }
    merged.flatMap { entries =>
      _capacity(DurableJobStorageQuotaRecord(policy, (entries ++ imported).sortBy(_.jobid)))
    }
  }

  private def _fenced(pending: DurableJobStorageQuotaPending, observed: DurableJobStorageQuotaMetadata): Boolean =
    pending.expectedrevision.forall(_ < observed.providerrevision)

  private def _reserve(current: DurableJobStorageQuotaRecord, lease: DurableJobStorageQuotaLease)
      : Consequence[DurableJobStorageQuotaRecord] = {
    val entry = current.entries.find(_.jobid == lease.jobid)
      .getOrElse(DurableJobStorageQuotaEntry(lease.jobid, 0L, None, None, Vector.empty))
    if (entry.pending.size >= DurableJobStorageResourcePolicy.maximumPending)
      DurableJobStorageResourcePolicy.capacity
    else {
      val replacement = entry.copy(pending = entry.pending :+ lease.pending)
      _capacity(current.copy(entries = (current.entries.filterNot(_.jobid == lease.jobid) :+ replacement).sortBy(_.jobid)))
    }
  }

  private def _confirm(
    current: DurableJobStorageQuotaRecord,
    inventory: Vector[DurableJobStorageQuotaMetadata],
    lease: DurableJobStorageQuotaLease,
    acknowledged: DurableJobStorageQuotaMetadata
  ): Consequence[DurableJobStorageQuotaRecord] = {
    val observed = inventory.find(_.jobid == lease.jobid)
    val tokenvalid = current.entries.find(_.jobid == lease.jobid).forall { entry =>
      entry.pending.find(_.token == lease.pending.token).forall(_ == lease.pending)
    }
    val acknowledgementvalid = acknowledged.jobid == lease.jobid && acknowledged.providerrevision > 0L &&
      lease.pending.expectedrevision.forall(_ < acknowledged.providerrevision) &&
      (lease.pending.expectedrevision.isEmpty ||
        (acknowledged.canonicalbytes == lease.pending.canonicalbytes && acknowledged.canonicalsha256 == lease.pending.canonicalsha256))
    val proof = observed.exists { actual =>
      actual.providerrevision >= acknowledged.providerrevision &&
        (actual.providerrevision > acknowledged.providerrevision ||
          (actual.canonicalbytes == acknowledged.canonicalbytes && actual.canonicalsha256 == acknowledged.canonicalsha256))
    }
    if (!tokenvalid || !acknowledgementvalid || !proof) DurableJobStorageResourcePolicy.confirmation
    else {
      val entries = current.entries.map { entry =>
        if (entry.jobid == lease.jobid) entry.copy(pending = entry.pending.filterNot(_.token == lease.pending.token))
        else entry
      }
      _capacity(current.copy(entries = entries))
    }
  }

  private def _capacity(record: DurableJobStorageQuotaRecord): Consequence[DurableJobStorageQuotaRecord] =
    DurableJobStorageResourcePolicy.protect {
      val bytes = record.entries.foldLeft(0L)((total, entry) => Math.addExact(total, entry.heldBytes))
      if (record.entries.size > policy.maxstoredjobs || bytes > policy.maxcanonicalbytes)
        DurableJobStorageResourcePolicy.capacity
      else DurableJobStorageQuotaCodec.usage(record).map(_ => record)
    }

  private def _policy(revision: EntityRevision): EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy(concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired, observedRevision = Some(revision))

  private def _ensure_collection()(using ctx: ExecutionContext): Consequence[Unit] =
    ctx.entitySpace.synchronized {
      ctx.entitySpace.entityOption(DurableJobStorageQuotaEntity.Collection) match {
        case Some(collection) if collection.descriptor.revisionBinding.exists(_.representation == EntityRevisionRepresentation.Detached) &&
            collection.descriptor.plan.memoryPolicy == EntityMemoryPolicy.StoreOnly &&
            collection.descriptor.plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic => Consequence.unit
        case Some(_) => DurableJobStorageResourcePolicy.unavailable
        case None =>
          ctx.entitySpace.registerEntity(DurableJobStorageQuotaEntity.Collection.name, _entity_collection())
          Consequence.unit
      }
    }

  private def _entity_collection()(using ctx: ExecutionContext): EntityCollection[DurableJobStorageQuotaEntity] = {
    val realm = new EntityRealm[DurableJobStorageQuotaEntity](
      entityName = DurableJobStorageQuotaEntity.Collection.name,
      loader = EntityLoader.fromEntityStore(entitystore)(using summon[EntityPersistent[DurableJobStorageQuotaEntity]], ctx),
      state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty))
    )
    val descriptor = EntityDescriptor(
      collectionId = DurableJobStorageQuotaEntity.Collection,
      plan = EntityRuntimePlan(entityName = DurableJobStorageQuotaEntity.Collection.name,
        memoryPolicy = EntityMemoryPolicy.StoreOnly, workingSet = None,
        partitionStrategy = PartitionStrategy.byEntityId, maxPartitions = 1, maxEntitiesPerPartition = 1,
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic),
      persistent = summon[EntityPersistent[DurableJobStorageQuotaEntity]],
      revisionBinding = Some(EntityRevisionBinding(EntityRevisionRepresentation.Detached))
    )
    new EntityCollection(descriptor, EntityStorage(realm))
  }
}
