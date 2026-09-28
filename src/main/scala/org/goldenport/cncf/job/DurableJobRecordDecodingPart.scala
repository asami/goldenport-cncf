package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import scala.util.Try
import scala.util.control.NonFatal

import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait DurableJobRecordDecodingPart { self: DurableJobRecordCodec.type =>
  import DurableJobRecordCodec.*

  private[job] def _format(cursor: ACursor): Either[String, DurableRecordFormat] =
    for {
      _ <- _only(cursor, Set("schemaId", "version"), "format")
      schemaid <- _required[String](cursor, "schemaId", "format")
      version <- _required[Int](cursor, "version", "format")
      _ <- Either.cond(schemaid == SCHEMA_ID, (), s"Unsupported durable job schema: $schemaid")
      _ <- Either.cond(version == 0 || version == 1 || version == 2, (), s"Unsupported durable job record version: $version")
    } yield DurableRecordFormat(schemaid, version)

  private[job] def _body(cursor: ACursor): Either[String, DurableJobRecordBody] =
    for {
      _ <- _only(cursor, Set("identity", "authorization", "lifecycle", "tasks", "inputs", "result", "timeline", "diagnostics", "calltreeReference", "definitionSnapshot", "retention"), "record")
      identity <- _identity(cursor.downField("identity"))
      authorization <- _authorization(cursor.downField("authorization"))
      lifecycle <- _lifecycle(cursor.downField("lifecycle"))
      tasks <- _vector(cursor, "tasks", "record")(_task)
      inputs <- _vector(cursor, "inputs", "record")(_input)
      result <- _result(cursor.downField("result"))
      timeline <- _vector(cursor, "timeline", "record")(_timeline)
      diagnostics <- _vector(cursor, "diagnostics", "record")(_diagnostic)
      calltree <- _optional_nested(cursor, "calltreeReference", "record")(_external_reference)
      definition <- _definition(cursor.downField("definitionSnapshot"))
      retention <- _retention(cursor.downField("retention"))
    } yield DurableJobRecordBody(identity, authorization, lifecycle, tasks, inputs, result, timeline, diagnostics, calltree, definition, retention)

  private def _identity(cursor: ACursor): Either[String, DurableJobIdentity] =
    for {
      _ <- _only(cursor, Set("jobId", "revision", "createdAt", "updatedAt"), "identity")
      id <- _required[String](cursor, "jobId", "identity")
      revision <- _required[Long](cursor, "revision", "identity")
      created <- _instant_required(cursor, "createdAt", "identity")
      updated <- _instant_required(cursor, "updatedAt", "identity")
    } yield DurableJobIdentity(id, revision, created, updated)

  private def _authorization(cursor: ACursor): Either[String, DurableJobAuthorization] =
    for {
      _ <- _only(cursor, Set("tenantId", "submitter", "visibility", "requiredScopes"), "authorization")
      tenant <- _required[String](cursor, "tenantId", "authorization")
      submitter <- _subject(cursor.downField("submitter"))
      visibilitytext <- _required[String](cursor, "visibility", "authorization")
      visibility <- DurableVisibility.parse(visibilitytext)
      scopes <- _required[Vector[String]](cursor, "requiredScopes", "authorization")
      _ <- _unique(scopes, "authorization.requiredScopes")
    } yield DurableJobAuthorization(tenant, submitter, visibility, scopes.toSet)

  private def _subject(cursor: ACursor): Either[String, DurableSubject] =
    for {
      _ <- _only(cursor, Set("id", "kind"), "authorization.submitter")
      id <- _required[String](cursor, "id", "authorization.submitter")
      kind <- _required[String](cursor, "kind", "authorization.submitter")
    } yield DurableSubject(id, kind)

  private def _lifecycle(cursor: ACursor): Either[String, DurableJobLifecycle] =
    for {
      _ <- _only(cursor, Set("status", "priority", "runMode", "retry", "schedule"), "lifecycle")
      statustext <- _required[String](cursor, "status", "lifecycle")
      status <- DurableJobLifecycleStatus.parse(statustext)
      priority <- _required[Int](cursor, "priority", "lifecycle")
      runmodetext <- _required[String](cursor, "runMode", "lifecycle")
      runmode <- DurableRunMode.parse(runmodetext)
      retry <- _retry(cursor.downField("retry"))
      schedule <- _schedule(cursor.downField("schedule"))
    } yield DurableJobLifecycle(status, priority, runmode, retry, schedule)

  private def _retry(cursor: ACursor): Either[String, DurableRetryEvidence] =
    for {
      _ <- _only(cursor, Set("attempts", "maxAttempts", "nextRetryAt", "exhausted", "recoveryRequired"), "lifecycle.retry")
      attempts <- _vector(cursor, "attempts", "lifecycle.retry")(_attempt)
      maxattempts <- _required[Int](cursor, "maxAttempts", "lifecycle.retry")
      nextretryat <- _instant_optional(cursor, "nextRetryAt", "lifecycle.retry")
      exhausted <- _required[Boolean](cursor, "exhausted", "lifecycle.retry")
      recoveryrequired <- _required[Boolean](cursor, "recoveryRequired", "lifecycle.retry")
    } yield DurableRetryEvidence(attempts, maxattempts, nextretryat, exhausted, recoveryrequired)

  private def _attempt(cursor: ACursor): Either[String, DurableAttemptEvidence] =
    for {
      _ <- _only(cursor, Set("number", "startedAt", "finishedAt", "outcome", "failure"), "attempt")
      number <- _required[Int](cursor, "number", "attempt")
      started <- _instant_required(cursor, "startedAt", "attempt")
      finished <- _instant_optional(cursor, "finishedAt", "attempt")
      outcometext <- _required[String](cursor, "outcome", "attempt")
      outcome <- DurableAttemptOutcome.parse(outcometext)
      failure <- _optional_nested(cursor, "failure", "attempt")(_failure_summary)
    } yield DurableAttemptEvidence(number, started, finished, outcome, failure)

  private def _schedule(cursor: ACursor): Either[String, DurableScheduleState] =
    for {
      _ <- _only(cursor, Set("scheduledAt", "startedAt", "completedAt"), "lifecycle.schedule")
      scheduled <- _instant_optional(cursor, "scheduledAt", "lifecycle.schedule")
      started <- _instant_optional(cursor, "startedAt", "lifecycle.schedule")
      completed <- _instant_optional(cursor, "completedAt", "lifecycle.schedule")
    } yield DurableScheduleState(scheduled, started, completed)

  private def _task(cursor: ACursor): Either[String, DurableTaskDescriptor] =
    for {
      _ <- _only(cursor, Set("taskId", "parentTaskId", "kind", "target", "relation", "transaction", "compensation"), "task")
      taskid <- _required[String](cursor, "taskId", "task")
      parenttaskid <- _optional[String](cursor, "parentTaskId", "task")
      kindtext <- _required[String](cursor, "kind", "task")
      kind <- DurableTaskKind.parse(kindtext)
      target <- _target(cursor.downField("target"))
      relation <- _relation(cursor.downField("relation"))
      transaction <- _transaction(cursor.downField("transaction"))
      compensation <- _optional_nested(cursor, "compensation", "task")(_compensation)
    } yield DurableTaskDescriptor(taskid, parenttaskid, kind, target, relation, transaction, compensation)

  private def _target(cursor: ACursor): Either[String, DurableTaskTarget] =
    for {
      _ <- _only(cursor, Set("targetKind", "operation"), "task.target")
      targetkind <- _required[String](cursor, "targetKind", "task.target")
      operation <- _operation(cursor.downField("operation"))
    } yield DurableTaskTarget(targetkind, operation)

  private def _operation(cursor: ACursor): Either[String, DurableOperationReference] =
    for {
      _ <- _only(cursor, Set("componentId", "serviceId", "operationId", "actionId"), "operation")
      componentid <- _required[String](cursor, "componentId", "operation")
      serviceid <- _optional[String](cursor, "serviceId", "operation")
      operationid <- _required[String](cursor, "operationId", "operation")
      actionid <- _optional[String](cursor, "actionId", "operation")
    } yield DurableOperationReference(componentid, serviceid, operationid, actionid)

  private def _relation(cursor: ACursor): Either[String, DurableTaskRelation] =
    for {
      _ <- _only(cursor, Set("kind", "reference"), "task.relation")
      kindtext <- _required[String](cursor, "kind", "task.relation")
      kind <- DurableTaskRelationKind.parse(kindtext)
      reference <- _optional[String](cursor, "reference", "task.relation")
    } yield DurableTaskRelation(kind, reference)

  private def _transaction(cursor: ACursor): Either[String, DurableTransactionDescriptor] =
    for {
      _ <- _only(cursor, Set("role", "scope", "outcome"), "task.transaction")
      roletext <- _required[String](cursor, "role", "task.transaction")
      role <- DurableTransactionRole.parse(roletext)
      scopetext <- _required[String](cursor, "scope", "task.transaction")
      scope <- DurableTransactionScope.parse(scopetext)
      outcometext <- _required[String](cursor, "outcome", "task.transaction")
      outcome <- DurableTransactionOutcome.parse(outcometext)
    } yield DurableTransactionDescriptor(role, scope, outcome)

  private def _compensation(cursor: ACursor): Either[String, DurableCompensationDescriptor] =
    for {
      _ <- _only(cursor, Set("operation", "compensatesTaskId", "status", "failure"), "task.compensation")
      operation <- _operation(cursor.downField("operation"))
      taskid <- _required[String](cursor, "compensatesTaskId", "task.compensation")
      statustext <- _required[String](cursor, "status", "task.compensation")
      status <- DurableCompensationStatus.parse(statustext)
      failure <- _optional_nested(cursor, "failure", "task.compensation")(_failure_summary)
    } yield DurableCompensationDescriptor(operation, taskid, status, failure)

  private def _input(cursor: ACursor): Either[String, DurableInputReference] =
    for {
      _ <- _only(cursor, Set("name", "value"), "input")
      name <- _required[String](cursor, "name", "input")
      value <- _value(cursor.downField("value"))
    } yield DurableInputReference(name, value)

  private def _value(cursor: ACursor): Either[String, DurableValue] =
    for {
      _ <- _only(cursor, Set("storage", "inlineMetadata", "externalReference"), "value")
      storage <- _required[String](cursor, "storage", "value")
      inline <- _optional_nested(cursor, "inlineMetadata", "value")(_inline_metadata)
      external <- _optional_nested(cursor, "externalReference", "value")(_external_reference)
      value <- (storage, inline, external) match {
        case ("inline-metadata", Some(metadata), None) => Right(DurableValue.Inline(metadata))
        case ("external-reference", None, Some(reference)) => Right(DurableValue.External(reference))
        case ("absent", None, None) => Right(DurableValue.Absent)
        case _ => Left("Durable value storage does not match its admitted value reference")
      }
    } yield value

  private def _inline_metadata(cursor: ACursor): Either[String, DurableInlineMetadata] =
    for {
      _ <- _only(cursor, Set("valueType", "contentType", "byteSize", "sha256", "classification"), "inlineMetadata")
      valuetype <- _required[String](cursor, "valueType", "inlineMetadata")
      contenttype <- _optional[String](cursor, "contentType", "inlineMetadata")
      bytesize <- _required[Long](cursor, "byteSize", "inlineMetadata")
      sha256 <- _required[String](cursor, "sha256", "inlineMetadata")
      classification <- _required[String](cursor, "classification", "inlineMetadata")
    } yield DurableInlineMetadata(valuetype, contenttype, bytesize, sha256, classification)

  private def _external_reference(cursor: ACursor): Either[String, DurableExternalReference] =
    for {
      _ <- _only(cursor, Set("reference", "storageKind", "contentType", "byteSize", "sha256"), "externalReference")
      reference <- _required[String](cursor, "reference", "externalReference")
      storagekind <- _required[String](cursor, "storageKind", "externalReference")
      contenttype <- _optional[String](cursor, "contentType", "externalReference")
      bytesize <- _required[Long](cursor, "byteSize", "externalReference")
      sha256 <- _required[String](cursor, "sha256", "externalReference")
    } yield DurableExternalReference(reference, storagekind, contenttype, bytesize, sha256)

  private def _result(cursor: ACursor): Either[String, DurableResultOutcome] =
    for {
      _ <- _only(cursor, Set("outcome", "value", "failure"), "result")
      outcome <- _required[String](cursor, "outcome", "result")
      value <- _optional_nested(cursor, "value", "result")(_value)
      failure <- _optional_nested(cursor, "failure", "result")(_failure_summary)
      result <- (outcome, value, failure) match {
        case ("pending", None, None) => Right(DurableResultOutcome.Pending)
        case ("succeeded", Some(admitted), None) => Right(DurableResultOutcome.Succeeded(admitted))
        case ("failed", None, Some(summary)) => Right(DurableResultOutcome.Failed(summary))
        case ("cancelled", None, summary) => Right(DurableResultOutcome.Cancelled(summary))
        case _ => Left("Durable result outcome does not match its admitted payload summary")
      }
    } yield result

  private def _failure_summary(cursor: ACursor): Either[String, DurableFailureSummary] =
    for {
      _ <- _only(cursor, Set("category", "code", "summary", "retryable"), "failureSummary")
      category <- _required[String](cursor, "category", "failureSummary")
      code <- _required[String](cursor, "code", "failureSummary")
      summary <- _required[String](cursor, "summary", "failureSummary")
      retryable <- _required[Boolean](cursor, "retryable", "failureSummary")
    } yield DurableFailureSummary(category, code, summary, retryable)

  private def _timeline(cursor: ACursor): Either[String, DurableTimelineEvent] =
    for {
      _ <- _only(cursor, Set("sequence", "occurredAt", "kind", "taskId", "summary"), "timeline event")
      sequence <- _required[Long](cursor, "sequence", "timeline event")
      occurred <- _instant_required(cursor, "occurredAt", "timeline event")
      kind <- _required[String](cursor, "kind", "timeline event")
      taskid <- _optional[String](cursor, "taskId", "timeline event")
      summary <- _optional[String](cursor, "summary", "timeline event")
    } yield DurableTimelineEvent(sequence, occurred, kind, taskid, summary)

  private def _diagnostic(cursor: ACursor): Either[String, DurableDiagnosticSummary] =
    for {
      _ <- _only(cursor, Set("category", "code", "severity", "summary"), "diagnostic")
      category <- _required[String](cursor, "category", "diagnostic")
      code <- _required[String](cursor, "code", "diagnostic")
      severity <- _required[String](cursor, "severity", "diagnostic")
      summary <- _required[String](cursor, "summary", "diagnostic")
    } yield DurableDiagnosticSummary(category, code, severity, summary)

  private def _definition(cursor: ACursor): Either[String, DurableDefinitionSnapshot] =
    for {
      _ <- _only(cursor, Set("definitionId", "key", "version", "revision", "hash", "declaredSource", "format", "declaredProfile"), "definitionSnapshot")
      id <- _required[String](cursor, "definitionId", "definitionSnapshot")
      key <- _required[String](cursor, "key", "definitionSnapshot")
      version <- _required[Int](cursor, "version", "definitionSnapshot")
      revision <- _required[Long](cursor, "revision", "definitionSnapshot")
      hash <- _required[String](cursor, "hash", "definitionSnapshot")
      source <- _optional_nested(cursor, "declaredSource", "definitionSnapshot")(_external_reference)
      format <- _optional[String](cursor, "format", "definitionSnapshot")
      profile <- _required[Map[String, String]](cursor, "declaredProfile", "definitionSnapshot")
    } yield DurableDefinitionSnapshot(id, key, version, revision, hash, source, format, profile)

  private def _retention(cursor: ACursor): Either[String, DurableRetentionState] =
    for {
      _ <- _only(cursor, Set("retainUntil", "expiresAt", "deletedAt", "deletionState", "tombstone"), "retention")
      retainuntil <- _instant_optional(cursor, "retainUntil", "retention")
      expiresat <- _instant_optional(cursor, "expiresAt", "retention")
      deletedat <- _instant_optional(cursor, "deletedAt", "retention")
      statetext <- _required[String](cursor, "deletionState", "retention")
      state <- DurableDeletionState.parse(statetext)
      tombstone <- _optional_nested(cursor, "tombstone", "retention")(_tombstone)
    } yield DurableRetentionState(retainuntil, expiresat, deletedat, state, tombstone)

  private def _tombstone(cursor: ACursor): Either[String, DurableTombstone] =
    for {
      _ <- _only(cursor, Set("deletedAt", "reason", "replacementRecordId"), "tombstone")
      deletedat <- _instant_required(cursor, "deletedAt", "tombstone")
      reason <- _required[String](cursor, "reason", "tombstone")
      replacement <- _optional[String](cursor, "replacementRecordId", "tombstone")
    } yield DurableTombstone(deletedat, reason, replacement)

  private[job] def _integrity(cursor: ACursor): Either[String, DurableRecordIntegrity] =
    for {
      _ <- _only(cursor, Set("algorithm", "unsignedBodySha256"), "integrity")
      algorithm <- _required[String](cursor, "algorithm", "integrity")
      digest <- _required[String](cursor, "unsignedBodySha256", "integrity")
    } yield DurableRecordIntegrity(algorithm, digest)

  private[job] def _parse_json(text: String): Either[String, Json] =
    try _strict_json_parser.parse(text).left.map(error => s"Malformed durable job record JSON: ${error.message}")
    catch {
      case NonFatal(error) => Left(s"Malformed durable job record JSON: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, A] =
    cursor.get[A](field).left.map(error => s"Durable record $context requires $field: ${error.message}")

  private def _optional[A: Decoder](cursor: ACursor, field: String, context: String): Either[String, Option[A]] =
    cursor.downField(field).focus match {
      case None => Right(None)
      case Some(json) if json.isNull => Right(None)
      case Some(_) => cursor.get[A](field).left.map(error => s"Invalid durable record $context.$field: ${error.message}").map(Some(_))
    }

  private def _optional_nested[A](
    cursor: ACursor,
    field: String,
    context: String
  )(f: ACursor => Either[String, A]): Either[String, Option[A]] =
    cursor.downField(field).focus match {
      case None => Right(None)
      case Some(json) if json.isNull => Right(None)
      case Some(json) => f(json.hcursor).map(Some(_))
    }

  private def _vector[A](
    cursor: ACursor,
    field: String,
    context: String
  )(f: ACursor => Either[String, A]): Either[String, Vector[A]] =
    _required[Vector[Json]](cursor, field, context).flatMap { values =>
      _sequence(values.zipWithIndex.map { case (value, index) =>
        f(value.hcursor).left.map(error => s"$error at $context.$field[$index]")
      })
    }

  private def _instant_required(cursor: ACursor, field: String, context: String): Either[String, Instant] =
    _required[String](cursor, field, context).flatMap(_instant(_, s"$context.$field"))

  private def _instant_optional(cursor: ACursor, field: String, context: String): Either[String, Option[Instant]] =
    _optional[String](cursor, field, context).flatMap {
      case Some(value) => _instant(value, s"$context.$field").map(Some(_))
      case None => Right(None)
    }

  private def _instant(value: String, context: String): Either[String, Instant] =
    Try(Instant.parse(value)).toEither.left.map(_ => s"Invalid durable record $context instant: $value")

  private[job] def _only(cursor: ACursor, expected: Set[String], context: String): Either[String, Unit] =
    cursor.success.toRight(s"Durable record $context must be an object").flatMap { hcursor =>
      val unknown = hcursor.keys.toVector.flatten.filterNot(expected).sorted
      Either.cond(unknown.isEmpty, (), s"Unknown durable record $context fields: ${unknown.mkString(", ")}")
    }

  private[job] def _unique(values: Vector[String], context: String): Either[String, Unit] = {
    val duplicates = values.groupBy(identity).collect { case (value, xs) if xs.size > 1 => value }.toVector.sorted
    Either.cond(duplicates.isEmpty, (), s"Duplicate $context identities: ${duplicates.mkString(", ")}")
  }

  private[job] def _non_empty(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && value.trim.nonEmpty, (), s"$context must not be empty")

  private[job] def _optional_non_empty(value: Option[String], context: String): Either[String, Unit] =
    value.map(_non_empty(_, context)).getOrElse(Right(()))

  private[job] def _bounded(value: String, context: String, maximum: Int): Either[String, Unit] =
    Either.cond(value != null && value.trim.nonEmpty && value.length <= maximum, (), s"$context must be non-empty and no longer than $maximum characters")

  private[job] def _validate_digest(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && "[0-9a-f]{64}".r.matches(value), (), s"$context must be a lowercase SHA-256 digest")

  private[job] def _sha256(value: String): Either[String, String] =
    Try {
      MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
    }.toEither.left.map(error => s"Cannot calculate durable record SHA-256: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")

  private[job] def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (acc, value) =>
      for {
        collected <- acc
        entry <- value
      } yield collected :+ entry
    }

  private[job] def _from_either[A](value: Either[String, A]): Consequence[A] = value match {
    case Right(result) => Consequence.success(result)
    case Left(message) => _failure(message)
  }

  private[job] def _failure[A](message: String): Consequence[A] =
    Consequence.argumentInvalid(message)

}
