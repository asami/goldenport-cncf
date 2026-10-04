package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Whole-vector startup barrier. Original native inner monitors remain owners.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineDurableRuntimePart extends JobEngine { self: InMemoryJobEngine =>
  @volatile private[job] var _durable_runtime_initializing = false
  @volatile private[job] var _durable_runtime_failed = false
  private var _durable_runtime_owner = Option.empty[DurableJobRuntimeOwner]

  private[job] def _durable_runtime_blocked: Boolean = _durable_runtime_initializing || _durable_runtime_failed
  private[job] def _durable_runtime_owner_option: Option[DurableJobRuntimeOwner] = _state_monitor.synchronized { _durable_runtime_owner }

  private[job] def _begin_durable_runtime_c(): Consequence[Unit] = _state_monitor.synchronized {
    val snapshot = resourceSnapshot
    if (_durable_runtime_blocked || _durable_runtime_owner.nonEmpty || _workers_started.get() ||
        _durable_lifecycle_write_bridge.nonEmpty || !_durable_jobs.isEmpty || !_runtime_jobs.isEmpty ||
        !_durable_terminal_facts.isEmpty || runtimeState._native_lifetime_bindings.nonEmpty ||
        !_work_queue.isEmpty || _bootstrap_pending.nonEmpty ||
        snapshot.retained != 0 || snapshot.pending != 0 || snapshot.executing != 0 ||
        _admission_closed || _force_cancel_requested) DurableJobRuntimeOwner.refused
    else { _durable_runtime_initializing = true; Consequence.unit }
  }

  private[job] def _commit_durable_runtime_c(
    owner: DurableJobRuntimeOwner,
    scheduler: DurableJobMaintenanceScheduler,
    onready: DurableJobRuntimeOwner => Consequence[Unit]
  ): Consequence[Unit] = _state_monitor.synchronized {
    if (!_durable_runtime_initializing || _durable_runtime_failed || _admission_closed || _force_cancel_requested)
      DurableJobRuntimeOwner.refused
    else scheduler.start().flatMap { _ =>
      onready(owner).flatMap { _ =>
        if (_admission_closed || _force_cancel_requested) DurableJobRuntimeOwner.refused
        else {
          _durable_runtime_owner = Some(owner)
          owner._ready()
          _durable_runtime_initializing = false
          if (!_admission_closed && !_force_cancel_requested && !_work_queue.isEmpty && schedulerConfig.autoStartWorkers)
            _ensure_scheduler_workers()
          Consequence.unit
        }
      }
    }
  }

  private[job] def _fail_durable_runtime(): Unit = _state_monitor.synchronized {
    _durable_runtime_failed = true
    _durable_runtime_initializing = false
    _admission_closed = true
    _signal_state_change()
  }

  private[job] def _durable_runtime_ready: Boolean = _state_monitor.synchronized {
    !_durable_runtime_blocked && !_admission_closed && !_force_cancel_requested && _durable_runtime_owner.nonEmpty
  }

  override def inspectDurableIntegrity(maxCandidates: Int = 100)
      (using ctx: ExecutionContext): Consequence[JobDurableIntegrityReport] =
    new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope.flatMap { _ =>
      if (maxCandidates < 1 || maxCandidates > 1024) DurableJobIntegrityInspection.refused
      else {
        val selected = _state_monitor.synchronized {
          if (_durable_runtime_blocked || _admission_closed || _force_cancel_requested) None
          else _durable_runtime_owner
        }
        selected.map(_._inspect_integrity_c(maxCandidates)).getOrElse(DurableJobIntegrityInspection.refused)
      }
    }

  private[job] def _execute_with_durable_runtime_c(body: => Unit): Consequence[Unit] = {
    val selected = _state_monitor.synchronized {
      if (_durable_runtime_blocked) Left(DurableJobRuntimeOwner.refused[Unit])
      else Right(_durable_runtime_owner)
    }
    selected match {
      case Left(failure) => failure
      case Right(Some(owner)) => owner._execute_c { body; Consequence.unit }
      case Right(None) => body; Consequence.unit
    }
  }

  override def durableOperationalHealth: Option[JobDurableOperationalHealth] = {
    val selected = _state_monitor.synchronized {
      _durable_runtime_owner.toRight(
        if (_durable_runtime_failed) JobDurableOperationalState.Degraded
        else if (_durable_runtime_initializing) JobDurableOperationalState.Starting
        else JobDurableOperationalState.Unconfigured)
    }
    Some(selected match {
      case Right(owner) => owner.health
      case Left(state) => JobDurableOperationalHealth(state, state != JobDurableOperationalState.Unconfigured,
        false, false, false, false, false)
    })
  }
}
