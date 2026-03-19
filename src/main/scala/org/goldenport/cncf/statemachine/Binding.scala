package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait Guard[S, E] {
  def eval(state: S, event: E): Consequence[Boolean]
}

trait ActionEffect[S, E] {
  def run(state: S, event: E): Consequence[Unit]
}

trait GuardBindingResolver[S, E] {
  def resolve(name: String): Consequence[Guard[S, E]]
}

trait ActionBindingResolver[S, E] {
  def resolve(name: String): Consequence[ActionEffect[S, E]]
}

