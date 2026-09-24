package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult

/** Pure, candidate-workflow-specific projection of an admitted State target
  * into the next durable WorkflowInstance history entry. The StateMachine
  * target is the selector; presentation text and worker output are not.
  */
object CandidateWorkflowProgression {
  val schemaVersion: String = "cncf.candidate-workflow-progression.v1"

  def nextC[R](
    record: InstanceRecord,
    submitted: ContinuationResult[R],
    target: CmlStateMachineTransitionTarget
  ): Consequence[InstanceRecord] =
    if (record == null || submitted == null || submitted.handle == null ||
        submitted.continuationId == null || submitted.resultReference == null ||
        target == null)
      Consequence.stateConflict("Candidate workflow progression input is incomplete")
    else for {
      current <- record.validateC
      definition <- current.definition.admittedCandidateDefinition match {
        case Some(value) => CandidateWorkflowAbi.admitC(value)
        case None => Consequence.stateConflict("WorkflowInstance is not bound to a Candidate-Admission workflow")
      }
      _ <- if (submitted.handle.instanceIdentity == current.identity &&
          submitted.handle.workflowIdentity == current.definition.workflowIdentity &&
          submitted.handle.workflowRevision == current.definition.workflowRevision)
        Consequence.unit
      else Consequence.stateConflict("Judgment result Handle differs from WorkflowInstance")
      _ <- if (current.lifecycle == Lifecycle.Active &&
          current.suspension.exists(_.continuationIdentity.value == submitted.continuationId.value))
        Consequence.unit
      else Consequence.stateConflict("WorkflowInstance is not suspended for this Judgment result")
      progression <- _progression_c(definition.workflow, target)
      _ <- if (_name(submitted.resultReference.identity) &&
          _name(submitted.resultReference.revision)) Consequence.unit
      else Consequence.stateConflict("Judgment result reference is incomplete")
      next <- current.appendC(current.revision, HistoryEntry(
        sequence = HistorySequence(current.history.size.toLong + 1L),
        revision = current.revision.next,
        lifecycle = Lifecycle.Active,
        currentProgression = Some(progression),
        correlation = current.correlation,
        causation = CausationReference(
          s"judgment-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"
        ),
        derivedCompositeOccurrence = current.derivedCompositeOccurrence,
        committedPredecessor = current.committedPredecessor,
        suspension = None
      ))
    } yield next

  private def _progression_c(
    workflow: CandidateWorkflowAbi.Workflow,
    target: CmlStateMachineTransitionTarget
  ): Consequence[ProgressionReference] = target match {
    case CmlStateMachineTransitionTarget.State(state)
        if state != null && state.machine != null && state.path != null &&
          state.machine.name == workflow.identity &&
          state.path.segments.size == 1 && workflow.states.contains(state.path.segments.head) =>
      val encoded = Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "machine" -> Json.fromString(state.machine.name),
        "path" -> Json.arr(state.path.segments.map(Json.fromString)*)
      ).noSpaces
      Consequence.success(ProgressionReference(encoded))
    case _ =>
      Consequence.stateConflict("StateMachine target is not a declared Candidate-Admission workflow state")
  }

  private def _name(value: String): Boolean =
    value != null && value.trim.nonEmpty
}
