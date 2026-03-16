package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 15, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait Repository[A] {
  def find(id: EntityId): Consequence[A]
}

object Repository {
  def from[E](collection: Collection[E]): Repository[E] =
    new Repository[E] {
      def find(id: EntityId): Consequence[E] =
        collection.resolve(id)
    }
}
