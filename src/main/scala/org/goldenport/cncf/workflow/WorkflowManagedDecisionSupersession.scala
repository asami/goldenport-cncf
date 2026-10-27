package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, HistoryEntry, InstanceIdentity, InstanceRecord, InstanceRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/** Typed owning disposition of an unanswered Decision. The application owns authority,
  * admissible correction, execution assessment and the later normal issue.
  * Available/unclaimed is not proof of external nonexecution; this operation
  * neither stops external work nor issues a replacement.
  * @since   Oct. 20, 2026
  * @version Oct. 27, 2026
  */
final class WorkflowManagedDecisionSupersession[W, A] private (
  engine: WorkflowManagedContinuationSupersession[W, A]
) {
  import WorkflowManagedDecisionSupersession.*
  def executeC(identity: ContinuationIdentity, expected: InstanceRevision, current: InstanceRecord,
    entry: HistoryEntry, payload: TypedValue[A])(using ExecutionContext): Consequence[Retained[A]] =
    engine.executeC(identity, expected, None, current, entry, payload).map(_retained)

  /** Historical collection confers no new effect or execution authority. */
  def loadRetainedC(identity: ContinuationIdentity, instance: InstanceIdentity)(using ExecutionContext):
      Consequence[Option[Retained[A]]] = engine.loadRetainedC(identity, instance).map(_.map(_retained))

  private def _retained(value: WorkflowManagedContinuationSupersession.Retained[A]): Retained[A] =
    new Retained(Receipt(value.receipt.identity, value.receipt.sourceRevision,
      value.receipt.nextRevision, value.receipt.payload), value.current)
}

object WorkflowManagedDecisionSupersession {
  final case class Receipt[A](identity: ContinuationIdentity, sourceRevision: InstanceRevision,
    nextRevision: InstanceRevision, payload: A)
  final class Retained[A] private[WorkflowManagedDecisionSupersession](val receipt: Receipt[A], val current: InstanceRecord)

  def bindC[W, A](subsystem: Subsystem, component: ComponentId, configuration: Configuration,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], codec: PayloadCodec[A]):
      Consequence[WorkflowManagedDecisionSupersession[W, A]] =
    WorkflowManagedContinuationSupersession.bindC(subsystem, component, configuration, operation,
      definitions, instances, continuations, inputCodec, codec, WorkflowManagedContinuationSupersession.Policy.Decision)
      .map(new WorkflowManagedDecisionSupersession(_))
}
