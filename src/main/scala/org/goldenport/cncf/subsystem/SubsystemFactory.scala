package org.goldenport.cncf.subsystem

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{ClientConfig, RuntimeConfig}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.builtin.debug.DebugComponent
import org.goldenport.cncf.component.builtin.event.EventComponent
import org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent
import org.goldenport.cncf.component.builtin.metrics.MetricsComponent
import org.goldenport.cncf.component.builtin.messagedeliverystub.MessageDeliveryStubComponent
import org.goldenport.cncf.component.builtin.specification.SpecificationComponent
import org.goldenport.cncf.component.builtin.tag.TagComponent
import org.goldenport.cncf.component.builtin.tool.ToolComponent
import org.goldenport.cncf.component.builtin.workflow.WorkflowComponent
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.http.{FakeHttpDriver, UrlConnectionHttpDriver}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 *  version Jan. 30, 2026
 *  version Feb. 15, 2026
 *  version Mar. 29, 2026
 *  version Apr. 26, 2026
 *  version May.  5, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
object DefaultSubsystemFactory {
  private val _admin = AdminComponent.Factory
  private val _client = ClientComponent.Factory
  private val _spec = SpecificationComponent.Factory()
  private val _subsystem_name = GlobalRuntimeContext.SubsystemName

  def subsystemName: String = _subsystem_name

  def builtinComponents(
  subsystem: Subsystem
  ): Vector[Component] =
    _or_raise(builtinComponentsC(subsystem))

  def builtinComponentsC(
    subsystem: Subsystem
  ): Consequence[Vector[Component]] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
    _sequence(Vector(
      _admin,
      AuthComponent.Factory,
      _client,
      BlobComponent.Factory,
      TagComponent.Factory,
      ToolComponent.Factory,
      DebugComponent.Factory,
      EventComponent.Factory,
      JobControlComponent.Factory,
      WorkflowComponent.Factory,
      MetricsComponent.Factory,
      MessageDeliveryStubComponent.Factory,
      _spec
    ).map(_.createC(params))).map(_.flatMap(_.participants))
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

  def default(
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem =
    defaultWithScope(
      context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = _subsystem_name,
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      ),
      mode = mode.flatMap(RunMode.from),
      configuration = configuration
    ).enableControlledTestExecution()

  def default(
    extraComponents: Seq[Component],
    mode: Option[String]
  ): Subsystem = {
    val subsystem = default(mode)
    if (extraComponents.nonEmpty) {
      val extras = extraComponents // extraComponents.map(_.withSystemContext(SystemContext.empty))
      subsystem.add(extras)
    }
    subsystem
  }

  def defaultWithScope(
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem = {
    GenericSubsystemFactory.resolveDescriptorC(configuration).TAKE match {
      case Some(descriptor) =>
        return GenericSubsystemFactory.defaultWithScope(
          descriptor = descriptor,
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver
        )
      case None =>
        ()
    }
    val subsystemname =
      RuntimeConfig
        .getString(configuration, RuntimeConfig.subsystemNameKey)
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse(_subsystem_name)
    subsystemname match {
      case "textus-identity" =>
        TextusIdentitySubsystemFactory.defaultWithScope(
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver
        )
      case name if name != _subsystem_name =>
        GenericSubsystemFactory.defaultWithScope(
          subsystemName = name,
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver
        )
      case _ =>
        _default_with_scope(
          context = context,
          mode = mode,
          configuration = configuration,
          aliasResolver = aliasResolver
        )
    }
  }

  private def _default_with_scope(
    context: ScopeContext,
    mode: Option[RunMode] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver: AliasResolver = GlobalRuntimeContext.current
      .map(_.aliasResolver)
      .getOrElse(AliasResolver.empty)
  ): Subsystem = {
    val runtimeconfig = RuntimeConfig.from(configuration)
    val runmode = mode.getOrElse(runtimeconfig.mode)
    val driver = _resolve_http_driver(runtimeconfig)
    val subsystem =
      Subsystem(
        name = _subsystem_name,
        scopeContext = Some(
          context.kind match {
            case ScopeKind.Runtime =>
              context.createChildScope(ScopeKind.Subsystem, _subsystem_name)
            case ScopeKind.Subsystem =>
              context
            case _ =>
              ScopeContext(
                kind = ScopeKind.Subsystem,
                name = _subsystem_name,
                parent = None,
                observabilityContext = context.observabilityContext
              )
          }
        ),
        httpdriver = Some(driver),
        configuration = configuration,
        aliasResolver = aliasResolver,
        runMode = runmode
      )
    val comps = builtinComponents(subsystem)
    subsystem.add(comps)
  }

  private def _resolve_http_driver(
    runtimeconfig: RuntimeConfig
  ): org.goldenport.cncf.http.HttpDriver = {
    val driver = runtimeconfig.httpDriver
    // val baseurl = sys.props.getOrElse("cncf.http.baseurl", ClientConfig.DefaultBaseUrl)
    // if (driver == "fake" || driver == "nop") {
    //   val ping = GlobalRuntimeContext.current
    //     .map(_.formatPing)
    //     .getOrElse(GlobalRuntimeContext.defaultPing)
    //   FakeHttpDriver.okText(ping)
    // } else {
    //   new UrlConnectionHttpDriver(baseurl)
    // }
    driver
  }

  // private def _bootstrap_core(): Component.Core = {
  //   val name = "bootstrap"
  //   val componentId = ComponentId(name)
  //   val instanceId = ComponentInstanceId.default(componentId)
  //   Component.Core.create(name, componentId, instanceId, _empty_protocol())
  // }
}
