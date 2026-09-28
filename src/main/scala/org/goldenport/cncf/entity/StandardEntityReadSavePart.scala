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
private[entity] trait StandardEntityReadSavePart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

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
        tc.admitStoreRecord(_).flatMap { admitted =>
          binding.snapshot(admitted)(
            EntityPersistent._decode_admitted_store_record(tc, id.collection, _)
          )
        }
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
        tc.admitStoreRecord(_).flatMap { admitted =>
          binding.detachedCarrier(admitted)(
            EntityPersistent._decode_admitted_store_record(tc, id.collection, _)
          )
        }
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

}
