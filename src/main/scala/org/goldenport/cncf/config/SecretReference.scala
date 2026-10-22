package org.goldenport.cncf.config

import java.net.URI
import java.nio.file.{Files, LinkOption, NoSuchFileException, NotDirectoryException, Path, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, PosixFileAttributes, UserPrincipalNotFoundException}
import scala.util.Using
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 17, 2026
 *  version Jul. 22, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecretReference private (
  private val _locator: String
) {
  override def equals(other: Any): Boolean =
    other match {
      case value: SecretReference => _locator == value._locator
      case _ => false
    }

  override def hashCode(): Int =
    0

  override def toString: String =
    "SecretReference(<redacted>)"

  /** Only runtime-private explicit file resolution may inspect the opaque locator. */
  private[config] def _read_private_file_c(): Consequence[SecretMaterial] = {
    try {
      val uri = new URI(this._locator)
      val path = Option.when(uri.getScheme == "file" && !uri.isOpaque && uri.getRawAuthority == null &&
        uri.getRawUserInfo == null && uri.getRawQuery == null && uri.getRawFragment == null)(Path.of(uri))
        .filter(file => file.isAbsolute && file == file.normalize() &&
        file.getFileName != null && file.getParent != null)
      path.fold(_private_file_invalid_c[SecretMaterial]) { file =>
        val username = ProcessHandle.current().info().user()
        if (!username.isPresent) _private_file_invalid_c
        else {
          val principal = file.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(username.get())
          val parent = file.getParent
          val ancestors = Vector.iterate(parent, parent.getNameCount + 1)(_.getParent)
          val directories = ancestors.forall { ancestor =>
            val attributes = Files.readAttributes(ancestor, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
            attributes.isDirectory && !attributes.isSymbolicLink
          }
          if (!directories) _private_file_invalid_c
          else {
            val directory = Files.readAttributes(parent, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
            val target = Files.readAttributes(file, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
            if (directory.owner() != principal || !_exact_mode(parent, 448) ||
                !target.isRegularFile || target.isSymbolicLink || target.owner() != principal ||
                !_exact_mode(file, 384) || !_single_link(file)) _private_file_invalid_c
            else {
              val bytes = Using.resource(Files.newInputStream(file,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))(_.readNBytes(131073))
              if (bytes.isEmpty || bytes.length > 131072) _private_file_invalid_c
              else Consequence.success(new SecretMaterial(bytes.clone()))
            }
          }
        }
      }
    } catch {
      case _: java.net.URISyntaxException | _: IllegalArgumentException | _: UnsupportedOperationException |
          _: SecurityException | _: NoSuchFileException | _: NotDirectoryException |
          _: UserPrincipalNotFoundException => _private_file_invalid_c
      case NonFatal(_) => Consequence.serviceUnavailable("Private secret file is unavailable")
    }
  }

  private def _exact_mode(file: Path, mode: Int): Boolean =
    Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS) match {
      case value: java.lang.Number => (value.intValue() & 4095) == mode
      case _ => false
    }

  private def _single_link(file: Path): Boolean =
    Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS) match {
      case value: java.lang.Number => value.longValue() == 1L
      case _ => false
    }

  private def _private_file_invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid("Private secret file configuration is invalid")
}

object SecretReference {
  private[cncf] def fromConfiguration(value: String): Consequence[SecretReference] =
    Option(value).map(_.trim).filter(_.nonEmpty).fold[Consequence[SecretReference]](
      Consequence.configurationInvalid("secret reference is required")
    )(value => Consequence.success(new SecretReference(value)))
}

private[cncf] final class SecretMaterial private[config] (
  private val _bytes: Array[Byte]
) {
  private[cncf] def _copy_bytes: Array[Byte] =
    _bytes.clone()

  override def toString: String =
    "SecretMaterial(<redacted>)"
}

private[cncf] trait RuntimeSecretResolver {
  def resolveSecret(reference: SecretReference): Consequence[SecretMaterial]
}

private[cncf] object RuntimeSecretResolver {
  /** Selection is inert; only resolveSecret performs private host filesystem IO. */
  val privateFile: RuntimeSecretResolver = new RuntimeSecretResolver {
    def resolveSecret(reference: SecretReference): Consequence[SecretMaterial] =
      if (reference == null)
        Consequence.configurationInvalid("Private secret file configuration is invalid")
      else reference._read_private_file_c()
  }

  def inMemory(entries: Iterable[(SecretReference, Array[Byte])]): RuntimeSecretResolver =
    new InMemorySecretResolver(
      entries.iterator.map { case (reference, value) =>
        reference -> new SecretMaterial(value.clone())
      }.toMap
    )

  private final class InMemorySecretResolver(
    entries: Map[SecretReference, SecretMaterial]
  ) extends RuntimeSecretResolver {
    def resolveSecret(reference: SecretReference): Consequence[SecretMaterial] =
      entries.get(reference).map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("secret reference cannot be resolved by the runtime")
      )
  }
}
