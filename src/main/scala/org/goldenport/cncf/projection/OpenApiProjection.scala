package org.goldenport.cncf.projection

import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjection {
  import MetaProjectionSupport._

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
    val aggregates = _json_array_strings(aggregateMetas(component).map(_.name))
    val views = _json_array_views(viewMetas(component))
    s"""{"openapi":"3.0.0","info":{"title":"${_escape(component.name)} API","version":"0.1.0","x-cncf-aggregate-collections":${aggregates},"x-cncf-view-collections":${views}},"paths":{${paths}}}"""
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

  private def _json_array_strings(xs: Vector[String]): String =
    xs.map(x => s""""${_escape(x)}"""").mkString("[", ",", "]")

  private def _json_array_views(xs: Vector[ViewMeta]): String = {
    val entries = xs.map { x =>
      val names = _json_array_strings(x.viewNames)
      s"""{"name":"${_escape(x.name)}","entityName":"${_escape(x.entityName)}","viewNames":${names}}"""
    }
    entries.mkString("[", ",", "]")
  }
}
