package org.goldenport.cncf.entity.view

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.Collection

/*
 * @since   Mar. 15, 2026
 *  version Mar. 24, 2026
 *  version Apr.  4, 2026
 * @version Apr. 14, 2026
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
        Consequence.notImplemented("Browser.query is not supported")
    }

  def from[V](
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        collection.resolve(id)

      def query(q: Query[_]): Consequence[Vector[V]] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.query(q)(queryfn)
          case _ => queryfn(q)
        }
    }

  def from[V](
    loadfn: EntityId => Consequence[V],
    collection: Collection[V],
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Browser[V] =
    new Browser[V] {
      def find(id: EntityId): Consequence[V] =
        loadfn(id)

      def query(q: Query[_]): Consequence[Vector[V]] =
        collection match {
          case m: ViewCollection[V @unchecked] => m.query(q)(queryfn)
          case _ => queryfn(q)
        }
    }
}
