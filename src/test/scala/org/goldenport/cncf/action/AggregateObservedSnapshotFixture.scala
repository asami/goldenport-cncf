package org.goldenport.cncf.action

import cats.~>
import cats.data.State
import cats.effect.Ref
import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateDefinition, ContextualAggregateBuilder}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.unitofwork.*
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Shrink}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
private[cncf] object AggregateObservedSnapshotFixture {
  val instant: Instant = Instant.parse("2026-10-14T03:04:05Z")
  val previousInstant: Instant = Instant.parse("2026-10-13T01:02:03Z")
  val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
  val body: String = "Aggregate overflow 日 α body\n" * 400
  val overflowCollection: DataStore.CollectionId = DataStore.CollectionId("cncf_content_body_overflow")
  val representations: Vector[EntityRevisionRepresentation] = Vector(
    EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)

  enum Mode { case Current, Changed, StaleCurrent, StaleChanged }
  enum Rendering { case Normal, Throw, Null }
  final case class Sample(title: String, untouched: String, mode: Mode,
    representation: EntityRevisionRepresentation)
  final case class Root(id: EntityId, record: Record, rendering: Rendering = Rendering.Normal)
      extends EntityPersistable {
    def toRecord(): Record = rendering match {
      case Rendering.Normal => record
      case Rendering.Throw => throw new IllegalStateException("private callback secret")
      case Rendering.Null => null
    }
    def withTitle(title: String): Root = copy(record = record.upsertSingle("title", title))
  }
  final case class Snapshot(root: Option[Map[String, Any]], overflow: Option[Map[String, Any]],
    unrelatedRoot: Option[Map[String, Any]], unrelatedOverflow: Option[Map[String, Any]])
  final case class Attempt(result: Consequence[EntityRecordSnapshot], callbacks: Int)

  private val _characters = Vector('a', 'Z', '0', '日', '本', '語', 'α', 'β', 'Ω')
  private val _text: Gen[String] = for {
    length <- Gen.chooseNum(1, 32)
    characters <- Gen.listOfN(length, Gen.oneOf(_characters))
  } yield characters.mkString
  val samples: Gen[Sample] = for {
    title <- _text
    untouched <- _text
    mode <- Gen.oneOf(Mode.values.toVector)
    representation <- Gen.oneOf(representations)
  } yield Sample(title, untouched, mode, representation)
  implicit val sampleShrink: Shrink[Sample] = Shrink { sample =>
    def _shorter_(value: String): LazyList[String] =
      if (value.length <= 1) LazyList.empty else LazyList(value.take(math.max(1, value.length / 2)))
    val title = _shorter_(sample.title).map(value => sample.copy(title = value))
    val untouched = _shorter_(sample.untouched).map(value => sample.copy(untouched = value))
    val modes = sample.mode match {
      case Mode.StaleChanged => LazyList(sample.copy(mode = Mode.StaleCurrent), sample.copy(mode = Mode.Changed))
      case Mode.StaleCurrent | Mode.Changed => LazyList(sample.copy(mode = Mode.Current))
      case Mode.Current => LazyList.empty
    }
    (title ++ untouched ++ modes).toStream
  }

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.display)
  }
  def revision(value: Long): EntityRevision = success(EntityRevision.createC(value))
  def reason(result: Consequence[?]): Option[String] = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collectFirst {
      case Descriptor.Facet.Reason(value) => value
    }
    case _ => None
  }
  def symptom(result: Consequence[?]): Option[Taxonomy.Symptom] = result match {
    case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy.symptom)
    case _ => None
  }
  def facets(result: Consequence[?]): Vector[Descriptor.Facet] = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets
    case _ => Vector.empty
  }
  def sqlitePhysical(path: java.nio.file.Path): Map[(String, String), StoredTable] = {
    val connection = java.sql.DriverManager.getConnection(s"jdbc:sqlite:${path.toAbsolutePath}")
    try {
      val catalog = connection.createStatement()
      val names = try {
        val rows = catalog.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        try {
          val builder = Vector.newBuilder[String]
          while (rows.next()) builder += rows.getString(1)
          builder.result()
        } finally rows.close()
      } finally catalog.close()
      names.map { name =>
        val statement = connection.createStatement()
        try {
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\" ORDER BY \"id\"")
          try {
            val metadata = rows.getMetaData
            val columns = (1 to metadata.getColumnCount).toVector.map(index =>
              metadata.getColumnLabel(index) -> metadata.getColumnTypeName(index))
            val builder = Vector.newBuilder[Map[String, Any]]
            while (rows.next()) builder += columns.zipWithIndex.map { case ((key, _), index) =>
              key -> rows.getObject(index + 1)
            }.toMap
            ("main" -> name) -> StoredTable(columns, builder.result())
          } finally rows.close()
        } finally statement.close()
      }.toMap
    } finally connection.close()
  }
  def changedPhysical(before: Map[(String, String), StoredTable], setup: Fixture,
    title: String): Map[(String, String), StoredTable] = before.map { case (key, table) =>
    val rows = if (key._2 == setup.dataCollection.collectionName) table.rows.map { row =>
      if (row.get("id").contains(DataStore.EntryId(setup.id).print))
        row.updated("title", title).updated(setup.revisionField, 2L)
          .updated("updated_at", instant.toString).updated("updated_by", "owner")
      else row
    } else table.rows
    key -> table.copy(rows = rows)
  }
  def fixture(token: String, representation: EntityRevisionRepresentation,
    provider: DataStore, alias: Boolean = false, managed: Boolean = true,
    optimistic: Boolean = true, registered: Boolean = true, rootRegistered: Boolean = true,
    managerOnly: Boolean = false, mismatch: Boolean = false, invalidBinding: Boolean = false): Fixture =
    new Fixture(EntityCollectionId("test", "aggregate", token), representation, provider,
      alias, managed, optimistic, registered, rootRegistered, managerOnly, mismatch, invalidBinding)

  final class MemoryStore extends EntityObservedDeleteCanonicalProviderFixture.MemoryStore() {
    private var _root_publications = 0
    private var _body_publications = 0
    def rootPublications: Int = _root_publications
    def bodyPublications: Int = _body_publications
    override def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] =
      super.mutateVersionedEntity(plan).map { result =>
        result match {
          case EntityVersionedMutationResult.Applied(_) =>
            _root_publications += 1
            _body_publications += plan.sideEffects.count {
              case EntityVersionedSideEffect.Save(_, _, _) => true
              case _ => false
            }
          case _ => ()
        }
        result
      }
    def clearPublications(): Unit = { _root_publications = 0; _body_publications = 0 }
  }

  final class Fixture(val collectionId: EntityCollectionId, val representation: EntityRevisionRepresentation,
    val provider: DataStore, alias: Boolean, managed: Boolean, optimistic: Boolean,
    registered: Boolean, rootRegistered: Boolean, managerOnly: Boolean, mismatch: Boolean, invalidBinding: Boolean) {
    val rootName: String = collectionId.name
    val aggregateName: String = if (alias) s"${rootName}_aggregate" else rootName
    val id: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("aggregate", "selected",
      collectionId, entropy = "aggregate_selected")
    val unrelatedId: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("aggregate", "unrelated",
      collectionId, entropy = "aggregate_unrelated")
    val dataCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(collectionId)
    val revisionField: String = representation.storageFieldName
    private val _datastores = new DataStoreSpace().useDataStore(provider)
    private val _entitystores = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val persistent: EntityPersistent[Root] = new EntityPersistent[Root] {
      def id(entity: Root): EntityId = entity.id
      def toRecord(entity: Root): Record = entity.toRecord()
      override def toStoreRecord(entity: Root): Record = entity.toRecord().upsertSingle("id", entity.id.value)
      def fromRecord(record: Record): Consequence[Root] = {
        val identity = record.getAny("id") match {
          case Some(value: EntityId) => Consequence.success(value)
          case Some(value: String) => EntityId.parse(value)
          case _ => Consequence.argumentMissing("id")
        }
        identity.map(value => Root(value, record.upsertSingle("id", value)))
      }
    }
    val component: Component = new Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        if (alias) Vector(AggregateDefinition(aggregateName, rootName)) else Vector.empty
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
        CmlOperationDefinition(name = "changeTitle", kind = "command", inputType = "Root", outputType = "Root",
          inputValueKind = "record", access = Some(CmlOperationAccess(if (managerOnly) "manager_only" else "authenticated_only"))))
    }
    private val _observability = ObservabilityContext(TraceId("test", "aggregate_observed"), None,
      Some(CorrelationId("test", "aggregate_observed")))
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "aggregate-root", None, _observability)
    private val _component_scope = Component.Context("aggregate-component", _root_scope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "aggregate-runtime", parent = Some(_component_scope),
        observabilityContext = _observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(_entitystores))),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "aggregate-observed-runtime")
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("owner")
        def attributes: Map[String, String] = Map("access_token" -> "aggregate-fixture-token")
      }
      base.copy(core = base.core.copy(clock = clock), cncfCore = base.cncfCore.copy(
        security = SecurityContext(principal, Set.empty, SecurityLevel("test"))))
    }
    val collection: EntityCollection[Root] = makeCollection(collectionId)
    if (rootRegistered) component.entitySpace.registerEntity(rootName, collection)
    if (managed && rootRegistered && !invalidBinding) EntityRevisionSpecSupport.registerRevisionBinding(context, collectionId, persistent,
      representation, if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None)
    if (registered) component.aggregateSpace.register(aggregateName, new AggregateCollection(new ContextualAggregateBuilder[Root] {
      def build_with_context(targetId: EntityId)(using ctx: ExecutionContext): Consequence[Root] =
        ctx.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(targetId, persistent))
          .flatMap(value => Consequence.successOrEntityNotFound(value)(targetId))
          .map(value => if (mismatch) value.withTitle("mismatched Aggregate") else value)
    }))
    private var _view_builds = 0
    val view: ViewCollection[String] = new ViewCollection(new ViewBuilder[String] {
      def build(targetId: EntityId): Consequence[String] = {
        _view_builds += 1
        loadC(targetId).map(_.record.getString("title").get)
      }
    })
    component.viewSpace.register(rootName, view, Browser.from(view, _ => view.resolve(id).map(Vector(_))))

    def makeCollection(collectionId: EntityCollectionId): EntityCollection[Root] = {
      given EntityPersistent[Root] = persistent
      val metadata = EntityRevisionModelMetadata(
        if (representation == EntityRevisionRepresentation.Embedded) EntityRevisionModelKind.SimpleEntity
        else EntityRevisionModelKind.NonSimpleEntity, if (managed) Some(representation) else None)
      val binding = if (invalidBinding) Some(EntityRevisionBinding(null))
        else if (managed) success(EntityRevisionBinding.resolve(metadata, Some(representation))) else None
      val realm = new EntityRealm[Root](rootName, EntityLoader[Root](_ => None), new IdRef(EntityRealmState[Root](Map.empty)))
      new EntityCollection(EntityDescriptor(collectionId, EntityRuntimePlan(entityName = rootName,
        memoryPolicy = EntityMemoryPolicy.LoadToMemory, workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC, maxPartitions = 4, maxEntitiesPerPartition = 16,
        concurrencyPolicy = if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None),
        persistent, revisionBinding = binding), EntityStorage(realm,
          Some(new PartitionedMemoryRealm[Root](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id))))
    }
    def peer(provider: DataStore): Fixture = new Fixture(collectionId, representation, provider, alias,
      managed, optimistic, registered, rootRegistered, managerOnly, mismatch = false, invalidBinding = invalidBinding)
    def viewBuilds: Int = _view_builds
    def overflowEntry(identity: EntityId): DataStore.EntryId = DataStore.StringEntryId(s"${identity.value}:content")
    def loadC(identity: EntityId = id): Consequence[Root] =
      context.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(identity, persistent))(using context)
        .flatMap(value => Consequence.successOrEntityNotFound(value)(identity))
    def authoritativeC(): Consequence[EntityRecordSnapshot] =
      if (representation == EntityRevisionRepresentation.Embedded)
        context.entityStoreSpace.loadSnapshot(id, persistent)(using context)
          .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
          .map(value => EntityRecordSnapshot(value.entity.record, value.revision))
      else context.entityStoreSpace.loadDetached(id, persistent)(using context)
        .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
        .map(value => EntityRecordSnapshot(value.entity.record, value.revision))
    def snapshot(reader: DataStore = provider): Snapshot = {
      def _row_(cid: DataStore.CollectionId, entry: DataStore.EntryId): Option[Map[String, Any]] =
        success(reader.load(cid, entry)(using context)).map(_.asMap)
      Snapshot(_row_(dataCollection, DataStore.EntryId(id)), _row_(overflowCollection, overflowEntry(id)),
        _row_(dataCollection, DataStore.EntryId(unrelatedId)), _row_(overflowCollection, overflowEntry(unrelatedId)))
    }
    def seed(title: String = "Before", untouched: String = "untouched independent value", initialRevision: Long = 1L): Unit = {
      given ExecutionContext = context
      Vector(id -> initialRevision, unrelatedId -> 91L).foreach { case (identity, version) =>
        val source = Record.dataAuto("id" -> identity.value,
          "title" -> (if (identity == id) title else "Unrelated independent title"), "business" -> untouched,
          "content" -> body, revisionField -> version, "short_id" -> identity.parts.entropy, "name" -> "Retained name",
          "created_at" -> previousInstant.toString, "created_by" -> "creator",
          "updated_at" -> previousInstant.toString, "updated_by" -> "prior-editor",
          "post_status" -> "draft", "aliveness" -> "alive", "owner_id" -> "owner", "group_id" -> "owner",
          "privilege_id" -> "owner", "permission" -> SimpleEntityStorageShapePolicy.permissionJson(SecurityAttributes.ownedBy("owner").rights),
          "trace_id" -> "original-trace", "correlation_id" -> "original-correlation", "audit" -> "independent retained audit",
          "nullable" -> org.simplemodeling.model.directive.Update.SetNull)
        val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(identity, source))
        success(provider.save(dataCollection, DataStore.EntryId(identity), preparation.record))
        preparation.sideEffects.foreach {
          case EntityVersionedSideEffect.Save(cid, entry, record) => success(provider.save(cid, entry, record))
          case EntityVersionedSideEffect.Delete(cid, entry) => success(provider.delete(cid, entry))
        }
      }
      collection.evict(id)
      collection.evict(unrelatedId)
      component.viewSpace.invalidate(rootName)
      provider match { case store: MemoryStore => store.clearPublications(); case _ => () }
    }
    def prime(): Unit = {
      collection.putScoped(success(loadC()))(using context)
      success(view.resolve(id))
    }
    def attempt(command: Root => Consequence[Root], expected: EntityRevision = EntityRevision.INITIAL,
      targetId: EntityId = id, aggregateName: String = this.aggregateName,
      commandName: String = "changeTitle"): Attempt = {
      val call = ActionCallSupport.actionCall("changeTitle", ActionCallSupport.componentPair(component, context)) { core =>
        new SnapshotCall(core, aggregateName, targetId, commandName, expected, command)
      }.asInstanceOf[SnapshotCall]
      call.execute()
      Attempt(call.snapshotResult.get, call.callbacks)
    }
    def writeC(title: String, expected: EntityRevision = EntityRevision.INITIAL): Consequence[EntityRecordSnapshot] =
      loadC().flatMap { current =>
        val policy = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.WriteIfChanged,
          RevisionPreconditionPolicy.ObservedRequired, Some(expected))
        if (representation == EntityRevisionRepresentation.Embedded)
          collection.saveRecordVersioned(current.withTitle(title).record, Some(expected), policy)(using context)
        else collection.saveRecordDetached(current.withTitle(title).record, Some(expected), policy)(using context)
          .map(value => EntityRecordSnapshot(value.entity, value.revision))
      }
    def expectedChanged(before: Snapshot, title: String): Snapshot = {
      val timestamp: Any = provider match { case _: SqlDataStore => instant.toString; case _ => instant }
      before.copy(root = before.root.map(_.updated("title", title).updated(revisionField, 2L)
        .updated("updated_at", timestamp).updated("updated_by", "owner")))
    }
  }

  final class SnapshotCall(val core: ActionCall.Core, aggregateName: String, targetId: EntityId,
    commandName: String, observed: EntityRevision, command: Root => Consequence[Root]) extends ProcedureActionCall {
    private var _snapshot_result: Option[Consequence[EntityRecordSnapshot]] = None
    private var _callbacks = 0
    def snapshotResult: Option[Consequence[EntityRecordSnapshot]] = _snapshot_result
    def callbacks: Int = _callbacks
    override def execute(): Consequence[OperationResponse] = {
      val callback: Root => Consequence[Root] = if (command == null) null else current => {
        _callbacks += 1
        command(current)
      }
      val result = aggregate_update_observed_snapshot_c[Root](aggregateName, targetId, commandName, observed)(callback)
      _snapshot_result = Some(result)
      result.map(value => OperationResponse.RecordResponse(value.record))
    }
  }

  trait Vocabulary extends Matchers with GivenWhenThen {
    self: org.scalatest.Informing =>
    def checkSnapshot(setup: Fixture, reader: Fixture, result: Consequence[EntityRecordSnapshot],
      title: String, expectedRevision: Long): Unit = {
      val snapshot = success(result)
      Then("the actual result equals the complete independent canonical snapshot with the prescribed identity, title and revision")
      snapshot shouldBe success(reader.authoritativeC())
      snapshot.revision.value shouldBe expectedRevision
      snapshot.record.getAs[EntityId]("id") shouldBe Some(setup.id)
      snapshot.record.getString("title") shouldBe Some(title)
      snapshot.record.getString("content") shouldBe Some(body)
      success(setup.collection.resolveScoped(setup.id)(using setup.context)).record shouldBe snapshot.record
    }
    def checkStale(result: Consequence[EntityRecordSnapshot], expectedRevision: Long, actualRevision: Long): Unit = {
      Then("the canonical conflict carries the independently prescribed expected and actual revision facets")
      result shouldBe a[Consequence.Failure[?]]
      reason(result) shouldBe Some("stale-entity-revision")
      symptom(result) shouldBe Some(Taxonomy.Symptom.Conflict)
      facets(result) should contain(Descriptor.Facet.Expected(expectedRevision))
      facets(result) should contain(Descriptor.Facet.Actual(actualRevision))
    }
    def checkSample(sample: Sample, setup: Fixture, reader: Fixture,
      physical: () => Map[(String, String), StoredTable] = () => Map.empty): Boolean = {
      Given("the bounded selected and unrelated full records reset sequentially at INITIAL1")
      setup.seed(sample.title, sample.untouched)
      val initial = setup.snapshot(reader.provider)
      val initialphysical = physical()
      val stale = sample.mode == Mode.StaleCurrent || sample.mode == Mode.StaleChanged
      val winningtitle = "winner:" + sample.title
      if (stale) {
        When("an actual canonical independent provider publishes the winning revision")
        val winner = reader.writeC(winningtitle)
        Then("the complete winner and all unrelated/body state correspond")
        checkSnapshot(reader, setup, winner, winningtitle, 2L)
        setup.snapshot(reader.provider) shouldBe setup.expectedChanged(initial, winningtitle)
        physical() shouldBe changedPhysical(initialphysical, setup, winningtitle)
      }
      Given("the complete authoritative rows and View observed before the generated Aggregate attempt")
      setup.prime()
      val before = setup.snapshot(reader.provider)
      val beforephysical = physical()
      val current = success(reader.authoritativeC())
      val view = success(setup.view.resolve(setup.id))
      val builds = setup.viewBuilds
      val changed = sample.mode == Mode.Changed || sample.mode == Mode.StaleChanged
      val targettitle = if (changed) "changed:" + sample.title else if (stale) winningtitle else sample.title
      When("the actual typed Aggregate API interprets changed/current/stale generated input")
      val attempt = setup.attempt(value => Consequence.success(value.withTitle(targettitle)))
      Then("the canonical outcome and every full independent persistence observation satisfy the law")
      if (stale) {
        checkStale(attempt.result, 1L, 2L)
        attempt.callbacks shouldBe 0
        setup.snapshot(reader.provider) shouldBe before
        physical() shouldBe beforephysical
        success(setup.view.resolve(setup.id)) shouldBe view
        setup.viewBuilds shouldBe builds
      } else {
        checkSnapshot(setup, reader, attempt.result, targettitle, if (changed) 2L else 1L)
        attempt.callbacks shouldBe 1
        if (changed) {
          setup.snapshot(reader.provider) shouldBe setup.expectedChanged(before, targettitle)
          physical() shouldBe changedPhysical(beforephysical, setup, targettitle)
          success(setup.view.resolve(setup.id)) shouldBe targettitle
          setup.viewBuilds shouldBe builds + 1
        } else {
          success(attempt.result) shouldBe current
          setup.snapshot(reader.provider) shouldBe before
          physical() shouldBe beforephysical
          success(setup.view.resolve(setup.id)) shouldBe view
          setup.viewBuilds shouldBe builds
          setup.provider match {
            case store: MemoryStore => store.rootPublications shouldBe 0; store.bodyPublications shouldBe 0
            case _ => ()
          }
        }
      }
      true
    }
  }

  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = _value
    def set(value: A): Unit = { _value = value }
    override def getAndSet(value: A): A = { val previous = _value; _value = value; previous }
    def access: (A, A => Boolean) = {
      val previous = _value
      (previous, next => if (_value == previous) { _value = next; true } else false)
    }
    override def tryUpdate(f: A => A): Boolean = { update(f); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = Some(modify(f))
    def update(f: A => A): Unit = { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = { val (next, result) = f(_value); _value = next; result }
    override def modifyState[B](state: State[A, B]): B = { val (next, result) = state.run(_value).value; _value = next; result }
    override def tryModifyState[B](state: State[A, B]): Option[B] = Some(modifyState(state))
  }
}
