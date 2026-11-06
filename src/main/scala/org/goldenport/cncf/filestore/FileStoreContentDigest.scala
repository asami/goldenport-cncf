package org.goldenport.cncf.filestore

import java.util.Locale
import org.goldenport.Consequence

/*
 * @since   Oct. 25, 2026
 * @version Nov.  6, 2026
 */

/** An observed external binary checksum, not workflow or registration authority. */
final class FileStoreContentDigest private (val value: String) {
  val algorithm: String = "sha256"

  override def equals(other: Any): Boolean = other match {
    case digest: FileStoreContentDigest => algorithm == digest.algorithm && value == digest.value
    case _ => false
  }

  override def hashCode(): Int = (algorithm, value).hashCode()

  override def toString: String = "FileStoreContentDigest(sha256)"
}

object FileStoreContentDigest {
  /** Accept exactly 64 ASCII hex characters, without trimming or coercion. */
  def sha256C(value: String): Consequence[FileStoreContentDigest] =
    if (Option(value).exists(text => text.length == 64 && text.forall(character =>
        (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f') ||
          (character >= 'A' && character <= 'F'))))
      Consequence.success(new FileStoreContentDigest(value.toLowerCase(Locale.ROOT)))
    else Consequence.argumentInvalid("Invalid file store content digest")
}
