package org.goldenport.cncf.entity.runtime

import java.time.Instant
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.datatype.EntityId

/*
 * @since   Mar. 14, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class PartitionedMemoryRealm[E](
  strategy: PartitionStrategy,
  idOf: E => EntityId,
  maxPartitions: Int = 64,
  maxEntitiesPerPartition: Int = 10000
) {
  // Partition storage for working-set realms.
  // NOTE: partitions are created lazily. If the number of distinct
  // partition keys grows very large, this map may grow without bound.
  // Future improvement may introduce partition eviction or a bounded
  // partition policy.
  private[this] val _partitions =
    TrieMap.empty[String, MemoryRealm[E]]

  // Track partition usage order for eviction
  private[this] val _order = mutable.LinkedHashSet.empty[String]
  private[this] val _lock = new AnyRef

  private def _partitionKey(id: EntityId): String =
    strategy.partitionKey(id)

  // Obtain partition realm, creating it lazily if necessary.
  private def _partition(key: String): MemoryRealm[E] = {
    _lock.synchronized {
      _partitions.get(key) match {
        case Some(realm) =>
          _touch(key)
          realm

        case None =>
          if (_partitions.size >= maxPartitions)
            _evictOne()

          val realm = new MemoryRealm[E](idOf, maxEntities = maxEntitiesPerPartition)
          _partitions.put(key, realm)
          _touch(key)
          realm
      }
    }
  }

  private def _touch(key: String): Unit =
    _order.remove(key)
    _order.add(key)

  private def _evictOne(): Unit =
    _order.headOption.foreach { k =>
      _order.remove(k)
      _partitions.remove(k)
    }

  def get(id: EntityId): Option[E] = {
    val key = _partitionKey(id)
    val realmopt = _lock.synchronized {
      _partitions.get(key).map { realm =>
        _touch(key)
        realm
      }
    }
    realmopt.flatMap(_.get(id))
  }

  def put(entity: E): Unit = {
    val id = idOf(entity)
    val key = _partitionKey(id)
    _lock.synchronized {
      val realm = _partition(key)
      realm.put(entity)
      _touch(key)
    }
  }

  def resolve(id: EntityId): Consequence[E] =
    get(id) match {
      case Some(e) =>
        Consequence.success(e)
      case None =>
        Consequence.failure(s"entity not found in memory realm: $id")
    }

  def valuesInRange(
    major: String,
    minor: String,
    from: Instant,
    to: Instant
  ): Vector[E] = {
    if (from.isAfter(to))
      Vector.empty
    else {
      val keys = strategy.partitionsForRange(major, minor, from, to)

      // Snapshot partitions to avoid concurrent map iteration issues
      val realms: Iterable[MemoryRealm[E]] =
        _lock.synchronized {
          if (keys.isEmpty)
            _partitions.values.toVector
          else
            keys.distinct.flatMap(k => _partitions.get(k))
        }

      realms.iterator.flatMap(_.values).toVector
    }
  }
  // --- metrics -------------------------------------------------------------

  /** Current number of active partitions in memory. */
  def partitionCount: Int =
    _lock.synchronized {
      _partitions.size
    }

  /** Current partition keys (snapshot). */
  def partitionKeys: Vector[String] =
    _lock.synchronized {
      _partitions.keys.toVector
    }

  /** Approximate total number of entities currently cached in all partitions. */
  def cachedEntityCount: Int =
    _lock.synchronized {
      _partitions.valuesIterator.map(_.cachedEntityCount).sum
    }
}
