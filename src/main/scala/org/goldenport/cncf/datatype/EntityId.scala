package org.goldenport.cncf.datatype

import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.convert.ValueReader
import io.circe.Codec

/*
 * @since   Apr. 11, 2025
 *  version Feb. 27, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityId(
  major: String,
  minor: String,
  collection: EntityCollectionId
) extends UniversalId(major, minor, "entity", collection.name) derives Codec.AsObject

object EntityId {
  given ValueReader[EntityId] with {
    def readC(v: Any): Consequence[EntityId] = Option(v) match {
      case None => Consequence.failure("Invalid EntityId value: null")
      case Some(value) => value match {
        case id: EntityId => Consequence.success(id)
        case s: String => parse(s)
        case other => ???
      }
    }
  }

  def parse(s: String): Consequence[EntityId] = ???
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
