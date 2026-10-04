package org.goldenport.cncf.job

import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy, EntityMutationExecutionPolicy, EntityPersistent, EntityRevisionBinding,
  EntityRevisionCarrier, EntityRevisionRepresentation, EntityStore, RevisionPreconditionPolicy
}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm,
  EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy
}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.simplemodeling.model.datatype.EntityRevision

/*
 * Native optimistic accounting; physical acknowledgements remain adapter-owned.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobOwnedBlobQuota(entitystore: EntityStore, policy: DurableJobOwnedBlobResourcePolicy) {
  import DurableJobOwnedBlobResourcePolicy.*
  import DurableJobOwnedBlobQuotaState.*

  def reserve(metadata: DurableJobOwnedBlobQuotaEntry)(using ctx: ExecutionContext): Consequence[DurableJobOwnedBlobQuotaLease] = protect {
    val entry = metadata.copy(token = Some(UUID.randomUUID().toString.replace("-", "")), state = Pending, bindingsha256 = None)
    val lease = DurableJobOwnedBlobQuotaLease(entry)
    for {
      _ <- _admit()
      _ <- if (entry.bytesize <= policy.maxpayloadbytes) Consequence.unit else invalid
      _ <- _change(maximumAttempts, (current, _) =>
        if (current.entries.exists(_.blobid == entry.blobid) || current.entries.size >= maximumEntries) capacity
        else _capacity(current.copy(entries = (current.entries :+ entry).sortBy(_.blobid))))
    } yield lease
  }

  def confirm(lease: DurableJobOwnedBlobQuotaLease, metadata: DurableJobOwnedBlobQuotaMetadata)
      (using ctx: ExecutionContext): Consequence[Unit] = protect {
    (for {
      _ <- _admit()
      _ <- _change(maximumAttempts, (current, inventory) => {
        current.entries.find(_.blobid == lease.entry.blobid) match {
          case Some(entry) if entry.state != Released && _same_lease(entry, lease) && metadata.matches(entry) &&
              inventory.exists(_ == metadata) =>
            Consequence.success(_replace(current, entry.copy(state = Published, bindingsha256 = Some(metadata.bindingsha256))))
          case _ => confirmation
        }
      })
    } yield ()).recoverWith(_ => confirmation)
  }.recoverWith(_ => confirmation)

  def prepareTargets(metadata: Vector[DurableJobOwnedBlobQuotaMetadata])
      (using ctx: ExecutionContext): Consequence[Unit] = protect {
    for {
      _ <- _admit()
      _ <- _change(maximumAttempts, (current, _) =>
        if (metadata.forall(fact => _target_matches(current, fact))) Consequence.success(current) else invalid)
    } yield ()
  }

  def inspectTargets(metadata: Vector[DurableJobOwnedBlobQuotaMetadata])
      (using ctx: ExecutionContext): Consequence[Unit] = protect {
    for {
      _ <- _admit()
      loaded <- _load()
      _ <- loaded match {
        case Some((_, current)) if metadata != null && metadata.forall(fact =>
            fact != null && _target_matches(current, fact) && current.entries.exists(entry =>
              entry.blobid == fact.blobid && entry.state == Published)) => Consequence.unit
        case _ => invalid
      }
    } yield ()
  }

  def releaseIssued(lease: DurableJobOwnedBlobQuotaLease, acknowledged: Option[DurableJobOwnedBlobQuotaMetadata])
      (using ctx: ExecutionContext): Consequence[Unit] = protect {
    (for {
      _ <- _admit()
      _ <- _change(maximumAttempts, (current, _) => current.entries.find(_.blobid == lease.entry.blobid) match {
        case Some(entry) if _same_lease(entry, lease) && acknowledged.forall(_.matches(entry)) =>
          if (entry.state == Released) Consequence.success(current)
          else Consequence.success(_replace(current, entry.copy(state = Released,
            bindingsha256 = entry.bindingsha256.orElse(acknowledged.map(_.bindingsha256)))))
        case _ => confirmation
      })
    } yield ()).recoverWith(_ => confirmation)
  }.recoverWith(_ => confirmation)

  def releaseTarget(metadata: DurableJobOwnedBlobQuotaMetadata)(using ctx: ExecutionContext): Consequence[Unit] = protect {
    (for {
      _ <- _admit()
      _ <- _change(maximumAttempts, (current, _) => {
        if (!_target_matches(current, metadata)) confirmation
        else {
          val entry = current.entries.find(_.blobid == metadata.blobid).get
          Consequence.success(_replace(current, entry.copy(state = Released)))
        }
      })
    } yield ()).recoverWith(_ => confirmation)
  }.recoverWith(_ => confirmation)

  def resourceSnapshot()(using ctx: ExecutionContext): Consequence[DurableJobOwnedBlobQuotaSnapshot] = protect {
    for {
      _ <- _admit()
      current <- _load()
      result <- current match {
        case Some((_, record)) => DurableJobOwnedBlobQuotaCodec.usage(record)
        case None => unavailable
      }
    } yield result
  }

  private def _admit()(using ctx: ExecutionContext): Consequence[Unit] = for {
    _ <- policy.validate
    _ <- _ensure_collection()
    _ <- DurableJobOwnedBlobQuotaInventory.admitProvider()
  } yield ()

  private def _change(
    remaining: Int,
    change: (DurableJobOwnedBlobQuotaRecord, Vector[DurableJobOwnedBlobQuotaMetadata]) => Consequence[DurableJobOwnedBlobQuotaRecord]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (remaining <= 0) unavailable
    else _load().flatMap {
      case None => DurableJobOwnedBlobQuotaInventory.scan().flatMap { inventory =>
        _bootstrap(inventory).flatMap(_ => _change(remaining - 1, change))
      }
      case Some((carrier, current)) =>
        val prepared = for {
          inventory <- DurableJobOwnedBlobQuotaInventory.scan()
          reconciled <- _reconcile(current, inventory)
          changed <- change(reconciled, inventory)
          canonical <- DurableJobOwnedBlobQuotaCodec.encode(changed)
        } yield DurableJobOwnedBlobQuotaEntity(carrier.entity.id, canonical)
        prepared match {
          case Consequence.Failure(conclusion) =>
            /* A torn census is retryable only when this observed revision became stale. */
            _load().flatMap {
              case Some((latest, _)) if latest.revision != carrier.revision => _change(remaining - 1, change)
              case _ => Consequence.Failure(conclusion)
            }
          case Consequence.Success(entity) =>
            entitystore.updateDetached(entity, Some(carrier.revision), _policy(carrier.revision)) match {
              case Consequence.Success(acknowledged) => _decode(acknowledged).flatMap { _ =>
                if (acknowledged.entity == entity && acknowledged.revision.value > carrier.revision.value) Consequence.unit
                else unavailable
              }
              case Consequence.Failure(conclusion) if conclusion.observation.cause.descriptor.facets
                  .contains(Descriptor.Facet.Reason("stale-entity-revision")) => _change(remaining - 1, change)
              case _ => unavailable
            }
        }
    }

  private def _load()(using ctx: ExecutionContext)
      : Consequence[Option[(EntityRevisionCarrier[DurableJobOwnedBlobQuotaEntity], DurableJobOwnedBlobQuotaRecord)]] = for {
    id <- DurableJobOwnedBlobQuotaEntity.entityId
    loaded <- entitystore.loadDetached[DurableJobOwnedBlobQuotaEntity](id).recoverWith(_ => unavailable)
    result <- loaded match {
      case None => Consequence.success(None)
      case Some(carrier) => _decode(carrier).map(record => Some(carrier -> record))
    }
  } yield result

  private def _decode(carrier: EntityRevisionCarrier[DurableJobOwnedBlobQuotaEntity]): Consequence[DurableJobOwnedBlobQuotaRecord] = for {
    id <- DurableJobOwnedBlobQuotaEntity.entityId
    _ <- if (carrier.entity.id == id && carrier.revision.value > 0L) Consequence.unit else invalid
    record <- DurableJobOwnedBlobQuotaCodec.decode(carrier.entity.canonicaljson)
    _ <- if (record.policy == policy) Consequence.unit else invalid
  } yield record

  private def _bootstrap(inventory: Vector[DurableJobOwnedBlobQuotaMetadata])(using ctx: ExecutionContext): Consequence[Unit] = for {
    id <- DurableJobOwnedBlobQuotaEntity.entityId
    record <- _capacity(DurableJobOwnedBlobQuotaRecord(policy, inventory.map(_.entry).sortBy(_.blobid)))
    canonical <- DurableJobOwnedBlobQuotaCodec.encode(record)
    _ <- entitystore.create(DurableJobOwnedBlobQuotaEntity(id, canonical)).flatMap { result =>
      if (result.id != id) unavailable
      else _load().flatMap {
        case Some(_) => Consequence.unit
        case None => unavailable
      }
    }.recoverWith { conclusion =>
      if (conclusion.observation.taxonomy == Taxonomy.dataStoreDuplicate) _load().flatMap {
        case Some(_) => Consequence.unit
        case None => unavailable
      } else unavailable
    }
  } yield ()

  private def _reconcile(current: DurableJobOwnedBlobQuotaRecord, inventory: Vector[DurableJobOwnedBlobQuotaMetadata])
      : Consequence[DurableJobOwnedBlobQuotaRecord] = {
    val actual = inventory.map(fact => fact.blobid -> fact).toMap
    val imported = inventory.filterNot(fact => current.entries.exists(_.blobid == fact.blobid)).map(_.entry)
    val merged = current.entries.foldLeft[Consequence[Vector[DurableJobOwnedBlobQuotaEntry]]](Consequence.success(Vector.empty)) {
      (result, entry) => result.flatMap { entries => actual.get(entry.blobid) match {
        case None => Consequence.success(entries :+ entry)
        case Some(fact) if !fact.matches(entry) => invalid
        case Some(fact) if entry.state == Pending =>
          Consequence.success(entries :+ entry.copy(state = Published, bindingsha256 = Some(fact.bindingsha256)))
        case Some(_) => Consequence.success(entries :+ entry)
      }}
    }
    merged.flatMap(entries => _capacity(current.copy(entries = (entries ++ imported).sortBy(_.blobid))))
  }

  private def _same_lease(entry: DurableJobOwnedBlobQuotaEntry, lease: DurableJobOwnedBlobQuotaLease): Boolean =
    entry.token.nonEmpty && entry.copy(state = Pending, bindingsha256 = None) == lease.entry

  private def _target_matches(current: DurableJobOwnedBlobQuotaRecord, metadata: DurableJobOwnedBlobQuotaMetadata): Boolean =
    current.entries.find(_.blobid == metadata.blobid).exists(entry =>
      metadata.matches(entry) && entry.bindingsha256.contains(metadata.bindingsha256) && entry.state != Pending)

  private def _replace(current: DurableJobOwnedBlobQuotaRecord, entry: DurableJobOwnedBlobQuotaEntry): DurableJobOwnedBlobQuotaRecord =
    current.copy(entries = current.entries.map(value => if (value.blobid == entry.blobid) entry else value))

  private def _capacity(record: DurableJobOwnedBlobQuotaRecord): Consequence[DurableJobOwnedBlobQuotaRecord] = protect {
    val occupied = record.entries.filterNot(_.state == Released)
    val bytes = occupied.foldLeft(0L)((total, entry) => Math.addExact(total, entry.bytesize))
    if (record.entries.size > maximumEntries || occupied.size > policy.maxownedblobs || bytes > policy.maxownedbytes) capacity
    else DurableJobOwnedBlobQuotaCodec.usage(record).map(_ => record)
  }

  private def _policy(revision: EntityRevision): EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy(concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired, observedRevision = Some(revision))

  private def _ensure_collection()(using ctx: ExecutionContext): Consequence[Unit] =
    ctx.entitySpace.synchronized {
      ctx.entitySpace.entityOption(DurableJobOwnedBlobQuotaEntity.Collection) match {
        case Some(collection) if collection.descriptor.collectionId == DurableJobOwnedBlobQuotaEntity.Collection &&
            collection.descriptor.revisionBinding.exists(_.representation == EntityRevisionRepresentation.Detached) &&
            collection.descriptor.plan.memoryPolicy == EntityMemoryPolicy.StoreOnly &&
            collection.descriptor.plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic => Consequence.unit
        case Some(_) => unavailable
        case None =>
          ctx.entitySpace.registerEntity(DurableJobOwnedBlobQuotaEntity.Collection.name, _entity_collection())
          Consequence.unit
      }
    }

  private def _entity_collection()(using ctx: ExecutionContext): EntityCollection[DurableJobOwnedBlobQuotaEntity] = {
    val realm = new EntityRealm[DurableJobOwnedBlobQuotaEntity](
      entityName = DurableJobOwnedBlobQuotaEntity.Collection.name,
      loader = EntityLoader.fromEntityStore(entitystore)(using summon[EntityPersistent[DurableJobOwnedBlobQuotaEntity]], ctx),
      state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty)))
    val descriptor = EntityDescriptor(collectionId = DurableJobOwnedBlobQuotaEntity.Collection,
      plan = EntityRuntimePlan(entityName = DurableJobOwnedBlobQuotaEntity.Collection.name,
        memoryPolicy = EntityMemoryPolicy.StoreOnly, workingSet = None,
        partitionStrategy = PartitionStrategy.byEntityId, maxPartitions = 1, maxEntitiesPerPartition = 1,
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic),
      persistent = summon[EntityPersistent[DurableJobOwnedBlobQuotaEntity]],
      revisionBinding = Some(EntityRevisionBinding(EntityRevisionRepresentation.Detached)))
    new EntityCollection(descriptor, EntityStorage(realm))
  }
}
