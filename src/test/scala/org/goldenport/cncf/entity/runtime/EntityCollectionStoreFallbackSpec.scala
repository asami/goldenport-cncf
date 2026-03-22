package org.goldenport.cncf.entity.runtime

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityCollectionStoreFallbackSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "1", "sample")

  "EntityCollection.resolve" should {
    "load from store realm on memory miss and cache it into memory realm" in {
      Given("an entity collection with empty memory and store loader containing the entity")
      val id = EntityId("m", "1", _cid)
      val entity = TestEntity(id, "name-1")

      given EntityPersistent[TestEntity] = new EntityPersistent[TestEntity] {
        def id(e: TestEntity): EntityId = e.id
        def toRecord(e: TestEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[TestEntity] =
          Consequence.failure("not used in this spec")
      }

      val storerealm = new EntityRealm[TestEntity](
        entityName = "sample",
        loader = EntityLoader[TestEntity](x => if (x == id) Some(entity) else None),
        state = new IdRef[EntityRealmState[TestEntity]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[TestEntity](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.id
      )
      val descriptor = EntityDescriptor(
        collectionId = _cid,
        plan = EntityRuntimePlan(
          entityName = "sample",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[TestEntity]]
      )
      val collection = new EntityCollection[TestEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )

      When("resolving the entity")
      val result = collection.resolve(id)

      Then("resolution succeeds via store fallback")
      result shouldBe Consequence.success(entity)

      And("the loaded entity is cached in memory realm")
      memoryrealm.get(id) shouldBe Some(entity)
    }
  }
}

private final case class TestEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name
    )
}

private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
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
