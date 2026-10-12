package org.goldenport.cncf.workflow

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
final class WorkflowSelectedEventRecordsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = {
     value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private val _collection = DataStore.CollectionId("selected_events")
  private val _now = Instant.parse("2026-09-30T00:00:00Z")
  private def _event(kind: String, id: String) = EventRecord(
    EventId("test", "selected", Some(_now), Some(id)), kind, kind, Map.empty, Map.empty,
    _now, true, EventRecord.Status.Stored, EventLane.Transactional)
  private val _policy = _take(EventPersistencePolicy.createC("v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist),
    EventPersistencePolicy.Rule(EventTypeId("progress"), EventPersistencePolicy.Mode.Transient))))
  private def _selected(records: Vector[EventRecord]) = _take(WorkflowEventSelection.prepareC(_policy, records))

  "Selected-only durable event proposal" should {
    "create only durable events with contiguous proposed sequences, preserving mixed delivery order" in {
      Given("an explicitly selected mixed transient and durable batch")
      val input = Vector(_event("progress", "1"), _event("fact", "2"), _event("progress", "3"), _event("fact", "4"))
      When("durable Creates and sequence proposals are prepared")
      val prepared = _take(WorkflowSelectedEventRecords.prepareC(_selected(input), _collection, 10L))
      Then("only the durable subset consumes contiguous sequences")
      prepared.persistent.map(_.sequence) shouldBe Vector(11L, 12L)
      prepared.creates.map(_.entryId.print) shouldBe Vector(input(1).id.value, input(3).id.value)
      prepared.proposedLastSequence shouldBe 12L
      prepared.requiresCursorUpdate shouldBe true
      When("actual durable candidates are consumed")
      val delivery = _take(prepared.admitDeliveryCandidatesC(prepared.persistent))
      Then("the original mixed order retains transient values and actual durable sequences")
      delivery.map(_.id) shouldBe input.map(_.id)
      delivery.map(_.sequence) shouldBe Vector(0L, 11L, 0L, 12L)
      delivery.map(_.persistent) shouldBe Vector(false, true, false, true)
      input.forall(_.sequence == 0L) shouldBe true
    }

    "produce no Creates or cursor movement for empty and all-transient selections" in {
      Given("empty and all-transient selected batches at exhausted cursor capacity")
      Vector(Vector.empty, Vector(_event("progress", "1"))).foreach { input =>
        When("the selected batch is prepared and its empty durable subset is consumed")
        val prepared = _take(WorkflowSelectedEventRecords.prepareC(_selected(input), _collection, Long.MaxValue))
        val delivery = _take(prepared.admitDeliveryCandidatesC(Vector.empty))
        Then("no durable Create or cursor movement is required")
        prepared.creates shouldBe empty
        prepared.requiresCursorUpdate shouldBe false
        prepared.proposedLastSequence shouldBe Long.MaxValue
        delivery.size shouldBe input.size
      }
    }

    "reject invalid and overflowing cursor proposals without returning partial Creates" in {
      Given("two selected durable facts and insufficient cursor capacity")
      val selection = _selected(Vector(_event("fact", "1"), _event("fact", "2")))
      When("invalid cursor or collection inputs reach preparation")
      val results = Vector(-1L, Long.MaxValue, Long.MaxValue - 1L).map(cursor =>
        WorkflowSelectedEventRecords.prepareC(selection, _collection, cursor).toOption) ++ Vector(
        WorkflowSelectedEventRecords.prepareC(null, _collection, 0L).toOption,
        WorkflowSelectedEventRecords.prepareC(selection, null, 0L).toOption)
      Then("no partial Creates are admitted")
      results.foreach(_ shouldBe None)
    }

    "reject an unsupported durable payload before producing any prepared batch" in {
      Given("one selected durable fact whose payload has no native representation")
      val input = Vector(_event("fact", "1"), _event("fact", "2").copy(payload = Map("bad" -> new Object)))
      When("the complete batch is prepared")
      val result = WorkflowSelectedEventRecords.prepareC(_selected(input), _collection, 0L).toOption
      Then("the unsupported durable value rejects the batch")
      result shouldBe None
    }

    "reject missing, foreign, duplicate, reordered and invalid native readback targets" in {
      Given("two selected durable IDs surrounding one transient input")
      val prepared = _take(WorkflowSelectedEventRecords.prepareC(
        _selected(Vector(_event("fact", "1"), _event("progress", "2"), _event("fact", "3"))), _collection, 0L))
      val invalid = Vector(null, Vector.empty, prepared.persistent.reverse,
        Vector.fill(2)(prepared.persistent.head),
        prepared.persistent.updated(0, _event("fact", "foreign").copy(sequence = 1L)),
        prepared.persistent.updated(1, prepared.persistent.last.copy(sequence = 1L)),
        prepared.persistent.updated(0, prepared.persistent.head.copy(persistent = false)),
        prepared.persistent.updated(0, prepared.persistent.head.copy(lane = EventLane.NonTransactional)),
        prepared.persistent.updated(0, prepared.persistent.head.copy(payload = Map("bad" -> new Object))),
        prepared.persistent :+ _event("fact", "extra").copy(sequence = 3L))
      When("each actual durable subset is consumed")
      val results = invalid.map(readback => prepared.admitDeliveryCandidatesC(readback).toOption)
      Then("invalid identity, cardinality, ordering or native data yields no candidates")
      results.foreach(_ shouldBe None)
    }

    "project actual changed durable name, payload and ordered sequence in the original mixed order" in {
      Given("a durable selection and different valid actual retained content for the same target IDs")
      val prepared = _take(WorkflowSelectedEventRecords.prepareC(
        _selected(Vector(_event("fact", "1"), _event("progress", "2"), _event("fact", "3"))), _collection, 0L))
      val actual = prepared.persistent.zipWithIndex.map { case (record, index) =>
        record.copy(name = "actual.saved", payload = Map("actual" -> (index + 1)), sequence = index.toLong + 10L)
      }
      When("the actual durable values are consumed")
      val delivery = _take(prepared.admitDeliveryCandidatesC(actual))
      Then("delivery consumes retained facts and keeps the original transient placement")
      delivery.filter(_.persistent) shouldBe actual
      delivery.map(_.sequence) shouldBe Vector(10L, 0L, 11L)
      delivery(1).persistent shouldBe false
    }
  }
}
