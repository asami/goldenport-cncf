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
object DurableJobRecordCodec  extends DurableJobRecordDecodingPart with DurableJobRecordEncodingPart {
  val SCHEMA_ID: String = DurableRecordFormat.SCHEMA_ID
  val V1: DurableRecordFormat = DurableRecordFormat.V1
  val V2: DurableRecordFormat = DurableRecordFormat.V2
  val V1_SCHEMA_VALUE: String = s"$SCHEMA_ID/v${V1.version}"
  val V2_SCHEMA_VALUE: String = s"$SCHEMA_ID/v${V2.version}"
  val SHA256_ALGORITHM = "sha-256"

  def sign(body: DurableJobRecordBody): Consequence[DurableJobRecord] =
    _from_either(_sign(V1, body))

  def signV2(body: DurableJobRecordBody): Consequence[DurableJobRecord] =
    _from_either(_sign(V2, body))

  def migrateV1ToV2(record: DurableJobRecord): Consequence[DurableJobRecord] =
    _from_either(for {
      _ <- Either.cond(record.format == V1, (), "Only an admitted v1 durable record can migrate to v2")
      _ <- _validate_record(record)
      migrated <- _sign(V2, record.body)
    } yield migrated)

  def canonicalJson(record: DurableJobRecord): Consequence[String] =
    _from_either(_validate_record(record).map(_ => _signed_json(record).noSpaces))

  def canonicalBytes(record: DurableJobRecord): Consequence[Array[Byte]] =
    canonicalJson(record).map(_.getBytes(StandardCharsets.UTF_8))

  /* A stable Record wrapper for Record-oriented callers; canonicalJson is the wire form. */
  def toRecord(record: DurableJobRecord): Consequence[Record] =
    canonicalJson(record).flatMap { json =>
      Try(Record.dataAuto(
        "schemaId" -> record.format.schemaId,
        "schemaVersion" -> record.format.version,
        "canonicalJson" -> json,
        "unsignedBodySha256" -> record.integrity.unsignedBodySha256
      )).toEither.left.map(error => s"Cannot construct durable job Record: ${error.getMessage}") match {
        case Right(value) => Consequence.success(value)
        case Left(message) => _failure(message)
      }
    }

  def canonicalV0Json(body: DurableJobRecordBody): Consequence[String] =
    _from_either(_validate_body(V1, body).map(_ => _unsigned_json(DurableRecordFormat.V0, body).noSpaces))

  def decode(text: String, access: DurableJobRecordAccess): Consequence[DurableJobRecord] =
    _from_either(_safe_decode_record(text).flatMap { record =>
      _authorize(record.body.authorization, access).map(_ => record)
    })

  private[job] def startupAdmission(
    text: String,
    access: DurableJobRecordAccess
  ): DurableJobRecordStartupAdmission =
    _safe_decode_record(text) match {
      case Right(record) =>
        _authorize(record.body.authorization, access) match {
          case Right(_) => DurableJobRecordStartupAdmission.Admitted(record)
          case Left(_) => DurableJobRecordStartupAdmission.Refused
        }
      case Left(_) => DurableJobRecordStartupAdmission.Corrupt
    }

  def publicProjection(
    record: DurableJobRecord,
    access: DurableJobRecordAccess
  ): Consequence[DurableJobPublicProjection] =
    _from_either(for {
      _ <- _validate_record(record)
      _ <- _authorize(record.body.authorization, access)
    } yield _public_projection(record.body))

  private def _safe_decode_record(text: String): Either[String, DurableJobRecord] =
    try _decode_record(text)
    catch {
      case NonFatal(error) => Left(s"Malformed durable job record: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
    }

  private def _decode_record(text: String): Either[String, DurableJobRecord] =
    for {
      json <- _parse_json(text)
      cursor = json.hcursor
      format <- _format(cursor.downField("format"))
      _ <- _only(cursor, _root_fields(format.version), "record root")
      body <- _body(cursor.downField("record"))
      record <- format.version match {
        case 0 => _sign(V1, body)
        case 1 => for {
          integrity <- _integrity(cursor.downField("integrity"))
          candidate = DurableJobRecord(format, body, integrity)
          _ <- _validate_record(candidate)
        } yield candidate
        case 2 => for {
          integrity <- _integrity(cursor.downField("integrity"))
          candidate = DurableJobRecord(format, body, integrity)
          _ <- _validate_record(candidate)
        } yield candidate
        case other => Left(s"Unsupported durable job record version: $other")
      }
    } yield record

  private def _root_fields(version: Int): Set[String] = version match {
    case 0 => Set("format", "record")
    case 1 | 2 => Set("format", "record", "integrity")
    case _ => Set.empty
  }

  private def _sign(format: DurableRecordFormat, body: DurableJobRecordBody): Either[String, DurableJobRecord] =
    for {
      _ <- _validate_body(format, body)
      unsigned = _unsigned_json(format, body).noSpaces
      digest <- _sha256(unsigned)
    } yield DurableJobRecord(
      format,
      body,
      DurableRecordIntegrity(SHA256_ALGORITHM, digest)
    )

  private def _validate_record(record: DurableJobRecord): Either[String, Unit] =
    for {
      _ <- Either.cond(record.format == V1 || record.format == V2, (), "Durable record must use the v1 or v2 format")
      _ <- _validate_body(record.format, record.body)
      _ <- Either.cond(record.integrity.algorithm == SHA256_ALGORITHM, (), s"Unsupported durable integrity algorithm: ${record.integrity.algorithm}")
      _ <- _validate_digest(record.integrity.unsignedBodySha256, "integrity.unsignedBodySha256")
      expected <- _sha256(_unsigned_json(record.format, record.body).noSpaces)
      _ <- Either.cond(
        expected == record.integrity.unsignedBodySha256,
        (),
        "Durable record integrity digest does not match canonical unsigned body"
      )
    } yield ()

  private def _validate_body(format: DurableRecordFormat, body: DurableJobRecordBody): Either[String, Unit] =
    for {
      _ <- _non_empty(body.identity.jobId, "identity.jobId")
      _ <- Either.cond(body.identity.revision >= 1, (), "identity.revision must be positive")
      _ <- Either.cond(!body.identity.updatedAt.isBefore(body.identity.createdAt), (), "identity.updatedAt precedes identity.createdAt")
      _ <- _authorization_valid(body.authorization)
      _ <- _lifecycle_valid(body.lifecycle)
      _ <- _tasks_valid(body.tasks)
      _ <- _inputs_valid(body.inputs)
      _ <- _result_valid(format, body.lifecycle.status, body.result)
      _ <- _timeline_valid(body.timeline, body.tasks.map(_.taskId).toSet)
      _ <- _diagnostics_valid(body.diagnostics)
      _ <- _reference_optional_valid(body.calltreeReference, "calltreeReference")
      _ <- _definition_valid(body.definitionSnapshot)
      _ <- _retention_valid(body.retention, body.identity.createdAt)
    } yield ()

  private def _authorization_valid(value: DurableJobAuthorization): Either[String, Unit] =
    for {
      _ <- _non_empty(value.tenantId, "authorization.tenantId")
      _ <- _non_empty(value.submitter.id, "authorization.submitter.id")
      _ <- _non_empty(value.submitter.kind, "authorization.submitter.kind")
      _ <- Either.cond(value.requiredScopes.forall(_.trim.nonEmpty), (), "authorization.requiredScopes contains an empty scope")
    } yield ()

  private def _lifecycle_valid(value: DurableJobLifecycle): Either[String, Unit] =
    for {
      _ <- Either.cond(value.priority >= -1000 && value.priority <= 1000, (), "lifecycle.priority is outside the admitted range")
      _ <- _retry_valid(value.retry)
      _ <- _schedule_valid(value.schedule)
    } yield ()

  private def _retry_valid(value: DurableRetryEvidence): Either[String, Unit] =
    for {
      _ <- Either.cond(value.maxAttempts >= 1, (), "retry.maxAttempts must be positive")
      _ <- Either.cond(value.attempts.size <= value.maxAttempts, (), "retry.attempts exceeds retry.maxAttempts")
      _ <- Either.cond(value.attempts.map(_.number) == (1 to value.attempts.size).toVector, (), "retry attempts must have contiguous one-based numbers")
      _ <- _sequence(value.attempts.zipWithIndex.map { case (attempt, index) => _attempt_valid(attempt, index) })
      _ <- Either.cond(!value.exhausted || value.attempts.size == value.maxAttempts, (), "retry.exhausted requires max attempts")
    } yield ()

  private def _attempt_valid(value: DurableAttemptEvidence, index: Int): Either[String, Unit] =
    for {
      _ <- Either.cond(value.finishedAt.forall(!_.isBefore(value.startedAt)), (), s"retry.attempts[$index].finishedAt precedes startedAt")
      _ <- Either.cond(value.outcome != DurableAttemptOutcome.Failed || value.failure.nonEmpty, (), s"retry.attempts[$index] failed without failure summary")
      _ <- _failure_optional_valid(value.failure, s"retry.attempts[$index].failure")
    } yield ()

  private def _schedule_valid(value: DurableScheduleState): Either[String, Unit] =
    for {
      _ <- Either.cond(value.startedAt.forall(start => value.scheduledAt.forall(!start.isBefore(_))), (), "schedule.startedAt precedes scheduledAt")
      _ <- Either.cond(value.completedAt.forall(end => value.startedAt.forall(!end.isBefore(_))), (), "schedule.completedAt precedes startedAt")
    } yield ()

  private def _tasks_valid(tasks: Vector[DurableTaskDescriptor]): Either[String, Unit] = {
    val ids = tasks.map(_.taskId)
    val parentbyid = tasks.map(task => task.taskId -> task.parentTaskId).toMap
    val colors = scala.collection.mutable.Map.empty[String, Int]
    val hascycle = ids.exists { startid =>
      if (colors.getOrElse(startid, 0) == 2) {
        false
      } else {
        val path = scala.collection.mutable.ArrayBuffer.empty[String]
        var current = Option(startid)
        var cycle = false
        while (current.nonEmpty && !cycle && colors.getOrElse(current.get, 0) != 2) {
          val currentid = current.get
          if (colors.getOrElse(currentid, 0) == 1) {
            cycle = true
          } else {
            colors.update(currentid, 1)
            path += currentid
            current = parentbyid.get(currentid).flatten
          }
        }
        if (!cycle) path.foreach(pathid => colors.update(pathid, 2))
        cycle
      }
    }
    for {
      _ <- Either.cond(tasks.nonEmpty, (), "Durable task tree must contain at least one task")
      _ <- _unique(ids, "durable task")
      _ <- _sequence(tasks.zipWithIndex.map { case (task, index) => _task_valid(task, index, ids.toSet) })
      _ <- Either.cond(!hascycle, (), "Durable task tree contains a parent cycle")
    } yield ()
  }

  private def _task_valid(value: DurableTaskDescriptor, index: Int, ids: Set[String]): Either[String, Unit] =
    for {
      _ <- _non_empty(value.taskId, s"tasks[$index].taskId")
      _ <- Either.cond(value.parentTaskId.forall(parent => parent != value.taskId && ids.contains(parent)), (), s"tasks[$index].parentTaskId is orphaned or self-referential")
      _ <- _target_valid(value.target, s"tasks[$index].target")
      _ <- _non_empty(value.relation.reference.getOrElse("root"), s"tasks[$index].relation.reference")
      _ <- _transaction_valid(value.transaction, s"tasks[$index].transaction")
      _ <- _compensation_optional_valid(value.compensation, s"tasks[$index].compensation", ids)
    } yield ()

  private def _target_valid(value: DurableTaskTarget, context: String): Either[String, Unit] =
    for {
      _ <- _non_empty(value.targetKind, s"$context.targetKind")
      _ <- _operation_valid(value.operation, s"$context.operation")
    } yield ()

  private def _operation_valid(value: DurableOperationReference, context: String): Either[String, Unit] =
    for {
      _ <- _non_empty(value.componentId, s"$context.componentId")
      _ <- _non_empty(value.operationId, s"$context.operationId")
      _ <- _optional_non_empty(value.serviceId, s"$context.serviceId")
      _ <- _optional_non_empty(value.actionId, s"$context.actionId")
    } yield ()

  private def _transaction_valid(value: DurableTransactionDescriptor, context: String): Either[String, Unit] =
    Either.cond(
      value.role != DurableTransactionRole.None ||
        (value.scope == DurableTransactionScope.None && value.outcome == DurableTransactionOutcome.Pending),
      (),
      s"$context non-transactional descriptor must use none/pending"
    )

  private def _compensation_optional_valid(
    value: Option[DurableCompensationDescriptor],
    context: String,
    taskids: Set[String]
  ): Either[String, Unit] =
    value.map { compensation =>
      for {
        _ <- _operation_valid(compensation.operation, s"$context.operation")
        _ <- Either.cond(taskids.contains(compensation.compensatesTaskId), (), s"$context.compensatesTaskId is unknown")
        _ <- Either.cond(compensation.status != DurableCompensationStatus.Failed || compensation.failure.nonEmpty, (), s"$context failed without failure summary")
        _ <- _failure_optional_valid(compensation.failure, s"$context.failure")
      } yield ()
    }.getOrElse(Right(()))

  private def _inputs_valid(values: Vector[DurableInputReference]): Either[String, Unit] =
    for {
      _ <- _unique(values.map(_.name), "durable input")
      _ <- _sequence(values.zipWithIndex.map { case (value, index) =>
        _non_empty(value.name, s"inputs[$index].name").flatMap(_ => _value_valid(value.value, s"inputs[$index].value"))
      })
    } yield ()

  private def _value_valid(value: DurableValue, context: String): Either[String, Unit] = value match {
    case DurableValue.Inline(metadata) => _inline_valid(metadata, s"$context.inline")
    case DurableValue.External(reference) => _reference_valid(reference, s"$context.external")
    case DurableValue.Absent => Right(())
  }

  private def _inline_valid(value: DurableInlineMetadata, context: String): Either[String, Unit] =
    for {
      _ <- _non_empty(value.valueType, s"$context.valueType")
      _ <- _optional_non_empty(value.contentType, s"$context.contentType")
      _ <- Either.cond(value.byteSize >= 0, (), s"$context.byteSize must not be negative")
      _ <- _validate_digest(value.sha256, s"$context.sha256")
      _ <- _non_empty(value.classification, s"$context.classification")
    } yield ()

  private def _reference_valid(value: DurableExternalReference, context: String): Either[String, Unit] =
    for {
      _ <- _non_empty(value.reference, s"$context.reference")
      _ <- _non_empty(value.storageKind, s"$context.storageKind")
      _ <- _optional_non_empty(value.contentType, s"$context.contentType")
      _ <- Either.cond(value.byteSize >= 0, (), s"$context.byteSize must not be negative")
      _ <- _validate_digest(value.sha256, s"$context.sha256")
    } yield ()

  private def _reference_optional_valid(value: Option[DurableExternalReference], context: String): Either[String, Unit] =
    value.map(_reference_valid(_, context)).getOrElse(Right(()))

  private def _result_valid(
    format: DurableRecordFormat,
    lifecycle: DurableJobLifecycleStatus,
    value: DurableResultOutcome
  ): Either[String, Unit] =
    (format, lifecycle, value) match {
      case (V2, status, DurableResultOutcome.Pending) if _is_pending_lifecycle(status) => Right(())
      case (_, DurableJobLifecycleStatus.Succeeded, DurableResultOutcome.Succeeded(result)) => _value_valid(result, "result.value")
      case (_, DurableJobLifecycleStatus.Failed, DurableResultOutcome.Failed(failure)) => _failure_valid(failure, "result.failure")
      case (_, DurableJobLifecycleStatus.Cancelled, DurableResultOutcome.Cancelled(summary)) => _failure_optional_valid(summary, "result.summary")
      case (V1, _, DurableResultOutcome.Pending) => Left("v1 durable records cannot carry a pending result")
      case (_, status, _) if _is_pending_lifecycle(status) =>
        Left("Non-terminal durable lifecycle statuses require a v2 pending result")
      case _ => Left("Durable lifecycle status and result outcome must match")
    }

  private def _is_pending_lifecycle(value: DurableJobLifecycleStatus): Boolean =
    value == DurableJobLifecycleStatus.Submitted ||
      value == DurableJobLifecycleStatus.Running ||
      value == DurableJobLifecycleStatus.Suspended

  private def _failure_optional_valid(value: Option[DurableFailureSummary], context: String): Either[String, Unit] =
    value.map(_failure_valid(_, context)).getOrElse(Right(()))

  private def _failure_valid(value: DurableFailureSummary, context: String): Either[String, Unit] =
    for {
      _ <- _non_empty(value.category, s"$context.category")
      _ <- _non_empty(value.code, s"$context.code")
      _ <- _bounded(value.summary, s"$context.summary", 1024)
    } yield ()

  private def _timeline_valid(values: Vector[DurableTimelineEvent], taskids: Set[String]): Either[String, Unit] =
    for {
      _ <- Either.cond(values.map(_.sequence).sliding(2).forall {
        case Vector(left, right) => left < right
        case _ => true
      }, (), "timeline sequences must be strictly monotonic")
      _ <- _sequence(values.zipWithIndex.map { case (value, index) =>
        for {
          _ <- Either.cond(value.sequence >= 1, (), s"timeline[$index].sequence must be positive")
          _ <- _non_empty(value.kind, s"timeline[$index].kind")
          _ <- Either.cond(value.taskId.forall(taskids.contains), (), s"timeline[$index].taskId is unknown")
          _ <- value.summary.map(_bounded(_, s"timeline[$index].summary", 1024)).getOrElse(Right(()))
        } yield ()
      })
    } yield ()

  private def _diagnostics_valid(values: Vector[DurableDiagnosticSummary]): Either[String, Unit] =
    for {
      _ <- Either.cond(values.size <= 64, (), "diagnostics exceeds the v1 bound")
      _ <- _sequence(values.zipWithIndex.map { case (value, index) =>
        for {
          _ <- _non_empty(value.category, s"diagnostics[$index].category")
          _ <- _non_empty(value.code, s"diagnostics[$index].code")
          _ <- _non_empty(value.severity, s"diagnostics[$index].severity")
          _ <- _bounded(value.summary, s"diagnostics[$index].summary", 1024)
        } yield ()
      })
    } yield ()

  private def _definition_valid(value: DurableDefinitionSnapshot): Either[String, Unit] =
    for {
      _ <- _non_empty(value.definitionId, "definitionSnapshot.definitionId")
      _ <- _non_empty(value.key, "definitionSnapshot.key")
      _ <- Either.cond(value.version >= 1, (), "definitionSnapshot.version must be positive")
      _ <- Either.cond(value.revision >= 1, (), "definitionSnapshot.revision must be positive")
      _ <- _validate_digest(value.hash, "definitionSnapshot.hash")
      _ <- _reference_optional_valid(value.declaredSource, "definitionSnapshot.declaredSource")
      _ <- _optional_non_empty(value.format, "definitionSnapshot.format")
      _ <- _map_valid(value.declaredProfile, "definitionSnapshot.declaredProfile")
    } yield ()

  private def _map_valid(value: Map[String, String], context: String): Either[String, Unit] =
    for {
      _ <- Either.cond(value.size <= 32, (), s"$context exceeds the v1 bound")
      _ <- _sequence(value.toVector.map { case (key, entry) =>
        for {
          _ <- _non_empty(key, s"$context key")
          _ <- _bounded(entry, s"$context[$key]", 256)
        } yield ()
      })
    } yield ()

  private def _retention_valid(value: DurableRetentionState, createdat: Instant): Either[String, Unit] =
    for {
      _ <- Either.cond(value.retainUntil.forall(!_.isBefore(createdat)), (), "retention.retainUntil precedes identity.createdAt")
      _ <- Either.cond(value.expiresAt.forall(!_.isBefore(createdat)), (), "retention.expiresAt precedes identity.createdAt")
      _ <- Either.cond(value.deletedAt.forall(!_.isBefore(createdat)), (), "retention.deletedAt precedes identity.createdAt")
      _ <- _retention_shape_valid(value)
    } yield ()

  private def _retention_shape_valid(value: DurableRetentionState): Either[String, Unit] = value.deletionState match {
    case DurableDeletionState.Active =>
      Either.cond(value.deletedAt.isEmpty && value.tombstone.isEmpty, (), "active retention must not be deleted or tombstoned")
    case DurableDeletionState.Expired =>
      Either.cond(value.expiresAt.nonEmpty && value.deletedAt.isEmpty && value.tombstone.isEmpty, (), "expired retention requires expiry only")
    case DurableDeletionState.Deleted =>
      Either.cond(value.deletedAt.nonEmpty && value.tombstone.isEmpty, (), "deleted retention requires deletedAt and no tombstone")
    case DurableDeletionState.Tombstoned =>
      for {
        deleted <- value.deletedAt.toRight("tombstoned retention requires deletedAt")
        tombstone <- value.tombstone.toRight("tombstoned retention requires tombstone")
        _ <- Either.cond(tombstone.deletedAt == deleted, (), "tombstone.deletedAt must equal retention.deletedAt")
        _ <- _bounded(tombstone.reason, "tombstone.reason", 1024)
        _ <- _optional_non_empty(tombstone.replacementRecordId, "tombstone.replacementRecordId")
      } yield ()
  }

  private def _authorize(
    authorization: DurableJobAuthorization,
    access: DurableJobRecordAccess
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(authorization.tenantId == access.tenantId, (), "Durable record access denied: tenant mismatch")
      _ <- Either.cond(authorization.submitter.id == access.subjectId, (), "Durable record access denied: subject mismatch")
      _ <- Either.cond(authorization.requiredScopes.subsetOf(access.scopes), (), "Durable record access denied: required scope missing")
    } yield ()

  private def _public_projection(body: DurableJobRecordBody): DurableJobPublicProjection =
    DurableJobPublicProjection(
      body.identity,
      body.lifecycle,
      body.authorization,
      body.tasks,
      body.inputs.map(input => DurablePublicInput(input.name, _public_value(input.value))),
      _public_result(body.result),
      body.timeline,
      body.diagnostics,
      body.calltreeReference,
      body.definitionSnapshot,
      body.retention
    )

  private def _public_value(value: DurableValue): DurablePublicValue = value match {
    case DurableValue.Inline(metadata) => DurablePublicValue("inline-metadata", Some(metadata), None)
    case DurableValue.External(reference) => DurablePublicValue("external-reference", None, Some(reference))
    case DurableValue.Absent => DurablePublicValue("absent", None, None)
  }

  private def _public_result(value: DurableResultOutcome): DurablePublicResult = value match {
    case DurableResultOutcome.Pending => DurablePublicResult("pending", None, None)
    case DurableResultOutcome.Succeeded(result) => DurablePublicResult("succeeded", Some(_public_value(result)), None)
    case DurableResultOutcome.Failed(failure) => DurablePublicResult("failed", None, Some(failure))
    case DurableResultOutcome.Cancelled(summary) => DurablePublicResult("cancelled", None, summary)
  }

  private[job] val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
}
