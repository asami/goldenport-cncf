package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version Mar. 22, 2026
 * @author  ASAMI, Tomoharu
 */
object DescribeProjection {
  import MetaProjectionSupport._

  def project(base: Component, selector: Option[String] = None): Record =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        Record.data(
          "type" -> "subsystem",
          "name" -> name,
          "components" -> components.map(component_record)
        )
      case Target.ComponentTarget(component) =>
        val services = component.protocol.services.services.sortBy(_.name)
        val aggregates = aggregateMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "entityName" -> x.entityName
          )
        }
        val views = viewMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "entityName" -> x.entityName,
            "viewNames" -> x.viewNames
          )
        }
        val operationdefs = operationMetas(component).map { x =>
          Record.data(
            "name" -> x.name,
            "kind" -> x.kind,
            "inputType" -> x.inputType,
            "outputType" -> x.outputType,
            "inputValueKind" -> x.inputValueKind,
            "parameters" -> x.parameters
          )
        }
        Record.data(
          "type" -> "component",
          "name" -> component.name,
          "summary" -> s"Component ${component.name}",
          "services" -> services.map(service_record),
          "aggregates" -> aggregates,
          "views" -> views,
          "operationDefinitions" -> operationdefs
        )
      case Target.ServiceTarget(component, service) =>
        val operations = service.operations.operations.toVector.sortBy(_.name)
        Record.data(
          "type" -> "service",
          "name" -> s"${component.name}.${service.name}",
          "summary" -> s"Service ${service.name}",
          "operations" -> operations.map(operation_record(service, _))
        )
      case Target.OperationTarget(component, service, operation) =>
        Record.data(
          "type" -> "operation",
          "name" -> s"${component.name}.${service.name}.${operation.name}",
          "summary" -> s"Operation ${service.name}.${operation.name}",
          "arguments" -> operation.specification.request.parameters.toVector.map(parameter_record),
          "returns" -> Option(operation.specification.response.result).map(_.toString).getOrElse("unknown")
        )
      case Target.NotFound(target) =>
        Record.data(
          "type" -> "error",
          "name" -> target.getOrElse("unknown"),
          "summary" -> "target not found"
        )
    }
}
