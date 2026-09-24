package org.goldenport.cncf.statemachine

import cats.~>
import cats.syntax.flatMap.*
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, Continuation, ContinuationRuntime, StateMachineOperationFailure}

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
  /**
   * Runs a workflow plan through the active UnitOfWork and publishes a typed
   * suspension only after its loose post-commit persistence step succeeds.
   * This does not make workflow progression and continuation creation atomic.
   */
  def executeCommittingC[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    unitOfWork: UnitOfWork,
    continuationRuntime: ContinuationRuntime,
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Option[Continuation]] =
    _execute_committing_c(plan, state, event, unitOfWork, continuationRuntime, None, observer)

  /** An ordered post-commit prerequisite runs before a suspended Continuation is claimable.
    * Its failure leaves the UnitOfWork committed and returns no Continuation.
    */
  def executeCommittingAfterC[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    unitOfWork: UnitOfWork,
    continuationRuntime: ContinuationRuntime,
    beforePublish: Continuation => Consequence[Unit],
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Option[Continuation]] =
    if (beforePublish == null)
      Consequence.stateConflict("StateMachine suspension prerequisite is missing")
    else _execute_committing_c(
      plan, state, event, unitOfWork, continuationRuntime, Some(beforePublish), observer
    )

  private def _execute_committing_c[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    unitOfWork: UnitOfWork,
    continuationRuntime: ContinuationRuntime,
    beforePublish: Option[Continuation => Consequence[Unit]],
    observer: TransitionLifecycleObserver[S, E]
  ): Consequence[Option[Continuation]] = {
    if (unitOfWork == null || continuationRuntime == null)
      Consequence.stateConflict("StateMachine suspension requires UnitOfWork and ContinuationRuntime")
    else {
      val uowinterpreter = new UnitOfWorkInterpreter(unitOfWork)
      val interpreter = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          uowinterpreter.interpret(operation)
      }
      val commitobserver = new TransitionLifecycleObserver[S, E] {
        def before(p: ExecutionPlan[S, E], s: S, e: E): Unit = observer.before(p, s, e)
        def after(p: ExecutionPlan[S, E], s: S, e: E): Unit = ()
        def failed(p: ExecutionPlan[S, E], s: S, e: E, failure: Conclusion): Unit =
          observer.failed(p, s, e, failure)
      }
      executeOutcome(plan, state, event, interpreter, commitobserver) match {
        case Consequence.Success(outcome) =>
          val staged = outcome match {
            case Some(suspended) => beforePublish match {
              case Some(prerequisite) => continuationRuntime.stageSuspensionAfterC(
                unitOfWork, suspended.continuation, () => prerequisite(suspended.continuation)
              )
              case None => continuationRuntime.stageSuspensionC(unitOfWork, suspended.continuation)
            }
            case None => Consequence.unit
          }
          staged match {
            case Consequence.Success(_) =>
              val committed =
                try unitOfWork.commit()
                catch {
                  case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
                }
              committed match {
                case Consequence.Success(_) =>
                  if (outcome.isEmpty) observer.after(plan, state, event)
                  Consequence.success(outcome.map(_.continuation))
                case Consequence.Failure(conclusion) =>
                  observer.failed(plan, state, event, conclusion)
                  Consequence.Failure[Option[Continuation]](conclusion)
              }
            case Consequence.Failure(conclusion) =>
              val aborted = _abort_execution_failure_c[Option[Continuation]](unitOfWork, conclusion)
              observer.failed(plan, state, event, conclusion)
              aborted
          }
        case Consequence.Failure(conclusion) =>
          _abort_execution_failure_c[Option[Continuation]](unitOfWork, conclusion)
      }
    }
  }

  private def _abort_execution_failure_c[A](
    unitOfWork: UnitOfWork,
    primary: Conclusion
  ): Consequence[A] =
    unitOfWork.abort() match {
      case Consequence.Success(_) => Consequence.Failure(primary)
      case Consequence.Failure(cleanup) => Consequence.Failure(cleanup ++ primary)
    }

  def execute[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    interpreter: UnitOfWorkOp ~> Consequence,
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Unit] =
    executeOutcome(plan, state, event, interpreter, observer).flatMap {
      case None => Consequence.unit
      case Some(_) =>
        val failure = Consequence.stateConflict[Unit](
          "StateMachine suspension requires durable continuation persistence"
        )
        failure match {
          case Consequence.Failure(conclusion) => observer.failed(plan, state, event, conclusion)
          case _ => ()
        }
        failure
    }

  /** Retains the typed stop for a caller that can stage its persistence. */
  private[statemachine] def executeOutcome[S, E](
    plan: ExecutionPlan[S, E],
    state: S,
    event: E,
    interpreter: UnitOfWorkOp ~> Consequence,
    observer: TransitionLifecycleObserver[S, E] = TransitionLifecycleObserver.noop[S, E]
  ): Consequence[Option[ActionExecution.Suspended]] = {
    observer.before(plan, state, event)
    val actions =
      plan.exitActions ++ plan.transitionActions ++ plan.entryActions
    val result = _program(actions, state, event).value.foldMap(interpreter).flatMap(identity)
    result match {
      case Consequence.Success(None) =>
        observer.after(plan, state, event)
      case Consequence.Success(Some(_)) =>
        () // The caller must persist the typed stop or reject the operation.
      case Consequence.Failure(conclusion) =>
        observer.failed(plan, state, event, conclusion)
    }
    result
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
