/*
 * @since   Mar. 30, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
package org.goldenport.cncf.component

import cats.~>
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.entity.aggregate.{AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.testutil.{EntityRevisionFixture, TestComponentFactory}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

final class ComponentFactoryDefaultAggregateCollectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import org.goldenport.cncf.component.entity.{Customer => CustomerEntity, Order => OrderEntity, OrderLine => OrderLineEntity}
  import org.goldenport.cncf.component.entity.aggregate.Order as OrderAggregate

  private val _order_id = EntityId("m", "o1", OrderEntity.collectionId)
  private val _customer_id = EntityId("m", "c1", CustomerEntity.collectionId)
  private val _line_id = EntityId("m", "l1", OrderLineEntity.collectionId)

  "ComponentFactory default aggregate collection" should {
    "build aggregate with plural composition member and singular aggregation member" in {
      Given("a generated aggregate whose members use composition and aggregation")
      val component = _component_with_default_aggregate()
      val factory = new ComponentFactory()
      _invoke_bootstrap_aggregates(factory, component)

      given ExecutionContext = _execution_context(_seed_records())

      When("the aggregate is resolved from its persisted root")
      val aggregate = component.aggregateSpace.resolve_with_context[OrderAggregate](_order_id).TAKE

      Then("plural composition and singular aggregation members are assembled")
      aggregate.id shouldBe _order_id
      aggregate.lines.map(_.id) shouldBe Vector(_line_id)
      aggregate.customer.map(_.id) shouldBe Some(_customer_id)
    }

    "keep promoted aggregate members out of the parent entity storage record" in {
      Given("an aggregate whose members are stored in their own Entity collections")
      val component = _component_with_default_aggregate()
      val factory = new ComponentFactory()
      _invoke_bootstrap_aggregates(factory, component)

      given ExecutionContext = _execution_context(_seed_records())
      val orderrecord = _load_store_record(OrderEntity.collectionId, _order_id)
      val linerecord = _load_store_record(OrderLineEntity.collectionId, _line_id)
      val customerrecord = _load_store_record(CustomerEntity.collectionId, _customer_id)

      When("the aggregate and its physical Entity records are loaded")
      val aggregate = component.aggregateSpace.resolve_with_context[OrderAggregate](_order_id).TAKE

      Then("the parent record omits promoted members while their own records retain the join")
      orderrecord.getString("name") shouldBe Some("Alpha")
      orderrecord.getAny("lines") shouldBe None
      orderrecord.getAny("customer") shouldBe None
      orderrecord.getAny("order_line") shouldBe None
      linerecord.getString("orderId").orElse(linerecord.getString("order_id")) shouldBe
        Some(_order_id.value)
      customerrecord.getString("orderId").orElse(customerrecord.getString("order_id")) shouldBe
        Some(_order_id.value)
      aggregate.lines.map(_.id) shouldBe Vector(_line_id)
      aggregate.customer.map(_.id) shouldBe Some(_customer_id)
    }

    "use aggregate-internal visibility while direct entity search still hides draft entities" in {
      Given("a draft aggregate root and its persisted members")
      val component = _component_with_default_aggregate()
      val factory = new ComponentFactory()
      _invoke_bootstrap_aggregates(factory, component)

      When("the same root is read directly and through the aggregate boundary")
      val directorders = {
        given ExecutionContext = _execution_context(_seed_records())
        EntityStore.standard().search[org.goldenport.cncf.component.entity.Order](
          EntityQuery(OrderEntity.collectionId, Query(Record.data("name" -> "Alpha")))
        ).TAKE
      }
      val aggregate = {
        given ExecutionContext = _execution_context(_seed_records())
        component.aggregateSpace.resolve_with_context[OrderAggregate](_order_id).TAKE
      }

      Then("public Entity search hides the draft while aggregate-internal resolution retains it")
      directorders.data shouldBe Vector.empty
      aggregate.id shouldBe _order_id
      aggregate.lines.map(_.id) shouldBe Vector(_line_id)
      aggregate.customer.map(_.id) shouldBe Some(_customer_id)
    }

    "persist aggregate command output through the ActionCall execution context" in {
      Given("a generated aggregate command and an empty EntityStore")
      val component = new ComponentFactory().bootstrap(_component_with_default_aggregate())
      given ExecutionContext = _execution_context(Vector.empty)
      val aggregate = OrderAggregate(
        id = _order_id,
        name = "Created through aggregate",
        status = "Active",
        customer = None,
        lines = Vector.empty
      )
      val action = _AggregatePersistenceAction(
        Request.ofService("order", "createOrder"),
        aggregate
      )
      val call = action.createCall(ActionCall.Core(action, summon[ExecutionContext], Some(component), None))

      When("the aggregate command is executed")
      val result = call.execute()

      Then("the aggregate root is persisted without embedding promoted members")
      withClue(result) {
        result.isSuccess shouldBe true
      }

      val stored = _load_store_record(OrderEntity.collectionId, _order_id)
      stored.getString("name") shouldBe Some("Created through aggregate")
      stored.getAny("lines") shouldBe None
      stored.getAny("customer") shouldBe None
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
                name = "line_items",
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
    val componentid = ComponentId(name)
    val instanceid = ComponentInstanceId.default(componentid)
    val factory = new Component.SinglePrimaryBundleFactory {
      override protected def create_Component(params: ComponentCreate): Component =
        component

      override protected def create_Core(
        params: ComponentCreate,
        comp: Component
      ): Component.Core =
        Component.Core.create(name, componentid, instanceid, protocol, this)
    }

    val core = Component.Core.create(name, componentid, instanceid, protocol, factory)
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
          EntityRevisionFixture.entitySeed(
            DataStore.CollectionId.EntityStore(cid),
            record
          )
        }
      )
    )
    context
  }

  private def _load_store_record(
    cid: org.simplemodeling.model.datatype.EntityCollectionId,
    id: EntityId
  )(using ctx: ExecutionContext): Record = {
    val collection = DataStore.CollectionId.EntityStore(cid)
    val dsid = ctx.entityStoreSpace.dataStoreEntryId(id).TAKE
    val ds = ctx.dataStoreSpace.dataStore(collection).TAKE
    ds.load(collection, dsid).TAKE.getOrElse(fail(s"record not found: ${id.print}"))
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
    Consequence.notImplemented("not used")
}

private final case class _AggregatePersistenceAction(
  request: Request,
  aggregate: org.goldenport.cncf.component.entity.aggregate.Order
) extends Action {
  override def createCall(core: ActionCall.Core): ActionCall =
    _AggregatePersistenceActionCall(core, aggregate)
}

private final case class _AggregatePersistenceActionCall(
  core: ActionCall.Core,
  aggregate: org.goldenport.cncf.component.entity.aggregate.Order
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    aggregate_create_c(
      "order",
      "createOrder",
      Consequence.success(aggregate)
    ).map(result => OperationResponse.create(result.toRecord()))
}
