package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, ComponentIdentityCompatibilityAdapter, SubsystemCapabilityId}
import org.goldenport.cncf.component.repository.ComponentRepository

/*
 * Descriptor-only admission for component-style subsystem requirements.
 *
 * This runs before a Subsystem, repository, component class loader, SPI
 * resolver, datastore, or job runtime is created.  It intentionally accepts
 * only the dedicated typed provider declarations; component instance metadata
 * is not a source of subsystem capability authority.
 *
 * @since   Jul. 31, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object SubsystemAssemblyAdmission {
  final case class Assignment(
    component: String,
    requirement: SubsystemCapabilityId,
    provider: String
  )

  final case class Report(assignments: Vector[Assignment]) {
    lazy val providerRequirements: Map[String, Vector[SubsystemCapabilityId]] =
      assignments.groupMap(_.provider)(_.requirement).view.mapValues(_.distinct.sortBy(_.canonical)).toMap
  }

  /**
   * Completes the static descriptor closure before admitting style
   * requirements. Explicit descriptor overrides are authoritative for their
   * component; otherwise the first configured repository that exposes a
   * static descriptor supplies the binding. Bindings are already canonical
   * namespace/id/version declarations before static descriptor discovery.
   * No repository is built here.
   */
  def resolveC(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[GenericSubsystemDescriptor] =
    _admit_component_bindings_c(descriptor).flatMap { admitted =>
      _discover_static_descriptors_c(admitted, repositories).flatMap { discovered =>
        val discovereddescriptor =
          if (discovered.isEmpty) admitted
          else admitted.copy(componentDescriptorOverrides = discovered.map(_.descriptor).distinct)
        if (_requires_descriptor_closure(discovereddescriptor))
          _resolve_descriptors_c(discovereddescriptor, repositories).flatMap { descriptors =>
            val resolved = admitted.copy(componentDescriptorOverrides = descriptors.map(_.descriptor).distinct)
            verifyC(resolved).map(_ => resolved)
          }
        else
          verifyC(discovereddescriptor).map(_ => discovereddescriptor)
      }
    }

  def verifyC(descriptor: GenericSubsystemDescriptor): Consequence[Unit] = {
    evaluateC(descriptor).map(_ => ())
  }

  def evaluateC(descriptor: GenericSubsystemDescriptor): Consequence[Report] = {
    val componentnames = descriptor.componentBindings.map(_.runtimeComponentName).toSet
    val unknownprovider = descriptor.subsystemCapabilityProviders.collectFirst {
      case provider if !componentnames.contains(GenericSubsystemDescriptor.runtimeComponentName(provider.component)) => provider
    }
    unknownprovider match {
      case Some(provider) =>
        Consequence.resourceInvalid(
          s"subsystem capability provider '${provider.name}' declares unknown component '${provider.component}'"
        )
      case None =>
        _resolve_requirements_c(descriptor).map(Report.apply)
    }
  }

  private def _requires_descriptor_closure(
    descriptor: GenericSubsystemDescriptor
  ): Boolean =
    descriptor.subsystemCapabilityProviders.nonEmpty ||
      descriptor.componentDescriptorOverrides.exists(_.componentStyleSnapshot.nonEmpty)

  private def _admit_component_bindings_c(
    descriptor: GenericSubsystemDescriptor
  ): Consequence[GenericSubsystemDescriptor] =
    descriptor.componentBindings.collectFirst {
      case binding if binding.componentId.isEmpty => binding
    } match {
      case Some(binding) =>
        Consequence.componentInvalid(
          s"component assembly binding requires canonical namespace/id/version: " +
            s"alias=${binding.componentName}; required=canonical namespace/id/version"
        )
      case None =>
        GenericSubsystemDescriptor._validate_component_bindings_c(descriptor.componentBindings)
          .map(validated => descriptor.copy(componentBindings = validated))
    }

  private def _resolve_descriptors_c(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[Vector[ComponentIdentityCompatibilityAdapter.DescriptorProjection]] =
    descriptor.componentBindings.foldLeft(Consequence.success(Vector.empty[ComponentIdentityCompatibilityAdapter.DescriptorProjection])) { (z, binding) =>
      z.flatMap { descriptors =>
        _resolve_descriptor_c(descriptor, binding, repositories).map(descriptors :+ _)
      }
    }

  private def _discover_static_descriptors_c(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[Vector[ComponentIdentityCompatibilityAdapter.DescriptorProjection]] =
    descriptor.componentBindings.foldLeft(Consequence.success(Vector.empty[ComponentIdentityCompatibilityAdapter.DescriptorProjection])) { (z, binding) =>
      z.flatMap { descriptors =>
        _discover_static_descriptor_c(descriptor, binding, repositories)
          .map(_.map(descriptors :+ _).getOrElse(descriptors))
      }
    }

  private def _discover_static_descriptor_c(
    descriptor: GenericSubsystemDescriptor,
    binding: GenericSubsystemComponentBinding,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[Option[ComponentIdentityCompatibilityAdapter.DescriptorProjection]] = {
    val overrides = descriptor.componentDescriptorOverrides.filter(_claims_binding(_, binding))
    overrides match {
      case Vector(value) =>
        _project_descriptor_c(binding, value).map(Some(_))
      case Vector() =>
        repositories.iterator
          .flatMap { repository =>
            _descriptor_lookup_names(binding).iterator
              .map(repository.resolveStaticComponentDescriptor)
          }
          .collectFirst { case Some(value) => value }
          .map(_project_descriptor_c(binding, _).map(Some(_)))
          .getOrElse(Consequence.success(None))
      case xs =>
        Consequence.resourceInvalid(
          s"component descriptor closure is ambiguous for binding '${binding.runtimeComponentName}': ${xs.size} explicit descriptors"
        )
    }
  }

  private def _descriptor_lookup_names(binding: GenericSubsystemComponentBinding): Vector[String] =
    binding.componentId.map(id => Vector(id.name)).getOrElse(Vector.empty)

  private def _resolve_descriptor_c(
    descriptor: GenericSubsystemDescriptor,
    binding: GenericSubsystemComponentBinding,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[ComponentIdentityCompatibilityAdapter.DescriptorProjection] =
    _discover_static_descriptor_c(descriptor, binding, repositories).flatMap {
      _.map(Consequence.success)
        .getOrElse(
          Consequence.resourceInvalid(
            s"component descriptor closure is missing binding '${binding.runtimeComponentName}'"
          )
        )
    }

  private def _claims_binding(
    descriptor: ComponentDescriptor,
    binding: GenericSubsystemComponentBinding
  ): Boolean =
    (for {
      componentid <- binding.componentId
      release <- binding.componentVersion
    } yield descriptor.requireCanonicalIdentityC.toOption.contains(componentid -> release)).getOrElse(false)

  private def _project_descriptor_c(
    binding: GenericSubsystemComponentBinding,
    descriptor: ComponentDescriptor
  ): Consequence[ComponentIdentityCompatibilityAdapter.DescriptorProjection] =
    (binding.componentId, binding.componentVersion) match {
      case (Some(componentid), Some(release)) =>
        descriptor.requireCanonicalIdentityC.flatMap { identity =>
          if (identity == componentid -> release)
            ComponentIdentityCompatibilityAdapter.projectDescriptorC(descriptor, componentid)
          else
            Consequence.componentInvalid(
              s"component descriptor closure canonical binding mismatch: expected=${componentid.name}:$release, " +
                s"actual=${identity._1.name}:${identity._2}"
            )
        }
      case (None, _) =>
        Consequence.componentInvalid(
          s"component assembly binding requires canonical namespace/id/version: " +
          s"alias=${binding.componentName}; required=canonical namespace/id/version"
        )
      case _ =>
        Consequence.componentInvalid(
          s"component assembly binding requires canonical namespace/id/version: " +
            s"alias=${binding.componentName}; required=canonical namespace/id/version"
        )
    }

  private def _resolve_requirements_c(
    descriptor: GenericSubsystemDescriptor
  ): Consequence[Vector[Assignment]] =
    descriptor.toComponentDescriptors
      .flatMap { component =>
        component.componentStyleSnapshot.toVector.flatMap { snapshot =>
          snapshot.subsystemCapabilities.map(capability => component -> capability)
        }
      }
      .foldLeft(Consequence.success(Vector.empty[Assignment])) { case (z, (component, capability)) =>
        z.flatMap(assignments => _resolve_requirement_c(descriptor, component.componentName.orElse(component.name).getOrElse("<unnamed>"), capability).map(assignments :+ _))
      }

  private def _resolve_requirement_c(
    descriptor: GenericSubsystemDescriptor,
    component: String,
    required: SubsystemCapabilityId
  ): Consequence[Assignment] = {
    val providers = descriptor.subsystemCapabilityProviders.filter(_.capabilities.contains(required))
    val incompatible = descriptor.subsystemCapabilityProviders.flatMap { provider =>
      provider.capabilities.collect {
        case capability if capability.family == required.family && capability.name == required.name =>
          s"${provider.name}:${capability.canonical}"
      }
    }
    providers match {
      case Vector(provider) => Consequence.success(Assignment(component, required, provider.name))
      case Vector() =>
        if (incompatible.nonEmpty)
          Consequence.resourceInvalid(
            s"component '$component' requires subsystem capability '${required.canonical}', but declared providers have an incompatible major version: ${incompatible.sorted.mkString(", ")}"
          )
        else
          Consequence.resourceInvalid(
            s"component '$component' requires subsystem capability '${required.canonical}', but no declared provider supplies it"
          )
      case xs =>
        Consequence.resourceInvalid(
          s"component '$component' requires subsystem capability '${required.canonical}', but declared providers are ambiguous: ${xs.map(_.name).sorted.mkString(", ")}"
        )
    }
  }

}
