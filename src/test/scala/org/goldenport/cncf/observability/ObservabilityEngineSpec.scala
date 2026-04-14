package org.goldenport.cncf.observability

import scala.collection.mutable.ListBuffer

import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.Conclusion
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.record.Record
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Jan.  8, 2026
 *  version Jan. 20, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ObservabilityEngineSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  override protected def afterEach(): Unit = {
    ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = LogLevel.Info))
    LogBackendHolder.reset()
    super.afterEach()
  }

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

  "visibility policy" should {
    "hide debug events when the minimum level is info" in {
      val backend = new MemoryBackend
      LogBackendHolder.install(backend)
      ObservabilityEngine.updateVisibilityPolicy(VisibilityPolicy(minLevel = LogLevel.Info))

      ObservabilityEngine.emitDebug(
        _scope_context_().observabilityContext,
        _scope_context_(),
        "debug-event",
        Record.empty
      )
      ObservabilityEngine.emitInfo(
        _scope_context_().observabilityContext,
        _scope_context_(),
        "info-event",
        Record.empty
      )

      backend.lines.exists(_.contains("debug-event")) shouldBe false
      backend.lines.exists(_.contains("info-event")) shouldBe true
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
            "cncf.runtime.server-emulator.baseurl" -> ConfigurationValue.StringValue("http://example.com/"),
            "cncf.runtime.http.driver" -> ConfigurationValue.StringValue("fake"),
            "cncf.runtime.mode" -> ConfigurationValue.StringValue("server"),
            "cncf.runtime.discover.classes" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.component-factory-class" -> ConfigurationValue.StringValue("example.Factory"),
            "cncf.runtime.workspace" -> ConfigurationValue.StringValue("/tmp/workspace"),
            "cncf.runtime.force-exit" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.no-exit" -> ConfigurationValue.BooleanValue(true),
            "cncf.runtime.logging.backend" -> ConfigurationValue.StringValue("stderr"),
            "cncf.runtime.logging.level" -> ConfigurationValue.StringValue("debug"),
            "cncf.runtime.logging.file.path" -> ConfigurationValue.StringValue("/tmp/cncf.log"),
            "cncf.runtime.web.operation.dispatcher" -> ConfigurationValue.StringValue("rest"),
            "cncf.runtime.web.operation.dispatcher.rest.base-url" -> ConfigurationValue.StringValue("http://app.example")
          )
        ),
        ConfigurationTrace.empty
      )

      RuntimeConfig.getString(configuration, RuntimeConfig.ServerEmulatorBaseUrlKey) shouldBe Some("http://example.com/")
      RuntimeConfig.getString(configuration, RuntimeConfig.HttpDriverKey) shouldBe Some("fake")
      RuntimeConfig.getString(configuration, RuntimeConfig.ModeKey) shouldBe Some("server")
      RuntimeConfig.getString(configuration, RuntimeConfig.DiscoverClassesKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.ComponentFactoryClassKey) shouldBe Some("example.Factory")
      RuntimeConfig.getString(configuration, RuntimeConfig.WorkspaceKey) shouldBe Some("/tmp/workspace")
      RuntimeConfig.getString(configuration, RuntimeConfig.ForceExitKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.NoExitKey) shouldBe Some("true")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogBackendKey) shouldBe Some("stderr")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogLevelKey) shouldBe Some("debug")
      RuntimeConfig.getString(configuration, RuntimeConfig.LogFilePathKey) shouldBe Some("/tmp/cncf.log")
      RuntimeConfig.getString(configuration, RuntimeConfig.WebOperationDispatcherKey) shouldBe Some("rest")
      RuntimeConfig.getString(configuration, RuntimeConfig.WebOperationDispatcherRestBaseUrlKey) shouldBe Some("http://app.example")
    }
  }

  private final class MemoryBackend extends LogBackend {
    private val _lines = ListBuffer.empty[String]

    def lines: Vector[String] = _lines.synchronized {
      _lines.toVector
    }

    override def writeLine(line: String): Unit = _lines.synchronized {
      _lines += line
    }
  }
}
