package org.simplemodeling.componentframework.event

trait EventListener {
  def receive(p: Event): Unit
}
