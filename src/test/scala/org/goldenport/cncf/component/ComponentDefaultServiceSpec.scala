package org.goldenport.cncf.component

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/*
 * @since   Mar.  4, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentDefaultServiceSpec extends AnyWordSpec with Matchers {
  "Component default services" should {
    "inject meta and system operations when protocol has none" in {
      val component = TestComponentFactory.create("default_service_target", Protocol.empty)
      val operations = _operations(component)

      operations.contains("meta.help") shouldBe true
      operations.contains("meta.describe") shouldBe true
      operations.contains("meta.components") shouldBe true
      operations.contains("meta.services") shouldBe true
      operations.contains("meta.operations") shouldBe true
      operations.contains("meta.schema") shouldBe true
      operations.contains("meta.openapi") shouldBe true
      operations.contains("meta.mcp") shouldBe true
      operations.contains("meta.tree") shouldBe true
      operations.contains("meta.statemachine") shouldBe true
      operations.contains("meta.version") shouldBe true
      operations.contains("system.ping") shouldBe true
      operations.contains("system.health") shouldBe true
    }

    "not override user-defined operations and only add missing defaults" in {
      val customPing = _CustomPingOperation()
      val serviceSystem = spec.ServiceDefinition(
        name = "system",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(customPing)
        )
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(serviceSystem)))
      val component = TestComponentFactory.create("custom_ping", protocol)

      val operations = _operations(component)
      operations.contains("system.ping") shouldBe true
      operations.contains("system.health") shouldBe true
      operations.contains("meta.help") shouldBe true
      operations.contains("meta.describe") shouldBe true
      operations.contains("meta.components") shouldBe true
      operations.contains("meta.services") shouldBe true
      operations.contains("meta.operations") shouldBe true
      operations.contains("meta.schema") shouldBe true
      operations.contains("meta.openapi") shouldBe true
      operations.contains("meta.mcp") shouldBe true
      operations.contains("meta.tree") shouldBe true
      operations.contains("meta.statemachine") shouldBe true
      operations.contains("meta.version") shouldBe true

      _execute(component, Request.of(component = "custom_ping", service = "system", operation = "ping")) match {
        case Consequence.Success(OperationResponse.Scalar(value)) =>
          value shouldBe "custom-ping-ok"
        case other =>
          fail(s"expected custom scalar ping response but got: ${other.show}")
      }
    }
  }

  private def _operations(component: Component): Set[String] =
    component.protocol.services.services.flatMap { service =>
      service.operations.operations.toVector.map(op => s"${service.name}.${op.name}")
    }.toSet

  private def _execute(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.failure(s"unexpected operation request type: ${other.getClass.getName}")
    }
}

private final case class _CustomPingOperation() extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "ping",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition()
    )

  override def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_CustomPingAction(req))
}

private final case class _CustomPingAction(
  request: Request
) extends QueryAction {
  override def createCall(core: ActionCall.Core): ActionCall =
    _CustomPingActionCall(core)
}

private final case class _CustomPingActionCall(
  core: ActionCall.Core
) extends ProcedureActionCall {
  override def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar("custom-ping-ok"))
}
