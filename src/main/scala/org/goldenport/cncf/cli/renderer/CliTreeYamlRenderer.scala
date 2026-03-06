package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.TreeModel

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object CliTreeYamlRenderer {
  def render(model: TreeModel): String = {
    val lines = Vector.newBuilder[String]
    lines += s"subsystem: ${model.subsystem}"
    lines += ""
    lines += "components:"
    model.components.sortBy(_.name).foreach { component =>
      lines += s"  ${component.name}:"
      lines += "    services:"
      component.services.sortBy(_.name).foreach { service =>
        lines += s"      ${service.name}:"
        lines += "        operations:"
        service.operations.foreach(op => lines += s"          - $op")
      }
    }
    lines.result().mkString("\n")
  }
}
