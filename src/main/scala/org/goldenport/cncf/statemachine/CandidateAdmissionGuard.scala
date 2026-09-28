package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionEvidence, AdmissionRequirement, AdmissionSubmission, EvidencePolicy}

/** Optional CAM admission check executed as an ordinary StateMachine guard.
  * The event may carry a candidate and evidence, but it cannot supply an
  * admitted result or choose the transition target.
  * @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionGuard[State, Event, C, E, Scope](
  submission: (State, Event) => Consequence[AdmissionSubmission[C, E, Scope]],
  requirements: (State, Event) => Consequence[Vector[AdmissionRequirement[Scope]]],
  availableEvidence: (State, Event) => Consequence[Vector[AdmissionEvidence[E, Scope]]],
  policy: EvidencePolicy[E, Scope]
) extends Guard[State, Event] {
  def eval(state: State, event: Event): Consequence[Boolean] =
    if (submission == null || requirements == null || availableEvidence == null || policy == null)
      Consequence.stateConflict("Candidate admission guard is incomplete")
    else for {
      submitted <- submission(state, event)
      required <- requirements(state, event)
      available <- availableEvidence(state, event)
      evaluated <- CandidateAdmissionModel.evaluateC(submitted, required, policy, available)
    } yield evaluated.gaps.isEmpty
}
