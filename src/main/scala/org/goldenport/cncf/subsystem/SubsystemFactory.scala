package org.goldenport.cncf.subsystem

import org.goldenport.cncf.component.{Component, ComponentInitParams}
import org.goldenport.cncf.client.ClientComponent
import org.goldenport.cncf.component.admin.AdminComponent
import org.goldenport.cncf.component.specification.SpecificationComponent
import org.goldenport.cncf.http.{FakeHttpDriver, UrlConnectionHttpDriver}

/*
 * @since   Jan.  7, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object DefaultSubsystemFactory {
  private val _admin = AdminComponent.Factory
  private val _client = ClientComponent.Factory
  private val _spec = SpecificationComponent.Factory()

  def default(): Subsystem = {
    val driver = _resolve_http_driver()
    val subsytem = Subsystem(name = "cncf", httpdriver = Some(driver))
    val params = ComponentInitParams(subsytem)
    val comps = Vector(_admin, _client, _spec).flatMap(_.create(params))
    subsytem.add(comps)
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
}
