package org.goldenport.cncf.action

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ScopeKind
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.processexecution.GitExecutionSpecSupport.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp, UnitOfWorkProgramRecorder, UnitOfWorkProgramStubProvider}
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Protected typed Git DSL" should {
    "return clean and exact whitespace-finding index facts through normal local Git and the typed UoW" in {
      Given("a temporary normal Git checkout with an initial committed fixed selected file")
      val driver = new LocalProcessExecutionDriver()
      val fixture = create(driver = Some(driver))
      setup(fixture.checkout, "init", "--initial-branch=main")
      setup(fixture.checkout, "config", "user.name", "Fixture")
      setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
      Files.writeString(fixture.checkout.resolve("selected.txt"), "initial\n")
      setup(fixture.checkout, "add", "--", "selected.txt")
      setup(fixture.checkout, "commit", "-m", "fixture initial")
      try {
        When("typed Head supplies the base and typed CheckPendingIndex observes the unchanged index")
        val base = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val clean = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckPendingIndex(base)).toOption.get
        Then("the actual exit-zero empty receipt yields a clean typed check")
        clean.outcome shouldBe GitExecutionOutcome.Succeeded
        clean.valueC.toOption shouldBe Some(GitIndexCheck(Set.empty))
        clean.valueC.toOption.get.isClean shouldBe true
        clean.processResult.termination shouldBe ProcessExecutionTermination.Exited(0)
        clean.processResult.stdout.content shouldBe empty
        clean.processResult.stderr.content shouldBe empty
        Given("the same fixed selected file now contains a line with trailing spaces")
        Files.writeString(fixture.checkout.resolve("selected.txt"), "changed  \n")
        When("typed StageSelectedAll stages that selector and typed CheckPendingIndex checks the same base")
        val staged = GitExecutionSpecSupport.execute(fixture, GitOperation.StageSelectedAll(selectionId)).toOption.get
        val findings = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckPendingIndex(base)).toOption.get
        Then("the actual exit-two receipt retains exact diagnostics and exposes completed findings rather than a clean check")
        staged.outcome shouldBe GitExecutionOutcome.Succeeded
        findings.outcome shouldBe GitExecutionOutcome.Succeeded
        findings.valueC.toOption shouldBe Some(GitIndexCheck(Set("selected.txt:1: trailing whitespace.")))
        findings.valueC.toOption.get.isClean shouldBe false
        findings.processResult.termination shouldBe ProcessExecutionTermination.Exited(2)
        new String(findings.processResult.stdout.content.toArray, java.nio.charset.StandardCharsets.UTF_8) shouldBe
          "selected.txt:1: trailing whitespace.\n+changed  \n"
        findings.processResult.stderr.content shouldBe empty
      } finally { driver.close(); fixture.close() }
    }

    "deny index checks in the current ungranted context and decode exactly one allowed check" in {
      Given("an authorized construction context, a current context without process grants and an independently allowed findings context")
      val source = create()
      val denied = create(capabilityGranted = false)
      val allowed = create(outputs = Map(GitOperationKind.CheckPendingIndex ->
        result("selected.txt:1: trailing whitespace.\n+changed  \n", ProcessExecutionTermination.Exited(2))))
      val program = new GitSpecBehavior(Behavior.Core(source.context, None, None))
        .program(source.target, GitOperation.CheckPendingIndex(objectId))
      try {
        When("the denied and allowed current interpreters each evaluate the same declared index-check intent")
        val refusal = new UnitOfWorkInterpreter(new UnitOfWork(denied.context)).run(program)
        val accepted = new UnitOfWorkInterpreter(new UnitOfWork(allowed.context)).run(program)
        Then("denial starts no process and only the allowed context starts once and returns its actual typed findings")
        refusal.isFaillure shouldBe true
        source.profile.driver.executions shouldBe empty
        denied.profile.driver.executions shouldBe empty
        accepted.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        accepted.toOption.get.valueC.toOption shouldBe Some(GitIndexCheck(Set("selected.txt:1: trailing whitespace.")))
        accepted.toOption.get.valueC.toOption.get.isClean shouldBe false
        accepted.toOption.get.processResult.termination shouldBe ProcessExecutionTermination.Exited(2)
        allowed.profile.driver.executions.map(_.definition) shouldBe Vector(
          allowed.definitions.find(_.capability == capability(GitOperationKind.CheckPendingIndex)).get)
        allowed.profile.driver.executions should have size 1
      } finally Vector(source, denied, allowed).foreach(_.close())
    }

    "use normal Git with a fixed literal colon, newline and backslash selection and typed checkpoint history" in {
      Given("a temporary normal Git checkout and a host-fixed selected filename containing colon, LF and backslash")
      val driver = new LocalProcessExecutionDriver()
      val filename = "C:literal\\name\n.txt"
      val fixture = create(driver = Some(driver), selectedPath = filename)
      setup(fixture.checkout, "init", "--initial-branch=main")
      setup(fixture.checkout, "config", "user.name", "Fixture")
      setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
      Files.writeString(fixture.checkout.resolve(filename), "initial\n")
      setup(fixture.checkout, "--literal-pathspecs", "add", "--", filename)
      setup(fixture.checkout, "commit", "-m", "fixture initial")
      try {
        When("protected typed operations stage the literal selection, commit and install a full-state checkpoint")
        val first = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val absentmerge = GitExecutionSpecSupport.execute(fixture, GitOperation.MergeHead)
        val shallow = GitExecutionSpecSupport.execute(fixture, GitOperation.Shallow)
        val replacements = GitExecutionSpecSupport.execute(fixture, GitOperation.Replacements)
        Files.writeString(fixture.checkout.resolve(filename), "selected change\n")
        val staged = GitExecutionSpecSupport.execute(fixture, GitOperation.StageSelectedAll(selectionId))
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status)
        val paths = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingPaths(first))
        val committed = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitSelected(selectionId))
        val parent = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val full = GitExecutionSpecSupport.execute(fixture, GitOperation.StageFullState)
        val tree = GitExecutionSpecSupport.execute(fixture, GitOperation.IndexTree).toOption.get.valueC.toOption.get
        val checkpoint = GitExecutionSpecSupport.execute(fixture, GitOperation.CreateCheckpoint(tree, parent)).toOption.get.valueC.toOption.get
        val installed = GitExecutionSpecSupport.execute(fixture, GitOperation.InstallCheckpoint(checkpoint, parent))
        val head = GitExecutionSpecSupport.execute(fixture, GitOperation.Head)
        val metadata = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitMetadata(checkpoint))
        Then("literal path bytes survive the normal process and each actual typed history fact identifies the installed checkpoint")
        Vector(staged, committed, full, installed).foreach(_.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded)
        paths.toOption.get.valueC.toOption.get.map(_.value) shouldBe Vector(filename)
        status.toOption.get.valueC.toOption.get.map(_.path.value) shouldBe Vector(filename)
        absentmerge.toOption.get.valueC.toOption shouldBe Some(None)
        shallow.toOption.get.valueC.toOption shouldBe Some(false)
        replacements.toOption.get.valueC.toOption shouldBe Some(Vector.empty)
        head.toOption.get.valueC.toOption shouldBe Some(checkpoint)
        metadata.toOption.get.valueC.toOption shouldBe Some(GitCommitMetadata(checkpoint, tree, Vector(parent)))
      } finally { driver.close(); fixture.close() }
    }

    "dispatch all thirty wrappers as one typed current-context execution with the exact result type" in {
      Given("the actual owning component with independent workspace and thirty operation grants")
      val fixture = create()
      val path = GitPath.parseC("selected.txt").toOption.get
      val expected: Vector[Any] = Vector("main", fixture.checkout.toRealPath().toString, GitRemoteTip(branchName, objectId),
        objectId, objectId, Vector(GitStatusEntry(' ', 'M', path, None)), false, Vector.empty, Some(objectId),
        Some(objectId), GitHistoryCounts(0, 0), Some(objectId), (), objectId, Vector.empty, (), (), (), (), (),
        objectId, (), GitCommitMetadata(objectId, objectId, Vector.empty), GitBlobContent("blob\n".getBytes("UTF-8").toVector, Some("blob\n")),
        GitIndexCheck(Set.empty), GitDiff("diff\n", false), Vector(path), (), (), ())
      try {
        When("the owning interpreter runs every protected typed wrapper")
        val results = operations.map(op => GitExecutionSpecSupport.execute(fixture, op).toOption.get)
        Then("each intent yields its typed value and exactly one admitted shared-process execution")
        results.map(_.operation) shouldBe operations
        results.map(_.target).distinct shouldBe Vector(fixture.target)
        results.map(_.outcome) shouldBe Vector.fill(30)(GitExecutionOutcome.Succeeded)
        results.map(_.value.get) shouldBe expected
        fixture.profile.driver.executions.map(_.definition) shouldBe fixture.definitions
        fixture.profile.driver.executions should have size 30
      } finally fixture.close()
    }

    "declare and record Git intent without workspace, process admission or driver calls" in {
      Given("a Behavior whose construction context has no semantic runtime services")
      val fixture = create(workspaceInstalled = false, processInstalled = false)
      val behavior = new GitSpecBehavior(Behavior.Core(fixture.context, None, None))
      val supplied = GitExecutionResult(fixture.target, GitOperation.Status, GitExecutionOutcome.Succeeded,
        Some(Vector.empty[GitStatusEntry]), result())
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.GitExec(_, GitOperation.Status) => Consequence.success(supplied)
          case _ => Consequence.notImplemented("unexpected Git recording operation")
        }
      }
      try {
        When("the protected Status wrapper constructs a program and the generic recorder traverses it")
        val program = behavior.program(fixture.target, GitOperation.Status)
        val recording = UnitOfWorkProgramRecorder.record(program, provider)
        Then("one original GitExec and the typed stub remain without runtime admission or a driver start")
        recording.result.toOption shouldBe Some(supplied)
        recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.GitExec(fixture.target, GitOperation.Status))
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "use the interpreter's actual context rather than authority or cwd retained from construction" in {
      Given("authorized construction A, denied component B, absent services and an independently authorized workspace C")
      val source = create()
      val denied = create(component = ComponentId("org.goldenport.spec.OtherGitOwner"))
      val absent = create(workspaceInstalled = false, processInstalled = false)
      val actual = create()
      val fromauthorized = new GitSpecBehavior(Behavior.Core(source.context, None, None)).program(source.target, GitOperation.Root)
      val fromabsent = new GitSpecBehavior(Behavior.Core(absent.context, None, None)).program(absent.target, GitOperation.Root)
      try {
        When("the current denied interpreter runs A's declaration and C runs both previously constructed declarations")
        val refusal = new UnitOfWorkInterpreter(new UnitOfWork(denied.context)).run(fromauthorized)
        val results = Vector(fromauthorized, fromabsent).map(new UnitOfWorkInterpreter(new UnitOfWork(actual.context)).run(_))
        Then("B fails structurally without launch while C alone supplies its exact binding and definitions")
        refusal.isFaillure shouldBe true
        results.foreach(_.toOption.get.valueC.toOption shouldBe Some(actual.checkout.toRealPath().toString))
        actual.profile.driver.executions.map(_.definition) shouldBe Vector.fill(2)(
          actual.definitions.find(_.capability == capability(GitOperationKind.Root)).get)
        actual.profile.driver.executions should have size 2
        Vector(source, denied, absent).foreach(_.profile.driver.executions shouldBe empty)
      } finally Vector(source, denied, absent, actual).foreach(_.close())
    }

    "defer malformed targets, null typed parameters and unknown selections until interpretation" in {
      Given("declarative malformed Git intents including a null workspace ID and a fully installed actual runtime")
      val fixture = create()
      val behavior = new GitSpecBehavior(Behavior.Core(fixture.context, None, None))
      val malformed = Vector(behavior.program(null, GitOperation.Head),
        behavior.program(GitTarget(null.asInstanceOf[WorkspaceId]), GitOperation.Head),
        behavior.program(fixture.target, GitOperation.PendingMerge(null)),
        behavior.program(fixture.target, GitOperation.StageSelected(null)),
        behavior.program(fixture.target, GitOperation.Push(objectId, null)),
        behavior.program(fixture.target, GitOperation.StageSelected(GitSelectionId.parseC("unknown").toOption.get)))
      try {
        When("the current UoW interprets malformed declarations including a null canonical operation")
        val failures = malformed.map(new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(_))
        val nulloperation = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.GitExec[Unit](fixture.target, null))
        Then("every invalid intent returns a structured failure before the driver")
        (failures :+ nulloperation).foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "require ReadOnly or Manage and an independent actual component capability for each operation" in {
      Given("ReadOnly workspace authority, missing grants, missing services and a wrong actual component")
      val readonly = create(permission = WorkspacePermission.ReadOnly)
      val denied = Vector(create(capabilityGranted = false), create(workspaceGranted = false),
        create(processInstalled = false), create(workspaceInstalled = false), create(component = ComponentId("org.goldenport.spec.OtherGitOwner")))
      try {
        When("read operations and write-tree are interpreted with ReadOnly and Head is interpreted in every denied context")
        val read = GitExecutionSpecSupport.execute(readonly, GitOperation.Head)
        val write = GitExecutionSpecSupport.execute(readonly, GitOperation.IndexTree)
        val failures = denied.map(GitExecutionSpecSupport.execute(_, GitOperation.Head))
        val unknown = new UnitOfWorkInterpreter(new UnitOfWork(readonly.context)).interpret(
          UnitOfWorkOp.GitExec(GitTarget(WorkspaceId.parseC("unknown").toOption.get), GitOperation.Head))
        Then("only the explicit read capability starts; write-tree and every missing authority fail before launch")
        read.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        (failures :+ write :+ unknown).foreach(_.isFaillure shouldBe true)
        readonly.profile.driver.executions should have size 1
        denied.foreach(_.profile.driver.executions shouldBe empty)
      } finally (denied :+ readonly).foreach(_.close())
    }

    "deny scope-label impersonation, a different binding object and invalidation after declaration" in {
      Given("one typed declaration, an equal logical ID at another root and a fabricated component scope label")
      val fixture = create()
      val alternate = Files.createDirectory(fixture.root.resolve("alternate"))
      val replacement = WorkspaceExecutionAdmission.createC(Vector(workspaceId -> alternate),
        Vector(WorkspaceGrant(owner, workspaceId, WorkspacePermission.Manage))).toOption.get
      val context = contextFor(fixture.root, Some(replacement), Some(fixture.admission), fixture.profile.driver)
      val runtime = fixture.context.cncfCore.scope.parent.get.parent.get.parent.get
      val impersonated = fixture.context.withScope(runtime.createChildScope(ScopeKind.Component, owner.name))
      val program = new GitSpecBehavior(Behavior.Core(fixture.context, None, None)).program(fixture.target, GitOperation.Head)
      try {
        When("the replacement and impersonating interpreters run and the original directory is then invalidated")
        val mismatch = new UnitOfWorkInterpreter(new UnitOfWork(context)).run(program)
        val falseowner = new UnitOfWorkInterpreter(new UnitOfWork(impersonated)).run(program)
        Files.move(fixture.checkout, fixture.root.resolve("moved"))
        val invalidated = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(program)
        Then("identity, actual component ancestry and current binding validity all prevent launch")
        Vector(mismatch, falseowner, invalidated).foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "reject generic tagged requests and even a valid pre-resolved Git ProcessExec before the driver" in {
      Given("an authorized typed Head intent and an internally admitted Git execution")
      val fixture = create()
      val execution = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.Head).toOption.get
      val request = ProcessExecutionRequest(capability(GitOperationKind.Head))
      try {
        When("generic policy resolution and generic ProcessExec interpretation attempt to bypass typed admission")
        val requestdenial = fixture.policy.resolveC(request, ProcessExecutionGrant(request.capability))
        val generic = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).interpret(UnitOfWorkOp.ProcessExec(execution))
        Then("both generic routes fail structurally with zero starts")
        requestdenial.isFaillure shouldBe true
        generic.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
        When("the equivalent protected typed Head wrapper runs in the same context")
        val typed = GitExecutionSpecSupport.execute(fixture, GitOperation.Head)
        Then("only that typed route executes once and retains its actual bounded receipt")
        typed.toOption.get.valueC.toOption shouldBe Some(objectId)
        fixture.profile.driver.executions should have size 1
      } finally fixture.close()
    }

    "retain incomplete, malformed, truncated and nonzero outcomes without manufacturing a typed value" in {
      Given("bounded terminal and capture results for a typed Head operation")
      val cases = Vector(result("ignored", ProcessExecutionTermination.TimedOut) -> GitExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut),
        result("ignored", ProcessExecutionTermination.Cancelled) -> GitExecutionOutcome.Incomplete(ProcessExecutionTermination.Cancelled),
        result("ignored", ProcessExecutionTermination.LaunchFailed) -> GitExecutionOutcome.Incomplete(ProcessExecutionTermination.LaunchFailed),
        result("ignored", ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)) -> GitExecutionOutcome.Incomplete(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)),
        result("ignored", ProcessExecutionTermination.ArtifactLimitExceeded) -> GitExecutionOutcome.Incomplete(ProcessExecutionTermination.ArtifactLimitExceeded),
        result("ignored", ProcessExecutionTermination.Exited(7)) -> GitExecutionOutcome.Failed(7))
      val fixtures = cases.map { case (raw, outcome) => (create(outputs = Map(GitOperationKind.Head -> raw)), raw, outcome) }
      val invalid = Vector(result("bad\n"), result(objectId.value + "\n", truncated = true),
        result(objectId.value + "\n").copy(stdout = ProcessExecutionCapture(Vector[Byte](-1), 1, false)))
        .map(raw => create(outputs = Map(GitOperationKind.Head -> raw)))
      try {
        When("the owning interpreter consumes each supplied bounded result")
        val terminals = fixtures.map { case (fixture, raw, outcome) => (GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get, raw, outcome) }
        val captures = invalid.map(GitExecutionSpecSupport.execute(_, GitOperation.Head).toOption.get)
        Then("incomplete and failed receipts retain their raw facts and invalid captures never produce successful object IDs")
        terminals.foreach { case (typed, raw, outcome) =>
          typed.outcome shouldBe outcome
          typed.processResult shouldBe raw
          typed.valueC.isFaillure shouldBe true
          typed.value shouldBe None
        }
        captures.foreach { typed =>
          typed.outcome shouldBe a[GitExecutionOutcome.InvalidOutput]
          typed.valueC.isFaillure shouldBe true
        }
        (fixtures.map(_._1) ++ invalid).foreach(_.profile.driver.executions should have size 1)
      } finally (fixtures.map(_._1) ++ invalid).foreach(_.close())
    }

    "require conflict observation after PendingMerge exit one and consume later typed status and index facts" in {
      Given("a PendingMerge exit-one receipt plus subsequent actual typed unmerged status and stage facts")
      val fixture = create(outputs = Map(GitOperationKind.PendingMerge -> result("", ProcessExecutionTermination.Exited(1)),
        GitOperationKind.Status -> result("UU selected.txt\u0000"),
        GitOperationKind.UnmergedIndex -> result(s"100644 ${objectId.value} 1\tselected.txt\u0000")))
      try {
        When("the typed merge result is followed by typed Status and UnmergedIndex observations")
        val merge = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingMerge(objectId)).toOption.get
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status).toOption.get
        val index = GitExecutionSpecSupport.execute(fixture, GitOperation.UnmergedIndex).toOption.get
        Then("the first result requires observation and only subsequent typed facts describe the unmerged index")
        merge.outcome shouldBe GitExecutionOutcome.ConflictObservationRequired
        merge.value shouldBe None
        merge.valueC.isFaillure shouldBe true
        status.valueC.toOption.get.head.unmerged shouldBe true
        index.valueC.toOption.get.head.stage shouldBe GitIndexStage.Base
        fixture.profile.driver.executions should have size 3
      } finally fixture.close()
    }

    "retain shared UoW WorkArea cleanup for timeout, cancellation and malformed captured Git output" in {
      Given("typed Git fixtures with controlled terminal handles that observe the UoW-owned WorkArea")
      val receipts = Vector(result("", ProcessExecutionTermination.TimedOut), result("", ProcessExecutionTermination.Cancelled), result("malformed\n"))
      val cases = receipts.map { receipt =>
        val driver = new GitLifecycleDriver(receipt)
        create(driver = Some(driver)) -> driver
      }
      try {
        When("the typed Head interpreter awaits each terminal result through the existing shared lifecycle")
        val results = cases.map { case (fixture, driver) => (GitExecutionSpecSupport.execute(fixture, GitOperation.Head), driver) }
        Then("every typed result retains its terminal facts and each allocated WorkArea is closed after one await")
        results.foreach { case (typed, driver) =>
          typed.isSuccess shouldBe true
          typed.toOption.get.valueC.isFaillure shouldBe true
          driver.awaitCount shouldBe 1
          driver.areas should have size 1
          driver.areas.foreach(path => Files.exists(path) shouldBe false)
        }
      } finally cases.foreach(_._1.close())
    }
  }
}

private final class GitLifecycleDriver(receipt: ProcessExecutionResult) extends ProcessExecutionDriver {
  private var _areas = Vector.empty[java.nio.file.Path]
  private var _await_count = 0
  val safeIdentity: String = "git-lifecycle-spec"
  def areas: Vector[java.nio.file.Path] = _areas
  def awaitCount: Int = _await_count
  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    Consequence.operationIllegal("git-spec", "UoW-owned WorkArea required")
  override def startC(execution: ResolvedProcessExecution, workArea: ProcessExecutionWorkArea): Consequence[ProcessExecutionHandle] = {
    _areas = _areas :+ workArea.root
    Consequence.success(new ProcessExecutionHandle {
      def awaitC: Consequence[ProcessExecutionResult] = { _await_count += 1; Consequence.success(receipt) }
      def cancelC: Consequence[Unit] = Consequence.unit
    })
  }
}
