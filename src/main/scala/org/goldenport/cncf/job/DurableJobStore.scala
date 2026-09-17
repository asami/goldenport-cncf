package org.goldenport.cncf.job

import cats.Id
import cats.data.State
import cats.effect.Ref
import cats.syntax.all.*
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationExecutionPolicy,
  EntityPersistent,
  EntityPersistentCreate,
  EntityRevisionCarrier,
  EntityRevisionBinding,
  EntityRevisionRepresentation,
  EntityStore,
  RevisionPreconditionPolicy
}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy
}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}

/*
 * Package-internal canonical v1 durable-record storage.  This remains
 * deliberately separate from JobEntity: it has no live JobEngine authority
 * and stores only the closed canonical durable value and stable identity.
 *
 * @since   Sep. 10, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobStoreSnapshot(
  record: DurableJobRecord,
  providerrevision: EntityRevision
)

/* Closed startup-recovery result; it never carries a load failure message. */
private[job] enum DurableJobStoreStartupRecoveryLoad {
  case Missing
  case Corrupt
  case Refused
  case Admitted(snapshot: DurableJobStoreSnapshot)
}

private[job] final class DurableJobStore(
  entitystore: EntityStore
) {
  def create(
    record: DurableJobRecord,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    _ensure_collection().flatMap(_ => _admit(record, access)).flatMap { case (canonical, admitted) =>
      for {
        entityid <- DurableJobStoreEntity.entityId(admitted.body.identity.jobId)
        entity = DurableJobStoreEntity(
          entityid,
          admitted.body.identity.jobId,
          canonical
        )
        _ <- entitystore.create(entity)
        snapshot <- _load(entity.id, entity.jobId, access)
        result <- _required_snapshot(entity.id, snapshot)
      } yield result
    }

  def load(
    jobId: String,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[Option[DurableJobStoreSnapshot]] =
    _ensure_collection().flatMap(_ => DurableJobStoreEntity.entityId(jobId)).flatMap(_load(_, jobId, access))

  def loadStartupRecovery(
    jobId: String,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): DurableJobStoreStartupRecoveryLoad =
    _ensure_collection() match {
      case Consequence.Success(_) =>
        DurableJobStoreEntity.entityId(jobId) match {
          case Consequence.Success(id) =>
            entitystore.loadDetached[DurableJobStoreEntity](id) match {
              case Consequence.Success(Some(carrier)) =>
                _startup_recovery_snapshot(carrier, id, jobId, access)
              case Consequence.Success(None) =>
                DurableJobStoreStartupRecoveryLoad.Missing
              case Consequence.Failure(_) =>
                DurableJobStoreStartupRecoveryLoad.Corrupt
            }
          case Consequence.Failure(_) =>
            DurableJobStoreStartupRecoveryLoad.Corrupt
        }
      case Consequence.Failure(_) =>
        DurableJobStoreStartupRecoveryLoad.Corrupt
    }

  def checkpoint(
    snapshot: DurableJobStoreSnapshot,
    record: DurableJobRecord,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    _ensure_collection().flatMap(_ => _admit(record, access)).flatMap { case (canonical, admitted) =>
      val jobid = admitted.body.identity.jobId
      for {
        entityid <- DurableJobStoreEntity.entityId(jobid)
        _ <- _same_identity(snapshot.record, admitted)
        currentoption <- load(jobid, access)
        current <- _required_snapshot(entityid, currentoption)
        _ <- _current_snapshot(snapshot, current)
        _ <- _contiguous_semantic_revision(snapshot.record, admitted)
        entity = DurableJobStoreEntity(
          entityid,
          jobid,
          canonical
        )
        carrier <- entitystore.updateDetached(
          entity,
          Some(snapshot.providerrevision),
          _checkpoint_policy(snapshot.providerrevision)
        )
        result <- _snapshot(carrier, entity.id, jobid, access)
      } yield result
    }

  private def _admit(
    record: DurableJobRecord,
    access: DurableJobRecordAccess
  ): Consequence[(String, DurableJobRecord)] =
    for {
      canonical <- DurableJobRecordCodec.canonicalJson(record)
      admitted <- DurableJobRecordCodec.decode(canonical, access)
    } yield canonical -> admitted

  private def _load(
    id: EntityId,
    jobid: String,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[Option[DurableJobStoreSnapshot]] =
    entitystore
      .loadDetached[DurableJobStoreEntity](id)
      .flatMap(_.traverse(_snapshot(_, id, jobid, access)))

  private def _snapshot(
    carrier: EntityRevisionCarrier[DurableJobStoreEntity],
    id: EntityId,
    jobid: String,
    access: DurableJobRecordAccess
  ): Consequence[DurableJobStoreSnapshot] =
    for {
      _ <- _storage_identity(carrier.entity, id, jobid)
      record <- DurableJobRecordCodec.decode(carrier.entity.canonicalJson, access)
      canonical <- DurableJobRecordCodec.canonicalJson(record)
      _ <- _canonical_storage(carrier.entity.canonicalJson, canonical)
      _ <- _record_identity(record, jobid)
    } yield DurableJobStoreSnapshot(record, carrier.revision)

  private def _startup_recovery_snapshot(
    carrier: EntityRevisionCarrier[DurableJobStoreEntity],
    id: EntityId,
    jobid: String,
    access: DurableJobRecordAccess
  ): DurableJobStoreStartupRecoveryLoad =
    _storage_identity(carrier.entity, id, jobid) match {
      case Consequence.Success(_) =>
        DurableJobRecordCodec.startupAdmission(carrier.entity.canonicalJson, access) match {
          case DurableJobRecordStartupAdmission.Admitted(record) =>
            val result = for {
              canonical <- DurableJobRecordCodec.canonicalJson(record)
              _ <- _canonical_storage(carrier.entity.canonicalJson, canonical)
              _ <- _record_identity(record, jobid)
            } yield DurableJobStoreSnapshot(record, carrier.revision)
            result match {
              case Consequence.Success(snapshot) =>
                DurableJobStoreStartupRecoveryLoad.Admitted(snapshot)
              case Consequence.Failure(_) =>
                DurableJobStoreStartupRecoveryLoad.Corrupt
            }
          case DurableJobRecordStartupAdmission.Refused =>
            DurableJobStoreStartupRecoveryLoad.Refused
          case DurableJobRecordStartupAdmission.Corrupt =>
            DurableJobStoreStartupRecoveryLoad.Corrupt
        }
      case Consequence.Failure(_) =>
        DurableJobStoreStartupRecoveryLoad.Corrupt
    }

  private def _required_snapshot(
    id: EntityId,
    snapshot: Option[DurableJobStoreSnapshot]
  ): Consequence[DurableJobStoreSnapshot] =
    snapshot.map(Consequence.success).getOrElse(Consequence.entityNotFound(id.print))

  private def _storage_identity(
    entity: DurableJobStoreEntity,
    id: EntityId,
    jobid: String
  ): Consequence[Unit] =
    if (entity.id == id && entity.jobId == jobid)
      Consequence.unit
    else
      Consequence.stateInvalid("Durable job storage identity does not match the requested durable job")

  private def _record_identity(
    record: DurableJobRecord,
    jobid: String
  ): Consequence[Unit] =
    if (record.body.identity.jobId == jobid)
      Consequence.unit
    else
      Consequence.stateInvalid("Durable job body identity does not match its storage identity")

  private def _canonical_storage(
    stored: String,
    canonical: String
  ): Consequence[Unit] =
    if (stored == canonical)
      Consequence.unit
    else
      Consequence.stateInvalid("Durable job storage text is not canonical v1 JSON")

  private def _same_identity(
    previous: DurableJobRecord,
    next: DurableJobRecord
  ): Consequence[Unit] =
    if (previous.body.identity.jobId == next.body.identity.jobId)
      Consequence.unit
    else
      Consequence.argumentInvalid(
        "durableJobId",
        previous.body.identity.jobId,
        next.body.identity.jobId
      )

  private def _current_snapshot(
    observed: DurableJobStoreSnapshot,
    current: DurableJobStoreSnapshot
  ): Consequence[Unit] =
    if (observed.providerrevision != current.providerrevision)
      Consequence.stateInvalid("Durable job checkpoint requires the current provider revision")
    else if (observed.record != current.record)
      Consequence.stateInvalid("Durable job checkpoint requires the current durable record")
    else
      Consequence.unit

  private def _contiguous_semantic_revision(
    previous: DurableJobRecord,
    next: DurableJobRecord
  ): Consequence[Unit] = {
    val revision = previous.body.identity.revision
    if (revision == Long.MaxValue)
      Consequence.stateInvalid("Durable job semantic revision cannot advance beyond Long.MaxValue")
    else if (next.body.identity.revision == revision + 1L)
      Consequence.unit
    else
      Consequence.stateInvalid("Durable job checkpoint requires a contiguous semantic revision")
  }

  private def _checkpoint_policy(
    revision: EntityRevision
  ): EntityMutationExecutionPolicy =
    EntityMutationExecutionPolicy(
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired,
      observedRevision = Some(revision)
    )

  private def _ensure_collection()(using
    ctx: ExecutionContext
  ): Consequence[Unit] =
    ctx.entitySpace.synchronized {
      ctx.entitySpace.entityOption(DurableJobStoreEntity.Collection) match {
        case Some(collection) =>
          collection.descriptor.revisionBinding match {
            case Some(binding)
                if binding.representation ==
                  EntityRevisionRepresentation.Detached =>
              Consequence.unit
            case other =>
              Consequence.stateInvalid(
                s"Durable job EntityCollection revision binding mismatch: ${DurableJobStoreEntity.Collection.print}; actual=$other"
              )
          }
        case None =>
          ctx.entitySpace.registerEntity(
            DurableJobStoreEntity.Collection.name,
            _entity_collection()
          )
          Consequence.unit
      }
    }

  private def _entity_collection()(using
    ctx: ExecutionContext
  ): EntityCollection[DurableJobStoreEntity] = {
    val realm = new EntityRealm[DurableJobStoreEntity](
      entityName = DurableJobStoreEntity.Collection.name,
      loader = EntityLoader.fromEntityStore(entitystore)(using
        summon[EntityPersistent[DurableJobStoreEntity]],
        ctx
      ),
      state = new DurableJobStoreEntityRealmRef(EntityRealmState(Map.empty))
    )
    val descriptor = EntityDescriptor(
      collectionId = DurableJobStoreEntity.Collection,
      plan = EntityRuntimePlan(
        entityName = DurableJobStoreEntity.Collection.name,
        memoryPolicy = EntityMemoryPolicy.StoreOnly,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byEntityId,
        maxPartitions = 1,
        maxEntitiesPerPartition = 1,
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
      ),
      persistent = summon[EntityPersistent[DurableJobStoreEntity]],
      revisionBinding = Some(
        EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      )
    )
    new EntityCollection(descriptor, EntityStorage(realm))
  }
}

private[job] final case class DurableJobStoreEntity(
  id: EntityId,
  jobId: String,
  canonicalJson: String
) {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "jobId" -> jobId,
      "canonicalJson" -> canonicalJson
    )
}

private[job] object DurableJobStoreEntity {
  val Collection: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "durableJob")

  def entityId(jobId: String): Consequence[EntityId] =
    Option(jobId).filter(_.nonEmpty) match {
      case Some(value) =>
        EntityId.bridgeFromParts(
          major = Collection.major,
          minor = Collection.minor,
          collection = Collection,
          timestamp = Instant.EPOCH,
          entropy = _stable_entropy(value)
        )
      case None =>
        Consequence.argumentInvalid("durableJobId", "nonempty durable job id", "empty")
    }

  given EntityPersistent[DurableJobStoreEntity] with {
    def id(entity: DurableJobStoreEntity): EntityId = entity.id

    def toRecord(entity: DurableJobStoreEntity): Record =
      entity.toRecord()

    override def toStoreRecord(entity: DurableJobStoreEntity): Record =
      entity.toRecord()

    def fromRecord(record: Record): Consequence[DurableJobStoreEntity] =
      for {
        id <- EntityId.createC(record)
        jobid <- _required_string(record, "jobId")
        canonical <- _required_string(record, "canonicalJson")
        expected <- entityId(jobid)
        _ <- if (id == expected)
          Consequence.unit
        else
          Consequence.stateInvalid("Durable job storage entity id does not derive from its durable job id")
      } yield DurableJobStoreEntity(id, jobid, canonical)

    override def fromStoreRecord(record: Record): Consequence[DurableJobStoreEntity] =
      fromRecord(record)
  }

  given EntityPersistentCreate[DurableJobStoreEntity] with {
    def id(entity: DurableJobStoreEntity): Option[EntityId] =
      Some(entity.id)

    def collection(entity: DurableJobStoreEntity): EntityCollectionId =
      entity.id.collection

    def toRecord(entity: DurableJobStoreEntity): Record =
      entity.toRecord()

    override def toStoreRecord(entity: DurableJobStoreEntity): Record =
      entity.toRecord()
  }

  private def _stable_entropy(jobid: String): String =
    jobid.getBytes(StandardCharsets.UTF_8).map(value => f"${value & 0xff}%02x").mkString

  private def _required_string(
    record: Record,
    key: String
  ): Consequence[String] =
    record.getString(key).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(key)
    }
}

private[job] final class DurableJobStoreEntityRealmRef[A](
  initial: A
) extends Ref[Id, A] {
  private var _value: A = initial

  def get: A =
    _value

  def set(value: A): Unit =
    _value = value

  override def getAndSet(value: A): A = {
    val previous = _value
    _value = value
    previous
  }

  def access: (A, A => Boolean) = {
    val snapshot = _value
    val setter: A => Boolean = { value =>
      if (_value == snapshot) {
        _value = value
        true
      } else {
        false
      }
    }
    (snapshot, setter)
  }

  override def tryUpdate(f: A => A): Boolean = {
    _value = f(_value)
    true
  }

  override def tryModify[B](
    f: A => (A, B)
  ): Option[B] = {
    val (next, result) = f(_value)
    _value = next
    Some(result)
  }

  def update(f: A => A): Unit =
    _value = f(_value)

  def modify[B](f: A => (A, B)): B = {
    val (next, result) = f(_value)
    _value = next
    result
  }

  override def modifyState[B](state: State[A, B]): B = {
    val (next, result) = state.run(_value).value
    _value = next
    result
  }

  override def tryModifyState[B](state: State[A, B]): Option[B] = {
    val (next, result) = state.run(_value).value
    _value = next
    Some(result)
  }
}
