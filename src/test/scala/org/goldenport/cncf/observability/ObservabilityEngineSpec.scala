package org.goldenport.cncf.observability

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.Conclusion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Jan.  8, 2026
 *  version Jan. 20, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class ObservabilityEngineSpec extends AnyWordSpec with Matchers {
  private def _trace_id_(): TraceId =
    TraceId("cncf", "test")

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

  "execution history configuration" should {
    "load retention limits and operation filters from RuntimeConfig" in {
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.ExecutionHistoryRecentLimitKey -> ConfigurationValue.NumberValue(BigDecimal(7)),
            RuntimeConfig.ExecutionHistoryFilteredLimitKey -> ConfigurationValue.NumberValue(BigDecimal(70)),
            RuntimeConfig.ExecutionHistoryFilterOperationContainsKey -> ConfigurationValue.StringValue("foo,bar")
          )
        ),
        ConfigurationTrace.empty
      )

      val config = RuntimeConfig.from(configuration)

      config.executionHistoryConfig.recentLimit shouldBe 7
      config.executionHistoryConfig.filteredLimit shouldBe 70
      config.executionHistoryConfig.filters.map(_.operationContains) shouldBe Vector(Some("foo"), Some("bar"))
      ObservabilityEngine.executionHistoryConfig shouldBe config.executionHistoryConfig
    }

    "resolve legacy cncf runtime aliases through RuntimeConfig" in {
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            "cncf.runtime.discover.classes" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.component-factory-class" -> ConfigurationValue.StringValue("example.Factory"),
            "cncf.runtime.workspace" -> ConfigurationValue.StringValue("/tmp/workspace"),
            "cncf.runtime.force-exit" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.no-exit" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.logging.backend" -> ConfigurationValue.StringValue("stderr"),
            "cncf.runtime.logging.level" -> ConfigurationValue.StringValue("debug"),
            "cncf.runtime.logging.file.path" -> ConfigurationValue.StringValue("/tmp/cncf.log")
          )
        ),
        ConfigurationTrace.empty
      )

      RuntimeConfig.getString(configuration, RuntimeConfig.DiscoverClassesKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.ComponentFactoryClassKey) shouldBe Some("example.Factory")
      RuntimeConfig.getString(configuration, RuntimeConfig.WorkspaceKey) shouldBe Some("/tmp/workspace")
      RuntimeConfig.getString(configuration, RuntimeConfig.ForceExitKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.NoExitKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogBackendKey) shouldBe Some("stderr")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogLevelKey) shouldBe Some("debug")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogFilePathKey) shouldBe Some("/tmp/cncf.log")
    }
  }
}
