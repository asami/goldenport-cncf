package org.goldenport.cncf.job

import java.security.MessageDigest
import java.time.{Duration, Instant}
import java.util.Base64
import java.util.concurrent.{
  ConcurrentHashMap,
  Executors,
  PriorityBlockingQueue,
  ScheduledExecutorService,
  TimeUnit,
  ExecutorService
}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.ExecutionContext as ScalaExecutionContext
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.consequence.Failures
import org.goldenport.id.UniversalId
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.conclusion.Disposition
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentLogic}
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionInvocationIdentity,
  ExecutionProfileRuntime,
  ExecutionSchedulerMode,
  ExecutionSchedulingRegistration,
  IdGenerationContext
}
import org.goldenport.cncf.entity.{
  EntityMutationExecutionPolicy,
  EntityPersistentCreate,
  EntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.event.{
  EventBus,
  EventLane,
  EventPublishOption,
  EventRecordFactory,
  EventStore,
  ReceptionDomainEvent
}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizer, ObservabilityEngine}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobPersistencePolicy {
  case Persistent
  case Ephemeral
}

final case class JobSubmitOption(
  persistence: JobPersistencePolicy = JobPersistencePolicy.Persistent,
  runMode: JobRunMode = JobRunMode.Async,
  priority: Int = 0,
  scheduledStartAt: Option[Instant] = None,
  requestSummary: Option[String] = None,
  parameters: Map[String, String] = Map.empty,
  executionNotes: Vector[String] = Vector.empty,
  declaredProfile: Option[JobDeclaredProfile] = None,
  jobDefinitionSnapshot: Option[JobDefinitionSnapshot] = None,
  input: Option[JobInput] = None
)

enum JobRunMode {
  case Async
  case Sync
}

enum JobInputRetentionPolicy {
  case DeleteOnCompletion
  case Ttl
  case Keep

  def print: String = this match {
    case DeleteOnCompletion => "delete-on-completion"
    case Ttl => "ttl"
    case Keep => "keep"
  }
}

object JobInputRetentionPolicy {
  def parse(value: String): Option[JobInputRetentionPolicy] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "delete-on-completion" | "delete_on_completion" | "delete" => Some(DeleteOnCompletion)
      case "ttl" => Some(Ttl)
      case "keep" => Some(Keep)
      case _ => None
    }
}

final case class JobInputPayload(
  storage: String,
  fieldName: Option[String] = None,
  filename: Option[String] = None,
  contentType: Option[String] = None,
  byteSize: Option[Long] = None,
  sha256: Option[String] = None,
  inlineBase64: Option[String] = None,
  blobId: Option[String] = None,
  createdAt: Instant
) {
  def sanitized: JobInputPayload =
    copy(inlineBase64 = None, blobId = None)

  def toRecord(includeraw: Boolean = false): Record =
    Record.dataAuto(
      "storage" -> storage,
      "fieldName" -> fieldName,
      "filename" -> filename,
      "contentType" -> contentType,
      "byteSize" -> byteSize,
      "sha256" -> sha256,
      "inlineBase64" -> Option.when(includeraw)(inlineBase64).flatten,
      "blobId" -> Option.when(includeraw || !isCleaned)(blobId).flatten,
      "createdAt" -> createdAt.toString
    )

  def isCleaned: Boolean =
    inlineBase64.isEmpty && blobId.isEmpty
}

final case class JobInput(
  payloads: Vector[JobInputPayload] = Vector.empty,
  retentionPolicy: JobInputRetentionPolicy = JobInputRetentionPolicy.Ttl,
  ttl: Duration = JobInput.DefaultTtl,
  createdAt: Instant,
  cleanedAt: Option[Instant] = None
) {
  def isCleaned: Boolean = cleanedAt.nonEmpty

  def cleaned(now: Instant): JobInput =
    copy(payloads = payloads.map(_.sanitized), cleanedAt = Some(now))

  def shouldCleanup(status: JobStatus, now: Instant): Boolean =
    !isCleaned && JobStatus.isTerminal(status) && (retentionPolicy match {
      case JobInputRetentionPolicy.DeleteOnCompletion => true
      case JobInputRetentionPolicy.Ttl => !now.isBefore(createdAt.plus(ttl))
      case JobInputRetentionPolicy.Keep => false
    })

  def toRecord(includeraw: Boolean = false): Record =
    Record.dataAuto(
      "payloads" -> payloads.map(_.toRecord(includeraw)),
      "retentionPolicy" -> retentionPolicy.print,
      "ttlSeconds" -> ttl.getSeconds,
      "createdAt" -> createdAt.toString,
      "cleanedAt" -> cleanedAt.map(_.toString)
    )
}

object JobInput {
  val DefaultInlineThresholdBytes: Long = 64L * 1024L
  val DefaultTtl: Duration = Duration.ofDays(7)

  def inline(
    fieldName: String,
    bytes: Array[Byte],
    filename: Option[String],
    contentType: Option[String],
    now: Instant
  ): JobInputPayload =
    JobInputPayload(
      storage = "inline",
      fieldName = Some(fieldName),
      filename = filename,
      contentType = contentType,
      byteSize = Some(bytes.length.toLong),
      sha256 = Some(_sha256(bytes)),
      inlineBase64 = Some(Base64.getEncoder.encodeToString(bytes)),
      createdAt = now
    )

  def blob(
    fieldName: String,
    blobId: String,
    filename: Option[String],
    contentType: Option[String],
    byteSize: Option[Long],
    sha256: Option[String],
    now: Instant
  ): JobInputPayload =
    JobInputPayload(
      storage = "blob",
      fieldName = Some(fieldName),
      filename = filename,
      contentType = contentType,
      byteSize = byteSize,
      sha256 = sha256,
      blobId = Some(blobId),
      createdAt = now
    )

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    digest.map("%02x".format(_)).mkString
  }
}
