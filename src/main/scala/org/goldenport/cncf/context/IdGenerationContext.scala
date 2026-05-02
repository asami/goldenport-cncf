package org.goldenport.cncf.context

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.id.CompactUuid
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   May.  2, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
trait IdGenerationContext {
  def entityId(collection: EntityCollectionId): EntityId
}

object IdGenerationContext {
  def default(namespace: IdNamespace): IdGenerationContext =
    Nondeterministic(namespace)

  def deterministic(namespace: IdNamespace, seed: String = "test"): IdGenerationContext =
    Deterministic(namespace, _safe_seed(seed))

  final case class IdNamespace(
    major: String,
    minor: String
  )

  private final case class Nondeterministic(
    namespace: IdNamespace
  ) extends IdGenerationContext {
    def entityId(collection: EntityCollectionId): EntityId =
      EntityId(
        namespace.major,
        namespace.minor,
        collection,
        timestamp = Some(Instant.now()),
        entropy = Some(CompactUuid.generateString())
      )
  }

  private final case class Deterministic(
    namespace: IdNamespace,
    seed: String
  ) extends IdGenerationContext {
    private val sequence = AtomicLong(0L)

    def entityId(collection: EntityCollectionId): EntityId = {
      val n = sequence.incrementAndGet()
      EntityId(
        namespace.major,
        namespace.minor,
        collection,
        timestamp = Some(Instant.EPOCH.plusMillis(n)),
        entropy = Some(f"${seed}_${n}%06d")
      )
    }
  }

  private def _safe_seed(value: String): String = {
    val raw = value.trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '_' => c
      case _ => '_'
    }.mkString.replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    val seed = if (raw.nonEmpty) raw else "test"
    (if (seed.headOption.exists(_.isLetter)) seed else s"test_$seed").take(40).stripSuffix("_")
  }
}
