package org.goldenport.cncf.workflow

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.event.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowEventSelectionSpec extends AnyWordSpec with Matchers {
  import EventPersistencePolicy.*
  import WorkflowEventSelection.Outcome

  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val now = Instant.parse("2026-09-30T00:00:00Z")
  private def record(kind: String, token: String, hint: Boolean = true): EventRecord =
    EventRecord(EventId("test", "selection", Some(now), Some(token)), kind, kind,
      Map.empty, Map.empty, now, hint, EventRecord.Status.Stored, EventLane.Transactional)
  private val rules = Vector(Rule(EventTypeId("fact"), Mode.Persist),
    Rule(EventTypeId("progress"), Mode.Transient))
  private val policy = take(createC("policy-v1", rules))

  "Workflow deterministic event selection" should {
    "select by exact event type rather than the caller persistence hint, preserving order" in {
      val input = Vector(record("progress", "1"), record("fact", "2", false), record("progress", "3"))
      val selected = take(WorkflowEventSelection.prepareC(policy, input))
      selected.policyRevision shouldBe "policy-v1"
      selected.events.map(_.id) shouldBe input.map(_.id)
      selected.persistent.map(_.id) shouldBe Vector(input(1).id)
      selected.transient.map(_.id) shouldBe Vector(input(0).id, input(2).id)
      selected.events.map(_.persistent) shouldBe Vector(false, true, false)
      input.map(_.persistent) shouldBe Vector(true, false, true)
      take(WorkflowEventSelection.prepareC(policy, input)).events shouldBe selected.events
    }

    "treat unmatched types as transient while rejecting missing policy" in {
      val selected = take(WorkflowEventSelection.prepareC(policy,
        Vector(record("fact", "1"), record("unknown", "2"))))
      selected.persistent.map(_.kind) shouldBe Vector("fact")
      selected.transient.map(_.kind) shouldBe Vector("unknown")
      WorkflowEventSelection.prepareC(null, Vector(record("fact", "1"))).toOption shouldBe None
    }

    "require canonical versioned exact rules, rejecting duplicates and wildcard defaults" in {
      Vector("", " policy-v1", "policy *").foreach { revision =>
        createC(revision, rules).toOption shouldBe None
      }
      createC("v1", rules :+ rules.head).toOption shouldBe None
      createC("v1", Vector(Rule(EventTypeId("*"), Mode.Persist))).toOption shouldBe None
      createC("v1", Vector.empty).toOption shouldBe None
      createC("v1", null).toOption shouldBe None
      createC("v1", Vector(Rule(EventTypeId("fact"), null))).toOption shouldBe None
    }

    "accept empty and all-transient selections without requesting durable records" in {
      take(WorkflowEventSelection.prepareC(policy, Vector.empty)).persistent shouldBe empty
      val selected = take(WorkflowEventSelection.prepareC(policy, Vector(record("progress", "1"))))
      selected.persistent shouldBe empty
      selected.transient.size shouldBe 1
      selected.events.forall(_.sequence == 0L) shouldBe true
    }

    "reject duplicate identities, preissued sequences, nontransactional lanes and malformed batches" in {
      val event = record("fact", "1")
      Vector(null, Vector(null), Vector(event, event), Vector(event.copy(sequence = 1L)),
        Vector(event.copy(lane = EventLane.NonTransactional)), Vector(event.copy(kind = null)),
        Vector.fill(MAX_BATCH_SIZE + 1)(event)).foreach { batch =>
        WorkflowEventSelection.prepareC(policy, batch).toOption shouldBe None
      }
    }

    "hold transient delivery until verified success and suppress it on rollback or uncertainty" in {
      val selected = take(WorkflowEventSelection.prepareC(policy,
        Vector(record("fact", "1"), record("progress", "2"))))
      Vector(Outcome.Pending, Outcome.RolledBack, Outcome.TransactionIndeterminate).foreach { outcome =>
        selected.transientDeliveryCandidates(outcome) shouldBe empty
      }
      selected.transientDeliveryCandidates(Outcome.CommitAndReadbackVerified) shouldBe selected.transient
    }

    "freeze the prepare-time revision without reevaluating a changed policy" in {
      val input = Vector(record("fact", "1"))
      val selected = take(WorkflowEventSelection.prepareC(policy, input))
      val changed = take(createC("policy-v2", Vector(Rule(EventTypeId("fact"), Mode.Transient))))
      take(WorkflowEventSelection.prepareC(changed, input)).persistent shouldBe empty
      selected.policyRevision shouldBe "policy-v1"
      selected.persistent.size shouldBe 1
    }
  }
}
