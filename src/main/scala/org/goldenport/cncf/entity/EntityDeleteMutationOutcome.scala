package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor}
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] final class EntityDeleteMutationOutcome private[EntityDeleteMutationOutcome] (
  val deleted: EntityDeletionResult,
  val coherence: Consequence[Unit]
) {
  override def toString: String = "EntityDeleteMutationOutcome"
}

private[cncf] object EntityDeleteMutationOutcome {
  private[cncf] def _from_deleted_c(
    expectedid: EntityId,
    expectedrevision: EntityRevision,
    deleted: EntityDeletionResult
  )(cohere: () => Consequence[Unit]): Consequence[EntityDeleteMutationOutcome] =
    if (expectedid == null || expectedid.collection == null || expectedrevision == null ||
        expectedrevision.value <= 0L || deleted == null || deleted.id == null ||
        deleted.id != expectedid || deleted.matchedRevision == null ||
        deleted.matchedRevision != expectedrevision) _invalid
    else Consequence.success(new EntityDeleteMutationOutcome(deleted, EntityMutationCoherence.captureC(cohere)))

  private def _invalid[A]: Consequence.Failure[A] =
    Consequence.operationInvalid("entity-delete-mutation", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-delete-mutation-outcome-invalid")))
}
