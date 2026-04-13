package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 15, 2026
 *  version Mar. 24, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
trait Repository[A] {
  def find(id: EntityId): Consequence[A]
  def query(q: Query[?]): Consequence[Vector[A]]
}

object Repository {
  def from[E](collection: AggregateCollection[E]): Repository[E] =
    new Repository[E] {
      def find(id: EntityId): Consequence[E] =
        collection.resolve(id)

      def query(q: Query[?]): Consequence[Vector[E]] =
        collection.query(q)
    }

  def from[E](collection: Collection[E]): Repository[E] =
    new Repository[E] {
      def find(id: EntityId): Consequence[E] =
        collection.resolve(id)

      def query(q: Query[?]): Consequence[Vector[E]] =
        Consequence.notImplemented("Repository.query is not supported")
    }
}
