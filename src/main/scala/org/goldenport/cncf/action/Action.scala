package org.goldenport.cncf.action

/*
 * @since   Apr. 12, 2025
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
trait Action {
  def name: String
}

abstract class Command(
  val name: String
) extends Action {
}

abstract class Query(
  val name: String
) extends Action {
}
