package org.goldenport.cncf.entity

import java.nio.file.Files
import java.time.Instant
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{
  Capability,
  CorrelationId,
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  IdGenerationContext,
  ObservabilityContext,
  Principal,
  PrincipalId,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  SecurityContext,
  SecurityLevel,
  TraceId
}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.Query
import org.simplemodeling.model.directive.{Condition, Update}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.testutil.EntityRevisionFixture
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 *  version Apr. 26, 2026
 *  version May.  5, 2026
 *  version Jul. 29, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreQueryRouteSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E3, rules:R1,R4, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:entity-store-routing, rules:R1,R4, phase:52")

  private val _cid = EntityCollectionId("test", "a", "person")

  "EntityPersistent store record contract" must _in_phase52_spec {
    "delegate default store APIs to RecordCodex compatibility methods" in {
      Given("an old-style EntityPersistent implementation with only toRecord/fromRecord")
      val id         = EntityId("test", "old_style", _cid)
      val entity     = PersonEntity(id, "taro", 20)
      val persistent = _person_persistent

      When("calling the formal store APIs")
      val storerecord = persistent.toStoreRecord(entity)
      val decoded     = persistent.fromStoreRecord(storerecord)

      Then("the compatibility bridge preserves existing behavior")
      storerecord shouldBe entity.toRecord()
      decoded shouldBe Consequence.success(entity)
    }

    "produce view records through the explicit view boundary API" in {
      Given("an entity whose DB record differs from its view record")
      val id         = EntityId("test", "view_1", EntityCollectionId("test", "a", "store_decode"))
      val entity     = StoreDecodeEntity(id, "view-name")
      val persistent = _store_decode_persistent

      When("requesting a view record")
      val viewrecord  = persistent.toViewRecord(entity, "admin", Vector("presentationName"))
      val storerecord = persistent.toStoreRecord(entity)

      Then("the view boundary does not expose the DB field shape")
      viewrecord.getString("presentationName") shouldBe Some("view-name")
      viewrecord.getString("store_name") shouldBe None
      storerecord.getString("store_name") shouldBe Some("view-name")
    }

    "which records EID-01 datastore routing" which {
      "E3 route canonical parsed ownership without reconstruction" must _in_eid01_spec {
        "route the exact parsed owner to datastore addresses" in {
          Given(
            "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R4; Example: E3; a canonical EntityId with an exact collection"
          )
          val exactcollection =
            EntityCollectionId("textus", "artscene", "facility")
          val original = EntityId("single", "global", exactcollection)
          val space    = new EntityStoreSpace()

          When("the canonical value is parsed and converted to datastore addresses")
          val parsed     = EntityId.parse(original.value).toOption.get
          val collection = space.dataStoreCollection(parsed)
          val entry      = space.dataStoreEntryId(parsed)

          Then("the route follows the exact parsed collection")
          parsed shouldBe original
          collection shouldBe Consequence.success(
            DataStore.CollectionId.EntityStore(exactcollection)
          )
          entry shouldBe Consequence.success(DataStore.EntryId(parsed))
        }
      }
    }
  }

  "EntityStoreSpace.search" must _in_phase52_spec {
    "query routing and exact persistence boundaries" which {
    "apply Query where/sort/offset/limit on entity-store route" in {
      Given("a searchable datastore + standard entity store route")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId     = e.id
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
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord()
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord()
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p3.toRecord()
            )
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
      result.map(r => (r.offset, r.limit, r.fetchedCount)) shouldBe Consequence.success((
        Some(1),
        Some(1),
        1
      ))
    }

    "route logical query paths to physical store fields through EntityPersistent mapping" in {
      Given("records stored under physical column names")
      val collectionid       = EntityCollectionId("test", "a", "post")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PostedEntity] = _posted_persistent

      val p1 = PostedEntity(EntityId("test", "p1", collectionid), "older", "2026-04-23T10:00:00Z")
      val p2 = PostedEntity(EntityId("test", "p2", collectionid), "newer", "2026-04-24T10:00:00Z")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              p1.toStoreRecord
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              p2.toStoreRecord
            )
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
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
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
        cid    <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(result.id)
        dsid   <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(result.id)
        ds     <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec    <- ds.load(cid, dsid)
      } yield rec

      Then("the stored DB record uses the store shape, not the presentation shape")
      loaded.map(_.flatMap(_.getString("store_name"))) shouldBe Consequence.success(
        Some("store-value")
      )
      loaded.map(_.flatMap(_.getString("presentationName"))) shouldBe Consequence.success(None)
    }

    "reject an explicit EntityId whose exact collection differs from the create collection" in {
      Given("a create codec with a declared collection and an EntityId owned elsewhere")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[StoreCreateCandidate] = _store_create_candidate_persistent
      val foreigncollection = EntityCollectionId("test", "foreign", "store_create_candidate")
      val foreignid         = EntityId("test", "mismatch", foreigncollection)

      When("the create route is asked to persist the foreign exact EntityId")
      val created = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = StoreCreateCandidate(Some(foreignid), "store-value"),
          tc = summon[EntityPersistentCreate[StoreCreateCandidate]]
        )
      )

      Then("the mismatch is rejected before a provider or datastore mutation")
      created shouldBe a[Consequence.Failure[?]]
      datastorespace
        .dataStore(DataStore.CollectionId.EntityStore(foreigncollection))
        .flatMap(_.load(DataStore.CollectionId.EntityStore(foreigncollection), DataStore.EntryId(foreignid))) shouldBe
        Consequence.success(None)
    }

    "isolate same-local EntityIds in different exact datastore collections" in {
      Given("two explicit EntityIds with the same local fields and different complete collections")
      val firstcollection  = EntityCollectionId("test", "first", "facility")
      val secondcollection = EntityCollectionId("test", "second", "facility")
      val firstid          = EntityId("test", "same_local", firstcollection)
      val secondid         = EntityId("test", "same_local", secondcollection)
      val datastorespace   = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[StoreCreateCandidate] =
        new EntityPersistentCreate[StoreCreateCandidate] {
          def id(entity: StoreCreateCandidate): Option[EntityId] = entity.id
          def toRecord(entity: StoreCreateCandidate): Record =
            Record.dataAuto("id" -> entity.id.map(_.value), "store_name" -> entity.name)
          def collection(entity: StoreCreateCandidate): EntityCollectionId =
            entity.id.map(_.collection).getOrElse(
              fail("This isolation fixture requires an explicit EntityId")
            )
        }

      When("both IDs are created through EntityStoreSpace")
      val first = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          StoreCreateCandidate(Some(firstid), "first"),
          summon[EntityPersistentCreate[StoreCreateCandidate]]
        )
      )
      val second = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          StoreCreateCandidate(Some(secondid), "second"),
          summon[EntityPersistentCreate[StoreCreateCandidate]]
        )
      )
      val loaded = for {
        firstcid <- entitystorespace.dataStoreCollection(firstid)
        firstentry <- entitystorespace.dataStoreEntryId(firstid)
        firststore <- datastorespace.dataStore(firstcid)
        firstrecord <- firststore.load(firstcid, firstentry)
        secondcid <- entitystorespace.dataStoreCollection(secondid)
        secondentry <- entitystorespace.dataStoreEntryId(secondid)
        secondstore <- datastorespace.dataStore(secondcid)
        secondrecord <- secondstore.load(secondcid, secondentry)
      } yield (firstrecord.flatMap(_.getString("store_name")), secondrecord.flatMap(_.getString("store_name")))

      Then("both values remain independently addressable by exact collection and complete ID")
      first.map(_.id) shouldBe Consequence.success(firstid)
      second.map(_.id) shouldBe Consequence.success(secondid)
      loaded shouldBe Consequence.success((Some("first"), Some("second")))
    }

    "decode load and search results through EntityPersistent.fromStoreRecord" in {
      Given("store records whose physical field names cannot be decoded by fromRecord")
      val collectionid       = EntityCollectionId("test", "a", "store_decode")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[StoreDecodeEntity] = _store_decode_persistent

      val id = EntityId("test", "decode_1", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"         -> id,
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

    "preserve parent-owned value objects through toStoreRecord and fromStoreRecord" in {
      Given("an entity whose store record contains owned single and repeated value objects")
      val collectionid       = EntityCollectionId("test", "a", "owned_value_entity")
      val path               = Files.createTempFile("cncf-owned-value-entity", ".db").toString
      val datastorespace     = new DataStoreSpace().addDataStore(SqlDataStore.sqlite(path))
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[OwnedValueEntity] = _owned_value_persistent

      val id = EntityId("test", "owned_value_1", collectionid)
      val entity = OwnedValueEntity(
        id = id,
        name = "owned-value",
        address = OwnedAddress("Tokyo", "100-0001"),
        lines = Vector(OwnedLine("sku-1", 2), OwnedLine("sku-2", 1))
      )

      val create = new EntityPersistentCreate[OwnedValueEntity] {
        def id(value: OwnedValueEntity): Option[EntityId] = Some(value.id)
        def collection(value: OwnedValueEntity): EntityCollectionId =
          value.id.collection
        def toRecord(value: OwnedValueEntity): Record =
          summon[EntityPersistent[OwnedValueEntity]].toRecord(value)
        override def toStoreRecord(value: OwnedValueEntity): Record =
          summon[EntityPersistent[OwnedValueEntity]].toStoreRecord(value)
      }

      When("creating and loading through the entity-store route")
      val saved = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(entity, create)
      )
      val loaded = for {
        _ <- saved
        x <- entitystorespace.load(UnitOfWorkOp.EntityStoreLoad(
          id,
          summon[EntityPersistent[OwnedValueEntity]]
        ))
      } yield x

      Then("the persistent adapter explicitly owns value object storage decoding")
      saved shouldBe a[Consequence.Success[_]]
      loaded shouldBe Consequence.success(Some(entity))
    }
    }

    "visibility and search policies" which {
    "return empty result when collection has not been created yet" in {
      Given("a searchable datastore with no entries for the collection")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
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
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "g1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "g2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "g3", _cid), "jiro", 40)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
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
      val datastorespace   = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalattributes = Map("role" -> "content_manager")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "m1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "m2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "m3", _cid), "jiro", 40)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
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

    "exclude deletedAt records even for content manager default filters" in {
      Given("content manager with configured lifecycle scope")
      val datastorespace   = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalattributes = Map(
          "role"              -> "content_manager",
          "search_poststatus" -> "published,draft,archived"
        )
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "a1", _cid), "taro", 20)
      val p2 = PersonEntity(EntityId("test", "a2", _cid), "hanako", 30)
      val p3 = PersonEntity(EntityId("test", "a3", _cid), "jiro", 40)
      val p4 = PersonEntity(EntityId("test", "a4", _cid), "saburo", 50)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p1.toRecord() ++ Record.dataAuto("postStatus" -> "Published", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p2.toRecord() ++ Record.dataAuto("postStatus" -> "Draft", "aliveness" -> "Alive")
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p3.toRecord() ++ Record.dataAuto("postStatus" -> "Archived", "aliveness" -> "Dead")
            ),
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              p4.toRecord() ++ Record.dataAuto(
                "postStatus" -> "Published",
                "aliveness"  -> "Alive",
                "deletedAt"  -> Instant.now()
              )
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

      Then("only deletedAt records are excluded in normal search path")
      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id, p3.id))
      result.map(_.totalCount) shouldBe Consequence.success(None)
    }
    }

    "create, save, and update lifecycle routing" which {
    "apply patch update by id on entity-store route" in {
      Given("a seeded entity")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity]      = _person_persistent
      given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

      val id     = EntityId("test", "ka", _cid)
      val entity = PersonEntity(id, "taro", 20)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              entity.toRecord()
            )
          )
        )
      )

      When("updating by id with Update patch shape")
      val op = UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(
        id = id,
        patch = PersonPatch(
          name = Update.set("hanako"),
          age = Update.noop[Int]
        ),
        purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
        tc = summon[EntityPersistentUpdate[PersonPatch]],
        authorization = None
      )
      val updated = entitystorespace.updateByIdUnversioned(op)

      Then("only set fields are reflected")
      updated shouldBe a[Consequence.Success[_]]
      val loaded = entitystorespace.load(
        UnitOfWorkOp.EntityStoreLoad(id, summon[EntityPersistent[PersonEntity]])
      )
      loaded.map(_.map(_.name)) shouldBe Consequence.success(Some("hanako"))
      loaded.map(_.map(_.age)) shouldBe Consequence.success(Some(20))
    }

    "clear only the set-null field for an unversioned patch update" in {
      Given("a seeded entity with name and age")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[PersonEntity]      = _person_persistent
      given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

      val id     = EntityId("test", "set_null", _cid)
      val entity = PersonEntity(id, "taro", 20)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              entity.toRecord()
            )
          )
        )
      )

      When("updating the name with an explicit SetNull patch")
      val updated = entitystorespace.updateByIdUnversioned(
        UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(
          id = id,
          patch = PersonPatch(
            name = Update.setNull[String],
            age = Update.noop[Int]
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistentUpdate[PersonPatch]],
          authorization = None
        )
      )
      val loaded = for {
        _    <- updated
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("the target field is absent while the unrelated field remains")
      updated shouldBe a[Consequence.Success[_]]
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getAny("age"))) shouldBe Consequence.success(Some(20))
    }

    "clear SQL columns through an unversioned set-null patch" in {
      Given("a SQLite-backed entity with name and age")
      val path = Files.createTempFile("cncf-entity-store-set-null", ".db")
      val datastore = SqlDataStore.sqlite(path.toString)
      try {
        val datastorespace   = new DataStoreSpace().addDataStore(datastore)
        val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
        given ExecutionContext = _execution_context(datastorespace, entitystorespace)
        given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

        val id = EntityId("test", "sqlite_set_null", _cid)
        val entity = PersonEntity(id, "taro", 20)
        val created = entitystorespace.create(
          UnitOfWorkOp.EntityStoreCreate(
            entity,
            _person_create_persistent
          )
        )

        When("an unversioned patch sets name to null")
        val updated = created.flatMap(_ =>
          entitystorespace.updateByIdUnversioned(
            UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(
              id = id,
              patch = PersonPatch(
                name = Update.setNull[String],
                age = Update.noop[Int]
              ),
              purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
              tc = summon[EntityPersistentUpdate[PersonPatch]],
              authorization = None
            )
          )
        )
        val loaded = for {
          _ <- updated
          cid <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
          entryid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
          record <- datastore.load(cid, entryid)
        } yield record

        Then("the SQL null clears name while retaining age")
        updated shouldBe a[Consequence.Success[_]]
        loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(None)
        loaded.map(_.flatMap(_.getAny("age"))) shouldBe Consequence.success(Some(20))
      } finally {
        try {
          val _ = datastore.closeC()
        } finally {
          val _ = Files.deleteIfExists(path)
        }
      }
    }

    "apply patch update by id through EntityPersistentUpdate.toStoreRecord" in {
      Given("a seeded store-shaped entity and a patch with a different view shape")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentUpdate[StorePatchCandidate] = _store_patch_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "store_patch_candidate")
      val id           = EntityId("test", "sp1", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"         -> id,
                "store_name" -> "before"
              )
            )
          )
        )
      )

      When("updating by id with a store-aware patch")
      val updated = entitystorespace.updateByIdUnversioned(
        UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(
          id = id,
          patch = StorePatchCandidate(Update.set("after")),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistentUpdate[StorePatchCandidate]],
          authorization = None
        )
      )
      val loaded = for {
        _    <- updated
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("the datastore update uses the store field name, not the view field name")
      updated shouldBe a[Consequence.Success[_]]
      loaded.map(_.flatMap(_.getString("store_name"))) shouldBe Consequence.success(Some("after"))
      loaded.map(_.flatMap(_.getString("displayName"))) shouldBe Consequence.success(None)
    }

    "auto-complement create defaults from ExecutionContext on entity-store route" in {
      Given("a create request with missing id/name metadata")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
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
      val created   = entitystorespace.create(createop)
      val createdid = created.map(_.id)
      val loaded = for {
        result <- created
        cid    <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(result.id)
        dsid   <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(result.id)
        ds     <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec    <- ds.load(cid, dsid)
      } yield rec

      Then("id/name and context-derived metadata are complemented")
      createdid shouldBe a[Consequence.Success[?]]
      createdid.map(_.major) shouldBe Consequence.success("single")
      createdid.map(_.minor) shouldBe Consequence.success("global")
      createdid.map(_.parts.entropy.matches("[0-9a-f]{32}")) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("id"))) shouldBe
        createdid.map(id => Some(id.print))
      loaded.map(_.flatMap(_.getString("short_id"))) shouldBe created.map(result =>
        Some(result.id.parts.entropy)
      )
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(
        Some("test-principal")
      )
      loaded.map(_.flatMap(_.getAny("age"))) shouldBe Consequence.success(Some(18))
      loaded.map(_.flatMap(_.getString("created_by"))) shouldBe Consequence.success(
        Some("test_principal")
      )
      loaded.map(
        _.flatMap(_.getAny("created_at")).exists(_.isInstanceOf[Instant])
      ) shouldBe Consequence.success(true)
      loaded.map(
        _.flatMap(_.getAny("updated_at")).exists(_.isInstanceOf[Instant])
      ) shouldBe Consequence.success(true)
      loaded.map(
        _.flatMap(_.getString("post_status")).exists(_.toLowerCase.contains("published"))
      ) shouldBe Consequence.success(true)
      loaded.map(
        _.flatMap(_.getString("aliveness")).exists(_.toLowerCase.contains("alive"))
      ) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("trace_id")).exists(_.nonEmpty)) shouldBe Consequence.success(
        true
      )
      loaded.map(_.flatMap(_.getString("correlation_id")).exists(_.nonEmpty)) shouldBe Consequence
        .success(true)
      loaded.map(_.exists(_record_has_decodable_permission)) shouldBe Consequence.success(true)
      loaded.map(_.exists(_has_no_legacy_runtime_shape)) shouldBe Consequence.success(true)
    }

    "auto-complement save defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and save payload with missing required defaults")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[SaveCandidate] = _save_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "save_candidate")
      val id           = EntityId("test", "ma", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"        -> id.print,
                "name"      -> "jiro",
                "age"       -> 20,
                "createdBy" -> "owner-x"
              )
            )
          )
        )
      )

      When("saving without name/createdBy")
      val saved = entitystorespace.saveUnversioned(
        UnitOfWorkOp.EntityStoreSaveUnversioned(
          entity = SaveCandidate(
            id = id,
            name = None,
            age = Some(21)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[SaveCandidate]],
          authorization = None
        )
      )
      val loaded = for {
        _    <- saved
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("missing fields are complemented while existing name/createdBy are preserved")
      saved shouldBe a[Consequence.Success[_]]
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("jiro"))
      loaded.map(_.flatMap(_.getString("created_by"))) shouldBe Consequence.success(Some("owner-x"))
      loaded.map(_.flatMap(_.getString("updated_by"))) shouldBe Consequence.success(
        Some("test-principal")
      )
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(None)
      loaded.map(
        _.flatMap(_.getAny("updated_at")).exists(_.isInstanceOf[Instant])
      ) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getAny("updatedAt"))) shouldBe Consequence.success(None)
      loaded.map(
        _.flatMap(_.getString("post_status")).exists(_.toLowerCase.contains("draft"))
      ) shouldBe Consequence.success(true)
      loaded.map(
        _.flatMap(_.getString("aliveness")).exists(_.toLowerCase.contains("alive"))
      ) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("trace_id")).exists(_.nonEmpty)) shouldBe Consequence.success(
        true
      )
      loaded.map(_.flatMap(_.getString("correlation_id")).exists(_.nonEmpty)) shouldBe Consequence
        .success(true)
    }

    "reject normal save on logically deleted existing records" in {
      Given("an existing record with deletedAt")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[SaveCandidate] = _save_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "save_candidate")
      val id           = EntityId("test", "deleted_save", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"        -> id.print,
                "name"      -> "deleted",
                "age"       -> 20,
                "deletedAt" -> Instant.now()
              )
            )
          )
        )
      )

      When("saving through the normal EntityStore route")
      val saved = entitystorespace.saveUnversioned(
        UnitOfWorkOp.EntityStoreSaveUnversioned(
          entity = SaveCandidate(
            id = id,
            name = Some("resurrected"),
            age = Some(21)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[SaveCandidate]],
          authorization = None
        )
      )
      val loaded = entitystorespace.load(UnitOfWorkOp.EntityStoreLoad(
        id,
        summon[EntityPersistent[SaveCandidate]]
      ))

      Then("the save fails and the record remains hidden")
      saved shouldBe a[Consequence.Failure[_]]
      loaded shouldBe Consequence.success(None)
    }

    "clear overflow content when a full save omits content" in {
      Given("an existing record with overflow content")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[SaveCandidate] = _save_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "save_candidate")
      val id           = EntityId("test", "overflow_save", collectionid)
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id"              -> id.print,
          "name"            -> "old",
          "age"             -> 20,
          "content"         -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              stored
            )
          )
        )
      )

      When("saving a replacement entity without content")
      val saved = entitystorespace.saveUnversioned(
        UnitOfWorkOp.EntityStoreSaveUnversioned(
          entity = SaveCandidate(
            id = id,
            name = Some("saved"),
            age = Some(21)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[SaveCandidate]],
          authorization = None
        )
      )
      val hydrated = for {
        _    <- saved
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
        hydrated <- rec.map(ContentBodyStoragePolicy.hydrate(id, _)).getOrElse(
          Consequence.success(Record.empty)
        )
      } yield hydrated

      Then("the omitted content is cleared instead of preserved")
      saved shouldBe a[Consequence.Success[_]]
      hydrated.map(_.getString("name")) shouldBe Consequence.success(Some("saved"))
      hydrated.map(_.getString("content")) shouldBe Consequence.success(None)
      hydrated.map(_.getString("content_storage")) shouldBe Consequence.success(None)
    }

    "generate deterministic but unique ids from ExecutionContext in tests" in {
      Given("two independent execution contexts with the same deterministic ID configuration")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[CreateCandidate] = _create_candidate_persistent

      When("creating them through the entity store")
      val first = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = CreateCandidate(None, Some("first"), Some(1)),
          tc = summon[EntityPersistentCreate[CreateCandidate]]
        )
      )
      val second = entitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = CreateCandidate(None, Some("second"), Some(2)),
          tc = summon[EntityPersistentCreate[CreateCandidate]]
        )
      )

      val replaydatastorespace   = DataStoreSpace.default()
      val replayentitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val replaycontext          = _execution_context(replaydatastorespace, replayentitystorespace)
      val replayfirst = replayentitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = CreateCandidate(None, Some("first"), Some(1)),
          tc = summon[EntityPersistentCreate[CreateCandidate]]
        )
      )(using replaycontext)
      val replaysecond = replayentitystorespace.create(
        UnitOfWorkOp.EntityStoreCreate(
          entity = CreateCandidate(None, Some("second"), Some(2)),
          tc = summon[EntityPersistentCreate[CreateCandidate]]
        )
      )(using replaycontext)

      Then("the opaque sequence replays across contexts and stays unique within one context")
      first.map(_.id) shouldBe replayfirst.map(_.id)
      second.map(_.id) shouldBe replaysecond.map(_.id)
      first.map(_.id.parts.entropy.matches("[0-9a-f]{32}")) shouldBe Consequence.success(true)
      first.map(_.id.value) should not be second.map(_.id.value)
    }

    "auto-complement update defaults from ExecutionContext on entity-store route" in {
      Given("an existing record and update payload")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[UpdateCandidate] = _update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "update_candidate")
      val id           = EntityId("test", "na", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"         -> id.print,
                "name"       -> "hanako",
                "age"        -> 30,
                "createdBy"  -> "owner-y",
                "postStatus" -> "Published"
              )
            )
          )
        )
      )

      When("updating without name/createdBy")
      val updated = entitystorespace.updateUnversioned(
        UnitOfWorkOp.EntityStoreUpdateUnversioned(
          entity = UpdateCandidate(
            id = id,
            age = Some(31)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[UpdateCandidate]],
          authorization = None
        )
      )
      val loaded = for {
        _    <- updated
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("update metadata is complemented and existing domain fields are preserved")
      updated shouldBe a[Consequence.Success[_]]
      loaded.map(_.flatMap(_.getString("name"))) shouldBe Consequence.success(Some("hanako"))
      loaded.map(_.flatMap(_.getString("created_by"))) shouldBe Consequence.success(Some("owner-y"))
      loaded.map(_.flatMap(_.getString("createdBy"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getString("post_status"))) shouldBe Consequence.success(
        Some("Published")
      )
      loaded.map(_.flatMap(_.getString("postStatus"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getString("updated_by"))) shouldBe Consequence.success(
        Some("test-principal")
      )
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(None)
      loaded.map(
        _.flatMap(_.getAny("updated_at")).exists(_.isInstanceOf[Instant])
      ) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getAny("updatedAt"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getString("trace_id")).exists(_.nonEmpty)) shouldBe Consequence.success(
        true
      )
      loaded.map(_.flatMap(_.getString("correlation_id")).exists(_.nonEmpty)) shouldBe Consequence
        .success(true)
    }

    "reject normal update on logically deleted existing records" in {
      Given("an existing record with deletedAt")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[UpdateCandidate] = _update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "update_candidate")
      val id           = EntityId("test", "deleted_update", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"        -> id.print,
                "name"      -> "deleted",
                "age"       -> 30,
                "deletedAt" -> Instant.now()
              )
            )
          )
        )
      )

      When("updating through the normal EntityStore route")
      val updated = entitystorespace.updateUnversioned(
        UnitOfWorkOp.EntityStoreUpdateUnversioned(
          entity = UpdateCandidate(
            id = id,
            age = Some(31)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[UpdateCandidate]],
          authorization = None
        )
      )
      val loaded = entitystorespace.load(UnitOfWorkOp.EntityStoreLoad(
        id,
        summon[EntityPersistent[UpdateCandidate]]
      ))

      Then("the update fails and the record remains hidden")
      updated shouldBe a[Consequence.Failure[_]]
      loaded shouldBe Consequence.success(None)
    }

    "preserve overflow content when a partial update omits content" in {
      Given("an existing record with overflow content")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[UpdateCandidate] = _update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "update_candidate")
      val id           = EntityId("test", "overflow_update", collectionid)
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id"              -> id.print,
          "name"            -> "hanako",
          "age"             -> 30,
          "content"         -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              stored
            )
          )
        )
      )

      When("updating another field without content")
      val updated = entitystorespace.updateUnversioned(
        UnitOfWorkOp.EntityStoreUpdateUnversioned(
          entity = UpdateCandidate(
            id = id,
            age = Some(31)
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[UpdateCandidate]],
          authorization = None
        )
      )
      val hydrated = for {
        _    <- updated
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
        hydrated <- rec.map(ContentBodyStoragePolicy.hydrate(id, _)).getOrElse(
          Consequence.success(Record.empty)
        )
      } yield hydrated

      Then("the existing overflow content remains attached to the entity")
      updated shouldBe a[Consequence.Success[_]]
      hydrated.map(_.getString("content")) shouldBe Consequence.success(Some("日本語"))
      hydrated.map(_.getString("content_storage")) shouldBe Consequence.success(Some("overflow"))
      hydrated.map(_.getInt("age")) shouldBe Consequence.success(Some(31))
    }

    "preserve overflow content when an unmanaged upsert omits content" in {
      Given("an unmanaged existing record whose content is stored in overflow")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistentCreate[CreateCandidate] = _create_candidate_persistent

      val collectionid = EntityCollectionId(
        "test",
        "a",
        "create_candidate"
      )
      val id = EntityId("test", "overflow_upsert", collectionid)
      val stored = _success(ContentBodyStoragePolicy.prepareForSave(
        id,
        Record.dataAuto(
          "id"              -> id,
          "name"            -> "before",
          "age"             -> 30,
          "content"         -> "日本語",
          "content_charset" -> "UTF-8"
        ),
        ContentBodyStoragePolicy.Config(inlineByteThreshold = 5)
      ))
      val seeded = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(
              DataStore.CollectionId.EntityStore(collectionid),
              stored
            )
          )
        )
      )

      When("the unversioned upsert changes another field without content")
      val updated = seeded.flatMap(_ =>
        entitystorespace.upsert(
          UnitOfWorkOp.EntityStoreUpsertUnversioned(
            entity = CreateCandidate(
              Some(id),
              Some("updated"),
              Some(31)
            ),
            id = id,
            purpose =
              EntityUnversionedMutationPurpose.FrameworkBootstrap,
            tc = summon[EntityPersistentCreate[CreateCandidate]]
          )
        )(_ => Consequence.unit)
      )
      val hydrated = for {
        _ <- updated
        cid <- summon[ExecutionContext].entityStoreSpace
          .dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace
          .dataStoreEntryId(id)
        ds     <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        record <- ds.load(cid, dsid)
        result <- record
          .map(ContentBodyStoragePolicy.hydrate(id, _))
          .getOrElse(Consequence.success(Record.empty))
      } yield result

      Then("the partial upsert retains both overflow metadata and payload")
      updated shouldBe a[Consequence.Success[?]]
      hydrated.map(_.getString("name")) shouldBe
        Consequence.success(Some("updated"))
      hydrated.map(_.getString("content")) shouldBe
        Consequence.success(Some("日本語"))
      hydrated.map(_.getString("content_storage")) shouldBe
        Consequence.success(Some("overflow"))
    }

    "overwrite caller-supplied update audit fields on entity-store route" in {
      Given("an existing record and an update payload containing spoofed audit fields")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)
      given EntityPersistent[AuditSpoofUpdateCandidate] = _audit_spoof_update_candidate_persistent

      val collectionid = EntityCollectionId("test", "a", "audit_spoof_update_candidate")
      val id           = EntityId("test", "qa", collectionid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(collectionid),
              Record.dataAuto(
                "id"        -> id.print,
                "name"      -> "audit-target",
                "updatedBy" -> "original"
              )
            )
          )
        )
      )

      When("updating through EntityStoreSpace")
      val updated = entitystorespace.updateUnversioned(
        UnitOfWorkOp.EntityStoreUpdateUnversioned(
          entity = AuditSpoofUpdateCandidate(
            id = id,
            updatedAt = Instant.EPOCH,
            updatedBy = "attacker"
          ),
          purpose = EntityUnversionedMutationPurpose.FrameworkBootstrap,
          tc = summon[EntityPersistent[AuditSpoofUpdateCandidate]],
          authorization = None
        )
      )
      val loaded = for {
        _    <- updated
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("runtime audit fields win over caller-supplied values")
      updated shouldBe a[Consequence.Success[_]]
      loaded.map(_.flatMap(_.getString("updated_by"))) shouldBe Consequence.success(
        Some("test-principal")
      )
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getAny("updated_at"))) should not be Consequence.success(
        Some(Instant.EPOCH)
      )
      loaded.map(_.flatMap(_.getAny("updatedAt"))) shouldBe Consequence.success(None)
    }
    }

    "delete lifecycle routing" which {
    "perform soft delete on entity-store route and keep record with lifecycle updates" in {
      Given("a seeded entity")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "oa", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id"         -> id.print,
                "name"       -> "taro",
                "postStatus" -> "Published",
                "aliveness"  -> "Alive",
                "updatedBy"  -> "owner-z"
              )
            )
          )
        )
      )

      When("deleting through EntityStoreSpace")
      val deleted = entitystorespace.delete(UnitOfWorkOp.EntityStoreDelete(id))
      val loaded = for {
        _    <- deleted
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("record remains and lifecycle/audit fields are updated")
      deleted shouldBe Consequence.unit
      loaded.map(_.flatMap(_.getString("id"))) shouldBe Consequence.success(Some(id.print))
      loaded.map(_.flatMap(r => r.getAny("aliveness").orElse(r.getAny("alive"))).exists(
        _.toString.toLowerCase.contains("dead")
      )) shouldBe Consequence.success(true)
      loaded.map(_.flatMap(_.getString("updated_by"))) shouldBe Consequence.success(
        Some("test-principal")
      )
      loaded.map(_.flatMap(_.getString("updatedBy"))) shouldBe Consequence.success(None)
      loaded.map(_.flatMap(_.getString("trace_id")).exists(_.nonEmpty)) shouldBe Consequence.success(
        true
      )
      loaded.map(_.flatMap(_.getString("correlation_id")).exists(_.nonEmpty)) shouldBe Consequence
        .success(true)
    }

    "perform hard delete on entity-store route and remove record physically" in {
      Given("a seeded entity")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "ob", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id"         -> id.print,
                "name"       -> "hanako",
                "postStatus" -> "Published",
                "aliveness"  -> "Alive"
              )
            )
          )
        )
      )

      When("hard deleting through EntityStoreSpace")
      val deleted = entitystorespace.deleteHard(UnitOfWorkOp.EntityStoreDeleteHard(id))
      val loaded = for {
        _    <- deleted
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("record is removed")
      deleted shouldBe Consequence.unit
      loaded shouldBe Consequence.success(None)
    }

    "perform physical delete on delete route when aliveness is absent" in {
      Given("a seeded entity without aliveness")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(datastorespace, entitystorespace)

      val id = EntityId("test", "oc", _cid)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              Record.dataAuto(
                "id"   -> id.print,
                "name" -> "jiro"
              )
            )
          )
        )
      )

      When("deleting through standard delete route")
      val deleted = entitystorespace.delete(UnitOfWorkOp.EntityStoreDelete(id))
      val loaded = for {
        _    <- deleted
        cid  <- summon[ExecutionContext].entityStoreSpace.dataStoreCollection(id)
        dsid <- summon[ExecutionContext].entityStoreSpace.dataStoreEntryId(id)
        ds   <- summon[ExecutionContext].dataStoreSpace.dataStore(cid)
        rec  <- ds.load(cid, dsid)
      } yield rec

      Then("record is physically removed")
      deleted shouldBe Consequence.unit
      loaded shouldBe Consequence.success(None)
    }
    }
  }

  private def _execution_context(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      principalattributes: Map[String, String] = Map.empty,
      capabilities: Set[Capability] = Set.empty
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_store_query_route"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "entity_store_query_route"))
    )
    val driver                         = FakeHttpDriver.okText("nop")
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
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in this spec")
        }
      },
      commitaction = uow => {
        val _ = uow.commit()
        ()
      },
      abortaction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeaction = _ => (),
      token = "entity-store-query-route-runtime-context"
    )
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId                 = PrincipalId("test-principal")
          def attributes: Map[String, String] = principalattributes
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = capabilities,
              level = SecurityLevel("test")
            ),
            idGeneration = IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE)
          )
        )
      case _ =>
        context
    }
  }

  private def _record_has_decodable_permission(record: Record): Boolean =
    record.getString("permission").flatMap(SimpleEntityStorageShapePolicy.permissionRightsFromJson).isDefined

  private def _has_no_legacy_runtime_shape(record: Record): Boolean =
    Vector(
      "shortid",
      "shortId",
      "createdAt",
      "updatedAt",
      "createdBy",
      "updatedBy",
      "postStatus",
      "traceId",
      "correlationId",
      "rights",
      "securityAttributes",
      "security_attributes"
    ).forall(record.getAny(_).isEmpty)
}

private final case class PersonEntity(
    id: EntityId,
    name: String,
    age: Int
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id"   -> id,
      "name" -> name,
      "age"  -> age
    )
}

private final case class PersonPatch(
    name: Update[String],
    age: Update[Int]
) extends EntityPersistableUpdate {
  def toRecord(): Record =
    Record.dataAuto(
      "name" -> name,
      "age"  -> age
    )
}

private def _person_persistent: EntityPersistent[PersonEntity] =
  new EntityPersistent[PersonEntity] {
    def id(e: PersonEntity): EntityId     = e.id
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

private def _person_create_persistent: EntityPersistentCreate[PersonEntity] =
  new EntityPersistentCreate[PersonEntity] {
    def id(entity: PersonEntity): Option[EntityId] = Some(entity.id)
    def collection(entity: PersonEntity): EntityCollectionId = entity.id.collection
    def toRecord(entity: PersonEntity): Record = entity.toRecord()
  }

private def _person_patch_persistent: EntityPersistentUpdate[PersonPatch] =
  EntityPersistentUpdate.derived(PersonPatch.createC, EntityCollectionId("test", "a", "person"))

private object PersonPatch {
  def createC(record: Record): Consequence[PersonPatch] = {
    val name = record.getAsC[String]("name").map {
      case Some(s) => Update.set(s)
      case None    => Update.noop[String]
    }
    val age = record.getAsC[Int]("age").map {
      case Some(s) => Update.set(s)
      case None    => Update.noop[Int]
    }
    for {
      n <- name
      a <- age
    } yield PersonPatch(n, a)
  }
}

private final case class StorePatchCandidate(
    name: Update[String]
) extends EntityPersistableUpdate {
  def toRecord(): Record =
    Record.dataAuto(
      "displayName" -> name
    )
}

private def _store_patch_candidate_persistent: EntityPersistentUpdate[StorePatchCandidate] =
  new EntityPersistentUpdate[StorePatchCandidate] {
    private val _collectionid = EntityCollectionId("test", "a", "store_patch_candidate")

    def toRecord(e: StorePatchCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[StorePatchCandidate] =
      Consequence.notImplemented("not used in this spec")
    override def toStoreRecord(e: StorePatchCandidate): Record =
      Record.dataAuto(
        "store_name" -> e.name
      )
    override def fromStoreRecord(r: Record): Consequence[StorePatchCandidate] =
      Consequence.notImplemented("not used in this spec")
    def collection(e: StorePatchCandidate): EntityCollectionId = _collectionid
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
      "id"   -> id.map(_.print),
      "name" -> name,
      "age"  -> age
    )
}

private def _create_candidate_persistent: EntityPersistentCreate[CreateCandidate] =
  new EntityPersistentCreate[CreateCandidate] {
    private val _collectionid = EntityCollectionId("test", "a", "create_candidate")

    def id(e: CreateCandidate): Option[EntityId]           = e.id
    def toRecord(e: CreateCandidate): Record               = e.toRecord()
    def collection(e: CreateCandidate): EntityCollectionId = _collectionid
  }

private final case class StoreCreateCandidate(
    id: Option[EntityId],
    name: String
) extends EntityPersistableCreate {
  def toRecord(): Record =
    Record.dataAuto(
      "id"               -> id.map(_.print),
      "presentationName" -> name
    )
}

private def _store_create_candidate_persistent: EntityPersistentCreate[StoreCreateCandidate] =
  new EntityPersistentCreate[StoreCreateCandidate] {
    private val _collectionid = EntityCollectionId("test", "a", "store_create_candidate")

    def id(e: StoreCreateCandidate): Option[EntityId] = e.id
    def toRecord(e: StoreCreateCandidate): Record     = e.toRecord()
    override def toStoreRecord(e: StoreCreateCandidate): Record =
      Record.dataAuto(
        "id"         -> e.id.map(_.print),
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
      "id"               -> id,
      "presentationName" -> name
    )
}

private final case class StoreDecodeQuery(
    id: Condition[EntityId],
    name: Condition[String]
) extends Query.ConditionShape

private def _store_decode_persistent: EntityPersistent[StoreDecodeEntity] =
  new EntityPersistent[StoreDecodeEntity] {
    def id(e: StoreDecodeEntity): EntityId     = e.id
    def toRecord(e: StoreDecodeEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[StoreDecodeEntity] =
      Consequence.argumentInvalid("presentation record decoder must not be used for store records")
    override def toStoreRecord(e: StoreDecodeEntity): Record =
      Record.dataAuto(
        "id"         -> e.id,
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
        case other  => other
      }
  }

private final case class OwnedAddress(
    city: String,
    postalcode: String
) extends RecordPresentable {
  def toRecord(): Record =
    Record.dataAuto(
      "city"        -> city,
      "postal_code" -> postalcode
    )
}

private final case class OwnedLine(
    sku: String,
    quantity: Int
) extends RecordPresentable {
  def toRecord(): Record =
    Record.dataAuto(
      "sku"      -> sku,
      "quantity" -> quantity
    )
}

private final case class OwnedValueEntity(
    id: EntityId,
    name: String,
    address: OwnedAddress,
    lines: Vector[OwnedLine]
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id"      -> id,
      "name"    -> name,
      "address" -> address,
      "lines"   -> lines
    )
}

private def _owned_value_persistent: EntityPersistent[OwnedValueEntity] =
  new EntityPersistent[OwnedValueEntity] {
    def id(e: OwnedValueEntity): EntityId     = e.id
    def toRecord(e: OwnedValueEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[OwnedValueEntity] =
      Consequence.argumentInvalid(
        "presentation record decoder must not be used for owned value storage"
      )
    override def toStoreRecord(e: OwnedValueEntity): Record =
      Record.dataAuto(
        "id"      -> e.id,
        "name"    -> e.name,
        "address" -> e.address,
        "lines"   -> e.lines
      )
    override def fromStoreRecord(r: Record): Consequence[OwnedValueEntity] = {
      val decoded = for {
        id      <- r.getAs[EntityId]("id")
        name    <- r.getString("name")
        address <- r.getRecord("address").flatMap(_owned_address)
        lines <- r.getVector("lines").map(_.collect { case rec: Record => rec }).map(_.flatMap(
          _owned_line
        ))
      } yield OwnedValueEntity(id, name, address, lines)
      decoded match {
        case Some(entity) => Consequence.success(entity)
        case None         => Consequence.argumentInvalid("invalid owned value storage record")
      }
    }
  }

private def _owned_address(record: Record): Option[OwnedAddress] =
  for {
    city       <- record.getString("city")
    postalcode <- record.getString("postal_code")
  } yield OwnedAddress(city, postalcode)

private def _owned_line(record: Record): Option[OwnedLine] =
  for {
    sku      <- record.getString("sku")
    quantity <- _int_value(record, "quantity")
  } yield OwnedLine(sku, quantity)

private def _int_value(record: Record, key: String): Option[Int] =
  record.getAny(key).flatMap {
    case n: java.lang.Number => Some(n.intValue)
    case s: String           => scala.util.Try(s.toDouble.toInt).toOption
    case other               => scala.util.Try(other.toString.toDouble.toInt).toOption
  }

private final case class SaveCandidate(
    id: EntityId,
    name: Option[String],
    age: Option[Int]
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id"   -> id.print,
      "name" -> name,
      "age"  -> age
    )
}

private def _save_candidate_persistent: EntityPersistent[SaveCandidate] =
  new EntityPersistent[SaveCandidate] {
    def id(e: SaveCandidate): EntityId     = e.id
    def toRecord(e: SaveCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[SaveCandidate] =
      r.getAsC[EntityId]("id").flatMap {
        case Some(entityid) =>
          Consequence.success(
            SaveCandidate(
              entityid,
              r.getString("name"),
              r.getInt("age")
            )
          )
        case None =>
          Consequence.argumentInvalid("id", "EntityId", "missing")
      }
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
    def id(e: UpdateCandidate): EntityId     = e.id
    def toRecord(e: UpdateCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[UpdateCandidate] =
      r.getAsC[EntityId]("id").flatMap {
        case Some(entityid) =>
          Consequence.success(UpdateCandidate(entityid, r.getInt("age")))
        case None =>
          Consequence.argumentInvalid("id", "EntityId", "missing")
      }
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
    def id(e: AuditSpoofUpdateCandidate): EntityId     = e.id
    def toRecord(e: AuditSpoofUpdateCandidate): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[AuditSpoofUpdateCandidate] =
      (
        r.getAs[EntityId]("id"),
        r.getAny("updated_at").collect { case value: Instant => value },
        r.getString("updated_by")
      ) match {
        case (Some(entityid), Some(updatedat), Some(updatedby)) =>
          Consequence.success(
            AuditSpoofUpdateCandidate(entityid, updatedat, updatedby)
          )
        case _ =>
          Consequence.argumentInvalid(
            "auditSpoofUpdateCandidate",
            "id, updated_at and updated_by",
            r
          )
      }
  }

private final case class PostedEntity(
    id: EntityId,
    body: String,
    postedAt: String
) {
  def toStoreRecord: Record =
    Record.dataAuto(
      "id"        -> id,
      "body"      -> body,
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
        "id"       -> e.id,
        "body"     -> e.body,
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
        case other      => other
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

private def _success[A](result: Consequence[A]): A =
  result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(c)     => throw new AssertionError(c.toString)
  }
