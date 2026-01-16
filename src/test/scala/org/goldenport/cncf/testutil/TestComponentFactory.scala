package org.goldenport.cncf.testutil

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan.  8, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object TestComponentFactory {
  private val emptyConfiguration =
    ResolvedConfiguration(
      Configuration.empty,
      ConfigurationTrace.empty
    )

  def emptySubsystem(
    name: String = "test",
    version: Option[String] = None
  ): Subsystem =
    Subsystem(
      name = name,
      version = version,
      configuration = emptyConfiguration
    )

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
    val dummy = emptySubsystem("test")
    val params = ComponentInit(dummy, c.core, ComponentOrigin.Builtin)
    c.initialize(params)
  }
}
