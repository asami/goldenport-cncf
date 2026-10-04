package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentId
import ComponentCapabilityReference.*

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentCapabilityProjection {
  final case class Projection(
    schemaVersion: Int,
    generatedAbiVersion: String,
    producer: Producer,
    component: ComponentCoordinate,
    packageCoordinate: PackageCoordinate,
    source: Source,
    capabilities: Vector[CapabilityDefinition],
    declarations: Vector[Declaration],
    compatibility: Presence[Vector[Compatibility]]
  ) {}

  final case class Producer(id: String, version: String) {}
  final case class ComponentCoordinate(componentId: ComponentId, version: String) {}
  final case class PackageCoordinate(coordinate: String, version: String) {}

  final case class CapabilityDefinition(
    identity: ModelElementId,
    contractVersion: String,
    name: String,
    summary: Presence[String],
    description: Presence[String],
    source: Source,
    discoveryReferences: Presence[Vector[ModelLink]],
    grounding: Presence[Vector[Grounding]],
    traceability: Presence[Vector[Traceability]]
  ) {
    val kind: String = "COMPONENT"
  }

  final case class CapabilityReference(identity: ModelElementId, contractVersion: String) {
    val kind: String = "COMPONENT"
  }

  enum Direction(val tag: String) {
    case Provided extends Direction("PROVIDED")
    case Required extends Direction("REQUIRED")
  }

  enum TargetKind(val tag: String) {
    case Operation extends TargetKind("OPERATION")
    case Workflow extends TargetKind("WORKFLOW")
    case StateMachine extends TargetKind("STATE_MACHINE")
  }

  final case class DeclarationKey(
    component: ComponentId,
    direction: Direction,
    capability: CapabilityReference
  ) {}

  final case class Declaration(
    component: ComponentId,
    direction: Direction,
    capability: CapabilityReference,
    source: Source,
    realizations: Presence[Vector[Realization]],
    traceability: Presence[Vector[Traceability]]
  ) {
    private[capability] def _key: DeclarationKey = {
      DeclarationKey(component, direction, capability)
    }
  }

  final case class Realization(
    declarationKey: DeclarationKey,
    targetKind: TargetKind,
    target: ModelReference,
    targetComponent: ComponentId,
    targetContractVersion: String,
    generatedAbiVersion: String,
    source: Source,
    origin: Origin
  ) {}

  final case class Compatibility(
    identity: ModelElementId,
    requiredVersion: String,
    providedVersion: String,
    source: Source,
    origin: Origin
  ) {}
}
