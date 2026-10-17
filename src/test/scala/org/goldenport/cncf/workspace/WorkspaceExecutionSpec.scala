package org.goldenport.cncf.workspace

import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
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
final class WorkspaceExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _id = WorkspaceId.parseC("project").toOption.get
  private val _owner = ComponentId("org.goldenport.spec.WorkspaceOwner")

  "Shared logical workspace admission" should {
    "accept safe logical IDs and normalized relative paths while rejecting host and traversal forms" in {
      Given("generated lowercase logical IDs and explicit unsafe ID and relative-path forms")
      val names = Gen.listOfN(8, Gen.alphaLowerChar).map(_.mkString)
      val invalidids = Vector("", "/tmp/project", "../project", "C:/project", "project/name", "project\n")
      val invalidpaths = Vector("", "/tmp/project", "C:/project", "a\\b", "a//b", ".", "..", "a/../b", "a/./b", "a/", "a\u0000b", "a\tb")

      When("the public value parsers receive those logical and path inputs")
      val property = Prop.forAll(names) { name =>
        WorkspaceId.parseC(name).toOption.exists(_.print == name) &&
          WorkspaceRelativePath.parseC(s"$name/build.sbt").toOption.exists(_.print == s"$name/build.sbt")
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property)
      val rejectedids = invalidids.map(WorkspaceId.parseC)
      val rejectedpaths = invalidpaths.map(WorkspaceRelativePath.parseC)

      Then("logical values round trip and every unsafe form fails without reflecting host paths")
      checked.passed shouldBe true
      rejectedids.foreach(_.isFaillure shouldBe true)
      rejectedpaths.foreach(_.isFaillure shouldBe true)
      rejectedids.mkString should not include "/tmp/project"
    }

    "reject duplicate bindings and grants, unknown grant targets and invalid roots" in {
      Given("one existing directory and component grant for a registered workspace")
      val root = _root()
      val grant = WorkspaceGrant(_owner, _id, WorkspacePermission.Manage)
      val file = Files.createFile(root.resolve("file"))
      try {
        When("trusted assembly receives duplicate, unknown or non-directory declarations")
        val duplicatebindings = WorkspaceExecutionAdmission.createC(Vector(_id -> root, _id -> root), Vector(grant))
        val duplicategrants = WorkspaceExecutionAdmission.createC(Vector(_id -> root), Vector(grant, grant))
        val unknown = WorkspaceExecutionAdmission.createC(Vector.empty, Vector(grant))
        val missing = WorkspaceExecutionAdmission.createC(Vector(_id -> root.resolve("missing")), Vector.empty)
        val nondirectory = WorkspaceExecutionAdmission.createC(Vector(_id -> file), Vector.empty)

        Then("assembly rejects every malformed binding before exposing an admission service")
        Vector(duplicatebindings, duplicategrants, unknown, missing, nondirectory).foreach(_.isFaillure shouldBe true)
      } finally _close(root)
    }

    "inherit one service through actual component ancestry and reject scope-label impersonation" in {
      Given("a runtime binding inherited by a real Component and a same-named generic component scope")
      val root = _root()
      val admission = _admission(root, WorkspacePermission.Manage)
      val runtime = _runtime(admission)
      val component = _component(runtime, _owner)
      val action = component.createChildScope(ScopeKind.Service, "build").createChildScope(ScopeKind.Action, "test")
      val impostor = runtime.createChildScope(ScopeKind.Component, _owner.name)
      try {
        When("workspace admission resolves each current execution scope")
        val admitted = admission.admitC(action, _id, WorkspacePermission.Manage)
        val rejected = admission.admitC(impostor, _id, WorkspacePermission.Manage)
        val execution = ExecutionContext.create().withScope(action)

        Then("the actual component keeps the same binding and shared service while the label fails")
        admitted.toOption.get should be theSameInstanceAs admission.bindingC(_id).toOption.get
        action.workspaceExecutionAdmissionOption.get should be theSameInstanceAs admission
        execution.workspaceExecutionAdmissionOption.get should be theSameInstanceAs admission
        rejected.isFaillure shouldBe true
      } finally _close(root)
    }

    "deny an unknown workspace or a different actual component owner" in {
      Given("a workspace granted only to the recorded component identity")
      val root = _root()
      val admission = _admission(root, WorkspacePermission.Manage)
      val runtime = _runtime(admission)
      val other = _component(runtime, ComponentId("org.goldenport.spec.OtherOwner"))
      try {
        When("the other component or an unknown logical target requests Manage access")
        val wrongowner = admission.admitC(other, _id, WorkspacePermission.Manage)
        val unknown = admission.admitC(_component(runtime, _owner), WorkspaceId.parseC("unknown").toOption.get, WorkspacePermission.Manage)

        Then("neither identity can select an admitted checkout")
        wrongowner.isFaillure shouldBe true
        unknown.isFaillure shouldBe true
      } finally _close(root)
    }

    "keep ReadOnly and Manage permissions distinct from Build capability grants" in {
      Given("separate ReadOnly and Manage services with no process capability installation")
      val root = _root()
      val readonly = _admission(root, WorkspacePermission.ReadOnly)
      val manage = _admission(root, WorkspacePermission.Manage)
      val readscope = _component(_runtime(readonly), _owner)
      val managescope = _component(_runtime(manage), _owner)
      try {
        When("the component requests read and build-writing workspace access")
        val read = readonly.admitC(readscope, _id, WorkspacePermission.ReadOnly)
        val denied = readonly.admitC(readscope, _id, WorkspacePermission.Manage)
        val admitted = manage.admitC(managescope, _id, WorkspacePermission.Manage)

        Then("ReadOnly cannot authorize build writes and Manage installs no process capability")
        read.isSuccess shouldBe true
        denied.isFaillure shouldBe true
        admitted.isSuccess shouldBe true
        managescope.processExecutionAdmissionOption shouldBe None
      } finally _close(root)
    }

    "canonicalize a configured root symlink and reject later retargeting or relative symlink escape" in {
      Given("two directories and a configured symlink to the initially admitted root")
      val root = _root()
      val first = Files.createDirectory(root.resolve("first"))
      val second = Files.createDirectory(root.resolve("second"))
      val configured = Files.createSymbolicLink(root.resolve("configured"), first)
      val binding = WorkspaceBinding.createC(_id, configured).toOption.get
      val relative = WorkspaceRelativePath.parseC("nested/build.sbt").toOption.get
      val escaped = WorkspaceRelativePath.parseC("escape/build.sbt").toOption.get
      Files.createSymbolicLink(first.resolve("escape"), second)
      try {
        When("logical paths resolve and the configured symlink is subsequently retargeted")
        val canonical = binding.workingDirectoryC
        val resolved = binding.resolveRelativeC(relative)
        val rejected = binding.resolveRelativeC(escaped)
        Files.delete(configured)
        Files.createSymbolicLink(configured, second)
        val retargeted = binding.workingDirectoryC

        Then("the original canonical root is used and escape or retargeting produces a safe failure")
        canonical.toOption shouldBe Some(first.toRealPath())
        resolved.toOption shouldBe Some(first.toRealPath().resolve(relative.value))
        rejected.isFaillure shouldBe true
        retargeted.isFaillure shouldBe true
        retargeted.toString should not include root.toString
        binding.toString shouldBe "WorkspaceBinding(project)"
      } finally _close(root)
    }
  }

  private def _root(): Path = {
    val base = Files.createDirectories(Paths.get("target", "workspace-execution-spec").toAbsolutePath)
    Files.createTempDirectory(base, "case-")
  }

  private def _close(root: Path): Unit =
    Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }

  private def _admission(root: Path, permission: WorkspacePermission): WorkspaceExecutionAdmission =
    WorkspaceExecutionAdmission.createC(Vector(_id -> root), Vector(WorkspaceGrant(_owner, _id, permission))).toOption.get

  private def _runtime(admission: WorkspaceExecutionAdmission): ScopeContext =
    ScopeContext(ScopeKind.Runtime, "workspace-spec", None, ExecutionContext.create().observability,
      workspaceExecutionAdmissionOption = Some(admission))

  private def _component(parent: ScopeContext, owner: ComponentId): Component.Context = {
    val component = Component.create(owner.name, owner, ComponentInstanceId.default(owner), Protocol.empty)
    Component.Context(owner.name, parent, component, ComponentOrigin.Unknown)
  }
}
