package org.goldenport.cncf.component

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 22, 2026
 * @version Mar. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentLogicOperationDefinitionSemanticsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentLogic operationDefinitions semantics" should {
    "execute generic action synchronously when CML operation kind is QUERY" in {
      Given("a component operation defined as QUERY in operationDefinitions")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchPerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the job is awaited and the action response is returned")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value shouldBe "fetch-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "execute generic action as async job when CML operation kind is COMMAND" in {
      Given("a component operation defined as COMMAND in operationDefinitions")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "savePerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the command default path returns job id response")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value should not be "save-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
    }
  }

  private def _component(): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "entity",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _ActionOperation("fetchPerson", "fetch-ok"),
                _ActionOperation("savePerson", "save-ok")
              )
            )
          )
        )
      )
    )
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "fetchPerson",
            kind = "QUERY",
            inputType = "FetchPerson",
            outputType = "PersonView",
            inputValueKind = "QUERY_VALUE"
          ),
          CmlOperationDefinition(
            name = "savePerson",
            kind = "COMMAND",
            inputType = "SavePerson",
            outputType = "SavePersonResult",
            inputValueKind = "COMMAND_VALUE"
          )
        )
    }
    val core = Component.Core.create(
      name = "operation_definition_semantics_spec",
      componentid = ComponentId("operation_definition_semantics_spec"),
      instanceid = ComponentInstanceId.default(ComponentId("operation_definition_semantics_spec")),
      protocol = protocol
    )
    val subsystem = TestComponentFactory.emptySubsystem("operation_definition_semantics_spec")
    val params = ComponentInit(
      subsystem = subsystem,
      core = core,
      origin = ComponentOrigin.Builtin
    )
    component.initialize(params)
  }
}

private final case class _ActionOperation(
  opname: String,
  result: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_PlainAction(req, result))
}

private final case class _PlainAction(
  request: Request,
  value: String
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _PlainActionCall(core, value)
}

private final case class _PlainActionCall(
  core: ActionCall.Core,
  value: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar(value))
}
