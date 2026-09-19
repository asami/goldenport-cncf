package org.goldenport.cncf.event

import java.time.Instant
import org.goldenport.Conclusion
import org.goldenport.cncf.context.{ExecutionContext, ExecutionContextId}
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.job.{JobId, TaskId}
import org.goldenport.cncf.statemachine.{CmlStateMachineTransitionTarget, CmlTransitionBinding, TransitionEvent}

/*
 * Canonical transition lifecycle envelope for EV-01.
 *
 * @since   Mar. 20, 2026
 *  version Mar. 24, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
enum TransitionLifecycleKind(val value: String) {
  case BeforeTransition extends TransitionLifecycleKind("before-transition")
  case AfterTransition extends TransitionLifecycleKind("after-transition")
  case TransitionFailed extends TransitionLifecycleKind("transition-failed")
}

enum TransitionLifecycleFailureStage(val value: String) {
  case Planning extends TransitionLifecycleFailureStage("planning")
  case Action extends TransitionLifecycleFailureStage("action")
}

/** Closed, safe classification for a transition lifecycle failure. */
enum TransitionLifecycleFailureOutcome(val value: String) {
  case Source extends TransitionLifecycleFailureOutcome("source")
  case NoMatch extends TransitionLifecycleFailureOutcome("no-match")
  case Ambiguity extends TransitionLifecycleFailureOutcome("ambiguity")
  case Guard extends TransitionLifecycleFailureOutcome("guard")
  case Action extends TransitionLifecycleFailureOutcome("action")
  case Target extends TransitionLifecycleFailureOutcome("target")
  case Persistence extends TransitionLifecycleFailureOutcome("persistence")
  case Rollback extends TransitionLifecycleFailureOutcome("rollback")
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
  targetId: Option[EntityId],
  machineVersion: Option[Int] = None,
  transitionDeclarationOrder: Option[Int] = None,
  source: Option[String] = None,
  targetKind: Option[String] = None,
  target: Option[String] = None,
  trigger: Option[String] = None,
  operationSelector: Option[String] = None
)

final case class TransitionLifecycleFailure(
  taxonomy: String,
  message: Option[String],
  stage: TransitionLifecycleFailureStage = TransitionLifecycleFailureStage.Action,
  outcome: TransitionLifecycleFailureOutcome = TransitionLifecycleFailureOutcome.Action
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
    collection: Option[String],
    binding: Option[CmlTransitionBinding] = None
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(TransitionLifecycleKind.BeforeTransition, event, collection, binding, None)

  def afterTransition(
    event: TransitionEvent,
    collection: Option[String],
    binding: Option[CmlTransitionBinding] = None
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(TransitionLifecycleKind.AfterTransition, event, collection, binding, None)

  def transitionFailed(
    event: TransitionEvent,
    collection: Option[String],
    failure: Conclusion,
    stage: TransitionLifecycleFailureStage = TransitionLifecycleFailureStage.Action,
    outcome: TransitionLifecycleFailureOutcome = TransitionLifecycleFailureOutcome.Action,
    binding: Option[CmlTransitionBinding] = None
  )(using ctx: ExecutionContext): TransitionLifecycleEvent =
    _create(
      TransitionLifecycleKind.TransitionFailed,
      event, collection, binding,
      Some(
        TransitionLifecycleFailure(
          taxonomy = failure.observation.taxonomy.print,
          // Keep the compatibility field structurally present without exporting
          // an action, guard, persistence, or provider failure's raw text.
          message = None,
          stage = stage,
          outcome = outcome
        )
      )
    )

  private def _create(
    kind: TransitionLifecycleKind,
    event: TransitionEvent,
    collection: Option[String],
    binding: Option[CmlTransitionBinding],
    failure: Option[TransitionLifecycleFailure]
  )(using ctx: ExecutionContext): TransitionLifecycleEvent = {
    val ob = ctx.observability
    val occurredat = ctx.clock.instant()
    TransitionLifecycleEvent(
      id = EventId.create("transition-lifecycle", occurredat),
      name = _name,
      kind = kind,
      occurredAt = occurredat,
      correlation = TransitionLifecycleCorrelation(
        executionContextId = ExecutionContextId.generate(),
        traceId = ob.traceId.print,
        spanId = ob.spanId.map(_.print),
        correlationId = ob.correlationId.map(_.print)
      ),
      transition = _transition(event, collection, binding),
      failure = failure
    )
  }

  private def _transition(
    event: TransitionEvent,
    collection: Option[String],
    binding: Option[CmlTransitionBinding]
  ): TransitionLifecycleTransition = {
    val target = binding.map(_target)
    TransitionLifecycleTransition(
      machine = binding.map(_.machine.name),
      state = binding.map(_.source.path.render),
      event = event.name,
      transition = binding.map(_.transition.declarationOrder.toString),
      collection = collection,
      targetId = event.targetId,
      machineVersion = binding.map(_.version.value),
      transitionDeclarationOrder = binding.map(_.transition.declarationOrder),
      source = binding.map(_.source.path.render),
      targetKind = target.map(_._1),
      target = target.map(_._2),
      trigger = binding.map(_.trigger.name),
      operationSelector = binding.flatMap { value =>
        value.operation.map { operation =>
          Vector(value.componentId.name, operation.service, operation.operation).mkString(".")
        }
      }
    )
  }

  private def _target(binding: CmlTransitionBinding): (String, String) =
    binding.target match {
      case CmlStateMachineTransitionTarget.State(state) => "state" -> state.path.render
      case CmlStateMachineTransitionTarget.ShallowHistory(target) =>
        "shallow-history" -> target.composite.path.render
      case CmlStateMachineTransitionTarget.Final => "final" -> ""
    }
}
