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
 * @version Sep. 28, 2026
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
