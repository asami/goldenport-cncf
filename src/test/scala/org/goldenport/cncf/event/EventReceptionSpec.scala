package org.goldenport.cncf.event

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext, SecurityLevel}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.job.{ActionId, JobContext, JobId, TaskId}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
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
      val reception = EventReception.default(bus, dispatcher)

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
      val reception = EventReception.default(bus, new _RecordingDispatcher(calls))

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
      val reception = EventReception.default(bus, new _RecordingDispatcher(calls))
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
      val reception = EventReception.default(bus, new _RecordingDispatcher(calls))
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
      val reception = EventReception.default(bus, new _RecordingDispatcher(calls))
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
      val reception = EventReception.default(bus, new _RecordingDispatcher(calls))
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
          attributes = Map("targetId" -> "p1")
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
      val reception = EventReception.default(bus, dispatcher)
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
          attributes = Map("targetId" -> "p1")
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
      val jobctx = JobContext(
        jobId = Some(JobId.generate()),
        taskId = Some(TaskId.generate()),
        actionId = Some(ActionId.generate())
      )
      given ExecutionContext = ExecutionContext.withJobContext(base, jobctx)

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

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = ArrayBuffer.empty[String]
    def entries: Vector[String] = _entries.toVector
    def record(entry: String): Unit = _entries += entry
  }
}
