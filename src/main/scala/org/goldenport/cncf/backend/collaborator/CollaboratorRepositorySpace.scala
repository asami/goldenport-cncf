package org.goldenport.cncf.backend.collaborator

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.api

/*
 * @since   Jan. 30, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorRepositorySpace(
  repositories: Vector[CollaboratorRepository] = Vector.empty
) {

  def discover(): Seq[CollaboratorRepository.CollaboratorEntry] =
    repositories.flatMap(_.discover())
}

object CollaboratorRepositorySpace {
  val empty = CollaboratorRepositorySpace()

  def create(c: ResolvedConfiguration): CollaboratorRepositorySpace = {
    val dirs = _resolve_repository_dirs(c)
    val apiUrl = _collaborator_api_url
    val repos =
      dirs.filter(dir => Files.isDirectory(dir)).map(dir =>
        new CollaboratorRepository.CollaboratorDirRepository(dir, apiUrl)
      )
    CollaboratorRepositorySpace(repos)
  }

  private def _resolve_repository_dirs(
    configuration: ResolvedConfiguration
  ): Vector[Path] =
    _config_collaborator_dirs(configuration).getOrElse(Vector(_default_collaborator_dir()))

  private def _config_collaborator_dirs(
    configuration: ResolvedConfiguration
  ): Option[Vector[Path]] =
    configuration.get[String]("cncf.collaborator.repositories") match {
      case Consequence.Success(Some(value)) =>
        val dirs =
          value
            .split(",")
            .map(_.trim)
            .filter(_.nonEmpty)
            .map(Paths.get(_).toAbsolutePath.normalize)
            .toVector
        if (dirs.nonEmpty) Some(dirs) else None
      case _ => None
    }

  private def _default_collaborator_dir(): Path =
    Paths.get("").toAbsolutePath.normalize.resolve("collaborator.d")

  private def _collaborator_api_url: Option[URL] =
    Option(classOf[api.Collaborator].getProtectionDomain)
      .flatMap(pd => Option(pd.getCodeSource))
      .map(_.getLocation)
}
