package org.goldenport.cncf.event

import java.time.Instant
import java.nio.file.Files
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.statemachine.*
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
final class EventRecordJsonV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {

  private def _temporary_directory(prefix: String): java.nio.file.Path = {
    val directory = java.nio.file.Path.of("target", "phase105", "work", "EventRecordJsonV2Spec").toAbsolutePath
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, prefix)
  }
  private given ExecutionContext = ExecutionContext.create()
  private def _take[A](value: Consequence[A]): A = {
     value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private val _time = Instant.parse("2026-09-30T00:00:00.123456789Z")
  private val _entity_type = EntityCollectionId("test", "key", "subject")
  private val _entity_id = _take(EntityId.restore("test", "entity", _entity_type,
    Instant.parse("2026-09-30T00:00:00.123Z"), "issued_1"))
  private val _machine = CmlStateMachineIdentity("lifecycle")
  private val _version = CmlStateMachineVersion(3)
  private val _trigger = CmlStateMachineTriggerIdentity(_machine, "change")
  private def _state(parts: String*) = CmlStateMachineStateIdentity(_machine, CmlStateMachineStatePath(parts.toVector))
  private val _targets = Vector(CmlStateMachineTransitionTarget.State(_state("After", "Leaf")),
    CmlStateMachineTransitionTarget.ShallowHistory(CmlStateMachineShallowHistoryTarget(
      _state("Composite"), _state("Composite", "Fallback"))), CmlStateMachineTransitionTarget.Final)
  private def _lookup_key(target: CmlStateMachineTransitionTarget = _targets.head, mode: Int = 2, reverse: Boolean = false) = {
    val identity = CmlStateMachineTriggerContextIdentity(_trigger)
    val fields = Vector("subject" -> CmlStateMachineScalarType.StringValue,
      "flag" -> CmlStateMachineScalarType.BooleanValue, "count" -> CmlStateMachineScalarType.IntegerValue)
      .map { case (name, scalar) => CmlStateMachineTriggerContextField(
        CmlStateMachineTriggerContextFieldIdentity(identity, name), scalar) }
    val context = if (mode == 0) None else Some(CmlStateMachineTriggerContext(identity, _version,
      if (reverse) fields.reverse else fields))
    CommittedDeliveryLookupKey("explicit-key", _entity_id,
      CmlTransitionBinding(ComponentId("test.key.Component"), _entity_type, _machine, _version,
        CmlStateMachineTransitionIdentity(_machine, 4), _state("Before", "Leaf"), target, _trigger,
        if (mode == 2) Some(CmlStateMachineOperationIdentity("service", "change")) else None, context), "operation")
  }
  private def _event(delivery: Option[CommittedDeliveryLookupKey] = Some(_lookup_key())): EventRecord = {
    val value = EventRecord(EventId("test", "v2", Some(_time), Some("event_1")), "fact", "committed-transition",
      Map("long" -> 9007199254740993L, "decimal" -> BigDecimal("1.2300"), "items" -> Vector(1, true)),
      Map("trace" -> "opaque"), _time, true, EventRecord.Status.Stored, EventLane.Transactional, Long.MaxValue)
    value._delivery_lookup_key = delivery
    value
  }
  private val _events = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "key", "events"))
  private val _indexes = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "key", "indexes"))
  private def _index_effect(value: EventRecord) = _take(EventDeliveryIndexRecordV1.prepareCreateC(_indexes, value)).get
  private def _index(value: EventRecord) = _take(EventDeliveryIndexRecordV1.decodeC(_index_effect(value).record))

  "Complete typed committed-delivery key" should {
    "restore all target and optional operation/context variants with ordered fields" in {
      for { target <- _targets; mode <- 0 to 2; reverse <- Vector(false, true) } {
        Given("a complete key with an explicit target, optional Operation and ordered context fields")
        val original = _lookup_key(target, mode, reverse)
        When("the structural key is written and recovered")
        val encoded = CommittedDeliveryKeyJsonV1.encodeStorage(original)
        val recovered = _take(CommittedDeliveryKeyJsonV1.decodeStorageC(encoded))
        Then("the actual complete key retains every typed constituent")
        recovered shouldBe original
        recovered.entityId.collection shouldBe _entity_type
        recovered.entityId.timestamp shouldBe _entity_id.timestamp
        recovered.entityId.entropy shouldBe _entity_id.entropy
        recovered.binding shouldBe original.binding
        When("the recovered actual key is written again")
        val rewritten = CommittedDeliveryKeyJsonV1.encodeStorage(recovered)
        Then("the canonical writer retains the complete key representation")
        rewritten shouldBe encoded
      }
      Given("two complete keys with opposite field order")
      When("the canonical writer encodes each field order")
      val reversed = CommittedDeliveryKeyJsonV1.encodeStorage(_lookup_key(reverse = true))
      val ordinary = CommittedDeliveryKeyJsonV1.encodeStorage(_lookup_key())
      Then("the actual field order remains part of the written key")
      reversed should not be ordinary
    }

    "keep every lookup constituent distinct without rendered-selector identity" in {
      Given("complete keys differing in one meaningful lookup constituent")
      val original = _lookup_key()
      val binding = original.binding
      val alternatives = Vector(original.copy(invocationKey = "another"), original.copy(operationId = "other"),
        original.copy(entityId = _take(EntityId.restore("test", "entity", _entity_type, _entity_id.timestamp.get, "issued_2"))),
        original.copy(binding = binding.copy(componentId = ComponentId("test.key.Other"))),
        original.copy(binding = binding.copy(entityType = EntityCollectionId("test", "key", "other"))),
        original.copy(binding = binding.copy(transition = CmlStateMachineTransitionIdentity(_machine, 5))),
        original.copy(binding = binding.copy(source = _state("Before", "Other"))),
        _lookup_key(_targets(1)), _lookup_key(_targets(2)), _lookup_key(mode = 1), _lookup_key(mode = 0), _lookup_key(reverse = true))
      When("the actual structural keys are written and read")
      val encodings = (original +: alternatives).map(CommittedDeliveryKeyJsonV1.encodeStorage)
      val recovered = alternatives.map(value => _take(CommittedDeliveryKeyJsonV1.decodeStorageC(
        CommittedDeliveryKeyJsonV1.encodeStorage(value))))
      Then("every distinct key remains distinct and recovers its original meaning")
      encodings.distinct.size shouldBe encodings.size
      recovered shouldBe alternatives
    }

    "reject corrupt, noncanonical, unsupported and over-bounded structural representations" in {
      Given("the unchanged structural key contract and malformed native representations")
      val bytes = CommittedDeliveryKeyJsonV1.encodeStorage(_lookup_key())
      val variants = Vector(null, bytes.stripPrefix(CommittedDeliveryKeyJsonV1.prefix), bytes + " ",
        bytes.replace("\"schema\":", "\"unknown\":null,\"schema\":"),
        bytes.replace("\"invocationKey\":\"explicit-key\"", "\"invocationKey\":\"x\",\"invocationKey\":\"explicit-key\""),
        bytes.replace("\"version\":3", "\"version\":3.0"),
        bytes.replace("\"version\":3", "\"version\":0"),
        bytes.replace("\"order\":4", "\"order\":-1"),
        bytes.replace("\"type\":\"integer\"", "\"type\":\"decimal\""),
        bytes.replace("\"entropy\":\"issued_1\"", "\"entropy\":\"invalid-entropy\""),
        bytes.replace("00:00:00.123Z", "00:00:00.123000001Z"),
        bytes.replace("cncf.committed-delivery-key.v1", "cncf.committed-delivery-key.v99"))
      When("the actual key reader consumes invalid required structure and bounds")
      val results = variants.map(value => CommittedDeliveryKeyJsonV1.decodeStorageC(value).toOption)
      val oversized = CommittedDeliveryKeyJsonV1.decodeStorageC("x" * 65537).toOption
      Then("each incompatible key is refused by the unchanged key-specific contract")
      results.foreach(_ shouldBe None)
      oversized shouldBe None
      When("an over-bounded public key reaches its canonical writer")
      val failure = intercept[IllegalArgumentException](CommittedDeliveryKeyJsonV1.encodeStorage(_lookup_key().copy(invocationKey = "x" * 8193)))
      Then("the writer retains the existing size obligation")
      failure.getMessage should not be empty
    }
  }

  "Separate V2 event envelope" should {
    "retain exact V1 body and full discriminator while preserving V1 rejection" in {
      Given("key-free and keyed events with exact V1 numeric types")
      Vector(None, Some(_lookup_key())).foreach { delivery =>
        val original = _event(delivery)
        When("the explicit V2 envelope is encoded and decoded")
        val bytes = EventRecordJsonV2.encodeStorage(original)
        val recovered = _take(EventRecordJsonV2.decodeStorageC(bytes))
        Then("all typed body facts and the hidden complete key survive")
        recovered shouldBe original
        recovered._delivery_lookup_key shouldBe delivery
        recovered.payload("long") shouldBe 9007199254740993L
        When("the recovered V2 event is written again")
        val rewritten = EventRecordJsonV2.encodeStorage(recovered)
        Then("the canonical writer retains the exact typed body and discriminator")
        rewritten shouldBe bytes
        original._delivery_lookup_key shouldBe delivery
        When("the explicit V1 reader receives the actual V2 envelope")
        val rejectedv1 = EventRecordJsonV1.decodeStorageC(bytes)
        Then("the V1 reader refuses the incompatible format")
        rejectedv1.toOption shouldBe None
      }
      When("an incompatible format is supplied to an explicitly selected reader")
      val rejectedcreate = EventRecordJsonV1.createSideEffectC(_events, _event()).toOption
      val rejectedread = EventRecordJsonV2.decodeStorageC(EventRecordJsonV1.encodeStorage(_event(None))).toOption
      Then("neither format fallback nor discriminator loss is admitted")
      rejectedcreate shouldBe None
      rejectedread shouldBe None
    }

    "consume whitespace, reordered properties and equivalent escapes while retaining the discriminator" in {
      Given("one complete keyed V2 envelope with typed decimal and Long content")
      val original = _event()
      val bytes = EventRecordJsonV2.encode(original)
      val reordered = Json.fromJsonObject(io.circe.JsonObject.fromIterable(
        parse(bytes).toOption.get.asObject.get.toVector.reverse)).spaces2
      val variants = Vector(" \n" + bytes + "\n ", reordered,
        bytes.replace("explicit-key", "explicit\\u002dkey"),
        "{\"delivery\":null," + bytes.drop(1))
      When("actual parsed V2 and storage representations are consumed")
      val decoded = variants.map(raw => _take(EventRecordJsonV2.decodeC(raw)))
      val stored = _take(EventRecordJsonV2.decodeStorageC("cncf.event-record.v2:" + variants.head))
      Then("equivalent representations retain actual content and deterministic canonical writing")
      (decoded :+ stored).foreach { value =>
        value shouldBe original
        value._delivery_lookup_key shouldBe original._delivery_lookup_key
        value.payload("decimal").asInstanceOf[BigDecimal].bigDecimal.scale shouldBe 4
        EventRecordJsonV2.encode(value) shouldBe bytes
      }
    }

    "reject corrupt envelope/body/key and never fall back or silently lose a discriminator" in {
      Given("a complete keyed V2 envelope and invalid consumed schema, body or key variants")
      val bytes = EventRecordJsonV2.encodeStorage(_event())
      val invalid = Vector(null, bytes.stripPrefix("cncf.event-record.v2:"),
        bytes.replace("cncf.event-record.v2", "cncf.event-record.v3"),
        bytes.replace("\"body\":", "\"extra\":null,\"body\":"),
        bytes.dropRight(1) + ",\"body\":null}",
        bytes.replace("\"kind\":\"state\"", "\"kind\":\"deep-history\""),
        bytes.replace("\"lane\":\"transactional\"", "\"lane\":\"unknown\""),
        bytes.replace("\"entropy\":\"event_1\"", "\"entropy\":null"),
        bytes.replace("\"timestamp\":\"2026-09-30T00:00:00.123456789Z\"", "\"timestamp\":null"),
        bytes.replace("\"type\":\"long\",\"value\":\"9007199254740993\"",
          "\"type\":\"long\",\"value\":\"9223372036854775808\""))
      When("invalid native representations and Create facts reach their selected boundary")
      val results = invalid.map(value => EventRecordJsonV2.decodeStorageC(value).toOption)
      val creates = Vector(EventRecordJsonV2.createSideEffectC(null, _event()).toOption,
        EventRecordJsonV2.createSideEffectC(_events, _event().copy(persistent = false)).toOption,
        EventRecordJsonV2.createSideEffectC(_events, _event().copy(sequence = 0L)).toOption)
      Then("malformed consumed values reject without fallback or a Create")
      results.foreach(_ shouldBe None)
      creates.foreach(_ shouldBe None)
    }
  }

  "Delivery index codec and actual identity checks" should {
    "round-trip exact fields and reject hash-row aliasing, absent or mismatched references" in {
      Given("a canonical index with one issued event and one complete addressed key")
      val original = _event()
      val value = _index(original)
      When("the matching actual event is consumed")
      val actual = _take(value.validateEventC(EventRecordJsonV2.encodeStorage(original)))
      val lookup = value.validateLookupC(value.entryId, value.lookupKeyBytes)
      Then("the native sequence and complete discriminator are retained")
      value.sequence shouldBe Long.MaxValue
      value.entryId.print should fullyMatch regex "delivery-v1-[0-9a-f]{64}"
      lookup.isSuccess shouldBe true
      actual._delivery_lookup_key shouldBe original._delivery_lookup_key
      When("the physical address, consumed key, reference or sequence differs")
      val invalidlookups = Vector(
        value.validateLookupC(value.entryId, CommittedDeliveryKeyJsonV1.encodeStorage(_lookup_key().copy(invocationKey = "different"))),
        value.validateLookupC(DataStore.StringEntryId("other"), value.lookupKeyBytes))
      val invalidevents = Vector(null, EventRecordJsonV2.encodeStorage(_event(None)),
        EventRecordJsonV2.encodeStorage(_event(Some(_lookup_key().copy(operationId = "different"))))).map(bytes =>
        value.validateEventC(bytes).toOption)
      val invalidsequences = Vector("0", "01", "+1", "9223372036854775808").map(sequence =>
        EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields.filterNot(_.key == "sequence") ++
          Record.dataAuto("sequence" -> sequence).fields)).toOption)
      val duplicate = EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields :+ value.toRecord.fields.head)).toOption
      Then("invalid consumed identities never produce an admitted lookup")
      invalidlookups.forall(_.toOption.isEmpty) shouldBe true
      invalidevents.foreach(_ shouldBe None)
      invalidsequences.foreach(_ shouldBe None)
      duplicate shouldBe None
      value.eventId shouldBe original.id
    }

    "consume actual event content and reference formatting with inert or absent digest annotations" in {
      Given("an index and an actual event with the same ID, sequence and complete delivery key")
      val original = _event()
      val value = _index(original)
      val changed = original.copy(name = "retained.actual", payload = Map("long" -> 7L))
      changed._delivery_lookup_key = original._delivery_lookup_key
      val body = "cncf.event-record.v2: \n" + parse(EventRecordJsonV2.encode(changed)).toOption.get.spaces2 + "\n "
      val reference = value.toRecord.getString("event_reference").get
      val normalizedreference = "cncf.event-reference.v1: " + parse(reference.stripPrefix("cncf.event-reference.v1:")).toOption.get.spaces2
      val base = Record(value.toRecord.fields.filterNot(field =>
        field.key == "event_body_digest" || field.key == "event_reference") ++
        Record.dataAuto("event_reference" -> normalizedreference, "extra" -> "annotation").fields)
      val variants = Vector(base,
        Record(base.fields ++ Record.dataAuto("event_body_digest" -> "stale diagnostic").fields),
        Record(base.fields ++ Record.dataAuto("event_body_digest" -> 42).fields),
        Record(base.fields ++ Record.dataAuto("event_body_digest" -> "first", "event_body_digest" -> "second",
          "extra" -> "another").fields))
      When("unused metadata and equivalent reference formatting are consumed with the actual event")
      val recovered = variants.map(row => _take(EventDeliveryIndexRecordV1.decodeC(row)))
      val eventsread = recovered.map(index => _take(index.validateEventC(body)))
      Then("every index selects the changed retained content without requiring body-copy equality")
      eventsread.foreach { event =>
        event.name shouldBe "retained.actual"
        event.payload shouldBe Map("long" -> 7L)
        event._delivery_lookup_key shouldBe original._delivery_lookup_key
      }
      recovered.map(_.eventBodyDigest) shouldBe Vector("", "stale diagnostic", "", "first")
    }

    "prepare no index for transient or key-free facts and never invent a key" in {
      Given("key-free, transient and unissued facts and a missing index collection")
      val transient = _event()
      val skipped = transient.copy(persistent = false)
      skipped._delivery_lookup_key = transient._delivery_lookup_key
      val unissued = _event().copy(sequence = 0L)
      unissued._delivery_lookup_key = transient._delivery_lookup_key
      When("index preparation consumes each actual fact")
      val skippedresults = Vector(_take(EventDeliveryIndexRecordV1.prepareCreateC(_indexes, _event(None))),
        _take(EventDeliveryIndexRecordV1.prepareCreateC(_indexes, skipped)))
      val invalidresults = Vector(EventDeliveryIndexRecordV1.prepareCreateC(null, _event()).toOption,
        EventDeliveryIndexRecordV1.prepareCreateC(_indexes, unissued).toOption)
      Then("no skipped or incompatible input invents an index or delivery key")
      skippedresults.foreach(_ shouldBe None)
      invalidresults.foreach(_ shouldBe None)
    }

    "consume normalized actual V2 envelope and inert index metadata through SQLite close and reopen" in {
      Given("a SQLite event/index pair with actual normalized JSON and stale body diagnostics")
      val file = _temporary_directory("cncf-v2-index-").resolve("events.sqlite")
      val original = _event()
      val eventcreate = _take(EventRecordJsonV2.createSideEffectC(_events, original))
      val indexcreate = _index_effect(original)
      val retained = original.copy(name = "retained.actual", payload = Map("long" -> 7L))
      retained._delivery_lookup_key = original._delivery_lookup_key
      val normalized = "cncf.event-record.v2: \n" + parse(EventRecordJsonV2.encode(retained)).toOption.get.spaces2 + "\n "
      val first = SqlDataStore.sqlite(file.toString)
      try {
        _take(first.create(_events, eventcreate.entryId, Record.dataAuto("event_json" -> normalized)))
        _take(first.create(_indexes, indexcreate.entryId, Record(indexcreate.record.fields.filterNot(_.key == "event_body_digest") ++
          Record.dataAuto("event_body_digest" -> "stale diagnostic", "annotation" -> "unused").fields)))
      } finally _take(first.closeC())
      When("a separate provider physically reopens and consumes the actual retained pair")
      val reopened = SqlDataStore.sqlite(file.toString)
      try {
        val logical = _take(reopened.load(_indexes, indexcreate.entryId)).get
        val recoveredindex = _take(EventDeliveryIndexRecordV1.decodeC(Record(logical.fields.filterNot(_.key == "id"))))
        val eventrow = _take(reopened.load(_events, DataStore.StringEntryId(recoveredindex.eventId.value))).get
        val bytes = eventrow.getString("event_json").get
        _take(recoveredindex.validateLookupC(indexcreate.entryId, recoveredindex.lookupKeyBytes))
        val recovered = _take(recoveredindex.validateEventC(bytes))
        Then("the actual changed retained facts and complete discriminator survive")
        bytes shouldBe normalized
        recovered shouldBe retained
        recovered._delivery_lookup_key shouldBe original._delivery_lookup_key
        recoveredindex.eventBodyDigest shouldBe "stale diagnostic"
      } finally _take(reopened.closeC())
    }
  }
}
