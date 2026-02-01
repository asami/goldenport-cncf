package org.goldenport.cncf.collaborator

import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jan. 30, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorFactory(
  private val _repository_space: CollaboratorRepositorySpace = CollaboratorRepositorySpace.empty
) {
}

object CollaboratorFactory {
  val empty = CollaboratorFactory()

  def create(c: ResolvedConfiguration): CollaboratorFactory = {
    val space = CollaboratorRepositorySpace.create(c)
    CollaboratorFactory(space)
  }
}
