package org.goldenport.cncf.component

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.{Collaborator, CollaboratorFactory}
import org.goldenport.cncf.collaborator.api
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
    val xs = cs.map(_initialize_special_component)
    xs
  }

  private def _initialize_special_component(p: Component): Component =
    p match {
      case m: CollaboratorComponent =>
        val entryOpt = _collaborators.resolve(m.core.name).orElse(_collaborators.entries.headOption)
        entryOpt match {
          case Some(entry) =>
            val collaboratorImpl = _wrapCollaborator(entry.collaborator)
            val init = CollaboratorComponentInit(CollaboratorComponent.Core(collaboratorImpl))
            m.initialize(init)
          case None =>
            m
        }
      case m => m
    }

  private def _wrapCollaborator(apiCollaborator: api.Collaborator): Collaborator = new Collaborator {
    private val delegate = Collaborator.Instance(Collaborator.Core(apiCollaborator))
    def execute(ctx: org.goldenport.cncf.context.ExecutionContext, request: org.goldenport.protocol.Request) =
      delegate.execute(ctx, request)
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
