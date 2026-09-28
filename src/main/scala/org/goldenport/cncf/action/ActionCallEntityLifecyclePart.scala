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
private[action] trait ActionCallEntityLifecyclePart extends ActionCallFeaturePart { self: ActionCallEntityStorePart & ActionCall.Core.Holder =>
  protected final def entity_conditional_transition[R, P, S](
    request: EntityConditionalTransition[R, P, S]
  ): ExecUowM[EntityConditionalTransitionResult[R, S]] =
    _entity_conditional_transition(request, serviceinternal = false)

  protected final def entity_conditional_transition_internal[R, P, S](
    request: EntityConditionalTransition[R, P, S]
  ): ExecUowM[EntityConditionalTransitionResult[R, S]] =
    _entity_conditional_transition(request, serviceinternal = true)

  private def _entity_conditional_transition[R, P, S](
    request: EntityConditionalTransition[R, P, S],
    serviceinternal: Boolean
  ): ExecUowM[EntityConditionalTransitionResult[R, S]] = {
    ensure_component_application_datastore()
    val rootid = request.rootId
    val successorintent = request.successor match {
      case create: EntitySuccessorIntent.Create[c, S] @unchecked =>
        create
      case bind: EntitySuccessorIntent.Bind[S] @unchecked =>
        new EntitySuccessorIntent.Bind(
          bind.id,
          bind.persisted
        )
    }
    val successorcollection = successorintent match {
      case create: EntitySuccessorIntent.Create[?, S] =>
        create.collection
      case bind: EntitySuccessorIntent.Bind[S] =>
        bind.id.collection
    }
    val admittedrequest =
      EntityConditionalTransition.create(
        rootid,
        request.expectation,
        request.rootPatch,
        successorintent
      )(using request.patchPersistent)
    exec_from(admittedrequest).flatMap { effectiverequest =>
      val owner =
        _conditional_transition_component_owner(
          rootid.collection,
          successorcollection
        )
      exec_from(owner).flatMap { componentowner =>
        val rootread =
          _conditional_transition_authorization(
            Some(rootid.collection.name),
            Some(rootid),
            "read",
            serviceinternal
          )
        val rootupdate =
          _conditional_transition_authorization(
            Some(rootid.collection.name),
            Some(rootid),
            "update",
            serviceinternal
          )
        val successor = successorintent match {
          case create: EntitySuccessorIntent.Create[?, S] =>
            _conditional_transition_authorization(
              Some(create.collection.name),
              None,
              "create",
              serviceinternal
            )
          case bind: EntitySuccessorIntent.Bind[S] =>
            _conditional_transition_authorization(
              Some(bind.id.collection.name),
              Some(bind.id),
              "read",
              serviceinternal
            )
        }
        val op = UnitOfWorkOp.EntityStoreConditionalTransition(
          effectiverequest,
          componentowner,
          rootread,
          rootupdate,
          successor
        )
        ConsequenceT.liftF(Free.liftF(op))
      }
    }
  }

  private def _conditional_transition_component_owner(
    rootcollection: EntityCollectionId,
    successorcollection: EntityCollectionId
  ): Consequence[DataStoreComponentOwner] =
    _component_entity_owner(Vector(rootcollection, successorcollection))

  private[action] def _component_entity_owner(
    collections: Vector[EntityCollectionId]
  ): Consequence[DataStoreComponentOwner] =
    component match {
      case Some(c)
          if collections.forall(c.entitySpace.entityOption(_).isDefined) =>
        component_name_option
          .map(DataStoreComponentOwner.create)
          .getOrElse(Consequence.argumentMissing("componentName"))
      case Some(_) =>
        Consequence.securityPermissionDenied(
          "Conditional transition collections must belong to the executing component.",
          Cause.Kind.Guard,
          Seq(
            Descriptor.Facet.Reason(
              "conditional-transition-component-scope"
            ),
            Descriptor.Facet.Guard(
              "cross-component"
            )
          )
        )
      case None =>
        Consequence.argumentMissing("component")
    }

  private def _conditional_transition_authorization(
    resourcetype: Option[String],
    targetid: Option[EntityId],
    accesskind: String,
    serviceinternal: Boolean
  ): Option[UnitOfWorkAuthorization] = {
    val authorization =
      _entity_uow_authorization(resourcetype, targetid, accesskind)
    if (serviceinternal)
      authorization.map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    else
      authorization
  }

  protected final def entity_delete(id: EntityId): ExecUowM[Unit] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val op = UnitOfWorkOp.EntityStoreDelete(
      effectiveid,
      _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "delete")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_restore(id: EntityId): ExecUowM[Unit] = {
    ensure_component_application_datastore()
    val effectiveid = id
    val op = UnitOfWorkOp.EntityStoreRestore(
      effectiveid,
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      )
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete_hard(id: EntityId): ExecUowM[Unit] = {
    ensure_component_application_datastore()
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDelete(
      id,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "delete")
    )
    exec_c(op)
  }

  protected final def entity_delete_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDelete(
      id,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "delete")
    )
    exec_or_throw(op)
  }

  protected final def entity_restore_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreRestore(
      id,
      _entity_uow_authorization(
        Some(id.collection.name),
        Some(id),
        "update"
      )
    )
    exec_c(op)
  }

  protected final def entity_restore_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreRestore(
      id,
      _entity_uow_authorization(
        Some(id.collection.name),
        Some(id),
        "update"
      )
    )
    exec_or_throw(op)
  }

  protected final def entity_delete_hard_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    exec_c(op)
  }

  protected final def entity_delete_hard_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    exec_or_throw(op)
  }

}
