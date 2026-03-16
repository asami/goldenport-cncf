package org.goldenport.cncf.entity.runtime

import org.goldenport.Consequence
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.directive.{Query, SearchResult}

/*
 * @since   Mar. 14, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait Collection[A] {
  def resolve(id: EntityId): Consequence[A]
}

final class EntityCollection[E](
  val descriptor: EntityDescriptor[E],
  val storage: EntityStorage[E]
) extends Collection[E] {
  // Load-through resolution:
  // 1. Try MemoryRealm (working set cache)
  // 2. Fallback to StoreRealm
  // 3. If loaded from StoreRealm and MemoryRealm exists, cache it
  def resolve(id: EntityId): Consequence[E] = {
    val memory = storage.memoryRealm
    memory.flatMap(_.get(id)) match {
      case Some(entity) =>
        Consequence.success(entity)
      case None =>
        val r = storage.storeRealm.resolve(id)
        r match {
          case Consequence.Success(e) =>
            memory.foreach(_.put(e))
          case _ =>
            ()
        }
        r
    }
  }

  // Current phase search API:
  // route is available through EntitySpace/EntityCollection.
  // Query matching uses directive.Query condition evaluation.
  def search(query: EntityQuery[?]): Consequence[SearchResult[E]] = {
    val source =
      storage.memoryRealm match {
        case Some(memory) => memory.values
        case None => storage.storeRealm.values
      }
    val filtered = source.filter(v => Query.matches(query.query, v))
    val sorted = Query.sortValues(filtered, query.query.sort)
    val values = Query.sliceValues(sorted, query.query.offset, query.query.limit)
    Consequence.success(
      SearchResult(
        query = query.query,
        data = values,
        totalCount = Some(filtered.size),
        offset = query.query.offset,
        limit = query.query.limit,
        fetchedCount = values.size
      )
    )
  }
}
