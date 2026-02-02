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

  private val _entries: Vector[CollaboratorRepository.CollaboratorEntry] =
    _repository_space.discover().toVector

  def entries: Vector[CollaboratorRepository.CollaboratorEntry] = _entries

  def resolve(name: String): Option[CollaboratorRepository.CollaboratorEntry] =
    _entries.find(_.name == name)
}

object CollaboratorFactory {
  val empty = CollaboratorFactory()

  def create(c: ResolvedConfiguration): CollaboratorFactory = {
    val space = CollaboratorRepositorySpace.create(c)
    CollaboratorFactory(space)
  }
}
