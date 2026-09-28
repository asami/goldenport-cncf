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
final case class JobId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "job", timestamp, entropy)

object JobId {
  def generate(): JobId =
    JobId("cncf", "job")

  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): JobId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): JobId =
    JobId(
      major = idgeneration.namespace.major,
      minor = idgeneration.namespace.minor,
      timestamp = Some(timestamp),
      entropy = Some(idgeneration.opaqueId(s"job.$purpose"))
    )

  def parse(s: String): Consequence[JobId] =
    UniversalId.parseParts(s, "job").map(parts =>
      JobId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy))
    )
}

final case class TaskId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "task", timestamp, entropy)

object TaskId {
  def generate(): TaskId =
    TaskId("cncf", "task")

  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): TaskId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): TaskId =
    TaskId(
      major = idgeneration.namespace.major,
      minor = idgeneration.namespace.minor,
      timestamp = Some(timestamp),
      entropy = Some(idgeneration.opaqueId(s"task.$purpose"))
    )

  def parse(s: String): Consequence[TaskId] =
    UniversalId.parseParts(s, "task").map(parts =>
      TaskId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy))
    )
}

final case class ActionId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "action", timestamp, entropy)

object ActionId {
  def generate(): ActionId =
    ActionId("cncf", "action")

  def create(
    purpose: String,
    timestamp: Instant
  )(using ctx: ExecutionContext): ActionId =
    create(purpose, timestamp, ctx.idGeneration)

  def create(
    purpose: String,
    timestamp: Instant,
    idgeneration: IdGenerationContext
  ): ActionId =
    ActionId(
      major = idgeneration.namespace.major,
      minor = idgeneration.namespace.minor,
      timestamp = Some(timestamp),
      entropy = Some(idgeneration.opaqueId(s"action.$purpose"))
    )

  def parse(s: String): Consequence[ActionId] =
    UniversalId.parseParts(s, "action").map(parts =>
      ActionId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy))
    )
}
