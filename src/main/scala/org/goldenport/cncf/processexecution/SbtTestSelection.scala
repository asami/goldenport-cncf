package org.goldenport.cncf.processexecution

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence

/*
 * @since   Oct. 15, 2026
 *  version Oct. 24, 2026
 * @version Nov. 22, 2026
 * @author  ASAMI, Tomoharu
 */
enum SbtTestSelectionFailure {
  case MissingSelection, IncompleteCapture, InvalidReport, ZeroTests, SuiteMismatch, FailedTests, ScenarioMismatch, InventoryMismatch, ModeObservationMismatch, DiscoveryUnavailable, BatchSelectionMismatch
}

enum SbtTestSelectionResult {
  case Verified(testClasses: Vector[String], completedSuites: Int, testsRun: Long)
  case Rejected(reason: SbtTestSelectionFailure)
}

enum SbtSuiteNameConfirmation {
  case Complete, Partial, Unknown, Contradictory
}

/** Names are literal reporter observations, never filled from the requested selection. */
final case class SbtTestObservation(
  suiteNames: Vector[String],
  nameConfirmation: SbtSuiteNameConfirmation,
  testsRun: Option[Long], completedSuites: Option[Long], abortedSuites: Option[Long],
  succeeded: Option[Long], failed: Option[Long], canceled: Option[Long],
  ignored: Option[Long], pending: Option[Long], failureDetails: Vector[String],
  captureComplete: Boolean
)

final case class SbtTestAssessment(selection: SbtTestSelectionResult, observation: SbtTestObservation)

/** Aggregate counts from the verified passing ScalaTest blocks of a retained FULL run. */
final case class SbtFullTestReport(completedSuites: Long, testsRun: Long, reportCount: Int)

/** Pure interpretation of retained test process evidence. */
object SbtTestReassessment {
  def assess(testClasses: Vector[String], result: ProcessExecutionResult,
    scenario: Option[SbtScenarioSelection] = None): SbtTestAssessment =
    SbtTestSelectionDecoder.assess(testClasses, result, scenario)

  def assessFull(result: ProcessExecutionResult): Either[SbtTestSelectionFailure, SbtFullTestReport] =
    SbtTestSelectionDecoder.assessFull(result)

  def reassess(target: SbtTarget, testClasses: Vector[String], result: ProcessExecutionResult): SbtExecutionResult = {
    val assessment = assess(testClasses, result, target.scenario)
    val selection = assessment.selection match {
      case verified @ SbtTestSelectionResult.Verified(_, _, count) =>
        if (target.validationEstimate.forall(_.leaves.size.toLong == count)) verified
        else SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.InventoryMismatch)
      case rejected => rejected
    }
    SbtExecutionResult(target, SbtOperation.TestOnly, result, Some(selection), Some(assessment.observation))
  }
}

/** The host owns the allowed classes; typed scenarios may only narrow that set. */
private[cncf] object SbtTestSelectionPolicy {
  def validClasses(testClasses: Vector[String]): Boolean =
    testClasses != null && testClasses.nonEmpty && testClasses.size <= 16 &&
      testClasses.forall(x => x != null && x.length <= 256 &&
        x.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) &&
      testClasses.distinct.size == testClasses.size &&
      testClasses.map(_.split('.').last).distinct.size == testClasses.size

  def validateC(operation: SbtOperation, testClasses: Vector[String]): Consequence[Unit] =
    if ((operation == SbtOperation.TestOnly && validClasses(testClasses)) ||
      ((operation == SbtOperation.Compile || operation == SbtOperation.TestCompile || operation == SbtOperation.Test || operation == SbtOperation.FixCheck ||
          operation == SbtOperation.PublishLocal || operation == SbtOperation.ScopedPublish) &&
        testClasses != null && testClasses.isEmpty))
      Consequence.unit
    else Consequence.argumentPolicyViolation(
      "testClasses", "sbt.execution.test-selection", "bounded distinct exact runtime TestOnly class names", "invalid"
    )
}

/** Conservative interpretation of the selected ScalaTest info report, not an integrity certificate. */
private[cncf] object SbtTestSelectionDecoder {
  private val _total = "Total number of tests run: ([0-9]+)".r
  private val _suites = "Suites: completed ([0-9]+), aborted ([0-9]+)".r
  private val _tests = "Tests: succeeded ([0-9]+), failed ([0-9]+), canceled ([0-9]+), ignored ([0-9]+), pending ([0-9]+)".r
  private val _header = "([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*):".r
  private val _duration = "[0-9]+ (?:days?|hours?|minutes?|seconds?|milliseconds?)"
  private val _run_completed = s"Run completed in ${_duration}(?:, ${_duration})*\\.".r
  private val _full_summary_prefixes = Vector("Run completed", "Total number of tests run",
    "Suites:", "Suites completed", "Tests:", "Tests succeeded", "All tests passed", "No tests were executed")

  private final case class FullTestBlock(completed: Long, total: Long, ignored: Long, pending: Long, headers: Vector[String])

  def assessFull(result: ProcessExecutionResult): Either[SbtTestSelectionFailure, SbtFullTestReport] = {
    import SbtTestSelectionFailure.*
    result.termination match {
      case ProcessExecutionTermination.Exited(0) =>
        for {
          stdout <- _capture(result.stdout)
          stderr <- _capture(result.stderr)
          _ <- Either.cond(!Iterator(stdout, stderr).flatMap(_.linesIterator).exists(line =>
            line.contains("***") && (line.contains("FAILED") || line.contains("ABORTED") ||
              line.contains("CANCELED"))), (), FailedTests)
          report <- _full_report(stdout)
        } yield report
      case ProcessExecutionTermination.Exited(_) => Left(FailedTests)
      case _ => Left(IncompleteCapture)
    }
  }

  private def _full_summary_marker(line: String): Boolean =
    _full_summary_prefixes.exists(line.startsWith)

  private def _full_report(stdout: String): Either[SbtTestSelectionFailure, SbtFullTestReport] = {
    val lines = _lines(stdout).iterator
      .filter(line => _full_summary_marker(line) || _header.matches(line))
      .buffered
    val reports = Vector.newBuilder[FullTestBlock]
    while (lines.hasNext) {
      _full_block(lines) match {
        case Right(block) => reports += block
        case Left(reason) => return Left(reason)
      }
    }
    _sum_full_report(reports.result())
  }

  private def _full_block(lines: BufferedIterator[String]): Either[SbtTestSelectionFailure, FullTestBlock] = {
    import SbtTestSelectionFailure.*
    val headers = Vector.newBuilder[String]
    while (lines.hasNext && !_full_summary_marker(lines.head) && _header.matches(lines.head))
      headers += lines.next().stripSuffix(":")
    val names = headers.result()
    if (lines.hasNext && lines.head.startsWith("Run completed") && !_run_completed.matches(lines.next()))
      return Left(InvalidReport)
    if (lines.hasNext && lines.head == "No tests were executed.")
      return Left(ZeroTests)

    (lines.nextOption(), lines.nextOption(), lines.nextOption()) match {
      case (Some(_total(all)), Some(_suites(suites, aborted)),
          Some(_tests(succeeded, failed, canceled, ignored, pending))) =>
        for {
          total <- _number(all)
          completed <- _number(suites)
          abortedcount <- _number(aborted)
          successes <- _number(succeeded)
          failures <- _number(failed)
          cancelations <- _number(canceled)
          ignoredcount <- _number(ignored)
          pendingcount <- _number(pending)
          _ <- Either.cond(abortedcount == 0L && failures == 0L && cancelations == 0L, (), FailedTests)
          _ <- Either.cond(completed > 0L && total > 0L, (), ZeroTests)
          _ <- Either.cond(successes == total && names.size.toLong <= completed && names.distinct.size == names.size,
            (), InvalidReport)
          _ <- Either.cond(lines.nextOption().contains("All tests passed."), (), InvalidReport)
        } yield FullTestBlock(completed, total, ignoredcount, pendingcount, names)
      case _ => Left(InvalidReport)
    }
  }

  private def _sum_full_report(blocks: Vector[FullTestBlock]): Either[SbtTestSelectionFailure, SbtFullTestReport] = {
    import SbtTestSelectionFailure.*
    val completesets = blocks.collect {
      case block if block.headers.size.toLong == block.completed => block.headers.toSet
    }
    if (blocks.isEmpty || completesets.distinct.size != completesets.size)
      return Left(InvalidReport)

    val countgroups = blocks.groupBy(x => (x.completed, x.total, x.ignored, x.pending))
    val distinct = countgroups.valuesIterator.forall { group =>
      if (group.size == 1) true
      else {
        val names = group.flatMap(_.headers)
        group.forall(x => x.headers.size.toLong == x.completed) && names.distinct.size == names.size
      }
    }
    if (!distinct)
      return Left(InvalidReport)

    var completed = 0L
    var total = 0L
    val reports = blocks.iterator
    while (reports.hasNext) {
      val block = reports.next()
      if (Long.MaxValue - completed < block.completed || Long.MaxValue - total < block.total)
        return Left(InvalidReport)
      completed += block.completed
      total += block.total
    }
    Right(SbtFullTestReport(completed, total, blocks.size))
  }

  def assess(testClasses: Vector[String], result: ProcessExecutionResult,
    scenario: Option[SbtScenarioSelection] = None): SbtTestAssessment = {
    val selection = decode(testClasses, result, scenario)
    val lines = _observation_lines(result.stdout).flatMap(_lines)
    val headers = lines.collect { case _header(name) => name }
    val names = Option(testClasses).getOrElse(Vector.empty).flatMap(x =>
      if (x == null) Vector.empty else Vector(x -> x, x.split('.').last -> x)).toMap
    val mapped = headers.flatMap(names.get)
    val confirmation = if (headers.isEmpty) SbtSuiteNameConfirmation.Unknown
      else if (mapped.size != headers.size || mapped.distinct.size != mapped.size) SbtSuiteNameConfirmation.Contradictory
      else if (mapped.size == Option(testClasses).fold(0)(_.size)) SbtSuiteNameConfirmation.Complete
      else SbtSuiteNameConfirmation.Partial
    def _one_[A](xs: Vector[A]): Option[A] = if (xs.size == 1) xs.headOption else None
    val total = _one_(lines.collect { case _total(n) => n.toLongOption }.flatten)
    val suites = _one_(lines.collect { case _suites(c, a) => for { x <- c.toLongOption; y <- a.toLongOption } yield (x, y) }.flatten)
    val tests = _one_(lines.collect { case _tests(s, f, c, i, p) => for {
      a <- s.toLongOption; b <- f.toLongOption; d <- c.toLongOption; e <- i.toLongOption; g <- p.toLongOption
    } yield (a, b, d, e, g) }.flatten)
    val diagnostics = Vector(result.stdout, result.stderr).flatMap(_observation_lines)
    val details = diagnostics.map(_.replaceFirst("^\\s*\\[(?:info|warn|error)\\]\\s*", "")).filter(x => x.contains("***") &&
      (x.contains("FAILED") || x.contains("ABORTED") || x.contains("CANCELED")))
    SbtTestAssessment(selection, SbtTestObservation(headers, confirmation, total,
      suites.map(_._1), suites.map(_._2), tests.map(_._1), tests.map(_._2), tests.map(_._3),
      tests.map(_._4), tests.map(_._5), details,
      _capture(result.stdout).isRight && _capture(result.stderr).isRight))
  }

  // In an incomplete stream, only newline-terminated, strictly decoded lines
  // preceding any malformed bytes are safe to report as observations.
  private def _observation_lines(capture: ProcessExecutionCapture): Vector[String] =
    _capture(capture) match {
      case Right(text) => text.linesIterator.toVector
      case Left(_) =>
        val bytes = capture.content.toArray
        val lines = Vector.newBuilder[String]
        var start = 0
        var index = 0
        var valid = true
        while (index < bytes.length && valid) {
          if (bytes(index) == 10.toByte) {
            try {
              val line = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, index - start)).toString.stripSuffix("\r")
              lines += line
              start = index + 1
            } catch {
              case _: CharacterCodingException => valid = false
            }
          }
          index += 1
        }
        lines.result()
    }

  private def _lines(stdout: String): Vector[String] =
    stdout.linesIterator.flatMap { line =>
      val stripped = line.replaceFirst("^\\s*\\[info\\]\\s*", "")
      if (stripped == line) None else Some(stripped)
    }.toVector

  def decode(
    testClasses: Vector[String],
    result: ProcessExecutionResult,
    scenario: Option[SbtScenarioSelection] = None
  ): SbtTestSelectionResult = {
    import SbtTestSelectionFailure.*
    val decoded = for {
      _ <- Either.cond(scenario != null && scenario.forall(x => x != null && x.validateC.isSuccess &&
        testClasses == Vector(x.suiteClassName)), (), ScenarioMismatch)
      _ <- Either.cond(SbtTestSelectionPolicy.validClasses(testClasses), (), MissingSelection)
      stdout <- _capture(result.stdout)
      stderr <- _capture(result.stderr)
      verified <- _report(testClasses, stdout, stderr, scenario)
    } yield verified
    decoded.fold(SbtTestSelectionResult.Rejected.apply, identity)
  }

  private def _capture(capture: ProcessExecutionCapture): Either[SbtTestSelectionFailure, String] = {
    import SbtTestSelectionFailure.*
    if (capture.truncated || capture.byteCount < 0 || capture.byteCount != capture.content.size.toLong)
      Left(IncompleteCapture)
    else try {
      Right(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(capture.content.toArray)).toString)
    } catch {
      case _: CharacterCodingException => Left(InvalidReport)
    }
  }

  private def _report(
    classes: Vector[String],
    stdout: String,
    stderr: String,
    scenario: Option[SbtScenarioSelection]
  ): Either[SbtTestSelectionFailure, SbtTestSelectionResult] = {
    import SbtTestSelectionFailure.*
    val lines = _lines(stdout)
    val totals = lines.filter(_.startsWith("Total number of tests run"))
    val suites = lines.filter(_.startsWith("Suites:"))
    val tests = lines.filter(_.startsWith("Tests:"))
    val passed = lines.count(_ == "All tests passed.")
    val headers = lines.collect { case _header(name) => name }
    val names = classes.flatMap(x => Vector(x -> x, x.split('.').last -> x)).toMap
    val canonical = headers.flatMap(names.get)
    for {
      _ <- Either.cond(totals.size == 1 && suites.size == 1 && tests.size == 1, (), InvalidReport)
      total <- totals.head match {
        case _total(value) => _number(value)
        case _ => Left(InvalidReport)
      }
      suitecounts <- suites.head match {
        case _suites(completed, aborted) => for { c <- _number(completed); a <- _number(aborted) } yield (c, a)
        case _ => Left(InvalidReport)
      }
      testcounts <- tests.head match {
        case _tests(succeeded, failed, canceled, ignored, pending) => for {
          s <- _number(succeeded); f <- _number(failed); c <- _number(canceled)
          _ <- _number(ignored); _ <- _number(pending)
        } yield (s, f, c)
        case _ => Left(InvalidReport)
      }
      _ <- Either.cond(suitecounts._2 == 0 && testcounts._2 == 0 && testcounts._3 == 0 &&
        !(stdout + "\n" + stderr).linesIterator.exists(x => x.contains("***") &&
          (x.contains("FAILED") || x.contains("ABORTED") || x.contains("CANCELED"))), (), FailedTests)
      _ <- Either.cond(scenario.isEmpty || total == 1L, (), ScenarioMismatch)
      _ <- Either.cond(total > 0, (), ZeroTests)
      _ <- Either.cond(testcounts._1 == total, (), InvalidReport)
      _ <- Either.cond(passed == 1 &&
        lines.indexOf(totals.head) < lines.indexOf(suites.head) && lines.indexOf(suites.head) < lines.indexOf(tests.head) &&
        lines.indexOf(tests.head) < lines.indexOf("All tests passed.") &&
        !lines.drop(lines.indexOf(totals.head)).exists(x => _header.pattern.matcher(x).matches()) &&
        !lines.exists(x => x.startsWith("All tests passed") && x != "All tests passed.") &&
        !lines.exists(x => x == "No tests were executed." ||
          (x.contains("***") && (x.contains("FAILED") || x.contains("ABORTED") || x.contains("CANCELED")))), (), InvalidReport)
      _ <- Either.cond(suitecounts._1 == classes.size.toLong && headers.size <= classes.size &&
        canonical.size == headers.size && canonical.distinct.size == canonical.size,
        (), SuiteMismatch)
    } yield SbtTestSelectionResult.Verified(classes, classes.size, total)
  }

  private def _number(value: String): Either[SbtTestSelectionFailure, Long] =
    value.toLongOption.toRight(SbtTestSelectionFailure.InvalidReport)
}
