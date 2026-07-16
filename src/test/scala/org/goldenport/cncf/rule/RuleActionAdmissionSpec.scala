package org.goldenport.cncf.rule

import java.util.concurrent.atomic.AtomicBoolean
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.event.{DomainEvent, EventDispatchHandler, EventSubscription}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.projection.RuleProjection
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.BeforeAndAfterEach
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for explicit production-rule firing. Runtime work
 * must be admitted through the existing component, event, and job boundaries.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleActionAdmissionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with BeforeAndAfterEach {
  private var _subsystem = Option.empty[Subsystem]

  override protected def afterEach(): Unit =
    try
      _subsystem.foreach(_.shutdown())
    finally {
      _subsystem = None
      super.afterEach()
    }

  "RuleActionAdmission" should {
    "admit operation, Event, Job, and recommendation plans through runtime boundaries" in {
      Given("a subsystem component with an executable operation and an Event subscription")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager),
        enabled = true
      )
      val operationexecuted = AtomicBoolean(false)
      val eventpublished = AtomicBoolean(false)
      val subsystem = TestComponentFactory.emptySubsystem("rule-admission")
      _subsystem = Some(subsystem)
      val component = TestComponentFactory.create(
        "orders",
        _protocol(operationexecuted),
        subsystem = subsystem
      )
      subsystem.add(component)
      subsystem.eventBus.register(EventSubscription(
        name = "rule-alert",
        eventName = Some("order.review-needed"),
        handler = new EventDispatchHandler {
          def dispatch(event: DomainEvent): Consequence[Unit] = {
            val _ = event
            eventpublished.set(true)
            Consequence.unit
          }
        }
      ))
      val plans = Vector[RuleActionPlan](
        RuleActionPlan.Operation(
          RuleActionPlanId("operation"),
          RuleId("notify"),
          "orders.order.approve",
          Record.data("orderId" -> "order-1")
        ),
        RuleActionPlan.Event(
          RuleActionPlanId("event"),
          RuleId("notify"),
          "order.review-needed",
          payload = Record.data("orderId" -> "order-1")
        ),
        RuleActionPlan.Job(
          RuleActionPlanId("job"),
          RuleId("notify"),
          "orders.order.approve",
          Record.data("orderId" -> "order-2")
        ),
        RuleActionPlan.Recommendation(
          RuleActionPlanId("recommendation"),
          RuleId("notify"),
          "Review approval outcome"
        )
      )
      val evaluation = RuleEvaluationResult(
        RuleSetIdentity(RuleSetId("orders"), RuleSetVersion("1")),
        WorkingMemory.empty,
        actionPlans = plans
      )
      val before = RuntimeDashboardMetrics.ruleExecutionSnapshot.summary.cumulative.total

      When("the caller explicitly fires the evaluated plans")
      val result = _success(new RuleActionAdmission(subsystem).fireC(evaluation))
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")
      val projection = RuleProjection.projectFiring(result)

      Then("the operation uses ActionCall execution, the Event is authorized and published, and the Job is tracked")
      operationexecuted.get shouldBe true
      eventpublished.get shouldBe true
      result.outcomes.map(_.plan.id.value) shouldBe Vector("operation", "event", "job", "recommendation")
      val jobid = result.outcomes.collectFirst { case RuleActionOutcome.JobSubmitted(_, id) => id }
        .getOrElse(fail("expected Job outcome"))
      _await(() => subsystem.jobEngine.getResult(jobid).nonEmpty) shouldBe true
      result.outcomes.exists(_.isInstanceOf[RuleActionOutcome.RecommendationRecorded]) shouldBe true
      calltree should include ("rule:fire")
      calltree should include ("job_ids")
      calltree should not include "order-1"
      projection.toString should include (jobid.value)
      projection.toString should not include "approved"
      projection.toString should not include "order-1"
      RuntimeDashboardMetrics.ruleExecutionSnapshot.summary.cumulative.total should be > before
    }

    "record a structured diagnostic when Rule firing cannot resolve an admitted action" in {
      Given("a Rule evaluation whose operation plan has no resolvable target")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(
        ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager),
        enabled = true
      )
      val subsystem = TestComponentFactory.emptySubsystem("rule-admission-failure")
      _subsystem = Some(subsystem)
      val evaluation = RuleEvaluationResult(
        RuleSetIdentity(RuleSetId("orders"), RuleSetVersion("1")),
        WorkingMemory.empty,
        actionPlans = Vector(
          RuleActionPlan.Operation(
            RuleActionPlanId("missing"),
            RuleId("notify"),
            "orders.order.missing",
            Record.data("orderId" -> "confidential-order")
          )
        )
      )
      val before = RuntimeDashboardMetrics.ruleExecutionSnapshot.summary.cumulative.errors

      When("the caller fires the unresolved plan")
      val result = new RuleActionAdmission(subsystem).fireC(evaluation)
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")

      Then("the structured failure is counted without logging plan parameters")
      result shouldBe a[Consequence.Failure[_]]
      calltree should include ("rule:fire")
      calltree should include ("outcome=failure")
      calltree should include ("diagnostic_key")
      calltree should not include "confidential-order"
      RuntimeDashboardMetrics.ruleExecutionSnapshot.summary.cumulative.errors should be > before
    }
  }

  private def _protocol(executed: AtomicBoolean): Protocol =
    Protocol(
      services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
        name = "order",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.one(_operation(executed)))
      )))
    )

  private def _operation(executed: AtomicBoolean): spec.OperationDefinition =
    new spec.OperationDefinition {
      val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
          name = "approve",
          request = spec.RequestDefinition(),
          response = spec.ResponseDefinition.void
        )

      def createOperationRequest(request: Request): Consequence[OperationRequest] =
        Consequence.success(_AdmissionAction(request, executed))
    }

  private def _await(condition: () => Boolean): Boolean = {
    var count = 0
    while (count < 100 && !condition()) {
      Thread.sleep(10L)
      count = count + 1
    }
    condition()
  }

  private def _success[A](value: Consequence[A]): A =
    value.toOption.getOrElse(fail(s"expected success: $value"))
}

private final case class _AdmissionAction(
  request: Request,
  executed: AtomicBoolean
) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    _AdmissionActionCall(core, executed)
}

private final case class _AdmissionActionCall(
  core: ActionCall.Core,
  executed: AtomicBoolean
) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] = {
    executed.set(true)
    Consequence.success(OperationResponse.Scalar("approved"))
  }
}
