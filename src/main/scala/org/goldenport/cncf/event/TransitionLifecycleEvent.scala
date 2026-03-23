package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Conclusion
import org.goldenport.cncf.context.{ExecutionContext, ExecutionContextId}
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.job.{JobId, TaskId}
import org.goldenport.cncf.statemachine.TransitionEvent

/*
 * Canonical transition lifecycle envelope for EV-01.
 *
 * @since   Mar. 20, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
enum TransitionLifecycleKind(val value: String) {
  case BeforeTransition extends TransitionLifecycleKind("before-transition")
  case AfterTransition extends TransitionLifecycleKind("after-transition")
  case TransitionFailed extends TransitionLifecycleKind("transition-failed")
}

final case class TransitionLifecycleCorrelation(
  executionContextId: ExecutionContextId,
  traceId: String,
  spanId: Option[String],
  correlationId: Option[String]
)

final case class TransitionLifecycleTransition(
  machine: Option[String],
  state: Option[String],
  event: String,
  transition: Option[String],
  collection: Option[String],
  targetId: Option[EntityId]
)

final case class TransitionLifecycleFailure(
  taxonomy: String,
  message: Option[String]
)

final case class TransitionLifecycleEvent(
  id: EventId,
  name: String,
  kind: TransitionLifecycleKind,
  occurredAt: Instant,
  correlation: TransitionLifecycleCorrelation,
  transition: TransitionLifecycleTransition,
  failure: Option[TransitionLifecycleFailure] = None,
  override val jobId: Option[JobId] = None,
  override val taskId: Option[TaskId] = None
) extends DomainEvent

object TransitionLifecycleEvent {
  private val _name = "transition.lifecycle"

  def beforeTransition(
    event: TransitionEvent,
    collection: Option[String]
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(TransitionLifecycleKind.BeforeTransition, event, collection, None)

  def afterTransition(
    event: TransitionEvent,
    collection: Option[String]
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(TransitionLifecycleKind.AfterTransition, event, collection, None)

  def transitionFailed(
    event: TransitionEvent,
    collection: Option[String],
    failure: Conclusion
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(
      TransitionLifecycleKind.TransitionFailed,
      event,
      collection,
      Some(
        TransitionLifecycleFailure(
          taxonomy = failure.observation.taxonomy.print,
          message = failure.observation.getEffectiveMessage
        )
      )
    )

  private def _create(
    kind: TransitionLifecycleKind,
    event: TransitionEvent,
    collection: Option[String],
    failure: Option[TransitionLifecycleFailure]
  )(using ctx: ExecutionContext): TransitionLifecycleEvent = {
    val ob = ctx.observability
    TransitionLifecycleEvent(
      id = EventId.generate(),
      name = _name,
      kind = kind,
      occurredAt = Instant.now(),
      correlation = TransitionLifecycleCorrelation(
        executionContextId = ExecutionContextId.generate(),
        traceId = ob.traceId.print,
        spanId = ob.spanId.map(_.print),
        correlationId = ob.correlationId.map(_.print)
      ),
      transition = TransitionLifecycleTransition(
        machine = None,
        state = None,
        event = event.name,
        transition = None,
        collection = collection,
        targetId = event.targetId
      ),
      failure = failure
    )
  }
}
