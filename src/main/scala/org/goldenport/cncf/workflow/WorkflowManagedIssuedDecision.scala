package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowInteraction
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Kind-bound native Decision publication. No WorkOrder SPI, claim, human
  * verification or completion authority is exposed by this capability.
  * The shared managed host owns every provider/lease/atomic transition.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowManagedIssuedDecision[W] private (binding: WorkflowManagedIssuedWork[W]) {
  def suspendC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    issued: WorkflowInteraction[W, Nothing], records: Vector[EventRecord] = Vector.empty
  )(using ExecutionContext): Consequence[WorkflowInstanceDataStorePersistence.PersistedSuspension] =
    binding.suspendC(intent, issued, records)

  def loadC(identity: ContinuationIdentity)(using ExecutionContext):
      Consequence[Option[WorkflowInteraction[W, Nothing]]] = binding.loadC(identity)

  /** Immutable equal retry only; never retrofits an independently saved question. */
  def putIfAbsentC(issued: WorkflowInteraction[W, Nothing])(using ExecutionContext):
      Consequence[WorkflowInteraction[W, Nothing]] = binding.putIfAbsentC(issued)
}

object WorkflowManagedIssuedDecision {
  def bindC[W](subsystem: Subsystem, component: ComponentId,
    configuration: Configuration, completion: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, codec: PayloadCodec[W]
  ): Consequence[WorkflowManagedIssuedDecision[W]] =
    WorkflowManagedIssuedWork.bindForKindC(subsystem, component, configuration, completion,
      definitions, instances, continuations, codec, WorkflowIssuedContinuationKind.Decision)
      .map(new WorkflowManagedIssuedDecision(_))
}
