package org.goldenport.cncf.processexecution

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence

/*
 * @since   Oct. 15, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
enum SbtTestSelectionFailure {
  case MissingSelection, IncompleteCapture, InvalidReport, ZeroTests, SuiteMismatch, FailedTests, ScenarioMismatch, InventoryMismatch
}

enum SbtTestSelectionResult {
  case Verified(testClasses: Vector[String], completedSuites: Int, testsRun: Long)
  case Rejected(reason: SbtTestSelectionFailure)
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
          operation == SbtOperation.PublishLocal) &&
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
      _ <- _capture(result.stderr)
      verified <- _report(testClasses, stdout, scenario)
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
    scenario: Option[SbtScenarioSelection]
  ): Either[SbtTestSelectionFailure, SbtTestSelectionResult] = {
    import SbtTestSelectionFailure.*
    val lines = stdout.linesIterator.collect { case line if line.startsWith("[info] ") => line.substring(7) }.toVector
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
      _ <- Either.cond(suitecounts._2 == 0 && testcounts._2 == 0 && testcounts._3 == 0, (), FailedTests)
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
      _ <- Either.cond(suitecounts._1 == classes.size.toLong && headers.size == classes.size &&
        canonical.size == headers.size && canonical.distinct.size == canonical.size && canonical.toSet == classes.toSet,
        (), SuiteMismatch)
    } yield SbtTestSelectionResult.Verified(classes, classes.size, total)
  }

  private def _number(value: String): Either[SbtTestSelectionFailure, Long] =
    value.toLongOption.toRight(SbtTestSelectionFailure.InvalidReport)
}
