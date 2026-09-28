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
trait ActionCallRepositoryPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def repo =
    component.map(_.aggregateSpace).getOrElse(Consequence.uninitializedState.RAISE)

  protected final def aggregate_load[A](id: EntityId): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:load",
      _aggregate_calltree_attributes("load", id.collection.name) + ("entity_id" -> id.print)
    ) {
      aggregate_load_c[A](id)
    }

  // Aggregate-oriented access for application logic.
  // This returns the domain value object directly.
  protected final def aggregate_load_c[A](id: EntityId): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "load",
      aggregatename = id.collection.name,
      targetid = Some(id)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(id.collection.name, id)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolve_with_context[A](id)(using execution_context)
        }
      } yield r
    }

  protected final def aggregate_authorize_type(
    aggregateName: String,
    accessKind: String
  ): ExecUowM[Unit] =
    exec_from(
      AggregateAuthorization.authorizeType(
        aggregateName = aggregateName,
        accessKind = accessKind,
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    )

  protected final def aggregate_authorize_instance(
    aggregateName: String,
    targetId: EntityId,
    accessKind: String,
    loadRecord: EntityId => Consequence[Option[Record]]
  ): ExecUowM[Unit] =
    exec_from(
      AggregateAuthorization.authorizeInstance(
        aggregateName = aggregateName,
        targetId = targetId,
        accessKind = accessKind,
        loadRecord = loadRecord,
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    )

  protected final def aggregate_authorize_command(
    aggregateName: String,
    targetId: Option[EntityId],
    commandName: String,
    loadRecord: EntityId => Consequence[Option[Record]]
  ): ExecUowM[Unit] =
    targetId match {
      case Some(id) =>
        aggregate_authorize_instance(
          aggregateName,
          id,
          s"command:$commandName",
          loadRecord
        )
      case None =>
        aggregate_authorize_type(aggregateName, s"create:$commandName")
    }

  private def _aggregate_operation_model: ServiceOperationModel = {
    val access = _aggregate_declared_access
    getFactory[org.goldenport.cncf.component.Component.Factory]
      .flatMap(_.serviceOperationModel(action, core))
      .orElse(access.flatMap(_.operationModel).map(ServiceOperationModel.parse))
      .getOrElse(ServiceOperationModel.default)
  }

  private def _aggregate_relation_rules: Vector[EntityAccessRelation] =
    _aggregate_declared_access.flatMap(_.relation).map(EntityAccessRelation.parseList).getOrElse(
      Vector.empty
    )

  private def _aggregate_natural_conditions: Vector[EntityAbacCondition] =
    _aggregate_declared_access.flatMap(_.condition).map(EntityAbacCondition.parseList).getOrElse(
      Vector.empty
    )

  private def _aggregate_declared_operation_definition =
    component.flatMap { c =>
      val actionname = _aggregate_normalize_name(action.name)
      c.operationDefinitions.find { op =>
        val opname = _aggregate_normalize_name(op.name)
        actionname == opname || actionname.endsWith(opname)
      }
    }

  private def _aggregate_declared_access =
    _aggregate_declared_operation_definition.flatMap(_.access)

  private def _aggregate_normalize_name(p: String): String =
    Option(p).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  protected final def aggregate_load_or_throw[A](id: EntityId): A =
    aggregate_load_c[A](id).TAKE

  protected final def aggregate_load_option[A](targetId: EntityId): ExecUowM[Option[A]] =
    exec_from_calltree(
      "uow:aggregate:load-option",
      _aggregate_calltree_attributes(
        "load-option",
        targetId.collection.name
      ) + ("entity_id" -> targetId.print)
    ) {
      aggregate_load_option_c[A](targetId)
    }

  // Preserve transport/storage failures (e.g. I/O) as Failure.
  // Only "not found" is converted to Success(None).
  protected final def aggregate_load_option_c[A](
      targetId: EntityId
  ): Consequence[Option[A]] =
    _aggregate_chokepoint[Option[A]](
      operation = "load",
      aggregatename = targetId.collection.name,
      targetid = Some(targetId)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(targetId.collection.name, targetId)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolveOption[A](targetId)(using execution_context)
        }
      } yield r
    }

  protected final def aggregate_load_option_or_throw[A](targetId: EntityId): Option[A] =
    aggregate_load_option_c[A](targetId).TAKE

  protected final def aggregate_load[A](
      collectionName: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:load",
      _aggregate_calltree_attributes("load", collectionName) + ("entity_id" -> id.print)
    ) {
      aggregate_load_c[A](collectionName, id)
    }

  protected final def aggregate_load_c[A](
      collectionName: String,
    id: EntityId
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "load",
      aggregatename = collectionName,
      targetid = Some(id)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(collectionName, id)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregate[A](collectionName))
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolve_with_context(id)(using execution_context)
        }
      } yield r
    }

  protected final def begin_aggregate_edit[A](
      aggregateName: String,
    id: EntityId,
    basetoken: String,
    lockscope: AggregateEditLockScope = AggregateEditLockScope.Principal,
    metadata: Record = Record.empty
  ): ExecUowM[AggregateEditContext[A]] =
    exec_from_calltree(
      "uow:aggregate-edit:begin",
      _aggregate_edit_calltree_attributes("begin", aggregateName, Some(id))
    ) {
      begin_aggregate_edit_c[A](aggregateName, id, basetoken, lockscope, metadata)
    }

  protected final def begin_aggregate_edit_c[A](
      aggregateName: String,
    id: EntityId,
    basetoken: String,
    lockscope: AggregateEditLockScope = AggregateEditLockScope.Principal,
    metadata: Record = Record.empty
  ): Consequence[AggregateEditContext[A]] =
    aggregate_load_c[A](aggregateName, id).flatMap { aggregate =>
      component
        .map(_.aggregateEditContextSpace)
        .getOrElse(Consequence.uninitializedState.RAISE)
        .begin(
          current_instant,
          opaque_id("aggregate-edit.context"),
          aggregateName,
          id,
          basetoken,
          aggregate,
          AggregateEditOwner.current(using execution_context),
          lockscope,
          metadata
        )
    }

  protected final def get_aggregate_edit[A](
    contextid: String
  ): ExecUowM[AggregateEditContext[A]] =
    exec_from_calltree(
      "uow:aggregate-edit:get",
      _aggregate_edit_calltree_attributes("get", contextid = Some(contextid))
    ) {
      get_aggregate_edit_c[A](contextid)
    }

  protected final def get_aggregate_edit_c[A](
    contextid: String
  ): Consequence[AggregateEditContext[A]] =
    component
      .map(_.aggregateEditContextSpace)
      .getOrElse(Consequence.uninitializedState.RAISE)
      .get[A](current_instant, contextid, AggregateEditOwner.current(using execution_context))

  protected final def update_aggregate_edit[A](
    contextid: String
  )(
    action: A => Consequence[A]
  ): ExecUowM[AggregateEditContext[A]] =
    exec_from_calltree(
      "uow:aggregate-edit:update",
      _aggregate_edit_calltree_attributes("update", contextid = Some(contextid))
    ) {
      update_aggregate_edit_c[A](contextid)(action)
    }

  protected final def update_aggregate_edit_c[A](
    contextid: String
  )(
    action: A => Consequence[A]
  ): Consequence[AggregateEditContext[A]] =
    component
      .map(_.aggregateEditContextSpace)
      .getOrElse(Consequence.uninitializedState.RAISE)
      .update[A](current_instant, contextid, AggregateEditOwner.current(using execution_context))(
        action
      )

  protected final def get_aggregate_edit_view[A, B](
    contextid: String
  )(
    action: AggregateEditContext[A] => Consequence[B]
  ): ExecUowM[B] =
    exec_from_calltree(
      "uow:aggregate-edit:view",
      _aggregate_edit_calltree_attributes("view", contextid = Some(contextid))
    ) {
      get_aggregate_edit_view_c[A, B](contextid)(action)
    }

  protected final def get_aggregate_edit_view_c[A, B](
    contextid: String
  )(
    action: AggregateEditContext[A] => Consequence[B]
  ): Consequence[B] =
    component
      .map(_.aggregateEditContextSpace)
      .getOrElse(Consequence.uninitializedState.RAISE)
      .view[A, B](current_instant, contextid, AggregateEditOwner.current(using execution_context))(
        action
      )

  protected final def save_aggregate_edit[A, B](
    contextid: String,
    currentbasetoken: Option[String] = None
  )(
    action: A => Consequence[B]
  ): ExecUowM[B] =
    exec_from_calltree(
      "uow:aggregate-edit:save",
      _aggregate_edit_calltree_attributes("save", contextid = Some(contextid))
    ) {
      save_aggregate_edit_c[A, B](contextid, currentbasetoken)(action)
    }

  protected final def save_aggregate_edit_c[A, B](
    contextid: String,
    currentbasetoken: Option[String] = None
  )(
    action: A => Consequence[B]
  ): Consequence[B] =
    component
      .map(_.aggregateEditContextSpace)
      .getOrElse(Consequence.uninitializedState.RAISE)
      .save[A, B](
        current_instant,
        contextid,
        currentbasetoken,
        AggregateEditOwner.current(using execution_context)
      )(action)

  protected final def discard_aggregate_edit(
    contextid: String
  ): ExecUowM[Boolean] =
    exec_from_calltree(
      "uow:aggregate-edit:discard",
      _aggregate_edit_calltree_attributes("discard", contextid = Some(contextid))
    ) {
      discard_aggregate_edit_c(contextid)
    }

  protected final def discard_aggregate_edit_c(
    contextid: String
  ): Consequence[Boolean] =
    component
      .map(_.aggregateEditContextSpace)
      .getOrElse(Consequence.uninitializedState.RAISE)
      .discard(current_instant, contextid, AggregateEditOwner.current(using execution_context))

  private def _aggregate_authorize_load(
      aggregatename: String,
    id: EntityId
  ): Consequence[Unit] =
    _aggregate_declared_entity_collection_option_c(aggregatename).flatMap {
      case Some(_) =>
        _aggregate_entity_collection_c(aggregatename, id).flatMap { _ =>
          AggregateAuthorization.authorizeInstance(
            aggregateName = aggregatename,
            targetId = id,
            accessKind = "read",
            loadRecord = _aggregate_load_record(aggregatename),
            access = _aggregate_declared_access,
            sourceComponentName = component_name_option,
            targetComponentName = component_name_option,
            operationModel = _aggregate_operation_model,
            relationRules = _aggregate_relation_rules,
            naturalConditions = _aggregate_natural_conditions
          )(using execution_context)
        }
      case None =>
        Consequence.unit
    }

  private def _aggregate_load_record(
      aggregatename: String
  )(id: EntityId): Consequence[Option[Record]] =
    _aggregate_entity_collection_c(aggregatename, id).flatMap(
      _aggregate_load_record_from_collection(_, id)
    )

  private def _aggregate_load_record_from_collection(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    id: EntityId
  ): Consequence[Option[Record]] = {
    def _to_record_(entity: Any): Consequence[Record] =
      _aggregate_raw_record(id).map {
        case Some(record) =>
          collection.descriptor.persistent.authorizationRecord(entity, record)
        case None =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }.recover {
        case _ =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }

    collection.resolve(id).flatMap(x => _to_record_(x).map(Some(_))).recoverWith {
      case conclusion if _is_aggregate_record_not_found(conclusion) =>
        given EntityPersistent[Any] =
          collection.descriptor.persistent.asInstanceOf[EntityPersistent[Any]]
        EntityStore.standard().load[Any](id)(using
          summon[EntityPersistent[Any]],
          execution_context
        ).flatMap {
          case Some(entity) => _to_record_(entity).map(Some(_))
          case None => Consequence.success(None)
        }
      case conclusion =>
        Consequence.Failure(conclusion)
    }
  }

  private def _aggregate_raw_record(
    id: EntityId
  ): Consequence[Option[Record]] =
    for {
      cid <- execution_context.entityStoreSpace.dataStoreCollection(id)
      dsid <- execution_context.entityStoreSpace.dataStoreEntryId(id)
      ds <- execution_context.dataStoreSpace.dataStore(cid)
      r <- ds.load(cid, dsid)(using execution_context)
    } yield r

  private def _is_aggregate_record_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

  private def _aggregate_entity_collection_name(
    component: org.goldenport.cncf.component.Component,
      aggregatename: String
  ): Option[String] =
    component.aggregateDefinitions.find(_.name == aggregatename).map(_.entityName).orElse(Some(
      aggregatename
    ))

  private def _aggregate_declared_entity_collection_option_c(
      aggregatename: String
  ): Consequence[Option[org.goldenport.cncf.entity.runtime.EntityCollection[Any]]] =
    component match {
      case Some(c) =>
        _aggregate_entity_collection_name(c, aggregatename)
          .map { entityname =>
            c.entitySpace.entityCollectionsByName(entityname) match {
              case Vector() =>
                Consequence.success(None)
              case Vector(collection) =>
                Consequence.success(
                  Some(
                    collection.asInstanceOf[
                      org.goldenport.cncf.entity.runtime.EntityCollection[Any]
                    ]
                  )
                )
              case collections =>
                val candidates =
                  collections.map(_.descriptor.collectionId.print).sorted.mkString(", ")
                Consequence.stateInvalid(
                  s"$aggregatename aggregate entity collection is ambiguous: $entityname",
                  Vector(
                    Descriptor.Facet.Policy("entity.collection.identity"),
                    Descriptor.Facet.Reason("entity-collection-name-ambiguous"),
                    Descriptor.Facet.Actual(candidates)
                  )
                )
            }
          }
          .getOrElse(Consequence.success(None))
      case None =>
        Consequence.uninitializedState
    }

  private def _aggregate_entity_collection_c(
    aggregatename: String,
    id: EntityId
  ): Consequence[org.goldenport.cncf.entity.runtime.EntityCollection[Any]] =
    _aggregate_declared_entity_collection_option_c(aggregatename).flatMap {
      case Some(collection)
          if collection.descriptor.collectionId == id.collection =>
        Consequence.success(collection)
      case Some(_) =>
        Consequence.argumentInvalid(
          s"$aggregatename aggregate does not own exact collection: ${id.collection.print}"
        )
      case None =>
        Consequence.argumentInvalid(
          s"$aggregatename aggregate entity collection is not available"
        )
    }

  protected final def aggregate_load_or_throw[A](
      collectionName: String,
    id: EntityId
  ): A =
    aggregate_load_c[A](collectionName, id).TAKE

  protected final def aggregate_search[A](
      collectionName: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from_calltree(
      "uow:aggregate:search",
      _aggregate_calltree_attributes("search", collectionName)
    ) {
      aggregate_search_c[A](collectionName, q)
    }

  protected final def aggregate_search_c[A](
      collectionName: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    _aggregate_chokepoint[SearchResult[A]](
      operation = "search",
      aggregatename = collectionName
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_search(collectionName)
        }
        xs <- _aggregate_phase(ctx, DslChokepointPhase.Query) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .query_with_context[A](collectionName, q)(using execution_context)
        }
      } yield SearchResult(
            query = q,
            data = xs,
            totalCount = Some(xs.size),
            offset = q.offset,
            limit = q.limit,
            fetchedCount = xs.size
          )
        }

  private def _aggregate_authorize_search(
      aggregatename: String
  ): Consequence[Unit] =
    AggregateAuthorization.authorizeType(
      aggregateName = aggregatename,
      accessKind = "search",
      access = _aggregate_declared_access,
      sourceComponentName = component_name_option,
      targetComponentName = component_name_option,
      operationModel = _aggregate_operation_model,
      relationRules = _aggregate_relation_rules,
      naturalConditions = _aggregate_natural_conditions
    )(using execution_context)

  protected final def aggregate_search_or_throw[A](
      collectionName: String,
    q: Query[?]
  ): SearchResult[A] =
    aggregate_search_c[A](collectionName, q).TAKE

  private def _aggregate_create_record_authorized_c(
      entityname: String,
    record: Record
  ): Consequence[Unit] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        _aggregate_exact_root_record_c(collection, record).flatMap { exactrecord =>
          collection.createRecordSynced(exactrecord)(using execution_context).map { _ =>
            component.foreach(_.viewSpace.invalidate(entityname))
          }
        }
      case None => Consequence.argumentInvalid(s"$entityname entity collection is not available")
    }

  private def _aggregate_save_record_authorized_c(
    entityname: String,
    record: Record,
    expectedrevision: Option[EntityRevision],
    executionpolicy: EntityMutationExecutionPolicy
  ): Consequence[Unit] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        _aggregate_exact_root_record_c(collection, record, requireid = true).flatMap { exactrecord =>
          val saved =
            if (
              executionpolicy.concurrencyPolicy ==
                EntityConcurrencyPolicy.None
            )
              collection.saveRecordManaged(
                exactrecord,
                executionpolicy
              )(using execution_context)
            else
              collection.descriptor.revisionBinding match {
                case Some(binding)
                    if binding.representation ==
                      EntityRevisionRepresentation.Embedded =>
                  collection.saveRecordVersioned(
                    exactrecord,
                    expectedrevision,
                    executionpolicy
                  )(using execution_context).map(_ => ())
                case Some(binding)
                    if binding.representation ==
                      EntityRevisionRepresentation.Detached =>
                  collection.saveRecordDetached(
                    exactrecord,
                    expectedrevision,
                    executionpolicy
                  )(using execution_context).map(_ => ())
                case Some(_) =>
                  Consequence.operationInvalid(
                    "aggregate-revision-representation",
                    "unsupported aggregate root revision representation"
                  )
                case None =>
                  Consequence.operationInvalid(
                    "aggregate-revision-representation",
                    "aggregate root has no managed revision representation"
                  )
              }
          saved.map { _ =>
            component.foreach(_.viewSpace.invalidate(entityname))
            ()
          }.recoverWith { conclusion =>
            if (
              ConclusionDiagnostics.classify(conclusion).reason
                .contains("committed-entity-projection-failure")
            )
              component.foreach(_.viewSpace.invalidate(entityname))
            Consequence.Failure(conclusion)
          }
        }
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private final case class AggregateRootState[A](
    entity: A,
    revision: Option[EntityRevision]
  )

  private def _aggregate_root_state_c(
    entityname: String,
    targetid: EntityId,
    revisionrequired: Boolean
  ): Consequence[AggregateRootState[Any]] =
    _aggregate_entity_collection_c(entityname, targetid).flatMap { collection =>
        if (!revisionrequired)
          execution_context.entityStoreSpace
            .load(
              UnitOfWorkOp.EntityStoreLoad(
                targetid,
                collection.descriptor.persistent
              )
            )(using execution_context)
            .flatMap(entity =>
              Consequence.successOrEntityNotFound(entity)(targetid)
            )
            .map(entity => AggregateRootState(entity, None))
        else
          collection.descriptor.revisionBinding match {
          case Some(binding)
              if binding.representation ==
                EntityRevisionRepresentation.Embedded =>
            execution_context.entityStoreSpace
              .loadSnapshot(
                targetid,
                collection.descriptor.persistent
              )(using execution_context)
              .flatMap(snapshot =>
                Consequence.successOrEntityNotFound(snapshot)(targetid)
              )
              .map(snapshot =>
                AggregateRootState(
                  snapshot.entity,
                  Some(snapshot.revision)
                )
              )
          case Some(binding)
              if binding.representation ==
                EntityRevisionRepresentation.Detached =>
            execution_context.entityStoreSpace
              .loadDetached(
                targetid,
                collection.descriptor.persistent
              )(using execution_context)
              .flatMap(carrier =>
                Consequence.successOrEntityNotFound(carrier)(targetid)
              )
              .map(carrier =>
                AggregateRootState(
                  carrier.entity,
                  Some(carrier.revision)
                )
              )
          case Some(_) =>
            Consequence.operationInvalid(
              "aggregate-revision-representation",
              "unsupported aggregate root revision representation"
            )
          case None =>
            Consequence.operationInvalid(
              "aggregate-revision-representation",
              "aggregate root has no managed revision representation"
            )
        }
    }

  private def _aggregate_mutation_policy(
    entityname: String,
    observedrevision: Option[EntityRevision] = None
  ): Consequence[EntityMutationExecutionPolicy] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        val concurrency = collection.descriptor.plan.concurrencyPolicy
        observedrevision match {
          case Some(_) if concurrency != EntityConcurrencyPolicy.Optimistic =>
            Consequence.operationInvalid(
              "aggregate-observed-revision",
              "observed Aggregate mutation requires Optimistic concurrency policy"
            )
          case Some(revision) =>
            EntityMutationExecutionPolicy(
              concurrencyPolicy = concurrency,
              preconditionPolicy =
                RevisionPreconditionPolicy.ObservedRequired,
              observedRevision = Some(revision)
            ).validateC
          case None =>
            EntityMutationExecutionPolicy(
              concurrencyPolicy = concurrency
            ).validateC
        }
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private def _aggregate_command_target_c[
      A <: org.goldenport.record.RecordPresentable
  ](
    entityname: String,
    targetid: EntityId,
    revisionrequired: Boolean
  ): Consequence[(A, AggregateRootState[Any])] =
    for {
      rootstate <-
        _aggregate_root_state_c(
          entityname,
          targetid,
          revisionrequired
        )
      aggregate <- component
        .map(_.aggregateSpace)
        .getOrElse(Consequence.uninitializedState.RAISE)
        .resolve_with_context[A](targetid)(using execution_context)
      _ <- _validate_aggregate_root_state(
        entityname,
        targetid,
        aggregate,
        rootstate
      )
    } yield aggregate -> rootstate

  private def _validate_aggregate_root_state[
      A <: org.goldenport.record.RecordPresentable
  ](
      entityname: String,
      targetid: EntityId,
      aggregate: A,
      rootstate: AggregateRootState[Any]
  ): Consequence[Unit] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        for {
          rootrecord <- _aggregate_exact_root_record_c(
            collection,
            collection.descriptor.persistent.toRecord(rootstate.entity),
            requireid = true
          ).map(SimpleEntityStorageShapePolicy.withoutManagedFields)
          aggregaterecord <- _aggregate_exact_root_record_c(
            collection,
            aggregate.toRecord(),
            requireid = true
          ).map(SimpleEntityStorageShapePolicy.withoutManagedFields)
          _ <- {
            val aggregatemap = aggregaterecord.asMap
            if (
              rootrecord.asMap.forall { case (key, value) =>
                aggregatemap.get(key).contains(value)
              }
            )
              Consequence.unit
            else
              Consequence.operationConflict(
                "aggregate-command",
                Vector(
                  Descriptor.Facet.Reason("aggregate-root-snapshot-mismatch"),
                  Descriptor.Facet.Policy("entity.optimistic-concurrency"),
                  Descriptor.Facet.Actual(targetid.print)
                )
              )
          }
        } yield ()
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private def _aggregate_exact_root_record_c(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    record: Record,
    requireid: Boolean = false
  ): Consequence[Record] =
    record.getAny("id") match {
      case None if requireid =>
        Consequence.argumentInvalid("aggregate record ID is required for update persistence")
      case None =>
        Consequence.success(record)
      case Some(id: EntityId) =>
        _aggregate_exact_root_record_c(collection, record, id)
      case Some(value: String) =>
        EntityId.parse(value).flatMap(_aggregate_exact_root_record_c(collection, record, _))
      case Some(value) =>
        Consequence.argumentInvalid(
          s"aggregate record ID must be an EntityId or canonical String: ${value.getClass.getName}"
        )
    }

  private def _aggregate_exact_root_record_c(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    record: Record,
    id: EntityId
  ): Consequence[Record] =
    if (id.collection == collection.descriptor.collectionId)
      Consequence.success(record.upsertSingle("id", id))
    else
      Consequence.argumentInvalid(
        s"aggregate record ID collection mismatch: ${id.collection.print}"
      )

  protected final def aggregate_create[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    commandName: String,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:create",
      _aggregate_calltree_attributes("create", entityName) + ("command" -> commandName)
    ) {
      aggregate_create_c(entityName, commandName, action)
    }

  protected final def aggregate_create_c[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    commandName: String,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "create",
      aggregatename = entityName,
      commandname = Some(commandName)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_create(entityName, commandName)
        }
        aggregate <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          action
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          _aggregate_create_record_authorized_c(entityName, aggregate.toRecord())
        }
      } yield aggregate
    }

  protected final def aggregate_update[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:update",
      _aggregate_calltree_attributes("update", entityName) ++ Map(
        "command"   -> commandName,
        "entity_id" -> targetId.print
      )
    ) {
      aggregate_update_c(entityName, targetId, commandName, action)
    }

  protected final def aggregate_update_c[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_update_c(
      entityName,
      targetId,
      commandName,
      None,
      action
    )

  protected final def aggregate_update_observed[
    A <: org.goldenport.record.RecordPresentable
  ](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    observedRevision: EntityRevision,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:update-observed",
      _aggregate_calltree_attributes("update-observed", entityName) ++ Map(
        "command"   -> commandName,
        "entity_id" -> targetId.print
      )
    ) {
      aggregate_update_observed_c(
        entityName,
        targetId,
        commandName,
        observedRevision,
        action
      )
    }

  protected final def aggregate_update_observed_c[
    A <: org.goldenport.record.RecordPresentable
  ](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    observedRevision: EntityRevision,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_update_c(
      entityName,
      targetId,
      commandName,
      Some(observedRevision),
      action
    )

  private def _aggregate_update_c[
    A <: org.goldenport.record.RecordPresentable
  ](
    entityname: String,
    targetid: EntityId,
    commandname: String,
    observedrevision: Option[EntityRevision],
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "update",
      aggregatename = entityname,
      targetid = Some(targetid),
      commandname = Some(commandname)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_update(entityname, targetid, commandname)
        }
        policy <- _aggregate_mutation_policy(
          entityname,
          observedrevision
        )
        expectedrevision <-
          if (
            policy.concurrencyPolicy ==
              EntityConcurrencyPolicy.Optimistic &&
            observedrevision.isEmpty
          )
            _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
              _aggregate_root_state_c(
                entityname,
                targetid,
                revisionrequired = true
              ).flatMap { state =>
                state.revision
                  .map(Consequence.success)
                  .getOrElse(
                    Consequence.operationInvalid(
                      "aggregate-revision",
                      "managed aggregate revision is not available"
                    )
                  )
              }
            }.map(Some(_))
          else
            Consequence.success(observedrevision)
        aggregate <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          action
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          _aggregate_save_record_authorized_c(
            entityname,
            aggregate.toRecord(),
            expectedrevision,
            policy
          )
        }
      } yield aggregate
    }

  protected final def aggregate_command[A <: org.goldenport.record.RecordPresentable](
    aggregateName: String,
    targetId: EntityId,
    commandName: String
  )(
    command: A => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:command",
      _aggregate_calltree_attributes("command", aggregateName) ++ Map(
        "command"   -> commandName,
        "entity_id" -> targetId.print
      )
    ) {
      aggregate_command_c(aggregateName, targetId, commandName)(command)
    }

  protected final def aggregate_command_c[A <: org.goldenport.record.RecordPresentable](
    aggregateName: String,
    targetId: EntityId,
    commandName: String
  )(
    command: A => Consequence[A]
  ): Consequence[A] =
    _aggregate_command_c(
      aggregateName,
      targetId,
      commandName,
      None
    )(command)

  protected final def aggregate_command_observed[
    A <: org.goldenport.record.RecordPresentable
  ](
    aggregateName: String,
    targetId: EntityId,
    commandName: String,
    observedRevision: EntityRevision
  )(
    command: A => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:command-observed",
      _aggregate_calltree_attributes("command-observed", aggregateName) ++ Map(
        "command"   -> commandName,
        "entity_id" -> targetId.print
      )
    ) {
      aggregate_command_observed_c(
        aggregateName,
        targetId,
        commandName,
        observedRevision
      )(command)
    }

  protected final def aggregate_command_observed_c[
    A <: org.goldenport.record.RecordPresentable
  ](
    aggregateName: String,
    targetId: EntityId,
    commandName: String,
    observedRevision: EntityRevision
  )(
    command: A => Consequence[A]
  ): Consequence[A] =
    _aggregate_command_c(
      aggregateName,
      targetId,
      commandName,
      Some(observedRevision)
    )(command)

  private def _aggregate_command_c[
    A <: org.goldenport.record.RecordPresentable
  ](
    aggregatename: String,
    targetid: EntityId,
    commandname: String,
    observedrevision: Option[EntityRevision]
  )(
    command: A => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "command",
      aggregatename = aggregatename,
      targetid = Some(targetid),
      commandname = Some(commandname)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_update(aggregatename, targetid, commandname)
        }
        policy <- _aggregate_mutation_policy(
          aggregatename,
          observedrevision
        )
        resolved <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          _aggregate_command_target_c[A](
            aggregatename,
            targetid,
            policy.concurrencyPolicy ==
              EntityConcurrencyPolicy.Optimistic
          )
        }
        (aggregate, rootstate) = resolved
        updated <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          command(aggregate)
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          if (aggregate.toRecord() == updated.toRecord())
            Consequence.unit
          else
            _aggregate_save_record_authorized_c(
              aggregatename,
              updated.toRecord(),
              observedrevision.orElse(rootstate.revision),
              policy
            )
        }
      } yield updated
    }

  private def _aggregate_chokepoint[A](
    operation: String,
      aggregatename: String,
      targetid: Option[EntityId] = None,
      commandname: Option[String] = None
  )(
    body: DslChokepointContext => Consequence[A]
  ): Consequence[A] = {
    given ExecutionContext = execution_context
    val ctx = DslChokepointContext(
      domain = "aggregate",
      operation = operation,
      componentName = component_name_option,
      resourceName = Some(aggregatename),
      targetId = targetid.map(_.toString),
      commandName = commandname
    )
    DslChokepointRunner.run(ctx)(body(ctx))
  }

  private def _aggregate_phase[A](
    context: DslChokepointContext,
    phase: DslChokepointPhase
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    given ExecutionContext = execution_context
    DslChokepointRunner.phase(context, phase)(body)
  }

  private def _aggregate_calltree_attributes(
    operation: String,
      aggregatename: String
  ): Map[String, String] =
    Map(
      "dsl" -> "uow",
      "operation" -> operation,
      "aggregate" -> aggregatename
    )

  private def _aggregate_edit_calltree_attributes(
    operation: String,
    aggregatename: String = "",
    targetid: Option[EntityId] = None,
    contextid: Option[String] = None
  ): Map[String, String] =
    Map(
      "dsl" -> "uow",
      "operation" -> operation,
      "aggregate_edit_context" -> contextid.getOrElse(""),
      "aggregate" -> aggregatename
    ) ++ targetid.map(id => "entity_id" -> id.print).toMap

  private def _aggregate_authorize_create(
      aggregatename: String,
      commandname: String
  ): Consequence[Unit] =
    AggregateAuthorization.authorizeType(
      aggregateName = aggregatename,
      accessKind = s"create:$commandname",
      access = _aggregate_declared_access,
      sourceComponentName = component_name_option,
      targetComponentName = component_name_option,
      operationModel = _aggregate_operation_model,
      relationRules = _aggregate_relation_rules,
      naturalConditions = _aggregate_natural_conditions
    )(using execution_context)

  private def _aggregate_authorize_update(
      aggregatename: String,
      targetid: EntityId,
      commandname: String
  ): Consequence[Unit] =
    _aggregate_entity_collection_c(aggregatename, targetid).flatMap { _ =>
      AggregateAuthorization.authorizeInstance(
        aggregateName = aggregatename,
        targetId = targetid,
        accessKind = s"command:$commandname",
        loadRecord = _aggregate_load_record(aggregatename),
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    }
}
