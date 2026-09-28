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
private[entity] trait StandardEntityCreatePart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

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

  private[cncf] override def upsertVersioned[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions,
    policy: EntityUpsertPolicy
  )(
    authorize: Option[Record] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    policy.validateC.flatMap(value => _upsert_versioned(entity, id, options, value, authorize))

  private def _upsert_versioned[T](
    entity: T,
    id: EntityId,
    options: EntityCreateOptions,
    policy: EntityUpsertPolicy,
    authorize: Option[Record] => Consequence[Unit]
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _with_upsert_lock(id) {
      for {
        binding <- _required_revision_binding(id.collection)
        cid <- ctx.entityStoreSpace.dataStoreCollection(id)
        dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
        ds <- ctx.dataStoreSpace.dataStore(cid)
        existing <- _with_datastore_calltree("load", cid, Some(dsid)) {
          ds.load(cid, dsid)
        }
        _ <- authorize(existing)
        _ <- _reject_logically_deleted_existing(id, existing)
        source = tc.toStoreRecord(entity)
        admittedsource <- binding.rejectManagedPatch(source, "entity")
        result <- existing match {
          case Some(current) =>
            for {
              expected <- binding.revision(current)
              candidate <- _merge_versioned_update_record(
                current,
                _complement_update_record(
                  SimpleEntityStorageShapePolicy.withoutManagedFields(admittedsource),
                  id
                ),
                binding
              )
              preparation <- ContentBodyStoragePolicy.planForVersionedSave(
                id,
                binding.withoutManagedRevision(candidate),
                preserveExistingOverflowOnMissingContent = true
              )
              mutation <- _mutate_versioned(
                cid,
                dsid,
                preparation,
                binding,
                Some(expected),
                EntityMutationExecutionPolicy(
                  concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
                )
              )
              record <- mutation match {
                case EntityVersionedMutationResult.Applied(_) |
                    EntityVersionedMutationResult.NoOp(_) =>
                  _managed_record(id, mutation, binding)
                case EntityVersionedMutationResult.Stale(expectedrevision, actualrevision)
                    if policy.maxAttempts > 1 =>
                  _upsert_versioned(
                    entity,
                    id,
                    options,
                    policy.copy(maxAttempts = policy.maxAttempts - 1),
                    authorize
                  ).map(_.record.getOrElse(Record.empty))
                case EntityVersionedMutationResult.Stale(expectedrevision, actualrevision) =>
                  _stale_mutation(expectedrevision, actualrevision)
              }
            } yield CreateResult(id, Some(record))
          case None =>
            (for {
              initialized <- binding.initializeForCreate(
                _complement_create_record(admittedsource, id, options)
              )
              prepared <- ContentBodyStoragePolicy.prepareForSave(id, initialized)
              _ <- _with_datastore_calltree("create", cid, Some(dsid)) {
                ds.create(cid, dsid, prepared)
              }
            } yield CreateResult[T](id, Some(prepared))).recoverWith { conclusion =>
              if (
                conclusion.observation.taxonomy ==
                  org.goldenport.observation.Taxonomy.dataStoreDuplicate &&
                policy.maxAttempts > 1
              )
                _upsert_versioned(
                  entity,
                  id,
                  options,
                  policy.copy(maxAttempts = policy.maxAttempts - 1),
                  authorize
                )
              else
                Consequence.Failure(conclusion)
            }
        }
      } yield result
    }

}
