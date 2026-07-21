package org.goldenport.cncf.mcp

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.operationtool.{OperationToolDefinition, OperationToolDefinitionBuilder, OperationToolInputSchema}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.spec.{OperationDefinition, ParameterDefinition, ServiceDefinition}

/*
 * @since   May. 18, 2026
 * @version Jul. 21, 2026
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
    subsystem.components.filter(_.isPrimaryParticipant).foldLeft(
      Consequence.success(Vector.empty[Tool])
    ) { (z, component) =>
      for {
        tools <- z
        componenttools <- consequenceToolsForComponent(component)
      } yield tools ++ componenttools
    }.flatMap(_validated_tools)

  def toolsForComponent(component: Component): Vector[Tool] =
    consequenceToolsForComponent(component).TAKE

  def consequenceToolsForComponent(component: Component): Consequence[Vector[Tool]] =
    component.protocol.services.services.flatMap(service =>
      service.operations.operations.toVector.filter(operation =>
        component.isMcpReady(service.name, operation.name)
      ).map(operation => service -> operation)
    ).foldLeft(Consequence.success(Vector.empty[Tool])) {
      case (z, (service, operation)) =>
        for {
          tools <- z
          definition <- OperationToolDefinitionBuilder.definitionC(component, service, operation)
        } yield tools :+ _tool_for_definition(definition)
    }.map(_.sortBy(_.name))

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

  private def _tool_for_definition(definition: OperationToolDefinition): Tool =
    Tool(
      name = definition.identity.print,
      description = definition.description,
      inputSchema = _input_schema(definition.inputSchema)
    )

  def toolForOperation(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Tool =
    consequenceToolForOperation(componentname, service, operation).TAKE

  def consequenceToolForOperation(
    componentname: String,
    service: ServiceDefinition,
    operation: OperationDefinition
  ): Consequence[Tool] =
    OperationToolDefinitionBuilder.definitionC(componentname, service, operation).map(_tool_for_definition)

  def inputSchema(parameters: Vector[ParameterDefinition]): Json =
    _input_schema(OperationToolDefinitionBuilder.inputSchema(parameters))

  private def _input_schema(schema: OperationToolInputSchema.ObjectValue): Json = {
    val properties = JsonObject.fromIterable(
      schema.fields.map(field => field.name -> _schema(field.schema))
    )
    val required = schema.fields.filter(_.required).map(field => Json.fromString(field.name))
    Json.obj(
      "type" -> Json.fromString("object"),
      "properties" -> Json.fromJsonObject(properties),
      "required" -> Json.arr(required: _*)
    )
  }

  def parameterSchema(parameter: ParameterDefinition): Json =
    _schema(OperationToolDefinitionBuilder.inputField(parameter).schema)

  private def _schema(schema: OperationToolInputSchema): Json =
    schema match {
      case OperationToolInputSchema.ArrayValue(items) =>
        Json.obj(
          "type" -> Json.fromString("array"),
          "items" -> _schema(items)
        )
      case OperationToolInputSchema.ObjectValue(fields) =>
        _input_schema(OperationToolInputSchema.ObjectValue(fields))
      case scalar =>
        Json.obj("type" -> Json.fromString(scalar.kind.name))
    }
}
