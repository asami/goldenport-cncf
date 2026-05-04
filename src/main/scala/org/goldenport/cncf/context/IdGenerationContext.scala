package org.goldenport.cncf.context

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.id.CompactUuid
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   May.  2, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
trait IdGenerationContext {
  def namespace: IdGenerationContext.IdNamespace
  def entityId(collection: EntityCollectionId): EntityId
}

object IdGenerationContext {
  val DefaultNamespace: IdNamespace = IdNamespace("single", "global")

  def default(namespace: IdNamespace): IdGenerationContext =
    Nondeterministic(namespace)

  def deterministic(namespace: IdNamespace, seed: String = "test"): IdGenerationContext =
    Deterministic(namespace, _safe_seed(seed))

  final case class IdNamespace(
    major: String,
    minor: String
  )

  object IdNamespace {
    def normalize(
      major: String,
      minor: String
    ): Either[String, IdNamespace] =
      for {
        majorlabel <- normalizeLabel(major).toRight(s"invalid id namespace major: ${major}")
        minorlabel <- normalizeLabel(minor).toRight(s"invalid id namespace minor: ${minor}")
      } yield IdNamespace(majorlabel, minorlabel)

    def normalizeOrThrow(
      major: String,
      minor: String
    ): IdNamespace =
      normalize(major, minor) match {
        case Right(namespace) => namespace
        case Left(message) => throw new IllegalArgumentException(message)
      }

    def normalizeLabel(value: String): Option[String] = {
      val raw = Option(value).getOrElse("").trim.toLowerCase(java.util.Locale.ROOT).map {
        case c if _is_ascii_letter(c) || _is_ascii_digit(c) || c == '_' => c
        case '-' | '.' => '_'
        case _ => '_'
      }.mkString.replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
      val label =
        if (raw.nonEmpty && raw.headOption.exists(_is_ascii_letter)) raw
        else if (raw.nonEmpty) s"n_${raw}"
        else ""
      if (label.exists(c => _is_ascii_letter(c) || _is_ascii_digit(c) || c == '_') && label.headOption.exists(_is_ascii_letter))
        Some(label.take(64).stripSuffix("_")).filter(_.nonEmpty)
      else
        None
    }

    private def _is_ascii_letter(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

    private def _is_ascii_digit(c: Char): Boolean =
      c >= '0' && c <= '9'
  }

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
