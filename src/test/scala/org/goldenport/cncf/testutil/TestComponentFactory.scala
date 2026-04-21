package org.goldenport.cncf.testutil

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver

/*
 * @since   Jan.  8, 2026
 *  version Jan. 14, 2026
 *  version Feb. 15, 2026
 * @version Apr. 15, 2026
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
    version: Option[String] = None,
    configuration: ResolvedConfiguration = emptyConfiguration
  ): Subsystem =
    Subsystem(
      name = name,
      version = version,
      configuration = configuration,
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    )

  def subsystemWithConfig(
    values: Map[String, ConfigurationValue],
    name: String = "test",
    version: Option[String] = None
  ): Subsystem =
    emptySubsystem(
      name,
      version,
      ResolvedConfiguration(
        Configuration(values),
        ConfigurationTrace.empty
      )
    )

  def create(
    name: String,
    protocol: Protocol,
    serviceFactoryOpt: Option[Component.ServiceFactory] = None,
    subsystem: Subsystem = emptySubsystem("test")
  ): Component = {
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val factory: Component.SinglePrimaryBundleFactory = new Component.SinglePrimaryBundleFactory {
      override def serviceFactory: Component.ServiceFactory =
        serviceFactoryOpt.getOrElse(Component.ServiceFactory.empty)

      override protected def create_Component(params: ComponentCreate): Component =
        new Component() {}

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
    factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin)).primary
  }
}
