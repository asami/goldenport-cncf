package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.cncf.association.{Association, AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Projection helper for adding Blob metadata to entity-oriented records.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 27, 2026
 * @author  ASAMI, Tomoharu
 */
object BlobProjection {
  def entityBlobRecords(
    sourceEntityId: String
  )(using ExecutionContext): Consequence[Vector[Record]] =
    entityBlobRows(sourceEntityId).map(_.map(_.toRecord))

  def entityBlobRows(
    sourceEntityId: String
  )(using ExecutionContext): Consequence[Vector[BlobProjectionRow]] = {
    val associations = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
    val blobs = BlobRepository.entityStore()
    associations.list(
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
            .flatMap(blobs.get)
            .map(blob => acc :+ BlobProjectionRow(blob.metadata, association))
        }
      }
    }
  }
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
