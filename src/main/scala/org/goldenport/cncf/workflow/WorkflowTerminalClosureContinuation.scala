package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.{Reference, Receipt, canonical}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.model.datatype.EntityRevision

/** Application-admitted closure only. Targets and retained provenance are data,
  * not business approval, nonexecution observations or execution authority.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
object WorkflowTerminalClosureContinuation {
  final case class Target(component: ComponentId, instance: InstanceIdentity,
    definitionIdentity: WorkflowDefinitionIdentity, definitionRevision: WorkflowDefinitionRevision,
    correlation: CorrelationReference, terminalSequence: HistorySequence,
    terminalRevision: InstanceRevision, terminalCausation: CausationReference, entityRevision: EntityRevision)
  final case class Intent[A](target: Target, reference: Reference, progression: ProgressionReference,
    causation: CausationReference, payload: TypedValue[A])

  /** One target from actual loaded native facts. It is not a transferable grant. */
  private[workflow] def targetC(component: ComponentId, current: InstanceRecord): Consequence[Target] = for {
    _ <- if (component != null && canonical(component.name) && current != null) Consequence.unit
      else Consequence.stateConflict("Terminal continuation owner or source is absent")
    _ <- current.validateC
    terminal <- current.history.lastOption.filter(e => current.lifecycle == Lifecycle.Completed &&
      current.suspension.isEmpty && e.lifecycle == Lifecycle.Completed && e.suspension.isEmpty)
      .map(Consequence.success).getOrElse(Consequence.stateConflict("Terminal continuation requires an actual unsuspended Completed frontier"))
    revision <- if (current.revision.value < Long.MaxValue) EntityRevision.createC(current.revision.value + 1L)
      else Consequence.stateConflict("Terminal continuation native revision is exhausted")
  } yield Target(component, current.identity, current.definition.workflowIdentity, current.definition.workflowRevision,
    current.correlation, terminal.sequence, terminal.revision, terminal.causation, revision)

  private[workflow] def admitC[A](component: ComponentId, operation: StateMachineOperationIdentity,
    current: InstanceRecord, intent: Intent[A]): Consequence[Receipt[A]] = for {
    actual <- targetC(component, current)
    _ <- if (intent != null && intent.target == actual && intent.reference != null && intent.progression != null &&
      canonical(intent.progression.value) && intent.causation != null && canonical(intent.causation.value) &&
      intent.payload != null && operation != null && canonical(operation.service) && canonical(operation.operation)) Consequence.unit
      else Consequence.stateConflict("Terminal continuation crosses its exact native target, definition, correlation or revisions")
    _ <- WorkflowMechanicalTransition.keyC(intent.reference)
    _ <- intent.payload.validateC
    states = current.definition.admittedCandidateDefinition.map(_.workflow.states)
      .orElse(current.definition.admittedEntryDefinition.map(_.states.map(_.identity)))
      .getOrElse(current.definition.admittedDefinition.compositeStateMachine.states.map(_.identity.value))
    _ <- if (states.contains(intent.progression.value)) Consequence.unit
      else Consequence.stateConflict("Terminal continuation progression is absent from the admitted execution definition")
    marker = TerminalClosureProvenance(component.name, operation.service, operation.operation,
      intent.reference.identity, intent.reference.revision, actual.terminalSequence, actual.terminalRevision, actual.terminalCausation)
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      Lifecycle.Active, Some(intent.progression), current.correlation, intent.causation,
      current.derivedCompositeOccurrence, current.committedPredecessor, None, Some(marker))
    next <- current._append_terminal_closure_c(current.revision, entry)
  } yield Receipt(intent.reference, component, operation, current, next, intent.payload)

  /** Validate passive receipt against its complete original native prefix. */
  private[workflow] def validateReceiptC[A](receipt: Receipt[A]): Consequence[Receipt[A]] = for {
    _ <- if (receipt != null && receipt.next != null && receipt.payload != null) Consequence.unit
      else Consequence.stateConflict("Terminal continuation receipt is absent")
    _ <- receipt.next.validateC
    target <- targetC(receipt.component, receipt.current)
    entry <- receipt.next.history.lastOption.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Terminal continuation receipt has no successor entry"))
    progression <- entry.currentProgression.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Terminal continuation receipt has no active progression"))
    expected <- admitC(receipt.component, receipt.operation, receipt.current,
      Intent(target, receipt.reference, progression, entry.causation, receipt.payload))
    _ <- if (receipt.next.identity == receipt.current.identity &&
      receipt.next.definition.workflowIdentity == receipt.current.definition.workflowIdentity &&
      receipt.next.definition.workflowRevision == receipt.current.definition.workflowRevision &&
      receipt.next.history == expected.next.history && receipt.next.revision == expected.next.revision)
      Consequence.unit else Consequence.stateConflict("Terminal continuation receipt successor differs from its original history")
  } yield receipt

  private[workflow] def collectC[A](receipt: Receipt[A], current: InstanceRecord): Consequence[Receipt[A]] = for {
    _ <- validateReceiptC(receipt)
    _ <- current.validateC
    _ <- if (current.identity == receipt.next.identity &&
      current.definition.workflowIdentity == receipt.next.definition.workflowIdentity &&
      current.definition.workflowRevision == receipt.next.definition.workflowRevision &&
      current.correlation == receipt.next.correlation && current.history.take(receipt.next.history.size) == receipt.next.history)
      Consequence.unit else Consequence.stateConflict("Terminal continuation receipt is not part of the actual native history")
  } yield receipt
}
