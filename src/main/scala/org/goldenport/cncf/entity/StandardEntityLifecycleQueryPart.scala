package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalExpectedField,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationExecutionPath,
  EntityMutationExclusionGuard,
  EntityMutationPathRequest,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation,
  EntityVersionedSideEffect,
  Query as DataStoreQuery,
  QueryDirective,
  QueryLimit,
  QueryOrder,
  OrderDirection
}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[entity] trait StandardEntityLifecycleQueryPart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

  private[cncf] override def conditionalTransition[R, P, S](
    command: EntityConditionalTransitionCommand[R, P, S]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] = {
    val request = command.request
    val rootid = request.rootId
    for {
      rootbinding <- _required_revision_binding(rootid.collection)
      successorbinding <-
        _required_revision_binding(request.successor.collection)
      rootcollection <- ctx.entityStoreSpace.dataStoreCollection(rootid)
      rootentry <- ctx.entityStoreSpace.dataStoreEntryId(rootid)
      revision <- EntityConcurrencyMetadata.mutationRevision(
        request.expectation.expectedRevision
      )
      _ <- _reject_logically_deleted_existing(
        rootid,
        Some(command.currentRootRecord)
      )
      rootchanges <- _admit_conditional_root_changes(
        Update.toChangesRecord(
          request.patchPersistent.toStoreRecord(request.rootPatch)
        )
      )
      rootcandidate <- _merge_versioned_update_record(
        command.currentRootRecord,
        _complement_update_record(rootchanges, rootid),
        rootbinding
      )
      _ <- _require_conditional_domain_change(
        command.currentRootRecord,
        rootcandidate
      )
      rootpreparation <- ContentBodyStoragePolicy.planForVersionedSave(
        rootid,
        rootbinding.withoutManagedRevision(rootcandidate),
        preserveExistingOverflowOnMissingContent = true
      )
      providerchanges =
        _conditional_record_delta(
          _conditional_storage_record(
            rootbinding.withoutManagedRevision(
              command.currentRootRecord
            )
          ),
          _conditional_storage_record(
            rootbinding.withoutManagedRevision(
              rootpreparation.record
            )
          )
        )
      root <- DataStoreConditionalRoot.create(
        componentOwner = command.componentOwner,
        collection = rootcollection,
        entryId = rootentry,
        revisionField = rootbinding.storageFieldName,
        expectedRevision = Some(revision._1),
        expectedFields = request.expectation.values.map { expected =>
          DataStoreConditionalExpectedField(
            expected.field.storageField,
            expected.providerValue
          )
        },
        changes = providerchanges,
        nextRevision = revision._2
      )
      preparedsuccessor <- _prepare_conditional_successor(
        request.successor,
        command.componentOwner,
        command.boundSuccessor,
        successorbinding
      )
      plan <- DataStoreConditionalTransitionPlan.create(
        root,
        preparedsuccessor._1,
        _conditional_storage_side_effects(
          rootpreparation.sideEffects ++ preparedsuccessor._2
        )
      )
      providerresult <- _with_datastore_calltree(
        "entity-conditional-transition",
        rootcollection,
        Some(rootentry)
      ) {
        ctx.dataStoreSpace.conditionalTransition(plan)
      }
      result <- _conditional_transition_result(
        request,
        preparedsuccessor._3,
        providerresult,
        rootbinding,
        successorbinding
      )
    } yield result
  }

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val revisionbinding = _revision_binding_option(id.collection)
    val policy = EntityMutationExecutionPolicy(
      concurrencyPolicy = _concurrency_policy(id.collection)
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      current <- _raw_record(cid, dsid)
      r <- current match {
        case Some(rec) =>
          if (_is_soft_delete_target(rec))
            revisionbinding match {
              case Some(binding) =>
                for {
                  expected <- _effective_expected_revision(
                    binding,
                    rec,
                    None,
                    policy
                  )
                  updated <- _merge_versioned_update_record(
                    rec,
                    _soft_delete_record(rec),
                    binding
                  )
                  preparation <- ContentBodyStoragePolicy.planForVersionedSave(
                    id,
                    binding.withoutManagedRevision(updated),
                    preserveExistingOverflowOnMissingContent = true
                  )
                  result <- _mutate_versioned(
                    cid,
                    dsid,
                    preparation,
                    binding,
                    expected,
                    policy
                  )
                  _ <- _mutation_unit(result)
                } yield ()
              case None =>
                _save_plain_lifecycle(
                  id,
                  cid,
                  dsid,
                  rec,
                  _soft_delete_record(rec)
                )
            }
          else
            ContentBodyStoragePolicy.deleteOverflow(id).flatMap { _ =>
              _with_datastore_calltree("delete", cid, Some(dsid)) {
                ctx.dataStoreSpace
                  .dataStore(cid)
                  .flatMap(_.delete(cid, dsid))
              }
            }
        case None =>
          Consequence.unit
      }
    } yield r
  }

  def restore(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val revisionbinding = _revision_binding_option(id.collection)
    val policy = EntityMutationExecutionPolicy(
      concurrencyPolicy = _concurrency_policy(id.collection)
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      current <- _raw_record(cid, dsid)
      base <- _required_record(dsid, current)
      _ <-
        if (_is_logically_deleted_record(base))
          Consequence.unit
        else
          Consequence.stateConflict(
            s"Entity is not logically deleted: ${id.print}"
          )
      _ <- revisionbinding match {
        case Some(binding) =>
          for {
            expected <- _effective_expected_revision(
              binding,
              base,
              None,
              policy
            )
            restored <- _merge_versioned_update_record(
              base,
              _restore_record(base),
              binding
            )
            preparation <- ContentBodyStoragePolicy.planForVersionedSave(
              id,
              binding.withoutManagedRevision(restored),
              preserveExistingOverflowOnMissingContent = true
            )
            result <- _mutate_versioned(
              cid,
              dsid,
              preparation,
              binding,
              expected,
              policy
            )
            _ <- _mutation_unit(result)
          } yield ()
        case None =>
          _save_plain_lifecycle(
            id,
            cid,
            dsid,
            base,
            _restore_record(base)
          )
      }
    } yield ()
  }

  def deleteHard(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id.collection)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      _ <- ContentBodyStoragePolicy.deleteOverflow(id)
      r <- _with_datastore_calltree("delete", cid, Some(dsid)) {
        ds.delete(cid, dsid)
      }
    } yield r

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    val storequery = EntityDirectiveQuery.mapPaths(query.query)(tc.storeFieldName)
    // Push normal entity access scope into the datastore query where possible.
    // The same scope is post-filtered below so in-memory/SQL/direct stores keep
    // identical deletedAt and future tenant semantics.
    val scopedexpr = EntityAccessScopePolicy.normalSearchExpr(
      query.collection,
      EntityDirectiveQuery.whereOf(storequery),
      tc.storeFieldName("deletedAt")
    )
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      directive = QueryDirective(
        query = DataStoreQuery.Expr(scopedexpr),
        order = _to_datastore_order(EntityDirectiveQuery.sortOf(storequery)),
        limit = QueryLimit.Unbounded,
        offset = 0
      )
      raw <- ctx.dataStoreSpace.search(
        cid,
        directive
      )
      // Safety filter paired with the query predicate above. This is important
      // for logical delete and for future ExecutionContext tenant scoping.
      scoped = EntityAccessScopePolicy.filterNormalRecords(query.collection, raw.records.toVector)
      accessscoped = scoped.filter(record =>
        EntityAccessScopePolicy.visibilityRecordVisible(
          query.collection,
          record,
          query.visibilityScope
        )
      )
      visible = query.visibilityScope match {
        case Some(EntityVisibilityScope.Owner) | Some(EntityVisibilityScope.Admin) =>
          accessscoped
        case _ =>
          _filter_visibility(accessscoped, query.query)
      }
      _ = _emit_entity_access(
        "entity.search.hit.data-store",
        Record.dataAuto(
          "entity" -> query.collection.name,
          "source" -> "data-store",
          "raw-count" -> raw.records.size,
          "scoped-count" -> scoped.size,
          "access-scoped-count" -> accessscoped.size,
          "visible-count" -> visible.size
        )
      )
      _ = _emit_visibility_filtered(
        query.collection.name,
        raw.records.size,
        accessscoped.size,
        visible.size
      )
      // Apply the original entity query against store records before decoding.
      // Some entity codecs intentionally expose richer value objects after
      // decode (for example AssociationDomain), while request/query values stay
      // in their wire/store shape. Filtering here keeps in-memory stores and
      // SQL stores consistent without imposing entity-value equality quirks.
      recordmatched = visible.filter(record => EntityDirectiveQuery.matches(storequery, record))
      hydrated <- ContentBodyStoragePolicy.hydrateAll(query.collection, recordmatched)
      decoded <- hydrated.traverse(
        _decode_entity(query.collection, _, tc)
      )
      sorted = EntityDirectiveQuery.sortValues(decoded, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
      count = if (query.query.includeTotal) Some(recordmatched.size) else None
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = count,
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )
  }

  def searchInternal[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      raw <- ctx.dataStoreSpace.search(
        cid,
        QueryDirective(DataStoreQuery.Empty)
      )
      scoped = EntityAccessScopePolicy.filterNormalRecords(query.collection, raw.records.toVector)
      hydrated <- ContentBodyStoragePolicy.hydrateAll(query.collection, scoped)
      decoded <- hydrated.foldLeft(Consequence.success(Vector.empty[T])) { (z, record) =>
        z.flatMap(xs =>
          _decode_entity(query.collection, record, tc)
            .map(xs :+ _)
        )
      }
      matched = decoded.filter(value => EntityDirectiveQuery.matches(query.query, value))
      sorted = EntityDirectiveQuery.sortValues(matched, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = if (query.query.includeTotal) Some(matched.size) else None,
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )

  def uniqueValueExists[T](
    collection: EntityCollectionId,
    @deprecatedName("fieldName", "0.5.1")
      fieldName: String,
    value: String,
    @deprecatedName("excludeId", "0.5.1")
      excludeId: Option[EntityId],
    scope: EntityIdentityScope,
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
      includeEntityIdEntropy: Boolean
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean] =
    _identity_records(collection).map { candidates =>
      candidates.exists { case (record, id) =>
        !excludeId.exists(_.value == id.value) &&
          scope.matches(record) &&
          (
          SimpleEntityStorageShapePolicy.stringValue(record, fieldName).contains(value) ||
            (includeEntityIdEntropy && id.parts.entropy == value)
          )
      }
    }

  def resolveIdentity[T](
    collection: EntityCollectionId,
    value: String,
    @deprecatedName("fieldNames", "0.5.1")
      fieldNames: Vector[String],
    @deprecatedName("includeEntityIdEntropy", "0.5.1")
      includeEntityIdEntropy: Boolean,
    scope: EntityIdentityScope
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]] =
    _identity_records(collection).map { candidates =>
      candidates.collectFirst {
        case (record, id)
            if scope.matches(record) && _identity_matches(
              id,
              record,
              value,
              fieldNames,
              includeEntityIdEntropy
            ) =>
          id
    }
  }

  private def _identity_records[T](
    collection: EntityCollectionId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Vector[(Record, EntityId)]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(collection)
      raw <- ctx.dataStoreSpace.search(
        cid,
        QueryDirective(DataStoreQuery.Empty)
      )
      notdeleted = EntityLifecycleRecordPolicy.filterNotLogicallyDeleted(raw.records.toVector)
      decoded <-
        notdeleted.foldLeft(Consequence.success(Vector.empty[(Record, EntityId)])) { (z, record) =>
        z.flatMap { xs =>
          _decode_entity(collection, record, tc)
            .map { entity =>
            xs :+ (record -> tc.id(entity))
          }
        }
      }
    } yield decoded

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

  private def _to_datastore_limit(
    query: EntityDirectiveQuery[?]
  ): QueryLimit =
    query.limit.map(QueryLimit.Limit.apply).getOrElse(QueryLimit.Unbounded)

  private final case class VisibilityPolicy(
    poststatuses: Option[Set[String]],
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
  )(using ctx: ExecutionContext): VisibilityPolicy = {
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
    VisibilityPolicy(
      poststatuses = poststatuses,
      alivenesses = alivenesses
    )
  }

  private final case class LifecycleConstraint(
    poststatusexplicit: Boolean,
    alivenessexplicit: Boolean
  )

  private def _lifecycle_constraint(
    query: EntityDirectiveQuery[?]
  ): LifecycleConstraint = {
    val expr = EntityDirectiveQuery.whereOf(query)
    val raw = _query_condition(query)
    LifecycleConstraint(
      poststatusexplicit =
        _mentions_path(expr, Set("poststatus")) || _mentions_condition_key(raw, Set("poststatus")),
      alivenessexplicit =
        _mentions_path(expr, Set("aliveness")) || _mentions_condition_key(raw, Set("aliveness"))
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
    policy: VisibilityPolicy
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

}
