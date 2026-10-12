package org.goldenport.cncf.workflow

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.statemachine.{CandidateAdmissionRouter, CmlStateMachineIdentity, CmlStateMachineStateIdentity, CmlStateMachineStatePath, CmlStateMachineTransitionTarget, ExecutionPlan, ExecutionPlanExecutor, ResolvedAction, StateMachineRequiredOperationAction}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkOp}
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 21, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowInstancePersistenceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import GeneratedWorkflowAbi.{
    Diagnostic as _,
    DiagnosticCode as _,
    ProducerAbiIdentity as _,
    SourceLocation as GeneratedSourceLocation,
    WorkflowAbiIdentity as _,
    *
  }
  import WorkflowInstancePersistence.*

  private val _e1 = afterWord(
    "in spec:workflow-instance-persistence, example:E1, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e2 = afterWord(
    "in spec:workflow-instance-persistence, example:E2, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e3 = afterWord(
    "in spec:workflow-instance-persistence, example:E3, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e4 = afterWord(
    "in spec:workflow-instance-persistence, example:E4, rules:CWF-77-03, phase:77, slice:77-S1B"
  )
  private val _e5 = afterWord(
    "in spec:workflow-instance-persistence, example:E5, rules:CWF-77-03, phase:77, slice:77-S1B"
  )

  private val _e6 = afterWord("in spec:workflow-instance-persistence, example:E6, rules:CWF-77-03, phase:77, slice:77-S1B")
  private val _e7 = afterWord("in spec:workflow-instance-atomic-transition-v1, example:E7, rules:CWF-77-06C, phase:77.1, slice:77.1-06C1")

  "Workflow instance persistence SPI" should {
    "E1 bind only admitted generated ABI provenance into the independent SPI family" must _e1 {
      "retain the definition binding without adopting legacy workflow runtime behavior" in {
        Given("a complete generated Workflow ABI definition and an explicit same-store configuration")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val record = _initial_record(binding)
        val provider: WorkflowInstancePersistence = new ProbePersistence

        When("the independent record is presented through the SPI create operation")
        val persisted = _take(provider.create(_same_store_configuration, record))

        Then("the accepted ABI identities and source correlation remain in the persistence binding")
        persisted.definition shouldBe binding
        binding.workflowIdentity shouldBe WorkflowDefinitionIdentity("WorkflowProducer")
        binding.workflowRevision shouldBe WorkflowDefinitionRevision("workflow-producer-v1")
        binding.producerAbiIdentity shouldBe ProducerAbiIdentity("workflow-producer-v1")
        binding.workflowAbiIdentity shouldBe WorkflowAbiIdentity("cozy.cml.statemachine-workflow-abi.v1")
        binding.bootstrapAbiIdentity shouldBe BootstrapAbiIdentity("cozy.cml.statemachine-workflow-bootstrap.v1")
        binding.sourceCorrelation.root.resource should include("skill-driven-workflow-producer.cml")
      }
    }

    "E2 preserve revisioned append-only history for independent instance records" must _e2 {
      "derive only the contiguous next record from its expected revision and sequence" in {
        Given("admitted bindings and a range of explicit active progression references")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty), Gen.choose(2, 16)) { (suffix, count) =>
          val initial = _initial_record(binding, suffix)
          val appended = (1 to count).foldLeft(Consequence.success(initial): Consequence[InstanceRecord]) { (current, ordinal) =>
            current.flatMap { record =>
              record.appendC(record.revision, _active_entry(suffix).copy(
                sequence = HistorySequence(ordinal.toLong), revision = InstanceRevision(ordinal.toLong)))
            }
          }
          appended.toOption.exists { record =>
            record.revision == InstanceRevision(count.toLong) &&
              record.history.map(_.sequence) == (1 to count).map(value => HistorySequence(value.toLong)).toVector &&
              record.lifecycle == Lifecycle.Active &&
              record.currentProgression == Some(ProgressionReference(s"progress-$suffix"))
          }
        }

        When("the pure append admission evaluates the generated records")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)
        val appended = _take(_initial_record(binding).appendC(initialRevision, _active_entry("one")))

        Then("the expected revision is consumed once and the immutable history remains the source of current facts")
        checked.passed shouldBe true
        appended.history should have size 1
        appended.history.head.revision shouldBe appended.revision
        appended.history.head.currentProgression shouldBe appended.currentProgression
      }
    }

    "E3 reject stale and incomplete persistence facts with stable diagnostics" must _e3 {
      "fail closed for incorrect expected revision, illegal lifecycle/progression, and incomplete suspension" in {
        Given("a valid initial record plus stale, illegal, and incomplete persistence inputs")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val initial = _initial_record(binding)
        When("public append and record validation consume stale, illegal and incomplete caller values")
        val stale = initial.appendC(InstanceRevision(1L), _active_entry("stale"))
        val illegal = initial.copy(
          lifecycle = Lifecycle.Active,
          currentProgression = None
        ).validateC
        val incomplete = initial.copy(
          lifecycle = Lifecycle.Active,
          currentProgression = Some(ProgressionReference("review")),
          suspension = Some(SuspensionBoundary(
            ContinuationIdentity("continuation-1"),
            initialRevision,
            ContextSnapshotReference("context-1"),
            CompletionReference("completion-1"),
            EvidenceReference(""),
            CorrelationReference("resume-1")
          ))
        ).validateC

        val diagnostics = Vector(stale, illegal, incomplete).map(_diagnostic)

        Then("each rejected fact has a deterministic closed diagnostic code")
        diagnostics.map(_.code) shouldBe Vector(
          DiagnosticCode.IncorrectExpectedRevision,
          DiagnosticCode.IllegalLifecycleProgression,
          DiagnosticCode.IncompleteSuspensionBoundary
        )
      }

      "reject malformed public current, adjacent null fields, terminal replay and revision overflow" in {
        Given("a valid initial record, its active successor and caller-owned boundary variants")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val initial = _initial_record(binding)
        val active = _take(initial.appendC(initialRevision, _active_entry("boundary")))
        val entry = _active_entry("boundary").copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L))
        val terminalentry = entry.copy(lifecycle = Lifecycle.Completed, currentProgression = None)
        val terminal = _take(active.appendC(active.revision, terminalentry))
        val invalidentries = Vector(null, entry.copy(sequence = null), entry.copy(revision = null),
          entry.copy(currentProgression = null), entry.copy(derivedCompositeOccurrence = null),
          entry.copy(committedPredecessor = null), entry.copy(suspension = null), entry.copy(causation = null))
        When("public append consumes malformed current history and each malformed adjacent entry")
        val results = Vector(
          active.copy(history = Vector(null)).appendC(active.revision, entry),
          active.appendC(null, entry), active.appendC(initialRevision, entry),
          active.copy(revision = InstanceRevision(Long.MaxValue)).appendC(InstanceRevision(Long.MaxValue), entry),
          terminal.appendC(terminal.revision, entry.copy(sequence = HistorySequence(3L), revision = InstanceRevision(3L)))) ++
          invalidentries.map(value => active.appendC(active.revision, value))
        Then("every invalid public fact returns a typed persistence failure without mutating the retained history")
        results.foreach(result => _diagnostic(result).code should not be null)
        results.forall(_.toOption.isEmpty) shouldBe true
        active.history shouldBe Vector(_active_entry("boundary"))
        terminal.history shouldBe active.history :+ terminalentry
      }
    }

    "E4 require an explicit valid delivery configuration for every consumer" must _e4 {
      "reject mismatched same-store identity and incomplete cross-store committed-transition proof" in {
        Given("a named workflow store with all non-delivery policies supplied")
        val mismatch = _same_store_configuration.copy(
          delivery = DeliveryConfiguration.SameStore(WorkflowStoreIdentity("entity-store"))
        )
        val incomplete = _same_store_configuration.copy(
          delivery = DeliveryConfiguration.CrossStoreCommittedTransition(
            WorkflowStoreIdentity("entity-store"),
            CommittedEntityTransitionReference("committed-transition-1"),
            IdempotencyKey(""),
            RecoverabilityReference("recovery-1")
          )
        )

        When("the explicit delivery alternatives are validated")
        val diagnostics = Vector(mismatch.validateC, incomplete.validateC).map(_diagnostic)

        Then("identity equality and cross-store recovery, idempotency, and committed-transition proof remain mandatory")
        diagnostics.map(_.code) shouldBe Vector(
          DiagnosticCode.MismatchedSameStoreIdentity,
          DiagnosticCode.IncompleteCrossStoreDeliveryProof
        )
      }
    }

    "E5 remain a provider-neutral three-operation SPI" must _e5 {
      "expose no default store or workflow runtime behavior" in {
        Given("the declared persistence service boundary")
        val methods = classOf[WorkflowInstancePersistence].getDeclaredMethods
          .filterNot(method => java.lang.reflect.Modifier.isStatic(method.getModifiers))
          .map(_.getName)
          .toSet

        When("its public SPI shape is inspected")
        val isinterface = classOf[WorkflowInstancePersistence].isInterface

        Then("only create, load, and append are available to a provider implementation")
        isinterface shouldBe true
        methods shouldBe Set("create", "load", "append")
      }
    }

    "E6 retain admitted executable definitions" must _e6 {
      "translate an unsupported execution revision at admission, without reflected tamper proofs" in {
        Given("an admitted generated definition and an unsupported runtime revision")
        val definition = _definition()
        val incompatible = definition.copy(
          workflow = definition.workflow.copy(revision = WorkflowRevision("unsupported-revision")))
        When("the runtime definition is admitted")
        val admission = _diagnostic(WorkflowInstancePersistence.bindDefinitionC(incompatible))
        Then("actual incompatibility preserves its generated diagnostic")
        admission.code shouldBe DiagnosticCode.GeneratedAbiAdmissionRejected
        admission.data("generated-code") shouldBe GeneratedWorkflowAbi.DiagnosticCode.UnsupportedWorkflowRevision.value
      }
    }

    "E7 admit only a versioned, correlated suspension intent" must _e7 {
      "derive the next WorkflowInstance record and reject foreign, claimed, or stale Continuations" in {
        Given("an admitted current instance, a suspension history entry, and an available Continuation")
        val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
        val current = _initial_record(binding)
        val boundary = SuspensionBoundary(
          ContinuationIdentity("continuation-one"),
          InstanceRevision(1L),
          ContextSnapshotReference("snapshot-one"),
          CompletionReference("completion-one"),
          EvidenceReference("evidence-one"),
          CorrelationReference("resume-one")
        )
        val entry = _active_entry("one").copy(suspension = Some(boundary))
        val available = _continuation_record("continuation-one")
        val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
          WorkflowInstanceAtomicTransitionV1.schemaVersion,
          _same_store_configuration,
          current,
          current.revision,
          entry,
          available
        )

        When("the pure versioned boundary admits the matched intent")
        val admitted = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)

        Then("only immutable next-state facts are derived")
        admitted.toOption.map(_.next.revision) shouldBe Some(InstanceRevision(1L))
        admitted.toOption.flatMap(_.next.suspension) shouldBe Some(boundary)
        admitted.toOption.map(_.continuation) shouldBe Some(available)

        When("the schema, suspension, Continuation identity, claim state, or expected revision is incompatible")
        val unsupported = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(
          intent.copy(schemaVersion = "unsupported")
        )
        val nosuspension = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(
          intent.copy(entry = _active_entry("one"))
        )
        val foreign = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(
          intent.copy(continuation = _continuation_record("foreign"))
        )
        val claimed = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(
          intent.copy(continuation = available.copy(
            status = ContinuationRuntimePersistence.Status.Claimed,
            claimId = Some("claim-one")
          ))
        )
        val stale = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(
          intent.copy(expectedRevision = InstanceRevision(1L))
        )

        Then("each invalid intent fails before it can be staged")
        Vector(unsupported, nosuspension, foreign, claimed, stale).forall(_.isFaillure) shouldBe true

        When("the current split-commit UnitOfWork is asked to stage the same suspension")
        val ordinaryunitofwork = new org.goldenport.cncf.unitofwork.UnitOfWork(
          org.goldenport.cncf.context.ExecutionContext.create()
        )
        val staged = ordinaryunitofwork.stageAtomicSuspensionC(intent)

        Then("it fails closed before staging any event or publishing a Continuation")
        staged.isFaillure shouldBe true
        ordinaryunitofwork.pendingEvents shouldBe empty
      }
    }

    "guard a separate-turn resume with the loaded WorkflowInstance suspension" in {
      import WorkflowProtocolV1.*

      Given("a persisted suspension, a recoverable claim, and separate WorkOrder persistence ports")
      val binding = _take(WorkflowInstancePersistence.bindDefinitionC(_definition()))
      val initial = _initial_record(binding)
      val boundary = SuspensionBoundary(
        ContinuationIdentity("continuation-one"), InstanceRevision(1L),
        ContextSnapshotReference("snapshot-one"),
        CompletionReference("review.completion"), EvidenceReference("review.evidence"),
        CorrelationReference("resume-one")
      )
      val suspended = _take(initial.appendC(initialRevision, _active_entry("one").copy(suspension = Some(boundary))))
      val unsuspended = _take(initial.appendC(initialRevision, _active_entry("one")))
      var stored: Option[InstanceRecord] = Some(suspended)
      val instances = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.notImplemented("resume guard fixture is read-only")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          Consequence.success(stored.filter(_.identity == identity))
        def append(
          configuration: Configuration,
          identity: InstanceIdentity,
          expectedRevision: InstanceRevision,
          entry: HistoryEntry
        ): Consequence[InstanceRecord] =
          Consequence.notImplemented("resume guard fixture is read-only")
      }
      val continuation = _continuation_record("continuation-one").continuation
      val continuationstore = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(continuationstore)
      val startunitofwork = new org.goldenport.cncf.unitofwork.UnitOfWork(
        org.goldenport.cncf.context.ExecutionContext.create()
      )
      runtime.stageSuspensionC(startunitofwork, continuation) shouldBe Consequence.unit
      startunitofwork.commit() shouldBe Consequence.unit
      val claim = _take(new PersistentContinuationRuntime(continuationstore).claimC(continuation.continuationId))
      val component = org.goldenport.cncf.component.ComponentId("org.goldenport.cncf.test.WorkflowInstancePersistenceSpec")
      val handle = _take(WorkflowHandle.fromRecordC(component, suspended))
      val recovered = new PersistentContinuationRuntime(continuationstore)
      val issuedstore = new IssuedWorkOrderPersistence.InMemory[String]
      val completionoperation = StateMachineOperationIdentity("WorkflowService", "submitReview")
      val requirement = ExecutionRequirement(
        Vector(CapabilityRequirement("review")), RiskLevel("standard"), ReasoningLevel.Deep, true
      )
      val presentation = MinimalPresentation("Review change", "Waiting for a reviewer")
      val failingstore = new IssuedWorkOrderPersistence[String] {
        def putIfAbsentC(issued: WorkflowInteraction[String, Nothing]): Consequence[WorkflowInteraction[String, Nothing]] =
          Consequence.stateConflict("planned WorkOrder write failure")
        def loadC(identity: org.goldenport.cncf.workflow.ContinuationIdentity): Consequence[Option[WorkflowInteraction[String, Nothing]]] =
          Consequence.success(None)
      }
      When("WorkOrder persistence rejects the first issuance")
      issueRecoveredWorkOrderC(handle, continuation.continuationId, None, completionoperation,
        requirement, presentation, recovered, failingstore) shouldBe a[Consequence.Failure[_]]
      Then("the claim remains recoverable")
      recovered.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)
      Given("Spec: docs/spec/workflow-completion-applicability.md; Rule: R4; Example: E7; a durable recovered claim and original issued meaning")
      When("a WorkOrder is issued and then retried with different presentation for the same actual target")
      val issued = _take(issueRecoveredWorkOrderC(
        handle, continuation.continuationId, None, completionoperation,
        requirement, presentation, recovered, issuedstore
      ))
      When("the same actual target retries its saved issue")
      val retry = issueRecoveredWorkOrderC(handle, continuation.continuationId, None, completionoperation,
        requirement, presentation, recovered, issuedstore)
      Then("retry selects the original saved issue without opening or consuming its claim")
      retry shouldBe Consequence.success(issued)
      When("the same actual target retries with a different presentation")
      val changedpresentation = issueRecoveredWorkOrderC(handle, continuation.continuationId, None, completionoperation,
        requirement, presentation.copy(title = "Different review"), recovered, issuedstore)
      Then("the original saved issue is selected despite the descriptive change")
      changedpresentation shouldBe Consequence.success(issued)
      When("the saved issue is offered with a foreign completion Operation")
      val foreigncompletion = issueRecoveredWorkOrderC(handle, continuation.continuationId, None,
        completionoperation.copy(operation = "foreignCompletion"), requirement, presentation, recovered, issuedstore)
      Then("the foreign completion is refused")
      foreigncompletion shouldBe a[Consequence.Failure[_]]
      Given("a local JSON adapter for the saved WorkOrder meaning")
      val jsonroot = java.nio.file.Files.createTempDirectory(
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target")), "issued-workorder-"
      )
      val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "review.context.v1"
        def encode(value: String): io.circe.Json = io.circe.Json.fromString(value)
        def decode(value: io.circe.Json): Either[String, String] =
          value.asString.toRight("expected review context string")
      }
      val jsonstore = new IssuedWorkOrderPersistence.LocalJson[String](jsonroot, codec)
      When("the original issue is saved through the local JSON adapter")
      val firstwrite = jsonstore.putIfAbsentC(issued)
      Then("the original issue is retained")
      firstwrite shouldBe Consequence.success(issued)
      When("the local WorkOrder adapter is reopened")
      val reopenedjsonstore = new IssuedWorkOrderPersistence.LocalJson[String](jsonroot, codec)
      Then("saved meaning survives physical JSON-store reopening without a presentation certificate")
      reopenedjsonstore.loadC(continuation.continuationId) shouldBe Consequence.success(Some(issued))
      When("the reopened adapter receives the original issue again")
      val repeatedwrite = reopenedjsonstore.putIfAbsentC(issued)
      Then("the retained issue remains the original saved meaning")
      repeatedwrite shouldBe Consequence.success(issued)
      When("issuance is retried through the reopened adapter with a descriptive change")
      val reopenedretry = issueRecoveredWorkOrderC(handle, continuation.continuationId, None, completionoperation,
        requirement, presentation.copy(title = "Different review"), recovered, reopenedjsonstore)
      Then("the saved original issue is selected without consuming the claim")
      reopenedretry shouldBe Consequence.success(issued)
      recovered.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)
      val submitted = ContinuationResult(
        handle, continuation.runId, continuation.continuationId,
        continuation.expectedRevision, continuation.context.snapshot,
        TypedValue("review.result.v1", "approved"), ContextReference("review-result", "1"),
        Vector.empty, ExecutionEvidence(Vector.empty)
      )
      var opened = 0
      val fresh = () => {
        opened += 1
        new org.goldenport.cncf.unitofwork.UnitOfWork(org.goldenport.cncf.context.ExecutionContext.create())
      }

      When("a foreign Handle or changed WorkflowInstance suspension is submitted")
      resumeIssuedWithInstanceGuardC(component, issued.copy(handle = handle.copy(
        componentIdentity = org.goldenport.cncf.component.ComponentId("org.goldenport.cncf.test.Other")
      )), submitted, recovered, instances, _same_store_configuration, fresh) shouldBe a[Consequence.Failure[_]]
      stored = Some(unsuspended)
      resumeIssuedWithInstanceGuardC(component, issued, submitted, recovered, instances, _same_store_configuration, fresh) shouldBe
        a[Consequence.Failure[_]]
      stored = Some(_take(initial.appendC(initialRevision, _active_entry("one").copy(
        suspension = Some(boundary.copy(continuationIdentity = ContinuationIdentity("other")))
      ))))
      resumeIssuedWithInstanceGuardC(component, issued, submitted, recovered, instances, _same_store_configuration, fresh) shouldBe
        a[Consequence.Failure[_]]
      Then("the guard rejects each mismatch before a fresh UnitOfWork is opened")
      opened shouldBe 0

      When("the active suspension receives a result with a missing or matching saved issue")
      stored = Some(suspended)
      resumePersistedWorkOrderC(component, submitted, new IssuedWorkOrderPersistence.InMemory[String], recovered,
        instances, _same_store_configuration, fresh) shouldBe a[Consequence.Failure[_]]
      opened shouldBe 0
      resumePersistedWorkOrderC(component, submitted, reopenedjsonstore, recovered, instances, _same_store_configuration, fresh) shouldBe
        Consequence.success(ContinuationRuntime.Resume(continuation, StateMachineOperationResult(
          StateMachineResultTypeReference("review.result.v1"), ContextReference("review-result", "1")
        )))
      Then("resume opens one fresh UnitOfWork and cannot complete the same claim twice")
      opened shouldBe 1
      When("the completed claim receives the same saved result again")
      val replay = resumePersistedWorkOrderC(component, submitted, issuedstore, new PersistentContinuationRuntime(continuationstore),
        instances, _same_store_configuration, fresh)
      Then("replay refuses without opening another UnitOfWork")
      replay shouldBe a[Consequence.Failure[_]]
      opened shouldBe 1
    }

    "advance a persisted Cozy Judgment WorkOrder after its one-shot guarded resume" in {
      import WorkflowProtocolV1.*
      import CandidateAdmissionProducerAbi.{AlternativeIdentity, Evidence, EvidenceFreshness, EvidenceProvenance, EvidenceScope, JudgmentActionIdentity, JudgmentResult, Rationale, TypeIdentity, TypedJudgmentResultV1}

      Given("the admitted Cozy Candidate fixture and a durable suspension for its Judgment Action")
      val source = scala.io.Source.fromInputStream(
        classOf[WorkflowInstancePersistenceSpec].getResourceAsStream(
          "/workflow/candidate-admission-producer-abi.json"), "UTF-8"
      )
      val sourceartifact = try _take(CandidateAdmissionProducerAbi.parseC(source.mkString)) finally source.close()
      val artifact = sourceartifact
      val mismatchedartifact = sourceartifact.copy(models = sourceartifact.models.map(producer =>
        producer.copy(model = producer.model.copy(workflow = producer.model.workflow.map(_.copy(
          identity = CandidateAdmissionProducerAbi.WorkflowIdentity("WorkflowProducer"),
          version = CandidateAdmissionProducerAbi.WorkflowVersion("workflow-producer-v1")
        ))))
      ))
      val workflowsource = scala.io.Source.fromInputStream(
        classOf[WorkflowInstancePersistenceSpec].getResourceAsStream(
          "/workflow/candidate-admission-workflow-abi.json"), "UTF-8"
      )
      val workflowjson = try workflowsource.mkString.stripSuffix("\n") finally workflowsource.close()
      val candidate = _take(CandidateWorkflowAbi.parseC(
        workflowjson, sourceartifact, "modeler/candidate-admission-workflow.cml"))
      val binding = _take(WorkflowInstancePersistence.bindCandidateDefinitionC(candidate))
      val initial = _initial_record(binding)
      val boundary = SuspensionBoundary(
        ContinuationIdentity("payment-review"), InstanceRevision(1L),
        ContextSnapshotReference("payment-v1"), CompletionReference("payment-completion"),
        EvidenceReference("payment-evidence"), CorrelationReference("payment-resume")
      )
      val suspended = _take(initial.appendC(initialRevision, _active_entry("one").copy(suspension = Some(boundary))))
      var stored: Option[InstanceRecord] = Some(initial)
      val instances = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.notImplemented("Judgment fixture starts from a suspended instance")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          Consequence.success(stored.filter(_.identity == identity))
        def append(configuration: Configuration, identity: InstanceIdentity,
          expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] = {
          stored match {
            case Some(current) if current.identity == identity =>
              current.appendC(expectedRevision, entry).map { next =>
                stored = Some(next)
                next
              }
            case _ => Consequence.stateConflict("Judgment instance is unavailable")
          }
        }
      }
      val required = StateMachineRequiredOperation(
        StateMachineRequiredOperationIdentity("capture-payment-capability"), "judge-payment",
        StateMachineOperationIdentity("OrderService", "capturePayment"), None,
        Some(StateMachineResultTypeReference("PaymentResult")),
        StateMachineRequiredOperationMetadata(
          ContextContract("payment-context", Vector.empty, Vector.empty),
          CompletionContract("payment-completion", Vector.empty),
          EvidenceContract("payment-evidence", Vector.empty), Vector.empty
        )
      )
      val continuation = Continuation(
        StateMachineRunIdentity("order-run"), org.goldenport.cncf.workflow.ContinuationIdentity("payment-review"),
        StateMachineRevision("1"), required,
        ContextBundle("payment", Vector.empty, Vector.empty, ContextSnapshot("workflow-v1"))
      )
      val continuationroot = java.nio.file.Files.createTempDirectory(
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target")), "candidate-continuation-"
      )
      val continuationstore = new ContinuationRuntimePersistence.LocalJson(continuationroot)
      val runtime = new PersistentContinuationRuntime(continuationstore)
      val provider = new StateMachineProgramProvider {
        val identity = ProviderIdentity("payment-judgment-provider")
        def program(request: ProviderExecutionRequest): ExecUowM[ActionExecution] = {
          request.runId shouldBe continuation.runId
          request.requiredOperation shouldBe required
          request.context shouldBe continuation.context
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Suspended(continuation)
          )
        }
      }
      val resolver = _take(StateMachineProviderResolver.create(
        Vector(StateMachineProviderBinding(required.identity, provider.identity)), Vector(provider)
      ))
      val componentruntime = new Component() {}
      componentruntime.withStateMachineProviderResolver(resolver)
      val root = ExecutionContext.create()
      val context = root.withScope(Component.Context(
        "payment-judgment", root.scope, componentruntime, ComponentOrigin.Embed
      ))
      val initialunitofwork = new UnitOfWork(context)
      val action = new StateMachineRequiredOperationAction[String, String]((_, _) =>
        ProviderExecutionRequest(continuation.runId, required, None, continuation.context)
      )
      val plan = ExecutionPlan[String, String](Vector.empty, Vector(action), Vector.empty)
      When("the Required SPI Action is interpreted and its WorkflowInstance boundary is appended before publication")
      continuationstore.loadC(continuation.continuationId) shouldBe Consequence.success(None)
      ExecutionPlanExecutor.executeCommittingAfterC(
        plan, "AwaitPayment", "requestJudgment", initialunitofwork, runtime,
        published => {
          published shouldBe continuation
          continuationstore.loadC(continuation.continuationId) shouldBe Consequence.success(None)
          instances.append(_same_store_configuration, initial.identity, initialRevision,
            _active_entry("one").copy(suspension = Some(boundary))).map(_ => ())
        }
      ) shouldBe Consequence.success(Some(continuation))
      Then("the committed suspension is retained")
      stored shouldBe Some(suspended)
      When("the reopened Continuation store claims the committed suspension")
      val claimed = new PersistentContinuationRuntime(
        new ContinuationRuntimePersistence.LocalJson(continuationroot)
      ).claimC(continuation.continuationId)
      Then("the reopened store supplies the actual recovered claim")
      val claim = _take(claimed)
      Given("the actual recovered claim and its WorkflowInstance Handle")
      val component = ComponentId("org.goldenport.cncf.test.JudgmentWorkOrder")
      val handle = _take(WorkflowHandle.fromRecordC(component, suspended))
      val issuedroot = java.nio.file.Files.createTempDirectory(
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target")), "candidate-workorder-"
      )
      val issuedcodec = new WorkflowResultJsonV1.PayloadCodec[String] {
        val typeIdentity = "payment.context.v1"
        def encode(value: String): io.circe.Json = io.circe.Json.fromString(value)
        def decode(value: io.circe.Json): Either[String, String] =
          value.asString.toRight("expected payment context string")
      }
      val issuedwriter = new IssuedWorkOrderPersistence.LocalJson[String](issuedroot, issuedcodec)
      When("the recovered claim issues a WorkOrder through local persistence")
      val issued = _take(issueRecoveredWorkOrderC(
        handle, claim.continuation.continuationId, None,
        StateMachineOperationIdentity("OrderService", "submitPaymentReview"),
        ExecutionRequirement(Vector.empty, RiskLevel("standard"), ReasoningLevel.Standard, true),
        MinimalPresentation("Review payment", "Waiting for judgment"),
        new PersistentContinuationRuntime(new ContinuationRuntimePersistence.LocalJson(continuationroot)),
        issuedwriter
      ))
      val issuedstore = new IssuedWorkOrderPersistence.LocalJson[String](issuedroot, issuedcodec)
      Then("the issued WorkOrder is available through a reopened local adapter")
      issuedstore.loadC(continuation.continuationId) shouldBe Consequence.success(Some(issued))
      val result = JudgmentResult(
        JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"),
        Rationale("decision-rationale"), Evidence("payment-evidence"),
        EvidenceScope("order"), EvidenceFreshness("current"), EvidenceProvenance("payment-ledger")
      )
      val typed = TypedJudgmentResultV1(
        CandidateAdmissionProducerAbi.acceptedTypedJudgmentResultSchemaVersion,
        result, TypeIdentity("PaymentResult"), ContextReference("payment-result", "1")
      )
      val resumedruntime = new PersistentContinuationRuntime(
        new ContinuationRuntimePersistence.LocalJson(continuationroot)
      )
      val adaptercomponent = new Component() with ContinuationRuntimeSource {
        override def continuationRuntimeOption: Option[ContinuationRuntime] = Some(resumedruntime)
      }
      adaptercomponent.initialize(ComponentInit(
        subsystem = TestComponentFactory.emptySubsystem("candidate_judgment_adapter"),
        core = Component.Core.create(
          component.name, component, ComponentInstanceId.default(component), Protocol.empty
        ),
        origin = ComponentOrigin.Builtin
      ))
      val factory = new ComponentFactory
      val bootstrapped = _take(factory.bootstrapC(adaptercomponent))
      bootstrapped.continuationRuntime shouldBe resumedruntime
      var adapted = 0
      val adapter = new ContinuationSpiAdapter[String, TypedJudgmentResultV1, TypedJudgmentResultV1] {
        def admitC(
          workOrder: WorkflowInteraction[String, Nothing],
          submission: TypedJudgmentResultV1
        ): Consequence[ContinuationResult[TypedJudgmentResultV1]] = {
          adapted += 1
          workOrder.current match {
            case work: WorkflowContinuation.WorkOrder[?] =>
              Consequence.success(ContinuationResult(
                workOrder.handle, work.request.runId, work.request.continuationId,
                work.request.expectedRevision, work.request.context.snapshot,
                TypedValue(submission.payloadType.value, submission), submission.payload,
                Vector.empty, ExecutionEvidence(Vector.empty)
              ))
            case _ => Consequence.stateConflict("external adapter requires a WorkOrder")
          }
        }
      }
      val bound = _take(factory.bindContinuationSpiAdapterC(bootstrapped, issuedstore, adapter))
      When("the injected external adapter admits a typed Judgment submission")
      val submitted = _take(bound.admitC(continuation.continuationId, typed))
      Then("the adapter is invoked exactly once")
      adapted shouldBe 1
      val target = CmlStateMachineTransitionTarget.State(CmlStateMachineStateIdentity(
        CmlStateMachineIdentity("OrderProgress"), CmlStateMachineStatePath(Vector("Complete"))
      ))
      val routes = Vector(CandidateAdmissionRouter.Route(
        JudgmentActionIdentity("judge-payment"), AlternativeIdentity("approve"), target
      ))
      val closingaction = new ResolvedAction[InstanceRecord, ContinuationResult[TypedJudgmentResultV1]] {
        def program(
          state: InstanceRecord,
          event: ContinuationResult[TypedJudgmentResultV1]
        ): ExecUowM[ActionExecution] =
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(
              StateMachineResultTypeReference(event.result.typeIdentity), event.resultReference
            ))
          )
      }
      val closingselector = _take(CandidateAdmissionClosingProgram.bindC(
        artifact, routes, issuedstore,
        Vector(CandidateAdmissionClosingProgram.Binding(target, closingaction))
      ))
      When("the admitted Judgment result is offered to the StateMachine closing selector")
      val selectedclosing = _take(closingselector.selectC(submitted, suspended))
      Then("the declared route selects a typed closing program and next WorkflowInstance record")
      selectedclosing.nextInstance shouldBe Some(_take(CandidateWorkflowProgression.nextC(
        suspended, submitted, target
      )))
      When("the selected closing program is interpreted in an active UnitOfWork")
      val interpreted = new org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter(
        new UnitOfWork(ExecutionContext.create())
      ).evaluateInActiveUnitOfWorkC(selectedclosing.program)
      Then("interpretation returns the declared completed result")
      interpreted shouldBe Consequence.success(
        ActionExecution.Completed(StateMachineOperationResult(
          StateMachineResultTypeReference("PaymentResult"), submitted.resultReference
        ))
      )
      When("the closing selector receives an unknown alternative")
      val unknownclosing = closingselector.selectC(submitted.copy(result = TypedValue("PaymentResult", typed.copy(
        result = result.copy(selectedAlternative = AlternativeIdentity("unknown"))
      ))), suspended)
      Then("the unknown alternative is refused")
      unknownclosing shouldBe a[Consequence.Failure[_]]
      Given("a selector without the declared closing target")
      val missingclosing = _take(CandidateAdmissionClosingProgram.bindC(
        artifact, routes, issuedstore,
        Vector(CandidateAdmissionClosingProgram.Binding(CmlStateMachineTransitionTarget.Final, closingaction))
      ))
      When("the actual submission is offered to the selector without its target")
      val missingselection = missingclosing.selectC(submitted, suspended)
      Then("the missing closing selection is refused")
      missingselection shouldBe a[Consequence.Failure[_]]
      var opened = 0
      val fresh = () => {
        opened += 1
        new org.goldenport.cncf.unitofwork.UnitOfWork(org.goldenport.cncf.context.ExecutionContext.create())
      }

      When("a foreign fixture, unknown alternative, or undeclared target is submitted")
      CandidateAdmissionRouter.resumeAndAdvancePersistedSubmittedC(component, mismatchedartifact, submitted,
        routes, issuedstore, resumedruntime, instances, _same_store_configuration, fresh) shouldBe
        a[Consequence.Failure[_]]
      opened shouldBe 0

      CandidateAdmissionRouter.resumeAndAdvancePersistedSubmittedC(component, artifact, submitted.copy(
        result = TypedValue("PaymentResult", typed.copy(result = result.copy(
          selectedAlternative = AlternativeIdentity("unknown")
        )))
      ), routes, issuedstore, resumedruntime, instances, _same_store_configuration, fresh) shouldBe
        a[Consequence.Failure[_]]
      opened shouldBe 0
      resumedruntime.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)

      val undeclaredtarget = CmlStateMachineTransitionTarget.State(CmlStateMachineStateIdentity(
        CmlStateMachineIdentity("OrderProgress"), CmlStateMachineStatePath(Vector("Undeclared"))
      ))
      CandidateAdmissionRouter.resumeAndAdvancePersistedSubmittedC(component, artifact, submitted,
        routes.map(_.copy(target = undeclaredtarget)), issuedstore, resumedruntime,
        instances, _same_store_configuration, fresh) shouldBe a[Consequence.Failure[_]]
      Then("each invalid submission leaves the suspended record and claim intact")
      opened shouldBe 0
      stored shouldBe Some(suspended)
      resumedruntime.recoverClaimC(continuation.continuationId) shouldBe Consequence.success(claim)

      val expectednext = _take(CandidateWorkflowProgression.nextC(suspended, submitted, target))
      When("the declared route resumes the Judgment Action in a fresh UnitOfWork")
      CandidateAdmissionRouter.resumeAndAdvancePersistedSubmittedC(component, artifact, submitted, routes,
        issuedstore, resumedruntime, instances, _same_store_configuration, fresh) shouldBe
        Consequence.success(CandidateAdmissionRouter.AdvancedResume(
          ContinuationRuntime.Resume(continuation, StateMachineOperationResult(
            StateMachineResultTypeReference("PaymentResult"), typed.payload
          )), target, expectednext
        ))
      Then("the next revision is persisted once and duplicate external execution is rejected")
      opened shouldBe 1
      stored shouldBe Some(expectednext)
      instances.load(_same_store_configuration, suspended.identity) shouldBe
        Consequence.success(Some(expectednext))
      expectednext.suspension shouldBe None
      expectednext.revision shouldBe InstanceRevision(2L)
      expectednext.currentProgression shouldBe Some(ProgressionReference(
        "{\"schemaVersion\":\"cncf.candidate-workflow-progression.v1\",\"machine\":\"OrderProgress\",\"path\":[\"Complete\"]}"
      ))
      When("native replay is offered and the completed issue is separately translated as retained data")
      val replay = CandidateAdmissionRouter.resumeAndAdvancePersistedSubmittedC(component, artifact, submitted, routes,
        issuedstore, new PersistentContinuationRuntime(
          new ContinuationRuntimePersistence.LocalJson(continuationroot)
        ), instances,
        _same_store_configuration, fresh)
      val translated = bound.admitC(continuation.continuationId, typed)
      val completedstatus = new ContinuationRuntimePersistence.LocalJson(continuationroot)
        .loadC(continuation.continuationId).toOption.flatten.map(_.status)
      Then("conversion returns the original meaning without a new transaction, while actual native replay is refused")
      replay shouldBe a[Consequence.Failure[_]]
      translated shouldBe Consequence.success(submitted)
      adapted shouldBe 2
      opened shouldBe 1
      stored shouldBe Some(expectednext)
      issuedstore.loadC(continuation.continuationId) shouldBe Consequence.success(Some(issued))
      completedstatus shouldBe Some(ContinuationRuntimePersistence.Status.Completed)
    }
  }

  private final class ProbePersistence extends WorkflowInstancePersistence {
    def create(
      configuration: Configuration,
      record: InstanceRecord
    ): Consequence[InstanceRecord] =
      Consequence.success(record)

    def load(
      configuration: Configuration,
      identity: InstanceIdentity
    ): Consequence[Option[InstanceRecord]] =
      Consequence.success(None)

    def append(
      configuration: Configuration,
      identity: InstanceIdentity,
      expectedRevision: InstanceRevision,
      entry: HistoryEntry
    ): Consequence[InstanceRecord] =
      Consequence.notImplemented("probe only verifies the SPI declaration")
  }

  private def _initial_record(
    binding: DefinitionBinding,
    suffix: String = "one"
  ): InstanceRecord =
    InstanceRecord(
      identity = InstanceIdentity(s"instance-$suffix"),
      definition = binding,
      revision = initialRevision,
      lifecycle = Lifecycle.NotStarted,
      currentProgression = None,
      correlation = CorrelationReference(s"correlation-$suffix"),
      causation = CausationReference(s"causation-$suffix"),
      derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference(s"composite-$suffix")),
      committedPredecessor = Some(CommittedEntityTransitionReference(s"committed-$suffix")),
      suspension = None,
      history = Vector.empty
    )

  private def _active_entry(suffix: String): HistoryEntry =
    HistoryEntry(
      sequence = HistorySequence(1L),
      revision = InstanceRevision(1L),
      lifecycle = Lifecycle.Active,
      currentProgression = Some(ProgressionReference(s"progress-$suffix")),
      correlation = CorrelationReference(s"correlation-$suffix"),
      causation = CausationReference(s"causation-$suffix"),
      derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference(s"composite-$suffix")),
      committedPredecessor = Some(CommittedEntityTransitionReference(s"committed-$suffix")),
      suspension = None
    )

  private def _continuation_record(identity: String): ContinuationRuntimePersistence.Record = {
    val required = StateMachineRequiredOperation(
      StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange",
      StateMachineOperationIdentity("review", "change"),
      None,
      Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(
        ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty),
        EvidenceContract("review.evidence", Vector.empty),
        Vector.empty
      )
    )
    ContinuationRuntimePersistence.Record(
      org.goldenport.cncf.workflow.Continuation(
        StateMachineRunIdentity("run-one"),
        org.goldenport.cncf.workflow.ContinuationIdentity(identity),
        StateMachineRevision("1"),
        required,
        ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))
      ),
      ContinuationRuntimePersistence.Status.Available
    )
  }

  private val _same_store_configuration = Configuration(
    workflowStore = NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    migrationPolicy = MigrationPolicy("consumer-managed-v1"),
    retentionPolicy = RetentionPolicy("consumer-managed-v1"),
    leasePolicy = LeasePolicy("consumer-managed-v1"),
    delivery = DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )

  private def _definition(): Definition = {
    val root = GeneratedSourceLocation(
      "src/test/resources/modeler/skill-driven-workflow-producer.cml",
      1
    )
    val definition = root.copy(line = 20)
    val composite = root.copy(line = 23)
    val states = Vector(
      StateDescriptor(StateIdentity("Pending"), root.copy(line = 31)),
      StateDescriptor(StateIdentity("Approved"), root.copy(line = 33))
    )
    val actionnames = Vector(
      "BuildProject" -> "buildProject",
      "RunTests" -> "runTests",
      "ReviewChange" -> "reviewChange",
      "CommitChanges" -> "commitChanges"
    )
    val actions = actionnames.zipWithIndex.map { case ((name, operation), index) =>
      val source = root.copy(line = 46 + index * 5)
      ActionDescriptor(
        ActionIdentity(name),
        ActionKind.Operation,
        _operation(operation, source),
        Some("review.subject"),
        source
      )
    }
    val reviewaction = actions.find(_.identity == ActionIdentity("ReviewChange")).get
    Definition(
      producerAbiIdentity = acceptedProducerAbiIdentity,
      workflowAbiIdentity = acceptedWorkflowAbiIdentity,
      bootstrapSchemaIdentity = acceptedBootstrapSchemaIdentity,
      producerRevision = acceptedProducerRevision,
      fixtureSha256 = acceptedFixtureSha256,
      workflow = WorkflowDescriptor(
        WorkflowIdentity("WorkflowProducer"),
        WorkflowRevision("workflow-producer-v1"),
        WorkflowSourceCorrelation(root, definition)
      ),
      compositeStateMachine = CompositeStateMachineDescriptor(
        CompositeStateMachineIdentity("review"),
        StateMachineIdentity("ReviewLifecycle"),
        states,
        composite
      ),
      actions = actions,
      requiredSpis = Vector(RequiredSpiDescriptor(
        RequiredSpiIdentity("review-change-capability"),
        reviewaction.identity,
        reviewaction.operation,
        root.copy(line = 68),
        reviewaction.sourceLocation
      )),
      schemaShapes = requiredSchemaShapes.toVector.sortBy(_._1).map {
        case (identity, members) => SchemaShapeDescriptor(identity, members, _abi_source)
      }
    )
  }

  private def _operation(
    operation: String,
    source: GeneratedSourceLocation
  ): OperationDescriptor =
    OperationDescriptor(
      ServiceIdentity("WorkflowService"),
      OperationIdentity(operation),
      Some(TypeIdentity("ReviewContext")),
      Some(TypeIdentity("ReviewResult")),
      source
    )

  private val _abi_source = GeneratedSourceLocation(
    "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala",
    1
  )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))

  private def _diagnostic[A](result: Consequence[A]): Diagnostic = {
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: WorkflowInstancePersistenceException) => exception.diagnostic
          case other => fail(s"expected workflow instance persistence diagnostic but got $other")
        }
      case other =>
        fail(s"expected workflow instance persistence failure but got $other")
    }
  }
}
