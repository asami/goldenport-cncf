package org.goldenport.cncf.statemachine

import cats.~>
import cats.free.Free
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkAuthorization, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, EvidenceContract, ProviderExecutionRequest, ProviderIdentity, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineProgramProvider, StateMachineProviderBinding, StateMachineProviderResolver, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRunIdentity}
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

    "execute its Required-SPI Provider program in the active UnitOfWork" in {
      Given("a Required-SPI Action and a bound Program Provider with a typed authorization operation")
      val request = _request("required.integrated")
      var requestcalls = 0
      var programcalls = 0
      val action = new StateMachineRequiredOperationAction[String, String]((state, event) => {
        state shouldBe "state"
        event shouldBe "event"
        requestcalls += 1
        request
      })
      val provider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.integrated")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe request
          programcalls += 1
          for {
            _ <- ConsequenceT.liftF(
              Free.liftF(
                UnitOfWorkOp.Authorize(
                  UnitOfWorkAuthorization(
                    "state-machine-required-operation",
                    accessKind = "execute",
                    accessMode = EntityAccessMode.System
                  )
                )
              )
            )
          } yield _completed
        }
      }
      val resolver = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      ).toOption.getOrElse(fail("Provider binding should be admitted"))
      val component = new Component() {}
      component.withStateMachineProviderResolver(resolver)
      val root = ExecutionContext.create()
      val context = root.withScope(
        Component.Context("state-machine-required-operation", root.scope, component, ComponentOrigin.Embed)
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(context))

      When("the Action program runs through the UnitOfWork interpreter")
      val result = interpreter.run(action.program("state", "event"))

      Then("the bound Provider program is interpreted once and returns its typed completion")
      result shouldBe Consequence.success(_completed)
      requestcalls shouldBe 1
      programcalls shouldBe 1
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
