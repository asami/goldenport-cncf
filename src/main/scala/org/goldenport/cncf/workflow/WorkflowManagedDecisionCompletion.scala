package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Opaque Decision-only completion binding. Structural result/evidence checks
  * are not human verification. Actual claim and closing execution remain owned
  * by CNCF; no public request can select the provider, claim or next history.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowManagedDecisionCompletion[R] private (binding: WorkflowManagedCompletion[R]) {
  private[workflow] def resultTypeIdentity: String = binding.resultTypeIdentity

  def loadRetainedC(identity: ContinuationIdentity)(using ExecutionContext):
      Consequence[Option[WorkflowManagedCompletion.Retained[R]]] = binding.loadRetainedC(identity)

  private[workflow] def completeC(owner: ComponentId, submitted: ContinuationResult[R],
    current: InstanceRecord, selected: WorkflowCompletionServiceOperation.SelectedClosing,
    context: ExecutionContext): Consequence[WorkflowManagedCompletion.Completed] =
    binding.completeC(owner, submitted, current, selected, context)
}

object WorkflowManagedDecisionCompletion {
  def bindC[W, R](subsystem: Subsystem, component: ComponentId,
    configuration: Configuration, operation: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R],
    closingType: StateMachineResultTypeReference,
    events: (ContinuationResult[R], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  ): Consequence[WorkflowManagedDecisionCompletion[R]] =
    WorkflowManagedCompletion.bindForKindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, resultCodec, closingType, events,
      WorkflowIssuedContinuationKind.Decision).map(new WorkflowManagedDecisionCompletion(_))
}
