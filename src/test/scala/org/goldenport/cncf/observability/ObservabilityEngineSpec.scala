package org.goldenport.cncf.observability

import scala.collection.mutable.ListBuffer

import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.Conclusion
import org.goldenport.cncf.blob.BlobStoreConfig
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import io.circe.Json
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}

/*
 * @since   Jan.  8, 2026
 *  version Jan. 20, 2026
 *  version Apr. 15, 2026
 * @version May. 11, 2026
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
      keys.contains("error.status") shouldBe true
      keys.contains("error.status_text") shouldBe true
      keys.contains("error.detail_code") shouldBe true
      keys.contains("error.taxonomy.category") shouldBe true
      keys.contains("error.taxonomy.symptom") shouldBe true
      keys.contains("error.cause.kind") shouldBe true
      keys.contains("error.interpretation") shouldBe true
      keys.contains("error.diagnostic_key") shouldBe true
    }
  }

  "ObservabilityEngine.callTreeRecord" should {
    "project calltree nodes with stable action fields and flow" in {
      val calltree = CallTreeContext.enabled
      calltree.enter(
        "action:notification.search",
        Map(
          "calltree_kind" -> "action",
          "operation" -> "searchMyNotifications",
          "component" -> "UserNotification",
          "service" -> "Notification"
        )
      )
      calltree.enter("uow:entitystore:search:direct", Map("calltree_kind" -> "uow", "source" -> "entity-store"))
      calltree.enter("io:datastore:search", Map("calltree_kind" -> "io", "datastore" -> "SqlDataStore"))
      calltree.leave(Map("outcome" -> "success", "result" -> """{"kind":"datastore-search-result","record_count":3,"inline":false}"""))
      calltree.mark("metrics:entity.search.start", Map("calltree_kind" -> "metric", "entity" -> "notification", "outcome" -> "start"))
      calltree.mark("metrics:datastore.search", Map("calltree_kind" -> "metric", "datastore" -> "SqlDataStore", "real_io" -> "true"))
      calltree.leave(Map("outcome" -> "success", "result" -> """{"kind":"search-result","record_count":3,"inline":false}"""))
      calltree.leave(Map("response_type" -> "RecordResponse", "outcome" -> "success", "response" -> """{"kind":"record","field_count":2,"inline":false}"""))

      val record = ObservabilityEngine.callTreeRecord(calltree.build().get)
      val nodes = record.asMap("calltree").asInstanceOf[Seq[Record]]
      val root = nodes.head
      val flow = root.asMap("flow").asInstanceOf[Seq[Record]]
      val uow = flow.head
      val uowFlow = uow.asMap("flow").asInstanceOf[Seq[Record]]
      val io = uowFlow.head
      val uowObservations = uow.asMap("observations").asInstanceOf[Seq[Record]]

      root.getString("label") shouldBe Some("action:notification.search")
      root.fields.take(5).map(_.key) shouldBe Vector("label", "kind", "component", "service", "operation")
      root.getString("kind") shouldBe Some("action")
      root.getString("component") shouldBe Some("UserNotification")
      root.getString("service") shouldBe Some("Notification")
      root.getString("operation") shouldBe Some("searchMyNotifications")
      root.getString("highlights") shouldBe Some("real_io")
      root.getString("real_io") shouldBe None
      root.getString("response_type") shouldBe Some("RecordResponse")
      root.asMap("response").asInstanceOf[Record].getString("kind") shouldBe Some("record")
      root.asMap.contains("attributes") shouldBe false
      root.asMap.contains("children") shouldBe false
      flow.map(_.getString("label")) should contain only Some("uow:entitystore:search:direct")
      uow.getString("kind") shouldBe Some("uow")
      uow.getString("highlights") shouldBe Some("real_io")
      uow.getString("real_io") shouldBe None
      uow.asMap("result").asInstanceOf[Record].getString("kind") shouldBe Some("search-result")
      io.getString("kind") shouldBe Some("io")
      io.getString("highlights") shouldBe Some("real_io")
      io.getString("real_io") shouldBe None
      io.asMap("result").asInstanceOf[Record].getString("kind") shouldBe Some("datastore-search-result")
      uowObservations.flatMap(_.getString("label")) should contain allOf ("metrics:entity.search.start", "metrics:datastore.search")
      uowObservations.head.asMap.contains("attributes") shouldBe false
      uowObservations.head.getString("entity") shouldBe Some("notification")
    }

    "preserve JSON payloads even when attributes contain JSON encoded as a string value" in {
      val calltree = CallTreeContext.enabled
      calltree.enter("action:notification.search", Map("calltree_kind" -> "action"))
      calltree.leave(Map(
        "outcome" -> "success",
        "response" -> "\"{\\\"kind\\\":\\\"record\\\",\\\"field_count\\\":9,\\\"size_bytes\\\":160}\""
      ))

      val record = ObservabilityEngine.callTreeRecord(calltree.build().get)
      val nodes = record.asMap("calltree").asInstanceOf[Seq[Record]]
      val response = nodes.head.asMap("response").asInstanceOf[Record]

      response.getString("kind") shouldBe Some("record")
      response.getString("field_count") shouldBe Some("9")
      response.getString("size_bytes") shouldBe Some("160")
    }

    "avoid inlining generic scalar result values in CallTree summaries" in {
      val calltree = CallTreeContext.enabled
      calltree.enter("uow:credential:issue", Map("calltree_kind" -> "uow"))
      calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes("proof-code-123"))

      val record = ObservabilityEngine.callTreeRecord(calltree.build().get)
      val nodes = record.asMap("calltree").asInstanceOf[Seq[Record]]
      val result = nodes.head.asMap("result").asInstanceOf[Record]

      result.getString("kind") shouldBe Some("string")
      result.getString("inline") shouldBe Some("false")
      result.show should not include ("proof-code-123")
    }
  }

  "DiagnosticPayloadSummary" should {
    "inline small safe records and summarize large records" in {
      val small = DiagnosticPayloadSummary.recordSummary(
        Record.data("status" -> "ok", "count" -> 1)
      ).toRecord
      val large = DiagnosticPayloadSummary.recordSummary(
        Record.dataAuto((1 to 30).map(i => s"field$i" -> s"value$i")*),
        includeInline = true
      ).toRecord

      small.getString("kind") shouldBe Some("record")
      small.getString("inline") should not be Some("false")
      large.getString("kind") shouldBe Some("record")
      large.getString("field_count") shouldBe Some("30")
      large.getString("inline") shouldBe Some("false")
    }

    "redact confidentiality metadata before summary generation" in {
      val summary = DiagnosticPayloadSummary.recordSummary(
        Record.data("proof" -> "123456", "status" -> "ok"),
        confidentiality = Map("proof" -> DataConfidentiality.Secret)
      ).toRecord

      summary.show should include ("[redacted]")
      summary.show should include ("status=ok")
      summary.show should not include ("123456")
    }

    "summarize operation response variants deterministically" in {
      val responses = Vector(
        OperationResponse.Void(),
        OperationResponse.RecordResponse(Record.data("status" -> "ok")),
        OperationResponse.Json(Json.obj("password" -> Json.fromString("secret"))),
        OperationResponse.Yaml("password: secret"),
        OperationResponse.Scalar("token=secret-token"),
        OperationResponse.Opaque("opaque-secret")
      )
      val summaries = responses.map(DiagnosticPayloadSummary.operationResponse(_).toRecord)

      summaries.map(_.getString("kind")) should contain allOf (Some("void"), Some("record"), Some("json"), Some("yaml"), Some("scalar"), Some("opaque"))
      summaries.find(_.getString("kind").contains("json")).get.getString("inline") shouldBe Some("false")
      summaries.find(_.getString("kind").contains("yaml")).get.getString("inline") shouldBe Some("false")
      summaries.find(_.getString("kind").contains("json")).get.show should not include ("secret")
      summaries.find(_.getString("kind").contains("yaml")).get.show should not include ("secret")
      summaries.find(_.getString("kind").contains("scalar")).get.show should not include ("secret-token")
      summaries.find(_.getString("kind").contains("opaque")).get.show should not include ("opaque-secret")
    }

    "preserve existing payload references without creating external files" in {
      val summary = DiagnosticPayloadSummary.recordSummary(
        Record.data(
          "payload_href" -> "/web/system/admin/execution/payloads/p1",
          "status" -> "externalized"
        )
      ).toRecord

      summary.getString("payload_href") shouldBe Some("/web/system/admin/execution/payloads/p1")
      summary.getString("href") shouldBe Some("/web/system/admin/execution/payloads/p1")
    }
  }

  "DiagnosticPayloadExternalizer" should {
    "leave summaries as disabled when externalization is off" in {
      val externalizer = DiagnosticPayloadExternalizer.disabled
      val record = Record.data("status" -> "ok")
      val summary = DiagnosticPayloadSummary.recordSummary(record)
      val result = externalizer.externalizeRecordSummary("demo.op", "result", record, summary).toRecord

      result.getString("externalization_status") shouldBe Some("disabled")
      result.getString("payload_href") shouldBe empty
    }

    "write sanitized local files only for matching payload targets and operations" in {
      val root = java.nio.file.Files.createTempDirectory("cncf-diagnostic-payload")
      val config = DiagnosticPayloadExternalizationConfig(
        enabled = true,
        destination = Some("local-file"),
        localRoot = root,
        thresholdBytes = 1,
        payloadTargets = Set("result"),
        operationExact = Set("UserNotification.Notification.searchMyNotifications")
      )
      val externalizer = DiagnosticPayloadExternalizer(config, OperationMode.Develop, BlobStoreConfig())
      val record = Record.data("proof" -> "123456", "status" -> "ok")
      val summary = DiagnosticPayloadSummary.recordSummary(
        record,
        confidentiality = Map("proof" -> DataConfidentiality.Secret)
      )

      val stored = externalizer.externalizeRecordSummary(
        "UserNotification.Notification.searchMyNotifications",
        "result",
        record,
        summary,
        Map("proof" -> DataConfidentiality.Secret)
      ).toRecord
      val skipped = externalizer.externalizeRecordSummary(
        "UserNotification.Notification.searchMyNotifications",
        "request",
        record,
        summary
      ).toRecord

      stored.getString("externalization_status") shouldBe Some("stored")
      stored.getString("payload_storage") shouldBe Some("local-file")
      val path = java.nio.file.Path.of(stored.getString("payload_path").get)
      java.nio.file.Files.exists(path) shouldBe true
      val text = java.nio.file.Files.readString(path)
      text should include ("[redacted]")
      text should not include ("123456")
      skipped.getString("externalization_status") shouldBe Some("not_matched")
      skipped.getString("payload_href") shouldBe empty
    }

    "write durable BlobStore backed references through the BlobStore boundary" in {
      val root = java.nio.file.Files.createTempDirectory("cncf-diagnostic-blob-payload")
      val config = DiagnosticPayloadExternalizationConfig(
        enabled = true,
        destination = Some("blob-store"),
        thresholdBytes = 1,
        payloadTargets = Set("result")
      )
      val externalizer = DiagnosticPayloadExternalizer(
        config,
        OperationMode.Develop,
        BlobStoreConfig(
          backend = BlobStoreConfig.BackendLocal,
          localRoot = Some(root)
        )
      )
      val record = Record.data("status" -> "ok", "count" -> 1)
      val summary = DiagnosticPayloadSummary.recordSummary(record)

      val stored = externalizer.externalizeRecordSummary(
        "demo.operation",
        "result",
        record,
        summary
      ).toRecord

      stored.getString("externalization_status") shouldBe Some("stored")
      stored.getString("payload_storage") shouldBe Some("blob-store")
      stored.getString("payload_ref").get should include ("local://")
      stored.getString("payload_href").get should include ("/web/system/admin/observability/payloads/blob-")
      DiagnosticPayloadReferenceCodec.decodeBlobRef(stored.getString("payload_href").get.split("/").last).map(_.print) shouldBe stored.getString("payload_ref")
    }

    "reject non-durable in-memory BlobStore externalization" in {
      val config = DiagnosticPayloadExternalizationConfig(
        enabled = true,
        destination = Some("blob-store"),
        thresholdBytes = 1,
        payloadTargets = Set("result")
      )
      val externalizer = DiagnosticPayloadExternalizer(config, OperationMode.Develop, BlobStoreConfig())
      val record = Record.data("status" -> "ok")
      val summary = DiagnosticPayloadSummary.recordSummary(record)

      val stored = externalizer.externalizeRecordSummary("demo.operation", "result", record, summary).toRecord

      stored.getString("externalization_status") shouldBe Some("failed")
      stored.getString("externalization_reason").get should include ("durable BlobStore")
    }

    "requires explicit unsafe opt-in before opaque payloads are externalized" in {
      val root = java.nio.file.Files.createTempDirectory("cncf-diagnostic-opaque-payload")
      val disabledUnsafe = DiagnosticPayloadExternalizationConfig(
        enabled = true,
        destination = Some("local-file"),
        localRoot = root,
        thresholdBytes = 1,
        payloadTargets = Set("response"),
        unsafeOpaquePayloads = false
      )
      val enabledUnsafe = disabledUnsafe.copy(unsafeOpaquePayloads = true)
      val summary = DiagnosticPayloadSummary.textSummary("yaml", "token: secret-token", includeInline = false)

      val skipped = DiagnosticPayloadExternalizer(disabledUnsafe, OperationMode.Develop, BlobStoreConfig())
        .externalizeUnsafeTextSummary("demo.operation", "response", "application/yaml", "yaml", "token: secret-token", summary)
        .toRecord
      val stored = DiagnosticPayloadExternalizer(enabledUnsafe, OperationMode.Develop, BlobStoreConfig())
        .externalizeUnsafeTextSummary("demo.operation", "response", "application/yaml", "yaml", "token: secret-token", summary)
        .toRecord

      skipped.getString("externalization_status") shouldBe Some("not_supported")
      stored.getString("externalization_status") shouldBe Some("stored")
      val text = java.nio.file.Files.readString(java.nio.file.Path.of(stored.getString("payload_path").get))
      text should include ("[redacted]")
      text should not include ("secret-token")
    }

    "record externalization outcomes in runtime metrics" in {
      val before = RuntimeDashboardMetrics
        .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
        .points
        .filter(_.scope == "diagnostic-payload.externalization")
        .map(_.count)
        .sum
      val externalizer = DiagnosticPayloadExternalizer.disabled
      val record = Record.data("status" -> "ok")
      val summary = DiagnosticPayloadSummary.recordSummary(record)

      externalizer.externalizeRecordSummary("demo.op", "result", record, summary)

      val metrics = RuntimeDashboardMetrics
        .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
        .points
        .filter(_.scope == "diagnostic-payload.externalization")
      metrics.map(_.count).sum should be > before
      metrics.exists(point =>
        point.labels.get("status").contains("disabled") &&
          point.labels.get("payload_kind").contains("result")
      ) shouldBe true
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
