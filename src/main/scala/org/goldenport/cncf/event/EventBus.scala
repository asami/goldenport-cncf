package org.goldenport.cncf.event

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.observation.{Cause, Taxonomy}

/*
 * EventBus baseline for EV-03.
 *
 * Dispatch policy:
 * - synchronous by default
 * - deterministic order: priority asc, then registration order
 *
 * Async/job execution is an extension point and is intentionally out of scope in EV-03.
 *
 * @since   Mar. 20, 2026
 *  version Apr. 22, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventDispatchHandler {
  def dispatch(event: DomainEvent): Consequence[Unit]

  def dispatchAuthorized(
    event: DomainEvent
  )(using ExecutionContext): Consequence[Unit] =
    dispatch(event)
}

trait ActionCallDispatcher {
  def dispatchAction(actionname: String, event: DomainEvent): Consequence[Unit]
}

trait ScopedActionCallDispatcher extends ActionCallDispatcher {
  def dispatchBaseExecutionContext(): ExecutionContext
}

final case class ParsedEventAction(
  actionName: String,
  action: Action,
  event: DomainEvent
)

trait ActionFactoryDispatcher extends ActionCallDispatcher {
  def parseValidateAction(
    actionname: String,
    event: DomainEvent
  ): Consequence[ParsedEventAction]

  def dispatchParsedAction(
    action: ParsedEventAction
  ): Consequence[Unit]

  final override def dispatchAction(
    actionname: String,
    event: DomainEvent
  ): Consequence[Unit] =
    parseValidateAction(actionname, event).flatMap(dispatchParsedAction)
}

trait SecureActionCallDispatcher extends ActionCallDispatcher {
  def dispatchActionAuthorized(
    actionname: String,
    event: DomainEvent
  )(using ExecutionContext): Consequence[Unit]
}

trait SecureActionFactoryDispatcher extends ActionFactoryDispatcher with SecureActionCallDispatcher {
  def dispatchParsedActionAuthorized(
    action: ParsedEventAction
  )(using ExecutionContext): Consequence[Unit]

  final override def dispatchActionAuthorized(
    actionname: String,
    event: DomainEvent
  )(using ExecutionContext): Consequence[Unit] =
    parseValidateAction(actionname, event).flatMap(dispatchParsedActionAuthorized)
}

final case class EventSubscription(
  name: String,
  eventName: Option[String] = None,
  kind: Option[String] = None,
  selector: Option[DomainEvent => Boolean] = None,
  priority: Int = 0,
  persistentOnly: Option[Boolean] = None,
  handler: EventDispatchHandler
)

final case class EventPublishOption(
  persistent: Boolean = false
)

final case class EventPublishResult(
  dispatchedCount: Int,
  persisted: Boolean
)

trait EventBus {
  def register(subscription: EventSubscription): Unit
  def subscriptions: Vector[EventSubscription]
  def subscriptionsVisible(
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[Vector[EventSubscription]]
  def publish(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption()
  ): Consequence[EventPublishResult]
  private[cncf] def publishRuntime(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption()
  )(using ExecutionContext): Consequence[EventPublishResult]
  def publishAuthorized(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption(),
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[EventPublishResult]
}

object EventBus {
  def default(eventengine: EventEngine): EventBus =
    new DefaultEventBus(eventengine)

  def actionCallHandler(
    actionname: String,
    dispatcher: ActionCallDispatcher
  ): EventDispatchHandler =
    new EventDispatchHandler {
      def dispatch(event: DomainEvent): Consequence[Unit] =
        dispatcher.dispatchAction(actionname, event)
    }
}

final class DefaultEventBus(
  eventengine: EventEngine
) extends EventBus {
  private case class Entry(
    order: Long,
    subscription: EventSubscription
  )

  private val _subscriptions = mutable.ArrayBuffer.empty[Entry]
  private var _order_seed = 0L

  def register(subscription: EventSubscription): Unit = synchronized {
    _order_seed = _order_seed + 1
    _subscriptions += Entry(_order_seed, subscription)
  }

  def subscriptions: Vector[EventSubscription] = synchronized {
    _subscriptions.toVector.map(_.subscription)
  }

  def subscriptionsVisible(
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ctx: ExecutionContext): Consequence[Vector[EventSubscription]] =
    policy.authorizeIntrospection.map(_ => subscriptions)

  def publish(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption()
  ): Consequence[EventPublishResult] = {
    _publish(event, option, None)
  }

  private[cncf] def publishRuntime(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption()
  )(using ctx: ExecutionContext): Consequence[EventPublishResult] =
    _publish(event, option, Some(ctx))

  def publishAuthorized(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption(),
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ctx: ExecutionContext): Consequence[EventPublishResult] =
    policy.authorizePublish.flatMap { _ =>
      policy.authorizeDispatch.flatMap { _ =>
        _publish(event, option, Some(ctx))
      }
    }

  private def _publish(
    event: DomainEvent,
    option: EventPublishOption,
    context: Option[ExecutionContext]
  ): Consequence[EventPublishResult] = {
    val name = _event_name(event)
    val kind = _event_kind(event)
    val resolved = synchronized {
      _subscriptions
        .toVector
        .filter(e => e.subscription.eventName.forall(_ == name))
        .filter(e => e.subscription.kind.forall(_ == kind))
        .filter(e => e.subscription.selector.forall(_(event)))
        .filter(e => e.subscription.persistentOnly.forall(_ == option.persistent))
        .sortBy(e => (e.subscription.priority, e.order))
    }

    val persisted = if (option.persistent) {
      context match {
        case Some(ctx) => eventengine.emit(Vector(event), EventRecordFactory.from(ctx)).map(_ => ())
        case None => eventengine.emit(Vector(event)).map(_ => ())
      }
    } else {
      Consequence.unit
    }

    persisted.flatMap { _ =>
      resolved.foldLeft(Consequence.success(0)) { (z, e) =>
        z.flatMap { count =>
          val dispatched = context match {
            case Some(ctx) =>
              given ExecutionContext = ctx
              e.subscription.handler.dispatchAuthorized(event)
            case None =>
              e.subscription.handler.dispatch(event)
          }
          dispatched match {
            case Consequence.Success(_) =>
              Consequence.success(count + 1)
            case Consequence.Failure(conclusion) =>
              _dispatch_failure(e.subscription, name, kind, conclusion)
          }
        }
      }.map(count =>
        EventPublishResult(
          dispatchedCount = count,
          persisted = option.persistent
        )
      )
    }
  }

  private def _event_name(event: DomainEvent): String =
    event match {
      case e: ReceptionDomainEvent => e.name
      case e: TransitionLifecycleEvent => e.name
      case e: ActionEvent => e.actionName
      case other => other.getClass.getSimpleName
    }

  private def _event_kind(event: DomainEvent): String =
    event match {
      case e: ReceptionDomainEvent => e.kind
      case e: TransitionLifecycleEvent => e.kind.value
      case e: ActionEvent => e.result.toString.toLowerCase
      case _ => "domain-event"
    }

  private def _dispatch_failure[A](
    subscription: EventSubscription,
    eventname: String,
    eventkind: String,
    cause: org.goldenport.Conclusion
  ): Consequence[A] =
    Consequence.operationInvalid(
      "event.dispatch",
      Cause.Kind.Inconsistency,
      Seq(
        Facet.Name(subscription.name),
        Facet.Key(eventname),
        Facet.State(eventkind),
        Facet.Message("event dispatch failed")
      ),
      previous = Some(cause)
    )
}
