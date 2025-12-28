package org.goldenport.cncf.event

/*
 * @since   Apr. 11, 2025
 * @version Apr. 11, 2025
 * @author  ASAMI, Tomoharu
 */
trait EventListener {
  def receive(p: Event): Unit
}
