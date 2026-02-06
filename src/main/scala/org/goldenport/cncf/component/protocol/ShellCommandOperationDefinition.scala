package org.goldenport.cncf.component.protocol

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.*
import org.goldenport.process.ShellCommand
import org.goldenport.cncf.action.*
import org.goldenport.cncf.component.{CommandParameterMappingRule, ShellCommandComponent}
import org.goldenport.cncf.component.protocol.ShellCommandSpecification

/*
 * @since   Feb.  5, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ShellCommandOperationDefinition() extends ComponentOperationDefinition() {
  def shellSpecification: ShellCommandSpecification =
    ShellCommandSpecification(name)

  protected final def mapping_Rule: CommandParameterMappingRule =
    shellSpecification.mappingRule
    
  def createOperationRequest(req: Request): Consequence[Action] = {
    Consequence.success(ShellCommandCommand(req, this, mapping_Rule))
  }
}

final case class ShellCommandSpecification(
  baseCommand: Vector[String],
  mappingRule: CommandParameterMappingRule = CommandParameterMappingRule.Default,
  workDirHint: Option[Path] = None,
  envHint: Map[String, String] = Map.empty
)
object ShellCommandSpecification {
  def apply(name: String): ShellCommandSpecification =
    ShellCommandSpecification(Vector(name))
}

case class ShellCommandCommand(
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
        val params = rule.parameters(operation, request)
        val command = spec.baseCommand ++
          Vector(rule.commandName(operation)) ++ params.parameters
        val external = ShellCommand(
          command = command,
          workDir = spec.workDirHint,
          env = spec.envHint,
          directive = params.directive
        )
        shell.commandExecutor.execute(external).map { result =>
          OperationResponse.Scalar(result.stdout.asStringUnsafe())
        }
      case _ =>
        Consequence.failure("ShellCommandComponent is required for shell execution")
    }
}
