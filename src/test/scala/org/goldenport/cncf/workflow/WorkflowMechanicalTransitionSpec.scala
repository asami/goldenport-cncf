package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/**
 * @since   Oct.  2, 2026
 * @version Oct. 21, 2026
 */
final class WorkflowMechanicalTransitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private def _take[A](value: Consequence[A]): A = {
     value match {
    case Consequence.Success(result) => result
    case other => fail(s"Mechanical fixture failed: $other")
  }
  }
  private def _resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val _producer = _take(CandidateAdmissionProducerAbi.parseC(_resource("/workflow/candidate-admission-producer-abi.json")))
  private val _definition = _take(bindCandidateDefinitionC(_take(CandidateWorkflowAbi.parseC(
    _resource("/workflow/candidate-admission-workflow-abi.json").trim, _producer, "modeler/candidate-admission-workflow.cml"))))
  private val _config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("mechanical")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("mechanical")))
  private val _component = ComponentId("org.test.Mechanical")
  private val _operation = StateMachineOperationIdentity("workflow", "advance")
  private val _initial = InstanceRecord(InstanceIdentity("instance"), _definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"),
    None, None, None, Vector.empty)
  private val _active = _take(_initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
    Lifecycle.Active, Some(ProgressionReference("planning")), _initial.correlation, _initial.causation, None, None, None)))
  private val _entry = HistoryEntry(HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
    Some(ProgressionReference("planned")), _active.correlation, CausationReference("checkpoint"), None, None, None)
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "planning.v1"
    def encode(value: String) = Json.fromString(value)
    def decode(value: Json) = value.asString.toRight("Expected planning text")
  }
  private val _catalog = _take(WorkflowInstanceRecordJsonV1.catalogC(Vector(_definition)))
  private val _intent = Intent(_config, Reference("checkpoint", "1"), _active, _active.revision, _entry,
    TypedValue(_codec.typeIdentity, "known planning"))
  private val _capacity_metadata = afterWord("in spec:workflow-mechanical-receipt-capacity, example:E2, rules:R1,R3,R4, phase:capacity-repair")
  private val _sqlite_capacity_metadata = afterWord("in spec:workflow-mechanical-receipt-capacity, example:E3, rules:R3,R4,R5, phase:capacity-repair")

  "Mechanical transition data admission" should {
    "E2 retain the one MiB default and admit an explicitly larger complete receipt" must _capacity_metadata {
      "when the full payload exceeds the default capacity" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3,R4; Example: E2; a complete receipt with more than one MiB of payload")
        val limits = _take(WorkflowMechanicalReceiptLimits.createC(16 * 1024 * 1024))
        val receipt = _take(admitC(_component, _operation,
          _intent.copy(payload = TypedValue(_codec.typeIdentity, "x" * 1048576))))
        When("default and explicitly selected writers and readers consume the same complete receipt")
        val refused = WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec)
        val encoded = _take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec, limits))
        val recovered = _take(WorkflowMechanicalTransitionJsonV1.decodeC(encoded, _catalog, _codec, limits))
        val lowerreader = WorkflowMechanicalTransitionJsonV1.decodeC(encoded, _catalog, _codec)
        Then("only the selected larger policy admits the entire unchanged envelope")
        refused shouldBe a[Consequence.Failure[?]]
        encoded.getBytes(UTF_8).length should be > 1048576
        recovered shouldBe receipt
        lowerreader shouldBe a[Consequence.Failure[?]]
      }
    }

    "E2 count escaped multibyte payload and both complete histories at the inclusive byte boundary" must _capacity_metadata {
      "when the exact full envelope capacity is selected" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R3,R4; Example: E2; generated escaped UTF-8 history and payload data")
        val property = Prop.forAll(Gen.choose(1, 80)) { count =>
          val text = "日本語\"\\\n" * count
          val cause = CausationReference(text)
          val current = _active.copy(causation = cause, history = _active.history.map(_.copy(causation = cause)))
          val receipt = _take(admitC(_component, _operation, _intent.copy(current = current,
            entry = _entry.copy(causation = cause), payload = TypedValue(_codec.typeIdentity, text))))
          val encoded = _take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec))
          val bytes = encoded.getBytes(UTF_8).length
          val exact = _take(WorkflowMechanicalReceiptLimits.createC(bytes))
          val lower = _take(WorkflowMechanicalReceiptLimits.createC(bytes - 1))
          bytes > encoded.length &&
            WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec, exact).toOption.contains(encoded) &&
            WorkflowMechanicalTransitionJsonV1._encode_c_unchecked(receipt, _codec, exact).toOption.contains(encoded) &&
            WorkflowMechanicalTransitionJsonV1.decodeC(encoded, _catalog, _codec, exact).toOption.contains(receipt) &&
            !WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec, lower).isSuccess &&
            !WorkflowMechanicalTransitionJsonV1._encode_c_unchecked(receipt, _codec, lower).isSuccess &&
            !WorkflowMechanicalTransitionJsonV1.decodeC(encoded, _catalog, _codec, lower).isSuccess
        }
        When("the complete-envelope UTF-8 capacity property is checked")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
        Then("the exact byte count succeeds and one byte less refuses on every encode and decode path")
        checked.passed shouldBe true
      }
    }

    "E2 retain five-argument configuration construction and resolve optional byte limits" must _capacity_metadata {
      "when existing callers supply the original five policies" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E2; original constructor and companion calls")
        val constructed = new Configuration(_config.workflowStore, _config.migrationPolicy, _config.retentionPolicy,
          _config.leasePolicy, _config.delivery)
        When("the configurations are validated")
        val accepted = _take(constructed.validateC)
        val endpoints = Vector(1, WorkflowMechanicalReceiptLimits.maximumBytes).map { bytes =>
          _take(_config.copy(mechanicalReceiptMaxBytes = Some(bytes)).validateC)
        }
        Then("both existing construction forms retain None and resolve one MiB while explicit endpoints resolve exactly")
        accepted.mechanicalReceiptMaxBytes shouldBe None
        _config.mechanicalReceiptMaxBytes shouldBe None
        _take(accepted.mechanicalReceiptLimitsC).maxBytes shouldBe 1048576
        _take(_config.mechanicalReceiptLimitsC).maxBytes shouldBe 1048576
        endpoints.map(value => _take(value.mechanicalReceiptLimitsC).maxBytes) shouldBe
          Vector(1, WorkflowMechanicalReceiptLimits.maximumBytes)
      }
    }

    "E2 refuse invalid optional byte limits before binding any mechanical effects" must _capacity_metadata {
      "when the container is null or an explicit integer is outside the finite range" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E2; an existing native root and no checkpoint")
        val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "invalidcapacity", "instances"))
        val checkpoints = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "invalidcapacity", "checkpoints"))
        val owner = _take(DataStoreComponentOwner.create(_component.name))
        val store = new DataStore.InMemoryDataStore(CommitRecorder.noop)
        val roots = _take(WorkflowInstanceDataStorePersistence.bindC(store, _config, Vector(_definition), instances))
        _take(roots.create(_config, _initial))
        val instanceid = DataStore.StringEntryId(_initial.identity.value)
        val checkpointid = DataStore.StringEntryId(_take(keyC(_intent.reference)))
        val before = _take(store.load(instances, instanceid))
        val invalid = Vector[Option[Int]](null, Some(0), Some(-1),
          Some(WorkflowMechanicalReceiptLimits.maximumBytes + 1), Some(Int.MaxValue))
        When("configuration validation, limit resolution and adapter binding consume each invalid selection")
        val results = invalid.map { bytes =>
          val config = _config.copy(mechanicalReceiptMaxBytes = bytes)
          (config.validateC, config.mechanicalReceiptLimitsC,
            WorkflowMechanicalTransitionDataStorePersistence.bindC(store, config, _component,
              _operation, Vector(_definition), instances, checkpoints, _codec, owner))
        }
        val after = _take(store.load(instances, instanceid))
        val absent = _take(store.load(checkpoints, checkpointid))
        Then("every invalid selection fails as a typed result and neither native row changes")
        results.foreach { case (validated, resolved, bound) =>
          validated shouldBe a[Consequence.Failure[?]]
          resolved shouldBe a[Consequence.Failure[?]]
          bound shouldBe a[Consequence.Failure[?]]
        }
        after shouldBe before
        absent shouldBe None
      }
    }

    "E3 refuse an oversized SQLite preparation atomically and physically reopen under the selected policy" must _sqlite_capacity_metadata {
      "when one complete receipt exceeds the reader and writer default" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R3,R4,R5; Example: E3; native SQLite root and checkpoint collections")
        val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanicalcapacity", "instances"))
        val checkpoints = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanicalcapacity", "checkpoints"))
        val owner = _take(DataStoreComponentOwner.create(_component.name))
        val config = _config.copy(mechanicalReceiptMaxBytes = Some(16 * 1024 * 1024))
        val intent = _intent.copy(configuration = config, payload = TypedValue(_codec.typeIdentity, "日本語\"\\\n" * 180000))
        val directory = Path.of("target", "phase105", "work", "mechanical")
        Files.createDirectories(directory)
        val path = Files.createTempDirectory(directory, "capacity-").resolve("workflow.sqlite")
        val checkpointid = DataStore.StringEntryId(_take(keyC(intent.reference)))
        val instanceid = DataStore.StringEntryId(_initial.identity.value)
        val first = SqlDataStore.sqlite(path.toString)
        try {
          val roots = _take(WorkflowInstanceDataStorePersistence.bindC(first, config, Vector(_definition), instances))
          _take(roots.create(config, _initial))
          _take(roots.append(config, _initial.identity, initialRevision, _active.history.head))
          val before = _take(first.load(instances, instanceid))
          val lower = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(first, _config, _component,
            _operation, Vector(_definition), instances, checkpoints, _codec, owner))
          When("the default adapter prepares the oversized complete receipt")
          val rejected = lower.prepareC(intent)
          val after = _take(first.load(instances, instanceid))
          val absent = _take(first.load(checkpoints, checkpointid))
          Then("refusal leaves the full native root and checkpoint unchanged")
          rejected shouldBe a[Consequence.Failure[?]]
          after shouldBe before
          absent shouldBe None

          When("the same native pair is prepared and committed with the explicit sixteen MiB configuration")
          val selected = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(first, config, _component,
            _operation, Vector(_definition), instances, checkpoints, _codec, owner))
          val committed = _take(_take(selected.prepareC(intent)).commitC())
          val lowerread = lower.loadRetainedC(intent.reference)
          Then("the full payload commits once and the lower reader refuses the retained row")
          committed.payload shouldBe intent.payload
          committed.next.history shouldBe _active.history :+ _entry
          lowerread shouldBe a[Consequence.Failure[?]]
        } finally _take(first.closeC())

        When("a new physical SQLite provider opens the file under the same selected configuration")
        val reopened = SqlDataStore.sqlite(path.toString)
        try {
          val selected = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(reopened, config, _component,
            _operation, Vector(_definition), instances, checkpoints, _codec, owner))
          val recovered = _take(selected.loadRetainedC(intent.reference)).get
          val lower = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(reopened, _config, _component,
            _operation, Vector(_definition), instances, checkpoints, _codec, owner))
          val lowerread = lower.loadRetainedC(intent.reference)
          Then("physical reopen recovers the entire receipt and a lower policy still refuses without relaxation")
          recovered._1.payload shouldBe intent.payload
          recovered._1.current.history shouldBe _active.history
          recovered._1.next.history shouldBe _active.history :+ _entry
          recovered._2 shouldBe recovered._1.next
          lowerread shouldBe a[Consequence.Failure[?]]
        } finally _take(reopened.closeC())
      }
    }

    "roundtrip one exact ordinary append without any issued interaction or AI result" in {
      Given("an active unsuspended instance, typed planning payload and one explicit next entry")
      val receipt = _take(admitC(_component, _operation, _intent))
      When("the separate closed mechanical codec writes and reads the complete receipt")
      val encoded = _take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec))
      val recovered = _take(WorkflowMechanicalTransitionJsonV1.decodeC(encoded, _catalog, _codec))
      Then("the original and exact appended instance and complete payload survive unchanged")
      recovered shouldBe receipt
      recovered.next.history shouldBe _active.history :+ _entry
      recovered.next.suspension shouldBe None
    }

    "encode unambiguous explicit keys and reject control, surrogate and excessive identities" in {
      Given("references containing delimiters, literal percent escapes and UTF-8 text")
      val refs = Vector(Reference("a@b", "1:2"), Reference("a%40b", "1:2"), Reference("日本語", "1"))
      When("checkpoint keys are derived without hashing or selecting another identity")
      val keys = refs.map(reference => _take(keyC(reference)))
      Then("delimiter escaping is canonical and distinct while invalid or oversized references refuse")
      keys.head shouldBe "mechanical:a%40b@1%3A2"
      keys.distinct.size shouldBe refs.size
      Given("control, surrogate and oversized reference variants")
      Vector(Reference("", "1"), Reference(" leading", "1"), Reference("a\u0000b", "1"),
        Reference("\uD800", "1"), Reference("x" * 1024, "1"), null)
        .foreach { reference =>
          When("a key is requested for the invalid actual reference")
          val rejected = keyC(reference)
          Then("the invalid or oversized reference is refused")
          rejected.isSuccess shouldBe false
        }
    }

    "refuse initial, terminal, suspended, stale and noncontiguous transitions" in {
      Given("an ordinary mechanical intent with invalid lifecycle, revision or history variants")
      val variants = Vector(_intent.copy(current = _initial, expectedRevision = initialRevision),
        _intent.copy(expectedRevision = InstanceRevision(0L)),
        _intent.copy(entry = _entry.copy(sequence = HistorySequence(3L))),
        _intent.copy(entry = _entry.copy(lifecycle = Lifecycle.Completed, currentProgression = None)),
        _intent.copy(entry = _entry.copy(correlation = CorrelationReference("foreign"))),
        _intent.copy(current = _active.copy(history = Vector(_active.history.head.copy(causation = null)))),
        _intent.copy(entry = _entry.copy(suspension = Some(SuspensionBoundary(ContinuationIdentity("work"),
          InstanceRevision(2L), ContextSnapshotReference("context"), CompletionReference("completion"),
          EvidenceReference("evidence"), CorrelationReference("resume"))))))
      When("the host admits only ordinary active unsuspended one-append progress")
      val admitted = variants.map(value => admitC(_component, _operation, value))
      Then("every invalid variant refuses without an admitted mechanical receipt")
      admitted.foreach(_.isSuccess shouldBe false)
    }

    "consume all-depth receipt annotations while refusing malformed consumed fields and oversized data" in {
      Given("a correctly encoded receipt and an independently admitted definition catalog")
      val receipt = _take(admitC(_component, _operation, _intent))
      val json = parse(_take(WorkflowMechanicalTransitionJsonV1.encodeC(receipt, _codec))).toOption.get
      val foreignpayload = json.hcursor.downField("payload").focus.get.mapObject(_.add("typeIdentity", Json.fromString("foreign")))
      def _annotate_(value: Json): Json = value.arrayOrObject(value,
        entries => Json.arr(entries.map(_annotate_)*),
        obj => Json.fromJsonObject(obj.mapValues(_annotate_).add("unused", Json.obj("diagnostic" -> Json.True))))
      val suspended = _take(_active.appendC(_active.revision, _entry.copy(suspension = Some(SuspensionBoundary(
        ContinuationIdentity("work"), _entry.revision, ContextSnapshotReference("context"),
        CompletionReference("complete"), EvidenceReference("evidence"), CorrelationReference("resume"))))))
      val suspendedreceipt = receipt.copy(current = suspended, next = suspended)
      val suspendedjson = parse(_take(WorkflowMechanicalTransitionJsonV1.encodeC(suspendedreceipt, _codec))).toOption.get
      val malformed = Vector(json.mapObject(_.remove("reference")),
        json.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))),
        json.mapObject(_.add("component", Json.fromInt(1))),
        json.mapObject(_.add("reference", Json.obj("identity" -> Json.fromString("checkpoint")))),
        json.mapObject(_.add("operation", Json.obj("service" -> Json.fromString("workflow"), "operation" -> Json.False))),
        json.mapObject(_.add("payload", foreignpayload)),
        json.mapObject(_.add("current", Json.Null)))
      When("native readers consume required fields and ignore annotations at every existing object depth")
      val annotated = _take(WorkflowMechanicalTransitionJsonV1.decodeC(_annotate_(json).spaces2, _catalog, _codec))
      val annotatedsuspension = _take(WorkflowMechanicalTransitionJsonV1.decodeC(_annotate_(suspendedjson).spaces2, _catalog, _codec))
      val decoded = malformed.map(value => WorkflowMechanicalTransitionJsonV1.decodeC(value.noSpaces, _catalog, _codec))
      val oversized = WorkflowMechanicalTransitionJsonV1.encodeC(
        receipt.copy(payload = TypedValue(_codec.typeIdentity, "x" * (1024 * 1024))), _codec)
      Then("annotations are inert while malformed required data and oversized payloads refuse")
      annotated shouldBe receipt
      annotatedsuspension shouldBe suspendedreceipt
      decoded.foreach(_.isSuccess shouldBe false)
      oversized.isSuccess shouldBe false
    }

    "commit and physically reopen a borrowed SQLite mechanical checkpoint with inert envelope annotations" in {
      Given("explicit instance/checkpoint EntityStore collections and an active native instance")
      val instances = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanical", "instances"))
      val checkpoints = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanical", "checkpoints"))
      val owner = _take(DataStoreComponentOwner.create(_component.name))
      val directory = Path.of("target", "phase105", "work", "mechanical")
      Files.createDirectories(directory)
      val path = Files.createTempDirectory(directory, "spec-").resolve("workflow.sqlite")
      val first = SqlDataStore.sqlite(path.toString)
      val checkpointid = DataStore.StringEntryId(_take(keyC(_intent.reference)))
      try {
        val instanceadapter = _take(WorkflowInstanceDataStorePersistence.bindC(first, _config, Vector(_definition), instances))
        _take(instanceadapter.create(_config, _initial))
        val current = _take(instanceadapter.append(_config, _initial.identity, initialRevision, _active.history.head))
        val adapter = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(first, _config, _component,
          _operation, Vector(_definition), instances, checkpoints, _codec, owner))
        When("the borrowed adapter prepares and performs its one guarded native commit")
        val prepared = _take(adapter.prepareC(_intent.copy(current = current)))
        val committed = _take(prepared.commitC())
        val stale = adapter.prepareC(_intent.copy(current = current))
        Then("actual history advances once and the original expected revision cannot be reused")
        committed.next.history shouldBe current.history :+ _entry
        stale.toOption shouldBe None
        When("the retained physical envelope receives unused host policy and diagnostic fields")
        _take(first.update(checkpoints, checkpointid,
          Record.dataAuto("configurationPayload" -> "later unused configuration", "annotation" -> "retained diagnostic")))
        val annotated = _take(first.load(checkpoints, checkpointid)).get
        Then("the unused retained metadata is present before physical close")
        annotated.getString("configurationPayload") shouldBe Some("later unused configuration")
        annotated.getString("annotation") shouldBe Some("retained diagnostic")
      } finally _take(first.closeC())

      When("a new physical provider reopens the explicit file under current host policy")
      val reopened = SqlDataStore.sqlite(path.toString)
      try {
        val adapter = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(reopened,
          _config.copy(retentionPolicy = RetentionPolicy("current")), _component, _operation, Vector(_definition),
          instances, checkpoints, _codec, owner))
        val recovered = _take(adapter.loadRetainedC(_intent.reference)).get
        val physical = _take(reopened.load(checkpoints, checkpointid)).get
        Then("actual retained receipt and current instance are consumed without policy-copy certification")
        recovered._1.current.history shouldBe _active.history
        recovered._1.next.history shouldBe _active.history :+ _entry
        recovered._2 shouldBe recovered._1.next
        physical.getString("configurationPayload") shouldBe Some("later unused configuration")

        Given("borrowed readback views over the same physical SQLite rows with consumed-envelope distortions")
        val revisionfield = WorkflowInstanceDataStorePersistence.revisionField
        val payloadfield = WorkflowMechanicalTransitionDataStorePersistence.payloadField
        val receiptjson = parse(_take(WorkflowInstanceDataStorePersistence.storageJsonC(physical.getString(payloadfield).get))).toOption.get
        def _payload_row_(json: Json): Record = Record(physical.fields.filterNot(_.key == payloadfield) ++
          Record.dataAuto(payloadfield -> WorkflowInstanceDataStorePersistence.storageText(json.noSpaces)).fields)
        def _read_row_(row: Record): Consequence[Option[(Receipt[String], InstanceRecord)]] = {
          val view = new DataStore.InMemoryDataStore(CommitRecorder.noop) {
            override def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ExecutionContext):
                Consequence[Option[Record]] = {
              if (collection == checkpoints && id == checkpointid) Consequence.success(Some(row))
              else reopened.load(collection, id)
            }
          }
          val reader = _take(WorkflowMechanicalTransitionDataStorePersistence.bindC(view, _config, _component,
            _operation, Vector(_definition), instances, checkpoints, _codec, owner))
          reader.loadRetainedC(_intent.reference)
        }
        val variants = Vector("id", revisionfield, payloadfield).map { field =>
          Record(physical.fields :+ physical.fields.find(_.key == field).get)
        } ++ Vector(
          Record(physical.fields.filterNot(_.key == "id")),
          Record(physical.fields.filterNot(_.key == revisionfield) ++ Record.dataAuto(revisionfield -> 2L).fields),
          Record(physical.fields.filterNot(_.key == payloadfield) ++ Record.dataAuto(payloadfield -> "not-native").fields),
          _payload_row_(receiptjson.mapObject(_.add("component", Json.fromString("foreign.Component")))),
          _payload_row_(receiptjson.deepMerge(Json.obj("reference" -> Json.obj("revision" -> Json.fromString("foreign"))))),
          _payload_row_(receiptjson.deepMerge(Json.obj("operation" -> Json.obj("operation" -> Json.fromString("foreign"))))),
          _payload_row_(receiptjson.deepMerge(Json.obj("payload" -> Json.obj("typeIdentity" -> Json.fromString("foreign"))))),
          _payload_row_(receiptjson.deepMerge(Json.obj("current" -> Json.obj("definition" ->
            Json.obj("revision" -> Json.fromString("unresolved")))))))
        When("unused native configuration and annotation fields are duplicated")
        val annotated = _read_row_(Record(physical.fields ++ Record.dataAuto("configurationPayload" -> "other unused",
          "annotation" -> "second diagnostic").fields))
        Then("unused physical metadata remains inert and cannot alter the consumed receipt")
        _take(annotated).get shouldBe recovered
        When("native retained readers consume ambiguous or malformed required envelope fields")
        val rejected = variants.map(row => _read_row_(row).toOption)
        Then("consumed ambiguity, native revision/payload corruption and foreign owner reject")
        rejected.foreach(_ shouldBe None)
      } finally _take(reopened.closeC())
    }

    "read historical records as inert data without reproducing or proving their transition" in {
      Given("a receipt carrying individually valid native records that are not a one-append pair")
      val admitted = _take(admitC(_component, _operation, _intent))
      val property = Prop.forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { cause =>
        val historic = _active.copy(causation = CausationReference(cause),
          history = _active.history.map(_.copy(causation = CausationReference(cause))))
        val saved = admitted.copy(next = historic)
        WorkflowMechanicalTransitionJsonV1.encodeC(saved, _codec)
          .flatMap(WorkflowMechanicalTransitionJsonV1.decodeC(_, _catalog, _codec)).toOption.contains(saved)
      }
      When("the codec stores and reloads this historical data")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("raw record data survives; decoding neither executes nor certifies an append")
      checked.passed shouldBe true
    }
  }
}
