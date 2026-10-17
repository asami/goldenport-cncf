package org.goldenport.cncf.entity

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] final class EntityCreateMutationOutcome[T] private[EntityCreateMutationOutcome] (
  val created: CreateResult[T],
  val persisted: EntityRecordSnapshot,
  val coherence: Consequence[Unit]
) {
  override def toString: String = "EntityCreateMutationOutcome"
}

private[cncf] object EntityCreateMutationOutcome {
  private[cncf] def _from_created_c[T](
    expectedcollection: EntityCollectionId,
    expectedid: Option[EntityId],
    created: CreateResult[T],
    binding: EntityRevisionBinding
  )(cohere: () => Consequence[Unit]): Consequence[EntityCreateMutationOutcome[T]] = {
    val admitted = try {
      if (expectedcollection == null || expectedid == null || expectedid.exists(_ == null) ||
          created == null || created.id == null || created.id.collection != expectedcollection ||
          expectedid.exists(_ != created.id) || created.record == null ||
          binding == null || binding.representation == null) false
      else created.record match {
        case Some(record) if record != null && record.fields != null =>
          EntityVersionedMutationOutcome._identity_matches(created.id, record) &&
            record.fields.count(_.key == binding.storageFieldName) == 1 &&
            (binding.revision(record) match {
              case Consequence.Success(revision) => revision == EntityRevision.INITIAL
              case _ => false
            })
        case _ => false
      }
    } catch { case NonFatal(_) => false }
    if (!admitted) _invalid
    else {
      val persisted = EntityRecordSnapshot(created.record.get, EntityRevision.INITIAL)
      Consequence.success(new EntityCreateMutationOutcome(created, persisted, EntityMutationCoherence.captureC(cohere)))
    }
  }

  private def _invalid[A]: Consequence.Failure[A] =
    Consequence.operationInvalid("entity-create-mutation", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-create-mutation-outcome-invalid")))
}

private[entity] object EntityMutationCoherence {
  def captureC(cohere: () => Consequence[Unit]): Consequence[Unit] =
    if (cohere == null) _invalid
    else try cohere() match {
      case null | Consequence.Failure(null) => _invalid
      case result => result
    } catch { case NonFatal(_) => _invalid }

  private def _invalid: Consequence.Failure[Unit] =
    Consequence.operationInvalid("entity-mutation-coherence", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-mutation-coherence-invalid")))
}
