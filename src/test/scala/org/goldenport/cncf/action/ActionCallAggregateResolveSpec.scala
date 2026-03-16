package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateSpaceSpecHelper, ProductBuilder, SalesOrderBuilder, UserBuilder}
import org.goldenport.cncf.entity.runtime.testdomain.{ProductAggregate, SalesOrder, SalesOrderAggregate, SalesOrderLine, UserAggregate}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallAggregateResolveSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with ActionCallHelper
  with AggregateSpaceSpecHelper {

  "ActionCall aggregate" should {
    "resolve sales order aggregate as typed value" in {
      Given("a component with aggregate collections registered")
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val salesorderid = sales_order_id()
      val expected = expected_sales_order_aggregate(salesorderid)

      When("executing an action call that resolves a typed aggregate")
      val call = action_call(
        actionname = "resolve-sales-order-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[SalesOrderAggregate](core, salesorderid)
      }
      val result = call.execute()

      Then("the call completes with record response")
      result shouldBe a[Consequence.Success[OperationResponse]]
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.print.nonEmpty shouldBe true
        case other =>
          fail(s"unexpected result: $other")
      }

      And("the aggregate is resolved through aggregate space as typed value")
      call.asInstanceOf[ResolveAggregateCall[SalesOrderAggregate]].resolved shouldBe Some(expected)
    }

    "resolve user aggregate as typed value" in {
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val userid = user_id()
      val expected = expected_user_aggregate(userid)

      val call = action_call(
        actionname = "resolve-user-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[UserAggregate](core, userid)
      }
      val result = call.execute()

      result shouldBe a[Consequence.Success[OperationResponse]]
      call.asInstanceOf[ResolveAggregateCall[UserAggregate]].resolved shouldBe Some(expected)
    }

    "resolve product aggregate as typed value" in {
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPair(component)

      val productid = product_id()
      val expected = expected_product_aggregate(productid)

      val call = action_call(
        actionname = "resolve-product-aggregate",
        pair
      ) { core =>
        ResolveAggregateCall[ProductAggregate](core, productid)
      }
      val result = call.execute()

      result shouldBe a[Consequence.Success[OperationResponse]]
      call.asInstanceOf[ResolveAggregateCall[ProductAggregate]].resolved shouldBe Some(expected)
    }

    "resolve and apply diff update to sales order aggregate" in {
      Given("a component with aggregate collections registered")
      val component = new Component() {}
      component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
      component.aggregateSpace.register("user", new AggregateCollection(new UserBuilder))
      component.aggregateSpace.register("product", new AggregateCollection(new ProductBuilder))
      val pair = ActionCallSupport.componentPairWithTestData(
        component,
        ActionCallSupport.TestDataKind.AllData
      )
      val salesorderid = sales_order_id()
      val patch = SalesOrderAggregatePatch(
        sku = Some("sku-updated"),
        quantityDelta = Some(2)
      )

      When("executing an action call that resolves and applies a diff update")
      val call = action_call(
        actionname = "resolve-and-diff-update-sales-order",
        pair
      ) { core =>
        ResolveAndDiffUpdateAggregateCall(core, salesorderid, patch)
      }
      val result = call.execute()

      Then("the call returns RecordResponse with update result")
      result shouldBe a[Consequence.Success[OperationResponse]]
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.print.contains("after") shouldBe true
        case other =>
          fail(s"unexpected result: $other")
      }

      And("the aggregate is loaded and updated as typed value")
      val typed = call.asInstanceOf[ResolveAndDiffUpdateAggregateCall]
      typed.before.map(_.line.quantity) shouldBe Some(1)
      typed.updated.map(_.line.quantity) shouldBe Some(3)
      typed.updated.map(_.line.sku) shouldBe Some("sku-updated")
      typed.storeUpdated shouldBe true
    }
  }

}

private final case class ResolveAggregateCall[A](
  core: ActionCall.Core,
  targetid: EntityId
) extends ProcedureActionCall {
  private var _resolved: Option[A] = None

  def resolved: Option[A] = _resolved

  override def execute(): Consequence[OperationResponse] = {
    aggregate_load[A](targetid)
      .map { value =>
      _resolved = Some(value)
      OperationResponse.RecordResponse(Record.dataAuto("aggregate" -> value))
    }
  }
}

private final case class SalesOrderAggregatePatch(
  sku: Option[String] = None,
  quantityDelta: Option[Int] = None
)

private final case class ResolveAndDiffUpdateAggregateCall(
  core: ActionCall.Core,
  targetid: EntityId,
  patch: SalesOrderAggregatePatch
) extends FunctionalActionCall {
  private var _before: Option[SalesOrderAggregate] = None
  private var _updated: Option[SalesOrderAggregate] = None
  private var _storeupdated: Boolean = false

  def before: Option[SalesOrderAggregate] = _before
  def updated: Option[SalesOrderAggregate] = _updated
  def storeUpdated: Boolean = _storeupdated

  protected def build_Program =
    exec_from {
      aggregate_load[SalesOrderAggregate](targetid).flatMap { current =>
        val updatedLine = current.line.copy(
          sku = patch.sku.getOrElse(current.line.sku),
          quantity = current.line.quantity + patch.quantityDelta.getOrElse(0)
        )
        val updated = current.copy(
          order = current.order.copy(line = updatedLine),
          line = updatedLine
        )
        _before = Some(current)
        _updated = Some(updated)

        store_update(updated).map { _ =>
          OperationResponse.RecordResponse(
            Record.dataAuto(
              "before" -> current,
              "patch" -> patch,
              "after" -> updated
            )
          )
        }
      }
    }

  private def store_update(
    updated: SalesOrderAggregate
  ): Consequence[Unit] = {
    val _ = store_update(updated.order.id, updated.order.toRecord())
    val _ = store_update(updated.line.id, updated.line.toRecord())
    _storeupdated = true
    Consequence.unit
  }
}
