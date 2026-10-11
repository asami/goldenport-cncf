package org.goldenport.cncf.workflow

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Phase 88 S1: borrowed provider acceptance, not server/MCP or joint claim recovery.
 * @since   Sep. 29, 2026
 * @version Oct.  9, 2026
 */
final class WorkflowInstanceDataStorePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val collection = DataStore.CollectionId("workflow_instance_v1")
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

  private def binding: DefinitionBinding = bindingWithSuffix("")

  private def bindingWithSuffix(suffix: String): DefinitionBinding = {
    val sidecar = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json")))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
      resource("/workflow/candidate-admission-workflow-abi.json").trim + suffix, sidecar,
      "modeler/candidate-admission-workflow.cml"))))
  }

  private def initial(definition: DefinitionBinding = binding): InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), definition, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      Some(DerivedCompositeOccurrenceReference("composite-1")),
      Some(CommittedEntityTransitionReference("predecessor-1")), None, Vector.empty)

  private def active: HistoryEntry =
    HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), CorrelationReference("correlation-1"),
      CausationReference("start-1"), Some(DerivedCompositeOccurrenceReference("composite-1")),
      Some(CommittedEntityTransitionReference("predecessor-1")), None)

  private def suspended: HistoryEntry = active.copy(sequence = HistorySequence(2L),
    revision = InstanceRevision(2L), suspension = Some(SuspensionBoundary(
      ContinuationIdentity("continuation-1"), InstanceRevision(2L), ContextSnapshotReference("context-1"),
      CompletionReference("complete-1"), EvidenceReference("evidence-1"), CorrelationReference("resume-1"))))

  private def provider(store: DataStore, configuration: Configuration = config): WorkflowInstanceDataStorePersistence =
    take(WorkflowInstanceDataStorePersistence.bindC(store, configuration, Vector(binding), collection))

  "Phase 88 Workflow instance datastore provider" should {
    "round trip every persisted fact through independently admitted catalog bindings" in {
      Given("a valid CAM binding and a suspended instance")
      val record = take(take(initial().appendC(initialRevision, active)).appendC(InstanceRevision(1L), suspended))
      val bytes = take(WorkflowInstanceRecordJsonV1.encodeC(record))
      When("the bytes reopen against a freshly admitted catalog")
      val decoded = take(WorkflowInstanceRecordJsonV1.decodeC(bytes,
        take(WorkflowInstanceRecordJsonV1.catalogC(Vector(binding)))))
      Then("history, suspension, provenance and current projection remain exact")
      take(WorkflowInstanceRecordJsonV1.encodeC(decoded)) shouldBe bytes
      decoded.history shouldBe record.history
      decoded.suspension shouldBe record.suspension
      decoded.definition.workflowIdentity shouldBe record.definition.workflowIdentity
      decoded.definition.workflowRevision shouldBe record.definition.workflowRevision
      (decoded.definition eq record.definition) shouldBe false
    }

    "reject wrong schema, unresolved references and inconsistent current history" in {
      Given("valid initial and active record bytes")
      val record = take(initial().appendC(initialRevision, active))
      val json = parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
      val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(binding)))
      val variants = Vector(
        json.deepMerge(Json.obj("nextState" -> Json.fromString("Closed"))),
        json.deepMerge(Json.obj("schemaVersion" -> Json.fromString("unknown"))),
        json.deepMerge(Json.obj("definition" -> Json.obj("revision" -> Json.fromString("foreign-revision")))),
        json.deepMerge(Json.obj("current" -> Json.obj("revision" -> Json.fromLong(2L)))),
        json.deepMerge(Json.obj("history" -> Json.arr()))
      )
      When("each modified document is decoded without trusting its definition bytes")
      val results = variants.map(value => WorkflowInstanceRecordJsonV1.decodeC(value.noSpaces, catalog))
      Then("none admits a reconstructed definition or divergent state")
      results.forall(_.toOption.isEmpty) shouldBe true
      WorkflowInstanceRecordJsonV1.catalogC(Vector(binding, binding)).toOption.nonEmpty shouldBe true
    }

    "resolve executable references while ignoring legacy integrity and provenance fields" in {
      Given("Spec: docs/spec/workflow-definition-reference-reopening.md; Rule: R1; Example: E1; two separately admitted definitions with equal declared references")
      val original = binding
      val later = bindingWithSuffix("\n\n")
      val record = take(initial(original).appendC(initialRevision, active))
      val json = parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
      val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(later)))
      When("the current wire and historical records reopen without using any stored fixture checksum")
      val variants = Vector(json) ++ Vector(Json.fromString("historical-unused-value"), Json.Null)
        .map(value => json.deepMerge(Json.obj("definition" -> Json.obj("fixtureSha256" -> value,
          "source" -> Json.obj("root" -> Json.obj("line" -> Json.fromInt(999))),
          "producerAbi" -> Json.fromString("old-generator-schema"),
          "producerRevision" -> Json.fromString("old-generator-revision")))))
      Then("new writes omit the digest and each read selects the actual separately admitted definition")
      json.hcursor.downField("definition").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision")
      variants.foreach { value =>
        val reopened = take(WorkflowInstanceRecordJsonV1.decodeC(value.noSpaces, catalog))
        (reopened.definition eq later) shouldBe true
        reopened.history shouldBe record.history
        reopened.revision shouldBe record.revision
        take(WorkflowInstanceRecordJsonV1.encodeC(reopened)) shouldBe json.noSpaces
      }
      When("an actual reference resolves to two distinct execution definitions")
      Then("resolution rejects ambiguity instead of silently choosing a definition")
      WorkflowInstanceRecordJsonV1.decodeC(json.noSpaces, take(WorkflowInstanceRecordJsonV1.catalogC(Vector(original, later)))).toOption shouldBe None
    }

    "physically reopen SQLite using a formatting-independent admitted definition reference" in {
      Given("Spec: docs/spec/workflow-definition-reference-reopening.md; Rule: R2; Example: E2; an actual file-backed instance and retained suspension")
      val path = Files.createTempDirectory("cncf-reference-reopen-").resolve("workflow.sqlite")
      val record = initial()
      val first = SqlDataStore.sqlite(path.toString)
      try {
        val writer = provider(first)
        take(writer.create(config, record))
        take(writer.append(config, record.identity, initialRevision, active))
        take(writer.append(config, record.identity, InstanceRevision(1L), suspended))
      } finally take(first.closeC())
      When("a new physical provider binds the same declared reference from differently formatted ABI text")
      val parsed = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/candidate-admission-workflow-abi.json"),
    take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/candidate-admission-producer-abi.json"))),
    "relocated/workflow.cml"))
  val later = take(WorkflowInstancePersistence.bindCandidateDefinitionC(parsed.copy(
    workflow = parsed.workflow.copy(rootLine = 999, definitionLine = 1000))))
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val reader = take(WorkflowInstanceDataStorePersistence.bindC(reopened, config, Vector(later), collection))
        val loaded = take(reader.load(config, record.identity)).get
        Then("the actual saved history reopens without a content certificate or stale-writer permission")
        (loaded.definition eq later) shouldBe true
        loaded.history shouldBe Vector(active, suspended)
        loaded.suspension shouldBe suspended.suspension
        reader.append(config, record.identity, initialRevision, active).toOption shouldBe None
      } finally take(reopened.closeC())
    }

    "use canonical in-memory CAS without adding a default store or mutation Operation" in {
      Given("two adapter bindings sharing one canonical datastore")
      val store = DataStore.inMemorySearchable()
      val writer = provider(store)
      val reader = provider(store)
      val record = initial()
      take(writer.create(config, record))
      When("one revision append and one stale replay are submitted")
      val accepted = take(writer.append(config, record.identity, initialRevision, active))
      val replay = reader.append(config, record.identity, initialRevision, active)
      Then("one immutable history entry is retained and the stale writer changes nothing")
      accepted.revision shouldBe InstanceRevision(1L)
      replay.toOption shouldBe None
      take(reader.load(config, record.identity)).get.history shouldBe Vector(active)
      writer.create(config, record).toOption shouldBe None
      writer.create(config, accepted).toOption shouldBe None
      take(reader.load(config, InstanceIdentity("absent"))) shouldBe None
      WorkflowInstanceDataStorePersistence.bindC(DataStore.noop(), config, Vector(binding), collection)
        .toOption shouldBe None
    }

    "recover full history and exact ABI after closing and reopening SQLite" in {
      Given("a fresh file-backed canonical SQLite datastore")
      val path = Files.createTempDirectory("cncf-phase88-reopen-").resolve("workflow.sqlite")
      val first = SqlDataStore.sqlite(path.toString)
      val record = initial()
      try {
        val writer = provider(first)
        take(writer.create(config, record))
        take(writer.append(config, record.identity, initialRevision, active))
        take(writer.append(config, record.identity, InstanceRevision(1L), suspended))
      } finally take(first.closeC())
      When("another SQLite resource and independently admitted adapter reopen the file")
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val loaded = take(provider(reopened).load(config, record.identity)).get
        Then("current facts and both append-only entries are rehydrated rather than fabricated")
        loaded.revision shouldBe InstanceRevision(2L)
        loaded.history shouldBe Vector(active, suspended)
        loaded.suspension shouldBe suspended.suspension
        loaded.definition.validateC.toOption.nonEmpty shouldBe true
        provider(reopened).append(config, record.identity, initialRevision, active).toOption shouldBe None
      } finally take(reopened.closeC())
    }

    "omit copied policy and reopen legacy rows under the current bound policy without rewriting them" in {
      Given("a real SQLite initial instance and independently selected current retention policy")
      val db = Files.createTempDirectory("cncf-policy-reopen-").resolve("workflow.sqlite")
      val first = SqlDataStore.sqlite(db.toString)
      val record = initial()
      val changed = config.copy(retentionPolicy = RetentionPolicy("changed"))
      val entryid = DataStore.StringEntryId(record.identity.value)
      var initialfields = Set.empty[String]
      try {
        take(provider(first).create(changed, record))
        initialfields = take(first.load(collection, entryid)).get.fields.map(_.key).toSet
        take(first.update(collection, entryid, Record.dataAuto("configurationPayload" -> "legacy arbitrary policy")))
      } finally take(first.closeC())
      When("a new physical provider uses current policy and appends through a legacy Configuration argument")
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val adapter = provider(reopened, changed)
        val loaded = take(adapter.load(config, record.identity)).get
        val appended = take(adapter.append(config, record.identity, initialRevision, active))
        val stale = adapter.append(config, record.identity, initialRevision, active)
        val retained = take(reopened.load(collection, entryid)).get
        Then("current binding owns access, the old copy is inert and actual stale revision is still refused")
        initialfields should not contain "configurationPayload"
        loaded.identity shouldBe record.identity
        loaded.revision shouldBe initialRevision
        appended.history shouldBe Vector(active)
        appended.revision shouldBe InstanceRevision(1L)
        stale.toOption shouldBe None
        retained.getString("configurationPayload") shouldBe Some("legacy arbitrary policy")
      } finally take(reopened.closeC())
    }

    "reject corrupt indexed revision before a mutation" in {
      Given("one stored initial record with an inconsistent physical revision")
      val store = DataStore.inMemorySearchable()
      val record = initial()
      take(provider(store).create(config, record))
      take(store.update(collection, DataStore.StringEntryId(record.identity.value),
        Record.dataAuto("cncf_revision" -> 7L)))
      When("load and append encounter the actual indexed revision mismatch")
      val loaded = provider(store).load(config, record.identity)
      val appended = provider(store).append(config, record.identity, initialRevision, active)
      Then("load and append reject divergence without silently resetting a store")
      loaded.toOption shouldBe None
      appended.toOption shouldBe None
    }

    "reject malformed storage envelopes and missing catalogs without changing stored facts" in {
      Given("canonical stores with unsupported, invalid or misbound persisted payloads")
      val changes = Vector(
        Record.dataAuto("recordPayload" -> "{}"),
        Record.dataAuto("recordPayload" -> "cncf.workflow-json.v2:{}"),
        Record.dataAuto("recordPayload" -> "cncf.workflow-json.v1:not-json")
      )
      changes.foreach { change =>
        val store = DataStore.inMemorySearchable()
        val adapter = provider(store)
        val record = initial()
        take(adapter.create(config, record))
        val entryId = DataStore.StringEntryId(record.identity.value)
        take(store.update(collection, entryId, change))
        val before = take(store.load(collection, entryId)).get
        When("the malformed row is loaded and an append is attempted")
        val loaded = adapter.load(config, record.identity)
        val appended = adapter.append(config, record.identity, initialRevision, active)
        val retained = take(store.load(collection, entryId)).get
        val uncatalogued = WorkflowInstanceDataStorePersistence.bindC(store, config, Vector.empty, collection)
        Then("neither decoding failure nor absent authority can rewrite the row")
        loaded.toOption shouldBe None
        appended.toOption shouldBe None
        retained shouldBe before
        uncatalogued.toOption shouldBe None
      }
    }

    "admit at most one concurrent SQLite writer for the same expected revision" in {
      Given("two canonical SQLite resources sharing one initial record")
      val path = Files.createTempDirectory("cncf-phase88-cas-").resolve("workflow.sqlite")
      val left = SqlDataStore.sqlite(path.toString)
      val right = SqlDataStore.sqlite(path.toString)
      val pool = Executors.newFixedThreadPool(2)
      try {
        val record = initial()
        val a = provider(left)
        val b = provider(right)
        take(a.create(config, record))
        val gate = new CountDownLatch(1)
        val futures = Vector(a, b).map { adapter =>
          pool.submit(new java.util.concurrent.Callable[Consequence[InstanceRecord]] {
            def call(): Consequence[InstanceRecord] = {
              if (!gate.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("CAS test gate timed out")
              adapter.append(config, record.identity, initialRevision, active)
            }
          })
        }
        When("both resources try to consume revision zero")
        gate.countDown()
        val results = futures.map(_.get(20L, TimeUnit.SECONDS))
        Then("only one append succeeds and reopening observes one unchanged history entry")
        results.count(_.toOption.nonEmpty) shouldBe 1
        val loaded = take(a.load(config, record.identity)).get
        loaded.revision shouldBe InstanceRevision(1L)
        loaded.history shouldBe Vector(active)
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        take(left.closeC())
        take(right.closeC())
      }
    }
  }
}
