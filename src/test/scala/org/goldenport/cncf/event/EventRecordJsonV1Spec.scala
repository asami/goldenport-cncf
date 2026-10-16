package org.goldenport.cncf.event

import java.time.Instant
import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.statemachine.*
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Sep. 29, 2026
 *  version Sep. 30, 2026
 * @version Oct. 12, 2026
 */
final class EventRecordJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given ExecutionContext = ExecutionContext.create()
  private val _instant = Instant.parse("2026-09-29T01:02:03.123456789Z")
  private val _events = DataStore.CollectionId("event_records_v1")
  private val _roots = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "event", "root"))
  private val _next = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "event", "next"))
  private val _entry = DataStore.StringEntryId("root")
  private def _take[A](result: Consequence[A]): A = {
     result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  }
  private def _event(payload: Map[String, Any] = Map.empty): EventRecord =
    EventRecord(EventId("test", "event", Some(_instant), Some("issued-id")),
      "transition.fact", "stored-fact", payload, Map("traceId" -> "opaque"),
      _instant, true, EventRecord.Status.Stored, EventLane.Transactional, Long.MaxValue)
  private def _json = _take(EventRecordJsonV1.decodeC(EventRecordJsonV1.encode(_event())))
  private def _plan(effect: EntityVersionedSideEffect) = {
    val owner = _take(DataStoreComponentOwner.create("event-codec"))
    val root = _take(DataStoreConditionalRoot.create(owner, _roots, _entry,
      "revision", Some(EntityRevision.INITIAL), Vector.empty,
      Record.dataAuto("state" -> "suspended"), _take(EntityRevision.createC(2L))))
    _take(DataStoreConditionalTransitionPlan.create(root,
      DataStoreConditionalSuccessor.Create(owner, _next, _entry, "revision",
        Record.dataAuto("revision" -> 1L)), Vector(effect)))
  }
  private def _database = {
    val directory = Path.of("target", "phase88", "work", "event-record-codec")
    Files.createDirectories(directory)
    Files.createTempDirectory(directory, "spec-").resolve("events.sqlite")
  }
  private def _with_store(backend: String)(work: (DataStore & EntityConditionalTransitionDataStore) => Unit): Unit = {
    val store: DataStore & EntityConditionalTransitionDataStore =
      if (backend == "memory") new DataStore.InMemoryDataStore(CommitRecorder.noop)
      else SqlDataStore.sqlite(_database.toString)
    try work(store) finally store match {
      case sql: SqlDataStore => _take(sql.closeC())
      case _ => ()
    }
  }

  "Canonical EventRecord encoding" should {
    "retain exact identity, nanosecond time, lane, flags, long precision and payload types" in {
      Given("an assigned event with distinct numeric runtime types and nested immutable values")
      val event = _event(Map("int" -> 1, "long" -> 1L, "large" -> Long.MaxValue,
        "decimal" -> BigDecimal("1.2300"), "unicode" -> "日本語", "empty" -> "",
        "nested" -> Vector(true, Map("value" -> "exact"))))
      When("canonical bytes are encoded and recovered")
      val decoded = _take(EventRecordJsonV1.decodeC(EventRecordJsonV1.encode(event)))
      Then("all event facts and runtime numeric types are exact without regeneration")
      decoded shouldBe event
      decoded.payload("int") shouldBe a[java.lang.Integer]
      decoded.payload("long") shouldBe a[java.lang.Long]
      decoded.payload("decimal").asInstanceOf[BigDecimal].bigDecimal.scale shouldBe 4
      decoded._delivery_lookup_key shouldBe None
      decoded.id.value shouldBe event.id.value
      val transient = event.copy(persistent = false, lane = EventLane.NonTransactional)
      _take(EventRecordJsonV1.decodeC(EventRecordJsonV1.encode(transient))) shouldBe transient
    }
    "produce canonical bytes independently of map insertion order" in {
      Given("identical event facts with reversed payload and attribute insertion order")
      val first = _event(Map("a" -> 1, "b" -> "two")).copy(attributes = Map("a" -> "1", "b" -> "2"))
      val second = first.copy(payload = Map("b" -> "two", "a" -> 1), attributes = Map("b" -> "2", "a" -> "1"))
      When("both values are encoded")
      val bytes = EventRecordJsonV1.encode(first)
      Then("the encoding is identical and stable after decode")
      EventRecordJsonV1.encode(second) shouldBe bytes
      EventRecordJsonV1.encode(_take(EventRecordJsonV1.decodeC(bytes))) shouldBe bytes
    }
    "consume equivalent JSON formatting and final parsed members as actual typed facts" in {
      Given("a valid issued event and equivalent whitespace, property order and string escapes")
      val original = _event(Map("long" -> 7L, "decimal" -> BigDecimal("1.2300")))
      val bytes = EventRecordJsonV1.encode(original)
      val reordered = Json.fromJsonObject(io.circe.JsonObject.fromIterable(
        parse(bytes).toOption.get.asObject.get.toVector.reverse)).spaces2
      val representations = Vector(" \n" + bytes + "\n ", reordered,
        bytes.replace("transition.fact", "transition\\u002efact"),
        "{\"schemaVersion\":\"obsolete\"," + bytes.drop(1))
      When("the actual parsed event and storage representations are consumed")
      val decoded = representations.map(raw => _take(EventRecordJsonV1.decodeC(raw)))
      val stored = _take(EventRecordJsonV1.decodeStorageC("cncf.event-record.v1:" + representations.head))
      Then("all representations retain typed content and canonical writing stays deterministic")
      decoded.foreach(_ shouldBe original)
      stored shouldBe original
      decoded.foreach(value => value.payload("decimal").asInstanceOf[BigDecimal].bigDecimal.scale shouldBe 4)
      decoded.map(EventRecordJsonV1.encode).distinct shouldBe Vector(bytes)
    }
    "reject corrupt, unknown and invalid final parsed wire facts" in {
      Given("one admitted canonical encoding")
      val bytes = EventRecordJsonV1.encode(_event())
      val json = parse(bytes).toOption.get
      val invalid = Vector(null, "", bytes.dropRight(1),
        bytes.replace("cncf.event-record.v1", "cncf.event-record.v2"),
        bytes.replace("\"stored\"", "\"unknown\""),
        bytes.replace("\"transactional\"", "\"unknown\""),
        bytes.replace(Long.MaxValue.toString, "0"),
        json.mapObject(_.add("unknown", Json.True)).noSpaces,
        bytes.dropRight(1) + ",\"schemaVersion\":\"wrong\"}")
      val stored = EventRecordJsonV1.encodeStorage(_event())
      val invalidstored = Vector(null, bytes, stored.replace("cncf.event-record.v1:", "unsupported:"))
      When("each representation is decoded")
      val results = invalid.map(raw => EventRecordJsonV1.decodeC(raw).toOption)
      val storageresults = invalidstored.map(raw => EventRecordJsonV1.decodeStorageC(raw).toOption)
      Then("unsupported or malformed consumed values never recover a fact")
      results.foreach(_ shouldBe None)
      storageresults.foreach(_ shouldBe None)
      _json.sequence shouldBe Long.MaxValue
    }
    "reject invalid issued identities, bounded values and numeric overflow directly from parsed input" in {
      Given("an event object whose consumed fields and tagged payload values can be varied independently")
      val root = parse(EventRecordJsonV1.encode(_event())).toOption.get
      val id = root.hcursor.downField("id").focus.get
      def _field_(key: String, value: Json): Json = root.mapObject(_.add(key, value))
      def _payload_(value: Json): Json = _field_("payload", Json.obj(
        "type" -> Json.fromString("map"), "value" -> Json.obj("value" -> value)))
      def _tag_(kind: String, value: Json): Json = Json.obj("type" -> Json.fromString(kind), "value" -> value)
      val string = _tag_("string", Json.fromString("leaf"))
      val nested = (1 to 17).foldLeft(string)((value, _) => _tag_("vector", Json.arr(value)))
      val nodes = _tag_("vector", Json.arr(Vector.fill(256)(
        _tag_("vector", Json.arr(Vector.fill(16)(string)*)))*))
      val invalid = Vector(
        _field_("id", id.mapObject(_.add("timestamp", Json.Null))),
        _field_("id", id.mapObject(_.add("timestamp", Json.fromString("not-an-instant")))),
        _field_("id", id.mapObject(_.add("entropy", Json.Null))),
        _field_("id", id.mapObject(_.add("entropy", Json.fromString(" ")))),
        _field_("id", id.mapObject(_.add("major", Json.fromString("x" * 129)))),
        _field_("name", Json.fromString("x" * 129)), _field_("kind", Json.fromString("")),
        _field_("attributes", Json.obj(("x" * 129) -> Json.fromString("value"))),
        _field_("attributes", Json.obj("key" -> Json.fromString("x" * 8193))),
        _field_("attributes", Json.obj(Vector.tabulate(257)(i => s"key$i" -> Json.fromString("x"))*)),
        _field_("payload", _tag_("map", Json.obj(("x" * 129) -> string))),
        _payload_(_tag_("string", Json.fromString("x" * 8193))),
        _payload_(_tag_("int", Json.fromString("2147483648"))),
        _payload_(_tag_("long", Json.fromString("9223372036854775808"))),
        _payload_(_tag_("decimal", Json.fromString("NaN"))),
        _payload_(_tag_("vector", Json.arr(Vector.fill(257)(string)*))),
        _payload_(_tag_("map", Json.obj(Vector.tabulate(257)(i => s"key$i" -> string)*))),
        _payload_(nested), _payload_(nodes),
        _field_("sequence", Json.fromString("9223372036854775808")))
      When("the actual parsed values are decoded without a writer round trip")
      val results = invalid.map(value => EventRecordJsonV1.decodeC(value.noSpaces).toOption)
      val oversized = EventRecordJsonV1.decodeC(" " * 65537).toOption
      Then("every invalid domain, payload budget and envelope bound rejects")
      results.foreach(_ shouldBe None)
      oversized shouldBe None
    }
    "reject unsupported payloads and all bounded representation overflows" in {
      Given("values outside the admitted closed payload algebra")
      val nested = (1 to 17).foldLeft[Any]("leaf")((value, _) => Vector(value))
      val invalid: Vector[Any] = Vector(null, new Object, Double.NaN, 1.5,
        List("not-vector"), scala.collection.mutable.Map("mutable" -> "value"),
        "x" * 8193, Vector.fill(257)("value"), Vector.tabulate(257)(i => s"key-$i" -> "value").toMap,
        Vector.fill(256)(Vector.fill(16)("node")), nested, Vector.fill(9)("x" * 8192))
      When("invalid facts are admitted as canonical side records")
      invalid.foreach(value =>
        EventRecordJsonV1.createSideEffectC(_events, _event(Map("value" -> value))).toOption shouldBe None)
      Then("encoding has no lossy fallback and publishes nothing")
      intercept[IllegalArgumentException](EventRecordJsonV1.encode(_event(Map("value" -> new Object))))
      EventRecordJsonV1.createSideEffectC(_events, _event(Map("" -> "value"))).toOption shouldBe None
    }
    "reject missing sequence, wrong lane, transient facts and absent collections before Create" in {
      Given("a pure record-to-effect boundary with no allocator or datastore")
      val event = _event()
      When("the host has not supplied admissible transactional facts")
      Vector(event.copy(sequence = 0L), event.copy(sequence = -1L),
        event.copy(id = EventId.generate()),
        event.copy(id = event.id.copy(timestamp = None)),
        event.copy(id = event.id.copy(entropy = None)),
        event.copy(persistent = false), event.copy(lane = EventLane.NonTransactional),
        event.copy(name = ""), event.copy(attributes = Map("bad" -> null))).foreach(value =>
          EventRecordJsonV1.createSideEffectC(_events, value).toOption shouldBe None)
      EventRecordJsonV1.createSideEffectC(null, event).toOption shouldBe None
      intercept[IllegalArgumentException](EventRecordJsonV1.encode(event.copy(id = EventId.generate())))
      Then("the original assigned facts remain unchanged")
      event.sequence shouldBe Long.MaxValue
    }
    "reject an actual internal committed delivery discriminator without silently losing it" in {
      Given("a valid typed committed delivery lookup outside EventRecord's public Product")
      val collection = EntityCollectionId("test", "event", "entity")
      val machine = CmlStateMachineIdentity("lifecycle")
      val source = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Before")))
      val target = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("After")))
      val binding = CmlTransitionBinding(ComponentId("test.event.Event"), collection, machine,
        CmlStateMachineVersion(1), CmlStateMachineTransitionIdentity(machine, 0),
        source, CmlStateMachineTransitionTarget.State(target), CmlStateMachineTriggerIdentity(machine, "change"))
      val entityid = summon[ExecutionContext].idGeneration.entityId(collection, "subject")
      val event = _event()
      event._delivery_lookup_key = Some(CommittedDeliveryLookupKey("explicit", entityid, binding, "change"))
      When("the unsupported durable discriminator reaches the codec")
      EventRecordJsonV1.createSideEffectC(_events, event).toOption shouldBe None
      val unissued = event.copy(sequence = 0L)
      unissued._delivery_lookup_key = event._delivery_lookup_key
      EventRecordJsonV1.prepareCreateC(_events, unissued, 1L).toOption shouldBe None
      val selectionpolicy = _take(EventPersistencePolicy.createC("v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId(unissued.kind), EventPersistencePolicy.Mode.Persist))))
      val selected = _take(org.goldenport.cncf.workflow.WorkflowEventSelection.prepareC(
        selectionpolicy, Vector(unissued)))
      org.goldenport.cncf.workflow.WorkflowSelectedEventRecords.prepareC(selected, _events, 0L)
        .toOption shouldBe None
      Then("the key is retained in the source and no apparent round-trip succeeds")
      event._delivery_lookup_key.get.invocationKey shouldBe "explicit"
      intercept[IllegalArgumentException](EventRecordJsonV1.encode(event))
    }
    "round-trip generated typed payloads and insertion orders exactly" in {
      Given("fifty generated payloads using only admitted values")
      val property = Prop.forAll(Gen.alphaNumStr, Gen.choose(Long.MinValue, Long.MaxValue)) { (text, number) =>
        val event = _event(Map("text" -> text, "number" -> number, "nested" -> Vector(text, true)))
        val bytes = EventRecordJsonV1.encode(event)
        EventRecordJsonV1.decodeC(bytes).toOption.exists(value =>
          value == event && value.payload("number").isInstanceOf[Long] &&
            EventRecordJsonV1.encode(value) == bytes)
      }
      When("the bounded property is executed")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("every generated fact retains exact typed content")
      result.passed shouldBe true
    }
  }

  Vector("memory", "sqlite").foreach { backend =>
    s"Canonical event composition ($backend)" should {
      "commit and recover the exact event with guarded Workflow-shaped records" in _with_store(backend) { store =>
        Given("an admitted root and an assigned persistent event")
        val event = _event(Map("count" -> 7L))
        val effect = _take(EventRecordJsonV1.createSideEffectC(_events, event))
        effect.entryId.print shouldBe event.id.value
        effect.record.getString("event_json") shouldBe Some(EventRecordJsonV1.encodeStorage(event))
        _take(store.create(_roots, _entry, Record.dataAuto("revision" -> 1L)))
        When("the existing canonical plan commits all records")
        _take(store.conditionalTransition(_plan(effect))) shouldBe a[DataStoreConditionalTransitionResult.Transitioned]
        Then("the stored bytes recover exact event facts and the successor exists")
        val bytes = _take(store.load(_events, effect.entryId)).get.getString("event_json").get
        bytes shouldBe EventRecordJsonV1.encodeStorage(event)
        _take(EventRecordJsonV1.decodeStorageC(bytes)) shouldBe event
        _take(store.load(_next, _entry)).nonEmpty shouldBe true
      }
      "roll back both guarded records when the event identity already exists" in _with_store(backend) { store =>
        Given("an existing retained event identity")
        val original = _event(Map("value" -> "first"))
        val effect = _take(EventRecordJsonV1.createSideEffectC(_events, original))
        _take(store.create(_roots, _entry, Record.dataAuto("revision" -> 1L)))
        _take(store.create(_events, effect.entryId, effect.record))
        val before = _take(store.load(_roots, _entry))
        When("a different payload with the same identity is composed")
        val conflicting = _take(EventRecordJsonV1.createSideEffectC(_events, original.copy(payload = Map("value" -> "second"))))
        store.conditionalTransition(_plan(conflicting)).toOption shouldBe None
        Then("the original event remains and neither guarded record moves")
        _take(store.load(_roots, _entry)) shouldBe before
        _take(store.load(_next, _entry)) shouldBe None
        val bytes = _take(store.load(_events, effect.entryId)).get.getString("event_json").get
        bytes shouldBe EventRecordJsonV1.encodeStorage(original)
        _take(EventRecordJsonV1.decodeStorageC(bytes)) shouldBe original
      }
    }
  }
  "SQLite event record recovery" should {
    "recover exact bytes after closing and reopening the canonical resource" in {
      Given("one canonical SQLite resource containing a composed event")
      val database = _database.toString
      val event = _event(Map("payload" -> "durable"))
      val effect = _take(EventRecordJsonV1.createSideEffectC(_events, event))
      val first = SqlDataStore.sqlite(database)
      try {
        _take(first.create(_roots, _entry, Record.dataAuto("revision" -> 1L)))
        _take(first.conditionalTransition(_plan(effect)))
      } finally _take(first.closeC())
      When("a new resource opens the same explicit path")
      val second = SqlDataStore.sqlite(database)
      try {
        Then("only retained facts are recovered, without append or sequence allocation")
        val bytes = _take(second.load(_events, effect.entryId)).get.getString("event_json").get
        bytes shouldBe EventRecordJsonV1.encodeStorage(event)
        _take(EventRecordJsonV1.decodeStorageC(bytes)) shouldBe event
      } finally _take(second.closeC())
    }
  }
}
