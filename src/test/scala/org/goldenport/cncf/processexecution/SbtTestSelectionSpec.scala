package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 *  version Oct. 30, 2026
 * @version Nov. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class SbtTestSelectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _classes = Vector("org.example.SelectedSpec", "org.other.OtherSpec")
  private val _target = SbtTarget(WorkspaceId.parseC("selected").toOption.get)

  "Trusted exact TestOnly selection" should {
    "accept bounded distinct fully qualified ASCII names only for TestOnly" in {
      Given("valid names at both bounds and invalid counts, grammar, duplicates and ambiguous simple names")
      val valid = Vector(Vector("a.B"), Vector("a." + "B" * 254), (1 to 16).map(x => s"a.Suite$x").toVector)
      val invalid = Vector(Vector.empty[String], Vector("Simple"), Vector("a." + "B" * 255),
        (1 to 17).map(x => s"a.Suite$x").toVector, Vector("a.B", "a.B"), Vector("a.B", "b.B"),
        Vector("a.B", ""), Vector("a.B", null), Vector(" a.B"), Vector("a.B "), Vector("a.Ü"),
        Vector("a.B*"), Vector("a.B$"), Vector("a.B-C"), Vector("a.B;test"), Vector("a/B"), Vector("a.\nB"))
      When("trusted runtime policy validates the closed operation and exact vector")
      val accepted = valid.map(SbtTestSelectionPolicy.validateC(SbtOperation.TestOnly, _))
      val rejected = invalid.map(SbtTestSelectionPolicy.validateC(SbtOperation.TestOnly, _))
      Then("only bounded exact selections are admitted and Compile/Test/FixCheck retain empty selections")
      accepted.foreach(_.isSuccess shouldBe true)
      rejected.foreach(_.isFaillure shouldBe true)
      Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck, SbtOperation.PublishLocal).foreach { operation =>
        SbtTestSelectionPolicy.validateC(operation, Vector.empty).isSuccess shouldBe true
        SbtTestSelectionPolicy.validateC(operation, _classes).isFaillure shouldBe true
        SbtTestSelectionPolicy.validateC(operation, null).isFaillure shouldBe true
      }
    }

    "deny generated command fragments in every exact selected class position" in {
      Given("generated class names contaminated by shell, wildcard, Unicode, separator or whitespace fragments")
      val fragments = Gen.oneOf("*", "$", ";", "/", "-", " ", "\t", "\u0000", "Ü", "\n")
      When("the trusted policy evaluates each contaminated name without normalization")
      val property = Prop.forAll(fragments, Gen.choose(0, 12)) { (fragment, position) =>
        val base = "SelectedSuite"
        val name = "org.example." + base.take(position) + fragment + base.drop(position)
        SbtTestSelectionPolicy.validateC(SbtOperation.TestOnly, Vector(name)).isFaillure
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      Then("every generated fragment is denied instead of becoming an SBT expression")
      checked.passed shouldBe true
    }
  }

  "Fixed check selection policy" should {
    "allow only an absent scenario for FixCheck" in {
      Given("the fixed check operation and absent, exact or malformed scenario selections")
      val selections = Vector(None, Some(SbtScenarioSelection(_classes.head, "selected case")), Some(null), null)
      When("the existing scenario policy validates each FixCheck intent")
      val results = selections.map(SbtScenarioSelection.validateOptionC(_, SbtOperation.FixCheck))
      Then("only the absent selector is admitted without adding a scenario rendering path")
      results.head.isSuccess shouldBe true
      results.tail.foreach(_.isFaillure shouldBe true)
    }
  }

  "Selected ScalaTest report interpretation" should {
    "require one exact selected class and exactly one successful scenario" in {
      Given("an exact scenario and supported reports for one, zero or multiple successful tests")
      val classes = _classes.take(1)
      val scenario = Some(SbtScenarioSelection(classes.head, "parameter case 1"))
      val reports = Vector(1L, 0L, 2L).map(x => _process(_report(classes, x, false)))
      When("scenario-aware interpretation checks actual counts and selected class agreement")
      val decoded = reports.map(SbtTestSelectionDecoder.decode(classes, _, scenario))
      val mismatch = SbtTestSelectionDecoder.decode(_classes, reports.head, scenario)
      Then("only the complete one-test report verifies and class or count disagreement stays unverified")
      decoded shouldBe Vector(SbtTestSelectionResult.Verified(classes, 1, 1L),
        SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.ScenarioMismatch),
        SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.ScenarioMismatch))
      mismatch shouldBe SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.ScenarioMismatch)
    }

    "retain conservative failures for incomplete, wrong-class and failed scenario reports" in {
      Given("an exact scenario and a valid ordinary one-test report")
      val classes = _classes.take(1)
      val scenario = Some(SbtScenarioSelection(classes.head, "parameter case 1"))
      val report = _report(classes, 1L, false)
      val cases = Vector(
        _process(report).copy(stdout = _process(report).stdout.copy(truncated = true)) -> SbtTestSelectionFailure.IncompleteCapture,
        _process(report.replace(classes.head, "org.other.WrongSpec")) -> SbtTestSelectionFailure.SuiteMismatch,
        _process(report.replace("failed 0", "failed 1")) -> SbtTestSelectionFailure.FailedTests,
        _process(report.replace("[info] All tests passed.", "")) -> SbtTestSelectionFailure.InvalidReport)
      When("each captured report is interpreted with the exact scenario")
      val decoded = cases.map { case (process, reason) =>
        SbtTestSelectionDecoder.decode(classes, process, scenario) -> reason
      }
      Then("an exact request cannot turn incomplete or contradictory output into success")
      decoded.foreach { case (result, reason) => result shouldBe SbtTestSelectionResult.Rejected(reason) }
    }

    "retain every host authority and projection fact when deriving a narrower definition" in {
      Given("one trusted TestOnly definition with two allowed classes, fixed environment and finite limits")
      val root = java.nio.file.Files.createTempDirectory("sbt-scenario-policy-")
      val workspaceid = WorkspaceId.parseC("selected").toOption.get
      val workspace = org.goldenport.cncf.workspace.WorkspaceExecutionAdmission.createC(
        Vector(workspaceid -> root), Vector.empty).toOption.get
      val binding = workspace.bindingC(workspaceid).toOption.get
      val limits = ProcessExecutionLimits(Some(10L), Some(20L), Some(5L), Some(1000L), Some(1000L),
        Some(1000L), Some(16L), Some(4096L), Some(2L), Some(1000L), Some(2000L))
      val definition = SbtProgramDefinition.createC(binding, SbtOperation.TestOnly,
        ProcessCapabilityId.parseC("selected").toOption.get, "selected", java.nio.file.Path.of("/usr/bin/true"),
        limits, Map("BUILD_PROFILE" -> "scenario"), _classes).toOption.get
      val selection = SbtScenarioSelection(_classes.head, "parameter case 1")
      try {
        When("the admitted class narrows the immutable definition")
        val narrowed = ProcessProgramDefinition.narrowSbtScenarioC(definition, Some(selection)).toOption.get
        Then("binding, capability, executable, environment, limits and allowed projections remain identical")
        (narrowed._sbt_metadata.get.binding eq binding) shouldBe true
        narrowed.capability shouldBe definition.capability
        narrowed.safeProgramIdentity shouldBe definition.safeProgramIdentity
        narrowed._executable_location shouldBe definition._executable_location
        (narrowed._environment eq definition._environment) shouldBe true
        narrowed.maximumLimits shouldBe definition.maximumLimits
        narrowed.allowedArtifacts shouldBe definition.allowedArtifacts
        narrowed.allowsWorkingDirectory shouldBe definition.allowsWorkingDirectory
        narrowed.allowedInputFiles shouldBe definition.allowedInputFiles
        narrowed.allowedInputFilePaths shouldBe definition.allowedInputFilePaths
        narrowed.allowedResourceTrees shouldBe definition.allowedResourceTrees
        narrowed._sbt_metadata.get.testClasses shouldBe Vector(selection.suiteClassName)
        narrowed._sbt_metadata.get.scenario shouldBe Some(selection)
        definition._sbt_metadata.get.testClasses shouldBe _classes
        definition._sbt_metadata.get.scenario shouldBe None
        narrowed.argumentPolicy shouldBe ProcessArgumentPolicy(narrowed.fixedArguments)
        narrowed.fixedArguments.last shouldBe "testOnly org.example.SelectedSpec -- -t \"parameter case 1\""
      } finally java.nio.file.Files.deleteIfExists(root)
    }

    "verify positive single and multiple suites through exact or unique simple headers" in {
      Given("one selected class or multiple classes, each with complete strict UTF8 captures and a positive report")
      val selections = Vector(_classes.take(1), _classes)
      val cases = selections.flatMap(classes => Vector(false, true).map(simple =>
        (classes, _process(_report(classes, 7L, simple)))))
      When("the pure decoder interprets each ordinary supported report")
      val decoded = cases.map { case (classes, process) => SbtTestSelectionDecoder.decode(classes, process) }
      Then("the result retains only the actual requested FQNs and the positive suite and test counts")
      decoded shouldBe cases.map { case (classes, _) => SbtTestSelectionResult.Verified(classes, classes.size, 7L) }
    }

    "preserve selected mappings and positive Long counts for generated supported reports" in {
      Given("generated nonzero test counts, one to sixteen unique suites and either supported header spelling")
      val counts = Gen.oneOf(Gen.choose(1L, 1000000L), Gen.const(Long.MaxValue))
      When("the decoder interprets generated reports with unrelated build log lines")
      val property = Prop.forAll(Gen.choose(1, 16), counts, Gen.oneOf(true, false)) { (size, count, simple) =>
        val classes = (1 to size).map(x => s"org.example.Suite$x").toVector
        val process = _process("[info] compiling sources\n" + _report(classes, count, simple) + "\n[success] done")
        SbtTestSelectionDecoder.decode(classes, process) == SbtTestSelectionResult.Verified(classes, size, count)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      Then("supported reports preserve the exact selected FQNs and counts including Long maximum")
      checked.passed shouldBe true
    }

    "reject zero, missing, duplicate, contradictory, negative and overflowed reports" in {
      Given("a positive report and malformed or inconsistent terminal and suite records")
      import SbtTestSelectionFailure.*
      val report = _report(_classes, 7L, false)
      val cases = Vector(
        _report(_classes, 0L, false) -> ZeroTests,
        report.replace("org.example.SelectedSpec:", "org.unknown.UnknownSpec:") -> SuiteMismatch,
        report.replace("org.other.OtherSpec:", "SelectedSpec:") -> SuiteMismatch,
        ("[info] org.example.SelectedSpec:\n" + report) -> SuiteMismatch,
        report.replace("completed 2", "completed 1") -> SuiteMismatch,
        report.replace("succeeded 7", "succeeded 6") -> InvalidReport,
        (report + "\n[info] Total number of tests run: 7") -> InvalidReport,
        (report + "\n[info] Total number of tests run: 8") -> InvalidReport,
        (report + "\n[info] Suites: completed 2, aborted 0") -> InvalidReport,
        (report + "\n[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0") -> InvalidReport,
        (report + "\n[info] All tests passed.") -> InvalidReport,
        (report + "\n[info] *** TESTS FAILED ***") -> FailedTests,
        (report + "\n[info] No tests were executed.") -> InvalidReport,
        report.replace("[info] All tests passed.", "") -> InvalidReport,
        report.replace("Total number of tests run: 7", "Total number of tests run: -1") -> InvalidReport,
        report.replace("Total number of tests run: 7", "Total number of tests run: 9223372036854775808") -> InvalidReport,
        report.replace("completed 2", "completed -1") -> InvalidReport,
        report.replace("aborted 0", "aborted 9223372036854775808") -> InvalidReport,
        report.replace("ignored 0", "ignored -1") -> InvalidReport,
        report.replace("pending 0", "pending 9223372036854775808") -> InvalidReport,
        report.replace("failed 0", "failed 1") -> FailedTests,
        report.replace("canceled 0", "canceled 1") -> FailedTests,
        report.replace("aborted 0", "aborted 1") -> FailedTests)
      When("the pure decoder consumes each complete capture")
      val decoded = cases.map { case (text, reason) => SbtTestSelectionDecoder.decode(_classes, _process(text)) -> reason }
      Then("no malformed or contradictory report verifies tests and each refusal remains typed result data")
      decoded.foreach { case (result, reason) => result shouldBe SbtTestSelectionResult.Rejected(reason) }
      SbtTestSelectionDecoder.decode(Vector.empty, _process(report)) shouldBe SbtTestSelectionResult.Rejected(MissingSelection)
    }

    "retain actual observations for headerless and partial reports" in {
      Given("headerless and partially named reports, including three selected suites and a contradictory zero suite count")
      val classes = _classes
      val headerless = _report(classes, 24L, false).linesIterator.drop(2).mkString("\n")
      val raw = _process("[warn] unrelated warning\nlogback diagnostic\n" + headerless)
      val three = classes :+ "org.third.ThirdSpec"
      val threeheaderless = _report(three, 24L, false).linesIterator.drop(3).mkString("\n")
      When("the reports are assessed against their original exact selections")
      val assessed = SbtTestReassessment.assess(classes, raw)
      val assessedthree = SbtTestReassessment.assess(three, _process(threeheaderless))
      val zero = SbtTestReassessment.assess(three, _process(threeheaderless.replace("completed 3", "completed 0")))
      Then("the observed count can confirm the selection while absent names remain unknown")
      assessedthree.selection shouldBe SbtTestSelectionResult.Verified(three, 3, 24L)
      assessedthree.observation.suiteNames shouldBe empty
      assessedthree.observation.nameConfirmation shouldBe SbtSuiteNameConfirmation.Unknown
      assessedthree.observation.completedSuites shouldBe Some(3L)
      zero.selection shouldBe SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.SuiteMismatch)
      assessed.selection shouldBe SbtTestSelectionResult.Verified(classes, 2, 24L)
      assessed.observation.suiteNames shouldBe Vector.empty
      assessed.observation.nameConfirmation shouldBe SbtSuiteNameConfirmation.Unknown
      assessed.observation.testsRun shouldBe Some(24L)
      assessed.observation.completedSuites shouldBe Some(2L)
      assessed.observation.succeeded shouldBe Some(24L)
      val partial = SbtTestReassessment.assess(classes, _process("[info] SelectedSpec:\n" + headerless))
      partial.observation.suiteNames shouldBe Vector("SelectedSpec")
      partial.observation.nameConfirmation shouldBe SbtSuiteNameConfirmation.Partial
      partial.selection shouldBe SbtTestSelectionResult.Verified(classes, 2, 24L)
      val contrary = SbtTestReassessment.assess(classes, _process("[info] WrongSpec:\n" + headerless))
      contrary.selection shouldBe SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.SuiteMismatch)
      contrary.observation.nameConfirmation shouldBe SbtSuiteNameConfirmation.Contradictory
    }

    "reassess a retained one-suite 24-pass execution without inventing a name" in {
      Given("an optional host-selected retained result, or a deterministic representative result")
      val representative = """[warn] compiler warning
logback diagnostic
[info] Run completed in 1 second.
[info] Total number of tests run: 24
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 24, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed."""
      val fixture = sys.env.get("CNCF_SPEC_RETAINED_SBT_RESULT").map { path =>
        val json = io.circe.parser.parse(java.nio.file.Files.readString(java.nio.file.Path.of(path))).toOption.get
        val cursor = json.hcursor
        val process = cursor.downField("process")
        val termination = process.downField("termination")
        termination.get[String]("kind").toOption.get shouldBe "exited"
        cursor.downField("scenario").focus.get.isNull shouldBe true
        process.get[Vector[io.circe.Json]]("artifacts").toOption.get shouldBe empty
        def _capture_(name: String): ProcessExecutionCapture = {
          val capture = process.downField(name)
          capture.get[String]("text_status").toOption.get shouldBe "complete"
          capture.get[Long]("trailing_undecoded_bytes").toOption.get shouldBe 0L
          ProcessExecutionCapture(
            java.util.Base64.getDecoder.decode(capture.get[String]("content_base64").toOption.get).toVector,
            capture.get[Long]("byte_count").toOption.get,
            capture.get[Boolean]("truncated").toOption.get)
        }
        val raw = ProcessExecutionResult(
          ProcessExecutionTermination.Exited(termination.get[Int]("exit_code").toOption.get),
          _capture_("stdout"), _capture_("stderr"), Vector.empty,
          process.get[Long]("elapsed_millis").toOption.get,
          process.get[String]("safe_program_identity").toOption.get)
        val expected = cursor.downField("expected")
        (cursor.get[Vector[String]]("testClasses").toOption.get,
          SbtTarget(WorkspaceId.parseC(cursor.get[String]("workspaceId").toOption.get).toOption.get),
          raw, expected.get[Long]("testsRun").toOption.get,
          expected.get[Long]("completedSuites").toOption.get,
          expected.get[String]("nameConfirmation").toOption.get,
          expected.get[Vector[String]]("suiteNames").toOption.get)
      }.getOrElse((Vector("org.example.SelectedSpec"), _target, _process(representative),
        24L, 1L, "Unknown", Vector.empty[String]))
      val (classes, target, raw, count, suites, confirmation, names) = fixture
      When("the public pure reassessment interprets the original selection and retained process facts")
      val assessment = SbtTestReassessment.reassess(target, classes, raw)
      Then("the execution succeeds on its observed counts while suite names remain unknown")
      count shouldBe 24L
      suites shouldBe classes.size.toLong
      confirmation shouldBe "Unknown"
      names shouldBe empty
      assessment.outcome shouldBe SbtExecutionOutcome.Succeeded
      assessment.processResult shouldBe raw
      assessment.testObservation.get.suiteNames shouldBe names
      assessment.testObservation.get.nameConfirmation shouldBe SbtSuiteNameConfirmation.Unknown
      assessment.testObservation.get.testsRun shouldBe Some(count)
      assessment.testObservation.get.completedSuites shouldBe Some(suites)
      SbtTestReassessment.reassess(target, classes, raw.copy(termination = ProcessExecutionTermination.TimedOut))
        .outcome shouldBe SbtExecutionOutcome.Incomplete(ProcessExecutionTermination.TimedOut)
    }

    "retain failed counts and diagnostic lines" in {
      Given("a completed report with a failed test count and diagnostic line")
      val report = _report(_classes, 7L, false).replace("failed 0", "failed 1") + "\n[error] *** TEST FAILED ***"
      When("the original capture is assessed")
      val assessed = SbtTestReassessment.assess(_classes, _process(report))
      Then("failure counts and details are retained and the validation fails")
      assessed.selection shouldBe SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.FailedTests)
      assessed.observation.failed shouldBe Some(1L)
      assessed.observation.failureDetails shouldBe Vector("*** TEST FAILED ***")
      SbtExecutionResult(_target, SbtOperation.TestOnly, _process(report), Some(assessed.selection))
        .outcome shouldBe SbtExecutionOutcome.TestValidationFailed(SbtTestSelectionFailure.FailedTests)
    }

    "retain complete observed lines from incomplete captures without accepting their tails" in {
      Given("truncated stdout and stderr, and a byte-count mismatch with complete evidence and partial tails")
      def capture(text: String, extra: Vector[Byte] = Vector.empty, truncated: Boolean = true): ProcessExecutionCapture = {
        val bytes = text.getBytes(StandardCharsets.UTF_8).toVector ++ extra
        ProcessExecutionCapture(bytes, bytes.size.toLong, truncated)
      }
      val stdout = capture("[info] SelectedSpec:\n[info] Total number of tests run: 7\n" +
        "[info] Suites: completed 2, aborted 0\n" +
        "[info] Tests: succeeded 6, failed 1, canceled 0, ignored 0, pending 0\n" +
        "[info] WrongSpec:")
      val stderr = capture("[error] *** TEST FAILED ***\n[error] *** TEST ABORTED")
      val raw = _process("").copy(stdout = stdout, stderr = stderr)
      val mismatch = raw.copy(stdout = stdout.copy(truncated = false, byteCount = stdout.byteCount + 1),
        stderr = stderr.copy(truncated = false, byteCount = stderr.byteCount + 1))
      val malformedTail = raw.copy(stdout = capture("[info] SelectedSpec:\n", Vector(0xc3.toByte)),
        stderr = capture("[error] *** TEST FAILED ***\n", Vector(0xc3.toByte)))
      val partialCount = raw.copy(stdout = capture("[info] Total number of tests run: 7"))
      When("the retained captures are reassessed without executing a process")
      val results = Vector(raw, mismatch, malformedTail, partialCount).map(x => SbtTestReassessment.reassess(_target, _classes, x))
      Then("complete evidence remains available with incomplete status and all results remain unverified")
      results.zip(Vector(raw, mismatch, malformedTail, partialCount)).foreach { case (result, original) =>
        result.outcome shouldBe SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.IncompleteCapture)
        result.processResult shouldBe original
        result.testObservation.get.captureComplete shouldBe false
      }
      results.take(2).foreach { result =>
        val observed = result.testObservation.get
        observed.suiteNames shouldBe Vector("SelectedSpec")
        observed.nameConfirmation shouldBe SbtSuiteNameConfirmation.Partial
        observed.testsRun shouldBe Some(7L)
        observed.completedSuites shouldBe Some(2L)
        observed.failed shouldBe Some(1L)
        observed.failureDetails shouldBe Vector("*** TEST FAILED ***")
      }
      results(2).testObservation.get.suiteNames shouldBe Vector("SelectedSpec")
      results(2).testObservation.get.failureDetails shouldBe Vector("*** TEST FAILED ***")
      results(3).testObservation.get.testsRun shouldBe None
    }

    "require complete byte-accurate strict UTF8 on both captured streams" in {
      Given("a valid selected report with truncation, negative or inconsistent byte counts and malformed UTF8")
      import SbtTestSelectionFailure.*
      val process = _process(_report(_classes, 7L, false))
      val malformed = ProcessExecutionCapture(Vector(0xc3.toByte, 0x28.toByte), 2L, false)
      val cases = Vector(
        process.copy(stdout = process.stdout.copy(truncated = true)) -> IncompleteCapture,
        process.copy(stderr = process.stderr.copy(truncated = true)) -> IncompleteCapture,
        process.copy(stdout = process.stdout.copy(byteCount = -1L)) -> IncompleteCapture,
        process.copy(stdout = process.stdout.copy(byteCount = process.stdout.byteCount + 1L)) -> IncompleteCapture,
        process.copy(stderr = process.stderr.copy(byteCount = 1L)) -> IncompleteCapture,
        process.copy(stdout = malformed) -> InvalidReport, process.copy(stderr = malformed) -> InvalidReport)
      When("the decoder inspects each stream before report interpretation")
      val decoded = cases.map { case (raw, reason) =>
        SbtExecutionResult(_target, SbtOperation.TestOnly, raw, Some(SbtTestSelectionDecoder.decode(_classes, raw))) -> (raw, reason)
      }
      Then("all captures remain unchanged and every zero-exit build has a typed unverified outcome")
      decoded.foreach { case (result, (raw, reason)) =>
        result.outcome shouldBe SbtExecutionOutcome.UnverifiedTests(reason)
        result.processResult shouldBe raw
      }
    }

    "preserve three-argument callers while requiring a verified TestOnly selection for zero exit" in {
      Given("one complete raw zero-exit process result and the existing three-argument result constructor")
      val raw = _process(_report(_classes, 7L, false))
      When("Compile, TestCompile, Test, TestOnly, FixCheck, PublishLocal and ScopedPublish interpret the same unmodified process result")
      val results = SbtOperation.values.toVector.map(operation => operation -> SbtExecutionResult(_target, operation, raw))
      Then("Class-free operations retain success while TestOnly without a supplied selection is unverified")
      results.map { case (operation, result) => operation -> result.outcome } shouldBe Vector(
        SbtOperation.Compile -> SbtExecutionOutcome.Succeeded,
        SbtOperation.TestCompile -> SbtExecutionOutcome.Succeeded,
        SbtOperation.Test -> SbtExecutionOutcome.Succeeded,
        SbtOperation.TestOnly -> SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.MissingSelection),
        SbtOperation.FixCheck -> SbtExecutionOutcome.Succeeded,
        SbtOperation.PublishLocal -> SbtExecutionOutcome.Succeeded,
        SbtOperation.ScopedPublish -> SbtExecutionOutcome.Succeeded)
      results.foreach { case (_, result) => result.processResult shouldBe raw }
      results.foreach { case (_, result) => result.testSelection shouldBe None }
    }
  }

  "Retained FULL ScalaTest reports" should {
    "aggregate the observed passing blocks of a single or multi-project execution" in {
      Given("an ordinary named report and a fixture assembled from three per-project summaries of one run")
      val single = _process(_report(_classes, 7L, false))
      val full = _process(Vector(
        "[info] Run completed in 8 minutes, 49 seconds.",
        _full_summary(290L, 3060L),
        "[success] completed the first project",
        "[info] Run completed in 1 second, 549 milliseconds.",
        _full_summary(5L, 58L),
        "[info] compiling sources",
        "[info] Run completed in 180 milliseconds.",
        _full_summary(1L, 7L)
      ).mkString("\n"))
      When("the fixture captures are reassessed as FULL")
      val ordinary = SbtTestReassessment.assessFull(single)
      val aggregated = SbtTestReassessment.assessFull(full)
      Then("the verified totals include every complete report block")
      ordinary shouldBe Right(SbtFullTestReport(2L, 7L, 1))
      aggregated shouldBe Right(SbtFullTestReport(296L, 3125L, 3))
    }

    "sum generated partitions with complete distinct literal suite names" in {
      Given("partitions with positive test counts, including ties, and one unique suite per report")
      val partitions = Gen.choose(1, 8).flatMap(size => Gen.listOfN(size, Gen.choose(1L, 5L)))
      When("FULL reassessment reads the generated report blocks")
      val property = Prop.forAll(partitions) { counts =>
        val output = counts.zipWithIndex.map { case (count, index) =>
          _report(Vector(s"org.example.Suite$index"), count, false)
        }.mkString("\n[info] next project\n")
        SbtTestReassessment.assessFull(_process(output)) ==
          Right(SbtFullTestReport(counts.size.toLong, counts.sum, counts.size))
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      Then("the suite, test and report totals follow the complete partition")
      checked.passed shouldBe true
    }

    "reject orphan, malformed, misplaced and unfinished report records" in {
      Given("complete passing output alongside inconsistent or incomplete report framing")
      import SbtTestSelectionFailure.*
      val report = _full_summary(2L, 7L)
      val records = report.linesIterator.toVector
      val isolated = Vector(
        "", records.last, records(1), records(2),
        records.take(3).mkString("\n"),
        Vector(records(1), records.head, records(2), records.last).mkString("\n"),
        report.replace("Suites:", "Suites"),
        report.replace("Tests:", "Tests"),
        "[info] Suites:\n" + report,
        "[info] Tests:\n" + report,
        report.replace("All tests passed.", "All tests passed"),
        report.replace("completed 2", "completed -1"),
        report.replace("succeeded 7", "succeeded 6"),
        report.replace(records(1), records(1) + "\n" + records(1)),
        report.replace(records(1), "[info] StraySuite:\n" + records(1)),
        "[info] Run completed in .\n" + report,
        "[info] Run completed in 1 second.\n[info] Run completed in 1 second.\n" + report
      )
      val tails = Vector(records.head, records(1), records(2), records.last,
        "[info] Total number of tests run:", "[info] Run completed in 1 second.",
        "[info] UnfinishedSuite:")
      val cases = isolated ++ tails.map(report + "\n" + _)
      When("the captured text is interpreted as FULL evidence")
      val decoded = cases.map(x => SbtTestReassessment.assessFull(_process(x)))
      Then("every malformed, orphan or unfinished record prevents verification")
      decoded.foreach(_ shouldBe Left(InvalidReport))
    }

    "require distinct complete suite names to distinguish identical numeric reports" in {
      Given("equal-count passing reports whose literal suite evidence is absent, partial, repeated or disjoint")
      import SbtTestSelectionFailure.*
      val headerless = _full_summary(2L, 7L)
      val first = _report(_classes, 7L, false)
      val second = _report(Vector("org.other.ThirdSpec", "org.other.FourthSpec"), 7L, false)
      val partial = "[info] ThirdSpec:\n" + headerless
      val overlap = _report(Vector(_classes.head, "org.other.ThirdSpec"), 7L, false)
      val repeated = _report(_classes.reverse, 9L, false)
      val duplicateheader = _report(Vector(_classes.head, _classes.head), 7L, false)
      val single = _full_summary(1L, 7L)
      val cases = Vector(
        headerless + "\n[info] Run completed in 2 seconds.\n" + headerless,
        first + "\n" + partial, first + "\n" + overlap,
        first + "\n" + repeated, duplicateheader,
        "[info] Tests:\n" + single + "\n[info] Suites:\n" + single
      )
      When("FULL reassessment reads all blocks")
      val admitted = SbtTestReassessment.assessFull(_process(first + "\n" + second))
      val refused = cases.map(x => SbtTestReassessment.assessFull(_process(x)))
      Then("only disjoint complete names distinguish the equal numeric counts")
      admitted shouldBe Right(SbtFullTestReport(4L, 14L, 2))
      refused.foreach(_ shouldBe Left(InvalidReport))
    }

    "reject nonpositive counts, overflow and observed test failures" in {
      Given("captures containing zero-test, overflowing or failed report evidence")
      import SbtTestSelectionFailure.*
      val report = _full_summary(2L, 7L)
      val overflow = "9223372036854775808"
      val fields = Vector("run: 7", "completed 2", "aborted 0", "succeeded 7",
        "failed 0", "canceled 0", "ignored 0", "pending 0")
      val outofrange = fields.map(x => report.replace(x, x.dropRight(1) + overflow) -> InvalidReport)
      val counts = Vector(
        "[info] No tests were executed." -> ZeroTests,
        _full_summary(1L, 0L) -> ZeroTests,
        _full_summary(0L, 7L) -> ZeroTests,
        (report + "\n" + _full_summary(1L, Long.MaxValue)) -> InvalidReport,
        (report + "\n" + _full_summary(Long.MaxValue, 5L)) -> InvalidReport,
        report.replace("aborted 0", "aborted 1") -> FailedTests,
        report.replace("failed 0", "failed 1") -> FailedTests,
        report.replace("canceled 0", "canceled 1") -> FailedTests
      )
      When("the reports are reassessed against observed counts")
      val decoded = (counts ++ outofrange).map { case (output, reason) =>
        SbtTestReassessment.assessFull(_process(output)) -> reason
      }
      Then("verification refuses every invalid numeric field and aggregate")
      decoded.foreach { case (result, reason) => result shouldBe Left(reason) }
    }

    "require a zero exit and complete strict UTF8 with no failure markers in either stream" in {
      Given("an otherwise passing summary with varying terminations and capture defects")
      import SbtTestSelectionFailure.*
      val report = _full_summary(2L, 7L)
      val raw = _process(report)
      val malformed = ProcessExecutionCapture(Vector(0xc3.toByte, 0x28.toByte), 2L, false)
      val markers = Vector("FAILED", "ABORTED", "CANCELED").flatMap { word =>
        val diagnostic = s"[warn] *** $word ***"
        Vector(_process(report + "\n" + diagnostic), raw.copy(stderr = _process(diagnostic).stdout))
      }
      val captures = Vector(
        raw.copy(stdout = raw.stdout.copy(truncated = true)) -> IncompleteCapture,
        raw.copy(stderr = raw.stderr.copy(truncated = true)) -> IncompleteCapture,
        raw.copy(stdout = raw.stdout.copy(byteCount = raw.stdout.byteCount + 1L)) -> IncompleteCapture,
        raw.copy(stderr = raw.stderr.copy(byteCount = -1L)) -> IncompleteCapture,
        raw.copy(stdout = malformed) -> InvalidReport,
        raw.copy(stderr = malformed) -> InvalidReport
      )
      import ProcessExecutionTermination.*
      val terminations = Vector(NotStarted, LaunchFailed, TimedOut, Cancelled, TerminationUnconfirmed,
        OutputLimitExceeded(ProcessExecutionStream.Stdout), ArtifactLimitExceeded)
        .map(x => raw.copy(termination = x) -> IncompleteCapture)
      val cases = captures ++ markers.map(_ -> FailedTests) ++ terminations ++ Vector(
        raw.copy(termination = Exited(1)) -> FailedTests
      )
      When("FULL reassessment reads the recorded termination and captures")
      val decoded = cases.map { case (process, reason) => SbtTestReassessment.assessFull(process) -> reason }
      Then("passing stdout cannot override a recorded failure or incomplete evidence")
      decoded.foreach { case (result, reason) => result shouldBe Left(reason) }
    }

    "keep exact TestOnly reassessment separate from FULL" in {
      Given("two independently valid reports for the same exact selection shape")
      val raw = _process(_full_summary(2L, 7L) + "\n" + _full_summary(2L, 9L))
      When("the retained process is assessed through each public API")
      val assessed = SbtTestReassessment.assess(_classes, raw)
      val reassessed = SbtTestReassessment.reassess(_target, _classes, raw)
      val full = SbtTestReassessment.assessFull(raw)
      Then("FULL accounts for both reports and exact TestOnly still requires one report")
      assessed.selection shouldBe SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.InvalidReport)
      assessed.observation.testsRun shouldBe None
      reassessed.outcome shouldBe SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.InvalidReport)
      reassessed.processResult shouldBe raw
      full shouldBe Right(SbtFullTestReport(4L, 16L, 2))
    }
  }

  private def _full_summary(completed: Long, total: Long): String =
    Vector(
      s"[info] Total number of tests run: $total",
      s"[info] Suites: completed $completed, aborted 0",
      s"[info] Tests: succeeded $total, failed 0, canceled 0, ignored 0, pending 0",
      "[info] All tests passed."
    ).mkString("\n")

  private def _report(classes: Vector[String], count: Long, simple: Boolean): String =
    (classes.map(x => s"[info] ${if (simple) x.split('.').last else x}:") ++ Vector(
      "[info] Run completed in 1 second.", s"[info] Total number of tests run: $count",
      s"[info] Suites: completed ${classes.size}, aborted 0",
      s"[info] Tests: succeeded $count, failed 0, canceled 0, ignored 0, pending 0", "[info] All tests passed.")).mkString("\n")

  private def _process(text: String): ProcessExecutionResult = {
    val bytes = text.getBytes(StandardCharsets.UTF_8).toVector
    ProcessExecutionResult(ProcessExecutionTermination.Exited(0), ProcessExecutionCapture(bytes, bytes.size.toLong, false),
      ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 37L, "selected-test-only")
  }
}
