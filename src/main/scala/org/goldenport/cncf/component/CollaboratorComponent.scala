package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.collaborator.Collaborator

/*
 * @since   Feb.  1, 2026
 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class CollaboratorComponent() extends Component()
    with CollaboratorComponent.Core.Holder {
  private var _collaborator_core: Option[CollaboratorComponent.Core] = None
  def collaboratorCore = _collaborator_core getOrElse Consequence.RAISE.UninitializedState

  def initialize(params: CollaboratorComponentInit): CollaboratorComponent = {
    _collaborator_core = Some(params.core)
    this
  }

  // TODO: promote to a stable wiring API once Collaborator is driven by CNCF core.
  def setCollaborator(collaborator: Collaborator): Unit = {
    _collaborator_core = Some(CollaboratorComponent.Core(collaborator))
  }

  inline def collaborator: Collaborator = collaboratorSlot.fold(
    Consequence.RAISE.UninitializedState(_),
    identity
  )
}

object CollaboratorComponent {
  case class Core(
    collaboratorSlot: Either[Conclusion, Collaborator]
  )
  object Core {
    trait Holder {
      def collaboratorCore: Core

      def collaboratorSlot = collaboratorCore.collaboratorSlot
    }

    def apply(p: Collaborator): Core = Core(Right(p))
  }
}

final case class CollaboratorComponentInit(
  core: CollaboratorComponent.Core
)
