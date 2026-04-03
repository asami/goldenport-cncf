package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.runtime.Collection
import org.goldenport.cncf.directive.Query

/*
 * @since   Mar. 14, 2026
 *  version Mar. 24, 2026
 * @version Apr.  4, 2026
 * @author  ASAMI, Tomoharu
 */
trait ViewBuilder[V] {
  def build(id: EntityId): Consequence[V]
}

final class ViewCollection[V](
  builder: ViewBuilder[V],
  maxEntities: Int = 10000,
  maxQueries: Int = 512
) extends Collection[V] {
  private[this] val _entityCache = mutable.LinkedHashMap.empty[EntityId, V]
  private[this] val _queryCache = mutable.LinkedHashMap.empty[String, Vector[V]]

  def resolve(id: EntityId): Consequence[V] = synchronized {
    _entityCache.get(id) match {
      case Some(v) =>
        _touch_entity(id, v)
        Consequence.success(v)
      case None =>
        builder.build(id).map { v =>
          _put_entity(id, v)
          v
        }
    }
  }

  def query(
    q: Query[_]
  )(queryfn: Query[_] => Consequence[Vector[V]]): Consequence[Vector[V]] = synchronized {
    val key = _query_key(q)
    _queryCache.get(key) match {
      case Some(v) =>
        _touch_query(key, v)
        Consequence.success(v)
      case None =>
        queryfn(q).map { v =>
          _put_query(key, v)
          v
        }
    }
  }

  def invalidate(id: EntityId): Unit = synchronized {
    _entityCache.remove(id)
    _queryCache.clear()
  }

  def invalidateAll(): Unit = synchronized {
    _entityCache.clear()
    _queryCache.clear()
  }

  private def _query_key(q: Query[_]): String =
    q.toString

  private def _touch_entity(id: EntityId, v: V): Unit = {
    _entityCache.remove(id)
    _entityCache.update(id, v)
  }

  private def _put_entity(id: EntityId, v: V): Unit = {
    if (_entityCache.size >= maxEntities)
      _entityCache.remove(_entityCache.head._1)
    _entityCache.update(id, v)
  }

  private def _touch_query(key: String, v: Vector[V]): Unit = {
    _queryCache.remove(key)
    _queryCache.update(key, v)
  }

  private def _put_query(key: String, v: Vector[V]): Unit = {
    if (_queryCache.size >= maxQueries)
      _queryCache.remove(_queryCache.head._1)
    _queryCache.update(key, v)
  }
}
