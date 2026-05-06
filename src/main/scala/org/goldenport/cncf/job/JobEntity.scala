package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Store-backed management projection for JobEngine jobs.
 *
 * JobEngine remains the execution authority. JobEntity is the lightweight
 * SimpleEntity-compatible record used for search, admin, and management views.
 *
 * @since   May.  7, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object JobEntityCollections {
  val Job: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "job")
}

final case class JobEntity(
  id: EntityId,
  record: Record
) extends EntityPersistable {
  def toRecord(): Record =
    if (record.getAny("id").nonEmpty) record
    else record ++ Record.dataAuto("id" -> id.value)
}

object JobEntity {
  def entityId(jobId: JobId): EntityId =
    JobId.parse(jobId.value) match {
      case Consequence.Success(parsed) =>
        EntityId(
          major = parsed.major,
          minor = parsed.minor,
          collection = JobEntityCollections.Job,
          timestamp = parsed.timestamp,
          entropy = parsed.entropy
        )
      case Consequence.Failure(_) =>
        EntityId(
          major = jobId.major,
          minor = jobId.minor,
          collection = JobEntityCollections.Job,
          timestamp = jobId.timestamp,
          entropy = jobId.entropy
        )
    }

  def from(model: JobQueryReadModel): JobEntity = {
    val id = entityId(model.jobId)
    val target = model.tasks.tasks.headOption
    val record = Record.dataAuto(
      "id" -> id.value,
      "jobId" -> model.jobId.value,
      "name" -> model.debug.requestSummary.orElse(target.flatMap(_.operation)).getOrElse(model.jobId.value),
      "title" -> model.debug.requestSummary.orElse(target.flatMap(_.operation)).getOrElse(model.jobId.value),
      "status" -> model.status.toString,
      "persistence" -> model.persistence.toString,
      "origin" -> model.origin.toString,
      "createdAt" -> model.createdAt.toString,
      "updatedAt" -> model.updatedAt.toString,
      "scheduledStartAt" -> model.scheduledStartAt.map(_.toString),
      "submitterPrincipalId" -> model.submitter.principalId,
      "submitterSubjectKind" -> model.submitter.subjectKind,
      "submitterSessionId" -> model.submitter.sessionId,
      "component" -> target.flatMap(_.component),
      "service" -> target.flatMap(_.service),
      "operation" -> target.flatMap(_.operation),
      "resultStatus" -> model.resultSummary.status.toString,
      "resultSuccess" -> model.resultSummary.success,
      "resultMessage" -> model.resultSummary.message,
      "resultAvailable" -> model.result.nonEmpty,
      "retryKind" -> model.retry.kind.toString,
      "retryAttemptCount" -> model.retry.attemptCount,
      "retryMaxAttempts" -> model.retry.maxAttempts,
      "retryNextDueAt" -> model.retry.nextRetryDueAt.map(_.toString),
      "retryExhausted" -> model.retry.exhausted,
      "recoveryRequired" -> model.retry.recoveryRequired,
      "deadLetter" -> model.retry.deadLetter,
      "poison" -> model.retry.poison,
      "lastFailureUserAction" -> model.retry.lastFailureUserAction,
      "lastFailureMessage" -> model.retry.lastFailureMessage,
      "taskCount" -> model.tasks.totalCount,
      "timelineCount" -> model.timeline.totalCount,
      "calltreeSaved" -> model.debug.calltreeSaved,
      "calltreeStorage" -> model.debug.calltreeStorage,
      "calltreeSerializedBytes" -> model.debug.calltreeSerializedBytes,
      "calltreeDropReason" -> model.debug.calltreeDropReason,
      "requestSummary" -> model.debug.requestSummary,
      "debugParameters" -> Record.data(model.debug.parameters.toVector.sortBy(_._1)*),
      "debugExecutionNotes" -> model.debug.executionNotes.mkString("\n"),
      "lineage" -> _lineage_record(model.lineage),
      "retry" -> _retry_record(model.retry),
      "taskSummary" -> _task_summary_record(model),
      "timelineSummary" -> _timeline_summary_record(model)
    )
    JobEntity(id, record)
  }

  def fromRecord(record: Record): Consequence[JobEntity] =
    EntityId.createC(record).map(id => JobEntity(id.copy(collection = JobEntityCollections.Job), record))

  given EntityPersistent[JobEntity] with {
    def id(e: JobEntity): EntityId = e.id
    def toRecord(e: JobEntity): Record = e.toRecord()
    override def toStoreRecord(e: JobEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[JobEntity] = JobEntity.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[JobEntity] = JobEntity.fromRecord(r)
  }

  def entityPersistent: EntityPersistent[JobEntity] =
    summon[EntityPersistent[JobEntity]]

  private def _retry_record(retry: JobRetryState): Record =
    Record.dataAuto(
      "kind" -> retry.kind.toString,
      "attemptCount" -> retry.attemptCount,
      "maxAttempts" -> retry.maxAttempts,
      "nextRetryDueAt" -> retry.nextRetryDueAt.map(_.toString),
      "exhausted" -> retry.exhausted,
      "recoveryRequired" -> retry.recoveryRequired,
      "deadLetter" -> retry.deadLetter,
      "poison" -> retry.poison,
      "lastFailureUserAction" -> retry.lastFailureUserAction,
      "lastFailureMessage" -> retry.lastFailureMessage
    )

  private def _lineage_record(lineage: JobEventLineage): Record =
    Record.dataAuto(
      "eventName" -> lineage.eventName,
      "eventKind" -> lineage.eventKind,
      "parentJobId" -> lineage.parentJobId,
      "correlationId" -> lineage.correlationId,
      "sagaId" -> lineage.sagaId,
      "causationId" -> lineage.causationId,
      "sourceSubsystem" -> lineage.sourceSubsystem,
      "sourceComponent" -> lineage.sourceComponent,
      "targetSubsystem" -> lineage.targetSubsystem,
      "targetComponent" -> lineage.targetComponent,
      "receptionRule" -> lineage.receptionRule,
      "receptionPolicy" -> lineage.receptionPolicy,
      "policySource" -> lineage.policySource,
      "jobRelation" -> lineage.jobRelation,
      "taskRelation" -> lineage.taskRelation,
      "transactionRelation" -> lineage.transactionRelation,
      "sagaRelation" -> lineage.sagaRelation,
      "failurePolicy" -> lineage.failurePolicy,
      "failureDisposition" -> lineage.failureDisposition.print
    )

  private def _task_summary_record(model: JobQueryReadModel): Record =
    Record.dataAuto(
      "totalCount" -> model.tasks.totalCount,
      "fetchedCount" -> model.tasks.fetchedCount,
      "running" -> model.tasks.tasks.count(_.status == JobTaskStatus.Running),
      "succeeded" -> model.tasks.tasks.count(_.status == JobTaskStatus.Succeeded),
      "failed" -> model.tasks.tasks.count(_.status == JobTaskStatus.Failed)
    )

  private def _timeline_summary_record(model: JobQueryReadModel): Record =
    Record.dataAuto(
      "totalCount" -> model.timeline.totalCount,
      "fetchedCount" -> model.timeline.fetchedCount,
      "lastEventKind" -> model.timeline.events.lastOption.map(_.kind),
      "lastEventAt" -> model.timeline.events.lastOption.map(_.occurredAt.toString)
    )
}
