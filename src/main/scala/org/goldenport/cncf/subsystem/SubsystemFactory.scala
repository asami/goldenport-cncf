package org.goldenport.cncf.subsystem

import org.goldenport.cncf.component.{Component, ComponentInitParams}
import org.goldenport.cncf.component.admin.AdminComponent
import org.goldenport.cncf.component.specification.SpecificationComponent

/*
 * @since   Jan.  7, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object DefaultSubsystemFactory {
  private val _admin = AdminComponent.Factory
  private val _spec = SpecificationComponent.Factory()

  def default(): Subsystem = {
    val subsytem = Subsystem(name = "cncf")
    val params = ComponentInitParams(subsytem)
    val comps = Vector(_admin, _spec).flatMap(_.create(params))
    subsytem.add(comps)
  }
}
