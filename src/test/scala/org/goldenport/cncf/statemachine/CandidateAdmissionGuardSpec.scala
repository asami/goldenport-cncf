package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/** @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionGuardSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = CandidateRef("draft", "r1")
  private val _submission = AdmissionSubmission("submit-1",
    CandidateSnapshot(_target, TypedValue("Draft", "content")),
    Vector.empty[AdmissionEvidence[String, Int]])
  private val _requirement = AdmissionRequirement(
    "review", RequirementKind.Semantic, "Review", TypedValue("Scope", 2))
  private val _evidence = AdmissionEvidence(
    "review-1", "review", _target, TypedValue("Review", "accepted"),
    TypedValue("Scope", 2), "reviewer-1")
  private val _policy = new EvidencePolicy[String, Int] {
    def isFresh(value: AdmissionEvidence[String, Int], target: CandidateRef): Boolean = true
    def covers(value: AdmissionEvidence[String, Int], required: AdmissionRequirement[Int]): Boolean =
      value.scope.value >= required.scope.value
  }
  private final case class State(available: Vector[AdmissionEvidence[String, Int]])

  "Candidate Admission Guard" should {
    "gate an existing canonical StateMachine transition without selecting its target" in {
      Given("a declared transition guarded by admission of a submitted candidate")
      val guard = new CandidateAdmissionGuard[State, Unit, String, String, Int](
        (_, _) => Consequence.success(_submission),
        (_, _) => Consequence.success(Vector(_requirement)),
        (state, _) => Consequence.success(state.available),
        _policy
      )
      val transition = Vector(TransitionCandidate("application-transition", 0, 0))

      When("the selector evaluates the guard without and then with accepted evidence")
      val missing = guard.eval(State(Vector.empty), ())
      val withheld = TransitionSelector.selectCanonical(transition)(_ => guard.eval(State(Vector.empty), ()))
      val selected = TransitionSelector.selectCanonical(transition)(_ => guard.eval(State(Vector(_evidence)), ()))
      Then("only the selector may choose the existing transition after admission")
      missing shouldBe Consequence.success(false)
      withheld shouldBe Consequence.success(None)
      selected shouldBe Consequence.success(Some("application-transition"))
    }
  }
}
