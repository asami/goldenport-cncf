package org.goldenport.cncf.entity

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.{Condition, Query, Update}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreQueryRouteSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "1", "person")

  "EntityStoreSpace.search" should {
    "apply Query where/sort/offset/limit on entity-store route" in {
      Given("a searchable datastore + standard entity store route")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] = {
          val m = r.asMap
          (m.get("id"), m.get("name"), m.get("age")) match {
            case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
              Consequence.success(PersonEntity(id, name, age))
            case _ =>
              Consequence.failure("invalid person record")
          }
        }
      }

      val p1 = PersonEntity(EntityId("test", "1", _cid), "jiro", 20)
      val p2 = PersonEntity(EntityId("test", "2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "3", _cid), "taro", 40)

      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p3.toRecord())
          )
        )
      )

      When("searching through EntityStoreSpace route")
      val condition = PersonQuery(
        id = Condition.any[EntityId],
        name = Condition.any[String],
        age = Condition.any[Int]
      )
      val planned = Query.plan(
        condition = condition,
        where = Query.Gte("age", 20),
        sort = Vector(Query.SortKey("age", Query.SortDirection.Desc)),
        offset = Some(1),
        limit = Some(1)
      )
      val op = UnitOfWorkOp.EntityStoreSearch(
        query = EntityQuery(_cid, planned),
        tc = summon[EntityPersistent[PersonEntity]]
      )
      val result = entitystorespace.search(op)

      Then("paged result is returned from store route")
      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(Some(3))
      result.map(r => (r.offset, r.limit, r.fetchedCount)) shouldBe Consequence.success((Some(1), Some(1), 1))
    }

    "apply patch update by id on entity-store route" in {
      Given("a seeded entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent
      given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

      val id = EntityId("test", "11", _cid)
      val entity = PersonEntity(id, "taro", 20)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), entity.toRecord())
          )
        )
      )

      When("updating by id with Update patch shape")
      val op = UnitOfWorkOp.EntityStoreUpdateById(
        id = id,
        patch = PersonPatch(
          name = Update.set("hanako"),
          age = Update.noop[Int]
        ),
        tc = summon[EntityPersistentUpdate[PersonPatch]]
      )
      val updated = entitystorespace.updateById(op)

      Then("only set fields are reflected")
      updated shouldBe Consequence.unit
      val loaded = entitystorespace.load(
        UnitOfWorkOp.EntityStoreLoad(id, summon[EntityPersistent[PersonEntity]])
      )
      loaded.map(_.map(_.name)) shouldBe Consequence.success(Some("hanako"))
      loaded.map(_.map(_.age)) shouldBe Consequence.success(Some(20))
    }
  }

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_store_query_route"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "entity_store_query_route"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-store-query-route-runtime",
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
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec")
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
      token = "entity-store-query-route-runtime-context"
    )
    context
  }
}

private final case class PersonEntity(
  id: EntityId,
  name: String,
  age: Int
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name,
      "age" -> age
    )
}

private final case class PersonPatch(
  name: Update[String],
  age: Update[Int]
) extends EntityPersistableUpdate {
  def toRecord(): Record =
    Record.dataAuto(
      "name" -> name,
      "age" -> age
    )
}

private def _person_persistent: EntityPersistent[PersonEntity] =
  new EntityPersistent[PersonEntity] {
    def id(e: PersonEntity): EntityId = e.id
    def toRecord(e: PersonEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[PersonEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("name"), m.get("age")) match {
        case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
          Consequence.success(PersonEntity(id, name, age))
        case _ =>
          Consequence.failure("invalid person record")
      }
    }
  }

private def _person_patch_persistent: EntityPersistentUpdate[PersonPatch] =
  EntityPersistentUpdate.derived(PersonPatch.createC, EntityCollectionId("test", "1", "person"))

private object PersonPatch {
  def createC(record: Record): Consequence[PersonPatch] = {
    val name = record.getAsC[String]("name").map {
      case Some(s) => Update.set(s)
      case None => Update.noop[String]
    }
    val age = record.getAsC[Int]("age").map {
      case Some(s) => Update.set(s)
      case None => Update.noop[Int]
    }
    for {
      n <- name
      a <- age
    } yield PersonPatch(n, a)
  }
}

private final case class PersonQuery(
  id: Condition[EntityId],
  name: Condition[String],
  age: Condition[Int]
) extends Query.ConditionShape
