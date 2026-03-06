package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.TreeModel

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object CliTreeJsonRenderer {
  def render(model: TreeModel): String = {
    val components = model.components.sortBy(_.name).map { component =>
      val services = component.services.sortBy(_.name).map { service =>
        val operations = service.operations.map(op => s""""${_escape(op)}"""").mkString(",")
        s""""${_escape(service.name)}":{"operations":[${operations}]}"""
      }.mkString(",")
      s""""${_escape(component.name)}":{"services":{${services}}}"""
    }.mkString(",")
    s"""{"subsystem":"${_escape(model.subsystem)}","components":{${components}}}"""
  }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
