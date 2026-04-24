package org.goldenport.cncf.component

import scala.collection.concurrent.TrieMap
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
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
  WorkingSetDefinition,
  WorkingSetInitializer,
  WorkingSetLoadState,
  WorkingSetPolicy
}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryWorkingSetIterableOnceSpec
  extends AnyWordSpec
  with Matchers
  with TableDrivenPropertyChecks
  with GivenWhenThen {
  "ComponentFactory working set initialization" should {
    "materialize IterableOnce and populate both store and memory paths" in {
      Given("a plan working set backed by single-consume iterator")
      val factory = new ComponentFactory()
      val entityspace = new EntitySpace
      val snapshot = TrieMap.empty[EntityId, Any]
      val cid = EntityCollectionId("test", "a", "sample")
      val id = EntityId("m", "a", cid)
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
          Consequence.notImplemented("not used in this spec")
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
      _await_working_set_ready(collection) shouldBe true
      memoryrealm.get(id) shouldBe Some(entity)
      snapshot.get(id) shouldBe Some(entity)
    }

    "populate store and memory from IterableOnce for multiple payload patterns" in {
      val table = Table(
        ("minor", "name"),
        ("a", "alpha"),
        ("b", "bravo"),
        ("c", "charlie")
      )

      forAll(table) { (minor, name) =>
        val factory = new ComponentFactory()
        val entityspace = new EntitySpace
        val snapshot = TrieMap.empty[EntityId, Any]
        val cid = EntityCollectionId("test", "a", "sample")
        val id = EntityId("m", minor, cid)
        val entity = WorkingSetEntity(id, name)

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
            Consequence.notImplemented("not used in this spec")
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

        val plan = EntityRuntimePlan[Any](
          entityName = "sample",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = Some(WorkingSetDefinition[Any]("sample", Iterator.single(entity))),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        )

        _initialize_working_sets_from_plan(factory, Vector(plan), entityspace, snapshot)

        storerealm.get(id) shouldBe Some(entity)
        _await_working_set_ready(collection) shouldBe true
        memoryrealm.get(id) shouldBe Some(entity)
        snapshot.get(id) shouldBe Some(entity)
      }
    }

    "satisfy IterableOnce materialization safety as a ScalaCheck property" in {
      val genMinor = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      val genName = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
      val gencase = for {
        minor <- genMinor
        name <- genName
      } yield (minor, name)

      val property = Prop.forAll(gencase) { (minor, name) =>
        val factory = new ComponentFactory()
        val entityspace = new EntitySpace
        val snapshot = TrieMap.empty[EntityId, Any]
        val cid = EntityCollectionId("test", "a", "sample")
        val id = EntityId("m", minor, cid)
        val entity = WorkingSetEntity(id, name)

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
            Consequence.notImplemented("not used in this spec")
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

        val plan = EntityRuntimePlan[Any](
          entityName = "sample",
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = Some(WorkingSetDefinition[Any]("sample", Iterator.single(entity))),
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        )

        _initialize_working_sets_from_plan(factory, Vector(plan), entityspace, snapshot)

        storerealm.get(id) == Some(entity) &&
        _await_working_set_ready(collection) &&
        memoryrealm.get(id) == Some(entity) &&
        snapshot.get(id) == Some(entity)
      }

      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(20),
        property
      )
      result.passed shouldBe true
    }

    "schedule async preload without consuming the source on the startup thread" in {
      Given("a working-set source that records when its iterator is consumed")
      val entityspace = new EntitySpace
      val cid = EntityCollectionId("test", "a", "sample")
      val id = EntityId("m", "async", cid)
      val entity = WorkingSetEntity(id, "async")
      val source = new RecordingIterableOnce[Any](Iterator.single(entity))
      val queued = new QueuedExecutionContext

      given EntityPersistent[Any] = _any_persistent

      val storerealm = new EntityRealm[Any](
        entityName = "sample",
        loader = EntityLoader[Any](_ => None),
        state = new IdRef2[EntityRealmState[Any]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[Any](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.asInstanceOf[WorkingSetEntity].id
      )
      val collection = new EntityCollection[Any](
        descriptor = _descriptor(cid),
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )
      entityspace.registerEntity("sample", collection)

      When("async preload is scheduled")
      new WorkingSetInitializer(entityspace).preloadAsync(WorkingSetDefinition[Any]("sample", source))(using queued)

      Then("the source is not consumed until the queued background task runs")
      source.consumed shouldBe false
      collection.storage.workingSetStatus.get.state shouldBe WorkingSetLoadState.Loading

      queued.runAll()
      _await_working_set_ready(collection) shouldBe true
      source.consumed shouldBe true
      memoryrealm.get(id) shouldBe Some(entity)
    }

    "leave policy-only working set initializing instead of marking ready from an empty store realm" in {
      Given("a runtime plan with a working-set policy but no explicit preload snapshot")
      val factory = new ComponentFactory()
      val entityspace = new EntitySpace
      val snapshot = TrieMap.empty[EntityId, Any]
      val cid = EntityCollectionId("test", "a", "sample")

      given EntityPersistent[Any] = _any_persistent

      val storerealm = new EntityRealm[Any](
        entityName = "sample",
        loader = EntityLoader[Any](_ => None),
        state = new IdRef2[EntityRealmState[Any]](EntityRealmState(Map.empty))
      )
      val memoryrealm = new PartitionedMemoryRealm[Any](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _.asInstanceOf[WorkingSetEntity].id
      )
      val collection = new EntityCollection[Any](
        descriptor = _descriptor(cid, Some(WorkingSetPolicy.ResidentAll)),
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )
      entityspace.registerEntity("sample", collection)
      val plan = EntityRuntimePlan[Any](
        entityName = "sample",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        workingSetPolicy = Some(WorkingSetPolicy.ResidentAll),
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      )

      When("working sets are initialized from policy-only runtime plans")
      _initialize_working_sets_from_plan(factory, Vector(plan), entityspace, snapshot)

      Then("the collection remains initializing so searches keep direct-store fallback")
      collection.storage.workingSetStatus.get.state shouldBe WorkingSetLoadState.Loading
      collection.workingSetSearchAvailable shouldBe false
      collection.shouldFallbackToStoreForWorkingSet(org.goldenport.cncf.entity.EntityQuery(cid, org.goldenport.cncf.directive.Query.plan(Record.empty))) shouldBe true
    }
  }

  private def _any_persistent: EntityPersistent[Any] =
    new EntityPersistent[Any] {
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
        Consequence.notImplemented("not used in this spec")
    }

  private def _descriptor(
    cid: EntityCollectionId,
    policy: Option[WorkingSetPolicy] = None
  )(using EntityPersistent[Any]): EntityDescriptor[Any] =
    EntityDescriptor[Any](
      collectionId = cid,
      plan = EntityRuntimePlan[Any](
        entityName = "sample",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        workingSetPolicy = policy,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[Any]]
    )

  private def _initialize_working_sets_from_plan(
    factory: ComponentFactory,
    plans: Vector[EntityRuntimePlan[Any]],
    entityspace: EntitySpace,
    snapshot: TrieMap[EntityId, Any]
  ): Unit = {
    val method = classOf[ComponentFactory].getDeclaredMethods
      .find(m =>
        m.getName.contains("_initialize_working_sets_from_plan") &&
        m.getParameterCount == 3 &&
        m.getParameterTypes.headOption.exists(_.getName.contains("Vector"))
      )
      .getOrElse(
        fail("private method _initialize_working_sets_from_plan(plans, entityspace, snapshot) is not found")
      )
    method.setAccessible(true)
    val _ = method.invoke(factory, plans, entityspace, snapshot)
  }

  private def _await_working_set_ready(
    collection: EntityCollection[Any]
  ): Boolean = {
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
    while (!collection.storage.workingSetStatus.get.isReady && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    collection.storage.workingSetStatus.get.isReady
  }
}

private final class RecordingIterableOnce[A](
  delegate: Iterator[A]
) extends IterableOnce[A] {
  @volatile private var _consumed = false

  def consumed: Boolean =
    _consumed

  def iterator: Iterator[A] = {
    _consumed = true
    delegate
  }
}

private final class QueuedExecutionContext extends scala.concurrent.ExecutionContext {
  private var _tasks: Vector[Runnable] = Vector.empty

  def execute(runnable: Runnable): Unit = synchronized {
    _tasks = _tasks :+ runnable
  }

  def reportFailure(cause: Throwable): Unit =
    throw cause

  def runAll(): Unit = {
    val tasks = synchronized {
      val r = _tasks
      _tasks = Vector.empty
      r
    }
    tasks.foreach(_.run())
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
