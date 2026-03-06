package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.cncf.component.Component

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[projection] object MetaProjectionSupport {
  sealed trait Target
  object Target {
    final case class Subsystem(components: Vector[Component], name: String) extends Target
    final case class ComponentTarget(component: Component) extends Target
    final case class ServiceTarget(component: Component, service: ServiceDefinition) extends Target
    final case class OperationTarget(
      component: Component,
      service: ServiceDefinition,
      operation: OperationDefinition
    ) extends Target
    final case class NotFound(selector: Option[String]) extends Target
  }

  def components(base: Component): Vector[Component] =
    base.subsystem.map(_.components.sortBy(_.name)).getOrElse(Vector(base))

  def resolve(base: Component, selector: Option[String]): Target = {
    val comps = components(base)
    selector.map(_.trim).filter(_.nonEmpty) match {
      case None =>
        Target.Subsystem(comps, base.subsystem.map(_.name).getOrElse("subsystem"))
      case Some(s) =>
        val segments = s.split("\\.").toVector.filter(_.nonEmpty)
        segments match {
          case Vector(componentName) =>
            _find_component(comps, componentName).map(Target.ComponentTarget.apply).getOrElse(Target.NotFound(Some(s)))
          case Vector(componentName, serviceName) =>
            (for {
              comp <- _find_component(comps, componentName)
              service <- _find_service(comp, serviceName)
            } yield Target.ServiceTarget(comp, service)).getOrElse(Target.NotFound(Some(s)))
          case Vector(componentName, serviceName, operationName) =>
            (for {
              comp <- _find_component(comps, componentName)
              service <- _find_service(comp, serviceName)
              op <- _find_operation(service, operationName)
            } yield Target.OperationTarget(comp, service, op)).getOrElse(Target.NotFound(Some(s)))
          case _ =>
            Target.NotFound(Some(s))
        }
    }
  }

  def component_record(comp: Component): Record =
    Record.data(
      "type" -> "component",
      "name" -> comp.name
    )

  def service_record(service: ServiceDefinition): Record =
    Record.data(
      "type" -> "service",
      "name" -> service.name
    )

  def operation_record(service: ServiceDefinition, operation: OperationDefinition): Record =
    Record.data(
      "type" -> "operation",
      "name" -> s"${service.name}.${operation.name}"
    )

  def parameter_record(param: ParameterDefinition): Record = {
    val datatype = Option(param.domain.datatype).map(_.toString).getOrElse("unknown")
    val multiplicity = Option(param.domain.multiplicity).map(_.toString).getOrElse("unknown")
    Record.data(
      "name" -> param.name,
      "kind" -> param.kind.toString,
      "type" -> datatype,
      "multiplicity" -> multiplicity
    )
  }

  def operation_details(operation: OperationDefinition): Record = {
    val args = operation.specification.request.parameters.toVector.map(parameter_record)
    val returns = Option(operation.specification.response.result).map(_.toString).getOrElse("unknown")
    Record.data(
      "arguments" -> args,
      "returns" -> returns
    )
  }

  private def _find_component(comps: Vector[Component], name: String): Option[Component] =
    comps.find(_.name == name)

  private def _find_service(component: Component, serviceName: String): Option[ServiceDefinition] =
    component.protocol.services.services.find(_.name == serviceName)

  private def _find_operation(service: ServiceDefinition, operationName: String): Option[OperationDefinition] =
    service.operations.operations.find(_.name == operationName)
}
