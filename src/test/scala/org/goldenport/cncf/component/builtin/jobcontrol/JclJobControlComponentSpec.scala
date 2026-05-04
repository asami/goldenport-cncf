package org.goldenport.cncf.component.builtin.jobcontrol

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import cats.effect.Ref
import org.goldenport.Consequence
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
 * @version May.  4, 2026
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
    }

    "reject invalid workflow-like or malformed YAML shapes" in {
      Given("invalid JCL payloads")
      _with_fixture() { fixture =>
      val missingJobs =
        """job:
          |  name: invalid
          |""".stripMargin
      val bothTargetKinds =
        """jobs:
          |  - name: invalid
          |    target:
          |      action: jcl_fixture.command.ok
          |      workflow:
          |        definition: sales-order-approval
          |        registration: approval
          |""".stripMargin
      val workflowMissingRegistration =
        """jobs:
          |  - name: invalid
          |    target:
          |      workflow:
          |        definition: sales-order-approval
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
      val r2 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", bothTargetKinds)
      val r3 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", branchShape)
      val r4 = _executeResult(fixture.subsystem, "job_control.job.describe_job_definition", workflowMissingRegistration)

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
      r4 match {
        case Consequence.Failure(_) => succeed
        case other => fail(s"expected failure but got $other")
      }
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
        entities = Vector(_SalesOrder(entityid, "approved"))
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
      val describedJob = _records(_record(described).asMap("jobs")).head
      describedJob.getRecord("target").flatMap(_.getRecord("workflow")).flatMap(_.getString("definition")) shouldBe Some("sales-order-approval")
      describedJob.getRecord("target").flatMap(_.getRecord("workflow")).flatMap(_.getString("registration")) shouldBe Some("approval")

      And("submission returns the workflow-triggered managed job id")
      val record = _record(response)
      record.getBoolean("success") shouldBe Some(true)
      val submittedIds = _strings(record, "submitted-job-ids")
      submittedIds.size shouldBe 1
      awaitCondition {
        fixture.trace.contains("workflow.advanceOrder")
      } shouldBe true
      fixture.trace should contain ("workflow.advanceOrder")

      val instance = fixture.subsystem.workflowEngine.instances.headOption.getOrElse(fail("workflow instance missing"))
      instance.registrationName shouldBe "approval"
      instance.relatedJobIds.head.value shouldBe submittedIds.head

      val workflowInstance = _execute(
        fixture.subsystem,
        "workflow.workflow.get_workflow_instance",
        arguments = List(Argument("id", instance.id.value))
      )
      _record(workflowInstance).getAny("related-job-ids").collect { case xs: Seq[?] => xs.map(_.toString).toVector }.getOrElse(Vector.empty) should contain (submittedIds.head)
      fixture.subsystem.jobEngine.queryVisible(org.goldenport.cncf.job.JobId.parse(submittedIds.head).toOption.get).toOption.flatten.map(_.jobId.value) shouldBe Some(submittedIds.head)
      }
    }

    "submit mixed action and workflow batches sequentially and fail-fast on workflow non-progression" in {
      Given("a fixture component with one progressable and one non-progressable workflow target")
      val approvedId = _entity_id("workflow_batch_ok")
      val pendingId = _entity_id("workflow_batch_pending")
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
          _SalesOrder(approvedId, "approved"),
          _SalesOrder(pendingId, "pending")
        )
      ) { fixture =>
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val mixed =
        s"""jobs:
           |  - name: direct
           |    target:
           |      action: jcl_fixture.command.ok
           |    parameters:
           |      orderId: direct-1
           |  - name: wf-ok
           |    target:
           |      workflow:
           |        definition: sales-order-approval
           |        registration: approval
           |    parameters:
           |      orderId: ${approvedId.value}
           |  - name: wf-no-progress
           |    target:
           |      workflow:
           |        definition: sales-order-approval
           |        registration: approval
           |    parameters:
           |      orderId: ${pendingId.value}
           |    onFailure:
           |      action: jcl_fixture.command.hook
           |      parameters:
           |        reason: workflow-no-progress
           |  - name: skipped
           |    target:
           |      action: jcl_fixture.command.ok
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

  private final case class _Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    trace: ArrayBuffer[String]
  )

  private def _with_fixture[A](
    definitions: Vector[WorkflowDefinition] = Vector.empty,
    entities: Vector[_SalesOrder] = Vector.empty
  )(body: _Fixture => A): A =
    SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
      given EntityPersistent[_SalesOrder] = _persistent
      val trace = ArrayBuffer.empty[String]
      val component = _component(subsystem, trace, definitions, entities)
      val bootstrapped = new ComponentFactory().bootstrap(component)
      subsystem.add(bootstrapped)
      body(_Fixture(subsystem, bootstrapped, trace))
    }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    trace: ArrayBuffer[String],
    definitions: Vector[WorkflowDefinition],
    entities: Vector[_SalesOrder]
  ): Component = {
    given EntityPersistent[_SalesOrder] = _persistent
    val component = new Component() {
      override def workflowDefinitions: Vector[WorkflowDefinition] = definitions
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector("ok", "fail", "hook", "advanceOrder").map { name =>
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
          ),
          spec.ServiceDefinition(
            name = "workflow",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _WorkflowOperation("advanceOrder", trace)
              )
            )
          )
        )
      )
    )
    component.entitySpace.registerEntity("salesOrder", _collection(entities))
    val componentId = ComponentId("jcl_fixture")
    val instanceId = ComponentInstanceId.default(componentId)
    val core = Component.Core.create("jcl_fixture", componentId, instanceId, protocol)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  private def _collection(
    entities: Vector[_SalesOrder]
  )(using EntityPersistent[_SalesOrder]): EntityCollection[_SalesOrder] = {
    val entitymap = entities.map(x => x.id -> x).toMap
    val storerealm = new EntityRealm[_SalesOrder](
      entityName = "salesOrder",
      loader = EntityLoader(id => entitymap.get(id)),
      state = new _IdRef[EntityRealmState[_SalesOrder]](EntityRealmState(Map.empty))
    )
    entities.foreach(storerealm.put)
    val descriptor = EntityDescriptor[_SalesOrder](
      collectionId = _collection_id,
      plan = EntityRuntimePlan[_SalesOrder](
        entityName = "salesOrder",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[_SalesOrder]]
    )
    new EntityCollection[_SalesOrder](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, None)
    )
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

  private def _entity_id(
    entropy: String
  ): EntityId =
    EntityId("jcl", entropy, _collection_id)

  private def _persistent: EntityPersistent[_SalesOrder] = new EntityPersistent[_SalesOrder] {
    def id(e: _SalesOrder): EntityId = e.id
    def toRecord(e: _SalesOrder): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_SalesOrder] =
      Consequence.notImplemented("not used in JclJobControlComponentSpec")
  }

  private val _seed = new AtomicInteger(0)
}

private final class _IdRef[A](initial: A) extends Ref[cats.Id, A] {
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

private final case class _SalesOrder(
  id: EntityId,
  status: String
) {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "status" -> status
    )
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

private final case class _WorkflowOperation(
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
    Consequence.success(_WorkflowAction(req, opname, trace))
}

private final case class _WorkflowAction(
  request: Request,
  opname: String,
  trace: ArrayBuffer[String]
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _WorkflowActionCall(core, opname, trace)
}

private final case class _WorkflowActionCall(
  core: ActionCall.Core,
  opname: String,
  trace: ArrayBuffer[String]
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] = {
    trace += s"workflow.$opname"
    Consequence.success(OperationResponse.Scalar(opname))
  }
}
