package org.goldenport.cncf.backend.collaborator

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.collaborator.api

/*
 * @since   Jan. 30, 2026
 *  version Feb.  5, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class CollaboratorRepositorySpace(
  repositories: Vector[CollaboratorRepository] = Vector.empty,
  private[collaborator] val _repository_directories: Vector[Path] = Vector.empty
) {

  def discover(): Seq[CollaboratorRepository.CollaboratorEntry] =
    repositories.flatMap(_.discover())
}

object CollaboratorRepositorySpace {
  val empty = CollaboratorRepositorySpace()

  /** Compatibility entry point for callers outside the typed runtime path. */
  def create(configuration: ResolvedConfiguration): CollaboratorRepositorySpace = {
    val dirs = _resolve_repository_dirs(configuration)
    _create(dirs)
  }

  /** Receives only normalized bootstrap paths from the runtime boundary. */
  def create(paths: Vector[Path]): CollaboratorRepositorySpace =
    _create(Option(paths).getOrElse(Vector.empty))

  private def _create(dirs: Vector[Path]): CollaboratorRepositorySpace = {
    val apiurl = _collaborator_api_url
    val repos =
      dirs.filter(dir => Files.isDirectory(dir)).map(dir =>
        new CollaboratorRepository.CollaboratorDirRepository(dir, apiurl)
      )
    CollaboratorRepositorySpace(repos, dirs)
  }

  private def _resolve_repository_dirs(
    configuration: ResolvedConfiguration
  ): Vector[Path] =
    _config_collaborator_dirs(configuration).getOrElse(Vector(_default_collaborator_dir()))

  private def _config_collaborator_dirs(
    configuration: ResolvedConfiguration
  ): Option[Vector[Path]] =
    _get_string(configuration, "textus.collaborator.repositories", "cncf.collaborator.repositories") match {
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

  private def _get_string(
    configuration: ResolvedConfiguration,
    primary: String,
    compatibility: String
  ): Consequence[Option[String]] =
    configuration.get[String](primary) match {
      case Consequence.Success(Some(value)) => Consequence.success(Some(value))
      case Consequence.Success(None) => configuration.get[String](compatibility)
      case failure => failure
    }

  private def _default_collaborator_dir(): Path =
    Paths.get("").toAbsolutePath.normalize.resolve("collaborator.d")

  private def _collaborator_api_url: Option[URL] =
    Option(classOf[api.Collaborator].getProtectionDomain)
      .flatMap(pd => Option(pd.getCodeSource))
      .map(_.getLocation)
}
