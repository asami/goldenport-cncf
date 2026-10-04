package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * Strict bounded version-1 operational ledger, separate from the signed Job codec.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobStorageQuotaCodec {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _ledger_fields = Set("version", "maximumJobs", "maximumCanonicalBytes", "entries")
  private val _entry_fields = Set("jobId", "committedBytes", "committedSha256", "committedProviderRevision", "pending")
  private val _pending_fields = Set("token", "expectedProviderRevision", "canonicalBytes", "canonicalSha256")
  private val _invalid = DurableJobStorageResourcePolicy.invalidLabel

  def encode(record: DurableJobStorageQuotaRecord): Consequence[String] =
    _result(for {
      _ <- _validate(record)
      text = Json.obj(
        "version" -> Json.fromInt(1),
        "maximumJobs" -> Json.fromInt(record.policy.maxstoredjobs),
        "maximumCanonicalBytes" -> Json.fromLong(record.policy.maxcanonicalbytes),
        "entries" -> Json.fromValues(record.entries.map(_entry_json))
      ).noSpaces
      _ <- _wire(text)
    } yield text)

  def decode(text: String): Consequence[DurableJobStorageQuotaRecord] =
    _result(for {
      _ <- _wire(text)
      json <- _parser.parse(text).left.map(_ => _invalid)
      obj <- _object(json, _ledger_fields)
      version <- _long(obj("version").get)
      _ <- Either.cond(version == 1L, (), _invalid)
      jobs <- _long(obj("maximumJobs").get)
      bytes <- _long(obj("maximumCanonicalBytes").get)
      _ <- Either.cond(jobs > 0L && jobs <= 4096L, (), _invalid)
      entriesjson <- obj("entries").get.asArray.toRight(_invalid)
      _ <- Either.cond(entriesjson.size <= 4096, (), _invalid)
      entries <- _sequence(entriesjson.toVector.map(_entry))
      record = DurableJobStorageQuotaRecord(DurableJobStorageResourcePolicy(jobs.toInt, bytes), entries)
      _ <- _validate(record)
    } yield record)

  def usage(record: DurableJobStorageQuotaRecord): Consequence[DurableJobStorageQuotaSnapshot] =
    _result(_validate(record).flatMap(_ => _usage(record)))

  private def _entry_json(entry: DurableJobStorageQuotaEntry): Json =
    Json.obj(
      "jobId" -> Json.fromString(entry.jobid),
      "committedBytes" -> Json.fromLong(entry.committedbytes),
      "committedSha256" -> entry.committedsha256.fold(Json.Null)(Json.fromString),
      "committedProviderRevision" -> entry.committedrevision.fold(Json.Null)(Json.fromLong),
      "pending" -> Json.fromValues(entry.pending.map { pending =>
        Json.obj(
          "token" -> Json.fromString(pending.token),
          "expectedProviderRevision" -> pending.expectedrevision.fold(Json.Null)(Json.fromLong),
          "canonicalBytes" -> Json.fromLong(pending.canonicalbytes),
          "canonicalSha256" -> Json.fromString(pending.canonicalsha256)
        )
      })
    )

  private def _entry(json: Json): Either[String, DurableJobStorageQuotaEntry] =
    for {
      obj <- _object(json, _entry_fields)
      id <- obj("jobId").get.asString.toRight(_invalid)
      bytes <- _long(obj("committedBytes").get)
      hash <- _optional(obj("committedSha256").get)(_.asString.toRight(_invalid))
      revision <- _optional(obj("committedProviderRevision").get)(_long)
      pendingjson <- obj("pending").get.asArray.toRight(_invalid)
      _ <- Either.cond(pendingjson.size <= DurableJobStorageResourcePolicy.maximumPending, (), _invalid)
      pending <- _sequence(pendingjson.toVector.map(_pending))
    } yield DurableJobStorageQuotaEntry(id, bytes, hash, revision, pending)

  private def _pending(json: Json): Either[String, DurableJobStorageQuotaPending] =
    for {
      obj <- _object(json, _pending_fields)
      token <- obj("token").get.asString.toRight(_invalid)
      expected <- _optional(obj("expectedProviderRevision").get)(_long)
      bytes <- _long(obj("canonicalBytes").get)
      hash <- obj("canonicalSha256").get.asString.toRight(_invalid)
    } yield DurableJobStorageQuotaPending(token, expected, bytes, hash)

  private def _validate(record: DurableJobStorageQuotaRecord): Either[String, Unit] = {
    val entries = record.entries
    val ids = entries.map(_.jobid)
    val tokens = entries.flatMap(_.pending.map(_.token))
    for {
      _ <- Either.cond(record.policy.isValid && entries.size <= 4096, (), _invalid)
      _ <- Either.cond(ids == ids.sorted && ids.distinct.size == ids.size, (), _invalid)
      _ <- Either.cond(tokens.distinct.size == tokens.size, (), _invalid)
      _ <- _sequence(entries.map { entry =>
        val committedvalid = (entry.committedbytes, entry.committedsha256, entry.committedrevision) match {
          case (0L, None, None) => entry.pending.nonEmpty
          case (bytes, Some(hash), Some(revision)) => _bytes_valid(bytes) && _hex(hash, 64) && revision > 0L
          case _ => false
        }
        for {
          _ <- Either.cond(DurableJobStorageResourcePolicy.validId(entry.jobid) && committedvalid, (), _invalid)
          _ <- Either.cond(entry.pending.size <= DurableJobStorageResourcePolicy.maximumPending, (), _invalid)
          _ <- Either.cond(entry.pending.forall { pending =>
            _hex(pending.token, 32) && _hex(pending.canonicalsha256, 64) &&
              _bytes_valid(pending.canonicalbytes) && pending.expectedrevision.forall(_ > 0L)
          }, (), _invalid)
        } yield ()
      })
      usage <- _usage(record)
      _ <- Either.cond(usage.retainedjobs <= record.policy.maxstoredjobs &&
        usage.canonicalbytes <= record.policy.maxcanonicalbytes, (), _invalid)
    } yield ()
  }

  private def _usage(record: DurableJobStorageQuotaRecord): Either[String, DurableJobStorageQuotaSnapshot] =
    try {
      val bytes = record.entries.foldLeft(0L)((total, entry) => Math.addExact(total, entry.heldBytes))
      val pending = record.entries.foldLeft(0)((total, entry) => Math.addExact(total, entry.pending.size))
      Right(DurableJobStorageQuotaSnapshot(record.entries.size, bytes, pending,
        record.policy.maxstoredjobs, record.policy.maxcanonicalbytes))
    } catch { case NonFatal(_) => Left(_invalid) }

  private def _object(json: Json, fields: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == fields).toRight(_invalid)

  private def _long(json: Json): Either[String, Long] =
    json.asNumber.flatMap(_.toLong).toRight(_invalid)

  private def _optional[A](json: Json)(decode: Json => Either[String, A]): Either[String, Option[A]] =
    if (json.isNull) Right(None) else decode(json).map(Some(_))

  private def _bytes_valid(bytes: Long): Boolean =
    bytes > 0L && bytes <= DurableJobStorageResourcePolicy.maximumRecordBytes

  private def _hex(text: String, length: Int): Boolean =
    text != null && text.length == length && text.forall(character =>
      (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))

  private def _wire(text: String): Either[String, Unit] = {
    val maximum = DurableJobStorageResourcePolicy.maximumLedgerBytes
    if (text == null || text.length > maximum || text.getBytes(StandardCharsets.UTF_8).length > maximum)
      Left(_invalid)
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
            if (depth > DurableJobStorageResourcePolicy.maximumLedgerDepth) return Left(_invalid)
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

  private def _result[A](result: Either[String, A]): Consequence[A] =
    result.fold(_ => DurableJobStorageResourcePolicy.invalid, Consequence.success)
}
