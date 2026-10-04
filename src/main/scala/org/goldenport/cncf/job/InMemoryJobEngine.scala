package org.goldenport.cncf.job

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, Executors, PriorityBlockingQueue, ScheduledExecutorService, TimeUnit, ExecutorService}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.ExecutionContext as ScalaExecutionContext
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ExecutionProfileRuntime, ExecutionSchedulerMode, ExecutionSchedulingRegistration}
import org.goldenport.cncf.event.{EventBus, EventStore}

/*
 * @since   Sep. 28, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class InMemoryJobEngine(
  val runtimeState: InMemoryJobEngine.State = InMemoryJobEngine.State(),
  val retrySchedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule.default,
    val schedulerConfig: InMemoryJobEngine.SchedulerConfig =
      InMemoryJobEngine.SchedulerConfig.default,
  val timeSource: JobTimeSource = JobTimeSource.system,
  timer: Option[JobTimer] = None
)(
  implicit val executionContext: ScalaExecutionContext
) extends JobEngine with InMemoryJobEngineAdmissionPart with InMemoryJobEngineQueryPart with InMemoryJobEngineDurableLifecyclePart with InMemoryJobEngineExecutionPart with InMemoryJobEngineControlPart with InMemoryJobEngineSettlementPart with InMemoryJobEngineProjectionPart with InMemoryJobEngineRetryRecoveryPart with InMemoryJobEngineResourcePart with InMemoryJobEngineLifetimePart with InMemoryJobEngineDurableRuntimePart {
  import InMemoryJobEngine._
  override def operationalMetrics: Option[Vector[org.goldenport.cncf.metrics.RuntimeMetricPoint]] =
    Some(runtimeState._operational_metrics.snapshot() ++ runtimeState._operation_metrics.snapshot())
  private[job] val _durable_jobs = runtimeState.durableJobs
  private[job] val _runtime_jobs = runtimeState.runtimeJobs
  private[job] val _state_monitor = runtimeState._state_monitor
  private[job] val _resource_budget = runtimeState._attach_resource_budget(schedulerConfig, retrySchedule)
  private[job] val _resource_owner = _resource_budget.newOwner()
  private[job] val _bootstrap_pending = _prepare_resource_bootstrap()
  private[job] val _durable_terminal_facts =
    new ConcurrentHashMap[JobId, DurableJobTerminalProjection]()
  private[job] var _durable_lifecycle_write_bridge =
    Option.empty[DurableJobLifecycleWriteBridge]
  private[job] val _durable_lifecycle_write_failures =
    new ConcurrentHashMap[JobId, DurableJobLifecycleWriteFailure]()
  private[job] var _event_store: Option[EventStore] = None
  private[job] var _event_bus: Option[EventBus] = None
  private[job] var _durable_startup_recovery_report =
    Option.empty[DurableJobStartupRecoveryReport]
  private[job] var _durable_runtime_rehydration_report =
    Option.empty[DurableJobRuntimeRehydrationReport]
  private[job] var _durable_terminal_projection_report =
    Option.empty[DurableJobTerminalProjectionReport]
  private[job] val _scheduler: Option[ScheduledExecutorService] =
    if (timer.isDefined) None else Some(Executors.newSingleThreadScheduledExecutor())
  private[job] val _timer: JobTimer =
    timer.getOrElse(new ScheduledJobTimer(_scheduler.get, timeSource))
  private[job] val _work_sequence = new AtomicLong(0L)
  private[job] val _work_queue = new PriorityBlockingQueue[JobReservedWork](
    11,
    java.util.Comparator
      .comparingInt[JobReservedWork](_.work.priority)
      .thenComparingLong(_.work.sequence)
  )
  private[job] val _worker_pool: ExecutorService =
    Executors.newFixedThreadPool(schedulerConfig.workerCount)
  private[job] val _workers_started = new AtomicBoolean(false)
  private[job] val _cancellation_scopes = new ConcurrentHashMap[JobId, JobCancellationScope]()
  private[job] var _execution_scheduling_registration = Option.empty[ExecutionSchedulingRegistration]
  @volatile private[job] var _admission_closed = false
  @volatile private[job] var _force_cancel_requested = false

  _finish_native_bootstrap_cancellation()
  _rehydrate_resource_work()

}

object InMemoryJobEngine {
  val CallTreeVarcharThresholdBytes: Int = 8 * 1024
  val DefaultSlowCallTreeThresholdMillis: Long = 1000L

  val MaxNonRetryDelay: java.time.Duration =
    java.time.Duration.ofMinutes(15)

  private[job] enum WaitOutcome {
    case Completed
    case TimedOut
    case Interrupted
  }

  final case class SchedulerConfig(
    workerCount: Int = 1,
    autoStartWorkers: Boolean = true
  )

  object SchedulerConfig {
    val default: SchedulerConfig = SchedulerConfig()
  }

  final case class RetrySchedule(
    delayedRetryDelays: Vector[java.time.Duration],
    maxRetries: Int = 3
  )

  object RetrySchedule {
    val default: RetrySchedule = RetrySchedule(
      delayedRetryDelays = Vector(
        java.time.Duration.ofMinutes(1),
        java.time.Duration.ofMinutes(5),
        java.time.Duration.ofMinutes(15)
      ),
      maxRetries = 3
    )
  }

  final case class State(
    durableJobs: ConcurrentHashMap[JobId, JobRecord] = new ConcurrentHashMap[JobId, JobRecord](),
    runtimeJobs: ConcurrentHashMap[JobId, JobRecord] = new ConcurrentHashMap[JobId, JobRecord]()
  ) {
    private[job] val _state_monitor = new Object
    private[job] val _operational_metrics = new JobOperationalMetrics
    private[job] val _operation_metrics = new JobOperationMetrics
    private[job] var _resource_budget = new JobResourceBudget(JobResourcePolicy.default)
    private[job] var _lifetime_budget = new JobLifetimeBudget(JobLifetimePolicy.default)
    private[job] val _native_lifetime_bindings =
      scala.collection.mutable.HashMap.empty[JobId, JobDurableLifetimeRuntimeBinding]
    private[job] var _bootstrap_cancelled = Set.empty[JobId]
    private var _resource_attached = false

    private[job] def bindLifetimePolicy(policy: JobLifetimePolicy): Consequence[Unit] =
      _state_monitor.synchronized {
        if (_resource_attached || policy == null) JobResourcePolicy.refused
        else policy.validate.map { _ =>
          _lifetime_budget = new JobLifetimeBudget(policy)
          ()
        }
      }

    private[job] def bindResourcePolicy(policy: JobResourcePolicy): Consequence[Unit] =
      _state_monitor.synchronized {
        if (_resource_attached || policy == null) JobResourcePolicy.refused
        else policy.validate.map { _ =>
          _resource_budget = new JobResourceBudget(policy)
          ()
        }
      }

    private[job] def _attach_resource_budget(
      scheduler: SchedulerConfig,
      retry: RetrySchedule
    ): JobResourceBudget = _state_monitor.synchronized {
      import scala.jdk.CollectionConverters.*
      val admitted = _resource_budget.policy.validateEngine(scheduler, retry).flatMap { _ =>
        _validate_inputs(durableJobs).flatMap(_ => _validate_inputs(runtimeJobs))
      }.flatMap { _ =>
        JobLifetimeBootstrap.stage(durableJobs, runtimeJobs, _resource_budget.policy,
          _lifetime_budget, _native_lifetime_bindings.toMap)
      }.flatMap { seeds =>
        _resource_budget.seed(durableJobs.keySet().iterator().asScala ++ runtimeJobs.keySet().iterator().asScala)
          .map { _ =>
            _lifetime_budget.commitSeeds(seeds.local)
            seeds.native.foreach { case (jobid, seed) => _lifetime_budget.replaceNativeSeed(jobid, seed) }
            _bootstrap_cancelled = seeds.cancelled
          }
      }
      admitted match {
        case Consequence.Success(_) =>
          _resource_attached = true
          _resource_budget
        case _ => throw new IllegalArgumentException("JobEngine resource bootstrap refused")
      }
    }

    private def _validate_inputs(records: ConcurrentHashMap[JobId, JobRecord]): Consequence[Unit] =
      if (records == null) JobInputResourcePolicy.invalid
      else {
        val entries = records.values().iterator()
        var count = 0
        var result: Consequence[Unit] = Consequence.unit
        while (entries.hasNext && result.isSuccess) {
          val record = entries.next()
          count += 1
          result =
            if (count > _resource_budget.policy.maxretainedjobs) JobResourcePolicy.refused
            else if (record == null) JobInputResourcePolicy.invalid
            else JobInputResourcePolicy.validate(record.input)
        }
        result
      }
  }

  private[job] sealed trait RetryPolicy
  private[job] object RetryPolicy {
    case object None extends RetryPolicy
    final case class Immediate(nextattempt: Int, maxattempts: Int) extends RetryPolicy
    final case class Delayed(nextattempt: Int, delay: java.time.Duration, maxattempts: Int) extends RetryPolicy
    final case class Exhausted(kind: JobRetryKind, attempts: Int, maxattempts: Int)
        extends RetryPolicy
  }

  private[job] sealed trait SchedulerWorkItem {
    def sequence: Long
    def priority: Int
    def jobId: JobId
  }

  private[job] object SchedulerWorkItem {
    final case class JobRun(
      sequence: Long,
      priority: Int,
      jobId: JobId
    ) extends SchedulerWorkItem

    final case class RetryRun(
      sequence: Long,
      priority: Int,
      jobId: JobId,
      origin: RetryRunOrigin
    ) extends SchedulerWorkItem

    enum RetryRunOrigin {
      case Automatic, Control, Recovery
    }

    final case class SameJobTask(
      sequence: Long,
      priority: Int,
      jobId: JobId,
      task: JobTask,
      ctx: ExecutionContext,
      forcedTaskId: TaskId
    ) extends SchedulerWorkItem
  }

  final class ScheduledJobTimer(
    scheduler: ScheduledExecutorService,
    timeSource: JobTimeSource
  ) extends JobTimer {
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = {
      val delaymillis = math.max(0L, dueat.toEpochMilli - timeSource.now().toEpochMilli)
      val future = scheduler.schedule(
        new Runnable {
          override def run(): Unit =
            body
        },
        delaymillis,
        TimeUnit.MILLISECONDS
      )
      new JobTimerRegistration {
        def close(): Unit = {
          val _ = future.cancel(false)
        }
      }
    }

    override def shutdown(): Unit =
      scheduler.shutdownNow()
  }

  private final class ExecutionSchedulerJobTimer(
    runtime: ExecutionProfileRuntime
  ) extends JobTimer {
    private var _registrations = Vector.empty[ExecutionSchedulingRegistration]

    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration =
      synchronized {
        var registration = Option.empty[ExecutionSchedulingRegistration]
        val created = runtime.schedulingRuntime.schedule(dueat) {
          try
            body
          finally
            synchronized {
              registration.foreach(x => _registrations = _registrations.filterNot(_ eq x))
            }
          }
        registration = Some(created)
        _registrations :+= created
        new JobTimerRegistration {
          def close(): Unit =
            ExecutionSchedulerJobTimer.this.synchronized {
              created.close()
              _registrations = _registrations.filterNot(_ eq created)
            }
        }
      }

    override def shutdown(): Unit =
      synchronized {
        _registrations.foreach(_.close())
        _registrations = Vector.empty
      }
  }

  final class ManualJobTimer(
    timeSource: ManualJobTimeSource
  ) extends JobTimer {
    private final case class Entry(
      sequence: Long,
      dueat: Instant,
      run: () => Unit
    )

    private var _sequence = 0L
    private var _entries = Vector.empty[Entry]

    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration =
      synchronized {
        _sequence += 1
        val sequence = _sequence
        _entries :+= Entry(sequence, dueat, () => body)
        new JobTimerRegistration {
          def close(): Unit =
            ManualJobTimer.this.synchronized {
              _entries = _entries.filterNot(_.sequence == sequence)
            }
        }
      }

    def fireDue(): Int = {
      val due =
        synchronized {
          val now = timeSource.now()
          val (ready, pending) = _entries.partition(_.dueat.compareTo(now) <= 0)
          _entries = pending
          ready.sortBy(x => (x.dueat, x.sequence))
        }
      due.foreach(_.run())
      due.size
    }

    def advanceBy(duration: java.time.Duration): Int = {
      val _ = timeSource.advanceBy(duration)
      fireDue()
    }

    def advanceTo(instant: Instant): Int = {
      val _ = timeSource.advanceTo(instant)
      fireDue()
    }

    def pendingCount: Int =
      synchronized {
        _entries.size
      }

    override def shutdown(): Unit =
      synchronized {
        _entries = Vector.empty
      }
  }

  def create(
    schedulerConfig: SchedulerConfig = SchedulerConfig.default
  ): InMemoryJobEngine =
    new InMemoryJobEngine(schedulerConfig = schedulerConfig)(
      scala.concurrent.ExecutionContext.global
    )

  def create(runtime: ExecutionProfileRuntime): InMemoryJobEngine = {
    val manual = runtime.profile.control.schedulerMode == ExecutionSchedulerMode.Manual
    val config =
      if (manual) SchedulerConfig(workerCount = 1, autoStartWorkers = false)
      else SchedulerConfig.default
    val timer =
      if (manual) Some(new ExecutionSchedulerJobTimer(runtime))
      else None
    val engine = new InMemoryJobEngine(
      schedulerConfig = config,
      timeSource = JobTimeSource.fromRuntime(runtime),
      timer = timer
    )(scala.concurrent.ExecutionContext.global)
    if (manual)
      engine.bind_execution_scheduling(runtime)
    engine
  }
}
