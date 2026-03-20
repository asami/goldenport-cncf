package org.goldenport.cncf.event

import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.runtime.EntitySpace
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy

/*
 * EV-06 canonical mapping:
 * - CML event definition -> reception rule
 * - reception input -> runtime domain event
 * - selective routing -> ActionCall dispatcher via EventBus subscription
 *
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
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
  activation: Option[EntityActivationMode] = None
) {
  def matches(event: ReceptionDomainEvent): Boolean =
    eventName == event.name
}

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

trait EventReception {
  def register(definition: CmlEventDefinition): Unit
  def registerSubscription(subscription: CmlSubscriptionDefinition): Unit
  def registerStateMachineListener(listener: StateMachineEventListener): Unit
  def registerDirectListener(listener: DirectEventListener): Unit
  def registerEntitySubscription(subscription: EntityEventSubscription): Unit
  def definitions: Vector[CmlEventDefinition]
  def subscriptions: Vector[CmlSubscriptionDefinition]
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
  def default(
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySpace: Option[EntitySpace] = None,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit(),
    workingSetEntities: Set[String] = Set.empty
  ): EventReception =
    new DefaultEventReception(
      eventBus,
      dispatcher,
      ingressSecurityResolver,
      entitySpace,
      entitySubscriptionLimit,
      workingSetEntities
    )

  private final class DefaultEventReception(
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver,
    entitySpace: Option[EntitySpace],
    entitySubscriptionLimit: EntitySubscriptionLimit,
    workingSetEntities: Set[String]
  ) extends EventReception {
    private val _definitions = ArrayBuffer.empty[CmlEventDefinition]
    private val _subscriptions = ArrayBuffer.empty[CmlSubscriptionDefinition]
    private val _listeners = ArrayBuffer.empty[StateMachineEventListener]
    private val _directlisteners = ArrayBuffer.empty[DirectEventListener]
    private val _entitysubscriptions = ArrayBuffer.empty[EntityEventSubscription]

    def register(definition: CmlEventDefinition): Unit = synchronized {
      _definitions += definition
    }

    def registerSubscription(subscription: CmlSubscriptionDefinition): Unit = synchronized {
      _validate_subscription(subscription)
      _subscriptions += subscription
    }

    def registerStateMachineListener(listener: StateMachineEventListener): Unit = synchronized {
      _listeners += listener
    }

    def registerDirectListener(listener: DirectEventListener): Unit = synchronized {
      _directlisteners += listener
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
      val byname = definitions.filter(_.name == input.name)
      if (byname.isEmpty)
        _failure(s"unknown event: ${input.name}")
      else {
        val matched = byname.filter(_.matches(input))
        if (matched.isEmpty)
          Consequence.success(
            ReceptionResult(
              outcome = ReceptionOutcome.Dropped,
              dispatchedCount = 0,
              persisted = false,
              reason = Some("non-target")
            )
          )
        else {
          val hasactionbinding =
            matched.exists(d => d.category == CmlEventCategory.ActionEvent && d.actionName.nonEmpty)
          val hasnonaction =
            matched.exists(_.category == CmlEventCategory.NonActionEvent)
          if (!hasactionbinding && !hasnonaction)
            _failure(s"subscription mismatch: ${input.name}/${input.kind}")
          else {
            val event = ReceptionDomainEvent(
              name = input.name,
              kind = input.kind,
              payload = input.payload,
              attributes = input.attributes
            )
            val option = EventPublishOption(persistent = input.persistent)
            val publish = ctx.map { x =>
              given ExecutionContext = x
              eventBus.publishAuthorized(event, option, policy)
            }.getOrElse {
              eventBus.publish(event, option)
            }
            publish.flatMap { r =>
              _dispatch_to_listeners(event, matched, ctx).flatMap { basecount =>
                _dispatch_to_entity_subscriptions(event, ctx).flatMap { entitycount =>
                  _dispatch_to_subscriptions(event, ctx).map { subscriptioncount =>
                    val count = basecount + entitycount + subscriptioncount
                    ReceptionResult(
                      outcome = if (count > 0) ReceptionOutcome.Routed else ReceptionOutcome.Dropped,
                      dispatchedCount = count,
                      persisted = r.persisted,
                      reason = if (count > 0) None else Some("non-target")
                    )
                  }
                }
              }
            }
          }
        }
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
      Consequence.fail(
        Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Invalid),
        Facet.Operation("event.reception"),
        Facet.Message(message)
      )

    private def _dispatch_to_listeners(
      event: ReceptionDomainEvent,
      matched: Vector[CmlEventDefinition],
      ctxopt: Option[ExecutionContext]
    ): Consequence[Int] = {
      val actiondefs = matched.filter(_.category == CmlEventCategory.ActionEvent)
      val hasactionbinding = actiondefs.exists(_.actionName.nonEmpty)
      val listeners = _listeners_snapshot(hasactionbinding)
      val directlisteners = _direct_listeners_snapshot()
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
            val attrs0 = event.attributes + ("targetId" -> targetid) + ("target" -> targetid)
            val attrs1 = s.entityName match {
              case Some(name) =>
                attrs0 ++ Map(
                  "entity" -> name,
                  "entityName" -> name,
                  "entity_name" -> name
                )
              case None =>
                attrs0
            }
            val evt = event.copy(attributes = attrs1)
            dispatcher match {
              case d: SecureActionCallDispatcher =>
                d.dispatchActionAuthorized(s.actionName, evt).map(_ => count + 1)
              case _ =>
                dispatcher.dispatchAction(s.actionName, evt).map(_ => count + 1)
            }
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
