package org.goldenport.cncf.action

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.record.Record
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Condition

/*
 * @since   Mar. 29, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallEntityAccessMetricsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private def _cid(name: String) = EntityCollectionId("test", "a", name)

  "read-side API metrics" should {
    "record entity-space hit for load when the cache already has the entity" in {
      EntityAccessMetricsRegistry.shared.synchronized {
        Given("a component entity space with a resident entity")
        EntityAccessMetricsRegistry.shared.clear()
        given EntityPersistent[TestPerson] = _persistent

        val cid = _cid("person_metrics_cache_load")
        val id = EntityId("test", "cache_load", cid)
        val entity = TestPerson(id, "taro", 20)
        val component = new org.goldenport.cncf.component.Component() {}
        component.entitySpace.registerEntity(cid.name, _resident_collection(cid, entity))
        val ctx = _execution_context(DataStoreSpace.default(), new EntityStoreSpace().addEntityStore(EntityStore.standard()))

        When("loading through ActionCallEntityStorePart")
        val result = _probe(component, ctx).load[TestPerson](id)

        Then("the result comes from entity-space and metrics reflect the cache hit")
        result shouldBe Consequence.success(Some(entity))
        _metric_count("entity.load.try.entity-space", "entity-space") shouldBe 1L
        _metric_count("entity.load.hit.entity-space", "entity-space") shouldBe 1L
        _metric_count("entity.load.hit.data-store", "data-store") shouldBe 0L
      }
    }

    "record data-store hit for load when entity-space misses and store fallback is used" in {
      EntityAccessMetricsRegistry.shared.synchronized {
        Given("a datastore-backed entity store without a resident entity collection")
        EntityAccessMetricsRegistry.shared.clear()
        given EntityPersistent[TestPerson] = _persistent

        val datastorespace = DataStoreSpace.default()
        val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
        val ctx = _execution_context(datastorespace, entitystorespace)
        given ExecutionContext = ctx
        val cid = _cid("person_metrics_store_load")
        val id = EntityId("test", "store_load", cid)
        val entity = TestPerson(id, "hanako", 30)
        val _ = datastorespace.inject(
          DataStoreSpace.Seed(
            Vector(DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(cid), entity.toRecord()))
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
    }

    "record data-store hit for search when resident cache is empty" in {
      EntityAccessMetricsRegistry.shared.synchronized {
        Given("an entity collection registered in entity space but with no resident values")
        EntityAccessMetricsRegistry.shared.clear()
        given EntityPersistent[TestPerson] = _persistent

        val datastorespace = DataStoreSpace.default()
        val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
        val ctx = _execution_context(datastorespace, entitystorespace)
        given ExecutionContext = ctx
        val cid = _cid("person_metrics_search")
        val p1 = TestPerson(EntityId("test", "search_1", cid), "alpha", 20)
        val p2 = TestPerson(EntityId("test", "search_2", cid), "beta", 30)
        val _ = datastorespace.inject(
          DataStoreSpace.Seed(
            Vector(
              DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(cid), p1.toRecord()),
              DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(cid), p2.toRecord())
            )
          )
        )
        val component = new org.goldenport.cncf.component.Component() {}
        component.entitySpace.registerEntity(cid.name, _empty_collection(cid))
        val query: EntityQuery[TestPerson] = EntityQuery(cid, Query(TestPersonQuery(
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

    "make a created entity visible to the next entity-space search" in {
      EntityAccessMetricsRegistry.shared.synchronized {
        Given("an empty resident entity collection and a create DTO distinct from the read entity")
        EntityAccessMetricsRegistry.shared.clear()
        given EntityPersistent[TestPerson] = _persistent
        given EntityPersistentCreate[TestPersonCreate] = _create_persistent

        val datastorespace = DataStoreSpace.default()
        val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
        val ctx = _execution_context(datastorespace, entitystorespace)
        val cid = _cid("person_metrics_create_search")
        val component = TestComponentFactory.create("create_search", Protocol.empty)
        component.entitySpace.registerEntity(cid.name, _empty_collection(cid))
        val probe = _component_scoped_probe(component, ctx)

        When("creating through the unit-of-work entity-store path")
        val created = probe.createPublic[TestPersonCreate](TestPersonCreate("created-from-dto", 42, cid))

        Then("the stored record is decoded into the entity-space working set")
        val createdId = created match {
          case Consequence.Success(result) => result.id
          case other => fail(s"create failed: $other")
        }
        val workingSet = component.entitySpace.entity[TestPerson](cid.name).storage.storeRealm.values
        workingSet.map(_.id) should contain(createdId)
        val createdEntity = workingSet.find(_.id == createdId).get
        createdEntity.postStatus.map(_.toLowerCase(java.util.Locale.ROOT).contains("published")) should contain(true)
        createdEntity.aliveness.map(_.toLowerCase(java.util.Locale.ROOT).contains("alive")) should contain(true)
        createdEntity.securityAttributes
          .flatMap(_.getRecord("rights"))
          .flatMap(_.getRecord("other"))
          .flatMap(_.getBoolean("read")) should contain(false)

        And("a following search can find the entity without falling back to the datastore")
        EntityAccessMetricsRegistry.shared.clear()
        val query: EntityQuery[TestPerson] = EntityQuery(cid, Query(TestPersonQuery(
          id = Condition.any[EntityId],
          name = Condition.is("created-from-dto"),
          age = Condition.any[Int]
        )))
        val result = probe.search[TestPerson](query)
        result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(createdId))
        _metric_count("entity.search.hit.entity-space", "entity-space") shouldBe 1L
        _metric_count("entity.search.fallback.entity-store", "entity-store") shouldBe 0L
      }
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

  private def _component_scoped_probe(
    component: org.goldenport.cncf.component.Component,
    ctx: ExecutionContext
  ): _EntityAccessProbe = {
    component.withScopeContext(ctx.cncfCore.scope)
    new _EntityAccessProbe(
      ActionCall.Core(
        action = _TestQueryAction(),
        executionContext = ctx.withScope(component.scopeContext),
        component = Some(component),
        correlationId = ctx.observability.correlationId
      )
    )
  }

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
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId = PrincipalId("test-principal")
          def attributes: Map[String, String] = Map("role" -> "content_manager")
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = Set(Capability("content_manager")),
              level = SecurityLevel("content_manager")
            )
          )
        )
      case _ =>
        context
    }
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
    cid: EntityCollectionId,
    entity: TestPerson
  )(using EntityPersistent[TestPerson]): EntityCollection[TestPerson] = {
    val storerealm = new EntityRealm[TestPerson](
      entityName = cid.name,
      loader = EntityLoader[TestPerson](_ => None),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    storerealm.put(entity)
    val memoryrealm = new PartitionedMemoryRealm[TestPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = cid,
      plan = EntityRuntimePlan(
        entityName = cid.name,
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
    cid: EntityCollectionId
  )(using EntityPersistent[TestPerson]): EntityCollection[TestPerson] = {
    val storerealm = new EntityRealm[TestPerson](
      entityName = cid.name,
      loader = EntityLoader[TestPerson](_ => None),
      state = new _IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[TestPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = cid,
      plan = EntityRuntimePlan(
        entityName = cid.name,
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
        val poststatus = r.getString("postStatus").orElse(r.getString("post_status"))
        val aliveness = r.getString("aliveness")
        val publishat = r.getString("publishAt").orElse(r.getString("publish_at"))
        val publicat = r.getString("publicAt").orElse(r.getString("public_at"))
        val publishedby = r.getString("publishedBy").orElse(r.getString("published_by"))
        val securityattributes = r.getRecord("securityAttributes").orElse(r.getRecord("security_attributes"))
        for {
          id <- pid
          name <- pname
          age <- page
        } yield TestPerson(id, name, age, poststatus, aliveness, publishat, publicat, publishedby, securityattributes)
      }
    }

  private def _create_persistent: EntityPersistentCreate[TestPersonCreate] =
    new EntityPersistentCreate[TestPersonCreate] {
      def id(e: TestPersonCreate): Option[EntityId] = None
      def collection(e: TestPersonCreate): EntityCollectionId = e.collectionId
      def toRecord(e: TestPersonCreate): Record = e.toRecord()
    }
}

private final case class TestPerson(
  id: EntityId,
  name: String,
  age: Int,
  postStatus: Option[String] = None,
  aliveness: Option[String] = None,
  publishAt: Option[String] = None,
  publicAt: Option[String] = None,
  publishedBy: Option[String] = None,
  securityAttributes: Option[Record] = None
) extends org.goldenport.cncf.entity.EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name,
      "age" -> age,
      "postStatus" -> postStatus,
      "aliveness" -> aliveness,
      "publishAt" -> publishAt,
      "publicAt" -> publicAt,
      "publishedBy" -> publishedBy,
      "securityAttributes" -> securityAttributes
    )
}

private final case class TestPersonCreate(
  name: String,
  age: Int,
  collectionId: EntityCollectionId
) {
  def toRecord(): Record =
    Record.dataAuto(
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
  def create[T](entity: T)(using tc: EntityPersistentCreate[T]): Consequence[org.goldenport.cncf.entity.CreateResult[T]] =
    new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).run(entity_create[T](entity))

  def createPublic[T](entity: T)(using tc: EntityPersistentCreate[T]): Consequence[org.goldenport.cncf.entity.CreateResult[T]] =
    new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).run(
      org.goldenport.ConsequenceT.liftF(
        cats.free.Free.liftF[UnitOfWorkOp, org.goldenport.cncf.entity.CreateResult[T]](
          UnitOfWorkOp.EntityStoreCreate(
            entity = entity,
            tc = tc,
            authorization = Some(
              org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization(
                resourceFamily = "domain",
                resourceType = Some("TestPerson"),
                collectionName = Some(tc.collection(entity).name),
                accessKind = "create",
                access = Some(org.goldenport.cncf.operation.CmlOperationAccess(policy = "public"))
              )
            )
          )
        )
      )
    )

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
