package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen

final class WorkflowProtocolV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  import WorkflowProtocolV1.*

  "Workflow Protocol V1" should {
    "retain a stable Handle and reject an incomplete generic Start request" in {
      Given("a stable Handle and an explicitly selected generic Start request")
      val handle = _handle
      val start = WorkflowStartRequest(
        StateMachineOperationIdentity("WorkflowService", "beginReview"),
        handle.workflowIdentity,
        handle.workflowRevision,
        TypedValue("ReviewContext", "input-reference"),
        "explicit-selection-1",
        "idempotency-1"
      )

      When("their typed admission validators are invoked")
      val admittedHandle = handle.validateC
      val admittedStart = start.validateC
      val missingInvocation = start.copy(invocationReference = "").validateC
      val unknownProtocol = handle.copy(protocolVersion = "unknown").validateC

      Then("matching identities pass while missing invocation and unknown protocol versions fail")
      admittedHandle shouldBe Consequence.success(handle)
      admittedStart shouldBe Consequence.success(start)
      missingInvocation shouldBe a[Consequence.Failure[_]]
      unknownProtocol shouldBe a[Consequence.Failure[_]]
    }

    "round-trip a profile-bound Start request and reject incompatible wire input" in {
      Given("a profile-bound Start request and its application-owned payload codec")
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
      When("the Start request is encoded for a separate turn")
      val wire = WorkflowStartJsonV1.encodeC(start, codec).toOption.getOrElse(
        fail("profile Start must encode")
      )

      Then("only the selected Operation, Workflow revision, schema, and payload decode")
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

    "round-trip typed Terminal output and reject incompatible or unknown wire fields" in {
      Given("a completed Workflow interaction and a typed Terminal payload codec")
      val terminal = WorkflowInteraction[Nothing, String](
        _handle,
        WorkflowContinuation.Terminal(
          TypedValue("ReviewResult", "approved"),
          MinimalPresentation("Review complete", "Approved")
        )
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewResult string")
      }
      When("the Terminal interaction is encoded")
      val wire = WorkflowTerminalJsonV1.encodeC(terminal, codec).toOption.getOrElse(
        fail("typed Terminal must encode")
      )

      Then("the matching document round-trips and incompatible documents fail closed")
      WorkflowTerminalJsonV1.decodeC(wire, codec) shouldBe Consequence.success(terminal)
      WorkflowTerminalJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.decodeC(wire.replace("\"ReviewResult\"", "\"WrongResult\""), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.decodeC(wire.replace("\"approved\"", "1"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.decodeC(wire.replace("\"Approved\"", "\"\""), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.encodeC(terminal.copy(handle = _handle.copy(protocolVersion = "unknown")), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowTerminalJsonV1.encodeC(terminal.copy(current = WorkflowContinuation.Terminal(
        TypedValue("WrongResult", "approved"), MinimalPresentation("Review complete", "Approved")
      )), codec) shouldBe a[Consequence.Failure[_]]
    }

    "round-trip Wait status without granting resume authority" in {
      Given("a Wait interaction with a current Handle and ContextSnapshot")
      val wait = WorkflowInteraction[Nothing, Nothing](
        _handle,
        WorkflowContinuation.Wait(
          StateMachineRevision("2"),
          ContextSnapshot("workflow-producer-v1", Some("model-1")),
          MinimalPresentation("Waiting for review", "No external result yet")
        )
      )
      When("the Wait status is encoded")
      val wire = WorkflowWaitJsonV1.encodeC(wait).toOption.getOrElse(
        fail("Wait status must encode")
      )

      Then("only the matching status document decodes; stale or incomplete state is rejected")
      WorkflowWaitJsonV1.decodeC(wire) shouldBe Consequence.success(wait)
      WorkflowWaitJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown")) shouldBe
        a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":")) shouldBe
        a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.decodeC(wire.replace("\"WAIT\"", "\"WORK_ORDER\"")) shouldBe
        a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.decodeC(wire.replace(
        "\"contextSnapshot\":{\"workflowRevision\":\"workflow-producer-v1\"",
        "\"contextSnapshot\":{\"workflowRevision\":\"stale\""
      )) shouldBe a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.decodeC(wire.replace("\"expectedRevision\":\"2\"",
        "\"expectedRevision\":\"\"")) shouldBe a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.decodeC(wire.replace("\"No external result yet\"", "null")) shouldBe
        a[Consequence.Failure[_]]
      WorkflowWaitJsonV1.encodeC(wait.copy(current = WorkflowContinuation.Wait(
        StateMachineRevision("2"), ContextSnapshot("stale"),
        MinimalPresentation("Waiting for review", "No external result yet")
      ))) shouldBe a[Consequence.Failure[_]]
    }

    "round-trip Decision without admitting it as a WorkOrder result" in {
      Given("a Decision request with typed input and no WorkOrder execution requirement")
      val continuation = _continuation
      val request = ContinuationRequest[String](
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.requiredOperation.identity, continuation.requiredOperation.operation,
        Some(TypedValue("ReviewContext", "review-input")),
        continuation.requiredOperation.resultType, continuation.context,
        CompletionContract("review-completion", Vector.empty),
        EvidenceContract("review-evidence", Vector.empty),
        StateMachineOperationIdentity("WorkflowService", "submitReview")
      )
      val decision = WorkflowInteraction[String, Nothing](
        _handle,
        WorkflowContinuation.Decision(
          request, MinimalPresentation("Review decision", "A decision is requested")
        )
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewContext string")
      }
      When("the Decision request is encoded")
      val wire = WorkflowDecisionJsonV1.encodeC(decision, codec).toOption.getOrElse(
        fail("Decision must encode")
      )

      Then("the Decision round-trips but cannot be decoded as a WorkOrder")
      WorkflowDecisionJsonV1.decodeC(wire, codec) shouldBe Consequence.success(decision)
      WorkflowWorkOrderJsonV1.decodeC(wire, codec) shouldBe a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.decodeC(wire.replace("\"DECISION\"", "\"WORK_ORDER\""), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.decodeC(wire.replace("\"ReviewContext\"", "\"WrongContext\""), codec) shouldBe
        a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.decodeC(wire.replace(
        "\"context\":{\"summary\":\"review\"", "\"context\":{\"summary\":\"\""
      ), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowDecisionJsonV1.encodeC(decision.copy(current = WorkflowContinuation.Decision(
        request.copy(context = request.context.copy(snapshot = ContextSnapshot("stale"))),
        MinimalPresentation("Review decision", "A decision is requested")
      )), codec) shouldBe a[Consequence.Failure[_]]

      Given("a typed Result that would otherwise match the suspended Continuation")
      val submitted = ContinuationResult(
        _handle, continuation.runId, continuation.continuationId,
        continuation.expectedRevision, continuation.context.snapshot,
        TypedValue("ReviewResult", "approved"), ContextReference("review-result", "1"),
        Vector.empty, ExecutionEvidence(Vector.empty)
      )
      Then("the Decision cannot be submitted through WorkOrder result admission")
      admitResultC(decision, submitted) shouldBe a[Consequence.Failure[_]]
    }

    "dispatch one current-Continuation wire response through the closed variant codecs" in {
      Given("typed WorkOrder, Decision, Wait, and Terminal interactions")
      val continuation = _continuation
      val request = ContinuationRequest[String](
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.requiredOperation.identity, continuation.requiredOperation.operation,
        Some(TypedValue("ReviewContext", "review-input")),
        continuation.requiredOperation.resultType, continuation.context,
        CompletionContract("review-completion", Vector.empty),
        EvidenceContract("review-evidence", Vector.empty),
        StateMachineOperationIdentity("WorkflowService", "submitReview")
      )
      val presentation = MinimalPresentation("Review change", "Waiting for a reviewer")
      val workcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewContext")
      }
      val terminalcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = value.asString.toRight("expected ReviewResult")
      }
      val interactions = Vector[WorkflowInteraction[String, String]](
        WorkflowInteraction(_handle, WorkflowContinuation.WorkOrder(
          request,
          ExecutionRequirement(Vector(CapabilityRequirement("review")), RiskLevel("standard"), ReasoningLevel.Deep, true),
          presentation
        )),
        WorkflowInteraction(_handle, WorkflowContinuation.Decision(request, presentation)),
        WorkflowInteraction(_handle, WorkflowContinuation.Wait(
          StateMachineRevision("2"), continuation.context.snapshot, presentation
        )),
        WorkflowInteraction(_handle, WorkflowContinuation.Terminal(
          TypedValue("ReviewResult", "approved"), MinimalPresentation("Review complete", "Approved")
        ))
      )

      When("each interaction is encoded by the closed response entry point")
      interactions.foreach { interaction =>
        val wire = WorkflowInteractionJsonV1.encodeC(interaction, workcodec, terminalcodec)
          .toOption.getOrElse(fail(s"${interaction.current.kind} must encode"))
        Then("its matching decoder accepts it and rejects unknown schema or fields")
        WorkflowInteractionJsonV1.decodeC(wire, workcodec, terminalcodec) shouldBe
          Consequence.success(interaction)
        WorkflowInteractionJsonV1.decodeC(
          wire.replace("cncf.workflow-protocol.v1", "unknown"), workcodec, terminalcodec
        ) shouldBe a[Consequence.Failure[_]]
        WorkflowInteractionJsonV1.decodeC(
          wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), workcodec, terminalcodec
        ) shouldBe a[Consequence.Failure[_]]
      }
      Then("unknown variants and invalid JSON do not acquire response authority")
      WorkflowInteractionJsonV1.decodeC(
        "{\"schemaVersion\":\"cncf.workflow-protocol.v1\",\"kind\":\"UNKNOWN\"}",
        workcodec, terminalcodec
      ) shouldBe a[Consequence.Failure[_]]
      WorkflowInteractionJsonV1.decodeC("not-json", workcodec, terminalcodec) shouldBe
        a[Consequence.Failure[_]]
    }

    "project only a post-commit claimed Continuation into a token-free WorkOrder" in {
      Given("a staged Continuation, typed requirement, and display-only Presentation")
      val runtime = new ContinuationRuntime.InMemory
      val unitofwork = new UnitOfWork(ExecutionContext.create())
      val continuation = _continuation
      val input = Some(TypedValue("ReviewContext", "review-input"))
      val completionoperation = StateMachineOperationIdentity("WorkflowService", "submitReview")
      val requirement = ExecutionRequirement(
        Vector(CapabilityRequirement("review")), RiskLevel("standard"),
        ReasoningLevel.Deep, reviewRequired = true
      )
      val presentation = MinimalPresentation("Review change", "Waiting for a reviewer")

      When("the UnitOfWork commits and the Continuation becomes claimable")
      val staged = runtime.stageSuspensionC(unitofwork, continuation)
      val prematureClaim = runtime.claimC(continuation.continuationId)
      val committed = unitofwork.commit()
      val claim = runtime.claimC(continuation.continuationId).toOption.getOrElse(
        fail("committed Continuation must be claimable")
      )
      val projected = projectClaimedWorkOrderC(
        _handle, claim, input, completionoperation, requirement, presentation
      ).toOption.getOrElse(fail("matching claimed Continuation must project"))

      Then("the projected WorkOrder preserves request facts without exposing its claim token")
      staged shouldBe Consequence.unit
      prematureClaim shouldBe a[Consequence.Failure[_]]
      committed shouldBe Consequence.unit
      projected.handle shouldBe _handle
      projected.current.kind shouldBe "WORK_ORDER"
      val workorder = projected.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      workorder.request.continuationId shouldBe continuation.continuationId
      workorder.request.expectedRevision shouldBe continuation.expectedRevision
      workorder.request.context.snapshot shouldBe continuation.context.snapshot
      workorder.request.input shouldBe input
      workorder.request.operation shouldBe continuation.requiredOperation.operation
      workorder.request.resultType shouldBe continuation.requiredOperation.resultType
      workorder.request.completionOperation shouldBe completionoperation
      workorder.requirement.reasoning shouldBe ReasoningLevel.Deep
      projected.toString should not include claim.claimId

      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewContext"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewContext string")
      }
      When("the projected WorkOrder crosses the Skill-facing JSON boundary")
      val wire = WorkflowWorkOrderJsonV1.encodeC(projected, codec).toOption.getOrElse(
        fail("claimed WorkOrder must encode")
      )
      Then("the strict codec round-trips only compatible schema and payload facts")
      wire should not include claim.claimId
      WorkflowWorkOrderJsonV1.decodeC(wire, codec) shouldBe Consequence.success(projected)
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("cncf.workflow-protocol.v1", "unknown"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("\"kind\":", "\"unknown\":1,\"kind\":"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowWorkOrderJsonV1.decodeC(wire.replace("\"ReviewContext\"", "\"WrongContext\""), codec) shouldBe a[Consequence.Failure[_]]

      Then("a mismatched Workflow revision or input cannot be projected")
      projectClaimedWorkOrderC(
        _handle.copy(workflowRevision = WorkflowDefinitionRevision("other")),
        claim, input, completionoperation, requirement, presentation
      ) shouldBe a[Consequence.Failure[_]]
      projectClaimedWorkOrderC(
        _handle, claim, Some(TypedValue("WrongContext", "review-input")),
        completionoperation, requirement, presentation
      ) shouldBe a[Consequence.Failure[_]]
    }

    "round-trip a separate-turn result and reject mismatched revision, evidence, and schema" in {
      Given("an issued WorkOrder and a typed separate-turn result with required evidence")
      val continuation = _continuation
      val evidenceref = ContextReference("review-evidence", "1")
      val completionref = ContextReference("review-completion", "1")
      val request = ContinuationRequest[String](
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.requiredOperation.identity, continuation.requiredOperation.operation,
        Some(TypedValue("ReviewContext", "review-input")),
        continuation.requiredOperation.resultType, continuation.context,
        CompletionContract("review-completion", Vector(completionref)),
        EvidenceContract("review-evidence", Vector(evidenceref)),
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
        Vector(completionref), ExecutionEvidence(Vector(evidenceref), Some("codex"), Some("selected-model"))
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewResult string")
      }
      When("the result is encoded and checked against the issued WorkOrder")
      val json = WorkflowResultJsonV1.encodeC(submitted, codec).toOption.getOrElse(fail("result must encode"))
      Then("the matching Result is admitted while schema, revision, evidence, and type drift fail")
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

    "normalize a Skill completion into typed result and dispatch evidence" in {
      Given("an issued WorkOrder and Host-selected Skill completion evidence")
      val continuation = _continuation
      val completionref = ContextReference("review-completion", "1")
      val evidenceref = ContextReference("review-evidence", "1")
      val requirement = ExecutionRequirement(
        Vector(CapabilityRequirement("review")), RiskLevel("standard"),
        ReasoningLevel.Deep, reviewRequired = true
      )
      val request = ContinuationRequest[String](
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.requiredOperation.identity, continuation.requiredOperation.operation,
        Some(TypedValue("ReviewContext", "review-input")),
        continuation.requiredOperation.resultType, continuation.context,
        CompletionContract("review-completion", Vector(completionref)),
        EvidenceContract("review-evidence", Vector(evidenceref)),
        StateMachineOperationIdentity("WorkflowService", "submitReview")
      )
      val issued = WorkflowInteraction[String, Nothing](
        _handle,
        WorkflowContinuation.WorkOrder(
          request, requirement, MinimalPresentation("Review change", "Waiting for a reviewer")
        )
      )
      val completion = SkillWorkResult(
        continuation.runId, continuation.continuationId, continuation.expectedRevision,
        continuation.context.snapshot, TypedValue("ReviewResult", "approved"),
        ContextReference("review-result", "1"), Vector(completionref), Vector(evidenceref),
        Some("codex"), Some("model-selected-by-host"), "reviewer-profile-v1", "mapping-v1"
      )
      val normalizer = new SkillWorkResultNormalizerV1[String, String]
      When("the completion is normalized against the issued WorkOrder")
      val normalized = normalizer.admitC(issued, completion).toOption.getOrElse(
        fail("Skill completion must normalize")
      )
      Then("the result retains the requirement and selected dispatch evidence")
      normalized.evidence.skillDispatch shouldBe Some(SkillDispatchEvidence(
        requirement, "reviewer-profile-v1", "mapping-v1"
      ))
      normalized.handle shouldBe issued.handle
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "ReviewResult"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] =
          value.asString.toRight("expected ReviewResult string")
      }
      When("the normalized result crosses the separate-turn JSON boundary")
      val wire = WorkflowResultJsonV1.encodeC(normalized, codec).toOption.getOrElse(
        fail("normalized Skill result must encode")
      )
      Then("only complete compatible dispatch evidence is admitted")
      WorkflowResultJsonV1.decodeC(wire, codec) shouldBe Consequence.success(normalized)
      WorkflowResultJsonV1.decodeC(wire.replace("\"mappingPolicyVersion\":\"mapping-v1\"",
        "\"mappingPolicyVersion\":null"), codec) shouldBe a[Consequence.Failure[_]]
      WorkflowResultJsonV1.decodeC(wire.replace("\"skillDispatch\":{",
        "\"skillDispatch\":{\"unknown\":true,"), codec) shouldBe a[Consequence.Failure[_]]

      normalizer.admitC(issued, completion.copy(expectedRevision = StateMachineRevision("stale"))) shouldBe
        a[Consequence.Failure[_]]
      normalizer.admitC(issued, completion.copy(evidenceReferences = Vector.empty)) shouldBe
        a[Consequence.Failure[_]]
      normalizer.admitC(issued, completion.copy(selectedWorkerProfile = "")) shouldBe
        a[Consequence.Failure[_]]
      normalizer.admitC(issued, completion.copy(mappingPolicyVersion = "")) shouldBe
        a[Consequence.Failure[_]]
      admitResultC(issued, normalized.copy(evidence = normalized.evidence.copy(
        skillDispatch = Some(SkillDispatchEvidence(
          requirement.copy(reasoning = ReasoningLevel.Routine), "reviewer-profile-v1", "mapping-v1"
        ))
      ))) shouldBe a[Consequence.Failure[_]]

      val anotherprofile = normalizer.admitC(issued, completion.copy(
        selectedWorkerProfile = "different-host-profile", modelIdentity = Some("other-model")
      )).toOption.getOrElse(fail("another host profile must not change result admission"))
      Then("a different valid Host profile does not change Workflow result admission")
      admitResultC(issued, anotherprofile) shouldBe admitResultC(issued, normalized)
    }

    "resume a verified WorkOrder through a recovered private claim after runtime recreation" in {
      Given("a persisted suspension and a claim recovered in another runtime instance")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val original = new PersistentContinuationRuntime(persistence)
      val continuation = _continuation
      val unitofwork = new UnitOfWork(ExecutionContext.create())
      original.stageSuspensionC(unitofwork, continuation) shouldBe Consequence.unit
      unitofwork.commit() shouldBe Consequence.unit
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

      When("stale revisions and altered Operations attempt to resume")
      val stale = resumeIssuedWorkOrderC(
        issued, submitted.copy(expectedRevision = StateMachineRevision("2")), recovered, fresh
      )
      val work = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val altered = issued.copy(current = work.copy(request = work.request.copy(
        operation = StateMachineOperationIdentity("WorkflowService", "other")
      )))
      val incompatible = resumeIssuedWorkOrderC(altered, submitted, recovered, fresh)
      val retainedClaim = recovered.recoverClaimC(continuation.continuationId)

      Then("they are rejected before any fresh UnitOfWork opens")
      stale shouldBe a[Consequence.Failure[_]]
      incompatible shouldBe a[Consequence.Failure[_]]
      opened shouldBe 0
      retainedClaim shouldBe Consequence.success(claim)

      When("the matching Result resumes through the recovered private claim")
      val resumed = resumeIssuedWorkOrderC(issued, submitted, recovered, fresh)
      val duplicate = resumeIssuedWorkOrderC(
        issued, submitted, new PersistentContinuationRuntime(persistence), fresh
      )

      Then("one fresh UnitOfWork opens and a duplicate resume fails")
      resumed shouldBe Consequence.success(
        ContinuationRuntime.Resume(continuation, StateMachineOperationResult(
          StateMachineResultTypeReference("ReviewResult"), ContextReference("review-result", "1")
        ))
      )
      opened shouldBe 1
      duplicate shouldBe a[Consequence.Failure[_]]
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
