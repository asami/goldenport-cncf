package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.component.{
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  ComponentInstanceMetadata
}
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Jul. 22, 2026
 * @version Aug. 6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class ComponentParameterContext private (
  val componentId: ComponentId,
  val componentInstanceId: ComponentInstanceId,
  val descriptor: ComponentDescriptor,
  assemblymetadata: ComponentInstanceMetadata
) {
  private val _assembly_metadata = assemblymetadata

  private[config] def subsystem_instance_configuration: Configuration = {
    val values = _assembly_metadata.config.map { case (key, value) =>
      key -> ConfigurationValue.StringValue(value)
    }
    Configuration(values)
  }
}

private[cncf] object ComponentParameterContext {
  def select(
    componentid: ComponentId,
    componentinstanceid: ComponentInstanceId,
    descriptors: Seq[ComponentDescriptor],
    assemblymetadata: Seq[ComponentInstanceMetadata]
  ): Consequence[ComponentParameterContext] =
    for {
      descriptor <- _select_descriptor(
        componentid,
        componentinstanceid,
        descriptors.toVector
      )
      _ <- _validate_identity(componentid, componentinstanceid, descriptor)
      metadata <- _select_metadata(
        componentid,
        componentinstanceid,
        descriptor,
        assemblymetadata.toVector
      )
    } yield new ComponentParameterContext(
      componentid,
      componentinstanceid,
      descriptor,
      metadata
    )

  private def _validate_identity(
    componentid: ComponentId,
    componentinstanceid: ComponentInstanceId,
    descriptor: ComponentDescriptor
  ): Consequence[Unit] = {
    val expected = ComponentInstanceId(componentid.name, componentinstanceid.instance)
    val runtimeidentity = expected.canonicalKey == componentinstanceid.canonicalKey
    val artifactalias = descriptor.name.exists { name =>
      descriptor.componentName.exists { componentname =>
        NamingConventions.equivalentByNormalized(componentname, componentid.name) &&
          NamingConventions.equivalentByNormalized(name, componentinstanceid.name)
      }
    }
    if (runtimeidentity || artifactalias)
      Consequence.unit
    else
      ComponentParameterDiagnostics.contextRejected(
        s"component parameter context identity mismatch: component=${componentid.name}, instance=${componentinstanceid.instance}, expected=${expected.canonicalKey}, actual=${componentinstanceid.canonicalKey}",
        componentid,
        componentinstanceid
      )
  }

  private def _select_descriptor(
    componentid: ComponentId,
    componentinstanceid: ComponentInstanceId,
    descriptors: Vector[ComponentDescriptor]
  ): Consequence[ComponentDescriptor] = {
    val candidates = descriptors.filter(_owns_component(_, componentid))
    candidates match {
      case Vector(descriptor) => Consequence.success(descriptor)
      case Vector() =>
        ComponentParameterDiagnostics.contextMissing(
          s"component parameter context descriptor is missing: component=${componentid.name}",
          componentid,
          componentinstanceid
        )
      case _ =>
        ComponentParameterDiagnostics.contextAmbiguous(
          s"component parameter context descriptor is ambiguous: component=${componentid.name}, candidates=${candidates.size}",
          componentid,
          componentinstanceid
        )
    }
  }

  private def _select_metadata(
    componentid: ComponentId,
    componentinstanceid: ComponentInstanceId,
    descriptor: ComponentDescriptor,
    assemblymetadata: Vector[ComponentInstanceMetadata]
  ): Consequence[ComponentInstanceMetadata] = {
    val roots = _descriptor_root_names(descriptor)
    val candidates = assemblymetadata.filter { metadata =>
      roots.exists(NamingConventions.equivalentByNormalized(_, metadata.componentName)) &&
        metadata.instance == componentinstanceid.instance
    }
    candidates match {
      case Vector(metadata) => Consequence.success(metadata)
      case Vector() =>
        ComponentParameterDiagnostics.contextMissing(
          s"component parameter context instance metadata is missing: component=${componentid.name}, instance=${componentinstanceid.instance}",
          componentid,
          componentinstanceid
        )
      case _ =>
        ComponentParameterDiagnostics.contextAmbiguous(
          s"component parameter context instance metadata is ambiguous: component=${componentid.name}, instance=${componentinstanceid.instance}, candidates=${candidates.size}",
          componentid,
          componentinstanceid
        )
    }
  }

  private def _owns_component(
    descriptor: ComponentDescriptor,
    componentid: ComponentId
  ): Boolean =
    (_descriptor_runtime_names(descriptor) ++ descriptor.componentlets.map(_.name))
      .exists(NamingConventions.equivalentByNormalized(_, componentid.name))

  private def _descriptor_runtime_names(
    descriptor: ComponentDescriptor
  ): Vector[String] =
    descriptor.componentName.orElse(descriptor.name).toVector

  private def _descriptor_root_names(
    descriptor: ComponentDescriptor
  ): Vector[String] =
    (descriptor.componentName.toVector ++ descriptor.name.toVector).distinct
}
