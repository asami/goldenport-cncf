package org.goldenport.cncf.filestore

import org.goldenport.cncf.unitofwork.ExecUowM

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 */
/** One independent page; callers own pagination and aggregate read budgets.
  * Neither child observations nor opaque continuation data confer authority.
  */
final case class FileStoreChildPage(
  items: Vector[FileStoreMetadata],
  nextPageToken: Option[String]
) {
  override def toString: String = "FileStoreChildPage(<redacted>)"
}

/** Optional bounded child reads, without changing the base four-operation SPI.
  * Names are display labels, not identities. No hidden stat, recursion or
  * automatic pagination is implied; empty pages may retain a continuation.
  */
trait FileStoreChildren extends FileStore {
  def listChildren(parent: FileStoreRef, limit: Int, pageToken: Option[String] = None): ExecUowM[FileStoreChildPage]
}
