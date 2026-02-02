package org.goldenport.cncf.component.minicollaborator

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.spec.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.CollaboratorComponent
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.action.{Action, ActionCall, CollaboratorActionCall}
import org.goldenport.cncf.action.Query
import org.goldenport.cncf.collaborator.api.Collaborator

/*
 * @since   Jan. 30, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class MiniCollaboratorComponent() extends CollaboratorComponent {
  // private object MiniAction extends Action {
  //   override def createCall(core: ActionCall.Core): ActionCall =
  //     new CollaboratorActionCall("ping", Map.empty) {
  //       override def action: Action = MiniAction
  //     }
  // }

  // // Placeholder exposure of actions; real component wiring is omitted intentionally.
  // def pingAction(): ActionCall = MiniAction.createCall(???) // not wired)
}

object MiniCollaboratorComponent {
  val name = "mini_collaborator"
  val componentId = ComponentId(name)

  class Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] =
      Vector(MiniCollaboratorComponent())

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = spec_create(
      name,
      componentId,
      MainService
    )
  }
}

object MainService extends ServiceDefinition {
  val specification = ServiceDefinition.Specification.Builder("main").
    operation(
      PingOperation
    ).build()
  
  object PingOperation extends OperationDefinition {
    val specification = OperationDefinition.Specification.Builder("ping").
      build()

    override def createOperationRequest(
      req: Request
    ): Consequence[PingQuery] =
      Consequence.success(PingQuery(req))
  }
}

final case class PingQuery(
  request: Request
) extends Query() {
  override def createCall(core: ActionCall.Core): ActionCall =
    PingActionCall(core, ???, this)
}

final case class PingActionCall(
  core: ActionCall.Core,
  collaboratorCore: CollaboratorActionCall.Core,
  query: PingQuery,
) extends CollaboratorActionCall {
}
