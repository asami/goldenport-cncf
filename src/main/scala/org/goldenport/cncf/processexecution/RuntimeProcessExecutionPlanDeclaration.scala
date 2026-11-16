package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}

/*
 * @since   Nov. 16, 2026
 * @version Nov. 16, 2026
 */
/** Parsed host data. It contains neither workspace bindings nor process admission. */
private[cncf] final case class RuntimeProcessExecutionPlanDeclaration(
  programs: Vector[ProcessProgramDefinition],
  grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
  schemaVersion: String,
  workspaceDeclarations: Vector[WorkspaceRuntimeDeclaration] = Vector.empty,
  workspaceGrants: Vector[WorkspaceGrant] = Vector.empty,
  sbtDeclarations: Vector[SbtRuntimeDeclaration] = Vector.empty,
  gitDeclarations: Vector[GitRuntimeDeclaration] = Vector.empty,
  gitRepositoryDeclarations: Vector[GitRepositoryRuntimeDeclaration] = Vector.empty,
  validationCheckDeclarations: Vector[ValidationCheckRuntimeDeclaration] = Vector.empty,
  candidatePolicies: Vector[SessionCandidateWorkspacePolicy] = Vector.empty,
  workspaceInitializations: Vector[WorkspaceRootInitialization] = Vector.empty,
  private[cncf] val _io_default_omissions: Map[(String, Int), Set[String]] = Map.empty
) {
  /** The caller has already checked selection and trusted provenance. */
  def admitC(initialize: Boolean): Consequence[RuntimeProcessExecutionPlan] =
    if (schemaVersion == RuntimeProcessExecutionPlanCodec.SCHEMA)
      RuntimeProcessExecutionPlan.createC(programs, grants).map(_._with_io_default_omissions(_io_default_omissions))
    else for {
      prepared <- RuntimeProcessExecutionPlan.prepareSemanticC(this)
      _ <- if (schemaVersion == RuntimeProcessExecutionPlanCodec.SCHEMA_V8)
        WorkspaceRootInitialization.prepareC(workspaceDeclarations.map(x => x.identity -> x.root),
          workspaceGrants, candidatePolicies, workspaceInitializations).flatMap { roots =>
          if (initialize) roots.initializeC else roots.verifyExistingC
        }
        else Consequence.unit
      plan <- RuntimeProcessExecutionPlan.bindPreparedC(prepared)
    } yield plan._with_io_default_omissions(_io_default_omissions)
}

private[cncf] object RuntimeProcessExecutionPlanDeclaration {
  def fromPlan(plan: RuntimeProcessExecutionPlan): RuntimeProcessExecutionPlanDeclaration =
    RuntimeProcessExecutionPlanDeclaration(plan.programs, plan.grants, plan.schemaVersion,
      plan.workspaceDeclarations, plan.workspaceGrants, plan.sbtDeclarations, plan.gitDeclarations,
      plan.gitRepositoryDeclarations, plan.validationCheckDeclarations, plan.candidatePolicies,
      plan.workspaceInitializations, plan._io_default_omissions)
}

private[cncf] object RuntimeProcessExecutionPlanDeclarationCodec
    extends ConfigurationValueCodec[RuntimeProcessExecutionPlanDeclaration] {
  def decode(value: ConfigurationValue): Consequence[RuntimeProcessExecutionPlanDeclaration] =
    RuntimeProcessExecutionPlanCodec.decodeDeclarationC(value)
  def encode(value: RuntimeProcessExecutionPlanDeclaration): Consequence[ConfigurationValue] =
    RuntimeProcessExecutionPlanCodec.encodeDeclarationC(value)
  def resolveIoDefaultsC(value: RuntimeProcessExecutionPlanDeclaration,
    commonBytes: Long): Consequence[RuntimeProcessExecutionPlanDeclaration] =
    RuntimeProcessExecutionPlanCodec.resolveDeclarationIoDefaultsC(value, commonBytes)
}

private[cncf] final case class RuntimeSemanticPreparation(
  declaration: RuntimeProcessExecutionPlanDeclaration,
  effectivegrants: Map[ComponentId, Vector[ProcessExecutionGrant]],
  sbt: Vector[(WorkspaceId, PreparedSbtProgram)],
  git: Vector[(WorkspaceId, PreparedGitProgram)],
  checks: Vector[(WorkspaceId, PreparedValidationCheckProgram)]
)
