package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentId}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, DeliveryConfiguration}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Factory-time composition capability, not a physical storage owner.
  * Usable before Component initialization; no raw provider or lease escapes.
  * @since   Oct.  1, 2026
  * @version Oct. 27, 2026
  */
final class WorkflowComponentStorage private (
  subsystem: Subsystem,
  component: ComponentId,
  configuration: Configuration,
  definitions: Vector[DefinitionBinding],
  instances: DataStore.CollectionId,
  continuations: DataStore.CollectionId
) {
  def bindInstancesC()(using context: ExecutionContext): Consequence[WorkflowInstancePersistenceBinding] =
    if (context == null) Consequence.configurationInvalid("Workflow composition requires an execution context")
    else subsystem.admitWorkflowStorageC(component, configuration.workflowStore.name, instances, continuations)
      .flatMap { _ => WorkflowInstancePersistenceBinding.bindC(component, subsystem,
        ComponentDataStore.Environment(ResolvedParameters.fromResolvedConfiguration(subsystem.configuration),
          Some(subsystem.configuration)),
        WorkflowInstancePersistenceBinding.Declaration(configuration, definitions, instances)) }

  def bindIssuedC[W](operation: StateMachineOperationIdentity, codec: PayloadCodec[W]):
      Consequence[WorkflowManagedIssuedWork[W]] =
    WorkflowManagedIssuedWork.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, codec)

  def bindDecisionC[W](operation: StateMachineOperationIdentity, codec: PayloadCodec[W]):
      Consequence[WorkflowManagedIssuedDecision[W]] =
    WorkflowManagedIssuedDecision.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, codec)

  def bindDecisionSupersessionC[W, A](operation: StateMachineOperationIdentity,
    inputCodec: PayloadCodec[W], codec: PayloadCodec[A]): Consequence[WorkflowManagedDecisionSupersession[W, A]] =
    WorkflowManagedDecisionSupersession.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, codec)

  def bindWorkOrderSupersessionC[W, A](operation: StateMachineOperationIdentity,
    inputCodec: PayloadCodec[W], codec: PayloadCodec[A]): Consequence[WorkflowManagedWorkOrderSupersession[W, A]] =
    WorkflowManagedWorkOrderSupersession.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, codec)

  def bindMechanicalC[A](operation: StateMachineOperationIdentity, codec: PayloadCodec[A]):
      Consequence[WorkflowManagedMechanicalTransition[A]] =
    WorkflowManagedMechanicalTransition.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, codec)

  def bindTerminalClosureContinuationC[A](operation: StateMachineOperationIdentity, codec: PayloadCodec[A]):
      Consequence[WorkflowManagedTerminalClosureContinuation[A]] =
    WorkflowManagedTerminalClosureContinuation.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, codec)

  def bindCompletionC[W, R](operation: StateMachineOperationIdentity,
    inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R],
    closingType: StateMachineResultTypeReference,
    events: (ContinuationResult[R], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  ): Consequence[WorkflowManagedCompletion[R]] =
    WorkflowManagedCompletion.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, resultCodec, closingType, events)

  def bindDecisionCompletionC[W, R](operation: StateMachineOperationIdentity,
    inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R],
    closingType: StateMachineResultTypeReference,
    events: (ContinuationResult[R], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  ): Consequence[WorkflowManagedDecisionCompletion[R]] =
    WorkflowManagedDecisionCompletion.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, resultCodec, closingType, events)
}

object WorkflowComponentStorage {
  /** Pure captured-authority admission. Physical access remains in each
    * delegated binding's managed host path, never configuration decoding.
    */
  def bindC(params: ComponentCreate, component: ComponentId,
    configuration: Configuration, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId
  ): Consequence[WorkflowComponentStorage] =
    if (params == null || params.assembly == null || component == null || configuration == null ||
        definitions == null || definitions.isEmpty || definitions.exists(_ == null) ||
        instances == null || continuations == null)
      Consequence.configurationInvalid("Workflow Component storage composition is incomplete")
    else {
      val subsystem = params.subsystem
      if (subsystem == null) Consequence.configurationInvalid("Workflow composition requires its owning Subsystem")
      else for {
        accepted <- configuration.validateC
        _ <- accepted.delivery match {
          case _: DeliveryConfiguration.SameStore => Consequence.unit
          case _ => Consequence.configurationInvalid("Workflow Component storage requires same-store delivery")
        }
        _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
        _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, continuations)
      } yield new WorkflowComponentStorage(subsystem, component, accepted, definitions, instances, continuations)
    }
}
