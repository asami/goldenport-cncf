package org.goldenport.cncf.workflow

import java.nio.file.Files
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

/** Actual committed representations, not a second certificate of the write plan.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowCommittedReadbackSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "readback_instances"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "readback_continuations"))
  private val _component = ComponentId("org.goldenport.cncf.test.CommittedReadback")
  private val _owner = _take(DataStoreComponentOwner.create(_component.name))
  private val _operation = StateMachineOperationIdentity("workflow", "completeReview")
  private val _identity = ContinuationIdentity("readback-continuation")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("readback-store")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("readback-store")))
  private val _input_codec = _codec("review.input.v1")
  private val _result_codec = _codec("review.result.v1")

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected rejection: $other")
  }
  private def _codec(identity: String): PayloadCodec[String] = new PayloadCodec[String] {
    val typeIdentity = identity
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("Review value must be text")
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
  private def _continuation: ContinuationRuntimePersistence.Record = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(_input_codec.typeIdentity)), Some(StateMachineResultTypeReference(_result_codec.typeIdentity)),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("review-run"), _identity,
      StateMachineRevision("machine-v1"), required,
      ContextBundle("  Review context  ", Vector.empty, Vector.empty, ContextSnapshot(_binding.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
  }
  private def _claims(store: DataStore): WorkflowContinuationDataStorePersistence =
    _take(WorkflowContinuationDataStorePersistence.bindC(store, _config, _continuations))
  private def _root(store: DataStore): WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_binding), _instances))
  private def _completion(store: DataStore, inputcodec: PayloadCodec[String] = _input_codec): WorkflowCompletionDataStorePersistence[String, String] =
    _take(WorkflowCompletionDataStorePersistence.bindC(store, _config, _component, _operation,
      Vector(_binding), _instances, _continuations, inputcodec, _result_codec))
  private def _seed(store: DataStore): WorkflowCompletionDataStorePersistence.Intent[String] = {
    val current = InstanceRecord(InstanceIdentity("readback-instance"), _binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("review-correlation"), CausationReference("review-start"), None, None, None, Vector.empty)
    val continuation = _continuation
    val c = continuation.continuation
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(_identity.value), InstanceRevision(1L),
        ContextSnapshotReference(c.context.snapshot.workflowRevision), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("review-resume"))))
    val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      _config, current, initialRevision, entry, continuation)
    val request = ContinuationRequest(c.runId, c.continuationId, c.expectedRevision, c.requiredOperation.identity,
      c.requiredOperation.operation, Some(TypedValue(_input_codec.typeIdentity, "review input")),
      c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
      c.requiredOperation.metadata.evidenceContract, _operation)
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"), ReasoningLevel.Deep, true)
    val work = WorkflowInteraction(_take(WorkflowHandle.fromRecordC(_component, current)),
      WorkflowContinuation.WorkOrder(request, requirement, MinimalPresentation("Review", "Awaiting result")))
    _take(_root(store).create(_config, current))
    val issued = _take(WorkflowIssuedWorkDataStorePersistence.bindC(store, _config, _component, _operation,
      Vector(_binding), _instances, _continuations, _input_codec))
    val suspended = _take(issued.suspendC(intent, work, _owner)).instance
    val token = _take(_claims(store).claimC(_identity)).claimId.get
    val result = ContinuationResult(work.handle, c.runId, c.continuationId, c.expectedRevision, c.context.snapshot,
      TypedValue(_result_codec.typeIdentity, "review result"), ContextReference("  result-reference  ", "1"),
      Vector.empty, ExecutionEvidence(Vector.empty, Some("worker"), Some("model"),
        Some(SkillDispatchEvidence(requirement, "review", "mapping-v1"))))
    val completed = entry.copy(sequence = HistorySequence(2L), revision = InstanceRevision(2L),
      lifecycle = Lifecycle.Completed, currentProgression = None, suspension = None)
    WorkflowCompletionDataStorePersistence.Intent(suspended, suspended.revision, completed, token, result)
  }
  private def _source(path: String): SQLiteDataSource = {
    val source = new SQLiteDataSource()
    source.setUrl(s"jdbc:sqlite:$path")
    source
  }
  private def _replace(changes: Record, field: String, bytes: String): Record =
    Record(changes.fields.filterNot(_.key == field)) ++
      Record.dataAuto(field -> WorkflowInstanceDataStorePersistence.storageText(bytes))

  "Committed Workflow readback" should {
    "Continuation CAS" which {
      "E10 return the actual normalized Context without certifying the planned body" must
        afterWord("in spec:workflow-completion-applicability, example:E10, rules:R6, phase:5") {
        "retain the same claim and durable normalized meaning after SQLite reopen" in {
          Given("docs/spec/workflow-completion-applicability.md R6 E10: a SQLite provider normalizes diagnostic Context at its atomic write boundary")
          val path = Files.createTempDirectory("cncf-readback-claim-").resolve("workflow.sqlite").toString
          val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
            override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ExecutionContext):
                Consequence[EntityMutationProviderResult] = {
              val field = WorkflowContinuationDataStorePersistence.payloadField
              val raw = _take(WorkflowInstanceDataStorePersistence.storageJsonC(plan.changes.getString(field).get))
              val record = _take(ContinuationRecordJsonV1.decodeC(raw))
              val c = record.continuation
              val normalized = record.copy(continuation = c.copy(context = c.context.copy(summary = c.context.summary.trim)))
              super.compareAndSetEntity(plan.copy(changes = _replace(plan.changes, field, _take(ContinuationRecordJsonV1.encodeC(normalized)))))
            }
          }
          var token = ""
          try {
            _take(_claims(store).createC(_continuation))
            When("the actual CAS provider claims the Continuation and returns its committed representation")
            val claimed = _take(_claims(store).claimC(_identity))
            token = claimed.claimId.get
            Then("the actual committed Context, not the unnormalized write plan, is returned")
            claimed.continuation.context.summary shouldBe "Review context"
            claimed.status shouldBe ContinuationRuntimePersistence.Status.Claimed
          } finally _take(store.closeC())
          When("the physical file reopens and receives foreign then actual claim tokens")
          val reopened = SqlDataStore.sqlite(path)
          try {
            val adapter = _claims(reopened)
            val retained = _take(adapter.loadC(_identity)).get
            val foreign = adapter.completeC(_identity, "foreign")
            val completed = _take(adapter.completeC(_identity, token))
            val duplicate = adapter.completeC(_identity, token)
            Then("normalization is durable while actual claim ownership and one-shot status still govern mutation")
            retained.continuation.context.summary shouldBe "Review context"
            retained.claimId shouldBe Some(token)
            foreign.toOption shouldBe None
            completed.status shouldBe ContinuationRuntimePersistence.Status.Completed
            duplicate.toOption shouldBe None
          } finally _take(reopened.closeC())
        }
      }
    }
    "joint completion" which {
      "E11 consume the actual stored result reference without another planned-reference certificate" must
        afterWord("in spec:workflow-completion-applicability, example:E11, rules:R6, phase:5") {
        "return and reopen the provider-normalized reference without replaying completion" in {
          Given("docs/spec/workflow-completion-applicability.md R6 E11: a SQLite provider normalizes a result reference within the same guarded joint write")
          val path = Files.createTempDirectory("cncf-readback-completion-").resolve("workflow.sqlite").toString
          val store = new SqlDataStore(SqliteDialectDriver, _source(path)) {
            override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(using ExecutionContext):
                Consequence[DataStoreConditionalTransitionResult] = plan.successor match {
              case DataStoreConditionalSuccessor.Update(root) =>
                val field = WorkflowCompletionDataStorePersistence.payloadField
                val raw = _take(WorkflowInstanceDataStorePersistence.storageJsonC(root.changes.getString(field).get))
                val record = _take(WorkflowCompletionRecordJsonV1.decodeC(raw, _result_codec))
                val reference = record.result.resultReference
                val normalized = record.copy(result = record.result.copy(resultReference = reference.copy(identity = reference.identity.trim)))
                val changed = _take(DataStoreConditionalRoot.create(root.componentOwner, root.collection, root.entryId,
                  root.revisionField, Some(root.expectedRevision), root.expectedFields,
                  _replace(root.changes, field, _take(WorkflowCompletionRecordJsonV1.encodeC(normalized, _result_codec))), root.nextRevision))
                super.conditionalTransition(_take(DataStoreConditionalTransitionPlan.create(plan.root,
                  DataStoreConditionalSuccessor.Update(changed), plan.sideEffects, plan.correlation)))
              case _ => super.conditionalTransition(plan)
            }
          }
          val request = _seed(store)
          try {
            When("joint completion commits both native records and consumes their actual readback")
            val completed = _take(_completion(store).completeC(request, _owner))
            Then("the returned result reference is the actual provider-owned stored value")
            completed.retained.result.resultReference.identity shouldBe "result-reference"
            completed.instance.lifecycle shouldBe Lifecycle.Completed
            completed.continuation.status shouldBe ContinuationRuntimePersistence.Status.Completed
          } finally _take(store.closeC())
          When("a new physical SQLite provider queries the retained result and receives a replay")
          val reopened = SqlDataStore.sqlite(path)
          try {
            val retained = _take(_completion(reopened).loadC(_identity)).get
            val replay = _completion(reopened).completeC(request, _owner)
            val instance = _take(_root(reopened).load(_config, request.current.identity)).get
            Then("query selects committed meaning and actual consumed state refuses another completion")
            retained.result.resultReference.identity shouldBe "result-reference"
            replay.toOption shouldBe None
            instance.history.size shouldBe 2
          } finally _take(reopened.closeC())
        }
      }
    }
    "retained-result query" which {
      "E12 select committed facts without replaying historical AI input or evidence admission" must
        afterWord("in spec:workflow-completion-applicability, example:E12, rules:R7, phase:5") {
        "read each native target once after physical reopen without a history certificate" in {
          Given("docs/spec/workflow-completion-applicability.md R7 E12: an actual joint completion with obsolete historical input and evidence conditions")
          val path = Files.createTempDirectory("cncf-readback-query-").resolve("workflow.sqlite").toString
          val store = SqlDataStore.sqlite(path)
          val request = _seed(store)
          try {
            _take(_completion(store).completeC(request, _owner))
            val key = WorkflowContinuationDataStorePersistence.id(_identity)
            val row = _take(store.load(_continuations, key)).get
            val issue = io.circe.parser.parse(_take(WorkflowInstanceDataStorePersistence.storageJsonC(
              row.getString(WorkflowIssuedWorkDataStorePersistence.payloadField).get))).toOption.get
            val obsolete = issue.mapObject(_.add("requirement", Json.Null).add("presentation", Json.Null))
              .hcursor.downField("request").downField("input").downField("value")
              .withFocus(_ => Json.obj("obsolete" -> Json.fromBoolean(true))).top.get
            val saved = _take(_claims(store).loadC(_identity)).get
            val required = saved.continuation.requiredOperation
            val changed = saved.copy(continuation = saved.continuation.copy(requiredOperation = required.copy(
              metadata = required.metadata.copy(evidenceContract = required.metadata.evidenceContract.copy(
                requiredEvidence = Vector(ContextReference("new-evidence-condition", "1")))))))
            _take(store.update(_continuations, key, Record.dataAuto(
              WorkflowIssuedWorkDataStorePersistence.payloadField -> WorkflowInstanceDataStorePersistence.storageText(obsolete.noSpaces),
              WorkflowContinuationDataStorePersistence.payloadField -> WorkflowInstanceDataStorePersistence.storageText(
                _take(ContinuationRecordJsonV1.encodeC(changed))))))
            val instance = _take(_root(store).load(_config, request.current.identity)).get
            val first = instance.history.head
            val historical = instance.copy(history = instance.history.updated(0, first.copy(suspension = first.suspension.map(
              _.copy(completion = CompletionReference("obsolete-completion-description"))))))
            _take(store.update(_instances, DataStore.StringEntryId(instance.identity.value), Record.dataAuto(
              WorkflowInstanceDataStorePersistence.recordField -> WorkflowInstanceDataStorePersistence.storageText(
                _take(WorkflowInstanceRecordJsonV1.encodeC(historical))))))
          } finally _take(store.closeC())
          var inputreads = 0
          var loads = Vector.empty[DataStore.CollectionId]
          val obsoletecodec = new PayloadCodec[String] {
            val typeIdentity = _input_codec.typeIdentity
            def encode(value: String): Json = throw new IllegalStateException("Query must not encode input")
            def decode(value: Json): Either[String, String] = {
              inputreads += 1
              Left("Historical input is not current executable AI input")
            }
          }
          val reopened = new SqlDataStore(SqliteDialectDriver, _source(path)) {
            override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext):
                Consequence[Option[Record]] = {
              loads :+= collection
              super.load(collection, id)
            }
          }
          try {
            When("read-only Completion selection resolves the stored references and typed result")
            val retained = _take(_completion(reopened, obsoletecodec).loadC(_identity)).get
            val observedinputreads = inputreads
            val continuationloads = loads.count(_ == _continuations)
            val instanceloads = loads.count(_ == _instances)
            val replay = _claims(reopened).completeC(_identity, request.claimId)
            val instance = _take(_root(reopened).load(_config, request.current.identity)).get
            Then("one native row and one referenced instance are read, without input, evidence or history re-admission")
            retained.result shouldBe request.result
            observedinputreads shouldBe 0
            continuationloads shouldBe 1
            instanceloads shouldBe 1
            replay.toOption shouldBe None
            instance.history.size shouldBe 2
          } finally _take(reopened.closeC())
        }
      }
      "E13 reject actual foreign stored references and native result scope" must
        afterWord("in spec:workflow-completion-applicability, example:E13, rules:R7, phase:5") {
        "reject unsupported kind, owner, target, Operation, run, revision, model and type without writes" in {
          Given("docs/spec/workflow-completion-applicability.md R7 E13: completed SQLite rows with independently foreign binding or scope")
          val issuefield = WorkflowIssuedWorkDataStorePersistence.payloadField
          val resultfield = WorkflowCompletionDataStorePersistence.payloadField
          val changes = Vector(
            (issuefield, Vector("schemaVersion"), "unsupported"),
            (issuefield, Vector("kind"), "DECISION"),
            (issuefield, Vector("handle", "componentIdentity"), "org.foreign.Component"),
            (issuefield, Vector("handle", "componentIdentity"), "foreign.Component"),
            (issuefield, Vector("handle", "instanceIdentity"), "foreign-instance"),
            (issuefield, Vector("request", "completionOperation", "operation"), "foreignOperation"),
            (resultfield, Vector("result", "runId"), "foreign-run"),
            (resultfield, Vector("result", "expectedRevision"), "foreign-revision"),
            (resultfield, Vector("result", "contextSnapshot", "modelRevision"), "foreign-model"),
            (resultfield, Vector("result", "result", "typeIdentity"), "foreign-type"))
          changes.foreach { case (field, fields, value) =>
            val path = Files.createTempDirectory("cncf-readback-foreign-").resolve("workflow.sqlite").toString
            val store = SqlDataStore.sqlite(path)
            try {
              val request = _seed(store)
              _take(_completion(store).completeC(request, _owner))
              val key = WorkflowContinuationDataStorePersistence.id(_identity)
              val row = _take(store.load(_continuations, key)).get
              val json = io.circe.parser.parse(_take(WorkflowInstanceDataStorePersistence.storageJsonC(row.getString(field).get))).toOption.get
              val cursor = fields.foldLeft[io.circe.ACursor](json.hcursor)((current, name) => current.downField(name))
              val foreign = cursor.withFocus(_ => Json.fromString(value)).top.get
              _take(store.update(_continuations, key, Record.dataAuto(
                field -> WorkflowInstanceDataStorePersistence.storageText(foreign.noSpaces))))
              val before = _take(store.load(_continuations, key)).get
              When("the query encounters the foreign reference or native result scope")
              val rejected = _completion(store).loadC(_identity)
              val after = _take(store.load(_continuations, key)).get
              val instance = _take(_root(store).load(_config, request.current.identity)).get
              Then("the actual mismatch rejects without repairing rows, executing work or changing state")
              rejected.toOption shouldBe None
              after shouldBe before
              instance.history.size shouldBe 2
            } finally _take(store.closeC())
          }
        }
      }
    }
  }
}
