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
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.entity.EntityMutationExpectation
import org.goldenport.cncf.entity.EntityRecordSnapshot
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
import org.goldenport.cncf.information.{
  InformationConflict,
  InformationFieldEvent,
  InformationFieldState,
  Information,
  InformationId,
  InformationIdentityBinding,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpace,
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
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext

  protected final def execution_clock: Clock =
    execution_context.clock

  protected final def current_instant: Instant =
    execution_clock.instant()

  protected final def current_zoned_datetime: ZonedDateTime =
    current_instant.atZone(execution_context.timezone)

  protected final def read_resource(reference: ResourceReference): Consequence[ResourceContent] =
    execution_context.resources.read(reference)

  protected final def read_resource_text(
    reference: ResourceReference,
    charset: Option[Charset] = None
  ): Consequence[String] =
    execution_context.resources.readText(reference, charset)

  protected final def read_static_web_resource(
    reference: ResourceReference
  ): Consequence[ResourceContent] =
    execution_context.resources.readStaticWeb(reference)

  protected final def read_resource_tree(
    reference: ResourceTreeReference,
    limits: ResourceTreeLimits = ResourceTreeLimits.default
  ): Consequence[ResourceTreeSnapshot] =
    execution_context.resourceTrees.snapshot(reference, limits)

  protected final def query_resource_tree(
    query: ResourceTreeQuery
  ): Consequence[ResourceTreeQueryResult] =
    execution_context.resourceTrees.query(query)

  protected final def await_delay(duration: Duration): Consequence[Unit] =
    if (duration.isNegative)
      Consequence.argumentInvalid("Execution delay must not be negative")
    else
      try
        _execution_capability(
          "time.await-delay",
          Map("duration_ms" -> duration.toMillis.toString)
        ) {
          execution_context.executionControl.schedulerMode match {
            case ExecutionSchedulerMode.Manual =>
              _execution_profile_runtime(execution_context.cncfCore.scope)
                .flatMap(_.executionProfileRuntime.testControl)
                .map(_.advanceBy(duration))
                .getOrElse(throw new IllegalStateException(
                  "Manual execution delay requires an execution-context-owned CNCF execution profile"
                ))
            case ExecutionSchedulerMode.Realtime =>
              Thread.sleep(duration.toMillis)
          }
          Consequence.unit
        }
      catch {
        case e: InterruptedException =>
          Thread.currentThread.interrupt()
          Consequence.serviceUnavailable("Execution delay interrupted")
        case e: IllegalStateException =>
          Consequence.operationInvalid(e.getMessage)
      }

  private def _execution_profile_runtime(
    scope: ScopeContext
  ): Option[GlobalRuntimeContext] =
    scope match {
      case runtime: GlobalRuntimeContext => Some(runtime)
      case other => other.parent.flatMap(_execution_profile_runtime)
    }

  protected final def random_int(purpose: String, bound: Int): Int =
    _execution_capability(
      "random.next-int",
      Map("purpose" -> purpose, "bound" -> bound.toString)
    ) {
      execution_context.random.stream(purpose).nextInt(bound)
    }

  protected final def random_long(purpose: String): Long =
    _execution_capability("random.next-long", Map("purpose" -> purpose)) {
      execution_context.random.stream(purpose).nextLong()
    }

  protected final def random_double(purpose: String): Double =
    _execution_capability("random.next-double", Map("purpose" -> purpose)) {
      execution_context.random.stream(purpose).nextDouble()
    }

  protected final def random_boolean(purpose: String): Boolean =
    _execution_capability("random.next-boolean", Map("purpose" -> purpose)) {
      execution_context.random.stream(purpose).nextBoolean()
    }

  protected final def entity_id(
    collection: EntityCollectionId,
    purpose: String
  ): EntityId =
    _execution_capability(
      "id.entity-id",
      Map(
        "purpose" -> purpose,
        "collection" -> collection.name
      )
    ) {
      execution_context.idGeneration.entityId(collection, purpose)
    }

  protected final def collection_entity_id(
    collection: EntityCollectionId,
    purpose: String
  ): EntityId =
    _execution_capability(
      "id.collection-entity-id",
      Map(
        "purpose" -> purpose,
        "collection" -> collection.name
      )
    ) {
      execution_context.idGeneration.entityIdInCollectionNamespace(collection, purpose)
    }

  protected final def opaque_id(purpose: String): String =
    _execution_capability("id.opaque-id", Map("purpose" -> purpose)) {
      execution_context.idGeneration.opaqueId(purpose)
    }

  protected final def component_name_option: Option[String] =
    component.flatMap(_.coreOption.map(_.name))

  protected final def exec_pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  protected final def exec_from[A](c: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](c)

  protected final def exec_from_calltree[A](
    label: String,
    attributes: Map[String, String] = Map.empty
  )(
    c: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(consequence_with_calltree(label, attributes)(c))

  protected final def exec_c[A](op: UnitOfWorkOp[A])(using uow: UnitOfWork): Consequence[A] =
    new UnitOfWorkInterpreter(uow).run(ConsequenceT.liftF(Free.liftF(op)))

  protected final def exec_or_throw[A](op: UnitOfWorkOp[A])(using uow: UnitOfWork): A =
    exec_c(op).TAKE

  protected final def response_string(p: String): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar(p))

  protected final def response_json(p: Json): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Json(p))

  protected final def response_yaml(p: String): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Yaml(p))

  protected final def consequence_with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val calltree = execution_context.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "uow"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "failure",
            "error" -> Option(e.getMessage).getOrElse(e.getClass.getName)
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _execution_capability[A](
    operation: String,
    attributes: Map[String, String]
  )(
    body: => A
  ): A = {
    val calltree = execution_context.observability.callTreeContext
    if (calltree.isEnabled) {
      val capability = operation.takeWhile(_ != '.')
      calltree.enter(
        s"execution:$operation",
        attributes ++ Map(
          "calltree_kind" -> "execution-capability",
          "capability" -> capability,
          "operation" -> operation,
          "profile" -> execution_context.executionControl.profile.name
        )
      )
      try {
        val result = body
        calltree.leave(Map("outcome" -> "success"))
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "failure",
            "error_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }
}

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
  ): Unit = {
    val componentname =
      component
        .flatMap(_.coreOption.map(_.name))
        .orElse(action.request.component)
        .getOrElse("component")
    executionContext.dataStoreSpace.bindApplicationDataStore(
      ComponentDataStore.Environment(
        executionContext.runtime.resolvedParameters,
        _component_configuration
      ),
      componentname,
      name
    )
  }

  protected final def component_datastore(
    name: String = "application"
  ): DataStore = {
    val componentname =
      component
        .flatMap(_.coreOption.map(_.name))
        .orElse(action.request.component)
        .getOrElse("component")
    ComponentDataStore.resolve(
      ComponentDataStore.Environment(
        executionContext.runtime.resolvedParameters,
        _component_configuration
      ),
      ComponentDataStore.Request(componentname, name)
    )
  }

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
    component.flatMap(_.factory).flatMap(_.create_aggregate_behavior(action, core)) match {
      case Some(behavior) => Consequence.success(behavior)
      case None => Consequence.operationNotFound(s"AggregateBehavior not found: ${action.name}")
    }

  protected final def invoke_aggregate_behavior[A](
    behavior: AggregateBehavior[A],
    target: A
  ): Consequence[OperationResponse] =
    behavior.run(target, executionContext)
}

trait BehaviorProcessExecutionPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>

  /** Resolves a provider's logical request through the runtime-owned admission service before
    * creating the resolved-only UnitOfWork operation.
   */
  protected final def process_exec(
    request: ProcessExecutionRequest
  ): ExecUowM[ProcessExecutionResult] =
    exec_from(ProcessExecutionAdmission.resolveC(execution_context.cncfCore.scope, request)).flatMap(
      process_exec
    )

  protected final def process_exec_c(
    request: ProcessExecutionRequest
  )(using uow: UnitOfWork): Consequence[ProcessExecutionResult] =
    ProcessExecutionAdmission.resolveC(execution_context.cncfCore.scope, request).flatMap(
      execution => process_exec_c(execution)
    )

  protected final def process_exec_or_throw(
    request: ProcessExecutionRequest
  )(using uow: UnitOfWork): ProcessExecutionResult =
    process_exec_c(request).TAKE

  /** Creates a Process Execution intent in the canonical UnitOfWork algebra. The input is already
    * capability-admitted; components never select a host executable or invoke a driver directly.
   */
  protected final def process_exec(
    execution: ResolvedProcessExecution
  ): ExecUowM[ProcessExecutionResult] =
    ConsequenceT.liftF(Free.liftF(_op_process_exec(execution)))

  protected final def process_exec_c(
    execution: ResolvedProcessExecution
  )(using uow: UnitOfWork): Consequence[ProcessExecutionResult] =
    exec_c(_op_process_exec(execution))

  protected final def process_exec_or_throw(
    execution: ResolvedProcessExecution
  )(using uow: UnitOfWork): ProcessExecutionResult =
    exec_or_throw(_op_process_exec(execution))

  private def _op_process_exec(
    execution: ResolvedProcessExecution
  ): UnitOfWorkOp[ProcessExecutionResult] =
    UnitOfWorkOp.ProcessExec(execution)
}

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
      .flatMap(_.service_operation_model(action, core))
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
    if (_aggregate_has_entity_collection(aggregatename))
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
    else
      Consequence.unit

  private def _aggregate_load_record(
      aggregatename: String
  )(id: EntityId): Consequence[Option[Record]] =
    component.flatMap(_aggregate_entity_collection_name(_, aggregatename)).flatMap(name =>
      component.flatMap(_.entitySpace.entityOption[Any](name))
    ) match {
        case Some(collection) => _aggregate_load_record_from_collection(collection, id)
        case None => Consequence.success(None)
      }

  private def _aggregate_load_record_from_collection(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    id: EntityId
  ): Consequence[Option[Record]] = {
    val effectiveid = _canonical_aggregate_entity_id(collection, id)

    def _to_record_(entity: Any): Consequence[Record] =
      _aggregate_raw_record(effectiveid).map {
        case Some(record) =>
          collection.descriptor.persistent.authorizationRecord(entity, record)
        case None =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }.recover {
        case _ =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }

    collection.resolve(effectiveid).flatMap(x => _to_record_(x).map(Some(_))).recoverWith {
      case conclusion if _is_aggregate_record_not_found(conclusion) =>
        given EntityPersistent[Any] =
          collection.descriptor.persistent.asInstanceOf[EntityPersistent[Any]]
        EntityStore.standard().load[Any](effectiveid)(using
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

  private def _canonical_aggregate_entity_id(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    id: EntityId
  ): EntityId = {
    val cid = collection.descriptor.collectionId
    if (id.collection == cid)
      id
    else
      EntityId(id.major, id.minor, cid, id.timestamp, id.entropy)
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

  private def _aggregate_has_entity_collection(
      aggregatename: String
  ): Boolean =
    component.flatMap(c =>
      _aggregate_entity_collection_name(c, aggregatename).flatMap(c.entitySpace.entityOption[Any])
    ).isDefined

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
        collection.createRecordSynced(
          _aggregate_canonical_root_record(collection, record)
        )(using execution_context).map { _ =>
          component.foreach(_.viewSpace.invalidate(entityname))
        }
      case None => Consequence.argumentInvalid(s"$entityname entity collection is not available")
    }

  private def _aggregate_save_record_authorized_c(
      entityname: String,
      record: Record,
      expectation: EntityMutationExpectation
  ): Consequence[EntityRecordSnapshot] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        collection.saveRecordVersioned(
          _aggregate_canonical_root_record(collection, record),
          expectation
        )(using execution_context).map { snapshot =>
          component.foreach(_.viewSpace.invalidate(entityname))
          snapshot
        }.recoverWith { conclusion =>
          if (
            ConclusionDiagnostics.classify(conclusion).reason
              .contains("committed-entity-projection-failure")
          )
            component.foreach(_.viewSpace.invalidate(entityname))
          Consequence.Failure(conclusion)
        }
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private def _aggregate_root_snapshot_c(
      entityname: String,
      targetid: EntityId
  ): Consequence[EntitySnapshot[Any]] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        execution_context.entityStoreSpace
          .loadSnapshot(
            _canonical_aggregate_entity_id(collection, targetid),
            collection.descriptor.persistent
          )(using execution_context)
          .flatMap(snapshot =>
            Consequence.successOrEntityNotFound(snapshot)(targetid)
          )
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private def _aggregate_command_target_c[
      A <: org.goldenport.record.RecordPresentable
  ](
      entityname: String,
      targetid: EntityId
  ): Consequence[(A, EntitySnapshot[Any])] =
    for {
      snapshot <- _aggregate_root_snapshot_c(entityname, targetid)
      aggregate <- component
        .map(_.aggregateSpace)
        .getOrElse(Consequence.uninitializedState.RAISE)
        .resolve_with_context[A](targetid)(using execution_context)
      _ <- _validate_aggregate_root_snapshot(
        entityname,
        targetid,
        aggregate,
        snapshot
      )
    } yield aggregate -> snapshot

  private def _validate_aggregate_root_snapshot[
      A <: org.goldenport.record.RecordPresentable
  ](
      entityname: String,
      targetid: EntityId,
      aggregate: A,
      snapshot: EntitySnapshot[Any]
  ): Consequence[Unit] =
    component.flatMap(_.entitySpace.entityOption[Any](entityname)) match {
      case Some(collection) =>
        val rootrecord =
          SimpleEntityStorageShapePolicy.withoutManagedFields(
            _aggregate_canonical_root_record(
              collection,
              collection.descriptor.persistent.toRecord(snapshot.entity)
            )
          )
        val aggregaterecord =
          SimpleEntityStorageShapePolicy.withoutManagedFields(
            _aggregate_canonical_root_record(collection, aggregate.toRecord())
          )
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
      case None =>
        Consequence.argumentInvalid(
          s"$entityname entity collection is not available"
        )
    }

  private def _aggregate_canonical_root_record(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    record: Record
  ): Record =
    record.getString("id")
      .flatMap(value => EntityId.parse(value).toOption)
      .map { id =>
        val canonicalid = _canonical_aggregate_entity_id(collection, id)
        record.upsertSingle("id", canonicalid)
      }
      .getOrElse(record)

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
      expectation: EntityMutationExpectation,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from_calltree(
      "uow:aggregate:update",
      _aggregate_calltree_attributes("update", entityName) + (
        "command"   -> commandName,
        "entity_id" -> targetId.print
      )
    ) {
      aggregate_update_c(entityName, targetId, commandName, expectation, action)
    }

  protected final def aggregate_update_c[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    targetId: EntityId,
    commandName: String,
      expectation: EntityMutationExpectation,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "update",
      aggregatename = entityName,
      targetid = Some(targetId),
      commandname = Some(commandName)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_update(entityName, targetId, commandName)
        }
        aggregate <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          action
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          _aggregate_save_record_authorized_c(
            entityName,
            aggregate.toRecord(),
            expectation
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
      _aggregate_calltree_attributes("command", aggregateName) + (
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
    _aggregate_chokepoint[A](
      operation = "command",
      aggregatename = aggregateName,
      targetid = Some(targetId),
      commandname = Some(commandName)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_update(aggregateName, targetId, commandName)
        }
        resolved <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          _aggregate_command_target_c[A](aggregateName, targetId)
        }
        (aggregate, snapshot) = resolved
        updated <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          command(aggregate)
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          if (aggregate.toRecord() == updated.toRecord())
            Consequence.unit
          else
            _aggregate_save_record_authorized_c(
              aggregateName,
              updated.toRecord(),
              EntityMutationExpectation(snapshot.token)
            ).map(_ => ())
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

trait ActionCallBrowserPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def browser =
    component.map(_.viewSpace).getOrElse(Consequence.uninitializedState.RAISE)

  protected final def view_load[A](
      collectionName: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from(view_load_c[A](collectionName, id))

  protected final def view_load_c[A](
      collectionName: String,
    id: EntityId
  ): Consequence[A] =
    consequence_with_calltree(
      "uow:view:load",
      _view_calltree_attributes("load", collectionName) + ("entity_id" -> id.print)
    ) {
      browser.browser[A](collectionName).find_with_context(id)(using execution_context)
    }

  protected final def view_load_or_throw[A](
      collectionName: String,
    id: EntityId
  ): A =
    view_load_c[A](collectionName, id).TAKE

  protected final def view_load[A](
      collectionName: String,
      viewName: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from(view_load_c[A](collectionName, viewName, id))

  protected final def view_load_c[A](
      collectionName: String,
      viewName: String,
    id: EntityId
  ): Consequence[A] =
    consequence_with_calltree(
      "uow:view:load",
      _view_calltree_attributes("load", collectionName, Some(viewName)) + ("entity_id" -> id.print)
    ) {
      browser.browser[A](collectionName, viewName).find_with_context(id)(using execution_context)
    }

  protected final def view_load_or_throw[A](
      collectionName: String,
      viewName: String,
    id: EntityId
  ): A =
    view_load_c[A](collectionName, viewName, id).TAKE

  protected final def view_search[A](
      collectionName: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from(view_search_c[A](collectionName, q))

  protected final def view_search_c[A](
      collectionName: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    consequence_with_calltree(
      "uow:view:search",
      _view_calltree_attributes("search", collectionName)
    ) {
      browser.browser[A](collectionName).query_with_context(q)(using execution_context).map(
        _to_search_result(q, _)
      )
    }

  protected final def view_search_or_throw[A](
      collectionName: String,
    q: Query[?]
  ): SearchResult[A] =
    view_search_c[A](collectionName, q).TAKE

  protected final def view_search[A](
      collectionName: String,
      viewName: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from(view_search_c[A](collectionName, viewName, q))

  protected final def view_search_c[A](
      collectionName: String,
      viewName: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    consequence_with_calltree(
      "uow:view:search",
      _view_calltree_attributes("search", collectionName, Some(viewName))
    ) {
      browser.browser[A](collectionName, viewName).query_with_context(q)(using execution_context)
        .map(_to_search_result(q, _))
    }

  protected final def view_search_or_throw[A](
      collectionName: String,
      viewName: String,
    q: Query[?]
  ): SearchResult[A] =
    view_search_c[A](collectionName, viewName, q).TAKE

  private def _to_search_result[A](
    q: Query[?],
    xs: Vector[A]
  ): SearchResult[A] =
    SearchResult(
      query = q,
      data = xs,
      totalCount = Some(xs.size),
      offset = q.offset,
      limit = q.limit,
      fetchedCount = xs.size
    )

  private def _view_calltree_attributes(
    operation: String,
    collectionname: String,
    viewname: Option[String] = None
  ): Map[String, String] =
    Map(
      "dsl" -> "uow",
      "operation" -> operation,
      "view" -> collectionname
    ) ++ viewname.map(x => Map("view_name" -> x)).getOrElse(Map.empty)
}

trait BehaviorHttpPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>

  // Declarative DSL (UoW / Free)
  protected final def http_get(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_get(path, headers, properties)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_post(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_post(path, body, headers, properties)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_post_bag(
    path: String,
    body: Option[org.goldenport.bag.Bag] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_post_bag(path, body, headers, properties)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_put(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_put(path, body, headers, properties)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_get_c(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_get(path, headers, properties)
    exec_c(op)
  }

  protected final def http_get_or_throw(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_get(path, headers, properties)
    exec_or_throw(op)
  }

  protected final def http_post_c(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_post(path, body, headers, properties)
    exec_c(op)
  }

  protected final def http_post_or_throw(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_post(path, body, headers, properties)
    exec_or_throw(op)
  }

  protected final def http_put_c(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_put(path, body, headers, properties)
    exec_c(op)
  }

  protected final def http_put_or_throw(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_put(path, body, headers, properties)
    exec_or_throw(op)
  }

  // Private helpers to build UnitOfWorkOp
  private def _op_http_get(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpGet(path, headers, properties)

  private def _op_http_post(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPost(path, body, headers, properties)

  private def _op_http_post_bag(
    path: String,
    body: Option[org.goldenport.bag.Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPostBag(path, body, headers, properties)

  private def _op_http_put(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPut(path, body, headers, properties)
}

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
    informationid: InformationId,
    workingdata: Record
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:update", _information_attributes("update", informationid)) {
      _information_space.flatMap(_.updateInformation(informationid, workingdata)(using
      execution_context))
    }

  protected final def information_append_field_event(
    informationid: InformationId,
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
    informationid: InformationId,
    events: Vector[InformationFieldEvent]
  ): ExecUowM[Unit] =
    events.foldLeft(exec_pure(())) { (z, event) =>
      z.flatMap { _ =>
        information_append_field_event(informationid, event).map(_ => ())
      }
    }

  protected final def information_validate(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:validate",
      _information_attributes("validate", informationid)
    ) {
      _information_space.flatMap(_.validateInformation(informationid)(using execution_context))
    }

  protected final def information_confirm(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree(
      "uow:information:confirm",
      _information_attributes("confirm", informationid)
    ) {
      _information_space.flatMap(_.confirmInformation(informationid)(using execution_context))
    }

  protected final def information_reject(
    informationid: InformationId,
    reason: String
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reject", _information_attributes("reject", informationid)) {
      _information_space.flatMap(_.rejectInformation(informationid, reason)(using
      execution_context))
    }

  protected final def information_reopen(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reopen", _information_attributes("reopen", informationid)) {
      _information_space.flatMap(_.reopenInformation(informationid)(using execution_context))
    }

  protected final def information_publish(
    informationid: InformationId,
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
    informationid: InformationId,
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
    informationid: InformationId,
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
    informationid: InformationId,
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
    informationid: InformationId,
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
    informationid: InformationId
  ): ExecUowM[Option[Information]] =
    exec_from_calltree("uow:information:option", _information_attributes("option", informationid)) {
      _information_space.map(_.getInformation(informationid))
    }

  protected final def information_validation_issues(
    informationid: InformationId
  ): ExecUowM[Vector[InformationValidationIssue]] =
    exec_from_calltree(
      "uow:information:validation-issues",
      _information_attributes("validation-issues", informationid)
    ) {
      _information_space.map(_.validationIssues(informationid))
    }

  protected final def information_add_conflict(
    informationid: InformationId,
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
    informationid: InformationId,
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
    informationid: InformationId
  ): Map[String, String] =
    Map("operation" -> operation, "information_id" -> informationid.print)

  private def _information_candidate_attributes(
    operation: String,
    informationid: InformationId,
    candidatekey: String
  ): Map[String, String] =
    Map(
      "operation"      -> operation,
      "information_id" -> informationid.print,
      "candidate_key"  -> candidatekey
    )
}

trait ActionCallHttpPart extends BehaviorHttpPart with ActionCallFeaturePart {
  self: ActionCall.Core.Holder =>
}

trait ProviderBehaviorFeaturePart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def provider_config_string(
    key: String,
    default: String
  ): String =
    executionContext.runtime.resolvedParameters.get(key)
      .map(_.value)
      .flatMap(_configuration_string)
      .getOrElse(default)

  protected final def provider_config_int(
    key: String,
    default: Int
  ): Int =
    executionContext.runtime.resolvedParameters.get(key)
      .map(_.value)
      .flatMap(_configuration_string)
      .flatMap(_.toIntOption)
      .getOrElse(default)

  protected final def provider_step[A](
    label: String,
    attributes: Map[String, String] = Map.empty
  )(
    body: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(_provider_step_consequence(label, attributes)(body))

  private def _provider_step_consequence[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val calltree = execution_context.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "provider-step"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "failure",
            "error" -> Option(e.getMessage).getOrElse(e.getClass.getName)
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _configuration_string(
    value: ConfigurationValue
  ): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }
}

trait ActionCallBlobPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def blob_normalize_inline_images(
    content: InlineImageContent
  ): ExecUowM[InlineImageNormalizeResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.BlobNormalizeInlineImages(content)))

  protected final def blob_attach_inline_images(
    sourceEntityId: String,
    occurrences: Vector[InlineImageOccurrence]
  ): ExecUowM[InlineImageAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.BlobAttachInlineImages(sourceEntityId, occurrences)))

  protected final def content_normalize_references(
    content: ContentReferenceContent
  ): ExecUowM[ContentReferenceNormalizeResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentNormalizeReferences(content)))

  protected final def content_attach_references(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[ContentReferenceAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentAttachReferences(sourceEntityId, references)))

  protected final def content_validate_references(
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[Unit] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentValidateReferences(references)))

  protected final def content_sync_inline_references(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[ContentReferenceAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentSyncInlineReferences(
      sourceEntityId,
      references
    )))

  protected final def content_render_html(
    content: ContentAttributes
  ): ExecUowM[ContentRenderResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentRenderHtml(content)))
}

trait ActionCallEntityStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  private def _emit_entity_access(
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

  private def _entity_load_attributes(
    id: EntityId,
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> id.collection.name,
    "id" -> id.value,
    "source" -> source,
    "outcome" -> outcome
  )

  private def _entity_search_attributes(
    query: EntityQuery[?],
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> query.collection.name,
    "source" -> source,
    "outcome" -> outcome,
    "query" -> query.query.toString
  )

  private def _entity_search_working_set_loading_attributes(
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

  private def _is_entity_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

  private def _canonical_entity_id(
    id: EntityId
  ): EntityId =
    component
      .flatMap(_.entitySpace.entityOption[Any](id.collection.name))
      .map { collection =>
        val cid = collection.descriptor.collectionId
        if (id.collection == cid)
          id
        else
          EntityId(id.major, id.minor, cid, id.timestamp, id.entropy)
      }
      .getOrElse(id)

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

  protected final def entity_load_option[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    ensure_component_application_datastore()
    val effectiveid = _canonical_entity_id(id)
    _emit_entity_access(
      "entity.load.start",
      _entity_load_attributes(effectiveid, "unknown", "start")
    )
    val effectivetc = _effective_entity_persistent(effectiveid.collection, tc)
    if (!_working_set_enabled) {
      _emit_entity_access(
        "entity.load.bypass.entity-space",
        _entity_load_attributes(effectiveid, "entity-space", "bypass")
      )
      val op = UnitOfWorkOp.EntityStoreLoad(
        effectiveid,
        effectivetc,
        _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "read"),
        _declared_visibility_scope
      )
      return ConsequenceT.liftF(Free.liftF(op))
    }
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
            exec_from(_authorize_entity_load_hit(effectiveid, entity, effectivetc))
          case Consequence.Failure(conclusion) if _is_entity_not_found(conclusion) =>
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
          _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "read"),
          _declared_visibility_scope
        )
        ConsequenceT.liftF(Free.liftF(op))
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
    val effectiveid = _canonical_entity_id(id)
    val effectivetc = _effective_entity_persistent(effectiveid.collection, tc)
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
    val effectiveid = _canonical_entity_id(id)
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreLoadDirect(
      effectiveid,
      _effective_entity_persistent(effectiveid.collection, tc)
    )))
  }

  protected final def entity_save[T](
      entity: T,
      expectation: EntityMutationExpectation
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectation,
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
      entity: T,
      expectation: EntityMutationExpectation
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
      expectation,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
      changes: T,
      expectation: EntityMutationExpectation
  )(using tc: EntityPersistent[T]): ExecUowM[EntitySnapshot[T]] = {
    ensure_component_application_datastore()
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectation,
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
      changes: T,
      expectation: EntityMutationExpectation
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
      expectation,
      effectivetc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Patch update with explicit target id.
  // This is intended for Update.PatchShape where id is excluded from patch object.
  protected final def entity_update[T](
    id: EntityId,
      patch: T,
      expectation: EntityMutationExpectation
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[EntityRecordSnapshot] = {
    ensure_component_application_datastore()
    val effectiveid = _canonical_entity_id(id)
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      effectiveid,
      patch,
      expectation,
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
      patch: T,
      expectation: EntityMutationExpectation
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[EntityRecordSnapshot] = {
    ensure_component_application_datastore()
    val effectiveid = _canonical_entity_id(id)
    val authorization =
      _entity_uow_authorization(
        Some(effectiveid.collection.name),
        Some(effectiveid),
        "update"
      ).map(_.copy(accessMode = EntityAccessMode.ServiceInternal))
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      effectiveid,
      patch,
      expectation,
      tc,
      authorization
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete(id: EntityId): ExecUowM[Unit] = {
    ensure_component_application_datastore()
    val effectiveid = _canonical_entity_id(id)
    val op = UnitOfWorkOp.EntityStoreDelete(
      effectiveid,
      _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "delete")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete_hard(id: EntityId): ExecUowM[Unit] = {
    ensure_component_application_datastore()
    val op = UnitOfWorkOp.EntityStoreDeleteHard(_canonical_entity_id(id))
    ConsequenceT.liftF(Free.liftF(op))
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

  private def _bypass_entity_space_resident_search: Boolean =
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

  private def _working_set_enabled: Boolean =
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

  protected final def entity_search[T](
    collection: EntityCollectionId,
    query: Query[?]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] =
    entity_search[T](EntityQuery(collection, query))

  protected final def entity_load_option_c[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Option[T]] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, _effective_entity_persistent(id.collection, tc))
    exec_c(op)
  }

  protected final def entity_load_option_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Option[T] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, _effective_entity_persistent(id.collection, tc))
    exec_or_throw(op)
  }

  private def _effective_entity_persistent[T](
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

  protected final def entity_save_c[T](
      entity: T,
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[EntitySnapshot[T]] = {
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectation,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_c(op)
  }

  protected final def entity_save_or_throw[T](
      entity: T,
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): EntitySnapshot[T] = {
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      expectation,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
      changes: T,
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[EntitySnapshot[T]] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectation,
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
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): EntitySnapshot[T] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      expectation,
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
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Consequence[EntityRecordSnapshot] = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      id,
      patch,
      expectation,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    id: EntityId,
      patch: T,
      expectation: EntityMutationExpectation
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): EntityRecordSnapshot = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      id,
      patch,
      expectation,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_or_throw(op)
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

  private def _declared_visibility_scope: Option[EntityVisibilityScope] =
    _declared_operation_definition.flatMap(_.visibility.flatMap(EntityVisibilityScope.parseOption))

  private def _with_declared_visibility[T](
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

  private def _entity_uow_authorization(
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
        .flatMap(_.entity_usage_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityoperationkind =
      factory
        .flatMap(_.entity_operation_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityOperationKind).map(EntityOperationKind.parse))
        .orElse(runtimeentitydescriptor.map(_.effectiveOperationKind))
        .getOrElse(
          entityusage match
            case EntityUsageKind.Executable => EntityOperationKind.Task
            case _ => EntityOperationKind.default
        )
    val entityapplicationdomain =
      factory
        .flatMap(_.entity_application_domain(action, entityname, core))
        .orElse(access.flatMap(_.entityApplicationDomain).map(EntityApplicationDomain.parse))
        .orElse(runtimeentitydescriptor.map(_.applicationDomain))
        .getOrElse(
          entityusage match
            case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
            case _ => EntityApplicationDomain.default
        )
    val operationmodel =
      factory
        .flatMap(_.service_operation_model(action, core))
        .orElse(access.flatMap(_.operationModel).map(ServiceOperationModel.parse))
        .getOrElse(ServiceOperationModel.default)
    val explicitrelations =
      factory
        .map(_.entity_access_relations(action, entityname, accesskind, core))
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
        .flatMap(_.entity_access_mode(action, entityname, accesskind, core))
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

  private def _entity_create_options(
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
        .flatMap(_.entity_usage_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityapplicationdomain =
      factory
        .flatMap(_.entity_application_domain(action, entityname, core))
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

trait ActionCallDataStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  // // Legacy direct datastore access (pre-UoW DSL)
  // protected final def ds_get(id: UniversalId): Option[DataStore.Record] = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.load(id)
  // }

  // protected final def ds_put(id: UniversalId, record: DataStore.Record): Unit = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.store(id, record)
  // }

  // protected final def ds_delete(id: UniversalId): Unit = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.delete(id)
  // }

  // Declarative DSL (UoW / Free)
  protected final def store_load(id: UniversalId): ExecUowM[Option[Record]] = {
    val op = _op_store_load(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_save(id: UniversalId, record: Record): ExecUowM[Unit] = {
    val op = _op_store_save(id, record)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_update(id: UniversalId, record: Record): ExecUowM[Unit] = {
    val op = _op_store_update(id, record)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_delete(id: UniversalId): ExecUowM[Unit] = {
    val op = _op_store_delete(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_load_c(
    id: UniversalId
  )(using
      uow: UnitOfWork,
      http: org.goldenport.cncf.http.HttpDriver
  ): Consequence[Option[Record]] = {
    val op = _op_store_load(id)
    exec_c(op)
  }

  protected final def store_load_or_throw(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Option[Record] = {
    val op = _op_store_load(id)
    exec_or_throw(op)
  }

  protected final def store_save_c(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_save(id, record)
    exec_c(op)
  }

  protected final def store_save_or_throw(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_save(id, record)
    exec_or_throw(op)
  }

  protected final def store_update_c(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_update(id, record)
    exec_c(op)
  }

  protected final def store_update_or_throw(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_update(id, record)
    exec_or_throw(op)
  }

  protected final def store_delete_c(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_delete(id)
    exec_c(op)
  }

  protected final def store_delete_or_throw(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_delete(id)
    exec_or_throw(op)
  }

  // Private helpers to build UnitOfWorkOp
  private def _op_store_load(id: UniversalId): UnitOfWorkOp[Option[Record]] = {
    // TODO: Implement DataStoreLoad operation
    UnitOfWorkOp.DataStoreLoad(id)
  }

  private def _op_store_save(id: UniversalId, record: Record): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreSave operation
    UnitOfWorkOp.DataStoreSave(id, record)
  }

  private def _op_store_update(id: UniversalId, record: Record): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreUpdate operation (currently routed to save op).
    UnitOfWorkOp.DataStoreSave(id, record)
  }

  private def _op_store_delete(id: UniversalId): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreDelete operation
    UnitOfWorkOp.DataStoreDelete(id)
  }
}

trait ActionCallEmbeddedDataStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>

  protected final def component_local_data_dir: ExecUowM[Path] =
    component_local_data_dir(_embedded_datastore_component_name)

  protected final def component_local_data_dir(
    componentName: String
  ): ExecUowM[Path] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.LocalDataDir(componentName)))

  protected final def embedded_datastore(
    name: String
  ): ExecUowM[EmbeddedDataStore] =
    embedded_datastore(_embedded_datastore_component_name, name)

  protected final def embedded_datastore(
    componentName: String,
    name: String
  ): ExecUowM[EmbeddedDataStore] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EmbeddedDataStoreOpen(componentName, name)))

  protected final def embedded_datastore_read(
    store: EmbeddedDataStore,
    statement: String,
    params: Vector[Any] = Vector.empty
  ): ExecUowM[Vector[Record]] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EmbeddedDataStoreRead(
      store,
      EmbeddedStatement(statement, params)
    )))

  protected final def embedded_datastore_update(
    store: EmbeddedDataStore,
    statement: String,
    params: Vector[Any] = Vector.empty
  ): ExecUowM[EmbeddedUpdateResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EmbeddedDataStoreUpdate(
      store,
      EmbeddedStatement(statement, params)
    )))

  protected final def embedded_datastore_migrate(
    store: EmbeddedDataStore,
    statements: Vector[String]
  ): ExecUowM[Unit] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EmbeddedDataStoreMigrate(store, statements)))

  private def _embedded_datastore_component_name: String =
    component_name_option
      .orElse(action.request.component)
      .getOrElse("component")
}

trait ActionCallShellCommandPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>

  protected final def shell_exec(
    command: ShellCommand
  ): ExecUowM[ShellCommandResult] = {
    val op = _op_shell_command_exec(command)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def shell_exec_c(
    command: ShellCommand
  )(using uow: UnitOfWork): Consequence[ShellCommandResult] = {
    val op = _op_shell_command_exec(command)
    exec_c(op)
  }

  protected final def shell_exec_or_throw(
    command: ShellCommand
  )(using uow: UnitOfWork): ShellCommandResult = {
    val op = _op_shell_command_exec(command)
    exec_or_throw(op)
  }

  private def _op_shell_command_exec(
    command: ShellCommand
  ): UnitOfWorkOp[ShellCommandResult] =
    UnitOfWorkOp.ShellCommandExec(command)
}
