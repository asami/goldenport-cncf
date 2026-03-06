package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjection {
  def projectComponent(component: Component): String = {
    val paths = component.protocol.services.services.flatMap { service =>
      service.operations.operations.toVector.map { op =>
        val path = s"/${component.name}/${service.name}/${op.name}"
        val method = _infer_method(service.name, op.name)
        val operationId = s"${component.name}.${service.name}.${op.name}"
        val summary = s"${service.name}.${op.name}"
        s""""${_escape(path)}":{"${method}":{"operationId":"${_escape(operationId)}","summary":"${_escape(summary)}","responses":{"200":{"description":"OK"}}}}"""
      }
    }.mkString(",")
    s"""{"openapi":"3.0.0","info":{"title":"${_escape(component.name)} API","version":"0.1.0"},"paths":{${paths}}}"""
  }

  private def _infer_method(serviceName: String, operationName: String): String = {
    val lowered = operationName.toLowerCase
    if (serviceName.toLowerCase == "http" && (lowered == "post" || lowered == "put" || lowered == "delete"))
      lowered
    else if (lowered == "post" || lowered == "put" || lowered == "delete")
      lowered
    else
      "get"
  }

  private def _escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
