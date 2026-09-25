package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/** @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = CandidateRef("candidate-1", "revision-2")
  private val _candidate = CandidateSnapshot(_target, TypedValue("CandidatePayload", "draft"))
  private val _requirement = AdmissionRequirement(
    "semantic-review", RequirementKind.Semantic, "ReviewEvidence", TypedValue("Scope", 3)
  )
  private val _policy = new EvidencePolicy[String, Int] {
    def isFresh(evidence: AdmissionEvidence[String, Int], target: CandidateRef): Boolean =
      evidence.provenance != "expired"
    def covers(evidence: AdmissionEvidence[String, Int], requirement: AdmissionRequirement[Int]): Boolean =
      evidence.scope.value >= requirement.scope.value
  }
  private def _evidence(
    evidencetarget: CandidateRef = _target,
    evidencetype: String = "ReviewEvidence",
    scope: Int = 3,
    provenance: String = "review-run-1"
  ): AdmissionEvidence[String, Int] =
    AdmissionEvidence("evidence-1", "semantic-review", evidencetarget,
      TypedValue(evidencetype, "approved"), TypedValue("Scope", scope), provenance)

  "Candidate Admission Model" should {
    "admit matching fresh and sufficiently covered evidence without choosing a transition" in {
      Given("a candidate with matching fresh evidence and an application-owned coverage policy")
      val submission = AdmissionSubmission("submission-1", _candidate, Vector(_evidence()))
      When("the candidate is evaluated against its semantic requirement")
      val evaluation = evaluateC(submission, Vector(_requirement), _policy)
      Then("the evidence is accepted without selecting a transition")
      evaluation shouldBe Consequence.success(
        AdmissionEvaluation(_target, Vector("evidence-1"), Vector.empty)
      )
      evaluation.toOption.get.result shouldBe
        AdmissionResult.Admitted(_target, Vector("evidence-1"))
    }

    "leave a typed semantic gap for missing or stale evidence" in {
      Given("a candidate with either no evidence or expired evidence")
      val missing = AdmissionSubmission("submission-1", _candidate, Vector.empty[AdmissionEvidence[String, Int]])
      val stale = missing.copy(evidence = Vector(_evidence(provenance = "expired")))
      When("each submission is evaluated")
      val missingresult = evaluateC(missing, Vector(_requirement), _policy).toOption.get.result
      val staleresult = evaluateC(stale, Vector(_requirement), _policy).toOption.get.result
      Then("each unmet requirement remains a typed semantic gap")
      missingresult shouldBe
        AdmissionResult.Pending(_target, Vector(AdmissionGap(_requirement, Vector(EvidenceDefect.Missing))))
      staleresult shouldBe
        AdmissionResult.Pending(_target, Vector(AdmissionGap(_requirement, Vector(EvidenceDefect.Stale))))
    }

    "re-evaluate the same submission with later evidence without resubmission" in {
      Given("an original submission without evidence and later matching evidence")
      val original = AdmissionSubmission("submission-1", _candidate,
        Vector.empty[AdmissionEvidence[String, Int]])
      When("the same submission is evaluated before and after evidence arrives")
      val first = evaluateC(original, Vector(_requirement), _policy).toOption.get
      val resumed = evaluateC(original, Vector(_requirement), _policy, Vector(_evidence())).toOption.get

      Then("the later evaluation admits without mutating or resubmitting the original")
      first.gaps.map(_.requirement.identity) shouldBe Vector("semantic-review")
      resumed.result shouldBe AdmissionResult.Admitted(_target, Vector("evidence-1"))
      original.evidence shouldBe Vector.empty
    }

    "fail closed for wrong target, evidence type, or insufficient application-owned scope" in {
      Given("evidence with a wrong revision, wrong type, or insufficient scope")
      val wrongtarget = AdmissionSubmission("submission-1", _candidate,
        Vector(_evidence(evidencetarget = CandidateRef("candidate-1", "revision-1"))))
      val wrongtype = AdmissionSubmission("submission-1", _candidate,
        Vector(_evidence(evidencetype = "OtherEvidence")))
      val narrow = AdmissionSubmission("submission-1", _candidate, Vector(_evidence(scope = 2)))
      When("each submission is evaluated")
      val targetdefects = evaluateC(wrongtarget, Vector(_requirement), _policy).toOption.get.gaps.head.defects
      val typedefects = evaluateC(wrongtype, Vector(_requirement), _policy).toOption.get.gaps.head.defects
      val scopedefects = evaluateC(narrow, Vector(_requirement), _policy).toOption.get.gaps.head.defects
      Then("none of the incompatible evidence is admitted")
      targetdefects should contain (
        EvidenceDefect.WrongTarget)
      typedefects should contain (
        EvidenceDefect.WrongType)
      scopedefects should contain (
        EvidenceDefect.InsufficientCoverage)
    }

    "reject a mismatched scope type and fail closed when the application policy throws" in {
      Given("evidence with a mismatched scope type and a policy that throws")
      val wrongscopetype = _evidence().copy(scope = TypedValue("DifferentScope", 3))
      val brokenpolicy = new EvidencePolicy[String, Int] {
        def isFresh(value: AdmissionEvidence[String, Int], target: CandidateRef): Boolean =
          throw new IllegalStateException("unavailable freshness source")
        def covers(value: AdmissionEvidence[String, Int], required: AdmissionRequirement[Int]): Boolean = true
      }

      When("the malformed and policy-failing submissions are evaluated")
      val scopedefects = evaluateC(AdmissionSubmission("submission-1", _candidate, Vector(wrongscopetype)),
        Vector(_requirement), _policy).toOption.get.gaps.head.defects
      val policydefects = evaluateC(AdmissionSubmission("submission-1", _candidate, Vector(_evidence())),
        Vector(_requirement), brokenpolicy).toOption.get.gaps.head.defects
      Then("both paths fail closed with the corresponding defects")
      scopedefects should contain (EvidenceDefect.WrongScopeType)
      policydefects should contain (EvidenceDefect.Stale)
    }

    "reject a duplicate evidence identity across submission and later evidence" in {
      Given("a submission and later evidence carrying the same evidence identity")
      val submission = AdmissionSubmission("submission-1", _candidate, Vector(_evidence()))
      When("the later evidence is merged for evaluation")
      val evaluation = evaluateC(submission, Vector(_requirement), _policy, Vector(_evidence()))
      Then("the duplicate identity is rejected")
      evaluation shouldBe
        a[Consequence.Failure[?]]
    }

    "reject malformed submissions and duplicate requirements before evaluation" in {
      Given("a submission with duplicate evidence and an input with duplicate requirements")
      val submission = AdmissionSubmission("submission-1", _candidate, Vector(_evidence(), _evidence()))
      When("each invalid input is evaluated")
      val duplicateevidence = evaluateC(submission, Vector(_requirement), _policy)
      val duplicaterequirements = evaluateC(submission.copy(evidence = Vector.empty),
        Vector(_requirement, _requirement), _policy)
      Then("neither invalid input can produce an admission")
      duplicateevidence shouldBe a[Consequence.Failure[?]]
      duplicaterequirements shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
