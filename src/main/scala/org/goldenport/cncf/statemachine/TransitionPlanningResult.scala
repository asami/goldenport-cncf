package org.goldenport.cncf.statemachine

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.event.TransitionLifecycleFailureOutcome

/*
 * @since   Sep. 19, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * The typed result of StateMachine planning.
 *
 * The result keeps selection facts separate from the compatibility
 * [[Consequence]] used by existing planner callers.  In particular, a
 * rejected transition carries its closed lifecycle outcome directly rather
 * than requiring a caller to infer it from a diagnostic message.
 */
sealed trait TransitionPlanningResult[S] {
  def toConsequence: Consequence[Option[ExecutionPlan[S, TransitionEvent]]]
}

object TransitionPlanningResult {
  final case class Selected[S](
    plan: ExecutionPlan[S, TransitionEvent]
  ) extends TransitionPlanningResult[S] {
    def toConsequence: Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
      Consequence.success(Some(plan))
  }

  final case class NoTransition[S]() extends TransitionPlanningResult[S] {
    def toConsequence: Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
      Consequence.success(None)
  }

  final case class Rejected[S](
    outcome: TransitionLifecycleFailureOutcome,
    conclusion: Conclusion,
    binding: Option[CmlTransitionBinding]
  ) extends TransitionPlanningResult[S] {
    def toConsequence: Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
      Consequence.Failure(conclusion)
  }

  def fromConsequence[S](
    result: Consequence[Option[ExecutionPlan[S, TransitionEvent]]],
    failureOutcome: TransitionLifecycleFailureOutcome = TransitionLifecycleFailureOutcome.NoMatch
  ): TransitionPlanningResult[S] =
    result match {
      case Consequence.Success(Some(plan)) => Selected(plan)
      case Consequence.Success(None) => NoTransition[S]()
      case Consequence.Failure(conclusion) => Rejected(failureOutcome, conclusion, None)
    }
}
