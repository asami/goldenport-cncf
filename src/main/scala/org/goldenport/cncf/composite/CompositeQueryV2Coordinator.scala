package org.goldenport.cncf.composite

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{JobCommandMode, JobControlCommand, JobControlOption, JobControlPolicy, JobControlRequest, JobEngine, JobId, JobPersistencePolicy, JobSubmitOption}
import org.goldenport.cncf.operation.evaluation.OperationEvaluationContext
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[composite] final class CompositeQueryV2Coordinator(
  subsystem: Subsystem,
  jobEngine: JobEngine,
  targets: CompositeQueryV2Targets,
  request: CompositeQueryV2Request,
  cancellation: CompositeQueryV2Cancellation,
  deadlineNanos: Long
) {
  private final case class Reservation(
    branchid: String,
    task: CompositeQueryV2BranchTask,
    targetid: String,
    admittednanos: Long,
    jobid: Option[JobId] = None,
    submitting: Boolean = true,
    pendingterminal: Option[CompositeQueryV2TaskResult] = None,
    bodyexited: Boolean = false
  )

  private val _monitor = new Object
  private val _branches = request.branches.map(x => x.branchId -> x).toMap
  private val _results = mutable.Map.empty[String, CompositeQueryV2BranchResult]
  private val _diagnostics = mutable.Map.empty[String, CompositeQueryV2Diagnostic]
  private val _reservations = mutable.Map.empty[String, Reservation]
  private val _pending_cancellations = mutable.Set.empty[JobId]
  // IDs outlive physical reservations so an already-settled task can never turn
  // into an authorization capability for a different invocation.
  private val _owned_job_ids = mutable.Set.empty[JobId]
  private var _cancelled = false
  private var _timed_out = false
  private val _deadline_nanos = deadlineNanos

  def execute(context: ExecutionContext): Consequence[CompositeQueryV2Response] = {
    if (request.branches.isEmpty)
      Consequence.success(CompositeQueryV2Response(Vector.empty, Vector.empty, CompositeQueryV2AggregateStatus.Succeeded))
    else if (cancellation.isCancelled)
      Consequence.success(_cancelled_response())
    else {
      val registration = cancellation.register(_cancel_request())
      val inherited = context.jobContext.cancellationScope.map(_.register(Consequence { _cancel_request() }))
      try {
        _run(context)
      } finally {
        registration.close()
        inherited.foreach(_.close())
      }
    }
  }

  private def _run(context: ExecutionContext): Consequence[CompositeQueryV2Response] = {
    var interrupted = false
    while (!_is_complete) {
      val now = System.nanoTime()
      _advance_timeouts(now)
      _cancel_owned(context)
      val ready = _admit_ready(now)
      ready.foreach(_submit(_, context))
      if (!_is_complete) {
        try _monitor.synchronized {
          if (!_is_complete)
            _monitor.wait(math.max(1L, math.min(25L, _remaining_millis())))
        } catch {
          case _: InterruptedException =>
            interrupted = true
            _cancel_request()
        }
      }
    }
    if (interrupted) Thread.currentThread.interrupt()
    _cancel_owned(context)
    Consequence.success(_response)
  }

  private def _admit_ready(now: Long): Vector[(CompositeQueryV2Branch, Reservation)] =
    _monitor.synchronized {
      _cascade_unavailable_dependencies()
      if (_cancelled || _timed_out) Vector.empty
      else {
        // A physical body leaving its worker does not free this compositor slot
        // by itself.  Canonical settlement still owns the reservation; only a
        // logical timeout/cancel together with body exit may release it.
        val capacity = request.policy.maxParallelism - _reservations.size
        if (capacity <= 0) Vector.empty
        else request.branches.iterator
          .filter(branch => !_results.contains(branch.branchId) && !_reservations.contains(branch.branchId))
          .filter(_dependencies_completed)
          .take(capacity)
          .flatMap { branch =>
            targets.resolve(branch.target, subsystem).toOption.map { case (targetid, _) =>
              val invocation = CompositeQueryV2Invocation(2, branch.branchId, targetid, branch.request)
              val task = new CompositeQueryV2BranchTask(
                branch,
                targetid,
                new CompositeQueryV2Protocol(subsystem, targets),
                invocation,
                _complete_task,
                _body_exited
              )
              val reservation = Reservation(branch.branchId, task, targetid, now)
              _reservations.update(branch.branchId, reservation)
              branch -> reservation
            }
          }.toVector
      }
    }

  private def _submit(pair: (CompositeQueryV2Branch, Reservation), context: ExecutionContext): Unit = {
    val (branch, reservation) = pair
    val submit = _monitor.synchronized {
      val now = System.nanoTime()
      if (now >= _deadline_nanos && !_cancelled && !_timed_out) {
        _timed_out = true
        _seal_incomplete(CompositeQueryV2FailureCode.TimedOut)
        false
      } else if (_reservations.get(branch.branchId).contains(reservation) &&
        !_results.contains(branch.branchId) && !_cancelled && !_timed_out && _branch_expired(reservation, now)) {
        // This ready vector was reserved before a provider call for an earlier
        // branch consumed the branch budget.  It was never submitted, so it has
        // no physical body and can settle immediately.
        _reservations.update(branch.branchId, reservation.copy(submitting = false, bodyexited = true))
        _timeout_branch(branch.branchId, reservation)
        false
      } else
        _reservations.get(branch.branchId).contains(reservation) &&
          !_results.contains(branch.branchId) && !_cancelled && !_timed_out
    }
    if (!submit) {
      _monitor.synchronized {
        _reservations.remove(branch.branchId)
        _monitor.notifyAll()
      }
      return
    }
    val submissioncontext = ExecutionContext.withFreshExecutionResponseCell(
      ExecutionContext.withFrameworkCallTreeEnabled(
        ExecutionContext.withOperationEvaluation(context, OperationEvaluationContext.empty),
        enabled = false
      )
    )
    val persistence = request.policy.persistence match {
      case CompositeQueryV2Persistence.Ephemeral => JobPersistencePolicy.Ephemeral
      case CompositeQueryV2Persistence.PersistentDiagnostics => JobPersistencePolicy.Persistent
    }
    jobEngine.submit(
      List(reservation.task),
      submissioncontext,
      JobSubmitOption(
        persistence = persistence,
        requestSummary = Some("composite-query-v2"),
        parameters = Map.empty,
        input = None
      )
    ) match {
      case Consequence.Success(jobid) =>
        val (cancelnow, pending) = _monitor.synchronized {
          _owned_job_ids += jobid
          _reservations.get(branch.branchId) match {
            case Some(current) if current.task eq reservation.task =>
              val updated = current.copy(
                jobid = Some(jobid),
                submitting = false
              )
              _reservations.update(branch.branchId, updated)
              (_cancelled || _timed_out || _results.contains(branch.branchId), updated.pendingterminal)
            case _ => true -> None
          }
        }
        pending.foreach(_complete_task)
        if (cancelnow) _cancel_job(jobid, context)
      case Consequence.Failure(_) =>
        // No task body entered when admission failed, so this reservation has no
        // physical worker to retain while its sanitized terminal result is published.
        _monitor.synchronized {
          _reservations.get(branch.branchId).foreach { current =>
            _reservations.update(branch.branchId, current.copy(submitting = false))
          }
        }
        _body_exited(branch.branchId)
        _complete_task(CompositeQueryV2TaskResult(
          branch.branchId,
          reservation.targetid,
          None,
          Some(CompositeQueryV2FailureCode.SchedulerRejected),
          false,
          reservation.admittednanos,
          System.nanoTime()
        ))
    }
  }

  private def _complete_task(taskresult: CompositeQueryV2TaskResult): Unit = {
    val calculated = _result_for(taskresult)
    _monitor.synchronized {
      _reservations.get(taskresult.branchid).foreach { reservation =>
        if (reservation.submitting && reservation.jobid.isEmpty) {
          _reservations.update(taskresult.branchid, reservation.copy(pendingterminal = Some(taskresult)))
        } else if (taskresult.cancelled && !_cancelled && !_timed_out) {
          // An owning Job cancelled outside this coordinator (including engine
          // shutdown) is an invocation cancellation, not an ordinary failure.
          _cancel_request()
        } else if (!_results.contains(taskresult.branchid)) {
          val elapsed = math.max(0L, TimeUnit.NANOSECONDS.toMillis(taskresult.completednanos - taskresult.startednanos))
          val queued = math.max(0L, TimeUnit.NANOSECONDS.toMillis(taskresult.startednanos - reservation.admittednanos))
          val result =
            if (System.nanoTime() >= _deadline_nanos) {
              // Request expiry is a whole-request terminal state.  It seals
              // every incomplete branch without selecting branch fallback.
              if (!_cancelled && !_timed_out) {
                _timed_out = true
                _seal_incomplete(CompositeQueryV2FailureCode.TimedOut)
              }
              _results(taskresult.branchid)
            } else if (_branch_expired(reservation, System.nanoTime())) {
              val branch = _branches(taskresult.branchid)
              _fallback(branch, CompositeQueryV2FailureCode.TimedOut).getOrElse(
                CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.TimedOut, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.TimedOut)))
              )
            } else calculated
          _results.update(taskresult.branchid, result)
          _diagnostics.update(taskresult.branchid, CompositeQueryV2Diagnostic(
            taskresult.branchid,
            reservation.targetid,
            result.outcome,
            result.failure.map(_.code).orElse(result.recoveredFailure),
            elapsed,
            queued,
            result.outcome == CompositeQueryV2Outcome.Cancelled,
            result.outcome == CompositeQueryV2Outcome.Fallback,
            reservation.jobid.map(_.value)
          ))
          if (request.policy.failureMode == CompositeQueryV2FailureMode.FailFastRequired &&
            result.required && !result.isUsable)
            _fail_fast()
        }
        if (reservation.bodyexited && !reservation.submitting)
          _reservations.remove(taskresult.branchid)
      }
      _monitor.notifyAll()
    }
  }

  private def _result_for(taskresult: CompositeQueryV2TaskResult): CompositeQueryV2BranchResult = {
    val branch = _branches(taskresult.branchid)
    taskresult.response match {
      case Some(response) =>
        CompositeQueryV2ResponseEncoding.byteSize(response) match {
          case Some(size) if size <= request.policy.maxResponseBytes =>
            CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Succeeded, Some(response))
          case Some(_) =>
            CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Failed, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.ResponseLimitExceeded)))
          case None =>
            CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Failed, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.UnsupportedResponse)))
        }
      case None =>
        val code = taskresult.failure.getOrElse(CompositeQueryV2FailureCode.InternalFailure)
        _fallback(branch, code).getOrElse {
          val outcome = code match {
            case CompositeQueryV2FailureCode.Cancelled => CompositeQueryV2Outcome.Cancelled
            case CompositeQueryV2FailureCode.TimedOut => CompositeQueryV2Outcome.TimedOut
            case _ => CompositeQueryV2Outcome.Failed
          }
          CompositeQueryV2BranchResult(branch.branchId, branch.required, outcome, None, Some(CompositeQueryV2Failure(code)))
        }
    }
  }

  private def _fallback(
    branch: CompositeQueryV2Branch,
    code: CompositeQueryV2FailureCode
  ): Option[CompositeQueryV2BranchResult] =
    branch.fallback.filter { fallback =>
      (code == CompositeQueryV2FailureCode.QueryFailed || code == CompositeQueryV2FailureCode.TimedOut) &&
        fallback.onCodes.contains(code)
    }.map { fallback =>
      CompositeQueryV2BranchResult(
        branch.branchId,
        branch.required,
        CompositeQueryV2Outcome.Fallback,
        Some(fallback.response),
        None,
        Some(code)
      )
    }

  private def _advance_timeouts(now: Long): Unit = {
    val requestexpired = now >= _deadline_nanos
    val expired = _monitor.synchronized {
      if (requestexpired && !_cancelled && !_timed_out) {
        _timed_out = true
        _seal_incomplete(CompositeQueryV2FailureCode.TimedOut)
        Vector.empty
      } else if (!_cancelled && !_timed_out) {
        _reservations.collect {
          case (id, reservation) if _branch_expired(reservation, now) =>
            id -> reservation
        }.toVector
      } else Vector.empty
    }
    expired.foreach { case (id, reservation) =>
      _timeout_branch(id, reservation)
    }
  }

  private def _timeout_branch(id: String, reservation: Reservation): Unit =
    _monitor.synchronized {
      val current = _reservations.getOrElse(id, reservation)
      if (!_results.contains(id)) {
        current.task.cancel()
        current.jobid.foreach(_pending_cancellations += _)
        val branch = _branches(id)
        val result = _fallback(branch, CompositeQueryV2FailureCode.TimedOut).getOrElse(
          CompositeQueryV2BranchResult(id, branch.required, CompositeQueryV2Outcome.TimedOut, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.TimedOut)))
        )
        _results.update(id, result)
        _diagnostics.update(id, CompositeQueryV2Diagnostic(id, current.targetid, result.outcome, Some(CompositeQueryV2FailureCode.TimedOut), 0L, 0L, false, result.outcome == CompositeQueryV2Outcome.Fallback, current.jobid.map(_.value)))
      }
      if (current.bodyexited) _reservations.remove(id)
      _monitor.notifyAll()
    }

  private def _cancel_request(): Unit = _monitor.synchronized {
    if (!_cancelled && !_timed_out) {
      _cancelled = true
      _seal_incomplete(CompositeQueryV2FailureCode.Cancelled)
    }
    _monitor.notifyAll()
  }

  private def _seal_incomplete(code: CompositeQueryV2FailureCode): Unit = {
    request.branches.foreach { branch =>
      if (!_results.contains(branch.branchId)) {
        _reservations.get(branch.branchId).foreach(_.task.cancel())
        _reservations.get(branch.branchId).flatMap(_.jobid).foreach(_pending_cancellations += _)
        val outcome = if (code == CompositeQueryV2FailureCode.Cancelled) CompositeQueryV2Outcome.Cancelled else CompositeQueryV2Outcome.TimedOut
        _results.update(branch.branchId, CompositeQueryV2BranchResult(branch.branchId, branch.required, outcome, None, Some(CompositeQueryV2Failure(code))))
        val targetid = _reservations.get(branch.branchId).map(_.targetid).getOrElse(_target_id(branch))
        _diagnostics.update(branch.branchId, CompositeQueryV2Diagnostic(branch.branchId, targetid, outcome, Some(code), 0L, 0L, outcome == CompositeQueryV2Outcome.Cancelled, false, _reservations.get(branch.branchId).flatMap(_.jobid).map(_.value)))
      }
    }
    _reservations.collect {
      case (id, reservation) if reservation.bodyexited && _results.contains(id) => id
    }.foreach(_reservations.remove)
  }

  private def _fail_fast(): Unit = {
    request.branches.foreach { branch =>
      if (!_results.contains(branch.branchId)) {
        _reservations.get(branch.branchId).foreach(_.task.cancel())
        _reservations.get(branch.branchId).flatMap(_.jobid).foreach(_pending_cancellations += _)
        _results.update(branch.branchId, CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Skipped, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.DependencyUnavailable))))
        _diagnostics.update(branch.branchId, CompositeQueryV2Diagnostic(branch.branchId, _target_id(branch), CompositeQueryV2Outcome.Skipped, Some(CompositeQueryV2FailureCode.DependencyUnavailable), 0L, 0L, false, false, _reservations.get(branch.branchId).flatMap(_.jobid).map(_.value)))
      }
    }
  }

  private def _cancel_owned(context: ExecutionContext): Unit = {
    val jobs = _monitor.synchronized {
      val result = _pending_cancellations.toVector
      _pending_cancellations.clear()
      result
    }
    jobs.foreach(_cancel_job(_, context))
  }

  private def _cancel_job(jobid: JobId, context: ExecutionContext): Unit = {
    val policy = new CompositeQueryV2JobControlPolicy(() => _monitor.synchronized(_owned_job_ids.toSet), context.security.principal.id.toString)
    given ExecutionContext = context
    val _ = jobEngine.control(
      jobid,
      JobControlRequest(JobControlCommand.Cancel, JobControlOption(mode = JobCommandMode.Async)),
      policy
    )
  }

  private def _cascade_unavailable_dependencies(): Unit = {
    var changed = true
    while (changed) {
      changed = false
      request.branches.foreach { branch =>
        if (!_results.contains(branch.branchId) && !_reservations.contains(branch.branchId)) {
          val blocked = branch.dependencies.exists { dependency =>
            dependency.mode == CompositeQueryV2DependencyMode.RequireValue &&
              _results.get(dependency.branchId).exists(!_.isUsable)
          }
          if (blocked) {
            _results.update(branch.branchId, CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Skipped, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.DependencyUnavailable))))
            _diagnostics.update(branch.branchId, CompositeQueryV2Diagnostic(branch.branchId, _target_id(branch), CompositeQueryV2Outcome.Skipped, Some(CompositeQueryV2FailureCode.DependencyUnavailable), 0L, 0L, false, false, None))
            changed = true
          }
        }
      }
    }
  }

  private def _dependencies_completed(branch: CompositeQueryV2Branch): Boolean =
    branch.dependencies.forall(dependency => _results.contains(dependency.branchId))

  private def _target_id(branch: CompositeQueryV2Branch): String = branch.target match {
    case CompositeQueryV2Target.Local => "local"
    case CompositeQueryV2Target.Subsystem(id) => id
  }

  private def _is_complete: Boolean = _monitor.synchronized {
    _results.size == request.branches.size
  }

  private def _body_exited(branchid: String): Unit = _monitor.synchronized {
    _reservations.get(branchid).foreach { reservation =>
      val updated = reservation.copy(bodyexited = true)
      if (_results.contains(branchid)) _reservations.remove(branchid)
      else _reservations.update(branchid, updated)
    }
    _monitor.notifyAll()
  }

  private def _branch_expired(reservation: Reservation, now: Long): Boolean =
    _branches(reservation.branchid).timeoutMillis.exists { timeout =>
      // The branch budget begins when the coordinator reserves it, before
      // provider admission and while the official scheduler queues it.
      now - reservation.admittednanos >= TimeUnit.MILLISECONDS.toNanos(timeout)
    }

  private def _remaining_millis(): Long =
    math.max(1L, TimeUnit.NANOSECONDS.toMillis(_deadline_nanos - System.nanoTime()))

  private def _response: CompositeQueryV2Response = _monitor.synchronized {
    val results = request.branches.map(branch => _results.getOrElse(branch.branchId, CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Failed, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.InternalFailure)))) )
    val diagnostics = request.branches.flatMap(branch => _diagnostics.get(branch.branchId))
    CompositeQueryV2Response(results, diagnostics, _aggregate_status(results))
  }

  private def _cancelled_response(): CompositeQueryV2Response = {
    val results = request.branches.map(branch => CompositeQueryV2BranchResult(branch.branchId, branch.required, CompositeQueryV2Outcome.Cancelled, None, Some(CompositeQueryV2Failure(CompositeQueryV2FailureCode.Cancelled))))
    val diagnostics = request.branches.map(branch => CompositeQueryV2Diagnostic(branch.branchId, _target_id(branch), CompositeQueryV2Outcome.Cancelled, Some(CompositeQueryV2FailureCode.Cancelled), 0L, 0L, true, false, None))
    CompositeQueryV2Response(results, diagnostics, CompositeQueryV2AggregateStatus.Cancelled)
  }

  private def _aggregate_status(results: Vector[CompositeQueryV2BranchResult]): CompositeQueryV2AggregateStatus =
    if (_cancelled) CompositeQueryV2AggregateStatus.Cancelled
    else if (_timed_out) CompositeQueryV2AggregateStatus.TimedOut
    else if (results.exists(x => x.required && !x.isUsable)) CompositeQueryV2AggregateStatus.Failed
    else if (results.exists(!_.isUsable)) CompositeQueryV2AggregateStatus.PartialFailure
    else CompositeQueryV2AggregateStatus.Succeeded
}

private[composite] final class CompositeQueryV2JobControlPolicy(
  owned: () => Set[JobId],
  principalId: String
) extends JobControlPolicy {
  def authorize(jobId: JobId, request: JobControlRequest)(using context: ExecutionContext): Consequence[Unit] =
    if (request.command != JobControlCommand.Cancel || !owned().contains(jobId) || context.security.principal.id.toString != principalId)
      Consequence.operationIllegal("composite-query-v2.job-control", "denied")
    else Consequence.unit
}
