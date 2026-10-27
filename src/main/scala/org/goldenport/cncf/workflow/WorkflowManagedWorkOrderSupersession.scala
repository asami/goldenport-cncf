package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, HistoryEntry, InstanceIdentity, InstanceRecord, InstanceRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowInteraction}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Typed owning disposition of an application-admitted WorkOrder. The application owns authority,
  * admissible correction, execution assessment and the later normal issue.
  * Available/unclaimed is not proof of external nonexecution; this operation
  * neither stops external work nor issues a replacement.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  */
final class WorkflowManagedWorkOrderSupersession[W, A] private (
  engine: WorkflowManagedContinuationSupersession[W, A]
) {
  import WorkflowManagedWorkOrderSupersession.*
  def executeC(identity: ContinuationIdentity, expected: InstanceRevision, expectedEntityRevision: EntityRevision,
    current: InstanceRecord,
    entry: HistoryEntry, payload: TypedValue[A])(using ExecutionContext): Consequence[Retained[A]] =
    engine.executeC(identity, expected, Some(expectedEntityRevision), current, entry, payload).map(_retained)

  /** Exact pending native facts only; Available/unclaimed does not prove nonexecution. */
  def inspectC(identity: ContinuationIdentity, instance: InstanceIdentity)(using ExecutionContext):
      Consequence[Option[Pending[W]]] = engine.inspectC(identity, instance).map(_.map(value =>
        new Pending(value.current, value.issued, value.continuation, value.entityRevision)))

  /** Historical collection confers no new effect or execution authority. */
  def loadRetainedC(identity: ContinuationIdentity, instance: InstanceIdentity)(using ExecutionContext):
      Consequence[Option[Retained[A]]] = engine.loadRetainedC(identity, instance).map(_.map(_retained))

  private def _retained(value: WorkflowManagedContinuationSupersession.Retained[A]): Retained[A] =
    new Retained(Receipt(value.receipt.identity, value.receipt.sourceRevision,
      value.receipt.nextRevision, value.receipt.payload), value.current)
}

object WorkflowManagedWorkOrderSupersession {
  final class Pending[W] private[WorkflowManagedWorkOrderSupersession](val current: InstanceRecord,
    val issued: WorkflowInteraction[W, Nothing], val continuation: ContinuationRuntimePersistence.Record,
    val entityRevision: EntityRevision)
  final case class Receipt[A](identity: ContinuationIdentity, sourceRevision: InstanceRevision,
    nextRevision: InstanceRevision, payload: A)
  final class Retained[A] private[WorkflowManagedWorkOrderSupersession](val receipt: Receipt[A], val current: InstanceRecord)

  def bindC[W, A](subsystem: Subsystem, component: ComponentId, configuration: Configuration,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], codec: PayloadCodec[A]):
      Consequence[WorkflowManagedWorkOrderSupersession[W, A]] =
    WorkflowManagedContinuationSupersession.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, codec, WorkflowManagedContinuationSupersession.Policy.WorkOrder)
      .map(new WorkflowManagedWorkOrderSupersession(_))
}
