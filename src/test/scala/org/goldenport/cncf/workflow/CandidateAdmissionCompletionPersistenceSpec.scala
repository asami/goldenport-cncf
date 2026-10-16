package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.sql.Connection
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
final class CandidateAdmissionCompletionPersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private type Submission = AdmissionSubmission[String, String, Int]
  private given ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "admission_instance_v1"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "admission_continuation_v1"))
  private val _component = ComponentId("org.goldenport.cncf.test.AdmissionWork")
  private val _operation = StateMachineOperationIdentity("workflow", "submitReview")
  private val _owner = _take(DataStoreComponentOwner.create("org.goldenport.cncf.test.AdmissionWork"))
  private val _configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))
  private val _identity = ContinuationIdentity("continuation-1")
  private val _target = CandidateRef("candidate-1", "candidate-revision-2")
  private val _input_codec = _text_codec("review.input.v1")
  private val _candidate_codec = _text_codec("candidate.payload.v1")
  private val _evidence_codec = _text_codec("review.evidence.payload.v1")
  private val _scope_codec = new PayloadCodec[Int] {
    val typeIdentity = "review.scope.v1"
    def encode(value: Int): Json = Json.fromInt(value)
    def decode(value: Json): Either[String, Int] = value.asNumber.flatMap(_.toInt).toRight("scope integer required")
  }
  private val _result_codec = _take(CandidateAdmissionSubmissionJsonV1.payloadCodecC(
    "review.submission.v1", _candidate_codec, _evidence_codec, _scope_codec))
  private val _requirement = AdmissionRequirement("semantic-review", RequirementKind.Semantic,
    _evidence_codec.typeIdentity, TypedValue(_scope_codec.typeIdentity, 3))
  private val _policy = new EvidencePolicy[String, Int] {
    def isFresh(evidence: AdmissionEvidence[String, Int], target: CandidateRef): Boolean =
      evidence.provenance != "expired"
    def covers(evidence: AdmissionEvidence[String, Int], requirement: AdmissionRequirement[Int]): Boolean =
      evidence.scope.value >= requirement.scope.value
  }
  private def _text_codec(identity: String): PayloadCodec[String] = new PayloadCodec[String] {
    val typeIdentity = identity
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("text required")
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def _binding: DefinitionBinding = {
    val producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
    _take(WorkflowInstancePersistence.bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
      _resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def _initial: InstanceRecord = InstanceRecord(InstanceIdentity("instance-1"), _binding,
    initialRevision, Lifecycle.NotStarted, None, CorrelationReference("correlation-1"),
    CausationReference("start-1"), None, None, None, Vector.empty)
  private def _submission: Submission = AdmissionSubmission("submission-1",
    CandidateSnapshot(_target, TypedValue(_candidate_codec.typeIdentity, "日本語 draft\n\"exact\"")),
    Vector(AdmissionEvidence("evidence-1", "semantic-review", _target,
      TypedValue(_evidence_codec.typeIdentity, "review payload"), TypedValue(_scope_codec.typeIdentity, 3), "review-run-1")))
  private def _root(store: DataStore) =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _configuration, Vector(_binding), _instances))
  private def _claims(store: DataStore) =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _configuration, _continuations))
  private def _completion(store: DataStore, codec: PayloadCodec[Submission] = _result_codec) =
    _take(WorkflowCompletionDataStorePersistence.bindC(store, _configuration, _component, _operation,
      Vector(_binding), _instances, _continuations, _input_codec, codec))
  private def _path: Path = {
    val directory = Path.of("target", "phase88", "work", "admission-evidence")
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "spec-").resolve("workflow.sqlite")
  }
  private def _sqlite_config: SQLiteConfig = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(10000)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    config
  }
  private def _stored(store: DataStore) = (
    _take(store.load(_instances, DataStore.StringEntryId("instance-1"))).get,
    _take(store.load(_continuations, DataStore.StringEntryId(_identity.value))).get)
  private def _seed(store: DataStore, submission: Submission = _submission):
      WorkflowCompletionDataStorePersistence.Intent[Submission] = {
    val current = _initial
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(_input_codec.typeIdentity)),
      Some(StateMachineResultTypeReference(_result_codec.typeIdentity)),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector(ContextReference("collected", "1"))),
        EvidenceContract("review.evidence", Vector(ContextReference("review-proof", "1"))), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      _identity, StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(_identity.value), InstanceRevision(1L),
        ContextSnapshotReference(current.definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume-1"))))
    val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _configuration, current, initialRevision, entry, continuation)
    val c = continuation.continuation
    val work: WorkflowInteraction[String, Nothing] = WorkflowInteraction(
      _take(WorkflowHandle.fromRecordC(_component, current)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation,
        Some(TypedValue(_input_codec.typeIdentity, "exact review input")), c.requiredOperation.resultType,
        c.context, c.requiredOperation.metadata.completionContract, c.requiredOperation.metadata.evidenceContract, _operation),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Awaiting semantic result")))
    _take(_root(store).create(_configuration, current))
    val publisher = _take(WorkflowIssuedWorkDataStorePersistence.bindC(store, _configuration, _component,
      _operation, Vector(_binding), _instances, _continuations, _input_codec))
    val suspended = _take(publisher.suspendC(intent, work, _owner)).instance
    val token = _take(_claims(store).claimC(_identity)).claimId.get
    val result = ContinuationResult(work.handle, c.runId, c.continuationId, c.expectedRevision, c.context.snapshot,
      TypedValue(_result_codec.typeIdentity, submission), ContextReference("result-1", "1"),
      c.requiredOperation.metadata.completionContract.requiredFacts,
      ExecutionEvidence(c.requiredOperation.metadata.evidenceContract.requiredEvidence, Some("worker-1"), Some("model-1")))
    val complete = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
      Some(ProgressionReference("review.Collected")), current.correlation, CausationReference("complete-1"), None, None, None)
    WorkflowCompletionDataStorePersistence.Intent(suspended, suspended.revision, complete, token, result)
  }

  "Candidate-Admission retained completion" should {
    "canonical payload/scope composition" which {
      "retain exact submission and ExecutionEvidence in the same memory completion" in {
        Given("one claimed typed submission in a borrowed canonical memory store")
        val store = DataStore.inMemory()
        val intent = _seed(store)
        val issued = _stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField)
        When("the host commits its guarded completion")
        val completed = _take(_completion(store).completeC(intent, _owner))
        val recovered = _take(_completion(store).loadC(_identity)).get
        Then("candidate, application evidence/scope and generic execution evidence are exact")
        recovered.result shouldBe intent.result
        completed.instance.history shouldBe intent.current.history :+ intent.entry
        completed.continuation.claimId shouldBe Some(intent.claimId)
        recovered.result.result.value.evidence.head.scope.value shouldBe 3
        _stored(store)._2.getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe issued
      }

      "recover exact submission after SQLite resource reopen without replay or admission authority" in {
        Given("one claimed submission and a configured SQLite record store")
        val path = _path
        val first = SqlDataStore.sqlite(path.toString)
        val intent = _seed(first)
        When("completion commits and the resource is closed and reconstructed")
        try _take(_completion(first).completeC(intent, _owner)) finally _take(first.closeC())
        val reopened = SqlDataStore.sqlite(path.toString)
        try {
          val recovered = _take(_completion(reopened).loadC(_identity)).get
          val evaluation = _take(evaluateC(recovered.result.result.value, Vector(_requirement), _policy))
          Then("exact retained payload/scope can be evaluated only through the explicit application policy")
          recovered.result shouldBe intent.result
          evaluation.result shouldBe AdmissionResult.Admitted(_target, Vector("evidence-1"))
          _completion(reopened).completeC(intent, _owner).toOption shouldBe None
          _claims(reopened).claimC(_identity).toOption shouldBe None
          _take(_root(reopened).load(_configuration, intent.current.identity)).get.lifecycle shouldBe Lifecycle.Active
        } finally _take(reopened.closeC())
      }
    }

    "application-owned evidence reuse" which {
      "retain missing, stale, wrong-target and narrow evidence as Pending gaps" in {
        Given("shape-valid submissions that fail four different admission requirements")
        val original = _submission
        val variants = Vector(
          original.copy(evidence = Vector.empty[AdmissionEvidence[String, Int]]) -> EvidenceDefect.Missing,
          original.copy(evidence = original.evidence.map(_.copy(provenance = "expired"))) -> EvidenceDefect.Stale,
          original.copy(evidence = original.evidence.map(_.copy(target = CandidateRef("candidate-1", "revision-1")))) -> EvidenceDefect.WrongTarget,
          original.copy(evidence = original.evidence.map(_.copy(scope = TypedValue(_scope_codec.typeIdentity, 1)))) -> EvidenceDefect.InsufficientCoverage)
        variants.foreach { case (submission, defect) =>
          val store = DataStore.inMemory()
          val intent = _seed(store, submission)
          When("the result is completed, recovered and evaluated under application policy")
          _take(_completion(store).completeC(intent, _owner))
          val retained = _take(_completion(store).loadC(_identity)).get
          val evaluation = _take(evaluateC(retained.result.result.value, Vector(_requirement), _policy))
          Then("completion preserves the evidence and never fabricates a positive admission")
          retained.result.result.value shouldBe submission
          evaluation.gaps.head.defects should contain (defect)
          evaluation.result.isInstanceOf[AdmissionResult.Pending[?]] shouldBe true
        }
      }

      "re-evaluate recovered evidence under a changed freshness policy without altering stored facts" in {
        Given("retained evidence that satisfied one explicitly supplied freshness policy")
        val store = DataStore.inMemory()
        val intent = _seed(store)
        _take(_completion(store).completeC(intent, _owner))
        val before = _stored(store)
        val expired = new EvidencePolicy[String, Int] {
          def isFresh(evidence: AdmissionEvidence[String, Int], target: CandidateRef): Boolean = false
          def covers(evidence: AdmissionEvidence[String, Int], requirement: AdmissionRequirement[Int]): Boolean = true
        }
        When("the same retained evidence is assessed with a different current policy")
        val submission = _take(_completion(store).loadC(_identity)).get.result.result.value
        val accepted = _take(evaluateC(submission, Vector(_requirement), _policy))
        val rejected = _take(evaluateC(submission, Vector(_requirement), expired))
        Then("stored completion grants no durable positive judgment or progression selector")
        accepted.gaps shouldBe empty
        rejected.gaps.head.defects should contain (EvidenceDefect.Stale)
        _stored(store) shouldBe before
      }
    }

    "failure and recovery" which {
      "reject invalid, lossy and throwing payload/scope codecs before any record mutation" in {
        Given("a valid claim and a candidate codec that loses or throws away the submitted meaning")
        val store = DataStore.inMemory()
        val intent = _seed(store)
        val before = _stored(store)
        val lossy = new PayloadCodec[String] {
          val typeIdentity = _candidate_codec.typeIdentity
          def encode(value: String): Json = Json.fromString("lost")
          def decode(value: Json): Either[String, String] = value.asString.toRight("text required")
        }
        val throwing = new PayloadCodec[Int] {
          val typeIdentity = _scope_codec.typeIdentity
          def encode(value: Int): Json = throw new IllegalStateException("scope unavailable")
          def decode(value: Json): Either[String, Int] = throw new IllegalStateException("scope unavailable")
        }
        val codecs = Vector(
          _take(CandidateAdmissionSubmissionJsonV1.payloadCodecC(_result_codec.typeIdentity,
            lossy, _evidence_codec, _scope_codec)),
          _take(CandidateAdmissionSubmissionJsonV1.payloadCodecC(_result_codec.typeIdentity,
            _candidate_codec, _evidence_codec, throwing)))
        val invalid = intent.result.copy(result = intent.result.result.copy(value =
          _submission.copy(evidence = _submission.evidence ++ _submission.evidence)))
        When("invalid and non-exact result payloads are submitted")
        val rejected = codecs.map(codec => _completion(store, codec).completeC(intent, _owner))
        val duplicated = _completion(store).completeC(intent.copy(result = invalid), _owner)
        Then("both guarded records remain unchanged with the claim still live")
        rejected.foreach(_.toOption shouldBe None)
        duplicated.toOption shouldBe None
        _stored(store) shouldBe before
      }

      "reject old claim tokens and replay without replacing retained evidence" in {
        Given("a released and re-claimed submission")
        val store = DataStore.inMemory()
        val intent = _seed(store)
        _take(_claims(store).releaseC(_identity, intent.claimId))
        val token = _take(_claims(store).claimC(_identity)).claimId.get
        val before = _stored(store)
        When("the stale token is rejected and the current token completes exactly once")
        val stale = _completion(store).completeC(intent, _owner)
        Then("the stale token changes no record")
        stale.toOption shouldBe None
        _stored(store) shouldBe before
        _take(_completion(store).completeC(intent.copy(claimId = token), _owner))
        val retained = _stored(store)
        val changed = intent.result.copy(result = intent.result.result.copy(value =
          _submission.copy(evidence = Vector.empty[AdmissionEvidence[String, Int]])))
        _completion(store).completeC(intent.copy(claimId = token, result = changed), _owner).toOption shouldBe None
        _stored(store) shouldBe retained
        _take(_completion(store).loadC(_identity)).get.result shouldBe intent.result
      }

      "roll back payload/scope and both records at pre-commit failure checkpoints" in {
        Given("canonical SQLite completion with injected provider rejection")
        val stages = Vector(Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
          Some(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared),
          Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
          Some(DataStoreConditionalTransitionCheckpoint.BeforePublish), None)
        stages.foreach { stage =>
          val path = _path
          val source = new SQLiteDataSource(_sqlite_config)
          source.setUrl(s"jdbc:sqlite:$path")
          var armed = false
          val store = new SqlDataStore(SqliteDialectDriver, source) {
            override protected def conditional_transition_checkpoint(value: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] =
              if (armed && stage.contains(value)) Consequence.operationInvalid("injected admission checkpoint")
              else Consequence.unit
            override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] =
              if (armed && stage.isEmpty) DataStoreConditionalTransitionFailure.transactionFailure("injected admission commit")
              else super.conditional_transition_commit(connection)
          }
          val intent = _seed(store)
          val before = _stored(store)
          When("the completion transaction is rejected before commit")
          armed = true
          val result = try _completion(store).completeC(intent, _owner) finally _take(store.closeC())
          val reopened = SqlDataStore.sqlite(path.toString)
          try {
            Then("neither completed evidence nor an appended instance survives reopen")
            result.toOption shouldBe None
            _stored(reopened) shouldBe before
            _take(_completion(reopened).loadC(_identity)) shouldBe None
          } finally _take(reopened.closeC())
        }
      }

      "recover committed evidence after indeterminate applied readback without executing again" in {
        Given("a provider that commits but returns an unadmittable result projection")
        val path = _path
        val source = new SQLiteDataSource(_sqlite_config)
        source.setUrl(s"jdbc:sqlite:$path")
        val store = new SqlDataStore(SqliteDialectDriver, source) {
          override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
              Consequence[DataStoreConditionalTransitionResult] = super.conditionalTransition(plan).map {
            case DataStoreConditionalTransitionResult.Transitioned(root, claim)
                if plan.successor.isInstanceOf[DataStoreConditionalSuccessor.Update] =>
              DataStoreConditionalTransitionResult.Transitioned(root,
                Record(claim.fields.filterNot(_.key == WorkflowCompletionDataStorePersistence.payloadField)))
            case other => other
          }
        }
        val intent = _seed(store)
        When("the committed result cannot be admitted from that readback")
        val result = try _completion(store).completeC(intent, _owner) finally _take(store.closeC())
        val reopened = SqlDataStore.sqlite(path.toString)
        try {
          Then("the failure is transaction-indeterminate and exact evidence is recovered by a query")
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.observation.cause.descriptor.facets.collect {
                case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
              } should contain ("transaction-indeterminate")
            case _ => fail("expected indeterminate completion")
          }
          _take(_completion(reopened).loadC(_identity)).get.result shouldBe intent.result
          _completion(reopened).completeC(intent, _owner).toOption shouldBe None
          _take(_root(reopened).load(_configuration, intent.current.identity)).get.history.size shouldBe 2
        } finally _take(reopened.closeC())
      }

      "reject incompatible scope recovery without rewriting the retained result" in {
        Given("a completed SQLite submission and an incompatible replacement scope decoder")
        val store = SqlDataStore.sqlite(_path.toString)
        try {
          val intent = _seed(store)
          _take(_completion(store).completeC(intent, _owner))
          val before = _stored(store)
          val incompatible = new PayloadCodec[Int] {
            val typeIdentity = _scope_codec.typeIdentity
            def encode(value: Int): Json = Json.fromInt(value)
            def decode(value: Json): Either[String, Int] = Left("scope schema unavailable")
          }
          val codec = _take(CandidateAdmissionSubmissionJsonV1.payloadCodecC(_result_codec.typeIdentity,
            _candidate_codec, _evidence_codec, incompatible))
          When("recovery uses the incompatible application scope binding")
          val failed = _completion(store, codec).loadC(_identity)
          Then("read-only recovery fails closed without repairing payload, scope, claim or history")
          failed.toOption shouldBe None
          _stored(store) shouldBe before
          _take(_completion(store).loadC(_identity)).get.result shouldBe intent.result
        } finally _take(store.closeC())
      }
    }
  }
}
