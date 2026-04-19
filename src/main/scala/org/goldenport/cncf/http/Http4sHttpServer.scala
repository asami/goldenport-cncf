package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
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
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.cncf.mcp.McpJsonRpcAdapter
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 21, 2026
 *  version Mar. 29, 2026
 * @version Apr. 19, 2026
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
  private val _mcp = new McpJsonRpcAdapter(engine.runtimeSubsystem)

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

  private[http] def routes(wsb: WebSocketBuilder2[IO]) = HttpRoutes.of[IO] {
      case GET -> Root / "mcp" =>
        _mcp_websocket(wsb, _mcp)
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
      case req @ GET -> Root / "web" / "system" / "admin" =>
        if (_is_web_authorized("system", "admin", "index", Some(req))) _system_admin() else _forbidden()
      case req @ GET -> Root / "web" / "system" / "admin" / "descriptor" =>
        if (_is_web_authorized("system", "admin", "descriptor", Some(req))) _system_admin_descriptor() else _forbidden()
      case GET -> Root / "web" / app / "dashboard" / "state" =>
        _dashboard_state(Some(app))
      case req @ GET -> Root / "web" / app / "admin" =>
        if (_is_web_authorized(app, "admin", "index", Some(req))) _component_admin(app) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "entities" =>
        if (_is_web_authorized(app, "admin.entities", "index", Some(req))) _component_admin_entities(app) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_type(req, app, entity) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / "new" =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_new(app, entity) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / id / "edit" =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_edit(req, app, entity, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / id =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_detail(req, app, entity, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "data" =>
        if (_is_web_authorized(app, "admin.data", "index", Some(req))) _component_admin_data(app) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "data" / data =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_type(req, app, data) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / "new" =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_new(app, data) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / id / "edit" =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_edit(app, data, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / id =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_detail(app, data, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" =>
        if (_is_web_authorized(app, "admin.aggregates", "index", Some(req))) _component_admin_aggregates(app) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" / aggregate / id =>
        if (_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) _component_admin_aggregate_instance_detail(app, aggregate, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" / aggregate =>
        if (_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) _component_admin_aggregate_detail(req, app, aggregate) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "views" =>
        if (_is_web_authorized(app, "admin.views", "index", Some(req))) _component_admin_views(app) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "views" / view / id =>
        if (_is_web_authorized(app, "admin.views", view, Some(req))) _component_admin_view_instance_detail(app, view, id) else _forbidden()
      case req @ GET -> Root / "web" / app / "admin" / "views" / view =>
        if (_is_web_authorized(app, "admin.views", view, Some(req))) _component_admin_view_detail(req, app, view) else _forbidden()
      case GET -> Root / "web" / app =>
        _static_form_app(app, Vector.empty)
      case GET -> Root / "web" / app / page =>
        _static_form_app(app, Vector(page))
      case GET -> Root / "form" / app =>
        _form_index(app)
      case req @ GET -> Root / "form" / app / service / operation =>
        _operation_form(req, app, service, operation)
      case req @ GET -> Root / "form-api" / app / "admin" / "entities" / entity =>
        _component_admin_entity_form_api_definition(req, app, entity)
      case req @ GET -> Root / "form-api" / app / "admin" / "entities" / entity / id / "update" =>
        _component_admin_entity_update_form_api_definition(req, app, entity, id)
      case req @ GET -> Root / "form-api" / app / "admin" / "data" / data =>
        _component_admin_data_form_api_definition(req, app, data)
      case req @ GET -> Root / "form-api" / app / "admin" / "data" / data / id / "update" =>
        _component_admin_data_update_form_api_definition(req, app, data, id)
      case req @ GET -> Root / "form-api" / app / "admin" / "views" / view =>
        _component_admin_view_form_api_definition(req, app, view)
      case req @ GET -> Root / "form-api" / app / "admin" / "aggregates" / aggregate =>
        _component_admin_aggregate_form_api_definition(req, app, aggregate)
      case req @ GET -> Root / "form-api" / app / service / operation =>
        _operation_form_api_definition(req, app, service, operation)
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
      case req @ POST -> Root / "form-api" / app / service / operation / "validate" =>
        _validate_operation_form_api(req, app, service, operation)
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
    StaticFormAppRenderer.renderComponentAdminEntityType(engine.runtimeSubsystem, app, entity, _page_request(req), engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity type admin not found"))
    }

  private def _component_admin_entity_detail(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityDetail(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity detail admin not found"))
    }

  private def _component_admin_entity_edit(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityEdit(engine.runtimeSubsystem, app, entity, id, values = req.uri.query.params.toMap, webDescriptor = engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminEntityNew(engine.runtimeSubsystem, app, entity, webDescriptor = engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminDataDetail(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminDataEdit(engine.runtimeSubsystem, app, data, id, webDescriptor = engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminDataNew(engine.runtimeSubsystem, app, data, webDescriptor = engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminViewInstanceDetail(engine.runtimeSubsystem, app, view, id, engine.webDescriptor) match {
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
    StaticFormAppRenderer.renderComponentAdminAggregateInstanceDetail(engine.runtimeSubsystem, app, aggregate, id, engine.webDescriptor) match {
      case Some(p) =>
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withEntity(p.body)
            .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
        )
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate instance detail admin not found"))
    }

  private[http] def _static_form_app(
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
        _web_error_response(
          Some(app),
          HStatus.NotFound,
          "Static Form App not found",
          s"/web/${app}${page.map(p => s"/${p}").mkString}"
        )
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

  private[http] def _operation_form_api_definition(
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
      StaticFormAppRenderer.renderOperationFormDefinition(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form API not found"))
      }
    }

  private[http] def _component_admin_entity_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    entity: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminEntityFormDefinition(engine.runtimeSubsystem, app, entity, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity form API not found"))
      }
    }

  private[http] def _component_admin_entity_update_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    entity: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminEntityUpdateFormDefinition(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity update form API not found"))
      }
    }

  private[http] def _component_admin_data_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    data: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.data", data, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminDataFormDefinition(engine.runtimeSubsystem, app, data, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data form API not found"))
      }
    }

  private[http] def _component_admin_data_update_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    data: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.data", data, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminDataUpdateFormDefinition(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data update form API not found"))
      }
    }

  private[http] def _component_admin_view_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    view: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.views", view, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminViewFormDefinition(engine.runtimeSubsystem, app, view, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view form API not found"))
      }
    }

  private[http] def _component_admin_aggregate_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    aggregate: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) {
      _forbidden()
    } else {
      StaticFormAppRenderer.renderComponentAdminAggregateFormDefinition(engine.runtimeSubsystem, app, aggregate, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate form API not found"))
      }
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
      values = _form_values(form)
      validation = StaticFormAppRenderer.validateOperationForm(
        engine.runtimeSubsystem,
        app,
        service,
        operation,
        values,
        engine.webDescriptor
      )
      response <-
        validation match {
          case Some(result) if !result.valid =>
            val page = StaticFormAppRenderer.renderOperationForm(
              engine.runtimeSubsystem,
              app,
              service,
              operation,
              engine.webDescriptor,
              values,
              Some(result)
            ).getOrElse(StaticFormAppRenderer.renderFormResult(
              _form_result_properties(app, service, operation, HttpResponse.Text(
                HttpStatus.BadRequest,
                ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
                Bag.text("Validation failed.", StandardCharsets.UTF_8)
              ), values)
            ))
            _html_status(page, HStatus.BadRequest).map { html =>
              RuntimeDashboardMetrics.recordHtmlRequest(
                req.method.name,
                req.uri.path.renderString,
                HStatus.BadRequest.code,
                (System.nanoTime() - started) / 1000000L
              )
              html
            }
          case _ =>
            _submit_valid_operation_form(req, app, service, operation, form, values, started)
        }
    } yield {
      response
    }
  }

  private def _submit_valid_operation_form(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String,
    form: Record,
    values: Map[String, String],
    started: Long
  ): IO[HResponse[IO]] = {
    val res = _dispatch_operation(
        app,
        service,
        operation,
        HttpRequest.fromPath(
          method = HttpRequest.POST,
          path = s"/${app}/${service}/${operation}",
          query = Record.create(req.uri.query.params.toVector),
          header = Record.create(req.headers.headers.map(h => h.name.toString -> h.value)),
          form = _operation_dispatch_form(form)
        )
      )
    val continuation = _create_form_continuation(app, service, operation, form, res, _form_chunk_size(form))
    val properties = _form_result_properties(app, service, operation, res, values ++ _continuation_values(continuation))
    _form_transition_response(app, service, operation, form, res, properties).map { response =>
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
            form = _operation_dispatch_form(form)
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

  private[http] def _validate_operation_form_api(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form validation API not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden()
    } else {
      for {
        form <- _to_plain_form_record(req)
        response <- StaticFormAppRenderer.renderOperationFormValidation(
          engine.runtimeSubsystem,
          app,
          service,
          operation,
          _form_values(form),
          engine.webDescriptor
        ) match {
          case Some(p) => _json(p)
          case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form validation API not found"))
        }
      } yield response
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

  private def _operation_dispatch_form(
    form: Record
  ): Record = {
    val adminContext = Vector(
      "textus.admin.principalId" -> "principalId",
      "textus.admin.subjectId" -> "subjectId",
      "textus.admin.privilege" -> "cncf.security.privilege"
    ).flatMap { case (source, target) =>
      form.getString(source).filter(_.nonEmpty).map(target -> _)
    }
    Record.create((_strip_framework_form_values(form.asMap) ++ adminContext).toVector)
  }

  private[http] def _submit_component_admin_entity_update(
    req: org.http4s.Request[IO],
    app: String,
    entity: String,
    id: String
  ): IO[HResponse[IO]] = {
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req), Some("admin.entity.update")))
      return _forbidden()
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      record = Record.create((form.asMap + ("id" -> id)).toVector)
      values = _form_values(record)
      validation = StaticFormAppRenderer.validateComponentAdminEntityForm(engine.runtimeSubsystem, app, entity, values, engine.webDescriptor, Some("detail"))
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_entity_validation_error_response(app, entity, Some(id), values, result)
        case _ =>
          val result = _dispatch_component_admin_entity_record("update", app, entity, record)
          val page = StaticFormAppRenderer.renderComponentAdminEntityUpdateResult(app, entity, id, values, result.applied, result.message, result.response.code)
          _admin_form_transition_response(app, "entities", entity, "update", record, result, page)
      }
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
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req), Some("admin.entity.create")))
      return _forbidden()
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      values = _form_values(form)
      validation = StaticFormAppRenderer.validateComponentAdminEntityForm(engine.runtimeSubsystem, app, entity, values, engine.webDescriptor, Some("create"))
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_entity_validation_error_response(app, entity, None, values, result)
        case _ =>
          val result = _dispatch_component_admin_entity_record("create", app, entity, form)
          val page = StaticFormAppRenderer.renderComponentAdminEntityCreateResult(app, entity, values, result.applied, result.message, result.response.code)
          _admin_form_transition_response(app, "entities", entity, "create", form, result, page)
      }
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
    if (!_is_web_authorized(app, "admin.data", data, Some(req), Some("admin.data.update")))
      return _forbidden()
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      record = Record.create((form.asMap + ("id" -> id)).toVector)
      values = _form_values(record)
      validation = StaticFormAppRenderer.validateComponentAdminDataForm(engine.runtimeSubsystem, app, data, values, engine.webDescriptor)
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_data_validation_error_response(app, data, Some(id), values, result)
        case _ =>
          val result = _dispatch_component_admin_data_record("update", app, data, record)
          val page = StaticFormAppRenderer.renderComponentAdminDataUpdateResult(app, data, id, values, result.applied, result.message, result.response.code)
          _admin_form_transition_response(app, "data", data, "update", record, result, page)
      }
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
    if (!_is_web_authorized(app, "admin.data", data, Some(req), Some("admin.data.create")))
      return _forbidden()
    val started = System.nanoTime()
    for {
      form <- _to_plain_form_record(req)
      values = _form_values(form)
      validation = StaticFormAppRenderer.validateComponentAdminDataForm(engine.runtimeSubsystem, app, data, values, engine.webDescriptor)
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_data_validation_error_response(app, data, None, values, result)
        case _ =>
          val result = _dispatch_component_admin_data_record("create", app, data, form)
          val page = StaticFormAppRenderer.renderComponentAdminDataCreateResult(app, data, values, result.applied, result.message, result.response.code)
          _admin_form_transition_response(app, "data", data, "create", form, result, page)
      }
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
  ): _AdminFormDispatchResult = {
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
    _AdminFormDispatchResult(response, "Data record was applied.")
  }

  private def _dispatch_component_admin_entity_record(
    operation: String,
    app: String,
    entity: String,
    record: Record
  ): _AdminFormDispatchResult = {
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
    _AdminFormDispatchResult(response, "Entity record was applied.")
  }

  private final case class _AdminFormDispatchResult(
    response: HttpResponse,
    defaultSuccessMessage: String
  ) {
    def applied: Boolean =
      response.code >= 200 && response.code < 300

    def message: String =
      response.getString.getOrElse {
        if (applied) defaultSuccessMessage else s"HTTP ${response.code}"
      }
  }

  private def _admin_form_transition_response(
    app: String,
    surface: String,
    collection: String,
    operation: String,
    form: Record,
    result: _AdminFormDispatchResult,
    fallback: StaticFormAppRenderer.Page
  ): IO[HResponse[IO]] = {
    val descriptor = _admin_form_descriptor(app, surface, collection, operation)
    val redirect =
      if (result.applied)
        descriptor.flatMap(_.successRedirect)
      else if (descriptor.exists(_.stayOnError))
        None
      else
        descriptor.flatMap(_.failureRedirect)
    redirect match {
      case Some(template) =>
        IO.pure(_see_other(_render_admin_redirect_template(template, app, surface, collection, operation, form, result)))
      case None =>
        if (!result.applied && descriptor.exists(_.stayOnError))
          _admin_stay_on_error_response(app, surface, collection, operation, form, result.response, fallback)
        else
          _html(fallback)
    }
  }

  private def _admin_entity_validation_error_response(
    app: String,
    entity: String,
    id: Option[String],
    values: Map[String, String],
    validation: StaticFormAppRenderer.FormValidationResult
  ): IO[HResponse[IO]] = {
    val page = id match {
      case Some(recordId) =>
        StaticFormAppRenderer.renderComponentAdminEntityEdit(
          engine.runtimeSubsystem,
          app,
          entity,
          recordId,
          values,
          engine.webDescriptor,
          Some(validation)
        )
      case None =>
        StaticFormAppRenderer.renderComponentAdminEntityNew(
          engine.runtimeSubsystem,
          app,
          entity,
          values,
          engine.webDescriptor,
          Some(validation)
        )
    }
    _html_status(page.getOrElse(StaticFormAppRenderer.renderSystemAdmin(engine.runtimeSubsystem)), HStatus.BadRequest)
  }

  private def _admin_data_validation_error_response(
    app: String,
    data: String,
    id: Option[String],
    values: Map[String, String],
    validation: StaticFormAppRenderer.FormValidationResult
  ): IO[HResponse[IO]] = {
    val page = id match {
      case Some(recordId) =>
        StaticFormAppRenderer.renderComponentAdminDataEdit(
          engine.runtimeSubsystem,
          app,
          data,
          recordId,
          values,
          engine.webDescriptor,
          Some(validation)
        )
      case None =>
        StaticFormAppRenderer.renderComponentAdminDataNew(
          engine.runtimeSubsystem,
          app,
          data,
          values,
          engine.webDescriptor,
          Some(validation)
        )
    }
    _html_status(page.getOrElse(StaticFormAppRenderer.renderSystemAdmin(engine.runtimeSubsystem)), HStatus.BadRequest)
  }

  private def _admin_stay_on_error_response(
    app: String,
    surface: String,
    collection: String,
    operation: String,
    form: Record,
    response: HttpResponse,
    fallback: StaticFormAppRenderer.Page
  ): IO[HResponse[IO]] = {
    val values = _form_values(form) ++ _error_values(response)
    val page =
      (surface, operation, form.getString("id")) match {
        case ("entities", "update", Some(id)) =>
          StaticFormAppRenderer.renderComponentAdminEntityEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values,
            engine.webDescriptor
          )
        case ("entities", "create", _) =>
          StaticFormAppRenderer.renderComponentAdminEntityNew(
            engine.runtimeSubsystem,
            app,
            collection,
            values,
            engine.webDescriptor
          )
        case ("data", "update", Some(id)) =>
          StaticFormAppRenderer.renderComponentAdminDataEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values,
            engine.webDescriptor
          )
        case ("data", "create", _) =>
          StaticFormAppRenderer.renderComponentAdminDataNew(
            engine.runtimeSubsystem,
            app,
            collection,
            values,
            engine.webDescriptor
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
      _form_result_properties(app, service, operation, res, values),
      _form_result_static_template(app, service, operation, res.code)
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
      response.getString.getOrElse(""),
      _form_result_table_columns(app, service, operation),
      engine.webDescriptor.defaultView
    )

  private def _form_result_table_columns(
    app: String,
    service: String,
    operation: String
  ): Map[String, Vector[StaticFormAppRenderer.TableColumn]] =
    _component(app).map { component =>
      val explicit = _all_entity_table_columns(component)
      val default = _form_result_entity_name(app, service, operation).toVector.flatMap { entityName =>
        val columns = WebTableColumnResolver.resolveEntity(component, entityName, engine.webDescriptor.defaultView)
        if (columns.isEmpty)
          Vector.empty
        else
          Vector("result.data" -> columns, "result.body.data" -> columns)
      }.toMap
      explicit ++ default
    }.getOrElse(Map.empty)

  private def _all_entity_table_columns(
    component: org.goldenport.cncf.component.Component
  ): Map[String, Vector[StaticFormAppRenderer.TableColumn]] =
    component.componentDescriptors.flatMap(_.entityRuntimeDescriptors).flatMap { descriptor =>
      val entity = descriptor.entityName
      val views = (WebTableColumnResolver.defaultViewName +: descriptor.viewNames).distinct
      views.flatMap { view =>
        val columns = WebTableColumnResolver.resolveEntity(component, entity, view)
        if (columns.isEmpty)
          Vector.empty
        else
          Vector(
            StaticFormAppRenderer.tableColumnKey("result.data", entity, view) -> columns,
            StaticFormAppRenderer.tableColumnKey("result.body.data", entity, view) -> columns
          )
      }
    }.toMap

  private def _form_result_entity_name(
    app: String,
    service: String,
    operation: String
  ): Option[String] = {
    val fromOperation = for {
      component <- _component(app)
      serviceDefinition <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, service))
      operationDefinition <- serviceDefinition.operations.operations.find(o => NamingConventions.equivalentByNormalized(o.name, operation))
      datatype <- operationDefinition.response.result.headOption
      entity <- _entity_name_from_result_type(datatype.name)
    } yield entity
    fromOperation.orElse(Some(service).filter(_.nonEmpty))
  }

  private def _entity_name_from_result_type(
    name: String
  ): Option[String] = {
    val text = name.trim
    val searchResult = """SearchResult\[(.+)\]""".r
    val option = """Option\[(.+)\]""".r
    text match {
      case searchResult(entity) => Some(entity.trim)
      case option(entity) => Some(entity.trim)
      case _ => None
    }
  }

  private def _component(
    app: String
  ): Option[org.goldenport.cncf.component.Component] =
    engine.runtimeSubsystem.components.find(c => NamingConventions.equivalentByNormalized(c.name, app))

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
          _html(StaticFormAppRenderer.renderFormResult(
            properties,
            _form_result_static_template(app, service, operation, response.code).orElse(descriptor.flatMap(_.resultTemplate))
          ))
    }
  }

  private def _form_result_static_template(
    app: String,
    service: String,
    operation: String,
    status: Int
  ): Option[String] =
    _web_template_roots().view.flatMap { root =>
      _form_result_template_candidates(app, service, operation, status).flatMap { candidate =>
        val path = root.resolve(candidate)
        if (Files.isRegularFile(path))
          Some(Files.readString(path, StandardCharsets.UTF_8))
        else
          None
      }
    }.headOption

  private def _form_result_template_candidates(
    app: String,
    service: String,
    operation: String,
    status: Int
  ): Vector[Path] = {
    val operationPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation)
    val servicePath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service)
    val appPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(app)
    val suffixes =
      if (status >= 200 && status < 400)
        Vector(status.toString, "success")
      else
        Vector(status.toString, "error")
    suffixes.flatMap { suffix =>
      val filename = s"${operationPath}__${suffix}.html"
      val common = s"__${suffix}.html"
      Vector(
        Paths.get(filename),
        Paths.get(appPath, servicePath, filename),
        Paths.get(appPath, filename),
        Paths.get(common),
        Paths.get(appPath, servicePath, common),
        Paths.get(appPath, common)
      )
    }
  }

  private def _web_error_template(
    app: Option[String],
    status: Int
  ): Option[String] =
    _web_template_roots().view.flatMap { root =>
      _web_error_template_candidates(app, status).flatMap { candidate =>
        val path = root.resolve(candidate)
        if (Files.isRegularFile(path))
          Some(Files.readString(path, StandardCharsets.UTF_8))
        else
          None
      }
    }.headOption

  private def _web_error_template_candidates(
    app: Option[String],
    status: Int
  ): Vector[Path] = {
    val statusName = s"__${status}.html"
    val errorName = "__error.html"
    val appPath = app.map(org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment)
    appPath.toVector.flatMap { x =>
      Vector(Paths.get(x, statusName), Paths.get(x, errorName))
    } ++ Vector(Paths.get(statusName), Paths.get(errorName))
  }

  private def _web_template_roots(): Vector[Path] =
    _web_descriptor_config_root().toVector ++ _subsystem_descriptor_web_root().toVector

  private def _web_descriptor_config_root(): Option[Path] =
    RuntimeConfig.getString(engine.runtimeSubsystem.configuration, RuntimeConfig.WebDescriptorKey).map { value =>
      val path = Paths.get(value)
      if (Files.isDirectory(path))
        path.resolve("web")
      else
        path.getParent
    }

  private def _subsystem_descriptor_web_root(): Option[Path] =
    engine.runtimeSubsystem.descriptor.map(d => d.path.resolve("web"))

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
    result: _AdminFormDispatchResult
  ): String = {
    val id = form.getString("id")
    val resultId = id.orElse(FormResultMetadata.fromHttpResponse(result.response).id)
    val values =
      _form_values(form) ++ Map(
        "component" -> app,
        "surface" -> surface,
        "collection" -> collection,
        "service" -> s"admin.${surface}.${collection}",
        "operation" -> operation,
        "result.status" -> result.response.code.toString,
        "result.body" -> result.message
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
    req: Option[org.http4s.Request[IO]],
    operationSelector: Option[String] = None
  ): Boolean = {
    val selector = Vector(app, service, operation).mkString(".")
    val subject = _web_authorization_subject(req)
    val runtimeConfig = RuntimeConfig.from(engine.runtimeSubsystem.configuration)
    val rule = engine.webDescriptor.authorization
      .get(selector)
      .orElse(
        operationSelector
          .orElse(_admin_operation_selector(service, operation))
          .flatMap(WebOperationAuthorizationPolicy.operationRule(engine.runtimeSubsystem, _, runtimeConfig))
      )
    val allowed =
      rule match {
        case Some(rule) =>
          WebDescriptorAuthorization.isAllowed(
            rule,
            subject,
            runtimeConfig.operationMode
          )
        case None =>
          true
      }
    RuntimeDashboardMetrics.recordAuthorizationDecision(!allowed)
    allowed
  }

  private def _admin_operation_selector(
    service: String,
    operation: String
  ): Option[String] =
    service match {
      case "admin" => Some("admin.config.show")
      case "admin.entities" =>
        Some(if (operation == "index") "admin.entity.list" else "admin.entity.read")
      case "admin.data" =>
        Some(if (operation == "index") "admin.data.list" else "admin.data.read")
      case "admin.views" => Some("admin.view.read")
      case "admin.aggregates" => Some("admin.aggregate.read")
      case _ => None
    }

  private def _web_authorization_subject(
    req: Option[org.http4s.Request[IO]]
  ): WebDescriptorAuthorization.Subject =
    req.map(WebDescriptorAuthorization.Subject.fromHttp).getOrElse(WebDescriptorAuthorization.Subject())

  private def _forbidden(): IO[HResponse[IO]] =
    IO.pure(HResponse[IO](HStatus.Forbidden).withEntity("Forbidden"))

  private[http] def _web_error_response(
    app: Option[String],
    status: HStatus,
    message: String,
    path: String
  ): IO[HResponse[IO]] =
    _web_error_template(app, status.code) match {
      case Some(template) =>
        _html_status(
          StaticFormAppRenderer.renderErrorTemplate(app, status.code, message, path, template),
          status
        )
      case None =>
        IO.pure(HResponse[IO](status).withEntity(message))
    }

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
    _strip_security_form_values(form.asMap)
      .map { case (k, v) => k -> v.toString }

  private def _strip_framework_form_values(
    values: Map[String, Any]
  ): Map[String, Any] =
    values.filterNot { case (k, _) => _is_framework_or_security_form_key(k) || _is_form_context_key(k) }

  private def _strip_security_form_values(
    values: Map[String, Any]
  ): Map[String, Any] =
    values.filterNot { case (k, _) => _is_framework_or_security_form_key(k) }

  private def _is_framework_or_security_form_key(
    key: String
  ): Boolean =
    key.startsWith("textus.admin.") ||
      key.startsWith("cncf.security.") ||
      key.startsWith("security.") ||
      key == "principalId" ||
      key == "principal_id" ||
      key == "subjectId" ||
      key == "subject_id" ||
      key == "privilege" ||
      key == "capability" ||
      key == "capabilities"

  private def _is_form_context_key(
    key: String
  ): Boolean =
    StaticFormAppRenderer.isHiddenFormContextKey(key)

  private def _html(p: StaticFormAppRenderer.Page): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )

  private def _html_status(p: StaticFormAppRenderer.Page, status: HStatus): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](status)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )

  private def _json(p: StaticFormAppRenderer.Page): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`)))
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
