package org.goldenport.cncf.statemachine

import cats.~>
import cats.syntax.flatMap.*
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, StateMachineOperationFailure}

/*
 * @since   Mar. 19, 2026
 *  version Mar. 20, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait ResolvedAction[S, E] {
  def program(state: S, event: E): ExecUowM[ActionExecution]
}

final case class ExecutionPlan[S, E](
  exitActions: Vector[ResolvedAction[S, E]],
  transitionActions: Vector[ResolvedAction[S, E]],
  entryActions: Vector[ResolvedAction[S, E]],
  selectedTransitionBinding: Option[CmlTransitionBinding] = None,
  selectedTransitionTrigger: Option[TransitionTrigger] = None
)

object ExecutionPlan {
  def empty[S, E]: ExecutionPlan[S, E] =
    ExecutionPlan(Vector.empty, Vector.empty, Vector.empty)
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
    interpreter: UnitOfWorkOp ~> Consequence,
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Unit] = {
    observer.before(plan, state, event)
    val actions =
      plan.exitActions ++ plan.transitionActions ++ plan.entryActions
    val result = _program(actions, state, event).value.foldMap(interpreter).flatMap(identity)
    result match {
      case Consequence.Success(None) =>
        observer.after(plan, state, event)
      case Consequence.Success(Some(_)) =>
        () // Suspension is a successful, structured stop in this non-durable slice.
      case Consequence.Failure(conclusion) =>
        observer.failed(plan, state, event, conclusion)
    }
    result.map(_ => ())
  }

  private def _program[S, E](
    actions: Vector[ResolvedAction[S, E]],
    state: S,
    event: E
  ): ExecUowM[Option[ActionExecution.Suspended]] =
    actions.foldLeft(_pure(Option.empty[ActionExecution.Suspended])) { (program, action) =>
      program.flatMap {
        case suspended @ Some(_) =>
          _pure(suspended)
        case None =>
          action.program(state, event).flatMap {
            case _: ActionExecution.Completed =>
              _pure(None)
            case suspended: ActionExecution.Suspended =>
              _pure(Some(suspended))
            case ActionExecution.Failed(failure) =>
              _failed(_failure_conclusion(failure))
          }
      }
    }

  private def _pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  private def _failed[A](conclusion: Conclusion): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](
      Consequence.Failure(conclusion)
    )

  private def _failure_conclusion(
    failure: StateMachineOperationFailure
  ): Conclusion =
    Consequence.stateConflict(s"StateMachine action failed: ${failure.code}") match {
      case Consequence.Failure(conclusion) => conclusion
      case null => throw new IllegalStateException("state conflict must produce a failure conclusion")
    }
}
