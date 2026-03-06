package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object SchemaProjection {
  import MetaProjectionSupport._

  def project(base: Component, selector: Option[String] = None): Record =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        Record.data(
          "type" -> "schema",
          "name" -> name,
          "components" -> components.map { component =>
            project(base, Some(component.name))
          }
        )
      case Target.ComponentTarget(component) =>
        val services = component.protocol.services.services.sortBy(_.name)
        Record.data(
          "type" -> "schema",
          "targetType" -> "component",
          "name" -> component.name,
          "services" -> services.map { service =>
            project(base, Some(s"${component.name}.${service.name}"))
          }
        )
      case Target.ServiceTarget(component, service) =>
        val operations = service.operations.operations.toVector.sortBy(_.name)
        Record.data(
          "type" -> "schema",
          "targetType" -> "service",
          "name" -> s"${component.name}.${service.name}",
          "operations" -> operations.map { op =>
            project(base, Some(s"${component.name}.${service.name}.${op.name}"))
          }
        )
      case Target.OperationTarget(component, service, operation) =>
        Record.data(
          "type" -> "schema",
          "targetType" -> "operation",
          "name" -> s"${component.name}.${service.name}.${operation.name}",
          "request" -> Record.data(
            "parameters" -> operation.specification.request.parameters.toVector.map(parameter_record)
          ),
          "response" -> Record.data(
            "result" -> Option(operation.specification.response.result).map(_.toString).getOrElse("unknown")
          )
        )
      case Target.NotFound(target) =>
        Record.data(
          "type" -> "error",
          "name" -> target.getOrElse("unknown"),
          "summary" -> "target not found"
        )
    }
}
