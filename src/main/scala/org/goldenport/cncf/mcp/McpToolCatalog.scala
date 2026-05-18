package org.goldenport.cncf.mcp

import io.circe.{Json, JsonObject}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}
import org.goldenport.schema.{Multiplicity, XBoolean, XDouble, XFloat, XInt, XInteger, XLong}

/*
 * @since   May. 18, 2026
 * @version May. 18, 2026
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
    subsystem.components.flatMap(toolsForComponent)

  def toolsForComponent(component: Component): Vector[Tool] =
    component.protocol.services.services.flatMap(service =>
      service.operations.operations.toVector.map(operation =>
        toolForOperation(component.name, service, operation)
      )
    )

  def toolForOperation(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Tool = {
    val parameters = operation.specification.request.parameters.toVector
    Tool(
      name = s"$componentname.${service.name}.${operation.name}",
      description = s"${service.name}.${operation.name}",
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
