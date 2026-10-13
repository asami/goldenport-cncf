package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.record.Record

/** Trusted provider acknowledgement, never caller authorization or readback proof.
  * Committed post-images must be captured from the transaction's prepared state,
  * not reconstructed by reading mutable storage after commit.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] sealed trait DataStoreMultiGuardAcknowledgement
private[cncf] object DataStoreMultiGuardAcknowledgement {
  final case class NotMatched(existingRoot: Record) extends DataStoreMultiGuardAcknowledgement
  final case class Committed(records: Vector[DataStoreMultiGuardRecord]) extends DataStoreMultiGuardAcknowledgement
}

private[cncf] final case class DataStoreMultiGuardRecord(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  record: Record
)

/** No instance here authorizes dispatch or confirms UnitOfWork completion. */
private[cncf] sealed trait DataStoreMultiGuardReadbackResult
private[cncf] object DataStoreMultiGuardReadbackResult {
  final case class NotMatched(existingRoot: Record) extends DataStoreMultiGuardReadbackResult
  final class Verified private[DataStoreMultiGuardReadbackResult] (
    val records: Vector[DataStoreMultiGuardRecord]
  ) extends DataStoreMultiGuardReadbackResult

  private[datastore] def verified(records: Vector[DataStoreMultiGuardRecord]): Verified =
    new Verified(records)
}

private[datastore] object DataStoreMultiGuardReadback {
  def validateTransitionC(transition: DataStoreConditionalTransitionPlan,
      records: Vector[DataStoreMultiGuardRecord]): Consequence[Unit] =
    if (records == null || records.size != 2 || records.exists(r => r == null || r.record == null) ||
        records.map(r => r.collection -> r.entryId) != Vector(transition.root.collection -> transition.root.entryId,
          transition.successor.collection -> transition.successor.entryId))
      Consequence.configurationInvalid("Workflow pair readback coverage differs")
    else for {
      _ <- _rootC(transition.root, records.head.record)
      _ <- transition.successor match {
        case update: DataStoreConditionalSuccessor.Update => _rootC(update.root, records(1).record)
        case create: DataStoreConditionalSuccessor.Create => _exactC(create.record, records(1).record)
        case bind: DataStoreConditionalSuccessor.Bind =>
          EntityVersionedMutationSupport.revision(records(1).record, bind.revisionField).flatMap { actual =>
            if (actual == bind.expectedRevision) Consequence.unit
            else Consequence.configurationInvalid("Workflow bound successor revision differs")
          }
      }
    } yield ()
  def targets(plan: DataStoreConditionalMultiGuardPlan): Vector[(DataStore.CollectionId, DataStore.EntryId)] =
    Vector(plan.transition.root.collection -> plan.transition.root.entryId,
      plan.transition.successor.collection -> plan.transition.successor.entryId) ++
      plan.additionalGuards.map(root => root.collection -> root.entryId) ++
      plan.transition.sideEffects.map(effect => effect.collection -> effect.entryId)

  /** Pure shape/post-image admission; actual storage reads occur only in the admitted seam. */
  def validateC(plan: DataStoreConditionalMultiGuardPlan, records: Vector[DataStoreMultiGuardRecord]):
      Consequence[Unit] = {
    val expected = targets(plan)
    if (records == null || records.size != expected.size || records.exists(item =>
      item == null || item.collection == null || item.entryId == null || item.record == null))
      Consequence.configurationInvalid("Multi-guard committed post-images are incomplete")
    else if (records.map(item => item.collection -> item.entryId) != expected)
      Consequence.configurationInvalid("Multi-guard committed post-image targets/order differ")
    else for {
      _ <- _rootC(plan.transition.root, records.head.record)
      _ <- plan.transition.successor match {
        case update: DataStoreConditionalSuccessor.Update => _rootC(update.root, records(1).record)
        case create: DataStoreConditionalSuccessor.Create => _exactC(create.record, records(1).record)
        case bind: DataStoreConditionalSuccessor.Bind =>
          EntityVersionedMutationSupport.revision(records(1).record, bind.revisionField).flatMap { actual =>
            if (actual == bind.expectedRevision) Consequence.unit
            else Consequence.configurationInvalid("Multi-guard bound successor revision differs")
          }
      }
      _ <- plan.additionalGuards.zip(records.drop(2)).foldLeft(Consequence.unit) {
        case (acc, (root, item)) => acc.flatMap(_ => _rootC(root, item.record))
      }
      _ <- plan.transition.sideEffects.zip(records.drop(2 + plan.additionalGuards.size))
        .foldLeft(Consequence.unit) {
          case (acc, (effect: EntityVersionedSideEffect.Create, item)) =>
            acc.flatMap(_ => _exactC(effect.record, item.record))
          case _ => Consequence.configurationInvalid("Multi-guard readback requires Create-only effects")
        }
    } yield ()
  }

  private def _rootC(root: DataStoreConditionalRoot, stored: Record): Consequence[Unit] =
    EntityVersionedMutationSupport.revision(stored, root.revisionField).flatMap { actual =>
      if (actual == root.nextRevision && _sameFields(EntityConditionalTransitionSupport.applyRootChanges(stored, root), stored))
        Consequence.unit
      else Consequence.configurationInvalid("Multi-guard guarded post-image differs from the prepared update")
    }

  private def _exactC(expected: Record, actual: Record): Consequence[Unit] =
    if (_sameFields(expected, actual)) Consequence.unit
    else Consequence.configurationInvalid("Multi-guard Create post-image differs from its canonical record")

  /** Physical column order is not a logical field change. Compare every field
    * and value, rejecting duplicates rather than collapsing them into a map.
    */
  private def _sameFields(expected: Record, actual: Record): Boolean = {
    val left = expected.fields
    val right = actual.fields
    left.map(_.key).distinct.size == left.size &&
      right.map(_.key).distinct.size == right.size &&
      left.sortBy(_.key) == right.sortBy(_.key)
  }
}
