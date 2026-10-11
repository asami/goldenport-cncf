package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Host-owned dispatch output. Returned profile diagnostics are not execution authority. */
final case class SkillWorkResult[R](
  runId: StateMachineRunIdentity,
  continuationId: ContinuationIdentity,
  expectedRevision: StateMachineRevision,
  contextSnapshot: ContextSnapshot,
  result: TypedValue[R],
  resultReference: ContextReference,
  completionFacts: Vector[ContextReference],
  evidenceReferences: Vector[ContextReference],
  workerIdentity: Option[String],
  modelIdentity: Option[String],
  selectedWorkerProfile: String,
  mappingPolicyVersion: String,
  execution: Option[ExecutionObservation] = None
)

/** Converts a Host completion without selecting a transition or proving execution.
  * An actual executor observation is transported unchanged when supplied.
  * @since   Sep. 25, 2026
  * @version Oct.  9, 2026
  */
final class SkillWorkResultNormalizerV1[W, R]
    extends ContinuationSpiAdapter[W, SkillWorkResult[R], R] {
  def admitC(
    issued: WorkflowInteraction[W, Nothing],
    submission: SkillWorkResult[R]
  ): Consequence[ContinuationResult[R]] =
    if (issued == null || submission == null)
      Consequence.stateConflict("Skill WorkResult normalization is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        val dispatch = SkillDispatchEvidence(
          work.requirement, submission.selectedWorkerProfile, submission.mappingPolicyVersion
        )
        Consequence.success(ContinuationResult(
            issued.handle, submission.runId, submission.continuationId,
            submission.expectedRevision, submission.contextSnapshot,
            submission.result, submission.resultReference, submission.completionFacts,
            ExecutionEvidence(submission.evidenceReferences, submission.workerIdentity,
              submission.modelIdentity, Some(dispatch), submission.execution)
          ))
      case _ => Consequence.stateConflict("Skill WorkResult requires an issued WorkOrder")
    }
}
