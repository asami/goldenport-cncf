package org.goldenport.cncf.subsystem

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}

/** Ordinary configuration data, no process execution or authority fixture.
  * @since Oct. 27, 2026
  * @version Nov. 16, 2026
  */
final class ComponentDefaultRuntimeValuesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _with_root[A](body: Path => A): A = {
    val root = Files.createTempDirectory("car-default-environment-")
    try body(root) finally Using.resource(Files.walk(root)) { paths =>
      paths.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
    }
  }
  private def _tool(root: Path): Path = {
    val bin = Files.createDirectories(root.resolve("bin"))
    val sbt = Files.writeString(bin.resolve("sbt"), "#!/bin/sh\nexit 0\n")
    sbt.toFile.setExecutable(true) shouldBe true
    bin
  }
  "CAR default environment data" should {
    "project normalized root and parent data for lexical paths without creating them" in _with_root { root =>
      Given("generated absent runtime paths containing lexical dot and parent segments")
      val names = Gen.choose(1, 20).map(n => "runtime-" + n)
      When("path-only and rich environment data are resolved without executable or Git fixtures")
      val property = Prop.forAll(names) { name =>
        val input = root.resolve(name).resolve("unused/.././source")
        val expectedroot = root.resolve(name).resolve("source")
        val expected = Map("runtime.root" -> expectedroot.toString, "runtime.parent" -> root.resolve(name).toString)
        val paths = ComponentDefaultRuntimeValues.pathValues(input)
        val rich = ComponentDefaultRuntimeValues.values(input, root.resolve("absent-home"), Vector.empty)
        paths == expected && expected.forall { case (key, value) => rich.get(key).contains(value) } &&
          ComponentDefaultRuntimeValues.expand("${runtime.parent}", paths) == root.resolve(name).toString &&
          Files.notExists(root.resolve(name)) && Files.notExists(root.resolve("absent-home"))
      }
      Then("both projections retain actual normalized paths without allocating any generated directory")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property).passed shouldBe true
      Using.resource(Files.list(root))(_.count()) shouldBe 0L
    }

    "leave the parent unavailable when the normalized runtime path is a filesystem root" in _with_root { root =>
      Given("the filesystem root, which has no parent")
      val filesystemroot = root.getRoot
      When("path-only and rich environment data are resolved")
      val paths = ComponentDefaultRuntimeValues.pathValues(filesystemroot)
      val rich = ComponentDefaultRuntimeValues.values(filesystemroot, root.resolve("absent-home"), Vector.empty)
      Then("no guessed parent is supplied and ordinary missing-token behavior is retained")
      paths shouldBe Map("runtime.root" -> filesystemroot.toString)
      Vector(paths, rich).foreach { values =>
        values.contains("runtime.parent") shouldBe false
        ComponentDefaultRuntimeValues.missing("${runtime.parent}", values) shouldBe true
        ComponentDefaultRuntimeValues.expand("${runtime.parent}", values) shouldBe "${runtime.parent}"
      }
      val process = "textus.subsystem.process-execution.plan"
      val descriptor = GenericSubsystemDescriptor(root.resolve("application.car"), "application",
        config = Map(process -> """{"anchor":"${runtime.parent}"}"""),
        componentDefaultSources = Map(process -> "application.car"))
      When("the descriptor applies its normal unresolved command-default treatment")
      val resolved = descriptor.withComponentDefaultRuntimeRoot(filesystemroot)
      Then("the unavailable parent follows the existing missing-token omission rule")
      resolved.configuration.values.contains(process) shouldBe false
      Using.resource(Files.list(root))(_.count()) shouldBe 0L
    }

    "expand parent path data in scalar JSON object and list defaults while retaining explicit overrides" in _with_root { root =>
      Given("CAR-owned defaults and explicit overrides with quoted and spaced parent path data")
      val parent = root.resolve("quoted \"parent\" with spaces")
      val runtime = parent.resolve("unused/../source")
      val scalar = "application.scalar"
      val json = "application.json"
      val structured = "application.structured"
      val overridekey = "application.override"
      val overrideobject = "application.override-object"
      val objectvalue = ConfigurationValue.ObjectValue(Map("parent" -> ConfigurationValue.StringValue("${runtime.parent}"),
        "roots" -> ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("${runtime.root}"),
          ConfigurationValue.StringValue("${runtime.parent}")))))
      val descriptor = GenericSubsystemDescriptor(root.resolve("application.car"), "application",
        config = Map(scalar -> "${runtime.parent}", json -> """{"parent":"${runtime.parent}","roots":["${runtime.root}"]}""",
          overridekey -> "${runtime.parent}"),
        _config_projection = Configuration(Map(structured -> objectvalue, overrideobject -> objectvalue)),
        componentDefaultSources = Map(scalar -> "application.car", json -> "application.car", structured -> "application.car"))
      When("the descriptor expands only its surviving component-owned path defaults")
      val resolved = descriptor.withComponentDefaultRuntimeRoot(runtime)
      Then("scalar and structured values use the normalized paths and JSON retains valid escaping")
      resolved.config(scalar) shouldBe parent.toString
      val parsed = io.circe.parser.parse(resolved.config(json)).toOption.get
      parsed.hcursor.get[String]("parent").toOption.get shouldBe parent.toString
      parsed.hcursor.get[Vector[String]]("roots").toOption.get shouldBe Vector(parent.resolve("source").toString)
      resolved.configuration.values(structured) shouldBe ConfigurationValue.ObjectValue(Map(
        "parent" -> ConfigurationValue.StringValue(parent.toString), "roots" -> ConfigurationValue.ListValue(List(
          ConfigurationValue.StringValue(parent.resolve("source").toString), ConfigurationValue.StringValue(parent.toString)))))
      resolved.config(overridekey) shouldBe "${runtime.parent}"
      resolved.configuration.values(overrideobject) shouldBe objectvalue
      Files.notExists(parent) shouldBe true
      Using.resource(Files.list(root))(_.count()) shouldBe 0L
    }

    "derive actual repository, tracking, author and existing executable data without a user Textus configuration" in _with_root { root =>
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
      val bin = _tool(root)
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

    "use linked-worktree common configuration and an ordinary included author configuration" in _with_root { root =>
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
      val values = ComponentDefaultRuntimeValues.values(nested, home, Vector(_tool(root)))
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

    "expand repository tokens in structured CAR defaults using the same typed source values" in _with_root { root =>
      Given("a CAR-owned object containing a repository token without any scalar config entries")
      val git = Files.createDirectories(root.resolve(".git"))
      Files.writeString(git.resolve("HEAD"), "ref: refs/heads/structured\n")
      val key = "application.input"
      val source = ConfigurationValue.ObjectValue(Map("branches" -> ConfigurationValue.ListValue(List(
        ConfigurationValue.StringValue("${git.branch}"))), "parent" -> ConfigurationValue.StringValue("${runtime.parent}")))
      val descriptor = GenericSubsystemDescriptor(root.resolve("application.car"), "application",
        _config_projection = Configuration(Map(key -> source)), componentDefaultSources = Map(key -> "application.car"))
      When("the owning runtime expands its surviving typed defaults")
      val resolved = descriptor.withComponentDefaultRuntimeRoot(root)
      Then("object and list traversal resolve the actual data rather than leaving a raw token")
      resolved.configuration.values(key) shouldBe ConfigurationValue.ObjectValue(Map("branches" ->
        ConfigurationValue.ListValue(List(ConfigurationValue.StringValue("structured"))),
        "parent" -> ConfigurationValue.StringValue(root.getParent.toString)))
    }

    "omit an unresolved CAR command default while retaining storage paths and Help metadata" in _with_root { root =>
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
