package org.goldenport.cncf.workflow

import java.time.{Clock, Instant, ZoneOffset}
import java.nio.file.Files
import io.circe.Json
import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.statemachine.{CandidateAdmissionGuard, StateMachineRequiredOperationAction, TransitionCandidate, TransitionSelector}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkAuthorization, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/** Reference seam between optional CAM admission and unchanged Phase 77 mechanics.
  * @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _target = CandidateRef("step-1", "revision-1")
  private val _requirement = AdmissionRequirement(
    "full-step-review", RequirementKind.Semantic, "ReviewEvidence", TypedValue("ReviewScope", 3))
  private val _initial_evidence = AdmissionEvidence(
    "early-review", _requirement.identity, _target, TypedValue("ReviewEvidence", "narrow"),
    TypedValue("ReviewScope", 1), "early-reviewer")
  private val _submission = AdmissionSubmission("request-step-close-1",
    CandidateSnapshot(_target, TypedValue("StepClosureCandidate", "draft")), Vector(_initial_evidence))
  private val _policy = new EvidencePolicy[String, Int] {
    def isFresh(evidence: AdmissionEvidence[String, Int], target: CandidateRef): Boolean =
      evidence.provenance != "stale"
    def covers(evidence: AdmissionEvidence[String, Int], requirement: AdmissionRequirement[Int]): Boolean =
      evidence.scope.value >= requirement.scope.value
  }
  private val _required = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity("step-review-capability"),
    "review-step",
    StateMachineOperationIdentity("StepService", "reviewStep"),
    Some(StateMachineInputTypeReference("StepReviewInput")),
    Some(StateMachineResultTypeReference("StepReviewResult")),
    StateMachineRequiredOperationMetadata(
      ContextContract("review-context", Vector.empty, Vector.empty),
      CompletionContract("review-completion", Vector.empty),
      EvidenceContract("review-evidence", Vector.empty),
      Vector.empty
    )
  )

  "Candidate Admission Step closure seam" should {
    "resume an admitted Required SPI gap and admit the same Step candidate with later typed evidence" in {
      Given("a Step closure candidate with narrower proactive review evidence")
      When("the broader closure requirement is evaluated and routed against an active admitted instance")
      val first = evaluateC(_submission, Vector(_requirement), _policy).toOption.get
      val routed = resolveForInstanceC(first,
        Vector(Binding(_requirement.identity, Target.SemanticAction(_required))), _active_step_instance())
        .toOption.get.head
      Then("the insufficient coverage remains a semantic gap routed to the declared Action")
      first.gaps.map(_.defects) shouldBe Vector(Vector(EvidenceDefect.InsufficientCoverage))
      val actionrequired = routed.target match {
        case Target.SemanticAction(operation) => operation
        case other => fail(s"unexpected gap route: $other")
      }

      val run = StateMachineRunIdentity("run-1")
      val context = ContextBundle("broader review", Vector.empty, Vector.empty,
        ContextSnapshot("step-close-v1", workspaceRevision = Some(_target.revision)))
      val continuation = Continuation(run, ContinuationIdentity("review-1"),
        StateMachineRevision("state-1"), actionrequired, context)
      val action = new StateMachineRequiredOperationAction[Unit, Unit]((_, _) =>
        ProviderExecutionRequest(run, actionrequired,
          Some(StateMachineOperationInput(StateMachineInputTypeReference("StepReviewInput"),
            ContextReference("step-review-input", _target.revision))), context))
      var actioncalls = 0
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("provider.step-review")
        def program(request: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          request.requiredOperation shouldBe actionrequired
          actioncalls += 1
          for {
            _ <- ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.Authorize(
              UnitOfWorkAuthorization("step-review", accessKind = "execute",
                accessMode = EntityAccessMode.System))))
          } yield ActionExecution.Suspended(continuation)
        }
      }
      val resolver = StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(actionrequired.identity, provider.identity)), Vector(provider))
        .toOption.get
      val component = new Component() {}
      component.withStateMachineProviderResolver(resolver)
      val root = ExecutionContext.create()
      val executioncontext = root.withScope(Component.Context("step-close-reference", root.scope,
        component, ComponentOrigin.Embed))
      val origin = new UnitOfWork(executioncontext)
      When("the ordinary UnitOfWork interpreter invokes the bound semantic Provider")
      val execution = new UnitOfWorkInterpreter(origin).run(action.program((), ())).toOption.get
      Then("the existing Action suspends through an unchanged Phase 77 Continuation")
      execution shouldBe ActionExecution.Suspended(continuation)

      When("the same UnitOfWork commits suspension and a recreated runtime resumes once")
      val persistence = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(persistence)
      val staged = runtime.stageSuspensionC(origin, continuation)
      val committed = origin.commit()
      val restarted = new PersistentContinuationRuntime(persistence)
      val claim = restarted.claimC(continuation.continuationId).toOption.get
      val result = StateMachineOperationResult(
        StateMachineResultTypeReference("StepReviewResult"), ContextReference("review-result", "result-1"))
      val resumed = restarted.resumeC(claim, result, () => _uow("resume"))
      val duplicate = restarted.resumeC(claim, result, () => _uow("duplicate"))
      Then("the staged suspension commits and only the first resume succeeds")
      staged shouldBe Consequence.unit
      committed shouldBe Consequence.unit
      resumed.isSuccess shouldBe true
      duplicate.isFaillure shouldBe true

      val laterevidence = AdmissionEvidence(
        "full-step-review-evidence", _requirement.identity, _target,
        TypedValue("ReviewEvidence", "accepted"), TypedValue("ReviewScope", 3),
        s"result:${result.contextReference.identity}@${result.contextReference.revision}")
      When("later typed evidence is persisted, recovered, and applied to the original submission")
      val recoveredevidence = _recover_evidence(laterevidence)
      val admitted = evaluateC(_submission, Vector(_requirement), _policy, recoveredevidence).toOption.get
      Then("the original Step candidate is admitted without resubmission or a second Action call")
      admitted.result shouldBe AdmissionResult.Admitted(_target, Vector(laterevidence.identity))
      _submission.evidence shouldBe Vector(_initial_evidence)
      actioncalls shouldBe 1

      val guard = new CandidateAdmissionGuard[Vector[AdmissionEvidence[String, Int]], Unit, String, String, Int](
        (_, _) => Consequence.success(_submission),
        (_, _) => Consequence.success(Vector(_requirement)),
        (available, _) => Consequence.success(available),
        _policy
      )
      val transitions = Vector(TransitionCandidate("application-commit", 0, 0))
      When("the existing StateMachine selector evaluates the admission guard")
      val withheld = TransitionSelector.selectCanonical(transitions)(_ => guard.eval(Vector.empty, ()))
      val selected = TransitionSelector.selectCanonical(transitions)(_ => guard.eval(recoveredevidence, ()))
      Then("the guard cannot choose the transition until recovered evidence is accepted")
      withheld shouldBe Consequence.success(None)
      selected shouldBe Consequence.success(Some("application-commit"))
      actioncalls shouldBe 1

      When("the same later evidence is marked stale")
      val stale = evaluateC(_submission, Vector(_requirement), _policy,
        Vector(laterevidence.copy(provenance = "stale"))).toOption.get
      Then("the evaluator retains a stale-evidence gap")
      stale.gaps.head.defects should contain (EvidenceDefect.Stale)
    }
  }

  /** Synthetic Step naming over an admitted Cozy Candidate-Admission ABI shape;
    * this is not a claim that Cozy generated a real sm-workflow model.
    */
  private def _active_step_instance(): WorkflowInstancePersistence.InstanceRecord = {
    import WorkflowInstancePersistence.{CausationReference, CommittedEntityTransitionReference,
      CorrelationReference, DerivedCompositeOccurrenceReference, HistoryEntry, HistorySequence,
      InstanceIdentity, InstanceRecord, InstanceRevision, Lifecycle, ProgressionReference, initialRevision}
    def _resource_(path: String): String = {
      val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
      try source.mkString finally source.close()
    }
    def _step_names_(text: String): String = Vector(
      "OrderProgress" -> "StepClosure",
      "workflow-v1" -> "step-close-v1",
      "judge-payment" -> "review-step",
      "admit-payment" -> "admit-step",
      "capture-payment-capability" -> "step-review-capability",
      "OrderService" -> "StepService",
      "capturePayment" -> "reviewStep",
      "PaymentCommand" -> "StepReviewInput",
      "PaymentResult" -> "StepReviewResult"
    ).foldLeft(text) { case (current, (from, to)) => current.replace(from, to) }
    val sidecar = CandidateAdmissionProducerAbi.parseC(
      _step_names_(_resource_("/workflow/candidate-admission-producer-abi.json"))).toOption.get
    val definition = CandidateWorkflowAbi.parseC(
      _step_names_(_resource_("/workflow/candidate-admission-workflow-abi.json").stripSuffix("\n")),
      sidecar, "modeler/step-closure-reference.cml").toOption.get
    val binding = WorkflowInstancePersistence.bindCandidateDefinitionC(definition).toOption.get
    val initial = InstanceRecord(InstanceIdentity("step-close-instance"), binding, initialRevision,
      Lifecycle.NotStarted, None, CorrelationReference("step-correlation"),
      CausationReference("step-causation"),
      Some(DerivedCompositeOccurrenceReference("step-composite")),
      Some(CommittedEntityTransitionReference("step-predecessor")), None, Vector.empty)
    initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
      Lifecycle.Active, Some(ProgressionReference("step-progress")),
      CorrelationReference("step-correlation"), CausationReference("step-causation"),
      Some(DerivedCompositeOccurrenceReference("step-composite")),
      Some(CommittedEntityTransitionReference("step-predecessor")), None)).toOption.get
  }

  private def _recover_evidence(
    evidence: AdmissionEvidence[String, Int]
  ): Vector[AdmissionEvidence[String, Int]] = {
    val evidencecodec = new PayloadCodec[String] {
      val typeIdentity = "ReviewEvidence"
      def encode(value: String): Json = Json.fromString(value)
      def decode(value: Json): Either[String, String] = value.asString.toRight("expected review evidence")
    }
    val scopecodec = new PayloadCodec[Int] {
      val typeIdentity = "ReviewScope"
      def encode(value: Int): Json = Json.fromInt(value)
      def decode(value: Json): Either[String, Int] = value.asNumber.flatMap(_.toInt).toRight("expected scope")
    }
    val directory = Files.createTempDirectory("cam-resume-evidence-")
    try {
      new CandidateAdmissionEvidencePersistence.LocalJson(directory, evidencecodec, scopecodec)
        .putIfAbsentC(_submission.identity, evidence) shouldBe Consequence.unit
      new CandidateAdmissionEvidencePersistence.LocalJson(directory, evidencecodec, scopecodec)
        .loadC(_submission.identity).toOption.get
    } finally {
      val entries = Files.list(directory)
      try entries.forEach(path => Files.deleteIfExists(path)) finally entries.close()
      Files.deleteIfExists(directory)
    }
  }

  private def _uow(name: String): UnitOfWork = {
    val clock = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC)
    given ExecutionContext = ExecutionContext.withIdGenerationContext(
      ExecutionContext.create(clock),
      IdGenerationContext.deterministic(IdGenerationContext.IdNamespace("test", "cam"), clock, name)
    )
    new UnitOfWork(summon[ExecutionContext], EventEngine.noop(DataStore.noop()))
  }
}
