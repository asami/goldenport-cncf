package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Generic execution requirements survive the issued and returned boundaries.
  * These specs are not proof of installed concrete AI provider execution.
  * @since   Oct.  8, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowExecutionDispositionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _handle = WorkflowHandle(ComponentId("org.goldenport.fixture.ExecutionDisposition"), WorkflowDefinitionIdentity("workflow"),
    WorkflowDefinitionRevision("v1"), InstanceIdentity("instance"))
  private val _snapshot = ContextSnapshot("v1")
  private val _legacy = ExecutionRequirement(Vector(CapabilityRequirement("semantic-work")), RiskLevel("SOURCE"),
    ReasoningLevel.Deep, reviewRequired = true)
  private def _requirement(placement: ExecutionPlacement, independence: ExecutionIndependence) =
    _legacy.copy(disposition = Some(ExecutionDisposition(placement, independence, "origin")),
      reasoningClass = Some(ReasoningClassReference("ReviewDeep")))
  private def _codec(identity: String) = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = identity
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("semantic payload must be a string")
  }
  private val _input_codec = _codec("SemanticInput")
  private val _result_codec = _codec("SemanticResult")
  private def _work(requirement: ExecutionRequirement): WorkflowInteraction[String, Nothing] =
    WorkflowInteraction(_handle, WorkflowContinuation.WorkOrder(
      ContinuationRequest(StateMachineRunIdentity("run"), ContinuationIdentity("work"), StateMachineRevision("2"),
        StateMachineRequiredOperationIdentity("semantic-work"), StateMachineOperationIdentity("SemanticService", "work"),
        Some(TypedValue("SemanticInput", "assigned input")), Some(StateMachineResultTypeReference("SemanticResult")),
        ContextBundle("Assigned work", Vector.empty, Vector.empty, _snapshot),
        CompletionContract("completion", Vector.empty), EvidenceContract("evidence", Vector.empty),
        StateMachineOperationIdentity("WorkflowService", "submit")), requirement, MinimalPresentation("Work", "Return result")))
  private def _result(requirement: ExecutionRequirement, execution: Option[ExecutionObservation]): ContinuationResult[String] =
    ContinuationResult(_handle, StateMachineRunIdentity("run"), ContinuationIdentity("work"), StateMachineRevision("2"),
      _snapshot, TypedValue("SemanticResult", "completed"), ContextReference("result", "1"), Vector.empty,
      ExecutionEvidence(Vector.empty, Some("worker"), Some("model"),
        Some(SkillDispatchEvidence(requirement, "profile", "mapping-v1")), execution))
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:workflow-execution-disposition, example:$example, rules:$rules, phase:80")

  "Workflow execution disposition" should {
    "E1 preserve requirements and actual observations on both wire routes" must _metadata("E1", "R1,R2,R3") {
      "for independently selected context/profile identities" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R1,R2,R3; Example: E1; explicit delegated independent work")
        val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { suffix =>
          val requirement = _requirement(ExecutionPlacement.Delegated, ExecutionIndependence.Required)
          val work = _work(requirement)
          val observation = ExecutionObservation(ExecutionPlacement.Delegated, "origin", "worker-" + suffix, "profile-" + suffix)
          val result = _result(requirement, Some(observation))
          val workroundtrip = WorkflowWorkOrderJsonV1.encodeC(work, _input_codec)
            .flatMap(WorkflowWorkOrderJsonV1.decodeC(_, _input_codec))
          val resultroundtrip = WorkflowResultJsonV1.encodeC(result, _result_codec)
            .flatMap(WorkflowResultJsonV1.decodeC(_, _result_codec))
          workroundtrip.toOption.contains(work) && resultroundtrip.toOption.contains(result) &&
            WorkflowProtocolV1.admitResultC(work, result).toOption.nonEmpty
        }
        When("issued and returned contract projections are interpreted over generated identities")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("both routes preserve the same requirement, observation and normal result admission")
        checked.passed shouldBe true
      }
    }
    "E2 retain legacy absence without granting newly required execution authority" must _metadata("E2", "R2,R3") {
      "when original records contain only the original fields" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R2,R3; Example: E2; original typed work/result without execution extension")
        val work = _work(_legacy)
        val result = _result(_legacy, None)
        When("legacy wire records are read and considered for new explicitly independent work")
        val workwire = WorkflowWorkOrderJsonV1.encodeC(work, _input_codec).toOption.get
        val resultwire = WorkflowResultJsonV1.encodeC(result, _result_codec).toOption.get
        val decoded = WorkflowResultJsonV1.decodeC(resultwire, _result_codec).toOption.get
        val newrequirement = _requirement(ExecutionPlacement.Delegated, ExecutionIndependence.Required)
        val missing = decoded.copy(evidence = decoded.evidence.copy(
          skillDispatch = Some(SkillDispatchEvidence(newrequirement, "profile", "mapping-v1"))))
        val rejected = WorkflowProtocolV1.admitResultC(_work(newrequirement), missing)
        Then("old history stays readable and the missing new observation cannot satisfy independent work")
        WorkflowWorkOrderJsonV1.decodeC(workwire, _input_codec) shouldBe Consequence.success(work)
        decoded shouldBe result
        workwire should not include "disposition"
        resultwire should not include "executorContextIdentity"
        rejected shouldBe a[Consequence.Failure[_]]
      }
    }
    "E3 distinguish placement and independence from model equality" must _metadata("E3", "R1,R2") {
      "when the selected profile identity is the same" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R1,R2; Example: E3; inline implementation and independent delegated review")
        val inline = _requirement(ExecutionPlacement.Inline, ExecutionIndependence.Optional)
        val independent = _requirement(ExecutionPlacement.Delegated, ExecutionIndependence.Required)
        val observations = Vector(
          (inline, ExecutionObservation(ExecutionPlacement.Inline, "origin", "origin", "profile"), true),
          (inline, ExecutionObservation(ExecutionPlacement.Inline, "origin", "other", "profile"), false),
          (independent, ExecutionObservation(ExecutionPlacement.Delegated, "origin", "other", "profile"), true),
          (independent, ExecutionObservation(ExecutionPlacement.Delegated, "origin", "origin", "profile"), false),
          (independent, ExecutionObservation(ExecutionPlacement.Delegated, "wrong-origin", "other", "profile"), false),
          (independent, ExecutionObservation(ExecutionPlacement.Inline, "origin", "other", "profile"), false),
          (independent, ExecutionObservation(ExecutionPlacement.Delegated, "origin", "", "profile"), false),
          (independent, ExecutionObservation(ExecutionPlacement.Delegated, "origin", "other", ""), true))
        When("normal result admission applies the selected runtime disposition")
        val outcomes = observations.map { (requirement, observation, expected) =>
          WorkflowProtocolV1.admitResultC(_work(requirement), _result(requirement, Some(observation))).toOption.nonEmpty -> expected
        }
        Then("actual context/placement satisfies the requirement; equal model or profile does not waive independence")
        outcomes.foreach { (actual, expected) => actual shouldBe expected }
      }
    }
    "E4 reject declared input contradictions rather than silently falling back" must _metadata("E4", "R1,R3") {
      "when current wire fields are malformed or undeclared" in {
        Given("Spec: docs/spec/workflow-execution-disposition.md; Rules: R1,R3; Example: E4; valid extended requirement/result wire")
        val requirement = _requirement(ExecutionPlacement.Delegated, ExecutionIndependence.Required)
        val workwire = WorkflowWorkOrderJsonV1.encodeC(_work(requirement), _input_codec).toOption.get
        val resultwire = WorkflowResultJsonV1.encodeC(_result(requirement,
          Some(ExecutionObservation(ExecutionPlacement.Delegated, "origin", "other", "profile"))), _result_codec).toOption.get
        When("contradictory declared fields enter either projection")
        val badwork = Vector(workwire.replace("DELEGATED", "UNKNOWN"), workwire.replace("REQUIRED", "UNKNOWN"),
          workwire.replace("\"reasoningClass\":\"ReviewDeep\"", "\"reasoningClass\":null"),
          workwire.replace("\"disposition\":{", "\"extraControl\":1,\"disposition\":{"))
          .map(WorkflowWorkOrderJsonV1.decodeC(_, _input_codec))
        val badresult = Vector(resultwire.replace("DELEGATED", "UNKNOWN"),
          resultwire.replace("\"executorContextIdentity\":\"other\"", "\"executorContextIdentity\":null"))
          .map(WorkflowResultJsonV1.decodeC(_, _result_codec))
        val diagnostic = WorkflowResultJsonV1.decodeC(resultwire.replace("\"profileIdentity\":\"profile\"",
          "\"profileIdentity\":\"\""), _result_codec)
        val blankexecutor = WorkflowResultJsonV1.decodeC(resultwire.replace("\"executorContextIdentity\":\"other\"",
          "\"executorContextIdentity\":\"\""), _result_codec)
        val consumed = blankexecutor.flatMap(WorkflowProtocolV1.admitResultC(_work(requirement), _))
        Then("unconvertible fields fail, diagnostics do not grant execution, and a blank independent executor is refused at consumption")
        (badwork ++ badresult).foreach(_ shouldBe a[Consequence.Failure[_]])
        diagnostic.isSuccess shouldBe true
        blankexecutor.isSuccess shouldBe true
        consumed shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
