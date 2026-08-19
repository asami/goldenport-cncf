package org.goldenport.cncf.cli

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.builtin.client.ClientComponent
import org.goldenport.cncf.component.builtin.client.GetQuery
import org.goldenport.cncf.component.builtin.client.PostCommand
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.config.ClientConfig
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Property
import org.goldenport.protocol.Request
import org.goldenport.protocol.Switch
import org.goldenport.datatype.ContentType
import org.goldenport.datatype.FileBundle
import org.goldenport.datatype.MimeBody
import org.goldenport.protocol.spec.RequestDefinition
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.schema.Multiplicity
import org.goldenport.schema.ValueDomain
import org.goldenport.schema.XFileBundle
import org.goldenport.schema.XString
import org.goldenport.value.BaseContent
import org.goldenport.cncf.http.Http4sHttpServer
import org.goldenport.cncf.http.HttpExecutionEngine
import org.goldenport.cncf.http.ServerEndpointPolicy
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.record.Record
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Apr. 30, 2026
 *  version May. 25, 2026
 *  version Jun. 29, 2026
 *  version Jul. 30, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cli] trait CncfRuntimeInstanceClientPart {
  this: GlobalObservable & CncfRuntimeInstanceLifecyclePart & CncfRuntimeInstanceCommandPart =>
  def startServer(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    (for {
      endpoint <- _server_endpoint(subsystem)
      engine <- HttpExecutionEngine.Factory.forRuntime(subsystem)
    } yield {
      val server = Http4sHttpServer.forEndpoint(engine, endpoint)
      server.start(args)
      0
    }) match {
      case Consequence.Success(exitcode) =>
        exitcode
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
        _exit_code(Consequence.Failure(conclusion))
    }
  }

  def startServer(subsystem: Subsystem, args: Array[String]): Unit = {
    (for {
      endpoint <- _server_endpoint(subsystem)
      engine <- HttpExecutionEngine.Factory.forRuntime(subsystem)
    } yield {
      val server = Http4sHttpServer.forEndpoint(engine, endpoint)
      server.start(args)
    }) match {
      case Consequence.Success(_) =>
        ()
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
  }

  private[cli] def _server_endpoint(
    subsystem: Subsystem
  ): Consequence[ServerEndpointPolicy.Endpoint] =
    ServerEndpointPolicy.resolve(subsystem)

  def executeClient(subsystem: Subsystem, req: Request): Int = {
    val args = _make_args(req)
    executeClient(subsystem, args)
  }

  def executeClient(subsystem: Subsystem, args: Array[String]): Int = {
    val operations = _component_operation_fqns(subsystem)
    val residualargs = CncfRuntime._admit_configuration_binding_arguments(args).residualArguments
    val (runtimeoptions, cleanargs) = RuntimeOptionsParser.extract(residualargs)
    val runtimeproperties = RuntimeOptionsParser.properties(runtimeoptions)
      .filter(p => _is_client_debug_passthrough_key(p.name))
    observe_trace(
      s"executeClient start args=${CncfRuntime._trace_safe_args(cleanargs).mkString(" ")} componentCount=${subsystem.components.size} operations=${_operation_sample(operations)}"
    )
    val result = _client_component(subsystem).flatMap { component =>
        parseClientArgs(subsystem, cleanargs.toArray).flatMap { req0 =>
        val req = req0.copy(properties = req0.properties ++ runtimeproperties)
        _client_action_from_request(req).flatMap { action =>
          component.execute(action).map(_ -> _request_debug_trace_job(req))
        }
      }
    }
    result match {
      case Consequence.Success((res, debugtracejob)) =>
        _print_operation_response(res)
        if (debugtracejob)
          _print_debug_job_reference(res)
      case Consequence.Failure(conclusion) =>
        _print_error(conclusion)
    }
    _exit_code(result)
  }

  private[cli] def _component_operation_fqns(subsystem: Subsystem): Vector[String] =
    subsystem.components.flatMap { comp =>
      comp.protocol.services.services.flatMap { service =>
        service.operations.operations.toVector.map(op => s"${comp.name}.${service.name}.${op.name}")
      }
    }.toVector

  private[cli] def _operation_sample(operations: Vector[String]): String = {
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

  private[cli] def _client_component(
    subsystem: Subsystem
  ): Consequence[ClientComponent] =
    subsystem.components.collectFirst { case c: ClientComponent => c } match {
      case Some(component) =>
        val operations = _component_operation_fqns(subsystem)
        observe_trace(
          s"[client:trace] client component found=${component.name} operations=${_operation_sample(operations)}"
        )
        Consequence.success(component)
      case None =>
        observe_trace("[client:trace] client component not available")
        Consequence.operationNotFound("client component")
    }

  def parseClientArgs(
    subsystem: Subsystem,
    args: Array[String]
  ): Consequence[Request] = {
    if (args.isEmpty) {
      Consequence.argumentMissing("client command")
    } else {
      _parse_client_command(subsystem, args.toIndexedSeq)
    }
  }

  private[cli] def _parse_client_command(
    subsystem: Subsystem,
    args: Seq[String]
  ): Consequence[Request] = {
    args.toVector match {
      case Vector("http", operation, rest @ _*) =>
        _parse_http_operation(operation).flatMap { op =>
          if (rest.isEmpty) {
            Consequence.argumentMissing("client http path")
          } else {
            _parse_client_http(op, rest).map { case (path, properties) =>
              Request.of(
                component = "client",
                service = "http",
                operation = op,
                arguments = List(Argument("path", path, None)),
                switches = Nil,
                properties = properties
              )
            }
          }
        }
      case Vector("http") =>
        Consequence.argumentMissing("client http operation/path")
      case _ =>
        _to_request(subsystem, args.toArray, RunMode.Client).flatMap(_command_request_to_client_request(subsystem, _))
    }
  }

  private[cli] def _command_request_to_client_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    for {
      operation <- _operation_request_definition(subsystem, req)
      normalized <- _prepare_filebundle_parameters(operation, req)
      transport <- _prepare_filebundle_transport_parameters(operation, normalized)
      action <- operation.createOperationRequest(normalized)
    } yield {
      val method = action match {
        case _: org.goldenport.cncf.action.QueryAction => "get"
        case _ => "post"
      }
      val clientproperties = req.properties.filter(p =>
        p.name == "baseurl" || _is_client_passthrough_framework_key(p.name)
      )
      Request.of(
        component = "client",
        service = "http",
        operation = method,
        arguments = Argument("path", _request_path(req), None) :: transport.arguments,
        switches = transport.switches,
        properties = transport.properties ++ clientproperties
      )
    }

  private[cli] def _prepare_filebundle_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] = {
    val names = operation.specification.request.parameters
      .filter(_is_filebundle_parameter)
      .flatMap(_.names)
      .toSet
    if (names.isEmpty) {
      Consequence.success(req)
    } else {
      for {
        arguments <- Consequence.zipN(req.arguments.map { argument =>
          if (names.contains(argument.name))
            _filebundle_value(argument.name, argument.value).map(v => argument.copy(value = v))
          else
            Consequence.success(argument)
        })
        properties <- Consequence.zipN(req.properties.map { property =>
          if (names.contains(property.name))
            _filebundle_value(property.name, property.value).map(v => property.copy(value = v))
          else
            Consequence.success(property)
        })
      } yield req.copy(arguments = arguments.toList, properties = properties.toList)
    }
  }

  private[cli] def _prepare_filebundle_parameters(
    subsystem: Subsystem,
    req: Request
  ): Consequence[Request] =
    _operation_request_definition(subsystem, req).flatMap(_prepare_filebundle_parameters(_, req))

  private[cli] def _prepare_filebundle_transport_parameters(
    operation: org.goldenport.protocol.spec.OperationDefinition,
    req: Request
  ): Consequence[Request] = {
    val names = operation.specification.request.parameters
      .filter(_is_filebundle_parameter)
      .flatMap(_.names)
      .toSet
    if (names.isEmpty) {
      Consequence.success(req)
    } else {
      for {
        arguments <- Consequence.zipN(req.arguments.map { argument =>
          if (names.contains(argument.name))
            _filebundle_transport_value(argument.name, argument.value).map(v => argument.copy(value = v))
          else
            Consequence.success(argument)
        })
        properties <- Consequence.zipN(req.properties.map { property =>
          if (names.contains(property.name))
            _filebundle_transport_value(property.name, property.value).map(v => property.copy(value = v))
          else
            Consequence.success(property)
        })
      } yield req.copy(arguments = arguments.toList, properties = properties.toList)
    }
  }

  private[cli] def _is_filebundle_parameter(
    parameter: ParameterDefinition
  ): Boolean =
    parameter.datatype == XFileBundle ||
      Option(parameter.datatype).map(_.name).exists { name =>
        _normalize_datatype_name(name) == "filebundle"
      }

  private[cli] def _normalize_datatype_name(
    name: String
  ): String =
    name.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)

  private[cli] def _filebundle_value(
    name: String,
    value: Any
  ): Consequence[FileBundle] =
    FileBundle.create(name, value)

  private[cli] def _filebundle_transport_value(
    name: String,
    value: Any
  ): Consequence[MimeBody] =
    value match {
      case bundle: FileBundle => bundle.toMimeBody
      case other => FileBundle.mimeBody(name, other)
    }

  private[cli] def _request_path(req: Request): String =
    s"/rest/v1${NamingConventions.toNormalizedPath(
      req.component.getOrElse(""),
      req.service.getOrElse(""),
      req.operation
    )}"

  private[cli] def _http_method_for_request(
    subsystem: Subsystem,
    req: Request
  ): Consequence[String] =
    _operation_request_definition(subsystem, req).flatMap(_.createOperationRequest(req)).map {
      case _: org.goldenport.cncf.action.QueryAction => "get"
      case _ => "post"
    }

  private[cli] def _operation_request_definition(
    subsystem: Subsystem,
    req: Request
  ): Consequence[org.goldenport.protocol.spec.OperationDefinition] =
    (for {
      componentname <- req.component
      servicename <- req.service
      component <- subsystem.components.find(_.name == componentname)
      service <- component.protocol.services.services.find(_.name == servicename)
      operation <- service.operations.operations.find(_.name == req.operation)
    } yield operation) match {
      case Some(op) => Consequence.success(op)
      case None => Consequence.operationNotFound(s"client target operation:${req.name}")
    }

  private[cli] def _framework_option_passthrough(
    args: Seq[String]
  ): List[Property] = {
    CncfRuntime._framework_option_passthrough(args)
  }

  private[cli] def _is_client_passthrough_framework_key(
    key: String
  ): Boolean =
    key.startsWith("textus.") || key.startsWith("cncf.") || key.startsWith("query.")

  private[cli] def _parse_http_operation(
    operation: String
  ): Consequence[String] = {
    val lower = operation.toLowerCase
    lower match {
      case "get" | "post" => Consequence.success(lower)
      case _ => Consequence.argumentInvalid("client http operation must be get or post")
    }
  }

  private[cli] def _parse_client_http(
    operation: String,
    params: Seq[String]
  ): Consequence[(String, List[Property])] = {
    val args = Array(operation) ++ params.toArray
    Request.parseArgs(_client_http_request_definition, args).flatMap { parsed =>
      parsed.arguments.headOption match {
        case Some(pathargument) =>
          Consequence.success((
            _normalize_path(pathargument.value.toString),
            _canonical_http_properties(params, parsed.properties) ++ _http_tail_properties(parsed.arguments.drop(1))
          ))
        case None =>
          Consequence.argumentMissing("client http path")
      }
    }
  }

  private[cli] def _http_tail_properties(
    arguments: List[Argument]
  ): List[Property] =
    arguments.zipWithIndex.map { case (argument, index) =>
      val text = argument.value.toString
      text.split("=", 2).toList match {
        case key :: value :: Nil if key.nonEmpty =>
          Property(key, value, None)
        case _ =>
          Property(s"arg${index + 1}", text, None)
      }
    }

  private[cli] def _canonical_http_properties(
    params: Seq[String],
    properties: List[Property]
  ): List[Property] =
    if (_has_short_http_body_option(params))
      properties.map {
        case Property("data", value, origin) => Property("http.body", value, origin)
        case other => other
      }
    else
      properties

  private[cli] def _has_short_http_body_option(
    params: Seq[String]
  ): Boolean =
    params.exists(p => p == "-d" || p.startsWith("-d="))

  private[cli] def _normalize_path(path: String): String = {
    val (route, suffix) = _split_path_suffix(path)
    val normalizedroute = if (route.contains(".")) route.replace(".", "/") else route
    val normalized = s"${normalizedroute}${suffix}"
    if (normalized.startsWith("/")) normalized else s"/${normalized}"
  }

  private[cli] def _split_path_suffix(
    path: String
  ): (String, String) = {
    val query = path.indexOf('?')
    val fragment = path.indexOf('#')
    val suffixstart = Vector(query, fragment).filter(_ >= 0).minOption
    suffixstart match {
      case Some(i) => path.substring(0, i) -> path.substring(i)
      case None => path -> ""
    }
  }

  private[cli] def _parse_client_path(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    if (args.isEmpty) {
      Consequence.argumentMissing("client path")
    } else {
      val xs = args.toVector
      if (xs.length >= 3) {
        val component = xs(0)
        val service = xs(1)
        val operation = xs(2)
        val rest = xs.drop(3)
        Consequence.success((_normalize_path(s"/${component}/${service}/${operation}"), rest))
      } else {
        val single = xs.head
        val rest = xs.drop(1)
        _parse_component_service_operation_string(single).map { case (component, service, operation) =>
          (_normalize_path(s"/${component}/${service}/${operation}"), rest)
        }
      }
    }
  }

  private[cli] val _client_http_request_definition: RequestDefinition = {
    val base = RequestDefinition.curlLike
    val baseurlparameter = ParameterDefinition(
      content = BaseContent.simple("baseurl"),
      kind = ParameterDefinition.Kind.Property,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne)
    )
    RequestDefinition(base.parameters :+ baseurlparameter)
  }

  private[cli] def _client_action_from_request(
    req: Request
  ): Consequence[org.goldenport.cncf.action.Action] = {
      if (req.component.contains("client") && req.service.contains("http")) {
        _client_path_from_request(req).flatMap { path =>
          val baseurl = _client_baseurl_from_request(req)
          val rawurl = _build_client_url(baseurl, path)
          val url = _append_client_query(rawurl, req)
          observe_trace(
            s"[client:trace] client action request operation=${req.operation} path=${path}"
          )
          req.operation match {
        case "post" =>
          _client_http_body_and_header(req).map { case (body, header) =>
            new PostCommand(
              req,
              // "system.ping", // TODO generic
              HttpRequest.fromUrl(
                method = HttpRequest.POST,
                url = _http_request_base_url(url),
                query = _http_request_query(url),
                header = header,
                body = body.map(_.value)
              )
            )
          }
        case "get" =>
          _client_explicit_mime_body_from_request(req).flatMap {
            case Some(_) =>
              Consequence.argumentInvalid("client http get does not accept a body")
            case None =>
              Consequence.success(
                new GetQuery(
                  req,
                  // "system.ping",
                HttpRequest.fromUrl(
                  method = HttpRequest.GET,
                  url = _http_request_base_url(url),
                  query = _http_request_query(url),
                  header = _client_http_header(req, None)
                )
              )
              )
          }
        case other =>
          Consequence.argumentInvalid(s"client http operation not supported: ${other}")
      }
      }
    } else {
      Consequence.argumentMissing("client http request")
    }
  }

  private[cli] def _http_request_base_url(
    url: String
  ): java.net.URL = {
    val uri = URI.create(url)
    new URI(
      uri.getScheme,
      uri.getAuthority,
      uri.getPath,
      null,
      uri.getFragment
    ).toURL
  }

  private[cli] def _http_request_query(
    url: String
  ): Record =
    Option(URI.create(url).toURL.getQuery)
      .filter(_.nonEmpty)
      .map(HttpRequest.parseQuery)
      .getOrElse(Record.empty)

  private[cli] def _client_baseurl_from_request(
    req: Request
  ): String =
    req.properties.find(_.name == "baseurl").map(_.value.toString)
      .orElse(sys.props.get("textus.http.baseurl"))
      .orElse(sys.props.get("cncf.http.baseurl"))
      .getOrElse(ClientConfig.DefaultBaseUrl)

  private[cli] def _build_client_url(
    baseurl: String,
    path: String
  ): String = {
    val base = if (baseurl.endsWith("/")) baseurl.dropRight(1) else baseurl
    val suffix = if (path.startsWith("/")) path else s"/${path}"
    s"${base}${suffix}"
  }

  private[cli] def _append_client_query(
    url: String,
    req: Request
  ): String =
    _client_query_string(req) match {
      case Some(query) =>
        val separator =
          if (url.contains("?")) {
            if (url.endsWith("?") || url.endsWith("&")) "" else "&"
          } else {
            "?"
          }
        s"${url}${separator}${query}"
      case None => url
    }

  // TODO Phase 2.85: Replace this ad-hoc query parameter mapping with OperationDefinition-driven parameter handling.
  private[cli] def _client_query_string(
    req: Request
  ): Option[String] = {
    val argumentparams = req.arguments.collect {
      case Argument(name, value, _) if name.startsWith("arg") && !_is_multipart_value(value) =>
        val encodedname = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedvalue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedname}=${encodedvalue}"
    }
    val propertyparams = req.properties.collect {
      case Property(name, value, _) if _is_http_parameter_property(name) && !_is_multipart_value(value) =>
        val encodedname = URLEncoder.encode(name, StandardCharsets.UTF_8)
        val encodedvalue = URLEncoder.encode(value.toString, StandardCharsets.UTF_8)
        s"${encodedname}=${encodedvalue}"
    }
    val params = argumentparams ++ propertyparams
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private[cli] def _is_http_parameter_property(
    name: String
  ): Boolean =
    name != null &&
      name.nonEmpty &&
      name != "header" &&
      !name.startsWith("header.") &&
      name != "baseurl" &&
      name != "http.body" &&
      name != "http.data" &&
      name != "-d"

  private[cli] def _client_path_from_request(
    req: Request
  ): Consequence[String] =
    req.arguments.find(_.name == "path").map(_.value.toString) match {
      case Some(path) => Consequence.success(path)
      case None => Consequence.argumentMissing("client http path")
    }

  private[cli] def _client_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("http.body", "http.data", "-d")).flatMap {
      case Some(body) => Consequence.success(Some(body))
      case None =>
        _client_multipart_mime_body(req) match {
          case Some(body) => Consequence.success(Some(body))
          case None =>
            _client_form_mime_body(req) match {
              case Some(body) => Consequence.success(Some(body))
              case None => Consequence.success(_mime_body_from_arguments(req.arguments))
            }
        }
    }

  private[cli] def _client_explicit_mime_body_from_request(
    req: Request
  ): Consequence[Option[MimeBody]] =
    _mime_body_from_property_names(req.properties, List("http.body", "http.data", "-d")).flatMap {
      case some @ Some(_) => Consequence.success(some)
      case None => Consequence.success(_mime_body_from_arguments(req.arguments))
    }

  private[cli] def _client_http_body_and_header(
    req: Request
  ): Consequence[(Option[MimeBody], Record)] =
    _client_mime_body_from_request(req).map {
      case Some(body) =>
        (Some(body), _client_http_header(req, Some(body)))
      case None =>
        (None, _client_http_header(req, None))
    }

  private[cli] def _client_http_header(
    req: Request,
    body: Option[MimeBody]
  ): Record = {
    val contenttype = body.toVector.map(x => "Content-Type" -> x.contentType.header)
    val explicit = req.properties.collect {
      case Property("header", record: Record, _) =>
        record.asMap.toVector.map { case (name, value) => name -> value.toString }
      case Property("header", values: Map[_, _], _) =>
        values.toVector.collect {
          case (name: String, value) => name -> value.toString
        }
      case Property(name, value, _) if name.startsWith("header.") && name.length > "header.".length =>
        Vector(name.drop("header.".length) -> value.toString)
    }.flatten ++ req.arguments.collect {
      case Argument("header", record: Record, _) =>
        record.asMap.toVector.map { case (name, value) => name -> value.toString }
      case Argument("header", values: Map[_, _], _) =>
        values.toVector.collect {
          case (name: String, value) => name -> value.toString
        }
      case Argument(name, value, _) if name.startsWith("header.") && name.length > "header.".length =>
        Vector(name.drop("header.".length) -> value.toString)
    }.flatten
    Record.create(contenttype ++ explicit)
  }

  private[cli] def _client_multipart_mime_body(
    req: Request
  ): Option[MimeBody] = {
    val fields = _client_multipart_fields(req)
    if (!fields.exists(_.isBinary)) {
      None
    } else {
      val boundary = s"cncf-${java.util.UUID.randomUUID().toString}"
      val bytes = _render_multipart(fields, boundary)
      Some(
        MimeBody(
          ContentType.MULTIPART_FORM_DATA.copy(parameters = Map("boundary" -> boundary)),
          Bag.binary(bytes)
        )
      )
    }
  }

  private[cli] final case class ClientMultipartField(
    name: String,
    value: Any
  ) {
    def isBinary: Boolean = _is_multipart_value(value)
  }

  private[cli] def _client_multipart_fields(
    req: Request
  ): Vector[ClientMultipartField] = {
    val argumentparams = req.arguments.collect {
      case Argument(name, value, _) if name != "path" =>
        ClientMultipartField(name, value)
    }
    val switchparams = req.switches.collect {
      case Switch(name, value, _) if value =>
        ClientMultipartField(name, "true")
    }
    val propertyparams = _client_http_parameter_properties(req).map { p =>
      ClientMultipartField(p.name, p.value)
    }
    (argumentparams ++ switchparams ++ propertyparams).toVector
  }

  private[cli] def _render_multipart(
    fields: Vector[ClientMultipartField],
    boundary: String
  ): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    fields.foreach { field =>
      _write_ascii(out, s"--${boundary}\r\n")
      field.value match {
        case body: MimeBody =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"; filename="${_quote_http_header(field.name)}.zip"\r\n""")
          _write_ascii(out, s"Content-Type: ${body.contentType.header}\r\n\r\n")
          _write_bag(out, body.value)
          _write_ascii(out, "\r\n")
        case bag: Bag =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"; filename="${_quote_http_header(field.name)}"\r\n""")
          _write_ascii(out, s"Content-Type: ${ContentType.APPLICATION_OCTET_STREAM.header}\r\n\r\n")
          _write_bag(out, bag)
          _write_ascii(out, "\r\n")
        case other =>
          _write_ascii(out, s"""Content-Disposition: form-data; name="${_quote_http_header(field.name)}"\r\n\r\n""")
          out.write(other.toString.getBytes(StandardCharsets.UTF_8))
          _write_ascii(out, "\r\n")
      }
    }
    _write_ascii(out, s"--${boundary}--\r\n")
    out.toByteArray
  }

  private[cli] def _write_ascii(
    out: ByteArrayOutputStream,
    text: String
  ): Unit =
    out.write(text.getBytes(StandardCharsets.US_ASCII))

  private[cli] def _write_bag(
    out: ByteArrayOutputStream,
    bag: Bag
  ): Unit = {
    val in = bag.openInputStream()
    try {
      val buffer = new Array[Byte](8192)
      var read = in.read(buffer)
      while (read != -1) {
        out.write(buffer, 0, read)
        read = in.read(buffer)
      }
    } finally {
      in.close()
    }
  }

  private[cli] def _quote_http_header(
    value: String
  ): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  private[cli] def _client_form_mime_body(
    req: Request
  ): Option[MimeBody] =
    _client_form_encoded_payload(req).map { payload =>
      MimeBody(
        ContentType.parse("application/x-www-form-urlencoded"),
        Bag.text(payload, StandardCharsets.UTF_8)
      )
    }

  private[cli] def _client_form_encoded_payload(
    req: Request
  ): Option[String] = {
    val argumentparams = req.arguments.collect {
      case Argument(name, value, _) if name != "path" && !value.isInstanceOf[MimeBody] =>
        s"${URLEncoder.encode(name, StandardCharsets.UTF_8)}=${URLEncoder.encode(value.toString, StandardCharsets.UTF_8)}"
    }
    val switchparams = req.switches.collect {
      case Switch(name, value, _) if value =>
        s"${URLEncoder.encode(name, StandardCharsets.UTF_8)}=true"
    }
    val propertyparams = _client_http_parameter_properties(req).filterNot(p => _is_multipart_value(p.value)).map { p =>
      s"${URLEncoder.encode(p.name, StandardCharsets.UTF_8)}=${URLEncoder.encode(p.value.toString, StandardCharsets.UTF_8)}"
    }
    val params = argumentparams ++ switchparams ++ propertyparams
    if (params.isEmpty) None else Some(params.mkString("&"))
  }

  private[cli] def _client_http_parameter_properties(
    req: Request
  ): List[Property] =
    req.properties.filter(p => _is_http_parameter_property(p.name))

  private[cli] def _is_multipart_value(
    value: Any
  ): Boolean =
    value.isInstanceOf[MimeBody] || value.isInstanceOf[Bag]

  private[cli] def _is_form_urlencoded(
    body: MimeBody
  ): Boolean =
    body.contentType.mimeType.value.equalsIgnoreCase("application/x-www-form-urlencoded")

  private[cli] def _mime_body_from_property_names(
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

  private[cli] def _mime_body_from_value(
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
        Consequence.argumentInvalid("client request body must be a MimeBody, Bag, or String")
    }

  private[cli] def _mime_body_from_arguments(
    arguments: List[Argument]
  ): Option[MimeBody] =
    arguments.collectFirst { case Argument(_, body: MimeBody, _) => body }
}
