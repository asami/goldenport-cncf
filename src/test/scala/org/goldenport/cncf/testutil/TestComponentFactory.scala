package org.goldenport.cncf.testutil

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Jan.  8, 2026
 *  version Jan. 14, 2026
 * @version Feb. 15, 2026
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
      configuration = emptyConfiguration,
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    )

  def create(
    name: String,
    protocol: Protocol,
    serviceFactoryOpt: Option[Component.ServiceFactory] = None
  ): Component = {
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val factory = new Component.Factory {
      override def serviceFactory: Component.ServiceFactory =
        serviceFactoryOpt.getOrElse(Component.ServiceFactory.empty)

      override protected def create_Components(params: ComponentCreate): Vector[Component] =
        Vector.empty

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(
          name,
          componentId,
          instanceId,
          protocol,
          this
        )
    }

    val core = Component.Core.create(
      name,
      componentId,
      instanceId,
      protocol,
      factory
    )
    val c = Component.Instance(core)
    val dummy = emptySubsystem("test")
    val params = ComponentInit(dummy, core, ComponentOrigin.Builtin)
    c.initialize(params)
  }
}
