package org.goldenport.cncf.repository

import java.net.{HttpURLConnection, URI}
import java.nio.file.{FileAlreadyExistsException, FileSystemException, Files, Path, StandardCopyOption}
import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.cncf.component.identity.{ComponentId, ComponentIdentityResult, ComponentReleaseCoordinate}

/*
 * Direct canonical CAR repository and cache resolver.
 *
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CanonicalCarRepositoryResolver {
  private val _connect_timeout_ms = 3000
  private val _read_timeout_ms = 10000

  def resolve(qualifiedComponentId: String, release: String, repositories: Seq[String], cacheRoot: Path): Either[String, Path] =
    for {
      componentid <- _shared(ComponentId.parse(qualifiedComponentId))
      coordinate <- _shared(ComponentReleaseCoordinate.create(componentid, release))
      roots <- _repositories(repositories)
      cache <- _cache_root(cacheRoot)
      resolved <- _resolve(coordinate, roots, cache)
    } yield resolved

  private def _resolve(coordinate: ComponentReleaseCoordinate, roots: Vector[RepositoryRoot], cacheroot: Path): Either[String, Path] = {
    val destination = cacheroot.resolve(coordinate.carCacheRelativePath()).normalize()
    if (Files.isRegularFile(destination))
      Right(destination)
    else
      roots.iterator.map(_resolve_root(_, coordinate, destination)).collectFirst { case Some(path) => path }.toRight(
        s"component.repository.car.not-found dependency=${coordinate.dependencyKey()} searched=${roots.map(_.source).mkString(", ")}"
      )
  }

  private def _resolve_root(root: RepositoryRoot, coordinate: ComponentReleaseCoordinate, destination: Path): Option[Path] =
    root match {
      case RepositoryRoot.Local(_, path) =>
        val candidate = path.resolve(coordinate.carRepositoryRelativePath()).normalize()
        Option.when(Files.isRegularFile(candidate))(candidate)
      case RepositoryRoot.Http(base) =>
        if (_is_snapshot(coordinate.release())) None
        else _download(base, coordinate, destination)
    }

  private def _download(base: String, coordinate: ComponentReleaseCoordinate, destination: Path): Option[Path] = {
    if (Files.isRegularFile(destination))
      Some(destination)
    else {
      var temporary: Option[Path] = None
      var httpconnection: Option[HttpURLConnection] = None
      try {
        val parent = Option(destination.getParent).getOrElse(throw new IllegalArgumentException("cache destination has no parent"))
        Files.createDirectories(parent)
        if (Files.isRegularFile(destination))
          return Some(destination)
        val filename = coordinate.carFilename()
        val path = Files.createTempFile(parent, s".$filename.", ".tmp")
        temporary = Some(path)
        val connection = new URI(s"${base.stripSuffix("/")}/${coordinate.carRepositoryRelativePath()}").toURL.openConnection()
        connection.setConnectTimeout(_connect_timeout_ms)
        connection.setReadTimeout(_read_timeout_ms)
        connection match {
          case http: HttpURLConnection =>
            httpconnection = Some(http)
            val status = http.getResponseCode
            if (status < 200 || status >= 300)
              throw new IllegalStateException(s"HTTP status $status")
          case _ => ()
        }
        val input = connection.getInputStream
        try Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
        finally input.close()
        _move_without_replacement(path, destination) match {
          case true =>
            temporary = None
            Option.when(Files.isRegularFile(destination))(destination)
          case false =>
            Option.when(Files.isRegularFile(destination))(destination)
        }
      } catch {
        case NonFatal(_) => None
      } finally {
        temporary.foreach(_delete_if_exists_best_effort)
        httpconnection.foreach(_.disconnect())
      }
    }
  }

  private def _move_without_replacement(temporary: Path, destination: Path): Boolean =
    try {
      Files.createLink(destination, temporary)
      Files.delete(temporary)
      true
    } catch {
      case _: FileAlreadyExistsException => false
      case _: UnsupportedOperationException => _move_without_replacement_fallback(temporary, destination)
      case _: FileSystemException => _move_without_replacement_fallback(temporary, destination)
    }

  private def _move_without_replacement_fallback(temporary: Path, destination: Path): Boolean =
    try {
      Files.move(temporary, destination)
      true
    } catch {
      case _: FileAlreadyExistsException => false
    }

  private def _delete_if_exists_best_effort(path: Path): Unit =
    try Files.deleteIfExists(path)
    catch {
      case NonFatal(_) => ()
    }

  private def _repositories(repositories: Seq[String]): Either[String, Vector[RepositoryRoot]] =
    if (repositories == null)
      Left("component.repository.repositories.required: repositories are required")
    else
      repositories.toVector.zipWithIndex.foldLeft(Right(Vector.empty): Either[String, Vector[RepositoryRoot]]) {
        case (z, (value, index)) =>
          for {
            roots <- z
            root <- _repository(value, index)
          } yield roots :+ root
      }

  private def _repository(value: String, index: Int): Either[String, RepositoryRoot] =
    if (value == null)
      Left(s"component.repository.repository.required index=$index: repository entry is required")
    else {
      val normalized = value.trim
      if (normalized.isEmpty)
        Left(s"component.repository.repository.blank index=$index: repository entry must not be blank")
      else if (normalized.startsWith("http://") || normalized.startsWith("https://"))
        Either.cond(_valid_http_uri(normalized), RepositoryRoot.Http(normalized), s"component.repository.repository.invalid index=$index: invalid repository root: $value")
      else if (normalized.startsWith("file:"))
        try Right(RepositoryRoot.Local(normalized, Path.of(new URI(normalized))))
        catch {
          case NonFatal(_) => Left(s"component.repository.repository.invalid index=$index: invalid repository root: $value")
        }
      else if (_has_uri_scheme(normalized))
        Left(s"component.repository.repository.invalid index=$index: invalid repository root: $value")
      else
        try Right(RepositoryRoot.Local(normalized, Path.of(normalized)))
        catch {
          case NonFatal(_) => Left(s"component.repository.repository.invalid index=$index: invalid repository root: $value")
        }
    }

  private def _cache_root(cacheroot: Path): Either[String, Path] =
    Option(cacheroot).toRight("component.repository.cache-root.required: cache root is required")

  private def _valid_http_uri(value: String): Boolean =
    try {
      val uri = new URI(value)
      val port = uri.getPort
      val authority = Option(uri.getRawAuthority).getOrElse("")
      val endpoint = authority.drop(authority.lastIndexOf('@') + 1)
      val explicitport =
        if (endpoint.startsWith("[")) endpoint.indexOf(']') < endpoint.length - 1
        else endpoint.contains(':')
      (uri.getScheme == "http" || uri.getScheme == "https") &&
        uri.getHost != null &&
        uri.getRawQuery == null &&
        uri.getRawFragment == null &&
        (!explicitport || (port >= 0 && port <= 65535))
    } catch {
      case NonFatal(_) => false
    }

  private def _has_uri_scheme(value: String): Boolean =
    value.matches("[A-Za-z][A-Za-z0-9+.-]*:.*")

  private def _is_snapshot(release: String): Boolean =
    release.toUpperCase(Locale.ROOT).contains("SNAPSHOT")

  private def _shared[A](result: ComponentIdentityResult[A]): Either[String, A] =
    if (result.isSuccess()) Right(result.value().get())
    else {
      val error = result.error().get()
      Left(s"${error.code()}: ${error.message()}")
    }

  private sealed trait RepositoryRoot {
    def source: String
  }

  private object RepositoryRoot {
    final case class Local(source: String, path: Path) extends RepositoryRoot
    final case class Http(source: String) extends RepositoryRoot
  }
}
