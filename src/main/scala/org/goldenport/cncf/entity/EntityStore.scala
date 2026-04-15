package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{DataStore, Query as DataStoreQuery, QueryDirective, QueryLimit, QueryOrder, OrderDirection}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 18, 2025
 *  version Jan. 10, 2026
 *  version Feb. 26, 2026
 *  version Mar. 30, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class EntityStore {
  def name: String
//  def serialize(entity: E): Consequence[Record]
//  def deserialize(record: Record): Consequence[E]
  def isAccept(cid: EntityCollectionId): Boolean = true

  def create[T](
    entity: T,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]]

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]]
  def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def deleteHard(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]]
}

object EntityStore {
  final val PROP_ID = "id"

  def noop() = NoopEntityStore()

  def standard(): EntityStore = StandardEntityStore()

  // final case class EntityId(
  //   major: String,
  //   minor: String,
  //   collection: CollectionId
  // ) extends UniversalId(major, minor, "entity", collection.name)

  // trait EntityInstance[T] {
  // }

  // def create[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
  //   ???
  // }

  // def load[T](store: EntityStore[T])(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
  //   ???
  // }

  // def search[T](store: EntityStore[T], directive: QueryDirective)(using instance: EntityInstance[T]): Consequence[SearchResult] = {
  //   ???
  // }

  // def store[T](store: EntityStore[T], id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def update[T](store: EntityStore[T], id: EntityId, changes: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def delete[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
  //   ???
  // }
}

final case class EntityCreateOptions(
  defaultProfiles: Set[String] = Set.empty,
  defaultValues: Record = Record.empty
) {
  def hasDefaultProfile(name: String): Boolean =
    defaultProfiles.contains(name.trim.toLowerCase(java.util.Locale.ROOT))
}
object EntityCreateOptions {
  val default: EntityCreateOptions = EntityCreateOptions()
}

case class CreateResult[T](
  id: EntityId,
  record: Option[Record] = None
) {
  def toRecord: Record = Record.data(
    "id" -> id.print
  )
}
case class GetResult[T]()
case class UpdateResult[T]()
case class DeleteResult[T]()

class NoopEntityStore() extends EntityStore {
  def name: String = "noop"
  def create[T](entity: T, options: EntityCreateOptions = EntityCreateOptions.default)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = ???
  def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = ???
  def save[T](entity: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = ???
  def update[T](changes: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = ???
  def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = ???
}

class StandardEntityStore(
) extends EntityStore {
  import EntityStore.*

  def name: String = "standard"

  def createId[T](entity: T)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): EntityId =
    {
      val collection = tc.collection(entity)
      EntityId(collection.major, collection.minor, collection)
    }

  def create[T](
    entity: T,
    options: EntityCreateOptions = EntityCreateOptions.default
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    val id = tc.id(entity) getOrElse createId(entity)
    val rec = _complement_create_record(tc.toRecord(entity), id, options)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      _ <- ds.create(cid, dsid, rec)
    } yield CreateResult(id, Some(rec))
  }

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      o <- ds.load(cid, dsid)
      visible = o.filterNot(_is_logically_deleted_record)
      _ = _emit_entity_access(
        "entity.load.hit.data-store",
        Record.dataAuto(
          "entity" -> id.collection.name,
          "id" -> id.value,
          "source" -> "data-store",
          "raw-count" -> o.size,
          "visible-count" -> visible.size
        )
      )
      r <- visible.traverse(tc.fromRecord)
    } yield r
  }

  def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = {
    val id = tc.id(entity)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      existing <- ds.load(cid, dsid)
      rec = _complement_save_record(tc.toRecord(entity), id, existing)
      r <- ds.save(cid, dsid, rec)
    } yield r
  }

  def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = {
    val id = tc.id(changes)
    val rec = _complement_update_record(tc.toRecord(changes), id)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      r <- ds.update(cid, dsid, rec)
    } yield r
  }

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      current <- ds.load(cid, dsid)
      r <- current match {
        case Some(rec) =>
          if (_is_soft_delete_target(rec))
            ds.update(cid, dsid, _soft_delete_record(rec))
          else
            ds.delete(cid, dsid)
        case None =>
          Consequence.unit
      }
    } yield r
  }

  def deleteHard(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      r <- ds.delete(cid, dsid)
    } yield r
  }

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      directive = QueryDirective(
        query = DataStoreQuery.Expr(EntityDirectiveQuery.whereOf(query.query)),
        order = _to_datastore_order(query.query.sort),
        limit = _to_datastore_limit(query.query)
      )
      raw <- ctx.dataStoreSpace.search(
        cid,
        directive
      )
      count <- if (query.query.includeTotal)
        ctx.dataStoreSpace.count(cid, directive.copy(limit = QueryLimit.Unbounded)).map(Some(_))
      else
        Consequence.success(None)
      notdeleted = raw.records.toVector.filterNot(_is_logically_deleted_record)
      visible = _filter_visibility(notdeleted, query.query)
      _ = _emit_entity_access(
        "entity.search.hit.data-store",
        Record.dataAuto(
          "entity" -> query.collection.name,
          "source" -> "data-store",
          "raw-count" -> raw.records.size,
          "notdeleted-count" -> notdeleted.size,
          "visible-count" -> visible.size
        )
      )
      _ = _emit_visibility_filtered(
        query.collection.name,
        raw.records.size,
        notdeleted.size,
        visible.size
      )
      decoded <- visible.traverse(tc.fromRecord)
      filtered = decoded.filter(x => EntityDirectiveQuery.matches(query.query, x))
      sorted = EntityDirectiveQuery.sortValues(filtered, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = count,
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )
  }

  private def _to_datastore_limit(
    query: EntityDirectiveQuery[?]
  ): QueryLimit =
    {
      val limit = for {
        l <- query.limit
      } yield query.offset.getOrElse(0) + l
      limit.map(QueryLimit.Limit.apply).getOrElse(QueryLimit.Unbounded)
    }

  private final case class _VisibilityPolicy(
    postStatuses: Option[Set[String]],
    alivenesses: Option[Set[String]]
  )

  private def _filter_visibility(
    records: Vector[Record],
    query: EntityDirectiveQuery[?]
  )(using ctx: ExecutionContext): Vector[Record] = {
    val policy = _visibility_policy(query)
    records.filter(_is_visible(_, policy))
  }

  private def _visibility_policy(
    query: EntityDirectiveQuery[?]
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

  private final case class _LifecycleConstraint(
    postStatusExplicit: Boolean,
    alivenessExplicit: Boolean
  )

  private def _lifecycle_constraint(
    query: EntityDirectiveQuery[?]
  ): _LifecycleConstraint = {
    val expr = EntityDirectiveQuery.whereOf(query)
    val raw = _query_condition(query)
    _LifecycleConstraint(
      postStatusExplicit = _mentions_path(expr, Set("poststatus")) || _mentions_condition_key(raw, Set("poststatus")),
      alivenessExplicit = _mentions_path(expr, Set("aliveness")) || _mentions_condition_key(raw, Set("aliveness"))
    )
  }

  private def _query_condition(
    query: EntityDirectiveQuery[?]
  ): Any =
    query.query match {
      case p: EntityDirectiveQuery.Plan[?] => p.condition
      case other => other
    }

  private def _mentions_path(
    expr: EntityDirectiveQuery.Expr,
    names: Set[String]
  ): Boolean =
    expr match {
      case EntityDirectiveQuery.True => false
      case EntityDirectiveQuery.False => false
      case EntityDirectiveQuery.And(items) => items.exists(_mentions_path(_, names))
      case EntityDirectiveQuery.Or(items) => items.exists(_mentions_path(_, names))
      case EntityDirectiveQuery.Not(item) => _mentions_path(item, names)
      case EntityDirectiveQuery.FieldCondition(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Eq(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Ne(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Gt(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Gte(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Lt(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Lte(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.In(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.NotIn(path, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.IsNull(path) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.IsNotNull(path) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Like(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.StartsWith(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.EndsWith(path, _, _) => names.contains(_normalize_path(path))
      case EntityDirectiveQuery.Contains(path, _, _) => names.contains(_normalize_path(path))
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

  private def _to_datastore_order(
    sort: Vector[EntityDirectiveQuery.SortKey]
  ): QueryOrder =
    sort.headOption match {
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Asc)) =>
        QueryOrder.By(path, OrderDirection.Asc)
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Desc)) =>
        QueryOrder.By(path, OrderDirection.Desc)
      case None =>
        QueryOrder.None
    }

  private def _complement_create_record[T](
    record: Record,
    id: EntityId,
    options: EntityCreateOptions
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record =
    ctx.runtime.entityCreateDefaultsPolicy.complementCreateRecord(
      record = record,
      id = id,
      options = options
    )

  private def _complement_save_record(
    record: Record,
    id: EntityId,
    existing: Option[Record]
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = existing,
      includesCreationDefaults = true,
      includesStateDefaults = true,
      createOptions = EntityCreateOptions.default
    )

  private def _complement_update_record(
    record: Record,
    id: EntityId
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = None,
      includesCreationDefaults = false,
      includesStateDefaults = false,
      createOptions = EntityCreateOptions.default
    )

  private def _complement_record(
    record: Record,
    id: EntityId,
    existing: Option[Record],
    includesCreationDefaults: Boolean,
    includesStateDefaults: Boolean,
    createOptions: EntityCreateOptions
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val propertyname = ctx.runtime.context.propertyName
    val defaults = Vector.newBuilder[(String, Any)]
    val existingmap = existing.map(_.asMap).getOrElse(Map.empty)
    val knownkeys = record.keySet ++ existingmap.keySet

    def key(canonical: String): String =
      propertyname.preferredName(knownkeys, canonical)

    def add_if_missing(canonical: String, value: => Option[Any]): Unit =
      if (!propertyname.aliases(canonical).exists(record.keySet.contains))
        value.foreach(v => defaults += (key(canonical) -> v))

    def existing_value(canonical: String): Option[Any] =
      propertyname.aliases(canonical).collectFirst(Function.unlift(existingmap.get))

    if (includesCreationDefaults) {
      add_if_missing("id", existing_value("id").orElse(Some(id.print)))
      add_if_missing("name", existing_value("name").orElse(Some(principalid)))
      add_if_missing("createdAt", existing_value("createdAt").orElse(Some(now)))
      add_if_missing("createdBy", existing_value("createdBy").orElse(Some(principal)))
    }

    add_if_missing("updatedAt", Some(now))
    add_if_missing("updatedBy", Some(principal))
    if (includesStateDefaults) {
      add_if_missing("postStatus", existing_value("postStatus").orElse(Some(_default_post_status(createOptions))))
      add_if_missing("aliveness", existing_value("aliveness").orElse(Some(Aliveness.default)))
    }
    if (includesCreationDefaults && createOptions.hasDefaultProfile("publication")) {
      add_if_missing("publishAt", existing_value("publishAt").orElse(Some(now)))
      add_if_missing("publicAt", existing_value("publicAt").orElse(Some(now)))
      add_if_missing("publishedBy", existing_value("publishedBy").orElse(Some(principal)))
      add_if_missing("securityAttributes", existing_value("securityAttributes").orElse(Some(_public_security_attributes(principal))))
    }
    add_if_missing("traceId", Some(ctx.observability.traceId.value))
    add_if_missing("correlationId", ctx.observability.correlationId.map(_.value))

    val complement = Record.dataAuto(defaults.result()*)
    record ++ complement
  }

  private def _default_post_status(
    options: EntityCreateOptions
  ): Any =
    if (options.hasDefaultProfile("publication"))
      PostStatus.Published
    else
      PostStatus.default

  private def _public_security_attributes(
    principal: String
  ): Record =
    org.simplemodeling.model.value.SecurityAttributes.publicOwnedBy(principal).toRecord

  private def _soft_delete_record(
    existing: Record
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val keyset = existing.keySet
    val propertyname = ctx.runtime.context.propertyName

    def key(canonical: String): String =
      propertyname.preferredName(keyset, canonical)

    val base = Vector.newBuilder[(String, Any)]
    base += key("postStatus") -> PostStatus.Archived
    base += key("aliveness") -> Aliveness.Dead
    base += key("updatedAt") -> now
    base += key("updatedBy") -> principal
    base += key("traceId") -> ctx.observability.traceId.value
    ctx.observability.correlationId.foreach { x =>
      base += key("correlationId") -> x.value
    }
    if (propertyname.aliases("deletedAt").exists(keyset.contains))
      base += key("deletedAt") -> now
    if (propertyname.aliases("deletedBy").exists(keyset.contains))
      base += key("deletedBy") -> principal
    Record.dataAuto(base.result()*)
  }

  private def _is_soft_delete_target(existing: Record): Boolean = {
    val keyset = existing.keySet
    keyset.contains("aliveness")
  }

  private def _is_logically_deleted_record(
    record: Record
  ): Boolean = {
    val keyset = record.keySet
    val propertyname = org.goldenport.cncf.context.RuntimeContext.Context.default.propertyName
    val hasdeletedat = propertyname.aliases("deletedAt").exists(keyset.contains)
    val poststatus = _record_value(record, Vector("postStatus")).flatMap(_post_status_token)
    val aliveness = _record_value(record, Vector("aliveness")).flatMap(_aliveness_token)
    hasdeletedat || poststatus.contains("archived") || aliveness.contains("dead")
  }

  private def _emit_entity_access(
    name: String,
    attributes: Record
  )(using ctx: ExecutionContext): Unit = {
    EntityAccessMetricsRegistry.shared.record(name, attributes)
    val _ = ctx.observability.emitInfo(ctx.cncfCore.scope, name, attributes)
  }

  private def _emit_visibility_filtered(
    entity: String,
    rawCount: Int,
    notdeletedCount: Int,
    visibleCount: Int
  )(using ctx: ExecutionContext): Unit = {
    val filteredCount = notdeletedCount - visibleCount
    if (filteredCount > 0) {
      val _ = _emit_entity_access(
        "entity.search.filtered.visibility",
        Record.dataAuto(
          "entity" -> entity,
          "source" -> "data-store",
          "raw-count" -> rawCount,
          "notdeleted-count" -> notdeletedCount,
          "visible-count" -> visibleCount,
          "filtered-count" -> filteredCount
        )
      )
    }
  }
}
