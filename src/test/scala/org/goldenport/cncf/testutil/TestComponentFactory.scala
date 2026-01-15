package org.goldenport.cncf.testutil

import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  8, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object TestComponentFactory {
  def create(
    name: String,
    protocol: Protocol,
    serviceFactory: Option[Component.ServiceFactory] = None
  ): Component = {
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val c = serviceFactory match {
      case Some(sf) =>
        Component.create(name, componentId, instanceId, protocol, sf)
      case None =>
        Component.create(name, componentId, instanceId, protocol)
    }
    val dummy = Subsystem("test")
    val params = ComponentInit(dummy, c.core, ComponentOrigin.Builtin)
    c.initialize(params)
  }
}
