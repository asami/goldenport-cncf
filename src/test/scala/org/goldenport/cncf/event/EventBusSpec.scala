package org.goldenport.cncf.event

import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
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
