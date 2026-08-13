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
 *  version Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpJsonRpcAdapter(
  subsystem: Subsystem
) {
  import McpJsonRpcOutcome.*

  def handle(
    input: String,
    protocolversionheader: Option[String]
  ): McpJsonRpcOutcome =
    _handle(input, protocolversionheader, enforceprotocolversion = true)

  def handleWebSocketCompatibility(input: String): McpJsonRpcOutcome =
    _handle(input, None, enforceprotocolversion = false)

  private def _handle(
    input: String,
    protocolversionheader: Option[String],
    enforceprotocolversion: Boolean
  ): McpJsonRpcOutcome =
    parse(input) match {
      case Left(_) =>
        ProtocolFailure(Some(_error(Json.Null, -32600, "invalid request")))
      case Right(json) =>
        _handle_json(json, protocolversionheader, enforceprotocolversion)
    }

  private def _handle_json(
    json: Json,
    protocolversionheader: Option[String],
    enforceprotocolversion: Boolean
  ): McpJsonRpcOutcome =
    json.asObject match {
      case Some(obj) =>
        val isrequest = obj.contains("id")
        val id = obj("id").getOrElse(Json.Null)
        val jsonrpcok = obj("jsonrpc").flatMap(_.asString).contains("2.0")
        val methodopt = obj("method").flatMap(_.asString)
        if (!jsonrpcok || methodopt.isEmpty) {
          ProtocolFailure(Some(_error(id, -32600, "invalid request")))
        } else if (isrequest) {
          _handle_request(id, methodopt.get, obj("params"), protocolversionheader, enforceprotocolversion)
        } else {
          _handle_notification(methodopt.get, protocolversionheader, enforceprotocolversion)
        }
      case None =>
        ProtocolFailure(Some(_error(Json.Null, -32600, "invalid request")))
    }

  private def _handle_request(
    id: Json,
    method: String,
    params: Option[Json],
    protocolversionheader: Option[String],
    enforceprotocolversion: Boolean
  ): McpJsonRpcOutcome =
    method match {
      case "initialize" =>
        _response(_initialize(id, params))
      case "notifications/initialized" =>
        ProtocolFailure(Some(_error(id, -32600, "invalid request: initialized must be a notification")))
      case _ =>
        _validate_protocol_version(protocolversionheader, enforceprotocolversion) match {
          case Some(message) => ProtocolFailure(Some(_error(id, -32600, message)))
          case None => method match {
            case "tools/list" => _response(_tools_list(id))
            case "tools/call" => _response(_tools_call(id, params))
            case _ => ProtocolFailure(Some(_error(id, -32601, "method not found")))
          }
        }
    }

  private def _handle_notification(
    method: String,
    protocolversionheader: Option[String],
    enforceprotocolversion: Boolean
  ): McpJsonRpcOutcome =
    if (method != "notifications/initialized")
      ProtocolFailure(None)
    else
      _validate_protocol_version(protocolversionheader, enforceprotocolversion) match {
        case Some(_) => ProtocolFailure(None)
        case None => AcceptedNotification
      }

  private def _validate_protocol_version(
    protocolversionheader: Option[String],
    enforceprotocolversion: Boolean
  ): Option[String] =
    if (!enforceprotocolversion)
      None
    else
      protocolversionheader match {
        case None => Some("invalid request: MCP-Protocol-Version is required")
        case Some(value) => McpProtocolRevision.parseC(value) match {
          case Consequence.Success(_) => None
          case Consequence.Failure(_) => Some("invalid request: unsupported MCP-Protocol-Version")
        }
      }

  private def _response(json: Json): McpJsonRpcOutcome =
    if (json.hcursor.downField("error").succeeded)
      ProtocolFailure(Some(json))
    else
      Response(json)

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
    name.split("\\.").toVector match {
      case parts if parts.length >= 3 =>
        val component = parts.dropRight(2).mkString(".")
        val service = parts(parts.length - 2)
        val operation = parts.last
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

/*
 * @since   Jul. 21, 2026
 *  version Jul. 21, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class McpJsonRpcOutcome {
  def responseBody: Option[Json]
}

object McpJsonRpcOutcome {
  final case class Response(body: Json) extends McpJsonRpcOutcome {
    def responseBody: Option[Json] = Some(body)
  }

  case object AcceptedNotification extends McpJsonRpcOutcome {
    def responseBody: Option[Json] = None
  }

  final case class ProtocolFailure(body: Option[Json]) extends McpJsonRpcOutcome {
    def responseBody: Option[Json] = body
  }
}
