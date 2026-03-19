package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.statemachine.Effect

/*
 * @since   Mar. 19, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
trait GuardBindingResolver[S, E] {
  def resolve(name: String): Consequence[Guard[S, E]]
}

trait ActionBindingResolver[S, E] {
  def resolve(name: String): Consequence[ActionEffect[S, E]]
}

final case class EffectAdapter[S, E](
  effect: Effect[S, E]
) extends ResolvedAction[S, E] {
  def run(state: S, event: E): Consequence[Unit] =
    effect.execute(state, event)
}
