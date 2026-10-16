package org.goldenport.cncf.component.builtin.metrics

import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.job.{JobRunMode, JobSubmitOption}
import org.goldenport.cncf.metrics.{
  ComponentMetricDefinition, RuntimeMetricPoint, RuntimeMetricsCatalog, RuntimeMetricsSnapshot
}
import org.goldenport.cncf.observability.{OpenTelemetryExportConfig, OpenTelemetryExporterSupport}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class MetricsJobOperationalIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E11 MC1 canonical runtime metrics Actions" must _scenario("E11", "R1,R4,R6") {
    "consume the actual subsystem JobEngine and preserve the original metrics scopes" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R4,R6; E11/MC1: an actual DefaultSubsystemFactory runtime, canonical metrics component and original runtime registries; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6; docs/spec/durable-job-operational-metrics-contract.md R6")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      try {
        val component = subsystem.findComponent(BuiltinComponentIdentity.METRICS).getOrElse(fail("missing metrics component"))
        RuntimeDashboardMetrics.recordHtmlRequest("GET", "/metrics-operational-spec", 200, 1L)
        subsystem.entityAccessMetrics.record("entity.operational-spec", Record.dataAuto(
          "entity" -> "fixture", "source" -> "datastore", "outcome" -> "success"))
        val originalrecorded = subsystem.componentMetrics.record(
          ComponentMetricDefinition("operational.fixture", Vector("component")), Map("component" -> "fixture"))
        When("a synchronous Nil Job executes through subsystem.jobEngine and canonical runtime/catalog Actions resolve and execute")
        val jobid = _success(subsystem.jobEngine.submit(Nil, component.logic.executionContext(),
          JobSubmitOption(runMode = JobRunMode.Sync)))
        val actual = subsystem.jobEngine.operationalMetrics.get
        val runtime = _execute(component, _request(subsystem.resolver, "load_runtime_metrics"))
        val catalog = _execute(component, _request(subsystem.resolver, "load_metrics_catalog"))
        Then("the runtime returns four exact real engine cells and all 24 catalog scopes with five lifecycle labels and two labels for each operation scope")
        originalrecorded shouldBe true
        subsystem.jobEngine.getResult(jobid) shouldBe None
        actual shouldBe _expected
        _job_records(runtime) shouldBe actual.map(_.toRecord)
        val scopes = _records(catalog, "scopes")
        scopes.flatMap(_.getString("scope")) shouldBe (_original_scopes ++ Vector("job.lifecycle", "job.operation", "job.durable.operation"))
        scopes.size shouldBe 24
        scopes.find(_.getString("scope").contains("job.lifecycle")).get shouldBe
          RuntimeMetricsCatalog.scopes.find(_.scope == "job.lifecycle").get.toRecord
        scopes.find(_.getString("scope").contains("job.lifecycle")).get.getVector("label_keys") shouldBe
          Some(Vector("family", "operation", "outcome", "persistence", "run_mode"))
        scopes.find(_.getString("scope").contains("job.operation")).get shouldBe
          RuntimeMetricsCatalog.scopes.find(_.scope == "job.operation").get.toRecord
        scopes.find(_.getString("scope").contains("job.operation")).get.getVector("label_keys") shouldBe
          Some(Vector("operation", "outcome"))
        scopes.find(_.getString("scope").contains("job.durable.operation")).get shouldBe
          RuntimeMetricsCatalog.scopes.find(_.scope == "job.durable.operation").get.toRecord
        scopes.find(_.getString("scope").contains("job.durable.operation")).get.getVector("label_keys") shouldBe
          Some(Vector("operation", "outcome"))
        val points = _records(runtime, "metrics")
        points.flatMap(_.getString("scope")) should contain allElementsOf Vector("web.request", "entity-access", "component")
        points.exists(_.getString("name").contains("operational.fixture")) shouldBe true
        subsystem.jobEngine.operationalMetrics shouldBe Some(actual)
      } finally Subsystem.shutdownOwned(subsystem)
    }
  }

  "E12 MC2 additive metrics service and OTEL consumption" must _scenario("E12", "R4,R6") {
    "retain four-argument callers and merge supplied counters before the existing exporter" in {
      Given("docs/spec/job-operational-metrics-contract.md R4,R6; E12/MC2: real subsystem engine counters, the original four-argument service and a fifth deferred supplier; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      try {
        val component = subsystem.findComponent(BuiltinComponentIdentity.METRICS).getOrElse(fail("missing metrics component"))
        _success(subsystem.jobEngine.submit(Nil, component.logic.executionContext(), JobSubmitOption(runMode = JobRunMode.Sync)))
        val source = subsystem.jobEngine.operationalMetrics
        val unsupported = Option.empty[Vector[RuntimeMetricPoint]]
        val config = RuntimeConfig.default.copy(openTelemetryExportConfig = OpenTelemetryExportConfig())
        val order = ArrayBuffer.empty[String]
        val policy = () => {
          order += "security"
          subsystem.runtimeOperationSecurityPolicyC
        }
        val original = new MetricsComponent.DefaultMetricsService(
          subsystem.entityAccessMetrics, subsystem.componentMetrics, config, policy)
        val supplied = new MetricsComponent.DefaultMetricsService(
          subsystem.entityAccessMetrics, subsystem.componentMetrics, config, policy, () => {
            order += "jobs"
            subsystem.jobEngine.operationalMetrics.getOrElse(Vector.empty)
          })
        val absent = new MetricsComponent.DefaultMetricsService(
          subsystem.entityAccessMetrics, subsystem.componentMetrics, config, policy,
          () => unsupported.getOrElse(Vector.empty))
        When("the original caller, real engine supplier and unsupported optional source load runtime snapshots")
        val originalrecord = _success(original.loadRuntimeMetrics())
        order.clear()
        val suppliedrecord = _success(supplied.loadRuntimeMetrics())
        val suppliedorder = order.toVector
        val absentrecord = _success(absent.loadRuntimeMetrics())
        val points = source.get
        val snapshot = RuntimeMetricsSnapshot(
          Instant.parse(suppliedrecord.getString("generated_at").get), points, RuntimeMetricsCatalog.scopes)
        val payload = OpenTelemetryExporterSupport.metricsPayload(snapshot)
        val metrics = payload.hcursor.downField("resourceMetrics").downArray
          .downField("scopeMetrics").downArray.downField("metrics").as[Vector[io.circe.Json]].toOption.get
        Then("deferred counters precede exporter construction, exact cells retain cumulative OTEL sums and unsupported capability invents no counters or readiness")
        source shouldBe Some(_expected)
        unsupported shouldBe None
        _job_records(originalrecord) shouldBe Vector.empty
        _job_records(absentrecord) shouldBe Vector.empty
        _job_records(suppliedrecord) shouldBe points.map(_.toRecord)
        suppliedorder shouldBe Vector("jobs", "security")
        suppliedrecord.getRecord("otel_export").flatMap(_.getString("status")) shouldBe Some("disabled")
        absentrecord.asMap.keySet should not contain "readiness"
        metrics.size shouldBe 4
        metrics.flatMap(_.hcursor.get[String]("name").toOption) shouldBe
          Vector.fill(3)("cncf.job.lifecycle.facts.count") :+ "cncf.job.operation.calls.count"
        metrics.zip(_expected).foreach { case (metric, expected) =>
          metric.hcursor.get[String]("name").toOption shouldBe
            Some(s"cncf.${expected.scope}.${expected.name}.count")
          val sum = metric.hcursor.downField("sum")
          sum.get[Int]("aggregationTemporality").toOption shouldBe Some(2)
          sum.get[Boolean]("isMonotonic").toOption shouldBe Some(true)
          sum.downField("dataPoints").downArray.get[String]("asInt").toOption shouldBe Some("1")
          metric.hcursor.downField("gauge").focus shouldBe None
          val attributes = sum.downField("dataPoints").downArray.downField("attributes")
            .as[Vector[io.circe.Json]].toOption.get
          attributes.flatMap(_.hcursor.get[String]("key").toOption).toSet shouldBe
            expected.labels.keySet.map(key => s"cncf.$key")
          attributes.map { attribute =>
            attribute.hcursor.get[String]("key").toOption.get ->
              attribute.hcursor.downField("value").get[String]("stringValue").toOption.get
          }.toMap shouldBe expected.labels.map { case (key, value) => s"cncf.$key" -> value }
        }
        Given("an actual supplier exception whose identity must remain visible")
        val failure = new IllegalStateException("supplier unavailable")
        val throwing = new MetricsComponent.DefaultMetricsService(
          subsystem.entityAccessMetrics, subsystem.componentMetrics, config, policy, () => throw failure)
        When("the service consumes the throwing supplier")
        val thrown = intercept[IllegalStateException](throwing.loadRuntimeMetrics())
        Then("the original exception propagates without an invented empty or healthy fallback")
        thrown shouldBe failure
      } finally Subsystem.shutdownOwned(subsystem)
    }
  }

  private val _original_scopes = Vector(
    "web.request", "action.execution", "authorization.decision", "dsl.chokepoint", "validation",
    "operation-request-validation", "blob.operation", "rule.execution", "spi.invocation",
    "operation-evaluation.delivery", "mcp-client.invocation", "process.execution", "resource-tree.snapshot",
    "resource-tree.query", "service-container.lifecycle", "component-initialization.parameter-resolution",
    "entity.conditional-transition", "diagnostic-payload.externalization", "otel.export", "entity-access", "component"
  )

  private val _expected = Vector(
    ("admission", "submit", "accepted"), ("execution", "run", "started"), ("execution", "run", "succeeded")
  ).map { case (family, operation, outcome) => RuntimeMetricPoint("job.lifecycle", "facts", Map(
    "family" -> family, "operation" -> operation, "outcome" -> outcome,
    "persistence" -> "persistent", "run_mode" -> "sync"), 1L) } :+
    RuntimeMetricPoint("job.operation", "calls", Map("operation" -> "submit", "outcome" -> "accepted"), 1L)

  private def _records(record: Record, key: String): Vector[Record] =
    record.getVector(key).getOrElse(Vector.empty).collect { case value: Record => value }

  private def _job_records(record: Record): Vector[Record] =
    _records(record, "metrics").filter(record => record.getString("scope").exists(scope => Set("job.lifecycle", "job.operation").contains(scope)))

  private def _execute(component: Component, request: Request): Record =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action => component.logic.execute(component.logic.createActionCall(action))
      case other => Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    } match {
      case Consequence.Success(OperationResponse.RecordResponse(record)) => record
      case other => fail(s"expected record response but got $other")
    }

  private def _request(resolver: OperationResolver, operation: String): Request =
    resolver.resolve(s"${BuiltinComponentIdentity.METRICS.name}.metrics.$operation") match {
      case ResolutionResult.Resolved(_, component, service, name) =>
        Request.of(component = component, service = service, operation = name)
      case other => fail(s"resolver failed: $other")
    }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-metrics, example:$example, rules:$rules, phase:69.7, additive-spec:job-operation-metrics, additive-rules:R1,R4,R6${if (example == "E11") ", companion-spec:durable-job-operational-metrics, companion-rules:R6" else ""}")
}
