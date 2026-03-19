package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object McpProjection {
  def projectComponent(component: Component): String = {
    val tools = component.protocol.services.services.flatMap { service =>
      service.operations.operations.toVector.map { op =>
        val fullname = s"${component.name}.${service.name}.${op.name}"
        val params = op.specification.request.parameters.toVector.map(_.name).distinct
        _tool_json(
          name = fullname,
          description = s"${service.name}.${op.name}",
          parameters = params
        )
      }
    }.mkString(",")
    s"""{"mcpVersion":"2026-03-19","server":{"name":"${_escape(component.name)}","version":"0.1.0"},"tools":[${tools}]}"""
  }

  private def _tool_json(
    name: String,
    description: String,
    parameters: Vector[String]
  ): String = {
    val props =
      parameters.map { n =>
        s""""${_escape(n)}":{"type":"string"}"""
      }.mkString(",")
    val reqs =
      parameters.map(n => s""""${_escape(n)}"""").mkString(",")
    s"""{"name":"${_escape(name)}","description":"${_escape(description)}","inputSchema":{"type":"object","properties":{${props}},"required":[${reqs}]}}"""
  }

  private def _escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
