package org.goldenport.cncf.workflow

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowSelectedEventRecordsSpec extends AnyWordSpec with Matchers {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val collection = DataStore.CollectionId("selected_events")
  private val now = Instant.parse("2026-09-30T00:00:00Z")
  private def event(kind: String, id: String) = EventRecord(
    EventId("test", "selected", Some(now), Some(id)), kind, kind, Map.empty, Map.empty,
    now, true, EventRecord.Status.Stored, EventLane.Transactional)
  private val policy = take(EventPersistencePolicy.createC("v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist),
    EventPersistencePolicy.Rule(EventTypeId("progress"), EventPersistencePolicy.Mode.Transient))))
  private def selected(records: Vector[EventRecord]) = take(WorkflowEventSelection.prepareC(policy, records))

  "Selected-only durable event proposal" should {
    "create only durable events with contiguous proposed sequences, preserving mixed delivery order" in {
      val input = Vector(event("progress", "1"), event("fact", "2"), event("progress", "3"), event("fact", "4"))
      val prepared = take(WorkflowSelectedEventRecords.prepareC(selected(input), collection, 10L))
      prepared.persistent.map(_.sequence) shouldBe Vector(11L, 12L)
      prepared.creates.map(_.entryId.print) shouldBe Vector(input(1).id.value, input(3).id.value)
      prepared.proposedLastSequence shouldBe 12L
      prepared.requiresCursorUpdate shouldBe true
      val delivery = take(prepared.admitDeliveryCandidatesC(prepared.persistent))
      delivery.map(_.id) shouldBe input.map(_.id)
      delivery.map(_.sequence) shouldBe Vector(0L, 11L, 0L, 12L)
      delivery.map(_.persistent) shouldBe Vector(false, true, false, true)
      input.forall(_.sequence == 0L) shouldBe true
    }

    "produce no Creates or cursor movement for empty and all-transient selections" in {
      Vector(Vector.empty, Vector(event("progress", "1"))).foreach { input =>
        val prepared = take(WorkflowSelectedEventRecords.prepareC(selected(input), collection, Long.MaxValue))
        prepared.creates shouldBe empty
        prepared.requiresCursorUpdate shouldBe false
        prepared.proposedLastSequence shouldBe Long.MaxValue
        take(prepared.admitDeliveryCandidatesC(Vector.empty)).size shouldBe input.size
      }
    }

    "reject invalid and overflowing cursor proposals without returning partial Creates" in {
      val selection = selected(Vector(event("fact", "1"), event("fact", "2")))
      Vector(-1L, Long.MaxValue, Long.MaxValue - 1L).foreach { cursor =>
        WorkflowSelectedEventRecords.prepareC(selection, collection, cursor).toOption shouldBe None
      }
      WorkflowSelectedEventRecords.prepareC(null, collection, 0L).toOption shouldBe None
      WorkflowSelectedEventRecords.prepareC(selection, null, 0L).toOption shouldBe None
    }

    "reject an unsupported durable payload before producing any prepared batch" in {
      val input = Vector(event("fact", "1"), event("fact", "2").copy(payload = Map("bad" -> new Object)))
      WorkflowSelectedEventRecords.prepareC(selected(input), collection, 0L).toOption shouldBe None
    }

    "reject missing, divergent, reordered and unexpected readback before returning delivery candidates" in {
      val prepared = take(WorkflowSelectedEventRecords.prepareC(
        selected(Vector(event("fact", "1"), event("progress", "2"), event("fact", "3"))), collection, 0L))
      Vector(null, Vector.empty, prepared.persistent.reverse,
        prepared.persistent.updated(0, prepared.persistent.head.copy(name = "changed")),
        prepared.persistent :+ event("fact", "extra").copy(sequence = 3L)).foreach { readback =>
        prepared.admitDeliveryCandidatesC(readback).toOption shouldBe None
      }
    }
  }
}
