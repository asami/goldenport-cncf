package org.goldenport.cncf.protocol.spec

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.*
import org.goldenport.cncf.action.*
import org.goldenport.cncf.component.{CommandParameterMappingRule, ShellCommandComponent}
import org.goldenport.cncf.protocol.spec.ShellCommandSpecification
import org.goldenport.process.ExternalCommand
import org.goldenport.cncf.protocol.spec.ShellCommandSpecification

/*
 * @since   Feb.  5, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ShellCommandOperationDefinition() extends OperationDefinition() {
  def shellSpecification: ShellCommandSpecification

  protected final def mapping_Rule: CommandParameterMappingRule =
    shellSpecification.mappingRule
    
  def createOperationRequest(req: Request): Consequence[Action] = {
    Consequence.success(ShellCommand(req, this, mapping_Rule))
  }
}

case class ShellCommand(
  request: Request,
  operation: ShellCommandOperationDefinition,
  rule: CommandParameterMappingRule
) extends Command() {
  def createCall(core: ActionCall.Core): ShellCommandCall = {
    ShellCommandCall(core, operation, rule)
  }
}

case class ShellCommandCall(
  core: ActionCall.Core,
  operation: ShellCommandOperationDefinition,
  rule: CommandParameterMappingRule
) extends ActionCall() {
  def execute(): Consequence[OperationResponse] =
    core.component match {
      case Some(shell: ShellCommandComponent) =>
        val spec = operation.shellSpecification
        val command = spec.baseCommand ++
          Vector(rule.commandName(operation)) ++
          rule.arguments(operation)
        val external = ExternalCommand(
          command = command,
          workDir = spec.workDirHint,
          env = spec.envHint
        )
        shell.commandExecutor.execute(external).map { result =>
          OperationResponse.Scalar(result.stdout)
        }
      case _ =>
        Consequence.failure("ShellCommandComponent is required for shell execution")
    }
}
