package org.goldenport.cncf.entity

import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.directive.Query

/*
 * @since   Apr. 11, 2025
 *  version Feb. 25, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
case class ListDirective(
)

case class ListResult[T](
)

case class EntityQuery[T](collection: EntityCollectionId, query: Query[?])
