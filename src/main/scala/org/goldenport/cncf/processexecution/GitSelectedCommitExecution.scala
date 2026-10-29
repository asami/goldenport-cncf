package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Oct. 24, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object GitSelectedCommitExecution {
  def executeC[A](context: ExecutionContext, target: GitTarget, operation: GitOperation[A],
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult]): Consequence[GitExecutionResult[A]] =
    GitExecutionAdmission.resolveC(context, target, operation).flatMap { execution =>
      if (GitPurposeCandidateExecution.isPurpose(operation.kind))
        GitPurposeCandidateExecution.executeC(context, target, operation, execution, run)
      else if (operation.kind != GitOperationKind.CommitSelected)
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

/** Closed purpose preparation and installation within the existing Git UoW.
  * Temporary indexes and merge inputs are execution resources, not an authority
  * or recovery ledger. Only native Git objects outlive successful preparation.
  */
private[cncf] object GitPurposeCandidateExecution {
  import cats.data.EitherT
  import cats.syntax.all.*
  import java.nio.charset.StandardCharsets.UTF_8
  import java.nio.file.{Files, LinkOption, Path}
  import scala.jdk.CollectionConverters.*
  import org.goldenport.cncf.workspace.{WorkspaceId, WorkspacePermission}

  private final case class Failure(outcome: GitExecutionOutcome, receipt: ProcessExecutionResult)
  private type Program[A] = EitherT[Consequence, Failure, A]
  private final case class Entry(mode: String, blob: GitObjectId)
  private final case class Projection(path: GitPath, mode: String, content: Option[String], indexed: Option[String])
  private final case class Trees(tree: GitObjectId, index: GitObjectId, worktreebefore: GitObjectId, worktreeafter: GitObjectId)

  def isPurpose(kind: GitOperationKind): Boolean = Set(GitOperationKind.PreparePurposeCandidate,
    GitOperationKind.MaterializePurposeCandidate, GitOperationKind.CommitPurposeCandidate)(kind)

  def executeC[A](context: ExecutionContext, target: GitTarget, operation: GitOperation[A],
    execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult]): Consequence[GitExecutionResult[A]] = for {
    root <- execution.definition._git_metadata.get.binding.workingDirectoryC
    area <- ProcessExecutionWorkArea.allocateC(context.cncfCore.scope.workAreaSpace)
    directory = area.root
    result <- {
      val metadata = execution.definition._git_metadata.get
      val paths = metadata.selectedPaths
      val selected = operation match {
        case GitOperation.PreparePurposeCandidate(_, request) => for {
          trees <- _build_c(execution, run, root, directory, request)
          message <- _admit_c(_message_c(metadata))
          commit <- _oid_c(execution, run, _identity(metadata) ++ Vector("commit-tree", "--no-gpg-sign",
            "-m", message, trees.tree.value, "-p", request.parent.value))
          receipt <- _step_c(execution, run, Vector("rev-parse", "--verify", "HEAD"))
        } yield GitPurposeCandidate(request, commit, trees.tree, trees.index, message, Some(trees.worktreebefore), Some(trees.worktreeafter)).asInstanceOf[A] -> receipt
        case GitOperation.MaterializePurposeCandidate(_, candidate, workspace) => for {
          _ <- _candidate_c(execution, run, root, directory, candidate)
          destination <- _admit_c(_destination_c(context, metadata, workspace))
          receipt <- _step_c(execution, run, Vector("worktree", "add", "--detach", "--",
            destination.toString, candidate.commit.value))
        } yield ().asInstanceOf[A] -> receipt
        case GitOperation.CommitPurposeCandidate(_, candidate) => for {
          _ <- _candidate_c(execution, run, root, directory, candidate)
          _ <- if (candidate.worktreeBefore == candidate.worktreeAfter) _success(()) else for {
            _ <- _step_c(execution, run, Vector("read-tree", candidate.worktreeBefore.get.value),
              index = Some(directory.resolve("installation-index").toString))
            installationindex = Some(directory.resolve("installation-index").toString)
            present = candidate.request.changes.filter(_.expectedWorktree.nonEmpty).map(_.path.value)
            // Tree-loaded entries lack the stat data required by Git's safe
            // worktree update. Refresh only selected files in this private index.
            _ <- if (present.isEmpty) _success(()) else
              _step_c(execution, run, Vector("update-index", "--add", "--") ++ present,
                index = installationindex).map(_ => ())
            refreshed <- _oid_c(execution, run, Vector("write-tree"), index = installationindex)
            _ <- _admit_c(if (candidate.worktreeBefore.contains(refreshed)) Consequence.unit
              else Consequence.stateConflict("Purpose selected worktree changed before repair installation"))
            _ <- _step_c(execution, run, Vector("read-tree", "-m", "-u", candidate.worktreeBefore.get.value, candidate.worktreeAfter.get.value),
              index = installationindex)
          } yield ()
          _ <- _step_c(execution, run, Vector("update-ref", "-m", candidate.message,
            "HEAD", candidate.commit.value, candidate.request.parent.value))
          _ <- _step_c(execution, run, Vector("read-tree", candidate.projectedIndex.value))
          actual <- _oid_c(execution, run, Vector("write-tree"))
          _ <- _admit_c(if (actual == candidate.projectedIndex) Consequence.unit
            else Consequence.stateConflict("Purpose commit installed but projected index observation differs; collect actual effect, do not replay"))
          receipt <- _step_c(execution, run, Vector("rev-parse", "--verify", "HEAD"))
        } yield ().asInstanceOf[A] -> receipt
        case _ => _admit_c[(A, ProcessExecutionResult)](GitProgramDefinition.invalidC)
      }
      val settled = selected.flatMap { answer =>
        _admit_c(area.collectArtifactsC(execution).flatMap(value =>
          if (!value.limitExceeded) Consequence.unit else Consequence.argumentLimitExceeded(
            "purposeWorkArea", execution.effectiveLimits.workAreaBytes.get,
            execution.effectiveLimits.workAreaBytes.get + 1, "git.purpose-candidate"))).map(_ => answer)
      }.value
      // Lifted admission/resource failures settle in the outer Consequence too.
      // Only an actual unconfirmed process can still own these private inputs.
      val keep = settled.toOption.exists(_.left.toOption.exists(
        _.receipt.termination == ProcessExecutionTermination.TerminationUnconfirmed))
      if (!keep) area.close()
      settled.map { value =>
        value match {
          case Right((answer, receipt)) => GitExecutionResult(target, operation,
            GitExecutionOutcome.Succeeded, Some(answer), receipt, paths)
          case Left(failure) => GitExecutionResult[A](target, operation,
            failure.outcome, None, failure.receipt, paths)
        }
      }
    }
  } yield result

  private def _build_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], root: Path, directory: Path,
    request: GitPurposeRequest): Program[Trees] = for {
    _ <- _admit_c(_validate_c(execution, request))
    head <- _oid_c(execution, run, Vector("rev-parse", "--verify", "HEAD"))
    index <- _oid_c(execution, run, Vector("write-tree"))
    branch <- _text_c(execution, run, Vector("symbolic-ref", "--quiet", "--short", "HEAD"))
    _ <- _admit_c(if (head == request.parent && index == request.index &&
        branch == execution.definition._git_metadata.get.localBranch.value + "\n") Consequence.unit
      else Consequence.stateConflict("Purpose candidate expected HEAD/index/branch differs from its actual source"))
    projections <- request.changes.foldLeft(_success(Vector.empty[Projection])) { (prior, change) => for {
      values <- prior
      worktree <- _admit_c(_worktree_c(root, change.path, execution.effectiveLimits.workAreaBytes.get / 8))
      _ <- _admit_c(if (worktree == change.expectedWorktree) Consequence.unit
        else Consequence.stateConflict("Purpose candidate selected worktree content differs from its expected source"))
      original <- _entry_c(execution, run, request.parent, change.path)
      staged <- _entry_c(execution, run, request.index, change.path)
      _ <- _admit_c(if (original.toVector.map(_.mode).toSet.union(staged.toVector.map(_.mode).toSet).size <= 1)
        Consequence.unit else Consequence.stateConflict("Purpose candidate mode changes require explicit semantic resolution"))
      before <- _content_c(execution, run, original)
      indexed <- _content_c(execution, run, staged)
      remainder <- _project_c(execution, run, directory, before, indexed, change.content)
      mode = original.orElse(staged).fold("100644")(_.mode)
    } yield values :+ Projection(change.path, mode, change.content, remainder) }
    tree <- _tree_c(execution, run, directory.resolve("candidate-index"), request.parent, projections, false)
    projected <- _tree_c(execution, run, directory.resolve("remainder-index"), request.index, projections, true)
    working <- request.changes.foldLeft(_success(Vector.empty[Projection])) { (prior, change) => for {
      values <- prior
      original <- _entry_c(execution, run, request.parent, change.path)
      baseline <- request.baselineTree match {
        case None => _success(change.content)
        case Some(tree) => _entry_c(execution, run, tree, change.path).flatMap(_content_c(execution, run, _))
      }
      after <- if (request.baselineTree.isEmpty) _success(change.expectedWorktree)
        else _project_c(execution, run, directory, baseline, change.expectedWorktree, change.content)
    } yield values :+ Projection(change.path, original.fold("100644")(_.mode), change.expectedWorktree, after) }
    beforeworktree <- _tree_c(execution, run, directory.resolve("worktree-before-index"), request.parent, working, false)
    afterworktree <- _tree_c(execution, run, directory.resolve("worktree-after-index"), request.parent, working, true)
    originaltree <- _oid_c(execution, run, Vector("rev-parse", "--verify", request.parent.value + "^{tree}"))
    _ <- _admit_c(if (tree != originaltree) Consequence.unit
      else Consequence.stateConflict("Purpose candidate has no actual content change"))
    _ <- _step_c(execution, run, Vector("diff-tree", "--no-commit-id", "--no-ext-diff", "--no-textconv",
      "--check", "-r", request.parent.value, tree.value, "--"))
  } yield Trees(tree, projected, beforeworktree, afterworktree)

  private def _candidate_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], root: Path, directory: Path,
    candidate: GitPurposeCandidate): Program[Unit] = for {
    _ <- _admit_c(if (candidate != null && candidate.commit != null && candidate.tree != null &&
        candidate.projectedIndex != null && candidate.worktreeBefore != null && candidate.worktreeAfter != null &&
        candidate.worktreeBefore.nonEmpty && candidate.worktreeAfter.nonEmpty) Consequence.unit else GitProgramDefinition.invalidC)
    message <- _admit_c(_message_c(execution.definition._git_metadata.get))
    _ <- _admit_c(if (candidate.message == message) Consequence.unit else GitProgramDefinition.invalidC)
    trees <- _build_c(execution, run, root, directory, candidate.request)
    _ <- _admit_c(if (trees.tree == candidate.tree && trees.index == candidate.projectedIndex &&
        candidate.worktreeBefore.contains(trees.worktreebefore) && candidate.worktreeAfter.contains(trees.worktreeafter))
      Consequence.unit else Consequence.stateConflict("Purpose candidate tree or staged remainder differs from its actual inputs"))
    commit <- _text_c(execution, run, Vector("cat-file", "commit", candidate.commit.value))
    _ <- _admit_c(if (commit.startsWith("tree " + candidate.tree.value + "\nparent " + candidate.request.parent.value + "\n") &&
        commit.takeWhile(_ != '\u0000').split("\n\n", 2).toVector.lastOption.contains(message + "\n") &&
        commit.split("\n\n", 2).head.split("\n").count(_.startsWith("parent ")) == 1)
      Consequence.unit else Consequence.stateConflict("Purpose candidate commit does not match its tree, parent and message"))
  } yield ()

  private def _validate_c(execution: ResolvedProcessExecution, request: GitPurposeRequest): Consequence[Unit] = {
    val metadata = execution.definition._git_metadata.get
    val valid = request != null && request.parent != null && request.index != null && request.baselineTree != null &&
      request.baselineTree.forall(_ != null) &&
      request.changes != null && request.changes.nonEmpty && request.changes.size <= 256 &&
      request.changes.forall(change => change != null && change.path != null &&
        GitPath.parseC(change.path.value).isSuccess && change.expectedWorktree != null && change.content != null &&
        Vector(change.expectedWorktree, change.content).flatten.forall(text => text != null &&
          !text.contains('\u0000') && UTF_8.newEncoder().canEncode(text)) &&
        metadata.selectedPaths.exists(scope => change.path == scope || change.path.value.startsWith(scope.value + "/"))) &&
      request.changes.map(_.path).distinct.size == request.changes.size
    if (!valid) GitProgramDefinition.invalidC
    else {
      val bytes = request.changes.flatMap(change => change.expectedWorktree.toVector ++ change.content.toVector)
        .map(_.getBytes(UTF_8).length.toLong).sum
      if (bytes <= execution.effectiveLimits.workAreaBytes.get / 8 &&
          bytes <= execution.effectiveLimits.stdinBytes.get && bytes <= execution.effectiveLimits.stdoutBytes.get)
        Consequence.unit
      else Consequence.argumentLimitExceeded("purposeContent", execution.effectiveLimits.workAreaBytes.get / 8,
        bytes, "git.purpose-candidate")
    }
  }

  private def _message_c(metadata: GitProgramMetadata): Consequence[String] = metadata.commitMessage match {
    case Some(message) if message.nonEmpty && message == message.trim => Consequence.success(message)
    case _ => GitProgramDefinition.invalidC
  }

  private def _entry_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], tree: GitObjectId,
    path: GitPath): Program[Option[Entry]] = for {
    text <- _text_c(execution, run, Vector("--literal-pathspecs", "ls-tree", "--full-tree", "-z", tree.value, "--", path.value))
    entry <- _admit_c(if (text.isEmpty) Consequence.success(Option.empty[Entry]) else {
      val fields = text.stripSuffix("\u0000").split("\t", 2)
      val header = fields.head.split(" ", -1)
      if (text.endsWith("\u0000") && text.count(_ == '\u0000') == 1 && fields.length == 2 && fields(1) == path.value &&
          header.length == 3 && Set("100644", "100755")(header(0)) && header(1) == "blob")
        GitObjectId.parseC(header(2)).map(blob => Some(Entry(header(0), blob)))
      else Consequence.stateConflict("Purpose candidate requires exact regular-file tree entries")
    })
  } yield entry

  private def _content_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], entry: Option[Entry]): Program[Option[String]] = entry match {
    case None => _success(None)
    case Some(value) => _text_c(execution, run, Vector("cat-file", "blob", value.blob.value)).flatMap { text =>
      _admit_c(if (!text.contains('\u0000')) Consequence.success(Some(text))
        else Consequence.stateConflict("Binary purpose input remains unexamined")) }
  }

  private def _project_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], directory: Path,
    before: Option[String], indexed: Option[String], content: Option[String]): Program[Option[String]] =
    if (indexed == before || indexed == content) _success(content)
    else if (content == before) _success(indexed)
    else (before, indexed, content) match {
      case (Some(base), Some(staged), Some(selected)) => for {
        _ <- _admit_c(_write_merge_c(directory, selected, base, staged))
        merged <- _text_c(execution, run, Vector("merge-file", "-p", "--",
          directory.resolve("selected").toString, directory.resolve("base").toString,
          directory.resolve("staged").toString))
      } yield Some(merged)
      case _ => _admit_c(Consequence.stateConflict("Purpose deletion/addition has an inseparable staged remainder; explicit grouping is required"))
    }

  private def _tree_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], index: Path,
    base: GitObjectId, projections: Vector[Projection], remainder: Boolean): Program[GitObjectId] = for {
    _ <- _step_c(execution, run, Vector("read-tree", base.value), index = Some(index.toString))
    entries <- projections.foldLeft(_success(Vector.empty[String])) { (prior, projection) => for {
      values <- prior
      blob <- ((if (remainder) projection.indexed else projection.content): Option[String]) match {
        case Some(text) => _oid_c(execution, run, Vector("hash-object", "-w", "--no-filters", "--stdin"),
          text.getBytes(UTF_8).toVector).map(value => projection.mode + " " + value.value)
        case None => _success("0 " + ("0" * base.value.length))
      }
    } yield values :+ (blob + "\t" + projection.path.value + "\u0000") }
    _ <- _step_c(execution, run, Vector("update-index", "-z", "--index-info"),
      entries.mkString.getBytes(UTF_8).toVector, Some(index.toString))
    tree <- _oid_c(execution, run, Vector("write-tree"), index = Some(index.toString))
  } yield tree

  private def _step_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], arguments: Vector[String],
    input: Vector[Byte] = Vector.empty, index: Option[String] = None): Program[ProcessExecutionResult] = EitherT(for {
    definition <- ProcessProgramDefinition.gitPurposeStepC(execution.definition, arguments, index)
    request = execution.request.copy(arguments = Vector.empty,
      input = if (input.isEmpty) ProcessExecutionInput.Empty else ProcessExecutionInput.Bytes(input))
    narrowed = execution.copy(definition = definition, request = request)
    _ <- definition.validateRequestC(request, execution.effectiveLimits)
    _ <- ProcessArgumentPolicy(Vector.empty, permittedArgumentVectors = Set(narrowed.effectiveArguments))
      .validateC(narrowed.effectiveArguments, execution.effectiveLimits)
    receipt <- run(narrowed)
  } yield receipt.termination match {
    case ProcessExecutionTermination.Exited(code) if receipt.stdout.truncated || receipt.stderr.truncated ||
        receipt.stdout.byteCount != receipt.stdout.content.size.toLong || receipt.stderr.byteCount != receipt.stderr.content.size.toLong =>
      Left(Failure(GitExecutionOutcome.InvalidOutput("Incomplete purpose Git capture"), receipt))
    case ProcessExecutionTermination.Exited(0) => Right(receipt)
    case ProcessExecutionTermination.Exited(code) => Left(Failure(GitExecutionOutcome.Failed(code), receipt))
    case termination => Left(Failure(GitExecutionOutcome.Incomplete(termination), receipt))
  })

  private def _text_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], arguments: Vector[String],
    input: Vector[Byte] = Vector.empty, index: Option[String] = None): Program[String] =
    _step_c(execution, run, arguments, input, index).flatMap(receipt =>
      EitherT.fromEither[Consequence](GitOutputParser.utf8(receipt.stdout.content)
        .toRight(Failure(GitExecutionOutcome.InvalidOutput("Purpose Git output is not complete UTF-8 text"), receipt))))

  private def _oid_c(execution: ResolvedProcessExecution,
    run: ResolvedProcessExecution => Consequence[ProcessExecutionResult], arguments: Vector[String],
    input: Vector[Byte] = Vector.empty, index: Option[String] = None): Program[GitObjectId] =
    _text_c(execution, run, arguments, input, index).flatMap(text => _admit_c(GitObjectId.parseC(text.stripSuffix("\n"))))

  private def _admit_c[A](value: Consequence[A]): Program[A] = EitherT.liftF(value)
  private def _success[A](value: A): Program[A] = EitherT.rightT[Consequence, Failure](value)

  private def _identity(metadata: GitProgramMetadata): Vector[String] = metadata.commitIdentity.toVector.flatMap(value =>
    Vector("-c", "user.name=" + value.name, "-c", "user.email=" + value.email))

  private def _write_merge_c(directory: Path, selected: String, base: String, staged: String): Consequence[Unit] = try {
    Files.writeString(directory.resolve("selected"), selected, UTF_8)
    Files.writeString(directory.resolve("base"), base, UTF_8)
    Files.writeString(directory.resolve("staged"), staged, UTF_8)
    Consequence.unit
  } catch { case scala.util.control.NonFatal(_) => Consequence.serviceUnavailable("Purpose Git merge resources unavailable") }

  private def _worktree_c(root: Path, path: GitPath, maximum: Long): Consequence[Option[String]] = try {
    val file = root.resolve(path.value)
    val parents = Iterator.iterate(file)(_.getParent).takeWhile(_ != root).toVector
    if (parents.exists(value => Files.isSymbolicLink(value)))
      Consequence.stateConflict("Purpose input symlinks are unsupported")
    else if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Consequence.success(None)
    else if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maximum)
      Consequence.stateConflict("Purpose input is not a bounded regular text file")
    else GitOutputParser.utf8(Files.readAllBytes(file).toVector).filterNot(_.contains('\u0000')) match {
      case Some(text) => Consequence.success(Some(text))
      case None => Consequence.stateConflict("Binary purpose input remains unexamined")
    }
  } catch { case scala.util.control.NonFatal(_) => Consequence.serviceUnavailable("Purpose worktree observation unavailable") }

  private def _destination_c(context: ExecutionContext, metadata: GitProgramMetadata,
    workspace: WorkspaceId): Consequence[Path] = for {
    _ <- WorkspaceId.parseC(workspace.value).map(_ => ())
    admission <- context.workspaceExecutionAdmissionOption.map(Consequence.success)
      .getOrElse(Consequence.serviceUnavailable("Purpose validation workspace admission unavailable"))
    binding <- admission.admitC(context.cncfCore.scope, workspace, WorkspacePermission.Manage)
    destination <- binding.workingDirectoryC
    source <- metadata.binding.workingDirectoryC
    _ <- try {
      val files = Files.list(destination)
      val empty = try !files.findAny().isPresent finally files.close()
      if (destination != source && !destination.startsWith(source) && !source.startsWith(destination) && empty)
        Consequence.unit else Consequence.stateConflict("Purpose validation requires a distinct admitted empty workspace")
    } catch { case scala.util.control.NonFatal(_) => Consequence.serviceUnavailable("Purpose validation workspace observation unavailable") }
  } yield destination

}
