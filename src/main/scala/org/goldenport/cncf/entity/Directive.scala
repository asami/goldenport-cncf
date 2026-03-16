package org.goldenport.cncf.entity

import org.goldenport.cncf.datatype.EntityCollectionId
import org.goldenport.cncf.directive.Query

/*
 * @since   Apr. 11, 2025
 * @version Feb. 25, 2026
 * @author  ASAMI, Tomoharu
 */
case class ListDirective(
)

case class ListResult[T](
)

case class EntityQuery[T](collection: EntityCollectionId, query: Query[?])
