package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.statemachine.CmlStateMachineDefinition
import ComponentCapabilityReference.{ModelElementId, Source}
import ComponentCapabilityProjection.TargetKind
import ComponentCapabilityAdmissionScope.Diagnostic

/*
 * Explicit observed metadata; these constructors confer no acquisition or
 * runtime authority. The caller owns acquisition and opaque version facts.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentCapabilityTargetSource {
  enum AcquisitionInput {
    case Acquired(scopeId: String, snapshots: Vector[Snapshot])
    case Rejected(diagnostic: Diagnostic)
  }

  final case class Snapshot(
    component: ComponentId,
    source: Source,
    operations: Vector[CmlOperationDefinition],
    workflows: Vector[WorkflowDefinition],
    stateMachines: Vector[CmlStateMachineDefinition]
  ) {
    def key: SnapshotKey = {
      SnapshotKey(component, source.modelAuthority, source.path, source.sourceRevision)
    }
  }

  final case class SnapshotKey(
    component: ComponentId,
    modelAuthority: String,
    path: String,
    sourceRevision: String
  ) {}

  final case class Binding(
    snapshot: SnapshotKey,
    identity: ModelElementId,
    targetKind: TargetKind,
    selector: String,
    contractVersion: String,
    generatedAbiVersion: String,
    source: Source
  ) {}

  enum Contract {
    case Operation(definition: CmlOperationDefinition)
    case Workflow(definition: WorkflowDefinition)
    case StateMachine(definition: CmlStateMachineDefinition)
  }
}
