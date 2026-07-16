package org.goldenport.cncf.resource

import java.nio.file.{Files, Path}
import java.util.Locale
import scala.util.Try
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
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
  def read(reference: TextusUrnReference): Consequence[ResourceContent]
}

final class FileTextusUrnResourceProvider(
  val namespace: String,
  root: Path
) extends TextusUrnResourceProvider {
  private val _root = root.toAbsolutePath.normalize

  def read(reference: TextusUrnReference): Consequence[ResourceContent] =
    if (reference.namespace != namespace)
      Consequence.resourceUnsupported("Textus URN namespace is not configured")
    else {
      val path = _root.resolve(reference.resourceId).normalize
      if (!path.startsWith(_root))
        Consequence.resourceUnsupported("Textus URN resource is outside configured read-only root")
      else if (!Files.isRegularFile(path))
        Consequence.resourceNotFound("configured Textus URN resource is not available")
      else
        _authorized_path_c(path).flatMap(_read_c(reference, _))
    }

  private def _authorized_path_c(path: Path): Consequence[Path] =
    (Try(_root.toRealPath()).toOption, Try(path.toRealPath()).toOption) match {
      case (Some(root), Some(realpath)) if realpath.startsWith(root) =>
        Consequence.success(realpath)
      case _ =>
        Consequence.resourceUnsupported("Textus URN resource is outside configured read-only root")
    }

  private def _read_c(
    reference: TextusUrnReference,
    path: Path
  ): Consequence[ResourceContent] =
    try {
      val bytes = Files.readAllBytes(path).toVector
      val media = Option(Files.probeContentType(path))
      Consequence.success(ResourceContent(reference.reference, bytes, media))
    } catch {
      case NonFatal(_) =>
        Consequence.resourceInvalid("configured Textus URN resource cannot be read")
    }
}

private final class TextusUrnResourceAccess(
  providers: Vector[TextusUrnResourceProvider]
) extends ResourceAccess {
  private val _providers = providers.map { provider =>
    provider.namespace.toLowerCase(Locale.ROOT) -> provider
  }.toMap

  def read(reference: ResourceReference): Consequence[ResourceContent] =
    reference match {
      case urn: ResourceReference.Urn =>
        TextusUrnReference.parseC(urn).flatMap { textusurn =>
          _providers.get(textusurn.namespace) match {
            case Some(provider) => provider.read(textusurn)
            case None => Consequence.resourceUnsupported("Textus URN namespace is not configured")
          }
        }
      case _ =>
        Consequence.resourceUnsupported("Textus URN resource access requires a URN reference")
    }
}
