package org.goldenport.cncf.unitofwork

import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, Continuation, ContinuationIdentity, EvidenceContract, ProviderExecutionRequest, ProviderIdentity, StateMachineDeterministicProvider, StateMachineOperationFailure, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineProgramProvider, StateMachineProvider, StateMachineProviderBinding, StateMachineProviderResolver, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
import org.simplemodeling.model.datatype.EntityCollectionId
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
    "reject an unmarked direct Provider before invoking its execute method" in {
      val request = _request("required.direct.unmarked")
      var executionCalls = 0
      val provider = new StateMachineProvider {
        val identity = ProviderIdentity("provider.direct.unmarked")
        def execute(value: ProviderExecutionRequest): ActionExecution = {
          executionCalls += 1
          _completed
        }
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("must declare deterministic execution")
      executionCalls shouldBe 0
    }

    "return a typed failure when a deterministic direct Provider throws" in {
      val request = _request("required.direct.throwing")
      val provider = new StateMachineDeterministicProvider {
        val identity = ProviderIdentity("provider.direct.throwing")
        def execute(value: ProviderExecutionRequest): ActionExecution =
          throw new IllegalStateException("planned deterministic Provider failure")
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("planned deterministic Provider failure")
    }

    "interpret a program Provider in the active UnitOfWork without using its direct compatibility method" in {
      Given("a Program Provider that emits a typed System authorization before completing")
      val request = _request("required.program")
      var programcalls = 0
      val provider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe request
          programcalls += 1
          for {
            _ <- ConsequenceT.liftF(
              Free.liftF(
                UnitOfWorkOp.Authorize(
                  UnitOfWorkAuthorization(
                    "state-machine-provider",
                    accessKind = "execute",
                    accessMode = EntityAccessMode.System
                  )
                )
              )
            )
          } yield _completed
        }
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider execution operation is interpreted in the active UnitOfWork")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the typed program completes without its direct compatibility method")
      result shouldBe Consequence.success(_completed)
      programcalls shouldBe 1
      provider.execute(request) shouldBe a[ActionExecution.Failed]
    }

    "propagate a typed UnitOfWork operation failure from a program Provider" in {
      Given("a Program Provider that emits a typed DataStore load with a canonical EntityId")
      val request = _request("required.program.failure")
      val collectionid = EntityCollectionId("state_machine", "unitofwork", "program_provider")
      val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "state_machine",
        "program_failure",
        collectionid,
        entropy = "program_failure"
      )
      var programcalls = 0
      val provider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program.failure")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe request
          programcalls += 1
          for {
            _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.DataStoreLoad(entityid)))
          } yield _completed
        }
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the typed UnitOfWork operation failure propagates")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("DataStore not wired: DataStoreLoad")
      programcalls shouldBe 1
    }

    "reject a recursive program Provider before constructing its program twice" in {
      Given("a Program Provider whose program dispatches the same request")
      val request = _request("required.program.cycle")
      var programcalls = 0
      val provider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program.cycle")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe request
          programcalls += 1
          for {
            outcome <- ConsequenceT.liftF(
              Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(request))
            )
          } yield outcome
        }
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the recursive Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the invocation cycle is a typed conflict before a second program is constructed")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("invocation cycle")
      programcalls shouldBe 1
    }

    "allow a program Provider to reenter with a distinct context" in {
      Given("a Program Provider whose nested request has the same run and Required SPI but a distinct context")
      val request = _request("required.program.context")
      val nestedrequest = request.copy(
        context = request.context.copy(summary = "nested context")
      )
      var programcalls = 0
      val provider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program.context")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          programcalls += 1
          if (value == request)
            for {
              outcome <- ConsequenceT.liftF(
                Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(nestedrequest))
              )
            } yield outcome
          else {
            value shouldBe nestedrequest
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              _completed
            )
          }
        }
      }
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the outer Provider execution operation dispatches the same Provider with the nested context")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("both finite invocations succeed without a false cycle conflict")
      result shouldBe Consequence.success(_completed)
      programcalls shouldBe 2
    }

    "allow nested acyclic program Provider dispatch in the active UnitOfWork" in {
      Given("two Program Providers whose outer program dispatches the distinct bound inner Provider")
      val outerrequest = _request("required.program.outer")
      val innerrequest = _request("required.program.inner")
      var outerprogramcalls = 0
      var innerprogramcalls = 0
      val innerprovider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program.inner")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe innerrequest
          innerprogramcalls += 1
          for {
            _ <- ConsequenceT.liftF(
              Free.liftF(
                UnitOfWorkOp.Authorize(
                  UnitOfWorkAuthorization(
                    "state-machine-provider",
                    accessKind = "execute",
                    accessMode = EntityAccessMode.System
                  )
                )
              )
            )
          } yield _completed
        }
      }
      val outerprovider = new StateMachineProgramProvider {
        override val identity = ProviderIdentity("provider.program.outer")
        override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          value shouldBe outerrequest
          outerprogramcalls += 1
          for {
            outcome <- ConsequenceT.liftF(
              Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(innerrequest))
            )
          } yield outcome
        }
      }
      val component = _component(
        Vector(
          StateMachineProviderBinding(outerrequest.requiredOperation.identity, outerprovider.identity),
          StateMachineProviderBinding(innerrequest.requiredOperation.identity, innerprovider.identity)
        ),
        Vector(outerprovider, innerprovider)
      )

      When("the outer Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(outerrequest))

      Then("both distinct Providers run once and preserve the completed outcome")
      result shouldBe Consequence.success(_completed)
      outerprogramcalls shouldBe 1
      innerprogramcalls shouldBe 1
    }

    "reject a sixty-fifth unique nested program Provider invocation" in {
      Given("sixty-five uniquely bound Program Providers that each dispatch the next request")
      val requests = Vector.tabulate(65)(index => _request(s"required.program.bound.$index"))
      var programcalls = Vector.fill(65)(0)
      val providers = requests.zipWithIndex.map { case (request, index) =>
        new StateMachineProgramProvider {
          override val identity = ProviderIdentity(s"provider.program.bound.$index")
          override def program(value: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
            value shouldBe request
            programcalls = programcalls.updated(index, programcalls(index) + 1)
            if (index == 64)
              ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
                _completed
              )
            else
              for {
                outcome <- ConsequenceT.liftF(
                  Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(requests(index + 1)))
                )
              } yield outcome
          }
        }
      }
      val component = _component(
        requests.zip(providers).map { case (request, provider) =>
          StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)
        },
        providers
      )

      When("the first Provider execution operation enters the sixty-five-level chain")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(requests.head))

      Then("the sixty-fifth invocation fails with a typed nesting-bound conflict before its program is constructed")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("nesting exceeds 64 active calls")
      programcalls shouldBe (Vector.fill(64)(1) :+ 0)
    }

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

    "reject a Completed result whose type differs from the Required SPI contract" in {
      Given("a bound Provider and a Required SPI declaring a different result type")
      val base = _request("required.typed")
      val required = base.requiredOperation.copy(
        resultType = Some(StateMachineResultTypeReference("expected.result"))
      )
      val request = base.copy(requiredOperation = required)
      val provider = new ProbeProvider("provider.wrong-result", _completed)
      val component = _component(
        Vector(StateMachineProviderBinding(required.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider returns a Completed result with an incompatible type")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the UnitOfWork interpreter rejects that result")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("result type is incompatible")
      provider.executionCalls shouldBe 1
    }

    "reject a suspension that changes the run identity" in {
      Given("a bound Provider request and a continuation for another run")
      val request = _request("required.identity")
      val wrong = ActionExecution.Suspended(
        Continuation(
          StateMachineRunIdentity("another-run"),
          ContinuationIdentity("continuation-wrong-run"),
          StateMachineRevision("1"),
          request.requiredOperation,
          request.context
        )
      )
      val provider = new ProbeProvider("provider.wrong-run", wrong)
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider returns the mismatched suspension")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the UnitOfWork interpreter rejects the foreign continuation")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("continuation does not match")
      provider.executionCalls shouldBe 1
    }

    "reject a suspension that substitutes the Required SPI or context" in {
      Given("a bound request and two continuations with foreign contract fields")
      val request = _request("required.suspension-contract")
      val wrongoperation = ActionExecution.Suspended(
        Continuation(
          request.runId,
          ContinuationIdentity("continuation-wrong-operation"),
          StateMachineRevision("1"),
          request.requiredOperation.copy(identity = StateMachineRequiredOperationIdentity("required.other")),
          request.context
        )
      )
      val wrongcontext = ActionExecution.Suspended(
        Continuation(
          request.runId,
          ContinuationIdentity("continuation-wrong-context"),
          StateMachineRevision("1"),
          request.requiredOperation,
          request.context.copy(summary = "foreign context")
        )
      )
      val providers = Vector(
        new ProbeProvider("provider.wrong-operation", wrongoperation),
        new ProbeProvider("provider.wrong-context", wrongcontext)
      )

      When("each Provider returns its mismatched continuation")
      val results = providers.map { provider =>
        val component = _component(
          Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
          Vector(provider)
        )
        _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))
      }

      Then("neither foreign continuation is admitted")
      results.foreach { result =>
        result shouldBe a[Consequence.Failure[_]]
        result.display should include ("continuation does not match")
      }
      providers.map(_.executionCalls) shouldBe Vector(1, 1)
    }

    "reject an incomplete Provider execution outcome" in {
      Given("a bound Provider that returns no ActionExecution")
      val request = _request("required.no-outcome")
      val provider = new ProbeProvider("provider.no-outcome", null)
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the missing outcome is a typed failure")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("outcome is incomplete")
      provider.executionCalls shouldBe 1
    }

    "reject incomplete Completed and Failed outcomes" in {
      Given("bound Providers returning a missing result or failure payload")
      val request = _request("required.missing-payload")
      val providers = Vector(
        new ProbeProvider("provider.missing-result", ActionExecution.Completed(null)),
        new ProbeProvider("provider.missing-failure", ActionExecution.Failed(null))
      )

      When("each incomplete outcome is interpreted")
      val results = providers.map { provider =>
        val component = _component(
          Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
          Vector(provider)
        )
        _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))
      }

      Then("both outcomes fail with typed boundary errors")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
      results.head.display should include ("completed result is incomplete")
      results.last.display should include ("outcome is incomplete")
      providers.map(_.executionCalls) shouldBe Vector(1, 1)
    }

    "preserve a well-formed Failed outcome without rewriting its failure" in {
      Given("a bound Provider returning a declared failure")
      val request = _request("required.failed")
      val provider = new ProbeProvider("provider.failed", _failed)
      val component = _component(
        Vector(StateMachineProviderBinding(request.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the Provider execution operation is interpreted")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the declared failure remains the typed Provider outcome")
      result shouldBe Consequence.success(_failed)
      provider.executionCalls shouldBe 1
    }

    "reject an incomplete Provider request before invoking the Provider" in {
      Given("a bound Provider and a request without a run identity")
      val complete = _request("required.incomplete")
      val request = complete.copy(runId = null)
      val provider = new ProbeProvider("provider.incomplete", _completed)
      val component = _component(
        Vector(StateMachineProviderBinding(complete.requiredOperation.identity, provider.identity)),
        Vector(provider)
      )

      When("the incomplete request enters the UnitOfWork interpreter")
      val result = _interpreter(component).interpret(UnitOfWorkOp.StateMachineProviderExecute(request))

      Then("the request is rejected before Provider execution")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("request is incomplete")
      provider.executionCalls shouldBe 0
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
  ) extends StateMachineDeterministicProvider {
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
