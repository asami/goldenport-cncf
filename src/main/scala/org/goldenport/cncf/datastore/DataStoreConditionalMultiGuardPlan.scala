package org.goldenport.cncf.datastore

import org.goldenport.Consequence

/** Host-admitted owner/collection binding for an additional guarded record.
  * A component label alone is not permission to enlist another component.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
final case class DataStoreConditionalGuardBinding(
  componentOwner: DataStoreComponentOwner,
  collection: DataStore.CollectionId
)

/** Package-limited scope intended for CNCF host composition, not a standalone
  * caller-authentication token. The host must separately admit the owner and
  * collection binding, and the provider must prove one physical datastore.
  */
final class DataStoreConditionalMultiGuardScope private[cncf] (
  val workflowOwner: DataStoreComponentOwner,
  val additionalBindings: Vector[DataStoreConditionalGuardBinding]
)

object DataStoreConditionalMultiGuardScope {
  private[cncf] def create(
    workflowOwner: DataStoreComponentOwner,
    additionalBindings: Vector[DataStoreConditionalGuardBinding]
  ): Consequence[DataStoreConditionalMultiGuardScope] =
    if (workflowOwner == null)
      Consequence.argumentMissing("workflowOwner")
    else if (additionalBindings == null)
      Consequence.argumentMissing("additionalBindings")
    else if (additionalBindings.exists(binding =>
      binding == null || binding.componentOwner == null || binding.collection == null
    ))
      Consequence.argumentInvalid(
        "additionalBindings",
        "non-null exact owner/collection bindings",
        "null binding or member"
      )
    else
      for {
        _ <- EntityConditionalTransitionSupport.validateComponentOwner(workflowOwner)
        _ <- additionalBindings.foldLeft(Consequence.unit) { (result, binding) =>
          result.flatMap(_ =>
            EntityConditionalTransitionSupport
              .validateComponentOwner(binding.componentOwner)
              .map(_ => ())
          )
        }
      } yield DataStoreConditionalMultiGuardScope(workflowOwner, additionalBindings)
}

/** Pure S12 admission only. No provider may infer transaction authority from
  * this value alone; the opt-in provider/domain capability is a separate step.
  */
final case class DataStoreConditionalMultiGuardPlan private (
  transition: DataStoreConditionalTransitionPlan,
  additionalGuards: Vector[DataStoreConditionalRoot],
  scope: DataStoreConditionalMultiGuardScope
)

object DataStoreConditionalMultiGuardPlan {
  val MAX_ADDITIONAL_GUARDS = 8

  def create(
    transition: DataStoreConditionalTransitionPlan,
    additionalGuards: Vector[DataStoreConditionalRoot],
    scope: DataStoreConditionalMultiGuardScope
  ): Consequence[DataStoreConditionalMultiGuardPlan] =
    for {
      admitted <- EntityConditionalTransitionSupport.validate(transition)
      _ <- _validate(admitted, additionalGuards, scope)
    } yield DataStoreConditionalMultiGuardPlan(admitted, additionalGuards, scope)

  private[datastore] def validate(
    plan: DataStoreConditionalMultiGuardPlan
  ): Consequence[DataStoreConditionalMultiGuardPlan] =
    if (plan == null)
      Consequence.argumentMissing("plan")
    else
      create(plan.transition, plan.additionalGuards, plan.scope)

  private def _validate(
    transition: DataStoreConditionalTransitionPlan,
    guards: Vector[DataStoreConditionalRoot],
    scope: DataStoreConditionalMultiGuardScope
  ): Consequence[Unit] =
    if (guards == null)
      Consequence.argumentMissing("additionalGuards")
    else if (scope == null)
      Consequence.argumentMissing("scope")
    else if (guards.isEmpty || guards.size > MAX_ADDITIONAL_GUARDS)
      Consequence.argumentInvalid(
        "additionalGuards",
        s"1..$MAX_ADDITIONAL_GUARDS guarded updates",
        guards.size.toString
      )
    else if (guards.exists(_ == null))
      Consequence.argumentInvalid(
        "additionalGuards",
        "non-null guarded updates",
        "null guard"
      )
    else if (scope.workflowOwner != transition.root.componentOwner ||
      scope.workflowOwner != transition.successor.componentOwner)
      Consequence.argumentPolicyViolation(
        "scope.workflowOwner",
        "entity-conditional-multi-guard.workflow-owner",
        transition.root.componentOwner.name,
        Option(scope.workflowOwner).map(_.name).getOrElse("null")
      )
    else if (!transition.sideEffects.forall(_.isInstanceOf[EntityVersionedSideEffect.Create]))
      Consequence.argumentPolicyViolation(
        "transition.sideEffects",
        "entity-conditional-multi-guard.create-only",
        "Create-only effects",
        "Save or Delete"
      )
    else
      guards.foldLeft(Consequence.unit) { (result, root) =>
        result.flatMap(_ => EntityConditionalTransitionSupport.validateRoot(root).map(_ => ()))
      }.flatMap { _ =>
        val expected = guards.map(root =>
          DataStoreConditionalGuardBinding(root.componentOwner, root.collection)
        )
        val targets =
          Vector(_target(transition.root.collection, transition.root.entryId),
            _target(transition.successor.collection, transition.successor.entryId)) ++
            transition.sideEffects.map(effect => _target(effect.collection, effect.entryId)) ++
            guards.map(root => _target(root.collection, root.entryId))
        if (scope.additionalBindings != expected)
          Consequence.argumentPolicyViolation(
            "scope.additionalBindings",
            "entity-conditional-multi-guard.exact-bindings",
            expected.toString,
            Option(scope.additionalBindings).map(_.toString).getOrElse("null")
          )
        else if (targets.distinct.size != targets.size)
          Consequence.argumentInvalid(
            "additionalGuards",
            "distinct root, successor, guard and side-effect targets",
            "overlapping targets"
          )
        else
          Consequence.unit
      }

  private def _target(
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId
  ): (String, String) =
    collection.print -> entryId.print
}
