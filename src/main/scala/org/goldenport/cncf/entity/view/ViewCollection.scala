package org.goldenport.cncf.entity.view

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.Collection
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.security.SecuritySubject

/*
 * @since   Mar. 14, 2026
 *  version Mar. 24, 2026
 *  version Apr. 15, 2026
 * @version May.  4, 2026
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
  metricsRegistry: Option[EntityAccessMetricsRegistry] = None,
  cachePolicy: Option[ViewCachePolicy] = None
) extends Collection[V] {
  private[this] val _cachePolicy =
    cachePolicy.getOrElse(ViewCachePolicy(maxEntities, maxQueries, queryChunkSize, if (maxQueries <= 0) ViewQueryCacheScope.Disabled else ViewQueryCacheScope.Shared, metricsName)).normalized
  private[this] val _entityCache = mutable.LinkedHashMap.empty[EntityId, V]
  private[this] val _queryChunkCache = mutable.LinkedHashMap.empty[String, Vector[V]]

  def resolve(id: EntityId): Consequence[V] = synchronized {
    _entityCache.get(id) match {
      case Some(v) =>
        _touch_entity(id, v)
        _emit_metric("view.load.hit", Record.dataAuto(
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit"
        ))
        Consequence.success(v)
      case None =>
        builder.build(id).map { v =>
          _put_entity(id, v)
          _emit_metric("view.load.miss", Record.dataAuto(
            "entity" -> _cachePolicy.metricsName,
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
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit"
        ))
        Consequence.success(v)
      case None =>
        _build_with_context(id).map { v =>
          _put_entity(id, v)
          _emit_metric("view.load.miss", Record.dataAuto(
            "entity" -> _cachePolicy.metricsName,
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
    _query(q, None)(queryfn)
  }

  private def _query(
    q: Query[_],
    scopeKey: Option[String]
  )(queryfn: Query[_] => Consequence[Vector[V]]): Consequence[Vector[V]] =
    (q.offset, q.limit) match {
      case (Some(offset), Some(limit)) if limit >= 0 =>
        _query_chunked(q, offset, limit, scopeKey)(queryfn)
      case (Some(offset), None) if offset >= 0 =>
        _query_chunked(q, offset, _cachePolicy.queryChunkSize, scopeKey)(queryfn)
      case _ =>
        _query_small_result(q, scopeKey)(queryfn)
    }

  def query_with_context(
    q: Query[_]
  )(
    queryfn: Query[_] => ExecutionContext ?=> Consequence[Vector[V]]
  )(using ctx: ExecutionContext): Consequence[Vector[V]] =
    synchronized {
      _query(q, _context_query_scope_key)(qq => queryfn(qq))
    }

  def invalidate(id: EntityId): Unit = synchronized {
    _entityCache.remove(id)
    _queryChunkCache.clear()
    _emit_metric("view.invalidate", Record.dataAuto(
      "entity" -> _cachePolicy.metricsName,
      "source" -> "view-cache",
      "outcome" -> "id"
    ))
  }

  def invalidateAll(): Unit = synchronized {
    _entityCache.clear()
    _queryChunkCache.clear()
    _emit_metric("view.invalidate", Record.dataAuto(
      "entity" -> _cachePolicy.metricsName,
      "source" -> "view-cache",
      "outcome" -> "all"
    ))
  }

  private def _query_chunked(
    q: Query[_],
    offset: Int,
    limit: Int,
    scopeKey: Option[String]
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    val normalizedoffset = math.max(0, offset)
    val normalizedlimit = math.max(0, limit)
    if (normalizedlimit == 0)
      Consequence.success(Vector.empty)
    else {
      val basekey = _query_base_key(q, scopeKey)
      val firstchunkstart = (normalizedoffset / _cachePolicy.queryChunkSize) * _cachePolicy.queryChunkSize
      val lastindex = normalizedoffset + normalizedlimit - 1
      val lastchunkstart = (lastindex / _cachePolicy.queryChunkSize) * _cachePolicy.queryChunkSize
      val chunkstarts =
        (firstchunkstart to lastchunkstart by _cachePolicy.queryChunkSize).toVector
      chunkstarts.foldLeft(Consequence.success(Vector.empty[V])) { (z, start) =>
        z.flatMap { xs =>
          _resolve_query_chunk(basekey, q, start, scopeKey)(queryfn).map(xs ++ _)
        }
      }.map { xs =>
        val localoffset = normalizedoffset - firstchunkstart
        xs.slice(localoffset, localoffset + normalizedlimit)
      }
    }
  }

  private def _query_small_result(
    q: Query[_],
    scopeKey: Option[String]
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    if (!_query_cache_enabled(scopeKey))
      return queryfn(q).map { v =>
        _emit_metric("view.query.small.bypass", Record.dataAuto(
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> _cache_bypass_outcome(scopeKey),
          "scope" -> _cachePolicy.queryScope.name,
          "fetchedCount" -> v.size
        ))
        v
      }
    val key = _query_small_result_key(q, scopeKey)
    _queryChunkCache.get(key) match {
      case Some(v) =>
        _touch_query(key, v)
        _emit_metric("view.query.small.hit", Record.dataAuto(
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit",
          "scope" -> _cachePolicy.queryScope.name
        ))
        Consequence.success(v)
      case None =>
        queryfn(q).map { v =>
          if (v.size <= _cachePolicy.queryChunkSize)
            _put_query(key, v)
          _emit_metric(
            if (v.size <= _cachePolicy.queryChunkSize) "view.query.small.miss"
            else "view.query.small.bypass",
            Record.dataAuto(
              "entity" -> _cachePolicy.metricsName,
              "source" -> "view-cache",
              "outcome" -> (if (v.size <= _cachePolicy.queryChunkSize) "miss" else "bypass"),
              "scope" -> _cachePolicy.queryScope.name,
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
    start: Int,
    scopeKey: Option[String]
  )(
    queryfn: Query[_] => Consequence[Vector[V]]
  ): Consequence[Vector[V]] = {
    if (!_query_cache_enabled(scopeKey))
      return queryfn(_query_chunk_query(q, start, _cachePolicy.queryChunkSize)).map { v =>
        _emit_metric("view.query.chunk.bypass", Record.dataAuto(
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> _cache_bypass_outcome(scopeKey),
          "scope" -> _cachePolicy.queryScope.name,
          "offset" -> start,
          "limit" -> _cachePolicy.queryChunkSize,
          "fetchedCount" -> v.size
        ))
        v
      }
    val chunkkey = _query_chunk_key(basekey, start)
    _queryChunkCache.get(chunkkey) match {
      case Some(v) =>
        _touch_query(chunkkey, v)
        _emit_metric("view.query.chunk.hit", Record.dataAuto(
          "entity" -> _cachePolicy.metricsName,
          "source" -> "view-cache",
          "outcome" -> "hit",
          "scope" -> _cachePolicy.queryScope.name,
          "offset" -> start,
          "limit" -> _cachePolicy.queryChunkSize
        ))
        Consequence.success(v)
      case None =>
        queryfn(_query_chunk_query(q, start, _cachePolicy.queryChunkSize)).map { v =>
          _put_query(chunkkey, v)
          _emit_metric("view.query.chunk.miss", Record.dataAuto(
            "entity" -> _cachePolicy.metricsName,
            "source" -> "view-cache",
            "outcome" -> "miss",
            "scope" -> _cachePolicy.queryScope.name,
            "offset" -> start,
            "limit" -> _cachePolicy.queryChunkSize,
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

  private def _query_base_key(q: Query[_], scopeKey: Option[String]): String = {
    val base = q.query match {
      case p: Query.Plan[_] =>
        Query(Query.Plan(p.condition, p.where, p.sort, None, None)).toString
      case other =>
        Query.plan(other, Query.whereOf(q), Query.sortOf(q), None, None).toString
    }
    scopeKey.map(x => s"$x@@$base").getOrElse(base)
  }

  private def _query_chunk_key(basekey: String, start: Int): String =
    s"$basekey@@$start/${_cachePolicy.queryChunkSize}"

  private def _query_small_result_key(q: Query[_], scopeKey: Option[String]): String =
    s"${_query_base_key(q, scopeKey)}@@small"

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
    if (_cachePolicy.maxEntities <= 0)
      return
    if (_entityCache.size >= _cachePolicy.maxEntities)
      _entityCache.remove(_entityCache.head._1)
    _entityCache.update(id, v)
  }

  private def _touch_query(key: String, v: Vector[V]): Unit = {
    _queryChunkCache.remove(key)
    _queryChunkCache.update(key, v)
  }

  private def _put_query(key: String, v: Vector[V]): Unit = {
    if (_cachePolicy.maxQueries <= 0)
      return
    if (_queryChunkCache.size >= _cachePolicy.maxQueries)
      _queryChunkCache.remove(_queryChunkCache.head._1)
    _queryChunkCache.update(key, v)
  }

  private def _query_cache_enabled(scopeKey: Option[String]): Boolean =
    _cachePolicy.queryScope match {
      case ViewQueryCacheScope.Disabled => false
      case ViewQueryCacheScope.Principal => scopeKey.isDefined && _cachePolicy.maxQueries > 0
      case ViewQueryCacheScope.Shared => _cachePolicy.maxQueries > 0
    }

  private def _cache_bypass_outcome(scopeKey: Option[String]): String =
    _cachePolicy.queryScope match {
      case ViewQueryCacheScope.Disabled => "disabled"
      case ViewQueryCacheScope.Principal if scopeKey.isEmpty => "context-missing"
      case _ => "bypass"
    }

  private def _context_query_scope_key(using ctx: ExecutionContext): Option[String] =
    _cachePolicy.queryScope match {
      case ViewQueryCacheScope.Principal =>
        val subject = SecuritySubject.current
        val privileges = subject.privileges.toVector.sorted.mkString(",")
        Some(s"principal:${ctx.major}/${ctx.minor}/${subject.subjectId}/${privileges}")
      case _ =>
        None
    }
}
