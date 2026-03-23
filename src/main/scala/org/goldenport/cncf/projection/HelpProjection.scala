package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.projection.model.HelpModel
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString

/*
 * @since   Mar.  5, 2026
 * @version Mar. 24, 2026
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
        val componentName = component.name
        val services = component.protocol.services.services.sortBy(_.name)
        val aggregates = aggregateMetas(component).map(_.name)
        val views = viewMetas(component).map(_.name)
        val operations = operationMetas(component).map(_.name)
        val artifactName = component.artifactMetadata.map(_.name).toVector
        val artifactVersion = component.artifactMetadata.map(_.version).toVector
        HelpModel(
          `type` = "component",
          name = componentName,
          summary = s"Component: $componentName",
          children = services.map(_.name),
          details = Map(
            "services" -> services.map(_.name),
            "aggregates" -> aggregates,
            "views" -> views,
            "operationDefinitions" -> operations,
            "origin" -> Vector(component.origin.label),
            "artifactName" -> artifactName,
            "artifactVersion" -> artifactVersion
          ),
          usage = services.headOption.map(s => Vector(s"command help $componentName.${s.name}")).getOrElse(Vector.empty)
        )
      case Target.ServiceTarget(component, service) =>
        val componentName = component.name
        val serviceName = service.name
        val operations = service.operations.operations.toVector.sortBy(_.name)
        val summary = _service_summary(service).getOrElse(s"Service: ${service.name}")
        HelpModel(
          `type` = "service",
          name = s"$componentName.$serviceName",
          summary = summary,
          children = operations.map(_.name),
          details = Map("operations" -> operations.map(_.name)),
          usage = operations.headOption.map(op => Vector(s"command help $componentName.$serviceName.${op.name}")).getOrElse(Vector.empty)
        )
      case Target.OperationTarget(component, service, operation) =>
        val componentName = component.name
        val serviceName = service.name
        val operationName = operation.name
        val args = operation.specification.request.parameters.toVector.map(_.name)
        val returns = Option(operation.specification.response.result).map(_.toString).getOrElse("unknown")
        val summary = _operation_summary(service, operation).getOrElse(s"Operation: ${service.name}.${operation.name}")
        val descriptionDetails = _trim_i18n(operation.specification.description).fold(Map.empty[String, Vector[String]])(x => Map("description" -> Vector(x)))
        HelpModel(
          `type` = "operation",
          name = s"$componentName.$serviceName.$operationName",
          summary = summary,
          children = Vector.empty,
          details = Map(
            "arguments" -> args,
            "returns" -> Vector(returns)
          ) ++ descriptionDetails,
          usage = Vector(s"command $componentName.$serviceName.$operationName")
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


  private def _trim_string(p: Option[String]): Option[String] =
    p.map(_.trim).filter(_.nonEmpty)

  private def _trim_i18n(p: Option[I18nString]): Option[String] =
    p.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _service_summary(service: ServiceDefinition): Option[String] =
    _trim_i18n(service.specification.summary).orElse(_trim_i18n(service.specification.description))

  private def _operation_summary(service: ServiceDefinition, operation: OperationDefinition): Option[String] =
    _trim_i18n(operation.specification.summary).
      orElse(_trim_i18n(operation.specification.description)).
      orElse(_service_summary(service))
}
