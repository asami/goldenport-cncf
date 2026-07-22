package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.component.{
  ComponentCreate,
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  ComponentInstanceMetadata
}
import org.goldenport.cncf.observability.ComponentParameterBootstrapObservation

/*
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentParameterBootstrap {
  def resolve(
    create: ComponentCreate,
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    declarations: Seq[ComponentParameterKey[?]]
  ): Consequence[ComponentInitializationParameters] = {
    val keys = declarations.toVector
    val result =
      if (keys.isEmpty) {
        Consequence.success(ComponentInitializationParameters.empty)
      } else {
        for {
          context <- ComponentParameterContext.select(
            componentid,
            instanceid,
            create.componentDescriptors,
            _assembly_metadata(create, instanceid)
          )
          testdescriptor <- RuntimeTestDescriptor.load(create.subsystem.configuration)
          runtimeprojection = ComponentRuntimeParameterProjection.create(
            create.subsystem.configuration,
            testdescriptor
          )
          layers = ComponentParameterResolutionLayers.create(
            ComponentPackagedParameterDefaults.fromConfiguration(
              _configuration(context.descriptor.config)
            ),
            ComponentAssemblyParameterDefaults.fromConfiguration(
              _configuration(create.subsystem.descriptor.fold(Map.empty[String, String])(_.config))
            ),
            context,
            runtimeprojection
          )
          parameters <- ComponentInitializationParameters.create(keys, layers)
        } yield parameters
      }
    ComponentParameterBootstrapObservation.record(
      componentid,
      instanceid,
      keys,
      result
    )
    result
  }

  private def _assembly_metadata(
    create: ComponentCreate,
    instanceid: ComponentInstanceId
  ): Vector[ComponentInstanceMetadata] =
    create.instanceMetadata.toVector match {
      case values if values.nonEmpty => values
      case _ =>
        create.componentDescriptors.flatMap { descriptor =>
          _descriptor_root_name(descriptor).map { name =>
            ComponentInstanceMetadata(name, instanceid.instance)
          }
        }
    }

  private def _descriptor_root_name(
    descriptor: ComponentDescriptor
  ): Option[String] =
    descriptor.componentName.orElse(descriptor.name)

  private def _configuration(values: Map[String, String]): Configuration =
    Configuration(
      values.view.mapValues(ConfigurationValue.StringValue.apply).toMap
    )
}
