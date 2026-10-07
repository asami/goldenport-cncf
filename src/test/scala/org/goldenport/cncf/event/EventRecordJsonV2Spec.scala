package org.goldenport.cncf.event

import java.time.Instant
import java.nio.file.Files
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.statemachine.*
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
final class EventRecordJsonV2Spec extends AnyWordSpec with Matchers {
  private given ExecutionContext = ExecutionContext.create()
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"unexpected rejection: $other")
  }
  private val time = Instant.parse("2026-09-30T00:00:00.123456789Z")
  private val entityType = EntityCollectionId("test", "key", "subject")
  private val entityId = take(EntityId.restore("test", "entity", entityType,
    Instant.parse("2026-09-30T00:00:00.123Z"), "issued_1"))
  private val machine = CmlStateMachineIdentity("lifecycle")
  private val version = CmlStateMachineVersion(3)
  private val trigger = CmlStateMachineTriggerIdentity(machine, "change")
  private def state(parts: String*) = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(parts.toVector))
  private val targets = Vector(CmlStateMachineTransitionTarget.State(state("After", "Leaf")),
    CmlStateMachineTransitionTarget.ShallowHistory(CmlStateMachineShallowHistoryTarget(
      state("Composite"), state("Composite", "Fallback"))), CmlStateMachineTransitionTarget.Final)
  private def lookupKey(target: CmlStateMachineTransitionTarget = targets.head, mode: Int = 2, reverse: Boolean = false) = {
    val identity = CmlStateMachineTriggerContextIdentity(trigger)
    val fields = Vector("subject" -> CmlStateMachineScalarType.StringValue,
      "flag" -> CmlStateMachineScalarType.BooleanValue, "count" -> CmlStateMachineScalarType.IntegerValue)
      .map { case (name, scalar) => CmlStateMachineTriggerContextField(
        CmlStateMachineTriggerContextFieldIdentity(identity, name), scalar) }
    val context = if (mode == 0) None else Some(CmlStateMachineTriggerContext(identity, version,
      if (reverse) fields.reverse else fields))
    CommittedDeliveryLookupKey("explicit-key", entityId,
      CmlTransitionBinding(ComponentId("test.key.Component"), entityType, machine, version,
        CmlStateMachineTransitionIdentity(machine, 4), state("Before", "Leaf"), target, trigger,
        if (mode == 2) Some(CmlStateMachineOperationIdentity("service", "change")) else None, context), "operation")
  }
  private def event(delivery: Option[CommittedDeliveryLookupKey] = Some(lookupKey())): EventRecord = {
    val value = EventRecord(EventId("test", "v2", Some(time), Some("event_1")), "fact", "committed-transition",
      Map("long" -> 9007199254740993L, "decimal" -> BigDecimal("1.2300"), "items" -> Vector(1, true)),
      Map("trace" -> "opaque"), time, true, EventRecord.Status.Stored, EventLane.Transactional, Long.MaxValue)
    value._delivery_lookup_key = delivery
    value
  }
  private val events = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "key", "events"))
  private val indexes = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "key", "indexes"))
  private def indexEffect(value: EventRecord) = take(EventDeliveryIndexRecordV1.prepareCreateC(indexes, value)).get
  private def index(value: EventRecord) = take(EventDeliveryIndexRecordV1.decodeC(indexEffect(value).record))

  "Complete typed committed-delivery key" should {
    "restore all target and optional operation/context variants with ordered fields" in {
      for { target <- targets; mode <- 0 to 2; reverse <- Vector(false, true) } {
        val original = lookupKey(target, mode, reverse)
        val encoded = CommittedDeliveryKeyJsonV1.encodeStorage(original)
        val recovered = take(CommittedDeliveryKeyJsonV1.decodeStorageC(encoded))
        recovered shouldBe original
        recovered.entityId.collection shouldBe entityType
        recovered.entityId.timestamp shouldBe entityId.timestamp
        recovered.entityId.entropy shouldBe entityId.entropy
        recovered.binding shouldBe original.binding
        CommittedDeliveryKeyJsonV1.encodeStorage(recovered) shouldBe encoded
      }
      CommittedDeliveryKeyJsonV1.encodeStorage(lookupKey(reverse = true)) should not be
        CommittedDeliveryKeyJsonV1.encodeStorage(lookupKey())
    }

    "keep every lookup constituent distinct without rendered-selector identity" in {
      val original = lookupKey()
      val binding = original.binding
      val alternatives = Vector(original.copy(invocationKey = "another"), original.copy(operationId = "other"),
        original.copy(entityId = take(EntityId.restore("test", "entity", entityType, entityId.timestamp.get, "issued_2"))),
        original.copy(binding = binding.copy(componentId = ComponentId("test.key.Other"))),
        original.copy(binding = binding.copy(entityType = EntityCollectionId("test", "key", "other"))),
        original.copy(binding = binding.copy(transition = CmlStateMachineTransitionIdentity(machine, 5))),
        original.copy(binding = binding.copy(source = state("Before", "Other"))),
        lookupKey(targets(1)), lookupKey(targets(2)), lookupKey(mode = 1), lookupKey(mode = 0), lookupKey(reverse = true))
      val encodings = (original +: alternatives).map(CommittedDeliveryKeyJsonV1.encodeStorage)
      encodings.distinct.size shouldBe encodings.size
      alternatives.foreach(value => take(CommittedDeliveryKeyJsonV1.decodeStorageC(
        CommittedDeliveryKeyJsonV1.encodeStorage(value))) shouldBe value)
    }

    "reject corrupt, noncanonical, unsupported and over-bounded structural representations" in {
      val bytes = CommittedDeliveryKeyJsonV1.encodeStorage(lookupKey())
      Vector(null, bytes.stripPrefix(CommittedDeliveryKeyJsonV1.prefix), bytes + " ",
        bytes.replace("\"schema\":", "\"unknown\":null,\"schema\":"),
        bytes.replace("\"invocationKey\":\"explicit-key\"", "\"invocationKey\":\"x\",\"invocationKey\":\"explicit-key\""),
        bytes.replace("\"version\":3", "\"version\":3.0"),
        bytes.replace("\"version\":3", "\"version\":0"),
        bytes.replace("\"order\":4", "\"order\":-1"),
        bytes.replace("\"type\":\"integer\"", "\"type\":\"decimal\""),
        bytes.replace("\"entropy\":\"issued_1\"", "\"entropy\":\"invalid-entropy\""),
        bytes.replace("00:00:00.123Z", "00:00:00.123000001Z"),
        bytes.replace("cncf.committed-delivery-key.v1", "cncf.committed-delivery-key.v99"))
        .foreach(value => CommittedDeliveryKeyJsonV1.decodeStorageC(value).toOption shouldBe None)
      intercept[IllegalArgumentException](CommittedDeliveryKeyJsonV1.encodeStorage(lookupKey().copy(invocationKey = "x" * 8193)))
      CommittedDeliveryKeyJsonV1.decodeStorageC("x" * 65537).toOption shouldBe None
    }
  }

  "Separate V2 event envelope" should {
    "retain exact V1 body and full discriminator while preserving V1 rejection" in {
      Vector(None, Some(lookupKey())).foreach { delivery =>
        val original = event(delivery)
        val bytes = EventRecordJsonV2.encodeStorage(original)
        val recovered = take(EventRecordJsonV2.decodeStorageC(bytes))
        recovered shouldBe original
        recovered._delivery_lookup_key shouldBe delivery
        recovered.payload("long") shouldBe 9007199254740993L
        EventRecordJsonV2.encodeStorage(recovered) shouldBe bytes
        original._delivery_lookup_key shouldBe delivery
        EventRecordJsonV1.decodeStorageC(bytes).toOption shouldBe None
      }
      EventRecordJsonV1.createSideEffectC(events, event()).toOption shouldBe None
      EventRecordJsonV2.decodeStorageC(EventRecordJsonV1.encodeStorage(event(None))).toOption shouldBe None
    }

    "reject corrupt envelope/body/key and never fall back or silently lose a discriminator" in {
      val bytes = EventRecordJsonV2.encodeStorage(event())
      Vector(null, bytes.stripPrefix("cncf.event-record.v2:"), bytes + " ",
        bytes.replace("cncf.event-record.v2", "cncf.event-record.v3"),
        bytes.replace("\"body\":", "\"extra\":null,\"body\":"),
        bytes.replace("\"delivery\":", "\"delivery\":null,\"delivery\":"),
        bytes.replace("\"kind\":\"state\"", "\"kind\":\"deep-history\""),
        bytes.replace("\"lane\":\"transactional\"", "\"lane\":\"unknown\""))
        .foreach(value => EventRecordJsonV2.decodeStorageC(value).toOption shouldBe None)
      EventRecordJsonV2.createSideEffectC(null, event()).toOption shouldBe None
      EventRecordJsonV2.createSideEffectC(events, event().copy(persistent = false)).toOption shouldBe None
      EventRecordJsonV2.createSideEffectC(events, event().copy(sequence = 0L)).toOption shouldBe None
    }
  }

  "Delivery index codec and pure integrity checks" should {
    "round-trip exact fields and reject hash-row aliasing, absent or mismatched references" in {
      val original = event()
      val value = index(original)
      value.sequence shouldBe Long.MaxValue
      value.entryId.print should fullyMatch regex "delivery-v1-[0-9a-f]{64}"
      take(value.validateLookupC(value.entryId, value.lookupKeyBytes))
      value.validateLookupC(value.entryId, CommittedDeliveryKeyJsonV1.encodeStorage(lookupKey().copy(invocationKey = "different"))).toOption shouldBe None
      value.validateLookupC(DataStore.StringEntryId("other"), value.lookupKeyBytes).toOption shouldBe None
      val exact = EventRecordJsonV2.encodeStorage(original)
      take(value.validateEventC(exact))._delivery_lookup_key shouldBe original._delivery_lookup_key
      val changedPayload = event().copy(payload = Map("long" -> 7L))
      changedPayload._delivery_lookup_key = original._delivery_lookup_key
      Vector(null, EventRecordJsonV2.encodeStorage(event(None)), EventRecordJsonV2.encodeStorage(changedPayload),
        EventRecordJsonV2.encodeStorage(event(Some(lookupKey().copy(operationId = "different"))))).foreach(bytes =>
        value.validateEventC(bytes).toOption shouldBe None)
      Vector("0", "01", "+1", "9223372036854775808").foreach(sequence =>
        EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields.filterNot(_.key == "sequence") ++
          Record.dataAuto("sequence" -> sequence).fields)).toOption shouldBe None)
      EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields :+ value.toRecord.fields.head)).toOption shouldBe None
      EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields ++ Record.dataAuto("extra" -> "x").fields)).toOption shouldBe None
      EventDeliveryIndexRecordV1.decodeC(Record(value.toRecord.fields.filterNot(_.key == "event_body_digest") ++
        Record.dataAuto("event_body_digest" -> value.eventBodyDigest.toUpperCase).fields)).toOption shouldBe None
    }

    "prepare no index for transient or key-free facts and never invent a key" in {
      take(EventDeliveryIndexRecordV1.prepareCreateC(indexes, event(None))) shouldBe None
      val transient = event()
      val skipped = transient.copy(persistent = false)
      skipped._delivery_lookup_key = transient._delivery_lookup_key
      take(EventDeliveryIndexRecordV1.prepareCreateC(indexes, skipped)) shouldBe None
      EventDeliveryIndexRecordV1.prepareCreateC(null, event()).toOption shouldBe None
      val unissued = event().copy(sequence = 0L)
      unissued._delivery_lookup_key = transient._delivery_lookup_key
      EventDeliveryIndexRecordV1.prepareCreateC(indexes, unissued).toOption shouldBe None
    }

    "preserve canonical V2 envelope and index text through SQLite close and reopen" in {
      val file = Files.createTempDirectory("cncf-v2-index-").resolve("events.sqlite")
      val original = event()
      val eventCreate = take(EventRecordJsonV2.createSideEffectC(events, original))
      val indexCreate = indexEffect(original)
      val first = SqlDataStore.sqlite(file.toString)
      try {
        take(first.create(events, eventCreate.entryId, eventCreate.record))
        take(first.create(indexes, indexCreate.entryId, indexCreate.record))
      } finally take(first.closeC())
      val reopened = SqlDataStore.sqlite(file.toString)
      try {
        val logical = take(reopened.load(indexes, indexCreate.entryId)).get
        val recoveredIndex = take(EventDeliveryIndexRecordV1.decodeC(Record(logical.fields.filterNot(_.key == "id"))))
        val eventRow = take(reopened.load(events, DataStore.StringEntryId(recoveredIndex.eventId.value))).get
        val bytes = eventRow.getString("event_json").get
        bytes shouldBe EventRecordJsonV2.encodeStorage(original)
        take(recoveredIndex.validateLookupC(indexCreate.entryId, recoveredIndex.lookupKeyBytes))
        val recovered = take(recoveredIndex.validateEventC(bytes))
        recovered shouldBe original
        recovered._delivery_lookup_key shouldBe original._delivery_lookup_key
        recoveredIndex.toRecord shouldBe indexCreate.record
      } finally take(reopened.closeC())
    }
  }
}
