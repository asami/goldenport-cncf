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
final case class ComponentCreate(
  private[cncf] assembly: ComponentAssemblyContext,
  origin: ComponentOrigin,
  componentDescriptors: Vector[ComponentDescriptor] = Vector.empty,
  instanceMetadata: Option[ComponentInstanceMetadata] = None,
  assemblyApiClassLoader: Option[ClassLoader] = None
) {
  private[cncf] def subsystem: Subsystem = assembly.subsystem

  private[cncf] def runtimeConfiguration: ResolvedConfiguration = assembly.runtimeConfiguration

  /** Read-only runtime configuration available to external component factories. */
  def configuration: ResolvedConfiguration = assembly.subsystem.configuration

  def withOrigin(p: ComponentOrigin) = copy(origin = p)

  def withComponentDescriptors(p: Vector[ComponentDescriptor]) =
    copy(componentDescriptors = p)

  def withInstanceMetadata(p: ComponentInstanceMetadata) =
    copy(instanceMetadata = Some(p))

  def withAssemblyApiClassLoader(p: ClassLoader) =
    copy(assemblyApiClassLoader = Some(p))

  private[cncf] def withRuntimeConfiguration(p: ResolvedConfiguration) =
    copy(assembly = ComponentAssemblyContext(subsystem, p))

  def toInit(core: Component.Core): ComponentInit =
    ComponentInit(assembly, core, origin, componentDescriptors, instanceMetadata = instanceMetadata)
}

object ComponentCreate {
  def apply(
    subsystem: Subsystem,
    origin: ComponentOrigin
  ): ComponentCreate =
    apply(subsystem, origin, Vector.empty)

  def apply(
    subsystem: Subsystem,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor]
  ): ComponentCreate =
    apply(subsystem, origin, componentDescriptors, None)

  def apply(
    subsystem: Subsystem,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor],
    instanceMetadata: Option[ComponentInstanceMetadata]
  ): ComponentCreate =
    apply(subsystem, origin, componentDescriptors, instanceMetadata, None)

  def apply(
    subsystem: Subsystem,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor],
    instanceMetadata: Option[ComponentInstanceMetadata],
    assemblyApiClassLoader: Option[ClassLoader]
  ): ComponentCreate =
    ComponentCreate(
      ComponentAssemblyContext(subsystem),
      origin,
      componentDescriptors,
      instanceMetadata,
      assemblyApiClassLoader
    )
}
