package org.goldenport.cncf.backend.docker

import java.nio.charset.StandardCharsets

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.Assertions.fail

import org.goldenport.Consequence
import org.goldenport.tree.{Tree, TreeDir, TreeEntry, TreeLeaf}
import org.goldenport.bag.Bag
import org.goldenport.process.{ShellCommand, ShellCommandExecutor, ShellCommandResult}
import org.goldenport.vfs.DirectoryFileSystemView

/*
 * Executable Specification for CommandDockerAdapter.
 *
 * Phase 3.1 scope:
 * - Tree[Bag] is expanded to filesystem
 * - filesystem is collected back into Tree[Bag]
 * - docker execution is stubbed
 */
/*
 * @since   Feb.  5, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class CommandDockerAdapterSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  "CommandDockerAdapter" should {

    "roundtrip Tree[Bag] through filesystem without loss" in {
      Given("a Tree with files and directories")
      val tree: Tree[Bag] =
        Tree(
          TreeDir(
            Vector(
              TreeEntry(
                "dir",
                TreeDir(
                  Vector(
                    TreeEntry(
                      "file.txt",
                      TreeLeaf(Bag.text("hello", StandardCharsets.UTF_8))
                    )
                  )
                )
              ),
              TreeEntry(
                "root.txt",
                TreeLeaf(Bag.text("world", StandardCharsets.UTF_8))
              )
            )
          )
        )

      val input =
        DockerInput(
          image = "stub",
          files = tree,
          command = Vector.empty
        )

      And("a CommandDockerAdapter")
      // Define a minimal fake executor that does not run real docker
      val fakeExecutor = new ShellCommandExecutor {
        override def execute(command: ShellCommand): Consequence[ShellCommandResult] =
          Consequence.success(
            ShellCommandResult(
              exitCode = 0,
              stdout = Bag.empty,
              stderr = Bag.empty,
              files = Map.empty,
              directories = Map.empty
            )
          )
      }
      val adapter = new CommandDockerAdapter(fakeExecutor)

      When("the adapter is executed")
      val result = adapter.execute(input)

      Then("execution succeeds")
      val dockerOutput = result match {
        case Consequence.Success(value) => value
        case Consequence.Failure(conclusion) =>
          fail(conclusion.toString)
      }

      val shellResult = dockerOutput.result

      And("exit code is zero")
      shellResult.exitCode shouldBe 0

      And("stdout and stderr are empty")
      shellResult.stdout.asStringUnsafe() shouldBe ""
      shellResult.stderr.asStringUnsafe() shouldBe ""

      And("the adapter reports the working directory view")
      shellResult.directories.get("work") shouldBe defined
      shellResult.directories("work") shouldBe a[DirectoryFileSystemView]
    }
  }
}
