package org.goldenport.cncf.action

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.{EntityPersistent, EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Condition

/*
 * @since   Mar. 29, 2026
 * @version Mar. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallEntityAccessMetricsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "1", "person")

  "read-side API metrics" should {
    "record entity-space hit for load when the cache already has the entity" in {
      Given("a component entity space with a resident entity")
      EntityAccessMetricsRegistry.shared.clear()
      given EntityPersistent[TestPerson] = _persistent

      val id = EntityId("test", "cache_load", _cid)
      val entity = TestPerson(id, "taro", 20)
      val component = new org.goldenport.cncf.component.Component() {}
      component.entitySpace.registerEntity("person", _resident_collection(entity))
      val ctx = _execution_context(DataStoreSpace.default(), new EntityStoreSpace().addEntityStore(EntityStore.standard()))

      When("loading through ActionCallEntityStorePart")
      val result = _probe(component, ctx).load[TestPerson](id)

      Then("the result comes from entity-space and metrics reflect the cache hit")
      result shouldBe Consequence.success(Some(entity))
      _metric_count("entity.load.try.entity-space", "entity-space") shouldBe 1L
      _metric_count("entity.load.hit.entity-space", "entity-space") shouldBe 1L
      _metric_count("entity.load.hit.data-store", "data-store") shouldBe 0L
    }

    "record data-store hit for load when entity-space misses and store fallback is used" in {
      Given("a datastore-backed entity store without a resident entity collection")
      EntityAccessMetricsRegistry.shared.clear()
      given EntityPersistent[TestPerson] = _persistent

      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val ctx = _execution_context(datastorespace, entitystorespace)
      given ExecutionContext = ctx
      val id = EntityId("test", "store_load", _cid)
      val entity = TestPerson(id, "hanako", 30)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), entity.toRecord()))
        )
      )
      val component = new org.goldenport.cncf.component.Component() {}

      When("loading through ActionCallEntityStorePart")
      val result = _probe(component, ctx).load[TestPerson](id)

      Then("the result falls back to datastore and metrics reflect that path")
      result shouldBe Consequence.success(Some(entity))
      _metric_count("entity.load.fallback.entity-store", "entity-store") shouldBe 1L
      _metric_count("entity.load.hit.data-store", "data-store") shouldBe 1L
      _metric_count("entity.load.hit.entity-space", "entity-space") shouldBe 0L
    }

    "record data-store hit for search when resident cache is empty" in {
      Given("an entity collection registered in entity space but with no resident values")
      EntityAccessMetricsRegistry.shared.clear()
      given EntityPersistent[TestPerson] = _persistent

      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val ctx = _execution_context(datastorespace, entitystorespace)
      given ExecutionContext = ctx
      val p1 = TestPerson(EntityId("test", "search_1", _cid), "alpha", 20)
      val p2 = TestPerson(EntityId("test", "search_2", _cid), "beta", 30)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val component = new org.goldenport.cncf.component.Component() {}
      component.entitySpace.registerEntity("person", _empty_collection())
      val query: EntityQuery[TestPerson] = EntityQuery(_cid, Query(TestPersonQuery(
        id = Condition.any[EntityId],
        name = Condition.is("alpha"),
        age = Condition.any[Int]
      )))

      When("searching through ActionCallEntityStorePart")
      val result = _probe(component, ctx).search[TestPerson](query)

      Then("the result comes from datastore fallback and metrics reflect the search route")
      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(p1.id))
      _metric_count("entity.search.try.entity-space", "entity-space") shouldBe 1L
      _metric_count("entity.search.fallback.entity-store", "entity-store") shouldBe 1L
      _metric_count("entity.search.hit.data-store", "data-store") shouldBe 1L
      _metric_count("entity.search.hit.entity-space", "entity-space") shouldBe 0L
    }
  }

  private def _probe(
    component: org.goldenport.cncf.component.Component,
    ctx: ExecutionContext
  ): _EntityAccessProbe =
    new _EntityAccessProbe(
      ActionCall.Core(
        action = _TestQueryAction(),
        executionContext = ctx,
        component = Some(component),
        correlationId = ctx.observability.correlationId
      )
    )

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_access_metrics"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "entity_access_metrics"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-access-metrics-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used directly in this spec")
        }
      },
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "entity-access-metrics-runtime"
    )
    context
  }

  private def _metric_count(
    name: String,
    source: String
  ): Long =
    EntityAccessMetricsRegistry.shared.snapshot()
      .find(x => x.name == name && x.source.contains(source))
      .map(_.count)
      .getOrElse(0L)

  private def _resident_collection(
    entity: TestPerson
  )(using EntityPersistent[TestPerson]): EntityCollection[TestPerson] = {
    val storerealm = new EntityRealm[TestPerson](
      entityName = "person",
      loader = EntityLoader[TestPerson](_ => None),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    storerealm.put(entity)
    val memoryrealm = new PartitionedMemoryRealm[TestPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _cid,
      plan = EntityRuntimePlan(
        entityName = "person",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[TestPerson]]
    )
    new EntityCollection[TestPerson](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _empty_collection(
  )(using EntityPersistent[TestPerson]): EntityCollection[TestPerson] = {
    val storerealm = new EntityRealm[TestPerson](
      entityName = "person",
      loader = EntityLoader[TestPerson](_ => None),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[TestPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _cid,
      plan = EntityRuntimePlan(
        entityName = "person",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[TestPerson]]
    )
    new EntityCollection[TestPerson](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _persistent: EntityPersistent[TestPerson] =
    new EntityPersistent[TestPerson] {
      def id(e: TestPerson): EntityId = e.id
      def toRecord(e: TestPerson): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[TestPerson] = {
        val m = r.asMap
        val pid = m.get("id") match {
          case Some(id: EntityId) => Consequence.success(id)
          case Some(idText: String) => EntityId.parse(idText)
          case Some(other) => EntityId.parse(other.toString)
          case None => Consequence.failure("missing id")
        }
        val pname = m.get("name").map(_.toString).filter(_.nonEmpty) match {
          case Some(v) => Consequence.success(v)
          case None => Consequence.failure("missing name")
        }
        val page = m.get("age") match {
          case Some(v: Int) => Consequence.success(v)
          case Some(v: Long) => Consequence.success(v.toInt)
          case Some(v: String) =>
            scala.util.Try(v.toInt).toOption match {
              case Some(n) => Consequence.success(n)
              case None => Consequence.failure("invalid age")
            }
          case Some(other) =>
            scala.util.Try(other.toString.toInt).toOption match {
              case Some(n) => Consequence.success(n)
              case None => Consequence.failure("invalid age")
            }
          case None => Consequence.failure("missing age")
        }
        for {
          id <- pid
          name <- pname
          age <- page
        } yield TestPerson(id, name, age)
      }
    }
}

private final case class TestPerson(
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

private final case class TestPersonQuery(
  id: Condition[EntityId],
  name: Condition[String],
  age: Condition[Int]
) extends Query.ConditionShape

private final case class _TestQueryAction() extends QueryAction {
  val request = org.goldenport.protocol.Request.ofOperation("test")
  def createCall(core: ActionCall.Core): ActionCall =
    throw new UnsupportedOperationException("not used in this spec")
}

private final class _EntityAccessProbe(
  val core: ActionCall.Core
) extends ActionCall.Core.Holder with ActionCallEntityStorePart {
  def load[T](id: EntityId)(using tc: EntityPersistent[T]): Consequence[Option[T]] =
    new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).run(entity_load_option[T](id))

  def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T]): Consequence[SearchResult[T]] =
    new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).run(entity_search[T](query))
}

private final class _IdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value: A = initial

  def get: A = synchronized { _value }
  def set(a: A): Unit = synchronized { _value = a }

  override def getAndSet(a: A): A = synchronized {
    val prev = _value
    _value = a
    prev
  }

  def access: (A, A => Boolean) = synchronized {
    val snapshot = _value
    val setter: A => Boolean = (next: A) => synchronized {
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
    val (next, out) = f(_value)
    _value = next
    Some(out)
  }

  def update(f: A => A): Unit = synchronized {
    _value = f(_value)
  }

  def modify[B](f: A => (A, B)): B = synchronized {
    val (next, out) = f(_value)
    _value = next
    out
  }

  override def modifyState[B](state: State[A, B]): B = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    out
  }

  override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
    val (next, out) = state.run(_value).value
    _value = next
    Some(out)
  }
}
