package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.projection.model.HelpModel

/*
 * @since   Mar.  5, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
object HelpProjection {
  import MetaProjectionSupport._

  def projectModel(base: Component, selector: Option[String] = None): HelpModel =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        HelpModel(
          `type` = "subsystem",
          name = name,
          summary = "Subsystem help",
          children = components.map(_.name),
          details = Map("components" -> components.map(_.name)),
          usage = Vector("command meta.help <component>")
        )
      case Target.ComponentTarget(component) =>
        val services = component.protocol.services.services.sortBy(_.name)
        val aggregates = aggregateMetas(component).map(_.name)
        val views = viewMetas(component).map(_.name)
        HelpModel(
          `type` = "component",
          name = component.name,
          summary = s"Component: ${component.name}",
          children = services.map(_.name),
          details = Map(
            "services" -> services.map(_.name),
            "aggregates" -> aggregates,
            "views" -> views
          ),
          usage = services.headOption.map(s => Vector(s"command help ${component.name}.${s.name}")).getOrElse(Vector.empty)
        )
      case Target.ServiceTarget(component, service) =>
        val operations = service.operations.operations.toVector.sortBy(_.name)
        HelpModel(
          `type` = "service",
          name = s"${component.name}.${service.name}",
          summary = s"Service: ${service.name}",
          children = operations.map(_.name),
          details = Map("operations" -> operations.map(_.name)),
          usage = operations.headOption.map(op => Vector(s"command help ${component.name}.${service.name}.${op.name}")).getOrElse(Vector.empty)
        )
      case Target.OperationTarget(component, service, operation) =>
        val args = operation.specification.request.parameters.toVector.map(_.name)
        val returns = Option(operation.specification.response.result).map(_.toString).getOrElse("unknown")
        HelpModel(
          `type` = "operation",
          name = s"${component.name}.${service.name}.${operation.name}",
          summary = s"Operation: ${service.name}.${operation.name}",
          children = Vector.empty,
          details = Map(
            "arguments" -> args,
            "returns" -> Vector(returns)
          ),
          usage = Vector(s"command ${component.name}.${service.name}.${operation.name}")
        )
      case Target.NotFound(target) =>
        HelpModel(
          `type` = "error",
          name = target.getOrElse("unknown"),
          summary = "target not found"
        )
    }

  def project(base: Component, selector: Option[String] = None): Record = {
    val model = projectModel(base, selector)
    val details = Record.create(model.details.toVector.map { case (k, v) => k -> v })
    Record.data(
      "type" -> model.`type`,
      "name" -> model.name,
      "summary" -> model.summary,
      "children" -> model.children,
      "details" -> details,
      "usage" -> model.usage
    )
  }
}
