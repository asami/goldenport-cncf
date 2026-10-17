package org.goldenport.cncf.unitofwork

import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.process.{ShellCommand, ShellCommandExecutor, ShellCommandResult}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkExternalEffectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _secret = "confidential-nonexecuting-shell-sentinel"

  private final class ObservedUnitOfWork(context: ExecutionContext) extends UnitOfWork(context) {
    private var _executor_accesses = 0
    private var _executor_calls = 0
    private val _fake_executor = new ShellCommandExecutor {
      override def execute(command: ShellCommand): Consequence[ShellCommandResult] = {
        _executor_calls += 1
        Consequence.stateConflict("Fake executor must not be called")
      }
    }

    override lazy val shellCommandExecutor: ShellCommandExecutor = {
      _executor_accesses += 1
      _fake_executor
    }

    def executorAccessCount: Int = _executor_accesses
    def executorCallCount: Int = _executor_calls
  }

  private def _expect_refusal(result: Consequence[ShellCommandResult]): Unit = result match {
    case Consequence.Failure(conclusion) =>
      ConclusionDiagnostics.classify(conclusion).policy shouldBe Some("external.effect.ordinary-shell")
      conclusion.toString should not include _secret
      result.toString should not include _secret
    case _ => fail("Ordinary shell execution must return its structured effect refusal")
  }

  "Ordinary UnitOfWork shell effects" should {
    "refuse direct interpretation before obtaining even a fake executor" in {
      Given("a calltree-enabled UnitOfWork with counted executor access and a nonexecuting confidential command token")
      val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), true)
      val uow = new ObservedUnitOfWork(context)
      val operation = UnitOfWorkOp.ShellCommandExec(ShellCommand(Vector(_secret), env = Map("SECRET" -> _secret)))
      When("the owning interpreter receives the direct shell intent")
      val result = new UnitOfWorkInterpreter(uow).interpret(operation)
      Then("the exact ordinary-shell policy refuses the effect without obtaining or calling the executor")
      _expect_refusal(result)
      uow.executorAccessCount shouldBe 0
      uow.executorCallCount shouldBe 0
      context.observability.callTreeContext.toString should not include _secret
    }

    "keep Free program construction passive and refuse interpretation before obtaining an executor" in {
      Given("the same counted UnitOfWork and a Free/ConsequenceT program containing only a confidential shell token")
      val context = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), true)
      val uow = new ObservedUnitOfWork(context)
      val operation = UnitOfWorkOp.ShellCommandExec(ShellCommand(Vector(_secret)))
      val program = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, ShellCommandResult](operation))
      When("the owning interpreter runs the constructed Free program")
      val result = new UnitOfWorkInterpreter(uow).run(program)
      Then("the returned failure carries the exact shell policy and neither executor access nor execution occurred")
      _expect_refusal(result)
      uow.executorAccessCount shouldBe 0
      uow.executorCallCount shouldBe 0
      context.observability.callTreeContext.toString should not include _secret
    }
  }
}
