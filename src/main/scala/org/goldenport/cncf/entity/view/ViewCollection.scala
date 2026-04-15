package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.Collection
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry

/*
 * @since   Mar. 14, 2026
 *  version Mar. 24, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait ViewBuilder[V] {
  def build(id: EntityId): Consequence[V]
}

trait ContextualViewBuilder[V] extends ViewBuilder[V] {
  def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V]

  override def build(id: EntityId): Consequence[V] =
    Consequence.operationInvalid("ExecutionContext is required for contextual view builder")
}

final class ViewCollection[V](
  builder: ViewBuilder[V],
  maxEntities: Int = 10000,
  maxQueries: Int = 512,
  queryChunkSize: Int = 1000,
  metricsName: String = "view",
  metricsRegistry: Option[EntityAccessMetricsRegistry] = None
) extends Collection[V] {
  private[this] val _entityCache = mutable.LinkedHashMap.empty[EntityId, V]
  private[this] val _queryChunkCache = mutable.LinkedHashMap.empty[String, Vector[V]]

  def resolve(id: EntityId): Consequence[V] = synchronized {
    _entityCache.get(id) match {
      case Some(v) =>
        _touch_entity(id, v)
        _emit_metric("view.load.hit", Record.dataAuto(
          "entity" -> metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit"
        ))
        Consequence.success(v)
      case None =>
        builder.build(id).map { v =>
          _put_entity(id, v)
          _emit_metric("view.load.miss", Record.dataAuto(
            "entity" -> metricsName,
            "source" -> "view-cache",
            "outcome" -> "miss"
          ))
          v
        }
    }
  }

  def resolve_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] = synchronized {
    _entityCache.get(id) match {
      case Some(v) =>
        _touch_entity(id, v)
        _emit_metric("view.load.hit", Record.dataAuto(
          "entity" -> metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit"
        ))
        Consequence.success(v)
      case None =>
        _build_with_context(id).map { v =>
          _put_entity(id, v)
          _emit_metric("view.load.miss", Record.dataAuto(
            "entity" -> metricsName,
            "source" -> "view-cache",
            "outcome" -> "miss"
          ))
          v
        }
    }
  }

  private def _build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[V] =
    builder match {
      case m: ContextualViewBuilder[V @unchecked] => m.build_with_context(id)
      case _ => builder.build(id)
    }

  def query(
    q: Query[_]
  )(queryfn: Query[_] => Consequence[Vector[V]]): Consequence[Vector[V]] = synchronized {
    (q.offset, q.limit) match {
      case (Some(offset), Some(limit)) if limit >= 0 =>
        _query_chunked(q, offset, limit)(queryfn)
      case (Some(offset), None) if offset >= 0 =>
        _query_chunked(q, offset, queryChunkSize)(queryfn)
      case _ =>
        _query_small_result(q)(queryfn)
    }
  }

  def query_with_context(
    q: Query[_]
  )(
    queryfn: Query[_] => ExecutionContext ?=> Consequence[Vector[V]]
  )(using ctx: ExecutionContext): Consequence[Vector[V]] =
    query(q)(qq => queryfn(qq))

  def invalidate(id: EntityId): Unit = synchronized {
    _entityCache.remove(id)
    _queryChunkCache.clear()
    _emit_metric("view.invalidate", Record.dataAuto(
      "entity" -> metricsName,
      "source" -> "view-cache",
      "outcome" -> "id"
    ))
  }

  def invalidateAll(): Unit = synchronized {
    _entityCache.clear()
    _queryChunkCache.clear()
    _emit_metric("view.invalidate", Record.dataAuto(
      "entity" -> metricsName,
      "source" -> "view-cache",
      "outcome" -> "all"
    ))
  }

  private def _query_chunked(
    q: Query[_],
    offset: Int,
    limit: Int
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    val normalizedoffset = math.max(0, offset)
    val normalizedlimit = math.max(0, limit)
    if (normalizedlimit == 0)
      Consequence.success(Vector.empty)
    else {
      val basekey = _query_base_key(q)
      val firstchunkstart = (normalizedoffset / queryChunkSize) * queryChunkSize
      val lastindex = normalizedoffset + normalizedlimit - 1
      val lastchunkstart = (lastindex / queryChunkSize) * queryChunkSize
      val chunkstarts =
        (firstchunkstart to lastchunkstart by queryChunkSize).toVector
      chunkstarts.foldLeft(Consequence.success(Vector.empty[V])) { (z, start) =>
        z.flatMap { xs =>
          _resolve_query_chunk(basekey, q, start)(queryfn).map(xs ++ _)
        }
      }.map { xs =>
        val localoffset = normalizedoffset - firstchunkstart
        xs.slice(localoffset, localoffset + normalizedlimit)
      }
    }
  }

  private def _query_small_result(
    q: Query[_]
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    val key = _query_small_result_key(q)
    _queryChunkCache.get(key) match {
      case Some(v) =>
        _touch_query(key, v)
        _emit_metric("view.query.small.hit", Record.dataAuto(
          "entity" -> metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit"
        ))
        Consequence.success(v)
      case None =>
        queryfn(q).map { v =>
          if (v.size <= queryChunkSize)
            _put_query(key, v)
          _emit_metric(
            if (v.size <= queryChunkSize) "view.query.small.miss"
            else "view.query.small.bypass",
            Record.dataAuto(
              "entity" -> metricsName,
              "source" -> "view-cache",
              "outcome" -> (if (v.size <= queryChunkSize) "miss" else "bypass"),
              "fetchedCount" -> v.size
            )
          )
          v
        }
    }
  }

  private def _resolve_query_chunk(
    basekey: String,
    q: Query[_],
    start: Int
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    val chunkkey = _query_chunk_key(basekey, start)
    _queryChunkCache.get(chunkkey) match {
      case Some(v) =>
        _touch_query(chunkkey, v)
        _emit_metric("view.query.chunk.hit", Record.dataAuto(
          "entity" -> metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit",
          "offset" -> start,
          "limit" -> queryChunkSize
        ))
        Consequence.success(v)
      case None =>
        queryfn(_query_chunk_query(q, start, queryChunkSize)).map { v =>
          _put_query(chunkkey, v)
          _emit_metric("view.query.chunk.miss", Record.dataAuto(
            "entity" -> metricsName,
            "source" -> "view-cache",
            "outcome" -> "miss",
            "offset" -> start,
            "limit" -> queryChunkSize,
            "fetchedCount" -> v.size
          ))
          v
        }
    }
  }

  private def _emit_metric(
    name: String,
    attributes: Record
  ): Unit =
    metricsRegistry.getOrElse(EntityAccessMetricsRegistry.shared).record(name, attributes)

  private def _query_base_key(q: Query[_]): String =
    q.query match {
      case p: Query.Plan[_] =>
        Query(Query.Plan(p.condition, p.where, p.sort, None, None)).toString
      case other =>
        Query.plan(other, Query.whereOf(q), Query.sortOf(q), None, None).toString
    }

  private def _query_chunk_key(basekey: String, start: Int): String =
    s"$basekey@@$start/$queryChunkSize"

  private def _query_small_result_key(q: Query[_]): String =
    s"${_query_base_key(q)}@@small"

  private def _query_chunk_query(
    q: Query[_],
    offset: Int,
    limit: Int
  ): Query[_] =
    q.query match {
      case p: Query.Plan[_] =>
        Query(Query.Plan(p.condition, p.where, p.sort, Some(limit), Some(offset)))
      case other =>
        Query.plan(other, Query.whereOf(q), Query.sortOf(q), Some(limit), Some(offset))
    }

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
    _queryChunkCache.remove(key)
    _queryChunkCache.update(key, v)
  }

  private def _put_query(key: String, v: Vector[V]): Unit = {
    if (_queryChunkCache.size >= maxQueries)
      _queryChunkCache.remove(_queryChunkCache.head._1)
    _queryChunkCache.update(key, v)
  }
}
