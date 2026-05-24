package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
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
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object JobEntityCollections {
  val Job: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "job")
  val JobDefinition: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "jobDefinition")
}

enum JobDefinitionStatus {
  case Draft
  case Active
  case Retired

  def print: String = this match {
    case Draft => "draft"
    case Active => "active"
    case Retired => "retired"
  }
}

object JobDefinitionStatus {
  def parse(text: String): Consequence[JobDefinitionStatus] =
    text.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "draft" => Consequence.success(JobDefinitionStatus.Draft)
      case "active" => Consequence.success(JobDefinitionStatus.Active)
      case "retired" => Consequence.success(JobDefinitionStatus.Retired)
      case other => Consequence.argumentInvalid(s"unknown JobDefinition status: $other")
    }
}

final case class JobDefinitionEntity(
  id: EntityId,
  key: String,
  jclSource: String,
  normalizedProfile: Option[JobDeclaredProfile],
  flowSource: Option[String],
  eventsSource: Option[String],
  onEventSource: Option[String],
  version: Int,
  revision: Int,
  hash: String,
  status: JobDefinitionStatus,
  targetComponent: Option[String],
  targetService: Option[String],
  targetOperation: Option[String],
  targetAction: Option[String],
  createdAt: Instant,
  updatedAt: Instant
) extends EntityPersistable {
  def isActive: Boolean =
    status == JobDefinitionStatus.Active

  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "key" -> key,
      "name" -> key,
      "title" -> key,
      "jclSource" -> jclSource,
      "normalizedProfile" -> normalizedProfile.map(_.toRecord),
      "flow" -> flowSource,
      "events" -> eventsSource,
      "onEvent" -> onEventSource,
      "version" -> version,
      "revision" -> revision,
      "hash" -> hash,
      "definitionStatus" -> status.print,
      "targetComponent" -> targetComponent,
      "targetService" -> targetService,
      "targetOperation" -> targetOperation,
      "targetAction" -> targetAction,
      "createdAt" -> createdAt.toString,
      "updatedAt" -> updatedAt.toString
    )

  def snapshotRecord: Record =
    Record.dataAuto(
      "jobDefinitionId" -> id.value,
      "jobDefinitionKey" -> key,
      "jobDefinitionVersion" -> version,
      "jobDefinitionRevision" -> revision,
      "jobDefinitionHash" -> hash,
      "declaredProfile" -> normalizedProfile.map(_.toRecord),
      "jclSource" -> jclSource
    )
}

object JobDefinitionEntity {
  def entityId(key: String): EntityId =
    EntityId("cncf", _entity_id_label(key), JobEntityCollections.JobDefinition)

  def create(
    key: String,
    jclSource: String,
    profile: Option[JobDeclaredProfile],
    flowSource: Option[String],
    eventsSource: Option[String],
    onEventSource: Option[String],
    status: JobDefinitionStatus,
    targetAction: Option[String],
    now: Instant = Instant.now()
  ): JobDefinitionEntity =
    JobDefinitionEntity(
      id = entityId(key),
      key = _normalize_key(key),
      jclSource = jclSource,
      normalizedProfile = profile,
      flowSource = flowSource,
      eventsSource = eventsSource,
      onEventSource = onEventSource,
      version = 1,
      revision = 1,
      hash = hashOf(jclSource),
      status = status,
      targetComponent = targetAction.flatMap(_target_part(_, 0)),
      targetService = targetAction.flatMap(_target_part(_, 1)),
      targetOperation = targetAction.flatMap(_target_part(_, 2)),
      targetAction = targetAction,
      createdAt = now,
      updatedAt = now
    )

  def updated(
    current: JobDefinitionEntity,
    jclSource: String,
    profile: Option[JobDeclaredProfile],
    flowSource: Option[String],
    eventsSource: Option[String],
    onEventSource: Option[String],
    status: Option[JobDefinitionStatus],
    targetAction: Option[String],
    now: Instant = Instant.now()
  ): JobDefinitionEntity = {
    val newhash = hashOf(jclSource)
    val changed = newhash != current.hash
    current.copy(
      jclSource = jclSource,
      normalizedProfile = profile,
      flowSource = flowSource,
      eventsSource = eventsSource,
      onEventSource = onEventSource,
      version = if (changed) current.version + 1 else current.version,
      revision = current.revision + 1,
      hash = newhash,
      status = status.getOrElse(current.status),
      targetComponent = targetAction.orElse(current.targetAction).flatMap(_target_part(_, 0)),
      targetService = targetAction.orElse(current.targetAction).flatMap(_target_part(_, 1)),
      targetOperation = targetAction.orElse(current.targetAction).flatMap(_target_part(_, 2)),
      targetAction = targetAction.orElse(current.targetAction),
      updatedAt = now
    )
  }

  def fromRecord(record: Record): Consequence[JobDefinitionEntity] =
    for {
      key <- _required(record, "key")
      jcl <- _required(record, "jclSource")
      status <- JobDefinitionStatus.parse(record.getString("definitionStatus").getOrElse("draft"))
      id <- EntityId.createC(record).map(_.copy(collection = JobEntityCollections.JobDefinition))
      parsed = JobBatchDefinition.parseYaml(jcl).toOption.flatMap(_.jobs.headOption)
    } yield JobDefinitionEntity(
      id = id,
      key = _normalize_key(key),
      jclSource = jcl,
      normalizedProfile = parsed.flatMap(_.profile),
      flowSource = record.getString("flow").orElse(parsed.flatMap(_.flow.map(_.show))),
      eventsSource = record.getString("events").orElse(parsed.flatMap(_.events.map(_.show))),
      onEventSource = record.getString("onEvent").orElse(parsed.flatMap(_.onEvent.map(_.show))),
      version = record.getInt("version").getOrElse(1),
      revision = record.getInt("revision").getOrElse(1),
      hash = record.getString("hash").getOrElse(hashOf(jcl)),
      status = status,
      targetComponent = record.getString("targetComponent").orElse(parsed.flatMap(_.target.action).flatMap(_target_part(_, 0))),
      targetService = record.getString("targetService").orElse(parsed.flatMap(_.target.action).flatMap(_target_part(_, 1))),
      targetOperation = record.getString("targetOperation").orElse(parsed.flatMap(_.target.action).flatMap(_target_part(_, 2))),
      targetAction = record.getString("targetAction").orElse(parsed.flatMap(_.target.action)),
      createdAt = record.getString("createdAt").flatMap(x => scala.util.Try(Instant.parse(x)).toOption).getOrElse(Instant.EPOCH),
      updatedAt = record.getString("updatedAt").flatMap(x => scala.util.Try(Instant.parse(x)).toOption).getOrElse(Instant.EPOCH)
    )

  def hashOf(text: String): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
    digest.map(b => "%02x".format(b & 0xff)).mkString
  }

  given EntityPersistent[JobDefinitionEntity] with {
    def id(e: JobDefinitionEntity): EntityId = e.id
    def toRecord(e: JobDefinitionEntity): Record = e.toRecord()
    override def toStoreRecord(e: JobDefinitionEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[JobDefinitionEntity] = JobDefinitionEntity.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[JobDefinitionEntity] = JobDefinitionEntity.fromRecord(r)
  }

  def entityPersistent: EntityPersistent[JobDefinitionEntity] =
    summon[EntityPersistent[JobDefinitionEntity]]

  private def _normalize_key(key: String): String =
    key.trim

  private def _entity_id_label(key: String): String = {
    val normalized = key.trim.map {
      case c if c.isLetterOrDigit || c == '_' => c
      case _ => '_'
    }.mkString
    val nonempty = if (normalized.nonEmpty) normalized else "jobDefinition"
    if (nonempty.headOption.exists(_.isLetter)) nonempty else s"j_$nonempty"
  }

  private def _required(record: Record, key: String): Consequence[String] =
    record.getString(key).filter(_.trim.nonEmpty) match {
      case Some(value) => Consequence.success(value.trim)
      case None => Consequence.argumentMissing(key)
    }

  private def _target_part(action: String, index: Int): Option[String] =
    action.split("\\.").toVector.lift(index).filter(_.nonEmpty)
}

final case class JobDefinitionSnapshot(
  id: String,
  key: String,
  version: Int,
  revision: Int,
  hash: String,
  profile: Option[JobDeclaredProfile],
  jclSource: Option[String]
) {
  def toParameters: Map[String, String] =
    Map(
      "jcl.jobDefinition.id" -> id,
      "jcl.jobDefinition.key" -> key,
      "jcl.jobDefinition.version" -> version.toString,
      "jcl.jobDefinition.revision" -> revision.toString,
      "jcl.jobDefinition.hash" -> hash
    ) ++ jclSource.map("jcl.jobDefinition.source" -> _)
}

object JobDefinitionSnapshot {
  def from(entity: JobDefinitionEntity): JobDefinitionSnapshot =
    JobDefinitionSnapshot(
      id = entity.id.value,
      key = entity.key,
      version = entity.version,
      revision = entity.revision,
      hash = entity.hash,
      profile = entity.normalizedProfile,
      jclSource = Some(entity.jclSource)
    )
}

final case class TaskExecutionTree(
  jobId: JobId,
  roots: Vector[TaskExecutionNode]
)

final case class TaskExecutionNode(
  taskId: String,
  parentTaskId: Option[String],
  relation: Option[String],
  children: Vector[TaskExecutionNode] = Vector.empty
)

final case class TaskCalltreeReference(
  taskId: String,
  storage: Option[String],
  reference: Option[String]
)

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
      "calltreePayloadReference" -> model.debug.calltreePayloadReference,
      "calltreeDropReason" -> model.debug.calltreeDropReason,
      "requestSummary" -> model.debug.requestSummary,
      "input" -> model.input.map(_.toRecord(includeraw = false)),
      "jobDefinitionId" -> model.debug.jobDefinitionSnapshot.map(_.id),
      "jobDefinitionKey" -> model.debug.jobDefinitionSnapshot.map(_.key),
      "jobDefinitionVersion" -> model.debug.jobDefinitionSnapshot.map(_.version),
      "jobDefinitionRevision" -> model.debug.jobDefinitionSnapshot.map(_.revision),
      "jobDefinitionHash" -> model.debug.jobDefinitionSnapshot.map(_.hash),
      "debugParameters" -> Record.data(model.debug.parameters.toVector.sortBy(_._1)*),
      "debugExecutionNotes" -> model.debug.executionNotes.mkString("\n"),
      "declaredProfile" -> model.debug.declaredProfile.map(_.toRecord),
      "jobDefinitionSnapshot" -> model.debug.jobDefinitionSnapshot.map(_.toParameters).map(xs => Record.data(xs.toVector.sortBy(_._1)*)),
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
