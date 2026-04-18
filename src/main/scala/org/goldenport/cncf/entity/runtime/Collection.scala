package org.goldenport.cncf.entity.runtime

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp

/*
 * @since   Mar. 14, 2026
 *  version Mar. 30, 2026
 * @version Apr. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait Collection[A] {
  def resolve(id: EntityId): Consequence[A]
}

final class EntityCollection[E](
  val descriptor: EntityDescriptor[E],
  val storage: EntityStorage[E]
) extends Collection[E] {
  def put(entity: E): Unit = {
    storage.storeRealm.put(entity)
    storage.memoryRealm.foreach(_.put(entity))
  }

  def putRecord(record: Record): Consequence[Unit] =
    descriptor.persistent.fromRecord(record).map { entity =>
      put(entity)
    }

  def putRecordSynced(
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      entity <- descriptor.persistent.fromRecord(record)
      _ <- ctx.entityStoreSpace.save(
        UnitOfWorkOp.EntityStoreSave(entity, descriptor.persistent)
      ).recoverWith { case _ =>
        Consequence.unit
      }
      _ = put(entity)
    } yield ()

  // Load-through resolution:
  // 1. Try MemoryRealm (working set cache)
  // 2. Fallback to StoreRealm
  // 3. If loaded from StoreRealm and MemoryRealm exists, cache it
  def resolve(id: EntityId): Consequence[E] = {
    val memory = storage.memoryRealm
    memory.flatMap(_.get(id)) match {
      case Some(entity) if !_is_logically_deleted(entity) =>
        Consequence.success(entity)
      case Some(_) =>
        memory.foreach(_.remove(id))
        storage.storeRealm.resolve(id).flatMap { entity =>
          if (_is_logically_deleted(entity))
            Consequence.successOrEntityNotFound(Option.empty[E])(id)
          else {
            memory.foreach(_.put(entity))
            Consequence.success(entity)
          }
        }
      case None =>
        storage.storeRealm.resolve(id).flatMap { entity =>
          if (_is_logically_deleted(entity))
            Consequence.successOrEntityNotFound(Option.empty[E])(id)
          else {
            memory.foreach(_.put(entity))
            Consequence.success(entity)
          }
        }
    }
  }

  def evict(id: EntityId): Unit = {
    storage.storeRealm.remove(id)
    storage.memoryRealm.foreach(_.remove(id))
  }

  // Current phase search API:
  // route is available through EntitySpace/EntityCollection.
  // Query matching uses directive.Query condition evaluation.
  def search(
    query: EntityQuery[?]
  )(using ctx: ExecutionContext): Consequence[SearchResult[E]] = {
    val visibilitypolicy = _visibility_policy(query.query)
    val source =
      storage.memoryRealm match {
        case Some(memory) => memory.values
        case None => storage.storeRealm.values
      }
    val notdeleted = source.filterNot(_is_logically_deleted)
    val visible = notdeleted.filter(v => _is_visible(descriptor.persistent.toRecord(v), visibilitypolicy))
    val filtered = visible.filter(v => Query.matches(query.query, v))
    val sorted = Query.sortValues(filtered, query.query.sort)
    val values = Query.sliceValues(sorted, query.query.offset, query.query.limit)
    Consequence.success(
      SearchResult(
        query = query.query,
        data = values,
        totalCount = if (query.query.includeTotal) Some(filtered.size) else None,
        offset = query.query.offset,
        limit = query.query.limit,
        fetchedCount = values.size
      )
    )
  }

  private final case class _VisibilityPolicy(
    postStatuses: Option[Set[String]],
    alivenesses: Option[Set[String]]
  )

  private final case class _LifecycleConstraint(
    postStatusExplicit: Boolean,
    alivenessExplicit: Boolean
  )

  private def _visibility_policy(
    query: Query[?]
  )(using ctx: ExecutionContext): _VisibilityPolicy = {
    val lifecycle = _lifecycle_constraint(query)
    val ismanager = _is_content_manager()
    val poststatuses = if (lifecycle.postStatusExplicit) {
      None
    } else if (ismanager) {
      val p = _post_statuses_for_manager()
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("published"))
    }
    val alivenesses = if (lifecycle.alivenessExplicit) {
      None
    } else if (ismanager) {
      val p = _alivenesses_for_manager(poststatuses.getOrElse(Set.empty))
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("alive"))
    }
    _VisibilityPolicy(
      postStatuses = poststatuses,
      alivenesses = alivenesses
    )
  }

  private def _lifecycle_constraint(
    query: Query[?]
  ): _LifecycleConstraint = {
    val expr = Query.whereOf(query)
    val raw = _query_condition(query)
    _LifecycleConstraint(
      postStatusExplicit = _mentions_path(expr, Set("poststatus")) || _mentions_condition_key(raw, Set("poststatus")),
      alivenessExplicit = _mentions_path(expr, Set("aliveness")) || _mentions_condition_key(raw, Set("aliveness"))
    )
  }

  private def _query_condition(
    query: Query[?]
  ): Any =
    query.query match {
      case p: Query.Plan[?] => p.condition
      case other => other
    }

  private def _mentions_path(
    expr: Query.Expr,
    names: Set[String]
  ): Boolean =
    expr match {
      case Query.True => false
      case Query.False => false
      case Query.And(items) => items.exists(_mentions_path(_, names))
      case Query.Or(items) => items.exists(_mentions_path(_, names))
      case Query.Not(item) => _mentions_path(item, names)
      case Query.FieldCondition(path, _) => names.contains(_normalize_path(path))
      case Query.Eq(path, _) => names.contains(_normalize_path(path))
      case Query.Ne(path, _) => names.contains(_normalize_path(path))
      case Query.Gt(path, _) => names.contains(_normalize_path(path))
      case Query.Gte(path, _) => names.contains(_normalize_path(path))
      case Query.Lt(path, _) => names.contains(_normalize_path(path))
      case Query.Lte(path, _) => names.contains(_normalize_path(path))
      case Query.In(path, _) => names.contains(_normalize_path(path))
      case Query.NotIn(path, _) => names.contains(_normalize_path(path))
      case Query.IsNull(path) => names.contains(_normalize_path(path))
      case Query.IsNotNull(path) => names.contains(_normalize_path(path))
      case Query.Like(path, _, _) => names.contains(_normalize_path(path))
      case Query.StartsWith(path, _, _) => names.contains(_normalize_path(path))
      case Query.EndsWith(path, _, _) => names.contains(_normalize_path(path))
      case Query.Contains(path, _, _) => names.contains(_normalize_path(path))
    }

  private def _mentions_condition_key(
    condition: Any,
    names: Set[String]
  ): Boolean =
    condition match {
      case r: Record =>
        r.asMap.keys.exists(k => names.contains(_normalize_path(k)))
      case m: Map[?, ?] =>
        m.keysIterator.collect { case k: String => k }.exists(k => names.contains(_normalize_path(k)))
      case p: Product =>
        p.productElementNames.exists(k => names.contains(_normalize_path(k)))
      case _ =>
        false
    }

  private def _normalize_path(path: String): String = {
    val segment = path.split("\\.").lastOption.getOrElse(path)
    org.goldenport.cncf.context.RuntimeContext.PropertyNameStyle.CamelCase.transform(segment)
  }

  private def _is_visible(
    record: Record,
    policy: _VisibilityPolicy
  ): Boolean = {
    val postok = policy.postStatuses match {
      case Some(allowed) =>
        _record_value(record, Vector("postStatus"))
          .flatMap(_post_status_token)
          .forall(allowed.contains)
      case None =>
        true
    }
    val aliveok = policy.alivenesses match {
      case Some(allowed) =>
        _record_value(record, Vector("aliveness"))
          .flatMap(_aliveness_token)
          .forall(allowed.contains)
      case None =>
        true
    }
    postok && aliveok
  }

  private def _record_value(
    record: Record,
    keys: Vector[String]
  ): Option[Any] = {
    val m = record.asMap
    keys
      .flatMap(org.goldenport.cncf.context.RuntimeContext.Context.default.propertyName.aliases)
      .distinct
      .collectFirst(Function.unlift(m.get))
  }

  private def _is_content_manager()(using ctx: ExecutionContext): Boolean = {
    if (ctx.isAggregateInternalRead)
      return true
    val aliases = Set(
      "contentmanager",
      "contentadmin",
      "contentadministrator",
      "contentowner"
    )
    val roles = _attribute_tokens("role", "roles", "authority", "authorities")
    val capabilities = ctx.security.capabilities.map(_.name).flatMap(_split_tokens)
    val level = _split_tokens(ctx.security.level.value)
    (roles ++ capabilities ++ level).exists(x => aliases.contains(_normalize_alias(x)))
  }

  private def _post_statuses_for_manager()(using ctx: ExecutionContext): Set[String] = {
    val configured = _attribute_tokens("search_poststatus", "search.poststatus", "poststatus", "post_status")
      .flatMap(_post_status_token)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose = _attribute_tokens("purpose").flatMap(_post_status_token)
      if (frompurpose.nonEmpty)
        frompurpose
      else
        Set("published", "draft")
    }
  }

  private def _alivenesses_for_manager(
    poststatuses: Set[String]
  )(using ctx: ExecutionContext): Set[String] = {
    val configured = _attribute_tokens("search_aliveness", "search.aliveness", "aliveness")
      .flatMap(_aliveness_token)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose = _attribute_tokens("purpose").flatMap(_aliveness_token)
      if (frompurpose.nonEmpty)
        frompurpose
      else if (poststatuses.contains("archived"))
        Set("alive", "dead")
      else
        Set("alive")
    }
  }

  private def _attribute_tokens(
    keys: String*
  )(using ctx: ExecutionContext): Set[String] = {
    val attrs = ctx.security.principal.attributes.map { case (k, v) =>
      k.toLowerCase -> v
    }
    keys.toVector
      .flatMap(k => attrs.get(k.toLowerCase))
      .flatMap(_split_tokens)
      .toSet
  }

  private def _split_tokens(p: String): Vector[String] =
    p.split("[,\\s]+").toVector.map(_.trim).filter(_.nonEmpty)

  private def _normalize_alias(p: String): String =
    p.toLowerCase.replace("_", "").replace("-", "")

  private def _post_status_token(p: Any): Option[String] = {
    val s = p.toString.toLowerCase
    if (s.contains("published"))
      Some("published")
    else if (s.contains("draft"))
      Some("draft")
    else if (s.contains("archived"))
      Some("archived")
    else
      None
  }

  private def _aliveness_token(p: Any): Option[String] = {
    val s = p.toString.toLowerCase
    if (s.contains("alive"))
      Some("alive")
    else if (s.contains("suspended"))
      Some("suspended")
    else if (s.contains("dead"))
      Some("dead")
    else
      None
  }

  private def _is_logically_deleted(
    entity: E
  ): Boolean = {
    val record = descriptor.persistent.toRecord(entity)
    val keyset = record.keySet
    val hasdeletedat = org.goldenport.cncf.context.RuntimeContext.Context.default.propertyName.aliases("deletedAt").exists(keyset.contains)
    val poststatus = _record_value(record, Vector("postStatus")).flatMap(_post_status_token)
    val aliveness = _record_value(record, Vector("aliveness")).flatMap(_aliveness_token)
    hasdeletedat || poststatus.contains("archived") || aliveness.contains("dead")
  }
}
