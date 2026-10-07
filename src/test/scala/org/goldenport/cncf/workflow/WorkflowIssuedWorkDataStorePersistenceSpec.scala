package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.sql.Connection
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
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
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
final class WorkflowIssuedWorkDataStorePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "issued_instance_v1"))
  private val continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "issued_continuation_v1"))
  private val component = ComponentId("org.goldenport.cncf.test.IssuedWork")
  private val completionOperation = StateMachineOperationIdentity("workflow", "submitReview")
  private val owner = take(DataStoreComponentOwner.create("org.goldenport.cncf.test.IssuedWork"))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))
  private val codec = new PayloadCodec[String] {
    val typeIdentity = "review.input.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("review input must be text")
  }
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def binding: DefinitionBinding = {
    val producer = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
      resource("/workflow/candidate-admission-workflow-abi.json").trim, producer,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def initial: InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      None, None, None, Vector.empty)
  private def intent(current: InstanceRecord, identity: String = "continuation-1"):
      WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"),
      Some(StateMachineInputTypeReference(codec.typeIdentity)), Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty), Vector.empty))
    val continuation = ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      ContinuationIdentity(identity), StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot(current.definition.workflowRevision.value))),
      ContinuationRuntimePersistence.Status.Available)
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(identity), InstanceRevision(1L),
        ContextSnapshotReference(current.definition.workflowRevision.value), CompletionReference("review.completion"),
        EvidenceReference("review.evidence"), CorrelationReference("resume-1"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      config, current, initialRevision, entry, continuation)
  }
  private def issue(request: WorkflowInstanceAtomicTransitionV1.SuspensionIntent): WorkflowInteraction[String, Nothing] = {
    val c = request.continuation.continuation
    WorkflowInteraction(take(WorkflowHandle.fromRecordC(component, request.current)),
      WorkflowContinuation.WorkOrder(ContinuationRequest(c.runId, c.continuationId, c.expectedRevision,
        c.requiredOperation.identity, c.requiredOperation.operation, Some(TypedValue(codec.typeIdentity, "exact review input")),
        c.requiredOperation.resultType, c.context, c.requiredOperation.metadata.completionContract,
        c.requiredOperation.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement("semantic.review")), RiskLevel("bounded"),
          ReasoningLevel.Deep, true), MinimalPresentation("Review", "Awaiting semantic result")))
  }
  private def root(store: DataStore) = take(WorkflowInstanceDataStorePersistence.bindC(store, config, Vector(binding), instances))
  private def claims(store: DataStore) = take(WorkflowContinuationDataStorePersistence.bindC(store, config, continuations))
  private def issued(store: DataStore, inputCodec: PayloadCodec[String] = codec) =
    take(WorkflowIssuedWorkDataStorePersistence.bindC(store, config, component, completionOperation, Vector(binding), instances, continuations, inputCodec))
  private def path: Path = Files.createTempDirectory("cncf-phase88-issued-").resolve("workflow.sqlite")
  private val identity = ContinuationIdentity("continuation-1")
  private def key(id: String) = DataStore.StringEntryId(id)
  private def sqliteConfig: SQLiteConfig = {
    val result = new SQLiteConfig()
    result.setBusyTimeout(10000)
    result.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    result
  }

  "Phase 88 atomic issued WorkOrder publication" should {
    "prepare an exact issue without publishing claimable work and compose one canonical local effect" in {
      Given("an initial Workflow instance and an exact typed issued WorkOrder")
      val store = DataStore.inMemory()
      val current = initial
      val request = intent(current)
      val work = issue(request)
      take(root(store).create(config, current))
      val before = take(store.load(instances, key(current.identity.value)))
      val effects = DataStore.CollectionId("issued_prepared_records_v1")
      When("the host prepares publication with one explicit local record")
      val prepared = take(issued(store).prepareSuspensionC(request, work, owner))
      val composed = take(prepared.withSideEffectsC(Vector(EntityVersionedSideEffect.Save(
        effects, key("closing"), Record.dataAuto("value" -> "prepared local effect")))))
      Then("the root is unchanged and no Continuation, claimable work or local effect exists")
      take(store.load(instances, key(current.identity.value))) shouldBe before
      take(store.load(continuations, key(identity.value))) shouldBe None
      take(issued(store).loadC(identity)) shouldBe None
      take(store.load(effects, key("closing"))) shouldBe None
      When("the prepared canonical commit succeeds")
      val result = take(composed.commitC())
      Then("history, exact issued bytes and the local record are visible together")
      result.instance.history shouldBe Vector(request.entry)
      take(issued(store).loadC(identity)) shouldBe Some(work)
      take(store.load(effects, key("closing"))).get.getString("value") shouldBe Some("prepared local effect")
      prepared.commitC().toOption shouldBe None
    }

    "reject delayed prepared issue after an immediate winner without publishing its local effect" in {
      Given("two issue attempts against the same stored expected instance revision")
      val store = DataStore.inMemory()
      val current = initial
      val request = intent(current)
      val work = issue(request)
      take(root(store).create(config, current))
      val effects = DataStore.CollectionId("issued_delayed_records_v1")
      val prepared = take(take(issued(store).prepareSuspensionC(request, work, owner)).withSideEffectsC(
        Vector(EntityVersionedSideEffect.Save(effects, key("late"), Record.dataAuto("value" -> "not published")))))
      When("the compatible immediate API commits first")
      take(issued(store).suspendC(request, work, owner))
      val before = take(store.load(instances, key(current.identity.value)))
      val result = prepared.commitC()
      Then("the delayed guard loses without a duplicate issue or side record")
      result.toOption shouldBe None
      take(store.load(instances, key(current.identity.value))) shouldBe before
      take(issued(store).loadC(identity)) shouldBe Some(work)
      take(store.load(effects, key("late"))) shouldBe None
    }

    "publish exact work with suspension and recover it through claim CAS and SQLite reopen" in {
      Given("one exact typed issue and one managed physical datastore")
      val db = path
      val first = SqlDataStore.sqlite(db.toString)
      val current = initial
      val request = intent(current)
      val work = issue(request)
      var token = ""
      var retainedPayload = ""
      try {
        take(root(first).create(config, current))
        When("the canonical compound transition creates the Continuation including issued work")
        take(issued(first).suspendC(request, work, owner)).instance.history shouldBe Vector(request.entry)
        val before = take(first.load(continuations, key(identity.value))).get
        retainedPayload = before.getString(WorkflowIssuedWorkDataStorePersistence.payloadField).get
        retainedPayload shouldBe WorkflowInstanceDataStorePersistence.storageText(
          take(WorkflowWorkOrderJsonV1.encodeC(work, codec)))
        take(issued(first).loadC(identity)) shouldBe Some(work)
        token = take(claims(first).claimC(identity)).claimId.get
        take(issued(first).loadC(identity)) shouldBe Some(work)
      } finally take(first.closeC())
      When("new physical providers and a separately admitted definition recover the same file")
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val recovered = issued(reopened)
        val claimStore = claims(reopened)
        Then("work, exact input, routing and requirement remain immutable without exposing claim authority")
        take(recovered.loadC(identity)) shouldBe Some(work)
        take(recovered.putIfAbsentC(work)) shouldBe work
        take(claimStore.loadC(identity)).get.claimId shouldBe Some(token)
        take(claimStore.releaseC(identity, token))
        val nextToken = take(claimStore.claimC(identity)).claimId.get
        nextToken should not be token
        take(claimStore.completeC(identity, nextToken))
        take(recovered.loadC(identity)) shouldBe Some(work)
        take(reopened.load(continuations, key(identity.value))).get
          .getString(WorkflowIssuedWorkDataStorePersistence.payloadField) shouldBe Some(retainedPayload)
        take(root(reopened).load(config, current.identity)).get.history shouldBe Vector(request.entry)
      } finally take(reopened.closeC())
    }

    "reject incompatible handles and every projected Continuation contract before writing" in {
      Given("an unmodified root and a matrix of incompatible typed work")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        val work = issue(request)
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        def changed(f: ContinuationRequest[String] => ContinuationRequest[String]) =
          work.copy(current = order.copy(request = f(order.request)))
        val variants = Vector(
          work.copy(handle = work.handle.copy(componentIdentity = ComponentId("org.goldenport.cncf.test.ForeignIssuedWork"))),
          work.copy(handle = work.handle.copy(instanceIdentity = InstanceIdentity("foreign"))),
          work.copy(handle = work.handle.copy(workflowIdentity = WorkflowDefinitionIdentity("foreign"))),
          work.copy(handle = work.handle.copy(workflowRevision = WorkflowDefinitionRevision("foreign"))),
          changed(_.copy(runId = StateMachineRunIdentity("foreign"))),
          changed(_.copy(continuationId = ContinuationIdentity("foreign"))),
          changed(_.copy(expectedRevision = StateMachineRevision("foreign"))),
          changed(_.copy(requiredOperation = StateMachineRequiredOperationIdentity("foreign"))),
          changed(_.copy(operation = StateMachineOperationIdentity("review", "foreign"))),
          changed(_.copy(input = None)),
          changed(_.copy(input = Some(TypedValue("foreign", "input")))),
          changed(_.copy(resultType = Some(StateMachineResultTypeReference("foreign")))),
          changed(r => r.copy(context = r.context.copy(summary = "foreign"))),
          changed(_.copy(completion = CompletionContract("foreign", Vector.empty))),
          changed(_.copy(evidence = EvidenceContract("foreign", Vector.empty))),
          changed(_.copy(completionOperation = StateMachineOperationIdentity("", "invalid"))),
          changed(_.copy(completionOperation = StateMachineOperationIdentity("workflow", "foreign"))))
        take(root(store).create(config, current))
        When("each mismatch is offered to atomic publication")
        variants.foreach(value => issued(store).suspendC(request, value, owner).toOption shouldBe None)
        val mismatchedHistory = request.copy(entry = request.entry.copy(suspension =
          request.entry.suspension.map(_.copy(completion = CompletionReference("foreign")))))
        issued(store).suspendC(mismatchedHistory, work, owner).toOption shouldBe None
        Then("no root revision, Continuation or issued work has been published")
        take(root(store).load(config, current.identity)).get.revision shouldBe initialRevision
        take(claims(store).loadC(identity)) shouldBe None
        take(issued(store).loadC(identity)) shouldBe None
      } finally take(store.closeC())
    }

    "reject incomplete runtime metadata as typed failure before work matching or publication" in {
      Given("a structurally named Continuation with missing nested runtime facts")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        val work = issue(request)
        val c = request.continuation.continuation
        val required = c.requiredOperation
        val malformed = Vector(required.copy(metadata = null), required.copy(inputType = null),
          required.copy(metadata = required.metadata.copy(completionContract = null)))
        take(root(store).create(config, current))
        When("atomic publication admits each malformed intent")
        malformed.foreach { value =>
          val invalid = request.copy(continuation = request.continuation.copy(
            continuation = c.copy(requiredOperation = value)))
          issued(store).suspendC(invalid, work, owner).toOption shouldBe None
        }
        issued(store).suspendC(null, work, owner).toOption shouldBe None
        issued(store).suspendC(request, null, owner).toOption shouldBe None
        Then("no exception, root transition or issue escapes")
        take(root(store).load(config, current.identity)).get.revision shouldBe initialRevision
        take(claims(store).loadC(identity)) shouldBe None
      } finally take(store.closeC())
    }

    "refuse independent initial issue, retrofit and divergent retries" in {
      Given("the existing issued-work SPI without atomic publication")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        val work = issue(request)
        val writer = root(store)
        val adapter = issued(store)
        take(writer.create(config, current))
        adapter.putIfAbsentC(work).toOption shouldBe None
        take(claims(store).loadC(identity)) shouldBe None
        When("legacy S3 publishes a pair without issued payload")
        take(writer.suspendC(request, owner, continuations))
        val before = take(store.load(continuations, key(identity.value))).get
        adapter.loadC(identity).toOption shouldBe None
        adapter.putIfAbsentC(work).toOption shouldBe None
        take(store.load(continuations, key(identity.value))).get shouldBe before
        Then("a separately atomically issued successor allows identical retry but no changed presentation/input")
        val separate = initial.copy(identity = InstanceIdentity("instance-2"))
        val secondRequest = intent(separate, "continuation-2")
        val secondWork = issue(secondRequest)
        take(writer.create(config, separate))
        take(adapter.suspendC(secondRequest, secondWork, owner))
        take(adapter.putIfAbsentC(secondWork)) shouldBe secondWork
        val order = secondWork.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
        adapter.putIfAbsentC(secondWork.copy(current = order.copy(presentation =
          order.presentation.copy(title = "different")))).toOption shouldBe None
        adapter.putIfAbsentC(secondWork.copy(current = order.copy(request =
          order.request.copy(input = Some(TypedValue(codec.typeIdentity, "different")))))).toOption shouldBe None
        take(adapter.loadC(ContinuationIdentity("continuation-2"))) shouldBe Some(secondWork)
      } finally take(store.closeC())
    }

    "fail closed on persisted payload drift without repairing or mutating a claim" in {
      Given("independently corrupted issued payload variants")
      val current = initial
      val request = intent(current)
      val work = issue(request)
      val bytes = take(WorkflowWorkOrderJsonV1.encodeC(work, codec))
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[String]]
      val foreign = work.copy(current = order.copy(request = order.request.copy(runId = StateMachineRunIdentity("foreign"))))
      val payloads = Vector("broken", "cncf.workflow-json.v1:{broken",
        WorkflowInstanceDataStorePersistence.storageText(bytes.replace("cncf.workflow-protocol.v1", "unknown")),
        WorkflowInstanceDataStorePersistence.storageText(take(WorkflowWorkOrderJsonV1.encodeC(foreign, codec))),
        WorkflowInstanceDataStorePersistence.storageText(" " + bytes))
      payloads.foreach { payload =>
        val store = SqlDataStore.sqlite(path.toString)
        try {
          val adapter = issued(store)
          take(root(store).create(config, current))
          take(adapter.suspendC(request, work, owner))
          take(claims(store).claimC(identity))
          take(store.update(continuations, key(identity.value), Record.dataAuto(
            WorkflowIssuedWorkDataStorePersistence.payloadField -> payload)))
          val before = take(store.load(continuations, key(identity.value))).get
          When("recovery tries to admit drifted bytes")
          adapter.loadC(identity).toOption shouldBe None
          adapter.putIfAbsentC(work).toOption shouldBe None
          Then("the stored issue and current token are untouched")
          take(store.load(continuations, key(identity.value))).get shouldBe before
        } finally take(store.closeC())
      }
    }

    "reject failed or lossy application codecs and unsupported store declarations before publication" in {
      Given("application codecs that throw or do not reproduce the admitted typed input")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        val work = issue(request)
        take(root(store).create(config, current))
        val throwing = new PayloadCodec[String] {
          val typeIdentity = codec.typeIdentity
          def encode(value: String): Json = throw new IllegalArgumentException("injected")
          def decode(value: Json): Either[String, String] = codec.decode(value)
        }
        val lossy = new PayloadCodec[String] {
          val typeIdentity = codec.typeIdentity
          def encode(value: String): Json = Json.fromString("lost")
          def decode(value: Json): Either[String, String] = codec.decode(value)
        }
        issued(store, throwing).suspendC(request, work, owner).toOption shouldBe None
        issued(store, lossy).suspendC(request, work, owner).toOption shouldBe None
        val cross = config.copy(delivery = DeliveryConfiguration.CrossStoreCommittedTransition(
          WorkflowStoreIdentity("other"), CommittedEntityTransitionReference("committed"),
          IdempotencyKey("key"), RecoverabilityReference("recovery")))
        WorkflowIssuedWorkDataStorePersistence.bindC(store, cross, component, completionOperation, Vector(binding), instances,
          continuations, codec).toOption shouldBe None
        WorkflowIssuedWorkDataStorePersistence.bindC(store, config, component, completionOperation, Vector(binding), instances,
          instances, codec).toOption shouldBe None
        WorkflowIssuedWorkDataStorePersistence.bindC(DataStore.noop(), config, component, completionOperation, Vector(binding), instances,
          continuations, codec).toOption shouldBe None
        Then("no ordinary fallback issue appears")
        take(root(store).load(config, current.identity)).get.revision shouldBe initialRevision
        take(claims(store).loadC(identity)) shouldBe None
      } finally take(store.closeC())
    }

    "roll back issued payload and both records at every pre-commit checkpoint" in {
      Given("a canonical transition failure matrix")
      val stages = Vector(Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
        Some(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.BeforePublish), None)
      stages.foreach { stage =>
        val db = path
        val source = new SQLiteDataSource(sqliteConfig)
        source.setUrl(s"jdbc:sqlite:$db")
        val store = new SqlDataStore(SqliteDialectDriver, source) {
          override protected def conditional_transition_checkpoint(value: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] =
            if (stage.contains(value)) Consequence.operationInvalid("injected issue checkpoint") else Consequence.unit
          override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] =
            if (stage.isEmpty) DataStoreConditionalTransitionFailure.transactionFailure("injected commit rejection")
            else super.conditional_transition_commit(connection)
        }
        val current = initial
        val request = intent(current)
        try {
          take(root(store).create(config, current))
          issued(store).suspendC(request, issue(request), owner).toOption shouldBe None
        } finally take(store.closeC())
        val reopened = SqlDataStore.sqlite(db.toString)
        try {
          Then("the root remains initial and no Continuation or issue survived")
          take(root(reopened).load(config, current.identity)).get.revision shouldBe initialRevision
          take(claims(reopened).loadC(identity)) shouldBe None
          take(issued(reopened).loadC(identity)) shouldBe None
        } finally take(reopened.closeC())
      }
    }

    "retain one winner and no orphan issue across independently bound SQLite writers" in {
      Given("two physical stores competing for one expected instance revision")
      val db = path
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      val pool = Executors.newFixedThreadPool(2)
      val gate = new CountDownLatch(1)
      try {
        val current = initial
        take(root(left).create(config, current))
        val requests = Vector(intent(current, "left"), intent(current, "right"))
        val adapters = Vector(issued(left), issued(right))
        val futures = requests.zip(adapters).map { case (request, adapter) =>
          pool.submit(new java.util.concurrent.Callable[Consequence[WorkflowInstanceDataStorePersistence.PersistedSuspension]] {
            def call(): Consequence[WorkflowInstanceDataStorePersistence.PersistedSuspension] = {
              if (!gate.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("race gate timed out")
              adapter.suspendC(request, issue(request), owner)
            }
          })
        }
        gate.countDown()
        val outcomes = futures.map(_.get(20L, TimeUnit.SECONDS))
        outcomes.count(_.toOption.nonEmpty) shouldBe 1
        val winner = outcomes.indexWhere(_.toOption.nonEmpty)
        val loser = 1 - winner
        val adapter = issued(left)
        take(adapter.loadC(requests(winner).continuation.continuation.continuationId)) shouldBe Some(issue(requests(winner)))
        take(adapter.loadC(requests(loser).continuation.continuation.continuationId)) shouldBe None
        adapter.suspendC(requests(winner), issue(requests(winner)), owner).toOption shouldBe None
        take(root(left).load(config, current.identity)).get.history shouldBe Vector(requests(winner).entry)
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        take(left.closeC())
        take(right.closeC())
      }
    }

    "classify corrupt applied issued readback as indeterminate and recover without replay" in {
      Given("a committed compound transition whose returned issue property is corrupted")
      val db = path
      val source = new SQLiteDataSource(sqliteConfig)
      source.setUrl(s"jdbc:sqlite:$db")
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(
          using ExecutionContext
        ): Consequence[DataStoreConditionalTransitionResult] =
          super.conditionalTransition(plan).map {
            case DataStoreConditionalTransitionResult.Transitioned(rootValue, successorValue) =>
              val corrupted = Record(successorValue.fields.filterNot(_.key ==
                WorkflowIssuedWorkDataStorePersistence.payloadField)) ++ Record.dataAuto(
                WorkflowIssuedWorkDataStorePersistence.payloadField -> "broken")
              DataStoreConditionalTransitionResult.Transitioned(rootValue, corrupted)
            case other => other
          }
      }
      val current = initial
      val request = intent(current)
      val work = issue(request)
      try {
        take(root(store).create(config, current))
        issued(store).suspendC(request, work, owner) match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.cause.descriptor.facets.collect {
              case org.goldenport.observation.Descriptor.Facet.Reason(value) => value
            } should contain ("transaction-indeterminate")
          case _ => fail("unadmittable applied readback must be indeterminate")
        }
      } finally take(store.closeC())
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        Then("committed exact work is recovered rather than reissued")
        val adapter = issued(reopened)
        take(adapter.loadC(identity)) shouldBe Some(work)
        adapter.suspendC(request, work, owner).toOption shouldBe None
        take(adapter.putIfAbsentC(work)) shouldBe work
        take(root(reopened).load(config, current.identity)).get.history shouldBe Vector(request.entry)
      } finally take(reopened.closeC())
    }
  }
}
