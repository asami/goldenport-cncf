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
trait ActionCallEntityStorePart extends ActionCallFeaturePart with ActionCallEntityAccessPart with ActionCallEntityMutationPart with ActionCallEntityLifecyclePart { self: ActionCall.Core.Holder =>
  private[action] def _emit_entity_access(
    name: String,
    attributes: Record
  ): Unit = {
    component.flatMap(_.subsystem).map(_.entityAccessMetrics).getOrElse(
      EntityAccessMetricsRegistry.shared
    ).record(name, attributes)
    val _ = execution_context.observability.emitDebug(
      execution_context.cncfCore.scope,
      name,
      attributes
    )
  }

  private def _calltree_metric_attributes(
    name: String,
    attributes: Record
  ): Map[String, String] =
    (Vector("metric" -> name) ++
      attributes.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) =>
          key -> _truncate_calltree_metric_text(
            _sanitize_calltree_metric_value(key, value).toString,
            1000
          )
        }).toMap

  private def _sanitize_calltree_metric_value(
    key: String,
    value: Any
  ): Any =
    if (_is_sensitive_calltree_metric_key(key)) "***" else value

  private def _is_sensitive_calltree_metric_key(
    key: String
  ): Boolean = {
    val normalized = key.toLowerCase(java.util.Locale.ROOT)
    normalized.contains("password") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie")
  }

  private def _truncate_calltree_metric_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private[action] def _entity_load_attributes(
    id: EntityId,
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> id.collection.name,
    "id" -> id.value,
    "source" -> source,
    "outcome" -> outcome
  )

  private[action] def _entity_search_attributes(
    query: EntityQuery[?],
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> query.collection.name,
    "source" -> source,
    "outcome" -> outcome,
    "query" -> query.query.toString
  )

  private[action] def _entity_search_working_set_loading_attributes(
    query: EntityQuery[?],
    state: org.goldenport.cncf.entity.runtime.WorkingSetLoadState
  ): Record = Record.dataAuto(
    "entity" -> query.collection.name,
    "source" -> "entity-store",
    "outcome" -> "fallback",
    "reason" -> "working-set-loading",
    "workingSetState" -> state.label,
    "query" -> query.query.toString
  )

  private[action] def _is_entity_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

  private[action] def _bypass_entity_space_resident_search: Boolean =
    _bypass_all_resident_search ||
      _config_bool(
        "textus.entity.search.bypass-entity-space-resident",
        "cncf.entity.search.bypass-entity-space-resident"
      )

  private def _bypass_all_resident_search: Boolean =
    _config_bool(
      "textus.entity.search.bypass-resident",
      "cncf.entity.search.bypass-resident"
    )

  private[action] def _working_set_enabled: Boolean =
    execution_context.framework.workingSetEnabled &&
      !org.goldenport.cncf.context.GlobalRuntimeContext.current.exists { global =>
        global.runtimeMode == RunMode.Command || global.runtimeMode == RunMode.Client
      }

  private def _config_bool(primary: String, compatibility: String): Boolean =
    _config_bool(primary) || _config_bool(compatibility)

  private def _config_bool(key: String): Boolean =
    component
      .flatMap(_.subsystem)
      .flatMap(_.configurationValue(key))
      .exists(_truthy)

  private def _truthy(value: ConfigurationValue): Boolean =
    _config_string(value).exists { x =>
      val v = x.trim.toLowerCase(java.util.Locale.ROOT)
      v == "true" || v == "yes" || v == "on" || v == "1"
    }

  private def _config_string(value: ConfigurationValue): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case _ => None
    }

  private def _declared_operation_definition =
    component.flatMap { c =>
      val actionname = _normalize_name(action.name)
      c.operationDefinitions.find { op =>
        val opname = _normalize_name(op.name)
        val inputname = _normalize_name(op.inputType)
        actionname == opname ||
          actionname.endsWith(opname) ||
          (inputname.nonEmpty && actionname == inputname)
      }
    }

  private def _declared_access =
    _declared_operation_definition.flatMap(_.access)

  private[action] def _declared_visibility_scope: Option[EntityVisibilityScope] =
    _declared_operation_definition.flatMap(_.visibility.flatMap(EntityVisibilityScope.parseOption))

  private[action] def _with_declared_visibility[T](
    query: EntityQuery[T]
  ): EntityQuery[T] =
    if (query.visibilityScope.nonEmpty)
      query
    else
      query.copy(visibilityScope = _declared_visibility_scope)

  private def _declared_entities =
    _declared_operation_definition.map { op =>
      if (op.entityNames.nonEmpty) op.entityNames else op.entityName.toVector
    }.getOrElse(Vector.empty)

  private[action] def _entity_uow_authorization(
      resourcetype: Option[String],
      targetid: Option[EntityId],
      accesskind: String
  ): Option[UnitOfWorkAuthorization] = {
    val access = _declared_access
    val entitynames = _declared_entities
    val entityname  = entitynames.headOption.orElse(resourcetype).getOrElse("")
    val factory = getFactory[org.goldenport.cncf.component.Component.Factory]
    val runtimeentitydescriptor =
      component.flatMap(_.entityRuntimeDescriptor(entityname))
    val entityusage =
      factory
        .flatMap(_.entityUsageKind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityoperationkind =
      factory
        .flatMap(_.entityOperationKind(action, entityname, core))
        .orElse(access.flatMap(_.entityOperationKind).map(EntityOperationKind.parse))
        .orElse(runtimeentitydescriptor.map(_.effectiveOperationKind))
        .getOrElse(
          entityusage match
            case EntityUsageKind.Executable => EntityOperationKind.Task
            case _ => EntityOperationKind.default
        )
    val entityapplicationdomain =
      factory
        .flatMap(_.entityApplicationDomain(action, entityname, core))
        .orElse(access.flatMap(_.entityApplicationDomain).map(EntityApplicationDomain.parse))
        .orElse(runtimeentitydescriptor.map(_.applicationDomain))
        .getOrElse(
          entityusage match
            case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
            case _ => EntityApplicationDomain.default
        )
    val operationmodel =
      factory
        .flatMap(_.serviceOperationModel(action, core))
        .orElse(access.flatMap(_.operationModel).map(ServiceOperationModel.parse))
        .getOrElse(ServiceOperationModel.default)
    val explicitrelations =
      factory
        .map(_.entityAccessRelations(action, entityname, accesskind, core))
        .getOrElse(Vector.empty) ++
      access.flatMap(_.relation).map(EntityAccessRelation.parseList).getOrElse(Vector.empty)
    val naturalconditions =
      access.flatMap(_.condition).map(EntityAbacCondition.parseList).getOrElse(Vector.empty)
    val derivedprofile = EntityAuthorizationProfile.derive(
      operationKind = entityoperationkind,
      applicationDomain = entityapplicationdomain,
      operationModel = operationmodel,
      explicitRelations = explicitrelations
    )
    val accessmode =
      factory
        .flatMap(_.entityAccessMode(action, entityname, accesskind, core))
        .orElse(access.flatMap(_.mode).map(EntityAccessMode.parse))
        .getOrElse(derivedprofile.accessMode)
    Some(
      UnitOfWorkAuthorization(
        resourceFamily = "domain",
        resourceType = entitynames.headOption.orElse(resourcetype),
        collectionName = targetid.map(_.collection.name).orElse(resourcetype),
        targetId = targetid,
        accessKind = accesskind,
        access = access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        entityNames = entitynames,
        accessMode = accessmode,
        operationModel = Some(operationmodel),
        entityOperationKind = Some(entityoperationkind),
        entityApplicationDomain = Some(entityapplicationdomain),
        visibilityScope = _declared_visibility_scope,
        relationRules = derivedprofile.relationRules,
        naturalConditions = naturalconditions
      )
    )
  }

  private[action] def _entity_create_options(
      resourcetype: Option[String]
  ): EntityCreateOptions = {
    val access = _declared_access
    val entitynames = _declared_entities
    val entityname  = entitynames.headOption.orElse(resourcetype).getOrElse("")
    val factory = getFactory[org.goldenport.cncf.component.Component.Factory]
    val runtimeentitydescriptor =
      component.flatMap(_.entityRuntimeDescriptor(entityname))
    val entityusage =
      factory
        .flatMap(_.entityUsageKind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityapplicationdomain =
      factory
        .flatMap(_.entityApplicationDomain(action, entityname, core))
        .orElse(access.flatMap(_.entityApplicationDomain).map(EntityApplicationDomain.parse))
        .orElse(runtimeentitydescriptor.map(_.applicationDomain))
        .getOrElse(
          entityusage match
            case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
            case _ => EntityApplicationDomain.default
        )
    val profiles =
      if (entityusage == EntityUsageKind.SharedRecord)
        EntityCreateOptions.sharedRecord.defaultProfiles
      else if (
        entityapplicationdomain == EntityApplicationDomain.Cms ||
        entityusage == EntityUsageKind.PublicContent ||
        access.exists(_.policy.equalsIgnoreCase("public"))
      )
        Set("cms", "publication", "public-content", "public-read")
      else
        Set.empty[String]
    EntityCreateOptions(defaultProfiles = profiles)
  }

  private def _normalize_name(p: String): String =
    Option(p).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")
}
