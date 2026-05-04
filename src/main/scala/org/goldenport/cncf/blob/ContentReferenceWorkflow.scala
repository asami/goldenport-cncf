package org.goldenport.cncf.blob

import java.nio.file.Paths
import scala.util.matching.Regex
import scala.util.control.NonFatal
import com.vladsch.flexmark.ast.{AutoLink as MarkdownAutoLink, Image as MarkdownImage, ImageRef as MarkdownImageRef, Link as MarkdownLink, LinkRef as MarkdownLinkRef, Reference as MarkdownReferenceDefinition}
import com.vladsch.flexmark.util.ast.Node as MarkdownNode
import com.vladsch.flexmark.util.sequence.BasedSequence
import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationBindingWorkflow, AssociationDomain, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.html.{HtmlAttributeReferenceOccurrence, HtmlAttributeReferenceRewrite, HtmlFragment, HtmlImageOccurrence, HtmlImageRewrite, HtmlLinkOccurrence, HtmlTree}
import org.goldenport.cncf.id.{TextusUrn, UrnRepository}
import org.goldenport.datatype.{ContentType, FileBundle}
import org.goldenport.record.Record
import org.goldenport.value.ContentReferenceOccurrence
import org.simplemodeling.model.datatype.EntityId
import org.smartdox.{DoxReference, DoxReferenceExtractor}
import org.smartdox.parser.Dox2Parser
import org.smartdox.renderer.DoxHtmlRenderer

/*
 * SimpleEntity content reference normalization and attachment workflow.
 *
 * @since   May.  3, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ContentReferenceContent(
  markup: InlineImageMarkup,
  text: String,
  fileBundle: Option[FileBundle] = None,
  externalPolicy: InlineImageExternalPolicy = InlineImageExternalPolicy.MetadataOnly
) {
  def toInlineImageContent: InlineImageContent =
    InlineImageContent(markup, text, fileBundle, externalPolicy)
}

final case class ContentReferenceNormalizeResult(
  markup: InlineImageMarkup,
  originalText: String,
  normalizedText: String,
  references: Vector[ContentReferenceOccurrence]
) {
  def toRecord: Record =
    Record.dataAuto(
      "markup" -> markup.toString,
      "originalText" -> originalText,
      "normalizedText" -> normalizedText,
      "references" -> references.map(_.toRecord()),
      "referenceCount" -> references.size
    )

  def inlineImageCount: Int =
    references.count(x => x.elementKind.contains("img") && x.attributeName.contains("src"))
}

final case class ContentReferenceAttachResult(
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

final class ContentReferenceWorkflow(
  component: Component,
  blobWorkflow: Option[BlobInlineImageWorkflow] = None,
  repository: BlobRepository = BlobRepository.entityStore(),
  mediaRepository: MediaRepository = MediaRepository.entityStore(),
  associations: AssociationRepository = AssociationRepository.entityStore(AssociationStoragePolicy.mediaAttachmentDefault),
  urnRepository: UrnRepository = UrnRepository.from(MediaUrnResolver.all :+ new BlobUrnResolver)
) {
  private lazy val _blob_workflow =
    blobWorkflow.getOrElse(BlobInlineImageWorkflow(component, repository, mediaRepository, AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault), urnRepository))

  private val _association_workflow =
    AssociationBindingWorkflow(
      associations,
      AssociationStoragePolicy.mediaAttachmentDefault,
      ContentReferenceWorkflow.mediaTargetValidator(mediaRepository)
    )

  def normalize(
    content: ContentReferenceContent
  )(using ExecutionContext): Consequence[ContentReferenceNormalizeResult] =
    content.markup match {
      case InlineImageMarkup.HtmlFragment =>
        _normalize_html(content)
      case InlineImageMarkup.Markdown =>
        _normalize_markdown(content)
      case InlineImageMarkup.SmartDox =>
        _normalize_smartdox(content)
    }

  def attachReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _attach_media_references(sourceEntityId, references)

  def validateInlineReferences(
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[Unit] =
    _media_attachment_reference_ids(references).map(_ => ())

  def syncInlineReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _media_attachment_reference_ids(references).flatMap { desired =>
      val desiredKeys = desired.map(_.key).distinct
      for {
        attached <- _attach_media_reference_ids(sourceEntityId, desired)
        _ <- _delete_stale_media_associations(sourceEntityId, desiredKeys, attached.created)
      } yield ContentReferenceAttachResult(sourceEntityId, attached.records)
    }

  private def _parse_html_fragment(
    html: String
  ): Consequence[HtmlFragment] =
    HtmlTree.parse(html).map(document => HtmlFragment(document.nodes))

  private def _normalize_html(
    content: ContentReferenceContent
  )(using ExecutionContext): Consequence[ContentReferenceNormalizeResult] =
    for {
      fragment <- _parse_html_fragment(content.text)
      refs <- _html_attribute_references(content, fragment.references)
      rewritten = fragment.rewriteAttributeReferencesWithComments { ref =>
        HtmlAttributeReferenceRewrite(
          refs.referencesByIndex.get(ref.index).flatMap(_.normalizedRef).filter(_ != ref.value),
          refs.failuresByIndex.get(ref.index).map(ContentReferenceWorkflow.htmlReferenceFailureComment(ref, _))
        )
      }
    } yield ContentReferenceNormalizeResult(
      markup = content.markup,
      originalText = content.text,
      normalizedText = rewritten.render,
      references = refs.referencesByIndex.toVector.sortBy(_._1).map(_._2)
    )

  private def _image_reference(
    occurrence: InlineImageOccurrence
  ): ContentReferenceOccurrence =
    ContentReferenceOccurrence(
      contentField = Some("content"),
      markup = Some("html-fragment"),
      elementKind = Some("img"),
      attributeName = Some("src"),
      occurrenceIndex = occurrence.index,
      originalRef = Some(occurrence.originalSrc),
      normalizedRef = Some(occurrence.normalizedSrc),
      referenceKind = Some("image"),
      urn = Some(occurrence.normalizedSrc),
      targetEntityId = Some(occurrence.imageId.value),
      alt = occurrence.alt,
      title = occurrence.title,
      mediaType = Some("image"),
      sortOrder = Some(occurrence.sortOrder)
    )

  private def _media_reference(
    ref: HtmlAttributeReferenceOccurrence,
    media: NormalizedMediaReference
  ): ContentReferenceOccurrence =
    ContentReferenceOccurrence(
      contentField = Some("content"),
      markup = Some("html-fragment"),
      elementKind = Some(ref.elementKind),
      attributeName = Some(ref.attributeName),
      occurrenceIndex = ref.index,
      originalRef = Some(ref.value),
      normalizedRef = Some(media.normalizedRef),
      referenceKind = Some(media.kind.print),
      urn = Some(media.normalizedRef),
      targetEntityId = Some(media.mediaId.value),
      label = ref.label,
      alt = ref.alt,
      title = ref.title,
      rel = ref.rel,
      mediaType = Some(media.kind.print),
      sortOrder = Some(ref.index)
    )

  private def _metadata_reference(
    ref: HtmlAttributeReferenceOccurrence
  )(using ExecutionContext): Consequence[ContentReferenceOccurrence] = {
    val href = ref.value.trim
    _resolve_reference_ref(href).map {
      case Some((kind, id, urnText)) =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("html-fragment"),
          elementKind = Some(ref.elementKind),
          attributeName = Some(ref.attributeName),
          occurrenceIndex = ref.index,
          originalRef = Some(ref.value),
          normalizedRef = Some(ref.value),
          referenceKind = Some(kind),
          urn = Some(urnText),
          targetEntityId = Some(id.value),
          label = ref.label,
          alt = ref.alt,
          title = ref.title,
          rel = ref.rel,
          mediaType = ref.mediaType,
          sortOrder = Some(ref.index)
        )
      case None =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("html-fragment"),
          elementKind = Some(ref.elementKind),
          attributeName = Some(ref.attributeName),
          occurrenceIndex = ref.index,
          originalRef = Some(ref.value),
          normalizedRef = Some(ref.value),
          referenceKind = Some(_reference_kind(href)),
          urn = TextusUrn.parseOption(href).map(_.print),
          label = ref.label,
          alt = ref.alt,
          title = ref.title,
          rel = ref.rel,
          mediaType = ref.mediaType,
          sortOrder = Some(ref.index)
        )
    }
  }

  private def _html_attribute_references(
    content: ContentReferenceContent,
    refs: Vector[HtmlAttributeReferenceOccurrence]
  )(using ExecutionContext): Consequence[MediaReferenceNormalizeResult] =
    refs.foldLeft(Consequence.success(MediaReferenceNormalizeResult.empty)) {
      case (z, ref) =>
        z.flatMap { state =>
          _html_attribute_reference(content, ref).map {
            case Some(reference) => state.add(ref.index, reference)
            case None => state
          }.recover { conclusion =>
            state.addFailure(ref.index, conclusion.show)
          }
        }
    }

  private def _html_attribute_reference(
    content: ContentReferenceContent,
    ref: HtmlAttributeReferenceOccurrence
  )(using ExecutionContext): Consequence[Option[ContentReferenceOccurrence]] =
    ref.elementKind match {
      case "img" if ref.attributeName == "src" =>
        _normalize_media_reference(content, ref, MediaKind.Image).map(_.map(_media_reference(ref, _)))
      case "video" if ref.attributeName == "src" =>
        _normalize_media_reference(content, ref, MediaKind.Video).map(_.map(_media_reference(ref, _)))
      case "source" if ref.attributeName == "src" =>
        _infer_source_media_kind(ref).flatMap(kind =>
          _normalize_media_reference(content, ref, kind).map(_.map(_media_reference(ref, _)))
        )
      case "a" if ref.attributeName == "href" && ref.download.isDefined =>
        _normalize_media_reference(content, ref, MediaKind.Attachment).flatMap {
          case Some(value) => Consequence.success(Some(_media_reference(ref, value)))
          case None => _metadata_reference(ref).map(Some(_))
        }
      case "a" if ref.attributeName == "href" =>
        _metadata_reference(ref).map(Some(_))
      case _ =>
        Consequence.success(None)
    }

  private def _link_references(
    links: Vector[HtmlLinkOccurrence]
  )(using ExecutionContext): Consequence[Vector[ContentReferenceOccurrence]] =
    links.foldLeft(Consequence.success(Vector.empty[ContentReferenceOccurrence])) {
      case (z, link) =>
        z.flatMap(xs => _link_reference(link).map(xs :+ _))
    }

  private def _link_reference(
    link: HtmlLinkOccurrence
  )(using ExecutionContext): Consequence[ContentReferenceOccurrence] = {
    val href = link.href.trim
    _resolve_reference_ref(href).map {
      case Some((kind, id, urnText)) =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("html-fragment"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = link.index,
          originalRef = Some(link.href),
          normalizedRef = Some(link.href),
          referenceKind = Some(kind),
          urn = Some(urnText),
          targetEntityId = Some(id.value),
          label = link.label,
          title = link.title,
          rel = link.rel,
          sortOrder = Some(link.index)
        )
      case None =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("html-fragment"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = link.index,
          originalRef = Some(link.href),
          normalizedRef = Some(link.href),
          referenceKind = Some(_reference_kind(href)),
          urn = TextusUrn.parseOption(href).map(_.print),
          label = link.label,
          title = link.title,
          rel = link.rel,
          sortOrder = Some(link.index)
        )
    }
  }

  private def _html_image_references(
    content: ContentReferenceContent,
    images: Vector[HtmlImageOccurrence]
  )(using ExecutionContext): Consequence[ImageReferenceNormalizeResult] =
    images.foldLeft(Consequence.success(ImageReferenceNormalizeResult.empty)) {
      case (z, image) =>
        z.flatMap { state =>
          _html_image_reference(content, image).map {
            case Some(occurrence) =>
              state.add(image.index, _image_reference(occurrence))
            case None =>
              state
          }.recover { conclusion =>
            state.addFailure(image.index, conclusion.show)
          }
        }
    }

  private def _html_image_reference(
    content: ContentReferenceContent,
    image: HtmlImageOccurrence
  )(using ExecutionContext): Consequence[Option[InlineImageOccurrence]] =
    _blob_workflow.normalize(InlineImageContent(
      InlineImageMarkup.HtmlFragment,
      _html_image_probe_html(image.src, image.alt, image.title),
      content.fileBundle,
      content.externalPolicy
    )).map(_.occurrences.headOption)

  private def _normalize_media_reference(
    content: ContentReferenceContent,
    ref: HtmlAttributeReferenceOccurrence,
    kind: MediaKind
  )(using ExecutionContext): Consequence[Option[NormalizedMediaReference]] = {
    val source = ref.value.trim
    _resolve_existing_media(kind, source, ref.alt, ref.title).flatMap {
      case Some((media, blob, createdMedia)) =>
        Consequence.success(Some(_normalized_media_reference(ref, kind, media, blob, createdBlob = false, createdMedia = createdMedia)))
      case None =>
        if (_is_local_relative(source))
          _register_local_media_blob(content, ref, kind).map(Some(_))
        else if (_is_external_url(source))
          _register_external_media_blob(ref, kind, content.externalPolicy)
        else
          Consequence.argumentInvalid(s"unsupported ${kind.print} reference source: ${ref.value}")
    }
  }

  private def _register_local_media_blob(
    content: ContentReferenceContent,
    ref: HtmlAttributeReferenceOccurrence,
    kind: MediaKind
  )(using ExecutionContext): Consequence[NormalizedMediaReference] =
    content.fileBundle match {
      case Some(bundle) =>
        for {
          view <- bundle.toFileSystemView
          file <- view.getFile(Paths.get(ref.value)).flatMap(_.map(Consequence.success).getOrElse(Consequence.operationNotFound(s"${kind.print} file not found in filebundle: ${ref.value}")))
          attrContentType <- _content_type_attribute(ref.mediaType)
          contentType <- _content_type_for_media(kind, file.filename, file.mimeType.map(ContentType(_, None)).orElse(attrContentType))
          id = summon[ExecutionContext].idGeneration.entityId(BlobRepository.CollectionId)
          blob <- BlobPayloadSupport.putManagedPayload(
            component = component,
            id = id,
            kind = _blob_kind(kind),
            filename = Some(file.filename),
            contentType = contentType,
            payload = file.content.promoteToBinary(),
            attributes = Map("sourcePath" -> ref.value)
          )
          media <- _ensure_media_for_created_blob(kind, blob, ref.alt, ref.title)
        } yield _normalized_media_reference(ref, kind, media.media, blob, createdBlob = true, createdMedia = media.created)
      case None =>
        Consequence.argumentMissing(s"fileBundle for ${kind.print} reference source: ${ref.value}")
    }

  private def _register_external_media_blob(
    ref: HtmlAttributeReferenceOccurrence,
    kind: MediaKind,
    externalPolicy: InlineImageExternalPolicy
  )(using ExecutionContext): Consequence[Option[NormalizedMediaReference]] =
    externalPolicy match {
      case InlineImageExternalPolicy.Preserve =>
        Consequence.success(None)
      case InlineImageExternalPolicy.Reject =>
        Consequence.argumentPolicyViolation(s"${kind.print}.src", "external-media-policy", "non-external media", ref.value)
      case InlineImageExternalPolicy.MetadataOnly =>
        for {
          normalized <- BlobExternalUrlPolicy.normalize(ref.value)
          contentType <- _content_type_attribute(ref.mediaType)
          mediaContentType <- _content_type_for_media(kind, _external_filename(normalized).getOrElse(normalized), contentType)
          id = summon[ExecutionContext].idGeneration.entityId(BlobRepository.CollectionId)
          blob <- repository.create(BlobCreate(
            id = id,
            kind = _blob_kind(kind),
            sourceMode = BlobSourceMode.ExternalUrl,
            filename = _external_filename(normalized),
            contentType = Some(mediaContentType),
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
          media <- _ensure_media_for_created_blob(kind, blob, ref.alt, ref.title)
        } yield Some(_normalized_media_reference(ref, kind, media.media, blob, createdBlob = true, createdMedia = media.created))
    }

  private def _resolve_existing_media(
    kind: MediaKind,
    value: String,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[Option[(MediaEntity, Blob, Boolean)]] =
    TextusUrn.parseOption(value) match {
      case Some(urn) if _textus_media_kind(urn.kind).contains(kind) =>
        _resolve_media_urn(kind, urn).map(_.map { case (media, blob) => (media, blob, false) })
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        _resolve_blob_ref(value).flatMap {
          case Some(blobId) =>
            repository.get(blobId).flatMap(blob => _ensure_media_for_blob(kind, blob, alt, title).map(result => Some((result.media, blob, result.created))))
          case None => Consequence.success(None)
        }
      case Some(urn) if _textus_media_kind(urn.kind).isDefined =>
        Consequence.argumentPolicyViolation(s"${kind.print}.urnKind", "textus-urn-kind", kind.print, urn.kind)
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(value)
          .map { blobValue =>
            _resolve_blob_content_value(blobValue).flatMap {
              case Some(blobId) =>
                repository.get(blobId).flatMap(blob => _ensure_media_for_blob(kind, blob, alt, title).map(result => Some((result.media, blob, result.created))))
              case None => Consequence.success(None)
            }
          }.getOrElse(Consequence.success(None))
    }

  private def _resolve_media_urn(
    kind: MediaKind,
    urn: TextusUrn
  )(using ExecutionContext): Consequence[Option[(MediaEntity, Blob)]] =
    urnRepository.resolve(urn).flatMap {
      case Some(resolution) if resolution.collection == MediaEntityCollections.collection(kind) =>
        mediaRepository.get(kind, resolution.entityId).flatMap { media =>
          repository.get(media.blobId).map(blob => Some(media -> blob))
        }
      case Some(_) =>
        Consequence.argumentInvalid(s"Textus URN is not a ${kind.print} URN: ${urn.print}")
      case None =>
        Consequence.success(None)
    }

  private def _ensure_media_for_created_blob(
    kind: MediaKind,
    blob: Blob,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[MediaEnsureResult] =
    _ensure_media_for_blob(kind, blob, alt, title).recoverWith { conclusion =>
      _delete_created_blob(blob.id).flatMap(_ => Consequence.Failure[MediaEnsureResult](conclusion))
    }

  private def _ensure_media_for_blob(
    kind: MediaKind,
    blob: Blob,
    alt: Option[String],
    title: Option[String]
  )(using ExecutionContext): Consequence[MediaEnsureResult] =
    _validate_media_blob(kind, blob).flatMap(_ =>
      mediaRepository.ensureForBlobResult(kind, blob, alt, title)
    )

  private def _normalized_media_reference(
    ref: HtmlAttributeReferenceOccurrence,
    kind: MediaKind,
    media: MediaEntity,
    blob: Blob,
    createdBlob: Boolean,
    createdMedia: Boolean
  ): NormalizedMediaReference =
    NormalizedMediaReference(
      index = ref.index,
      originalRef = ref.value,
      normalizedRef = _media_urn(kind, media.id).print,
      kind = kind,
      mediaId = media.id,
      blobId = blob.id,
      createdBlob = createdBlob,
      createdMedia = createdMedia
    )

  private def _infer_source_media_kind(
    ref: HtmlAttributeReferenceOccurrence
  ): Consequence[MediaKind] = {
    val typeKind = ref.mediaType.flatMap(_media_kind_from_content_type)
    val parentKind = ref.parentElementKind.flatMap {
      case "video" => Some(MediaKind.Video)
      case "audio" => Some(MediaKind.Audio)
      case "picture" => Some(MediaKind.Image)
      case _ => None
    }
    (typeKind, parentKind) match {
      case (Some(a), Some(b)) if a != b =>
        Consequence.argumentInvalid(s"source/src media type conflicts with parent: type=${a.print}, parent=${b.print}")
      case (Some(value), _) =>
        Consequence.success(value)
      case (_, Some(value)) =>
        Consequence.success(value)
      case _ =>
        Consequence.argumentInvalid(s"source/src media kind is ambiguous: ${ref.value}")
    }
  }

  private def _media_kind_from_content_type(value: String): Option[MediaKind] = {
    val mime = value.takeWhile(_ != ';').trim.toLowerCase(java.util.Locale.ROOT)
    if (mime.startsWith("image/")) Some(MediaKind.Image)
    else if (mime.startsWith("video/")) Some(MediaKind.Video)
    else if (mime.startsWith("audio/")) Some(MediaKind.Audio)
    else None
  }

  private def _normalize_markdown(
    content: ContentReferenceContent
  )(using ExecutionContext): Consequence[ContentReferenceNormalizeResult] = {
    val refs = _markdown_references(content.text)
    val imageRefs = refs.filter(_.kind == MarkdownReferenceKind.Image)
    val linkRefs = refs.filter(_.kind == MarkdownReferenceKind.Link)
    val linkDefinitionSpans = linkRefs.map(x => (x.urlStart, x.urlEnd)).toSet
    val mixedDefinitionSpans = imageRefs.map(x => (x.urlStart, x.urlEnd)).toSet.intersect(linkDefinitionSpans)
    for {
      links <- _markdown_link_references(linkRefs)
      images <- _markdown_image_references(content, imageRefs, mixedDefinitionSpans)
    } yield {
      val references = refs.flatMap { ref =>
        ref.kind match {
          case MarkdownReferenceKind.Image => images.referencesByIndex.get(ref.index)
          case MarkdownReferenceKind.Link => links.get(ref.index)
        }
      }
      ContentReferenceNormalizeResult(
        markup = content.markup,
        originalText = content.text,
        normalizedText = _apply_markdown_rewrites(content.text, images.rewrites),
        references = references
      )
    }
  }

  private def _markdown_link_references(
    refs: Vector[MarkdownReference]
  )(using ExecutionContext): Consequence[Map[Int, ContentReferenceOccurrence]] =
    refs.foldLeft(Consequence.success(Map.empty[Int, ContentReferenceOccurrence])) {
      case (z, ref) =>
        z.flatMap { map =>
          _markdown_link_reference(ref).map(reference => map + (ref.index -> reference))
        }
    }

  private def _markdown_image_references(
    content: ContentReferenceContent,
    refs: Vector[MarkdownReference],
    mixedDefinitionSpans: Set[(Int, Int)]
  )(using ExecutionContext): Consequence[MarkdownImageNormalizeResult] =
    refs.foldLeft(Consequence.success(MarkdownImageNormalizeResult.empty)) {
      case (z, ref) =>
        z.flatMap { state =>
          if (mixedDefinitionSpans.contains((ref.urlStart, ref.urlEnd)))
            Consequence.success(state.addFailure(ref, s"shared Markdown reference definition is used by image and link: ${ref.ref}"))
          else
            _markdown_image_reference(content, ref).map {
              case Some(occurrence) =>
                state.add(
                  ref.index,
                  _markdown_image_occurrence(ref, occurrence),
                  MarkdownRewrite(ref.urlStart, ref.urlEnd, occurrence.normalizedSrc)
                )
              case None =>
                state
            }.recover { conclusion =>
              state.addFailure(ref, conclusion.show)
            }
        }
    }

  private def _markdown_image_reference(
    content: ContentReferenceContent,
    ref: MarkdownReference
  )(using ExecutionContext): Consequence[Option[InlineImageOccurrence]] =
    _blob_workflow.normalize(InlineImageContent(
        InlineImageMarkup.HtmlFragment,
        _markdown_image_probe_html(ref),
        content.fileBundle,
        content.externalPolicy
      )).map(_.occurrences.headOption)

  private def _markdown_image_occurrence(
    ref: MarkdownReference,
    occurrence: InlineImageOccurrence
  ): ContentReferenceOccurrence =
    _image_reference(occurrence).copy(
      markup = Some("markdown-gfm"),
      occurrenceIndex = ref.index,
      originalRef = Some(ref.ref),
      alt = ref.label,
      title = ref.title,
      sortOrder = Some(ref.index)
    )

  private def _markdown_link_reference(
    ref: MarkdownReference
  )(using ExecutionContext): Consequence[ContentReferenceOccurrence] = {
    val href = ref.ref.trim
    _resolve_reference_ref(href).map {
      case Some((kind, id, urnText)) =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("markdown-gfm"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = ref.index,
          originalRef = Some(ref.ref),
          normalizedRef = Some(ref.ref),
          referenceKind = Some(kind),
          urn = Some(urnText),
          targetEntityId = Some(id.value),
          label = ref.label,
          title = ref.title,
          sortOrder = Some(ref.index)
        )
      case None =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("markdown-gfm"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = ref.index,
          originalRef = Some(ref.ref),
          normalizedRef = Some(ref.ref),
          referenceKind = Some(_reference_kind(href)),
          urn = TextusUrn.parseOption(href).map(_.print),
          label = ref.label,
          title = ref.title,
          sortOrder = Some(ref.index)
        )
    }
  }

  private def _markdown_references(
    text: String
  ): Vector[MarkdownReference] =
    _markdown_nodes(ContentRenderWorkflow.parseMarkdown(text)).collect {
      case image: MarkdownImage =>
        _markdown_reference(MarkdownReferenceKind.Image, image.getUrl, image.getText, image.getTitle, image.getEndOffset)
      case image: MarkdownImageRef =>
        _markdown_reference_ref(MarkdownReferenceKind.Image, image, image.getReferenceNode(image.getDocument))
      case link: MarkdownLink =>
        _markdown_reference(MarkdownReferenceKind.Link, link.getUrl, link.getText, link.getTitle, link.getEndOffset)
      case link: MarkdownLinkRef =>
        _markdown_reference_ref(MarkdownReferenceKind.Link, link, link.getReferenceNode(link.getDocument))
      case autolink: MarkdownAutoLink =>
        _markdown_reference(MarkdownReferenceKind.Link, autolink.getUrl, autolink.getText, BasedSequence.NULL, autolink.getEndOffset)
    }.flatten.sortBy(_.occurrenceStart).zipWithIndex.map {
      case (ref, index) => ref.copy(index = index)
    }

  private def _markdown_nodes(
    root: MarkdownNode
  ): Vector[MarkdownNode] = {
    val builder = Vector.newBuilder[MarkdownNode]
    def loop(node: MarkdownNode): Unit = {
      var child = node.getFirstChild
      while (child != null) {
        builder += child
        loop(child)
        child = child.getNext
      }
    }
    loop(root)
    builder.result()
  }

  private def _markdown_reference(
    kind: MarkdownReferenceKind,
    url: BasedSequence,
    label: BasedSequence,
    title: BasedSequence,
    endOffset: Int
  ): Option[MarkdownReference] =
    _markdown_span(url).map {
      case (start, end, raw) =>
        MarkdownReference(
          index = 0,
          kind = kind,
          ref = raw,
          label = _markdown_text(label),
          title = _markdown_text(title),
          urlStart = start,
          urlEnd = end,
          occurrenceStart = start,
          endOffset = endOffset
        )
    }

  private def _markdown_reference_ref(
    kind: MarkdownReferenceKind,
    node: com.vladsch.flexmark.ast.RefNode,
    definition: MarkdownReferenceDefinition
  ): Option[MarkdownReference] =
    Option(definition).flatMap { refdef =>
      _markdown_span(refdef.getUrl).map {
        case (start, end, raw) =>
          MarkdownReference(
            index = 0,
            kind = kind,
            ref = raw,
            label = _markdown_text(node.getText),
            title = _markdown_text(refdef.getTitle),
            urlStart = start,
            urlEnd = end,
            occurrenceStart = node.getStartOffset,
            endOffset = node.getEndOffset
          )
      }
    }

  private def _markdown_span(
    value: BasedSequence
  ): Option[(Int, Int, String)] =
    Option(value).flatMap { seq =>
      val start = seq.getStartOffset
      val end = seq.getEndOffset
      val raw = seq.unescape()
      Option.when(start >= 0 && end >= start && raw.nonEmpty)((start, end, raw))
    }

  private def _markdown_text(
    value: BasedSequence
  ): Option[String] =
    Option(value).map(_.unescape()).map(_.trim).filter(_.nonEmpty)

  private def _markdown_image_probe_html(
    ref: MarkdownReference
  ): String =
    _html_image_probe_html(ref.ref, ref.label, ref.title)

  private def _html_image_probe_html(
    src: String,
    altText: Option[String],
    titleText: Option[String]
  ): String = {
    val alt = altText.map(value => s""" alt="${_html_attr(value)}"""").getOrElse("")
    val title = titleText.map(value => s""" title="${_html_attr(value)}"""").getOrElse("")
    s"""<img src="${_html_attr(src)}"$alt$title>"""
  }

  private def _apply_markdown_rewrites(
    text: String,
    rewrites: Vector[MarkdownRewrite]
  ): String =
    rewrites.distinctBy(x => (x.start, x.end, x.value)).sortBy(_.start).reverse.foldLeft(text) {
      case (z, rewrite) =>
        if (rewrite.start >= 0 && rewrite.end >= rewrite.start && rewrite.end <= z.length)
          z.substring(0, rewrite.start) + rewrite.value + z.substring(rewrite.end)
        else
          z
    }

  private def _html_attr(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("\"", "&quot;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

  private def _normalize_smartdox(
    content: ContentReferenceContent
  )(using ExecutionContext): Consequence[ContentReferenceNormalizeResult] =
    Dox2Parser.parseC(content.text).flatMap { document =>
      val refs = DoxReferenceExtractor.extract(document)
      val imageRefs = refs.filter(ref => ref.elementKind == "img" && ref.attributeName == "src")
      val linkRefs = refs.filterNot(ref => ref.elementKind == "img" && ref.attributeName == "src")
      for {
        images <- _smartdox_image_references(content, imageRefs)
        links <- linkRefs.foldLeft(Consequence.success(Vector.empty[ContentReferenceOccurrence])) {
          case (z, ref) =>
            z.flatMap(xs => _smartdox_link_reference(ref).map(xs :+ _))
        }
        imageReferences = images.referencesByIndex.toVector.sortBy(_._1).map(_._2)
      } yield ContentReferenceNormalizeResult(
        markup = content.markup,
        originalText = content.text,
        normalizedText = _apply_smartdox_rewrites(content.text, images.rewrites, images.trailingFailures),
        references = imageReferences ++ links
      )
    }

  private def _smartdox_image_references(
    content: ContentReferenceContent,
    refs: Vector[DoxReference]
  )(using ExecutionContext): Consequence[SmartDoxImageNormalizeResult] =
    refs.foldLeft(Consequence.success(SmartDoxImageNormalizeResult.empty)) {
      case (z, ref) =>
        z.flatMap { state =>
          _smartdox_image_reference(content, ref).map {
            case Some(occurrence) =>
              state.add(ref, _image_reference(occurrence).copy(
                markup = Some("smartdox"),
                occurrenceIndex = ref.occurrenceIndex,
                originalRef = Some(ref.ref),
                normalizedRef = Some(occurrence.normalizedSrc),
                urn = Some(occurrence.normalizedSrc),
                alt = ref.alt.orElse(ref.label),
                title = ref.title,
                sortOrder = Some(ref.occurrenceIndex)
              ), occurrence.normalizedSrc)
            case None =>
              state
          }.recover { conclusion =>
            state.addFailure(ref, s"${ref.ref}: ${conclusion.show}")
          }
        }
    }

  private def _smartdox_image_reference(
    content: ContentReferenceContent,
    ref: DoxReference
  )(using ExecutionContext): Consequence[Option[InlineImageOccurrence]] =
    _blob_workflow.normalize(InlineImageContent(
      InlineImageMarkup.HtmlFragment,
      _html_image_probe_html(ref.ref, ref.alt.orElse(ref.label), ref.title),
      content.fileBundle,
      content.externalPolicy
    )).map(_.occurrences.headOption)

  private def _apply_smartdox_rewrites(
    text: String,
    rewrites: Vector[MarkdownRewrite],
    trailingFailures: Vector[String]
  ): String = {
    val rewritten = _apply_markdown_rewrites(text, rewrites)
    if (trailingFailures.isEmpty)
      rewritten
    else {
      val suffix = trailingFailures.map(ContentReferenceWorkflow.smartdoxFailureComment).mkString("\n")
      val separator = if (rewritten.endsWith("\n")) "" else "\n"
      s"$rewritten$separator$suffix"
    }
  }

  private def _smartdox_link_reference(
    ref: DoxReference
  )(using ExecutionContext): Consequence[ContentReferenceOccurrence] = {
    val href = ref.ref.trim
    _resolve_reference_ref(href).map {
      case Some((kind, id, urnText)) =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("smartdox"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = ref.occurrenceIndex,
          originalRef = Some(ref.ref),
          normalizedRef = Some(ref.ref),
          referenceKind = Some(kind),
          urn = Some(urnText),
          targetEntityId = Some(id.value),
          label = ref.label,
          title = ref.title,
          sortOrder = Some(ref.occurrenceIndex)
        )
      case None =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("smartdox"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = ref.occurrenceIndex,
          originalRef = Some(ref.ref),
          normalizedRef = Some(ref.ref),
          referenceKind = Some(ref.referenceKind),
          urn = TextusUrn.parseOption(href).map(_.print),
          label = ref.label,
          title = ref.title,
          sortOrder = Some(ref.occurrenceIndex)
        )
    }
  }

  private def _attach_media_references(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _media_attachment_reference_ids(references).flatMap { refs =>
      _attach_media_reference_ids(sourceEntityId, refs)
        .map(attached => ContentReferenceAttachResult(sourceEntityId, attached.records))
    }

  private def _attach_media_reference_ids(
    sourceEntityId: String,
    refs: Vector[MediaAttachmentReference]
  )(using ExecutionContext): Consequence[AttachedInlineReferences] =
    refs.distinctBy(_.key).zipWithIndex.foldLeft(Consequence.success(AttachedInlineReferences.empty)) {
      case (z, (ref, index)) =>
        z.flatMap { attached =>
          _association_workflow.attachExistingTargetResult(
            sourceEntityId = sourceEntityId,
            domain = AssociationDomain.MediaAttachment,
            targetKind = Some(ref.kind.print),
            targetEntityId = ref.id,
            role = ref.role,
            sortOrder = Some(index)
          ).map { result =>
            attached.add(result)
          }.recoverWith { conclusion =>
            _association_workflow.compensate(attached.created).flatMap(_ => Consequence.Failure[AttachedInlineReferences](conclusion))
          }
        }
    }

  private def _delete_stale_media_associations(
    sourceEntityId: String,
    desiredKeys: Vector[(String, String, String)],
    created: Vector[org.goldenport.cncf.association.AssociationBindingAttachResult]
  )(using ExecutionContext): Consequence[Unit] =
    Vector("inline", "attachment").foldLeft(Consequence.unit) {
      case (z, role) =>
        z.flatMap { _ =>
          associations.list(org.goldenport.cncf.association.AssociationFilter(
            domain = AssociationDomain.MediaAttachment,
            sourceEntityId = Some(sourceEntityId),
            role = Some(role)
          )).flatMap { existing =>
            val keep = desiredKeys.toSet
            existing.filterNot(x => keep.contains((x.targetKind.getOrElse(""), x.targetEntityId, x.role))).foldLeft(Consequence.unit) {
              case (z, association) =>
                z.flatMap(_ => associations.delete(association))
            }
          }
        }
    }.recoverWith { conclusion =>
      _association_workflow.compensate(created).flatMap(_ => Consequence.Failure[Unit](conclusion))
    }

  private def _media_attachment_reference_ids(
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[Vector[MediaAttachmentReference]] =
    references.foldLeft(Consequence.success(Vector.empty[MediaAttachmentReference])) {
      case (z, ref) =>
        z.flatMap { refs =>
          _media_attachment_reference_id(ref).map {
            case Some(value) => refs :+ value
            case None => refs
          }
        }
      }

  private def _media_attachment_reference_id(
    ref: ContentReferenceOccurrence
  )(using ExecutionContext): Consequence[Option[MediaAttachmentReference]] =
    _media_attachment_role(ref) match {
      case Some((kind, role)) =>
        ref.targetEntityId match {
          case Some(value) =>
            EntityId.parse(value).flatMap { id =>
              val mediaId = _media_entity_id(kind, id)
              mediaRepository.get(kind, mediaId).map(_ => Some(MediaAttachmentReference(ref, mediaId, kind, role)))
            }
          case None =>
            ref.urn.orElse(ref.normalizedRef).orElse(ref.originalRef)
              .map(value => _resolve_media_ref(kind, value).map(_.map(id => MediaAttachmentReference(ref, id, kind, role))))
              .getOrElse(Consequence.success(None))
        }
      case None =>
        Consequence.success(None)
    }

  private def _media_attachment_role(
    ref: ContentReferenceOccurrence
  ): Option[(MediaKind, String)] = {
    val kind = ref.referenceKind.flatMap(_textus_media_kind)
    (ref.elementKind, ref.attributeName, ref.referenceKind) match {
      case (Some("img"), Some("src"), Some("image") | Some("blob")) =>
        Some(MediaKind.Image -> "inline")
      case (Some("video"), Some("src"), Some("video") | Some("blob")) =>
        Some(MediaKind.Video -> "inline")
      case (Some("source"), Some("src"), _) =>
        kind.filter(k => k == MediaKind.Image || k == MediaKind.Video || k == MediaKind.Audio).map(_ -> "inline")
      case (Some("a"), Some("href"), Some("attachment")) =>
        Some(MediaKind.Attachment -> "attachment")
      case _ =>
        None
    }
  }

  private def _resolve_media_ref(
    kind: MediaKind,
    value: String
  )(using ExecutionContext): Consequence[Option[EntityId]] =
    TextusUrn.parseOption(value) match {
      case Some(urn) if _textus_media_kind(urn.kind).contains(kind) =>
        urnRepository.resolve(urn).map(_.map(_.entityId))
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        _resolve_blob_ref(value).flatMap {
          case Some(blobId) =>
            repository.get(blobId).flatMap(blob => _ensure_media_for_blob(kind, blob, None, None).map(result => Some(result.media.id)))
          case None => Consequence.success(None)
        }
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(value)
          .map { blobValue =>
            _resolve_blob_content_value(blobValue).flatMap {
              case Some(blobId) =>
                repository.get(blobId).flatMap(blob => _ensure_media_for_blob(kind, blob, None, None).map(result => Some(result.media.id)))
              case None => Consequence.success(None)
            }
          }.getOrElse(Consequence.success(None))
    }

  private def _resolve_reference_ref(
    value: String
  )(using ExecutionContext): Consequence[Option[(String, EntityId, String)]] =
    TextusUrn.parseOption(value) match {
      case Some(urn) if urn.kind == TextusUrn.ImageKind =>
        urnRepository.resolve(urn).map(_.map(resolution => ("image", resolution.entityId, urn.print)))
      case Some(urn) if urn.kind == TextusUrn.VideoKind =>
        urnRepository.resolve(urn).map(_.map(resolution => ("video", resolution.entityId, urn.print)))
      case Some(urn) if urn.kind == TextusUrn.AudioKind =>
        urnRepository.resolve(urn).map(_.map(resolution => ("audio", resolution.entityId, urn.print)))
      case Some(urn) if urn.kind == TextusUrn.AttachmentKind =>
        urnRepository.resolve(urn).map(_.map(resolution => ("attachment", resolution.entityId, urn.print)))
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        _resolve_blob_ref(value).map(_.map(id => ("blob", id, urn.print)))
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(value)
          .map(_resolve_blob_content_value)
          .getOrElse(Consequence.success(None))
          .map(_.map(id => ("blob", id, TextusUrn.blob(id.parts.entropy).print)))
    }

  private def _resolve_blob_ref(
    value: String
  )(using ExecutionContext): Consequence[Option[EntityId]] =
    TextusUrn.parseOption(value) match {
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        urnRepository.resolve(urn).map(_.map(_.entityId))
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(value)
          .map(_resolve_blob_content_value)
          .getOrElse(Consequence.success(None))
    }

  private def _resolve_blob_content_value(
    value: String
  )(using ExecutionContext): Consequence[Option[EntityId]] =
    EntityId.parse(value).toOption match {
      case Some(id) if id.collection.name == BlobRepository.CollectionId.name =>
        val blobId = _blob_entity_id(id)
        repository.get(blobId).map(_ => Some(blobId))
      case Some(_) =>
        Consequence.success(None)
      case None =>
        urnRepository.resolve(TextusUrn.blob(value)).map(_.map(_.entityId))
    }

  private def _blob_entity_id(id: EntityId): EntityId =
    if (id.collection == BlobRepository.CollectionId)
      id
    else
      id.copy(collection = BlobRepository.CollectionId)

  private def _media_entity_id(kind: MediaKind, id: EntityId): EntityId = {
    val collection = MediaEntityCollections.collection(kind)
    if (id.collection == collection)
      id
    else
      id.copy(collection = collection)
  }

  private def _media_urn(kind: MediaKind, id: EntityId): TextusUrn =
    kind match {
      case MediaKind.Image => TextusUrn.image(id.parts.entropy)
      case MediaKind.Video => TextusUrn.video(id.parts.entropy)
      case MediaKind.Audio => TextusUrn.audio(id.parts.entropy)
      case MediaKind.Attachment => TextusUrn.attachment(id.parts.entropy)
    }

  private def _textus_media_kind(kind: String): Option[MediaKind] =
    kind match {
      case TextusUrn.ImageKind => Some(MediaKind.Image)
      case TextusUrn.VideoKind => Some(MediaKind.Video)
      case TextusUrn.AudioKind => Some(MediaKind.Audio)
      case TextusUrn.AttachmentKind => Some(MediaKind.Attachment)
      case _ => None
    }

  private def _blob_kind(kind: MediaKind): BlobKind =
    kind match {
      case MediaKind.Image => BlobKind.Image
      case MediaKind.Video => BlobKind.Video
      case MediaKind.Audio => BlobKind.Audio
      case MediaKind.Attachment => BlobKind.Attachment
    }

  private def _validate_media_blob(
    kind: MediaKind,
    blob: Blob
  ): Consequence[Unit] = {
    val kindOk = kind match {
      case MediaKind.Attachment =>
        blob.kind == BlobKind.Attachment || blob.kind == BlobKind.Binary
      case _ =>
        blob.kind == _blob_kind(kind)
    }
    if (!kindOk)
      Consequence.argumentPolicyViolation(s"${kind.print}.blobKind", "blob-kind", _blob_kind(kind).print, blob.kind.print)
    else
      blob.contentType match {
        case Some(contentType) if !_is_media_content_type(kind, contentType) =>
          Consequence.argumentPolicyViolation(s"${kind.print}.contentType", "mime-kind", _expected_mime(kind), contentType.header)
        case _ =>
          Consequence.unit
      }
  }

  private def _content_type_for_media(
    kind: MediaKind,
    filename: String,
    explicit: Option[ContentType]
  ): Consequence[ContentType] = {
    val contentType = explicit.orElse(_content_type_from_filename(filename)).getOrElse(ContentType.APPLICATION_OCTET_STREAM)
    if (_is_media_content_type(kind, contentType))
      Consequence.success(contentType)
    else
      Consequence.argumentPolicyViolation(s"${kind.print}.contentType", "mime-kind", _expected_mime(kind), contentType.header)
  }

  private def _content_type_attribute(
    value: Option[String]
  ): Consequence[Option[ContentType]] =
    value.map { source =>
      try {
        Consequence.success(Some(ContentType.parse(source)))
      } catch {
        case NonFatal(e) =>
          Consequence.argumentInvalid(s"invalid media type attribute: ${source}: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
      }
    }.getOrElse(Consequence.success(None))

  private def _is_media_content_type(
    kind: MediaKind,
    contentType: ContentType
  ): Boolean = {
    val mime = contentType.mimeType.print.toLowerCase(java.util.Locale.ROOT)
    kind match {
      case MediaKind.Image => mime.startsWith("image/")
      case MediaKind.Video => mime.startsWith("video/")
      case MediaKind.Audio => mime.startsWith("audio/")
      case MediaKind.Attachment => true
    }
  }

  private def _expected_mime(kind: MediaKind): String =
    kind match {
      case MediaKind.Image => "image/*"
      case MediaKind.Video => "video/*"
      case MediaKind.Audio => "audio/*"
      case MediaKind.Attachment => "downloadable payload"
    }

  private def _content_type_from_filename(filename: String): Option[ContentType] = {
    val lower = filename.toLowerCase(java.util.Locale.ROOT)
    if (lower.endsWith(".png")) Some(ContentType.IMAGE_PNG)
    else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) Some(ContentType.IMAGE_JPEG)
    else if (lower.endsWith(".gif")) Some(ContentType.IMAGE_GIF)
    else if (lower.endsWith(".svg") || lower.endsWith(".svgz")) Some(ContentType.IMAGE_SVG)
    else if (lower.endsWith(".webp")) Some(ContentType.parse("image/webp"))
    else if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) Some(ContentType.parse("video/mp4"))
    else if (lower.endsWith(".webm")) Some(ContentType.parse("video/webm"))
    else if (lower.endsWith(".ogv")) Some(ContentType.parse("video/ogg"))
    else if (lower.endsWith(".mp3")) Some(ContentType.parse("audio/mpeg"))
    else if (lower.endsWith(".m4a")) Some(ContentType.parse("audio/mp4"))
    else if (lower.endsWith(".wav")) Some(ContentType.parse("audio/wav"))
    else if (lower.endsWith(".oga") || lower.endsWith(".ogg")) Some(ContentType.parse("audio/ogg"))
    else if (lower.endsWith(".pdf")) Some(ContentType.APPLICATION_PDF)
    else if (lower.endsWith(".zip")) Some(ContentType.APPLICATION_ZIP)
    else None
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

  private def _reference_kind(value: String): String =
    if (_is_external_url(value))
      "external-url"
    else if (TextusUrn.parseOption(value).isDefined)
      "textus-urn"
    else
      "link"

  private def _is_external_url(value: String): Boolean =
    value.startsWith("http://") || value.startsWith("https://")

  private def _is_local_relative(src: String): Boolean =
    src.nonEmpty &&
      !src.startsWith("/") &&
      !src.startsWith("#") &&
      !src.startsWith("//") &&
      !src.contains(":")

  private def _external_filename(url: String): Option[String] =
    url.split('/').lastOption.map(_.takeWhile(ch => ch != '?' && ch != '#')).filter(_.nonEmpty)

  private def _blob_content_value(src: String): Option[String] =
    BlobContentPattern.findFirstMatchIn(src).map(_.group(1))

  private val BlobContentPattern: Regex =
    """/web/blob/content/([^"'?\s<>#]+)""".r
}

private final case class AttachedInlineReferences(
  records: Vector[Record],
  created: Vector[org.goldenport.cncf.association.AssociationBindingAttachResult]
) {
  def add(result: org.goldenport.cncf.association.AssociationBindingAttachResult): AttachedInlineReferences =
    copy(
      records = records :+ AssociationRecordCodec.toRecord(result.association),
      created = if (result.created) created :+ result else created
    )
}

private object AttachedInlineReferences {
  val empty: AttachedInlineReferences =
    AttachedInlineReferences(Vector.empty, Vector.empty)
}

private final case class MediaAttachmentReference(
  reference: ContentReferenceOccurrence,
  id: EntityId,
  kind: MediaKind,
  role: String
) {
  def key: (String, String, String) =
    (kind.print, id.value, role)
}

private final case class NormalizedMediaReference(
  index: Int,
  originalRef: String,
  normalizedRef: String,
  kind: MediaKind,
  mediaId: EntityId,
  blobId: EntityId,
  createdBlob: Boolean,
  createdMedia: Boolean
)

private final case class MediaReferenceNormalizeResult(
  referencesByIndex: Map[Int, ContentReferenceOccurrence],
  failuresByIndex: Map[Int, String]
) {
  def add(
    index: Int,
    reference: ContentReferenceOccurrence
  ): MediaReferenceNormalizeResult =
    copy(referencesByIndex = referencesByIndex + (index -> reference))

  def addFailure(
    index: Int,
    message: String
  ): MediaReferenceNormalizeResult =
    copy(failuresByIndex = failuresByIndex + (index -> message))
}

private object MediaReferenceNormalizeResult {
  val empty: MediaReferenceNormalizeResult =
    MediaReferenceNormalizeResult(Map.empty, Map.empty)
}

private final case class ImageReferenceNormalizeResult(
  referencesByIndex: Map[Int, ContentReferenceOccurrence],
  failuresByIndex: Map[Int, String]
) {
  def add(
    index: Int,
    reference: ContentReferenceOccurrence
  ): ImageReferenceNormalizeResult =
    copy(referencesByIndex = referencesByIndex + (index -> reference))

  def addFailure(
    index: Int,
    message: String
  ): ImageReferenceNormalizeResult =
    copy(failuresByIndex = failuresByIndex + (index -> message))
}

private object ImageReferenceNormalizeResult {
  val empty: ImageReferenceNormalizeResult =
    ImageReferenceNormalizeResult(Map.empty, Map.empty)
}

private enum MarkdownReferenceKind {
  case Image
  case Link
}

private final case class MarkdownReference(
  index: Int,
  kind: MarkdownReferenceKind,
  ref: String,
  label: Option[String],
  title: Option[String],
  urlStart: Int,
  urlEnd: Int,
  occurrenceStart: Int,
  endOffset: Int
)

private final case class MarkdownRewrite(
  start: Int,
  end: Int,
  value: String
)

private final case class MarkdownImageNormalizeResult(
  referencesByIndex: Map[Int, ContentReferenceOccurrence],
  failuresByIndex: Map[Int, String],
  rewrites: Vector[MarkdownRewrite]
) {
  def add(
    index: Int,
    reference: ContentReferenceOccurrence,
    rewrite: MarkdownRewrite
  ): MarkdownImageNormalizeResult =
    copy(
      referencesByIndex = referencesByIndex + (index -> reference),
      rewrites = rewrites :+ rewrite
    )

  def addFailure(
    ref: MarkdownReference,
    message: String
  ): MarkdownImageNormalizeResult =
    copy(
      failuresByIndex = failuresByIndex + (ref.index -> message),
      rewrites = rewrites :+ MarkdownRewrite(ref.endOffset, ref.endOffset, ContentReferenceWorkflow.markdownFailureComment(message))
    )
}

private object MarkdownImageNormalizeResult {
  val empty: MarkdownImageNormalizeResult =
    MarkdownImageNormalizeResult(Map.empty, Map.empty, Vector.empty)
}

private final case class SmartDoxImageNormalizeResult(
  referencesByIndex: Map[Int, ContentReferenceOccurrence],
  failuresByIndex: Map[Int, String],
  rewrites: Vector[MarkdownRewrite],
  trailingFailures: Vector[String]
) {
  def add(
    ref: DoxReference,
    reference: ContentReferenceOccurrence,
    normalizedRef: String
  ): SmartDoxImageNormalizeResult =
    copy(
      referencesByIndex = referencesByIndex + (ref.occurrenceIndex -> reference),
      rewrites = ref.sourceSpan.map(_.target).map(span => rewrites :+ MarkdownRewrite(span.start, span.end, normalizedRef)).getOrElse(rewrites)
    )

  def addFailure(
    ref: DoxReference,
    message: String
  ): SmartDoxImageNormalizeResult = {
    val failure = ContentReferenceWorkflow.smartdoxFailureComment(message)
    val rewrite = ref.sourceSpan.map(_.node.end).map(offset => MarkdownRewrite(offset, offset, s"\n$failure\n"))
    copy(
      failuresByIndex = failuresByIndex + (ref.occurrenceIndex -> message),
      rewrites = rewrite.map(rewrites :+ _).getOrElse(rewrites),
      trailingFailures = if (rewrite.isDefined) trailingFailures else trailingFailures :+ message
    )
  }
}

private object SmartDoxImageNormalizeResult {
  val empty: SmartDoxImageNormalizeResult =
    SmartDoxImageNormalizeResult(Map.empty, Map.empty, Vector.empty, Vector.empty)
}

object ContentReferenceWorkflow {
  private[blob] def markdownFailureComment(message: String): String =
    s" <!-- textus:image-normalization-failed: ${commentText(message)} -->"

  private[blob] def htmlFailureComment(message: String): String =
    s" textus:image-normalization-failed: ${commentText(message)} "

  private[blob] def htmlReferenceFailureComment(
    ref: HtmlAttributeReferenceOccurrence,
    message: String
  ): String = {
    val kind =
      if (ref.elementKind == "img") "image"
      else if (ref.elementKind == "video") "video"
      else if (ref.elementKind == "source") "media"
      else if (ref.elementKind == "a" && ref.download.isDefined) "attachment"
      else "reference"
    s" textus:${kind}-normalization-failed: ${commentText(message)} "
  }

  private[blob] def smartdoxFailureComment(message: String): String =
    s"# textus:image-normalization-failed: ${commentText(message)}"

  private[blob] def commentText(message: String): String =
    message
      .replace("--", "- -")
      .replace("\r", " ")
      .replace("\n", " ")
      .trim

  def mediaTargetValidator(
    repository: MediaRepository
  ): org.goldenport.cncf.association.AssociationTargetValidator =
    new org.goldenport.cncf.association.AssociationTargetValidator {
      def validate(
        targetKind: Option[String],
        id: EntityId
      )(using ExecutionContext): Consequence[Unit] =
        targetKind match {
          case Some("image") =>
            repository.get(MediaKind.Image, id).map(_ => ())
          case Some("video") =>
            repository.get(MediaKind.Video, id).map(_ => ())
          case Some("audio") =>
            repository.get(MediaKind.Audio, id).map(_ => ())
          case Some("attachment") =>
            repository.get(MediaKind.Attachment, id).map(_ => ())
          case Some(other) =>
            Consequence.argumentInvalid(s"unsupported media association target kind: $other")
          case None =>
            Consequence.argumentMissing("targetKind")
        }
    }
}
