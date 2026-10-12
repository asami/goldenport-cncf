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
 * @version Oct. 12, 2026
 */
final class WorkflowInstanceDataStorePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  private def _temporary_directory(prefix: String): java.nio.file.Path = {
    val directory = java.nio.file.Path.of("target", "phase105", "work", "WorkflowInstanceDataStorePersistenceSpec").toAbsolutePath
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, prefix)
  }
  private given ExecutionContext = ExecutionContext.create()
  private val _collection = DataStore.CollectionId("workflow_instance_v1")
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("store-1")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("store-1")))

  private def _take[A](result: Consequence[A]): A = {
     result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  }

  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }

  private def _binding: DefinitionBinding = _binding_with_suffix("")

  private def _binding_with_suffix(suffix: String): DefinitionBinding = {
    val sidecar = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
    _take(WorkflowInstancePersistence.bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
      _resource("/workflow/candidate-admission-workflow-abi.json").trim + suffix, sidecar,
      "modeler/candidate-admission-workflow.cml"))))
  }

  private def _initial(definition: DefinitionBinding = _binding): InstanceRecord =
    InstanceRecord(InstanceIdentity("instance-1"), definition, initialRevision, Lifecycle.NotStarted,
      None, CorrelationReference("correlation-1"), CausationReference("start-1"),
      Some(DerivedCompositeOccurrenceReference("composite-1")),
      Some(CommittedEntityTransitionReference("predecessor-1")), None, Vector.empty)

  private def _active: HistoryEntry =
    HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("review.Awaiting")), CorrelationReference("correlation-1"),
      CausationReference("start-1"), Some(DerivedCompositeOccurrenceReference("composite-1")),
      Some(CommittedEntityTransitionReference("predecessor-1")), None)

  private def _suspended: HistoryEntry = _active.copy(sequence = HistorySequence(2L),
    revision = InstanceRevision(2L), suspension = Some(SuspensionBoundary(
      ContinuationIdentity("continuation-1"), InstanceRevision(2L), ContextSnapshotReference("context-1"),
      CompletionReference("complete-1"), EvidenceReference("evidence-1"), CorrelationReference("resume-1"))))

  private def _provider(store: DataStore, configuration: Configuration = _config): WorkflowInstanceDataStorePersistence =
    _take(WorkflowInstanceDataStorePersistence.bindC(store, configuration, Vector(_binding), _collection))

  "Phase 88 Workflow instance datastore provider" should {
    "round trip every persisted fact through independently admitted catalog bindings" in {
      Given("a valid CAM binding and a suspended instance")
      val record = _take(_take(_initial().appendC(initialRevision, _active)).appendC(InstanceRevision(1L), _suspended))
      val bytes = _take(WorkflowInstanceRecordJsonV1.encodeC(record))
      When("the bytes reopen against a freshly admitted catalog")
      val decoded = _take(WorkflowInstanceRecordJsonV1.decodeC(bytes,
        _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_binding)))))
      Then("history, suspension, provenance and current projection remain exact")
      _take(WorkflowInstanceRecordJsonV1.encodeC(decoded)) shouldBe bytes
      decoded.history shouldBe record.history
      decoded.suspension shouldBe record.suspension
      decoded.definition.workflowIdentity shouldBe record.definition.workflowIdentity
      decoded.definition.workflowRevision shouldBe record.definition.workflowRevision
      (decoded.definition eq record.definition) shouldBe false
    }

    "reject wrong schema, unresolved references and inconsistent current history" in {
      Given("valid initial and active record bytes")
      val record = _take(_initial().appendC(initialRevision, _active))
      val json = parse(_take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
      val catalog = _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_binding)))
      val variants = Vector(
        json.deepMerge(Json.obj("schemaVersion" -> Json.fromString("unknown"))),
        json.deepMerge(Json.obj("definition" -> Json.obj("revision" -> Json.fromString("foreign-revision")))),
        json.deepMerge(Json.obj("current" -> Json.obj("revision" -> Json.fromLong(2L)))),
        json.deepMerge(Json.obj("history" -> Json.arr()))
      )
      When("each modified document is decoded without trusting its definition bytes")
      val results = variants.map(value => WorkflowInstanceRecordJsonV1.decodeC(value.noSpaces, catalog))
      Then("none admits a reconstructed definition or divergent state")
      results.forall(_.toOption.isEmpty) shouldBe true
      WorkflowInstanceRecordJsonV1.catalogC(Vector(_binding, _binding)).toOption.nonEmpty shouldBe true
    }

    "consume unused annotations at every instance JSON depth without admitting changed required facts" in {
      Given("an actual suspended record and its independently admitted definition catalog")
      val record = _take(_take(_initial().appendC(initialRevision, _active)).appendC(InstanceRevision(1L), _suspended))
      val json = parse(_take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
      val catalog = _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_binding)))
      def _annotate_(value: Json): Json = value.arrayOrObject(value,
        values => Json.fromValues(values.map(_annotate_)),
        fields => Json.fromJsonObject(fields.mapValues(_annotate_).add("unusedDiagnostic", Json.obj("nextState" -> Json.Null))))
      When("the parsed root, references, current facts, history and suspension carry harmless annotations")
      val decoded = _take(WorkflowInstanceRecordJsonV1.decodeC(_annotate_(json).spaces2, catalog))
      Then("actual typed current facts and complete history retain their meaning")
      decoded.history shouldBe record.history
      decoded.revision shouldBe record.revision
      decoded.suspension shouldBe record.suspension
      _take(WorkflowInstanceRecordJsonV1.encodeC(decoded)) shouldBe _take(WorkflowInstanceRecordJsonV1.encodeC(record))
    }

    "resolve executable references while ignoring legacy integrity and provenance fields" in {
      Given("Spec: docs/spec/workflow-definition-reference-reopening.md; Rule: R1; Example: E1; two separately admitted definitions with equal declared references")
      val original = _binding
      val later = _binding_with_suffix("\n\n")
      val record = _take(_initial(original).appendC(initialRevision, _active))
      When("the admitted record is written with its executable definition reference")
      val json = parse(_take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
      val catalog = _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(later)))
      val variants = Vector(json) ++ Vector(Json.fromString("historical-unused-value"), Json.Null)
        .map(value => json.deepMerge(Json.obj("definition" -> Json.obj("fixtureSha256" -> value,
          "source" -> Json.obj("root" -> Json.obj("line" -> Json.fromInt(999))),
          "producerAbi" -> Json.fromString("old-generator-schema"),
          "producerRevision" -> Json.fromString("old-generator-revision")))))
      Then("new writes omit the unused digest")
      json.hcursor.downField("definition").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision")
      Given("the current wire and historical records contain only inert legacy annotations")
      variants.foreach { value =>
        When("the actual definition reference reopens without a stored fixture checksum")
        val reopened = _take(WorkflowInstanceRecordJsonV1.decodeC(value.noSpaces, catalog))
        Then("the read selects the actual separately admitted definition and retains its history")
        (reopened.definition eq later) shouldBe true
        reopened.history shouldBe record.history
        reopened.revision shouldBe record.revision
        When("the reopened record is written through the current native codec")
        val written = _take(WorkflowInstanceRecordJsonV1.encodeC(reopened))
        Then("the canonical writer retains the current reference and actual history")
        written shouldBe json.noSpaces
      }
      When("an actual reference resolves to two distinct execution definitions")
      val ambiguous = WorkflowInstanceRecordJsonV1.decodeC(json.noSpaces, _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(original, later))))
      Then("resolution rejects ambiguity instead of silently choosing a definition")
      ambiguous.toOption shouldBe None
    }

    "physically reopen SQLite using a formatting-independent admitted definition reference" in {
      Given("Spec: docs/spec/workflow-definition-reference-reopening.md; Rule: R2; Example: E2; an actual file-backed instance and retained suspension")
      val path = _temporary_directory("cncf-reference-reopen-").resolve("workflow.sqlite")
      val record = _initial()
      val first = SqlDataStore.sqlite(path.toString)
      try {
        val writer = _provider(first)
        _take(writer.create(_config, record))
        _take(writer.append(_config, record.identity, initialRevision, _active))
        _take(writer.append(_config, record.identity, InstanceRevision(1L), _suspended))
      } finally _take(first.closeC())
      When("a new physical provider binds the same declared reference from differently formatted ABI text")
      val parsed = _take(CandidateWorkflowAbi.parseC(
        _resource("/workflow/candidate-admission-workflow-abi.json"),
        _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json"))),
        "relocated/workflow.cml"))
      val later = _take(WorkflowInstancePersistence.bindCandidateDefinitionC(parsed.copy(
        workflow = parsed.workflow.copy(rootLine = 999, definitionLine = 1000))))
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val reader = _take(WorkflowInstanceDataStorePersistence.bindC(reopened, _config, Vector(later), _collection))
        val loaded = _take(reader.load(_config, record.identity)).get
        Then("the actual saved history reopens without a content certificate or stale-writer permission")
        (loaded.definition eq later) shouldBe true
        loaded.history shouldBe Vector(_active, _suspended)
        loaded.suspension shouldBe _suspended.suspension
        When("the reopened adapter attempts an append with the stale initial revision")
        val staleappend = reader.append(_config, record.identity, initialRevision, _active)
        Then("the stale writer receives no append permission")
        staleappend.toOption shouldBe None
      } finally _take(reopened.closeC())
    }

    "use canonical in-memory CAS without adding a default store or mutation Operation" in {
      Given("two adapter bindings sharing one canonical datastore")
      val store = DataStore.inMemorySearchable()
      val writer = _provider(store)
      val reader = _provider(store)
      val record = _initial()
      _take(writer.create(_config, record))
      When("one revision append and one stale replay are submitted")
      val accepted = _take(writer.append(_config, record.identity, initialRevision, _active))
      val replay = reader.append(_config, record.identity, initialRevision, _active)
      Then("one immutable history entry is retained and the stale writer changes nothing")
      accepted.revision shouldBe InstanceRevision(1L)
      replay.toOption shouldBe None
      _take(reader.load(_config, record.identity)).get.history shouldBe Vector(_active)
      When("the original instance is created again")
      val duplicateinitial = writer.create(_config, record)
      Then("the duplicate initial Create is refused")
      duplicateinitial.toOption shouldBe None
      When("the accepted instance is created again")
      val duplicateaccepted = writer.create(_config, accepted)
      Then("the duplicate accepted Create is refused")
      duplicateaccepted.toOption shouldBe None
      _take(reader.load(_config, InstanceIdentity("absent"))) shouldBe None
      When("an adapter is bound without canonical storage capability")
      val unavailable = WorkflowInstanceDataStorePersistence.bindC(DataStore.noop(), _config, Vector(_binding), _collection)
      Then("the binding is refused without adding a default store")
      unavailable.toOption shouldBe None
    }

    "recover full history and exact ABI after closing and reopening SQLite" in {
      Given("a fresh file-backed canonical SQLite datastore")
      val path = _temporary_directory("cncf-phase88-reopen-").resolve("workflow.sqlite")
      val first = SqlDataStore.sqlite(path.toString)
      val record = _initial()
      val resumed = _active.copy(sequence = HistorySequence(3L), revision = InstanceRevision(3L),
        causation = CausationReference("resume-actual"))
      val completed = resumed.copy(sequence = HistorySequence(4L), revision = InstanceRevision(4L),
        lifecycle = Lifecycle.Completed, currentProgression = None, causation = CausationReference("complete-actual"))
      When("native append consumes four adjacent entries including suspension, resumption and completion")
      try {
        val writer = _provider(first)
        _take(writer.create(_config, record))
        _take(writer.append(_config, record.identity, initialRevision, _active))
        _take(writer.append(_config, record.identity, InstanceRevision(1L), _suspended))
        _take(writer.append(_config, record.identity, InstanceRevision(2L), resumed))
        val appended = _take(writer.append(_config, record.identity, InstanceRevision(3L), completed))
        Then("native append retains the four adjacent actual entries before physical close")
        appended.history shouldBe Vector(_active, _suspended, resumed, completed)
      } finally _take(first.closeC())
      When("another SQLite resource and independently admitted adapter reopen the file")
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val loaded = _take(_provider(reopened).load(_config, record.identity)).get
        Then("current facts and all four immutable entries are rehydrated rather than fabricated")
        loaded.revision shouldBe InstanceRevision(4L)
        loaded.history shouldBe Vector(_active, _suspended, resumed, completed)
        loaded.suspension shouldBe None
        loaded.lifecycle shouldBe Lifecycle.Completed
        loaded.definition.validateC.toOption.nonEmpty shouldBe true
        When("a stale original CAS and a terminal successor attempt are submitted")
        val failures = Vector(_provider(reopened).append(_config, record.identity, initialRevision, _active),
          _provider(reopened).append(_config, record.identity, loaded.revision,
            _active.copy(sequence = HistorySequence(5L), revision = InstanceRevision(5L))))
        Then("neither guarded writer changes the actual retained terminal history")
        failures.forall(_.toOption.isEmpty) shouldBe true
        _take(_provider(reopened).load(_config, record.identity)).get.history shouldBe loaded.history
      } finally _take(reopened.closeC())
    }

    "omit copied policy and reopen legacy rows under the current bound policy without rewriting them" in {
      Given("a real SQLite initial instance and independently selected current retention policy")
      val db = _temporary_directory("cncf-policy-reopen-").resolve("workflow.sqlite")
      val first = SqlDataStore.sqlite(db.toString)
      val record = _initial()
      val changed = _config.copy(retentionPolicy = RetentionPolicy("changed"))
      val entryid = DataStore.StringEntryId(record.identity.value)
      var initialfields = Set.empty[String]
      try {
        _take(_provider(first).create(changed, record))
        initialfields = _take(first.load(_collection, entryid)).get.fields.map(_.key).toSet
        _take(first.update(_collection, entryid, Record.dataAuto("configurationPayload" -> "legacy arbitrary policy")))
      } finally _take(first.closeC())
      When("a new physical provider uses current policy and appends through a legacy Configuration argument")
      val reopened = SqlDataStore.sqlite(db.toString)
      try {
        val adapter = _provider(reopened, changed)
        val loaded = _take(adapter.load(_config, record.identity)).get
        val appended = _take(adapter.append(_config, record.identity, initialRevision, _active))
        val stale = adapter.append(_config, record.identity, initialRevision, _active)
        val retained = _take(reopened.load(_collection, entryid)).get
        Then("current binding owns access, the old copy is inert and actual stale revision is still refused")
        initialfields should not contain "configurationPayload"
        loaded.identity shouldBe record.identity
        loaded.revision shouldBe initialRevision
        appended.history shouldBe Vector(_active)
        appended.revision shouldBe InstanceRevision(1L)
        stale.toOption shouldBe None
        retained.getString("configurationPayload") shouldBe Some("legacy arbitrary policy")
      } finally _take(reopened.closeC())
    }

    "reject corrupt indexed revision before a mutation" in {
      Given("one stored initial record with an inconsistent physical revision")
      val store = DataStore.inMemorySearchable()
      val record = _initial()
      _take(_provider(store).create(_config, record))
      _take(store.update(_collection, DataStore.StringEntryId(record.identity.value),
        Record.dataAuto("cncf_revision" -> 7L)))
      When("load and append encounter the actual indexed revision mismatch")
      val loaded = _provider(store).load(_config, record.identity)
      val appended = _provider(store).append(_config, record.identity, initialRevision, _active)
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
        val adapter = _provider(store)
        val record = _initial()
        _take(adapter.create(_config, record))
        val entryid = DataStore.StringEntryId(record.identity.value)
        _take(store.update(_collection, entryid, change))
        val before = _take(store.load(_collection, entryid)).get
        When("the malformed row is loaded and an append is attempted")
        val loaded = adapter.load(_config, record.identity)
        val appended = adapter.append(_config, record.identity, initialRevision, _active)
        val retained = _take(store.load(_collection, entryid)).get
        val uncatalogued = WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector.empty, _collection)
        Then("neither decoding failure nor absent authority can rewrite the row")
        loaded.toOption shouldBe None
        appended.toOption shouldBe None
        retained shouldBe before
        uncatalogued.toOption shouldBe None
      }
    }

    "admit at most one concurrent SQLite writer for the same expected revision" in {
      Given("two canonical SQLite resources sharing one initial record")
      val path = _temporary_directory("cncf-phase88-cas-").resolve("workflow.sqlite")
      val left = SqlDataStore.sqlite(path.toString)
      val right = SqlDataStore.sqlite(path.toString)
      val pool = Executors.newFixedThreadPool(2)
      try {
        val record = _initial()
        val a = _provider(left)
        val b = _provider(right)
        _take(a.create(_config, record))
        val gate = new CountDownLatch(1)
        val futures = Vector(a, b).map { adapter =>
          pool.submit(new java.util.concurrent.Callable[Consequence[InstanceRecord]] {
            def call(): Consequence[InstanceRecord] = {
              if (!gate.await(10L, TimeUnit.SECONDS)) throw new IllegalStateException("CAS test gate timed out")
              adapter.append(_config, record.identity, initialRevision, _active)
            }
          })
        }
        When("both resources try to consume revision zero")
        gate.countDown()
        val results = futures.map(_.get(20L, TimeUnit.SECONDS))
        Then("only one append succeeds and reopening observes one unchanged history entry")
        results.count(_.toOption.nonEmpty) shouldBe 1
        val loaded = _take(a.load(_config, record.identity)).get
        loaded.revision shouldBe InstanceRevision(1L)
        loaded.history shouldBe Vector(_active)
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(20L, TimeUnit.SECONDS)
        _take(left.closeC())
        _take(right.closeC())
      }
    }
  }
}
