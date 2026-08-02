package org.goldenport.cncf.subsystem

import scala.collection.mutable
import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.datatype.FileBundle
import org.goldenport.http.{HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.record.Record
import org.goldenport.schema.XFileBundle
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, QueryAction}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{
  Component,
  ComponentCapabilityId,
  ComponentId,
  ComponentInstanceId,
  ComponentSpace
}
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.component.ComponentLocator.NameLocator
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.component.builtin.debug.DebugComponent
import org.goldenport.cncf.association.{AssociationBindingAttachResult, AssociationBindingWorkflow, AssociationDomain, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.{BlobAttachmentWorkflow, BlobPayloadSupport, BlobRepository}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.entity.{
  ChildEntityBindingSummary,
  ChildEntityBindingWorkflow,
  EntityMutationAdapterDefaults,
  EntityRevisionTransport
}
import org.goldenport.cncf.http.{HttpDriver, HttpExecutionResult}
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventBus, EventEngine, EventReception, EventStore}
import org.goldenport.cncf.usernotification.UserNotificationEventForwarder
import org.goldenport.cncf.workflow.WorkflowEngine
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.protocol.{Property, Request, Response}

import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeTestDescriptor}
import org.goldenport.cncf.path.{AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.cncf.protocol.OperationRequestValidationObserver
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, ChildEntityBindingOperationDefinition, CmlOperationAssociationBinding, CmlOperationChildEntityBinding, CmlOperationDefinition, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.operation.evaluation.{CmlOperationEvaluationDeclaration, EvaluationAdmissionRequirement, OperationEvaluationActionTask, OperationEvaluationAdmission, OperationEvaluationAdmissionDiagnostic, OperationEvaluationAdmissionRequest, OperationEvaluationAttemptCapture, OperationEvaluationCrossSinkPolicy, OperationEvaluationDeliveryRuntime, OperationEvaluationLimitation, OperationEvaluationLimitationKind, OperationEvaluationOperationIdentity}
import org.goldenport.cncf.security.{AdminAuthorizationPolicy, IngressSecurityResolver, OperationAuthorization, OperationAuthorizationProvider}
import org.goldenport.cncf.config.{ResolvedParameter, ResolvedParameters}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.metrics.{ComponentMetricsRegistry, EntityAccessMetricsRegistry}
import org.goldenport.cncf.mcp.client.{CodexMcpRuntimeAssembly, CodexMcpRuntimeConfiguration, McpServerSetId}
import org.goldenport.cncf.operationtool.{OperationToolRuntimeConfiguration, OperationToolRuntimeRegistry, OperationToolSetId}
import org.goldenport.cncf.spi.{ComponentApiResolver, ResolvedSpiBinding, SpiInvoker, SpiOperationSelector}
import org.goldenport.cncf.servicecontainer.{ServiceContainerCleanupOutcome, ServiceContainerDiagnostics, ServiceContainerId, ServiceContainerRuntime, ServiceContainerRuntimeConfiguration}
import org.goldenport.cncf.observability.ServiceContainerRuntimeObservation

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  4, 2026
 *  version Apr. 30, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class Subsystem(
  val name: String,
  val version: Option[String] = None,
  @deprecatedName("scopeContext", "0.5.1") scopecontext: Option[ScopeContext] = None, // TODO
  httpdriver: Option[HttpDriver] = None,
  val configuration: ResolvedConfiguration,
  val aliasResolver: AliasResolver = GlobalRuntimeContext.current.map(_.aliasResolver).getOrElse(AliasResolver.empty),
  @deprecatedName("runMode", "0.5.1")
  runmode: RunMode = GlobalRuntimeContext.current.map(_.runtimeMode).getOrElse(RunMode.Server),
  @deprecatedName("operationEvaluationCrossSinkPolicyOption", "0.5.1")
  operationevaluationcrosssinkpolicyoption: Option[OperationEvaluationCrossSinkPolicy] = None
) {
  final case class ExecutionResult(
    response: OperationResponse,
    metadata: RuntimeContext.ExecutionMetadata
  )

  final case class FormattedExecutionResult(
    response: Response,
    metadata: RuntimeContext.ExecutionMetadata
  )

  private var _component_factory: ComponentFactory = new ComponentFactory(
    workingsetclock = _find_global_runtime_context(scopecontext)
      .map(_.executionProfileRuntime.runtimeClock.clock)
      .getOrElse(RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock)
  )
  // Keep the original public constructor descriptor.  SystemNode creation is
  // intentionally internal to construction so invalid node configuration
  // still fails before a binding can be used.
  private val _system_node = SystemNode.createC(configuration).TAKE
  private val _datastore_binding = _system_node.bind()
  private val _active_datastore_lease = new ThreadLocal[SystemNodeResourceLease]()

  private[cncf] def systemNode: SystemNode = _system_node
  private var _component_space: ComponentSpace = ComponentSpace()
  private var _resolver: OperationResolver = OperationResolver.empty
  private val _http_driver: Option[HttpDriver] = httpdriver
  private val _job_engine: JobEngine =
    _find_global_runtime_context(scopecontext)
      .map(x => InMemoryJobEngine.create(x.executionProfileRuntime))
      .getOrElse(InMemoryJobEngine.create())
  private val _event_store: EventStore = EventStore.inMemory
  private lazy val _event_bus: EventBus =
    EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = _event_store))
  private lazy val _workflow_engine: WorkflowEngine = WorkflowEngine.inMemory(this)
  private val _event_receptions = mutable.LinkedHashMap.empty[String, EventReception]
  private val _entity_access_metrics: EntityAccessMetricsRegistry = EntityAccessMetricsRegistry.shared
  private val _component_metrics: ComponentMetricsRegistry = ComponentMetricsRegistry.shared
  private val _operation_evaluation_delivery_runtime = new OperationEvaluationDeliveryRuntime()
  private val _site_base_url_keys = Vector(
    RuntimeConfig.siteBaseUrlKey,
    RuntimeConfig.runtimeSiteBaseUrlKey,
    "cncf.site.base-url",
    "cncf.runtime.site.base-url"
  )
  private var _descriptor: Option[GenericSubsystemDescriptor] = None
  private var _assembly_admission_report: Option[SubsystemAssemblyAdmission.Report] = None
  private var _component_api_resolver: ComponentApiResolver = ComponentApiResolver.empty
  private lazy val _spi_invoker: SpiInvoker = SpiInvoker._create(this)
  private var _resolved_security_wiring: ResolvedSecurityWiring = ResolvedSecurityWiring.empty
  private var _controlled_test_execution: Boolean = false
  private lazy val _subsystem_user_mode_c: Consequence[SubsystemUserModeResolution] =
    SubsystemUserMode.resolveForSubsystem(configuration, this)
  private var _user_notification_forwarding_registered: Boolean = false
  private var _service_container_runtime: Option[ServiceContainerRuntime] = None
  private var _mcp_client_runtime: Option[CodexMcpRuntimeAssembly] = None
  private var _operation_tool_runtime: Option[OperationToolRuntimeRegistry] = None
  private val _operation_evaluation_cross_sink_policy =
    operationevaluationcrosssinkpolicyoption
      .orElse(scopecontext.flatMap(_.operationEvaluationCrossSinkPolicyOption))
      .getOrElse(OperationEvaluationCrossSinkPolicy.disabled)

  def globalRuntimeContext: GlobalRuntimeContext = {
    val a = _find_global_runtime_context(scopecontext)
    a orElse GlobalRuntimeContext.current getOrElse {
      Consequence.RAISE.UnreachableReached
    }
  }

  @annotation.tailrec
  private def _find_global_runtime_context(p: Option[ScopeContext]): Option[GlobalRuntimeContext] =
    p match {
      case Some(s) => s match {
        case m: GlobalRuntimeContext => Some(m)
        case m => _find_global_runtime_context(m.parent)
      }
      case None => None
    }

  def httpDriver: Option[HttpDriver] = _http_driver
  def jobEngine: JobEngine = _job_engine
  def eventStore: EventStore = _event_store
  def eventBus: EventBus = _event_bus
  def workflowEngine: WorkflowEngine = _workflow_engine
  def eventReceptions: Map[String, EventReception] = _event_receptions.toMap
  def entityAccessMetrics: EntityAccessMetricsRegistry = _entity_access_metrics
  def componentMetrics: ComponentMetricsRegistry = _component_metrics
  def serverEmulatorBaseUrl: String = globalRuntimeContext.serverEmulatorBaseUrl
  def descriptor: Option[GenericSubsystemDescriptor] = _descriptor
  def assemblyAdmissionReport: Option[SubsystemAssemblyAdmission.Report] = _assembly_admission_report
  def resolvedSecurityWiring: ResolvedSecurityWiring = _resolved_security_wiring
  def subsystemUserModeC: Consequence[SubsystemUserModeResolution] = _subsystem_user_mode_c
  def executionProfileC: Consequence[SubsystemExecutionProfile] =
    subsystemUserModeC.flatMap(resolution => executionProfileForUserModeC(resolution.mode))

  private[cncf] def executionProfileForUserModeC(
    mode: SubsystemUserMode
  ): Consequence[SubsystemExecutionProfile] = {
    val authentication = _resolved_security_wiring.authentication
    mode match {
      case SubsystemUserMode.Standalone =>
        if (authentication.localSubject.nonEmpty)
          Consequence.success(SubsystemExecutionProfile.Fixed)
        else
          _controlled_test_execution_profile_c
      case SubsystemUserMode.MultiUser =>
        if (authentication.enabledProviders.nonEmpty)
          Consequence.success(SubsystemExecutionProfile.Authenticated)
        else
          Consequence.securityPermissionDenied(
            s"${SubsystemUserMode.CONFIGURATION_KEY}=multi-user requires authenticated-user wiring for the Subsystem."
          )
    }
  }

  private[cncf] def enableControlledTestExecution(): Subsystem = {
    _controlled_test_execution = true
    this
  }

  def directComponentProvides(capability: String): Boolean =
    ComponentCapabilityId.parseC(capability).toOption.exists { expected =>
      _descriptor.flatMap { descriptor =>
        descriptor.implicitRootComponentName.flatMap { rootname =>
          descriptor.componentBindings
            .find(_.runtimeComponentName == rootname)
            .flatMap { rootbinding =>
              descriptor.componentDescriptorOverrides
                .find { component =>
                  GenericSubsystemDescriptor.runtimeComponentName(
                    component.componentName.orElse(component.name).getOrElse("")
                  ) == rootbinding.runtimeComponentName
                }
                .flatMap(_.componentStyleSnapshot)
            }
        }
      }.exists(_.effectiveCapabilities.exists(_.canonical == expected.canonical))
    }

  def mcpClientServerSetIds: Vector[McpServerSetId] =
    _mcp_client_runtime.toVector.flatMap(_.serverSetIds)
  def operationToolSetIds: Vector[OperationToolSetId] =
    _operation_tool_runtime.toVector.flatMap(_.toolSetIds)

  def serviceContainerRuntime(using context: ExecutionContext): Option[ServiceContainerRuntime] =
    _service_container_runtime.map(ServiceContainerRuntimeObservation.observed)

  def serviceContainerRuntimeC(
    serviceid: ServiceContainerId
  )(using context: ExecutionContext): Consequence[ServiceContainerRuntime] = synchronized {
    serviceContainerRuntime match {
      case Some(runtime) => Consequence.success(runtime)
      case None =>
        ServiceContainerRuntimeConfiguration.createC(configuration).flatMap {
          case Some(runtime) =>
            installServiceContainerRuntimeC(runtime).map(_ => ServiceContainerRuntimeObservation.observed(runtime))
          case None => ServiceContainerDiagnostics.gatewayUnavailableC(serviceid)
        }
    }
  }

  def installServiceContainerRuntimeC(runtime: ServiceContainerRuntime): Consequence[Unit] = synchronized {
    _service_container_runtime match {
      case None =>
        _service_container_runtime = Some(runtime)
        Consequence.success(())
      case Some(current) if current.eq(runtime) =>
        Consequence.success(())
      case Some(_) =>
        Consequence.operationConflict("service-container runtime installation", Vector.empty)
    }
  }

  private[cncf] def activateCodexMcpClientRuntimeC(
    path: java.nio.file.Path
  )(using context: ExecutionContext): Consequence[Unit] = synchronized {
    _mcp_client_runtime match {
      case Some(_) =>
        Consequence.operationConflict("MCP client runtime activation", Vector.empty)
      case None =>
        val assemblyresult = for {
          configuration <- CodexMcpRuntimeConfiguration.loadC(path)
          source <- org.goldenport.record.io.RecordSourceLoader.load(configuration.definitionSource)
          assembly <- CodexMcpRuntimeAssembly.createC(source, configuration.policy)
        } yield assembly
        val installed: Consequence[CodexMcpRuntimeAssembly] = assemblyresult.flatMap { assembly =>
          _install_mcp_client_runtime_c(assembly, components) match {
            case Consequence.Success(_) => Consequence.success(assembly)
            case Consequence.Failure(conclusion) =>
              try assembly.close()
              catch { case _: Throwable => () }
              Consequence.Failure[CodexMcpRuntimeAssembly](conclusion)
          }
        }
        installed match {
          case Consequence.Success(assembly) =>
            _mcp_client_runtime = Some(assembly)
            Consequence.unit
          case Consequence.Failure(conclusion) =>
            Consequence.Failure[Unit](conclusion)
        }
    }
  }

  private[cncf] def activateOperationToolRuntimeC(
    path: java.nio.file.Path
  )(using context: ExecutionContext): Consequence[Unit] = synchronized {
    _operation_tool_runtime match {
      case Some(_) =>
        Consequence.operationConflict("Operation tool runtime activation", Vector.empty)
      case None =>
        val result = for {
          configuration <- OperationToolRuntimeConfiguration.loadC(path)
          runtime <- OperationToolRuntimeRegistry.createC(this, configuration.admissions)
          _ <- runtime.install(components)
        } yield runtime
        result match {
          case Consequence.Success(runtime) =>
            _operation_tool_runtime = Some(runtime)
            Consequence.unit
          case Consequence.Failure(conclusion) => Consequence.Failure[Unit](conclusion)
        }
    }
  }

  private var _shutdown_in_progress: Boolean = false
  private var _shutdown_result: Option[Consequence[Vector[ServiceContainerCleanupOutcome]]] = None

  def shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]] = {
    val owner = synchronized {
      _shutdown_result match {
        case Some(_) => false
        case None if _shutdown_in_progress => false
        case None =>
          _shutdown_in_progress = true
          true
      }
    }
    if (owner) {
      val result = _shutdown_owned_resources_c()
      synchronized {
        _shutdown_result = Some(result)
        _shutdown_in_progress = false
        notifyAll()
      }
      result
    } else
      _await_shutdown_c()
  }

  private def _shutdown_owned_resources_c(): Consequence[Vector[ServiceContainerCleanupOutcome]] = {
    val jobresult =
      try {
        _job_engine.quiesce()
        Consequence.success(())
      } catch {
        case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
      }
    val serviceresult = _service_container_runtime
      .map(ServiceContainerRuntimeObservation.shutdownC)
      .getOrElse(Consequence.success(Vector.empty))
    val mcpresult = _mcp_client_runtime match {
      case Some(runtime) =>
        try {
          runtime.close()
          Consequence.unit
        } catch {
          case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
        }
      case None => Consequence.unit
    }
    val evaluationresult =
      try {
        _operation_evaluation_delivery_runtime.close()
        Consequence.unit
      } catch {
        case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
      }
    // A Subsystem owns its logical binding and its local workers, never the
    // SystemNode's physical pool.  Node finalization is performed by the
    // owning runtime adapter after this cleanup has released the binding.
    val bindingdrainresult = _datastore_binding.drainC(() => _job_engine.forceCancel())
    val jobterminalresult =
      try {
        _job_engine.forceCancel()
        Consequence.success(())
      } catch {
        case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
      }
    val bindingresult =
      try {
        _datastore_binding.release()
        Consequence.unit
      } catch {
        case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
      }
    val failures = Vector(jobresult, mcpresult, evaluationresult, serviceresult, bindingdrainresult, jobterminalresult, bindingresult).collect {
      case Consequence.Failure(conclusion) => conclusion
    }
    failures.reduceOption(_ ++ _) match {
      case Some(conclusion) => Consequence.Failure(conclusion)
      case None => serviceresult
    }
  }

  def shutdown(): Unit = {
    val _ = shutdownC()
  }

  private def _await_shutdown_c(): Consequence[Vector[ServiceContainerCleanupOutcome]] = synchronized {
    while (_shutdown_result.isEmpty)
      wait()
    _shutdown_result.get
  }

  def withDescriptor(descriptor: GenericSubsystemDescriptor): Subsystem = {
    _controlled_test_execution = false
    _descriptor = Some(descriptor)
    _resolved_security_wiring = ResolvedSecurityWiring.resolve(_descriptor, components)
    _ensure_user_notification_event_forwarding()
    this
  }

  def withAssemblyAdmissionReport(
    report: SubsystemAssemblyAdmission.Report
  ): Subsystem = {
    _assembly_admission_report = Some(report)
    this
  }

  def componentApiResolver: ComponentApiResolver =
    _component_api_resolver

  def spiInvoker: SpiInvoker =
    _spi_invoker

  def withComponentApiResolver(resolver: ComponentApiResolver): Subsystem = {
    _component_api_resolver = _component_api_resolver.merge(resolver)
    this
  }

  def setup(cf: ComponentFactory): Subsystem =
    _or_raise(setupC(cf))

  def setupC(cf: ComponentFactory): Consequence[Subsystem] = {
    _component_factory = cf
    cf.discoverC().flatMap(addC)
  }

  def add(comps: Seq[Component]): Subsystem =
    _or_raise(addC(comps))

  def addC(comps: Seq[Component]): Consequence[Subsystem] =
    _prepare_components_c(comps).map { injected =>
      _component_space = _component_space.add(injected)
      _rebuild_resolver()
      this
    }

  def add(bundle: Component.Bundle): Subsystem =
    add(bundle.participants)

  def add(component: Component): Subsystem =
    add(Vector(component))

  def upsert(comps: Seq[Component]): Subsystem =
    _or_raise(upsertC(comps))

  def upsertC(comps: Seq[Component]): Consequence[Subsystem] =
    _prepare_components_c(comps).map { injected =>
      _component_space = _component_space.upsert(injected)
      _rebuild_resolver()
      this
    }

  private def _prepare_components_c(
    comps: Seq[Component]
  ): Consequence[Vector[Component]] =
    _sequence(comps.toVector.map(_component_factory.bootstrapC)).flatMap { bootstrapped =>
      val injected = bootstrapped.map(x => _inject_context(x.name, x))
      injected.foreach(_bind_runtime_services)
      val mcpc = _mcp_client_runtime.fold(Consequence.unit) { runtime =>
        _install_mcp_client_runtime_c(runtime, injected)
      }
      mcpc.flatMap { _ =>
        _operation_tool_runtime.fold(Consequence.unit) { runtime =>
          runtime.install(injected)
        }
      }.map(_ => injected)
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

  def registerEventReception(
    @deprecatedName("componentName", "0.5.1") componentname: String,
    reception: EventReception
  ): Subsystem = {
    _event_receptions.update(componentname, reception)
    this
  }

  // def addComponent(name: String, comp: Component): Subsystem = {
  //   val c = _inject_context(name, comp)
  //   _component_space = _component_space.add(c)
  //   this
  // }

  // TODO SubsystemContext extends ScopeContext
  private val _subsystem_scope_context: ScopeContext =
    scopecontext
      .map(ScopeContext.withOperationEvaluationCrossSinkPolicy(
        _,
        _operation_evaluation_cross_sink_policy
      ))
      .getOrElse {
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = name,
          parent = None,
          observabilityContext = ExecutionContext.create().observability,
          operationEvaluationCrossSinkPolicyOption =
            Some(_operation_evaluation_cross_sink_policy)
        )
      }

  private def _inject_context(name: String, comp: Component): Component = {
    val sc = Component.Context(
      name = name,
      parent = _subsystem_scope_context,
      comp,
      componentOrigin = comp.origin
    )
    comp.withScopeContext(sc)
  }

  private def _bind_runtime_services(component: Component): Unit = {
    val _ = component.withEventStore(_event_store)
    component.jobEngine match {
      case m: InMemoryJobEngine =>
        m.withEventStore(_event_store).withEventBus(_event_bus)
      case _ =>
        ()
    }
    component.eventReception.foreach(registerEventReception(component.name, _))
  }

  private def _install_mcp_client_runtime_c(
    runtime: CodexMcpRuntimeAssembly,
    targetcomponents: Seq[Component]
  ): Consequence[Unit] =
    runtime.installC(targetcomponents)

  // private val _components: Map[String, Component] =
  //   components.map { case (componentname, component) =>
  //     val sc = _subsystem_scope_context.createChildScope(
  //       ScopeKind.Component,
  //       componentname
  //     )
  //     component.withScopeContext(sc)
  //     componentname -> component
  //   }

  // private val _component_ids: Map[String, ComponentId] =
  //   _components.keys.map { name =>
  //     name -> _component_id(name)
  //   }.toMap

  // private val _component_instance_ids: Map[String, ComponentInstanceId] =
  //   _component_ids.map { case (name, id) =>
  //     name -> _component_instance_id(id)
  //   }

  // private val _component_space: ComponentSpace =
  //   new ComponentSpace(
  //     _component_instance_ids.map { case (name, id) =>
  //       id -> _components(name)
  //     },
  //     _component_ids.map { case (name, id) =>
  //       id -> _component_instance_ids(name)
  //     }
  //   )

  def components: Vector[Component] = _component_space.components

  def findComponent(name: String): Option[Component] =
    _component_space.find(NameLocator(name))

  def resolver: OperationResolver = _resolver

  def operationResolver: OperationResolver = _resolver

  def configurationValue(key: String): Option[org.goldenport.configuration.ConfigurationValue] =
    configuration.configuration.values.get(key)

  def configurationOrEmpty: org.goldenport.configuration.Configuration =
    configuration.configuration

  private def _rebuild_resolver(): Unit = {
    _resolver = OperationResolver.build(_component_space.components)
    _resolved_security_wiring = ResolvedSecurityWiring.resolve(_descriptor, _component_space.components)
    _ensure_user_notification_event_forwarding()
  }

  private def _ensure_user_notification_event_forwarding(): Unit =
    if (!_user_notification_forwarding_registered) {
      _event_bus.register(UserNotificationEventForwarder.subscription(this))
      _user_notification_forwarding_registered = true
    }

  def executeHttp(req: HttpRequest): HttpResponse =
    executeHttpWithMetadata(req).response

  private[cncf] def bindManagedApplicationDataStoreC(
    dataStoreSpace: org.goldenport.cncf.datastore.DataStoreSpace,
    environment: org.goldenport.cncf.datastore.ComponentDataStore.Environment,
    componentName: String,
    datastoreName: String
  ): Consequence[Unit] =
    _with_managed_datastore_lease_c { lease =>
      dataStoreSpace.bindManagedApplicationDataStoreC(
        environment,
        componentName,
        datastoreName,
        _datastore_binding,
        lease,
        _system_node.hmacKey
      )
    }

  private[cncf] def resolveManagedComponentDataStoreC(
    environment: org.goldenport.cncf.datastore.ComponentDataStore.Environment,
    componentName: String,
    datastoreName: String
  ): Consequence[org.goldenport.cncf.datastore.DataStore] =
    Option(_active_datastore_lease.get()) match {
      case Some(lease) =>
        _datastore_binding.inheritLeaseC(lease).flatMap { inherited =>
          org.goldenport.cncf.datastore.ComponentDataStore
            .resolveManagedForDataStoreSpaceC(
              environment,
              org.goldenport.cncf.datastore.ComponentDataStore.Request(componentName, datastoreName),
              _datastore_binding,
              inherited,
              _system_node.hmacKey
            )
            .flatMap(_.map(Consequence.success).getOrElse(
              Consequence.dataStoreUnavailable("component application datastore is unavailable")
            ))
        }
      case None =>
        Consequence.stateInvalid("managed component datastore access requires an active SystemNode lease")
    }

  private[cncf] def _with_managed_datastore_lease_c[A](
    f: SystemNodeResourceLease => Consequence[A]
  ): Consequence[A] =
    Option(_active_datastore_lease.get()) match {
      case Some(lease) =>
        _datastore_binding.inheritLeaseC(lease).flatMap(f)
      case None =>
        _datastore_binding.acquireLeaseC().flatMap { lease =>
          _active_datastore_lease.set(lease)
          try {
            f(lease)
          } finally {
            _active_datastore_lease.remove()
            lease.release()
          }
        }
    }

  def executeHttpWithMetadata(req: HttpRequest): HttpExecutionResult = {
    _resolve_route(req) match {
      case Some((component, service, operation)) =>
        _execute_http(component, service, operation, req)
      case None =>
        HttpExecutionResult(_not_found(), RuntimeContext.ExecutionMetadata.empty)
    }
  }

  def execute(request: Request): Consequence[Response] = {
    executeResponseWithMetadata(request).map(_.response)
  }

  def executeResponseWithMetadata(request: Request): Consequence[FormattedExecutionResult] = {
    executeWithMetadata(request).map { result =>
      FormattedExecutionResult(
        _to_response(request, result.response, result.metadata),
        result.metadata
      )
    }
  }

  def executeOperationResponse(request: Request): Consequence[OperationResponse] = {
    executeWithMetadata(request).map(_.response)
  }

  /** Executes an admitted component call without replacing the caller's execution context. */
  def executeOperationResponseInContext(
    request: Request,
    executioncontext: ExecutionContext
  ): Consequence[OperationResponse] =
    _execute_operation_response_in_context(request, executioncontext)

  /**
   * Executes an admitted component call as a synchronous child invocation.
   * Security, runtime configuration, and observability remain inherited, while
   * the caller's active job task is not reused as the child command's task.
   */
  def executeOperationResponseInChildContext(
    request: Request,
    executioncontext: ExecutionContext
  ): Consequence[OperationResponse] =
    _execute_operation_response_in_context(
      request,
      ExecutionContext.withJobContext(executioncontext, org.goldenport.cncf.job.JobContext.empty)
    )

  /** Executes a framework-admitted in-process call with the caller's existing context. */
  private[cncf] def executeOperationResponse(
    request: Request,
    executioncontext: ExecutionContext
  ): Consequence[OperationResponse] =
    _execute_operation_response_in_context(request, executioncontext)

  private def _execute_operation_response_in_context(
    request: Request,
    executioncontext: ExecutionContext
  ): Consequence[OperationResponse] = {
    val result = for {
      route <- _resolve_route(request) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.operationNotFound("operation route")
      }
      normalized <- _prepare_filebundle_parameters(route._3, request)
      response <- _execute_resolved_operation(route, normalized, executioncontext)
    } yield response
    _observe_execute_failure(request, result)
    result
  }

  def executeWithMetadata(request: Request): Consequence[ExecutionResult] = {
    _execute_with_metadata(request, None)
  }

  def executeWithMetadata(
    request: Request,
    @deprecatedName("httpRequest", "0.5.1") httprequest: HttpRequest
  ): Consequence[ExecutionResult] =
    _execute_with_metadata(request, Some(httprequest))

  def executeQueryOnlyWithMetadata(
    request: Request
  )(using executioncontext: ExecutionContext): Consequence[ExecutionResult] = {
    _execute_query_only_with_metadata(request, None)
  }

  private def _execute_with_metadata(
    request: Request,
    httprequest: Option[HttpRequest]
  ): Consequence[ExecutionResult] = {
    val requestwithhttpproperties =
      httprequest
        .map(req => _with_framework_properties(request, req))
        .getOrElse(request)
    var lastexecutionmetadata = RuntimeContext.ExecutionMetadata.empty
    val r: Consequence[ExecutionResult] = for {
      route <- _resolve_route(requestwithhttpproperties) match {
        case Some(r) =>
          Consequence.success(r)
        case None =>
          Consequence.operationNotFound("operation route")
      }
      normalizedrequest <- _prepare_filebundle_parameters(route._3, requestwithhttpproperties)
      profile <- executionProfileC
      response <- {
        val (component, _, _) = route
        IngressSecurityResolver.resolve(
          profile,
          component.logic.executionContext(),
          _request_security_attributes(normalizedrequest)
        ).flatMap { security =>
          val executioncontext =
            _with_http_runtime_parameters(security.executionContext, httprequest)
          _execute_resolved_operation(route, normalizedrequest, executioncontext).map { result =>
            lastexecutionmetadata = executioncontext.runtime.executionMetadata
            ExecutionResult(result, lastexecutionmetadata)
          }.recoverWith { conclusion =>
            lastexecutionmetadata = executioncontext.runtime.executionMetadata
            Consequence.Failure(conclusion)
          }
        }
      }
    } yield response
    _observe_execute_failure(requestwithhttpproperties, r)
    httprequest match {
      case Some(_) =>
        r.recover { conclusion =>
          ExecutionResult(
            OperationResponse.Http(_failure_response(conclusion)),
            lastexecutionmetadata
          )
        }
      case None =>
        r
    }
  }

  private def _execute_resolved_operation(
    route: (Component, ServiceDefinition, OperationDefinition),
    request: Request,
    executioncontext: ExecutionContext,
    queryonly: Boolean = false
  ): Consequence[OperationResponse] = {
    _with_managed_datastore_lease_c { _ =>
      val (component, _, _) = route
      val domainrequest = _domain_request(request)
      given ExecutionContext = executioncontext
      _authorize_operation(route, executioncontext).flatMap { _ =>
        if (executioncontext.operationEvaluation.invocation.isEmpty)
          executioncontext.runtime.clearExecutionMetadata()
        val preparedcontext = _prepare_operation_evaluation_context(route, executioncontext)
        val attemptcapture = _operation_evaluation_attempt_capture(route)
        val operationdomainrequest = _operation_business_request(route, domainrequest)
        val admittedcontext = _resolve_operation_evaluation_admission(route, preparedcontext)
        val result = admittedcontext.flatMap { activecontext =>
          val operationrequest = component.logic.makeOperationRequest(operationdomainrequest)
          _observe_operation_request_validation_failure(
            route,
            operationdomainrequest,
            operationrequest,
            activecontext
          )
          operationrequest.flatMap {
            case action: QueryAction =>
              component.logic._execute_action(
                action,
                activecontext,
                _operation_evaluation_task_decorator(route, domainrequest, attemptcapture)
              )
            case action: Action if !queryonly =>
              component.logic._execute_action(
                action,
                activecontext,
                _operation_evaluation_task_decorator(route, domainrequest, attemptcapture)
              )
            case action: Action =>
              Consequence.operationInvalid(s"CompositeQuery accepts only Query operations: ${action.request.name}")
            case _ =>
              Consequence.argumentInvalid("OperationRequest must be Action")
          }
        }
        result match {
          case Consequence.Failure(conclusion) =>
            attemptcapture._record_admission_failure(conclusion, preparedcontext)
          case Consequence.Success(_) =>
            ()
        }
        result
      }
    }
  }

  private def _prepare_operation_evaluation_context(
    route: (Component, ServiceDefinition, OperationDefinition),
    context: ExecutionContext
  ): ExecutionContext = {
    ExecutionContext
      .prepareOperationEvaluation(context, _operation_evaluation_identity(route))
      .toOption
      .getOrElse(context)
  }

  private def _resolve_operation_evaluation_admission(
    route: (Component, ServiceDefinition, OperationDefinition),
    context: ExecutionContext
  ): Consequence[ExecutionContext] =
    _operation_evaluation_declaration(route) match {
      case None =>
        Consequence.success(context)
      case Some(declaration) =>
        val operation = _operation_evaluation_identity(route)
        val request = OperationEvaluationAdmissionRequest(operation, declaration)
        given ExecutionContext = context
        Consequence.run(
          context.cncfCore.scope.operationEvaluationResolver.resolve(request)
        ) match {
          case Consequence.Failure(conclusion) =>
            _operation_evaluation_admission_failure(operation, conclusion, context)
          case Consequence.Success(admitted: OperationEvaluationAdmission.Admitted) =>
            _validate_operation_evaluation_admission(declaration, admitted).flatMap { _ =>
              ExecutionContext.admitOperationEvaluation(
                context,
                admitted.corpus,
                admitted.experiment,
                admitted.assignment
              ).map { activecontext =>
                context.runtime.noteOperationEvaluationAdmission(
                  OperationEvaluationAdmissionDiagnostic.admitted(operation)
                )
                activecontext
              }
            }.recoverWith { conclusion =>
              _operation_evaluation_admission_failure(operation, conclusion, context)
            }
          case Consequence.Success(OperationEvaluationAdmission.Unavailable(rawlimitations)) =>
            val limitations =
              if (rawlimitations.nonEmpty) rawlimitations
              else Vector(OperationEvaluationLimitation(OperationEvaluationLimitationKind.Unavailable))
            if (_operation_evaluation_admission_required(declaration))
              OperationEvaluationAdmissionDiagnostic.rejectedC(operation, limitations) match {
                case Consequence.Failure(conclusion) =>
                  _operation_evaluation_admission_failure(operation, conclusion, context)
                case Consequence.Success(diagnostic) =>
                  context.runtime.noteOperationEvaluationAdmission(diagnostic)
                  Consequence.serviceUnavailable("required operation evaluation admission is unavailable")
              }
            else
              OperationEvaluationAdmissionDiagnostic.unavailableC(operation, limitations) match {
                case Consequence.Failure(conclusion) =>
                  _operation_evaluation_admission_failure(operation, conclusion, context)
                case Consequence.Success(diagnostic) =>
                  context.runtime.noteOperationEvaluationAdmission(diagnostic)
                  Consequence.success(context)
              }
        }
    }

  private def _operation_evaluation_admission_failure(
    operation: OperationEvaluationOperationIdentity,
    conclusion: org.goldenport.Conclusion,
    context: ExecutionContext
  ): Consequence[ExecutionContext] = {
    context.runtime.noteOperationEvaluationAdmission(
      OperationEvaluationAdmissionDiagnostic.failed(operation, conclusion)
    )
    Consequence.Failure(conclusion)
  }

  private def _validate_operation_evaluation_admission(
    declaration: CmlOperationEvaluationDeclaration,
    admission: OperationEvaluationAdmission.Admitted
  ): Consequence[Unit] = {
    val emptyadmission =
      admission.corpus.isEmpty && admission.experiment.isEmpty && admission.assignment.isEmpty
    val undeclaredcorpus = declaration.corpus.isEmpty && admission.corpus.nonEmpty
    val undeclaredexperiment =
      declaration.experiment.isEmpty && (admission.experiment.nonEmpty || admission.assignment.nonEmpty)
    val incompletexperiment = admission.experiment.nonEmpty != admission.assignment.nonEmpty
    val missingrequiredcorpus =
      declaration.corpus.exists(_.admission == EvaluationAdmissionRequirement.Required) &&
        admission.corpus.isEmpty
    val missingrequiredexperiment =
      declaration.experiment.exists(_.admission == EvaluationAdmissionRequirement.Required) &&
        (admission.experiment.isEmpty || admission.assignment.isEmpty)
    if (emptyadmission)
      Consequence.stateInvalid("operation evaluation resolver returned an empty admission")
    else if (undeclaredcorpus)
      Consequence.stateInvalid("operation evaluation resolver returned undeclared Corpus membership")
    else if (undeclaredexperiment)
      Consequence.stateInvalid("operation evaluation resolver returned undeclared Experiment assignment")
    else if (incompletexperiment)
      Consequence.stateInvalid("operation evaluation Experiment correlation and assignment must be complete")
    else if (missingrequiredcorpus || missingrequiredexperiment)
      Consequence.serviceUnavailable("required operation evaluation admission is incomplete")
    else
      Consequence.unit
  }

  private def _operation_evaluation_declaration(
    route: (Component, ServiceDefinition, OperationDefinition)
  ): Option[CmlOperationEvaluationDeclaration] = {
    val (component, _, operation) = route
    component.operationDefinitions
      .find(definition => NamingConventions.equivalentByNormalized(definition.name, operation.name))
      .flatMap(_.evaluation)
      .filterNot(_.isEmpty)
  }

  private def _operation_evaluation_admission_required(
    declaration: CmlOperationEvaluationDeclaration
  ): Boolean =
    declaration.corpus.exists(_.admission == EvaluationAdmissionRequirement.Required) ||
      declaration.experiment.exists(_.admission == EvaluationAdmissionRequirement.Required)

  private def _operation_evaluation_identity(
    route: (Component, ServiceDefinition, OperationDefinition)
  ): OperationEvaluationOperationIdentity = {
    val (component, service, operation) = route
    OperationEvaluationOperationIdentity.fromResolvedRoute(component.name, service.name, operation.name)
  }

  private def _operation_evaluation_task_decorator(
    route: (Component, ServiceDefinition, OperationDefinition),
    request: Request,
    attemptcapture: OperationEvaluationAttemptCapture,
    executionscope: Option[ScopeContext] = None
  ): org.goldenport.cncf.job.ActionTask => org.goldenport.cncf.job.JobTask =
    task => new OperationEvaluationActionTask(
      task,
      attemptcapture,
      (response, context) => _apply_operation_association_bindings(route, request, response, context),
      executionscope
    )

  private def _operation_evaluation_attempt_capture(
    route: (Component, ServiceDefinition, OperationDefinition)
  ): OperationEvaluationAttemptCapture =
    new OperationEvaluationAttemptCapture(
      _operation_evaluation_identity(route),
      _operation_evaluation_delivery_runtime
    )

  private[cncf] def _prepare_operation_task(
    action: Action,
    task: org.goldenport.cncf.job.ActionTask,
    context: ExecutionContext
  ): Consequence[(org.goldenport.cncf.job.JobTask, ExecutionContext)] =
    _resolve_route(action.request) match {
      case Some(route) =>
        _prepare_operation_task(route, action, task, context)
      case None =>
        Consequence.operationNotFound("operation route")
    }

  private[cncf] def _prepare_component_operation_task(
    action: Action,
    task: org.goldenport.cncf.job.ActionTask,
    context: ExecutionContext
  ): Consequence[(org.goldenport.cncf.job.JobTask, ExecutionContext)] =
    _resolve_route(action.request) match {
      case Some(route) =>
        _prepare_operation_task(route, action, task, context)
      case None =>
        Consequence.success(task -> context)
    }

  private def _prepare_operation_task(
    route: (Component, ServiceDefinition, OperationDefinition),
    action: Action,
    task: org.goldenport.cncf.job.ActionTask,
    context: ExecutionContext
  ): Consequence[(org.goldenport.cncf.job.JobTask, ExecutionContext)] =
    _authorize_operation(route, context).flatMap { _ =>
      val targetscope = route._1.scopeContext.createChildScope(ScopeKind.Action, action.name)
      val preparedcontext = _prepare_operation_evaluation_context(route, context.withScope(targetscope))
      val domainrequest = _domain_request(action.request)
      val attemptcapture = _operation_evaluation_attempt_capture(route)
      val admittedcontext = _resolve_operation_evaluation_admission(route, preparedcontext)
      admittedcontext match {
        case Consequence.Failure(conclusion) =>
          attemptcapture._record_admission_failure(conclusion, preparedcontext)
        case Consequence.Success(_) =>
          ()
      }
      admittedcontext.map { activecontext =>
        _operation_evaluation_task_decorator(
          route,
          domainrequest,
          attemptcapture,
          Some(targetscope)
        )(task) -> activecontext
      }
    }

  private[cncf] def _invoke_spi(
    binding: ResolvedSpiBinding,
    selector: SpiOperationSelector,
    record: Record
  )(using executioncontext: ExecutionContext): Consequence[OperationResponse] =
    if (!binding._target_component.subsystem.contains(this))
      Consequence.serviceUnavailable(
        s"resolved SPI binding belongs to another subsystem: provider=${binding.provider.instanceId.canonicalKey}"
      )
    else
      for {
        route <- _resolve_spi_route(binding, selector)
        request = Request.of(
          component = binding.provider.component,
          service = route._2.name,
          operation = route._3.name,
          properties = record.fields.map(field => Property(field.key, field.value.single, None)).toList
        )
        normalized <- _prepare_filebundle_parameters(route._3, request)
        response <- _execute_resolved_operation(route, normalized, executioncontext)
      } yield response

  private def _resolve_spi_route(
    binding: ResolvedSpiBinding,
    selector: SpiOperationSelector
  ): Consequence[(Component, ServiceDefinition, OperationDefinition)] = {
    val component = binding._target_component
    val services = component.protocol.services.services.toVector
    val exposed = binding.operations.exists { operation =>
      NamingConventions.equivalentByNormalized(operation.operation, selector.operation) &&
        selector.service.forall(requested => operation.service.exists(
          declared => NamingConventions.equivalentByNormalized(declared, requested)
        ))
    }
    val matches = if (!exposed) Vector.empty else selector.service match {
      case Some(servicename) =>
        services
          .filter(service => NamingConventions.equivalentByNormalized(service.name, servicename))
          .flatMap(service => service.operations.operations.toVector
            .filter(operation => NamingConventions.equivalentByNormalized(operation.name, selector.operation))
            .map(operation => (component, service, operation)))
      case None =>
        services.flatMap(service => service.operations.operations.toVector
          .filter(operation => NamingConventions.equivalentByNormalized(operation.name, selector.operation))
          .map(operation => (component, service, operation)))
    }
    matches match {
      case Vector(route) =>
        Consequence.success(route)
      case Vector() =>
        if (!exposed)
          Consequence.operationNotFound(
            s"component API operation is not exposed by contract ${binding.provider.contract}: " +
              s"service=${selector.service.getOrElse("*")}, operation=${selector.operation}"
          )
        else
          Consequence.operationNotFound(
            s"component API operation: component=${binding.provider.component}, " +
              s"instance=${binding.provider.instanceId.instance}, service=${selector.service.getOrElse("*")}, " +
              s"operation=${selector.operation}"
          )
      case xs =>
        Consequence.operationInvalid(
          s"ambiguous component API operation: component=${binding.provider.component}, " +
            s"instance=${binding.provider.instanceId.instance}, operation=${selector.operation}, candidates=${xs.size}"
        )
    }
  }

  private def _execute_query_only_with_metadata(
    request: Request,
    httprequest: Option[HttpRequest]
  )(using executioncontext: ExecutionContext): Consequence[ExecutionResult] = {
    val r: Consequence[ExecutionResult] = for {
      route <- _resolve_route(request) match {
        case Some(r) =>
          Consequence.success(r)
        case None =>
          Consequence.operationNotFound("operation route")
      }
      normalizedrequest <- _prepare_filebundle_parameters(route._3, request)
      response <- {
        val resolvedexecutioncontext =
          _with_http_runtime_parameters(executioncontext, httprequest)
        given ExecutionContext = resolvedexecutioncontext
        if (
          resolvedexecutioncontext.framework.traceJob ||
          _query_only_trace_job_requested(normalizedrequest)
        ) {
          Consequence.operationInvalid("CompositeQuery accepts only direct Query execution; trace-job is not allowed")
        } else
          _execute_resolved_operation(route, normalizedrequest, resolvedexecutioncontext, queryonly = true).map { response =>
            ExecutionResult(response, resolvedexecutioncontext.runtime.executionMetadata)
          }
      }
    } yield response
    _observe_execute_failure(request, r)
    r
  }

  private def _query_only_trace_job_requested(
    request: Request
  ): Boolean = {
    val keys = Set(
      RuntimeConfig.debugTraceJobKey,
      RuntimeConfig.runtimeDebugTraceJobKey,
      "cncf.debug.trace-job",
      "cncf.runtime.debug.trace-job",
      "x-textus-debug-trace-job"
    ).map(_.toLowerCase(java.util.Locale.ROOT))
    request.properties.exists { property =>
      keys.contains(property.name.toLowerCase(java.util.Locale.ROOT)) &&
        (property.value.toString.trim.toLowerCase(java.util.Locale.ROOT) match {
          case "true" | "1" | "yes" | "on" => true
          case _ => false
        })
    }
  }

  private def _apply_operation_association_bindings(
    route: (Component, ServiceDefinition, OperationDefinition),
    request: Request,
    response: OperationResponse,
    context: ExecutionContext
  ): Consequence[OperationResponse] = {
    val (component, _, operation) = route
    given ExecutionContext = context
    val childentitybindings =
      _operation_child_entity_bindings(component, operation).filter(_.isAutomaticCreate)
    val associationbindings =
      _operation_association_binding(component, operation).filter(_.isAutomaticCreate).toVector
    val imagebindings =
      _operation_image_binding(component, operation).filter(_.toAssociationBinding.isAutomaticCreate).toVector
    for {
      childsummaries <- childentitybindings.foldLeft(
        Consequence.success(Vector.empty[(CmlOperationChildEntityBinding, ChildEntityBindingSummary)])
      ) { (z, binding) =>
        z.flatMap { xs =>
          _apply_child_entity_binding(component, binding, request, response).map(summary => xs :+ (binding -> summary))
        }
      }
      associationsummaries <- _apply_association_bindings(associationbindings, request, response).recoverWith { conclusion =>
        _compensate_child_entity_bindings(component, childsummaries.reverse)
          .flatMap(_ => Consequence.Failure[Vector[(CmlOperationAssociationBinding, Vector[AssociationBindingAttachResult])]](conclusion))
      }
      _ <- imagebindings.foldLeft(Consequence.unit) { (z, binding) =>
        z.flatMap(_ => _apply_image_binding(component, binding, request, response))
      }.recoverWith { conclusion =>
        _compensate_association_bindings(associationsummaries.reverse)
          .flatMap(_ => _compensate_child_entity_bindings(component, childsummaries.reverse))
          .flatMap(_ => Consequence.Failure[Unit](conclusion))
      }
    } yield response
  }

  private def _operation_business_request(
    route: (Component, ServiceDefinition, OperationDefinition),
    request: Request
  ): Request = {
    val (component, _, operation) = route
    if (_operation_owns_binding_parameters(operation))
      request
    else {
      val declared = _operation_declared_parameter_names(component, operation)
      def keep(name: String): Boolean =
        declared.contains(name) || !_is_operation_binding_parameter(component, operation, name)
      request.copy(
        arguments = request.arguments.filter(arg => keep(arg.name)),
        properties = request.properties.filter(prop => keep(prop.name))
      )
    }
  }

  private def _operation_owns_binding_parameters(
    operation: OperationDefinition
  ): Boolean =
    operation.isInstanceOf[AssociationBindingOperationDefinition] ||
      operation.isInstanceOf[ImageBindingOperationDefinition]

  private def _operation_declared_parameter_names(
    component: Component,
    operation: OperationDefinition
  ): Set[String] = {
    val protocolnames = operation.specification.request.parameters.toVector.flatMap(_.names)
    val cmlnames = component.operationDefinitions
      .find(definition => NamingConventions.equivalentByNormalized(definition.name, operation.name))
      .toVector
      .flatMap(_.parameters.map(_.name))
    (protocolnames ++ cmlnames).toSet
  }

  private def _is_operation_binding_parameter(
    component: Component,
    operation: OperationDefinition,
    name: String
  ): Boolean =
    _operation_image_binding(component, operation).exists(_ => _is_image_binding_parameter(name)) ||
      _operation_association_binding(component, operation).exists(binding => _is_association_binding_parameter(binding, name))

  private def _is_image_binding_parameter(
    name: String
  ): Boolean =
    name.startsWith("imageAttachments.") ||
      name.startsWith("blob.") ||
      name.startsWith("blobId.")

  private def _is_association_binding_parameter(
    binding: CmlOperationAssociationBinding,
    name: String
  ): Boolean =
    (binding.parameters ++
      binding.sourceEntityIdParameters ++
      binding.targetIdParameters ++
      binding.sortOrderParameters).contains(name) ||
      binding.targetIdParameters.exists(p => name == s"${p}.sortOrder")

  private def _apply_association_bindings(
    bindings: Vector[CmlOperationAssociationBinding],
    request: Request,
    response: OperationResponse
  )(using ExecutionContext): Consequence[Vector[(CmlOperationAssociationBinding, Vector[AssociationBindingAttachResult])]] =
    bindings.foldLeft(Consequence.success(Vector.empty[(CmlOperationAssociationBinding, Vector[AssociationBindingAttachResult])])) {
      case (z, binding) =>
        z.flatMap { xs =>
          _apply_association_binding(binding, request, response).map(results => xs :+ (binding -> results))
        }.recoverWith { conclusion =>
          z.flatMap(xs => _compensate_association_bindings(xs.reverse))
            .flatMap(_ => Consequence.Failure[Vector[(CmlOperationAssociationBinding, Vector[AssociationBindingAttachResult])]](conclusion))
        }
    }

  private def _apply_child_entity_binding(
    component: Component,
    binding: CmlOperationChildEntityBinding,
    request: Request,
    response: OperationResponse
  )(using ExecutionContext): Consequence[ChildEntityBindingSummary] =
    ChildEntityBindingWorkflow(component).createChildren(binding, request, response)

  private def _compensate_child_entity_bindings(
    component: Component,
    summaries: Vector[(CmlOperationChildEntityBinding, ChildEntityBindingSummary)]
  )(using ExecutionContext): Consequence[Unit] = {
    val workflow = ChildEntityBindingWorkflow(component)
    summaries.foldLeft(Consequence.unit) {
      case (z, (binding, summary)) =>
        z.flatMap(_ => workflow.compensate(binding, summary))
    }
  }

  private def _compensate_association_bindings(
    summaries: Vector[(CmlOperationAssociationBinding, Vector[AssociationBindingAttachResult])]
  )(using ExecutionContext): Consequence[Unit] =
    summaries.foldLeft(Consequence.unit) {
      case (z, (binding, results)) =>
        z.flatMap { _ =>
          val storagepolicy = _association_storage_policy(binding.domain)
          AssociationBindingWorkflow(
            AssociationRepository.entityStore(storagepolicy),
            storagepolicy
          ).compensate(results)
        }
    }

  private def _apply_association_binding(
    binding: CmlOperationAssociationBinding,
    request: Request,
    response: OperationResponse
  )(using ExecutionContext): Consequence[Vector[AssociationBindingAttachResult]] = {
    val storagepolicy = _association_storage_policy(binding.domain)
    val workflow = AssociationBindingWorkflow(
      AssociationRepository.entityStore(storagepolicy),
      storagepolicy
    )
    AssociationBindingWorkflow.extract(binding, request).flatMap {
      case Vector() =>
        Consequence.success(Vector.empty)
      case _ =>
        for {
          sourceid <- AssociationBindingWorkflow.resolveSourceEntityId(binding, request, response)
          results <- workflow.attachExistingTargetResults(sourceid, binding, request)
        } yield results
    }
  }

  private def _apply_image_binding(
    component: Component,
    binding: CmlOperationImageBinding,
    request: Request,
    response: OperationResponse
  )(using ExecutionContext): Consequence[Unit] =
    BlobAttachmentWorkflow.extract(
      request,
      acceptsUpload = binding.acceptsUpload,
      acceptsExistingBlobId = binding.acceptsExistingBlobId
    ).flatMap {
      case attachment if attachment.isEmpty =>
        Consequence.unit
      case attachment =>
        for {
          sourceid <- AssociationBindingWorkflow.resolveSourceEntityId(binding.toAssociationBinding, request, response)
          service <- BlobPayloadSupport.service(component)
          workflow = BlobAttachmentWorkflow(
            service.blobStore,
            BlobRepository.entityStore(),
            AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
          )
          _ <- workflow.attachToEntity(sourceid, attachment)
        } yield ()
    }

  private def _operation_child_entity_bindings(
    component: Component,
    operation: OperationDefinition
  ): Vector[CmlOperationChildEntityBinding] =
    operation match {
      case x: ChildEntityBindingOperationDefinition =>
        x.childEntityBindings
      case _ =>
        component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .map(_.childEntityBindings)
          .getOrElse(Vector.empty)
    }

  private def _operation_association_binding(
    component: Component,
    operation: OperationDefinition
  ): Option[CmlOperationAssociationBinding] =
    operation match {
      case x: AssociationBindingOperationDefinition =>
        Some(x.associationBinding)
      case _ =>
        component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .flatMap(_.associationBinding)
    }

  private def _operation_image_binding(
    component: Component,
    operation: OperationDefinition
  ): Option[CmlOperationImageBinding] =
    operation match {
      case x: ImageBindingOperationDefinition =>
        Some(x.imageBinding)
      case _ =>
        component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .flatMap(_.imageBinding)
    }

  private def _association_storage_policy(domain: String): AssociationStoragePolicy =
    if (domain == AssociationDomain.BlobAttachment.value)
      AssociationStoragePolicy.blobAttachmentDefault
    else if (domain == AssociationDomain.MediaAttachment.value)
      AssociationStoragePolicy.mediaAttachmentDefault
    else if (domain == AssociationDomain.TagAttachment.value)
      AssociationStoragePolicy.tagAttachmentDefault
    else
      AssociationStoragePolicy.shared

  private def _observe_execute_failure[A](
    request: Request,
    r: Consequence[A]
  ): Unit =
    r match {
      case Consequence.Failure(c) =>
        val _ = _subsystem_scope_context.observe_error(
          "execute_failed",
          attributes = Record.data(
            "reason" -> c.status.toString,
            "request" -> request.toString
          )
        )
      case _ =>
        ()
    }

  private def _observe_operation_request_validation_failure[A](
    route: (Component, ServiceDefinition, OperationDefinition),
    request: Request,
    result: Consequence[A],
    context: ExecutionContext
  ): Unit =
    {
      val (component, service, operation) = route
      OperationRequestValidationObserver.observeFailure(
        componentName = component.name,
        serviceName = service.name,
        operationName = operation.name,
        operation = Some(operation),
        request = request,
        result = result,
        context = context
      )
    }

  def executeWired(
    binding: GenericSubsystemResolvedWiringBinding,
    request: Request
  ): Consequence[Subsystem.WiredExecutionResult] =
    executeWired(Some(binding), request)

  def executeWired(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request
  ): Consequence[Subsystem.WiredExecutionResult] =
    for {
      mediatedrequest <- _apply_request_glue(binding, request)
      response <- execute(mediatedrequest)
      mediatedresponse <- _apply_response_glue(binding, mediatedrequest, response)
    } yield mediatedresponse

  def executeAction(action: Action): Consequence[OperationResponse] =
    _with_managed_datastore_lease_c { _ => _resolve_route(action.request) match {
      case Some((component, _, _)) =>
        executionProfileC.flatMap { profile =>
          IngressSecurityResolver.resolve(
            profile,
            component.logic.executionContext(),
            _request_security_attributes(action.request)
          ).flatMap { security =>
            _execute_action_c(action, security.executionContext)
          }
        }
      case None =>
        Consequence.operationNotFound("operation route")
    }}

  private[cncf] def _execute_action_c(
    action: Action,
    context: ExecutionContext
  ): Consequence[OperationResponse] =
    _with_managed_datastore_lease_c { _ => _resolve_route(action.request) match {
      case Some(route) =>
        _execute_action_c(route, action, context)
      case None =>
        Consequence.operationNotFound("operation route")
    }}

  private[cncf] def _execute_component_action_c(
    component: Component,
    action: Action,
    context: ExecutionContext
  ): Consequence[OperationResponse] =
    _with_managed_datastore_lease_c { _ => _resolve_route(action.request) match {
      case Some(route) =>
        _execute_action_c(route, action, context)
      case None =>
        component.logic._execute_action(action, context, identity)
    }}

  private def _execute_action_c(
    route: (Component, ServiceDefinition, OperationDefinition),
    action: Action,
    context: ExecutionContext
  ): Consequence[OperationResponse] =
    _with_managed_datastore_lease_c { _ => _authorize_operation(route, context).flatMap { _ =>
      if (context.operationEvaluation.invocation.isEmpty)
        context.runtime.clearExecutionMetadata()
      val domainrequest = _domain_request(action.request)
      val preparedcontext = _prepare_operation_evaluation_context(route, context)
      val attemptcapture = _operation_evaluation_attempt_capture(route)
      val result = _resolve_operation_evaluation_admission(route, preparedcontext).flatMap { activecontext =>
        route._1.logic._execute_action(
          action,
          activecontext,
          _operation_evaluation_task_decorator(route, domainrequest, attemptcapture)
        )
      }
      result match {
        case Consequence.Failure(conclusion) =>
          attemptcapture._record_admission_failure(conclusion, preparedcontext)
        case Consequence.Success(_) =>
          ()
      }
      result
    }}

  private def _authorize_operation(
    route: (Component, ServiceDefinition, OperationDefinition),
    ctx: ExecutionContext
  ): Consequence[Unit] = {
    val (component, service, operation) = route
    val selector = s"${component.name}.${service.name}.${operation.name}"
    val runtimeconfig = RuntimeConfig.from(configuration)
    val rule = if (component.name == AdminComponent.name)
      Some(AdminAuthorizationPolicy.operationRule(selector, runtimeconfig))
    else operation match {
      case provider: OperationAuthorizationProvider =>
        Some(provider.operationAuthorization(runtimeconfig))
      case _ =>
        _cml_operation_authorization_rule(component, operation.name)
          .orElse(descriptor.flatMap(_.operationAuthorizationRule(selector)))
    }
    rule match {
      case Some(r) =>
        given ExecutionContext = _operation_authorization_context(ctx, runtimeconfig)
        OperationAuthorization.authorize(selector, r)
      case _ =>
        Consequence.unit
    }
  }

  private def _cml_operation_authorization_rule(
    component: Component,
    operationname: String
  ): Option[org.goldenport.cncf.security.OperationAuthorizationRule] =
    component.operationDefinitions
      .find(x => _normalize_operation_name(x.name) == _normalize_operation_name(operationname))
      .flatMap(_.operationAuthorization)

  private def _normalize_operation_name(name: String): String =
    Option(name).getOrElse("").replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT)

  private def _operation_authorization_context(
    ctx: ExecutionContext,
    runtimeconfig: RuntimeConfig
  ): ExecutionContext = {
    val runtime = new RuntimeContext(
      core = ctx.runtime.core,
      unitOfWorkSupplier = () => ctx.unitOfWork,
      unitOfWorkInterpreterFn = ctx.runtime.unitOfWorkInterpreter,
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "operation-authorization",
      context = ctx.runtime.context,
      operationMode = runtimeconfig.operationMode,
      transitionValidationHook = ctx.runtime.transitionValidationHook,
      entityCreateDefaultsPolicy = ctx.runtime.entityCreateDefaultsPolicy
    )
    ExecutionContext.withRuntimeContext(ctx, runtime)
  }

  private def _to_response(
    request: Request,
    response: OperationResponse
  ): Response =
    OperationResponseFormatter.toResponse(request, response, _http_run_mode)

  private def _to_response(
    request: Request,
    response: OperationResponse,
    metadata: RuntimeContext.ExecutionMetadata
  ): Response =
    OperationResponseFormatter.toResponse(request, response, _http_run_mode, metadata)

  private def _apply_request_glue(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request
  ): Consequence[Request] =
    _glue_mode(binding, "request/mode") match {
      case "passthrough" =>
        Consequence.success(request)
      case other =>
        Consequence.operationInvalid(s"unsupported request glue mode: ${other}")
    }

  private def _apply_response_glue(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    request: Request,
    response: Response
  ): Consequence[Subsystem.WiredExecutionResult] = {
    val requestmode = _glue_mode(binding, "request/mode")
    val responsemode = _glue_mode(binding, "response/mode")
    responsemode match {
      case "passthrough" =>
        Consequence.success(
          Subsystem.WiredExecutionResult(
            request = request,
            response = response,
            glueApplied = Record.data(
              "request_mode" -> requestmode,
              "response_mode" -> responsemode
            )
          )
        )
      case other =>
        Consequence.operationInvalid(s"unsupported response glue mode: ${other}")
    }
  }

  private def _glue_mode(
    binding: Option[GenericSubsystemResolvedWiringBinding],
    key: String
  ): String =
    binding
      .map(_.glue)
      .flatMap(_.asMap.get(key))
      .map(_.toString)
      .filter(_.nonEmpty)
      .getOrElse("passthrough")

  private def _domain_request(
    request: Request
  ): Request =
    request.copy(
      properties = request.properties.filterNot(p => _is_framework_or_query_property(p.name)),
      arguments = request.arguments.filterNot(p => _is_framework_or_security_argument(p.name))
    )

  private def _with_http_runtime_parameters(
    ctx: ExecutionContext,
    req: Option[HttpRequest]
  ): ExecutionContext =
    _http_site_base_url_property(ctx, req) match {
      case Some(prop) =>
        val parent = Some(ctx.runtime.resolvedParameters)
        ctx.runtime.setResolvedParameters(
          ResolvedParameters.fromFrameworkProperties(
            properties = List(prop),
            kind = "http",
            parent = parent
          )
        )
        ctx
      case None => ctx
    }

  private def _http_site_base_url_property(
    ctx: ExecutionContext,
    req: Option[HttpRequest]
  ): Option[Property] =
    if (_has_resolved_site_base_url(ctx))
      None
    else
      for {
        request <- req
        scheme <- request.context.scheme.map(_.trim).filter(_.nonEmpty)
        authority <- request.context.authority.map(_.trim).filter(_.nonEmpty)
      } yield Property(RuntimeConfig.siteBaseUrlKey, s"$scheme://$authority", None)

  private def _has_resolved_site_base_url(
    ctx: ExecutionContext
  ): Boolean =
    _site_base_url_keys.exists { key =>
      ctx.runtime.resolvedParameters.get(key)
        .map(param => ResolvedParameter.format_value(param.value).trim)
        .exists(_.nonEmpty)
    }

  private def _is_framework_or_query_property(
    name: String
  ): Boolean =
    if (name == null)
      false
    else if (_is_job_input_property(name))
      false
    else if (_is_operation_origin_slot_property(name))
      false
    else if (
      name.equalsIgnoreCase(
        EntityMutationAdapterDefaults.profilePropertyName
      )
    )
      false
    else if (
      name.equalsIgnoreCase(
        EntityRevisionTransport.observedRevisionPropertyName
      )
    )
      false
    else {
      val lower = name.toLowerCase(java.util.Locale.ROOT)
      name.startsWith("textus.") ||
        name.startsWith("cncf.") ||
        lower == "x-textus-session" ||
        lower.startsWith("x-textus-debug-")
    }

  private def _is_operation_origin_slot_property(
    name: String
  ): Boolean = {
    val lower = Option(name).getOrElse("").toLowerCase(java.util.Locale.ROOT)
      _http_name_matches(lower, "x-textus-debug-request-kind") ||
      _http_name_matches(lower, "x-textus-operation-origin-slot") ||
      _http_name_matches(lower, "textus.operation.origin.slot")
  }

  private def _is_job_input_property(
    name: String
  ): Boolean =
    name == "cncf.job.input" || name.startsWith("cncf.job.input.")

  private def _is_framework_or_security_argument(
    name: String
  ): Boolean =
    if (name == null)
      false
    else {
      val lower = name.toLowerCase(java.util.Locale.ROOT)
      name.startsWith("textus.") ||
        name.startsWith("cncf.") ||
        lower == "x-textus-session" ||
        lower.startsWith("x-textus-debug-") ||
        name.startsWith("security.") ||
        name.startsWith("crud.") ||
        name == "principalId" ||
        name == "principal_id" ||
        name == "subjectId" ||
        name == "subject_id" ||
        name == "privilege" ||
        name == "capability" ||
        name == "capabilities"
    }

  private def _resolve_route(
    req: HttpRequest
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val segments = req.pathParts
    val spec = if (_is_spec_route(segments)) _resolve_spec_route(segments) else None
    spec.orElse {
      val normalizedsegments =
        PathPreNormalizer.rewriteSegments(segments, _http_run_mode, _alias_resolver)
      normalizedsegments match {
        case Vector(componentname, servicename, operationname) =>
          _resolve_route_via_resolver(componentname, servicename, operationname)
        case _ =>
          None
      }
    }
  }

  private def _resolve_route_via_resolver(
    componentname: String,
    servicename: String,
    operationname: String
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val selector = s"$componentname.$servicename.$operationname"
    _resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        val locator = NameLocator(component)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == service)
          operation <- _find_operation(service, operation)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _find_operation(
    service: ServiceDefinition,
    name: String
  ): Option[OperationDefinition] =
    service.operations.operations.find(_.name == name)

  private def _resolve_route(
    request: Request
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    (request.component, request.service) match {
      case (Some(componentname), Some(servicename)) =>
        val locator = NameLocator(componentname)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- _find_operation(service, request.operation)
        } yield (component, service, operation)
      case (None, Some(serviceid)) =>
        _resolve_route(serviceid, request.operation)
      case _ =>
        None
    }
  }

  private def _resolve_route(
    serviceid: String,
    operationname: String
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    serviceid.split("\\.") match {
      case Array(componentname, servicename) =>
        val locator = NameLocator(componentname)
        for {
          component <- _component_space.find(locator)
          service <- component.protocol.services.services.find(_.name == servicename)
          operation <- _find_operation(service, operationname)
        } yield (component, service, operation)
      case _ =>
        None
    }
  }

  private def _resolve_spec_route(
    segments: Vector[String]
  ): Option[(Component, ServiceDefinition, OperationDefinition)] = {
    val specsegments = segments match {
      case Vector("spec-old", rest @ _*) =>
        rest.toVector
      case _ =>
        segments
    }
    val opsegment = specsegments match {
      case Vector("spec", "export", op) =>
        Some(op)
      case Vector("spec", "current", op) =>
        Some(op)
      case Vector("export", op) =>
        Some(op)
      case Vector("current", op) =>
        Some(op)
      case Vector("spec", op) =>
        Some(op)
      case Vector("openapi") =>
        Some("openapi")
      case Vector("openapi.json") =>
        Some("openapi.json")
      case Vector("openapi.html") =>
        Some("openapi.html")
      case _ =>
        None
    }
    opsegment.flatMap { op =>
      val operationname = op match {
        case "openapi" | "openapi.json" => "openapi"
        case other => other
      }
      val locator = NameLocator("spec")
      for {
        component <- _component_space.find(locator)
        service <- component.protocol.services.services.find(_.name == "export")
        operation <- service.operations.operations.find(_.name == operationname)
      } yield (component, service, operation)
    }
  }

  private def _is_spec_route(
    segments: Vector[String]
  ): Boolean =
    segments match {
      case Vector("spec-old", _*) => true
      case Vector("spec", _*) => true
      case Vector("export", _*) => true
      case Vector("current", _*) => true
      case Vector("openapi") => true
      case Vector("openapi.json") => true
      case Vector("openapi.html") => true
      case _ => false
    }

  private def _execute_http(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition,
    req: HttpRequest
  ): HttpExecutionResult = {
//    _ensure_system_context(component)
    val _ = service
    val r: Consequence[(HttpResponse, RuntimeContext.ExecutionMetadata)] = for {
      ingress <- Consequence.fromOption(
        component.protocol.handler.ingresses
          .findByInput(classOf[HttpRequest]),
        "HTTP ingress not configured"
      )
      request0 <- ingress.encode(operation, req)
      request1 = _with_framework_properties(request0, req)
      request = request1.component match {
        case Some(_) => request1
        case None =>
          Request.ofHttpRequest(
            req,
            component = component.name,
            service = service.name,
            operation = operation.name,
            arguments = request1.arguments,
            switches = request1.switches,
            properties = request1.properties
          )
      }
      // enrichedRequest = if (component.name == DebugComponent.name) {
      //   val metadata = List(
      //     Property("http.method", req.method.name, None),
      //     Property("http.path", req.path.asString, None)
      //   )
      //   request.copy(properties = request.properties ++ metadata)
      // } else {
      //   request
      // }
      normalized <- _prepare_filebundle_parameters(operation, request)
      // Route HTTP ingress through the standard request execution path so
      // request-derived execution mode, security, and other ingress context
      // are applied consistently with command/client execution.
      result <- _execute_with_metadata(normalized, Some(req))
      response = result.response match {
        case OperationResponse.Http(http) => http
        case other => _egress(component).encode(operation, _to_response(normalized, other, result.metadata))
      }
    } yield response -> result.metadata
    r match {
      case Consequence.Success((res, metadata)) =>
        HttpExecutionResult(res, metadata)
      case Consequence.Failure(c) =>
        HttpExecutionResult(_failure_response(c), RuntimeContext.ExecutionMetadata.empty)
    }
  }

  private def _egress(
    component: Component
  ): Egress[HttpResponse] =
    component.protocol.handler.egresses
      .findByOutput(classOf[HttpResponse])
      .getOrElse {
        throw new IllegalStateException("HTTP egress not configured")
      }

  private def _prepare_filebundle_parameters(
    operation: OperationDefinition,
    req: Request
  ): Consequence[Request] = {
    val names = operation.specification.request.parameters
      .filter(_is_filebundle_parameter)
      .flatMap(_.names)
      .toSet
    if (names.isEmpty) {
      Consequence.success(req)
    } else {
      for {
        arguments <- Consequence.zipN(req.arguments.map { argument =>
          if (names.contains(argument.name))
            FileBundle.create(argument.name, argument.value).map(v => argument.copy(value = v))
          else
            Consequence.success(argument)
        })
        properties <- Consequence.zipN(req.properties.map { property =>
          if (names.contains(property.name))
            FileBundle.create(property.name, property.value).map(v => property.copy(value = v))
          else
            Consequence.success(property)
        })
      } yield req.copy(arguments = arguments.toList, properties = properties.toList)
    }
  }

  private def _is_filebundle_parameter(
    parameter: ParameterDefinition
  ): Boolean =
    parameter.datatype == XFileBundle ||
      Option(parameter.datatype).map(_.name).exists { name =>
        _normalize_datatype_name(name) == "filebundle"
      }

  private def _normalize_datatype_name(
    name: String
  ): String =
    name.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)

  private def _framework_properties_from_http(
    req: HttpRequest
  ): List[Property] = {
    val query = _http_query_record(req).asMap.toVector.collect {
      case (name, value) if _is_http_framework_key(name) =>
        Property(name, value.toString, None)
    }
    val form = _http_form_framework_passthrough_keys.flatMap { name =>
      req.form.getString(name).filter(_.nonEmpty).map(value => Property(name, value, None))
    }
    val header = req.header.asMap.toVector.collect {
      case (name, value) if _http_name_matches(name, "authorization") =>
        Property("authorization", value.toString, None)
      case (name, value) if _http_name_matches(name, "cookie") =>
        Property("cookie", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-debug-calltree") =>
        Property("x-textus-debug-calltree", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-debug-trace-job") =>
        Property("x-textus-debug-trace-job", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-debug-save-calltree") =>
        Property("x-textus-debug-save-calltree", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-debug-calltree-sql") =>
        Property("x-textus-debug-calltree-sql", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-debug-request-kind") =>
        Property("x-textus-debug-request-kind", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-operation-origin-slot") =>
        Property("x-textus-operation-origin-slot", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-textus-session") =>
        Property("x-textus-session", value.toString, None)
      case (name, value) if _http_name_matches(name, "x-cncf-session") =>
        Property("x-cncf-session", value.toString, None)
      case (name, value)
          if _http_name_matches(
            name,
            EntityMutationAdapterDefaults.profilePropertyName
          ) =>
        Property(
          EntityMutationAdapterDefaults.profilePropertyName,
          value.toString,
          None
        )
      case (name, value)
          if _http_name_matches(
            name,
            EntityRevisionTransport.observedRevisionPropertyName
          ) =>
        Property(
          EntityRevisionTransport.observedRevisionPropertyName,
          value.toString,
          None
        )
    }
    (query ++ form ++ header).toList
  }

  private def _with_framework_properties(
    request: Request,
    httprequest: HttpRequest
  ): Request = {
    val additions = _framework_properties_from_http(httprequest)
    val additionnames = additions.iterator.map(_.name.toLowerCase(java.util.Locale.ROOT)).toSet
    val retained = request.properties.filterNot { property =>
      additionnames.contains(property.name.toLowerCase(java.util.Locale.ROOT))
    }
    request.copy(properties = retained ++ additions)
  }

  private def _http_query_record(
    req: HttpRequest
  ): Record = {
    val fallback = _http_query_record_from_uri(req)
    if (req.query.isEmpty)
      fallback.getOrElse(Record.empty)
    else
      fallback match {
        case Some(record) if !record.isEmpty =>
          Record.create(record.asMap.toVector ++ req.query.asMap.toVector)
        case _ =>
          req.query
      }
  }

  private def _http_query_record_from_uri(
    req: HttpRequest
  ): Option[Record] =
    _http_query_record_from_string(req.context.originalUri)
      .orElse(req.url.flatMap(url => Option(url.getQuery).map(HttpRequest.parseQuery)))

  private def _http_query_record_from_string(
    value: Option[String]
  ): Option[Record] =
    value.flatMap { text =>
      val i = text.indexOf('?')
      if (i < 0)
        None
      else {
        val raw = text.substring(i + 1)
        val j = raw.indexOf('#')
        val query = if (j < 0) raw else raw.substring(0, j)
        if (query.isEmpty) None else Some(HttpRequest.parseQuery(query))
      }
    }

  private def _is_http_framework_key(
    name: String
  ): Boolean =
    name.startsWith("textus.") ||
      name.startsWith("cncf.") ||
      name.startsWith("query.")

  private def _http_name_matches(
    name: String,
    canonical: String
  ): Boolean =
    _http_name_key(name) == _http_name_key(canonical)

  private def _http_name_key(name: String): String =
    Option(name)
      .getOrElse("")
      .toLowerCase(java.util.Locale.ROOT)
      .filter(_.isLetterOrDigit)

  private def _http_form_framework_passthrough_keys: Vector[String] =
    Vector(
      "cncf.job.input",
      "cncf.job.input.fieldName",
      "cncf.job.input.filename",
      "cncf.job.input.contentType",
      "cncf.job.input.byteSize",
      "cncf.job.input.sha256",
      "cncf.job.input.retention",
      "cncf.job.input.ttlSeconds",
      "cncf.job.input.createdAt",
      "cncf.job.input.storage",
      "cncf.job.input.inlineBase64",
      "cncf.job.input.blobId"
    )

  private def _not_found(): HttpResponse =
    HttpResponse.notFound()

  private def _controlled_test_execution_profile_c: Consequence[SubsystemExecutionProfile] =
    if (
      _controlled_test_execution &&
      _resolved_security_wiring.authentication.enabledProviders.isEmpty &&
      _resolved_security_wiring.authentication.localSubject.isEmpty
    )
      Consequence.success(SubsystemExecutionProfile.ControlledTest)
    else if (
      _descriptor.isEmpty &&
      _is_test_runtime &&
      _allows_controlled_test_execution
    )
      Consequence.success(SubsystemExecutionProfile.ControlledTest)
    else
      RuntimeTestDescriptor.load(configuration).flatMap {
        case Some(_) => Consequence.success(SubsystemExecutionProfile.ControlledTest)
        case None => Consequence.securityPermissionDenied(
          "Subsystem execution requires fixed-user or authenticated-user wiring; controlled test execution requires an explicit runtime test descriptor."
        )
      }

  private def _is_test_runtime: Boolean =
    sys.props.get("textus.test").exists { value =>
      val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
      normalized == "true" || normalized == "1" || normalized == "yes" || normalized == "on"
    }

  private def _allows_controlled_test_execution: Boolean = true

  private def _request_security_attributes(request: Request): Map[String, String] = {
    val properties = request.properties.foldLeft(Map.empty[String, String]) { (z, property) =>
      val value = Option(property.value).map(_.toString).getOrElse("")
      if (property.name.nonEmpty && value.nonEmpty) z.updated(property.name, value) else z
    }
    request.arguments.foldLeft(properties) { (z, argument) =>
      val value = Option(argument.value).map(_.toString).getOrElse("")
      if (argument.name.nonEmpty && value.nonEmpty) z.updated(argument.name, value) else z
    }
  }

  private def _internal_error(): HttpResponse =
    HttpResponse.internalServerError()

  private def _failure_response(c: org.goldenport.Conclusion): HttpResponse =
    HttpResponse.text(_http_status(c), c.displayMessage)

  private def _http_status(c: org.goldenport.Conclusion): HttpStatus =
    HttpStatus.fromInt(c.status.webCode.code).getOrElse(HttpStatus.InternalServerError)

  private val _alias_resolver: AliasResolver = aliasResolver
  private val _http_run_mode: RunMode = runmode

  // private def _ensure_system_context(
  //   component: Component
  // ): Unit = {
  //   val system = component.systemContext
  //   val snapshot = system.configSnapshot
  //   val mode = snapshot.get("cncf.mode")
  //   if (!mode.contains("server")) {
  //     val runtimeVersion = CncfVersion.current
  //     val subsystemVersion = version.getOrElse(runtimeVersion)
  //     val updated = snapshot ++ Map(
  //       "cncf.mode" -> "server",
  //       "cncf.subsystem" -> name,
  //       "cncf.runtime.version" -> runtimeVersion,
  //       "cncf.subsystem.version" -> subsystemVersion
  //     )
  //     component.withSystemContext(system.copy(configSnapshot = updated))
  //   }
  // }

  private def _component_id(name: String): ComponentId =
    ComponentId(name)

  private def _component_instance_id(
    id: ComponentId
  ): ComponentInstanceId =
    ComponentInstanceId.default(id)

  private def _header_value(
    header: Record,
    key: String
  ): Option[String] =
    header.asMap.collectFirst {
      case (name, value) if name.equalsIgnoreCase(key) => value.toString
    }
}

object Subsystem {
  import cats.syntax.all.*
  import org.goldenport.Consequence
  import org.goldenport.configuration.ResolvedConfiguration
  import org.goldenport.cncf.cli.RunMode

  final case class WiredExecutionResult(
    request: Request,
    response: Response,
    glueApplied: Record
  )

  // Factories own a newly constructed Subsystem until they return it.  If
  // assembly/bootstrap work fails, release its logical binding first and then
  // terminalize the factory-owned SystemNode.
  private[cncf] def withStartupCleanup[A](subsystem: Subsystem)(f: => A): A =
    try {
      f
    } catch {
      case e: Throwable =>
        try {
          shutdownOwned(subsystem)
        } catch {
          case cleanup: Throwable => e.addSuppressed(cleanup)
        }
        throw e
    }

  private[cncf] def shutdownOwned(subsystem: Subsystem): Unit = {
    var failure: Option[Throwable] = None
    def capture(result: Consequence[_]): Unit = result match {
      case Consequence.Success(_) => ()
      case Consequence.Failure(conclusion) =>
        val throwable = conclusion.getException.getOrElse(new IllegalStateException(conclusion.show))
        failure match {
          case Some(primary) => primary.addSuppressed(throwable)
          case None => failure = Some(throwable)
        }
    }
    try {
      capture(subsystem.shutdownC())
    } finally {
      capture(subsystem.systemNode.shutdownC())
    }
    failure.foreach(throw _)
  }

  // Unused
  final case class Config(
    httpDriver: String,
    mode: RunMode
  )

  object Config {
    def from(conf: ResolvedConfiguration): Consequence[Config] = {
      val httpdriver =
        conf.get[String]("cncf.subsystem.http.driver").flatMap {
          case Some(value) => Consequence.success(value)
          case None        => Consequence.argumentMissing("cncf.subsystem.http.driver")
        }

      val mode =
        conf
          .get[String]("cncf.subsystem.mode")
          .map(_.getOrElse("normal"))
          .flatMap { value =>
            RunMode.from(value) match {
              case Some(runmode) => Consequence.success(runmode)
              case None          => Consequence.argumentInvalid(s"invalid run mode: ${value}")
            }
          }

      (httpdriver, mode).mapN(Config.apply)
    }
  }
}
