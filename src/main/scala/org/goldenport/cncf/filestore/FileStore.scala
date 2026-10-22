package org.goldenport.cncf.filestore

import org.goldenport.bag.Bag
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.datatype.MimeType

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 */

/** Configured store identity and provider-opaque physical key, never authority.
  * A key need not be a URI or domain Entity/Material ID; slash and Unicode keys
  * are permitted, including configured S3 bucket/prefix/key mappings.
  */
final case class FileStoreRef(store: String, key: String)

/** A Collection groups objects; a provider prefix need not be a physical folder. */
enum FileStoreKind {
  case File, Collection
}

/** Unknown size/version remain None. Version is an observation only and does
  * not promise version-pinned reads. Names are display labels, not identities,
  * unique paths, or grants; duplicate labels are permitted.
  */
final case class FileStoreMetadata(
  reference: FileStoreRef,
  name: String,
  mediaType: MimeType,
  byteLength: Option[Long],
  parents: Vector[FileStoreRef],
  kind: FileStoreKind,
  version: Option[String] = None
)

/** A materialized value that remains readable after UnitOfWork completion. */
final case class FileStoreContent(mediaType: MimeType, content: Bag)

/** Lower provider-neutral storage capability expressed as native UoW programs.
  *
  * The owning Operation must establish entity/source authorization before using
  * the provider. A reference or stronger provider credential never confers it;
  * runtime HTTP host admission is an additional boundary. Implementations must
  * construct native operations, without a generic Eval or interpreter escape.
  *
  * stat and read are independent: callers can validate expected parents, MIME
  * and length after stat before read. read must not hide an additional stat.
  * Creation issues a fresh provider reference and never replaces an object,
  * including when display names duplicate an earlier object's name.
  *
  * Input Bags are materialized values whose backing storage belongs to callers.
  * A provider borrows a Bag only during one interpreted write; the driver closes
  * each opened stream exactly once on success or failure. Returned Bags must
  * be materialized, not live HTTP streams. Do not eagerly copy/hash unbounded
  * content into temporary protocol encodings. Providers must reject URL-backed
  * input Bags to avoid another unadmitted network read and should require known
  * nonnegative size when their wire protocol needs it. This trait does not
  * invent global runtime checks that only a provider can enforce.
  *
  * Write errors after submission may leave an unconfirmed remote write. Never
  * automatically retry/replay, delete as rollback, or claim nothing was written.
  * Callers must keep incomplete package attempts out of accepted Material state.
  *
  * No list/delete/rename/move/copy/accessURL, automatic digest, domain registry
  * or managed BlobStore migration is implied. An optional thin read-only
  * FileSystemView is a separate presentation capability, not this SPI.
  */
trait FileStore {
  def name: String
  def stat(reference: FileStoreRef): ExecUowM[FileStoreMetadata]
  def read(reference: FileStoreRef): ExecUowM[FileStoreContent]
  def createCollection(parent: FileStoreRef, name: String): ExecUowM[FileStoreMetadata]
  def create(parent: FileStoreRef, name: String, mediaType: MimeType, content: Bag): ExecUowM[FileStoreMetadata]
}
