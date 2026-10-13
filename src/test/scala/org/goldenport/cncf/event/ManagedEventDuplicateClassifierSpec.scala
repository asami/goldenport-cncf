package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.DataStoreMultiGuardRecord
import org.goldenport.record.Record
import io.circe.parser.parse
import org.goldenport.cncf.statemachine.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * Pure observation fixtures only: no managed snapshot/commit authority.
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
final class ManagedEventDuplicateClassifierSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ManagedEventDuplicateClassifier.Disposition
  private def _take[A](value: Consequence[A]): A = {
     value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private val _time = Instant.parse("2026-09-30T00:00:00Z")
  private val _entity_type = EntityCollectionId("test", "dedup", "subject")
  private val _entity = _take(EntityId.restore("test", "entity", _entity_type, _time, "issued"))
  private val _machine = CmlStateMachineIdentity("lifecycle")
  private val _binding = CmlTransitionBinding(ComponentId("test.dedup.Component"), _entity_type, _machine,
    CmlStateMachineVersion(1), CmlStateMachineTransitionIdentity(_machine, 0),
    CmlStateMachineStateIdentity(_machine, CmlStateMachineStatePath(Vector("Before"))),
    CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(_machine, "change"))
  private val _indexes = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "dedup", "indexes"))
  private val _cursor = _take(EventCursorRecordV1.createC(1L, 10L))
  private def _incoming(token: String, key: Option[String], payload: Any = "first", persistent: Boolean = true) = {
    val event = EventRecord(EventId("test", "dedup", Some(_time), Some(token)), "fact", "fact",
      Map("value" -> payload), Map.empty, _time, persistent, EventRecord.Status.Stored, EventLane.Transactional)
    event._delivery_lookup_key = key.map(value => CommittedDeliveryLookupKey(value, _entity, _binding, "change"))
    event
  }
  private def _stored(event: EventRecord, sequence: Long) = {
    val result = event.copy(sequence = sequence)
    result._delivery_lookup_key = event._delivery_lookup_key
    result
  }
  private def _rows(retained: Vector[EventRecord]) = retained.flatMap { event =>
    _take(EventDeliveryIndexRecordV1.prepareCreateC(_indexes, event)).map(effect => effect.entryId -> effect.record)
  }
  private def _classify(input: Vector[EventRecord], retained: Vector[EventRecord] = Vector.empty,
    readcursor: EventCursorRecordV1 = _cursor) = ManagedEventDuplicateClassifier.classifyC(input, readcursor,
      retained.map(EventRecordJsonV2.encodeStorage), _rows(retained))

  "Pure managed duplicate classification" should {
    "retain exact EventId-first stored bytes and neither replace payload nor bind an additional key alias" in {
      Given("a retained key-free ID and two incoming facts sharing an introduced key")
      val old = _stored(_incoming("old", None), 5L)
      When("the batch is classified by actual ID and complete key")
      val result = _take(_classify(Vector(_incoming("old", Some("introduced"), new Object),
        _incoming("new", Some("introduced"), "new-fact")), Vector(old)))
      Then("only the new ID uses cursor capacity and the retained content stays selected")
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(EventRecordJsonV2.encodeStorage(old)), Disposition.New(0))
      result.newDurableCount shouldBe 1
      val proposed = _take(result.cursorProposalC).get
      proposed.sequences shouldBe Vector(11L)
      proposed.next.lastSequence shouldBe 11L
      _take(result.newEventsC).head.payload("value") shouldBe "new-fact"
      old.payload("value") shouldBe "first"
    }

    "retain a full-key match without binding the incoming EventId as an alias" in {
      Given("one retained complete key and an incoming alias ID")
      val old = _stored(_incoming("old", Some("retained-key")), 5L)
      When("the alias and another key are classified together")
      val result = _take(_classify(Vector(_incoming("alias", Some("retained-key"), "changed"),
        _incoming("alias", Some("new-key"), "new-fact")), Vector(old)))
      Then("the retained key supplies its fact and does not bind an additional ID alias")
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(EventRecordJsonV2.encodeStorage(old)), Disposition.New(0))
      _take(result.newEventsC).head.id shouldBe _incoming("alias", None).id
      result.newDurableCount shouldBe 1
    }

    "keep the earliest batch-local durable fact and consume only unique new slots in original order" in {
      Given("batch-local ID/key duplicates and one transient value")
      val first = _incoming("one", Some("key-one"), "first")
      When("the selected batch is classified")
      val result = _take(_classify(Vector(first, _incoming("two", Some("key-one"), "ignored"),
        _incoming("one", Some("unbound-key"), "ignored"), _incoming("three", Some("unbound-key"), "second"),
        _incoming("progress", Some("key-one"), new Object, false))))
      Then("only the earliest durable facts consume sequence slots")
      result.dispositions shouldBe Vector(Disposition.New(0), Disposition.RetainedBatch(0),
        Disposition.RetainedBatch(0), Disposition.New(1), Disposition.Transient(4))
      result.newDurableCount shouldBe 2
      _take(result.cursorProposalC).get.sequences shouldBe Vector(11L, 12L)
      _take(result.newEventsC).map(_.payload("value")) shouldBe Vector("first", "second")
      _take(result.newEventsC).foreach(_.sequence shouldBe 0L)
      // The result freezes canonical facts, not mutable hidden-key references.
      When("source and returned clone diagnostics are later changed")
      first._delivery_lookup_key = None
      val returned = _take(result.newEventsC)
      returned.head._delivery_lookup_key = None
      val retainedkey = _take(result.newEventsC).head._delivery_lookup_key
      Then("the immutable classification still retains its original delivery key")
      retainedkey should not be empty
    }

    "reject divergent EventId/key matches across both retained and batch-local facts" in {
      Given("an ID naming one fact and a complete key naming another")
      val a = _incoming("a", Some("key-a"))
      val b = _incoming("b", Some("key-b"))
      val contradictory = _incoming("a", Some("key-b"))
      When("retained and batch-local classifications consume the contradictory target")
      val retained = _classify(Vector(contradictory), Vector(_stored(a, 3L), _stored(b, 4L))).toOption
      val batch = _classify(Vector(a, b, contradictory)).toOption
      Then("both contradictions reject without assigning the input")
      retained shouldBe None
      batch shouldBe None
      a.sequence shouldBe 0L
    }

    "reject missing, dangling, duplicated or corrupt observation inventories without repair" in {
      Given("one indexed retained fact and malformed observation inventories")
      val old = _stored(_incoming("old", Some("key")), 5L)
      val bytes = Vector(EventRecordJsonV2.encodeStorage(old))
      val index = _rows(Vector(old))
      When("classification consumes each incomplete or inconsistent inventory")
      val mismatchedid = Vector(DataStore.StringEntryId("different") -> index.head._2)
      val results = Vector(
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, bytes, Vector.empty),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, Vector.empty, index),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, bytes ++ bytes, index),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, bytes, index ++ index),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, Vector("corrupt"), Vector.empty),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, bytes, mismatchedid),
        _classify(Vector.empty, Vector(old), _take(EventCursorRecordV1.createC(1L, 4L))),
        _classify(Vector.empty, Vector(old, _stored(_incoming("other", None), 5L))))
      Then("no stored fact was reassigned by this pure interpretation")
      results.foreach(_.toOption shouldBe None)
      old.sequence shouldBe 5L
    }

    "consume no cursor capacity for retained or transient facts even when both counters are exhausted" in {
      Given("a retained fact and an exhausted sequence/revision domain")
      val old = _stored(_incoming("old", Some("key")), Long.MaxValue)
      val exhausted = _take(EventCursorRecordV1.createC(Long.MaxValue, Long.MaxValue))
      When("retained/transient and new-durable batches are classified")
      val result = _take(_classify(Vector(_incoming("old", Some("key")), _incoming("progress", None, persistent = false)),
        Vector(old), exhausted))
      val rejected = _classify(Vector(_incoming("new", None)), Vector(old), exhausted).toOption
      Then("only the new durable batch refuses capacity exhaustion")
      result.newDurableCount shouldBe 0
      _take(result.cursorProposalC) shouldBe None
      rejected shouldBe None
    }

    "admit explicit key-free V1 observations without V2 fallback or migration" in {
      Given("an explicit key-free V1 retained observation")
      val old = _stored(_incoming("old", None), 5L)
      val bytes = EventRecordJsonV1.encodeStorage(old)
      When("the declared V1 prefix is consumed and a false V2 wrapper is attempted")
      val result = _take(ManagedEventDuplicateClassifier.classifyC(Vector(_incoming("old", None)), _cursor,
        Vector(bytes), Vector.empty))
      val rejected = ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor,
        Vector(bytes.replace("cncf.event-record.v1:", "cncf.event-record.v2:")), Vector.empty).toOption
      Then("V1 facts are selected without a failed-V2 fallback")
      result.dispositions shouldBe Vector(Disposition.RetainedExisting(bytes))
      result.newDurableCount shouldBe 0
      rejected shouldBe None
    }

    "reject null, over-bounded, issued input and unsupported new durable facts" in {
      Given("inputs outside the unissued bounded durable batch contract")
      When("those actual inputs reach classification")
      val results = Vector(ManagedEventDuplicateClassifier.classifyC(null, _cursor, Vector.empty, Vector.empty),
        _classify(Vector.fill(257)(_incoming("same", None))),
        ManagedEventDuplicateClassifier.classifyC(Vector.empty, _cursor, Vector.fill(1025)("x"), Vector.empty),
        _classify(Vector(_stored(_incoming("issued", None), 1L))),
        _classify(Vector(_incoming("new", None, new Object))))
      Then("the cursor has not allocated a sequence")
      results.foreach(_.toOption shouldBe None)
      _cursor.lastSequence shouldBe 10L
    }

    "finalize actual new, retained and batch-deduplicated rows in original mixed order" in {
      Given("one retained key, a new keyed fact, its batch duplicate and a transient value sharing its ID")
      val old = _stored(_incoming("old", Some("retained")), 5L)
      val transient = _incoming("new", None, new Object, persistent = false)
      val input = Vector(_incoming("alias", Some("retained")), _incoming("new", Some("new-key")),
        _incoming("batch-alias", Some("new-key")), transient)
      val classified = _take(_classify(input, Vector(old)))
      val fresh = _stored(_incoming("new", Some("new-key"), "actual-new"), 20L).copy(name = "actual.new")
      fresh._delivery_lookup_key = input(1)._delivery_lookup_key
      val retained = old.copy(name = "actual.retained", payload = Map("value" -> "actual-old"))
      retained._delivery_lookup_key = old._delivery_lookup_key
      val collection = DataStore.CollectionId("finalized_events")
      val rows = Vector(retained, fresh).map { value =>
        val bytes = "cncf.event-record.v2: \n" + parse(EventRecordJsonV2.encode(value)).toOption.get.spaces2 + "\n "
        DataStoreMultiGuardRecord(collection, DataStore.StringEntryId(value.id.value), Record.dataAuto("event_json" -> bytes))
      }
      When("native finalized readback consumes each disposition's actual durable target")
      val finalized = _take(classified.finalizedReadbackC(input, collection, rows))
      Then("actual retained facts fill durable slots while the original transient value stays transient")
      finalized.map(_.id) shouldBe Vector(old.id, fresh.id, fresh.id, transient.id)
      finalized.take(3).map(_.payload("value")) shouldBe Vector("actual-old", "actual-new", "actual-new")
      finalized.take(3).map(_.sequence) shouldBe Vector(5L, 20L, 20L)
      finalized.last shouldBe transient
      finalized.last.persistent shouldBe false
      finalized.last.sequence shouldBe 0L
    }

    "withhold finalized delivery for missing, ambiguous, foreign or unreadable actual rows" in {
      Given("a selected new durable target and actual native row distortions")
      val input = Vector(_incoming("new", None))
      val classified = _take(_classify(input))
      val collection = DataStore.CollectionId("finalized_events")
      val actual = _stored(input.head, 11L)
      val row = DataStoreMultiGuardRecord(collection, DataStore.StringEntryId(actual.id.value),
        Record.dataAuto("event_json" -> EventRecordJsonV2.encodeStorage(actual)))
      val foreign = _stored(_incoming("foreign", None), 11L)
      val variants = Vector(Vector.empty, Vector(row, row),
        Vector(row.copy(record = Record.dataAuto("event_json" -> "cncf.event-record.v2:not-json"))),
        Vector(row.copy(record = Record.dataAuto("event_json" -> EventRecordJsonV2.encodeStorage(foreign)))),
        Vector(row.copy(record = Record.dataAuto("unused" -> "no event"))),
        Vector(row.copy(collection = _indexes)))
      When("finalized projection consumes each incomplete actual readback")
      val results = variants.map(rows => classified.finalizedReadbackC(input, collection, rows))
      Then("every result is transaction-indeterminate and grants no delivery")
      results.foreach { result =>
        result.toOption shouldBe None
        val conclusion = result match {
          case Consequence.Failure(value) => value
          case _ => fail("expected transaction-indeterminate failure")
        }
        conclusion.observation.cause.descriptor.facets.collect {
          case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
        } should contain ("transaction-indeterminate")
      }
    }
  }
}
