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
private[job] trait InMemoryJobEngineQueryPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  def getStatus(jobId: JobId): Option[JobStatus] =
    _get_record(jobId)
      .map(_.status)
      .orElse(durableTerminalProjection(jobId).map(_.queryReadModel.status))

  def getResult(jobId: JobId): Option[JobResult] =
    _get_record(jobId).flatMap(_.result)

  override def getPrimaryResult(jobId: JobId): Option[JobResult] =
    _get_record(jobId).flatMap(record => record.primaryResult.orElse(record.result))

  override def awaitResult(
    jobId: JobId,
    timeoutMillis: Long
  ): Consequence[JobResult] =
    _get_record(jobId) match {
      case None => Consequence.operationNotFound(s"job:${jobId.value}")
      case Some(_) =>
        val deadline = _now().plusMillis(math.max(0L, timeoutMillis))
        _wait_until(deadline)(getResult(jobId).nonEmpty) match {
          case WaitOutcome.Completed =>
            getResult(jobId) match {
              case Some(result) => Consequence.success(result)
              case None => Consequence.stateConflict(s"job timeout: ${jobId.value}")
            }
          case WaitOutcome.TimedOut =>
            Consequence.stateConflict(s"job timeout: ${jobId.value}")
          case WaitOutcome.Interrupted =>
            Consequence.stateConflict(s"job await interrupted: ${jobId.value}")
        }
    }

  def control(
    jobId: JobId,
    request: JobControlRequest,
    policy: JobControlPolicy = JobControlPolicy.default
  )(using ctx: ExecutionContext): Consequence[JobControlResponse] =
    policy.authorize(jobId, request).flatMap { _ =>
      _control(jobId, request)
    }

  def query(jobId: JobId): Option[JobQueryReadModel] =
    _get_record(jobId)
      .map(_read_model)
      .orElse(durableTerminalProjection(jobId).map(_.queryReadModel))

  override def queryPage(
    request: JobManagementQuery,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[JobManagementPage] =
    JobManagementReader.queryPage(_management_snapshot, request, policy)

  override def listJobs(
    limit: Int = 100,
    persistentOnly: Boolean = true
  ): Vector[JobQueryReadModel] = {
    val durable = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector
    val runtime =
      if (persistentOnly) Vector.empty
      else _runtime_jobs.values().toArray(new Array[JobRecord](0)).toVector
    val terminal = durableTerminalProjections.map(_.queryReadModel)
    (durable.map(_read_model) ++ runtime.map(_read_model) ++ terminal)
      .sortBy(_.updatedAt)
      .reverse
      .take(math.max(0, limit))
  }

  private def _management_snapshot: JobManagementReader.Snapshot =
    _state_monitor.synchronized {
      val durable = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector
      val durableids = durable.map(_.id).toSet
      val runtime = _runtime_jobs.values().toArray(new Array[JobRecord](0)).toVector
        .filterNot(record => durableids.contains(record.id))
      val records = durable ++ runtime
      val live = records.map(_read_model)
      val liveids = live.map(_.jobId).toSet
      val terminalprojections = durableTerminalProjections
      val terminal = terminalprojections.map(_.queryReadModel)
        .filterNot(model => liveids.contains(model.jobId))
      JobManagementReader.Snapshot(
        models = live ++ terminal,
        results = records.flatMap(record => record.result.map(record.id -> _)).toMap,
        durableTerminalIds = terminalprojections.map(_.queryReadModel.jobId).toSet
      )
    }

  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] =
    _get_record(jobId)
      .map(_task_page(_, offset, limit))
      .orElse(durableTerminalProjection(jobId).map(projection =>
        _task_page(projection.queryReadModel.tasks.tasks, offset, limit)
      ))

  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] =
    _get_record(jobId)
      .map(_timeline_page(_, offset, limit))
      .orElse(durableTerminalProjection(jobId).map(projection =>
        _timeline_page(projection.queryReadModel.timeline.events, offset, limit)
      ))

  override def queryTaskExecutionTree(jobId: JobId): Option[JobTraceTree] =
    query(jobId).map(_.traceTree)

  override def queryTaskDetail(jobId: JobId, taskId: TaskId): Option[JobTaskDetail] =
    query(jobId).flatMap { model =>
      model.tasks.tasks.find(_.taskId == taskId).map { task =>
        JobTaskDetail(
          jobId = jobId,
          task = task,
          events = model.timeline.events.filter(_.taskId.contains(taskId)).sortBy(_.sequence),
          children = model.traceTree.roots.flatMap(_find_children(_, taskId))
        )
      }
    }

  override def queryManagementDetail(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobManagementDetail]] =
    JobManagementReader.queryDetail(_management_snapshot, jobId, policy)

  override def queryManagementResult(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobManagementResult]] =
    JobManagementReader.queryResult(_management_snapshot, jobId, policy)

  override def queryManagementTasks(
    jobId: JobId,
    offset: Int = 0,
    limit: Int = JobManagementQuery.DefaultLimit,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobTaskPage]] =
    JobManagementReader.queryTasks(_management_snapshot, jobId, offset, limit, policy)

  override def queryManagementTimeline(
    jobId: JobId,
    offset: Int = 0,
    limit: Int = JobManagementQuery.DefaultLimit,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobTimelinePage]] =
    JobManagementReader.queryTimeline(_management_snapshot, jobId, offset, limit, policy)

  override def queryManagementTaskExecutionTree(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobTraceTree]] =
    JobManagementReader.queryTaskExecutionTree(_management_snapshot, jobId, policy)

  override def queryManagementTaskDetail(
    jobId: JobId,
    taskId: TaskId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ctx: ExecutionContext): Consequence[Option[JobTaskDetail]] =
    JobManagementReader.queryTaskDetail(_management_snapshot, jobId, taskId, policy)

  override def metrics: Option[JobMetrics] = {
    val records = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector ++
      _runtime_jobs.values().toArray(new Array[JobRecord](0)).toVector
    val running = records.count(_.status == JobStatus.Running)
    val queued = records.count(_.status == JobStatus.Submitted)
    val completed = records.count(_.status == JobStatus.Succeeded)
    val failed = records.count(_.status == JobStatus.Failed)
    Some(JobMetrics(running = running, queued = queued, completed = completed, failed = failed))
  }

  override def annotateJob(
    jobId: JobId,
    parameters: Map[String, String],
    executionNotes: Vector[String] = Vector.empty
  ): Unit =
    _mutate_record(jobId) { record =>
      record.copy(
        debug = record.debug.copy(
          parameters = record.debug.parameters ++ parameters,
          executionNotes = record.debug.executionNotes ++ executionNotes
        ),
        updatedAt = _now()
      )
    }

  override def cleanupExpiredInputs(now: Instant): Int = {
    val ids = (_durable_jobs.keySet().toArray(new Array[JobId](0)).toVector ++
      _runtime_jobs.keySet().toArray(new Array[JobId](0)).toVector).distinct
    ids.count { id =>
      _get_record(id).exists { record =>
        record.input.exists(_.shouldCleanup(record.status, now)) && {
          _mutate_record(id)(r => r.copy(input = r.input.map(_.cleaned(now)), updatedAt = now))
          true
        }
      }
    }
  }

  override def annotateJobProfile(
    jobId: JobId,
    profile: JobDeclaredProfile
  ): Unit =
    _mutate_record(jobId) { record =>
      record.copy(
        debug = record.debug.copy(
          declaredProfile = Some(profile),
          executionNotes =
            if (record.debug.executionNotes.contains("jcl declared profile attached"))
              record.debug.executionNotes
            else
              record.debug.executionNotes :+ "jcl declared profile attached"
        ),
        updatedAt = _now()
      )
    }

  override def runTaskInJobSync(
    jobId: JobId,
    task: JobTask,
    ctx: ExecutionContext
  ): Consequence[TaskOutcome] =
    _get_record(jobId) match {
      case Some(_) if _can_run_next_task(jobId) =>
        _run_same_job_task(jobId, task, ctx).flatMap { outcome =>
          outcome.result.map(_ => outcome)
        }
      case Some(_) =>
        Consequence.stateInvalid(s"job task admission blocked: ${jobId.value}")
      case None =>
        Consequence.operationNotFound(s"job:${jobId.value}")
    }

  override def enqueueTaskInJob(
    jobId: JobId,
    task: JobTask,
    ctx: ExecutionContext
  ): Consequence[TaskId] =
    _get_record(jobId) match {
      case Some(_) if _can_run_next_task(jobId) =>
        val taskid = TaskId.create("same-job.enqueue", ctx.clock.instant(), ctx.idGeneration)
        _append_same_job_task_queued(jobId, taskid, task, ctx.jobContext.currentTask)
        val priority = _get_record(jobId).map(_.priority).getOrElse(0)
        _enqueue_work(SchedulerWorkItem.SameJobTask(
          _next_sequence(),
          priority,
          jobId,
          task,
          ctx,
          taskid
        ))
        Consequence.success(taskid)
      case Some(_) =>
        Consequence.stateInvalid(s"job task admission blocked: ${jobId.value}")
      case None =>
        Consequence.operationNotFound(s"job:${jobId.value}")
    }

}
