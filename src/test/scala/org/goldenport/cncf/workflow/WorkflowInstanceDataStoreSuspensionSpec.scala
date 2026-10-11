package org.goldenport.cncf.workflow

import java.nio.file.{Files, Path}
import java.sql.Connection
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
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
import org.sqlite.{SQLiteConfig, SQLiteDataSource}

/**
 * Paired datastore commit and claim recovery, not full UoW/issued-work/evidence atomicity.
 * @since   Sep. 29, 2026
 * @version Oct.  9, 2026
 */
final class WorkflowInstanceDataStoreSuspensionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "workflow_instance_v1"))
  private val continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "workflow_continuation_v1"))
  private val owner = take(DataStoreComponentOwner.create("org.goldenport.cncf.test.Suspension"))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private def binding: DefinitionBinding = {
    val sidecar = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
      resource("/workflow/candidate-admission-workflow-abi.json").trim, sidecar,
      "modeler/candidate-admission-workflow.cml"))))
  }
  private def initial: InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), binding, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      None, None, None, Vector.empty)
  private def continuation(identity: String): ContinuationRuntimePersistence.Record = {
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("review.required"),
      "ReviewChange", StateMachineOperationIdentity("review", "change"), None,
      Some(StateMachineResultTypeReference("review.result.v1")),
      StateMachineRequiredOperationMetadata(ContextContract("review.context", Vector.empty, Vector.empty),
        CompletionContract("review.completion", Vector.empty), EvidenceContract("review.evidence", Vector.empty),
        Vector.empty))
    ContinuationRuntimePersistence.Record(Continuation(StateMachineRunIdentity("run-1"),
      ContinuationIdentity(identity), StateMachineRevision("machine-rev-1"), required,
      ContextBundle("review context", Vector.empty, Vector.empty, ContextSnapshot("workflow-producer-v1"))),
      ContinuationRuntimePersistence.Status.Available)
  }
  private def intent(current: InstanceRecord, identity: String = "continuation-1"):
      WorkflowInstanceAtomicTransitionV1.SuspensionIntent = {
    val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), current.correlation, current.causation,
      None, None, Some(SuspensionBoundary(InstanceContinuationIdentity(identity), InstanceRevision(1L),
        ContextSnapshotReference("context-1"), CompletionReference("complete-1"),
        EvidenceReference("evidence-1"), CorrelationReference("resume-1"))))
    WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      config, current, initialRevision, entry, continuation(identity))
  }
  private def provider(store: DataStore, configuration: Configuration = config,
    collection: DataStore.CollectionId = instances): WorkflowInstanceDataStorePersistence =
    take(WorkflowInstanceDataStorePersistence.bindC(store, configuration, Vector(binding), collection))
  private def claims(store: DataStore, configuration: Configuration = config): WorkflowContinuationDataStorePersistence =
    take(WorkflowContinuationDataStorePersistence.bindC(store, configuration, continuations))
  private def path: Path = Files.createTempDirectory("cncf-phase88-suspension-").resolve("workflow.sqlite")
  private def ckey(identity: String) = DataStore.StringEntryId(identity)
  private def race[A](calls: Vector[() => Consequence[A]]): Vector[Consequence[A]] = {
    val pool = Executors.newFixedThreadPool(calls.size)
    val gate = new CountDownLatch(1)
    try {
      val futures = calls.map { operation =>
        pool.submit(new java.util.concurrent.Callable[Consequence[A]] {
          def call(): Consequence[A] = {
            if (!gate.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("race gate timed out")
            operation()
          }
        })
      }
      gate.countDown()
      futures.map(_.get(20L, TimeUnit.SECONDS))
    } finally {
      pool.shutdownNow()
      pool.awaitTermination(20L, TimeUnit.SECONDS)
    }
  }

  "Phase 88 paired suspension and durable claims" should {
    "commit exact instance and Continuation together and retain claimed work after SQLite reopen" in {
      Given("one exact admitted instance and Available suspension intent")
      val db = path
      val first = SqlDataStore.sqlite(db.toString)
      val current = initial
      val request = intent(current)
      var token = ""
      try {
        val writer = provider(first)
        take(writer.create(config, current))
        When("one canonical compound transition publishes the pair")
        val paired = take(writer.suspendC(request, owner, continuations))
        paired.instance.history shouldBe Vector(request.entry)
        paired.continuation shouldBe request.continuation
        token = take(claims(first).claimC(ContinuationIdentity("continuation-1"))).claimId.get
      } finally take(first.closeC())
      When("a new physical resource and admitted catalog recover the same file")
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val recovered = take(provider(reopened).load(config, current.identity)).get
        val claimStore = claims(reopened)
        val retained = take(claimStore.loadC(ContinuationIdentity("continuation-1"))).get
        Then("exact suspension and claim remain; restart does not automatically release work")
        recovered.history shouldBe Vector(request.entry)
        (recovered.definition eq current.definition) shouldBe false
        retained.status shouldBe ContinuationRuntimePersistence.Status.Claimed
        retained.claimId shouldBe Some(token)
        claimStore.claimC(ContinuationIdentity("continuation-1")).toOption shouldBe None
        take(claimStore.completeC(ContinuationIdentity("continuation-1"), token)).status shouldBe
          ContinuationRuntimePersistence.Status.Completed
        claimStore.completeC(ContinuationIdentity("continuation-1"), token).toOption shouldBe None
        take(provider(reopened).load(config, current.identity)).get.history shouldBe Vector(request.entry)
      } finally take(reopened.closeC())
    }

    "roll back an occupied Continuation without publishing an orphan root append" in {
      Given("an initial instance and an already-created Continuation")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val writer = provider(store)
        val current = initial
        take(writer.create(config, current))
        take(claims(store).createC(continuation("continuation-1")))
        When("a suspension tries to create the occupied successor")
        val rejected = writer.suspendC(intent(current), owner, continuations)
        val retainedroot = take(writer.load(config, current.identity)).get
        val retainedcontinuation = take(claims(store).loadC(ContinuationIdentity("continuation-1"))).get
        Then("the root remains initial and the existing successor remains exact")
        rejected.toOption shouldBe None
        retainedroot.identity shouldBe current.identity
        retainedroot.revision shouldBe initialRevision
        retainedroot.lifecycle shouldBe Lifecycle.NotStarted
        retainedroot.currentProgression shouldBe None
        retainedroot.suspension shouldBe None
        retainedroot.history shouldBe Vector.empty
        retainedroot.correlation shouldBe current.correlation
        retainedroot.causation shouldBe current.causation
        retainedcontinuation shouldBe continuation("continuation-1")
      } finally take(store.closeC())
    }

    "use the stored predecessor rather than a diagnostic copy and refuse a stale append without orphan work" in {
      Given("an actual initial root, a different caller causation copy and its unchanged explicit append")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val writer = provider(store)
        val current = initial
        take(writer.create(config, current))
        val diagnostic = current.copy(causation = CausationReference("different-start"))
        val request = intent(current, "winner").copy(current = diagnostic)
        When("the valid request appends against the stored root and another request uses its old revision")
        val accepted = take(writer.suspendC(request, owner, continuations))
        val stale = writer.suspendC(intent(current, "stale"), owner, continuations)
        val retainedroot = take(writer.load(config, current.identity)).get
        val retainedwinner = take(claims(store).loadC(ContinuationIdentity("winner")))
        val orphan = take(claims(store).loadC(ContinuationIdentity("stale")))
        Then("the actual append and its continuation survive; the stale writer creates neither a revision nor work")
        accepted.instance.history shouldBe Vector(request.entry)
        accepted.instance.causation shouldBe request.entry.causation
        retainedroot shouldBe accepted.instance
        retainedwinner shouldBe Some(request.continuation)
        stale.toOption shouldBe None
        orphan shouldBe None
      } finally take(store.closeC())
    }

    "roll back both records at every canonical provider checkpoint and rejected commit" in {
      Given("a failure matrix after guard, successor, root, before publish and before commit")
      val stages = Vector(
        Some(DataStoreConditionalTransitionCheckpoint.GuardAdmitted),
        Some(DataStoreConditionalTransitionCheckpoint.SuccessorPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.RootPrepared),
        Some(DataStoreConditionalTransitionCheckpoint.BeforePublish), None)
      stages.foreach { stage =>
        val db = path
        val source = new SQLiteDataSource(sqliteConfig)
        source.setUrl(s"jdbc:sqlite:$db")
        val failing = new SqlDataStore(SqliteDialectDriver, source) {
          override protected def conditional_transition_checkpoint(value: DataStoreConditionalTransitionCheckpoint):
              Consequence[Unit] =
            if (stage.contains(value)) Consequence.operationInvalid("injected suspension checkpoint")
            else Consequence.unit
          override protected def conditional_transition_commit(connection: Connection): Consequence[Unit] =
            if (stage.isEmpty) DataStoreConditionalTransitionFailure.transactionFailure("injected commit rejection")
            else super.conditional_transition_commit(connection)
        }
        val current = initial
        val writer = provider(failing)
        take(writer.create(config, current))
        When(s"the paired transition fails at $stage")
        writer.suspendC(intent(current), owner, continuations).toOption shouldBe None
        val reopened = SqlDataStore.sqlite(db.toString)
        try {
          Then("the durable root is unchanged and no Available work survived")
          take(provider(reopened).load(config, current.identity)).get.revision shouldBe initialRevision
          take(claims(reopened).loadC(ContinuationIdentity("continuation-1"))) shouldBe None
        } finally take(reopened.closeC())
      }
    }

    "publish exactly one pair across independent SQLite suspension writers" in {
      Given("two physical adapters and different Continuations competing for one root revision")
      val db = path
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      try {
        val current = initial
        val a = provider(left)
        val b = provider(right)
        take(a.create(config, current))
        When("both compound transitions race")
        val outcomes = race(Vector(
          () => a.suspendC(intent(current, "left"), owner, continuations),
          () => b.suspendC(intent(current, "right"), owner, continuations)))
        Then("one root append and only its corresponding successor exist")
        outcomes.count(_.toOption.nonEmpty) shouldBe 1
        val winner = outcomes.flatMap(_.toOption).head
        val loaded = take(a.load(config, current.identity)).get
        loaded.history shouldBe winner.instance.history
        Vector("left", "right").count(id => take(claims(left).loadC(ContinuationIdentity(id))).nonEmpty) shouldBe 1
        take(claims(left).loadC(winner.continuation.continuation.continuationId)).get shouldBe winner.continuation
      } finally { take(left.closeC()); take(right.closeC()) }
    }

    "admit one durable claim winner across independent SQLite callers" in {
      Given("one Available Continuation in a shared canonical database")
      val db = path
      val left = SqlDataStore.sqlite(db.toString)
      val right = SqlDataStore.sqlite(db.toString)
      try {
        val a = claims(left)
        val b = claims(right)
        val identity = ContinuationIdentity("continuation-1")
        take(a.createC(continuation(identity.value)))
        When("both independent claim adapters race")
        val outcomes = race(Vector(() => a.claimC(identity), () => b.claimC(identity)))
        Then("only one exact token is retained")
        outcomes.count(_.toOption.nonEmpty) shouldBe 1
        take(b.loadC(identity)).get shouldBe outcomes.flatMap(_.toOption).head
      } finally { take(left.closeC()); take(right.closeC()) }
    }

    "enforce exact token and status transitions without changing the StateMachine revision" in {
      Given("one Available record and a canonical claim adapter")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val adapter = claims(store)
        val identity = ContinuationIdentity("continuation-1")
        take(adapter.createC(continuation(identity.value)))
        val first = take(adapter.claimC(identity)).claimId.get
        When("foreign tokens, release, replacement claim and completion are submitted")
        adapter.releaseC(identity, "foreign").toOption shouldBe None
        adapter.completeC(identity, null).toOption shouldBe None
        take(adapter.releaseC(identity, first)).status shouldBe ContinuationRuntimePersistence.Status.Available
        val second = take(adapter.claimC(identity)).claimId.get
        second should not be first
        adapter.completeC(identity, first).toOption shouldBe None
        take(adapter.completeC(identity, second)).status shouldBe ContinuationRuntimePersistence.Status.Completed
        adapter.releaseC(identity, second).toOption shouldBe None
        adapter.completeC(identity, second).toOption shouldBe None
        Then("immutable operation/context revision survives five guarded storage states")
        take(adapter.loadC(identity)).get.continuation shouldBe continuation(identity.value).continuation
        val stored = take(store.load(continuations, ckey(identity.value))).get
        take(WorkflowContinuationDataStorePersistence.revisionBinding.revision(stored)).value shouldBe 5L
        adapter.createC(continuation("bad").copy(status = ContinuationRuntimePersistence.Status.Completed,
          claimId = Some("token"))).toOption shouldBe None
      } finally take(store.closeC())
    }

    "reopen legacy Continuations under current policy while guarding actual claim tokens" in {
      Given("a new SQLite Continuation with an arbitrary old policy column added")
      val db = path
      val first = SqlDataStore.sqlite(db.toString)
      val identity = ContinuationIdentity("continuation-1")
      var initialfields = Set.empty[String]
      try {
        take(claims(first).createC(continuation(identity.value)))
        initialfields = take(first.load(continuations, ckey(identity.value))).get.fields.map(_.key).toSet
        take(first.update(continuations, ckey(identity.value), Record.dataAuto("configurationPayload" -> "old policy")))
      } finally take(first.closeC())
      When("the physical database reopens with changed current policy and consumes actual live claims")
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val adapter = claims(reopened, config.copy(retentionPolicy = RetentionPolicy("changed")))
        val loaded = take(adapter.loadC(identity)).get
        val firstclaim = take(adapter.claimC(identity)).claimId.get
        val foreign = adapter.completeC(identity, "foreign")
        val released = take(adapter.releaseC(identity, firstclaim))
        val secondclaim = take(adapter.claimC(identity)).claimId.get
        val stale = adapter.completeC(identity, firstclaim)
        val completed = take(adapter.completeC(identity, secondclaim))
        val retained = take(reopened.load(continuations, ckey(identity.value))).get
        val revision = take(WorkflowContinuationDataStorePersistence.revisionBinding.revision(retained))
        Then("the copy remains inert and unchanged while claim state and revision remain authoritative")
        initialfields should not contain "configurationPayload"
        loaded.status shouldBe ContinuationRuntimePersistence.Status.Available
        foreign.toOption shouldBe None
        released.status shouldBe ContinuationRuntimePersistence.Status.Available
        secondclaim should not be firstclaim
        stale.toOption shouldBe None
        completed.status shouldBe ContinuationRuntimePersistence.Status.Completed
        retained.getString("configurationPayload") shouldBe Some("old policy")
        revision.value shouldBe 5L
      } finally take(reopened.closeC())
    }

    "commit suspension when only a legacy policy column changes after preparation" in {
      Given("an actual SQLite root and an uncommitted prepared suspension")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val request = intent(current)
        val writer = provider(store)
        take(writer.create(config, current))
        val prepared = take(writer.prepareSuspensionC(request, owner, continuations))
        When("legacy policy material changes without consuming the indexed root revision")
        take(store.update(instances, ckey(current.identity.value), Record.dataAuto("configurationPayload" -> "changed after preparation")))
        val paired = take(prepared.commitC())
        val retainedroot = take(store.load(instances, ckey(current.identity.value))).get
        val retainedcontinuation = take(store.load(continuations, ckey("continuation-1"))).get
        Then("the real revision commits the pair and neither copies nor certifies policy material")
        paired.instance.history shouldBe Vector(request.entry)
        paired.continuation.status shouldBe ContinuationRuntimePersistence.Status.Available
        retainedroot.getString("configurationPayload") shouldBe Some("changed after preparation")
        retainedcontinuation.fields.map(_.key) should not contain "configurationPayload"
      } finally take(store.closeC())
    }

    "reject malformed Continuation storage without rewriting stored facts" in {
      Given("invalid payload envelopes or indexed revision")
      val corruptions = Vector(
        Record.dataAuto("continuationPayload" -> "{}"),
        Record.dataAuto("continuationPayload" -> "cncf.workflow-json.v1:not-json"),
        Record.dataAuto("cncf_revision" -> 0L))
      corruptions.foreach { changes =>
        val store = SqlDataStore.sqlite(path.toString)
        try {
          val adapter = claims(store)
          val identity = ContinuationIdentity("continuation-1")
          take(adapter.createC(continuation(identity.value)))
          take(store.update(continuations, ckey(identity.value), changes))
          val before = take(store.load(continuations, ckey(identity.value))).get
          When("load or claim encounters inconsistent stored facts")
          val loaded = adapter.loadC(identity)
          val claimed = adapter.claimC(identity)
          val retained = take(store.load(continuations, ckey(identity.value))).get
          Then("no payload or indexed revision is repaired")
          loaded.toOption shouldBe None
          claimed.toOption shouldBe None
          retained shouldBe before
        } finally take(store.closeC())
      }
    }

    "recheck the root revision inside the compound commit after its earlier admission read" in {
      Given("a competing writer advancing the root after the adapter loaded revision zero")
      val db = path
      val current = initial
      val request = intent(current)
      val source = new SQLiteDataSource(sqliteConfig)
      source.setUrl(s"jdbc:sqlite:$db")
      val competing = request.entry.copy(suspension = None)
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(
          using ExecutionContext
        ): Consequence[DataStoreConditionalTransitionResult] = {
          take(provider(this).append(config, current.identity, initialRevision, competing))
          super.conditionalTransition(plan)
        }
      }
      val writer = provider(store)
      take(writer.create(config, current))
      When("the compound provider receives the now-stale guarded intent")
      writer.suspendC(request, owner, continuations).toOption shouldBe None
      Then("the concurrent append is retained and the losing Continuation never appears")
      take(writer.load(config, current.identity)).get.history shouldBe Vector(competing)
      take(claims(store).loadC(ContinuationIdentity("continuation-1"))) shouldBe None
    }

    "classify committed but inadmissible authoritative readback as indeterminate without replay" in {
      Given("a canonical provider whose durable commit succeeds but returned payload is corrupted")
      val db = path
      val source = new SQLiteDataSource(sqliteConfig)
      source.setUrl(s"jdbc:sqlite:$db")
      val store = new SqlDataStore(SqliteDialectDriver, source) {
        override def conditionalTransition(plan: DataStoreConditionalTransitionPlan)(
          using ExecutionContext
        ): Consequence[DataStoreConditionalTransitionResult] =
          super.conditionalTransition(plan).map {
            case DataStoreConditionalTransitionResult.Transitioned(_, successor) =>
              DataStoreConditionalTransitionResult.Transitioned(Record.dataAuto("broken" -> "readback"), successor)
            case other => other
          }
        override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(
          using ExecutionContext
        ): Consequence[EntityMutationProviderResult] =
          super.compareAndSetEntity(plan).map {
            case EntityMutationProviderResult.Applied(_) =>
              EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(
                Record.dataAuto("broken" -> "readback")))
            case other => other
          }
      }
      val current = initial
      val writer = provider(store)
      take(writer.create(config, current))
      def reasons[A](value: Consequence[A]): Set[String] = value match {
        case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
          case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
        }.toSet
        case _ => Set.empty
      }
      When("paired suspension and then claim commit but cannot admit the returned bytes")
      reasons(writer.suspendC(intent(current), owner, continuations)) should contain ("transaction-indeterminate")
      val adapter = claims(store)
      val identity = ContinuationIdentity("continuation-1")
      reasons(adapter.claimC(identity)) should contain ("transaction-indeterminate")
      Then("ordinary recovery reads retain the committed pair and claim instead of manufacturing retries")
      take(writer.load(config, current.identity)).get.history shouldBe Vector(intent(current).entry)
      val retained = take(adapter.loadC(identity)).get
      retained.status shouldBe ContinuationRuntimePersistence.Status.Claimed
      retained.claimId.nonEmpty shouldBe true
      adapter.claimC(identity).toOption shouldBe None
    }

    "fail closed on cross-store delivery, non-Entity collections and malformed suspension admission" in {
      Given("one valid instance but inadmissible transaction declarations")
      val store = SqlDataStore.sqlite(path.toString)
      try {
        val current = initial
        val writer = provider(store)
        take(writer.create(config, current))
        val request = intent(current)
        When("unsupported or conflicting compound boundaries are requested")
        writer.suspendC(request, owner, instances).toOption shouldBe None
        writer.suspendC(request, owner, DataStore.CollectionId("non_entity")).toOption shouldBe None
        writer.suspendC(request, owner, null).toOption shouldBe None
        writer.suspendC(request.copy(continuation = continuation("foreign")), owner, continuations).toOption shouldBe None
        writer.suspendC(request.copy(continuation = continuation("continuation-1").copy(
          status = ContinuationRuntimePersistence.Status.Claimed, claimId = Some("already"))), owner, continuations)
          .toOption shouldBe None
        writer.suspendC(null, owner, continuations).toOption shouldBe None
        val cross = config.copy(delivery = DeliveryConfiguration.CrossStoreCommittedTransition(
          WorkflowStoreIdentity("other"), CommittedEntityTransitionReference("committed"),
          IdempotencyKey("key"), RecoverabilityReference("recovery")))
        provider(store, cross).suspendC(request.copy(configuration = cross), owner, continuations).toOption shouldBe None
        Then("neither root history nor available work changes")
        take(writer.load(config, current.identity)).get.revision shouldBe initialRevision
        take(claims(store).loadC(ContinuationIdentity("continuation-1"))) shouldBe None
        WorkflowContinuationDataStorePersistence.bindC(DataStore.noop(), config, continuations).toOption shouldBe None
      } finally take(store.closeC())
    }
  }
  private def sqliteConfig: SQLiteConfig = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(10000)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    config
  }
}
