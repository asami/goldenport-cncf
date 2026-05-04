package org.goldenport.cncf.blob

import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntitySearchScope, EntityStore}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Built-in media entities layered above Blob storage metadata.
 *
 * @since   May.  3, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
enum MediaKind(val value: String) {
  case Image extends MediaKind("image")
  case Video extends MediaKind("video")
  case Audio extends MediaKind("audio")
  case Attachment extends MediaKind("attachment")

  def print: String = value
}

object MediaKind {
  def parse(value: String): Consequence[MediaKind] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)) match {
      case Some("image") => Consequence.success(MediaKind.Image)
      case Some("video") => Consequence.success(MediaKind.Video)
      case Some("audio") => Consequence.success(MediaKind.Audio)
      case Some("attachment") => Consequence.success(MediaKind.Attachment)
      case Some(other) if other.nonEmpty =>
        Consequence.argumentInvalid(s"unknown media kind: $other")
      case _ =>
        Consequence.argumentInvalid("media kind must not be empty")
    }
}

object MediaEntityCollections {
  val Image: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "image")
  val Video: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "video")
  val Audio: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "audio")
  val Attachment: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "attachment")

  def collection(kind: MediaKind): EntityCollectionId =
    kind match {
      case MediaKind.Image => Image
      case MediaKind.Video => Video
      case MediaKind.Audio => Audio
      case MediaKind.Attachment => Attachment
    }

  def kind(collection: EntityCollectionId): Option[MediaKind] =
    collection.name match {
      case "image" => Some(MediaKind.Image)
      case "video" => Some(MediaKind.Video)
      case "audio" => Some(MediaKind.Audio)
      case "attachment" => Some(MediaKind.Attachment)
      case _ => None
    }
}

final case class MediaEntity(
  id: EntityId,
  kind: MediaKind,
  blobId: EntityId,
  name: Option[String],
  title: Option[String],
  contentType: Option[String],
  filename: Option[String],
  byteSize: Option[Long],
  digest: Option[String],
  accessUrl: Option[String],
  alt: Option[String] = None,
  width: Option[Int] = None,
  height: Option[Int] = None,
  duration: Option[Long] = None,
  posterImageId: Option[EntityId] = None,
  createdAt: Instant,
  updatedAt: Instant,
  attributes: Map[String, String] = Map.empty
) {
  def toRecord: Record =
    MediaRecordCodec.toRecord(this)
}

final case class MediaCreate(
  id: EntityId,
  kind: MediaKind,
  blobId: EntityId,
  name: Option[String],
  title: Option[String],
  contentType: Option[String],
  filename: Option[String],
  byteSize: Option[Long],
  digest: Option[String],
  accessUrl: Option[String],
  alt: Option[String] = None,
  width: Option[Int] = None,
  height: Option[Int] = None,
  duration: Option[Long] = None,
  posterImageId: Option[EntityId] = None,
  attributes: Map[String, String] = Map.empty
)

final case class MediaEnsureResult(
  media: MediaEntity,
  created: Boolean
)

trait MediaRepository {
  def create(media: MediaCreate)(using ExecutionContext): Consequence[MediaEntity]
  def get(kind: MediaKind, id: EntityId)(using ExecutionContext): Consequence[MediaEntity]
  def list(kind: MediaKind)(using ExecutionContext): Consequence[Vector[MediaEntity]]
  def findByBlob(kind: MediaKind, blobId: EntityId)(using ExecutionContext): Consequence[Option[MediaEntity]]
  def ensureForBlobResult(kind: MediaKind, blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEnsureResult]
  def ensureForBlob(kind: MediaKind, blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEntity] =
    ensureForBlobResult(kind, blob, alt, title).map(_.media)
  def ensureImageForBlobResult(blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEnsureResult] =
    ensureForBlobResult(MediaKind.Image, blob, alt, title)
  def ensureImageForBlob(blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEntity] =
    ensureImageForBlobResult(blob, alt, title).map(_.media)
  def delete(media: MediaEntity)(using ExecutionContext): Consequence[Unit]
}

object MediaRepository {
  def entityStore(): MediaRepository =
    new EntityStoreMediaRepository()

  given EntityPersistent[MediaEntity] with {
    def id(e: MediaEntity): EntityId = e.id
    def toRecord(e: MediaEntity): Record = MediaRecordCodec.toRecord(e)
    override def toStoreRecord(e: MediaEntity): Record = MediaRecordCodec.toStoreRecord(e)
    def fromRecord(r: Record): Consequence[MediaEntity] = MediaRecordCodec.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[MediaEntity] = MediaRecordCodec.fromStoreRecord(r)
  }

  given EntityPersistentCreate[MediaCreate] with {
    def id(e: MediaCreate): Option[EntityId] = Some(e.id)
    def collection(e: MediaCreate): EntityCollectionId = MediaEntityCollections.collection(e.kind)
    def toRecord(e: MediaCreate): Record = MediaRecordCodec.toRecord(e)
    override def toStoreRecord(e: MediaCreate): Record = MediaRecordCodec.toStoreRecord(e)
  }
}

final class EntityStoreMediaRepository extends MediaRepository {
  import MediaRepository.given

  def create(media: MediaCreate)(using ctx: ExecutionContext): Consequence[MediaEntity] =
    for {
      result <- EntityStore.standard().create(media)
      loaded <- EntityStore.standard().load[MediaEntity](result.id)
      created <- loaded.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"media entity:${result.id.print}"))
    } yield _normalize_collection(created)

  def get(kind: MediaKind, id: EntityId)(using ctx: ExecutionContext): Consequence[MediaEntity] = {
    val mediaId = _media_entity_id(kind, id)
    EntityStore.standard().load[MediaEntity](mediaId).flatMap {
      case Some(value) => Consequence.success(_normalize_collection(value))
      case None => Consequence.operationNotFound(s"${kind.print} entity:${mediaId.value}")
    }
  }

  def list(kind: MediaKind)(using ctx: ExecutionContext): Consequence[Vector[MediaEntity]] =
    EntityStore.standard()
      .search[MediaEntity](EntityQuery(MediaEntityCollections.collection(kind), Query.plan(Record.empty), EntitySearchScope.Store))
      .map(_.data.map(_normalize_collection))

  def findByBlob(kind: MediaKind, blobId: EntityId)(using ctx: ExecutionContext): Consequence[Option[MediaEntity]] =
    list(kind).map(_.find(_.blobId.value == blobId.value))

  def ensureForBlobResult(kind: MediaKind, blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ctx: ExecutionContext): Consequence[MediaEnsureResult] =
    findByBlob(kind, blob.id).flatMap {
      case Some(value) => Consequence.success(MediaEnsureResult(value, created = false))
      case None =>
        create(MediaCreate(
          id = ctx.idGeneration.entityId(MediaEntityCollections.collection(kind)),
          kind = kind,
          blobId = blob.id,
          name = blob.filename.orElse(Some(blob.id.parts.entropy)),
          title = title.orElse(blob.filename),
          contentType = blob.contentType.map(_.header),
          filename = blob.filename,
          byteSize = blob.byteSize,
          digest = blob.digest,
          accessUrl = Some(blob.accessUrl.displayUrl),
          alt = if (kind == MediaKind.Image) alt else None,
          attributes = Map("sourceBlobId" -> blob.id.value)
        )).map(MediaEnsureResult(_, created = true))
    }

  def delete(media: MediaEntity)(using ctx: ExecutionContext): Consequence[Unit] =
    EntityStore.standard().delete(media.id)

  private def _normalize_collection(media: MediaEntity): MediaEntity =
    media.copy(id = _media_entity_id(media.kind, media.id))

  private def _media_entity_id(kind: MediaKind, id: EntityId): EntityId = {
    val collection = MediaEntityCollections.collection(kind)
    if (id.collection == collection)
      id
    else
      id.copy(collection = collection)
  }
}

object MediaRecordCodec {
  def toRecord(media: MediaEntity): Record =
    _record(
      id = Some(media.id),
      kind = media.kind,
      blobid = media.blobId,
      name = media.name,
      title = media.title,
      contenttype = media.contentType,
      filename = media.filename,
      bytesize = media.byteSize,
      digest = media.digest,
      accessurl = media.accessUrl,
      alt = media.alt,
      width = media.width,
      height = media.height,
      duration = media.duration,
      posterimageid = media.posterImageId,
      createdat = Some(media.createdAt),
      updatedat = Some(media.updatedAt),
      attributes = media.attributes
    )

  def toStoreRecord(media: MediaEntity): Record =
    toRecord(media)

  def toRecord(media: MediaCreate): Record =
    _record(
      id = Some(media.id),
      kind = media.kind,
      blobid = media.blobId,
      name = media.name,
      title = media.title,
      contenttype = media.contentType,
      filename = media.filename,
      bytesize = media.byteSize,
      digest = media.digest,
      accessurl = media.accessUrl,
      alt = media.alt,
      width = media.width,
      height = media.height,
      duration = media.duration,
      posterimageid = media.posterImageId,
      createdat = None,
      updatedat = None,
      attributes = media.attributes
    )

  def toStoreRecord(media: MediaCreate): Record =
    toRecord(media)

  def fromRecord(record: Record): Consequence[MediaEntity] =
    fromStoreRecord(record)

  def fromStoreRecord(record: Record): Consequence[MediaEntity] =
    for {
      rawid <- EntityId.createC(record)
      kind <- _string(record, "kind").map(MediaKind.parse).orElse(MediaEntityCollections.kind(rawid.collection).map(Consequence.success)).getOrElse(Consequence.argumentMissing("kind"))
      id = EntityId(rawid.major, rawid.minor, MediaEntityCollections.collection(kind), rawid.timestamp, rawid.entropy)
      blobid <- _entity_id(record, "blobId", "blob_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("blobId"))
      createdat <- _instant(record, "createdAt", "created_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("createdAt"))
      updatedat <- _instant(record, "updatedAt", "updated_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("updatedAt"))
    } yield MediaEntity(
      id = id,
      kind = kind,
      blobId = blobid,
      name = _string(record, "name"),
      title = _string(record, "title"),
      contentType = _string(record, "contentType", "content_type"),
      filename = _string(record, "filename"),
      byteSize = _long(record, "byteSize", "byte_size"),
      digest = _string(record, "digest"),
      accessUrl = _string(record, "accessUrl", "access_url"),
      alt = _string(record, "alt"),
      width = _int(record, "width"),
      height = _int(record, "height"),
      duration = _long(record, "duration"),
      posterImageId = _entity_id(record, "posterImageId", "poster_image_id"),
      createdAt = createdat,
      updatedAt = updatedat,
      attributes = _attributes(record)
    )

  private def _record(
    id: Option[EntityId],
    kind: MediaKind,
    blobid: EntityId,
    name: Option[String],
    title: Option[String],
    contenttype: Option[String],
    filename: Option[String],
    bytesize: Option[Long],
    digest: Option[String],
    accessurl: Option[String],
    alt: Option[String],
    width: Option[Int],
    height: Option[Int],
    duration: Option[Long],
    posterimageid: Option[EntityId],
    createdat: Option[Instant],
    updatedat: Option[Instant],
    attributes: Map[String, String]
  ): Record =
    Record.dataAuto(
      "id" -> id.map(_.value),
      "kind" -> kind.print,
      "blobId" -> blobid.value,
      "name" -> name,
      "title" -> title,
      "contentType" -> contenttype,
      "filename" -> filename,
      "byteSize" -> bytesize,
      "digest" -> digest,
      "accessUrl" -> accessurl,
      "alt" -> alt,
      "width" -> width,
      "height" -> height,
      "duration" -> duration,
      "posterImageId" -> posterimageid.map(_.value),
      "createdAt" -> createdat.map(_.toString),
      "updatedAt" -> updatedat.map(_.toString),
      "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
    )

  private def _string(record: Record, names: String*): Option[String] =
    names.iterator.flatMap(record.getAny).map(_.toString.trim).find(_.nonEmpty)

  private def _entity_id(record: Record, names: String*): Option[EntityId] =
    names.iterator.flatMap(record.getAny).flatMap {
      case id: EntityId => Some(id)
      case s: String => EntityId.parse(s).toOption
      case other => EntityId.parse(other.toString).toOption
    }.nextOption()

  private def _int(record: Record, names: String*): Option[Int] =
    names.iterator.flatMap(record.getAny).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case other => scala.util.Try(other.toString.trim.toInt).toOption
    }.nextOption()

  private def _long(record: Record, names: String*): Option[Long] =
    names.iterator.flatMap(record.getAny).flatMap {
      case n: java.lang.Number => Some(n.longValue)
      case s: String => scala.util.Try(s.trim.toLong).toOption
      case other => scala.util.Try(other.toString.trim.toLong).toOption
    }.nextOption()

  private def _instant(record: Record, names: String*): Option[Instant] =
    names.iterator.flatMap(record.getAny).flatMap {
      case m: Instant => Some(m)
      case s: String => scala.util.Try(Instant.parse(s.trim)).toOption
      case other => scala.util.Try(Instant.parse(other.toString.trim)).toOption
    }.nextOption()

  private def _attributes(record: Record): Map[String, String] =
    record.getAny("attributes") match {
      case Some(rec: Record) => rec.asMap.map { case (k, v) => k -> v.toString }
      case Some(map: Map[?, ?]) => map.iterator.collect { case (k: String, v) => k -> v.toString }.toMap
      case _ => Map.empty
    }
}
