package org.goldenport.cncf.unitofwork

import cats.~>
import cats.data.State
import cats.effect.Ref
import cats.free.Free
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.sql.{Connection, Types}
import java.time.{Clock, Instant, ZoneOffset}
import javax.sql.DataSource
import org.goldenport.{Consequence, ConsequenceT, Conclusion}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{Database, StoredTable, AcknowledgementStore}
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityCreateFailureSafetyFixture.{BodyMode, FaultMode, FaultTarget}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.observation.Descriptor
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Shrink}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] object UnitOfWorkMutationCaptureFixture {
  val instant: Instant = Instant.parse("2026-10-15T01:02:03Z")
  val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
  val permission: String = "{\"owner\":{\"read\":true,\"write\":true,\"execute\":false},\"group\":{\"read\":false,\"write\":false,\"execute\":false},\"other\":{\"read\":false,\"write\":false,\"execute\":false}}"
  val trace: TraceId = TraceId("test", "mutation_capture")
  val correlation: CorrelationId = CorrelationId("test", "mutation_capture")
  val bodyCollection: DataStore.CollectionId = DataStore.CollectionId("cncf_content_body_overflow")
  val representations: Vector[EntityRevisionRepresentation] = EntityRevisionRepresentation.values.toVector
  enum DecodeMode { case Good, Structured, Throw, NullConsequence, NullFailure, NullEntity }
  final case class Input(identity: Option[EntityId], record: Record)
  final case class Entity(id: EntityId, record: Record)
  final case class Sample(title: String, business: String, version: Long, body: BodyMode,
    representation: EntityRevisionRepresentation, supplied: Boolean, logical: Boolean, mode: DecodeMode)
  final case class Rows(fresh: Option[Record], freshbody: Option[Record], selected: Option[Record],
    selectedbody: Option[Record], unrelated: Option[Record], unrelatedbody: Option[Record])
  final case class Expected(rows: Rows, physical: Map[(String, String), StoredTable], raw: Record)
  final case class Resident(selected: Option[Entity], fresh: Option[Entity], unrelated: Option[Entity],
    memoryselected: Option[Entity], memoryfresh: Option[Entity], memoryunrelated: Option[Entity])
  final case class Environment(writer: DataStore, reader: DataStore, exact: DataStore,
    source: Option[DataSource], postgresql: Boolean, normalize: Boolean,
    physical: () => Map[(String, String), StoredTable]) {
    def sql: Boolean = source.nonEmpty
  }
  final class CanonicalId(collectionId: EntityCollectionId, token: String)
      extends EntityId("single", "global", collectionId, Some(instant), Some(token))

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
  def conclusion(result: Consequence[?]): Conclusion = result match {
    case Consequence.Failure(value) => value
    case _ => throw new IllegalStateException("Expected a structured failure")
  }
  def field(name: String, value: Any): Field = Field(name, Field.Value.Single(value))
  def record(values: (String, Any)*): Record = Record(values.toVector.map { case (name, value) => field(name, value) })
  def permissionRecord: Record = record(
    "owner" -> record("read" -> true, "write" -> true, "execute" -> false),
    "group" -> record("read" -> false, "write" -> false, "execute" -> false),
    "other" -> record("read" -> false, "write" -> false, "execute" -> false))
  def digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8))
    .iterator.map(value => f"${value & 0xff}%02x").mkString
  def content(sample: Sample): String = if (sample.body == BodyMode.Overflow) (sample.title + " body 日本 Ω ") * 700 else sample.title + " inline"
  def sample: Sample = Sample("Candidate 日本", "business retained Ω", 7L, BodyMode.Overflow,
    EntityRevisionRepresentation.Detached, true, false, DecodeMode.Good)

  private val _aliases = Map("short_id" -> "shortId", "created_at" -> "createdAt", "created_by" -> "createdBy",
    "updated_at" -> "updatedAt", "updated_by" -> "updatedBy", "post_status" -> "postStatus", "owner_id" -> "ownerId",
    "group_id" -> "groupId", "privilege_id" -> "privilegeId", "trace_id" -> "traceId", "correlation_id" -> "correlationId",
    "content_byte_size" -> "contentByteSize", "content_digest" -> "contentDigest", "content_charset" -> "contentCharset",
    "content_storage" -> "contentStorage", "content_ref" -> "contentRef", "source_entity_id" -> "sourceEntityId",
    "deleted_at" -> "deletedAt", "deleted_by" -> "deletedBy")
  val rootColumns: Vector[String] = Vector("id", "title", "business", "nullable", "content", "short_id", "name",
    "created_at", "created_by", "updated_at", "updated_by", "post_status", "aliveness", "owner_id", "group_id",
    "privilege_id", "permission", "trace_id", "correlation_id", "content_byte_size", "content_digest", "content_charset",
    "content_storage", "content_ref", "deleted_at", "deleted_by")
  val bodyColumns: Vector[String] = Vector("id", "source_entity_id", "field", "content", "content_byte_size", "content_digest", "content_charset")

  def memory(writer: DataStore = new EntityCreateFailureSafetyFixture.MemoryStore()): Environment =
    Environment(writer, writer, writer, None, false, false, () => Map.empty)
  def withSqlite[A](normalize: Boolean = false, acknowledgement: Boolean = false)(body: Environment => A): A =
    EntityObservedDeleteCanonicalProviderFixture.withSqlite { database =>
      val source = EntityCreateProviderFixture.sqliteSource(database)
      val config = SqlDataStore.Config(normalizeColumnNames = normalize)
      val writer = if (acknowledgement) new AcknowledgementStore(SqliteDialectDriver, source)
        else new EntityCreateFailureSafetyFixture.SqlStore(SqliteDialectDriver, source, config)
      val reader = new SqlDataStore(SqliteDialectDriver, source, config = config)
      val exact = new SqlDataStore(SqliteDialectDriver, source)
      try body(Environment(writer, reader, exact, Some(source), false, normalize, () => snapshotSqlite(source)))
      finally { success(writer.closeC()); success(reader.closeC()); success(exact.closeC()) }
    }
  def postgresql(database: Database, normalize: Boolean, acknowledgement: Boolean = false): Environment =
    Environment(if (acknowledgement) database.openAcknowledgement() else database.openFault(normalize),
      database.open(normalize), database.open(), Some(database.source()), true, normalize, () => database.snapshotAll())
  def snapshotSqlite(source: DataSource): Map[(String, String), StoredTable] = {
    val connection = source.getConnection()
    try {
      val statement = connection.createStatement()
      val tables = try {
        val rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        try { val names = Vector.newBuilder[String]; while (rows.next()) names += rows.getString(1); names.result() }
        finally rows.close()
      } finally statement.close()
      tables.map { table =>
        val reader = connection.createStatement()
        try {
          val rows = reader.executeQuery(s"SELECT * FROM ${quote(table)} ORDER BY id")
          try {
            val metadata = rows.getMetaData
            val columns = (1 to metadata.getColumnCount).toVector.map(index => metadata.getColumnLabel(index) -> metadata.getColumnTypeName(index))
            val records = Vector.newBuilder[Map[String, Any]]
            while (rows.next()) records += columns.zipWithIndex.map { case ((name, _), index) => name -> rows.getObject(index + 1) }.toMap
            ("main" -> table) -> StoredTable(columns, records.result())
          } finally rows.close()
        } finally reader.close()
      }.toMap
    } finally connection.close()
  }
  def quote(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

  // This observer delegates all capabilities, writers and lifecycle calls; no result is manufactured.
  final class RecordingStore(val underlying: DataStore) extends DataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    private val _versioned = underlying.asInstanceOf[EntityVersionedMutationDataStore]
    private val _create = underlying.asInstanceOf[EntityCreateDataStore]
    private var _creates = 0
    private var _deletes = 0
    private var _writes = 0
    private var _lifecycle = Vector.empty[String]
    private var _created: Option[EntityCreateResult] = None
    private var _deleted: Option[EntityCompareAndDeleteResult] = None
    private var _after_create: () => Unit = () => ()
    private var _after_delete: () => Unit = () => ()
    def creates: Int = _creates
    def deletes: Int = _deletes
    def writes: Int = _writes
    def lifecycle: Vector[String] = _lifecycle
    def created: Option[EntityCreateResult] = _created
    def deleted: Option[EntityCompareAndDeleteResult] = _deleted
    def reset(afterCreate: () => Unit, afterDelete: () => Unit): Unit = {
      _creates = 0; _deletes = 0; _writes = 0; _lifecycle = Vector.empty; _created = None; _deleted = None
      _after_create = afterCreate; _after_delete = afterDelete
    }
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = _versioned.entityMutationProviderCapabilities
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.create(collection, id, record)
    }
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.save(collection, id, record)
    }
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.update(collection, id, changes)
    }
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _writes += 1; underlying.delete(collection, id)
    }
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = { _lifecycle :+= "prepare"; underlying.prepare(tx) }
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _lifecycle :+= "commit"; underlying.commit(tx) }
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _lifecycle :+= "abort"; underlying.abort(tx) }
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      _creates += 1
      _create.createEntity(plan).map { result => _created = Some(result); _after_create(); result }
    }
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] = {
      _deletes += 1
      _versioned.compareAndDeleteEntity(plan).map { result =>
        _deleted = Some(result)
        result match { case _: EntityCompareAndDeleteResult.Deleted => _after_delete(); case _ => () }
        result
      }
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = _versioned.mutateVersionedEntity(plan)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = _versioned.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = _versioned.compareAndSetEntity(plan)
  }
  final class RecordingSpace extends EntityStoreSpace {
    private var _creates = 0
    private var _deletes = 0
    private var _created: Option[CreateResult[?]] = None
    private var _deleted: Option[EntityDeletionResult] = None
    def creates: Int = _creates
    def deletes: Int = _deletes
    def created: Option[CreateResult[?]] = _created
    def deleted: Option[EntityDeletionResult] = _deleted
    def reset(): Unit = { _creates = 0; _deletes = 0; _created = None; _deleted = None }
    override def create[T](op: UnitOfWorkOp.EntityStoreCreate[T])(using ctx: ExecutionContext): Consequence[CreateResult[T]] = {
      _creates += 1; super.create(op).map { result => _created = Some(result); result }
    }
    override def deleteObserved(op: UnitOfWorkOp.EntityStoreDeleteObserved)(using ctx: ExecutionContext): Consequence[EntityDeletionResult] = {
      _deletes += 1; super.deleteObserved(op).map { result => _deleted = Some(result); result }
    }
  }

  final class Session(val token: String, val sample: Sample, val environment: Environment,
    managed: Boolean = true, registered: Boolean = true, principal: String = "owner", denyFactory: Boolean = false) {
    val collectionId: EntityCollectionId = EntityCollectionId("test", "capture", token)
    val id: EntityId = new CanonicalId(collectionId, "selected")
    val freshId: EntityId = new CanonicalId(collectionId, "fresh")
    val unrelatedId: EntityId = new CanonicalId(collectionId, "unrelated")
    val revisionField: String = sample.representation.storageFieldName
    val dataCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(collectionId)
    val recording = new RecordingStore(environment.writer)
    val space = new RecordingSpace()
    space.addEntityStore(EntityStore.standard())
    private val _datastores = new DataStoreSpace().useDataStore(recording)
    private var _factory_calls = Vector.empty[UnitOfWorkAuthorization]
    private var _decode_mode = DecodeMode.Good
    private var _decode_calls = 0
    private var _throw_evict = false
    private var _coherence = Vector.empty[String]
    private var _epoch = 0
    private var _view_builds = 0
    private var _id_calls = 0
    val decodeFailure: Consequence.Failure[Entity] = Consequence.operationInvalid("registered-capture-decode")
    val component: Component = new Component() {
      override def factoryOption: Option[Component.Factory] = Some(new Component.Factory {
        protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
        protected def create_Component(params: ComponentCreate): Component = new Component() {}
        override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
          _factory_calls :+= authorization
          if (denyFactory) Some(Consequence.securityPermissionDenied("capture-factory-denied")) else None
        }
      })
    }
    private val _observability = ObservabilityContext(trace, None, Some(correlation))
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "capture-root", None, _observability)
    private val _component_scope = Component.Context("capture-component", _root_scope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "capture-runtime", parent = Some(_component_scope),
        observabilityContext = _observability, httpDriverOption = None, datastore = Some(DataStoreContext(_datastores)),
        entitystore = Some(EntityStoreContext(space)), entityspace = Some(new EntitySpaceContext(component.entitySpace))),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "capture-runtime")
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val subject = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = Map.empty
      }
      val generation = new IdGenerationContext {
        val namespace: IdGenerationContext.IdNamespace = IdGenerationContext.IdNamespace("single", "global")
        def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
          require(collection == collectionId && purpose == "entity-create")
          _id_calls += 1; freshId
        }
        def opaqueId(purpose: String): String = s"capture-$purpose"
      }
      ExecutionContext.withIdGenerationContext(base.copy(core = base.core.copy(clock = clock), cncfCore = base.cncfCore.copy(
        observability = _observability,
        security = SecurityContext(subject, Set.empty, SecurityLevel("test")))), generation)
    }
    val persistent: EntityPersistent[Entity] = new EntityPersistent[Entity] {
      def id(entity: Entity): EntityId = entity.id
      def toRecord(entity: Entity): Record = entity.record
      def fromRecord(record: Record): Consequence[Entity] = {
        _decode_calls += 1; _coherence :+= "decode"
        _decode_mode match {
          case DecodeMode.Structured => decodeFailure
          case DecodeMode.Throw => throw new IllegalStateException("private-database-credential")
          case DecodeMode.NullConsequence => null
          case DecodeMode.NullFailure => Consequence.Failure(null)
          case DecodeMode.NullEntity => Consequence.success(null)
          case DecodeMode.Good =>
            val value = record.getAny("id")
            Vector(Session.this.id, freshId, unrelatedId).find(identity => value.contains(identity) || value.contains(identity.print))
              .map(identity => Consequence.success(Entity(identity, record)))
              .getOrElse(Consequence.argumentInvalid("id", "declared fixture identity", "foreign identity"))
        }
      }
    }
    val createPersistent: EntityPersistentCreate[Input] = new EntityPersistentCreate[Input] {
      def id(entity: Input): Option[EntityId] = entity.identity
      def collection(entity: Input): EntityCollectionId = collectionId
      def toRecord(entity: Input): Record = entity.record
    }
    val realm: EntityRealm[Entity] = new EntityRealm[Entity](collectionId.name, EntityLoader[Entity](_ => None),
      new IdRef(EntityRealmState[Entity](Map.empty)))(using persistent) {
      override def remove(id: EntityId): Boolean = {
        _coherence :+= s"evict-view-${success(view.resolve(id))}"
        if (_throw_evict) throw new IllegalStateException("private-eviction-detail")
        super.remove(id)
      }
    }
    val memoryRealm = new PartitionedMemoryRealm[Entity](PartitionStrategy.byOrganizationMonthUTC, _.id)
    val collection: EntityCollection[Entity] = new EntityCollection(EntityDescriptor(collectionId,
      EntityRuntimePlan[Entity](collectionId.name, EntityMemoryPolicy.LoadToMemory, None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC, maxPartitions = 4, maxEntitiesPerPartition = 16),
      persistent, if (managed) Some(EntityRevisionBinding(sample.representation)) else None), EntityStorage(realm, Some(memoryRealm)))
    if (registered) component.entitySpace.registerEntity(collectionId.name, collection)
    val view = new ViewCollection[Int](new ViewBuilder[Int] {
      def build(id: EntityId): Consequence[Int] = { _view_builds += 1; Consequence.success(_epoch) }
    })
    component.viewSpace.register(collectionId.name, view, Browser.from(view, _ => Consequence.success(Vector(_epoch))))
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    def idCalls: Int = _id_calls
    def decodeCalls: Int = _decode_calls
    def coherence: Vector[String] = _coherence
    def factoryCalls: Vector[UnitOfWorkAuthorization] = _factory_calls
    def viewBuilds: Int = _view_builds
    def entry(identity: EntityId): DataStore.EntryId = DataStore.EntryId(identity)
    def bodyEntry(identity: EntityId): DataStore.EntryId = DataStore.StringEntryId(s"${identity.value}:content")
    def input: Input = {
      val fields = Vector(field("title", sample.title), field("business", sample.business), field("nullable", Update.SetNull)) ++
        (if (sample.supplied) Vector(field("id", freshId)) else Vector.empty) ++ (sample.body match {
          case BodyMode.Absent => Vector.empty
          case BodyMode.SetNull => Vector(field("content", Update.SetNull))
          case _ => Vector(field("content", content(sample)))
        })
      Input(if (sample.supplied) Some(freshId) else None, Record(fields))
    }
    def authorization(kind: String, identity: Option[EntityId] = None): UnitOfWorkAuthorization =
      UnitOfWorkAuthorization("domain", Some("CaptureEntity"), Some(collectionId.name), identity, kind,
        sourceComponentName = Some("capture-component"), targetComponentName = Some("capture-component"),
        entityNames = Vector("CaptureEntity"), accessMode = EntityAccessMode.UserPermission)
    def createOperation: UnitOfWorkOp.EntityStoreCreateOutcome[Input] =
      UnitOfWorkOp.EntityStoreCreateOutcome(input, createPersistent, authorization = Some(authorization("create")))
    def deleteOperation(expectedRevision: EntityRevision = revision(sample.version)): UnitOfWorkOp.EntityStoreDeleteObservedOutcome =
      UnitOfWorkOp.EntityStoreDeleteObservedOutcome(id, expectedRevision, Some(authorization("delete", Some(id))))
    def program[A](operation: UnitOfWorkOp[A]): ExecUowM[A] = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, A](operation))
    def evaluateC[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(program(operation))
    def arm(mode: DecodeMode = sample.mode, throwEvict: Boolean = false): Unit = {
      _decode_mode = DecodeMode.Good; _throw_evict = false; _decode_calls = 0; _coherence = Vector.empty; _factory_calls = Vector.empty; _id_calls = 0
      space.reset()
      recording.reset(() => { _decode_mode = mode; _epoch = 1 }, () => { _throw_evict = throwEvict; _epoch = 1 })
    }
    def resident: Resident = Resident(realm.get(id), realm.get(freshId), realm.get(unrelatedId),
      memoryRealm.get(id), memoryRealm.get(freshId), memoryRealm.get(unrelatedId))
    def rows(reader: DataStore = environment.reader): Rows = {
      def _read_(collection: DataStore.CollectionId, entry: DataStore.EntryId): Option[Record] = success(reader.load(collection, entry)(using context))
      Rows(_read_(dataCollection, entry(freshId)), _read_(bodyCollection, bodyEntry(freshId)),
        _read_(dataCollection, entry(id)), _read_(bodyCollection, bodyEntry(id)),
        _read_(dataCollection, entry(unrelatedId)), _read_(bodyCollection, bodyEntry(unrelatedId)))
    }
    def rootColumnNames: Vector[String] = rootColumns ++ (if (environment.normalize) Vector.empty else Vector("deletedAt")) :+ revisionField
    def declaredColumns(body: Boolean): Vector[(String, String)] = (if (body) bodyColumns else rootColumnNames).map { name =>
      name -> (if (name == revisionField || name == "content_byte_size") {
        if (environment.postgresql) "int8" else "INTEGER"
      } else if (environment.postgresql) "text" else "TEXT")
    }
    def seed(): Unit = {
      given ExecutionContext = context
      environment.source.foreach { source =>
        val connection = source.getConnection()
        try Vector(dataCollection.collectionName -> rootColumnNames, bodyCollection.collectionName -> bodyColumns).foreach { case (name, columns) =>
          val definitions = columns.map(column => quote(column) + " " +
            (if (column == revisionField || column == "content_byte_size") {
              if (environment.postgresql) "BIGINT" else "INTEGER"
            } else "TEXT") + (if (column == "id") " PRIMARY KEY" else ""))
          val statement = connection.createStatement()
          try statement.execute(s"CREATE TABLE IF NOT EXISTS ${quote(name)} (${definitions.mkString(",")})") finally statement.close()
        } finally connection.close()
      }
      Vector(id -> sample.version, unrelatedId -> 91L).foreach { case (identity, version) =>
        val text = "retained overflow 日本 Ω " * 700
        val body = bodyRecord(identity, text)
        val root = record("id" -> identity.print, "title" -> "Before", "business" -> "retained business",
          "owner_id" -> "owner", "group_id" -> "owner", "privilege_id" -> "owner", "permission" -> permission,
          "post_status" -> "published", "created_at" -> instant, "created_by" -> "owner", "updated_at" -> instant,
          "updated_by" -> "owner", "trace_id" -> trace.value, "correlation_id" -> correlation.value,
          "content_byte_size" -> text.getBytes(UTF_8).length, "content_digest" -> digest(text), "content_charset" -> "UTF-8",
          "content_storage" -> "overflow", "content_ref" -> s"${identity.value}:content", revisionField -> version)
        val seeded = if (sample.logical) Record(root.fields :+ field("aliveness", "alive")) else root
        success(environment.writer.create(dataCollection, entry(identity), seeded))
        success(environment.writer.create(bodyCollection, bodyEntry(identity), body))
        collection.putScoped(Entity(identity, seeded))
      }
      collection.putScoped(Entity(freshId, record("id" -> freshId, "title" -> "Stale resident")))
      success(view.resolve(id)); success(view.resolve(freshId)); success(view.resolve(unrelatedId))
      arm()
    }
    def fault(mode: FaultMode): FaultTarget = {
      val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(freshId, input.record))
      val sides = preparation.sideEffects.collect { case save: EntityVersionedSideEffect.Save => save }
      require(sides.size == 1)
      val side = sides.head
      require(side.collection.collectionName == bodyCollection.collectionName)
      require(side.entryId.print == bodyEntry(freshId).print)
      FaultTarget(dataCollection, entry(freshId), side.collection, side.entryId, mode)
    }
  }

  def bodyRecord(identity: EntityId, text: String): Record = record("id" -> s"${identity.value}:content",
    "source_entity_id" -> identity.value, "field" -> "content", "content" -> text,
    "content_byte_size" -> text.getBytes(UTF_8).length, "content_digest" -> digest(text), "content_charset" -> "UTF-8")
  def expectedMemoryCreate(session: Session): (Record, Option[Record]) = {
    val sample = session.sample
    val identity = session.freshId
    val declared: Map[String, Any] = Map("title" -> sample.title, "business" -> sample.business, "nullable" -> Update.SetNull,
      "id" -> (if (sample.supplied) identity else identity.print), "short_id" -> identity.parts.entropy, "name" -> "owner",
      "created_at" -> instant, "updated_at" -> instant, "created_by" -> "owner", "updated_by" -> "owner",
      "post_status" -> PostStatus.Published, "aliveness" -> Aliveness.default, "owner_id" -> "owner", "group_id" -> "owner",
      "privilege_id" -> "owner", "permission" -> permission, "trace_id" -> trace.value, "correlation_id" -> correlation.value) ++
      (sample.body match {
        case BodyMode.Absent => Map.empty[String, Any]
        case BodyMode.SetNull => Map[String, Any]("content" -> Update.SetNull)
        case _ => Map[String, Any]("content" -> content(sample))
      })
    val base = declared.toVector.map { case (name, value) => field(name, value) } :+ field(session.revisionField, 1L)
    val prepared = sample.body match {
      case BodyMode.Overflow | BodyMode.Inline =>
        val text = content(sample)
        val metadata = Vector(field("content_byte_size", text.getBytes(UTF_8).length), field("content_digest", digest(text)), field("content_charset", "UTF-8"))
        if (sample.body == BodyMode.Overflow) base.filterNot(_.key == "content") ++ metadata ++
          Vector(field("content_storage", "overflow"), field("content_ref", s"${identity.value}:content"))
        else base ++ metadata :+ field("content_storage", "inline")
      case BodyMode.SetNull => base.filterNot(_.key == "content")
      case BodyMode.Absent => base
    }
    Record(prepared.filterNot(_.key == "nullable")) ->
      (if (sample.body == BodyMode.Overflow) Some(bodyRecord(identity, content(sample))) else None)
  }
  private def _physical_value(value: Any): Any = value match {
    case identity: EntityId => identity.print
    case instant: Instant => instant.toString
    case PostStatus.Published => "published"
    case Aliveness.Alive => "alive"
    case other => other
  }
  private def _cells(record: Record, columns: Vector[(String, String)]): Map[String, Any] =
    columns.map { case (name, _) => name -> record.fields.find(_.key == name).map(field => _physical_value(field.value.single)).orNull }.toMap
  private def _logical(cells: Map[String, Any], columns: Vector[(String, String)], session: Session, normalized: Boolean): Record = {
    val names = columns.map(_._1)
    val order = if (session.environment.postgresql) Vector("id") ++ names.filter(name => name == session.revisionField) ++
      names.filterNot(name => name == "id" || name == session.revisionField) else names
    Record(order.filterNot(name => java.util.Objects.isNull(cells(name))).map { name =>
      val value = if (name == "permission") permissionRecord
        else if (name == "business" && cells(name) == "{\"n\":7,\"ok\":false}") record("n" -> BigDecimal(7), "ok" -> false)
        else if (name == "business" && cells(name) == "[7,false,\"label\"]") Vector(7L, false, "label")
        else cells(name)
      field(if (normalized) _aliases.getOrElse(name, name) else name, value)
    })
  }
  def expectedCreate(session: Session, before: Rows, physical: Map[(String, String), StoredTable], normalized: Boolean): Expected = {
    val (root, body) = expectedMemoryCreate(session)
    if (!session.environment.sql) Expected(before.copy(fresh = Some(root), freshbody = body), physical, root)
    else {
      var expected = physical
      var raw: Record = Record.empty
      var rawbody = Option.empty[Record]
      Vector(session.dataCollection.collectionName -> Some(root), bodyCollection.collectionName -> body).foreach { case (name, value) =>
        value.foreach { declared =>
          val key = physical.keys.find(_._2 == name).get
          val table = physical(key)
          val cells = _cells(declared, table.columns)
          val logical = _logical(cells, table.columns, session, normalized)
          if (name == session.dataCollection.collectionName) raw = logical else rawbody = Some(logical)
          expected = expected.updated(key, table.copy(rows = (table.rows :+ cells).sortBy(_("id").toString)))
        }
      }
      Expected(before.copy(fresh = Some(raw), freshbody = rawbody), expected, raw)
    }
  }
  def expectedDelete(session: Session, before: Rows, physical: Map[(String, String), StoredTable], normalized: Boolean): Expected = {
    val updates = Vector("post_status" -> 3, "aliveness" -> 3, "updated_at" -> instant, "updated_by" -> "owner",
      "trace_id" -> trace.value, "correlation_id" -> correlation.value, "deleted_at" -> instant, "deleted_by" -> "owner")
    if (!session.environment.sql) {
      val root = if (session.sample.logical) Some(Record(before.selected.get.fields.filterNot(field =>
        updates.exists(_._1 == field.key) || field.key == session.revisionField) ++ updates.map { case (name, value) => field(name, value) } :+
        field(session.revisionField, session.sample.version + 1))) else None
      Expected(before.copy(selected = root, selectedbody = if (session.sample.logical) before.selectedbody else None), physical, root.getOrElse(Record.empty))
    } else {
      var root = Option.empty[Record]
      val expected = physical.map { case (key, table) =>
        val rows = if (key._2 == session.dataCollection.collectionName) table.rows.flatMap { row =>
          if (row("id") != session.id.print) Some(row)
          else if (!session.sample.logical) None
          else {
            val changed = row ++ updates.map { case (name, value) => name -> (if (name == "post_status" || name == "aliveness") "3" else _physical_value(value)) } +
              (session.revisionField -> (session.sample.version + 1))
            root = Some(_logical(changed, table.columns, session, normalized)); Some(changed)
          }
        } else if (key._2 == bodyCollection.collectionName && !session.sample.logical)
          table.rows.filterNot(_("id") == s"${session.id.value}:content") else table.rows
        key -> table.copy(rows = rows)
      }
      Expected(before.copy(selected = root, selectedbody = if (session.sample.logical) before.selectedbody else None), expected, root.getOrElse(Record.empty))
    }
  }

  private val _text = for { length <- Gen.chooseNum(1, 14); chars <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '日', 'Ω', 'λ', '0')) } yield "domain " + chars.mkString
  val samples: Gen[Sample] = for {
    title <- _text; business <- _text; version <- Gen.chooseNum(2L, 40L); body <- Gen.oneOf(BodyMode.values.toVector)
    representation <- Gen.oneOf(representations); supplied <- Gen.oneOf(false, true); logical <- Gen.oneOf(false, true)
    mode <- Gen.oneOf(DecodeMode.Good, DecodeMode.Structured)
  } yield Sample(title, business, version, body, representation, supplied, logical, mode)
  given Shrink[Sample] = Shrink { sample =>
    val title = if (sample.title.length > 1) LazyList(sample.copy(title = sample.title.take(sample.title.length / 2))) else LazyList.empty
    val business = if (sample.business.length > 1) LazyList(sample.copy(business = sample.business.take(sample.business.length / 2))) else LazyList.empty
    val version = if (sample.version > 2L) LazyList(sample.copy(version = 2L)) else LazyList.empty
    val body = if (sample.body != BodyMode.Absent) LazyList(sample.copy(body = BodyMode.Absent)) else LazyList.empty
    val representation = if (sample.representation == EntityRevisionRepresentation.Detached) LazyList(sample.copy(representation = EntityRevisionRepresentation.Embedded)) else LazyList.empty
    val modes = if (sample.mode != DecodeMode.Good) LazyList(sample.copy(mode = DecodeMode.Good)) else LazyList.empty
    val identity = if (sample.supplied) LazyList(sample.copy(supplied = false)) else LazyList.empty
    val lifecycle = if (sample.logical) LazyList(sample.copy(logical = false)) else LazyList.empty
    (title #::: business #::: version #::: body #::: representation #::: modes #::: identity #::: lifecycle).toStream
  }

  trait Vocabulary extends Matchers with GivenWhenThen { self: org.scalatest.Informing =>
    def checkRecord(actual: Record, expected: Record): Unit = {
      actual.fields.map(_.key) shouldBe expected.fields.map(_.key)
      actual.fields shouldBe expected.fields
    }
    def checkRows(actual: Rows, expected: Rows): Unit = {
      Vector(actual.fresh -> expected.fresh, actual.freshbody -> expected.freshbody, actual.selected -> expected.selected,
        actual.selectedbody -> expected.selectedbody, actual.unrelated -> expected.unrelated, actual.unrelatedbody -> expected.unrelatedbody).foreach { case (left, right) =>
        left.isDefined shouldBe right.isDefined
        left.zip(right).foreach { case (found, wanted) => checkRecord(found, wanted) }
      }
    }
    def checkPhysical(actual: Map[(String, String), StoredTable], expected: Map[(String, String), StoredTable]): Unit = {
      actual.keySet shouldBe expected.keySet
      actual.keys.foreach { key =>
        actual(key).columns shouldBe expected(key).columns
        actual(key).rows shouldBe expected(key).rows
      }
    }
    def checkSchema(session: Session, before: Map[(String, String), StoredTable]): Unit = if (session.environment.sql) {
      before.find(_._1._2 == session.dataCollection.collectionName).get._2.columns shouldBe session.declaredColumns(false)
      before.find(_._1._2 == bodyCollection.collectionName).get._2.columns shouldBe session.declaredColumns(true)
    }
    def checkCounters(session: Session, creates: Int, deletes: Int): Unit = {
      session.recording.creates shouldBe creates; session.recording.deletes shouldBe deletes; session.recording.writes shouldBe 0
      session.recording.lifecycle shouldBe Vector.empty
      session.uow.lastCommitResult shouldBe None; session.uow.lastAbortResult shouldBe None
    }
    def checkCreated(outcome: EntityCreateMutationOutcome[Input], session: Session, expected: Record): Unit = {
      (outcome.created eq session.space.created.get) shouldBe true
      (outcome.persisted.record eq outcome.created.record.get) shouldBe true
      (outcome.persisted.record eq session.recording.created.get.record) shouldBe true
      outcome.created.id shouldBe session.freshId; outcome.persisted.revision shouldBe EntityRevision.INITIAL
      checkRecord(outcome.persisted.record, expected)
      session.space.creates shouldBe 1; session.space.deletes shouldBe 0
      session.idCalls shouldBe (if (session.sample.supplied) 0 else 1)
      checkCounters(session, 1, 0)
    }
    def checkDeleted(outcome: EntityDeleteMutationOutcome, session: Session): Unit = {
      (outcome.deleted eq session.space.deleted.get) shouldBe true
      outcome.deleted.id shouldBe session.id; outcome.deleted.matchedRevision shouldBe revision(session.sample.version)
      session.recording.deleted.get shouldBe EntityCompareAndDeleteResult.Deleted(revision(session.sample.version))
      session.recording.deleted.get match {
        case EntityCompareAndDeleteResult.Deleted(matched) => (outcome.deleted.matchedRevision eq matched) shouldBe true
        case _ => fail("An actual acknowledged native delete result is required")
      }
      session.space.creates shouldBe 0; session.space.deletes shouldBe 1
      checkCounters(session, 0, 1)
    }
    def checkCreateResident(actual: Resident, before: Resident, raw: Record, session: Session): Unit = {
      actual.selected shouldBe before.selected; actual.memoryselected shouldBe before.memoryselected
      actual.unrelated shouldBe before.unrelated; actual.memoryunrelated shouldBe before.memoryunrelated
      val entity = if (session.sample.mode == DecodeMode.Good) Some(Entity(session.freshId,
        if (session.sample.representation == EntityRevisionRepresentation.Detached)
          Record(raw.fields.filterNot(_.key == session.revisionField)) else raw)) else None
      actual.fresh shouldBe entity; actual.memoryfresh shouldBe entity
    }
    def checkDeleteResident(actual: Resident, before: Resident): Unit = {
      actual.selected shouldBe None; actual.memoryselected shouldBe None
      actual.fresh shouldBe before.fresh; actual.memoryfresh shouldBe before.memoryfresh
      actual.unrelated shouldBe before.unrelated; actual.memoryunrelated shouldBe before.memoryunrelated
    }
  }

  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = _value
    def set(value: A): Unit = { _value = value }
    override def getAndSet(value: A): A = { val previous = _value; _value = value; previous }
    def access: (A, A => Boolean) = { val saved = _value; (saved, value => if (_value == saved) { _value = value; true } else false) }
    override def tryUpdate(f: A => A): Boolean = { update(f); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = Some(modify(f))
    def update(f: A => A): Unit = { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = { val (next, result) = f(_value); _value = next; result }
    override def modifyState[B](state: State[A, B]): B = { val (next, result) = state.run(_value).value; _value = next; result }
    override def tryModifyState[B](state: State[A, B]): Option[B] = Some(modifyState(state))
  }
}
