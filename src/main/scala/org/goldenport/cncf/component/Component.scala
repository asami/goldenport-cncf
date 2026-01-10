package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.logic.ProtocolLogic
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.protocol.service.{Service => ProtocolService}
// import org.goldenport.cncf.action.ActionLogic
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeContext, ScopeKind, SystemContext}
import org.goldenport.cncf.action.{Action, ActionEngine}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.config.model.Config
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.datastore.DataStackFactory
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Jan.  1, 2026
 *  version Jan.  3, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Component() extends Component.Core.Holder {
  private var _application_config: Component.ApplicationConfig = Component.ApplicationConfig()
  private var _system_context: SystemContext = SystemContext.empty
  private var _unit_of_work: UnitOfWork = DataStackFactory.create(Config.empty)
  private var _scope_context: Option[ScopeContext] = None
  private var _services: Option[ServiceGroup] = None
  private var _subsystem: Option[Subsystem] = None

  def services: ServiceGroup = _services.getOrElse {
    throw new IllegalStateException("Component does not initialized.")
  }

  lazy val receptors: ReceptorGroup = ReceptorGroup.empty // TODO

  lazy val logic: ComponentLogic = ComponentLogic(this, _system_context)

  def initialize(params: ComponentInitParams): Component = {
    _subsystem = Some(params.subsystem)
    _inherit_http_driver_(params)
    _services = Some(ServiceGroup(protocol.services.services.map(_to_service)))
    initialize_Component(params)
    this
  }

  private def _to_service(p: ServiceDefinition): Service = {
    serviceFactory.setup(this)
    p.createService(serviceFactory)
  }

  protected def initialize_Component(params: ComponentInitParams): Unit = {}

  def service: Service = services.services.head // TODO

  def execute(action: Action): Consequence[OperationResponse] = {
    val call = logic.createActionCall(action)
    logic.execute(call)
  }

  def applicationConfig: Component.ApplicationConfig = _application_config

  def subsystem: Option[Subsystem] = _subsystem

  def withApplicationConfig(ac: Component.ApplicationConfig): Component = {
    _application_config = ac
    this
  }

  def systemContext: SystemContext = _system_context

  def withSystemContext(sc: SystemContext): Component = {
    _system_context = sc
    this
  }

  def unitOfWork: UnitOfWork = _unit_of_work

  def scopeContext: ScopeContext =
    _scope_context.getOrElse(_default_scope_context())

  def withScopeContext(sc: ScopeContext): Component = {
    _scope_context = Some(sc)
    this
  }

  private def _init_data_stack(config: Config): Unit = {
    _unit_of_work = DataStackFactory.create(config)
  }

  private def _inherit_http_driver_(
    params: ComponentInitParams
  ): Unit = {
    if (_application_config.httpDriver.isEmpty) {
      params.subsystem.httpDriver.foreach { driver =>
        _application_config = _application_config.copy(
          httpDriver = Some(driver)
        )
      }
    }
  }

  private def _default_scope_context(): ScopeContext =
    ScopeContext(
      kind = ScopeKind.Component,
      name = "component",
      parent = None,
      observabilityContext = ExecutionContext.create().observability
    )
}

object Component {
  final case class ApplicationConfig(
    applicationContext: Option[ApplicationContext] = None,
    httpDriver: Option[HttpDriver] = None,
    config: Option[org.goldenport.cncf.config.model.Config] = None
  )

  type ApplicationContext =
    org.goldenport.cncf.context.ExecutionContext.ApplicationContext

  case class Core(
    name: String,
    componentId: ComponentId,
    instanceId: ComponentInstanceId,
    protocol: Protocol,
    protocolLogic: ProtocolLogic,
    actionEngine: ActionEngine,
    jobEngine: JobEngine,
    serviceFactory: ServiceFactory
  )
  object Core {
    trait Holder {
      def core: Core

      def name = core.name
      def componentId = core.componentId
      def instanceId = core.instanceId
      def protocol = core.protocol
      def protocolLogic = core.protocolLogic
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
      val servicefactory = ServiceFactory()
      create(name, componentid, instanceid, protocol, servicefactory)
    }

    def create(
      name: String,
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol,
      serviceFactory: ServiceFactory
    ): Core = {
      val r = create(
        name,
        componentid,
        instanceid,
        protocol,
        ProtocolLogic(protocol),
        ActionEngine.create(),
        InMemoryJobEngine.create(),
        serviceFactory
      )
//      serviceFactory.setup(r)
      r
    }

    def create(
      name: String,
      componentid: ComponentId,
      instanceid: ComponentInstanceId,
      protocol: Protocol,
      protocolLogic: ProtocolLogic,
      actionEngine: ActionEngine,
      jobEngine: JobEngine,
      serviceFactory: ServiceFactory
    ): Core = {
      Core(
        name,
        componentid,
        instanceid,
        protocol,
        protocolLogic,
        actionEngine,
        jobEngine,
        serviceFactory
      )
    }
  }

  trait Factory {
    final def create(params: ComponentInitParams): Vector[Component] = {
      val xs = create_Components(params)
      xs.map(_.initialize(params))
    }

    protected def create_Components(params: ComponentInitParams): Vector[Component]
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
    def apply(): ServiceFactory = Instance()

    case class Instance() extends ServiceFactory {
      def create(core: ProtocolService.Core, ccore: Service.CCore): Service =
        Service(core, ccore)
    }
  }

  case class Instance(core: Core) extends Component {
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
    val conf = applicationConfig.config.getOrElse(org.goldenport.cncf.config.model.Config.empty)
    c._init_data_stack(conf)
    c
  }

  def create(
    name: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    protocol: Protocol,
    serviceFactory: ServiceFactory
  ): Component = {
    val core = Core.create(
      name,
      componentid,
      instanceid,
      protocol,
      ProtocolLogic(protocol),
      ActionEngine.create(),
      InMemoryJobEngine.create(),
      serviceFactory
    )
    val r = Instance(core)
    serviceFactory.setup(r)
    r
  }

  def create(
    name: String,
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    protocol: Protocol,
    protocolLogic: ProtocolLogic,
    actionEngine: ActionEngine,
    jobEngine: JobEngine,
    serviceFactory: ServiceFactory
  ): Component = {
    val core = Core.create(
      name,
      componentid,
      instanceid,
      protocol,
      protocolLogic,
      actionEngine,
      jobEngine,
      serviceFactory
    )
    val r = Instance(core)
    serviceFactory.setup(r)
    r
  }

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
  //   c._init_data_stack(conf)
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
}

final case class ComponentId(
  name: String
) extends UniversalId("cncf", name, "component")

final case class ComponentInstanceId(
  name: String,
  instance: String
) extends UniversalId("cncf", name, "component_instance", Some(instance))

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

final case class ComponentInitParams( // TODO use config
  subsystem: Subsystem
)

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
