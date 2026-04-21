package org.goldenport.cncf.event

import scala.collection.mutable.ArrayBuffer
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.job.{ActionId, JobContext, JobControlPolicy, JobControlRequest, JobControlResponse, JobEngine, JobPersistencePolicy, JobQueryReadModel, JobResult, JobStatus, JobSubmitOption, JobTask, JobTaskPage, JobTimelinePage, JobId, TaskId}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.unitofwork.{CommitRecorder, UnitOfWork, UnitOfWorkOp}
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 21, 2026
 * @version Apr. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventReceptionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventReception" should {
    "route target event to ActionCall dispatcher deterministically" in {
      Given("reception with CML event definition and action route")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val dispatcher = new _RecordingDispatcher(calls)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        currentSubsystemName = Some("sample"),
        currentComponentName = Some("publisher")
      )

      reception.register(
        CmlEventDefinition(
          name = "person.created",
          category = CmlEventCategory.ActionEvent,
          kind = Some("created"),
          selectors = Map("source" -> "crm"),
          actionName = Some("person.sync"),
          priority = 0
        )
      )

      When("target event is received")
      val result = reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map("source" -> "crm"),
          persistent = true
        )
      )

      Then("ActionCall dispatcher is executed through routed subscription")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = true
        )
      )
      calls.toVector shouldBe Vector("person.sync")
      store.query(EventStore.Query(name = Some("person.created"))).toOption.getOrElse(Vector.empty).size shouldBe 1
    }

    "drop non-target event deterministically" in {
      Given("reception with target definition")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )

      reception.register(
        CmlEventDefinition(
          name = "invoice.closed",
          category = CmlEventCategory.ActionEvent,
          kind = Some("closed"),
          actionName = Some("invoice.report")
        )
      )

      When("non-target kind is received")
      val result = reception.receive(
        ReceptionInput(
          name = "invoice.closed",
          kind = "opened"
        )
      )

      Then("event is dropped and not routed")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Dropped,
          dispatchedCount = 0,
          persisted = false,
          reason = Some("non-target")
        )
      )
      calls.toVector shouldBe Vector.empty
    }

    "fail for unknown event" in {
      Given("empty reception definition")
      val recorder = new _InMemoryCommitRecorder
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, EventStore.inMemory)
      val reception = EventReception.default(
        EventBus.default(engine),
        new _RecordingDispatcher(ArrayBuffer.empty)
      )

      When("receiving unknown event")
      val result = reception.receive(
        ReceptionInput(
          name = "unknown.event",
          kind = "x"
        )
      )

      Then("failure is returned with deterministic taxonomy")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Invalid
        case _ =>
          fail("expected failure")
      }
    }

    "fail for subscription mismatch when event has no route binding" in {
      Given("known event without action binding")
      val recorder = new _InMemoryCommitRecorder
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, EventStore.inMemory)
      val reception = EventReception.default(
        EventBus.default(engine),
        new _RecordingDispatcher(ArrayBuffer.empty)
      )
      reception.register(
        CmlEventDefinition(
          name = "order.shipped",
          category = CmlEventCategory.ActionEvent,
          kind = Some("shipped"),
          actionName = None
        )
      )

      When("receiving matching event")
      val result = reception.receive(
        ReceptionInput(
          name = "order.shipped",
          kind = "shipped"
        )
      )

      Then("mismatch is reported as failure")
      result shouldBe a[Consequence.Failure[_]]
    }

    "deny authorized reception by event policy for user privilege" in {
      Given("authorized reception with user privilege")
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "audit.ingested",
          category = CmlEventCategory.ActionEvent,
          kind = Some("ingested"),
          actionName = Some("audit.handle")
        )
      )

      When("receiving via authorized entry point")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "audit.ingested",
          kind = "ingested"
        )
      )

      Then("policy denial is returned and action is not dispatched")
      result shouldBe a[Consequence.Failure[_]]
      calls.toVector shouldBe Vector.empty
    }

    "bind ingress-resolved execution context to secure action dispatcher" in {
      Given("secured reception and secure action dispatcher")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val levels = ArrayBuffer.empty[SecurityLevel]
      val dispatcher = new _SecureRecordingDispatcher(calls, levels)
      val reception = EventReception.default(bus, dispatcher, IngressSecurityResolver.default)
      reception.register(
        CmlEventDefinition(
          name = "secured.event",
          category = CmlEventCategory.ActionEvent,
          kind = Some("accepted"),
          actionName = Some("secured.action")
        )
      )

      When("receiving secured event with content-manager privilege")
      val result = reception.receiveSecured(
        ReceptionInput(
          name = "secured.event",
          kind = "accepted",
          attributes = Map(
            "privilege" -> "application_content_manager"
          )
        )
      )

      Then("dispatcher receives bound execution context resolved from ingress")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("secured.action")
      levels.toVector shouldBe Vector(SecurityLevel("content_manager"))
    }

    "route non-action event only through state-machine listener" in {
      Given("non-action event definition and explicit state-machine listener")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      val listened = ArrayBuffer.empty[String]
      reception.registerStateMachineListener(
        new StateMachineEventListener {
          def onEvent(
            event: ReceptionDomainEvent,
            definitions: Vector[CmlEventDefinition]
          )(using ExecutionContext): Consequence[Unit] = {
            val _ = definitions
            listened += event.name
            Consequence.unit
          }
        }
      )
      reception.register(
        CmlEventDefinition(
          name = "inventory.snapshot",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("snapshot")
        )
      )

      When("non-action event is received")
      val result = reception.receiveSecured(
        ReceptionInput(
          name = "inventory.snapshot",
          kind = "snapshot",
          attributes = Map("privilege" -> "application_content_manager")
        )
      )

      Then("only state-machine listener route is used")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      listened.toVector shouldBe Vector("inventory.snapshot")
      calls.toVector shouldBe Vector.empty
    }

    "route non-action event through direct listener without state-machine listener" in {
      Given("non-action event definition and direct listener only")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      val listened = ArrayBuffer.empty[String]
      reception.registerDirectListener(
        new DirectEventListener {
          def onEvent(
            event: ReceptionDomainEvent,
            definitions: Vector[CmlEventDefinition]
          )(using ExecutionContext): Consequence[Unit] = {
            val _ = definitions
            listened += event.name
            Consequence.unit
          }
        }
      )
      reception.register(
        CmlEventDefinition(
          name = "product.refreshed",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("refreshed")
        )
      )

      When("non-action event is received")
      val result = reception.receiveSecured(
        ReceptionInput(
          name = "product.refreshed",
          kind = "refreshed",
          attributes = Map("privilege" -> "application_content_manager")
        )
      )

      Then("direct listener route is executed without state-machine path")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      listened.toVector shouldBe Vector("product.refreshed")
      calls.toVector shouldBe Vector.empty
    }

    "dispatch subscription route from CML subscription definition" in {
      Given("subscription-based route with unicast target expression")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "person.created",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("created")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "person-sync",
          eventName = "person.created",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "person.sync",
          declaredTargetUpperBound = 1
        )
      )

      When("matching event is received with target attribute")
      val result = reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map(
            "targetId" -> "p1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample"
          )
        )
      )

      Then("subscription route dispatches action")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("person.sync")
    }

    "validate invalid broadcast subscription deterministically" in {
      Given("broadcast subscription with selector")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val reception = EventReception.default(bus, new _RecordingDispatcher(ArrayBuffer.empty))

      When("registering invalid subscription")
      val ex = intercept[IllegalStateException] {
        reception.registerSubscription(
          CmlSubscriptionDefinition(
            name = "bad",
            eventName = "x",
            route = DispatchRoute.Broadcast,
            selector = Some("source=crm"),
            actionName = "x.handle"
          )
        )
      }

      Then("registration fails with validation message")
      ex.getMessage should include("not allowed for broadcast")
    }

    "propagate entity target attributes to action for entity-state transition bridge" in {
      Given("subscription with entity target bridge")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val seen = ArrayBuffer.empty[(String, Map[String, String])]
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          event match {
            case e: ReceptionDomainEvent =>
              seen += actionName -> e.attributes
            case _ =>
              ()
          }
          Consequence.unit
        }
      }
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "person.updated",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("updated")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "person-update-bridge",
          eventName = "person.updated",
          route = DispatchRoute.Unicast,
          entityName = Some("person"),
          target = Some("targetId"),
          actionName = "domain.entity.updatePerson",
          declaredTargetUpperBound = 1
        )
      )

      When("event is received")
      val result = reception.receive(
        ReceptionInput(
          name = "person.updated",
          kind = "updated",
          attributes = Map(
            "targetId" -> "p1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample"
          )
        )
      )

      Then("dispatcher receives entity and target attributes")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      seen.size shouldBe 1
      val (_, attrs) = seen.head
      attrs.get("targetId") shouldBe Some("p1")
      attrs.get("target") shouldBe Some("p1")
      attrs.get("entity") shouldBe Some("person")
      attrs.get("entityName") shouldBe Some("person")
      attrs.get("entity_name") shouldBe Some("person")
    }

    "inject standard context attributes into event attributes for authorized reception" in {
      Given("authorized reception with explicit job and observability context")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val captured = ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          val _ = actionName
          event match {
            case e: ReceptionDomainEvent =>
              captured += e.attributes
            case _ =>
              ()
          }
          Consequence.unit
        }
      }
      val reception = EventReception.default(bus, dispatcher)
      reception.register(
        CmlEventDefinition(
          name = "context.bound.event",
          category = CmlEventCategory.ActionEvent,
          kind = Some("accepted"),
          actionName = Some("context.bound.action")
        )
      )
      val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val subsystemScope = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = "sample",
        parent = None,
        observabilityContext = base.observability
      )
      val componentScope = subsystemScope.createChildScope(ScopeKind.Component, "publisher")
      val jobctx = JobContext(
        jobId = Some(JobId.generate()),
        taskId = Some(TaskId.generate()),
        actionId = Some(ActionId.generate())
      )
      given ExecutionContext = ExecutionContext.withJobContext(base.withScope(componentScope), jobctx)

      When("receiving authorized event")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "context.bound.event",
          kind = "accepted",
          attributes = Map("source" -> "cozy")
        )
      )

      Then("standard context attributes are available to downstream dispatch")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      captured.size shouldBe 1
      val attrs = captured.head
      attrs.get("source") shouldBe Some("cozy")
      attrs.get(EventReception.StandardAttribute.TraceId) shouldBe Some(base.observability.traceId.print)
      attrs.get(EventReception.StandardAttribute.CorrelationId) shouldBe base.observability.correlationId.map(_.print)
      attrs.get(EventReception.StandardAttribute.JobId) shouldBe jobctx.jobId.map(_.print)
      attrs.get(EventReception.StandardAttribute.TaskId) shouldBe jobctx.taskId.map(_.print)
      attrs.get(EventReception.StandardAttribute.ActionId) shouldBe jobctx.actionId.map(_.print)
      attrs.get(EventReception.StandardAttribute.CausationId).nonEmpty shouldBe true
      attrs.get(EventReception.StandardAttribute.SecurityLevel) shouldBe Some(base.security.level.value)
      attrs.get(EventReception.StandardAttribute.PrincipalId) shouldBe Some(base.security.principal.id.value)
      attrs.get(EventReception.StandardAttribute.EventName) shouldBe Some("context.bound.event")
      attrs.get(EventReception.StandardAttribute.EventKind) shouldBe Some("accepted")
      attrs.get(EventReception.StandardAttribute.SourceSubsystem) shouldBe Some("sample")
      attrs.get(EventReception.StandardAttribute.SourceComponent) shouldBe Some("publisher")
    }

    "route external-subsystem reception with async new-job same-saga policy" in {
      Given("subscription with rule-selected async new-job same-saga policy")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val jobengine = org.goldenport.cncf.job.InMemoryJobEngine.create()
      val calls = ArrayBuffer.empty[String]
      val parentjobs = ArrayBuffer.empty[Option[String]]
      val levels = ArrayBuffer.empty[SecurityLevel]
      val attrs = ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new _SecureRecordingDispatcher2(calls, levels, parentjobs, attrs)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        ingressSecurityResolver = IngressSecurityResolver.default,
        currentSubsystemName = Some("sales"),
        jobEngine = Some(jobengine)
      )
      reception.register(
        CmlEventDefinition(
          name = "order.approved",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("approved")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "order-fulfill",
          eventName = "order.approved",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "order.fulfill"
        )
      )
      reception.registerRule(
        EventReceptionRule(
          name = "external-order-approved",
          condition = EventReceptionCondition(
            originBoundary = Some(EventOriginBoundary.ExternalSubsystem),
            eventName = Some("order.approved"),
            eventKind = Some("approved")
          ),
          policy = EventReceptionExecutionPolicy.AsyncNewJobSameSaga
        )
      )
      val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val parentjobid = JobId.generate()
      val parentctx = ExecutionContext.withJobContext(
        base,
        JobContext(
          jobId = Some(parentjobid),
          taskId = Some(TaskId.generate()),
          actionId = Some(ActionId.generate())
        )
      )
      given ExecutionContext = parentctx

      When("event is received via authorized entry")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "order.approved",
          kind = "approved",
          attributes = Map(
            "targetId" -> "o1",
            EventReception.StandardAttribute.SourceSubsystem -> "billing"
          )
        )
      )
      EventAwaitSupport.awaitVisible(calls.nonEmpty) shouldBe true

      Then("dispatch is executed through a new job task with selected policy metadata")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("order.fulfill")
      levels.nonEmpty shouldBe true
      attrs.head.get(EventReception.StandardAttribute.OriginBoundary) shouldBe Some("external-subsystem")
      attrs.head.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("external-order-approved")
      attrs.head.get(EventReception.StandardAttribute.ReceptionPolicy) shouldBe Some(EventReceptionExecutionPolicy.AsyncNewJobSameSaga.modeName)
      attrs.head.get(EventReception.StandardAttribute.PolicySource) shouldBe Some("explicit-rule")
      attrs.head.get(EventReception.StandardAttribute.SagaRelation) shouldBe Some("same-saga")
    }

    "drop duplicated replay event deterministically" in {
      Given("replay-tagged event with replayEventId")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "inventory.replayed",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("replayed")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "inventory-sync",
          eventName = "inventory.replayed",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "inventory.sync"
        )
      )

      When("the same replay event id is received twice")
      val first = reception.receive(
        ReceptionInput(
          name = "inventory.replayed",
          kind = "replayed",
          attributes = Map(
            "targetId" -> "i1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample",
            EventReception.StandardAttribute.Replay -> "true",
            EventReception.StandardAttribute.ReplayEventId -> "evt-1",
            EventReception.StandardAttribute.ReplaySequence -> "1"
          )
        )
      )
      val second = reception.receive(
        ReceptionInput(
          name = "inventory.replayed",
          kind = "replayed",
          attributes = Map(
            "targetId" -> "i1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample",
            EventReception.StandardAttribute.Replay -> "true",
            EventReception.StandardAttribute.ReplayEventId -> "evt-1",
            EventReception.StandardAttribute.ReplaySequence -> "2"
          )
        )
      )

      Then("the second event is dropped as replay-duplicate")
      first shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      second shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Dropped,
          dispatchedCount = 0,
          persisted = false,
          reason = Some("replay-duplicate")
        )
      )
      calls.toVector shouldBe Vector("inventory.sync")
    }

    "drop out-of-order replay event deterministically" in {
      Given("replay-tagged events in the same replay stream")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(calls),
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "billing.replayed",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("replayed")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "billing-sync",
          eventName = "billing.replayed",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "billing.sync"
        )
      )

      When("lower sequence arrives after higher sequence")
      val first = reception.receive(
        ReceptionInput(
          name = "billing.replayed",
          kind = "replayed",
          attributes = Map(
            "targetId" -> "b1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample",
            EventReception.StandardAttribute.Replay -> "true",
            EventReception.StandardAttribute.ReplayEventId -> "evt-10",
            EventReception.StandardAttribute.ReplayStream -> "stream-a",
            EventReception.StandardAttribute.ReplaySequence -> "10"
          )
        )
      )
      val second = reception.receive(
        ReceptionInput(
          name = "billing.replayed",
          kind = "replayed",
          attributes = Map(
            "targetId" -> "b1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample",
            EventReception.StandardAttribute.Replay -> "true",
            EventReception.StandardAttribute.ReplayEventId -> "evt-9",
            EventReception.StandardAttribute.ReplayStream -> "stream-a",
            EventReception.StandardAttribute.ReplaySequence -> "9"
          )
        )
      )

      Then("second event is dropped as replay-out-of-order")
      first shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      second shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Dropped,
          dispatchedCount = 0,
          persisted = false,
          reason = Some("replay-out-of-order")
        )
      )
      calls.toVector shouldBe Vector("billing.sync")
    }

    "route same-subsystem reception with default sync policy without creating a new job" in {
      Given("subscription without explicit rule or continuation and parent job context")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = ArrayBuffer.empty[String]
      val jobids = ArrayBuffer.empty[Option[String]]
      val parentids = ArrayBuffer.empty[Option[String]]
      val attrs = ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new _SecureRecordingDispatcher3(calls, jobids, parentids, attrs)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        currentSubsystemName = Some("accounting")
      )
      reception.register(
        CmlEventDefinition(
          name = "account.updated",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("updated")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "account-sync",
          eventName = "account.updated",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "account.sync"
        )
      )
      val parent = JobId.generate()
      val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val basejob = JobContext(
        jobId = Some(JobId.generate()),
        taskId = Some(TaskId.generate()),
        actionId = Some(ActionId.generate()),
        parentJobId = Some(parent)
      )
      given ExecutionContext = ExecutionContext.withJobContext(base, basejob)

      When("event is received via authorized entry")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "account.updated",
          kind = "updated",
          attributes = Map(
            "targetId" -> "a1",
            EventReception.StandardAttribute.SourceSubsystem -> "accounting"
          )
        )
      )

      Then("dispatch continues on same job context and default same-subsystem metadata is propagated")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("account.sync")
      jobids.flatten should contain(basejob.jobId.get.print)
      parentids.flatten should contain(parent.print)
      attrs.head.get(EventReception.StandardAttribute.ContinuationMode) shouldBe Some("same-job")
      attrs.head.get(EventReception.StandardAttribute.OriginBoundary) shouldBe Some("same-subsystem")
      attrs.head.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("default:same-subsystem")
      attrs.head.get(EventReception.StandardAttribute.ReceptionPolicy) shouldBe Some(EventReceptionExecutionPolicy.SameSubsystemDefault.modeName)
      attrs.head.get(EventReception.StandardAttribute.PolicySource) shouldBe Some("subsystem-default")
      attrs.head.get(EventReception.StandardAttribute.CorrelationId).nonEmpty shouldBe true
      attrs.head.get(EventReception.StandardAttribute.CausationId).nonEmpty shouldBe true
      attrs.head.get(EventReception.StandardAttribute.ParentJobId) shouldBe Some(parent.print)
    }

    "materialize compatibility-mapped async continuation as spawned job lineage" in {
      Given("same-subsystem subscription with legacy NewJob continuation")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val jobengine = org.goldenport.cncf.job.InMemoryJobEngine.create()
      val calls = ArrayBuffer.empty[String]
      val jobids = ArrayBuffer.empty[Option[String]]
      val parentids = ArrayBuffer.empty[Option[String]]
      val attrs = ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new _SecureRecordingDispatcher3(calls, jobids, parentids, attrs)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        currentSubsystemName = Some("inventory"),
        currentComponentName = Some("public-notice"),
        jobEngine = Some(jobengine)
      )
      reception.register(
        CmlEventDefinition(
          name = "notice.published",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("published")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "notice-sync",
          eventName = "notice.published",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "notice.sync",
          continuationMode = Some(EventContinuationMode.NewJob)
        )
      )
      val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val parentjob = JobId.generate()
      given ExecutionContext = ExecutionContext.withJobContext(
        base,
        JobContext(
          jobId = Some(parentjob),
          taskId = Some(TaskId.generate()),
          actionId = Some(ActionId.generate())
        )
      )

      When("event is received")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "notice.published",
          kind = "published",
          attributes = Map(
            "targetId" -> "n1",
            EventReception.StandardAttribute.SourceSubsystem -> "inventory",
            EventReception.StandardAttribute.SourceComponent -> "publisher"
          )
        )
      )
      EventAwaitSupport.awaitVisible(jobids.flatten.nonEmpty) shouldBe true

      Then("async child job preserves explicit compatibility diagnostics")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      val childJobId = JobId.parse(jobids.flatten.head).toOption.get
      val child = jobengine.query(childJobId).get
      calls.toVector shouldBe Vector("notice.sync")
      parentids.flatten should contain(parentjob.print)
      attrs.head.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("compatibility:new-job")
      attrs.head.get(EventReception.StandardAttribute.PolicySource) shouldBe Some("compatibility-mapping")
      attrs.head.get(EventReception.StandardAttribute.TargetComponent) shouldBe Some("public-notice")
      child.lineage.eventTriggered shouldBe true
      child.lineage.parentJobId shouldBe Some(parentjob.print)
      child.lineage.sourceComponent shouldBe Some("publisher")
      child.lineage.targetComponent shouldBe Some("public-notice")
      child.lineage.policySource shouldBe Some("compatibility-mapping")
      child.lineage.receptionRule shouldBe Some("compatibility:new-job")
      child.lineage.receptionPolicy shouldBe Some(EventReceptionExecutionPolicy.AsyncNewJobSameSaga.modeName)
    }

    "select more specific external rule and mark new-saga async dispatch" in {
      Given("external rules with different specificity")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val jobengine = org.goldenport.cncf.job.InMemoryJobEngine.create()
      val calls = ArrayBuffer.empty[String]
      val parentjobs = ArrayBuffer.empty[Option[String]]
      val attrs = ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new _SecureRecordingDispatcher2(calls, ArrayBuffer.empty, parentjobs, attrs)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = dispatcher,
        currentSubsystemName = Some("inventory"),
        jobEngine = Some(jobengine)
      )
      reception.register(
        CmlEventDefinition(
          name = "stock.adjusted",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("adjusted")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "stock-sync",
          eventName = "stock.adjusted",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "stock.sync"
        )
      )
      reception.registerRule(
        EventReceptionRule(
          name = "external-default",
          condition = EventReceptionCondition(
            originBoundary = Some(EventOriginBoundary.ExternalSubsystem),
            eventName = Some("stock.adjusted")
          ),
          policy = EventReceptionExecutionPolicy.AsyncNewJobSameSaga
        )
      )
      reception.registerRule(
        EventReceptionRule(
          name = "external-priority-csv",
          condition = EventReceptionCondition(
            originBoundary = Some(EventOriginBoundary.ExternalSubsystem),
            eventName = Some("stock.adjusted"),
            eventKind = Some("adjusted"),
            selectors = Map("source" -> "csv")
          ),
          policy = EventReceptionExecutionPolicy.AsyncNewJobNewSaga
        )
      )
      given ExecutionContext = _shared_event_context(recorder, store)

      When("receiving selector-specific external event")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "stock.adjusted",
          kind = "adjusted",
          attributes = Map(
            "targetId" -> "s1",
            "source" -> "csv",
            EventReception.StandardAttribute.SourceSubsystem -> "backoffice"
          )
        )
      )
      EventAwaitSupport.awaitVisible(calls.nonEmpty) shouldBe true

      Then("the more specific rule wins and new-saga metadata is recorded")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      attrs.head.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("external-priority-csv")
      attrs.head.get(EventReception.StandardAttribute.ReceptionPolicy) shouldBe Some(EventReceptionExecutionPolicy.AsyncNewJobNewSaga.modeName)
      attrs.head.get(EventReception.StandardAttribute.PolicySource) shouldBe Some("explicit-rule")
      attrs.head.get(EventReception.StandardAttribute.SagaRelation) shouldBe Some("new-saga")
      attrs.head.get(EventReception.StandardAttribute.ContinuationMode) shouldBe Some("new-job")
    }

    "persist same-subsystem sync event after inline dispatch with framework-owned history" in {
      Given("persistent same-subsystem sync reception with inline dispatch recorder")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val dispatchentries = ArrayBuffer.empty[Vector[String]]
      val attrs = ArrayBuffer.empty[Map[String, String]]
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _SecureRecordingDispatcherWithRecorder(dispatchentries, attrs, recorder),
        currentSubsystemName = Some("public-notice"),
        currentComponentName = Some("notice-admin")
      )
      reception.register(
        CmlEventDefinition(
          name = "notice.published",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("published")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "notice-admin-sync",
          eventName = "notice.published",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "notice.admin.sync"
        )
      )
      given ExecutionContext = _shared_event_context(recorder, store)

      When("persistent event is received through same-subsystem sync path")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "notice.published",
          kind = "published",
          attributes = Map(
            "targetId" -> "notice-1",
            EventReception.StandardAttribute.SourceSubsystem -> "public-notice",
            EventReception.StandardAttribute.SourceComponent -> "public-notice"
          ),
          persistent = true
        )
      )

      Then("dispatch happens before commit and final stored event contains framework metadata and history")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = true
        )
      )
      dispatchentries.headOption.getOrElse(Vector.empty) should not contain "UnitOfWork.commit"
      val stored = store.query(EventStore.Query(name = Some("notice.published"))).toOption.getOrElse(Vector.empty)
      stored.size shouldBe 1
      val record = stored.head
      record.attributes.get(EventReception.StandardAttribute.OriginBoundary) shouldBe Some("same-subsystem")
      record.attributes.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("default:same-subsystem")
      record.attributes.get(EventReception.StandardAttribute.ReceptionPolicy) shouldBe Some(EventReceptionExecutionPolicy.SameSubsystemDefault.modeName)
      record.attributes.get(EventReception.StandardAttribute.PolicySource) shouldBe Some("subsystem-default")
      record.attributes.get(EventReception.StandardAttribute.TargetComponent) shouldBe Some("notice-admin")
      record.attributes.get(EventReception.StandardAttribute.EventHistoryFormat) shouldBe Some("delta-trail")
      record.attributes.get(EventReception.StandardAttribute.EventHistoryOverflow) shouldBe Some("fail-fast")
      record.attributes.get(EventReception.StandardAttribute.EventHistoryCount) shouldBe Some("3")
      record.attributes.get(EventReception.StandardAttribute.EventHistory).exists(_.contains("source{")) shouldBe true
      record.attributes.get(EventReception.StandardAttribute.EventHistory).exists(_.contains("reception{")) shouldBe true
      record.attributes.get(EventReception.StandardAttribute.EventHistory).exists(_.contains("dispatch{")) shouldBe true
      recorder.entries shouldBe Vector(
        "UnitOfWork.prepare",
        "EventEngine.prepare",
        "UnitOfWork.commit",
        "EventEngine.commit",
        "DataStore.commit"
      )
    }

    "rollback same-subsystem sync reception when inline dispatch fails" in {
      Given("persistent same-subsystem sync reception with failing inline dispatch")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _FailingSecureDispatcher,
        currentSubsystemName = Some("public-notice"),
        currentComponentName = Some("notice-admin")
      )
      reception.register(
        CmlEventDefinition(
          name = "notice.failed",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("published")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "notice-admin-sync",
          eventName = "notice.failed",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "notice.admin.sync"
        )
      )
      given ExecutionContext = _shared_event_context(recorder, store)

      When("dispatch fails")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "notice.failed",
          kind = "published",
          attributes = Map(
            "targetId" -> "notice-2",
            EventReception.StandardAttribute.SourceSubsystem -> "public-notice"
          ),
          persistent = true
        )
      )

      Then("the failure is returned and no event is committed")
      result shouldBe a[Consequence.Failure[_]]
      store.query(EventStore.Query(name = Some("notice.failed"))).toOption.getOrElse(Vector.empty) shouldBe Vector.empty
      recorder.entries should not contain "UnitOfWork.commit"
      recorder.entries should not contain "EventEngine.commit"
    }

    "fail fast when event history exceeds configured cap" in {
      Given("same-subsystem sync reception with oversized target metadata")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _SecureRecordingDispatcher(ArrayBuffer.empty, ArrayBuffer.empty),
        currentSubsystemName = Some("public-notice"),
        currentComponentName = Some("notice-admin")
      )
      reception.register(
        CmlEventDefinition(
          name = "notice.oversized",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("published")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "notice-admin-sync",
          eventName = "notice.oversized",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "notice.admin.sync"
        )
      )
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val oversized = "x" * 5000

      When("history append would exceed the cap")
      val result = reception.receiveAuthorized(
        ReceptionInput(
          name = "notice.oversized",
          kind = "published",
          attributes = Map(
            "targetId" -> oversized,
            EventReception.StandardAttribute.SourceSubsystem -> "public-notice"
          ),
          persistent = true
        )
      )

      Then("the reception fails before commit and nothing is persisted")
      result shouldBe a[Consequence.Failure[_]]
      store.query(EventStore.Query(name = Some("notice.oversized"))).toOption.getOrElse(Vector.empty) shouldBe Vector.empty
      recorder.entries should not contain "UnitOfWork.commit"
    }

    "fail policy selection when subscription event has no source boundary information" in {
      Given("subscription-based event reception without source subsystem or external ingress marker")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(ArrayBuffer.empty),
        currentSubsystemName = Some("sample")
      )
      reception.register(
        CmlEventDefinition(
          name = "person.synced",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("synced")
        )
      )
      reception.registerSubscription(
        CmlSubscriptionDefinition(
          name = "person-sync",
          eventName = "person.synced",
          route = DispatchRoute.Unicast,
          target = Some("targetId"),
          actionName = "person.sync"
        )
      )

      When("receiving event without boundary metadata")
      val result = reception.receive(
        ReceptionInput(
          name = "person.synced",
          kind = "synced",
          attributes = Map("targetId" -> "p1")
        )
      )

      Then("selection fails deterministically instead of guessing a boundary")
      result shouldBe a[Consequence.Failure[_]]
    }

    "materialize no-match event as ephemeral job when jobEngine is provided" in {
      Given("a known event whose selector does not match and recording job engine")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val recordingengine = new _RecordingJobEngine
      val reception = EventReception.default(
        eventBus = bus,
        dispatcher = new _RecordingDispatcher(ArrayBuffer.empty),
        jobEngine = Some(recordingengine)
      )
      reception.register(
        CmlEventDefinition(
          name = "order.nomatch",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("accepted"),
          selectors = Map("source" -> "crm")
        )
      )

      When("receiving non-target input")
      val result = reception.receive(
        ReceptionInput(
          name = "order.nomatch",
          kind = "accepted",
          attributes = Map("source" -> "erp")
        )
      )

      Then("no-match is dropped and Ephemeral Job is submitted")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Dropped,
          dispatchedCount = 0,
          persisted = false,
          reason = Some("non-target")
        )
      )
      recordingengine.lastOption.map(_.persistence) shouldBe Some(JobPersistencePolicy.Ephemeral)
    }
  }

  private final class _RecordingDispatcher(
    calls: ArrayBuffer[String]
  ) extends ActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      val _ = event
      calls += actionName
      Consequence.unit
    }
  }

  private final class _SecureRecordingDispatcher(
    calls: ArrayBuffer[String],
    levels: ArrayBuffer[SecurityLevel]
  ) extends SecureActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      val _ = event
      calls += actionName
      Consequence.unit
    }

    def dispatchActionAuthorized(
      actionName: String,
      event: DomainEvent
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      val _ = event
      calls += actionName
      levels += ctx.security.level
      Consequence.unit
    }
  }

  private final class _SecureRecordingDispatcher2(
    calls: ArrayBuffer[String],
    levels: ArrayBuffer[SecurityLevel],
    parentjobs: ArrayBuffer[Option[String]],
    attrs: ArrayBuffer[Map[String, String]]
  ) extends SecureActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      calls += actionName
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }

    def dispatchActionAuthorized(
      actionName: String,
      event: DomainEvent
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      calls += actionName
      levels += ctx.security.level
      parentjobs += ctx.jobContext.parentJobId.map(_.print)
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }
  }

  private def _await(
    ready: () => Boolean,
    timeoutMillis: Long = 3000L
  ): Unit = {
    if (!EventAwaitSupport.awaitVisible(ready(), timeoutMillis)) {
      fail("timeout waiting async event dispatch")
    }
  }

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = ArrayBuffer.empty[String]
    def entries: Vector[String] = _entries.toVector
    def record(entry: String): Unit = _entries += entry
  }

  private final class _SecureRecordingDispatcher3(
    calls: ArrayBuffer[String],
    jobids: ArrayBuffer[Option[String]],
    parentids: ArrayBuffer[Option[String]],
    attrs: ArrayBuffer[Map[String, String]]
  ) extends SecureActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      calls += actionName
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }

    def dispatchActionAuthorized(
      actionName: String,
      event: DomainEvent
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      calls += actionName
      jobids += ctx.jobContext.jobId.map(_.print)
      parentids += ctx.jobContext.parentJobId.map(_.print)
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }
  }

  private final class _SecureRecordingDispatcherWithRecorder(
    entriesAtDispatch: ArrayBuffer[Vector[String]],
    attrs: ArrayBuffer[Map[String, String]],
    recorder: _InMemoryCommitRecorder
  ) extends SecureActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      val _ = actionName
      entriesAtDispatch += recorder.entries
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }

    def dispatchActionAuthorized(
      actionName: String,
      event: DomainEvent
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      val _ = actionName
      val _ = ctx
      entriesAtDispatch += recorder.entries
      event match {
        case e: ReceptionDomainEvent => attrs += e.attributes
        case _ => ()
      }
      Consequence.unit
    }
  }

  private final class _FailingSecureDispatcher extends SecureActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      val _ = actionName
      val _ = event
      Consequence.operationInvalid("event.dispatch", "forced failure")
    }

    def dispatchActionAuthorized(
      actionName: String,
      event: DomainEvent
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      val _ = actionName
      val _ = event
      val _ = ctx
      Consequence.operationInvalid("event.dispatch", "forced failure")
    }
  }

  private final class _RecordingJobEngine extends JobEngine {
    @volatile private var _options: Vector[JobSubmitOption] = Vector.empty

    def lastOption: Option[JobSubmitOption] =
      _options.lastOption

    def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId =
      submit(tasks, ctx, JobSubmitOption())

    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): JobId = {
      val _ = tasks
      val _ = ctx
      _options = _options :+ option
      JobId.generate()
    }

    def getStatus(jobId: JobId): Option[JobStatus] = None
    def getResult(jobId: JobId): Option[JobResult] = None
    def control(
      jobId: JobId,
      request: JobControlRequest,
      policy: JobControlPolicy
    )(using ExecutionContext): Consequence[JobControlResponse] =
      Consequence.notImplemented("not used in this spec")
    def query(jobId: JobId): Option[JobQueryReadModel] = None
    def queryTasks(jobId: JobId, offset: Int, limit: Int): Option[JobTaskPage] = None
    def queryTimeline(jobId: JobId, offset: Int, limit: Int): Option[JobTimelinePage] = None
  }

  private def _shared_event_context(
    recorder: CommitRecorder,
    store: EventStore
  ): ExecutionContext = {
    val base = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
    val driver = FakeHttpDriver.okText("nop")
    val consequenceinterpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in EventReceptionSpec")
    }
    final class RuntimeHolder {
      private var _uow: Option[UnitOfWork] = None
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val runtime = new org.goldenport.cncf.context.RuntimeContext(
        core = org.goldenport.cncf.context.RuntimeContext.core(
          name = "event-reception-spec-runtime",
          parent = None,
          observabilityContext = base.observability,
          httpDriverOption = Some(driver)
        ),
        unitOfWorkSupplier = () => _uow.getOrElse {
          throw new IllegalStateException("UnitOfWork has not been bound")
        },
        unitOfWorkInterpreterFn = consequenceinterpreter,
        commitAction = uow => {
          val _ = uow.commit()
          ()
        },
        abortAction = uow => {
          val _ = uow.rollback()
          ()
        },
        disposeAction = _ => (),
        token = "event-reception-spec-runtime"
      )
      lazy val context: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
      def bind(): ExecutionContext = {
        _uow = Some(new UnitOfWork(context, eventengine, recorder))
        context
      }
    }
    new RuntimeHolder().bind()
  }
}
