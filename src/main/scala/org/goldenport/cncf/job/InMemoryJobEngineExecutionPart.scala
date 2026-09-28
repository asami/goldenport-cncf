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
private[job] trait InMemoryJobEngineExecutionPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _run_job_sync(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit =
    if (_establish_durable_start_intent(jobid) && _establish_durable_running_intent(jobid))
      _run_job_body(jobid, tasks, ctx, taskbridgewrites = true)

  private def _start_scheduler_workers(): Unit =
    (0 until math.max(1, schedulerConfig.workerCount)).foreach { _ =>
      _worker_pool.submit(
        new Runnable {
          override def run(): Unit =
            _scheduler_worker_loop()
        }
      )
    }

  private def _ensure_scheduler_workers(): Unit =
    if (!_force_cancel_requested && _workers_started.compareAndSet(false, true))
      _start_scheduler_workers()

  private def _scheduler_worker_loop(): Unit =
    while (!_force_cancel_requested && !Thread.currentThread().isInterrupted)
      try {
        val work = _work_queue.take()
        _run_scheduler_work(work)
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
        case e: Throwable =>
          val _ = e
      }

  private[job] def _run_scheduler_work(work: SchedulerWorkItem): Unit =
    work match {
      case SchedulerWorkItem.JobRun(_, _, jobid) =>
        _run_job_record(jobid, Some("job-run"))
      case SchedulerWorkItem.RetryRun(_, _, jobid, origin) =>
        _run_job_record(
          jobid,
          Some("retry-run"),
          taskbridgewrites = origin == SchedulerWorkItem.RetryRunOrigin.Automatic,
          retryorigin = origin
        )
      case SchedulerWorkItem.SameJobTask(_, _, jobid, task, ctx, forcedTaskId) =>
        _run_queued_same_job_task(jobid, task, ctx, forcedTaskId, Some("same-job-task"))
    }

  private def _run_job_record(jobid: JobId): Unit =
    _run_job_record(jobid, None)

  private def _run_job_record(
    jobid: JobId,
    note: Option[String],
    taskbridgewrites: Boolean = true,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Unit =
    _get_record(jobid).foreach { record =>
      if (
        _can_run_next_task(jobid) &&
          _establish_durable_start_intent_if_required(record, note) &&
          _establish_durable_running_intent_if_required(record, note)
      ) {
        try {
          _append_timeline(jobid, "job.scheduler.started", None, None, note)
          _run_job_body(
            jobid,
            record.tasks,
            ExecutionContext.withFreshExecutionResponseCell(record.submittedContext),
            taskbridgewrites,
            retryorigin
          )
        } catch {
          case e: Throwable =>
            _handle_worker_failure(jobid, e)
        }
      }
    }

  private def _run_queued_same_job_task(
    jobid: JobId,
    task: JobTask,
    ctx: ExecutionContext,
      forcedtaskid: TaskId,
    note: Option[String]
  ): Unit =
    if (_can_run_next_task(jobid)) {
      try {
        _append_timeline(
          jobid,
          "job.scheduler.started",
          Some(forcedtaskid),
          ctx.jobContext.currentTask,
          note
        )
        val _ = _run_same_job_task(jobid, task, ctx, Some(forcedtaskid))
      } catch {
        case e: Throwable =>
          _handle_worker_failure(jobid, e)
      }
    } else if (_get_record(jobid).exists(_.status == JobStatus.Cancelled)) {
      _mutate_record(jobid) { record =>
        record.copy(
          pendingTaskCount = math.max(0, record.pendingTaskCount - 1),
          updatedAt = _now()
        )
      }
      _mark_base_completion(jobid, _control_result_for(JobStatus.Cancelled))
    }

  private def _handle_worker_failure(
    jobid: JobId,
    e: Throwable
  ): Unit = {
    val message = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getName)
    val conclusion = Consequence.stateInvalid[Nothing](
      s"job scheduler failure: $message",
      Seq(
        Descriptor.Facet.Operation("job.scheduler"),
        Descriptor.Facet.State("scheduler-worker-failure")
      ),
      previous = Some(Conclusion.fromThrowable(e))
    ).conclusion
    _append_timeline(jobid, "job.failed", None, None, Some(message))
    _update_record(jobid, JobStatus.Failed, Some(JobResult.Failure(conclusion)))
    _append_event(
      jobid = jobid,
      name = "job.failed",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Failed.toString,
        "message" -> conclusion.show
      )
    )
  }

  private[job] def _enqueue_work(work: SchedulerWorkItem): Unit = {
      if (schedulerConfig.autoStartWorkers)
        _ensure_scheduler_workers()
      _work_queue.put(work)
    }

  private[job] def _next_sequence(): Long =
    _work_sequence.incrementAndGet()

  private def _run_job_body(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext,
    taskbridgewrites: Boolean,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Unit = {
    _append_timeline(jobid, "job.running", None, None, None)
    _update_record(jobid, JobStatus.Running, None)
    _append_event(
      jobid = jobid,
      name = "job.running",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Running.toString
      )
    )
    tasks match {
      case Nil =>
        _append_timeline(jobid, "job.succeeded", None, None, Some("no task"))
        _update_record(jobid, JobStatus.Succeeded, None)
        _append_event(
          jobid = jobid,
          name = "job.succeeded",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> JobStatus.Succeeded.toString
          )
        )
        _cancellation_scopes.remove(jobid)
      case _ =>
        var previous: Option[TaskId] = None
        var failure: Option[Conclusion] = None
        var failedtaskid: Option[TaskId] = None
        var committedtasks = Vector.empty[(TaskId, JobTask)]
        var successresponse: Option[OperationResponse] = None
        var completedtasks = Vector.empty[(JobTask, TaskOutcome, ExecutionContext, Boolean)]
        var taskstartrefused = false
        var taskoutcomerefused = false
        tasks.foreach { task =>
          if (failure.isEmpty && !taskstartrefused && !taskoutcomerefused && _can_run_next_task(jobid)) {
            if (_await_if_suspended(jobid)) {
              val taskid = TaskId.create("execute", ctx.clock.instant(), ctx.idGeneration)
              val startedat = _now()
              val startednanos = System.nanoTime()
              val jobcontext = JobContext(
                jobId = Some(jobid),
                taskId = Some(taskid),
                actionId = Some(task.actionId),
                parentJobId = ctx.jobContext.jobId,
                currentTask = Some(taskid),
                taskStack = previous.toVector :+ taskid,
                causationId = ctx.observability.correlationId.map(_.print),
                traceMetadata = Map(
                  "traceId" -> ctx.observability.traceId.print
                ) ++ ctx.observability.correlationId.map(x => "correlationId" -> x.print),
                cancellationScope = Some(_cancellation_scope(jobid))
              )
              val executioncontext = _job_execution_context(jobid, ctx, jobcontext)
              _append_task_running(jobid, taskid, previous, startedat, task)
              (if (taskbridgewrites)
                 _establish_durable_task_start_intent(jobid, taskid, previous)
               else
                 Consequence.unit) match {
                case Consequence.Success(_) =>
                  val taskoutcome = task.run(executioncontext)
                  val taskcancelled =
                    executioncontext.jobContext.cancellationScope.exists(_.isCancelled)
                  taskoutcome match {
                    case TaskSucceeded(res) =>
                      _capture_calltree_if_needed(jobid, executioncontext, failed = false, startednanos)
                      _append_task_finished(
                        jobid,
                        taskid,
                        previous,
                        JobTaskStatus.Succeeded,
                        JobTaskResultSummary(success = true, message = Some("ok")),
                        _now()
                      )
                      _append_timeline(
                        jobid,
                        "task.transaction.committed",
                        Some(taskid),
                        previous,
                        None
                      )
                      (if (taskbridgewrites)
                         _establish_durable_task_outcome_checkpoint(jobid, taskid, previous)
                       else
                         Consequence.unit) match {
                        case Consequence.Success(_) =>
                          completedtasks =
                            completedtasks :+ ((task, taskoutcome, executioncontext, taskcancelled))
                          successresponse = Some(res)
                          committedtasks = committedtasks :+ (taskid -> task)
                          previous = Some(taskid)
                        case Consequence.Failure(_) =>
                          taskoutcomerefused = true
                      }
                    case TaskFailed(c) =>
                      _capture_calltree_if_needed(jobid, executioncontext, failed = true, startednanos)
                      _append_task_finished(
                        jobid,
                        taskid,
                        previous,
                        JobTaskStatus.Failed,
                        JobTaskResultSummary(
                          success = false,
                          message = c.observation.getEffectiveMessage
                        ),
                        _now()
                      )
                      _append_timeline(
                        jobid,
                        "task.transaction.failed",
                        Some(taskid),
                        previous,
                        c.observation.getEffectiveMessage
                      )
                      (if (taskbridgewrites)
                         _establish_durable_task_outcome_checkpoint(jobid, taskid, previous)
                       else
                         Consequence.unit) match {
                        case Consequence.Success(_) =>
                          completedtasks =
                            completedtasks :+ ((task, taskoutcome, executioncontext, taskcancelled))
                          failure = Some(c)
                          failedtaskid = Some(taskid)
                        case Consequence.Failure(_) =>
                          taskoutcomerefused = true
                      }
                  }
                case Consequence.Failure(_) =>
                  taskstartrefused = true
              }
            }
          }
        }
        if (!taskstartrefused && !taskoutcomerefused) {
          val compensationobservations =
            if (failure.nonEmpty)
              _run_compensations(jobid, failedtaskid, committedtasks.reverse, ctx)
            else
              Vector.empty[(JobTask, TaskOutcome, ExecutionContext, Boolean)]
          val deferred = _get_record(jobid).map(_.status) match {
            case Some(JobStatus.Cancelled) =>
              Some(JobResult.Failure(Consequence.stateInvalid[Nothing](
                "job cancelled",
                Seq(Descriptor.Facet.State("cancelled"))
              ).conclusion))
            case _ =>
              failure.map(JobResult.Failure.apply).orElse(
                successresponse.map(JobResult.Success.apply)
              )
          }
          if (_mark_base_completion(jobid, deferred, taskbridgewrites, retryorigin)) {
            completedtasks.foreach { case (task, outcome, executioncontext, taskcancelled) =>
              _observe_task_canonical_outcome(task, outcome, executioncontext, taskcancelled)
            }
            compensationobservations.foreach { case (task, outcome, executioncontext, taskcancelled) =>
              _observe_task_canonical_outcome(task, outcome, executioncontext, taskcancelled)
            }
          }
        }
    }
  }

  private[job] def _run_same_job_task(
    jobid: JobId,
    task: JobTask,
    ctx: ExecutionContext,
      forcedtaskid: Option[TaskId] = None
  ): Consequence[TaskOutcome] = {
    val taskid = forcedtaskid.getOrElse(
      TaskId.create("same-job.execute", ctx.clock.instant(), ctx.idGeneration)
    )
    val parent = ctx.jobContext.currentTask
    val startedat = _now()
    val startednanos = System.nanoTime()
    val jobcontext = JobContext(
      jobId = Some(jobid),
      taskId = Some(taskid),
      actionId = Some(task.actionId),
      parentJobId = ctx.jobContext.jobId,
      currentTask = Some(taskid),
      taskStack = ctx.jobContext.taskStack :+ taskid,
      causationId = ctx.observability.correlationId.map(_.print),
      traceMetadata = Map("traceId" -> ctx.observability.traceId.print) ++
        ctx.observability.correlationId.map(x => "correlationId" -> x.print),
      cancellationScope = Some(_cancellation_scope(jobid))
    )
    val executioncontext = _job_execution_context(jobid, ctx, jobcontext)
    _append_task_running(
      jobid,
      taskid,
      parent,
      startedat,
      task,
      admittedfromqueue = forcedtaskid.isDefined
    )
    _establish_durable_task_start_intent(jobid, taskid, parent).flatMap { _ =>
      val outcome = task.run(executioncontext)
      val taskcancelled = executioncontext.jobContext.cancellationScope.exists(_.isCancelled)
      outcome match {
        case TaskSucceeded(res) =>
          _capture_calltree_if_needed(jobid, executioncontext, failed = false, startednanos)
          _append_task_finished(
            jobid,
            taskid,
            parent,
            JobTaskStatus.Succeeded,
            JobTaskResultSummary(success = true, message = Some("ok")),
            _now()
          )
          _append_timeline(jobid, "task.transaction.committed", Some(taskid), parent, None)
        case TaskFailed(c) =>
          _capture_calltree_if_needed(jobid, executioncontext, failed = true, startednanos)
          _append_task_finished(
            jobid,
            taskid,
            parent,
            JobTaskStatus.Failed,
            JobTaskResultSummary(success = false, message = c.observation.getEffectiveMessage),
            _now()
          )
          _append_timeline(
            jobid,
            "task.transaction.failed",
            Some(taskid),
            parent,
            c.observation.getEffectiveMessage
          )
          ()
      }
      _establish_durable_task_outcome_checkpoint(jobid, taskid, parent).map { _ =>
        val compensationobservations = outcome match {
          case TaskSucceeded(_) =>
            Vector.empty[(JobTask, TaskOutcome, ExecutionContext, Boolean)]
          case TaskFailed(c) =>
            val observations = _run_same_job_compensations(jobid, Some(taskid), ctx)
            _update_deferred_result(jobid, Some(JobResult.Failure(c)))
            observations
        }
        if (_settle_if_ready(jobid)) {
          _observe_task_canonical_outcome(task, outcome, executioncontext, taskcancelled)
          compensationobservations.foreach { case (task, outcome, executioncontext, taskcancelled) =>
            _observe_task_canonical_outcome(task, outcome, executioncontext, taskcancelled)
          }
        }
        outcome
      }
    }
  }

  private def _observe_task_canonical_outcome(
    task: JobTask,
    outcome: TaskOutcome,
    executioncontext: ExecutionContext,
    cancelled: Boolean
  ): Unit =
    try
      task.observeCanonicalOutcome(outcome, executioncontext, cancelled)
    catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
      case NonFatal(_) => ()
    }

  private[job] def _observe_task_admission_failure(
    task: JobTask,
    conclusion: Conclusion,
    executioncontext: ExecutionContext
  ): Unit =
    try
      task.observeAdmissionFailure(conclusion, executioncontext)
    catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
      case NonFatal(_) => ()
    }

  private[job] def _job_execution_context(
    jobid: JobId,
    ctx: ExecutionContext,
    jobcontext: JobContext
  ): ExecutionContext = {
    val withjob = ExecutionContext.withJobContext(ctx, jobcontext)
    _get_record(jobid).map(_.persistence) match {
      case Some(JobPersistencePolicy.Persistent)
          if !withjob.observability.callTreeContext.isEnabled =>
        ExecutionContext.withFrameworkCallTreeEnabled(withjob, enabled = true)
      case _ =>
        withjob
    }
  }

  private[job] def _capture_calltree_if_needed(
    jobid: JobId,
    ctx: ExecutionContext,
    failed: Boolean,
    startednanos: Long
  ): Unit =
    _get_record(jobid).foreach { record =>
      val elapsedmillis = math.max(0L, (System.nanoTime() - startednanos) / 1000000L)
      val save = record.persistence == JobPersistencePolicy.Persistent && (
        failed ||
        ctx.framework.saveCallTree ||
        elapsedmillis >= InMemoryJobEngine.DefaultSlowCallTreeThresholdMillis
      )
      if (record.persistence != JobPersistencePolicy.Persistent) {
        _mark_calltree_not_saved(jobid, "not_persistent")
      } else if (save) {
        ctx.observability.callTreeContext.build() match {
          case Some(tree) =>
            _save_calltree(jobid, ctx.runtime.operationMode, ObservabilityEngine.callTreeRecord(tree, Some(jobid.value)))
          case None =>
            _mark_calltree_not_saved(jobid, "not_captured")
        }
      } else {
        _mark_calltree_not_saved(jobid, "not_matched_policy")
      }
    }

  private def _save_calltree(
    jobid: JobId,
    operationMode: org.goldenport.cncf.config.OperationMode,
    record: Record
  ): Unit = {
    val json = RecordEncoder.json(record)
    val bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
    val externalizer = DiagnosticPayloadExternalizer.fromGlobal(operationMode)
    val externalsummary =
      externalizer.externalizeText(
        operation = _job_operation_fqn(jobid),
        payloadKind = "calltree",
        contentType = "application/json",
        extension = "json",
        text = json,
        summary = org.goldenport.cncf.observability.DiagnosticPayloadSummary(
          kind = "calltree",
          valueType = Some("Record"),
          sizeBytes = Some(bytes)
        )
      )
    externalsummary.payloadReference match {
      case Some(ref) if externalsummary.externalizationStatus.contains("stored") =>
        _mutate_record(jobid) { current =>
          current.copy(
            debug = current.debug.copy(
              calltree = None,
              calltreeJson = None,
              calltreeClob = None,
              calltreeSaved = true,
              calltreeStorage = Some(s"external:${ref.storage.getOrElse("payload")}"),
              calltreeSerializedBytes = Some(bytes),
              calltreePayloadReference = Some(ref.toRecord),
              calltreeDropReason = None
            )
          )
        }
      case _ =>
        val useclob = bytes > InMemoryJobEngine.CallTreeVarcharThresholdBytes
        _mutate_record(jobid) { current =>
          current.copy(
            debug = current.debug.copy(
              calltree = Some(record),
              calltreeJson = if (useclob) None else Some(json),
              calltreeClob = if (useclob) Some(json) else None,
              calltreeSaved = true,
              calltreeStorage = Some(if (useclob) "calltree_clob" else "calltree"),
              calltreeSerializedBytes = Some(bytes),
              calltreePayloadReference = None,
              calltreeDropReason = None
            )
          )
        }
    }
  }

  private def _job_operation_fqn(
    jobid: JobId
  ): String =
    _get_record(jobid)
      .flatMap(_.tasks.headOption)
      .flatMap { task =>
        val parts = Vector(task.componentName, task.serviceName, task.operationName).flatten.filter(
          _.nonEmpty
        )
        Option.when(parts.nonEmpty)(parts.mkString("."))
      }
      .getOrElse("job.calltree")

  private def _mark_calltree_not_saved(
    jobid: JobId,
    reason: String
  ): Unit =
    _mutate_record(jobid) { current =>
      if (current.debug.calltreeSaved)
        current
      else
        current.copy(
          debug = current.debug.copy(
            calltreeSaved = false,
            calltreeDropReason = Some(reason)
          )
        )
    }

}
