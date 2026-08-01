package org.goldenport.cncf.subsystem

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemExecutionProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:subsystem-user-mode-admission, example:PM-53-01, rules:PM-53-01, phase:53")

  "SubsystemExecutionProfile" must _in_phase53_spec {
    "remain mode-free while a Subsystem user mode selects its execution profile" in {
      Given("the two Subsystem user modes")

      When("each Subsystem mode is admitted into a Subsystem execution profile")
      val standalone = SubsystemUserMode.Standalone.toExecutionProfile
      val multiuser = SubsystemUserMode.MultiUser.toExecutionProfile

      Then("only current-user evidence crosses into the Subsystem contract")
      standalone shouldBe SubsystemExecutionProfile.Fixed
      multiuser shouldBe SubsystemExecutionProfile.Authenticated
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.http.WebApplicationMode"
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.context.OperationMode"
    }

    "allow controlled tests to select explicit identity evidence without a mode type" in {
      Given("the mode-free profile contract")

      When("a controlled test profile is selected")
      val profile = SubsystemExecutionProfile.ControlledTest

      Then("the selected evidence is explicit and ingress independent")
      profile.currentUserEvidence shouldBe SubsystemCurrentUserEvidence.ControlledTest
    }
  }
}
