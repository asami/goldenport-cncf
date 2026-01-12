package org.goldenport.cncf.subsystem

import org.goldenport.cncf.component.{Component, ComponentId, ComponentInitParams, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.client.ClientComponent
import org.goldenport.cncf.component.admin.AdminComponent
import org.goldenport.cncf.component.specification.SpecificationComponent
import org.goldenport.cncf.http.{FakeHttpDriver, UrlConnectionHttpDriver}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.spec as spec

/*
 * @since   Jan.  7, 2026
 * @version Jan. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object DefaultSubsystemFactory {
  private val _admin = AdminComponent.Factory
  private val _client = ClientComponent.Factory
  private val _spec = SpecificationComponent.Factory()

  def default(): Subsystem = {
    val driver = _resolve_http_driver()
    val subsytem = Subsystem(name = "cncf", httpdriver = Some(driver))
    val params = ComponentInitParams(subsytem, _bootstrap_core(), ComponentOrigin.Builtin)
    val comps = Vector(_admin, _client, _spec).flatMap(_.create(params))
    subsytem.add(comps)
  }

  def default(extraComponents: Seq[Component]): Subsystem = {
    val subsystem = default()
    if (extraComponents.nonEmpty) {
      subsystem.add(extraComponents)
    }
    subsystem
  }

  private def _resolve_http_driver(): org.goldenport.cncf.http.HttpDriver = {
    val driver = sys.props.getOrElse("cncf.http.driver", "real")
    val baseurl = sys.props.getOrElse("cncf.http.baseurl", "http://localhost:8080")
    if (driver == "fake") {
      FakeHttpDriver.okText("OK")
    } else {
      new UrlConnectionHttpDriver(baseurl)
    }
  }

  private def _bootstrap_core(): Component.Core = {
    val name = "bootstrap"
    val componentId = ComponentId(name)
    val instanceId = ComponentInstanceId.default(componentId)
    Component.Core.create(name, componentId, instanceId, _empty_protocol())
  }

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
