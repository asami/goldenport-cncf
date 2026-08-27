package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId, ComponentIdentityCompatibilityAdapter, SubsystemCapabilityId}
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositoryStaticIdentityCandidates}

/*
 * Descriptor-only admission for component-style subsystem requirements.
 *
 * This runs before a Subsystem, repository, component class loader, SPI
 * resolver, datastore, or job runtime is created.  It intentionally accepts
 * only the dedicated typed provider declarations; component instance metadata
 * is not a source of subsystem capability authority.
 *
 * @since   Jul. 31, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
object SubsystemAssemblyAdmission {
  private[cncf] final case class DetailedAdmission(
    descriptor: GenericSubsystemDescriptor,
    notices: Vector[ComponentIdentityCompatibilityAdapter.Notice]
  )

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
   * static descriptor supplies the binding. Typed bindings, canonical
   * overrides, and active repository static descriptors form the complete
   * admitted compatibility candidate set. No repository is built here.
   */
  def resolveC(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[GenericSubsystemDescriptor] =
    resolveWithNoticesC(descriptor, repositories).map(_.descriptor)

  private[cncf] def resolveWithNoticesC(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[DetailedAdmission] =
    _promote_component_bindings_c(descriptor, repositories).flatMap { admitted =>
      _discover_static_descriptors_c(admitted.descriptor, repositories).flatMap { discovered =>
        val discovereddescriptor =
          if (discovered.isEmpty) admitted.descriptor
          else admitted.descriptor.copy(componentDescriptorOverrides = discovered.map(_.descriptor).distinct)
        val discoverednotices = admitted.notices ++ discovered.flatMap(_.notices)
        if (_requires_descriptor_closure(discovereddescriptor))
          _resolve_descriptors_c(discovereddescriptor, repositories).flatMap { descriptors =>
            val resolved = discovereddescriptor.copy(componentDescriptorOverrides = descriptors.map(_.descriptor).distinct)
            verifyC(resolved).map(_ => DetailedAdmission(
              resolved,
              (discoverednotices ++ descriptors.flatMap(_.notices)).distinct
            ))
          }
        else
          verifyC(discovereddescriptor).map(_ => DetailedAdmission(discovereddescriptor, discoverednotices.distinct))
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

  private def _promote_component_bindings_c(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Consequence[DetailedAdmission] = {
    val parsedbindings = descriptor.componentBindings.map { binding =>
      binding.componentId match {
        case Some(_) => Consequence.success(binding)
        case None =>
          ComponentId.parseC(binding.componentName)
            .map(id => _canonical_binding(binding, id))
            .recover { _ => binding }
      }
    }
    _sequence(parsedbindings).flatMap { parsed =>
      val parseddescriptor = descriptor.copy(componentBindings = parsed)
      val candidates = _compatibility_candidates(parseddescriptor, repositories)
      _sequence(parsed.map(_adapt_legacy_binding_c(_, candidates))).flatMap { adapted =>
        val bindings = adapted.map(_._1)
        val notices = adapted.flatMap(_._2).distinct
        GenericSubsystemDescriptor._validate_component_bindings_c(bindings)
          .map(validated => DetailedAdmission(descriptor.copy(componentBindings = validated), notices))
      }
    }
  }

  private def _compatibility_candidates(
    descriptor: GenericSubsystemDescriptor,
    repositories: Vector[ComponentRepository.Specification]
  ): Vector[ComponentId] = {
    val bindingids = descriptor.componentBindings.flatMap(_.componentId)
    val overrideids = descriptor.componentDescriptorOverrides.flatMap(
      _.requireCanonicalIdentityC.toOption.map(_._1)
    )
    val repositoryids = descriptor.componentBindings
      .filter(_.componentId.isEmpty)
      .flatMap { binding =>
        repositories.flatMap(ComponentRepositoryStaticIdentityCandidates.resolve(_, binding.componentName))
      }
    (bindingids ++ overrideids ++ repositoryids).distinct.sortBy(_.name)
  }

  private def _adapt_legacy_binding_c(
    binding: GenericSubsystemComponentBinding,
    candidates: Vector[ComponentId]
  ): Consequence[(GenericSubsystemComponentBinding, Vector[ComponentIdentityCompatibilityAdapter.Notice])] =
    binding.componentId match {
      case Some(_) => Consequence.success(binding -> Vector.empty)
      case None =>
        ComponentIdentityCompatibilityAdapter.resolve(
          binding.componentName,
          candidates,
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        ) match {
          case result: ComponentIdentityCompatibilityAdapter.Canonical =>
            result.toConsequence.map(admission =>
              _canonical_binding(binding, admission.componentid) -> admission.notice.toVector
            )
          case result: ComponentIdentityCompatibilityAdapter.Adapted =>
            result.toConsequence.map(admission =>
              _canonical_binding(binding, admission.componentid) -> admission.notice.toVector
            )
          case ComponentIdentityCompatibilityAdapter.Rejected(_: ComponentIdentityCompatibilityAdapter.Unsupported) =>
            Consequence.success(binding -> Vector.empty)
          case result: ComponentIdentityCompatibilityAdapter.Rejected =>
            result.toConsequence.map(admission =>
              _canonical_binding(binding, admission.componentid) -> admission.notice.toVector
            )
        }
    }

  private def _canonical_binding(
    binding: GenericSubsystemComponentBinding,
    componentid: ComponentId
  ): GenericSubsystemComponentBinding =
    binding.copy(
      componentName = componentid.name,
      componentId = Some(componentid)
    )

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
          .collectFirst { case Some(value) if _matches_binding_version(binding, value) => value }
          .map(_project_descriptor_c(binding, _).map(Some(_)))
          .getOrElse(Consequence.success(None))
      case xs =>
        Consequence.resourceInvalid(
          s"component descriptor closure is ambiguous for binding '${binding.runtimeComponentName}': ${xs.size} explicit descriptors"
        )
    }
  }

  private def _descriptor_lookup_names(binding: GenericSubsystemComponentBinding): Vector[String] =
    binding.componentId
      .map(ComponentIdentityCompatibilityAdapter.descriptorAliases)
      .getOrElse(Vector(binding.componentName))

  private def _matches_binding_version(
    binding: GenericSubsystemComponentBinding,
    descriptor: ComponentDescriptor
  ): Boolean =
    binding.componentVersion.forall(descriptor.version.contains)

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
          binding.componentId.exists { componentid =>
            ComponentIdentityCompatibilityAdapter.descriptorClaimsIdentity(descriptor, componentid)
          }

  private def _project_descriptor_c(
    binding: GenericSubsystemComponentBinding,
    descriptor: ComponentDescriptor
  ): Consequence[ComponentIdentityCompatibilityAdapter.DescriptorProjection] =
    (binding.componentId, binding.componentVersion) match {
      case (Some(componentid), Some(release)) =>
        ComponentIdentityCompatibilityAdapter.projectDescriptorC(descriptor, componentid).flatMap { projection =>
          projection.descriptor.version match {
            case Some(actual) if actual == release => Consequence.success(projection)
            case Some(actual) =>
              Consequence.componentInvalid(
                s"component descriptor closure canonical binding mismatch: expected=${componentid.name}:$release, " +
                  s"actual=${componentid.name}:$actual"
              )
            case None =>
              Consequence.componentInvalid(
                s"component descriptor closure canonical binding requires version: expected=${componentid.name}:$release"
              )
          }
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

  private def _sequence[A](
    values: Vector[Consequence[A]]
  ): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (z, value) =>
      z.flatMap(xs => value.map(xs :+ _))
    }

}
