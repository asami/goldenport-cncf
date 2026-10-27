package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Ordinary configuration data, no process execution or authority fixture.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
final class ComponentDefaultRuntimeValuesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def withRoot[A](body: Path => A): A = {
    val root = Files.createTempDirectory("car-default-environment-")
    try body(root) finally Using.resource(Files.walk(root)) { paths =>
      paths.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
    }
  }
  private def tool(root: Path): Path = {
    val bin = Files.createDirectories(root.resolve("bin"))
    val sbt = Files.writeString(bin.resolve("sbt"), "#!/bin/sh\nexit 0\n")
    sbt.toFile.setExecutable(true) shouldBe true
    bin
  }
  "CAR default environment data" should {
    "derive actual repository, tracking, author and existing executable data without a user Textus configuration" in withRoot { root =>
      Given("ordinary Git metadata and a tool on the selected executable PATH")
      val repository = Files.createDirectories(root.resolve("repository"))
      val git = Files.createDirectories(repository.resolve(".git"))
      Files.writeString(git.resolve("HEAD"), "ref: refs/heads/feature/topic\n")
      Files.writeString(git.resolve("config"), """[remote "origin"]
        |  url = ssh://example.org/project.git
        |[branch "feature/topic"]
        |  remote = origin
        |  merge = refs/heads/integration
        |[user]
        |  name = "Fixture User" # an ordinary quoted Git value
        |  email = fixture@example.invalid
        |""".stripMargin)
      val home = Files.createDirectories(root.resolve("home"))
      val bin = tool(root)
      When("the runtime resolves its explicitly templated CAR defaults")
      val values = ComponentDefaultRuntimeValues.values(repository, home, Vector(bin))
      Then("all supplied data comes from the actual checkout and existing executable")
      values("repository.root") shouldBe repository.toString
      values("git.branch") shouldBe "feature/topic"
      values("git.remote-branch") shouldBe "integration"
      values("git.remote") shouldBe "origin"
      values("git.endpoint") shouldBe "ssh://example.org/project.git"
      values("git.user-name") shouldBe "Fixture User"
      values("git.user-email") shouldBe "fixture@example.invalid"
      values("executable.sbt") shouldBe bin.resolve("sbt").toString
    }

    "use linked-worktree common configuration and an ordinary included author configuration" in withRoot { root =>
      Given("a linked worktree with a relative gitdir, commondir and user include")
      val worktree = Files.createDirectories(root.resolve("worktree"))
      val common = Files.createDirectories(root.resolve("common"))
      val gitdir = Files.createDirectories(common.resolve("worktrees/work"))
      Files.writeString(worktree.resolve(".git"), "gitdir: ../common/worktrees/work\n")
      Files.writeString(gitdir.resolve("commondir"), "../..\n")
      Files.writeString(gitdir.resolve("HEAD"), "ref: refs/heads/local\n")
      Files.writeString(common.resolve("config"), "[remote \"origin\"]\nurl = https://example.org/work.git\n")
      Files.writeString(gitdir.resolve("config.worktree"), "[user]\nname = Worktree Author\n")
      val home = Files.createDirectories(root.resolve("home"))
      Files.writeString(home.resolve(".gitconfig"), "[include]\npath = author.config\n")
      Files.writeString(home.resolve("author.config"), "[user]\nname = Included Author\nemail = included@example.invalid\n")
      val nested = Files.createDirectories(worktree.resolve("nested"))
      When("RuntimeRoot is inside that worktree")
      val values = ComponentDefaultRuntimeValues.values(nested, home, Vector(tool(root)))
      Then("the worktree and its existing configuration are resolved without copying repository state")
      values("runtime.root") shouldBe nested.toString
      values("repository.root") shouldBe worktree.toString
      values("git.branch") shouldBe "local"
      values("git.remote-branch") shouldBe "local"
      values("git.user-name") shouldBe "Included Author"
      Files.writeString(common.resolve("config"), "[remote \"origin\"]\nurl = https://example.org/work.git\n[extensions]\nworktreeConfig = true\n")
      val enabled = ComponentDefaultRuntimeValues.values(nested, home, Vector(root.resolve("bin")))
      enabled("git.user-name") shouldBe "Worktree Author"
    }

    "expand repository tokens in structured CAR defaults using the same typed source values" in withRoot { root =>
      Given("a CAR-owned object containing a repository token without any scalar config entries")
      val git = Files.createDirectories(root.resolve(".git"))
      Files.writeString(git.resolve("HEAD"), "ref: refs/heads/structured\n")
      val key = "application.input"
      val source = ConfigurationValue.ObjectValue(Map("branches" -> ConfigurationValue.ListValue(List(
        ConfigurationValue.StringValue("${git.branch}")))))
      val descriptor = GenericSubsystemDescriptor(root.resolve("application.car"), "application",
        _config_projection = Configuration(Map(key -> source)), componentDefaultSources = Map(key -> "application.car"))
      When("the owning runtime expands its surviving typed defaults")
      val resolved = descriptor.withComponentDefaultRuntimeRoot(root)
      Then("object and list traversal resolve the actual data rather than leaving a raw token")
      resolved.configuration.values(key) shouldBe ConfigurationValue.ObjectValue(Map("branches" ->
        ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("structured")))))
    }

    "omit an unresolved CAR command default while retaining storage paths and Help metadata" in withRoot { root =>
      Given("a RuntimeRoot with no Git metadata and a CAR command template requiring real repository data")
      val process = "textus.subsystem.process-execution.plan"
      val storage = "textus.datastore.path"
      val descriptor = GenericSubsystemDescriptor(root.resolve("application.car"), "application",
        config = Map(process -> """{"branch":"${git.branch}"}""", storage -> "${runtime.root}/state.sqlite"),
        componentDefaultSources = Map(process -> "application.car", storage -> "application.car"))
      When("the owning runtime expands the surviving component defaults")
      val resolved = descriptor.withComponentDefaultRuntimeRoot(root)
      Then("no fake branch or unusable command profile is introduced and storage still has a normal root")
      resolved.config.contains(process) shouldBe false
      resolved.configuration.values.contains(process) shouldBe false
      resolved.config(storage) shouldBe root.resolve("state.sqlite").toString
    }
  }
}
