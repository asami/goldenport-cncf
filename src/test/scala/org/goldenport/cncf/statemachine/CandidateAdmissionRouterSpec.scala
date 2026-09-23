package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.{AdmittedJudgmentResult, AlternativeIdentity, Evidence, EvidenceFreshness, EvidenceProvenance, EvidenceScope, JudgmentActionIdentity, JudgmentResult, Rationale}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @version Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class CandidateAdmissionRouterSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import CandidateAdmissionRouter.*

  "Candidate-Admission StateMachine router" should {
    "select the explicit StateMachine target for an already admitted result" in {
      Given("an admitted JudgmentResult and an explicit StateMachine route table")
      val result = _admitted("approve")
      val target = CmlStateMachineTransitionTarget.State(_approved)
      val routes = Vector(Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), target))
      When("the pure StateMachine router selects the result's declared route")
      val routed = CandidateAdmissionRouter.routeC(result, routes)
      Then("only the matching CmlStateMachineTransitionTarget is returned")
      _success(routed) shouldBe target
    }

    "fail closed when the StateMachine route is absent or ambiguous" in {
      Given("an admitted result with no matching route and with duplicate matching routes")
      val result = _admitted("reject")
      val target = CmlStateMachineTransitionTarget.State(_rejected)
      val missingroutes = Vector(Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), target))
      val ambiguousroutes = Vector(
        Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("reject"), target),
        Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("reject"), CmlStateMachineTransitionTarget.Final)
      )
      When("the StateMachine owns route selection without a worker callback or Provider")
      val missing = CandidateAdmissionRouter.routeC(result, missingroutes)
      val ambiguous = CandidateAdmissionRouter.routeC(result, ambiguousroutes)
      Then("no worker-selected route can cross an incomplete or ambiguous route table")
      _diagnostic(missing).code shouldBe DiagnosticCode.MissingRoute
      _diagnostic(ambiguous).code shouldBe DiagnosticCode.AmbiguousRoute
    }

    "fail closed when a matching StateMachine route has no target" in {
      Given("an admitted result and a matching route whose target is null")
      val result = _admitted("approve")
      val routes = Vector(Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), null))
      When("the pure StateMachine router selects the matching route")
      val routed = CandidateAdmissionRouter.routeC(result, routes)
      Then("the router reports the stable MissingTarget diagnostic")
      _diagnostic(routed).code shouldBe DiagnosticCode.MissingTarget
    }
  }

  private val _machine = CmlStateMachineIdentity("OrderProgress")
  private val _approved = CmlStateMachineStateIdentity(_machine, CmlStateMachineStatePath(Vector("Approved")))
  private val _rejected = CmlStateMachineStateIdentity(_machine, CmlStateMachineStatePath(Vector("Rejected")))

  private def _admitted(alternative: String): AdmittedJudgmentResult = {
    val artifact = _success(CandidateAdmissionProducerAbi.parseC(_fixture))
    _success(CandidateAdmissionProducerAbi.admitJudgmentResultC(
      artifact,
      JudgmentResult(
        JudgmentActionIdentity("judge-payment"),
        AlternativeIdentity(alternative),
        Rationale("decision-rationale"),
        Evidence("payment-evidence"),
        EvidenceScope("order"),
        EvidenceFreshness("current"),
        EvidenceProvenance("payment-ledger")
      )
    ))
  }

  private def _fixture: String = {
    val stream = Option(getClass.getResourceAsStream("/workflow/candidate-admission-producer-abi.json"))
      .getOrElse(fail("candidate-admission producer ABI fixture is missing"))
    val source = scala.io.Source.fromInputStream(stream, "UTF-8")
    try source.mkString finally source.close()
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail("expected Candidate-Admission result to succeed"))

  private def _diagnostic[A](result: Consequence[A]): Diagnostic =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: CandidateAdmissionRouterException) => exception.diagnostic
          case other => fail(s"expected Candidate-Admission router diagnostic but got $other")
        }
      case _ => fail("expected Candidate-Admission router to fail")
    }
}
