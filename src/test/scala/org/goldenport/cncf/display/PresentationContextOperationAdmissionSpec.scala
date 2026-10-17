package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, ActionTask}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class PresentationContextOperationAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PresentationContextRuntimeFixture.*

  "Ordinary Operation presentation admission" should afterWord(
    "in spec:display-presentation-context, example:BA1..BA7, rules:R5,R6,R7, phase:96") {
    "normal authorized preparation routes" which {
      "BA1 authorize then restore then admit before canonical request construction and business" in {
        Given("R5,R7: a real declared Operation and a canonical metadata-bearing Request")
        val fixture = open()
        try {
          val request = withCandidate(fixture.request)
          When("normal Subsystem Request dispatch executes the Operation")
          val result = fixture.subsystem.executeOperationResponseInContext(request, fixture.context)
          Then("restoration precedes declared evaluation request construction and business with stripped domain metadata")
          result shouldBe Consequence.success(OperationResponse.Scalar("ordinary"))
          fixture.evidence.events.toVector shouldBe Vector("authorize", "provider", "evaluation", "request", "business")
          fixture.evidence.requests.head.properties.exists(_.name == "cncf.presentationContext") shouldBe false
          fixture.evidence.evaluations.head.presentationContext should not be empty
          fixture.evidence.business.head.presentationContext should not be empty
          fixture.provider.requests should have size 1
        } finally fixture.close()
      }
      "BA2 preserve the supplied Action and its original canonical metadata Request" in {
        Given("R5,R7: an independently constructed Action with canonical reserved metadata")
        val fixture = open()
        try {
          val request = withCandidate(fixture.request)
          val action = fixture.action(request)
          When("the existing direct Action route executes the supplied Action")
          val result = fixture.subsystem._execute_action_c(action, fixture.context)
          Then("the same Action reaches business after authorization presentation and evaluation without reconstruction")
          result.isSuccess shouldBe true
          fixture.evidence.events.toVector shouldBe Vector("authorize", "provider", "evaluation", "business")
          (fixture.evidence.actions.head eq action) shouldBe true
          (action.request eq request) shouldBe true
          action.request.properties.exists(_.name == "cncf.presentationContext") shouldBe true
          fixture.evidence.business.head.presentationContext should not be empty
        } finally fixture.close()
      }
      "BA3 retain exact accepted resolution and assignment across prepared ActionTask retry" in {
        Given("R5,R7: an ActionTask whose target component has the presentation capability")
        val fixture = open()
        try {
          val ctx = fixture.context
          val action = fixture.action(withCandidate(fixture.request))
          val raw = ActionTask(ActionId.create("presentation", ctx.clock.instant(), ctx.idGeneration),
            action, fixture.component.actionEngine, Some(fixture.component))
          When("normal task preparation admits once and the accepted task executes twice")
          val (task, active) = success(fixture.subsystem._prepare_operation_task(action, raw, ctx))
          val first = task.run(active).result
          val second = task.run(active).result
          Then("retry keeps immutable server flow and assignment with one provider lookup and distinct attempts")
          first.isSuccess shouldBe true
          second.isSuccess shouldBe true
          fixture.evidence.events.toVector shouldBe Vector("authorize", "provider", "evaluation", "business", "business")
          fixture.provider.requests should have size 1
          fixture.evidence.evaluations should have size 1
          fixture.evidence.business.foreach { context =>
            (context.presentationContext.get eq active.presentationContext.get) shouldBe true
            context.operationEvaluation.invocation.get.assignment shouldBe active.operationEvaluation.invocation.get.assignment
            context.operationEvaluation.invocation.get.executionId shouldBe active.operationEvaluation.invocation.get.executionId
          }
          fixture.evidence.business.map(_.operationEvaluation.correlation.get.attemptId).distinct should have size 2
        } finally fixture.close()
      }
    }
    "admission isolation and failure ordering" which {
      "BA4 deny all three routes before decoding provider evaluation or business" in {
        Given("R5,R7: denied canonical Action and Task calls carrying malformed context")
        val fixture = open(denied = true)
        try {
          val request = withCandidate(fixture.request, "SECRET malformed")
          val action = fixture.action(request)
          val ctx = fixture.context
          val raw = ActionTask(ActionId.create("denied", ctx.clock.instant(), ctx.idGeneration),
            action, fixture.component.actionEngine, Some(fixture.component))
          When("each existing route reaches Operation authorization")
          val results = Vector(fixture.subsystem.executeOperationResponseInContext(request, ctx),
            fixture.subsystem._execute_action_c(action, ctx), fixture.subsystem._prepare_operation_task(action, raw, ctx))
          Then("only authorization is observable and no presentation or evaluation or business work occurs")
          results.foreach(_.isSuccess shouldBe false)
          fixture.evidence.events.toVector shouldBe Vector("authorize", "authorize", "authorize")
          fixture.provider.requests shouldBe empty
          fixture.evidence.business shouldBe empty
        } finally fixture.close()
      }
      "BA5 keep required evaluation fail-closed and undeclared operations resolver-free" in {
        Given("R5,R7: valid presentation with required unavailable evaluation and an undeclared control Operation")
        val requiredfixture = open(required = true, evaluationAvailable = false)
        val control = open(declared = false)
        try {
          When("both normal Request routes restore presentation before their existing declaration policy")
          val rejected = requiredfixture.subsystem.executeOperationResponseInContext(withCandidate(requiredfixture.request), requiredfixture.context)
          val ordinary = control.subsystem.executeOperationResponseInContext(withCandidate(control.request), control.context)
          Then("presentation cannot satisfy required evaluation and an undeclared Operation never invokes its evaluation resolver")
          rejected.isSuccess shouldBe false
          requiredfixture.evidence.events.toVector shouldBe Vector("authorize", "provider", "evaluation")
          requiredfixture.evidence.business shouldBe empty
          ordinary.isSuccess shouldBe true
          control.evidence.events.toVector shouldBe Vector("authorize", "provider", "request", "business")
        } finally { requiredfixture.close(); control.close() }
      }
      "BA6 clear inherited presentation in nested and cross-component new Operations while creating independent executions" in {
        Given("R5,R7: an accepted parent Action and ordinary nested same-component and cross-component calls without candidates")
        val fixture = open()
        val other = open(componentName = "org.goldenport.cncf.test.OtherPresentationRuntime")
        try {
          fixture.evidence.nested = Some { parent =>
            success(fixture.subsystem.executeOperationResponseInContext(fixture.request, parent))
            success(other.subsystem.executeOperationResponseInContext(other.request, parent))
            ()
          }
          When("the parent Operation invokes both children through normal Subsystem routing")
          val result = fixture.subsystem.executeOperationResponseInContext(withCandidate(fixture.request), fixture.context)
          Then("only the explicit parent restores a flow and each child has an independent execution and attempt")
          result.isSuccess shouldBe true
          fixture.provider.requests should have size 1
          other.provider.requests shouldBe empty
          fixture.evidence.business.head.presentationContext should not be empty
          fixture.evidence.business(1).presentationContext shouldBe None
          other.evidence.business.head.presentationContext shouldBe None
          val contexts = fixture.evidence.business.toVector ++ other.evidence.business.toVector
          contexts.map(_.operationEvaluation.invocation.get.executionId).distinct should have size 3
          contexts.map(_.operationEvaluation.correlation.get.attemptId).distinct should have size 3
        } finally { fixture.close(); other.close() }
      }
      "BA7 stop provider defects before evaluation request construction and business on every route" in {
        Given("R6,R7: an installed presentation provider returning an invalid null result")
        val fixture = open()
        try {
          fixture.provider.answer = (_, _) => null
          val request = withCandidate(fixture.request)
          val ctx = fixture.context
          val action = fixture.action(request)
          val raw = ActionTask(ActionId.create("invalid", ctx.clock.instant(), ctx.idGeneration),
            action, fixture.component.actionEngine, Some(fixture.component))
          When("canonical Action and Task preparation encounter the invalid provider")
          val results = Vector(fixture.subsystem.executeOperationResponseInContext(request, ctx),
            fixture.subsystem._execute_action_c(action, ctx), fixture.subsystem._prepare_operation_task(action, raw, ctx))
          Then("every route fails before its evaluation and business boundary")
          results.foreach(_.isSuccess shouldBe false)
          fixture.evidence.events.toVector shouldBe Vector.fill(3)(Vector("authorize", "provider")).flatten
          fixture.evidence.evaluations shouldBe empty
          fixture.evidence.requests shouldBe empty
          fixture.evidence.business shouldBe empty
        } finally fixture.close()
      }
    }
  }
}
