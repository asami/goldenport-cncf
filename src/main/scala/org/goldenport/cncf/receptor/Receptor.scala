package org.goldenport.cncf.receptor

import org.goldenport.Consequence

/*
 * @since   Jan.  3, 2026
 * @version Jan.  3, 2026
 * @author  ASAMI, Tomoharu
 */
class Receptor()

case class ReceptorGroup(receptors: Vector[Receptor] = Vector.empty)

object ReceptorGroup {
  val empty = new ReceptorGroup()

  def apply(): ReceptorGroup = empty
}
