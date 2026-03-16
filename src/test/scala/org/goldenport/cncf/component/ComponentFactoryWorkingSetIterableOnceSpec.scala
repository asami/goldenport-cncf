package org.goldenport.cncf.component

import scala.collection.concurrent.TrieMap
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntitySpace,
  EntityStorage,
  PartitionStrategy,
  PartitionedMemoryRealm,
  WorkingSetDefinition
}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryWorkingSetIterableOnceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "ComponentFactory working set initialization" should {
    "materialize IterableOnce and populate both store and memory paths" in {
      Given("a plan working set backed by single-consume iterator")
      val factory = new ComponentFactory()
      val entityspace = new EntitySpace
      val snapshot = TrieMap.empty[EntityId, Any]
      val cid = EntityCollectionId("test", "1", "sample")
      val id = EntityId("m", "1", cid)
      val entity = WorkingSetEntity(id, "sample")

      given EntityPersistent[Any] = new EntityPersistent[Any] {
        def id(e: Any): EntityId =
          e match {
            case m: WorkingSetEntity => m.id
            case _ => throw new IllegalStateException("unsupported entity")
          }
        def toRecord(e: Any): Record =
          e match {
            case m: WorkingSetEntity => m.toRecord()
            case _ => Record.empty
          }
        def fromRecord(r: Record): Consequence[Any] =
          Consequence.failure("not used in this spec")
      }

      val storerealm = new EntityRealm[Any](
        entityName = "sample",
        loader = EntityLoader[Any](_ => None),
        state = new IdRef2[EntityRealmState[Any]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[Any](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.asInstanceOf[WorkingSetEntity].id
      )
      val descriptor = EntityDescriptor[Any](
        collectionId = cid,
        plan = EntityRuntimePlan[Any](
          entityName = "sample",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent = summon[EntityPersistent[Any]]
      )
      val collection = new EntityCollection[Any](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )
      entityspace.registerEntity("sample", collection)

      val iterator = Iterator.single(entity)
      val plan = EntityRuntimePlan[Any](
        entityName = "sample",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = Some(WorkingSetDefinition[Any]("sample", iterator)),
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      )

      When("initializing working set from plan")
      _initialize_working_sets_from_plan(factory, Vector(plan), entityspace, snapshot)

      Then("store realm and memory realm are both populated from the same source")
      storerealm.get(id) shouldBe Some(entity)
      memoryrealm.get(id) shouldBe Some(entity)
      snapshot.get(id) shouldBe Some(entity)
    }
  }

  private def _initialize_working_sets_from_plan(
    factory: ComponentFactory,
    plans: Vector[EntityRuntimePlan[Any]],
    entityspace: EntitySpace,
    snapshot: TrieMap[EntityId, Any]
  ): Unit = {
    val method = classOf[ComponentFactory].getDeclaredMethods
      .find(m =>
        m.getName.contains("_initialize_working_sets_from_plan") &&
        m.getParameterCount == 3
      )
      .getOrElse(
        fail("private method _initialize_working_sets_from_plan(plans, entityspace, snapshot) is not found")
      )
    method.setAccessible(true)
    val _ = method.invoke(factory, plans, entityspace, snapshot)
  }
}

private final case class WorkingSetEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name
    )
}

private final class IdRef2[A](initial: A) extends Ref[cats.Id, A] {
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
