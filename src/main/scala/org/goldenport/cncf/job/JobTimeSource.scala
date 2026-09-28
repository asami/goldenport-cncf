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
trait JobTimeSource {
  def now(): Instant
}

object JobTimeSource {
  val system: JobTimeSource = new JobTimeSource {
    def now(): Instant = Instant.now()
  }

  def fromRuntime(runtime: ExecutionProfileRuntime): JobTimeSource =
    new JobTimeSource {
      def now(): Instant = runtime.schedulingRuntime.clock.instant()
    }
}

final class ManualJobTimeSource(initial: Instant) extends JobTimeSource {
  @volatile private var _current = initial

  def now(): Instant = _current

  def advanceBy(duration: java.time.Duration): Instant =
    synchronized {
      _current = _current.plus(duration)
      _current
    }

  def advanceTo(instant: Instant): Instant =
    synchronized {
      if (instant.isAfter(_current))
        _current = instant
      _current
    }
}

trait JobTimer {
  def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration
  def shutdown(): Unit = ()
}

trait JobTimerRegistration {
  def close(): Unit
}
