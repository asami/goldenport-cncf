package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import java.nio.file.Path
import org.goldenport.cncf.workspace.*

/** Immutable host configuration. Parsing does not install a driver or grant caller authority. */
/*
 * @since   Oct.  7, 2026
 * @version Oct. 20, 2026
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

private[cncf] final class RuntimeProcessExecutionPlan private (
  val programs: Vector[ProcessProgramDefinition],
  val grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
  val admission: ProcessExecutionAdmission,
  val schemaVersion: String,
  val workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration],
  val workspaceGrants: Vector[WorkspaceGrant],
  val sbtDeclarations: Vector[SbtRuntimeDeclaration],
  val gitDeclarations: Vector[GitRuntimeDeclaration],
  val workspaceAdmissionOption: Option[WorkspaceExecutionAdmission]
)

private[cncf] object RuntimeProcessExecutionPlan {
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
      RuntimeProcessExecutionPlanCodec.SCHEMA, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None)
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

  private def _create_semantic_c(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
    workspacedeclarations: Vector[WorkspaceRuntimeDeclaration],
    workspacegrants: Vector[WorkspaceGrant],
    sbtdeclarations: Vector[SbtRuntimeDeclaration],
    gitdeclarations: Vector[GitRuntimeDeclaration],
    schema: String
  ): Consequence[RuntimeProcessExecutionPlan] = for {
    // Generic restrictions apply to generic declarations alone, before the
    // semantic tags are combined with the existing process policy.
    _ <- createC(programs, grants.view.mapValues(_.filter(g => programs.exists(_.capability == g.capability))).toMap)
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
    workspace <- WorkspaceExecutionAdmission.createC(workspacedeclarations.map(x => x.identity -> x.root), workspacegrants)
    sbttagged <- sbtdeclarations.foldLeft(Consequence.success(Vector.empty[ProcessProgramDefinition])) { (prior, declaration) => for {
      values <- prior
      binding <- workspace.bindingC(declaration.target)
      definition <- SbtProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.sbtExecutable, declaration.limits, declaration.environment, declaration.testClasses)
    } yield values :+ definition }
    gittagged <- gitdeclarations.foldLeft(Consequence.success(Vector.empty[ProcessProgramDefinition])) { (prior, declaration) => for {
      values <- prior
      binding <- workspace.bindingC(declaration.target)
      definition <- GitProgramDefinition.createC(binding, declaration.operation, declaration.capability,
        declaration.identity, declaration.gitExecutable, declaration.localBranch, declaration.remote,
        declaration.remoteBranch, declaration.endpoint, declaration.commitIdentity, declaration.selectionId,
        declaration.selectedPath, declaration.commitMessage, declaration.limits, declaration.environment, declaration.selectedPaths)
    } yield values :+ definition }
    tagged = sbttagged ++ gittagged
    policy <- ProcessExecutionPolicy.createC(programs ++ tagged)
    _ <- if (grants.values.flatten.forall(g => (programs ++ tagged).exists(_.capability == g.capability)))
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
    admission <- ProcessExecutionAdmission.forComponentsC(policy, grants)
  } yield new RuntimeProcessExecutionPlan(programs, grants, admission,
    schema, workspacedeclarations, workspacegrants, sbtdeclarations, gitdeclarations, Some(workspace))
}
