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
private[job] trait InMemoryJobEngineRetryRecoveryPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _handle_failed_settlement(
    jobid: JobId,
    record: JobRecord,
    conclusion: Conclusion,
    taskbridgewrites: Boolean = true,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Boolean =
    _retry_policy(conclusion, record.retry.attemptCount) match {
      case RetryPolicy.None =>
        val retry = _terminal_retry_state(record.retry, conclusion, poison = true)
        _append_timeline(
          jobid,
          "job.poison",
          None,
          None,
          conclusion.observation.getEffectiveMessage
        )
        _append_timeline(
          jobid,
          "job.failed",
          None,
          None,
          conclusion.observation.getEffectiveMessage
        )
        _update_record_with_retry(
          jobid,
          JobStatus.Failed,
          Some(JobResult.Failure(conclusion)),
          retry
        )
        val terminalcheckpointed = _terminal_checkpoint_succeeded(jobid, taskbridgewrites)
        if (terminalcheckpointed)
          _append_failure_event(jobid, conclusion, retry)
        terminalcheckpointed
      case RetryPolicy.Immediate(nextattempt, maxAttempts) =>
        _append_timeline(
          jobid,
          "job.retry.immediate.submitted",
          None,
          None,
          Some(s"$nextattempt/$maxAttempts")
        )
        _update_record_for_retry(
          jobid,
          record,
          JobRetryKind.Immediate,
          nextattempt,
          None,
          conclusion
        )
        _append_retry_event(
          jobid,
          "job.retry.immediate.submitted",
          conclusion,
          record.retry.copy(
          kind = JobRetryKind.Immediate,
          attemptCount = nextattempt,
          maxAttempts = maxAttempts,
          lastFailureUserAction = _user_action_name(conclusion),
          lastFailureMessage = conclusion.observation.getEffectiveMessage
          )
        )
        _append_timeline(jobid, "job.async.queued", None, None, Some("retry-now"))
        _enqueue_work(SchedulerWorkItem.RetryRun(
          _next_sequence(),
          record.priority,
          jobid,
          retryorigin
        ))
        true
      case RetryPolicy.Delayed(nextattempt, dueat, maxAttempts) =>
        _append_timeline(
          jobid,
          "job.retry.delayed.scheduled",
          None,
          None,
          Some(s"$nextattempt/$maxAttempts @ ${dueat.toString}")
        )
        _update_record_for_retry(
          jobid,
          record,
          JobRetryKind.Delayed,
          nextattempt,
          Some(dueat),
          conclusion
        )
        val scheduled = record.retry.copy(
          kind = JobRetryKind.Delayed,
          attemptCount = nextattempt,
          maxAttempts = maxAttempts,
          nextRetryDueAt = Some(dueat),
          lastFailureUserAction = _user_action_name(conclusion),
          lastFailureMessage = conclusion.observation.getEffectiveMessage
        )
        _append_retry_event(jobid, "job.retry.delayed.scheduled", conclusion, scheduled)
        _schedule_delayed_retry(jobid, dueat, retryorigin)
        true
      case RetryPolicy.Exhausted(kind, attempts, maxAttempts) =>
        val retry = _terminal_retry_state(
          record.retry.copy(
            kind = kind,
            attemptCount = attempts,
            maxAttempts = maxAttempts
          ),
          conclusion,
          poison = false
        )
        _append_timeline(jobid, "job.retry.exhausted", None, None, Some(s"$attempts/$maxAttempts"))
        _append_timeline(
          jobid,
          "job.dead-letter",
          None,
          None,
          conclusion.observation.getEffectiveMessage
        )
        _append_timeline(
          jobid,
          "job.failed",
          None,
          None,
          conclusion.observation.getEffectiveMessage
        )
        _update_record_with_retry(
          jobid,
          JobStatus.Failed,
          Some(JobResult.Failure(conclusion)),
          retry
        )
        val terminalcheckpointed = _terminal_checkpoint_succeeded(jobid, taskbridgewrites)
        if (terminalcheckpointed)
          _append_failure_event(jobid, conclusion, retry)
        terminalcheckpointed
    }

  private def _update_record_for_retry(
    jobid: JobId,
    record: JobRecord,
    kind: JobRetryKind,
      attemptcount: Int,
      nextdueat: Option[Instant],
    conclusion: Conclusion
  ): Unit =
    _put_record(
      record.copy(
        status = JobStatus.Submitted,
        result = None,
        deferredResult = None,
        baseTasksCompleted = false,
        retry = JobRetryState(
          kind = kind,
          attemptCount = attemptcount,
          maxAttempts = retrySchedule.maxRetries,
          nextRetryDueAt = nextdueat,
          exhausted = false,
          recoveryRequired = false,
          deadLetter = false,
          poison = false,
          lastFailureUserAction = _user_action_name(conclusion),
          lastFailureMessage = conclusion.observation.getEffectiveMessage
        ),
        updatedAt = _now()
      )
    )

  private def _update_record_with_retry(
    jobid: JobId,
    status: JobStatus,
    result: Option[JobResult],
    retry: JobRetryState
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        status = status,
        result = result.orElse(record.result),
        retry = retry,
        updatedAt = _now()
      )
    }

  private[job] def _clear_runtime_retry_state(
    retry: JobRetryState
  ): JobRetryState =
    retry.copy(
      nextRetryDueAt = None,
      exhausted = false,
      recoveryRequired = false,
      deadLetter = false,
      poison = false
    )

  private def _terminal_retry_state(
    current: JobRetryState,
    conclusion: Conclusion,
    poison: Boolean
  ): JobRetryState =
    current.copy(
      nextRetryDueAt = None,
      exhausted = !poison,
      recoveryRequired = true,
      deadLetter = !poison,
      poison = poison,
      lastFailureUserAction = _user_action_name(conclusion),
      lastFailureMessage = conclusion.observation.getEffectiveMessage
    )

  private def _append_failure_event(
    jobid: JobId,
    conclusion: Conclusion,
    retry: JobRetryState
  ): Unit =
    _append_event(
      jobid = jobid,
      name = "job.failed",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Failed.toString,
        "message" -> conclusion.show
      ),
      attributes = _retry_event_attributes(retry)
    )

  private def _append_retry_event(
    jobid: JobId,
    name: String,
    conclusion: Conclusion,
    retry: JobRetryState
  ): Unit =
    _append_event(
      jobid = jobid,
      name = name,
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Submitted.toString,
        "message" -> conclusion.show
      ),
      attributes = _retry_event_attributes(retry)
    )

  private def _retry_event_attributes(
    retry: JobRetryState
  ): Map[String, String] =
    Map(
      "cncf.job.retryKind" -> retry.kind.print,
      "cncf.job.retryAttemptCount" -> retry.attemptCount.toString,
      "cncf.job.retryMaxAttempts" -> retry.maxAttempts.toString,
      "cncf.job.retryNextDueAt" -> retry.nextRetryDueAt.map(_.toString).getOrElse(""),
      "cncf.job.retryExhausted" -> retry.exhausted.toString,
      "cncf.job.recoveryRequired" -> retry.recoveryRequired.toString,
      "cncf.job.deadLetter" -> retry.deadLetter.toString,
      "cncf.job.poison" -> retry.poison.toString,
      "cncf.job.userAction" -> retry.lastFailureUserAction.getOrElse("")
    )

  private def _retry_policy(
    conclusion: Conclusion,
    retrycount: Int
  ): RetryPolicy = {
    val max = retrySchedule.maxRetries
    conclusion.disposition.userAction match {
      case Some(Disposition.UserAction.RetryNow) =>
        if (retrycount < max)
          RetryPolicy.Immediate(retrycount + 1, max)
        else
          RetryPolicy.Exhausted(JobRetryKind.Immediate, retrycount, max)
      case Some(Disposition.UserAction.RetryLater) =>
        if (retrycount < max) {
          val nextattempt = retrycount + 1
          val dueat = _now().plusMillis(retrySchedule.delayedRetryDelays(nextattempt - 1).toMillis)
          RetryPolicy.Delayed(nextattempt, dueat, max)
        } else {
          RetryPolicy.Exhausted(JobRetryKind.Delayed, retrycount, max)
        }
      case _ =>
        RetryPolicy.None
    }
  }

  private def _user_action_name(conclusion: Conclusion): Option[String] =
    conclusion.disposition.userAction.map(_.name)

  private def _schedule_delayed_retry(
    jobid: JobId,
    dueat: Instant,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Unit =
    _timer.schedule(dueat) {
      _run_scheduled_retry(jobid, retryorigin)
    }

  private def _run_scheduled_retry(
    jobid: JobId,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Unit =
    _get_record(jobid).foreach { record =>
      if (
        record.status == JobStatus.Submitted &&
        record.retry.kind == JobRetryKind.Delayed &&
        record.retry.nextRetryDueAt.exists(!_.isAfter(_now()))
      ) {
        _append_timeline(
          jobid,
          "job.retry.delayed.submitted",
          None,
          None,
          Some(s"${record.retry.attemptCount}/${record.retry.maxAttempts}")
        )
        _append_timeline(
          jobid,
          "job.retry.delayed.enqueued",
          None,
          None,
          Some(s"${record.retry.attemptCount}/${record.retry.maxAttempts}")
        )
        _append_event(
          jobid = jobid,
          name = "job.retry.delayed.submitted",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> JobStatus.Submitted.toString
          ),
          attributes = _retry_event_attributes(record.retry.copy(nextRetryDueAt = None))
        )
        _put_record(
          record.copy(
            retry = record.retry.copy(nextRetryDueAt = None),
            updatedAt = _now()
          )
        )
        _enqueue_work(SchedulerWorkItem.RetryRun(
          _next_sequence(),
          record.priority,
          jobid,
          retryorigin
        ))
      }
    }

  private[job] def _schedule_delayed_start(
    jobid: JobId,
    dueat: Instant
  ): Unit =
    _timer.schedule(dueat) {
      _run_scheduled_start(jobid)
    }

  private def _run_scheduled_start(jobid: JobId): Unit =
    _get_record(jobid).foreach { record =>
      if (
        record.status == JobStatus.Submitted &&
        record.retry.kind == JobRetryKind.None &&
        record.scheduledStartAt.exists(!_.isAfter(_now()))
      ) {
        _append_timeline(
          jobid,
          "job.delayed.enqueued",
          None,
          None,
          record.scheduledStartAt.map(_.toString)
        )
        _append_timeline(jobid, "job.async.queued", None, None, Some("delayed-start"))
        _append_event(
          jobid = jobid,
          name = "job.delayed.enqueued",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> JobStatus.Submitted.toString,
            "scheduled-start-at" -> record.scheduledStartAt.map(_.toString).getOrElse("")
          )
        )
        _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), record.priority, jobid))
      }
    }

  private[job] def _rehydrate_delayed_retries(): Unit = {
    val records = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector
    records.foreach { record =>
      if (
        record.retry.kind == JobRetryKind.Delayed &&
        record.status == JobStatus.Submitted &&
        record.retry.nextRetryDueAt.nonEmpty
      ) {
        _schedule_delayed_retry(
          record.id,
          record.retry.nextRetryDueAt.get,
          SchedulerWorkItem.RetryRunOrigin.Recovery
        )
      }
    }
  }

  private[job] def _rehydrate_delayed_starts(): Unit = {
    val records = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector
    records.foreach { record =>
      if (
        record.status == JobStatus.Submitted &&
        record.retry.kind == JobRetryKind.None &&
        record.scheduledStartAt.nonEmpty
      ) {
        val scheduledat = record.scheduledStartAt.get
        if (scheduledat.isAfter(_now()))
          _schedule_delayed_start(record.id, scheduledat)
        else
          _run_scheduled_start(record.id)
      }
    }
  }

  private[job] def _register_durable_terminal_fact(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    projection: DurableJobTerminalProjection
  ): DurableJobTerminalProjectionFact =
    _state_monitor.synchronized {
      val jobid = projection.queryReadModel.jobId
      if (
        candidate.jobId != jobid.value ||
          snapshot.record.body.identity.jobId != jobid.value ||
          projection.snapshot != snapshot ||
          projection.decision != decision ||
          _get_record(jobid).nonEmpty ||
          _durable_terminal_facts.containsKey(jobid)
      )
        DurableJobTerminalProjectionFact.Refused
      else {
        _durable_terminal_facts.put(jobid, projection)
        DurableJobTerminalProjectionFact.Registered
      }
    }

  private[job] def _register_durable_runtime_rehydration(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    rehydration: DurableJobRuntimeRehydration
  ): DurableJobRuntimeRehydrationFact =
    _state_monitor.synchronized {
      _runtime_rehydrated_record(candidate, snapshot, decision, rehydration) match {
        case Consequence.Success(record)
            if _get_record(record.id).isEmpty && !_durable_terminal_facts.containsKey(record.id) =>
          _put_record(record, preserveupdatedatonentitysyncfailure = true)
          _register_rehydrated_due_state(record)
          DurableJobRuntimeRehydrationFact.Registered
        case _ => DurableJobRuntimeRehydrationFact.Refused
      }
    }

  private def _runtime_rehydrated_record(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    rehydration: DurableJobRuntimeRehydration
  ): Consequence[JobRecord] =
    _validate_runtime_rehydration(candidate, snapshot, decision, rehydration).map { _ =>
      val lifecycle = snapshot.record.body.lifecycle
      val definitionsnapshot = rehydration.definitionSnapshot
      val now = _now()
      JobRecord(
        id = rehydration.jobId,
        tasks = rehydration.tasks,
        submittedContext = rehydration.context,
        status = JobStatus.Submitted,
        result = None,
        persistence = JobPersistencePolicy.Persistent,
        runMode = _runtime_run_mode(lifecycle.runMode),
        priority = lifecycle.priority,
        scheduledStartAt = lifecycle.schedule.scheduledAt,
        createdAt = snapshot.record.body.identity.createdAt,
        updatedAt = snapshot.record.body.identity.updatedAt,
        taskReadModels = Vector.empty,
        timeline = Vector(
          JobTimelineEvent(
            sequence = 1L,
            occurredAt = now,
            kind = "job.runtime-rehydrated",
            taskId = None,
            parentTaskId = None,
            note = None
          )
        ),
        debug = JobDebugInfo(
          requestSummary = rehydration.tasks.headOption.flatMap(_.requestSummary),
          parameters = definitionsnapshot.map(_.toParameters).getOrElse(Map.empty),
          executionNotes = Vector("durable-runtime-rehydrated"),
          declaredProfile = definitionsnapshot.flatMap(_.profile),
          jobDefinitionSnapshot = definitionsnapshot
        ),
        input = rehydration.input.map(_sanitized_rehydrated_input),
        retry = _rehydrated_retry_state(lifecycle.retry, decision)
      )
    }

  private def _validate_runtime_rehydration(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    rehydration: DurableJobRuntimeRehydration
  ): Consequence[Unit] =
    if (rehydration == null)
      Consequence.argumentInvalid("durable runtime rehydration result is missing")
    else if (rehydration.jobId == null)
      Consequence.argumentInvalid("durable runtime rehydration result has no Job id")
    else if (rehydration.tasks == null || rehydration.tasks.isEmpty || rehydration.tasks.exists(_ == null))
      Consequence.argumentInvalid("durable runtime rehydration result has no live tasks")
    else if (rehydration.context == null)
      Consequence.argumentInvalid("durable runtime rehydration result has no execution context")
    else if (
      rehydration.input == null ||
        rehydration.input.exists(_ == null) ||
        rehydration.definitionSnapshot == null ||
        rehydration.definitionSnapshot.exists(_ == null)
    )
      Consequence.argumentInvalid("durable runtime rehydration result is malformed")
    else if (candidate.jobId != snapshot.record.body.identity.jobId)
      Consequence.stateInvalid("durable runtime rehydration candidate identity does not match the admitted record")
    else if (rehydration.jobId.value != candidate.jobId)
      Consequence.stateInvalid("durable runtime rehydration Job id does not match the admitted durable id")
    else
      Consequence.unit

  private def _runtime_run_mode(runmode: DurableRunMode): JobRunMode =
    runmode match {
      case DurableRunMode.Async => JobRunMode.Async
      case DurableRunMode.Sync => JobRunMode.Sync
    }

  private def _rehydrated_retry_state(
    retry: DurableRetryEvidence,
    decision: DurableJobRecoveryDecision
  ): JobRetryState =
    decision.outcome match {
      case DurableJobRecoveryOutcome.Retryable =>
        JobRetryState(
          kind = JobRetryKind.Delayed,
          attemptCount = retry.attempts.size,
          maxAttempts = retry.maxAttempts,
          nextRetryDueAt = retry.nextRetryAt,
          exhausted = retry.exhausted
        )
      case _ =>
        JobRetryState(
          attemptCount = retry.attempts.size,
          maxAttempts = retry.maxAttempts,
          exhausted = retry.exhausted
        )
    }

  private def _sanitized_rehydrated_input(input: JobInput): JobInput =
    input.copy(payloads = input.payloads.map(_.sanitized))

  private def _register_rehydrated_due_state(record: JobRecord): Unit =
    record.retry.nextRetryDueAt match {
      case Some(dueat) if dueat.isAfter(_now()) =>
        _schedule_delayed_retry(record.id, dueat, SchedulerWorkItem.RetryRunOrigin.Recovery)
      case Some(_) =>
        _run_scheduled_retry(record.id, SchedulerWorkItem.RetryRunOrigin.Recovery)
      case None =>
        record.scheduledStartAt match {
          case Some(scheduledat) if scheduledat.isAfter(_now()) =>
            _schedule_delayed_start(record.id, scheduledat)
          case Some(_) =>
            _run_scheduled_start(record.id)
          case None =>
            _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), record.priority, record.id))
        }
    }
}
