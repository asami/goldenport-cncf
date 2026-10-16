package org.goldenport.cncf.event

import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.context.ExecutionContextId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.observation.{Cause, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 *  version Apr. 14, 2026
 *  version Jul. 15, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventBusSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "EventBus" should {
    "dispatch finalized native records without appending again and match subscriptions using selected persistence" in {
      Given("an event bus with persistence-filtered subscriptions and finalized durable and transient records")
      given ExecutionContext = ExecutionContext.create()
      val store = EventStore.inMemory
      val bus = EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = store))
      val trace = ArrayBuffer.empty[String]
      Vector(true, false).foreach { persistent =>
        bus.register(EventSubscription(s"selected-$persistent", eventName = Some("native"), kind = Some("fact"),
          persistentOnly = Some(persistent), handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = event match {
              case FinalizedRecordDomainEvent(record) => trace += s"$persistent:${record.sequence}"; Consequence.unit
              case _ => Consequence.stateConflict("Expected exact finalized record")
            }
          }))
      }
      val time = Instant.parse("2026-10-01T00:00:00Z")
      val durable = EventRecord(EventId("test", "native", Some(time), Some("durable")), "native", "fact",
        Map("value" -> "exact"), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional, 7L)
      val transient = durable.copy(id = EventId("test", "native", Some(time), Some("transient")), persistent = false, sequence = 0L)
      When("the durable finalized record is dispatched")
      val durableresult = bus.dispatchCommittedRecordC(durable)
      Then("the result retains its selected persistence")
      durableresult.toOption.get.persisted shouldBe true
      When("the transient finalized record is dispatched")
      val transientresult = bus.dispatchCommittedRecordC(transient)
      Then("only matching subscriptions run and no record is appended again")
      transientresult.toOption.get.persisted shouldBe false
      trace.toVector shouldBe Vector("true:7", "false:0")
      store.query(EventStore.Query()).toOption.get shouldBe empty
      When("a durable record has no durable sequence")
      val missingsequence = bus.dispatchCommittedRecordC(durable.copy(sequence = 0L))
      Then("dispatch is rejected")
      missingsequence.isSuccess shouldBe false
      When("a transient record has a durable sequence")
      val unexpectedsequence = bus.dispatchCommittedRecordC(transient.copy(sequence = 1L))
      Then("dispatch is rejected")
      unexpectedsequence.isSuccess shouldBe false
      When("no finalized record is supplied")
      val absentrecord = bus.dispatchCommittedRecordC(null)
      Then("dispatch is rejected without invoking another handler")
      absentrecord.isSuccess shouldBe false
      trace.size shouldBe 2
    }

    "register and dispatch in deterministic priority/order" in {
      Given("event bus with multiple subscriptions for same event")
      val recorder = new InMemoryCommitRecorder
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
      val recorder = new InMemoryCommitRecorder
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
      val recorder = new InMemoryCommitRecorder
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
      val recorder = new InMemoryCommitRecorder
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
              Consequence.operationInvalid("handler failed")
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
          c.observation.cause.kind shouldBe Some(Cause.Kind.Inconsistency)
          c.previous should not be empty
        case _ =>
          fail("expected failure")
      }
    }

    "deny publish for user privilege in authorized entry point" in {
      Given("event bus and user execution context")
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
      val recorder = new InMemoryCommitRecorder
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
      val recorder = new InMemoryCommitRecorder
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

    "preserve the authorized execution context for dispatch handlers" in {
      Given("an authorized event handler that records its execution context")
      given context: ExecutionContext =
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val recorder = new InMemoryCommitRecorder
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(recorder), recorder, eventstore)
      val bus = EventBus.default(eventengine)
      var dispatchedcontext: Option[ExecutionContext] = None
      bus.register(
        EventSubscription(
          name = "authorized-context-sub",
          eventName = Some("authorized-context-event"),
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              val _ = event
              Consequence.notImplemented("authorized dispatch was not used")
            }

            override def dispatchAuthorized(
              event: DomainEvent
            )(using current: ExecutionContext): Consequence[Unit] = {
              val _ = event
              dispatchedcontext = Some(current)
              Consequence.unit
            }
          }
        )
      )

      When("the event is published through the authorized entry point")
      val result = bus.publishAuthorized(
        _action_event("authorized-context-event"),
        EventPublishOption(persistent = false)
      )

      Then("the handler receives the caller's execution context")
      result shouldBe Consequence.success(EventPublishResult(1, persisted = false))
      dispatchedcontext shouldBe Some(context)
    }

    "deny subscription introspection for user privilege" in {
      Given("event bus and user execution context")
      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
      val recorder = new InMemoryCommitRecorder
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
      val recorder = new InMemoryCommitRecorder
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

  "EB1 Authorized persistent refusal" should _persistence_scenario("EB1", "E2,E6") {
    "retain default authorization Failure without persistence dispatch observation or provider work" in {
      Given("E2,E6: real default bus/native store/handler and a user context without event capabilities")
      import EventPersistenceOperationalAuditTestSupport.{NativeGraph, RecordingBackend, instant, withBackend}
      val graph = new NativeGraph
      val user = ExecutionContext.withSecurityContext(graph.executionContext,
        ExecutionContext.test(SecurityContext.Privilege.User).security)
      val event = ReceptionDomainEvent("authorization-refusal", "original", Map.empty, Map.empty, instant)
      graph.bus.register(EventSubscription("native", handler = graph.handler("native")))
      val backend = new RecordingBackend
      val before = graph.observations
      When("the persistent request enters the unchanged default authorized publish path")
      val result = withBackend(backend)(graph.bus.publishAuthorized(event, EventPublishOption(persistent = true))(using user))
      Then("the deterministic operation-illegal Failure precedes all append dispatch audit clock and ID work")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          conclusion.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Illegal
        case _ => fail("default publish authorization should refuse")
      }
      graph.observations shouldBe before
      graph.store.results shouldBe empty
      graph.store.stored shouldBe empty
      graph.received shouldBe empty
      backend.events shouldBe empty
    }
  }

  "EB2 Excluded context and persistence paths" should _persistence_scenario("EB2", "E2,E6") {
    "preserve context-free persistence and authorized nonpersistent dispatch with no implicit audit fallback" in {
      Given("E2,E6: native bus with explicit original factory and native handler preserving original authorized context")
      import EventPersistenceOperationalAuditTestSupport.{NativeGraph, RecordingBackend, instant, withBackend}
      val graph = new NativeGraph
      val event = ReceptionDomainEvent("excluded-path", "original", Map("original" -> "payload"), Map("original" -> "attribute"), instant)
      graph.bus.register(EventSubscription("native", handler = graph.handler("native")))
      val backend = new RecordingBackend
      val before = graph.observations
      When("context-free persistent publish uses its supplied factory and authorized nonpersistent publish uses the original context")
      val results = withBackend(backend) {
        val persistent = graph.bus.publish(event, EventPublishOption(persistent = true))
        val ephemeral = graph.bus.publishAuthorized(event, EventPublishOption(persistent = false))(using graph.executionContext)
        (persistent, ephemeral)
      }
      Then("public results and canonical persistence remain original and handlers receive their exact applicable context without any audit")
      results shouldBe ((Consequence.success(EventPublishResult(1, persisted = true)),
        Consequence.success(EventPublishResult(1, persisted = false))))
      graph.store.stored.size shouldBe 1
      val stored = graph.store.stored.head
      stored.name shouldBe event.name
      stored.kind shouldBe event.kind
      stored.payload shouldBe event.payload
      stored.attributes shouldBe event.attributes
      stored.createdAt shouldBe event.occurredAt
      stored.lane shouldBe EventLane.NonTransactional
      (graph.store.results.head.toOption.get.head eq stored) shouldBe true
      graph.store.appendCalls.get() shouldBe 1
      graph.store.readCalls.get() shouldBe 0
      graph.clock.calls.get() shouldBe before._1
      graph.ids.calls.get() should be > before._2
      graph.received.size shouldBe 2
      graph.received.foreach { case (_, source, _) => (source.asInstanceOf[AnyRef] eq event) shouldBe true }
      graph.received.head._3 shouldBe None
      (graph.received.last._3.get eq graph.executionContext) shouldBe true
      backend.events shouldBe empty
    }
  }

  private def _persistence_scenario(example: String, rules: String) =
    afterWord(s"in spec:event-persistence-operational-audit, example:$example, rules:$rules, phase:69.7, slice:C7")

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

  private final class InMemoryCommitRecorder extends CommitRecorder {
    def record(message: String): Unit = {
      val _ = message
    }
  }
}
