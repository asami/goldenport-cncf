package org.goldenport.cncf.job

import org.goldenport.cncf.event.EventTypeId

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobPlan(
  jobId: JobId,
  expected: Set[ExpectedEvent],
  policy: Option[JobPolicy] = None
)

final case class ExpectedEvent(
  kind: ExpectedEventKind,
  matcher: EventMatcher,
  multiplicity: Multiplicity = Multiplicity.ExactlyOnce,
  taskId: Option[TaskId] = None
)

sealed trait ExpectedEventKind

object ExpectedEventKind {
  case object TaskCompletion extends ExpectedEventKind
  case object DomainFact extends ExpectedEventKind
  case object ExternalSignal extends ExpectedEventKind
}

final case class EventMatcher(
  eventType: EventTypeId,
  correlation: CorrelationPredicate
)

sealed trait CorrelationPredicate

object CorrelationPredicate {
  case object Any extends CorrelationPredicate
  final case class Equals(
    name: String,
    value: String
  ) extends CorrelationPredicate
}

sealed trait Multiplicity

object Multiplicity {
  case object ExactlyOnce extends Multiplicity
  case object AtLeastOnce extends Multiplicity
  final case class Times(
    n: Int
  ) extends Multiplicity
}

sealed trait JobPolicy
