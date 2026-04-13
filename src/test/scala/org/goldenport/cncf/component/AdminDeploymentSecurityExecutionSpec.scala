package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 *  version Apr.  9, 2026
 *  version Apr. 11, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminDeploymentSecurityExecutionSpec
  extends AnyWordSpec
  with Matchers {

  "AdminComponent" should {
    "execute admin.deployment.securityMermaid requests" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val adminComponent = _admin_component(subsystem)
      val request = _build_request(subsystem.resolver, "admin.deployment.securityMermaid")

      _execute(adminComponent, request) match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("flowchart LR")
          text should include ("ExecutionContext(SecurityContext)")
          text should include ("ActionCall")
          text should include ("UnitOfWork")
        case other =>
          fail(s"expected mermaid scalar but got $other")
      }
    }

    "execute admin.deployment.securityMarkdown requests" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val adminComponent = _admin_component(subsystem)
      val request = _build_request(subsystem.resolver, "admin.deployment.securityMarkdown")

      _execute(adminComponent, request) match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include ("# Security Deployment Specification")
          text should include ("## Diagram")
          text should include ("## Authentication Providers")
          text should include ("## Framework Chokepoints")
        case other =>
          fail(s"expected markdown scalar but got $other")
      }
    }

    "execute variation list and describe requests" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val adminComponent = _admin_component(subsystem)
      val listRequest = _build_request(subsystem.resolver, "admin.variation.list")
      val describeRequest = _build_request(subsystem.resolver, "admin.variation.describe").copy(
        arguments = List(Argument("key", RuntimeConfig.ExecutionHistoryRecentLimitKey))
      )

      _execute(adminComponent, listRequest) match {
        case Consequence.Success(OperationResponse.Scalar(text: String)) =>
          text should include (s"key  : ${RuntimeConfig.ExecutionHistoryRecentLimitKey}")
          text should include ("brief: Recent execution history size.")
          text should not include ("detail: Number of most recent action execution records")
        case other =>
          fail(s"expected variation list scalar but got $other")
      }

      _execute(adminComponent, describeRequest) match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("key") shouldBe Some(RuntimeConfig.ExecutionHistoryRecentLimitKey)
          record.getString("brief") shouldBe Some("Recent execution history size.")
          record.getString("detail").exists(_.contains("Number of most recent action execution records")) shouldBe true
        case other =>
          fail(s"expected variation describe record but got $other")
      }
    }
  }

  private def _admin_component(subsystem: org.goldenport.cncf.subsystem.Subsystem): Component =
    subsystem.components
      .collectFirst { case comp if comp.name == "admin" => comp }
      .getOrElse(fail("admin component not found"))

  private def _execute(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = component.logic.createActionCall(action)
        component.logic.execute(call)
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }

  private def _build_request(
    resolver: OperationResolver,
    selector: String
  ): Request =
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(
          component = component,
          service = service,
          operation = operation
        )
      case other =>
        fail(s"resolver failed for $selector: $other")
    }
}
