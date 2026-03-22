package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 15, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait Browser[V] {
  def find(id: EntityId): Consequence[V]
  def query(q: Query[_]): Consequence[Vector[V]]
}

object Browser {
  def from[V](collection: Collection[V]): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        collection.resolve(id)

      def query(q: Query[_]): Consequence[Vector[V]] =
        Consequence.failure("Browser.query is not supported")
    }

  def from[V](
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        collection.resolve(id)

      def query(q: Query[_]): Consequence[Vector[V]] =
        queryfn(q)
    }
}
