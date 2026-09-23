package org.goldenport.cncf.statemachine

import cats.~>
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, ContextReference, StateMachineOperationResult, StateMachineResultTypeReference}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class EffectAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "StateMachine action program construction" should {
    "construct one typed program without the legacy effect adapter" in {
      Given("a state-machine rule action that returns a completed program")
      val action = StateMachineRuleBuilder.action[String] { (_, _) =>
        _completed_program
      }
      val plan = StateMachineRuleBuilder.plan[String](transitionActions = Vector(action))
      val interpreter = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("the pure action program must not invoke an operation")
      }

      When("the plan is interpreted through its explicit UnitOfWork interpreter")
      val result = ExecutionPlanExecutor.execute(plan, "s1", TransitionEvent("update", None), interpreter)

      Then("the completed action succeeds without a direct Effect execution route")
      result shouldBe Consequence.unit
    }
  }

  private def _completed_program: ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](
      ActionExecution.Completed(
        StateMachineOperationResult(
          StateMachineResultTypeReference("test.result"),
          ContextReference("result", "1")
        )
      )
    )
}
