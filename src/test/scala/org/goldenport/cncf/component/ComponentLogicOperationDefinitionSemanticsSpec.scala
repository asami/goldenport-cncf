package org.goldenport.cncf.component

import cats.data.NonEmptyVector
import cats.free.Free
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.record.Record
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.action.{Action, ActionCall, CommandExecutionPolicy, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 22, 2026
 *  version Apr. 25, 2026
 *  version May. 11, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentLogicOperationDefinitionSemanticsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:action-execution-semantics, example:$example, rules:$rules")

  "ComponentLogic operationDefinitions semantics" should {
    "execute generic action synchronously when CML operation kind is QUERY" must _metadata("E3", "R2,R4,R11") {
      "when a generic QUERY action executes directly" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a component operation defined as QUERY in operationDefinitions")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchPerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the query is executed directly and the action response is returned")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value shouldBe "fetch-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "execute query operation and return record payload for generated VO smoke" must _metadata("E3", "R2,R4,R11") {
      "when a QUERY operation returns an address-like record" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a component operation defined as QUERY that returns an address-like record")
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
    }

    "keep normal query jobs ephemeral but retain debug query jobs as persistent" must _metadata("E3", "R2,R4,R11") {
      "when a QUERY executes normally or with explicit trace-job" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a component operation defined as QUERY")
      val component = _component()
      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchPerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed")).asInstanceOf[Action]

      When("the query is executed normally")
      val normal = component.logic.executeAction(action, ExecutionContext.create())

      Then("the normal result is returned and no persistent job is listed")
      normal.toOption.map(_.print) shouldBe Some("fetch-ok")
      component.jobEngine.listJobs().map(_.debug.executionNotes).flatten should not contain ("debug trace query")

      When("the same query is executed with debug trace-job enabled")
      val debugctx = ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.create(), enabled = true)
      val debug = component.logic.executeAction(action, debugctx)

      Then("the result shape is unchanged but a persistent debug job is retained")
      debug.toOption.map(_.print) shouldBe Some("fetch-ok")
      val jobs = component.jobEngine.listJobs()
      jobs.exists(_.debug.executionNotes.contains("debug trace query")) shouldBe true
      jobs.head.persistence shouldBe org.goldenport.cncf.job.JobPersistencePolicy.Persistent
    }
      }

    "retain job-specific calltree for debug query when calltree is enabled" must _metadata("E3", "R2,R4,R11") {
      "when an explicit trace-job QUERY executes with calltree enabled" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a debug trace-job query execution context with calltree save enabled")
      val component = _component()
      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchPerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed")).asInstanceOf[Action]
      val debugctx = ExecutionContext.withFrameworkSaveCallTreeEnabled(
        ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.create(), enabled = true),
        enabled = true
      )

      When("the query is executed")
      val result = component.logic.executeAction(action, debugctx)

      Then("the result is returned and the retained job exposes a calltree")
      result.toOption.map(_.print) shouldBe Some("fetch-ok")
      val job = component.jobEngine.listJobs().headOption.getOrElse(fail("debug job missing"))
      job.calltree should not be empty
      val calltree = job.calltree.map(_.show).getOrElse("")
      calltree should include ("action:org.goldenport.cncf.test.OperationDefinitionSemanticsSpec.entity.fetchPerson")
      calltree should include ("kind=action")
      calltree should include ("operation=fetchPerson")
      calltree should include ("response_type")
      calltree should include ("kind=scalar")
      calltree should include ("inline=false")
      calltree should not include ("fetch-ok")
      job.debug.calltreeSaved shouldBe true
      job.debug.calltreeStorage shouldBe Some("calltree")
      job.debug.calltreeSerializedBytes.exists(_ > 0) shouldBe true
      job.debug.calltreeDropReason shouldBe None
    }
      }

    "include UoW spans in inline calltree when debug calltree is enabled" must _metadata("E3", "R2,R4,R11") {
      "when a QUERY action executes a UoW with inline calltree enabled" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a functional query action that executes a UoW operation")
      val component = _component()
      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchWithUow"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed")).asInstanceOf[Action]
      val debugctx = ExecutionContext.withFrameworkInlineCallTreeEnabled(ExecutionContext.create(), enabled = true)

      When("the action is executed")
      val result = component.logic.executeAction(action, debugctx)

      Then("the inline calltree contains the action and its nested UoW span")
      result.toOption.map(_.print) shouldBe Some("uow-ok")
      val calltree = debugctx.runtime.executionMetadata.inlineCallTree.map(_.show).getOrElse("")
      calltree should include ("action:org.goldenport.cncf.test.OperationDefinitionSemanticsSpec.entity.fetchWithUow")
      calltree should include ("uow:http:get")
    }
      }

    "save job-specific calltree for failed persistent debug query" must _metadata("E3", "R2,R4,R11") {
      "when a trace-job QUERY fails without explicit save-calltree" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a debug trace-job query execution context without explicit save-calltree")
      val component = _component()
      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "fetchFailure"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed")).asInstanceOf[Action]
      val debugctx = ExecutionContext.withFrameworkTraceJobEnabled(ExecutionContext.create(), enabled = true)

      When("the query fails under persistent debug job execution")
      val result = component.logic.executeAction(action, debugctx)

      Then("the failed job is retained and the calltree is saved by error policy")
      result match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"unexpected result: $other")
      }
      val job = component.jobEngine.listJobs().headOption.getOrElse(fail("debug job missing"))
      job.status shouldBe org.goldenport.cncf.job.JobStatus.Failed
      job.debug.calltreeSaved shouldBe true
      job.debug.calltreeStorage shouldBe Some("calltree")
      job.calltree should not be empty
      val calltree = job.calltree.map(_.show).getOrElse("")
      calltree should include ("io:error")
      calltree should include ("outcome=failure")
      calltree should include ("diagnostic_key=argument")
      calltree should include ("taxonomy_category=operation")
      calltree should include ("taxonomy_symptom=invalid")
      calltree should not include ("debug trace query failure")
      calltree should include ("kind=action")
    }
      }

    "validate request values while building a generated VO before action execution" must _metadata("E3", "R2,R4,R11") {
      "when a QUERY builds a validated address-like value object" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R4, R11; Example: E3; a query operation that builds an address-like value object from request properties")
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
    }

    "execute generic action synchronously when CML operation kind is COMMAND without async metadata" must _metadata("E4", "R2,R5,R6") {
      "when a generic COMMAND action executes without async metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R5, R6; Example: E4; a component operation defined as COMMAND in operationDefinitions")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "savePerson"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the command default path returns the operation response directly")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value shouldBe "save-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "execute generic command as async job when legacy execution metadata is async" must _metadata("E4", "R2,R5,R6") {
      "when a generic COMMAND uses legacy async execution metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R5, R6; Example: E4; a component operation defined as COMMAND with execution=async")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "savePersonAsync"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the legacy async path returns a job id response")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value should not be "save-async-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "prefer typed command execution policy over legacy execution metadata" must _metadata("E4", "R2,R5,R6") {
      "when a COMMAND has typed sync policy and legacy async metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R5, R6; Example: E4; a command operation with execution=async but typed sync direct policy")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "savePersonTypedPolicy"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the typed policy wins and the operation returns directly")
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value shouldBe "save-typed-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "ignore invalid typed command execution policy instead of overriding legacy execution metadata" must _metadata("E4", "R2,R5,R6") {
      "when a COMMAND has invalid typed policy and valid legacy async metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R5, R6; Example: E4; a command operation with execution=async and an invalid typed policy string")
      val component = _component()

      val req = Request.of(
        component = component.name,
        service = "entity",
        operation = "savePersonInvalidTypedPolicy"
      )
      val action = component.logic.makeOperationRequest(req).toOption.getOrElse(fail("action creation failed"))

      When("the action is executed")
      val result = component.logic.executeAction(action.asInstanceOf[Action], ExecutionContext.create())

      Then("the invalid typed policy does not collapse the command into direct sync execution")
      CommandExecutionPolicy.parse("managedByJob=treu") shouldBe None
      result match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value should not be "save-invalid-policy-ok"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "validate request values while building a generated VO before command execution" must _metadata("E4", "R2,R5,R6") {
      "when a COMMAND builds a validated address-like value object" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R2, R5, R6; Example: E4; a command operation that builds an address-like value object from request properties")
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

      Then("the request values are validated during action creation and the command returns the operation response directly")
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("addressCountry") shouldBe Some("JP")
          record.getString("postalCode") shouldBe Some("160-0022")
        case other =>
          fail(s"unexpected result: $other")
      }
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
                SemanticsActionOperation("fetchPerson", "fetch-ok"),
                SemanticsFunctionalUowOperation("fetchWithUow"),
                SemanticsFailingOperation("fetchFailure"),
                SemanticsRecordOperation("fetchAddress"),
                SemanticsValidatedRecordOperation("fetchAddressValidated"),
                SemanticsActionOperation("savePerson", "save-ok"),
                SemanticsActionOperation("savePersonAsync", "save-async-ok"),
                SemanticsActionOperation("savePersonTypedPolicy", "save-typed-ok"),
                SemanticsActionOperation("savePersonInvalidTypedPolicy", "save-invalid-policy-ok"),
                SemanticsValidatedCommandOperation("saveAddressValidated")
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
            name = "fetchWithUow",
            kind = "QUERY",
            inputType = "FetchWithUow",
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
            name = "fetchFailure",
            kind = "QUERY",
            inputType = "FetchFailure",
            outputType = "FailureView",
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
            name = "savePersonAsync",
            kind = "COMMAND",
            execution = Some("async"),
            inputType = "SavePersonAsync",
            outputType = "SavePersonResult",
            inputValueKind = "COMMAND_VALUE"
          ),
          CmlOperationDefinition(
            name = "savePersonTypedPolicy",
            kind = "COMMAND",
            execution = Some("async"),
            commandExecutionPolicy = Some(CommandExecutionPolicy.default),
            inputType = "SavePersonTypedPolicy",
            outputType = "SavePersonResult",
            inputValueKind = "COMMAND_VALUE"
          ),
          CmlOperationDefinition(
            name = "savePersonInvalidTypedPolicy",
            kind = "COMMAND",
            execution = Some("async"),
            commandExecutionPolicy = CommandExecutionPolicy.parse("managedByJob=treu"),
            inputType = "SavePersonInvalidTypedPolicy",
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
      name = "org.goldenport.cncf.test.OperationDefinitionSemanticsSpec",
      componentid = ComponentId("org.goldenport.cncf.test.OperationDefinitionSemanticsSpec"),
      instanceid = ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.OperationDefinitionSemanticsSpec")),
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

private final case class SemanticsActionOperation(
  opname: String,
  result: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(SemanticsPlainAction(req, result))
}

private final case class SemanticsPlainAction(
  request: Request,
  value: String
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsPlainActionCall(core, value)
}

private final case class SemanticsPlainActionCall(
  core: ActionCall.Core,
  value: String
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar(value))
}

private final case class SemanticsFunctionalUowOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(SemanticsFunctionalUowAction(req))
}

private final case class SemanticsFunctionalUowAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsFunctionalUowActionCall(core)
}

private final case class SemanticsFunctionalUowActionCall(
  core: ActionCall.Core
) extends org.goldenport.cncf.action.FunctionalActionCall {
  protected def build_Program: ExecUowM[OperationResponse] =
    for {
      _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.HttpGet("/debug/uow")))
    } yield OperationResponse.Scalar("uow-ok")
}

private final case class SemanticsFailingOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(SemanticsFailingAction(req))
}

private final case class SemanticsFailingAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsFailingActionCall(core)
}

private final case class SemanticsFailingActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.operationInvalid("debug trace query failure")
}

private final case class SemanticsRecordOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(SemanticsRecordAction(req))
}

private final case class SemanticsRecordAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsRecordActionCall(core)
}

private final case class SemanticsRecordActionCall(
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

private final case class SemanticsValidatedRecordOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    if (req.properties.exists(p => p.name == "addressCountry" && p.value.toString == "JP"))
      Consequence.success(SemanticsValidatedRecordAction(req))
    else
      Consequence.argumentInvalid("addressCountry must be JP")
}

private final case class SemanticsValidatedRecordAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsValidatedRecordActionCall(core)
}

private final case class SemanticsValidatedRecordActionCall(
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

private final case class SemanticsValidatedCommandOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    if (req.properties.exists(p => p.name == "addressCountry" && p.value.toString == "JP"))
      Consequence.success(SemanticsValidatedCommandAction(req))
    else
      Consequence.argumentInvalid("addressCountry must be JP")
}

private final case class SemanticsValidatedCommandAction(
  request: Request
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    SemanticsValidatedCommandActionCall(core)
}

private final case class SemanticsValidatedCommandActionCall(
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
