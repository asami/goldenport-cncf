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
 * Provider-neutral durable execution record contract.
 *
 * This model is deliberately made only of closed value types.  It is not a
 * serialization form for a live JobTask, Action, ExecutionContext, provider,
 * ClassLoader, closure, credential, secret, or an arbitrary object graph.
 * Phase 69.1 owns provider writes and process recovery.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DurableRecordFormat(schemaId: String, version: Int)

object DurableRecordFormat {
  val SCHEMA_ID = "cncf.durable-job-record"
  val V0 = DurableRecordFormat(SCHEMA_ID, 0)
  val V1 = DurableRecordFormat(SCHEMA_ID, 1)
  val V2 = DurableRecordFormat(SCHEMA_ID, 2)
}

final case class DurableJobIdentity(
  jobId: String,
  revision: Long,
  createdAt: Instant,
  updatedAt: Instant
)

final case class DurableSubject(id: String, kind: String)

enum DurableVisibility(val wire: String) {
  case Subject extends DurableVisibility("subject")
  case Tenant extends DurableVisibility("tenant")
  case Scoped extends DurableVisibility("scoped")
}

object DurableVisibility {
  def parse(value: String): Either[String, DurableVisibility] =
    values.find(_.wire == value).toRight(s"Unknown durable visibility: $value")
}

final case class DurableJobAuthorization(
  tenantId: String,
  submitter: DurableSubject,
  visibility: DurableVisibility,
  requiredScopes: Set[String]
)

/*
 * The caller presents this value before a durable body is decoded into an
 * admitted record or projected.  Subject equality is intentional in v1:
 * delegated/admin cross-subject reads require a future, explicitly versioned
 * authorization contract rather than an implicit bypass.
 */
final case class DurableJobRecordAccess(
  tenantId: String,
  subjectId: String,
  scopes: Set[String]
)

enum DurableJobLifecycleStatus(val wire: String) {
  case Submitted extends DurableJobLifecycleStatus("submitted")
  case Running extends DurableJobLifecycleStatus("running")
  case Suspended extends DurableJobLifecycleStatus("suspended")
  case Succeeded extends DurableJobLifecycleStatus("succeeded")
  case Failed extends DurableJobLifecycleStatus("failed")
  case Cancelled extends DurableJobLifecycleStatus("cancelled")
}

object DurableJobLifecycleStatus {
  def parse(value: String): Either[String, DurableJobLifecycleStatus] =
    values.find(_.wire == value).toRight(s"Unknown durable lifecycle status: $value")
}

enum DurableRunMode(val wire: String) {
  case Sync extends DurableRunMode("sync")
  case Async extends DurableRunMode("async")
}

object DurableRunMode {
  def parse(value: String): Either[String, DurableRunMode] =
    values.find(_.wire == value).toRight(s"Unknown durable run mode: $value")
}

enum DurableAttemptOutcome(val wire: String) {
  case Running extends DurableAttemptOutcome("running")
  case Succeeded extends DurableAttemptOutcome("succeeded")
  case Failed extends DurableAttemptOutcome("failed")
  case Cancelled extends DurableAttemptOutcome("cancelled")
}

object DurableAttemptOutcome {
  def parse(value: String): Either[String, DurableAttemptOutcome] =
    values.find(_.wire == value).toRight(s"Unknown durable attempt outcome: $value")
}

final case class DurableAttemptEvidence(
  number: Int,
  startedAt: Instant,
  finishedAt: Option[Instant],
  outcome: DurableAttemptOutcome,
  failure: Option[DurableFailureSummary]
)

final case class DurableRetryEvidence(
  attempts: Vector[DurableAttemptEvidence],
  maxAttempts: Int,
  nextRetryAt: Option[Instant],
  exhausted: Boolean,
  recoveryRequired: Boolean
)

final case class DurableScheduleState(
  scheduledAt: Option[Instant],
  startedAt: Option[Instant],
  completedAt: Option[Instant]
)

final case class DurableJobLifecycle(
  status: DurableJobLifecycleStatus,
  priority: Int,
  runMode: DurableRunMode,
  retry: DurableRetryEvidence,
  schedule: DurableScheduleState
)

enum DurableTaskKind(val wire: String) {
  case Action extends DurableTaskKind("action")
  case Operation extends DurableTaskKind("operation")
  case Aggregate extends DurableTaskKind("aggregate")
  case Event extends DurableTaskKind("event")
  case Continuation extends DurableTaskKind("continuation")
}

object DurableTaskKind {
  def parse(value: String): Either[String, DurableTaskKind] =
    values.find(_.wire == value).toRight(s"Unknown durable task kind: $value")
}

enum DurableTaskRelationKind(val wire: String) {
  case Root extends DurableTaskRelationKind("root")
  case Child extends DurableTaskRelationKind("child")
  case EventReception extends DurableTaskRelationKind("event-reception")
  case Continuation extends DurableTaskRelationKind("continuation")
  case Compensation extends DurableTaskRelationKind("compensation")
}

object DurableTaskRelationKind {
  def parse(value: String): Either[String, DurableTaskRelationKind] =
    values.find(_.wire == value).toRight(s"Unknown durable task relation: $value")
}

final case class DurableTaskRelation(
  kind: DurableTaskRelationKind,
  reference: Option[String]
)

final case class DurableOperationReference(
  componentId: String,
  serviceId: Option[String],
  operationId: String,
  actionId: Option[String]
)

final case class DurableTaskTarget(
  targetKind: String,
  operation: DurableOperationReference
)

enum DurableTransactionRole(val wire: String) {
  case Own extends DurableTransactionRole("own")
  case Join extends DurableTransactionRole("join")
  case None extends DurableTransactionRole("none")
}

object DurableTransactionRole {
  def parse(value: String): Either[String, DurableTransactionRole] =
    values.find(_.wire == value).toRight(s"Unknown durable transaction role: $value")
}

enum DurableTransactionScope(val wire: String) {
  case PerTask extends DurableTransactionScope("per-task")
  case WholeJob extends DurableTransactionScope("whole-job")
  case None extends DurableTransactionScope("none")
}

object DurableTransactionScope {
  def parse(value: String): Either[String, DurableTransactionScope] =
    values.find(_.wire == value).toRight(s"Unknown durable transaction scope: $value")
}

enum DurableTransactionOutcome(val wire: String) {
  case Pending extends DurableTransactionOutcome("pending")
  case Committed extends DurableTransactionOutcome("committed")
  case Failed extends DurableTransactionOutcome("failed")
  case Compensated extends DurableTransactionOutcome("compensated")
}

object DurableTransactionOutcome {
  def parse(value: String): Either[String, DurableTransactionOutcome] =
    values.find(_.wire == value).toRight(s"Unknown durable transaction outcome: $value")
}

final case class DurableTransactionDescriptor(
  role: DurableTransactionRole,
  scope: DurableTransactionScope,
  outcome: DurableTransactionOutcome
)

enum DurableCompensationStatus(val wire: String) {
  case NotRequired extends DurableCompensationStatus("not-required")
  case Pending extends DurableCompensationStatus("pending")
  case Succeeded extends DurableCompensationStatus("succeeded")
  case Failed extends DurableCompensationStatus("failed")
}

object DurableCompensationStatus {
  def parse(value: String): Either[String, DurableCompensationStatus] =
    values.find(_.wire == value).toRight(s"Unknown durable compensation status: $value")
}

final case class DurableFailureSummary(
  category: String,
  code: String,
  summary: String,
  retryable: Boolean
)

final case class DurableCompensationDescriptor(
  operation: DurableOperationReference,
  compensatesTaskId: String,
  status: DurableCompensationStatus,
  failure: Option[DurableFailureSummary]
)

final case class DurableTaskDescriptor(
  taskId: String,
  parentTaskId: Option[String],
  kind: DurableTaskKind,
  target: DurableTaskTarget,
  relation: DurableTaskRelation,
  transaction: DurableTransactionDescriptor,
  compensation: Option[DurableCompensationDescriptor]
)

/*
 * Inline means metadata is admitted in this record; it never means that the
 * data body is admitted.  The raw input/result body remains outside this
 * contract.  An external reference is opaque and contains no provider object.
 */
final case class DurableInlineMetadata(
  valueType: String,
  contentType: Option[String],
  byteSize: Long,
  sha256: String,
  classification: String
)

final case class DurableExternalReference(
  reference: String,
  storageKind: String,
  contentType: Option[String],
  byteSize: Long,
  sha256: String
)

sealed trait DurableValue

object DurableValue {
  final case class Inline(metadata: DurableInlineMetadata) extends DurableValue
  final case class External(reference: DurableExternalReference) extends DurableValue
  case object Absent extends DurableValue
}

final case class DurableInputReference(name: String, value: DurableValue)

sealed trait DurableResultOutcome

object DurableResultOutcome {
  case object Pending extends DurableResultOutcome
  final case class Succeeded(value: DurableValue) extends DurableResultOutcome
  final case class Failed(failure: DurableFailureSummary) extends DurableResultOutcome
  final case class Cancelled(summary: Option[DurableFailureSummary]) extends DurableResultOutcome
}

final case class DurableTimelineEvent(
  sequence: Long,
  occurredAt: Instant,
  kind: String,
  taskId: Option[String],
  summary: Option[String]
)

final case class DurableDiagnosticSummary(
  category: String,
  code: String,
  severity: String,
  summary: String
)

final case class DurableDefinitionSnapshot(
  definitionId: String,
  key: String,
  version: Int,
  revision: Long,
  hash: String,
  declaredSource: Option[DurableExternalReference],
  format: Option[String],
  declaredProfile: Map[String, String]
)

enum DurableDeletionState(val wire: String) {
  case Active extends DurableDeletionState("active")
  case Expired extends DurableDeletionState("expired")
  case Deleted extends DurableDeletionState("deleted")
  case Tombstoned extends DurableDeletionState("tombstoned")
}

object DurableDeletionState {
  def parse(value: String): Either[String, DurableDeletionState] =
    values.find(_.wire == value).toRight(s"Unknown durable deletion state: $value")
}

final case class DurableTombstone(
  deletedAt: Instant,
  reason: String,
  replacementRecordId: Option[String]
)

final case class DurableRetentionState(
  retainUntil: Option[Instant],
  expiresAt: Option[Instant],
  deletedAt: Option[Instant],
  deletionState: DurableDeletionState,
  tombstone: Option[DurableTombstone]
)

final case class DurableRecordIntegrity(
  algorithm: String,
  unsignedBodySha256: String
)

final case class DurableJobRecordBody(
  identity: DurableJobIdentity,
  authorization: DurableJobAuthorization,
  lifecycle: DurableJobLifecycle,
  tasks: Vector[DurableTaskDescriptor],
  inputs: Vector[DurableInputReference],
  result: DurableResultOutcome,
  timeline: Vector[DurableTimelineEvent],
  diagnostics: Vector[DurableDiagnosticSummary],
  calltreeReference: Option[DurableExternalReference],
  definitionSnapshot: DurableDefinitionSnapshot,
  retention: DurableRetentionState
)

final case class DurableJobRecord(
  format: DurableRecordFormat,
  body: DurableJobRecordBody,
  integrity: DurableRecordIntegrity
) {
  def publicProjection(access: DurableJobRecordAccess): Consequence[DurableJobPublicProjection] =
    DurableJobRecordCodec.publicProjection(this, access)
}

object DurableJobRecord {
  def create(body: DurableJobRecordBody): Consequence[DurableJobRecord] =
    DurableJobRecordCodec.sign(body)

  def createV2(body: DurableJobRecordBody): Consequence[DurableJobRecord] =
    DurableJobRecordCodec.signV2(body)

  def migrateV1ToV2(record: DurableJobRecord): Consequence[DurableJobRecord] =
    DurableJobRecordCodec.migrateV1ToV2(record)
}

final case class DurablePublicValue(
  storage: String,
  inlineMetadata: Option[DurableInlineMetadata],
  externalReference: Option[DurableExternalReference]
)

final case class DurablePublicInput(name: String, value: DurablePublicValue)

final case class DurablePublicResult(
  outcome: String,
  value: Option[DurablePublicValue],
  failure: Option[DurableFailureSummary]
)

final case class DurableJobPublicProjection(
  identity: DurableJobIdentity,
  lifecycle: DurableJobLifecycle,
  authorization: DurableJobAuthorization,
  tasks: Vector[DurableTaskDescriptor],
  inputs: Vector[DurablePublicInput],
  result: DurablePublicResult,
  timeline: Vector[DurableTimelineEvent],
  diagnostics: Vector[DurableDiagnosticSummary],
  calltreeReference: Option[DurableExternalReference],
  definitionSnapshot: DurableDefinitionSnapshot,
  retention: DurableRetentionState
)

/*
 * Closed startup admission fact.  It intentionally exposes neither a decode
 * failure nor an authorization detail across the recovery boundary.
 */
private[job] enum DurableJobRecordStartupAdmission {
  case Admitted(record: DurableJobRecord)
  case Refused
  case Corrupt
}

object DurableJobRecordCodec {
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

  private def _format(cursor: ACursor): Either[String, DurableRecordFormat] =
    for {
      _ <- _only(cursor, Set("schemaId", "version"), "format")
      schemaid <- _required[String](cursor, "schemaId", "format")
      version <- _required[Int](cursor, "version", "format")
      _ <- Either.cond(schemaid == SCHEMA_ID, (), s"Unsupported durable job schema: $schemaid")
      _ <- Either.cond(version == 0 || version == 1 || version == 2, (), s"Unsupported durable job record version: $version")
    } yield DurableRecordFormat(schemaid, version)

  private def _body(cursor: ACursor): Either[String, DurableJobRecordBody] =
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

  private def _integrity(cursor: ACursor): Either[String, DurableRecordIntegrity] =
    for {
      _ <- _only(cursor, Set("algorithm", "unsignedBodySha256"), "integrity")
      algorithm <- _required[String](cursor, "algorithm", "integrity")
      digest <- _required[String](cursor, "unsignedBodySha256", "integrity")
    } yield DurableRecordIntegrity(algorithm, digest)

  private def _unsigned_json(format: DurableRecordFormat, body: DurableJobRecordBody): Json =
    Json.obj(
      "format" -> _format_json(format),
      "record" -> _body_json(body)
    )

  private def _signed_json(record: DurableJobRecord): Json =
    Json.obj(
      "format" -> _format_json(record.format),
      "record" -> _body_json(record.body),
      "integrity" -> _integrity_json(record.integrity)
    )

  private def _format_json(value: DurableRecordFormat): Json =
    Json.obj(
      "schemaId" -> Json.fromString(value.schemaId),
      "version" -> Json.fromInt(value.version)
    )

  private def _body_json(value: DurableJobRecordBody): Json =
    Json.obj(
      "identity" -> _identity_json(value.identity),
      "authorization" -> _authorization_json(value.authorization),
      "lifecycle" -> _lifecycle_json(value.lifecycle),
      "tasks" -> Json.fromValues(value.tasks.map(_task_json)),
      "inputs" -> Json.fromValues(value.inputs.map(_input_json)),
      "result" -> _result_json(value.result),
      "timeline" -> Json.fromValues(value.timeline.map(_timeline_json)),
      "diagnostics" -> Json.fromValues(value.diagnostics.map(_diagnostic_json)),
      "calltreeReference" -> _optional_json(value.calltreeReference)(_external_reference_json),
      "definitionSnapshot" -> _definition_json(value.definitionSnapshot),
      "retention" -> _retention_json(value.retention)
    )

  private def _identity_json(value: DurableJobIdentity): Json =
    Json.obj(
      "jobId" -> Json.fromString(value.jobId),
      "revision" -> Json.fromLong(value.revision),
      "createdAt" -> Json.fromString(value.createdAt.toString),
      "updatedAt" -> Json.fromString(value.updatedAt.toString)
    )

  private def _authorization_json(value: DurableJobAuthorization): Json =
    Json.obj(
      "tenantId" -> Json.fromString(value.tenantId),
      "submitter" -> Json.obj("id" -> Json.fromString(value.submitter.id), "kind" -> Json.fromString(value.submitter.kind)),
      "visibility" -> Json.fromString(value.visibility.wire),
      "requiredScopes" -> Json.fromValues(value.requiredScopes.toVector.sorted.map(Json.fromString))
    )

  private def _lifecycle_json(value: DurableJobLifecycle): Json =
    Json.obj(
      "status" -> Json.fromString(value.status.wire),
      "priority" -> Json.fromInt(value.priority),
      "runMode" -> Json.fromString(value.runMode.wire),
      "retry" -> _retry_json(value.retry),
      "schedule" -> _schedule_json(value.schedule)
    )

  private def _retry_json(value: DurableRetryEvidence): Json =
    Json.obj(
      "attempts" -> Json.fromValues(value.attempts.map(_attempt_json)),
      "maxAttempts" -> Json.fromInt(value.maxAttempts),
      "nextRetryAt" -> _optional_json(value.nextRetryAt)(x => Json.fromString(x.toString)),
      "exhausted" -> Json.fromBoolean(value.exhausted),
      "recoveryRequired" -> Json.fromBoolean(value.recoveryRequired)
    )

  private def _attempt_json(value: DurableAttemptEvidence): Json =
    Json.obj(
      "number" -> Json.fromInt(value.number),
      "startedAt" -> Json.fromString(value.startedAt.toString),
      "finishedAt" -> _optional_json(value.finishedAt)(x => Json.fromString(x.toString)),
      "outcome" -> Json.fromString(value.outcome.wire),
      "failure" -> _optional_json(value.failure)(_failure_json)
    )

  private def _schedule_json(value: DurableScheduleState): Json =
    Json.obj(
      "scheduledAt" -> _optional_json(value.scheduledAt)(x => Json.fromString(x.toString)),
      "startedAt" -> _optional_json(value.startedAt)(x => Json.fromString(x.toString)),
      "completedAt" -> _optional_json(value.completedAt)(x => Json.fromString(x.toString))
    )

  private def _task_json(value: DurableTaskDescriptor): Json =
    Json.obj(
      "taskId" -> Json.fromString(value.taskId),
      "parentTaskId" -> _optional_json(value.parentTaskId)(Json.fromString),
      "kind" -> Json.fromString(value.kind.wire),
      "target" -> _target_json(value.target),
      "relation" -> _relation_json(value.relation),
      "transaction" -> _transaction_json(value.transaction),
      "compensation" -> _optional_json(value.compensation)(_compensation_json)
    )

  private def _target_json(value: DurableTaskTarget): Json =
    Json.obj(
      "targetKind" -> Json.fromString(value.targetKind),
      "operation" -> _operation_json(value.operation)
    )

  private def _operation_json(value: DurableOperationReference): Json =
    Json.obj(
      "componentId" -> Json.fromString(value.componentId),
      "serviceId" -> _optional_json(value.serviceId)(Json.fromString),
      "operationId" -> Json.fromString(value.operationId),
      "actionId" -> _optional_json(value.actionId)(Json.fromString)
    )

  private def _relation_json(value: DurableTaskRelation): Json =
    Json.obj(
      "kind" -> Json.fromString(value.kind.wire),
      "reference" -> _optional_json(value.reference)(Json.fromString)
    )

  private def _transaction_json(value: DurableTransactionDescriptor): Json =
    Json.obj(
      "role" -> Json.fromString(value.role.wire),
      "scope" -> Json.fromString(value.scope.wire),
      "outcome" -> Json.fromString(value.outcome.wire)
    )

  private def _compensation_json(value: DurableCompensationDescriptor): Json =
    Json.obj(
      "operation" -> _operation_json(value.operation),
      "compensatesTaskId" -> Json.fromString(value.compensatesTaskId),
      "status" -> Json.fromString(value.status.wire),
      "failure" -> _optional_json(value.failure)(_failure_json)
    )

  private def _input_json(value: DurableInputReference): Json =
    Json.obj("name" -> Json.fromString(value.name), "value" -> _value_json(value.value))

  private def _value_json(value: DurableValue): Json = value match {
    case DurableValue.Inline(metadata) => Json.obj(
      "storage" -> Json.fromString("inline-metadata"),
      "inlineMetadata" -> _inline_metadata_json(metadata),
      "externalReference" -> Json.Null
    )
    case DurableValue.External(reference) => Json.obj(
      "storage" -> Json.fromString("external-reference"),
      "inlineMetadata" -> Json.Null,
      "externalReference" -> _external_reference_json(reference)
    )
    case DurableValue.Absent => Json.obj(
      "storage" -> Json.fromString("absent"),
      "inlineMetadata" -> Json.Null,
      "externalReference" -> Json.Null
    )
  }

  private def _inline_metadata_json(value: DurableInlineMetadata): Json =
    Json.obj(
      "valueType" -> Json.fromString(value.valueType),
      "contentType" -> _optional_json(value.contentType)(Json.fromString),
      "byteSize" -> Json.fromLong(value.byteSize),
      "sha256" -> Json.fromString(value.sha256),
      "classification" -> Json.fromString(value.classification)
    )

  private def _external_reference_json(value: DurableExternalReference): Json =
    Json.obj(
      "reference" -> Json.fromString(value.reference),
      "storageKind" -> Json.fromString(value.storageKind),
      "contentType" -> _optional_json(value.contentType)(Json.fromString),
      "byteSize" -> Json.fromLong(value.byteSize),
      "sha256" -> Json.fromString(value.sha256)
    )

  private def _result_json(value: DurableResultOutcome): Json = value match {
    case DurableResultOutcome.Pending => Json.obj(
      "outcome" -> Json.fromString("pending"),
      "value" -> Json.Null,
      "failure" -> Json.Null
    )
    case DurableResultOutcome.Succeeded(result) => Json.obj(
      "outcome" -> Json.fromString("succeeded"),
      "value" -> _value_json(result),
      "failure" -> Json.Null
    )
    case DurableResultOutcome.Failed(failure) => Json.obj(
      "outcome" -> Json.fromString("failed"),
      "value" -> Json.Null,
      "failure" -> _failure_json(failure)
    )
    case DurableResultOutcome.Cancelled(summary) => Json.obj(
      "outcome" -> Json.fromString("cancelled"),
      "value" -> Json.Null,
      "failure" -> _optional_json(summary)(_failure_json)
    )
  }

  private def _failure_json(value: DurableFailureSummary): Json =
    Json.obj(
      "category" -> Json.fromString(value.category),
      "code" -> Json.fromString(value.code),
      "summary" -> Json.fromString(value.summary),
      "retryable" -> Json.fromBoolean(value.retryable)
    )

  private def _timeline_json(value: DurableTimelineEvent): Json =
    Json.obj(
      "sequence" -> Json.fromLong(value.sequence),
      "occurredAt" -> Json.fromString(value.occurredAt.toString),
      "kind" -> Json.fromString(value.kind),
      "taskId" -> _optional_json(value.taskId)(Json.fromString),
      "summary" -> _optional_json(value.summary)(Json.fromString)
    )

  private def _diagnostic_json(value: DurableDiagnosticSummary): Json =
    Json.obj(
      "category" -> Json.fromString(value.category),
      "code" -> Json.fromString(value.code),
      "severity" -> Json.fromString(value.severity),
      "summary" -> Json.fromString(value.summary)
    )

  private def _definition_json(value: DurableDefinitionSnapshot): Json =
    Json.obj(
      "definitionId" -> Json.fromString(value.definitionId),
      "key" -> Json.fromString(value.key),
      "version" -> Json.fromInt(value.version),
      "revision" -> Json.fromLong(value.revision),
      "hash" -> Json.fromString(value.hash),
      "declaredSource" -> _optional_json(value.declaredSource)(_external_reference_json),
      "format" -> _optional_json(value.format)(Json.fromString),
      "declaredProfile" -> _map_json(value.declaredProfile)
    )

  private def _retention_json(value: DurableRetentionState): Json =
    Json.obj(
      "retainUntil" -> _optional_json(value.retainUntil)(x => Json.fromString(x.toString)),
      "expiresAt" -> _optional_json(value.expiresAt)(x => Json.fromString(x.toString)),
      "deletedAt" -> _optional_json(value.deletedAt)(x => Json.fromString(x.toString)),
      "deletionState" -> Json.fromString(value.deletionState.wire),
      "tombstone" -> _optional_json(value.tombstone)(_tombstone_json)
    )

  private def _tombstone_json(value: DurableTombstone): Json =
    Json.obj(
      "deletedAt" -> Json.fromString(value.deletedAt.toString),
      "reason" -> Json.fromString(value.reason),
      "replacementRecordId" -> _optional_json(value.replacementRecordId)(Json.fromString)
    )

  private def _integrity_json(value: DurableRecordIntegrity): Json =
    Json.obj(
      "algorithm" -> Json.fromString(value.algorithm),
      "unsignedBodySha256" -> Json.fromString(value.unsignedBodySha256)
    )

  private def _map_json(value: Map[String, String]): Json =
    Json.obj(value.toVector.sortBy(_._1).map { case (key, entry) => key -> Json.fromString(entry) }*)

  private def _optional_json[A](value: Option[A])(f: A => Json): Json =
    value.map(f).getOrElse(Json.Null)

  private def _parse_json(text: String): Either[String, Json] =
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

  private def _only(cursor: ACursor, expected: Set[String], context: String): Either[String, Unit] =
    cursor.success.toRight(s"Durable record $context must be an object").flatMap { hcursor =>
      val unknown = hcursor.keys.toVector.flatten.filterNot(expected).sorted
      Either.cond(unknown.isEmpty, (), s"Unknown durable record $context fields: ${unknown.mkString(", ")}")
    }

  private def _unique(values: Vector[String], context: String): Either[String, Unit] = {
    val duplicates = values.groupBy(identity).collect { case (value, xs) if xs.size > 1 => value }.toVector.sorted
    Either.cond(duplicates.isEmpty, (), s"Duplicate $context identities: ${duplicates.mkString(", ")}")
  }

  private def _non_empty(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && value.trim.nonEmpty, (), s"$context must not be empty")

  private def _optional_non_empty(value: Option[String], context: String): Either[String, Unit] =
    value.map(_non_empty(_, context)).getOrElse(Right(()))

  private def _bounded(value: String, context: String, maximum: Int): Either[String, Unit] =
    Either.cond(value != null && value.trim.nonEmpty && value.length <= maximum, (), s"$context must be non-empty and no longer than $maximum characters")

  private def _validate_digest(value: String, context: String): Either[String, Unit] =
    Either.cond(value != null && "[0-9a-f]{64}".r.matches(value), (), s"$context must be a lowercase SHA-256 digest")

  private def _sha256(value: String): Either[String, String] =
    Try {
      MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
    }.toEither.left.map(error => s"Cannot calculate durable record SHA-256: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft(Right(Vector.empty): Either[String, Vector[A]]) { (acc, value) =>
      for {
        collected <- acc
        entry <- value
      } yield collected :+ entry
    }

  private def _from_either[A](value: Either[String, A]): Consequence[A] = value match {
    case Right(result) => Consequence.success(result)
    case Left(message) => _failure(message)
  }

  private def _failure[A](message: String): Consequence[A] =
    Consequence.argumentInvalid(message)

  private val _strict_json_parser = JawnParser(allowDuplicateKeys = false)
}
