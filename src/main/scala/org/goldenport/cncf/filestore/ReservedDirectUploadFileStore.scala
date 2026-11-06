package org.goldenport.cncf.filestore

import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.datatype.MimeType

/*
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */

/** Private retained session and server-reserved identity, never caller authority.
  * Restored fields are untrusted and must be validated before credentials or IO.
  * storageRoot observes a provider volume, not a selected directory or grant.
  */
final case class DirectFileUploadReservation(
  upload: DirectFileUpload,
  fileReference: FileStoreRef,
  storageRoot: Option[FileStoreRef]
) {
  override def toString: String = "DirectFileUploadReservation(<redacted>)"
}

/** Optional new-file reservation and independently bound completion observation.
  * No expected-digest verification or domain registration is implied.
  */
trait ReservedDirectUploadFileStore extends DirectUploadFileStore {
  def prepareReservedDirectUpload(parent: FileStoreRef, name: String, mediaType: MimeType,
    byteLength: Long): ExecUowM[DirectFileUploadReservation]
  def inspectReservedDirectUpload(reservation: DirectFileUploadReservation): ExecUowM[DirectFileUploadStatus]
}
