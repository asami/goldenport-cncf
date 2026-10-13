package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.simplemodeling.model.statemachine.Aliveness

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
private[entity] trait StandardEntityObservedDeletePart extends EntityStore { self: StandardEntityStore =>
  override def deleteObserved(
    id: EntityId,
    expectedRevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[EntityDeletionResult] =
    (for {
      _ <- EntityStore._admit_observed_delete(id, expectedRevision)
      binding <- _required_revision_binding(id.collection)
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entry <- ctx.entityStoreSpace.dataStoreEntryId(id)
      stored <- _raw_record(collection, entry)
      current <- _required_record(entry, stored)
      _ <-
        if (_is_logically_deleted_record(current) ||
            EntityLifecycleRecordPolicy.alivenessToken(current).contains("dead"))
          Consequence.DataStoreNotFound(entry.print)
        else Consequence.unit
      _ <- binding.validatePersistedRecord(current)
      actual <- binding.revision(current)
      _ <- actual.nextC
      logical = _is_soft_delete_target(current)
      mutation =
        if (logical)
          EntityDeleteRootMutation.Logical(_conditional_storage_record(_soft_delete_record(current)))
        else EntityDeleteRootMutation.Physical
      plan = EntityCompareAndDeletePlan(
        collection = collection,
        entryId = entry,
        revisionField = binding.storageFieldName,
        expectedRevision = expectedRevision,
        rootMutation = mutation,
        exclusionGuards = Vector(
          EntityMutationExclusionGuard.EqualTo("aliveness", Aliveness.Dead.dbValue),
          EntityMutationExclusionGuard.Present(SimpleEntityStorageShapePolicy.targetName("deletedAt")),
          EntityMutationExclusionGuard.Present("deletedAt")
        ),
        sideEffects = if (logical) Vector.empty else ContentBodyStoragePolicy.planForDelete(id)
      )
      deleted <- _with_datastore_calltree("entity-compare-and-delete", collection, Some(entry)) {
        ctx.dataStoreSpace.compareAndDeleteEntity(plan)
      }
      result <- deleted match {
        case EntityCompareAndDeleteResult.Deleted(matched) =>
          Consequence.success(EntityDeletionResult(id, matched))
        case EntityCompareAndDeleteResult.Stale(expected, persisted) =>
          _stale_mutation(expected, persisted)
      }
    } yield result).recoverWith(EntityConcurrencyMetadata.mutationTargetFailure)
}
