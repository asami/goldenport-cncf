package org.goldenport.cncf.datatype

import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.convert.ValueReader
import io.circe.Codec

/*
 * @since   Apr. 11, 2025
 *  version Feb. 27, 2026
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityId(
  major: String,
  minor: String,
  collection: EntityCollectionId
) extends UniversalId(major, minor, "entity", collection.name) derives Codec.AsObject {
  private var _parsedValue: Option[String] = None

  override def value: String =
    _parsedValue.getOrElse(super.value)

  private[datatype] def withParsedValue(p: String): EntityId = {
    _parsedValue = Some(p)
    this
  }
}

object EntityId {
  given ValueReader[EntityId] with {
    def readC(v: Any): Consequence[EntityId] = Option(v) match {
      case None => Consequence.failure("Invalid EntityId value: null")
      case Some(value) => value match {
        case id: EntityId => Consequence.success(id)
        case s: String => parse(s)
        case other => parse(other.toString)
      }
    }
  }

  def parse(s: String): Consequence[EntityId] = {
    UniversalId.parseParts(s, "entity").flatMap { parts =>
      parts.subkind match {
        case Some(name) =>
          Consequence.success(
            EntityId(
              major = parts.major,
              minor = parts.minor,
              collection = EntityCollectionId(parts.major, parts.minor, name)
            ).withParsedValue(parts.value)
          )
        case None =>
          Consequence.failure(s"Invalid EntityId format: missing collection name in '$s'")
      }
    }
  }
}

final case class EntityCollectionId(
  major: String,
  minor: String,
  name: String,
) extends UniversalId(major, minor, "entity_collection", name) derives Codec.AsObject

object EntityCollectionId {
  given ValueReader[EntityCollectionId] with {
    def readC(v: Any): Consequence[EntityCollectionId] = Option(v) match {
      case None => Consequence.failure("Invalid EntityCollectionId value: null")
      case Some(value) => value match {
        case id: EntityCollectionId => Consequence.success(id)
        case s: String => ???
        case other => ???
      }
    }
  }
}

final case class AggregateCollectionId(
  major: String,
  minor: String,
  name: String,
) extends UniversalId(major, minor, "aggregate_collection", name) derives Codec.AsObject

object AggregateCollectionId {
  given ValueReader[AggregateCollectionId] with {
    def readC(v: Any): Consequence[AggregateCollectionId] = Option(v) match {
      case None => Consequence.failure("Invalid AggregateCollectionId value: null")
      case Some(value) => value match {
        case id: AggregateCollectionId => Consequence.success(id)
        case s: String => ???
        case other => ???
      }
    }
  }
}

final case class ViewCollectionId(
  major: String,
  minor: String,
  name: String,
) extends UniversalId(major, minor, "view_collection", name) derives Codec.AsObject

object ViewCollectionId {
  given ValueReader[ViewCollectionId] with {
    def readC(v: Any): Consequence[ViewCollectionId] = Option(v) match {
      case None => Consequence.failure("Invalid ViewCollectionId value: null")
      case Some(value) => value match {
        case id: ViewCollectionId => Consequence.success(id)
        case s: String => ???
        case other => ???
      }
    }
  }
}
