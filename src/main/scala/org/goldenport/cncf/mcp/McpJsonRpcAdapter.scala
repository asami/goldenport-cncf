package org.goldenport.cncf.mcp

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Mar. 19, 2026
 *  version Mar. 27, 2026
 *  version Apr. 15, 2026
 *  version May. 20, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpJsonRpcAdapter(
  subsystem: Subsystem
) {
  def handle(input: String): String =
    parse(input) match {
      case Left(_) =>
        _error(Json.Null, -32600, "invalid request").noSpaces
      case Right(json) =>
        _handle_json(json).noSpaces
    }

  private def _handle_json(json: Json): Json =
    json.asObject match {
      case Some(obj) =>
        val id = obj("id").getOrElse(Json.Null)
        val jsonrpcok = obj("jsonrpc").flatMap(_.asString).contains("2.0")
        val methodopt = obj("method").flatMap(_.asString)
        if (!jsonrpcok || methodopt.isEmpty) {
          _error(id, -32600, "invalid request")
        } else {
          val params = obj("params")
          methodopt.get match {
            case "initialize" =>
              _initialize(id, params)
            case "tools/list" =>
              _tools_list(id)
            case "tools/call" =>
              _tools_call(id, params)
            case _ =>
              _error(id, -32601, "method not found")
          }
        }
      case None =>
        _error(Json.Null, -32600, "invalid request")
    }

  private def _initialize(
    id: Json,
    params: Option[Json]
  ): Json =
    params.flatMap(_.asObject).flatMap(_("protocolVersion")) match {
      case None => _error(id, -32602, "invalid params: protocolVersion is required")
      case Some(value) => value.asString match {
        case None => _error(id, -32602, "invalid params: protocolVersion must be a string")
        case Some(version) => McpProtocolRevision.parseC(version) match {
          case Consequence.Success(revision) => _result(id, _initialize_result(revision))
          case Consequence.Failure(_) => _error(id, -32602, "invalid params: unsupported protocolVersion")
        }
      }
    }

  private def _initialize_result(revision: McpProtocolRevision): Json =
    Json.obj(
      "protocolVersion" -> Json.fromString(revision.print),
      "serverInfo" -> Json.obj(
        "name" -> Json.fromString(subsystem.name),
        "version" -> Json.fromString(subsystem.version.getOrElse("0.1.0"))
      ),
      "capabilities" -> Json.obj(
        "tools" -> Json.obj()
      )
    )

  private def _tools_list(id: Json): Json =
    _tool_catalog match {
      case Consequence.Success(tools) =>
        _result(id, Json.obj("tools" -> Json.arr(tools.map(_.toJson): _*)))
      case Consequence.Failure(conclusion) =>
        _tool_catalog_error(id, conclusion.display)
    }

  private def _tool_catalog: Consequence[Vector[McpToolCatalog.Tool]] =
    McpToolCatalog.consequenceToolsForSubsystem(subsystem)

  private def _tool_catalog_error(id: Json, message: String): Json =
    _error(id, -32603, s"MCP tool catalog unavailable: $message")

  private def _tools_call(
    id: Json,
    params: Option[Json]
  ): Json =
    params.flatMap(_.asObject) match {
      case Some(p) =>
        p("name").flatMap(_.asString) match {
          case Some(name) =>
            val arguments = p("arguments").flatMap(_.asObject).getOrElse(JsonObject.empty)
            _execute_tool(id, name, arguments)
          case None =>
            _error(id, -32602, "invalid params: name is required")
        }
      case None =>
        _error(id, -32602, "invalid params")
    }

  private def _execute_tool(
    id: Json,
    name: String,
    arguments: JsonObject
  ): Json =
    _tool_catalog match {
      case Consequence.Failure(conclusion) =>
        _tool_catalog_error(id, conclusion.display)
      case Consequence.Success(tools) =>
        if (!tools.exists(_.name == name))
          _error(id, -32602, s"MCP tool is not published: $name")
        else _to_request(name, arguments) match {
          case Left(message) =>
            _error(id, -32602, message)
          case Right(req) =>
            subsystem.execute(req) match {
              case Consequence.Success(response) =>
                _result(
                  id,
                  Json.obj(
                    "content" -> Json.arr(
                      Json.obj(
                        "type" -> Json.fromString("text"),
                        "text" -> Json.fromString(response.print)
                      )
                    )
                  )
                )
              case Consequence.Failure(conclusion) =>
                _result(
                  id,
                  Json.obj(
                    "isError" -> Json.True,
                    "content" -> Json.arr(
                      Json.obj(
                        "type" -> Json.fromString("text"),
                        "text" -> Json.fromString(conclusion.show)
                      )
                    )
                  )
                )
            }
        }
    }

  private def _to_request(
    name: String,
    arguments: JsonObject
  ): Either[String, Request] =
    name.split("\\.") match {
      case Array(component, service, operation) =>
        val args = arguments.toVector
          .sortBy(_._1)
          .map { case (k, v) =>
            Argument(k, _json_argument_value(v))
          }
          .toList
        val properties = _request_properties(args)
        Right(
          Request.of(
            component = component,
            service = service,
            operation = operation,
            arguments = Nil,
            switches = Nil,
            properties = properties :+ Property("textus.format", "json", None)
          )
        )
      case _ =>
        Left(s"invalid tool name: $name")
    }

  private def _request_properties(args: List[Argument]): List[Property] =
    args.map(x => Property(x.name, x.value, None))

  private def _json_argument_value(v: Json): String =
    v.asString.getOrElse(v.noSpaces)

  private def _result(
    id: Json,
    result: Json
  ): Json =
    Json.obj(
      "jsonrpc" -> Json.fromString("2.0"),
      "id" -> id,
      "result" -> result
    )

  private def _error(
    id: Json,
    code: Int,
    message: String
  ): Json =
    Json.obj(
      "jsonrpc" -> Json.fromString("2.0"),
      "id" -> id,
      "error" -> Json.obj(
        "code" -> Json.fromInt(code),
        "message" -> Json.fromString(message)
      )
    )
}
