package org.goldenport.cncf.entity.runtime

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.goldenport.cncf.entity.{
  EntityAccessScopePolicy,
  EntityIdentityScope,
  EntityLifecycleRecordPolicy,
  EntityMutationExecutionPolicy,
  EntityPersistent,
  EntityPersistentCreate,
  EntityQuery,
  EntityRecordSnapshot,
  EntityRevisionCarrier,
  EntitySearchScope,
  EntityVisibilityScope,
  SimpleEntityStorageShapePolicy
}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.observability.{CallTreeValueSummary, ConclusionDiagnostics}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp

/*
 * @since   Mar. 14, 2026
 *  version Mar. 30, 2026
 *  version May. 10, 2026
 * @version Jul. 28, 2026
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
    query.scope == EntitySearchScope
      .WorkingSet && _has_effective_working_set_policy && !workingSetStatus.isReady

  def put(entity: E): Unit =
    _residency(entity, None) match {
      case Some(resident) => _put(entity, resident)
      case None =>
        throw new IllegalStateException(_time_dependent_policy_message)
    }

  def putScoped(
    entity: E
  )(using ctx: ExecutionContext): Unit = {
    val evaluationinstant = ctx.clock.instant()
    _put(entity, evaluationinstant)
  }

  private def _put(
    entity: E,
    evaluationinstant: Instant
  ): Unit =
    _put(entity, _is_resident(entity, evaluationinstant))

  private def _put(
    entity: E,
    resident: Boolean
  ): Unit = {
    val id = descriptor.persistent.id(entity)
    storage.storeRealm.put(entity)
    storage.memoryRealm.foreach { memory =>
      if (resident)
        memory.put(entity)
      else
        memory.remove(id)
    }
  }

  def putRecord(record: Record): Consequence[Unit] =
    descriptor.persistent.fromRecord(record).flatMap { entity =>
      _residency(entity, None) match {
        case Some(resident) =>
          _put(entity, resident)
          Consequence.unit
        case None =>
          Consequence.operationInvalid(
            "entity.working-set.admission",
            _time_dependent_policy_message
          )
      }
    }

  def putRecordScoped(
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val evaluationinstant = ctx.clock.instant()
    descriptor.persistent.fromRecord(record).map { entity =>
      _put(entity, evaluationinstant)
    }
  }

  def createRecordSynced(
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val evaluationinstant = ctx.clock.instant()
    for {
      admitted <- descriptor.revisionBinding match {
        case Some(binding)
            if binding.representation ==
              org.goldenport.cncf.entity.EntityRevisionRepresentation.Embedded =>
          binding
            .rejectManagedPatch(record, "entity")
            .map(
              _.upsertSingle(
                binding.storageFieldName,
                EntityRevision.INITIAL.value
              )
            )
        case Some(binding) =>
          binding.rejectManagedPatch(record, "entity")
        case None =>
          Consequence.success(record)
      }
      entity <- descriptor.persistent.fromRecord(admitted)
      create = new EntityPersistentCreate[E] {
        def id(value: E): Option[EntityId] =
          Some(descriptor.persistent.id(value))
        def toRecord(value: E): Record =
          descriptor.persistent.toRecord(value)
        override def toStoreRecord(value: E): Record =
          descriptor.revisionBinding
            .map(
              _.withoutManagedRevision(
                descriptor.persistent.toStoreRecord(value)
              )
            )
            .getOrElse(descriptor.persistent.toStoreRecord(value))
        def collection(value: E) =
          descriptor.persistent.id(value).collection
      }
      created <- ctx.entityStoreSpace.create(
        UnitOfWorkOp.EntityStoreCreate(entity, create)
      )
      persisted <- (
        created.record match {
          case Some(record) =>
            descriptor.revisionBinding match {
              case Some(binding) =>
                binding.decodeEntity(record)(
                  EntityPersistent._decode_store_record(
                    descriptor.persistent,
                    descriptor.collectionId,
                    _
                  )
                )
              case None =>
                EntityPersistent._decode_store_record(
                  descriptor.persistent,
                  descriptor.collectionId,
                  record
                )
            }
          case None =>
            EntityPersistent._require_exact_collection(
              entity,
              descriptor.persistent.id(entity),
              descriptor.collectionId
            )
        }
      ).recoverWith { conclusion =>
        ctx.entityStoreSpace
          .deleteHard(UnitOfWorkOp.EntityStoreDeleteHard(created.id))
          .flatMap(_ => Consequence.Failure[E](conclusion))
      }
      _ = _put(persisted, evaluationinstant)
    } yield ()
  }

  def saveRecordVersioned(
      record: Record,
      expectedRevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
    saveRecordVersioned(
      record,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def saveRecordVersioned(
      record: Record,
      expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy
  )(using ctx: ExecutionContext): Consequence[EntityRecordSnapshot] = {
    val evaluationinstant = ctx.clock.instant()
    descriptor.persistent.fromRecord(record).flatMap { entity =>
      val entityid = descriptor.persistent.id(entity)
      ctx.entityStoreSpace.saveVersioned(
        entity,
        descriptor.persistent,
        expectedRevision,
        executionPolicy
      ).map { snapshot =>
        _put(snapshot.entity, evaluationinstant)
        EntityRecordSnapshot(
          descriptor.persistent.toRecord(snapshot.entity),
          snapshot.revision
        )
      }.recoverWith { conclusion =>
        val reason = ConclusionDiagnostics.classify(conclusion).reason
        if (
          reason.contains("stale-entity-revision") ||
          reason.contains("committed-entity-projection-failure")
        )
          evict(entityid)
        Consequence.Failure(conclusion)
      }
    }
  }

  def saveRecordManaged(
    record: Record,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val evaluationinstant = ctx.clock.instant()
    descriptor.persistent.fromRecord(record).flatMap { entity =>
      val entityid = descriptor.persistent.id(entity)
      (
        for {
          saved <- ctx.entityStoreSpace.saveManaged(
            entity,
            descriptor.persistent,
            executionPolicy
          )
        } yield {
          _put(saved, evaluationinstant)
          ()
        }
      ).recoverWith { conclusion =>
          val reason = ConclusionDiagnostics.classify(conclusion).reason
          if (reason.contains("committed-entity-projection-failure"))
            evict(entityid)
          Consequence.Failure(conclusion)
      }
    }
  }

  def saveRecordDetached(
      record: Record,
      expectedRevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[EntityRevisionCarrier[Record]] =
    saveRecordDetached(
      record,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def saveRecordDetached(
      record: Record,
      expectedRevision: Option[EntityRevision],
      executionPolicy: EntityMutationExecutionPolicy
  )(using ctx: ExecutionContext): Consequence[EntityRevisionCarrier[Record]] = {
    val evaluationinstant = ctx.clock.instant()
    descriptor.persistent.fromRecord(record).flatMap { entity =>
      val entityid = descriptor.persistent.id(entity)
      ctx.entityStoreSpace.saveDetached(
        UnitOfWorkOp.EntityStoreSaveDetached(
          entity,
          expectedRevision,
          descriptor.persistent,
          executionPolicy = executionPolicy
        )
      ).map { carrier =>
        _put(carrier.entity, evaluationinstant)
        EntityRevisionCarrier(
          descriptor.persistent.toRecord(carrier.entity),
          carrier.revision
        )
      }.recoverWith { conclusion =>
        val reason = ConclusionDiagnostics.classify(conclusion).reason
        if (
          reason.contains("stale-entity-revision") ||
          reason.contains("committed-entity-projection-failure")
        )
          evict(entityid)
        Consequence.Failure(conclusion)
      }
    }
  }

  // Load-through resolution:
  // 1. Try MemoryRealm (working set cache)
  // 2. Fallback to StoreRealm
  // 3. If loaded from StoreRealm and MemoryRealm exists, cache it
  def resolve(id: EntityId): Consequence[E] =
    _resolve(id, entity => !_is_logically_deleted(entity), None)

  override def resolveScoped(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[E] =
    _with_calltree(
      "space:entity:resolve",
      _entity_collection_attributes("resolve") + ("entity_id" -> id.print)
    ) {
      _resolve(id, _is_normal_access_visible, Some(ctx.clock.instant()))
    }

  private def _resolve(
    id: EntityId,
    visible: E => Boolean,
    evaluationinstant: Option[Instant]
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
            _cache_if_resident(entity, evaluationinstant)
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
            _cache_if_resident(entity, evaluationinstant)
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
      case None     => Consequence.successOrEntityNotFound(Option.empty[E])(Identifier(idOrShortid))
    }

  def uniqueValueExists(
    fieldName: String,
    value: String,
    excludeId: Option[EntityId],
    scope: EntityIdentityScope,
    includeEntityIdEntropy: Boolean
  )(using ctx: ExecutionContext): Boolean =
    _with_calltree(
      "space:entity:unique-value-exists",
      _entity_collection_attributes("unique-value-exists") + ("field" -> fieldName)
    ) {
      _identity_candidates(scope).exists { case (_, id, record) =>
        !excludeId.exists(_.value == id.value) &&
          (
            SimpleEntityStorageShapePolicy.stringValue(record, fieldName).contains(value) ||
              (includeEntityIdEntropy && id.parts.entropy == value)
          )
      }
    }

  def resolveIdentity(
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean,
    scope: EntityIdentityScope
  )(using ctx: ExecutionContext): Option[EntityId] =
    _with_calltree(
      "space:entity:resolve-identity",
      _entity_collection_attributes("resolve-identity")
    ) {
      _identity_candidates(scope).collectFirst {
        case (_, id, record)
            if _identity_matches(id, record, value, fieldNames, includeEntityIdEntropy) =>
          id
      }
    }

  private def _canonical_entity_id(
      idorshortid: String
  ): Option[EntityId] =
    EntityId.parse(idorshortid).toOption
      .filter(_.collection.name == descriptor.collectionId.name)
      .map { id =>
        if (id.collection == descriptor.collectionId)
          id
        else
          id.copy(collection = descriptor.collectionId)
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
      fieldnames: Vector[String],
      includeentityidentropy: Boolean
  ): Boolean =
    id.value == value ||
      id.print == value ||
      fieldnames.exists(name =>
        SimpleEntityStorageShapePolicy.stringValue(record, name).contains(value)
      ) ||
      (includeentityidentropy && id.parts.entropy == value)

  // Current phase search API:
  // route is available through EntitySpace/EntityCollection.
  // Query matching uses directive.Query condition evaluation.
  def search(
    query: EntityQuery[?]
  )(using ctx: ExecutionContext): Consequence[SearchResult[E]] = {
    val evaluationinstant = ctx.clock.instant()
    _with_calltree(
      "space:entity:search",
      _entity_collection_attributes("search") ++ Map(
        "scope"             -> query.scope.toString,
        "working_set_ready" -> workingSetStatus.isReady.toString
      )
    ) {
      val visibilitypolicy = _visibility_policy(query)
      val source = query.scope match {
        case EntitySearchScope.WorkingSet =>
          if (workingSetSearchAvailable) _working_set_source else _search_source
        case EntitySearchScope.Store => _search_source
      }
      val notdeleted = source.filterNot(_is_logically_deleted)
      val scoped =
        notdeleted.filter(entity => _is_access_scope_visible(entity, query.visibilityScope))
      val resident = query.scope match {
        case EntitySearchScope.WorkingSet =>
          if (workingSetSearchAvailable) scoped.filter(_is_resident(_, evaluationinstant))
          else scoped
        case EntitySearchScope.Store => scoped
      }
      val visible =
        resident.filter(v => _is_visible(descriptor.persistent.toRecord(v), visibilitypolicy))
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
  }

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[?] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
          case other =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other)
            )
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave()
          throw e
      }
    } else {
      body
    }
  }

  private def _entity_collection_attributes(
    operation: String
  ): Map[String, String] =
    Map(
      "space" -> "entity",
      "operation" -> operation,
      "collection" -> descriptor.collectionId.print
    )

  private def _search_source: Vector[E] = {
    val memory = storage.memoryRealm.map(_.values).getOrElse(Vector.empty)
    if (memory.isEmpty) {
      storage.storeRealm.values
    } else {
      val ids = memory.iterator.map(descriptor.persistent.id).toSet
      memory ++ storage.storeRealm.values.filterNot(entity =>
        ids.contains(descriptor.persistent.id(entity))
      )
    }
  }

  private def _working_set_source: Vector[E] =
    storage.memoryRealm.map(_.values).getOrElse(_search_source)

  private def _cache_if_resident(
    entity: E,
    evaluationinstant: Option[Instant]
  ): Unit = {
    val id = descriptor.persistent.id(entity)
    storage.memoryRealm.foreach { memory =>
      _residency(entity, evaluationinstant).foreach { resident =>
        if (resident)
          memory.put(entity)
        else
          memory.remove(id)
      }
    }
  }

  private def _is_resident(
    entity: E,
    evaluationinstant: Instant
  ): Boolean =
    _residency(entity, Some(evaluationinstant)).contains(true)

  private def _residency(
    entity: E,
    evaluationinstant: Option[Instant]
  ): Option[Boolean] =
    if (_is_logically_deleted(entity))
      Some(false)
    else
      descriptor.plan.workingSetPolicy match {
        case Some(WorkingSetPolicy.Disabled) => Some(false)
        case Some(WorkingSetPolicy.ResidentAll) => Some(true)
        case Some(policy) =>
          evaluationinstant.map(policy.isResident(descriptor.persistent.toRecord(entity), _))
        case None => Some(true)
      }

  private def _has_effective_working_set_policy: Boolean =
    descriptor.plan.workingSetPolicy match {
      case Some(WorkingSetPolicy.Disabled) | None => false
      case Some(_) => true
    }

  private def _time_dependent_policy_message: String =
    "time-dependent working-set policy evaluation requires ExecutionContext"

  private final case class _VisibilityPolicy(
    poststatuses: Option[Set[String]],
    alivenesses: Option[Set[String]]
  )

  private final case class _LifecycleConstraint(
    poststatusexplicit: Boolean,
    alivenessexplicit: Boolean
  )

  private def _visibility_policy(
      entityquery: EntityQuery[?]
  )(using ctx: ExecutionContext): _VisibilityPolicy = {
    entityquery.visibilityScope match {
      case Some(EntityVisibilityScope.Public) =>
        return _VisibilityPolicy(Some(Set("published")), Some(Set("alive")))
      case Some(EntityVisibilityScope.Owner) | Some(EntityVisibilityScope.Admin) =>
        return _VisibilityPolicy(None, None)
      case None =>
        ()
    }
    val query     = entityquery.query
    val lifecycle = _lifecycle_constraint(query)
    val ismanager = _is_content_manager()
    val poststatuses = if (lifecycle.poststatusexplicit) {
      None
    } else if (ismanager) {
      val p = _post_statuses_for_manager()
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("published"))
    }
    val alivenesses = if (lifecycle.alivenessexplicit) {
      None
    } else if (ismanager) {
      val p = _alivenesses_for_manager(poststatuses.getOrElse(Set.empty))
      if (p.isEmpty) None else Some(p)
    } else {
      Some(Set("alive"))
    }
    _VisibilityPolicy(
      poststatuses = poststatuses,
      alivenesses = alivenesses
    )
  }

  private def _lifecycle_constraint(
    query: Query[?]
  ): _LifecycleConstraint = {
    val expr = Query.whereOf(query)
    val raw = _query_condition(query)
    _LifecycleConstraint(
      poststatusexplicit =
        _mentions_path(expr, Set("poststatus")) || _mentions_condition_key(raw, Set("poststatus")),
      alivenessexplicit =
        _mentions_path(expr, Set("aliveness")) || _mentions_condition_key(raw, Set("aliveness"))
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
        m.keysIterator.collect { case k: String => k }.exists(k =>
          names.contains(_normalize_path(k))
        )
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
    val postok = policy.poststatuses match {
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
    val configured =
      _attribute_tokens("search_poststatus", "search.poststatus", "poststatus", "post_status")
      .flatMap(EntityLifecycleRecordPolicy.postStatusToken)
    if (configured.nonEmpty)
      configured
    else {
      val frompurpose =
        _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.postStatusToken)
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
      val frompurpose =
        _attribute_tokens("purpose").flatMap(EntityLifecycleRecordPolicy.alivenessToken)
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
