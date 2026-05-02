package org.goldenport.cncf.entity

import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.directive.Query

/*
 * @since   Apr. 11, 2025
 *  version Feb. 25, 2026
 *  version Mar. 24, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
case class ListDirective(
)

case class ListResult[T](
)

enum EntitySearchScope {
  case WorkingSet, Store
}

enum EntityVisibilityScope {
  case Public, Owner, Admin
}

object EntityVisibilityScope {
  def parseOption(text: String): Option[EntityVisibilityScope] =
    Option(text).map(_.trim.toLowerCase(java.util.Locale.ROOT).replace("_", "-")).flatMap {
      case "public" | "public-reader" | "reader" => Some(EntityVisibilityScope.Public)
      case "owner" | "my" | "author" => Some(EntityVisibilityScope.Owner)
      case "admin" | "administrator" | "manager" => Some(EntityVisibilityScope.Admin)
      case "" | "default" | "normal" => None
      case _ => None
    }
}

case class EntityQuery[T](
  collection: EntityCollectionId,
  query: Query[?],
  scope: EntitySearchScope = EntitySearchScope.WorkingSet,
  visibilityScope: Option[EntityVisibilityScope] = None
)
