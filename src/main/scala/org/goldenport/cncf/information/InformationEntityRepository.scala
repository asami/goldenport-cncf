package org.goldenport.cncf.information

import cats.Id
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityMutationExecutionPolicy,
  EntityPersistent,
  EntityPersistentCreate,
  EntityQuery,
  EntityRevisionBinding,
  EntityRevisionRepresentation,
  EntitySearchScope,
  EntityStore,
  EntityWritePolicy,
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
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * Component-owned EntityStore repository for generated Information roots.
 *
 * @since   Aug. 31, 2026
 * @version Sep.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[information] final class InformationEntityRepository(
  owner: Option[Component]
) {
  def collectionIdC(using ctx: ExecutionContext): Consequence[EntityCollectionId] =
    owner match {
      case Some(component) =>
        try
          Consequence.success(_collection_id(component.componentId.name, ctx))
        catch {
          case _: IllegalStateException =>
            Consequence.stateInvalid(
              "InformationSpace owner requires a canonical Component identity"
            )
        }
      case None =>
        Consequence.success(_direct_test_collection_id(ctx))
    }

  def create(
    information: Information
  )(using ctx: ExecutionContext): Consequence[Information] =
    for {
      collectionid <- _ensure_collection()
      _ <- _require_collection(information.id, collectionid)
      created <- EntityStore.standard().create(information)(using
        InformationEntityRepository.informationPersistentCreate,
        ctx
      )
      loaded <- EntityStore.standard().load[Information](created.id)(using
        InformationEntityRepository.informationPersistent,
        ctx
      )
      value <- loaded match {
        case Some(information) => Consequence.success(information)
        case None => Consequence.entityNotFound(created.id.print)
      }
    } yield value

  def load(
    informationid: InformationId
  )(using ctx: ExecutionContext): Consequence[Option[Information]] =
    for {
      collectionid <- _ensure_collection()
      _ <- _require_collection(informationid, collectionid)
      information <- EntityStore.standard().load[Information](informationid)(using
        InformationEntityRepository.informationPersistent,
        ctx
      )
    } yield information

  def update(
    information: Information
  )(using ctx: ExecutionContext): Consequence[Information] =
    for {
      collectionid <- _ensure_collection()
      _ <- _require_collection(information.id, collectionid)
      saved <- EntityStore.standard().update(
        information,
        None,
        EntityMutationExecutionPolicy.default
      )(using InformationEntityRepository.informationPersistent, ctx)
    } yield saved.entity

  /**
   * Saves an Information edit from a strict ingress adapter.  The adapter's
   * observed revision is checked atomically by the standard EntityStore path;
   * it is not copied into the Information domain model.
  */
  def updateObserved(
    information: Information,
    observedRevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[Information] =
    for {
      collectionid <- _ensure_collection()
      _ <- _require_collection(information.id, collectionid)
      policy <- EntityMutationExecutionPolicy(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        writePolicy = EntityWritePolicy.WriteIfChanged,
        preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired,
        observedRevision = Some(observedRevision)
      ).validateC
      saved <- EntityStore.standard().update(
        information,
        None,
        policy
      )(using InformationEntityRepository.informationPersistent, ctx)
    } yield saved.entity

  def search()(using ctx: ExecutionContext): Consequence[Vector[Information]] =
    _ensure_collection().flatMap { collectionid =>
      EntityStore.standard()
        .searchInternal[Information](
          EntityQuery(
            collection = collectionid,
            query = Query.plan(Record.empty),
            scope = EntitySearchScope.Store
          )
        )(using InformationEntityRepository.informationPersistent, ctx)
        .map(_.data.sortBy(_.id.print))
    }

  def clear()(using ctx: ExecutionContext): Consequence[Unit] =
    search().flatMap { information =>
      _clear_information(information, Vector.empty)
    }

  private def _clear_information(
    remaining: Vector[Information],
    deleted: Vector[Information]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    remaining match {
      case head +: tail =>
        EntityStore.standard().delete(head.id) match {
          case Consequence.Success(_) =>
            _clear_information(tail, deleted :+ head)
          case Consequence.Failure(deletefailure) =>
            _restore_information(deleted.reverse) match {
              case Consequence.Success(_) =>
                Consequence.Failure(deletefailure)
              case Consequence.Failure(restorefailure) =>
                Consequence.Failure(deletefailure ++ restorefailure)
            }
        }
      case _ =>
        Consequence.unit
    }

  private def _restore_information(
    information: Vector[Information]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    information match {
      case head +: tail =>
        val restored = EntityStore.standard().restore(head.id)
        val remaining = _restore_information(tail)
        restored.zip(remaining).map(_ => ())
      case _ =>
        Consequence.unit
    }

  private def _ensure_collection()(using
    ctx: ExecutionContext
  ): Consequence[EntityCollectionId] =
    collectionIdC.flatMap { collectionid =>
      ctx.entitySpace.entityOption(collectionid) match {
        case Some(collection) =>
          collection.descriptor.revisionBinding match {
            case Some(binding)
                if binding.representation ==
                  EntityRevisionRepresentation.Embedded =>
              Consequence.success(collectionid)
            case other =>
              Consequence.stateInvalid(
                s"Information EntityCollection revision binding mismatch: ${collectionid.print}; actual=$other"
              )
          }
        case None =>
          ctx.entitySpace.registerEntity(
            collectionid.name,
            _entity_collection(collectionid)
          )
          Consequence.success(collectionid)
      }
    }

  private def _entity_collection(
    collectionid: EntityCollectionId
  )(using ctx: ExecutionContext): EntityCollection[Information] = {
    val realm = new EntityRealm[Information](
      entityName = collectionid.name,
      loader = EntityLoader.fromEntityStore(EntityStore.standard())(using
        InformationEntityRepository.informationPersistent,
        ctx
      ),
      state = new InformationEntityRealmRef(EntityRealmState(Map.empty))
    )
    val descriptor = EntityDescriptor(
      collectionId = collectionid,
      plan = EntityRuntimePlan(
        entityName = collectionid.name,
        memoryPolicy = EntityMemoryPolicy.StoreOnly,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byEntityId,
        maxPartitions = 1,
        maxEntitiesPerPartition = 1,
        concurrencyPolicy = EntityConcurrencyPolicy.default
      ),
      persistent = InformationEntityRepository.informationPersistent,
      revisionBinding = Some(
        EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
      )
    )
    new EntityCollection(descriptor, EntityStorage(realm))
  }

  private def _collection_id(
    componentid: String,
    ctx: ExecutionContext
  ): EntityCollectionId = {
    val namespace = ctx.idGeneration.namespace
    EntityCollectionId(
      namespace.major,
      s"${namespace.minor}_${componentid.replace(".", "_")}",
      InformationEntityRepository.entityName
    )
  }

  private def _direct_test_collection_id(
    ctx: ExecutionContext
  ): EntityCollectionId = {
    val namespace = ctx.idGeneration.namespace
    EntityCollectionId(
      namespace.major,
      namespace.minor,
      InformationEntityRepository.entityName
    )
  }

  private def _require_collection(
    informationid: InformationId,
    collectionid: EntityCollectionId
  ): Consequence[Unit] =
    if (informationid.collection == collectionid)
      Consequence.unit
    else
      Consequence.stateInvalid(
        s"Information Entity ID collection mismatch: expected ${collectionid.print}, actual ${informationid.collection.print}"
      )
}

private[information] object InformationEntityRepository {
  val entityName = "information"

  private val _revision_binding =
    EntityRevisionBinding(EntityRevisionRepresentation.Embedded)

  private val _generated_persistent =
    summon[EntityPersistent[org.goldenport.cncf.information.entity.Information]]

  val informationPersistent: EntityPersistent[Information] = new EntityPersistent[Information] {
    def id(information: Information): EntityId =
      information.id

    def toRecord(information: Information): Record =
      _generated_persistent.toRecord(information)

    override def toStoreRecord(information: Information): Record =
      _normalized_store_record(information)

    def fromRecord(record: Record): Consequence[Information] =
      _generated_persistent.fromRecord(record)

    override private[cncf] def admitStoreRecord(record: Record): Consequence[Record] =
      InformationPersistenceMigration.canonicalRecordC(record)

    override def fromStoreRecord(record: Record): Consequence[Information] =
      admitStoreRecord(record).flatMap(_generated_persistent.fromRecord)

    override private[cncf] def decodeAdmittedStoreRecord(
      record: Record
    ): Consequence[Information] =
      _generated_persistent.fromRecord(record)
  }

  val informationPersistentCreate: EntityPersistentCreate[Information] =
    new EntityPersistentCreate[Information] {
      def id(information: Information): Option[EntityId] =
        Some(information.id)

      def collection(information: Information): EntityCollectionId =
        information.id.collection

      def toRecord(information: Information): Record =
        _generated_persistent.toRecord(information)

      override def toStoreRecord(information: Information): Record =
        _revision_binding.withoutManagedRevision(
          _normalized_store_record(information)
        )
    }

  private def _normalized_store_record(information: Information): Record = {
    val keys = Set(
      "identityBindings",
      "rawData",
      "resolutionCandidates",
      "workingData"
    )
    Record(
      _generated_persistent.toStoreRecord(information).fields.filterNot { field =>
        keys.contains(field.key)
      }
    ) ++ Record.createFull(
      Vector(
        "rawData" -> information.rawData,
        "identityBindings" -> information.identityBindings.map(
          _binding_store_record
        ),
        "resolutionCandidates" -> information.resolutionCandidates.map(
          _candidate_store_record
        ),
        "workingData" -> information.workingData
      )
    )
  }

  private def _candidate_store_record(
    candidate: InformationResolutionCandidate
  ): Record =
    _replace_store_fields(
      candidate.toDataStore(),
      "binding" -> _binding_store_record(candidate.binding)
    )

  private[information] def bindingStoreRecord(
    binding: InformationIdentityBinding
  ): Record =
    _binding_store_record(binding)

  private def _binding_store_record(
    binding: InformationIdentityBinding
  ): Record =
    _replace_store_fields(
      binding.toDataStore(),
      "rdfSubject" -> binding.rdfSubject.map(_.value),
      "externalIdentifiers" -> binding.externalIdentifiers.map { identifier =>
        Record.dataAuto(
          "system" -> identifier.system,
          "value" -> identifier.value,
          "kind" -> identifier.kind
        )
      },
      "entityBindings" -> binding.entityBindings.map { entitybinding =>
        Record.dataAuto(
          "entityName" -> entitybinding.entityName,
          "entityId" -> entitybinding.entityId,
          "entityVersion" -> entitybinding.entityVersion,
          "component" -> entitybinding.component
        )
      },
      "knowledgeNodeId" -> binding.knowledgeNodeId.map(_.value)
    )

  private def _replace_store_fields(
    record: Record,
    replacements: (String, Any)*
  ): Record = {
    val keys = replacements.iterator.map(_._1).toSet
    Record(record.fields.filterNot(field => keys.contains(field.key))) ++
      Record.dataAuto(replacements*)
  }
}

private[information] final class InformationEntityRealmRef[A](
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
