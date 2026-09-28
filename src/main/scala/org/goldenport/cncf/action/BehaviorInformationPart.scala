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
trait BehaviorInformationPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def information_space: ExecUowM[InformationSpace] =
    exec_from(_information_space)

  protected final def information_register(
    domain: String,
    records: Vector[Record]
  ): ExecUowM[Vector[Information]] =
    exec_from_calltree(
      "uow:information:register",
      _information_attributes("register", domain) + ("record_count" -> records.size.toString)
    ) {
      _information_space.flatMap(_.registerInformation(domain, records)(using execution_context))
    }

  protected final def information_update(
    informationid: EntityId,
    workingdata: Record
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:update", _information_attributes("update", informationid)) {
      _information_space.flatMap(_.updateInformation(informationid, workingdata)(using
      execution_context))
    }

  protected final def information_update_observed(
    informationId: EntityId,
    workingData: Record,
    observedRevision: EntityRevision
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:update",
      _information_attributes("update", informationId) + ("observed_revision" -> observedRevision.value.toString)
    ) {
      _information_space.flatMap(_.updateInformationObserved(
        informationId,
        workingData,
        observedRevision
      )(using execution_context))
    }

  protected final def information_append_field_event(
    informationid: EntityId,
    event: InformationFieldEvent
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:field-event:append",
      _information_attributes("field-event-append", informationid) + ("field_path" -> event
        .fieldPath) + ("state" -> event.state.value) + ("source" -> event.source)
    ) {
      _information_space.flatMap(_.appendFieldEvent(informationid, event)(using execution_context))
    }

  protected final def information_append_field_events(
    informationid: EntityId,
    events: Vector[InformationFieldEvent]
  ): ExecUowM[Unit] =
    events.foldLeft(exec_pure(())) { (z, event) =>
      z.flatMap { _ =>
        information_append_field_event(informationid, event).map(_ => ())
      }
    }

  protected final def information_validate(
    informationid: EntityId
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:validate",
      _information_attributes("validate", informationid)
    ) {
      _information_space.flatMap(_.validateInformation(informationid)(using execution_context))
    }

  protected final def information_confirm(
    informationid: EntityId
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:confirm",
      _information_attributes("confirm", informationid)
    ) {
      _information_space.flatMap(_.confirmInformation(informationid)(using execution_context))
    }

  protected final def information_reject(
    informationid: EntityId,
    reason: String
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reject", _information_attributes("reject", informationid)) {
      _information_space.flatMap(_.rejectInformation(informationid, reason)(using
      execution_context))
    }

  protected final def information_reopen(
    informationid: EntityId
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reopen", _information_attributes("reopen", informationid)) {
      _information_space.flatMap(_.reopenInformation(informationid)(using execution_context))
    }

  protected final def information_publish(
    informationid: EntityId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): ExecUowM[InformationPublicationStatus] =
    exec_from_calltree(
      "uow:information:publish",
      _information_attributes("publish", informationid) + ("target" -> target)
    ) {
      _information_space.flatMap(_.publishInformation(
        informationid,
        target,
        message,
        knowledgeframeid
      )(using execution_context))
    }

  protected final def information_fail_publication(
    informationid: EntityId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): ExecUowM[InformationPublicationStatus] =
    exec_from_calltree(
      "uow:information:publish-failure",
      _information_attributes("publish-failure", informationid) + ("target" -> target)
    ) {
      _information_space.flatMap(_.failInformationPublication(
        informationid,
        target,
        message,
        knowledgeframeid
      )(using execution_context))
    }

  protected final def information_add_resolution_candidate(
    informationid: EntityId,
    fieldpath: String,
    candidatelabel: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree(
      "uow:information:candidate:add",
      _information_attributes("candidate-add", informationid) + ("field_path" -> fieldpath)
    ) {
      _information_space.flatMap(_.addResolutionCandidate(
        informationid,
        fieldpath,
        candidatelabel,
        binding,
        confidence,
        evidence
      )(using execution_context))
    }

  protected final def information_select_resolution_candidate(
    informationid: EntityId,
    candidatekey: String
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree(
      "uow:information:candidate:select",
      _information_candidate_attributes("candidate-select", informationid, candidatekey)
    ) {
      _information_space.flatMap(_.selectResolutionCandidate(informationid, candidatekey)(using
      execution_context))
    }

  protected final def information_clear_resolution_candidate(
    informationid: EntityId,
    candidatekey: String
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree(
      "uow:information:candidate:clear",
      _information_candidate_attributes("candidate-clear", informationid, candidatekey)
    ) {
      _information_space.flatMap(_.clearResolutionCandidate(informationid, candidatekey)(using
      execution_context))
    }

  protected final def information_materialize(
    information: Information
  ): ExecUowM[KnowledgeWorkingSetSnapshot] =
    exec_from_calltree(
      "uow:information:materialize",
      _information_attributes("materialize", information.id)
    ) {
      Consequence.success(InformationSpace.materializeInformation(information)(using
      execution_context))
    }

  protected final def information_option(
    informationid: EntityId
  ): ExecUowM[Option[Information]] =
    exec_from_calltree("uow:information:option", _information_attributes("option", informationid)) {
      _information_space.flatMap(_.getInformationC(informationid)(using execution_context))
    }

  protected final def information_validation_issues(
    informationid: EntityId
  ): ExecUowM[Vector[InformationValidationIssue]] =
    exec_from_calltree(
      "uow:information:validation-issues",
      _information_attributes("validation-issues", informationid)
    ) {
      _information_space.flatMap(_.validationIssuesC(informationid)(using execution_context))
    }

  protected final def information_add_conflict(
    informationid: EntityId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  ): ExecUowM[InformationConflict] =
    exec_from_calltree(
      "uow:information:conflict:record",
      _information_attributes("conflict-record", informationid) + ("field_path" -> fieldpath)
    ) {
      _information_space.flatMap(_.recordConflict(
        informationid,
        fieldpath,
        informationvalue,
        rdfvalue,
        severity
      )(using execution_context))
    }

  protected final def information_resolve_conflict(
    informationid: EntityId,
    conflictkey: String,
    decision: String
  ): ExecUowM[InformationConflict] =
    exec_from_calltree(
      "uow:information:conflict:resolve",
      Map(
        "operation"      -> "conflict-resolve",
        "information_id" -> informationid.print,
        "conflict_key"   -> conflictkey
      )
    ) {
      _information_space.flatMap(_.resolveConflict(informationid, conflictkey, decision)(using
      execution_context))
    }

  private def _information_space: Consequence[InformationSpace] =
    component match {
      case Some(component) => Consequence.success(component.informationSpace)
      case None =>
        Consequence.serviceUnavailable("InformationSpace is unavailable: component is not bound.")
    }

  private def _information_attributes(
    operation: String,
    domain: String
  ): Map[String, String] =
    Map("operation" -> operation, "domain" -> domain)

  private def _information_attributes(
    operation: String,
    informationid: EntityId
  ): Map[String, String] =
    Map("operation" -> operation, "information_id" -> informationid.print)

  private def _information_candidate_attributes(
    operation: String,
    informationid: EntityId,
    candidatekey: String
  ): Map[String, String] =
    Map(
      "operation"      -> operation,
      "information_id" -> informationid.print,
      "candidate_key"  -> candidatekey
    )
}
