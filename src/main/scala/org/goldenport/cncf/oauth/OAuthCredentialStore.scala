package org.goldenport.cncf.oauth

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, NoSuchFileException, Path, StandardCopyOption, StandardOpenOption}
import java.nio.file.attribute.{BasicFileAttributes, PosixFileAttributes, PosixFilePermissions}
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
/** Synchronous private host IO; only a trusted native interpreter may invoke it. */
private[cncf] trait OAuthCredentialStore {
  def loadC(): Consequence[Option[OAuthCredential]]
  def saveC(credential: OAuthCredential): Consequence[Unit]
  def forgetC(): Consequence[Unit]
  override def toString: String = "OAuthCredentialStore(<redacted>)"
}

/** The narrow host durability capability, also used to specify uncertain outcomes. */
private[oauth] trait OAuthCredentialDurability {
  def forceDirectory(directory: Path): Unit
}

private[oauth] object OAuthCredentialDurability {
  val posix: OAuthCredentialDurability = new OAuthCredentialDurability {
    def forceDirectory(directory: Path): Unit =
      Using.resource(FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))(_.force(true))
  }
}

/** Owner-only plaintext, not encryption. The selected directory must be outside Git/Dropbox. */
private[cncf] final class PrivateFileOAuthCredentialStore private[oauth] (
  credentialfile: Path,
  connectionid: String,
  ownerid: String,
  client: OAuthClient,
  durability: OAuthCredentialDurability
) extends OAuthCredentialStore {
  import PrivateFileOAuthCredentialStore.*

  override def toString: String = "PrivateFileOAuthCredentialStore(<redacted>)"

  def loadC(): Consequence[Option[OAuthCredential]] = _io_c {
    _admit_c(credentialfile, connectionid, ownerid, client).flatMap { exists =>
      if (exists) _read_c().map(Some(_)) else Consequence.success(None)
    }
  }

  def saveC(credential: OAuthCredential): Consequence[Unit] = _io_c {
    _record_c(credential).flatMap { bytes =>
      _admit_c(credentialfile, connectionid, ownerid, client).flatMap { exists =>
        val previous = if (exists) _read_c().map(_ => ()) else Consequence.unit
        previous.flatMap(_ => _replace_c(bytes))
      }
    }
  }

  def forgetC(): Consequence[Unit] = _io_c {
    _admit_c(credentialfile, connectionid, ownerid, client).flatMap { exists =>
      if (!exists) Consequence.unit
      else _read_c().flatMap { _ =>
        Files.delete(credentialfile)
        _confirm_c()
      }
    }
  }

  private def _read_c(): Consequence[OAuthCredential] = {
    val bytes = Using.resource(Files.newInputStream(credentialfile,
      StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))(_.readNBytes(131073))
    if (bytes.isEmpty || bytes.length > 131072) _record_invalid_c
    else {
      val record = try {
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
        _parser.parse(text).toOption.flatMap(_.asObject)
      } catch { case NonFatal(_) => None }
      val fields = record.filter { fields =>
        val keys = fields.keys.toSet
        (keys == _required_keys || keys == _required_keys + "refreshToken") &&
          fields("schema").contains(Json.fromString("cncf.oauth-credential/1")) &&
          fields("connectionId").contains(Json.fromString(connectionid)) &&
          fields("ownerId").contains(Json.fromString(ownerid)) &&
          fields("client").contains(client._connection_binding)
      }
      val decoded = fields.flatMap { fields =>
        for {
          access <- fields("accessToken").flatMap(_.asString)
          refresh <- fields("refreshToken") match {
            case None => Some(None)
            case Some(value) => value.asString.map(Some(_))
          }
          scopes <- fields("grantedScopes").flatMap(_.asArray).flatMap { values =>
            val strings = values.flatMap(_.asString)
            Option.when(strings.size == values.size)(strings)
          }
          text <- fields("expiresAt").flatMap(_.asString)
          expiry <- _instant(text)
        } yield (access, refresh, scopes, expiry)
      }
      decoded.fold(_record_invalid_c[OAuthCredential]) { case (access, refresh, scopes, expiry) =>
        client._restore_credential_c(access, refresh, scopes, expiry)
      }
    }
  }

  private def _record_c(credential: OAuthCredential): Consequence[Array[Byte]] = {
    if (credential == null || client == null || !(credential._client eq client)) _record_invalid_c
    else client._restore_credential_c(credential.accessToken, credential.refreshToken,
      credential.grantedScopes, credential.expiresAt).flatMap { _ =>
      val fields = Vector(
        "schema" -> Json.fromString("cncf.oauth-credential/1"),
        "connectionId" -> Json.fromString(connectionid),
        "ownerId" -> Json.fromString(ownerid),
        "client" -> client._connection_binding,
        "accessToken" -> Json.fromString(credential.accessToken),
        "grantedScopes" -> Json.fromValues(credential.grantedScopes.map(Json.fromString)),
        "expiresAt" -> Json.fromString(credential.expiresAt.toString)) ++
        credential.refreshToken.map(value => "refreshToken" -> Json.fromString(value))
      val bytes = Json.obj(fields*).noSpaces.getBytes(StandardCharsets.UTF_8)
      if (bytes.nonEmpty && bytes.length <= 131072) Consequence.success(bytes) else _record_invalid_c
    }
  }

  private def _replace_c(bytes: Array[Byte]): Consequence[Unit] = {
    // JDK createTempFile creates one sibling exclusively (CREATE_NEW), with 0600 at creation.
    val staged = Files.createTempFile(credentialfile.getParent, ".cncf-oauth-", ".tmp",
      PosixFilePermissions.asFileAttribute(_file_permissions))
    try {
      Using.resource(FileChannel.open(staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { channel =>
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining) channel.write(buffer)
        channel.force(true)
      }
      Files.move(staged, credentialfile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      _confirm_c()
    } finally {
      // Only this attempt's staging name is eligible; never remove the selected target.
      try Files.deleteIfExists(staged) catch { case NonFatal(_) => () }
    }
  }

  private def _confirm_c(): Consequence[Unit] = {
    try {
      durability.forceDirectory(credentialfile.getParent)
      Consequence.unit
    } catch {
      case NonFatal(_) => Consequence.stateConflict("OAuth credential storage update outcome is uncertain")
    }
  }
}

private[cncf] object PrivateFileOAuthCredentialStore {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _required_keys = Set("schema", "connectionId", "ownerId", "client", "accessToken", "grantedScopes", "expiresAt")
  private val _directory_permissions = PosixFilePermissions.fromString("rwx------")
  private val _file_permissions = PosixFilePermissions.fromString("rw-------")

  def createC(credentialFile: Path, connectionId: String, ownerId: String,
    client: OAuthClient): Consequence[PrivateFileOAuthCredentialStore] = _io_c {
    _admit_c(credentialFile, connectionId, ownerId, client).map { _ =>
      new PrivateFileOAuthCredentialStore(credentialFile, connectionId, ownerId, client, OAuthCredentialDurability.posix)
    }
  }

  private def _admit_c(file: Path, connection: String, owner: String, client: OAuthClient): Consequence[Boolean] = {
    if (file == null || client == null || !file.isAbsolute || file != file.normalize() ||
        file.getFileName == null || file.getParent == null ||
        !Option(connection).exists(_.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) ||
        !OAuthValidation.printable(owner, 256)) _configuration_invalid_c
    else {
      val username = ProcessHandle.current().info().user()
      if (!username.isPresent) _configuration_invalid_c
      else {
        val principal = file.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(username.get())
        val parent = file.getParent
        val ancestors = Vector.iterate(parent, parent.getNameCount + 1)(_.getParent)
        val directories = try ancestors.forall { ancestor =>
          val attributes = Files.readAttributes(ancestor, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
          attributes.isDirectory && !attributes.isSymbolicLink
        } catch { case _: NoSuchFileException => false }
        if (!directories) _configuration_invalid_c
        else {
          val attributes = Files.readAttributes(parent, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
          if (attributes.owner() != principal || attributes.permissions() != _directory_permissions ||
              !_exact_mode(parent, 448))
            _configuration_invalid_c
          else {
            val target = try Some(Files.readAttributes(file, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS))
              catch { case _: NoSuchFileException => None }
            target match {
              case None => Consequence.success(false)
              case Some(value) if value.isRegularFile && !value.isSymbolicLink && value.owner() == principal &&
                  value.permissions() == _file_permissions && _exact_mode(file, 384) && _single_link(file) =>
                Consequence.success(true)
              case _ => _configuration_invalid_c
            }
          }
        }
      }
    }
  }

  private def _instant(text: String): Option[Instant] = {
    try Option(Instant.parse(text)).filter(_.toString == text) catch { case NonFatal(_) => None }
  }
  private def _exact_mode(file: Path, mode: Int): Boolean =
    Files.getAttribute(file, "unix:mode", LinkOption.NOFOLLOW_LINKS) match {
      case value: java.lang.Number => (value.intValue() & 4095) == mode
      case _ => false
    }
  private def _single_link(file: Path): Boolean =
    Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS) match {
      case count: java.lang.Number => count.longValue() == 1L
      case _ => false
    }
  private def _configuration_invalid_c[A]: Consequence[A] =
    Consequence.configurationInvalid("OAuth credential storage configuration is invalid")
  private def _record_invalid_c[A]: Consequence[A] =
    Consequence.resourceInvalid("OAuth credential storage record is invalid")
  private def _io_c[A](body: => Consequence[A]): Consequence[A] = {
    try body catch {
      case _: UnsupportedOperationException | _: IllegalArgumentException | _: SecurityException => _configuration_invalid_c
      case NonFatal(_) => Consequence.serviceUnavailable("OAuth credential storage is unavailable")
    }
  }
}
