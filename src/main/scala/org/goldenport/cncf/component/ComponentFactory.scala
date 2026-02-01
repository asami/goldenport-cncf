package org.goldenport.cncf.component

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.CollaboratorFactory
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.component.repository.ComponentSource
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Jan. 30, 2026
 *  version Jan. 31, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory(
  private val _component_repository_space: ComponentRepositorySpace = ComponentRepositorySpace(),
  private val _collaborators: CollaboratorFactory = CollaboratorFactory.empty
) {
  def discover(): Vector[Component] = {
    val cs = _component_repository_space.discover()
    // TODO collaborators
    cs
  }
}

object ComponentFactory {
  // NEW
  def create(
    subsystem: Subsystem,
    collaborators: CollaboratorFactory,
    cwd: Path,
    c: ResolvedConfiguration
  ): ComponentFactory = {
    val space = _build_component_repository_space(subsystem, cwd, c)
    new ComponentFactory(space, collaborators)
  }

  private def _build_component_repository_space(
    subsystem: Subsystem,
    cwd: Path,
    c: ResolvedConfiguration
  ): ComponentRepositorySpace =
    ComponentRepositorySpace.create(subsystem, cwd, c)

  // CncfRuntime
  def create(
    subsystem: Subsystem,
    c: ResolvedConfiguration,
    collaborators: CollaboratorFactory,
    repositorySpecs: Vector[ComponentRepository.Specification]
  ): ComponentFactory = {
    val space = ComponentRepositorySpace.create(subsystem, c, repositorySpecs)
    new ComponentFactory(space, collaborators)
  }

  def build(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: String
  ): Consequence[Vector[ComponentSource]] = ???
}
