package org.goldenport.cncf.resource

import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}
import java.util.Locale
import scala.util.Try
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 *  version Jul. 16, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TextusUrnReference private[resource] (
  reference: ResourceReference.Urn,
  namespace: String,
  resourceId: String
)

object TextusUrnReference {
  private val _namespace_pattern = "^[a-z][a-z0-9-]{0,31}$".r
  private val _resource_id_pattern = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$".r

  def parseC(reference: ResourceReference.Urn): Consequence[TextusUrnReference] =
    if (reference.nid != "textus")
      Consequence.argumentFormatError("reference", "urn:textus:<namespace>:<resource-id>", reference.print)
    else {
      reference.nss.split(":", 2).toVector match {
        case Vector(rawnamespace, resourceid) =>
          val namespace = rawnamespace.toLowerCase(Locale.ROOT)
          (namespace, resourceid) match {
            case (_namespace_pattern(), _resource_id_pattern()) if _is_safe_resource_id(resourceid) =>
              Consequence.success(TextusUrnReference(reference, namespace, resourceid))
            case _ =>
              Consequence.argumentFormatError(
                "reference",
                "urn:textus:<namespace>:<resource-id> with a safe namespace and resource id",
                reference.print
              )
          }
        case _ =>
          Consequence.argumentFormatError("reference", "urn:textus:<namespace>:<resource-id>", reference.print)
      }
    }

  def resolveC(
    base: ResourceReference.Urn,
    relativepath: String
  ): Consequence[ResourceReference] =
    parseC(base).flatMap { reference =>
      val resourceid = s"${reference.resourceId}/$relativepath"
      ResourceReference.parseC(s"urn:textus:${reference.namespace}:$resourceid")
    }

  private def _is_safe_resource_id(value: String): Boolean =
    !value.split("/").exists(segment => segment.isEmpty || segment == "." || segment == "..")
}

final case class TextusUrnResourcePolicy(
  fileRoots: Map[String, Path] = Map.empty
) {
  lazy val normalizedFileRoots: Map[String, Path] =
    fileRoots.map { case (namespace, root) =>
      namespace.toLowerCase(Locale.ROOT) -> root.toAbsolutePath.normalize
    }

  def fileProviders: Vector[TextusUrnResourceProvider] =
    normalizedFileRoots.toVector.sortBy(_._1).map { case (namespace, root) =>
      new FileTextusUrnResourceProvider(namespace, root)
    }
}

object TextusUrnResourcePolicy {
  private val _namespace_pattern = "^[a-z][a-z0-9-]{0,31}$".r

  def fromValuesC(values: Vector[String]): Consequence[TextusUrnResourcePolicy] =
    values.foldLeft(Consequence.success(Map.empty[String, Path])) { (z, value) =>
      for {
        bindings <- z
        binding <- _binding_c(value)
        _ <- if (bindings.contains(binding._1))
          Consequence.argumentInvalid(
            "textus.resource.urn.textus.file-roots",
            "each namespace exactly once",
            binding._1
          )
        else
          Consequence.unit
      } yield bindings + binding
    }.map(TextusUrnResourcePolicy(_))

  private def _binding_c(value: String): Consequence[(String, Path)] =
    value.trim.split("=", 2).toVector match {
      case Vector(rawnamespace, rawroot) =>
        val namespace = rawnamespace.trim.toLowerCase(Locale.ROOT)
        Try(Path.of(rawroot.trim)).toOption match {
          case Some(root) if _namespace_pattern.matches(namespace) && root.isAbsolute =>
            Consequence.success(namespace -> root.normalize)
          case _ =>
            Consequence.argumentFormatError(
              "textus.resource.urn.textus.file-roots",
              "<namespace>=<absolute filesystem path>",
              value
            )
        }
      case _ =>
        Consequence.argumentFormatError(
          "textus.resource.urn.textus.file-roots",
          "<namespace>=<absolute filesystem path>",
          value
        )
    }
}

trait TextusUrnResourceProvider {
  def namespace: String
  def read(reference: TextusUrnReference, limits: ResourceReadLimits): Consequence[ResourceContent]
}

final class FileTextusUrnResourceProvider(
  val namespace: String,
  root: Path
) extends TextusUrnResourceProvider {
  private val _root = root.toAbsolutePath.normalize

  def read(reference: TextusUrnReference, limits: ResourceReadLimits): Consequence[ResourceContent] =
    if (reference.namespace != namespace)
      Consequence.resourceUnsupported("Textus URN namespace is not configured")
    else {
      val path = _root.resolve(reference.resourceId).normalize
      _authorized_path_c(path).flatMap { admitted =>
        _read_c(reference, admitted, limits)
      }
    }

  private def _authorized_path_c(path: Path): Consequence[Path] = {
    try {
      if (!path.startsWith(_root) || _has_symbolic_link(_root) || _has_symbolic_link(path))
        Consequence.resourceUnsupported("Textus URN resource path is not admitted")
      else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        Consequence.resourceNotFound("configured Textus URN resource is not available")
      else {
        val realroot = _root.toRealPath()
        val realpath = path.toRealPath()
        if (realpath.startsWith(realroot))
          Consequence.success(path)
        else
          Consequence.resourceUnsupported("Textus URN resource path is not admitted")
      }
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured Textus URN resource cannot be read")
    }
  }

  private def _has_symbolic_link(path: Path): Boolean = {
    Iterator.iterate(path)(_.getParent).takeWhile(_ != null).exists(Files.isSymbolicLink(_))
  }

  private def _read_c(
    reference: TextusUrnReference,
    path: Path,
    limits: ResourceReadLimits
  ): Consequence[ResourceContent] =
    try {
      ResourceReadAcquisition.readC(
        Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), limits
      ).map { bytes =>
        ResourceContent(reference.reference, bytes, Option(Files.probeContentType(path)))
      }
    } catch {
      case NonFatal(_) => Consequence.resourceInvalid("configured Textus URN resource cannot be read")
    }
}

private final class TextusUrnResourceAccess(
  providers: Vector[TextusUrnResourceProvider],
  limits: ResourceReadLimits
) extends ResourceAccess {
  private val _providers = providers.map { provider =>
    provider.namespace.toLowerCase(Locale.ROOT) -> provider
  }.toMap

  def read(reference: ResourceReference): Consequence[ResourceContent] =
    reference match {
      case urn: ResourceReference.Urn =>
        TextusUrnReference.parseC(urn) match {
          case Consequence.Success(textusurn) =>
            _providers.get(textusurn.namespace) match {
              case Some(provider) => provider.read(textusurn, limits)
              case None => Consequence.resourceUnsupported("Textus URN namespace is not configured")
            }
          case Consequence.Failure(_) =>
            Consequence.argumentInvalid("Textus URN reference requires a safe namespace and resource id")
        }
      case _ =>
        Consequence.resourceUnsupported("Textus URN resource access requires a URN reference")
    }

  override def providerMetadata(reference: ResourceReference): ResourceProviderMetadata =
    reference match {
      case urn: ResourceReference.Urn =>
        TextusUrnReference.parseC(urn).toOption.flatMap { textusurn =>
          _providers.get(textusurn.namespace).map { provider =>
            ResourceProviderMetadata("textus-urn", provider.namespace, configured = true)
          }
        }.getOrElse(ResourceProviderMetadata.unconfigured("textus"))
      case _ => ResourceProviderMetadata.unconfigured("urn")
    }
}
