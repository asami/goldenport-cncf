package org.goldenport.cncf.mcp

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.datatype.I18nString
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.schema.{Multiplicity, XBoolean, XDouble, XFloat, XInt, XInteger, XLong}

/*
 * @since   May. 18, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object McpToolCatalog {
  final case class Tool(
    name: String,
    description: String,
    inputSchema: Json
  ) {
    def toJson: Json =
      Json.obj(
        "name" -> Json.fromString(name),
        "description" -> Json.fromString(description),
        "inputSchema" -> inputSchema
      )
  }

  def toolsForSubsystem(subsystem: Subsystem): Vector[Tool] =
    consequenceToolsForSubsystem(subsystem).TAKE

  def consequenceToolsForSubsystem(subsystem: Subsystem): Consequence[Vector[Tool]] =
    _validated_tools(
      subsystem.components.filter(_.isPrimaryParticipant).flatMap(toolsForComponent)
    )

  def toolsForComponent(component: Component): Vector[Tool] =
    component.protocol.services.services.flatMap(service =>
      service.operations.operations.toVector.filter(operation =>
        component.isMcpReady(service.name, operation.name)
      ).map(operation =>
        _tool_for_operation(component, service, operation)
      )
    ).sortBy(_.name)

  private def _validated_tools(tools: Vector[Tool]): Consequence[Vector[Tool]] = {
    val ordered = tools.sortBy(_.name)
    val duplicateidentities = ordered
      .groupBy(_.name)
      .toVector
      .collect { case (name, entries) if entries.size > 1 => name }
      .sorted
    if (duplicateidentities.isEmpty)
      Consequence.success(ordered)
    else
      Consequence.stateConflict(
        s"duplicate MCP tool identities: ${duplicateidentities.mkString(", ")}"
      )
  }

  private def _tool_for_operation(
    component: Component,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Tool = {
    val parameters = operation.specification.request.parameters.toVector
    Tool(
      name = s"${component.name}.${service.name}.${operation.name}",
      description = _description(Some(component), component.name, service, operation),
      inputSchema = inputSchema(parameters)
    )
  }

  def toolForOperation(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Tool = {
    val parameters = operation.specification.request.parameters.toVector
    Tool(
      name = s"$componentname.${service.name}.${operation.name}",
      description = _description(None, componentname, service, operation),
      inputSchema = inputSchema(parameters)
    )
  }

  def inputSchema(parameters: Vector[ParameterDefinition]): Json = {
    val distinctparameters = parameters.groupBy(_.name).toVector.sortBy(_._1).map(_._2.head)
    val properties = JsonObject.fromIterable(
      distinctparameters.map(parameter => parameter.name -> parameterSchema(parameter))
    )
    val required = distinctparameters
      .filter(parameter => _is_required(parameter.multiplicity))
      .map(parameter => Json.fromString(parameter.name))
    Json.obj(
      "type" -> Json.fromString("object"),
      "properties" -> Json.fromJsonObject(properties),
      "required" -> Json.arr(required: _*)
    )
  }

  def parameterSchema(parameter: ParameterDefinition): Json = {
    val scalar = Json.obj("type" -> Json.fromString(_json_type(parameter)))
    parameter.multiplicity match {
      case Multiplicity.OneMore | Multiplicity.ZeroMore =>
        Json.obj(
          "type" -> Json.fromString("array"),
          "items" -> scalar
        )
      case _ =>
        scalar
    }
  }

  private def _is_required(multiplicity: Multiplicity): Boolean =
    multiplicity match {
      case Multiplicity.One | Multiplicity.OneMore => true
      case _ => false
    }

  private def _description(
    component: Option[Component],
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): String =
    _trim_i18n(operation.specification.summary)
      .orElse(_trim_i18n(operation.specification.description))
      .orElse(component.flatMap(_component_operation_summary(_, operation.name)))
      .orElse(_trim_i18n(service.specification.summary))
      .orElse(_trim_i18n(service.specification.description))
      .getOrElse(s"$componentname.${service.name}.${operation.name}")

  private def _trim_i18n(value: Option[I18nString]): Option[String] =
    value.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _component_operation_summary(
    component: Component,
    operationname: String
  ): Option[String] = {
    val target = _normalized_name(operationname)
    val summaries = component.operationDefinitions
      .filter(x => _normalized_name(x.name) == target)
      .flatMap(_.summary.map(_.trim).filter(_.nonEmpty))
      .distinct
    summaries match {
      case Vector(summary) => Some(summary)
      case _ => None
    }
  }

  private def _normalized_name(value: String): String =
    Option(value).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  private def _json_type(parameter: ParameterDefinition): String =
    parameter.datatype match {
      case XBoolean => "boolean"
      case XInt | XInteger | XLong => "integer"
      case XFloat | XDouble => "number"
      case datatype =>
        datatype.name.toLowerCase(java.util.Locale.ROOT) match {
          case "boolean" | "bool" => "boolean"
          case "int" | "integer" | "long" | "short" => "integer"
          case "float" | "double" | "decimal" | "number" => "number"
          case _ => "string"
        }
    }
}
