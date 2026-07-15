package org.goldenport.cncf.workflow

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.event.ReceptionDomainEvent
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowExecutionDeterminismSpec
  extends AnyPropSpec
  with Matchers
  with GivenWhenThen {

  property("Workflow instance IDs replay for equivalent capabilities without colliding within one invocation") {
    Given("equivalent deterministic clocks and ID-generation capabilities")
    When("two workflow instance IDs are generated in each invocation")
    Then("the sequences must replay while each invocation retains unique IDs")
    val replayproperty = Prop.forAll(Gen.chooseNum(0L, 315576000L)) { epochsecond =>
      val instant = Instant.ofEpochSecond(epochsecond)
      val left = _execution_context(instant, "workflow-id-seed")
      val right = _execution_context(instant, "workflow-id-seed")

      val leftids = Vector(
        WorkflowInstanceId.create("instance", instant)(using left),
        WorkflowInstanceId.create("instance", instant)(using left)
      )
      val rightids = Vector(
        WorkflowInstanceId.create("instance", instant)(using right),
        WorkflowInstanceId.create("instance", instant)(using right)
      )

      leftids == rightids &&
        leftids.distinct.size == 2 &&
        leftids.forall(_.timestamp.contains(instant))
    }
    val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), replayproperty)
    checked.passed shouldBe true
  }

  property("Workflow lifecycle records use the caller clock and ID namespace") {
    Given("a workflow engine and a caller with fixed execution capabilities")
    val instant = Instant.parse("2026-07-16T01:23:45Z")
    TestComponentFactory.withEmptySubsystem("workflow-execution-determinism") { subsystem =>
      val component = _component(subsystem)
      val context = _execution_context(instant, "workflow-lifecycle-seed")

      When("different entities enter the workflow through the same invocation context")
      Vector("order-1", "order-2").foreach { entityid =>
        val result = subsystem.workflowEngine.handle(
          component.name,
          ReceptionDomainEvent(
            name = "sales-order.received",
            kind = "domain-event",
            payload = Map.empty,
            attributes = Map("entity" -> "salesOrder", "orderId" -> entityid),
            occurredAt = Instant.EPOCH
          )
        )(using context)
        result.toOption.map(_.reason) shouldBe Some(Some("entity-unresolved"))
      }

      Then("instance identity and every lifecycle timestamp come from those capabilities")
      val instances = subsystem.workflowEngine.instances
      instances should have size 2
      instances.map(_.id).distinct should have size 2
      instances.foreach(_.id.major shouldBe "workflow")
      instances.foreach(_.id.minor shouldBe "determinism")
      instances.foreach(_.id.timestamp shouldBe Some(instant))
      instances.foreach(_.startedAt shouldBe instant)
      instances.foreach(_.updatedAt shouldBe instant)
      instances.flatMap(_.history).foreach(_.occurredAt shouldBe instant)
    }
  }

  private def _component(
    subsystem: org.goldenport.cncf.subsystem.Subsystem
  ): Component = {
    val definition = WorkflowDefinition(
      name = "sales-order-workflow",
      registrations = Vector(
        WorkflowRegistration(
          name = "received",
          eventName = "sales-order.received",
          entityCollection = "salesOrder",
          entityIdKey = "orderId",
          statusField = "status",
          statusRules = Vector(WorkflowStatusRule("received", "workflow.advance"))
        )
      )
    )
    val component = new Component() {
      override def workflowDefinitions: Vector[WorkflowDefinition] = Vector(definition)
    }
    val name = "workflow_determinism_component"
    val componentid = ComponentId(name)
    val instanceid = ComponentInstanceId.default(componentid)
    val core = Component.Core.create(name, componentid, instanceid, Protocol.empty)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
    val bootstrapped = new ComponentFactory().bootstrap(component)
    subsystem.add(bootstrapped)
    bootstrapped
  }

  private def _execution_context(
    instant: Instant,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("workflow", "determinism"),
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }
}
