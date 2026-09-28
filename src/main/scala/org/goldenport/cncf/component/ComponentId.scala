package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.component.identity.{
  ComponentId => SharedComponentId,
  ComponentIdentityResult,
  ComponentInstanceId => SharedComponentInstanceId
}
import org.goldenport.record.Record
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.logic.ProtocolLogic
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.protocol.spec.ServiceDefinitionGroup
import org.goldenport.protocol.service.{Service => ProtocolService}
// import org.goldenport.cncf.action.ActionLogic
import org.goldenport.protocol.handler.*
import org.goldenport.protocol.handler.ingress.*
import org.goldenport.protocol.handler.egress.*
import org.goldenport.protocol.handler.projection.*
import java.nio.file.Path
import scala.reflect.ClassTag
import org.goldenport.cncf.context.{CorrelationId, EntitySpaceContext, ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, AggregateBehavior, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.http.{HttpDriver, WebMessageCatalog, WebPageContextProvider}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterBootstrap, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}
import org.goldenport.cncf.cli.renderer.{CliTreeJsonRenderer, CliTreeYamlRenderer}
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateEditContextSpace, AggregateSpace, Repository, AggregateDefinition}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntitySpace}
import org.goldenport.cncf.entity.view.{Browser, ViewCollection, ViewSpace, ViewDefinition}
import org.goldenport.cncf.information.InformationSpace
import org.goldenport.cncf.knowledge.KnowledgeSpace
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.statemachine.{CmlStateMachineDefinition, StateMachinePlannerProvider}
import org.goldenport.cncf.event.{CmlEventDefinition, CmlRoutingDefinition, CmlSubscriptionDefinition, EventReception, EventReceptionRule, EventStore}
import org.goldenport.cncf.security.AuthenticationProvider
import org.goldenport.cncf.messagedelivery.MessageDeliveryProvider
import org.goldenport.cncf.usernotification.UserNotificationProvider
import org.goldenport.cncf.projection.{HelpProjection, DescribeProjection, SchemaProjection, OpenApiProjection, McpProjection, TreeProjection, StateMachineProjection}
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.workflow.{ContinuationRuntime, ContinuationRuntimeSource, GeneratedProvidedApiAbi, GeneratedWorkflowAbi, StateMachineProvidedApiDispatcher, StateMachineProviderResolver, StateMachineProviderSource}
import cats.data.NonEmptyVector
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.Properties
import scala.deprecatedName
import scala.util.control.NonFatal
import org.goldenport.schema.{DataType, XString}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentId private (
  val sharedIdentity: SharedComponentId
) {
  def name: String = sharedIdentity.qualifiedName()

  def namespace = sharedIdentity.namespace()

  def localId = sharedIdentity.localId()

  override def equals(other: Any): Boolean =
    other match {
      case that: ComponentId => sharedIdentity.equals(that.sharedIdentity)
      case _ => false
    }

  override def hashCode(): Int = sharedIdentity.hashCode()

  override def toString: String = sharedIdentity.toString()
}

object ComponentId {
  def parseC(qualifiedId: String): Consequence[ComponentId] =
    _parse_strict_c(qualifiedId)

  def apply(qualifiedId: String): ComponentId =
    _require(SharedComponentId.parse(qualifiedId))(_from_shared)

  private[component] def _from_shared_component_id(sharedidentity: SharedComponentId): ComponentId =
    _from_shared(sharedidentity)

  private def _from_shared(sharedidentity: SharedComponentId): ComponentId =
    new ComponentId(sharedidentity)

  private def _parse_strict_c(qualifiedid: String): Consequence[ComponentId] =
    _to_consequence(SharedComponentId.parse(qualifiedid))(_from_shared)

  private def _to_consequence[A, B](
    result: ComponentIdentityResult[A]
  )(f: A => B): Consequence[B] =
    if (result.isSuccess())
      Consequence(f(result.value().get()))
    else
      Consequence.componentInvalid(_error_message(result.error().get()))

  private def _require[A, B](
    result: ComponentIdentityResult[A]
  )(f: A => B): B =
    if (result.isSuccess())
      f(result.value().get())
    else
      throw new IllegalArgumentException(_error_message(result.error().get()))

  private def _error_message(error: ComponentIdentityResult.Error): String =
    s"${error.code()}: ${error.message()}"
}
