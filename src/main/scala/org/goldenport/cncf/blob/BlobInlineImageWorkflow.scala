package org.goldenport.cncf.blob

import java.nio.file.Paths
import java.time.Instant
import scala.util.matching.Regex
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.association.{AssociationBindingWorkflow, AssociationDomain, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.association.AssociationTargetValidator
import org.goldenport.cncf.html.{HtmlFragment, HtmlImageOccurrence as HtmlInlineImage, HtmlTree}
import org.goldenport.cncf.id.{TextusUrn, UrnRepository}
import org.goldenport.datatype.{ContentType, FileBundle, MimeType}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Common inline-image normalization for content-bearing entities.
 *
 * v1 supports HTML fragments. Markdown and SmartDox are reserved content
 * markup values for the next content-format slice.
 *
 * @since   May.  3, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
enum InlineImageMarkup {
  case HtmlFragment
  case Markdown
  case SmartDox
}

enum InlineImageExternalPolicy {
  case MetadataOnly
  case Preserve
  case Reject
}

enum InlineImageSourceKind {
  case LocalRelative
  case TextusUrn
  case BlobContentUrl
  case ExternalUrl
}

final case class InlineImageContent(
  markup: InlineImageMarkup,
  text: String,
  fileBundle: Option[FileBundle] = None,
  externalPolicy: InlineImageExternalPolicy = InlineImageExternalPolicy.MetadataOnly
)

final case class InlineImageOccurrence(
  index: Int,
  originalSrc: String,
  normalizedSrc: String,
  imageId: EntityId,
  blobId: EntityId,
  alt: Option[String],
  title: Option[String],
  sortOrder: Int,
  sourceKind: InlineImageSourceKind,
  createdBlob: Boolean,
  createdMedia: Boolean
)

final case class InlineImageNormalizeResult(
  markup: InlineImageMarkup,
  originalText: String,
  normalizedText: String,
  occurrences: Vector[InlineImageOccurrence]
) {
  def toRecord: Record =
    Record.dataAuto(
      "markup" -> markup.toString,
      "originalText" -> originalText,
      "normalizedText" -> normalizedText,
      "occurrences" -> occurrences.map { x =>
        Record.dataAuto(
          "index" -> x.index,
          "originalSrc" -> x.originalSrc,
          "normalizedSrc" -> x.normalizedSrc,
          "imageId" -> x.imageId,
          "blobId" -> x.blobId,
          "alt" -> x.alt,
          "title" -> x.title,
          "sortOrder" -> x.sortOrder,
          "sourceKind" -> x.sourceKind.toString,
          "createdBlob" -> x.createdBlob,
          "createdMedia" -> x.createdMedia
        )
      }
    )
}

final case class InlineImageAttachResult(
  sourceEntityId: String,
  associations: Vector[Record]
) {
  def toRecord: Record =
    Record.dataAuto(
      "sourceEntityId" -> sourceEntityId,
      "associations" -> associations,
      "associationCount" -> associations.size
    )
}

final class BlobInlineImageWorkflow(
  component: Component,
  repository: BlobRepository = BlobRepository.entityStore(),
  mediaRepository: MediaRepository = MediaRepository.entityStore(),
  associations: AssociationRepository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault),
  urnRepository: UrnRepository = UrnRepository.from(MediaUrnResolver.all :+ new BlobUrnResolver)
) {
  private val associationWorkflow =
    AssociationBindingWorkflow(
      associations,
      AssociationStoragePolicy.blobAttachmentDefault,
      BlobInlineImageWorkflow.blobTargetValidator(repository)
    )

  def normalize(
    content: InlineImageContent
  )(using ExecutionContext): Consequence[InlineImageNormalizeResult] =
    content.markup match {
      case InlineImageMarkup.HtmlFragment =>
        _normalize_html(content)
      case InlineImageMarkup.Markdown =>
        Consequence.operationInvalid("GFM-compatible Markdown inline image normalization is reserved for the next content-format slice")
      case InlineImageMarkup.SmartDox =>
        Consequence.operationInvalid("SmartDox inline image normalization is reserved for the next content-format slice")
    }

  def attachInlineImages(
    sourceEntityId: String,
    occurrences: Vector[InlineImageOccurrence]
  )(using ExecutionContext): Consequence[InlineImageAttachResult] =
    occurrences.foldLeft(Consequence.success(Vector.empty[Record])) { (z, occurrence) =>
      z.flatMap { records =>
        associationWorkflow.attachExistingTargetResult(
          sourceEntityId = sourceEntityId,
          domain = AssociationDomain.BlobAttachment,
          targetKind = Some("blob"),
          targetEntityId = occurrence.blobId,
          role = "inline",
          sortOrder = Some(occurrence.sortOrder)
        ).map { result =>
          records :+ AssociationRecordCodec.toRecord(result.association)
        }
      }
    }.map(InlineImageAttachResult(sourceEntityId, _))

  def renderTextusBlobUrns(
    html: String
  )(using ExecutionContext): Consequence[String] =
    _parse_html_fragment(html).flatMap { fragment =>
      _render_textus_blob_urns(fragment).map(_.render)
    }

  private def _normalize_html(
    content: InlineImageContent
  )(using ExecutionContext): Consequence[InlineImageNormalizeResult] =
    for {
      fragment <- _parse_html_fragment(content.text)
      view <- content.fileBundle
        .map(_.toFileSystemView.map(Some(_)))
        .getOrElse(Consequence.success(None))
      resolved <- _normalize_images(fragment.images, view, content.externalPolicy, Vector.empty)
      byIndex = resolved.map(x => x.index -> x.normalizedSrc).toMap
      rewritten = fragment.rewriteImageSources(img => byIndex.get(img.index))
    } yield InlineImageNormalizeResult(
      markup = content.markup,
      originalText = content.text,
      normalizedText = rewritten.render,
      occurrences = resolved
    )

  private def _parse_html_fragment(
    html: String
  ): Consequence[HtmlFragment] =
    HtmlTree.parse(html).map(document => HtmlFragment(document.nodes))

  private def _normalize_image(
    index: Int,
    src: String,
    alt: Option[String],
    title: Option[String],
    fileView: Option[org.goldenport.vfs.FileSystemView],
    externalPolicy: InlineImageExternalPolicy
  )(using ExecutionContext): Consequence[Option[InlineImageOccurrence]] = {
    val source = src.trim
    _resolve_existing_image(source, alt, title).flatMap {
      case Some((media, blob, createdMedia)) =>
        Consequence.success(Some(InlineImageOccurrence(index, src, _image_urn(media.id).print, media.id, blob.id, alt, title, index, _existing_kind(source), createdBlob = false, createdMedia = createdMedia)))
      case None =>
        if (_is_local_relative(source))
          _register_local_blob(index, src, alt, title, fileView).map(Some(_))
        else if (_is_external_url(source))
          _register_external_blob(index, src, alt, title, externalPolicy)
        else
          Consequence.argumentInvalid(s"unsupported inline image source: ${src}")
    }
  }

  private def _normalize_images(
    images: Vector[HtmlInlineImage],
    fileView: Option[org.goldenport.vfs.FileSystemView],
    externalPolicy: InlineImageExternalPolicy,
    acc: Vector[InlineImageOccurrence]
  )(using ExecutionContext): Consequence[Vector[InlineImageOccurrence]] =
    images match {
      case head +: tail =>
        _normalize_image(head.index, head.src, head.alt, head.title, fileView, externalPolicy).flatMap { occurrence =>
          _normalize_images(tail, fileView, externalPolicy, occurrence.map(acc :+ _).getOrElse(acc))
        }.recoverWith { conclusion =>
          _cleanup_created_blobs(acc).flatMap(_ => Consequence.Failure[Vector[InlineImageOccurrence]](conclusion))
        }
      case _ =>
        Consequence.success(acc)
    }

  private def _register_local_blob(
    index: Int,
    src: String,
    alt: Option[String],
    title: Option[String],
    fileView: Option[org.goldenport.vfs.FileSystemView]
  )(using ExecutionContext): Consequence[InlineImageOccurrence] =
    fileView match {
      case Some(view) =>
        for {
          file <- view.getFile(Paths.get(src)).flatMap(_.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"inline image file not found in filebundle: ${src}")))
          contentType <- _image_content_type(file.filename, file.mimeType)
          id = summon[ExecutionContext].idGeneration.entityId(BlobRepository.CollectionId)
          blob <- BlobPayloadSupport.putManagedPayload(
            component = component,
            id = id,
            kind = BlobKind.Image,
            filename = Some(file.filename),
            contentType = contentType,
            payload = file.content.promoteToBinary(),
            attributes = Map("sourcePath" -> src)
          )
          media <- _ensure_image_for_created_blob(blob, alt, title)
        } yield InlineImageOccurrence(index, src, _image_urn(media.media.id).print, media.media.id, blob.id, alt, title, index, InlineImageSourceKind.LocalRelative, createdBlob = true, createdMedia = media.created)
      case None =>
        Consequence.argumentMissing(s"fileBundle for inline image source: ${src}")
    }

  private def _register_external_blob(
    index: Int,
    src: String,
    alt: Option[String],
    title: Option[String],
    externalPolicy: InlineImageExternalPolicy
  )(using ExecutionContext): Consequence[Option[InlineImageOccurrence]] =
    externalPolicy match {
      case InlineImageExternalPolicy.Preserve =>
        Consequence.success(None)
      case InlineImageExternalPolicy.Reject =>
        Consequence.argumentPolicyViolation("inlineImage.src", "external-image-policy", "non-external image", src)
      case InlineImageExternalPolicy.MetadataOnly =>
        for {
          normalized <- BlobExternalUrlPolicy.normalize(src)
          id = summon[ExecutionContext].idGeneration.entityId(BlobRepository.CollectionId)
          blob <- repository.create(BlobCreate(
            id = id,
            kind = BlobKind.Image,
            sourceMode = BlobSourceMode.ExternalUrl,
            filename = _external_filename(normalized),
            contentType = None,
            byteSize = None,
            digest = None,
            storageRef = None,
            externalUrl = Some(normalized),
            accessUrl = BlobAccessUrl(
              displayUrl = normalized,
              downloadUrl = normalized,
              urlSource = BlobAccessUrlSource.Backend
            ),
            attributes = Map("sourceUrl" -> normalized)
          ))
          media <- _ensure_image_for_created_blob(blob, alt, title)
        } yield Some(InlineImageOccurrence(index, src, _image_urn(media.media.id).print, media.media.id, blob.id, alt, title, index, InlineImageSourceKind.ExternalUrl, createdBlob = true, createdMedia = media.created))
    }

  private def _ensure_image_for_created_blob(
    blob: Blob,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[MediaEnsureResult] =
    _ensure_image_for_blob(blob, alt, title).recoverWith { conclusion =>
      _delete_created_blob(blob.id).flatMap { _ =>
        Consequence.Failure[MediaEnsureResult](conclusion)
      }
    }

  private def _ensure_image_for_blob(
    blob: Blob,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[MediaEnsureResult] =
    _validate_image_blob(blob).flatMap { _ =>
      mediaRepository.ensureImageForBlobResult(blob, alt, title)
    }

  private def _validate_image_blob(
    blob: Blob
  ): Consequence[Unit] =
    if (blob.kind != BlobKind.Image)
      Consequence.argumentPolicyViolation("inlineImage.blobKind", "blob-kind", BlobKind.Image.print, blob.kind.print)
    else
      blob.contentType match {
        case Some(contentType) if !_is_image_content_type(contentType) =>
          Consequence.argumentPolicyViolation("inlineImage.contentType", "mime-kind", "image/*", contentType.header)
        case _ =>
          Consequence.unit
      }

  private def _cleanup_created_blobs(
    occurrences: Vector[InlineImageOccurrence]
  )(using ExecutionContext): Consequence[Unit] =
    occurrences.foldLeft(Consequence.unit) { (z, occurrence) =>
      z.flatMap { _ =>
        val cleanupMedia =
          if (occurrence.createdMedia)
            mediaRepository.delete(MediaEntity(
              id = occurrence.imageId,
              kind = MediaKind.Image,
              blobId = occurrence.blobId,
              name = None,
              title = None,
              contentType = None,
              filename = None,
              byteSize = None,
              digest = None,
              accessUrl = None,
              createdAt = Instant.EPOCH,
              updatedAt = Instant.EPOCH
            )).recover(_ => ())
          else
            Consequence.unit
        cleanupMedia.flatMap { _ =>
          if (occurrence.createdBlob)
            _delete_created_blob(occurrence.blobId)
          else
            Consequence.unit
        }
      }
    }

  private def _delete_created_blob(
    id: EntityId
  )(using ExecutionContext): Consequence[Unit] =
    repository.get(id).flatMap { blob =>
      repository.delete(id).recover(_ => ()).flatMap { _ =>
        blob.sourceMode match {
          case BlobSourceMode.Managed =>
            blob.storageRef
              .map(ref => BlobPayloadSupport.service(component).flatMap(_.blobStore.delete(ref)).recover(_ => ()))
              .getOrElse(Consequence.unit)
          case BlobSourceMode.ExternalUrl =>
            Consequence.unit
        }
      }
    }.recover(_ => ())

  private def _render_textus_blob_urns(
    fragment: HtmlFragment
  )(using ExecutionContext): Consequence[HtmlFragment] =
    fragment.images.foldLeft(Consequence.success(Map.empty[Int, String])) {
      case (z, image) =>
        z.flatMap { map =>
          TextusUrn.parseOption(image.src.trim) match {
            case Some(urn) if urn.kind == TextusUrn.ImageKind =>
              _resolve_image_urn(urn).flatMap {
                case Some((_, blob)) => Consequence.success(map + (image.index -> _display_url(blob)))
                case None => Consequence.argumentInvalid(s"Image URN does not resolve: ${urn.print}")
              }
            case Some(urn) if urn.kind == TextusUrn.BlobKind =>
              _resolve_blob_urn(urn).flatMap {
                case Some(blob) => Consequence.success(map + (image.index -> _display_url(blob)))
                case None => Consequence.argumentInvalid(s"Blob URN does not resolve: ${urn.print}")
              }
            case _ =>
              Consequence.success(map)
          }
        }
    }.map { byIndex =>
      fragment.rewriteImageSources(img => byIndex.get(img.index))
    }

  private def _resolve_existing_image(
    src: String,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[Option[(MediaEntity, Blob, Boolean)]] =
    TextusUrn.parseOption(src) match {
      case Some(urn) if urn.kind == TextusUrn.ImageKind =>
        _resolve_image_urn(urn).map(_.map { case (media, blob) => (media, blob, false) })
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        _resolve_blob_urn(urn).flatMap {
          case Some(blob) => _ensure_image_for_blob(blob, alt, title).map(x => Some((x.media, blob, x.created)))
          case None => Consequence.success(None)
        }
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(src) match {
          case Some(value) =>
            _resolve_blob_value(value).flatMap {
              case Some(blob) => _ensure_image_for_blob(blob, alt, title).map(x => Some((x.media, blob, x.created)))
              case None => Consequence.success(None)
            }
          case None => Consequence.success(None)
        }
    }

  private def _resolve_image_urn(
    urn: TextusUrn
  )(using ExecutionContext): Consequence[Option[(MediaEntity, Blob)]] =
    urnRepository.resolve(urn).flatMap {
      case Some(resolution) if resolution.collection == MediaEntityCollections.Image =>
        mediaRepository.get(MediaKind.Image, resolution.entityId).flatMap { media =>
          repository.get(media.blobId).map(blob => Some(media -> blob))
        }
      case Some(_) =>
        Consequence.argumentInvalid(s"Textus URN is not an Image URN: ${urn.print}")
      case None =>
        Consequence.success(None)
    }

  private def _resolve_blob_urn(
    urn: TextusUrn
  )(using ExecutionContext): Consequence[Option[Blob]] =
    urnRepository.resolve(urn).flatMap {
      case Some(resolution) if resolution.collection == BlobRepository.CollectionId =>
        repository.get(resolution.entityId).map(Some(_))
      case Some(_) =>
        Consequence.argumentInvalid(s"Textus URN is not a Blob URN: ${urn.print}")
      case None =>
        Consequence.success(None)
    }

  private def _resolve_blob_value(
    value: String
  )(using ExecutionContext): Consequence[Option[Blob]] = {
    EntityId.parse(value).toOption match {
      case Some(id) if id.collection.name == BlobRepository.CollectionId.name =>
        repository.get(_blob_entity_id(id)).map(Some(_))
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _resolve_blob_urn(TextusUrn.blob(value))
    }
  }

  private def _existing_kind(src: String): InlineImageSourceKind =
    if (TextusUrn.parseOption(src).exists(urn => urn.kind == TextusUrn.BlobKind || urn.kind == TextusUrn.ImageKind))
      InlineImageSourceKind.TextusUrn
    else
      InlineImageSourceKind.BlobContentUrl

  private def _image_urn(id: EntityId): TextusUrn =
    TextusUrn.image(id.parts.entropy)

  private def _blob_urn(id: EntityId): TextusUrn =
    TextusUrn.blob(id.parts.entropy)

  private def _blob_entity_id(id: EntityId): EntityId =
    if (id.collection == BlobRepository.CollectionId)
      id
    else
      id.copy(collection = BlobRepository.CollectionId)

  private def _display_url(blob: Blob): String =
    blob.sourceMode match {
      case BlobSourceMode.ExternalUrl => blob.accessUrl.displayUrl
      case BlobSourceMode.Managed => BlobUrl.cncfRoute(blob.id).displayUrl
    }

  private def _blob_content_value(src: String): Option[String] =
    BlobContentPattern.findFirstMatchIn(src).map(_.group(1))

  private def _is_local_relative(src: String): Boolean =
    src.nonEmpty &&
      !src.startsWith("/") &&
      !src.startsWith("#") &&
      !src.startsWith("//") &&
      !src.contains(":")

  private def _is_external_url(src: String): Boolean =
    src.startsWith("http://") || src.startsWith("https://")

  private def _image_content_type(
    filename: String,
    mimeType: Option[MimeType]
  ): Consequence[ContentType] = {
    val ct = mimeType.map(ContentType(_, None)).orElse(_content_type_from_filename(filename))
      .getOrElse(ContentType.APPLICATION_OCTET_STREAM)
    if (_is_image_content_type(ct))
      Consequence.success(ct)
    else
      Consequence.argumentPolicyViolation("inlineImage.contentType", "mime-kind", "image/*", ct.header)
  }

  private def _is_image_content_type(contentType: ContentType): Boolean =
    contentType.mimeType.print.toLowerCase(java.util.Locale.ROOT).startsWith("image/")

  private def _content_type_from_filename(filename: String): Option[ContentType] = {
    val lower = filename.toLowerCase(java.util.Locale.ROOT)
    if (lower.endsWith(".png")) Some(ContentType.IMAGE_PNG)
    else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) Some(ContentType.IMAGE_JPEG)
    else if (lower.endsWith(".gif")) Some(ContentType.IMAGE_GIF)
    else if (lower.endsWith(".svg") || lower.endsWith(".svgz")) Some(ContentType.IMAGE_SVG)
    else None
  }

  private def _external_filename(url: String): Option[String] =
    url.split('/').lastOption.map(_.takeWhile(ch => ch != '?' && ch != '#')).filter(_.nonEmpty)

  private val BlobContentPattern: Regex =
    """/web/blob/content/([^"'?\s<>#]+)""".r
}

object BlobInlineImageWorkflow {
  def blobTargetValidator(
    repository: BlobRepository
  ): AssociationTargetValidator =
    new AssociationTargetValidator {
      def validate(
        targetKind: Option[String],
        id: EntityId
      )(using ExecutionContext): Consequence[Unit] =
        targetKind match {
          case Some(kind) if kind != "blob" =>
            Consequence.argumentInvalid(s"association target kind mismatch: expected $kind but was ${id.collection.name}")
          case _ =>
            repository.get(id).map(_ => ())
        }
    }
}
