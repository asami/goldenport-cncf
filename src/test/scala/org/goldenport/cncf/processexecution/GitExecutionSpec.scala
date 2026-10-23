package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.cncf.action.Behavior
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}
import org.goldenport.cncf.workarea.{FileWorkArea, WorkArea, WorkAreaGroup, WorkAreaId, WorkAreaSpace}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class GitExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GitExecutionSpecSupport.*

  "Typed Git protocol" should {
    "E1 Existing five-argument result creation" should afterWord(
      "in spec:typed-git-result-selection, example:E1, rules:R1, phase:CC-02") {
      "preserve source creation and the five-argument JVM entry points with empty selection metadata" in {
        Given("R1: existing callers construct a typed result without selection metadata")
        val target = GitTarget(workspaceId)
        val raw = result("main\n")
        When("the existing companion apply and constructor receive their original five arguments")
        val applied = GitExecutionResult(target, GitOperation.Branch, GitExecutionOutcome.Succeeded, Some("main"), raw)
        val constructed = new GitExecutionResult(target, GitOperation.Branch, GitExecutionOutcome.Succeeded, Some("main"), raw)
        Then("both retain their original values and JVM signatures while exposing an empty selected path vector")
        Vector(applied, constructed).foreach { typed =>
          typed.valueC.toOption shouldBe Some("main")
          typed.processResult shouldBe raw
          typed.selectedPaths shouldBe empty
        }
        classOf[GitExecutionResult[?]].getConstructors.exists(_.getParameterCount == 5) shouldBe true
        GitExecutionResult.getClass.getMethods.exists(method => method.getName == "apply" && method.getParameterCount == 5) shouldBe true
      }
    }

    "E2 Failed selected Git commit" should afterWord(
      "in spec:typed-git-result-selection, example:E2, rules:R2, phase:CC-02") {
      "retain the actual selected paths, operation, exit code and stderr for an unmatched pathspec" in {
        Given("R2: an ordinary repository with an initial commit and a host-selected path absent from that repository")
        val driver = new LocalProcessExecutionDriver()
        val fixture = create(driver = Some(driver), selectedPath = "missing.txt")
        try {
          setup(fixture.checkout, "init", "--initial-branch=main")
          setup(fixture.checkout, "config", "user.name", "Fixture")
          setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
          Files.writeString(fixture.checkout.resolve("tracked.txt"), "initial\n")
          setup(fixture.checkout, "add", "--", "tracked.txt")
          setup(fixture.checkout, "commit", "-m", "fixture initial")
          val operation = GitOperation.CommitSelected(selectionId)
          When("the owning typed UoW attempts the declared selected commit through ordinary Git")
          val committed = GitExecutionSpecSupport.execute(fixture, operation)
          Then("the typed failure retains its exact selection and actual process evidence without manufacturing a successful value")
          committed.isSuccess shouldBe true
          val typed = committed.toOption.get
          typed.operation shouldBe operation
          typed.outcome shouldBe GitExecutionOutcome.Failed(1)
          typed.selectedPaths.map(_.value) shouldBe Vector("missing.txt")
          typed.value shouldBe None
          typed.valueC.isFaillure shouldBe true
          typed.processResult.termination shouldBe ProcessExecutionTermination.Exited(1)
          typed.processResult.stderr.truncated shouldBe false
          typed.processResult.stderr.byteCount shouldBe typed.processResult.stderr.content.size.toLong
          val stderr = new String(typed.processResult.stderr.content.toArray, StandardCharsets.UTF_8)
          stderr should include("pathspec 'missing.txt'")
          stderr should include("did not match any file(s) known to git")
        } finally { driver.close(); fixture.close() }
      }
    }

    "E3 GIT-DIAG-01 Safe Git failure diagnostics" should afterWord(
      "in spec:typed-git-execution, example:E3, rules:GIT-DIAG-01, phase:CC-04") {
      "present bounded typed failure metadata while preserving the raw receipt and selection" in {
        Given("closed Git failure outcomes and actual process receipts containing private output and identity text")
        val target = GitTarget(workspaceId)
        val selection = GitSelectionId.parseC("secret-selection-token").toOption.get
        val paths = Vector(GitPath.parseC("secret-selected-path.txt").toOption.get)
        val operation = GitOperation.CommitSelected(selection)
        val stdout = "secret-stdout-diagnostic" + ("s" * 4096)
        val stderr = "secret-stderr-diagnostic" + ("e" * 4096)
        val reason = "secret-invalid-output-diagnostic"
        val identity = "secret-program-identity"
        val cases = Vector(
          (GitExecutionOutcome.Failed(128), ProcessExecutionTermination.Exited(128),
            "failed(exitCode=128)", "exited(exitCode=128)"),
          (GitExecutionOutcome.Incomplete(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)),
            ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
            "incomplete(output-limit-exceeded(stream=stdout))", "output-limit-exceeded(stream=stdout)"),
          (GitExecutionOutcome.Incomplete(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr)),
            ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr),
            "incomplete(output-limit-exceeded(stream=stderr))", "output-limit-exceeded(stream=stderr)"),
          (GitExecutionOutcome.Incomplete(ProcessExecutionTermination.TerminationUnconfirmed),
            ProcessExecutionTermination.TerminationUnconfirmed, "incomplete(termination-unconfirmed)", "termination-unconfirmed"),
          (GitExecutionOutcome.InvalidOutput(reason), ProcessExecutionTermination.Exited(0), "invalid-output", "exited(exitCode=0)"),
          (GitExecutionOutcome.ConflictObservationRequired, ProcessExecutionTermination.Exited(1),
            "conflict-observation-required", "exited(exitCode=1)"),
          (GitExecutionOutcome.Succeeded, ProcessExecutionTermination.Exited(0), "succeeded-without-value", "exited(exitCode=0)"))
        cases.foreach { case (outcome, termination, outcometag, terminationtag) =>
          val raw = result(stdout, termination).copy(stderr = result(stderr).stdout, safeProgramIdentity = identity)
          val typed = GitExecutionResult(target, operation, outcome, None, raw, paths)
          When("the caller requests an unavailable typed value")
          val failure = typed.valueC
          val presentation = failure.toString
          Then("the structured failure includes only deterministic operation, outcome, termination and capture metadata")
          failure.isFaillure shouldBe true
          presentation should include("git.execution.result")
          presentation should include("complete successful typed Git value")
          presentation should include("operation=commit-selected")
          presentation should include(s"outcome=$outcometag;")
          presentation should include(s"termination=$terminationtag;")
          presentation should include(s"stdout={byteCount=${raw.stdout.byteCount},retainedBytes=${raw.stdout.content.size},truncated=false}")
          presentation should include(s"stderr={byteCount=${raw.stderr.byteCount},retainedBytes=${raw.stderr.content.size},truncated=false}")
          presentation.length should be < 1024
          presentation shouldBe typed.valueC.toString
          Vector("secret-stdout-diagnostic", "secret-stderr-diagnostic", reason, identity,
            selection.value, paths.head.value, workspaceId.value).foreach { secret =>
            presentation should not include secret
          }
          And("the original operation, failed outcome, absent value, receipt and ordered selection remain unchanged")
          typed.target shouldBe target
          typed.operation shouldBe operation
          typed.outcome shouldBe outcome
          typed.value shouldBe None
          typed.processResult shouldBe raw
          typed.processResult should be theSameInstanceAs raw
          typed.selectedPaths shouldBe paths
          typed.selectedPaths should be theSameInstanceAs paths
        }
      }

      "retain termination uncertainty alongside output overflow evidence" in {
        Given("an unconfirmed PendingDiff termination with a truncated capture and a larger observed byte count")
        val raw = result("secret-pending-diff", ProcessExecutionTermination.TerminationUnconfirmed, truncated = true)
          .copy(stdout = ProcessExecutionCapture("secret-pending-diff".getBytes(StandardCharsets.UTF_8).toVector, 142652L, true))
        val outcome = GitExecutionOutcome.Incomplete(ProcessExecutionTermination.TerminationUnconfirmed)
        val typed = GitExecutionResult(GitTarget(workspaceId), GitOperation.PendingDiff(objectId), outcome, None, raw)
        When("the caller requests the unavailable diff value")
        val failure = typed.valueC
        val presentation = failure.toString
        Then("the diagnostic reports both facts without settling the termination or exposing the retained diff")
        failure.isFaillure shouldBe true
        presentation should include("operation=pending-diff")
        presentation should include("outcome=incomplete(termination-unconfirmed);")
        presentation should include("termination=termination-unconfirmed;")
        presentation should include(s"stdout={byteCount=142652,retainedBytes=${raw.stdout.content.size},truncated=true}")
        presentation should include("stderr={byteCount=0,retainedBytes=0,truncated=false}")
        Vector("output-limit-exceeded", "not-executed", "not-started", "secret-pending-diff").foreach { absent =>
          presentation should not include absent
        }
        typed.outcome shouldBe outcome
        typed.processResult should be theSameInstanceAs raw
        typed.processResult.termination shouldBe ProcessExecutionTermination.TerminationUnconfirmed
        typed.processResult.stdout.byteCount shouldBe 142652L
        typed.processResult.stdout.truncated shouldBe true
        typed.selectedPaths shouldBe empty
      }

      "return the identical successful typed value without failure diagnostics" in {
        Given("a complete successful Branch result with its original raw receipt")
        val raw = result("main\n")
        val typed = GitExecutionResult(GitTarget(workspaceId), GitOperation.Branch,
          GitExecutionOutcome.Succeeded, Some("main"), raw)
        When("the caller requests the successful typed value")
        val value = typed.valueC
        Then("the existing successful Consequence and raw result remain identical")
        value shouldBe Consequence.success("main")
        value.toOption shouldBe Some("main")
        typed.processResult should be theSameInstanceAs raw
        typed.selectedPaths shouldBe empty
      }
    }

    "E4 CAND-BUDGET-01 Pending diff admission" should afterWord(
      "in spec:typed-git-execution, example:E4, rules:CAND-BUDGET-01, phase:CC-04") {
      "tighten only stdout to the smaller of the selected property and explicit host ceiling" in {
        Given("admitted host ceilings above and below independently selected ordinary diff properties")
        val cases = Vector((1048576L, None, 1048576L),
          (1048576L, Some(262144L), 262144L), (131072L, Some(1048576L), 131072L),
          (1048576L, Some(1L), 1L))
        cases.foreach { case (hostbytes, selectedbytes, expectedbytes) =>
          val fixture = create(diffStdoutBytes = hostbytes)
          try {
            val values = selectedbytes.map(bytes => GitDiffParameters.key -> ConfigurationValue.NumberValue(BigDecimal(bytes))).toMap
            fixture.context.runtime.setResolvedParameters(ResolvedParameters.fromComponentConfiguration(
              Configuration(values), "git-diff-spec", None))
            When("typed UoW admission resolves PendingDiff and the unrelated Branch operation")
            val diff = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.PendingDiff(objectId)).toOption.get
            val branch = GitExecutionAdmission.resolveC(fixture.context, fixture.target, GitOperation.Branch).toOption.get
            Then("PendingDiff cannot widen host authority and every other limit and operation remains unchanged")
            diff.effectiveLimits shouldBe limits.copy(stdoutBytes = Some(expectedbytes))
            diff.definition.maximumLimits shouldBe limits.copy(stdoutBytes = Some(hostbytes))
            branch.effectiveLimits shouldBe limits
            fixture.context.resolvedParameters.usedEntries.map(_.key) shouldBe selectedbytes.toVector.map(_ => GitDiffParameters.key)
            fixture.profile.driver.executions shouldBe empty
          } finally fixture.close()
        }
      }

      "refuse an invalid PendingDiff property before launching while preserving unrelated operations" in {
        Given("an admitted PendingDiff host declaration and an explicitly invalid ordinary property")
        val fixture = create(diffStdoutBytes = 1048576L)
        try {
          fixture.context.runtime.setResolvedParameters(ResolvedParameters.fromComponentConfiguration(
            Configuration(Map(GitDiffParameters.key -> ConfigurationValue.NullValue)), "git-diff-spec", None))
          When("the owning UoW requests the diff with invalid configuration")
          val diff = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(objectId))
          Then("the configuration fails before any process-driver launch")
          diff.isFaillure shouldBe true
          diff.toString should include(GitDiffParameters.key)
          fixture.profile.driver.executions shouldBe empty
          When("the same owning UoW requests an unrelated Branch observation")
          val branch = GitExecutionSpecSupport.execute(fixture, GitOperation.Branch)
          Then("the unrelated operation retains its existing typed result and admitted limits")
          branch.toOption.get.valueC.toOption shouldBe Some("main")
          fixture.profile.driver.executions.map(_.effectiveLimits) shouldBe Vector(limits)
        } finally fixture.close()
      }

      "retain a complete diff larger than the historical 128 KiB ceiling under an admitted one MiB host limit" in {
        Given("a complete 142652-byte diff receipt and an explicit one MiB PendingDiff host declaration")
        val text = "diff\n" + ("x" * 142647)
        val raw = result(text)
        val fixture = create(outputs = Map(GitOperationKind.PendingDiff -> raw), diffStdoutBytes = 1048576L)
        try {
          fixture.context.runtime.setResolvedParameters(ResolvedParameters.empty())
          When("the owning UoW consumes the complete receipt under the absent-property default")
          val diff = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(objectId)).toOption.get
          Then("the original complete diff and raw process evidence remain available within the selected one MiB bound")
          diff.outcome shouldBe GitExecutionOutcome.Succeeded
          diff.valueC.toOption.get.text shouldBe text
          diff.processResult shouldBe raw
          diff.processResult.stdout.byteCount shouldBe 142652L
          diff.processResult.stdout.truncated shouldBe false
          fixture.profile.driver.executions.map(_.effectiveLimits) shouldBe Vector(limits.copy(stdoutBytes = Some(1048576L)))
        } finally fixture.close()
      }

      "reject either incomplete diff stream without exposing its private content or changing the receipt" in {
        Given("exited diff receipts with truncated or count-inconsistent stdout and stderr")
        val secret = "secret-incomplete-diff-output"
        val complete = result(secret).copy(stderr = result(secret).stdout)
        val invalid = Vector(complete.copy(stdout = complete.stdout.copy(truncated = true)),
          complete.copy(stdout = complete.stdout.copy(byteCount = complete.stdout.byteCount + 1L)),
          complete.copy(stderr = complete.stderr.copy(truncated = true)),
          complete.copy(stderr = complete.stderr.copy(byteCount = complete.stderr.byteCount + 1L)))
        val fixtures = invalid.map(raw => create(outputs = Map(GitOperationKind.PendingDiff -> raw)))
        try {
          When("the owning UoW decodes each incomplete diff receipt")
          val decoded = fixtures.map(fixture => GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(objectId)).toOption.get)
          Then("every incomplete stream produces a typed failure retaining the exact actual capture")
          decoded.zip(invalid).foreach { case (typed, raw) =>
            typed.outcome shouldBe GitExecutionOutcome.InvalidOutput("Incomplete Git diff capture")
            typed.value shouldBe None
            typed.valueC.isFaillure shouldBe true
            typed.valueC.toString should not include secret
            typed.outcome.toString should not include secret
            typed.processResult should be theSameInstanceAs raw
            typed.selectedPaths shouldBe empty
          }
        } finally fixtures.foreach(_.close())
      }

      "apply the selected ceiling while the ordinary Git driver reads a large pending diff" in {
        Given("a real staged diff above 128 KiB under an explicit one MiB host declaration")
        val driver = new LocalProcessExecutionDriver()
        val fixture = create(driver = Some(driver), diffStdoutBytes = 1048576L)
        try {
          setup(fixture.checkout, "init", "--initial-branch=main")
          setup(fixture.checkout, "config", "user.name", "Fixture")
          setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
          Files.writeString(fixture.checkout.resolve("tracked.txt"), "initial\n")
          setup(fixture.checkout, "add", "--", "tracked.txt")
          setup(fixture.checkout, "commit", "-m", "fixture initial")
          Files.writeString(fixture.checkout.resolve("tracked.txt"), "line-012345678901234567890123456789\n" * 5000)
          setup(fixture.checkout, "add", "--", "tracked.txt")
          val parameters = ResolvedParameters.fromComponentConfiguration(Configuration(Map(
            GitDiffParameters.key -> ConfigurationValue.NumberValue(BigDecimal(1048576)))), "git-diff-spec", None)
          fixture.context.runtime.setResolvedParameters(parameters)
          val head = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
          When("ordinary typed Git execution reads the staged diff with the selected one MiB ceiling")
          val complete = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(head)).toOption.get
          Then("the actual complete capture exceeds the historical bound and retains the selected ordinary parameter provenance")
          complete.outcome shouldBe GitExecutionOutcome.Succeeded
          complete.valueC.toOption.get.text.nonEmpty shouldBe true
          complete.processResult.stdout.byteCount should be > 131072L
          complete.processResult.stdout.byteCount should be < 1048576L
          complete.processResult.stdout.content.size.toLong shouldBe complete.processResult.stdout.byteCount
          complete.processResult.stdout.truncated shouldBe false
          complete.processResult.stderr.truncated shouldBe false
          complete.processResult.stderr.byteCount shouldBe complete.processResult.stderr.content.size.toLong
          parameters.usedEntries.map(_.key) shouldBe Vector(GitDiffParameters.key)
          parameters.usedEntries.head.source shouldBe org.goldenport.cncf.config.ResolvedParameter.Source.Component("git-diff-spec")
          And("the same admitted host authority remains fixed while the ordinary property selects 128 KiB")
          val lower = ResolvedParameters.fromComponentConfiguration(Configuration(Map(
            GitDiffParameters.key -> ConfigurationValue.NumberValue(BigDecimal(131072)))), "git-diff-lower-spec", None)
          fixture.context.runtime.setResolvedParameters(lower)
          When("the same ordinary typed Git execution reads the unchanged diff under the lower selected ceiling")
          val bounded = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(head)).toOption.get
          Then("capture-time enforcement produces typed failure and bounded retained bytes without settling uncertain termination")
          bounded.value shouldBe None
          bounded.valueC.isFaillure shouldBe true
          bounded.outcome shouldBe a[GitExecutionOutcome.Incomplete]
          bounded.processResult.stdout.truncated shouldBe true
          bounded.processResult.stdout.byteCount shouldBe 131072L
          bounded.processResult.stdout.content.size.toLong shouldBe bounded.processResult.stdout.byteCount
          lower.usedEntries.map(_.key) shouldBe Vector(GitDiffParameters.key)
        } finally {
          try {
            if (driver.terminationConfirmed) driver.close()
            else intercept[IllegalStateException](driver.close())
          } finally fixture.close()
        }
      }
    }

    "classify only exact EOF blank-line findings with unambiguous literal paths" in {
      Given("actual-shaped EOF diagnostics with literal colons, repeated keys and source context")
      val first = "C:notes.txt:2: new blank line at EOF."
      val second = "other.txt:14: new blank line at EOF."
      val paths = Set("C:notes.txt", "other.txt").map(x => GitPath.parseC(x).toOption.get)
      When("the shared stored-receipt parser observes an exact exit-two EOF-only finding set")
      val findings = GitIndexCheck.parseStoredC(2, s"$first\n+\n$second\n$first\n", "")
      Then("opaque keys remain intact and exact typed paths accompany the EOF-only fact")
      findings.toOption shouldBe Some(GitIndexCheck(Set(first, second), paths, true))
      findings.toOption.get.isClean shouldBe false
      Given("mixed, trailing-space, quoted, escaped, invalid-path and nonpositive-line diagnostics")
      val ordinary = Vector(s"$first\nx:1: trailing whitespace.\n", s"$first \n",
        "\"quoted\\tname\":2: new blank line at EOF.\n", "escaped\\tname:2: new blank line at EOF.\n",
        "../escape:2: new blank line at EOF.\n", "x:0: new blank line at EOF.\n",
        "x:01: new blank line at EOF.\n", "x:2: new blank line at EOF.\r\n")
      When("the same parser observes those completed findings and an empty clean check")
      val decoded = ordinary.map(GitIndexCheck.parseStoredC(2, _, "")) :+ GitIndexCheck.parseStoredC(0, "", "")
      Then("each keeps its ordinary cleanliness and keys without granting EOF-only classification")
      decoded.foreach { value =>
        value.toOption.get.onlyEofBlankLines shouldBe false
        value.toOption.get.eofBlankLinePaths shouldBe empty
      }
      decoded.last.toOption.get.isClean shouldBe true
    }

    "decode selected checks through the owning UoW with complete actual stdout and stderr" in {
      Given("a selected EOF-only observation plus contradictory, incomplete, failed and invalid-text receipts")
      val diagnostic = "selected.txt:2: new blank line at EOF.\n"
      val findings = result(diagnostic, ProcessExecutionTermination.Exited(2))
      val invalid = Vector(findings.copy(stdout = findings.stdout.copy(truncated = true)),
        findings.copy(stderr = findings.stderr.copy(truncated = true)),
        findings.copy(stdout = findings.stdout.copy(byteCount = 1L)),
        findings.copy(stderr = findings.stderr.copy(byteCount = 1L)),
        findings.copy(stderr = result("warning").stdout), result(diagnostic), result("", ProcessExecutionTermination.Exited(2)),
        result("+context\n", ProcessExecutionTermination.Exited(2))) ++
        Vector(Vector[Byte](-61, 40), Vector[Byte](0)).flatMap { bytes =>
          val capture = ProcessExecutionCapture(bytes, bytes.size.toLong, false)
          Vector(findings.copy(stdout = capture), findings.copy(stderr = capture))
        }
      val failed = result(diagnostic, ProcessExecutionTermination.Exited(1))
      val incomplete = result(diagnostic, ProcessExecutionTermination.TimedOut)
      val receipts = Vector(result(), findings) ++ invalid ++ Vector(failed, incomplete)
      val fixtures = receipts.map(raw => create(outputs = Map(GitOperationKind.CheckSelectedIndex -> raw)))
      try {
        When("the owning interpreter consumes each actual selected check receipt")
        val decoded = fixtures.map(GitExecutionSpecSupport.execute(_, GitOperation.CheckSelectedIndex(selectionId, objectId)).toOption.get)
        Then("clean and EOF-only findings are distinct typed observations and every unusable capture retains no value")
        decoded.take(2).map(_.outcome) shouldBe Vector.fill(2)(GitExecutionOutcome.Succeeded)
        decoded.head.valueC.toOption shouldBe Some(GitIndexCheck(Set.empty))
        decoded(1).valueC.toOption shouldBe Some(GitIndexCheck(Set(diagnostic.dropRight(1)),
          Set(GitPath.parseC("selected.txt").toOption.get), true))
        decoded.slice(2, 2 + invalid.size).foreach(_.outcome shouldBe a[GitExecutionOutcome.InvalidOutput])
        decoded.takeRight(2).map(_.outcome) shouldBe Vector(GitExecutionOutcome.Failed(1),
          GitExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut))
        decoded.drop(2).foreach(_.value shouldBe None)
        decoded.zip(receipts).foreach { case (typed, raw) => typed.processResult shouldBe raw }
        fixtures.foreach(_.profile.driver.executions should have size 1)
      } finally fixtures.foreach(_.close())
    }

    "parse raw add modify delete and type-change records with literal filenames and native object lengths" in {
      Given("complete NUL raw diffs with exact five-field metadata and literal colon, backslash and LF paths")
      val fixture = create()
      val names = Vector("C:new\\name\n.txt", "modified", "deleted", "type")
      val paths = names.map(x => GitPath.parseC(x).toOption.get)
      val operations = Vector(GitOperation.SelectedIndexChanges(selectionId, objectId), GitOperation.TreeChanges(objectId, objectId))
      try {
        Vector(40, 64).foreach { length =>
          val oldid = GitObjectId.parseC("a" * length).toOption.get
          val newid = GitObjectId.parseC("b" * length).toOption.get
          val zero = "0" * length
          val raw = s":000000 100644 $zero ${newid.value} A\u0000${names(0)}\u0000" +
            s":100644 100644 ${oldid.value} ${newid.value} M\u0000${names(1)}\u0000" +
            s":100644 000000 ${oldid.value} $zero D\u0000${names(2)}\u0000" +
            s":100644 120000 ${oldid.value} ${newid.value} T\u0000${names(3)}\u0000"
          When("each raw-result operation interprets the complete native Git protocol")
          val decoded = operations.map(parse(fixture, _, raw))
          val empty = operations.map(parse(fixture, _, ""))
          Then("typed records preserve ordered modes, absence, status and exact filename bytes; empty output has no changes")
          val expected = Vector(GitTreeChange("000000", "100644", None, Some(newid), "A", paths(0)),
            GitTreeChange("100644", "100644", Some(oldid), Some(newid), "M", paths(1)),
            GitTreeChange("100644", "000000", Some(oldid), None, "D", paths(2)),
            GitTreeChange("100644", "120000", Some(oldid), Some(newid), "T", paths(3)))
          decoded.foreach(_.toOption shouldBe Some(expected))
          empty.foreach(_.toOption shouldBe Some(Vector.empty))
        }
      } finally fixture.close()
    }

    "reject malformed raw diff metadata paths and incomplete or invalid UTF8 captures" in {
      Given("raw diff rows with inconsistent absence, modes, statuses, fields, OIDs, NUL records and paths")
      val fixture = create()
      val oid = objectId.value
      val zero = "0" * 40
      val row = s":100644 100644 $oid $oid M\u0000literal\u0000"
      val malformed = Vector(row.replace(":100644", ":10064"), row.replace("100644", "100648"),
        row.replace(s"$oid $oid", s"$zero $oid"), row.replace(s"$oid $oid", s"$oid $zero"),
        row.replace(":100644", ":000000"), row.replace(":100644 100644", ":100644 000000"),
        row.replace(" M\u0000", " A\u0000"), row.replace(" M\u0000", " D\u0000"),
        row.replace(" M\u0000", " U\u0000"), row.replace(" M\u0000", " R100\u0000"),
        row.replace(" M\u0000", " C\u0000"), row.replace(" M\u0000", " M100\u0000"),
        row.replace(" M\u0000", " M extra\u0000"), row.replace(" M\u0000", "\u0000"),
        row.replace(s"$oid $oid", s"${"A" * 40} $oid"), row.replace(s"$oid $oid", s"$oid ${"b" * 64}"),
        row.replace("literal", ""), row.replace("literal", "../escape"), row.replace("literal", ".git/config"),
        row.dropRight(1), row + "extra\u0000", row + row,
        s":000000 000000 $zero $zero A\u0000x\u0000", s":000000 000000 $zero $zero D\u0000x\u0000")
      val operations = Vector(GitOperation.SelectedIndexChanges(selectionId, objectId), GitOperation.TreeChanges(objectId, objectId))
      try {
        When("both raw parsers receive malformed text and damaged actual bounded captures")
        val decoded = operations.flatMap(op => malformed.map(parse(fixture, op, _)))
        val captures = Vector(result(row, truncated = true).stdout, result(row).stdout.copy(byteCount = 1L),
          ProcessExecutionCapture(Vector[Byte](-61, 40), 2L, false))
        val damaged = operations.flatMap { op =>
          val metadata = fixture.definitions.find(_._git_metadata.get.kind == op.kind).get._git_metadata.get
          captures.map(GitOutputParser.parse(op, metadata, _, 0))
        }
        Then("none yields usable raw changes")
        (decoded ++ damaged).foreach(_.isLeft shouldBe true)
      } finally fixture.close()
    }

    "require bounded explicit selected path vectors and preserve selected-observation argument limits" in {
      Given("three bounded host kinds, an ordinary kind and a shared admitted workspace")
      val fixture = create()
      val binding = fixture.workspace.bindingC(workspaceId).toOption.get
      val kinds = Vector(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges)
      val path = GitPath.parseC("selected.txt").toOption.get
      val many = Vector.tabulate(257)(n => GitPath.parseC(s"file$n").toOption.get)
      val invalid = kinds.flatMap { kind =>
        val declared = declaration(kind, fixture.endpoint)
        Vector(declared.copy(selectedPaths = null), declared.copy(selectedPaths = Vector.empty),
          declared.copy(selectedPaths = Vector(path, path)), declared.copy(selectedPaths = Vector(null)),
          declared.copy(selectedPaths = many))
      } :+ declaration(GitOperationKind.Head, fixture.endpoint).copy(selectedPaths = Vector(path))
      try {
        When("host definition assembly applies path bounds and typed resolution applies the unchanged finite argument limit")
        val rejected = invalid.map(definition(binding, _))
        val bounded = kinds.map(kind => definition(binding, declaration(kind, fixture.endpoint).copy(
          selectedPaths = many.take(256), limits = limits.copy(argumentCount = Some(512L)))))
        val limited = Vector(GitOperation.CheckSelectedIndex(selectionId, objectId), GitOperation.SelectedIndexChanges(selectionId, objectId)).map { op =>
          val declared = definition(binding, declaration(op.kind, fixture.endpoint).copy(selectedPaths = many.take(256))).toOption.get
          val policy = ProcessExecutionPolicy.createC(Vector(declared)).toOption.get
          val arguments = GitProgramDefinition.typedArgumentsC(op, declared._git_metadata.get).toOption.get
          policy.resolveGitC(declared, ProcessExecutionGrant(declared.capability), arguments)
        }
        val commit = declaration(GitOperationKind.CommitSelected, fixture.endpoint)
        val olddefault = GitProgramDefinition.createC(binding, commit.operation, commit.capability, commit.identity,
          commit.gitExecutable, commit.localBranch, commit.remote, commit.remoteBranch, commit.endpoint,
          commit.commitIdentity, commit.selectionId, commit.selectedPath, commit.commitMessage, commit.limits, commit.environment)
        Then("only explicit valid vectors within both limits are admitted and an old unbounded commit remains rejected")
        rejected.foreach(_.isFaillure shouldBe true)
        bounded.foreach(_.isSuccess shouldBe true)
        limited.foreach(_.isFaillure shouldBe true)
        olddefault.isFaillure shouldBe true
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "decode clean and diagnostic stored index-check receipts with exact opaque keys" in {
      Given("ordinary stored receipt fields with duplicate diagnostics, literal colons, CR and plus-prefixed context")
      val first = " C:literal:name.txt:2: trailing whitespace.\r"
      val second = "other.txt:4: new blank line at EOF."
      val stdout = s"$first\n+source with spaces  \n\n$second\n$first\n"
      When("the pure stored codec decodes clean receipts and findings with or without a final LF")
      val clean = GitIndexCheck.parseStoredC(0, "", "")
      val findings = Vector(stdout, stdout.dropRight(1)).map(GitIndexCheck.parseStoredC(2, _, ""))
      Then("clean has no keys and findings preserve exact diagnostic characters as a set without context or duplicates")
      clean.toOption shouldBe Some(GitIndexCheck(Set.empty))
      clean.toOption.get.isClean shouldBe true
      findings.foreach { decoded =>
        decoded.toOption shouldBe Some(GitIndexCheck(Set(first, second)))
        decoded.toOption.get.isClean shouldBe false
      }
    }

    "reject contradictory stored index-check receipt facts" in {
      Given("contradictory exit/output combinations, unsupported exits, nulls, NUL and malformed Unicode in either stream")
      val diagnostic = "selected.txt:1: trailing whitespace.\n"
      val receipts = Vector((0, diagnostic, ""), (0, "", "warning"), (2, "", ""),
        (2, "+context\n\n+more\n", ""), (2, diagnostic, "warning")) ++
        Vector(1, 7, 128, -1).map(code => (code, diagnostic, "")) ++
        Vector(null, "\u0000", "\ud800", "\udc00").flatMap(text => Vector((2, text, ""), (2, diagnostic, text)))
      When("only the pure historical receipt codec interprets those stored fields")
      val decoded = receipts.map { case (code, out, err) => GitIndexCheck.parseStoredC(code, out, err) }
      Then("no contradictory or invalid historical receipt yields typed findings or a clean check")
      decoded.foreach(_.isFaillure shouldBe true)
    }

    "retain clean and findings observations but reject incomplete invalid and unsupported live index-check captures within owning UoW" in {
      Given("typed index-check intents with both-stream capture faults, contradictory observations and incomplete or failed terminals")
      val diagnostic = "C:selected.txt:1: trailing whitespace.\n"
      val clean = result()
      val findings = result(diagnostic + "+changed  \n", ProcessExecutionTermination.Exited(2))
      val invalid = Vector(clean, findings).flatMap { raw =>
        Vector(raw.copy(stdout = raw.stdout.copy(truncated = true)),
          raw.copy(stderr = raw.stderr.copy(truncated = true)),
          raw.copy(stdout = raw.stdout.copy(byteCount = raw.stdout.byteCount + 1)),
          raw.copy(stderr = raw.stderr.copy(byteCount = raw.stderr.byteCount + 1))) ++
          Vector(Vector[Byte](-61, 40), Vector[Byte](0)).flatMap { bytes =>
            val capture = ProcessExecutionCapture(bytes, bytes.size.toLong, false)
            Vector(raw.copy(stdout = capture), raw.copy(stderr = capture))
          }
      } ++ Vector(result(diagnostic), clean.copy(stderr = result("warning").stdout),
        result("", ProcessExecutionTermination.Exited(2)), result("+context\n", ProcessExecutionTermination.Exited(2)),
        findings.copy(stderr = result("warning").stdout))
      val terminals = Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
        ProcessExecutionTermination.ArtifactLimitExceeded)
      val failed = Vector(1, 7, 128).map(code => result(diagnostic, ProcessExecutionTermination.Exited(code)))
      val incomplete = terminals.map(termination => result(diagnostic, termination))
      val receipts = Vector(clean, findings) ++ invalid ++ failed ++ incomplete
      val fixtures = receipts.map(raw => create(outputs = Map(GitOperationKind.CheckPendingIndex -> raw)))
      try {
        When("the actual owning UoW executes and decodes each check using its actual stdout and stderr")
        val decoded = fixtures.map(GitExecutionSpecSupport.execute(_, GitOperation.CheckPendingIndex(objectId)).toOption.get)
        val parserfacts = Vector("", diagnostic).zip(Vector(0, 2)).map { case (out, code) =>
          parse(fixtures.head, GitOperation.CheckPendingIndex(objectId), out, code)
        }
        Then("only the clean and findings observations yield distinct typed facts while every original receipt remains unchanged")
        decoded.take(2).map(_.outcome) shouldBe Vector.fill(2)(GitExecutionOutcome.Succeeded)
        decoded.take(2).map(_.valueC.toOption.get) shouldBe Vector(GitIndexCheck(Set.empty), GitIndexCheck(Set(diagnostic.dropRight(1))))
        decoded.take(2).map(_.valueC.toOption.get.isClean) shouldBe Vector(true, false)
        decoded(1).processResult.termination shouldBe ProcessExecutionTermination.Exited(2)
        parserfacts.map(_.toOption.get) shouldBe decoded.take(2).map(_.valueC.toOption.get)
        decoded.slice(2, 2 + invalid.size).foreach(_.outcome shouldBe a[GitExecutionOutcome.InvalidOutput])
        decoded.slice(2 + invalid.size, 2 + invalid.size + failed.size).map(_.outcome) shouldBe
          Vector(1, 7, 128).map(GitExecutionOutcome.Failed.apply)
        decoded.takeRight(incomplete.size).map(_.outcome) shouldBe terminals.map(GitExecutionOutcome.Incomplete.apply)
        decoded.drop(2).foreach { typed =>
          typed.value shouldBe None
          typed.valueC.isFaillure shouldBe true
        }
        decoded.zip(receipts).foreach { case (typed, raw) => typed.processResult shouldBe raw }
        fixtures.foreach(_.profile.driver.executions should have size 1)
      } finally fixtures.foreach(_.close())
    }

    "refuse unavailable executables, nonfinite limits and ambient Git environment overrides at trusted definition assembly" in {
      Given("a shared workspace and a valid fixed Head declaration")
      val fixture = create()
      val binding = fixture.workspace.bindingC(workspaceId).toOption.get
      val valid = declaration(GitOperationKind.Head, fixture.endpoint)
      val invalid = Vector(valid.copy(gitExecutable = Paths.get("git")), valid.copy(gitExecutable = fixture.root.resolve("missing")),
        valid.copy(gitExecutable = fixture.root), valid.copy(limits = limits.copy(stdoutBytes = None)),
        valid.copy(environment = Map("GIT_DIR" -> "/other")), valid.copy(environment = Map("GIT_CONFIG_GLOBAL" -> "/other")),
        valid.copy(environment = Map("LC_ALL" -> "C")), valid.copy(environment = Map("PATH" -> "bad\npath")))
      try {
        When("trusted host assembly attempts to create each invalid definition")
        val results = invalid.map(definition(binding, _))
        Then("all declarations fail safely before a driver can receive them")
        results.foreach(_.isFaillure shouldBe true)
        results.mkString should not include fixture.root.toString
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "render the complete thirty-three-operation table with only fixed host inputs and canonical OID slots" in {
      Given("one admitted workspace and all thirty-three fixed runtime Git declarations")
      val fixture = create()
      val oid = objectId.value
      val tracking = "refs/remotes/origin/main"
      val expected = Vector(
        Vector("symbolic-ref", "--quiet", "--short", "HEAD"), Vector("rev-parse", "--show-toplevel"),
        Vector("ls-remote", "--exit-code", "--heads", "--", fixture.endpoint, "refs/heads/main"),
        Vector("rev-parse", "--verify", "HEAD^{commit}"), Vector("rev-parse", "--verify", "HEAD^{tree}"),
        Vector("status", "--porcelain=v1", "-z", "--untracked-files=all"), Vector("rev-parse", "--is-shallow-repository"),
        Vector("replace", "--list"), Vector("rev-parse", "--verify", "--quiet", "MERGE_HEAD"),
        Vector("rev-parse", "--verify", "--quiet", s"$tracking^{commit}"),
        Vector("rev-list", "--left-right", "--count", s"HEAD...$tracking"), Vector("merge-base", "--all", "HEAD", tracking),
        Vector("add", "--all", "--", "."), Vector("write-tree"), Vector("ls-files", "--unmerged", "-z"),
        Vector("fetch", "--no-tags", "--no-write-fetch-head", "--no-recurse-submodules", "--no-auto-maintenance",
          "--no-write-commit-graph", "--no-prune", "--refmap=", "--", fixture.endpoint, s"refs/heads/main:$tracking"),
        Vector("merge", "--ff-only", "--no-edit", "--no-autostash", "--no-verify", "--no-gpg-sign", "--", oid),
        identityArguments ++ Vector("merge", "--no-ff", "--no-commit", "--no-edit", "--no-autostash", "--no-verify", "--no-gpg-sign", "--", oid),
        identityArguments ++ Vector("commit", "--no-verify", "--no-gpg-sign", "--no-edit"),
        Vector("push", "--porcelain", "--no-verify", "--no-follow-tags", "--recurse-submodules=no", "--", fixture.endpoint, s"$oid:refs/heads/main"),
        identityArguments ++ Vector("commit-tree", "--no-gpg-sign", "-m", GitProgramDefinition.checkpointMessage, oid, "-p", oid),
        Vector("update-ref", "--no-deref", "--create-reflog", "-m", GitProgramDefinition.checkpointMessage, "refs/heads/main", oid, oid),
        Vector("show", "--no-patch", "--no-show-signature", "--format=%H%n%T%n%P", oid, "--"), Vector("cat-file", "blob", oid),
        Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--check", oid, "--"),
        Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-color", "--no-renames", oid, "--"),
        Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-only", "-z", oid, "--"),
        Vector("--literal-pathspecs", "add", "--", "selected.txt"), Vector("--literal-pathspecs", "add", "--all", "--", "selected.txt"),
        identityArguments ++ Vector("--literal-pathspecs", "commit", "--only", "--no-verify", "--no-gpg-sign", "-m", "selected checkpoint", "--", "selected.txt"),
        Vector("--literal-pathspecs", "diff", "--cached", "--no-ext-diff", "--no-textconv", "--check", oid, "--", "selected.txt"),
        Vector("--literal-pathspecs", "diff", "--cached", "--no-ext-diff", "--no-textconv", "--raw", "--no-abbrev", "--no-renames", "-z", oid, "--", "selected.txt"),
        Vector("diff-tree", "--no-commit-id", "--no-ext-diff", "--no-textconv", "--raw", "--no-abbrev", "--no-renames", "-r", "-z", oid, oid, "--"))
      try {
        When("trusted typed admission resolves every operation without launching a process")
        val admitted = operations.map(op => GitExecutionAdmission.resolveC(fixture.context, fixture.target, op).toOption.get)
        Then("each exact normal Git vector and fixed environment matches its closed protocol")
        admitted.map(_.effectiveArguments) shouldBe expected.map(GitProgramDefinition.prefix ++ _)
        admitted.map(_.definition._executable_location).distinct shouldBe Vector(Paths.get("/usr/bin/git").toRealPath().toString)
        admitted.foreach { execution =>
          execution.definition._environment.values shouldBe Map("PATH" -> "/usr/bin:/bin") ++ GitProgramDefinition.fixedEnvironment
          execution.effectiveLimits.requireFiniteC.isSuccess shouldBe true
          execution.request.workingDirectory shouldBe None
        }
        fixture.profile.driver.executions shouldBe empty
      } finally fixture.close()
    }

    "preserve literal newline and backslash filenames, type changes and all seven unmerged status pairs" in {
      Given("porcelain v1 NUL records with rename destination before source and legal status pairs")
      val fixture = create()
      val pairs = Vector("M ", "MT", "AM", "R ", "C ", "T ", "D ", " A", " M", " T", " D", " R", " C", "??", "!!") ++ GitOutputParser.unmergedPairs.toVector.sorted
      val rows = pairs.map(pair => pair + " " + "literal\\name\n.txt\u0000" +
        (if (pair.exists(c => c == 'R' || c == 'C')) "old\nname\\.txt\u0000" else "")).mkString
      try {
        When("the CNCF porcelain parser interprets the complete bounded capture")
        val result = parse(fixture, GitOperation.Status, rows)
        Then("filenames and rename direction are preserved and only the exact unmerged pairs carry conflict facts")
        result.toOption.get.map(_.path.value).distinct shouldBe Vector("literal\\name\n.txt")
        result.toOption.get.filter(_.renamedFrom.nonEmpty).map(_.renamedFrom.get.value).distinct shouldBe Vector("old\nname\\.txt")
        result.toOption.get.filter(_.unmerged).map(x => s"${x.indexCode}${x.worktreeCode}").toSet shouldBe GitOutputParser.unmergedPairs
      } finally fixture.close()
    }

    "preserve every ordered path in a complete bounded status capture with 4096 records" in {
      Given("4096 unique untracked file records within the fixed 64 KiB capture limit")
      val paths = Vector.tabulate(4096)(n => s"file$n")
      val rows = paths.map(path => s"?? $path\u0000").mkString
      val fixture = create(outputs = Map(GitOperationKind.Status -> result(rows)))
      try {
        When("the owning typed UoW interprets the complete status capture")
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status).toOption.get
        Then("all exact paths retain their order and every record remains unmerged false")
        rows.getBytes(StandardCharsets.UTF_8).length should be <= (64 * 1024)
        status.outcome shouldBe GitExecutionOutcome.Succeeded
        val entries = status.valueC.toOption.get
        entries.map(_.path.value) shouldBe paths
        entries.map(_.unmerged) shouldBe Vector.fill(4096)(false)
        fixture.profile.driver.executions should have size 1
      } finally fixture.close()
    }

    "reject malformed status, duplicate index stages, duplicate changed paths and incomplete protocol records" in {
      Given("invalid porcelain, ls-files stage and changed-path NUL vectors")
      val fixture = create()
      val row = s"100644 ${objectId.value} 1\tx\u0000"
      try {
        When("each malformed complete capture is interpreted by its typed parser")
        val statuses = Vector("  x\u0000", "ZZ x\u0000", "MDx\u0000", "R  new\u0000", " M x", " M ../x\u0000", " D x\u0000garbage\u0000").map(parse(fixture, GitOperation.Status, _))
        val indices = Vector(row + row, row.replace(" 1\t", " 0\t"), row.replace("100644", "10064"), row.dropRight(1)).map(parse(fixture, GitOperation.UnmergedIndex, _))
        val paths = parse(fixture, GitOperation.PendingPaths(objectId), "x\u0000x\u0000")
        Then("no malformed record becomes usable status, index or changed-path data")
        statuses.foreach(_.isLeft shouldBe true)
        indices.foreach(_.isLeft shouldBe true)
        paths.isLeft shouldBe true
      } finally fixture.close()
    }

    "parse exact index stages, optional not-found, history counts and commit metadata" in {
      Given("complete ls-files, optional-ref, merge-base, counts and commit-format output")
      val fixture = create()
      val oid = objectId.value
      try {
        When("the typed parsers interpret success and exact empty exit-one observations")
        val index = parse(fixture, GitOperation.UnmergedIndex, (1 to 3).map(n => s"100644 $oid $n\tname\n\\.txt\u0000").mkString)
        val optional = Vector(GitOperation.MergeHead, GitOperation.TrackingTip, GitOperation.MergeBase).map(op => parse(fixture, op, "", 1))
        val counts = parse(fixture, GitOperation.Relation, "12\t34\n")
        val metadata = parse(fixture, GitOperation.CommitMetadata(objectId), s"$oid\n$oid\n$oid $oid\n")
        val invalid = Vector(parse(fixture, GitOperation.MergeBase, s"$oid\n$oid\n"),
          parse(fixture, GitOperation.MergeBase, s"$oid\n", 1), parse(fixture, GitOperation.Relation, "9223372036854775808\t0\n"),
          parse(fixture, GitOperation.Relation, "-1\t0\n"), parse(fixture, GitOperation.Head, oid.toUpperCase + "\n"),
          parse(fixture, GitOperation.RemoteTip, s"$oid\trefs/heads/other\n"), parse(fixture, GitOperation.Shallow, "TRUE\n"))
        Then("stages and typed facts remain exact while ambiguous, overflowing or noncanonical observations fail")
        index.toOption.get.map(_.stage) shouldBe Vector(GitIndexStage.Base, GitIndexStage.Local, GitIndexStage.Remote)
        optional.foreach(_.toOption shouldBe Some(None))
        counts.toOption shouldBe Some(GitHistoryCounts(12, 34))
        metadata.toOption shouldBe Some(GitCommitMetadata(objectId, objectId, Vector(objectId, objectId)))
        invalid.foreach(_.isLeft shouldBe true)
      } finally fixture.close()
    }

    "retain binary bytes and require complete capture before any blob or text result is usable" in {
      Given("binary and invalid UTF-8 blobs, truncated and inconsistent capture counts")
      val fixture = create()
      val binary = Vector[Byte](0, -1, 10)
      val malformed = Vector[Byte](-61, 40)
      val metadata = fixture.definitions.find(_._git_metadata.get.kind == GitOperationKind.BlobContent).get._git_metadata.get
      try {
        When("blob and text decoding applies the bounded-capture gate")
        val blobs = Vector(binary, malformed).map(bytes => GitOutputParser.parse(GitOperation.BlobContent(objectId), metadata,
          ProcessExecutionCapture(bytes, bytes.size.toLong, false), 0))
        val incomplete = Vector(ProcessExecutionCapture(binary, binary.size.toLong, true), ProcessExecutionCapture(binary, 20L, false))
          .map(GitOutputParser.parse(GitOperation.BlobContent(objectId), metadata, _, 0))
        val diff = parse(fixture, GitOperation.PendingDiff(objectId), "Binary files a/x and b/x differ\n")
        Then("complete binary payloads preserve bytes without invented text and incomplete payloads never succeed")
        blobs.map(_.toOption.get.bytes) shouldBe Vector(binary, malformed)
        blobs.foreach(_.toOption.get.text shouldBe None)
        incomplete.foreach(_.isLeft shouldBe true)
        diff.toOption shouldBe Some(GitDiff("Binary files a/x and b/x differ\n", true))
      } finally fixture.close()
    }

    "remove exactly one root LF and compare the preserved absolute root with the admitted binding" in {
      Given("a workspace whose actual directory name contains an embedded LF")
      val fixture = create(checkoutName = "checkout\nname")
      try {
        When("root results with a valid terminator, a different root and no terminator are parsed")
        val good = parse(fixture, GitOperation.Root, fixture.checkout.toRealPath().toString + "\n")
        val wrong = parse(fixture, GitOperation.Root, fixture.root.toString + "\n")
        val missing = parse(fixture, GitOperation.Root, fixture.checkout.toString)
        Then("the exact preserved root succeeds and neither mismatch nor incomplete root is accepted")
        good.toOption shouldBe Some(fixture.checkout.toRealPath().toString)
        wrong.isLeft shouldBe true
        missing.isLeft shouldBe true
      } finally fixture.close()
    }

    "validate opaque object, filename, branch and selection values without normalizing actual Git filenames" in {
      Given("generated canonical object IDs and unsafe path, reference and selector strings")
      val ids = Gen.oneOf(40, 64).flatMap(n => Gen.listOfN(n, Gen.oneOf("0123456789abcdef".toSeq)).map(_.mkString))
      When("the public parsers interpret generated and malformed values")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), Prop.forAll(ids)(x => GitObjectId.parseC(x).toOption.map(_.value).contains(x)))
      val paths = Vector("", "/x", "x/../y", "x/.git/config", "x\u0000", "./x").map(GitPath.parseC)
      val branches = Vector("-x", "@", "a..b", "a//b", "x.lock", "a/../b", "a\\b", "a b", "a@{x").map(GitBranchName.parseC)
      val selectors = Vector("X", "a/b", "x" * 65).map(GitSelectionId.parseC)
      val colon = GitPath.parseC("C:notes")
      val literal = GitPath.parseC("a\\b\nc")
      Then("canonical IDs round-trip and unsafe selectors fail without changing legitimate path bytes")
      checked.passed shouldBe true
      paths.foreach(_.isFaillure shouldBe true)
      branches.foreach(_.isFaillure shouldBe true)
      selectors.foreach(_.isFaillure shouldBe true)
      colon.toOption.map(_.value) shouldBe Some("C:notes")
      literal.toOption.map(_.value) shouldBe Some("a\\b\nc")
    }

    "use actual normal Git through the typed UoW for cwd, selected staging, commit, diff, history and fixed local remote" in {
      Given("a temporary repository and bare remote initialized only by fixture setup")
      val driver = new LocalProcessExecutionDriver()
      val fixture = create(driver = Some(driver))
      setup(fixture.checkout, "init", "--initial-branch=main")
      setup(fixture.checkout, "config", "user.name", "Fixture")
      setup(fixture.checkout, "config", "user.email", "fixture@example.invalid")
      Files.writeString(fixture.checkout.resolve("selected.txt"), "initial\n")
      setup(fixture.checkout, "add", "--", "selected.txt")
      setup(fixture.checkout, "commit", "-m", "fixture initial")
      setup(fixture.root, "init", "--bare", fixture.endpoint)
      try {
        When("the actual owning typed DSL observes, stages and commits the selected change")
        val first = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val root = GitExecutionSpecSupport.execute(fixture, GitOperation.Root)
        val branch = GitExecutionSpecSupport.execute(fixture, GitOperation.Branch)
        val cleancheck = GitExecutionSpecSupport.execute(fixture, GitOperation.CheckSelectedIndex(selectionId, first))
        val cleanchanges = GitExecutionSpecSupport.execute(fixture, GitOperation.SelectedIndexChanges(selectionId, first))
        Files.writeString(fixture.checkout.resolve("selected.txt"), "changed\n")
        val status = GitExecutionSpecSupport.execute(fixture, GitOperation.Status)
        val staged = GitExecutionSpecSupport.execute(fixture, GitOperation.StageSelected(selectionId))
        val paths = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingPaths(first))
        val diff = GitExecutionSpecSupport.execute(fixture, GitOperation.PendingDiff(first))
        val committed = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitSelected(selectionId))
        val head = GitExecutionSpecSupport.execute(fixture, GitOperation.Head).toOption.get.valueC.toOption.get
        val pushed = GitExecutionSpecSupport.execute(fixture, GitOperation.Push(head, branchName))
        val fetched = GitExecutionSpecSupport.execute(fixture, GitOperation.Fetch)
        val tip = GitExecutionSpecSupport.execute(fixture, GitOperation.RemoteTip)
        val tracking = GitExecutionSpecSupport.execute(fixture, GitOperation.TrackingTip)
        val relation = GitExecutionSpecSupport.execute(fixture, GitOperation.Relation)
        val metadata = GitExecutionSpecSupport.execute(fixture, GitOperation.CommitMetadata(head))
        Then("actual typed values identify the canonical cwd and selected changes and the configured temporary remote converges")
        root.toOption.get.valueC.toOption shouldBe Some(fixture.checkout.toRealPath().toString)
        branch.toOption.get.valueC.toOption shouldBe Some("main")
        root.toOption.get.selectedPaths shouldBe empty
        branch.toOption.get.selectedPaths shouldBe empty
        cleancheck.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        cleancheck.toOption.get.valueC.toOption shouldBe Some(GitIndexCheck(Set.empty))
        cleanchanges.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded
        cleanchanges.toOption.get.valueC.toOption shouldBe Some(Vector.empty)
        cleancheck.toOption.get.selectedPaths.map(_.value) shouldBe Vector("selected.txt")
        cleanchanges.toOption.get.selectedPaths.map(_.value) shouldBe Vector("selected.txt")
        committed.toOption.get.selectedPaths.map(_.value) shouldBe Vector("selected.txt")
        status.toOption.get.valueC.toOption.get.map(_.path.value) shouldBe Vector("selected.txt")
        Vector(staged, committed, pushed, fetched).foreach(_.toOption.get.outcome shouldBe GitExecutionOutcome.Succeeded)
        paths.toOption.get.valueC.toOption.get.map(_.value) shouldBe Vector("selected.txt")
        diff.toOption.get.valueC.toOption.get.text should include("+changed")
        tip.toOption.get.valueC.toOption shouldBe Some(GitRemoteTip(branchName, head))
        tracking.toOption.get.valueC.toOption shouldBe Some(Some(head))
        relation.toOption.get.valueC.toOption shouldBe Some(GitHistoryCounts(0, 0))
        metadata.toOption.get.valueC.toOption.get.parents shouldBe Vector(first)
      } finally { driver.close(); fixture.close() }
    }
  }
}

private[cncf] final case class GitSpecFixture(
  root: Path,
  checkout: Path,
  endpoint: String,
  target: GitTarget,
  workspace: WorkspaceExecutionAdmission,
  definitions: Vector[ProcessProgramDefinition],
  policy: ProcessExecutionPolicy,
  admission: ProcessExecutionAdmission,
  profile: ProcessExecutionTestProfile,
  context: ExecutionContext
) {
  def close(): Unit = Using.resource(Files.walk(root)) { stream =>
    stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
  }
}

private[cncf] object GitExecutionSpecSupport {
  val owner = ComponentId("org.goldenport.spec.GitOwner")
  val workspaceId = WorkspaceId.parseC("git-project").toOption.get
  val objectId = GitObjectId.parseC("a" * 40).toOption.get
  val branchName = GitBranchName.parseC("main").toOption.get
  val selectionId = GitSelectionId.parseC("chosen").toOption.get
  val limits = ProcessExecutionLimits(Some(5000L), Some(5000L), Some(100L), Some(65536L), Some(65536L),
    Some(65536L), Some(32L), Some(65536L), Some(16L), Some(65536L), Some(65536L))
  val identityArguments = Vector("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "user.useConfigOnly=true")
  val operations: Vector[GitOperation[?]] = Vector(GitOperation.Branch, GitOperation.Root, GitOperation.RemoteTip,
    GitOperation.Head, GitOperation.Tree, GitOperation.Status, GitOperation.Shallow, GitOperation.Replacements,
    GitOperation.MergeHead, GitOperation.TrackingTip, GitOperation.Relation, GitOperation.MergeBase, GitOperation.StageFullState,
    GitOperation.IndexTree, GitOperation.UnmergedIndex, GitOperation.Fetch, GitOperation.FastForward(objectId),
    GitOperation.PendingMerge(objectId), GitOperation.CommitPending, GitOperation.Push(objectId, branchName),
    GitOperation.CreateCheckpoint(objectId, objectId), GitOperation.InstallCheckpoint(objectId, objectId),
    GitOperation.CommitMetadata(objectId), GitOperation.BlobContent(objectId), GitOperation.CheckPendingIndex(objectId),
    GitOperation.PendingDiff(objectId), GitOperation.PendingPaths(objectId), GitOperation.StageSelected(selectionId),
    GitOperation.StageSelectedAll(selectionId), GitOperation.CommitSelected(selectionId),
    GitOperation.CheckSelectedIndex(selectionId, objectId), GitOperation.SelectedIndexChanges(selectionId, objectId),
    GitOperation.TreeChanges(objectId, objectId))

  def capability(kind: GitOperationKind): ProcessCapabilityId = ProcessCapabilityId.parseC("git-" + kind.wireName).toOption.get

  def declaration(kind: GitOperationKind, endpoint: String): GitRuntimeDeclaration = GitRuntimeDeclaration(
    workspaceId, kind, capability(kind), "git-" + kind.wireName, Paths.get("/usr/bin/git"), branchName, "origin", branchName,
    endpoint, if (kind.requiresIdentity) Some(GitCommitIdentity("Fixture", "fixture@example.invalid")) else None,
    if (kind.selected) Some(selectionId) else None,
    if (kind == GitOperationKind.StageSelected || kind == GitOperationKind.StageSelectedAll) Some(GitPath.parseC("selected.txt").toOption.get) else None,
    if (kind == GitOperationKind.CommitSelected) Some("selected checkpoint") else None, limits, Map("PATH" -> "/usr/bin:/bin"),
    if (Set(GitOperationKind.CommitSelected, GitOperationKind.CheckSelectedIndex, GitOperationKind.SelectedIndexChanges).contains(kind))
      Vector(GitPath.parseC("selected.txt").toOption.get) else Vector.empty)

  def definition(binding: WorkspaceBinding, declaration: GitRuntimeDeclaration): Consequence[ProcessProgramDefinition] =
    GitProgramDefinition.createC(binding, declaration.operation, declaration.capability, declaration.identity,
      declaration.gitExecutable, declaration.localBranch, declaration.remote, declaration.remoteBranch, declaration.endpoint,
      declaration.commitIdentity, declaration.selectionId, declaration.selectedPath, declaration.commitMessage,
      declaration.limits, declaration.environment, declaration.selectedPaths)

  def result(text: String = "", termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0),
    truncated: Boolean = false): ProcessExecutionResult = {
    val bytes = text.getBytes(StandardCharsets.UTF_8).toVector
    ProcessExecutionResult(termination, ProcessExecutionCapture(bytes, bytes.size.toLong, truncated),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 1L, "git-spec")
  }

  def sample(kind: GitOperationKind, checkout: Path): String = kind match {
    case GitOperationKind.Branch => "main\n"
    case GitOperationKind.Root => checkout.toRealPath().toString + "\n"
    case GitOperationKind.RemoteTip => objectId.value + "\trefs/heads/main\n"
    case GitOperationKind.Head | GitOperationKind.Tree | GitOperationKind.IndexTree | GitOperationKind.CreateCheckpoint |
        GitOperationKind.MergeHead | GitOperationKind.TrackingTip | GitOperationKind.MergeBase => objectId.value + "\n"
    case GitOperationKind.Status => " M selected.txt\u0000"
    case GitOperationKind.Shallow => "false\n"
    case GitOperationKind.Relation => "0\t0\n"
    case GitOperationKind.CommitMetadata => s"${objectId.value}\n${objectId.value}\n\n"
    case GitOperationKind.BlobContent => "blob\n"
    case GitOperationKind.PendingDiff => "diff\n"
    case GitOperationKind.PendingPaths => "selected.txt\u0000"
    case GitOperationKind.SelectedIndexChanges | GitOperationKind.TreeChanges =>
      s":000000 100644 ${"0" * 40} ${objectId.value} A\u0000selected.txt\u0000"
    case _ => ""
  }

  def create(
    permission: WorkspacePermission = WorkspacePermission.Manage,
    component: ComponentId = owner,
    workspaceInstalled: Boolean = true,
    processInstalled: Boolean = true,
    capabilityGranted: Boolean = true,
    workspaceGranted: Boolean = true,
    driver: Option[ProcessExecutionDriver] = None,
    outputs: Map[GitOperationKind, ProcessExecutionResult] = Map.empty,
    checkoutName: String = "checkout",
    selectedPath: String = "selected.txt",
    diffStdoutBytes: Long = limits.stdoutBytes.get
  ): GitSpecFixture = {
    val base = Files.createDirectories(Paths.get("target", "git-execution-spec").toAbsolutePath)
    val root = Files.createTempDirectory(base, "case-")
    val checkout = Files.createDirectory(root.resolve(checkoutName))
    val endpoint = root.resolve("remote.git").toString
    val workspace = WorkspaceExecutionAdmission.createC(Vector(workspaceId -> checkout),
      if (workspaceGranted) Vector(WorkspaceGrant(owner, workspaceId, permission)) else Vector.empty).toOption.get
    val binding = workspace.bindingC(workspaceId).toOption.get
    val definitions = GitOperationKind.values.toVector.map { kind =>
      val declared = declaration(kind, endpoint)
      val selected = declared.copy(
        limits = if (kind == GitOperationKind.PendingDiff) declared.limits.copy(stdoutBytes = Some(diffStdoutBytes)) else declared.limits,
        selectedPath = if (declared.selectedPath.nonEmpty) Some(GitPath.parseC(selectedPath).toOption.get) else None,
        selectedPaths = if (declared.selectedPaths.nonEmpty) Vector(GitPath.parseC(selectedPath).toOption.get) else Vector.empty)
      definition(binding, selected).toOption.get
    }
    val policy = ProcessExecutionPolicy.createC(definitions).toOption.get
    val grants = if (capabilityGranted) definitions.map(x => ProcessExecutionGrant(x.capability)) else Vector.empty
    val admission = ProcessExecutionAdmission.forComponentsC(policy, Map(owner -> grants)).toOption.get
    val profile = ProcessExecutionTestProfile(GitOperationKind.values.map(kind => capability(kind) ->
      outputs.getOrElse(kind, result(sample(kind, checkout)))).toMap)
    val context = contextFor(root, if (workspaceInstalled) Some(workspace) else None,
      if (processInstalled) Some(admission) else None, driver.getOrElse(profile.driver), component)
    GitSpecFixture(root, checkout, endpoint, GitTarget(workspaceId), workspace, definitions, policy, admission, profile, context)
  }

  def contextFor(root: Path, workspace: Option[WorkspaceExecutionAdmission], admission: Option[ProcessExecutionAdmission],
    driver: ProcessExecutionDriver, component: ComponentId = owner): ExecutionContext = {
    val area = Files.createDirectories(root.resolve("workareas"))
    GlobalContext.set(GlobalContext(new WorkAreaSpace(WorkAreaGroup(new FileWorkArea(WorkAreaId("git_spec"),
      WorkArea.Limit(None, None, None), area)))))
    val execution = ExecutionContext.create()
    val runtime = ScopeContext(ScopeKind.Runtime, "git-spec", None, execution.observability,
      processexecutiondriveroption = Some(driver), processexecutionadmissionoption = admission,
      workspaceExecutionAdmissionOption = workspace)
    val owning = Component.create(component.name, component, ComponentInstanceId.default(component), Protocol.empty)
    execution.withScope(Component.Context(component.name, runtime, owning, ComponentOrigin.Unknown)
      .createChildScope(ScopeKind.Service, "git").createChildScope(ScopeKind.Action, "execute"))
  }

  def execute[A](fixture: GitSpecFixture, operation: GitOperation[A]): Consequence[GitExecutionResult[A]] =
    new UnitOfWorkInterpreter(new UnitOfWork(fixture.context)).run(
      new GitSpecBehavior(Behavior.Core(fixture.context, None, None)).program(fixture.target, operation))

  def parse[A](fixture: GitSpecFixture, operation: GitOperation[A], text: String, exitCode: Int = 0): Either[String, A] = {
    val metadata = fixture.definitions.find(_._git_metadata.get.kind == operation.kind).get._git_metadata.get
    GitOutputParser.parse(operation, metadata, result(text).stdout, exitCode)
  }

  /** Fixture initialization only; subject operations always use the owning typed UoW DSL. */
  def setup(directory: Path, arguments: String*): Unit = {
    val builder = new ProcessBuilder((Vector("/usr/bin/git") ++ arguments)*).directory(directory.toFile).redirectErrorStream(true)
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1")
    builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null")
    val process = builder.start()
    val output = Using.resource(process.getInputStream)(_.readAllBytes())
    if (process.waitFor() != 0) throw new IllegalStateException("Git fixture initialization failed: " + new String(output, StandardCharsets.UTF_8))
  }
}

private[cncf] final class GitSpecBehavior(val behaviorCore: Behavior.Core) extends Behavior {
  def program[A](target: GitTarget, operation: GitOperation[A]): ExecUowM[GitExecutionResult[A]] = operation match {
    case GitOperation.Branch => git_branch(target)
    case GitOperation.Root => git_root(target)
    case GitOperation.RemoteTip => git_remote_tip(target)
    case GitOperation.Head => git_head(target)
    case GitOperation.Tree => git_tree(target)
    case GitOperation.Status => git_status(target)
    case GitOperation.Shallow => git_shallow(target)
    case GitOperation.Replacements => git_replacements(target)
    case GitOperation.MergeHead => git_merge_head(target)
    case GitOperation.TrackingTip => git_tracking_tip(target)
    case GitOperation.Relation => git_relation(target)
    case GitOperation.MergeBase => git_merge_base(target)
    case GitOperation.StageFullState => git_stage_full_state(target)
    case GitOperation.IndexTree => git_index_tree(target)
    case GitOperation.UnmergedIndex => git_unmerged_index(target)
    case GitOperation.Fetch => git_fetch(target)
    case GitOperation.FastForward(tip) => git_fast_forward(target, tip)
    case GitOperation.PendingMerge(tip) => git_pending_merge(target, tip)
    case GitOperation.CommitPending => git_commit_pending(target)
    case GitOperation.Push(tip, branch) => git_push(target, tip, branch)
    case GitOperation.CreateCheckpoint(tree, parent) => git_create_checkpoint(target, tree, parent)
    case GitOperation.InstallCheckpoint(commit, parent) => git_install_checkpoint(target, commit, parent)
    case GitOperation.CommitMetadata(commit) => git_commit_metadata(target, commit)
    case GitOperation.BlobContent(blob) => git_blob_content(target, blob)
    case GitOperation.CheckPendingIndex(parent) => git_check_pending_index(target, parent)
    case GitOperation.PendingDiff(parent) => git_pending_diff(target, parent)
    case GitOperation.PendingPaths(parent) => git_pending_paths(target, parent)
    case GitOperation.StageSelected(selectionId) => git_stage_selected(target, selectionId)
    case GitOperation.StageSelectedAll(selectionId) => git_stage_selected_all(target, selectionId)
    case GitOperation.CommitSelected(selectionId) => git_commit_selected(target, selectionId)
    case GitOperation.CheckSelectedIndex(selectionid, parent) => git_check_selected_index(target, selectionid, parent)
    case GitOperation.SelectedIndexChanges(selectionid, parent) => git_selected_index_changes(target, selectionid, parent)
    case GitOperation.TreeChanges(before, after) => git_tree_changes(target, before, after)
  }
}
