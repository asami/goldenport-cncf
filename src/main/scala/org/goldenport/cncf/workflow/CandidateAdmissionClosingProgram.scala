package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.statemachine.{CandidateAdmissionRouter, CmlStateMachineTransitionTarget, ResolvedAction}
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.TypedJudgmentResultV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult

/** Selects a closing Action through an admitted Judgment result and an
  * explicit StateMachine route. The submitted result cannot name an Action.
  * Selection is pure; the completion runtime interprets the selected program
  * in its fresh resume UnitOfWork.
  */
object CandidateAdmissionClosingProgram {
  final case class Binding(
    target: CmlStateMachineTransitionTarget,
    action: ResolvedAction[InstanceRecord, ContinuationResult[TypedJudgmentResultV1]]
  )

  def bindC[W](
    artifact: CandidateAdmissionProducerAbi.Artifact,
    routes: Vector[CandidateAdmissionRouter.Route],
    issuedPersistence: IssuedWorkOrderPersistence[W],
    bindings: Vector[Binding]
  ): Consequence[WorkflowCompletionServiceOperation.ClosingProgram[TypedJudgmentResultV1]] =
    if (artifact == null || routes == null || issuedPersistence == null || bindings == null ||
        bindings.isEmpty || bindings.exists(b => b == null || b.target == null || b.action == null) ||
        bindings.map(_.target).distinct.size != bindings.size)
      Consequence.configurationInvalid("Candidate-Admission closing Action bindings are incomplete or ambiguous")
    else CandidateAdmissionProducerAbi.admitC(artifact).map { admitted =>
      new WorkflowCompletionServiceOperation.ClosingProgram[TypedJudgmentResultV1] {
        def selectC(
          submitted: ContinuationResult[TypedJudgmentResultV1],
          current: InstanceRecord
        ): Consequence[WorkflowCompletionServiceOperation.SelectedClosing] =
          if (submitted == null || submitted.continuationId == null || current == null)
            Consequence.stateConflict("Candidate-Admission closing Action input is incomplete")
          else for {
            loaded <- issuedPersistence.loadC(submitted.continuationId)
            issued <- loaded match {
              case Some(value) if value != null => Consequence.success(value)
              case _ => Consequence.stateConflict("Candidate-Admission closing Action has no issued WorkOrder")
            }
            target <- CandidateAdmissionRouter.routeSubmittedC(admitted, issued, submitted, routes)
            next <- CandidateWorkflowProgression.nextC(current, submitted, target)
            binding <- bindings.find(_.target == target) match {
              case Some(value) => Consequence.success(value)
              case None => Consequence.stateConflict("StateMachine target has no closing Action binding")
            }
            program <- Option(binding.action.program(current, submitted)) match {
              case Some(value) => Consequence.success(value)
              case None => Consequence.stateConflict("StateMachine closing Action program is missing")
            }
          } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
      }
    }
}
