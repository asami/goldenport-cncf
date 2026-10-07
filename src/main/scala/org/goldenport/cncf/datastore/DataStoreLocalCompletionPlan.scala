package org.goldenport.cncf.datastore

import org.goldenport.Consequence

/** Canonical local data intents, not a host grant or an executable closing
  * program. Zero guards is legal; no dummy EventStore cursor is introduced.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[datastore] final class DataStoreLocalCompletionPlan private (
  val transition: DataStoreConditionalTransitionPlan,
  val guards: Vector[DataStoreConditionalRoot],
  multi: Option[DataStoreConditionalMultiGuardPlan]
) {
  val targets: Vector[(DataStore.CollectionId, DataStore.EntryId)] =
    Vector(transition.root.collection -> transition.root.entryId,
      transition.successor.collection -> transition.successor.entryId) ++
      guards.map(g => g.collection -> g.entryId) ++ transition.sideEffects.map(e => e.collection -> e.entryId)

  def validatePolicyC(policy: DataStoreConditionalMultiGuardHostPolicy,
      reserved: Vector[DataStore.CollectionId]): Consequence[Unit] =
    if ((guards.map(_.collection) ++ transition.sideEffects.map(_.collection)).exists(reserved.contains))
      Consequence.configurationInvalid("Local intents cannot write reserved EventStore collections")
    else multi match {
      case Some(plan) => policy.validate(plan)
      case None => for {
        pair <- DataStoreConditionalTransitionPlan.create(transition.root, transition.successor,
          Vector.empty, transition.correlation)
        _ <- policy.validateRetainedC(pair)
        _ <- if (transition.sideEffects.forall(e => policy.sideCreateCollections.contains(e.collection))) Consequence.unit
          else Consequence.configurationInvalid("Local Create collection is not host-admitted")
      } yield ()
    }

  def validateReadbackC(records: Vector[DataStoreMultiGuardRecord]): Consequence[Unit] =
    if (records == null || records.exists(r => r == null || r.record == null) ||
        records.map(r => r.collection -> r.entryId) != targets)
      Consequence.configurationInvalid("Local completion readback coverage differs")
    else multi match {
      case Some(plan) => DataStoreMultiGuardReadback.validateC(plan, records)
      case None => for {
        _ <- DataStoreMultiGuardReadback.validateTransitionC(transition, records.take(2))
        _ <- if (transition.sideEffects.zip(records.drop(2)).forall {
          case (create: EntityVersionedSideEffect.Create, actual) =>
            actual.record.fields.map(_.key).distinct.size == actual.record.fields.size &&
              actual.record.fields.sortBy(_.key) == create.record.fields.sortBy(_.key)
          case _ => false
        }) Consequence.unit else Consequence.stateConflict("Local Create readback differs")
      } yield ()
    }
}

private[datastore] object DataStoreLocalCompletionPlan {
  def createC(pair: DataStoreConditionalTransitionPlan, guards: Vector[DataStoreConditionalRoot],
      creates: Vector[EntityVersionedSideEffect.Create]): Consequence[DataStoreLocalCompletionPlan] =
    if (pair == null || pair.sideEffects == null || pair.sideEffects.nonEmpty || guards == null || creates == null ||
        guards.exists(_ == null) || creates.exists(_ == null) ||
        guards.size >= DataStoreConditionalMultiGuardPlan.MAX_ADDITIONAL_GUARDS)
      Consequence.configurationInvalid("Local completion requires a pair, bounded guards and Create-only intents")
    else for {
      transition <- DataStoreConditionalTransitionPlan.create(pair.root, pair.successor, creates, pair.correlation)
      multi <- if (guards.isEmpty) Consequence.success(None)
        else for {
          scope <- DataStoreConditionalMultiGuardScope.create(pair.root.componentOwner,
            guards.map(g => DataStoreConditionalGuardBinding(g.componentOwner, g.collection)))
          plan <- DataStoreConditionalMultiGuardPlan.create(transition, guards, scope)
        } yield Some(plan)
    } yield new DataStoreLocalCompletionPlan(transition, guards, multi)
}
