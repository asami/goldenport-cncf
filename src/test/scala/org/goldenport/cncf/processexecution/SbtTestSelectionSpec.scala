package org.goldenport.cncf.processexecution

import java.nio.charset.StandardCharsets
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
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
      Then("only bounded exact selections are admitted and Compile/Test retain empty selections")
      accepted.foreach(_.isSuccess shouldBe true)
      rejected.foreach(_.isFaillure shouldBe true)
      Vector(SbtOperation.Compile, SbtOperation.Test).foreach { operation =>
        SbtTestSelectionPolicy.validateC(operation, Vector.empty).isSuccess shouldBe true
        SbtTestSelectionPolicy.validateC(operation, _classes).isFaillure shouldBe true
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

  "Selected ScalaTest report interpretation" should {
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
        report.replace("[info] org.example.SelectedSpec:\n", "") -> SuiteMismatch,
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
        (report + "\n[info] *** TESTS FAILED ***") -> InvalidReport,
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
      When("Compile, Test and TestOnly outcomes interpret the same unmodified process result")
      val results = SbtOperation.values.toVector.map(SbtExecutionResult(_target, _, raw))
      Then("Compile and Test retain success while TestOnly without a supplied selection is unverified")
      results.map(_.outcome) shouldBe Vector(SbtExecutionOutcome.Succeeded, SbtExecutionOutcome.Succeeded,
        SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.MissingSelection))
      results.foreach(_.processResult shouldBe raw)
      results.foreach(_.testSelection shouldBe None)
    }
  }

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
