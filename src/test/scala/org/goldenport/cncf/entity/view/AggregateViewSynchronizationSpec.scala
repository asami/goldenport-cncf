package org.goldenport.cncf.entity.view

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateViewSynchronizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  final case class _View(total: Int)
  final case class _Command(amount: Int)
  final case class _Event(amount: Int)
  final case class _State(total: Int)

  "AggregateViewSynchronizer" should {
    "synchronize command -> event -> projection -> view" in {
      val aggregate = new AggregateCommandHandler[_Command, _State, _Event] {
        def handle(command: _Command, state: _State): Consequence[AggregateCommandResult[_State, _Event]] =
          Consequence.success(
            AggregateCommandResult(
              newState = _State(state.total + command.amount),
              events = Vector(_Event(command.amount))
            )
          )
      }
      val projector = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] =
          Consequence.success(_View(current.total + _amount(event)))
      }
      val synchronizer = new AggregateViewSynchronizer[_View](_View(0), projector)

      Given("a command and current aggregate state")
      val command = _Command(10)
      val state = _State(0)

      When("command handling emits event and synchronization runs")
      val result = synchronizer.synchronizeCommand(
        command,
        state,
        aggregate,
        e => _event_record("command.applied", 1L, e.amount)
      )

      Then("new aggregate state and projected view are both updated")
      result shouldBe Consequence.success((_State(10), SynchronizationResult(_View(10), 1, 0, 0, Vector.empty, Some(1L))))
    }

    "apply projection in deterministic sequence order" in {
      val projector = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] =
          Consequence.success(_View(current.total + _amount(event)))
      }
      val synchronizer = new AggregateViewSynchronizer[_View](_View(0), projector)

      Given("events in non-sequential input order")
      val e2 = _event_record("order.changed", 2L, 20)
      val e1 = _event_record("order.changed", 1L, 10)

      When("synchronize is executed")
      val result = synchronizer.synchronize(Vector(e2, e1))

      Then("projection follows sequence order deterministically")
      result.map(_.view) shouldBe Consequence.success(_View(30))
      result.map(_.lastSequence) shouldBe Consequence.success(Some(2L))
    }

    "keep idempotency for replayed events with the same EventId" in {
      val projector = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] =
          Consequence.success(_View(current.total + _amount(event)))
      }
      val synchronizer = new AggregateViewSynchronizer[_View](_View(0), projector)

      Given("a replayed event (same EventId)")
      val id = EventId.generate()
      val first = _event_record_with_id(id, "replay.target", 10L, 5)
      val replayed = _event_record_with_id(id, "replay.target", 11L, 5)

      When("the event is synchronized twice")
      val _ = synchronizer.synchronize(Vector(first))
      val second = synchronizer.synchronize(Vector(replayed))

      Then("the replayed event is skipped idempotently")
      second.map(_.view) shouldBe Consequence.success(_View(5))
      second.map(_.skippedCount) shouldBe Consequence.success(1)
    }

    "apply retry/skip/dead-letter failure policies deterministically" in {
      Given("a projector that fails once then succeeds")
      var invoked = 0
      val flaky = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] = {
          invoked = invoked + 1
          if (invoked == 1) Consequence.failure("temporary failure")
          else Consequence.success(_View(current.total + _amount(event)))
        }
      }
      val retrySynchronizer =
        new AggregateViewSynchronizer[_View](_View(0), flaky, ProjectionFailurePolicy.RetryOnce)

      When("RetryOnce policy is used")
      val retried = retrySynchronizer.synchronize(Vector(_event_record("retry.case", 1L, 7)))

      Then("projection is retried once and applied")
      retried.map(_.view) shouldBe Consequence.success(_View(7))
      retried.map(_.retriedCount) shouldBe Consequence.success(1)

      Given("a projector that always fails")
      val alwaysFail = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] =
          Consequence.failure("projection failure")
      }
      val skipSynchronizer =
        new AggregateViewSynchronizer[_View](_View(0), alwaysFail, ProjectionFailurePolicy.Skip)
      val deadLetterSynchronizer =
        new AggregateViewSynchronizer[_View](_View(0), alwaysFail, ProjectionFailurePolicy.DeadLetter)

      When("Skip policy is used")
      val skipped = skipSynchronizer.synchronize(Vector(_event_record("skip.case", 2L, 9)))
      Then("event is skipped and view remains unchanged")
      skipped.map(_.view) shouldBe Consequence.success(_View(0))
      skipped.map(_.skippedCount) shouldBe Consequence.success(1)

      When("DeadLetter policy is used")
      val dead = deadLetterSynchronizer.synchronize(Vector(_event_record("dead.case", 3L, 11)))
      Then("event is recorded in dead-letter boundary")
      dead.map(_.deadLetters.size) shouldBe Consequence.success(1)
      dead.map(_.view) shouldBe Consequence.success(_View(0))
    }
  }

  private def _event_record(name: String, sequence: Long, amount: Int): EventRecord =
    _event_record_with_id(EventId.generate(), name, sequence, amount)

  private def _event_record_with_id(
    id: EventId,
    name: String,
    sequence: Long,
    amount: Int
  ): EventRecord =
    EventRecord(
      id = id,
      name = name,
      kind = "domain-event",
      payload = Map("amount" -> amount),
      attributes = Map.empty,
      createdAt = Instant.ofEpochMilli(sequence),
      persistent = true,
      status = EventRecord.Status.Stored,
      lane = EventLane.Transactional,
      sequence = sequence
    )

  private def _amount(record: EventRecord): Int =
    record.payload.get("amount") match {
      case Some(i: Int) => i
      case Some(l: Long) => l.toInt
      case Some(s: String) => s.toInt
      case _ => 0
    }
}

