package org.goldenport.cncf.filestore

import org.goldenport.cncf.unitofwork.ExecUowM

/** Application-owned binding. The owning operation must authenticate and authorize
  * the caller and verify this binding against its own durable checkpoint.
  * None of these values is authority or evidence by itself.
  */
final case class FileStoreWriteBinding(operation: String, caller: String, parent: FileStoreRef, name: String)

/** A reserved opaque identity, to be durably retained before submission. */
final case class FileStoreCollectionReservation(binding: FileStoreWriteBinding, reference: FileStoreRef,
  storageRoot: Option[FileStoreRef])

sealed trait FileStoreWriteState
object FileStoreWriteState {
  case object NotDispatched extends FileStoreWriteState
  case object PossiblyDispatched extends FileStoreWriteState
  final case class Confirmed(metadata: FileStoreMetadata) extends FileStoreWriteState
  case object Unknown extends FileStoreWriteState
}

final case class FileStoreWriteObservation(reservation: FileStoreCollectionReservation, state: FileStoreWriteState) {
  override def toString: String = "FileStoreWriteObservation(<redacted>)"
  def matches(expected: FileStoreCollectionReservation): Boolean = reservation == expected
}

/** Optional capability. Reservation is distinct from submission; an uncertain
  * submission is never retried automatically. Inspection is read-only and an
  * absent provider result does not prove non-dispatch or non-creation.
  */
trait ObservedCollectionFileStore extends FileStore {
  def reserveCollection(binding: FileStoreWriteBinding): ExecUowM[FileStoreCollectionReservation]
  def createReservedCollection(reservation: FileStoreCollectionReservation): ExecUowM[FileStoreWriteObservation]
  def inspectReservedCollection(reservation: FileStoreCollectionReservation): ExecUowM[FileStoreWriteObservation]
}
