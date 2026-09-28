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

  private[job] def durableLifecycleWriteFailureFacts: Vector[DurableJobLifecycleWriteFailureFact] = {
    val entries = _durable_lifecycle_write_failures.entrySet().iterator()
    val builder = Vector.newBuilder[DurableJobLifecycleWriteFailureFact]
    while (entries.hasNext) {
      val entry = entries.next()
      builder += DurableJobLifecycleWriteFailureFact(entry.getKey, entry.getValue)
    }
    builder.result().sortBy(_.jobid.value)
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
      .recoverTerminal(maxCandidates)(_register_durable_terminal_fact)
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
      .recoverRuntime(maxCandidates, port)(_register_durable_runtime_rehydration)
      .map { report =>
        _durable_runtime_rehydration_report = Some(report)
        report
      }

  private[job] def durableRuntimeRehydrationReport: Option[DurableJobRuntimeRehydrationReport] =
    _durable_runtime_rehydration_report

  override def shutdown(): Unit = {
    quiesce()
    forceCancel()
  }

  override def quiesce(): Unit = {
    _admission_closed = true
    _signal_state_change()
  }

  override def forceCancel(): Unit = {
    _force_cancel_requested = true
    _timer.shutdown()
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
    Option(_work_queue.poll()) match {
      case Some(work) =>
        _run_scheduler_work(work)
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
    submit(tasks, ctx, _default_submit_option(tasks))

  def submit(
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption
  ): Consequence[JobId] =
    if (_admission_closed)
      Consequence.serviceUnavailable("JobEngine is quiescing")
    else {
      _validate_submit_option(option).recoverWith { conclusion =>
        tasks.foreach(_observe_task_admission_failure(_, conclusion, ctx))
        Consequence.Failure(conclusion)
      }.flatMap { _ =>
        val jobid = JobId.create("submit", ctx.clock.instant(), ctx.idGeneration)
        val now = _now()
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
        val record = JobRecord(
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
        _admit_durable_lifecycle(record).recoverWith { conclusion =>
          _record_durable_lifecycle_write_failure(jobid, DurableJobLifecycleWriteFailure.AdmissionRefused)
          tasks.foreach(_observe_task_admission_failure(_, conclusion, ctx))
          Consequence.Failure(conclusion)
        }.map { _ =>
          _cancellation_scopes.put(jobid, new JobCancellationScope)
          _put_record(record)
          _append_event(
            jobid = jobid,
            name = "job.submitted",
            payload = Map(
              "job-id" -> jobid.value,
              "status" -> JobStatus.Submitted.toString,
              "request-summary" -> initialdebug.requestSummary.getOrElse("")
            )
          )
          option.runMode match {
            case JobRunMode.Async =>
              option.scheduledStartAt.filter(_.isAfter(now)) match {
                case Some(scheduledat) =>
                  _append_timeline(
                    jobid,
                    "job.delayed.scheduled",
                    None,
                    None,
                    Some(scheduledat.toString)
                  )
                  _append_event(
                    jobid = jobid,
                    name = "job.delayed.scheduled",
                    payload = Map(
                      "job-id" -> jobid.value,
                      "status" -> JobStatus.Submitted.toString,
                      "scheduled-start-at" -> scheduledat.toString
                    )
                  )
                  _schedule_delayed_start(jobid, scheduledat)
                case None =>
                  _append_timeline(jobid, "job.async.queued", None, None, None)
                  _enqueue_work(SchedulerWorkItem.JobRun(_next_sequence(), option.priority, jobid))
              }
            case JobRunMode.Sync =>
              _run_job_sync(jobid, tasks, ctx)
          }
          jobid
        }
      }
    }

}
