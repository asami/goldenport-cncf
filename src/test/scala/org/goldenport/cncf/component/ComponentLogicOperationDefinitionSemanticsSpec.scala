package org.goldenport.cncf.component

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.Property
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
 * @author  ASAMI, Tomoharu
 * @version Mar. 28, 2026
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

    "execute query operation and return record payload for generated VO smoke" in {
      Given("a component operation defined as QUERY that returns an address-like record")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchAddress"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the query returns a record response that can carry generated VO-shaped payload")
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("addressCountry") shouldBe Some("JP")
          record.getString("postalCode") shouldBe Some("160-0022")
          record.getString("streetAddress") shouldBe Some("1-2-3")
        case other =>
          fail(s"unexpected result: $other")
      }
    }

    "validate request values while building a generated VO before action execution" in {
      Given("a query operation that builds an address-like value object from request properties")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchAddressValidated",
        properties = List(
          Property("addressCountry", "JP", None),
          Property("postalCode", "160-0022", None),
          Property("streetAddress", "1-2-3", None)
        )
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the request values are validated during action creation and the record response is returned")
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(payload)) =>
          payload.getString("addressCountry") shouldBe Some("JP")
          payload.getString("postalCode") shouldBe Some("160-0022")
          payload.getString("streetAddress") shouldBe Some("1-2-3")
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

    "validate request values while building a generated VO before command execution" in {
      Given("a command operation that builds an address-like value object from request properties")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "saveAddressValidated",
        properties = List(
          Property("addressCountry", "JP", None),
          Property("postalCode", "160-0022", None),
          Property("streetAddress", "1-2-3", None)
        )
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the request values are validated during action creation and the command returns the default async response")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value.toString.nonEmpty shouldBe true
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
                _RecordOperation("fetchAddress"),
                _ValidatedRecordOperation("fetchAddressValidated"),
                _ActionOperation("savePerson", "save-ok"),
                _ValidatedCommandOperation("saveAddressValidated")
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
            name = "fetchAddress",
            kind = "QUERY",
            inputType = "FetchAddress",
            outputType = "AddressView",
            inputValueKind = "QUERY_VALUE"
          ),
          CmlOperationDefinition(
            name = "fetchAddressValidated",
            kind = "QUERY",
            inputType = "FetchAddressValidated",
            outputType = "AddressView",
            inputValueKind = "QUERY_VALUE"
          ),
          CmlOperationDefinition(
            name = "savePerson",
            kind = "COMMAND",
            inputType = "SavePerson",
            outputType = "SavePersonResult",
            inputValueKind = "COMMAND_VALUE"
          ),
          CmlOperationDefinition(
            name = "saveAddressValidated",
            kind = "COMMAND",
            inputType = "SaveAddressValidated",
            outputType = "AddressView",
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

private final case class _RecordOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_RecordAction(req))
}

private final case class _RecordAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _RecordActionCall(core)
}

private final case class _RecordActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(
      OperationResponse.RecordResponse(
        Record.dataAuto(
          "addressCountry" -> "JP",
          "postalCode" -> "160-0022",
          "streetAddress" -> "1-2-3"
        )
      )
    )
}

private final case class _ValidatedRecordOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    if (req.properties.exists(p => p.name == "addressCountry" && p.value.toString == "JP"))
      Consequence.success(_ValidatedRecordAction(req))
    else
      Consequence.failure("addressCountry must be JP")
}

private final case class _ValidatedRecordAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _ValidatedRecordActionCall(core)
}

private final case class _ValidatedRecordActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(
      OperationResponse.RecordResponse(
        Record.dataAuto(
          "addressCountry" -> "JP",
          "postalCode" -> "160-0022",
          "streetAddress" -> "1-2-3"
        )
      )
    )
}

private final case class _ValidatedCommandOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    if (req.properties.exists(p => p.name == "addressCountry" && p.value.toString == "JP"))
      Consequence.success(_ValidatedCommandAction(req))
    else
      Consequence.failure("addressCountry must be JP")
}

private final case class _ValidatedCommandAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _ValidatedCommandActionCall(core)
}

private final case class _ValidatedCommandActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(
      OperationResponse.RecordResponse(
        Record.dataAuto(
          "addressCountry" -> "JP",
          "postalCode" -> "160-0022",
          "streetAddress" -> "1-2-3"
        )
      )
    )
}
