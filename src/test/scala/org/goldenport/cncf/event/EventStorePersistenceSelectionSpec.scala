package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowEventSelection
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class EventStorePersistenceSelectionSpec extends AnyWordSpec with Matchers {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val now = Instant.parse("2026-09-30T00:00:00Z")
  private val policy = take(EventPersistencePolicy.createC("store-policy-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist),
    EventPersistencePolicy.Rule(EventTypeId("progress"), EventPersistencePolicy.Mode.Transient))))
  private def record(kind: String, token: String, hint: Boolean = true,
      lane: EventLane = EventLane.Transactional) =
    EventRecord(EventId("test", "store_selection", Some(now), Some(token)), kind, kind,
      Map.empty, Map.empty, now, hint, EventRecord.Status.Stored, lane)

  "EventStore-owned persistence selection" should {
    "select ordinary EventEngine emissions without a sender persistence parameter" in {
      val store = EventStore.inMemory(policy)
      val engine = EventEngine.noop(DataStore.noop(), eventstore = store)
      val sent = Vector("fact", "progress", "unmatched").map(kind =>
        ReceptionDomainEvent(kind, kind, Map.empty, Map.empty, now))
      val received = take(engine.emit(sent))
      received.map(_.kind) shouldBe Vector("fact", "progress", "unmatched")
      received.map(_.persistent) shouldBe Vector(true, false, false)
      take(store.query(EventStore.Query())).map(_.kind) shouldBe Vector("fact")
    }

    "receive ordinary records and retain only matching kinds regardless of sender hints" in {
      val store = EventStore.inMemory(policy)
      val input = Vector(record("progress", "1"), record("fact", "2", false), record("unknown", "3"),
        record("fact", "4", false))
      val received = take(store.append(input))
      received.map(_.id) shouldBe input.map(_.id)
      received.map(_.persistent) shouldBe Vector(false, true, false, true)
      received.map(_.sequence) shouldBe Vector(0L, 1L, 0L, 2L)
      take(store.query(EventStore.Query())).map(_.id) shouldBe Vector(input(1).id, input(3).id)
      take(store.replay(EventStore.Query())).map(_.id) shouldBe Vector(input(1).id, input(3).id)
      take(store.load(input.head.id)) shouldBe None
      take(store.load(input(2).id)) shouldBe None
      input.map(_.persistent) shouldBe Vector(true, false, true, false)
    }

    "not retain or allocate sequences for empty, excluded or unmatched batches" in {
      val store = EventStore.inMemory(policy)
      take(store.append(Vector.empty)) shouldBe empty
      val transient = Vector(record("progress", "1"), record("unmatched", "2"))
      take(store.append(transient)).forall(_.sequence == 0L) shouldBe true
      take(store.query(EventStore.Query())) shouldBe empty
      take(store.append(Vector(record("fact", "3")))).head.sequence shouldBe 1L
    }

    "keep transient identities outside retained deduplication in either arrival order" in {
      val store = EventStore.inMemory(policy)
      val excluded = record("progress", "shared")
      take(store.append(Vector(excluded))).head.sequence shouldBe 0L
      take(store.load(excluded.id)) shouldBe None
      val durable = record("fact", "shared", false)
      val retained = take(store.append(Vector(durable))).head
      retained.sequence shouldBe 1L
      retained.kind shouldBe "fact"
      val laterTransient = take(store.append(Vector(excluded))).head
      laterTransient.kind shouldBe "progress"
      laterTransient.persistent shouldBe false
      laterTransient.sequence shouldBe 0L
      take(store.load(durable.id)) shouldBe Some(retained)
      take(store.replay(EventStore.Query())) shouldBe Vector(retained)
    }

    "preserve first durable retention on repeated ordinary sends without allocating again" in {
      val store = EventStore.inMemory(policy)
      val first = record("fact", "same", false).copy(payload = Map("value" -> "first"))
      val retained = take(store.append(Vector(first))).head
      val changed = first.copy(payload = Map("value" -> "later"))
      take(store.append(Vector(changed))) shouldBe Vector(retained)
      take(store.load(first.id)) shouldBe Some(retained)
      take(store.append(Vector(record("fact", "next")))).head.sequence shouldBe 2L
      take(store.query(EventStore.Query())).head.payload shouldBe Map("value" -> "first")
      first.sequence shouldBe 0L
      changed.sequence shouldBe 0L
    }

    "freeze Workflow preparation from the store policy without appending or accepting sender policy options" in {
      val store = EventStore.inMemory(policy)
      val input = Vector(record("fact", "1", false), record("unmatched", "2"))
      val selected = take(WorkflowEventSelection.prepareFromStoreC(store, input))
      selected.policyRevision shouldBe "store-policy-v1"
      selected.persistent.map(_.id) shouldBe Vector(input.head.id)
      selected.transient.map(_.id) shouldBe Vector(input.last.id)
      take(store.query(EventStore.Query())) shouldBe empty
      val changed = EventStore.inMemory(take(EventPersistencePolicy.createC("store-policy-v2", Vector(
        EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Transient)))))
      take(WorkflowEventSelection.prepareFromStoreC(changed, input)).persistent shouldBe empty
      selected.policyRevision shouldBe "store-policy-v1"
      selected.persistent.size shouldBe 1
      WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory, input).toOption shouldBe None
    }

    "apply the same store policy to the ordinary nontransactional lane while keeping Workflow preparation transactional" in {
      val store = EventStore.inMemory(policy)
      val input = Vector(record("fact", "1", lane = EventLane.NonTransactional),
        record("progress", "2", lane = EventLane.NonTransactional))
      take(store.append(input)).map(_.persistent) shouldBe Vector(true, false)
      take(store.replay(EventStore.Query())).map(_.id) shouldBe Vector(input.head.id)
      WorkflowEventSelection.prepareFromStoreC(store, input).toOption shouldBe None
    }

    "reject malformed batches before retaining even an earlier matching event" in {
      val store = EventStore.inMemory(policy)
      val first = record("fact", "1")
      store.append(Vector(first, record("", "2"))).toOption shouldBe None
      store.append(Vector(first, first)).toOption shouldBe None
      store.append(Vector(first.copy(sequence = 1L))).toOption shouldBe None
      store.append(null).toOption shouldBe None
      take(store.query(EventStore.Query())) shouldBe empty
      take(store.append(Vector(first))).head.sequence shouldBe 1L
    }
  }
}
