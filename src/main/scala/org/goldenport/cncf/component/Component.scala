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
 * @since   Jan.  1, 2026
 *  version Jan. 22, 2026
 *  version Feb. 17, 2026
 *  version Mar. 30, 2026
 *  version Apr. 30, 2026
 *  version May. 20, 2026
 *  version Jun. 18, 2026
 *  version Aug. 13, 2026
 *  version Aug. 31, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Component() extends Component.Core.Holder {
  private var _core: Option[Component.Core] = None
  private var _origin: Option[ComponentOrigin] = None
  private var _participant_role: Component.ParticipantRole = Component.ParticipantRole.Primary
  private var _application_config: Component.ApplicationConfig = Component.ApplicationConfig()
  private var _collaborator_classpath: Option[Vector[Path]] = None
//  private var _system_context: SystemContext = SystemContext.empty
  private var _parent_scope_context: Option[ScopeContext] = None
  private var _component_context: Option[Component.Context] = None
  private var _services: Option[ServiceGroup] = None
  private var _subsystem: Option[Subsystem] = None
  private var _health_contributors: Vector[Component.HealthContributor] = Vector.empty
  private var _state_machine_planner_provider: StateMachinePlannerProvider =
    StateMachinePlannerProvider.noop
  private var _state_machine_definitions: Vector[CmlStateMachineDefinition] =
    Vector.empty
  private var _admitted_generated_workflow_metadata: Vector[GeneratedWorkflowAbi.Definition] =
    Vector.empty
  private var _admitted_generated_provided_api_metadata: Vector[GeneratedProvidedApiAbi.Definition] =
    Vector.empty
  private var _state_machine_provided_api_dispatcher: StateMachineProvidedApiDispatcher =
    StateMachineProvidedApiDispatcher.empty
  private var _state_machine_provider_resolver: StateMachineProviderResolver =
    StateMachineProviderResolver.empty
  private var _continuation_runtime: ContinuationRuntime = ContinuationRuntime.empty
  private var _working_set_entity_names: Set[String] = Set.empty
  private var _artifact_metadata: Option[Component.ArtifactMetadata] = None
  private var _event_reception: Option[EventReception] = None
  private var _event_store: Option[EventStore] = None
  private var _port: Component.Port = Component.Port.empty
  private var _bindings: Map[String, Component.Binding[?, ?]] = Map.empty
  private var _event_effect_record: Record = Record.empty
  private var _component_descriptors: Vector[ComponentDescriptor] = Vector.empty
  private var _instance_metadata: Option[ComponentInstanceMetadata] = None
  private var _initialization_parameters: ComponentInitializationParameters =
    ComponentInitializationParameters.empty
  private var _collections_bootstrapped: Boolean = false
  private var _mcp_ready_services: Set[String] = Set.empty
  private var _mcp_ready_operations: Set[String] = Set.empty
  val entitySpace: EntitySpace = new EntitySpace()
  val aggregateSpace: AggregateSpace = new AggregateSpace()
  val aggregateEditContextSpace: AggregateEditContextSpace = new AggregateEditContextSpace()
  val viewSpace: ViewSpace = new ViewSpace()
  val knowledgeSpace: KnowledgeSpace = new KnowledgeSpace()
  val informationSpace: InformationSpace = new InformationSpace(this)

  override def core: Component.Core =
    _core.getOrElse(throw new IllegalStateException("Component core is not initialized."))

  def coreOption: Option[Component.Core] =
    _core

  /**
   * Stable human-facing component label. Runtime identity is always componentId.
   */
  def displayName: String =
    componentId.localId.value()

  def factoryOption: Option[Component.Factory] =
    _core.flatMap(_.factory)

  def origin: ComponentOrigin =
    _origin.getOrElse(ComponentOrigin.Unknown)

  def participantRole: Component.ParticipantRole =
    _participant_role

  def isPrimaryParticipant: Boolean =
    _participant_role == Component.ParticipantRole.Primary

  def isComponentletParticipant: Boolean =
    _participant_role == Component.ParticipantRole.Componentlet

  def componentDescriptors: Vector[ComponentDescriptor] =
    _component_descriptors

  def instanceMetadata: Option[ComponentInstanceMetadata] =
    _instance_metadata

  def initializationParameters: ComponentInitializationParameters =
    _initialization_parameters

  def collectionsBootstrapped: Boolean =
    _collections_bootstrapped

  def entityRuntimeDescriptor(
    entityName: String
  ): Option[org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor] = {
    val name = Option(entityName).getOrElse("").trim
    (_component_descriptors ++ componentDescriptors).distinct.iterator.flatMap(_.entityRuntimeDescriptors).find { d =>
      NamingConventions.equivalentByNormalized(d.entityName, name) ||
      NamingConventions.equivalentByNormalized(d.collectionId.name, name)
    }
  }

  def withComponentDescriptors(
    descriptors: Vector[ComponentDescriptor]
  ): Component = {
    _component_descriptors = descriptors
    this
  }

  def withCollectionsBootstrapped(
    value: Boolean = true
  ): Component = {
    _collections_bootstrapped = value
    this
  }

  def services: ServiceGroup = _services.getOrElse {
    throw new IllegalStateException("Component does not initialized.")
  }

  lazy val receptors: ReceptorGroup = ReceptorGroup.empty // TODO

  lazy val logic: ComponentLogic = ComponentLogic(this)

  def initialize(params: ComponentInit): Component =
    _or_raise(initializeC(params))

  def initializeC(params: ComponentInit): Consequence[Component] =
    try {
      _core = Some(params.core)
      _origin = Some(params.origin)
      _participant_role = params.participantRole
      _subsystem = Some(params.subsystem)
      _component_descriptors = params.componentDescriptors
      _instance_metadata = params.instanceMetadata
      _initialization_parameters = params.initializationParameters
      _inherit_http_driver(params)
      _event_store = Some(params.subsystem.eventStore)
      jobEngine match {
        case m: InMemoryJobEngine =>
          m.withEventStore(params.subsystem.eventStore)
        case _ =>
          ()
      }
      _services = Some(ServiceGroup(protocol.services.services.map(_to_service)))
      initialize_component_c(params).map(_ => this)
    } catch {
      case NonFatal(e) => Consequence.componentInvalid(e)
    }

  private def _to_service(p: ServiceDefinition): Service = {
    serviceFactory.setup(this)
    p.createService(serviceFactory)
  }

  protected def initialize_Component(params: ComponentInit): Unit = {}

  protected def initialize_component_c(params: ComponentInit): Consequence[Unit] =
    try {
      initialize_Component(params)
      Consequence.unit
    } catch {
      case NonFatal(e) => Consequence.componentInvalid(e)
    }

  private def _or_raise[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
    }

  def service: Service = services.services.head // TODO

  def port: Component.Port = _port

  def componentApiProviders: Vector[org.goldenport.cncf.spi.SpiProvider[?]] =
    Vector.empty

  def binding: Option[Component.Binding[?, ?]] = bindings.headOption.map(_._2)

  def withBinding(binding: Component.Binding[?, ?]): Component =
    withBinding("default", binding)

  def bindings: Map[String, Component.Binding[?, ?]] = _bindings

  def binding(name: String): Option[Component.Binding[?, ?]] =
    _bindings.get(name)

  def withBinding(
    name: String,
    binding: Component.Binding[?, ?]
  ): Component = {
    _bindings = _bindings.updated(name, binding)
    this
  }

  def clearBindings(): Component = {
    _bindings = Map.empty
    this
  }

  def installBinding[Req, S](
    name: String,
    req: Req
  )(using ExecutionContext): Consequence[Component] =
    binding(name) match {
      case Some(m: Component.Binding[?, ?]) =>
        m.asInstanceOf[Component.Binding[Req, S]].install(this, req)
      case None =>
        Consequence.serviceUnavailable(s"binding not found: $name")
    }

  @deprecated("Use installBinding.", "0.5.2")
  def install_binding[Req, S](
    name: String,
    req: Req
  )(using ExecutionContext): Consequence[Component] =
    installBinding(name, req)

  def withPort(port: Component.Port): Component = {
    _port = port
    this
  }

  def execute(action: Action): Consequence[OperationResponse] = {
    logic.executeAction(action)
  }

  def applicationConfig: Component.ApplicationConfig = _application_config

  def mcpReadyServices: Set[String] = _mcp_ready_services

  def mcpReadyOperations: Set[String] = _mcp_ready_operations

  def withMcpReadyServices(names: Set[String]): Component = {
    _mcp_ready_services = names
    this
  }

  def withMcpReadyOperations(names: Set[String]): Component = {
    _mcp_ready_operations = names
    this
  }

  def isMcpReady(
    serviceName: String,
    operationName: String
  ): Boolean = {
    val servicekey = NamingConventions.toNormalizedSegment(serviceName)
    val operationkey = s"$servicekey.${NamingConventions.toNormalizedSegment(operationName)}"
    val ready =
      mcpReadyServices.exists(x => NamingConventions.toNormalizedSegment(x) == servicekey) ||
        mcpReadyOperations.exists { x =>
          x.split("\\.", 2).toList match {
            case service :: operation :: Nil =>
              NamingConventions.toNormalizedSegment(service) == servicekey &&
                NamingConventions.toNormalizedSegment(operation) == NamingConventions.toNormalizedSegment(operationName)
            case _ => false
          }
        }
    ready && _mcp_publication_enabled &&
      !_mcp_disabled_services.contains(servicekey) &&
      !_mcp_disabled_operations.contains(operationkey)
  }

  def subsystem: Option[Subsystem] = _subsystem

  def collaboratorClasspath: Option[Vector[Path]] = _collaborator_classpath

  def withCollaboratorClasspath(paths: Option[Seq[Path]]): Component = {
    _collaborator_classpath = paths.map(_.toVector)
    this
  }

  def withApplicationConfig(ac: Component.ApplicationConfig): Component = {
    _application_config = ac
    this
  }

  private def _mcp_publication_enabled: Boolean =
    _mcp_config_value("cncf.mcp.enabled").forall(_.toBooleanOption.getOrElse(false))

  private def _mcp_disabled_services: Set[String] =
    _mcp_config_values("cncf.mcp.disabled-services")

  private def _mcp_disabled_operations: Set[String] =
    _mcp_config_values("cncf.mcp.disabled-operations")

  private def _mcp_config_values(key: String): Set[String] =
    _mcp_config_value(key).toSet.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).map { value =>
      value.split("\\.").map(NamingConventions.toNormalizedSegment).mkString(".")
    }

  private def _mcp_config_value(key: String): Option[String] =
    _application_config.config.flatMap(_.string(key)).map(_.trim).filter(_.nonEmpty)

//  def systemContext: SystemContext = _system_context

  // def withSystemContext(sc: SystemContext): Component = {
  //   _system_context = sc
  //   this
  // }

  def healthContributors: Vector[Component.HealthContributor] = _health_contributors

  def healthSnapshot: Component.HealthSnapshot =
    Component.healthSnapshot(this)

  def registerHealthContributor(contributor: Component.HealthContributor): Component = {
    _health_contributors = _health_contributors :+ contributor
    this
  }

  def stateMachinePlannerProvider: StateMachinePlannerProvider =
    _state_machine_planner_provider

  def withStateMachinePlannerProvider(
    p: StateMachinePlannerProvider
  ): Component = {
    _state_machine_planner_provider = p
    this
  }

  def workingSetEntityNames: Set[String] =
    _working_set_entity_names

  def withWorkingSetEntityNames(
    names: Set[String]
  ): Component = {
    _working_set_entity_names = names
    this
  }

  // Cozy-generated component metadata hooks (event/reception DSL).
  def stateMachineDefinitions: Vector[CmlStateMachineDefinition] =
    _state_machine_definitions

  def withStateMachineDefinitions(
    definitions: Vector[CmlStateMachineDefinition]
  ): Component = {
    _state_machine_definitions = definitions
    this
  }

  def admittedGeneratedWorkflowMetadata: Vector[GeneratedWorkflowAbi.Definition] =
    _admitted_generated_workflow_metadata

  def withAdmittedGeneratedWorkflowMetadata(
    metadata: Vector[GeneratedWorkflowAbi.Definition]
  ): Component = {
    _admitted_generated_workflow_metadata = metadata
    this
  }

  def admittedGeneratedProvidedApiMetadata: Vector[GeneratedProvidedApiAbi.Definition] =
    _admitted_generated_provided_api_metadata

  def withAdmittedGeneratedProvidedApiMetadata(
    metadata: Vector[GeneratedProvidedApiAbi.Definition]
  ): Component = {
    _admitted_generated_provided_api_metadata = metadata
    this
  }

  def stateMachineProvidedApiDispatcher: StateMachineProvidedApiDispatcher =
    _state_machine_provided_api_dispatcher

  def withStateMachineProvidedApiDispatcher(
    dispatcher: StateMachineProvidedApiDispatcher
  ): Component = {
    _state_machine_provided_api_dispatcher = dispatcher
    this
  }

  def stateMachineProviderResolver: StateMachineProviderResolver =
    _state_machine_provider_resolver

  def withStateMachineProviderResolver(
    resolver: StateMachineProviderResolver
  ): Component = {
    _state_machine_provider_resolver = resolver
    this
  }

  def continuationRuntime: ContinuationRuntime =
    _continuation_runtime

  def withContinuationRuntime(
    runtime: ContinuationRuntime
  ): Component = {
    _continuation_runtime = Option(runtime).getOrElse(ContinuationRuntime.empty)
    this
  }

  def eventReceptionDefinitions: Vector[CmlEventDefinition] = Vector.empty
  def eventRoutingDefinitions: Vector[CmlRoutingDefinition] = Vector.empty
  def eventSubscriptionDefinitions: Vector[CmlSubscriptionDefinition] = Vector.empty
  def eventReceptionRuleDefinitions: Vector[EventReceptionRule] = Vector.empty
  def workflowDefinitions: Vector[WorkflowDefinition] = Vector.empty
  def aggregateDefinitions: Vector[AggregateDefinition] = Vector.empty
  def viewDefinitions: Vector[ViewDefinition] = Vector.empty
  def relationshipDefinitions: Vector[CmlEntityRelationshipDefinition] = Vector.empty
  def operationDefinitions: Vector[CmlOperationDefinition] = Vector.empty
  def componentDefinitionRecords: Vector[Record] = Vector.empty
  def subsystemDefinitionRecords: Vector[Record] = Vector.empty
  def authenticationProviders: Vector[AuthenticationProvider] = Vector.empty
  def messageDeliveryProviders: Vector[MessageDeliveryProvider] = Vector.empty
  def userNotificationProviders: Vector[UserNotificationProvider] = Vector.empty
  def webMessageCatalogs: Vector[WebMessageCatalog] = Vector.empty
  def webPageContextProviders: Vector[WebPageContextProvider] = Vector.empty

  def artifactMetadata: Option[Component.ArtifactMetadata] =
    _artifact_metadata

  def withArtifactMetadata(
    metadata: Component.ArtifactMetadata
  ): Component = {
    _artifact_metadata = Some(metadata)
    this
  }

  def eventReception: Option[EventReception] =
    _event_reception

  def withEventReception(
    reception: EventReception
  ): Component = {
    _event_reception = Some(reception)
    this
  }

  def eventStore: Option[EventStore] =
    _event_store

  def withEventStore(
    store: EventStore
  ): Component = {
    _event_store = Some(store)
    this
  }

  def recordEventEffect(
    record: Record
  ): Component = {
    _event_effect_record = record
    this
  }

  def loadEventEffect(): Record =
    _event_effect_record

  def entity[E](name: String): EntityCollection[E] =
    entitySpace.entity(name)

  def aggregate[A](name: String): AggregateCollection[A] =
    aggregateSpace.collection(name)

  def view[V](name: String): ViewCollection[V] =
    viewSpace.collection(name)

  def repository[E](name: String): Repository[E] =
    aggregateSpace.repository(name)

  def browser[V](name: String): Browser[V] =
    viewSpace.browser(name)

  def browser[V](name: String, viewname: String): Browser[V] =
    viewSpace.browser(name, viewname)

  def scopeContext: ScopeContext = {
    val parent = _parent_scope_context getOrElse _default_scope_context()
    _component_context getOrElse {
      val cc = _component_context(parent)
      _component_context = Some(cc)
      cc
    }
  }

  def withScopeContext(sc: ScopeContext): Component = {
    _parent_scope_context = Some(sc)
    _component_context = Some(_component_context(sc))
    this
  }

  private def _inherit_http_driver(
    params: ComponentInit
  ): Unit = {
    if (_application_config.httpDriver.isEmpty) {
      params.subsystem.httpDriver.foreach { driver =>
        _application_config = _application_config.copy(
          httpDriver = Some(driver)
        )
      }
    }
  }

  private def _default_scope_context(): ScopeContext = {
    ScopeContext(
      kind = ScopeKind.Runtime,
      name = "runtime",
      parent = None,
      observabilitycontext = ExecutionContext.create().observability
    )
  }

  private def _component_context(parent: ScopeContext): Component.Context =
    Component.Context(
      name = parent.name,
      parent = parent,
      this,
      componentOrigin = ComponentOrigin.Unknown
    )
}

object Component  extends ComponentMetadataActions {
  private[component] val _default_meta_service_name = "meta"
  private[component] val _default_system_service_name = "system"
  private val _default_repository_type = "component"
  private[component] val _default_unknown_version = "unknown"

  final case class AggregateBehaviorBinding(
    @deprecatedName("operation_name", "0.5.2") operationName: String,
    behavior: AggregateBehavior[?]
  ) {
    @deprecated("Use operationName.", "0.5.2")
    def operation_name: String = operationName
  }

  final case class AggregateCollectionBinding(
    @deprecatedName("aggregate_name", "0.5.2") aggregateName: String,
    collection: AggregateCollection[?]
  ) {
    @deprecated("Use aggregateName.", "0.5.2")
    def aggregate_name: String = aggregateName
  }

  final case class Binding[Req, S](
    port: org.goldenport.cncf.component.Port[Req, S]
  ) {
    def bind(req: Req)(using ExecutionContext): Consequence[S] =
      for {
        contract <- port.api.resolve(req)
        selection <- port.variation.current(req)
        service <- _provide(contract, selection)
      } yield service

    def bind(
      req: Req,
      selection: VariationSelection
    )(using ExecutionContext): Consequence[S] =
      for {
        injected <- port.variation.inject(req, selection)
        service <- bind(injected)
      } yield service

    def install(
      component: Component,
      req: Req
    )(using ExecutionContext): Consequence[Component] =
      bind(req).map { service =>
        component.withPort(Component.Port.of(service).orElse(component.port))
      }

    def install(
      component: Component,
      req: Req,
      selection: VariationSelection
    )(using ExecutionContext): Consequence[Component] =
      bind(req, selection).map { service =>
        component.withPort(Component.Port.of(service).orElse(component.port))
      }

    private def _provide(
      contract: ServiceContract[S],
      selection: VariationSelection
    )(using ExecutionContext): Consequence[S] =
      port.spi.find(_.supports(contract, selection)) match {
        case Some(extensionpoint) =>
          extensionpoint.provide(contract, selection)
        case None =>
          Consequence.serviceUnavailable(
            s"extension point not found for contract=${contract.name}, variation=$selection"
          )
      }
  }

  // private var _script_count = 0
  // private def _script_number(): String = {
  //   val s = if (_script_count == 0) "" else _script_count.toString
  //   _script_count = _script_count + 1
  //   s
  // }

  // private def _create_script_component_name(): String =
  //   s"SCRIPT${_script_number()}"

  final case class Context(
    core: ScopeContext.Core,
    component: Component,
    componentOrigin: ComponentOrigin
  ) extends ScopeContext() {
  }

  final case class ArtifactMetadata(
    sourceType: String,
    name: String,
    version: String,
    component: Option[String] = None,
    subsystem: Option[String] = None,
    archivePath: Option[String] = None,
    effectiveExtensions: Map[String, String] = Map.empty,
    effectiveConfig: Map[String, String] = Map.empty,
    componentId: Option[ComponentId] = None
  )

  object Context {
    def apply(
      name: String,
      parent: ScopeContext,
      component: Component,
      componentOrigin: ComponentOrigin
    ): Context = {
      val contextcore = ScopeContext.Core(
        kind = ScopeKind.Component,
        name = name,
        parent = Some(parent),
        observabilityContext = parent.observabilityContext.createChild(parent, ScopeKind.Component, name),
        httpDriverOption = None,
        entityspace = Some(EntitySpaceContext(component.entitySpace))
      )
      Context(
        core = contextcore,
        component = component,
        componentOrigin = componentOrigin
      )
    }
  }

  def createScriptCore(): org.goldenport.cncf.component.Component.Core =
    createScriptCore(Protocol.empty)

  def createScriptCore(service: ServiceDefinition): org.goldenport.cncf.component.Component.Core =
    createScriptCore(Vector(service))

  def createScriptCore(services: Seq[ServiceDefinition]): org.goldenport.cncf.component.Component.Core =
    createScriptCore(Protocol(services))

  def createScriptCore(services: ServiceDefinitionGroup): org.goldenport.cncf.component.Component.Core =
    createScriptCore(Protocol(services))

  def createScriptCore(protocol: Protocol): org.goldenport.cncf.component.Component.Core = {
    val componentid = ComponentId("org.goldenport.cncf.Script")
    val name = componentid.name
    val instanceid = ComponentInstanceId.default(componentid)
    org.goldenport.cncf.component.Component.Core.create(
      name,
      componentid,
      instanceid,
      protocol
    )
  }

  trait Port {
    def get[T: ClassTag]: Option[T]
    def entries: Vector[Any]
    def inputEntries: Vector[Any] = Vector.empty
    def outputEntries: Vector[Any] = entries
    def orElse(other: Port): Port
  }

  object Port {
    val empty: Port = new Port {
      def get[T: ClassTag]: Option[T] = None
      def entries: Vector[Any] = Vector.empty
      override def inputEntries: Vector[Any] = Vector.empty
      override def outputEntries: Vector[Any] = Vector.empty
      def orElse(other: Port): Port = other
    }

    def of(services: Any*): Port =
      output(services*)

    def input(services: Any*): Port =
      _create(services.toVector, Vector.empty)

    def output(services: Any*): Port =
      _create(Vector.empty, services.toVector)

    private def _create(
      inputs: Vector[Any],
      outputs: Vector[Any]
    ): Port =
      new Port {
        private val _services = inputs ++ outputs
        def get[T: ClassTag]: Option[T] = {
          val clazz = summon[ClassTag[T]].runtimeClass
          _services.collectFirst {
            case service if clazz.isInstance(service) => service.asInstanceOf[T]
          }
        }
        def entries: Vector[Any] = _services
        override def inputEntries: Vector[Any] = inputs
        override def outputEntries: Vector[Any] = outputs
        def orElse(other: Port): Port = Port.combined(this, other)
      }

    def combined(primary: Port, secondary: Port): Port =
      new Port {
        def get[T: ClassTag]: Option[T] =
          primary.get[T].orElse(secondary.get[T])
        def entries: Vector[Any] =
          primary.entries ++ secondary.entries
        override def inputEntries: Vector[Any] =
          primary.inputEntries ++ secondary.inputEntries
        override def outputEntries: Vector[Any] =
          primary.outputEntries ++ secondary.outputEntries
        def orElse(other: Port): Port =
          Port.combined(this, other)
      }
  }

  final case class ApplicationConfig(
    httpDriver: Option[HttpDriver] = None,
    config: Option[org.goldenport.configuration.Configuration] = None
  )

  case class Core(
    name: String,
    componentId: ComponentId,
    instanceId: ComponentInstanceId,
    protocol: Protocol,
    protocolLogic: ProtocolLogic,
    factory: Option[Component.Factory],
    actionEngine: ActionEngine,
    jobEngine: JobEngine
  ) {
    if (componentId == null)
      throw new IllegalArgumentException(
        "component.core.component-id.required: expected qualified component ID; actual qualified component ID: null"
      )
    if (instanceId == null)
      throw new IllegalArgumentException(
        s"component.core.instance-id.required: expected ComponentInstanceId for qualified component ID '${componentId.name}'; actual instance identity: null"
      )
    if (name != componentId.name)
      throw new IllegalArgumentException(
        s"component.core.name.component-id.mismatch: expected qualified component ID '${componentId.name}'; actual supplied name '$name'"
      )
    if (instanceId.componentId != componentId)
      throw new IllegalArgumentException(
        s"component.core.instance.component-id.mismatch: expected qualified component ID '${componentId.name}'; actual instance component ID '${instanceId.componentId.name}'"
      )

    lazy val serviceFactory = factory.map(_.serviceFactory) getOrElse ServiceFactory.empty
  }
  object Core {
    trait Holder {
      def core: Core

      def name = core.name
      def componentId = core.componentId
      def instanceId = core.instanceId
      def protocol = core.protocol
      def protocolLogic = core.protocolLogic
      def factory = core.factory
      def actionEngine = core.actionEngine
      def jobEngine = core.jobEngine
      def serviceFactory = core.serviceFactory
    }

    def create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
      protocol: Protocol
    ): Core = {
      create(name, componentId, instanceId, protocol, InMemoryJobEngine.create())
    }

    def create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
      protocol: Protocol,
      jobEngine: JobEngine
    ): Core = {
      val mergedprotocol = _with_default_services(protocol)
      Core(
        name,
        componentId,
        instanceId,
        mergedprotocol,
        ProtocolLogic(mergedprotocol),
        None,
        ActionEngine.create(),
        jobEngine,
      )
    }

    def create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
      protocol: Protocol,
      factory: Component.Factory
    ): Core = create(
      name,
      componentId,
      instanceId,
      protocol,
      ProtocolLogic(protocol),
      factory,
      ActionEngine.create(),
      InMemoryJobEngine.create(),
    )

    def create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
      protocol: Protocol,
      protocolLogic: ProtocolLogic,
      factory: Component.Factory,
      actionEngine: ActionEngine,
      jobEngine: JobEngine,
    ): Core = {
      val mergedprotocol = _with_default_services(protocol)
      Core(
        name,
        componentId,
        instanceId,
        mergedprotocol,
        ProtocolLogic(mergedprotocol),
        Some(factory),
        actionEngine,
        jobEngine,
      )
    }
  }

  sealed trait ParticipantRole
  object ParticipantRole {
    case object Primary extends ParticipantRole
    case object Componentlet extends ParticipantRole
  }

  final case class Bundle(
    primary: Component,
    componentlets: Vector[Component] = Vector.empty
  ) {
    require(primary != null, "primary component is required")
    require(componentlets.forall(_ != null), "componentlets must not contain null")

    def participants: Vector[Component] =
      primary +: componentlets

    def validate(): Bundle = {
      val names = participants.map(_.name)
      val duplicated = names.groupBy(x => x).collectFirst { case (name, xs) if xs.size > 1 => name }
      require(!componentlets.exists(_ eq primary), "primary component must not appear in componentlets")
      require(duplicated.isEmpty, s"duplicate participant name in bundle: ${duplicated.getOrElse("")}")
      this
    }
  }

  abstract class Factory extends StateMachineProviderSource with ContinuationRuntimeSource {
    def serviceFactory: ServiceFactory = ServiceFactory.empty

    // Internal aggregate-assembly DSL lookup. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def aggregateCollectionBindings(
      comp: Component
    ): Vector[AggregateCollectionBinding] = Vector.empty

    // Internal ActionCall aggregate-selection DSL lookup. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def aggregateBehaviorBindings(
      comp: Component
    ): Vector[AggregateBehaviorBinding] = Vector.empty

    // Internal aggregate reconstruction fallback. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def createAggregateFromRecord(
      entityName: String,
      record: Record,
      default: => Consequence[Any]
    ): Consequence[Any] = default

    // Internal ActionCall aggregate-behavior selection using holder-provided runtime evidence. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def createAggregateBehavior(
      action: Action,
      core: ActionCall.Core
    ): Option[AggregateBehavior[?]] =
      for {
        comp <- core.component
        binding <- aggregateBehaviorBindings(comp)
          .find(_.operationName == action.request.operation)
      } yield binding.behavior

    // Internal ActionCall access-authorization extension point for reviewed Component policy.
    def authorizeOperationAccess(
      action: Action,
      access: CmlOperationAccess,
      core: ActionCall.Core
    ): Option[Consequence[Unit]] = None

    // Internal ActionCall entity-authorization extension point for reviewed Component policy.
    def authorizeOperationEntity(
      action: Action,
      entityName: String,
      core: ActionCall.Core
    ): Option[Consequence[Unit]] = None

    // Internal UnitOfWork authorization extension point for reviewed Component policy.
    def authorizeUnitOfWork(
      authorization: org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization,
      uow: org.goldenport.cncf.unitofwork.UnitOfWork
    ): Option[Consequence[Unit]] = None

    // Internal ActionCall entity-usage resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def entityUsageKind(
      action: Action,
      entityName: String,
      core: ActionCall.Core
    ): Option[org.goldenport.cncf.security.EntityUsageKind] = None

    // Internal ActionCall entity-operation resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def entityOperationKind(
      action: Action,
      entityName: String,
      core: ActionCall.Core
    ): Option[org.goldenport.cncf.security.EntityOperationKind] = None

    // Internal ActionCall entity-domain resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def entityApplicationDomain(
      action: Action,
      entityName: String,
      core: ActionCall.Core
    ): Option[org.goldenport.cncf.security.EntityApplicationDomain] = None

    // Internal ActionCall service-operation-model resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def serviceOperationModel(
      action: Action,
      core: ActionCall.Core
    ): Option[org.goldenport.cncf.security.ServiceOperationModel] = None

    // Internal ActionCall entity-access-mode resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def entityAccessMode(
      action: Action,
      entityName: String,
      accessKind: String,
      core: ActionCall.Core
    ): Option[org.goldenport.cncf.security.EntityAccessMode] = None

    // Internal ActionCall entity-access-relation resolution. Keep final until a concrete Component extension needs a narrower reviewed contract.
    final def entityAccessRelations(
      action: Action,
      entityName: String,
      accessKind: String,
      core: ActionCall.Core
    ): Vector[org.goldenport.cncf.security.EntityAccessRelation] = Vector.empty

    def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] =
      Vector.empty

    def initializationParameterPathRoutes: Vector[ComponentParameterPathRoute] =
      Vector.empty

    final def createPrimary(params: ComponentCreate): Component =
      _or_raise(createPrimaryC(params))

    final def createPrimaryC(params: ComponentCreate): Consequence[Component] =
      _create_participant_c(params, ParticipantRole.Primary)

    final def createComponentlet(params: ComponentCreate): Component =
      _or_raise(createComponentletC(params))

    final def createComponentletC(params: ComponentCreate): Consequence[Component] =
      _create_participant_c(params, ParticipantRole.Componentlet)

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core

    protected def create_Component(params: ComponentCreate): Component

    protected def initialize_component_c(
      component: Component,
      params: ComponentInit
    ): Consequence[Component] =
      component.initializeC(params)

    private def _create_participant_c(
      params: ComponentCreate,
      role: ParticipantRole
    ): Consequence[Component] =
      try {
        val comp = create_Component(params)
        val core = create_Core(params, comp)
        val instanceid = params.instanceMetadata.map { metadata =>
          role match {
            case ParticipantRole.Primary => metadata.instanceId
            case ParticipantRole.Componentlet => ComponentInstanceId(core.name, metadata.instance)
          }
        }.getOrElse(core.instanceId)
        val sharedcore = core.copy(
          instanceId = instanceid,
          jobEngine = params.subsystem.jobEngine
        )
        params.instanceMetadata.filter(_.config.nonEmpty).foreach { metadata =>
          val values = metadata.config.map { case (key, value) =>
            key -> ConfigurationValue.StringValue(value)
          }
          val packagedvalues = comp.applicationConfig.config.map(_.values).getOrElse(Map.empty)
          comp.withApplicationConfig(
            comp.applicationConfig.copy(config = Some(Configuration(packagedvalues ++ values)))
          )
        }
        ComponentParameterBootstrap
          .resolve(
            params.withComponentDescriptors(
              _initialization_parameter_descriptors(params, comp, sharedcore.componentId)
            ),
            sharedcore.componentId,
            sharedcore.instanceId,
            initializationParameterDeclarations,
            initializationParameterPathRoutes
          )
          .flatMap { parameters =>
            initialize_component_c(
              comp,
              ComponentInit(
                subsystem = params.subsystem,
                core = sharedcore,
                origin = params.origin,
                componentDescriptors = params.componentDescriptors,
                participantRole = role,
                instanceMetadata = params.instanceMetadata,
                initializationParameters = parameters
              )
            )
          }
      } catch {
        case NonFatal(e) => Consequence.componentInvalid(e)
      }

    private def _initialization_parameter_descriptors(
      params: ComponentCreate,
      component: Component,
      componentid: ComponentId
    ): Vector[ComponentDescriptor] = {
      val supplied = params.componentDescriptors
      if (supplied.exists(_owns_component(_, componentid)) || supplied.exists(_.isCanonicalIdentity)) supplied
      else supplied ++ _bind_component_owned_descriptor(component.componentDescriptors, componentid)
    }

    private def _bind_component_owned_descriptor(
      descriptors: Vector[ComponentDescriptor],
      componentid: ComponentId
    ): Vector[ComponentDescriptor] =
      descriptors match {
        case Vector(descriptor) if descriptor.isCanonicalIdentity =>
          Vector(descriptor)
        case Vector(descriptor) if !_owns_component(descriptor, componentid) =>
          Vector(descriptor.copy(componentName = Some(componentid.name)))
        case values => values
    }

    private def _owns_component(
      descriptor: ComponentDescriptor,
      componentid: ComponentId
    ): Boolean =
      if (descriptor.isCanonicalIdentity)
        descriptor.componentId.contains(componentid)
      else
        (descriptor.componentName.orElse(descriptor.name).toVector ++
          descriptor.componentlets.map(_.name))
          .exists(NamingConventions.equivalentByNormalized(_, componentid.name))

    private def _or_raise[A](result: Consequence[A]): A =
      result match {
        case Consequence.Success(value) => value
        case Consequence.Failure(conclusion) =>
          throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
      }

    protected final def spec_create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      service: ServiceDefinition
    ): Component.Core =
      spec_create(name, componentId, Vector(service))

    protected final def spec_create(
      name: String,
      @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
      services: Seq[ServiceDefinition]
    ): Component.Core = {
      val protocol = Protocol(
        services = ServiceDefinitionGroup(services),
        handler = ProtocolHandler.default
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol,
        this
      )
    }
  }

  trait PrimaryComponentFactory extends Factory

  trait ComponentletFactory extends Factory

  trait BundleFactory {
    def primaryFactory: PrimaryComponentFactory

    def componentletFactories: Vector[ComponentletFactory] = Vector.empty

    final def create(params: ComponentCreate): Bundle =
      _or_raise(createC(params))

    final def createC(params: ComponentCreate): Consequence[Bundle] =
      primaryFactory.createPrimaryC(params).flatMap { primary =>
        _sequence(componentletFactories.map(_.createComponentletC(params))).flatMap { componentlets =>
          try {
            Consequence.success(Bundle(primary, componentlets).validate())
          } catch {
            case NonFatal(e) => Consequence.componentInvalid(e)
          }
        }
      }

    private def _sequence[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
      values.foldLeft(Consequence.success(Vector.empty[A])) { (acc, value) =>
        acc.flatMap(xs => value.map(xs :+ _))
      }

    private def _or_raise[A](result: Consequence[A]): A =
      result match {
        case Consequence.Success(value) => value
        case Consequence.Failure(conclusion) =>
          throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
      }
  }

  abstract class SinglePrimaryBundleFactory extends Factory with BundleFactory with PrimaryComponentFactory {
    final override def primaryFactory: PrimaryComponentFactory = this
    final override def componentletFactories: Vector[ComponentletFactory] = Vector.empty
  }

  abstract class ServiceFactory extends ServiceDefinition.Factory[Service] {
    protected var service_ccore: Option[Service.CCore] = None

    def setup(comp: Component): Unit =
      service_ccore = Some(Service.CCore(comp.logic))

    def create(core: ProtocolService.Core): Service = {
      val c = service_ccore getOrElse ???
      create(core, c)
    }

    def create(core: ProtocolService.Core, ccore: Service.CCore): Service
  }
  object ServiceFactory {
    val empty = apply()

    def apply(): ServiceFactory = Instance()

    case class Instance() extends ServiceFactory {
      def create(core: ProtocolService.Core, ccore: Service.CCore): Service =
        Service(core, ccore)
    }
  }

  case class Instance(override val core: Core) extends Component {
  }

  def create(
    name: String,
    @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
    @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
    protocol: Protocol
  ): Component = {
    val core = Core.create(name, componentId, instanceId, protocol)
    val r = Instance(core)
    core.serviceFactory.setup(r)
    r
  }

  def create(
    name: String,
    @deprecatedName("componentid", "0.5.2") componentId: ComponentId,
    @deprecatedName("instanceid", "0.5.2") instanceId: ComponentInstanceId,
    protocol: Protocol,
    applicationConfig: ApplicationConfig
  ): Component = {
    val c = create(name, componentId, instanceId, protocol)
    c.withApplicationConfig(applicationConfig)
    c
  }
  
  trait HealthContributor {
    def name: String
    def check(component: Component): HealthCheck
  }

  final case class HealthCheck(
    name: String,
    status: String,
    detail: Option[String] = None
  ) {
    def toRecord: Record = Record.data(
      "name" -> name,
      "status" -> status
    ) ++ Record.dataOption(
      "detail" -> detail
    )
  }

  final case class HealthSnapshot(
    status: String,
    checks: Vector[HealthCheck]
  )

  def healthSnapshot(component: Component): HealthSnapshot = {
    val checks = _resolve_health_checks(component)
    HealthSnapshot(_overall_status(checks), checks)
  }

}
