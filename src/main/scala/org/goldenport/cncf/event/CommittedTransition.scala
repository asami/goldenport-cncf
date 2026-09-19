package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.cncf.context.{ExecutionContext, ExecutionContextId, ExecutionInvocationIdentity}
import org.goldenport.cncf.statemachine.CmlTransitionBinding
import org.goldenport.cncf.unitofwork.TransactionContext.TransactionContextId
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Sep. 18, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
/*
 * A post-commit fact for one explicitly CML-bound transition.  It contains
 * identity and correlation only; mutable entity records and mutation inputs
 * deliberately remain outside this public event boundary.
 */
final case class CommittedTransition(
  id: EventId,
  entityId: EntityId,
  binding: CmlTransitionBinding,
  operationId: String,
  transactionId: TransactionContextId,
  occurredAt: Instant,
  correlation: TransitionLifecycleCorrelation
) extends DomainEvent {
  require(operationId.trim.nonEmpty, "Committed transition operation ID must be nonempty")
  require(
    entityId.collection == binding.entityType,
    "Committed transition entity must belong to its bound entity type"
  )

  val name: String = CommittedTransition.Name
  val kind: String = CommittedTransition.Kind
  private[event] var _delivery_lookup_key: Option[CommittedDeliveryLookupKey] = None
}

object CommittedTransition {
  val Name = "transition.committed"
  val Kind = "committed-transition"

  def create(
    entityId: EntityId,
    binding: CmlTransitionBinding,
    operationId: String,
    transactionId: TransactionContextId
  )(using ctx: ExecutionContext): CommittedTransition = {
    pending(entityId, binding, operationId, None).deliver(transactionId)
  }

  /**
   * Issues the committed-transition occurrence before post-commit delivery.
   * The optional lookup discriminator deduplicates an explicitly keyed replay;
   * it is never an EventId input or a persisted event payload/attribute.
   */
  def pending(
    entityId: EntityId,
    binding: CmlTransitionBinding,
    operationId: String,
    invocation: Option[ExecutionInvocationIdentity]
  )(using ctx: ExecutionContext): PendingCommittedTransition = {
    val occurredat = ctx.clock.instant()
    val observability = ctx.observability
    val key = invocation.filter(_.explicit).map { value =>
      CommittedDeliveryLookupKey(value.key, entityId, binding, operationId)
    }
    val transition = PendingCommittedTransition(
      id = EventId.create("committed-transition", occurredat),
      entityId = entityId,
      binding = binding,
      operationId = operationId,
      occurredAt = occurredat,
      correlation = TransitionLifecycleCorrelation(
        executionContextId = ExecutionContextId.create("committed-transition", occurredat),
        traceId = observability.traceId.print,
        spanId = observability.spanId.map(_.print),
        correlationId = observability.correlationId.map(_.print)
      )
    )
    transition._delivery_lookup_key = key
    transition
  }
}

/** Internal, typed EventStore lookup discriminator for explicit delivery replay. */
private[event] final case class CommittedDeliveryLookupKey(
  invocationKey: String,
  entityId: EntityId,
  binding: CmlTransitionBinding,
  operationId: String
)

/** A pre-issued occurrence that gains transaction identity only at delivery. */
final case class PendingCommittedTransition private[event] (
  id: EventId,
  entityId: EntityId,
  binding: CmlTransitionBinding,
  operationId: String,
  occurredAt: Instant,
  correlation: TransitionLifecycleCorrelation
) {
  private[event] var _delivery_lookup_key: Option[CommittedDeliveryLookupKey] = None

  def deliver(
    transactionid: TransactionContextId
  ): CommittedTransition = {
    val transition = CommittedTransition(
      id = id,
      entityId = entityId,
      binding = binding,
      operationId = operationId,
      transactionId = transactionid,
      occurredAt = occurredAt,
      correlation = correlation
    )
    transition._delivery_lookup_key = _delivery_lookup_key
    transition
  }
}
