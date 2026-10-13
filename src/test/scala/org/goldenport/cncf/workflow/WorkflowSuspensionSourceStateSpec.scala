package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.sqlite.SQLiteDataSource

/** Actual source state and CAS, without a caller-copy certificate.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowSuspensionSourceStateSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "source_instance"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "source_continuation"))
  private val _owner = _take(DataStoreComponentOwner.create("org.goldenport.cncf.test.SuspensionSource"))
  private val _identity = ContinuationIdentity("next-continuation")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("source-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("source-store")))

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
  private def _initial: InstanceRecord =
    InstanceRecord(InstanceIdentity("source-instance"), _binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("source-correlation"), CausationReference("source-start"),
      None, None, None, Vector.empty)
  private def _boundary(identity: String, revision: InstanceRevision): SuspensionBoundary =
    SuspensionBoundary(InstanceContinuationIdentity(identity), revision,
      ContextSnapshotReference(_binding.workflowRevision.value), CompletionReference("review.completion"),
      EvidenceReference("review.evidence"), CorrelationReference("source-resume"))
  private def _entry(current: InstanceRecord): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("review.Actual")), current.correlation, current.causation,
      None, None, None)
  private def _intent(current: InstanceRecord): WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"), None,
      Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("source-run"),
      _identity, StateMachineRevision("machine-v1"), required,
      ContextBundle("Review source", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = _entry(current).copy(suspension = Some(_boundary(_identity.value, current.revision.next)))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _config, current, current.revision, entry, continuation)
  }
  private def _root(store: DataStore): WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_binding), _instances))
  private def _claims(store: DataStore): WorkflowContinuationDataStorePersistence =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _continuations))
  private def _seed(writer: WorkflowInstanceDataStorePersistence): InstanceRecord = {
    val initial = _initial
    _take(writer.create(_config, initial))
    _take(writer.append(_config, initial.identity, initialRevision,
      _entry(initial).copy(suspension = Some(_boundary("original-continuation", InstanceRevision(1L))))))
  }
  private def _copy(current: InstanceRecord, variant: String): InstanceRecord = {
    val entry = current.history.last.copy(
      currentProgression = if (variant == "progression" || variant == "both")
        Some(ProgressionReference("review.CallerCopy")) else current.currentProgression,
      suspension = if (variant == "suspension" || variant == "both")
        Some(_boundary("caller-copy-continuation", current.revision)) else current.suspension)
    current.copy(currentProgression = entry.currentProgression, suspension = entry.suspension,
      history = current.history.updated(current.history.size - 1, entry))
  }
  private def _definition(identity: WorkflowDefinitionIdentity, revision: WorkflowDefinitionRevision): DefinitionBinding = {
    val original = _binding
    new DefinitionBinding(identity, revision, original.sourceCorrelation, original.producerAbiIdentity,
      original.workflowAbiIdentity, original.bootstrapAbiIdentity, original.producerRevision,
      original.admittedDefinition, original.admittedCandidateDefinition, original.admittedEntryDefinition)
  }
  private def _path(context: String): String = {
    val directory = Path.of("target", "workflow", "work", "suspension-source", context)
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "case-").resolve("workflow.sqlite").toString
  }
  private def _source(path: String): SQLiteDataSource = {
    val source = new SQLiteDataSource()
    source.setUrl(s"jdbc:sqlite:$path")
    source
  }

  "Actual suspension source" should {
    "caller copies" which {
      "E17 retain actual prior facts instead of certifying the supplied copy" must
        afterWord("in spec:workflow-completion-applicability, example:E17, rules:R9, phase:5") {
        "append and physically reopen actual history for three differing valid copies" in {
          Given("docs/spec/workflow-completion-applicability.md R9 E17: valid caller copies differ in progression, suspension or both")
          When("unique SQLite providers are opened and actual prior source history is seeded")
          Vector("progression", "suspension", "both").foreach { variant =>
            val path = _path("copy")
            val store = SqlDataStore.sqlite(path)
            var expectedhistory = Vector.empty[HistoryEntry]
            try {
              val writer = _root(store)
              val current = _seed(writer)
              val request = _intent(_copy(current, variant))
              When(s"actual SQLite source accepts the adjacent entry with a different $variant copy")
              val paired = _take(writer.suspendC(request, _owner, _continuations))
              val loaded = _take(writer.load(_config, current.identity)).get
              val continuation = _take(_claims(store).loadC(_identity)).get
              expectedhistory = current.history :+ request.entry
              Then("actual original history wins and one new continuation is available")
              paired.instance.history shouldBe expectedhistory
              loaded.history shouldBe expectedhistory
              continuation.status shouldBe ContinuationRuntimePersistence.Status.Available
              continuation.claimId shouldBe None
            } finally _take(store.closeC())
            When("a fresh physical provider reads the committed history")
            val reopened = SqlDataStore.sqlite(path)
            try {
              val loaded = _take(_root(reopened).load(_config, InstanceIdentity("source-instance"))).get
              val continuation = _take(_claims(reopened).loadC(_identity)).get
              Then("the actual prior progression and suspension survive physical reopen")
              loaded.history shouldBe expectedhistory
              loaded.history.head.currentProgression shouldBe Some(ProgressionReference("review.Actual"))
              loaded.history.head.suspension.get.continuationIdentity.value shouldBe "original-continuation"
              continuation.continuation.continuationId shouldBe _identity
            } finally _take(reopened.closeC())
          }
        }
      }
    }
    "actual source admission" which {
      "E18 refuse incompatible targets, stale revision and terminal transitions" must
        afterWord("in spec:workflow-completion-applicability, example:E18, rules:R9, phase:5") {
        "reject five inputs before the compound provider and preserve source state" in {
          Given("docs/spec/workflow-completion-applicability.md R9 E18: well-formed inputs cannot replace actual source state or executable target")
          When("unique SQLite providers are opened and actual source states and pure input copies are prepared")
          Vector("stale", "completed", "workflow-identity", "workflow-revision", "absent").foreach { variant =>
            val path = _path("admission")
            var commits = 0
            val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
              override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
                  Consequence[DataStoreConditionalTransitionResult] = {
                commits += 1
                super.conditionalTransition(plan)
              }
            }
            try {
              val writer = _root(store)
              val first = _seed(writer)
              val actual = if (variant == "stale" || variant == "completed")
                _take(writer.append(_config, first.identity, first.revision, _entry(first).copy(
                  lifecycle = if (variant == "completed") Lifecycle.Completed else Lifecycle.Active,
                  currentProgression = if (variant == "completed") None else first.currentProgression))) else first
              val supplied = variant match {
                case "completed" => _take(first.appendC(first.revision, _entry(first)))
                case "workflow-identity" => first.copy(definition =
                  _definition(WorkflowDefinitionIdentity("foreign-workflow"), first.definition.workflowRevision))
                case "workflow-revision" => first.copy(definition =
                  _definition(first.definition.workflowIdentity, WorkflowDefinitionRevision("foreign-revision")))
                case "absent" => first.copy(identity = InstanceIdentity("absent-instance"))
                case _ => first
              }
              val request = _intent(supplied)
              When(s"the $variant input meets pure intent shape but targets incompatible actual source")
              val admitted = WorkflowInstanceAtomicTransitionV1.admitSuspensionC(request)
              val rejected = writer.suspendC(request, _owner, _continuations)
              val after = _take(writer.load(_config, first.identity)).get
              val successor = _take(_claims(store).loadC(_identity))
              val observedcommits = commits
              Then("native source/target admission rejects without publication or source replacement")
              admitted.toOption.nonEmpty shouldBe true
              rejected.toOption shouldBe None
              after.history shouldBe actual.history
              successor shouldBe None
              observedcommits shouldBe 0
            } finally _take(store.closeC())
          }
        }
      }
      "E19 keep the original expected revision at the commit boundary" must
        afterWord("in spec:workflow-completion-applicability, example:E19, rules:R9, phase:5") {
        "lose a real SQLite CAS race without renewing the expectation or publishing a successor" in {
          Given("docs/spec/workflow-completion-applicability.md R9 E19: a prepared suspension competes with a later committed append")
          When("a unique SQLite provider is opened and actual prior source history is seeded")
          val store = SqlDataStore.sqlite(_path("race"))
          try {
            val writer = _root(store)
            val current = _seed(writer)
            val request = _intent(_copy(current, "both"))
            When("the prepared source transition loses to an actual append")
            val prepared = _take(writer.prepareSuspensionC(request, _owner, _continuations))
            val winner = _take(writer.append(_config, current.identity, current.revision, _entry(current)))
            val lost = prepared.commitC()
            val after = _take(writer.load(_config, current.identity)).get
            val successor = _take(_claims(store).loadC(_identity))
            Then("CAS refuses the stale prepared pair and retains only the winner")
            lost.toOption shouldBe None
            after.history shouldBe winner.history
            after.history.size shouldBe 2
            successor shouldBe None
          } finally _take(store.closeC())
        }
      }
    }
  }
}
