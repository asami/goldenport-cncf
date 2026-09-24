package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class WorkflowProtocolV1Spec extends AnyWordSpec with Matchers {
  import WorkflowProtocolV1.*

  "Workflow Protocol V1" should {
    "retain a stable Handle and reject an incomplete generic Start request" in {
      val handle = _handle
      val start = WorkflowStartRequest(
        StateMachineOperationIdentity("WorkflowService", "beginReview"),
        handle.workflowIdentity,
        handle.workflowRevision,
        TypedValue("ReviewContext", "input-reference"),
        "explicit-selection-1",
        "idempotency-1"
      )

      handle.validateC shouldBe Consequence.success(handle)
      start.validateC shouldBe Consequence.success(start)
      start.copy(invocationReference = "").validateC shouldBe a[Consequence.Failure[_]]
      handle.copy(protocolVersion = "unknown").validateC shouldBe a[Consequence.Failure[_]]
    }

    "round-trip a profile-bound Start request and reject incompatible wire input" in {
      val handle = _handle
      val operation = StateMachineOperationIdentity("WorkflowService", "beginReview")
      val start = WorkflowStartRequest(
        operation, handle.workflowIdentity, handle.workflowRevision,
        TypedValue("ReviewContext", "review-input"), "selection-1", "start-once-1"
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewContext string")
      }
      val wire = WorkflowStartJsonV1.encodeC(start, codec).toOption.getOrElse(
        fail("profile Start must encode")
      )

      WorkflowStartJsonV1.decodeBoundC(wire, codec, operation,
        handle.workflowIdentity, handle.workflowRevision) shouldBe Consequence.success(start)
      WorkflowStartJsonV1.decodeBoundC(wire, codec,
        StateMachineOperationIdentity("WorkflowService", "other"),
        handle.workflowIdentity, handle.workflowRevision) shouldBe a[Consequence.Failure[_]]
      WorkflowStartJsonV1.decodeBoundC(wire, codec, operation,
        handle.workflowIdentity, WorkflowDefinitionRevision("other")) shouldBe a[Consequence.Failure[_]]
      WorkflowStartJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowStartJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowStartJsonV1.decodeC(wire.replace("\"ReviewContext\"", "\"WrongContext\""), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowStartJsonV1.decodeC(wire.replace("\"review-input\"", "1"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowStartJsonV1.encodeC(start.copy(input = TypedValue("WrongContext", "review-input")), codec) shouldBe
        a[Consequence.Failure[_]]
    }

    "project only a post-commit claimed Continuation into a token-free WorkOrder" in {
      val runtime = new ContinuationRuntime.InMemory
      val unitOfWork = new UnitOfWork(ExecutionContext.create())
      val continuation = _continuation
      val input = Some(TypedValue("ReviewContext", "review-input"))
      val completionOperation = StateMachineOperationIdentity("WorkflowService", "submitReview")
      val requirement = ExecutionRequirement(
        Vector(CapabilityRequirement("review")), RiskLevel("standard"),
        ReasoningLevel.Deep, reviewRequired = true
      )
      val presentation = MinimalPresentation("Review change", "Waiting for a reviewer")

      runtime.stageSuspensionC(unitOfWork, continuation) shouldBe Consequence.unit
      runtime.claimC(continuation.continuationId) shouldBe a[Consequence.Failure[_]]
      unitOfWork.commit() shouldBe Consequence.unit
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(
        fail("committed Continuation must be claimable")
      )
      val projected = projectClaimedWorkOrderC(
        _handle, claim, input, completionOperation, requirement, presentation
      ).toOption.getOrElse(fail("matching claimed Continuation must project"))

      projected.handle shouldBe _handle
      projected.current.kind shouldBe "WORK_ORDER"
      val workOrder = projected.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      workOrder.request.continuationId shouldBe continuation.continuationId
      workOrder.request.expectedRevision shouldBe continuation.expectedRevision
      workOrder.request.context.snapshot shouldBe continuation.context.snapshot
      workOrder.request.input shouldBe input
      workOrder.request.operation shouldBe continuation.requiredOperation.operation
      workOrder.request.resultType shouldBe continuation.requiredOperation.resultType
      workOrder.request.completionOperation shouldBe completionOperation
      workOrder.requirement.reasoning shouldBe ReasoningLevel.Deep
      projected.toString should not include claim.claimId

      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewContext string")
      }
      val wire = WorkflowWorkOrderJsonV1.encodeC(projected, codec).toOption.getOrElse(
        fail("claimed WorkOrder must encode")
      )
      wire should not include claim.claimId
      WorkflowWorkOrderJsonV1.decodeC(wire, codec) shouldBe Consequence.success(projected)
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("\"ReviewContext\"", "\"WrongContext\""), codec) shouldBe a[Consequence.Failure[_]]

      projectClaimedWorkOrderC(
        _handle.copy(workflowRevision = WorkflowDefinitionRevision("other")),
        claim, input, completionOperation, requirement, presentation
      ) shouldBe a[Consequence.Failure[_]]
      projectClaimedWorkOrderC(
        _handle, claim, Some(TypedValue("WrongContext", "review-input")),
        completionOperation, requirement, presentation
      ) shouldBe a[Consequence.Failure[_]]
    }

    "round-trip a separate-turn result and reject mismatched revision, evidence, and schema" in {
      val continuation = _continuation
      val evidenceRef = ContextReference("review-evidence", "1")
      val completionRef = ContextReference("review-completion", "1")
      val request = ContinuationRequest[String](
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.requiredOperation.identity, continuation.requiredOperation.operation,
        Some(TypedValue("ReviewContext", "review-input")),
        continuation.requiredOperation.resultType, continuation.context,
        CompletionContract("review-completion", Vector(completionRef)),
        EvidenceContract("review-evidence", Vector(evidenceRef)),
        StateMachineOperationIdentity("WorkflowService", "submitReview")
      )
      val issued = WorkflowInteraction[String, Nothing](
        _handle,
        WorkflowContinuation.WorkOrder(
          request,
          ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("standard"), ReasoningLevel.Deep, true),
          MinimalPresentation("Review change", "Waiting for a reviewer")
        )
      )
      val submitted = ContinuationResult(
        _handle, continuation.runId, continuation.continuationId,
        continuation.expectedRevision, continuation.context.snapshot,
        TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
        Vector(completionRef), ExecutionEvidence(Vector(evidenceRef), Some("codex"), Some("selected-model"))
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewResult string")
      }
      val json = WorkflowResultJsonV1.encodeC(submitted, codec).toOption.getOrElse(fail("result must encode"))
      WorkflowResultJsonV1.decodeC(json, codec) shouldBe Consequence.success(submitted)
      admitResultC(issued, submitted) shouldBe Consequence.success(
        StateMachineOperationResult(StateMachineResultTypeReference("ReviewResult"), submitted.resultReference)
      )
      WorkflowResultJsonV1.decodeC(json.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowResultJsonV1.decodeC(json.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowResultJsonV1.decodeC(json.replace("\"approved\"", "1"), codec) shouldBe a[Consequence.Failure[_]]
      admitResultC(issued, submitted.copy(expectedRevision = StateMachineRevision("2"))) shouldBe a[Consequence.Failure[_]]
      admitResultC(issued, submitted.copy(evidence = ExecutionEvidence(Vector.empty))) shouldBe a[Consequence.Failure[_]]
      admitResultC(issued, submitted.copy(result = TypedValue("WrongResult", "approved"))) shouldBe a[Consequence.Failure[_]]
    }

    "resume a verified WorkOrder through a recovered private claim after runtime recreation" in {
      val persistence = new ContinuationRuntimePersistence.InMemory
      val original = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation
      val unitOfWork = new UnitOfWork(ExecutionContext.create())
      original.stageSuspensionC(unitOfWork, continuation) shouldBe Consequence.unit
      unitOfWork.commit() shouldBe Consequence.unit
      val claim = new PersistentContinuationRuntime(persistence)
        .claimC(continuation.continuationId).toOption.getOrElse(fail("claim must persist"))
      val issued = projectClaimedWorkOrderC(
        _handle, claim, Some(TypedValue("ReviewContext", "review-input")),
        StateMachineOperationIdentity("WorkflowService", "submitReview"),
        ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("standard"), ReasoningLevel.Deep, true),
        MinimalPresentation("Review change", "Waiting for a reviewer")
      ).toOption.getOrElse(fail("claimed work must project"))
      val submitted = ContinuationResult(
        issued.handle, continuation.runId, continuation.continuationId,
        continuation.expectedRevision, continuation.context.snapshot,
        TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
        Vector.empty, ExecutionEvidence(Vector.empty)
      )
      val recovered = new PersistentContinuationRuntime(persistence)
      var opened = 0
      val fresh = () => {
        opened += 1
        new UnitOfWork(ExecutionContext.create())
      }

      resumeIssuedWorkOrderC(issued, submitted.copy(expectedRevision = StateMachineRevision("2")), recovered, fresh) shouldBe
        a[Consequence.Failure[_]]
      val work = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val altered = issued.copy(current = work.copy(request = work.request.copy(
        operation = StateMachineOperationIdentity("WorkflowService", "other")
      )))
      resumeIssuedWorkOrderC(altered, submitted, recovered, fresh) shouldBe a[Consequence.Failure[_]]
      opened shouldBe 0
      recovered.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)

      resumeIssuedWorkOrderC(issued, submitted, recovered, fresh) shouldBe Consequence.success(
        ContinuationRuntime.Resume(continuation, StateMachineOperationResult(
          StateMachineResultTypeReference("ReviewResult"), ContextReference("review-result", "1")
        ))
      )
      opened shouldBe 1
      resumeIssuedWorkOrderC(issued, submitted, new PersistentContinuationRuntime(persistence), fresh) shouldBe
        a[Consequence.Failure[_]]
      opened shouldBe 1
    }
  }

  private def _handle: WorkflowHandle =
    WorkflowHandle(
      ComponentId("org.goldenport.cncf.test.WorkflowProtocolV1Spec"),
      WorkflowDefinitionIdentity("WorkflowProducer"),
      WorkflowDefinitionRevision("workflow-producer-v1"),
      InstanceIdentity("instance-1")
    )

  private def _continuation: Continuation = {
    val operation = StateMachineRequiredOperation(
      StateMachineRequiredOperationIdentity("review-change-capability"),
      "ReviewChange",
      StateMachineOperationIdentity("WorkflowService", "reviewChange"),
      Some(StateMachineInputTypeReference("ReviewContext")),
      Some(StateMachineResultTypeReference("ReviewResult")),
      StateMachineRequiredOperationMetadata(
        ContextContract("review-context", Vector.empty, Vector.empty),
        CompletionContract("review-completion", Vector.empty),
        EvidenceContract("review-evidence", Vector.empty),
        Vector.empty
      )
    )
    Continuation(
      StateMachineRunIdentity("run-1"),
      ContinuationIdentity("continuation-1"),
      StateMachineRevision("1"),
      operation,
      ContextBundle("review", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
    )
  }
}
