package org.goldenport.cncf.cli

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.bootstrap.BootstrapConfig
import org.goldenport.cncf.subsystem.SystemNode
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeDataStoreShutdownSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String) =
    afterWord(s"in spec:phase-54-dsp06, example:$example, rules:DSP06-R1-R4, phase:54, slice:DSP-06")

  "Cncf runtime datastore shutdown" when {
    "an owned embedding handle is closed" which {
      "E1 finalizes its runtime-owned SystemNode exactly once" must _metadata("E1") {
        "close the runtime before resetting its embedding surface" in {
          Given("a controlled runtime test descriptor and an owned CncfHandle")
          val cwd = Files.createTempDirectory("cncf-runtime-datastore-shutdown")
          val descriptor = cwd.resolve("test.yaml")
          Files.writeString(
            descriptor,
            """kind: test-descriptor
              |execution:
              |  profile: controlled
              |  key: phase-54-dsp06
              |  time:
              |    mode: manual
              |    start-at: 2026-08-02T00:00:00Z
              |  random:
              |    mode: seeded
              |    seed: phase-54-dsp06
              |  ids:
              |    mode: deterministic
              |  scheduler:
              |    mode: manual
              |  ordering:
              |    mode: deterministic
              |""".stripMargin
          )
          val runtime = new CncfRuntime()
          val handle = runtime.initializeHandle(BootstrapConfig(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor")
          )) match {
            case Consequence.Success(value) => value
            case Consequence.Failure(conclusion) => fail(conclusion.show)
          }
          val node = handle.subsystem.systemNode

          When("the owned handle closes repeatedly")
          handle.close()
          handle.close()
          val later = handle.executeCommand(Array("help"))

          Then("the SystemNode is terminal and further owned-handle execution fails structurally")
          node.state shouldBe SystemNode.State.Stopped
          later.toOption shouldBe None
        }
      }
    }

    "a caller-managed embedding runtime is returned" which {
      "E2 leaves physical node finalization to its caller" must _metadata("E2") {
        "preserve the caller-managed compatibility boundary" in {
          Given("a controlled runtime test descriptor and a bare embedding initialization")
          val cwd = Files.createTempDirectory("cncf-runtime-bare-embedding")
          val descriptor = _controlled_descriptor(cwd, "phase-54-dsp06-bare")
          val runtime = new CncfRuntime()
          val subsystem = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor")
          ) match {
            case Consequence.Success(value) => value
            case Consequence.Failure(conclusion) => fail(conclusion.show)
          }
          val node = subsystem.systemNode

          When("the caller releases the logical Subsystem binding and then finalizes its node")
          val before = node.state
          subsystem.shutdown()
          val afterSubsystem = node.state
          val nodeResult = node.shutdownC()

          Then("bare initialization has no ambient owner: Subsystem cleanup releases only its binding")
          before shouldBe SystemNode.State.Running
          afterSubsystem shouldBe SystemNode.State.Running
          node.state shouldBe SystemNode.State.Stopped
          nodeResult.toOption shouldBe Some(())
          runtime.closeEmbedding()
        }
      }

      "E3 makes repeated Subsystem cleanup a cached logical-binding operation" must _metadata("E3") {
        "avoid repeated resource cleanup while leaving node finalization to the caller" in {
          Given("a controlled bare embedding runtime")
          val cwd = Files.createTempDirectory("cncf-runtime-bare-repeated-close")
          val descriptor = _controlled_descriptor(cwd, "phase-54-dsp06-repeated-close")
          val runtime = new CncfRuntime()
          val subsystem = runtime.initializeForEmbedding(
            cwd = cwd,
            args = Array(s"--textus.test.descriptor=$descriptor")
          ).toOption.get
          val node = subsystem.systemNode

          When("the caller repeats logical Subsystem cleanup")
          val first = subsystem.shutdownC()
          val second = subsystem.shutdownC()
          val nodeResult = node.shutdownC()

          Then("the first result is reused and only the explicit node owner terminalizes pools")
          second shouldBe first
          node.bindingCount shouldBe 0
          nodeResult.toOption shouldBe Some(())
          node.state shouldBe SystemNode.State.Stopped
          runtime.closeEmbedding()
        }
      }
    }
  }

  private def _controlled_descriptor(cwd: java.nio.file.Path, key: String): java.nio.file.Path = {
    val descriptor = cwd.resolve("test.yaml")
    Files.writeString(
      descriptor,
      s"""kind: test-descriptor
         |execution:
         |  profile: controlled
         |  key: $key
         |  time:
         |    mode: manual
         |    start-at: 2026-08-02T00:00:00Z
         |  random:
         |    mode: seeded
         |    seed: $key
         |  ids:
         |    mode: deterministic
         |  scheduler:
         |    mode: manual
         |  ordering:
         |    mode: deterministic
         |""".stripMargin
    )
    descriptor
  }
}
