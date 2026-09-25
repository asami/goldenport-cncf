package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Host-owned dispatch output. Its profile choice is evidence, never a Workflow selector. */
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
  mappingPolicyVersion: String
)

/** Adapts a Skill/Host completion to the common typed result after issued-work admission. */
final class SkillWorkResultNormalizerV1[W, R]
    extends ContinuationSpiAdapter[W, SkillWorkResult[R], R] {
  def admitC(
    issued: WorkflowInteraction[W, Nothing],
    submission: SkillWorkResult[R]
  ): Consequence[ContinuationResult[R]] =
    if (issued == null || issued.handle == null || submission == null ||
        submission.result == null || submission.workerIdentity == null ||
        submission.modelIdentity == null || submission.completionFacts == null ||
        submission.evidenceReferences == null)
      Consequence.stateConflict("Skill WorkResult normalization is incomplete")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] if work.request != null &&
          work.requirement != null =>
        val dispatch = SkillDispatchEvidence(
          work.requirement, submission.selectedWorkerProfile, submission.mappingPolicyVersion
        )
        for {
          _ <- dispatch.validateC
          normalized = ContinuationResult(
            issued.handle, submission.runId, submission.continuationId,
            submission.expectedRevision, submission.contextSnapshot,
            submission.result, submission.resultReference, submission.completionFacts,
            ExecutionEvidence(submission.evidenceReferences, submission.workerIdentity,
              submission.modelIdentity, Some(dispatch))
          )
          _ <- WorkflowProtocolV1.admitResultC(issued, normalized)
        } yield normalized
      case _ => Consequence.stateConflict("Skill WorkResult requires an issued WorkOrder")
    }
}
