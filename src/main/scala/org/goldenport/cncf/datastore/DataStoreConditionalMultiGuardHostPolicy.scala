package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[cncf] final case class DataStoreConditionalGuardTarget(
  binding: DataStoreConditionalGuardBinding,
  entryId: DataStore.EntryId
)

/** Exact host-side comparison rules for S12. This value is not an authority
  * token: only trusted runtime assembly may later attach it to a live,
  * call-scoped provider/lease admission. In particular, callers cannot pass
  * this value to DataStoreSpace to grant themselves a cross-owner write.
  */
private[cncf] final class DataStoreConditionalMultiGuardHostPolicy private (
  val workflowOwner: DataStoreComponentOwner,
  val rootCollection: DataStore.CollectionId,
  val successorCollection: DataStore.CollectionId,
  val guardBindings: Vector[DataStoreConditionalGuardBinding],
  val sideCreateCollections: Vector[DataStore.CollectionId],
  val guardTargets: Vector[DataStoreConditionalGuardTarget]
) {
  /** Validation-only event composition admits no cursor update or side effect. */
  private[datastore] def validateRetainedC(transition: DataStoreConditionalTransitionPlan): Consequence[Unit] =
    EntityConditionalTransitionSupport.validate(transition).flatMap { admitted =>
      if (admitted.root.componentOwner != workflowOwner || admitted.successor.componentOwner != workflowOwner)
        _reject("workflow-owner-mismatch")
      else if (admitted.root.collection != rootCollection || admitted.successor.collection != successorCollection)
        _reject("workflow-collection-mismatch")
      else if (admitted.sideEffects.nonEmpty) _reject("validation-only-effects-not-admitted")
      else Consequence.unit
    }
  def validate(plan: DataStoreConditionalMultiGuardPlan): Consequence[Unit] =
    DataStoreConditionalMultiGuardPlan.validate(plan).flatMap { admitted =>
      val transition = admitted.transition
      if (transition.root.componentOwner != workflowOwner ||
          transition.successor.componentOwner != workflowOwner)
        _reject("workflow-owner-mismatch")
      else if (transition.root.collection != rootCollection ||
          transition.successor.collection != successorCollection)
        _reject("workflow-collection-mismatch")
      else if (!admitted.additionalGuards.forall { guard =>
          guardBindings.contains(DataStoreConditionalGuardBinding(
            guard.componentOwner, guard.collection))
        })
        _reject("guard-owner-or-collection-not-admitted")
      else if (!admitted.additionalGuards.forall { guard =>
          val binding = DataStoreConditionalGuardBinding(guard.componentOwner, guard.collection)
          val targets = guardTargets.filter(_.binding == binding)
          targets.isEmpty || targets.exists(_.entryId == guard.entryId)
        })
        _reject("guard-entry-not-admitted")
      else if (!transition.sideEffects.forall { effect =>
          sideCreateCollections.contains(effect.collection)
        })
        _reject("side-create-collection-not-admitted")
      else
        Consequence.unit
    }

  private def _reject(reason: String): Consequence[Unit] =
    Consequence.operationInvalid(
      "entity-conditional-multi-guard",
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Capability("datastore.entity-conditional-multi-guard")
      )
    )
}

private[cncf] object DataStoreConditionalMultiGuardHostPolicy {
  /** Build only a comparison policy, never a permission to invoke a provider. */
  def create(
    workflowOwner: DataStoreComponentOwner,
    rootCollection: DataStore.CollectionId,
    successorCollection: DataStore.CollectionId,
    guardBindings: Vector[DataStoreConditionalGuardBinding],
    sideCreateCollections: Vector[DataStore.CollectionId],
    guardTargets: Vector[DataStoreConditionalGuardTarget] = Vector.empty
  ): Consequence[DataStoreConditionalMultiGuardHostPolicy] =
    if (workflowOwner == null || rootCollection == null || successorCollection == null ||
        guardBindings == null || sideCreateCollections == null ||
        guardBindings.exists(binding =>
          binding == null || binding.componentOwner == null || binding.collection == null) ||
        sideCreateCollections.exists(_ == null) || guardTargets == null ||
        guardTargets.exists(target => target == null || target.binding == null ||
          target.entryId == null || !guardBindings.contains(target.binding)) ||
        guardTargets.distinct.size != guardTargets.size)
      Consequence.argumentInvalid(
        "hostPolicy", "non-null exact owner and collections", "missing member")
    else
      for {
        _ <- EntityConditionalTransitionSupport.validateComponentOwner(workflowOwner)
        _ <- guardBindings.foldLeft(Consequence.unit) { (result, binding) =>
          result.flatMap(_ =>
            EntityConditionalTransitionSupport
              .validateComponentOwner(binding.componentOwner).map(_ => ()))
        }
      } yield new DataStoreConditionalMultiGuardHostPolicy(
        workflowOwner, rootCollection, successorCollection,
        guardBindings, sideCreateCollections, guardTargets)
}
