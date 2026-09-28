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
private[entity] trait StandardEntityUpdatePart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

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

}
