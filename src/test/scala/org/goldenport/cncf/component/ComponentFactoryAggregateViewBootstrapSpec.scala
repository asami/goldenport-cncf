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
import org.goldenport.cncf.testutil.{EntityRevisionFixture, TestComponentFactory}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Mar. 24, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryAggregateViewBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _entity_name = "view_bootstrap_person"
  private val _cid = EntityCollectionId("test", "a", _entity_name)

  "ComponentFactory bootstrap" should {
    "register aggregate/view collections from component metadata definitions" in {
      Given("component metadata with one entity collection and aggregate/view definitions")
      given EntityPersistent[PersonEntity] = _persistent
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity(_entity_name, _collection(Vector(
        PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )))
      val factory = new ComponentFactory()

      When("the component factory bootstraps aggregate and view collections")
      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      Then("all declared aggregate, default view, and named view surfaces are registered")
      component.aggregateSpace.collectionOption[Any]("person_aggregate").isDefined shouldBe true
      component.viewSpace.collectionOption[Any]("person_view").isDefined shouldBe true
      component.viewSpace.browserOption[Any]("person_view", "detail").isDefined shouldBe true
      component.viewSpace.browserOption[Any]("person_view", "search_by_city").isDefined shouldBe true
    }

    "provide total count capability for generated default view and aggregate" in {
      Given("a searchable datastore context with two persistent and resident entities")
      given EntityPersistent[PersonEntity] = _persistent
      given ExecutionContext = _execution_context(DataStore.inMemorySearchable())
      val entities = Vector(
        PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )
      val seeded = summon[ExecutionContext].dataStoreSpace.inject(
        DataStoreSpace.Seed(entities.map { entity =>
          EntityRevisionFixture.entitySeed(DataStore.CollectionId.EntityStore(_cid), entity.toRecord())
        })
      )
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity(_entity_name, _collection(entities))
      val factory = new ComponentFactory()

      When("the generated default view and aggregate are bootstrapped")
      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      Then("both surfaces report supported contextual totals from their source entities")
      seeded shouldBe Consequence.unit
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
      Given("a non-searchable datastore and resident source entities")
      given EntityPersistent[PersonEntity] = _persistent
      given ExecutionContext = _execution_context(DataStore.noop())
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity(_entity_name, _collection(Vector(
        PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )))
      val factory = new ComponentFactory()

      When("the generated default view and aggregate are bootstrapped")
      _invoke_bootstrap_aggregates(factory, component)
      _invoke_bootstrap_views(factory, component)

      Then("both surfaces keep contextual total count unsupported")
      val browser = component.viewSpace.browser[Any]("person_view")
      browser.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Unsupported

      val aggregate = component.aggregateSpace.collection[Any]("person_aggregate")
      aggregate.totalCountCapabilityWithContext.TAKE shouldBe TotalCountCapability.Unsupported
    }

    "prefer the complete datastore source over a partial resident working set" in {
      Given("two persisted entities while only one entity remains resident")
      given EntityPersistent[PersonEntity] = _persistent
      val datastore = DataStore.inMemorySearchable()
      given ExecutionContext = _execution_context(datastore)
      val persisted = Vector(
        PersonEntity(EntityId("m", "a", _cid), "taro", "Tokyo"),
        PersonEntity(EntityId("m", "b", _cid), "hanako", "Osaka")
      )
      val seeded = summon[ExecutionContext].dataStoreSpace.inject(
        DataStoreSpace.Seed(persisted.map { entity =>
          EntityRevisionFixture.entitySeed(DataStore.CollectionId.EntityStore(_cid), entity.toRecord())
        })
      )
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity(_entity_name, _collection(persisted.take(1)))
      val factory = new ComponentFactory()
      val query = Query.plan(Record.empty, includeTotal = true)

      When("the default view searches and counts its source entities")
      val searched = _invoke_search_view_sources(factory, component, query)
      val counted = _invoke_count_view_sources(factory, component, query)

      Then("the view uses both persisted records instead of truncating to the resident subset")
      seeded shouldBe Consequence.unit
      searched.map(_.map(_.asInstanceOf[PersonEntity].id)) shouldBe
        Consequence.success(persisted.map(_.id))
      counted shouldBe Consequence.success(2)
    }

    "keep an empty persistent result authoritative over stale resident entities" in {
      Given("an empty searchable datastore while one stale entity remains resident")
      given EntityPersistent[PersonEntity] = _persistent
      given ExecutionContext = _execution_context(DataStore.inMemorySearchable())
      val resident = PersonEntity(EntityId("m", "stale", _cid), "stale", "Tokyo")
      val component = _create_component_with_metadata()
      component.entitySpace.registerEntity(_entity_name, _collection(Vector(resident)))
      val factory = new ComponentFactory()
      val query = Query.plan(Record.empty, includeTotal = true)

      When("the default view searches and counts its source entities")
      val searched = _invoke_search_view_sources(factory, component, query)
      val counted = _invoke_count_view_sources(factory, component, query)

      Then("the empty persistent result does not resurrect the stale resident entity")
      searched shouldBe Consequence.success(Vector.empty)
      counted shouldBe Consequence.success(0)
    }
  }

  private def _create_component_with_metadata(): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "person",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("loadPerson"))
            )
          )
        )
      )
    )

    val component = new Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(AggregateDefinition(
          name = "person_aggregate",
          entityName = _entity_name,
          members = Vector(AggregateMemberDefinition("person", _entity_name))
        ))

      override def viewDefinitions: Vector[ViewDefinition] =
        Vector(
          ViewDefinition(
            name = "person_view",
            entityName = _entity_name,
            viewNames = Vector("detail"),
            queries = Vector(ViewQueryDefinition("search_by_city", Some(s"${_entity_name}.city == query.city")))
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

  private def _invoke_search_view_sources(
    factory: ComponentFactory,
    component: Component,
    query: Query[?]
  )(using context: ExecutionContext): Consequence[Vector[Any]] = {
    val method = classOf[ComponentFactory].getDeclaredMethods.toVector
      .find(_.getName.endsWith("$_search_view_source_entities"))
      .getOrElse(throw new NoSuchMethodException("_search_view_source_entities"))
    method.setAccessible(true)
    method.invoke(factory, component, component.entitySpace, _entity_name, query, context)
      .asInstanceOf[Consequence[Vector[Any]]]
  }

  private def _invoke_count_view_sources(
    factory: ComponentFactory,
    component: Component,
    query: Query[?]
  )(using context: ExecutionContext): Consequence[Int] = {
    val method = classOf[ComponentFactory].getDeclaredMethods.toVector
      .find(_.getName.endsWith("$_count_view_source_entities"))
      .getOrElse(throw new NoSuchMethodException("_count_view_source_entities"))
    method.setAccessible(true)
    method.invoke(factory, component, component.entitySpace, _entity_name, query, context)
      .asInstanceOf[Consequence[Int]]
  }

  private def _initialize_component(
    name: String,
    protocol: Protocol,
    component: Component
  ): Component = {
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(name)
    val instanceid = ComponentInstanceId.default(componentid)
    val factory = new Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: ComponentCreate): Component =
        component

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(componentid.name, componentid, instanceid, protocol, this)
    }

    val core = Component.Core.create(componentid.name, componentid, instanceid, protocol, factory)
    val params = ComponentInit(
      subsystem = TestComponentFactory.emptySubsystem("test"),
      core = core,
      origin = ComponentOrigin.Repository("test")
    )
    component.initialize(params)
  }

  private def _collection(
    entities: Vector[PersonEntity]
  )(using EntityPersistent[PersonEntity]): EntityCollection[PersonEntity] = {
    val storerealm = new EntityRealm[PersonEntity](
      entityName = _entity_name,
      loader = EntityLoader[PersonEntity](x => entities.find(_.id == x)),
      state = new IdRef3[EntityRealmState[PersonEntity]](EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[PersonEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _cid,
      plan = EntityRuntimePlan(
        entityName = _entity_name,
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
    entities.foreach { entity =>
      collection.storage.storeRealm.put(entity)
      collection.storage.memoryRealm.foreach(_.put(entity))
    }
    collection
  }

  private def _persistent: EntityPersistent[PersonEntity] =
    entity.ViewBootstrapPerson.entityPersistent

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
        observabilitycontext = observability,
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
      commitaction = _.commit(),
      abortaction = _.rollback(),
      disposeaction = _ => (),
      token = "aggregate-view-bootstrap-runtime"
    )
    context
  }
}

private final case class NoopOperation(
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

private[component] final case class PersonEntity(
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

private final class IdRef3[A](initial: A) extends Ref[cats.Id, A] {
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

package entity {
  object ViewBootstrapPerson {
    val collectionId: EntityCollectionId = EntityCollectionId("test", "a", "view_bootstrap_person")

    val entityPersistent: EntityPersistent[PersonEntity] =
      new EntityPersistent[PersonEntity] {
        def id(e: PersonEntity): EntityId = e.id
        def toRecord(e: PersonEntity): Record = e.toRecord()
        def fromRecord(r: Record): Consequence[PersonEntity] =
          r.asMap match {
            case m if m.get("id").exists(_.isInstanceOf[EntityId]) =>
              (m.get("id"), m.get("name"), m.get("city")) match {
                case (Some(id: EntityId), Some(name: String), Some(city: String)) =>
                  Consequence.success(PersonEntity(id, name, city))
                case _ =>
                  Consequence.argumentInvalid("invalid person record")
              }
            case _ =>
              Consequence.argumentInvalid("invalid person record")
          }
      }
  }
}
