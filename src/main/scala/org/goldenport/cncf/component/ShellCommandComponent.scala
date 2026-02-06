package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.text.Presentable
import org.goldenport.protocol.*
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.process.ShellCommandExecutor
import org.goldenport.process.ShellCommand
import org.goldenport.bag.Bag

/*
 * @since   Feb.  5, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class ShellCommandComponent(
  protected val mapping: CommandParameterMappingRule =
    CommandParameterMappingRule.Default
) extends Component {

  protected def shell_Executor: ShellCommandExecutor

  protected def base_Command(operation: OperationDefinition): Vector[String]

  def commandExecutor: ShellCommandExecutor = shell_Executor

  // protected final def executeCommand(
  //   operation: OperationDefinition
  // ): Consequence[Any] = {
  //   val command =
  //     ???
  //     // baseCommand(operation) ++
  //     //   Vector(mapping.commandName(operation)) ++
  //     //   mapping.arguments(operation, context)

  //   val external =
  //     org.goldenport.process.ShellCommand(
  //       command = command,
  //       workDir = ??? // context.workDirOption
  //     )

  //   executor.execute(external).map(result => result)
  // }
}

trait CommandParameterMappingRule {
  def commandName(operation: OperationDefinition): String

  def parameters(
    operation: OperationDefinition,
    req: Request
  ): CommandParameterMappingRule.Parameters
}
object CommandParameterMappingRule {
  case class Parameters(
    parameters: Vector[String],
    directive: ShellCommand.Directive
  )

  object Default extends CommandParameterMappingRule {
    override def commandName(operation: OperationDefinition): String =
      operation.name

    override def parameters(
      operation: OperationDefinition,
      request: Request
    ): Parameters = {
      case class Z(
        params: Vector[String] = Vector.empty,
        directive: ShellCommand.Directive = ShellCommand.Directive.empty
      ) {
        def result = Parameters(params, directive)

        def addArguments(ps: Seq[Argument]) =
          ps.foldLeft(this)((z, x) => z._add_value(x.name, x.value))

        def addProperties(ps: Seq[Property]) =
          ps.foldLeft(this)((z, x) => z._add_prop(x.name, x.value))

        def addSwitches(ps: Seq[Switch]) =
          ps.foldLeft(this)((z, x) => z._add_switch(x.name, x.value))

        private def _add_value(name: String, v: Any) = {
          v match {
            case m: Bag => Consequence.failNotImplemented.RAISE
            case m => copy(params = params :+ _print_value(name, v))
          }
        }

        private def _add_prop(name: String, v: Any) = {
          v match {
            case m: Bag => Consequence.failNotImplemented.RAISE
            case m => copy(params = params :+ s"-$name" :+ _print_value(name, v))
          }
        }

        private def _add_switch(name: String, v: Any) = {
          v match {
            case false => this
            case m: Bag => Consequence.failNotImplemented.RAISE
            case m => copy(params = params :+ s"-$name")
          }
        }

        private def _print_value(name: String, v: Any): String = 
          operation.getParameter(name) match {
            case Some(s) => s.print(v)
            case None => Presentable.print(v)
          }
      }
      Z().
        addArguments(request.arguments).
        addProperties(request.properties).
        addSwitches(request.switches).
        result
    }
  }
}
