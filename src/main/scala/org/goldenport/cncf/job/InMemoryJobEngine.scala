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
final class InMemoryJobEngine(
  val runtimeState: InMemoryJobEngine.State = InMemoryJobEngine.State(),
  val retrySchedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule.default,
    val schedulerConfig: InMemoryJobEngine.SchedulerConfig =
      InMemoryJobEngine.SchedulerConfig.default,
  val timeSource: JobTimeSource = JobTimeSource.system,
  timer: Option[JobTimer] = None
)(
  implicit val executionContext: ScalaExecutionContext
) extends JobEngine with InMemoryJobEngineAdmissionPart with InMemoryJobEngineQueryPart with InMemoryJobEngineDurableLifecyclePart with InMemoryJobEngineExecutionPart with InMemoryJobEngineControlPart with InMemoryJobEngineSettlementPart with InMemoryJobEngineProjectionPart with InMemoryJobEngineRetryRecoveryPart {
  import InMemoryJobEngine._
  private[job] val _durable_jobs = runtimeState.durableJobs
  private[job] val _runtime_jobs = runtimeState.runtimeJobs
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
  private val _scheduler: Option[ScheduledExecutorService] =
    if (timer.isDefined) None else Some(Executors.newSingleThreadScheduledExecutor())
  private[job] val _timer: JobTimer =
    timer.getOrElse(new ScheduledJobTimer(_scheduler.get, timeSource))
  private[job] val _work_sequence = new AtomicLong(0L)
  private[job] val _work_queue = new PriorityBlockingQueue[SchedulerWorkItem](
    11,
    java.util.Comparator
      .comparingInt[SchedulerWorkItem](_.priority)
      .thenComparingLong(_.sequence)
  )
  private[job] val _worker_pool: ExecutorService =
    Executors.newFixedThreadPool(math.max(1, schedulerConfig.workerCount))
  private[job] val _workers_started = new AtomicBoolean(false)
  private[job] val _state_monitor = new Object
  private[job] val _cancellation_scopes = new ConcurrentHashMap[JobId, JobCancellationScope]()
  private[job] var _execution_scheduling_registration = Option.empty[ExecutionSchedulingRegistration]
  @volatile private[job] var _admission_closed = false
  @volatile private[job] var _force_cancel_requested = false

  _rehydrate_delayed_starts()
  _rehydrate_delayed_retries()

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
  )

  private[job] sealed trait RetryPolicy
  private[job] object RetryPolicy {
    case object None extends RetryPolicy
    final case class Immediate(nextattempt: Int, maxAttempts: Int) extends RetryPolicy
    final case class Delayed(nextattempt: Int, dueat: Instant, maxAttempts: Int) extends RetryPolicy
    final case class Exhausted(kind: JobRetryKind, attempts: Int, maxAttempts: Int)
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
