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
 *  version Jul. 26, 2026
 *  version Aug.  5, 2026
 * @version Sep. 28, 2026
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

  /** Creates a stable-id Entity or conditionally updates it through the
    * versioned-mutation provider. It retries only duplicate create and stale
    * OCC results; it never falls back to an unversioned write.
    */
  private[cncf] def upsertVersioned[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions,
    policy: EntityUpsertPolicy
  )(
    authorize: Option[Record] => Consequence[Unit]
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
