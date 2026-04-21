package org.goldenport.cncf.component

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 10, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryEventReceptionBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "a", "customer")

  "ComponentFactory.createEventReception" should {
    "propagate working-set entity names to reception and enforce keep-resident pub-sub" in {
      Given("component with working-set entity marker and empty memory realm")
      val id = EntityId("m", "a", _cid)
      val entity = _BootEntity(id, "suzuki")
      given EntityPersistent[_BootEntity] = _persistent

      val component = new Component() {}
      component.withWorkingSetEntityNames(Set("customer"))
      component.entitySpace.registerEntity("customer", _collection(id, entity))

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)

      val factory = new ComponentFactory()
      val reception = factory.createEventReceptionWithOperationDispatcher(component, bus)
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
          activationMode = EntityActivationMode.ActivateOnReceive,
          targetResolver = _ => Consequence.success(Vector(id)),
          onEntity = (_, _, _) => Consequence.unit
        )
      )

      When("event is received")
      val result = reception.receive(
        ReceptionInput(
          name = "customer.pubsub",
          kind = "pubsub"
        )
      )

      Then("pub-sub route is treated as keep-resident and fails on memory miss")
      result shouldBe a[Consequence.Failure[_]]
    }

    "register component event/subscription metadata automatically" in {
      Given("component exposing generated event metadata")
      val component = new Component() {
        override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
          Vector(
            CmlEventDefinition(
              name = "person.created",
              category = CmlEventCategory.NonActionEvent,
              kind = Some("created")
            )
          )

        override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
          Vector(
            CmlSubscriptionDefinition(
              name = "person-sync",
              eventName = "person.created",
              route = DispatchRoute.Unicast,
              target = Some("targetId"),
              actionName = "person.sync",
              declaredTargetUpperBound = 1
            )
          )
      }

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = scala.collection.mutable.ArrayBuffer.empty[String]
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          val _ = event
          calls += actionName
          Consequence.unit
        }
      }

      When("factory creates reception")
      val factory = new ComponentFactory()
      val reception = factory.createEventReception(component, bus, dispatcher)
      val result = reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map("targetId" -> "p1")
        )
      )

      Then("metadata is already registered and routed")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("person.sync")
    }

    "dispatch subscription across receptions sharing the same event bus" in {
      Given("publisher and subscriber receptions on one shared event bus")
      val publisher = new Component() {
        override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
          Vector(
            CmlEventDefinition(
              name = "person.created",
              category = CmlEventCategory.NonActionEvent,
              kind = Some("created")
            )
          )
      }
      val subscriber = new Component() {
        override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
          Vector(
            CmlSubscriptionDefinition(
              name = "person-sync",
              eventName = "person.created",
              route = DispatchRoute.Unicast,
              target = Some("targetId"),
              actionName = "person.sync",
              declaredTargetUpperBound = 1
            )
          )
      }

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val calls = scala.collection.mutable.ArrayBuffer.empty[String]
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          val _ = event
          calls += actionName
          Consequence.unit
        }
      }

      val factory = new ComponentFactory()
      val publisherReception = factory.createEventReceptionWithOperationDispatcher(publisher, bus)
      val _ = factory.createEventReception(subscriber, bus, dispatcher)

      When("publisher receives an event")
      val result = publisherReception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map("targetId" -> "p1")
        )
      )

      Then("subscriber subscription is dispatched through the shared bus")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("person.sync")
    }

    "provide operation-based action dispatcher with parse/validate stage" in {
      Given("default component and operation-based dispatcher")
      val component = new Component() {}
      val factory = new ComponentFactory()
      val dispatcher = factory.createOperationActionDispatcher(component)
      val event = ReceptionDomainEvent(
        name = "system.test",
        kind = "test",
        payload = Map.empty,
        attributes = Map.empty
      )

      Then("dispatcher exposes parse/validate flow")
      dispatcher shouldBe a[ActionFactoryDispatcher]

      And("invalid action format fails at parse stage before component resolution")
      val invalid = dispatcher
        .asInstanceOf[ActionFactoryDispatcher]
        .parseValidateAction("help", event)
      invalid shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _collection(
    id: EntityId,
    entity: _BootEntity
  )(using EntityPersistent[_BootEntity]): EntityCollection[_BootEntity] = {
    val storerealm = new EntityRealm[_BootEntity](
      entityName = "customer",
      loader = EntityLoader[_BootEntity](x => if (x == id) Some(entity) else None),
      state = new _IdRef2[EntityRealmState[_BootEntity]](EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[_BootEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _cid,
      plan = EntityRuntimePlan(
        entityName = "customer",
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = summon[EntityPersistent[_BootEntity]]
    )
    new EntityCollection[_BootEntity](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _persistent: EntityPersistent[_BootEntity] =
    new EntityPersistent[_BootEntity] {
      def id(e: _BootEntity): EntityId = e.id
      def toRecord(e: _BootEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_BootEntity] =
        Consequence.notImplemented("not used in this spec")
    }
}

private final case class _BootEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name
    )
}

private final class _InMemoryCommitRecorder extends CommitRecorder {
  private var _entries: Vector[String] = Vector.empty
  def record(entry: String): Unit = _entries = _entries :+ entry
}

private final class _IdRef2[A](initial: A) extends Ref[cats.Id, A] {
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
