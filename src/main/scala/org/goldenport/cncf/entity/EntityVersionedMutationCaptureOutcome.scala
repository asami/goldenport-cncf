package org.goldenport.cncf.entity

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor}
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] final class EntityVersionedMutationCaptureOutcome[+A] private[EntityVersionedMutationCaptureOutcome] (
  val mutation: EntityVersionedMutationOutcome[A],
  val coherence: Consequence[Unit]
) {
  override def toString: String = "EntityVersionedMutationCaptureOutcome"
}

private[cncf] object EntityVersionedMutationCaptureOutcome {
  private[cncf] def _from_outcome_c[A](
    expectedid: EntityId,
    outcome: EntityVersionedMutationOutcome[A]
  )(cohere: () => Consequence[Unit]): Consequence[EntityVersionedMutationCaptureOutcome[A]] = {
    val admitted = try {
      expectedid != null && expectedid.collection != null && outcome != null &&
        outcome.id != null && outcome.id.collection != null && outcome.id == expectedid &&
        outcome.id.collection == expectedid.collection && outcome.persisted != null &&
        outcome.persisted.record != null && outcome.persisted.record.fields != null &&
        outcome.persisted.revision != null && outcome.projection != null &&
        (outcome.projection match {
          case Consequence.Success(null) | Consequence.Failure(null) => false
          case _ => true
        })
    } catch { case NonFatal(_) => false }
    if (!admitted) _invalid
    else Consequence.success(new EntityVersionedMutationCaptureOutcome(outcome, EntityMutationCoherence.captureC(cohere)))
  }

  private def _invalid[A]: Consequence.Failure[A] =
    Consequence.operationInvalid("entity-versioned-mutation-capture", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-versioned-mutation-capture-invalid")))
}
