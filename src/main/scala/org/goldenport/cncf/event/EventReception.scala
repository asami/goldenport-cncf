package org.goldenport.cncf.event

import java.time.Instant
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.job.{ActionId, JobEngine, JobPersistencePolicy, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.record.Record
import org.goldenport.provisional.observation.Taxonomy

/*
 * EV-06 canonical mapping:
 * - CML event definition -> reception rule
 * - reception input -> runtime domain event
 * - selective routing -> ActionCall dispatcher via EventBus subscription
 *
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
enum CmlEventCategory {
  case ActionEvent
  case NonActionEvent
}

final case class CmlEventDefinition(
  name: String,
  category: CmlEventCategory = CmlEventCategory.NonActionEvent,
  kind: Option[String] = None,
  selectors: Map[String, String] = Map.empty,
  actionName: Option[String] = None,
  priority: Int = 0
) {
  def matches(input: ReceptionInput): Boolean = {
    val kindok = kind.forall(_ == input.kind)
    val selectorok = selectors.forall { case (k, v) =>
      input.attributes.get(k).contains(v)
    }
    kindok && selectorok
  }
}

enum DispatchRoute {
  case Unicast
  case Multicast
  case Broadcast
}

final case class CmlRoutingDefinition(
  name: String,
  when: Option[String] = None,
  topic: Option[String] = None,
  service: Option[String] = None,
  partition: Option[String] = None
)

final case class CmlSubscriptionDefinition(
  name: String,
  eventName: String,
  route: DispatchRoute = DispatchRoute.Unicast,
  entityName: Option[String] = None,
  target: Option[String] = None,
  targets: Vector[String] = Vector.empty,
  selector: Option[String] = None,
  actionName: String,
  declaredTargetUpperBound: Int = 1,
  activation: Option[EntityActivationMode] = None,
  continuationMode: Option[EventContinuationMode] = None
) {
  def matches(event: ReceptionDomainEvent): Boolean =
    eventName == event.name
}

enum EventContinuationMode {
  case SameJob
  case NewJob
}

enum EventOriginBoundary {
  case SameSubsystem
  case ExternalSubsystem
}

final case class EventReceptionCondition(
  originBoundary: Option[EventOriginBoundary] = None,
  eventName: Option[String] = None,
  eventKind: Option[String] = None,
  eventCategory: Option[CmlEventCategory] = None,
  selectors: Map[String, String] = Map.empty,
  abacConditions: Vector[EventReceptionAbacCondition] = Vector.empty
) {
  def matches(
    event: ReceptionDomainEvent,
    boundary: EventOriginBoundary,
    category: Option[CmlEventCategory]
  ): Boolean = {
    val boundaryok = originBoundary.forall(_ == boundary)
    val nameok = eventName.forall(_ == event.name)
    val kindok = eventKind.forall(_ == event.kind)
    val categoryok = eventCategory.forall(x => category.contains(x))
    val selectorok = selectors.forall { case (k, v) =>
      event.attributes.get(k).contains(v)
    }
    boundaryok && nameok && kindok && categoryok && selectorok
  }

  def specificity: Int =
    Vector(
      originBoundary.map(_ => 1).getOrElse(0),
      eventName.map(_ => 1).getOrElse(0),
      eventKind.map(_ => 1).getOrElse(0),
      eventCategory.map(_ => 1).getOrElse(0),
      selectors.size,
      abacConditions.size
    ).sum
}

final case class EventReceptionAbacCondition(
  left: EventReceptionAbacOperand,
  right: EventReceptionAbacOperand,
  operator: EventReceptionAbacOperator = EventReceptionAbacOperator.Eq
) {
  def evaluate(
    ctx: ExecutionContext,
    event: ReceptionDomainEvent
  ): EventReceptionAbacEvaluation = {
    val leftValue = left.resolve(ctx, event)
    val rightValue = right.resolve(ctx, event)
    val matched = (leftValue, rightValue) match {
      case (Some(l), Some(r)) => operator.matches(l, r)
      case _ => false
    }
    EventReceptionAbacEvaluation(this, matched, leftValue, rightValue)
  }
}

final case class EventReceptionAbacEvaluation(
  condition: EventReceptionAbacCondition,
  matched: Boolean,
  leftValue: Option[String],
  rightValue: Option[String]
) {
  def summary: String =
    s"${condition.left.label}${condition.operator.symbol}${condition.right.label} actual=${leftValue.getOrElse("<missing>")} expected=${rightValue.getOrElse("<missing>")}"
}

enum EventReceptionAbacOperator {
  case Eq

  def symbol: String = this match {
    case EventReceptionAbacOperator.Eq => "="
  }

  def matches(left: String, right: String): Boolean =
    this match {
      case EventReceptionAbacOperator.Eq =>
        EventReceptionAbacOperand.normalize(left) == EventReceptionAbacOperand.normalize(right)
    }
}

sealed trait EventReceptionAbacOperand {
  def label: String
  def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String]
}

object EventReceptionAbacOperand {
  final case class SubjectAttribute(name: String) extends EventReceptionAbacOperand {
    def label: String = s"subject.$name"
    def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String] = {
      val _ = event
      ctx.security.principal.attributes.get(name).map(_.trim).filter(_.nonEmpty)
    }
  }

  case object SubjectId extends EventReceptionAbacOperand {
    def label: String = "subject.id"
    def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String] = {
      val _ = event
      Some(ctx.security.principal.id.value)
    }
  }

  final case class EventAttribute(name: String) extends EventReceptionAbacOperand {
    def label: String = s"event.attr.$name"
    def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String] = {
      val _ = ctx
      event.attributes.get(name).map(_.trim).filter(_.nonEmpty)
    }
  }

  final case class EventPayload(name: String) extends EventReceptionAbacOperand {
    def label: String = s"event.payload.$name"
    def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String] = {
      val _ = ctx
      event.payload.get(name).map(_.toString.trim).filter(_.nonEmpty)
    }
  }

  final case class Literal(value: String) extends EventReceptionAbacOperand {
    def label: String = value
    def resolve(ctx: ExecutionContext, event: ReceptionDomainEvent): Option[String] = {
      val _ = ctx
      val _ = event
      Some(value)
    }
  }

  def normalize(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)
}

enum EventExecutionTiming {
  case Sync
  case Async
}

enum EventJobRelation {
  case SameJob
  case NewJob
}

enum EventSagaRelation {
  case SameSaga
  case NewSaga
}

enum EventTransactionRelation {
  case SameTransaction
  case NewTransaction
}

enum EventFailurePolicy {
  case Fail
  case Retry
}

enum EventReceptionPolicySource {
  case ExplicitRule
  case CompatibilityMapping
  case SubsystemDefault

  def print: String = this match {
    case EventReceptionPolicySource.ExplicitRule => "explicit-rule"
    case EventReceptionPolicySource.CompatibilityMapping => "compatibility-mapping"
    case EventReceptionPolicySource.SubsystemDefault => "subsystem-default"
  }
}

final case class EventReceptionExecutionPolicy(
  timing: EventExecutionTiming,
  jobRelation: EventJobRelation,
  sagaRelation: EventSagaRelation,
  transactionRelation: EventTransactionRelation,
  failurePolicy: EventFailurePolicy
) {
  def modeName: String = {
    val timingLabel = timing match {
      case EventExecutionTiming.Sync => "sync"
      case EventExecutionTiming.Async => "async"
    }
    val jobLabel = jobRelation match {
      case EventJobRelation.SameJob => "same-job"
      case EventJobRelation.NewJob => "new-job"
    }
    val sagaLabel = sagaRelation match {
      case EventSagaRelation.SameSaga => "same-saga"
      case EventSagaRelation.NewSaga => "new-saga"
    }
    val txLabel = transactionRelation match {
      case EventTransactionRelation.SameTransaction => "same-transaction"
      case EventTransactionRelation.NewTransaction => "new-transaction"
    }
    s"$timingLabel:$jobLabel:$sagaLabel:$txLabel"
  }
}

object EventReceptionExecutionPolicy {
  val SameSubsystemDefault: EventReceptionExecutionPolicy =
    EventReceptionExecutionPolicy(
      timing = EventExecutionTiming.Sync,
      jobRelation = EventJobRelation.SameJob,
      sagaRelation = EventSagaRelation.SameSaga,
      transactionRelation = EventTransactionRelation.SameTransaction,
      failurePolicy = EventFailurePolicy.Fail
    )

  val AsyncNewJobSameSaga: EventReceptionExecutionPolicy =
    EventReceptionExecutionPolicy(
      timing = EventExecutionTiming.Async,
      jobRelation = EventJobRelation.NewJob,
      sagaRelation = EventSagaRelation.SameSaga,
      transactionRelation = EventTransactionRelation.NewTransaction,
      failurePolicy = EventFailurePolicy.Retry
    )

  val AsyncNewJobNewSaga: EventReceptionExecutionPolicy =
    EventReceptionExecutionPolicy(
      timing = EventExecutionTiming.Async,
      jobRelation = EventJobRelation.NewJob,
      sagaRelation = EventSagaRelation.NewSaga,
      transactionRelation = EventTransactionRelation.NewTransaction,
      failurePolicy = EventFailurePolicy.Retry
    )

  val AsyncSameJobSameSagaNewTransaction: EventReceptionExecutionPolicy =
    EventReceptionExecutionPolicy(
      timing = EventExecutionTiming.Async,
      jobRelation = EventJobRelation.SameJob,
      sagaRelation = EventSagaRelation.SameSaga,
      transactionRelation = EventTransactionRelation.NewTransaction,
      failurePolicy = EventFailurePolicy.Retry
    )
}

final case class EventReceptionRule(
  name: String,
  condition: EventReceptionCondition,
  policy: EventReceptionExecutionPolicy,
  priority: Int = 0
)

final case class ReceptionInput(
  name: String,
  kind: String = "domain-event",
  payload: Map[String, Any] = Map.empty,
  attributes: Map[String, String] = Map.empty,
  persistent: Boolean = false
)

final case class ReceptionDomainEvent(
  name: String,
  kind: String,
  payload: Map[String, Any],
  attributes: Map[String, String],
  occurredAt: Instant = Instant.now()
) extends DomainEvent

enum ReceptionOutcome {
  case Routed
  case Dropped
}

final case class ReceptionResult(
  outcome: ReceptionOutcome,
  dispatchedCount: Int,
  persisted: Boolean,
  reason: Option[String] = None
)

enum EntityActivationMode {
  case KeepResident
  case ActivateOnReceive
}

enum EntityEventRoute {
  case Direct
  case PubSub
}

final case class EntitySubscriptionLimit(
  maxTotalSubscriptions: Int = 1024,
  maxSubscriptionsPerEntity: Int = 256,
  maxDeclaredTargetUpperBound: Int = 100000
)

final case class EntityEventSubscription(
  entityName: String,
  route: EntityEventRoute = EntityEventRoute.Direct,
  eventName: Option[String] = None,
  kind: Option[String] = None,
  selectors: Map[String, String] = Map.empty,
  declaredTargetUpperBound: Int = 1,
  activationMode: EntityActivationMode = EntityActivationMode.ActivateOnReceive,
  targetResolver: ReceptionDomainEvent => Consequence[Vector[EntityId]],
  onEntity: (EntityId, Any, ReceptionDomainEvent) => Consequence[Unit]
) {
  def matches(event: ReceptionDomainEvent): Boolean = {
    val nameok = eventName.forall(_ == event.name)
    val kindok = kind.forall(_ == event.kind)
    val selectorok = selectors.forall { case (k, v) =>
      event.attributes.get(k).contains(v)
    }
    nameok && kindok && selectorok
  }
}

trait StateMachineEventListener {
  def onEvent(
    event: ReceptionDomainEvent,
    definitions: Vector[CmlEventDefinition]
  )(using ExecutionContext): Consequence[Unit]
}

trait DirectEventListener {
  def onEvent(
    event: ReceptionDomainEvent,
    definitions: Vector[CmlEventDefinition]
  )(using ExecutionContext): Consequence[Unit]
}

trait WorkflowEventListener {
  def onEvent(
    event: ReceptionDomainEvent,
    definitions: Vector[CmlEventDefinition]
  )(using ExecutionContext): Consequence[Unit]
}

trait EventReception {
  def register(definition: CmlEventDefinition): Unit
  def registerSubscription(subscription: CmlSubscriptionDefinition): Unit
  def registerRule(rule: EventReceptionRule): Unit
  def registerStateMachineListener(listener: StateMachineEventListener): Unit
  def registerDirectListener(listener: DirectEventListener): Unit
  def registerWorkflowListener(listener: WorkflowEventListener): Unit
  def registerEntitySubscription(subscription: EntityEventSubscription): Unit
  def definitions: Vector[CmlEventDefinition]
  def subscriptions: Vector[CmlSubscriptionDefinition]
  def rules: Vector[EventReceptionRule]
  def receive(input: ReceptionInput): Consequence[ReceptionResult]
  def receiveSecured(
    input: ReceptionInput,
    policy: EventPolicyEngine = EventPolicyEngine.default
  ): Consequence[ReceptionResult]
  def receiveAuthorized(
    input: ReceptionInput,
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[ReceptionResult]
}

object EventReception {
  object StandardAttribute {
    val EventName = "cncf.event.name"
    val EventKind = "cncf.event.kind"
    val EventOccurredAt = "cncf.event.occurredAt"
    val EventPersistent = "cncf.event.persistent"
    val EventHistory = "cncf.event.history"
    val EventHistoryCount = "cncf.event.historyCount"
    val EventHistoryFormat = "cncf.event.historyFormat"
    val EventHistoryOverflow = "cncf.event.historyOverflow"
    val ContinuationMode = "cncf.event.continuationMode"
    val IngressBoundary = "cncf.event.ingressBoundary"
    val OriginBoundary = "cncf.event.originBoundary"
    val ReceptionRuleName = "cncf.event.receptionRule"
    val ReceptionPolicy = "cncf.event.receptionPolicy"
    val PolicySource = "cncf.event.policySource"
    val FailurePolicy = "cncf.event.failurePolicy"
    val FailureDispositionBase = "cncf.event.failureDispositionBase"
    val DispatchKind = "cncf.event.dispatchKind"
    val DispatchStatus = "cncf.event.dispatchStatus"
    val TaskRelation = "cncf.event.taskRelation"
    val TransactionRelation = "cncf.event.transactionRelation"
    val SagaRelation = "cncf.event.sagaRelation"
    val SagaId = "cncf.event.sagaId"
    val Replay = "cncf.event.replay"
    val ReplayEventId = "cncf.event.replayEventId"
    val ReplaySequence = "cncf.event.replaySequence"
    val ReplayStream = "cncf.event.replayStream"

    val SourceSubsystem = "cncf.source.subsystem"
    val SourceComponent = "cncf.source.component"
    val SourceAction = "cncf.source.action"
    val TargetSubsystem = "cncf.target.subsystem"
    val TargetComponent = "cncf.target.component"

    val TraceId = "cncf.context.traceId"
    val SpanId = "cncf.context.spanId"
    val CorrelationId = "cncf.context.correlationId"

    val JobId = "cncf.context.jobId"
    val TaskId = "cncf.context.taskId"
    val ActionId = "cncf.context.actionId"
    val ParentJobId = "cncf.context.parentJobId"
    val CausationId = "cncf.context.causationId"

    val SecurityLevel = "cncf.context.securityLevel"
    val PrincipalId = "cncf.context.principalId"

    val LegacyTraceId = "traceId"
    val LegacyCorrelationId = "correlationId"
    val LegacyJobId = "jobId"
    val LegacyTaskId = "taskId"
    val LegacyActionId = "actionId"
    val LegacyParentJobId = "parentJobId"
    val LegacyCausationId = "causationId"
    val LegacyReplay = "replay"
    val LegacyReplayEventId = "replayEventId"
    val LegacyReplaySequence = "replaySequence"
    val LegacyReplayStream = "replayStream"
  }

  def default(
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySpace: Option[EntitySpace] = None,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit(),
    workingSetEntities: Set[String] = Set.empty,
    currentSubsystemName: Option[String] = None,
    currentComponentName: Option[String] = None,
    // NOTE:
    // no-match Ephemeral Job materialization requires jobEngine.
    // If jobEngine is None, no-match path remains "Dropped" only.
    // Keep this explicit to support future direct EventReception.default(...) usage.
    jobEngine: Option[JobEngine] = None,
    onNoMatch: Option[(ReceptionInput, Option[ExecutionContext]) => Consequence[Unit]] = None
  ): EventReception =
    new DefaultEventReception(
      eventBus,
      dispatcher,
      ingressSecurityResolver,
      entitySpace,
      entitySubscriptionLimit,
      workingSetEntities,
      currentSubsystemName,
      currentComponentName,
      jobEngine,
      onNoMatch
    )

  private final class DefaultEventReception(
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver,
    entitySpace: Option[EntitySpace],
    entitySubscriptionLimit: EntitySubscriptionLimit,
    workingSetEntities: Set[String],
    currentSubsystemName: Option[String],
    currentComponentName: Option[String],
    jobEngine: Option[JobEngine],
    onNoMatch: Option[(ReceptionInput, Option[ExecutionContext]) => Consequence[Unit]]
  ) extends EventReception {
    private val _definitions = ArrayBuffer.empty[CmlEventDefinition]
    private val _subscriptions = ArrayBuffer.empty[CmlSubscriptionDefinition]
    private val _rules = ArrayBuffer.empty[(EventReceptionRule, Int)]
    private val _listeners = ArrayBuffer.empty[StateMachineEventListener]
    private val _directlisteners = ArrayBuffer.empty[DirectEventListener]
    private val _workflowlisteners = ArrayBuffer.empty[WorkflowEventListener]
    private val _entitysubscriptions = ArrayBuffer.empty[EntityEventSubscription]
    private val _replay_seen_ids = scala.collection.mutable.HashSet.empty[String]
    private val _replay_last_sequence = scala.collection.mutable.HashMap.empty[String, Long]
    private val _event_history_max_size = 4096

    def register(definition: CmlEventDefinition): Unit = synchronized {
      _definitions += definition
    }

    def registerSubscription(subscription: CmlSubscriptionDefinition): Unit = synchronized {
      _validate_subscription(subscription)
      _subscriptions += subscription
      eventBus.register(
        EventSubscription(
          name = subscription.name,
          eventName = Some(subscription.eventName),
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] =
              event match {
                case reception: ReceptionDomainEvent =>
                  _dispatch_registered_subscription(subscription, reception)
                case _ =>
                  Consequence.unit
              }
          }
        )
      )
    }

    def registerRule(rule: EventReceptionRule): Unit = synchronized {
      _validate_rule(rule)
      _rules += ((rule, _rules.size))
    }

    def registerStateMachineListener(listener: StateMachineEventListener): Unit = synchronized {
      _listeners += listener
    }

    def registerDirectListener(listener: DirectEventListener): Unit = synchronized {
      _directlisteners += listener
    }

    def registerWorkflowListener(listener: WorkflowEventListener): Unit = synchronized {
      _workflowlisteners += listener
    }

    def registerEntitySubscription(subscription: EntityEventSubscription): Unit = synchronized {
      val normalized = _normalize_entity_subscription(subscription)
      _validate_entity_subscription(normalized)
      _entitysubscriptions += normalized
    }

    def definitions: Vector[CmlEventDefinition] = synchronized {
      _definitions.toVector
    }

    def subscriptions: Vector[CmlSubscriptionDefinition] = synchronized {
      _subscriptions.toVector
    }

    def rules: Vector[EventReceptionRule] = synchronized {
      _rules.toVector.map(_._1)
    }

    def receive(input: ReceptionInput): Consequence[ReceptionResult] =
      _receive(input, EventPolicyEngine.default, None)

    def receiveSecured(
      input: ReceptionInput,
      policy: EventPolicyEngine = EventPolicyEngine.default
    ): Consequence[ReceptionResult] =
      ingressSecurityResolver
        .resolve(input.attributes)
        .flatMap(x => _receive(input, policy, Some(x.executionContext)))

    def receiveAuthorized(
      input: ReceptionInput,
      policy: EventPolicyEngine = EventPolicyEngine.default
    )(using ExecutionContext): Consequence[ReceptionResult] =
      _receive(input, policy, Some(summon[ExecutionContext]))

    private def _receive(
      input: ReceptionInput,
      policy: EventPolicyEngine,
      ctx: Option[ExecutionContext]
    ): Consequence[ReceptionResult] = {
      _replay_guard(input) match {
        case Some(guarded) =>
          return Consequence.success(guarded)
        case None =>
          ()
      }
      val byname = definitions.filter(_.name == input.name)
      if (byname.isEmpty)
        _failure(s"unknown event: ${input.name}")
      else {
        val matched = byname.filter(_.matches(input))
        if (matched.isEmpty)
          _on_nomatch(input, ctx).map { _ =>
            ReceptionResult(
              outcome = ReceptionOutcome.Dropped,
              dispatchedCount = 0,
              persisted = false,
              reason = Some("non-target")
            )
          }
        else {
          val hasactionbinding =
            matched.exists(d => d.category == CmlEventCategory.ActionEvent && d.actionName.nonEmpty)
          val hasnonaction =
            matched.exists(_.category == CmlEventCategory.NonActionEvent)
          if (_requires_explicit_error_boundary(input, matched) && !_has_explicit_boundary_listener(matched))
            _failure(s"error/compensation boundary requires explicit subscription: ${input.name}/${input.kind}")
          else
          if (!hasactionbinding && !hasnonaction)
            _failure(s"subscription mismatch: ${input.name}/${input.kind}")
          else {
            val occurredat = Instant.now()
            val event = ReceptionDomainEvent(
              name = input.name,
              kind = input.kind,
              payload = input.payload,
              attributes = _effective_attributes(input, ctx, occurredat),
              occurredAt = occurredat
            )
            val option = EventPublishOption(persistent = input.persistent)
            if (_should_use_sync_inline_path(event, matched, ctx))
              _receive_sync_inline(input, event, matched, policy, ctx.get)
            else {
              _prepare_event_for_publish(event, ctx).flatMap { publishevent =>
                val publish = ctx.map { x =>
                  given ExecutionContext = x
                  eventBus.publishAuthorized(publishevent, option, policy)
                }.getOrElse {
                  eventBus.publish(publishevent, option)
                }
                publish.flatMap { r =>
                  _dispatch_to_listeners(publishevent, matched, ctx).flatMap { basecount =>
                    _dispatch_to_entity_subscriptions(publishevent, ctx).flatMap { entitycount =>
                      val count = basecount + entitycount + r.dispatchedCount
                      if (count > 0) {
                        Consequence.success(
                          ReceptionResult(
                            outcome = ReceptionOutcome.Routed,
                            dispatchedCount = count,
                            persisted = r.persisted,
                            reason = None
                          )
                        )
                      } else {
                        _on_nomatch(input, ctx).map { _ =>
                          ReceptionResult(
                            outcome = ReceptionOutcome.Dropped,
                            dispatchedCount = 0,
                            persisted = r.persisted,
                            reason = Some("non-target")
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    private def _prepare_event_for_publish(
      event: ReceptionDomainEvent,
      ctxopt: Option[ExecutionContext]
    ): Consequence[ReceptionDomainEvent] =
      _append_source_history(event).flatMap {
        case sourceevent if ctxopt.isEmpty =>
          Consequence.success(sourceevent)
        case sourceevent =>
          _decorate_event_for_persistence(sourceevent, ctxopt.get)
      }

    private def _decorate_event_for_persistence(
      event: ReceptionDomainEvent,
      ctx: ExecutionContext
    ): Consequence[ReceptionDomainEvent] =
      _resolve_first_persistent_dispatch(event, ctx).flatMap {
        case Some(prepared) => Consequence.success(prepared)
        case None => Consequence.success(event)
      }

    private def _resolve_first_persistent_dispatch(
      event: ReceptionDomainEvent,
      ctx: ExecutionContext
    ): Consequence[Option[ReceptionDomainEvent]] = {
      given ExecutionContext = ctx
      _sync_inline_receptions(ctx).foldLeft(Consequence.success(Option.empty[ReceptionDomainEvent])) { (z, reception) =>
        z.flatMap {
          case s @ Some(_) =>
            Consequence.success(s)
          case None =>
              reception._resolve_first_persistent_dispatch_local(event)
        }
      }
    }

    private def _resolve_first_persistent_dispatch_local(
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[Option[ReceptionDomainEvent]] =
      _subscriptions_snapshot()
        .filter(_.matches(event))
        .foldLeft(Consequence.success(Option.empty[ReceptionDomainEvent])) { (z, subscription) =>
          z.flatMap {
            case s @ Some(_) =>
              Consequence.success(s)
            case None if !_selector_matches(subscription.selector, event) =>
              Consequence.success(None)
            case None =>
              _resolve_first_persistent_dispatch_target(subscription, event)
          }
        }

    private def _resolve_first_persistent_dispatch_target(
      subscription: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[Option[ReceptionDomainEvent]] = {
      val targets = _resolve_subscription_targets(subscription, event).take(subscription.declaredTargetUpperBound)
      targets.headOption match {
        case Some(targetid) =>
          _resolve_execution_policy(subscription, event).flatMap { resolved =>
            _decorate_event_for_dispatch(event, subscription, targetid, resolved).map(Some(_))
          }
        case None =>
          Consequence.success(None)
      }
    }

    private def _should_use_sync_inline_path(
      event: ReceptionDomainEvent,
      matched: Vector[CmlEventDefinition],
      ctx: Option[ExecutionContext]
    ): Boolean =
      ctx.nonEmpty && {
        val bysubscription = ctx.exists { ec =>
          _sync_inline_receptions(ec)
            .flatMap(_._subscriptions_snapshot().filter(_.matches(event)))
            .exists { subscription =>
              given ExecutionContext = ec
              _resolve_execution_policy(subscription, event).toOption.exists(_.policy.timing == EventExecutionTiming.Sync)
            }
        }
        val byactionbinding =
          matched.exists(d => d.category == CmlEventCategory.ActionEvent && d.actionName.nonEmpty) &&
            _resolve_origin_boundary(event).toOption.contains(EventOriginBoundary.SameSubsystem)
        bysubscription || byactionbinding
      }

    private def _receive_sync_inline(
      input: ReceptionInput,
      event: ReceptionDomainEvent,
      matched: Vector[CmlEventDefinition],
      policy: EventPolicyEngine,
      ctx: ExecutionContext
    ): Consequence[ReceptionResult] = {
      given ExecutionContext = ctx
      policy.authorizePublish.flatMap { _ =>
        policy.authorizeDispatch.flatMap { _ =>
          _append_source_history(event).flatMap { event0 =>
            _dispatch_to_subscriptions_inline_all(event0, Some(ctx)).flatMap { case (event1, subscriptioncount) =>
              _dispatch_to_listeners(event1, matched, Some(ctx)).flatMap { listenercount =>
                _dispatch_to_entity_subscriptions(event1, Some(ctx)).flatMap { entitycount =>
                  val count = subscriptioncount + listenercount + entitycount
                  val staged =
                    if (input.persistent) {
                      ctx.runtime.unitOfWork.stageEvent(event1)
                      true
                    } else {
                      false
                    }
                  val outcome =
                    if (count > 0)
                      Consequence.success(
                        ReceptionResult(
                          outcome = ReceptionOutcome.Routed,
                          dispatchedCount = count,
                          persisted = staged,
                          reason = None
                        )
                      )
                    else
                      _on_nomatch(input, Some(ctx)).map { _ =>
                        ReceptionResult(
                          outcome = ReceptionOutcome.Dropped,
                          dispatchedCount = 0,
                          persisted = staged,
                          reason = Some("non-target")
                        )
                      }
                  if (staged && !_has_action_scope(ctx.cncfCore.scope))
                    ctx.runtime.unitOfWork.commit().flatMap(_ => outcome)
                  else
                    outcome
                }
              }
            }
          }
        }
      }
    }

    private def _on_nomatch(
      input: ReceptionInput,
      ctx: Option[ExecutionContext]
    ): Consequence[Unit] =
      onNoMatch
        .orElse(_default_nomatch_materializer)
        .map(_(input, ctx))
        .getOrElse(Consequence.unit)

    private def _default_nomatch_materializer: Option[(ReceptionInput, Option[ExecutionContext]) => Consequence[Unit]] =
      jobEngine.map { engine =>
        (input, ctxopt) =>
          val ctx = ctxopt.getOrElse(ExecutionContext.create())
          val option = JobSubmitOption(
            persistence = JobPersistencePolicy.Ephemeral,
            requestSummary = Some(s"event.reception.nomatch:${input.name}"),
            parameters = input.attributes ++ Map(
              "event.name" -> input.name,
              "event.kind" -> input.kind
            ),
            executionNotes = Vector("event no matched subscription")
          )
          engine.submit(List(_NoMatchEventTask(input)), ctx, option).map(_ => ())
      }

    private def _effective_attributes(
      input: ReceptionInput,
      ctx: Option[ExecutionContext],
      occurredat: Instant
    ): Map[String, String] = {
      val eventattrs = Map(
        StandardAttribute.EventName -> input.name,
        StandardAttribute.EventKind -> input.kind,
        StandardAttribute.EventOccurredAt -> occurredat.toString,
        StandardAttribute.EventPersistent -> input.persistent.toString
      )
      val contextattrs = ctx.map(_standard_context_attributes).getOrElse(Map.empty)
      input.attributes ++ eventattrs ++ contextattrs.filterNot { case (k, _) =>
        input.attributes.contains(k)
      }
    }

    private def _external_ingress_boundary(
      attributes: Map[String, String]
    ): Boolean =
      _read_first(attributes, Vector(StandardAttribute.IngressBoundary)).exists { raw =>
        raw.trim.equalsIgnoreCase("external-subsystem") || raw.trim.equalsIgnoreCase("external")
      }

    private def _replay_guard(
      input: ReceptionInput
    ): Option[ReceptionResult] = {
      if (!_is_replay(input.attributes))
        None
      else {
        val replayid = _replay_event_id(input.attributes)
        val replayseq = _replay_sequence(input.attributes)
        val replaystream = _replay_stream(input.attributes)
        synchronized {
          replayid match {
            case Some(id) if _replay_seen_ids.contains(id) =>
              Some(
                ReceptionResult(
                  outcome = ReceptionOutcome.Dropped,
                  dispatchedCount = 0,
                  persisted = false,
                  reason = Some("replay-duplicate")
                )
              )
            case _ =>
              val outoforder = replayseq.exists { seq =>
                _replay_last_sequence.get(replaystream).exists(last => seq <= last)
              }
              if (outoforder)
                Some(
                  ReceptionResult(
                    outcome = ReceptionOutcome.Dropped,
                    dispatchedCount = 0,
                    persisted = false,
                    reason = Some("replay-out-of-order")
                  )
                )
              else {
                replayid.foreach(_replay_seen_ids.add)
                replayseq.foreach(seq => _replay_last_sequence.update(replaystream, seq))
                None
              }
          }
        }
      }
    }

    private def _is_replay(
      attributes: Map[String, String]
    ): Boolean =
      _read_boolean(attributes, Vector(
        StandardAttribute.Replay,
        StandardAttribute.LegacyReplay
      ))

    private def _replay_event_id(
      attributes: Map[String, String]
    ): Option[String] =
      _read_first(attributes, Vector(
        StandardAttribute.ReplayEventId,
        StandardAttribute.LegacyReplayEventId
      ))

    private def _replay_sequence(
      attributes: Map[String, String]
    ): Option[Long] =
      _read_first(attributes, Vector(
        StandardAttribute.ReplaySequence,
        StandardAttribute.LegacyReplaySequence
      )).flatMap(x => scala.util.Try(x.toLong).toOption)

    private def _replay_stream(
      attributes: Map[String, String]
    ): String =
      _read_first(attributes, Vector(
        StandardAttribute.ReplayStream,
        StandardAttribute.LegacyReplayStream
      )).getOrElse("default")

    private def _read_first(
      attributes: Map[String, String],
      keys: Vector[String]
    ): Option[String] =
      keys.iterator.flatMap(attributes.get).map(_.trim).find(_.nonEmpty)

    private def _read_boolean(
      attributes: Map[String, String],
      keys: Vector[String]
    ): Boolean =
      _read_first(attributes, keys).exists { v =>
        v.equalsIgnoreCase("true") || v == "1" || v.equalsIgnoreCase("yes")
      }

    private def _requires_explicit_error_boundary(
      input: ReceptionInput,
      matched: Vector[CmlEventDefinition]
    ): Boolean = {
      val kinds = (input.kind +: matched.flatMap(_.kind)).map(_.toLowerCase)
      kinds.exists(k => k.contains("error") || k.contains("compensation"))
    }

    private def _has_explicit_boundary_listener(
      matched: Vector[CmlEventDefinition]
    ): Boolean = synchronized {
        _subscriptions.nonEmpty || _listeners.nonEmpty || _directlisteners.nonEmpty || matched.exists(_.actionName.nonEmpty)
    }

    private def _has_explicit_compatibility_mode(
      subscription: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    ): Boolean =
      subscription.continuationMode.nonEmpty || _continuation_mode_from_attributes(event.attributes).nonEmpty

    private def _standard_context_attributes(
      ctx: ExecutionContext
    ): Map[String, String] = {
      val ob = ctx.observability
      val job = ctx.jobContext
      val sourceSubsystem = _find_scope_name(ctx.cncfCore.scope, org.goldenport.cncf.context.ScopeKind.Subsystem)
        .orElse(currentSubsystemName)
      val sourceComponent = _find_scope_name(ctx.cncfCore.scope, org.goldenport.cncf.context.ScopeKind.Component)
        .orElse(currentComponentName)
      val sourceAction = _find_scope_name(ctx.cncfCore.scope, org.goldenport.cncf.context.ScopeKind.Action)
      val causationid = job.causationId
        .orElse(job.actionId.map(_.print))
        .orElse(ob.correlationId.map(_.print))
        .getOrElse("unknown")
      val pairs = Vector(
        sourceSubsystem.map(x => StandardAttribute.SourceSubsystem -> x),
        sourceComponent.map(x => StandardAttribute.SourceComponent -> x),
        sourceAction.map(x => StandardAttribute.SourceAction -> x),
        Some(StandardAttribute.TraceId -> ob.traceId.print),
        ob.spanId.map(x => StandardAttribute.SpanId -> x.print),
        ob.correlationId.map(x => StandardAttribute.CorrelationId -> x.print),
        ob.sagaId.map(x => StandardAttribute.SagaId -> x),
        job.jobId.map(x => StandardAttribute.JobId -> x.print),
        job.taskId.map(x => StandardAttribute.TaskId -> x.print),
        job.actionId.map(x => StandardAttribute.ActionId -> x.print),
        job.parentJobId.map(x => StandardAttribute.ParentJobId -> x.print),
        Some(StandardAttribute.CausationId -> causationid),
        Some(StandardAttribute.SecurityLevel -> ctx.security.level.value),
        Some(StandardAttribute.PrincipalId -> ctx.security.principal.id.value),
        Some(StandardAttribute.LegacyTraceId -> ob.traceId.print),
        ob.correlationId.map(x => StandardAttribute.LegacyCorrelationId -> x.print),
        job.jobId.map(x => StandardAttribute.LegacyJobId -> x.print),
        job.taskId.map(x => StandardAttribute.LegacyTaskId -> x.print),
        job.actionId.map(x => StandardAttribute.LegacyActionId -> x.print),
        job.parentJobId.map(x => StandardAttribute.LegacyParentJobId -> x.print),
        Some(StandardAttribute.LegacyCausationId -> causationid)
      )
      pairs.collect { case Some((k, v)) if k.nonEmpty && v.nonEmpty => k -> v }.toMap
    }

    private def _find_scope_name(
      scope: org.goldenport.cncf.context.ScopeContext,
      kind: org.goldenport.cncf.context.ScopeKind
    ): Option[String] =
      if (scope.core.kind == kind)
        Some(scope.core.name)
      else
        scope.core.parent.flatMap(_find_scope_name(_, kind))

    private def _sync_inline_receptions(
      ctx: ExecutionContext
    ): Vector[DefaultEventReception] =
      _subsystem_from_scope(ctx.cncfCore.scope)
        .map(_.eventReceptions.values.collect { case m: DefaultEventReception => m }.toVector)
        .filter(_.nonEmpty)
        .getOrElse(Vector(this))

    @annotation.tailrec
    private def _subsystem_from_scope(
      scope: org.goldenport.cncf.context.ScopeContext
    ): Option[org.goldenport.cncf.subsystem.Subsystem] =
      scope match {
        case cc: org.goldenport.cncf.component.Component.Context =>
          cc.component.subsystem
        case other =>
          other.core.parent match {
            case Some(parent) => _subsystem_from_scope(parent)
            case None => None
          }
      }

    private def _has_action_scope(
      scope: org.goldenport.cncf.context.ScopeContext
    ): Boolean =
      scope.core.kind == org.goldenport.cncf.context.ScopeKind.Action ||
        scope.core.parent.exists(_has_action_scope)

    private final case class _NoMatchEventTask(
      input: ReceptionInput
    ) extends JobTask {
      val actionId: ActionId = ActionId.generate()
      def run(ctx: ExecutionContext): TaskOutcome = {
        val _ = ctx
        val _ = input
        TaskSucceeded(OperationResponse.Scalar("event.no-match"))
      }
    }

    private def _selector(
      definition: CmlEventDefinition
    ): DomainEvent => Boolean = {
      case e: ReceptionDomainEvent =>
        definition.selectors.forall { case (k, v) =>
          e.attributes.get(k).contains(v)
        }
      case _ =>
        false
    }

    private def _failure[A](message: String): Consequence[A] =
      Consequence.operationInvalid(s"event.reception: $message")

    private def _dispatch_to_listeners(
      event: ReceptionDomainEvent,
      matched: Vector[CmlEventDefinition],
      ctxopt: Option[ExecutionContext]
    ): Consequence[Int] = {
      val actiondefs = matched.filter(_.category == CmlEventCategory.ActionEvent)
      val hasactionbinding = actiondefs.exists(_.actionName.nonEmpty)
      val listeners = _listeners_snapshot(hasactionbinding)
      val directlisteners = _direct_listeners_snapshot()
      val workflowlisteners = _workflow_listeners_snapshot()
      val resolvectx: Consequence[ExecutionContext] = ctxopt match {
        case Some(ctx) => Consequence.success(ctx)
        case None => ingressSecurityResolver.resolve(event.attributes).map(_.executionContext)
      }
      resolvectx.flatMap { ctx =>
        given ExecutionContext = ctx
        val statemachinecount = listeners.foldLeft(Consequence.success(0)) { (z, listener) =>
          z.flatMap { count =>
            listener.onEvent(event, matched).map(_ => count + 1)
          }
        }
        statemachinecount.flatMap { count0 =>
          directlisteners.foldLeft(Consequence.success(count0)) { (z, listener) =>
            z.flatMap { count =>
              listener.onEvent(event, matched).map(_ => count + 1)
            }
          }.flatMap { count1 =>
            workflowlisteners.foldLeft(Consequence.success(count1)) { (z, listener) =>
              z.flatMap { count =>
                listener.onEvent(event, matched).map(_ => count + 1)
              }
            }
          }
        }
      }
    }

    private def _listeners_snapshot(
      hasactionbinding: Boolean
    ): Vector[StateMachineEventListener] = synchronized {
      if (_listeners.nonEmpty) {
        _listeners.toVector
      } else if (hasactionbinding) {
        Vector(_ActionRouteStateMachineListener(dispatcher))
      } else {
        Vector.empty
      }
    }

    private def _direct_listeners_snapshot(): Vector[DirectEventListener] = synchronized {
      _directlisteners.toVector
    }

    private def _workflow_listeners_snapshot(): Vector[WorkflowEventListener] = synchronized {
      _workflowlisteners.toVector
    }

    private def _subscriptions_snapshot(): Vector[CmlSubscriptionDefinition] = synchronized {
      _subscriptions.toVector
    }

    private def _entity_subscriptions_snapshot(): Vector[EntityEventSubscription] = synchronized {
      _entitysubscriptions.toVector
    }

    private def _validate_subscription(
      subscription: CmlSubscriptionDefinition
    ): Unit = {
      if (subscription.declaredTargetUpperBound <= 0)
        throw new IllegalStateException(
          s"invalid declaredTargetUpperBound: ${subscription.name}/${subscription.declaredTargetUpperBound}"
        )
      subscription.route match {
        case DispatchRoute.Unicast =>
          if (subscription.selector.nonEmpty)
            throw new IllegalStateException(
              s"selector is not allowed for unicast subscription: ${subscription.name}"
            )
          if (subscription.targets.nonEmpty)
            throw new IllegalStateException(
              s"targets are not allowed for unicast subscription: ${subscription.name}"
            )
          if (subscription.target != Some("targetId"))
            throw new IllegalStateException(
              s"targetId is required for unicast subscription target: ${subscription.name}"
            )
        case DispatchRoute.Multicast =>
          if (subscription.selector.isEmpty)
            throw new IllegalStateException(
              s"selector is required for multicast subscription: ${subscription.name}"
            )
        case DispatchRoute.Broadcast =>
          if (subscription.selector.nonEmpty || subscription.target.nonEmpty || subscription.targets.nonEmpty)
            throw new IllegalStateException(
              s"selector/target/targets are not allowed for broadcast subscription: ${subscription.name}"
            )
      }
    }

    private def _validate_rule(
      rule: EventReceptionRule
    ): Unit = {
      val ambiguous = _rules.exists { case (existing, _) =>
        existing.priority == rule.priority &&
        existing.condition == rule.condition
      }
      if (ambiguous)
        throw new IllegalStateException(
          s"ambiguous event reception rule: ${rule.name}"
        )
    }

    private def _normalize_entity_subscription(
      subscription: EntityEventSubscription
    ): EntityEventSubscription = {
      val memoryresident = workingSetEntities.contains(subscription.entityName)
      if (memoryresident && subscription.route == EntityEventRoute.PubSub)
        subscription.copy(activationMode = EntityActivationMode.KeepResident)
      else
        subscription
    }

    private def _validate_entity_subscription(
      subscription: EntityEventSubscription
    ): Unit = {
      if (subscription.declaredTargetUpperBound <= 0)
        throw new IllegalStateException(
          s"invalid entity subscription declaredTargetUpperBound: ${subscription.entityName}/${subscription.declaredTargetUpperBound}"
        )
      if (subscription.declaredTargetUpperBound > entitySubscriptionLimit.maxDeclaredTargetUpperBound)
        throw new IllegalStateException(
          s"entity subscription target upper bound exceeds limit: entity=${subscription.entityName}, value=${subscription.declaredTargetUpperBound}, limit=${entitySubscriptionLimit.maxDeclaredTargetUpperBound}"
        )
      val totalafter = _entitysubscriptions.size + 1
      if (totalafter > entitySubscriptionLimit.maxTotalSubscriptions)
        throw new IllegalStateException(
          s"entity subscription total exceeds limit: value=$totalafter, limit=${entitySubscriptionLimit.maxTotalSubscriptions}"
        )
      val perentityafter = _entitysubscriptions.count(_.entityName == subscription.entityName) + 1
      if (perentityafter > entitySubscriptionLimit.maxSubscriptionsPerEntity)
        throw new IllegalStateException(
          s"entity subscription per-entity exceeds limit: entity=${subscription.entityName}, value=$perentityafter, limit=${entitySubscriptionLimit.maxSubscriptionsPerEntity}"
        )
      if (subscription.route == EntityEventRoute.PubSub && subscription.activationMode != EntityActivationMode.KeepResident)
        throw new IllegalStateException(
          s"pub/sub entity subscription requires KeepResident activation: entity=${subscription.entityName}"
        )
    }

    private def _dispatch_to_entity_subscriptions(
      event: ReceptionDomainEvent,
      ctxopt: Option[ExecutionContext]
    ): Consequence[Int] = {
      val subscriptions = _entity_subscriptions_snapshot().filter(_.matches(event))
      if (subscriptions.isEmpty)
        Consequence.success(0)
      else {
        val resolvectx: Consequence[ExecutionContext] = ctxopt match {
          case Some(ctx) => Consequence.success(ctx)
          case None => ingressSecurityResolver.resolve(event.attributes).map(_.executionContext)
        }
        resolvectx.flatMap { ctx =>
          given ExecutionContext = ctx
          subscriptions.foldLeft(Consequence.success(0)) { (z, subscription) =>
            z.flatMap { count =>
              _run_entity_subscription(subscription, event).map(count + _)
            }
          }
        }
      }
    }

    private def _dispatch_to_subscriptions(
      event: ReceptionDomainEvent,
      ctxopt: Option[ExecutionContext]
    ): Consequence[Int] = {
      val xs = _subscriptions_snapshot().filter(_.matches(event))
      if (xs.isEmpty)
        Consequence.success(0)
      else {
        val resolvectx: Consequence[ExecutionContext] = ctxopt match {
          case Some(ctx) => Consequence.success(ctx)
          case None => ingressSecurityResolver.resolve(event.attributes).map(_.executionContext)
        }
        resolvectx.flatMap { ctx =>
          given ExecutionContext = ctx
          xs.foldLeft(Consequence.success(0)) { (z, s) =>
            z.flatMap { count =>
              _dispatch_subscription(s, event).map(count + _)
            }
          }
        }
      }
    }

    private def _dispatch_to_subscriptions_inline(
      event: ReceptionDomainEvent,
      ctxopt: Option[ExecutionContext]
    ): Consequence[(ReceptionDomainEvent, Int)] = {
      val xs = _subscriptions_snapshot().filter(_.matches(event))
      if (xs.isEmpty)
        Consequence.success((event, 0))
      else {
        val resolvectx: Consequence[ExecutionContext] = ctxopt match {
          case Some(ctx) => Consequence.success(ctx)
          case None => ingressSecurityResolver.resolve(event.attributes).map(_.executionContext)
        }
        resolvectx.flatMap { ctx =>
          given ExecutionContext = ctx
          xs.foldLeft(Consequence.success((event, 0))) { (z, s) =>
            z.flatMap { case (currentevent, count) =>
              _dispatch_subscription_inline(s, currentevent).map { case (updated, delta) =>
                (updated, count + delta)
              }
            }
          }
        }
      }
    }

    private def _dispatch_to_subscriptions_inline_all(
      event: ReceptionDomainEvent,
      ctxopt: Option[ExecutionContext]
    ): Consequence[(ReceptionDomainEvent, Int)] =
      ctxopt match {
        case Some(ctx) =>
          _sync_inline_receptions(ctx).foldLeft(Consequence.success((event, 0))) { (z, reception) =>
            z.flatMap { case (currentevent, count) =>
              reception._dispatch_to_subscriptions_inline(currentevent, Some(ctx)).map { case (updated, delta) =>
                (updated, count + delta)
              }
            }
          }
        case None =>
          _dispatch_to_subscriptions_inline(event, None)
      }

    private def _dispatch_registered_subscription(
      subscription: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    ): Consequence[Unit] =
      _resolve_dispatch_execution_context(event.attributes).flatMap { security =>
        given ExecutionContext = security.executionContext
        _resolve_execution_policy(subscription, event).flatMap { resolved =>
          if (resolved.policy.timing == EventExecutionTiming.Sync &&
            resolved.policy.jobRelation == EventJobRelation.SameJob &&
            security.executionContext.jobContext.jobId.isEmpty
          )
            _dispatch_subscription_inline(subscription, event).map(_ => ())
          else
            _dispatch_subscription(subscription, event).map(_ => ())
        }
      }

    private def _resolve_dispatch_execution_context(
      attributes: Map[String, String]
    ): Consequence[org.goldenport.cncf.security.ResolvedIngressSecurity] =
      dispatcher match {
        case scoped: ScopedActionCallDispatcher =>
          ingressSecurityResolver.resolve(scoped.dispatchBaseExecutionContext(), attributes)
        case _ =>
          ingressSecurityResolver.resolve(attributes)
      }

    private def _dispatch_subscription_inline(
      s: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[(ReceptionDomainEvent, Int)] =
      if (!_selector_matches(s.selector, event))
        Consequence.success((event, 0))
      else {
        val targets = _resolve_subscription_targets(s, event)
        val bounded = targets.take(s.declaredTargetUpperBound)
        bounded.foldLeft(Consequence.success((event, 0))) { (z, targetid) =>
          z.flatMap { case (currentevent, count) =>
            _resolve_execution_policy(s, currentevent).flatMap { resolved =>
              _decorate_event_for_dispatch(currentevent, s, targetid, resolved).flatMap { dispatching =>
                _dispatch_event_action(s.actionName, dispatching, resolved).flatMap { _ =>
                  _append_dispatch_history(dispatching, "succeeded", None).map { finished =>
                    (finished, count + 1)
                  }
                }.recoverWith { conclusion =>
                  _append_dispatch_history(
                    dispatching,
                    "failed",
                    Some(conclusion.show)
                  ).flatMap(_ => Consequence.Failure(conclusion))
                }
              }
            }
          }
        }
      }

    private def _dispatch_subscription(
      s: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[Int] =
      if (!_selector_matches(s.selector, event))
        Consequence.success(0)
      else {
        val targets = _resolve_subscription_targets(s, event)
        val bounded = targets.take(s.declaredTargetUpperBound)
        bounded.foldLeft(Consequence.success(0)) { (z, targetid) =>
          z.flatMap { count =>
            _resolve_execution_policy(s, event).flatMap { resolved =>
              _decorate_event_for_dispatch(event, s, targetid, resolved).flatMap { evt =>
                _dispatch_event_action(s.actionName, evt, resolved).map(_ => count + 1)
              }
            }
          }
        }
      }

    private final case class _ResolvedExecutionPolicy(
      ruleName: String,
      policy: EventReceptionExecutionPolicy,
      boundary: EventOriginBoundary,
      compatibilityMode: EventContinuationMode,
      policySource: EventReceptionPolicySource
    )

    private def _resolve_continuation_mode(
      subscription: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    ): EventContinuationMode =
      subscription.continuationMode
        .orElse(_continuation_mode_from_attributes(event.attributes))
        .getOrElse(EventContinuationMode.SameJob)

    private def _resolve_execution_policy(
      subscription: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[_ResolvedExecutionPolicy] = {
      val category = definitions.find(_.name == event.name).map(_.category)
      _resolve_origin_boundary(event).flatMap { boundary =>
        _select_rule(event, boundary, category) match {
          case Some((rule, _)) =>
            val resolved = _ResolvedExecutionPolicy(
              ruleName = rule.name,
              policy = rule.policy,
              boundary = boundary,
              compatibilityMode = _compatibility_mode(rule.policy, boundary),
              policySource = EventReceptionPolicySource.ExplicitRule
            )
            _validate_supported_policy(resolved).map(_ => resolved)
          case None =>
            val compatibility = _resolve_continuation_mode(subscription, event)
            val explicitcompat = _has_explicit_compatibility_mode(subscription, event)
            val policy = _compatibility_policy(compatibility, boundary)
            val source =
              if (explicitcompat)
                EventReceptionPolicySource.CompatibilityMapping
              else
                EventReceptionPolicySource.SubsystemDefault
            val rulename =
              if (explicitcompat)
                s"compatibility:${_continuation_mode_name(compatibility)}"
              else
                s"default:${_origin_boundary_name(boundary)}"
            val resolved = _ResolvedExecutionPolicy(
              ruleName = rulename,
              policy = policy,
              boundary = boundary,
              compatibilityMode = compatibility,
              policySource = source
            )
            _validate_supported_policy(resolved).map(_ => resolved)
        }
      }
    }

    private def _validate_supported_policy(
      resolved: _ResolvedExecutionPolicy
    ): Consequence[Unit] =
      (resolved.policy.timing, resolved.policy.jobRelation, resolved.policy.transactionRelation) match {
        case (EventExecutionTiming.Async, EventJobRelation.SameJob, EventTransactionRelation.SameTransaction) =>
          _failure(s"unsupported event reception policy: ${resolved.policy.modeName}")
        case _ =>
          Consequence.unit
      }

    private def _resolve_origin_boundary(
      event: ReceptionDomainEvent
    ): Consequence[EventOriginBoundary] =
      event.attributes.get(StandardAttribute.SourceSubsystem) match {
        case Some(name) if currentSubsystemName.contains(name) =>
          Consequence.success(EventOriginBoundary.SameSubsystem)
        case Some(_) =>
          Consequence.success(EventOriginBoundary.ExternalSubsystem)
        case None if _external_ingress_boundary(event.attributes) =>
          Consequence.success(EventOriginBoundary.ExternalSubsystem)
        case None =>
          _failure("source subsystem is required for reception policy selection")
      }

    private def _rules_snapshot(): Vector[(EventReceptionRule, Int)] = synchronized {
      _rules.toVector
    }

    private def _select_rule(
      event: ReceptionDomainEvent,
      boundary: EventOriginBoundary,
      category: Option[CmlEventCategory]
    )(using ctx: ExecutionContext): Option[(EventReceptionRule, Int)] =
      _rules_snapshot()
        .sortBy { case (rule, declarationOrder) =>
          (-rule.condition.specificity, -rule.priority, declarationOrder)
        }
        .find { case (rule, _) =>
          if (!rule.condition.matches(event, boundary, category))
            false
          else
            _abac_matches_(rule, event)
        }

    private def _abac_matches_(
      rule: EventReceptionRule,
      event: ReceptionDomainEvent
    )(using ctx: ExecutionContext): Boolean =
      rule.condition.abacConditions.forall { condition =>
        val evaluation = condition.evaluate(ctx, event)
        if (evaluation.matched)
          true
        else {
          _emit_abac_non_match_(rule, evaluation, event)
          false
        }
      }

    private def _compatibility_policy(
      mode: EventContinuationMode,
      boundary: EventOriginBoundary
    ): EventReceptionExecutionPolicy =
      mode match {
        case EventContinuationMode.SameJob =>
          boundary match {
            case EventOriginBoundary.SameSubsystem =>
              EventReceptionExecutionPolicy.SameSubsystemDefault
            case EventOriginBoundary.ExternalSubsystem =>
              EventReceptionExecutionPolicy.AsyncNewJobSameSaga
          }
        case EventContinuationMode.NewJob =>
          EventReceptionExecutionPolicy.AsyncNewJobSameSaga
      }

    private def _compatibility_mode(
      policy: EventReceptionExecutionPolicy,
      boundary: EventOriginBoundary
    ): EventContinuationMode =
      policy.jobRelation match {
        case EventJobRelation.NewJob => EventContinuationMode.NewJob
        case EventJobRelation.SameJob =>
          boundary match {
            case EventOriginBoundary.SameSubsystem => EventContinuationMode.SameJob
            case EventOriginBoundary.ExternalSubsystem => EventContinuationMode.NewJob
          }
      }

    private def _continuation_mode_from_attributes(
      attributes: Map[String, String]
    ): Option[EventContinuationMode] =
      _read_first(attributes, Vector(StandardAttribute.ContinuationMode)).flatMap { raw =>
        raw.trim.toLowerCase match {
          case "same-job" | "samejob" => Some(EventContinuationMode.SameJob)
          case "new-job" | "newjob" => Some(EventContinuationMode.NewJob)
          case _ => None
        }
      }

    private def _continuation_mode_name(
      mode: EventContinuationMode
    ): String =
      mode match {
        case EventContinuationMode.SameJob => "same-job"
        case EventContinuationMode.NewJob => "new-job"
      }

    private def _origin_boundary_name(
      boundary: EventOriginBoundary
    ): String =
      boundary match {
        case EventOriginBoundary.SameSubsystem => "same-subsystem"
        case EventOriginBoundary.ExternalSubsystem => "external-subsystem"
      }

    private def _saga_relation_name(
      relation: EventSagaRelation
    ): String =
      relation match {
        case EventSagaRelation.SameSaga => "same-saga"
        case EventSagaRelation.NewSaga => "new-saga"
      }

    private def _dispatch_event_action(
      actionname: String,
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      val eventwithsaga = _with_saga_identity_(ctx, event, resolved)
      val dispatchctx = _dispatch_execution_context(ctx, eventwithsaga, resolved)
      jobEngine match {
        case Some(engine) if resolved.policy.jobRelation == EventJobRelation.SameJob =>
          dispatchctx.jobContext.jobId match {
            case Some(jobid) =>
              val parameters = _job_parameters(eventwithsaga, resolved)
              engine.annotateJob(
                jobid,
                parameters,
                Vector(
                  s"event continuation policy: ${resolved.policy.modeName}",
                  s"event continuation target: ${eventwithsaga.attributes.getOrElse(StandardAttribute.TargetComponent, "")}"
                )
              )
              resolved.policy.timing match {
                case EventExecutionTiming.Sync =>
                  engine.runTaskInJobSync(jobid, _DispatchActionTask(actionname, eventwithsaga), dispatchctx).map(_ => ())
                case EventExecutionTiming.Async =>
                  val enqueue = () => engine.enqueueTaskInJob(jobid, _DispatchActionTask(actionname, eventwithsaga), dispatchctx).map(_ => ())
                  if (_has_action_scope(dispatchctx.cncfCore.scope))
                    dispatchctx.runtime.unitOfWork.stagePostCommit {
                      val _ = enqueue()
                    }
                  else
                    val _ = enqueue()
                  Consequence.unit
              }
            case None if resolved.policy.timing == EventExecutionTiming.Sync =>
              dispatcher match {
                case d: SecureActionCallDispatcher =>
                  given ExecutionContext = dispatchctx
                  d.dispatchActionAuthorized(actionname, eventwithsaga)
                case _ =>
                  given ExecutionContext = dispatchctx
                  dispatcher.dispatchAction(actionname, eventwithsaga)
              }
            case None =>
              _failure(s"same-job event reception requires job context: ${resolved.policy.modeName}")
          }
        case _ =>
          resolved.policy.timing match {
            case EventExecutionTiming.Sync =>
              dispatcher match {
                case d: SecureActionCallDispatcher =>
                  given ExecutionContext = dispatchctx
                  d.dispatchActionAuthorized(actionname, eventwithsaga)
                case _ =>
                  given ExecutionContext = dispatchctx
                  dispatcher.dispatchAction(actionname, eventwithsaga)
              }
            case EventExecutionTiming.Async =>
              jobEngine match {
                case Some(engine) =>
                  val submitctx = _job_submission_context(dispatchctx, eventwithsaga, resolved)
                  val option = JobSubmitOption(
                    persistence =
                      if (_read_boolean(eventwithsaga.attributes, Vector(StandardAttribute.EventPersistent)))
                        JobPersistencePolicy.Persistent
                      else
                        JobPersistencePolicy.Ephemeral,
                    requestSummary = Some(s"event.continuation:${eventwithsaga.name}"),
                    parameters = _job_parameters(eventwithsaga, resolved),
                    executionNotes = Vector(
                      "event continuation via async reception",
                      s"event reception rule: ${resolved.ruleName}",
                      s"event reception policy: ${resolved.policy.modeName}",
                      s"event reception policy source: ${resolved.policySource.print}"
                    )
                  )
                  val submit = () => engine.submit(List(_DispatchActionTask(actionname, eventwithsaga)), submitctx, option)
                  if (_has_action_scope(dispatchctx.cncfCore.scope)) {
                    dispatchctx.runtime.unitOfWork.stagePostCommit {
                      val _ = submit()
                    }
                    Consequence.unit
                  } else {
                    submit().map(_ => ())
                  }
                case None =>
                  _failure("async event reception requires job engine")
              }
          }
      }
    }

    private def _job_parameters(
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy
    ): Map[String, String] =
      event.attributes ++ Map(
        "event.name" -> event.name,
        "event.kind" -> event.kind,
        "continuation.mode" -> _continuation_mode_name(resolved.compatibilityMode),
        "origin.boundary" -> _origin_boundary_name(resolved.boundary),
        "reception.rule" -> resolved.ruleName,
        "reception.policy" -> resolved.policy.modeName,
        "reception.policySource" -> resolved.policySource.print,
        "reception.jobRelation" -> resolved.policy.jobRelation.toString.toLowerCase(java.util.Locale.ROOT),
        "reception.taskRelation" -> _task_relation_name(resolved.policy),
        "reception.transactionRelation" -> _transaction_relation_name(resolved.policy),
        "saga.relation" -> _saga_relation_name(resolved.policy.sagaRelation),
        "saga.id" -> event.attributes.getOrElse(StandardAttribute.SagaId, ""),
        "failure.policy" -> resolved.policy.failurePolicy.toString.toLowerCase(java.util.Locale.ROOT)
      )

    private def _decorate_event_for_dispatch(
      event: ReceptionDomainEvent,
      subscription: CmlSubscriptionDefinition,
      targetid: String,
      resolved: _ResolvedExecutionPolicy
    )(using ctx: ExecutionContext): Consequence[ReceptionDomainEvent] = {
      val withSaga = _with_saga_identity_(ctx, event, resolved)
      val attrs0 = withSaga.attributes + ("targetId" -> targetid) + ("target" -> targetid)
      val attrs1 = subscription.entityName match {
        case Some(name) =>
          attrs0 ++ Map(
            "entity" -> name,
            "entityName" -> name,
            "entity_name" -> name
          )
        case None =>
          attrs0
      }
      val attrs2 = attrs1 ++ Vector(
        Some(StandardAttribute.ContinuationMode -> _continuation_mode_name(resolved.compatibilityMode)),
        Some(StandardAttribute.OriginBoundary -> _origin_boundary_name(resolved.boundary)),
        Some(StandardAttribute.ReceptionRuleName -> resolved.ruleName),
        Some(StandardAttribute.ReceptionPolicy -> resolved.policy.modeName),
        Some(StandardAttribute.PolicySource -> resolved.policySource.print),
        Some(StandardAttribute.FailurePolicy -> _failure_policy_name(resolved.policy.failurePolicy)),
        Some(StandardAttribute.FailureDispositionBase -> _failure_disposition_base_name(resolved.policy)),
        Some(StandardAttribute.DispatchKind -> _dispatch_kind_name(resolved.policy)),
        Some(StandardAttribute.DispatchStatus -> _initial_dispatch_status_name(resolved.policy)),
        Some(StandardAttribute.TaskRelation -> _task_relation_name(resolved.policy)),
        Some(StandardAttribute.TransactionRelation -> _transaction_relation_name(resolved.policy)),
        Some(StandardAttribute.SagaRelation -> _saga_relation_name(resolved.policy.sagaRelation)),
        _read_first(withSaga.attributes, Vector(StandardAttribute.SagaId, StandardAttribute.CorrelationId)).map(x => StandardAttribute.SagaId -> x),
        currentSubsystemName.filter(_.nonEmpty).map(x => StandardAttribute.TargetSubsystem -> x),
        currentComponentName.filter(_.nonEmpty).map(x => StandardAttribute.TargetComponent -> x)
      ).flatten.toMap
      _append_reception_history(withSaga.copy(attributes = attrs2))
    }

    private def _append_source_history(
      event: ReceptionDomainEvent
    ): Consequence[ReceptionDomainEvent] =
      _append_history(
        event,
        "source",
        Vector(
          "source.subsystem" -> event.attributes.getOrElse(StandardAttribute.SourceSubsystem, ""),
          "source.component" -> event.attributes.getOrElse(StandardAttribute.SourceComponent, ""),
          "source.action" -> event.attributes.getOrElse(StandardAttribute.SourceAction, ""),
          "job.id" -> event.attributes.getOrElse(StandardAttribute.JobId, ""),
          "correlation.id" -> event.attributes.getOrElse(StandardAttribute.CorrelationId, ""),
          "causation.id" -> event.attributes.getOrElse(StandardAttribute.CausationId, "")
        )
      )

    private def _append_reception_history(
      event: ReceptionDomainEvent
    ): Consequence[ReceptionDomainEvent] =
      _append_history(
        event,
        "reception",
        Vector(
          "origin.boundary" -> event.attributes.getOrElse(StandardAttribute.OriginBoundary, ""),
          "subscription.target" -> event.attributes.getOrElse("targetId", ""),
          "rule" -> event.attributes.getOrElse(StandardAttribute.ReceptionRuleName, ""),
          "policy" -> event.attributes.getOrElse(StandardAttribute.ReceptionPolicy, ""),
          "policy.source" -> event.attributes.getOrElse(StandardAttribute.PolicySource, ""),
          "target.subsystem" -> event.attributes.getOrElse(StandardAttribute.TargetSubsystem, ""),
          "target.component" -> event.attributes.getOrElse(StandardAttribute.TargetComponent, "")
        )
      )

    private def _append_dispatch_history(
      event: ReceptionDomainEvent,
      result: String,
      failure: Option[String]
    ): Consequence[ReceptionDomainEvent] =
      _append_history(
        event.copy(
          attributes = event.attributes ++ Map(
            StandardAttribute.DispatchKind -> event.attributes.getOrElse(StandardAttribute.DispatchKind, "sync-inline"),
            StandardAttribute.DispatchStatus -> result,
            StandardAttribute.FailureDispositionBase -> event.attributes.getOrElse(StandardAttribute.FailureDispositionBase, "not-applicable")
          )
        ),
        "dispatch",
        Vector(
          "mode" -> event.attributes.getOrElse(StandardAttribute.DispatchKind, "sync-inline"),
          "result" -> result,
          "failure" -> failure.getOrElse("")
        )
      )

    private def _dispatch_kind_name(
      policy: EventReceptionExecutionPolicy
    ): String =
      (policy.timing, policy.jobRelation) match {
        case (EventExecutionTiming.Sync, EventJobRelation.SameJob) => "sync-inline"
        case (EventExecutionTiming.Async, EventJobRelation.SameJob) => "async-same-job"
        case (EventExecutionTiming.Async, EventJobRelation.NewJob) => "async-new-job"
        case (EventExecutionTiming.Sync, EventJobRelation.NewJob) => "sync-new-job"
      }

    private def _initial_dispatch_status_name(
      policy: EventReceptionExecutionPolicy
    ): String =
      policy.timing match {
        case EventExecutionTiming.Sync => "pending-inline"
        case EventExecutionTiming.Async => "queued"
      }

    private def _failure_policy_name(
      policy: EventFailurePolicy
    ): String =
      policy.toString.toLowerCase(java.util.Locale.ROOT)

    private def _failure_disposition_base_name(
      policy: EventReceptionExecutionPolicy
    ): String =
      policy.timing match {
        case EventExecutionTiming.Sync => "not-applicable"
        case EventExecutionTiming.Async =>
          policy.failurePolicy match {
            case EventFailurePolicy.Fail => "terminal"
            case EventFailurePolicy.Retry => "retryable"
          }
      }

    private def _task_relation_name(
      policy: EventReceptionExecutionPolicy
    ): String =
      policy.timing match {
        case EventExecutionTiming.Sync => "separate-task"
        case EventExecutionTiming.Async => "separate-task"
      }

    private def _transaction_relation_name(
      policy: EventReceptionExecutionPolicy
    ): String =
      policy.transactionRelation match {
        case EventTransactionRelation.SameTransaction => "same-transaction"
        case EventTransactionRelation.NewTransaction => "new-transaction"
      }

    private def _append_history(
      event: ReceptionDomainEvent,
      stage: String,
      fields: Vector[(String, String)]
    ): Consequence[ReceptionDomainEvent] = {
      val entry = _serialize_history_entry(stage, fields)
      val current = event.attributes.get(StandardAttribute.EventHistory).filter(_.nonEmpty)
      val merged = current.fold(entry)(x => s"$x||$entry")
      if (merged.length > _event_history_max_size)
        _failure(s"event history exceeds cap: ${merged.length}/${_event_history_max_size}")
      else {
        val count = current.fold(1)(x => x.split("\\|\\|", -1).length + 1)
        Consequence.success(
          event.copy(
            attributes = event.attributes ++ Map(
              StandardAttribute.EventHistory -> merged,
              StandardAttribute.EventHistoryCount -> count.toString,
              StandardAttribute.EventHistoryFormat -> "delta-trail",
              StandardAttribute.EventHistoryOverflow -> "fail-fast"
            )
          )
        )
      }
    }

    private def _serialize_history_entry(
      stage: String,
      fields: Vector[(String, String)]
    ): String = {
      val body = fields.collect {
        case (k, v) if k.nonEmpty && v.nonEmpty =>
          s"${_encode_history(k)}=${_encode_history(v)}"
      }.mkString(",")
      s"${_encode_history(stage)}{$body}"
    }

    private def _encode_history(
      p: String
    ): String =
      URLEncoder.encode(Option(p).getOrElse(""), StandardCharsets.UTF_8)

    private def _job_submission_context(
      ctx: ExecutionContext,
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy
    ): ExecutionContext =
      resolved.policy.sagaRelation match {
        case EventSagaRelation.SameSaga =>
          val sagaid = event.attributes.get(StandardAttribute.SagaId).filter(_.nonEmpty).orElse(ctx.observability.sagaId)
          ExecutionContext.withObservabilityContext(
            ctx,
            ctx.observability.copy(sagaId = sagaid)
          )
        case EventSagaRelation.NewSaga =>
          val sagaid = event.attributes.get(StandardAttribute.SagaId).filter(_.nonEmpty).orElse(ctx.observability.sagaId).getOrElse(_generated_saga_boundary_(event))
          currentSubsystemName match {
            case Some(name) =>
              val observability = ctx.observability.copy(
                correlationId = Some(org.goldenport.cncf.context.CorrelationId(name, _correlation_minor_(sagaid))),
                sagaId = Some(sagaid)
              )
              ExecutionContext.withObservabilityContext(ctx, observability)
            case None =>
              ExecutionContext.withObservabilityContext(
                ctx,
                ctx.observability.copy(sagaId = Some(sagaid))
              )
          }
      }

    private def _dispatch_execution_context(
      ctx: ExecutionContext,
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy
    ): ExecutionContext = {
      val sagaid = event.attributes.get(StandardAttribute.SagaId).filter(_.nonEmpty).orElse(ctx.observability.sagaId)
      val observability = resolved.policy.sagaRelation match {
        case EventSagaRelation.SameSaga =>
          ctx.observability.copy(sagaId = sagaid)
        case EventSagaRelation.NewSaga =>
          val nextsaga = sagaid.getOrElse(_generated_saga_boundary_(event))
          ctx.observability.copy(sagaId = Some(nextsaga))
      }
      ExecutionContext.withObservabilityContext(ctx, observability)
    }

    private def _with_saga_identity_(
      ctx: ExecutionContext,
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy
    ): ReceptionDomainEvent = {
      val sagaid = resolved.policy.sagaRelation match {
        case EventSagaRelation.SameSaga =>
          event.attributes.get(StandardAttribute.SagaId)
            .orElse(ctx.observability.sagaId)
            .orElse(ctx.observability.correlationId.map(_.print))
            .getOrElse(_generated_saga_boundary_(event))
        case EventSagaRelation.NewSaga =>
          _generated_saga_boundary_(event)
      }
      val attrs = event.attributes ++ Map(
        StandardAttribute.SagaId -> sagaid,
        StandardAttribute.CorrelationId -> _correlation_value_for_saga_(ctx, event, resolved, sagaid)
      )
      event.copy(attributes = attrs)
    }

    private def _generated_saga_boundary_(
      event: ReceptionDomainEvent
    ): String =
      s"event_${event.name.replace('.', '_')}_${java.lang.Long.toUnsignedString(Instant.now().toEpochMilli, 36)}"

    private def _correlation_minor_(sagaid: String): String = {
      val normalized = Option(sagaid).getOrElse("")
        .trim
        .map { c =>
          if (c.isLetterOrDigit || c == '_') c else '_'
        }
        .mkString
      val body = if (normalized.nonEmpty) normalized else "saga"
      if (body.headOption.exists(_.isLetter))
        body
      else
        s"saga_$body"
    }

    private def _correlation_value_for_saga_(
      ctx: ExecutionContext,
      event: ReceptionDomainEvent,
      resolved: _ResolvedExecutionPolicy,
      sagaid: String
    ): String =
      resolved.policy.sagaRelation match {
        case EventSagaRelation.SameSaga =>
          event.attributes.get(StandardAttribute.CorrelationId)
            .orElse(ctx.observability.correlationId.map(_.print))
            .getOrElse(sagaid)
        case EventSagaRelation.NewSaga =>
          currentSubsystemName match {
            case Some(name) =>
              org.goldenport.cncf.context.CorrelationId(name, _correlation_minor_(sagaid)).print
            case None =>
              sagaid
          }
      }

    private def _emit_abac_non_match_(
      rule: EventReceptionRule,
      evaluation: EventReceptionAbacEvaluation,
      event: ReceptionDomainEvent
    )(using ctx: ExecutionContext): Unit = {
      val _ = ctx.observability.emitDebug(
        ctx.cncfCore.scope,
        "event.reception.abac.non-match",
        Record.data(
          "rule" -> rule.name,
          "event.name" -> event.name,
          "event.kind" -> event.kind,
          "condition" -> evaluation.summary
        )
      )
    }

    private final case class _DispatchActionTask(
      actionName: String,
      event: ReceptionDomainEvent
    ) extends JobTask {
      val actionId: ActionId = ActionId.generate()
      override val componentName: Option[String] =
        event.attributes.get(StandardAttribute.TargetComponent).filter(_.nonEmpty)
      override val serviceName: Option[String] =
        Some(actionName).map(_.split('.').toVector).collect {
          case Vector(service, _operation) => service
        }
      override val operationName: Option[String] =
        Some(actionName).map(_.split('.').toVector).collect {
          case Vector(_service, operation) => operation
        }.orElse(Some(actionName))
      def run(ctx: ExecutionContext): TaskOutcome = {
        val dispatched = _resolve_dispatch_execution_context(event.attributes).flatMap { security =>
          val base = security.executionContext
          val withobservability = ExecutionContext.withObservabilityContext(base, ctx.observability)
          given ExecutionContext = ExecutionContext.withJobContext(withobservability, ctx.jobContext)
          dispatcher match {
            case d: SecureActionCallDispatcher =>
              d.dispatchActionAuthorized(actionName, event)
            case _ =>
              dispatcher.dispatchAction(actionName, event)
          }
        }
        dispatched match {
          case Consequence.Success(_) =>
            TaskSucceeded(OperationResponse.Scalar("event.dispatched"))
          case Consequence.Failure(c) =>
            org.goldenport.cncf.job.TaskFailed(c)
        }
      }
    }

    private def _selector_matches(
      selector: Option[String],
      event: ReceptionDomainEvent
    ): Boolean =
      selector.forall { s =>
        val i = s.indexOf("=")
        if (i <= 0)
          event.attributes.contains(s.trim)
        else {
          val k = s.substring(0, i).trim
          val v = s.substring(i + 1).trim
          event.attributes.get(k).contains(v)
        }
      }

    private def _resolve_subscription_targets(
      s: CmlSubscriptionDefinition,
      event: ReceptionDomainEvent
    ): Vector[String] = {
      val explicit = if (s.targets.nonEmpty) s.targets else s.target.toVector.flatMap(_resolve_target_expr(_, event))
      s.route match {
        case DispatchRoute.Unicast =>
          _target_id_from_attributes(event).toVector
        case DispatchRoute.Multicast =>
          if (explicit.nonEmpty) explicit.distinct else _targets_from_attributes(event).distinct
        case DispatchRoute.Broadcast =>
          _targets_from_attributes(event).distinct match {
            case Vector() => Vector("*")
            case xs => xs
          }
      }
    }

    private def _resolve_target_expr(
      expr: String,
      event: ReceptionDomainEvent
    ): Vector[String] = {
      val key = expr.trim
      val normalized = key.stripPrefix("event.").stripPrefix("attributes.")
      event.attributes.get(normalized).toVector.flatMap(_split_csv)
    }

    private def _targets_from_attributes(
      event: ReceptionDomainEvent
    ): Vector[String] =
      event.attributes.get("targets").toVector.flatMap(_split_csv)

    private def _target_id_from_attributes(
      event: ReceptionDomainEvent
    ): Option[String] =
      event.attributes.get("targetId").map(_.trim).filter(_.nonEmpty)

    private def _split_csv(
      p: String
    ): Vector[String] =
      p.split(",").toVector.map(_.trim).filter(_.nonEmpty)

    private def _run_entity_subscription(
      subscription: EntityEventSubscription,
      event: ReceptionDomainEvent
    )(using ExecutionContext): Consequence[Int] =
      subscription.targetResolver(event).flatMap { ids =>
        ids.distinct.foldLeft(Consequence.success(0)) { (z, id) =>
          z.flatMap { count =>
            _resolve_entity_for_subscription(subscription, id).flatMap { entity =>
              subscription.onEntity(id, entity, event).map(_ => count + 1)
            }
          }
        }
      }

    private def _resolve_entity_for_subscription(
      subscription: EntityEventSubscription,
      id: EntityId
    ): Consequence[Any] =
      entitySpace match {
        case None =>
          _failure("entity subscription requires entity space")
        case Some(space) =>
          space.entityOption[Any](subscription.entityName) match {
            case None =>
              _failure(s"entity collection not found: ${subscription.entityName}")
            case Some(collection) =>
              subscription.activationMode match {
                case EntityActivationMode.ActivateOnReceive =>
                  collection.resolve(id).map(_.asInstanceOf[Any])
                case EntityActivationMode.KeepResident =>
                  collection.storage.memoryRealm.flatMap(_.get(id)) match {
                    case Some(entity) =>
                      Consequence.success(entity.asInstanceOf[Any])
                    case None =>
                      _failure(s"entity is not active in memory: ${subscription.entityName}/${id.print}")
                  }
              }
          }
      }
  }

  private final case class _ActionRouteStateMachineListener(
    dispatcher: ActionCallDispatcher
  ) extends StateMachineEventListener {
    def onEvent(
      event: ReceptionDomainEvent,
      definitions: Vector[CmlEventDefinition]
    )(using ctx: ExecutionContext): Consequence[Unit] = {
      val targets = definitions
        .filter(_.category == CmlEventCategory.ActionEvent)
        .flatMap(_.actionName)
      targets.foldLeft(Consequence.unit) { (z, actionname) =>
        z.flatMap { _ =>
          dispatcher match {
            case d: SecureActionCallDispatcher =>
              d.dispatchActionAuthorized(actionname, event)
            case _ =>
              dispatcher.dispatchAction(actionname, event)
          }
        }
      }
    }
  }
}
