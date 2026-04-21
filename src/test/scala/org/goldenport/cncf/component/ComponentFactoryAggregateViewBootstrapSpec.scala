package org.goldenport.cncf.component

import cats.data.{NonEmptyVector, State}
import cats.effect.Ref
import cats.~>
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.protocol.Request
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, TotalCountCapability}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.aggregate.{AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.view.{ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryAggregateViewBootstrapSpec extends AnyWordSpec with Matchers {
  private val _cid = EntityCollectionId("test", "a", "person")

  "ComponentFactory bootstrap" should {
    "register aggregate/view collections from component metadata definitions" in {
      given EntityPersistent[_PersonEntity] = _persistent
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity("person", _collection(Vector(
        _PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        _PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )))
      val factory = new ComponentFactory()

      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      component.aggregateSpace.collectionOption[Any]("person_aggregate").isDefined shouldBe true
      component.viewSpace.collectionOption[Any]("person_view").isDefined shouldBe true
      component.viewSpace.browserOption[Any]("person_view", "detail").isDefined shouldBe true
      component.viewSpace.browserOption[Any]("person_view", "search_by_city").isDefined shouldBe true
    }

    "provide total count capability for generated default view and aggregate" in {
      given EntityPersistent[_PersonEntity] = _persistent
      given ExecutionContext = ExecutionContext.create()
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity("person", _collection(Vector(
        _PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        _PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )))
      val factory = new ComponentFactory()

      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      val browser = component.viewSpace.browser[Any]("person_view")
      browser.totalCountCapability shouldBe TotalCountCapability.Unsupported
      browser.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Supported
      browser.count_with_context(Query.plan(Record.empty, includeTotal = true)).TAKE shouldBe 2

      val aggregate = component.aggregateSpace.collection[Any]("person_aggregate")
      aggregate.totalCountCapability shouldBe TotalCountCapability.Unsupported
      aggregate.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Supported
      aggregate.count_with_context(Query.plan(Record.empty, includeTotal = true)).TAKE shouldBe 2
    }

    "derive unsupported total count capability from a non-searchable datastore" in {
      given EntityPersistent[_PersonEntity] = _persistent
      given ExecutionContext = _execution_context(DataStore.noop())
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity("person", _collection(Vector(
        _PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        _PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )))
      val factory = new ComponentFactory()

      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      val browser = component.viewSpace.browser[Any]("person_view")
      browser.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Unsupported

      val aggregate = component.aggregateSpace.collection[Any]("person_aggregate")
      aggregate.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Unsupported
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
        Vector(AggregateDefinition(
          name = "person_aggregate",
          entityName = "person",
          members = Vector(AggregateMemberDefinition("person", "person"))
        ))

      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "person_view",
            entityName = "person",
            viewNames = Vector("detail"),
            queries = Vector(ViewQueryDefinition("search_by_city", Some("person.city == query.city")))
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
    val factory = new Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: ComponentCreate): Component =
        component

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
    entities: Vector[_PersonEntity]
  )(using EntityPersistent[_PersonEntity]): EntityCollection[_PersonEntity] = {
    val storerealm = new EntityRealm[_PersonEntity](
      entityName = "person",
      loader = EntityLoader[_PersonEntity](x => entities.find(_.id == x)),
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
    val collection = new EntityCollection[_PersonEntity](
      descriptor = descriptor,
      storage = EntityStorage(storerealm, Some(memoryrealm))
    )
    entities.foreach { entity =>
      collection.storage.storeRealm.put(entity)
      collection.storage.memoryRealm.foreach(_.put(entity))
    }
    collection
  }

  private def _persistent: EntityPersistent[_PersonEntity] =
    new EntityPersistent[_PersonEntity] {
      def id(e: _PersonEntity): EntityId = e.id
      def toRecord(e: _PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[_PersonEntity] =
        Consequence.notImplemented("not used")
    }

  private def _execution_context(
    datastore: DataStore
  ): ExecutionContext = {
    val datastorespace = new DataStoreSpace().addDataStore(datastore)
    val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "aggregate_view_bootstrap"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "aggregate_view_bootstrap"))
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = RuntimeContext.core(
        name = "aggregate-view-bootstrap-runtime",
        parent = None,
        observabilityContext = observability,
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
      commitAction = _.commit(),
      abortAction = _.rollback(),
      disposeAction = _ => (),
      token = "aggregate-view-bootstrap-runtime"
    )
    context
  }
}

private final case class _NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.notImplemented("not used")
}

private final case class _PersonEntity(
  id: EntityId,
  name: String,
  city: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id,
      "name" -> name,
      "city" -> city
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
