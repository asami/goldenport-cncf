package org.goldenport.cncf.blob

import scala.util.matching.Regex
import org.goldenport.Consequence
import org.goldenport.cncf.association.{AssociationBindingWorkflow, AssociationDomain, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.html.{HtmlFragment, HtmlLinkOccurrence, HtmlTree}
import org.goldenport.cncf.id.{TextusUrn, UrnRepository}
import org.goldenport.datatype.FileBundle
import org.goldenport.record.Record
import org.goldenport.value.ContentReferenceOccurrence
import org.simplemodeling.model.datatype.EntityId

/*
 * SimpleEntity content reference normalization and attachment workflow.
 *
 * @since   May.  3, 2026
 * @version May.  3, 2026
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
  associations: AssociationRepository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault),
  urnRepository: UrnRepository = UrnRepository(new BlobUrnResolver)
) {
  private lazy val _blob_workflow =
    blobWorkflow.getOrElse(BlobInlineImageWorkflow(component, repository, associations, urnRepository))

  private val _association_workflow =
    AssociationBindingWorkflow(
      associations,
      AssociationStoragePolicy.blobAttachmentDefault,
      BlobInlineImageWorkflow.blobTargetValidator(repository)
    )

  def normalize(
    content: ContentReferenceContent
  )(using ExecutionContext): Consequence[ContentReferenceNormalizeResult] =
    content.markup match {
      case InlineImageMarkup.HtmlFragment =>
        for {
          inline <- _blob_workflow.normalize(content.toInlineImageContent)
          fragment <- _parse_html_fragment(inline.normalizedText)
          linkRefs <- _link_references(fragment.links)
        } yield ContentReferenceNormalizeResult(
          markup = content.markup,
          originalText = content.text,
          normalizedText = inline.normalizedText,
          references = inline.occurrences.map(_image_reference) ++ linkRefs
        )
      case InlineImageMarkup.Markdown =>
        Consequence.operationInvalid("GFM-compatible Markdown content reference normalization is reserved for the next content-format slice")
      case InlineImageMarkup.SmartDox =>
        Consequence.operationInvalid("SmartDox content reference normalization is reserved for the next content-format slice")
    }

  def attachReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  )(using ExecutionContext): Consequence[ContentReferenceAttachResult] =
    _attach_inline_images(sourceEntityId, references)

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
      referenceKind = Some("blob"),
      urn = Some(occurrence.normalizedSrc),
      targetEntityId = Some(occurrence.blobId.value),
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
    _resolve_blob_ref(href).map {
      case Some(id) =>
        ContentReferenceOccurrence(
          contentField = Some("content"),
          markup = Some("html-fragment"),
          elementKind = Some("a"),
          attributeName = Some("href"),
          occurrenceIndex = link.index,
          originalRef = Some(link.href),
          normalizedRef = Some(link.href),
          referenceKind = Some("blob"),
          urn = Some(TextusUrn.blob(id.parts.entropy).print),
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
            domain = AssociationDomain.BlobAttachment,
            targetKind = Some("blob"),
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
      domain = AssociationDomain.BlobAttachment,
      sourceEntityId = Some(sourceEntityId),
      targetKind = Some("blob"),
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
          EntityId.parse(value).map(id => Some(_blob_entity_id(id)))
        case None =>
          ref.urn.orElse(ref.normalizedRef).orElse(ref.originalRef)
            .map(_resolve_blob_ref)
            .getOrElse(Consequence.success(None))
      }
    else
      Consequence.success(None)

  private def _is_inline_image_reference(ref: ContentReferenceOccurrence): Boolean =
    ref.elementKind.contains("img") && ref.attributeName.contains("src") && ref.referenceKind.contains("blob")

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
