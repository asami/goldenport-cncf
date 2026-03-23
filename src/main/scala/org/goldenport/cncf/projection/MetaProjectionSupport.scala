package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   Mar.  5, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[projection] object MetaProjectionSupport {
  final case class AggregateMeta(
    name: String,
    entityName: String
  )
  final case class ViewMeta(
    name: String,
    entityName: String,
    viewNames: Vector[String]
  )
  final case class OperationMeta(
    name: String,
    kind: String,
    inputType: String,
    outputType: String,
    inputValueKind: String,
    parameters: Vector[Record]
  )

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
    Record.data( // Includes runtime-origin and archive metadata for CAR/SAR introspection.
      "type" -> "component",
      "name" -> comp.name,
      "runtimeName" -> component_runtime_name(comp),
      "origin" -> comp.origin.label,
      "artifact" -> comp.artifactMetadata.map { m =>
        Record.data(
          "sourceType" -> m.sourceType,
          "name" -> m.name,
          "version" -> m.version,
          "component" -> m.component.getOrElse(""),
          "subsystem" -> m.subsystem.getOrElse(""),
          "effectiveExtensions" -> m.effectiveExtensions.toVector.sortBy(_._1).map { case (k, v) => Record.data("key" -> k, "value" -> v) },
          "effectiveConfig" -> m.effectiveConfig.toVector.sortBy(_._1).map { case (k, v) => Record.data("key" -> k, "value" -> v) }
        )
      }.getOrElse(Record.empty)
    )

  def service_record(service: ServiceDefinition): Record =
    Record.data(
      "type" -> "service",
      "name" -> service.name,
      "runtimeName" -> service_runtime_name(service)
    )

  def operation_record(service: ServiceDefinition, operation: OperationDefinition): Record =
    Record.data(
      "type" -> "operation",
      "name" -> s"${service.name}.${operation.name}",
      "runtimeName" -> s"${service_runtime_name(service)}.${operation_runtime_name(operation)}"
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

  def aggregateMetas(component: Component): Vector[AggregateMeta] =
    component.aggregateDefinitions
      .sortBy(_.name)
      .map(x => AggregateMeta(x.name, x.entityName))

  def viewMetas(component: Component): Vector[ViewMeta] =
    component.viewDefinitions
      .sortBy(_.name)
      .map(x => ViewMeta(x.name, x.entityName, x.viewNames.distinct.sorted))

  def operationMetas(component: Component): Vector[OperationMeta] =
    component.operationDefinitions
      .groupBy(_.name)
      .toVector
      .sortBy(_._1)
      .map(_._2.head)
      .map { x =>
        OperationMeta(
          name = x.name,
          kind = x.kind,
          inputType = x.inputType,
          outputType = x.outputType,
          inputValueKind = x.inputValueKind,
          parameters = x.parameters.map { p =>
            Record.data(
              "name" -> p.name,
              "datatype" -> p.datatype,
              "multiplicity" -> p.multiplicity
            )
          }
        )
      }

  def component_runtime_name(component: Component): String =
    NamingConventions.toNormalizedSegment(component.name)

  def service_runtime_name(service: ServiceDefinition): String =
    NamingConventions.toNormalizedSegment(service.name)

  def operation_runtime_name(operation: OperationDefinition): String =
    NamingConventions.toNormalizedSegment(operation.name)

  def selector_runtime_name(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    NamingConventions.toNormalizedSelector(component.name, service.name, operation.name)

  private def _find_component(comps: Vector[Component], name: String): Option[Component] =
    comps.find(x => NamingConventions.equivalentByNormalized(x.name, name))

  private def _find_service(component: Component, serviceName: String): Option[ServiceDefinition] =
    component.protocol.services.services.find(x => NamingConventions.equivalentByNormalized(x.name, serviceName))

  private def _find_operation(service: ServiceDefinition, operationName: String): Option[OperationDefinition] =
    service.operations.operations.find(x => NamingConventions.equivalentByNormalized(x.name, operationName))
}
