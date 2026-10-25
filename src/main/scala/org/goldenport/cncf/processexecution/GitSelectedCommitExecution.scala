package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Oct. 24, 2026
 * @version Oct. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object GitSelectedCommitExecution {
  def executeC[A](context: ExecutionContext, target: GitTarget, operation: GitOperation[A],
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult]): Consequence[GitExecutionResult[A]] =
    GitExecutionAdmission.resolveC(context, target, operation).flatMap { execution =>
      if (operation.kind != GitOperationKind.CommitSelected)
        run(execution).map(GitExecutionAdmission.decode(target, operation, execution, _))
      else for {
        probe <- ProcessProgramDefinition.gitSelectionProbeC(execution.definition)
        receipt <- _run_c(execution, probe, run)
        result <- _prepare_c(target, operation, execution, receipt, run)
      } yield result
    }

  private def _prepare_c[A](target: GitTarget, operation: GitOperation[A], execution: ResolvedProcessExecution,
    receipt: ProcessExecutionResult, run: ResolvedProcessExecution => Consequence[ProcessExecutionResult]
  ): Consequence[GitExecutionResult[A]] = {
    val parsed = for {
      _ <- _complete_success(receipt)
      paths <- GitOutputParser.parsePaths(receipt.stdout).left.map(GitExecutionOutcome.InvalidOutput.apply)
      _ <- Either.cond(paths.nonEmpty && paths.size <= 256, (),
        GitExecutionOutcome.InvalidOutput("Expected one to 256 changed Git filenames"))
      scopes = execution.definition._git_metadata.get.selectedPaths
      _ <- Either.cond(paths.forall(path => scopes.exists(scope =>
        path == scope || path.value.startsWith(scope.value + "/"))), (),
        GitExecutionOutcome.InvalidOutput("Changed Git filename outside admitted selection"))
    } yield paths
    parsed match {
      case Left(outcome) => Consequence.success(GitExecutionResult[A](target, operation, outcome, None, receipt, Vector.empty))
      case Right(paths) => for {
        clean <- ProcessProgramDefinition.gitSelectedWorktreeProbeC(execution.definition, paths)
        checked <- _run_c(execution, clean, run)
        result <- _complete_success(checked).flatMap { _ =>
          Either.cond(checked.stdout.content.isEmpty && checked.stderr.content.isEmpty, (),
            GitExecutionOutcome.InvalidOutput("Unexpected Git worktree-check output"))
        } match {
          case Left(outcome) => Consequence.success(GitExecutionResult[A](target, operation, outcome, None, checked, paths))
          case Right(_) => for {
            narrowed <- ProcessProgramDefinition.narrowGitSelectedCommitC(execution.definition, paths)
            admitted = execution.copy(definition = narrowed, request = execution.request.copy(arguments = Vector.empty))
            committed <- _run_c(execution, narrowed, run)
          } yield GitExecutionAdmission.decode(target, operation, admitted, committed)
        }
      } yield result
    }
  }

  private def _run_c(execution: ResolvedProcessExecution, definition: ProcessProgramDefinition,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult]): Consequence[ProcessExecutionResult] = {
    val narrowed = execution.copy(definition = definition, request = execution.request.copy(arguments = Vector.empty))
    for {
      _ <- narrowed.request.validateC
      _ <- ProcessArgumentTemplate(narrowed.effectiveArguments.map(ProcessArgumentSlot.Literal.apply)).validateC
      _ <- definition.validateRequestC(narrowed.request, narrowed.effectiveLimits)
      // Fixed runtime arguments retain the inherited finite count/byte ceilings too.
      _ <- ProcessArgumentPolicy(Vector.empty, permittedArgumentVectors = Set(narrowed.effectiveArguments))
        .validateC(narrowed.effectiveArguments, narrowed.effectiveLimits)
      result <- run(narrowed)
    } yield result
  }

  private def _complete_success(result: ProcessExecutionResult): Either[GitExecutionOutcome, Unit] =
    result.termination match {
      case ProcessExecutionTermination.Exited(code) =>
        if (result.stdout.truncated || result.stdout.byteCount != result.stdout.content.size.toLong ||
          result.stderr.truncated || result.stderr.byteCount != result.stderr.content.size.toLong)
          Left(GitExecutionOutcome.InvalidOutput("Incomplete Git selected-commit preparation capture"))
        else if (code != 0) Left(GitExecutionOutcome.Failed(code))
        else Right(())
      case termination => Left(GitExecutionOutcome.Incomplete(termination))
    }
}
