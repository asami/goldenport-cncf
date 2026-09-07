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
 * @version Sep.  7, 2026
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
  def stateMachineDefinitions: Vector[CmlStateMachineDefinition] = Vector.empty
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

object Component {
  private val _default_meta_service_name = "meta"
  private val _default_system_service_name = "system"
  private val _default_repository_type = "component"
  private val _default_unknown_version = "unknown"

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

  abstract class Factory {
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

  private def _with_default_services(protocol: Protocol): Protocol = {
    val withmetahelp = _ensure_operation(protocol, _default_meta_service_name, DefaultMetaHelpOperation)
    val withmetadescribe = _ensure_operation(withmetahelp, _default_meta_service_name, DefaultMetaDescribeOperation)
    val withmetacomponents = _ensure_operation(withmetadescribe, _default_meta_service_name, DefaultMetaComponentsOperation)
    val withmetaservices = _ensure_operation(withmetacomponents, _default_meta_service_name, DefaultMetaServicesOperation)
    val withmetaoperations = _ensure_operation(withmetaservices, _default_meta_service_name, DefaultMetaOperationsOperation)
    val withmetaschema = _ensure_operation(withmetaoperations, _default_meta_service_name, DefaultMetaSchemaOperation)
    val withmetaopenapi = _ensure_operation(withmetaschema, _default_meta_service_name, DefaultMetaOpenApiOperation)
    val withmetamcp = _ensure_operation(withmetaopenapi, _default_meta_service_name, DefaultMetaMcpOperation)
    val withmetatree = _ensure_operation(withmetamcp, _default_meta_service_name, DefaultMetaTreeOperation)
    val withmetastatemachine = _ensure_operation(withmetatree, _default_meta_service_name, DefaultMetaStateMachineOperation)
    val withmetaversion = _ensure_operation(withmetastatemachine, _default_meta_service_name, DefaultMetaVersionOperation)
    val withsystemping = _ensure_operation(withmetaversion, _default_system_service_name, DefaultSystemPingOperation)
    val withsystemhealth = _ensure_operation(withsystemping, _default_system_service_name, DefaultSystemHealthOperation)
    _ensure_operation(withsystemhealth, _default_system_service_name, DefaultSystemStatusOperation)
  }

  private def _ensure_operation(
    protocol: Protocol,
    servicename: String,
    operation: OperationDefinition
  ): Protocol = {
    val exists = protocol.services.services.exists { service =>
      service.name == servicename &&
      service.operations.operations.exists(_.name == operation.name)
    }
    if (exists) {
      protocol
    } else {
      protocol.copy(
        services = protocol.services.addOperation(servicename, operation)
      )
    }
  }

  private object DefaultMetaHelpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "help",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaHelpAction(req))
  }

  private object DefaultMetaDescribeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "describe",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaDescribeAction(req))
  }

  private object DefaultMetaComponentsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "components",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaComponentsAction(req))
  }

  private object DefaultMetaServicesOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "services",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaServicesAction(req))
  }

  private object DefaultMetaOperationsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "operations",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaOperationsAction(req))
  }

  private object DefaultMetaSchemaOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "schema",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaSchemaAction(req))
  }

  private object DefaultMetaOpenApiOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "openapi",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaOpenApiAction(req))
  }

  private object DefaultMetaTreeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "tree",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaTreeAction(req))
  }

  private object DefaultMetaMcpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "mcp",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaMcpAction(req))
  }

  private object DefaultMetaStateMachineOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "statemachine",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaStateMachineAction(req))
  }

  private object DefaultMetaVersionOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "version",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaVersionAction(req))
  }

  private object DefaultSystemPingOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "ping",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemPingAction(req))
  }

  private object DefaultSystemHealthOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "health",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemHealthAction(req))
  }

  private object DefaultSystemStatusOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "status",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemStatusAction(req))
  }

  private final case class DefaultMetaHelpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaHelpActionCall(request, core)
  }

  private final case class DefaultMetaHelpActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val response = core.component match {
        case Some(component) =>
          val record = HelpProjection.project(
            component,
            _meta_help_selector(req)
          )
          OperationResponse.RecordResponse(record)
        case None =>
          OperationResponse.RecordResponse(
            Record.data(
              "type" -> "error",
              "summary" -> "component context missing"
            )
          )
      }
      Consequence.success(response)
    }
  }

  private final case class DefaultMetaDescribeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaDescribeActionCall(request, core)
  }

  private final case class DefaultMetaDescribeActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          DescribeProjection.project(
            component,
            _meta_describe_selector(req)
          )
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaComponentsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaComponentsActionCall(request, core)
  }

  private final case class DefaultMetaComponentsActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          DescribeProjection.project(component, None)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaServicesAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaServicesActionCall(request, core)
  }

  private final case class DefaultMetaServicesActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val selector = _meta_services_selector(req)
          HelpProjection.project(component, selector)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaOperationsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaOperationsActionCall(request, core)
  }

  private final case class DefaultMetaOperationsActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val selector = _meta_operations_selector(req)
          HelpProjection.project(component, selector)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaSchemaAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaSchemaActionCall(request, core)
  }

  private final case class DefaultMetaSchemaActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          SchemaProjection.project(component, _meta_schema_selector(req))
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaOpenApiAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaOpenApiActionCall(request, core)
  }

  private final case class DefaultMetaOpenApiActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          _meta_openapi_selector(req) match {
            case Some(name) =>
              component.subsystem
                .flatMap(_.components.find(_.name == name))
                .map(OpenApiProjection.projectComponent)
                .getOrElse(OpenApiProjection.projectComponent(component))
            case None =>
              OpenApiProjection.projectComponent(component)
          }
        case None =>
          """{"error":"component context missing"}"""
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaTreeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaTreeActionCall(request, core)
  }

  private final case class DefaultMetaMcpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaMcpActionCall(request, core)
  }

  private final case class DefaultMetaStateMachineAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaStateMachineActionCall(request, core)
  }

  private final case class DefaultMetaMcpActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          _meta_mcp_selector(req) match {
            case Some(name) =>
              component.subsystem
                .flatMap(_.components.find(_.name == name))
                .map(McpProjection.projectComponent)
                .getOrElse(McpProjection.projectComponent(component))
            case None =>
              McpProjection.projectComponent(component)
          }
        case None =>
          """{"error":"component context missing"}"""
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaTreeActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          val model = TreeProjection.project(component)
          if (_request_wants_json(req)) {
            CliTreeJsonRenderer.render(model)
          } else {
            CliTreeYamlRenderer.render(model)
          }
        case None =>
          "subsystem: unknown\ncomponents: {}"
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaStateMachineActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          StateMachineProjection.project(component, _meta_statemachine_selector(req))
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaVersionAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaVersionActionCall(core)
  }

  private final case class DefaultMetaVersionActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) => _resolve_version_record(component)
        case None =>
          Record.data(
            "component" -> "unknown",
            "version" -> _default_unknown_version,
            "source" -> "unavailable"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultSystemPingAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemPingActionCall(core)
  }

  private final case class DefaultSystemPingActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
  }

  private final case class DefaultSystemHealthAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemHealthActionCall(core)
  }

  private final case class DefaultSystemHealthActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val health = healthSnapshot(component)
          Record.data(
            "status" -> health.status,
            "checks" -> health.checks.map(_.toRecord)
          )
        case None =>
          Record.data(
            "status" -> "error",
            "checks" -> Vector(
              HealthCheck("component.available", "error", Some("component context missing")).toRecord
            )
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultSystemStatusAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemStatusActionCall(core)
  }

  private final case class DefaultSystemStatusActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val now = core.executionContext.clock.instant()
      val bootedat = _global_runtime_context(core.executionContext.runtime).map(_.bootedAt).getOrElse(now)
      val duration = Duration.between(bootedat, now)
      val uptime = if (duration.isNegative) Duration.ZERO else duration
      val base = Record.data(
        "status" -> "UP",
        "timestamp" -> now.toString,
        "uptime" -> uptime.toString
      )
      val record = core.component match {
        case Some(component) =>
          component.jobEngine.metrics match {
            case Some(metrics) =>
              base ++ Record.data(
                "jobsRunning" -> metrics.running,
                "jobsQueued" -> metrics.queued,
                "jobsCompleted" -> metrics.completed,
                "jobsFailed" -> metrics.failed
              )
            case None =>
              base
          }
        case None =>
          base
      }
      Consequence.success(OperationResponse.RecordResponse(record))
    }
  }

  private def _read_resource_text(
    loader: ClassLoader,
    path: String
  ): Option[String] =
    Option(loader.getResourceAsStream(path)).flatMap { is =>
      try {
        val text = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim
        if (text.nonEmpty) Some(text) else None
      } catch {
        case NonFatal(_) => None
      } finally {
        is.close()
      }
    }

  private def _resolve_version_record(component: Component): Record = {
    val configversion = _configuration_value(component, "component.version")
    val properties = _component_properties(component)
    val propertyversion =
      properties.get("component.version").orElse(properties.get("version"))
    val manifestversion = Option(component.getClass.getPackage)
      .flatMap(p => Option(p.getImplementationVersion))
      .map(_.trim)
      .filter(_.nonEmpty)

    val versionwithsource =
      configversion.map(_ -> "config")
        .orElse(propertyversion.map(_ -> "resource"))
        .orElse(manifestversion.map(_ -> "manifest"))
        .getOrElse(_default_unknown_version -> "default")

    val buildinfo =
      _configuration_value(component, "component.build")
        .orElse(_configuration_value(component, "component.build.info"))
        .orElse(properties.get("build"))
        .orElse(properties.get("build.info"))
        .orElse(properties.get("build-time"))
        .orElse(properties.get("build.revision"))

    Record.data(
      "component" -> component.name,
      "version" -> versionwithsource._1,
      "source" -> versionwithsource._2
    ) ++ Record.dataOption(
      "build" -> buildinfo
    )
  }

  private def _component_properties(
    component: Component
  ): Map[String, String] =
    _read_properties(component.getClass.getClassLoader, "META-INF/component.properties")

  private def _read_properties(
    loader: ClassLoader,
    path: String
  ): Map[String, String] =
    Option(loader.getResourceAsStream(path)).map { is =>
      try {
        val p = Properties()
        p.load(is)
        p.stringPropertyNames().toArray.toVector.map(_.toString).map { key =>
          key -> p.getProperty(key)
        }.toMap
      } catch {
        case NonFatal(_) => Map.empty[String, String]
      } finally {
        is.close()
      }
    }.getOrElse(Map.empty)

  private def _request_argument_values(request: Request): Vector[String] =
    request.arguments
      .map(arg => Option(arg.value).map(_.toString).getOrElse("").trim)
      .filter(_.nonEmpty)
      .toVector

  private def _meta_help_selector(request: Request): Option[String] =
    _request_argument_values(request).headOption

  private def _meta_describe_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args match {
      case Vector(service, operation) =>
        request.component.map(c => s"$c.$service.$operation").orElse(Some(s"$service.$operation"))
      case Vector(single) =>
        if (single.contains(".")) {
          single.split("\\.").toVector.filter(_.nonEmpty) match {
            case Vector(_, _, _) => Some(single)
            case Vector(_, _) =>
              request.component.map(c => s"$c.$single").orElse(Some(single))
            case Vector(_) =>
              request.component.map(c => s"$c.$single").orElse(Some(single))
            case _ => Some(single)
          }
        } else {
          request.component.map(c => s"$c.$single").orElse(Some(single))
        }
      case _ =>
        request.component
    }
  }

  private def _meta_services_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(name) if name.contains(".") =>
        Some(name)
      case Some(componentname) =>
        Some(componentname)
      case None =>
        request.component
    }
  }

  private def _meta_operations_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args match {
      case Vector(servicename, operationname) =>
        request.component.map(c => s"$c.$servicename.$operationname")
      case Vector(servicename) =>
        if (servicename.contains(".")) {
          Some(servicename)
        } else {
          request.component.map(c => s"$c.$servicename")
        }
      case _ =>
        None
    }
  }

  private def _meta_schema_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(selector) => Some(selector)
      case None => request.component
    }
  }

  private def _meta_openapi_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption
  }

  private def _meta_mcp_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption
  }

  private def _meta_statemachine_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(selector) => Some(selector)
      case None => request.component
    }
  }

  private def _request_wants_json(request: Request): Boolean =
    request.properties.exists { p =>
      (
        p.name.equalsIgnoreCase("textus.format") ||
          p.name.equalsIgnoreCase("textus.output.format") ||
          p.name.equalsIgnoreCase("cncf.format") ||
          p.name.equalsIgnoreCase("cncf.output.format")
      ) && Option(p.value).map(_.toString.trim.toLowerCase).contains("json")
    }

  private def _configuration_value(
    component: Component,
    key: String
  ): Option[String] =
    component.subsystem.flatMap { ss =>
      ss.configuration.get[String](key) match {
        case Consequence.Success(Some(value)) =>
          val normalized = value.trim
          if (normalized.isEmpty) None else Some(normalized)
        case _ => None
      }
    }

  private def _resolve_health_checks(component: Component): Vector[HealthCheck] = {
    val base = Vector(
      HealthCheck("component.reachable", "ok"),
      HealthCheck("component.runtime", "ok")
    )
    val contributorchecks = component.healthContributors.map { contributor =>
      try {
        contributor.check(component)
      } catch {
        case NonFatal(e) =>
          val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getName)
          HealthCheck(contributor.name, "error", Some(detail))
      }
    }
    val configuredchecks = _configured_health_check_names(component).filterNot { name =>
      contributorchecks.exists(_.name == name)
    }.map { name =>
      HealthCheck(name, "warning", Some("configured check has no registered contributor"))
    }
    base ++ contributorchecks ++ configuredchecks
  }

  private def _configured_health_check_names(component: Component): Vector[String] = {
    val fromconfig = _configuration_value(component, "component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    val fromresource = _component_properties(component)
      .get("component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    (fromconfig ++ fromresource).distinct
  }

  private def _comma_separated_values(value: String): Vector[String] =
    value.split(",").toVector.map(_.trim).filter(_.nonEmpty)

  private def _overall_status(checks: Vector[HealthCheck]): String = {
    val statuses = checks.map(_.status.toLowerCase)
    if (statuses.contains("error")) {
      "error"
    } else if (statuses.contains("warning")) {
      "warning"
    } else {
      "ok"
    }
  }

  @annotation.tailrec
  private def _global_runtime_context(scope: ScopeContext): Option[GlobalRuntimeContext] =
    scope match {
      case global: GlobalRuntimeContext => Some(global)
      case other => other.parent match {
        case Some(parent) => _global_runtime_context(parent)
        case None => None
      }
    }
}

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

final class ComponentInstanceId private (
  val sharedIdentity: SharedComponentInstanceId
) {
  def componentId: ComponentId = ComponentId._from_shared_component_id(sharedIdentity.componentId())

  def name: String = sharedIdentity.componentId().qualifiedName()

  def instance: String = sharedIdentity.label()

  def canonicalKey: String = sharedIdentity.toString()

  override def equals(other: Any): Boolean =
    other match {
      case that: ComponentInstanceId => sharedIdentity.equals(that.sharedIdentity)
      case _ => false
    }

  override def hashCode(): Int = sharedIdentity.hashCode()

  override def toString: String = sharedIdentity.toString()
}

object ComponentInstanceId {
  def createC(
    componentId: ComponentId,
    label: String
  ): Consequence[ComponentInstanceId] =
    _to_consequence(SharedComponentInstanceId.of(_shared_component_id(componentId), label))

  def createC(
    qualifiedId: String,
    label: String
  ): Consequence[ComponentInstanceId] =
    ComponentId.parseC(qualifiedId).flatMap(createC(_, label))

  def apply(
    componentId: ComponentId,
    label: String
  ): ComponentInstanceId =
    _require(SharedComponentInstanceId.of(_shared_component_id(componentId), label))

  def apply(
    qualifiedId: String,
    label: String
  ): ComponentInstanceId =
    apply(ComponentId(qualifiedId), label)

  def default(componentId: ComponentId): ComponentInstanceId =
    _require(SharedComponentInstanceId.defaultInstance(_shared_component_id(componentId)))

  private def _shared_component_id(componentid: ComponentId): SharedComponentId =
    if (componentid == null) null else componentid.sharedIdentity

  private def _to_consequence(
    result: ComponentIdentityResult[SharedComponentInstanceId]
  ): Consequence[ComponentInstanceId] =
    if (result.isSuccess())
      Consequence(new ComponentInstanceId(result.value().get()))
    else
      Consequence.componentInvalid(_error_message(result.error().get()))

  private def _require(
    result: ComponentIdentityResult[SharedComponentInstanceId]
  ): ComponentInstanceId =
    if (result.isSuccess())
      new ComponentInstanceId(result.value().get())
    else
      throw new IllegalArgumentException(_error_message(result.error().get()))

  private def _error_message(error: ComponentIdentityResult.Error): String =
    s"${error.code()}: ${error.message()}"
}

final case class ComponentInstanceMetadata(
  componentName: String,
  instance: String = "default",
  config: Map[String, String] = Map.empty,
  rules: Record = Record.empty,
  purposes: Vector[String] = Vector.empty,
  tags: Vector[String] = Vector.empty,
  priority: Int = 0,
  isDefault: Boolean = false,
  capabilities: Vector[String] = Vector.empty,
  componentId: Option[ComponentId] = None
) {
  def instanceId: ComponentInstanceId =
    componentId.map(ComponentInstanceId(_, instance)).getOrElse(ComponentInstanceId(componentName, instance))
}


sealed trait ComponentLocator {
//  def locate(space: ComponentSpace): Option[ComponentInstanceId]
}

object ComponentLocator {
  final case class ComponentIdLocator(id: ComponentId) extends ComponentLocator
  final case class NameLocator(name: String) extends ComponentLocator
}

private[cncf] final class ComponentAssemblyContext private (
  private val _subsystem: Subsystem,
  private val _runtime_configuration: ResolvedConfiguration
) {
  private[cncf] def subsystem: Subsystem = _subsystem

  private[cncf] def runtimeConfiguration: ResolvedConfiguration = _runtime_configuration
}

private[cncf] object ComponentAssemblyContext {
  def apply(subsystem: Subsystem): ComponentAssemblyContext =
    apply(subsystem, subsystem.configuration)

  def apply(
    subsystem: Subsystem,
    runtimeconfiguration: ResolvedConfiguration
  ): ComponentAssemblyContext =
    new ComponentAssemblyContext(subsystem, runtimeconfiguration)
}

/**
 * Component factory input. The Subsystem carrier is internal framework
 * assembly state and is deliberately not part of the Component-facing API.
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

final case class ComponentInit( // TODO use config
  private[cncf] assembly: ComponentAssemblyContext,
  core: Component.Core,
  origin: ComponentOrigin,
  componentDescriptors: Vector[ComponentDescriptor] = Vector.empty,
  participantRole: Component.ParticipantRole = Component.ParticipantRole.Primary,
  instanceMetadata: Option[ComponentInstanceMetadata] = None,
  initializationParameters: ComponentInitializationParameters = ComponentInitializationParameters.empty
) {
  private[cncf] def subsystem: Subsystem = assembly.subsystem
}

object ComponentInit {
  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin
  ): ComponentInit =
    apply(subsystem, core, origin, Vector.empty)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor]
  ): ComponentInit =
    apply(subsystem, core, origin, componentDescriptors, Component.ParticipantRole.Primary)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    instanceMetadata: Option[ComponentInstanceMetadata]
  ): ComponentInit =
    apply(subsystem, core, origin, Vector.empty, Component.ParticipantRole.Primary, instanceMetadata)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    participantRole: Component.ParticipantRole,
    instanceMetadata: Option[ComponentInstanceMetadata]
  ): ComponentInit =
    apply(subsystem, core, origin, Vector.empty, participantRole, instanceMetadata)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor],
    participantRole: Component.ParticipantRole
  ): ComponentInit =
    apply(subsystem, core, origin, componentDescriptors, participantRole, None)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor],
    participantRole: Component.ParticipantRole,
    instanceMetadata: Option[ComponentInstanceMetadata]
  ): ComponentInit =
    apply(subsystem, core, origin, componentDescriptors, participantRole, instanceMetadata, ComponentInitializationParameters.empty)

  def apply(
    subsystem: Subsystem,
    core: Component.Core,
    origin: ComponentOrigin,
    componentDescriptors: Vector[ComponentDescriptor],
    participantRole: Component.ParticipantRole,
    instanceMetadata: Option[ComponentInstanceMetadata],
    initializationParameters: ComponentInitializationParameters
  ): ComponentInit =
    ComponentInit(
      ComponentAssemblyContext(subsystem),
      core,
      origin,
      componentDescriptors,
      participantRole,
      instanceMetadata,
      initializationParameters
    )
}

sealed trait ComponentOrigin {
  def label: String
}

object ComponentOrigin {
  case object Builtin extends ComponentOrigin {
    val label: String = "builtin"
  }
  final case class Repository(label: String) extends ComponentOrigin
  case object Main extends ComponentOrigin {
    val label: String = "main"
  }
  case object Embed extends ComponentOrigin {
    val label: String = "embed"
  }
  case object Unknown extends ComponentOrigin {
    val label: String = "unknown"
  }
}

// trait ComponentActionEntry {
//   def name: String
//   def opdef: OperationDefinition
//   def logic: ActionLogic
// }

// trait Service {
//   def entries: Seq[ComponentActionEntry]

//   def call(
//     name: String,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse]
// }

// trait Receptor {
//   def entries: Seq[ComponentActionEntry]

//   def receive(
//     name: String,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse]
// }
