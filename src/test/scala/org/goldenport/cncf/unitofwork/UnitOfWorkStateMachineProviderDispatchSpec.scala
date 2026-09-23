package org.goldenport.cncf.unitofwork

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, Continuation, ContinuationIdentity, EvidenceContract, ProviderExecutionRequest, ProviderIdentity, StateMachineOperationFailure, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineProvider, StateMachineProviderBinding, StateMachineProviderResolver, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkStateMachineProviderDispatchSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "UnitOfWork StateMachine Provider dispatch" should {
    "select and invoke only the bound Provider once while preserving a Completed outcome" in {
      Given("a Component resolver with distinct selected and unselected Providers")
      val request = _request("required.completed")
      val selected = new ProbeProvider("provider.selected", _completed)
      val unselected = new ProbeProvider("provider.unselected", _failed)
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, selected.identity)),
        Vector(selected, unselected)
      )

      When("the Provider execution operation is interpreted inside that Component context")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the exact bound Provider runs once and its Completed outcome is unchanged")
      result shouldBe Consequence.success(_completed)
      selected.executionCalls shouldBe 1
      selected.requests shouldBe Vector(request)
      unselected.executionCalls shouldBe 0
    }

    "preserve a Suspended outcome as the direct typed Provider result" in {
      Given("a Component resolver whose bound Provider returns a structured suspension")
      val request = _request("required.suspended")
      val suspended = ActionExecution.Suspended(
        Continuation(
          request.runId,
          ContinuationIdentity("continuation-1"),
          StateMachineRevision("1"),
          request.requiredOperation,
          request.context
        )
      )
      val provider = new ProbeProvider("provider.suspended", suspended)
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the suspension remains unchanged without continuation persistence or resume")
      result shouldBe Consequence.success(suspended)
      provider.executionCalls shouldBe 1
      provider.requests shouldBe Vector(request)
    }

    "fail before invocation when the Required-SPI identity is unbound" in {
      Given("a Component resolver with a Provider bound to a different Required-SPI identity")
      val request = _request("required.unbound")
      val provider = new ProbeProvider("provider.bound", _completed)
      val component = _component(
        Vector(StateMachineProviderBinding(StateMachineRequiredOperationIdentity("required.bound"), provider.identity)),
        Vector(provider)
      )

      When("the unbound Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("resolution fails as a typed Consequence before any Provider invocation")
      result shouldBe a[Consequence.Failure[_]]
      provider.executionCalls shouldBe 0
      provider.requests shouldBe empty
    }
  }

  private def _component(
    bindings: Vector[StateMachineProviderBinding],
    providers: Vector[StateMachineProvider]
  ): Component = {
    val resolver = StateMachineProviderResolver.create(bindings, providers).toOption.getOrElse(
      fail("test Provider bindings should be admitted")
    )
    val component = new Component() {}
    component.withStateMachineProviderResolver(resolver)
  }

  private def _interpreter(component: Component): UnitOfWorkInterpreter = {
    val root = ExecutionContext.create()
    val context = root.withScope(
      Component.Context("state-machine-provider-dispatch", root.scope, component, ComponentOrigin.Embed)
    )
    new UnitOfWorkInterpreter(new UnitOfWork(context))
  }

  private def _request(capability: String): ProviderExecutionRequest =
    ProviderExecutionRequest(
      StateMachineRunIdentity("run-dispatch"),
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

  private val _failed: ActionExecution =
    ActionExecution.Failed(
      StateMachineOperationFailure("test.failure", "not selected", Vector.empty)
    )

  private final class ProbeProvider(
    value: String,
    outcome: ActionExecution
  ) extends StateMachineProvider {
    private var _execution_calls = 0
    private var _requests = Vector.empty[ProviderExecutionRequest]

    def executionCalls: Int = _execution_calls
    def requests: Vector[ProviderExecutionRequest] = _requests

    override val identity: ProviderIdentity = ProviderIdentity(value)

    override def execute(request: ProviderExecutionRequest): ActionExecution = {
      _execution_calls += 1
      _requests = _requests :+ request
      outcome
    }
  }
}
