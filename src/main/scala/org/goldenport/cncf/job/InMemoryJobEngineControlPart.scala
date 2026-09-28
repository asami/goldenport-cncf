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
private[job] trait InMemoryJobEngineControlPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _control(
    jobid: JobId,
    request: JobControlRequest
  ): Consequence[JobControlResponse] = {
    val transition = _state_monitor.synchronized {
      _get_record(jobid) match {
        case None =>
          _control_failure(s"job not found: ${jobid.value}")
        case Some(_) if _has_durable_task_checkpoint_refusal(jobid) =>
          _control_failure(s"job control blocked by durable task checkpoint refusal: ${jobid.value}")
        case Some(record) =>
          _control_transition_for(request.command, record.status).map {
            case applied @ ControlTransition.Apply(target) =>
              request.command match {
                case JobControlCommand.Retry =>
                  _retry_job(jobid, record)
                case _ =>
                  _append_timelinefor_control(jobid, request.command, target)
                  _update_record(jobid, target, _control_result_for(target))
                  if (request.command == JobControlCommand.Cancel)
                    _cancellation_scope(jobid).cancel()
                  if (
                    request.command == JobControlCommand.Cancel &&
                      _has_eligible_durable_terminal_checkpoint(jobid) &&
                      _has_no_active_or_pending_local_work(jobid)
                  )
                    _mark_base_completion(jobid, _control_result_for(target))
                  if (
                    request.command != JobControlCommand.Cancel ||
                      !_has_eligible_durable_terminal_checkpoint(jobid)
                  )
                    _append_eventfor_control(jobid, request.command, target)
              }
              applied
            case replay => replay
          }
      }
    }
    transition.flatMap {
      case ControlTransition.Apply(target) =>
        _control_response(jobid, request, target, changed = true)
      case ControlTransition.Replay(status) =>
        _control_replay_response(jobid, request, status)
    }
  }

  private def _control_response(
    jobid: JobId,
    request: JobControlRequest,
    target: JobStatus,
    changed: Boolean
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
        _await_control_sync(jobid, request, target, changed)
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

  private def _retry_job(jobid: JobId, record: JobRecord): Unit = {
    _cancellation_scopes.put(jobid, new JobCancellationScope)
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
    ))
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
    changed: Boolean
  ): Consequence[JobControlResponse] = {
    val deadline = _now().plusMillis(math.max(0L, request.option.timeoutMillis))
    var status = getStatus(jobid).getOrElse(target)
    val waitoutcome =
      if (request.command == JobControlCommand.Retry && !_is_retry_settled(status))
        _wait_until(deadline) {
          status = getStatus(jobid).getOrElse(status)
          _is_retry_settled(status)
        }
      else
        WaitOutcome.Completed
    status = getStatus(jobid).getOrElse(status)
    if (waitoutcome == WaitOutcome.TimedOut && !_is_retry_settled(status)) {
      _control_timeout(s"sync timeout for job control: ${jobid.value}")
    } else if (waitoutcome == WaitOutcome.Interrupted) {
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
    _get_record(jobid).exists(r =>
      r.status != JobStatus.Cancelled && !_has_durable_task_checkpoint_refusal(jobid)
    )

  private[job] def _has_eligible_durable_terminal_checkpoint(jobid: JobId): Boolean =
    _get_record(jobid).exists { record =>
      record.persistence == JobPersistencePolicy.Persistent &&
        record.taskReadModels.nonEmpty &&
        _durable_lifecycle_write_bridge.exists(_.hasAdmittedSnapshot(record.id))
    }

  private def _has_no_active_or_pending_local_work(jobid: JobId): Boolean =
    _get_record(jobid).exists { record =>
      record.activeTaskCount == 0 && record.pendingTaskCount == 0
    }

  private def _has_durable_task_checkpoint_refusal(jobid: JobId): Boolean =
    Option(_durable_lifecycle_write_failures.get(jobid)).exists {
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

  private[job] def _wait_until(
    deadline: Instant
  )(done: => Boolean): WaitOutcome =
    if (done)
      WaitOutcome.Completed
    else if (!_now().isBefore(deadline))
      WaitOutcome.TimedOut
    else {
      val registration = _timer.schedule(deadline) {
        _signal_state_change()
      }
      try
        _state_monitor.synchronized {
          var completed = done
          try {
            while (!completed && !_force_cancel_requested && _now().isBefore(deadline)) {
              val remainingnanos = Duration.between(_now(), deadline).toNanos
              if (remainingnanos > 0L) {
                val waitmillis = remainingnanos / 1000000L
                val waitnanos = (remainingnanos % 1000000L).toInt
                _state_monitor.wait(waitmillis, waitnanos)
              }
              completed = done
            }
            if (completed) WaitOutcome.Completed else WaitOutcome.TimedOut
          } catch {
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
              WaitOutcome.Interrupted
          }
        }
      finally
        registration.close()
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
