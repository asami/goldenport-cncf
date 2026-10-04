package org.goldenport.cncf.job

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Sep. 28, 2026
 * @version Oct. 3, 2026
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
      case RetryPolicy.Immediate(nextattempt, maxattempts) =>
        _reserve_lifetime_retry(jobid, record.tasks) { _reserve_pending(jobid, root = true) } match {
          case Consequence.Failure(_) =>
            _finish_retry_exhaustion(jobid, record, conclusion, JobRetryKind.Immediate,
              record.retry.attemptCount, maxattempts, taskbridgewrites)
          case Consequence.Success(lease) =>
            try {
            _append_timeline(jobid, "job.retry.immediate.submitted", None, None,
              Some(s"$nextattempt/$maxattempts"))
            _update_record_for_retry(jobid, record, JobRetryKind.Immediate, nextattempt, None, conclusion)
            _append_retry_event(jobid, "job.retry.immediate.submitted", conclusion,
              record.retry.copy(kind = JobRetryKind.Immediate, attemptCount = nextattempt,
                maxAttempts = maxattempts, lastFailureUserAction = _user_action_name(conclusion),
                lastFailureMessage = conclusion.observation.getEffectiveMessage))
            _append_timeline(jobid, "job.async.queued", None, None, Some("retry-now"))
            _enqueue_work(SchedulerWorkItem.RetryRun(_next_sequence(), record.priority, jobid, retryorigin), lease)
            true
            } catch {
              case e: Throwable =>
                _resource_budget.release(lease)
                throw e
            }
        }
      case RetryPolicy.Delayed(nextattempt, delay, maxattempts) =>
        _reserve_lifetime_retry(jobid, record.tasks) { _reserve_pending(jobid, root = true) } match {
          case Consequence.Failure(_) =>
            _finish_retry_exhaustion(jobid, record, conclusion, JobRetryKind.Delayed,
              record.retry.attemptCount, maxattempts, taskbridgewrites)
          case Consequence.Success(lease) =>
            try {
            val dueat = _now().plus(delay)
            _append_timeline(jobid, "job.retry.delayed.scheduled", None, None,
              Some(s"$nextattempt/$maxattempts @ ${dueat.toString}"))
            _update_record_for_retry(jobid, record, JobRetryKind.Delayed, nextattempt, Some(dueat), conclusion)
            val scheduled = record.retry.copy(kind = JobRetryKind.Delayed, attemptCount = nextattempt,
              maxAttempts = maxattempts, nextRetryDueAt = Some(dueat),
              lastFailureUserAction = _user_action_name(conclusion),
              lastFailureMessage = conclusion.observation.getEffectiveMessage)
            _append_retry_event(jobid, "job.retry.delayed.scheduled", conclusion, scheduled)
            if (_schedule_delayed_retry(jobid, dueat, lease, retryorigin)) true
            else _finish_retry_exhaustion(jobid, record, conclusion, JobRetryKind.Delayed,
              record.retry.attemptCount, maxattempts, taskbridgewrites)
            } catch {
              case _: InterruptedException =>
                Thread.currentThread().interrupt()
                _resource_budget.release(lease)
                _finish_retry_exhaustion(jobid, record, conclusion, JobRetryKind.Delayed,
                  record.retry.attemptCount, maxattempts, taskbridgewrites)
              case NonFatal(_) =>
                _resource_budget.release(lease)
                _finish_retry_exhaustion(jobid, record, conclusion, JobRetryKind.Delayed,
                  record.retry.attemptCount, maxattempts, taskbridgewrites)
            }
        }
      case RetryPolicy.Exhausted(kind, attempts, maxattempts) =>
        _finish_retry_exhaustion(jobid, record, conclusion, kind, attempts, maxattempts, taskbridgewrites)
    }

  private[job] def _finish_retry_exhaustion(
    jobid: JobId,
    record: JobRecord,
    conclusion: Conclusion,
    kind: JobRetryKind,
    attempts: Int,
    maxattempts: Int,
    taskbridgewrites: Boolean
  ): Boolean = {
    val retry = _terminal_retry_state(record.retry.copy(
      kind = kind, attemptCount = attempts, maxAttempts = maxattempts
    ), conclusion, poison = false)
    _append_timeline(jobid, "job.retry.exhausted", None, None, Some(s"$attempts/$maxattempts"))
    _append_timeline(jobid, "job.dead-letter", None, None, conclusion.observation.getEffectiveMessage)
    _append_timeline(jobid, "job.failed", None, None, conclusion.observation.getEffectiveMessage)
    _update_record_with_retry(jobid, JobStatus.Failed, Some(JobResult.Failure(conclusion)), retry)
    val checkpointed = _terminal_checkpoint_succeeded(jobid, taskbridgewrites)
    if (checkpointed) _append_failure_event(jobid, conclusion, retry)
    checkpointed
  }

  private def _update_record_for_retry(
    jobid: JobId,
    record: JobRecord,
    kind: JobRetryKind,
      attemptcount: Int,
      nextdueat: Option[Instant],
    conclusion: Conclusion
  ): Unit =
    _mutate_record(jobid) { current =>
      current.copy(
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
    }

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
        if (retrycount >= 0 && retrycount < max)
          RetryPolicy.Immediate(retrycount + 1, max)
        else
          RetryPolicy.Exhausted(JobRetryKind.Immediate, retrycount, max)
      case Some(Disposition.UserAction.RetryLater) =>
        if (retrycount >= 0 && retrycount < max && retrycount < retrySchedule.delayedRetryDelays.size) {
          val delay = retrySchedule.delayedRetryDelays(retrycount)
          if (delay != null && !delay.isZero && !delay.isNegative &&
              delay.compareTo(java.time.Duration.ofHours(24)) <= 0)
            RetryPolicy.Delayed(retrycount + 1, delay, max)
          else RetryPolicy.Exhausted(JobRetryKind.Delayed, retrycount, max)
        } else RetryPolicy.Exhausted(JobRetryKind.Delayed, retrycount, max)
      case _ =>
        RetryPolicy.None
    }
  }

  private def _user_action_name(conclusion: Conclusion): Option[String] =
    conclusion.disposition.userAction.map(_.name)

  private[job] def _schedule_delayed_retry(
    jobid: JobId,
    dueat: Instant,
    lease: JobResourceBudget.Lease,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Boolean =
    _register_resource_timer(jobid, dueat, lease) {
      _run_scheduled_retry(jobid, lease, retryorigin)
    }

  private[job] def _run_scheduled_retry(
    jobid: JobId,
    lease: JobResourceBudget.Lease,
    retryorigin: SchedulerWorkItem.RetryRunOrigin = SchedulerWorkItem.RetryRunOrigin.Automatic
  ): Unit = _state_monitor.synchronized {
    if (!_can_run_next_task(jobid)) _resource_budget.release(lease)
    else if (_resource_budget.consumeGeneration(lease)) {
      _get_record(jobid) match {
        case Some(record) if record.status == JobStatus.Submitted &&
            record.retry.kind == JobRetryKind.Delayed &&
            record.retry.nextRetryDueAt.exists(!_.isAfter(_now())) =>
          _append_timeline(jobid, "job.retry.delayed.submitted", None, None,
            Some(s"${record.retry.attemptCount}/${record.retry.maxAttempts}"))
          _append_timeline(jobid, "job.retry.delayed.enqueued", None, None,
            Some(s"${record.retry.attemptCount}/${record.retry.maxAttempts}"))
          _append_event(jobid = jobid, name = "job.retry.delayed.submitted",
            payload = Map("job-id" -> jobid.value, "status" -> JobStatus.Submitted.toString),
            attributes = _retry_event_attributes(record.retry.copy(nextRetryDueAt = None)))
          _mutate_record(jobid)(current => current.copy(
            retry = current.retry.copy(nextRetryDueAt = None), updatedAt = _now()))
          _enqueue_work(SchedulerWorkItem.RetryRun(_next_sequence(), record.priority, jobid, retryorigin), lease)
        case _ => _resource_budget.release(lease)
      }
    }
  }

  private[job] def _schedule_delayed_start(
    jobid: JobId,
    dueat: Instant,
    lease: JobResourceBudget.Lease
  ): Boolean =
    _register_resource_timer(jobid, dueat, lease) {
      _run_scheduled_start(jobid, lease)
    }

  private[job] def _run_scheduled_start(jobid: JobId, lease: JobResourceBudget.Lease): Unit =
    _state_monitor.synchronized {
      if (!_can_run_next_task(jobid)) _resource_budget.release(lease)
      else if (_resource_budget.consumeGeneration(lease)) {
        _get_record(jobid) match {
          case Some(record) if record.status == JobStatus.Submitted &&
              record.retry.kind == JobRetryKind.None &&
              record.scheduledStartAt.exists(!_.isAfter(_now())) =>
            _append_timeline(jobid, "job.delayed.enqueued", None, None, record.scheduledStartAt.map(_.toString))
            _append_timeline(jobid, "job.async.queued", None, None, Some("delayed-start"))
            _append_event(jobid = jobid, name = "job.delayed.enqueued",
              payload = Map("job-id" -> jobid.value, "status" -> JobStatus.Submitted.toString,
                "scheduled-start-at" -> record.scheduledStartAt.map(_.toString).getOrElse("")))
            _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), record.priority, jobid), lease)
          case _ => _resource_budget.release(lease)
        }
      }
    }

  private[job] def _register_durable_terminal_fact(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    projection: DurableJobTerminalProjection,
    store: DurableJobStore,
    policy: DurableJobLifetimeQuotaPolicy,
    context: ExecutionContext
  ): DurableJobTerminalProjectionFact = _state_monitor.synchronized {
    val jobid = projection.queryReadModel.jobId
    val staged = DurableJobLifetimeRecovery.footprint(snapshot, _resource_budget.policy,
      runtimeState._lifetime_budget.policy).flatMap { footprint =>
      if (candidate.jobId != jobid.value || snapshot.record.body.identity.jobId != jobid.value ||
          projection.snapshot != snapshot || projection.decision != decision || _admission_closed ||
          _get_record(jobid).nonEmpty || _durable_terminal_facts.containsKey(jobid) ||
          runtimeState._native_lifetime_bindings.contains(jobid) ||
          runtimeState._native_lifetime_bindings.size >= _resource_budget.policy.maxretainedjobs ||
          runtimeState._lifetime_budget.snapshot(jobid).nonEmpty) JobResourcePolicy.refused
      else DurableJobLifetimeRecovery.prepare(store, snapshot, candidate.access, context, policy,
        footprint, None)
    }
    staged.flatMap { prepared =>
      _resource_budget.reserve(_resource_owner, retained = true, jobid = Some(jobid)).map(_ -> prepared)
    } match {
      case Consequence.Success((lease, prepared)) =>
        runtimeState._native_lifetime_bindings.put(jobid,
          new JobDurableLifetimeRuntimeBinding(prepared.native, context, None, new JobCancellationScope))
        runtimeState._lifetime_budget.commitSeeds(Map(jobid -> prepared.quota.seed))
        _resource_budget.commit(lease, jobid)
        _durable_terminal_facts.put(jobid, projection)
        _resource_budget.release(lease)
        DurableJobTerminalProjectionFact.Registered
      case _ => DurableJobTerminalProjectionFact.Refused
    }
  }

  private[job] def _register_durable_runtime_rehydration(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    rehydration: DurableJobRuntimeRehydration,
    store: DurableJobStore,
    policy: DurableJobLifetimeQuotaPolicy,
    context: ExecutionContext
  ): DurableJobRuntimeRehydrationFact = _state_monitor.synchronized {
    val staged = DurableJobLifetimeQuotaPolicy.protect {
      for {
        _ <- _validate_runtime_rehydration(candidate, snapshot, decision, rehydration)
        count <- JobLifetimeBootstrap.taskCount(rehydration.tasks, _resource_budget.policy)
        footprint <- DurableJobLifetimeRecovery.footprint(snapshot, _resource_budget.policy,
          runtimeState._lifetime_budget.policy)
        _ <- if (Math.multiplyExact(2L, count.toLong) <= runtimeState._lifetime_budget.policy.maxchargedtasks)
          Consequence.unit else JobResourcePolicy.refused
        _ <- if (!_admission_closed && _get_record(rehydration.jobId).isEmpty &&
            !_durable_terminal_facts.containsKey(rehydration.jobId) &&
            !runtimeState._native_lifetime_bindings.contains(rehydration.jobId) &&
            runtimeState._native_lifetime_bindings.size < _resource_budget.policy.maxretainedjobs &&
            runtimeState._lifetime_budget.snapshot(rehydration.jobId).isEmpty) Consequence.unit
          else JobResourcePolicy.refused
        bridge <- _durable_lifecycle_write_bridge.filter(value => value.ownsStore(store) &&
          !value.hasAdmittedSnapshot(rehydration.jobId) &&
          value.recoveryPolicy(runtimeState._lifetime_budget.policy) == policy)
          .map(Consequence.success).getOrElse(DurableJobLifetimeQuotaPolicy.unavailable)
        prepared <- DurableJobLifetimeRecovery.prepare(store, snapshot, candidate.access, context,
          policy, footprint, Some(count))
        record <- DurableJobLifetimeQuotaPolicy.pure {
          Consequence.success(_runtime_rehydrated_record(candidate, snapshot, decision, rehydration,
            prepared.quota.cancelclaimed))
        }
      } yield (bridge, prepared, record)
    }
    staged match {
      case Consequence.Success((bridge, prepared, record)) =>
        _resource_budget.reserve(_resource_owner, retained = true,
          pending = !prepared.quota.cancelclaimed, jobid = Some(record.id), root = true) match {
          case Consequence.Success(lease) =>
            bridge.adoptRecovery(record.id, prepared) match {
              case Consequence.Success(_) =>
                val binding = new JobDurableLifetimeRuntimeBinding(prepared.native, context,
                  Some(bridge), new JobCancellationScope)
                runtimeState._native_lifetime_bindings.put(record.id, binding)
                runtimeState._lifetime_budget.commitSeeds(Map(record.id -> prepared.quota.seed))
                try {
                  _resource_budget.commit(lease, record.id)
                  _put_record(record, preserveupdatedatonentitysyncfailure = true)
                  if (prepared.quota.cancelclaimed) {
                    binding.scope.cancel()
                    _resource_budget.release(lease)
                    _apply_safety_cancel(record.id)
                  } else _register_rehydrated_due_state(record, lease)
                  DurableJobRuntimeRehydrationFact.Registered
                } catch {
                  case NonFatal(_) =>
                    binding.refused = true
                    _resource_budget.release(lease)
                    DurableJobRuntimeRehydrationFact.Refused
                }
              case _ =>
                _resource_budget.release(lease)
                DurableJobRuntimeRehydrationFact.Refused
            }
          case _ => DurableJobRuntimeRehydrationFact.Refused
        }
      case _ => DurableJobRuntimeRehydrationFact.Refused
    }
  }

  private def _runtime_rehydrated_record(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    rehydration: DurableJobRuntimeRehydration,
    cancelled: Boolean
  ): JobRecord = {
      val _ = candidate
      val lifecycle = snapshot.record.body.lifecycle
      val definitionsnapshot = rehydration.definitionSnapshot
      val now = if (cancelled) snapshot.record.body.identity.updatedAt else _now()
      JobRecord(
        id = rehydration.jobId,
        tasks = if (cancelled) Nil else rehydration.tasks,
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
          requestSummary = if (cancelled) None else rehydration.tasks.headOption.flatMap(_.requestSummary),
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
      JobInputResourcePolicy.validate(rehydration.input)

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

  private def _register_rehydrated_due_state(record: JobRecord, lease: JobResourceBudget.Lease): Unit = {
    val scheduled = record.retry.nextRetryDueAt match {
      case Some(dueat) if dueat.isAfter(_now()) =>
        _schedule_delayed_retry(record.id, dueat, lease, SchedulerWorkItem.RetryRunOrigin.Recovery)
      case Some(_) =>
        _run_scheduled_retry(record.id, lease, SchedulerWorkItem.RetryRunOrigin.Recovery)
        true
      case None => record.scheduledStartAt match {
        case Some(scheduledat) if scheduledat.isAfter(_now()) =>
          _schedule_delayed_start(record.id, scheduledat, lease)
        case Some(_) =>
          _run_scheduled_start(record.id, lease)
          true
        case None =>
          _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), record.priority, record.id), lease)
          true
      }
    }
    if (!scheduled) _resource_scheduling_failure(record.id)
  }
}
