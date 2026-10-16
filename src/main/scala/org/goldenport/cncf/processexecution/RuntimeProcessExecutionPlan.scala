package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId

/** Immutable host configuration. Parsing does not install a driver or grant caller authority. */
/*
 * @since Oct.  7, 2026
 * @version Oct.  7, 2026
 */
private[cncf] final class RuntimeProcessExecutionPlan private (
  val programs: Vector[ProcessProgramDefinition],
  val grants: Map[ComponentId, Vector[ProcessExecutionGrant]],
  val admission: ProcessExecutionAdmission
)

private[cncf] object RuntimeProcessExecutionPlan {
  def createC(
    programs: Vector[ProcessProgramDefinition],
    grants: Map[ComponentId, Vector[ProcessExecutionGrant]]
  ): Consequence[RuntimeProcessExecutionPlan] = {
    val capabilities = programs.map(_.capability).toSet
    for {
      policy <- ProcessExecutionPolicy.createC(programs)
      _ <- if (programs.forall(p => p.argumentPolicy.admission == ProcessArgumentAdmission.Template &&
          p.allowedInputFiles == p.allowedInputFilePaths.keySet && p.allowedResourceTrees.isEmpty) &&
          grants.values.flatten.forall(g => g.maximumLimits == ProcessExecutionLimits.empty && g.resourceTreeLimits.isEmpty))
        Consequence.unit
      else Consequence.configurationInvalid("Runtime process V1 requires complete templates and path declarations without resource-tree or grant-limit extensions")
      _ <- if (grants.values.flatten.forall(g => capabilities.contains(g.capability))) Consequence.unit
        else Consequence.configurationInvalid("Process grants require declared runtime programs")
      admission <- ProcessExecutionAdmission.forComponentsC(policy, grants)
    } yield new RuntimeProcessExecutionPlan(programs, grants, admission)
  }
}
