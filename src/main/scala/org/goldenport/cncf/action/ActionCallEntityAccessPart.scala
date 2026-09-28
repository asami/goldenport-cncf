package org.goldenport.cncf.action

import java.nio.file.Path
import java.nio.charset.Charset
import java.time.{Clock, Duration, Instant, ZonedDateTime}
import cats.free.Free
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.observation.Cause
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.http.HttpResponse
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionSchedulerMode,
  GlobalRuntimeContext,
  ScopeContext
}
import org.goldenport.cncf.resource.{
  ResourceContent,
  ResourceReference,
  ResourceTreeLimits,
  ResourceTreeQuery,
  ResourceTreeQueryResult,
  ResourceTreeReference,
  ResourceTreeSnapshot
}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkAuthorization}
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.embedded.{EmbeddedDataStore, EmbeddedStatement, EmbeddedUpdateResult}
import org.goldenport.cncf.security.{
  AggregateAuthorization,
  EntityAbacCondition,
  EntityAccessMode,
  EntityAccessRelation,
  EntityApplicationDomain,
  EntityAuthorizationProfile,
  EntityOperationKind,
  EntityUsageKind,
  OperationAccessPolicy,
  ServiceOperationModel
}
import org.goldenport.cncf.Program
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.datastore.{
  ComponentDataStore,
  DataStore,
  DataStoreComponentOwner
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityConditionalTransition,
  EntityMutationExecutionPolicy,
  EntityConditionalTransitionResult,
  RevisionPreconditionPolicy,
  EntitySuccessorIntent
}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.entity.EntityRecordSnapshot
import org.goldenport.cncf.entity.EntityRevisionCarrier
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.goldenport.cncf.entity.EntitySnapshot
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.entity.EntitySearchScope
import org.goldenport.cncf.entity.EntityIdentityScope
import org.goldenport.cncf.entity.EntityVisibilityScope
import org.goldenport.cncf.entity.EntityCreateOptions
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy
import org.goldenport.cncf.blob.{
  ContentReferenceAttachResult,
  ContentReferenceContent,
  ContentReferenceNormalizeResult,
  ContentRenderResult,
  InlineImageAttachResult,
  InlineImageContent,
  InlineImageNormalizeResult,
  InlineImageOccurrence
}
import org.goldenport.value.{ContentAttributes, ContentReferenceOccurrence}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.aggregate.{
  AggregateEditContext,
  AggregateEditLockScope,
  AggregateEditOwner
}
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.action.AggregateBehavior
import org.goldenport.cncf.information.InformationSpace
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationConflict,
  InformationFieldEvent,
  InformationIdentityBinding,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{KnowledgeFrameId, KnowledgeWorkingSetSnapshot}
import org.goldenport.cncf.observability.{
  CallTreeValueSummary,
  ConclusionDiagnostics,
  DslChokepointContext,
  DslChokepointPhase,
  DslChokepointRunner
}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.Configuration
import org.goldenport.configuration.ConfigurationTrace
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.configuration.source.file.ConfigTextDecoder
import org.goldenport.cncf.config.RuntimeFileConfigLoader
import org.goldenport.cncf.config.{
  ComponentConfigurationAccess,
  ComponentConfigurationKey,
  ComponentConfigurationResolution,
  ComponentConfigurationSources
}
import org.goldenport.cncf.processexecution.{
  ProcessExecutionAdmission,
  ProcessExecutionRequest,
  ProcessExecutionResult,
  ResolvedProcessExecution
}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[action] trait ActionCallEntityAccessPart extends ActionCallFeaturePart { self: ActionCallEntityStorePart & ActionCall.Core.Holder =>
  protected final def entity_load_option[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    _emit_entity_access(
      "entity.load.start",
      _entity_load_attributes(effectiveid, "unknown", "start")
    )
    _effective_entity_persistent_c(effectiveid.collection, tc) match {
      case Consequence.Failure(conclusion) =>
        exec_from(Consequence.Failure(conclusion))
      case Consequence.Success(effectivetc) if !_working_set_enabled =>
        _emit_entity_access(
          "entity.load.bypass.entity-space",
          _entity_load_attributes(effectiveid, "entity-space", "bypass")
        )
        val op = UnitOfWorkOp.EntityStoreLoad(
          effectiveid,
          effectivetc,
          _entity_uow_authorization(
            Some(effectiveid.collection.name),
            Some(effectiveid),
            "read"
          ),
          _declared_visibility_scope,
          useEntitySpace = false
        )
        ConsequenceT.liftF(Free.liftF(op))
      case Consequence.Success(effectivetc)
          if !_uses_runtime_entity_persistent(
            effectiveid.collection,
            effectivetc
          ) =>
        _emit_entity_access(
          "entity.load.bypass.entity-space",
          _entity_load_attributes(effectiveid, "entity-space", "bypass")
        )
        _emit_entity_access(
          "entity.load.fallback.entity-store",
          _entity_load_attributes(effectiveid, "entity-store", "fallback")
        )
        val op = UnitOfWorkOp.EntityStoreLoad(
          effectiveid,
          effectivetc,
          _entity_uow_authorization(
            Some(effectiveid.collection.name),
            Some(effectiveid),
            "read"
          ),
          _declared_visibility_scope,
          useEntitySpace = false
        )
        ConsequenceT.liftF(Free.liftF(op))
      case Consequence.Success(effectivetc) =>
        component.flatMap(_.entitySpace.entityOption(effectiveid.collection).map(
          _.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[T]]
        )) match {
          case Some(collection) =>
            _emit_entity_access(
              "entity.load.try.entity-space",
              _entity_load_attributes(effectiveid, "entity-space", "try")
            )
            collection.resolve(effectiveid) match {
              case Consequence.Success(entity) =>
                _emit_entity_access(
                  "entity.load.hit.entity-space",
                  _entity_load_attributes(effectiveid, "entity-space", "hit")
                )
                exec_from(
                  _authorize_entity_load_hit(
                    effectiveid,
                    entity,
                    effectivetc
                  )
                )
              case Consequence.Failure(conclusion)
                  if _is_entity_not_found(conclusion) =>
                _emit_entity_access(
                  "entity.load.fallback.entity-store",
                  _entity_load_attributes(
                    effectiveid,
                    "entity-store",
                    "fallback"
                  )
                )
                val op = UnitOfWorkOp.EntityStoreLoad(
                  effectiveid,
                  effectivetc,
                  _entity_uow_authorization(
                    Some(effectiveid.collection.name),
                    Some(effectiveid),
                    "read"
                  ),
                  _declared_visibility_scope,
                  useEntitySpace = true
                )
                ConsequenceT.liftF(Free.liftF(op))
              case Consequence.Failure(conclusion) =>
                exec_from(Consequence.Failure(conclusion))
            }
          case None =>
            _emit_entity_access(
              "entity.load.fallback.entity-store",
              _entity_load_attributes(effectiveid, "entity-store", "fallback")
            )
            val op = UnitOfWorkOp.EntityStoreLoad(
              effectiveid,
              effectivetc,
              _entity_uow_authorization(
                Some(effectiveid.collection.name),
                Some(effectiveid),
                "read"
              ),
              _declared_visibility_scope
            )
            ConsequenceT.liftF(Free.liftF(op))
        }
    }
  }

  private def _authorize_entity_load_hit[T](
    id: EntityId,
    entity: T,
    tc: EntityPersistent[T]
  ): Consequence[Option[T]] = {
    given ExecutionContext = execution_context
    if (
      !org.goldenport.cncf.entity.EntityAccessScopePolicy.visibilityRecordVisible(
        id.collection,
        tc.toRecord(entity),
        _declared_visibility_scope
      )
    )
      return Consequence.success(None)
    _entity_uow_authorization(Some(id.collection.name), Some(id), "read") match {
      case Some(authorization) =>
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          authorization,
          _ =>
            _entity_store_record(id).map {
            case Some(record) => Some(tc.authorizationRecord(entity, record))
            case None => Some(tc.authorizationRecord(entity))
          }
        ).map(_ => Some(entity))
      case None =>
        Consequence.success(Some(entity))
    }
  }

  private def _entity_store_record(
    id: EntityId
  ): Consequence[Option[Record]] = {
    given ExecutionContext = execution_context
    for {
      cid <- execution_context.entityStoreSpace.dataStoreCollection(id)
      dsid <- execution_context.entityStoreSpace.dataStoreEntryId(id)
      ds <- execution_context.dataStoreSpace.dataStore(cid)
      rec <- ds.load(cid, dsid)
    } yield rec
  }

  protected final def entity_load[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[T] =
    entity_load_option(id).flatMap { x =>
      val r = Consequence.successOrEntityNotFound(x)(id)
      exec_from(r)
    }

  protected final def entity_load_snapshot[T](
      id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    exec_from(
      _effective_entity_persistent_c(effectiveid.collection, tc)
    ).flatMap { effectivetc =>
      val op = UnitOfWorkOp.EntityStoreLoadSnapshot(
        effectiveid,
        effectivetc,
        _entity_uow_authorization(
          Some(effectiveid.collection.name),
          Some(effectiveid),
          "read"
        )
      )
      val loaded: ExecUowM[Option[EntitySnapshot[T]]] =
        ConsequenceT.liftF(
          Free.liftF[UnitOfWorkOp, Option[EntitySnapshot[T]]](op)
        )
      loaded.flatMap { snapshot =>
        exec_from(Consequence.successOrEntityNotFound(snapshot)(effectiveid))
      }
    }
  }

  /** Loads an authoritative server-owned Entity snapshot through the UnitOfWork boundary.
   *
   * This is the ServiceInternal counterpart of `entity_load_snapshot`. Component workflows use
   * the returned Entity revision to construct protected conditional transitions without
   * imposing caller-owned Entity permissions or bypassing EntityStore authorization.
   */
  protected final def entity_load_snapshot_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    exec_from(
      _component_entity_owner(Vector(effectiveid.collection)).map(_ => ())
    ).flatMap { _ =>
      exec_from(
        _effective_entity_persistent_c(effectiveid.collection, tc)
      ).flatMap { effectivetc =>
        val authorization =
          _entity_uow_authorization(
            Some(effectiveid.collection.name),
            Some(effectiveid),
            "read"
          ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
        val operation = UnitOfWorkOp.EntityStoreLoadSnapshot(
          effectiveid,
          effectivetc,
          authorization
        )
        val loaded: ExecUowM[Option[EntitySnapshot[T]]] =
          ConsequenceT.liftF(
            Free.liftF[UnitOfWorkOp, Option[EntitySnapshot[T]]](operation)
          )
        loaded.flatMap { snapshot =>
          exec_from(
            Consequence.successOrEntityNotFound(snapshot)(effectiveid)
          )
        }
      }
    }
  }

  protected final def entity_load_detached[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_load_detached(id, serviceinternal = false)

  protected final def entity_load_detached_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_load_detached(id, serviceinternal = true)

  private def _entity_load_detached[T](
    id: EntityId,
    serviceinternal: Boolean
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val ownership =
      if (serviceinternal)
        _component_entity_owner(Vector(effectiveid.collection)).map(_ => ())
      else
        Consequence.unit
    exec_from(ownership).flatMap { _ =>
      exec_from(
        _effective_entity_persistent_c(effectiveid.collection, tc)
      ).flatMap { effectivetc =>
        val authorization =
          _entity_uow_authorization(
            Some(effectiveid.collection.name),
            Some(effectiveid),
            "read"
          ).map { value =>
            if (serviceinternal)
              value.copy(accessMode = EntityAccessMode.ServiceInternal)
            else
              value
          }
        val operation = UnitOfWorkOp.EntityStoreLoadDetached(
          effectiveid,
          effectivetc,
          authorization
        )
        val loaded: ExecUowM[Option[EntityRevisionCarrier[T]]] =
          ConsequenceT.liftF(
            Free.liftF[
              UnitOfWorkOp,
              Option[EntityRevisionCarrier[T]]
            ](operation)
          )
        loaded.flatMap { carrier =>
          exec_from(
            Consequence.successOrEntityNotFound(carrier)(effectiveid)
          )
        }
      }
    }
  }

  protected final def entity_load_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[T] =
    entity_load_option_internal(id).flatMap { x =>
      exec_from(Consequence.successOrEntityNotFound(x)(id))
    }

  protected final def entity_load_option_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    exec_from(
      _effective_entity_persistent_c(effectiveid.collection, tc)
    ).flatMap { effectivetc =>
      ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreLoadDirect(
        effectiveid,
        effectivetc
      )))
    }
  }

  protected final def entity_search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    ensure_component_application_datastore()
    val effectivequery = _with_declared_visibility(query)
    _emit_entity_access(
      "entity.search.start",
      _entity_search_attributes(effectivequery, "unknown", "start")
    )
    val effectivetc = _effective_entity_persistent(effectivequery.collection, tc)
    if (effectivequery.scope == EntitySearchScope.Store)
      return _entity_store_search_direct(effectivequery, effectivetc)
    if (!_working_set_enabled) {
      _emit_entity_access(
        "entity.search.bypass.entity-space",
        _entity_search_attributes(effectivequery, "entity-space", "bypass")
      )
      return _entity_store_search_direct(effectivequery, effectivetc)
    }
    component.flatMap(_.entitySpace.entityOption(effectivequery.collection).map(
      _.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[T]]
    )) match {
      case Some(collection) =>
        if (_bypass_entity_space_resident_search) {
          _emit_entity_access(
            "entity.search.bypass.entity-space",
            _entity_search_attributes(effectivequery, "entity-space", "bypass")
          )
          _entity_store_search_direct(effectivequery, tc)
        } else {
          _emit_entity_access(
            "entity.search.try.entity-space",
            _entity_search_attributes(effectivequery, "entity-space", "try")
          )
          if (collection.shouldFallbackToStoreForWorkingSet(effectivequery)) {
            val state = collection.workingSetStatus.state
            _emit_entity_access(
              "entity.search.fallback.entity-store",
              _entity_search_attributes(effectivequery, "entity-store", "fallback")
            )
            if (collection.workingSetStatus.isInitializing)
              _emit_entity_access(
                "entity.search.fallback.working-set-loading",
                _entity_search_working_set_loading_attributes(effectivequery, state)
              )
            return _entity_store_search_direct(effectivequery, effectivetc)
          }
          val hasworkingsetpolicy =
            collection.descriptor.plan.workingSetPolicy match {
              case Some(org.goldenport.cncf.entity.runtime.WorkingSetPolicy.Disabled) | None =>
                false
              case Some(_) => true
            }
          val hasresident =
            effectivequery.scope match {
              case EntitySearchScope.WorkingSet =>
                hasworkingsetpolicy && collection.workingSetSearchAvailable
              case EntitySearchScope.Store =>
                collection.storage.storeRealm.values.nonEmpty ||
                  collection.storage.memoryRealm.exists(_.values.nonEmpty)
            }
          if (hasresident) {
            _emit_entity_access(
              "entity.search.hit.entity-space",
              _entity_search_attributes(effectivequery, "entity-space", "hit")
            )
            val authorization =
              _entity_uow_authorization(Some(effectivequery.collection.name), None, "search/list")
            exec_from(
              collection.search(effectivequery)(using execution_context).flatMap { result =>
                authorization
                  .map(OperationAccessPolicy.filterVisibleSearchResult(_, result, tc)(using
                  execution_context))
                  .getOrElse(Consequence.success(result))
              }
            )
          } else {
            _emit_entity_access(
              "entity.search.fallback.entity-store",
              _entity_search_attributes(effectivequery, "entity-store", "fallback")
            )
            _entity_store_search_direct(effectivequery, effectivetc)
          }
        }
      case None =>
        _emit_entity_access(
          "entity.search.fallback.entity-store",
          _entity_search_attributes(effectivequery, "entity-store", "fallback")
        )
        _entity_store_search_direct(effectivequery, effectivetc)
    }
  }

  protected final def entity_search_internal[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(query.collection, tc)
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreSearchInternal(query, effectivetc)))
  }

  protected final def entity_unique_value_exists[T](
    collection: EntityCollectionId,
    fieldName: String,
    value: String,
    excludeId: Option[EntityId] = None,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext,
    includeEntityIdEntropy: Boolean = false
  )(using tc: EntityPersistent[T]): ExecUowM[Boolean] = {
    ensure_component_application_datastore()
    ConsequenceT.liftF(
      Free.liftF(
        UnitOfWorkOp.EntityStoreUniqueValueExists(
          collection,
          fieldName,
          value,
          excludeId,
          scope,
          includeEntityIdEntropy,
          tc
        )
      )
    )
  }

  protected final def entity_resolve_identity[T](
    collection: EntityCollectionId,
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean = true,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext
  )(using tc: EntityPersistent[T]): ExecUowM[Option[EntityId]] = {
    ensure_component_application_datastore()
    ConsequenceT.liftF(
      Free.liftF(
        UnitOfWorkOp.EntityStoreResolveIdentity(
          collection,
          value,
          fieldNames,
          includeEntityIdEntropy,
          scope,
          tc
        )
      )
    )
  }

  private def _entity_store_search[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ): ExecUowM[SearchResult[T]] = {
    ensure_component_application_datastore()
    val op = UnitOfWorkOp.EntityStoreSearch(
      _with_declared_visibility(query),
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  private def _entity_store_load_direct[T](
    id: EntityId,
    tc: EntityPersistent[T]
  ): ExecUowM[Option[T]] = {
    ensure_component_application_datastore()
    _emit_entity_access(
      "entity.load.bypass.entity-space",
      _entity_load_attributes(id, "entity-space", "bypass")
    )
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  private def _entity_store_search_direct[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ): ExecUowM[SearchResult[T]] = {
    ensure_component_application_datastore()
    val op = UnitOfWorkOp.EntityStoreSearchDirect(
      _with_declared_visibility(query),
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    collection: EntityCollectionId,
    query: Query[?]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] =
    entity_search[T](EntityQuery(collection, query))

  protected final def entity_load_option_c[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Option[T]] =
    _effective_entity_persistent_c(id.collection, tc).flatMap { effectivetc =>
      exec_c(UnitOfWorkOp.EntityStoreLoadDirect(id, effectivetc))
    }

  protected final def entity_load_option_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Option[T] =
    entity_load_option_c(id).TAKE

  private def _effective_entity_persistent_c[T](
    collectionid: EntityCollectionId,
    fallback: EntityPersistent[T]
  ): Consequence[EntityPersistent[T]] =
    Consequence.success(fallback)

  private def _uses_runtime_entity_persistent[T](
    collectionid: EntityCollectionId,
    persistent: EntityPersistent[T]
  ): Boolean =
    component
      .flatMap(_.entitySpace.entityOption(collectionid))
      .exists(collection =>
        collection.descriptor.persistent.asInstanceOf[AnyRef] eq
          persistent.asInstanceOf[AnyRef]
      )

  private[action] def _effective_entity_persistent[T](
    collectionid: EntityCollectionId,
    fallback: EntityPersistent[T]
  ): EntityPersistent[T] =
    component
      .flatMap(_.entitySpace.entityOption(collectionid))
      .map(_.descriptor.persistent.asInstanceOf[EntityPersistent[T]])
      .getOrElse(fallback)

  protected final def entity_load_c[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[T] =
    entity_load_option_c(id).flatMap(x => Consequence.successOrEntityNotFound(x)(id))

  protected final def entity_load_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): T =
    entity_load_c(id).TAKE

  protected final def entity_search_c[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearch(
      query,
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    exec_c(op)
  }

  protected final def entity_search_or_throw[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): SearchResult[T] = {
    val op = UnitOfWorkOp.EntityStoreSearch(
      query,
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    exec_or_throw(op)
  }

}
