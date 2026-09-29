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
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizer, ObservabilityEngine}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobDataOrigin {
  case Durable
  case Runtime
}

enum AsyncFailureDisposition {
  case NotApplicable
  case Retryable
  case Terminal

  def print: String = this match {
    case AsyncFailureDisposition.NotApplicable => "not-applicable"
    case AsyncFailureDisposition.Retryable => "retryable"
    case AsyncFailureDisposition.Terminal => "terminal"
  }
}

enum JobTaskStatus {
  case Running
  case Succeeded
  case Failed
}

enum JobTaskTransactionOutcome {
  case Running
  case Committed
  case Failed
  case CompensationCommitted
  case CompensationFailed

  def print: String = this match {
    case JobTaskTransactionOutcome.Running => "running"
    case JobTaskTransactionOutcome.Committed => "committed"
    case JobTaskTransactionOutcome.Failed => "failed"
    case JobTaskTransactionOutcome.CompensationCommitted => "compensation-committed"
    case JobTaskTransactionOutcome.CompensationFailed => "compensation-failed"
  }
}

final case class JobResultSummary(
  status: JobStatus,
  success: Boolean,
  message: Option[String]
)

final case class JobTaskResultSummary(
  success: Boolean,
  message: Option[String]
)

final case class JobTaskReadModel(
  taskId: TaskId,
  parentTaskId: Option[TaskId],
  status: JobTaskStatus,
  startedAt: Instant,
  finishedAt: Option[Instant],
  result: JobTaskResultSummary,
  component: Option[String] = None,
  service: Option[String] = None,
  operation: Option[String] = None,
  taskKind: String = "action",
  targetKind: Option[String] = None,
  relation: Option[String] = None,
  transactionRole: Option[String] = None,
  transactionScope: Option[String] = None,
  transactionOutcome: Option[String] = None,
  compensationActionRef: Option[String] = None,
  compensatesTaskId: Option[TaskId] = None,
  compensationStatus: Option[String] = None,
  compensationFailureSummary: Option[String] = None,
  recoveryRequired: Boolean = false
)

final case class JobTimelineEvent(
  sequence: Long,
  occurredAt: Instant,
  kind: String,
  taskId: Option[TaskId],
  parentTaskId: Option[TaskId],
  note: Option[String]
)

final case class JobDebugInfo(
  requestSummary: Option[String],
  parameters: Map[String, String],
  executionNotes: Vector[String],
  declaredProfile: Option[JobDeclaredProfile] = None,
  jobDefinitionSnapshot: Option[JobDefinitionSnapshot] = None,
  calltree: Option[Record] = None,
  calltreeJson: Option[String] = None,
  calltreeClob: Option[String] = None,
  calltreeSaved: Boolean = false,
  calltreeStorage: Option[String] = None,
  calltreeSerializedBytes: Option[Int] = None,
  calltreePayloadReference: Option[Record] = None,
  calltreeDropReason: Option[String] = None
)

final case class JobEventLineage(
  eventName: Option[String],
  eventKind: Option[String],
  parentJobId: Option[String],
  correlationId: Option[String],
  sagaId: Option[String],
  causationId: Option[String],
  sourceSubsystem: Option[String],
  sourceComponent: Option[String],
  targetSubsystem: Option[String],
  targetComponent: Option[String],
  receptionRule: Option[String],
  receptionPolicy: Option[String],
  policySource: Option[String],
  jobRelation: Option[String],
  taskRelation: Option[String],
  transactionRelation: Option[String],
  sagaRelation: Option[String],
  failurePolicy: Option[String],
  failureDisposition: AsyncFailureDisposition
) {
  def eventTriggered: Boolean =
    eventName.nonEmpty || receptionRule.nonEmpty || receptionPolicy.nonEmpty
}

final case class JobContinuationTaskRef(
  taskId: TaskId,
  status: String,
  action: Option[String],
  parentTaskId: Option[TaskId]
)

final case class JobContinuationSummary(
  mode: Option[String],
  policy: Option[String],
  tasks: Vector[JobContinuationTaskRef]
) {
  def taskIds: Vector[TaskId] =
    tasks.map(_.taskId)

  def hasContinuation: Boolean =
    mode.nonEmpty || tasks.nonEmpty
}

object JobContinuationSummary {
  val empty: JobContinuationSummary =
    JobContinuationSummary(None, None, Vector.empty)
}

final case class JobTimelinePage(
  offset: Int,
  limit: Int,
  totalCount: Int,
  fetchedCount: Int,
  events: Vector[JobTimelineEvent]
)

final case class JobTaskPage(
  offset: Int,
  limit: Int,
  totalCount: Int,
  fetchedCount: Int,
  tasks: Vector[JobTaskReadModel]
)

final case class JobTraceTaskNode(
  taskId: TaskId,
  parentTaskId: Option[TaskId],
  taskKind: String,
  relation: Option[String],
  status: JobTaskStatus,
  transactionRole: Option[String],
  transactionScope: Option[String],
  transactionOutcome: Option[String],
  compensationStatus: Option[String],
  recoveryRequired: Boolean,
  events: Vector[JobTimelineEvent],
  children: Vector[JobTraceTaskNode]
)

final case class JobTraceTree(
  jobId: JobId,
  roots: Vector[JobTraceTaskNode]
)

final case class JobTaskDetail(
  jobId: JobId,
  task: JobTaskReadModel,
  events: Vector[JobTimelineEvent],
  children: Vector[JobTraceTaskNode]
)

enum JobRetryKind {
  case None
  case Immediate
  case Delayed

  def print: String = this match {
    case JobRetryKind.None => "none"
    case JobRetryKind.Immediate => "now"
    case JobRetryKind.Delayed => "later"
  }
}

final case class JobRetryState(
  kind: JobRetryKind = JobRetryKind.None,
  attemptCount: Int = 0,
  maxAttempts: Int = 3,
  nextRetryDueAt: Option[Instant] = None,
  exhausted: Boolean = false,
  recoveryRequired: Boolean = false,
  deadLetter: Boolean = false,
  poison: Boolean = false,
  lastFailureUserAction: Option[String] = None,
  lastFailureMessage: Option[String] = None
)

final case class JobQueryReadModel(
  jobId: JobId,
  status: JobStatus,
  persistence: JobPersistencePolicy,
  origin: JobDataOrigin,
  submitter: JobSubmitter,
  createdAt: Instant,
  updatedAt: Instant,
  scheduledStartAt: Option[Instant],
  tasks: JobTaskPage,
  timeline: JobTimelinePage,
  traceTree: JobTraceTree,
  debug: JobDebugInfo,
  input: Option[JobInput],
  lineage: JobEventLineage,
  continuation: JobContinuationSummary,
  retry: JobRetryState,
  resultSummary: JobResultSummary,
  calltree: Option[Record],
  result: Option[OperationResponse]
)

final case class JobSubmitter(
  principalId: String,
  subjectKind: String,
  sessionId: Option[String] = None
)

object JobSubmitter {
  def from(ctx: ExecutionContext): JobSubmitter =
    JobSubmitter(
      ctx.security.principal.id.value,
      ctx.security.subjectKind.toString,
      ctx.security.session.flatMap(_.sessionId)
    )
}

trait JobQueryPolicy {
  def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit]

  /** An empty key preserves the original management cursor wire format. */
  def visibilityKey: String = ""
}

object JobQueryPolicy {
  val default: JobQueryPolicy = new DefaultJobQueryPolicy

  /**
   * Canonical session-first ownership rule shared by management and UX projections.
   * A stored session is authoritative; older records fall back to principal and kind.
   */
  def isOwner(submitter: JobSubmitter, context: ExecutionContext): Boolean =
    submitter.sessionId match {
      case Some(sessionid) =>
        context.security.session.flatMap(_.sessionId).contains(sessionid)
      case None =>
        submitter.principalId == context.security.principal.id.value &&
          submitter.subjectKind == context.security.subjectKind.toString
    }

  private final class DefaultJobQueryPolicy extends JobQueryPolicy {
    private val _read_caps = Set("job_view", "job_admin", "content_manager", "content_admin")

    def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] =
      if (ctx.security.hasAnyCapability(_read_caps))
        Consequence.unit
      else if (isOwner(model.submitter, ctx))
        Consequence.unit
      else
        Consequence.operationIllegal(
          "job.query",
          s"job is not owned by the current subject; required capability: ${_read_caps.toVector.sorted.mkString("|")}"
        )
  }
}
