package org.goldenport.cncf.event

import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedSideEffect}
import org.goldenport.record.Record

/**
 * Pure addressed lookup facts, not deduplication or dispatch authority.
 * @since   Sep. 30, 2026
 * @version Oct. 12, 2026
 */
private[cncf] final class EventDeliveryIndexRecordV1 private[event] (
  val lookupKeyBytes: String,
  val eventId: EventId,
  val sequence: Long,
  val eventBodyDigest: String
) {
  def entryId: DataStore.StringEntryId = DataStore.StringEntryId(
    "delivery-v1-" + EventDeliveryIndexRecordV1.digest(lookupKeyBytes))
  def toRecord: Record = Record.dataAuto("index_schema" -> EventDeliveryIndexRecordV1.schema,
    "lookup_key" -> lookupKeyBytes, "event_reference" -> EventDeliveryIndexRecordV1.reference(eventId),
    "sequence" -> sequence.toString, "event_body_digest" -> eventBodyDigest)

  /** Full bytes are authoritative even if a lookup hash/row ID happens to match. */
  def validateLookupC(actualEntry: DataStore.EntryId, expectedKeyBytes: String): Consequence[Unit] =
    EventDeliveryIndexRecordV1.safely {
      require(CommittedDeliveryKeyJsonV1.decodeStorageC(expectedKeyBytes).toOption.nonEmpty,
        "Invalid requested delivery key")
      require(actualEntry != null && actualEntry == entryId && expectedKeyBytes == lookupKeyBytes,
        "Delivery index identity/key mismatch")
      ()
    }

  /** Caller must load the exact reference from its coherent admitted snapshot.
    * Missing/corrupt evidence rejects; there is no repair or fallback.
    */
  def validateEventC(storedEnvelope: String): Consequence[EventRecord] =
    EventRecordJsonV2.decodeStorageC(storedEnvelope).flatMap { event =>
      EventDeliveryIndexRecordV1.safely {
        require(event.persistent && event.lane == EventLane.Transactional && event.id == eventId &&
          event.sequence == sequence &&
          event._delivery_lookup_key.exists(key => CommittedDeliveryKeyJsonV1.encodeStorage(key) == lookupKeyBytes),
          "Delivery index referenced event differs")
        event
      }
    }
}

private[cncf] object EventDeliveryIndexRecordV1 {
  val schema = "cncf.event-delivery-index.v1"
  private val _consumed_fields = Set("index_schema", "lookup_key", "event_reference", "sequence")
  private val _reference_prefix = "cncf.event-reference.v1:"

  /** Transient/key-free events have no index; unsupported keyed facts reject. */
  def prepareCreateC(collection: DataStore.CollectionId, event: EventRecord):
      Consequence[Option[EntityVersionedSideEffect.Create]] = {
    if (collection == null || event == null || event._delivery_lookup_key == null)
      Consequence.configurationInvalid("Delivery index preparation is incomplete")
    else if (!event.persistent || event._delivery_lookup_key.isEmpty) Consequence.success(None)
    else safely {
      require(event.lane == EventLane.Transactional, "Delivery index requires transactional event")
      val bytes = EventRecordJsonV2.encodeStorage(event)
      val index = new EventDeliveryIndexRecordV1(CommittedDeliveryKeyJsonV1.encodeStorage(event._delivery_lookup_key.get),
        event.id, event.sequence, digest(bytes))
      require(decodeC(index.toRecord).toOption.nonEmpty && index.validateEventC(bytes).toOption.nonEmpty,
        "Delivery index cannot round-trip")
      Some(EntityVersionedSideEffect.Create(collection, index.entryId, index.toRecord))
    }
  }

  def decodeC(record: Record): Consequence[EventDeliveryIndexRecordV1] = safely {
    require(record != null && _consumed_fields.forall(key => record.fields.count(_.key == key) == 1),
      "Missing/duplicate consumed index fields")
    def _text_(key: String): String = {
       record.fields.find(_.key == key).get.value.single match {
      case value: String => value
      case Some(value: String) => value
      case _ => throw new IllegalArgumentException("Index field must be exact text")
    }
    }
    require(_text_("index_schema") == schema, "Unknown index schema")
    val key = _text_("lookup_key")
    require(CommittedDeliveryKeyJsonV1.decodeStorageC(key).toOption.nonEmpty, "Invalid index key")
    val seq = _text_("sequence")
    require(seq.length <= 19 && seq.matches("[1-9][0-9]*"), "Invalid index sequence")
    val sequence = seq.toLongOption.getOrElse(throw new IllegalArgumentException("Index sequence overflow"))
    // Legacy body-copy diagnostics are not event/index identity.
    val bodydigest = record.fields.filter(_.key == "event_body_digest").iterator
      .map(_.value.single).collectFirst {
        case value: String => value
        case Some(value: String) => value
      }.getOrElse("")
    new EventDeliveryIndexRecordV1(key, _read_reference(_text_("event_reference")), sequence, bodydigest)
  }

  private[event] def reference(id: EventId): String = {
    require(id != null && id.timestamp.nonEmpty && id.entropy.nonEmpty, "Missing issued EventId")
    def _text_(value: String): Json = {
      require(value != null && value.trim.nonEmpty && value.length <= 128, "Invalid EventId part")
      Json.fromString(value)
    }
    _reference_prefix + Json.obj("major" -> _text_(id.major), "minor" -> _text_(id.minor),
      "timestamp" -> Json.fromString(id.timestamp.get.toString), "entropy" -> _text_(id.entropy.get)).noSpaces
  }
  private def _read_reference(value: String): EventId = {
    require(value.length <= 1024 && value.startsWith(_reference_prefix), "Invalid EventId reference")
    val parts = parse(value.substring(_reference_prefix.length)).toOption.flatMap(_.asObject).map(_.toMap).getOrElse(
      throw new IllegalArgumentException("Invalid EventId reference JSON"))
    require(parts.keySet == Set("major", "minor", "timestamp", "entropy"), "Invalid EventId reference fields")
    def _text_(key: String): String = parts(key).asString
      .filter(text => text.trim.nonEmpty && text.length <= 128)
      .getOrElse(throw new IllegalArgumentException("Invalid EventId part"))
    EventId(_text_("major"), _text_("minor"), Some(Instant.parse(_text_("timestamp"))), Some(_text_("entropy")))
  }
  private[event] def digest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  private[event] def safely[A](f: => A): Consequence[A] = {
     try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Delivery index canonical admission failed")
  }
  }
}
