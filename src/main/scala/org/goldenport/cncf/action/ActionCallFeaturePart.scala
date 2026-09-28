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
 * @since   Jan.  6, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 30, 2026
 *  version Apr. 29, 2026
 *  version May. 25, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionCallFeaturePart extends BehaviorFeaturePart { self: ActionCall.Core.Holder =>
  protected final def action_property_string(name: String): Option[String] =
    action.properties.reverseIterator.collectFirst {
      case prop if prop.name.equalsIgnoreCase(name) =>
        Option(prop.value).map(_.toString.trim).getOrElse("")
    }.filter(_.nonEmpty)

  protected final def action_required_property_string(name: String): Consequence[String] =
    Consequence.fromOption(
      action_property_string(name),
      s"Property not found: $name"
    )

  protected final def execution_property_string(name: String): Option[String] =
    executionContext.runtime.resolvedParameters.get(name)
      .flatMap(parameter => _configuration_value_string(parameter.value))

  protected final def execution_property_string(
    primary: String,
    compatibility: String
  ): Option[String] =
    execution_property_string(primary).orElse(execution_property_string(compatibility))

  protected final def config_string(key: String): Option[String] =
    action_property_string(key)
      .orElse(
        component
          .flatMap(_.subsystem)
          .flatMap(_.configurationValue(key))
          .flatMap(_configuration_value_string)
      )
      .orElse(
        executionContext.runtime.resolvedParameters.get(key)
          .flatMap(parameter => _configuration_value_string(parameter.value))
      )

  protected final def config_string(
    primary: String,
    compatibility: String
  ): Option[String] =
    config_string(primary).orElse(config_string(compatibility))

  protected final def component_configuration[A](
    key: ComponentConfigurationKey[A]
  ): Consequence[ComponentConfigurationResolution[A]] =
    ComponentConfigurationAccess(_component_configuration_sources).resolve(key)

  private def _component_configuration_sources: ComponentConfigurationSources = {
    val componentconfiguration =
      component.flatMap(_.applicationConfig.config).getOrElse(Configuration.empty)
    val subsystemconfiguration =
      component.flatMap(_.subsystem).map(_.configuration.configuration).getOrElse(
        Configuration.empty
      )
    val runtimeconfiguration = _runtime_configuration(executionContext.runtime)
    ComponentConfigurationSources(
      componentconfiguration,
      subsystemconfiguration,
      runtimeconfiguration
    )
  }

  @annotation.tailrec
  private def _runtime_configuration(scope: ScopeContext): Configuration =
    scope match {
      case m: GlobalRuntimeContext => m.resolvedConfiguration.configuration
      case _ => scope.parent match {
        case Some(parent) => _runtime_configuration(parent)
        case None => Configuration.empty
      }
    }

  private def _component_configuration: Option[ResolvedConfiguration] = {
    val subsystemconfiguration = component.flatMap(_.subsystem).map(_.configuration)
    val artifactconfig =
      component.flatMap(_.artifactMetadata).map(_.effectiveConfig).getOrElse(Map.empty)
    if (artifactconfig.isEmpty)
      subsystemconfiguration
    else {
      val artifactconfiguration = ResolvedConfiguration(
        Configuration(artifactconfig.view.mapValues(ConfigurationValue.StringValue.apply).toMap),
        ConfigurationTrace.empty
      )
      Some(
        subsystemconfiguration
          .map(configuration =>
            ResolvedConfiguration(
              Configuration(
                configuration.configuration.values ++ artifactconfiguration.configuration.values
              ),
              configuration.trace
            )
          )
          .getOrElse(artifactconfiguration)
      )
    }
  }

  protected final def ensure_component_application_datastore(
    name: String = "application"
  ): Unit =
    bind_component_application_datastore_c(name) match {
      case Consequence.Success(_) => ()
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
    }

  protected final def bind_component_application_datastore_c(
    name: String = "application"
  ): Consequence[Unit] = {
    val request = _component_datastore_request(name)
    val environment = ComponentDataStore.Environment(
      executionContext.runtime.resolvedParameters,
      _component_configuration
    )
    component.flatMap(_.subsystem) match {
      case Some(subsystem) =>
        subsystem.bindManagedApplicationDataStoreC(
          executionContext.dataStoreSpace,
          environment,
          request
        )
      case None =>
        Consequence(executionContext.dataStoreSpace.bindApplicationDataStore(environment, request))
    }
  }

  protected final def component_datastore(
    name: String = "application"
  ): DataStore = {
    val request = _component_datastore_request(name)
    val environment = ComponentDataStore.Environment(
      executionContext.runtime.resolvedParameters,
      _component_configuration
    )
    component.flatMap(_.subsystem) match {
      case Some(subsystem) =>
        subsystem.resolveManagedComponentDataStoreC(environment, request) match {
          case Consequence.Success(datastore) => datastore
          case Consequence.Failure(conclusion) =>
            throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
        }
      case None =>
        ComponentDataStore.resolve(environment, request)
    }
  }

  private def _component_datastore_request(name: String): ComponentDataStore.Request =
    component
      .flatMap(_.coreOption.map(_.componentId))
      .map(componentid => ComponentDataStore.Request.forComponent(componentid, name))
      .getOrElse(ComponentDataStore.Request(action.request.component.getOrElse("component"), name))

  protected final def config_int(key: String): Option[Int] =
    config_string(key).flatMap(_.toIntOption)

  protected final def config_double(key: String): Option[Double] =
    config_string(key).flatMap(_.toDoubleOption)

  protected final def config_boolean(key: String): Option[Boolean] =
    config_string(key).flatMap(_config_boolean)

  protected final def parse_dsl_document(
    path: Path
  ): Consequence[Configuration] =
    consequence_with_calltree(
      "cncf:dsl:parse",
      Map("source" -> path.toString)
    ) {
      new RuntimeFileConfigLoader().load(path)
    }

  protected final def parse_dsl_document(
    filename: String,
    content: String
  ): Consequence[Configuration] =
    consequence_with_calltree(
      "cncf:dsl:parse",
      Map("source" -> filename)
    ) {
      ConfigTextDecoder.decode(filename, content)
    }

  protected final def parse_dsl_content(
    format: String,
    content: String
  ): Consequence[Configuration] = {
    val normalized = Option(format).map(_.trim).filter(_.nonEmpty).getOrElse("yaml")
    consequence_with_calltree(
      "cncf:dsl:parse",
      Map("format" -> normalized)
    ) {
      ConfigTextDecoder.decode(s"inline.$normalized", content)
    }
  }

  protected final def parse_dsl_content_json(
    content: String
  ): Consequence[Configuration] =
    parse_dsl_content("json", content)

  protected final def parse_dsl_content_yaml(
    content: String
  ): Consequence[Configuration] =
    parse_dsl_content("yaml", content)

  private def _configuration_value_string(
    value: ConfigurationValue
  ): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Option(v).map(_.trim).filter(_.nonEmpty)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }

  private def _config_boolean(value: String): Option[Boolean] = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    normalized match {
      case "true" | "yes" | "on" | "1" => Some(true)
      case "false" | "no" | "off" | "0" => Some(false)
      case _ => None
    }
  }

  protected final def resolve_aggregate_behavior(
  ): Consequence[AggregateBehavior[?]] =
    component.flatMap(_.factory).flatMap(_.createAggregateBehavior(action, core)) match {
      case Some(behavior) => Consequence.success(behavior)
      case None => Consequence.operationNotFound(s"AggregateBehavior not found: ${action.name}")
    }

  protected final def invoke_aggregate_behavior[A](
    behavior: AggregateBehavior[A],
    target: A
  ): Consequence[OperationResponse] =
    behavior.run(target, executionContext)
}
