/*
 * @since   Mar. 30, 2026
 * @version Apr. 10, 2026
 */
package org.goldenport.cncf.component

import cats.~>
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.entity.aggregate.{AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

final class ComponentFactoryDefaultAggregateCollectionSpec extends AnyWordSpec with Matchers {
  import org.goldenport.cncf.component.entity.{Customer => CustomerEntity, Order => OrderEntity, OrderLine => OrderLineEntity}
  import org.goldenport.cncf.component.entity.aggregate.Order as OrderAggregate

  private val _order_id = EntityId("m", "o1", OrderEntity.collectionId)
  private val _customer_id = EntityId("m", "c1", CustomerEntity.collectionId)
  private val _line_id = EntityId("m", "l1", OrderLineEntity.collectionId)

  "ComponentFactory default aggregate collection" should {
    "build aggregate with plural composition member and singular aggregation member" in {
      val component = _component_with_default_aggregate()
      val factory = new ComponentFactory()
      _invoke_bootstrap_aggregates(factory, component)

      given ExecutionContext = _execution_context(_seed_records())
      val aggregate = component.aggregateSpace.resolve_with_context[OrderAggregate](_order_id).TAKE

      aggregate.id shouldBe _order_id
      aggregate.lines.map(_.id) shouldBe Vector(_line_id)
      aggregate.customer.map(_.id) shouldBe Some(_customer_id)
    }

    "use aggregate-internal visibility while direct entity search still hides draft entities" in {
      val component = _component_with_default_aggregate()
      val factory = new ComponentFactory()
      _invoke_bootstrap_aggregates(factory, component)

      val directOrders = {
        given ExecutionContext = _execution_context(_seed_records())
        EntityStore.standard().search[org.goldenport.cncf.component.entity.Order](
          EntityQuery(OrderEntity.collectionId, Query(Record.data("name" -> "Alpha")))
        ).TAKE
      }
      directOrders.data shouldBe Vector.empty

      val aggregate = {
        given ExecutionContext = _execution_context(_seed_records())
        component.aggregateSpace.resolve_with_context[OrderAggregate](_order_id).TAKE
      }
      aggregate.id shouldBe _order_id
      aggregate.lines.map(_.id) shouldBe Vector(_line_id)
      aggregate.customer.map(_.id) shouldBe Some(_customer_id)
    }
  }

  private def _component_with_default_aggregate(): Component = {
    given org.goldenport.cncf.entity.EntityPersistent[org.goldenport.cncf.component.entity.Order] = OrderEntity.given_EntityPersistent_Order
    given org.goldenport.cncf.entity.EntityPersistent[org.goldenport.cncf.component.entity.OrderLine] = OrderLineEntity.given_EntityPersistent_OrderLine
    given org.goldenport.cncf.entity.EntityPersistent[org.goldenport.cncf.component.entity.Customer] = CustomerEntity.given_EntityPersistent_Customer
    _create_component_with_metadata()
  }

  private def _create_component_with_metadata(): Component = {
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "order",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(_AggregateNoopOperation("loadOrderAggregate"))
            )
          )
        )
      )
    )

    val component = new Component() {
      override def aggregateDefinitions: Vector[AggregateDefinition] =
        Vector(
          AggregateDefinition(
            name = "order",
            entityName = "order",
            members = Vector(
              AggregateMemberDefinition(
                name = "lines",
                entityName = "order_line",
                kind = Some("composition"),
                joinFieldName = Some("orderId"),
                multiplicity = Some("*")
              ),
              AggregateMemberDefinition(
                name = "customer",
                entityName = "customer",
                kind = Some("aggregation"),
                joinFieldName = Some("orderId"),
                multiplicity = Some("1")
              )
            )
          )
        )
    }
    _initialize_component("aggregate_default", protocol, component)
  }

  private def _invoke_bootstrap_aggregates(factory: ComponentFactory, component: Component): Unit = {
    val method = classOf[ComponentFactory].getDeclaredMethod(
      "_bootstrap_aggregates",
      classOf[Component],
      classOf[org.goldenport.cncf.entity.aggregate.AggregateSpace],
      classOf[org.goldenport.cncf.entity.runtime.EntitySpace]
    )
    method.setAccessible(true)
    val _ = method.invoke(factory, component, component.aggregateSpace, component.entitySpace)
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
      subsystem = TestComponentFactory.emptySubsystem("aggregate_default_spec"),
      core = core,
      origin = ComponentOrigin.Repository("test")
    )
    component.initialize(params)
  }

  private def _execution_context(
    seed: Vector[(org.simplemodeling.model.datatype.EntityCollectionId, Record)]
  ): ExecutionContext = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "default_aggregate_collection"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "default_aggregate_collection"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "default-aggregate-collection-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used directly in this spec")
        }
      },
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "default-aggregate-collection-runtime"
    )
    given ExecutionContext = context
    val _ = datastorespace.inject(
      DataStoreSpace.Seed(
        seed.map { case (cid, record) =>
          DataStoreSpace.SeedEntry(
            DataStore.CollectionId.EntityStore(cid),
            record
          )
        }
      )
    )
    context
  }

  private def _order_entity =
    org.goldenport.cncf.component.entity.Order(
      id = _order_id,
      name = "Alpha",
      status = "Active"
    )

  private def _line_entity =
    org.goldenport.cncf.component.entity.OrderLine(
      id = _line_id,
      orderId = _order_id,
      name = "Widget",
      quantity = 2
    )

  private def _customer_entity =
    org.goldenport.cncf.component.entity.Customer(
      id = _customer_id,
      orderId = _order_id,
      name = "Acme"
    )

  private def _seed_records(): Vector[(org.simplemodeling.model.datatype.EntityCollectionId, Record)] =
    Vector(
      OrderEntity.collectionId -> _order_entity.toRecord(),
      OrderLineEntity.collectionId -> _line_entity.toRecord(),
      CustomerEntity.collectionId -> _customer_entity.toRecord()
    )
}

private final case class _AggregateNoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.failure("not used")
}
