package org.goldenport.cncf.event

import java.time.Instant
import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedSideEffect}
import org.goldenport.record.Record

/*
 * @since   Sep. 29, 2026
 * @version Sep. 30, 2026
 */
/** Pure bounded event encoding, not EventStore append or sequence authority. */
object EventRecordJsonV1 {
  val schemaVersion: String = "cncf.event-record.v1"
  private val _storage_prefix = "cncf.event-record.v1:"
  private val _maximum_bytes = 65536
  private val _root_fields = Set("schemaVersion", "id", "name", "kind", "payload",
    "attributes", "createdAt", "persistent", "status", "lane", "sequence")

  /** DbC encoding of an admitted record; never performs I/O. */
  def encode(value: EventRecord): String = {
    require(value != null && value.id != null && value.createdAt != null, "Missing event facts")
    require(value._delivery_lookup_key != null && value._delivery_lookup_key.isEmpty,
      "Committed delivery lookup has no admitted durable encoding")
    require(value.sequence > 0 && value.status == EventRecord.Status.Stored && value.lane != null,
      "Event sequence/status/lane is not admitted")
    require(value.id.timestamp != null && value.id.entropy != null &&
      value.id.timestamp.isDefined && value.id.entropy.isDefined,
      "EventId must have fully issued timestamp and entropy")
    val budget = new Budget
    val json = Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "id" -> Json.obj(
        "major" -> _name(value.id.major), "minor" -> _name(value.id.minor),
        "timestamp" -> value.id.timestamp.map(x => Json.fromString(x.toString)).getOrElse(Json.Null),
        "entropy" -> value.id.entropy.map(_name).getOrElse(Json.Null)),
      "name" -> _name(value.name), "kind" -> _name(value.kind),
      "payload" -> _payload(value.payload, 0, budget),
      "attributes" -> _attributes(value.attributes),
      "createdAt" -> Json.fromString(value.createdAt.toString),
      "persistent" -> Json.fromBoolean(value.persistent),
      "status" -> Json.fromString(value.status.value),
      "lane" -> Json.fromString(value.lane.value),
      "sequence" -> Json.fromString(value.sequence.toString))
    val encoded = json.noSpaces
    require(encoded.length <= _maximum_bytes, "Event encoding exceeds the bound")
    encoded
  }

  /** Only exact canonical bytes recover facts; never repairs or grants authority. */
  def decodeC(encoded: String): Consequence[EventRecord] =
    _safely_c {
      if (encoded == null || encoded.length > _maximum_bytes)
        Consequence.stateConflict("Event encoding is missing or oversized")
      else {
        val decoded = for {
          json <- parse(encoded).left.map(_ => "Invalid event JSON")
          root <- _fields(json, _root_fields)
          schema <- _text(root("schemaVersion"))
          _ <- Either.cond(schema == schemaVersion, (), "Unsupported event schema")
          id <- _fields(root("id"), Set("major", "minor", "timestamp", "entropy"))
          major <- _text(id("major"))
          minor <- _text(id("minor"))
          timestamp <- _optional_instant(id("timestamp"))
          entropy <- _optional_text(id("entropy"))
          name <- _text(root("name"))
          kind <- _text(root("kind"))
          payloadvalue <- _decode_payload(root("payload"), 0, new Budget)
          payload <- payloadvalue match {
            case map: Map[?, ?] => Right(map.asInstanceOf[Map[String, Any]])
            case _ => Left("Event payload is not a map")
          }
          attributes <- _decode_attributes(root("attributes"))
          created <- _instant(root("createdAt"))
          persistent <- root("persistent").asBoolean.toRight("Invalid event persistence flag")
          status <- _text(root("status"))
          _ <- Either.cond(status == EventRecord.Status.Stored.value, (), "Unsupported event status")
          lanetext <- _text(root("lane"))
          lane <- EventLane.values.find(_.value == lanetext).toRight("Unsupported event lane")
          sequencevalue <- _text(root("sequence"))
          sequence <- scala.util.Try(sequencevalue.toLong).toEither.left.map(_ => "Invalid event sequence")
          _ <- Either.cond(sequence > 0, (), "Unassigned event sequence")
        } yield EventRecord(EventId(major, minor, timestamp, entropy), name, kind, payload,
          attributes, created, persistent, EventRecord.Status.Stored, lane, sequence)
        decoded.fold(Consequence.stateConflict(_), value =>
          if (encode(value) == encoded) Consequence.success(value)
          else Consequence.stateConflict("Event bytes are not canonical or contain ambiguous fields"))
      }
    }

  /** Canonical non-JSON storage text preserves the body through SQL record decoding. */
  def encodeStorage(value: EventRecord): String = _storage_prefix + encode(value)

  /** Strict storage wrapper recovery, with no bare-JSON or alternate-prefix fallback. */
  def decodeStorageC(stored: String): Consequence[EventRecord] =
    if (stored == null || stored.length > _maximum_bytes + _storage_prefix.length ||
        !stored.startsWith(_storage_prefix))
      Consequence.stateConflict("Event storage prefix is missing or unsupported")
    else decodeC(stored.substring(_storage_prefix.length))

  /** Host supplies the collection and sequence; the containing plan admits the store. */
  def createSideEffectC(
    collection: DataStore.CollectionId,
    value: EventRecord
  ): Consequence[EntityVersionedSideEffect.Create] =
    _safely_c {
      if (collection == null || value == null || !value.persistent || value.lane != EventLane.Transactional)
        Consequence.configurationInvalid("Event Create requires an explicit collection and persistent transactional facts")
      else {
        val encoded = encode(value)
        decodeC(encoded).map(_ => EntityVersionedSideEffect.Create(collection,
          DataStore.StringEntryId(value.id.value), Record.dataAuto("event_json" -> (_storage_prefix + encoded))))
      }
    }

  /** Pure sequence proposal, not allocation or commit authority. Preserve the
    * internal delivery key so unsupported durable encoding cannot be bypassed
    * by a case-class copy that silently drops it.
    */
  private[cncf] def prepareCreateC(
    collection: DataStore.CollectionId,
    value: EventRecord,
    proposedSequence: Long
  ): Consequence[(EventRecord, EntityVersionedSideEffect.Create)] =
    if (value == null || value.sequence != 0L || proposedSequence <= 0L)
      Consequence.configurationInvalid("Event Create preparation requires unissued facts and a positive sequence proposal")
    else {
      val proposed = value.copy(sequence = proposedSequence)
      proposed._delivery_lookup_key = value._delivery_lookup_key
      createSideEffectC(collection, proposed).flatMap { effect =>
        decodeStorageC(effect.record.getString("event_json").get).map(record => record -> effect)
      }
    }

  private final class Budget {
    private var _remaining = 4096
    def admit(depth: Int): Unit = {
      require(depth <= 16 && _remaining > 0, "Event payload nesting/node bound exceeded")
      _remaining -= 1
    }
  }

  private def _name(value: String): Json = {
    require(value != null && value.trim.nonEmpty && value.length <= 128, "Invalid event name/key/ID component")
    Json.fromString(value)
  }

  private def _string(value: String): Json = {
    require(value != null && value.length <= 8192, "Invalid event string")
    Json.fromString(value)
  }

  private def _attributes(value: Map[String, String]): Json = {
    require(value != null && value.size <= 256, "Invalid event attributes")
    Json.obj(value.toVector.sortBy(_._1).map { case (key, item) =>
      _name(key)
      key -> _string(item)
    }*)
  }

  private def _tag(kind: String, value: Json): Json =
    Json.obj("type" -> Json.fromString(kind), "value" -> value)

  private def _payload(value: Any, depth: Int, budget: Budget): Json = {
    budget.admit(depth)
    value match {
      case text: String => _tag("string", _string(text))
      case boolean: Boolean => _tag("boolean", Json.fromBoolean(boolean))
      case number: Int => _tag("int", Json.fromString(number.toString))
      case number: Long => _tag("long", Json.fromString(number.toString))
      case number: BigDecimal => _tag("decimal", _string(number.bigDecimal.toString))
      case items: Vector[?] =>
        require(items.size <= 256, "Event vector bound exceeded")
        _tag("vector", Json.arr(items.map(_payload(_, depth + 1, budget))*))
      case items: Map[?, ?] =>
        require(items.size <= 256 && items.keys.forall(_.isInstanceOf[String]), "Invalid event map")
        val entries = items.toVector.map { case (key, item) => key.asInstanceOf[String] -> item }.sortBy(_._1)
        _tag("map", Json.obj(entries.map { case (key, item) =>
          _name(key)
          key -> _payload(item, depth + 1, budget)
        }*))
      case _ => throw new IllegalArgumentException("Unsupported event payload type")
    }
  }

  private def _decode_payload(json: Json, depth: Int, budget: Budget): Either[String, Any] = {
    budget.admit(depth)
    _fields(json, Set("type", "value")).flatMap { fields =>
      fields("type").asString.toRight("Missing event value type").flatMap {
        case "string" => fields("value").asString.toRight("Invalid event string")
        case "boolean" => fields("value").asBoolean.toRight("Invalid event boolean")
        case "int" => _number(fields("value"))(_.toInt)
        case "long" => _number(fields("value"))(_.toLong)
        case "decimal" => _number(fields("value"))(BigDecimal(_))
        case "vector" => fields("value").asArray.filter(_.size <= 256).toRight("Invalid event vector").flatMap {
          _.foldLeft[Either[String, Vector[Any]]](Right(Vector.empty)) { (acc, item) =>
            for { values <- acc; value <- _decode_payload(item, depth + 1, budget) } yield values :+ value
          }
        }
        case "map" => fields("value").asObject.filter(_.size <= 256).toRight("Invalid event map").flatMap {
          _.toVector.foldLeft[Either[String, Map[String, Any]]](Right(Map.empty)) {
            case (acc, (key, item)) =>
              for { values <- acc; value <- _decode_payload(item, depth + 1, budget) } yield values.updated(key, value)
          }
        }
        case _ => Left("Unsupported event value type")
      }
    }
  }

  private def _number[A](json: Json)(convert: String => A): Either[String, A] =
    json.asString.toRight("Event number must be tagged text").flatMap(value =>
      scala.util.Try(convert(value)).toEither.left.map(_ => "Invalid event number"))

  private def _fields(json: Json, expected: Set[String]): Either[String, Map[String, Json]] =
    json.asObject.map(_.toMap).filter(_.keySet == expected).toRight("Event fields differ")

  private def _text(json: Json): Either[String, String] =
    json.asString.filter(x => x.trim.nonEmpty && x.length <= 128).toRight("Invalid event metadata")

  private def _instant(json: Json): Either[String, Instant] =
    _text(json).flatMap(value => scala.util.Try(Instant.parse(value)).toEither.left.map(_ => "Invalid event instant"))

  private def _optional_text(json: Json): Either[String, Option[String]] =
    if (json.isNull) Right(None) else _text(json).map(Some(_))

  private def _optional_instant(json: Json): Either[String, Option[Instant]] =
    if (json.isNull) Right(None) else _instant(json).map(Some(_))

  private def _decode_attributes(json: Json): Either[String, Map[String, String]] =
    json.asObject.filter(_.size <= 256).toRight("Invalid event attributes").flatMap {
      _.toVector.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
        case (acc, (key, item)) => for {
          values <- acc
          value <- item.asString.toRight("Invalid event attribute")
        } yield values.updated(key, value)
      }
    }

  private def _safely_c[A](operation: => Consequence[A]): Consequence[A] =
    try operation catch {
      case NonFatal(_) => Consequence.stateConflict("Event canonical storage admission failed")
    }
}
