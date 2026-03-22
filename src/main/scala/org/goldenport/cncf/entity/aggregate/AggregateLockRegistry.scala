package org.goldenport.cncf.entity.aggregate

import scala.collection.concurrent.TrieMap
import org.goldenport.Consequence
import org.goldenport.model.datatype.EntityId

/*
 * AggregateLockRegistry
 *
 * Serializes execution per aggregate root.
 *
 * NOTE:
 * Currently the lock key is EntityId because the runtime does not yet
 * distinguish AggregateId. In the future this should be changed to use
 * AggregateId (aggregate root identifier) so that the lock boundary
 * exactly matches the aggregate consistency boundary.
 *
 * @since   Mar. 14, 2026
 * @version Mar. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateLockRegistry {
  // Lock objects per aggregate root.
  // NOTE: locks may accumulate if the number of distinct EntityId values
  // grows very large. If this becomes an issue, introduce lock eviction
  // or weak references.
  private[this] val _locks: TrieMap[EntityId, AnyRef] = TrieMap.empty

  // Lock key abstraction. Currently identical to EntityId but isolated
  // so the implementation can later switch to AggregateId without
  // touching the rest of the class.
  private def _lockKey(id: EntityId): EntityId = id

  private def _lock(id: EntityId): AnyRef =
    _locks.getOrElseUpdate(_lockKey(id), new AnyRef)

  /**
   * Execute the given body while holding the lock for the specified EntityId.
   * This is a generic version used internally and can support other return types.
   */
  def withLock[A](id: EntityId)(body: => A): A = {
    val lock = _lock(id)
    lock.synchronized {
      body
    }
  }

  /**
   * Execute the given body while holding the lock for the specified EntityId.
   *
   * Same EntityId → serialized execution
   * Different EntityId → concurrent execution
   */
  def execute[A](id: EntityId)(body: => Consequence[A]): Consequence[A] =
    withLock(id) {
      body
    }
}
