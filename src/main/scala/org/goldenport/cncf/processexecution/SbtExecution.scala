package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId, WorkspacePermission}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SbtTarget(workspaceId: WorkspaceId, scenario: Option[SbtScenarioSelection] = None)

enum SbtOperation {
  case Compile, Test, TestOnly
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
}

final case class SbtExecutionResult(
  target: SbtTarget,
  operation: SbtOperation,
  processResult: ProcessExecutionResult,
  testSelection: Option[SbtTestSelectionResult] = None
) {
  def outcome: SbtExecutionOutcome = processResult.termination match {
    case ProcessExecutionTermination.NotStarted => SbtExecutionOutcome.NotExecuted
    case ProcessExecutionTermination.Exited(0) if operation == SbtOperation.TestOnly => testSelection match {
      case Some(SbtTestSelectionResult.Verified(_, _, _)) => SbtExecutionOutcome.Succeeded
      case Some(SbtTestSelectionResult.Rejected(reason)) => SbtExecutionOutcome.UnverifiedTests(reason)
      case None => SbtExecutionOutcome.UnverifiedTests(SbtTestSelectionFailure.MissingSelection)
    }
    case ProcessExecutionTermination.Exited(0) => SbtExecutionOutcome.Succeeded
    case ProcessExecutionTermination.Exited(code) => SbtExecutionOutcome.Failed(code)
    case termination => SbtExecutionOutcome.Incomplete(termination)
  }

  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.Build, target.workspaceId)
}

/** Immutable runtime tag: neither a request selector nor a separate tool registry. */
private[cncf] final case class SbtProgramMetadata(
  binding: WorkspaceBinding,
  operation: SbtOperation,
  testClasses: Vector[String] = Vector.empty,
  scenario: Option[SbtScenarioSelection] = None
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
    testClasses: Vector[String] = Vector.empty
  ): Consequence[ProcessProgramDefinition] =
    for {
      _ <- binding.workingDirectoryC
      _ <- if (operation != null) Consequence.unit else _invalid_operation_c
      _ <- SbtTestSelectionPolicy.validateC(operation, testClasses)
      executable <- _host_location_c(sbtExecutable)
      arguments = Vector(
        "--batch",
        "-Dsbt.server.autostart=false",
        "-Dsbt.supershell=false",
        "-Dsbt.color=false",
        operation match {
          case SbtOperation.Compile => "compile"
          case SbtOperation.Test => "test"
          case SbtOperation.TestOnly => "testOnly " + testClasses.mkString(" ")
        }
      )
      definition <- ProcessProgramDefinition.fromSbtRuntimeC(
        capability, safeProgramIdentity, executable.toString, arguments, limits,
        environment, SbtProgramMetadata(binding, operation, testClasses)
      )
    } yield definition

  private def _host_location_c(path: Path): Consequence[Path] =
    try {
      if (path == null || !path.isAbsolute || path.toString.exists(_.isControl))
        _invalid_location_c
      else {
        val real = path.toRealPath()
        if (Files.isRegularFile(real) && Files.isExecutable(real))
          Consequence.success(real)
        else
          _invalid_location_c
      }
    } catch {
      case NonFatal(_) => _invalid_location_c
    }

  private def _invalid_location_c: Consequence[Path] =
    Consequence.argumentPolicyViolation(
      "sbt", "sbt.execution.runtime-location", "absolute available sbt executable", "invalid"
    )

  private def _invalid_operation_c: Consequence[Unit] =
    Consequence.argumentPolicyViolation("operation", "sbt.execution.operation", "Compile, Test or TestOnly", "unknown")
}

private[cncf] object SbtExecutionAdmission {
  def decode(
    target: SbtTarget,
    operation: SbtOperation,
    execution: ResolvedProcessExecution,
    result: ProcessExecutionResult
  ): SbtExecutionResult = {
    val metadata = execution.definition._sbt_metadata
    val selection = if (operation == SbtOperation.TestOnly && result.termination == ProcessExecutionTermination.Exited(0))
      Some(SbtTestSelectionDecoder.decode(metadata.map(_.testClasses).getOrElse(Vector.empty), result,
        metadata.flatMap(_.scenario)))
    else None
    SbtExecutionResult(target, operation, result, selection)
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
      installed <- RuntimeConfig.installedRuntimeDistributionC(context.resolvedParameters)
      bound <- installed match {
        case None => Consequence.success(execution)
        case Some(distribution) =>
          val definition = execution.definition
          definition._sbt_metadata match {
            case Some(metadata) =>
              val arguments = definition.fixedArguments.dropRight(1) ++ Vector(
                s"-D${RuntimeConfig.installedRuntimeManifestKey}=${distribution.manifest}",
                "-Dcncf.installed-runtime.required=true",
                "cozyCheckInstalledRuntime",
                definition.fixedArguments.last
              )
              ProcessProgramDefinition.fromSbtRuntimeC(
                definition.capability, definition.safeProgramIdentity, definition._executable_location,
                arguments, definition.maximumLimits, definition._environment.values, metadata
              ).map(bound => execution.copy(definition = bound))
            case None => Consequence.serviceUnavailable("Typed SBT program metadata is unavailable")
          }
      }
    } yield bound

  /** Tagged executions have no WorkArea projections or caller-provided process input. */
  def validateRequestC(request: ProcessExecutionRequest): Consequence[Unit] =
    if (request.arguments.isEmpty && request.workingDirectory.isEmpty && request.outputs.isEmpty &&
      request.inputFiles.isEmpty && request.resourceTrees.isEmpty &&
      request.input == ProcessExecutionInput.Empty && request.requestedLimits == ProcessExecutionLimits.empty)
      Consequence.unit
    else
      Consequence.argumentPolicyViolation(
        "sbt", "sbt.execution.fixed-intent", "runtime-owned Compile/Test/TestOnly without request overrides", "unapproved"
      )
}
