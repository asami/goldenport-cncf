package org.goldenport.cncf.config

import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.ConfigurationBindingReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationEnvironmentBindingAdmissionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-environment-binding-runtime-admission, example:E1, rules:GCF08G-R1,R2,R3, phase:55, slice:GCF-08G"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-environment-binding-runtime-admission, example:E2, rules:GCF08G-R1,R4, phase:55, slice:GCF-08G"
  )

  "CNCF configuration environment binding admission" should {
    "E1 partition canonical binding names while retaining opaque values and ordinary environment" must _e1 {
      "when Global and Unicode Subsystem bindings share one supplied environment map" in {
        Given("the closed catalog, canonical environment-name codec, and ordinary application environment")
        val codec = _take(CncfConfigurationEnvironmentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
        val subsystem = _take(SubsystemInstanceId.create("platförm", "default"))
        val global = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.repositoryDir.id,
          CncfConfigurationTarget.Global
        ))
        val scoped = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.subsystemUserMode.id,
          _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem))
        ))
        val globalName = _take(codec.encode(global))
        val scopedName = _take(codec.encode(scoped))

        When("the supplied environment is admitted once before legacy source construction")
        val admission = _take(CncfConfigurationEnvironmentBindingAdmission.admit(Map(
          "TEXTUS_LOG_LEVEL" -> "info",
          globalName -> "a=b",
          scopedName -> "standalone"
        ), CncfConfigurationParameterCatalog.closed))

        Then("only typed assignments leave the boundary and ordinary environment remains residual")
        admission.assignments.map(_.reference.parameterId) shouldBe Vector(global.parameterId, scoped.parameterId)
        admission.assignments.map(_.reference.target) shouldBe Vector(global.target, scoped.target)
        admission.assignments.map(_.rawValue) shouldBe Vector("a=b", "standalone")
        admission.residualEnvironment shouldBe Map("TEXTUS_LOG_LEVEL" -> "info")
      }
    }

    "E2 fail malformed binding names without exposing supplied secrets" must _e2 {
      "when a binding-prefixed name is non-canonical" in {
        Given("a malformed binding key and opaque key/value secrets")
        val secretName = "TEXTUS_BINDING_G_TEXTUS_DSUBSYSTEM_DUSER_HMODE_secret"
        val secretValue = "do-not-render-environment-secret"

        When("admission validates the name before any legacy source exists")
        val result = CncfConfigurationEnvironmentBindingAdmission.admit(Map(secretName -> secretValue))

        Then("a fixed structured failure is returned without the name or value")
        result.isSuccess shouldBe false
        result.display.contains(secretName) shouldBe false
        result.display.contains(secretValue) shouldBe false
      }
    }
  }

  private def _take[A](result: org.goldenport.Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
