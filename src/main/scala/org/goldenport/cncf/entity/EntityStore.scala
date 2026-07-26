package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalExpectedField,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationExecutionPath,
  EntityMutationExclusionGuard,
  EntityMutationPathRequest,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation,
  EntityVersionedSideEffect,
  Query as DataStoreQuery,
  QueryDirective,
  QueryLimit,
  QueryOrder,
  OrderDirection
}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Apr. 11, 2025
 *  version Dec. 18, 2025
 *  version Jan. 10, 2026
 *  version Feb. 26, 2026
 *  version Mar. 30, 2026
 *  version Apr. 26, 2026
 *  version May. 17, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class EntityStore {
  def name: String
//  def serialize(entity: E): Consequence[Record]
//  def deserialize(record: Record): Consequence[E]
  def isAccept(cid: EntityCollectionId): Boolean = true

  def create[T](
    entity: T,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]]

  /** Atomically creates a stable-id entity or returns the entity already stored under that id.
    * Components reach this only through the protected internal Entity DSL; it is not an upsert and
    * never changes an existing record.
   */
  def claimOrLoad[C, P](
    entity: C,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(using
      createTc: EntityPersistentCreate[C],
      persisted: EntityPersistent[P],
      ctx: ExecutionContext
  ): Consequence[EntityStore.EntityClaimResult[C, P]] =
    createTc.id(entity) match {
      case Some(id) =>
        create(entity, options)
          .map(EntityStore.EntityClaimResult.Claimed.apply)
          .recoverWith { conclusion =>
            if (
              conclusion.observation.taxonomy == org.goldenport.observation.Taxonomy.dataStoreDuplicate
            )
              load[P](id).flatMap {
                case Some(existing) =>
                  Consequence.success(EntityStore.EntityClaimResult.Loaded(existing))
                case None => Consequence.Failure(conclusion)
              }
            else
              Consequence.Failure(conclusion)
          }
      case None =>
        Consequence.argumentInvalid("entity_claim_or_load requires a stable entity id")
    }

  private[cncf] def upsert[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(
    authorize: Option[Record] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    upsert(entity, id, options)(authorize, (_: CreateResult[T]) => Consequence.unit)

  private[cncf] def upsert[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions
  )(
    authorize: Option[Record] => Consequence[Unit],
    @deprecatedName("onSaved", "0.5.1")
    onsaved: CreateResult[T] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]]

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]]

  def loadSnapshot[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntitySnapshot[T]]]

  def loadDetached[T](
    id: EntityId
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Option[EntityRevisionCarrier[T]]] =
    _unsupported_detached_revision[Option[EntityRevisionCarrier[T]]]

  private[cncf] def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  private[cncf] def saveManaged[T](
    entity: T,
    executionPolicy: EntityMutationExecutionPolicy
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def save[T](
    entity: T,
    expectedRevision: EntityRevision
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    save(
      entity,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def save[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]]

  def saveDetached[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] =
    _unsupported_detached_revision[EntityRevisionCarrier[T]]

  private[cncf] def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def update[T](
    changes: T,
    expectedRevision: EntityRevision
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    update(
      changes,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def update[T](
    changes: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]]

  def updateDetached[T](
    changes: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] =
    _unsupported_detached_revision[EntityRevisionCarrier[T]]

  def updateById[P](
    id: EntityId,
    patch: P,
    expectedRevision: EntityRevision
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] =
    updateById(
      id,
      patch,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def updateById[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot]

  private[cncf] def updateByIdManaged[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Record]

  private[cncf] def updateByIdManagedAuthoritative[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy,
    managedMutationBase: EntityStore.ManagedMutationBase
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityStore.ManagedRecordMutationResult]

  def updateByIdDetached[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[Record]] =
    _unsupported_detached_revision[EntityRevisionCarrier[Record]]

  private[cncf] def updateByIdUnversioned[P](
    id: EntityId,
    patch: P
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit]

  private[cncf] def conditionalTransition[R, P, S](
    command: EntityConditionalTransitionCommand[R, P, S]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] =
    Consequence.operationInvalid(
      "entity-conditional-transition",
      Vector(
        org.goldenport.observation.Descriptor.Facet.Reason(
          "unsupported-capability"
        ),
        org.goldenport.observation.Descriptor.Facet.Capability(
          "entitystore.conditional-transition"
        )
      )
    )

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def restore(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def deleteHard(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]]

  def searchInternal[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]]

  def uniqueValueExists[T](
    collection: EntityCollectionId,
    @deprecatedName("fieldName", "0.5.1")
    fieldname: String,
    value: String,
    @deprecatedName("excludeId", "0.5.1")
    excludeid: Option[EntityId],
    scope: EntityIdentityScope,
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
    includeentityidentropy: Boolean
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean]

  def resolveIdentity[T](
    collection: EntityCollectionId,
    value: String,
    @deprecatedName("fieldNames", "0.5.1")
    fieldnames: Vector[String],
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
    includeentityidentropy: Boolean,
    scope: EntityIdentityScope
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]]

  private def _unsupported_detached_revision[A]: Consequence[A] =
    Consequence.operationInvalid(
      "entity-detached-revision",
      Vector(
        org.goldenport.observation.Descriptor.Facet.Reason(
          "unsupported-capability"
        ),
        org.goldenport.observation.Descriptor.Facet.Capability(
          "entitystore.detached-revision"
        )
      )
    )
}

object EntityStore {
  final val PROP_ID = "id"

  final case class ManagedRecordMutationResult(
    record: Record,
    authoritativeRecord: Record
  )

  private[cncf] enum ManagedMutationBase {
    case Unresolved
    case Resolved(record: Option[Record])
  }

  def noop() = NoopEntityStore()

  def standard(): EntityStore = StandardEntityStore()

  sealed trait EntityClaimResult[+C, +P] {
    def id: EntityId
  }

  object EntityClaimResult {
    final case class Claimed[C](created: CreateResult[C]) extends EntityClaimResult[C, Nothing] {
      def id: EntityId = created.id
    }

    final case class Loaded[P](entity: P)(using persisted: EntityPersistent[P])
        extends EntityClaimResult[Nothing, P] {
      def id: EntityId = persisted.id(entity)
    }
  }

  // final case class EntityId(
  //   major: String,
  //   minor: String,
  //   collection: CollectionId
  // ) extends UniversalId(major, minor, "entity", collection.name)

  // trait EntityInstance[T] {
  // }

  // def create[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
  //   ???
  // }

  // def load[T](store: EntityStore[T])(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
  //   ???
  // }

  // def search[T](store: EntityStore[T], directive: QueryDirective)(using instance: EntityInstance[T]): Consequence[SearchResult] = {
  //   ???
  // }

  // def store[T](store: EntityStore[T], id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def update[T](store: EntityStore[T], id: EntityId, changes: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def delete[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
  //   ???
  // }
}

final case class EntityCreateOptions(
  defaultProfiles: Set[String] = Set.empty,
  defaultValues: Record = Record.empty
) {
  def hasDefaultProfile(name: String): Boolean =
    defaultProfiles.contains(name.trim.toLowerCase(java.util.Locale.ROOT))
}
object EntityCreateOptions {
  val default: EntityCreateOptions = EntityCreateOptions()
  val sharedRecord: EntityCreateOptions = EntityCreateOptions(Set("public-read"))
}

case class CreateResult[T](
  id: EntityId,
  record: Option[Record] = None
) {
  def toRecord: Record = Record.data(
    "id" -> id.print
  )
}
case class GetResult[T]()
case class UpdateResult[T]()
case class DeleteResult[T]()

class NoopEntityStore() extends EntityStore {
  def name: String = "noop"
  def create[T](entity: T, options: EntityCreateOptions = EntityCreateOptions.default)(using
      tc: EntityPersistentCreate[T],
      ctx: ExecutionContext
  ): Consequence[CreateResult[T]] = ???
  private[cncf] def upsert[T](entity: T, id: EntityId, options: EntityCreateOptions)(
      authorize: Option[Record] => Consequence[Unit],
      @deprecatedName("onSaved", "0.5.1") onsaved: CreateResult[T] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = ???
  def load[T](id: EntityId)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[Option[T]] = ???
  def loadSnapshot[T](id: EntityId)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[Option[EntitySnapshot[T]]] = ???
  private[cncf] def save[T](entity: T)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[Unit] = ???
  private[cncf] def saveManaged[T](
    entity: T,
    executionPolicy: EntityMutationExecutionPolicy
  )(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[Unit] = ???
  override def save[T](entity: T, expectedRevision: EntityRevision)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = ???
  def save[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = ???
  private[cncf] def update[T](changes: T)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[Unit] = ???
  override def update[T](changes: T, expectedRevision: EntityRevision)(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = ???
  def update[T](
    changes: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = ???
  override def updateById[P](id: EntityId, patch: P, expectedRevision: EntityRevision)(using
      tc: EntityPersistentUpdate[P],
      ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] = ???
  def updateById[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
      tc: EntityPersistentUpdate[P],
      ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] = ???
  private[cncf] def updateByIdManaged[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy
  )(using
      tc: EntityPersistentUpdate[P],
      ctx: ExecutionContext
  ): Consequence[Record] = ???
  private[cncf] def updateByIdManagedAuthoritative[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy,
    managedMutationBase: EntityStore.ManagedMutationBase
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityStore.ManagedRecordMutationResult] = ???
  private[cncf] def updateByIdUnversioned[P](
    id: EntityId,
    patch: P
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit] = ???
  def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def restore(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def search[T](query: EntityQuery[T])(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[SearchResult[T]] = ???
  def searchInternal[T](query: EntityQuery[T])(using
      tc: EntityPersistent[T],
      ctx: ExecutionContext
  ): Consequence[SearchResult[T]] = ???
  def uniqueValueExists[T](
      collection: EntityCollectionId,
      @deprecatedName("fieldName", "0.5.1") fieldName: String,
      value: String,
      @deprecatedName("excludeId", "0.5.1") excludeId: Option[EntityId],
      scope: EntityIdentityScope,
      @deprecatedName("includeEntityIdEntropy", "0.5.1") includeEntityIdEntropy: Boolean
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean] = ???
  def resolveIdentity[T](
      collection: EntityCollectionId,
      value: String,
      @deprecatedName("fieldNames", "0.5.1") fieldNames: Vector[String],
      @deprecatedName("includeEntityIdEntropy", "0.5.1") includeEntityIdEntropy: Boolean,
      scope: EntityIdentityScope
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]] = ???
}

class StandardEntityStore(
) extends EntityStore {
  import EntityStore.*

  private val _upsert_locks = Array.fill(64)(new Object)

  def name: String = "standard"

  def createId[T](entity: T)(using
      tc: EntityPersistentCreate[T],
      ctx: ExecutionContext
  ): EntityId = {
      val collection = tc.collection(entity)
      ctx.idGeneration.entityId(collection)
    }

  def create[T](
    entity: T,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    val id = tc.id(entity) getOrElse createId(entity)
    val revisionbinding = _revision_binding_option(id.collection)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      initialized <- revisionbinding match {
        case Some(binding) =>
          for {
            admitted <- binding.rejectManagedPatch(
              tc.toStoreRecord(entity),
              "entity"
            )
            initialized <- binding.initializeForCreate(
              _complement_create_record(admitted, id, options)
            )
          } yield initialized
        case None =>
          _reject_detached_managed_field(
            tc.toStoreRecord(entity),
            "entity"
          ).map(_complement_create_record(_, id, options))
      }
      rec <- ContentBodyStoragePolicy.prepareForSave(id, initialized)
      _ <- _with_datastore_calltree("create", cid, Some(dsid)) {
        ds.create(cid, dsid, rec)
      }
    } yield CreateResult(id, Some(rec))
  }

  private[cncf] override def upsert[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions
  )(
    authorize: Option[Record] => Consequence[Unit],
    @deprecatedName("onSaved", "0.5.1")
    onsaved: CreateResult[T] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _with_upsert_lock(id) {
      val revisionbinding = _revision_binding_option(id.collection)
      for {
        cid <- ctx.entityStoreSpace.dataStoreCollection(id)
        dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
        ds <- ctx.dataStoreSpace.dataStore(cid)
        existing <- _with_datastore_calltree("load", cid, Some(dsid)) {
          ds.load(cid, dsid)
        }
        _ <- authorize(existing)
        _ <- _reject_logically_deleted_existing(id, existing)
        source = tc.toStoreRecord(entity)
        admittedsource <- revisionbinding match {
          case Some(binding) =>
            binding.rejectManagedPatch(source, "entity")
          case None =>
            _reject_detached_managed_field(source, "entity")
        }
        rec <- (existing, revisionbinding) match {
          case (Some(current), Some(binding)) =>
            val admitted =
              SimpleEntityStorageShapePolicy.withoutManagedFields(
                admittedsource
              )
            for {
              candidate <- _merge_versioned_update_record(
                current,
                _complement_update_record(admitted, id),
                binding
              )
              preparation <-
                ContentBodyStoragePolicy.planForVersionedSave(
                  id,
                  binding.withoutManagedRevision(candidate),
                  preserveExistingOverflowOnMissingContent = true
                )
              mutation <- _mutate_versioned(
                cid,
                dsid,
                preparation,
                binding,
                None,
                EntityMutationExecutionPolicy(
                  concurrencyPolicy = EntityConcurrencyPolicy.None
                )
              )
              record <- _managed_record(
                id,
                mutation,
                binding
              )
            } yield record
          case (Some(current), None) =>
            for {
              candidate <- _merge_plain_update_record(
                current,
                _complement_update_record(admittedsource, id)
              )
              prepared <- ContentBodyStoragePolicy.prepareForSave(
                id,
                candidate,
                preserveExistingOverflowOnMissingContent = true
              )
              _ <- _with_datastore_calltree("upsert", cid, Some(dsid)) {
                ds.save(cid, dsid, prepared)
              }
            } yield prepared
          case (None, Some(binding)) =>
            for {
              initialized <- binding.initializeForCreate(
                _complement_create_record(admittedsource, id, options)
              )
              prepared <- ContentBodyStoragePolicy.prepareForSave(
                id,
                initialized
              )
              _ <- _with_datastore_calltree("upsert", cid, Some(dsid)) {
                ds.save(cid, dsid, prepared)
              }
            } yield prepared
          case (None, None) =>
            for {
              prepared <- ContentBodyStoragePolicy.prepareForSave(
                id,
                _complement_create_record(admittedsource, id, options)
              )
              _ <- _with_datastore_calltree("upsert", cid, Some(dsid)) {
                ds.save(cid, dsid, prepared)
              }
            } yield prepared
        }
        result = CreateResult[T](id, Some(rec))
        _ <- onsaved(result)
      } yield result
    }

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] =
    _load_record(id).flatMap(
      _.traverse(
        _decode_entity(id.collection, _, tc)
      )
    )

  def loadSnapshot[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntitySnapshot[T]]] =
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Embedded
      )
      record <- _load_record(id)
      snapshot <- record.traverse(
        binding.snapshot(_)(
          EntityPersistent._decode_store_record(tc, id.collection, _)
        )
      )
    } yield snapshot

  override def loadDetached[T](
    id: EntityId
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Option[EntityRevisionCarrier[T]]] =
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Detached
      )
      record <- _load_record(id)
      carrier <- record.traverse(
        binding.detachedCarrier(_)(
          EntityPersistent._decode_store_record(tc, id.collection, _)
        )
      )
    } yield carrier

  private def _load_record(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Option[Record]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      o <- _with_datastore_calltree("load", cid, Some(dsid)) {
        ds.load(cid, dsid)
      }
      // Normal get uses the same access-scope hook as search. Today this means
      // deletedAt exclusion; tenant scope is a prepared NOP hook tied to
      // ExecutionContext.
      visible = o.filter(EntityAccessScopePolicy.normalRecordVisible(id.collection, _))
      _ = _emit_entity_access(
        "entity.load.hit.data-store",
        Record.dataAuto(
          "entity" -> id.collection.name,
          "id" -> id.value,
          "source" -> "data-store",
          "raw-count" -> o.size,
          "visible-count" -> visible.size
        )
      )
      hydrated <- visible.traverse(ContentBodyStoragePolicy.hydrate(id, _))
    } yield hydrated

  private[cncf] def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
    saveManaged(entity, EntityMutationExecutionPolicy.default)

  private[cncf] def saveManaged[T](
    entity: T,
    executionPolicy: EntityMutationExecutionPolicy
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = {
    val id = tc.id(entity)
    val revisionbinding = _revision_binding_option(id.collection)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(cid, dsid)
      _ <- _reject_logically_deleted_existing(id, existing)
      _ <- existing match {
        case Some(_) =>
          revisionbinding match {
            case Some(binding) =>
              _save_managed_result(
                entity,
                binding,
                None,
                executionPolicy,
                None
              ).flatMap(_managed_save_result)
            case None =>
              _save_plain(entity, id, cid, dsid, existing)
          }
        case None =>
          revisionbinding match {
            case Some(binding) =>
              for {
                datastore <- ctx.dataStoreSpace.dataStore(cid)
                admitted <- binding.rejectManagedPatch(
                  tc.toStoreRecord(entity),
                  "entity"
                )
                initialized <- binding.initializeForCreate(
                  _complement_save_record(
                    admitted,
                    id,
                    None
                  )
                )
                prepared <- ContentBodyStoragePolicy.prepareForSave(
                  id,
                  initialized
                )
                _ <- _with_datastore_calltree("create", cid, Some(dsid)) {
                  datastore.create(cid, dsid, prepared)
                }
              } yield ()
            case None =>
              _save_plain(entity, id, cid, dsid, None)
          }
      }
    } yield ()
  }

  def save[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    _save_versioned(
      entity,
      expectedRevision,
      executionPolicy,
      None
    )

  override def saveDetached[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] = {
    val id = tc.id(entity)
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Detached
      )
      result <- _save_managed_result(
        entity,
        binding,
        expectedRevision,
        executionPolicy,
        None
      )
      carrier <- _typed_detached_carrier(id, result, binding, tc)
    } yield carrier
  }

  private def _save_versioned[T](
    entity: T,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = {
    val id = tc.id(entity)
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Embedded
      )
      result <- _save_managed_result(
        entity,
        binding,
        expectedrevision,
        executionpolicy,
        concurrencyoverride
      )
      snapshot <- _typed_snapshot(
        id,
        result,
        binding,
        tc
      )
    } yield snapshot
  }

  private def _save_managed_result[T](
    entity: T,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] = {
    val id = tc.id(entity)
    val policy =
      executionpolicy.copy(
        concurrencyPolicy =
          concurrencyoverride.getOrElse(
            EntityConcurrencyPolicy.effectivePolicy(
              _concurrency_policy(id.collection),
              executionpolicy.concurrencyPolicy
            )
          )
      )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(cid, dsid)
      base <- _required_record(dsid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _admit_typed_mutation(
        revisionbinding,
        tc.toStoreRecord(entity),
        expectedrevision
      )
      effectiveexpected <- _effective_expected_revision(
        revisionbinding,
        base,
        expectedrevision.orElse(admitted._2),
        policy
      )
      candidate =
        _complement_save_record(
          admitted._1,
          id,
          Some(base)
        )
      preparation <-
        ContentBodyStoragePolicy.planForVersionedSave(id, candidate)
      result <- _mutate_versioned(
        cid,
        dsid,
        preparation,
        revisionbinding,
        effectiveexpected,
        policy
      )
    } yield result
  }

  private def _managed_save_result(
    result: EntityVersionedMutationResult
  ): Consequence[Unit] =
    result match {
      case _: EntityVersionedMutationResult.Applied =>
        Consequence.unit
      case _: EntityVersionedMutationResult.NoOp =>
        Consequence.unit
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private[cncf] def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
    _revision_binding_option(tc.id(changes).collection) match {
      case Some(binding) =>
        _update_managed_result(
          changes,
          binding,
          None,
          EntityMutationExecutionPolicy.default,
          Some(EntityConcurrencyPolicy.None)
        ).map(_ => ())
      case None =>
        _update_plain(changes)
    }

  def update[T](
    changes: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    _update_versioned(
      changes,
      expectedRevision,
      executionPolicy,
      None
    )

  override def updateDetached[T](
    changes: T,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] = {
    val id = tc.id(changes)
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Detached
      )
      result <- _update_managed_result(
        changes,
        binding,
        expectedRevision,
        executionPolicy,
        None
      )
      carrier <- _typed_detached_carrier(id, result, binding, tc)
    } yield carrier
  }

  private def _update_versioned[T](
    changes: T,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = {
    val id = tc.id(changes)
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Embedded
      )
      result <- _update_managed_result(
        changes,
        binding,
        expectedrevision,
        executionpolicy,
        concurrencyoverride
      )
      snapshot <- _typed_snapshot(
        id,
        result,
        binding,
        tc
      )
    } yield snapshot
  }

  private def _update_managed_result[T](
    changes: T,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] = {
    val id = tc.id(changes)
    val policy =
      executionpolicy.copy(
        concurrencyPolicy =
          concurrencyoverride.getOrElse(
            EntityConcurrencyPolicy.effectivePolicy(
              _concurrency_policy(id.collection),
              executionpolicy.concurrencyPolicy
            )
          )
      )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(cid, dsid)
      base <- _required_record(dsid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _admit_typed_mutation(
        revisionbinding,
        tc.toStoreRecord(changes),
        expectedrevision
      )
      effectiveexpected <- _effective_expected_revision(
        revisionbinding,
        base,
        expectedrevision.orElse(admitted._2),
        policy
      )
      candidate <- _merge_versioned_update_record(
        base,
        _complement_update_record(
          admitted._1,
          id
        ),
        revisionbinding
      )
      preparation <- ContentBodyStoragePolicy.planForVersionedSave(
        id,
        revisionbinding.withoutManagedRevision(candidate),
        preserveExistingOverflowOnMissingContent = true
      )
      result <- _mutate_versioned(
        cid,
        dsid,
        preparation,
        revisionbinding,
        effectiveexpected,
        policy
      )
    } yield result
  }

  def updateById[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] =
    _update_by_id_versioned(
      id,
      patch,
      expectedRevision,
      executionPolicy,
      None
    )

  private[cncf] def updateByIdManaged[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Record] =
    updateByIdManagedAuthoritative(
      id,
      patch,
      executionPolicy,
      EntityStore.ManagedMutationBase.Unresolved
    ).map(_.record)

  private[cncf] def updateByIdManagedAuthoritative[P](
    id: EntityId,
    patch: P,
    executionPolicy: EntityMutationExecutionPolicy,
    managedMutationBase: EntityStore.ManagedMutationBase
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityStore.ManagedRecordMutationResult] =
    (_revision_binding_option(id.collection) match {
      case Some(binding) =>
        for {
          expectedrevision <- managedMutationBase match {
            case EntityStore.ManagedMutationBase.Resolved(Some(record)) =>
              binding.revision(record).map(Some(_))
            case EntityStore.ManagedMutationBase.Resolved(None) =>
              Consequence.entityNotFound(s"entity not found: ${id.print}")
            case EntityStore.ManagedMutationBase.Unresolved =>
              Consequence.success(None)
          }
          result <- _update_by_id_managed_result(
            id,
            patch,
            binding,
            expectedrevision,
            executionPolicy,
            None
          )
          mutation <- _record_mutation_result(id, result, binding)
        } yield mutation
      case None =>
        _update_by_id_plain_record(id, patch).map(record =>
          EntityStore.ManagedRecordMutationResult(record, record)
        )
    }).recoverWith(EntityConcurrencyMetadata.mutationTargetFailure)

  override def updateByIdDetached[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[Record]] =
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Detached
      )
      result <- _update_by_id_managed_result(
        id,
        patch,
        binding,
        expectedRevision,
        executionPolicy,
        None
      )
      carrier <- _record_detached_carrier(id, result, binding)
    } yield carrier

  private[cncf] def updateByIdUnversioned[P](
    id: EntityId,
    patch: P
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    _revision_binding_option(id.collection) match {
      case Some(binding) =>
        _update_by_id_managed_unversioned(
          id,
          patch,
          binding,
          EntityMutationExecutionPolicy.default.copy(
            concurrencyPolicy = EntityConcurrencyPolicy.None
          )
        )
      case None =>
        _update_by_id_plain(id, patch)
    }

  private def _update_by_id_versioned[P](
    id: EntityId,
    patch: P,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] = {
    for {
      binding <- _required_revision_binding(
        id.collection,
        EntityRevisionRepresentation.Embedded
      )
      result <- _update_by_id_managed_result(
        id,
        patch,
        binding,
        expectedrevision,
        executionpolicy,
        concurrencyoverride
      )
      snapshot <- _record_snapshot(id, result, binding)
    } yield snapshot
  }

  private def _update_by_id_managed_result[P](
    id: EntityId,
    patch: P,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy,
    concurrencyoverride: Option[EntityConcurrencyPolicy]
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] = {
    val policy =
      executionpolicy.copy(
        concurrencyPolicy =
          concurrencyoverride.getOrElse(
            EntityConcurrencyPolicy.effectivePolicy(
              _concurrency_policy(id.collection),
              executionpolicy.concurrencyPolicy
            )
          )
      )
    (for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      changes <- revisionbinding.rejectManagedPatch(
        Update.toChangesRecord(tc.toStoreRecord(patch)),
        "patch"
      )
      complemented = _complement_update_record(changes, id)
      path <- _select_patch_mutation_path(
        cid,
        policy,
        complemented
      )
      result <- path match {
        case EntityMutationExecutionPath.DirectAlwaysWrite =>
          _mutate_direct_patch(
            cid,
            dsid,
            revisionbinding,
            complemented
          )
        case EntityMutationExecutionPath.OptimisticCompareAndSet =>
          _mutate_compare_and_set_patch(
            cid,
            dsid,
            revisionbinding,
            expectedrevision,
            policy,
            complemented
          )
        case _ =>
          _update_by_id_guarded_result(
            id,
            cid,
            dsid,
            revisionbinding,
            expectedrevision,
            policy,
            complemented
          )
      }
    } yield result).recoverWith(EntityConcurrencyMetadata.mutationTargetFailure)
  }

  private def _update_by_id_guarded_result(
    id: EntityId,
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    policy: EntityMutationExecutionPolicy,
    changes: Record
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    for {
      existing <- _raw_record(collection, entryid)
      base <- _required_record(entryid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      effectiveexpected <- _effective_expected_revision(
        revisionbinding,
        base,
        expectedrevision,
        policy
      )
      candidate <- _merge_versioned_update_record(
        base,
        changes,
        revisionbinding
      )
      preparation <- ContentBodyStoragePolicy.planForVersionedSave(
        id,
        revisionbinding.withoutManagedRevision(candidate),
        preserveExistingOverflowOnMissingContent = true
      )
      result <- _mutate_versioned(
        collection,
        entryid,
        preparation,
        revisionbinding,
        effectiveexpected,
        policy
      )
    } yield result

  private def _update_by_id_managed_unversioned[P](
    id: EntityId,
    patch: P,
    revisionbinding: EntityRevisionBinding,
    policy: EntityMutationExecutionPolicy
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entryid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      changes <- revisionbinding.rejectManagedPatch(
        Update.toChangesRecord(tc.toStoreRecord(patch)),
        "patch"
      )
      complemented = _complement_update_record(changes, id)
      path <- _select_patch_mutation_path(
        collection,
        policy,
        complemented,
        EntityMutationReadbackRequirement.None
      )
      _ <- path match {
        case EntityMutationExecutionPath.DirectAlwaysWrite =>
          ctx.dataStoreSpace
            .mutateEntityDirect(
              EntityDirectMutationPlan(
                collection,
                entryid,
                revisionbinding.storageFieldName,
                complemented,
                EntityMutationReadbackRequirement.None,
                _native_mutation_exclusion_guards
              )
            )
            .flatMap(_native_mutation_unit)
        case _ =>
          _update_by_id_guarded_result(
            id,
            collection,
            entryid,
            revisionbinding,
            None,
            policy,
            complemented
          ).map(_ => ())
      }
    } yield ()

  private def _select_patch_mutation_path(
    collection: DataStore.CollectionId,
    policy: EntityMutationExecutionPolicy,
    changes: Record,
    readbackrequirement: EntityMutationReadbackRequirement =
      EntityMutationReadbackRequirement.AuthoritativeRecord
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityMutationExecutionPath] = {
    val mustguard = _has_content_body_change(changes)
    if (mustguard)
      Consequence.success(EntityMutationExecutionPath.GuardedVersionedFallback)
    else
      ctx.dataStoreSpace.selectEntityMutationPath(
        collection,
        EntityMutationPathRequest(
          policy,
          readbackrequirement,
          hasSideEffects = false
        )
      )
  }

  private def _mutate_direct_patch(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    revisionbinding: EntityRevisionBinding,
    changes: Record
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    ctx.dataStoreSpace
      .mutateEntityDirect(
        EntityDirectMutationPlan(
          collection,
          entryid,
          revisionbinding.storageFieldName,
          changes,
          EntityMutationReadbackRequirement.AuthoritativeRecord,
          _native_mutation_exclusion_guards
        )
      )
      .flatMap(_native_mutation_result)

  private def _mutate_compare_and_set_patch(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    policy: EntityMutationExecutionPolicy,
    changes: Record
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] = {
    _native_compare_and_set_expected_revision(
      collection,
      entryid,
      revisionbinding,
      expectedrevision,
      policy
    )
      .flatMap { revision =>
        ctx.dataStoreSpace
          .compareAndSetEntity(
            EntityCompareAndSetMutationPlan(
              collection,
              entryid,
              revisionbinding.storageFieldName,
              revision,
              changes,
              EntityMutationReadbackRequirement.AuthoritativeRecord,
              _native_mutation_exclusion_guards
            )
          )
          .flatMap(_native_mutation_result)
      }
  }

  private def _native_compare_and_set_expected_revision(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    policy: EntityMutationExecutionPolicy
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevision] =
    policy.preconditionPolicy match {
      case RevisionPreconditionPolicy.ObservedRequired =>
        policy.observedRevision
          .map(Consequence.success)
          .getOrElse(Consequence.argumentMissing("expectedRevision"))
      case RevisionPreconditionPolicy.Managed =>
        expectedrevision
          .map(Consequence.success)
          .getOrElse(
            for {
              existing <- _raw_record(collection, entryid)
              record <- _required_record(entryid, existing)
              revision <- revisionbinding.revision(record)
            } yield revision
          )
    }

  private def _native_mutation_result(
    result: EntityMutationProviderResult
  ): Consequence[EntityVersionedMutationResult] =
    result match {
      case EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Authoritative(record)
          ) =>
        Consequence.success(EntityVersionedMutationResult.Applied(record))
      case EntityMutationProviderResult.NoOp(
            EntityMutationProviderReadback.Authoritative(record)
          ) =>
        Consequence.success(EntityVersionedMutationResult.NoOp(record))
      case EntityMutationProviderResult.Stale(expected, actual) =>
        Consequence.success(EntityVersionedMutationResult.Stale(expected, actual))
      case EntityMutationProviderResult.Applied(
            EntityMutationProviderReadback.Omitted
          ) |
          EntityMutationProviderResult.NoOp(
            EntityMutationProviderReadback.Omitted
          ) =>
        Consequence.operationInvalid(
          "entity-native-mutation",
          Vector(
            org.goldenport.observation.Descriptor.Facet.Reason(
              "missing-authoritative-readback"
            )
          )
        )
    }

  private def _native_mutation_unit(
    result: EntityMutationProviderResult
  ): Consequence[Unit] =
    result match {
      case _: EntityMutationProviderResult.Applied |
          _: EntityMutationProviderResult.NoOp =>
        Consequence.unit
      case EntityMutationProviderResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _has_content_body_change(
    changes: Record
  ): Boolean =
    changes.fields.exists { field =>
      Set(
        "content",
        "contentcharset",
        "contentstorage",
        "contentref",
        "contentbytesize",
        "contentdigest"
      ).contains(
        field.key
          .filter(_.isLetterOrDigit)
          .toLowerCase(java.util.Locale.ROOT)
      )
    }

  private def _native_mutation_exclusion_guards
      : Vector[EntityMutationExclusionGuard] =
    Vector(
      EntityMutationExclusionGuard.EqualTo(
        SimpleEntityStorageShapePolicy.targetName("aliveness"),
        Aliveness.Dead
      ),
      EntityMutationExclusionGuard.Present(
        SimpleEntityStorageShapePolicy.targetName("deletedAt")
      )
    )

  private def _update_by_id_plain_record[P](
    id: EntityId,
    patch: P
  )(using
    tc: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Record] =
    for {
      _ <- _update_by_id_plain(id, patch)
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      stored <- _raw_record(cid, dsid)
      record <- _required_record(dsid, stored)
      hydrated <- ContentBodyStoragePolicy.hydrate(id, record)
    } yield hydrated

  private[cncf] override def conditionalTransition[R, P, S](
    command: EntityConditionalTransitionCommand[R, P, S]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] = {
    val request = command.request
    val rootid = request.rootId
    for {
      rootbinding <- _required_revision_binding(rootid.collection)
      successorbinding <-
        _required_revision_binding(request.successor.collection)
      rootcollection <- ctx.entityStoreSpace.dataStoreCollection(rootid)
      rootentry <- ctx.entityStoreSpace.dataStoreEntryId(rootid)
      revision <- EntityConcurrencyMetadata.mutationRevision(
        request.expectation.expectedRevision
      )
      _ <- _reject_logically_deleted_existing(
        rootid,
        Some(command.currentRootRecord)
      )
      rootchanges <- _admit_conditional_root_changes(
        Update.toChangesRecord(
          request.patchPersistent.toStoreRecord(request.rootPatch)
        )
      )
      rootcandidate <- _merge_versioned_update_record(
        command.currentRootRecord,
        _complement_update_record(rootchanges, rootid),
        rootbinding
      )
      _ <- _require_conditional_domain_change(
        command.currentRootRecord,
        rootcandidate
      )
      rootpreparation <- ContentBodyStoragePolicy.planForVersionedSave(
        rootid,
        rootbinding.withoutManagedRevision(rootcandidate),
        preserveExistingOverflowOnMissingContent = true
      )
      providerchanges =
        _conditional_record_delta(
          _conditional_storage_record(
            rootbinding.withoutManagedRevision(
              command.currentRootRecord
            )
          ),
          _conditional_storage_record(
            rootbinding.withoutManagedRevision(
              rootpreparation.record
            )
          )
        )
      root <- DataStoreConditionalRoot.create(
        componentOwner = command.componentOwner,
        collection = rootcollection,
        entryId = rootentry,
        revisionField = rootbinding.storageFieldName,
        expectedRevision = Some(revision._1),
        expectedFields = request.expectation.values.map { expected =>
          DataStoreConditionalExpectedField(
            expected.field.storageField,
            expected.providerValue
          )
        },
        changes = providerchanges,
        nextRevision = revision._2
      )
      preparedsuccessor <- _prepare_conditional_successor(
        request.successor,
        command.componentOwner,
        command.boundSuccessor,
        successorbinding
      )
      plan <- DataStoreConditionalTransitionPlan.create(
        root,
        preparedsuccessor._1,
        _conditional_storage_side_effects(
          rootpreparation.sideEffects ++ preparedsuccessor._2
        )
      )
      providerresult <- _with_datastore_calltree(
        "entity-conditional-transition",
        rootcollection,
        Some(rootentry)
      ) {
        ctx.dataStoreSpace.conditionalTransition(plan)
      }
      result <- _conditional_transition_result(
        request,
        preparedsuccessor._3,
        providerresult,
        rootbinding,
        successorbinding
      )
    } yield result
  }

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val revisionbinding = _revision_binding_option(id.collection)
    val policy = EntityMutationExecutionPolicy(
      concurrencyPolicy = _concurrency_policy(id.collection)
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      current <- _raw_record(cid, dsid)
      r <- current match {
        case Some(rec) =>
          if (_is_soft_delete_target(rec))
            revisionbinding match {
              case Some(binding) =>
                for {
                  expected <- _effective_expected_revision(
                    binding,
                    rec,
                    None,
                    policy
                  )
                  updated <- _merge_versioned_update_record(
                    rec,
                    _soft_delete_record(rec),
                    binding
                  )
                  preparation <- ContentBodyStoragePolicy.planForVersionedSave(
                    id,
                    binding.withoutManagedRevision(updated),
                    preserveExistingOverflowOnMissingContent = true
                  )
                  result <- _mutate_versioned(
                    cid,
                    dsid,
                    preparation,
                    binding,
                    expected,
                    policy
                  )
                  _ <- _mutation_unit(result)
                } yield ()
              case None =>
                _save_plain_lifecycle(
                  id,
                  cid,
                  dsid,
                  rec,
                  _soft_delete_record(rec)
                )
            }
          else
            ContentBodyStoragePolicy.deleteOverflow(id).flatMap { _ =>
              _with_datastore_calltree("delete", cid, Some(dsid)) {
                ctx.dataStoreSpace
                  .dataStore(cid)
                  .flatMap(_.delete(cid, dsid))
              }
            }
        case None =>
          Consequence.unit
      }
    } yield r
  }

  def restore(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val revisionbinding = _revision_binding_option(id.collection)
    val policy = EntityMutationExecutionPolicy(
      concurrencyPolicy = _concurrency_policy(id.collection)
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      current <- _raw_record(cid, dsid)
      base <- _required_record(dsid, current)
      _ <-
        if (_is_logically_deleted_record(base))
          Consequence.unit
        else
          Consequence.stateConflict(
            s"Entity is not logically deleted: ${id.print}"
          )
      _ <- revisionbinding match {
        case Some(binding) =>
          for {
            expected <- _effective_expected_revision(
              binding,
              base,
              None,
              policy
            )
            restored <- _merge_versioned_update_record(
              base,
              _restore_record(base),
              binding
            )
            preparation <- ContentBodyStoragePolicy.planForVersionedSave(
              id,
              binding.withoutManagedRevision(restored),
              preserveExistingOverflowOnMissingContent = true
            )
            result <- _mutate_versioned(
              cid,
              dsid,
              preparation,
              binding,
              expected,
              policy
            )
            _ <- _mutation_unit(result)
          } yield ()
        case None =>
          _save_plain_lifecycle(
            id,
            cid,
            dsid,
            base,
            _restore_record(base)
          )
      }
    } yield ()
  }

  def deleteHard(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id.collection)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      _ <- ContentBodyStoragePolicy.deleteOverflow(id)
      r <- _with_datastore_calltree("delete", cid, Some(dsid)) {
        ds.delete(cid, dsid)
      }
    } yield r

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    val storequery = EntityDirectiveQuery.mapPaths(query.query)(tc.storeFieldName)
    // Push normal entity access scope into the datastore query where possible.
    // The same scope is post-filtered below so in-memory/SQL/direct stores keep
    // identical deletedAt and future tenant semantics.
    val scopedexpr = EntityAccessScopePolicy.normalSearchExpr(
      query.collection,
      EntityDirectiveQuery.whereOf(storequery),
      tc.storeFieldName("deletedAt")
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      directive = QueryDirective(
        query = DataStoreQuery.Expr(scopedexpr),
        order = _to_datastore_order(EntityDirectiveQuery.sortOf(storequery)),
        limit = QueryLimit.Unbounded,
        offset = 0
      )
      raw <- ctx.dataStoreSpace.search(
        cid,
        directive
      )
      // Safety filter paired with the query predicate above. This is important
      // for logical delete and for future ExecutionContext tenant scoping.
      scoped = EntityAccessScopePolicy.filterNormalRecords(query.collection, raw.records.toVector)
      accessscoped = scoped.filter(record =>
        EntityAccessScopePolicy.visibilityRecordVisible(
          query.collection,
          record,
          query.visibilityScope
        )
      )
      visible = query.visibilityScope match {
        case Some(EntityVisibilityScope.Owner) | Some(EntityVisibilityScope.Admin) =>
          accessscoped
        case _ =>
          _filter_visibility(accessscoped, query.query)
      }
      _ = _emit_entity_access(
        "entity.search.hit.data-store",
        Record.dataAuto(
          "entity" -> query.collection.name,
          "source" -> "data-store",
          "raw-count" -> raw.records.size,
          "scoped-count" -> scoped.size,
          "access-scoped-count" -> accessscoped.size,
          "visible-count" -> visible.size
        )
      )
      _ = _emit_visibility_filtered(
        query.collection.name,
        raw.records.size,
        accessscoped.size,
        visible.size
      )
      // Apply the original entity query against store records before decoding.
      // Some entity codecs intentionally expose richer value objects after
      // decode (for example AssociationDomain), while request/query values stay
      // in their wire/store shape. Filtering here keeps in-memory stores and
      // SQL stores consistent without imposing entity-value equality quirks.
      recordmatched = visible.filter(record => EntityDirectiveQuery.matches(storequery, record))
      hydrated <- ContentBodyStoragePolicy.hydrateAll(query.collection, recordmatched)
      decoded <- hydrated.traverse(
        _decode_entity(query.collection, _, tc)
      )
      sorted = EntityDirectiveQuery.sortValues(decoded, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
      count = if (query.query.includeTotal) Some(recordmatched.size) else None
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = count,
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )
  }

  def searchInternal[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      raw <- ctx.dataStoreSpace.search(
        cid,
        QueryDirective(DataStoreQuery.Empty)
      )
      scoped = EntityAccessScopePolicy.filterNormalRecords(query.collection, raw.records.toVector)
      hydrated <- ContentBodyStoragePolicy.hydrateAll(query.collection, scoped)
      decoded <- hydrated.foldLeft(Consequence.success(Vector.empty[T])) { (z, record) =>
        z.flatMap(xs =>
          _decode_entity(query.collection, record, tc)
            .map(xs :+ _)
        )
      }
      matched = decoded.filter(value => EntityDirectiveQuery.matches(query.query, value))
      sorted = EntityDirectiveQuery.sortValues(matched, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = if (query.query.includeTotal) Some(matched.size) else None,
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )

  def uniqueValueExists[T](
    collection: EntityCollectionId,
    @deprecatedName("fieldName", "0.5.1")
      fieldName: String,
    value: String,
    @deprecatedName("excludeId", "0.5.1")
      excludeId: Option[EntityId],
    scope: EntityIdentityScope,
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
      includeEntityIdEntropy: Boolean
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean] =
    _identity_records(collection).map { candidates =>
      candidates.exists { case (record, id) =>
        !excludeId.exists(_.value == id.value) &&
          scope.matches(record) &&
          (
          SimpleEntityStorageShapePolicy.stringValue(record, fieldName).contains(value) ||
            (includeEntityIdEntropy && id.parts.entropy == value)
          )
      }
    }

  def resolveIdentity[T](
    collection: EntityCollectionId,
    value: String,
    @deprecatedName("fieldNames", "0.5.1")
      fieldNames: Vector[String],
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
      includeEntityIdEntropy: Boolean,
    scope: EntityIdentityScope
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]] =
    _identity_records(collection).map { candidates =>
      candidates.collectFirst {
        case (record, id)
            if scope.matches(record) && _identity_matches(
              id,
              record,
              value,
              fieldNames,
              includeEntityIdEntropy
            ) =>
          id
    }
  }

  private def _identity_records[T](
    collection: EntityCollectionId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Vector[(Record, EntityId)]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(collection)
      raw <- ctx.dataStoreSpace.search(
        cid,
        QueryDirective(DataStoreQuery.Empty)
      )
      notdeleted = EntityLifecycleRecordPolicy.filterNotLogicallyDeleted(raw.records.toVector)
      decoded <-
        notdeleted.foldLeft(Consequence.success(Vector.empty[(Record, EntityId)])) { (z, record) =>
        z.flatMap { xs =>
          _decode_entity(collection, record, tc)
            .map { entity =>
            xs :+ (record -> tc.id(entity))
          }
        }
      }
    } yield decoded

  private def _identity_matches(
    id: EntityId,
    record: Record,
    value: String,
    fieldnames: Vector[String],
    includeentityidentropy: Boolean
  ): Boolean =
    id.value == value ||
      id.print == value ||
      fieldnames.exists(name =>
        SimpleEntityStorageShapePolicy.stringValue(record, name).contains(value)
      ) ||
      (includeentityidentropy && id.parts.entropy == value)

  private def _to_datastore_limit(
    query: EntityDirectiveQuery[?]
  ): QueryLimit =
    query.limit.map(QueryLimit.Limit.apply).getOrElse(QueryLimit.Unbounded)

  private final case class VisibilityPolicy(
    poststatuses: Option[Set[String]],
    alivenesses: Option[Set[String]]
  )

  private def _filter_visibility(
    records: Vector[Record],
    query: EntityDirectiveQuery[?]
  )(using ctx: ExecutionContext): Vector[Record] = {
    val policy = _visibility_policy(query)
    records.filter(_is_visible(_, policy))
  }

  private def _visibility_policy(
    query: EntityDirectiveQuery[?]
  )(using ctx: ExecutionContext): VisibilityPolicy = {
    val lifecycle = _lifecycle_constraint(query)
    val ismanager = _is_content_manager()
    val poststatuses = if (lifecycle.poststatusexplicit) {
      None
    } else if (ismanager) {
      val p = _post_statuses_for_manager()
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("published"))
    }
    val alivenesses = if (lifecycle.alivenessexplicit) {
      None
    } else if (ismanager) {
      val p = _alivenesses_for_manager(poststatuses.getOrElse(Set.empty))
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("alive"))
    }
    VisibilityPolicy(
      poststatuses = poststatuses,
      alivenesses = alivenesses
    )
  }

  private final case class LifecycleConstraint(
    poststatusexplicit: Boolean,
    alivenessexplicit: Boolean
  )

  private def _lifecycle_constraint(
    query: EntityDirectiveQuery[?]
  ): LifecycleConstraint = {
    val expr = EntityDirectiveQuery.whereOf(query)
    val raw = _query_condition(query)
    LifecycleConstraint(
      poststatusexplicit =
        _mentions_path(expr, Set("poststatus")) || _mentions_condition_key(raw, Set("poststatus")),
      alivenessexplicit =
        _mentions_path(expr, Set("aliveness")) || _mentions_condition_key(raw, Set("aliveness"))
    )
  }

  private def _query_condition(
    query: EntityDirectiveQuery[?]
  ): Any =
    query.query match {
      case p: EntityDirectiveQuery.Plan[?] => p.condition
      case other => other
    }

  private def _mentions_path(
    expr: EntityDirectiveQuery.Expr,
    names: Set[String]
  ): Boolean =
    expr match {
      case EntityDirectiveQuery.True => false
      case EntityDirectiveQuery.False => false
      case EntityDirectiveQuery.And(items) => items.exists(_mentions_path(_, names))
      case EntityDirectiveQuery.Or(items) => items.exists(_mentions_path(_, names))
      case EntityDirectiveQuery.Not(item) => _mentions_path(item, names)
      case EntityDirectiveQuery.FieldCondition(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Eq(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Ne(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Gt(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Gte(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Lt(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Lte(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.In(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.NotIn(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.IsNull(path) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.IsNotNull(path) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Like(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.StartsWith(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.EndsWith(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Contains(path, _, _) => names.contains(_normalize_path(path))
    }

  private def _mentions_condition_key(
    condition: Any,
    names: Set[String]
  ): Boolean =
    condition match {
      case r: Record =>
        r.asMap.keys.exists(k => names.contains(_normalize_path(k)))
      case m: Map[?, ?] =>
        m.keysIterator.collect { case k: String => k }.exists(k =>
          names.contains(_normalize_path(k))
        )
      case p: Product =>
        p.productElementNames.exists(k => names.contains(_normalize_path(k)))
      case _ =>
        false
    }

  private def _normalize_path(path: String): String = {
    val segment = path.split("\\.").lastOption.getOrElse(path)
    org.goldenport.cncf.context.RuntimeContext.PropertyNameStyle.CamelCase.transform(segment)
  }

  private def _is_visible(
    record: Record,
    policy: VisibilityPolicy
  ): Boolean = {
    val postok = policy.poststatuses match {
      case Some(allowed) =>
        _record_value(record, Vector("postStatus"))
          .flatMap(EntityLifecycleRecordPolicy.postStatusToken)
          .forall(allowed.contains)
      case None =>
        true
    }
    val aliveok = policy.alivenesses match {
      case Some(allowed) =>
        _record_value(record, Vector("aliveness"))
          .flatMap(EntityLifecycleRecordPolicy.alivenessToken)
          .forall(allowed.contains)
      case None =>
        true
    }
    postok && aliveok
  }

  private def _record_value(
    record: Record,
    keys: Vector[String]
  ): Option[Any] = {
    val m = record.asMap
    keys
      .flatMap(org.goldenport.cncf.context.RuntimeContext.Context.default.propertyName.aliases)
      .distinct
      .collectFirst(Function.unlift(m.get))
  }

  private def _is_content_manager()(using ctx: ExecutionContext): Boolean = {
    if (ctx.isAggregateInternalRead)
      return true
    val aliases = Set(
      "contentmanager",
      "contentadmin",
      "contentadministrator",
      "contentowner"
    )
    val roles = _attribute_tokens("role", "roles", "authority", "authorities")
    val capabilities = ctx.security.capabilities.map(_.name).flatMap(_split_tokens)
    val level = _split_tokens(ctx.security.level.value)
    (roles ++ capabilities ++ level).exists(x => aliases.contains(_normalize_alias(x)))
  }

  private def _post_statuses_for_manager()(using ctx: ExecutionContext): Set[String] = {
    val configured =
      _attribute_tokens("search_poststatus", "search.poststatus", "poststatus", "post_status")
      .flatMap(EntityLifecycleRecordPolicy.postStatusToken)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose =
        _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.postStatusToken)
      if (frompurpose.nonEmpty)
        frompurpose
      else
        Set("published", "draft")
    }
  }

  private def _alivenesses_for_manager(
    poststatuses: Set[String]
  )(using ctx: ExecutionContext): Set[String] = {
    val configured = _attribute_tokens("search_aliveness", "search.aliveness", "aliveness")
      .flatMap(EntityLifecycleRecordPolicy.alivenessToken)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose =
        _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.alivenessToken)
      if (frompurpose.nonEmpty)
        frompurpose
      else if (poststatuses.contains("archived"))
        Set("alive", "dead")
      else
        Set("alive")
    }
  }

  private def _attribute_tokens(
    keys: String*
  )(using ctx: ExecutionContext): Set[String] = {
    val attrs = ctx.security.principal.attributes.map { case (k, v) =>
      k.toLowerCase -> v
    }
    keys.toVector
      .flatMap(k => attrs.get(k.toLowerCase))
      .flatMap(_split_tokens)
      .toSet
  }

  private def _split_tokens(p: String): Vector[String] =
    p.split("[,\\s]+").toVector.map(_.trim).filter(_.nonEmpty)

  private def _normalize_alias(p: String): String =
    p.toLowerCase.replace("_", "").replace("-", "")

  private def _post_status_token(p: Any): Option[String] =
    EntityLifecycleRecordPolicy.postStatusToken(p)

  private def _aliveness_token(p: Any): Option[String] =
    EntityLifecycleRecordPolicy.alivenessToken(p)

  private def _to_datastore_order(
    sort: Vector[EntityDirectiveQuery.SortKey]
  ): QueryOrder =
    sort.headOption match {
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Asc)) =>
        QueryOrder.By(path, OrderDirection.Asc)
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Desc)) =>
        QueryOrder.By(path, OrderDirection.Desc)
      case None =>
        QueryOrder.None
    }

  private def _complement_create_record[T](
    record: Record,
    id: EntityId,
    options: EntityCreateOptions
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record =
    ctx.runtime.entityCreateDefaultsPolicy.complementCreateRecord(
      record = record,
      id = id,
      options = options
    )

  private def _complement_save_record(
    record: Record,
    id: EntityId,
    existing: Option[Record]
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = existing,
      includescreationdefaults = true,
      includesstatedefaults = true,
      createoptions = EntityCreateOptions.default
    )

  private def _complement_update_record(
    record: Record,
    id: EntityId
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = None,
      includescreationdefaults = false,
      includesstatedefaults = false,
      createoptions = EntityCreateOptions.default
    )

  private def _merge_update_record(
    existing: Record,
    changes: Record
  ): Consequence[Record] = {
    val sanitized = EntityConcurrencyMetadata.withoutManagedField(changes)
    val changedkeys = sanitized.keySet
    val retained = Record(_retained_existing_managed_record(existing).fields.filterNot(f =>
      changedkeys.contains(f.key)
    ))
    val domain =
      Record(SimpleEntityStorageShapePolicy.withoutManagedFields(existing).fields.filterNot(f =>
        changedkeys.contains(f.key)
      ))
    EntityConcurrencyMetadata.preserveForMutation(
      sanitized ++ retained ++ domain,
      existing
    )
  }

  private def _merge_versioned_update_record(
    existing: Record,
    changes: Record,
    revisionbinding: EntityRevisionBinding
  ): Consequence[Record] = {
    val sanitized = revisionbinding.withoutManagedRevision(changes)
    val changedkeys = sanitized.keySet
    val retained =
      Record(
        _retained_existing_managed_record(existing).fields.filterNot(field =>
          changedkeys.contains(field.key)
        )
      )
    val domain =
      Record(
        SimpleEntityStorageShapePolicy
          .withoutManagedFields(existing)
          .fields
          .filterNot(field => changedkeys.contains(field.key))
      )
    Consequence.success(
      revisionbinding.withoutManagedRevision(
        sanitized ++ retained ++ domain
      )
    )
  }

  private def _merge_plain_update_record(
    existing: Record,
    changes: Record
  ): Consequence[Record] = {
    val changedkeys = changes.keySet
    val retained =
      Record(
        _retained_existing_managed_record(existing).fields.filterNot(field =>
          changedkeys.contains(field.key)
        )
      )
    val domain =
      Record(
        SimpleEntityStorageShapePolicy
          .withoutManagedFields(existing)
          .fields
          .filterNot(field => changedkeys.contains(field.key))
      )
    Consequence.success(changes ++ retained ++ domain)
  }

  private def _save_plain[T](
    entity: T,
    id: EntityId,
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    existing: Option[Record]
  )(using
    persistent: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      admitted <- _reject_detached_managed_field(
        persistent.toStoreRecord(entity),
        "entity"
      )
      candidate = _complement_save_record(admitted, id, existing)
      prepared <- ContentBodyStoragePolicy.prepareForSave(id, candidate)
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        if (existing.isDefined) "save" else "create",
        collection,
        Some(entryid)
      ) {
        existing match {
          case Some(_) =>
            datastore.save(collection, entryid, prepared)
          case None =>
            datastore.create(collection, entryid, prepared)
        }
      }
    } yield ()

  private def _update_plain[T](
    changes: T
  )(using
    persistent: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Unit] = {
    val id = persistent.id(changes)
    for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entryid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(collection, entryid)
      base <- _required_record(entryid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _reject_detached_managed_field(
        persistent.toStoreRecord(changes),
        "entity"
      )
      candidate <- _merge_plain_update_record(
        base,
        _complement_update_record(admitted, id)
      )
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()
  }

  private def _update_by_id_plain[P](
    id: EntityId,
    patch: P
  )(using
    persistent: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entryid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(collection, entryid)
      base <- _required_record(entryid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _reject_detached_managed_field(
        Update.toChangesRecord(persistent.toStoreRecord(patch)),
        "patch"
      )
      candidate <- _merge_plain_update_record(
        base,
        _complement_update_record(admitted, id)
      )
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()

  private def _save_plain_lifecycle(
    id: EntityId,
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    existing: Record,
    changes: Record
  )(using
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      candidate <- _merge_plain_update_record(existing, changes)
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()

  private def _reject_detached_managed_field(
    record: Record,
    parameter: String
  ): Consequence[Record] =
    EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      .rejectManagedPatch(record, parameter)

  private def _retained_existing_managed_record(
    existing: Record
  ): Record = {
    val generalfields = Vector(
      "id",
      "shortid",
      "name",
      "createdAt",
      "createdBy",
      "postStatus",
      "aliveness",
      "tenantId",
      "organizationId",
      "publishAt",
      "publicAt",
      "publishedBy"
    ).flatMap { name =>
      SimpleEntityStorageShapePolicy.value(existing, name)
        .map(SimpleEntityStorageShapePolicy.targetName(name) -> _)
    }
    val securityfields =
      SimpleEntityStorageShapePolicy.securityAttributesFromRecord(existing)
        .toVector
        .flatMap { attributes =>
          Vector(
            Some("owner_id" -> attributes.ownerId.id.value),
            Option(attributes.groupId.id.value)
              .filter(_.nonEmpty)
              .map("group_id" -> _),
            Option(attributes.privilegeId.id.value)
              .filter(_.nonEmpty)
              .map("privilege_id" -> _),
            Some(
              "permission" ->
                SimpleEntityStorageShapePolicy.permissionJson(attributes.rights)
            )
          ).flatten
        }
    Record.dataAuto((generalfields ++ securityfields)*)
  }

  private def _reject_logically_deleted_existing(
    id: EntityId,
    existing: Option[Record]
  ): Consequence[Unit] =
    existing match {
      case Some(record) if EntityLifecycleRecordPolicy.isLogicallyDeleted(record) =>
        Consequence.entityNotFound(s"entity is logically deleted: ${id.value}")
      case _ =>
        Consequence.unit
    }

  private def _with_upsert_lock[A](
    id: EntityId
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val index = Math.floorMod(id.print.hashCode, _upsert_locks.length)
    _upsert_locks(index).synchronized(body)
  }

  private def _complement_record(
    record: Record,
    id: EntityId,
    existing: Option[Record],
    includescreationdefaults: Boolean,
    includesstatedefaults: Boolean,
    createoptions: EntityCreateOptions
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val zonednow = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val defaults = Vector.newBuilder[(String, Any)]
    val existingmap = existing.map(_.asMap).getOrElse(Map.empty)

    def _add_if_missing_(canonical: String, value: => Option[Any]): Unit =
      SimpleEntityStorageShapePolicy.value(record, canonical) match {
        case Some(current) =>
          defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> current)
        case None =>
          value.foreach(v =>
            defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> v)
          )
      }

    def _add_or_replace_(canonical: String, value: => Option[Any]): Unit =
      value.foreach(v => defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> v))

    def _existing_value_(canonical: String): Option[Any] =
      existing.flatMap(SimpleEntityStorageShapePolicy.value(_, canonical))

    if (includescreationdefaults) {
      _add_if_missing_("id", _existing_value_("id").orElse(Some(id.value)))
      _add_if_missing_("shortid", _existing_value_("shortid").orElse(Some(id.parts.entropy)))
      _add_if_missing_("name", _existing_value_("name").orElse(Some(principalid)))
      _add_if_missing_("createdAt", _existing_value_("createdAt").orElse(Some(now)))
      _add_if_missing_("createdBy", _existing_value_("createdBy").orElse(Some(principal)))
    }

    _add_or_replace_("updatedAt", Some(now))
    _add_or_replace_("updatedBy", Some(principal))
    if (includesstatedefaults) {
      _add_if_missing_(
        "postStatus",
        _existing_value_("postStatus").orElse(Some(_default_post_status(createoptions)))
      )
      _add_if_missing_("aliveness", _existing_value_("aliveness").orElse(Some(Aliveness.default)))
    }
    if (includescreationdefaults) {
      val security =
        if (createoptions.hasDefaultProfile("publication"))
          org.simplemodeling.model.value.SecurityAttributes.publicOwnedBy(principal)
        else
          org.simplemodeling.model.value.SecurityAttributes.privateOwnedBy(principal)
      _add_if_missing_(
        "ownerId",
        _existing_value_("ownerId").orElse(Some(security.ownerId.id.value))
      )
      _add_if_missing_(
        "groupId",
        _existing_value_("groupId").orElse(Some(security.groupId.id.value))
      )
      _add_if_missing_(
        "privilegeId",
        _existing_value_("privilegeId").orElse(Some(security.privilegeId.id.value))
      )
      _add_if_missing_(
        "permission",
        Some(SimpleEntityStorageShapePolicy.permissionJson(security.rights))
      )
    }
    if (includescreationdefaults && createoptions.hasDefaultProfile("publication")) {
      _add_if_missing_("publishAt", _existing_value_("publishAt").orElse(Some(zonednow)))
      _add_if_missing_("publicAt", _existing_value_("publicAt").orElse(Some(zonednow)))
      _add_if_missing_("publishedBy", _existing_value_("publishedBy").orElse(Some(principal)))
    }
    _add_if_missing_("traceId", Some(ctx.observability.traceId.value))
    _add_if_missing_("correlationId", ctx.observability.correlationId.map(_.value))

    val complement = Record.dataAuto(defaults.result()*)
    complement ++
      SimpleEntityStorageShapePolicy.withoutManagedFields(
        EntityConcurrencyMetadata.withoutManagedField(record)
      )
  }

  private def _default_post_status(
    options: EntityCreateOptions
  ): Any =
    if (options.hasDefaultProfile("publication"))
      PostStatus.Published
    else
      PostStatus.default

  private def _soft_delete_record(
    existing: Record
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val base = Vector.newBuilder[(String, Any)]
    base += SimpleEntityStorageShapePolicy.targetName("postStatus") -> PostStatus.Archived
    base += SimpleEntityStorageShapePolicy.targetName("aliveness") -> Aliveness.Dead
    base += SimpleEntityStorageShapePolicy.targetName("updatedAt") -> now
    base += SimpleEntityStorageShapePolicy.targetName("updatedBy") -> principal
    base += SimpleEntityStorageShapePolicy.targetName("traceId") -> ctx.observability.traceId.value
    ctx.observability.correlationId.foreach { x =>
      base += SimpleEntityStorageShapePolicy.targetName("correlationId") -> x.value
    }
    base += SimpleEntityStorageShapePolicy.targetName("deletedAt") -> now
    base += SimpleEntityStorageShapePolicy.targetName("deletedBy") -> principal
    Record.dataAuto(base.result()*)
  }

  private def _restore_record(
    existing: Record
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val principal = ctx.security.principal.id.value
    val deletedfields =
      Set("deletedAt", "deleted_at", "deletedBy", "deleted_by")
    val preserved =
      Record(existing.fields.filterNot(field => deletedfields.contains(field.key)))
    val restored = Record.dataAuto(
      SimpleEntityStorageShapePolicy.targetName("postStatus") -> PostStatus.Draft,
      SimpleEntityStorageShapePolicy.targetName("aliveness") -> Aliveness.Alive,
      SimpleEntityStorageShapePolicy.targetName("updatedAt") -> now,
      SimpleEntityStorageShapePolicy.targetName("updatedBy") -> principal,
      SimpleEntityStorageShapePolicy.targetName("traceId") ->
        ctx.observability.traceId.value
    )
    val correlated =
      ctx.observability.correlationId
        .map(value =>
          Record.dataAuto(
            SimpleEntityStorageShapePolicy.targetName("correlationId") ->
              value.value
          )
        )
        .getOrElse(Record.empty)
    preserved ++ restored ++ correlated
  }

  private def _is_soft_delete_target(existing: Record): Boolean = {
    val keyset = existing.keySet
    keyset.contains("aliveness")
  }

  private def _prepare_conditional_successor[S](
    successor: EntitySuccessorIntent[S],
    owner: org.goldenport.cncf.datastore.DataStoreComponentOwner,
    boundevidence: Option[EntityBoundSuccessorEvidence],
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[
    (
      DataStoreConditionalSuccessor,
      Vector[EntityVersionedSideEffect],
      EntityId
    )
  ] =
    successor match {
      case createintent: EntitySuccessorIntent.Create[c, S] @unchecked =>
        given EntityPersistentCreate[c] = createintent.create
        val id =
          createintent.candidateId
            .getOrElse(ctx.idGeneration.entityId(createintent.collection))
        for {
          collection <- ctx.entityStoreSpace.dataStoreCollection(id)
          entry <- ctx.entityStoreSpace.dataStoreEntryId(id)
          initialized <- revisionbinding.initializeForCreate(
              _complement_create_record(
                createintent.create.toStoreRecord(createintent.candidate),
                id,
                EntityCreateOptions.default
              )
            )
          preparation <- ContentBodyStoragePolicy.planForVersionedSave(
            id,
            initialized
          )
        } yield (
          DataStoreConditionalSuccessor.Create(
            owner,
            collection,
            entry,
            revisionbinding.storageFieldName,
            _conditional_storage_record(preparation.record)
          ),
          preparation.sideEffects,
          id
        )
      case bindintent: EntitySuccessorIntent.Bind[S] @unchecked =>
        boundevidence match {
          case Some(evidence) if evidence.id == bindintent.id =>
            for {
              collection <-
                ctx.entityStoreSpace.dataStoreCollection(bindintent.id)
              entry <-
                ctx.entityStoreSpace.dataStoreEntryId(bindintent.id)
              revision <- EntityConcurrencyMetadata
                .mutationRevision(evidence.revision)
                .map(_._1)
            } yield (
              DataStoreConditionalSuccessor.Bind(
                owner,
                collection,
                entry,
                revisionbinding.storageFieldName,
                revision
              ),
              Vector.empty,
              bindintent.id
            )
          case Some(evidence) =>
            Consequence.argumentExpectedActualMismatch(
              "boundSuccessor.id",
              bindintent.id,
              evidence.id
            )
          case None =>
            Consequence.argumentMissing("boundSuccessor")
        }
    }

  private def _admit_conditional_root_changes(
    changes: Record
  ): Consequence[Record] = {
    val domainchanges =
      SimpleEntityStorageShapePolicy.withoutManagedFields(changes)
    val managedfields = changes.keySet -- domainchanges.keySet
    if (managedfields.nonEmpty)
      Consequence.argumentPolicyViolation(
        "rootPatch",
        "entity-conditional-transition.framework-managed-field",
        "domain fields only",
        managedfields.toVector.sorted.mkString(",")
      )
    else if (domainchanges.isEmpty)
      Consequence.argumentInvalid(
        "rootPatch",
        "one or more domain changes",
        "empty patch"
      )
    else
      Consequence.success(domainchanges)
  }

  private def _require_conditional_domain_change(
    current: Record,
    candidate: Record
  ): Consequence[Unit] = {
    val currentdomain =
      _conditional_storage_record(
        SimpleEntityStorageShapePolicy.withoutManagedFields(current)
      )
    val candidatedomain =
      _conditional_storage_record(
        SimpleEntityStorageShapePolicy.withoutManagedFields(candidate)
      )
    if (_conditional_record_delta(currentdomain, candidatedomain).isEmpty)
      Consequence.argumentInvalid(
        "rootPatch",
        "one or more effective domain changes",
        "no-op patch"
      )
    else
      Consequence.unit
  }

  private def _conditional_transition_result[R, P, S](
    request: EntityConditionalTransition[R, P, S],
    successorid: EntityId,
    providerresult: DataStoreConditionalTransitionResult,
    rootbinding: EntityRevisionBinding,
    successorbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] =
    providerresult match {
      case DataStoreConditionalTransitionResult.Transitioned(
            rootrecord,
            successorrecord
          ) =>
        (for {
          hydratedroot <-
            ContentBodyStoragePolicy.hydrate(request.rootId, rootrecord)
          rootvalue <- _conditional_transition_value(
            rootbinding,
            hydratedroot
          )(
            EntityPersistent._decode_store_record(
              request.rootPersistent,
              request.rootId.collection,
              _
            )
          )
          hydratedsuccessor <-
            ContentBodyStoragePolicy.hydrate(successorid, successorrecord)
          successorvalue <- _conditional_transition_value(
            successorbinding,
            hydratedsuccessor
          )(
            EntityPersistent._decode_store_record(
              request.successor.persisted,
              successorid.collection,
              _
            )
          )
        } yield EntityConditionalTransitionExecutionResult.Transitioned(
          EntityConditionalTransitionResult.Transitioned(
            rootvalue,
            successorvalue
          ),
          hydratedroot,
          hydratedsuccessor
        )).recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case DataStoreConditionalTransitionResult.NotMatched(existingroot) =>
        for {
          hydratedroot <-
            ContentBodyStoragePolicy.hydrate(request.rootId, existingroot)
          rootvalue <- _conditional_transition_value(
            rootbinding,
            hydratedroot
          )(
            EntityPersistent._decode_store_record(
              request.rootPersistent,
              request.rootId.collection,
              _
            )
          )
        } yield EntityConditionalTransitionExecutionResult.NotMatched(
          EntityConditionalTransitionResult.NotMatched(rootvalue),
          hydratedroot
        )
    }

  private def _conditional_transition_value[A](
    binding: EntityRevisionBinding,
    record: Record
  )(
    decode: Record => Consequence[A]
  ): Consequence[EntityConditionalTransitionValue[A]] =
    binding.representation match {
      case EntityRevisionRepresentation.Embedded =>
        for {
          revision <- binding.revision(record)
          entity <- binding.decodeEntity(record)(decode)
        } yield EntityConditionalTransitionValue.Embedded(entity, revision)
      case EntityRevisionRepresentation.Detached =>
        binding
          .detachedCarrier(record)(decode)
          .map(EntityConditionalTransitionValue.Detached(_))
    }

  private def _conditional_storage_side_effects(
    effects: Vector[EntityVersionedSideEffect]
  ): Vector[EntityVersionedSideEffect] =
    effects.map {
      case save: EntityVersionedSideEffect.Save =>
        save.copy(record = _conditional_storage_record(save.record))
      case delete: EntityVersionedSideEffect.Delete =>
        delete
    }

  private def _conditional_storage_record(
    record: Record
  ): Record =
    Record.dataAuto(
      record.fields.map(field =>
        field.key -> _conditional_storage_value(field.value.single)
      )*
    )

  private def _conditional_record_delta(
    current: Record,
    candidate: Record
  ): Record = {
    val currentvalues = current.asMap
    val candidatevalues = candidate.asMap
    val changed = candidate.fields.collect {
      case field
          if currentvalues.get(field.key) !=
            Some(field.value.single) =>
        field.key -> field.value.single
    }
    val removed = current.fields.collect {
      case field if !candidatevalues.contains(field.key) =>
        field.key -> Update.SetNull
    }
    Record.dataAuto((changed ++ removed)*)
  }

  private def _conditional_storage_value(
    value: Any
  ): Any =
    value match {
      case id: EntityId => id.print
      case identifier: Identifier => identifier.value
      case scalar: NominalScalar =>
        _conditional_storage_value(scalar.value)
      case aliveness: Aliveness => aliveness.dbValue
      case status: PostStatus => status.dbValue
      case record: Record => _conditional_storage_record(record)
      case values: Seq[?] =>
        values.map(_conditional_storage_value)
      case Some(content) =>
        _conditional_storage_value(content)
      case None =>
        None
      case other =>
        other
    }

  private def _is_logically_deleted_record(
    record: Record
  ): Boolean =
    EntityLifecycleRecordPolicy.isLogicallyDeleted(record)

  private def _revision_binding_option(
    collection: EntityCollectionId
  )(using
    ctx: ExecutionContext
  ): Option[EntityRevisionBinding] =
    ctx.entitySpace
      .entityOption(collection)
      .flatMap(_.descriptor.revisionBinding)

  private def _required_revision_binding(
    collection: EntityCollectionId
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionBinding] =
    _revision_binding_option(collection) match {
      case Some(binding) =>
        Consequence.success(binding)
      case None =>
        Consequence.operationInvalid(
          "entity-revision-representation",
          Vector(
            org.goldenport.observation.Descriptor.Facet.Policy(
              "entity.revision.representation"
            ),
            org.goldenport.observation.Descriptor.Facet.Expected(
              "embedded-or-detached"
            ),
            org.goldenport.observation.Descriptor.Facet.Actual("unmanaged")
          )
        )
    }

  private def _required_revision_binding(
    collection: EntityCollectionId,
    representation: EntityRevisionRepresentation
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionBinding] =
    _required_revision_binding(collection).flatMap { binding =>
        binding.requireRepresentation(representation).map(_ => binding)
    }

  private def _decode_entity[T](
    collection: EntityCollectionId,
    record: Record,
    persistent: EntityPersistent[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[T] =
    _revision_binding_option(collection) match {
      case Some(binding) =>
        binding.decodeEntity(record)(
          EntityPersistent._decode_store_record(persistent, collection, _)
        )
      case None =>
        EntityPersistent._decode_store_record(persistent, collection, record)
    }

  private def _concurrency_policy(
    collection: EntityCollectionId
  )(using
    ctx: ExecutionContext
  ): EntityConcurrencyPolicy =
    ctx.entitySpace
      .entityOption(collection)
      .map(_.descriptor.plan.concurrencyPolicy)
      .getOrElse(EntityConcurrencyPolicy.default)

  private def _admit_typed_mutation(
    binding: EntityRevisionBinding,
    record: Record,
    expectedrevision: Option[EntityRevision]
  ): Consequence[(Record, Option[EntityRevision])] =
    binding.representation match {
      case EntityRevisionRepresentation.Embedded =>
        binding.revision(record).flatMap { supplied =>
          expectedrevision match {
            case Some(expected) if supplied != expected =>
              Consequence.argumentExpectedActualMismatch(
                binding.storageFieldName,
                expected,
                supplied
              )
            case _ =>
              Consequence.success(
                binding.withoutManagedRevision(record) -> Some(supplied)
              )
          }
        }
      case EntityRevisionRepresentation.Detached =>
        binding
          .rejectManagedPatch(record, "entity")
          .map(_ -> expectedrevision)
    }

  private def _effective_expected_revision(
    binding: EntityRevisionBinding,
    persisted: Record,
    expectedrevision: Option[EntityRevision],
    policy: EntityMutationExecutionPolicy
  ): Consequence[Option[EntityRevision]] =
    if (
      policy.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic &&
      expectedrevision.isEmpty
    )
      binding.revision(persisted).map(Some(_))
    else
      Consequence.success(expectedrevision)

  private def _raw_record(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId
  )(using
    ctx: ExecutionContext
  ): Consequence[Option[Record]] =
    for {
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      record <- _with_datastore_calltree(
        "load",
        collection,
        Some(entryid)
      ) {
        datastore.load(collection, entryid)
      }
    } yield record

  private def _required_record(
    entryid: DataStore.EntryId,
    record: Option[Record]
  ): Consequence[Record] =
    record
      .map(Consequence.success)
      .getOrElse(Consequence.DataStoreNotFound(entryid.print))

  private def _mutate_versioned(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    preparation: ContentBodyStoragePolicy.VersionedPreparation,
    revisionbinding: EntityRevisionBinding,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityVersionedMutationResult] =
    for {
      policy <- executionpolicy.validateC
      plan = EntityVersionedMutationPlan(
        collection = collection,
        entryId = entryid,
        revisionField = revisionbinding.storageFieldName,
        concurrencyPolicy = policy.concurrencyPolicy,
        writePolicy = policy.writePolicy,
        preconditionPolicy = policy.preconditionPolicy,
        expectedRevision =
          policy.preconditionPolicy match {
            case RevisionPreconditionPolicy.ObservedRequired =>
              policy.observedRevision
            case RevisionPreconditionPolicy.Managed
                if policy.concurrencyPolicy ==
                  EntityConcurrencyPolicy.Optimistic =>
              expectedrevision
            case RevisionPreconditionPolicy.Managed =>
              None
          },
        rootMutation = EntityVersionedRootMutation.Replace(
          revisionbinding.withoutManagedRevision(preparation.record)
        ),
        comparisonExcludedFields = _mutation_comparison_excluded_fields,
        sideEffects = preparation.sideEffects
      )
      result <- _with_datastore_calltree(
        "entity-versioned-mutation",
        collection,
        Some(entryid)
      ) {
        ctx.dataStoreSpace.mutateVersionedEntity(plan)
      }
    } yield result

  private def _typed_snapshot[T](
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding,
    persistent: EntityPersistent[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(
            revisionbinding.snapshot(_)(
              EntityPersistent._decode_store_record(
                persistent,
                id.collection,
                _
              )
            )
          )
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(
            revisionbinding.snapshot(_)(
              EntityPersistent._decode_store_record(
                persistent,
                id.collection,
                _
              )
            )
          )
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _typed_detached_carrier[T](
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding,
    persistent: EntityPersistent[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(
            revisionbinding.detachedCarrier(_)(
              EntityPersistent._decode_store_record(
                persistent,
                id.collection,
                _
              )
            )
          )
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(
            revisionbinding.detachedCarrier(_)(
              EntityPersistent._decode_store_record(
                persistent,
                id.collection,
                _
              )
            )
          )
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _record_snapshot(
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(revisionbinding.recordSnapshot)
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(revisionbinding.recordSnapshot)
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _record_mutation_result(
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityStore.ManagedRecordMutationResult] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .map(authoritative =>
            EntityStore.ManagedRecordMutationResult(
              revisionbinding.withoutManagedRevision(authoritative),
              authoritative
            )
          )
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .map(authoritative =>
            EntityStore.ManagedRecordMutationResult(
              revisionbinding.withoutManagedRevision(authoritative),
              authoritative
            )
          )
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _record_detached_carrier(
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[Record]] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(revisionbinding.detachedRecordCarrier)
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap(revisionbinding.detachedRecordCarrier)
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _managed_record(
    id: EntityId,
    result: EntityVersionedMutationResult,
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[Record] =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .map { hydrated =>
            revisionbinding.representation match {
              case EntityRevisionRepresentation.Embedded =>
                hydrated
              case EntityRevisionRepresentation.Detached =>
                revisionbinding.withoutManagedRevision(hydrated)
            }
          }
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .map { hydrated =>
            revisionbinding.representation match {
              case EntityRevisionRepresentation.Embedded =>
                hydrated
              case EntityRevisionRepresentation.Detached =>
                revisionbinding.withoutManagedRevision(hydrated)
            }
          }
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _mutation_unit(
    result: EntityVersionedMutationResult
  ): Consequence[Unit] =
    result match {
      case EntityVersionedMutationResult.Applied(_) |
          EntityVersionedMutationResult.NoOp(_) =>
        Consequence.unit
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private def _stale_mutation[A](
    expectedrevision: EntityRevision,
    actualrevision: EntityRevision
  ): Consequence[A] =
    EntityConcurrencyMetadata.staleMutation(
      expectedrevision,
      actualrevision
    )

  private val _mutation_comparison_excluded_fields: Set[String] =
    Set(
      "createdAt",
      "created_at",
      "updatedAt",
      "updated_at",
      "createdBy",
      "created_by",
      "updatedBy",
      "updated_by",
      "traceId",
      "trace_id",
      "correlationId",
      "correlation_id"
    )

  private def _with_datastore_calltree[A](
    operation: String,
    cid: DataStore.CollectionId,
    entryid: Option[DataStore.EntryId] = None
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(
        s"io:datastore:$operation",
        _datastore_calltree_attributes(operation, cid, entryid) ++ Map("calltree_kind" -> "io")
      )
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[?] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
          case other =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other)
            )
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _datastore_calltree_attributes(
    operation: String,
    cid: DataStore.CollectionId,
    entryid: Option[DataStore.EntryId]
  ): Map[String, String] =
    Map(
      "space" -> "datastore",
      "operation" -> operation,
      "collection" -> cid.print,
      "real_io" -> "true"
    ) ++ entryid.map(x => Map("entry_id" -> x.print)).getOrElse(Map.empty)

  private def _emit_entity_access(
    name: String,
    attributes: Record
  )(using ctx: ExecutionContext): Unit = {
    EntityAccessMetricsRegistry.shared.record(name, attributes)
    val _ = ctx.observability.emitDebug(ctx.cncfCore.scope, name, attributes)
  }

  private def _calltree_metric_attributes(
    name: String,
    attributes: Record
  ): Map[String, String] =
    (Vector("metric" -> name) ++
      attributes.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) =>
          key -> _truncate_calltree_metric_text(
            _sanitize_calltree_metric_value(key, value).toString,
            1000
          )
        }).toMap

  private def _sanitize_calltree_metric_value(
    key: String,
    value: Any
  ): Any =
    if (_is_sensitive_calltree_metric_key(key)) "***" else value

  private def _is_sensitive_calltree_metric_key(
    key: String
  ): Boolean = {
    val normalized = key.toLowerCase(java.util.Locale.ROOT)
    normalized.contains("password") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie")
  }

  private def _truncate_calltree_metric_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def _emit_visibility_filtered(
    entity: String,
    rawcount: Int,
    notdeletedcount: Int,
    visiblecount: Int
  )(using ctx: ExecutionContext): Unit = {
    val filteredcount = notdeletedcount - visiblecount
    if (filteredcount > 0) {
      val _ = _emit_entity_access(
        "entity.search.filtered.visibility",
        Record.dataAuto(
          "entity" -> entity,
          "source" -> "data-store",
          "raw-count" -> rawcount,
          "notdeleted-count" -> notdeletedcount,
          "visible-count" -> visiblecount,
          "filtered-count" -> filteredcount
        )
      )
    }
  }
}
