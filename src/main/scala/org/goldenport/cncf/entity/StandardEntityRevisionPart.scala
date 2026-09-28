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
private[entity] trait StandardEntityRevisionPart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

  private[entity] def _is_logically_deleted_record(
    record: Record
  ): Boolean =
    EntityLifecycleRecordPolicy.isLogicallyDeleted(record)

  private[entity] def _revision_binding_option(
    collection: EntityCollectionId
  )(using
    ctx: ExecutionContext
  ): Option[EntityRevisionBinding] =
    ctx.entitySpace
      .entityOption(collection)
      .flatMap(_.descriptor.revisionBinding)

  private[entity] def _required_revision_binding(
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

  private[entity] def _required_revision_binding(
    collection: EntityCollectionId,
    representation: EntityRevisionRepresentation
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionBinding] =
    _required_revision_binding(collection).flatMap { binding =>
        binding.requireRepresentation(representation).map(_ => binding)
    }

  private[entity] def _decode_entity[T](
    collection: EntityCollectionId,
    record: Record,
    persistent: EntityPersistent[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[T] =
    _revision_binding_option(collection) match {
      case Some(binding) =>
        persistent.admitStoreRecord(record).flatMap { admitted =>
          binding.decodeEntity(admitted)(
            EntityPersistent._decode_admitted_store_record(persistent, collection, _)
          )
        }
      case None =>
        EntityPersistent._decode_store_record(persistent, collection, record)
    }

  private[entity] def _concurrency_policy(
    collection: EntityCollectionId
  )(using
    ctx: ExecutionContext
  ): EntityConcurrencyPolicy =
    ctx.entitySpace
      .entityOption(collection)
      .map(_.descriptor.plan.concurrencyPolicy)
      .getOrElse(EntityConcurrencyPolicy.default)

  private[entity] def _admit_typed_mutation(
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

  private[entity] def _effective_expected_revision(
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

  private[entity] def _raw_record(
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

  private[entity] def _required_record(
    entryid: DataStore.EntryId,
    record: Option[Record]
  ): Consequence[Record] =
    record
      .map(Consequence.success)
      .getOrElse(Consequence.DataStoreNotFound(entryid.print))

  private[entity] def _mutate_versioned(
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

  private[entity] def _typed_snapshot[T](
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
          .flatMap { hydrated =>
            persistent.admitStoreRecord(hydrated).flatMap { admitted =>
              revisionbinding.snapshot(admitted)(
                EntityPersistent._decode_admitted_store_record(
                  persistent,
                  id.collection,
                  _
                )
              )
            }
          }
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap { hydrated =>
            persistent.admitStoreRecord(hydrated).flatMap { admitted =>
              revisionbinding.snapshot(admitted)(
                EntityPersistent._decode_admitted_store_record(
                  persistent,
                  id.collection,
                  _
                )
              )
            }
          }
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private[entity] def _typed_detached_carrier[T](
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
          .flatMap { hydrated =>
            persistent.admitStoreRecord(hydrated).flatMap { admitted =>
              revisionbinding.detachedCarrier(admitted)(
                EntityPersistent._decode_admitted_store_record(
                  persistent,
                  id.collection,
                  _
                )
              )
            }
          }
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case EntityVersionedMutationResult.NoOp(record) =>
        ContentBodyStoragePolicy
          .hydrate(id, record)
          .flatMap { hydrated =>
            persistent.admitStoreRecord(hydrated).flatMap { admitted =>
              revisionbinding.detachedCarrier(admitted)(
                EntityPersistent._decode_admitted_store_record(
                  persistent,
                  id.collection,
                  _
                )
              )
            }
          }
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private[entity] def _record_snapshot(
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

  private[entity] def _record_mutation_result(
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

  private[entity] def _record_detached_carrier(
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

  private[entity] def _managed_record(
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

  private[entity] def _mutation_unit(
    result: EntityVersionedMutationResult
  ): Consequence[Unit] =
    result match {
      case EntityVersionedMutationResult.Applied(_) |
          EntityVersionedMutationResult.NoOp(_) =>
        Consequence.unit
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        _stale_mutation(expected, actual)
    }

  private[entity] def _stale_mutation[A](
    expectedrevision: EntityRevision,
    actualrevision: EntityRevision
  ): Consequence[A] =
    EntityConcurrencyMetadata.staleMutation(
      expectedrevision,
      actualrevision
    )

}
