package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.{CollaboratorComponent, Component, ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata}
import org.goldenport.cncf.component.repository.ComponentRepository

/*
 * Descriptor bindings need isolated component results but one assembly API
 * class identity.  Cohorts therefore describe discovery scope independently
 * from the aggregate assembly metadata scope.
 *
 * @since   Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[subsystem] object GenericSubsystemComponentDiscovery {
  def discoverC(
    repositorySpecs: Vector[ComponentRepository.Specification],
    componentDescriptors: Vector[ComponentDescriptor],
    developmentClaims: Map[ComponentRepository.Specification, Set[(org.goldenport.cncf.component.ComponentId, String)]],
    bindings: Vector[GenericSubsystemComponentBinding],
    params: ComponentCreate
  ): Consequence[Vector[Component]] = {
    val cohorts = bindings.zip(componentDescriptors).map { case (binding, componentdescriptor) =>
      repositorySpecs.zipWithIndex.collect {
        case (spec, index) if ComponentRepository.descriptorsForSpecification(
          spec,
          repositorySpecs.take(index),
          componentDescriptors,
          developmentClaims
        ).contains(componentdescriptor) =>
          spec.build(
            params
              .withComponentDescriptors(Vector(componentdescriptor))
              .withInstanceMetadata(binding.instanceMetadata)
          )
      }.toVector
    }
    val assemblyrepositories = cohorts.flatten.foldLeft(Vector.empty[ComponentRepository]) { (acc, repository) =>
      if (acc.exists(_ eq repository)) acc else acc :+ repository
    }
    ComponentRepository.discoverAssemblyPartitionsC(assemblyrepositories, cohorts).flatMap { discovered =>
      _sequence(bindings.zip(discovered).map { case (binding, components) =>
        materializeComponentBindingC(
          components.filter(matchesArtifactOwner(_, binding)),
          binding,
          params
        )
      }).map(_.flatten)
    }
  }

  def matchesArtifactOwner(
    component: Component,
    binding: GenericSubsystemComponentBinding
  ): Boolean =
    (binding.componentId, binding.componentVersion) match {
      case (Some(id), Some(release)) =>
        component.artifactMetadata.exists(metadata =>
          metadata.componentId.contains(id) && metadata.version == release
        )
      case _ => false
    }

  def materializeComponentInstancesC(
    discovered: Seq[Component],
    descriptor: GenericSubsystemDescriptor,
    params: ComponentCreate
  ): Consequence[Vector[Component]] =
    _sequence(descriptor.componentBindings.map(materializeComponentBindingC(discovered, _, params))).map(_.flatten)

  def materializeComponentBindingC(
    discovered: Seq[Component],
    binding: GenericSubsystemComponentBinding,
    params: ComponentCreate
  ): Consequence[Vector[Component]] =
    if (binding.componentId.isEmpty || binding.componentVersion.isEmpty) {
      Consequence.componentInvalid(
        s"component assembly binding requires canonical namespace/id/version: " +
          s"alias=${binding.componentName}; required=canonical namespace/id/version"
      )
    } else {
      val componentid = binding.componentId.get
      val owned = discovered.filter(matchesArtifactOwner(_, binding)).toVector
      val candidates = owned.filter(_matches_descriptor_component(_, binding))
      candidates match {
        case Vector() =>
          Consequence.componentInvalid(
            s"canonical component binding has no exact Core/artifact identity match: ${componentid.name}"
          )
        case Vector(_) =>
          _sequence(owned.map { participant =>
            if (participant.instanceMetadata.contains(_binding_instance_metadata(binding, participant)))
              Consequence.success(participant)
            else
              _create_component_participant_c(participant, binding, params)
          })
        case _ =>
          Consequence.componentInvalid(
            s"canonical component binding has multiple exact Core/artifact identity matches: ${componentid.name}"
          )
      }
    }

  private def _matches_descriptor_component(
    component: Component,
    binding: GenericSubsystemComponentBinding
  ): Boolean =
    (binding.componentId, binding.componentVersion) match {
      case (Some(id), _) =>
        component.isPrimaryParticipant && component.core.componentId == id && matchesArtifactOwner(component, binding)
      case _ => false
    }

  private def _create_component_participant_c(
    prototype: Component,
    binding: GenericSubsystemComponentBinding,
    params: ComponentCreate
  ): Consequence[Component] =
    prototype.factoryOption match {
      case Some(factory) =>
        val instanceparams = params
          .withOrigin(prototype.origin)
          .withComponentDescriptors(prototype.componentDescriptors)
          .withInstanceMetadata(_binding_instance_metadata(binding, prototype))
        val componentc =
          if (prototype.isComponentletParticipant) factory.createComponentletC(instanceparams)
          else factory.createPrimaryC(instanceparams)
        componentc.map { component =>
          prototype.artifactMetadata.foreach(component.withArtifactMetadata)
          component.withCollaboratorClasspath(prototype.collaboratorClasspath)
          (prototype, component) match {
            case (source: CollaboratorComponent, replacement: CollaboratorComponent) =>
              replacement.inheritCollaboratorWiringFrom(source)
            case _ => ()
          }
          component
        }
      case None if binding.hasInstanceDeclaration =>
        Consequence.componentInvalid(
          s"component factory is required for named instance: ${binding.componentName}/${binding.instanceName}"
        )
      case None => Consequence.success(prototype)
    }

  private def _binding_instance_metadata(
    binding: GenericSubsystemComponentBinding,
    prototype: Component
  ): ComponentInstanceMetadata =
    if (prototype.isComponentletParticipant)
      binding.instanceMetadata.copy(
        componentName = prototype.core.componentId.name,
        componentId = Some(prototype.core.componentId)
      )
    else
      binding.instanceMetadata

  private def _sequence[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (acc, value) =>
      acc.flatMap(xs => value.map(xs :+ _))
    }
}
