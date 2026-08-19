package org.goldenport.cncf.mcp.client

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException}
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.time.Duration
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.{Callable, ConcurrentHashMap, Executors, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicLong

import scala.jdk.CollectionConverters.*
import scala.util.Try

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, ServiceContract, VariationSelection}
import org.goldenport.cncf.config.{RuntimeSecretResolver, SecretMaterial, SecretReference}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.mcp.McpProtocolRevision
import org.goldenport.observation.{Cause, Descriptor}

/*
 * MCP Streamable HTTP transport behind the provider-neutral client Port.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class McpStreamableHttpCredential private[client] () {
  private[client] def secretReference: SecretReference
}

object McpStreamableHttpCredential {
  def bearerC(reference: SecretReference): Consequence[McpStreamableHttpCredential] =
    Option(reference).map(x => Consequence.success(new _Bearer(x))).getOrElse(
      Consequence.argumentMissing("credentialReference")
    )

  private final class _Bearer(
    val secretReference: SecretReference
  ) extends McpStreamableHttpCredential {
    override def toString: String =
      "McpStreamableHttpCredential.Bearer(<redacted>)"
  }
}

final case class McpStreamableHttpServerConfig private (
  serverId: McpServerId,
  endpoint: URI,
  requestedProtocolVersion: McpProtocolRevision,
  supportedProtocolVersions: Set[McpProtocolRevision],
  credential: Option[McpStreamableHttpCredential]
)

object McpStreamableHttpServerConfig {
  val DEFAULT_PROTOCOL_VERSION = McpProtocolRevision.PREFERRED.print
  val DEFAULT_SUPPORTED_PROTOCOL_VERSIONS = McpProtocolRevision.SUPPORTED.map(_.print).toSet

  def createC(
    serverid: McpServerId,
    endpoint: String,
    requestedprotocolversion: String = DEFAULT_PROTOCOL_VERSION,
    supportedprotocolversions: Set[String] = DEFAULT_SUPPORTED_PROTOCOL_VERSIONS,
    credential: Option[McpStreamableHttpCredential] = None
  ): Consequence[McpStreamableHttpServerConfig] =
    if (credential.exists(_ == null))
      Consequence.argumentInvalid(
        "credential",
        "an admitted MCP Streamable HTTP credential",
        "null"
      )
    else for {
      uri <- _uri_c(endpoint)
      requested <- McpProtocolRevision.parseC(requestedprotocolversion)
      supported <- _supported_revisions_c(supportedprotocolversions)
      _ <- if (supported.contains(requested)) Consequence.unit else
        Consequence.argumentPolicyViolation(
          "requestedProtocolVersion",
          "mcp-client.protocol-version",
          "one configured supported protocol version",
          "not configured"
        )
    } yield McpStreamableHttpServerConfig(
      serverid,
      uri,
      requested,
      supported,
      credential
    )

  private def _supported_revisions_c(
    values: Set[String]
  ): Consequence[Set[McpProtocolRevision]] =
    if (values.isEmpty)
      Consequence.argumentMissing("supportedProtocolVersions")
    else
      values.toVector.sorted.foldLeft(Consequence.success(Set.empty[McpProtocolRevision])) {
        case (z, value) =>
          for {
            revisions <- z
            revision <- McpProtocolRevision.parseC(value)
          } yield revisions + revision
      }

  private def _uri_c(value: String): Consequence[URI] =
    Try(URI.create(Option(value).map(_.trim).getOrElse(""))).toOption match {
      case Some(uri)
          if uri.isAbsolute &&
            Set("http", "https").contains(Option(uri.getScheme).map(_.toLowerCase(Locale.ROOT)).getOrElse("")) &&
            Option(uri.getHost).exists(_.nonEmpty) &&
            uri.getUserInfo == null &&
            uri.getFragment == null =>
        Consequence.success(uri)
      case _ =>
        Consequence.argumentFormatError(
          "endpoint",
          "absolute HTTP(S) URI without user-info or fragment",
          "invalid"
        )
    }
}

final case class McpStreamableHttpServerSetConfig private (
  serverSetId: McpServerSetId,
  servers: Vector[McpStreamableHttpServerConfig]
)

object McpStreamableHttpServerSetConfig {
  def createC(
    serversetid: McpServerSetId,
    servers: Vector[McpStreamableHttpServerConfig]
  ): Consequence[McpStreamableHttpServerSetConfig] =
    if (servers.isEmpty)
      Consequence.argumentMissing("servers")
    else if (servers.map(_.serverId).distinct.size != servers.size)
      Consequence.argumentPolicyViolation(
        "servers",
        "mcp-client.streamable-http",
        "unique server identities",
        "duplicate"
      )
    else
      Consequence.success(McpStreamableHttpServerSetConfig(
        serversetid,
        servers.sortBy(_.serverId.print)
      ))
}

final class McpStreamableHttpTransportProvider private (
  private val _configs: Map[McpServerSetId, McpStreamableHttpServerSetConfig],
  secretresolver: Option[RuntimeSecretResolver],
  exchangefactory: () => McpStreamableHttpExchange
) extends ExtensionPoint[McpClientTransport] {
  def supports(
    contract: ServiceContract[McpClientTransport],
    variation: VariationSelection
  )(using ExecutionContext): Boolean =
    variation == VariationSelection() &&
      McpClientTransportPortApi.serverSetId(contract).exists(_configs.contains)

  def provide(
    contract: ServiceContract[McpClientTransport],
    variation: VariationSelection
  )(using ExecutionContext): Consequence[McpClientTransport] =
    McpClientTransportPortApi.serverSetId(contract).flatMap(_configs.get) match {
      case Some(config) =>
        Consequence.success(new McpStreamableHttpTransport(config, secretresolver, exchangefactory()))
      case None =>
        Consequence.serviceUnavailable(
          "MCP Streamable HTTP transport is not configured",
          Cause.Kind.Policy,
          _diagnostic_facets("transport-not-configured")
        )
    }

  def binding: Component.Binding[McpClientTransportRequirement, McpClientTransport] =
    Component.Binding(Port(
      api = McpClientTransportPortApi,
      spi = Vector(this),
      variation = McpClientTransportSelectionPoint
    ))

  private def _diagnostic_facets(reason: String): Vector[Descriptor.Facet] =
    Vector(
      Descriptor.Facet.Reason(reason),
      Descriptor.Facet.Policy("mcp-client.streamable-http")
    )
}

object McpStreamableHttpTransportProvider {
  def createC(
    configs: Vector[McpStreamableHttpServerSetConfig]
  ): Consequence[McpStreamableHttpTransportProvider] =
    _create_c(configs, None, () => new JavaMcpStreamableHttpExchange(HttpClient.newHttpClient()))

  private[cncf] def createC(
    configs: Vector[McpStreamableHttpServerSetConfig],
    secretresolver: RuntimeSecretResolver
  ): Consequence[McpStreamableHttpTransportProvider] =
    _create_c(configs, Some(secretresolver), () => new JavaMcpStreamableHttpExchange(HttpClient.newHttpClient()))

  private[client] def createC(
    configs: Vector[McpStreamableHttpServerSetConfig],
    exchangefactory: () => McpStreamableHttpExchange
  ): Consequence[McpStreamableHttpTransportProvider] =
    _create_c(configs, None, exchangefactory)

  private[client] def createC(
    configs: Vector[McpStreamableHttpServerSetConfig],
    secretresolver: RuntimeSecretResolver,
    exchangefactory: () => McpStreamableHttpExchange
  ): Consequence[McpStreamableHttpTransportProvider] =
    _create_c(configs, Some(secretresolver), exchangefactory)

  private def _create_c(
    configs: Vector[McpStreamableHttpServerSetConfig],
    secretresolver: Option[RuntimeSecretResolver],
    exchangefactory: () => McpStreamableHttpExchange
  ): Consequence[McpStreamableHttpTransportProvider] = {
    val identities = configs.map(_.serverSetId)
    if (identities.distinct.size != identities.size)
      Consequence.argumentPolicyViolation(
        "serverSets",
        "mcp-client.streamable-http",
        "unique server-set identities",
        "duplicate"
      )
    else if (configs.flatMap(_.servers).exists(_.credential.nonEmpty) && secretresolver.isEmpty)
      Consequence.configurationInvalid("MCP Streamable HTTP credential references require a runtime secret resolver")
    else
      Consequence.success(new McpStreamableHttpTransportProvider(
        configs.map(x => x.serverSetId -> x).toMap,
        secretresolver,
        exchangefactory
      ))
  }
}

private[client] final case class McpStreamableHttpRequest(
  method: String,
  endpoint: URI,
  headers: Map[String, String],
  body: Option[String],
  timeoutMillis: Long,
  maximumResponseBytes: Long
)

private[client] final case class McpStreamableHttpResponse(
  status: Int,
  headers: Map[String, String],
  body: String
) {
  def header(name: String): Option[String] =
    headers.get(name.toLowerCase(Locale.ROOT))
}

private[client] abstract class McpStreamableHttpExchange extends AutoCloseable {
  def execute(request: McpStreamableHttpRequest): Consequence[McpStreamableHttpResponse]
  def close(): Unit = ()
}

private[client] final class McpStreamableHttpBodyReader extends AutoCloseable {
  private val _executor = Executors.newCachedThreadPool()

  def readC(
    input: java.io.InputStream,
    maximumbytes: Long,
    timeoutmillis: Long
  ): Consequence[String] = {
    val future = _executor.submit(new Callable[Consequence[String]] {
      def call(): Consequence[String] =
        _read_body_c(input, maximumbytes)
    })
    try
      future.get(timeoutmillis, TimeUnit.MILLISECONDS)
    catch {
      case _: TimeoutException =>
        Try(input.close())
        future.cancel(true)
        _transport_failure("timeout", Cause.Kind.Timeout)
      case _: InterruptedException =>
        Try(input.close())
        future.cancel(true)
        Thread.currentThread().interrupt()
        _transport_failure("interrupted", Cause.Kind.Exhaustion)
      case _: Throwable =>
        Try(input.close())
        future.cancel(true)
        _transport_failure("unavailable", Cause.Kind.Unknown)
    }
  }

  def close(): Unit =
    _executor.shutdownNow()

  private def _read_body_c(
    input: java.io.InputStream,
    maximumbytes: Long
  ): Consequence[String] = {
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](8192)
    var count = 0L
    try {
      var length = input.read(buffer)
      while (length >= 0 && count + length <= maximumbytes) {
        if (length > 0) {
          output.write(buffer, 0, length)
          count += length
        }
        length = input.read(buffer)
      }
      if (length >= 0)
        _response_limit_failure(maximumbytes, count + length)
      else
        Consequence.success(output.toString(StandardCharsets.UTF_8))
    } finally {
      input.close()
      output.close()
    }
  }

  private def _response_limit_failure[A](limit: Long, actual: Long): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP response exceeds its byte limit",
      Cause.Kind.Limit,
      Vector(
        Descriptor.Facet.Reason("maximum-output-bytes"),
        Descriptor.Facet.Policy("mcp-client.limits"),
        Descriptor.Facet.Limit(limit),
        Descriptor.Facet.Actual(actual)
      )
    )

  private def _transport_failure[A](reason: String, kind: Cause.Kind): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP transport failed",
      kind,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.streamable-http")
      )
    )
}

private final class JavaMcpStreamableHttpExchange(
  client: HttpClient
) extends McpStreamableHttpExchange {
  private val _body_reader = new McpStreamableHttpBodyReader()

  def execute(request: McpStreamableHttpRequest): Consequence[McpStreamableHttpResponse] = {
    val startedat = System.nanoTime()
    val builder = HttpRequest.newBuilder(request.endpoint)
      .timeout(Duration.ofMillis(request.timeoutMillis))
    request.headers.toVector.sortBy(_._1).foreach { case (name, value) =>
      builder.header(name, value)
    }
    request.method match {
      case "POST" => builder.POST(HttpRequest.BodyPublishers.ofString(request.body.getOrElse(""), StandardCharsets.UTF_8))
      case "DELETE" => builder.DELETE()
      case method => builder.method(method, HttpRequest.BodyPublishers.noBody())
    }
    try {
      val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
      _read_response_c(response, request, startedat)
    } catch {
      case _: HttpTimeoutException =>
        _transport_failure("timeout", Cause.Kind.Timeout)
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        _transport_failure("interrupted", Cause.Kind.Exhaustion)
      case _: Throwable =>
        _transport_failure("unavailable", Cause.Kind.Unknown)
    }
  }

  override def close(): Unit =
    _body_reader.close()

  private def _read_response_c(
    response: HttpResponse[java.io.InputStream],
    request: McpStreamableHttpRequest,
    startedat: Long
  ): Consequence[McpStreamableHttpResponse] = {
    val elapsedmillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat)
    val remainingmillis = request.timeoutMillis - elapsedmillis
    if (remainingmillis <= 0L) {
      Try(response.body().close())
      _transport_failure("timeout", Cause.Kind.Timeout)
    } else
      _body_reader.readC(response.body(), request.maximumResponseBytes, remainingmillis).map { body =>
        McpStreamableHttpResponse(
          response.statusCode(),
          response.headers().map().asScala.toVector.map { case (name, values) =>
            name.toLowerCase(Locale.ROOT) -> values.asScala.headOption.getOrElse("")
          }.toMap,
          body
        )
      }
  }

  private def _transport_failure[A](
    reason: String,
    kind: Cause.Kind
  ): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP transport failed",
      kind,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.streamable-http")
      )
    )
}

private final class McpStreamableHttpTransport(
  config: McpStreamableHttpServerSetConfig,
  secretresolver: Option[RuntimeSecretResolver],
  exchange: McpStreamableHttpExchange
) extends McpClientTransport {
  private final case class _SessionId(value: String)

  private final case class _Session(
    protocolVersion: McpProtocolRevision,
    sessionId: Option[_SessionId]
  )

  private val _request_id = new AtomicLong(0L)
  private val _sessions = new ConcurrentHashMap[McpServerId, _Session]()
  private val _limits = new ConcurrentHashMap[McpServerId, McpClientLimits]()
  private val _configs = config.servers.map(x => x.serverId -> x).toMap

  def initialize(
    server: McpClientServer,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[Unit] = synchronized {
    Option(_sessions.get(server.id)) match {
      case Some(_) => Consequence.unit
      case None =>
        _config_c(server).flatMap { serverconfig =>
          val requestid = _request_id.incrementAndGet()
          val message = _request(requestid, "initialize", Some(Json.obj(
            "protocolVersion" -> Json.fromString(serverconfig.requestedProtocolVersion.print),
            "capabilities" -> Json.obj(),
            "clientInfo" -> Json.obj(
              "name" -> Json.fromString("cncf"),
              "version" -> Json.fromString("phase-45")
            )
          )))
          _post_request(serverconfig, None, None, message, requestid, limits).flatMap { case (response, result) =>
            result.hcursor.get[String]("protocolVersion").toOption.flatMap(
              McpProtocolRevision.parseC(_).toOption
            ) match {
              case Some(version) if serverconfig.supportedProtocolVersions.contains(version) =>
                _session_id_option_c(response.header("mcp-session-id")).flatMap { sessionid =>
                  val session = _Session(version, sessionid)
                  _post_notification(
                    serverconfig,
                    session,
                    _notification("notifications/initialized", None),
                    limits
                  ).map { _ =>
                    _sessions.put(server.id, session)
                    _limits.put(server.id, limits)
                    ()
                  }
                }
              case _ => _protocol_failure("unsupported-protocol-version")
            }
          }
        }
    }
  }

  def listTools(
    server: McpClientServer,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[Vector[McpClientTool]] =
    for {
      serverconfig <- _config_c(server)
      _ <- _session_c(server)
      tools <- _list_tools(serverconfig, server, None, 0, Vector.empty, limits)
    } yield tools

  def callTool(
    server: McpClientServer,
    call: McpClientCall,
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[McpClientResult] =
    for {
      serverconfig <- _config_c(server)
      session <- _session_c(server)
      requestid = _request_id.incrementAndGet()
      params = Json.obj(
        "name" -> Json.fromString(call.toolIdentity.toolName.print),
        "arguments" -> _value_to_json(call.arguments)
      )
      response <- _post_session_request(
        serverconfig,
        server,
        session,
        _request(requestid, "tools/call", Some(params)),
        requestid,
        allowreinitialize = true,
        limits = limits,
        enforceinputlimit = true
      )
      result <- _call_result_c(response._2)
    } yield result

  override def close(): Unit = synchronized {
    _configs.values.toVector.sortBy(_.serverId.print).foreach { serverconfig =>
      Option(_sessions.remove(serverconfig.serverId)).foreach { session =>
        val limits = Option(_limits.remove(serverconfig.serverId)).getOrElse(McpClientLimits.default)
        session.sessionId.foreach { sessionid =>
          _execute_server_c(serverconfig, McpStreamableHttpRequest(
            "DELETE",
            serverconfig.endpoint,
            _headers(Some(sessionid), Some(session.protocolVersion), accept = "application/json, text/event-stream"),
            None,
            limits.timeoutMillis,
            limits.maximumOutputBytes
          ), None)
        }
      }
    }
    exchange.close()
  }

  private def _list_tools(
    serverconfig: McpStreamableHttpServerConfig,
    server: McpClientServer,
    cursor: Option[String],
    pages: Int,
    accumulator: Vector[McpClientTool],
    limits: McpClientLimits
  )(using ExecutionContext): Consequence[Vector[McpClientTool]] =
    if (pages >= 128)
      _protocol_failure("catalog-page-limit")
    else {
      val requestid = _request_id.incrementAndGet()
      val params = cursor.map(x => Json.obj("cursor" -> Json.fromString(x)))
      _session_c(server).flatMap { session =>
        _post_session_request(
          serverconfig,
          server,
          session,
          _request(requestid, "tools/list", params),
          requestid,
          allowreinitialize = true,
          limits = limits,
          enforceinputlimit = false
        ).flatMap { case (_, result) =>
          result.hcursor.downField("tools").focus.flatMap(_.asArray) match {
            case Some(values) =>
              _traverse(values.toVector)(_admitted_tool_c(server, _)).flatMap { tools =>
                val admittedtools = tools.flatten
                val next = result.hcursor.get[String]("nextCursor").toOption.filter(_.nonEmpty)
                next match {
                  case Some(value) => _list_tools(serverconfig, server, Some(value), pages + 1, accumulator ++ admittedtools, limits)
                  case None => Consequence.success(accumulator ++ admittedtools)
                }
              }
            case None => _protocol_failure("tools-list-missing")
          }
        }
      }
    }

  private def _post_session_request(
    serverconfig: McpStreamableHttpServerConfig,
    server: McpClientServer,
    session: _Session,
    message: Json,
    requestid: Long,
    allowreinitialize: Boolean,
    limits: McpClientLimits,
    enforceinputlimit: Boolean
  )(using ExecutionContext): Consequence[(McpStreamableHttpResponse, Json)] =
    _execute_server_c(serverconfig, McpStreamableHttpRequest(
      "POST",
      serverconfig.endpoint,
      _headers(session.sessionId, Some(session.protocolVersion), "application/json, text/event-stream"),
      Some(message.noSpaces),
      limits.timeoutMillis,
      limits.maximumOutputBytes
    ), if (enforceinputlimit) Some(limits.maximumInputBytes) else None).flatMap { response =>
      if (response.status == 404 && session.sessionId.nonEmpty && allowreinitialize) {
        _sessions.remove(server.id, session)
        initialize(server, limits).flatMap { _ =>
          _session_c(server).flatMap { freshsession =>
            _post_session_request(
              serverconfig,
              server,
              freshsession,
              message,
              requestid,
              allowreinitialize = false,
              limits = limits,
              enforceinputlimit = enforceinputlimit
            )
          }
        }
      } else if (response.status / 100 != 2)
        _transport_status_failure(response.status)
      else
        _response_result_c(response, requestid).map(response -> _)
    }

  private def _post_request(
    serverconfig: McpStreamableHttpServerConfig,
    sessionid: Option[_SessionId],
    protocolversion: Option[McpProtocolRevision],
    message: Json,
    requestid: Long,
    limits: McpClientLimits
  ): Consequence[(McpStreamableHttpResponse, Json)] =
    _execute_server_c(serverconfig, McpStreamableHttpRequest(
      "POST",
      serverconfig.endpoint,
      _headers(sessionid, protocolversion, "application/json, text/event-stream"),
      Some(message.noSpaces),
      limits.timeoutMillis,
      limits.maximumOutputBytes
    ), None).flatMap { response =>
      if (response.status / 100 != 2)
        _transport_status_failure(response.status)
      else
        _response_result_c(response, requestid).map(response -> _)
    }

  private def _post_notification(
    serverconfig: McpStreamableHttpServerConfig,
    session: _Session,
    message: Json,
    limits: McpClientLimits
  ): Consequence[Unit] =
    _execute_server_c(serverconfig, McpStreamableHttpRequest(
      "POST",
      serverconfig.endpoint,
      _headers(session.sessionId, Some(session.protocolVersion), "application/json, text/event-stream"),
      Some(message.noSpaces),
      limits.timeoutMillis,
      limits.maximumOutputBytes
    ), None).flatMap { response =>
      if (response.status == 202)
        Consequence.unit
      else
        _transport_status_failure(response.status)
    }

  private def _execute_server_c(
    serverconfig: McpStreamableHttpServerConfig,
    request: McpStreamableHttpRequest,
    maximuminputbytes: Option[Long]
  ): Consequence[McpStreamableHttpResponse] =
    _authorization_headers_c(serverconfig).flatMap { authorizationheaders =>
      _execute_c(request.copy(headers = request.headers ++ authorizationheaders), maximuminputbytes)
    }

  private def _execute_c(
    request: McpStreamableHttpRequest,
    maximuminputbytes: Option[Long]
  ): Consequence[McpStreamableHttpResponse] =
    maximuminputbytes match {
      case Some(limit) =>
        val actual = request.body.map(_.getBytes(StandardCharsets.UTF_8).length.toLong).getOrElse(0L)
        if (actual > limit)
          _limit_failure("maximum-input-bytes", limit, actual)
        else
          _execute_response_c(request)
      case None => _execute_response_c(request)
    }

  private def _execute_response_c(
    request: McpStreamableHttpRequest
  ): Consequence[McpStreamableHttpResponse] =
    exchange.execute(request).flatMap { response =>
      val actual = response.body.getBytes(StandardCharsets.UTF_8).length.toLong
      if (actual > request.maximumResponseBytes)
        _limit_failure("maximum-output-bytes", request.maximumResponseBytes, actual)
      else
        Consequence.success(response)
    }

  private def _authorization_headers_c(
    serverconfig: McpStreamableHttpServerConfig
  ): Consequence[Map[String, String]] =
    serverconfig.credential match {
      case None => Consequence.success(Map.empty)
      case Some(credential) =>
        secretresolver match {
          case Some(resolver) =>
            resolver.resolveSecret(credential.secretReference) match {
              case Consequence.Success(material) => _bearer_header_c(material)
              case _: Consequence.Failure[?] => _credential_failure("credential-reference-unresolved")
            }
          case None => _credential_failure("credential-resolver-unavailable")
        }
    }

  private def _bearer_header_c(
    material: SecretMaterial
  ): Consequence[Map[String, String]] = {
    val bytes = material._copy_bytes
    try {
      val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
      val token = Try(decoder.decode(ByteBuffer.wrap(bytes)).toString).toOption
      token.filter(_.matches("[A-Za-z0-9\\-._~+/]+={0,}")) match {
        case Some(value) => Consequence.success(Map("Authorization" -> s"Bearer $value"))
        case None => _credential_failure("credential-material-invalid")
      }
    } finally {
      Arrays.fill(bytes, 0.toByte)
    }
  }

  private def _credential_failure[A](reason: String): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP credential policy rejected the request",
      Cause.Kind.Policy,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.credential-reference")
      )
    )

  private def _limit_failure[A](
    reason: String,
    limit: Long,
    actual: Long
  ): Consequence.Failure[A] =
    Consequence.operationInvalid(
      "MCP client resource limit exceeded",
      Cause.Kind.Limit,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.limits"),
        Descriptor.Facet.Limit(limit),
        Descriptor.Facet.Actual(actual)
      )
    )

  private def _response_result_c(
    response: McpStreamableHttpResponse,
    requestid: Long
  ): Consequence[Json] =
    _response_messages_c(response).flatMap { messages =>
      messages.find(_.hcursor.get[Long]("id").contains(requestid)) match {
        case Some(message) =>
          if (!message.hcursor.get[String]("jsonrpc").contains("2.0"))
            _protocol_failure("json-rpc-version")
          else
            message.hcursor.downField("error").focus match {
              case Some(_) => _protocol_failure("json-rpc-error")
              case None => message.hcursor.downField("result").focus match {
                case Some(result) => Consequence.success(result)
                case None => _protocol_failure("json-rpc-result-missing")
              }
            }
        case None => _protocol_failure("json-rpc-response-missing")
      }
    }

  private def _response_messages_c(response: McpStreamableHttpResponse): Consequence[Vector[Json]] = {
    val contenttype = response.header("content-type").getOrElse("").toLowerCase(Locale.ROOT)
    val texts =
      if (contenttype.startsWith("text/event-stream"))
        _sse_data(response.body)
      else if (contenttype.startsWith("application/json"))
        Vector(response.body)
      else
        Vector.empty
    if (texts.isEmpty)
      _protocol_failure("response-content-type")
    else
      _traverse(texts) { text =>
        parse(text).fold(_ => _protocol_failure("response-json"), Consequence.success)
      }.map(_.flatMap(x => x.asArray.map(_.toVector).getOrElse(Vector(x))))
  }

  private def _sse_data(body: String): Vector[String] = {
    val events = Vector.newBuilder[String]
    val data = Vector.newBuilder[String]
    def _flush_(): Unit = {
      val values = data.result()
      if (values.nonEmpty)
        events += values.mkString("\n")
      data.clear()
    }
    body.linesIterator.foreach { line =>
      if (line.isEmpty)
        _flush_()
      else if (line.startsWith("data:"))
        data += line.substring(5).stripPrefix(" ")
    }
    _flush_()
    events.result()
  }

  private def _admitted_tool_c(
    server: McpClientServer,
    value: Json
  ): Consequence[Option[McpClientTool]] = {
    val cursor = value.hcursor
    for {
      namevalue <- cursor.get[String]("name").fold(_ => _protocol_failure("tool-name"), Consequence.success)
      name <- McpToolName.parseC(namevalue)
      tool <- if (server.admits(name))
        _tool_c(server.id, name, value).map(Some(_))
      else
        Consequence.success(None)
    } yield tool
  }

  private def _tool_c(
    serverid: McpServerId,
    name: McpToolName,
    value: Json
  ): Consequence[McpClientTool] = {
    val cursor = value.hcursor
    for {
      schemajson <- cursor.downField("inputSchema").focus.map(Consequence.success).getOrElse(_protocol_failure("tool-input-schema"))
      schema <- _schema_c(schemajson)
      title <- _display_text_option_c(cursor.get[String]("title").toOption)
      description <- _display_text_option_c(cursor.get[String]("description").toOption)
      tool <- McpClientTool.createC(McpToolIdentity(serverid, name), schema, title, description)
    } yield tool
  }

  private def _schema_c(value: Json): Consequence[McpInputSchema] = {
    val cursor = value.hcursor
    cursor.get[String]("type").toOption match {
      case Some("null") => Consequence.success(McpInputSchema.NullValue)
      case Some("string") => Consequence.success(McpInputSchema.StringValue)
      case Some("boolean") => Consequence.success(McpInputSchema.BooleanValue)
      case Some("integer") => Consequence.success(McpInputSchema.IntegerValue)
      case Some("number") => Consequence.success(McpInputSchema.NumberValue)
      case Some("array") =>
        cursor.downField("items").focus match {
          case Some(items) => _schema_c(items).map(McpInputSchema.array)
          case None => Consequence.success(McpInputSchema.array(McpInputSchema.AnyValue))
        }
      case Some("object") | None if value.isObject => _object_schema_c(value)
      case Some(_) => Consequence.success(McpInputSchema.AnyValue)
      case None => Consequence.success(McpInputSchema.AnyValue)
    }
  }

  private def _object_schema_c(value: Json): Consequence[McpInputSchema] = {
    val cursor = value.hcursor
    val required = cursor.get[Vector[String]]("required").toOption.getOrElse(Vector.empty).toSet
    val properties = cursor.downField("properties").focus.flatMap(_.asObject).getOrElse(JsonObject.empty)
    _traverse(properties.toVector.sortBy(_._1)) { case (namevalue, schemajson) =>
      for {
        name <- McpFieldName.parseC(namevalue)
        schema <- _schema_c(schemajson)
        description <- _display_text_option_c(schemajson.hcursor.get[String]("description").toOption)
        field <- McpInputField.createC(name, schema, required.contains(namevalue), description)
      } yield field
    }.flatMap { fields =>
      McpInputSchema.objectC(
        fields,
        cursor.get[Boolean]("additionalProperties").toOption.getOrElse(false)
      )
    }
  }

  private def _display_text_option_c(value: Option[String]): Consequence[Option[McpDisplayText]] =
    value match {
      case Some(text) => McpDisplayText.parseC(text).map(Some(_))
      case None => Consequence.success(None)
    }

  private def _call_result_c(result: Json): Consequence[McpClientResult] =
    if (result.hcursor.get[Boolean]("isError").toOption.contains(true))
      Consequence.operationInvalid(
        "remote MCP tool",
        Cause.Kind.Policy,
        Vector(
          Descriptor.Facet.Reason("remote-tool-error"),
          Descriptor.Facet.Policy("mcp-client.remote-tool")
        )
      )
    else {
      result.hcursor.downField("content").focus.flatMap(_.asArray) match {
        case Some(contentvalues) =>
          for {
            content <- _traverse(contentvalues.toVector)(_content_c)
            structured <- result.hcursor.downField("structuredContent").focus match {
              case Some(value) if value.isObject => _json_to_value_c(value).map(Some(_))
              case Some(_) => _protocol_failure("structured-content-object")
              case None => Consequence.success(None)
            }
          } yield McpClientResult(content, structured)
        case None => _protocol_failure("tool-result-content")
      }
    }

  private def _content_c(value: Json): Consequence[McpClientContent] =
    _annotations_c(value.hcursor.downField("annotations").focus).flatMap { annotations =>
      value.hcursor.get[String]("type").toOption match {
        case Some("text") =>
          value.hcursor.get[String]("text").fold(
            _ => _protocol_failure("text-content"),
            x => Consequence.success(McpClientContent.Text(x, annotations))
          )
        case Some("image") => _binary_content_c(value, annotations, isimage = true)
        case Some("audio") => _binary_content_c(value, annotations, isimage = false)
        case Some("resource_link") => _resource_link_c(value, annotations)
        case Some("resource") => _embedded_resource_c(value, annotations)
        case Some("structured") =>
          value.hcursor.downField("value").focus match {
            case Some(x) => _json_to_value_c(x).map(McpClientContent.Structured(_, annotations))
            case None => _protocol_failure("structured-content")
          }
        case _ => _protocol_failure("unsupported-content-type")
      }
    }

  private def _binary_content_c(
    value: Json,
    annotations: Option[McpContentAnnotations],
    isimage: Boolean
  ): Consequence[McpClientContent] =
    for {
      datavalue <- value.hcursor.get[String]("data").fold(_ => _protocol_failure("binary-data"), Consequence.success)
      data <- McpBase64Data.parseC(datavalue)
      mimevalue <- value.hcursor.get[String]("mimeType").fold(_ => _protocol_failure("binary-mime-type"), Consequence.success)
      mime <- McpMimeType.parseC(mimevalue)
    } yield if (isimage)
      McpClientContent.Image(data, mime, annotations)
    else
      McpClientContent.Audio(data, mime, annotations)

  private def _resource_link_c(
    value: Json,
    annotations: Option[McpContentAnnotations]
  ): Consequence[McpClientContent] =
    for {
      urivalue <- value.hcursor.get[String]("uri").fold(_ => _protocol_failure("resource-link-uri"), Consequence.success)
      uri <- McpResourceUri.parseC(urivalue)
      namevalue <- value.hcursor.get[String]("name").fold(_ => _protocol_failure("resource-link-name"), Consequence.success)
      name <- McpDisplayText.parseC(namevalue)
      title <- _display_text_option_c(value.hcursor.get[String]("title").toOption)
      description <- _display_text_option_c(value.hcursor.get[String]("description").toOption)
      mime <- _mime_type_option_c(value.hcursor.get[String]("mimeType").toOption)
      size <- _resource_size_c(value.hcursor.get[Long]("size").toOption)
    } yield McpClientContent.ResourceLink(uri, name, title, description, mime, size, annotations)

  private def _embedded_resource_c(
    value: Json,
    annotations: Option[McpContentAnnotations]
  ): Consequence[McpClientContent] =
    value.hcursor.downField("resource").focus match {
      case Some(resource) =>
        for {
          urivalue <- resource.hcursor.get[String]("uri").fold(_ => _protocol_failure("resource-uri"), Consequence.success)
          uri <- McpResourceUri.parseC(urivalue)
          mime <- _mime_type_option_c(resource.hcursor.get[String]("mimeType").toOption)
          content <- (resource.hcursor.get[String]("text").toOption, resource.hcursor.get[String]("blob").toOption) match {
            case (Some(text), None) => Consequence.success(McpClientContent.EmbeddedTextResource(uri, text, mime, annotations))
            case (None, Some(blobvalue)) =>
              McpBase64Data.parseC(blobvalue).map(McpClientContent.EmbeddedBlobResource(uri, _, mime, annotations))
            case _ => _protocol_failure("resource-content")
          }
        } yield content
      case None => _protocol_failure("embedded-resource")
    }

  private def _annotations_c(value: Option[Json]): Consequence[Option[McpContentAnnotations]] =
    value match {
      case None => Consequence.success(None)
      case Some(json) =>
        val cursor = json.hcursor
        for {
          audience <- _traverse(cursor.get[Vector[String]]("audience").toOption.getOrElse(Vector.empty))(McpContentRole.parseC)
          priority = cursor.get[BigDecimal]("priority").toOption
          lastmodified <- cursor.get[String]("lastModified").toOption match {
            case Some(text) => Try(java.time.Instant.parse(text)).toOption match {
              case Some(instant) => Consequence.success(Some(instant))
              case None => _protocol_failure("annotation-last-modified")
            }
            case None => Consequence.success(None)
          }
          result <- McpContentAnnotations.createC(audience.toSet, priority, lastmodified)
        } yield Some(result)
    }

  private def _mime_type_option_c(value: Option[String]): Consequence[Option[McpMimeType]] =
    value match {
      case Some(text) => McpMimeType.parseC(text).map(Some(_))
      case None => Consequence.success(None)
    }

  private def _resource_size_c(value: Option[Long]): Consequence[Option[Long]] =
    value match {
      case Some(size) if size < 0 => _protocol_failure("resource-size")
      case _ => Consequence.success(value)
    }

  private def _json_to_value_c(value: Json): Consequence[McpValue] =
    value.fold(
      Consequence.success(McpValue.NullValue),
      x => Consequence.success(McpValue.BooleanValue(x)),
      x => x.toBigInt match {
        case Some(number) => Consequence.success(McpValue.IntegerValue(number))
        case None => Consequence.success(McpValue.NumberValue(x.toBigDecimal.getOrElse(BigDecimal(0))))
      },
      x => Consequence.success(McpValue.StringValue(x)),
      xs => _traverse(xs.toVector)(_json_to_value_c).map(McpValue.ArrayValue.apply),
      fields => _traverse(fields.toVector.sortBy(_._1)) { case (namevalue, child) =>
        for {
          name <- McpFieldName.parseC(namevalue)
          result <- _json_to_value_c(child)
        } yield name -> result
      }.flatMap(McpValue.objectC)
    )

  private def _value_to_json(value: McpValue): Json =
    value match {
      case McpValue.NullValue => Json.Null
      case McpValue.StringValue(x) => Json.fromString(x)
      case McpValue.BooleanValue(x) => Json.fromBoolean(x)
      case McpValue.IntegerValue(x) => Json.fromBigInt(x)
      case McpValue.NumberValue(x) => Json.fromBigDecimal(x)
      case McpValue.ArrayValue(xs) => Json.arr(xs.map(_value_to_json)*)
      case McpValue.ObjectValue(fields) => Json.obj(fields.map { case (name, child) => name.print -> _value_to_json(child) }*)
    }

  private def _request(
    id: Long,
    method: String,
    params: Option[Json]
  ): Json =
    Json.obj((Vector(
      "jsonrpc" -> Json.fromString("2.0"),
      "id" -> Json.fromLong(id),
      "method" -> Json.fromString(method)
    ) ++ params.map("params" -> _))* )

  private def _notification(
    method: String,
    params: Option[Json]
  ): Json =
    Json.obj((Vector(
      "jsonrpc" -> Json.fromString("2.0"),
      "method" -> Json.fromString(method)
    ) ++ params.map("params" -> _))* )

  private def _headers(
    sessionid: Option[_SessionId],
    protocolversion: Option[McpProtocolRevision],
    accept: String
  ): Map[String, String] =
    Map(
      "Accept" -> accept,
      "Content-Type" -> "application/json"
    ) ++ sessionid.map(x => "Mcp-Session-Id" -> x.value) ++ protocolversion.map(x => "MCP-Protocol-Version" -> x.print)

  private def _session_id_option_c(value: Option[String]): Consequence[Option[_SessionId]] =
    value match {
      case Some(text) if text.nonEmpty && text.forall(x => x >= 0x21 && x <= 0x7e) =>
        Consequence.success(Some(_SessionId(text)))
      case Some(_) => _protocol_failure("session-id")
      case None => Consequence.success(None)
    }

  private def _config_c(server: McpClientServer): Consequence[McpStreamableHttpServerConfig] =
    _configs.get(server.id) match {
      case Some(value) => Consequence.success(value)
      case None => _transport_policy_failure("server-not-configured")
    }

  private def _session_c(server: McpClientServer): Consequence[_Session] =
    Option(_sessions.get(server.id)) match {
      case Some(value) => Consequence.success(value)
      case None => _protocol_failure("server-not-initialized")
    }

  private def _transport_status_failure[A](status: Int): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP request failed",
      Cause.Kind.Unknown,
      Vector(
        Descriptor.Facet.Reason("http-status"),
        Descriptor.Facet.Policy("mcp-client.streamable-http"),
        Descriptor.Facet.Value(status)
      )
    )

  private def _transport_policy_failure[A](reason: String): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      "MCP Streamable HTTP transport policy rejected the request",
      Cause.Kind.Policy,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.streamable-http")
      )
    )

  private def _protocol_failure[A](reason: String): Consequence.Failure[A] =
    Consequence.operationInvalid(
      "MCP protocol response",
      Cause.Kind.Inconsistency,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy("mcp-client.protocol")
      )
    )

  private def _traverse[A, B](
    values: Vector[A]
  )(f: A => Consequence[B]): Consequence[Vector[B]] =
    values.foldLeft(Consequence.success(Vector.empty[B])) { (z, value) =>
      for {
        xs <- z
        x <- f(value)
      } yield xs :+ x
    }
}
