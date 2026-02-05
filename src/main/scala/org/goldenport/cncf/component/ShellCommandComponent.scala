package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.process.ExternalCommandExecutor

/*
 * @since   Feb.  5, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
trait CommandParameterMappingRule {
  def commandName(operation: OperationDefinition): String

  def arguments(operation: OperationDefinition): Vector[String]
}
object CommandParameterMappingRule {
  object Default extends CommandParameterMappingRule {
    override def commandName(operation: OperationDefinition): String =
      ??? // operation.name

    override def arguments(operation: OperationDefinition): Vector[String] = {
      // val args = context.arguments.map(_.toString)
      // val switches = context.switches.flatMap { switch =>
      //   Vector("-" + switch.name, switch.value.toString)
      // }
      // args ++ switches
      ???
    }
  }
}

abstract class ShellCommandComponent(
  protected val mapping: CommandParameterMappingRule =
    CommandParameterMappingRule.Default
) extends Component {

  protected def executor: ExternalCommandExecutor

  protected def baseCommand(operation: OperationDefinition): Vector[String]

  def commandExecutor: ExternalCommandExecutor = executor

  protected final def executeCommand(
    operation: OperationDefinition
  ): Consequence[Any] = {
    val command =
      ???
      // baseCommand(operation) ++
      //   Vector(mapping.commandName(operation)) ++
      //   mapping.arguments(operation, context)

    val external =
      org.goldenport.process.ExternalCommand(
        command = command,
        workDir = ??? // context.workDirOption
      )

    executor.execute(external).map(result => result)
  }
}
