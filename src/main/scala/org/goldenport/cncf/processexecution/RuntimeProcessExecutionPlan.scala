package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal
import org.goldenport.cncf.workspace.*

/** Immutable host configuration. Parsing does not install a driver or grant caller authority. */
/*
 * @since   Oct.  7, 2026
 * @version Oct. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final case class WorkspaceRuntimeDeclaration(identity: WorkspaceId, root: Path)

private[cncf] final case class SbtRuntimeDeclaration(
  target: WorkspaceId,
  operation: SbtOperation,
  capability: ProcessCapabilityId,
  identity: String,
  sbtExecutable: Path,
  limits: ProcessExecutionLimits,
  environment: Map[String, String],
  testClasses: Vector[String] = Vector.empty
)

private[cncf] final case class GitRuntimeDeclaration(
  target: WorkspaceId,
  operation: GitOperationKind,
  capability: ProcessCapabilityId,
  identity: String,
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
  environment: Map[String, String],
  selectedPaths: Vector[GitPath] = Vector.empty
)

private[cncf] final case class GitRepositoryRuntimeDeclaration(
  target: WorkspaceId,
  capability: ProcessCapabilityId,
  identity: String,
  gitExecutable: Option[Path],
  localBranch: GitBranchName,
  remote: String,
  remoteBranch: GitBranchName,
  endpoint: String,
  commitIdentity: GitCommitIdentity,
  limits: ProcessExecutionLimits,
  environment: Map[String, String]
)

private[cncf] final class RuntimeProcessExecutionPlan private (
  val programs: Vector[ProcessProgramDefinition],
  val grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
  val admission: ProcessExecutionAdmission,
  val schemaVersion: String,
  val workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration],
  val workspaceGrants: Vector[WorkspaceGrant],
  val sbtDeclarations: Vector[SbtRuntimeDeclaration],
  val gitDeclarations: Vector[GitRuntimeDeclaration],
  val gitRepositoryDeclarations: Vector[GitRepositoryRuntimeDeclaration],
  val workspaceAdmissionOption: Option[WorkspaceExecutionAdmission]
)

private[cncf] object RuntimeProcessExecutionPlan {
  // Deliberately closed: a future Git kind requires an explicit family contract update.
  private val _repository_kinds = Vector(
    GitOperationKind.Branch, GitOperationKind.Root, GitOperationKind.RemoteTip, GitOperationKind.Head,
    GitOperationKind.Tree, GitOperationKind.Status, GitOperationKind.Shallow, GitOperationKind.Replacements,
    GitOperationKind.MergeHead, GitOperationKind.TrackingTip, GitOperationKind.Relation, GitOperationKind.MergeBase,
    GitOperationKind.StageFullState, GitOperationKind.IndexTree, GitOperationKind.UnmergedIndex, GitOperationKind.Fetch,
    GitOperationKind.FastForward, GitOperationKind.PendingMerge, GitOperationKind.CommitPending, GitOperationKind.Push,
    GitOperationKind.CreateCheckpoint, GitOperationKind.InstallCheckpoint, GitOperationKind.CommitMetadata,
    GitOperationKind.BlobContent, GitOperationKind.CheckPendingIndex, GitOperationKind.PendingDiff,
    GitOperationKind.PendingPaths, GitOperationKind.TreeChanges)

  def createC(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]]
  ): Consequence[RuntimeProcessExecutionPlan] = {
    val capabilities = programs.map(_.capability).toSet
    for {
      _ <- programs.foldLeft(Consequence.unit) { (prior, definition) =>
        prior.flatMap(_ => ProcessExternalEffectPolicy.validateGenericDefinitionC(definition))
      }
      policy <- ProcessExecutionPolicy.createC(programs)
      _ <- if (programs.forall(p => p._sbt_metadata.isEmpty && p._git_metadata.isEmpty &&
          p.argumentPolicy.admission == ProcessArgumentAdmission.Template &&
          p.allowedInputFiles == p.allowedInputFilePaths.keySet && p.allowedResourceTrees.isEmpty) &&
          grants.values.flatten.forall(g => g.maximumLimits == ProcessExecutionLimits.empty && g.resourceTreeLimits.isEmpty))
        Consequence.unit
      else Consequence.configurationInvalid("Runtime process V1 requires complete templates and path declarations without resource-tree or grant-limit extensions")
      _ <- if (grants.values.flatten.forall(g => capabilities.contains(g.capability))) Consequence.unit
        else Consequence.configurationInvalid("Process grants require declared runtime programs")
      admission <- ProcessExecutionAdmission.forComponentsC(policy, grants)
    } yield new RuntimeProcessExecutionPlan(programs, grants, admission,
      RuntimeProcessExecutionPlanCodec.SCHEMA, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None)
  }

  def createV2C(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration],
    workspaceGrants: Vector[WorkspaceGrant],
    sbtDeclarations: Vector[SbtRuntimeDeclaration]
  ): Consequence[RuntimeProcessExecutionPlan] =
    _create_semantic_c(programs, grants, workspaceDeclarations, workspaceGrants, sbtDeclarations,
      Vector.empty, RuntimeProcessExecutionPlanCodec.SCHEMA_V2)

  def createV3C(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration],
    workspaceGrants: Vector[WorkspaceGrant],
    sbtDeclarations: Vector[SbtRuntimeDeclaration],
    gitDeclarations: Vector[GitRuntimeDeclaration]
  ): Consequence[RuntimeProcessExecutionPlan] =
    _create_semantic_c(programs, grants, workspaceDeclarations, workspaceGrants, sbtDeclarations,
      gitDeclarations, RuntimeProcessExecutionPlanCodec.SCHEMA_V3)

  def createV5C(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration],
    workspaceGrants: Vector[WorkspaceGrant],
    sbtDeclarations: Vector[SbtRuntimeDeclaration],
    gitDeclarations: Vector[GitRuntimeDeclaration],
    gitRepositoryDeclarations: Vector[GitRepositoryRuntimeDeclaration]
  ): Consequence[RuntimeProcessExecutionPlan] =
    _create_semantic_c(programs, grants, workspaceDeclarations, workspaceGrants, sbtDeclarations,
      gitDeclarations, RuntimeProcessExecutionPlanCodec.SCHEMA_V5, gitRepositoryDeclarations)

  private def _create_semantic_c(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    workspacedeclarations: Vector[WorkspaceRuntimeDeclaration],
    workspacegrants: Vector[WorkspaceGrant],
    sbtdeclarations: Vector[SbtRuntimeDeclaration],
    gitdeclarations: Vector[GitRuntimeDeclaration],
    schema: String,
    gitrepositories: Vector[GitRepositoryRuntimeDeclaration] = Vector.empty
  ): Consequence[RuntimeProcessExecutionPlan] = for {
    // Generic restrictions apply to generic declarations alone, before the
    // semantic tags are combined with the existing process policy.
    _ <- createC(programs, grants.view.mapValues(_.filter(g => programs.exists(_.capability == g.capability))).toMap)
    expanded <- _expand_repositories_c(programs, grants, sbtdeclarations, gitdeclarations, gitrepositories, workspacegrants)
    allgit = gitdeclarations ++ expanded
    effectivegrants = grants.map { case (owner, values) => owner -> values.flatMap { grant =>
      gitrepositories.find(_.capability == grant.capability) match {
        case Some(repository) => expanded.filter(_.target == repository.target).map(x => ProcessExecutionGrant(x.capability))
        case None => Vector(grant)
      }
    } }
    _ <- if (programs.forall(p => p._sbt_metadata.isEmpty && p._git_metadata.isEmpty) &&
        sbtdeclarations.map(x => x.target -> x.operation).distinct.size == sbtdeclarations.size &&
        gitdeclarations.map(x => (x.target, x.operation, x.selectionId)).distinct.size == gitdeclarations.size &&
        grants.keys.forall(owner => owner != null && ComponentId.parseC(owner.name).isSuccess) &&
        workspacedeclarations.forall(x => WorkspaceId.parseC(x.identity.value).isSuccess) &&
        workspacegrants.forall(g => g.componentId != null && ComponentId.parseC(g.componentId.name).isSuccess) &&
        grants.values.forall(gs => gs.map(_.capability).distinct.size == gs.size) &&
        grants.values.flatten.forall(g => g.maximumLimits == ProcessExecutionLimits.empty && g.resourceTreeLimits.isEmpty))
      Consequence.unit else Consequence.configurationInvalid("Invalid or duplicate runtime authority declaration")
    _ <- if (gitdeclarations.groupBy(_.target).values.forall { declarations =>
      declarations.count(_.operation == GitOperationKind.StageSelected) <= 128 &&
      declarations.count(_.operation == GitOperationKind.StageSelectedAll) <= 256 &&
      declarations.count(_.operation == GitOperationKind.CommitSelected) <= 64 &&
      declarations.count(_.operation == GitOperationKind.CheckSelectedIndex) <= 64 &&
      declarations.count(_.operation == GitOperationKind.SelectedIndexChanges) <= 64
    }) Consequence.unit else Consequence.configurationInvalid("Git selection declarations exceed the per-workspace bounds")
    _ <- if (gitdeclarations.filter(x => x.operation match {
      case GitOperationKind.CommitSelected | GitOperationKind.CheckSelectedIndex | GitOperationKind.SelectedIndexChanges => true
      case _ => false
    }).groupBy(x => x.target -> x.selectionId).values.forall(_.map(_.selectedPaths).distinct.size == 1))
      Consequence.unit else Consequence.configurationInvalid("Git selection declarations require identical ordered selected paths for each workspace and selection ID")
    workspace <- WorkspaceExecutionAdmission.createC(workspacedeclarations.map(x => x.identity -> x.root), workspacegrants)
    sbttagged <- sbtdeclarations.foldLeft(Consequence.success(Vector.empty[ProcessProgramDefinition])) { (prior, declaration) => for {
      values <- prior
      binding <- workspace.bindingC(declaration.target)
      definition <- SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment, declaration.testClasses)
    } yield values :+ definition }
    gittagged <- allgit.foldLeft(Consequence.success(Vector.empty[ProcessProgramDefinition])) { (prior, declaration) => for {
      values <- prior
      binding <- workspace.bindingC(declaration.target)
      definition <- GitProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.gitExecutable, declaration.localBranch, declaration.remote,
        declaration.remoteBranch, declaration.endpoint, declaration.commitIdentity, declaration.selectionId,
        declaration.selectedPath, declaration.commitMessage, declaration.limits, declaration.environment, declaration.selectedPaths)
    } yield values :+ definition }
    tagged = sbttagged ++ gittagged
    policy <- ProcessExecutionPolicy.createC(programs ++ tagged)
    _ <- if (effectivegrants.values.flatten.forall(g => (programs ++ tagged).exists(_.capability == g.capability)))
      Consequence.unit else Consequence.configurationInvalid("Process grants require declared runtime programs")
    _ <- if (grants.forall { case (owner, values) => values.forall { grant =>
      sbtdeclarations.filter(_.capability == grant.capability).forall { declaration =>
        workspacegrants.exists(g => g.componentId == owner && g.workspaceId == declaration.target &&
          g.permission == WorkspacePermission.Manage)
      } && gitdeclarations.filter(_.capability == grant.capability).forall { declaration =>
        workspacegrants.exists(g => g.componentId == owner && g.workspaceId == declaration.target &&
          (g.permission == WorkspacePermission.Manage || g.permission == declaration.operation.permission))
      }
    } }) Consequence.unit else Consequence.configurationInvalid("Build process grants require the same owner's Manage workspace grant")
    admission <- ProcessExecutionAdmission.forComponentsC(policy, effectivegrants)
  } yield new RuntimeProcessExecutionPlan(programs, grants, admission,
    schema, workspacedeclarations, workspacegrants, sbtdeclarations, gitdeclarations, gitrepositories, Some(workspace))

  private def _expand_repositories_c(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    sbtdeclarations: Vector[SbtRuntimeDeclaration],
    gitdeclarations: Vector[GitRuntimeDeclaration],
    repositories: Vector[GitRepositoryRuntimeDeclaration],
    workspacegrants: Vector[WorkspaceGrant]
  ): Consequence[Vector[GitRuntimeDeclaration]] = for {
    _ <- if (repositories.forall(x => x.capability != null &&
        x.capability.print.matches("[a-z][a-z0-9-]{0,31}") && x.identity != null &&
        x.identity.matches("[a-z][a-z0-9-]{0,31}") && x.commitIdentity != null) &&
        repositories.map(_.target).distinct.size == repositories.size &&
        !repositories.exists(x => gitdeclarations.exists(g => g.target == x.target && !g.operation.selected)))
      Consequence.unit else Consequence.configurationInvalid("Invalid, duplicate or overlapping Git repository declaration")
    expanded <- repositories.foldLeft(Consequence.success(Vector.empty[GitRuntimeDeclaration])) { (prior, repository) => for {
      previous <- prior
      executable <- _repository_executable_c(repository)
      expanded <- _repository_kinds.foldLeft(Consequence.success(previous)) { (acc, kind) => for {
        values <- acc
        capability <- ProcessCapabilityId.parseC(repository.capability.print + "-" + kind.wireName)
      } yield values :+ GitRuntimeDeclaration(repository.target, kind, capability,
        repository.identity + "-" + kind.wireName, executable._1, repository.localBranch,
        repository.remote, repository.remoteBranch, repository.endpoint,
        if (kind.requiresIdentity) Some(repository.commitIdentity) else None,
        None, None, None, repository.limits, executable._2) }
    } yield expanded }
    capabilities = programs.map(_.capability) ++ sbtdeclarations.map(_.capability) ++
      gitdeclarations.map(_.capability) ++ repositories.map(_.capability) ++ expanded.map(_.capability)
    identities = programs.map(_.safeProgramIdentity) ++ sbtdeclarations.map(_.identity) ++
      gitdeclarations.map(_.identity) ++ repositories.map(_.identity) ++ expanded.map(_.identity)
    publiccapabilities = (programs.map(_.capability) ++ sbtdeclarations.map(_.capability) ++
      gitdeclarations.map(_.capability) ++ repositories.map(_.capability)).toSet
    _ <- if (repositories.isEmpty || (capabilities.distinct.size == capabilities.size && identities.distinct.size == identities.size))
      Consequence.unit else Consequence.configurationInvalid("Git repository capability or identity collision")
    _ <- if (grants.values.flatten.forall(g => publiccapabilities.contains(g.capability))) Consequence.unit
      else Consequence.configurationInvalid("Process grants require declared public capabilities")
    _ <- if (grants.forall { case (owner, values) => values.forall { grant =>
      repositories.filter(_.capability == grant.capability).forall { repository =>
        workspacegrants.exists(g => g.componentId == owner && g.workspaceId == repository.target &&
          g.permission == WorkspacePermission.Manage)
      }
    } }) Consequence.unit else Consequence.configurationInvalid("Git repository grants require the same owner's Manage workspace grant")
  } yield expanded

  private def _repository_executable_c(
    repository: GitRepositoryRuntimeDeclaration
  ): Consequence[(Path, Map[String, String])] = repository.gitExecutable match {
    // Explicit overrides are validated by GitProgramDefinition, without PATH fallback.
    case Some(path) => Consequence.success(path -> repository.environment)
    case None => try {
      val path = Option(repository.environment).flatMap(_.get("PATH")).orElse(sys.env.get("PATH"))
      val directories = path.toVector.flatMap(_.split(java.io.File.pathSeparator, -1)).map(Paths.get(_))
      if (repository.environment == null || directories.isEmpty ||
          directories.exists(x => x.toString.isEmpty || !x.isAbsolute))
        Consequence.configurationInvalid("Git repository requires git on an absolute, nonempty PATH")
      else directories.iterator.map(_.resolve("git")).find(x => Files.isRegularFile(x) && Files.isExecutable(x)) match {
        case Some(executable) => Consequence.success(executable.toRealPath() -> (repository.environment + ("PATH" -> path.get)))
        case None => Consequence.configurationInvalid("Git repository executable git is unavailable on PATH")
      }
    } catch { case NonFatal(_) => Consequence.configurationInvalid("Git repository executable git is unavailable on a valid PATH") }
    case null => Consequence.configurationInvalid("Invalid Git repository executable override")
  }
}
