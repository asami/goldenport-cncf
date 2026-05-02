package org.goldenport.cncf.entity.runtime

import org.goldenport.Consequence
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityAccessScopePolicy, EntityIdentityScope, EntityLifecycleRecordPolicy, EntityQuery, EntitySearchScope, EntityVisibilityScope, SimpleEntityStorageShapePolicy}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp

/*
 * @since   Mar. 14, 2026
 *  version Mar. 30, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
trait Collection[A] {
  def resolve(id: EntityId): Consequence[A]

  def resolveScoped(id: EntityId)(using ExecutionContext): Consequence[A] =
    resolve(id)
}

final class EntityCollection[E](
  val descriptor: EntityDescriptor[E],
  val storage: EntityStorage[E]
) extends Collection[E] {
  def workingSetStatus: WorkingSetStatus =
    storage.workingSetStatus.get

  def residentCount: Int =
    storage.memoryRealm.map(_.values.size).getOrElse(0)

  def workingSetSearchAvailable: Boolean =
    _has_effective_working_set_policy && workingSetStatus.isReady

  def hasEffectiveWorkingSetPolicy: Boolean =
    _has_effective_working_set_policy

  def shouldFallbackToStoreForWorkingSet(
    query: EntityQuery[?]
  ): Boolean =
    query.scope == EntitySearchScope.WorkingSet && _has_effective_working_set_policy && !workingSetStatus.isReady

  def put(entity: E): Unit = {
    val id = descriptor.persistent.id(entity)
    val resident = _is_resident(entity)
    storage.storeRealm.put(entity)
    storage.memoryRealm.foreach { memory =>
      if (resident)
        memory.put(entity)
      else
        memory.remove(id)
    }
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
      )
      _ = put(entity)
    } yield ()

  // Load-through resolution:
  // 1. Try MemoryRealm (working set cache)
  // 2. Fallback to StoreRealm
  // 3. If loaded from StoreRealm and MemoryRealm exists, cache it
  def resolve(id: EntityId): Consequence[E] =
    _resolve(id, entity => !_is_logically_deleted(entity))

  override def resolveScoped(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[E] =
    _resolve(id, _is_normal_access_visible)

  private def _resolve(
    id: EntityId,
    visible: E => Boolean
  ): Consequence[E] = {
    val memory = storage.memoryRealm
    memory.flatMap(_.get(id)) match {
      case Some(entity) if visible(entity) =>
        Consequence.success(entity)
      case Some(entity) if _is_logically_deleted(entity) =>
        memory.foreach(_.remove(id))
        storage.storeRealm.resolve(id).flatMap { entity =>
          if (!visible(entity))
            Consequence.successOrEntityNotFound(Option.empty[E])(id)
          else {
            _cache_if_resident(entity)
            Consequence.success(entity)
          }
        }
      case Some(_) =>
        Consequence.successOrEntityNotFound(Option.empty[E])(id)
      case None =>
        storage.storeRealm.resolve(id).flatMap { entity =>
          if (!visible(entity))
            Consequence.successOrEntityNotFound(Option.empty[E])(id)
          else {
            _cache_if_resident(entity)
            Consequence.success(entity)
          }
        }
    }
  }

  def evict(id: EntityId): Unit = {
    storage.storeRealm.remove(id)
    storage.memoryRealm.foreach(_.remove(id))
  }

  def resolveEntityId(idOrShortid: String): Option[EntityId] =
    _canonical_entity_id(idOrShortid).orElse(_entity_id_by_shortid(idOrShortid))

  def resolveByReference(idOrShortid: String): Consequence[E] =
    resolveEntityId(idOrShortid) match {
      case Some(id) => resolve(id)
      case None => Consequence.successOrEntityNotFound(Option.empty[E])(Identifier(idOrShortid))
    }

  def uniqueValueExists(
    fieldName: String,
    value: String,
    excludeId: Option[EntityId],
    scope: EntityIdentityScope,
    includeEntityIdEntropy: Boolean
  )(using ctx: ExecutionContext): Boolean =
    _identity_candidates(scope).exists { case (_, id, record) =>
      !excludeId.exists(_.value == id.value) &&
        (
          SimpleEntityStorageShapePolicy.stringValue(record, fieldName).contains(value) ||
            (includeEntityIdEntropy && id.parts.entropy == value)
        )
    }

  def resolveIdentity(
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean,
    scope: EntityIdentityScope
  )(using ctx: ExecutionContext): Option[EntityId] =
    _identity_candidates(scope).collectFirst {
      case (_, id, record) if _identity_matches(id, record, value, fieldNames, includeEntityIdEntropy) =>
        id
      }

  private def _canonical_entity_id(
    idOrShortid: String
  ): Option[EntityId] =
    EntityId.parse(idOrShortid).toOption.filter { id =>
      id.collection.major == descriptor.collectionId.major &&
        id.collection.name == descriptor.collectionId.name
    }

  private def _entity_id_by_shortid(
    shortid: String
  ): Option[EntityId] =
    _all_values.find { entity =>
      val id = descriptor.persistent.id(entity)
      val record = descriptor.persistent.toRecord(entity)
      record.getString("shortid").contains(shortid) || id.parts.entropy == shortid
    }.map(descriptor.persistent.id)

  private def _all_values: Vector[E] =
    storage.memoryRealm.map(_.values).getOrElse(Vector.empty) ++
      storage.storeRealm.values

  private def _identity_candidates(
    scope: EntityIdentityScope
  )(using ctx: ExecutionContext): Vector[(E, EntityId, Record)] =
    _search_source.flatMap { entity =>
      val id = descriptor.persistent.id(entity)
      val record = descriptor.persistent.toRecord(entity)
      if (EntityLifecycleRecordPolicy.isLogicallyDeleted(record) || !scope.matches(record))
        None
      else
        Some((entity, id, record))
    }

  private def _identity_matches(
    id: EntityId,
    record: Record,
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean
  ): Boolean =
    id.value == value ||
      id.print == value ||
      fieldNames.exists(name => SimpleEntityStorageShapePolicy.stringValue(record, name).contains(value)) ||
      (includeEntityIdEntropy && id.parts.entropy == value)

  // Current phase search API:
  // route is available through EntitySpace/EntityCollection.
  // Query matching uses directive.Query condition evaluation.
  def search(
    query: EntityQuery[?]
  )(using ctx: ExecutionContext): Consequence[SearchResult[E]] = {
    val visibilitypolicy = _visibility_policy(query)
    val source = query.scope match {
      case EntitySearchScope.WorkingSet =>
        if (workingSetSearchAvailable) _working_set_source else _search_source
      case EntitySearchScope.Store => _search_source
    }
    val notdeleted = source.filterNot(_is_logically_deleted)
    val scoped = notdeleted.filter(entity => _is_access_scope_visible(entity, query.visibilityScope))
    val resident = query.scope match {
      case EntitySearchScope.WorkingSet =>
        if (workingSetSearchAvailable) scoped.filter(_is_resident) else scoped
      case EntitySearchScope.Store => scoped
    }
    val visible = resident.filter(v => _is_visible(descriptor.persistent.toRecord(v), visibilitypolicy))
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

  private def _search_source: Vector[E] = {
    val memory = storage.memoryRealm.map(_.values).getOrElse(Vector.empty)
    if (memory.isEmpty) {
      storage.storeRealm.values
    } else {
      val ids = memory.iterator.map(descriptor.persistent.id).toSet
      memory ++ storage.storeRealm.values.filterNot(entity => ids.contains(descriptor.persistent.id(entity)))
    }
  }

  private def _working_set_source: Vector[E] =
    storage.memoryRealm.map(_.values).getOrElse(_search_source)

  private def _cache_if_resident(
    entity: E
  ): Unit = {
    val id = descriptor.persistent.id(entity)
    storage.memoryRealm.foreach { memory =>
      if (_is_resident(entity))
        memory.put(entity)
      else
        memory.remove(id)
    }
  }

  private def _is_resident(
    entity: E
  ): Boolean =
    !_is_logically_deleted(entity) && (
      descriptor.plan.workingSetPolicy match {
        case Some(policy) => policy.isResident(descriptor.persistent.toRecord(entity))
        case None => true
      }
    )

  private def _has_effective_working_set_policy: Boolean =
    descriptor.plan.workingSetPolicy match {
      case Some(WorkingSetPolicy.Disabled) | None => false
      case Some(_) => true
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
    entityQuery: EntityQuery[?]
  )(using ctx: ExecutionContext): _VisibilityPolicy = {
    entityQuery.visibilityScope match {
      case Some(EntityVisibilityScope.Public) =>
        return _VisibilityPolicy(Some(Set("published")), Some(Set("alive")))
      case Some(EntityVisibilityScope.Owner) | Some(EntityVisibilityScope.Admin) =>
        return _VisibilityPolicy(None, None)
      case None =>
        ()
    }
    val query = entityQuery.query
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
          .flatMap(EntityLifecycleRecordPolicy.postStatusToken)
          .forall(allowed.contains)
      case None =>
        true
    }
    val aliveok = policy.alivenesses match {
      case Some(allowed) =>
        _record_value(record, Vector("aliveness"))
          .flatMap(EntityLifecycleRecordPolicy.alivenessToken)
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
      .flatMap(EntityLifecycleRecordPolicy.postStatusToken)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose = _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.postStatusToken)
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
      .flatMap(EntityLifecycleRecordPolicy.alivenessToken)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose = _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.alivenessToken)
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

  private def _post_status_token(p: Any): Option[String] =
    EntityLifecycleRecordPolicy.postStatusToken(p)

  private def _aliveness_token(p: Any): Option[String] =
    EntityLifecycleRecordPolicy.alivenessToken(p)

  private def _is_logically_deleted(
    entity: E
  ): Boolean = {
    val record = descriptor.persistent.toRecord(entity)
    EntityLifecycleRecordPolicy.isLogicallyDeleted(record)
  }

  private def _is_normal_access_visible(
    entity: E
  )(using ctx: ExecutionContext): Boolean = {
    val record = descriptor.persistent.toRecord(entity)
    // EntitySpace mirrors EntityStore normal access scope: deletedAt exclusion
    // now, and the prepared ExecutionContext tenant hook when it becomes active.
    EntityAccessScopePolicy.normalRecordVisible(descriptor.collectionId, record)
  }

  private def _is_access_scope_visible(
    entity: E,
    scope: Option[EntityVisibilityScope]
  )(using ctx: ExecutionContext): Boolean = {
    val record = descriptor.persistent.toRecord(entity)
    EntityAccessScopePolicy.visibilityRecordVisible(descriptor.collectionId, record, scope)
  }
}
