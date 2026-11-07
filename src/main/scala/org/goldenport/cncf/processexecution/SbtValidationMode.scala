package org.goldenport.cncf.processexecution

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.validation.ValidationExecutionModeV1 as M
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workspace.{WorkspaceId, WorkspacePermission}

/** Immutable managed-child mode specialization, using the existing ScalaTest ConfigMap path.
 * @since   Nov.  6, 2026
 * @version Nov.  6, 2026
 */
object SbtValidationMode {
  final case class Binding(modes: Set[String], provider: String, livePrerequisites: Vector[String] = Vector.empty) {
    def validateC: Consequence[Binding] = for {
      normalized <- M.normalizeC(modes)
      _ <- if (normalized == modes && Set("fixture", "live")(provider) && livePrerequisites != null &&
          livePrerequisites.distinct == livePrerequisites && livePrerequisites.forall(value => value != null && value.matches("[A-Z][A-Z0-9_]{0,127}")) &&
          (provider != "live" || livePrerequisites.nonEmpty)) Consequence.unit
        else Consequence.configurationInvalid("Invalid managed validation mode/provider binding")
    } yield this
  }
  enum Result {
    case Observed(value: M.Observation)
    case Unverified(reason: String)
  }

  final case class Case(selector: SbtScenarioSelection, mode: String, requiredProvider: Option[String] = None) {
    def validateC: Consequence[Case] = for {
      _ <- if (selector == null) Consequence.configurationInvalid("Null exact batch selector") else selector.validateC.map(_ => ())
      normalized <- M.normalizeC(Set(mode))
      _ <- if (normalized == Set(mode) && requiredProvider != null && requiredProvider.forall(Set("fixture", "live"))) Consequence.unit
        else Consequence.configurationInvalid("Invalid exact validation batch variant")
    } yield this
  }
  final case class CaseResult(selected: Case, status: String, observation: Option[M.Observation])

  private[cncf] def task(selected: Case, provider: String): String = {
    val inputs = SbtScenarioParameters.renderArguments(selected.selector.parameters)
    val command = s"testOnly ${selected.selector.suiteClassName} -- -t \"${selected.selector.testName}\""
    (Vector(command) ++ inputs ++ Vector(s"\"-Dcncf.validation.mode=${selected.mode}\"",
      s"\"-Dcncf.validation.provider=$provider\"")).mkString(" ")
  }

  /** Pure bounded grouping after normal current-context admission; no process launches. */
  def groupCasesC(context: ExecutionContext, workspaceId: WorkspaceId, cases: Vector[Case]): Consequence[Vector[Vector[Case]]] =
    if (cases == null || cases.isEmpty || cases.exists(value => value == null || !value.validateC.isSuccess))
      Consequence.configurationInvalid("Ordinary validation requires complete nonempty exact variants")
    else (for {
      workspace <- context.workspaceExecutionAdmissionOption.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Ordinary TestOnly workspace admission is unavailable"))
      binding <- workspace.admitC(context.cncfCore.scope, workspaceId, WorkspacePermission.Manage)
      admission <- context.cncfCore.scope.processExecutionAdmissionOption.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Ordinary TestOnly process admission is unavailable"))
      admitted <- admission.admitSbtForScopeC(context.cncfCore.scope, binding, SbtOperation.TestOnly, None)
      execution <- SbtJvmParameters.applyC(context.resolvedParameters, admitted)
      policy <- execution.definition._sbt_metadata.flatMap(_.validationMode).map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Ordinary TestOnly mode/provider declaration is unavailable"))
      _ <- policy.validateC
    } yield (execution, policy)).flatMap { (execution, policy) =>
      // Grouping reads authority/finite budgets; actual batch admission checks every prerequisite before launch.
      val provider = policy.provider
      val overhead = execution.definition.fixedArguments.dropRight(1).map(_.getBytes(StandardCharsets.UTF_8).length.toLong).sum
      val ceiling = execution.effectiveLimits.argumentBytes.getOrElse(0L) - overhead
      val groups = cases.foldLeft(Vector(Vector.empty[Case])) { (groups, selected) =>
        val current = groups.last
        val bytes = (current :+ selected).map(value => task(value, provider).getBytes(StandardCharsets.UTF_8).length.toLong + 1L).sum
        if (current.nonEmpty && (current.size >= 64 || bytes > ceiling)) groups :+ Vector(selected)
        else groups.dropRight(1) :+ (current :+ selected)
      }
      if (groups.flatten != cases || groups.exists(_.exists(value => task(value, provider).getBytes(StandardCharsets.UTF_8).length.toLong + 1L > ceiling)))
        Consequence.serviceUnavailable("Exact validation variant exceeds the admitted finite SBT argument budget")
      else Consequence.success(groups)
    }

  private[cncf] def specializeBatchC(execution: ResolvedProcessExecution, target: SbtTarget,
    operation: SbtOperation): Consequence[ResolvedProcessExecution] = {
    val dynamic = execution.definition._sbt_metadata.exists(_.discoveredTests)
    if (target.validationBatch == null) Consequence.configurationInvalid("Invalid null validation batch")
    else if (target.validationBatch.isEmpty && !dynamic) Consequence.success(execution)
    else if (target.validationBatch.isEmpty) Consequence.serviceUnavailable("Dynamic TestOnly requires an exact discovered variant batch")
    else for {
      metadata <- execution.definition._sbt_metadata.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Exact batch requires independent ordinary TestOnly authority"))
      binding <- metadata.validationMode.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Exact batch requires the ordinary TestOnly mode/provider binding"))
      _ <- binding.validateC
      _ <- if (operation == SbtOperation.TestOnly && target.scenario.isEmpty && target.validationMode.isEmpty &&
          target.validationProvider.isEmpty && target.validationBatch.size <= 64 &&
          target.validationBatch.distinct == target.validationBatch) Consequence.unit
        else Consequence.configurationInvalid("Exact batch cannot broaden or combine incompatible selector forms")
      _ <- target.validationBatch.foldLeft(Consequence.unit) { (prior, selected) => prior.flatMap { _ => for {
        _ <- if (selected == null) Consequence.configurationInvalid("Null batch variant") else selected.validateC.map(_ => ())
        _ <- if (binding.modes(selected.mode) && selected.requiredProvider.forall(_ == binding.provider) &&
            (binding.provider != "live" || binding.livePrerequisites.forall(key => execution.definition._environment.values.get(key).exists(_.nonEmpty))))
          Consequence.unit else Consequence.serviceUnavailable("Required mode/live provider prerequisites are unavailable; no fixture fallback")
        _ <- if ((metadata.discoveredTests && metadata.discovery.exists(scope =>
              selected.selector.suiteClassName.startsWith(scope.packagePrefix + "."))) ||
            (!metadata.discoveredTests && metadata.testClasses.contains(selected.selector.suiteClassName))) Consequence.unit
          else Consequence.serviceUnavailable("Selected test is outside the independently admitted TestOnly scope")
      } yield () }}
      updated <- ProcessProgramDefinition.withSbtValidationBatchC(execution.definition,
        target.validationBatch.map(selected => task(selected, binding.provider)))
    } yield execution.copy(definition = updated)
  }

  private[cncf] def decodeBatch(cases: Vector[Case], result: ProcessExecutionResult, expectedprovider: Option[String]): Vector[CaseResult] = try {
    val captures = Vector(result.stdout, result.stderr)
    if (captures.exists(capture => capture.truncated || capture.byteCount != capture.content.size.toLong))
      cases.map(CaseResult(_, "unverified", None))
    else {
      def _text_(capture: ProcessExecutionCapture): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(capture.content.toArray)).toString
      val lines = _text_(result.stdout).linesIterator.toVector
      val segments = scala.collection.mutable.ArrayBuffer.empty[Vector[String]]
      var current = Vector.empty[String]
      lines.foreach { line =>
        current :+= line
        if (line.stripPrefix("[info] ") == "All tests passed.") { segments += current; current = Vector.empty }
      }
      def _observation_(lines: Vector[String], selected: Case): Option[M.Observation] = {
        val raw = lines.map(_.stripPrefix("[info] ")).filter(_.startsWith(M.observationPrefix))
        if (raw.size != 1) None
        else io.circe.parser.parse(raw.head.stripPrefix(M.observationPrefix)).toOption.flatMap { json =>
          val cursor = json.hcursor
          for {
            suite <- cursor.get[String]("suite").toOption
            test <- cursor.get[String]("test").toOption
            mode <- cursor.get[String]("mode").toOption
            provider <- cursor.get[String]("provider").toOption
            observed = M.Observation(suite, test, mode, provider)
            if suite == selected.selector.suiteClassName && test == selected.selector.testName && mode == selected.mode &&
              Set("fixture", "live")(provider) && expectedprovider.contains(provider) && selected.requiredProvider.forall(_ == provider) && observed.line == raw.head
          } yield observed
        }
      }
      // A child observation precedes the test body; a nonzero sbt exit alone
      // cannot establish that this exact test failed. Admit only a complete,
      // single-test ScalaTest failure report with the exact suite and test.
      def _failed_(lines: Vector[String], selected: Case): Boolean = {
        val info = lines.filter(_.startsWith("[info] ")).map(_.stripPrefix("[info] "))
        val suite = selected.selector.suiteClassName
        val headers = info.filter(_.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*:"))
        val failure = s"- ${selected.selector.testName} *** FAILED ***"
        val totals = info.filter(_.startsWith("Total number of tests run:"))
        val suites = info.filter(_.startsWith("Suites:"))
        val tests = info.filter(_.startsWith("Tests:"))
        val markers = info.filter(line => line.contains("***") &&
          (line.contains("FAILED") || line.contains("ABORTED") || line.contains("CANCELED")))
        val summary = "*** 1 TEST FAILED ***"
        headers == Vector(s"$suite:") && totals == Vector("Total number of tests run: 1") &&
          suites == Vector("Suites: completed 1, aborted 0") &&
          tests == Vector("Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0") &&
          markers == Vector(failure, summary) &&
          info.indexOf(s"$suite:") < info.indexOf(failure) && info.indexOf(failure) < info.indexOf(totals.head) &&
          info.indexOf(totals.head) < info.indexOf(suites.head) && info.indexOf(suites.head) < info.indexOf(tests.head) &&
          info.indexOf(tests.head) < info.indexOf(summary) &&
          !lines.filterNot(_.startsWith("[info] ")).exists(line => line.contains("***") &&
            (line.contains("FAILED") || line.contains("ABORTED") || line.contains("CANCELED"))) &&
          !_text_(result.stderr).linesIterator.exists(line => line.contains("***") &&
            (line.contains("FAILED") || line.contains("ABORTED") || line.contains("CANCELED")))
      }
      val extra = segments.size > cases.size || _text_(result.stderr).contains(M.observationPrefix) ||
        (segments.size == cases.size && current.exists { line =>
          val normalized = line.stripPrefix("[info] ")
          normalized.startsWith(M.observationPrefix) || normalized.startsWith("Total number of tests run:") ||
            normalized.startsWith("Suites:") || normalized.startsWith("Tests:") ||
            normalized.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*:")
        })
      cases.zipWithIndex.map { (selected, index) =>
        segments.lift(index) match {
          case Some(segment) =>
            val observation = _observation_(segment, selected)
            val bytes = segment.mkString("\n").getBytes(StandardCharsets.UTF_8).toVector
            val report = result.copy(termination = ProcessExecutionTermination.Exited(0),
              stdout = ProcessExecutionCapture(bytes, bytes.size.toLong, false), stderr = ProcessExecutionCapture(Vector.empty, 0L, false))
            val verified = SbtTestSelectionDecoder.decode(Vector(selected.selector.suiteClassName), report, Some(selected.selector)) match {
              case SbtTestSelectionResult.Verified(_, _, 1L) => true
              case _ => false
            }
            CaseResult(selected, if (!extra && verified && observation.nonEmpty) "passed" else "unverified", observation)
          case None if index == segments.size =>
            val observation = _observation_(current, selected)
            CaseResult(selected, if (observation.isEmpty) "unverified" else result.termination match {
              case ProcessExecutionTermination.Exited(code) if code != 0 && !extra && _failed_(current, selected) => "failed"
              case _ => "unverified"
            }, observation)
          case None => CaseResult(selected, "not-executed", None)
        }
      }
    }
  } catch { case NonFatal(_) => cases.map(CaseResult(_, "unverified", None)) }

  private[cncf] def specializeC(execution: ResolvedProcessExecution, target: SbtTarget): Consequence[ResolvedProcessExecution] =
    if (target.validationMode == null || target.validationMode.contains(null) || target.validationProvider == null ||
        target.validationProvider.exists(value => !Set("fixture", "live")(value)) ||
        (target.validationProvider.nonEmpty && target.validationMode.isEmpty))
      Consequence.configurationInvalid("Invalid managed validation mode/provider intent")
    else target.validationMode match {
      case None => Consequence.success(execution)
      case Some(mode) => for {
        normalized <- M.normalizeC(Set(mode))
        metadata <- execution.definition._sbt_metadata.map(Consequence.success).getOrElse(
          Consequence.serviceUnavailable("Managed validation requires the admitted typed SBT definition"))
        _ <- metadata.scenario.map(Consequence.success).getOrElse(
          Consequence.serviceUnavailable("Managed validation mode requires an exact selected test"))
        binding <- metadata.validationMode.map(Consequence.success).getOrElse(
          Consequence.serviceUnavailable("ValidationSetupRequired: project has no shared child mode/provider binding"))
        _ <- binding.validateC
        _ <- if (target.validationProvider.forall(_ == binding.provider)) Consequence.unit
          else Consequence.serviceUnavailable("Required live provider is unavailable in this project binding; no fixture fallback")
        _ <- if (normalized == Set(mode) && binding.modes(mode)) Consequence.unit
          else Consequence.serviceUnavailable("Selected validation mode is not supported by the project binding")
        _ <- if (binding.provider != "live" || binding.livePrerequisites.forall(key =>
            execution.definition._environment.values.get(key).exists(_.nonEmpty))) Consequence.unit
          else Consequence.serviceUnavailable("Required live validation prerequisites are unavailable; no fixture fallback")
        definition <- ProcessProgramDefinition.withSbtValidationModeC(execution.definition, mode, binding.provider)
      } yield execution.copy(definition = definition)
    }

  private[cncf] def decode(target: SbtTarget, execution: ResolvedProcessExecution,
    result: ProcessExecutionResult): Option[Result] = target.validationMode.map { mode =>
    val expected = for {
      scenario <- target.scenario
      binding <- execution.definition._sbt_metadata.flatMap(_.validationMode)
    } yield M.Observation(scenario.suiteClassName, scenario.testName, mode, binding.provider)
    try {
      val captures = Vector(result.stdout, result.stderr)
      if (captures.exists(c => c.truncated || c.byteCount != c.content.size.toLong)) Result.Unverified("incomplete child mode observation")
      else {
        val lines = captures.flatMap { capture =>
          StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(capture.content.toArray)).toString
            .linesIterator.map(_.stripPrefix("[info] ")).filter(_.startsWith(M.observationPrefix)).toVector
        }
        if (expected.exists(value => lines == Vector(value.line))) Result.Observed(expected.get)
        else Result.Unverified("missing, conflicting or foreign child mode/provider observation")
      }
    } catch { case NonFatal(_) => Result.Unverified("invalid UTF-8 child mode observation") }
  }
}
