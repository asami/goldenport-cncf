package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminExecutionDiagnosticsSpec
  extends AnyWordSpec
  with Matchers {

  "AdminComponent" should {
    "execute admin.execution.diagnostics and expose authoritative event/job entry points" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val adminComponent = _admin_component(subsystem)
      val request = _build_request(subsystem.resolver, "admin.execution.diagnostics")

      _execute(adminComponent, request) match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("kind") shouldBe Some("phase-13-execution-diagnostics")
          record.getString("summary").exists(_.contains("queued dispatch contract")) shouldBe true
          val routes = record.getAny("routes").collect { case xs: Seq[?] => xs }.getOrElse(fail("routes missing"))
          routes.map(_.asInstanceOf[org.goldenport.record.Record].getString("selector").getOrElse("")) should contain allOf(
            "event.event.search_event",
            "event.event.load_event",
            "event.event_admin.load_job_events",
            "job_control.job.get_job_status",
            "job_control.job.load_job_history",
            "job_control.job.get_job_result",
            "job_control.job.await_job_result",
            "job_control.job_admin.load_job_events"
          )
          record.getAny("event-fields").collect { case xs: Seq[?] => xs.map(_.toString) }.getOrElse(fail("event-fields missing")) should contain allOf(
            "reception-rule",
            "reception-policy",
            "policy-source",
            "failure-policy",
            "failure-disposition-base",
            "dispatch-kind",
            "dispatch-status",
            "source-subsystem",
            "source-component",
            "target-subsystem",
            "target-component"
          )
          record.getAny("job-fields").collect { case xs: Seq[?] => xs.map(_.toString) }.getOrElse(fail("job-fields missing")) should contain allOf(
            "reception-rule",
            "reception-policy",
            "policy-source",
            "job-relation",
            "saga-relation",
            "failure-policy",
            "failure-disposition",
            "source-subsystem",
            "source-component",
            "target-subsystem",
            "target-component"
          )
        case other =>
          fail(s"expected diagnostics record but got $other")
      }
    }

    "register execution.diagnostics in admin protocol for help/introspection discovery" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val adminComponent = _admin_component(subsystem)
      val operation = adminComponent.protocol.services.services
        .find(_.name == "execution")
        .flatMap(_.operations.operations.toVector.find(_.name == "diagnostics"))
        .getOrElse(fail("execution.diagnostics not registered"))

      operation.name shouldBe "diagnostics"
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
