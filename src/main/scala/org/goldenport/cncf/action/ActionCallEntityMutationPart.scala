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
private[action] trait ActionCallEntityMutationPart extends ActionCallFeaturePart { self: ActionCallEntityStorePart & ActionCall.Core.Holder =>
  protected final def entity_create[T](
    entity: T
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    ensure_component_application_datastore()
    val op = UnitOfWorkOp.EntityStoreCreate(
      entity,
      tc,
      _entity_create_options(Some(tc.collection(entity).name)),
      _entity_uow_authorization(Some(tc.collection(entity).name), None, "create")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  /** Creates a server-owned record through the Entity/UnitOfWork boundary. */
  protected final def entity_create_internal[T](
    entity: T
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    ensure_component_application_datastore()
    val authorization = _entity_uow_authorization(Some(tc.collection(entity).name), None, "create")
      .map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = UnitOfWorkOp.EntityStoreCreate(
      entity,
      tc,
      _entity_create_options(Some(tc.collection(entity).name)),
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  /** Creates a stable-id Entity or applies its replacement through bounded OCC
    * retry. This is not the legacy unversioned overwrite operation.
    */
  protected final def entity_upsert[T](
    entity: T,
    policy: org.goldenport.cncf.entity.EntityUpsertPolicy =
      org.goldenport.cncf.entity.EntityUpsertPolicy.default
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    ensure_component_application_datastore()
    tc.id(entity) match {
      case Some(sourceid) =>
        val id = sourceid
        val op = UnitOfWorkOp.EntityStoreUpsert(
          entity,
          id,
          policy,
          tc,
          _entity_create_options(Some(id.collection.name)),
          _entity_uow_authorization(Some(id.collection.name), None, "create"),
          _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
        )
        ConsequenceT.liftF(Free.liftF(op))
      case None =>
        exec_from(Consequence.argumentInvalid("entity_upsert requires a stable entity id"))
    }
  }

  /** Service-internal stable-identity conditional upsert. */
  protected final def entity_upsert_internal[T](
    entity: T,
    policy: org.goldenport.cncf.entity.EntityUpsertPolicy =
      org.goldenport.cncf.entity.EntityUpsertPolicy.default
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    ensure_component_application_datastore()
    tc.id(entity) match {
      case Some(sourceid) =>
        val id = sourceid
        val createauthorization =
          _entity_uow_authorization(Some(id.collection.name), None, "create")
            .map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
        val updateauthorization =
          _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
            .map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
        val op = UnitOfWorkOp.EntityStoreUpsert(
          entity,
          id,
          policy,
          tc,
          _entity_create_options(Some(id.collection.name)),
          createauthorization,
          updateauthorization
        )
        ConsequenceT.liftF(Free.liftF(op))
      case None =>
        exec_from(
          Consequence.argumentInvalid("entity_upsert_internal requires a stable entity id")
        )
    }
  }

  /** Claims a stable Entity identity without overwriting an existing record. The returned branch
    * tells the caller whether it owns expensive work or must join/reuse the already persisted
    * entity.
   */
  protected final def entity_claim_or_load[C, P](
    entity: C
  )(using
      create: EntityPersistentCreate[C],
      persisted: EntityPersistent[P]
  ): ExecUowM[EntityStore.EntityClaimResult[C, P]] = {
    ensure_component_application_datastore()
    create.id(entity) match {
      case Some(id) =>
        val op = UnitOfWorkOp.EntityStoreClaimOrLoad(
          entity,
          create,
          persisted,
          _entity_create_options(Some(id.collection.name)),
          _entity_uow_authorization(Some(id.collection.name), None, "create"),
          _entity_uow_authorization(Some(id.collection.name), Some(id), "read")
        )
        ConsequenceT.liftF(Free.liftF(op))
      case None =>
        exec_from(Consequence.argumentInvalid("entity_claim_or_load requires a stable entity id"))
    }
  }

  /** Claims or reads a server-owned stable Entity identity. Internal component workflows use this
    * when the identity has already been derived from trusted admitted input and must not depend on
    * user-record ACL fields.
   */
  protected final def entity_claim_or_load_internal[C, P](
    entity: C
  )(using
      create: EntityPersistentCreate[C],
      persisted: EntityPersistent[P]
  ): ExecUowM[EntityStore.EntityClaimResult[C, P]] = {
    ensure_component_application_datastore()
    create.id(entity) match {
      case Some(id) =>
        val createauthorization =
          _entity_uow_authorization(Some(id.collection.name), None, "create")
          .map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
        val loadauthorization =
          _entity_uow_authorization(Some(id.collection.name), Some(id), "read")
          .map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
        val op = UnitOfWorkOp.EntityStoreClaimOrLoad(
          entity,
          create,
          persisted,
          _entity_create_options(Some(id.collection.name)),
          createauthorization,
          loadauthorization
        )
        ConsequenceT.liftF(Free.liftF(op))
      case None =>
        exec_from(
          Consequence.argumentInvalid("entity_claim_or_load_internal requires a stable entity id")
        )
    }
  }

  /** Saves an Entity while keeping managed revision representation out of application logic. */
  protected final def entity_save_managed[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[T] =
    entity_save_managed(entity, EntityMutationExecutionPolicy.default)

  protected final def entity_save_managed[T](
    entity: T,
    executionPolicy: EntityMutationExecutionPolicy
  )(using tc: EntityPersistent[T]): ExecUowM[T] = {
    ensure_component_application_datastore()
    val id = tc.id(entity)
    val operation = UnitOfWorkOp.EntityStoreSaveManaged(
      entity,
      tc,
      _entity_uow_authorization(
        Some(id.collection.name),
        Some(id),
        "update"
      ),
      executionPolicy
    )
    ConsequenceT.liftF(Free.liftF(operation))
  }

  protected final def entity_save[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val op = new UnitOfWorkOp.EntityStoreSave(
      entity,
      None,
      effectivetc,
      _entity_uow_authorization(
        Some(effectivetc.id(entity).collection.name),
        Some(effectivetc.id(entity)),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_save[T](
      entity: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectedRevision,
      effectivetc,
      _entity_uow_authorization(
        Some(effectivetc.id(entity).collection.name),
        Some(effectivetc.id(entity)),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  /** Saves a server-owned Entity through the authorized UnitOfWork boundary. */
  protected final def entity_save_internal[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val authorization =
      _entity_uow_authorization(
        Some(effectivetc.id(entity).collection.name),
        Some(effectivetc.id(entity)),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = new UnitOfWorkOp.EntityStoreSave(
      entity,
      None,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_save_internal[T](
      entity: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val authorization =
      _entity_uow_authorization(
        Some(effectivetc.id(entity).collection.name),
        Some(effectivetc.id(entity)),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectedRevision,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_save_detached[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_save_detached(entity, None, serviceinternal = false)

  protected final def entity_save_detached[T](
    entity: T,
    expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_save_detached(
      entity,
      Some(expectedRevision),
      serviceinternal = false
    )

  protected final def entity_save_detached_internal[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_save_detached(entity, None, serviceinternal = true)

  protected final def entity_save_detached_internal[T](
    entity: T,
    expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_save_detached(
      entity,
      Some(expectedRevision),
      serviceinternal = true
    )

  private def _entity_save_detached[T](
    entity: T,
    expectedrevision: Option[EntityRevision],
    serviceinternal: Boolean
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] = {
    ensure_component_application_datastore()
    val effectivetc =
      _effective_entity_persistent(tc.id(entity).collection, tc)
    val id = effectivetc.id(entity)
    val authorization =
      _entity_uow_authorization(
        Some(id.collection.name),
        Some(id),
        "update"
      ).map { value =>
        if (serviceinternal)
          value.copy(accessMode = EntityAccessMode.ServiceInternal)
        else
          value
      }
    val operation = UnitOfWorkOp.EntityStoreSaveDetached(
      entity,
      expectedrevision,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(operation))
  }

  protected final def entity_update[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val op = new UnitOfWorkOp.EntityStoreUpdate(
      changes,
      None,
      effectivetc,
      _entity_uow_authorization(
        Some(effectivetc.id(changes).collection.name),
        Some(effectivetc.id(changes)),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
      changes: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectedRevision,
      effectivetc,
      _entity_uow_authorization(
        Some(effectivetc.id(changes).collection.name),
        Some(effectivetc.id(changes)),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update_internal[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val authorization =
      _entity_uow_authorization(
        Some(effectivetc.id(changes).collection.name),
        Some(effectivetc.id(changes)),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = new UnitOfWorkOp.EntityStoreUpdate(
      changes,
      None,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update_internal[T](
      changes: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val authorization =
      _entity_uow_authorization(
        Some(effectivetc.id(changes).collection.name),
        Some(effectivetc.id(changes)),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectedRevision,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update_detached[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_update_detached(changes, None, serviceinternal = false)

  protected final def entity_update_detached[T](
    changes: T,
    expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_update_detached(
      changes,
      Some(expectedRevision),
      serviceinternal = false
    )

  protected final def entity_update_detached_internal[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_update_detached(changes, None, serviceinternal = true)

  protected final def entity_update_detached_internal[T](
    changes: T,
    expectedRevision: EntityRevision
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] =
    _entity_update_detached(
      changes,
      Some(expectedRevision),
      serviceinternal = true
    )

  private def _entity_update_detached[T](
    changes: T,
    expectedrevision: Option[EntityRevision],
    serviceinternal: Boolean
  )(using tc: EntityPersistent[T]): ExecUowM[EntityRevisionCarrier[T]] = {
    ensure_component_application_datastore()
    val effectivetc =
      _effective_entity_persistent(tc.id(changes).collection, tc)
    val id = effectivetc.id(changes)
    val authorization =
      _entity_uow_authorization(
        Some(id.collection.name),
        Some(id),
        "update"
      ).map { value =>
        if (serviceinternal)
          value.copy(accessMode = EntityAccessMode.ServiceInternal)
        else
          value
      }
    val operation = UnitOfWorkOp.EntityStoreUpdateDetached(
      changes,
      expectedrevision,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(operation))
  }

  // Patch update with explicit target id.
  // This is intended for Update.PatchShape where id is excluded from patch object.
  protected final def entity_update[T](
    id: EntityId,
    patch: T
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[Record] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val op = new UnitOfWorkOp.EntityStoreUpdateById(
      effectiveid,
      patch,
      tc,
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
    id: EntityId,
      patch: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[EntityRecordSnapshot] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val op = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
      effectiveid,
      patch,
      expectedRevision,
      tc,
      _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  /** Applies a generated patch to a server-owned Entity.
   *
    * This is the ServiceInternal counterpart of `entity_update(id, patch)`. Keeping canonical ID
    * handling and authorization construction here prevents components from assembling UnitOfWork
    * operations or security metadata.
   */
  protected final def entity_update_internal[T](
    id: EntityId,
    patch: T
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[Record] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val authorization =
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = new UnitOfWorkOp.EntityStoreUpdateById(
      effectiveid,
      patch,
      tc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update_internal[T](
    id: EntityId,
      patch: T,
      expectedRevision: EntityRevision
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[EntityRecordSnapshot] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val authorization =
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
      effectiveid,
      patch,
      expectedRevision,
      tc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update_detached[T](
    id: EntityId,
    patch: T
  )(using
    tc: EntityPersistentUpdate[T]
  ): ExecUowM[EntityRevisionCarrier[Record]] =
    _entity_update_detached(
      id,
      patch,
      None,
      serviceinternal = false
    )

  protected final def entity_update_detached[T](
    id: EntityId,
    patch: T,
    expectedRevision: EntityRevision
  )(using
    tc: EntityPersistentUpdate[T]
  ): ExecUowM[EntityRevisionCarrier[Record]] =
    _entity_update_detached(
      id,
      patch,
      Some(expectedRevision),
      serviceinternal = false
    )

  protected final def entity_update_detached_internal[T](
    id: EntityId,
    patch: T
  )(using
    tc: EntityPersistentUpdate[T]
  ): ExecUowM[EntityRevisionCarrier[Record]] =
    _entity_update_detached(
      id,
      patch,
      None,
      serviceinternal = true
    )

  protected final def entity_update_detached_internal[T](
    id: EntityId,
    patch: T,
    expectedRevision: EntityRevision
  )(using
    tc: EntityPersistentUpdate[T]
  ): ExecUowM[EntityRevisionCarrier[Record]] =
    _entity_update_detached(
      id,
      patch,
      Some(expectedRevision),
      serviceinternal = true
    )

  private def _entity_update_detached[T](
    id: EntityId,
    patch: T,
    expectedrevision: Option[EntityRevision],
    serviceinternal: Boolean
  )(using
    tc: EntityPersistentUpdate[T]
  ): ExecUowM[EntityRevisionCarrier[Record]] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val authorization =
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      ).map { value =>
        if (serviceinternal)
          value.copy(accessMode = EntityAccessMode.ServiceInternal)
        else
          value
      }
    val operation = UnitOfWorkOp.EntityStoreUpdateByIdDetached(
      effectiveid,
      patch,
      expectedrevision,
      tc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(operation))
  }

  protected final def entity_save_c[T](
      entity: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[EntitySnapshot[T]] = {
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectedRevision,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_c(op)
  }

  protected final def entity_save_or_throw[T](
      entity: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): EntitySnapshot[T] = {
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectedRevision,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
      changes: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[EntitySnapshot[T]] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectedRevision,
      tc,
      _entity_uow_authorization(
        Some(tc.id(changes).collection.name),
        Some(tc.id(changes)),
        "update"
      )
    )
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
      changes: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): EntitySnapshot[T] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectedRevision,
      tc,
      _entity_uow_authorization(
        Some(tc.id(changes).collection.name),
        Some(tc.id(changes)),
        "update"
      )
    )
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
      id: EntityId,
      patch: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Consequence[EntityRecordSnapshot] = {
    val op = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
      id,
      patch,
      expectedRevision,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    id: EntityId,
      patch: T,
      expectedRevision: EntityRevision
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): EntityRecordSnapshot = {
    val op = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
      id,
      patch,
      expectedRevision,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_or_throw(op)
  }

}
