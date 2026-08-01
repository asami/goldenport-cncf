package org.goldenport.cncf.subsystem

import org.goldenport.cncf.http.WebApplicationMode
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemExecutionProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SubsystemExecutionProfile" should {
    "remain mode-free while Web projects its application modes at the Web boundary" in {
      Given("the two Web application modes")

      When("each mode is projected into a Subsystem execution profile")
      val standalone = WebApplicationMode.Standalone.toSubsystemExecutionProfile
      val multiuser = WebApplicationMode.MultiUser.toSubsystemExecutionProfile

      Then("only current-user evidence crosses into the Subsystem contract")
      standalone shouldBe SubsystemExecutionProfile.Fixed
      multiuser shouldBe SubsystemExecutionProfile.Authenticated
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.http.WebApplicationMode"
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.context.OperationMode"
    }

    "allow controlled tests to select explicit identity evidence without a Web type" in {
      Given("the mode-free profile contract")

      When("a controlled test profile is selected")
      val profile = SubsystemExecutionProfile.ControlledTest

      Then("the selected evidence is explicit and ingress independent")
      profile.currentUserEvidence shouldBe SubsystemCurrentUserEvidence.ControlledTest
    }
  }
}
