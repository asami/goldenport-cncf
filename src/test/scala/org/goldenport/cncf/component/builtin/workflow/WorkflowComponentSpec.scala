package org.goldenport.cncf.component.builtin.workflow

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import cats.data.NonEmptyVector
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy}
import org.goldenport.cncf.event.{ReceptionInput, ReceptionOutcome}
import org.goldenport.cncf.job.JobStatus
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.workflow.{WorkflowDefinition, WorkflowHistoryEntry, WorkflowInstance, WorkflowStatusRule, WorkflowPriority, WorkflowRegistration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowComponentSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _collection_id = EntityCollectionId("workflow", "sales", "salesOrder")

  "WorkflowComponent" should {
    "list and describe workflow definitions from component metadata" in {
      Given("a bootstrapped component with workflow metadata")
      val fixture = _fixture(
        workflowDefinitions = Vector(
          WorkflowDefinition(
            name = "sales-order-approval",
            registrations = Vector(
              WorkflowRegistration(
                name = "approval",
                eventName = "sales-order.approved",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(
                  WorkflowStatusRule("approved", "workflow.advanceOrder", Some("advance approved order"))
                ),
                priority = WorkflowPriority(10)
              )
            )
          )
        ),
        entities = Vector(_SalesOrder(_entity_id("approved_definition"), "approved"))
      )

      When("workflow builtin operations are invoked")
      val list = _execute(fixture.subsystem, "workflow.workflow.list_workflow_definitions")
      val describe = _execute(
        fixture.subsystem,
        "workflow.workflow.describe_workflow_definition",
        arguments = List(Argument("id", "sales-order-approval"))
      )

      Then("the builtin surface exposes authoritative definition metadata")
      val definitions = _records_from_key(list, "definitions")
      definitions.size shouldBe 1
      definitions.head.getString("name") shouldBe Some("sales-order-approval")
      val registrations = _records(definitions.head.asMap("registrations"))
      registrations.size shouldBe 1
      registrations.head.getString("event-name") shouldBe Some("sales-order.approved")
      registrations.head.getString("entity-collection") shouldBe Some("salesOrder")
      val rules = _records(registrations.head.asMap("status-rules"))
      rules.head.getString("current-status") shouldBe Some("approved")
      rules.head.getString("next-action") shouldBe Some("workflow.advanceOrder")

      val described = _record(describe)
      described.getString("name") shouldBe Some("sales-order-approval")
      _records(described.asMap("registrations")).head.getString("priority") shouldBe Some("10")
    }

    "expose workflow instances and history while keeping jobs authoritative in job_control" in {
      Given("a workflow-triggered execution that submitted a managed job")
      val fixture = _fixture(
        workflowDefinitions = Vector(
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
        entities = Vector(_SalesOrder(_entity_id("approved_instance"), "approved"))
      )

      val entityid = fixture.entities.head.id
      val routed = fixture.component.eventReception.get.receive(
        ReceptionInput(
          name = "sales-order.approved",
          attributes = Map(
            "entity" -> "salesOrder",
            "orderId" -> entityid.value
          )
        )
      )
      routed.toOption.get.outcome shouldBe ReceptionOutcome.Routed
      val instance = fixture.subsystem.workflowEngine.instances.head
      _await_job_completion(fixture.subsystem, instance.relatedJobIds.head)

      When("workflow instance and history surfaces are queried")
      val listInstances = _execute(fixture.subsystem, "workflow.workflow.list_workflow_instances")
      val getInstance = _execute(
        fixture.subsystem,
        "workflow.workflow.get_workflow_instance",
        arguments = List(Argument("id", instance.id.value))
      )
      val loadHistory = _execute(
        fixture.subsystem,
        "workflow.workflow.load_workflow_history",
        arguments = List(Argument("id", instance.id.value))
      )
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val jobStatus = fixture.subsystem.components
        .find(_.name == "job_control")
        .flatMap(_.port.get[JobControlComponent.JobService])
        .getOrElse(fail("job_control service missing"))
        .getJobStatus(instance.relatedJobIds.head)

      Then("workflow shows orchestration state and cross-links to job ids without duplicating job schema")
      val instances = _records_from_key(listInstances, "instances")
      instances.nonEmpty shouldBe true
      instances.head.getString("instance-id") shouldBe Some(instance.id.value)

      val loaded = _record(getInstance)
      loaded.getString("registration-name") shouldBe Some("approval")
      loaded.getString("last-action") shouldBe Some(s"${fixture.component.name}.workflow.advanceOrder")
      loaded.getAny("related-job-ids").collect { case xs: Seq[?] => xs.map(_.toString) }.getOrElse(fail("related-job-ids missing")) shouldBe Vector(instance.relatedJobIds.head.value)
      loaded.getRecord("job-surface").flatMap(_.getAny("selectors")).collect {
        case xs: Seq[?] => xs.map(_.toString)
      }.getOrElse(fail("job-surface selectors missing")) should contain allOf(
        "job_control.job.get_job_status",
        "job_control.job.load_job_history",
        "job_control.job.get_job_result"
      )
      loaded.asMap.contains("job-status") shouldBe false
      loaded.asMap.contains("job-result") shouldBe false

      val history = _records_from_key(loadHistory, "history")
      history.head.getString("message") shouldBe Some("instance-created")
      history.last.getString("selected-action") shouldBe Some(s"${fixture.component.name}.workflow.advanceOrder")
      history.last.getString("related-job-id") shouldBe Some(instance.relatedJobIds.head.value)

      And("job_control remains the authoritative job surface for the same execution")
      jobStatus.toOption.map(_.jobId.value) shouldBe Some(instance.relatedJobIds.head.value)
      jobStatus.toOption.map(_.status).exists(Set(JobStatus.Succeeded, JobStatus.Running, JobStatus.Submitted).contains) shouldBe true
    }
  }

  private final case class _Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component,
    entities: Vector[_SalesOrder]
  )

  private def _fixture(
    workflowDefinitions: Vector[WorkflowDefinition],
    entities: Vector[_SalesOrder]
  ): _Fixture = {
    given EntityPersistent[_SalesOrder] = _persistent
    val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
    val component = _component(subsystem, workflowDefinitions, entities)
    val bootstrapped = new ComponentFactory().bootstrap(component)
    subsystem.add(bootstrapped)
    _Fixture(subsystem, bootstrapped, entities)
  }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    definitions: Vector[WorkflowDefinition],
    entities: Vector[_SalesOrder]
  ): Component = {
    given EntityPersistent[_SalesOrder] = _persistent
    val trace = ArrayBuffer.empty[String]
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
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
    val component = new Component() {
      override def workflowDefinitions: Vector[WorkflowDefinition] = definitions
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          CmlOperationDefinition(
            name = "advanceOrder",
            kind = "COMMAND",
            inputType = "advanceOrderInput",
            outputType = "advanceOrderResult",
            inputValueKind = "COMMAND_VALUE"
          )
        )
    }
    component.entitySpace.registerEntity("salesOrder", _collection(entities))
    val name = s"workflow_projection_component_${_seed.incrementAndGet()}"
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val core = Component.Core.create(name, componentId, instanceId, protocol)
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

  private def _await_job_completion(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    jobId: org.goldenport.cncf.job.JobId
  ): Unit = {
    val deadline = System.currentTimeMillis() + 3000L
    while ({
      subsystem.jobEngine.query(jobId).exists(m => m.status == JobStatus.Submitted || m.status == JobStatus.Running) &&
      System.currentTimeMillis() < deadline
    }) {
      Thread.sleep(10L)
    }
  }

  private def _execute(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    selector: String,
    arguments: List[Argument] = Nil
  ): OperationResponse =
    _component_execute(_component_for(subsystem, selector), _build_request(subsystem.resolver, selector, arguments))

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

  private def _component_execute(
    component: Component,
    request: Request
  ): OperationResponse =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    } match {
      case Consequence.Success(response) => response
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _record(response: OperationResponse): Record =
    response match {
      case OperationResponse.RecordResponse(record) => record
      case other => fail(s"expected RecordResponse but got $other")
    }

  private def _records_from_key(response: OperationResponse, key: String): Vector[Record] =
    _records(_record(response).asMap(key))

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Seq[?] => xs.collect { case rec: Record => rec }.toVector
      case other => fail(s"expected record list but got ${Option(other).map(_.getClass.getName).getOrElse("null")}")
    }

  private def _entity_id(
    entropy: String
  ): EntityId =
    EntityId("workflow", entropy, _collection_id)

  private def _persistent: EntityPersistent[_SalesOrder] = new EntityPersistent[_SalesOrder] {
    def id(e: _SalesOrder): EntityId = e.id
    def toRecord(e: _SalesOrder): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_SalesOrder] =
      Consequence.notImplemented("not used in WorkflowComponentSpec")
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

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
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
