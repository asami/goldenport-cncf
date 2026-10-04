package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * Closed bounded operational version one, independent of signed Job schemas.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOwnedBlobQuotaCodec {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _ledger_fields = Set("version", "maximumOwnedBlobs", "maximumOwnedBytes", "maximumPayloadBytes", "entries")
  private val _entry_fields = Set("blobId", "ownerSha256", "contentType", "byteSize", "payloadSha256", "token", "state", "bindingSha256")
  private val _invalid = DurableJobOwnedBlobResourcePolicy.invalidLabel

  def encode(record: DurableJobOwnedBlobQuotaRecord): Consequence[String] = _result {
    for {
      _ <- _validate(record)
      text = Json.obj(
        "version" -> Json.fromInt(1),
        "maximumOwnedBlobs" -> Json.fromInt(record.policy.maxownedblobs),
        "maximumOwnedBytes" -> Json.fromLong(record.policy.maxownedbytes),
        "maximumPayloadBytes" -> Json.fromInt(record.policy.maxpayloadbytes),
        "entries" -> Json.fromValues(record.entries.map(_entry_json))
      ).noSpaces
      _ <- _wire(text)
      _ <- _parser.parse(text).left.map(_ => _invalid)
    } yield text
  }

  def decode(text: String): Consequence[DurableJobOwnedBlobQuotaRecord] = _result {
    for {
      _ <- _wire(text)
      json <- _parser.parse(text).left.map(_ => _invalid)
      obj <- _object(json, _ledger_fields)
      version <- _long(obj("version").get)
      _ <- Either.cond(version == 1L, (), _invalid)
      count <- _long(obj("maximumOwnedBlobs").get)
      bytes <- _long(obj("maximumOwnedBytes").get)
      payload <- _long(obj("maximumPayloadBytes").get)
      _ <- Either.cond(count > 0L && count <= 4096L && payload > 0L && payload <= 8388608L, (), _invalid)
      values <- obj("entries").get.asArray.toRight(_invalid)
      _ <- Either.cond(values.size <= DurableJobOwnedBlobResourcePolicy.maximumEntries, (), _invalid)
      entries <- _sequence(values.toVector.map(_entry))
      record = DurableJobOwnedBlobQuotaRecord(DurableJobOwnedBlobResourcePolicy(count.toInt, bytes, payload.toInt), entries)
      _ <- _validate(record)
    } yield record
  }

  def usage(record: DurableJobOwnedBlobQuotaRecord): Consequence[DurableJobOwnedBlobQuotaSnapshot] =
    _result(_validate(record).flatMap(_ => _usage(record)))

  private def _entry_json(entry: DurableJobOwnedBlobQuotaEntry): Json = Json.obj(
    "blobId" -> Json.fromString(entry.blobid), "ownerSha256" -> Json.fromString(entry.ownersha256),
    "contentType" -> Json.fromString(entry.contenttype), "byteSize" -> Json.fromLong(entry.bytesize),
    "payloadSha256" -> Json.fromString(entry.payloadsha256), "token" -> entry.token.fold(Json.Null)(Json.fromString),
    "state" -> Json.fromString(entry.state.toString), "bindingSha256" -> entry.bindingsha256.fold(Json.Null)(Json.fromString))

  private def _entry(json: Json): Either[String, DurableJobOwnedBlobQuotaEntry] = for {
    obj <- _object(json, _entry_fields)
    id <- obj("blobId").get.asString.toRight(_invalid)
    owner <- obj("ownerSha256").get.asString.toRight(_invalid)
    content <- obj("contentType").get.asString.toRight(_invalid)
    bytes <- _long(obj("byteSize").get)
    digest <- obj("payloadSha256").get.asString.toRight(_invalid)
    token <- _optional(obj("token").get)
    statevalue <- obj("state").get.asString.toRight(_invalid)
    state <- DurableJobOwnedBlobQuotaState.values.find(_.toString == statevalue).toRight(_invalid)
    binding <- _optional(obj("bindingSha256").get)
  } yield DurableJobOwnedBlobQuotaEntry(id, owner, content, bytes, digest, token, state, binding)

  private def _validate(record: DurableJobOwnedBlobQuotaRecord): Either[String, Unit] = {
    val entries = record.entries
    val ids = entries.map(_.blobid)
    val tokens = entries.flatMap(_.token)
    for {
      _ <- Either.cond(record.policy.isValid && entries.size <= DurableJobOwnedBlobResourcePolicy.maximumEntries, (), _invalid)
      _ <- Either.cond(ids == ids.sorted && ids.distinct.size == ids.size && tokens.distinct.size == tokens.size, (), _invalid)
      _ <- _sequence(entries.map { entry =>
        val statevalid = entry.state match {
          case DurableJobOwnedBlobQuotaState.Pending => entry.token.nonEmpty && entry.bindingsha256.isEmpty
          case DurableJobOwnedBlobQuotaState.Published => entry.bindingsha256.nonEmpty
          case DurableJobOwnedBlobQuotaState.Released => entry.bindingsha256.nonEmpty || entry.token.nonEmpty
        }
        Either.cond(DurableJobOwnedBlobResourcePolicy.canonicalId(entry.blobid).isSuccess &&
          DurableJobOwnedBlobResourcePolicy.hex(entry.ownersha256, 64) &&
          DurableJobOwnedBlobResourcePolicy.boundedText(entry.contenttype, DurableJobOwnedBlobResourcePolicy.maximumContentBytes) &&
          entry.bytesize >= 0L && entry.bytesize <= DurableJobOwnedBlobResourcePolicy.maximumPayloadBytes &&
          DurableJobOwnedBlobResourcePolicy.hex(entry.payloadsha256, 64) &&
          entry.token.forall(DurableJobOwnedBlobResourcePolicy.hex(_, 32)) &&
          entry.bindingsha256.forall(DurableJobOwnedBlobResourcePolicy.hex(_, 64)) && statevalid, (), _invalid)
      })
      usage <- _usage(record)
      _ <- Either.cond(usage.occupiedblobs <= record.policy.maxownedblobs && usage.payloadbytes <= record.policy.maxownedbytes, (), _invalid)
    } yield ()
  }

  private def _usage(record: DurableJobOwnedBlobQuotaRecord): Either[String, DurableJobOwnedBlobQuotaSnapshot] =
    try {
      val occupied = record.entries.filterNot(_.state == DurableJobOwnedBlobQuotaState.Released)
      val count = occupied.foldLeft(0L)((total, _) => Math.addExact(total, 1L))
      val bytes = occupied.foldLeft(0L)((total, entry) => Math.addExact(total, entry.bytesize))
      val pending = occupied.foldLeft(0L)((total, entry) =>
        if (entry.state == DurableJobOwnedBlobQuotaState.Pending) Math.addExact(total, 1L) else total)
      val released = record.entries.foldLeft(0L)((total, entry) =>
        if (entry.state == DurableJobOwnedBlobQuotaState.Released) Math.addExact(total, 1L) else total)
      Right(DurableJobOwnedBlobQuotaSnapshot(count, bytes, pending, released, record.entries.size,
        record.policy.maxownedblobs, record.policy.maxownedbytes, record.policy.maxpayloadbytes))
    } catch { case NonFatal(_) => Left(_invalid) }

  private def _object(json: Json, fields: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == fields).toRight(_invalid)
  private def _long(json: Json): Either[String, Long] = json.asNumber.flatMap(_.toLong).toRight(_invalid)
  private def _optional(json: Json): Either[String, Option[String]] =
    if (json.isNull) Right(None) else json.asString.toRight(_invalid).map(Some(_))

  private def _wire(text: String): Either[String, Unit] = {
    val maximum = DurableJobOwnedBlobResourcePolicy.maximumLedgerBytes
    if (text == null || text.length > maximum || text.getBytes(StandardCharsets.UTF_8).length > maximum) Left(_invalid)
    else {
      var index = 0
      var depth = 0
      var quoted = false
      var escaped = false
      while (index < text.length) {
        val character = text.charAt(index)
        if (quoted) {
          if (escaped) escaped = false
          else if (character == '\\') escaped = true
          else if (character == '"') quoted = false
        } else character match {
          case '"' => quoted = true
          case '{' | '[' =>
            depth += 1
            if (depth > DurableJobOwnedBlobResourcePolicy.maximumLedgerDepth) return Left(_invalid)
          case '}' | ']' => depth = math.max(0, depth - 1)
          case _ => ()
        }
        index += 1
      }
      Right(())
    }
  }

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (result, value) =>
      for { previous <- result; next <- value } yield previous :+ next
    }

  private def _result[A](result: => Either[String, A]): Consequence[A] =
    try result.fold(_ => DurableJobOwnedBlobResourcePolicy.invalid, Consequence.success)
    catch { case NonFatal(_) => DurableJobOwnedBlobResourcePolicy.invalid }
}
