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
final case class JobContext(
  jobId: Option[JobId],
  taskId: Option[TaskId],
  actionId: Option[ActionId],
  parentJobId: Option[JobId] = None,
  currentTask: Option[TaskId] = None,
  taskStack: Vector[TaskId] = Vector.empty,
  causationId: Option[String] = None,
  traceMetadata: Map[String, String] = Map.empty,
  cancellationScope: Option[JobCancellationScope] = None
)

object JobContext {
  val empty: JobContext = JobContext(None, None, None)
}

sealed trait JobStatus
object JobStatus {
  case object Submitted extends JobStatus
  case object Running extends JobStatus
  case object Suspended extends JobStatus
  case object Cancelled extends JobStatus
  case object Succeeded extends JobStatus
  case object Failed extends JobStatus

  def isTerminal(status: JobStatus): Boolean =
    status == Cancelled || status == Succeeded || status == Failed
}

enum JobControlCommand {
  case Cancel
  case Retry
  case Suspend
  case Resume
}

enum JobCommandMode {
  case Async
  case Sync
}

final case class JobControlOption(
  mode: JobCommandMode = JobCommandMode.Async,
  timeoutMillis: Long = 3000L,
  pollMillis: Long = 10L
)

final case class JobControlRequest(
  command: JobControlCommand,
  option: JobControlOption = JobControlOption()
)

final case class JobControlResponse(
  jobId: JobId,
  status: JobStatus,
  response: Option[OperationResponse],
  async: Boolean,
  changed: Boolean = true
)

final case class JobMetrics(
  running: Int,
  queued: Int,
  completed: Int,
  failed: Int
)

trait JobControlPolicy {
  def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit]
}

object JobControlPolicy {
  val default: JobControlPolicy = new DefaultJobControlPolicy

  private final class DefaultJobControlPolicy extends JobControlPolicy {
    private val _control_caps = Set("job_control", "job_admin", "content_manager", "content_admin")

    def authorize(jobId: JobId, request: JobControlRequest)(using
        ctx: ExecutionContext
    ): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      if (ctx.security.hasAnyCapability(_control_caps))
        Consequence.unit
      else
        Consequence.operationIllegal(
          "job.control",
          s"required capability: ${_control_caps.toVector.sorted.mkString("|")}"
        )
    }
  }
}

sealed trait JobResult
object JobResult {
  final case class Success(response: OperationResponse) extends JobResult
  final case class Failure(conclusion: Conclusion) extends JobResult
}
