package org.goldenport.cncf.blob

import scala.collection.concurrent.TrieMap
import org.goldenport.Consequence

/*
 * Minimal metadata repository for Blob user operations.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
trait BlobMetadataRepository {
  def save(metadata: BlobMetadata): Consequence[BlobMetadata]
  def get(blobId: BlobId): Consequence[BlobMetadata]
  def list(): Consequence[Vector[BlobMetadata]]
}

object BlobMetadataRepository {
  def inMemory(): BlobMetadataRepository =
    new InMemoryBlobMetadataRepository()
}

final class InMemoryBlobMetadataRepository extends BlobMetadataRepository {
  private val _entries = TrieMap.empty[String, BlobMetadata]

  def save(metadata: BlobMetadata): Consequence[BlobMetadata] = {
    _entries.update(metadata.blobId.value, metadata)
    Consequence.success(metadata)
  }

  def get(blobId: BlobId): Consequence[BlobMetadata] =
    _entries.get(blobId.value) match {
      case Some(metadata) => Consequence.success(metadata)
      case None => Consequence.operationNotFound(s"blob metadata:${blobId.value}")
    }

  def list(): Consequence[Vector[BlobMetadata]] =
    Consequence.success(_entries.values.toVector.sortBy(_.createdAt.toString))
}
