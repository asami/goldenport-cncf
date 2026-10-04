package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.consequence.Failures
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.protocol.operation.OperationResponse

/*
 * @since   Sep. 28, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineControlPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*
  import JobResourceBudget.WaitLease

  private[job] def _control(
    jobid: JobId,
    request: JobControlRequest
  ): Consequence[JobControlResponse] =
    if (request == null || request.command == null || request.option == null ||
        request.option.mode == null || request.option.timeoutMillis < 0L ||
        request.option.timeoutMillis > 86400000L)
      Consequence.argumentInvalid("JobEngine wait request refused")
    else request.option.mode match {
      case JobCommandMode.Async => _control_admitted(jobid, request, None)
      case JobCommandMode.Sync =>
        _wait_deadline(request.option.timeoutMillis).flatMap { deadline =>
          _with_wait_capacity(lease => _control_admitted(jobid, request, Some(deadline -> lease)))
        }
    }

  private def _control_admitted(
    jobid: JobId,
    request: JobControlRequest,
    wait: Option[(Instant, WaitLease)]
  ): Consequence[JobControlResponse] = {
    val transition = _state_monitor.synchronized {
      if (request.command == JobControlCommand.Retry && _admission_closed)
        JobResourcePolicy.refused[ControlTransition]
      else _get_record(jobid) match {
        case None =>
          _control_failure(s"job not found: ${jobid.value}")
        case Some(_) if _has_durable_task_checkpoint_refusal(jobid) =>
          _control_failure(s"job control blocked by durable task checkpoint refusal: ${jobid.value}")
        case Some(record) =>
          _control_transition_for(request.command, record.status).flatMap {
            case applied @ ControlTransition.Apply(target) =>
              request.command match {
                case JobControlCommand.Retry =>
                  _retry_job(jobid, record).map(_ => applied)
                case _ =>
                  _pay_lifetime_control(jobid, request.command).map { _ =>
                  if (request.command == JobControlCommand.Cancel)
                    _apply_safety_cancel(jobid)
                  else {
                    _append_timelinefor_control(jobid, request.command, target)
                    _update_record(jobid, target, _control_result_for(target))
                    _append_eventfor_control(jobid, request.command, target)
                  }
                  applied
                  }
              }
            case replay => Consequence.success(replay)
          }
      }
    }
    transition.flatMap {
      case ControlTransition.Apply(target) =>
        _control_response(jobid, request, target, changed = true, wait = wait)
      case ControlTransition.Replay(status) =>
        _control_replay_response(jobid, request, status)
    }
  }

  private[job] def _apply_safety_cancel(jobid: JobId): Unit =
    _state_monitor.synchronized {
      _get_record(jobid).foreach { record =>
        record.status match {
          case JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended =>
            _remove_pending_resources(Some(jobid))
            _append_timelinefor_control(jobid, JobControlCommand.Cancel, JobStatus.Cancelled)
            _update_record(jobid, JobStatus.Cancelled, _control_result_for(JobStatus.Cancelled))
            _cancellation_scope(jobid).cancel()
            if (_has_eligible_durable_terminal_checkpoint(jobid) &&
                _has_no_active_or_pending_local_work(jobid))
              _mark_base_completion(jobid, _control_result_for(JobStatus.Cancelled))
            if (!_has_eligible_durable_terminal_checkpoint(jobid))
              _append_eventfor_control(jobid, JobControlCommand.Cancel, JobStatus.Cancelled)
          case JobStatus.Cancelled =>
            _remove_pending_resources(Some(jobid))
            _cancellation_scope(jobid).cancel()
          case _ => ()
        }
      }
    }

  private def _control_response(
    jobid: JobId,
    request: JobControlRequest,
    target: JobStatus,
    changed: Boolean,
    wait: Option[(Instant, WaitLease)]
  ): Consequence[JobControlResponse] =
    request.option.mode match {
      case JobCommandMode.Async =>
        Consequence.success(
          JobControlResponse(
            jobId = jobid,
            status = target,
            response = None,
            async = true,
            changed = changed
          )
        )
      case JobCommandMode.Sync =>
        val (deadline, lease) = wait.get
        _await_control_sync(jobid, request, target, changed, deadline, lease)
    }

  private def _control_replay_response(
    jobid: JobId,
    request: JobControlRequest,
    status: JobStatus
  ): Consequence[JobControlResponse] =
    request.option.mode match {
      case JobCommandMode.Async =>
        Consequence.success(
          JobControlResponse(
            jobId = jobid,
            status = status,
            response = None,
            async = true,
            changed = false
          )
        )
      case JobCommandMode.Sync =>
        val response = request.command match {
          case JobControlCommand.Retry => getResponse(jobid)
          case _ => Some(OperationResponse.Scalar(status.toString))
        }
        Consequence.success(
          JobControlResponse(
            jobId = jobid,
            status = status,
            response = response,
            async = false,
            changed = false
          )
        )
    }

  private def _retry_job(jobid: JobId, record: JobRecord): Consequence[Unit] =
    _reserve_lifetime_retry(jobid, record.tasks) { _reserve_pending(jobid, root = true) }.map { lease =>
    try {
    val now = _now()
    _put_record(
      record.copy(
        status = JobStatus.Submitted,
        result = None,
        retry = _clear_runtime_retry_state(record.retry),
        scheduledStartAt = None,
        updatedAt = now
      )
    )
    _append_timeline(jobid, "job.retry.submitted", None, None, None)
    _append_event(
      jobid = jobid,
      name = "job.retry.submitted",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Submitted.toString
      )
    )
    _append_timeline(jobid, "job.async.queued", None, None, Some("retry"))
    _enqueue_work(SchedulerWorkItem.RetryRun(
      _next_sequence(),
      record.priority,
      jobid,
      SchedulerWorkItem.RetryRunOrigin.Control
    ), lease)
    ()
    } catch {
      case e: Throwable =>
        _resource_budget.release(lease)
        throw e
    }
  }

  private def _append_timelinefor_control(
    jobid: JobId,
    command: JobControlCommand,
    status: JobStatus
  ): Unit =
    command match {
      case JobControlCommand.Cancel =>
        _append_timeline(jobid, "job.cancelled", None, None, Some(status.toString))
      case JobControlCommand.Suspend =>
        _append_timeline(jobid, "job.suspended", None, None, Some(status.toString))
      case JobControlCommand.Resume =>
        _append_timeline(jobid, "job.resumed", None, None, Some(status.toString))
      case JobControlCommand.Retry =>
        ()
    }

  private[job] def _append_eventfor_control(
    jobid: JobId,
    command: JobControlCommand,
    status: JobStatus
  ): Unit =
    command match {
      case JobControlCommand.Cancel =>
        _append_event(
          jobid = jobid,
          name = "job.cancelled",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Suspend =>
        _append_event(
          jobid = jobid,
          name = "job.suspended",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Resume =>
        _append_event(
          jobid = jobid,
          name = "job.resumed",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Retry =>
        ()
    }

  private[job] def _control_result_for(status: JobStatus): Option[JobResult] =
    status match {
      case JobStatus.Cancelled => Some(JobResult.Failure(Consequence.stateInvalid[Nothing](
        "job cancelled",
        Seq(Descriptor.Facet.State("cancelled"))
      ).conclusion))
      case _ => None
    }

  private def _await_control_sync(
    jobid: JobId,
    request: JobControlRequest,
    target: JobStatus,
    changed: Boolean,
    deadline: Instant,
    lease: WaitLease
  ): Consequence[JobControlResponse] = {
    var status = getStatus(jobid).getOrElse(target)
    val waitoutcome =
      if (request.command == JobControlCommand.Retry && !_is_retry_settled(status))
        _wait_until(deadline, lease) {
          status = getStatus(jobid).getOrElse(status)
          _is_retry_settled(status)
        }
      else
        Consequence.success(WaitOutcome.Completed)
    waitoutcome.flatMap { outcome =>
    status = getStatus(jobid).getOrElse(status)
    if (outcome == WaitOutcome.TimedOut && !_is_retry_settled(status)) {
      _control_timeout(s"sync timeout for job control: ${jobid.value}")
    } else if (outcome == WaitOutcome.Interrupted) {
      Consequence.stateConflict(s"job control await interrupted: ${jobid.value}")
    } else {
      val response = request.command match {
        case JobControlCommand.Retry =>
          getResponse(jobid)
        case _ =>
          Some(OperationResponse.Scalar(status.toString))
      }
      Consequence.success(
        JobControlResponse(
          jobId = jobid,
          status = status,
          response = response,
          async = false,
          changed = changed
        )
      )
    }
    }
  }

  private def _is_retry_settled(status: JobStatus): Boolean =
    status match {
      case JobStatus.Succeeded | JobStatus.Failed | JobStatus.Cancelled => true
      case _ => false
    }

  private enum ControlTransition {
    case Apply(status: JobStatus)
    case Replay(status: JobStatus)
  }

  private def _control_transition_for(
    command: JobControlCommand,
    status: JobStatus
  ): Consequence[ControlTransition] =
    (command, status) match {
      case (
            JobControlCommand.Cancel,
            JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended
          ) =>
        Consequence.success(ControlTransition.Apply(JobStatus.Cancelled))
      case (JobControlCommand.Cancel, JobStatus.Cancelled) =>
        Consequence.success(ControlTransition.Replay(JobStatus.Cancelled))
      case (JobControlCommand.Suspend, JobStatus.Submitted | JobStatus.Running) =>
        Consequence.success(ControlTransition.Apply(JobStatus.Suspended))
      case (JobControlCommand.Suspend, JobStatus.Suspended) =>
        Consequence.success(ControlTransition.Replay(JobStatus.Suspended))
      case (JobControlCommand.Resume, JobStatus.Suspended) =>
        Consequence.success(ControlTransition.Apply(JobStatus.Running))
      case (JobControlCommand.Resume, JobStatus.Running) =>
        Consequence.success(ControlTransition.Replay(JobStatus.Running))
      case (JobControlCommand.Retry, JobStatus.Failed | JobStatus.Cancelled) =>
        Consequence.success(ControlTransition.Apply(JobStatus.Submitted))
      case (JobControlCommand.Retry, JobStatus.Submitted | JobStatus.Running) =>
        Consequence.success(ControlTransition.Replay(status))
      case _ =>
        _control_invalid_transition(command, status)
    }

  private[job] def _can_run_next_task(jobid: JobId): Boolean =
    _state_monitor.synchronized {
      _get_record(jobid).exists(record =>
        record.status != JobStatus.Cancelled && !_has_durable_task_checkpoint_refusal(jobid) &&
          _native_dispatch_allowed(jobid)
      )
    }

  private[job] def _has_eligible_durable_terminal_checkpoint(jobid: JobId): Boolean =
    _get_record(jobid).exists { record =>
      record.persistence == JobPersistencePolicy.Persistent &&
        _lifecycle_bridge(record.id).exists(bridge => bridge.hasAdmittedSnapshot(record.id) &&
          (record.taskReadModels.nonEmpty || bridge.recoveredCancelled(record)))
    }

  private def _has_no_active_or_pending_local_work(jobid: JobId): Boolean =
    _get_record(jobid).exists { record =>
      record.activeTaskCount == 0 && record.pendingTaskCount == 0
    }

  private[job] def _has_durable_task_checkpoint_refusal(jobid: JobId): Boolean =
    _lifecycle_failure(jobid).exists {
      case DurableJobLifecycleWriteFailure.TaskStartIntentRefused => true
      case DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused => true
      case DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused => true
      case _ => false
    }

  private[job] def _await_if_suspended(jobid: JobId): Boolean = {
    _state_monitor.synchronized {
      var status = _get_record(jobid).map(_.status)
      while (!_force_cancel_requested && status.contains(JobStatus.Suspended)) {
        try
          _state_monitor.wait()
        catch {
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
            return false
        }
        status = _get_record(jobid).map(_.status)
      }
    }
    val status = _get_record(jobid).map(_.status)
    status.forall(_ != JobStatus.Cancelled)
  }

  private[job] def _wait_deadline(timeoutmillis: Long): Consequence[Instant] =
    if (timeoutmillis < 0L || timeoutmillis > 86400000L)
      Consequence.argumentInvalid("JobEngine wait request refused")
    else try Consequence.success(_now().plusMillis(timeoutmillis))
    catch {
      case NonFatal(_) => Consequence.argumentInvalid("JobEngine wait request refused")
    }

  private[job] def _with_wait_capacity[A](body: WaitLease => Consequence[A]): Consequence[A] = {
    val admission = _state_monitor.synchronized {
      if (_admission_closed) JobResourcePolicy.refused[WaitLease]
      else _resource_budget.reserveWait(_resource_owner)
    }
    admission.flatMap { lease =>
      try body(lease)
      finally _resource_budget.releaseWait(lease)
    }
  }

  private def _wait_remaining(deadline: Instant): Consequence[Long] =
    try {
      val remaining = Duration.between(_now(), deadline)
      if (remaining.compareTo(Duration.ofMillis(86400000L)) > 0)
        JobResourcePolicy.refused
      else if (remaining.isNegative || remaining.isZero)
        Consequence.success(0L)
      else Consequence.success(remaining.toNanos)
    } catch {
      case NonFatal(_) => JobResourcePolicy.refused
    }

  private[job] def _wait_until(
    deadline: Instant,
    lease: WaitLease
  )(done: => Boolean): Consequence[WaitOutcome] =
    if (done) Consequence.success(WaitOutcome.Completed)
    else _wait_remaining(deadline).flatMap { remaining =>
      if (remaining == 0L) Consequence.success(WaitOutcome.TimedOut)
      else {
        // External schedule/close may have taken effect without an acknowledgement.
        _resource_budget.markWaitAmbiguous(lease)
        val scheduled = try {
          val registration = _timer.schedule(deadline) { _signal_state_change() }
          if (registration == null) JobResourcePolicy.refused[JobTimerRegistration]
          else {
            _resource_budget.acknowledgeWait(lease)
            Consequence.success(registration)
          }
        } catch {
          case NonFatal(_) => JobResourcePolicy.refused[JobTimerRegistration]
        }
        scheduled.flatMap { registration =>
          var closefailed = false
          val outcome = try {
            _state_monitor.synchronized {
              var outcome = Option.empty[Consequence[WaitOutcome]]
              try {
                while (outcome.isEmpty) {
                  if (done) outcome = Some(Consequence.success(WaitOutcome.Completed))
                  else if (_force_cancel_requested) outcome = Some(Consequence.success(WaitOutcome.TimedOut))
                  else _wait_remaining(deadline) match {
                    case Consequence.Failure(conclusion) => outcome = Some(Consequence.Failure(conclusion))
                    case Consequence.Success(nanos) =>
                      if (nanos == 0L) outcome = Some(Consequence.success(WaitOutcome.TimedOut))
                      else _state_monitor.wait(nanos / 1000000L, (nanos % 1000000L).toInt)
                  }
                }
              } catch {
                case _: InterruptedException =>
                  Thread.currentThread().interrupt()
                  outcome = Some(Consequence.success(WaitOutcome.Interrupted))
              }
              outcome.get
            }
          } finally {
            _resource_budget.markWaitAmbiguous(lease)
            try {
              registration.close()
              _resource_budget.acknowledgeWait(lease)
            } catch {
              case NonFatal(_) => closefailed = true
            }
          }
          if (closefailed) JobResourcePolicy.refused else outcome
        }
      }
    }

  private[job] def _signal_state_change(): Unit =
    _state_monitor.synchronized {
      _state_monitor.notifyAll()
    }

  private def _control_invalid_transition[A](
    command: JobControlCommand,
    status: JobStatus
  ): Consequence[A] =
    Consequence.operationInvalid(
      "job.control",
      Cause.Kind.Guard,
      Seq(
        Descriptor.Facet.Operation(s"job.control.${command.toString.toLowerCase}"),
        Descriptor.Facet.State(status.toString.toLowerCase),
        Descriptor.Facet.Message(s"invalid transition: status=${status.toString.toLowerCase}")
      )
    )

  private def _control_failure[A](message: String): Consequence[A] =
    Consequence.operationInvalid(
      "job.control",
      Cause.Kind.Policy,
      Seq(Descriptor.Facet.Message(message))
    )

  private def _control_timeout[A](message: String): Consequence[A] =
    Failures.fail(
      Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Unavailable),
      Seq(
        Descriptor.Facet.Operation("job.control"),
        Descriptor.Facet.Message(s"job.control: $message")
      )
    )

}
