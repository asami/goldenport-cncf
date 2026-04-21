package org.goldenport.cncf.component

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 10, 2026
 * @version Apr. 21, 2026
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
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val component = _initialized_component("person_component", new Component() {
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
      }, subsystem)

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
          attributes = Map(
            "targetId" -> "p1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample"
          )
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

    "register component reception rules automatically" in {
      Given("component exposing generated reception rule metadata")
      val captured = scala.collection.mutable.ArrayBuffer.empty[Map[String, String]]
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val component = _initialized_component("person_rule_component", new Component() {
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

        override def eventReceptionRuleDefinitions: Vector[EventReceptionRule] =
          Vector(
            EventReceptionRule(
              name = "person-created-sync",
              condition = EventReceptionCondition(
                eventName = Some("person.created"),
                eventKind = Some("created")
              ),
              policy = EventReceptionExecutionPolicy.SameSubsystemDefault
            )
          )
      }, subsystem)

      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          val _ = actionName
          event match {
            case e: ReceptionDomainEvent => captured += e.attributes
            case _ => ()
          }
          Consequence.unit
        }
      }

      When("factory creates reception from component metadata")
      val factory = new ComponentFactory()
      val reception = factory.createEventReception(component, bus, dispatcher)
      val result = reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map(
            "targetId" -> "p1",
            EventReception.StandardAttribute.SourceSubsystem -> "remote-subsystem"
          )
        )
      )

      Then("registered rules are applied without ad hoc wiring")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      captured.head.get(EventReception.StandardAttribute.ReceptionRuleName) shouldBe Some("person-created-sync")
      captured.head.get(EventReception.StandardAttribute.ReceptionPolicy) shouldBe Some(EventReceptionExecutionPolicy.SameSubsystemDefault.modeName)
    }

    "reject ambiguous component reception rules at factory registration time" in {
      Given("component exposing duplicate reception rules")
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val component = _initialized_component("duplicate_rule_component", new Component() {
        override def eventReceptionRuleDefinitions: Vector[EventReceptionRule] =
          Vector(
            EventReceptionRule(
              name = "dup-1",
              condition = EventReceptionCondition(
                originBoundary = Some(EventOriginBoundary.ExternalSubsystem),
                eventName = Some("dup.event"),
                eventKind = Some("created")
              ),
              policy = EventReceptionExecutionPolicy.AsyncNewJobSameSaga
            ),
            EventReceptionRule(
              name = "dup-2",
              condition = EventReceptionCondition(
                originBoundary = Some(EventOriginBoundary.ExternalSubsystem),
                eventName = Some("dup.event"),
                eventKind = Some("created")
              ),
              policy = EventReceptionExecutionPolicy.AsyncNewJobNewSaga
            )
          )
      }, subsystem)
      val recorder = new _InMemoryCommitRecorder
      val store = EventStore.inMemory
      val engine = EventEngine.noop(DataStore.noop(recorder), recorder, store)
      val bus = EventBus.default(engine)
      val factory = new ComponentFactory()

      When("factory registers generated rules")
      val ex = intercept[IllegalStateException] {
        factory.createEventReception(component, bus, new ActionCallDispatcher {
          def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
            val _ = actionName
            val _ = event
            Consequence.unit
          }
        })
      }

      Then("ambiguous equal-priority rules fail deterministically")
      ex.getMessage should include("ambiguous event reception rule")
    }

    "dispatch subscription across receptions sharing the same event bus" in {
      Given("publisher and subscriber receptions on one shared event bus")
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val publisher = _initialized_component("publisher", new Component() {
        override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
          Vector(
            CmlEventDefinition(
              name = "person.created",
              category = CmlEventCategory.NonActionEvent,
              kind = Some("created")
            )
          )
      }, subsystem)
      val subscriber = _initialized_component("subscriber", new Component() {
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
      }, subsystem)

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
          attributes = Map(
            "targetId" -> "p1",
            EventReception.StandardAttribute.SourceSubsystem -> "sample"
          )
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

    "preserve runtime componentlet identity in cross-component event dispatch" in {
      Given("publisher and real runtime componentlet sharing one subsystem bus")
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val publisher = _initialized_component("publisher", new Component() {
        override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
          Vector(
            CmlEventDefinition(
              name = "notice.published",
              category = CmlEventCategory.NonActionEvent,
              kind = Some("published")
            )
          )

        withComponentDescriptors(
          Vector(
            ComponentDescriptor(
              name = Some("notice-board"),
              componentName = Some("notice-board"),
              componentlets = Vector(
                ComponentletDescriptor(name = "public-notice", kind = Some("componentlet"))
              )
            )
          )
        )
      }, subsystem)
      val subscriber = _initialized_component("public-notice", new Component() {
        override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
          Vector(
            CmlSubscriptionDefinition(
              name = "notice-sync",
              eventName = "notice.published",
              route = DispatchRoute.Unicast,
              target = Some("targetId"),
              actionName = "notice.sync"
            )
          )
      }, subsystem, componentIdLabel = "public_notice")

      val calls = scala.collection.mutable.ArrayBuffer.empty[String]
      val attrs = scala.collection.mutable.ArrayBuffer.empty[Map[String, String]]
      val dispatcher = new ActionCallDispatcher {
        def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
          calls += actionName
          event match {
            case e: ReceptionDomainEvent => attrs += e.attributes
            case _ => ()
          }
          Consequence.unit
        }
      }

      val factory = new ComponentFactory()
      val publisherReception = factory.createEventReceptionWithOperationDispatcher(publisher, subsystem.eventBus)
      val _ = factory.createEventReception(subscriber, subsystem.eventBus, dispatcher)
      given org.goldenport.cncf.context.ExecutionContext =
        org.goldenport.cncf.context.ExecutionContext.test(
          org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager
        )

      When("publisher emits an event routed to the runtime componentlet")
      val result = publisherReception.receiveAuthorized(
        ReceptionInput(
          name = "notice.published",
          kind = "published",
          attributes = Map("targetId" -> "n1")
        )
      )

      Then("dispatched event preserves the componentlet runtime name")
      result shouldBe Consequence.success(
        ReceptionResult(
          outcome = ReceptionOutcome.Routed,
          dispatchedCount = 1,
          persisted = false
        )
      )
      calls.toVector shouldBe Vector("notice.sync")
      attrs.head.get(EventReception.StandardAttribute.SourceComponent) shouldBe Some("publisher")
      attrs.head.get(EventReception.StandardAttribute.TargetComponent) shouldBe Some("public-notice")
    }

    "not register metadata-only componentlet as an event reception participant" in {
      Given("primary component descriptor with metadata-only componentlet entry")
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val component = _initialized_component("notice-board", new Component() {}.withComponentDescriptors(
        Vector(
          ComponentDescriptor(
            name = Some("notice-board"),
            componentName = Some("notice-board"),
            componentlets = Vector(
              ComponentletDescriptor(name = "public-notice", kind = Some("componentlet"))
            )
          )
        )
      ), subsystem, componentIdLabel = "notice_board")

      When("factory bootstraps the primary component only")
      val bootstrapped = new ComponentFactory().bootstrap(component)

      Then("no runtime reception is materialized for metadata-only componentlets")
      bootstrapped.eventReception shouldBe empty
      subsystem.eventReceptions.contains("notice-board") shouldBe false
      subsystem.eventReceptions.contains("public-notice") shouldBe false
    }

    "register bootstrapped reception into subsystem-owned facilities" in {
      Given("initialized component with event metadata and subsystem ownership")
      val subsystem = TestComponentFactory.emptySubsystem("sample")
      val component = _initialized_component("boot_component", new Component() {
        override def eventReceptionDefinitions: Vector[CmlEventDefinition] =
          Vector(
            CmlEventDefinition(
              name = "boot.event",
              category = CmlEventCategory.NonActionEvent,
              kind = Some("created")
            )
          )

        override def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] =
          Vector(
            CmlSubscriptionDefinition(
              name = "boot-sync",
              eventName = "boot.event",
              route = DispatchRoute.Unicast,
              target = Some("targetId"),
              actionName = "boot.sync"
            )
          )
      }, subsystem)

      When("factory bootstraps the component")
      val bootstrapped = new ComponentFactory().bootstrap(component)

      Then("subsystem exposes the shared event reception facility")
      bootstrapped.eventReception.nonEmpty shouldBe true
      subsystem.eventReceptions.get("boot_component") shouldBe bootstrapped.eventReception
      bootstrapped.eventStore shouldBe Some(subsystem.eventStore)
      subsystem.eventBus should not be null
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

  private def _initialized_component(
    name: String,
    component: Component,
    subsystem: Subsystem,
    componentIdLabel: String = ""
  ): Component = {
    val idlabel = if (componentIdLabel.nonEmpty) componentIdLabel else name
    val componentId = ComponentId(idlabel)
    val instanceId = ComponentInstanceId.default(componentId)
    val core = Component.Core.create(name, componentId, instanceId, Protocol.empty)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
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
