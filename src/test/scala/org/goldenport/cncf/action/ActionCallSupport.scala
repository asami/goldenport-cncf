package org.goldenport.cncf.action

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.context.DataStoreContext
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, ProductBuilder, SalesOrderBuilder, UserBuilder}
import org.goldenport.cncf.entity.runtime.testdomain.{Product, SalesOrder, SalesOrderLine, User}
import org.goldenport.protocol.Request

/*
 * @since   Mar. 19, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object ActionCallSupport {
  sealed trait TestDataKind
  object TestDataKind {
    case object SalesOrderData extends TestDataKind
    case object SalesOrderLineData extends TestDataKind
    case object UserData extends TestDataKind
    case object ProductData extends TestDataKind
    case object AllData extends TestDataKind
  }

  final case class Pair(
    component: Option[Component],
    executioncontext: ExecutionContext
  )

  def defaultPair(): Pair =
    Pair(None, ExecutionContext.create())

  def componentPair(
    component: Component
  ): Pair =
    Pair(Some(component), ExecutionContext.create())

  def componentPair(
    component: Component,
    executioncontext: ExecutionContext
  ): Pair =
    Pair(Some(component), executioncontext)

  def componentPairWithDataStore(
    component: Component,
    seed: DataStoreSpace.Seed
  ): Pair = {
    val datastorespace = DataStoreSpace.default()
    val executioncontext = _execution_context_with_datastore(datastorespace)
    given ExecutionContext = executioncontext
    val _ = datastorespace.inject(seed)
    Pair(Some(component), executioncontext)
  }

  def componentPairWithTestData(
    component: Component,
    kind: TestDataKind
  ): Pair = {
    val datastorespace = DataStoreSpace.default()
    val executioncontext = _execution_context_with_datastore(datastorespace)
    given ExecutionContext = executioncontext
    val seed = DataStoreSpace.Seed(_seed_entries(kind))
    val _ = datastorespace.inject(seed)
    Pair(Some(component), executioncontext)
  }

  def componentPairWithTestData(
    kind: TestDataKind
  ): Pair = {
    val component = new Component() {}
    _register_aggregates(component)
    componentPairWithTestData(component, kind)
  }

  def executionContextWithTestData(
    kind: TestDataKind
  ): ExecutionContext = {
    val datastorespace = DataStoreSpace.default()
    val executioncontext = _execution_context_with_datastore(datastorespace)
    given ExecutionContext = executioncontext
    val seed = DataStoreSpace.Seed(_seed_entries(kind))
    val _ = datastorespace.inject(seed)
    executioncontext
  }

  def pair(
    component: Option[Component],
    executioncontext: ExecutionContext
  ): Pair =
    Pair(component, executioncontext)

  def actionCall(
    actionname: String,
    pair: Pair
  )(createcall: ActionCall.Core => ActionCall): ActionCall = {
    val action = new CommandAction() {
      val request = Request.ofOperation(actionname)
      def createCall(core: ActionCall.Core): ActionCall =
        createcall(core)
    }
    val core = ActionCall.Core(action, pair.executioncontext, pair.component, None)
    action.createCall(core)
  }

  def actionCall(
    actionname: String,
    kind: TestDataKind
  )(createcall: ActionCall.Core => ActionCall): ActionCall =
    actionCall(actionname, componentPairWithTestData(kind))(createcall)

  def actionCall(
    kind: TestDataKind
  )(createcall: ActionCall.Core => ActionCall): ActionCall =
    actionCall("action-call", componentPairWithTestData(kind))(createcall)

  def actionCallCore(
    action: Action,
    pair: Pair
  ): ActionCall.Core =
    ActionCall.Core(action, pair.executioncontext, pair.component, None)

  private def _execution_context_with_datastore(
    datastorespace: DataStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "action-call-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
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
      token = "action-call-spec-runtime-context"
    )
    context
  }

  private def _seed_entries(
    kind: TestDataKind
  ): Vector[DataStoreSpace.SeedEntry] = {
    val lineid = _entity_id("line_1", SalesOrderLine.collectionId)
    val orderid = _entity_id("order_1", SalesOrder.collectionId)
    val userid = _entity_id("user_1", User.collectionId)
    val productid = _entity_id("product_1", Product.collectionId)

    val line = SalesOrderLine(lineid, "sku-1", 1)
    val order = SalesOrder(orderid, line)
    val user = User(userid, "user-1")
    val product = Product(productid, "product-1")

    val lineentry = _seed_entry(SalesOrderLine.collectionId, line.toRecord())
    val orderentry = _seed_entry(SalesOrder.collectionId, order.toRecord())
    val userentry = _seed_entry(User.collectionId, user.toRecord())
    val productentry = _seed_entry(Product.collectionId, product.toRecord())

    kind match {
      case TestDataKind.SalesOrderData =>
        Vector(orderentry, lineentry)
      case TestDataKind.SalesOrderLineData =>
        Vector(lineentry)
      case TestDataKind.UserData =>
        Vector(userentry)
      case TestDataKind.ProductData =>
        Vector(productentry)
      case TestDataKind.AllData =>
        Vector(orderentry, lineentry, userentry, productentry)
    }
  }

  private def _register_aggregates(
    component: Component
  ): Unit = {
    val aggregatespace = component.aggregateSpace
    val salesordercollection = new AggregateCollection(new SalesOrderBuilder)
    val usercollection = new AggregateCollection(new UserBuilder)
    val productcollection = new AggregateCollection(new ProductBuilder)
    aggregatespace.register("sales_order", salesordercollection)
    aggregatespace.register("user", usercollection)
    aggregatespace.register("product", productcollection)
  }

  private def _seed_entry(
    cid: org.simplemodeling.model.datatype.EntityCollectionId,
    record: org.goldenport.record.Record
  ): DataStoreSpace.SeedEntry =
    DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(cid), record)

  private def _entity_id(
    minor: String,
    cid: org.simplemodeling.model.datatype.EntityCollectionId
  ): org.simplemodeling.model.datatype.EntityId =
    org.simplemodeling.model.datatype.EntityId("test", minor, cid)
}
