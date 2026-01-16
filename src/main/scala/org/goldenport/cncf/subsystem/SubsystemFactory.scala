package org.goldenport.cncf.subsystem

import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin, PingRuntime, PingRuntimeInfo}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.client.ClientComponent
import org.goldenport.cncf.component.admin.AdminComponent
import org.goldenport.cncf.component.specification.SpecificationComponent
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.{FakeHttpDriver, UrlConnectionHttpDriver}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object DefaultSubsystemFactory {
  private val _admin = AdminComponent.Factory
  private val _client = ClientComponent.Factory
  private val _spec = SpecificationComponent.Factory()
  private val _subsystem_name = "goldenport-cncf"

  def subsystemName: String = _subsystem_name

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
      mode = mode,
      configuration = configuration
    )

  def default(
    extraComponents: Seq[Component],
    mode: Option[String]
  ): Subsystem = {
    val subsystem = default(mode)
    if (extraComponents.nonEmpty) {
      val modeLabel = mode.getOrElse("command")
      val system = PingRuntime.systemContext(
        mode = modeLabel,
        subsystem = subsystem.name,
        runtimeVersion = CncfVersion.current,
        subsystemVersion = subsystem.version
      )
      val extras = extraComponents.map(_.withSystemContext(system))
      subsystem.add(extras)
    }
    subsystem
  }

  def defaultWithScope(
    context: ScopeContext,
    mode: Option[String] = None,
    configuration: ResolvedConfiguration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
  ): Subsystem = {
    val modeLabel = mode.getOrElse("command")
    val system = PingRuntime.systemContext(
      mode = modeLabel,
      subsystem = _subsystem_name,
      runtimeVersion = CncfVersion.current,
      subsystemVersion = None
    )
    val driver = _resolve_http_driver(mode, _subsystem_name)
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
        configuration = configuration
      )
    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin)
    val comps = Vector(_admin, _client, _spec)
      .flatMap(_.create(params))
      .map(_.withSystemContext(system))
    subsystem.add(comps)
  }

  private def _resolve_http_driver(
    mode: Option[String],
    subsystemName: String
  ): org.goldenport.cncf.http.HttpDriver = {
    val driver = sys.props.getOrElse("cncf.http.driver", "real")
    val baseurl = sys.props.getOrElse("cncf.http.baseurl", "http://localhost:8080")
    if (driver == "fake") {
      val info = PingRuntimeInfo(
        mode = mode.getOrElse("command"),
        subsystem = subsystemName,
        runtimeVersion = CncfVersion.current,
        subsystemVersion = CncfVersion.current
      )
      FakeHttpDriver.okText(PingRuntime.format(info))
    } else {
      new UrlConnectionHttpDriver(baseurl)
    }
  }

  // private def _bootstrap_core(): Component.Core = {
  //   val name = "bootstrap"
  //   val componentId = ComponentId(name)
  //   val instanceId = ComponentInstanceId.default(componentId)
  //   Component.Core.create(name, componentId, instanceId, _empty_protocol())
  // }

  private def _empty_protocol(): Protocol = {
    Protocol(
      services = spec.ServiceDefinitionGroup(services = Vector.empty),
      handler = ProtocolHandler(
        ingresses = IngressCollection(Vector.empty),
        egresses = EgressCollection(Vector.empty),
        projections = ProjectionCollection()
      )
    )
  }
}
