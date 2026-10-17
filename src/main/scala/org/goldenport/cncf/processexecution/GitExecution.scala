package org.goldenport.cncf.processexecution

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workspace.{WorkspaceBinding, WorkspaceId, WorkspacePermission}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class GitTarget(workspaceId: WorkspaceId)

final case class GitObjectId private (value: String)
object GitObjectId {
  def parseC(value: String): Consequence[GitObjectId] =
    if (value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
      Consequence.success(GitObjectId(value))
    else Consequence.argumentFormatError("objectId", "canonical lower hexadecimal Git object ID", "invalid")
}

final case class GitPath private (value: String)
object GitPath {
  def parseC(value: String): Consequence[GitPath] = {
    val parts = Option(value).getOrElse("").split("/", -1)
    if (value == null || value.isEmpty || value.startsWith("/") ||
      value.contains('\u0000') || parts.exists(x => x.isEmpty || x == "." || x == ".." || x == ".git"))
      Consequence.argumentFormatError("gitPath", "relative Git filename without traversal or Git metadata", "invalid")
    else Consequence.success(GitPath(value))
  }
}

final case class GitBranchName private (value: String)
object GitBranchName {
  def parseC(value: String): Consequence[GitBranchName] =
    if (value == null || value.isEmpty || value.startsWith("-") || value.startsWith("/") ||
      value.endsWith("/") || value.endsWith(".") || value.contains("..") || value.contains("//") ||
      value.contains("@{") || value == "@" || value.exists(c => c <= ' ' || c == 127 || "~^:?*[\\".contains(c)) ||
      value.split("/", -1).exists(x => x.startsWith(".") || x.endsWith(".lock")))
      Consequence.argumentFormatError("branch", "strict Git reference part", "invalid")
    else Consequence.success(GitBranchName(value))
}

final case class GitSelectionId private (value: String)
object GitSelectionId {
  def parseC(value: String): Consequence[GitSelectionId] =
    if (value != null && value.matches("[a-z][a-z0-9-]{0,63}")) Consequence.success(GitSelectionId(value))
    else Consequence.argumentFormatError("selectionId", "safe lowercase selection ID", "invalid")
}

enum GitOperationKind(val wireName: String) {
  case Branch extends GitOperationKind("branch")
  case Root extends GitOperationKind("root")
  case RemoteTip extends GitOperationKind("remote-tip")
  case Head extends GitOperationKind("head")
  case Tree extends GitOperationKind("tree")
  case Status extends GitOperationKind("status")
  case Shallow extends GitOperationKind("shallow")
  case Replacements extends GitOperationKind("replacements")
  case MergeHead extends GitOperationKind("merge-head")
  case TrackingTip extends GitOperationKind("tracking-tip")
  case Relation extends GitOperationKind("relation")
  case MergeBase extends GitOperationKind("merge-base")
  case StageFullState extends GitOperationKind("stage-full-state")
  case IndexTree extends GitOperationKind("index-tree")
  case UnmergedIndex extends GitOperationKind("unmerged-index")
  case Fetch extends GitOperationKind("fetch")
  case FastForward extends GitOperationKind("fast-forward")
  case PendingMerge extends GitOperationKind("pending-merge")
  case CommitPending extends GitOperationKind("commit-pending")
  case Push extends GitOperationKind("push")
  case CreateCheckpoint extends GitOperationKind("create-checkpoint")
  case InstallCheckpoint extends GitOperationKind("install-checkpoint")
  case CommitMetadata extends GitOperationKind("commit-metadata")
  case BlobContent extends GitOperationKind("blob-content")
  case CheckPendingIndex extends GitOperationKind("check-pending-index")
  case PendingDiff extends GitOperationKind("pending-diff")
  case PendingPaths extends GitOperationKind("pending-paths")
  case StageSelected extends GitOperationKind("stage-selected")
  case StageSelectedAll extends GitOperationKind("stage-selected-all")
  case CommitSelected extends GitOperationKind("commit-selected")

  private[cncf] def permission: WorkspacePermission = this match {
    case StageFullState | IndexTree | Fetch | FastForward | PendingMerge | CommitPending | Push |
        CreateCheckpoint | InstallCheckpoint | StageSelected | StageSelectedAll | CommitSelected => WorkspacePermission.Manage
    case _ => WorkspacePermission.ReadOnly
  }

  private[cncf] def requiresIdentity: Boolean = this match {
    case PendingMerge | CommitPending | CreateCheckpoint | CommitSelected => true
    case _ => false
  }

  private[cncf] def selected: Boolean = this match {
    case StageSelected | StageSelectedAll | CommitSelected => true
    case _ => false
  }
}

enum GitOperation[A] {
  case Branch extends GitOperation[String]
  case Root extends GitOperation[String]
  case RemoteTip extends GitOperation[GitRemoteTip]
  case Head extends GitOperation[GitObjectId]
  case Tree extends GitOperation[GitObjectId]
  case Status extends GitOperation[Vector[GitStatusEntry]]
  case Shallow extends GitOperation[Boolean]
  case Replacements extends GitOperation[Vector[GitObjectId]]
  case MergeHead extends GitOperation[Option[GitObjectId]]
  case TrackingTip extends GitOperation[Option[GitObjectId]]
  case Relation extends GitOperation[GitHistoryCounts]
  case MergeBase extends GitOperation[Option[GitObjectId]]
  case StageFullState extends GitOperation[Unit]
  case IndexTree extends GitOperation[GitObjectId]
  case UnmergedIndex extends GitOperation[Vector[GitIndexEntry]]
  case Fetch extends GitOperation[Unit]
  case FastForward(tip: GitObjectId) extends GitOperation[Unit]
  case PendingMerge(tip: GitObjectId) extends GitOperation[Unit]
  case CommitPending extends GitOperation[Unit]
  case Push(tip: GitObjectId, branch: GitBranchName) extends GitOperation[Unit]
  case CreateCheckpoint(tree: GitObjectId, parent: GitObjectId) extends GitOperation[GitObjectId]
  case InstallCheckpoint(commit: GitObjectId, parent: GitObjectId) extends GitOperation[Unit]
  case CommitMetadata(commit: GitObjectId) extends GitOperation[GitCommitMetadata]
  case BlobContent(blob: GitObjectId) extends GitOperation[GitBlobContent]
  case CheckPendingIndex(parent: GitObjectId) extends GitOperation[GitIndexCheck]
  case PendingDiff(parent: GitObjectId) extends GitOperation[GitDiff]
  case PendingPaths(parent: GitObjectId) extends GitOperation[Vector[GitPath]]
  case StageSelected(selectionId: GitSelectionId) extends GitOperation[Unit]
  case StageSelectedAll(selectionId: GitSelectionId) extends GitOperation[Unit]
  case CommitSelected(selectionId: GitSelectionId) extends GitOperation[Unit]

  def kind: GitOperationKind = this match {
    case Branch => GitOperationKind.Branch
    case Root => GitOperationKind.Root
    case RemoteTip => GitOperationKind.RemoteTip
    case Head => GitOperationKind.Head
    case Tree => GitOperationKind.Tree
    case Status => GitOperationKind.Status
    case Shallow => GitOperationKind.Shallow
    case Replacements => GitOperationKind.Replacements
    case MergeHead => GitOperationKind.MergeHead
    case TrackingTip => GitOperationKind.TrackingTip
    case Relation => GitOperationKind.Relation
    case MergeBase => GitOperationKind.MergeBase
    case StageFullState => GitOperationKind.StageFullState
    case IndexTree => GitOperationKind.IndexTree
    case UnmergedIndex => GitOperationKind.UnmergedIndex
    case Fetch => GitOperationKind.Fetch
    case FastForward(_) => GitOperationKind.FastForward
    case PendingMerge(_) => GitOperationKind.PendingMerge
    case CommitPending => GitOperationKind.CommitPending
    case Push(_, _) => GitOperationKind.Push
    case CreateCheckpoint(_, _) => GitOperationKind.CreateCheckpoint
    case InstallCheckpoint(_, _) => GitOperationKind.InstallCheckpoint
    case CommitMetadata(_) => GitOperationKind.CommitMetadata
    case BlobContent(_) => GitOperationKind.BlobContent
    case CheckPendingIndex(_) => GitOperationKind.CheckPendingIndex
    case PendingDiff(_) => GitOperationKind.PendingDiff
    case PendingPaths(_) => GitOperationKind.PendingPaths
    case StageSelected(_) => GitOperationKind.StageSelected
    case StageSelectedAll(_) => GitOperationKind.StageSelectedAll
    case CommitSelected(_) => GitOperationKind.CommitSelected
  }

  private[cncf] def selectionIdOption: Option[GitSelectionId] = this match {
    case StageSelected(id) => Some(id)
    case StageSelectedAll(id) => Some(id)
    case CommitSelected(id) => Some(id)
    case _ => None
  }
}

final case class GitRemoteTip(branch: GitBranchName, tip: GitObjectId)
final case class GitHistoryCounts(localAhead: Long, remoteAhead: Long)
final case class GitCommitMetadata(commit: GitObjectId, tree: GitObjectId, parents: Vector[GitObjectId])
final case class GitStatusEntry(indexCode: Char, worktreeCode: Char, path: GitPath, renamedFrom: Option[GitPath]) {
  def unmerged: Boolean = GitOutputParser.unmergedPairs.contains(s"$indexCode$worktreeCode")
}
enum GitIndexStage {
  case Merged, Base, Local, Remote
}
final case class GitIndexEntry(mode: String, blob: GitObjectId, stage: GitIndexStage, path: GitPath)
final case class GitBlobContent(bytes: Vector[Byte], text: Option[String])
final case class GitDiff(text: String, isBinary: Boolean)
final case class GitIndexCheck(findingKeys: Set[String]) {
  def isClean: Boolean = findingKeys.isEmpty
}
object GitIndexCheck {
  /** Pure legacy Receipt decoding; the caller retains completion and capture-bound admission. */
  def parseStoredC(exitCode: Int, stdout: String, stderr: String): Consequence[GitIndexCheck] =
    parseText(exitCode, stdout, stderr) match {
      case Right(value) => Consequence.success(value)
      case Left(_) => Consequence.argumentFormatError("gitIndexCheck", "consistent Git index-check receipt", "invalid")
    }

  private[cncf] def parseText(exitCode: Int, stdout: String, stderr: String): Either[String, GitIndexCheck] = {
    if (!_valid_text(stdout) || !_valid_text(stderr)) Left("Invalid Git index-check text")
    else if (exitCode == 0)
      Either.cond(stdout.isEmpty && stderr.isEmpty, GitIndexCheck(Set.empty), "Unexpected clean Git index-check output")
    else if (exitCode == 2) {
      val keys = stdout.split("\n", -1).filter(line => line.nonEmpty && !line.startsWith("+")).toSet
      Either.cond(stderr.isEmpty && keys.nonEmpty, GitIndexCheck(keys), "Missing or contradictory Git index-check findings")
    } else Left("Unsupported Git index-check exit")
  }

  private def _valid_text(text: String): Boolean =
    text != null && !text.contains('\u0000') && StandardCharsets.UTF_8.newEncoder().canEncode(text)
}

enum GitExecutionOutcome {
  case Succeeded
  case Failed(exitCode: Int)
  case Incomplete(termination: ProcessExecutionTermination)
  case InvalidOutput(reason: String)
  case ConflictObservationRequired
}

final case class GitExecutionResult[A](
  target: GitTarget,
  operation: GitOperation[A],
  outcome: GitExecutionOutcome,
  value: Option[A],
  processResult: ProcessExecutionResult
) {
  def valueC: Consequence[A] = (outcome, value) match {
    case (GitExecutionOutcome.Succeeded, Some(result)) => Consequence.success(result)
    case _ => Consequence.argumentPolicyViolation("git", "git.execution.result", "complete successful typed Git value", "unavailable")
  }
}

private[cncf] final case class GitCommitIdentity(name: String, email: String)

private[cncf] final case class GitProgramMetadata(
  binding: WorkspaceBinding,
  kind: GitOperationKind,
  selectionId: Option[GitSelectionId],
  localBranch: GitBranchName,
  remote: String,
  remoteBranch: GitBranchName,
  endpoint: String,
  commitIdentity: Option[GitCommitIdentity],
  selectedPath: Option[GitPath],
  commitMessage: Option[String]
) {
  def trackingRef: String = s"refs/remotes/$remote/${remoteBranch.value}"
}

private[cncf] object GitProgramDefinition {
  val checkpointMessage = "sm-workflow full-state checkpoint"
  val fixedEnvironment = Map("GIT_TERMINAL_PROMPT" -> "0", "GIT_CONFIG_NOSYSTEM" -> "1",
    "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")
  val prefix = Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
    "-c", "core.fsmonitor=false", "-c", "credential.helper=", "-c", "gc.auto=0", "-c", "maintenance.auto=false")

  def createC(
    binding: WorkspaceBinding,
    kind: GitOperationKind,
    capability: ProcessCapabilityId,
    safeProgramIdentity: String,
    gitExecutable: Path,
    localBranch: GitBranchName,
    remote: String,
    remoteBranch: GitBranchName,
    endpoint: String,
    commitIdentity: Option[GitCommitIdentity],
    selectionId: Option[GitSelectionId],
    selectedPath: Option[GitPath],
    commitMessage: Option[String],
    limits: ProcessExecutionLimits,
    environment: Map[String, String]
  ): Consequence[ProcessProgramDefinition] = for {
    _ <- if (binding != null && kind != null) Consequence.unit else invalidC
    _ <- if (safeProgramIdentity != null && safeProgramIdentity.matches("[a-z][a-z0-9-]{0,63}") &&
      capability != null && capability.value.matches("[a-z][a-z0-9-]{0,63}")) Consequence.unit else invalidC
    _ <- binding.workingDirectoryC
    _ <- _validate_host_c(kind, localBranch, remote, remoteBranch, endpoint, commitIdentity, selectionId, selectedPath, commitMessage)
    executable <- _executable_c(gitExecutable)
    _ <- if (environment != null && environment.keySet.subsetOf(Set("PATH", "SSH_AUTH_SOCK", "TMPDIR"))) Consequence.unit else invalidC
    metadata = GitProgramMetadata(binding, kind, selectionId, localBranch, remote, remoteBranch, endpoint,
      commitIdentity, selectedPath, commitMessage)
    slots = _slots(metadata)
    boundary = slots.indexWhere { case _: ProcessArgumentSlot.LowerHex => true; case _ => false }
    fixed = if (boundary < 0) slots.map { case ProcessArgumentSlot.Literal(text) => text; case _ => "" }
      else slots.take(boundary).map { case ProcessArgumentSlot.Literal(text) => text; case _ => "" }
    dynamic = if (boundary < 0) Vector.empty else slots.drop(boundary)
    definition <- ProcessProgramDefinition.fromGitRuntimeC(capability, safeProgramIdentity, executable.toString,
      fixed, ProcessArgumentPolicy(fixed, admission = ProcessArgumentAdmission.Template,
        permittedArgumentTemplates = Set(ProcessArgumentTemplate(dynamic))), limits, environment ++ fixedEnvironment, metadata)
  } yield definition

  private def _validate_host_c(kind: GitOperationKind, localbranch: GitBranchName, remote: String,
    remotebranch: GitBranchName, endpoint: String, identity: Option[GitCommitIdentity],
    selection: Option[GitSelectionId], path: Option[GitPath], message: Option[String]): Consequence[Unit] = for {
    _ <- if (localbranch != null && remotebranch != null) Consequence.unit else invalidC
    _ <- GitBranchName.parseC(localbranch.value)
    _ <- GitBranchName.parseC(remotebranch.value)
    _ <- GitBranchName.parseC(remote)
    _ <- if (!remote.contains("/") && endpoint != null && endpoint.nonEmpty && !endpoint.startsWith("-") &&
      !endpoint.exists(_.isControl)) Consequence.unit else invalidC
    _ <- if (identity != null && selection != null && path != null && message != null &&
      identity.isDefined == kind.requiresIdentity && selection.isDefined == kind.selected &&
      path.isDefined == (kind == GitOperationKind.StageSelected || kind == GitOperationKind.StageSelectedAll) &&
      message.isDefined == (kind == GitOperationKind.CommitSelected)) Consequence.unit else invalidC
    _ <- identity.fold(Consequence.unit)(x => if (x != null && _identity_text(x.name) && _identity_text(x.email)) Consequence.unit else invalidC)
    _ <- selection.fold(Consequence.unit)(x => if (x == null) invalidC else GitSelectionId.parseC(x.value).map(_ => ()))
    _ <- path.fold(Consequence.unit)(x => if (x == null) invalidC else GitPath.parseC(x.value).map(_ => ()))
    _ <- message.fold(Consequence.unit)(x => if (x != null && !x.contains('\u0000') && x.getBytes(StandardCharsets.UTF_8).length <= 4096 &&
      x.nonEmpty) Consequence.unit else invalidC)
  } yield ()

  private def _identity_text(text: String): Boolean = text != null && text.trim.nonEmpty && !text.exists(_.isControl)

  private def _executable_c(path: Path): Consequence[Path] = try {
    if (path == null || !path.isAbsolute || path.toString.exists(_.isControl)) invalidC
    else {
      val real = path.toRealPath()
      if (Files.isRegularFile(real) && Files.isExecutable(real)) Consequence.success(real) else invalidC
    }
  } catch { case NonFatal(_) => invalidC }

  private def _slots(metadata: GitProgramMetadata): Vector[ProcessArgumentSlot] = {
    import ProcessArgumentSlot.{Literal, LowerHex}
    def _l_(texts: String*): Vector[ProcessArgumentSlot] = texts.toVector.map(Literal.apply)
    val oid = LowerHex(Set(40, 64))
    val identity = metadata.commitIdentity.toVector.flatMap(x => _l_("-c", s"user.name=${x.name.trim}",
      "-c", s"user.email=${x.email.trim}", "-c", "user.useConfigOnly=true"))
    val command = metadata.kind match {
      case GitOperationKind.Branch => _l_("symbolic-ref", "--quiet", "--short", "HEAD")
      case GitOperationKind.Root => _l_("rev-parse", "--show-toplevel")
      case GitOperationKind.RemoteTip => _l_("ls-remote", "--exit-code", "--heads", "--", metadata.endpoint, s"refs/heads/${metadata.remoteBranch.value}")
      case GitOperationKind.Head => _l_("rev-parse", "--verify", "HEAD^{commit}")
      case GitOperationKind.Tree => _l_("rev-parse", "--verify", "HEAD^{tree}")
      case GitOperationKind.Status => _l_("status", "--porcelain=v1", "-z", "--untracked-files=all")
      case GitOperationKind.Shallow => _l_("rev-parse", "--is-shallow-repository")
      case GitOperationKind.Replacements => _l_("replace", "--list")
      case GitOperationKind.MergeHead => _l_("rev-parse", "--verify", "--quiet", "MERGE_HEAD")
      case GitOperationKind.TrackingTip => _l_("rev-parse", "--verify", "--quiet", s"${metadata.trackingRef}^{commit}")
      case GitOperationKind.Relation => _l_("rev-list", "--left-right", "--count", s"HEAD...${metadata.trackingRef}")
      case GitOperationKind.MergeBase => _l_("merge-base", "--all", "HEAD", metadata.trackingRef)
      case GitOperationKind.StageFullState => _l_("add", "--all", "--", ".")
      case GitOperationKind.IndexTree => _l_("write-tree")
      case GitOperationKind.UnmergedIndex => _l_("ls-files", "--unmerged", "-z")
      case GitOperationKind.Fetch => _l_("fetch", "--no-tags", "--no-write-fetch-head", "--no-recurse-submodules",
        "--no-auto-maintenance", "--no-write-commit-graph", "--no-prune", "--refmap=", "--", metadata.endpoint,
        s"refs/heads/${metadata.remoteBranch.value}:${metadata.trackingRef}")
      case GitOperationKind.FastForward => _l_("merge", "--ff-only", "--no-edit", "--no-autostash", "--no-verify", "--no-gpg-sign", "--") :+ oid
      case GitOperationKind.PendingMerge => identity ++ _l_("merge", "--no-ff", "--no-commit", "--no-edit", "--no-autostash", "--no-verify", "--no-gpg-sign", "--") :+ oid
      case GitOperationKind.CommitPending => identity ++ _l_("commit", "--no-verify", "--no-gpg-sign", "--no-edit")
      case GitOperationKind.Push => _l_("push", "--porcelain", "--no-verify", "--no-follow-tags", "--recurse-submodules=no", "--", metadata.endpoint) :+
        LowerHex(Set(40, 64), suffix = s":refs/heads/${metadata.remoteBranch.value}")
      case GitOperationKind.CreateCheckpoint => identity ++ _l_("commit-tree", "--no-gpg-sign", "-m", checkpointMessage) ++ Vector(oid, Literal("-p"), oid)
      case GitOperationKind.InstallCheckpoint => _l_("update-ref", "--no-deref", "--create-reflog", "-m", checkpointMessage,
        s"refs/heads/${metadata.localBranch.value}") ++ Vector(oid, oid)
      case GitOperationKind.CommitMetadata => _l_("show", "--no-patch", "--no-show-signature", "--format=%H%n%T%n%P") ++ Vector(oid, Literal("--"))
      case GitOperationKind.BlobContent => _l_("cat-file", "blob") :+ oid
      case GitOperationKind.CheckPendingIndex => _l_("diff", "--cached", "--no-ext-diff", "--no-textconv", "--check") ++ Vector(oid, Literal("--"))
      case GitOperationKind.PendingDiff => _l_("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-color", "--no-renames") ++ Vector(oid, Literal("--"))
      case GitOperationKind.PendingPaths => _l_("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-only", "-z") ++ Vector(oid, Literal("--"))
      case GitOperationKind.StageSelected => _l_("--literal-pathspecs", "add", "--", metadata.selectedPath.get.value)
      case GitOperationKind.StageSelectedAll => _l_("--literal-pathspecs", "add", "--all", "--", metadata.selectedPath.get.value)
      case GitOperationKind.CommitSelected => identity ++ _l_("commit", "--no-verify", "--no-gpg-sign", "-m", metadata.commitMessage.get)
    }
    prefix.map(Literal.apply) ++ command
  }

  private[cncf] def typedArgumentsC(operation: GitOperation[?], metadata: GitProgramMetadata): Consequence[Vector[String]] = {
    def _oid_(value: GitObjectId): Consequence[String] =
      if (value == null) invalidC else GitObjectId.parseC(value.value).map(_.value)
    operation match {
      case GitOperation.FastForward(tip) => _oid_(tip).map(Vector(_))
      case GitOperation.PendingMerge(tip) => _oid_(tip).map(Vector(_))
      case GitOperation.Push(tip, branch) => for {
        oid <- _oid_(tip)
        _ <- if (branch != null) GitBranchName.parseC(branch.value).map(_ => ()) else invalidC
        _ <- if (branch == metadata.remoteBranch) Consequence.unit else invalidC
      } yield Vector(s"$oid:refs/heads/${metadata.remoteBranch.value}")
      case GitOperation.CreateCheckpoint(tree, parent) => for { t <- _oid_(tree); p <- _oid_(parent) } yield Vector(t, "-p", p)
      case GitOperation.InstallCheckpoint(commit, parent) => for { c <- _oid_(commit); p <- _oid_(parent) } yield Vector(c, p)
      case GitOperation.CommitMetadata(commit) => _oid_(commit).map(x => Vector(x, "--"))
      case GitOperation.BlobContent(blob) => _oid_(blob).map(Vector(_))
      case GitOperation.CheckPendingIndex(parent) => _oid_(parent).map(x => Vector(x, "--"))
      case GitOperation.PendingDiff(parent) => _oid_(parent).map(x => Vector(x, "--"))
      case GitOperation.PendingPaths(parent) => _oid_(parent).map(x => Vector(x, "--"))
      case _ => Consequence.success(Vector.empty)
    }
  }

  private[cncf] def invalidC[A]: Consequence[A] =
    Consequence.argumentPolicyViolation("git", "git.execution.fixed-intent", "valid closed typed Git declaration", "invalid")
}

private[cncf] object GitExecutionAdmission {
  def resolveC[A](context: ExecutionContext, target: GitTarget, operation: GitOperation[A]): Consequence[ResolvedProcessExecution] = for {
    _ <- if (target != null && operation != null) Consequence.unit else GitProgramDefinition.invalidC
    _ <- WorkspaceId.parseC(target.workspaceId.value)
    _ <- operation.selectionIdOption.fold(Consequence.unit)(x => if (x == null) GitProgramDefinition.invalidC else GitSelectionId.parseC(x.value).map(_ => ()))
    workspace <- context.workspaceExecutionAdmissionOption.map(Consequence.success).getOrElse(
      Consequence.serviceUnavailable("Workspace Execution admission is not configured"))
    binding <- workspace.admitC(context.cncfCore.scope, target.workspaceId, operation.kind.permission)
    admission <- context.cncfCore.scope.processExecutionAdmissionOption.map(Consequence.success).getOrElse(
      Consequence.serviceUnavailable("Process Execution admission is not configured"))
    execution <- admission.admitGitForScopeC(context.cncfCore.scope, binding, operation)
  } yield execution

  def validateGenericExecutionC(execution: ResolvedProcessExecution): Consequence[Unit] =
    if (execution.definition._git_metadata.isEmpty) Consequence.unit else
      Consequence.argumentPolicyViolation("git", "git.execution.typed-intent", "typed UnitOfWork Git intent", "generic ProcessExec")

  def validateRequestC(request: ProcessExecutionRequest): Consequence[Unit] =
    if (request.workingDirectory.isEmpty && request.input == ProcessExecutionInput.Empty && request.inputFiles.isEmpty &&
      request.resourceTrees.isEmpty && request.outputs.isEmpty && request.requestedLimits == ProcessExecutionLimits.empty)
      Consequence.unit
    else GitProgramDefinition.invalidC

  def decode[A](target: GitTarget, operation: GitOperation[A], execution: ResolvedProcessExecution,
    result: ProcessExecutionResult): GitExecutionResult[A] = {
    val metadata = execution.definition._git_metadata.get
    val decoded: (GitExecutionOutcome, Option[A]) = result.termination match {
      case ProcessExecutionTermination.Exited(code) =>
        if (operation.kind == GitOperationKind.CheckPendingIndex && (code == 0 || code == 2))
          GitOutputParser.parseIndexCheck(result.stdout, result.stderr, code) match {
            case Right(value) => GitExecutionOutcome.Succeeded -> Some(value.asInstanceOf[A])
            case Left(reason) => GitExecutionOutcome.InvalidOutput(reason) -> None
          }
        else if (code == 1 && operation.kind == GitOperationKind.PendingMerge)
          GitExecutionOutcome.ConflictObservationRequired -> None
        else if (code != 0 && !(code == 1 && Set(GitOperationKind.MergeHead, GitOperationKind.TrackingTip, GitOperationKind.MergeBase).contains(operation.kind)))
          GitExecutionOutcome.Failed(code) -> None
        else GitOutputParser.parse(operation, metadata, result.stdout, code) match {
          case Right(value) => GitExecutionOutcome.Succeeded -> Some(value)
          case Left(reason) => GitExecutionOutcome.InvalidOutput(reason) -> None
        }
      case termination => GitExecutionOutcome.Incomplete(termination) -> None
    }
    GitExecutionResult(target, operation, decoded._1, decoded._2, result)
  }
}

private[cncf] object GitOutputParser {
  val unmergedPairs = Set("DD", "AU", "UD", "UA", "DU", "AA", "UU")

  def utf8(bytes: Vector[Byte]): Option[String] = try {
    Some(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toArray)).toString)
  } catch { case NonFatal(_) => None }

  def parse[A](operation: GitOperation[A], metadata: GitProgramMetadata, capture: ProcessExecutionCapture,
    exitCode: Int): Either[String, A] = {
    val complete = !capture.truncated && capture.byteCount == capture.content.size.toLong
    val result: Either[String, Any] = if (operation.kind == GitOperationKind.CheckPendingIndex)
        parseIndexCheck(capture, ProcessExecutionCapture(Vector.empty, 0L, false), exitCode)
      else if (!complete) Left("Incomplete Git stdout capture")
      else if (operation.kind == GitOperationKind.BlobContent && exitCode == 0) {
        val text = utf8(capture.content).filterNot(_.contains('\u0000'))
        Right(GitBlobContent(capture.content, text))
      } else utf8(capture.content).toRight("Invalid UTF-8 Git stdout").flatMap { text =>
        if (exitCode == 1) Either.cond(text.isEmpty, None, "Unexpected optional Git output")
        else _parse_text(operation.kind, metadata, text)
      }
    result.map(_.asInstanceOf[A])
  }

  def parseIndexCheck(stdout: ProcessExecutionCapture, stderr: ProcessExecutionCapture,
    exitCode: Int): Either[String, GitIndexCheck] = for {
    _ <- Either.cond(!stdout.truncated && stdout.byteCount == stdout.content.size.toLong &&
      !stderr.truncated && stderr.byteCount == stderr.content.size.toLong, (), "Incomplete Git index-check capture")
    out <- utf8(stdout.content).toRight("Invalid UTF-8 Git index-check stdout")
    err <- utf8(stderr.content).toRight("Invalid UTF-8 Git index-check stderr")
    value <- GitIndexCheck.parseText(exitCode, out, err)
  } yield value

  private def _oid(text: String): Either[String, GitObjectId] = GitObjectId.parseC(text).toOption.toRight("Invalid Git object ID")
  private def _path(text: String): Either[String, GitPath] = GitPath.parseC(text).toOption.toRight("Invalid Git filename")
  private def _line(text: String): Either[String, String] =
    Either.cond(text.endsWith("\n") && !text.dropRight(1).contains('\n') && !text.contains('\r') && !text.contains('\u0000'),
      text.dropRight(1), "Expected one Git output line")
  private def _lines(text: String): Either[String, Vector[String]] =
    if (text.isEmpty) Right(Vector.empty)
    else if (text.endsWith("\n")) Right(text.dropRight(1).split("\n", -1).toVector)
    else Left("Missing Git line terminator")
  private def _nul_fields(text: String): Either[String, Vector[String]] =
    if (text.isEmpty) Right(Vector.empty)
    else if (text.endsWith("\u0000")) Right(text.dropRight(1).split("\u0000", -1).toVector)
    else Left("Missing Git NUL terminator")
  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty))((prior, next) => for { p <- prior; n <- next } yield p :+ n)

  private def _parse_text(kind: GitOperationKind, metadata: GitProgramMetadata, text: String): Either[String, Any] = kind match {
    case GitOperationKind.Branch => _line(text).flatMap(x => GitBranchName.parseC(x).toOption.map(_.value).toRight("Invalid Git branch"))
    case GitOperationKind.Root => for {
      _ <- Either.cond(text.endsWith("\n"), (), "Missing Git root terminator")
      root = text.dropRight(1)
      admitted <- metadata.binding.workingDirectoryC.toOption.toRight("Unavailable Git workspace")
      _ <- try Either.cond(root.nonEmpty && Paths.get(root).isAbsolute && Paths.get(root) == admitted, (), "Git root mismatch")
        catch { case NonFatal(_) => Left("Invalid Git root") }
    } yield root
    case GitOperationKind.Head | GitOperationKind.Tree | GitOperationKind.IndexTree | GitOperationKind.CreateCheckpoint => _line(text).flatMap(_oid)
    case GitOperationKind.RemoteTip => for {
      line <- _line(text)
      fields = line.split("\t", -1)
      _ <- Either.cond(fields.length == 2 && fields(1) == s"refs/heads/${metadata.remoteBranch.value}", (), "Unexpected Git remote reference")
      oid <- _oid(fields(0))
    } yield GitRemoteTip(metadata.remoteBranch, oid)
    case GitOperationKind.Shallow => _line(text).flatMap {
      case "true" => Right(true)
      case "false" => Right(false)
      case _ => Left("Invalid shallow observation")
    }
    case GitOperationKind.Replacements => _lines(text).flatMap(xs => _sequence(xs.map(_oid)))
    case GitOperationKind.MergeHead | GitOperationKind.TrackingTip | GitOperationKind.MergeBase => _line(text).flatMap(_oid).map(Some(_))
    case GitOperationKind.Relation => for {
      line <- _line(text)
      parts = line.split("\t", -1)
      counts <- try {
        if (parts.length == 2 && parts.forall(_.matches("[0-9]+"))) Right(GitHistoryCounts(parts(0).toLong, parts(1).toLong))
        else Left("Invalid history counts")
      } catch { case NonFatal(_) => Left("History count overflow") }
    } yield counts
    case GitOperationKind.Status => _status(text)
    case GitOperationKind.UnmergedIndex => _index(text)
    case GitOperationKind.CommitMetadata => for {
      lines <- _lines(text)
      _ <- Either.cond(lines.size == 3, (), "Invalid commit metadata")
      commit <- _oid(lines(0))
      tree <- _oid(lines(1))
      parents <- if (lines(2).isEmpty) Right(Vector.empty) else _sequence(lines(2).split(" ", -1).toVector.map(_oid))
    } yield GitCommitMetadata(commit, tree, parents)
    case GitOperationKind.PendingPaths => for {
      fields <- _nul_fields(text)
      paths <- _sequence(fields.map(_path))
      _ <- Either.cond(paths.distinct.size == paths.size, (), "Duplicate changed Git filename")
    } yield paths
    case GitOperationKind.PendingDiff => Right(GitDiff(text, text.linesIterator.exists(x => x.startsWith("Binary files ") || x == "GIT binary patch")))
    case GitOperationKind.BlobContent => Left("Unexpected blob decoder")
    case _ => Right(())
  }

  private def _status(text: String): Either[String, Vector[GitStatusEntry]] = _nul_fields(text).flatMap { fields =>
    @scala.annotation.tailrec
    def _loop_(remaining: Vector[String], entries: Vector[GitStatusEntry]): Either[String, Vector[GitStatusEntry]] =
      if (remaining.isEmpty) Right(entries)
      else {
        val header = remaining.head
        if (header.length < 4 || header(2) != ' ') Left("Invalid porcelain status header")
        else {
          val pair = header.take(2)
          val valid = unmergedPairs.contains(pair) || pair == "??" || pair == "!!" || pair == "D " ||
            ("MTARC".contains(pair(0)) && " MTD".contains(pair(1))) ||
            (pair(0) == ' ' && "AMTDRC".contains(pair(1)))
          val renamed = pair.exists(c => c == 'R' || c == 'C')
          val entry = for {
            _ <- Either.cond(valid && (!renamed || remaining.size >= 2), (), "Invalid porcelain status pair")
            path <- _path(header.drop(3))
            source <- if (renamed) _path(remaining(1)).map(Some(_)) else Right(None)
          } yield GitStatusEntry(pair(0), pair(1), path, source)
          entry match {
            case Left(error) => Left(error)
            case Right(value) => _loop_(remaining.drop(if (renamed) 2 else 1), entries :+ value)
          }
        }
      }
    _loop_(fields, Vector.empty)
  }

  private def _index(text: String): Either[String, Vector[GitIndexEntry]] = for {
    fields <- _nul_fields(text)
    entries <- _sequence(fields.map { row =>
      val tab = row.indexOf('\t')
      if (tab < 0) Left("Missing unmerged index separator")
      else {
        val header = row.take(tab).split(" ", -1)
        for {
          _ <- Either.cond(header.length == 3 && header(0).matches("[0-7]{6}") && Set("1", "2", "3").contains(header(2)), (), "Invalid unmerged index header")
          blob <- _oid(header(1))
          path <- _path(row.drop(tab + 1))
          stage = header(2) match { case "1" => GitIndexStage.Base; case "2" => GitIndexStage.Local; case _ => GitIndexStage.Remote }
        } yield GitIndexEntry(header(0), blob, stage, path)
      }
    })
    _ <- Either.cond(entries.map(x => x.path -> x.stage).distinct.size == entries.size, (), "Duplicate unmerged index stage")
  } yield entries
}
