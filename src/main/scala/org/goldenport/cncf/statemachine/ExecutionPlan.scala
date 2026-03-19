package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait ResolvedAction[S, E] {
  def run(state: S, event: E): Consequence[Unit]
}

final case class ExecutionPlan[S, E](
  exitActions: Vector[ResolvedAction[S, E]],
  transitionAction: Option[ResolvedAction[S, E]],
  entryActions: Vector[ResolvedAction[S, E]]
)

object ExecutionPlan {
  def empty[S, E]: ExecutionPlan[S, E] =
    ExecutionPlan(Vector.empty, None, Vector.empty)
}

object ExecutionPlanExecutor {
  def execute[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E
  ): Consequence[Unit] = {
    val actions =
      plan.exitActions ++ plan.transitionAction.toVector ++ plan.entryActions
    actions.foldLeft(Consequence.unit) { (z, a) =>
      z.flatMap(_ => a.run(state, event))
    }
  }
}

