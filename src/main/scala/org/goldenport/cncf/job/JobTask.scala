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
sealed trait TaskOutcome {
  def result: Consequence[OperationResponse]
}

final case class TaskSucceeded(
  response: OperationResponse
) extends TaskOutcome {
  def result: Consequence[OperationResponse] =
    Consequence.success(response)
}

final case class TaskFailed(
  conclusion: Conclusion
) extends TaskOutcome {
  def result: Consequence[OperationResponse] =
    Consequence.Failure(conclusion)
}

trait JobTask {
  def actionId: ActionId
  def taskKind: String = "action"
  def targetKind: Option[String] = None
  def relation: Option[String] = None
  def transactionRole: Option[String] = None
  def transactionScope: Option[String] = None
  def compensationActionRef: Option[String] = None
  def compensationTask: Option[JobTask] = None
  def componentName: Option[String] = None
  def serviceName: Option[String] = None
  def operationName: Option[String] = None
  def requestSummary: Option[String] = None
  def requestParameters: Map[String, String] = Map.empty
  def defaultPersistence: JobPersistencePolicy = JobPersistencePolicy.Persistent
  def run(ctx: ExecutionContext): TaskOutcome
  def observeCanonicalOutcome(
    outcome: TaskOutcome,
    ctx: ExecutionContext,
    cancelled: Boolean
  ): Unit = ()
  def observeAdmissionFailure(
    conclusion: Conclusion,
    ctx: ExecutionContext
  ): Unit = ()
}

final case class ActionTask(
  actionId: ActionId,
  action: Action,
  actionEngine: ActionEngine,
  component: Option[Component],
  override val compensationActionRef: Option[String] = None,
  override val compensationTask: Option[JobTask] = None
) extends JobTask {
  override def targetKind: Option[String] =
    if (_is_aggregate_target) Some("aggregate") else Some("action")

  private def _is_aggregate_target: Boolean =
    component.exists { c =>
      val operation = action.request.operation
      c.aggregateDefinitions.exists { definition =>
        definition.creates.exists(x =>
          NamingConventions.equivalentByNormalized(x.name, operation)
        ) ||
          definition.commands.exists(x => NamingConventions.equivalentByNormalized(x.name, operation))
      }
    }

  override def componentName: Option[String] =
    component.map(_.name)

  override def serviceName: Option[String] =
    None

  override def operationName: Option[String] =
    Some(action.name)

  override def requestSummary: Option[String] =
    Some(action.show)

  override def requestParameters: Map[String, String] = {
    val req = action.request
    val args = req.arguments.map(a => a.name -> a.value.toString).toMap
    val switches = req.switches.map(s => s.name -> s.value.toString).toMap
    val props = req.properties.map(p => p.name -> p.value.toString).toMap
    args ++ switches ++ props
  }

  override def defaultPersistence: JobPersistencePolicy =
    action match {
      case _: QueryAction => JobPersistencePolicy.Ephemeral
      case _ => JobPersistencePolicy.Persistent
    }

  def run(ctx: ExecutionContext): TaskOutcome = {
    val call = component.map(ComponentLogic(_).createActionCall(action, ctx)).getOrElse {
      val boundctx = ExecutionContext.withExecutionInvocation(
        ctx,
        ExecutionInvocationIdentity.operationSelector(
          action.request.component,
          action.request.service,
          action.request.operation
        )
      )
      val correlationid = boundctx.observability.correlationId
      val core = ActionCall.Core(action, boundctx, component, correlationid)
      action.createCall(core)
    }
    val result = component.flatMap(_.subsystem) match {
      case Some(subsystem) =>
        subsystem._with_managed_datastore_lease_c(_ => actionEngine.execute(call))
      case None =>
        actionEngine.execute(call)
    }
    result match {
      case Consequence.Success(res) =>
        TaskSucceeded(res)
      case Consequence.Failure(c) =>
        TaskFailed(c)
    }
  }
}
