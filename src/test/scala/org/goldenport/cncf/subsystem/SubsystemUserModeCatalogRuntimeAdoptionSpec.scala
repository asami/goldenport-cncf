package org.goldenport.cncf.subsystem

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolution, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemUserModeCatalogRuntimeAdoptionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-55-gcf07b-subsystem-user-mode-runtime-adoption, example:$example, rules:$rules, phase:55, slice:GCF-07B")

  "Subsystem user-mode catalog runtime adoption" should {
    "E1 resolve the registered catalog witness without changing the legacy trace authority" must _metadata("E1", "GCF07B-R1,R2") {
      "when a fixed-user Subsystem receives the canonical typed value and a prior Phase 53 trace" in {
        Given("one descriptor-owned Subsystem, canonical standalone input, and its existing ConfigurationResolution")
        val trace = ConfigurationResolution(
          SubsystemUserMode.CONFIGURATION_KEY,
          ConfigurationValue.StringValue("standalone"),
          ConfigurationOrigin.Home,
          Nil,
          Some("file"),
          Some(".textus/runtime.conf")
        )
        val subsystem = _fixed_subsystem(_configuration(
          ConfigurationValue.StringValue("standalone"),
          ConfigurationTrace(Map(SubsystemUserMode.CONFIGURATION_KEY -> trace))
        ))

        When("Subsystem admission resolves the registered catalog parameter")
        val result = subsystem.subsystemUserModeC

        Then("the catalog value selects standalone and the legacy Phase 53 trace remains unchanged")
        result.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
        result.toOption.map(_.trace) shouldBe Some(trace)
      }
    }

    "E2 fail structurally for catalog-rejected input without deriving standalone" must _metadata("E2", "GCF07B-R1,R3") {
      "when a fixed-user Subsystem receives an invalid enum or a non-string value" in {
        Given("two present canonical values outside the registered codec")
        val malformed = _fixed_subsystem(_configuration(ConfigurationValue.StringValue("multi_user")))
        val structured = _fixed_subsystem(_configuration(ConfigurationValue.BooleanValue(true)))

        When("the Subsystem resolves its user-mode")
        val malformedresult = malformed.subsystemUserModeC
        val structuredresult = structured.subsystemUserModeC

        Then("both values fail before default derivation")
        malformedresult shouldBe a[Consequence.Failure[_]]
        structuredresult shouldBe a[Consequence.Failure[_]]
      }
    }
  }

  private def _fixed_subsystem(configuration: ResolvedConfiguration): Subsystem =
    Subsystem("gcf07b-runtime", configuration = configuration)
      .withDescriptor(
        GenericSubsystemDescriptor(
          path = Path.of("gcf07b-runtime.car"),
          subsystemName = "gcf07b-runtime",
          security = Some(GenericSubsystemSecurityBinding(
            authentication = Some(GenericSubsystemAuthenticationBinding(
              localSubject = Some(GenericSubsystemLocalSubjectBinding("gcf07b-runtime"))
            ))
          ))
        )
      )

  private def _configuration(
    value: ConfigurationValue,
    trace: ConfigurationTrace = ConfigurationTrace.empty
  ): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(SubsystemUserMode.CONFIGURATION_KEY -> value)),
      trace
    )
}
