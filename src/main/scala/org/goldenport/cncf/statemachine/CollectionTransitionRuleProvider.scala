package org.goldenport.cncf.statemachine

/*
 * Transitional component-side rule provider.
 *
 * Cozy/simplemodeling metadata binding is not wired yet, so components can
 * optionally provide transition rules directly through this interface.
 *
 * @since   Mar. 19, 2026
 * @version Jul. 16, 2026
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
  plan: ExecutionPlan[S, TransitionEvent],
  machineName: Option[String] = None,
  stateFieldName: Option[String] = None,
  fromState: Option[String] = None,
  fromStateValue: Option[Int] = None,
  toState: Option[String] = None,
  toStateValue: Option[Int] = None
) {
  def isStructural: Boolean =
    stateFieldName.isDefined && fromState.isDefined && toState.isDefined
}

trait CollectionTransitionRuleProvider {
  def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
    Vector.empty
}
