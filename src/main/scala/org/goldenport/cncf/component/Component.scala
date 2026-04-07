package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.id.UniversalId
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
import org.goldenport.cncf.context.{CorrelationId, EntitySpaceContext, ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, AggregateBehavior, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer, CliTreeJsonRenderer, CliTreeYamlRenderer}
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateSpace, Repository, AggregateDefinition}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntitySpace}
import org.goldenport.cncf.entity.view.{Browser, ViewCollection, ViewSpace, ViewDefinition}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.statemachine.{CmlStateMachineDefinition, StateMachinePlannerProvider}
import org.goldenport.cncf.event.{CmlEventDefinition, CmlRoutingDefinition, CmlSubscriptionDefinition, EventReception, EventStore}
import org.goldenport.cncf.projection.{HelpProjection, DescribeProjection, SchemaProjection, OpenApiProjection, McpProjection, TreeProjection, StateMachineProjection}
import cats.data.NonEmptyVector
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.Properties
import scala.util.control.NonFatal

/*
 * @since   Jan.  1, 2026
 *  version Apr.  6, 2026
 * @version Apr.  7, 2026
 *  version Mar. 30, 2026
 *  version Jan. 22, 2026
 *  version Feb. 17, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Component() extends Component.Core.Holder {
  private var _core: Option[Component.Core] = None
  private var _origin: Option[ComponentOrigin] = None
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
  val entitySpace: EntitySpace = new EntitySpace()
  val aggregateSpace: AggregateSpace = new AggregateSpace()
  val viewSpace: ViewSpace = new ViewSpace()

  override def core: Component.Core =
    _core.getOrElse(throw new IllegalStateException("Component core is not initialized."))

  def origin: ComponentOrigin =
    _origin.getOrElse(ComponentOrigin.Unknown)

  def services: ServiceGroup = _services.getOrElse {
    throw new IllegalStateException("Component does not initialized.")
  }

  lazy val receptors: ReceptorGroup = ReceptorGroup.empty // TODO

  lazy val logic: ComponentLogic = ComponentLogic(this)

  def initialize(params: ComponentInit): Component = {
    _core = Some(params.core)
    _origin = Some(params.origin)
    _subsystem = Some(params.subsystem)
    _inherit_http_driver_(params)
    _event_store = Some(params.subsystem.eventStore)
    jobEngine match {
      case m: InMemoryJobEngine =>
        m.withEventStore(params.subsystem.eventStore)
      case _ =>
        ()
    }
    _services = Some(ServiceGroup(protocol.services.services.map(_to_service)))
    initialize_Component(params)
    this
  }

  private def _to_service(p: ServiceDefinition): Service = {
    serviceFactory.setup(this)
    p.createService(serviceFactory)
  }

  protected def initialize_Component(params: ComponentInit): Unit = {}

  def service: Service = services.services.head // TODO

  def port: Component.Port = _port

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

  def install_binding[Req, S](
    name: String,
    req: Req
  )(using ExecutionContext): Consequence[Component] =
    binding(name) match {
      case Some(m: Component.Binding[?, ?]) =>
        m.asInstanceOf[Component.Binding[Req, S]].install(this, req)
      case None =>
        Consequence.failure(s"binding not found: $name")
    }

  def withPort(port: Component.Port): Component = {
    _port = port
    this
  }

  def execute(action: Action): Consequence[OperationResponse] = {
    logic.executeAction(action)
  }

  def applicationConfig: Component.ApplicationConfig = _application_config

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

//  def systemContext: SystemContext = _system_context

  // def withSystemContext(sc: SystemContext): Component = {
  //   _system_context = sc
  //   this
  // }

  def healthContributors: Vector[Component.HealthContributor] = _health_contributors

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
  def aggregateDefinitions: Vector[AggregateDefinition] = Vector.empty
  def viewDefinitions: Vector[ViewDefinition] = Vector.empty
  def operationDefinitions: Vector[CmlOperationDefinition] = Vector.empty
  def componentDefinitionRecords: Vector[Record] = Vector.empty
  def subsystemDefinitionRecords: Vector[Record] = Vector.empty

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

  private def _inherit_http_driver_(
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
      observabilityContext = ExecutionContext.create().observability
    )
  }

  private def _component_context(parent: ScopeContext): Component.Context =
    Component.Context(
      name = "component",
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
    operation_name: String,
    behavior: AggregateBehavior[?]
  )

  final case class AggregateCollectionBinding(
    aggregate_name: String,
    collection: AggregateCollection[?]
  )

  final case class Binding[Req, S](
    port: org.goldenport.cncf.component.Port[Req, S]
  ) {
    def bind(req: Req)(using ExecutionContext): Consequence[S] =
      for {
        contract <- port.api.resolve(req)
        selection <- port.variation.current(req)
        service <- _provide_(contract, selection)
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

    private def _provide_(
      contract: ServiceContract[S],
      selection: VariationSelection
    )(using ExecutionContext): Consequence[S] =
      port.spi.find(_.supports(contract, selection)) match {
        case Some(extensionpoint) =>
          extensionpoint.provide(contract, selection)
        case None =>
          Consequence.failure(
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
    effectiveExtensions: Map[String, String] = Map.empty,
    effectiveConfig: Map[String, String] = Map.empty
  )

  object Context {
    def apply(
      name: String,
      parent: ScopeContext,
      component: Component,
      componentOrigin: ComponentOrigin
    ): Context = {
      val _core = ScopeContext.Core(
        kind = ScopeKind.Component,
        name = name,
        parent = Some(parent),
        observabilityContext = parent.observabilityContext.createChild(parent, ScopeKind.Component, name),
        httpDriverOption = None,
        entityspace = Some(EntitySpaceContext(component.entitySpace))
      )
      Context(
        core = _core,
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
    val name = "SCRIPT" // _create_script_component_name()
    val componentId = ComponentId("script")
    val instanceId = ComponentInstanceId.default(componentId)
    org.goldenport.cncf.component.Component.Core.create(
      name,
      componentId,
      instanceId,
      protocol
    )
  }

  trait Port {
    def get[T: ClassTag]: Option[T]
    def orElse(other: Port): Port
  }

  object Port {
    val empty: Port = new Port {
      def get[T: ClassTag]: Option[T] = None
      def orElse(other: Port): Port = other
    }

    def of(services: Any*): Port =
      new Port {
        private val _services = services.toVector
        def get[T: ClassTag]: Option[T] = {
          val clazz = summon[ClassTag[T]].runtimeClass
          _services.collectFirst {
            case service if clazz.isInstance(service) => service.asInstanceOf[T]
          }
        }
        def orElse(other: Port): Port = Port.combined(this, other)
      }

    def combined(primary: Port, secondary: Port): Port =
      new Port {
        def get[T: ClassTag]: Option[T] =
          primary.get[T].orElse(secondary.get[T])
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
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol
    ): Core = {
      create(name, componentid, instanceid, protocol, InMemoryJobEngine.create())
    }

    def create(
      name: String,
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol,
      jobEngine: JobEngine
    ): Core = {
      val mergedProtocol = _with_default_services(protocol)
      Core(
        name,
        componentid,
        instanceid,
        mergedProtocol,
        ProtocolLogic(mergedProtocol),
        None,
        ActionEngine.create(),
        jobEngine,
      )
    }

    def create(
      name: String,
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol,
      factory: Component.Factory
    ): Core = create(
      name,
      componentid,
      instanceid,
      protocol,
      ProtocolLogic(protocol),
      factory,
      ActionEngine.create(),
      InMemoryJobEngine.create(),
    )

    def create(
      name: String,
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol,
      protocolLogic: ProtocolLogic,
      factory: Component.Factory,
      actionEngine: ActionEngine,
      jobEngine: JobEngine,
    ): Core = {
      val mergedProtocol = _with_default_services(protocol)
      Core(
        name,
        componentid,
        instanceid,
        mergedProtocol,
        ProtocolLogic(mergedProtocol),
        Some(factory),
        actionEngine,
        jobEngine,
      )
    }
  }

  abstract class Factory {
    def serviceFactory: ServiceFactory = ServiceFactory.empty

    def aggregate_collection_bindings(
      comp: Component
    ): Vector[AggregateCollectionBinding] = Vector.empty

    def aggregate_behavior_bindings(
      comp: Component
    ): Vector[AggregateBehaviorBinding] = Vector.empty

    def create_aggregate_behavior(
      action: Action,
      core: ActionCall.Core
    ): Option[AggregateBehavior[?]] =
      for {
        comp <- core.component
        binding <- aggregate_behavior_bindings(comp)
          .find(_.operation_name == action.request.operation)
      } yield binding.behavior

    def authorize_operation_access(
      action: Action,
      access: CmlOperationAccess,
      core: ActionCall.Core
    ): Option[Consequence[Unit]] = None

    def authorize_operation_entity(
      action: Action,
      entityName: String,
      core: ActionCall.Core
    ): Option[Consequence[Unit]] = None

    def authorize_unit_of_work(
      authorization: org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization,
      uow: org.goldenport.cncf.unitofwork.UnitOfWork
    ): Option[Consequence[Unit]] = None

    final def create(params: ComponentCreate): Vector[Component] = {
      val xs = create_Components(params)
      xs.map { comp =>
        val core = create_Core(params, comp)
        val sharedCore = core.copy(jobEngine = params.subsystem.jobEngine)
        comp.initialize(ComponentInit(params.subsystem, sharedCore, params.origin))
      }
    }

    protected def create_Components(params: ComponentCreate): Vector[Component]

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core

    // private def _resolve_core(
    //   params: ComponentInitParams,
    //   comp: Component
    // ): Component.Core = {
    //   try {
    //     comp.core
    //   } catch {
    //     case NonFatal(_) => params.core
    //   }
    // }

    protected final def spec_create(
      name: String,
      componentid: ComponentId,
      service: ServiceDefinition
    ): Component.Core =
      spec_create(name, componentid, Vector(service))

    protected final def spec_create(
      name: String,
      componentid: ComponentId,
      services: Seq[ServiceDefinition]
    ): Component.Core = {
      val protocol = Protocol(
        services = ServiceDefinitionGroup(services),
        handler = ProtocolHandler.default
      )
      val instanceid = ComponentInstanceId.default(componentid)
      Component.Core.create(
        name,
        componentid,
        instanceid,
        protocol,
        this
      )
    }
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
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    protocol: Protocol
  ): Component = {
    val core = Core.create(name, componentid, instanceid, protocol)
    val r = Instance(core)
    core.serviceFactory.setup(r)
    r
  }

  def create(
    name: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    protocol: Protocol,
    applicationConfig: ApplicationConfig
  ): Component = {
    val c = create(name, componentid, instanceid, protocol)
    c.withApplicationConfig(applicationConfig)
    c
  }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol,
  //   serviceFactory: ServiceFactory
  // ): Component = {
  //   val core = Core.create(
  //     name,
  //     componentid,
  //     instanceid,
  //     protocol,
  //     ProtocolLogic(protocol),
  //     ActionEngine.create(),
  //     InMemoryJobEngine.create(),
  //     serviceFactory
  //   )
  //   val r = Instance(core)
  //   serviceFactory.setup(r)
  //   r
  // }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol,
  //   protocolLogic: ProtocolLogic,
  //   actionEngine: ActionEngine,
  //   jobEngine: JobEngine,
  //   serviceFactory: ServiceFactory
  // ): Component = {
  //   val core = Core.create(
  //     name,
  //     componentid,
  //     instanceid,
  //     protocol,
  //     protocolLogic,
  //     actionEngine,
  //     jobEngine,
  //     serviceFactory
  //   )
  //   val r = Instance(core)
  //   serviceFactory.setup(r)
  //   r
  // }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol
  // ): Component = {
  //   val servicefactory = ServiceFactory()
  //   create(name, componentid, instanceid, protocol, servicefactory)
  // }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol,
  //   applicationConfig: ApplicationConfig
  // ): Component = {
  //   val c = create(name, componentid, instanceid, protocol)
  //   c.withApplicationConfig(applicationConfig)
  //   val conf = applicationConfig.config.getOrElse(org.goldenport.cncf.config.model.Config.empty)
  //   c
  // }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol,
  //   serviceFactory: ServiceFactory
  // ): Component = {
  //   val r = create(
  //     name,
  //     componentid,
  //     instanceid,
  //     protocol,
  //     ProtocolLogic(protocol),
  //     ActionEngine.create(),
  //     InMemoryJobEngine.create(),
  //     serviceFactory
  //   )
  //   serviceFactory.setup(r)
  //   r
  // }

  // def create(
  //   name: String,
  //   componentid: ComponentId,
  //   instanceid: ComponentInstanceId,
  //   protocol: Protocol,
  //   protocolLogic: ProtocolLogic,
  //   actionEngine: ActionEngine,
  //   jobEngine: JobEngine,
  //   serviceFactory: ServiceFactory
  // ): Component = {
  //   val core = Core(
  //     name,
  //     componentid,
  //     instanceid,
  //     protocol,
  //     protocolLogic,
  //     actionEngine,
  //     jobEngine,
  //     serviceFactory
  //   )
  //   Instance(core)
  // }
  
  final case class Config(
    httpDriver: Option[String],
    mode: Option[RunMode]
  )

  object Config {
    def from(conf: ResolvedConfiguration): Consequence[Config] = {
      import cats.syntax.all.*

      val http =
        conf.get[String]("cncf.component.http.driver")

      val mode =
        conf.get[String]("cncf.component.mode")
          .flatMap(_.traverse(RunMode.parse))

      (http, mode).mapN(Config.apply)
    }
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

  private def _with_default_services(protocol: Protocol): Protocol = {
    val withMetaHelp = _ensure_operation(protocol, _default_meta_service_name, _DefaultMetaHelpOperation)
    val withMetaDescribe = _ensure_operation(withMetaHelp, _default_meta_service_name, _DefaultMetaDescribeOperation)
    val withMetaComponents = _ensure_operation(withMetaDescribe, _default_meta_service_name, _DefaultMetaComponentsOperation)
    val withMetaServices = _ensure_operation(withMetaComponents, _default_meta_service_name, _DefaultMetaServicesOperation)
    val withMetaOperations = _ensure_operation(withMetaServices, _default_meta_service_name, _DefaultMetaOperationsOperation)
    val withMetaSchema = _ensure_operation(withMetaOperations, _default_meta_service_name, _DefaultMetaSchemaOperation)
    val withMetaOpenApi = _ensure_operation(withMetaSchema, _default_meta_service_name, _DefaultMetaOpenApiOperation)
    val withMetaMcp = _ensure_operation(withMetaOpenApi, _default_meta_service_name, _DefaultMetaMcpOperation)
    val withMetaTree = _ensure_operation(withMetaMcp, _default_meta_service_name, _DefaultMetaTreeOperation)
    val withMetaStateMachine = _ensure_operation(withMetaTree, _default_meta_service_name, _DefaultMetaStateMachineOperation)
    val withMetaVersion = _ensure_operation(withMetaStateMachine, _default_meta_service_name, _DefaultMetaVersionOperation)
    val withSystemPing = _ensure_operation(withMetaVersion, _default_system_service_name, _DefaultSystemPingOperation)
    val withSystemHealth = _ensure_operation(withSystemPing, _default_system_service_name, _DefaultSystemHealthOperation)
    _ensure_operation(withSystemHealth, _default_system_service_name, _DefaultSystemStatusOperation)
  }

  private def _ensure_operation(
    protocol: Protocol,
    serviceName: String,
    operation: OperationDefinition
  ): Protocol = {
    val exists = protocol.services.services.exists { service =>
      service.name == serviceName &&
      service.operations.operations.exists(_.name == operation.name)
    }
    if (exists) {
      protocol
    } else {
      protocol.copy(
        services = protocol.services.addOperation(serviceName, operation)
      )
    }
  }

  private object _DefaultMetaHelpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "help",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaHelpAction(req))
  }

  private object _DefaultMetaDescribeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "describe",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaDescribeAction(req))
  }

  private object _DefaultMetaComponentsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "components",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaComponentsAction(req))
  }

  private object _DefaultMetaServicesOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "services",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaServicesAction(req))
  }

  private object _DefaultMetaOperationsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "operations",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaOperationsAction(req))
  }

  private object _DefaultMetaSchemaOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "schema",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaSchemaAction(req))
  }

  private object _DefaultMetaOpenApiOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "openapi",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaOpenApiAction(req))
  }

  private object _DefaultMetaTreeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "tree",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaTreeAction(req))
  }

  private object _DefaultMetaMcpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "mcp",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaMcpAction(req))
  }

  private object _DefaultMetaStateMachineOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "statemachine",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaStateMachineAction(req))
  }

  private object _DefaultMetaVersionOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "version",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultMetaVersionAction(req))
  }

  private object _DefaultSystemPingOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "ping",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultSystemPingAction(req))
  }

  private object _DefaultSystemHealthOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "health",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultSystemHealthAction(req))
  }

  private object _DefaultSystemStatusOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "status",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition()
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(_DefaultSystemStatusAction(req))
  }

  private final case class _DefaultMetaHelpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaHelpActionCall(request, core)
  }

  private final case class _DefaultMetaHelpActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          val model = HelpProjection.projectModel(
            component,
            _meta_help_selector(req)
          )
          if (_request_wants_json(req)) {
            CliHelpJsonRenderer.render(model)
          } else {
            CliHelpYamlRenderer.render(model)
          }
        case None =>
          "type: error\nsummary: component context missing"
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class _DefaultMetaDescribeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaDescribeActionCall(request, core)
  }

  private final case class _DefaultMetaDescribeActionCall(
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

  private final case class _DefaultMetaComponentsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaComponentsActionCall(request, core)
  }

  private final case class _DefaultMetaComponentsActionCall(
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

  private final case class _DefaultMetaServicesAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaServicesActionCall(request, core)
  }

  private final case class _DefaultMetaServicesActionCall(
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

  private final case class _DefaultMetaOperationsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaOperationsActionCall(request, core)
  }

  private final case class _DefaultMetaOperationsActionCall(
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

  private final case class _DefaultMetaSchemaAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaSchemaActionCall(request, core)
  }

  private final case class _DefaultMetaSchemaActionCall(
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

  private final case class _DefaultMetaOpenApiAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaOpenApiActionCall(request, core)
  }

  private final case class _DefaultMetaOpenApiActionCall(
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

  private final case class _DefaultMetaTreeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaTreeActionCall(request, core)
  }

  private final case class _DefaultMetaMcpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaMcpActionCall(request, core)
  }

  private final case class _DefaultMetaStateMachineAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaStateMachineActionCall(request, core)
  }

  private final case class _DefaultMetaMcpActionCall(
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

  private final case class _DefaultMetaTreeActionCall(
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

  private final case class _DefaultMetaStateMachineActionCall(
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

  private final case class _DefaultMetaVersionAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultMetaVersionActionCall(core)
  }

  private final case class _DefaultMetaVersionActionCall(
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

  private final case class _DefaultSystemPingAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultSystemPingActionCall(core)
  }

  private final case class _DefaultSystemPingActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
  }

  private final case class _DefaultSystemHealthAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultSystemHealthActionCall(core)
  }

  private final case class _DefaultSystemHealthActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val checks = _resolve_health_checks(component)
          val overall = _overall_status(checks)
          Record.data(
            "status" -> overall,
            "checks" -> checks.map(_.toRecord)
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

  private final case class _DefaultSystemStatusAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      _DefaultSystemStatusActionCall(core)
  }

  private final case class _DefaultSystemStatusActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val now = Instant.now()
      val base = Record.data(
        "status" -> "UP",
        "timestamp" -> now.toString,
        "uptime" -> Duration.between(_booted_at, now).toString
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
    val configVersion = _configuration_value(component, "component.version")
    val properties = _component_properties(component)
    val propertyVersion =
      properties.get("component.version").orElse(properties.get("version"))
    val manifestVersion = Option(component.getClass.getPackage)
      .flatMap(p => Option(p.getImplementationVersion))
      .map(_.trim)
      .filter(_.nonEmpty)

    val versionWithSource =
      configVersion.map(_ -> "config")
        .orElse(propertyVersion.map(_ -> "resource"))
        .orElse(manifestVersion.map(_ -> "manifest"))
        .getOrElse(_default_unknown_version -> "default")

    val buildInfo =
      _configuration_value(component, "component.build")
        .orElse(_configuration_value(component, "component.build.info"))
        .orElse(properties.get("build"))
        .orElse(properties.get("build.info"))
        .orElse(properties.get("build-time"))
        .orElse(properties.get("build.revision"))

    Record.data(
      "component" -> component.name,
      "version" -> versionWithSource._1,
      "source" -> versionWithSource._2
    ) ++ Record.dataOption(
      "build" -> buildInfo
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
      case Some(componentName) =>
        Some(componentName)
      case None =>
        request.component
    }
  }

  private def _meta_operations_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args match {
      case Vector(serviceName, operationName) =>
        request.component.map(c => s"$c.$serviceName.$operationName")
      case Vector(serviceName) =>
        if (serviceName.contains(".")) {
          Some(serviceName)
        } else {
          request.component.map(c => s"$c.$serviceName")
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
      p.name == "cncf.format" && Option(p.value).map(_.toString.toLowerCase).contains("json")
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
    val contributorChecks = component.healthContributors.map { contributor =>
      try {
        contributor.check(component)
      } catch {
        case NonFatal(e) =>
          val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getName)
          HealthCheck(contributor.name, "error", Some(detail))
      }
    }
    val configuredChecks = _configured_health_check_names(component).filterNot { name =>
      contributorChecks.exists(_.name == name)
    }.map { name =>
      HealthCheck(name, "warning", Some("configured check has no registered contributor"))
    }
    base ++ contributorChecks ++ configuredChecks
  }

  private def _configured_health_check_names(component: Component): Vector[String] = {
    val fromConfig = _configuration_value(component, "component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    val fromResource = _component_properties(component)
      .get("component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    (fromConfig ++ fromResource).distinct
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

  private val _booted_at: Instant = Instant.now()
}

final case class ComponentId(
  name: String
) extends UniversalId("cncf", name, "component")

final case class ComponentInstanceId(
  name: String,
  instance: String
) extends UniversalId("cncf", name, "component_instance", instance)

object ComponentInstanceId {
  def default(componentId: ComponentId): ComponentInstanceId =
    ComponentInstanceId(componentId.name, "default")
}


sealed trait ComponentLocator {
//  def locate(space: ComponentSpace): Option[ComponentInstanceId]
}

object ComponentLocator {
  final case class ComponentIdLocator(id: ComponentId) extends ComponentLocator
  final case class NameLocator(name: String) extends ComponentLocator
}

final case class ComponentCreate(
  subsystem: Subsystem,
  origin: ComponentOrigin,
  componentDescriptors: Vector[ComponentDescriptor] = Vector.empty
) {
  def withOrigin(p: ComponentOrigin) = copy(origin = p)

  def withComponentDescriptors(p: Vector[ComponentDescriptor]) =
    copy(componentDescriptors = p)

  def toInit(core: Component.Core): ComponentInit =
    ComponentInit(subsystem, core, origin)
}

final case class ComponentInit( // TODO use config
  subsystem: Subsystem,
  core: Component.Core,
  origin: ComponentOrigin
)

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
