package org.goldenport.cncf.event

import java.time.{Clock, Instant}
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}

/*
 * EventStore baseline for EV-02.
 *
 * - append
 * - load
 * - query
 * - replay
 *
 * Replay returns a deterministic event stream snapshot ordered by sequence.
 * The in-memory baseline retains the first record for an already-issued EventId.
 *
 * @since   Mar. 20, 2026
 *  version Jul. 16, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventStore {
  def append(records: Seq[EventRecord]): Consequence[Vector[EventRecord]]
  def load(id: EventId): Consequence[Option[EventRecord]]
  def query(q: EventStore.Query): Consequence[Vector[EventRecord]]
  def replay(q: EventStore.Query): Consequence[Vector[EventRecord]]

  final def queryVisible(
    q: EventStore.Query,
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[Vector[EventRecord]] =
    policy.authorizeIntrospection.flatMap(_ => query(q))

  final def replayAuthorized(
    q: EventStore.Query,
    policy: EventPolicyEngine = EventPolicyEngine.default
  )(using ExecutionContext): Consequence[Vector[EventRecord]] =
    policy.authorizeReplay.flatMap(_ => replay(q))

  final def replay(
    q: EventStore.Query,
    dispatch: EventRecord => Consequence[Unit]
  ): Consequence[Unit] =
    replay(q).flatMap { records =>
      records.foldLeft(Consequence.unit) { (z, record) =>
        z.flatMap(_ => dispatch(record))
      }
    }
}

enum EventLane(val value: String) {
  case Transactional extends EventLane("transactional")
  case NonTransactional extends EventLane("non-transactional")
}

final case class EventRecord(
  id: EventId,
  name: String,
  kind: String,
  payload: Map[String, Any],
  attributes: Map[String, String],
  createdAt: Instant,
  persistent: Boolean,
  status: EventRecord.Status,
  lane: EventLane,
  sequence: Long = 0L
)

object EventRecord {
  enum Status(val value: String) {
    case Stored extends Status("stored")
  }

  def fromDomainEvent(
    event: DomainEvent,
    lane: EventLane,
    factory: EventRecordFactory
  ): EventRecord =
    factory.create(event, lane)
}

final case class EventRecordFactory(
  clock: Clock,
  idGeneration: IdGenerationContext
) {
  def create(
    event: DomainEvent,
    lane: EventLane
  ): EventRecord = {
    val occurredat = _occurred_at(event)
    event match {
      case e: ReceptionDomainEvent =>
        EventRecord(
          id = _event_id(e, occurredat),
          name = e.name,
          kind = e.kind,
          payload = e.payload,
          attributes = e.attributes,
          createdAt = e.occurredAt,
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = lane
        )
      case e: TransitionLifecycleEvent =>
        EventRecord(
          id = e.id,
          name = e.name,
          kind = e.kind.value,
          payload = Map(
            "transition.event" -> e.transition.event,
            "transition.collection" -> e.transition.collection.getOrElse(""),
            "transition.targetId" -> e.transition.targetId.map(_.print).getOrElse("")
          ),
          attributes = Map(
            "traceId" -> e.correlation.traceId,
            "spanId" -> e.correlation.spanId.getOrElse(""),
            "correlationId" -> e.correlation.correlationId.getOrElse("")
          ),
          createdAt = e.occurredAt,
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = lane
        )
      case e: CommittedTransition =>
        val target = _committed_transition_target(e)
        EventRecord(
          id = e.id,
          name = e.name,
          kind = e.kind,
          payload = Map(
            "entity.id" -> e.entityId.print,
            "component.id" -> e.binding.componentId.name,
            "entity.type" -> e.binding.entityType.print,
            "machine.name" -> e.binding.machine.name,
            "machine.version" -> e.binding.version.value,
            "transition.declarationOrder" -> e.binding.transition.declarationOrder,
            "transition.source" -> e.binding.source.path.render,
            "transition.target.kind" -> target.kind,
            "transition.target" -> target.value,
            "transition.target.fallback" -> target.fallback.getOrElse(""),
            "transition.trigger" -> e.binding.trigger.name,
            "operation.id" -> e.operationId,
            "transaction.id" -> e.transactionId.print
          ),
          attributes = Map(
            "executionContextId" -> e.correlation.executionContextId.print,
            "traceId" -> e.correlation.traceId,
            "spanId" -> e.correlation.spanId.getOrElse(""),
            "correlationId" -> e.correlation.correlationId.getOrElse("")
          ),
          createdAt = e.occurredAt,
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = lane
        )
      case e: ActionEvent =>
        EventRecord(
          id = _event_id(e, occurredat),
          name = e.actionName,
          kind = e.result.toString.toLowerCase,
          payload = Map("reason" -> e.reason.getOrElse("")),
          attributes = Map("executionContextId" -> e.executionContextId.print),
          createdAt = e.occurredAt,
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = lane
        )
      case other =>
        EventRecord(
          id = _event_id(other, occurredat),
          name = other.getClass.getSimpleName,
          kind = "domain-event",
          payload = Map.empty,
          attributes = Map.empty,
          createdAt = occurredat,
          persistent = true,
          status = EventRecord.Status.Stored,
          lane = lane
        )
    }
  }

  private def _occurred_at(event: DomainEvent): Instant =
    event match {
      case e: ReceptionDomainEvent => e.occurredAt
      case e: TransitionLifecycleEvent => e.occurredAt
      case e: ActionEvent => e.occurredAt
      case _ => Instant.now(clock)
    }

  private def _event_id(
    event: DomainEvent,
    occurredat: Instant
  ): EventId =
    event match {
      case e: TransitionLifecycleEvent => e.id
      case e: ReceptionDomainEvent => EventId.create(s"record.${e.name}", occurredat, idGeneration)
      case e: ActionEvent => EventId.create(s"record.${e.actionName}", occurredat, idGeneration)
      case other => EventId.create(s"record.${other.getClass.getName}", occurredat, idGeneration)
    }

  private final case class CommittedTransitionTarget(
    kind: String,
    value: String,
    fallback: Option[String]
  )

  private def _committed_transition_target(
    event: CommittedTransition
  ): CommittedTransitionTarget =
    event.binding.target match {
      case org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget.State(state) =>
        CommittedTransitionTarget("state", state.path.render, None)
      case org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget.ShallowHistory(target) =>
        CommittedTransitionTarget(
          "shallow-history",
          target.composite.path.render,
          Some(target.fallbackLeaf.path.render)
        )
      case org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget.Final =>
        CommittedTransitionTarget("final", "", None)
    }
}

object EventRecordFactory {
  def standard: EventRecordFactory = {
    val clock = RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock
    EventRecordFactory(
      clock,
      IdGenerationContext.default(IdGenerationContext.DEFAULT_NAMESPACE, clock)
    )
  }

  def from(ctx: ExecutionContext): EventRecordFactory =
    EventRecordFactory(ctx.core.clock, ctx.idGeneration)
}

object EventStore {
  final case class Query(
    name: Option[String] = None,
    kind: Option[String] = None,
    lane: Option[EventLane] = None,
    persistent: Option[Boolean] = None,
    from: Option[Instant] = None,
    to: Option[Instant] = None,
    offset: Int = 0,
    limit: Option[Int] = None
  )

  def inMemory: EventStore = new InMemory

  private final class InMemory extends EventStore {
    private val _records = mutable.ArrayBuffer.empty[EventRecord]
    private val _index = mutable.HashMap.empty[EventId, EventRecord]
    private var _sequence = 0L

    def append(records: Seq[EventRecord]): Consequence[Vector[EventRecord]] = synchronized {
      val stored = records.toVector.map { r =>
        _index.get(r.id).getOrElse {
          _sequence = _sequence + 1
          val x = r.copy(sequence = _sequence)
          _records += x
          _index.update(x.id, x)
          x
        }
      }
      Consequence.success(stored)
    }

    def load(id: EventId): Consequence[Option[EventRecord]] =
      synchronized {
        Consequence.success(_index.get(id))
      }

    def query(q: Query): Consequence[Vector[EventRecord]] =
      synchronized {
        val filtered = _records
          .iterator
          .filter(r => q.name.forall(_ == r.name))
          .filter(r => q.kind.forall(_ == r.kind))
          .filter(r => q.lane.forall(_ == r.lane))
          .filter(r => q.persistent.forall(_ == r.persistent))
          .filter(r => q.from.forall(!r.createdAt.isBefore(_)))
          .filter(r => q.to.forall(!r.createdAt.isAfter(_)))
          .toVector
          .sortBy(_.sequence)
        val dropped = if (q.offset > 0) filtered.drop(q.offset) else filtered
        val sliced = q.limit.fold(dropped)(dropped.take)
        Consequence.success(sliced)
      }

    def replay(q: Query): Consequence[Vector[EventRecord]] =
      query(q)
  }
}
