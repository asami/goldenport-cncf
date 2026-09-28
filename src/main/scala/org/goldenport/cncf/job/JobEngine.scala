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
 * @since   Jan.  4, 2026
 *  version Mar. 30, 2026
 *  version May. 31, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
trait JobEngine {
  def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId]
  def submit(
      tasks: List[JobTask],
      ctx: ExecutionContext,
      option: JobSubmitOption
  ): Consequence[JobId]
  def quiesce(): Unit = shutdown()
  def forceCancel(): Unit = shutdown()
  def shutdown(): Unit = ()
  def getStatus(jobId: JobId): Option[JobStatus]
  def getResult(jobId: JobId): Option[JobResult]
  def getPrimaryResult(jobId: JobId): Option[JobResult] = getResult(jobId)
  def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult]
  def control(
    jobId: JobId,
    request: JobControlRequest,
    policy: JobControlPolicy = JobControlPolicy.default
  )(using ExecutionContext): Consequence[JobControlResponse]

  def getResponse(jobId: JobId): Option[OperationResponse] =
    getResult(jobId) match {
      case Some(JobResult.Success(response)) => Some(response)
      case _ => None
    }

  def query(jobId: JobId): Option[JobQueryReadModel]
  def queryPage(
    request: JobManagementQuery,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[JobManagementPage] =
    Consequence.operationInvalid("job.management-query", "page queries are not supported by this engine")
  def listJobs(limit: Int = 100, persistentOnly: Boolean = true): Vector[JobQueryReadModel] =
    Vector.empty
  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage]
  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage]
  def queryTaskExecutionTree(jobId: JobId): Option[JobTraceTree] =
    query(jobId).map(_.traceTree)
  def queryTaskDetail(jobId: JobId, taskId: TaskId): Option[JobTaskDetail] =
    query(jobId).flatMap { model =>
      model.tasks.tasks.find(_.taskId == taskId).map { task =>
        val children = model.traceTree.roots.flatMap(_find_children(_, taskId))
        JobTaskDetail(
          jobId = jobId,
          task = task,
          events = model.timeline.events.filter(_.taskId.contains(taskId)),
          children = children
        )
      }
    }
  def queryManagementDetail(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobManagementDetail]] =
    Consequence.operationInvalid("job.management-detail", "exact management detail is not supported by this engine")
  def queryManagementResult(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobManagementResult]] =
    Consequence.operationInvalid("job.management-result", "exact management result is not supported by this engine")
  def queryManagementTasks(
    jobId: JobId,
    offset: Int = 0,
    limit: Int = JobManagementQuery.DefaultLimit,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobTaskPage]] =
    Consequence.operationInvalid("job.management-tasks", "exact management tasks are not supported by this engine")
  def queryManagementTimeline(
    jobId: JobId,
    offset: Int = 0,
    limit: Int = JobManagementQuery.DefaultLimit,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobTimelinePage]] =
    Consequence.operationInvalid("job.management-timeline", "exact management timeline is not supported by this engine")
  def queryManagementTaskExecutionTree(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobTraceTree]] =
    Consequence.operationInvalid("job.management-task-tree", "exact management task tree is not supported by this engine")
  def queryManagementTaskDetail(
    jobId: JobId,
    taskId: TaskId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobTaskDetail]] =
    Consequence.operationInvalid("job.management-task-detail", "exact management task detail is not supported by this engine")
  def metrics: Option[JobMetrics] = None
  def annotateJob(
      jobId: JobId,
      parameters: Map[String, String],
      executionNotes: Vector[String] = Vector.empty
  ): Unit = ()
  def cleanupExpiredInputs(now: Instant): Int = 0
  def annotateJobProfile(jobId: JobId, profile: JobDeclaredProfile): Unit = ()
  def runTaskInJobSync(
      jobId: JobId,
      task: JobTask,
      ctx: ExecutionContext
  ): Consequence[TaskOutcome] =
    Consequence.operationInvalid("job.same-job.sync-task")
  def enqueueTaskInJob(jobId: JobId, task: JobTask, ctx: ExecutionContext): Consequence[TaskId] =
    Consequence.operationInvalid("job.same-job.async-task")

  def queryVisible(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobQueryReadModel]] =
    query(jobId) match {
      case Some(model) => policy.authorizeRead(model).map(_ => Some(model))
      case None => Consequence.success(None)
    }
}

private[job] def _find_children(node: JobTraceTaskNode, taskid: TaskId): Vector[JobTraceTaskNode] =
  if (node.taskId == taskid) node.children
  else node.children.flatMap(_find_children(_, taskid))
