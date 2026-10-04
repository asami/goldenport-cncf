package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * Closed version one; paid encoding capacity includes both future ID vectors.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobLifetimeQuotaCodec {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _invalid = DurableJobLifetimeQuotaPolicy.invalidLabel
  private val _root_fields = Set("version", "maximumChargedTasks", "maximumHistoryCredits",
    "maximumAdmissions", "maximumLedgerJobs", "maximumLedgerBytes", "entries")
  private val _entry_fields = Set("jobId", "ownerSha256", "rootSha256", "rootToken", "rootState",
    "chargedTasks", "historyCredits", "admissions", "cancelCredits", "remainingNormals",
    "normalIds", "compensationClaims", "cancelClaimed")

  def validateWire(text: String): Consequence[Unit] = _result(_wire(text))

  def encode(record: DurableJobLifetimeQuotaRecord): Consequence[String] = _result {
    for {
      _ <- _validate(record)
      policy = record.policy
      text = Json.obj("version" -> Json.fromInt(1),
        "maximumChargedTasks" -> Json.fromLong(policy.lifetime.maxchargedtasks),
        "maximumHistoryCredits" -> Json.fromLong(policy.lifetime.maxhistorycredits),
        "maximumAdmissions" -> Json.fromLong(policy.lifetime.maxadmissions),
        "maximumLedgerJobs" -> Json.fromInt(policy.maxledgerjobs),
        "maximumLedgerBytes" -> Json.fromLong(policy.maxledgerbytes),
        "entries" -> Json.fromValues(record.entries.map(_entry_json))).noSpaces
      _ <- _wire(text)
      _ <- Either.cond(text.getBytes(StandardCharsets.UTF_8).length.toLong <= policy.maxledgerbytes, (), _invalid)
    } yield text
  }

  def decode(text: String): Consequence[DurableJobLifetimeQuotaRecord] = _result {
    for {
      _ <- _wire(text)
      json <- _parser.parse(text).left.map(_ => _invalid)
      obj <- _object(json, _root_fields)
      version <- _long(obj("version").get)
      _ <- Either.cond(version == 1L, (), _invalid)
      tasks <- _long(obj("maximumChargedTasks").get)
      history <- _long(obj("maximumHistoryCredits").get)
      admissions <- _long(obj("maximumAdmissions").get)
      jobs <- _long(obj("maximumLedgerJobs").get)
      bytes <- _long(obj("maximumLedgerBytes").get)
      _ <- Either.cond(jobs > 0L && jobs <= 4096L, (), _invalid)
      policy = DurableJobLifetimeQuotaPolicy(JobLifetimePolicy(tasks, history, admissions), jobs.toInt, bytes)
      _ <- Either.cond(policy.isValid && text.getBytes(StandardCharsets.UTF_8).length.toLong <= bytes, (), _invalid)
      values <- obj("entries").get.asArray.toRight(_invalid)
      _ <- Either.cond(values.size <= policy.maxledgerjobs, (), _invalid)
      entries <- _sequence(values.toVector.map(value => _entry(value, policy)))
      record = DurableJobLifetimeQuotaRecord(policy, entries)
      _ <- _validate(record)
    } yield record
  }

  def usage(record: DurableJobLifetimeQuotaRecord): Consequence[DurableJobLifetimeQuotaResourceSnapshot] =
    _result(_validate(record).map(_ => _usage(record)))

  private def _entry_json(entry: DurableJobLifetimeQuotaEntry): Json = Json.obj(
    "jobId" -> Json.fromString(entry.jobid), "ownerSha256" -> Json.fromString(entry.ownersha256),
    "rootSha256" -> Json.fromString(entry.rootsha256), "rootToken" -> Json.fromString(entry.roottoken),
    "rootState" -> Json.fromString(entry.rootstate.toString),
    "chargedTasks" -> Json.fromLong(entry.paid.chargedtasks),
    "historyCredits" -> Json.fromLong(entry.paid.historycredits),
    "admissions" -> Json.fromLong(entry.paid.admissions),
    "cancelCredits" -> Json.fromLong(entry.paid.cancelcredits),
    "remainingNormals" -> Json.fromLong(entry.remaining),
    "normalIds" -> Json.fromValues(entry.normalids.map(Json.fromString)),
    "compensationClaims" -> Json.fromValues(entry.claims.map(Json.fromString)),
    "cancelClaimed" -> Json.fromBoolean(entry.cancelclaimed))

  private def _entry(json: Json, policy: DurableJobLifetimeQuotaPolicy): Either[String, DurableJobLifetimeQuotaEntry] = for {
    obj <- _object(json, _entry_fields)
    id <- obj("jobId").get.asString.toRight(_invalid)
    owner <- obj("ownerSha256").get.asString.toRight(_invalid)
    hash <- obj("rootSha256").get.asString.toRight(_invalid)
    token <- obj("rootToken").get.asString.toRight(_invalid)
    statevalue <- obj("rootState").get.asString.toRight(_invalid)
    state <- DurableJobLifetimeQuotaRootState.values.find(_.toString == statevalue).toRight(_invalid)
    tasks <- _long(obj("chargedTasks").get)
    history <- _long(obj("historyCredits").get)
    admissions <- _long(obj("admissions").get)
    cancel <- _long(obj("cancelCredits").get)
    remaining <- _long(obj("remainingNormals").get)
    normals <- _ids(obj("normalIds").get, policy)
    claims <- _ids(obj("compensationClaims").get, policy)
    claimed <- obj("cancelClaimed").get.asBoolean.toRight(_invalid)
  } yield DurableJobLifetimeQuotaEntry(id, owner, hash, token, state,
    JobLifetimeSnapshot(tasks, history, admissions, cancel), remaining, normals, claims, claimed)

  private def _ids(json: Json, policy: DurableJobLifetimeQuotaPolicy): Either[String, Vector[String]] = for {
    values <- json.asArray.toRight(_invalid)
    _ <- Either.cond(values.size.toLong <= policy.lifetime.maxchargedtasks / 2L, (), _invalid)
    ids <- _sequence(values.toVector.map(_.asString.toRight(_invalid)))
  } yield ids

  private def _validate(record: DurableJobLifetimeQuotaRecord): Either[String, Unit] = {
    if (record == null || record.policy == null || !record.policy.isValid || record.entries == null ||
        record.entries.size > record.policy.maxledgerjobs || record.entries.exists(_ == null)) Left(_invalid)
    else {
      val policy = record.policy
      val ids = record.entries.map(_.jobid)
      for {
        _ <- Either.cond(ids.forall(DurableJobLifetimeQuotaPolicy.validId) && _sorted_unique(ids), (), _invalid)
        _ <- _sequence(record.entries.map(entry => _validate_entry(entry, policy)))
        usage = _usage(record)
        _ <- Either.cond(usage.heldbytes <= policy.maxledgerbytes, (), _invalid)
      } yield ()
    }
  }

  private def _validate_entry(entry: DurableJobLifetimeQuotaEntry, policy: DurableJobLifetimeQuotaPolicy): Either[String, Unit] = {
    val paid = entry.paid
    if (paid == null || entry.normalids == null || entry.claims == null || entry.rootstate == null ||
        entry.normalids.size.toLong > policy.lifetime.maxchargedtasks / 2L ||
        entry.claims.size.toLong > policy.lifetime.maxchargedtasks / 2L) Left(_invalid)
    else {
      val pendingvalid = entry.rootstate != DurableJobLifetimeQuotaRootState.Pending ||
        (entry.normalids.isEmpty && entry.claims.isEmpty && !entry.cancelclaimed)
      Either.cond(DurableJobLifetimeQuotaPolicy.validId(entry.jobid) &&
        DurableJobLifetimeQuotaPolicy.hex(entry.ownersha256, 64) &&
        DurableJobLifetimeQuotaPolicy.hex(entry.rootsha256, 64) &&
        DurableJobLifetimeQuotaPolicy.hex(entry.roottoken, 32) && policy.lifetime._fits(paid) &&
        paid.cancelcredits <= paid.admissions && entry.remaining >= 0L &&
        entry.remaining <= paid.chargedtasks / 2L &&
        entry.normalids.size.toLong + entry.remaining <= paid.chargedtasks / 2L &&
        entry.normalids.forall(DurableJobLifetimeQuotaPolicy.validId) &&
        entry.claims.forall(DurableJobLifetimeQuotaPolicy.validId) &&
        _sorted_unique(entry.normalids) && _sorted_unique(entry.claims) &&
        entry.claims.toSet.subsetOf(entry.normalids.toSet) && pendingvalid, (), _invalid)
    }
  }

  private def _usage(record: DurableJobLifetimeQuotaRecord): DurableJobLifetimeQuotaResourceSnapshot = {
    def _sum_(select: DurableJobLifetimeQuotaEntry => Long): Long =
      record.entries.foldLeft(0L)((total, entry) => Math.addExact(total, select(entry)))
    val policy = record.policy
    DurableJobLifetimeQuotaResourceSnapshot(record.entries.size, _sum_(_.heldBytes),
      _sum_(_.paid.chargedtasks), _sum_(_.paid.historycredits), _sum_(_.paid.admissions),
      _sum_(_.paid.cancelcredits), policy.lifetime.maxchargedtasks, policy.lifetime.maxhistorycredits,
      policy.lifetime.maxadmissions, policy.maxledgerjobs, policy.maxledgerbytes)
  }

  private def _sorted_unique(ids: Vector[String]): Boolean = ids == ids.sorted && ids.distinct.size == ids.size
  private def _object(json: Json, fields: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == fields).toRight(_invalid)
  private def _long(json: Json): Either[String, Long] = json.asNumber.flatMap(_.toLong).toRight(_invalid)
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (result, value) =>
      for { previous <- result; next <- value } yield previous :+ next
    }

  private def _wire(text: String): Either[String, Unit] = {
    val maximum = DurableJobLifetimeQuotaPolicy.maximumLedgerBytes
    if (text == null || text.isEmpty || text.length > maximum ||
        text.getBytes(StandardCharsets.UTF_8).length > maximum) Left(_invalid)
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
            if (depth > DurableJobLifetimeQuotaPolicy.maximumLedgerDepth) return Left(_invalid)
          case '}' | ']' => depth -= 1
          case _ => ()
        }
        index += 1
      }
      Right(())
    }
  }

  private def _result[A](body: => Either[String, A]): Consequence[A] =
    try body.fold(_ => DurableJobLifetimeQuotaPolicy.invalid, Consequence.success)
    catch { case NonFatal(_) => DurableJobLifetimeQuotaPolicy.invalid }
}
