package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/** Actual claimed conditions select execution without a saved-copy certificate.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowClaimedSourceConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _component = ComponentId("org.goldenport.cncf.test.ClaimedSource")
  private val _identity = org.goldenport.cncf.workflow.ContinuationIdentity("claimed-source")
  private val _handle = WorkflowHandle(_component, WorkflowDefinitionIdentity("review.workflow"),
    WorkflowDefinitionRevision("workflow-v1"), InstanceIdentity("review-instance"))
  private val _fact = ContextReference("actual.fact", "1")
  private val _evidence = ContextReference("actual.evidence", "1")
  private val _obsolete = ContextReference("obsolete.copy", "old")
  private val _collection = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "claimed_source"))
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("claimed-source-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("claimed-source-store")))
  private val _native = Continuation(StateMachineRunIdentity("review-run"), _identity, StateMachineRevision("machine-v1"),
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"), "ReviewChange",
      StateMachineOperationIdentity("review", "change"), Some(StateMachineInputTypeReference("review.input.v1")),
      Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector(_fact)), EvidenceContract("review.evidence", Vector(_evidence)), Vector.empty)),
    ContextBundle("Actual native diagnostics", Vector.empty, Vector.empty, ContextSnapshot("workflow-v1", Some("model-v1"))))
  private val _requirement = ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
    ReasoningLevel.Deep, true, Some(ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional, "parent")))
  private val _request = ContinuationRequest(_native.runId, _identity, _native.expectedRevision,
    _native.requiredOperation.identity, _native.requiredOperation.operation, Some(TypedValue("review.input.v1", "Original saved input")),
    _native.requiredOperation.resultType,
    _native.context.copy(summary = "Original saved diagnostics", snapshot = _native.context.snapshot.copy(workspaceRevision = Some("old-workspace"))),
    CompletionContract("review.completion", Vector(_obsolete)), EvidenceContract("review.evidence", Vector(_obsolete)),
    StateMachineOperationIdentity("workflow", "completeReview"))
  private val _order = WorkflowContinuation.WorkOrder(_request, _requirement, MinimalPresentation("Original title", "Original situation"))
  private val _issued = WorkflowInteraction[String, Nothing](_handle, _order)
  private val _result = ContinuationResult(_handle, _native.runId, _identity, _native.expectedRevision, _native.context.snapshot,
    TypedValue("review.result.v1", "Actual result"), ContextReference("review.result", "1"), Vector(_fact),
    ExecutionEvidence(Vector(_evidence), execution = Some(ExecutionObservation(ExecutionPlacement.Inline, "parent", "parent", "fixture-profile"))))
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("Input must be text")
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected rejection: $other")
  }
  private def _directory(context: String): Path = {
    val root = Path.of("target", "workflow", "work", "claimed-source", context)
    Files.createDirectories(root)
    Files.createTempDirectory(root, "case-")
  }
  private def _claims(store: DataStore): WorkflowContinuationDataStorePersistence =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _collection))
  private def _seed(path: String): Unit = {
    val store = SqlDataStore.sqlite(path)
    try {
      val persistence = _claims(store)
      _take(persistence.createC(ContinuationRuntimePersistence.Record(_native, ContinuationRuntimePersistence.Status.Available)))
      _take(new PersistentContinuationRuntime(persistence).claimC(_identity))
    } finally _take(store.closeC())
  }
  private def _saved(issued: WorkflowInteraction[String, Nothing]): IssuedWorkOrderPersistence[String] = {
    val persistence = new IssuedWorkOrderPersistence.InMemory[String]
    _take(persistence.putIfAbsentC(issued))
    persistence
  }
  private final class ObservingAdapter extends ContinuationSpiAdapter[String, ContinuationResult[String], String] {
    var observed = Vector.empty[WorkflowInteraction[String, Nothing]]
    def admitC(issued: WorkflowInteraction[String, Nothing], submission: ContinuationResult[String]): Consequence[ContinuationResult[String]] = {
      observed = observed :+ issued
      Consequence.success(submission)
    }
  }
  private def _metadata(example: String, rule: String = "R11") = afterWord(s"in spec:workflow-completion-applicability, example:$example, rules:$rule, phase:5")

  "Claimed source consumption" should {
    "external adapter input" which {
      "E23 separate Component input access from result applicability" must _metadata("E23", "R11 R12") {
        "preserve original issued meaning while completion consumes native conditions" in {
          Given("docs/spec/workflow-completion-applicability.md R11 R12 E23: original issue annotations differ from native completion conditions")
          When("a unique SQLite provider persists the native claim, closes and physically reopens")
          val path = _directory("adapter").resolve("workflow.sqlite").toString
          _seed(path)
          val store = SqlDataStore.sqlite(path)
          try {
            val runtime = new PersistentContinuationRuntime(_claims(store))
            val adapter = new ObservingAdapter
            val bound = _take(ContinuationSpiAdapter.bindC(_component, runtime, _saved(_issued), adapter))
            val variants = Vector(_result.copy(completionFacts = Vector.empty),
              _result.copy(evidence = _result.evidence.copy(references = Vector.empty)),
              _result.copy(evidence = _result.evidence.copy(execution = None)))
            When("Component-scoped translation receives results missing native facts, evidence or execution")
            val converted = variants.map(bound.admitC(_identity, _))
            val observed = adapter.observed
            var opened = 0
            val rejected = variants.map(resumeIssuedWorkOrderC(_issued, _, runtime,
              () => { opened += 1; new UnitOfWork(ExecutionContext.create()) }))
            val durable = _take(_claims(store).loadC(_identity)).get
            val openings = opened
            Then("translation preserves original input and returns data; native completion refuses missing conditions before a transaction")
            converted shouldBe variants.map(Consequence.success(_))
            observed shouldBe Vector.fill(variants.size)(_issued)
            rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
            durable.status shouldBe ContinuationRuntimePersistence.Status.Claimed
            openings shouldBe 0
            When("another Operation or model scope is supplied to native completion")
            val incompatible = Vector(_request.copy(operation = StateMachineOperationIdentity("review", "other")),
              _request.copy(context = _request.context.copy(snapshot = _request.context.snapshot.copy(modelRevision = Some("foreign-model")))))
            val refused = incompatible.map { request =>
              resumeIssuedWorkOrderC(_issued.copy(current = _order.copy(request = request)), _result, runtime,
                () => { opened += 1; new UnitOfWork(ExecutionContext.create()) })
            }
            val after = opened
            Then("incompatible native target selection creates no transaction and does not consume the claim")
            refused.foreach(_ shouldBe a[Consequence.Failure[?]])
            after shouldBe 0
          } finally _take(store.closeC())
        }
      }
    }
    "one-shot recovered resume" which {
      "E24 admit actual native conditions before opening a fresh UnitOfWork" must _metadata("E24") {
        "preserve a claim on rejection and complete once without a serialized-copy proof" in {
          Given("docs/spec/workflow-completion-applicability.md R11 E24: an actual durable claim with obsolete issued condition vectors")
          When("SQLite stores the native claim and a fresh physical provider recovers it")
          val path = _directory("resume").resolve("workflow.sqlite").toString
          _seed(path)
          val store = SqlDataStore.sqlite(path)
          try {
            val runtime = new PersistentContinuationRuntime(_claims(store))
            var opened = 0
            val fresh = () => { opened += 1; new UnitOfWork(ExecutionContext.create()) }
            When("results omit actual facts, evidence, execution or target the wrong revision")
            val negatives = Vector(_result.copy(completionFacts = Vector.empty),
              _result.copy(evidence = _result.evidence.copy(references = Vector.empty)),
              _result.copy(evidence = _result.evidence.copy(execution = None)),
              _result.copy(expectedRevision = StateMachineRevision("foreign")),
              _result.copy(result = TypedValue("foreign.result", "Actual result")),
              _result.copy(contextSnapshot = _result.contextSnapshot.copy(modelRevision = Some("foreign"))))
              .map(resumeIssuedWorkOrderC(_issued, _, runtime, fresh))
            val before = _take(_claims(store).loadC(_identity)).get
            val openings = opened
            Then("failed actual result admission leaves the existing claim and opens no fresh transaction")
            negatives.foreach(_.toOption shouldBe None)
            before.status shouldBe ContinuationRuntimePersistence.Status.Claimed
            openings shouldBe 0
            When("the actual native requirements are met and a second resume is attempted")
            val accepted = resumeIssuedWorkOrderC(_issued, _result, runtime, fresh)
            val replay = resumeIssuedWorkOrderC(_issued, _result, runtime, fresh)
            val durable = _take(_claims(store).loadC(_identity)).get
            val after = opened
            Then("one fresh UnitOfWork completes the native claim and replay cannot open another")
            accepted shouldBe Consequence.success(ContinuationRuntime.Resume(_native,
              StateMachineOperationResult(StateMachineResultTypeReference("review.result.v1"), ContextReference("review.result", "1"))))
            replay.toOption shouldBe None
            durable.status shouldBe ContinuationRuntimePersistence.Status.Completed
            after shouldBe 1
          } finally _take(store.closeC())
        }
      }
    }
    "original issue retry" which {
      "E25 select original saved meaning instead of comparing condition copies" must _metadata("E25") {
        "retain one issue in memory and physically reopened JSON while rejecting actual incompatible targets" in {
          Given("docs/spec/workflow-completion-applicability.md R11 E25: one original issue and a compatible retry with different condition, input and presentation copies")
          When("a unique target-owned directory stores the issue and the local JSON adapter physically reopens")
          val directory = _directory("retry")
          val first = new IssuedWorkOrderPersistence.LocalJson[String](directory, _codec)
          val memory = _saved(_issued)
          _take(first.putIfAbsentC(_issued))
          val local = new IssuedWorkOrderPersistence.LocalJson[String](directory, _codec)
          val candidate = _issued.copy(current = _order.copy(request = _request.copy(
            input = Some(TypedValue("review.input.v1", "Retry input is not saved authority")),
            completion = _native.requiredOperation.metadata.completionContract,
            evidence = _native.requiredOperation.metadata.evidenceContract),
            presentation = MinimalPresentation("Retry title", "Retry situation")))
          When("both original stores receive the compatible retry and actual incompatible contract, route and input type variants")
          val accepted = Vector(memory, local).map(_.putIfAbsentC(candidate))
          val variants = Vector(_request.copy(completion = CompletionContract("other.completion", Vector(_fact))),
            _request.copy(evidence = EvidenceContract("other.evidence", Vector(_evidence))),
            _request.copy(operation = StateMachineOperationIdentity("review", "other")),
            _request.copy(input = Some(TypedValue("foreign.input", "payload"))))
          val rejected = Vector(memory, local).flatMap(persistence => variants.map(request =>
            persistence.putIfAbsentC(_issued.copy(current = _order.copy(request = request)))))
          val retained = _take(local.loadC(_identity)).get
          Then("retry returns original input and obsolete annotations without granting another actual contract or type")
          accepted.foreach(_ shouldBe Consequence.success(_issued))
          rejected.foreach(_.toOption shouldBe None)
          retained shouldBe _issued
        }
      }
    }
    "translation and input access" which {
      "E26 transport diagnostics and actual observations without completion admission" must _metadata("E26", "R12") {
        "preserve direct and bound values through result encoding without manufacturing executor evidence" in {
          Given("docs/spec/workflow-completion-applicability.md R12 E26: translation owns no native claim or completion permit")
          When("a unique SQLite claim physically reopens and Component-scoped and direct normalizers receive completions")
          val path = _directory("normalizer").resolve("workflow.sqlite").toString
          _seed(path)
          val store = SqlDataStore.sqlite(path)
          try {
            val runtime = new PersistentContinuationRuntime(_claims(store))
            val skill = SkillWorkResult(_native.runId, _identity, _native.expectedRevision, _native.context.snapshot,
              _result.result, _result.resultReference, Vector(_fact), Vector(_evidence), Some("host-worker"),
              Some("host-model"), "host-profile", "host-policy", _result.evidence.execution)
            val normalizer = new SkillWorkResultNormalizerV1[String, String]
            val bound = _take(ContinuationSpiAdapter.bindC(_component, runtime, _saved(_issued), normalizer))
            val codec = new WorkflowResultJsonV1.PayloadCodec[String] {
              val typeIdentity = "review.result.v1"
              def encode(value: String): Json = Json.fromString(value)
              def decode(value: Json): Either[String, String] = value.asString.toRight("Result must be text")
            }
            val extra = ContextReference("", "")
            val variants = Vector(skill.copy(selectedWorkerProfile = ""), skill.copy(mappingPolicyVersion = ""),
              skill.copy(workerIdentity = Some("")), skill.copy(modelIdentity = Some(" ")),
              skill.copy(completionFacts = Vector(_fact, _fact, extra, extra),
                evidenceReferences = Vector(_evidence, _evidence, extra, extra)),
              skill.copy(contextSnapshot = skill.contextSnapshot.copy(workspaceRevision = Some(""), evidenceRevision = Some(""))),
              skill.copy(expectedRevision = StateMachineRevision("foreign")), skill.copy(result = TypedValue("foreign.result", "body")),
              skill.copy(completionFacts = Vector.empty), skill.copy(evidenceReferences = Vector.empty),
              skill.copy(execution = None))
            When("normalization and the supplied codec transport diagnostics, native mismatches and absent actual observations")
            val converted = variants.map { value =>
              val direct = _take(normalizer.admitC(_issued, value))
              val scoped = bound.admitC(_identity, value)
              val wire = _take(WorkflowResultJsonV1.encodeC(direct, codec))
              val restored = WorkflowResultJsonV1.decodeC(wire, codec)
              (value, direct, scoped, restored)
            }
            val malformed = Vector(skill.copy(workerIdentity = null), skill.copy(result = null),
              skill.copy(completionFacts = null), skill.copy(evidenceReferences = null)).map { value =>
              normalizer.admitC(_issued, value).flatMap(WorkflowResultJsonV1.encodeC(_, codec))
            }
            val durable = _take(_claims(store).loadC(_identity)).get
            Then("representable data is preserved, malformed representation is a typed failure, and no profile becomes an execution receipt")
            converted.foreach { case (submitted, direct, scoped, restored) =>
              direct.runId shouldBe submitted.runId
              direct.expectedRevision shouldBe submitted.expectedRevision
              direct.result shouldBe submitted.result
              direct.completionFacts shouldBe submitted.completionFacts
              direct.evidence.references shouldBe submitted.evidenceReferences
              direct.evidence.workerIdentity shouldBe submitted.workerIdentity
              direct.evidence.modelIdentity shouldBe submitted.modelIdentity
              direct.evidence.skillDispatch shouldBe Some(SkillDispatchEvidence(_requirement,
                submitted.selectedWorkerProfile, submitted.mappingPolicyVersion))
              direct.evidence.execution shouldBe submitted.execution
              scoped shouldBe Consequence.success(direct)
              restored shouldBe Consequence.success(direct)
            }
            malformed.foreach(_ shouldBe a[Consequence.Failure[?]])
            durable.status shouldBe ContinuationRuntimePersistence.Status.Claimed
          } finally _take(store.closeC())
        }
      }
      "E27 keep Component input scope without making conversion a runtime gate" must _metadata("E27", "R12") {
        "withhold foreign input and permit translation before and after native completion" in {
          Given("docs/spec/workflow-completion-applicability.md R12 E27: Component-bound input access is not claim ownership or result admission")
          When("a unique SQLite claim physically reopens and a Component reader binds without a live runtime dependency")
          val path = _directory("normalization-boundary").resolve("workflow.sqlite").toString
          _seed(path)
          val store = SqlDataStore.sqlite(path)
          try {
            val runtime = new PersistentContinuationRuntime(_claims(store))
            val adapter = new ObservingAdapter
            val bound = _take(ContinuationSpiAdapter.bindC(_component, ContinuationRuntime.empty, _saved(_issued), adapter))
            val foreign = _issued.copy(handle = _handle.copy(componentIdentity = ComponentId("org.goldenport.cncf.test.Foreign")))
            val foreignadapter = new ObservingAdapter
            val foreignbound = _take(ContinuationSpiAdapter.bindC(_component, ContinuationRuntime.empty, _saved(foreign), foreignadapter))
            When("readers translate an own-Component input and attempt to disclose a foreign-Component input")
            val translated = bound.admitC(_identity, _result)
            val refused = foreignbound.admitC(_identity, _result)
            val disclosed = foreignadapter.observed
            Then("own input is translated and the foreign input never reaches the application callback")
            translated shouldBe Consequence.success(_result)
            refused shouldBe a[Consequence.Failure[?]]
            disclosed shouldBe empty
            When("native completion consumes its claim and the same reader translates retained data afterward")
            var opened = 0
            val completed = resumeIssuedWorkOrderC(_issued, _result, runtime,
              () => { opened += 1; new UnitOfWork(ExecutionContext.create()) })
            val retained = bound.admitC(_identity, _result)
            val replay = resumeIssuedWorkOrderC(_issued, _result, runtime,
              () => { opened += 1; new UnitOfWork(ExecutionContext.create()) })
            val durable = _take(_claims(store).loadC(_identity)).get
            val observed = adapter.observed
            val openings = opened
            Then("data conversion remains available while native replay cannot create another transaction")
            completed.isSuccess shouldBe true
            retained shouldBe Consequence.success(_result)
            replay shouldBe a[Consequence.Failure[?]]
            observed shouldBe Vector(_issued, _issued)
            durable.status shouldBe ContinuationRuntimePersistence.Status.Completed
            openings shouldBe 1
          } finally _take(store.closeC())
        }
      }
    }
  }
}
