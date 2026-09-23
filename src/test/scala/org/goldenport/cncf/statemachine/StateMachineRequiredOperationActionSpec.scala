package org.goldenport.cncf.statemachine

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, EvidenceContract, ProviderExecutionRequest, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRunIdentity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineRequiredOperationActionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "StateMachineRequiredOperationAction" should {
    "lower a Required-SPI request to exactly one Provider UnitOfWork operation" in {
      Given("a provider-neutral action with a state and event request factory")
      val request = _request("required.lowering")
      val action = new StateMachineRequiredOperationAction[String, String]((state, event) => {
        state shouldBe "state"
        event shouldBe "event"
        request
      })
      var observed = Option.empty[ProviderExecutionRequest]

      When("the action program is interpreted by a capturing UnitOfWork algebra interpreter")
      val result = action.program("state", "event").value.foldMap(
        new (UnitOfWorkOp ~> Consequence) {
          def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
            operation match {
              case UnitOfWorkOp.StateMachineProviderExecute(value) =>
                observed = Some(value)
                Consequence.success(_completed).asInstanceOf[Consequence[A]]
              case other =>
                fail(s"unexpected UnitOfWork operation: $other")
            }
        }
      )

      Then("the request is the sole lowered operation and no Provider is held or invoked by the action")
      result.toOption shouldBe Some(Consequence.success(_completed))
      observed shouldBe Some(request)
    }
  }

  private def _request(capability: String): ProviderExecutionRequest =
    ProviderExecutionRequest(
      StateMachineRunIdentity("run-action"),
      StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity(capability),
        "action.required",
        StateMachineOperationIdentity("test.service", "required"),
        None,
        None,
        StateMachineRequiredOperationMetadata(
          ContextContract("context", Vector.empty, Vector.empty),
          CompletionContract("completion", Vector.empty),
          EvidenceContract("evidence", Vector.empty),
          Vector.empty
        )
      ),
      None,
      ContextBundle("context", Vector.empty, Vector.empty, ContextSnapshot("1"))
    )

  private val _completed: ActionExecution =
    ActionExecution.Completed(
      StateMachineOperationResult(
        StateMachineResultTypeReference("test.result"),
        ContextReference("result", "1")
      )
    )
}
