package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore, DataStoreSpace,
  DataStoreComponentOwner, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/**
 * Metadata-only binding. All execution/observation borrows the existing host.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowManagedMechanicalTransition[A] private (
  subsystem: Subsystem, component: ComponentId, configuration: Configuration,
  operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
  instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner
) {
  import WorkflowManagedMechanicalTransition.*

  def executeC(intent: Intent[A], program: ExecUowM[Unit], records: Vector[EventRecord] = Vector.empty)(
    using context: ExecutionContext): Consequence[Completed[A]] =
    if (records == null || records.exists(_ == null))
      Consequence.stateConflict("Mechanical event batch is incomplete")
    else for {
      captured <- WorkflowMechanicalProgramPreparation.prepareC(program)
      creates <- captured.createsC
      result <- withHostC { (adapter, space) => for {
        prepared <- adapter.prepareC(intent)
        participant <- prepared.hostParticipantC(space, records, Vector.empty, () => Consequence.unit,
          batch => batch.foldLeft(Consequence.unit)((acc, event) =>
            acc.flatMap(_ => subsystem.eventBus.dispatchCommittedRecordC(event).map(_ => ()))),
          localCreates = creates)
        committed <- {
          val unitOfWork = new ManagedCompletionUnitOfWork(context, participant)
          unitOfWork.commit().flatMap { _ => unitOfWork.storageOutcome match {
            case Some(ManagedCompletionParticipant.Outcome.Verified(value)) =>
              Consequence.success(new Completed(value))
            case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Mechanical transition lacks verified canonical retained data")
          }}
        }
      } yield committed }
    } yield result

  def loadRetainedC(reference: Reference)(using ExecutionContext): Consequence[Option[Retained[A]]] =
    withHostC((adapter, _) => adapter.loadRetainedC(reference).map(_.map { case (receipt, current) =>
      new Retained(receipt, current)
    }))

  private def withHostC[B](body: (WorkflowMechanicalTransitionDataStorePersistence[A], DataStoreSpace) => Consequence[B])(
    using context: ExecutionContext): Consequence[B] =
    if (context == null) Consequence.stateConflict("Mechanical transition requires a current execution context")
    else subsystem.admitWorkflowStorageC(component, configuration.workflowStore.name, instances, checkpoints).flatMap { _ =>
      val environment = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
      subsystem.withWorkflowVerifiedMultiGuardSpaceC(component, environment) { space => for {
        store <- space.dataStore(instances)
        adapter <- WorkflowMechanicalTransitionDataStorePersistence.bindC(store, configuration, component,
          operation, definitions, instances, checkpoints, codec, owner)
        result <- body(adapter, space)
      } yield result }
    }
}

object WorkflowManagedMechanicalTransition {
  final class Completed[A] private[WorkflowManagedMechanicalTransition] (val receipt: Receipt[A])
  final class Retained[A] private[WorkflowManagedMechanicalTransition] (
    val receipt: Receipt[A], val current: InstanceRecord)

  def bindC[A](subsystem: Subsystem, component: ComponentId, configuration: Configuration,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId,
    codec: PayloadCodec[A]): Consequence[WorkflowManagedMechanicalTransition[A]] =
    if (subsystem == null || component == null || !canonical(component.name) || configuration == null ||
        operation == null || !canonical(operation.service) || !canonical(operation.operation) ||
        definitions == null || definitions.isEmpty || definitions.exists(_ == null) ||
        instances == null || checkpoints == null || codec == null || !canonical(codec.typeIdentity))
      Consequence.configurationInvalid("Mechanical Workflow composition is incomplete")
    else for {
      accepted <- configuration.validateC
      _ <- accepted.delivery match {
        case _: DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Mechanical Workflow requires same-store delivery")
      }
      _ <- (instances, checkpoints) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Mechanical Workflow requires distinct EntityStore collections")
      }
      _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, checkpoints)
      owner <- DataStoreComponentOwner.create(component.name)
    } yield new WorkflowManagedMechanicalTransition(subsystem, component, accepted, operation,
      definitions, instances, checkpoints, codec, owner)
}
