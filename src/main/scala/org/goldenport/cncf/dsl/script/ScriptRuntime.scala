package org.goldenport.cncf.dsl.script

import org.goldenport.Consequence
import org.goldenport.protocol.*
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.operation.*
import org.goldenport.cncf.action.*
import org.goldenport.cncf.component.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.cli.CncfRuntime

/*
 * @since   Jan. 14, 2026
 * @version Jan. 22, 2026
 * @author  ASAMI, Tomoharu
 */
type Script = ScriptActionCall => Any

object ScriptRuntime {
  def run(args: Seq[String])(script: Script): Consequence[Response] =
    run(args.toArray)(script)

  def run(args: Array[String])(script: Script): Consequence[Response] = {
    val r = execute(args)(script)
    r match {
      case Consequence.Success(s) => Console.out.println(s.print)
      case Consequence.Failure(c) => Console.err.println(c.display)
    }
    r
  }

  def execute(arg: String, args: String*)(script: Script): Consequence[Response] =
    execute(arg +: args)(script)

  def execute(args: Seq[String])(script: Script): Consequence[Response] =
    execute(args.toArray)(script)

  def execute(args: Array[String])(script: Script): Consequence[Response] =
    CncfRuntime.executeScript(args, _build_components(script))

  private def _build_components(script: Script)(p: Subsystem): Vector[Component] = {
    val create = ComponentCreate(p, ComponentOrigin.Main)
    ScriptExecutionComponent.Factory(script).create(create)
  }

  // def run(
  //   body: Request => Any,
  //   args: Array[String]
  // ): Unit = {
  //   val req = _build_request(args)
  //   val result = body(req)
  //   _emit_result(result)
  // }

  // private def _build_request(args: Array[String]): Request = {
  //   val arguments =
  //     args.toList.map(a => Argument(a))

  //   Request(
  //     component = None,
  //     service = None,
  //     operation = "RUN",
  //     arguments = arguments,
  //     switches = Nil,
  //     properties = Nil
  //   )
  // }

  // private def _emit_result(result: Any): Unit = {
  //   result match {
  //     case null =>
  //       ()
  //     case v =>
  //       println(v.toString)
  //   }
  // }
}

class ScriptExecutionComponent() extends Component {
}

object ScriptExecutionComponent {
  case class ScriptOperationDefinition(
    specification: OperationDefinition.Specification,
    script: Script
  ) extends OperationDefinition {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ScriptAction(s"script_$name", req, script))
  }
  object ScriptOperationDefinition {
    // case object Factory extends OperationDefinition.Builder.OperationFactory {
    //   def createOperation(name: String, req: RequestDefinition, res: ResponseDefinition): OperationDefinition =
    //     ScriptOperationDefinition(
    //       OperationDefinition.Specification(name, req, res)
    //     )
    // }
    def apply(
      name: String,
      req: RequestDefinition,
      res: ResponseDefinition,
      script: Script
    ): OperationDefinition = ScriptOperationDefinition(
      OperationDefinition.Specification(name, req, res),
      script
    )
  }

  case class Factory(script: Script) extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] =
      Vector(ScriptExecutionComponent())

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      // val service = ServiceDefinition(
      //   "DEFAULT",
      //   "RUN",
      //   RequestDefinition.script,
      //   ResponseDefinition.script
      // )
      val service = ServiceDefinitionGroup.
        Builder().operation(
          "DEFAULT",
          ScriptOperationDefinition(
            "RUN",
            RequestDefinition.script,
            ResponseDefinition.script,
            script
          )
        ).build()
      Component.createScriptCore(service)
    }
  }
}

case class ScriptAction(
  override val name: String,
  request: Request,
  script: Script
) extends Command() {
  def createCall(core: ActionCall.Core): ActionCall =
    ScriptActionCall(core, script)
}

case class ScriptActionCall(
  core: ActionCall.Core,
  script: Script
) extends ActionCall {
  def execute(): Consequence[OperationResponse] = Consequence run {
    val r = script(this)
    Consequence(OperationResponse.create(r))
  }

  // val scriptAction: ScriptAction = action.asInstanceOf[ScriptAction]
  // def request: Request = scriptAction.request
  // def arguments: List[Argument] = request.arguments
  // def switches: List[Switch] = request.switches
  // def properties: List[Property] = request.properties
  // def args: List[String] = request.args
}
