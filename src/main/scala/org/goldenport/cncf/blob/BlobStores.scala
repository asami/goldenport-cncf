package org.goldenport.cncf.blob

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import scala.collection.concurrent.TrieMap
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.EntityId

/*
 * Initial BlobStore backends for development and executable specifications.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class InMemoryBlobStore(
  val name: String = "in-memory",
  container: String = BlobStorageRef.DefaultContainer,
  backendBaseUrl: Option[String] = None
) extends BlobStore {
  private final case class Stored(
    id: EntityId,
    ref: BlobStorageRef,
    contentType: ContentType,
    bytes: Array[Byte],
    digest: String,
    storedAt: Instant
  )

  private val _entries = TrieMap.empty[String, Stored]

  def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] =
    for {
      bytes <- BlobStoreSupport.readAllBytes(payload)
      now = Instant.now
      digest = BlobStoreSupport.sha256(bytes)
      key = BlobStoreSupport.keyFor(request.id, request.filename)
      ref = BlobStorageRef(name, container, key)
      stored = Stored(request.id, ref, request.contentType, bytes, digest, now)
      _ = _entries.update(ref.print, stored)
      access <- accessUrl(ref)
    } yield BlobPutResult(
      id = request.id,
      storageRef = ref,
      contentType = request.contentType,
      byteSize = bytes.length.toLong,
      digest = digest,
      accessUrl = access,
      storedAt = now
    )

  def get(ref: BlobStorageRef): Consequence[BlobReadResult] =
    _validate_ref(ref).flatMap { _ =>
      _entries.get(ref.print) match {
        case Some(stored) =>
          accessUrl(ref).map { access =>
            BlobReadResult(
              id = stored.id,
              storageRef = ref,
              contentType = stored.contentType,
              byteSize = stored.bytes.length.toLong,
              digest = stored.digest,
              payload = Bag.binary(stored.bytes.clone()),
              accessUrl = access,
              storedAt = stored.storedAt
            )
          }
        case None =>
          Consequence.operationNotFound(s"blob:${ref.print}")
      }
    }

  def delete(ref: BlobStorageRef): Consequence[Unit] =
    _validate_ref(ref).map { _ =>
      _entries.remove(ref.print)
      ()
    }

  def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] =
    _validate_ref(ref).map { _ =>
      backendBaseUrl
        .map(base => BlobStoreSupport.backendAccessUrl(base, ref))
        .getOrElse(BlobUrl.cncfRoute(ref))
    }

  def status(): Consequence[BlobStoreStatus] =
    Consequence.success(
      BlobStoreStatus(
        backend = "in_memory",
        available = true,
        container = Some(container),
        location = None,
        message = Some(s"${_entries.size} blob payloads")
      )
    )

  private def _validate_ref(ref: BlobStorageRef): Consequence[Unit] =
    if (ref.store != name)
      Consequence.argumentInvalid(s"blob store mismatch: ${ref.store}")
    else if (ref.container != container)
      Consequence.argumentInvalid(s"blob container mismatch: ${ref.container}")
    else if (!BlobStoreSupport.validKey(ref.key))
      Consequence.argumentInvalid(s"invalid blob storage key: ${ref.key}")
    else
      Consequence.unit
}

final class LocalBlobStore(
  root: Path,
  val name: String = "local",
  container: String = BlobStorageRef.DefaultContainer,
  backendBaseUrl: Option[String] = None
) extends BlobStore {
  private final case class StoredMetadata(
    id: EntityId,
    contentType: ContentType,
    byteSize: Long,
    digest: String,
    storedAt: Instant
  )

  private val _metadata = TrieMap.empty[String, StoredMetadata]

  def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult] =
    for {
      bytes <- BlobStoreSupport.readAllBytes(payload)
      _ <- _ensure_root()
      now = Instant.now
      digest = BlobStoreSupport.sha256(bytes)
      key = BlobStoreSupport.keyFor(request.id, request.filename)
      ref = BlobStorageRef(name, container, key)
      path <- _path(ref)
      _ <- _write(path, bytes)
      _ = _metadata.update(ref.print, StoredMetadata(request.id, request.contentType, bytes.length.toLong, digest, now))
      access <- accessUrl(ref)
    } yield BlobPutResult(
      id = request.id,
      storageRef = ref,
      contentType = request.contentType,
      byteSize = bytes.length.toLong,
      digest = digest,
      accessUrl = access,
      storedAt = now
    )

  def get(ref: BlobStorageRef): Consequence[BlobReadResult] =
    for {
      path <- _path(ref)
      exists <- Consequence(Files.exists(path))
      result <-
        if (!exists) {
          Consequence.operationNotFound(s"blob:${ref.print}")
        }
        else
          for {
            metadata <- _metadata.get(ref.print) match {
              case Some(m) => Consequence.success(m)
              case None => Consequence.operationNotFound(s"blob metadata:${ref.print}")
            }
            access <- accessUrl(ref)
          } yield BlobReadResult(
            id = metadata.id,
            storageRef = ref,
            contentType = metadata.contentType,
            byteSize = metadata.byteSize,
            digest = metadata.digest,
            payload = Bag.file(path).promoteToBinary(),
            accessUrl = access,
            storedAt = metadata.storedAt
          )
    } yield result

  def delete(ref: BlobStorageRef): Consequence[Unit] =
    for {
      path <- _path(ref)
      _ <- Consequence {
        Files.deleteIfExists(path)
        _metadata.remove(ref.print)
        ()
      }
    } yield ()

  def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl] =
    _path(ref).map { _ =>
      backendBaseUrl
        .map(base => BlobStoreSupport.backendAccessUrl(base, ref))
        .getOrElse(BlobUrl.cncfRoute(ref))
    }

  def status(): Consequence[BlobStoreStatus] =
    Consequence.success(
      BlobStoreStatus(
        backend = "local",
        available = Files.exists(root) || Option(root.getParent).forall(Files.exists(_)),
        container = Some(container),
        location = Some(root.toAbsolutePath.normalize.toString),
        message = None
      )
    )

  private def _ensure_root(): Consequence[Unit] =
    Consequence {
      Files.createDirectories(root.resolve(container))
      ()
    }

  private def _path(ref: BlobStorageRef): Consequence[Path] =
    if (ref.store != name)
      Consequence.argumentInvalid(s"blob store mismatch: ${ref.store}")
    else if (ref.container != container)
      Consequence.argumentInvalid(s"blob container mismatch: ${ref.container}")
    else if (!BlobStoreSupport.validKey(ref.key))
      Consequence.argumentInvalid(s"invalid blob storage key: ${ref.key}")
    else {
      val base = root.resolve(container).toAbsolutePath.normalize
      val path = base.resolve(ref.key).normalize
      if (!path.startsWith(base))
        Consequence.argumentInvalid(s"invalid blob storage key: ${ref.key}")
      else
        Consequence.success(path)
    }

  private def _write(path: Path, bytes: Array[Byte]): Consequence[Unit] =
    Consequence {
      Files.createDirectories(path.getParent)
      Files.write(path, bytes)
      ()
    }
}

object BlobStoreSupport {
  def readAllBytes(payload: BinaryBag): Consequence[Array[Byte]] =
    Consequence {
      Using.resource(payload.openInputStream())(_.readAllBytes())
    }

  def sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    digest.map("%02x".format(_)).mkString
  }

  def keyFor(id: EntityId, filename: Option[String]): String = {
    val safe = filename.map(safeFilename).filter(_.nonEmpty).getOrElse("payload.bin")
    s"${safeSegment(id.value)}/$safe"
  }

  def validKey(key: String): Boolean = {
    val segments = key.split("/").toVector
    key.trim.nonEmpty &&
      !key.startsWith("/") &&
      !key.endsWith("/") &&
      segments.nonEmpty &&
      segments.forall { segment =>
        segment.nonEmpty && segment != "." && segment != ".." && !segment.contains("\\")
      }
  }

  def backendAccessUrl(base: String, ref: BlobStorageRef): BlobAccessUrl = {
    val normalizedBase = base.stripSuffix("/")
    val path = ref.key.split("/").toVector.map(BlobUrlSegment.encode).mkString("/")
    val url = s"$normalizedBase/${BlobUrlSegment.encode(ref.container)}/$path"
    BlobAccessUrl(
      displayUrl = url,
      downloadUrl = url,
      expiresAt = None,
      urlSource = BlobAccessUrlSource.Backend
    )
  }

  def safeSegment(value: String): String =
    value.trim.toLowerCase(Locale.ROOT).map {
      case c if c.isLetterOrDigit => c
      case '-' | '_' => '-'
      case _ => '-'
    }.mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-") match {
      case "" => "blob"
      case s => s
    }

  def safeFilename(value: String): String = {
    val name = value.split("[/\\\\]").lastOption.getOrElse(value)
    val cleaned = name.trim.map {
      case c if c.isLetterOrDigit => c
      case c @ ('.' | '-' | '_') => c
      case _ => '-'
    }.mkString.replaceAll("-+", "-")
    if (cleaned.nonEmpty && cleaned != "." && cleaned != "..") cleaned else "payload.bin"
  }
}

private object BlobUrlSegment {
  def encode(value: String): String =
    java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
      .replace("+", "%20")
}
