package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ExecutionProfileRuntime}
import org.goldenport.cncf.event.{EventBus, EventStore}
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Sep. 28, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineAdmissionPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _now(): Instant =
    timeSource.now()

  def withEventStore(store: EventStore): InMemoryJobEngine = {
    _event_store = Some(store)
    this
  }

  def withEventBus(bus: EventBus): InMemoryJobEngine = {
    _event_bus = Some(bus)
    this
  }

  private[job] def bindDurableLifecycleWriteBridge(
    bridge: DurableJobLifecycleWriteBridge
  ): Unit =
    _state_monitor.synchronized {
      _durable_lifecycle_write_bridge = Option(bridge)
    }

  private[job] def durableLifecycleWriteFailureFacts: Vector[DurableJobLifecycleWriteFailureFact] = _state_monitor.synchronized {
    val entries = _durable_lifecycle_write_failures.entrySet().iterator()
    val builder = Vector.newBuilder[DurableJobLifecycleWriteFailureFact]
    while (entries.hasNext) {
      val entry = entries.next()
      builder += DurableJobLifecycleWriteFailureFact(entry.getKey, entry.getValue)
    }
    val shared = runtimeState._native_lifetime_bindings.iterator.flatMap { case (jobid, binding) =>
      binding.checkpointfailure.map(DurableJobLifecycleWriteFailureFact(jobid, _))
    }.toVector
    (shared ++ builder.result()).distinctBy(_.jobid).sortBy(_.jobid.value)
      .take(_resource_budget.policy.maxretainedjobs)
  }

  private[job] def recoverDurableStartup(
    source: DurableJobStartupRecoverySource,
    store: DurableJobStore,
    maxCandidates: Int
  )(using ctx: ExecutionContext): Consequence[DurableJobStartupRecoveryReport] =
    (new DurableJobStartupRecoveryCoordinator(source, store)).recover(maxCandidates).map { report =>
      _durable_startup_recovery_report = Some(report)
      report
    }

  private[job] def durableStartupRecoveryReport: Option[DurableJobStartupRecoveryReport] =
    _durable_startup_recovery_report

  private[job] def recoverDurableTerminalFacts(
    source: DurableJobStartupRecoverySource,
    store: DurableJobStore,
    maxCandidates: Int
  )(using ctx: ExecutionContext): Consequence[DurableJobTerminalProjectionReport] =
    (new DurableJobStartupRecoveryCoordinator(source, store))
      .recoverTerminal(maxCandidates)((candidate, snapshot, decision, projection) =>
        _register_durable_terminal_fact(candidate, snapshot, decision, projection, store,
          _recovery_lifetime_policy, ctx))
      .map { report =>
        _durable_terminal_projection_report = Some(report)
        report
      }

  private[job] def durableTerminalProjectionReport: Option[DurableJobTerminalProjectionReport] =
    _durable_terminal_projection_report

  private[job] def durableTerminalProjection(
    jobId: JobId
  ): Option[DurableJobTerminalProjection] =
    Option(_durable_terminal_facts.get(jobId))

  private[job] def durableTerminalProjections: Vector[DurableJobTerminalProjection] =
    _durable_terminal_facts
      .values()
      .toArray(new Array[DurableJobTerminalProjection](0))
      .toVector

  private[job] def recoverDurableStartup(
    source: DurableJobStartupRecoverySource,
    store: DurableJobStore,
    port: DurableJobRuntimeRehydrationPort,
    maxCandidates: Int
  )(using ctx: ExecutionContext): Consequence[DurableJobRuntimeRehydrationReport] =
    (new DurableJobStartupRecoveryCoordinator(source, store))
      .recoverRuntime(maxCandidates, port)((candidate, snapshot, decision, rehydration) =>
        _register_durable_runtime_rehydration(candidate, snapshot, decision, rehydration, store,
          _recovery_lifetime_policy, ctx))
      .map { report =>
        _durable_runtime_rehydration_report = Some(report)
        report
      }

  private[job] def durableRuntimeRehydrationReport: Option[DurableJobRuntimeRehydrationReport] =
    _durable_runtime_rehydration_report

  private def _recovery_lifetime_policy: DurableJobLifetimeQuotaPolicy =
    _durable_lifecycle_write_bridge.map(_.recoveryPolicy(runtimeState._lifetime_budget.policy))
      .getOrElse(DurableJobLifetimeQuotaPolicy.default.copy(lifetime = runtimeState._lifetime_budget.policy))

  override def shutdown(): Unit = {
    quiesce()
    forceCancel()
  }

  override def quiesce(): Unit = _state_monitor.synchronized {
    _admission_closed = true
    _signal_state_change()
  }

  override def forceCancel(): Unit = _state_monitor.synchronized {
    _admission_closed = true
    _force_cancel_requested = true
    _remove_pending_resources(None, close = true)
    _scheduler.foreach(_.shutdownNow())
    _execution_scheduling_registration.foreach(_.close())
    _execution_scheduling_registration = None
    _worker_pool.shutdownNow()
    _signal_state_change()
  }

  private[job] def bind_execution_scheduling(runtime: ExecutionProfileRuntime): Unit =
    _execution_scheduling_registration = Some(
      runtime.schedulingRuntime.register_work_queue(() => drainOne())
    )

  def drainOne(): Boolean =
    _claim_work(blocking = false) match {
      case Some(work) =>
        _execute_reserved_work(work)
        true
      case None =>
        false
    }

  def drainAll(limit: Int = 1000): Int = {
    var count = 0
    while (count < limit && drainOne())
      count += 1
    count
  }

  def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] =
    runtimeState._operation_metrics.observeSubmission(
      if (_durable_runtime_blocked) DurableJobRuntimeOwner.refused
      else if (_admission_closed) Consequence.serviceUnavailable("JobEngine is quiescing")
      else _state_monitor.synchronized { _prepare_lifetime_root(tasks, Vector.empty) }
        .flatMap(_ => _submit_with_option(tasks, ctx, _default_submit_option(tasks)))
    )

  def submit(
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption
  ): Consequence[JobId] =
    runtimeState._operation_metrics.observeSubmission(_submit_with_option(tasks, ctx, option))

  override def stageSubmit(
    tasks: List[JobTask],
    unitOfWork: UnitOfWork,
    option: JobSubmitOption
  ): Consequence[JobId] =
    if (unitOfWork == null || unitOfWork.executionContext == null || option == null)
      JobInputResourcePolicy.invalid
    else {
      val ctx = unitOfWork.executionContext
      val reservation = _state_monitor.synchronized {
        _staging_owner_c().flatMap { owner =>
          _validate_staged_option(option).flatMap { _ =>
            JobInputResourcePolicy.validate(option.input).flatMap { _ =>
              _prepare_lifetime_root(tasks, option.executionNotes).flatMap { ticket =>
                _validate_submit_option(option).flatMap { _ =>
                  _reserve_submission(tasks, JobRunMode.Async).map { lease =>
                    var transferred = false
                    try {
                      val jobid = JobId.create("submit", ctx.clock.instant(), ctx.idGeneration)
                      val submission = new JobUnitOfWorkSubmission(jobid,
                        () => _resource_budget.release(lease),
                        published => runtimeState._operation_metrics.observeSubmission {
                          owner._execute_c {
                            _state_monitor.synchronized {
                              _staging_owner_c().flatMap { current =>
                                if (!(current eq owner) || !_resource_budget.isPending(lease))
                                  JobResourcePolicy.refused
                                else _publish_submission(
                                  _build_submission_record(jobid, tasks, ctx, option, _now()),
                                  option, ticket, lease, published)
                              }
                            }
                          }
                        })
                      transferred = true
                      submission
                    } finally if (!transferred) _resource_budget.release(lease)
                  }
                }
              }
            }
          }
        }
      }
      reservation.flatMap(_._stage_c(unitOfWork))
    }

  private def _staging_owner_c(): Consequence[DurableJobRuntimeOwner] =
    if (!_durable_runtime_ready) DurableJobRuntimeOwner.refused
    else _durable_runtime_owner_option match {
      case Some(owner) if owner.health.state == JobDurableOperationalState.Ready &&
          _durable_lifecycle_write_bridge.exists(_ eq owner._bridge) => Consequence.success(owner)
      case _ => DurableJobRuntimeOwner.refused
    }

  private def _validate_staged_option(option: JobSubmitOption): Consequence[Unit] =
    if (option.persistence != JobPersistencePolicy.Persistent || option.runMode != JobRunMode.Async)
      Consequence.argumentInvalid("stageSubmit requires Persistent Async options")
    else if (option.scheduledStartAt == null || option.scheduledStartAt.exists(_ == null) ||
        option.requestSummary == null || option.requestSummary.exists(_ == null) ||
        option.parameters == null || option.parameters.exists { case (key, value) => key == null || value == null } ||
        option.declaredProfile == null || option.declaredProfile.exists(_ == null) ||
        option.jobDefinitionSnapshot == null || option.jobDefinitionSnapshot.exists(_ == null))
      JobInputResourcePolicy.invalid
    else Consequence.unit

  private def _submit_with_option(
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption
  ): Consequence[JobId] =
    if (_durable_runtime_blocked) DurableJobRuntimeOwner.refused
    else if (_admission_closed)
      Consequence.serviceUnavailable("JobEngine is quiescing")
    else if (option == null)
      JobInputResourcePolicy.invalid
    else {
      JobInputResourcePolicy.validate(option.input).flatMap { _ =>
      _state_monitor.synchronized { _prepare_lifetime_root(tasks, option.executionNotes) }.flatMap { ticket =>
      _validate_submit_option(option).recoverWith { conclusion =>
        tasks.foreach(_observe_task_admission_failure(_, conclusion, ctx))
        Consequence.Failure(conclusion)
      }.flatMap { _ =>
        val admission = _state_monitor.synchronized {
          _reserve_submission(tasks, option.runMode).flatMap { lease =>
            try {
        val jobid = JobId.create("submit", ctx.clock.instant(), ctx.idGeneration)
        val record = _build_submission_record(jobid, tasks, ctx, option, _now())
        val admitted = _publish_submission(record, option, ticket, lease, () => ()).map(_ -> lease)
        admitted match {
          case Consequence.Failure(_) => _resource_budget.release(lease)
          case _ => ()
        }
        admitted
            } catch {
              case e: Throwable =>
                _resource_budget.release(lease)
                throw e
            }
          }
        }
        admission.flatMap { case (jobid, lease) =>
          if (option.runMode != JobRunMode.Sync) Consequence.success(jobid)
          else {
            var entered = false
            try _execute_with_durable_runtime_c {
              entered = true
              _with_execution_lease(jobid, lease) { _run_job_sync(jobid, tasks, ctx) }
            }.map(_ => jobid)
            finally if (!entered) _resource_budget.release(lease)
          }
        }
      }
      }
      }
    }

  private def _build_submission_record(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption,
    now: Instant
  ): JobRecord = {
        val initialdebug = JobDebugInfo(
          requestSummary = option.requestSummary.orElse(_request_summary(tasks)),
          parameters =
            (if (option.parameters.nonEmpty) option.parameters else _request_parameters(tasks)) ++
              option.jobDefinitionSnapshot.map(_.toParameters).getOrElse(Map.empty),
          executionNotes = option.executionNotes,
          declaredProfile =
            option.declaredProfile.orElse(option.jobDefinitionSnapshot.flatMap(_.profile)),
          jobDefinitionSnapshot = option.jobDefinitionSnapshot
        )
        JobRecord(
          id = jobid,
          tasks = tasks,
          submittedContext = ctx,
          status = JobStatus.Submitted,
          result = None,
          persistence = option.persistence,
          runMode = option.runMode,
          priority = option.priority,
          scheduledStartAt = option.scheduledStartAt.filter(_.isAfter(now)),
          createdAt = now,
          updatedAt = now,
          taskReadModels = Vector.empty,
          timeline = Vector(
            JobTimelineEvent(
              sequence = 1L,
              occurredAt = now,
              kind = "job.submitted",
              taskId = None,
              parentTaskId = None,
              note = None
            )
          ),
          debug = initialdebug,
          input = option.input
        )
  }

  private def _publish_submission(
    record: JobRecord,
    option: JobSubmitOption,
    ticket: JobLifetimeBudget.Ticket,
    lease: JobResourceBudget.Lease,
    published: () => Unit
  ): Consequence[JobId] = {
    val jobid = record.id
    val now = record.createdAt
    _admit_durable_lifecycle(record).recoverWith { conclusion =>
          _record_durable_lifecycle_write_failure(jobid, DurableJobLifecycleWriteFailure.AdmissionRefused)
          record.tasks.foreach(_observe_task_admission_failure(_, conclusion, record.submittedContext))
          Consequence.Failure(conclusion)
        }.map { _ =>
          runtimeState._lifetime_budget.publishRoot(jobid, ticket)
          _resource_budget.commit(lease, jobid)
          published()
          _cancellation_scopes.put(jobid, _cancellation_scope(jobid))
          _put_record(record)
          _append_event(
            jobid = jobid,
            name = "job.submitted",
            payload = Map(
              "job-id" -> jobid.value,
              "status" -> JobStatus.Submitted.toString,
              "request-summary" -> record.debug.requestSummary.getOrElse("")
            )
          )
          option.runMode match {
            case JobRunMode.Async =>
              option.scheduledStartAt.filter(_.isAfter(now)) match {
                case Some(scheduledat) =>
                  _append_timeline(jobid, "job.delayed.scheduled", None, None, Some(scheduledat.toString))
                  _append_event(
                    jobid = jobid,
                    name = "job.delayed.scheduled",
                    payload = Map(
                      "job-id" -> jobid.value,
                      "status" -> JobStatus.Submitted.toString,
                      "scheduled-start-at" -> scheduledat.toString
                    )
                  )
                  if (!_schedule_delayed_start(jobid, scheduledat, lease))
                    _resource_scheduling_failure(jobid)
                case None =>
                  _append_timeline(jobid, "job.async.queued", None, None, None)
                  _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), option.priority, jobid), lease)
              }
            case JobRunMode.Sync => ()
          }
          jobid
        }
  }

}
