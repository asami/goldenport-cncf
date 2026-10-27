package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore, DataStoreSpace,
  DataStoreComponentOwner, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.{Reference, Receipt, canonical}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.cncf.workflow.WorkflowTerminalClosureContinuation.{Target, Intent}

/** Dedicated same-owner terminal frontier continuation. Application admission
  * precedes this call; the host supplies native revision and Create guards.
  * There is no command execution, event dispatch or transferable approval.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  */
final class WorkflowManagedTerminalClosureContinuation[A] private (
  subsystem: Subsystem, component: ComponentId, configuration: Configuration,
  operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
  instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId,
  codec: PayloadCodec[A], owner: DataStoreComponentOwner
) {
  import WorkflowManagedTerminalClosureContinuation.*

  def inspectC(instance: InstanceIdentity)(using ExecutionContext): Consequence[Target] =
    _with_host_c((adapter, _) => adapter.inspectC(instance))

  /** An exact duplicate collects its retained outcome, including after later
    * native progress. A new reference still needs the exact Completed frontier.
    */
  def executeC(intent: Intent[A])(using context: ExecutionContext): Consequence[Completed[A]] =
    if (intent == null || intent.target == null || intent.target.component != component)
      Consequence.stateConflict("Terminal closure intent has no exact captured owner target")
    else _with_host_c { (adapter, space) => for {
      retained <- adapter.loadRetainedC(intent.reference, intent.target.instance)
      result <- retained match {
        case Some((receipt, current)) => for {
          expected <- WorkflowTerminalClosureContinuation.admitC(component, operation, receipt.current, intent)
          limits <- configuration.mechanicalReceiptLimitsC
          saved <- WorkflowTerminalClosureContinuationJsonV1.encodeC(receipt, codec, limits)
          proposed <- WorkflowTerminalClosureContinuationJsonV1.encodeC(expected, codec, limits)
          _ <- if (saved == proposed) Consequence.unit
            else Consequence.stateConflict("Terminal closure reference already retains a different exact intent")
        } yield new Completed(receipt, current)
        case None => for {
          prepared <- adapter.prepareC(intent)
          participant <- prepared.hostParticipantC(space, Vector.empty, Vector.empty,
            () => Consequence.unit, _ => Consequence.unit)
          committed <- {
            val unitofwork = new ManagedCompletionUnitOfWork(context, participant)
            unitofwork.commit().flatMap { _ => unitofwork.storageOutcome match {
              case Some(ManagedCompletionParticipant.Outcome.Verified(value)) =>
                Consequence.success(new Completed(value, value.next))
              case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "Terminal closure lacks verified canonical retained data; collect the exact reference")
            }}
          }
        } yield committed
      }
    } yield result }

  def loadRetainedC(reference: Reference, instance: InstanceIdentity)(using ExecutionContext):
      Consequence[Option[Retained[A]]] =
    _with_host_c((adapter, _) => adapter.loadRetainedC(reference, instance).map(_.map {
      case (receipt, current) => new Retained(receipt, current)
    }))

  private def _with_host_c[B](body: (WorkflowTerminalClosureContinuationDataStorePersistence[A], DataStoreSpace) => Consequence[B])(
    using context: ExecutionContext): Consequence[B] =
    if (context == null) Consequence.stateConflict("Terminal closure requires a current execution context")
    else subsystem.admitWorkflowStorageC(component, configuration.workflowStore.name, instances, checkpoints).flatMap { _ =>
      val environment = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))
      subsystem.withWorkflowVerifiedMultiGuardSpaceC(component, environment) { space => for {
        store <- space.dataStore(instances)
        adapter <- WorkflowTerminalClosureContinuationDataStorePersistence.bindC(store, configuration, component,
          operation, definitions, instances, checkpoints, codec, owner)
        result <- body(adapter, space)
      } yield result }
    }
}

object WorkflowManagedTerminalClosureContinuation {
  final class Completed[A] private[WorkflowManagedTerminalClosureContinuation] (
    val receipt: Receipt[A], val current: InstanceRecord)
  final class Retained[A] private[WorkflowManagedTerminalClosureContinuation] (
    val receipt: Receipt[A], val current: InstanceRecord)

  def bindC[A](subsystem: Subsystem, component: ComponentId, configuration: Configuration,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instances: DataStore.CollectionId, checkpoints: DataStore.CollectionId,
    codec: PayloadCodec[A]): Consequence[WorkflowManagedTerminalClosureContinuation[A]] =
    if (subsystem == null || component == null || !canonical(component.name) || configuration == null ||
        operation == null || !canonical(operation.service) || !canonical(operation.operation) ||
        definitions == null || definitions.isEmpty || definitions.exists(_ == null) ||
        instances == null || checkpoints == null || codec == null || !canonical(codec.typeIdentity))
      Consequence.configurationInvalid("Terminal closure composition is incomplete")
    else for {
      accepted <- configuration.validateC
      _ <- accepted.delivery match {
        case _: DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Terminal closure requires same-store delivery")
      }
      _ <- (instances, checkpoints) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Terminal closure requires distinct EntityStore collections")
      }
      _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, checkpoints)
      owner <- DataStoreComponentOwner.create(component.name)
    } yield new WorkflowManagedTerminalClosureContinuation(subsystem, component, accepted, operation,
      definitions, instances, checkpoints, codec, owner)
}
