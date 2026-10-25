package org.goldenport.cncf.datastore.sql

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.{EventStorePersistenceConstruction, Subsystem}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Real managed history queries, retained transactions and non-mutating reads.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
final class ManagedEventHistoryQuerySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given _context: ExecutionContext =
    ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
  private val _time = Instant.parse("2026-10-25T00:00:00Z")
  private val _owner = ComponentId("org.example.HistoryWorkflow")
  private def _collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "historyquery", name))
  private val _events = _collection("events")
  private val _roots = _collection("root")
  private val _next = _collection("continuation")
  private val _entry = DataStore.StringEntryId("instance")
  private val _declaration = _take(EventStorePersistenceDeclaration.createC("events",
    _collection("cursor"), _events, _collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(_owner, "workflow", _roots, _next))))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected managed query fixture rejection: $other")
  }
  private def _rejection[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.print
    case _ => fail("Expected a structured history rejection")
  }
  private def _catalog(store: SqlDataStore): Vector[(String, String, String)] =
    _take(store.managedResourceOption.get.borrowC { source => Consequence {
      val connection = source.getConnection()
      try {
        val statement = connection.prepareStatement("SELECT type,name,sql FROM sqlite_master ORDER BY type,name")
        try {
          val rows = statement.executeQuery()
          try {
            val entries = Vector.newBuilder[(String, String, String)]
            while (rows.next()) entries += ((rows.getString(1), rows.getString(2), rows.getString(3)))
            entries.result()
          } finally rows.close()
        } finally statement.close()
      } finally connection.close()
    }})
  private def _history(subsystem: Subsystem, namespace: String, limit: Int = 20) =
    _take(_take(subsystem.bindEventHistoryC(_context)).readC(
      ManagedEventHistory.Query(namespace, "one", Vector("recorded", "observed"), limit)))
  private def _database: Path = {
    val root = Path.of("target", "managed-event-history", "query").toAbsolutePath
    Files.createDirectories(root)
    Files.createTempDirectory(root, "spec-").resolve("events.sqlite")
  }
  private def _with_store(path: Path)(work: (Subsystem, SqlDataStore) => Unit): Unit = {
    val configuration = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    EventStorePersistenceConstruction.withDeclaration(_declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = configuration) { subsystem =>
        _take(subsystem._with_managed_datastore_lease_c { _ =>
          subsystem.resolveManagedComponentDataStoreC(
            ComponentDataStore.Environment(_context.runtime.resolvedParameters, Some(subsystem.configuration)),
            _declaration.serviceRequest).map {
            case sql: SqlDataStore =>
              sql.managedResourceOption.nonEmpty shouldBe true
              work(subsystem, sql)
            case other => fail(s"Fixture requires normal managed SQL: $other")
          }
        })
        subsystem.systemNode.activeLeaseCount shouldBe 0
      }
    }
  }
  private def _event(namespace: String, sequence: Long, payload: Map[String, Any]) =
    EventRecord(EventId("sample", "event", Some(_time), Some(s"issued-$sequence")), "recorded", "fact",
      payload, Map("history.namespace" -> namespace, "history.key" -> "one"), _time, true,
      EventRecord.Status.Stored, EventLane.Transactional, sequence)
  private def _plan(effects: Vector[EntityVersionedSideEffect.Create]) = {
    val owner = _take(DataStoreComponentOwner.create(_owner.name))
    val root = _take(DataStoreConditionalRoot.create(owner, _roots, _entry, "revision",
      Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("state" -> "closed"),
      _take(EntityRevision.createC(2L))))
    _take(DataStoreConditionalTransitionPlan.create(root,
      DataStoreConditionalSuccessor.Create(owner, _next, _entry, "revision",
        Record.dataAuto("revision" -> 1L)), effects))
  }

  "Generic history storage on the normal managed SQL resource" should {
    "retain two unrelated producers and exact numeric projection after host close and reopen" in {
      Given("two generic producers and one captured EventStore database")
      val file = _database
      val events = Vector(
        _event("sample.orders.v1", 9007199254740993L, Map("amount" -> BigDecimal("12.50"))),
        _event("sample.shipments.v1", 9007199254740994L, Map("route" -> Vector("Tokyo", "Osaka"))))
      val creates = Vector(_take(EventRecordJsonV1.createSideEffectC(_events, events.head)),
        _take(EventRecordJsonV2.createSideEffectC(_events, events.last)))
      _with_store(file) { (subsystem, store) =>
        _take(store.create(_roots, _entry, Record.dataAuto("revision" -> 1L)))
        When("the existing conditional transition commits both same-row event projections")
        val result = _take(store.conditionalTransition(_plan(creates)))
        Then("the normal transaction commits all records")
        result shouldBe a[DataStoreConditionalTransitionResult.Transitioned]
        _history(subsystem, "sample.orders.v1").events shouldBe Vector(events.head)
        _history(subsystem, "sample.shipments.v1").events shouldBe Vector(events.last)
      }
      When("another normal host opens the retained database")
      _with_store(file) { (subsystem, store) =>
        val rows = creates.map(create => _take(store.load(_events, create.entryId)).get)
        Then("canonical bytes, unrelated payloads and Long sequence values survive without backfill")
        rows.zip(events).zip(creates).foreach { case ((row, event), create) =>
          row.getString("event_json") shouldBe create.record.getString("event_json")
          row.getString(EventHistoryCorrelation.namespaceField) shouldBe event.attributes.get("history.namespace")
          row.fields.find(_.key == EventHistoryCorrelation.sequenceField).get.value.single shouldBe event.sequence
        }
        _history(subsystem, "sample.orders.v1").events shouldBe Vector(events.head)
        _history(subsystem, "sample.shipments.v1").events shouldBe Vector(events.last)
      }
    }

    "roll back generic projections together with the existing guarded records" in {
      Given("one retained event identity and a different fresh event in the same proposed transaction")
      val file = _database
      val retained = _take(EventRecordJsonV2.createSideEffectC(_events,
        _event("sample.shipments.v1", 10L, Map("route" -> "retained"))))
      val fresh = _take(EventRecordJsonV1.createSideEffectC(_events,
        _event("sample.orders.v1", 11L, Map("amount" -> 100L))))
      _with_store(file) { (subsystem, store) =>
        _take(store.create(_roots, _entry, Record.dataAuto("revision" -> 1L)))
        _take(store.create(_events, retained.entryId, retained.record))
        val before = _take(store.load(_roots, _entry))
        When("a composed transition tries to Create an already retained event after a fresh event")
        val rejected = store.conditionalTransition(_plan(Vector(fresh, retained)))
        Then("the fresh projection, guarded mutation and successor do not escape rollback")
        rejected.isSuccess shouldBe false
        _take(store.load(_events, fresh.entryId)) shouldBe None
        _take(store.load(_roots, _entry)) shouldBe before
        _take(store.load(_next, _entry)) shouldBe None
        _take(store.load(_events, retained.entryId)).flatMap(_.getString("event_json")) shouldBe
          retained.record.getString("event_json")
        _history(subsystem, "sample.orders.v1").events shouldBe Vector.empty[EventRecord]
        _history(subsystem, "sample.shipments.v1").events.map(_.sequence) shouldBe Vector(10L)
      }
    }

    "filter unrelated namespaces, keys, names and legacy rows before the source bound" in {
      Given("a legacy row and many newer unrelated events surrounding three matching Long-sequenced facts")
      val matching = Vector(9007199254741003L, 10L, 2L).map(sequence =>
        _event("sample.orders.v1", sequence, Map("amount" -> sequence)))
      val selected = matching.updated(1, matching(1).copy(name = "observed"))
      val unrelated = Vector.tabulate(90) { index =>
        val event = _event("sample.shipments.v1", 9200000000000000L + index, Map("route" -> s"parcel-$index"))
        index % 3 match {
          case 0 => event
          case 1 => event.copy(attributes = Map("history.namespace" -> "sample.orders.v1", "history.key" -> "other"))
          case _ => event.copy(name = "excluded", attributes = Map("history.namespace" -> "sample.orders.v1", "history.key" -> "one"))
        }
      }
      val legacy = _event("sample.orders.v1", Long.MaxValue, Map("old" -> true)).copy(attributes = Map.empty)
      _with_store(_database) { (subsystem, store) =>
        val old = _take(EventRecordJsonV1.createSideEffectC(_events, legacy))
        _take(store.create(_events, old.entryId, old.record))
        (unrelated ++ selected).zipWithIndex.foreach { case (event, index) =>
          val create = if (index % 2 == 0) _take(EventRecordJsonV1.createSideEffectC(_events, event))
            else _take(EventRecordJsonV2.createSideEffectC(_events, event))
          _take(store.create(_events, create.entryId, create.record))
        }
        val query = ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded", "observed"), 2)
        val before = _catalog(store)
        val start = _context.clock.instant()
        When("the provider and normal capability read the exact finite scope")
        val rows = _take(store.readCommittedEventHistoryC(_events, query))
        val snapshot = _take(_take(subsystem.bindEventHistoryC(_context)).readC(query))
        val finish = _context.clock.instant()
        Then("source selection returns only limit+1 matching rows with exact numeric order and no DDL")
        rows.size shouldBe 3
        rows.map(_.getString("id").get) shouldBe selected.map(_.id.value)
        snapshot.events shouldBe selected.take(2)
        snapshot.truncated shouldBe true
        snapshot.coverage shouldBe "retained-correlated-window"
        snapshot.observedAt.isBefore(start) shouldBe false
        snapshot.observedAt.isAfter(finish) shouldBe false
        _catalog(store) shouldBe before
        _history(subsystem, "sample.shipments.v1", 1).events.head.payload.keySet shouldBe Set("route")
      }
    }

    "report missing or nonnumeric projection without creating or altering legacy schema" in {
      Given("an absent event table, a canonical-only legacy table and a text-sequence table")
      Vector("absent", "legacy", "text-sequence").foreach { mode =>
        _with_store(_database) { (subsystem, store) =>
          val event = _event("sample.orders.v1", 10L, Map("fixture" -> mode))
          if (mode != "absent") {
            val value = if (mode == "legacy") event.copy(attributes = Map.empty) else event
            val create = _take(EventRecordJsonV1.createSideEffectC(_events, value))
            val record = if (mode == "text-sequence")
              create.record.upsertSingle(EventHistoryCorrelation.sequenceField, "10") else create.record
            _take(store.create(_events, create.entryId, record))
          }
          val before = _catalog(store)
          When("the normal history reader encounters an unsupported retained projection")
          val rejected = _take(subsystem.bindEventHistoryC(_context)).readC(
            ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded")))
          Then("unavailability is explicit and the complete SQLite catalog remains unchanged")
          _rejection(rejected) should include("projection schema is unavailable or nonnumeric")
          _catalog(store) shouldBe before
        }
      }
    }

    "reject corrupted canonical association even in the physical look-ahead row" in {
      Given("two valid matching rows followed by one row with crossed canonical attributes")
      _with_store(_database) { (subsystem, store) =>
        val events = Vector(30L, 20L, 10L).map(sequence =>
          _event("sample.orders.v1", sequence, Map("opaque" -> sequence)))
        events.foreach { event =>
          val create = _take(EventRecordJsonV2.createSideEffectC(_events, event))
          val record = if (event.sequence == 10L) create.record.upsertSingle("event_json",
            EventRecordJsonV2.encodeStorage(event.copy(attributes = Map(
              "history.namespace" -> "sample.shipments.v1", "history.key" -> "one")))) else create.record
          _take(store.create(_events, create.entryId, record))
        }
        When("a two-event page fetches the third row to determine truncation")
        val rejected = _take(subsystem.bindEventHistoryC(_context)).readC(
          ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded"), 2))
        Then("the whole snapshot rejects the mismatch instead of silently dropping the extra row")
        _rejection(rejected) should include("differs from its canonical correlated event")
      }
    }

    "refuse a standalone SQLite owner rather than invent a dispatch lease" in {
      Given("a standalone SQL owner outside normal managed dispatch")
      val store = SqlDataStore.sqlite(_database.toString)
      try {
        When("the internal provider method is invoked without a borrowed managed admission")
        val rejected = store.readCommittedEventHistoryC(_events,
          ManagedEventHistory.Query("sample.orders.v1", "one", Vector("recorded")))
        Then("it refuses access instead of replacing the owning resource or lease")
        _rejection(rejected) should include("borrowed managed SQLite resource")
      } finally _take(store.closeC())
    }

    "compile every finite predicate into source SQL before ordering and limiting" in {
      Given("an exact query containing SQL punctuation as opaque bound data")
      val query = ManagedEventHistory.Query("sample.orders.v1", "one' OR 1=1 --", Vector("recorded", "observed"), 1)
      _with_store(_database) { (_, store) =>
        When("the existing SQL builder receives only the fixed generic directive")
        val sql = store.debugSearchSql(_events, _take(ManagedEventHistory.directiveC(query)))
        Then("the source includes pair and name predicates, descending order and limit+1 with bound values")
        sql.sql should include(" WHERE ")
        sql.sql should include("event_history_namespace")
        sql.sql should include("event_history_key")
        sql.sql should include("event_history_name")
        sql.sql should include(" OR ")
        sql.sql should include(" DESC LIMIT 2")
        sql.sql should not include query.correlationKey
        sql.sql.indexOf(" WHERE ") should be < sql.sql.indexOf(" ORDER BY ")
        sql.params shouldBe Vector(query.correlationNamespace, query.correlationKey, "recorded", "observed", 0L)
      }
    }
  }
}
