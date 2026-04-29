package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationDomain, AssociationFilter}
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Projection helper for adding Blob metadata to entity-oriented records.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 30, 2026
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
    loaders.listAssociations(
      AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some(sourceEntityId),
        targetKind = Some("blob")
      )
    ).flatMap { rows =>
      val ordered = rows.sortBy(x => (x.sortOrder.getOrElse(Int.MaxValue), x.associationId))
      ordered.foldLeft(Consequence.success(Vector.empty[BlobProjectionRow])) { (z, association) =>
        z.flatMap { acc =>
          _blob_entity_id(association.targetEntityId)
            .flatMap(loaders.loadBlob)
            .map(blob => acc :+ BlobProjectionRow(blob.metadata, association))
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
    loadBlob: EntityId => Consequence[Blob]
  )

  private def _normalize_role(role: String): String =
    Option(role).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT)

  private def _blob_entity_id(value: String): Consequence[EntityId] =
    UniversalId.parseParts(value, "entity").map { parts =>
      EntityId(
        major = parts.major,
        minor = parts.minor,
        collection = BlobRepository.CollectionId,
        timestamp = Some(parts.timestamp),
        entropy = Some(parts.entropy)
      )
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
