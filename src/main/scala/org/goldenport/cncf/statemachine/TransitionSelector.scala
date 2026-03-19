package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionCandidate[A](
  transition: A,
  priority: Int,
  declarationOrder: Int
)

object TransitionSelector {
  def ordered[A](candidates: Vector[TransitionCandidate[A]]): Vector[TransitionCandidate[A]] =
    candidates.sortBy(c => (c.priority, c.declarationOrder))

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
}

