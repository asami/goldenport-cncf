package org.goldenport.cncf.event

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedSideEffect}
import org.goldenport.record.Record

/**
 * Separate strict envelope; V1 is unchanged and never used as a fallback.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
object EventRecordJsonV2 {
  val schemaVersion = "cncf.event-record.v2"
  private val prefix = schemaVersion + ":"
  private val maximumBytes = 131072

  def encode(value: EventRecord): String = {
    require(value != null && value._delivery_lookup_key != null, "Missing event")
    // This key-free copy is local to this encoder; the complete key is retained
    // below. No public helper can strip it to bypass V1 admission.
    val body = EventRecordJsonV1.encode(value.copy())
    val encoded = Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
      "body" -> parse(body).toOption.get,
      "delivery" -> value._delivery_lookup_key.map(CommittedDeliveryKeyJsonV1.toJson).getOrElse(Json.Null)).noSpaces
    require(encoded.getBytes(UTF_8).length <= maximumBytes, "V2 event exceeds byte bound")
    encoded
  }

  def decodeC(encoded: String): Consequence[EventRecord] = safely {
    require(encoded != null && encoded.getBytes(UTF_8).length <= maximumBytes, "Invalid V2 event bytes")
    val root = parse(encoded).toOption.flatMap(_.asObject).map(_.toMap).getOrElse(
      throw new IllegalArgumentException("Invalid V2 envelope"))
    require(root.keySet == Set("schemaVersion", "body", "delivery") &&
      root("schemaVersion").asString.contains(schemaVersion), "Unknown V2 schema/fields")
    val event = EventRecordJsonV1.decodeC(root("body").noSpaces).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid V1 event body"))
    event._delivery_lookup_key = if (root("delivery").isNull) None
      else Some(CommittedDeliveryKeyJsonV1.fromJson(root("delivery")))
    require(encode(event) == encoded, "Noncanonical or ambiguous V2 bytes")
    event
  }

  def encodeStorage(value: EventRecord): String = prefix + encode(value)
  def decodeStorageC(value: String): Consequence[EventRecord] =
    if (value == null || !value.startsWith(prefix) || value.getBytes(UTF_8).length > maximumBytes + prefix.length)
      Consequence.stateConflict("Invalid V2 event storage prefix/bound")
    else decodeC(value.substring(prefix.length))

  def createSideEffectC(collection: DataStore.CollectionId, value: EventRecord): Consequence[EntityVersionedSideEffect.Create] =
    safely {
      require(collection != null && value != null && value.persistent && value.lane == EventLane.Transactional,
        "V2 Create requires persistent transactional facts")
      val bytes = encodeStorage(value)
      require(decodeStorageC(bytes).toOption.nonEmpty, "V2 Create cannot round-trip")
      EntityVersionedSideEffect.Create(collection, DataStore.StringEntryId(value.id.value), Record.dataAuto("event_json" -> bytes))
    }

  /** Preserve the internal discriminator while assigning a proposal, not a
    * reserved sequence. No source mutation, storage or retry is performed.
    */
  private[cncf] def prepareCreateC(collection: DataStore.CollectionId, value: EventRecord, proposedSequence: Long):
      Consequence[(EventRecord, EntityVersionedSideEffect.Create)] =
    if (value == null || value.sequence != 0L || proposedSequence <= 0L)
      Consequence.configurationInvalid("V2 preparation requires unissued facts and a positive sequence proposal")
    else {
      val proposed = value.copy(sequence = proposedSequence)
      proposed._delivery_lookup_key = value._delivery_lookup_key
      createSideEffectC(collection, proposed).flatMap { effect =>
        decodeStorageC(effect.record.getString("event_json").get).map(_ -> effect)
      }
    }

  private def safely[A](f: => A): Consequence[A] = try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("V2 event canonical admission failed")
  }
}
