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
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
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
  private[cncf] def upsertVersioned[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions,
    policy: EntityUpsertPolicy
  )(
    authorize: Option[Record] => Consequence[Unit]
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
