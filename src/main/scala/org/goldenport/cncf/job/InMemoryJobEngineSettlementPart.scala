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
private[job] trait InMemoryJobEngineSettlementPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _append_task_running(
    jobid: JobId,
      taskid: TaskId,
    parent: Option[TaskId],
    startedat: Instant,
    taskdef: JobTask,
    relation: Option[String] = None,
      compensatestaskid: Option[TaskId] = None,
      admittedfromqueue: Boolean = false
  ): Unit =
    _mutate_record(jobid) { record =>
      val taskrelation = relation.orElse(taskdef.relation)
      val compensationactionref =
        if (taskrelation.contains("compensation"))
          taskdef.compensationActionRef.orElse(taskdef.operationName.filter(_.trim.nonEmpty))
        else
          taskdef.compensationActionRef
      val task = JobTaskReadModel(
        taskId = taskid,
        parentTaskId = parent,
        status = JobTaskStatus.Running,
        startedAt = startedat,
        finishedAt = None,
        result = JobTaskResultSummary(success = true, message = None),
        component = taskdef.componentName,
        service = taskdef.serviceName,
        operation = taskdef.operationName,
        taskKind = taskdef.taskKind,
        targetKind = taskdef.targetKind,
        relation = taskrelation,
        transactionRole =
          taskdef.transactionRole.orElse(_task_transaction_role(record.debug.parameters)),
        transactionScope = taskdef.transactionScope.orElse(
          record.debug.parameters.get("command.job-transaction-scope")
        ),
        transactionOutcome = Some(JobTaskTransactionOutcome.Running.print),
        compensationActionRef = compensationactionref,
        compensatesTaskId = compensatestaskid
      )
      val timeline = _next_timeline(
        record.timeline,
        "task.running",
        Some(taskid),
        parent,
        None
      )
      record.copy(
        status = JobStatus.Running,
        activeTaskCount = record.activeTaskCount + 1,
        pendingTaskCount =
          if (admittedfromqueue) math.max(0, record.pendingTaskCount - 1)
          else record.pendingTaskCount,
        taskReadModels = record.taskReadModels :+ task,
        taskDefinitions = record.taskDefinitions.updated(taskid, taskdef),
        timeline = timeline,
        updatedAt = _now()
      )
    }

  private[job] def _append_task_finished(
    jobid: JobId,
      taskid: TaskId,
    parent: Option[TaskId],
    status: JobTaskStatus,
    summary: JobTaskResultSummary,
    finishedat: Instant,
      transactionoutcome: Option[String] = None,
      compensationstatus: Option[String] = None,
      compensationfailuresummary: Option[String] = None,
      recoveryrequired: Boolean = false
  ): Unit =
    _mutate_record(jobid) { record =>
      val tasks = record.taskReadModels.map { task =>
        if (task.taskId == taskid)
          task.copy(
            status = status,
            finishedAt = Some(finishedat),
            result = summary,
            transactionOutcome = transactionoutcome.orElse(status match {
              case JobTaskStatus.Succeeded => Some(JobTaskTransactionOutcome.Committed.print)
              case JobTaskStatus.Failed => Some(JobTaskTransactionOutcome.Failed.print)
              case JobTaskStatus.Running => Some(JobTaskTransactionOutcome.Running.print)
            }),
            compensationStatus = compensationstatus.orElse(task.compensationStatus),
            compensationFailureSummary =
              compensationfailuresummary.orElse(task.compensationFailureSummary),
            recoveryRequired = task.recoveryRequired || recoveryrequired
          )
        else
          task
      }
      val timeline = _next_timeline(
        record.timeline,
        status match {
          case JobTaskStatus.Succeeded => "task.succeeded"
          case JobTaskStatus.Failed => "task.failed"
          case JobTaskStatus.Running => "task.running"
        },
        Some(taskid),
        parent,
        summary.message
      )
      record.copy(
        activeTaskCount = math.max(0, record.activeTaskCount - 1),
        taskReadModels = tasks,
        timeline = timeline,
        updatedAt = _now()
      )
    }

  private[job] def _mark_base_completion(
    jobid: JobId,
    result: Option[JobResult],
    taskbridgewrites: Boolean = true,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Boolean = {
    _mutate_record(jobid) { record =>
      record.copy(
        baseTasksCompleted = true,
        primaryResult = result.orElse(record.primaryResult),
        deferredResult = result.orElse(record.deferredResult),
        updatedAt = _now()
      )
    }
    _settle_if_ready(jobid, taskbridgewrites, retryorigin)
  }

  private[job] def _update_deferred_result(
    jobid: JobId,
    result: Option[JobResult]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        deferredResult = result.orElse(record.deferredResult),
        updatedAt = _now()
      )
    }

  private[job] def _settle_if_ready(
    jobid: JobId,
    taskbridgewrites: Boolean = true,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Boolean =
    _state_monitor.synchronized {
      var terminalcheckpointed = true
      _get_record(jobid).foreach { record =>
        if (
          record.baseTasksCompleted && record.activeTaskCount == 0 && record.pendingTaskCount == 0
        ) {
          record.deferredResult match {
            case Some(JobResult.Failure(c)) if record.status != JobStatus.Cancelled =>
              terminalcheckpointed = _handle_failed_settlement(
                jobid,
                record,
                c,
                taskbridgewrites,
                retryorigin
              )
            case Some(JobResult.Failure(_)) =>
              terminalcheckpointed = _terminal_checkpoint_succeeded(jobid, taskbridgewrites)
              if (terminalcheckpointed && _has_eligible_durable_terminal_checkpoint(jobid))
                _append_eventfor_control(jobid, JobControlCommand.Cancel, JobStatus.Cancelled)
            case Some(success @ JobResult.Success(_)) =>
              _append_timeline(jobid, "job.succeeded", None, None, None)
              _update_record(jobid, JobStatus.Succeeded, Some(success))
              terminalcheckpointed = _terminal_checkpoint_succeeded(jobid, taskbridgewrites)
              if (terminalcheckpointed)
                _append_event(
                  jobid = jobid,
                  name = "job.succeeded",
                  payload = Map(
                    "job-id" -> jobid.value,
                    "status" -> JobStatus.Succeeded.toString
                  )
                )
            case None =>
              ()
          }
          _mutate_record(jobid)(_.copy(
            baseTasksCompleted = false,
            deferredResult = None,
            updatedAt = _now()
          ))
          if (_get_record(jobid).exists(record => JobStatus.isTerminal(record.status)))
            _cancellation_scopes.remove(jobid)
        }
      }
      terminalcheckpointed
    }

  private[job] def _cancellation_scope(jobid: JobId): JobCancellationScope =
    _cancellation_scopes.computeIfAbsent(jobid, _ => new JobCancellationScope)

  private[job] def _run_compensations(
    jobid: JobId,
      failuretaskid: Option[TaskId],
      committedtasks: Vector[(TaskId, JobTask)],
    ctx: ExecutionContext
  ): Vector[(JobTask, TaskOutcome, ExecutionContext, Boolean)] =
    if (committedtasks.nonEmpty) {
      var observations = Vector.empty[(JobTask, TaskOutcome, ExecutionContext, Boolean)]
      _append_timeline(
        jobid,
        "job.compensation.started",
        failuretaskid,
        None,
        Some(committedtasks.size.toString)
      )
      committedtasks.foreach { case (originalTaskId, originalTask) =>
        originalTask.compensationTask match {
          case Some(compensation) =>
            val compensationtaskid = TaskId.create(
              "compensation",
              ctx.clock.instant(),
              ctx.idGeneration
            )
            val startedat = _now()
            val startednanos = System.nanoTime()
            val parent       = failuretaskid.orElse(Some(originalTaskId))
            _mark_task_compensation(
              jobid,
              originalTaskId,
              Some("running"),
              None,
              recoveryrequired = false
            )
            _append_timeline(
              jobid,
              "task.compensation.started",
              Some(originalTaskId),
              parent,
              originalTask.compensationActionRef
            )
            _append_task_running(
              jobid,
              compensationtaskid,
              parent,
              startedat,
              compensation,
              relation = Some("compensation"),
              compensatestaskid = Some(originalTaskId)
            )
            val jobcontext = JobContext(
              jobId = Some(jobid),
              taskId = Some(compensationtaskid),
              actionId = Some(compensation.actionId),
              parentJobId = ctx.jobContext.jobId,
              currentTask = Some(compensationtaskid),
              taskStack = ctx.jobContext.taskStack :+ compensationtaskid,
              causationId = ctx.observability.correlationId.map(_.print),
              traceMetadata = Map("traceId" -> ctx.observability.traceId.print) ++
                ctx.observability.correlationId.map(x => "correlationId" -> x.print),
              cancellationScope = Some(_cancellation_scope(jobid))
            )
            val executioncontext = _job_execution_context(jobid, ctx, jobcontext)
            val compensationoutcome = compensation.run(executioncontext)
            val compensationcancelled =
              executioncontext.jobContext.cancellationScope.exists(_.isCancelled)
            compensationoutcome match {
              case TaskSucceeded(_) =>
                _capture_calltree_if_needed(jobid, executioncontext, failed = false, startednanos)
                _append_task_finished(
                  jobid,
                  compensationtaskid,
                  parent,
                  JobTaskStatus.Succeeded,
                  JobTaskResultSummary(success = true, message = Some("compensated")),
                  _now(),
                  transactionoutcome = Some(JobTaskTransactionOutcome.CompensationCommitted.print),
                  compensationstatus = Some("succeeded")
                )
                _mark_task_compensation(
                  jobid,
                  originalTaskId,
                  Some("succeeded"),
                  None,
                  recoveryrequired = false
                )
                _append_timeline(
                  jobid,
                  "task.compensation.succeeded",
                  Some(originalTaskId),
                  parent,
                  None
                )
              case TaskFailed(c) =>
                val message = c.observation.getEffectiveMessage
                _capture_calltree_if_needed(jobid, executioncontext, failed = true, startednanos)
                _append_task_finished(
                  jobid,
                  compensationtaskid,
                  parent,
                  JobTaskStatus.Failed,
                  JobTaskResultSummary(success = false, message = message),
                  _now(),
                  transactionoutcome = Some(JobTaskTransactionOutcome.CompensationFailed.print),
                  compensationstatus = Some("failed"),
                  compensationfailuresummary = message,
                  recoveryrequired = true
                )
                _mark_task_compensation(
                  jobid,
                  originalTaskId,
                  Some("failed"),
                  message,
                  recoveryrequired = true
                )
                _mark_recovery_required(
                  jobid,
                  s"compensation failed for task ${originalTaskId.value}: ${message.getOrElse(c.show)}"
                )
                _append_timeline(
                  jobid,
                  "task.compensation.failed",
                  Some(originalTaskId),
                  parent,
                  message
                )
            }
            observations = observations :+ (
              compensation,
              compensationoutcome,
              executioncontext,
              compensationcancelled
            )
          case None =>
            val message = s"no compensation action for task ${originalTaskId.value}"
            _mark_task_compensation(
              jobid,
              originalTaskId,
              Some("missing"),
              Some(message),
              recoveryrequired = true
            )
            _mark_recovery_required(jobid, message)
            _append_timeline(
              jobid,
              "task.compensation.missing",
              Some(originalTaskId),
              failuretaskid,
              Some(message)
            )
        }
      }
      _append_timeline(jobid, "job.compensation.finished", failuretaskid, None, None)
      observations
    } else
      Vector.empty

  private[job] def _run_same_job_compensations(
    jobid: JobId,
      failuretaskid: Option[TaskId],
    ctx: ExecutionContext
  ): Vector[(JobTask, TaskOutcome, ExecutionContext, Boolean)] = {
    val committed = _committed_tasks_for_compensation(jobid, failuretaskid)
    if (committed.nonEmpty)
      _run_compensations(jobid, failuretaskid, committed, ctx)
    else
      Vector.empty
  }

  private def _committed_tasks_for_compensation(
    jobid: JobId,
      failuretaskid: Option[TaskId]
  ): Vector[(TaskId, JobTask)] =
    _get_record(jobid).toVector.flatMap { record =>
      record.taskReadModels.reverseIterator.toVector.flatMap { task =>
        val compensatable =
          task.status == JobTaskStatus.Succeeded &&
            task.relation.forall(_ != "compensation") &&
            !failuretaskid.contains(task.taskId) &&
            task.compensationStatus.isEmpty
        if (compensatable)
          record.taskDefinitions.get(task.taskId).map(task.taskId -> _)
        else
          None
      }
    }

  private def _mark_task_compensation(
    jobid: JobId,
      taskid: TaskId,
    status: Option[String],
      failuresummary: Option[String],
      recoveryrequired: Boolean
  ): Unit =
    _mutate_record(jobid) { record =>
      val tasks = record.taskReadModels.map { task =>
        if (task.taskId == taskid)
          task.copy(
            compensationStatus = status.orElse(task.compensationStatus),
            compensationFailureSummary = failuresummary.orElse(task.compensationFailureSummary),
            recoveryRequired = task.recoveryRequired || recoveryrequired
          )
        else
          task
      }
      record.copy(taskReadModels = tasks, updatedAt = _now())
    }

  private def _mark_recovery_required(
    jobid: JobId,
    message: String
  ): Unit = {
    _mutate_record(jobid) { record =>
      record.copy(
        retry = record.retry.copy(
          recoveryRequired = true,
          lastFailureMessage = Some(message)
        ),
        debug = record.debug.copy(
          executionNotes = record.debug.executionNotes :+ s"recovery-required: $message"
        ),
        updatedAt = _now()
      )
    }
    _append_timeline(jobid, "job.recovery-required", None, None, Some(message))
    _append_event(
      jobid = jobid,
      name = "job.recovery-required",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Failed.toString,
        "message" -> message
      ),
      attributes = Map("cncf.job.recoveryRequired" -> "true")
    )
  }

  private[job] def _append_timeline(
    jobid: JobId,
    kind: String,
      taskid: Option[TaskId],
    parent: Option[TaskId],
    note: Option[String]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        timeline = _next_timeline(record.timeline, kind, taskid, parent, note),
        updatedAt = _now()
      )
    }

  private[job] def _append_same_job_task_queued(
    jobid: JobId,
    taskid: TaskId,
    task: JobTask,
    parent: Option[TaskId]
  ): Unit =
    _mutate_record(jobid) { record =>
      val reopening = JobStatus.isTerminal(record.status)
      record.copy(
        status = if (reopening) JobStatus.Running else record.status,
        result = if (reopening) None else record.result,
        deferredResult =
          if (reopening) record.primaryResult.orElse(record.result).orElse(record.deferredResult)
          else record.deferredResult,
        baseTasksCompleted = record.baseTasksCompleted || reopening,
        pendingTaskCount = record.pendingTaskCount + 1,
        timeline = _next_timeline(
          record.timeline,
          "job.same-job-async.queued",
          Some(taskid),
          parent,
          Some(task.operationName.getOrElse(task.actionId.print))
        ),
        updatedAt = _now()
      )
    }

  private def _next_timeline(
    current: Vector[JobTimelineEvent],
    kind: String,
      taskid: Option[TaskId],
    parent: Option[TaskId],
    note: Option[String]
  ): Vector[JobTimelineEvent] = {
    val seq = current.lastOption.map(_.sequence + 1).getOrElse(1L)
    current :+ JobTimelineEvent(
      sequence = seq,
      occurredAt = _now(),
      kind = kind,
      taskId = taskid,
      parentTaskId = parent,
      note = note
    )
  }

}
