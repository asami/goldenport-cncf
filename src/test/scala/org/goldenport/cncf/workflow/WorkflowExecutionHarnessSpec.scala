package org.goldenport.cncf.workflow

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import WorkflowExecutionHarness as H

/** Host observations here are specification-owned, never real provider evidence.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class WorkflowExecutionHarnessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _primary = WorkflowConcreteExecutionProfile("primary", "fixture", "primary-model", Map("effort" -> "bounded"))
  private val _alternate = _primary.copy(identity = "alternate", model = "alternate-model")
  private val _origin = WorkflowExecutorDescriptor("origin", _primary)
  private val _work = H.WorkReference(WorkflowHandle(ComponentId("org.fixture.Execution"), WorkflowDefinitionIdentity("work"),
    WorkflowDefinitionRevision("1"), InstanceIdentity("instance")), StateMachineRunIdentity("run"), ContinuationIdentity("next"), StateMachineRevision("7"))
  private def _take[A](value: Consequence[A]): A = value.toOption.get
  private def _mapping(alternate: Boolean = true, revision: String = "1", primary: WorkflowConcreteExecutionProfile = _primary) =
    _take(WorkflowExecutionProfileMapping.createC("fixture-mapping", revision, Vector(primary, _alternate),
      Vector(WorkflowExecutionProfileMapping.ClassBinding("ReviewStandard", primary.identity)),
      if (alternate) Vector(WorkflowExecutionProfileMapping.ClassBinding("ReviewStandard", _alternate.identity)) else Vector.empty))
  private def _requirement(review: Boolean = true, inline: Boolean = false) = ExecutionRequirement(
    Vector(CapabilityRequirement("semantic")), RiskLevel("bounded"), ReasoningLevel.Standard, review,
    Some(ExecutionDisposition(if (inline) ExecutionPlacement.Inline else ExecutionPlacement.Delegated,
      if (review) ExecutionIndependence.Required else ExecutionIndependence.Optional, "origin")),
    Some(ReasoningClassReference("ReviewStandard")))
  private def _session(alternate: Boolean = true, review: Boolean = true, inline: Boolean = false) =
    _take(H.prepareIssuedC(_work, _requirement(review, inline), _origin, _mapping(alternate)))
  private def _record(session: H.Session, status: H.Status, reason: H.Reason,
    executor: Option[WorkflowExecutorDescriptor] = None, resources: H.Resources = H.Resources()) =
    session.recordC(session.nextSelection.get, H.Outcome(status, reason, "Actual fixture host outcome", executor, resources))
  private def _complete(session: H.Session, resources: H.Resources = H.Resources()) =
    _record(session, H.Status.Completed, H.Reason.Completed,
      Some(WorkflowExecutorDescriptor(if (session.requirement.disposition.get.placement == ExecutionPlacement.Inline) "origin" else "independent",
        session.nextSelection.get.profile)), resources)

  "Finite host execution" should afterWord("in spec:workflow-execution-disposition, examples:E1,E2,E3,E4,E5,E6,E7,E8, rules:R9,R10,R11,R12,R13, phase:80") {
    "E1 consume issued INLINE and DELEGATED without re-resolving disposition" in {
      Given("R9: issued dispositions and the original mapping, including forced INLINE with a class absent from that snapshot")
      val inline = _requirement(review = false, inline = true).copy(reasoningClass = Some(ReasoningClassReference("OldClass")))
      When("the pure handoff prepares the actual issued work")
      val local = _take(H.prepareIssuedC(_work, inline, _origin, _mapping()))
      val delegated = _session()
      Then("selection retains the exact issued contract and inline current profile without mandatory child or alternate")
      local.requirement shouldBe inline
      local.primary shouldBe _origin.profile
      local.alternate shouldBe None
      local.nextSelection.get.attempt shouldBe 1
      delegated.requirement shouldBe _requirement()
      delegated.primary shouldBe _primary
      delegated.alternate shouldBe Some(_alternate)
      _mapping().resolveC(delegated.requirement, _origin) shouldBe a[Consequence.Failure[?]]
    }

    "E2 consume one actual unavailable or declined attempt and exactly one alternate" in {
      for ((status, reason) <- Vector(H.Status.Unavailable -> H.Reason.ProviderUnavailable, H.Status.Declined -> H.Reason.CapabilityDeclined)) {
        Given("R10,R11: a delegated issued session and an actual first " + status.value)
        val initial = _session()
        When("the host records that outcome then observes completed work from the configured alternate")
        val fallback = _take(_record(initial, status, reason))
        val completed = _take(_complete(fallback))
        Then("both attempts retain the original work reference and requirement with a final matching independent observation")
        fallback.nextSelection.get shouldBe H.Selection(2, _alternate)
        completed.workReference shouldBe initial.workReference
        completed.requirement shouldBe initial.requirement
        completed.attempts.map(_.outcome.status) shouldBe Vector(status, H.Status.Completed)
        completed.nextSelection shouldBe None
        _take(completed.completionObservationC).profileIdentity shouldBe _alternate.identity
        completed.recordC(H.Selection(2, _alternate), H.Outcome(status, reason, "No retry")) shouldBe a[Consequence.Failure[?]]
      }
    }

    "E3 stop blocked failed uncertain exhausted and inline work without completion evidence" in {
      for ((status, reason) <- Vector(H.Status.Blocked -> H.Reason.AuthorityBlocked, H.Status.Blocked -> H.Reason.ResourceBlocked,
          H.Status.Failed -> H.Reason.SemanticFailure, H.Status.Failed -> H.Reason.UncertainExecution)) {
        Given("R10: an actual terminal operational outcome " + reason)
        When("the host records it on a delegated session")
        val stopped = _take(_record(_session(), status, reason))
        Then("it cannot select another executor or become semantic completion")
        stopped.nextSelection shouldBe None
        stopped.completionObservationC shouldBe a[Consequence.Failure[?]]
      }
      for (initial <- Vector(_session(alternate = false), _session(review = false, inline = true),
          _take(_record(_session(), H.Status.Unavailable, H.Reason.ProviderUnavailable)))) {
        Given("R10: no alternate, INLINE, or the final configured alternate")
        When("the actual selected attempt is unavailable or declines")
        val unavailable = _take(_record(initial, H.Status.Unavailable, H.Reason.ProviderUnavailable))
        val declined = _take(_record(initial, H.Status.Declined, H.Reason.CapabilityDeclined))
        Then("exhaustion stops without successful execution")
        Vector(unavailable, declined).foreach { stopped =>
          stopped.nextSelection shouldBe None
          stopped.completionObservationC shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "E4 require concrete profile and independent actual executor relations" in {
      Given("R9,R11: REQUIRED review, plus missing and contradictory issued dispositions")
      val initial = _session()
      val invalid = Vector(_requirement().copy(disposition = None), _requirement().copy(disposition = Some(
        ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Required, "origin"))),
        _requirement(review = false, inline = true).copy(disposition = Some(
          ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Required, "origin"))),
        _requirement().copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required, "other"))))
      When("the host supplies same-context or wrong-profile observations and one genuine distinct-context descriptor")
      val same = _record(initial, H.Status.Completed, H.Reason.Completed, Some(_origin))
      val wrong = _record(initial, H.Status.Completed, H.Reason.Completed, Some(WorkflowExecutorDescriptor("independent", _alternate)))
      val absent = _record(initial, H.Status.Completed, H.Reason.Completed)
      val unavailableexecutor = _record(initial, H.Status.Unavailable, H.Reason.ProviderUnavailable,
        Some(WorkflowExecutorDescriptor("independent", _primary)))
      val completed = _take(_complete(initial))
      Then("only matching concrete work in a distinct context completes and invalid issued inputs reject")
      same shouldBe a[Consequence.Failure[?]]
      wrong shouldBe a[Consequence.Failure[?]]
      absent shouldBe a[Consequence.Failure[?]]
      unavailableexecutor shouldBe a[Consequence.Failure[?]]
      invalid.foreach(r => H.prepareIssuedC(_work, r, _origin, _mapping()) shouldBe a[Consequence.Failure[?]])
      _take(completed.completionObservationC).executorContextIdentity shouldBe "independent"
    }

    "E5 serialize known and unknown resources and reject malformed outcome paths" in {
      Given("R11,R12: actual host resource observations and explicit unknown values")
      val known = H.Resources(Some(42), Some(10), Some(20), Some(BigDecimal("0.02")), Some("USD"), Some("fixture-machine"))
      val completed = _take(_complete(_session(), known))
      val pending = _session()
      When("the host encodes and restores its session and receives invalid numeric or status/reason data")
      val restored = _take(H.decodeC(_take(H.encodeC(completed))))
      val unknowncompleted = _take(_complete(pending))
      val unknown = _take(H.decodeC(_take(H.encodeC(unknowncompleted))))
      val restoredpending = _take(H.decodeC(_take(H.encodeC(pending))))
      val encoded = io.circe.parser.parse(_take(H.encodeC(completed))).toOption.get
      val first = encoded.hcursor.downField("attempts").focus.get.asArray.get.head
      def _decode_attempt_(fields: Json): Consequence[H.Session] = H.decodeC(encoded.mapObject(_.add("attempts", Json.arr(fields))).noSpaces)
      val omitted = _take(_decode_attempt_(first.mapObject(_.add("resources", Json.obj()))))
      Then("observed data survives, missing metrics stay unknown, and illegal statuses/reasons or extra attempts reject")
      restored.attempts.last.outcome.resources shouldBe known
      unknown.attempts.last.outcome.resources shouldBe H.Resources()
      omitted.attempts.last.outcome.resources shouldBe H.Resources()
      restoredpending.nextSelection shouldBe pending.nextSelection
      Vector(H.Resources(elapsedMillis = Some(-1)), H.Resources(inputTokens = Some(-1)), H.Resources(outputTokens = Some(-1)),
        H.Resources(monetaryCost = Some(BigDecimal(-1)), currency = Some("USD")), H.Resources(currency = Some("USD")))
        .foreach(r => _complete(pending, r) shouldBe a[Consequence.Failure[?]])
      _decode_attempt_(first.mapObject(_.add("status", Json.fromString("UNCERTAIN")))) shouldBe a[Consequence.Failure[?]]
      _decode_attempt_(first.mapObject(_.add("reason", Json.fromString("ProviderUnavailable")))) shouldBe a[Consequence.Failure[?]]
      H.decodeC(encoded.mapObject(_.add("attempts", Json.arr(first, first))).noSpaces) shouldBe a[Consequence.Failure[?]]
    }

    "E6 use changed canonical configuration only for new issued work" in {
      Given("R9,R12: a retained delegated selection and a later canonical mapping with a different model")
      val old = _session()
      val laterprofile = _primary.copy(identity = "new-primary", model = "new-model")
      val later = _mapping(revision = "2", primary = laterprofile)
      When("the old session is restored and a new issued work is prepared using the later snapshot")
      val restored = _take(H.decodeC(_take(H.encodeC(old))))
      val fresh = _take(H.prepareIssuedC(_work.copy(continuationId = ContinuationIdentity("fresh")), _requirement(), _origin, later))
      Then("the old selection and provenance remain saved while only new work uses the new profile")
      restored.primary shouldBe _primary
      restored.mappingRevision shouldBe "1"
      fresh.primary shouldBe laterprofile
      fresh.mappingRevision shouldBe "2"
      _take(_complete(restored)).completionObservationC.toOption.get.profileIdentity shouldBe _primary.identity
    }

    "E7 consume only declared normal wire aliases without weakening strict issued requirements" in {
      Given("R13: canonical issued requirements and the standard formatter's three consumed snake names")
      val declared = WorkflowExecutionJsonV1.requirement(_requirement())
      val wire = declared.mapObject(fields => fields.remove("reviewRequired").remove("reasoningClass")
        .add("review_required", fields("reviewRequired").get).add("reasoning_class", fields("reasoningClass").get)
        .add("disposition", fields("disposition").get.mapObject(nested => nested.remove("originContextIdentity")
          .add("origin_context_identity", nested("originContextIdentity").get))))
      val equal = wire.mapObject(_.add("reviewRequired", Json.True).add("reasoningClass", Json.fromString("ReviewStandard"))
        .add("disposition", wire.hcursor.downField("disposition").focus.get.mapObject(
          _.add("originContextIdentity", Json.fromString("origin")))))
      When("the host prepares declared, wire and matching alias Requests and decodes optional or malformed input")
      val sessions = Vector(declared, wire, equal).map(value => _take(H.prepareIssuedJsonC(_work, value, _origin, _mapping())))
      val absent = _take(H.decodeIssuedRequirementC(declared.mapObject(_.remove("disposition").remove("reasoningClass"))))
      val invalid = Vector(wire.mapObject(_.add("reviewRequired", Json.False)),
        wire.mapObject(_.add("reasoningClass", Json.fromString("OtherClass"))),
        equal.mapObject(_.add("disposition", equal.hcursor.downField("disposition").focus.get.mapObject(
          _.add("origin_context_identity", Json.fromString("foreign"))))),
        wire.mapObject(_.add("review_required", Json.fromString("true"))),
        declared.mapObject(_.remove("reviewRequired")), declared.mapObject(_.add("unknown", Json.True)))
      Then("all normal spellings select the same semantic requirement while absence stays absent and contradictions/types/unknowns reject")
      sessions.foreach { session =>
        session.requirement shouldBe _requirement()
        session.nextSelection shouldBe Some(H.Selection(1, _primary))
      }
      absent.disposition shouldBe None
      absent.reasoningClass shouldBe None
      invalid.foreach(value => H.decodeIssuedRequirementC(value) shouldBe a[Consequence.Failure[?]])
    }

    "E8 consume public observation codecs through the finite alternate and restored completion" in {
      for ((status, reason) <- Vector(H.Status.Unavailable -> H.Reason.ProviderUnavailable, H.Status.Declined -> H.Reason.CapabilityDeclined)) {
        Given("R10,R12,R13: a prepared issued Session and an actual flat selected-attempt observation " + status.value)
        val initial = _session()
        val firstselection = io.circe.parser.parse(_take(H.encodeSelectionC(initial.nextSelection.get))).toOption.get
        val observation = firstselection.mapObject(_.add("status", Json.fromString(status.value))
          .add("reason", Json.fromString(reason.toString)).add("detail", Json.fromString("Observed fixture noncompletion"))
          .add("resources", Json.obj()))
        When("the host decodes the supplied selection, records noncompletion, then restores and completes the alternate")
        val first = _take(H.decodeAttemptC(observation))
        val alternate = _take(initial.recordC(first.selection, first.outcome))
        val saved = _take(H.decodeC(_take(H.encodeC(alternate))))
        val selected = io.circe.parser.parse(_take(H.encodeSelectionC(saved.nextSelection.get))).toOption.get
        val completedjson = selected.mapObject(_.add("status", Json.fromString("COMPLETED"))
          .add("reason", Json.fromString("Completed")).add("detail", Json.fromString("Observed alternate result"))
          .add("executor", Json.obj("contextIdentity" -> Json.fromString("independent"), "profile" -> selected.hcursor.downField("profile").focus.get))
          .add("resources", Json.obj("elapsedMillis" -> Json.fromLong(9))))
        val actual = _take(H.decodeAttemptC(completedjson))
        val completed = _take(H.decodeC(_take(H.encodeC(_take(saved.recordC(actual.selection, actual.outcome))))))
        Then("the original work and both outcomes survive and recordC rejects stale ordinals/profiles and invalid metrics")
        completed.workReference shouldBe _work
        completed.attempts.map(_.outcome.status) shouldBe Vector(status, H.Status.Completed)
        _take(completed.completionObservationC).profileIdentity shouldBe _alternate.identity
        saved.recordC(first.selection, first.outcome) shouldBe a[Consequence.Failure[?]]
        saved.recordC(actual.selection.copy(profile = _primary), actual.outcome) shouldBe a[Consequence.Failure[?]]
        val invalid = _take(H.decodeAttemptC(completedjson.mapObject(_.add("resources", Json.obj("inputTokens" -> Json.fromLong(-1))))))
        saved.recordC(invalid.selection, invalid.outcome) shouldBe a[Consequence.Failure[?]]
        H.encodeSelectionC(null) shouldBe a[Consequence.Failure[?]]
        H.encodeSelectionC(H.Selection(3, _primary)) shouldBe a[Consequence.Failure[?]]
        H.encodeSelectionC(H.Selection(1, _primary.copy(model = ""))) shouldBe a[Consequence.Failure[?]]
        H.decodeAttemptC(null) shouldBe a[Consequence.Failure[?]]
        H.decodeAttemptC(Json.arr()) shouldBe a[Consequence.Failure[?]]
        H.decodeAttemptC(completedjson.mapObject(_.add("resources", Json.obj("inputTokens" -> Json.fromString("unknown"))))) shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
