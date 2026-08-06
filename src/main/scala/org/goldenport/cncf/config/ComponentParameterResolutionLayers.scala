package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Jul. 22, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] sealed abstract class ComponentParameterLayer private[config] (
  private[config] val _configuration: Configuration,
  private[config] val _provenance: ComponentParameterProvenance
)

private[cncf] final class ComponentPackagedParameterDefaults private (
  configuration: Configuration
) extends ComponentParameterLayer(configuration, ComponentParameterProvenance.PackagedDefault)

private[cncf] object ComponentPackagedParameterDefaults {
  def fromConfiguration(
    configuration: Configuration
  ): ComponentPackagedParameterDefaults =
    new ComponentPackagedParameterDefaults(configuration)
}

private[cncf] final class ComponentAssemblyParameterDefaults private (
  configuration: Configuration
) extends ComponentParameterLayer(configuration, ComponentParameterProvenance.AssemblyDefault)

private[cncf] object ComponentAssemblyParameterDefaults {
  def fromConfiguration(
    configuration: Configuration
  ): ComponentAssemblyParameterDefaults =
    new ComponentAssemblyParameterDefaults(configuration)
}

private[cncf] final class SubsystemComponentInstanceParameterSettings private (
  configuration: Configuration
) extends ComponentParameterLayer(configuration, ComponentParameterProvenance.SubsystemInstance)

private[cncf] object SubsystemComponentInstanceParameterSettings {
  private[config] def from_context(
    context: ComponentParameterContext
  ): SubsystemComponentInstanceParameterSettings =
    new SubsystemComponentInstanceParameterSettings(
      context.subsystem_instance_configuration
    )
}

private[cncf] final class ComponentRuntimeParameterConfiguration private (
  configuration: Configuration
) extends ComponentParameterLayer(configuration, ComponentParameterProvenance.RuntimeConfiguration)

private[cncf] object ComponentRuntimeParameterConfiguration {
  def fromConfiguration(
    configuration: Configuration
  ): ComponentRuntimeParameterConfiguration =
    new ComponentRuntimeParameterConfiguration(configuration)
}

private[cncf] final class ComponentTestParameterOverlay private (
  configuration: Configuration
) extends ComponentParameterLayer(configuration, ComponentParameterProvenance.TestOverlay)

private[cncf] object ComponentTestParameterOverlay {
  def fromDescriptor(
    descriptor: Option[RuntimeTestDescriptor]
  ): ComponentTestParameterOverlay =
    new ComponentTestParameterOverlay(
      descriptor.fold(Configuration.empty) { value =>
        Configuration(
          value.config.view.mapValues(ConfigurationValue.StringValue.apply).toMap
        )
      }
    )
}

private[cncf] final class ComponentRuntimeParameterProjection private (
  val runtimeConfiguration: ComponentRuntimeParameterConfiguration,
  val testOverlay: ComponentTestParameterOverlay
)

private[cncf] object ComponentRuntimeParameterProjection {
  def create(
    runtimeConfiguration: ResolvedConfiguration,
    testDescriptor: Option[RuntimeTestDescriptor]
  ): ComponentRuntimeParameterProjection = {
    val testoverlay = ComponentTestParameterOverlay.fromDescriptor(testDescriptor)
    val testkeys = testoverlay._configuration.values.keySet
    val runtimevalues = runtimeConfiguration.configuration.values -- testkeys
    new ComponentRuntimeParameterProjection(
      ComponentRuntimeParameterConfiguration.fromConfiguration(Configuration(runtimevalues)),
      testoverlay
    )
  }
}

private[cncf] final class ComponentParameterResolutionLayers private (
  packageddefaults: ComponentPackagedParameterDefaults,
  assemblydefaults: ComponentAssemblyParameterDefaults,
  context: ComponentParameterContext,
  runtimeconfiguration: ComponentRuntimeParameterConfiguration,
  testoverlay: ComponentTestParameterOverlay
) extends ComponentParameterResolver() {
  private val _packaged_defaults = packageddefaults
  private val _assembly_defaults = assemblydefaults
  private val _context = context
  private val _subsystem_instance =
    SubsystemComponentInstanceParameterSettings.from_context(context)
  private val _runtime_configuration = runtimeconfiguration
  private val _test_overlay = testoverlay

  private[config] def parameterLayersHighToLow: Vector[ComponentParameterLayer] =
    Vector(
      _test_overlay,
      _runtime_configuration,
      _subsystem_instance,
      _assembly_defaults,
      _packaged_defaults
    )

  protected def lookup_parameter(
    name: String
  ): Consequence[Option[ComponentParameterCandidate]] =
    Consequence.success(
      _candidate(_test_overlay, name)
        .orElse(_candidate(_runtime_configuration, name))
        .orElse(_candidate(_subsystem_instance, name))
        .orElse(_candidate(_assembly_defaults, name))
        .orElse(_candidate(_packaged_defaults, name))
    )

  private def _candidate(
    layer: ComponentParameterLayer,
    name: String
  ): Option[ComponentParameterCandidate] =
    _configuration_value(layer._configuration, name).map(ComponentParameterCandidate(_, layer._provenance))

  private def _configuration_value(
    configuration: Configuration,
    name: String
  ): Option[ConfigurationValue] =
    configuration.get(name).orElse(
      _nested_configuration_value(configuration.values, name.split('.').toList)
    )

  private def _nested_configuration_value(
    values: Map[String, ConfigurationValue],
    path: List[String]
  ): Option[ConfigurationValue] =
    path match {
      case Nil => None
      case name :: Nil => values.get(name)
      case name :: tail =>
        values.get(name).collect {
          case ConfigurationValue.ObjectValue(children) => children
        }.flatMap(_nested_configuration_value(_, tail))
    }
}

private[cncf] object ComponentParameterResolutionLayers {
  def create(
    packagedDefaults: ComponentPackagedParameterDefaults,
    assemblyDefaults: ComponentAssemblyParameterDefaults,
    context: ComponentParameterContext,
    runtimeProjection: ComponentRuntimeParameterProjection
  ): ComponentParameterResolutionLayers =
    new ComponentParameterResolutionLayers(
      packagedDefaults,
      assemblyDefaults,
      context,
      runtimeProjection.runtimeConfiguration,
      runtimeProjection.testOverlay
    )
}
