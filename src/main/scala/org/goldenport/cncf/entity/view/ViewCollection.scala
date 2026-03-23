package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 14, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait ViewBuilder[V] {
  def build(id: EntityId): Consequence[V]
}

final class ViewCollection[V](
  builder: ViewBuilder[V]
) extends Collection[V] {
  def resolve(id: EntityId): Consequence[V] =
    builder.build(id)
}
