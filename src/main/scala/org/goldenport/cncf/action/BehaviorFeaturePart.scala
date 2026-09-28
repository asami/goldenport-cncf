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
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
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
