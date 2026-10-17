package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.sqlite.SQLiteDataSource

/** Selected native data and owning result conditions, not copy certificates.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowIssuedSourceConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "issued_source_instance"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "issued_source_continuation"))
  private val _component = ComponentId("org.goldenport.cncf.test.IssuedSource")
  private val _owner = _take(DataStoreComponentOwner.create(_component.name))
  private val _operation = StateMachineOperationIdentity("workflow", "completeReview")
  private val _identity = ContinuationIdentity("issued-source-continuation")
  private val _fact = ContextReference("required.fact", "1")
  private val _evidence = ContextReference("required.evidence", "1")
  private val _obsolete = ContextReference("obsolete.copy.only", "old")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("issued-source-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("issued-source-store")))
  private var _input_reads = 0
  private val _input_codec = new PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = {
      _input_reads += 1
      value.asString.toRight("Review input must be text")
    }
  }
  private val _result_codec = new PayloadCodec[String] {
    val typeIdentity = "review.result.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("Review result must be text")
  }

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected rejection: $other")
  }
  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def _binding: DefinitionBinding = {
    val producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
    _take(bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
      _resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def _claims(store: DataStore): WorkflowContinuationDataStorePersistence =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _continuations))
  private def _root(store: DataStore): WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_binding), _instances))
  private def _issued(store: DataStore, kind: WorkflowIssuedContinuationKind): WorkflowIssuedWorkDataStorePersistence[String] =
    _take(WorkflowIssuedWorkDataStorePersistence.bindForKindC(store, _config, _component, _operation,
      Vector(_binding), _instances, _continuations, _input_codec, kind))
  private def _completion(store: DataStore, kind: WorkflowIssuedContinuationKind): WorkflowCompletionDataStorePersistence[String, String] =
    _take(WorkflowCompletionDataStorePersistence.bindForKindC(store, _config, _component, _operation,
      Vector(_binding), _instances, _continuations, _input_codec, _result_codec, kind))
  private def _path(context: String): String = {
    val directory = Path.of("target", "workflow", "work", "issued-source", context)
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "case-").resolve("workflow.sqlite").toString
  }
  private def _source(path: String): SQLiteDataSource = {
    val source = new SQLiteDataSource()
    source.setUrl(s"jdbc:sqlite:$path")
    source
  }
  private final class ObservedStore(path: String) extends SqlDataStore(SqliteDialectDriver, _source(path)) {
    var instanceReads = 0
    var continuationReads = 0
    var commits = 0
    override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext): Consequence[Option[Record]] = {
      if (collection == _instances) instanceReads += 1
      if (collection == _continuations) continuationReads += 1
      super.load(collection, id)
    }
    override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext): Consequence[DataStoreConditionalTransitionResult] = {
      commits += 1
      super.conditionalTransition(plan)
    }
    def reset(): Unit = { instanceReads = 0; continuationReads = 0; commits = 0; _input_reads = 0 }
    def observations: (Int, Int, Int, Int) = (instanceReads, continuationReads, _input_reads, commits)
  }
  private def _seed(store: DataStore, kind: WorkflowIssuedContinuationKind): WorkflowCompletionDataStorePersistence.Intent[String] = {
    val current = InstanceRecord(InstanceIdentity("issued-source-instance"), _binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("source-correlation"), CausationReference("source-start"), None, None, None, Vector.empty)
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(_input_codec.typeIdentity)), Some(StateMachineResultTypeReference(_result_codec.typeIdentity)),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector(_fact)), EvidenceContract("review.evidence", Vector(_evidence)), Vector.empty))
    val c = Continuation(StateMachineRunIdentity("issued-source-run"), _identity, StateMachineRevision("machine-v1"),
      required, ContextBundle("Review original input", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value)))
    val native = ContinuationRuntimePersistence.Record(c, ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(_identity.value), InstanceRevision(1L),
        ContextSnapshotReference(c.context.snapshot.workflowRevision), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("source-resume"))))
    val request = ContinuationRequest(c.runId, c.continuationId, c.expectedRevision, required.identity, required.operation,
      Some(TypedValue(_input_codec.typeIdentity, "Original review input")), required.resultType, c.context,
      required.metadata.completionContract, required.metadata.evidenceContract, _operation)
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"), ReasoningLevel.Deep, true,
      Some(ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional, "source-parent")))
    val presentation = MinimalPresentation("Original review", "Awaiting original result")
    val interaction: WorkflowContinuation[String, Nothing] = kind match {
      case WorkflowIssuedContinuationKind.WorkOrder => WorkflowContinuation.WorkOrder(request, requirement, presentation)
      case WorkflowIssuedContinuationKind.Decision => WorkflowContinuation.Decision(request, presentation)
    }
    val work = WorkflowInteraction(_take(WorkflowHandle.fromRecordC(_component, current)), interaction)
    _take(_root(store).create(_config, current))
    val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _config, current, initialRevision, entry, native)
    val suspended = _take(_issued(store, kind).suspendC(intent, work, _owner)).instance
    val token = _take(_claims(store).claimC(_identity)).claimId.get
    val execution = if (kind == WorkflowIssuedContinuationKind.WorkOrder)
      Some(ExecutionObservation(ExecutionPlacement.Inline, "source-parent", "source-parent", "fixture-profile")) else None
    val result = ContinuationResult(work.handle, c.runId, c.continuationId, c.expectedRevision, c.context.snapshot,
      TypedValue(_result_codec.typeIdentity, "Actual review result"), ContextReference("source-result", "1"),
      Vector(_fact), ExecutionEvidence(Vector(_evidence), execution = execution))
    val completed = entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
      lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
    WorkflowCompletionDataStorePersistence.Intent(suspended, suspended.revision, completed, token, result)
  }
  private def _obsolete_issue(store: DataStore, kind: WorkflowIssuedContinuationKind): Unit = {
    val saved = _take(_issued(store, kind).loadC(_identity)).get
    val request = _take(WorkflowIssuedWorkDataStorePersistence.requestC(saved, kind))
    val obsolete = request.copy(completion = request.completion.copy(requiredFacts = Vector(_obsolete)),
      evidence = request.evidence.copy(requiredEvidence = Vector(_obsolete)))
    val changed: WorkflowContinuation[String, Nothing] = saved.current match {
      case order: WorkflowContinuation.WorkOrder[?] =>
        order.asInstanceOf[WorkflowContinuation.WorkOrder[String]].copy(request = obsolete)
      case decision: WorkflowContinuation.Decision[?] =>
        decision.asInstanceOf[WorkflowContinuation.Decision[String]].copy(request = obsolete)
      case _ => fail("Fixture requires its issued kind")
    }
    val bytes = _take(WorkflowIssuedWorkDataStorePersistence.encodeC(saved.copy(current = changed), _input_codec, kind))
    _take(store.update(_continuations, WorkflowContinuationDataStorePersistence.id(_identity), Record.dataAuto(
      WorkflowIssuedWorkDataStorePersistence.payloadField -> WorkflowInstanceDataStorePersistence.storageText(bytes))))
  }
  private def _complete_native_conditions(kind: WorkflowIssuedContinuationKind): Unit = {
    val path = _path("conditions")
    val first = SqlDataStore.sqlite(path)
    val request = try {
      val intent = _seed(first, kind)
      _obsolete_issue(first, kind)
      intent
    } finally _take(first.closeC())
    val reopened = new ObservedStore(path)
    try {
      val completion = _completion(reopened, kind)
      val original = _take(_issued(reopened, kind).loadC(_identity)).get
      val variants = Vector("missing-fact", "missing-evidence", "foreign-claim") ++
        (if (kind == WorkflowIssuedContinuationKind.WorkOrder) Vector("missing-execution") else Vector.empty)
      variants.foreach { variant =>
        val candidate = variant match {
          case "missing-fact" => request.copy(result = request.result.copy(completionFacts = Vector.empty))
          case "missing-evidence" => request.copy(result = request.result.copy(evidence = request.result.evidence.copy(references = Vector.empty)))
          case "missing-execution" => request.copy(result = request.result.copy(evidence = request.result.evidence.copy(execution = None)))
          case _ => request.copy(claimId = "foreign-claim")
        }
        When(s"$kind completion consumes $variant against actual native conditions or claim")
        reopened.reset()
        val rejected = completion.completeC(candidate, _owner)
        val observed = reopened.observations
        val instance = _take(_root(reopened).load(_config, request.current.identity)).get
        val claim = _take(_claims(reopened).loadC(_identity)).get
        Then("one source read and input decode cannot grant missing actual requirements or a foreign claim")
        rejected.toOption shouldBe None
        observed shouldBe ((1, 1, 1, 0))
        instance.revision shouldBe request.current.revision
        claim.status shouldBe ContinuationRuntimePersistence.Status.Claimed
        claim.claimId shouldBe Some(request.claimId)
      }
      When(s"$kind completes with actual native facts/evidence rather than obsolete copy-only requirements")
      reopened.reset()
      val completed = _take(completion.completeC(request, _owner))
      val observed = reopened.observations
      val after = _take(_issued(reopened, kind).loadC(_identity)).get
      val replay = completion.completeC(request, _owner)
      val retained = _take(completion.loadC(_identity)).get
      Then("one native selection commits once, preserves original issued meaning and refuses replay")
      observed shouldBe ((1, 1, 1, 1))
      completed.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
      after shouldBe original
      replay.toOption shouldBe None
      retained.result.completionFacts shouldBe Vector(_fact)
      retained.result.evidence.references shouldBe Vector(_evidence)
    } finally _take(reopened.closeC())
  }

  "Issued native source consumption" should {
    "retained input" which {
      "E20 read original issued meaning without a historical publication certificate" must
        afterWord("in spec:workflow-completion-applicability, example:E20, rules:R10, phase:5") {
        "physically reopen differing historical annotations without claim or rewrite" in {
          Given("docs/spec/workflow-completion-applicability.md R10 E20: original issued input after completed history annotations differ")
          When("a target-owned SQLite provider issues and completes actual work, then retains different historical annotations")
          val path = _path("history")
          val first = SqlDataStore.sqlite(path)
          var original: Option[WorkflowInteraction[String, Nothing]] = None
          try {
            val request = _seed(first, WorkflowIssuedContinuationKind.WorkOrder)
            original = _take(_issued(first, WorkflowIssuedContinuationKind.WorkOrder).loadC(_identity))
            val completed = _take(_completion(first, WorkflowIssuedContinuationKind.WorkOrder).completeC(request, _owner))
            val history = completed.instance.history
            val entry = history.head.copy(suspension = history.head.suspension.map(_.copy(
              contextSnapshot = ContextSnapshotReference("historical-context"),
              completion = CompletionReference("historical-completion"), evidence = EvidenceReference("historical-evidence"))))
            val changed = completed.instance.copy(history = history.updated(0, entry))
            _take(first.update(_instances, DataStore.StringEntryId(changed.identity.value), Record.dataAuto(
              WorkflowInstanceDataStorePersistence.recordField -> WorkflowInstanceDataStorePersistence.storageText(
                _take(WorkflowInstanceRecordJsonV1.encodeC(changed))))))
          } finally _take(first.closeC())
          When("a new physical provider selects retained issued input without scanning suspension history")
          val reopened = new ObservedStore(path)
          try {
            val adapter = _issued(reopened, WorkflowIssuedContinuationKind.WorkOrder)
            reopened.reset()
            val selected = _take(adapter.loadC(_identity))
            val observed = reopened.observations
            val claim = _take(_claims(reopened).loadC(_identity)).get
            Then("one row and actual instance resolve original meaning without new publication or claim")
            selected shouldBe original
            observed shouldBe ((1, 1, 1, 0))
            claim.status shouldBe ContinuationRuntimePersistence.Status.Completed
          } finally _take(reopened.closeC())
        }
      }
    }
    "owning native completion conditions" which {
      "E21 evaluate native WorkOrder requirements without comparing their serialized copy" must
        afterWord("in spec:workflow-completion-applicability, example:E21, rules:R10, phase:5") {
        "retain actual facts, evidence, execution disposition and one-shot claims after physical reopen" in {
          Given("docs/spec/workflow-completion-applicability.md R10 E21: a WorkOrder copy has obsolete result conditions, not execution authority")
          When("physical SQLite reopens the actual native requirements and original issued execution disposition")
          _complete_native_conditions(WorkflowIssuedContinuationKind.WorkOrder)
        }
      }
      "E22 evaluate native Decision requirements without a contract-copy certificate" must
        afterWord("in spec:workflow-completion-applicability, example:E22, rules:R10, phase:5") {
        "retain structural answer conditions and claims without claiming Human consent" in {
          Given("docs/spec/workflow-completion-applicability.md R10 E22: a structural Decision fixture, not actual Human consent")
          When("physical SQLite reopens the actual native answer requirements and obsolete serialized copy")
          _complete_native_conditions(WorkflowIssuedContinuationKind.Decision)
        }
      }
    }
  }
}
