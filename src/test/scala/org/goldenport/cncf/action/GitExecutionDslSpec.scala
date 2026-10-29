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
 * @version Oct. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitExecutionDslSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Protected typed Git DSL" should {
    "record purpose preparation separately from accepted installation without executing either" in {
      Given("a construction context with no workspace or execution installation")
      val fixture = create(workspaceInstalled = false, processInstalled = false)
      val behavior = new GitSpecBehavior(Behavior.Core(fixture.context, None, None))
      val request = GitPurposeRequest(objectId, objectId, Vector(GitPurposeChange(
        GitPath.parseC("selected.txt").toOption.get, Some("actual\n"), Some("purpose\n"))))
      val candidate = GitPurposeCandidate(request, objectId, objectId, objectId, "purpose")
      val operations = Vector[GitOperation[?]](GitOperation.PreparePurposeCandidate(selectionId, request),
        GitOperation.MaterializePurposeCandidate(selectionId, candidate, WorkspaceId.parseC("candidate").toOption.get),
        GitOperation.CommitPurposeCandidate(selectionId, candidate))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.GitExec(target, value @ GitOperation.PreparePurposeCandidate(_, _)) =>
            Consequence.success(GitExecutionResult(target, value, GitExecutionOutcome.Succeeded, Some(candidate), result()).asInstanceOf[A])
          case UnitOfWorkOp.GitExec(target, value) =>
            Consequence.success(GitExecutionResult(target, value.asInstanceOf[GitOperation[Any]], GitExecutionOutcome.Succeeded, Some(()), result()).asInstanceOf[A])
          case _ => Consequence.notImplemented("unexpected purpose recording operation")
        }
      }
      try {
        When("the recorder traverses each protected purpose operation")
        val recordings = operations.map(operation => UnitOfWorkProgramRecorder.record(behavior.program(fixture.target, operation), provider))
        Then("each is one typed Git intent and the declaration grants no execution or acceptance")
        recordings.zip(operations).foreach { case (recording, operation) =>
          recording.result.isSuccess shouldBe true
          recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.GitExec(fixture.target, operation))
        }
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "observe a new selected EOF-only finding before dispatch and commit only its corrected fixed path" in {
      Given("a normal Git checkout with unrelated staged and unstaged changes and an untracked selected NEW file")
      val driver = new LocalProcessExecutionDriver()
      val fixture = create(driver = Some(driver))
      setup(fixture.checkout, "init", "--initial-branch=main")
      setup(fixture.checkout, "config", "user.name", "Fixture")
      setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
      Files.writeString(fixture.checkout.resolve("staged.txt"), "staged base\n")
      Files.writeString(fixture.checkout.resolve("unstaged.txt"), "unstaged base\n")
      setup(fixture.checkout, "add", "--", "staged.txt", "unstaged.txt")
      setup(fixture.checkout, "commit", "-m", "fixture initial")
      Files.writeString(fixture.checkout.resolve("staged.txt"), "unrelated staged bytes\n")
      setup(fixture.checkout, "add", "--", "staged.txt")
      Files.writeString(fixture.checkout.resolve("unstaged.txt"), "unrelated unstaged bytes\n")
      Files.writeString(fixture.checkout.resolve("selected.txt"), "selected new\n\n")
      val stagedbytes = Files.readAllBytes(fixture.checkout.resolve("staged.txt")).toVector
      val unstagedbytes = Files.readAllBytes(fixture.checkout.resolve("unstaged.txt")).toVector
      try {
        When("typed staging and the fixed selected index check observe the new file before any commit dispatch")
        val base = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val originaldiff = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(base)).toOption.get.valueC.toOption.get
        val staged = GitExecutionSpecSupport.execute(fixture, GitOperation.StageSelectedAll(selectionId)).toOption.get
        val findings = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckSelectedIndex(selectionId, base)).toOption.get
        val observedhead = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        Then("the actual exit-two receipt classifies only the selected EOF blank line and HEAD remains unchanged")
        staged.outcome shouldBe GitExecutionOutcome.Succeeded
        findings.outcome shouldBe GitExecutionOutcome.Succeeded
        findings.processResult.termination shouldBe ProcessExecutionTermination.Exited(2)
        findings.valueC.toOption shouldBe Some(GitIndexCheck(Set("selected.txt:2: new blank line at EOF."),
          Set(GitPath.parseC("selected.txt").toOption.get), true))
        observedhead shouldBe base
        Given("the fixture applies its bounded terminal-LF correction to the selected file")
        Files.writeString(fixture.checkout.resolve("selected.txt"), "selected new\n")
        When("typed restaging and selected checking succeed, followed by the selected raw observation and real selected commit")
        val restaged = GitExecutionSpecSupport.execute(fixture, GitOperation.StageSelectedAll(selectionId)).toOption.get
        val clean = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckSelectedIndex(selectionId, base)).toOption.get
        val selected = GitExecutionSpecSupport.execute(fixture, GitOperation.SelectedIndexChanges(selectionId, base)).toOption.get
        val committed = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitSelected(selectionId)).toOption.get
        val head = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val tree = GitExecutionSpecSupport.execute(fixture, GitOperation.TreeChanges(base, head)).toOption.get
        val pending = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(head)).toOption.get.valueC.toOption.get
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status).toOption.get.valueC.toOption.get
        Then("the selected add is the whole commit and unrelated index and worktree content retain their original bytes and state")
        Vector(restaged, clean, selected, committed, tree).foreach(_.outcome shouldBe GitExecutionOutcome.Succeeded)
        clean.valueC.toOption shouldBe Some(GitIndexCheck(Set.empty))
        val changes = selected.valueC.toOption.get
        changes should have size 1
        changes.head.path.value shouldBe "selected.txt"
        changes.head.status shouldBe "A"
        changes.head.oldBlob shouldBe None
        changes.head.newBlob.isDefined shouldBe true
        tree.valueC.toOption shouldBe Some(changes)
        head should not be (base)
        pending shouldBe originaldiff
        status.map(entry => (entry.path.value, entry.indexCode, entry.worktreeCode)).toSet shouldBe
          Set(("staged.txt", 'M', ' '), ("unstaged.txt", ' ', 'M'))
        Files.readAllBytes(fixture.checkout.resolve("staged.txt")).toVector shouldBe stagedbytes
        Files.readAllBytes(fixture.checkout.resolve("unstaged.txt")).toVector shouldBe unstagedbytes
        Files.readString(fixture.checkout.resolve("selected.txt")) shouldBe "selected new\n"
      } finally { driver.close(); fixture.close() }
    }

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
        val clean = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckSelectedIndex(selectionId, first))
        val selected = GitExecutionSpecSupport.execute(fixture, GitOperation.SelectedIndexChanges(selectionId, first))
        val committed = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitSelected(selectionId))
        val parent = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val changed = GitExecutionSpecSupport.execute(fixture, GitOperation.TreeChanges(first, parent))
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
        clean.toOption.get.valueC.toOption shouldBe Some(GitIndexCheck(Set.empty))
        selected.toOption.get.valueC.toOption.get.map(_.path.value) shouldBe Vector(filename)
        changed.toOption.get.valueC.toOption shouldBe selected.toOption.get.valueC.toOption
        absentmerge.toOption.get.valueC.toOption shouldBe Some(None)
        shallow.toOption.get.valueC.toOption shouldBe Some(false)
        replacements.toOption.get.valueC.toOption shouldBe Some(Vector.empty)
        head.toOption.get.valueC.toOption shouldBe Some(checkpoint)
        metadata.toOption.get.valueC.toOption shouldBe Some(GitCommitMetadata(checkpoint, tree, Vector(parent)))
      } finally { driver.close(); fixture.close() }
    }

    "dispatch thirty-three semantic GitExec occurrences through thirty-five processes with the exact typed results" in {
      Given("the actual owning component with independent workspace and thirty-three operation grants")
      val fixture = create()
      val receipts = operations.flatMap { operation =>
        if (operation.kind == GitOperationKind.CommitSelected) Vector(result("selected.txt\u0000"), result(), result())
        else Vector(result(sample(operation.kind, fixture.checkout)))
      }
      val driver = new GitSelectedCommitSpecDriver(receipts)
      val configured = fixture.copy(context = contextFor(fixture.root, Some(fixture.workspace), Some(fixture.admission), driver))
      val path = GitPath.parseC("selected.txt").toOption.get
      val expected: Vector[Any] = Vector("main", fixture.checkout.toRealPath().toString, GitRemoteTip(branchName, objectId),
        objectId, objectId, Vector(GitStatusEntry(' ', 'M', path, None)), false, Vector.empty, Some(objectId),
        Some(objectId), GitHistoryCounts(0, 0), Some(objectId), (), objectId, Vector.empty, (), (), (), (), (),
        objectId, (), GitCommitMetadata(objectId, objectId, Vector.empty), GitBlobContent("blob\n".getBytes("UTF-8").toVector, Some("blob\n")),
        GitIndexCheck(Set.empty), GitDiff("diff\n", false), Vector(path), (), (), (), GitIndexCheck(Set.empty),
        Vector(GitTreeChange("000000", "100644", None, Some(objectId), "A", path)),
        Vector(GitTreeChange("000000", "100644", None, Some(objectId), "A", path)))
      try {
        When("the owning interpreter runs every protected typed wrapper")
        val results = operations.map(op => GitExecutionSpecSupport.execute(configured, op).toOption.get)
        Then("each semantic intent yields its typed value and selected commit retains both probes before its physical commit")
        results.map(_.operation) shouldBe operations
        results.map(_.target).distinct shouldBe Vector(fixture.target)
        results.map(_.outcome) shouldBe Vector.fill(33)(GitExecutionOutcome.Succeeded)
        results.map(_.value.get) shouldBe expected
        results.find(_.operation.kind == GitOperationKind.CommitSelected).get.selectedPaths shouldBe Vector(path)
        val expectedkinds = operations.flatMap { operation =>
          if (operation.kind == GitOperationKind.CommitSelected) Vector.fill(3)(operation.kind) else Vector(operation.kind)
        }
        driver.executions.map(_.definition.capability) shouldBe expectedkinds.map(capability)
        driver.executions.map(_.request.capability) shouldBe expectedkinds.map(capability)
        driver.executions.map(_.definition.safeProgramIdentity) shouldBe expectedkinds.map(kind => "git-" + kind.wireName)
        driver.executions should have size 35
      } finally fixture.close()
    }

    "record each new wrapper as exactly one original GitExec without runtime authority" in {
      Given("a declarative Behavior with absent runtime services and exact new operation stubs")
      val fixture = create(workspaceInstalled = false, processInstalled = false)
      val behavior = new GitSpecBehavior(Behavior.Core(fixture.context, None, None))
      val selected: Vector[GitOperation[?]] = Vector(GitOperation.CheckSelectedIndex(selectionId, objectId),
        GitOperation.SelectedIndexChanges(selectionId, objectId), GitOperation.TreeChanges(objectId, objectId),
        GitOperation.TreeEntries(objectId, selectionId))
      val provider = new UnitOfWorkProgramStubProvider {
        def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.GitExec(target, GitOperation.CheckSelectedIndex(id, parent)) =>
            Consequence.success(GitExecutionResult(target, GitOperation.CheckSelectedIndex(id, parent),
              GitExecutionOutcome.Succeeded, Some(GitIndexCheck(Set.empty)), result()))
          case UnitOfWorkOp.GitExec(target, GitOperation.SelectedIndexChanges(id, parent)) =>
            Consequence.success(GitExecutionResult(target, GitOperation.SelectedIndexChanges(id, parent),
              GitExecutionOutcome.Succeeded, Some(Vector.empty[GitTreeChange]), result()))
          case UnitOfWorkOp.GitExec(target, GitOperation.TreeChanges(before, after)) =>
            Consequence.success(GitExecutionResult(target, GitOperation.TreeChanges(before, after),
              GitExecutionOutcome.Succeeded, Some(Vector.empty[GitTreeChange]), result()))
          case UnitOfWorkOp.GitExec(target, GitOperation.TreeEntries(tree, id)) =>
            Consequence.success(GitExecutionResult(target, GitOperation.TreeEntries(tree, id),
              GitExecutionOutcome.Succeeded, Some(Vector(GitSelectedTreeEntry(GitPath.parseC("selected.txt").toOption.get, None))), result()))
          case _ => Consequence.notImplemented("unexpected selected Git recording operation")
        }
      }
      try {
        When("the generic recorder traverses each protected wrapper")
        val recordings = selected.map(op => UnitOfWorkProgramRecorder.record(behavior.program(fixture.target, op), provider))
        Then("each declaration records one exact target and typed operation with no driver start")
        recordings.zip(selected).foreach { case (recording, op) =>
          recording.result.isSuccess shouldBe true
          recording.occurrences.map(_.operation) shouldBe Vector(UnitOfWorkOp.GitExec(fixture.target, op))
        }
        fixture.profile.driver.executions shouldBe empty
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
        behavior.program(fixture.target, GitOperation.StageSelected(GitSelectionId.parseC("unknown").toOption.get)),
        behavior.program(fixture.target, GitOperation.CheckSelectedIndex(null, objectId)),
        behavior.program(fixture.target, GitOperation.CheckSelectedIndex(selectionId, null)),
        behavior.program(fixture.target, GitOperation.SelectedIndexChanges(null, objectId)),
        behavior.program(fixture.target, GitOperation.SelectedIndexChanges(selectionId, null)),
        behavior.program(fixture.target, GitOperation.TreeChanges(null, objectId)),
        behavior.program(fixture.target, GitOperation.TreeChanges(objectId, null)),
        behavior.program(fixture.target, GitOperation.TreeEntries(null, selectionId)),
        behavior.program(fixture.target, GitOperation.TreeEntries(objectId, null)),
        behavior.program(fixture.target, GitOperation.TreeEntries(objectId, GitSelectionId.parseC("unknown").toOption.get)),
        behavior.program(fixture.target, GitOperation.CheckSelectedIndex(GitSelectionId.parseC("unknown").toOption.get, objectId)),
        behavior.program(fixture.target, GitOperation.SelectedIndexChanges(GitSelectionId.parseC("unknown").toOption.get, objectId)))
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
        When("Head and all new observations use ReadOnly while writes and ungranted contexts attempt interpretation")
        val readoperations: Vector[GitOperation[?]] = Vector(GitOperation.Head, GitOperation.CheckSelectedIndex(selectionId, objectId),
          GitOperation.SelectedIndexChanges(selectionId, objectId), GitOperation.TreeChanges(objectId, objectId),
          GitOperation.TreeEntries(objectId, selectionId))
        val read = readoperations.map(GitExecutionSpecSupport.execute(readonly, _))
        val write = GitExecutionSpecSupport.execute(readonly, GitOperation.IndexTree)
        val failures = denied.flatMap(fixture => readoperations.map(GitExecutionSpecSupport.execute(fixture, _)))
        val unknown = new UnitOfWorkInterpreter(new UnitOfWork(readonly.context)).interpret(
          UnitOfWorkOp.GitExec(GitTarget(WorkspaceId.parseC("unknown").toOption.get), GitOperation.Head))
        Then("only the explicit read capability starts; write-tree and every missing authority fail before launch")
        read.foreach(_.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded)
        (failures :+ write :+ unknown).foreach(_.isFaillure shouldBe true)
        readonly.profile.driver.executions should have size 5
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
