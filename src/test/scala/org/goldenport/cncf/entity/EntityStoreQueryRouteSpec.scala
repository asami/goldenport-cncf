package org.goldenport.cncf.entity

import java.time.Instant
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.Query
import org.simplemodeling.model.directive.{Condition, Update}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 *  version Apr. 25, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreQueryRouteSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "a", "person")

  "EntityPersistent store record contract" should {
    "delegate default store APIs to RecordCodex compatibility methods" in {
      Given("an old-style EntityPersistent implementation with only toRecord/fromRecord")
      val id = EntityId("test", "old_style", _cid)
      val entity = PersonEntity(id, "taro", 20)
      val persistent = _person_persistent

      When("calling the formal store APIs")
      val storeRecord = persistent.toStoreRecord(entity)
      val decoded = persistent.fromStoreRecord(storeRecord)

      Then("the compatibility bridge preserves existing behavior")
      storeRecord shouldBe entity.toRecord()
      decoded shouldBe Consequence.success(entity)
    }
  }

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
              Consequence.argumentInvalid("invalid person record")
          }
        }
      }

      val p1 = PersonEntity(EntityId("test", "a", _cid), "jiro", 20)
      val p2 = PersonEntity(EntityId("test", "b", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "c", _cid), "taro", 40)

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
        limit = Some(1),
        includeTotal = true
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

    "route logical query paths to physical store fields through EntityPersistent mapping" in {
      Given("records stored under physical column names")
      val collectionid = EntityCollectionId("test", "a", "post")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PostedEntity] = _posted_persistent

      val p1 = PostedEntity(EntityId("test", "p1", collectionid), "older", "2026-04-23T10:00:00Z")
      val p2 = PostedEntity(EntityId("test", "p2", collectionid), "newer", "2026-04-24T10:00:00Z")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(collectionid), p1.toStoreRecord),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(collectionid), p2.toStoreRecord)
          )
        )
      )

      When("searching and sorting by the logical postedAt field")
      val planned = Query.plan(
        condition = PostedQuery(
          id = Condition.any[EntityId],
          body = Condition.any[String],
          postedAt = Condition.any[String]
        ),
        where = Query.Gte("postedAt", "2026-04-24T00:00:00Z"),
        sort = Vector(Query.SortKey("postedAt", Query.SortDirection.Desc))
      )
      val result = entitystorespace.search(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(collectionid, planned, EntitySearchScope.Store),
          tc = summon[EntityPersistent[PostedEntity]]
        )
      )

      Then("the store-backed route uses the entity-owned physical field mapping")
      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(p2.id))
      result.map(_.fetchedCount) shouldBe Consequence.success(1)
    }

    "persist create payloads through EntityPersistentCreate.toStoreRecord" in {
      Given("a create model whose presentation record differs from its store record")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[StoreCreateCandidate] = _store_create_candidate_persistent

      When("creating through the entity-store route")
      val created = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = StoreCreateCandidate(None, "store-value"),
          tc = summon[EntityPersistentCreate[StoreCreateCandidate]]
        )
      )
      val loaded = for {
        result <- created
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(result.id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(result.id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("the stored DB record uses the store shape, not the presentation shape")
      loaded.map(_.flatMap(_.getString("store_name"))) shouldBe Consequence.success(Some("store-value"))
      loaded.map(_.flatMap(_.getString("presentationName"))) shouldBe Consequence.success(None)
    }

    "decode load and search results through EntityPersistent.fromStoreRecord" in {
      Given("store records whose physical field names cannot be decoded by fromRecord")
      val collectionid = EntityCollectionId("test", "a", "store_decode")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[StoreDecodeEntity] = _store_decode_persistent

      val id = EntityId("test", "decode_1", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id" -> id,
                "store_name" -> "decoded-from-store"
              )
            )
          )
        )
      )

      When("loading and searching through the entity-store route")
      val loaded = entitystorespace.load(
        UnitOfWorkOp.EntityStoreLoad(id, summon[EntityPersistent[StoreDecodeEntity]])
      )
      val result = entitystorespace.search(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(
            collectionid,
            Query(
              StoreDecodeQuery(
                id = Condition.any[EntityId],
                name = Condition.any[String]
              )
            ),
            EntitySearchScope.Store
          ),
          tc = summon[EntityPersistent[StoreDecodeEntity]]
        )
      )

      Then("both paths use fromStoreRecord instead of the compatibility decoder")
      loaded.map(_.map(_.name)) shouldBe Consequence.success(Some("decoded-from-store"))
      result.map(_.data.map(_.name)) shouldBe Consequence.success(Vector("decoded-from-store"))
    }

    "return empty result when collection has not been created yet" in {
      Given("a searchable datastore with no entries for the collection")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent

      When("searching through EntityStoreSpace route")
      val query = Query(
        PersonQuery(
          id = Condition.any[EntityId],
          name = Condition.any[String],
          age = Condition.any[Int]
        )
      )
      val op = UnitOfWorkOp.EntityStoreSearch(
        query = EntityQuery(_cid, query),
        tc = summon[EntityPersistent[PersonEntity]]
      )
      val result = entitystorespace.search(op)

      Then("an empty search result is returned without total count by default")
      result.map(_.data) shouldBe Consequence.success(Vector.empty)
      result.map(_.totalCount) shouldBe Consequence.success(None)
      result.map(_.fetchedCount) shouldBe Consequence.success(0)
    }

    "apply default visibility for general user (published + alive)" in {
      Given("records with mixed postStatus/aliveness")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "g1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "g2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "g3", _cid), "jiro", 40)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p3.toRecord() ++ Record.dataAuto("postStatus" -> "Archived", "aliveness" -> "Dead")
            )
          )
        )
      )

      When("searching without explicit lifecycle filters")
      val query = Query(
        PersonQuery(
          id = Condition.any[EntityId],
          name = Condition.any[String],
          age = Condition.any[Int]
        )
      )
      val result = entitystorespace.search(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, query),
          tc = summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("only published + alive is visible")
      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(p1.id))
      result.map(_.totalCount) shouldBe Consequence.success(None)
    }

    "apply default visibility for content manager (published + draft)" in {
      Given("content manager principal")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalAttributes = Map("role" -> "content_manager")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "m1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "m2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "m3", _cid), "jiro", 40)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p3.toRecord() ++ Record.dataAuto("postStatus" -> "Archived", "aliveness" -> "Dead")
            )
          )
        )
      )

      When("searching without explicit lifecycle filters")
      val query = Query(
        PersonQuery(
          id = Condition.any[EntityId],
          name = Condition.any[String],
          age = Condition.any[Int]
        )
      )
      val result = entitystorespace.search(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, query),
          tc = summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("published + draft are visible by default")
      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(None)
    }

    "exclude logically deleted records even for content manager default filters" in {
      Given("content manager with configured lifecycle scope")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalAttributes = Map(
          "role" -> "content_manager",
          "search_poststatus" -> "published,draft,archived"
        )
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "a1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "a2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "a3", _cid), "jiro", 40)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              p3.toRecord() ++ Record.dataAuto("postStatus" -> "Archived", "aliveness" -> "Dead")
            )
          )
        )
      )

      When("searching with expanded manager defaults")
      val query = Query(
        PersonQuery(
          id = Condition.any[EntityId],
          name = Condition.any[String],
          age = Condition.any[Int]
        )
      )
      val result = entitystorespace.search(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, query),
          tc = summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("archived records are excluded in normal search path")
      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(None)
    }

    "apply patch update by id on entity-store route" in {
      Given("a seeded entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent
      given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

      val id = EntityId("test", "ka", _cid)
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
      created.map(_.id) shouldBe Consequence.success(created.TAKE.id)
      loaded.map(_.flatMap(_.getString("id"))) shouldBe Consequence.success(Some(created.TAKE.id.print))
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(_.getString("created_by"))) shouldBe Consequence.success(Some("test_principal"))
      loaded.map(_.flatMap(r => r.getAny("createdAt").orElse(r.getAny("created_at"))).exists(_.isInstanceOf[Instant])) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getAny("updatedAt").orElse(r.getAny("updated_at"))).exists(_.isInstanceOf[Instant])) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("post_status")).exists(_.toLowerCase.contains("published"))) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("aliveness")).exists(_.toLowerCase.contains("alive"))) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("trace_id")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("correlation_id")).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "auto-complement save defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and save payload with missing required defaults")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[SaveCandidate] = _save_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "save_candidate")
      val id = EntityId("test", "ma", collectionid)
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
      loaded.map(_.flatMap(r => r.getString("updatedBy").orElse(r.getString("updated_by")))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(r => r.getAny("updatedAt").orElse(r.getAny("updated_at"))).exists(_.isInstanceOf[Instant])) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("postStatus").orElse(r.getString("post_status"))).exists(_.toLowerCase.contains("draft"))) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("aliveness")).exists(_.toLowerCase.contains("alive"))) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("traceId").orElse(r.getString("trace_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("correlationId").orElse(r.getString("correlation_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "auto-complement update defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and update payload")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[UpdateCandidate] = _update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "update_candidate")
      val id = EntityId("test", "na", collectionid)
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
      loaded.map(_.flatMap(r => r.getString("updatedBy").orElse(r.getString("updated_by")))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(r => r.getAny("updatedAt").orElse(r.getAny("updated_at"))).exists(_.isInstanceOf[Instant])) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("traceId").orElse(r.getString("trace_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("correlationId").orElse(r.getString("correlation_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "overwrite caller-supplied update audit fields on entity-store route" in {
      Given("an existing record and an update payload containing spoofed audit fields")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[AuditSpoofUpdateCandidate] = _audit_spoof_update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "audit_spoof_update_candidate")
      val id = EntityId("test", "qa", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "audit-target",
                "updatedBy" -> "original"
              )
            )
          )
        )
      )

      When("updating through EntityStoreSpace")
      val updated = entitystorespace.update(
        UnitOfWorkOp.EntityStoreUpdate(
          entity = AuditSpoofUpdateCandidate(
            id = id,
            updatedAt = Instant.EPOCH,
            updatedBy = "attacker"
          ),
          tc = summon[EntityPersistent[AuditSpoofUpdateCandidate]]
        )
      )
      val loaded = for {
        _ <- updated
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("runtime audit fields win over caller-supplied values")
      updated shouldBe Consequence.unit
      loaded.map(_.flatMap(r => r.getString("updatedBy").orElse(r.getString("updated_by")))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(r => r.getAny("updatedAt").orElse(r.getAny("updated_at")))) should not be Consequence.success(Some(Instant.EPOCH))
    }

    "perform soft delete on entity-store route and keep record with lifecycle updates" in {
      Given("a seeded entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "oa", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "taro",
                "postStatus" -> "Published",
                "aliveness" -> "Alive",
                "updatedBy" -> "owner-z"
              )
            )
          )
        )
      )

      When("deleting through EntityStoreSpace")
      val deleted = entitystorespace.delete(UnitOfWorkOp.EntityStoreDelete(id))
      val loaded = for {
        _ <- deleted
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("record remains and lifecycle/audit fields are updated")
      deleted shouldBe Consequence.unit
      loaded.map(_.flatMap(_.getString("id"))) shouldBe Consequence.success(Some(id.print))
      loaded.map(_.flatMap(r => r.getAny("aliveness").orElse(r.getAny("alive"))).exists(_.toString.toLowerCase.contains("dead"))) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("updatedBy").orElse(r.getString("updated_by")))) shouldBe Consequence.success(Some("test-principal"))
      loaded.map(_.flatMap(r => r.getString("traceId").orElse(r.getString("trace_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(r => r.getString("correlationId").orElse(r.getString("correlation_id"))).exists(_.nonEmpty)) shouldBe Consequence.success(true)
    }

    "perform hard delete on entity-store route and remove record physically" in {
      Given("a seeded entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "ob", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "hanako",
                "postStatus" -> "Published",
                "aliveness" -> "Alive"
              )
            )
          )
        )
      )

      When("hard deleting through EntityStoreSpace")
      val deleted = entitystorespace.deleteHard(UnitOfWorkOp.EntityStoreDeleteHard(id))
      val loaded = for {
        _ <- deleted
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("record is removed")
      deleted shouldBe Consequence.unit
      loaded shouldBe Consequence.success(None)
    }

    "perform physical delete on delete route when aliveness is absent" in {
      Given("a seeded entity without aliveness")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "oc", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id" -> id.print,
                "name" -> "jiro"
              )
            )
          )
        )
      )

      When("deleting through standard delete route")
      val deleted = entitystorespace.delete(UnitOfWorkOp.EntityStoreDelete(id))
      val loaded = for {
        _ <- deleted
        cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec <- ds.load(cid, dsid)
      } yield rec

      Then("record is physically removed")
      deleted shouldBe Consequence.unit
      loaded shouldBe Consequence.success(None)
    }
  }

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    principalAttributes: Map[String, String] = Map.empty,
    capabilities: Set[Capability] = Set.empty
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
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId = PrincipalId("test-principal")
          def attributes: Map[String, String] = principalAttributes
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = capabilities,
              level = SecurityLevel("test")
            )
          )
        )
      case _ =>
        context
    }
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
          Consequence.argumentInvalid("invalid person record")
      }
    }
  }

private def _person_patch_persistent: EntityPersistentUpdate[PersonPatch] =
  EntityPersistentUpdate.derived(PersonPatch.createC, EntityCollectionId("test", "a", "person"))

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
    private val _collectionid = EntityCollectionId("test", "a", "create_candidate")

    def id(e: CreateCandidate): Option[EntityId] = e.id
    def toRecord(e: CreateCandidate): Record = e.toRecord()
    def collection(e: CreateCandidate): EntityCollectionId = _collectionid
  }

private final case class StoreCreateCandidate(
  id: Option[EntityId],
  name: String
) extends EntityPersistableCreate {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.map(_.print),
      "presentationName" -> name
    )
}

private def _store_create_candidate_persistent: EntityPersistentCreate[StoreCreateCandidate] =
  new EntityPersistentCreate[StoreCreateCandidate] {
    private val _collectionid = EntityCollectionId("test", "a", "store_create_candidate")

    def id(e: StoreCreateCandidate): Option[EntityId] = e.id
    def toRecord(e: StoreCreateCandidate): Record = e.toRecord()
    override def toStoreRecord(e: StoreCreateCandidate): Record =
      Record.dataAuto(
        "id" -> e.id.map(_.print),
        "store_name" -> e.name
      )
    def collection(e: StoreCreateCandidate): EntityCollectionId = _collectionid
  }

private final case class StoreDecodeEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "presentationName" -> name
    )
}

private final case class StoreDecodeQuery(
  id: Condition[EntityId],
  name: Condition[String]
) extends Query.ConditionShape

private def _store_decode_persistent: EntityPersistent[StoreDecodeEntity] =
  new EntityPersistent[StoreDecodeEntity] {
    def id(e: StoreDecodeEntity): EntityId = e.id
    def toRecord(e: StoreDecodeEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[StoreDecodeEntity] =
      Consequence.argumentInvalid("presentation record decoder must not be used for store records")
    override def toStoreRecord(e: StoreDecodeEntity): Record =
      Record.dataAuto(
        "id" -> e.id,
        "store_name" -> e.name
      )
    override def fromStoreRecord(r: Record): Consequence[StoreDecodeEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("store_name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(StoreDecodeEntity(id, name))
        case _ =>
          Consequence.argumentInvalid("invalid store decode record")
      }
    }
    override def storeFieldName(logicalName: String): String =
      logicalName match {
        case "name" => "store_name"
        case other => other
      }
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
      Consequence.notImplemented("not used in this spec")
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
      Consequence.notImplemented("not used in this spec")
  }

private final case class AuditSpoofUpdateCandidate(
  id: EntityId,
  updatedAt: Instant,
  updatedBy: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "updatedAt" -> updatedAt,
      "updatedBy" -> updatedBy
    )
}

private def _audit_spoof_update_candidate_persistent: EntityPersistent[AuditSpoofUpdateCandidate] =
  new EntityPersistent[AuditSpoofUpdateCandidate] {
    def id(e: AuditSpoofUpdateCandidate): EntityId = e.id
    def toRecord(e: AuditSpoofUpdateCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[AuditSpoofUpdateCandidate] =
      Consequence.notImplemented("not used in this spec")
  }

private final case class PostedEntity(
  id: EntityId,
  body: String,
  postedAt: String
) {
  def toStoreRecord: Record =
    Record.dataAuto(
      "id" -> id,
      "body" -> body,
      "posted_at" -> postedAt
    )
}

private final case class PostedQuery(
  id: Condition[EntityId],
  body: Condition[String],
  postedAt: Condition[String]
) extends Query.ConditionShape

private def _posted_persistent: EntityPersistent[PostedEntity] =
  new EntityPersistent[PostedEntity] {
    def id(e: PostedEntity): EntityId = e.id
    def toRecord(e: PostedEntity): Record =
      Record.dataAuto(
        "id" -> e.id,
        "body" -> e.body,
        "postedAt" -> e.postedAt
      )
    override def toStoreRecord(e: PostedEntity): Record =
      e.toStoreRecord
    def fromRecord(r: Record): Consequence[PostedEntity] =
      _record_to_posted(r)
    override def fromStoreRecord(r: Record): Consequence[PostedEntity] =
      _record_to_posted(r)
    override def storeFieldName(logicalName: String): String =
      logicalName match {
        case "postedAt" => "posted_at"
        case other => other
      }

    private def _record_to_posted(
      r: Record
    ): Consequence[PostedEntity] = {
      val m = r.asMap
      (m.get("id"), m.get("body"), m.get("postedAt").orElse(m.get("posted_at"))) match {
        case (Some(id: EntityId), Some(body: String), Some(postedAt: String)) =>
          Consequence.success(PostedEntity(id, body, postedAt))
        case _ =>
          Consequence.argumentInvalid("invalid posted record")
      }
    }
  }
