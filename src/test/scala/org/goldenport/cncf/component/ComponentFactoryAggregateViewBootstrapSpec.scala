package org.goldenport.cncf.component

import cats.data.{NonEmptyVector, State}
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.protocol.Request
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.aggregate.AggregateDefinition
import org.goldenport.cncf.entity.view.ViewDefinition
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryAggregateViewBootstrapSpec extends AnyWordSpec with Matchers {
  private val _cid = EntityCollectionId("test", "1", "person")

  "ComponentFactory bootstrap" should {
    "register aggregate/view collections from component metadata definitions" in {
      given EntityPersistent[_PersonEntity] = _persistent
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity("person", _collection(EntityId("m", "1", _cid), _PersonEntity(EntityId("m", "1", _cid), "taro")))
      val factory = new ComponentFactory()

      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      component.aggregateSpace.collectionOption[Any]("person_aggregate").isDefined shouldBe true
      component.viewSpace.collectionOption[Any]("person_view").isDefined shouldBe true
      component.viewSpace.browserOption[Any]("person_view", "detail").isDefined shouldBe true
    }
  }

  private def _create_component_with_metadata(): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "person",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(_NoopOperation("loadPerson"))
            )
          )
        )
      )
    )

    val component = new Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(AggregateDefinition(name = "person_aggregate", entityName = "person"))

      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "person_view",
            entityName = "person",
            viewNames = Vector("detail")
          )
        )
    }
    _initialize_component("av_metadata", protocol, component)
  }

  private def _invoke_bootstrap_aggregates(factory: ComponentFactory, component: Component): Unit = {
    val method = classOf[ComponentFactory].getDeclaredMethod(
      "_bootstrap_aggregates",
      classOf[Component],
      classOf[org.goldenport.cncf.entity.aggregate.AggregateSpace],
      classOf[EntitySpace]
    )
    method.setAccessible(true)
    val _ = method.invoke(factory, component, component.aggregateSpace, component.entitySpace)
  }

  private def _invoke_bootstrap_views(factory: ComponentFactory, component: Component): Unit = {
    val method = classOf[ComponentFactory].getDeclaredMethod(
      "_bootstrap_views",
      classOf[Component],
      classOf[org.goldenport.cncf.entity.view.ViewSpace],
      classOf[EntitySpace]
    )
    method.setAccessible(true)
    val _ = method.invoke(factory, component, component.viewSpace, component.entitySpace)
  }

  private def _initialize_component(
    name: String,
    protocol: Protocol,
    component: Component
  ): Component = {
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    val factory = new Component.Factory {
      override protected def create_Components(params: ComponentCreate): Vector[Component] =
        Vector.empty

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(name, componentId, instanceId, protocol, this)
    }

    val core = Component.Core.create(name, componentId, instanceId, protocol, factory)
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("test"),
      core = core,
      origin = ComponentOrigin.Repository("test")
    )
    component.initialize(params)
  }

  private def _collection(
    id: EntityId,
    entity: _PersonEntity
  )(using EntityPersistent[_PersonEntity]): EntityCollection[_PersonEntity] = {
    val storerealm = new EntityRealm[_PersonEntity](
      entityName = "person",
      loader = EntityLoader[_PersonEntity](x => if (x == id) Some(entity) else None),
      state = new _IdRef3[EntityRealmState[_PersonEntity]](EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[_PersonEntity](
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
      persistent = summon[EntityPersistent[_PersonEntity]]
    )
    new EntityCollection[_PersonEntity](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _persistent: EntityPersistent[_PersonEntity] =
    new EntityPersistent[_PersonEntity] {
      def id(e: _PersonEntity): EntityId = e.id
      def toRecord(e: _PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_PersonEntity] =
        Consequence.failure("not used")
    }
}

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.failure("not used")
}

private final case class _PersonEntity(
  id: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name
    )
}

private final class _IdRef3[A](initial: A) extends Ref[cats.Id, A] {
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
