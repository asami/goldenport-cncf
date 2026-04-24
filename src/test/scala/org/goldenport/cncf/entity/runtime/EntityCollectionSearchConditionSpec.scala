package org.goldenport.cncf.entity.runtime

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.directive.Query
import org.simplemodeling.model.directive.Condition
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent, EntityQuery, EntitySearchScope}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 *  version Mar. 24, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityCollectionSearchConditionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "a", "person")

  "EntityCollection.search" should {
    "search store scope when memory realm exists but is empty" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val p1 = PersonEntity(EntityId("m", "a", _cid), Name("taro"), Age(20))
      val p2 = PersonEntity(EntityId("m", "b", _cid), Name("hanako"), Age(30))

      val storerealm = new EntityRealm[PersonEntity](
        entityName = "person",
        loader = EntityLoader[PersonEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(p1)
      storerealm.put(p2)

      val memoryrealm = new PartitionedMemoryRealm[PersonEntity](
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
        persistent = summon[EntityPersistent[PersonEntity]]
      )
      val collection = new EntityCollection[PersonEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      val query = EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.Store)

      val result = collection.search(query)

      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(Some(2))
    }

    "merge memory and store-backed entities without dropping store-only rows for store scope" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val p1 = PersonEntity(EntityId("m", "a", _cid), Name("taro"), Age(20))
      val p2 = PersonEntity(EntityId("m", "b", _cid), Name("hanako"), Age(30))
      val p3 = PersonEntity(EntityId("m", "c", _cid), Name("jiro"), Age(40))

      val storerealm = new EntityRealm[PersonEntity](
        entityName = "person",
        loader = EntityLoader[PersonEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(p1)
      storerealm.put(p2)
      storerealm.put(p3)

      val memoryrealm = new PartitionedMemoryRealm[PersonEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      memoryrealm.put(p1)

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
        persistent = summon[EntityPersistent[PersonEntity]]
      )
      val collection = new EntityCollection[PersonEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      val result = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.Store))

      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id, p3.id))
      result.map(_.totalCount) shouldBe Consequence.success(Some(3))
    }

    "fall back to store-backed search when working-set policy is none" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val p1 = PersonEntity(EntityId("m", "a", _cid), Name("taro"), Age(20))
      val p2 = PersonEntity(EntityId("m", "b", _cid), Name("hanako"), Age(30))

      val storerealm = new EntityRealm[PersonEntity](
        entityName = "person",
        loader = EntityLoader[PersonEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(p1)
      storerealm.put(p2)

      val memoryrealm = new PartitionedMemoryRealm[PersonEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      memoryrealm.put(p1)

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
        persistent = summon[EntityPersistent[PersonEntity]]
      )
      val collection = new EntityCollection[PersonEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      val result = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.WorkingSet))

      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(Some(2))
    }

    "keep only recent entities resident for working-set scope" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[TimedPostEntity] = new EntityPersistent[TimedPostEntity] {
        def id(e: TimedPostEntity): EntityId = e.id
        def toRecord(e: TimedPostEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[TimedPostEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val recent = TimedPostEntity(EntityId("m", "r", _cid), java.time.Instant.now().minusSeconds(3600), "recent")
      val old = TimedPostEntity(EntityId("m", "o", _cid), java.time.Instant.now().minusSeconds(3 * 24 * 3600), "old")
      val storerealm = new EntityRealm[TimedPostEntity](
        entityName = "person",
        loader = EntityLoader[TimedPostEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[TimedPostEntity]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[TimedPostEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          workingSetPolicy = Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt")),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[TimedPostEntity]]
      )
      val collection = new EntityCollection[TimedPostEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      collection.put(recent)
      collection.put(old)
      collection.storage.workingSetStatus.markReady()

      memoryrealm.get(recent.id) shouldBe Some(recent)
      memoryrealm.get(old.id) shouldBe None

      val workingSetResult = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.WorkingSet))
      val storeResult = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.Store))

      workingSetResult.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(recent.id))
      storeResult.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(recent.id, old.id))
    }

    "compare Instant query values against generated string datetime fields in resident search" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[GeneratedTimedPostEntity] = new EntityPersistent[GeneratedTimedPostEntity] {
        def id(e: GeneratedTimedPostEntity): EntityId = e.id
        def toRecord(e: GeneratedTimedPostEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[GeneratedTimedPostEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val recent = GeneratedTimedPostEntity(
        EntityId("m", "gr", _cid),
        java.time.Instant.parse("2026-04-24T10:00:00Z").toString,
        "recent"
      )
      val old = GeneratedTimedPostEntity(
        EntityId("m", "go", _cid),
        java.time.Instant.parse("2026-04-22T10:00:00Z").toString,
        "old"
      )
      val storerealm = new EntityRealm[GeneratedTimedPostEntity](
        entityName = "person",
        loader = EntityLoader[GeneratedTimedPostEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[GeneratedTimedPostEntity]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[GeneratedTimedPostEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          workingSetPolicy = Some(WorkingSetPolicy.ResidentAll),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[GeneratedTimedPostEntity]]
      )
      val collection = new EntityCollection[GeneratedTimedPostEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      collection.put(recent)
      collection.put(old)

      val result = collection.search(
        EntityQuery(
          _cid,
          Query.plan(
            Record.empty,
            where = Query.Gte("postedAt", java.time.Instant.parse("2026-04-24T00:00:00Z"))
          ),
          EntitySearchScope.WorkingSet
        )
      )

      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(recent.id))
    }

    "compare Instant query values against store-style mapped datetime fields in resident search" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[GeneratedStoreStyleTimedPostEntity] = new EntityPersistent[GeneratedStoreStyleTimedPostEntity] {
        def id(e: GeneratedStoreStyleTimedPostEntity): EntityId = e.id
        def toRecord(e: GeneratedStoreStyleTimedPostEntity): Record = e.toRecord()
        override def toStoreRecord(e: GeneratedStoreStyleTimedPostEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[GeneratedStoreStyleTimedPostEntity] =
          Consequence.notImplemented("not used in this spec")
        override def storeFieldName(logicalName: String): String = logicalName match {
          case "postedAt" => "posted_at"
          case other => other
        }
      }

      val recent = GeneratedStoreStyleTimedPostEntity(
        EntityId("m", "gsr", _cid),
        java.time.Instant.parse("2026-04-24T10:00:00Z").toString,
        "recent"
      )
      val old = GeneratedStoreStyleTimedPostEntity(
        EntityId("m", "gso", _cid),
        java.time.Instant.parse("2026-04-22T10:00:00Z").toString,
        "old"
      )
      val storerealm = new EntityRealm[GeneratedStoreStyleTimedPostEntity](
        entityName = "person",
        loader = EntityLoader[GeneratedStoreStyleTimedPostEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[GeneratedStoreStyleTimedPostEntity]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[GeneratedStoreStyleTimedPostEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          workingSetPolicy = Some(WorkingSetPolicy.ResidentAll),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[GeneratedStoreStyleTimedPostEntity]]
      )
      val collection = new EntityCollection[GeneratedStoreStyleTimedPostEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      collection.put(recent)
      collection.put(old)

      val result = collection.search(
        EntityQuery(
          _cid,
          Query.plan(
            Record.empty,
            where = Query.Gte("postedAt", java.time.Instant.parse("2026-04-24T00:00:00Z"))
          ),
          EntitySearchScope.WorkingSet
        )
      )

      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(recent.id))
    }

    "exclude logically deleted entities from the working set even when recent" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[TimedLifecyclePostEntity] = new EntityPersistent[TimedLifecyclePostEntity] {
        def id(e: TimedLifecyclePostEntity): EntityId = e.id
        def toRecord(e: TimedLifecyclePostEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[TimedLifecyclePostEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val live = TimedLifecyclePostEntity(
        EntityId("m", "l", _cid),
        java.time.Instant.now().minusSeconds(3600),
        "live",
        "alive"
      )
      val deleted = TimedLifecyclePostEntity(
        EntityId("m", "d", _cid),
        java.time.Instant.now().minusSeconds(3600),
        "deleted",
        "dead"
      )
      val storerealm = new EntityRealm[TimedLifecyclePostEntity](
        entityName = "person",
        loader = EntityLoader[TimedLifecyclePostEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[TimedLifecyclePostEntity]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[TimedLifecyclePostEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          workingSetPolicy = Some(WorkingSetPolicy.Recent(java.time.Duration.ofHours(24), "postedAt")),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[TimedLifecyclePostEntity]]
      )
      val collection = new EntityCollection[TimedLifecyclePostEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      collection.put(live)
      collection.put(deleted)

      memoryrealm.get(live.id) shouldBe Some(live)
      memoryrealm.get(deleted.id) shouldBe None

      val workingSetResult = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.WorkingSet))
      val storeResult = collection.search(EntityQuery(_cid, Query.plan(Record.empty, includeTotal = true), EntitySearchScope.Store))

      workingSetResult.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(live.id))
      storeResult.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(live.id))
    }

    "filter entities by generated condition object via directive.Query" in {
      given ExecutionContext = ExecutionContext.test()
      Given("an entity collection with in-memory person entities")
      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val p1 = PersonEntity(EntityId("m", "a", _cid), Name("taro"), Age(20))
      val p2 = PersonEntity(EntityId("m", "b", _cid), Name("hanako"), Age(30))
      val p3 = PersonEntity(EntityId("m", "c", _cid), Name("taro"), Age(40))

      val storerealm = new EntityRealm[PersonEntity](
        entityName = "person",
        loader = EntityLoader[PersonEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(p1)
      storerealm.put(p2)
      storerealm.put(p3)

      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.StoreOnly,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[PersonEntity]]
      )
      val collection = new EntityCollection[PersonEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, None)
      )

      When("searching by condition object generated by Cozy")
      val condition = domain.query.Person(
        id = Condition.any[EntityId],
        name = Condition.is(Name("taro")),
        age = Condition.any[Age]
      )
      val query = EntityQuery(_cid, Query(condition))
      val result = collection.search(query)

      Then("only matched entities are returned")
      result.map(_.data.map(_.id).toSet) shouldBe Consequence.success(Set(p1.id, p3.id))
    }

    "apply sql-like where/sort/limit/offset via Query.plan" in {
      given ExecutionContext = ExecutionContext.test()
      Given("an entity collection with in-memory person entities")
      given EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val p1 = PersonEntity(EntityId("m", "a", _cid), Name("taro"), Age(20))
      val p2 = PersonEntity(EntityId("m", "b", _cid), Name("hanako"), Age(30))
      val p3 = PersonEntity(EntityId("m", "c", _cid), Name("jiro"), Age(40))

      val storerealm = new EntityRealm[PersonEntity](
        entityName = "person",
        loader = EntityLoader[PersonEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(p1)
      storerealm.put(p2)
      storerealm.put(p3)

      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "person",
          memoryPolicy = EntityMemoryPolicy.StoreOnly,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[PersonEntity]]
      )
      val collection = new EntityCollection[PersonEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, None)
      )

      When("searching with where(age >= 20), order by age desc, offset 1, limit 1")
      val planned = Query.plan(
        condition = domain.query.Person(
          id = Condition.any[EntityId],
          name = Condition.any[Name],
          age = Condition.any[Age]
        ),
        where = Query.Gte("age.value", 20),
        sort = Vector(Query.SortKey("age.value", Query.SortDirection.Desc)),
        limit = Some(1),
        offset = Some(1),
        includeTotal = true
      )
      val query = EntityQuery(_cid, planned)
      val result = collection.search(query)

      Then("the second oldest person is returned")
      result.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(p2.id))
      result.map(_.totalCount) shouldBe Consequence.success(Some(3))
      result.map(r => (r.offset, r.limit, r.fetchedCount)) shouldBe Consequence.success((Some(1), Some(1), 1))
    }

    "treat camel, snake, and kebab query paths as equivalent" in {
      given ExecutionContext = ExecutionContext.test()
      given EntityPersistent[StatusEntity] = new EntityPersistent[StatusEntity] {
        def id(e: StatusEntity): EntityId = e.id
        def toRecord(e: StatusEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[StatusEntity] =
          Consequence.notImplemented("not used in this spec")
      }

      val s1 = StatusEntity(EntityId("m", "ja", _cid), "Published", "trace-a")
      val s2 = StatusEntity(EntityId("m", "jb", _cid), "Draft", "trace-b")

      val storerealm = new EntityRealm[StatusEntity](
        entityName = "statusPerson",
        loader = EntityLoader[StatusEntity](_ => None),
        state = new _SearchIdRef[EntityRealmState[StatusEntity]](EntityRealmState(Map.empty))
      )
      storerealm.put(s1)
      storerealm.put(s2)

      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "statusPerson",
          memoryPolicy = EntityMemoryPolicy.StoreOnly,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[StatusEntity]]
      )
      val collection = new EntityCollection[StatusEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, None)
      )

      val camel = collection.search(EntityQuery(_cid, Query.plan(Record.empty, where = Query.Eq("postStatus", "Published"))))
      val snake = collection.search(EntityQuery(_cid, Query.plan(Record.empty, where = Query.Eq("post_status", "Published"))))
      val kebab = collection.search(EntityQuery(_cid, Query.plan(Record.empty, where = Query.Eq("post-status", "Published"))))
      val traceSnake = collection.search(EntityQuery(_cid, Query.plan(Record.empty, where = Query.Eq("trace_id", "trace-a"))))
      val traceKebab = collection.search(EntityQuery(_cid, Query.plan(Record.empty, where = Query.Eq("trace-id", "trace-a"))))

      camel.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(s1.id))
      snake.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(s1.id))
      kebab.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(s1.id))
      traceSnake.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(s1.id))
      traceKebab.map(_.data.map(_.id)) shouldBe Consequence.success(Vector(s1.id))
    }
  }
}

private final case class Name(value: String)
private final case class Age(value: Int)

private final case class PersonEntity(
  id: EntityId,
  name: Name,
  age: Age
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name.value,
      "age" -> age.value
    )
}

private final case class StatusEntity(
  id: EntityId,
  postStatus: String,
  traceId: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "postStatus" -> postStatus,
      "traceId" -> traceId
    )
}

private final case class TimedPostEntity(
  id: EntityId,
  postedAt: java.time.Instant,
  body: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "postedAt" -> postedAt.toString,
      "body" -> body
    )
}

private final case class TimedLifecyclePostEntity(
  id: EntityId,
  postedAt: java.time.Instant,
  body: String,
  aliveness: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "postedAt" -> postedAt.toString,
      "body" -> body,
      "aliveness" -> aliveness
    )
}

private final case class GeneratedTimedPostEntity(
  id: EntityId,
  postedAt: String,
  body: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "postedAt" -> postedAt,
      "body" -> body
    )
}

private final case class GeneratedStoreStyleTimedPostEntity(
  id: EntityId,
  postedAt: String,
  body: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "posted_at" -> postedAt,
      "body" -> body
    )
}

private object domain {
  object query {
    final case class Person(
      id: Condition[EntityId],
      name: Condition[Name],
      age: Condition[Age]
    ) extends Query.ConditionShape
  }
}

private final class _SearchIdRef[A](initial: A) extends Ref[cats.Id, A] {
  private var _value: A = initial

  def get: A = synchronized {
    _value
  }

  def set(a: A): Unit = synchronized {
    _value = a
  }

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
