package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 26, 2026
 * @version Oct. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitRepositoryExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GitExecutionSpecSupport.*

  private val _capability = ProcessCapabilityId.parseC("repository").toOption.get
  private def _plan(fixture: GitSpecFixture, granted: Boolean = true,
    permission: WorkspacePermission = WorkspacePermission.Manage,
    workspacegranted: Boolean = true, selected: Boolean = false, selectedgranted: Boolean = true,
    gitexecutable: Option[Path] = None, environment: Map[String, String] = Map("PATH" -> "/usr/bin:/bin")): Consequence[RuntimeProcessExecutionPlan] = {
    val declarations = if (selected) GitOperationKind.values.toVector.filter(_.selected).map(declaration(_, fixture.endpoint))
      else Vector.empty
    val grants = (if (granted) Vector(ProcessExecutionGrant(_capability)) else Vector.empty) ++
      (if (selectedgranted) declarations.map(x => ProcessExecutionGrant(x.capability)) else Vector.empty)
    RuntimeProcessExecutionPlan.createV5C(Vector.empty, Map(owner -> grants),
      Vector(WorkspaceRuntimeDeclaration(workspaceId, fixture.checkout)),
      if (workspacegranted) Vector(WorkspaceGrant(owner, workspaceId, permission)) else Vector.empty,
      Vector.empty, declarations, Vector(GitRepositoryRuntimeDeclaration(workspaceId, _capability,
        "repository", gitexecutable, branchName, "origin", branchName, fixture.endpoint,
        GitCommitIdentity("Fixture", "fixture@example.invalid"), limits, environment)))
  }

  private def _configured(fixture: GitSpecFixture, plan: RuntimeProcessExecutionPlan,
    driver: ProcessExecutionDriver, component: ComponentId = owner): GitSpecFixture =
    fixture.copy(workspace = plan.workspaceAdmissionOption.get, admission = plan.admission,
      context = contextFor(fixture.root, plan.workspaceAdmissionOption, Some(plan.admission), driver, component))

  private def _value[A](fixture: GitSpecFixture, operation: GitOperation[A]): A = {
    val typed = GitExecutionSpecSupport.execute(fixture, operation).toOption.get
    typed.outcome shouldBe GitExecutionOutcome.Succeeded
    typed.processResult.safeProgramIdentity shouldBe "repository-" + operation.kind.wireName
    typed.selectedPaths shouldBe empty
    typed.valueC.toOption.get
  }

  "Run-selected Git data" should {
    "stage and commit actual run paths while preserving unrelated dirty work through the ordinary UoW" in {
      Given("a granted repository family with real files and no configured selected paths or message")
      val driver = new LocalProcessExecutionDriver()
      val original = create(driver = Some(driver))
      try {
        initialize(original, Map("selected.txt" -> "before\n", "unrelated.txt" -> "keep\n"))
        val fixture = _configured(original, _plan(original).toOption.get, driver)
        val parent = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        Files.writeString(fixture.checkout.resolve("selected.txt"), "after\n")
        Files.writeString(fixture.checkout.resolve("unrelated.txt"), "unrelated edit\n")
        val id = GitSelectionId.parseC("actual-step").toOption.get
        val data = GitSelectionParameters(id, Vector(GitPath.parseC("selected.txt").toOption.get), Some("Actual selected Step"))
        val selected = fixture.copy(target = GitTarget(workspaceId, Some(data)))
        When("a run supplies typed stage, index and commit intents using its actual selected paths")
        GitExecutionSpecSupport.execute(selected, GitOperation.StageSelectedAll(id)).toOption.get.valueC.toOption.get
        val check = GitExecutionSpecSupport.execute(selected, GitOperation.CheckSelectedIndex(id, parent)).toOption.get.valueC.toOption.get
        val changes = GitExecutionSpecSupport.execute(selected, GitOperation.SelectedIndexChanges(id, parent)).toOption.get.valueC.toOption.get
        GitExecutionSpecSupport.execute(selected, GitOperation.CommitSelected(id)).toOption.get.valueC.toOption.get
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status).toOption.get.valueC.toOption.get
        Then("only the selected path is committed and the unrelated worktree edit remains")
        check.isClean shouldBe true
        changes.map(_.path.value) shouldBe Vector("selected.txt")
        status.map(_.path.value) shouldBe Vector("unrelated.txt")
        Files.readString(fixture.checkout.resolve(".git/logs/HEAD")) should include("commit: Actual selected Step")
      } finally { driver.close(); original.close() }
    }

    "retain both checkpoint grants and their stricter execution limits for a selected commit" in {
      Given("independent creation and installation capabilities with different finite host and grant bounds")
      val fixture = create()
      try {
        val create = declaration(GitOperationKind.CreateCheckpoint, fixture.endpoint)
        val install = declaration(GitOperationKind.InstallCheckpoint, fixture.endpoint)
          .copy(limits = limits.copy(executionTimeoutMillis = Some(1000L)))
        val binding = fixture.workspace.bindingC(workspaceId).toOption.get
        val policy = ProcessExecutionPolicy.createC(Vector(definition(binding, create).toOption.get,
          definition(binding, install).toOption.get)).toOption.get
        def configured(withInstallation: Boolean) = {
          val grants = Vector(ProcessExecutionGrant(create.capability)) ++
            (if (withInstallation) Vector(ProcessExecutionGrant(install.capability,
              ProcessExecutionLimits(stdoutBytes = Some(512L)))) else Vector.empty)
          val admission = ProcessExecutionAdmission.forComponentsC(policy, Map(owner -> grants)).toOption.get
          fixture.copy(admission = admission, context = contextFor(fixture.root, Some(fixture.workspace),
            Some(admission), fixture.profile.driver, owner))
        }
        val granted = configured(true)
        val missing = configured(false)
        val id = GitSelectionId.parseC("actual-step").toOption.get
        val target = GitTarget(workspaceId, Some(GitSelectionParameters(id,
          Vector(GitPath.parseC("selected.txt").toOption.get), Some("Actual selected Step"))))
        When("the ordinary admission resolves the selected commit without starting a process")
        val resolved = GitExecutionAdmission.resolveC(granted.context, target, GitOperation.CommitSelected(id)).toOption.get
        val denied = GitExecutionAdmission.resolveC(missing.context, target, GitOperation.CommitSelected(id))
        Then("creation never substitutes for installation authority or discards the installation bounds")
        resolved.effectiveLimits.executionTimeoutMillis shouldBe Some(1000L)
        resolved.effectiveLimits.stdoutBytes shouldBe Some(512L)
        denied.isSuccess shouldBe false
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "reject a null selection container at the existing typed input boundary" in {
      Given("a granted repository target with an invalid optional selection container")
      val fixture = create()
      try {
        val configured = _configured(fixture, _plan(fixture).toOption.get, fixture.profile.driver)
        When("ordinary typed Git admission receives that target")
        val result = GitExecutionAdmission.resolveC(configured.context, GitTarget(workspaceId, null), GitOperation.Root)
        Then("it returns a typed failure and does not start a process")
        result.isSuccess shouldBe false
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }
  }

  "E1 Repository Git family route" must afterWord(
    "in spec:typed-git-execution, example:E1, rules:R1,R2,R3, phase:GIT-ROUTE-20261026-01") {
    "resolve exactly the current closed 28 nonselected kinds through the existing typed admission" in {
      Given("R1 R2: a single repository declaration with a deliberately closed operation family")
      val fixture = create()
      try {
        val expected = Set("branch", "root", "remote-tip", "head", "tree", "status", "shallow", "replacements",
          "merge-head", "tracking-tip", "relation", "merge-base", "stage-full-state", "index-tree", "unmerged-index",
          "fetch", "fast-forward", "pending-merge", "commit-pending", "push", "create-checkpoint", "install-checkpoint",
          "commit-metadata", "blob-content", "check-pending-index", "pending-diff", "pending-paths", "tree-changes")
        val operations = GitExecutionSpecSupport.operations.filter(operation => expected.contains(operation.kind.wireName))
        val profile = ProcessExecutionTestProfile(operations.map { operation =>
          ProcessCapabilityId.parseC("repository-" + operation.kind.wireName).toOption.get ->
            result(sample(operation.kind, fixture.checkout))
        }.toMap)
        val configured = _configured(fixture, _plan(fixture).toOption.get, profile.driver)
        When("each fixed family member is interpreted using a bounded deterministic process receipt")
        val results = operations.map(operation => GitExecutionSpecSupport.execute(configured, operation))
        Then("all 28 current members retain derived identities and the family contract is explicitly enumerated")
        expected should have size 28
        operations.map(_.kind.wireName).toSet shouldBe expected
        GitOperationKind.values.toVector.filterNot(_.selected).map(_.wireName).toSet shouldBe expected
        results.foreach(_.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded)
        profile.driver.executions.map(_.request.capability.print).toSet shouldBe expected.map("repository-" + _)
        profile.driver.executions.foreach { execution =>
          execution.definition.safeProgramIdentity shouldBe execution.request.capability.print
          val metadata = execution.definition._git_metadata.get
          metadata.binding should be theSameInstanceAs configured.workspace.bindingC(workspaceId).toOption.get
          metadata.commitIdentity.isDefined shouldBe metadata.kind.requiresIdentity
          metadata.selectionId shouldBe None
          metadata.selectedPath shouldBe None
          metadata.selectedPaths shouldBe empty
          metadata.commitMessage shouldBe None
        }
      } finally fixture.close()
    }

    "observe and synchronize a local bare remote through one repository declaration and the existing typed UoW" in {
      Given("R1 R2 R3: one host repository family and the actual owner's independent Manage and process grants")
      val driver = new LocalProcessExecutionDriver()
      val original = create(driver = Some(driver))
      try {
        initialize(original, Map("tracked.txt" -> "initial\n"))
        setup(original.root, "init", "--bare", original.endpoint)
        val plan = _plan(original).toOption.get
        val fixture = _configured(original, plan, driver)
        When("the typed Git route observes the checkout, stages full state and installs and pushes a checkpoint")
        val root = _value(fixture, GitOperation.Root)
        val first = _value(fixture, GitOperation.Head)
        _value(fixture, GitOperation.Push(first, branchName))
        val initialtip = _value(fixture, GitOperation.RemoteTip)
        _value(fixture, GitOperation.Fetch)
        Files.writeString(fixture.checkout.resolve("tracked.txt"), "changed\n")
        Files.writeString(fixture.checkout.resolve("added.txt"), "added\n")
        _value(fixture, GitOperation.StageFullState)
        val paths = _value(fixture, GitOperation.PendingPaths(first))
        val check = _value(fixture, GitOperation.CheckPendingIndex(first))
        val diff = _value(fixture, GitOperation.PendingDiff(first))
        val tree = _value(fixture, GitOperation.IndexTree)
        val checkpoint = _value(fixture, GitOperation.CreateCheckpoint(tree, first))
        _value(fixture, GitOperation.InstallCheckpoint(checkpoint, first))
        val head = _value(fixture, GitOperation.Head)
        val metadata = _value(fixture, GitOperation.CommitMetadata(head))
        _value(fixture, GitOperation.Push(head, branchName))
        _value(fixture, GitOperation.Fetch)
        val remotetip = _value(fixture, GitOperation.RemoteTip)
        val tracking = _value(fixture, GitOperation.TrackingTip)
        val relation = _value(fixture, GitOperation.Relation)
        val status = _value(fixture, GitOperation.Status)
        Then("the original admission and lifecycle produce real values with the internally derived program identities")
        root shouldBe fixture.checkout.toRealPath().toString
        initialtip shouldBe GitRemoteTip(branchName, first)
        paths.map(_.value).toSet shouldBe Set("tracked.txt", "added.txt")
        check.isClean shouldBe true
        diff.text should include("+changed")
        head shouldBe checkpoint
        metadata shouldBe GitCommitMetadata(head, tree, Vector(first))
        remotetip shouldBe GitRemoteTip(branchName, checkpoint)
        tracking shouldBe Some(checkpoint)
        relation shouldBe GitHistoryCounts(0, 0)
        status shouldBe empty
        plan.gitDeclarations shouldBe empty
        plan.gitRepositoryDeclarations should have size 1
        plan.grants(owner) shouldBe Vector(ProcessExecutionGrant(_capability))
      } finally { driver.close(); original.close() }
    }
  }

  "E2 Repository Git family authority" must afterWord(
    "in spec:typed-git-execution, example:E2, rules:R2,R3,R4, phase:GIT-ROUTE-20261026-01") {
    "deny another component, missing process authority, missing Manage and undeclared workspaces before launch" in {
      Given("R2 R3 R4: a repository family whose public grant belongs only to its declared actual component")
      val fixture = create()
      try {
        val plan = _plan(fixture).toOption.get
        val other = _configured(fixture, plan, fixture.profile.driver, ComponentId("org.example.Other"))
        val ungranted = _configured(fixture, _plan(fixture, granted = false).toOption.get, fixture.profile.driver)
        val readonly = _plan(fixture, permission = WorkspacePermission.ReadOnly)
        val missing = _plan(fixture, workspacegranted = false)
        val outside = _configured(fixture, plan, fixture.profile.driver).copy(
          target = GitTarget(WorkspaceId.parseC("outside").toOption.get))
        When("each denied interpretation requests the otherwise valid typed Root operation")
        val denied = Vector(other, ungranted, outside).map(GitExecutionSpecSupport.execute(_, GitOperation.Root))
        Then("neither family installation nor workspace existence manufactures caller authority")
        denied.foreach(_.isFaillure shouldBe true)
        readonly.isFaillure shouldBe true
        missing.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "leave all five selected kinds under their independent explicit declarations and grants" in {
      Given("R4: family authority alone and the same family alongside explicit selected declarations")
      val fixture = create()
      try {
        val family = _configured(fixture, _plan(fixture).toOption.get, fixture.profile.driver)
        val ungranted = _configured(fixture, _plan(fixture, selected = true, selectedgranted = false).toOption.get, fixture.profile.driver)
        val explicit = _configured(fixture, _plan(fixture, selected = true).toOption.get, fixture.profile.driver)
        val operations = GitExecutionSpecSupport.operations.filter(_.kind.selected)
        When("typed selected operations resolve through the current actual component")
        val denied = Vector(family, ungranted).flatMap(configured =>
          operations.map(operation => GitExecutionSpecSupport.execute(configured, operation)))
        val admitted = GitExecutionSpecSupport.execute(explicit, GitOperation.StageSelected(selectionId))
        Then("the repository grant cannot resolve selected operations and the old explicit route remains usable")
        denied.foreach(_.isFaillure shouldBe true)
        admitted.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        fixture.profile.driver.executions.head.definition.safeProgramIdentity shouldBe "git-stage-selected"
        fixture.profile.driver.executions should have size 1
      } finally fixture.close()
    }
  }

  "E4 Repository Git executable selection" must afterWord(
    "in spec:typed-git-execution, example:E4, rules:R3,R5,R6, phase:GIT-ROUTE-20261026-01") {
    "use an omitted executable from effective PATH and honor an explicit override independently of PATH" in {
      Given("R6: ordinary Git available on a configured absolute PATH and an explicit executable with an empty PATH")
      val driver = new LocalProcessExecutionDriver()
      val fixture = create(driver = Some(driver))
      try {
        initialize(fixture, Map("tracked.txt" -> "initial\n"))
        val defaultplan = _plan(fixture).toOption.get
        val overrideplan = _plan(fixture, gitexecutable = Some(Paths.get("/usr/bin/git")), environment = Map("PATH" -> "")).toOption.get
        val nogitoverride = _plan(fixture, gitexecutable = Some(Paths.get("/usr/bin/git")),
          environment = Map("PATH" -> fixture.root.toString)).toOption.get
        val inherited = _plan(fixture, environment = Map.empty).toOption.get
        When("each host-selected route executes the typed Root observation")
        val roots = Vector(defaultplan, overrideplan, nogitoverride, inherited).map(plan =>
          _value(_configured(fixture, plan, driver), GitOperation.Root))
        val profile = ProcessExecutionTestProfile(Map(ProcessCapabilityId.parseC("repository-root").toOption.get ->
          result(sample(GitOperationKind.Root, fixture.checkout))))
        val configured = _configured(fixture, inherited, profile.driver)
        val observed = GitExecutionSpecSupport.execute(configured, GitOperation.Root)
        Then("the original optional public choice is retained and inherited PATH is installed for the child process")
        roots.foreach(_ shouldBe fixture.checkout.toRealPath().toString)
        defaultplan.gitRepositoryDeclarations.head.gitExecutable shouldBe None
        overrideplan.gitRepositoryDeclarations.head.gitExecutable shouldBe Some(Paths.get("/usr/bin/git"))
        inherited.gitRepositoryDeclarations.head.environment shouldBe empty
        observed.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        profile.driver.executions.head.definition._environment.values("PATH") shouldBe sys.env("PATH")
      } finally { driver.close(); fixture.close() }
    }

    "report unavailable Git honestly and reject invalid overrides without searching a valid PATH" in {
      Given("R6: a PATH with no git, unsafe PATH entries and unavailable or nonexecutable explicit overrides")
      val fixture = create()
      try {
        val plain = Files.writeString(fixture.root.resolve("plain-git"), "not executable\n")
        When("the family creator resolves only its declared executable policy")
        val missing = _plan(fixture, environment = Map("PATH" -> fixture.root.toString))
        val unsafe = Vector("", ":/usr/bin", "/usr/bin:", "relative:/usr/bin").map(path =>
          _plan(fixture, environment = Map("PATH" -> path)))
        val invalid = Vector(fixture.root.resolve("missing-git"), plain, fixture.root).map(path =>
          _plan(fixture, gitexecutable = Some(path)))
        Then("no alternate executable or working-directory search replaces the requested route")
        missing.isFaillure shouldBe true
        missing.toString should include("git")
        missing.toString should include("PATH")
        (unsafe ++ invalid).foreach(_.isFaillure shouldBe true)
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }
  }
}
