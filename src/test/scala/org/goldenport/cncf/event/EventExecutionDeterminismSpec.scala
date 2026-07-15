package org.goldenport.cncf.event

import java.time.{Clock, Instant, ZoneOffset}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.statemachine.TransitionEvent
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
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
      IdGenerationContext.DefaultNamespace,
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }
}
