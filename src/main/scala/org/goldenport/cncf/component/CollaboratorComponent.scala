package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.collaborator.Collaborator

/*
 * @since   Feb.  1, 2026
 * @version Feb.  1, 2026
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
}

object CollaboratorComponent {
  case class Core(
    collaborator: Collaborator
  )
  object Core {
    trait Holder {
      def collaboratorCore: Core

      def collaborator = collaboratorCore.collaborator
    }
  }
}

final case class CollaboratorComponentInit(
  core: CollaboratorComponent.Core
)
