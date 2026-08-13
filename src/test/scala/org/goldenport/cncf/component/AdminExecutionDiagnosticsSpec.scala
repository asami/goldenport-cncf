package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 22, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminExecutionDiagnosticsSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AdminComponent" should {
    "execute admin.execution.diagnostics and expose authoritative event/job entry points" in {
      Given("a command subsystem with an Admin diagnostics request")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val admincomponent = _admin_component(subsystem)
      val request = _build_request(
        subsystem.resolver,
        s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.diagnostics"
      )

      When("the canonical Admin diagnostics request executes")
      val result = _execute(admincomponent, request)

      Then("the diagnostics record exposes authoritative event and job entry points")
      result match {
        case Consequence.Success(OperationResponse.RecordResponse(record)) =>
          record.getString("kind") shouldBe Some("phase-13-execution-diagnostics")
          record.getString("summary").exists(_.contains("queued dispatch contract")) shouldBe true
          val routes = record.getAny("routes").collect { case xs: Seq[?] => xs }.getOrElse(fail("routes missing"))
          routes.map(_.asInstanceOf[org.goldenport.record.Record].getString("selector").getOrElse("")) should contain allOf(
            "org.goldenport.cncf.Workflow.workflow.list_workflow_definitions",
            "org.goldenport.cncf.Workflow.workflow.describe_workflow_definition",
            "org.goldenport.cncf.Workflow.workflow.list_workflow_instances",
            "org.goldenport.cncf.Workflow.workflow.get_workflow_instance",
            "org.goldenport.cncf.Workflow.workflow.load_workflow_history",
            "org.goldenport.cncf.Event.event.search_event",
            "org.goldenport.cncf.Event.event.load_event",
            "org.goldenport.cncf.Event.event_admin.load_job_events",
            "org.goldenport.cncf.JobControl.job.get_job_status",
            "org.goldenport.cncf.JobControl.job.load_job_history",
            "org.goldenport.cncf.JobControl.job.get_job_result",
            "org.goldenport.cncf.JobControl.job.await_job_result",
            "org.goldenport.cncf.JobControl.job_admin.load_job_events"
          )
          record.getAny("event-fields").collect { case xs: Seq[?] => xs.map(_.toString) }.getOrElse(fail("event-fields missing")) should contain allOf(
            "reception-rule",
            "reception-policy",
            "policy-source",
            "saga-id",
            "task-relation",
            "transaction-relation",
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
            "task-relation",
            "transaction-relation",
            "saga-id",
            "saga-relation",
            "failure-policy",
            "failure-disposition",
            "source-subsystem",
            "source-component",
            "target-subsystem",
            "target-component"
          )
          record.getRecord("workflow-surface").flatMap(_.getString("selector")) shouldBe Some("org.goldenport.cncf.Workflow.workflow.list_workflow_instances")
          record.getRecord("job-surface").flatMap(_.getString("selector")) shouldBe Some("org.goldenport.cncf.JobControl.job.get_job_status")
        case other =>
          fail(s"expected diagnostics record but got $other")
      }
    }

    "register execution.diagnostics in admin protocol for help/introspection discovery" in {
      Given("a command subsystem with the Admin component")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val admincomponent = _admin_component(subsystem)

      When("the Admin execution service protocol is inspected")
      val operation = admincomponent.protocol.services.services
        .find(_.name == "execution")
        .flatMap(_.operations.operations.toVector.find(_.name == "diagnostics"))
        .getOrElse(fail("execution.diagnostics not registered"))

      Then("execution.diagnostics is registered")
      operation.name shouldBe "diagnostics"
    }
  }

  private def _admin_component(subsystem: org.goldenport.cncf.subsystem.Subsystem): Component =
    subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
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
