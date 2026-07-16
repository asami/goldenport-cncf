package org.goldenport.cncf.resource

import java.net.URI
import java.nio.file.{Files, Path}
import scala.util.{Try, Using}
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResourceUrlPolicy(
  fileRoots: Vector[Path] = Vector.empty,
  httpsHosts: Vector[String] = Vector.empty
) {
  lazy val normalizedFileRoots: Vector[Path] =
    fileRoots.map(_.toAbsolutePath.normalize).distinct

  lazy val normalizedHttpsHosts: Set[String] =
    httpsHosts.map(_.toLowerCase(java.util.Locale.ROOT)).toSet

  def permitsFile(path: Path): Boolean =
    normalizedFileRoots.exists(path.startsWith)

  def permitsHttpsHost(host: String): Boolean =
    normalizedHttpsHosts.contains(host.toLowerCase(java.util.Locale.ROOT))
}

object ResourceUrlPolicy {
  def fromValuesC(
    fileroots: Vector[String],
    httpshosts: Vector[String]
  ): Consequence[ResourceUrlPolicy] =
    for {
      roots <- _values_c(fileroots)(_file_root_c)
      hosts <- _values_c(httpshosts)(_https_host_c)
    } yield ResourceUrlPolicy(roots, hosts)

  private def _values_c[A](
    values: Vector[String]
  )(
    f: String => Consequence[A]
  ): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (z, x) =>
      for {
        xs <- z
        value <- f(x)
      } yield xs :+ value
    }

  private def _file_root_c(value: String): Consequence[Path] =
    Try(Path.of(value.trim)).toOption match {
      case Some(path) if path.isAbsolute =>
        Consequence.success(path.normalize)
      case _ =>
        Consequence.argumentFormatError(
          "textus.resource.url.file.roots",
          "an absolute filesystem path",
          value
        )
    }

  private def _https_host_c(value: String): Consequence[String] = {
    val host = value.trim.toLowerCase(java.util.Locale.ROOT)
    val uri = Try(URI.create(s"https://${host}")).toOption
    uri match {
      case Some(v) if host.nonEmpty && v.getHost == host && v.getPort == -1 && v.getPath.isEmpty =>
        Consequence.success(host)
      case _ =>
        Consequence.argumentFormatError(
          "textus.resource.url.https.hosts",
          "an HTTPS host without a scheme, path, or port",
          value
        )
    }
  }
}

trait UrlResourceProvider {
  def scheme: String
  def read(reference: ResourceReference.Url): Consequence[ResourceContent]
}

final class FileUrlResourceProvider(
  policy: ResourceUrlPolicy
) extends UrlResourceProvider {
  val scheme: String = "file"

  def read(reference: ResourceReference.Url): Consequence[ResourceContent] =
    _path_c(reference.uri).flatMap { path =>
      if (!policy.permitsFile(path))
        Consequence.resourceUnsupported("file resource is outside configured read-only roots")
      else if (!Files.isRegularFile(path))
        Consequence.resourceNotFound("configured file resource is not available")
      else
        _authorized_path_c(path).flatMap(_read_c(reference, _))
    }

  private def _path_c(uri: URI): Consequence[Path] =
    Try(Path.of(uri)).toOption match {
      case Some(path) => Consequence.success(path.toAbsolutePath.normalize)
      case None => Consequence.resourceUnsupported("file resource reference is not supported")
    }

  private def _authorized_path_c(path: Path): Consequence[Path] =
    Try(path.toRealPath()).toOption match {
      case Some(realpath) if _permits_real_path(realpath) =>
        Consequence.success(realpath)
      case _ =>
        Consequence.resourceUnsupported("file resource is outside configured read-only roots")
    }

  private def _permits_real_path(path: Path): Boolean =
    policy.normalizedFileRoots.flatMap(root => Try(root.toRealPath()).toOption)
      .exists(path.startsWith)

  private def _read_c(
    reference: ResourceReference.Url,
    path: Path
  ): Consequence[ResourceContent] =
    try {
      val bytes = Files.readAllBytes(path).toVector
      val media = Option(Files.probeContentType(path))
      Consequence.success(ResourceContent(reference, bytes, media))
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured file resource cannot be read")
    }
}

final class HttpsUrlResourceProvider(
  policy: ResourceUrlPolicy,
  httpdriver: org.goldenport.cncf.http.HttpDriver
) extends UrlResourceProvider {
  val scheme: String = "https"

  def read(reference: ResourceReference.Url): Consequence[ResourceContent] =
    _authorize_c(reference.uri).flatMap { _ =>
      _response_c(reference).flatMap { response =>
        if (response.isSuccess)
          _content_c(reference, response)
        else if (response.isNotFound)
          Consequence.resourceNotFound("configured HTTPS resource is not available")
        else
          Consequence.resourceInvalid(s"configured HTTPS resource returned HTTP ${response.code}")
      }
    }

  private def _authorize_c(uri: URI): Consequence[Unit] =
    Option(uri.getHost).filter(policy.permitsHttpsHost) match {
      case Some(_) if uri.getUserInfo == null => Consequence.unit
      case _ =>
        Consequence.resourceUnsupported("HTTPS resource host is not configured for read access")
    }

  private def _response_c(
    reference: ResourceReference.Url
  ): Consequence[org.goldenport.http.HttpResponse] =
    try {
      Consequence.success(httpdriver.get(
        reference.print,
        properties = Vector(org.goldenport.protocol.Property("http.follow-redirects", "false", None))
      ))
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured HTTPS resource cannot be read")
    }

  private def _content_c(
    reference: ResourceReference.Url,
    response: org.goldenport.http.HttpResponse
  ): Consequence[ResourceContent] =
    try {
      val bytes = Using.resource(response.bag.openInputStream())(_.readAllBytes()).toVector
      Consequence.success(ResourceContent(
        reference,
        bytes,
        mediaType = Some(response.mime.toString),
        declaredCharset = response.charset
      ))
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured HTTPS resource body cannot be read")
    }
}

private final class UrlResourceAccess(
  policy: ResourceUrlPolicy,
  providers: Vector[UrlResourceProvider]
) extends ResourceAccess {
  private val _providers = providers.map { provider =>
    provider.scheme.toLowerCase(java.util.Locale.ROOT) -> provider
  }.toMap

  def read(reference: ResourceReference): Consequence[ResourceContent] =
    reference match {
      case url: ResourceReference.Url =>
        _authorize_c(url).flatMap { _ =>
          _providers.get(url.scheme) match {
            case Some(provider) => provider.read(url)
            case None => _unconfigured_scheme(url.scheme)
          }
        }
      case _: ResourceReference.Urn =>
        Consequence.resourceUnsupported("URN resource providers are not configured")
    }

  private def _authorize_c(url: ResourceReference.Url): Consequence[Unit] =
    url.scheme match {
      case "file" if policy.normalizedFileRoots.nonEmpty => Consequence.unit
      case "https" if Option(url.uri.getHost).exists(policy.permitsHttpsHost) => Consequence.unit
      case _ => _unconfigured_scheme(url.scheme).map(_ => ())
    }

  private def _unconfigured_scheme(scheme: String): Consequence[ResourceContent] =
    Consequence.resourceUnsupported(s"${scheme} resource access is not configured")
}
