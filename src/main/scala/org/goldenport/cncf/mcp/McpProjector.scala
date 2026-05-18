package org.goldenport.cncf.mcp

import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object McpProjector {
  def forSubsystem(subsystem: Subsystem): String = {
    val tools = McpToolCatalog.toolsForSubsystem(subsystem)
      .map(_.toJson.noSpaces)
      .mkString(",")
    s"""{"mcpVersion":"2026-03-19","server":{"name":"${_escape(subsystem.name)}","version":"${_escape(subsystem.version.getOrElse("0.1.0"))}"},"tools":[${tools}]}"""
  }

  private def _escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
