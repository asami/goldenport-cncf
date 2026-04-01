package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.projection.model.{HelpModel, HelpSelectorModel}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 * @version Apr.  1, 2026
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
          selector = Some(_subsystem_selector(name)),
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
          selector = Some(_component_selector(componentName)),
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
          name = serviceName,
          summary = summary,
          component = Some(componentName),
          selector = Some(_service_selector(componentName, serviceName)),
          children = operations.map(_.name),
          details = Map("operations" -> operations.map(_.name)),
          usage = operations.headOption.map(op => Vector(s"command help ${_service_cli_selector(componentName, serviceName)}.${NamingConventions.toNormalizedSegment(op.name)}")).getOrElse(Vector.empty)
        )
      case Target.OperationTarget(component, service, operation) =>
        val componentName = component.name
        val serviceName = service.name
        val operationName = operation.name
        val args = operation.specification.request.parameters.toVector.map(_.name)
        val returns = render_operation_returns(operation)
        val summary = _operation_summary(service, operation).getOrElse(s"Operation: ${service.name}.${operation.name}")
        val descriptionDetails = _trim_i18n(operation.specification.description).fold(Map.empty[String, Vector[String]])(x => Map("description" -> Vector(x)))
        HelpModel(
          `type` = "operation",
          name = operationName,
          summary = summary,
          component = Some(componentName),
          service = Some(serviceName),
          selector = Some(_operation_selector(componentName, serviceName, operationName)),
          children = Vector.empty,
          details = Map(
            "arguments" -> args,
            "returns" -> Vector(returns)
          ) ++ descriptionDetails,
          usage = Vector(s"command ${_operation_cli_selector(componentName, serviceName, operationName)}")
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
      "component" -> model.component,
      "service" -> model.service,
      "selector" -> model.selector.map { x =>
        Record.data(
          "canonical" -> x.canonical,
          "cli" -> x.cli,
          "rest" -> x.rest,
          "accepted" -> x.accepted
        )
      },
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

  private def _subsystem_selector(name: String): HelpSelectorModel = {
    val cli = NamingConventions.toNormalizedSegment(name)
    HelpSelectorModel(
      canonical = name,
      cli = cli,
      rest = s"/$cli",
      accepted = Vector(name)
    )
  }

  private def _component_selector(componentName: String): HelpSelectorModel = {
    val cli = NamingConventions.toNormalizedSegment(componentName)
    HelpSelectorModel(
      canonical = componentName,
      cli = cli,
      rest = s"/$cli",
      accepted = Vector(componentName)
    )
  }

  private def _service_selector(componentName: String, serviceName: String): HelpSelectorModel = {
    val canonical = s"$componentName.$serviceName"
    val cliComponent = NamingConventions.toNormalizedSegment(componentName)
    val cliService = NamingConventions.toNormalizedSegment(serviceName)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$cliComponent.$cliService",
      rest = s"/$cliComponent/$cliService",
      accepted = Vector(canonical)
    )
  }

  private def _operation_selector(componentName: String, serviceName: String, operationName: String): HelpSelectorModel = {
    val canonical = s"$componentName.$serviceName.$operationName"
    val cliComponent = NamingConventions.toNormalizedSegment(componentName)
    val cliService = NamingConventions.toNormalizedSegment(serviceName)
    val cliOperation = NamingConventions.toNormalizedSegment(operationName)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$cliComponent.$cliService.$cliOperation",
      rest = s"/$cliComponent/$cliService/$cliOperation",
      accepted = Vector(canonical)
    )
  }

  private def _service_cli_selector(componentName: String, serviceName: String): String =
    s"${NamingConventions.toNormalizedSegment(componentName)}.${NamingConventions.toNormalizedSegment(serviceName)}"

  private def _operation_cli_selector(componentName: String, serviceName: String, operationName: String): String =
    s"${NamingConventions.toNormalizedSegment(componentName)}.${NamingConventions.toNormalizedSegment(serviceName)}.${NamingConventions.toNormalizedSegment(operationName)}"
}
