package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Paths}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  4, 2026
 * @version Nov.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitPurposeProjectionCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Session candidate binding" should {
    "materialize with typed Git and run typed SBT in the same isolated workspace" in {
      Given("an explicitly granted source, managed parent, eligible alias and typed Git and SBT programs")
      val driver = new LocalProcessExecutionDriver()
      val fixture = GitExecutionSpecSupport.create(driver = Some(driver))
      val support = GitExecutionSpecSupport
      val parent = WorkspaceId.parseC("candidate-parent").toOption.get
      val alias = WorkspaceId.parseC("candidate-alias").toOption.get
      val managed = Files.createDirectory(fixture.root.resolve("managed"))
      val workspace = WorkspaceExecutionAdmission.createC(Vector(support.workspaceId -> fixture.checkout,
        parent -> managed, alias -> fixture.checkout), Vector(support.workspaceId, parent, alias).map(id =>
        WorkspaceGrant(support.owner, id, WorkspacePermission.Manage)),
        Vector(SessionCandidateWorkspacePolicy(support.owner, parent, alias))).toOption.get
      val oldsource = fixture.workspace.bindingC(support.workspaceId).toOption.get
      val source = workspace.bindingC(support.workspaceId).toOption.get
      val aliasbinding = workspace.bindingC(alias).toOption.get
      val gitdefs = fixture.definitions.map(ProcessProgramDefinition.rebindWorkspace(_, oldsource, source))
      val observations = Vector(GitOperationKind.Root, GitOperationKind.Head).map { kind =>
        val declaration = support.declaration(kind, fixture.endpoint).copy(target = alias,
          capability = ProcessCapabilityId.parseC("candidate-" + kind.wireName).toOption.get,
          identity = "candidate-" + kind.wireName)
        support.definition(aliasbinding, declaration).toOption.get
      }
      val sbtcap = ProcessCapabilityId.parseC("candidate-compile").toOption.get
      val sbt = SbtProgramDefinition.createC(aliasbinding, SbtOperation.Compile, sbtcap, "candidate-compile",
        Paths.get("/usr/bin/true"), support.limits, Map.empty).toOption.get
      val definitions = gitdefs ++ observations :+ sbt
      val policy = ProcessExecutionPolicy.createC(definitions).toOption.get
      val admission = ProcessExecutionAdmission.forComponentsC(policy,
        Map(support.owner -> definitions.map(x => ProcessExecutionGrant(x.capability)))).toOption.get
      val context = support.contextFor(fixture.root, Some(workspace), Some(admission), driver)
      val preparation = new UnitOfWork(context)
      val selected = try new UnitOfWorkInterpreter(preparation).interpret(UnitOfWorkOp.SessionCandidatePrepare(
        support.workspaceId, parent, alias, "session-1", "candidate-1")).toOption.get finally preparation.dispose()
      val restored = SessionCandidateWorkspaceReference.fromRecordC(selected.reference.record).toOption.get
      val freshcontext = support.contextFor(fixture.root, Some(workspace), Some(admission), driver)
      val reacquired = SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record, SessionCandidateWorkspaceState.Empty).toOption.get
      val duplicate = new UnitOfWork(freshcontext)
      val duplicateattempt = try new UnitOfWorkInterpreter(duplicate).interpret(UnitOfWorkOp.SessionCandidatePrepare(
        support.workspaceId, parent, alias, "session-1", "candidate-1")) finally duplicate.dispose()
      duplicateattempt.isFaillure shouldBe true
      SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record, SessionCandidateWorkspaceState.Unknown).isFaillure shouldBe true
      val second = new UnitOfWork(freshcontext)
      val other = try new UnitOfWorkInterpreter(second).interpret(UnitOfWorkOp.SessionCandidatePrepare(
        support.workspaceId, parent, alias, "session-2", "candidate-1")).toOption.get finally second.dispose()
      val firstpath = reacquired.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get
      val otherpath = other.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get
      firstpath should not be otherpath
      val scoped = fixture.copy(context = reacquired.context)
      try {
        support.initialize(fixture, Map("selected.txt" -> "base\n"))
        Files.writeString(fixture.checkout.resolve("selected.txt"), "candidate\nexcluded\n")
        val head = support.execute(scoped, GitOperation.Head).toOption.get.valueC.toOption.get
        val index = support.execute(scoped, GitOperation.IndexTree).toOption.get.valueC.toOption.get
        val request = GitPurposeRequest(head, index, Vector(GitPurposeChange(GitPath.parseC("selected.txt").toOption.get,
          Some("candidate\nexcluded\n"), Some("candidate\n"))))
        val candidate = support.execute(scoped, GitOperation.PreparePurposeCandidate(support.selectionId, request))
          .toOption.get.valueC.toOption.get
        When("native Git materializes the candidate and the native SBT UoW targets its alias")
        val materialized = support.execute(scoped, GitOperation.MaterializePurposeCandidate(support.selectionId, candidate, alias)).toOption.get
        val candidatepath = reacquired.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get
        val uow = new UnitOfWork(reacquired.context)
        val result = try new UnitOfWorkInterpreter(uow).interpret(UnitOfWorkOp.SbtExec(SbtTarget(alias), SbtOperation.Compile))
          finally uow.dispose()
        Then("both use the candidate root and the source remains unchanged")
        materialized.outcome shouldBe GitExecutionOutcome.Succeeded
        Files.readString(candidatepath.resolve("selected.txt")) shouldBe "candidate\n"
        Files.readString(fixture.checkout.resolve("selected.txt")) shouldBe "candidate\nexcluded\n"
        result.toOption.get.outcome shouldBe SbtExecutionOutcome.Succeeded
        val sbtexecution = SbtExecutionAdmission.resolveC(reacquired.context, SbtTarget(alias), SbtOperation.Compile).toOption.get
        sbtexecution.definition._sbt_metadata.get.binding.workingDirectoryC.toOption shouldBe Some(candidatepath)
        SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record, SessionCandidateWorkspaceState.Empty).isFaillure shouldBe true
        val observation = SessionCandidateWorkspace.observationContextC(freshcontext, restored, selected.reference.record).toOption.get
        val observer = fixture.copy(context = observation, target = GitTarget(alias))
        val observedroot = support.execute(observer, GitOperation.Root).toOption.get
        val observedhead = support.execute(observer, GitOperation.Head).toOption.get
        val known = SessionCandidateWorkspaceState.Materialized(materialized, observedroot, observedhead)
        SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record, known).isSuccess shouldBe true
      } finally { driver.close(); fixture.close() }
    }
  }
  "E1 Purpose projection JVM compatibility" should afterWord(
    "in spec:typed-git-execution, example:E1, rules:Purpose-specific-candidates, phase:sync-runtime-20261104-08") {
    "preserve the legacy three-argument JVM calls while retaining an explicit projection on copy" in {
      Given("the exact constructor, companion apply and copy signatures used by already compiled callers")
      val path = GitPath.parseC("selected.txt").toOption.get
      val parameters = Array(classOf[GitPath], classOf[Option[?]], classOf[Option[?]])
      val constructor = classOf[GitPurposeChange].getConstructor(parameters*)
      val applymethod = GitPurposeChange.getClass.getMethod("apply", parameters*)
      val copymethod = classOf[GitPurposeChange].getMethod("copy", parameters*)
      val projected = new GitPurposeChange(path, Some("captured\n"), Some("current\n"),
        Some(GitPurposeWorktreeProjection(Some("integrated\n"))))
      When("those legacy JVM signatures are invoked directly rather than recompiled with a fourth default argument")
      val constructed = constructor.newInstance(path, Some("captured\n"), Some("current\n"))
      val applied = applymethod.invoke(GitPurposeChange, path, Some("captured\n"), Some("current\n"))
        .asInstanceOf[GitPurposeChange]
      val copied = copymethod.invoke(projected, path, Some("captured\n"), Some("corrected\n"))
        .asInstanceOf[GitPurposeChange]
      Then("legacy creation means absent projection and an existing explicit projection survives legacy copying")
      constructed.worktreeProjection shouldBe None
      applied shouldBe constructed
      copied.content shouldBe Some("corrected\n")
      copied.worktreeProjection shouldBe projected.worktreeProjection
      classOf[GitPurposeChange].getMethod("copy$default$1").invoke(projected) shouldBe path
      classOf[GitPurposeChange].getMethod("copy$default$2").invoke(projected) shouldBe projected.expectedWorktree
      classOf[GitPurposeChange].getMethod("copy$default$3").invoke(projected) shouldBe projected.content
      val unapplymethod = GitPurposeChange.getClass.getMethod("unapply", classOf[GitPurposeChange])
      unapplymethod.getReturnType shouldBe classOf[GitPurposeChange]
      unapplymethod.invoke(GitPurposeChange, projected) shouldBe projected
    }
  }
}
