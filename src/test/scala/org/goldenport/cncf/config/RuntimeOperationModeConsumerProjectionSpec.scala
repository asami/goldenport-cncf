package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentLogic
import org.goldenport.cncf.component.builtin.metrics.MetricsComponent
import org.goldenport.cncf.metrics.{ComponentMetricsRegistry, EntityAccessMetricsRegistry}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, DiagnosticPayloadExternalizer, OpenTelemetryExportConfig}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeOperationModeConsumerProjectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Runtime operation-mode consumers" should {
    "project normal component and Metrics operation modes from admitted policy values" in {
      Given("a raw develop decode and a conflicting admitted production policy")
      val rawmode = OperationMode.Develop
      val admitted = Consequence.success(RuntimeOperationSecurityPolicy.default.copy(
        operationMode = OperationMode.Production
      ))

      When("component runtime and Metrics project their operation modes")
      val componentmode = ComponentLogic.runtimeOperationMode(Some(admitted))
      val metricsmode = MetricsComponent.operationMode(admitted)

      Then("both consumers retain the admitted production mode instead of the raw develop value")
      rawmode shouldBe OperationMode.Develop
      componentmode shouldBe OperationMode.Production
      metricsmode shouldBe OperationMode.Production

      And("the framework default remains the compatibility fallback before admission")
      ComponentLogic.runtimeOperationMode(None) shouldBe RuntimeConfig.defaultOperationMode
      MetricsComponent.operationMode(Consequence.configurationInvalid("not admitted")) shouldBe RuntimeConfig.defaultOperationMode
    }

    "evaluate OpenTelemetry endpoint and production validation for the authoritative mode" in {
      Given("an enabled OTEL configuration decoded against a conflicting raw develop mode")
      def _decode_(mode: OperationMode) = OpenTelemetryExportConfig.fromValues(
          enabled = true,
          endpoint = None,
          protocol = None,
          tracesEnabled = None,
          metricsEnabled = None,
          logsEnabled = None,
          operationMode = mode
        )
      val rawdecoded = _decode_(OperationMode.Develop)

      When("the admitted production mode is applied before export")
      val authoritative = rawdecoded.forOperationMode(OperationMode.Production)

      Then("the develop default endpoint is removed and production validation is enforced")
      rawdecoded.normalizedEndpoint(OperationMode.Develop) shouldBe Some(OpenTelemetryExportConfig.DefaultEndpoint)
      rawdecoded.validationError shouldBe None
      authoritative.normalizedEndpoint(OperationMode.Production) shouldBe None
      authoritative.validationError shouldBe Some(
        "textus.observability.otel.endpoint is required when OpenTelemetry export is enabled in production"
      )

      And("a stale raw production validation can be cleared by an admitted develop mode")
      val rawproduction = _decode_(OperationMode.Production)
      rawproduction.validationError.nonEmpty shouldBe true
      rawproduction.forOperationMode(OperationMode.Develop).validationError shouldBe None
    }

    "evaluate diagnostic destination validation for the authoritative mode and restore nested scope" in {
      Given("an enabled diagnostic configuration decoded against a conflicting raw develop mode")
      def _decode_(mode: OperationMode, allowrequestoverride: Option[Boolean] = None) = DiagnosticPayloadExternalizationConfig.fromValues(
          enabled = true,
          destination = None,
          localRoot = None,
          thresholdBytes = None,
          payloadTargets = Vector.empty,
          operationExact = Vector.empty,
          operationContains = Vector.empty,
          allowRequestOverride = allowrequestoverride,
          unsafeOpaquePayloads = None,
          retentionDays = None,
          operationMode = mode
        )
      val rawdecoded = _decode_(OperationMode.Develop)

      When("the admitted production mode is applied and a nested diagnostic scope runs")
      val authoritative = rawdecoded.forOperationMode(OperationMode.Production)
      val nested = DiagnosticPayloadExternalizer.withOperation("outer", OperationMode.Production) {
        val outer = DiagnosticPayloadExternalizer.currentOperationMode
        val inner = DiagnosticPayloadExternalizer.withOperation("inner", OperationMode.Develop) {
          DiagnosticPayloadExternalizer.currentOperationMode
        }
        (outer, inner, DiagnosticPayloadExternalizer.currentOperationMode)
      }

      Then("production requires an explicit destination rather than retaining the raw develop default")
      rawdecoded.normalizedDestination(OperationMode.Develop) shouldBe Some(DiagnosticPayloadExternalizationConfig.DestinationLocalFile)
      rawdecoded.validationError shouldBe None
      authoritative.normalizedDestination(OperationMode.Production) shouldBe None
      authoritative.validationError shouldBe Some(
        "textus.observability.payload.externalization.destination is required when externalization is enabled in production"
      )

      And("implicit request overrides are recomputed while explicit values remain authoritative")
      val explicitenabled = _decode_(OperationMode.Production, Some(true))
      val explicitdisabled = _decode_(OperationMode.Develop, Some(false))
      val directenabled = DiagnosticPayloadExternalizationConfig(allowRequestOverride = true)
      val directdisabled = DiagnosticPayloadExternalizationConfig(allowRequestOverride = false)
      rawdecoded.allowRequestOverride shouldBe true
      rawdecoded.forOperationMode(OperationMode.Production).allowRequestOverride shouldBe false
      explicitenabled.forOperationMode(OperationMode.Develop).allowRequestOverride shouldBe true
      explicitdisabled.forOperationMode(OperationMode.Production).allowRequestOverride shouldBe false
      directenabled.forOperationMode(OperationMode.Production).allowRequestOverride shouldBe true
      directdisabled.forOperationMode(OperationMode.Develop).allowRequestOverride shouldBe false

      And("a stale raw production validation can be cleared by an admitted develop mode")
      val rawproduction = _decode_(OperationMode.Production)
      rawproduction.validationError.nonEmpty shouldBe true
      rawproduction.forOperationMode(OperationMode.Develop).validationError shouldBe None

      And("nested scopes restore the caller's admitted mode")
      nested shouldBe ((Some(OperationMode.Production), Some(OperationMode.Develop), Some(OperationMode.Production)))
      DiagnosticPayloadExternalizer.currentOperationMode shouldBe None
    }

    "defer Metrics operation-mode projection until runtime metric export" in {
      Given("a Metrics service constructed before policy admission with a deferred supplier")
      var policy: Consequence[RuntimeOperationSecurityPolicy] = Consequence.configurationInvalid(
        "runtime operation security policy bindings have not been admitted"
      )
      var suppliedmodes = Vector.empty[OperationMode]
      val service = new MetricsComponent.DefaultMetricsService(
        EntityAccessMetricsRegistry.shared,
        ComponentMetricsRegistry.create(),
        RuntimeConfig.default.copy(openTelemetryExportConfig = OpenTelemetryExportConfig(metricsEnabled = false)),
        () => {
          val mode = policy.toOption.map(_.operationMode).getOrElse(RuntimeConfig.defaultOperationMode)
          suppliedmodes = suppliedmodes :+ mode
          policy
        }
      )

      When("runtime binding admission changes the same supplier to production before export")
      suppliedmodes shouldBe empty
      val beforeadmission = service.loadRuntimeMetrics()
      policy = Consequence.success(RuntimeOperationSecurityPolicy.default.copy(
        operationMode = OperationMode.Production
      ))
      val afteradmission = service.loadRuntimeMetrics()

      Then("the existing consumer resolves the admitted production policy at load time")
      beforeadmission.toOption.nonEmpty shouldBe true
      afteradmission.toOption.nonEmpty shouldBe true
      suppliedmodes shouldBe Vector(RuntimeConfig.defaultOperationMode, OperationMode.Production)

      And("no construction-time fallback policy was captured")
      MetricsComponent.operationMode(Consequence.configurationInvalid("not admitted")) shouldBe RuntimeConfig.defaultOperationMode
    }
  }
}
