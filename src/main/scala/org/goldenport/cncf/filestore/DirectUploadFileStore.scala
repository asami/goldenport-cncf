package org.goldenport.cncf.filestore

import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.datatype.MimeType

/*
 * Optional preparation/status capability. FileStore callers remain compatible.
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */

/** Sensitive provider session, never a domain identity or general credential.
  * The provider must validate every field on restoration before outbound IO.
  * The owning server retains this privately; client status must not contain it.
  */
final case class DirectFileUpload(
  parent: FileStoreRef, name: String, mediaType: MimeType, byteLength: Long,
  protocol: String, uploadUri: String
) {
  override def toString: String = "DirectFileUpload(<redacted>)"
}

enum DirectFileUploadStatus {
  case Pending(nextOffset: Long)
  case Completed(metadata: FileStoreMetadata)
  case Expired
}

trait DirectUploadFileStore extends FileStore {
  def directUploadProtocol: String
  def prepareDirectUpload(parent: FileStoreRef, name: String, mediaType: MimeType,
    byteLength: Long): ExecUowM[DirectFileUpload]
  def inspectDirectUpload(upload: DirectFileUpload): ExecUowM[DirectFileUploadStatus]
}
