package org.goldenport.cncf.importer

import cats.~>
import cats.effect.Ref
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentInit, ComponentOrigin}
import org.goldenport.cncf.context.{DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityQuery, EntityStoreSpace}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityStorage, EntityRuntimePlan, PartitionStrategy}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Mar. 27, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class StartupEntityImportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val collectionId = EntityCollectionId("test", "a", "person")
  private val p1IdText = "test-a-entity-person-1742198400000-abcd1234"
  private val p2IdText = "test-a-entity-person-1742198400000-abcd1235"
  private val p1Id = _parse_entity_id(p1IdText)
  private val p2Id = _parse_entity_id(p2IdText)

  "Startup import for cncf.import.entity.file" should {
    "import entities from a URL via UnitOfWork-backed fetch" in {
      Given("a bootstrap URL source served over HTTP")
      val cwd = Files.createTempDirectory("cncf-startup-entity-url")
      val server = _http_server(
        "/entity.yaml",
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1234
          |        name: url-entity
          |        age: 20
          |""".stripMargin
      )
      server.start()

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/entity.yaml"
        val subsystem = runtime.initializeForEmbedding(
          cwd = cwd,
          args = Array(
            s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
            s"--cncf.runtime.http.driver=real",
            s"--cncf.import.entity.file=${url}"
          ),
          extraComponents = _extra_components
        ) match {
          case Consequence.Success(subsystem) => subsystem
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("the URL content is imported")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        val entity = _person(p1Id, "url-entity", 20)
        space.load(UnitOfWorkOp.EntityStoreLoad(entity.id, _persistent)) shouldBe
          Consequence.success(Some(entity))
      } finally {
        runtime.closeEmbedding()
        server.stop(0)
      }
    }

    "import a default entity directory at bootstrap when no explicit config is set" in {
      Given("a bootstrap cwd with a default entity.d directory")
      val cwd = Files.createTempDirectory("cncf-startup-entity-default")
      val dir = Files.createDirectories(cwd.resolve("entity.d"))
      val file = dir.resolve("a.yaml")
      Files.writeString(
        file,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1234
          |        name: default-entity
          |        age: 20
          |""".stripMargin
      )

      When("bootstrapping the runtime without explicit import config")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, None)
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("the default entity directory is imported")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        val entity = _person(p1Id, "default-entity", 20)
        space.load(UnitOfWorkOp.EntityStoreLoad(entity.id, _persistent)) shouldBe
          Consequence.success(Some(entity))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "import a single yaml file at bootstrap" in {
      Given("a bootstrap config that points to one entity yaml file")
      val cwd = Files.createTempDirectory("cncf-startup-entity-single")
      val file = cwd.resolve("entity.yaml")
      Files.writeString(
        file,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1234
          |        name: taro
          |        age: 20
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, Some(file.toString))
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("the entity is available through the entity-store route")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        val entity = _person(p1Id, "taro", 20)
        space.load(UnitOfWorkOp.EntityStoreLoad(entity.id, _persistent)) shouldBe
          Consequence.success(Some(entity))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "import yaml files from a directory recursively in deterministic order" in {
      Given("a bootstrap config that points to a directory with nested entity yaml files")
      val cwd = Files.createTempDirectory("cncf-startup-entity-dir")
      val nested = Files.createDirectories(cwd.resolve("a"))
      val later = cwd.resolve("b.yaml")
      val earlier = nested.resolve("a.yaml")
      Files.writeString(
        earlier,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1234
          |        name: nested-first
          |        age: 10
          |""".stripMargin
      )
      Files.writeString(
        later,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1235
          |        name: root-second
          |        age: 11
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, Some(cwd.toString))
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("all entities are available through the entity-store route")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        val first = _person(p1Id, "nested-first", 10)
        val second = _person(p2Id, "root-second", 11)
        space.load(UnitOfWorkOp.EntityStoreLoad(first.id, _persistent)) shouldBe
          Consequence.success(Some(first))
        space.load(UnitOfWorkOp.EntityStoreLoad(second.id, _persistent)) shouldBe
          Consequence.success(Some(second))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a selected yaml file has an unknown collection" in {
      Given("a bootstrap config that points to an entity yaml file with an unknown collection")
      val cwd = Files.createTempDirectory("cncf-startup-entity-unknown")
      val file = cwd.resolve("entity.yaml")
      Files.writeString(
        file,
        """entitystore:
          |  - collection: unknown.a.person
          |    records:
          |      - id: p1
          |        name: taro
          |        age: 20
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, file.toString)
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup entity import collection is not registered")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a selected yaml file has no entitystore section" in {
      Given("a bootstrap config that points to a yaml file without entitystore")
      val cwd = Files.createTempDirectory("cncf-startup-entity-empty")
      val file = cwd.resolve("empty.yaml")
      Files.writeString(
        file,
        """datastore:
          |  - collection: startup_people
          |    records:
          |      - id: p1
          |        name: taro
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, file.toString)
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import file has no entitystore entries")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when a selected yaml file has an empty entitystore section" in {
      Given("a bootstrap config that points to a yaml file with an empty entitystore section")
      val cwd = Files.createTempDirectory("cncf-startup-entity-empty-list")
      val file = cwd.resolve("empty-list.yaml")
      Files.writeString(
        file,
        """entitystore: []
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = _args(cwd, file.toString)
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import file has no entitystore entries")
        }
      } finally {
        runtime.closeEmbedding()
      }
    }

    "import both explicit entity config and the default entity directory" in {
      Given("a bootstrap cwd with both explicit entity config and a default entity.d directory")
      val cwd = Files.createTempDirectory("cncf-startup-entity-override")
      val dir = Files.createDirectories(cwd.resolve("entity.d"))
      val defaultFile = dir.resolve("default.yaml")
      val explicitFile = cwd.resolve("explicit.yaml")
      Files.writeString(
        defaultFile,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1235
          |        name: default-entity
          |        age: 20
          |""".stripMargin
      )
      Files.writeString(
        explicitFile,
        """entitystore:
          |  - collection: test.a.person
          |    records:
          |      - id: test-a-entity-person-1742198400000-abcd1234
          |        name: explicit-entity
          |        age: 21
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, Some(explicitFile.toString))
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("both sources are imported in deterministic order")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        val explicit = _person(p1Id, "explicit-entity", 21)
        val default = _person(p2Id, "default-entity", 20)
        space.load(UnitOfWorkOp.EntityStoreLoad(explicit.id, _persistent)) shouldBe
          Consequence.success(Some(explicit))
        space.load(UnitOfWorkOp.EntityStoreLoad(default.id, _persistent)) shouldBe
          Consequence.success(Some(default))
      } finally {
        runtime.closeEmbedding()
      }
    }

    "do nothing when no explicit config and no default entity directory exist" in {
      Given("a bootstrap cwd without explicit config or entity.d")
      val cwd = Files.createTempDirectory("cncf-startup-entity-none")

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, None)
        given ExecutionContext = _execution_context(
          subsystem.globalRuntimeContext.config.dataStoreSpace,
          subsystem.globalRuntimeContext.config.entityStoreSpace
        )

        Then("startup succeeds without importing any entities")
        val space = subsystem.globalRuntimeContext.config.entityStoreSpace
        space.load(UnitOfWorkOp.EntityStoreLoad(p1Id, _persistent)) shouldBe Consequence.success(None)
      } finally {
        runtime.closeEmbedding()
      }
    }

    "fail when an entity URL fetch fails" in {
      Given("a bootstrap URL source whose fetch returns an error")
      val cwd = Files.createTempDirectory("cncf-startup-entity-url-fail")
      val server = _http_server("/entity.yaml", "not-found", 404)
      server.start()

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/entity.yaml"
        val result = runtime.initializeForEmbedding(
          cwd = cwd,
          args = Array(
            s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
            s"--cncf.runtime.http.driver=real",
            s"--cncf.import.entity.file=${url}"
          ),
          extraComponents = _extra_components
        )

        Then("startup import fails explicitly")
        result match {
          case Consequence.Success(_) =>
            fail("expected startup import to fail")
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("startup import URL fetch failed")
        }
      } finally {
        runtime.closeEmbedding()
        server.stop(0)
      }
    }
  }

  private def _initialize(
    runtime: CncfRuntime,
    cwd: Path,
    importPath: Option[String]
  ) = {
    val args = importPath match {
      case Some(path) => _args(cwd, path)
      case None => _args(cwd)
    }
    val result = runtime.initializeForEmbedding(
      cwd = cwd,
      args = args,
      extraComponents = _extra_components
    )
    result match {
      case Consequence.Success(subsystem) =>
        subsystem
      case Consequence.Failure(conclusion) =>
        fail(conclusion.show)
    }
  }

  private def _args(
    cwd: Path,
    importPath: String = ""
  ): Array[String] =
    if (importPath.isEmpty)
      Array(
        s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}"
      )
    else
      Array(
        s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
        s"--cncf.import.entity.file=${importPath}"
      )

  private def _http_server(
    path: String,
    body: String,
    status: Int = 200
  ): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext(path, new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        val os = exchange.getResponseBody
        try os.write(bytes)
        finally os.close()
      }
    })
    server
  }

  private def _extra_components(
    subsystem: org.goldenport.cncf.subsystem.Subsystem
  ): Seq[Component] = {
    val component = new Component() {}
    val name = "startup_entity_registry"
    val core = Component.Core.create(
      name,
      ComponentId(name),
      ComponentInstanceId.default(ComponentId(name)),
      Protocol.Builder().build()
    )
    component.initialize(
      ComponentInit(
        subsystem = subsystem,
        core = core,
        origin = ComponentOrigin.Embed
      )
    )
    component.entitySpace.registerEntity("person", _collection())
    Seq(component)
  }

  private def _collection(): EntityCollection[_PersonEntity] = {
    given EntityPersistent[_PersonEntity] = _persistent
    val storerealm = new EntityRealm[_PersonEntity](
      entityName = "person",
      loader = EntityLoader[_PersonEntity](_ => None),
      state = new _IdRef[EntityRealmState[_PersonEntity]](EntityRealmState(Map.empty))
    )
    val descriptor = EntityDescriptor(
      collectionId = collectionId,
      plan = EntityRuntimePlan(
        entityName = "person",
        memoryPolicy = EntityMemoryPolicy.StoreOnly,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = _persistent
    )
    new EntityCollection[_PersonEntity](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, None)
    )
  }

  private def _person(
    id: EntityId,
    name: String,
    age: Int
  ): _PersonEntity =
    _PersonEntity(id, name, age)

  private def _persistent: EntityPersistent[_PersonEntity] =
    new EntityPersistent[_PersonEntity] {
      def id(e: _PersonEntity): EntityId = e.id
      def toRecord(e: _PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_PersonEntity] = {
        val m = r.asMap
        (m.get("id"), m.get("name"), m.get("age")) match {
          case (Some(id), Some(name), Some(age)) =>
            val entityId = EntityId.parse(id.toString)
            val ageValue = age match {
              case n: Number => Consequence.success(n.intValue())
              case x =>
                scala.util.Try(x.toString.toInt).toOption match {
                  case Some(n) => Consequence.success(n)
                  case None => Consequence.failure("invalid person record")
                }
            }
            (entityId, ageValue) match {
              case (Consequence.Success(parsed), Consequence.Success(parsedAge)) =>
                Consequence.success(_PersonEntity(parsed, name.toString, parsedAge))
              case (Consequence.Failure(conclusion), _) =>
                Consequence.failure(conclusion.show)
              case (_, Consequence.Failure(conclusion)) =>
                Consequence.failure(conclusion.show)
            }
          case _ =>
            Consequence.failure("invalid person record")
        }
      }
    }

  private def _parse_entity_id(text: String): EntityId =
    EntityId.parse(text) match {
      case Consequence.Success(id) =>
        id
      case Consequence.Failure(conclusion) =>
        fail(conclusion.show)
    }

  private def _execution_context(
    dataStoreSpace: org.goldenport.cncf.datastore.DataStoreSpace,
    entityStoreSpace: org.goldenport.cncf.entity.EntityStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "startup_entity_import"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "startup_entity_import_runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(FakeHttpDriver.okText("nop")),
        datastore = Some(DataStoreContext(dataStoreSpace)),
        entitystore = Some(EntityStoreContext(entityStoreSpace))
      ),
      unitOfWorkSupplier = () => new org.goldenport.cncf.unitofwork.UnitOfWork(context),
      unitOfWorkInterpreterFn = new (org.goldenport.cncf.unitofwork.UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: org.goldenport.cncf.unitofwork.UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("startup entity import test does not use unit of work")
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "startup_entity_import_test"
    )
    context
  }

  private final case class _PersonEntity(
    id: EntityId,
    name: String,
    age: Int
  ) extends org.goldenport.cncf.entity.EntityPersistable {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name,
        "age" -> age
      )
  }

  private final class _IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = _value
    def set(a: A): Unit = _value = a
    override def getAndSet(a: A): A = { val p = _value; _value = a; p }
    def access: (A, A => Boolean) = {
      val snapshot = _value
      (snapshot, (next: A) => { _value = next; true })
    }
    override def tryUpdate(f: A => A): Boolean = { _value = f(_value); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = {
      val (next, out) = f(_value)
      _value = next
      Some(out)
    }
    def update(f: A => A): Unit = _value = f(_value)
    def modify[B](f: A => (A, B)): B = {
      val (next, out) = f(_value)
      _value = next
      out
    }
    override def modifyState[B](state: cats.data.State[A, B]): B = {
      val (next, out) = state.run(_value).value
      _value = next
      out
    }
    override def tryModifyState[B](state: cats.data.State[A, B]): Option[B] = Some(modifyState(state))
    }
  }
