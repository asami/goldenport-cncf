package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.cncf.association.{Association, AssociationBindingAttachResult, AssociationBindingWorkflow, AssociationDomain, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.protocol.Request
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Application-facing helper for attaching uploaded or existing Blob assets to
 * entities in the same create/update request flow.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final case class BlobUploadPart(
  role: String,
  payload: BinaryBag,
  kind: BlobKind,
  filename: Option[String],
  contentType: ContentType,
  sortOrder: Option[Int]
)

final case class BlobReferencePart(
  role: String,
  id: EntityId,
  sortOrder: Option[Int]
)

final case class BlobAttachmentSummary(
  sourceEntityId: String,
  uploaded: Vector[BlobMetadata],
  referenced: Vector[BlobMetadata],
  associations: Vector[Record],
  compensated: Boolean = false
) {
  def toRecord: Record =
    Record.dataAuto(
      "sourceEntityId" -> sourceEntityId,
      "uploaded" -> uploaded.map(_.toRecord),
      "referenced" -> referenced.map(_.toRecord),
      "associations" -> associations,
      "uploadedCount" -> uploaded.size,
      "referencedCount" -> referenced.size,
      "associationCount" -> associations.size,
      "compensated" -> compensated
    )
}

final case class BlobAttachmentRequest(
  uploads: Vector[BlobUploadPart],
  references: Vector[BlobReferencePart]
) {
  def isEmpty: Boolean = uploads.isEmpty && references.isEmpty
}

final class BlobAttachmentWorkflow(
  store: BlobStore,
  repository: BlobRepository,
  associations: AssociationRepository
) {
  private val associationWorkflow: AssociationBindingWorkflow =
    AssociationBindingWorkflow(associations, AssociationStoragePolicy.blobAttachmentDefault)

  def extract(request: Request): Consequence[BlobAttachmentRequest] =
    BlobAttachmentWorkflow.extract(request)

  def attachToEntity(
    sourceEntityId: String,
    request: Request
  )(using ExecutionContext): Consequence[BlobAttachmentSummary] =
    extract(request).flatMap(attachToEntity(sourceEntityId, _))

  def attachToEntity(
    sourceEntityId: String,
    request: BlobAttachmentRequest
  )(using ExecutionContext): Consequence[BlobAttachmentSummary] =
    _register_uploads(request.uploads).flatMap { uploaded =>
      val uploadRefs = uploaded.zip(request.uploads).map { case (blob, part) =>
        BlobReferencePart(part.role, blob.id, part.sortOrder)
      }
      _attach_references(sourceEntityId, uploadRefs).flatMap { case (_, uploadedAssociations) =>
        _attach_references(sourceEntityId, request.references).flatMap { case (referenced, referencedAssociations) =>
          Consequence.success(BlobAttachmentSummary(
            sourceEntityId = sourceEntityId,
            uploaded = uploaded.map(_.metadata),
            referenced = referenced.map(_.metadata),
            associations = (uploadedAssociations ++ referencedAssociations).map(x => AssociationRecordCodec.toRecord(x.association))
          ))
        }.recoverWith { conclusion =>
          _cleanup_associations(uploadedAssociations)
            .flatMap(_ => _cleanup_uploaded(uploaded))
            .flatMap(_ => Consequence.Failure[BlobAttachmentSummary](conclusion))
        }
      }.recoverWith { conclusion =>
        _cleanup_uploaded(uploaded)
          .flatMap(_ => Consequence.Failure[BlobAttachmentSummary](conclusion))
      }
    }

  def createEntityWithBlobAttachments[A](
    request: Request
  )(
    create: => Consequence[A],
    entityId: A => String,
    compensateEntity: A => Consequence[Unit] = (_: A) => Consequence.unit
  )(using ExecutionContext): Consequence[(A, BlobAttachmentSummary)] =
    create.flatMap { entity =>
      attachToEntity(entityId(entity), request).map(entity -> _).recoverWith { conclusion =>
        compensateEntity(entity).recover(_ => ()).flatMap(_ => Consequence.Failure[(A, BlobAttachmentSummary)](conclusion))
      }
    }

  def updateEntityWithBlobAttachments[A](
    request: Request
  )(
    update: => Consequence[A],
    entityId: A => String
  )(using ExecutionContext): Consequence[(A, BlobAttachmentSummary)] =
    update.flatMap { entity =>
      attachToEntity(entityId(entity), request).map(entity -> _)
    }

  private def _register_uploads(
    parts: Vector[BlobUploadPart]
  )(using ExecutionContext): Consequence[Vector[Blob]] =
    parts.foldLeft(Consequence.success(Vector.empty[Blob])) { (z, part) =>
      z.flatMap { acc =>
        _register_upload(part).map(blob => acc :+ blob).recoverWith { conclusion =>
          _cleanup_uploaded(acc).flatMap(_ => Consequence.Failure[Vector[Blob]](conclusion))
        }
      }
    }

  private def _register_upload(
    part: BlobUploadPart
  )(using ExecutionContext): Consequence[Blob] = {
    val id = EntityId(BlobRepository.CollectionId.major, BlobRepository.CollectionId.minor, BlobRepository.CollectionId)
    store.put(
      BlobPutRequest(
        id = id,
        kind = part.kind,
        filename = part.filename,
        contentType = part.contentType
      ),
      part.payload
    ).flatMap { result =>
      repository.create(
        BlobCreate(
          id = result.id,
          kind = part.kind,
          sourceMode = BlobSourceMode.Managed,
          filename = part.filename,
          contentType = Some(result.contentType),
          byteSize = Some(result.byteSize),
          digest = Some(result.digest),
          storageRef = Some(result.storageRef),
          externalUrl = None,
          accessUrl = _managed_blob_access_url(result)
        )
      ).recoverWith { conclusion =>
        store.delete(result.storageRef).recover(_ => ()).flatMap(_ => Consequence.Failure[Blob](conclusion))
      }
    }
  }

  private def _managed_blob_access_url(result: BlobPutResult): BlobAccessUrl =
    if (result.accessUrl.urlSource == BlobAccessUrlSource.Backend && result.accessUrl.displayUrl.nonEmpty)
      result.accessUrl
    else
      BlobUrl.cncfRoute(result.id)

  private def _attach_references(
    sourceEntityId: String,
    parts: Vector[BlobReferencePart]
  )(using ExecutionContext): Consequence[(Vector[Blob], Vector[AssociationBindingAttachResult])] =
    parts.foldLeft(Consequence.success((Vector.empty[Blob], Vector.empty[AssociationBindingAttachResult]))) { (z, part) =>
      z.flatMap { case (blobs, created) =>
        repository.get(part.id).flatMap { blob =>
          _attach_blob(sourceEntityId, blob.id, part.role, part.sortOrder).map { association =>
            (blobs :+ blob, created :+ association)
          }
        }.recoverWith { conclusion =>
          _cleanup_associations(created).flatMap(_ => Consequence.Failure[(Vector[Blob], Vector[AssociationBindingAttachResult])](conclusion))
        }
      }
    }

  private def _attach_blob(
    sourceEntityId: String,
    id: EntityId,
    role: String,
    sortOrder: Option[Int]
  )(using ExecutionContext): Consequence[AssociationBindingAttachResult] =
    associationWorkflow.attachExistingTargetResult(
      sourceEntityId = sourceEntityId,
      domain = AssociationDomain.BlobAttachment,
      targetKind = Some("blob"),
      targetEntityId = id,
      role = role,
      sortOrder = sortOrder
    )

  private def _cleanup_uploaded(
    blobs: Vector[Blob]
  )(using ExecutionContext): Consequence[Unit] =
    blobs.foldLeft(Consequence.unit) { (z, blob) =>
      z.flatMap { _ =>
        repository.delete(blob.id).recover(_ => ()).flatMap { _ =>
          blob.storageRef match {
            case Some(ref) => store.delete(ref).recover(_ => ())
            case None => Consequence.unit
          }
        }
      }
    }

  private def _cleanup_associations(
    values: Vector[AssociationBindingAttachResult]
  )(using ExecutionContext): Consequence[Unit] =
    values.filter(_.created).map(_.association).foldLeft(Consequence.unit) { (z, association) =>
      z.flatMap(_ => associations.delete(association).recover(_ => ()))
    }
}

object BlobAttachmentWorkflow {
  def extract(request: Request): Consequence[BlobAttachmentRequest] = {
    val values = _values(request)
    for {
      uploads <- _uploads(values)
      refs <- _references(values)
      structured <- _image_attachments(values)
    } yield BlobAttachmentRequest(uploads ++ structured.uploads, refs ++ structured.references)
  }

  def extract(
    request: Request,
    acceptsUpload: Boolean,
    acceptsExistingBlobId: Boolean
  ): Consequence[BlobAttachmentRequest] =
    extract(request).flatMap { attachment =>
      if (!acceptsUpload && attachment.uploads.nonEmpty)
        Consequence.argumentInvalid("imageBinding does not accept upload payloads")
      else if (!acceptsExistingBlobId && attachment.references.nonEmpty)
        Consequence.argumentInvalid("imageBinding does not accept existing Blob ids")
      else
        Consequence.success(attachment)
    }

  private def _values(
    request: Request
  ): Vector[(String, Any)] =
    (request.arguments.map(x => x.name -> x.value) ++ request.properties.map(x => x.name -> x.value)).toVector

  private def _uploads(
    values: Vector[(String, Any)]
  ): Consequence[Vector[BlobUploadPart]] =
    values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[BlobUploadPart])) { (z, entry) =>
      z.flatMap { acc =>
        val ((name, value), position) = entry
        _upload_name(name) match {
          case Some((role, index)) =>
            _binary(value).flatMap { case (payload, contenttype) =>
              val key = _key("blob", role, index)
              _kind(values, key, contenttype).map { kind =>
                acc :+ BlobUploadPart(
                  role = role,
                  payload = payload,
                  kind = kind,
                  filename = _string(values, s"$key.filename", s"$key.fileName"),
                  contentType = contenttype,
                  sortOrder = _int(values, s"$key.sortOrder", s"$key.sort_order").orElse(Some(position))
                )
              }
            }
          case None =>
            Consequence.success(acc)
        }
      }
    }

  private def _references(
    values: Vector[(String, Any)]
  ): Consequence[Vector[BlobReferencePart]] =
    values.zipWithIndex.foldLeft(Consequence.success(Vector.empty[BlobReferencePart])) { (z, entry) =>
      z.flatMap { acc =>
        val ((name, value), position) = entry
        _reference_name(name) match {
          case Some((role, index)) =>
            val key = _key("blobId", role, index)
            EntityId.parse(value.toString.trim).map { id =>
              acc :+ BlobReferencePart(
                role = role,
                id = id,
                sortOrder = _int(values, s"$key.sortOrder", s"$key.sort_order").orElse(Some(position))
              )
            }
          case None =>
            Consequence.success(acc)
        }
      }
    }

  private def _image_attachments(
    values: Vector[(String, Any)]
  ): Consequence[BlobAttachmentRequest] = {
    val rows = _image_attachment_rows(values)
    rows.foldLeft(Consequence.success(BlobAttachmentRequest(Vector.empty, Vector.empty))) { (z, row) =>
      z.flatMap { acc =>
        _image_attachment(row).map { part =>
          BlobAttachmentRequest(
            uploads = acc.uploads ++ part.uploads,
            references = acc.references ++ part.references
          )
        }
      }
    }
  }

  private def _image_attachment_rows(
    values: Vector[(String, Any)]
  ): Vector[_ImageAttachmentRow] = {
    val rows = scala.collection.mutable.Map.empty[Int, Vector[(String, Any, Int)]]
    values.zipWithIndex.foreach { case ((name, value), position) =>
      _image_attachment_name(name).foreach { case (index, field) =>
        val current = rows.getOrElse(index, Vector.empty)
        rows.update(index, current :+ (field, value, position))
      }
    }
    rows.toVector.sortBy(_._1).map { case (index, fields) =>
      _ImageAttachmentRow(index, fields)
    }
  }

  private def _image_attachment(
    row: _ImageAttachmentRow
  ): Consequence[BlobAttachmentRequest] =
    if (row.isEmpty)
      Consequence.success(BlobAttachmentRequest(Vector.empty, Vector.empty))
    else {
      row.string("role") match {
        case None =>
          Consequence.argumentInvalid(s"imageAttachments.${row.index}.role is required")
        case Some(role) =>
          (row.upload, row.string("blobId")) match {
            case (Some(_), Some(_)) =>
              Consequence.argumentInvalid(s"imageAttachments.${row.index} cannot specify both file and blobId")
            case (None, None) =>
              Consequence.argumentInvalid(s"imageAttachments.${row.index} requires file or blobId")
            case (Some(value), None) =>
              _binary(value).flatMap { case (payload, contenttype) =>
                _kind(row.fields, s"imageAttachments.${row.index}", contenttype).map { kind =>
                  BlobAttachmentRequest(
                    uploads = Vector(BlobUploadPart(
                      role = role,
                      payload = payload,
                      kind = kind,
                      filename = row.string("filename").orElse(row.string("file.filename")).orElse(row.string("file.fileName")),
                      contentType = contenttype,
                      sortOrder = row.int("sortOrder", "sort_order").orElse(Some(row.position))
                    )),
                    references = Vector.empty
                  )
                }
              }
            case (None, Some(blobId)) =>
              EntityId.parse(blobId).map { id =>
                BlobAttachmentRequest(
                  uploads = Vector.empty,
                  references = Vector(BlobReferencePart(role, id, row.int("sortOrder", "sort_order").orElse(Some(row.position))))
                )
              }
          }
      }
    }

  private def _image_attachment_name(
    name: String
  ): Option[(Int, String)] = {
    val prefix = "imageAttachments."
    if (!name.startsWith(prefix))
      None
    else {
      val rest = name.drop(prefix.length)
      val i = rest.indexOf('.')
      if (i <= 0 || i == rest.length - 1)
        None
      else {
        rest.take(i).toIntOption.map(_ -> rest.drop(i + 1))
      }
    }
  }

  private def _upload_name(name: String): Option[(String, Option[Int])] =
    _role_index(name, "blob")

  private def _reference_name(name: String): Option[(String, Option[Int])] =
    _role_index(name, "blobId")

  private def _role_index(
    name: String,
    prefix: String
  ): Option[(String, Option[Int])] = {
    val p = s"$prefix."
    if (!name.startsWith(p))
      None
    else {
      val suffix = name.drop(p.length)
      if (suffix.isEmpty || suffix.contains(".kind") || suffix.contains(".filename") || suffix.contains(".fileName") || suffix.contains(".sortOrder") || suffix.contains(".sort_order"))
        None
      else {
        val parts = suffix.split("\\.").toVector
        parts match {
          case Vector(role) if role.nonEmpty =>
            Some(role -> None)
          case Vector(role, index) if role.nonEmpty && index.forall(_.isDigit) =>
            Some(role -> index.toIntOption)
          case _ =>
            None
        }
      }
    }
  }

  private def _key(
    prefix: String,
    role: String,
    index: Option[Int]
  ): String =
    index.fold(s"$prefix.$role")(i => s"$prefix.$role.$i")

  private def _binary(value: Any): Consequence[(BinaryBag, ContentType)] =
    value match {
      case m: MimeBody =>
        Consequence.success(m.value.promoteToBinary() -> m.contentType)
      case b: BinaryBag =>
        Consequence.success(b -> ContentType.APPLICATION_OCTET_STREAM)
      case b: Bag =>
        Consequence.success(b.promoteToBinary() -> ContentType.APPLICATION_OCTET_STREAM)
      case bytes: Array[Byte] =>
        Consequence.success(Bag.binary(bytes) -> ContentType.APPLICATION_OCTET_STREAM)
      case other =>
        Consequence.argumentInvalid(s"unsupported blob upload type: ${other.getClass.getName}")
    }

  private def _kind(
    values: Vector[(String, Any)],
    key: String,
    contenttype: ContentType
  ): Consequence[BlobKind] =
    _string(values, s"$key.kind") match {
      case Some(value) =>
        BlobKind.parse(value)
      case None =>
        Consequence.success(_infer_kind(contenttype))
    }

  private def _infer_kind(contenttype: ContentType): BlobKind = {
    val mime = contenttype.mimeType.print.toLowerCase(java.util.Locale.ROOT)
    if (mime.startsWith("image/"))
      BlobKind.Image
    else if (mime.startsWith("video/"))
      BlobKind.Video
    else if (mime == "application/octet-stream")
      BlobKind.Binary
    else
      BlobKind.Attachment
  }

  private def _string(
    values: Vector[(String, Any)],
    names: String*
  ): Option[String] =
    names.iterator.flatMap(name => values.collectFirst {
      case (key, value) if key == name => value.toString.trim
    }).find(_.nonEmpty)

  private def _int(
    values: Vector[(String, Any)],
    names: String*
  ): Option[Int] =
    _string(values, names*).flatMap(_.toIntOption)

  private final case class _ImageAttachmentRow(
    index: Int,
    values: Vector[(String, Any, Int)]
  ) {
    lazy val fields: Vector[(String, Any)] =
      values.map { case (field, value, _) => s"imageAttachments.${index}.${field}" -> value }

    def position: Int =
      values.map(_._3).minOption.getOrElse(index)

    def string(names: String*): Option[String] =
      names.iterator.flatMap { name =>
        values.collectFirst {
          case (field, value, _) if field == name => value.toString.trim
        }
      }.find(_.nonEmpty)

    def int(names: String*): Option[Int] =
      string(names*).flatMap(_.toIntOption)

    def upload: Option[Any] =
      values.collectFirst {
        case ("file", value, _) if !_is_empty_upload(value) => value
      }

    def isEmpty: Boolean =
      values.forall { case (field, value, _) =>
        field == "file" && _is_empty_upload(value) || _is_empty_scalar(value)
      }

    private def _is_empty_scalar(value: Any): Boolean =
      value match {
        case m: MimeBody => _is_empty_upload(m)
        case b: Bag => b.metadata.size.contains(0L)
        case a: Array[Byte] => a.isEmpty
        case other => other.toString.trim.isEmpty
      }

    private def _is_empty_upload(value: Any): Boolean =
      value match {
        case m: MimeBody =>
          m.value.metadata.size.contains(0L)
        case b: Bag =>
          b.metadata.size.contains(0L)
        case a: Array[Byte] =>
          a.isEmpty
        case other =>
          other.toString.trim.isEmpty
      }
  }
}
