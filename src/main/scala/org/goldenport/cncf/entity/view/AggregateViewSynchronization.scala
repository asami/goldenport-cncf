package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.event.{EventId, EventRecord}

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AggregateCommandResult[S, E](
  newState: S,
  events: Vector[E]
)

trait AggregateCommandHandler[C, S, E] {
  def handle(command: C, state: S): Consequence[AggregateCommandResult[S, E]]
}

trait EventProjector[V] {
  def project(current: V, event: EventRecord): Consequence[V]
}

enum ProjectionFailurePolicy {
  case RetryOnce
  case Skip
  case DeadLetter
}

final case class ProjectionDeadLetter(
  eventId: EventId,
  sequence: Long,
  reason: String
)

final case class SynchronizationResult[V](
  view: V,
  appliedCount: Int,
  skippedCount: Int,
  retriedCount: Int,
  deadLetters: Vector[ProjectionDeadLetter],
  lastSequence: Option[Long]
)

/*
 * AV-03 synchronization baseline:
 * - deterministic event application order
 * - eventual consistency (command success and projection synchronization are explicit)
 * - idempotent projection for repeated/replayed events
 * - explicit failure policy (retry/skip/dead-letter)
 */
final class AggregateViewSynchronizer[V](
  initialView: V,
  projector: EventProjector[V],
  failurePolicy: ProjectionFailurePolicy = ProjectionFailurePolicy.RetryOnce
) {
  private val _seen_event_ids = mutable.HashSet.empty[String]
  private var _view: V = initialView
  private var _last_sequence: Option[Long] = None
  private var _dead_letters: Vector[ProjectionDeadLetter] = Vector.empty

  def currentView: V = _view

  def lastSequence: Option[Long] = _last_sequence

  def deadLetters: Vector[ProjectionDeadLetter] = _dead_letters

  def synchronize(events: Seq[EventRecord]): Consequence[SynchronizationResult[V]] = {
    val ordered = events.toVector.sortBy(e => (e.sequence, e.createdAt.toEpochMilli))
    var applied = 0
    var skipped = 0
    var retried = 0

    ordered.foreach { event =>
      if (_is_duplicate(event)) {
        skipped = skipped + 1
      } else {
        _apply_event(event) match {
          case _ApplyOutcome.Applied =>
            applied = applied + 1
            _remember(event)
          case _ApplyOutcome.Skipped =>
            skipped = skipped + 1
            _remember(event)
          case _ApplyOutcome.RetriedApplied =>
            retried = retried + 1
            applied = applied + 1
            _remember(event)
          case _ApplyOutcome.DeadLettered =>
            _remember(event)
        }
      }
    }

    Consequence.success(
      SynchronizationResult(
        view = _view,
        appliedCount = applied,
        skippedCount = skipped,
        retriedCount = retried,
        deadLetters = _dead_letters,
        lastSequence = _last_sequence
      )
    )
  }

  def synchronizeCommand[C, S, E](
    command: C,
    state: S,
    aggregate: AggregateCommandHandler[C, S, E],
    toEventRecord: E => EventRecord
  ): Consequence[(S, SynchronizationResult[V])] =
    aggregate.handle(command, state).flatMap { out =>
      synchronize(out.events.map(toEventRecord)).map(sync => (out.newState, sync))
    }

  private enum _ApplyOutcome {
    case Applied
    case Skipped
    case RetriedApplied
    case DeadLettered
  }

  private def _is_duplicate(event: EventRecord): Boolean =
    _seen_event_ids.contains(event.id.print)

  private def _remember(event: EventRecord): Unit = {
    _seen_event_ids.add(event.id.print)
    _last_sequence = Some(_last_sequence.fold(event.sequence)(x => math.max(x, event.sequence)))
  }

  private def _apply_event(event: EventRecord): _ApplyOutcome =
    projector.project(_view, event) match {
      case Consequence.Success(next) =>
        _view = next
        _ApplyOutcome.Applied
      case Consequence.Failure(firstError) =>
        failurePolicy match {
          case ProjectionFailurePolicy.RetryOnce =>
            projector.project(_view, event) match {
              case Consequence.Success(next) =>
                _view = next
                _ApplyOutcome.RetriedApplied
              case Consequence.Failure(secondError) =>
                _dead_letter(event, secondError.show)
                _ApplyOutcome.DeadLettered
            }
          case ProjectionFailurePolicy.Skip =>
            _ApplyOutcome.Skipped
          case ProjectionFailurePolicy.DeadLetter =>
            _dead_letter(event, firstError.show)
            _ApplyOutcome.DeadLettered
        }
    }

  private def _dead_letter(event: EventRecord, reason: String): Unit =
    _dead_letters = _dead_letters :+ ProjectionDeadLetter(
      eventId = event.id,
      sequence = event.sequence,
      reason = reason
    )
}

