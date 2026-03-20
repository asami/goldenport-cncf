package org.goldenport.cncf.event

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy

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
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventDispatchHandler {
  def dispatch(event: DomainEvent): Consequence[Unit]
}

trait ActionCallDispatcher {
  def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit]
}

final case class EventSubscription(
  name: String,
  eventName: Option[String] = None,
  kind: Option[String] = None,
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
  def publishAuthorized(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption(),
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[EventPublishResult]
}

object EventBus {
  def default(eventEngine: EventEngine): EventBus =
    new DefaultEventBus(eventEngine)

  def actionCallHandler(
    actionName: String,
    dispatcher: ActionCallDispatcher
  ): EventDispatchHandler =
    new EventDispatchHandler {
      def dispatch(event: DomainEvent): Consequence[Unit] =
        dispatcher.dispatchAction(actionName, event)
    }
}

final class DefaultEventBus(
  eventEngine: EventEngine
) extends EventBus {
  private case class _Entry(
    order: Long,
    subscription: EventSubscription
  )

  private val _subscriptions = mutable.ArrayBuffer.empty[_Entry]
  private var _order_seed = 0L

  def register(subscription: EventSubscription): Unit = synchronized {
    _order_seed = _order_seed + 1
    _subscriptions += _Entry(_order_seed, subscription)
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
    _publish(event, option)
  }

  def publishAuthorized(
    event: DomainEvent,
    option: EventPublishOption = EventPublishOption(),
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ctx: ExecutionContext): Consequence[EventPublishResult] =
    policy.authorizePublish.flatMap { _ =>
      policy.authorizeDispatch.flatMap { _ =>
        _publish(event, option)
      }
    }

  private def _publish(
    event: DomainEvent,
    option: EventPublishOption
  ): Consequence[EventPublishResult] = {
    val name = _event_name(event)
    val kind = _event_kind(event)
    val resolved = synchronized {
      _subscriptions
        .toVector
        .filter(e => e.subscription.eventName.forall(_ == name))
        .filter(e => e.subscription.kind.forall(_ == kind))
        .filter(e => e.subscription.persistentOnly.forall(_ == option.persistent))
        .sortBy(e => (e.subscription.priority, e.order))
    }

    val persisted = if (option.persistent) {
      eventEngine.emit(Vector(event)).map(_ => ())
    } else {
      Consequence.unit
    }

    persisted.flatMap { _ =>
      resolved.foldLeft(Consequence.success(0)) { (z, e) =>
        z.flatMap { count =>
          e.subscription.handler.dispatch(event) match {
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
      case e: TransitionLifecycleEvent => e.name
      case e: ActionEvent => e.actionName
      case other => other.getClass.getSimpleName
    }

  private def _event_kind(event: DomainEvent): String =
    event match {
      case e: TransitionLifecycleEvent => e.kind.value
      case e: ActionEvent => e.result.toString.toLowerCase
      case _ => "domain-event"
    }

  private def _dispatch_failure[A](
    subscription: EventSubscription,
    eventName: String,
    eventKind: String,
    cause: org.goldenport.Conclusion
  ): Consequence[A] =
    Consequence.fail(
      Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Invalid),
      Facet.Operation("event.dispatch"),
      Facet.Name(subscription.name),
      Facet.Message(s"event=$eventName kind=$eventKind cause=${cause.show}")
    )
}
