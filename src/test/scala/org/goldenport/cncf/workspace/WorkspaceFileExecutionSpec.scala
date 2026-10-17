package org.goldenport.cncf.workspace

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFileAttributeView
import java.util.Comparator
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.effect.ExternalEffectKind
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkspaceFileExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _id = WorkspaceId.parseC("project").toOption.get
  private val _target = FileTarget(_id)
  private val _owner = ComponentId("org.goldenport.spec.FileOwner")

  "Literal workspace filenames" should {
    "retain literal control characters and Unicode while refusing absolute and traversal forms" in {
      Given("literal POSIX names and explicit host, empty-segment and traversal inputs")
      val names = Vector(" space ", "line\nname", "tab\tname", "back\\slash", "C:literal", "日本/語.txt")
      val invalid = Vector(null, "", "/host", "C:/host", "C:\\host", "\\\\server\\file", "a//b", "a/", ".", "..", "a/../b", "a/./b", "a\u0000b")
      val generated = Gen.listOfN(8, Gen.oneOf("a", "日本", "\n", "\t", "\\", ":", " ")).map(_.mkString)
      When("the literal File path parser consumes each input without normalization")
      val accepted = names.map(WorkspaceFilePath.parseC)
      val rejected = invalid.map(WorkspaceFilePath.parseC)
      val property = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), Prop.forAll(generated) { name =>
        WorkspaceFilePath.parseC("prefix" + name).toOption.exists(_.value == "prefix" + name)
      })
      Then("exact names round trip while every unsafe shape fails privately")
      accepted.flatMap(_.toOption.map(_.value)) shouldBe names
      rejected.foreach(_.isFaillure shouldBe true)
      property.passed shouldBe true
      rejected.mkString should not include "server"
    }
  }

  "Complete bounded File reads" should {
    "distinguish absence from empty text and preserve literal filenames, Unicode and final newline without side effects" in {
      Given("an admitted canonical directory with empty and literal-named UTF-8 files")
      _with_root { root =>
        val text = "日本語—🚀\n"
        val names = Vector("space name", "line\nname", "tab\tname", "back\\slash", "colon:name")
        names.foreach(name => Files.writeString(root.resolve(name), text, StandardCharsets.UTF_8))
        Files.createFile(root.resolve("empty"))
        val context = _context(root)
        val before = _names(root)
        When("the current adapter reads absent, empty and literal-named files")
        val absent = WorkspaceFileExecution.readTextC(context, _target, _path("absent"))
        val empty = WorkspaceFileExecution.readTextC(context, _target, _path("empty"))
        val results = names.map(name => WorkspaceFileExecution.readTextC(context, _target, _path(name)))
        Then("absence and empty remain distinct and read creates no lock or staging file")
        absent.toOption.get.text shouldBe None
        empty.toOption.get.text shouldBe Some("")
        results.foreach { result =>
          result.toOption.get.text shouldBe Some(text)
          result.toOption.get.effect.kind shouldBe ExternalEffectKind.FileRead
          result.toString should not include text
          result.toString should not include root.toString
        }
        names.foreach(name => Files.readString(root.resolve(name), StandardCharsets.UTF_8) shouldBe text)
        _names(root) shouldBe before
      }
    }

    "accept exact encoded byte caps and refuse overflow and malformed UTF-8 without partial text" in {
      Given("complete files at and above four bytes plus malformed private UTF-8 bytes")
      _with_root { root =>
        Files.writeString(root.resolve("equal"), "éé", StandardCharsets.UTF_8)
        val excess = Files.writeString(root.resolve("excess"), "éé\n", StandardCharsets.UTF_8)
        val malformed = Files.write(root.resolve("malformed"), Array(0xc3.toByte, 0x28.toByte))
        val context = _context(root, cap = 4)
        When("all three sources are read under the current four-byte runtime limit")
        val equal = WorkspaceFileExecution.readTextC(context, _target, _path("equal"))
        val refused = Vector("excess", "malformed").map(name => WorkspaceFileExecution.readTextC(context, _target, _path(name)))
        Then("equality is complete and failures expose neither partial text nor physical locations")
        equal.toOption.get.text shouldBe Some("éé")
        refused.foreach { result =>
          result.isFaillure shouldBe true
          result.toOption shouldBe None
          result.toString should not include root.toString
          result.toString should not include "éé"
        }
        Files.readAllBytes(excess).toVector shouldBe "éé\n".getBytes(StandardCharsets.UTF_8).toVector
        Files.readAllBytes(malformed).toVector shouldBe Vector(0xc3.toByte, 0x28.toByte)
      }
    }
  }

  "Current workspace authority" should {
    "allow a ReadOnly read and deny writes, unknown targets, wrong owners and missing services before mutation" in {
      Given("a private file, a ReadOnly owner and contexts missing actual ownership, service or global limits")
      _with_root { root =>
        val file = Files.writeString(root.resolve("private"), "secret\n", StandardCharsets.UTF_8)
        val readonly = _context(root, permission = WorkspacePermission.ReadOnly)
        val wrong = _context(root, owner = ComponentId("org.goldenport.spec.OtherFileOwner"))
        val missingservice = ExecutionContext.create()
        val missingglobal = _context(root, global = false)
        val allowed = _context(root)
        val global = allowed.cncfCore.scope.parent.get
        val impostor = allowed.withScope(global.createChildScope(ScopeKind.Component, _owner.name))
        val before = _names(root)
        When("each current context attempts its admitted or denied File operation")
        val read = WorkspaceFileExecution.readTextC(readonly, _target, _path("private"))
        val denied = Vector(readonly, wrong, missingservice, missingglobal, impostor).map(context =>
          WorkspaceFileExecution.writeTextC(context, _target, _path("new"), "replacement", FileWriteMode.CreateNew))
        val unknown = WorkspaceFileExecution.readTextC(allowed, FileTarget(WorkspaceId.parseC("unknown").toOption.get), _path("private"))
        Then("only the granted read succeeds and every denied operation leaves the directory unchanged")
        read.toOption.get.text shouldBe Some("secret\n")
        denied.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include root.toString
          result.toString should not include "replacement"
        }
        unknown.isFaillure shouldBe true
        _names(root) shouldBe before
        Files.readString(file, StandardCharsets.UTF_8) shouldBe "secret\n"
      }
    }

    "refuse final and ancestor links, nonregular files, missing parents and forged traversal values" in {
      Given("an admitted directory with links, a directory leaf and a regular-file parent")
      _with_root { root =>
        val nested = Files.createDirectory(root.resolve("nested"))
        val source = Files.writeString(nested.resolve("private"), "private content", StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("link"), source)
        Files.createSymbolicLink(root.resolve("ancestor"), nested)
        Files.writeString(root.resolve("plain"), "parent file", StandardCharsets.UTF_8)
        val context = _context(root)
        val paths = Vector("link", "ancestor/private", "nested", "missing/leaf", "plain/leaf").map(_path) :+
          _forged_path("../escape")
        val before = _names(root)
        When("reads and writes select each disallowed path")
        val reads = paths.map(WorkspaceFileExecution.readTextC(context, _target, _))
        val writes = paths.map(WorkspaceFileExecution.writeTextC(context, _target, _, "new", FileWriteMode.CreateNew))
        Then("policy failures return no file payload or mutations, including reflected constructor bypasses")
        reads.foreach(_.isFaillure shouldBe true)
        writes.foreach(_.isFaillure shouldBe true)
        (reads ++ writes).foreach(_.toString should not include root.toString)
        Files.readString(source, StandardCharsets.UTF_8) shouldBe "private content"
        _names(root) shouldBe before
      }
    }

    "reject a missing or retargeted admitted root without following the replacement" in {
      Given("a binding canonicalized through a configured root alias and another ordinary directory")
      _with_root { root =>
        val first = Files.createDirectory(root.resolve("first"))
        val second = Files.createDirectory(root.resolve("second"))
        Files.writeString(first.resolve("file"), "original", StandardCharsets.UTF_8)
        Files.writeString(second.resolve("file"), "replacement", StandardCharsets.UTF_8)
        val alias = Files.createSymbolicLink(root.resolve("configured"), first)
        val context = _context(alias)
        val missingcontext = _context(first)
        When("the configured alias is retargeted and the other admitted directory is removed")
        Files.delete(alias)
        Files.createSymbolicLink(alias, second)
        val retargeted = WorkspaceFileExecution.readTextC(context, _target, _path("file"))
        Files.delete(first.resolve("file"))
        Files.delete(first)
        val missing = WorkspaceFileExecution.writeTextC(missingcontext, _target, _path("new"), "new", FileWriteMode.CreateNew)
        Then("both operations fail without selecting or modifying the replacement root")
        retargeted.isFaillure shouldBe true
        missing.isFaillure shouldBe true
        Files.readString(second.resolve("file"), StandardCharsets.UTF_8) shouldBe "replacement"
        _names(second) shouldBe Set("file")
      }
    }
  }

  "Atomic File write modes" should {
    "create only absent ordinary leaves and preserve occupied files even when their text is equal" in {
      Given("an admitted existing parent, an unselected file and a Unicode replacement with final newline")
      _with_root { root =>
        val context = _context(root)
        val text = "日本—🚀\n"
        val untouched = Files.writeString(root.resolve("unselected"), "keep", StandardCharsets.UTF_8)
        When("CreateNew installs a new file and subsequently competes with equal and unequal existing text")
        val created = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), text, FileWriteMode.CreateNew)
        val equal = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), text, FileWriteMode.CreateNew)
        val unequal = WorkspaceFileExecution.writeTextC(context, _target, _path("new"), "other", FileWriteMode.CreateNew)
        Then("only the absent leaf is installed, later attempts report AlreadyExists and staging is removed")
        created.toOption.get.outcome shouldBe FileWriteOutcome.Created
        created.toOption.get.effect.kind shouldBe ExternalEffectKind.FileWrite
        Vector(equal, unequal).foreach(_.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
        Files.readAllBytes(root.resolve("new")).toVector shouldBe text.getBytes(StandardCharsets.UTF_8).toVector
        Files.readString(untouched, StandardCharsets.UTF_8) shouldBe "keep"
        _names(root) shouldBe Set("new", "unselected")
      }
    }

    "update only exact expected text while preserving bytes, final newline and supported POSIX permissions" in {
      Given("an existing ordinary file with known expected text and its supported POSIX mode")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "before\n", StandardCharsets.UTF_8)
        val view = Files.getFileAttributeView(path, classOf[PosixFileAttributeView])
        val permissions = Option(view).map(_.readAttributes().permissions())
        val context = _context(root)
        val text = "更新🚀\n"
        When("UpdateExisting receives missing, changed and exactly matching preconditions")
        val missing = WorkspaceFileExecution.writeTextC(context, _target, _path("missing"), text, FileWriteMode.UpdateExisting("before\n"))
        val changed = WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, FileWriteMode.UpdateExisting("wrong"))
        val unchanged = Files.readString(path, StandardCharsets.UTF_8)
        val updated = WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, FileWriteMode.UpdateExisting("before\n"))
        Then("only equality installs the replacement and every temporary file is reclaimed")
        missing.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.Missing)
        changed.toOption.get.outcome shouldBe FileWriteOutcome.Conflict(FileWriteConflict.Changed)
        unchanged shouldBe "before\n"
        updated.toOption.get.outcome shouldBe FileWriteOutcome.Updated
        Files.readAllBytes(path).toVector shouldBe text.getBytes(StandardCharsets.UTF_8).toVector
        permissions.foreach(value => Files.getPosixFilePermissions(path) shouldBe value)
        _names(root) shouldBe Set("edit")
      }
    }

    "refuse malformed, null or over-cap expected and replacement text before staging" in {
      Given("a current four-byte cap and an unchanged ordinary file")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "old\n", StandardCharsets.UTF_8)
        val context = _context(root, cap = 4)
        val cases = Vector(
          ("large", FileWriteMode.CreateNew), ("\uD800", FileWriteMode.CreateNew),
          (null, FileWriteMode.CreateNew), ("new", FileWriteMode.UpdateExisting("large")),
          ("new", FileWriteMode.UpdateExisting("\uDC00")), ("new", FileWriteMode.UpdateExisting(null)),
          ("new", null)
        )
        When("the adapter validates each invalid typed write under that runtime policy")
        val results = cases.map { case (text, mode) => WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text, mode) }
        Then("no invalid write or expected value reaches staging and diagnostics omit private payloads")
        results.foreach { result =>
          result.isFaillure shouldBe true
          result.toString should not include root.toString
          result.toString should not include "large"
        }
        Files.readString(path, StandardCharsets.UTF_8) shouldBe "old\n"
        _names(root) shouldBe Set("edit")
      }
    }

    "serialize cooperating updates on the same binding so one expected-text contender wins" in {
      Given("one binding, a common expected text and two bounded thread contenders")
      _with_root { root =>
        val path = Files.writeString(root.resolve("edit"), "before", StandardCharsets.UTF_8)
        val context = _context(root)
        val executor = Executors.newFixedThreadPool(2)
        val ready = new CountDownLatch(2)
        val start = new CountDownLatch(1)
        try {
          When("both contenders request an update using that same current binding")
          val futures = Vector("first\n", "second\n").map { text => executor.submit(new Callable[(String, FileWriteOutcome)] {
            def call(): (String, FileWriteOutcome) = {
              ready.countDown()
              if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("bounded start did not arrive")
              text -> WorkspaceFileExecution.writeTextC(context, _target, _path("edit"), text,
                FileWriteMode.UpdateExisting("before")).toOption.get.outcome
            }
          }) }
          val prepared = ready.await(5, TimeUnit.SECONDS)
          start.countDown()
          val outcomes = futures.map(_.get(10, TimeUnit.SECONDS))
          Then("exactly one update succeeds and the other observes Changed, with only the winner's bytes installed")
          prepared shouldBe true
          outcomes.map(_._2).count(_ == FileWriteOutcome.Updated) shouldBe 1
          outcomes.map(_._2).count(_ == FileWriteOutcome.Conflict(FileWriteConflict.Changed)) shouldBe 1
          Files.readString(path, StandardCharsets.UTF_8) shouldBe outcomes.find(_._2 == FileWriteOutcome.Updated).get._1
          _names(root) shouldBe Set("edit")
        } finally {
          start.countDown()
          executor.shutdownNow()
          executor.awaitTermination(5, TimeUnit.SECONDS)
        }
      }
    }
  }

  private def _path(value: String): WorkspaceFilePath = WorkspaceFilePath.parseC(value).toOption.get

  private def _forged_path(value: String): WorkspaceFilePath = {
    val constructor = classOf[WorkspaceFilePath].getDeclaredConstructor(classOf[String])
    constructor.setAccessible(true)
    constructor.newInstance(value)
  }

  private def _context(
    root: Path,
    permission: WorkspacePermission = WorkspacePermission.Manage,
    owner: ComponentId = _owner,
    cap: Int = 128,
    global: Boolean = true
  ): ExecutionContext = {
    val execution = ExecutionContext.create()
    val admission = WorkspaceExecutionAdmission.createC(Vector(_id -> root), Vector(WorkspaceGrant(_owner, _id, permission))).toOption.get
    val runtime = if (global) GlobalRuntimeContext.create("file-spec", RuntimeConfig.default.copy(resourceReadLimits = ResourceReadLimits(cap)),
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty), execution.observability, AliasResolver.empty,
      workspaceAdmission = Some(admission))
    else ScopeContext(ScopeKind.Runtime, "no-global", None, execution.observability, workspaceExecutionAdmissionOption = Some(admission))
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    execution.withScope(Component.Context(owner.name, runtime, component, ComponentOrigin.Unknown))
  }

  private def _with_root(body: Path => Unit): Unit = {
    val base = Files.createDirectories(Paths.get("target", "workspace-file-execution-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-").toRealPath()
    try body(root) finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }

  private def _names(root: Path): Set[String] =
    Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toSet)
}
