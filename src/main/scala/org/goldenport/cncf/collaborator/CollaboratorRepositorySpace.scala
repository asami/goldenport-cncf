package org.goldenport.cncf.collaborator

import org.goldenport.configuration.ResolvedConfiguration

/*
 * @since   Jan. 30, 2026
 * @version Jan. 30, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorRepositorySpace(
  repositories: Vector[CollaboratorRepository] = Vector.empty
) {
}

object CollaboratorRepositorySpace {
  val empty = CollaboratorRepositorySpace()

  def create(c: ResolvedConfiguration): CollaboratorRepositorySpace = {
    val repos = Vector.empty // TODO
    CollaboratorRepositorySpace(repos)
  }
}
