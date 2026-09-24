package org.goldenport.cncf.statemachine

import java.time.{Clock, Instant, ZoneOffset}
import java.nio.file.Paths
import scala.collection.mutable.ArrayBuffer
import cats.~>
import cats.free.Free
import cats.syntax.functor.*
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkOp, UnitOfWorkTermination}
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, Continuation, ContinuationIdentity, ContinuationRuntimePersistence, EvidenceContract, PersistentContinuationRuntime, StateMachineOperationFailure, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
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
      val result = ExecutionPlanExecutor.executeOutcome(plan, "state", "event", _interpreter(trace), observer)

      Then("the exact suspension remains visible and stops later actions without lifecycle completion")
      result shouldBe Consequence.success(Some(_suspended))
      trace.toVector shouldBe Vector("exit", "transition-1", "transition-suspended")
      observer.beforeCount shouldBe 1
      observer.afterCount shouldBe 0
      observer.failures shouldBe empty
    }

    "reject a suspended outcome when the caller has no durable continuation boundary" in {
      Given("a legacy Unit-returning execution call with an action that suspends")
      val trace = ArrayBuffer.empty[String]
      val observer = new Observer
      val plan = ExecutionPlan[String, String](
        exitActions = Vector.empty,
        transitionActions = Vector(_action("transition-suspended", _suspended)),
        entryActions = Vector(_action("entry"))
      )

      When("the execution plan is interpreted through the Unit-returning call")
      val result = ExecutionPlanExecutor.execute(plan, "state", "event", _interpreter(trace), observer)

      Then("the operation fails closed rather than discarding a continuation as success")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("durable continuation persistence")
      trace.toVector shouldBe Vector("transition-suspended")
      observer.beforeCount shouldBe 1
      observer.afterCount shouldBe 0
      observer.failures should have size 1
    }

    "return a suspended continuation only after the loose persistence step succeeds" in {
      Given("a pure typed suspension and a persistent continuation runtime")
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val unitOfWork = _uow("suspension-success")
      val observer = new Observer
      val plan = ExecutionPlan[String, String](
        Vector.empty, Vector(_pure_action(_suspended)), Vector(_pure_action(_failed))
      )

      When("the plan runs and its active UnitOfWork commits")
      val result = ExecutionPlanExecutor.executeCommittingC(plan, "state", "event", unitOfWork, runtime, observer)

      Then("the caller receives the continuation after persistence, without executing later actions")
      result shouldBe Consequence.success(Some(_suspended.asInstanceOf[ActionExecution.Suspended].continuation))
      unitOfWork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      runtime.claimC(ContinuationIdentity("continuation-1")).isSuccess shouldBe true
      observer.afterCount shouldBe 0
      observer.failures shouldBe empty
    }

    "report a failed post-commit suspension write without returning work" in {
      Given("a continuation store whose creation fails")
      val backing = new ContinuationRuntimePersistence.InMemory
      val rejecting = new ContinuationRuntimePersistence {
        def createC(record: ContinuationRuntimePersistence.Record): Consequence[Unit] =
          Consequence.Failure(Conclusion.from(new IllegalStateException("planned continuation write failure")))
        def loadC(identity: ContinuationIdentity): Consequence[Option[ContinuationRuntimePersistence.Record]] = backing.loadC(identity)
        def claimC(identity: ContinuationIdentity): Consequence[ContinuationRuntimePersistence.Record] = backing.claimC(identity)
        def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.releaseC(identity, claimId)
        def completeC(identity: ContinuationIdentity, claimId: String): Consequence[ContinuationRuntimePersistence.Record] =
          backing.completeC(identity, claimId)
      }
      val runtime = new PersistentContinuationRuntime(rejecting)
      val unitOfWork = _uow("suspension-write-failure")
      val observer = new Observer
      val plan = ExecutionPlan[String, String](Vector.empty, Vector(_pure_action(_suspended)), Vector.empty)

      When("the active UnitOfWork commits but the post-commit write fails")
      val result = ExecutionPlanExecutor.executeCommittingC(plan, "state", "event", unitOfWork, runtime, observer)

      Then("the committed UnitOfWork is reported incomplete and no work is returned")
      result.isFaillure shouldBe true
      result.display should include ("planned continuation write failure")
      unitOfWork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(ContinuationIdentity("continuation-1")).toOption.flatten shouldBe None
      observer.afterCount shouldBe 0
      observer.failures should have size 1
    }

    "run a post-commit prerequisite before publishing a suspension" in {
      Given("a suspended plan and a prerequisite that rejects WorkflowInstance persistence")
      val backing = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(backing)
      val unitOfWork = _uow("suspension-prerequisite-failure")
      val observer = new Observer
      val plan = ExecutionPlan[String, String](Vector.empty, Vector(_pure_action(_suspended)), Vector.empty)
      var inspected = 0

      When("the active UnitOfWork commits and the prerequisite fails")
      val result = ExecutionPlanExecutor.executeCommittingAfterC(
        plan, "state", "event", unitOfWork, runtime,
        continuation => {
          continuation shouldBe _suspended.asInstanceOf[ActionExecution.Suspended].continuation
          backing.loadC(continuation.continuationId) shouldBe Consequence.success(None)
          inspected += 1
          Consequence.stateConflict("planned WorkflowInstance suspension write failure")
        }, observer
      )

      Then("no Continuation becomes claimable and the committed-but-incomplete result is reported")
      result.isFaillure shouldBe true
      result.display should include ("planned WorkflowInstance suspension write failure")
      inspected shouldBe 1
      unitOfWork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      backing.loadC(ContinuationIdentity("continuation-1")) shouldBe Consequence.success(None)
      observer.afterCount shouldBe 0
      observer.failures should have size 1
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

  private def _pure_action(outcome: ActionExecution): ResolvedAction[String, String] =
    new ResolvedAction[String, String] {
      def program(state: String, event: String): ExecUowM[ActionExecution] =
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](outcome)
    }

  private def _uow(name: String): UnitOfWork = {
    val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)
    given ExecutionContext = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("test", "plansuspension"), clock, name)
    )
    new UnitOfWork(summon[ExecutionContext], EventEngine.noop(DataStore.noop()))
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
