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
 *  version Apr. 15, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object TestComponentFactory {
  private val _empty_configuration =
    ResolvedConfiguration(
      Configuration.empty,
      ConfigurationTrace.empty
    )

  def emptySubsystem(
    name: String = "test",
    version: Option[String] = None,
    configuration: ResolvedConfiguration = _empty_configuration
  ): Subsystem =
    Subsystem(
      name = name,
      version = version,
      configuration = configuration,
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    ).enableControlledTestExecution()

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

  def admittedEmptySubsystem(
    name: String = "test",
    version: Option[String] = None,
    configuration: ResolvedConfiguration = _empty_configuration
  ): Subsystem =
    RuntimeBindingAdmissionFixture.admit(emptySubsystem(name, version, configuration))

  def admittedSubsystemWithConfig(
    values: Map[String, ConfigurationValue],
    name: String = "test",
    version: Option[String] = None
  ): Subsystem =
    RuntimeBindingAdmissionFixture.admit(subsystemWithConfig(values, name, version))

  def withSubsystem[A](
    startup: SubsystemTestFixture.Startup = SubsystemTestFixture.Startup.Empty,
    params: SubsystemTestFixture.Params = SubsystemTestFixture.Params()
  )(body: Subsystem => A): A =
    SubsystemTestFixture.withSubsystem(startup, params)(body)

  def withEmptySubsystem[A](
    name: String = "test",
    version: Option[String] = None,
    configuration: ResolvedConfiguration = _empty_configuration
  )(body: Subsystem => A): A =
    withSubsystem(
      params = SubsystemTestFixture.Params(
        name = name,
        version = version,
        configuration = configuration
      )
    )(body)

  def withAdmittedSubsystem[A](
    startup: SubsystemTestFixture.Startup = SubsystemTestFixture.Startup.Empty,
    params: SubsystemTestFixture.Params = SubsystemTestFixture.Params()
  )(body: Subsystem => A): A =
    SubsystemTestFixture.withAdmittedSubsystem(startup, params)(body)

  def withAdmittedEmptySubsystem[A](
    name: String = "test",
    version: Option[String] = None,
    configuration: ResolvedConfiguration = _empty_configuration
  )(body: Subsystem => A): A =
    withAdmittedSubsystem(
      params = SubsystemTestFixture.Params(
        name = name,
        version = version,
        configuration = configuration
      )
    )(body)

  def create(
    name: String,
    protocol: Protocol,
    serviceFactoryOpt: Option[Component.ServiceFactory] = None,
    subsystem: Subsystem = emptySubsystem("test")
  ): Component = {
    val componentid = ComponentId(name)
    val instanceid = ComponentInstanceId.default(componentid)
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
          componentid,
          instanceid,
          protocol,
          this
        )
    }

    val core = Component.Core.create(
      name,
      componentid,
      instanceid,
      protocol,
      factory
    )
    factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin)).primary
  }
}
