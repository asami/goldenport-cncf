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
import org.http4s.{HttpRoutes, MediaType, Request as HRequest, Response as HResponse, Status as HStatus, Uri}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{`Content-Type`, Location}
import org.http4s.Charset
import org.http4s.syntax.all.*
import org.http4s.dsl.io.*
import org.http4s.multipart.Multipart
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.goldenport.record.Record
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.cncf.mcp.McpJsonRpcAdapter
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 21, 2026
 *  version Mar. 29, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class Http4sHttpServer(
  engine: HttpExecutionEngine,
  port: Int = Http4sHttpServer.defaultPort,
  operationDispatcherOption: Option[WebOperationDispatcher] = None
) extends HttpServer(engine) {
  private val _bind_host = Host.fromString("0.0.0.0").get
  private val _form_continuations = TrieMap.empty[String, Http4sHttpServer.FormContinuation]
  private val _operation_dispatcher =
    operationDispatcherOption.getOrElse(WebOperationDispatcher.create(engine))

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
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity =>
        _component_admin_entity_type(req, app, entity)
      case GET -> Root / "web" / app / "admin" / "entities" / entity / "new" =>
        _component_admin_entity_new(app, entity)
      case GET -> Root / "web" / app / "admin" / "entities" / entity / id / "edit" =>
        _component_admin_entity_edit(app, entity, id)
      case GET -> Root / "web" / app / "admin" / "entities" / entity / id =>
        _component_admin_entity_detail(app, entity, id)
      case GET -> Root / "web" / app / "admin" / "data" =>
        _component_admin_data(app)
      case req @ GET -> Root / "web" / app / "admin" / "data" / data =>
        _component_admin_data_type(req, app, data)
      case GET -> Root / "web" / app / "admin" / "data" / data / "new" =>
        _component_admin_data_new(app, data)
      case GET -> Root / "web" / app / "admin" / "data" / data / id / "edit" =>
        _component_admin_data_edit(app, data, id)
      case GET -> Root / "web" / app / "admin" / "data" / data / id =>
        _component_admin_data_detail(app, data, id)
      case GET -> Root / "web" / app / "admin" / "aggregates" =>
        _component_admin_aggregates(app)
      case GET -> Root / "web" / app / "admin" / "aggregates" / aggregate / id =>
        _component_admin_aggregate_instance_detail(app, aggregate, id)
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" / aggregate =>
        _component_admin_aggregate_detail(req, app, aggregate)
      case GET -> Root / "web" / app / "admin" / "views" =>
        _component_admin_views(app)
      case GET -> Root / "web" / app / "admin" / "views" / view / id =>
        _component_admin_view_instance_detail(app, view, id)
      case req @ GET -> Root / "web" / app / "admin" / "views" / view =>
        _component_admin_view_detail(req, app, view)
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
      case req @ POST -> Root / "form" / app / "admin" / "entities" / entity / "create" =>
        _submit_component_admin_entity_create(req, app, entity)
      case req @ POST -> Root / "form" / app / "admin" / "entities" / entity / id / "update" =>
        _submit_component_admin_entity_update(req, app, entity, id)
      case req @ POST -> Root / "form" / app / "admin" / "data" / data / "create" =>
        _submit_component_admin_data_create(req, app, data)
      case req @ POST -> Root / "form" / app / "admin" / "data" / data / id / "update" =>
        _submit_component_admin_data_update(req, app, data, id)
      case req @ POST -> Root / "form" / app / service / operation =>
        _submit_operation_form(req, app, service, operation)
      case req @ POST -> Root / "form-api" / app / service / operation =>
        _submit_operation_form_api(req, app, service, operation)
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

  private def _component_admin_entity_type(req: HRequest[IO], app: String, entity: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityType(engine.runtimeSubsystem, app, entity, _page_request(req), engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity type admin not found"))
    }

  private def _component_admin_entity_detail(app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityDetail(engine.runtimeSubsystem, app, entity, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity detail admin not found"))
    }

  private def _component_admin_entity_edit(app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityEdit(engine.runtimeSubsystem, app, entity, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity edit admin not found"))
    }

  private def _component_admin_entity_new(app: String, entity: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityNew(engine.runtimeSubsystem, app, entity) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity new admin not found"))
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

  private def _component_admin_data_type(req: HRequest[IO], app: String, data: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataType(engine.runtimeSubsystem, app, data, _page_request(req), engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data type admin not found"))
    }

  private def _component_admin_data_detail(app: String, data: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataDetail(engine.runtimeSubsystem, app, data, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data detail admin not found"))
    }

  private def _component_admin_data_edit(app: String, data: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataEdit(engine.runtimeSubsystem, app, data, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data edit admin not found"))
    }

  private def _component_admin_data_new(app: String, data: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataNew(engine.runtimeSubsystem, app, data) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data new admin not found"))
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

  private def _component_admin_view_detail(req: HRequest[IO], app: String, view: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViewDetail(engine.runtimeSubsystem, app, view, _page_request(req), engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view detail admin not found"))
    }

  private def _component_admin_view_instance_detail(app: String, view: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViewInstanceDetail(engine.runtimeSubsystem, app, view, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view instance detail admin not found"))
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

  private def _component_admin_aggregate_detail(req: HRequest[IO], app: String, aggregate: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregateDetail(engine.runtimeSubsystem, app, aggregate, _page_request(req), engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate detail admin not found"))
    }

  private def _component_admin_aggregate_instance_detail(app: String, aggregate: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregateInstanceDetail(engine.runtimeSubsystem, app, aggregate, id) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate instance detail admin not found"))
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
    else StaticFormAppRenderer.renderOperationForm(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) =>
        _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    }

  private[http] def _submit_operation_form(
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
      res = _dispatch_operation(
        app,
        service,
        operation,
        HttpRequest.fromPath(
          method = HttpRequest.POST,
          path = s"/${app}/${service}/${operation}",
          query = Record.create(req.uri.query.params.toVector),
          header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value)),
          form = form
        )
      )
      continuation = _create_form_continuation(app, service, operation, form, res, _form_chunk_size(form))
      properties = _form_result_properties(app, service, operation, res, _form_values(form) ++ _continuation_values(continuation))
      response <- _form_transition_response(app, service, operation, form, res, properties)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        res.code,
        (System.nanoTime() - started) / 1000000L
      )
      response
    }
  }

  private[http] def _submit_operation_form_api(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form API not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden()
    } else {
      val started = System.nanoTime()
      for {
        form <- _to_plain_form_record(req)
        res = _dispatch_operation(
          app,
          service,
          operation,
          HttpRequest.fromPath(
            method = HttpRequest.POST,
            path = s"/${app}/${service}/${operation}",
            query = Record.create(req.uri.query.params.toVector),
            header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value)),
            form = form
          )
        )
        out <- _to_http_response(res)
      } yield {
        RuntimeDashboardMetrics.recordHtmlRequest(
          req.method.name,
          req.uri.path.renderString,
          res.code,
          (System.nanoTime() - started) / 1000000L
        )
        out
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
        val pagingValues = queryValues.filter { case (k, _) => k == "page" || k == "pageSize" || k == "includeTotal" }
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

  private def _dispatch_operation(
    app: String,
    service: String,
    operation: String,
    request: HttpRequest
  ): HttpResponse = {
    given ExecutionContext = ExecutionContext.create()
    val context = DslChokepointContext(
      domain = "web",
      operation = "operation.dispatch",
      componentName = Some(app),
      resourceName = Some(s"${service}.${operation}"),
      commandName = Some(operation),
      attributes = Record.dataAuto(
        "web.service" -> service,
        "web.operation" -> operation,
        "web.dispatch.target" -> _operation_dispatcher.targetName
      )
    )
    DslChokepointRunner.run(context) {
      DslChokepointRunner.phase(context, DslChokepointPhase.Method) {
        org.goldenport.Consequence.success(_operation_dispatcher.dispatch(request))
      }
    } match {
      case org.goldenport.Consequence.Success(response) =>
        response
      case org.goldenport.Consequence.Failure(conclusion) =>
        HttpResponse.internalServerError(conclusion.toString)
    }
  }

  private[http] def _submit_component_admin_entity_update(
    req: org.http4s.Request[IO],
    app: String,
    entity: String,
    id: String
  ): IO[HResponse[IO]] = {
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      record = Record.create((form.asMap + ("id" -> id)).toVector)
      result = _dispatch_component_admin_entity_record("update", app, entity, record)
      page = StaticFormAppRenderer.renderComponentAdminEntityUpdateResult(app, entity, id, _form_values(record), result._1, result._2)
      html <- _admin_form_transition_response(app, "entities", entity, "update", record, result._1, result._2, page)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        html.status.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private[http] def _submit_component_admin_entity_create(
    req: org.http4s.Request[IO],
    app: String,
    entity: String
  ): IO[HResponse[IO]] = {
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      result = _dispatch_component_admin_entity_record("create", app, entity, form)
      page = StaticFormAppRenderer.renderComponentAdminEntityCreateResult(app, entity, _form_values(form), result._1, result._2)
      html <- _admin_form_transition_response(app, "entities", entity, "create", form, result._1, result._2, page)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        html.status.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private[http] def _submit_component_admin_data_update(
    req: org.http4s.Request[IO],
    app: String,
    data: String,
    id: String
  ): IO[HResponse[IO]] = {
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      record = Record.create((form.asMap + ("id" -> id)).toVector)
      result = _dispatch_component_admin_data_record("update", app, data, record)
      page = StaticFormAppRenderer.renderComponentAdminDataUpdateResult(app, data, id, _form_values(record), result._1, result._2)
      html <- _admin_form_transition_response(app, "data", data, "update", record, result._1, result._2, page)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        html.status.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private[http] def _submit_component_admin_data_create(
    req: org.http4s.Request[IO],
    app: String,
    data: String
  ): IO[HResponse[IO]] = {
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      result = _dispatch_component_admin_data_record("create", app, data, form)
      page = StaticFormAppRenderer.renderComponentAdminDataCreateResult(app, data, _form_values(form), result._1, result._2)
      html <- _admin_form_transition_response(app, "data", data, "create", form, result._1, result._2, page)
    } yield {
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        html.status.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private def _dispatch_component_admin_data_record(
    operation: String,
    app: String,
    data: String,
    record: Record
  ): (Boolean, String) = {
    val response = _dispatch_operation(
      "admin",
      "data",
      operation,
      HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = s"/admin/data/${operation}",
        form = Record.create((record.asMap + ("component" -> app) + ("data" -> data)).toVector)
      )
    )
    val applied = response.code >= 200 && response.code < 300
    val message = response.getString.getOrElse {
      if (applied) "Data record was applied." else s"HTTP ${response.code}"
    }
    (applied, message)
  }

  private def _dispatch_component_admin_entity_record(
    operation: String,
    app: String,
    entity: String,
    record: Record
  ): (Boolean, String) = {
    val response = _dispatch_operation(
      "admin",
      "entity",
      operation,
      HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = s"/admin/entity/${operation}",
        form = Record.create((record.asMap + ("component" -> app) + ("entity" -> entity)).toVector)
      )
    )
    val applied = response.code >= 200 && response.code < 300
    val message = response.getString.getOrElse {
      if (applied) "Entity record was applied." else s"HTTP ${response.code}"
    }
    (applied, message)
  }

  private def _admin_form_transition_response(
    app: String,
    surface: String,
    collection: String,
    operation: String,
    form: Record,
    applied: Boolean,
    message: String,
    fallback: StaticFormAppRenderer.Page
  ): IO[HResponse[IO]] = {
    val descriptor = _admin_form_descriptor(app, surface, collection, operation)
    val redirect =
      if (applied)
        descriptor.flatMap(_.successRedirect)
      else if (descriptor.exists(_.stayOnError))
        None
      else
        descriptor.flatMap(_.failureRedirect)
    redirect match {
      case Some(template) =>
        IO.pure(_see_other(_render_admin_redirect_template(template, app, surface, collection, operation, form, applied, message)))
      case None =>
        if (!applied && descriptor.exists(_.stayOnError))
          _admin_stay_on_error_response(app, surface, collection, operation, form, message, fallback)
        else
          _html(fallback)
    }
  }

  private def _admin_stay_on_error_response(
    app: String,
    surface: String,
    collection: String,
    operation: String,
    form: Record,
    message: String,
    fallback: StaticFormAppRenderer.Page
  ): IO[HResponse[IO]] = {
    val values = _form_values(form) ++ Map(
      "error.status" -> "400",
      "error.body" -> message
    )
    val page =
      (surface, operation, form.getString("id")) match {
        case ("entities", "update", Some(id)) =>
          StaticFormAppRenderer.renderComponentAdminEntityEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values
          )
        case ("entities", "create", _) =>
          StaticFormAppRenderer.renderComponentAdminEntityNew(
            engine.runtimeSubsystem,
            app,
            collection,
            values
          )
        case ("data", "update", Some(id)) =>
          StaticFormAppRenderer.renderComponentAdminDataEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values
          )
        case ("data", "create", _) =>
          StaticFormAppRenderer.renderComponentAdminDataNew(
            engine.runtimeSubsystem,
            app,
            collection,
            values
          )
        case _ =>
          Some(fallback)
      }
    _html(page.getOrElse(fallback))
  }

  private def _admin_form_descriptor(
    app: String,
    surface: String,
    collection: String,
    operation: String
  ): Option[WebDescriptor.Form] =
    engine.webDescriptor.form.get(Vector(app, "admin", surface, collection, operation).mkString("."))

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
    val res = _dispatch_operation(
      app,
      service,
      operation,
      HttpRequest.fromPath(
        method = HttpRequest.GET,
        path = s"/${app}/${service}/${operation}",
        query = query,
        header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value))
      )
    )
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

  private def _form_transition_response(
    app: String,
    service: String,
    operation: String,
    form: Record,
    response: HttpResponse,
    properties: StaticFormAppRenderer.FormResultProperties
  ): IO[HResponse[IO]] = {
    val descriptor = _form_descriptor(app, service, operation)
    val ok = response.code >= 200 && response.code < 400
    val redirect =
      if (ok)
        descriptor.flatMap(_.successRedirect)
      else if (descriptor.exists(_.stayOnError))
        None
      else
        descriptor.flatMap(_.failureRedirect)
    redirect match {
      case Some(template) =>
        IO.pure(_see_other(_render_redirect_template(template, app, service, operation, form, response)))
      case None =>
        if (!ok && descriptor.exists(_.stayOnError))
          _html(StaticFormAppRenderer.renderOperationForm(
            engine.runtimeSubsystem,
            app,
            service,
            operation,
            engine.webDescriptor,
            _form_values(form) ++ _error_values(response)
          ).getOrElse(StaticFormAppRenderer.renderFormResult(properties)))
        else
          _html(StaticFormAppRenderer.renderFormResult(properties))
    }
  }

  private def _form_descriptor(
    app: String,
    service: String,
    operation: String
  ): Option[WebDescriptor.Form] =
    engine.webDescriptor.form.get(Vector(app, service, operation).mkString("."))

  private def _render_redirect_template(
    template: String,
    app: String,
    service: String,
    operation: String,
    form: Record,
    response: HttpResponse
  ): String = {
    val values =
      _form_values(form) ++ Map(
        "component" -> app,
        "service" -> service,
        "operation" -> operation,
        "result.status" -> response.code.toString,
        "result.body" -> response.getString.getOrElse("")
      ) ++ _result_values(response)
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(values.getOrElse(m.group(1), ""))
    )
  }

  private def _error_values(response: HttpResponse): Map[String, String] =
    Map(
      "error.status" -> response.code.toString,
      "error.body" -> response.getString.getOrElse("")
    )

  private def _result_values(response: HttpResponse): Map[String, String] = {
    FormResultMetadata.fromHttpResponse(response).toTemplateValues
  }

  private def _render_admin_redirect_template(
    template: String,
    app: String,
    surface: String,
    collection: String,
    operation: String,
    form: Record,
    applied: Boolean,
    message: String
  ): String = {
    val id = form.getString("id")
    val resultId = id.orElse(FormResultMetadata.fromBody(message).id)
    val values =
      _form_values(form) ++ Map(
        "component" -> app,
        "surface" -> surface,
        "collection" -> collection,
        "service" -> s"admin.${surface}.${collection}",
        "operation" -> operation,
        "result.status" -> (if (applied) "200" else "400"),
        "result.body" -> message
      ) ++ id.map("id" -> _).toMap ++ resultId.map("result.id" -> _).toMap
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(values.getOrElse(m.group(1), ""))
    )
  }

  private def _see_other(path: String): HResponse[IO] =
    HResponse[IO](HStatus.SeeOther).putHeaders(Location(Uri.unsafeFromString(path)))

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
      base.getString("fields") match {
        case Some(fields) =>
          Record.create(base.asMap.toVector.filterNot(_._1 == "fields") ++ _fields_to_record(fields).asMap.toVector)
        case None =>
          base
      }
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

  private def _page_request(
    req: HRequest[IO]
  ): StaticFormAppRenderer.PageRequest = {
    val params = req.uri.query.params
    StaticFormAppRenderer.PageRequest(
      page = params.get("page").flatMap(_.toIntOption).filter(_ > 0).getOrElse(1),
      pageSize = params.get("pageSize").flatMap(_.toIntOption).filter(_ > 0).getOrElse(20),
      includeTotal = _boolean_param(params, "includeTotal")
    )
  }

  private def _boolean_param(
    params: Map[String, String],
    key: String
  ): Boolean =
    params.get(key).map(_.trim.toLowerCase).exists {
      case "true" | "yes" | "on" | "1" => true
      case _ => false
    }

}

object Http4sHttpServer {
  val PortPropertyKey = "textus.server.port"
  val LegacyPortPropertyKey = "cncf.server.port"

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
      .orElse(sys.props.get(LegacyPortPropertyKey))
      .flatMap(x => scala.util.Try(x.toInt).toOption)
      .getOrElse(8080)

  def create(): Http4sHttpServer =
    new Http4sHttpServer(
      HttpExecutionEngine.Factory.engine()
    )
}
