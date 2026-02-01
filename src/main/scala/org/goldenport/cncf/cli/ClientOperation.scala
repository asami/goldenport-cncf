package org.goldenport.cncf.cli

import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Property
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.model.value.BaseContent
import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.bag.Bag
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.builtin.client.{GetQuery, PostCommand}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class ClientOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.Client

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    val operations = _component_operation_fqns
    observe_trace(
      s"executeClient start args=${args.mkString(" ")} componentCount=${subsystem.components.size} operations=${_operation_sample(operations)}"
    )
    val result = _client_component(subsystem).flatMap { component =>
      _parse_client_args(args).flatMap { req =>
        _client_action_from_request(req).flatMap { action =>
          component.execute(action)
        }
      }
    }
    result match {
      case Consequence.Success(res) =>
        print_response(res)
      case Consequence.Failure(conclusion) =>
        print_error(conclusion)
    }
    exit_code(result)
  }

  private def _component_operation_fqns: Vector[String] =
    subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => s"${comp.name}.${service.name}.${op.name}")
      }
    }.toVector

  private def _operation_sample(operations: Vector[String]): String = {
    if (operations.isEmpty) {
      "none"
    } else {
      val sample = operations.take(10).mkString(",")
      if (operations.size > 10) {
        s"$sample...(+${operations.size - 10})"
      } else {
        sample
      }
    }
  }

  private def _client_component(
    subsystem: Subsystem
  ): Consequence[ClientComponent] =
    subsystem.components.collectFirst { case c: ClientComponent => c } match {
      case Some(component) =>
        val operations = _component_operation_fqns
        observe_trace(
          s"[client:trace] client component found=${component.name} operations=${_operation_sample(operations)}"
        )
        Consequence.success(component)
      case None =>
        observe_trace("[client:trace] client component not available")
        Consequence.failure("client component not available")
    }

  private def _parse_client_args(
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.failure("client command is required")
    } else {
      _parse_client_command(args.toIndexedSeq).map {
        case (operation, path, extraArgs, clientProperties) =>
          Request.of(
            component = "client",
            service = "http",
            operation = operation,
            arguments = _client_arguments(path, extraArgs),
            switches = Nil,
            properties = clientProperties
          )
      }
    }
  }

  private def _client_arguments(
    path: String,
    extraArgs: Seq[String]
  ): List[Argument] = {
    val extras = extraArgs.zipWithIndex.map { case (value, index) =>
      Argument(s"arg${index + 1}", value, None)
    }
    Argument("path", path, None) :: extras.toList
  }

  private def _parse_client_command(
    args: Seq[String]
  ): Consequence[(String, String, Seq[String], List[Property])] = {
    args.toVector match {
      case Vector("http", operation, rest @ _*) =>
        _parse_http_operation(operation).flatMap { op =>
          if (rest.isEmpty) {
            Consequence.failure("client http path is required")
          } else {
            _parse_client_http(op, rest).map { case (path, properties) =>
              (op, path, Seq.empty, properties)
            }
          }
        }
      case Vector("http") =>
        Consequence.failure("client http requires operation and path")
      case _ =>
        _parse_client_path(args).map { case (path, extra) =>
          ("get", path, extra, Nil)
        }
    }
  }

  private def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.failure("client http path is required")
    }

  private def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    if (args.isEmpty) {
      Consequence.failure("client path is required")
    } else {
      args.toVector match {
        case Vector(component, service, operation, rest @ _*) =>
          Consequence.success((_normalize_path(s"/${component}/${service}/${operation}"), rest))
        case Vector(single, rest @ _*) =>
          parse_component_service_operation_string(single).map { case (component, service, operation) =>
            (_normalize_path(s"/${component}/${service}/${operation}"), rest)
          }
      }
    }
  }

  private def _normalize_path(path: String): String = {
    val normalized = if (path.contains(".")) path.replace(".", "/") else path
    if (normalized.startsWith("/")) normalized else s"/${normalized}"
  }

  private def _parse_http_operation(
    operation: String
  ): Consequence[String] = {
    val lower = operation.toLowerCase
    lower match {
      case "get" | "post" => Consequence.success(lower)
      case _ => Consequence.failure("client http operation must be get or post")
    }
  }

  private def _parse_client_http(
    operation: String,
    params: Seq[String]
  ): Consequence[(String, List[Property])] = {
    val args = Array(operation) ++ params.toArray
    Request.parseArgs(_client_http_request_definition, args).flatMap { parsed =>
      parsed.arguments.headOption match {
        case Some(pathArgument) =>
          Consequence.success((_normalize_path(pathArgument.value.toString), parsed.properties))
        case None =>
          Consequence.failure("client http path is required")
      }
    }
  }

  private val _client_http_request_definition: RequestDefinition = {
    val base = RequestDefinition.curlLike
    val baseurlParameter = ParameterDefinition(
      content = BaseContent.simple("baseurl"),
      kind = ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)
    )
    RequestDefinition(base.parameters :+ baseurlParameter)
  }

  private def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] = {
      if (req.component.contains("client") && req.service.contains("http")) {
        _client_path_from_request(req).flatMap { path =>
          val baseurl = _client_baseurl_from_request(req)
          val rawUrl = _build_client_url(baseurl, path)
          val url = _append_client_query(rawUrl, req)
          observe_trace(
            s"[client:trace] client action request operation=${req.operation} path=${path} url=${url}"
          )
          req.operation match {
        case "post" =>
          _client_mime_body_from_request(req).map { body =>
            new PostCommand(
              req,
              // "system.ping", // TODO generic
              HttpRequest.fromUrl(
                method = HttpRequest.POST,
                url = new URL(url),
                body = body.map(_.value)
              )
            )
          }
        case "get" =>
          _client_mime_body_from_request(req).flatMap {
            case Some(_) =>
              Consequence.failure("client http get does not accept a body")
            case None =>
              Consequence.success(
                new GetQuery(
                  req,
                  // "system.ping",
                  HttpRequest.fromUrl(
                    method = HttpRequest.GET,
                    url = new URL(url)
                  )
                )
              )
          }
        case other =>
          Consequence.failure(s"client http operation not supported: ${other}")
      }
      }
    } else {
      Consequence.failure("client http request is required")
    }
  }

  private def _client_baseurl_from_request(
    req: Request
  ): String =
    req.properties.find(_.name == "baseurl").map(_.value.toString)
      .getOrElse(ClientConfig.DefaultBaseUrl)

  private def _build_client_url(
    baseurl: String,
    path: String
  ): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private def _append_client_query(
    url: String,
    req: Request
  ): String =
    _client_query_string(req) match {
      case Some(query) => s"${url}?${query}"
      case None => url
    }

  private def _client_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("body", "data", "-d")).flatMap {
      case Some(body) => Consequence.success(Some(body))
      case None =>
        Consequence.success(_mime_body_from_arguments(req.arguments))
    }

  private def _mime_body_from_property_names(
    properties: List[Property],
    names: List[String]
  ): Consequence[Option[MimeBody]] =
    names match {
      case Nil => Consequence.success(None)
      case head :: tail =>
        properties.find(_.name == head) match {
          case Some(property) => _mime_body_from_value(property.value).map(Some(_))
          case None => _mime_body_from_property_names(properties, tail)
        }
    }

  private def _mime_body_from_value(
    value: Any
  ): Consequence[MimeBody] =
    value match {
      case mime: MimeBody => Consequence.success(mime)
      case bag: Bag => Consequence.success(MimeBody(ContentType.APPLICATION_OCTET_STREAM, bag))
      case text: String =>
        Consequence.success(
          MimeBody(ContentType.APPLICATION_OCTET_STREAM, Bag.text(text, StandardCharsets.UTF_8))
        )
      case _ =>
        Consequence.failure("client request body must be a MimeBody, Bag, or String")
    }

  private def _mime_body_from_arguments(
    arguments: List[Argument]
  ): Option[MimeBody] =
    arguments.collectFirst { case Argument(_, body: MimeBody, _) => body }

  // TODO Phase 2.85: Replace this ad-hoc query parameter mapping with OperationDefinition-driven parameter handling.
  private def _client_query_string(
    req: Request
  ): Option[String] = {
    val params = req.arguments.collect {
      case Argument(name, value, _) if name.startsWith("arg") =>
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedValue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedName}=${encodedValue}"
    }
    if (params.isEmpty) None else Some(params.mkString("&"))
  }
}

object ClientOperation {
}
