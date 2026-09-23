package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
trait GuardBindingResolver[S, E] {
  def resolve(name: String): Consequence[Guard[S, E]]
}

trait ActionBindingResolver[S, E] {
  def resolve(name: String): Consequence[ResolvedAction[S, E]]
}
