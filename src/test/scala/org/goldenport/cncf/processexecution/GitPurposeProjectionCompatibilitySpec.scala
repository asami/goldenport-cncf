package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov.  4, 2026
 * @version Nov. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitPurposeProjectionCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Session candidate binding" should {
    "use the owning Procedure interpreter" which {
    "materializes with typed Git and launches typed SBT in the same isolated workspace" in {
      Given("an explicitly granted source, managed parent, eligible alias and typed Git and SBT programs")
      val driver = new LocalProcessExecutionDriver()
      val fixture = GitExecutionSpecSupport.create(driver = Some(driver))
      val support = GitExecutionSpecSupport
      val parent = WorkspaceId.parseC("candidate-parent").toOption.get
      val alias = WorkspaceId.parseC("candidate-alias").toOption.get
      val managed = Files.createDirectory(fixture.root.resolve("managed"))
      val placeholder = Files.createDirectory(fixture.root.resolve("candidate-alias"))
      val workspace = WorkspaceExecutionAdmission.createC(Vector(support.workspaceId -> fixture.checkout,
        parent -> managed, alias -> placeholder), Vector(support.workspaceId, parent, alias).map(id =>
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
      // An explicit fixture executable proves the typed launch cwd without a nested SBT build.
      val executable = Files.writeString(fixture.root.resolve("sbt-cwd"), "#!/bin/sh\n/bin/pwd -P\n")
      executable.toFile.setExecutable(true) shouldBe true
      val sbt = SbtProgramDefinition.createC(aliasbinding, SbtOperation.Compile, sbtcap, "candidate-compile",
        executable, support.limits, Map.empty).toOption.get
      val definitions = gitdefs ++ observations :+ sbt
      val policy = ProcessExecutionPolicy.createC(definitions).toOption.get
      val admission = ProcessExecutionAdmission.forComponentsC(policy,
        Map(support.owner -> definitions.map(x => ProcessExecutionGrant(x.capability)))).toOption.get
      val base = support.contextFor(fixture.root, Some(workspace), Some(admission), driver)
      val context = ExecutionContext.withActionScope(base, base.scope)
      val freshbase = support.contextFor(fixture.root, Some(workspace), Some(admission), driver)
      val freshcontext = ExecutionContext.withActionScope(freshbase, freshbase.scope)
      context.runtime.setResolvedParameters(context.runtime.resolvedParameters)
      freshcontext.runtime.setResolvedParameters(freshcontext.runtime.resolvedParameters)
      try {
        support.initialize(fixture, Map("selected.txt" -> "base\n"))
        Files.writeString(fixture.checkout.resolve("selected.txt"), "candidate\nexcluded\n")
        context.runtime.noteResponseJobId("owning-session")

        When("the owning Procedure prepares a candidate and the retained empty reference is reacquired")
        val selected = _run(context, UnitOfWorkOp.SessionCandidatePrepare(
          support.workspaceId, parent, alias, "session-1", "candidate-1")).toOption.get
        val restored = SessionCandidateWorkspaceReference.fromRecordC(selected.reference.record).toOption.get
        val reacquired = SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record,
          SessionCandidateWorkspaceState.Empty).toOption.get
        val duplicateattempt = _run(freshcontext, UnitOfWorkOp.SessionCandidatePrepare(
          support.workspaceId, parent, alias, "session-1", "candidate-1"))
        val unknown = SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record,
          SessionCandidateWorkspaceState.Unknown)
        val other = _run(freshcontext, UnitOfWorkOp.SessionCandidatePrepare(
          support.workspaceId, parent, alias, "session-2", "candidate-1")).toOption.get
        val candidatepath = selected.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get
        val otherpath = other.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get

        Then("candidate selection retains framework ownership and isolates sessions while refusing occupied or unknown state")
        context.unitOfWork.executionContext should be theSameInstanceAs context
        freshcontext.unitOfWork.executionContext should be theSameInstanceAs freshcontext
        _retains_binding(selected.context, context, support.workspaceId)
        _retains_binding(reacquired.context, freshcontext, support.workspaceId)
        duplicateattempt.isFaillure shouldBe true
        unknown.isFaillure shouldBe true
        candidatepath should not be otherpath
        reacquired.context.workspaceExecutionAdmissionOption.get.bindingC(alias).toOption.get.workingDirectoryC.toOption.get shouldBe candidatepath

        When("the selected Procedure observes the source and materializes its purpose candidate")
        val head = _run(selected.context, UnitOfWorkOp.GitExec(fixture.target, GitOperation.Head)).toOption.get.valueC.toOption.get
        val index = _run(selected.context, UnitOfWorkOp.GitExec(fixture.target, GitOperation.IndexTree)).toOption.get.valueC.toOption.get
        val request = GitPurposeRequest(head, index, Vector(GitPurposeChange(GitPath.parseC("selected.txt").toOption.get,
          Some("candidate\nexcluded\n"), Some("candidate\n"))))
        val candidate = _run(selected.context, UnitOfWorkOp.GitExec(fixture.target,
          GitOperation.PreparePurposeCandidate(support.selectionId, request)))
          .toOption.get.valueC.toOption.get
        val materialized = _run(selected.context, UnitOfWorkOp.GitExec(fixture.target,
          GitOperation.MaterializePurposeCandidate(support.selectionId, candidate, alias))).toOption.get
        val occupied = SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record,
          SessionCandidateWorkspaceState.Empty)

        Then("materialization uses the managed child, leaves the source content intact and never populates the original alias")
        materialized.outcome shouldBe GitExecutionOutcome.Succeeded
        Files.readString(candidatepath.resolve("selected.txt")) shouldBe "candidate\n"
        Files.readString(fixture.checkout.resolve("selected.txt")) shouldBe "candidate\nexcluded\n"
        val entries = Files.list(placeholder)
        try entries.findAny().isPresent shouldBe false finally entries.close()
        occupied.isFaillure shouldBe true

        When("the returned observation Procedure supplies typed Git evidence for materialized reacquisition")
        val observation = SessionCandidateWorkspace.observationContextC(freshcontext, restored, selected.reference.record).toOption.get
        val observedroot = _run(observation, UnitOfWorkOp.GitExec(GitTarget(alias), GitOperation.Root)).toOption.get
        val observedhead = _run(observation, UnitOfWorkOp.GitExec(GitTarget(alias), GitOperation.Head)).toOption.get
        val known = SessionCandidateWorkspaceState.Materialized(materialized, observedroot, observedhead)
        val resumed = SessionCandidateWorkspace.reacquireC(freshcontext, restored, selected.reference.record, known)

        Then("actual observations match the managed path and candidate commit and normal materialized reacquisition succeeds")
        _retains_binding(observation, freshcontext, support.workspaceId)
        observedroot.valueC.toOption shouldBe Some(candidatepath.toString)
        observedhead.valueC.toOption shouldBe Some(candidate.commit)
        resumed.isSuccess shouldBe true
        val resumedcontext = resumed.toOption.get.context
        _retains_binding(resumedcontext, freshcontext, support.workspaceId)

        When("the returned reacquired Procedure launches the typed SBT executable and records synchronous diagnostics")
        val result = _run(resumedcontext, UnitOfWorkOp.SbtExec(SbtTarget(alias), SbtOperation.Compile)).toOption.get
        resumedcontext.runtime.noteDebugJobId("candidate-launch")
        selected.context.runtime.noteDebugJobId("selected-materialization")

        Then("captured physical cwd is the managed child and each synchronous child shares its owner's metadata")
        result.outcome shouldBe SbtExecutionOutcome.Succeeded
        new String(result.processResult.stdout.content.toArray, StandardCharsets.UTF_8).trim shouldBe candidatepath.toRealPath().toString
        context.runtime.executionMetadata.responseJobId shouldBe Some("owning-session")
        context.runtime.executionMetadata.debugJobId shouldBe Some("selected-materialization")
        freshcontext.runtime.executionMetadata.debugJobId shouldBe Some("candidate-launch")
        reacquired.context.runtime.executionMetadata shouldBe freshcontext.runtime.executionMetadata
        observation.runtime.executionMetadata shouldBe freshcontext.runtime.executionMetadata
      } finally { driver.close(); fixture.close() }
    }
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

  private def _run[A](context: ExecutionContext, operation: UnitOfWorkOp[A]): Consequence[A] =
    _procedure(context).run(ConsequenceT.liftF(Free.liftF(operation)))

  private def _procedure(context: ExecutionContext): WorkspaceProcedure = {
    val action = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall = new WorkspaceProcedure(core)
      override def request: Request = Request(component = None, service = None, operation = "candidate_workspace",
        arguments = Nil, switches = Nil, properties = Nil)
    }
    new WorkspaceProcedure(ActionCall.Core(action, context, None, None))
  }

  private def _retains_binding(child: ExecutionContext, owner: ExecutionContext, source: WorkspaceId): Unit = {
    child.unitOfWork.executionContext should be theSameInstanceAs child
    child.unitOfWork should not be theSameInstanceAs(owner.unitOfWork)
    child.unitOfWork should be theSameInstanceAs child.unitOfWork
    child.workspaceExecutionAdmissionOption.get.bindingC(source).toOption shouldBe
      owner.workspaceExecutionAdmissionOption.get.bindingC(source).toOption
    child.scope.parent.get should be theSameInstanceAs owner.scope
    child.core should be theSameInstanceAs owner.core
    child.security should be theSameInstanceAs owner.security
    child.framework shouldBe owner.framework
    child.idGeneration should be theSameInstanceAs owner.idGeneration
    child.executionControl should be theSameInstanceAs owner.executionControl
    child.entitySpace should be theSameInstanceAs owner.entitySpace
    child.dataStoreSpace should be theSameInstanceAs owner.dataStoreSpace
    child.entityStoreSpace should be theSameInstanceAs owner.entityStoreSpace
    child.unitOfWork.httpDriver should be theSameInstanceAs owner.unitOfWork.httpDriver
    child.cncfCore.resources should be theSameInstanceAs owner.cncfCore.resources
    child.cncfCore.resourceTrees should be theSameInstanceAs owner.cncfCore.resourceTrees
    child.runtime.toToken shouldBe owner.runtime.toToken
    child.runtime.context should be theSameInstanceAs owner.runtime.context
    child.runtime.operationMode shouldBe owner.runtime.operationMode
    child.runtime.resolvedParameters should be theSameInstanceAs owner.runtime.resolvedParameters
    child.runtime.transitionValidationHook should be theSameInstanceAs owner.runtime.transitionValidationHook
    child.runtime.entityCreateDefaultsPolicy should be theSameInstanceAs owner.runtime.entityCreateDefaultsPolicy
    child.runtime.executionMetadata shouldBe owner.runtime.executionMetadata
  }

  private final class WorkspaceProcedure(val core: ActionCall.Core) extends ProcedureActionCall with ActionCall.Core.Holder {
    def run[A](program: ExecUowM[A]): Consequence[A] = executeProgram(program)
    override def execute(): Consequence[OperationResponse] = Consequence.success(OperationResponse.Scalar("candidate workspace"))
  }
}
