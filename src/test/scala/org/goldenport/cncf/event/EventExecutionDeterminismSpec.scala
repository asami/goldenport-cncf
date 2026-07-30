package org.goldenport.cncf.event

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.statemachine.TransitionEvent
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventExecutionDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Event execution capabilities" should {
    "derive reception time and saga identity from the selected execution profile" in {
      Given("generated fixed execution instants and deterministic ID streams")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _receive(instant, "reception-seed")
        val right = _receive(instant, "reception-seed")

        left.occurredAt == instant &&
        right.occurredAt == instant &&
        left.attributes.get(EventReception.StandardAttribute.EventOccurredAt).contains(instant.toString) &&
        left.attributes.get(EventReception.StandardAttribute.SagaId) ==
          right.attributes.get(EventReception.StandardAttribute.SagaId)
      }

      When("equivalent event receptions are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("their semantic time and generated saga boundary are replay-stable")
      checked.passed shouldBe true
    }

    "derive transition lifecycle time and EventId from the selected execution profile" in {
      Given("generated fixed execution instants and deterministic ID streams")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _transition_event(instant, "transition-seed")
        val right = _transition_event(instant, "transition-seed")

        left.occurredAt == instant &&
        right.occurredAt == instant &&
        left.id == right.id &&
        left.id.timestamp.contains(instant) &&
        left.id.entropy.nonEmpty
      }

      When("equivalent transition events are replayed")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("their lifecycle timestamp and EventId are replay-stable")
      checked.passed shouldBe true
    }

    "materialize transaction Event records from the invocation execution profile" in {
      Given("generated fixed execution instants and deterministic invocation ID streams")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _commit_records(instant, "event-record-seed")
        val right = _commit_records(instant, "event-record-seed")

        left.map(_.id) == right.map(_.id) &&
        left.map(_.createdAt).forall(_ == instant) &&
        left.map(_.id).distinct.size == 2 &&
        left.map(_.id.major).forall(_ == IdGenerationContext.DEFAULT_NAMESPACE.major)
      }

      When("equivalent transactions are committed through UnitOfWork")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("Event records are replay-stable and remain distinct within one transaction")
      checked.passed shouldBe true
    }

    "use the authorized caller execution profile for persistent EventBus records" in {
      Given("an EventEngine fallback profile distinct from the authorized caller profile")
      val instant = Instant.parse("2026-07-15T12:00:00Z")
      val callerclock = Clock.fixed(instant, ZoneOffset.UTC)
      val callerids = IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("caller", "event"),
        callerclock,
        "caller-seed"
      )
      val base = ExecutionContext.create(callerclock)
      given ExecutionContext = ExecutionContext.withIdGenerationContext(base, callerids)
      val fallbackclock = Clock.fixed(instant.minusSeconds(60L), ZoneOffset.UTC)
      val fallbackfactory = EventRecordFactory(
        fallbackclock,
        IdGenerationContext.deterministic(
          IdGenerationContext.IdNamespace("fallback", "event"),
          fallbackclock,
          "fallback-seed"
        )
      )
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(
        DataStore.noop(),
        eventstore = eventstore,
        recordfactory = fallbackfactory
      )
      val eventbus = EventBus.default(eventengine)

      When("a persistent event is published through the authorized caller boundary")
      val published = eventbus.publishAuthorized(
        ReceptionDomainEvent(
          "profile.checked",
          "checked",
          payload = Map.empty,
          attributes = Map.empty,
          occurredAt = instant
        ),
        EventPublishOption(persistent = true),
        EventPolicyEngine.internal
      )

      Then("the stored Event identity comes from the caller capability")
      published.toOption.map(_.persisted) shouldBe Some(true)
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records should have size 1
      records.head.id.major shouldBe "caller"
      records.head.id.minor shouldBe "event"
      records.head.createdAt shouldBe instant
    }

    "fix generic Event record time before transaction commit" in {
      Given("a mutable execution clock and one staged generic Event")
      val stagedat = Instant.parse("2026-07-15T13:00:00Z")
      val clock = new MutableClock(stagedat)
      val factory = EventRecordFactory(
        clock,
        IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE, clock, "stage-time-seed")
      )
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(
        DataStore.noop(),
        eventstore = eventstore,
        recordfactory = factory
      )
      val tx = org.goldenport.cncf.unitofwork.TransactionContext.create(
        ExecutionContext.create(clock).transactionContext
      )

      When("the Event is staged before logical time advances and the transaction commits")
      eventengine.stage(Vector(StoredEvent("staged")), factory)
      eventengine.prepare(tx)
      clock.set(stagedat.plusSeconds(300L))
      eventengine.commit(tx)

      Then("the persisted record retains the stage-time timestamp")
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records should have size 1
      records.head.createdAt shouldBe stagedat
      records.head.id.timestamp shouldBe Some(stagedat)
    }
  }

  private def _commit_records(
    instant: Instant,
    seed: String
  ): Vector[EventRecord] = {
    val ctx = _execution_context(instant, seed)
    val fallbackclock = Clock.fixed(instant.minusSeconds(60L), ZoneOffset.UTC)
    val fallbackfactory = EventRecordFactory(
      fallbackclock,
      IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("fallback", "event"),
        fallbackclock,
        "fallback-seed"
      )
    )
    val eventstore = EventStore.inMemory
    val eventengine = EventEngine.noop(
      DataStore.noop(),
      eventstore = eventstore,
      recordfactory = fallbackfactory
    )
    val unitofwork = new UnitOfWork(ctx, eventengine)
    val committed = unitofwork.commit(Vector(StoredEvent("first"), StoredEvent("second")))
    committed.toOption.toVector.flatMap(_ =>
      eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
    )
  }

  private def _receive(
    instant: Instant,
    seed: String
  ): ReceptionDomainEvent = {
    given ExecutionContext = _execution_context(instant, seed)
    val eventstore = EventStore.inMemory
    val eventengine = EventEngine.noop(DataStore.noop(), eventstore = eventstore)
    val bus = EventBus.default(eventengine)
    val received = ArrayBuffer.empty[ReceptionDomainEvent]
    val dispatcher = new ActionCallDispatcher {
      def dispatchAction(actionname: String, event: DomainEvent): Consequence[Unit] = {
        val _ = actionname
        val _ = event
        Consequence.unit
      }
    }
    val reception = EventReception.default(
      eventBus = bus,
      dispatcher = dispatcher,
      currentSubsystemName = Some("determinism"),
      currentComponentName = Some("event-source")
    )
    reception.register(CmlEventDefinition(
      name = "determinism.checked",
      category = CmlEventCategory.NonActionEvent,
      kind = Some("checked")
    ))
    reception.registerDirectListener(new DirectEventListener {
      def onEvent(
        event: ReceptionDomainEvent,
        definitions: Vector[CmlEventDefinition]
      )(using ExecutionContext): Consequence[Unit] = {
        val _ = definitions
        received += event
        Consequence.unit
      }
    })

    val result = reception.receiveInternal(ReceptionInput(
      name = "determinism.checked",
      kind = "checked"
    ))
    require(result.toOption.nonEmpty, s"event reception failed: $result")
    received.head
  }

  private def _transition_event(
    instant: Instant,
    seed: String
  ): TransitionLifecycleEvent = {
    given ExecutionContext = _execution_context(instant, seed)
    TransitionLifecycleEvent.beforeTransition(
      TransitionEvent("update", None),
      Some("deterministic-entity")
    )
  }

  private def _execution_context(
    instant: Instant,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.DEFAULT_NAMESPACE,
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }

  private final case class StoredEvent(name: String) extends DomainEvent

  private final class MutableClock(initial: Instant) extends Clock {
    private var _current = initial

    def set(instant: Instant): Unit = synchronized {
      _current = instant
    }

    override def getZone: ZoneId = ZoneOffset.UTC

    override def withZone(zone: ZoneId): Clock = {
      val _ = zone
      this
    }

    override def instant(): Instant = synchronized {
      _current
    }
  }
}
