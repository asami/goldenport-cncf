package org.goldenport.cncf.blob

import scala.util.matching.Regex
import com.vladsch.flexmark.ast.{AutoLink as MarkdownAutoLink, Image as MarkdownImage, ImageRef as MarkdownImageRef, Link as MarkdownLink, LinkRef as MarkdownLinkRef, Reference as MarkdownReferenceDefinition}
import com.vladsch.flexmark.util.ast.Node as MarkdownNode
import com.vladsch.flexmark.util.sequence.BasedSequence
import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationBindingWorkflow, AssociationDomain, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.html.{HtmlFragment, HtmlImageOccurrence, HtmlImageRewrite, HtmlLinkOccurrence, HtmlTree}
import org.goldenport.cncf.id.{TextusUrn, UrnRepository}
import org.goldenport.datatype.FileBundle
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
 * @version May.  4, 2026
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
    _attach_inline_images(sourceEntityId, references)

  def validateInlineReferences(
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[Unit] =
    _inline_image_reference_ids(references).map(_ => ())

  def syncInlineReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _inline_image_reference_ids(references).flatMap { desired =>
      val desiredIds = desired.map(_._2).distinctBy(_.value)
      for {
        attached <- _attach_inline_image_ids(sourceEntityId, desired)
        _ <- _delete_stale_inline_associations(sourceEntityId, desiredIds, attached.created)
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
      images <- _html_image_references(content, fragment.images)
      linkRefs <- _link_references(fragment.links)
      rewritten = fragment.rewriteImageSourcesWithComments { image =>
        HtmlImageRewrite(
          images.referencesByIndex.get(image.index).flatMap(_.normalizedRef),
          images.failuresByIndex.get(image.index).map(ContentReferenceWorkflow.htmlFailureComment)
        )
      }
    } yield ContentReferenceNormalizeResult(
      markup = content.markup,
      originalText = content.text,
      normalizedText = rewritten.render,
      references = images.referencesByIndex.toVector.sortBy(_._1).map(_._2) ++ linkRefs
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
        normalizedText = _append_smartdox_failures(content.text, images.failuresByIndex.values.toVector),
        references = imageReferences ++ links
      )
    }

  private def _smartdox_image_references(
    content: ContentReferenceContent,
    refs: Vector[DoxReference]
  )(using ExecutionContext): Consequence[ImageReferenceNormalizeResult] =
    refs.foldLeft(Consequence.success(ImageReferenceNormalizeResult.empty)) {
      case (z, ref) =>
        z.flatMap { state =>
          _smartdox_image_reference(content, ref).map {
            case Some(occurrence) =>
              state.add(ref.occurrenceIndex, _image_reference(occurrence).copy(
                markup = Some("smartdox"),
                occurrenceIndex = ref.occurrenceIndex,
                originalRef = Some(ref.ref),
                alt = ref.alt.orElse(ref.label),
                title = ref.title,
                sortOrder = Some(ref.occurrenceIndex)
              ))
            case None =>
              state
          }.recover { conclusion =>
            state.addFailure(ref.occurrenceIndex, s"${ref.ref}: ${conclusion.show}")
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

  private def _append_smartdox_failures(
    text: String,
    failures: Vector[String]
  ): String =
    if (failures.isEmpty)
      text
    else {
      val suffix = failures.map(ContentReferenceWorkflow.smartdoxFailureComment).mkString("\n")
      val separator = if (text.endsWith("\n")) "" else "\n"
      s"$text$separator$suffix"
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

  private def _attach_inline_images(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _inline_image_reference_ids(references).flatMap { refs =>
      _attach_inline_image_ids(sourceEntityId, refs)
        .map(attached => ContentReferenceAttachResult(sourceEntityId, attached.records))
    }

  private def _attach_inline_image_ids(
    sourceEntityId: String,
    refs: Vector[(ContentReferenceOccurrence, EntityId)]
  )(using ExecutionContext): Consequence[AttachedInlineReferences] =
    refs.distinctBy(_._2.value).zipWithIndex.foldLeft(Consequence.success(AttachedInlineReferences.empty)) {
      case (z, ((_, id), index)) =>
        z.flatMap { attached =>
          _association_workflow.attachExistingTargetResult(
            sourceEntityId = sourceEntityId,
            domain = AssociationDomain.MediaAttachment,
            targetKind = Some("image"),
            targetEntityId = id,
            role = "inline",
            sortOrder = Some(index)
          ).map { result =>
            attached.add(result)
          }.recoverWith { conclusion =>
            _association_workflow.compensate(attached.created).flatMap(_ => Consequence.Failure[AttachedInlineReferences](conclusion))
          }
        }
    }

  private def _delete_stale_inline_associations(
    sourceEntityId: String,
    desiredIds: Vector[EntityId],
    created: Vector[org.goldenport.cncf.association.AssociationBindingAttachResult]
  )(using ExecutionContext): Consequence[Unit] =
    associations.list(org.goldenport.cncf.association.AssociationFilter(
      domain = AssociationDomain.MediaAttachment,
      sourceEntityId = Some(sourceEntityId),
      targetKind = Some("image"),
      role = Some("inline")
    )).flatMap { existing =>
      val keep = desiredIds.map(_.value).toSet
      existing.filterNot(x => keep.contains(x.targetEntityId)).foldLeft(Consequence.unit) {
        case (z, association) =>
          z.flatMap(_ => associations.delete(association))
      }.recoverWith { conclusion =>
        _association_workflow.compensate(created).flatMap(_ => Consequence.Failure[Unit](conclusion))
      }
    }

  private def _inline_image_reference_ids(
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[Vector[(ContentReferenceOccurrence, EntityId)]] =
    references.foldLeft(Consequence.success(Vector.empty[(ContentReferenceOccurrence, EntityId)])) {
      case (z, ref) =>
        z.flatMap { refs =>
          _inline_image_reference_id(ref).map {
            case Some(id) => refs :+ (ref -> id)
            case None => refs
          }
        }
    }

  private def _inline_image_reference_id(
    ref: ContentReferenceOccurrence
  )(using ExecutionContext): Consequence[Option[EntityId]] =
    if (_is_inline_image_reference(ref))
      ref.targetEntityId match {
        case Some(value) =>
          EntityId.parse(value).flatMap { id =>
            val imageId = _media_entity_id(MediaKind.Image, id)
            mediaRepository.get(MediaKind.Image, imageId).map(_ => Some(imageId))
          }
        case None =>
          ref.urn.orElse(ref.normalizedRef).orElse(ref.originalRef)
            .map(_resolve_image_ref)
            .getOrElse(Consequence.success(None))
      }
    else
      Consequence.success(None)

  private def _is_inline_image_reference(ref: ContentReferenceOccurrence): Boolean =
    ref.elementKind.contains("img") && ref.attributeName.contains("src") &&
      (ref.referenceKind.contains("image") || ref.referenceKind.contains("blob"))

  private def _resolve_image_ref(
    value: String
  )(using ExecutionContext): Consequence[Option[EntityId]] =
    TextusUrn.parseOption(value) match {
      case Some(urn) if urn.kind == TextusUrn.ImageKind =>
        urnRepository.resolve(urn).map(_.map(_.entityId))
      case Some(urn) if urn.kind == TextusUrn.BlobKind =>
        _resolve_blob_ref(value).flatMap {
          case Some(blobId) =>
            repository.get(blobId).flatMap(blob => mediaRepository.ensureImageForBlob(blob).map(media => Some(media.id)))
          case None => Consequence.success(None)
        }
      case Some(_) =>
        Consequence.success(None)
      case None =>
        _blob_content_value(value)
          .map { _ =>
            _resolve_blob_content_value(value).flatMap {
              case Some(blobId) =>
                repository.get(blobId).flatMap(blob => mediaRepository.ensureImageForBlob(blob).map(media => Some(media.id)))
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

  private def _reference_kind(value: String): String =
    if (_is_external_url(value))
      "external-url"
    else if (TextusUrn.parseOption(value).isDefined)
      "textus-urn"
    else
      "link"

  private def _is_external_url(value: String): Boolean =
    value.startsWith("http://") || value.startsWith("https://")

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

object ContentReferenceWorkflow {
  private[blob] def markdownFailureComment(message: String): String =
    s" <!-- textus:image-normalization-failed: ${commentText(message)} -->"

  private[blob] def htmlFailureComment(message: String): String =
    s" textus:image-normalization-failed: ${commentText(message)} "

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
