package org.goldenport.cncf.component.builtin.jobcontrol

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.job.JobStatus
import org.goldenport.cncf.job.JobBatchDefinition
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class JclJobControlComponentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "JobControlComponent JCL surface" should {
    "describe a valid jobs[] YAML into normalized record form" in {
      Given("a valid action-only JCL definition")
      val fixture = _fixture()
      val body =
        """jobs:
          |  - name: first
          |    target:
          |      action: jcl_fixture.command.ok
          |    parameters:
          |      orderId: a-1
          |    submit:
          |      persistence: ephemeral
          |      requestSummary: first-run
          |    onFailure:
          |      action: jcl_fixture.command.hook
          |      parameters:
          |        reason: fail
          |""".stripMargin

      When("job_control.job.describe_job_definition is invoked")
      val response = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", body))
      )

      Then("the normalized record preserves jobs, target, parameters, and failure hook")
      val record = _record(response)
      val jobs = _records(record.asMap("jobs"))
      jobs.size shouldBe 1
      jobs.head.getString("name") shouldBe Some("first")
      jobs.head.getRecord("target").flatMap(_.getString("action")) shouldBe Some("jcl_fixture.command.ok")
      jobs.head.getRecord("submit").flatMap(_.getString("persistence")) shouldBe Some("Ephemeral")
      jobs.head.getRecord("on-failure").flatMap(_.getString("action")) shouldBe Some("jcl_fixture.command.hook")
    }

    "reject invalid workflow-like or malformed YAML shapes" in {
      Given("invalid JCL payloads")
      val fixture = _fixture()
      val missingJobs =
        """job:
          |  name: invalid
          |""".stripMargin
      val workflowTarget =
        """jobs:
          |  - name: invalid
          |    target:
          |      workflow: wf.entry
          |""".stripMargin
      val branchShape =
        """jobs:
          |  - name: invalid
          |    target:
          |      action: jcl_fixture.command.ok
          |      branch: x
          |""".stripMargin

      When("describe is invoked with unsupported payloads")
      val r1 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", missingJobs)
      val r2 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", workflowTarget)
      val r3 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", branchShape)

      Then("the payloads fail deterministically")
      r1 match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"expected failure but got $other")
      }
      r2 match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"expected failure but got $other")
      }
      r3 match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"expected failure but got $other")
      }
    }

    "submit a single action job and a fail-fast batch with failure hook" in {
      Given("a fixture component with ok/fail/hook actions")
      val fixture = _fixture()
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      val single =
        """jobs:
          |  - name: single-ok
          |    target:
          |      action: jcl_fixture.command.ok
          |    parameters:
          |      orderId: one
          |""".stripMargin
      val batch =
        """jobs:
          |  - name: ok
          |    target:
          |      action: jcl_fixture.command.ok
          |    parameters:
          |      orderId: a
          |  - name: fail
          |    target:
          |      action: jcl_fixture.command.fail
          |    parameters:
          |      orderId: b
          |    onFailure:
          |      action: jcl_fixture.command.hook
          |      parameters:
          |        reason: after-fail
          |  - name: skipped
          |    target:
          |      action: jcl_fixture.command.ok
          |    parameters:
          |      orderId: c
          |""".stripMargin
      JobBatchDefinition.parseYaml(batch).toOption.map(_.jobs.size) shouldBe Some(3)

      When("submit_job_definition and submit_job_batch are invoked")
      val singleResponse = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", single))
      )
      val batchResponse = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_batch",
        arguments = List(Argument("body", batch))
      )

      Then("single submission returns one visible job id")
      val singleRecord = _record(singleResponse)
      val singleIds = _strings(singleRecord, "submitted-job-ids")
      singleIds.size shouldBe 1
      fixture.subsystem.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(singleIds.head).toOption.get).toOption.flatten.map(_.jobId.value) shouldBe Some(singleIds.head)

      And("batch submission is sequential fail-fast and runs the failure hook")
      val batchRecord = _record(batchResponse)
      batchRecord.getBoolean("success") shouldBe Some(false)
      _strings(batchRecord, "submitted-job-ids").size shouldBe 2
      batchRecord.getInt("stopped-at-index") shouldBe Some(1)
      batchRecord.getString("stopped-at-name") shouldBe Some("fail")
      batchRecord.getString("failure-hook-job-id").exists(_.nonEmpty) shouldBe true

      And("the third job is not executed")
      val trace = fixture.trace.toVector
      trace.takeRight(3) shouldBe Vector(
        "ok:orderId=a",
        "fail:orderId=b",
        "hook:reason=after-fail"
      )
    }
  }

  private final case class _Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    trace: ArrayBuffer[String]
  )

  private def _fixture(): _Fixture = {
    val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
    val trace = ArrayBuffer.empty[String]
    val component = _component(subsystem, trace)
    val bootstrapped = new ComponentFactory().bootstrap(component)
    subsystem.add(bootstrapped)
    _Fixture(subsystem, bootstrapped, trace)
  }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    trace: ArrayBuffer[String]
  ): Component = {
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector("ok", "fail", "hook").map { name =>
          CmlOperationDefinition(
            name = name,
            kind = "COMMAND",
            inputType = s"${name}Input",
            outputType = s"${name}Result",
            inputValueKind = "COMMAND_VALUE"
          )
        }
    }
    val protocol = org.goldenport.protocol.Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "command",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _Operation("ok", trace, fail = false),
                _Operation("fail", trace, fail = true),
                _Operation("hook", trace, fail = false)
              )
            )
          )
        )
      )
    )
    val componentId = ComponentId("jcl_fixture")
    val instanceId = ComponentInstanceId.default(componentId)
    val core = Component.Core.create("jcl_fixture", componentId, instanceId, protocol)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _execute(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    arguments: List[Argument]
  ): OperationResponse =
    _executeResult(subsystem, selector, arguments) match {
      case Consequence.Success(response) => response
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _executeResult(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    body: String
  ): Consequence[OperationResponse] =
    _executeResult(subsystem, selector, List(Argument("body", body)))

  private def _executeResult(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    arguments: List[Argument]
  ): Consequence[OperationResponse] = {
    val component = _component_for(subsystem, selector)
    val request = _build_request(subsystem.resolver, selector, arguments)
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }
  }

  private def _component_for(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String
  ): Component =
    subsystem.resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, _, _) =>
        subsystem.components.find(_.name == component).getOrElse(fail(s"component not found: $component"))
      case other =>
        fail(s"resolver failed for $selector: $other")
    }

  private def _build_request(
    resolver: OperationResolver,
    selector: String,
    arguments: List[Argument]
  ): Request =
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(
          component = component,
          service = service,
          operation = operation,
          arguments = arguments
        )
      case other =>
        fail(s"resolver failed for $selector: $other")
    }

  private def _record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected RecordResponse but got $other")
    }

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Seq[?] => xs.collect { case rec: Record => rec }.toVector
      case other => fail(s"expected record list but got $other")
    }

  private def _strings(record: Record, key: String): Vector[String] =
    record.getAny(key).collect { case xs: Seq[?] => xs.map(_.toString).toVector }.getOrElse(Vector.empty)

  private val _seed = new AtomicInteger(0)
}

private final case class _Operation(
  opname: String,
  trace: ArrayBuffer[String],
  fail: Boolean
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[org.goldenport.protocol.operation.OperationRequest] =
    Consequence.success(_Action(req, opname, trace, fail))
}

private final case class _Action(
  request: Request,
  opname: String,
  trace: ArrayBuffer[String],
  fail: Boolean
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _ActionCall(core, opname, trace, fail)
}

private final case class _ActionCall(
  core: ActionCall.Core,
  opname: String,
  trace: ArrayBuffer[String],
  fail: Boolean
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    val params = core.action.request.arguments.map(x => s"${x.name}=${x.value}").mkString(",")
    trace += s"$opname:$params"
    if (fail)
      Consequence.argumentInvalid(s"jcl fixture failure: $opname")
    else
      Consequence.success(OperationResponse.Scalar(opname))
  }
}
