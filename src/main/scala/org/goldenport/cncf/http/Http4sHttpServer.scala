package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import java.util.UUID
import scala.collection.concurrent.TrieMap
import fs2.Pipe
import fs2.Stream
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.{HttpRoutes, MediaType, Response as HResponse, Status as HStatus}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.Charset
import org.http4s.syntax.all.*
import org.http4s.dsl.io.*
import org.http4s.multipart.Multipart
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.goldenport.record.Record
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.mcp.McpJsonRpcAdapter
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 21, 2026
 *  version Mar. 29, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class Http4sHttpServer(
  engine: HttpExecutionEngine,
  port: Int = Http4sHttpServer.defaultPort
) extends HttpServer(engine) {
  private val _bind_host = Host.fromString("0.0.0.0").get
  private val _form_continuations = TrieMap.empty[String, Http4sHttpServer.FormContinuation]

  def start(args: Array[String] = Array.empty): Unit = {
    val _ = args
    _server().unsafeRunSync()
  }

  private def _server(): IO[Unit] = {
    val scope = ScopeContext(
      kind = ScopeKind.Subsystem,
      name = "cncf",
      parent = None,
      observabilityContext = ExecutionContext.create().observability
    )
    scope.observe_infoC(
      message = "started",
      attributes = Record.create(
        Vector(
          "kind" -> scope.kind.toString,
          "name" -> scope.name
        )
      )
    )
    val mcp = new McpJsonRpcAdapter(engine.runtimeSubsystem)
    def routes(wsb: WebSocketBuilder2[IO]) = HttpRoutes.of[IO] {
      case GET -> Root / "mcp" =>
        _mcp_websocket(wsb, mcp)
      case GET -> Root / "web" / "assets" / "bootstrap.min.css" =>
        _bootstrap_css()
      case GET -> Root / "web" / "assets" / "bootstrap.bundle.min.js" =>
        _bootstrap_bundle_js()
      case GET -> Root / "web" / "system" / "dashboard" =>
        _subsystem_dashboard()
      case GET -> Root / "web" / "system" / "dashboard" / "state" =>
        _dashboard_state(None)
      case GET -> Root / "web" / "system" / "performance" =>
        _system_performance()
      case GET -> Root / "web" / "system" / "admin" =>
        _system_admin()
      case GET -> Root / "web" / "system" / "admin" / "descriptor" =>
        _system_admin_descriptor()
      case GET -> Root / "web" / app / "dashboard" / "state" =>
        _dashboard_state(Some(app))
      case GET -> Root / "web" / app / "admin" =>
        _component_admin(app)
      case GET -> Root / "web" / app / "admin" / "entities" =>
        _component_admin_entities(app)
      case GET -> Root / "web" / app / "admin" / "data" =>
        _component_admin_data(app)
      case GET -> Root / "web" / app / "admin" / "aggregates" =>
        _component_admin_aggregates(app)
      case GET -> Root / "web" / app / "admin" / "views" =>
        _component_admin_views(app)
      case GET -> Root / "web" / app =>
        _static_form_app(app, Vector.empty)
      case GET -> Root / "web" / app / page =>
        _static_form_app(app, Vector(page))
      case GET -> Root / "form" / app =>
        _form_index(app)
      case req @ GET -> Root / "form" / app / service / operation =>
        _operation_form(req, app, service, operation)
      case req @ GET -> Root / "form" / app / service / operation / "result" =>
        _operation_form_result(req, app, service, operation)
      case req @ GET -> Root / "form" / app / service / operation / "continue" / id =>
        _operation_form_continue(req, app, service, operation, id)
      case req @ POST -> Root / "form" / app / service / operation =>
        _submit_operation_form(req, app, service, operation)
      case req =>
        try {
          val started = System.nanoTime()
          for {
            core <- _to_http_request(req)
            res <- _to_http_response(execute(core))
          } yield {
            RuntimeDashboardMetrics.recordHtmlRequest(
              req.method.name,
              req.uri.path.renderString,
              res.status.code,
              (System.nanoTime() - started) / 1000000L
            )
            res
          }
        } catch {
          case e: Throwable =>
            e.printStackTrace(Console.err)
            RuntimeDashboardMetrics.recordHtmlRequest(
              req.method.name,
              req.uri.path.renderString,
              HStatus.InternalServerError.code,
              0L
            )
            IO.pure(HResponse[IO](HStatus.InternalServerError))
        }
    }
    EmberServerBuilder
      .default[IO]
      .withHost(_bind_host)
      .withPort(Port.fromInt(port).get)
      .withHttpWebSocketApp(wsb => routes(wsb).orNotFound)
      .build
      .use { _ =>
        // Block forever to keep server mode alive.
        IO.never
      }
  }

  private def _mcp_websocket(
    wsb: WebSocketBuilder2[IO],
    adapter: McpJsonRpcAdapter
  ): IO[HResponse[IO]] =
    Queue.unbounded[IO, WebSocketFrame].flatMap { queue =>
      val send = Stream.repeatEval(queue.take)
      val receive: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
        case WebSocketFrame.Text(text, _) =>
          queue.offer(WebSocketFrame.Text(adapter.handle(text)))
        case _ =>
          IO.unit
      }
      wsb.build(send, receive)
    }

  private def _bootstrap_css(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.bootstrapCss)
        .withContentType(`Content-Type`(MediaType.text.css, Some(Charset.`UTF-8`)))
    )

  private def _bootstrap_bundle_js(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.bootstrapBundleJs)
        .withContentType(`Content-Type`(MediaType.application.javascript, Some(Charset.`UTF-8`)))
    )

  private def _subsystem_dashboard(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSubsystemDashboard(engine.runtimeSubsystem)
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )
  }

  private def _dashboard_state(
    componentName: Option[String]
  ): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderDashboardState(engine.runtimeSubsystem, componentName) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("""{"error":{"code":"NOT_FOUND","message":"Dashboard target not found"}}"""))
    }

  private def _system_performance(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSystemPerformance(engine.runtimeSubsystem)
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )
  }

  private def _system_admin(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSystemAdmin(engine.runtimeSubsystem, engine.webDescriptor)
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )
  }

  private def _system_admin_descriptor(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSystemAdminDescriptor(engine.webDescriptor)
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )
  }

  private def _component_admin(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdmin(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component admin not found"))
    }

  private def _component_admin_entities(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntities(engine.runtimeSubsystem, app) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity admin not found"))
    }

  private def _component_admin_data(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminData(engine.runtimeSubsystem, app) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data admin not found"))
    }

  private def _component_admin_views(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViews(engine.runtimeSubsystem, app) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view admin not found"))
    }

  private def _component_admin_aggregates(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregates(engine.runtimeSubsystem, app) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate admin not found"))
    }

  private def _static_form_app(
    app: String,
    page: Vector[String]
  ): IO[HResponse[IO]] =
    StaticFormAppRenderer.render(engine.runtimeSubsystem, app, page, engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Static Form App not found"))
    }

  private def _form_index(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderFormIndex(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) =>
        _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Form App not found"))
    }

  private def _operation_form(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, service, operation, Some(req)))
      _forbidden()
    else StaticFormAppRenderer.renderOperationForm(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor) match {
      case Some(p) =>
        _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    }

  private def _submit_operation_form(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden()
    } else {
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      res = execute(HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = s"/${app}/${service}/${operation}",
        query = Record.create(req.uri.query.params.toVector),
        header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value)),
        form = form
      ))
      continuation = _create_form_continuation(app, service, operation, form, res, _form_chunk_size(form))
      properties = _form_result_properties(app, service, operation, res, _form_values(form) ++ _continuation_values(continuation))
      page = StaticFormAppRenderer.renderFormResult(properties)
      html <- _html(page)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        res.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private def _operation_form_continue(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Form continuation not found"))
    else if (!_is_web_authorized(app, service, operation, Some(req)))
      _forbidden()
    else
    _form_continuations.get(id) match {
      case Some(continuation) if continuation.matches(app, service, operation) =>
        val started = System.nanoTime()
        val queryValues = req.uri.query.params.toMap
        val pagingValues = queryValues.filter { case (k, _) => k == "page" || k == "pageSize" || k == "requireTotal" }
        val res = continuation.response
        val values = _continuation_values(continuation) ++ pagingValues.map { case (k, v) => s"paging.${k}" -> v }
        val page = StaticFormAppRenderer.renderFormResult(
          _form_result_properties(app, service, operation, res, values)
        )
        _html(page).map { html =>
          RuntimeDashboardMetrics.recordHtmlRequest(
            req.method.name,
            req.uri.path.renderString,
            res.code,
            (System.nanoTime() - started) / 1000000L
          )
          html
        }
      case _ =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Form continuation not found"))
    }

  private def _operation_form_result(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden()
    } else {
    val started = System.nanoTime()
    val query = Record.create(req.uri.query.params.toVector)
    val values = req.uri.query.params.toMap
    val res = execute(HttpRequest.fromPath(
      method = HttpRequest.GET,
      path = s"/${app}/${service}/${operation}",
      query = query,
      header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value))
    ))
    val page = StaticFormAppRenderer.renderFormResult(
      _form_result_properties(app, service, operation, res, values)
    )
    _html(page).map { html =>
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        res.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private def _form_result_properties(
    app: String,
    service: String,
    operation: String,
    response: HttpResponse,
    values: Map[String, String]
  ): StaticFormAppRenderer.FormResultProperties =
    StaticFormAppRenderer.FormResultProperties(
      StaticFormAppRenderer.FormPageProperties(app, service, operation, values),
      response.code,
      response.mime.value,
      response.getString.getOrElse("")
    )

  private def _is_form_enabled(
    app: String,
    service: String,
    operation: String
  ): Boolean =
    engine.webDescriptor.isFormEnabled(Vector(app, service, operation).mkString("."))

  private def _is_web_authorized(
    app: String,
    service: String,
    operation: String,
    req: Option[org.http4s.Request[IO]]
  ): Boolean = {
    val selector = Vector(app, service, operation).mkString(".")
    if (!engine.webDescriptor.authorization.contains(selector)) {
      true
    } else {
      val allowed = WebDescriptorAuthorization.isAllowed(
        engine.webDescriptor,
        selector,
        _web_authorization_subject(req)
      )
      RuntimeDashboardMetrics.recordAuthorizationDecision(!allowed)
      allowed
    }
  }

  private def _web_authorization_subject(
    req: Option[org.http4s.Request[IO]]
  ): WebDescriptorAuthorization.Subject = {
    val headerValues = req.toVector.flatMap(_.headers.headers.map(h => h.name.toString -> h.value))
    val queryValues = req.toVector.flatMap(_.uri.query.params.toVector)
    def tokens(keys: String*): Set[String] = {
      val normalizedKeys = keys.map(_.toLowerCase).toSet
      (headerValues ++ queryValues)
        .collect {
          case (key, value) if normalizedKeys.contains(key.toLowerCase) => value
        }
        .flatMap(_split_tokens)
        .toSet
    }
    WebDescriptorAuthorization.Subject(
      roles = tokens("role", "roles", "x-cncf-role", "x-cncf-roles", "x-textus-role", "x-textus-roles"),
      scopes = tokens("scope", "scopes", "x-cncf-scope", "x-cncf-scopes", "x-textus-scope", "x-textus-scopes"),
      capabilities = tokens("capability", "capabilities", "x-cncf-capability", "x-cncf-capabilities", "x-textus-capability", "x-textus-capabilities")
    )
  }

  private def _split_tokens(value: String): Vector[String] =
    value.split("[,\\s|]+").toVector.map(_.trim).filter(_.nonEmpty)

  private def _forbidden(): IO[HResponse[IO]] =
    IO.pure(HResponse[IO](HStatus.Forbidden).withEntity("Forbidden"))

  private def _create_form_continuation(
    app: String,
    service: String,
    operation: String,
    form: Record,
    response: HttpResponse,
    chunkSize: Int
  ): Http4sHttpServer.FormContinuation = {
    val id = UUID.randomUUID().toString
    val continuation = Http4sHttpServer.FormContinuation(id, app, service, operation, form, response, chunkSize)
    _form_continuations.update(id, continuation)
    continuation
  }

  private def _continuation_values(
    continuation: Http4sHttpServer.FormContinuation
  ): Map[String, String] =
    Map(
      "form.continuation" -> continuation.id,
      "paging.chunkSize" -> continuation.chunkSize.toString,
      "paging.href" -> s"/form/${continuation.app}/${continuation.service}/${continuation.operation}/continue/${continuation.id}?page={page}&pageSize={pageSize}"
    )

  private def _form_chunk_size(form: Record): Int =
    form.getString("paging.chunkSize").flatMap(_.toIntOption).getOrElse(1000)

  private def _form_values(form: Record): Map[String, String] =
    form.asMap.map { case (k, v) => k -> v.toString }

  private def _html(p: StaticFormAppRenderer.Page): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )

  private def _to_plain_form_record(
    req: org.http4s.Request[IO]
  ): IO[Record] =
    req.body.compile.to(Array).map { bytes =>
      val text = new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)
      val base =
        if (text.trim.isEmpty) Record.empty
        else HttpRequest.parseQuery(text)
      base.getString("fields").map(_fields_to_record).getOrElse(base)
    }

  private def _fields_to_record(text: String): Record = {
    val trimmedText = text.trim
    if (!trimmedText.contains("\n") && trimmedText.contains("&")) {
      HttpRequest.parseQuery(trimmedText)
    } else {
      val pairs = text.linesIterator.toVector.flatMap { line =>
        val trimmed = line.trim
        if (trimmed.isEmpty) {
          None
        } else {
          val i = trimmed.indexOf("=")
          if (i < 0) {
            Some(trimmed -> "")
          } else {
            Some(trimmed.take(i).trim -> trimmed.drop(i + 1).trim)
          }
        }
      }
      if (pairs.isEmpty) Record.empty else Record.create(pairs)
    }
  }

  private def _to_http_request(
    req: org.http4s.Request[IO]
  ): IO[HttpRequest] = {
    val method = req.method match {
      case org.http4s.Method.GET => HttpRequest.GET
      case org.http4s.Method.POST => HttpRequest.POST
      case org.http4s.Method.PUT => HttpRequest.PUT
      case org.http4s.Method.DELETE => HttpRequest.DELETE
      case _ => HttpRequest.GET
    }
    val query = Record.create(req.uri.query.params.toVector)
    val header = Record.create(
      req.headers.headers.map(h => h.name.toString -> h.value)
    )
    val context = HttpContext(
      scheme = req.uri.scheme.map(_.value),
      authority = req.uri.authority.map(_.renderString),
      originalUri = Some(req.uri.renderString)
    )
    val contentTypeHeader = req.headers.get[`Content-Type`]

    if (_is_multipart(contentTypeHeader))
      _to_multipart_http_request(req, method, query, header, context)
    else
      _to_regular_http_request(req, method, query, header, context, contentTypeHeader)
  }

  private def _is_multipart(contentType: Option[`Content-Type`]): Boolean =
    contentType.exists { header =>
      header.mediaType.mainType.equalsIgnoreCase("multipart") &&
        header.mediaType.subType.equalsIgnoreCase("form-data")
    }

  private def _to_regular_http_request(
    req: org.http4s.Request[IO],
    method: HttpRequest.Method,
    query: Record,
    header: Record,
    context: HttpContext,
    contentTypeHeader: Option[`Content-Type`]
  ): IO[HttpRequest] =
    req.body.compile.to(Array).map { bytes =>
      val isFormUrlEncoded = contentTypeHeader.exists { header =>
        header.mediaType.mainType.equalsIgnoreCase("application") &&
          header.mediaType.subType.equalsIgnoreCase("x-www-form-urlencoded")
      }
      val (bodyOption, formRecord) =
        if (bytes.isEmpty) {
          (None, Record.empty)
        } else if (isFormUrlEncoded) {
          val text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
          (None, HttpRequest.parseQuery(text))
        } else {
          (Some(Bag.binary(bytes.toArray)), Record.empty)
        }
      HttpRequest.fromPath(
        method = method,
        path = req.uri.path.renderString,
        query = query,
        header = header,
        body = bodyOption,
        context = context,
        form = formRecord
      )
    }

  private def _to_multipart_http_request(
    req: org.http4s.Request[IO],
    method: HttpRequest.Method,
    query: Record,
    header: Record,
    context: HttpContext
  ): IO[HttpRequest] =
    req.as[Multipart[IO]].flatMap { multipart =>
      multipart.parts.toVector.traverse { part =>
        part.name match {
          case Some(name) if name.nonEmpty =>
            part.body.compile.to(Array).map { bytes =>
              val bag = Bag.binary(bytes.toArray)
              val contentType =
                part.headers
                  .get[`Content-Type`]
                  .map(_.value)
                  .map(ContentType.parse)
                  .getOrElse(ContentType.APPLICATION_OCTET_STREAM)
              Some(name -> MimeBody(contentType, bag))
            }
          case _ =>
            IO.pure(None)
        }
      }.map { entries =>
        val values = entries.flatten
        val form =
          if (values.isEmpty) Record.empty else Record.create(values)
        HttpRequest.fromPath(
          method = method,
          path = req.uri.path.renderString,
          query = query,
          header = header,
          body = None,
          context = context,
          form = form
        )
      }
    }

  private def _to_http_response(
    res: HttpResponse
  ): IO[org.http4s.Response[IO]] = {
    val status = res.code match {
      case 200 => HStatus.Ok
      case 400 => HStatus.BadRequest
      case 401 => HStatus.Unauthorized
      case 403 => HStatus.Forbidden
      case 404 => HStatus.NotFound
      case _ => HStatus.InternalServerError
    }
    val body = res.getString.getOrElse("")
    val mime = MediaType.parse(res.mime.value).fold(_ => MediaType.text.plain, identity)
    val charset: Option[org.http4s.Charset] =
      res.charset.map(c => org.http4s.Charset.fromNioCharset(c))
    val contentType = `Content-Type`(mime, charset)
    IO.pure(
      HResponse[IO](status).withEntity(body).withContentType(contentType)
    )
  }

}

object Http4sHttpServer {
  val PortPropertyKey = "cncf.server.port"

  final case class FormContinuation(
    id: String,
    app: String,
    service: String,
    operation: String,
    form: Record,
    response: HttpResponse,
    chunkSize: Int
  ) {
    def matches(
      app: String,
      service: String,
      operation: String
    ): Boolean =
      this.app == app && this.service == service && this.operation == operation
  }

  def defaultPort: Int =
    sys.props
      .get(PortPropertyKey)
      .flatMap(x => scala.util.Try(x.toInt).toOption)
      .getOrElse(8080)

  def create(): Http4sHttpServer =
    new Http4sHttpServer(
      HttpExecutionEngine.Factory.engine()
    )
}
