package org.goldenport.cncf.processexecution

import java.nio.file.{Files, Path}
import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.effect.{ExternalEffect, ExternalEffectKind}
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId, WorkspacePermission}

/*
 * @since   Nov.  3, 2026
 * @version Nov.  3, 2026
 * @author  ASAMI, Tomoharu
 */
opaque type ValidationCheckId = String

object ValidationCheckId {
  def parseC(value: String): Consequence[ValidationCheckId] =
    if (value != null && value.nonEmpty && !value.exists(_.isControl) &&
        !_whitespace(value.head) && !_whitespace(value.last))
      Consequence.success(value)
    else
      Consequence.argumentFormatError("checkIdentity", "exact nonempty logical check identity without control or edge whitespace", "invalid")

  private def _whitespace(value: Char): Boolean = value.isWhitespace || Character.isSpaceChar(value)

  extension (identity: ValidationCheckId) {
    def value: String = identity
    def print: String = identity
  }
}

final case class ValidationCheckTarget(workspaceId: WorkspaceId, checkIdentity: ValidationCheckId)

enum ValidationCheckOperation {
  case Run
}

enum ValidationCheckPurpose(val wireName: String) {
  case Compilation extends ValidationCheckPurpose("compilation")
  case Behavior extends ValidationCheckPurpose("behavior")
  case Lint extends ValidationCheckPurpose("lint")
}

enum ValidationCheckExecutionAttempt {
  case Observed(result: ValidationCheckExecutionResult)
  case AdmissionRejected(target: ValidationCheckTarget, operation: ValidationCheckOperation, conclusion: Conclusion)
  case Uncertain(target: ValidationCheckTarget, operation: ValidationCheckOperation, conclusion: Conclusion)
}

enum ValidationCheckExecutionOutcome {
  case Succeeded
  case NotExecuted
  case Failed(exitCode: Int)
  case IncompleteOutput
  case Incomplete(termination: ProcessExecutionTermination)
}

final case class ValidationCheckExecutionResult(
  target: ValidationCheckTarget,
  operation: ValidationCheckOperation,
  processResult: ProcessExecutionResult,
  purpose: ValidationCheckPurpose
) {
  def outcome: ValidationCheckExecutionOutcome = processResult.termination match {
    case ProcessExecutionTermination.NotStarted => ValidationCheckExecutionOutcome.NotExecuted
    case ProcessExecutionTermination.Exited(0) =>
      if (_complete(processResult.stdout) && _complete(processResult.stderr)) ValidationCheckExecutionOutcome.Succeeded
      else ValidationCheckExecutionOutcome.IncompleteOutput
    case ProcessExecutionTermination.Exited(code) => ValidationCheckExecutionOutcome.Failed(code)
    case termination => ValidationCheckExecutionOutcome.Incomplete(termination)
  }

  def effect: ExternalEffect = ExternalEffect(ExternalEffectKind.Build, target.workspaceId)

  private def _complete(capture: ProcessExecutionCapture): Boolean =
    capture != null && capture.content != null && capture.byteCount == capture.content.size.toLong && !capture.truncated
}

/** Only trusted host assembly creates this immutable semantic tag. */
private[cncf] final case class ValidationCheckProgramMetadata(
  binding: WorkspaceBinding,
  checkIdentity: ValidationCheckId,
  purpose: ValidationCheckPurpose
)

private[cncf] object ValidationCheckProgramDefinition {
  val ADAPTER = "fixed-process"
  private val _launcher_names = Set("sh", "bash", "zsh", "dash", "ksh", "csh", "tcsh", "fish",
    "cmd", "powershell", "pwsh", "env", "xargs", "sudo", "doas")

  def createC(
    binding: WorkspaceBinding,
    checkIdentity: ValidationCheckId,
    purpose: ValidationCheckPurpose,
    adapter: String,
    capability: ProcessCapabilityId,
    safeProgramIdentity: String,
    executable: Path,
    fixedArguments: Vector[String],
    limits: ProcessExecutionLimits,
    environment: Map[String, String]
  ): Consequence[ProcessProgramDefinition] = for {
    _ <- if (binding != null && purpose != null && capability != null && limits != null && environment != null &&
        fixedArguments != null && fixedArguments.forall(x => x != null && !x.contains('\u0000')))
      Consequence.unit else _invalid_c
    _ <- ValidationCheckId.parseC(checkIdentity.value)
    _ <- if (adapter == ADAPTER) Consequence.unit else _invalid_c
    _ <- binding.workingDirectoryC
    location <- _host_location_c(executable)
    _ <- ProcessArgumentPolicy(Vector.empty, permittedArgumentVectors = Set(fixedArguments)).validateC(fixedArguments, limits)
    definition <- ProcessProgramDefinition.fromValidationCheckRuntimeC(capability, safeProgramIdentity,
      location.toString, fixedArguments, limits, environment, ValidationCheckProgramMetadata(binding, checkIdentity, purpose))
  } yield definition

  private def _host_location_c(path: Path): Consequence[Path] = try {
    def _launcher_(value: Path): Boolean =
      _launcher_names.contains(value.getFileName.toString.toLowerCase(Locale.ROOT).stripSuffix(".exe"))
    if (path == null || !path.isAbsolute || path.toString.exists(_.isControl) || _launcher_(path))
      _invalid_c
    else {
      val real = path.toRealPath()
      if (Files.isRegularFile(real) && Files.isExecutable(real) && !_launcher_(real)) Consequence.success(real)
      else _invalid_c
    }
  } catch { case NonFatal(_) => _invalid_c }

  private def _invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid("Validation check requires an explicit purpose, fixed-process adapter, admitted workspace and available ordinary absolute executable with fixed arguments, environment and finite limits")
}

private[cncf] object ValidationCheckExecutionAdmission {
  def decode(target: ValidationCheckTarget, operation: ValidationCheckOperation,
    execution: ResolvedProcessExecution, result: ProcessExecutionResult): ValidationCheckExecutionResult =
    ValidationCheckExecutionResult(target, operation, result, execution.definition._validation_check_metadata.get.purpose)

  def resolveC(context: ExecutionContext, target: ValidationCheckTarget,
    operation: ValidationCheckOperation): Consequence[ResolvedProcessExecution] = for {
    _ <- if (target != null && operation == ValidationCheckOperation.Run) Consequence.unit else
      Consequence.argumentPolicyViolation("validationCheck", "validation-check.execution.intent", "typed target and Run operation", "invalid")
    _ <- WorkspaceId.parseC(target.workspaceId.value)
    _ <- ValidationCheckId.parseC(target.checkIdentity.value)
    workspace <- context.workspaceExecutionAdmissionOption.map(Consequence.success).getOrElse(
      Consequence.serviceUnavailable("Workspace Execution admission is not configured"))
    binding <- workspace.admitC(context.cncfCore.scope, target.workspaceId, WorkspacePermission.Manage)
    admission <- context.cncfCore.scope.processExecutionAdmissionOption.map(Consequence.success).getOrElse(
      Consequence.serviceUnavailable("Process Execution admission is not configured"))
    execution <- admission.admitValidationCheckForScopeC(context.cncfCore.scope, binding, target.checkIdentity)
  } yield execution

  def validateGenericExecutionC(execution: ResolvedProcessExecution): Consequence[Unit] =
    if (execution.definition._validation_check_metadata.isEmpty) Consequence.unit else
      Consequence.argumentPolicyViolation("validationCheck", "validation-check.execution.typed-intent", "typed UnitOfWork validation check intent", "generic ProcessExec")

  def validateRequestC(request: ProcessExecutionRequest): Consequence[Unit] =
    if (request.arguments.isEmpty && request.workingDirectory.isEmpty && request.outputs.isEmpty &&
        request.inputFiles.isEmpty && request.resourceTrees.isEmpty && request.input == ProcessExecutionInput.Empty &&
        request.requestedLimits == ProcessExecutionLimits.empty) Consequence.unit else
      Consequence.argumentPolicyViolation("validationCheck", "validation-check.execution.fixed-intent", "runtime-owned declared check without request overrides", "unapproved")
}
