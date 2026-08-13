package org.goldenport.cncf.component.builtin.metrics

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.ComponentMetricDefinition
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 11, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class MetricsComponentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MetricsComponent" should {
    "expose entity access, runtime metrics, and metrics catalog queries" in {
      Given("a command subsystem with recorded entity and runtime metrics")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val metrics = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.METRICS).getOrElse(fail("missing metrics component"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/metrics-spec", 200, 3L)
      RuntimeDashboardMetrics.recordDiagnosticPayloadExternalization("result", "stored", "local-file")
      subsystem.entityAccessMetrics.record(
        "entity.search",
        Record.dataAuto(
          "entity" -> "notice",
          "source" -> "datastore",
          "outcome" -> "success"
        )
      )
      subsystem.componentMetrics.record(
        ComponentMetricDefinition("semantic.provider.request", Vector("component", "provider", "outcome")),
        Map("component" -> "textus-sie", "provider" -> "fuseki", "outcome" -> "success"),
        durationmillis = Some(4L)
      )

      When("the canonical metrics queries execute")
      val entityaccess = _execute(
        metrics,
        _request(subsystem.resolver, s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.METRICS.name}.metrics.load_entity_access_metrics")
      )
      val runtime = _execute(
        metrics,
        _request(subsystem.resolver, s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.METRICS.name}.metrics.load_runtime_metrics")
      )
      val catalog = _execute(
        metrics,
        _request(subsystem.resolver, s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.METRICS.name}.metrics.load_metrics_catalog")
      )

      Then("the metric responses expose entity, runtime, and catalog data")
      entityaccess.show should include ("entity.search")
      runtime.show should include ("web.request")
      runtime.show should include ("diagnostic-payload.externalization")
      runtime.show should include ("otel.export")
      runtime.show should include ("entity-access")
      runtime.show should include ("semantic.provider.request")
      runtime.show should include ("payload_kind=result")
      runtime.show should include ("otel_export")
      catalog.show should include ("web.request")
      catalog.show should include ("otel.export")
      catalog.show should include ("component")
      catalog.show should include ("label_keys")
    }
  }

  private def _execute(
    component: Component,
    request: Request
  ): Record =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        component.logic.execute(component.logic.createActionCall(action))
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    } match {
      case Consequence.Success(OperationResponse.RecordResponse(record)) =>
        record
      case other =>
        fail(s"expected record response but got $other")
    }

  private def _request(
    resolver: OperationResolver,
    selector: String
  ): Request =
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(component = component, service = service, operation = operation)
      case other =>
        fail(s"resolver failed for $selector: $other")
    }
}
