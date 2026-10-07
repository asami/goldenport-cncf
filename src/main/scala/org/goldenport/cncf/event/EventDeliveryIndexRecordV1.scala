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
 * Pure lookup/integrity facts, not deduplication or dispatch authority.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
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
          event.sequence == sequence && EventDeliveryIndexRecordV1.digest(storedEnvelope) == eventBodyDigest &&
          event._delivery_lookup_key.exists(key => CommittedDeliveryKeyJsonV1.encodeStorage(key) == lookupKeyBytes),
          "Delivery index referenced event differs")
        event
      }
    }
}

private[cncf] object EventDeliveryIndexRecordV1 {
  val schema = "cncf.event-delivery-index.v1"
  private val fields = Set("index_schema", "lookup_key", "event_reference", "sequence", "event_body_digest")
  private val referencePrefix = "cncf.event-reference.v1:"

  /** Transient/key-free events have no index; unsupported keyed facts reject. */
  def prepareCreateC(collection: DataStore.CollectionId, event: EventRecord):
      Consequence[Option[EntityVersionedSideEffect.Create]] =
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

  def decodeC(record: Record): Consequence[EventDeliveryIndexRecordV1] = safely {
    require(record != null && record.fields.size == fields.size && record.fields.map(_.key).toSet == fields,
      "Missing/duplicate/unknown index fields")
    def text(key: String): String = record.fields.find(_.key == key).get.value.single match {
      case value: String => value
      case Some(value: String) => value
      case _ => throw new IllegalArgumentException("Index field must be exact text")
    }
    require(text("index_schema") == schema, "Unknown index schema")
    val key = text("lookup_key")
    require(CommittedDeliveryKeyJsonV1.decodeStorageC(key).toOption.nonEmpty, "Invalid index key")
    val seq = text("sequence")
    require(seq.length <= 19 && seq.matches("[1-9][0-9]*"), "Invalid index sequence")
    val sequence = seq.toLongOption.getOrElse(throw new IllegalArgumentException("Index sequence overflow"))
    val bodyDigest = text("event_body_digest")
    require(bodyDigest.matches("[0-9a-f]{64}"), "Invalid index digest")
    new EventDeliveryIndexRecordV1(key, readReference(text("event_reference")), sequence, bodyDigest)
  }

  private[event] def reference(id: EventId): String = {
    require(id != null && id.timestamp.nonEmpty && id.entropy.nonEmpty, "Missing issued EventId")
    def text(value: String): Json = {
      require(value != null && value.trim.nonEmpty && value.length <= 128, "Invalid EventId part")
      Json.fromString(value)
    }
    referencePrefix + Json.obj("major" -> text(id.major), "minor" -> text(id.minor),
      "timestamp" -> Json.fromString(id.timestamp.get.toString), "entropy" -> text(id.entropy.get)).noSpaces
  }
  private def readReference(value: String): EventId = {
    require(value.length <= 1024 && value.startsWith(referencePrefix), "Invalid EventId reference")
    val parts = parse(value.substring(referencePrefix.length)).toOption.flatMap(_.asObject).map(_.toMap).getOrElse(
      throw new IllegalArgumentException("Invalid EventId reference JSON"))
    require(parts.keySet == Set("major", "minor", "timestamp", "entropy"), "Invalid EventId reference fields")
    def text(key: String) = parts(key).asString.getOrElse(throw new IllegalArgumentException("Invalid EventId part"))
    val id = EventId(text("major"), text("minor"), Some(Instant.parse(text("timestamp"))), Some(text("entropy")))
    require(reference(id) == value, "Noncanonical/ambiguous EventId reference")
    id
  }
  private[event] def digest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
  private[event] def safely[A](f: => A): Consequence[A] = try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Delivery index canonical admission failed")
  }
}
