package org.goldenport.cncf.backend.docker

import java.nio.charset.StandardCharsets

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.Assertions.fail

import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.tree.{Tree, TreeDir, TreeEntry, TreeLeaf}
import org.goldenport.bag.Bag
import org.goldenport.process.{ExternalCommand, ExternalCommandExecutor, ExternalCommandResult}

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
 * @version Feb.  5, 2026
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
      val fakeExecutor = new ExternalCommandExecutor {
        override def execute(command: ExternalCommand): Consequence[ExternalCommandResult] =
          Consequence.success(ExternalCommandResult(0, "", ""))
      }
      val adapter = new CommandDockerAdapter(fakeExecutor)

      When("the adapter is executed")
      val result = adapter.execute(input)

      Then("execution succeeds")
      val output = result match {
        case Consequence.Success(value) => value
        case Consequence.Failure(conclusion) =>
          fail(conclusion.toString)
      }

      And("exit code is zero")
      output.exitCode shouldBe 0

      And("stdout and stderr are empty")
      output.stdout shouldBe ""
      output.stderr shouldBe ""

      And("the output tree preserves structure and content")
      val outTree = output.files

      val expectedPaths = Map(
        "dir/file.txt" -> "hello",
        "root.txt" -> "world"
      )
      val actualPaths = flattenFiles(outTree.root).map { case (path, bag) =>
        path -> readBag(bag)
      }

      actualPaths shouldBe expectedPaths
    }
  }
}

  private def flattenFiles(
    dir: TreeDir[Bag],
    prefix: String = ""
  ): Map[String, Bag] =
    dir.children.flatMap { entry =>
      val path = if (prefix.isEmpty) entry.name else s"$prefix/${entry.name}"
      entry.node match {
        case childDir: TreeDir[Bag] => flattenFiles(childDir, path)
        case TreeLeaf(bag, _) => Map(path -> bag)
      }
    }.toMap

  private def readBag(bag: Bag): String =
    Using.resource(bag.openInputStream()) { in =>
      new String(in.readAllBytes(), StandardCharsets.UTF_8)
    }
