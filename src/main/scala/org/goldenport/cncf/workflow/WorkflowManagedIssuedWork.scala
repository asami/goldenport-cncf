package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore, DataStoreSpace, DataStoreComponentOwner, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowInteraction
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Opaque borrowed host binding. It owns no pool and retains no lease or store.
  * Issue is atomic; reads and identical retries confer no claim authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowManagedIssuedWork[W] private (
  subsystem: Subsystem,
  component: ComponentId,
  configuration: Configuration,
  completion: StateMachineOperationIdentity,
  definitions: Vector[DefinitionBinding],
  instances: DataStore.CollectionId,
  continuations: DataStore.CollectionId,
  codec: PayloadCodec[W],
  owner: DataStoreComponentOwner,
  kind: WorkflowIssuedContinuationKind
) {
  def suspendC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    issued: WorkflowInteraction[W, Nothing],
    records: Vector[EventRecord] = Vector.empty
  )(using context: ExecutionContext): Consequence[WorkflowInstanceDataStorePersistence.PersistedSuspension] =
    if (intent == null || intent.configuration != configuration || records == null || records.exists(_ == null))
      Consequence.stateConflict("Managed issue requires its bound Workflow configuration")
    else withHostC { (adapter, space) =>
      for {
        prepared <- adapter.prepareSuspensionC(intent, issued, owner)
        participant <- prepared.hostParticipantC(space, records, Vector.empty, () => Consequence.unit,
          batch => batch.foldLeft(Consequence.unit)((acc, record) =>
            acc.flatMap(_ => subsystem.eventBus.dispatchCommittedRecordC(record).map(_ => ()))))
        result <- {
          val unitOfWork = new ManagedCompletionUnitOfWork(context, participant)
          unitOfWork.commit().flatMap { _ => unitOfWork.storageOutcome match {
            case Some(ManagedCompletionParticipant.Outcome.Verified(value)) => Consequence.success(value)
            case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Managed issue lacks verified retained suspension")
          }}
        }
      } yield result
    }

  def loadC(identity: ContinuationIdentity)(using ExecutionContext):
      Consequence[Option[WorkflowInteraction[W, Nothing]]] =
    withProviderC(_.loadC(identity))

  def putIfAbsentC(issued: WorkflowInteraction[W, Nothing])(using ExecutionContext):
      Consequence[WorkflowInteraction[W, Nothing]] =
    withProviderC(_.putIfAbsentC(issued))

  /** SPI projection captures a call context, never a borrowed provider. */
  def issuedPersistence(using context: ExecutionContext): IssuedWorkOrderPersistence[W] =
    new IssuedWorkOrderPersistence[W] {
      def loadC(identity: ContinuationIdentity) = WorkflowManagedIssuedWork.this.loadC(identity)
      def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]) =
        WorkflowManagedIssuedWork.this.putIfAbsentC(issued)
    }

  private def withProviderC[A](f: WorkflowIssuedWorkDataStorePersistence[W] => Consequence[A])(
    using context: ExecutionContext
  ): Consequence[A] =
    withHostC((adapter, _) => f(adapter))

  private def withHostC[A](f: (WorkflowIssuedWorkDataStorePersistence[W], DataStoreSpace) => Consequence[A])(
    using context: ExecutionContext
  ): Consequence[A] =
    if (context == null) Consequence.stateConflict("Managed issued work requires an execution context")
    else subsystem.admitWorkflowStorageC(component, configuration.workflowStore.name,
      instances, continuations).flatMap { _ =>
      val environment = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
      subsystem.withWorkflowVerifiedMultiGuardSpaceC(component, environment) { space =>
        for {
          store <- space.dataStore(instances)
          adapter <- WorkflowIssuedWorkDataStorePersistence.bindForKindC(store, configuration, component,
            completion, definitions, instances, continuations, codec, kind)
          result <- f(adapter, space)
        } yield result
      }
    }
}

object WorkflowManagedIssuedWork {
  /** Pure composition admission; actual managed provider is borrowed per call. */
  def bindC[W](subsystem: Subsystem, component: ComponentId,
    configuration: Configuration, completion: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, codec: PayloadCodec[W]
  ): Consequence[WorkflowManagedIssuedWork[W]] =
    bindForKindC(subsystem, component, configuration, completion, definitions,
      instances, continuations, codec, WorkflowIssuedContinuationKind.WorkOrder)

  private[workflow] def bindForKindC[W](subsystem: Subsystem, component: ComponentId,
    configuration: Configuration, completion: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, codec: PayloadCodec[W], kind: WorkflowIssuedContinuationKind
  ): Consequence[WorkflowManagedIssuedWork[W]] =
    if (subsystem == null || component == null || configuration == null || completion == null ||
        completion.service == null || completion.service.trim.isEmpty || completion.operation == null ||
        completion.operation.trim.isEmpty || definitions == null || definitions.isEmpty ||
        definitions.exists(_ == null) || instances == null || continuations == null ||
        codec == null || codec.typeIdentity == null || codec.typeIdentity.trim.isEmpty || kind == null)
      Consequence.configurationInvalid("Managed issued-work host binding is incomplete")
    else for {
      accepted <- configuration.validateC
      _ <- accepted.delivery match {
        case _: WorkflowInstancePersistence.DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Managed issued work requires same-store delivery")
      }
      _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, continuations)
      owner <- DataStoreComponentOwner.create(component.name)
    } yield new WorkflowManagedIssuedWork(subsystem, component, accepted, completion,
      definitions, instances, continuations, codec, owner, kind)
}
