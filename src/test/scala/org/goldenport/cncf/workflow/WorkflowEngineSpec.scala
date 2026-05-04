package org.goldenport.cncf.workflow

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntityStorage, PartitionStrategy}
import org.goldenport.cncf.event.{ReceptionDomainEvent, ReceptionInput, ReceptionOutcome}
import org.goldenport.cncf.job.JobStatus
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.data.NonEmptyVector
import cats.effect.Ref
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowEngineSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with WorkflowEngineTestFixture {
  private val _collection_id = EntityCollectionId("workflow", "sales", "salesOrder")

  "WorkflowEngine" should {
    "bootstrap component metadata and submit workflow-selected action as a new managed job" in {
      Given("component metadata with workflow definition and resident entity")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("approved_1")
      _with_fixture(
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
        trace = trace,
        entities = Vector(_SalesOrder(entityid, "approved"))
      ) { fixture =>
        When("matching event is received")
        val result = fixture.component.eventReception.get.receive(
          ReceptionInput(
            name = "sales-order.approved",
            attributes = Map(
              "entity" -> "salesOrder",
              "orderId" -> entityid.value
            )
          )
        )

        Then("workflow creates one instance, submits one job, and runs through ActionCall")
        result.toOption.get.outcome shouldBe ReceptionOutcome.Routed
        val instance = fixture.subsystem.workflowEngine.instances.head
        instance.registrationName shouldBe "approval"
        instance.relatedJobIds.size shouldBe 1
        _await_job_completion(fixture, instance.relatedJobIds.head)
        trace.toVector shouldBe Vector("workflow.advanceOrder")
        fixture.subsystem.jobEngine.query(instance.relatedJobIds.head).flatMap(_.tasks.tasks.headOption.flatMap(_.component)) shouldBe Some(fixture.component.name)
      }
    }

    "update existing workflow instance on later matching event for the same entity" in {
      Given("one workflow instance already created for the entity")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("repeat_1")
      _with_fixture(
        workflowDefinitions = Vector(
          WorkflowDefinition(
            name = "sales-order-repeat",
            registrations = Vector(
              WorkflowRegistration(
                name = "repeat",
                eventName = "sales-order.changed",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrder")),
                priority = WorkflowPriority(5)
              )
            )
          )
        ),
        trace = trace,
        entities = Vector(_SalesOrder(entityid, "approved"))
      ) { fixture =>
        val input = ReceptionInput(
          name = "sales-order.changed",
          attributes = Map(
            "entity" -> "salesOrder",
            "orderId" -> entityid.value
          )
        )

        When("the same entity triggers the workflow twice")
        val _ = fixture.component.eventReception.get.receive(input)
        val _ = fixture.component.eventReception.get.receive(input)

        Then("the same workflow instance is reused and accumulates related job ids")
        val instances = fixture.subsystem.workflowEngine.instances
        instances.size shouldBe 1
        instances.head.relatedJobIds.size shouldBe 2
      }
    }

    "record deterministic non-progression when entity id, entity, or status cannot be resolved" in {
      Given("workflow registrations that do not progress")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("missing_status_1")
      _with_fixture(
        workflowDefinitions = Vector(
          WorkflowDefinition(
            name = "sales-order-failures",
            registrations = Vector(
              WorkflowRegistration(
                name = "missing-status",
                eventName = "sales-order.status-check",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "phase",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrder")),
                priority = WorkflowPriority(7)
              )
            )
          )
        ),
        trace = trace,
        entities = Vector(_SalesOrder(entityid, "approved"))
      ) { fixture =>
        When("entity id is missing")
        val missingId = fixture.subsystem.workflowEngine.handle(
          fixture.component.name,
          ReceptionDomainEvent(
            name = "sales-order.status-check",
            kind = "domain-event",
            payload = Map.empty,
            attributes = Map("entity" -> "salesOrder")
          )
        )(using fixture.component.logic.executionContext()).toOption.get

        When("entity is unresolved")
        val missingEntity = fixture.subsystem.workflowEngine.handle(
          fixture.component.name,
          ReceptionDomainEvent(
            name = "sales-order.status-check",
            kind = "domain-event",
            payload = Map.empty,
            attributes = Map("entity" -> "salesOrder", "orderId" -> _entity_id("missing").value)
          )
        )(using fixture.component.logic.executionContext()).toOption.get

        When("status field is missing")
        val missingStatus = fixture.component.eventReception.get.receive(
          ReceptionInput(
            name = "sales-order.status-check",
            attributes = Map("entity" -> "salesOrder", "orderId" -> entityid.value)
          )
        )

        Then("workflow does not progress and no action is executed")
        missingId.progressed shouldBe false
        missingId.reason shouldBe Some("missing-entity-id")
        missingEntity.progressed shouldBe false
        missingEntity.reason shouldBe Some("entity-unresolved")
        missingStatus.toOption.get.outcome shouldBe ReceptionOutcome.Routed
        fixture.subsystem.workflowEngine.instances.head.status shouldBe WorkflowStatus.NoProgress
        trace shouldBe empty
      }
    }

    "use the smallest-priority registration and reject equal-priority ambiguity at bootstrap" in {
      Given("multiple workflow registrations for the same event and collection")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("priority_1")
      _with_fixture(
        workflowDefinitions = Vector(
          WorkflowDefinition(
            name = "priority-winner",
            registrations = Vector(
              WorkflowRegistration(
                name = "low",
                eventName = "sales-order.priority",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrderLow")),
                priority = WorkflowPriority(1)
              ),
              WorkflowRegistration(
                name = "high",
                eventName = "sales-order.priority",
                entityCollection = "salesOrder",
                entityIdKey = "orderId",
                statusField = "status",
                statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrderHigh")),
                priority = WorkflowPriority(9)
              )
            )
          )
        ),
        trace = trace,
        entities = Vector(_SalesOrder(entityid, "approved"))
      ) { fixture =>
        When("the event matches multiple registrations")
        val _ = fixture.component.eventReception.get.receive(
          ReceptionInput(
            name = "sales-order.priority",
            attributes = Map(
              "entity" -> "salesOrder",
              "orderId" -> entityid.value
            )
          )
        )
        val instance = fixture.subsystem.workflowEngine.instances.head
        _await_job_completion(fixture, instance.relatedJobIds.head)

        Then("the smallest numeric priority registration wins")
        trace.toVector shouldBe Vector("workflow.advanceOrderLow")

        And("equal-priority ambiguity is rejected during bootstrap")
        withWorkflowSubsystem("workflow-ambiguous") { subsystem =>
          val component = _component(
            subsystem = subsystem,
            definitions = Vector(
              WorkflowDefinition(
                name = "ambiguous",
                registrations = Vector(
                  WorkflowRegistration(
                    name = "a",
                    eventName = "sales-order.priority",
                    entityCollection = "salesOrder",
                    entityIdKey = "orderId",
                    statusField = "status",
                    statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrder")),
                    priority = WorkflowPriority(4)
                  ),
                  WorkflowRegistration(
                    name = "b",
                    eventName = "sales-order.priority",
                    entityCollection = "salesOrder",
                    entityIdKey = "orderId",
                    statusField = "status",
                    statusRules = Vector(WorkflowStatusRule("approved", "workflow.advanceOrderHigh")),
                    priority = WorkflowPriority(4)
                  )
                )
              )
            ),
            trace = ArrayBuffer.empty,
            entities = Vector(_SalesOrder(_entity_id("priority_2"), "approved"))
          )

          val ex = intercept[IllegalStateException] {
            new ComponentFactory().bootstrap(component)
          }
          ex.getMessage should include ("ambiguous workflow registration")
        }
      }
    }
  }

  private final case class _Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component
  )

  private def _with_fixture[A](
    workflowDefinitions: Vector[WorkflowDefinition],
    trace: ArrayBuffer[String],
    entities: Vector[_SalesOrder]
  )(body: _Fixture => A): A =
    withWorkflowSubsystem(s"workflow-spec-${_seed.incrementAndGet()}") { subsystem =>
      val component = _component(subsystem, workflowDefinitions, trace, entities)
      val factory = new ComponentFactory()
      val bootstrapped = factory.bootstrap(component)
      subsystem.add(bootstrapped)
      body(_Fixture(subsystem, bootstrapped))
    }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    definitions: Vector[WorkflowDefinition],
    trace: ArrayBuffer[String],
    entities: Vector[_SalesOrder]
  ): Component = {
    given EntityPersistent[_SalesOrder] = _persistent
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "workflow",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                _WorkflowOperation("advanceOrder", trace),
                _WorkflowOperation("advanceOrderLow", trace),
                _WorkflowOperation("advanceOrderHigh", trace)
              )
            )
          )
        )
      )
    )
    val component = new Component() {
      override def workflowDefinitions: Vector[WorkflowDefinition] = definitions
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector("advanceOrder", "advanceOrderLow", "advanceOrderHigh").map { name =>
          CmlOperationDefinition(
            name = name,
            kind = "COMMAND",
            inputType = s"${name}Input",
            outputType = s"${name}Result",
            inputValueKind = "COMMAND_VALUE"
          )
        }
    }
    component.entitySpace.registerEntity("salesOrder", _collection(entities))
    val name = s"workflow_component_${_seed.incrementAndGet()}"
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
    val descriptor = EntityDescriptor[
      _SalesOrder
    ](
      collectionId = _collection_id,
      plan = EntityRuntimePlan[
        _SalesOrder
      ](
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
    fixture: _Fixture,
    jobId: org.goldenport.cncf.job.JobId
  ): Unit = {
    val deadline = System.currentTimeMillis() + 3000L
    while ({
      fixture.subsystem.jobEngine.query(jobId).exists(m => m.status == JobStatus.Submitted || m.status == JobStatus.Running) &&
      System.currentTimeMillis() < deadline
    }) {
      Thread.sleep(10L)
    }
  }

  private def _entity_id(
    entropy: String
  ): EntityId =
    EntityId("workflow", entropy, _collection_id)

  private def _persistent: EntityPersistent[_SalesOrder] = new EntityPersistent[_SalesOrder] {
    def id(e: _SalesOrder): EntityId = e.id
    def toRecord(e: _SalesOrder): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_SalesOrder] =
      Consequence.notImplemented("not used in WorkflowEngineSpec")
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
