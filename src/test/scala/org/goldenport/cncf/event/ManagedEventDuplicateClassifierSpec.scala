package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.statemachine.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * Pure observation fixtures only: no managed snapshot/commit authority.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class ManagedEventDuplicateClassifierSpec extends AnyWordSpec with Matchers {
  import ManagedEventDuplicateClassifier.Disposition
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val time = Instant.parse("2026-09-30T00:00:00Z")
  private val entityType = EntityCollectionId("test", "dedup", "subject")
  private val entity = take(EntityId.restore("test", "entity", entityType, time, "issued"))
  private val machine = CmlStateMachineIdentity("lifecycle")
  private val binding = CmlTransitionBinding(ComponentId("test.dedup.Component"), entityType, machine,
    CmlStateMachineVersion(1), CmlStateMachineTransitionIdentity(machine, 0),
    CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Before"))),
    CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(machine, "change"))
  private val indexes = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "dedup", "indexes"))
  private val cursor = take(EventCursorRecordV1.createC(1L, 10L))
  private def incoming(token: String, key: Option[String], payload: Any = "first", persistent: Boolean = true) = {
    val event = EventRecord(EventId("test", "dedup", Some(time), Some(token)), "fact", "fact",
      Map("value" -> payload), Map.empty, time, persistent, EventRecord.Status.Stored, EventLane.Transactional)
    event._delivery_lookup_key = key.map(value => CommittedDeliveryLookupKey(value, entity, binding, "change"))
    event
  }
  private def stored(event: EventRecord, sequence: Long) = {
    val result = event.copy(sequence = sequence)
    result._delivery_lookup_key = event._delivery_lookup_key
    result
  }
  private def rows(retained: Vector[EventRecord]) = retained.flatMap { event =>
    take(EventDeliveryIndexRecordV1.prepareCreateC(indexes, event)).map(effect => effect.entryId -> effect.record)
  }
  private def classify(input: Vector[EventRecord], retained: Vector[EventRecord] = Vector.empty,
    readCursor: EventCursorRecordV1 = cursor) = ManagedEventDuplicateClassifier.classifyC(input, readCursor,
      retained.map(EventRecordJsonV2.encodeStorage), rows(retained))

  "Pure managed duplicate classification" should {
    "retain exact EventId-first stored bytes and neither replace payload nor bind an additional key alias" in {
      val old = stored(incoming("old", None), 5L)
      val result = take(classify(Vector(incoming("old", Some("introduced"), new Object),
        incoming("new", Some("introduced"), "new-fact")), Vector(old)))
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(EventRecordJsonV2.encodeStorage(old)), Disposition.New(0))
      result.newDurableCount shouldBe 1
      val proposed = take(result.cursorProposalC).get
      proposed.sequences shouldBe Vector(11L)
      proposed.next.lastSequence shouldBe 11L
      take(result.newEventsC).head.payload("value") shouldBe "new-fact"
      old.payload("value") shouldBe "first"
    }

    "retain a full-key match without binding the incoming EventId as an alias" in {
      val old = stored(incoming("old", Some("retained-key")), 5L)
      val result = take(classify(Vector(incoming("alias", Some("retained-key"), "changed"),
        incoming("alias", Some("new-key"), "new-fact")), Vector(old)))
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(EventRecordJsonV2.encodeStorage(old)), Disposition.New(0))
      take(result.newEventsC).head.id shouldBe incoming("alias", None).id
      result.newDurableCount shouldBe 1
    }

    "keep the earliest batch-local durable fact and consume only unique new slots in original order" in {
      val first = incoming("one", Some("key-one"), "first")
      val result = take(classify(Vector(first, incoming("two", Some("key-one"), "ignored"),
        incoming("one", Some("unbound-key"), "ignored"), incoming("three", Some("unbound-key"), "second"),
        incoming("progress", Some("key-one"), new Object, false))))
      result.dispositions shouldBe Vector(Disposition.New(0), Disposition.RetainedBatch(0),
        Disposition.RetainedBatch(0), Disposition.New(1), Disposition.Transient(4))
      result.newDurableCount shouldBe 2
      take(result.cursorProposalC).get.sequences shouldBe Vector(11L, 12L)
      take(result.newEventsC).map(_.payload("value")) shouldBe Vector("first", "second")
      take(result.newEventsC).foreach(_.sequence shouldBe 0L)
      // The result freezes canonical facts, not mutable hidden-key references.
      first._delivery_lookup_key = None
      take(result.newEventsC).head._delivery_lookup_key should not be empty
      val returned = take(result.newEventsC)
      returned.head._delivery_lookup_key = None
      take(result.newEventsC).head._delivery_lookup_key should not be empty
    }

    "reject divergent EventId/key matches across both retained and batch-local facts" in {
      val a = incoming("a", Some("key-a"))
      val b = incoming("b", Some("key-b"))
      val contradictory = incoming("a", Some("key-b"))
      classify(Vector(contradictory), Vector(stored(a, 3L), stored(b, 4L))).toOption shouldBe None
      classify(Vector(a, b, contradictory)).toOption shouldBe None
      a.sequence shouldBe 0L
    }

    "reject missing, dangling, duplicated or corrupt observation inventories without repair" in {
      val old = stored(incoming("old", Some("key")), 5L)
      val bytes = Vector(EventRecordJsonV2.encodeStorage(old))
      val index = rows(Vector(old))
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, bytes, Vector.empty).toOption shouldBe None
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, Vector.empty, index).toOption shouldBe None
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, bytes ++ bytes, index).toOption shouldBe None
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, bytes, index ++ index).toOption shouldBe None
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, Vector("corrupt"), Vector.empty).toOption shouldBe None
      val mismatchedId = Vector(DataStore.StringEntryId("different") -> index.head._2)
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor, bytes, mismatchedId).toOption shouldBe None
      classify(Vector.empty, Vector(old), take(EventCursorRecordV1.createC(1L, 4L))).toOption shouldBe None
      classify(Vector.empty, Vector(old, stored(incoming("other", None), 5L))).toOption shouldBe None
    }

    "consume no cursor capacity for retained or transient facts even when both counters are exhausted" in {
      val old = stored(incoming("old", Some("key")), Long.MaxValue)
      val exhausted = take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))
      val result = take(classify(Vector(incoming("old", Some("key")), incoming("progress", None, persistent = false)),
        Vector(old), exhausted))
      result.newDurableCount shouldBe 0
      take(result.cursorProposalC) shouldBe None
      classify(Vector(incoming("new", None)), Vector(old), exhausted).toOption shouldBe None
    }

    "admit explicit key-free V1 observations without V2 fallback or migration" in {
      val old = stored(incoming("old", None), 5L)
      val bytes = EventRecordJsonV1.encodeStorage(old)
      val result = take(ManagedEventDuplicateClassifier.classifyC(Vector(incoming("old", None)), cursor,
        Vector(bytes), Vector.empty))
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(bytes))
      result.newDurableCount shouldBe 0
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor,
        Vector(bytes.replace("cncf.event-record.v1:", "cncf.event-record.v2:")), Vector.empty).toOption shouldBe None
    }

    "reject null, over-bounded, issued input and unsupported new durable facts" in {
      ManagedEventDuplicateClassifier.classifyC(null, cursor, Vector.empty, Vector.empty).toOption shouldBe None
      classify(Vector.fill(257)(incoming("same", None))).toOption shouldBe None
      ManagedEventDuplicateClassifier.classifyC(Vector.empty, cursor,
        Vector.fill(1025)("x"), Vector.empty).toOption shouldBe None
      classify(Vector(stored(incoming("issued", None), 1L))).toOption shouldBe None
      classify(Vector(incoming("new", None, new Object))).toOption shouldBe None
    }
  }
}
