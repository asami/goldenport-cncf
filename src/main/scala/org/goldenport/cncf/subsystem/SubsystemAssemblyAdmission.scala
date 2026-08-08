package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, SubsystemCapabilityId}
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
 * @version Aug.  8, 2026
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
   * static descriptor supplies the binding. No repository is built here.
   */
  def resolveC(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[GenericSubsystemDescriptor] =
    _promote_qualified_component_ids_c(descriptor).flatMap { admitted =>
      val discovered = _discover_static_descriptors(admitted, repositories)
      val discovereddescriptor =
        if (discovered.isEmpty) admitted
        else admitted.copy(componentDescriptorOverrides = discovered)
      if (_requires_descriptor_closure(discovereddescriptor))
        _resolve_descriptors_c(discovereddescriptor, repositories).flatMap { descriptors =>
          val resolved = admitted.copy(componentDescriptorOverrides = descriptors)
          verifyC(resolved).map(_ => resolved)
        }
      else
        verifyC(discovereddescriptor).map(_ => discovereddescriptor)
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

  private def _promote_qualified_component_ids_c(
    descriptor: GenericSubsystemDescriptor
  ): Consequence[GenericSubsystemDescriptor] = {
    val bindings = descriptor.componentBindings.map { binding =>
      binding.componentId match {
        case Some(_) => Consequence.success(binding)
        case None =>
          ComponentId.parseC(binding.componentName)
            .map(id => binding.copy(componentId = Some(id)))
            .recover { _ => binding }
      }
    }
    _sequence(bindings).flatMap { promoted =>
      GenericSubsystemDescriptor._validate_component_bindings_c(promoted)
        .map(validated => descriptor.copy(componentBindings = validated))
    }
  }

  private def _resolve_descriptors_c(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[Vector[ComponentDescriptor]] =
    descriptor.componentBindings.foldLeft(Consequence.success(Vector.empty[ComponentDescriptor])) { (z, binding) =>
      z.flatMap { descriptors =>
        _resolve_descriptor_c(descriptor, binding, repositories).map(descriptors :+ _)
      }
    }

  private def _discover_static_descriptors(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Vector[ComponentDescriptor] =
    descriptor.componentBindings.flatMap { binding =>
      val overrides = descriptor.componentDescriptorOverrides.filter(_matches_binding(_, binding))
      overrides match {
        case Vector(value) => Some(value)
        case Vector() =>
          repositories.iterator
            .map(_.resolveStaticComponentDescriptor(binding.componentId.map(_.name).getOrElse(binding.componentName)))
            .collectFirst { case Some(value) => value }
        case _ => None
      }
    }

  private def _resolve_descriptor_c(
    descriptor: GenericSubsystemDescriptor,
    binding: GenericSubsystemComponentBinding,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[ComponentDescriptor] = {
    val overrides = descriptor.componentDescriptorOverrides.filter(_matches_binding(_, binding))
    overrides match {
      case Vector(value) => Consequence.success(value)
      case Vector() =>
        repositories.iterator
          .map(_.resolveStaticComponentDescriptor(binding.componentId.map(_.name).getOrElse(binding.componentName)))
          .collectFirst { case Some(value) => value }
          .map(Consequence.success)
          .getOrElse(
            Consequence.resourceInvalid(
              s"component descriptor closure is missing binding '${binding.runtimeComponentName}'"
            )
          )
      case xs =>
        Consequence.resourceInvalid(
          s"component descriptor closure is ambiguous for binding '${binding.runtimeComponentName}': ${xs.size} explicit descriptors"
        )
    }
  }

  private def _matches_binding(
    descriptor: ComponentDescriptor,
    binding: GenericSubsystemComponentBinding
  ): Boolean =
    binding.componentId match {
      case Some(id) => descriptor.componentId.contains(id)
      case None => GenericSubsystemDescriptor.runtimeComponentName(
        descriptor.componentName.orElse(descriptor.name).getOrElse("")
      ) == binding.runtimeComponentName
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

  private def _sequence[A](
    values: Vector[Consequence[A]]
  ): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (z, value) =>
      z.flatMap(xs => value.map(xs :+ _))
    }
}
