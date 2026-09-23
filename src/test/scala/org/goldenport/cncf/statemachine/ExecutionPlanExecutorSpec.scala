package org.goldenport.cncf.statemachine

import java.nio.file.Paths
import scala.collection.mutable.ArrayBuffer
import cats.~>
import cats.free.Free
import cats.syntax.functor.*
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, Continuation, ContinuationIdentity, EvidenceContract, StateMachineOperationFailure, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 *  version Apr. 14, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionPlanExecutorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ExecutionPlanExecutor" should {
    "interpret completed actions in exit -> transition -> entry order" in {
      Given("an execution plan whose ordered actions construct typed UnitOfWork programs")
      val trace = ArrayBuffer.empty[String]
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(_action("exit-1"), _action("exit-2")),
        transitionActions = Vector(_action("transition-1"), _action("transition-2")),
        entryActions = Vector(_action("entry-1"))
      )
      val interpreter = _interpreter(trace)
      trace.toVector shouldBe empty

      When("the composed plan is interpreted through the supplied interpreter")
      val result = ExecutionPlanExecutor.execute(plan, "state", "event", interpreter)

      Then("only the interpreter executes every completed action in lifecycle and declared transition order")
      result shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit-1", "exit-2", "transition-1", "transition-2", "entry-1")
    }

    "continue after a completed action outcome" in {
      Given("a plan with completed exit and transition action programs")
      val trace = ArrayBuffer.empty[String]
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(_action("exit")),
        transitionActions = Vector(_action("transition")),
        entryActions = Vector(_action("entry"))
      )

      When("the supplied interpreter evaluates the composed program")
      val result = ExecutionPlanExecutor.execute(plan, "state", "event", _interpreter(trace))

      Then("each later action is reached after the preceding Completed outcome")
      result shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition", "entry")
    }

    "short circuit at a structured suspended outcome" in {
      Given("a plan whose earlier transition action returns a typed suspension")
      val trace = ArrayBuffer.empty[String]
      val observer = new Observer
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(_action("exit")),
        transitionActions = Vector(
          _action("transition-1"),
          _action("transition-suspended", _suspended),
          _action("transition-later")
        ),
        entryActions = Vector(_action("entry"))
      )

      When("the composed plan is interpreted")
      val result = ExecutionPlanExecutor.execute(plan, "state", "event", _interpreter(trace), observer)

      Then("the suspension stops later transition and entry actions without a success or failure lifecycle completion")
      result shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit", "transition-1", "transition-suspended")
      observer.beforeCount shouldBe 1
      observer.afterCount shouldBe 0
      observer.failures shouldBe empty
    }

    "short circuit a typed failed outcome into one lifecycle failure observation" in {
      Given("a plan whose earlier transition action returns a typed failure")
      val trace = ArrayBuffer.empty[String]
      val observer = new Observer
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(_action("exit")),
        transitionActions = Vector(
          _action("transition-1"),
          _action("transition-failed", _failed),
          _action("transition-later")
        ),
        entryActions = Vector(_action("entry"))
      )

      When("the composed plan is interpreted")
      val result = ExecutionPlanExecutor.execute(plan, "state", "event", _interpreter(trace), observer)

      Then("the typed failure stops later transition and entry actions and is observed exactly once")
      result shouldBe a[Consequence.Failure[_]]
      trace.toVector shouldBe Vector("exit", "transition-1", "transition-failed")
      observer.beforeCount shouldBe 1
      observer.afterCount shouldBe 0
      observer.failures should have size 1
    }
  }

  private def _action(
    label: String,
    outcome: ActionExecution = _completed
  ): ResolvedAction[String, String] =
    new ResolvedAction[String, String] {
      def program(state: String, event: String): ExecUowM[ActionExecution] = {
        val _ = (state, event)
        ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.LocalDataDir(label))).map(_ => outcome)
      }
    }

  private def _interpreter(
    trace: ArrayBuffer[String]
  ): UnitOfWorkOp ~> Consequence =
    new (UnitOfWorkOp ~> Consequence) {
      def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
        operation match {
          case UnitOfWorkOp.LocalDataDir(label) =>
            trace += label
            Consequence.success(Paths.get(label)).asInstanceOf[Consequence[A]]
          case _ =>
            throw new UnsupportedOperationException("unexpected UnitOfWork operation in execution-plan spec")
        }
    }

  private val _completed: ActionExecution =
    ActionExecution.Completed(
      StateMachineOperationResult(
        StateMachineResultTypeReference("test.result"),
        ContextReference("result", "1")
      )
    )

  private val _suspended: ActionExecution =
    ActionExecution.Suspended(
      Continuation(
        StateMachineRunIdentity("run-1"),
        ContinuationIdentity("continuation-1"),
        StateMachineRevision("1"),
        StateMachineRequiredOperation(
          StateMachineRequiredOperationIdentity("test.capability"),
          "test.action",
          StateMachineOperationIdentity("test", "operation"),
          None,
          None,
          StateMachineRequiredOperationMetadata(
            ContextContract("test.context", Vector.empty, Vector.empty),
            CompletionContract("test.completion", Vector.empty),
            EvidenceContract("test.evidence", Vector.empty),
            Vector.empty
          )
        ),
        ContextBundle("test context", Vector.empty, Vector.empty, ContextSnapshot("1"))
      )
    )

  private val _failed: ActionExecution =
    ActionExecution.Failed(StateMachineOperationFailure("transition_failed", "test failure", Vector.empty))

  private final class Observer extends TransitionLifecycleObserver[String, String] {
    private var _before = 0
    private var _after = 0
    private var _failures = Vector.empty[Conclusion]

    def beforeCount: Int = _before
    def afterCount: Int = _after
    def failures: Vector[Conclusion] = _failures

    def before(plan: ExecutionPlan[String, String], state: String, event: String): Unit = {
      val _ = (plan, state, event)
      _before += 1
    }

    def after(plan: ExecutionPlan[String, String], state: String, event: String): Unit = {
      val _ = (plan, state, event)
      _after += 1
    }

    def failed(
      plan: ExecutionPlan[String, String],
      state: String,
      event: String,
      failure: Conclusion
    ): Unit = {
      val _ = (plan, state, event)
      _failures :+= failure
    }
  }
}
