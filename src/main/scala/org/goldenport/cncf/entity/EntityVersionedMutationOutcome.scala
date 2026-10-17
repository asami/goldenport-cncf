package org.goldenport.cncf.entity

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedMutationResult}
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] final class EntityVersionedMutationOutcome[+A] private[EntityVersionedMutationOutcome] (
  val id: EntityId,
  val persisted: EntityRecordSnapshot,
  val applied: Boolean,
  val projection: Consequence[A]
) {
  override def toString: String = "EntityVersionedMutationOutcome"
}

private[cncf] object EntityVersionedMutationOutcome {
  private[entity] def _from_provider_c[A](
    id: EntityId,
    result: EntityVersionedMutationResult,
    binding: EntityRevisionBinding
  )(project: (Record, EntityRevision) => Consequence[A]): Consequence[EntityVersionedMutationOutcome[A]] = {
    if (id == null || result == null || binding == null || binding.representation == null || project == null)
      _persistence_invalid
    else result match {
      case EntityVersionedMutationResult.Stale(expected, actual) =>
        if (expected == null || actual == null) _persistence_invalid
        else EntityConcurrencyMetadata.staleMutation(expected, actual)
      case EntityVersionedMutationResult.Applied(record) =>
        _capture_c(id, record, binding, true)(project)
      case EntityVersionedMutationResult.NoOp(record) =>
        _capture_c(id, record, binding, false)(project)
    }
  }

  private def _capture_c[A](id: EntityId, record: Record,
    binding: EntityRevisionBinding, applied: Boolean
  )(project: (Record, EntityRevision) => Consequence[A]): Consequence[EntityVersionedMutationOutcome[A]] = {
    val admitted: Consequence[EntityRevision] = try {
      if (!_identity_matches(id, record)) _persistence_invalid
      else binding.revision(record) match {
        case Consequence.Success(revision) if revision != null => Consequence.success(revision)
        case _ => _persistence_invalid
      }
    } catch { case NonFatal(_) => _persistence_invalid }
    admitted.map { revision =>
      val persisted = EntityRecordSnapshot(record, revision)
      val projection = try {
        project(record, revision) match {
          case null => _projection_invalid[A]
          case Consequence.Success(null) => _projection_invalid[A]
          case Consequence.Failure(null) => _projection_invalid[A]
          case value => value
        }
      } catch { case NonFatal(_) => _projection_invalid[A] }
      val marked = if (applied)
        projection.recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      else projection
      new EntityVersionedMutationOutcome(id, persisted, applied, marked)
    }
  }

  // Both raw and Record projections use the selected typed identity without parsing it.
  private[entity] def _identity_matches(id: EntityId, record: Record): Boolean = {
    if (id == null || record == null || record.fields == null) false
    else {
      val fields = record.fields.filter(_.key == "id")
      fields match {
        case Vector(field) if field.value != null => field.value.single match {
          case value: String => value == DataStore.EntryId(id).print
          case value: EntityId => value == id
          case value: DataStore.EntryId => value == DataStore.EntryId(id)
          case _ => false
        }
        case _ => false
      }
    }
  }

  private def _persistence_invalid[A]: Consequence.Failure[A] =
    Consequence.operationInvalid("entity-versioned-mutation", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-mutation-outcome-invalid")))

  private[entity] def _projection_invalid[A]: Consequence.Failure[A] =
    Consequence.operationInvalid("entity-versioned-mutation", Cause.Kind.Inconsistency,
      Vector(Descriptor.Facet.Reason("entity-mutation-projection-invalid")))
}
