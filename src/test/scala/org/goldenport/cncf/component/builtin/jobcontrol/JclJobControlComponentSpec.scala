package org.goldenport.cncf.component.builtin.jobcontrol

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import cats.effect.Ref
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy}
import org.goldenport.cncf.job.{JobEngineTestFixture, JobStatus}
import org.goldenport.cncf.job.JobBatchDefinition
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.workflow.{WorkflowDefinition, WorkflowPriority, WorkflowRegistration, WorkflowStatusRule}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Apr. 22, 2026
 *  version May.  7, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class JclJobControlComponentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _collection_id = EntityCollectionId("jcl", "sales", "salesOrder")

  "JobControlComponent JCL surface" should {
    "describe a valid jobs[] YAML into normalized record form" in {
      Given("a valid action-only JCL definition")
      _with_fixture() { fixture =>
      val body =
        """jobs:
          |  - name: first
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |    parameters:
          |      orderId: a-1
          |    submit:
          |      persistence: ephemeral
          |      requestSummary: first-run
	          |    onFailure:
	          |      action: org.goldenport.cncf.test.JclFixture.command.hook
	          |      parameters:
	          |        reason: fail
	          |    compensation:
	          |      action: org.goldenport.cncf.test.JclFixture.command.compensate
	          |      parameters:
	          |        step: first
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
      jobs.head.getRecord("target").flatMap(_.getString("action")) shouldBe Some("org.goldenport.cncf.test.JclFixture.command.ok")
      jobs.head.getRecord("submit").flatMap(_.getString("persistence")) shouldBe Some("Ephemeral")
      jobs.head.getRecord("on-failure").flatMap(_.getString("action")) shouldBe Some("org.goldenport.cncf.test.JclFixture.command.hook")
      jobs.head.getRecord("compensation").flatMap(_.getString("action")) shouldBe Some("org.goldenport.cncf.test.JclFixture.command.compensate")
      }
    }

    "describe JCL from non-YAML structured formats" in {
      Given("valid JSON, XML, and HOCON JCL definitions")
      _with_fixture() { fixture =>
      val json =
        """{
          |  "jobs": [
          |    {
          |      "name": "json-first",
          |      "target": {
          |        "action": "org.goldenport.cncf.test.JclFixture.command.ok"
          |      },
          |      "parameters": {
          |        "orderId": "json-1"
          |      }
          |    }
          |  ]
          |}""".stripMargin
      val xml =
        """<root>
          |  <job>
          |    <name>xml-first</name>
          |    <target>
          |      <action>org.goldenport.cncf.test.JclFixture.command.ok</action>
          |    </target>
          |    <parameters>
          |      <orderId>xml-1</orderId>
          |    </parameters>
          |  </job>
          |</root>""".stripMargin
      val hocon =
        """jobs = [
          |  {
          |    name = "hocon-first"
          |    target {
          |      action = "org.goldenport.cncf.test.JclFixture.command.ok"
          |    }
          |    parameters {
          |      orderId = "hocon-1"
          |    }
          |  }
          |]
          |""".stripMargin

      When("describe_job_definition is invoked with explicit JCL formats")
      val jsonresponse = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", json), Argument("jclFormat", "json"))
      )
      val xmlresponse = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", xml), Argument("jclFormat", "xml"))
      )
      val hoconresponse = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", hocon), Argument("jclFormat", "hocon"))
      )

      Then("both inputs are normalized to the same JCL record surface")
      _records(_record(jsonresponse).asMap("jobs")).head.getString("name") shouldBe Some("json-first")
      _record(xmlresponse).getRecord("job").flatMap(_.getString("name")) shouldBe Some("xml-first")
      _records(_record(hoconresponse).asMap("jobs")).head.getString("name") shouldBe Some("hocon-first")
      }
    }

    "store and submit a JSON JobDefinition without reparsing it as YAML" in {
      Given("a JSON JCL job definition")
      _with_fixture() { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val json =
        """{
          |  "job": {
          |    "name": "stored-json",
          |    "target": {
          |      "action": "org.goldenport.cncf.test.JclFixture.command.ok"
          |    },
          |    "parameters": {
          |      "orderId": "stored-json-1"
          |    }
          |  }
          |}""".stripMargin

      When("the definition is created with jclFormat=json and submitted by reference")
      val created = _execute(
        fixture.subsystem,
        "job_control.job.create_job_definition",
        arguments = List(
          Argument("key", "stored-json"),
          Argument("status", "active"),
          Argument("jclFormat", "json"),
          Argument("body", json)
        )
      )
      val submitted = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", "jobDefinitionRef: stored-json"))
      )

      Then("the stored format is retained and the referenced definition runs")
      _record(created).getString("jclFormat") shouldBe Some("json")
      _strings(_record(submitted), "submitted-job-ids").size shouldBe 1
      fixture.trace.toVector.lastOption shouldBe Some("ok:orderId=stored-json-1")
      }
    }

    "describe canonical job YAML with event/action profile" in {
      Given("a canonical single-job JCL definition")
      _with_fixture() { fixture =>
      val body =
        """job:
          |  name: profile-job
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  profile:
          |    expectedStatus: succeeded
          |    eventChain:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |        emits:
          |          - event: order.accepted
          |            occurrence: possible
          |            receivers:
          |              - action: org.goldenport.cncf.test.JclFixture.command.hook
          |                guard: order.hasHook
          |                occurrence: possible
          |""".stripMargin

      When("job_control.job.describe_job_definition is invoked")
      val response = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", body))
      )

      Then("the normalized record uses canonical job shape and preserves eventChain")
      val record = _record(response)
      val job = record.getRecord("job").getOrElse(fail("job record missing"))
      job.getString("name") shouldBe Some("profile-job")
      val profile = job.getRecord("profile").getOrElse(fail("profile missing"))
      profile.getString("expectedStatus") shouldBe Some("Succeeded")
      val chain = _records(profile.asMap("eventChain"))
      chain.size shouldBe 1
      chain.head.getString("action") shouldBe Some("org.goldenport.cncf.test.JclFixture.command.ok")
      val emits = _records(chain.head.asMap("emits"))
      emits.head.getString("event") shouldBe Some("order.accepted")
      _records(emits.head.asMap("receivers")).head.getString("guard") shouldBe Some("order.hasHook")
      }
    }

    "reject invalid workflow-like or malformed YAML shapes" in {
      Given("invalid JCL payloads")
      _with_fixture() { fixture =>
      val missingjobs =
        """job:
          |  name: invalid
          |""".stripMargin
      val bothroots =
        """job:
          |  name: invalid
          |jobs: []
          |""".stripMargin
      val bothtargetkinds =
        """jobs:
          |  - name: invalid
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |      workflow:
          |        definition: sales-order-approval
          |        registration: approval
          |""".stripMargin
      val workflowmissingregistration =
        """jobs:
          |  - name: invalid
          |    target:
          |      workflow:
          |        definition: sales-order-approval
          |""".stripMargin
      val branchshape =
        """jobs:
          |  - name: invalid
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |      branch: x
          |""".stripMargin
      val invalidprofile =
        """job:
          |  name: invalid
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  profile:
          |    eventChain:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |        emits:
          |          - event: order.accepted
          |            occurrence: always
          |""".stripMargin

      When("describe is invoked with unsupported payloads")
      val r1 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", missingjobs)
      val r2 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", bothtargetkinds)
      val r3 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", branchshape)
      val r4 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", workflowmissingregistration)
      val r5 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", bothroots)
      val r6 = _execute_result(fixture.subsystem, "job_control.job.describe_job_definition", invalidprofile)

      Then("the payloads fail deterministically")
      Vector(r1, r2, r3, r4, r5, r6).foreach {
        case Consequence.Failure(_) => succeed
        case other => fail(s"expected failure but got $other")
      }
      }
    }

    "manage JobDefinition entity lifecycle and submit by reference with snapshots" in {
      Given("a reusable JobDefinition with inert future JCL sections")
      _with_fixture() { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val body =
        """job:
          |  name: reusable-ok
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  parameters:
          |    orderId: reusable-1
          |  profile:
          |    expectedStatus: succeeded
          |    eventChain:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |  flow:
          |    steps:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |  onEvent:
          |    receivers: []
          |""".stripMargin

      When("the definition is created, searched, and submitted by ref")
      val created = _execute(
        fixture.subsystem,
        "job_control.job.create_job_definition",
        arguments = List(
          Argument("key", "nightly-ok"),
          Argument("status", "active"),
          Argument("body", body)
        )
      )
      val searched = _execute(
        fixture.subsystem,
        "job_control.job.search_job_definitions",
        arguments = Nil
      )
      val submitted = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", "jobDefinitionRef: nightly-ok"))
      )

      Then("the definition is a versioned active management record")
      val createdrecord = _record(created)
      createdrecord.getString("key") shouldBe Some("nightly-ok")
      createdrecord.getString("definitionStatus") shouldBe Some("active")
      createdrecord.getInt("version") shouldBe Some(1)
      createdrecord.getString("hash").exists(_.nonEmpty) shouldBe true
      createdrecord.getString("flow").exists(_.nonEmpty) shouldBe true
      createdrecord.getString("onEvent").exists(_.nonEmpty) shouldBe true
      _records(_record(searched).asMap("jobDefinitions")).map(_.getString("key")) should contain (Some("nightly-ok"))

      And("the submitted Job keeps an immutable definition snapshot")
      val jobid = _strings(_record(submitted), "submitted-job-ids").head
      val model = fixture.subsystem.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(jobid).toOption.get).toOption.flatten.getOrElse(fail("job missing"))
      model.debug.jobDefinitionSnapshot.map(_.key) shouldBe Some("nightly-ok")
      model.debug.jobDefinitionSnapshot.map(_.version) shouldBe Some(1)
      model.debug.parameters.get("jcl.jobDefinition.key") shouldBe Some("nightly-ok")
      model.debug.declaredProfile.flatMap(_.expectedStatus).map(_.toString) shouldBe Some("Succeeded")
      }
    }

    "attach operation JobDefinition snapshots during normal Command launch" in {
      Given("an operation bound to an active JobDefinition")
      _with_fixture(
        operationExecution = Map("ok" -> "sync-job"),
        operationJobDefinitionRefs = Map("ok" -> "op-bound-ok")
      ) { fixture =>
      val body =
        """job:
          |  name: operation-bound-ok
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  profile:
          |    expectedStatus: succeeded
          |""".stripMargin
      _execute(
        fixture.subsystem,
        "job_control.job.create_job_definition",
        arguments = List(
          Argument("key", "op-bound-ok"),
          Argument("status", "active"),
          Argument("body", body)
        )
      )

      When("the operation is executed through ComponentLogic")
      val component = _component_for(fixture.subsystem, "org.goldenport.cncf.test.JclFixture.command.ok")
      val request = _build_request(fixture.subsystem.resolver, "org.goldenport.cncf.test.JclFixture.command.ok", Nil)
      val action = component.logic.makeOperationRequest(request).toOption.collect {
        case action: Action => action
      }.getOrElse(fail("action missing"))
      val ctx = component.logic.executionContext()
      val response = component.logic.executeAction(action, ctx)
      given ExecutionContext = ctx

      Then("the managed Job carries the JobDefinition snapshot")
      response shouldBe Consequence.success(OperationResponse.Scalar("ok"))
      val jobid = ctx.runtime.executionMetadata.responseJobId.getOrElse(fail("response job id missing"))
      val model = component.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(jobid).toOption.get).toOption.flatten.getOrElse(fail("job missing"))
      model.debug.jobDefinitionSnapshot.map(_.key) shouldBe Some("op-bound-ok")
      model.debug.declaredProfile.flatMap(_.expectedStatus).map(_.toString) shouldBe Some("Succeeded")
      }
    }

    "apply operation JobDefinition compensation during normal Command launch" in {
      Given("an operation bound to a JobDefinition with explicit compensation")
      _with_fixture(
        operationExecution = Map("ok" -> "sync-job"),
        operationJobDefinitionRefs = Map("ok" -> "op-bound-compensation")
      ) { fixture =>
      val body =
        """job:
          |  name: operation-bound-compensation
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  compensation:
          |    action: org.goldenport.cncf.test.JclFixture.command.compensate
          |""".stripMargin
      _execute(
        fixture.subsystem,
        "job_control.job.create_job_definition",
        arguments = List(
          Argument("key", "op-bound-compensation"),
          Argument("status", "active"),
          Argument("body", body)
        )
      )

      When("the operation completes and a later same-job continuation fails")
      val component = _component_for(fixture.subsystem, "org.goldenport.cncf.test.JclFixture.command.ok")
      val request = _build_request(fixture.subsystem.resolver, "org.goldenport.cncf.test.JclFixture.command.ok", Nil)
      val action = component.logic.makeOperationRequest(request).toOption.collect {
        case action: Action => action
      }.getOrElse(fail("action missing"))
      val ctx = component.logic.executionContext()
      val response = component.logic.executeAction(action, ctx)
      val jobid = org.goldenport.cncf.job.JobId.parse(ctx.runtime.executionMetadata.responseJobId.getOrElse(fail("response job id missing"))).toOption.get
      val failed = new org.goldenport.cncf.job.JobTask {
        val actionid = org.goldenport.cncf.job.ActionId.generate()
        override def actionId: org.goldenport.cncf.job.ActionId = actionid
        override def operationName: Option[String] = Some("same-job-failure")
        def run(ctx: ExecutionContext): org.goldenport.cncf.job.TaskOutcome =
          org.goldenport.cncf.job.TaskFailed(Conclusion.simple("same-job-failure: forced"))
      }
      val _ = component.jobEngine.runTaskInJobSync(jobid, failed, ctx)
      val model = component.jobEngine.queryVisible(jobid)(using ctx).toOption.flatten.getOrElse(fail("job missing"))

      Then("the root task carries and runs the JobDefinition compensation action")
      response shouldBe Consequence.success(OperationResponse.Scalar("ok"))
      val root = model.tasks.tasks.find(_.operation.exists(_.endsWith(".ok"))).getOrElse(fail("root task missing"))
      root.compensationActionRef shouldBe Some("org.goldenport.cncf.test.JclFixture.command.compensate")
      model.tasks.tasks.exists(_.operation.exists(_.endsWith(".compensate"))) shouldBe true
      root.compensationStatus shouldBe Some("succeeded")
      fixture.trace.exists(_.startsWith("compensate:")) shouldBe true
      }
    }

    "submit canonical JCL and compare declared and observed profile diagnostics" in {
      Given("a canonical JCL profile whose required action succeeds")
      _with_fixture() { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val body =
        """job:
          |  name: compare-ok
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  parameters:
          |    orderId: profile-1
          |  profile:
          |    expectedStatus: succeeded
          |    eventChain:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |""".stripMargin

      When("the job is submitted and compared")
      val response = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", body))
      )
      val jobid = _strings(_record(response), "submitted-job-ids").head
      val comparison = _execute(
        fixture.subsystem,
        "job_control.job.compare_job_profile",
        arguments = List(Argument("id", jobid))
      )

      Then("the declared profile is attached and no error difference is reported")
      val record = _record(comparison)
      record.getString("summarySeverity") shouldBe Some("ok")
      record.getBoolean("accepted") shouldBe Some(true)
      record.getRecord("declared").flatMap(_.getString("expectedStatus")) shouldBe Some("Succeeded")
      _records(record.asMap("differences")) shouldBe empty
      }
    }

    "report profile contradictions and reconstruct canonical job JCL" in {
      Given("a canonical JCL profile whose declared status is wrong")
      _with_fixture() { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val body =
        """job:
          |  name: compare-status-mismatch
          |  target:
          |    action: org.goldenport.cncf.test.JclFixture.command.ok
          |  profile:
          |    expectedStatus: failed
          |    eventChain:
          |      - action: org.goldenport.cncf.test.JclFixture.command.ok
          |""".stripMargin

      When("the job is submitted, compared, and reconstructed")
      val response = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", body))
      )
      val jobid = _strings(_record(response), "submitted-job-ids").head
      val comparison = _execute(
        fixture.subsystem,
        "job_control.job.compare_job_profile",
        arguments = List(Argument("id", jobid))
      )
      val reconstructed = _execute(
        fixture.subsystem,
        "job_control.job.reconstruct_job_profile",
        arguments = List(Argument("id", jobid))
      )

      Then("comparison reports an error-level status mismatch")
      val differences = _records(_record(comparison).asMap("differences"))
      differences.exists(_.getString("kind").contains("status-mismatch")) shouldBe true
      _record(comparison).getString("summarySeverity") shouldBe Some("error")

      And("reconstruction emits canonical single-job shape")
      val job = _record(reconstructed).getRecord("job").getOrElse(fail("canonical job missing"))
      job.getString("name") shouldBe Some("compare-status-mismatch")
      job.getRecord("profile").flatMap(_.getString("expectedStatus")) shouldBe Some("Succeeded")
      }
    }

    "submit a single action job and a fail-fast batch with failure hook" in {
      Given("a fixture component with ok/fail/hook actions")
      _with_fixture() { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      val single =
        """jobs:
          |  - name: single-ok
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |    parameters:
          |      orderId: one
          |""".stripMargin
      val batch =
        """jobs:
          |  - name: ok
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |    parameters:
          |      orderId: a
          |  - name: fail
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.fail
          |    parameters:
          |      orderId: b
          |    onFailure:
          |      action: org.goldenport.cncf.test.JclFixture.command.hook
          |      parameters:
          |        reason: after-fail
          |  - name: skipped
          |    target:
          |      action: org.goldenport.cncf.test.JclFixture.command.ok
          |    parameters:
          |      orderId: c
          |""".stripMargin
      JobBatchDefinition.parseYaml(batch).toOption.map(_.jobs.size) shouldBe Some(3)

      When("submit_job_definition and submit_job_batch are invoked")
      val singleresponse = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", single))
      )
      val batchresponse = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_batch",
        arguments = List(Argument("body", batch))
      )

      Then("single submission returns one visible job id")
      val singlerecord = _record(singleresponse)
      val singleids = _strings(singlerecord, "submitted-job-ids")
      singleids.size shouldBe 1
      fixture.subsystem.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(singleids.head).toOption.get).toOption.flatten.map(_.jobId.value) shouldBe Some(singleids.head)

      And("batch submission is sequential fail-fast and runs the failure hook")
      val batchrecord = _record(batchresponse)
      batchrecord.getBoolean("success") shouldBe Some(false)
      _strings(batchrecord, "submitted-job-ids").size shouldBe 2
      batchrecord.getInt("stopped-at-index") shouldBe Some(1)
      batchrecord.getString("stopped-at-name") shouldBe Some("fail")
      batchrecord.getString("failure-hook-job-id").exists(_.nonEmpty) shouldBe true

      And("the third job is not executed")
      val trace = fixture.trace.toVector
      trace.takeRight(3) shouldBe Vector(
        "ok:orderId=a",
        "fail:orderId=b",
        "hook:reason=after-fail"
      )
      }
    }

    "describe and submit workflow-target JCL while preserving workflow and job surfaces" in {
      Given("a fixture component with workflow metadata and entity state")
      val entityid = _entity_id("workflow_submit")
      _with_fixture(
        definitions = Vector(
          WorkflowDefinition(
            name = "sales-order-approval",
            registrations = Vector(
              WorkflowRegistration(
                name = "approval",
                eventName = "sales-order.approved",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrder")),
                priority = WorkflowPriority(10)
              )
            )
          )
        ),
        entities = Vector(SalesOrder(entityid, "approved"))
      ) { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val body =
        s"""jobs:
           |  - name: start-approval
           |    target:
           |      workflow:
           |        definition: sales-order-approval
           |        registration: approval
           |    parameters:
           |      orderId: ${entityid.value}
           |""".stripMargin

      When("the workflow-target JCL is described and submitted")
      val described = _execute(
        fixture.subsystem,
        "job_control.job.describe_job_definition",
        arguments = List(Argument("body", body))
      )
      val response = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_definition",
        arguments = List(Argument("body", body))
      )

      Then("the workflow target is preserved in normalized output")
      val describedjob = _records(_record(described).asMap("jobs")).head
      describedjob.getRecord("target").flatMap(_.getRecord("workflow")).flatMap(_.getString("definition")) shouldBe Some("sales-order-approval")
      describedjob.getRecord("target").flatMap(_.getRecord("workflow")).flatMap(_.getString("registration")) shouldBe Some("approval")

      And("submission returns the workflow-triggered managed job id")
      val record = _record(response)
      record.getBoolean("success") shouldBe Some(true)
      val submittedids = _strings(record, "submitted-job-ids")
      submittedids.size shouldBe 1
      awaitCondition {
        fixture.trace.contains("workflow.advanceOrder")
      } shouldBe true
      fixture.trace should contain ("workflow.advanceOrder")

      val instance = fixture.subsystem.workflowEngine.instances.headOption.getOrElse(fail("workflow instance missing"))
      instance.registrationName shouldBe "approval"
      instance.relatedJobIds.head.value shouldBe submittedids.head

      val workflowinstance = _execute(
        fixture.subsystem,
        "workflow.workflow.get_workflow_instance",
        arguments = List(Argument("id", instance.id.value))
      )
      _record(workflowinstance).getAny("related-job-ids").collect { case xs: Seq[?] => xs.map(_.toString).toVector }.getOrElse(Vector.empty) should contain (submittedids.head)
      fixture.subsystem.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(submittedids.head).toOption.get).toOption.flatten.map(_.jobId.value) shouldBe Some(submittedids.head)
      }
    }

    "submit mixed action and workflow batches sequentially and fail-fast on workflow non-progression" in {
      Given("a fixture component with one progressable and one non-progressable workflow target")
      val approvedid = _entity_id("workflow_batch_ok")
      val pendingid = _entity_id("workflow_batch_pending")
      _with_fixture(
        definitions = Vector(
          WorkflowDefinition(
            name = "sales-order-approval",
            registrations = Vector(
              WorkflowRegistration(
                name = "approval",
                eventName = "sales-order.approved",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrder")),
                priority = WorkflowPriority(10)
              )
            )
          )
        ),
        entities = Vector(
          SalesOrder(approvedid, "approved"),
          SalesOrder(pendingid, "pending")
        )
      ) { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val mixed =
        s"""jobs:
           |  - name: direct
           |    target:
           |      action: org.goldenport.cncf.test.JclFixture.command.ok
           |    parameters:
           |      orderId: direct-1
           |  - name: wf-ok
           |    target:
           |      workflow:
           |        definition: sales-order-approval
           |        registration: approval
           |    parameters:
           |      orderId: ${approvedid.value}
           |  - name: wf-no-progress
           |    target:
           |      workflow:
           |        definition: sales-order-approval
           |        registration: approval
           |    parameters:
           |      orderId: ${pendingid.value}
           |    onFailure:
           |      action: org.goldenport.cncf.test.JclFixture.command.hook
           |      parameters:
           |        reason: workflow-no-progress
           |  - name: skipped
           |    target:
           |      action: org.goldenport.cncf.test.JclFixture.command.ok
           |    parameters:
           |      orderId: never
           |""".stripMargin

      When("the mixed batch is submitted")
      val response = _execute(
        fixture.subsystem,
        "job_control.job.submit_job_batch",
        arguments = List(Argument("body", mixed))
      )

      Then("action target and workflow target both contribute visible submitted job ids")
      val record = _record(response)
      record.getBoolean("success") shouldBe Some(false)
      _strings(record, "submitted-job-ids").size shouldBe 2
      record.getInt("stopped-at-index") shouldBe Some(2)
      record.getString("stopped-at-name") shouldBe Some("wf-no-progress")
      record.getString("failure-hook-job-id").exists(_.nonEmpty) shouldBe true

      And("the failure hook runs and later jobs are not executed")
      awaitCondition {
        fixture.trace.toVector == Vector(
          "ok:orderId=direct-1",
          "workflow.advanceOrder",
          "hook:reason=workflow-no-progress"
        )
      } shouldBe true
      fixture.trace.toVector shouldBe Vector(
        "ok:orderId=direct-1",
        "workflow.advanceOrder",
        "hook:reason=workflow-no-progress"
      )
      fixture.subsystem.workflowEngine.instances.size shouldBe 2
      }
    }
  }

  private final case class Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    trace: ArrayBuffer[String]
  )

  private def _with_fixture[A](
    definitions: Vector[WorkflowDefinition] = Vector.empty,
    entities: Vector[SalesOrder] = Vector.empty,
    operationExecution: Map[String, String] = Map.empty,
    operationJobDefinitionRefs: Map[String, String] = Map.empty
  )(body: Fixture => A): A =
    SubsystemTestFixture.withAdmittedSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
      given EntityPersistent[SalesOrder] = _persistent
      val trace = ArrayBuffer.empty[String]
      val component = _component(subsystem, trace, definitions, entities, operationExecution, operationJobDefinitionRefs)
      val bootstrapped = new ComponentFactory().bootstrap(component)
      subsystem.add(bootstrapped)
      body(Fixture(subsystem, bootstrapped, trace))
    }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    trace: ArrayBuffer[String],
    definitions: Vector[WorkflowDefinition],
    entities: Vector[SalesOrder],
    operationExecution: Map[String, String],
    operationJobDefinitionRefs: Map[String, String]
  ): Component = {
    given EntityPersistent[SalesOrder] = _persistent
    val component = new Component() {
      override def displayName: String = "org.goldenport.cncf.test.JclFixture"

      override def workflowDefinitions: Vector[WorkflowDefinition] = definitions
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector("ok", "fail", "hook", "compensate", "advanceOrder").map { name =>
          CmlOperationDefinition(
            name = name,
            kind = "COMMAND",
            execution = operationExecution.get(name),
            jobDefinitionRef = operationJobDefinitionRefs.get(name),
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
                JclOperation("ok", trace, fail = false),
                JclOperation("fail", trace, fail = true),
                JclOperation("hook", trace, fail = false),
                JclOperation("compensate", trace, fail = false)
              )
            )
          ),
          spec.ServiceDefinition(
            name = "workflow",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                WorkflowOperation("advanceOrder", trace)
              )
            )
          )
        )
      )
    )
    component.entitySpace.registerEntity("salesOrder", _collection(entities))
    val componentid = ComponentId("org.goldenport.cncf.test.JclFixture")
    val instanceid = ComponentInstanceId.default(componentid)
    val core = Component.Core.create(componentid.name, componentid, instanceid, protocol)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _collection(
    entities: Vector[SalesOrder]
  )(using EntityPersistent[SalesOrder]): EntityCollection[SalesOrder] = {
    val entitymap = entities.map(x => x.id -> x).toMap
    val storerealm = new EntityRealm[SalesOrder](
      entityName = "salesOrder",
      loader = EntityLoader(id => entitymap.get(id)),
      state = new IdRef[EntityRealmState[SalesOrder]](EntityRealmState(Map.empty))
    )
    entities.foreach(storerealm.put)
    val descriptor = EntityDescriptor[SalesOrder](
      collectionId = _collection_id,
      plan = EntityRuntimePlan[SalesOrder](
        entityName = "salesOrder",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[SalesOrder]]
    )
    new EntityCollection[SalesOrder](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, None)
    )
  }

  private def _execute(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    arguments: List[Argument]
  ): OperationResponse =
    _execute_result(subsystem, selector, arguments) match {
      case Consequence.Success(response) => response
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _execute_result(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    body: String
  ): Consequence[OperationResponse] =
    _execute_result(subsystem, selector, List(Argument("body", body)))

  private def _execute_result(
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

  private def _entity_id(
    entropy: String
  ): EntityId =
    EntityId("jcl", entropy, _collection_id)

  private def _persistent: EntityPersistent[SalesOrder] = new EntityPersistent[SalesOrder] {
    def id(e: SalesOrder): EntityId = e.id
    def toRecord(e: SalesOrder): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[SalesOrder] =
      Consequence.notImplemented("not used in JclJobControlComponentSpec")
  }

  private val _seed = new AtomicInteger(0)
}

private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value: A = initial

  def get: A = synchronized { _value }
  def set(a: A): Unit = synchronized { _value = a }

  override def getAndSet(a: A): A = synchronized {
    val prev = _value
    _value = a
    prev
  }

  def access: (A, A => Boolean) = synchronized {
    val snapshot = _value
    val setter: A => Boolean = (next: A) => synchronized {
      if (_value == snapshot) {
        _value = next
        true
      } else {
        false
      }
    }
    (snapshot, setter)
  }

  override def tryUpdate(f: A => A): Boolean = synchronized {
    _value = f(_value)
    true
  }

  override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
    val (next, out) = f(_value)
    _value = next
    Some(out)
  }

  def update(f: A => A): Unit = synchronized {
    _value = f(_value)
  }

  def modify[B](f: A => (A, B)): B = synchronized {
    val (next, out) = f(_value)
    _value = next
    out
  }

  override def modifyState[B](state: cats.data.State[A, B]): B = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    out
  }

  override def tryModifyState[B](state: cats.data.State[A, B]): Option[B] = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    Some(out)
  }
}

private final case class SalesOrder(
  id: EntityId,
  status: String
) {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "status" -> status
    )
}

private final case class JclOperation(
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
    Consequence.success(JclAction(req, opname, trace, fail))
}

private final case class JclAction(
  request: Request,
  opname: String,
  trace: ArrayBuffer[String],
  fail: Boolean
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    JclActionCall(core, opname, trace, fail)
}

private final case class JclActionCall(
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

private final case class WorkflowOperation(
  opname: String,
  trace: ArrayBuffer[String]
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[org.goldenport.protocol.operation.OperationRequest] =
    Consequence.success(WorkflowAction(req, opname, trace))
}

private final case class WorkflowAction(
  request: Request,
  opname: String,
  trace: ArrayBuffer[String]
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    WorkflowActionCall(core, opname, trace)
}

private final case class WorkflowActionCall(
  core: ActionCall.Core,
  opname: String,
  trace: ArrayBuffer[String]
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    trace += s"workflow.$opname"
    Consequence.success(OperationResponse.Scalar(opname))
  }
}
