package org.goldenport.cncf.component.cache

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.component.repository.ComponentRepository

/** Managed payload identity is independent of a mutable repository filename.
  * @since Oct. 21, 2026
 * @version Oct. 21, 2026
  */
enum ArchiveKind(val directory: String, val suffix: String, val profile: String) {
  case Car extends ArchiveKind("car", ".car", "car-v1")
  case Sar extends ArchiveKind("sar", ".sar", "sar-v1")
}

final case class SourceGeneration(kind: ArchiveKind, digest: String) {
  require(digest.matches("[0-9a-f]{64}"), "invalid managed source generation")
  private[cache] def key: String = s"source-${kind.directory}-$digest"
}

final case class ExtractionGeneration(source: SourceGeneration) {
  def profile: String = source.kind.profile
  private[cache] def key: String = s"extracted-${source.kind.directory}-${source.digest}-$profile"
}

final class SourceLease private[cache] (
  val generation: SourceGeneration,
  protectedPath: Path,
  release: () => Unit
) extends AutoCloseable {
  private val _closed = new AtomicBoolean(false)
  def path: Path = {
    checkOpen()
    protectedPath
  }
  private[cache] def checkOpen(): Unit =
    if (_closed.get()) throw new IllegalStateException("managed source lease is closed")
  def close(): Unit = if (_closed.compareAndSet(false, true)) release()
}

final class ExtractionLease[A] private[cache] (
  val generation: ExtractionGeneration,
  protectedValue: A,
  release: () => Unit
) extends AutoCloseable {
  private val _closed = new AtomicBoolean(false)
  def value: A = {
    if (_closed.get()) throw new IllegalStateException("managed extraction lease is closed")
    protectedValue
  }
  def close(): Unit = if (_closed.compareAndSet(false, true)) release()
}

final case class CacheFailure(
  reason: CacheFailure.Reason,
  operation: String,
  entry: String,
  requiredBytes: Option[Long] = None,
  availableBytes: Option[Long] = None,
  detail: String = ""
) extends RuntimeException(
  s"managed cache reason=$reason operation=$operation entry=$entry" +
    requiredBytes.fold("")(value => s" requiredBytes=$value") +
    availableBytes.fold("")(value => s" availableBytes=$value") +
    (if (detail.isEmpty) "" else s" detail=$detail")
)

object CacheFailure {
  enum Reason {
    case UnsupportedFilesystem, CapacityExceeded, ContentionExceeded, SourceChanged
    case PublicationFailed, DeletionFailed, PolicyConflict
  }
}

final case class ManagedCachePolicy(
  root: Path,
  extractedMaxBytes: Long = 8589934592L,
  sourceMaxBytes: Long = 17179869184L,
  stagingMaxBytes: Long = 2147483648L,
  sourceEntryMaxBytes: Long = 536870912L,
  extractedEntryMaxBytes: Long = 2147483648L,
  idleMillis: Long = 86400000L,
  evictReusableSources: Boolean = false,
  collectionMaxEntries: Int = 128,
  collectionMaxBytes: Long = 268435456L,
  acquisitionMaxAttempts: Int = 64,
  acquisitionMaxWaitMillis: Long = 120000L,
  reservationQuantumBytes: Long = 1048576L
)

object ManagedCachePolicy {
  private val _prefix = "cncf.component.cache."

  def fromC(
    configuration: ResolvedConfiguration,
    repositoryRoot: Option[Path] = None
  ): Consequence[ManagedCachePolicy] = Consequence {
    val values = configuration.configuration.values
    def _invalid_(key: String): Nothing = throw CacheFailure(
      CacheFailure.Reason.PolicyConflict, "configure", key, detail = "invalid managed cache configuration")
    def _number_(key: String, default: Long, zero: Boolean = false): Long = {
      val result = values.get(_prefix + key) match {
        case None => default
        case Some(ConfigurationValue.NumberValue(value)) if value.isWhole && value.isValidLong => value.toLong
        case _ => _invalid_(key)
      }
      if (result < 0 || (!zero && result == 0)) _invalid_(key)
      result
    }
    def _integer_(key: String, default: Int): Int = {
      val value = _number_(key, default.toLong)
      if (value > Int.MaxValue) _invalid_(key)
      value.toInt
    }
    val configuredroot = values.get(_prefix + "root") match {
      case None => ComponentRepository.defaultStandardRepositoryDir()
      case Some(ConfigurationValue.StringValue(value)) if value.trim.nonEmpty => Path.of(value)
      case _ => _invalid_("root")
    }
    val eviction = values.get(_prefix + "source.evict-reusable") match {
      case None => false
      case Some(ConfigurationValue.BooleanValue(value)) => value
      case _ => _invalid_("source.evict-reusable")
    }
    val policy = ManagedCachePolicy(
      repositoryRoot.getOrElse(configuredroot).toAbsolutePath.normalize,
      _number_("extracted.max-bytes", 8589934592L),
      _number_("source.max-bytes", 17179869184L),
      _number_("staging.max-bytes", 2147483648L),
      _number_("source-entry.max-bytes", 536870912L),
      _number_("extracted-entry.max-bytes", 2147483648L),
      _number_("extracted.idle-millis", 86400000L, zero = true),
      eviction,
      _integer_("collection.max-entries", 128),
      _number_("collection.max-bytes", 268435456L),
      _integer_("acquisition.max-attempts", 64),
      _number_("acquisition.max-wait-millis", 120000L),
      _number_("reservation.quantum-bytes", 1048576L)
    )
    if (policy.sourceEntryMaxBytes > policy.sourceMaxBytes ||
        policy.extractedEntryMaxBytes > policy.extractedMaxBytes ||
        policy.reservationQuantumBytes > math.min(policy.sourceMaxBytes, policy.extractedMaxBytes))
      _invalid_("capacity")
    policy
  }
}

enum CacheCollectionDisposition {
  case Reclaimed, WouldReclaim, InUse, Retained, Failed
}

final case class CacheCollectionEntry(
  generation: ExtractionGeneration,
  bytes: Long,
  disposition: CacheCollectionDisposition,
  reason: Option[String] = None
)

final case class CacheCollectionResult(entries: Vector[CacheCollectionEntry] = Vector.empty) {
  def reclaimedBytes: Long = entries.filter(_.disposition == CacheCollectionDisposition.Reclaimed).map(_.bytes).sum
  def reclaimedCount: Int = entries.count(_.disposition == CacheCollectionDisposition.Reclaimed)
}

object CacheCollectionResult {
  val empty: CacheCollectionResult = new CacheCollectionResult()
  def apply(): CacheCollectionResult = empty
}

final case class CacheInspection(sourceBytes: Long, extractedBytes: Long, stagingBytes: Long)
