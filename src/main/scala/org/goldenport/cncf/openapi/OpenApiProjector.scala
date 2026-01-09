package org.goldenport.cncf.openapi

import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}

/*
 * @since   Jan.  8, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjector {
  def forSubsystem(subsystem: Subsystem): String = {
    val paths =
      subsystem.components.flatMap { comp =>
        _paths(comp.name, comp.protocol.services.services)
      }.distinct.sorted
    _openapi_json(paths)
  }

  private def _paths(
    componentName: String,
    services: Vector[ServiceDefinition]
  ): Vector[String] =
    services.flatMap { service =>
      service.operations.operations.toVector.map { op =>
        s"/${componentName}/${service.name}/${op.name}"
      }
    }

  private def _openapi_json(paths: Vector[String]): String = {
    val pathsJson =
      paths.map { path =>
        val p = _escape(path)
        s""""${p}":{"get":{"responses":{"200":{"description":"OK"}}}}"""
      }.mkString(",")
    s"""{"openapi":"3.0.0","info":{"title":"CNCF API","version":"0.1.0"},"paths":{${pathsJson}}}"""
  }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
}
