package org.goldenport.cncf.entity.runtime

import java.util.concurrent.atomic.LongAdder
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.datatype.EntityId

/*
 * @since   Mar. 14, 2026
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class MemoryRealm[E](
  idOf: E => EntityId,
  maxEntities: Int = 10000
) {
  private[this] val _entities_map =
    mutable.HashMap.empty[EntityId, E]

  private[this] val _access_order =
    mutable.LinkedHashSet.empty[EntityId]

  private[this] val _entity_eviction_count = new LongAdder()
  private[this] val _hit_count = new LongAdder()
  private[this] val _miss_count = new LongAdder()

  def get(id: EntityId): Option[E] = synchronized {
    _entities_map.get(id) match {
      case Some(e) =>
        _hit_count.increment()
        _touch(id)
        Some(e)
      case None =>
        _miss_count.increment()
        None
    }
  }

  def put(entity: E): Unit = synchronized {
    val id = idOf(entity)

    if (_entities_map.contains(id)) {
      _entities_map.update(id, entity)
      _touch(id)
    } else {
      if (_entities_map.size >= maxEntities)
        _evict_one()

      _entities_map.put(id, entity)
      _touch(id)
    }
  }

  def resolve(id: EntityId): Consequence[E] =
    get(id) match {
      case Some(e) => Consequence.success(e)
      case None => Consequence.failure(s"entity not found in memory realm: $id")
    }

  def remove(id: EntityId): Boolean = synchronized {
    val removed = _entities_map.remove(id).isDefined
    if (removed)
      _access_order.remove(id)
    removed
  }

  def values: Vector[E] = synchronized {
    _entities_map.values.toVector
  }

  def cachedEntityCount: Int = synchronized {
    _entities_map.size
  }

  def entityEvictionCount: Long =
    _entity_eviction_count.sum()

  def hitCount: Long =
    _hit_count.sum()

  def missCount: Long =
    _miss_count.sum()

  private def _touch(id: EntityId): Unit = {
    _access_order.remove(id)
    _access_order.add(id)
  }

  private def _evict_one(): Unit = {
    _access_order.headOption.foreach { id =>
      _access_order.remove(id)
      _entities_map.remove(id)
      _entity_eviction_count.increment()
    }
  }
}
