package org.goldenport.cncf.event

import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.context.ExecutionContextId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventBusSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventBus" should {
    "register and dispatch in deterministic priority/order" in {
      Given("event bus with multiple subscriptions for same event")
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val trace = ArrayBuffer.empty[String]
      val e = _action_event("ping")

      bus.register(
        EventSubscription(
          name = "s3",
          eventName = Some("ping"),
          priority = 10,
          handler = _handler("s3", trace)
        )
      )
      bus.register(
        EventSubscription(
          name = "s1",
          eventName = Some("ping"),
          priority = 1,
          handler = _handler("s1", trace)
        )
      )
      bus.register(
        EventSubscription(
          name = "s2",
          eventName = Some("ping"),
          priority = 1,
          handler = _handler("s2", trace)
        )
      )

      When("publishing an ephemeral event")
      val result = bus.publish(e, EventPublishOption(persistent = false))

      Then("dispatch order is deterministic: priority asc then registration order")
      result shouldBe Consequence.success(EventPublishResult(3, persisted = false))
      trace.toVector shouldBe Vector("s1", "s2", "s3")
    }

    "dispatch ephemeral event without persistence dependency" in {
      Given("event bus with ephemeral publish")
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val trace = ArrayBuffer.empty[String]
      val e = _action_event("ephemeral-event")
      bus.register(
        EventSubscription(
          name = "ephemeral-sub",
          eventName = Some("ephemeral-event"),
          handler = _handler("ephemeral-sub", trace)
        )
      )

      When("publishing as persistent=false")
      val result = bus.publish(e, EventPublishOption(persistent = false))

      Then("dispatch succeeds and event store remains unchanged")
      result shouldBe Consequence.success(EventPublishResult(1, persisted = false))
      trace.toVector shouldBe Vector("ephemeral-sub")
      eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty) shouldBe Vector.empty
    }

    "persist and dispatch when publish is persistent" in {
      Given("event bus with persistent publish")
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val trace = ArrayBuffer.empty[String]
      val e = _action_event("persistent-event")
      bus.register(
        EventSubscription(
          name = "persistent-sub",
          eventName = Some("persistent-event"),
          handler = _handler("persistent-sub", trace)
        )
      )

      When("publishing as persistent=true")
      val result = bus.publish(e, EventPublishOption(persistent = true))

      Then("event is persisted and dispatched")
      result shouldBe Consequence.success(EventPublishResult(1, persisted = true))
      trace.toVector shouldBe Vector("persistent-sub")
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records.size shouldBe 1
      records.head.lane shouldBe EventLane.NonTransactional
    }

    "map dispatch failure to structured consequence failure" in {
      Given("event bus with failing handler")
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val e = _action_event("failure-event")
      bus.register(
        EventSubscription(
          name = "bad-sub",
          eventName = Some("failure-event"),
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              val _ = event
              Consequence.failure("handler failed")
            }
          }
        )
      )

      When("publishing event")
      val result = bus.publish(e, EventPublishOption(persistent = false))

      Then("dispatch failure is mapped to taxonomy/facets")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Invalid
        case _ =>
          fail("expected failure")
      }
    }

    "deny publish for user privilege in authorized entry point" in {
      Given("event bus and user execution context")
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val e = _action_event("policy-event")
      bus.register(
        EventSubscription(
          name = "policy-sub",
          eventName = Some("policy-event"),
          handler = _handler("policy-sub", ArrayBuffer.empty)
        )
      )

      When("publishing via authorized entry point")
      val result = bus.publishAuthorized(e, EventPublishOption(persistent = false))

      Then("request is denied with deterministic taxonomy")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Illegal
        case _ =>
          fail("expected failure")
      }
    }

    "allow publish for content-manager privilege in authorized entry point" in {
      Given("event bus and content-manager execution context")
      given ExecutionContext =
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      val trace = ArrayBuffer.empty[String]
      val e = _action_event("policy-allow-event")
      bus.register(
        EventSubscription(
          name = "policy-allow-sub",
          eventName = Some("policy-allow-event"),
          handler = _handler("policy-allow-sub", trace)
        )
      )

      When("publishing via authorized entry point")
      val result = bus.publishAuthorized(e, EventPublishOption(persistent = false))

      Then("publish and dispatch succeed")
      result shouldBe Consequence.success(EventPublishResult(1, persisted = false))
      trace.toVector shouldBe Vector("policy-allow-sub")
    }

    "deny subscription introspection for user privilege" in {
      Given("event bus and user execution context")
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      bus.register(
        EventSubscription(
          name = "introspection-sub",
          handler = _handler("introspection-sub", ArrayBuffer.empty)
        )
      )

      When("reading visible subscriptions")
      val result = bus.subscriptionsVisible()

      Then("access is denied")
      result shouldBe a[Consequence.Failure[_]]
    }

    "allow subscription introspection for content-manager privilege" in {
      Given("event bus and content-manager execution context")
      given ExecutionContext =
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val recorder = new _InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      bus.register(
        EventSubscription(
          name = "introspection-sub-allow",
          handler = _handler("introspection-sub-allow", ArrayBuffer.empty)
        )
      )

      When("reading visible subscriptions")
      val result = bus.subscriptionsVisible()

      Then("access succeeds")
      result shouldBe a[Consequence.Success[_]]
      result.toOption.getOrElse(Vector.empty).map(_.name) shouldBe Vector("introspection-sub-allow")
    }
  }

  private def _action_event(name: String): ActionEvent =
    ActionEvent(
      executionContextId = ExecutionContextId.generate(),
      actionName = name,
      result = ActionResult.Succeeded,
      reason = None,
      occurredAt = Instant.now()
    )

  private def _handler(label: String, trace: ArrayBuffer[String]): EventDispatchHandler =
    new EventDispatchHandler {
      def dispatch(event: DomainEvent): Consequence[Unit] = {
        val _ = event
        trace += label
        Consequence.unit
      }
    }

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    def record(message: String): Unit = {
      val _ = message
    }
  }
}
