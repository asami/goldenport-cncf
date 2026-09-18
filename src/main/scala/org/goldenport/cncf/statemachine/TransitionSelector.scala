package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.statemachine.{Guard, State, StateMachine, StateMachineIdentity, Transition, TransitionDecider, TransitionIdentity, TransitionSelectionOutcome}

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionCandidate[A](
  transition: A,
  priority: Int,
  declarationOrder: Int
)

object TransitionSelector {
  private val _canonical_selector_machine = StateMachineIdentity(
    "org.goldenport.cncf.statemachine.TransitionSelector"
  )
  private val _canonical_selector_state = State("canonical-selection")

  def ordered[A](candidates: Vector[TransitionCandidate[A]]): Vector[TransitionCandidate[A]] =
    candidates.sortBy(c => (c.priority, c.declarationOrder))

  def orderedCanonical[A](
    candidates: Vector[TransitionCandidate[A]]
  ): Consequence[Vector[TransitionCandidate[A]]] =
    _canonical_transitions(candidates, _ => Consequence.success(true)).flatMap { transitions =>
      val candidatesbyorder = candidates.map(x => x.declarationOrder -> x).toMap
      TransitionDecider.orderedCanonical(transitions).map { orderedtransitions =>
        orderedtransitions.flatMap(_.identity).map(identity => candidatesbyorder(identity.declarationOrder))
      }
    }

  def selectCanonical[A](
    candidates: Vector[TransitionCandidate[A]]
  )(predicate: A => Consequence[Boolean]): Consequence[Option[A]] =
    _canonical_transitions(candidates, predicate).flatMap { transitions =>
      val candidatesbyorder = candidates.map(x => x.declarationOrder -> x).toMap
      val machine = StateMachine[Unit, Unit](
        states = Vector(_canonical_selector_state),
        initial = _canonical_selector_state,
        transitions = transitions,
        stateOf = _ => _canonical_selector_state
      )
      TransitionDecider.decideCanonicalOutcome(machine, (), ()).flatMap {
        case TransitionSelectionOutcome.Selected(plan) =>
          plan.transition.identity.flatMap(identity => candidatesbyorder.get(identity.declarationOrder)) match {
            case Some(candidate) => Consequence.success(Some(candidate.transition))
            case None => Consequence.operationInvalid("canonical selection outcome is not mapped to its adapter candidate")
          }
        case TransitionSelectionOutcome.NoMatch() => Consequence.success(None)
      }
    }

  def select[A](
    candidates: Vector[TransitionCandidate[A]]
  )(predicate: A => Consequence[Boolean]): Consequence[Option[A]] = {
    val sorted = ordered(candidates)
    var i = 0
    while (i < sorted.size) {
      val candidate = sorted(i)
      predicate(candidate.transition) match {
        case Consequence.Success(true) =>
          return Consequence.success(Some(candidate.transition))
        case Consequence.Success(false) =>
          ()
        case Consequence.Failure(conclusion) =>
          return Consequence.Failure(conclusion)
      }
      i += 1
    }
    Consequence.success(None)
  }

  private def _canonical_transitions[A](
    candidates: Vector[TransitionCandidate[A]],
    predicate: A => Consequence[Boolean]
  ): Consequence[Vector[Transition[Unit, Unit]]] =
    if (candidates.exists(x => x.priority < 0 || x.declarationOrder < 0))
      Consequence.operationInvalid(
        "canonical StateMachine transition priority and declaration order must be non-negative"
      )
    else
      Consequence.success(
        candidates.map { candidate =>
          Transition[Unit, Unit](
            from = _canonical_selector_state,
            to = _canonical_selector_state,
            event = (),
            guard = Some(new CanonicalCandidateGuard(candidate.transition, predicate)),
            priority = candidate.priority,
            identity = Some(TransitionIdentity(_canonical_selector_machine, candidate.declarationOrder))
          )
        }
      )

  private final class CanonicalCandidateGuard[A](
    candidate: A,
    predicate: A => Consequence[Boolean]
  ) extends Guard[Unit, Unit] {
    def eval(state: Unit, event: Unit): Consequence[Boolean] =
      predicate(candidate)
  }
}
