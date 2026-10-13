package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.sqlite.SQLiteDataSource

/** Consume actual selected Instance facts without a write-plan certificate.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowInstanceCommittedReadbackSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "actual_instance"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "actual_continuation"))
  private val _owner = _take(DataStoreComponentOwner.create("org.goldenport.cncf.test.InstanceReadback"))
  private val _identity = ContinuationIdentity("actual-continuation")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("actual-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("actual-store")))

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
    InstanceRecord(InstanceIdentity("actual-instance"), _binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("review-correlation"), CausationReference("  review-start  "),
      None, None, None, Vector.empty)
  private def _entry(current: InstanceRecord): HistoryEntry =
    HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation,
      None, None, None)
  private def _intent(current: InstanceRecord): WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"), None,
      Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("actual-run"),
      _identity, StateMachineRevision("machine-v1"), required,
      ContextBundle("  Review context  ", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = _entry(current).copy(suspension = Some(SuspensionBoundary(
      InstanceContinuationIdentity(_identity.value), InstanceRevision(1L),
      ContextSnapshotReference(current.definition.workflowRevision.value), CompletionReference("review.completion"),
      EvidenceReference("review.evidence"), CorrelationReference("  review-resume  "))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _config, current, initialRevision, entry, continuation)
  }
  private def _root(store: DataStore): WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_binding), _instances))
  private def _claims(store: DataStore): WorkflowContinuationDataStorePersistence =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _continuations))
  private def _source(path: String): SQLiteDataSource = {
    val source = new SQLiteDataSource()
    source.setUrl(s"jdbc:sqlite:$path")
    source
  }
  private def _path(context: String): String = {
    val directory = Path.of("target", "workflow", "work", "instance-readback", context)
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "case-").resolve("workflow.sqlite").toString
  }
  private def _replace(changes: Record, field: String, bytes: String): Record =
    Record(changes.fields.filterNot(_.key == field)) ++
      Record.dataAuto(field -> WorkflowInstanceDataStorePersistence.storageText(bytes))
  private def _decode_instance(changes: Record): InstanceRecord = {
    val raw = _take(WorkflowInstanceDataStorePersistence.storageJsonC(
      changes.getString(WorkflowInstanceDataStorePersistence.recordField).get))
    _take(WorkflowInstanceRecordJsonV1.decodeC(raw, _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_binding)))))
  }
  private def _reasons[A](result: Consequence[A]): Set[String] = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
    }.toSet
    case _ => Set.empty
  }

  "Actual Instance readback" should {
    "append CAS" which {
      "E14 consume provider-normalized history instead of the planned entry" must
        afterWord("in spec:workflow-completion-applicability, example:E14, rules:R8, phase:5") {
        "retain the actual causation after physical reopen and refuse the stale writer" in {
          Given("docs/spec/workflow-completion-applicability.md R8 E14: a SQLite CAS provider normalizes causation at its write boundary")
          val path = _path("append")
          val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
            override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ExecutionContext):
                Consequence[EntityMutationProviderResult] = {
              val record = _decode_instance(plan.changes)
              val entry = record.history.last.copy(causation = CausationReference(record.causation.value.trim))
              val actual = record.copy(causation = entry.causation, history = record.history.updated(record.history.size - 1, entry))
              super.compareAndSetEntity(plan.copy(changes = _replace(plan.changes,
                WorkflowInstanceDataStorePersistence.recordField, _take(WorkflowInstanceRecordJsonV1.encodeC(actual)))))
            }
          }
          val current = _initial
          val entry = _entry(current)
          try {
            val writer = _root(store)
            _take(writer.create(_config, current))
            When("append consumes the authoritative committed record")
            val appended = _take(writer.append(_config, current.identity, initialRevision, entry))
            val retained = _take(writer.load(_config, current.identity)).get
            Then("the actual normalized history is returned, not rejected or replaced by the write plan")
            appended.causation.value shouldBe "review-start"
            appended.history.last.causation shouldBe appended.causation
            appended.revision shouldBe InstanceRevision(1L)
            appended shouldBe retained
          } finally _take(store.closeC())
          When("a new physical provider reads the file and receives the old expected revision")
          val reopened = SqlDataStore.sqlite(path)
          try {
            val writer = _root(reopened)
            val loaded = _take(writer.load(_config, current.identity)).get
            val stale = writer.append(_config, current.identity, initialRevision, entry)
            val after = _take(writer.load(_config, current.identity)).get
            Then("the actual causation is durable and stale replay adds no history")
            loaded.causation.value shouldBe "review-start"
            stale.toOption shouldBe None
            after.history shouldBe loaded.history
            after.history.size shouldBe 1
          } finally _take(reopened.closeC())
        }
      }
    }
    "joint suspension" which {
      "E15 consume the actual pair without certifying planned suspension facts" must
        afterWord("in spec:workflow-completion-applicability, example:E15, rules:R8, phase:5") {
        "reopen normalized resume correlation and Context without duplicate publication" in {
          Given("docs/spec/workflow-completion-applicability.md R8 E15: a SQLite joint provider normalizes both representations within its guarded write")
          val path = _path("suspension")
          val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
            override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
                Consequence[DataStoreConditionalTransitionResult] = plan.successor match {
              case DataStoreConditionalSuccessor.Create(owner, collection, identity, revisionfield, stored) =>
                val record = _decode_instance(plan.root.changes)
                val boundary = record.suspension.get
                val normalized = boundary.copy(resumeCorrelation = CorrelationReference(boundary.resumeCorrelation.value.trim))
                val actual = record.copy(suspension = Some(normalized), history = record.history.updated(record.history.size - 1,
                  record.history.last.copy(suspension = Some(normalized))))
                val root = plan.root
                val changed = _take(DataStoreConditionalRoot.create(root.componentOwner, root.collection, root.entryId,
                  root.revisionField, Some(root.expectedRevision), root.expectedFields, _replace(root.changes,
                    WorkflowInstanceDataStorePersistence.recordField, _take(WorkflowInstanceRecordJsonV1.encodeC(actual))), root.nextRevision))
                val field = WorkflowContinuationDataStorePersistence.payloadField
                val raw = _take(WorkflowInstanceDataStorePersistence.storageJsonC(stored.getString(field).get))
                val continuation = _take(ContinuationRecordJsonV1.decodeC(raw))
                val c = continuation.continuation
                val saved = continuation.copy(continuation = c.copy(context = c.context.copy(summary = c.context.summary.trim)))
                val successor = DataStoreConditionalSuccessor.Create(owner, collection, identity, revisionfield,
                  _replace(stored, field, _take(ContinuationRecordJsonV1.encodeC(saved))))
                super.conditionalTransition(_take(DataStoreConditionalTransitionPlan.create(changed, successor, plan.sideEffects, plan.correlation)))
              case _ => super.conditionalTransition(plan)
            }
          }
          val current = _initial
          val request = _intent(current)
          try {
            val writer = _root(store)
            _take(writer.create(_config, current))
            When("one guarded compound commit returns the actual selected pair")
            val paired = _take(writer.suspendC(request, _owner, _continuations))
            val loaded = _take(writer.load(_config, current.identity)).get
            val continuation = _take(_claims(store).loadC(_identity)).get
            Then("actual committed correlation and Context are consumed without a second plan proof")
            paired.instance.suspension.get.resumeCorrelation.value shouldBe "review-resume"
            paired.continuation.continuation.context.summary shouldBe "Review context"
            paired.instance shouldBe loaded
            paired.continuation shouldBe continuation
          } finally _take(store.closeC())
          When("a new physical provider selects the retained pair and receives a stale suspension")
          val reopened = SqlDataStore.sqlite(path)
          try {
            val writer = _root(reopened)
            val loaded = _take(writer.load(_config, current.identity)).get
            val continuation = _take(_claims(reopened).loadC(_identity)).get
            val replay = writer.suspendC(request, _owner, _continuations)
            val after = _take(writer.load(_config, current.identity)).get
            Then("both normalizations are durable and consumed CAS state prevents another publication")
            loaded.suspension.get.resumeCorrelation.value shouldBe "review-resume"
            continuation.continuation.context.summary shouldBe "Review context"
            replay.toOption shouldBe None
            after.history shouldBe loaded.history
            after.history.size shouldBe 1
          } finally _take(reopened.closeC())
        }
      }
      "E16 refuse unreadable or foreign selected readback without retrying a committed pair" must
        afterWord("in spec:workflow-completion-applicability, example:E16, rules:R8, phase:5") {
        "retain indeterminate outcome and recover actual committed records" in {
          Given("docs/spec/workflow-completion-applicability.md R8 E16: acknowledged SQLite commits with malformed or foreign readback")
          Vector("missing-root", "missing-successor", "foreign-root", "foreign-successor", "root-revision", "successor-revision").foreach { variant =>
            val path = _path("indeterminate")
            var commits = 0
            val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
              override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
                  Consequence[DataStoreConditionalTransitionResult] = {
                commits += 1
                super.conditionalTransition(plan).map {
                  case DataStoreConditionalTransitionResult.Transitioned(root, successor) =>
                    val rootfield = WorkflowInstanceDataStorePersistence.recordField
                    val cfield = WorkflowContinuationDataStorePersistence.payloadField
                    val changedroot = variant match {
                      case "missing-root" => Record.dataAuto("broken" -> "root")
                      case "foreign-root" => _replace(root, rootfield, _take(WorkflowInstanceRecordJsonV1.encodeC(
                        _decode_instance(root).copy(identity = InstanceIdentity("foreign-instance")))))
                      case "root-revision" => Record(root.fields.filterNot(_.key == WorkflowInstanceDataStorePersistence.revisionField)) ++
                        Record.dataAuto(WorkflowInstanceDataStorePersistence.revisionField -> 99L)
                      case _ => root
                    }
                    val changedsuccessor = variant match {
                      case "missing-successor" => Record.dataAuto("broken" -> "successor")
                      case "foreign-successor" =>
                        val saved = _take(ContinuationRecordJsonV1.decodeC(_take(WorkflowInstanceDataStorePersistence.storageJsonC(
                          successor.getString(cfield).get))))
                        _replace(successor, cfield, _take(ContinuationRecordJsonV1.encodeC(saved.copy(
                          continuation = saved.continuation.copy(continuationId = ContinuationIdentity("foreign-continuation"))))))
                      case "successor-revision" => Record(successor.fields.filterNot(_.key == WorkflowContinuationDataStorePersistence.revisionField)) ++
                        Record.dataAuto(WorkflowContinuationDataStorePersistence.revisionField -> 0L)
                      case _ => successor
                    }
                    DataStoreConditionalTransitionResult.Transitioned(changedroot, changedsuccessor)
                  case other => other
                }
              }
            }
            val current = _initial
            val request = _intent(current)
            try {
              val writer = _root(store)
              _take(writer.create(_config, current))
              When(s"the committed pair has $variant in its authoritative readback")
              val result = writer.suspendC(request, _owner, _continuations)
              val reasons = _reasons(result)
              val observedcommits = commits
              Then("no success or automatic replay is inferred from a representation that cannot be consumed")
              result.toOption shouldBe None
              reasons should contain ("transaction-indeterminate")
              observedcommits shouldBe 1
            } finally _take(store.closeC())
            When("a fresh provider reads actual durable facts and receives the old suspension")
            val reopened = SqlDataStore.sqlite(path)
            try {
              val writer = _root(reopened)
              val loaded = _take(writer.load(_config, current.identity)).get
              val continuation = _take(_claims(reopened).loadC(_identity)).get
              val replay = writer.suspendC(request, _owner, _continuations)
              val after = _take(writer.load(_config, current.identity)).get
              Then("actual native targets survive once and stale state grants no replacement publication")
              loaded.history shouldBe Vector(request.entry)
              continuation.continuation.continuationId shouldBe _identity
              replay.toOption shouldBe None
              after.history shouldBe loaded.history
            } finally _take(reopened.closeC())
          }
        }
      }
    }
  }
}
