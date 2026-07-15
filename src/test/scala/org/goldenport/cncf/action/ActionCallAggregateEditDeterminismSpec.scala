package org.goldenport.cncf.action

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateEditContext, AggregateSpaceSpecHelper, SalesOrderBuilder}
import org.goldenport.cncf.entity.runtime.testdomain.SalesOrderAggregate
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallAggregateEditDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with AggregateSpaceSpecHelper {

  "ActionCall aggregate-edit DSL" should {
    "bind edit-context lifecycle time to the caller execution clock" in {
      Given("generated fixed caller clocks and a component aggregate collection")
      val property = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
        val instant = Instant.ofEpochSecond(epochsecond)
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val executioncontext = ExecutionContext.create(clock)
        val component = new Component() {}
        component.aggregateSpace.register("sales_order", new AggregateCollection(new SalesOrderBuilder))
        val id = sales_order_id()
        val pair = ActionCallSupport.componentPair(component, executioncontext)
        val call = ActionCallSupport.actionCall("aggregate-edit-clock", pair) { core =>
          AggregateEditClockProbeCall(core, id)
        }.asInstanceOf[AggregateEditClockProbeCall]

        val result = call.execute()

        result.isInstanceOf[Consequence.Success[?]] &&
          call.context.exists(context =>
            context.createdAt == instant && context.updatedAt == instant
          )
      }

      When("the aggregate edit is started through the ActionCall internal DSL")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("the context timestamps equal the bound execution-capability instant")
      checked.passed shouldBe true
    }
  }
}

private final case class AggregateEditClockProbeCall(
  core: ActionCall.Core,
  id: EntityId
) extends ProcedureActionCall {
  private var _context: Option[AggregateEditContext[SalesOrderAggregate]] = None

  def context: Option[AggregateEditContext[SalesOrderAggregate]] =
    _context

  override def execute(): Consequence[OperationResponse] =
    begin_aggregate_edit_c[SalesOrderAggregate]("sales_order", id, "v1").map { context =>
      _context = Some(context)
      OperationResponse.void
    }
}
