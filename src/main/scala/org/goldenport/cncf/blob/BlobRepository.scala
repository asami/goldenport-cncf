package org.goldenport.cncf.blob

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntitySearchScope, EntityStore}
import org.goldenport.datatype.ContentType
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Entity-backed repository for Blob metadata.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 27, 2026
 * @author  ASAMI, Tomoharu
 */
trait BlobRepository {
  def create(blob: BlobCreate)(using ExecutionContext): Consequence[Blob]
  def get(id: EntityId)(using ExecutionContext): Consequence[Blob]
  def list(offset: Int = 0, limit: Option[Int] = None)(using ExecutionContext): Consequence[Vector[Blob]]
}

object BlobRepository {
  val CollectionId: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "blob")

  def entityStore(): BlobRepository =
    new EntityStoreBlobRepository()

  given EntityPersistent[Blob] with {
    def id(e: Blob): EntityId = e.id
    def toRecord(e: Blob): Record = BlobRecordCodec.toRecord(e)
    override def toStoreRecord(e: Blob): Record = BlobRecordCodec.toStoreRecord(e)
    def fromRecord(r: Record): Consequence[Blob] = BlobRecordCodec.fromRecord(r)
    override def fromStoreRecord(r: Record): Consequence[Blob] = BlobRecordCodec.fromStoreRecord(r)
  }

  given EntityPersistentCreate[BlobCreate] with {
    def id(e: BlobCreate): Option[EntityId] = Some(e.id)
    def collection(e: BlobCreate): EntityCollectionId = CollectionId
    def toRecord(e: BlobCreate): Record = BlobRecordCodec.toRecord(e)
    override def toStoreRecord(e: BlobCreate): Record = BlobRecordCodec.toStoreRecord(e)
  }
}

final class EntityStoreBlobRepository extends BlobRepository {
  import BlobRepository.given

  def create(blob: BlobCreate)(using ctx: ExecutionContext): Consequence[Blob] =
    for {
      result <- EntityStore.standard().create(blob)
      id = result.id
      loaded <- EntityStore.standard().load[Blob](id)
      blob <- loaded match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.operationNotFound(s"blob entity:${id.print}")
      }
    } yield blob

  def get(id: EntityId)(using ctx: ExecutionContext): Consequence[Blob] =
    EntityStore.standard().load[Blob](id).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.operationNotFound(s"blob metadata:${id.value}")
    }

  def list(offset: Int = 0, limit: Option[Int] = None)(using ctx: ExecutionContext): Consequence[Vector[Blob]] =
    _search(Query.plan(Record.empty, limit = limit, offset = Some(offset)))

  private def _search(
    query: Query[?]
  )(using ctx: ExecutionContext): Consequence[Vector[Blob]] =
    EntityStore.standard()
      .search[Blob](EntityQuery(BlobRepository.CollectionId, query, EntitySearchScope.Store))
      .map(_.data)
}

object BlobRecordCodec {
  def toRecord(blob: Blob): Record =
    _record(blob.id, blob.kind, blob.sourceMode, blob.filename, blob.contentType, blob.byteSize, blob.digest, blob.storageRef, blob.externalUrl, blob.accessUrl, Some(blob.createdAt), Some(blob.updatedAt), blob.attributes)

  def toStoreRecord(blob: Blob): Record =
    toRecord(blob)

  def toRecord(blob: BlobCreate): Record =
    _record(blob.id, blob.kind, blob.sourceMode, blob.filename, blob.contentType, blob.byteSize, blob.digest, blob.storageRef, blob.externalUrl, blob.accessUrl, None, None, blob.attributes)

  def toStoreRecord(blob: BlobCreate): Record =
    toRecord(blob)

  def fromRecord(record: Record): Consequence[Blob] =
    fromStoreRecord(record)

  def fromStoreRecord(record: Record): Consequence[Blob] =
    for {
      id <- EntityId.createC(record)
      kind <- _string(record, "kind").map(BlobKind.parse).getOrElse(Consequence.argumentMissing("kind"))
      sourcemode <- _string(record, "sourceMode", "source_mode").map(BlobSourceMode.parse).getOrElse(Consequence.argumentMissing("sourceMode"))
      access <- _access_url(record)
      createdat <- _instant(record, "createdAt", "created_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("createdAt"))
      updatedat <- _instant(record, "updatedAt", "updated_at").map(Consequence.success).getOrElse(Consequence.argumentMissing("updatedAt"))
      storageref <- _storage_ref(record)
    } yield Blob(
      id = id,
      kind = kind,
      sourceMode = sourcemode,
      filename = _string(record, "filename"),
      contentType = _string(record, "contentType", "content_type").map(ContentType.parse),
      byteSize = _long(record, "byteSize", "byte_size"),
      digest = _string(record, "digest"),
      storageRef = storageref,
      externalUrl = _string(record, "externalUrl", "external_url"),
      accessUrl = access,
      createdAt = createdat,
      updatedAt = updatedat,
      attributes = _attributes(record)
    )

  private def _record(
    id: EntityId,
    kind: BlobKind,
    sourcemode: BlobSourceMode,
    filename: Option[String],
    contenttype: Option[ContentType],
    bytesize: Option[Long],
    digest: Option[String],
    storageref: Option[BlobStorageRef],
    externalurl: Option[String],
    accessurl: BlobAccessUrl,
    createdat: Option[Instant],
    updatedat: Option[Instant],
    attributes: Map[String, String]
  ): Record =
    Record.dataAuto(
      "id" -> id.value,
      "kind" -> kind.print,
      "sourceMode" -> sourcemode.print,
      "filename" -> filename,
      "contentType" -> contenttype.map(_.header),
      "byteSize" -> bytesize,
      "digest" -> digest,
      "storageRef" -> storageref.map(_.print),
      "storageRefStore" -> storageref.map(_.store),
      "storageRefContainer" -> storageref.map(_.container),
      "storageRefKey" -> storageref.map(_.key),
      "storageRefVersion" -> storageref.flatMap(_.version),
      "storageRefEtag" -> storageref.flatMap(_.etag),
      "externalUrl" -> externalurl,
      "displayUrl" -> accessurl.displayUrl,
      "downloadUrl" -> accessurl.downloadUrl,
      "urlSource" -> accessurl.urlSource.print,
      "createdAt" -> createdat.map(_.toString),
      "updatedAt" -> updatedat.map(_.toString),
      "attributes" -> Record.data(attributes.toVector.sortBy(_._1)*)
    )

  private def _access_url(record: Record): Consequence[BlobAccessUrl] =
    for {
      display <- _string(record, "displayUrl", "display_url").map(Consequence.success).getOrElse(Consequence.argumentMissing("displayUrl"))
      download <- _string(record, "downloadUrl", "download_url").map(Consequence.success).getOrElse(Consequence.argumentMissing("downloadUrl"))
      source <- _string(record, "urlSource", "url_source").map(_url_source).getOrElse(Consequence.success(BlobAccessUrlSource.CncfRoute))
    } yield BlobAccessUrl(display, download, _instant(record, "expiresAt", "expires_at"), source)

  private def _url_source(value: String): Consequence[BlobAccessUrlSource] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "backend" => Consequence.success(BlobAccessUrlSource.Backend)
      case "cncf_route" | "cncf-route" => Consequence.success(BlobAccessUrlSource.CncfRoute)
      case other => Consequence.argumentInvalid(s"unknown blob URL source: $other")
    }

  private def _storage_ref(record: Record): Consequence[Option[BlobStorageRef]] = {
    val structured =
      for {
        store <- _string(record, "storageRefStore", "storage_ref_store")
        container <- _string(record, "storageRefContainer", "storage_ref_container")
        key <- _string(record, "storageRefKey", "storage_ref_key")
      } yield BlobStorageRef(
        store = store,
        container = container,
        key = key,
        version = _string(record, "storageRefVersion", "storage_ref_version"),
        etag = _string(record, "storageRefEtag", "storage_ref_etag")
      )
    structured match {
      case Some(ref) => Consequence.success(Some(ref))
      case None => _string(record, "storageRef", "storage_ref").map(_parse_storage_ref).getOrElse(Consequence.success(None))
    }
  }

  private def _parse_storage_ref(value: String): Consequence[Option[BlobStorageRef]] = {
    val trimmed = value.trim
    val scheme = trimmed.indexOf("://")
    if (scheme <= 0)
      Consequence.argumentInvalid(s"invalid blob storage ref: $value")
    else {
      val store = trimmed.substring(0, scheme)
      val rest = trimmed.substring(scheme + 3)
      val hash = rest.indexOf("#")
      val path = if (hash >= 0) rest.substring(0, hash) else rest
      val version = if (hash >= 0) Some(rest.substring(hash + 1)).filter(_.nonEmpty) else None
      val slash = path.indexOf("/")
      if (slash <= 0 || slash == path.length - 1)
        Consequence.argumentInvalid(s"invalid blob storage ref: $value")
      else
        Consequence.success(Some(BlobStorageRef(store, path.substring(0, slash), path.substring(slash + 1), version)))
    }
  }

  private def _string(record: Record, names: String*): Option[String] =
    names.iterator.flatMap(record.getAny).map(_.toString.trim).find(_.nonEmpty)

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
