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
import org.goldenport.cncf.job.{JobEngineTestFixture, JobStatus, JobProducerOperationalAuditTestSupport}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.log.LogBackend
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
 *  version May.  4, 2026
 *  version Aug. 11, 2026
 *  version Sep. 17, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowEngineSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with WorkflowEngineTestFixture
  with JobEngineTestFixture {
  private val _collection_id = EntityCollectionId("workflow", "sales", "salesOrder")

  "WorkflowEngine" should {
    "bootstrap component metadata and submit workflow-selected action as a new managed job" in {
      Given("component metadata with workflow definition and resident entity")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("approved_1")
      _with_fixture(
        workflowdefinitions = Vector(
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
        entities = Vector(SalesOrder(entityid, "approved"))
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
        workflowdefinitions = Vector(
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
        entities = Vector(SalesOrder(entityid, "approved"))
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
        workflowdefinitions = Vector(
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
        entities = Vector(SalesOrder(entityid, "approved"))
      ) { fixture =>
        When("entity id is missing")
        val missingid = fixture.subsystem.workflowEngine.handle(
          fixture.component.name,
          ReceptionDomainEvent(
            name = "sales-order.status-check",
            kind = "domain-event",
            payload = Map.empty,
            attributes = Map("entity" -> "salesOrder"),
            occurredAt = java.time.Instant.EPOCH
          )
        )(using fixture.component.logic.executionContext()).toOption.get

        When("entity is unresolved")
        val missingentity = fixture.subsystem.workflowEngine.handle(
          fixture.component.name,
          ReceptionDomainEvent(
            name = "sales-order.status-check",
            kind = "domain-event",
            payload = Map.empty,
            attributes = Map("entity" -> "salesOrder", "orderId" -> _entity_id("missing").value),
            occurredAt = java.time.Instant.EPOCH
          )
        )(using fixture.component.logic.executionContext()).toOption.get

        When("status field is missing")
        val missingstatus = fixture.component.eventReception.get.receive(
          ReceptionInput(
            name = "sales-order.status-check",
            attributes = Map("entity" -> "salesOrder", "orderId" -> entityid.value)
          )
        )

        Then("workflow does not progress and no action is executed")
        missingid.progressed shouldBe false
        missingid.reason shouldBe Some("missing-entity-id")
        missingentity.progressed shouldBe false
        missingentity.reason shouldBe Some("entity-unresolved")
        missingstatus.toOption.get.outcome shouldBe ReceptionOutcome.Routed
        fixture.subsystem.workflowEngine.instances.head.status shouldBe WorkflowStatus.NoProgress
        trace shouldBe empty
      }
    }

    "use the smallest-priority registration and reject equal-priority ambiguity at bootstrap" in {
      Given("multiple workflow registrations for the same event and collection")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("priority_1")
      _with_fixture(
        workflowdefinitions = Vector(
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
        entities = Vector(SalesOrder(entityid, "approved"))
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
            entities = Vector(SalesOrder(_entity_id("priority_2"), "approved"))
          )

          val ex = intercept[IllegalStateException] {
            new ComponentFactory().bootstrap(component)
          }
          ex.getMessage should include ("ambiguous workflow registration")
        }
      }
    }
  }

  "PW1 Native Workflow acknowledgement" should _producer_scenario("PW1", "R2,R4,R7") {
    "bind the normally recorded instance and real Job and Action to the admitted definition" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R2,R4,R7; PW1: admitted component metadata and a resident salesOrder using the real resolver and prepared ActionTask")
      val trace = ArrayBuffer.empty[String]
      val entityid = _entity_id("producer_1")
      val definition = _producer_definition("native", "workflow.advanceOrder")
      _with_fixture(Vector(definition), trace, Vector(SalesOrder(entityid, "approved"))) { fixture =>
        val clock = new JobProducerOperationalAuditTestSupport.CountingClock
        val ids = new JobProducerOperationalAuditTestSupport.CountingIds
        given ExecutionContext = JobProducerOperationalAuditTestSupport.context(clock, ids)
        val recorded = ArrayBuffer.empty[WorkflowInstance]
        val backend = new JobProducerOperationalAuditTestSupport.RecordingBackend(_ =>
          recorded += fixture.subsystem.workflowEngine.instances.head)
        When("the actual matching domain event submits its native action and records the Workflow update")
        val result = JobProducerOperationalAuditTestSupport.withBackend(backend) {
          fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value))
        }
        val decision = result.toOption.get
        val instance = fixture.subsystem.workflowEngine.instances.head
        val jobid = decision.relatedJobId.get
        _await_job_completion(fixture, jobid)
        val native = JobProducerOperationalAuditTestSupport.nativeRecord(fixture.subsystem.jobEngine, jobid)
        Then("one source-bound frame sees the already recorded original instance and its actual Job and Action IDs")
        decision.progressed shouldBe true
        recorded.toVector shouldBe Vector(instance)
        instance.history.size shouldBe 2
        instance.status shouldBe WorkflowStatus.Submitted
        instance.relatedJobIds shouldBe Vector(jobid)
        trace.toVector shouldBe Vector("workflow.advanceOrder")
        decision.selectedAction shouldBe Some(s"${fixture.component.name}.workflow.advanceOrder")
        native.tasks.size shouldBe 1
        backend.events.size shouldBe 1
        val atoms = backend.events.head.attributes
        atoms.getString("producer") shouldBe Some("workflow")
        atoms.getString("job-id") shouldBe Some(native.id.value)
        atoms.getString("operation-action-id") shouldBe Some(native.tasks.head.actionId.value)
        atoms.getString("workflow-instance-id") shouldBe Some(instance.id.value)
        atoms.getString("workflow-definition-id") shouldBe Some(definition.id.value)
        atoms.getString("occurred-at") shouldBe Some(instance.updatedAt.toString)
        atoms.getString("workflow-status") shouldBe Some("submitted")
        backend.events.head.message should not include entityid.value
        backend.events.head.message should not include "advanceOrder"
      }
    }
  }

  "PW2 Native Workflow non-progression" should _producer_scenario("PW2", "R2,R7") {
    "preserve missing match identity entity status rule and resolver or submit refusal without a guessed association" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R2,R7; PW2: real native non-progression and resolver failures, including a historical acknowledged Job before actual admission refusal")
      val backend = new JobProducerOperationalAuditTestSupport.RecordingBackend
      val entityid = _entity_id("producer_missing")
      val definition = _producer_definition("non-progress", "workflow.advanceOrder")
      _with_fixture(Vector(definition), ArrayBuffer.empty, Vector(SalesOrder(entityid, "approved"))) { fixture =>
        given ExecutionContext = fixture.component.logic.executionContext()
        When("unmatched missing-id and unresolved-entity events reach the actual Workflow owner")
        JobProducerOperationalAuditTestSupport.withBackend(backend) {
          val unmatched = fixture.subsystem.workflowEngine.handle(fixture.component.name,
            _producer_event(entityid.value).copy(name = "unmatched")).toOption.get
          val missingid = fixture.subsystem.workflowEngine.handle(fixture.component.name,
            _producer_event(entityid.value).copy(attributes = Map("entity" -> "salesOrder"))).toOption.get
          val unresolved = fixture.subsystem.workflowEngine.handle(fixture.component.name,
            _producer_event(_entity_id("unresolved").value)).toOption.get
          Then("the original decisions and NoProgress history remain and emit no producer frame")
          unmatched.reason shouldBe Some("no-workflow-match")
          missingid.reason shouldBe Some("missing-entity-id")
          unresolved.reason shouldBe Some("entity-unresolved")
          Vector(unmatched, missingid, unresolved).foreach(_.progressed shouldBe false)
          backend.events shouldBe empty
          fixture.subsystem.workflowEngine.instances.head.relatedJobIds shouldBe empty
          val accepted = fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value)).toOption.get
          _await_job_completion(fixture, accepted.relatedJobId.get)
          val prior = fixture.subsystem.workflowEngine.findInstance(accepted.instanceId.get).get
          backend.clear()
          fixture.subsystem.jobEngine.quiesce()
          val refused = fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value))
          refused.isSuccess shouldBe false
          fixture.subsystem.workflowEngine.findInstance(prior.id) shouldBe Some(prior)
          backend.events shouldBe empty
        }
      }
      Vector("phase" -> "approved", "status" -> "unmatched").foreach { case (field, status) =>
        val registration = definition.registrations.head.copy(statusField = field)
        _with_fixture(Vector(definition.copy(registrations = Vector(registration))), ArrayBuffer.empty,
          Vector(SalesOrder(entityid, status))) { fixture =>
          given ExecutionContext = fixture.component.logic.executionContext()
          When("the actual resident entity lacks the required status or matching rule")
          val result = JobProducerOperationalAuditTestSupport.withBackend(backend) {
            fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value))
          }.toOption.get
          Then("the original missing-status or unmatched-rule decision retains NoProgress with no submitted Job")
          result.reason shouldBe Some(if (field == "phase") "missing-status-field" else "status-unmatched")
          result.progressed shouldBe false
          fixture.subsystem.workflowEngine.instances.head.status shouldBe WorkflowStatus.NoProgress
          fixture.subsystem.workflowEngine.instances.head.relatedJobIds shouldBe empty
          backend.events shouldBe empty
        }
      }
      _with_fixture(Vector(_producer_definition("unknown", "workflow.unknown")), ArrayBuffer.empty,
        Vector(SalesOrder(entityid, "approved"))) { fixture =>
        given ExecutionContext = fixture.component.logic.executionContext()
        When("the actual resolver cannot find the chosen target")
        val result = JobProducerOperationalAuditTestSupport.withBackend(backend) {
          fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value))
        }
        Then("the original failure leaves only the initial instance history and no association")
        result.isSuccess shouldBe false
        fixture.subsystem.workflowEngine.instances.head.history.size shouldBe 1
        fixture.subsystem.workflowEngine.instances.head.relatedJobIds shouldBe empty
        backend.events shouldBe empty
      }
    }
  }

  "PW3 Native Workflow repetition and priority" should _producer_scenario("PW3", "R2,R5,R7") {
    "reuse the real instance and acknowledge two distinct jobs and actions for the exact priority-winning definition" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R2,R5,R7; PW3: independent fresh native fixtures with the same seed, counting providers, entity and distinct-priority definitions")
      val low = _producer_definition("low", "workflow.advanceOrderLow", 1)
      val backend = new JobProducerOperationalAuditTestSupport.RecordingBackend
      When("the same actual entity event is handled twice through priority selection independently under Nop and recording backends")
      val nop = _producer_repeat_run(LogBackend.NopLogBackend)
      val observed = _producer_repeat_run(backend)
      Then("the complete canonical native subset and exact ID, clock and body counts equal the independent Nop run")
      observed shouldBe nop
      observed.idcalls shouldBe nop.idcalls
      observed.clockcalls shouldBe nop.clockcalls
      observed.trace.size shouldBe nop.trace.size
      And("one original instance retains both distinct acknowledged jobs and exactly one frame per accepted submission")
      observed.instancecount shouldBe 1
      observed.decisions.map(_.instanceId).distinct.size shouldBe 1
      observed.jobids.distinct.size shouldBe 2
      observed.actions.map(_.head).distinct.size shouldBe 2
      observed.instance.relatedJobIds.map(_.value) shouldBe observed.jobids
      observed.instance.history.size shouldBe 3
      observed.instance.registrationName shouldBe "low"
      observed.statuses shouldBe Vector.fill(2)(JobStatus.Succeeded)
      observed.results shouldBe Vector.fill(2)(true)
      observed.trace shouldBe Vector.fill(2)("workflow.advanceOrderLow")
      observed.trace.size shouldBe 2
      backend.events.size shouldBe 2
      backend.events.map(_.attributes.getString("workflow-definition-id")) shouldBe Vector.fill(2)(Some(low.id.value))
      backend.events.map(_.attributes.getString("workflow-instance-id")) shouldBe Vector.fill(2)(Some(observed.instance.id.value))
      backend.events.map(_.attributes.getString("job-id")) shouldBe observed.jobids.map(Some(_))
      backend.events.map(_.attributes.getString("operation-action-id")) shouldBe observed.actions.map(actions => Some(actions.head))
    }
  }

  "PW4 Native Workflow observation has no effects" should _producer_scenario("PW4", "R1,R2,R8") {
    "preserve independent deterministic native outcomes counts and history and propagate the original domain throw" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R1,R2,R8; PW4: independent admitted native runs using identical explicit clock and ID providers with Nop recording and throwing backends")
      When("the real Workflow submission runs independently under each backend")
      val backend = new JobProducerOperationalAuditTestSupport.RecordingBackend
      val throwing = new JobProducerOperationalAuditTestSupport.ThrowingBackend
      val nop = _producer_run(LogBackend.NopLogBackend)
      val observed = _producer_run(backend)
      val failedlog = _producer_run(throwing)
      Then("public decisions instance histories native Jobs action outcomes and clock-ID-body counts are identical")
      observed shouldBe nop
      failedlog shouldBe nop
      backend.events.size shouldBe 1
      throwing.calls.get() shouldBe 1
      val entityid = _entity_id("producer_throw")
      _with_fixture(Vector(_producer_definition("throw", "workflow.advanceOrder")), ArrayBuffer.empty,
        Vector(SalesOrder(entityid, "approved"))) { fixture =>
        val clock = new JobProducerOperationalAuditTestSupport.CountingClock
        given ExecutionContext = JobProducerOperationalAuditTestSupport.context(clock, new JobProducerOperationalAuditTestSupport.CountingIds)
        clock.failOnCall = Some(clock.calls.get() + 1)
        backend.clear()
        When("the original Workflow domain clock throws before its native instance return")
        val thrown = intercept[IllegalStateException] {
          JobProducerOperationalAuditTestSupport.withBackend(backend) {
            fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value))
          }
        }
        Then("the original domain exception escapes and no association or retry is produced")
        (thrown eq clock.fault) shouldBe true
        fixture.subsystem.workflowEngine.instances shouldBe empty
        backend.events shouldBe empty
      }
    }
  }

  private def _producer_scenario(example: String, rules: String) =
    afterWord(s"in spec:job-producer-operational-correlation, example:$example, rules:$rules, phase:69.7")

  private def _producer_definition(name: String, action: String, priority: Int = 1): WorkflowDefinition =
    WorkflowDefinition(WorkflowDefinitionId("native", "producer", Some(java.time.Instant.EPOCH), Some(name)), name,
      Vector(WorkflowRegistration(name, "producer.event", "salesOrder", "orderId", "status",
        Vector(WorkflowStatusRule("approved", action)), WorkflowPriority(priority))))

  private def _producer_event(entityid: String): ReceptionDomainEvent =
    ReceptionDomainEvent("producer.event", "domain-event", Map.empty,
      Map("entity" -> "salesOrder", "orderId" -> entityid), java.time.Instant.EPOCH)

  private final case class ProducerRun(decision: WorkflowDecision, instance: WorkflowInstance,
    jobid: String, actions: Vector[String], status: JobStatus, result: Boolean,
    trace: Vector[String], clockcalls: Int, idcalls: Int)

  private final case class ProducerRepeatRun(decisions: Vector[WorkflowDecision], instancecount: Int,
    instance: WorkflowInstance, jobids: Vector[String], actions: Vector[Vector[String]],
    statuses: Vector[JobStatus], results: Vector[Boolean], trace: Vector[String],
    clockcalls: Int, idcalls: Int)

  private def _producer_repeat_run(backend: LogBackend): ProducerRepeatRun = {
    Given("a fresh admitted repeated-event fixture with deterministic seed and identical native definitions and providers")
    _seed.set(700)
    val trace = ArrayBuffer.empty[String]
    val entityid = _entity_id("producer_repeat")
    val low = _producer_definition("low", "workflow.advanceOrderLow", 1)
    val high = _producer_definition("high", "workflow.advanceOrderHigh", 9)
    _with_fixture(Vector(high, low), trace, Vector(SalesOrder(entityid, "approved"))) { fixture =>
      val clock = new JobProducerOperationalAuditTestSupport.CountingClock
      val ids = new JobProducerOperationalAuditTestSupport.CountingIds
      given ExecutionContext = JobProducerOperationalAuditTestSupport.context(clock, ids)
      When("exactly two native handles select the priority winner and both original jobs finish")
      val decisions = JobProducerOperationalAuditTestSupport.withBackend(backend) {
        Vector.fill(2)(fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value)).toOption.get)
      }
      val jobs = decisions.map(_.relatedJobId.get)
      jobs.foreach(_await_job_completion(fixture, _))
      val instances = fixture.subsystem.workflowEngine.instances
      val records = jobs.map(JobProducerOperationalAuditTestSupport.nativeRecord(fixture.subsystem.jobEngine, _))
      ProducerRepeatRun(decisions, instances.size, instances.head, records.map(_.id.value),
        records.map(_.tasks.map(_.actionId.value).toVector), records.map(_.status),
        records.map(_.result.exists { case org.goldenport.cncf.job.JobResult.Success(_) => true; case _ => false }),
        trace.toVector, clock.calls.get(), ids.calls.get())
    }
  }

  private def _producer_run(backend: LogBackend): ProducerRun = {
    _seed.set(700)
    val trace = ArrayBuffer.empty[String]
    val entityid = _entity_id("producer_deterministic")
    _with_fixture(Vector(_producer_definition("deterministic", "workflow.advanceOrder")), trace,
      Vector(SalesOrder(entityid, "approved"))) { fixture =>
      val clock = new JobProducerOperationalAuditTestSupport.CountingClock
      val ids = new JobProducerOperationalAuditTestSupport.CountingIds
      given ExecutionContext = JobProducerOperationalAuditTestSupport.context(clock, ids)
      val decision = JobProducerOperationalAuditTestSupport.withBackend(backend) {
        fixture.subsystem.workflowEngine.handle(fixture.component.name, _producer_event(entityid.value)).toOption.get
      }
      _await_job_completion(fixture, decision.relatedJobId.get)
      val native = JobProducerOperationalAuditTestSupport.nativeRecord(fixture.subsystem.jobEngine, decision.relatedJobId.get)
      ProducerRun(decision, fixture.subsystem.workflowEngine.instances.head, native.id.value,
        native.tasks.map(_.actionId.value).toVector, native.status, native.result.exists { case org.goldenport.cncf.job.JobResult.Success(_) => true; case _ => false },
        trace.toVector, clock.calls.get(), ids.calls.get())
    }
  }

  private final case class Fixture(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: Component
  )

  private def _with_fixture[A](
    workflowdefinitions: Vector[WorkflowDefinition],
    trace: ArrayBuffer[String],
    entities: Vector[SalesOrder]
  )(body: Fixture => A): A =
    withWorkflowSubsystem(s"workflow-spec-${_seed.incrementAndGet()}") { subsystem =>
      val component = _component(subsystem, workflowdefinitions, trace, entities)
      val factory = new ComponentFactory()
      val bootstrapped = factory.bootstrap(component)
      subsystem.add(bootstrapped)
      body(Fixture(subsystem, bootstrapped))
    }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    definitions: Vector[WorkflowDefinition],
    trace: ArrayBuffer[String],
    entities: Vector[SalesOrder]
  ): Component = {
    given EntityPersistent[SalesOrder] = _persistent
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "workflow",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(
                WorkflowOperation("advanceOrder", trace),
                WorkflowOperation("advanceOrderLow", trace),
                WorkflowOperation("advanceOrderHigh", trace)
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
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
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
    val descriptor = EntityDescriptor[
      SalesOrder
    ](
      collectionId = _collection_id,
      plan = EntityRuntimePlan[
        SalesOrder
      ](
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

  private def _await_job_completion(
    fixture: Fixture,
    jobid: org.goldenport.cncf.job.JobId
  ): Unit = {
    awaitCondition {
      fixture.subsystem.jobEngine.query(jobid).exists { m =>
        m.status != JobStatus.Submitted && m.status != JobStatus.Running
      }
    } shouldBe true
  }

  private def _entity_id(
    entropy: String
  ): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("workflow", entropy, _collection_id, entropy = entropy)

  private def _persistent: EntityPersistent[SalesOrder] = new EntityPersistent[SalesOrder] {
    def id(e: SalesOrder): EntityId = e.id
    def toRecord(e: SalesOrder): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[SalesOrder] =
      Consequence.notImplemented("not used in WorkflowEngineSpec")
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

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
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
