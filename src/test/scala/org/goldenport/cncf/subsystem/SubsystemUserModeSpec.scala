package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemUserModeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-pm-53-01-subsystem-user-mode, example:$example, rules:$rules, phase:53, slice:PM-53-01")

  "Subsystem user mode" should {
    "E1 accept only the canonical key and exact canonical values" must _metadata("E1", "PM53-R1") {
    "when two stable Subsystems have obsolete Web inputs" in {
      Given("spec:phase-53-pm-53-01-subsystem-user-mode, example:E1, rules:PM53-R1")
      val standalone = DefaultSubsystemFactory.default(
        mode = None,
        configuration = _configuration(Map(
          SubsystemUserMode.CONFIGURATION_KEY -> "standalone",
          "textus.web.application-mode" -> "multi-user"
        ))
      )
      val multiuser = DefaultSubsystemFactory.default(
        mode = None,
        configuration = _configuration(Map(
          SubsystemUserMode.CONFIGURATION_KEY -> "multi-user",
          "textus.web.application-mode" -> "standalone"
        ))
      )

      When("each stable Subsystem resolves its own mode")
      val standalonec = standalone.subsystemUserModeC
      val multiuserc = multiuser.subsystemUserModeC

      Then("their values do not share a JVM-wide authority and Web values cannot override them")
      standalonec.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
      SubsystemUserMode.parse("multi-user") shouldBe Some(SubsystemUserMode.MultiUser)
      multiuserc shouldBe a[Consequence.Failure[_]]
    }
    }

    "E2 reject malformed and compatibility value spellings" must _metadata("E2", "PM53-R2") {
    "when blank, underscore, and legacy-compatible values are supplied" in {
      Given("spec:phase-53-pm-53-01-subsystem-user-mode, example:E2, rules:PM53-R2")
      val values = Vector(" ", " STANDALONE ", "MULTI-USER", "multi_user", "multiuser", "shared")

      When("the canonical parser is applied")
      val results = values.map(SubsystemUserMode.parse)

      Then("no spelling selects a Subsystem user mode")
      results shouldBe Vector.fill(values.size)(None)
    }
    }

    "E3 reject malformed canonical configuration without using Web fallback keys" must _metadata("E3", "PM53-R3") {
    "when an obsolete Web value accompanies malformed canonical input" in {
      Given("spec:phase-53-pm-53-01-subsystem-user-mode, example:E3, rules:PM53-R3")
      val subsystem = DefaultSubsystemFactory.default(
        mode = None,
        configuration = _configuration(Map(
          SubsystemUserMode.CONFIGURATION_KEY -> "multi_user",
          "textus.web.application-mode" -> "standalone"
        ))
      )

      When("Subsystem user-mode admission runs")
      val result = subsystem.subsystemUserModeC

      Then("the malformed canonical value fails closed without a Web fallback")
      result shouldBe a[Consequence.Failure[_]]
    }
    }

    "E4 reject composite and null canonical values without deriving standalone" must _metadata("E4", "PM53-R4") {
    "when canonical input is not one of the two exact strings" in {
      Given("spec:phase-53-pm-53-01-subsystem-user-mode, example:E4, rules:PM53-R4")
      val values = Vector[ConfigurationValue](
        ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("standalone"))),
        ConfigurationValue.ObjectValue(Map("value" -> ConfigurationValue.StringValue("multi-user"))),
        ConfigurationValue.NullValue,
        ConfigurationValue.NumberValue(BigDecimal(1)),
        ConfigurationValue.BooleanValue(true)
      )

      When("controlled Subsystems resolve each structured canonical value")
      val results = values.map { value =>
        DefaultSubsystemFactory.default(
          mode = None,
          configuration = _configuration_values(Map(SubsystemUserMode.CONFIGURATION_KEY -> value))
        ).subsystemUserModeC
      }

      Then("every present non-string value fails closed rather than selecting a default")
      results.foreach(_ shouldBe a[Consequence.Failure[_]])
    }
    }
  }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    _configuration_values(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap)

  private def _configuration_values(values: Map[String, ConfigurationValue]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values),
      ConfigurationTrace.empty
    )
}
