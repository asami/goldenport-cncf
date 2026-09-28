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
private[job] trait DurableJobRecordEncodingPart { self: DurableJobRecordCodec.type =>
  import DurableJobRecordCodec.*

  private[job] def _unsigned_json(format: DurableRecordFormat, body: DurableJobRecordBody): Json =
    Json.obj(
      "format" -> _format_json(format),
      "record" -> _body_json(body)
    )

  private[job] def _signed_json(record: DurableJobRecord): Json =
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

}
