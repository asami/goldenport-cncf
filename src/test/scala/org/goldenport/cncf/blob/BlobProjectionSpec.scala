package org.goldenport.cncf.blob

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationDomain, AssociationFilter, AssociationStoragePolicy}
import org.goldenport.datatype.ContentType
import org.goldenport.id.UniversalId
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for BlobAttachment projection helpers.
 *
 * @since   Apr. 30, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "BlobProjection" should {
    "return associated image rows in deterministic sort order" in {
      Given("Blob attachments with out-of-order sort values")
      val associations = Vector(
        _association("article-order", "gallery", "blob-order-3", Some(3), "assoc-order-3"),
        _association("article-order", "gallery", "blob-order-1", Some(1), "assoc-order-1"),
        _association("article-order", "gallery", "blob-order-2", Some(2), "assoc-order-2")
      )

      When("projecting associated image rows")
      val projection = _success(BlobProjection.entityImageProjection("article-order")(_loaders(associations)))

      Then("images are ordered by sortOrder")
      projection.images.map(_.metadata.id.minor) shouldBe Vector("blob_order_1", "blob_order_2", "blob_order_3")
    }

    "select primary before cover" in {
      val projection = _success(BlobProjection.entityImageProjection("article-primary")(_loaders(Vector(
        _association("article-primary", "cover", "blob-cover", Some(1), "assoc-cover"),
        _association("article-primary", "primary", "blob-primary", Some(2), "assoc-primary")
      ))))

      projection.representativeImage.map(_.association.role) shouldBe Some("primary")
    }

    "select cover before thumbnail" in {
      val projection = _success(BlobProjection.entityImageProjection("article-cover")(_loaders(Vector(
        _association("article-cover", "thumbnail", "blob-thumbnail", Some(1), "assoc-thumbnail"),
        _association("article-cover", "cover", "blob-cover", Some(2), "assoc-cover")
      ))))

      projection.representativeImage.map(_.association.role) shouldBe Some("cover")
    }

    "select thumbnail before inline" in {
      val projection = _success(BlobProjection.entityImageProjection("article-thumbnail")(_loaders(Vector(
        _association("article-thumbnail", "inline", "blob-inline", Some(1), "assoc-inline"),
        _association("article-thumbnail", "thumbnail", "blob-thumbnail", Some(2), "assoc-thumbnail")
      ))))

      projection.representativeImage.map(_.association.role) shouldBe Some("thumbnail")
    }

    "select first inline image by occurrence order as representative fallback" in {
      val projection = _success(BlobProjection.entityImageProjection("article-inline")(_loaders(Vector(
        _association("article-inline", "inline", "blob-inline-2", Some(2), "assoc-inline-2"),
        _association("article-inline", "inline", "blob-inline-1", Some(1), "assoc-inline-1")
      ))))

      projection.representativeImage.map(_.metadata.id.minor) shouldBe Some("blob_inline_1")
    }

    "not select gallery when it is the only image role" in {
      val projection = _success(BlobProjection.entityImageProjection("article-gallery")(_loaders(Vector(
        _association("article-gallery", "gallery", "blob-gallery", Some(1), "assoc-gallery")
      ))))

      projection.representativeImage shouldBe None
    }

    "render projection record with images and representativeImage" in {
      val record = _success(BlobProjection.entityImageProjectionRecord("article-record")(_loaders(Vector(
        _association("article-record", "inline", "blob-inline", Some(1), "assoc-inline")
      ))))

      record.getVector("images").map(_.size) shouldBe Some(1)
      record.getRecord("representativeImage").flatMap(_.getString("role")) shouldBe Some("inline")
    }
  }

  private def _loaders(
    associations: Vector[Association]
  ): BlobProjection.Loaders = {
    val blobs = associations.map(a => _blob(_blob_id_from_value(a.targetEntityId))).map(blob => blob.id -> blob).toMap
    BlobProjection.Loaders(
      listAssociations = filter => Consequence.success(_filter(associations, filter)),
      loadBlob = id => {
        id.collection shouldBe BlobRepository.CollectionId
        blobs.get(id).map(Consequence.success).getOrElse(Consequence.entityNotFound(s"blob:${id.value}"))
      }
    )
  }

  private def _filter(
    associations: Vector[Association],
    filter: AssociationFilter
  ): Vector[Association] =
    associations.filter { association =>
      association.associationDomain == filter.domain &&
        filter.sourceEntityId.forall(_ == association.sourceEntityId) &&
        filter.targetEntityId.forall(_ == association.targetEntityId) &&
        filter.targetKind.forall(value => association.targetKind.contains(value)) &&
        filter.role.forall(_ == association.role)
    }

  private def _association(
    source: String,
    role: String,
    blobMinor: String,
    sortOrder: Option[Int],
    associationId: String
  ): Association =
    Association(
      id = EntityId("cncf", _safe_minor(associationId), AssociationStoragePolicy.BlobAttachmentCollection),
      associationId = associationId,
      sourceEntityId = source,
      targetEntityId = _blob_id(blobMinor).value,
      targetKind = Some("blob"),
      role = role,
      associationDomain = AssociationDomain.BlobAttachment,
      sortOrder = sortOrder,
      createdAt = _now,
      updatedAt = _now
    )

  private def _blob(
    id: EntityId
  ): Blob =
    Blob(
      id = id,
      kind = BlobKind.Image,
      sourceMode = BlobSourceMode.Managed,
      filename = Some(s"${id.minor}.png"),
      contentType = Some(ContentType.IMAGE_PNG),
      byteSize = Some(1L),
      digest = Some(s"digest-${id.minor}"),
      storageRef = None,
      externalUrl = None,
      accessUrl = BlobUrl.cncfRoute(id),
      createdAt = _now,
      updatedAt = _now
    )

  private def _blob_id(
    minor: String
  ): EntityId =
    EntityId(BlobRepository.CollectionId.major, _safe_minor(minor), BlobRepository.CollectionId)

  private def _safe_minor(
    value: String
  ): String =
    value.replace("-", "_")

  private def _blob_id_from_value(
    value: String
  ): EntityId =
    UniversalId.parseParts(value, "entity") match {
      case Consequence.Success(parts) =>
        EntityId(
          major = parts.major,
          minor = parts.minor,
          collection = BlobRepository.CollectionId,
          timestamp = Some(parts.timestamp),
          entropy = Some(parts.entropy)
        )
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private val _now: Instant =
    Instant.parse("2026-04-30T00:00:00Z")

  private def _success[A](
    result: Consequence[A]
  ): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
