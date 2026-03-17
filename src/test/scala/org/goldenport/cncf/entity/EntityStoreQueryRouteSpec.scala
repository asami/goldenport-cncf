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

    "auto-complement create defaults from ExecutionContext on entity-store route" in {
      Given("a create request with missing id/name metadata")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[CreateCandidate] = _create_candidate_persistent

      val createop = UnitOfWorkOp.EntityStoreCreate(
        entity = CreateCandidate(
          id = None,
          name = None,
          age = Some(18)
        ),
        tc = summon[EntityPersistentCreate[CreateCandidate]]
      )

      When("creating entity through EntityStoreSpace")
      val created = entitystorespace.create(createop)
      val loaded = for {
        result <- created
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(result.id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(result.id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("id/name and context-derived metadata are complemented")
      created.map(_.id) shouldBe Consequence.success(created.take.id)
      loaded.map(_.flatMap(_.getString("id"))) shouldBe Consequence.success(Some(created.take.id.print))
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(_.getString("createdBy"))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(_.getString("postStatus"))) shouldBe Consequence.success(Some("Draft"))
      loaded.map(_.flatMap(_.getString("aliveness"))) shouldBe Consequence.success(Some("Alive"))
      loaded.map(_.flatMap(_.getString("traceId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("correlationId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "auto-complement save defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and save payload with missing required defaults")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[SaveCandidate] = _save_candidate_persistent

      val collectionid = EntityCollectionId("test", "1", "save_candidate")
      val id = EntityId("test", "31", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "jiro",
                "age" -> 20,
                "createdBy" -> "owner-x"
              )
            )
          )
        )
      )

      When("saving without name/createdBy")
      val saved = entitystorespace.save(
        UnitOfWorkOp.EntityStoreSave(
          entity = SaveCandidate(
            id = id,
            name = None,
            age = Some(21)
          ),
          tc = summon[EntityPersistent[SaveCandidate]]
        )
      )
      val loaded = for {
        _ <- saved
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("missing fields are complemented while existing name/createdBy are preserved")
      saved shouldBe Consequence.unit
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("jiro"))
      loaded.map(_.flatMap(_.getString("createdBy"))) shouldBe Consequence.success(Some("owner-x"))
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(_.getString("postStatus"))) shouldBe Consequence.success(Some("Draft"))
      loaded.map(_.flatMap(_.getString("aliveness"))) shouldBe Consequence.success(Some("Alive"))
      loaded.map(_.flatMap(_.getString("traceId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("correlationId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "auto-complement update defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and update payload")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[UpdateCandidate] = _update_candidate_persistent

      val collectionid = EntityCollectionId("test", "1", "update_candidate")
      val id = EntityId("test", "41", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "hanako",
                "age" -> 30,
                "createdBy" -> "owner-y",
                "postStatus" -> "Published"
              )
            )
          )
        )
      )

      When("updating without name/createdBy")
      val updated = entitystorespace.update(
        UnitOfWorkOp.EntityStoreUpdate(
          entity = UpdateCandidate(
            id = id,
            age = Some(31)
          ),
          tc = summon[EntityPersistent[UpdateCandidate]]
        )
      )
      val loaded = for {
        _ <- updated
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("update metadata is complemented and existing domain fields are preserved")
      updated shouldBe Consequence.unit
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("hanako"))
      loaded.map(_.flatMap(_.getString("createdBy"))) shouldBe Consequence.success(Some("owner-y"))
      loaded.map(_.flatMap(_.getString("postStatus"))) shouldBe Consequence.success(Some("Published"))
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(_.getString("traceId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("correlationId")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
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

private final case class CreateCandidate(
  id: Option[EntityId],
  name: Option[String],
  age: Option[Int]
) extends EntityPersistableCreate {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.map(_.print),
      "name" -> name,
      "age" -> age
    )
}

private def _create_candidate_persistent: EntityPersistentCreate[CreateCandidate] =
  new EntityPersistentCreate[CreateCandidate] {
    private val _collectionid = EntityCollectionId("test", "1", "create_candidate")

    def id(e: CreateCandidate): Option[EntityId] = e.id
    def toRecord(e: CreateCandidate): Record = e.toRecord()
    def collection(e: CreateCandidate): EntityCollectionId = _collectionid
  }

private final case class SaveCandidate(
  id: EntityId,
  name: Option[String],
  age: Option[Int]
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.print,
      "name" -> name,
      "age" -> age
    )
}

private def _save_candidate_persistent: EntityPersistent[SaveCandidate] =
  new EntityPersistent[SaveCandidate] {
    def id(e: SaveCandidate): EntityId = e.id
    def toRecord(e: SaveCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[SaveCandidate] =
      Consequence.failure("not used in this spec")
  }

private final case class UpdateCandidate(
  id: EntityId,
  age: Option[Int]
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "age" -> age
    )
}

private def _update_candidate_persistent: EntityPersistent[UpdateCandidate] =
  new EntityPersistent[UpdateCandidate] {
    def id(e: UpdateCandidate): EntityId = e.id
    def toRecord(e: UpdateCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[UpdateCandidate] =
      Consequence.failure("not used in this spec")
  }
