package org.goldenport.cncf.observability

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.Conclusion
import org.goldenport.id.UniversalId
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext, ScopeKind, TraceId}

/*
 * @since   Jan.  8, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
class ObservabilityEngineSpec extends AnyWordSpec with Matchers {
  private def _trace_id_(): TraceId =
    TraceId(new UniversalId("cncf", "test", "trace") {})

  private def _scope_context_(): ScopeContext =
    ScopeContext(
      kind = ScopeKind.Action,
      name = "ping",
      parent = None,
      observabilityContext = ObservabilityContext(
        traceId = _trace_id_(),
        spanId = None,
        correlationId = None
      )
    )

  "ObservabilityEngine.build" should {
    "include required keys for success outcome" in {
      val record = ObservabilityEngine.build(
        scope = _scope_context_(),
        http = None,
        operation = Some(OperationContext("admin.system.ping")),
        outcome = Right(())
      )
      val keys = record.asMap.keySet
      keys.contains("scope.subsystem") shouldBe true
      keys.contains("scope.ingress") shouldBe true
      keys.contains("operation.fqn") shouldBe true
      keys.contains("result.success") shouldBe true
    }

    "include required keys for failure outcome" in {
      val record = ObservabilityEngine.build(
        scope = _scope_context_(),
        http = None,
        operation = Some(OperationContext("admin.system.ping")),
        outcome = Left(Conclusion.from(new RuntimeException("x")))
      )
      val keys = record.asMap.keySet
      keys.contains("result.success") shouldBe true
      keys.contains("error.kind") shouldBe true
      keys.contains("error.code") shouldBe true
    }
  }
}
