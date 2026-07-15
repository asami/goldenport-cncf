package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.goldenport.context.EntropyContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   May.  2, 2026
 *  version May.  5, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait IdGenerationContext {
  def namespace: IdGenerationContext.IdNamespace

  def entityId(collection: EntityCollectionId): EntityId =
    entityId(collection, "entity-create")

  def entityId(collection: EntityCollectionId, purpose: String): EntityId

  def entityIdInCollectionNamespace(
    collection: EntityCollectionId,
    purpose: String
  ): EntityId

  def opaqueId(purpose: String): String
}

object IdGenerationContext {
  val DefaultNamespace: IdNamespace = IdNamespace("single", "global")

  def default(namespace: IdNamespace): IdGenerationContext =
    production(namespace, Clock.systemUTC(), EntropyContext.secure())

  def default(namespace: IdNamespace, clock: Clock): IdGenerationContext =
    production(namespace, clock, EntropyContext.secure())

  def production(
    namespace: IdNamespace,
    clock: Clock,
    entropy: EntropyContext
  ): IdGenerationContext =
    Context(namespace, clock, entropy)

  def deterministic(namespace: IdNamespace, seed: String = "test"): IdGenerationContext =
    deterministic(
      namespace,
      Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
      seed
    )

  def deterministic(
    namespace: IdNamespace,
    clock: Clock,
    seed: String
  ): IdGenerationContext =
    Context(namespace, clock, EntropyContext.deterministic(_safe_seed(seed)))

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

  private final case class Context(
    namespace: IdNamespace,
    clock: Clock,
    entropy: EntropyContext
  ) extends IdGenerationContext {
    private val _sequences = new ConcurrentHashMap[String, AtomicLong]()

    def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
      _entity_id(namespace, collection, purpose)
    }

    def entityIdInCollectionNamespace(
      collection: EntityCollectionId,
      purpose: String
    ): EntityId =
      _entity_id(IdNamespace(collection.major, collection.minor), collection, purpose)

    private def _entity_id(
      idnamespace: IdNamespace,
      collection: EntityCollectionId,
      purpose: String
    ): EntityId = {
      val key = s"entity.${_collection_key(collection)}.${_purpose(purpose)}"
      EntityId(
        idnamespace.major,
        idnamespace.minor,
        collection,
        timestamp = Some(clock.instant()),
        entropy = Some(_token(key))
      )
    }

    def opaqueId(purpose: String): String =
      _token(s"opaque.${_purpose(purpose)}")

    private def _token(key: String): String = {
      val sequence = _sequences.computeIfAbsent(key, _ => new AtomicLong(0L)).incrementAndGet()
      _hex(entropy.bytes(s"$key.$sequence", 16))
    }
  }

  private def _collection_key(collection: EntityCollectionId): String =
    Vector(collection.major, collection.minor, collection.name)
      .map(_purpose)
      .mkString(".")

  private def _purpose(value: String): String = {
    val source = Option(value).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
    require(source.nonEmpty, "ID purpose must not be empty")
    source.map {
      case c if c >= 'a' && c <= 'z' => c
      case c if c >= '0' && c <= '9' => c
      case c @ ('.' | '-' | '_') => c
      case _ => '_'
    }
  }

  private def _hex(bytes: Array[Byte]): String =
    bytes.iterator.map(byte => f"${byte & 0xff}%02x").mkString

  private def _safe_seed(value: String): String = {
    val raw = value.trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '_' => c
      case _ => '_'
    }.mkString.replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    val seed = if (raw.nonEmpty) raw else "test"
    (if (seed.headOption.exists(_.isLetter)) seed else s"test_$seed").take(40).stripSuffix("_")
  }
}
