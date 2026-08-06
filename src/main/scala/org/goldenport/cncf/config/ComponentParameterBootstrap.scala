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
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentParameterBootstrap {
  def resolve(
    create: ComponentCreate,
    componentId: ComponentId,
    instanceId: ComponentInstanceId,
    declarations: Seq[ComponentParameterKey[?]],
    pathRoutes: Seq[ComponentParameterPathRoute]
  ): Consequence[ComponentInitializationParameters] = {
    val keys = declarations.toVector
    val routes = pathRoutes.toVector
    val prepared =
      if (keys.isEmpty && routes.isEmpty) {
        Consequence.success(None)
      } else {
        for {
          context <- ComponentParameterContext.select(
            componentId,
            instanceId,
            create.componentDescriptors,
            _assembly_metadata(create, instanceId)
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
          pathschema <- ComponentParameterPathSchemaRegistration.registerC(routes, layers, keys)
        } yield Some(layers -> pathschema)
      }
    val observationkeys = prepared.toOption.flatten.fold(keys) { case (_, pathschema) =>
      keys ++ pathschema.declarations
    }
    val result = prepared.flatMap {
      case None =>
        Consequence.success(ComponentInitializationParameters.empty)
      case Some((layers, pathschema)) =>
        for {
          parameters <- ComponentInitializationParameters.create(keys, layers)
        } yield parameters.withPathParameters(pathschema.parameters)
    }
    ComponentParameterBootstrapObservation.record(
      componentId,
      instanceId,
      observationkeys,
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
