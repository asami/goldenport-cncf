package org.goldenport.cncf.datastore.sql

import java.nio.file.{Files, Path}
import java.sql.{Connection, SQLException}
import java.time.Instant
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{Capability, ExecutionContext}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.statemachine.*
import org.goldenport.cncf.subsystem.{EventStoreConstruction, EventStorePersistenceConstruction, ObservationLeaseTestSupport, Subsystem, SystemNodeResourceLease}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.workflow.{WorkflowCursorEventProposal, WorkflowEventSelection}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/** Real borrowed managed resource, instrumented only in this test provider.
  * No production callback, unmanaged snapshot or publication adapter.
  * @since   Sep. 30, 2026
  * @version Oct. 27, 2026
  */
final class ManagedEventObservationIsolationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](c: Consequence[A]): A = c match {
    case Consequence.Success(a) => a
    case other => fail(s"Unexpected rejection: $other")
  }
  private def uncertain[A](value: Consequence[A]): Unit = value match {
    case Consequence.Failure(conclusion) =>
      conclusion.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Reason(name) => name
      } should contain("transaction-indeterminate")
    case _ => fail("Uncertain retained completion projected as success")
  }
  private val owner = ComponentId("test.isolation.Workflow")
  private def collection(n: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "isolation", n))
  private val cursors = collection("cursor")
  private val events = collection("events")
  private val indexes = collection("indexes")
  private val declaration = take(EventStorePersistenceDeclaration.createC("events", cursors, events, indexes,
    Vector(EventStorePersistenceDeclaration.WorkflowGrant(owner, "workflow", collection("root"), collection("claim"))),
    DataStore.StringEntryId("stream")))
  private val time = Instant.parse("2026-09-30T00:00:00Z")
  private val entityType = EntityCollectionId("test", "isolation", "subject")
  private val entity = take(EntityId.restore("test", "entity", entityType, time, "issued"))
  private val machine = CmlStateMachineIdentity("lifecycle")
  private val binding = CmlTransitionBinding(owner, entityType, machine, CmlStateMachineVersion(1),
    CmlStateMachineTransitionIdentity(machine, 0),
    CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Before"))),
    CmlStateMachineTransitionTarget.Final, CmlStateMachineTriggerIdentity(machine, "change"))
  private def event(token: String) = {
    val e = EventRecord(EventId("test", "isolation", Some(time), Some(token)), "fact", "fact",
      Map("value" -> token), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional)
    ObservationEventTestSupport.key(e, token, entity, binding)
    e
  }
  private val policy = take(EventPersistencePolicy.createC("isolation-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("fact"), EventPersistencePolicy.Mode.Persist))))
  private def selection(es: Vector[EventRecord]) = take(WorkflowEventSelection.prepareFromStoreC(EventStore.inMemory(policy), es))
  private def env(s: Subsystem) = ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(s.configuration))
  private def _fixture_event(token: String, correlated: Boolean): EventRecord = {
    val original = event(token)
    if (!correlated) original
    else {
      val value = original.copy(attributes = Map("history.namespace" -> "sample.isolation.v1", "history.key" -> "stream"))
      ObservationEventTestSupport.copyKey(original, value)
      value
    }
  }
  private def withHost(f: (Subsystem, Path) => Unit): Unit =
    _with_host(Files.createTempDirectory("cncf-observation-isolation-").resolve("events.sqlite"))(f)

  private def _with_host(path: Path)(task: (Subsystem, Path) => Unit): Unit = {
    val conf = ResolvedConfiguration(Configuration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    EventStorePersistenceConstruction.withDeclaration(declaration) {
      TestComponentFactory.withEmptySubsystem(configuration = conf)(s => task(s, path))
    }
  }
  private class Instrumented(resource: ManagedSqlDataStoreResource, lease: SystemNodeResourceLease)
      extends SqlDataStore(SqliteDialectDriver, resource.datasource) with EntityObservationDataStore with EntityVerifiedMultiGuardDataStore with EntityRetainedEventDataStore with EntityTransientEventDataStore {
    override private[datastore] def supportsPreparedEvents: Boolean = true
    useManagedResource(resource, () => if (lease.isReleased || lease.isRevoked)
      Consequence.stateInvalid("Test managed lease is not live") else Consequence.unit)
    var reads = 0
    var indexReads = 0
    var calls = 0
    var commits = 0
    var active = false
    var beforeRead: (Int, Int) => Unit = (_, _) => ()
    var afterCleanup: () => Unit = () => ()
    var writes = 0
    var transientWrites = 0
    var retainedWrites = 0
    var preparedWrites = 0
    var snapshots = 0
    var checkpoint: DataStoreConditionalTransitionCheckpoint => Consequence[Unit] = _ => Consequence.unit
    var afterReadback: () => Unit = () => ()
    var preparedReadback: Consequence[Vector[DataStoreMultiGuardRecord]] => Consequence[Vector[DataStoreMultiGuardRecord]] = identity
    var retainedAcknowledgement: DataStoreMultiGuardAcknowledgement => DataStoreMultiGuardAcknowledgement = identity
    var retainedReadback: Consequence[Vector[DataStoreMultiGuardRecord]] => Consequence[Vector[DataStoreMultiGuardRecord]] = identity
    var traceTransient = false
    var transientEventSql = Vector.empty[String]
    var transientAcknowledgement: DataStoreMultiGuardAcknowledgement => DataStoreMultiGuardAcknowledgement = identity
    var transientReadback: Consequence[Vector[DataStoreMultiGuardRecord]] => Consequence[Vector[DataStoreMultiGuardRecord]] = identity
    var beforeInitialCursorInsert: () => Unit = () => ()
    var afterCommit: () => Consequence[Unit] = () => Consequence.unit
    override protected def commit_transient_events(inv: DataStoreSpace.TransientEventInvocation)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = {
      writes += 1
      transientWrites += 1
      commitTransientEventsManagedC(inv).map(transientAcknowledgement)
    }
    override protected def readback_transient_events(inv: DataStoreSpace.TransientEventInvocation)
        (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
      snapshots += 1
      val result = readbackTransientEventsManagedC(inv)
      afterReadback()
      transientReadback(result)
    }
    override protected def conditional_transition_checkpoint(stage: DataStoreConditionalTransitionCheckpoint): Consequence[Unit] = checkpoint(stage)
    override protected def commit_multi_guard_for_readback(plan: DataStoreConditionalMultiGuardPlan)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
      Consequence.configurationInvalid("Fixture requires a live host invocation")
    override protected def commit_multi_guard_admitted(inv: DataStoreSpace.MultiGuardInvocation)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = {
      writes += 1
      preparedWrites += 1
      conditionalMultiGuardAcknowledgedManaged(inv)
    }
    override protected def readback_multi_guard(inv: DataStoreSpace.MultiGuardInvocation)
        (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
      snapshots += 1
      val result = readbackMultiGuardSnapshotC(inv)
      afterReadback()
      preparedReadback(result)
    }
    override protected def commit_retained_events(inv: DataStoreSpace.RetainedEventInvocation)
        (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] = {
      writes += 1
      retainedWrites += 1
      commitRetainedEventsManagedC(inv).map(retainedAcknowledgement)
    }
    override protected def readback_retained_events(inv: DataStoreSpace.RetainedEventInvocation)
        (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]] = {
      snapshots += 1
      val result = readbackRetainedEventsManagedC(inv)
      afterReadback()
      retainedReadback(result)
    }
    override protected def sql_statement(sql: String): Unit = {
      if (sql.startsWith(s"INSERT INTO ${SqliteDialectDriver.quoteIdentifier(cursors.collectionName)} ("))
        beforeInitialCursorInsert()
      if (traceTransient && Vector(cursors, events, indexes).exists(c =>
          sql.contains(SqliteDialectDriver.quoteIdentifier(c.collectionName)))) transientEventSql :+= sql
      if (active) {
        if (sql == SqliteDialectDriver.selectByIdSql(events.collectionName)) { reads += 1; beforeRead(reads, indexReads) }
        if (sql == SqliteDialectDriver.selectByIdSql(indexes.collectionName)) { indexReads += 1; beforeRead(reads, indexReads) }
      }
    }
    override protected def conditional_transition_commit(c: Connection): Consequence[Unit] = {
      commits += 1
      super.conditional_transition_commit(c).flatMap(_ => afterCommit())
    }
    override protected def observe_native(inv: DataStoreSpace.ObservationInvocation)
        (using ExecutionContext): Consequence[Vector[(DataStoreObservationTarget, Option[Record])]] = {
      calls += 1
      active = true
      try {
        val result = observeDeclaredSnapshotC(inv)
        active = false
        afterCleanup()
        result
      } finally active = false
    }
  }
  private def fixture(s: Subsystem, correlated: Boolean = false, initialize: Boolean = true)(
      f: (DataStoreSpace, Instrumented, SystemNodeResourceLease, WorkflowEventSelection) => Unit): Unit = {
    if (initialize) take(s.initializeEventCursorC(env(s)))
    val original = selection(Vector(_fixture_event("one", correlated), _fixture_event("two", correlated)))
    val prepared = take(WorkflowCursorEventProposal.prepareV2C(original, declaration, EventCursorRecordV1.initial))
    take(s._with_managed_datastore_lease_c { lease =>
      for {
        base <- s.resolveManagedComponentDataStoreC(env(s), declaration.serviceRequest)
        resource <- Consequence.success(base.asInstanceOf[SqlDataStore].managedResourceOption.get)
        writePolicy <- declaration.policyC(take(declaration.grantC(owner)))
        readPolicy <- DataStoreObservationPolicy.createC(DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes)
        value <- {
          val store = new Instrumented(resource, lease)
          val space = take(DataStoreSpace.multiGuardHostViewC(writePolicy, store, lease, Some(readPolicy), Some(s.eventStore)))
          if (initialize) {
            (prepared.events.creates ++ prepared.events.indexCreates).foreach(c => take(store.create(c.collection, c.entryId, c.record)))
            take(store.save(cursors, declaration.cursorEntryId, take(EventCursorRecordV1.createC(2L, 2L)).toRecord))
          }
          val alias = _fixture_event("alias", correlated)
          ObservationEventTestSupport.copyKey(original.events.head, alias)
          val byid = _fixture_event("two", correlated)
          ObservationEventTestSupport.clearKey(byid)
          f(space, store, lease, selection(Vector(alias, byid)))
          Consequence.unit
        }
      } yield value
    })
  }
  private def _history(s: Subsystem): Vector[EventRecord] = {
    val actor = ExecutionContext.withSecurityContext(context,
      context.security.copy(capabilities = context.security.capabilities + Capability("event_view")))
    take(take(s.bindEventHistoryC(actor)).readC(
      ManagedEventHistory.Query("sample.isolation.v1", "stream", Vector("fact")))).events
  }
  private def writer(file: Path): SQLiteDataSource = {
    val config = new SQLiteConfig()
    config.setBusyTimeout(0)
    config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
    val source = new SQLiteDataSource(config)
    source.setUrl(s"jdbc:sqlite:$file")
    source
  }
  private def workflow(store: Instrumented): DataStoreConditionalTransitionPlan = {
    val ownerId = take(DataStoreComponentOwner.create(owner.name))
    val grant = take(declaration.grantC(owner))
    def root(c: DataStore.CollectionId, id: String) = {
      take(store.create(c, DataStore.StringEntryId(id), Record.dataAuto("revision" -> 1L, "status" -> "open")))
      take(DataStoreConditionalRoot.create(ownerId, c, DataStore.StringEntryId(id), "revision",
        Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "closed"), take(EntityRevision.createC(2L))))
    }
    take(DataStoreConditionalTransitionPlan.create(root(grant.rootCollection, "instance"),
      DataStoreConditionalSuccessor.Update(root(grant.continuationCollection, "continuation")), Vector.empty))
  }
  private def inspect(s: Subsystem, committed: Boolean): Unit = {
    take(s.loadEventCursorC(env(s))).lastSequence shouldBe (if (committed) 3L else 2L)
    take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
      val grant = take(declaration.grantC(owner))
      Vector(grant.rootCollection -> "instance", grant.continuationCollection -> "continuation").foreach { case (c, id) =>
        take(take(space.dataStore(c)).load(c, DataStore.StringEntryId(id))).get.getString("status") shouldBe
          Some(if (committed) "closed" else "open")
      }
      val fresh = event("fresh")
      val actual = take(take(space.dataStore(events)).load(events, DataStore.StringEntryId(fresh.id.value)))
      actual.nonEmpty shouldBe committed
      val selected = selection(Vector(fresh))
      val index = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration,
        take(EventCursorRecordV1.createC(2L, 2L)))).events.indexCreates.head
      take(take(space.dataStore(indexes)).load(indexes, index.entryId)).nonEmpty shouldBe committed
      Consequence.unit
    })
  }

  private def inspectRetained(s: Subsystem, committed: Boolean, conflicted: Option[DataStore.CollectionId] = None): Unit = {
    take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { space =>
      def read(c: DataStore.CollectionId, id: DataStore.EntryId) = take(take(space.dataStore(c)).load(c, id))
      def physicalFields(record: Record, id: DataStore.EntryId) =
        record.fields.toSet ++ Record.dataAuto("id" -> id.print).fields.toSet
      read(cursors, declaration.cursorEntryId).get.fields.toSet shouldBe
        physicalFields(take(EventCursorRecordV1.createC(2L, 2L)).toRecord, declaration.cursorEntryId)
      val grant = take(declaration.grantC(owner))
      Vector(grant.rootCollection -> "instance", grant.continuationCollection -> "continuation").foreach { case (c, id) =>
        val record = read(c, DataStore.StringEntryId(id)).get
        val expectedStatus = if (conflicted.contains(c)) "external" else if (committed) "closed" else "open"
        val expectedRevision = if (conflicted.contains(c)) 9L else if (committed) 2L else 1L
        record.getString("status") shouldBe Some(expectedStatus)
        record.fields.toSet shouldBe physicalFields(Record.dataAuto("revision" -> expectedRevision,
          "status" -> expectedStatus), DataStore.StringEntryId(id))
      }
      val original = take(WorkflowCursorEventProposal.prepareV2C(selection(Vector(event("one"), event("two"))),
        declaration, EventCursorRecordV1.initial))
      (original.events.creates ++ original.events.indexCreates).foreach { c =>
        read(c.collection, c.entryId).get.fields.toSet shouldBe physicalFields(c.record, c.entryId)
      }
      Vector("alias", "fresh").foreach { token =>
        val e = event(token)
        read(events, DataStore.StringEntryId(e.id.value)) shouldBe None
        val p = take(WorkflowCursorEventProposal.prepareV2C(selection(Vector(e)), declaration, EventCursorRecordV1.initial))
        read(indexes, p.events.indexCreates.head.entryId) shouldBe None
      }
      Consequence.unit
    })
  }

  private def transientInput = Vector(EventRecord(
    EventId("test", "isolation", Some(time), Some("ephemeral")), "fact", "ephemeral",
    Map("unsupported" -> new Object), Map.empty, time, true, EventRecord.Status.Stored, EventLane.Transactional))

  private def _first_install_path: Path = {
    val root = Files.createDirectories(Path.of("target", "managed-event-first-install")).toAbsolutePath
    Files.createTempDirectory(root, "spec-").resolve("events.sqlite")
  }

  private def _with_install_store(subsystem: Subsystem)(task: (Instrumented, SystemNodeResourceLease) => Unit): Unit =
    take(subsystem._with_managed_datastore_lease_c { lease =>
      subsystem.resolveManagedComponentDataStoreC(env(subsystem), declaration.serviceRequest).map { base =>
        val resource = base.asInstanceOf[SqlDataStore].managedResourceOption.get
        task(new Instrumented(resource, lease), lease)
      }
    })

  // Provider specifications inspect actual schema/data; the consumer fixture
  // never obtains SQL or uses these helpers to manufacture a successful Start.
  private def _first_install_sql(path: Path, statements: Vector[String]): Unit = {
    val connection = writer(path).getConnection()
    try {
      val statement = connection.createStatement()
      try statements.foreach(statement.execute(_)) finally statement.close()
    } finally connection.close()
  }

  private def _first_install_contents(path: Path): Vector[String] = {
    val connection = writer(path).getConnection()
    try {
      val statement = connection.createStatement()
      val tables = try {
        val rows = statement.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name")
        val values = Vector.newBuilder[(String, String)]
        try while (rows.next()) values += ((rows.getString(1), rows.getString(2)))
        finally rows.close()
        values.result()
      } finally statement.close()
      tables.flatMap { case (name, schema) =>
        val query = connection.createStatement()
        try {
          val rows = query.executeQuery(s"SELECT * FROM ${SqliteDialectDriver.quoteIdentifier(name)} ORDER BY id")
          val values = Vector.newBuilder[String]
          try while (rows.next()) values += (1 to rows.getMetaData.getColumnCount).map(index =>
            Option(rows.getObject(index)).fold("NULL")(_.toString)).mkString("|")
          finally rows.close()
          Vector(schema) ++ values.result()
        } finally query.close()
      }
    } finally connection.close()
  }

  "Explicit managed EventStore installation" should afterWord(
    "spec:managed-event-history example:E2 rules:R1,R4 phase:5.1") {
    "publish the first genuine guarded event and retain it across normal reopen without materializing sample rows" in {
      Vector(false, true).foreach { correlated =>
        Given(s"Spec: docs/spec/managed-event-history.md; Rules: R1,R4; Examples: E2,E3,E4; correlation=$correlated")
        val path = _first_install_path
        val original = _fixture_event("first-real-publication", correlated)
        val selected = selection(Vector(original))
        EventStoreConstruction.withService(EventStore.inMemory(policy)) {
          _with_host(path) { (subsystem, _) =>
            take(subsystem._initialize_event_storage_c(env(subsystem))).lastSequence shouldBe 0L
            _with_install_store(subsystem) { (store, lease) =>
              val readpolicy = take(DataStoreObservationPolicy.createC(
                DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
              val writepolicy = take(declaration.policyC(take(declaration.grantC(owner))))
              val space = take(DataStoreSpace.multiGuardHostViewC(writepolicy, store, lease, Some(readpolicy), Some(subsystem.eventStore)))
              val transition = workflow(store)
              When("the first real selected event is observed and committed with its genuine Workflow transition")
              val snapshot = take(space.observeHostEventsC(selected.events))
              take(snapshot.classificationC).newDurableCount shouldBe 1
              take(snapshot.recordsC).tail.foreach(_._2 shouldBe None)
              val prepared = take(snapshot.preparationC)
              take(space.conditionalPreparedEventsVerified(transition, prepared)) shouldBe a[DataStoreMultiGuardReadbackResult.Verified]
              Then("only that event and its actual key are published and cursor advances exactly once")
              take(subsystem.loadEventCursorC(env(subsystem))).lastSequence shouldBe 1L
              val row = take(store.load(events, DataStore.StringEntryId(original.id.value))).get
              val logical = Record(row.fields.filterNot(_.key == "id"))
              val canonical = take(EventHistoryCorrelation.admitStoredRowC(logical))
              canonical.id shouldBe original.id
              canonical.sequence shouldBe 1L
              logical.fields.exists(_.key == EventHistoryCorrelation.namespaceField) shouldBe correlated
              val expected = take(WorkflowCursorEventProposal.prepareV2C(selected, declaration, EventCursorRecordV1.initial))
              expected.events.indexCreates.size shouldBe 1
              val index = expected.events.indexCreates.head
              take(store.load(indexes, index.entryId)).nonEmpty shouldBe true
              store.commits shouldBe 1
            }
          }
          val retained = _first_install_contents(path)
          When("a fresh normal host reopens without installer, reseeding or replay")
          _with_host(path) { (subsystem, _) =>
            take(subsystem.loadEventCursorC(env(subsystem))).lastSequence shouldBe 1L
            take(subsystem._with_managed_datastore_lease_c { _ =>
              val history = _history(subsystem)
              history.map(_.id) shouldBe (if (correlated) Vector(original.id) else Vector.empty)
              subsystem.withWorkflowVerifiedMultiGuardSpaceC(owner, env(subsystem)) { space =>
                val grant = take(declaration.grantC(owner))
                take(take(space.dataStore(grant.rootCollection)).load(grant.rootCollection,
                  DataStore.StringEntryId("instance"))).get.getString("status") shouldBe Some("closed")
                take(take(space.dataStore(grant.continuationCollection)).load(grant.continuationCollection,
                  DataStore.StringEntryId("continuation"))).get.getString("status") shouldBe Some("closed")
                Consequence.unit
              }
            })
            subsystem._initialize_event_storage_c(env(subsystem)).isSuccess shouldBe false
          }
          Then("genuine native/event/index data and the advanced cursor are unchanged")
          _first_install_contents(path) shouldBe retained
        }
      }
    }
  }

  "Explicit managed first-install boundaries" should afterWord(
    "spec:managed-event-history example:E6 rules:R1,R2 phase:5.1") {
    "reuse every compatible empty subset of the fixed infrastructure without seeding event or index data" in {
      (0 until 8).foreach { mask =>
        Given(s"Spec: docs/spec/managed-event-history.md; Rules: R1,R2; Example: E6; empty schema subset=$mask")
        val path = _first_install_path
        val definitions = Vector(
          cursors -> Vector("cursor_schema" -> "", "revision" -> 0L, "last_sequence" -> ""),
          events -> Vector("event_json" -> "", EventHistoryCorrelation.namespaceField -> "", EventHistoryCorrelation.keyField -> "",
            EventHistoryCorrelation.nameField -> "", EventHistoryCorrelation.sequenceField -> 0L),
          indexes -> Vector("index_schema" -> "", "lookup_key" -> "", "event_reference" -> "", "sequence" -> "", "event_body_digest" -> ""))
        _first_install_sql(path, definitions.zipWithIndex.collect { case ((collection, columns), index) if (mask & (1 << index)) != 0 =>
          SqliteDialectDriver.createTableSql(collection.collectionName, columns)
        })
        When("the captured managed owner performs explicit first installation")
        _with_host(path) { (subsystem, _) =>
          val cursor = take(subsystem._initialize_event_storage_c(env(subsystem)))
          Then("all fixed tables exist and only the genuine initial cursor is stored")
          cursor.revision shouldBe 1L
          cursor.lastSequence shouldBe 0L
        }
        val contents = _first_install_contents(path)
        contents.count(_.startsWith("CREATE TABLE")) shouldBe 3
        contents.count(value => !value.startsWith("CREATE TABLE")) shouldBe 1
      }
    }

    "reject incompatible schemas and orphan native data without changing any retained collection" in {
      Vector("wrong-affinity", "missing-column", "extra-column", "wrong-primary-key", "required-history", "default-history",
        "foreign-cursor", "orphan-event", "orphan-index").foreach { fault =>
        Given(s"Spec: docs/spec/managed-event-history.md; Rules: R2; Example: E6; retained $fault")
        val path = _first_install_path
        _with_host(path) { (subsystem, _) =>
          _with_install_store(subsystem) { (store, _) =>
            val table = SqliteDialectDriver.quoteIdentifier(events.collectionName)
            if (Vector("wrong-affinity", "missing-column", "extra-column", "wrong-primary-key", "required-history", "default-history").contains(fault)) {
              val id = if (fault == "wrong-primary-key") "id TEXT" else "id TEXT PRIMARY KEY"
              val namespace = "event_history_namespace TEXT" + (if (fault == "required-history") " NOT NULL"
                else if (fault == "default-history") " DEFAULT 'fabricated'" else "")
              val fields = Vector("event_json TEXT", namespace, "event_history_key TEXT", "event_history_name TEXT") ++
                (if (fault == "missing-column") Vector.empty else Vector(s"event_history_sequence ${if (fault == "wrong-affinity") "TEXT" else "INTEGER"}")) ++
                (if (fault == "extra-column") Vector("unexpected TEXT") else Vector.empty)
              _first_install_sql(path, Vector(s"CREATE TABLE $table (${(Vector(id) ++ fields).mkString(", ")})"))
            } else if (fault == "foreign-cursor") {
              take(store.create(cursors, DataStore.StringEntryId("other-cursor"), EventCursorRecordV1.initial.toRecord))
            } else {
              // A canonical fact/index retained without its cursor is an
              // explicit invalid-installation fixture, never a schema seed.
              val proposal = take(WorkflowCursorEventProposal.prepareV2C(selection(Vector(_fixture_event("orphan", true))),
                declaration, EventCursorRecordV1.initial))
              val effect = if (fault == "orphan-event") proposal.events.creates.head else proposal.events.indexCreates.head
              take(store.create(effect.collection, effect.entryId, effect.record))
            }
            val retained = _first_install_contents(path)
            When("explicit initialization examines all existing infrastructure before any write")
            store._initialize_event_storage_c(declaration).isSuccess shouldBe false
            Then("it refuses without repairing, replacing or dropping retained schema/data")
            store.commits shouldBe 0
            _first_install_contents(path) shouldBe retained
          }
        }
      }
    }

  }

  "Managed first-install failure settlement" should afterWord(
    "spec:managed-event-history example:E7 rules:R3,R5 phase:5.1") {
    "rollback staged infrastructure on known failures and preserve uncertainty after a real committed acknowledgement is lost" in {
      Vector("insert-failure", "revoked-before-entry", "revoked-after-ddl", "committed-unknown").foreach { fault =>
        Given(s"Spec: docs/spec/managed-event-history.md; Rules: R3,R5; Example: E7; $fault at existing provider seam")
        val path = _first_install_path
        _with_host(path) { (subsystem, _) =>
          _with_install_store(subsystem) { (store, lease) =>
            if (fault == "insert-failure") store.beforeInitialCursorInsert = () => throw new SQLException("injected initial-cursor insert failure")
            if (fault == "revoked-before-entry") ObservationLeaseTestSupport.revoke(lease)
            if (fault == "revoked-after-ddl") store.beforeInitialCursorInsert = () => ObservationLeaseTestSupport.revoke(lease)
            if (fault == "committed-unknown") store.afterCommit = () =>
              DataStoreConditionalTransitionFailure.transactionIndeterminate("Injected loss of initial-install commit acknowledgement")
            When("the original managed transaction attempts installation once")
            val result = store._initialize_event_storage_c(declaration)
            Then("precommit failures rollback every table and unknown acknowledgement never becomes success or replay")
            result.isSuccess shouldBe false
            store.commits shouldBe (if (fault == "committed-unknown") 1 else 0)
            if (fault == "committed-unknown") {
              uncertain(result)
              _first_install_contents(path).count(_.startsWith("CREATE TABLE")) shouldBe 3
              _first_install_contents(path).count(value => !value.startsWith("CREATE TABLE")) shouldBe 1
            } else _first_install_contents(path) shouldBe empty
          }
        }
      }
    }

  }

  "First-install provider and observation boundaries" should afterWord(
    "spec:managed-event-history example:E5 rules:R5 phase:5.1") {
    "retain missing-table refusal without changing cursor-only storage during observation" in {
      Given("Spec: docs/spec/managed-event-history.md; Rules: R5; Example: E5; managed cursor-only infrastructure")
      val path = _first_install_path
      _with_host(path) { (subsystem, _) =>
        take(subsystem.initializeEventCursorC(env(subsystem)))
        _with_install_store(subsystem) { (store, lease) =>
          val readpolicy = take(DataStoreObservationPolicy.createC(
            DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
          val writepolicy = take(declaration.policyC(take(declaration.grantC(owner))))
          val space = take(DataStoreSpace.multiGuardHostViewC(writepolicy, store, lease, Some(readpolicy), Some(subsystem.eventStore)))
          val retained = _first_install_contents(path)
          When("normal observation reads an actual selected event against unprovisioned events and indexes")
          space.observeHostEventsC(selection(Vector(_fixture_event("unprovisioned", true))).events).isSuccess shouldBe false
          Then("it refuses authoritative absence without creating any table or publishing an event")
          store.commits shouldBe 0
          _first_install_contents(path) shouldBe retained
        }
      }
    }

    "refuse unowned and unsupported providers before creating infrastructure" in {
      Given("Spec: docs/spec/managed-event-history.md; Rules: R5; Example: E5; a standalone SQLite provider")
      val path = _first_install_path
      val store = new SqlDataStore(SqliteDialectDriver, writer(path))
      When("it receives an infrastructure initialization request without managed host admission")
      store._initialize_event_storage_c(declaration).isSuccess shouldBe false
      Then("no database is opened by the rejected call")
      Files.exists(path) shouldBe false
      _with_host(path) { (subsystem, _) =>
        _with_install_store(subsystem) { (managed, lease) =>
          val unsupported = new SqlDataStore(PostgreSqlDialectDriver, managed.managedResourceOption.get.datasource)
          unsupported.useManagedResource(managed.managedResourceOption.get,
            () => if (lease.isRevoked) Consequence.stateInvalid("revoked") else Consequence.unit)
          unsupported._initialize_event_storage_c(declaration).isSuccess shouldBe false
          _first_install_contents(path) shouldBe empty
        }
      }
    }

  }

  "Concurrent managed first installation" should afterWord(
    "spec:managed-event-history example:E8 rules:R3 phase:5.1") {
    "allow at most one of two separately managed concurrent first installers to succeed" in {
      Given("Spec: docs/spec/managed-event-history.md; Rules: R3; Example: E8; two independent managed hosts on one fresh database")
      val path = _first_install_path
      val ready = new CountDownLatch(2)
      val start = new CountDownLatch(1)
      val pool = Executors.newFixedThreadPool(2)
      given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutor(pool)
      try {
        val attempts = Vector.fill(2)(Future {
          var result = Option.empty[Consequence[EventCursorRecordV1]]
          _with_host(path) { (subsystem, _) =>
            _with_install_store(subsystem) { (store, _) =>
              ready.countDown()
              start.await(10, TimeUnit.SECONDS) shouldBe true
              result = Some(store._initialize_event_storage_c(declaration))
            }
          }
          result.get
        })
        ready.await(10, TimeUnit.SECONDS) shouldBe true
        When("both admitted hosts enter SQLite first installation without an application-wide lock")
        start.countDown()
        val results = Await.result(Future.sequence(attempts), 20.seconds)
        Then("one installation succeeds, the competitor is refused, and no extra cursor/event/index row appears")
        results.count(_.isSuccess) shouldBe 1
        _first_install_contents(path).count(_.startsWith("CREATE TABLE")) shouldBe 3
        _first_install_contents(path).count(value => !value.startsWith("CREATE TABLE")) shouldBe 1
      } finally {
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS) shouldBe true
      }
    }
  }

  "Correlated managed event retention" should {
    "reuse retained ID and delivery-key facts, append only new facts and reobserve them after physical host reopen" in {
      Vector(false, true).foreach { mixed =>
        Given(s"a real managed SQLite host with correlated retained ID/key duplicates and mixed-new=$mixed")
        var retainedpath = Option.empty[Path]
        EventStoreConstruction.withService(EventStore.inMemory(policy)) {
          withHost { (s, path) =>
            retainedpath = Some(path)
            fixture(s, correlated = true) { (space, store, _, selected) =>
              val transition = workflow(store)
              val original = take(WorkflowCursorEventProposal.prepareV2C(
                selection(Vector(_fixture_event("one", true), _fixture_event("two", true))), declaration, EventCursorRecordV1.initial))
              val retained = (original.events.creates ++ original.events.indexCreates).map(effect =>
                (effect.collection, effect.entryId, take(store.load(effect.collection, effect.entryId)).get))
              val input = selected.events ++ (if (mixed) Vector(_fixture_event("fresh", true)) else Vector.empty)
              When("the actual managed observation classifies both retained identities before one native completion")
              val snapshot = take(space.observeHostEventsC(input))
              val classification = take(snapshot.classificationC)
              classification.newDurableCount shouldBe (if (mixed) 1 else 0)
              classification.dispositions.count {
                case ManagedEventDuplicateClassifier.Disposition.RetainedExisting(_) => true
                case _ => false
              } shouldBe 2
              val prepared = take(snapshot.preparationC)
              val completed = if (mixed) space.conditionalPreparedEventsVerified(transition, prepared)
                else space.conditionalRetainedEventsVerified(transition, prepared)
              Then("the transaction preserves every retained byte, index and sequence and consumes a cursor slot only for the new fact")
              take(completed) shouldBe a[DataStoreMultiGuardReadbackResult.Verified]
              retained.foreach { case (collection, id, record) =>
                take(store.load(collection, id)).get.fields.toSet shouldBe record.fields.toSet
              }
              take(s.loadEventCursorC(env(s))).lastSequence shouldBe (if (mixed) 3L else 2L)
              val history = _history(s)
              history.map(_.sequence) shouldBe (if (mixed) Vector(3L, 2L, 1L) else Vector(2L, 1L))
              history.map(_.id).toSet shouldBe (Vector(_fixture_event("one", true), _fixture_event("two", true)) ++
                (if (mixed) Vector(_fixture_event("fresh", true)) else Vector.empty)).map(_.id).toSet
              take(store.load(events, DataStore.StringEntryId(event("alias").id.value))) shouldBe None
            }
          }
          When("a new host and managed lease reopen the same physical database without cursor initialization or event reseeding")
          _with_host(retainedpath.get) { (s, _) =>
            fixture(s, correlated = true, initialize = false) { (space, store, _, selected) =>
              val snapshot = take(space.observeHostEventsC(selected.events))
              Then("the same correlated ID/key duplicates remain retained and no read consumes or rewrites a sequence")
              take(snapshot.classificationC).newDurableCount shouldBe 0
              take(snapshot.recordsC).flatMap(_._2).count(_.getString("event_json").nonEmpty) shouldBe 2
              _history(s).map(_.sequence) shouldBe (if (mixed) Vector(3L, 2L, 1L) else Vector(2L, 1L))
              take(s.loadEventCursorC(env(s))).lastSequence shouldBe (if (mixed) 3L else 2L)
              store.writes shouldBe 0
              store.commits shouldBe 0
            }
          }
        }
      }
    }

    "refuse every changed frozen metadata column and a consistent envelope replacement before publishing Workflow or local writes" in {
      Vector(EventHistoryCorrelation.namespaceField, EventHistoryCorrelation.keyField,
        EventHistoryCorrelation.nameField, EventHistoryCorrelation.sequenceField, "canonical-envelope").foreach { fault =>
        Given(s"a genuine correlated retained snapshot and a competing physical $fault change after freeze")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s, correlated = true) { (_, store, lease, selected) =>
            val transition = workflow(store)
            val basepolicy = take(declaration.policyC(take(declaration.grantC(owner))))
            val localfacts = collection("correlated_facts")
            val localid = DataStore.StringEntryId("fact-1")
            val localpolicy = take(DataStoreConditionalMultiGuardHostPolicy.create(take(DataStoreComponentOwner.create(owner.name)),
              basepolicy.rootCollection, basepolicy.successorCollection, basepolicy.guardBindings,
              basepolicy.sideCreateCollections :+ localfacts, basepolicy.guardTargets))
            val readpolicy = take(DataStoreObservationPolicy.createC(
              DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
            val space = take(DataStoreSpace.multiGuardHostViewC(localpolicy, store, lease, Some(readpolicy), Some(s.eventStore)))
            val snapshot = take(space.observeHostEventsC(selected.events :+ _fixture_event("fresh", true)))
            val prepared = take(snapshot.preparationC)
            val id = DataStore.StringEntryId(event("one").id.value)
            val before = take(store.load(events, id)).get
            val indexrows = take(snapshot.recordsC).collect {
              case (target, Some(record)) if target.collection == indexes => target -> record
            }
            val changed = if (fault == "canonical-envelope") {
              val original = take(EventRecordJsonV2.decodeStorageC(before.getString("event_json").get))
              val replacement = original.copy(attributes = original.attributes.updated("history.key", "changed-stream"))
              ObservationEventTestSupport.copyKey(original, replacement)
              take(EventRecordJsonV2.createSideEffectC(events, replacement)).record
            } else Record.dataAuto(fault -> (if (fault == EventHistoryCorrelation.sequenceField) 99L else "changed"))
            take(store.update(events, id, changed))
            val create = EntityVersionedSideEffect.Create(localfacts, localid, Record.dataAuto("value" -> "closed"))
            When("the original frozen preparation requests its one guarded Workflow/local/event completion")
            val result = space.conditionalPreparedEventsWithLocalIntentsVerified(transition, prepared, Vector.empty, Vector(create))
            Then("the changed physical fact cannot be stripped or recertified, and no Workflow/local/new-event writes survive")
            result.toOption shouldBe None
            store.commits shouldBe 0
            store.snapshots shouldBe 0
            take(store.load(localfacts, localid)) shouldBe None
            take(store.load(events, DataStore.StringEntryId(event("fresh").id.value))) shouldBe None
            take(s.loadEventCursorC(env(s))).lastSequence shouldBe 2L
            val grant = take(declaration.grantC(owner))
            Vector(grant.rootCollection -> "instance", grant.continuationCollection -> "continuation").foreach { case (collection, entry) =>
              val row = take(store.load(collection, DataStore.StringEntryId(entry))).get
              row.getString("status") shouldBe Some("open")
              row.fields.find(_.key == "revision").get.value.single shouldBe 1L
            }
            indexrows.foreach { case (target, record) =>
              take(store.load(target.collection, target.entryId)).get.fields.filterNot(_.key == "id").toSet shouldBe record.fields.toSet
            }
          }
        }}
      }
    }
  }

  "Managed ordinary-input host completion" should {
    "stop on missing authority, unsupported new payload or native rollback without selecting another route" in {
      Vector("missing-service", "missing-read-admission", "unsupported-payload", "native-rollback").foreach { failure =>
        Given(s"ordinary durable input and $failure")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, lease, _) =>
            val transition = workflow(store)
            val writePolicy = take(declaration.policyC(take(declaration.grantC(owner))))
            val readPolicy = take(DataStoreObservationPolicy.createC(
              DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
            val admitted = failure match {
              case "missing-service" => take(DataStoreSpace.multiGuardHostViewC(writePolicy, store, lease, Some(readPolicy), None))
              case "missing-read-admission" => take(DataStoreSpace.multiGuardHostViewC(writePolicy, store, lease, None, Some(s.eventStore)))
              case _ => space
            }
            val fresh = event("fresh")
            val input = Vector(if (failure == "unsupported-payload") fresh.copy(payload = Map("unsupported" -> new Object)) else fresh)
            if (failure == "native-rollback") store.checkpoint = cp =>
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) Consequence.operationInvalid("fixture-host-rollback")
              else Consequence.unit
            When("the common host entry makes its single completion attempt")
            admitted.conditionalHostEventsVerified(transition, input).toOption shouldBe None
            Then("the failure cannot invoke transient or retained fallback, commit or readback")
            val observed = failure == "unsupported-payload" || failure == "native-rollback"
            store.calls shouldBe (if (observed) 1 else 0)
            store.writes shouldBe (if (failure == "native-rollback") 1 else 0)
            store.preparedWrites shouldBe (if (failure == "native-rollback") 1 else 0)
            store.transientWrites shouldBe 0
            store.retainedWrites shouldBe 0
            store.commits shouldBe 0
            store.snapshots shouldBe 0
          }
          Then("fresh inspection finds the open pair and exact unchanged cursor, events and indexes")
          inspectRetained(s, false)
        }}
      }
    }

    "choose exactly one native route from captured policy and frozen duplicate classification" in {
      Vector("empty", "transient", "retained", "new", "mixed").foreach { mode =>
        Given(s"ordinary $mode input without a caller-selected storage route")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val input = mode match {
              case "empty" => Vector.empty
              case "transient" => transientInput
              case "retained" => selected.events ++ transientInput
              case "new" => Vector(event("fresh"))
              case "mixed" => selected.events ++ transientInput ++ Vector(event("fresh"))
            }
            store.traceTransient = true
            When("the host completes the effect-free Workflow pair using ordinary events")
            val verified = take(space.conditionalHostEventsVerified(transition, input))
              .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            Then("classification selects one write route with one commit and coherent readback")
            val transientOnly = mode == "empty" || mode == "transient"
            val newDurable = mode == "new" || mode == "mixed"
            verified.records.size shouldBe (mode match {
              case "empty" | "transient" => 2
              case "retained" => 7
              case "new" => 5
              case "mixed" => 9
            })
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
            store.calls shouldBe (if (transientOnly) 0 else 1)
            store.transientWrites shouldBe (if (transientOnly) 1 else 0)
            store.retainedWrites shouldBe (if (mode == "retained") 1 else 0)
            store.preparedWrites shouldBe (if (newDurable) 1 else 0)
            if (transientOnly) store.transientEventSql shouldBe empty
            input.foreach(_.sequence shouldBe 0L)
          }
          if (mode == "new" || mode == "mixed") inspect(s, true) else inspectRetained(s, true)
        }}
      }
    }
  }

  "Managed transient completion isolation" should {
    "exclude an independent pair writer until transient commit and release the native write domain afterward" in {
      Given("ordinary transient input and a separate immediate zero-timeout SQLite writer")
      EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, file) =>
        fixture(s) { (space, store, _, _) =>
          val transition = workflow(store)
          store.traceTransient = true
          val start = new CountDownLatch(1)
          val done = new CountDownLatch(1)
          val source = writer(file)
          val pool = Executors.newSingleThreadExecutor()
          given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
          def probe(): Int = {
            val connection = source.getConnection()
            try {
              val statement = connection.prepareStatement(s"UPDATE ${SqliteDialectDriver.quoteIdentifier(transition.root.collection.collectionName)} SET revision = revision WHERE id = ?")
              try { statement.setString(1, transition.root.entryId.print); statement.executeUpdate() }
              finally statement.close()
            } finally connection.close()
          }
          try {
            val attempted = Future {
              try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No transient staging")
                try Right(probe()) catch { case e: SQLException => Left(e.getErrorCode) }
              } finally done.countDown()
            }
            store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) {
                start.countDown()
                done.await(10, TimeUnit.SECONDS) shouldBe true
              }
              Consequence.unit
            }
            When("the independent writer tries pair mutation during native staging")
            val result = take(space.conditionalTransientEventsVerified(transition, transientInput))
            Then("SQLITE_BUSY excludes the writer and a single two-record completion verifies")
            Await.result(attempted, 10.seconds) shouldBe Left(5)
            result.asInstanceOf[DataStoreMultiGuardReadbackResult.Verified].records.size shouldBe 2
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
            probe() shouldBe 1 // Explicit same-value independent probe, not completion replay.
          } finally {
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
          }
        }
        Then("fresh inspection sees the closed pair with exact unchanged durable facts")
        inspectRetained(s, true)
      }}
    }

    "reject missing or unconfigured services and wrong-thread, revoked, released or escaped hosts before writes" in {
      Vector("missing-service", "unconfigured-service", "wrong-thread", "revoked", "released", "escaped").foreach { misuse =>
        Given(s"ordinary transient input with $misuse host admission")
        val service = if (misuse == "unconfigured-service") EventStore.inMemory else EventStore.inMemory(policy)
        EventStoreConstruction.withService(service) { withHost { (s, _) =>
          var escaped = Option.empty[(DataStoreSpace, Instrumented, DataStoreConditionalTransitionPlan)]
          fixture(s) { (space, store, lease, _) =>
            val transition = workflow(store)
            store.traceTransient = true
            When("the invalid host attempts ordinary-input transient completion")
            misuse match {
              case "missing-service" =>
                val writePolicy = take(declaration.policyC(take(declaration.grantC(owner))))
                val noService = take(DataStoreSpace.multiGuardHostViewC(writePolicy, store, lease))
                noService.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
              case "unconfigured-service" =>
                space.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
              case "wrong-thread" =>
                val result = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
                val thread = new Thread(() => result.set(space.conditionalTransientEventsVerified(transition, transientInput).toOption.nonEmpty))
                thread.start()
                thread.join(5000L)
                thread.isAlive shouldBe false
                result.get() shouldBe java.lang.Boolean.FALSE
              case "revoked" =>
                ObservationLeaseTestSupport.revoke(lease)
                space.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
              case "released" =>
                lease.release()
                space.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
              case "escaped" => escaped = Some((space, store, transition))
            }
            Then("no provider write, commit, readback, durable observation or event SQL has begun")
            store.writes shouldBe 0
            store.commits shouldBe 0
            store.snapshots shouldBe 0
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          escaped.foreach { case (space, store, transition) =>
            When("the original released view receives an escaped completion request")
            space.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
            Then("original authority cannot be revived and no provider execution occurs")
            store.writes shouldBe 0
            store.commits shouldBe 0
            store.snapshots shouldBe 0
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          Then("fresh admitted reads preserve the open pair and every durable fact")
          inspectRetained(s, false)
        }}
      }
    }

    "classify malformed acknowledgements and faulty complete readback as indeterminate without compensating or replaying" in {
      val acknowledgementFaults: Vector[(String, Vector[DataStoreMultiGuardRecord] => Vector[DataStoreMultiGuardRecord])] = Vector(
        "missing" -> (_.take(1)), "reordered" -> (_.reverse), "null" -> (_ => null),
        "wrong-revision" -> (rows => rows.updated(0, rows(0).copy(record = Record.dataAuto("revision" -> 1L, "status" -> "open")))))
      val readbackFaults: Vector[(String, Consequence[Vector[DataStoreMultiGuardRecord]] => Consequence[Vector[DataStoreMultiGuardRecord]])] = Vector(
        "failed" -> (_ => Consequence.operationInvalid("fixture-transient-readback")),
        "throwing" -> (_ => throw new IllegalStateException("fixture-transient-readback")),
        "missing" -> (_.map(_.take(1))), "reordered" -> (_.map(_.reverse)),
        "null" -> (_ => Consequence.success(null)),
        "extra-field" -> (_.map(rows => rows.updated(0, rows(0).copy(record = Record.dataAuto("revision" -> 2L, "status" -> "closed", "extra" -> true))))))
      val cases = acknowledgementFaults.map { case (name, fault) => ("ack-" + name, false,
        (store: Instrumented) => store.transientAcknowledgement = {
          case DataStoreMultiGuardAcknowledgement.Committed(rows) => DataStoreMultiGuardAcknowledgement.Committed(fault(rows))
          case other => other
        }) } ++ readbackFaults.map { case (name, fault) => ("readback-" + name, true,
        (store: Instrumented) => store.transientReadback = fault) }
      cases.foreach { case (name, readback, inject) =>
        Given(s"an actual managed transient pair commit followed by $name provider-return fault")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, _) =>
            val transition = workflow(store)
            store.traceTransient = true
            inject(store)
            When("the host requests its single transient completion")
            val result = space.conditionalTransientEventsVerified(transition, transientInput)
            Then("uncertainty never becomes Verified or NotMatched and publication is not retried")
            uncertain(result)
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe (if (readback) 1 else 0)
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          Then("fresh scope inspection finds the committed pair and exact durable facts, without compensation")
          inspectRetained(s, true)
        }}
      }
    }

    "rollback the pair at every precommit checkpoint with no durable observations or event SQL" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { failAt =>
        Given(s"ordinary transient input and a managed pair failure at $failAt")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, _) =>
            val transition = workflow(store)
            store.traceTransient = true
            store.checkpoint = cp => if (cp == failAt) Consequence.operationInvalid("fixture-transient-staging") else Consequence.unit
            When("the live host attempts one transient-only pair completion")
            space.conditionalTransientEventsVerified(transition, transientInput).toOption shouldBe None
            Then("the pair rolls back without commit/readback, durable observation or event/cursor/index SQL")
            store.writes shouldBe 1
            store.commits shouldBe 0
            store.snapshots shouldBe 0
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          Then("fresh admitted inspection finds the open pair and exact existing durable fields")
          inspectRetained(s, false)
        }}
      }
    }

    "distinguish staging revocation from committed readback revocation and never replay either" in {
      Vector("staging", "readback").foreach { stage =>
        Given(s"ordinary transient input and actual lease revocation during $stage")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, lease, _) =>
            val transition = workflow(store)
            store.traceTransient = true
            if (stage == "staging") store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) ObservationLeaseTestSupport.revoke(lease)
              Consequence.unit
            } else store.afterReadback = () => ObservationLeaseTestSupport.revoke(lease)
            When("the original host makes its one transient completion attempt")
            val result = space.conditionalTransientEventsVerified(transition, transientInput)
            Then("no Verified or retry occurs even if the native pair commit already succeeded")
            result.toOption shouldBe None
            if (stage == "readback") uncertain(result)
            lease.isRevoked shouldBe true
            store.writes shouldBe 1
            store.commits shouldBe (if (stage == "readback") 1 else 0)
            store.snapshots shouldBe (if (stage == "readback") 1 else 0)
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          Then("fresh inspection distinguishes pair rollback from committed but unverified state while durable facts are unchanged")
          inspectRetained(s, stage == "readback")
        }}
      }
    }

    "verify empty and transient pairs once with no event SQL even when durable facts already exist" in {
      Vector(false, true).foreach { emptyInput =>
        Given("an original captured-policy host and unchanged pre-existing cursor/events/indexes")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, _) =>
            val transition = workflow(store)
            store.traceTransient = true
            When("empty or ordinary transient input completes the pair")
            val verified = take(space.conditionalTransientEventsVerified(transition, if (emptyInput) Vector.empty else transientInput))
              .asInstanceOf[DataStoreMultiGuardReadbackResult.Verified]
            Then("one pair-only commit/readback succeeds without durable observations or event-domain SQL")
            verified.records.size shouldBe 2
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
            store.calls shouldBe 0
            store.transientEventSql shouldBe empty
          }
          Then("the closed pair coexists with exact unchanged durable storage")
          inspectRetained(s, true)
        }}
      }
    }
  }

  "Managed retained completion isolation" should {
    "deny invalid Workflow transitions and preserve external root or successor conflicts without event effects" in {
      Vector("wrong-owner", "wrong-collection", "injected-effect", "physical-id", "root-conflict", "successor-conflict").foreach { misuse =>
        Given(s"all-retained observations and a $misuse Workflow transition")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          val grant = take(declaration.grantC(owner))
          fixture(s) { (space, store, _, selected) =>
            val original = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            def alteredRoot(component: DataStoreComponentOwner = original.root.componentOwner,
                c: DataStore.CollectionId = original.root.collection, changes: Record = original.root.changes) =
              take(DataStoreConditionalRoot.create(component, c, original.root.entryId, original.root.revisionField,
                Some(original.root.expectedRevision), original.root.expectedFields, changes, original.root.nextRevision))
            val transition = misuse match {
              case "wrong-owner" =>
                val foreign = take(DataStoreComponentOwner.create("foreign.Workflow"))
                val successor = original.successor.asInstanceOf[DataStoreConditionalSuccessor.Update].root
                val foreignSuccessor = take(DataStoreConditionalRoot.create(foreign, successor.collection,
                  successor.entryId, successor.revisionField, Some(successor.expectedRevision), successor.expectedFields,
                  successor.changes, successor.nextRevision))
                take(DataStoreConditionalTransitionPlan.create(alteredRoot(component = foreign),
                  DataStoreConditionalSuccessor.Update(foreignSuccessor), Vector.empty))
              case "wrong-collection" => take(DataStoreConditionalTransitionPlan.create(
                alteredRoot(c = collection("foreign")), original.successor, Vector.empty))
              case "injected-effect" => take(DataStoreConditionalTransitionPlan.create(original.root, original.successor,
                Vector(EntityVersionedSideEffect.Create(events, DataStore.StringEntryId("injected"), Record.dataAuto("text" -> "foreign")))))
              case "physical-id" => take(DataStoreConditionalTransitionPlan.create(
                alteredRoot(changes = Record.dataAuto("id" -> "foreign", "status" -> "closed")), original.successor, Vector.empty))
              case "root-conflict" | "successor-conflict" =>
                val c = if (misuse == "root-conflict") grant.rootCollection else grant.continuationCollection
                val id = if (misuse == "root-conflict") original.root.entryId else original.successor.entryId
                take(store.save(c, id, Record.dataAuto("revision" -> 9L, "status" -> "external")))
                original
            }
            When("the host attempts the invalid or stale retained completion once")
            val result = space.conditionalRetainedEventsVerified(transition, prepared)
            Then("no verified completion or replay can overwrite competing state or create caller-supplied effects")
            if (misuse == "root-conflict") take(result) shouldBe a[DataStoreMultiGuardReadbackResult.NotMatched]
            else result.toOption shouldBe None
            store.writes shouldBe (if (Set("physical-id", "root-conflict", "successor-conflict").contains(misuse)) 1 else 0)
            store.snapshots shouldBe 0
            store.commits shouldBe (if (misuse == "root-conflict") 1 else 0) // Successful read-only NotMatched transaction, not publication.
            take(store.load(events, DataStore.StringEntryId("injected"))) shouldBe None
          }
          Then("fresh inspection retains the external conflict, untouched other Workflow record and exact event/cursor/index facts")
          val conflict = misuse match {
            case "root-conflict" => Some(grant.rootCollection)
            case "successor-conflict" => Some(grant.continuationCollection)
            case _ => None
          }
          inspectRetained(s, false, conflict)
        }}
      }
    }

    "reject foreign hosts, wrong threads and revoked, released or escaped leases before provider writes" in {
      Vector("foreign-host", "thread", "revoked", "released", "escaped").foreach { misuse =>
        Given(s"an all-retained preparation issued by the original managed host and $misuse use")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          var escaped = Option.empty[(DataStoreSpace, Instrumented, DataStoreConditionalTransitionPlan, ManagedEventPreparation)]
          fixture(s) { (space, store, lease, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            When("completion is attempted outside its original live host authority")
            misuse match {
              case "foreign-host" =>
                val writePolicy = take(declaration.policyC(take(declaration.grantC(owner))))
                val readPolicy = take(DataStoreObservationPolicy.createC(
                  DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
                // Same provider, service, policies, thread and actual lease; only issuing host identity differs.
                val other = take(DataStoreSpace.multiGuardHostViewC(writePolicy, store, lease, Some(readPolicy), Some(s.eventStore)))
                other.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
              case "thread" =>
                val result = new java.util.concurrent.atomic.AtomicReference[java.lang.Boolean]()
                val thread = new Thread(() => result.set(space.conditionalRetainedEventsVerified(transition, prepared).toOption.nonEmpty))
                thread.start()
                thread.join(5000L)
                thread.isAlive shouldBe false
                result.get() shouldBe java.lang.Boolean.FALSE
              case "revoked" =>
                ObservationLeaseTestSupport.revoke(lease)
                lease.isRevoked shouldBe true
                space.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
              case "released" =>
                lease.release()
                lease.isReleased shouldBe true
                space.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
              case "escaped" => escaped = Some((space, store, transition, prepared))
            }
            if (misuse != "escaped") {
              Then("no provider write, native commit or readback begins")
              store.writes shouldBe 0
              store.commits shouldBe 0
              store.snapshots shouldBe 0
            }
          }
          escaped.foreach { case (original, store, transition, prepared) =>
            When("the released original host and a newly admitted host receive the escaped preparation")
            original.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
            take(s.withWorkflowVerifiedMultiGuardSpaceC(owner, env(s)) { fresh =>
              fresh.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
              Consequence.unit
            })
            Then("neither invocation can revive original host authority or start a provider write")
            store.writes shouldBe 0
            store.commits shouldBe 0
            store.snapshots shouldBe 0
          }
          Then("fresh inspection finds both Workflow records open and every cursor/retained fact unchanged")
          inspectRetained(s, false)
        }}
      }
    }

    "reject every malformed committed acknowledgement without readback, rollback or replay" in {
      val faults: Vector[(String, Vector[DataStoreMultiGuardRecord] => Vector[DataStoreMultiGuardRecord])] = Vector(
        "missing" -> (_.dropRight(1)),
        "reordered" -> (_.reverse),
        "duplicate-target" -> (rows => rows.updated(6, rows(5))),
        "unexpected-target" -> (rows => rows.updated(6, rows(6).copy(entryId = DataStore.StringEntryId("foreign")))),
        "invalid-workflow" -> (rows => rows.updated(0, rows(0).copy(record = Record.dataAuto("revision" -> 1L, "status" -> "open")))),
        "changed-cursor" -> (rows => rows.updated(2, rows(2).copy(record = take(EventCursorRecordV1.createC(3L, 3L)).toRecord))),
        "changed-retained-fact" -> (rows => rows.updated(6, rows(6).copy(record = Record.dataAuto("corrupt" -> true)))),
        "null-row" -> (rows => rows.updated(6, null)),
        "null-vector" -> (_ => null))
      faults.foreach { case (name, fault) =>
        Given(s"an actual all-retained commit whose trusted provider acknowledgement is $name")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            store.retainedAcknowledgement = {
              case DataStoreMultiGuardAcknowledgement.Committed(rows) => DataStoreMultiGuardAcknowledgement.Committed(fault(rows))
              case other => other
            }
            When("the original host requests retained completion once")
            val result = space.conditionalRetainedEventsVerified(transition, prepared)
            Then("the malformed acknowledgement is transaction-indeterminate, never Verified or NotMatched")
            uncertain(result)
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 0
          }
          Then("fresh inspection proves committed Workflow storage and unchanged retained facts, without compensating rollback")
          inspectRetained(s, true)
        }}
      }
    }

    "withhold verification on failed, throwing, incomplete or divergent postcommit readback without replay" in {
      val faults: Vector[(String, Consequence[Vector[DataStoreMultiGuardRecord]] => Consequence[Vector[DataStoreMultiGuardRecord]])] = Vector(
        "failed" -> (_ => Consequence.operationInvalid("fixture-retained-readback")),
        "throwing" -> (_ => throw new IllegalStateException("fixture-retained-readback")),
        "missing" -> (_.map(_.dropRight(1))),
        "reordered" -> (_.map(_.reverse)),
        "duplicate-target" -> (_.map(rows => rows.updated(6, rows(5)))),
        "unexpected-target" -> (_.map(rows => rows.updated(6, rows(6).copy(entryId = DataStore.StringEntryId("foreign"))))),
        "invalid-workflow" -> (_.map(rows => rows.updated(0, rows(0).copy(record = Record.dataAuto("revision" -> 1L, "status" -> "open"))))),
        "changed-cursor" -> (_.map(rows => rows.updated(2, rows(2).copy(record = take(EventCursorRecordV1.createC(3L, 3L)).toRecord)))),
        "changed-retained-fact" -> (_.map(rows => rows.updated(6, rows(6).copy(record = Record.dataAuto("corrupt" -> true))))),
        "extra-workflow-field" -> (_.map(rows => rows.updated(0, rows(0).copy(record = Record.dataAuto("revision" -> 2L, "status" -> "closed", "extra" -> true))))),
        "null-row" -> (_.map(rows => rows.updated(6, null))),
        "null-vector" -> (_ => Consequence.success(null)))
      faults.foreach { case (name, fault) =>
        Given(s"a valid native retained acknowledgement followed by $name postcommit readback")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            store.retainedReadback = fault
            When("the host attempts one retained completion and one native snapshot")
            val result = space.conditionalRetainedEventsVerified(transition, prepared)
            Then("the failed verification is transaction-indeterminate with exactly one commit and readback")
            uncertain(result)
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
          }
          Then("a new scope sees the committed pair and original retained facts, with no repair or replay")
          inspectRetained(s, true)
        }}
      }
    }

    "rollback the Workflow pair at every precommit checkpoint without altering retained facts or cursor" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { failAt =>
        Given(s"all-retained observations and a failure at $failAt on the managed provider")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            store.checkpoint = stage => if (stage == failAt) Consequence.operationInvalid("fixture-retained-failure") else Consequence.unit
            When("the original host attempts retained-only completion")
            space.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
            Then("one write attempt rolls back without commit, readback or retry")
            store.writes shouldBe 1
            store.commits shouldBe 0
            store.snapshots shouldBe 0
          }
          Then("a fresh scope finds the complete original Workflow, cursor, event and index fields")
          inspectRetained(s, false)
        }}
      }
    }

    "rollback on staging revocation and withhold verification after committed readback revocation without replay" in {
      Vector("staging", "readback").foreach { stage =>
        Given(s"all-retained observations and actual lease revocation during $stage")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, lease, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
            if (stage == "staging") store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) ObservationLeaseTestSupport.revoke(lease)
              Consequence.unit
            } else store.afterReadback = () => ObservationLeaseTestSupport.revoke(lease)
            When("the host makes its single retained completion attempt")
            space.conditionalRetainedEventsVerified(transition, prepared).toOption shouldBe None
            Then("revocation suppresses Verified and never replays publication")
            lease.isRevoked shouldBe true
            store.writes shouldBe 1
            store.commits shouldBe (if (stage == "readback") 1 else 0)
            store.snapshots shouldBe (if (stage == "readback") 1 else 0)
          }
          Then("fresh inspection distinguishes the rolled-back pair from the committed pair with unchanged retained storage")
          inspectRetained(s, stage == "readback")
        }}
      }
    }

    "exclude an independent cursor writer until retained completion commits and releases the native write domain" in {
      Given("all-retained observations and an independent immediate zero-timeout SQLite writer")
      EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, file) =>
        fixture(s) { (space, store, _, selected) =>
          val transition = workflow(store)
          val prepared = take(take(space.observeHostEventsC(selected.events)).preparationC)
          val start = new CountDownLatch(1)
          val done = new CountDownLatch(1)
          val source = writer(file)
          val pool = Executors.newSingleThreadExecutor()
          given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
          try {
            val attempted = Future {
              try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No retained staging")
                try Right(update(source, 2L)) catch { case e: SQLException => Left(e.getErrorCode) }
              } finally done.countDown()
            }
            store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) {
                start.countDown()
                done.await(10, TimeUnit.SECONDS) shouldBe true
              }
              Consequence.unit
            }
            When("the competing writer attempts mutation after observation validation and Workflow staging")
            val result = take(space.conditionalRetainedEventsVerified(transition, prepared))
            Then("SQLITE_BUSY excludes that writer while one complete seven-record readback verifies completion")
            Await.result(attempted, 10.seconds) shouldBe Left(5)
            result shouldBe a[DataStoreMultiGuardReadbackResult.Verified]
            result.asInstanceOf[DataStoreMultiGuardReadbackResult.Verified].records.size shouldBe 7
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
            // Same-value independent write proves release without changing the retained cursor evidence.
            update(source, 2L) shouldBe 1
          } finally {
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
          }
        }
        Then("the Workflow pair is closed but all cursor, envelope and index fields are unchanged")
        inspectRetained(s, true)
      }}
    }
  }

  "Managed prepared publication isolation" should {
    "compose local intents through host-selected retained, transient and empty routes without EventStore writes" in {
      for (mode <- Vector("retained", "transient", "empty"); fault <- Vector("success", "create-only", "stale", "rollback", "readback")) {
        Given(s"ordinary $mode input with local $fault configuration")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (_, store, lease, selected) =>
            val transition = workflow(store)
            val local = collection("local_mode_guard")
            val facts = collection("local_mode_facts")
            val id = DataStore.StringEntryId("closing")
            val ownerId = take(DataStoreComponentOwner.create(owner.name))
            take(store.create(local, id, Record.dataAuto("revision" -> (if (fault == "stale") 9L else 1L), "status" -> "open")))
            val guard = take(DataStoreConditionalRoot.create(ownerId, local, id, "revision",
              Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "closed"), take(EntityRevision.createC(2L))))
            val base = take(declaration.policyC(take(declaration.grantC(owner))))
            val localBinding = DataStoreConditionalGuardBinding(ownerId, local)
            val write = take(DataStoreConditionalMultiGuardHostPolicy.create(ownerId, base.rootCollection, base.successorCollection,
              base.guardBindings :+ localBinding, base.sideCreateCollections :+ facts,
              base.guardTargets :+ DataStoreConditionalGuardTarget(localBinding, id)))
            val read = take(DataStoreObservationPolicy.createC(DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
            val space = take(DataStoreSpace.multiGuardHostViewC(write, store, lease, Some(read), Some(s.eventStore)))
            val input = if (mode == "retained") selected.events ++ transientInput else if (mode == "transient") transientInput else Vector.empty
            val guards = if (fault == "create-only") Vector.empty else Vector(guard)
            val create = EntityVersionedSideEffect.Create(facts, id, Record.dataAuto("value" -> "closed"))
            if (fault == "rollback") store.checkpoint = cp =>
              if (cp == DataStoreConditionalTransitionCheckpoint.BeforePublish) Consequence.operationInvalid("fixture-local-rollback") else Consequence.unit
            def corrupt(result: Consequence[Vector[DataStoreMultiGuardRecord]]) = result.map(_.map { row =>
              if (row.collection == local) row.copy(record = Record.dataAuto("revision" -> 2L, "status" -> "diverged")) else row
            })
            if (fault == "readback") { store.retainedReadback = corrupt; store.transientReadback = corrupt }
            store.traceTransient = true
            When("the common host entry chooses its route and executes the single local native composition")
            val result = space.conditionalHostEventsWithLocalIntentsVerified(transition, input, guards, Vector(create))
            val committed = fault == "success" || fault == "create-only" || fault == "readback"
            Then("all local post-images share the Workflow outcome, while cursor/event/index storage remains unchanged")
            if (fault == "readback") uncertain(result)
            else if (committed) take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified].records.size shouldBe
              2 + guards.size + 1 + (if (mode == "retained") 5 else 0)
            else result.isSuccess shouldBe false
            store.writes shouldBe 1
            store.commits shouldBe (if (committed) 1 else 0)
            store.snapshots shouldBe (if (committed) 1 else 0)
            store.calls shouldBe (if (mode == "retained") 1 else 0)
            store.preparedWrites shouldBe 0
            if (mode != "retained") store.transientEventSql shouldBe empty
            take(store.load(local, id)).get.getString("status") shouldBe
              Some(if (committed && fault != "create-only") "closed" else "open")
            take(store.load(facts, id)).nonEmpty shouldBe committed
            inspectRetained(s, committed)
          }
        }}
      }
    }

    "reject local Creates targeting reserved EventStore domains through both ordinary and prepared entries" in {
      for (entry <- Vector("ordinary", "prepared"); reserved <- Vector(cursors, events, indexes)) {
        Given(s"a $entry host entry and a local Create in ${reserved.collectionName}")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val injected = EntityVersionedSideEffect.Create(reserved, DataStore.StringEntryId("unselected-local"), Record.dataAuto("value" -> "denied"))
            When("local intent input attempts to bypass captured event-kind selection")
            val result = if (entry == "ordinary")
              space.conditionalHostEventsWithLocalIntentsVerified(transition, transientInput, Vector.empty, Vector(injected))
              else {
                val prepared = take(take(space.observeHostEventsC(selected.events :+ event("fresh"))).preparationC)
                space.conditionalPreparedEventsWithLocalIntentsVerified(transition, prepared, Vector.empty, Vector(injected))
              }
            Then("reserved-domain input rejects before any native write or Create publication")
            result.isSuccess shouldBe false
            store.writes shouldBe 0
            store.commits shouldBe 0
            take(store.load(reserved, DataStore.StringEntryId("unselected-local"))) shouldBe None
            inspectRetained(s, false)
          }
        }}
      }
    }

    "commit admitted local guards and Creates with selected events or rollback the complete native composition" in {
      Vector("success", "stale-local", "duplicate-create", "before-publish", "ungranted-owner", "local-readback").foreach { mode =>
        Given(s"a real managed provider and trusted host-local admission with $mode")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (_, store, lease, selected) =>
            val transition = workflow(store)
            val workflowOwner = take(DataStoreComponentOwner.create(owner.name))
            val local = collection("closing_guard")
            val localId = DataStore.StringEntryId("closing-1")
            val localFacts = collection("closing_facts")
            val factId = DataStore.StringEntryId("fact-1")
            take(store.create(local, localId, Record.dataAuto("revision" -> (if (mode == "stale-local") 9L else 1L),
              "status" -> "open", "unchanged" -> "retained")))
            if (mode == "duplicate-create") take(store.create(localFacts, factId, Record.dataAuto("value" -> "existing")))
            val nativeOwner = if (mode == "ungranted-owner") take(DataStoreComponentOwner.create("foreign.owner")) else workflowOwner
            val guard = take(DataStoreConditionalRoot.create(nativeOwner, local, localId, "revision",
              Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("status" -> "closed"),
              take(EntityRevision.createC(2L))))
            val basePolicy = take(declaration.policyC(take(declaration.grantC(owner))))
            val admittedBinding = DataStoreConditionalGuardBinding(workflowOwner, local)
            // Trusted assembly fixture only. Standard Subsystem declaration does
            // NOT acquire these permissions from submitted local intent input.
            val localPolicy = take(DataStoreConditionalMultiGuardHostPolicy.create(workflowOwner,
              basePolicy.rootCollection, basePolicy.successorCollection,
              basePolicy.guardBindings :+ admittedBinding, basePolicy.sideCreateCollections :+ localFacts,
              basePolicy.guardTargets :+ DataStoreConditionalGuardTarget(admittedBinding, localId)))
            val readPolicy = take(DataStoreObservationPolicy.createC(
              DataStoreObservationTarget(cursors, declaration.cursorEntryId), events, indexes))
            val space = take(DataStoreSpace.multiGuardHostViewC(localPolicy, store, lease, Some(readPolicy), Some(s.eventStore)))
            val fresh = event("fresh")
            val prepared = take(take(space.observeHostEventsC(selected.events :+ fresh)).preparationC)
            val create = EntityVersionedSideEffect.Create(localFacts, factId, Record.dataAuto("value" -> "closed"))
            store.checkpoint = cp => if (mode == "before-publish" && cp == DataStoreConditionalTransitionCheckpoint.BeforePublish)
              Consequence.operationInvalid("fixture-before-publish") else Consequence.unit
            if (mode == "local-readback") store.preparedReadback = _.map(_.map { row =>
              if (row.collection == local) row.copy(record = Record(row.record.fields.filterNot(_.key == "status") ++
                Record.dataAuto("status" -> "diverged").fields)) else row
            })
            When("the host enlists the local guard and Create in the prepared-event native transaction")
            val result = space.conditionalPreparedEventsWithLocalIntentsVerified(transition, prepared, Vector(guard), Vector(create))
            val committed = mode == "success" || mode == "local-readback"
            Then("one physical commit verifies every post-image, or neither Workflow, closing nor event writes survive")
            if (committed) {
              if (mode == "local-readback") uncertain(result)
              else take(result).asInstanceOf[DataStoreMultiGuardReadbackResult.Verified].records.size shouldBe 11
              store.commits shouldBe 1
              store.snapshots shouldBe 1
            } else {
              result.isSuccess shouldBe false
              store.commits shouldBe 0
              store.snapshots shouldBe 0
            }
            store.writes shouldBe (if (mode == "ungranted-owner") 0 else 1)
            val row = take(store.load(local, localId)).get
            row.getString("status") shouldBe Some(if (committed) "closed" else "open")
            row.getString("unchanged") shouldBe Some("retained")
            val created = take(store.load(localFacts, factId))
            if (mode == "duplicate-create") created.get.getString("value") shouldBe Some("existing")
            else if (committed) created.get.getString("value") shouldBe Some("closed")
            else created shouldBe None
            inspect(s, committed)
          }
        }}
      }
    }

    "deny local intents under the unchanged standard host grant before starting a provider write" in {
      Given("a captured standard host declaration without local closing permissions")
      EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
        fixture(s) { (space, store, _, selected) =>
          val transition = workflow(store)
          val local = collection("ungranted_closing")
          val localId = DataStore.StringEntryId("closing")
          take(store.create(local, localId, Record.dataAuto("revision" -> 1L, "status" -> "open")))
          val guard = take(DataStoreConditionalRoot.create(take(DataStoreComponentOwner.create(owner.name)),
            local, localId, "revision", Some(EntityRevision.INITIAL), Vector.empty,
            Record.dataAuto("status" -> "closed"), take(EntityRevision.createC(2L))))
          val prepared = take(take(space.observeHostEventsC(selected.events :+ event("fresh"))).preparationC)
          When("an ordinary local intent attempts to enlarge that captured grant")
          space.conditionalPreparedEventsWithLocalIntentsVerified(transition, prepared, Vector(guard), Vector.empty)
            .isSuccess shouldBe false
          Then("intent labels do not authorize collections and no native write is attempted")
          store.writes shouldBe 0
          store.commits shouldBe 0
          take(store.load(local, localId)).get.getString("status") shouldBe Some("open")
          inspect(s, false)
        }
      }}
    }

    "rollback every staged record at each precommit checkpoint without commit, readback or retry" in {
      Vector(DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
        DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
        DataStoreConditionalTransitionCheckpoint.RootPrepared,
        DataStoreConditionalTransitionCheckpoint.BeforePublish).foreach { failAt =>
        Given(s"a captured-service preparation and failure at $failAt on the actual managed write provider")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, _, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events :+ event("fresh"))).preparationC)
            store.checkpoint = stage => if (stage == failAt) Consequence.operationInvalid("fixture-staging-failure") else Consequence.unit
            When("the prepared route stages the Workflow and event transaction")
            val result = space.conditionalPreparedEventsVerified(transition, prepared)
            Then("the attempt fails exactly once before native commit or readback")
            result.toOption shouldBe None
            store.writes shouldBe 1
            store.commits shouldBe 0
            store.snapshots shouldBe 0
          }
          Then("fresh post-scope reads show both Workflow records, cursor and new event/index rolled back")
          inspect(s, false)
        }}
      }
    }

    "rollback on in-flight revocation but suppress verification without replay after committed readback revocation" in {
      Vector("staging", "readback").foreach { stage =>
        Given(s"a captured-service preparation and actual lease revocation during $stage")
        EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, _) =>
          fixture(s) { (space, store, lease, selected) =>
            val transition = workflow(store)
            val prepared = take(take(space.observeHostEventsC(selected.events :+ event("fresh"))).preparationC)
            if (stage == "staging") store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) ObservationLeaseTestSupport.revoke(lease)
              Consequence.unit
            } else store.afterReadback = () => ObservationLeaseTestSupport.revoke(lease)
            When("the original host submits its one prepared publication")
            val result = space.conditionalPreparedEventsVerified(transition, prepared)
            Then("no Verified is returned and no automatic write is retried")
            result.toOption shouldBe None
            lease.isRevoked shouldBe true
            store.writes shouldBe 1
            store.commits shouldBe (if (stage == "readback") 1 else 0)
            store.snapshots shouldBe (if (stage == "readback") 1 else 0)
          }
          Then("new-scope inspection distinguishes rollback from already committed but unverified storage")
          inspect(s, stage == "readback")
        }}
      }
    }

    "hold the native write domain against an independent writer after observation validation and release it after commit" in {
      Given("a prepared transaction and a separate SQLite writer with immediate zero-timeout admission")
      EventStoreConstruction.withService(EventStore.inMemory(policy)) { withHost { (s, file) =>
        fixture(s) { (space, store, _, selected) =>
          val transition = workflow(store)
          val prepared = take(take(space.observeHostEventsC(selected.events :+ event("fresh"))).preparationC)
          val start = new CountDownLatch(1)
          val done = new CountDownLatch(1)
          val source = writer(file)
          val pool = Executors.newSingleThreadExecutor()
          given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
          try {
            val attempted = Future {
              try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No write staging")
                try Right(update(source)) catch { case e: SQLException => Left(e.getErrorCode) }
              } finally done.countDown()
            }
            store.checkpoint = cp => {
              if (cp == DataStoreConditionalTransitionCheckpoint.RootPrepared) {
                start.countDown()
                done.await(10, TimeUnit.SECONDS) shouldBe true
              }
              Consequence.unit
            }
            When("the competitor attempts cursor mutation after validation and Workflow staging")
            val result = take(space.conditionalPreparedEventsVerified(transition, prepared))
            Then("the competitor gets exact SQLITE_BUSY and one complete retained/new storage verification succeeds")
            Await.result(attempted, 10.seconds) shouldBe Left(5)
            result shouldBe a[DataStoreMultiGuardReadbackResult.Verified]
            result.asInstanceOf[DataStoreMultiGuardReadbackResult.Verified].records.size shouldBe 9
            store.writes shouldBe 1
            store.commits shouldBe 1
            store.snapshots shouldBe 1
            // Explicit subsequent writer probe proves lock release, not a retry of publication.
            update(source) shouldBe 1
          } finally {
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
          }
        }
        inspect(s, true)
      }}
    }
  }
  private def update(source: SQLiteDataSource, revision: Long = 3L): Int = {
    val c = source.getConnection()
    try {
      val st = c.prepareStatement(s"UPDATE ${SqliteDialectDriver.quoteIdentifier(cursors.collectionName)} SET revision = $revision, last_sequence = '$revision' WHERE id = ?")
      try { st.setString(1, declaration.cursorEntryId.print); st.executeUpdate() }
      finally st.close()
    } finally c.close()
  }

  "Managed event closure isolation" should {
    "retain one cursor/event/index snapshot while a separate writer competes before reference expansion" in withHost { (s, file) =>
      fixture(s) { (space, store, _, selected) =>
        val start = new CountDownLatch(1)
        val done = new CountDownLatch(1)
        val source = writer(file)
        val pool = Executors.newSingleThreadExecutor()
        given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(pool)
        try {
          val attempted = Future {
            try {
              if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No reference expansion")
              try Right(update(source)) catch { case e: SQLException => Left(e.getErrorCode) }
            } finally done.countDown()
          }
          store.beforeRead = (eventsRead, _) => if (eventsRead == 3) {
            start.countDown()
            done.await(10, TimeUnit.SECONDS) shouldBe true
          }
          val snapshot = take(space.observeSelectedEventsC(selected))
          Await.result(attempted, 10.seconds) shouldBe Left(5)
          store.reads shouldBe 3
          store.indexReads shouldBe 2
          store.calls shouldBe 1
          store.commits shouldBe 0
          take(snapshot.classificationC).newDurableCount shouldBe 0
          val retained = take(snapshot.recordsC)
          retained.head._2.get.getString("last_sequence") shouldBe Some("2")
          // Explicit later mutation, not an automatic retry of an uncertain write.
          update(source) shouldBe 1
          take(store.load(cursors, declaration.cursorEntryId)).get.getString("last_sequence") shouldBe Some("3")
          take(snapshot.recordsC).head._2.get.getString("last_sequence") shouldBe Some("2")
        } finally {
          start.countDown()
          pool.shutdown()
          pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
        }
      }
    }

    "reject revocation at entry, reference expansion, intrinsic index expansion and after read cleanup without retry" in {
      Vector("entry", "reference", "intrinsic", "cleanup").foreach { stage => withHost { (s, _) =>
        fixture(s) { (space, store, lease, selected) =>
          if (stage == "entry") ObservationLeaseTestSupport.revoke(lease)
          else if (stage == "cleanup") store.afterCleanup = () => ObservationLeaseTestSupport.revoke(lease)
          else store.beforeRead = (e, i) => if ((stage == "reference" && e == 3) ||
              (stage == "intrinsic" && i == 2)) ObservationLeaseTestSupport.revoke(lease)
          space.observeSelectedEventsC(selected).toOption shouldBe None
          lease.isRevoked shouldBe true
          store.calls shouldBe (if (stage == "entry") 0 else 1)
          store.commits shouldBe 0
          if (stage == "reference") store.indexReads shouldBe 1
          if (stage == "intrinsic" || stage == "cleanup") store.indexReads shouldBe 2
        }
        // A newly admitted host may inspect retained storage, never reuse the revoked snapshot.
        take(s.loadEventCursorC(env(s))).lastSequence shouldBe 2L
      }}
    }
  }
}
