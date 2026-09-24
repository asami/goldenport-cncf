package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.{CompletionContract, ContextBundle, ContextReference, ContextSnapshot, ContinuationIdentity, EvidenceContract, StateMachineOperationIdentity, StateMachineRequiredOperationIdentity, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.{AdmittedJudgmentResult, AlternativeIdentity, Evidence, EvidenceFreshness, EvidenceProvenance, EvidenceScope, JudgmentActionIdentity, JudgmentResult, Rationale, TypeIdentity, TypedJudgmentResultV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
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

    "route an admitted typed result without letting its worker choose the target" in {
      Given("a typed result admitted against the declared Operation result type")
      val artifact = _success(CandidateAdmissionProducerAbi.parseC(_fixture))
      val base = JudgmentResult(
        JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"),
        Rationale("decision-rationale"), Evidence("payment-evidence"),
        EvidenceScope("order"), EvidenceFreshness("current"), EvidenceProvenance("payment-ledger")
      )
      val result = _success(CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(
        artifact,
        TypedJudgmentResultV1(CandidateAdmissionProducerAbi.acceptedTypedJudgmentResultSchemaVersion,
          base, TypeIdentity("PaymentResult"), ContextReference("payment-result", "1"))
      ))
      val target = CmlStateMachineTransitionTarget.State(_approved)
      val routes = Vector(Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), target))

      When("the StateMachine routes the admitted decision")
      _success(CandidateAdmissionRouter.routeTypedC(result, routes)) shouldBe target
    }

    "admit a separate-turn JudgmentResult against the issued WorkOrder before routing" in {
      val artifact = _success(CandidateAdmissionProducerAbi.parseC(_fixture))
      val handle = WorkflowHandle(
        ComponentId("org.goldenport.cncf.test.OrderComponent"), WorkflowDefinitionIdentity("OrderProgress"),
        WorkflowDefinitionRevision("workflow-v1"), InstanceIdentity("order-1")
      )
      val run = StateMachineRunIdentity("order-run")
      val continuation = ContinuationIdentity("payment-review")
      val revision = StateMachineRevision("1")
      val context = ContextBundle("payment", Vector.empty, Vector.empty, ContextSnapshot("payment-v1"))
      val request = ContinuationRequest[String](
        run, continuation, revision, StateMachineRequiredOperationIdentity("capture-payment-capability"),
        StateMachineOperationIdentity("OrderService", "capturePayment"), None,
        Some(StateMachineResultTypeReference("PaymentResult")), context,
        CompletionContract("payment-completion", Vector.empty),
        EvidenceContract("payment-evidence", Vector.empty),
        StateMachineOperationIdentity("OrderService", "submitPaymentReview")
      )
      val issued = WorkflowInteraction[String, Nothing](
        handle, WorkflowContinuation.WorkOrder(
          request,
          ExecutionRequirement(Vector.empty, RiskLevel("standard"), ReasoningLevel.Standard, true),
          MinimalPresentation("Review payment", "Waiting for judgment")
        )
      )
      val base = JudgmentResult(
        JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"),
        Rationale("decision-rationale"), Evidence("payment-evidence"),
        EvidenceScope("order"), EvidenceFreshness("current"), EvidenceProvenance("payment-ledger")
      )
      val typed = TypedJudgmentResultV1(
        CandidateAdmissionProducerAbi.acceptedTypedJudgmentResultSchemaVersion,
        base, TypeIdentity("PaymentResult"), ContextReference("payment-result", "1")
      )
      val submitted = ContinuationResult(
        handle, run, continuation, revision, context.snapshot,
        TypedValue("PaymentResult", typed), ContextReference("payment-result", "1"),
        Vector.empty, ExecutionEvidence(Vector.empty)
      )
      val approved = CmlStateMachineTransitionTarget.State(_approved)
      val rejected = CmlStateMachineTransitionTarget.State(_rejected)
      val routes = Vector(
        Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), approved),
        Route(JudgmentActionIdentity("judge-payment"), AlternativeIdentity("reject"), rejected)
      )

      _success(routeSubmittedC(artifact, issued, submitted, routes)) shouldBe approved
      val rejectedResult = submitted.copy(result = TypedValue("PaymentResult", typed.copy(
        result = base.copy(selectedAlternative = AlternativeIdentity("reject"))
      )))
      _success(routeSubmittedC(artifact, issued, rejectedResult, routes)) shouldBe rejected
      routeSubmittedC(artifact, issued, submitted.copy(
        result = TypedValue("WrongResult", typed)
      ), routes) shouldBe a[Consequence.Failure[_]]
      routeSubmittedC(artifact, issued, submitted.copy(
        result = TypedValue("PaymentResult", typed.copy(payloadType = TypeIdentity("WrongResult")))
      ), routes) shouldBe a[Consequence.Failure[_]]
      routeSubmittedC(artifact, issued, submitted.copy(
        result = TypedValue("PaymentResult", typed.copy(
          result = base.copy(selectedAlternative = AlternativeIdentity("unknown"))
        ))
      ), routes) shouldBe a[Consequence.Failure[_]]
      routeSubmittedC(artifact, issued, submitted.copy(
        handle = handle.copy(instanceIdentity = InstanceIdentity("another-order"))
      ), routes) shouldBe a[Consequence.Failure[_]]
      routeSubmittedC(artifact, issued, submitted.copy(
        resultReference = ContextReference("different-result", "1")
      ), routes) shouldBe a[Consequence.Failure[_]]
      val foreignHandle = handle.copy(workflowIdentity = WorkflowDefinitionIdentity("OtherWorkflow"))
      routeSubmittedC(artifact, issued.copy(handle = foreignHandle),
        submitted.copy(handle = foreignHandle), routes) shouldBe a[Consequence.Failure[_]]
      val work = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      routeSubmittedC(artifact, issued.copy(current = work.copy(request = work.request.copy(
        operation = StateMachineOperationIdentity("OrderService", "otherOperation")
      ))), submitted, routes) shouldBe a[Consequence.Failure[_]]
      routeSubmittedC(artifact, issued.copy(current = work.copy(request = work.request.copy(
        requiredOperation = StateMachineRequiredOperationIdentity("other-capability")
      ))), submitted, routes) shouldBe a[Consequence.Failure[_]]
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
