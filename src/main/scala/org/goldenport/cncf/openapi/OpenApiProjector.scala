package org.goldenport.cncf.openapi

import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}

/*
 * @since   Jan.  8, 2026
 * @version Jan.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object OpenApiProjector {
  private final case class PathSpec(
    path: String,
    tag: String,
    component: String,
    service: String,
    operation: String
  )

  def forSubsystem(subsystem: Subsystem): String = {
    val paths =
      subsystem.components.flatMap { comp =>
        _paths(comp.name, comp.protocol.services.services)
      }.distinct.sortBy(_.path)
    _openapi_json(paths)
  }

  private def _paths(
    componentName: String,
    services: Vector[ServiceDefinition]
  ): Vector[PathSpec] =
    services.flatMap { service =>
      service.operations.operations.toVector.map { op =>
        val serviceTag = s"${componentName}.${service.name}"
        PathSpec(
          path = s"/${componentName}/${service.name}/${op.name}",
          tag = s"experimental:${serviceTag}",
          component = componentName,
          service = service.name,
          operation = op.name
        )
      }
    }

  private def _openapi_json(paths: Vector[PathSpec]): String = {
    val pathsJson =
      paths.map { path =>
        val p = _escape(path.path)
        val tag = _escape(path.tag)
        val summary = _escape(s"${path.component}.${path.service}.${path.operation}")
        val description = _escape(
          s"Component: ${path.component}\n" +
            s"Service: ${path.service}\n" +
            s"Operation: ${path.operation}\n" +
            "(experimental; subject to change in Phase 2.8)"
        )
        // TODO (Phase 2.8): Redesign OpenAPI tagging strategy.
        // TODO (Phase 2.8): Decide whether Service should map to OpenAPI tags.
        // TODO (Phase 2.8): Remove or formalize `experimental:*` tags.
        // TODO Phase 2.8: Replace experimental summary/description
        // with contract-level operation specifications.
        s""""${p}":{"get":{"tags":["${tag}"],"summary":"${summary}","description":"${description}","responses":{"200":{"description":"OK"}}}}"""
      }.mkString(",")
    s"""{"openapi":"3.0.0","info":{"title":"CNCF API","version":"0.1.0"},"paths":{${pathsJson}}}"""
  }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
