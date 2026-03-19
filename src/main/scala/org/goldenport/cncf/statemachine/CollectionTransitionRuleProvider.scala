package org.goldenport.cncf.statemachine

/*
 * Transitional component-side rule provider.
 *
 * Cozy/simplemodeling metadata binding is not wired yet, so components can
 * optionally provide transition rules directly through this interface.
 *
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
enum TransitionTrigger {
  case Save, Update
}

final case class CollectionTransitionRule[S](
  collectionName: String,
  trigger: TransitionTrigger,
  eventName: String,
  priority: Int,
  declarationOrder: Int,
  guard: Option[Guard[S, TransitionEvent]],
  plan: ExecutionPlan[S, TransitionEvent]
)

trait CollectionTransitionRuleProvider {
  def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
    Vector.empty
}

