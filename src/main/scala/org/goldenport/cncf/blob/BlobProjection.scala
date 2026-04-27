package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationDomain, AssociationFilter}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Projection helper for adding Blob metadata to entity-oriented records.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object BlobProjection {
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
          EntityId.parse(association.targetEntityId)
            .flatMap(loaders.loadBlob)
            .map(blob => acc :+ BlobProjectionRow(blob.metadata, association))
        }
      }
    }

  final case class Loaders(
    listAssociations: AssociationFilter => Consequence[Vector[Association]],
    loadBlob: EntityId => Consequence[Blob]
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
