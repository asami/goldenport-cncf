package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.mcp.McpToolCatalog

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object McpProjection {
  def projectComponent(component: Component): String = {
    val tools = McpToolCatalog.toolsForComponent(component)
      .map(_.toJson.noSpaces)
      .mkString(",")
    s"""{"mcpVersion":"2026-03-19","server":{"name":"${_escape(component.name)}","version":"0.1.0"},"tools":[${tools}]}"""
  }

  private def _escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
