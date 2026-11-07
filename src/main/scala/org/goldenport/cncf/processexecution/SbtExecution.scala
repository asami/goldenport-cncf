package org.goldenport.cncf.processexecution

import java.nio.file.Path
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId, WorkspacePermission}

/*
 * @since   Oct. 15, 2026
 *  version Oct. 24, 2026
 * @version Nov.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SbtTarget(workspaceId: WorkspaceId, scenario: Option[SbtScenarioSelection] = None,
  validationEstimate: Option[SbtValidationEstimate] = None,
  validationMode: Option[String] = None,
  validationProvider: Option[String] = None,
  validationDiscovery: Boolean = false, validationBatch: Vector[SbtValidationMode.Case] = Vector.empty) {
  // Retain the operational three-field JVM entries without adding overloaded defaults.
  def this(workspaceId: WorkspaceId, scenario: Option[SbtScenarioSelection],
    validationEstimate: Option[SbtValidationEstimate]) =
    this(workspaceId, scenario, validationEstimate, None, None, false, Vector.empty)

  def copy(workspaceId: WorkspaceId, scenario: Option[SbtScenarioSelection],
    validationEstimate: Option[SbtValidationEstimate]): SbtTarget =
    new SbtTarget(workspaceId, scenario, validationEstimate, validationMode,
      validationProvider, validationDiscovery, validationBatch)
}
object SbtTarget {
  def apply(workspaceId: WorkspaceId, scenario: Option[SbtScenarioSelection],
    validationEstimate: Option[SbtValidationEstimate]): SbtTarget =
    new SbtTarget(workspaceId, scenario, validationEstimate)
}

enum SbtOperation {
  case Compile, TestCompile, Test, TestOnly, FixCheck, PublishLocal
}

/** Admission failure carries no process receipt; admitted failures never imply safe replay. */
enum SbtExecutionAttempt {
  case Observed(result: SbtExecutionResult)
  case AdmissionRejected(target: SbtTarget, operation: SbtOperation, conclusion: Conclusion)
  case Uncertain(target: SbtTarget, operation: SbtOperation, conclusion: Conclusion)
}

enum SbtExecutionOutcome {
  case Succeeded
  case NotExecuted
  case Failed(exitCode: Int)
  case Incomplete(termination: ProcessExecutionTermination)
  case UnverifiedTests(reason: SbtTestSelectionFailure)
  case TestValidationFailed(reason: SbtTestSelectionFailure)
}

final case class SbtExecutionResult(
  target: SbtTarget,
  operation: SbtOperation,
  processResult: ProcessExecutionResult,
  testSelection: Option[SbtTestSelectionResult] = None,
  testObservation: Option[SbtTestObservation] = None,
  validationMode: Option[SbtValidationMode.Result] = None,
  discovery: Option[SbtValidationDiscovery.Result] = None,
  batch: Option[Vector[SbtValidationMode.CaseResult]] = None
) {
  // The legacy fifth field is reporter observation, never B2 mode evidence.
  def this(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult]) =
    this(target, operation, processResult, testSelection, None, None, None, None)

  def this(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult], testObservation: Option[SbtTestObservation]) =
    this(target, operation, processResult, testSelection, testObservation, None, None, None)

  def copy(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult]): SbtExecutionResult =
    new SbtExecutionResult(target, operation, processResult, testSelection, testObservation,
      validationMode, discovery, batch)

  def copy(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult], testObservation: Option[SbtTestObservation]): SbtExecutionResult =
    new SbtExecutionResult(target, operation, processResult, testSelection, testObservation,
      validationMode, discovery, batch)

  def outcome: SbtExecutionOutcome = processResult.termination match {
    case ProcessExecutionTermination.NotStarted => SbtExecutionOutcome.NotExecuted
    case ProcessExecutionTermination.Exited(0) if target.validationDiscovery &&
        !discovery.exists(_.isInstanceOf[SbtValidationDiscovery.Result.Available]) =>
      SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.DiscoveryUnavailable)
    case ProcessExecutionTermination.Exited(0) if target.validationBatch.nonEmpty =>
      if (batch.exists(values => values.map(_.selected) == target.validationBatch && values.forall(_.status == "passed")))
        SbtExecutionOutcome.Succeeded
      else SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.BatchSelectionMismatch)
    case ProcessExecutionTermination.Exited(0) if target.validationMode.nonEmpty &&
        !validationMode.exists {
          case SbtValidationMode.Result.Observed(value) => value.mode == target.validationMode.get &&
            target.validationProvider.forall(_ == value.provider)
          case _ => false
        } =>
      SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.ModeObservationMismatch)
    case ProcessExecutionTermination.Exited(0) if operation == SbtOperation.TestOnly => testSelection match {
      case Some(SbtTestSelectionResult.Verified(_, _, _)) => SbtExecutionOutcome.Succeeded
      case Some(SbtTestSelectionResult.Rejected(reason)) => reason match {
        case SbtTestSelectionFailure.FailedTests | SbtTestSelectionFailure.ZeroTests |
             SbtTestSelectionFailure.SuiteMismatch | SbtTestSelectionFailure.ScenarioMismatch |
             SbtTestSelectionFailure.InventoryMismatch => SbtExecutionOutcome.TestValidationFailed(reason)
        case _ => SbtExecutionOutcome.UnverifiedTests(reason)
      }
      case None => SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.MissingSelection)
    }
    case ProcessExecutionTermination.Exited(0) => SbtExecutionOutcome.Succeeded
    case ProcessExecutionTermination.Exited(code) => SbtExecutionOutcome.Failed(code)
    case termination => SbtExecutionOutcome.Incomplete(termination)
  }

  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.Build, target.workspaceId)
}

object SbtExecutionResult {
  def apply(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult]): SbtExecutionResult =
    new SbtExecutionResult(target, operation, processResult, testSelection)

  def apply(target: SbtTarget, operation: SbtOperation, processResult: ProcessExecutionResult,
    testSelection: Option[SbtTestSelectionResult], testObservation: Option[SbtTestObservation]): SbtExecutionResult =
    new SbtExecutionResult(target, operation, processResult, testSelection, testObservation)
}

/** Immutable runtime tag: neither a request selector nor a separate tool registry. */
private[cncf] final case class SbtProgramMetadata(
  binding: WorkspaceBinding,
  operation: SbtOperation,
  testClasses: Vector[String] = Vector.empty,
  scenario: Option[SbtScenarioSelection] = None,
  validationMode: Option[SbtValidationMode.Binding] = None,
  discovery: Option[SbtValidationDiscovery.Binding] = None, discoveredTests: Boolean = false
) {
  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.Build, binding.workspaceId)
}

private[cncf] object SbtProgramDefinition {
  def createC(
    binding: WorkspaceBinding,
    operation: SbtOperation,
    capability: ProcessCapabilityId,
    safeProgramIdentity: String,
    sbtExecutable: Path,
    limits: ProcessExecutionLimits,
    environment: Map[String, String],
    testClasses: Vector[String] = Vector.empty,
    validationMode: Option[SbtValidationMode.Binding] = None,
    discovery: Option[SbtValidationDiscovery.Binding] = None, discoveredTests: Boolean = false
  ): Consequence[ProcessProgramDefinition] =
    for {
      _ <- binding.workingDirectoryC
      _ <- if (operation != null) Consequence.unit else _invalid_operation_c
      _ <- if (discoveredTests && operation == SbtOperation.TestOnly && testClasses.isEmpty && discovery.nonEmpty) Consequence.unit
        else SbtTestSelectionPolicy.validateC(operation, testClasses)
      _ <- if (discovery == null || discovery.exists(_ == null)) Consequence.configurationInvalid("Invalid ordinary discovery binding")
        else discovery.fold(Consequence.unit)(_.validateC.map(_ => ()))
      _ <- if ((!discoveredTests || (operation == SbtOperation.TestOnly && discovery.nonEmpty && validationMode.nonEmpty)) &&
          (discovery.isEmpty || operation == SbtOperation.TestCompile || discoveredTests)) Consequence.unit
        else Consequence.configurationInvalid("Discovery requires opted-in TestCompile and independently declared dynamic TestOnly mode authority")
      _ <- if (validationMode == null || validationMode.exists(_ == null)) Consequence.configurationInvalid("Invalid optional mode binding")
        else validationMode.fold(Consequence.unit)(_.validateC.map(_ => ()))
      _ <- if (validationMode.isEmpty || operation == SbtOperation.TestOnly) Consequence.unit
        else Consequence.configurationInvalid("Mode binding requires exact TestOnly selection")
      executable <- SbtExecutableLocation.resolveC(sbtExecutable, environment)
      basearguments = Vector(
        "--batch",
        "-Dsbt.server.autostart=false",
        "-Dsbt.supershell=false",
        "-Dsbt.color=false",
        operation match {
          case SbtOperation.Compile => "compile"
          case SbtOperation.TestCompile => "Test / compile"
          case SbtOperation.Test => "test"
          case SbtOperation.TestOnly => "testOnly " + testClasses.mkString(" ")
          case SbtOperation.FixCheck => "fixCheck"
          case SbtOperation.PublishLocal => "publishLocal"
        }
      )
      arguments = if (operation == SbtOperation.TestCompile && discovery.nonEmpty)
        basearguments.dropRight(1) ++ SbtValidationDiscovery.tasks else basearguments
      definition <- ProcessProgramDefinition.fromSbtRuntimeC(
        capability, safeProgramIdentity, executable.toString, arguments, limits,
        environment, SbtProgramMetadata(binding, operation, testClasses, validationMode = validationMode, discovery = discovery, discoveredTests = discoveredTests)
      )
    } yield definition

  private def _invalid_operation_c: Consequence[Unit] =
    Consequence.argumentPolicyViolation("operation", "sbt.execution.operation", "Compile, TestCompile, Test, TestOnly, FixCheck or PublishLocal", "unknown")
}

private[cncf] object SbtExecutionAdmission {
  def decode(
    target: SbtTarget,
    operation: SbtOperation,
    execution: ResolvedProcessExecution,
    result: ProcessExecutionResult
  ): SbtExecutionResult = {
    val metadata = execution.definition._sbt_metadata
    val assessed = Option.when(target.validationBatch.isEmpty && operation == SbtOperation.TestOnly)(
      SbtTestReassessment.reassess(target, metadata.map(_.testClasses).getOrElse(Vector.empty), result))
    val discovery = Option.when(target.validationDiscovery)(metadata.flatMap(_.discovery)
      .map(binding => SbtValidationDiscovery.decode(target.workspaceId, binding, result))
      .getOrElse(SbtValidationDiscovery.Result.Unavailable("ordinary discovery binding is unavailable")))
    val batch = Option.when(target.validationBatch.nonEmpty)(SbtValidationMode.decodeBatch(target.validationBatch, result, metadata.flatMap(_.validationMode).map(_.provider)))
    SbtExecutionResult(target, operation, result, assessed.flatMap(_.testSelection), assessed.flatMap(_.testObservation),
      validationMode = SbtValidationMode.decode(target, execution, result), discovery = discovery, batch = batch)
  }

  def validateGenericExecutionC(execution: ResolvedProcessExecution): Consequence[Unit] =
    if (execution.definition._sbt_metadata.nonEmpty)
      Consequence.argumentPolicyViolation(
        "sbt", "sbt.execution.typed-intent", "typed UnitOfWork SBT intent", "generic ProcessExec"
      )
    else
      Consequence.unit

  def resolveC(
    context: ExecutionContext,
    target: SbtTarget,
    operation: SbtOperation
  ): Consequence[ResolvedProcessExecution] =
    for {
      _ <- if (target != null && operation != null) Consequence.unit else
        Consequence.argumentPolicyViolation("sbt", "sbt.execution.intent", "typed target and operation", "invalid")
      _ <- SbtScenarioSelection.validateOptionC(target.scenario, operation)
      workspace <- context.workspaceExecutionAdmissionOption.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Workspace Execution admission is not configured")
      )
      binding <- workspace.admitC(context.cncfCore.scope, target.workspaceId, WorkspacePermission.Manage)
      admission <- context.cncfCore.scope.processExecutionAdmissionOption.map(Consequence.success).getOrElse(
        Consequence.serviceUnavailable("Process Execution admission is not configured")
      )
      execution <- admission.admitSbtForScopeC(context.cncfCore.scope, binding, operation, target.scenario)
      _ <- SbtValidationDiscovery.admitC(target, operation, execution)
      batched <- SbtValidationMode.specializeBatchC(execution, target, operation)
      mode <- SbtValidationMode.specializeC(batched, target)
      jvm <- SbtJvmParameters.applyC(context.resolvedParameters, mode)
      limits <- SbtTestOutputParameters.applyC(context.resolvedParameters, operation, jvm.effectiveLimits)
      estimated <- SbtValidationEstimate.applyC(target, operation,
        jvm.definition._sbt_metadata.map(_.testClasses).getOrElse(Vector.empty), limits)
    } yield jvm.copy(effectiveLimits = estimated)

  /** Tagged executions have no WorkArea projections or caller-provided process input. */
  def validateRequestC(request: ProcessExecutionRequest): Consequence[Unit] =
    if (request.arguments.isEmpty && request.workingDirectory.isEmpty && request.outputs.isEmpty &&
      request.inputFiles.isEmpty && request.resourceTrees.isEmpty &&
      request.input == ProcessExecutionInput.Empty && request.requestedLimits == ProcessExecutionLimits.empty)
      Consequence.unit
    else
      Consequence.argumentPolicyViolation(
        "sbt", "sbt.execution.fixed-intent", "runtime-owned Compile/Test/TestOnly/FixCheck without request overrides", "unapproved"
      )
}
