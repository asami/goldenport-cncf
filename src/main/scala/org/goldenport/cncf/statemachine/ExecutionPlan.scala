package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 * @version Mar. 20, 2026
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

trait TransitionLifecycleObserver[S, E] {
  def before(plan: ExecutionPlan[S, E], state: S, event: E): Unit
  def after(plan: ExecutionPlan[S, E], state: S, event: E): Unit
  def failed(plan: ExecutionPlan[S, E], state: S, event: E, failure: org.goldenport.Conclusion): Unit
}

object TransitionLifecycleObserver {
  def noop[S, E]: TransitionLifecycleObserver[S, E] =
    new TransitionLifecycleObserver[S, E] {
      def before(plan: ExecutionPlan[S, E], state: S, event: E): Unit = {
        val _ = (plan, state, event)
      }
      def after(plan: ExecutionPlan[S, E], state: S, event: E): Unit = {
        val _ = (plan, state, event)
      }
      def failed(
        plan: ExecutionPlan[S, E],
        state: S,
        event: E,
        failure: org.goldenport.Conclusion
      ): Unit = {
        val _ = (plan, state, event, failure)
      }
    }
}

object ExecutionPlanExecutor {
  def execute[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Unit] = {
    observer.before(plan, state, event)
    val actions =
      plan.exitActions ++ plan.transitionAction.toVector ++ plan.entryActions
    val result = actions.foldLeft(Consequence.unit) { (z, a) =>
      z.flatMap(_ => a.run(state, event))
    }
    result match {
      case Consequence.Success(_) =>
        observer.after(plan, state, event)
      case Consequence.Failure(conclusion) =>
        observer.failed(plan, state, event, conclusion)
    }
    result
  }
}
