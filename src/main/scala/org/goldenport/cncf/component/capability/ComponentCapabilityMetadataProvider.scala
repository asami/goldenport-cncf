package org.goldenport.cncf.component.capability

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.statemachine.CmlStateMachineDefinition
import ComponentCapabilityReference.Source
import ComponentCapabilityAdmissionScope.{Diagnostic, ScopeInput, TargetContract}
import ComponentCapabilityMetadataProvider.NativeInput

/*
 * Pure independently observed native metadata; no runtime activation.
 *
 * @since   Oct. 14, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
trait ComponentCapabilityMetadataProvider {
  def componentCapabilityMetadata: NativeInput
}

object ComponentCapabilityMetadataProvider {
  final case class NativeInput(acquisition: NativeAcquisition, scopeInput: ScopeInput, bindings: Vector[NativeBinding]) {}

  enum NativeAcquisition {
    case Acquired(scopeId: String, snapshots: Vector[NativeSnapshot])
    case Rejected(diagnostic: Diagnostic)
  }

  final case class NativeSnapshot(
    component: ComponentId,
    source: Source,
    operations: Vector[CmlOperationDefinition],
    workflows: Vector[WorkflowDefinition],
    stateMachines: Vector[CmlStateMachineDefinition]
  ) {}

  final case class NativeBinding(
    snapshotComponent: ComponentId,
    snapshotSource: Source,
    target: TargetContract,
    selector: String
  ) {}

  enum NativeContract {
    case Operation(definition: CmlOperationDefinition)
    case Workflow(definition: WorkflowDefinition)
    case StateMachine(definition: CmlStateMachineDefinition)
  }
}
