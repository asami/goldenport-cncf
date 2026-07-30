package org.goldenport.cncf.unitofwork

import cats.~>
import cats.data.State
import cats.effect.Ref
import com.mysql.cj.jdbc.MysqlDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.datastore.sql.{
  MySqlDialectDriver,
  SqlDataStore
}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.record.Record
import org.scalatest.{
  BeforeAndAfterAll,
  GivenWhenThen
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.simplemodeling.model.directive.Update
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

/*
 * @since   Jul. 26, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkPlainMutationProviderParitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with BeforeAndAfterAll {
  import UnitOfWorkPlainMutationProviderParitySpec.*

  private var _mysql_container: Option[LiveMysqlContainer] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (_live_mysql_enabled) {
      val container = new LiveMysqlContainer()
      container.start()
      _mysql_container = Some(container)
    }
  }

  override protected def afterAll(): Unit =
    try {
      _mysql_container.foreach(_.stop())
      _mysql_container = None
    } finally {
      super.afterAll()
    }

  "UnitOfWork plain mutation provider parity" should {
    "preserve revision stale-writer and cache coherence with in-memory storage" in {
      Given("one None-concurrency SimpleEntity on the in-memory provider")
      val store = DataStore.inMemorySearchable()

      When("ordinary direct and explicit observed mutations run through UnitOfWork")
      val evidence = _exercise_provider("memory", store)

      Then("provider state and the resident Entity projection remain coherent")
      evidence shouldBe _expected_evidence
    }

    "preserve revision stale-writer and cache coherence with SQLite storage" in {
      Given("one None-concurrency SimpleEntity on a file-backed SQLite provider")
      val path = java.nio.file.Files.createTempFile(
        "cncf-pc02-provider-parity-",
        ".db"
      )
      val store = SqlDataStore.sqlite(path.toString)

      When("ordinary direct and explicit observed mutations run through UnitOfWork")
      val evidence = _exercise_provider("sqlite", store)

      Then("provider state and the resident Entity projection remain coherent")
      evidence shouldBe _expected_evidence
    }

    "preserve revision stale-writer and cache coherence with MySQL storage" in {
      _with_live_mysql { database =>
        Given("one None-concurrency SimpleEntity on the live MySQL provider")
        val datasource = new MysqlDataSource()
        datasource.setURL(database.jdbcurl)
        datasource.setUser(database.username)
        datasource.setPassword(database.password)
        val store = new SqlDataStore(
          MySqlDialectDriver,
          datasource
        )

        When("ordinary direct and explicit observed mutations run through UnitOfWork")
        val evidence = _exercise_provider("mysql", store)

        Then("provider state and the resident Entity projection remain coherent")
        evidence shouldBe _expected_evidence
      }
    }
  }

  private def _exercise_provider(
    providername: String,
    datastore: DataStore
  ): ParityEvidence = {
    val collectionid =
      EntityCollectionId(
        "test",
        "pc02",
        s"plain_mutation_$providername"
      )
    val fixture = _fixture(collectionid, datastore)
    given ExecutionContext = fixture.context
    val id = EntityId(
      "test",
      s"plain_mutation_$providername",
      collectionid
    )
    val interpreter =
      new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
    val authorization =
      Some(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("PlainMutationEntity"),
          targetId = Some(id),
          accessKind = "update",
          accessMode = EntityAccessMode.ServiceInternal
        )
      )
    val created =
      interpreter.interpret(
        UnitOfWorkOp.EntityStoreCreate(
          PlainMutationCreate(id, "before"),
          _create_persistent
        )
      )
    val direct =
      created.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateById(
            id,
            PlainMutationPatch(Update.set("direct")),
            _patch_persistent(collectionid),
            authorization
          )
        )
      )
    val residentafterdirect =
      fixture.collection.resolve(id).toOption.map(entity =>
        entity.name -> entity.revision.value
      )
    val observed =
      direct.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateByIdObserved(
            id,
            PlainMutationPatch(Update.set("observed")),
            _revision(2L),
            _patch_persistent(collectionid),
            authorization
          )
        )
      )
    val residentafterobserved =
      fixture.collection.resolve(id).toOption.map(entity =>
        entity.name -> entity.revision.value
      )
    val stale =
      observed.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateByIdObserved(
            id,
            PlainMutationPatch(Update.set("stale")),
            _revision(2L),
            _patch_persistent(collectionid),
            authorization
          )
        )
      )
    val residentafterstale = fixture.collection.resolve(id).toOption
    val stored =
      fixture.datastorespace
        .dataStore(DataStore.CollectionId.EntityStore(collectionid))
        .flatMap(
          _.load(
            DataStore.CollectionId.EntityStore(collectionid),
            DataStore.EntryId(id)
          )
        )
        .toOption
        .flatten
    ParityEvidence(
      direct.toOption.flatMap(_.getString("name")),
      residentafterdirect,
      observed.toOption.flatMap(_.record.getString("name")),
      residentafterobserved,
      stale match {
        case _: Consequence.Failure[?] => true
        case _ => false
      },
      residentafterstale.isEmpty,
      stored.flatMap(_.getString("name")),
      stored.flatMap(_.getLong("revision"))
    )
  }

  private def _fixture(
    collectionid: EntityCollectionId,
    datastore: DataStore
  ): Fixture = {
    val datastorespace = new DataStoreSpace().useDataStore(datastore)
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val component = new Component() {}
    given EntityPersistent[PlainMutationEntity] = _persistent
    val storerealm = new EntityRealm[PlainMutationEntity](
      entityName = collectionid.name,
      loader = EntityLoader[PlainMutationEntity](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[PlainMutationEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val collection = new EntityCollection[PlainMutationEntity](
      EntityDescriptor(
        collectionid,
        EntityRuntimePlan(
          entityName = collectionid.name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16,
          concurrencyPolicy = EntityConcurrencyPolicy.None
        ),
        _persistent,
        revisionBinding = Some(
          EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
        )
      ),
      EntityStorage(storerealm, Some(memoryrealm))
    )
    component.entitySpace.registerEntity(collectionid.name, collection)
    val observability = ObservabilityContext(
      traceId = TraceId("test", "pc02_provider_parity"),
      spanId = None,
      correlationId = None
    )
    val rootscope = ScopeContext(
      ScopeKind.Runtime,
      "pc02-provider-parity-root",
      None,
      observability
    )
    val componentscope = Component.Context(
      "pc02-provider-parity-component",
      rootscope,
      component,
      ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "pc02-provider-parity-runtime",
        parent = Some(componentscope),
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(new UnitOfWork(context))
            .interpret(operation)
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "pc02-provider-parity-runtime-context"
    )
    val _ = context
    Fixture(datastorespace, collection, context)
  }

  private def _with_live_mysql[A](
    body: LiveDatabase => A
  ): A =
    _mysql_container match {
      case Some(container) =>
        body(
          LiveDatabase(
            container.getJdbcUrl,
            container.getUsername,
            container.getPassword
          )
        )
      case None =>
        cancel(
          s"Set $LIVE_MYSQL_ENV=true to run the MySQL provider-parity specification."
        )
    }

  private def _patch_persistent(
    collectionid: EntityCollectionId
  ): EntityPersistentUpdate[PlainMutationPatch] =
    new EntityPersistentUpdate[PlainMutationPatch] {
      def collection(entity: PlainMutationPatch): EntityCollectionId = {
        val _ = entity
        collectionid
      }

      def toRecord(entity: PlainMutationPatch): Record =
        Record.dataAuto("name" -> entity.name)

      def fromRecord(record: Record): Consequence[PlainMutationPatch] = {
        val _ = record
        Consequence.notImplemented(
          "PlainMutationPatch decoding is not part of this provider specification"
        )
      }
    }

  private def _revision(value: Long): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )
}

object UnitOfWorkPlainMutationProviderParitySpec {
  final val LIVE_MYSQL_ENV = "CNCF_LIVE_MYSQL_TEST"

  private val _live_mysql_enabled =
    sys.env.get(LIVE_MYSQL_ENV).contains("true")

  private final case class Fixture(
    datastorespace: DataStoreSpace,
    collection: EntityCollection[PlainMutationEntity],
    context: ExecutionContext
  )

  private final case class PlainMutationEntity(
    id: EntityId,
    name: String,
    revision: EntityRevision = EntityRevision.INITIAL
  )

  private final case class PlainMutationCreate(
    id: EntityId,
    name: String
  )

  private final case class PlainMutationPatch(
    name: Update[String]
  )

  private final case class ParityEvidence(
    directname: Option[String],
    residentafterdirect: Option[(String, Long)],
    observedname: Option[String],
    residentafterobserved: Option[(String, Long)],
    stalerejected: Boolean,
    staleevicted: Boolean,
    storedname: Option[String],
    storedrevision: Option[Long]
  )

  private val _expected_evidence =
    ParityEvidence(
      Some("direct"),
      Some("direct" -> 2L),
      Some("observed"),
      Some("observed" -> 3L),
      stalerejected = true,
      staleevicted = true,
      Some("observed"),
      Some(3L)
    )

  private final case class LiveDatabase(
    jdbcurl: String,
    username: String,
    password: String
  )

  private final class LiveMysqlContainer
      extends MySQLContainer(
        DockerImageName.parse("mysql:8.4")
      ) {
    withDatabaseName("cncf_pc02")
    withUsername("cncf")
    withPassword("cncf-pc02")
  }

  private val _create_persistent
      : EntityPersistentCreate[PlainMutationCreate] =
    new EntityPersistentCreate[PlainMutationCreate] {
      def id(entity: PlainMutationCreate): Option[EntityId] =
        Some(entity.id)

      def collection(entity: PlainMutationCreate): EntityCollectionId =
        entity.id.collection

      def toRecord(entity: PlainMutationCreate): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "name" -> entity.name
        )
    }

  private val _persistent: EntityPersistent[PlainMutationEntity] =
    new EntityPersistent[PlainMutationEntity] {
      def id(entity: PlainMutationEntity): EntityId = entity.id

      def toRecord(entity: PlainMutationEntity): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name,
          "revision" -> entity.revision.value
        )

      def fromRecord(record: Record): Consequence[PlainMutationEntity] =
        for {
          id <- record
            .getAs[EntityId]("id")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("id"))
          name <- record
            .getString("name")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("name"))
          revision <- record
            .getAny("revision")
            .map(EntityRevision.createC)
            .getOrElse(Consequence.argumentMissing("revision"))
        } yield PlainMutationEntity(id, name, revision)
    }

  private final class IdRef[A](
    initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized(_value)

    def set(value: A): Unit = synchronized {
      _value = value
    }

    override def getAndSet(value: A): A = synchronized {
      val previous = _value
      _value = value
      previous
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = (next: A) =>
        synchronized {
          if (_value == snapshot) {
            _value = next
            true
          } else {
            false
          }
        }
      (snapshot, setter)
    }

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, result) = f(_value)
      _value = next
      Some(result)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, result) = f(_value)
      _value = next
      result
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }

    override def tryModifyState[B](
      state: State[A, B]
    ): Option[B] = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      Some(result)
    }
  }
}
