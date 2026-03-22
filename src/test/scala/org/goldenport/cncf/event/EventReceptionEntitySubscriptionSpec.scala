package org.goldenport.cncf.event

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventReceptionEntitySubscriptionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "1", "customer")

  "EventReception entity subscription" should {
    "activate entity on receive when memory miss occurs" in {
      Given("entity subscription with ActivateOnReceive")
      val id = EntityId("m", "1", _cid)
      val entity = _TestEntity(id, "taro")
      given EntityPersistent[_TestEntity] = _persistent
      val collection = _collection("customer", id, entity)
      val entitiespace = new EntitySpace()
      entitiespace.registerEntity("customer", collection)

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val reception = EventReception.default(
        eventBus = EventBus.default(engine),
        dispatcher = new _NoopDispatcher,
        entitySpace = Some(entitiespace)
      )
      reception.register(
        CmlEventDefinition(
          name = "customer.changed",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("changed")
        )
      )

      var touched: Vector[EntityId] = Vector.empty
      reception.registerEntitySubscription(
        EntityEventSubscription(
          entityName = "customer",
          eventName = Some("customer.changed"),
          kind = Some("changed"),
          activationMode = EntityActivationMode.ActivateOnReceive,
          targetResolver = _ => Consequence.success(Vector(id)),
          onEntity = (eid, e, _) => {
            val _ = e
            touched = touched :+ eid
            Consequence.unit
          }
        )
      )

      When("event is received with memory miss")
      val result = reception.receive(
        ReceptionInput(
          name = "customer.changed",
          kind = "changed"
        )
      )

      Then("entity is loaded through store and subscription handler runs")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      touched shouldBe Vector(id)
      collection.storage.memoryRealm.flatMap(_.get(id)) shouldBe Some(entity)
    }

    "fail KeepResident when entity is not active in memory" in {
      Given("entity subscription with KeepResident and empty memory")
      val id = EntityId("m", "1", _cid)
      val entity = _TestEntity(id, "hanako")
      given EntityPersistent[_TestEntity] = _persistent
      val collection = _collection("customer", id, entity)
      val entitiespace = new EntitySpace()
      entitiespace.registerEntity("customer", collection)

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val reception = EventReception.default(
        eventBus = EventBus.default(engine),
        dispatcher = new _NoopDispatcher,
        entitySpace = Some(entitiespace)
      )
      reception.register(
        CmlEventDefinition(
          name = "customer.probe",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("probe")
        )
      )
      reception.registerEntitySubscription(
        EntityEventSubscription(
          entityName = "customer",
          eventName = Some("customer.probe"),
          kind = Some("probe"),
          activationMode = EntityActivationMode.KeepResident,
          targetResolver = _ => Consequence.success(Vector(id)),
          onEntity = (_, _, _) => Consequence.unit
        )
      )

      When("event is received")
      val result = reception.receive(
        ReceptionInput(
          name = "customer.probe",
          kind = "probe"
        )
      )

      Then("subscription fails because entity is not active in memory")
      result shouldBe a[Consequence.Failure[_]]
    }

    "reject subscription registration when declared target upper bound exceeds limit" in {
      Given("reception with strict entity subscription limit")
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val reception = EventReception.default(
        eventBus = EventBus.default(engine),
        dispatcher = new _NoopDispatcher,
        entitySubscriptionLimit = EntitySubscriptionLimit(
          maxTotalSubscriptions = 10,
          maxSubscriptionsPerEntity = 10,
          maxDeclaredTargetUpperBound = 2
        )
      )

      When("registering entity subscription beyond declared bound limit")
      Then("registration fails immediately")
      assertThrows[IllegalStateException] {
        reception.registerEntitySubscription(
          EntityEventSubscription(
            entityName = "customer",
            route = EntityEventRoute.Direct,
            eventName = Some("customer.changed"),
            kind = Some("changed"),
            declaredTargetUpperBound = 3,
            targetResolver = _ => Consequence.success(Vector.empty),
            onEntity = (_, _, _) => Consequence.unit
          )
        )
      }
    }

    "treat working-set pub-sub subscription as keep-resident automatically" in {
      Given("pub-sub subscription for working-set entity")
      val id = EntityId("m", "1", _cid)
      val entity = _TestEntity(id, "jiro")
      given EntityPersistent[_TestEntity] = _persistent
      val collection = _collection("customer", id, entity)
      val entitiespace = new EntitySpace()
      entitiespace.registerEntity("customer", collection)

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val reception = EventReception.default(
        eventBus = EventBus.default(engine),
        dispatcher = new _NoopDispatcher,
        entitySpace = Some(entitiespace),
        workingSetEntities = Set("customer")
      )
      reception.register(
        CmlEventDefinition(
          name = "customer.pubsub",
          category = CmlEventCategory.NonActionEvent,
          kind = Some("pubsub")
        )
      )
      reception.registerEntitySubscription(
        EntityEventSubscription(
          entityName = "customer",
          route = EntityEventRoute.PubSub,
          eventName = Some("customer.pubsub"),
          kind = Some("pubsub"),
          declaredTargetUpperBound = 1,
          // This will be normalized to KeepResident because entity is in working set.
          activationMode = EntityActivationMode.ActivateOnReceive,
          targetResolver = _ => Consequence.success(Vector(id)),
          onEntity = (_, _, _) => Consequence.unit
        )
      )

      When("event is received while entity is not resident in memory")
      val result = reception.receive(
        ReceptionInput(
          name = "customer.pubsub",
          kind = "pubsub"
        )
      )

      Then("it fails as keep-resident route (no activate-on-receive)")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _collection(
    name: String,
    id: EntityId,
    entity: _TestEntity
  )(using EntityPersistent[_TestEntity]): EntityCollection[_TestEntity] = {
    val storerealm = new EntityRealm[_TestEntity](
      entityName = name,
      loader = EntityLoader[_TestEntity](x => if (x == id) Some(entity) else None),
      state = new _IdRef[EntityRealmState[_TestEntity]](EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[_TestEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _cid,
      plan = EntityRuntimePlan(
        entityName = name,
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[_TestEntity]]
    )
    new EntityCollection[_TestEntity](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _persistent: EntityPersistent[_TestEntity] =
    new EntityPersistent[_TestEntity] {
      def id(e: _TestEntity): EntityId = e.id
      def toRecord(e: _TestEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_TestEntity] =
        Consequence.failure("not used in this spec")
    }
}

private final case class _TestEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name
    )
}

private final class _NoopDispatcher extends ActionCallDispatcher {
  def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
    val _ = (actionName, event)
    Consequence.unit
  }
}

private final class _InMemoryCommitRecorder extends CommitRecorder {
  private var _entries: Vector[String] = Vector.empty
  def record(entry: String): Unit = _entries = _entries :+ entry
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
