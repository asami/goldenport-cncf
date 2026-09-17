package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationDomain, AssociationFilter}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Projection helper for adding Blob metadata to entity-oriented records.
 *
 * @since   Apr. 27, 2026
 *  version Apr. 30, 2026
 *  version May.  4, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object BlobProjection {
  val RepresentativeRolePriority: Vector[String] =
    Vector("primary", "cover", "thumbnail", "inline")

  def entityImageProjectionRecord(
    sourceEntityId: String
  )(
    loaders: BlobProjection.Loaders
  ): Consequence[Record] =
    entityImageProjection(sourceEntityId)(loaders).map(_.toRecord)

  def entityImageProjection(
    sourceEntityId: String
  )(
    loaders: BlobProjection.Loaders
  ): Consequence[BlobProjectionResult] =
    entityBlobRows(sourceEntityId)(loaders).map { rows =>
      val ordered = orderedRows(rows)
      BlobProjectionResult(ordered, representativeImage(ordered))
    }

  def entityBlobRecords(
    sourceEntityId: String
  )(
    loaders: BlobProjection.Loaders
  ): Consequence[Vector[Record]] =
    entityBlobRows(sourceEntityId)(loaders).map(_.map(_.toRecord))

  def entityBlobRows(
    sourceEntityId: String
  )(
    loaders: BlobProjection.Loaders
  ): Consequence[Vector[BlobProjectionRow]] =
    val blobRows = loaders.listAssociations(
      AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetKind = Some("blob")
      )
    )
    val mediaRows = loaders.listAssociations(
      AssociationFilter(
        domain = AssociationDomain.MediaAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetKind = Some("image")
      )
    )
    blobRows.flatMap { blobs =>
      mediaRows.flatMap { media =>
        val ordered = (blobs ++ media).sortBy(x => (x.sortOrder.getOrElse(Int.MaxValue), x.associationId))
        ordered.foldLeft(Consequence.success(Vector.empty[BlobProjectionRow])) { (z, association) =>
          z.flatMap { acc =>
            _association_blob_id(association, loaders)
              .flatMap(loaders.loadBlob)
              .map(blob => acc :+ BlobProjectionRow(blob.metadata, association))
          }
        }
      }
    }

  def representativeImage(
    rows: Vector[BlobProjectionRow]
  ): Option[BlobProjectionRow] = {
    val ordered = orderedRows(rows)
    RepresentativeRolePriority.iterator.flatMap { role =>
      ordered.find(row => _normalize_role(row.association.role) == role)
    }.nextOption()
  }

  def orderedRows(
    rows: Vector[BlobProjectionRow]
  ): Vector[BlobProjectionRow] =
    rows.sortBy(row => (row.association.sortOrder.getOrElse(Int.MaxValue), row.association.associationId))

  final case class Loaders(
    listAssociations: AssociationFilter => Consequence[Vector[Association]],
    loadBlob: EntityId => Consequence[Blob],
    loadMedia: EntityId => Consequence[MediaEntity] = id => Consequence.operationNotFound(s"media entity:${id.value}")
  )

  private def _normalize_role(role: String): String =
    Option(role).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)

  private def _blob_entity_id(value: String): Consequence[EntityId] =
    EntityId.parse(value).flatMap { id =>
      if (id.collection == BlobRepository.CollectionId)
        EntityId.bridgeFromParts(
          major = id.major,
          minor = id.minor,
          collection = id.collection,
          timestamp = id.timestamp.get,
          entropy = id.entropy.get
        )
      else
        Consequence.argumentInvalid(
          s"blob projection id collection mismatch: expected ${BlobRepository.CollectionId.print}, got ${id.collection.print}"
        )
    }

  private def _association_blob_id(
    association: Association,
    loaders: Loaders
  ): Consequence[EntityId] =
    association.associationDomain match {
      case AssociationDomain.BlobAttachment =>
        _blob_entity_id(association.targetEntityId)
      case AssociationDomain.MediaAttachment =>
        _media_entity_id(association.targetEntityId, association.targetKind)
          .flatMap(loaders.loadMedia)
          .map(_.blobId)
      case other =>
        Consequence.argumentInvalid(s"unsupported projection association domain: ${other.value}")
    }

  private def _media_entity_id(
    value: String,
    targetKind: Option[String]
  ): Consequence[EntityId] =
    EntityId.parse(value).flatMap { id =>
      val kind = targetKind.getOrElse("image")
      MediaKind.parse(kind).flatMap { mediaKind =>
        val expected = MediaEntityCollections.collection(mediaKind)
        if (id.collection == expected)
          EntityId.bridgeFromParts(
            major = id.major,
            minor = id.minor,
            collection = id.collection,
            timestamp = id.timestamp.get,
            entropy = id.entropy.get
          )
        else
          Consequence.argumentInvalid(
            s"media projection id collection mismatch: expected ${expected.print}, got ${id.collection.print}"
          )
      }
    }
}

final case class BlobProjectionResult(
  images: Vector[BlobProjectionRow],
  representativeImage: Option[BlobProjectionRow]
) {
  def toRecord: Record =
    Record.dataAuto(
      "images" -> images.map(_.toRecord),
      "representativeImage" -> representativeImage.map(_.toRecord)
    )
}

final case class BlobProjectionRow(
  metadata: BlobMetadata,
  association: Association
) {
  def toRecord: Record =
    metadata.toRecord ++ Record.dataAuto(
      "associationId" -> association.associationId,
      "role" -> association.role,
      "sortOrder" -> association.sortOrder
    )
}
