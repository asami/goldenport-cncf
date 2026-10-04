package org.goldenport.cncf.job

import java.time.Instant
import scala.collection.mutable
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Oct. 1, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineResourcePart { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*
  import JobResourceBudget.Lease

  private val _execution_stack = new ThreadLocal[List[(JobId, Lease)]] {
    override def initialValue(): List[(JobId, Lease)] = Nil
  }
  private val _pending_registrations = mutable.HashMap.empty[Lease, (JobId, JobTimerRegistration)]
  private var _bootstrap_complete = false

  private[job] def resourceSnapshot: JobResourceSnapshot = _resource_budget.snapshot

  private[job] def _reserve_submission(
    tasks: List[JobTask],
    mode: JobRunMode
  ): Consequence[Lease] =
    if (_admission_closed || _durable_runtime_blocked || tasks == null || tasks.size > _resource_budget.policy.maxtaskspersubmission)
      JobResourcePolicy.refused
    else
      _resource_budget.reserve(
        _resource_owner,
        retained = true,
        pending = mode == JobRunMode.Async,
        executing = mode == JobRunMode.Sync,
        root = mode == JobRunMode.Async
      )

  private[job] def _reserve_pending(jobid: JobId, root: Boolean): Consequence[Lease] =
    _resource_budget.reserve(_resource_owner, pending = true, jobid = Some(jobid), root = root)

  private[job] def _with_execution_lease[A](jobid: JobId, lease: Lease)(body: => A): A = {
    val previous = _execution_stack.get()
    _execution_stack.set((jobid -> lease) :: previous)
    try body
    finally {
      _execution_stack.set(previous)
      if (previous.isEmpty) _execution_stack.remove()
      _resource_budget.release(lease)
    }
  }

  private[job] def _with_same_job_execution[A](jobid: JobId)(body: => Consequence[A]): Consequence[A] =
    _state_monitor.synchronized {
      if (_admission_closed) JobResourcePolicy.refused[Option[Lease]]
      else {
        val ticket = JobLifetimeBudget.work(1)
        _prepare_lifetime_ticket(jobid, ticket, normal = true).flatMap { _ =>
          val resource = _execution_stack.get().headOption match {
            case Some((current, _)) if current == jobid => Consequence.success(None)
            case _ => _resource_budget.reserve(_resource_owner, executing = true, jobid = Some(jobid))
              .map(Some(_))
          }
          resource.flatMap { lease =>
            _pay_lifetime_work(jobid, 1, retry = false) match {
              case Consequence.Success(_) => Consequence.success(lease)
              case Consequence.Failure(conclusion) =>
                lease.foreach(_resource_budget.release)
                Consequence.Failure(conclusion)
            }
          }
        }
      }
    }.flatMap {
      case Some(lease) => _with_execution_lease(jobid, lease)(body)
      case None => body
    }

  private[job] def _enqueue_work(work: SchedulerWorkItem, lease: Lease): Unit = {
    val queued = _resource_budget.enqueue(lease, work, _work_queue)
    if (queued && _bootstrap_complete && schedulerConfig.autoStartWorkers)
      _ensure_scheduler_workers()
  }

  private[job] def _claim_work(blocking: Boolean): Option[JobReservedWork] =
    if (blocking) _resource_budget.claim(_resource_owner, _work_queue, blocking)
    else _state_monitor.synchronized {
      if (_durable_runtime_blocked) None else _resource_budget.claim(_resource_owner, _work_queue, false)
    }

  private[job] def _execute_reserved_work(envelope: JobReservedWork): Unit = {
    var entered = false
    try {
      val _ = _execute_with_durable_runtime_c {
        entered = true
        _with_execution_lease(envelope.work.jobId, envelope.lease) { _run_scheduler_work(envelope.work) }
      }
    } finally if (!entered) _resource_budget.release(envelope.lease)
  }

  private[job] def _register_resource_timer(
    jobid: JobId,
    dueat: Instant,
    lease: Lease
  )(body: => Unit): Boolean =
    try {
      val registration = _timer.schedule(dueat) {
        _state_monitor.synchronized {
          if (_resource_budget.isTimerPending(lease)) {
            _pending_registrations.remove(lease).foreach(entry => _close_resource_registration(entry._2))
            body
          }
        }
      }
      if (_resource_budget.isTimerPending(lease))
        _pending_registrations.put(lease, jobid -> registration)
      else
        _close_resource_registration(registration)
      true
    } catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        _resource_budget.release(lease)
        false
      case NonFatal(_) =>
        _resource_budget.release(lease)
        false
    }

  private[job] def _remove_pending_resources(jobid: Option[JobId], close: Boolean = false): Unit = {
    val removed = _resource_budget.removePending(_resource_owner, jobid, _work_queue, close)
    val registrations = _pending_registrations.iterator
      .filter(entry => jobid.forall(_ == entry._2._1)).toVector
    registrations.foreach { case (lease, (_, registration)) =>
      _pending_registrations.remove(lease)
      _close_resource_registration(registration)
    }
    removed.foreach { envelope =>
      envelope.work match {
        case SchedulerWorkItem.SameJobTask(_, _, id, _, _, _) =>
          _mutate_record(id)(record => record.copy(
            pendingTaskCount = math.max(0, record.pendingTaskCount - 1),
            updatedAt = _now()
          ))
        case _ => ()
      }
    }
  }

  private def _close_resource_registration(registration: JobTimerRegistration): Unit =
    try registration.close()
    catch {
      case _: InterruptedException => Thread.currentThread().interrupt()
      case NonFatal(_) => ()
    }

  private[job] def _prepare_resource_bootstrap(): Vector[(JobRecord, Lease)] =
    _state_monitor.synchronized {
      val builder = Vector.newBuilder[JobRecord]
      val entries = _durable_jobs.values().iterator()
      while (entries.hasNext) {
        val record = entries.next()
        if (
          !runtimeState._bootstrap_cancelled.contains(record.id) &&
            record.status == JobStatus.Submitted &&
            ((record.retry.kind == JobRetryKind.Delayed && record.retry.nextRetryDueAt.nonEmpty) ||
              (record.retry.kind == JobRetryKind.None && record.scheduledStartAt.nonEmpty))
        ) {
          if (!_valid_recovered_resources(record))
            throw new IllegalArgumentException("JobEngine resource bootstrap refused")
          builder += record
        }
      }
      val records = builder.result()
      _resource_budget.reserveBatch(_resource_owner, records.map(_.id)) match {
        case Consequence.Success(leases) =>
          records.filter(record => leases.exists(lease => _resource_budget.jobIdentity(lease).contains(record.id)))
            .map(record => record -> leases.find(lease => _resource_budget.jobIdentity(lease).contains(record.id)).get)
        case _ => throw new IllegalArgumentException("JobEngine resource bootstrap refused")
      }
    }

  private[job] def _valid_recovered_resources(record: JobRecord): Boolean =
    record.tasks != null && record.tasks.size <= _resource_budget.policy.maxtaskspersubmission &&
      record.retry.attemptCount >= 0 && record.retry.attemptCount <= _resource_budget.policy.maxretryattempts &&
      record.retry.maxAttempts >= 0 && record.retry.maxAttempts <= _resource_budget.policy.maxretryattempts

  private[job] def _rehydrate_resource_work(): Unit = _state_monitor.synchronized {
    try {
      // Register the complete future batch before admitting due work to workers.
      _bootstrap_pending.filter { case (record, _) =>
        record.retry.nextRetryDueAt.orElse(record.scheduledStartAt).exists(_.isAfter(_now()))
      }.foreach { case (record, lease) =>
        val ok = record.retry.nextRetryDueAt match {
          case Some(dueat) =>
            _schedule_delayed_retry(record.id, dueat, lease, SchedulerWorkItem.RetryRunOrigin.Recovery)
          case None => _schedule_delayed_start(record.id, record.scheduledStartAt.get, lease)
        }
        if (!ok) throw new IllegalArgumentException("JobEngine resource bootstrap refused")
      }
      _bootstrap_pending.filter { case (record, _) =>
        record.retry.nextRetryDueAt.orElse(record.scheduledStartAt).exists(!_.isAfter(_now()))
      }.foreach { case (record, lease) =>
        record.retry.nextRetryDueAt match {
          case Some(_) => _run_scheduled_retry(record.id, lease, SchedulerWorkItem.RetryRunOrigin.Recovery)
          case None => _run_scheduled_start(record.id, lease)
        }
      }
      _bootstrap_complete = true
      if (!_work_queue.isEmpty && schedulerConfig.autoStartWorkers) _ensure_scheduler_workers()
    } catch {
      case NonFatal(_) =>
        _force_cancel_requested = true
        _remove_pending_resources(None, close = true)
        _worker_pool.shutdownNow()
        _scheduler.foreach(_.shutdownNow())
        throw new IllegalArgumentException("JobEngine resource bootstrap refused")
    }
  }

  private[job] def _resource_scheduling_failure(jobid: JobId): Unit =
    _get_record(jobid).foreach { record =>
      _finish_retry_exhaustion(
        jobid, record, JobResourcePolicy.refused[Nothing].conclusion,
        JobRetryKind.None, record.retry.attemptCount, retrySchedule.maxRetries, true
      )
    }
}
