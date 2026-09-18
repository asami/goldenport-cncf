package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.cncf.context.{ExecutionContext, ExecutionContextId}
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
    val occurredat = ctx.clock.instant()
    val observability = ctx.observability
    CommittedTransition(
      id = EventId.create("committed-transition", occurredat),
      entityId = entityId,
      binding = binding,
      operationId = operationId,
      transactionId = transactionId,
      occurredAt = occurredat,
      correlation = TransitionLifecycleCorrelation(
        executionContextId = ExecutionContextId.create("committed-transition", occurredat),
        traceId = observability.traceId.print,
        spanId = observability.spanId.map(_.print),
        correlationId = observability.correlationId.map(_.print)
      )
    )
  }
}
