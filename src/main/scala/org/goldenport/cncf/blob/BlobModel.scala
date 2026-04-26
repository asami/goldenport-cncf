package org.goldenport.cncf.blob

import java.net.URLEncoder
import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.bag.BinaryBag
import org.goldenport.datatype.ContentType
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Runtime model for CNCF-managed Blob payload storage.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 27, 2026
 * @author  ASAMI, Tomoharu
 */
enum BlobKind(val value: String) {
  case Image extends BlobKind("image")
  case Video extends BlobKind("video")
  case Attachment extends BlobKind("attachment")
  case Binary extends BlobKind("binary")

  def print: String = value
}

object BlobKind {
  def parse(value: String): Consequence[BlobKind] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("image") => Consequence.success(BlobKind.Image)
      case Some("video") => Consequence.success(BlobKind.Video)
      case Some("attachment") => Consequence.success(BlobKind.Attachment)
      case Some("binary") => Consequence.success(BlobKind.Binary)
      case Some(other) if other.nonEmpty =>
        Consequence.argumentInvalid(s"unknown blob kind: $other")
      case _ =>
        Consequence.argumentInvalid("blob kind must not be empty")
    }
}

enum BlobSourceMode(val value: String) {
  case Managed extends BlobSourceMode("managed")
  case ExternalUrl extends BlobSourceMode("external_url")

  def print: String = value
}

object BlobSourceMode {
  def parse(value: String): Consequence[BlobSourceMode] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT).replace("-", "_")) match {
      case Some("managed") => Consequence.success(BlobSourceMode.Managed)
      case Some("external_url") => Consequence.success(BlobSourceMode.ExternalUrl)
      case Some(other) if other.nonEmpty =>
        Consequence.argumentInvalid(s"unknown blob source mode: $other")
      case _ =>
        Consequence.argumentInvalid("blob source mode must not be empty")
    }
}

final case class BlobStorageRef(
  store: String,
  container: String,
  key: String,
  version: Option[String] = None,
  etag: Option[String] = None
) {
  def print: String =
    version.fold(s"$store://$container/$key")(v => s"$store://$container/$key#$v")
}

object BlobStorageRef {
  val DefaultStore: String = "blob"
  val DefaultContainer: String = "default"
}

enum BlobAccessUrlSource(val value: String) {
  case Backend extends BlobAccessUrlSource("backend")
  case CncfRoute extends BlobAccessUrlSource("cncf_route")

  def print: String = value
}

final case class BlobAccessUrl(
  displayUrl: String,
  downloadUrl: String,
  expiresAt: Option[Instant] = None,
  urlSource: BlobAccessUrlSource = BlobAccessUrlSource.CncfRoute
)

final case class BlobPutRequest(
  id: EntityId,
  kind: BlobKind,
  filename: Option[String],
  contentType: ContentType,
  attributes: Map[String, String] = Map.empty
)

final case class BlobPutResult(
  id: EntityId,
  storageRef: BlobStorageRef,
  contentType: ContentType,
  byteSize: Long,
  digest: String,
  accessUrl: BlobAccessUrl,
  storedAt: Instant
)

final case class BlobReadResult(
  id: EntityId,
  storageRef: BlobStorageRef,
  contentType: ContentType,
  byteSize: Long,
  digest: String,
  payload: BinaryBag,
  accessUrl: BlobAccessUrl,
  storedAt: Instant
)

final case class BlobMetadata(
  id: EntityId,
  kind: BlobKind,
  sourceMode: BlobSourceMode,
  filename: Option[String],
  contentType: Option[ContentType],
  byteSize: Option[Long],
  digest: Option[String],
  storageRef: Option[BlobStorageRef],
  externalUrl: Option[String],
  accessUrl: BlobAccessUrl,
  createdAt: Instant,
  updatedAt: Instant,
  attributes: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    Record.dataAuto(
      "id" -> id.value,
      "kind" -> kind.print,
      "sourceMode" -> sourceMode.print,
      "filename" -> filename,
      "contentType" -> contentType.map(_.header),
      "byteSize" -> byteSize,
      "digest" -> digest,
      "storageRef" -> storageRef.map(_.print),
      "externalUrl" -> externalUrl,
      "displayUrl" -> accessUrl.displayUrl,
      "downloadUrl" -> accessUrl.downloadUrl,
      "urlSource" -> accessUrl.urlSource.print,
      "createdAt" -> createdAt.toString,
      "updatedAt" -> updatedAt.toString,
      "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
    )
}

final case class Blob(
  id: EntityId,
  kind: BlobKind,
  sourceMode: BlobSourceMode,
  filename: Option[String],
  contentType: Option[ContentType],
  byteSize: Option[Long],
  digest: Option[String],
  storageRef: Option[BlobStorageRef],
  externalUrl: Option[String],
  accessUrl: BlobAccessUrl,
  createdAt: Instant,
  updatedAt: Instant,
  attributes: Map[String, String] = Map.empty
) {
  def metadata: BlobMetadata =
    BlobMetadata(
      id = id,
      kind = kind,
      sourceMode = sourceMode,
      filename = filename,
      contentType = contentType,
      byteSize = byteSize,
      digest = digest,
      storageRef = storageRef,
      externalUrl = externalUrl,
      accessUrl = accessUrl,
      createdAt = createdAt,
      updatedAt = updatedAt,
      attributes = attributes
    )
}

final case class BlobCreate(
  id: EntityId,
  kind: BlobKind,
  sourceMode: BlobSourceMode,
  filename: Option[String],
  contentType: Option[ContentType],
  byteSize: Option[Long],
  digest: Option[String],
  storageRef: Option[BlobStorageRef],
  externalUrl: Option[String],
  accessUrl: BlobAccessUrl,
  attributes: Map[String, String] = Map.empty
)

final case class BlobStoreStatus(
  backend: String,
  available: Boolean,
  container: Option[String] = None,
  location: Option[String] = None,
  message: Option[String] = None
)

trait BlobStore {
  def name: String
  def put(request: BlobPutRequest, payload: BinaryBag): Consequence[BlobPutResult]
  def get(ref: BlobStorageRef): Consequence[BlobReadResult]
  def delete(ref: BlobStorageRef): Consequence[Unit]
  def accessUrl(ref: BlobStorageRef): Consequence[BlobAccessUrl]
  def status(): Consequence[BlobStoreStatus]
}

object BlobUrl {
  def cncfRoute(ref: BlobStorageRef): BlobAccessUrl = {
    val encodedContainer = _encode(ref.container)
    val encodedKey = ref.key.split("/").toVector.map(_encode).mkString("/")
    val base = s"/web/blob/content/$encodedContainer/$encodedKey"
    BlobAccessUrl(
      displayUrl = base,
      downloadUrl = s"$base?download=true",
      expiresAt = None,
      urlSource = BlobAccessUrlSource.CncfRoute
    )
  }

  private def _encode(value: String): String =
    URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
      .replace("+", "%20")
}
