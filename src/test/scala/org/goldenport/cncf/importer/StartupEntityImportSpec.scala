package org.goldenport.cncf.importer

import cats.~>
import cats.effect.Ref
import java.nio.file.{Files, Path}
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
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class StartupEntityImportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val collectionId = EntityCollectionId("test", "1", "person")
  private val p1IdText = "test-1-entity-person-1742198400000-abcd1234"
  private val p2IdText = "test-1-entity-person-1742198400000-abcd1235"
  private val p1Id = _parse_entity_id(p1IdText)
  private val p2Id = _parse_entity_id(p2IdText)

  "Startup import for cncf.import.entity.file" should {
    "import a single yaml file at bootstrap" in {
      Given("a bootstrap config that points to one entity yaml file")
      val cwd = Files.createTempDirectory("cncf-startup-entity-single")
      val file = cwd.resolve("entity.yaml")
      Files.writeString(
        file,
        """entitystore:
          |  - collection: test.1.person
          |    records:
          |      - id: test-1-entity-person-1742198400000-abcd1234
          |        name: taro
          |        age: 20
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, file.toString)
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
          |  - collection: test.1.person
          |    records:
          |      - id: test-1-entity-person-1742198400000-abcd1234
          |        name: nested-first
          |        age: 10
          |""".stripMargin
      )
      Files.writeString(
        later,
        """entitystore:
          |  - collection: test.1.person
          |    records:
          |      - id: test-1-entity-person-1742198400000-abcd1235
          |        name: root-second
          |        age: 11
          |""".stripMargin
      )

      When("bootstrapping the runtime")
      val runtime = new CncfRuntime()
      try {
        val subsystem = _initialize(runtime, cwd, cwd.toString)
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
          |  - collection: unknown.1.person
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
  }

  private def _initialize(
    runtime: CncfRuntime,
    cwd: Path,
    importPath: String
  ) = {
    val result = runtime.initializeForEmbedding(
      cwd = cwd,
      args = _args(cwd, importPath),
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
    importPath: String
  ): Array[String] =
    Array(
      s"--cncf.datastore.sqlite.path=${cwd.resolve("startup-import.sqlite").toString}",
      s"--cncf.import.entity.file=${importPath}"
    )

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
