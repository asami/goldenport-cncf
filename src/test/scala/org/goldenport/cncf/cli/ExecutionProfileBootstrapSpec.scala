package org.goldenport.cncf.cli

import java.nio.file.Files
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionProfileMode, RuntimeClockMode}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionProfileBootstrapSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "CNCF execution-profile bootstrap" should {
    "activate a controlled profile only from an explicit test descriptor" in {
      Given("a controlled execution block in an explicitly selected test descriptor")
      val cwd = Files.createTempDirectory("cncf-controlled-profile-bootstrap")
      val descriptor = cwd.resolve("test.yaml")
      Files.writeString(
        descriptor,
        """kind: test-descriptor
          |execution:
          |  profile: controlled
          |  key: bootstrap-run
          |  time:
          |    mode: manual
          |    start-at: 2026-07-28T09:00:00Z
          |  random:
          |    mode: seeded
          |    seed: bootstrap-seed
          |  ids:
          |    mode: deterministic
          |  scheduler:
          |    mode: manual
          |  ordering:
          |    mode: deterministic
          |""".stripMargin
      )

      When("cncf test selects the descriptor")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("test", "--test-config", descriptor.toString, "server")
      )
      val config = RuntimeConfig.create(bootstrap.configuration).toOption.get

      Then("the descriptor provenance authorizes one coherent controlled runtime profile")
      config.executionProfile.identity.mode shouldBe ExecutionProfileMode.Controlled
      config.executionClock.mode shouldBe RuntimeClockMode.Manual
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY) shouldBe
        Some(descriptor.normalize.toString)
    }

    "not auto-discover a controlled test descriptor during ordinary startup" in {
      Given("a test.yaml file that is present but not selected")
      val cwd = Files.createTempDirectory("cncf-unselected-profile-bootstrap")
      Files.writeString(
        cwd.resolve("test.yaml"),
        """kind: test-descriptor
          |execution:
          |  profile: controlled
          |  key: unselected-run
          |  time:
          |    mode: manual
          |    start-at: 2026-07-28T09:00:00Z
          |  random:
          |    seed: unselected-seed
          |""".stripMargin
      )

      When("ordinary server bootstrap runs without a test descriptor option")
      val bootstrap = CncfRuntime.bootstrap(cwd, Array("server"))
      val config = RuntimeConfig.create(bootstrap.configuration).toOption.get

      Then("the runtime remains on the standard profile")
      config.executionProfile.identity.mode shouldBe ExecutionProfileMode.Standard
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY) shouldBe None
    }
  }
}
