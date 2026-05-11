package org.goldenport.cncf.component.builtin.metrics

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 11, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class MetricsComponentSpec extends AnyWordSpec with Matchers {
  "MetricsComponent" should {
    "expose entity access, runtime metrics, and metrics catalog queries" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val metrics = subsystem.findComponent("metrics").getOrElse(fail("missing metrics component"))
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

      val entityAccess = _execute(metrics, _request(subsystem.resolver, "metrics.metrics.load_entity_access_metrics"))
      val runtime = _execute(metrics, _request(subsystem.resolver, "metrics.metrics.load_runtime_metrics"))
      val catalog = _execute(metrics, _request(subsystem.resolver, "metrics.metrics.load_metrics_catalog"))

      entityAccess.show should include ("entity.search")
      runtime.show should include ("web.request")
      runtime.show should include ("diagnostic-payload.externalization")
      runtime.show should include ("entity-access")
      runtime.show should include ("payload_kind=result")
      catalog.show should include ("web.request")
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
