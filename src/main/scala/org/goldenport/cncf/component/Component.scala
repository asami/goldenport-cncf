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
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ResolvedConfiguration}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.datastore.DataStackFactory
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.cli.renderer.{CliHelpJsonRenderer, CliHelpYamlRenderer, CliTreeJsonRenderer, CliTreeYamlRenderer}
import org.goldenport.cncf.projection.{HelpProjection, DescribeProjection, SchemaProjection, OpenApiProjection, TreeProjection}
import cats.data.NonEmptyVector
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import scala.util.control.NonFatal

/*
 * @since   Jan.  1, 2026
 *  version Jan.  3, 2026
 *  version Jan. 22, 2026
 *  version Feb. 17, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Component() extends Component.Core.Holder {
  private var _core: Option[Component.Core] = None
  private var _origin: Option[ComponentOrigin] = None
  private var _application_config: Component.ApplicationConfig = Component.ApplicationConfig()
  private var _collaborator_classpath: Option[Vector[Path]] = None
//  private var _system_context: SystemContext = SystemContext.empty
  private var _unit_of_work: UnitOfWork = DataStackFactory.create(Configuration.empty)
  private var _parent_scope_context: Option[ScopeContext] = None
  private var _component_context: Option[Component.Context] = None
  private var _services: Option[ServiceGroup] = None
  private var _subsystem: Option[Subsystem] = None
  private var _health_contributors: Vector[Component.HealthContributor] = Vector.empty

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

  def execute(action: Action): Consequence[OperationResponse] = {
    val call = logic.createActionCall(action)
    logic.execute(call)
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

  def unitOfWork: UnitOfWork = _unit_of_work
  def healthContributors: Vector[Component.HealthContributor] = _health_contributors

  def registerHealthContributor(contributor: Component.HealthContributor): Component = {
    _health_contributors = _health_contributors :+ contributor
    this
  }

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

  private def _init_data_stack(config: Configuration): Unit = {
    _unit_of_work = DataStackFactory.create(config)
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
        httpDriverOption = None
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
      val mergedProtocol = _with_default_services(protocol)
      Core(
        name,
        componentid,
        instanceid,
        mergedProtocol,
        ProtocolLogic(mergedProtocol),
        None,
        ActionEngine.create(),
        InMemoryJobEngine.create(),
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

    final def create(params: ComponentCreate): Vector[Component] = {
      val xs = create_Components(params)
      xs.map { comp =>
        val core = create_Core(params, comp)
        comp.initialize(ComponentInit(params.subsystem, core, params.origin))
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
    val conf = applicationConfig.config.getOrElse(org.goldenport.configuration.Configuration.empty)
    c._init_data_stack(conf)
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
    val withMetaTree = _ensure_operation(withMetaOpenApi, _default_meta_service_name, _DefaultMetaTreeOperation)
    val withMetaVersion = _ensure_operation(withMetaTree, _default_meta_service_name, _DefaultMetaVersionOperation)
    val withSystemPing = _ensure_operation(withMetaVersion, _default_system_service_name, _DefaultSystemPingOperation)
    _ensure_operation(withSystemPing, _default_system_service_name, _DefaultSystemHealthOperation)
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

  private def _request_wants_json(request: Request): Boolean =
    request.properties.exists { p =>
      p.name == "format" && Option(p.value).map(_.toString.toLowerCase).contains("json")
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

final case class ComponentCreate(
  subsystem: Subsystem,
  origin: ComponentOrigin
) {
  def withOrigin(p: ComponentOrigin) = copy(origin = p)

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
