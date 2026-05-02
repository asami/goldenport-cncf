package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.concurrent.duration.*
import scala.collection.concurrent.TrieMap
import fs2.Pipe
import fs2.Stream
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import io.circe.Json
import org.http4s.{HttpRoutes, MediaType, Request as HRequest, Response as HResponse, ResponseCookie, SameSite, Status as HStatus, Uri}
import org.http4s.Header
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{`Content-Type`, Location}
import org.http4s.Charset
import org.http4s.syntax.all.*
import org.http4s.dsl.io.*
import org.http4s.multipart.Multipart
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci.CIString
import org.goldenport.record.Record
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse, HttpStatus}
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.observability.{ConclusionDiagnostics, DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.cncf.mcp.McpJsonRpcAdapter
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.security.AuthenticationRequest
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 21, 2026
 *  version Mar. 29, 2026
 *  version Apr. 30, 2026
 * @version May.  2, 2026
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
      case req if _is_root_request(req) =>
        IO.pure(_temporary_redirect(_redirect_target_with_query(req, "/web")))
      case GET -> Root / "mcp" =>
        _mcp_websocket(wsb, _mcp)
      case GET -> Root / "web" / "assets" / "bootstrap.min.css" =>
        _bootstrap_css()
      case GET -> Root / "web" / "assets" / "bootstrap.bundle.min.js" =>
        _bootstrap_bundle_js()
      case GET -> Root / "web" / "assets" / "textus-widgets.css" =>
        _textus_widgets_css()
      case GET -> Root / "web" / "assets" / "textus-widgets.js" =>
        _textus_widgets_js()
      case req @ GET -> _ if _web_global_asset_path(req).nonEmpty =>
        _web_global_asset(_web_global_asset_path(req).get)
      case req @ GET -> Root / "web" / "blob" / "content" / id =>
        _blob_content(req, id)
      case GET -> Root / "web" / "system" / "dashboard" =>
        _subsystem_dashboard()
      case GET -> Root / "web" / "system" / "dashboard" / "state" =>
        _dashboard_state(None)
      case GET -> Root / "web" / "system" / "performance" =>
        _system_performance()
      case GET -> Root / "web" / "system" / "manual" =>
        _system_manual()
      case GET -> Root / "web" / "system" / "manual" / "openapi.json" =>
        _system_manual_openapi()
      case req @ GET -> Root / "web" / app / "login" =>
        _web_route_alias(Vector("web", app, "login")).flatMap {
          case Some(response) => IO.pure(response)
          case None => _login_page(req, app)
        }
      case req @ GET -> Root / "web" / app / "signup" =>
        _web_route_alias(Vector("web", app, "signup")).flatMap {
          case Some(response) => IO.pure(response)
          case None => _component_web_app_or_static_form_app(app, "signup")
        }
      case req @ POST -> Root / "web" / app / "login" =>
        _login_submit(req, app)
      case req @ POST -> Root / "web" / app / "logout" =>
        _logout_submit(req, app)
      case req @ GET -> Root / "web" / app / "session" =>
        _current_session(req, app)
      case req @ GET -> Root / "web" / "system" / "jobs" / jobId =>
        _system_job(req, jobId)
      case req @ POST -> Root / "web" / "system" / "jobs" / jobId / "await" =>
        _system_job_await(req, jobId)
      case req @ GET -> Root / "web" / "system" / "admin" =>
        if (_is_web_authorized("system", "admin", "index", Some(req))) _system_admin() else _forbidden_web(req, Some("system"), Some("admin"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "descriptor" =>
        if (_is_web_authorized("system", "admin", "descriptor", Some(req))) _system_admin_descriptor() else _forbidden_web(req, Some("system"), Some("admin"), Some("descriptor"))
      case req @ GET -> Root / "web" / "system" / "admin" / "jobs" =>
        if (_is_web_authorized("system", "admin.jobs", "index", Some(req))) _system_admin_jobs() else _forbidden_web(req, Some("system"), Some("admin.jobs"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "jobs" / jobId =>
        if (_is_web_authorized("system", "admin.jobs", jobId, Some(req))) _system_admin_job(req, jobId) else _forbidden_web(req, Some("system"), Some("admin.jobs"), Some(jobId))
      case req @ GET -> Root / "web" / "blob" / "admin" =>
        if (_is_web_authorized("blob", "admin", "index", Some(req))) _blob_admin() else _forbidden_web(req, Some("blob"), Some("admin"), Some("index"))
      case req @ GET -> Root / "web" / "blob" / "admin" / "blobs" =>
        if (_is_web_authorized("blob", "admin.blobs", "index", Some(req))) _blob_admin_blobs(req) else _forbidden_web(req, Some("blob"), Some("admin.blobs"), Some("index"))
      case req @ GET -> Root / "web" / "blob" / "admin" / "blobs" / id =>
        if (_is_web_authorized("blob", "admin.blobs", id, Some(req))) _blob_admin_blob(req, id) else _forbidden_web(req, Some("blob"), Some("admin.blobs"), Some(id))
      case req @ GET -> Root / "web" / "blob" / "admin" / "blobs" / id / "delete" =>
        if (_is_web_authorized("blob", "admin.blobs", "delete", Some(req), Some("admin.entity.update"))) _blob_admin_blob_delete(req, id) else _forbidden_web(req, Some("blob"), Some("admin.blobs"), Some("delete"))
      case req @ POST -> Root / "web" / "blob" / "admin" / "blobs" / id / "delete" =>
        if (_is_web_authorized("blob", "admin.blobs", "delete", Some(req), Some("admin.entity.update"))) _blob_admin_blob_delete_submit(req, id) else _forbidden_web(req, Some("blob"), Some("admin.blobs"), Some("delete"))
      case req @ GET -> Root / "web" / "blob" / "admin" / "associations" =>
        if (_is_web_authorized("blob", "admin.associations", "index", Some(req))) _blob_admin_associations(req) else _forbidden_web(req, Some("blob"), Some("admin.associations"), Some("index"))
      case req @ POST -> Root / "web" / "blob" / "admin" / "associations" / "attach" =>
        if (_is_web_authorized("blob", "admin.associations", "attach", Some(req), Some("admin.entity.update"))) _blob_admin_association_attach(req) else _forbidden_web(req, Some("blob"), Some("admin.associations"), Some("attach"))
      case req @ POST -> Root / "web" / "blob" / "admin" / "associations" / "detach" =>
        if (_is_web_authorized("blob", "admin.associations", "detach", Some(req), Some("admin.entity.update"))) _blob_admin_association_detach(req) else _forbidden_web(req, Some("blob"), Some("admin.associations"), Some("detach"))
      case req @ GET -> Root / "web" / "blob" / "admin" / "store" =>
        if (_is_web_authorized("blob", "admin.store", "index", Some(req))) _blob_admin_store(req) else _forbidden_web(req, Some("blob"), Some("admin.store"), Some("index"))
      case req @ GET -> Root / "web" / "admin" / "associations" =>
        if (_is_web_authorized("admin", "associations", "index", Some(req), Some("admin.entity.read"))) _admin_associations(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("index"))
      case req @ POST -> Root / "web" / "admin" / "associations" / "attach" =>
        if (_is_web_authorized("admin", "associations", "attach", Some(req), Some("admin.entity.update"))) _admin_association_attach(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("attach"))
      case req @ POST -> Root / "web" / "admin" / "associations" / "detach" =>
        if (_is_web_authorized("admin", "associations", "detach", Some(req), Some("admin.entity.update"))) _admin_association_detach(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("detach"))
      case GET -> Root / "web" / app / "dashboard" / "state" =>
        _dashboard_state(Some(app))
      case req @ GET -> Root / "web" / app / "admin" =>
        if (_is_web_authorized(app, "admin", "index", Some(req))) _component_admin(app) else _forbidden_web(req, Some(app), Some("admin"), Some("index"))
      case req @ GET -> Root / "web" / app / "admin" / "descriptor" =>
        if (_is_web_authorized(app, "admin", "descriptor", Some(req))) _component_admin_descriptor(app) else _forbidden_web(req, Some(app), Some("admin"), Some("descriptor"))
      case req @ GET -> Root / "web" / app / "admin" / "entities" =>
        if (_is_web_authorized(app, "admin.entities", "index", Some(req))) _component_admin_entities(app) else _forbidden_web(req, Some(app), Some("admin.entities"), Some("index"))
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_type(req, app, entity) else _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / "new" =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_new(app, entity) else _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / id / "edit" =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_edit(req, app, entity, id) else _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
      case req @ GET -> Root / "web" / app / "admin" / "entities" / entity / id =>
        if (_is_web_authorized(app, "admin.entities", entity, Some(req))) _component_admin_entity_detail(req, app, entity, id) else _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
      case req @ GET -> Root / "web" / app / "admin" / "data" =>
        if (_is_web_authorized(app, "admin.data", "index", Some(req))) _component_admin_data(app) else _forbidden_web(req, Some(app), Some("admin.data"), Some("index"))
      case req @ GET -> Root / "web" / app / "admin" / "data" / data =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_type(req, app, data) else _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / "new" =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_new(app, data) else _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / id / "edit" =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_edit(app, data, id) else _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
      case req @ GET -> Root / "web" / app / "admin" / "data" / data / id =>
        if (_is_web_authorized(app, "admin.data", data, Some(req))) _component_admin_data_detail(app, data, id) else _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" =>
        if (_is_web_authorized(app, "admin.aggregates", "index", Some(req))) _component_admin_aggregates(app) else _forbidden_web(req, Some(app), Some("admin.aggregates"), Some("index"))
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" / aggregate / id =>
        if (_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) _component_admin_aggregate_instance_detail(app, aggregate, id) else _forbidden_web(req, Some(app), Some("admin.aggregates"), Some(aggregate))
      case req @ GET -> Root / "web" / app / "admin" / "aggregates" / aggregate =>
        if (_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) _component_admin_aggregate_detail(req, app, aggregate) else _forbidden_web(req, Some(app), Some("admin.aggregates"), Some(aggregate))
      case req @ GET -> Root / "web" / app / "admin" / "views" =>
        if (_is_web_authorized(app, "admin.views", "index", Some(req))) _component_admin_views(app) else _forbidden_web(req, Some(app), Some("admin.views"), Some("index"))
      case req @ GET -> Root / "web" / app / "admin" / "views" / view / id =>
        if (_is_web_authorized(app, "admin.views", view, Some(req))) _component_admin_view_instance_detail(app, view, id) else _forbidden_web(req, Some(app), Some("admin.views"), Some(view))
      case req @ GET -> Root / "web" / app / "admin" / "views" / view =>
        if (_is_web_authorized(app, "admin.views", view, Some(req))) _component_admin_view_detail(req, app, view) else _forbidden_web(req, Some(app), Some("admin.views"), Some(view))
      case GET -> Root / "web" / app / "manual" =>
        _component_manual(app)
      case GET -> Root / "web" / app / "manual" / service =>
        _component_manual_service(app, service)
      case GET -> Root / "web" / app / "manual" / service / operation =>
        _component_manual_operation(app, service, operation)
      case GET -> Root / "web" =>
        _web_route_alias(Vector("web")).flatMap {
          case Some(response) => IO.pure(response)
          case None =>
            if (_show_runtime_landing)
              _runtime_landing()
            else
              IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Web app route not found"))
        }
      case req @ GET -> _ if _web_component_asset_path(req).nonEmpty =>
        val (component, webApp, assetPath) = _web_component_asset_path(req).get
        _web_app_asset(component, webApp, assetPath)
      case req @ GET -> _ if _web_alias_asset_path(req).nonEmpty =>
        val (app, assetPath) = _web_alias_asset_path(req).get
        _web_route_alias_asset(app, assetPath)
      case GET -> Root / "web" / component / webApp / page =>
        _component_web_app(component, webApp, Vector(page))
      case GET -> Root / "web" / app =>
        _web_route_alias(Vector("web", app)).flatMap {
          case Some(response) => IO.pure(response)
          case None => _static_form_app(app, Vector.empty)
        }
      case GET -> Root / "web" / first / second =>
        _component_web_app_or_static_form_app(first, second)
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
      case req @ POST -> Root / "form" / app / service / operation / "jobs" / jobId / "await" =>
        _await_operation_form_job(req, app, service, operation, jobId)
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
      case req if _is_rest_compatibility_request(req) =>
        IO.pure(_temporary_redirect(_rest_latest_stable_target(req)))
      case req if _is_rest_v1_request(req) =>
        try {
          val started = System.nanoTime()
          for {
            core <- _to_http_request(req, Some(_rest_execution_path(req)))
            res <- _to_http_execution_response(executeWithMetadata(core), Some(req))
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
      case _ =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Route not found"))
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

  private def _textus_widgets_css(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.textusWidgetsCss)
        .withContentType(`Content-Type`(MediaType.text.css, Some(Charset.`UTF-8`)))
    )

  private def _textus_widgets_js(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.textusWidgetsJs)
        .withContentType(`Content-Type`(MediaType.application.javascript, Some(Charset.`UTF-8`)))
    )

  private def _subsystem_dashboard(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSubsystemDashboard(engine.runtimeSubsystem)
    _html(p)
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
    _html(p)
  }

  private def _system_admin(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSystemAdmin(engine.runtimeSubsystem, engine.webDescriptor)
    _html(p)
  }

  private def _system_admin_descriptor(): IO[HResponse[IO]] = {
    val p = StaticFormAppRenderer.renderSystemAdminDescriptor(engine.webDescriptor)
    _html(p)
  }

  private def _system_admin_jobs(): IO[HResponse[IO]] =
    _html(StaticFormAppRenderer.renderSystemAdminJobs(engine.runtimeSubsystem))

  private def _system_admin_job(
    req: org.http4s.Request[IO],
    jobId: String
  ): IO[HResponse[IO]] =
    JobId.parse(jobId).toOption.flatMap(engine.runtimeSubsystem.jobEngine.query) match {
      case Some(model) =>
        _html(StaticFormAppRenderer.renderSystemAdminJob(engine.runtimeSubsystem, model))
      case None =>
        _html_status(StaticFormAppRenderer.renderSystemJobResult(jobId, HttpResponse.notFound(s"job not found: $jobId")), HStatus.NotFound)
    }

  private def _blob_admin(): IO[HResponse[IO]] =
    _html(StaticFormAppRenderer.renderBlobAdmin(), Some("blob"))

  private def _blob_admin_blobs(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      StaticFormAppRenderer.renderBlobAdminBlobs(engine.runtimeSubsystem, req.uri.query.params.toMap, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some("index")
    )

  private def _blob_admin_blob(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      StaticFormAppRenderer.renderBlobAdminBlobDetail(engine.runtimeSubsystem, id, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some(id)
    )

  private def _blob_admin_blob_delete(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      StaticFormAppRenderer.renderBlobAdminBlobDelete(engine.runtimeSubsystem, id, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some("delete")
    )

  private def _blob_admin_blob_delete_submit(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        StaticFormAppRenderer.renderBlobAdminBlobDeleteResult(engine.runtimeSubsystem, id, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.blobs"),
        Some("delete")
      )
    } yield response

  private def _blob_admin_associations(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      StaticFormAppRenderer.renderBlobAdminAssociations(engine.runtimeSubsystem, req.uri.query.params.toMap, _blob_admin_request_properties(req)),
      Some("admin.associations"),
      Some("index")
    )

  private def _blob_admin_association_attach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        StaticFormAppRenderer.renderBlobAdminAssociationAttachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.associations"),
        Some("attach")
      )
    } yield response

  private def _blob_admin_association_detach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        StaticFormAppRenderer.renderBlobAdminAssociationDetachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.associations"),
        Some("detach")
      )
    } yield response

  private def _admin_associations(req: HRequest[IO]): IO[HResponse[IO]] =
    _admin_page(
      req,
      StaticFormAppRenderer.renderAdminAssociations(engine.runtimeSubsystem, req.uri.query.params.toMap, _admin_request_properties(req)),
      Some("associations"),
      Some("index")
    )

  private def _admin_association_attach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        StaticFormAppRenderer.renderAdminAssociationAttachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("associations"),
        Some("attach")
      )
    } yield response

  private def _admin_association_detach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        StaticFormAppRenderer.renderAdminAssociationDetachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("associations"),
        Some("detach")
      )
    } yield response

  private def _blob_admin_store(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      StaticFormAppRenderer.renderBlobAdminStore(engine.runtimeSubsystem, _blob_admin_request_properties(req)),
      Some("admin.store"),
      Some("index")
    )

  private def _blob_content(req: HRequest[IO], id: String): IO[HResponse[IO]] = {
    val request = org.goldenport.protocol.Request.of(
      component = "blob",
      service = "blob",
      operation = "read_blob",
      properties = List(org.goldenport.protocol.Property("id", id, None)) ++
        _session_id_(req).map(x => org.goldenport.protocol.Property("x-textus-session", x, None)).toList
    )
    engine.runtimeSubsystem.executeWithMetadata(request) match {
      case Consequence.Success(result) =>
        result.response match {
          case org.goldenport.protocol.operation.OperationResponse.Http(response) =>
            if (_blob_if_none_match(req, response)) {
              RuntimeDashboardMetrics.recordBlobOperation("content", error = false)
              IO.pure(_blob_not_modified_response(response, result.metadata))
            }
            else
              _blob_content_response(req, response, result.metadata)
          case other =>
            RuntimeDashboardMetrics.recordBlobOperation("content", error = true, diagnosticKey = Some(ConclusionDiagnostics.unknown.diagnosticKey))
            _web_error_response(Some("blob"), HStatus.InternalServerError, s"Blob content operation returned ${other.show}", req.uri.path.renderString)
        }
      case Consequence.Failure(conclusion) =>
        val status = HStatus.fromInt(conclusion.status.webCode.code).getOrElse(HStatus.InternalServerError)
        RuntimeDashboardMetrics.recordBlobOperation("content", error = true, diagnosticKey = Some(ConclusionDiagnostics.classify(conclusion).diagnosticKey))
        _web_error_response(Some("blob"), status, conclusion, req.uri.path.renderString, req.method.name)
    }
  }

  private def _blob_if_none_match(
    req: HRequest[IO],
    response: HttpResponse
  ): Boolean =
    response.headerValue("ETag").exists { etag =>
      req.headers.get(CIString("If-None-Match")).exists(_.exists(h => _blob_etag_matches(h.value, etag)))
    }

  private def _blob_etag_matches(
    requestValue: String,
    etag: String
  ): Boolean =
    requestValue.split(",").iterator.map(_.trim).exists(x => x == "*" || x == etag)

  private def _blob_not_modified_response(
    response: HttpResponse,
    metadata: RuntimeContext.ExecutionMetadata
  ): HResponse[IO] =
    _blob_cache_headers(
      _with_job_id_header(HResponse[IO](HStatus.NotModified), metadata),
      response,
      Vector("ETag", "Last-Modified", "Cache-Control", "X-Content-Type-Options")
    )

  private def _blob_content_response(
    req: HRequest[IO],
    response: HttpResponse,
    metadata: RuntimeContext.ExecutionMetadata
  ): IO[HResponse[IO]] =
    response.getBinary match {
      case Some(binary) if response.code < 400 =>
        val mime = MediaType.parse(response.mime.value).fold(_ => MediaType.application.`octet-stream`, identity)
        val charset: Option[org.http4s.Charset] =
          response.charset.map(c => org.http4s.Charset.fromNioCharset(c))
        val base = _with_job_id_header(HResponse[IO](HStatus.Ok), metadata)
          .withBodyStream(fs2.io.readInputStream(IO(binary.openInputStream()), 8192, closeAfterUse = true))
          .withContentType(`Content-Type`(mime, charset))
        RuntimeDashboardMetrics.recordBlobOperation("content", error = false)
        IO.pure(
          _blob_cache_headers(
            base,
            response,
            Vector("ETag", "Last-Modified", "Content-Length", "Cache-Control", "X-Content-Type-Options")
          )
            .putHeaders(Header.Raw(CIString("Content-Disposition"), _blob_content_disposition(req, response)))
        )
      case _ =>
        RuntimeDashboardMetrics.recordBlobOperation(
          "content",
          error = response.code >= 400,
          diagnosticKey = if (response.code >= 400) Some(ConclusionDiagnostics.unknown.diagnosticKey) else None
        )
        _to_http_response_with_metadata(response, Some(req), Some("blob"), Some("blob"), Some("read_blob"), metadata)
    }

  private def _blob_cache_headers(
    out: HResponse[IO],
    response: HttpResponse,
    names: Vector[String]
  ): HResponse[IO] =
    names.
      flatMap(name => response.headerValue(name).map(value => Header.Raw(CIString(name), value))).
      foldLeft(out)((z, h) => z.putHeaders(h))

  private def _blob_content_disposition(
    req: HRequest[IO],
    response: HttpResponse
  ): String = {
    val base = response.headerValue("Content-Disposition").getOrElse("inline")
    val disposition =
      if (req.uri.query.params.get("download").exists(_.equalsIgnoreCase("true")))
        "attachment"
      else
        "inline"
    base.replaceFirst("(?i)^(inline|attachment)", disposition)
  }

  private def _blob_admin_page(
    req: HRequest[IO],
    page: org.goldenport.Consequence[StaticFormAppRenderer.Page],
    service: Option[String],
    operation: Option[String]
  ): IO[HResponse[IO]] =
    page match {
      case org.goldenport.Consequence.Success(p) =>
        _html(p, Some("blob"))
      case org.goldenport.Consequence.Failure(conclusion) =>
        val status = HStatus.fromInt(conclusion.status.webCode.code).getOrElse(HStatus.InternalServerError)
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          status.code,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some("blob"),
          service = service,
          operation = operation
        )
        _web_error_response(Some("blob"), status, error)
    }

  private def _admin_page(
    req: HRequest[IO],
    page: org.goldenport.Consequence[StaticFormAppRenderer.Page],
    service: Option[String],
    operation: Option[String]
  ): IO[HResponse[IO]] =
    page match {
      case org.goldenport.Consequence.Success(p) =>
        _html(p, Some("admin"))
      case org.goldenport.Consequence.Failure(conclusion) =>
        val status = HStatus.fromInt(conclusion.status.webCode.code).getOrElse(HStatus.InternalServerError)
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          status.code,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some("admin"),
          service = service,
          operation = operation
        )
        _web_error_response(Some("admin"), status, error)
    }

  private def _admin_request_properties(
    req: HRequest[IO]
  ): Vector[(String, String)] =
    _session_id_(req).map("x-textus-session" -> _).toVector

  private def _blob_admin_request_properties(
    req: HRequest[IO]
  ): Vector[(String, String)] =
    _session_id_(req).map("x-textus-session" -> _).toVector

  private def _system_manual(): IO[HResponse[IO]] =
    _html(StaticFormAppRenderer.renderSystemManual(engine.runtimeSubsystem))

  private def _runtime_landing(): IO[HResponse[IO]] =
    _html(StaticFormAppRenderer.renderRuntimeLanding(engine.runtimeSubsystem))

  private def _system_manual_openapi(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(OpenApiProjector.forSubsystem(engine.runtimeSubsystem))
        .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`)))
    )

  private def _system_job(
    req: org.http4s.Request[IO],
    jobId: String
  ): IO[HResponse[IO]] = {
    val res = _dispatch_operation(
      "job_control",
      "job",
      "get_job_status",
      HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/job_control/job/get_job_status",
        query = Record.empty,
        header = _request_header_record(req),
        form = Record.data("id" -> jobId)
      )
    )
    if (res.code >= 200 && res.code < 400)
      _html(StaticFormAppRenderer.renderSystemJobTicket(jobId))
    else
      _html_status(StaticFormAppRenderer.renderSystemJobResult(jobId, res), HStatus.fromInt(res.code).getOrElse(HStatus.Forbidden))
  }

  private def _system_job_await(
    req: org.http4s.Request[IO],
    jobId: String
  ): IO[HResponse[IO]] = {
    val res = _dispatch_operation(
      "job_control",
      "job",
      "await_job_result",
      HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/job_control/job/await_job_result",
        query = Record.empty,
        header = _request_header_record(req),
        form = Record.data("id" -> jobId)
      )
    )
    _html_status(StaticFormAppRenderer.renderSystemJobResult(jobId, res), HStatus.fromInt(res.code).getOrElse(HStatus.Ok))
  }

  private def _component_manual(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentManual(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component manual not found"))
    }

  private def _component_manual_service(
    app: String,
    service: String
  ): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentManualService(engine.runtimeSubsystem, app, service) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Service manual not found"))
    }

  private def _component_manual_operation(
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentManualOperation(engine.runtimeSubsystem, app, service, operation) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation manual not found"))
    }

  private[http] def _web_app_asset(
    componentName: String,
    webAppName: String,
    assetName: String
  ): IO[HResponse[IO]] =
    _web_app_asset(componentName, webAppName, Vector(assetName))

  private[http] def _web_app_asset(
    componentName: String,
    webAppName: String,
    assetPath: Vector[String]
  ): IO[HResponse[IO]] =
    if (!_component_exists(componentName))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component Web app asset not found"))
    else if (!_safe_asset_path(assetPath))
      IO.pure(HResponse[IO](HStatus.BadRequest).withEntity("Invalid Web app asset path"))
    else
      _web_app_asset_content(webAppName, assetPath) match {
        case Some((content, mediaType)) =>
          _asset_response(content, mediaType)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component Web app asset not found"))
      }

  private[http] def _web_global_asset(
    assetName: String
  ): IO[HResponse[IO]] =
    _web_global_asset(Vector(assetName))

  private[http] def _web_global_asset(
    assetPath: Vector[String]
  ): IO[HResponse[IO]] =
    if (!_safe_asset_path(assetPath))
      IO.pure(HResponse[IO](HStatus.BadRequest).withEntity("Invalid Web asset path"))
    else
      _web_global_asset_content(assetPath) match {
        case Some((content, mediaType)) =>
          _asset_response(content, mediaType)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Web asset not found"))
      }

  private[http] def _web_route_alias(
    path: Vector[String]
  ): IO[Option[HResponse[IO]]] =
    engine.webDescriptor.webRouteFor(path) match {
      case Some(route) =>
        engine.webDescriptor.appKind(route.target.normalizedApp).map(_.toLowerCase) match {
          case Some("static-form") if route.remainingPath.isEmpty =>
            if (_web_app_static_html_content(route.target.normalizedApp, Vector.empty).nonEmpty)
              _component_web_app(
                route.target.normalizedComponent,
                route.target.normalizedApp,
                Vector.empty
              ).map(Some(_))
            else
              _static_form_app(route.target.normalizedApp, Vector.empty).map(Some(_))
          case Some("static-form") =>
            _component_web_app(
              route.target.normalizedComponent,
              route.target.normalizedApp,
              route.remainingPath
            ).map(Some(_))
          case _ =>
            _component_web_app(
              route.target.normalizedComponent,
              route.target.normalizedApp,
              route.remainingPath
            ).map(Some(_))
        }
      case None =>
        IO.pure(None)
    }

  private[http] def _web_route_alias_asset(
    app: String,
    assetName: String
  ): IO[HResponse[IO]] =
    _web_route_alias_asset(app, Vector(assetName))

  private[http] def _web_route_alias_asset(
    app: String,
    assetPath: Vector[String]
  ): IO[HResponse[IO]] =
    engine.webDescriptor.webRouteFor(Vector("web", app)) match {
      case Some(route) if route.remainingPath.isEmpty =>
        _web_app_asset(route.target.normalizedComponent, route.target.normalizedApp, assetPath)
      case _ =>
        _static_form_app(app, "assets" +: assetPath)
    }

  private[http] def _component_web_app(
    componentName: String,
    webAppName: String,
    page: Vector[String]
  ): IO[HResponse[IO]] =
    if (!_component_exists(componentName))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component Web app not found"))
    else
      _web_app_static_html_content(webAppName, page) match {
        case Some(content) =>
          _html_content(content, Some(webAppName), Some(componentName))
        case None =>
          _web_error_response(
            Some(webAppName),
            HStatus.NotFound,
            "Component Web app page not found",
            s"/web/${componentName}/${webAppName}${page.map(p => s"/${p}").mkString}"
          )
      }

  private[http] def _component_web_app_or_static_form_app(
    first: String,
    second: String
  ): IO[HResponse[IO]] =
    if (_component_exists(first) && _web_app_static_html_content(second, Vector.empty).nonEmpty)
      _component_web_app(first, second, Vector.empty)
    else engine.webDescriptor.webRouteFor(Vector("web", first, second)) match {
      case Some(route) =>
        _component_web_app(route.target.normalizedComponent, route.target.normalizedApp, route.remainingPath)
      case None =>
      _static_form_app(first, Vector(second))
    }

  private def _component_admin(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdmin(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component admin not found"))
    }

  private def _component_admin_descriptor(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDescriptor(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component descriptor admin not found"))
    }

  private def _component_admin_entities(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntities(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity admin not found"))
    }

  private def _component_admin_entity_type(req: HRequest[IO], app: String, entity: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityType(engine.runtimeSubsystem, app, entity, _page_request(req), engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity type admin not found"))
    }

  private def _component_admin_entity_detail(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityDetail(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity detail admin not found"))
    }

  private def _component_admin_entity_edit(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityEdit(engine.runtimeSubsystem, app, entity, id, values = req.uri.query.params.toMap, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity edit admin not found"))
    }

  private def _component_admin_entity_new(app: String, entity: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminEntityNew(engine.runtimeSubsystem, app, entity, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity new admin not found"))
    }

  private def _component_admin_data(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminData(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data admin not found"))
    }

  private def _component_admin_data_type(req: HRequest[IO], app: String, data: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataType(engine.runtimeSubsystem, app, data, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data type admin not found"))
    }

  private def _component_admin_data_detail(app: String, data: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataDetail(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data detail admin not found"))
    }

  private def _component_admin_data_edit(app: String, data: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataEdit(engine.runtimeSubsystem, app, data, id, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data edit admin not found"))
    }

  private def _component_admin_data_new(app: String, data: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminDataNew(engine.runtimeSubsystem, app, data, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data new admin not found"))
    }

  private def _component_admin_views(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViews(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view admin not found"))
    }

  private def _component_admin_view_detail(req: HRequest[IO], app: String, view: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViewDetail(engine.runtimeSubsystem, app, view, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view detail admin not found"))
    }

  private def _component_admin_view_instance_detail(app: String, view: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminViewInstanceDetail(engine.runtimeSubsystem, app, view, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view instance detail admin not found"))
    }

  private def _component_admin_aggregates(app: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregates(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate admin not found"))
    }

  private def _component_admin_aggregate_detail(req: HRequest[IO], app: String, aggregate: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregateDetail(engine.runtimeSubsystem, app, aggregate, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate detail admin not found"))
    }

  private def _component_admin_aggregate_instance_detail(app: String, aggregate: String, id: String): IO[HResponse[IO]] =
    StaticFormAppRenderer.renderComponentAdminAggregateInstanceDetail(engine.runtimeSubsystem, app, aggregate, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate instance detail admin not found"))
    }

  private[http] def _static_form_app(
    app: String,
    page: Vector[String]
  ): IO[HResponse[IO]] =
    StaticFormAppRenderer.render(engine.runtimeSubsystem, app, page, engine.webDescriptor) match {
      case Some(p) => _html(p, Some(app))
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
        _html(p, Some(app))
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
      _forbidden_web(req, Some(app), Some(service), Some(operation))
    else StaticFormAppRenderer.renderOperationForm(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) =>
        _html(p, Some(app))
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
      _not_found_api_response(req, "Operation form API not found", Some(app), Some(service), Some(operation))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden_api(req, Some(app), Some(service), Some(operation))
    } else {
      StaticFormAppRenderer.renderOperationFormDefinition(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Operation form API not found", Some(app), Some(service), Some(operation))
      }
    }

  private[http] def _component_admin_entity_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    entity: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.entities"), Some(entity))
    } else {
      StaticFormAppRenderer.renderComponentAdminEntityFormDefinition(engine.runtimeSubsystem, app, entity, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component entity form API not found", Some(app), Some("admin.entities"), Some(entity))
      }
    }

  private[http] def _component_admin_entity_update_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    entity: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.entities"), Some(entity))
    } else {
      StaticFormAppRenderer.renderComponentAdminEntityUpdateFormDefinition(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component entity update form API not found", Some(app), Some("admin.entities"), Some(entity))
      }
    }

  private[http] def _component_admin_data_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    data: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.data", data, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.data"), Some(data))
    } else {
      StaticFormAppRenderer.renderComponentAdminDataFormDefinition(engine.runtimeSubsystem, app, data, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component data form API not found", Some(app), Some("admin.data"), Some(data))
      }
    }

  private[http] def _component_admin_data_update_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    data: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.data", data, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.data"), Some(data))
    } else {
      StaticFormAppRenderer.renderComponentAdminDataUpdateFormDefinition(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component data update form API not found", Some(app), Some("admin.data"), Some(data))
      }
    }

  private[http] def _component_admin_view_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    view: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.views", view, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.views"), Some(view))
    } else {
      StaticFormAppRenderer.renderComponentAdminViewFormDefinition(engine.runtimeSubsystem, app, view, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component view form API not found", Some(app), Some("admin.views"), Some(view))
      }
    }

  private[http] def _component_admin_aggregate_form_api_definition(
    req: org.http4s.Request[IO],
    app: String,
    aggregate: String
  ): IO[HResponse[IO]] =
    if (!_is_web_authorized(app, "admin.aggregates", aggregate, Some(req))) {
      _forbidden_api(req, Some(app), Some("admin.aggregates"), Some(aggregate))
    } else {
      StaticFormAppRenderer.renderComponentAdminAggregateFormDefinition(engine.runtimeSubsystem, app, aggregate, engine.webDescriptor) match {
        case Some(p) =>
          _json(p)
        case None =>
          _not_found_api_response(req, "Component aggregate form API not found", Some(app), Some("admin.aggregates"), Some(aggregate))
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
      _forbidden_web(req, Some(app), Some(service), Some(operation))
    } else {
    val started = System.nanoTime()
    for {
      form <- _to_form_record(req)
      operationValues = _operation_form_values(form)
      pageValues = _form_values(form)
      validation = StaticFormAppRenderer.validateOperationForm(
        engine.runtimeSubsystem,
        app,
        service,
        operation,
        operationValues,
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
              pageValues,
              Some(result)
            ).getOrElse(StaticFormAppRenderer.renderFormResult(
              _form_result_properties(app, service, operation, HttpResponse.Text(
                HttpStatus.BadRequest,
                ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
                Bag.text("Validation failed.", StandardCharsets.UTF_8)
              ), pageValues)
            ))
            _html_status(page, HStatus.BadRequest, Some(app)).map { html =>
              RuntimeDashboardMetrics.recordHtmlRequest(
                req.method.name,
                req.uri.path.renderString,
                HStatus.BadRequest.code,
                (System.nanoTime() - started) / 1000000L
              )
              html
            }
          case _ =>
            _submit_valid_operation_form(req, app, service, operation, form, pageValues, started)
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
          header = _request_header_record(req),
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
      _not_found_api_response(req, "Operation form API not found", Some(app), Some(service), Some(operation))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden_api(req, Some(app), Some(service), Some(operation))
    } else {
      val started = System.nanoTime()
      for {
        form <- _to_form_record(req)
        query = Record.create(req.uri.query.params.toVector)
        header = _request_header_record(req, query, form)
        componentSegment = _dispatch_component_segment(app)
        result = _dispatch_operation_result(
          componentSegment,
          service,
          operation,
          HttpRequest.fromPath(
            method = HttpRequest.POST,
            path = s"/${componentSegment}/${service}/${operation}",
            query = query,
            header = header,
            form = _operation_dispatch_form(form)
          )
        )
        res = result.response
        out <- _to_http_execution_response(
          result,
          Some(req),
          component = Some(app),
          service = Some(service),
          operation = Some(operation)
        )
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
      _not_found_api_response(req, "Operation form validation API not found", Some(app), Some(service), Some(operation))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden_api(req, Some(app), Some(service), Some(operation))
    } else {
      for {
        form <- _to_plain_form_record(req)
        response <- StaticFormAppRenderer.renderOperationFormValidation(
          engine.runtimeSubsystem,
          app,
          service,
          operation,
          _operation_form_values(form),
          engine.webDescriptor
        ) match {
          case Some(p) => _json(p)
          case None => _not_found_api_response(req, "Operation form validation API not found", Some(app), Some(service), Some(operation))
        }
      } yield response
    }

  private[http] def _operation_form_continue(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String,
    id: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Form continuation not found"))
    else if (!_is_web_authorized(app, service, operation, Some(req)))
      _forbidden_web(req, Some(app), Some(service), Some(operation))
    else
    _form_continuations.get(id) match {
      case Some(continuation) if continuation.matches(app, service, operation) =>
        val started = System.nanoTime()
        val queryValues = req.uri.query.params.toMap
        val pagingValues = queryValues.filter { case (k, _) => k == "page" || k == "pageSize" || k == "includeTotal" }
        val res = continuation.response
        val continuationValues =
          if (_boolean_param(queryValues, "includeTotal"))
            _continuation_values(continuation) + (
              "paging.href" -> s"/form/${continuation.app}/${continuation.service}/${continuation.operation}/continue/${continuation.id}?page={page}&pageSize={pageSize}&includeTotal=true"
            )
          else
            _continuation_values(continuation)
        val pageValues = _form_result_page_values(continuation.form)
        val values = continuationValues ++ pageValues ++ pagingValues.map { case (k, v) => s"paging.${k}" -> v }
        val page = StaticFormAppRenderer.renderFormResult(
          _form_result_properties(app, service, operation, res, values),
          _form_result_static_template(app, service, operation, res.code, values)
            .orElse(_form_descriptor(app, service, operation).flatMap(_.resultTemplate))
        )
        _html(page, Some(app)).map { html =>
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

  private[http] def _dispatch_operation(
    app: String,
    service: String,
    operation: String,
    request: HttpRequest
  ): HttpResponse =
    _dispatch_operation_result(app, service, operation, request).response

  private[http] def _dispatch_operation_result(
    app: String,
    service: String,
    operation: String,
    request: HttpRequest
  ): HttpExecutionResult = {
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
        org.goldenport.Consequence.success(_operation_dispatcher.dispatchWithMetadata(request))
      }
    } match {
      case org.goldenport.Consequence.Success(response) =>
        response
      case org.goldenport.Consequence.Failure(conclusion) =>
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          HStatus.InternalServerError.code,
          request.path.asString,
          request.method.name,
          _operation_mode,
          component = Some(app),
          service = Some(service),
          operation = Some(operation)
        )
        HttpExecutionResult(
          HttpResponse.Text(
            HttpStatus.InternalServerError,
            ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
            Bag.text(error.envelopeJson, StandardCharsets.UTF_8)
          ),
          RuntimeContext.ExecutionMetadata.empty
        )
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

  private def _operation_dispatch_record(
    record: Record
  ): Record =
    Record.create(_strip_framework_form_values(record.asMap).toVector)

  private[http] def _submit_component_admin_entity_update(
    req: org.http4s.Request[IO],
    app: String,
    entity: String,
    id: String
  ): IO[HResponse[IO]] = {
    if (!_is_web_authorized(app, "admin.entities", entity, Some(req), Some("admin.entity.update")))
      return _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
    val started = System.nanoTime()
    for {
      form <- _to_form_record(req)
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
      return _forbidden_web(req, Some(app), Some("admin.entities"), Some(entity))
    val started = System.nanoTime()
    for {
      form <- _to_form_record(req)
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
      return _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
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
      return _forbidden_web(req, Some(app), Some("admin.data"), Some(data))
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

  private[http] def _operation_form_result(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden_web(req, Some(app), Some(service), Some(operation))
    } else {
    val started = System.nanoTime()
    val query = _operation_dispatch_record(Record.create(req.uri.query.params.toVector))
    val values = req.uri.query.params.toMap
    val res = _dispatch_operation(
      app,
      service,
      operation,
      HttpRequest.fromPath(
        method = HttpRequest.GET,
        path = s"/${app}/${service}/${operation}",
        query = query,
        header = _request_header_record(req)
      )
    )
    val page = StaticFormAppRenderer.renderFormResult(
      _form_result_properties(app, service, operation, res, values),
      _form_result_static_template(app, service, operation, res.code, values)
        .orElse(_form_descriptor(app, service, operation).flatMap(_.resultTemplate))
    )
    _html(page, Some(app)).map { html =>
      RuntimeDashboardMetrics.recordHtmlRequest(
        req.method.name,
        req.uri.path.renderString,
        res.code,
        (System.nanoTime() - started) / 1000000L
      )
      html
    }
  }

  private[http] def _await_operation_form_job(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String,
    jobId: String
  ): IO[HResponse[IO]] =
    if (!_is_form_enabled(app, service, operation)) {
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation form not found"))
    } else if (!_is_web_authorized(app, service, operation, Some(req))) {
      _forbidden_web(req, Some(app), Some(service), Some(operation))
    } else {
      val started = System.nanoTime()
      val values = Map(
        "result.job.id" -> jobId,
        "result.job.href" -> s"/form/${app}/${service}/${operation}/jobs/${jobId}/await"
      )
      for {
        form <- _to_plain_form_record(req)
        dispatchForm = Record.create(
          (_operation_dispatch_form(form).asMap + ("id" -> jobId)).toVector
        )
        res = _dispatch_operation(
          "job_control",
          "job",
          "await_job_result",
          HttpRequest.fromPath(
            method = HttpRequest.POST,
            path = "/job_control/job/await_job_result",
            query = Record.empty,
            header = _request_header_record(req),
            form = dispatchForm
          )
        )
        page = StaticFormAppRenderer.renderFormResult(
          _form_result_properties(app, service, operation, res, values),
          _form_result_static_template(app, service, operation, res.code, values)
            .orElse(_form_descriptor(app, service, operation).flatMap(_.resultTemplate))
        )
        html <- _html(page, Some(app))
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
      engine.webDescriptor.defaultView,
      _form_result_asset_completion_options(app, service, operation)
    )

  private def _form_result_asset_completion_options(
    app: String,
    service: String,
    operation: String
  ): StaticFormAppLayout.AssetCompletionOptions = {
    val assets = engine.webDescriptor.resultAssets(app, service, operation)
    StaticFormAppLayout.AssetCompletionOptions(
      autoComplete = assets.autoComplete,
      declaredCss = assets.css,
      declaredJs = assets.js
    )
  }

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
    engine.runtimeSubsystem.components.find(c =>
      NamingConventions.equivalentByNormalized(c.name, app)
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
            _operation_form_values(form) ++ _error_values(response)
          ).getOrElse(StaticFormAppRenderer.renderFormResult(properties)), Some(app))
        else
          _html(StaticFormAppRenderer.renderFormResult(
            properties,
            _form_result_static_template(app, service, operation, response.code, properties.page.values).orElse(descriptor.flatMap(_.resultTemplate))
          ), Some(app))
    }
  }

  private[http] def _form_result_static_template(
    app: String,
    service: String,
    operation: String,
    status: Int,
    values: Map[String, String] = Map.empty
  ): Option[String] =
    _web_resource_roots().view.flatMap { root =>
      _form_result_template_candidates(app, service, operation, status, values).flatMap { candidate =>
        root.readText(candidate)
      }
    }.headOption

  private[http] def _form_result_template_candidates(
    app: String,
    service: String,
    operation: String,
    status: Int,
    values: Map[String, String] = Map.empty
  ): Vector[Path] = {
    val operationPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation)
    val servicePath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service)
    val appPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(app)
    val suffixes =
      if (status >= 200 && status < 400)
        Vector(status.toString, "success")
      else
        Vector(status.toString, "error")
    val routeLocalOperation = suffixes.flatMap { suffix =>
      val filename = s"${operationPath}__${suffix}.html"
      Vector(
        Paths.get(appPath, servicePath, filename),
        Paths.get(appPath, filename)
      )
    }
    val routeLocalCommon = suffixes.flatMap { suffix =>
      val common = s"__${suffix}.html"
      Vector(
        Paths.get(appPath, servicePath, common),
        Paths.get(appPath, common)
      )
    }
    val pageLocal = _form_result_page(values).toVector.flatMap { page =>
      suffixes.flatMap { suffix =>
        Vector(
          Paths.get(appPath, s"${page}__${suffix}.html"),
          Paths.get(s"${page}__${suffix}.html")
        )
      }
    }
    val common = suffixes.flatMap { suffix =>
      val filename = s"${operationPath}__${suffix}.html"
      val common = s"__${suffix}.html"
      Vector(
        Paths.get(filename),
        Paths.get(common)
      )
    }
    pageLocal ++ routeLocalOperation ++ routeLocalCommon ++ common
  }

  private def _form_result_page(
    values: Map[String, String]
  ): Option[String] =
    values.get("textus.form.page")
      .orElse(values.get("cncf.form.page"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(_.stripSuffix(".html"))
      .filter(_safe_form_result_page_name)
      .map(org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment)

  private def _safe_form_result_page_name(
    name: String
  ): Boolean =
    name.forall { c =>
      c.isLetterOrDigit || c == '-' || c == '_' || c == '.'
    }

  private def _form_result_page_values(
    form: Record
  ): Map[String, String] =
    _form_values(form).filter { case (k, _) =>
      k == "textus.form.page" || k == "cncf.form.page"
    }

  private[http] def _web_error_template(
    app: Option[String],
    status: Int
  ): Option[String] =
    _web_resource_roots().view.flatMap { root =>
      _web_error_template_candidates(app, status).flatMap { candidate =>
        root.readText(candidate)
      }
    }.headOption

  private[http] def _web_error_template_candidates(
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

  private[http] def _web_resource_roots(): Vector[WebResourceRoot] =
    _web_descriptor_config_root().toVector ++
      _component_web_roots() ++
      _subsystem_descriptor_web_root().toVector

  private[http] def _component_web_roots(): Vector[WebResourceRoot] =
    engine.runtimeSubsystem.components
      .flatMap(_.artifactMetadata.flatMap(_.archivePath))
      .map(path => Paths.get(path).toAbsolutePath.normalize)
      .distinct
      .flatMap { path =>
        if (WebResourceRoot.isArchiveFile(path))
          Vector(WebResourceRoot.archive(path))
        else
          Vector(
            path.resolve("car.d").resolve("web"),
            path.resolve("src").resolve("main").resolve("web"),
            path.resolve("web")
          ).filter(Files.isDirectory(_)).map(WebResourceRoot.directory)
      }

  private[http] def _web_app_asset_content(
    webAppName: String,
    assetName: String
  ): Option[(BinaryBag, MediaType)] =
    _web_app_asset_content(webAppName, Vector(assetName))

  private[http] def _web_app_asset_content(
    webAppName: String,
    assetPath: Vector[String]
  ): Option[(BinaryBag, MediaType)] =
    _web_app_asset_candidates(webAppName, assetPath).view.flatMap { path =>
      _web_resource_roots().view.flatMap { root =>
        root.readBinary(path).map(_ -> _asset_media_type(assetPath.lastOption.getOrElse("")))
      }
    }.headOption

  private def _web_app_asset_candidates(
    webAppName: String,
    assetPath: Vector[String]
  ): Vector[Path] = {
    val webAppPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(webAppName)
    val flat = Vector(
      _relative_path("assets" +: assetPath),
      _relative_path(webAppPath +: "assets" +: assetPath)
    )
    val appNamed = flat.reverse
    if (_is_flat_web_root_app(webAppName)) flat else appNamed
  }

  private[http] def _web_global_asset_content(
    assetName: String
  ): Option[(BinaryBag, MediaType)] =
    _web_global_asset_content(Vector(assetName))

  private[http] def _web_global_asset_content(
    assetPath: Vector[String]
  ): Option[(BinaryBag, MediaType)] =
    _web_resource_roots().view.flatMap { root =>
      val path = _relative_path("assets" +: assetPath)
      root.readBinary(path).map(_ -> _asset_media_type(assetPath.lastOption.getOrElse("")))
    }.headOption

  private[http] def _web_app_static_html_content(
    webAppName: String,
    page: Vector[String]
  ): Option[String] =
    if (!_safe_web_page_path(page))
      None
    else
      _web_app_static_html_candidates(webAppName, page).view.flatMap { path =>
        _web_resource_roots().view.flatMap(_.readText(path))
      }.headOption

  private[http] def _web_app_static_html_candidates(
    webAppName: String,
    page: Vector[String]
  ): Vector[Path] = {
    val webAppPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(webAppName)
    if (page.isEmpty)
      _flat_or_app_named_candidates(webAppName, Vector(
        Paths.get("index.html"),
        Paths.get(webAppPath, "index.html")
      ))
    else {
      val normalized = page.map(_normalize_web_page_segment)
      val exact =
        if (normalized.last.endsWith(".html"))
          _flat_or_app_named_candidates(webAppName, Vector(
            _relative_path(normalized),
            _relative_path(webAppPath +: normalized)
          ))
        else
          Vector.empty
      val html =
        if (normalized.last.endsWith(".html"))
          Vector.empty
        else {
          val withHtml = normalized.init :+ s"${normalized.last}.html"
          _flat_or_app_named_candidates(webAppName, Vector(
            _relative_path(withHtml),
            _relative_path(webAppPath +: withHtml)
          ))
        }
      val index = _flat_or_app_named_candidates(webAppName, Vector(
        _relative_path(normalized :+ "index.html"),
        _relative_path(webAppPath +: (normalized :+ "index.html"))
      ))
      exact ++ html ++ index
    }
  }

  private def _flat_or_app_named_candidates(
    webAppName: String,
    flatFirst: Vector[Path]
  ): Vector[Path] =
    if (_is_flat_web_root_app(webAppName)) flatFirst else flatFirst.reverse

  private def _is_flat_web_root_app(
    webAppName: String
  ): Boolean = {
    val normalized = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(webAppName)
    val staticApps = engine.webDescriptor.apps.filter(app => app.effectiveKind.equalsIgnoreCase("static-form"))
    staticApps.size == 1 && staticApps.head.normalizedName == normalized
  }

  private def _normalize_web_page_segment(
    segment: String
  ): String =
    if (segment.endsWith(".html"))
      s"${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(segment.stripSuffix(".html"))}.html"
    else
      org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(segment)

  private def _safe_web_page_path(
    page: Vector[String]
  ): Boolean =
    page.forall { segment =>
      segment.nonEmpty &&
        !segment.contains("..") &&
        !segment.contains("/") &&
        !segment.contains("\\")
    }

  private def _component_exists(
    componentName: String
  ): Boolean =
    engine.runtimeSubsystem.findComponent(componentName).isDefined

  private def _safe_asset_name(
    assetName: String
  ): Boolean =
    assetName.nonEmpty && !assetName.contains("..") && !assetName.contains("/") && !assetName.contains("\\")

  private def _safe_asset_path(
    assetPath: Vector[String]
  ): Boolean =
    assetPath.nonEmpty && assetPath.forall(_safe_asset_name)

  private def _relative_path(
    segments: Vector[String]
  ): Path =
    Paths.get(segments.mkString("/"))

  private def _web_path_segments(
    req: org.http4s.Request[IO]
  ): Vector[String] =
    req.uri.path.renderString.split("/").toVector.filter(_.nonEmpty)

  private def _web_global_asset_path(
    req: org.http4s.Request[IO]
  ): Option[Vector[String]] =
    _web_path_segments(req) match {
      case Vector("web", "assets", tail*) =>
        Some(tail.toVector)
      case _ =>
        None
    }

  private def _web_component_asset_path(
    req: org.http4s.Request[IO]
  ): Option[(String, String, Vector[String])] =
    _web_path_segments(req) match {
      case Vector("web", component, webApp, "assets", tail*) =>
        Some((component, webApp, tail.toVector))
      case _ =>
        None
    }

  private def _web_alias_asset_path(
    req: org.http4s.Request[IO]
  ): Option[(String, Vector[String])] =
    _web_path_segments(req) match {
      case Vector("web", app, "assets", tail*) =>
        Some((app, tail.toVector))
      case _ =>
        None
    }

  private def _asset_response(
    content: BinaryBag,
    mediaType: MediaType
  ): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(fs2.io.readInputStream(IO(content.openInputStream()), 8192, closeAfterUse = true))
        .withContentType(`Content-Type`(mediaType, None))
    )

  private def _asset_media_type(
    assetName: String
  ): MediaType =
    assetName.toLowerCase(java.util.Locale.ROOT) match {
      case x if x.endsWith(".css") => MediaType.text.css
      case x if x.endsWith(".js") => MediaType.application.javascript
      case x if x.endsWith(".json") => MediaType.application.json
      case x if x.endsWith(".html") => MediaType.text.html
      case x if x.endsWith(".svg") => _media_type("image/svg+xml")
      case x if x.endsWith(".png") => _media_type("image/png")
      case x if x.endsWith(".jpg") || x.endsWith(".jpeg") => _media_type("image/jpeg")
      case x if x.endsWith(".woff") => _media_type("font/woff")
      case x if x.endsWith(".woff2") => _media_type("font/woff2")
      case _ => MediaType.text.plain
    }

  private def _media_type(
    value: String
  ): MediaType =
    MediaType.parse(value).fold(_ => MediaType.text.plain, identity)

  private[http] def _web_descriptor_config_root(): Option[WebResourceRoot] =
    RuntimeConfig.getString(engine.runtimeSubsystem.configuration, RuntimeConfig.WebDescriptorKey).map { value =>
      val path = Paths.get(value)
      if (WebResourceRoot.isArchiveFile(path))
        WebResourceRoot.archive(path)
      else if (Files.isDirectory(path))
        WebResourceRoot.directory(path.resolve("web"))
      else
        WebResourceRoot.directory(Option(path.getParent).getOrElse(Paths.get(".")))
    }

  private[http] def _subsystem_descriptor_web_root(): Option[WebResourceRoot] =
    engine.runtimeSubsystem.descriptor.map { d =>
      if (WebResourceRoot.isArchiveFile(d.path))
        WebResourceRoot.archive(d.path)
      else
        WebResourceRoot.directory(d.path.resolve("web"))
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

  private def _temporary_redirect(path: String): HResponse[IO] =
    HResponse[IO](HStatus.TemporaryRedirect).putHeaders(Location(Uri.unsafeFromString(path)))

  private def _is_root_request(req: org.http4s.Request[IO]): Boolean = {
    val path = req.uri.path.renderString
    path == "/" || path.isEmpty
  }

  private def _is_rest_v1_request(req: org.http4s.Request[IO]): Boolean = {
    val path = req.uri.path.renderString
    path == "/rest/v1" || path == "/rest/v1/" || path.startsWith("/rest/v1/")
  }

  private def _is_rest_compatibility_request(req: org.http4s.Request[IO]): Boolean = {
    val path = req.uri.path.renderString
    (path == "/rest" || path == "/rest/" || path.startsWith("/rest/")) &&
      !_is_rest_v1_request(req)
  }

  private def _rest_execution_path(req: org.http4s.Request[IO]): String = {
    val path = req.uri.path.renderString
    val stripped =
      if (path == "/rest/v1" || path == "/rest/v1/") "/"
      else path.stripPrefix("/rest/v1")
    if (stripped.isEmpty) "/" else stripped
  }

  private def _rest_latest_stable_target(req: org.http4s.Request[IO]): String = {
    val path = req.uri.path.renderString
    val redirectedPath =
      if (path == "/rest" || path == "/rest/") "/rest/v1"
      else s"/rest/v1${path.stripPrefix("/rest")}"
    _redirect_target_with_query(req, redirectedPath)
  }

  private def _redirect_target_with_query(
    req: org.http4s.Request[IO],
    path: String
  ): String = {
    val query = req.uri.query.renderString
    if (query.nonEmpty) s"$path?$query" else path
  }

  private[http] def _login_page(
    req: org.http4s.Request[IO],
    app: String,
    error: Option[String] = None
  ): IO[HResponse[IO]] = {
    val returnTo = _login_return_to_(req.uri.query.params).getOrElse(s"/web/${_escape_path_segment_(app)}")
    val hiddenReturnTo =
      s"""<input type="hidden" name="returnTo" value="${_escape_html_(returnTo)}">"""
    _html(
      StaticFormAppRenderer.Page(
        s"""<!doctype html>
           |<html lang="en">
           |<head>
           |  <meta charset="utf-8">
           |  <meta name="viewport" content="width=device-width, initial-scale=1">
           |  <title>${_escape_html_(app)} Login</title>
           |  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
           |</head>
           |<body class="bg-light">
           |  <main class="container py-5">
           |    <div class="row justify-content-center">
           |      <div class="col-12 col-md-6 col-lg-4">
           |        <div class="card shadow-sm">
           |          <div class="card-body">
           |            <h1 class="h4 mb-3">${_escape_html_(app)} Login</h1>
           |            ${error.map(e => s"""<div class="alert alert-danger" role="alert">${_escape_html_(e)}</div>""").getOrElse("")}
           |            <form method="post" action="/web/${_escape_path_segment_(app)}/login">
           |              $hiddenReturnTo
           |              <div class="mb-3">
           |                <label class="form-label" for="username">Username</label>
           |                <input class="form-control" id="username" name="username" autocomplete="username" required>
           |              </div>
           |              <div class="mb-3">
           |                <label class="form-label" for="password">Password</label>
           |                <input class="form-control" id="password" name="password" type="password" autocomplete="current-password" required>
           |              </div>
           |              <button class="btn btn-primary w-100" type="submit">Login</button>
           |            </form>
           |          </div>
           |        </div>
           |      </div>
           |    </div>
           |  </main>
           |</body>
           |</html>""".stripMargin
      )
    )
  }

  private[http] def _login_submit(
    req: org.http4s.Request[IO],
    app: String
  ): IO[HResponse[IO]] =
    _auth_service match {
      case Some(service) =>
        for {
          form <- _to_plain_form_record(req)
          response <- {
            given ExecutionContext = ExecutionContext.create()
            val formAttributes = form.asMap.map((k, v) => k -> v.toString)
            service.login(AuthenticationRequest(_request_attributes(req, formAttributes))) match {
              case org.goldenport.Consequence.Success(summary) =>
                val sessionid = summary.sessionId.getOrElse(throw new IllegalStateException("auth.login must return session id"))
                IO.pure(
                  _see_other(_login_return_to_(req.uri.query.params ++ formAttributes).getOrElse(s"/web/${app}"))
                    .addCookie(_session_cookie_(sessionid))
                )
              case org.goldenport.Consequence.Failure(c) =>
                if (_has_exact_web_route_alias_(Vector("web", app, "login")))
                  IO.pure(_see_other(_login_error_redirect_(app, c.displayMessage, _login_return_to_(req.uri.query.params ++ formAttributes))))
                else
                  _login_page(req, app, Some(c.displayMessage))
            }
          }
        } yield response
      case None =>
        _web_error_response(Some(app), HStatus.ServiceUnavailable, "Authentication service is unavailable.", s"/web/${app}/login")
    }

  private[http] def _logout_submit(
    req: org.http4s.Request[IO],
    app: String
  ): IO[HResponse[IO]] =
    _auth_service match {
      case Some(service) =>
        given ExecutionContext = ExecutionContext.create()
        service.logout(AuthenticationRequest(_request_attributes(req))) match {
          case org.goldenport.Consequence.Success(_) =>
            IO.pure(
              _see_other(s"/web/${app}/login")
                .addCookie(_expired_session_cookie)
            )
          case org.goldenport.Consequence.Failure(c) =>
            _web_error_response(Some(app), HStatus.Unauthorized, c, s"/web/${app}/logout", req.method.name)
        }
      case None =>
        IO.pure(
          _see_other(s"/web/${app}/login")
            .addCookie(_expired_session_cookie)
        )
    }

  private[http] def _current_session(
    req: org.http4s.Request[IO],
    app: String
  ): IO[HResponse[IO]] =
    _auth_service match {
      case Some(service) =>
        given ExecutionContext = ExecutionContext.create()
        service.currentSession(AuthenticationRequest(_request_attributes(req))) match {
          case org.goldenport.Consequence.Success(summary) =>
            _json(StaticFormAppRenderer.Page(summary.toJson.noSpaces))
          case org.goldenport.Consequence.Failure(c) =>
            _web_error_response(Some(app), HStatus.Unauthorized, c, s"/web/${app}/session", req.method.name)
        }
      case None =>
        _web_error_response(Some(app), HStatus.ServiceUnavailable, "Authentication service is unavailable.", s"/web/${app}/session")
    }

  private def _auth_service: Option[AuthComponent.AuthService] =
    engine.runtimeSubsystem
      .findComponent(AuthComponent.name)
      .flatMap(_.port.get[AuthComponent.AuthService])

  private def _request_attributes(
    req: org.http4s.Request[IO],
    extra: Map[String, String] = Map.empty
  ): Map[String, String] =
    _request_header_record(req).asMap.view.mapValues(_.toString).toMap ++
      req.uri.query.params.toMap ++
      extra

  private def _has_exact_web_route_alias_(
    path: Vector[String]
  ): Boolean =
    engine.webDescriptor.webRouteFor(path).isDefined

  private def _login_error_redirect_(
    app: String,
    error: String,
    returnTo: Option[String]
  ): String = {
    val base = Uri.unsafeFromString(s"/web/${_escape_path_segment_(app)}/login")
      .withQueryParam("error", error)
    returnTo.fold(base)(x => base.withQueryParam("returnTo", x)).renderString
  }

  private def _login_return_to_(
    params: Map[String, String]
  ): Option[String] =
    params.get("returnTo")
      .map(_.trim)
      .filter(_non_empty_local_web_path_)

  private def _non_empty_local_web_path_(
    path: String
  ): Boolean =
    path.nonEmpty &&
      path.startsWith("/") &&
      !path.startsWith("//")
    && !path.contains("\r")
    && !path.contains("\n")

  private[http] def _request_header_record(
    req: org.http4s.Request[IO],
    query: Record = Record.empty,
    form: Record = Record.empty
  ): Record = {
    val base = req.headers.headers.map(h => h.name.toString -> h.value).toVector
    val enriched =
      _session_id_(req, query, form) match {
        case Some(sessionid) if !base.exists { case (k, _) => k.equalsIgnoreCase("x-textus-session") } =>
          base :+ ("x-textus-session" -> sessionid)
        case _ =>
          base
      }
    Record.create(enriched)
  }

  private[http] def _session_id_(
    req: org.http4s.Request[IO],
    query: Record = Record.empty,
    form: Record = Record.empty
  ): Option[String] =
    req.headers.headers
      .collectFirst {
        case h if h.name.toString.equalsIgnoreCase("x-textus-session") => h.value
      }
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse {
        req.cookies.collectFirst {
          case cookie if cookie.name.equalsIgnoreCase(_session_cookie_name) => cookie.content
        }.map(_.trim).filter(_.nonEmpty)
      }
      .orElse(form.getString("x-textus-session").map(_.trim).filter(_.nonEmpty))
      .orElse(query.getString("x-textus-session").map(_.trim).filter(_.nonEmpty))

  private def _session_cookie_name: String =
    s"textus-session-${NamingConventions.toNormalizedSegment(engine.runtimeSubsystem.name)}"

  private def _session_cookie_(
    sessionid: String
  ): ResponseCookie =
    ResponseCookie(
      name = _session_cookie_name,
      content = sessionid,
      path = Some("/"),
      httpOnly = true,
      sameSite = Some(SameSite.Lax)
    )

  private def _expired_session_cookie: ResponseCookie =
    ResponseCookie(
      name = _session_cookie_name,
      content = "",
      path = Some("/"),
      httpOnly = true,
      sameSite = Some(SameSite.Lax),
      maxAge = Some(0L)
    )

  private def _escape_html_(p: String): String =
    Option(p).getOrElse("")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  private def _escape_path_segment_(p: String): String =
    java.net.URLEncoder.encode(Option(p).getOrElse(""), StandardCharsets.UTF_8)

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
    val runtimeConfig = RuntimeConfig.from(engine.runtimeSubsystem.configuration)
    val subject = _web_authorization_subject(req, runtimeConfig)
    val rule = engine.webDescriptor.authorization
      .get(selector)
      .orElse(
        operationSelector
          .orElse(_admin_operation_selector(app, service, operation))
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

  private def _show_runtime_landing: Boolean =
    RuntimeConfig.from(engine.runtimeSubsystem.configuration).operationMode != org.goldenport.cncf.config.OperationMode.Production

  private def _dispatch_component_segment(
    app: String
  ): String =
    engine.runtimeSubsystem
      .findComponent(app)
      .map(_.name)
      .map(NamingConventions.toNormalizedSegment)
      .getOrElse(app)

  private def _admin_operation_selector(
    app: String,
    service: String,
    operation: String
  ): Option[String] =
    service match {
      case "admin" =>
        Some(if (app == "system") "admin.config.show" else "admin.entity.list")
      case "admin.jobs" =>
        Some("admin.execution.history")
      case "admin.entities" =>
        Some(if (operation == "index") "admin.entity.list" else "admin.entity.read")
      case "admin.data" =>
        Some(if (operation == "index") "admin.data.list" else "admin.data.read")
      case "admin.views" => Some("admin.view.read")
      case "admin.aggregates" => Some("admin.aggregate.read")
      case "admin.blobs" =>
        Some(if (operation == "index") "admin.entity.list" else "admin.entity.read")
      case "admin.associations" => Some("admin.entity.read")
      case "admin.store" => Some("admin.entity.read")
      case _ => None
    }

  private def _web_authorization_subject(
    req: Option[org.http4s.Request[IO]],
    runtimeConfig: RuntimeConfig
  ): WebDescriptorAuthorization.Subject =
    if (runtimeConfig.operationMode == OperationMode.Production)
      req.flatMap(_web_authorization_subject_from_session)
        .getOrElse(WebDescriptorAuthorization.Subject())
    else
      req.map { r =>
        val subject = WebDescriptorAuthorization.Subject.fromHttp(r)
        if (!subject.anonymous || _session_id_(r).isEmpty)
          subject
        else
          subject.copy(anonymous = false)
      }.getOrElse(WebDescriptorAuthorization.Subject())

  private def _web_authorization_subject_from_session(
    req: org.http4s.Request[IO]
  ): Option[WebDescriptorAuthorization.Subject] =
    _session_id_(req).flatMap { sessionid =>
      _auth_service.flatMap { service =>
        given ExecutionContext = ExecutionContext.create()
        service.currentSession(_session_authentication_request(sessionid)) match {
          case org.goldenport.Consequence.Success(summary) if summary.authenticated =>
            Some(_web_authorization_subject(summary))
          case _ =>
            None
        }
      }
    }

  private def _session_authentication_request(
    sessionid: String
  ): AuthenticationRequest =
    AuthenticationRequest(Map("x-textus-session" -> sessionid))

  private def _web_authorization_subject(
    summary: AuthComponent.SessionSummary
  ): WebDescriptorAuthorization.Subject = {
    val attributes = summary.attributes
    WebDescriptorAuthorization.Subject(
      roles = _web_authorization_tokens(attributes, "role", "roles"),
      scopes = _web_authorization_tokens(attributes, "scope", "scopes"),
      capabilities = summary.capabilities.toSet ++ _web_authorization_tokens(attributes, "capability", "capabilities"),
      privileges = _web_authorization_tokens(attributes, "privilege", "privileges") ++ Set(summary.securityLevel),
      anonymous = !summary.authenticated,
      authenticated = summary.authenticated,
      providerAuthenticated = summary.authenticated
    )
  }

  private def _web_authorization_tokens(
    attributes: Map[String, String],
    keys: String*
  ): Set[String] = {
    val targets = keys.map(_.toLowerCase(java.util.Locale.ROOT)).toSet
    attributes.iterator
      .collect {
        case (key, value) if targets.contains(key.toLowerCase(java.util.Locale.ROOT)) => value
      }
      .flatMap(_.split("[,\\s|]+"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSet
  }

  private def _forbidden_error(
    req: org.http4s.Request[IO],
    component: Option[String],
    service: Option[String],
    operation: Option[String]
  ): StructuredHttpError =
    StructuredHttpError.fromConclusion(
      Conclusion.securityPermissionDenied("Forbidden"),
      HStatus.Forbidden.code,
      req.uri.path.renderString,
      req.method.name,
      _operation_mode,
      component = component,
      service = service,
      operation = operation
    )

  private def _forbidden_web(
    req: org.http4s.Request[IO],
    component: Option[String],
    service: Option[String],
    operation: Option[String]
  ): IO[HResponse[IO]] = {
    val error = _forbidden_error(req, component, service, operation)
    _web_error_response(component, HStatus.Forbidden, error)
  }

  private def _forbidden_api(
    req: org.http4s.Request[IO],
    component: Option[String],
    service: Option[String],
    operation: Option[String]
  ): IO[HResponse[IO]] =
    _structured_error_response(req, _forbidden_error(req, component, service, operation))

  private[http] def _web_error_response(
    app: Option[String],
    status: HStatus,
    message: String,
    path: String
  ): IO[HResponse[IO]] = {
    val error = StructuredHttpError.fromMessage(
      message,
      status.code,
      path,
      "GET",
      _operation_mode,
      component = app
    )
    _web_error_response(app, status, error)
  }

  private[http] def _web_error_response(
    app: Option[String],
    status: HStatus,
    conclusion: Conclusion,
    path: String,
    method: String = "GET"
  ): IO[HResponse[IO]] = {
    val error = StructuredHttpError.fromConclusion(
      conclusion,
      status.code,
      path,
      method,
      _operation_mode,
      component = app
    )
    _web_error_response(app, status, error)
  }

  private def _web_error_response(
    app: Option[String],
    status: HStatus,
    error: StructuredHttpError
  ): IO[HResponse[IO]] =
    _web_error_template(app, status.code) match {
      case Some(template) =>
        _html_status(
          StaticFormAppRenderer.renderErrorTemplate(app, status.code, error.message, error.path, Some(error), template),
          status
        )
      case None =>
        _html_status(StaticFormAppRenderer.renderStructuredErrorPage(app, error), status)
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
      "paging.href" -> s"/form/${continuation.app}/${continuation.service}/${continuation.operation}/continue/${continuation.id}?page={page}&pageSize={pageSize}${_continuation_total_query(continuation)}"
    )

  private def _continuation_total_query(
    continuation: Http4sHttpServer.FormContinuation
  ): String =
    if (_form_boolean(continuation.form, "paging.includeTotal") || _form_boolean(continuation.form, "includeTotal"))
      "&includeTotal=true"
    else
      ""

  private def _form_chunk_size(form: Record): Int =
    form.getString("paging.chunkSize").flatMap(_.toIntOption).getOrElse(1000)

  private def _form_boolean(
    form: Record,
    key: String
  ): Boolean =
    form.getString(key).exists(_is_truthy)

  private def _form_values(form: Record): Map[String, String] =
    _strip_security_form_values(form.asMap)
      .map { case (k, v) => k -> v.toString }

  private def _operation_form_values(form: Record): Map[String, String] =
    _strip_framework_form_values(form.asMap)
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
    _html(p, None)

  private def _html(
    p: StaticFormAppRenderer.Page,
    appName: Option[String]
  ): IO[HResponse[IO]] =
    _html_content(p.body, appName)

  private def _html_content(
    body: String,
    appName: Option[String]
  ): IO[HResponse[IO]] =
    _html_content(body, appName, None)

  private def _html_content(
    body: String,
    appName: Option[String],
    componentName: Option[String]
  ): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(_themed_html(body, appName, componentName))
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )

  private def _html_status(p: StaticFormAppRenderer.Page, status: HStatus): IO[HResponse[IO]] =
    _html_status(p, status, None)

  private def _html_status(
    p: StaticFormAppRenderer.Page,
    status: HStatus,
    appName: Option[String]
  ): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](status)
        .withEntity(_themed_html(p.body, appName, None))
        .withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    )

  private def _themed_html(
    body: String,
    appName: Option[String],
    componentName: Option[String] = None
  ): String = {
    val descriptor = engine.webDescriptor
    val baseAssets = appName
      .map(name => descriptor.assets.merge(descriptor.appAssets(name)))
      .getOrElse(descriptor.assets)
    val customized = _page_customized_html(body, componentName, appName)
    val themed = StaticFormAppLayout.completeThemeAssets(
      customized,
      descriptor.themeFor(appName).toLayoutOptions
    )
    StaticFormAppLayout.completeDeclaredAssets(
      themed,
      StaticFormAppLayout.AssetCompletionOptions(
        declaredCss = baseAssets.css,
        declaredJs = baseAssets.js
      )
    )
  }

  private def _page_customized_html(
    body: String,
    componentName: Option[String],
    appName: Option[String]
  ): String =
    engine.webDescriptor.pageCustomization(componentName, appName) match {
      case Some(page) =>
        _insert_before(
          body,
          "</body>",
          _page_customization_script(page)
        )
      case None =>
        body
    }

  private def _page_customization_script(
    page: WebDescriptor.PageCustomization
  ): String = {
    val json = _page_customization_json(page).noSpaces.replace("</", "<\\/")
    s"""|  <script id="textus-page-customization" type="application/json">${json}</script>
        |  <script>
        |    (function() {
        |      const source = document.getElementById("textus-page-customization");
        |      if (!source) return;
        |      let config = {};
        |      try { config = JSON.parse(source.textContent || "{}"); } catch (_) { return; }
        |
        |      function text(role, value) {
        |        if (!value) return;
        |        document.querySelectorAll("[data-textus-role='" + role + "']").forEach(function(node) {
        |          node.textContent = value;
        |        });
        |      }
        |
        |      function esc(name) {
        |        if (window.CSS && CSS.escape) return CSS.escape(name);
        |        return String(name).replace(/["'\\\\\\]]/g, "\\\\$$&");
        |      }
        |
        |      function input(name) {
        |        return document.querySelector("[name='" + esc(name) + "']");
        |      }
        |
        |      function applyControl(name, control) {
        |        const field = document.querySelector("[data-textus-field='" + esc(name) + "']");
        |        const element = input(name);
        |        if (control.label && field) {
        |          const label = field.querySelector("label");
        |          if (label) label.textContent = control.label;
        |        }
        |        if (control.help && field) {
        |          const help = field.querySelector(".form-text");
        |          if (help) help.textContent = control.help;
        |        }
        |        if (control.placeholder && element) element.setAttribute("placeholder", control.placeholder);
        |        if (control.defaultValue !== undefined && control.defaultValue !== null && element && !element.value) element.value = control.defaultValue;
        |      }
        |
        |      function fieldRequired(name) {
        |        const element = input(name);
        |        return !!(element && element.required);
        |      }
        |
        |      function hideField(name) {
        |        const field = document.querySelector("[data-textus-field='" + esc(name) + "']");
        |        if (field) field.classList.add("d-none");
        |      }
        |
        |      function showField(name) {
        |        const field = document.querySelector("[data-textus-field='" + esc(name) + "']");
        |        if (field) field.classList.remove("d-none");
        |      }
        |
        |      function reorder(fields) {
        |        if (!fields || !fields.length) return;
        |        const first = document.querySelector("[data-textus-field]");
        |        if (!first || !first.parentElement) return;
        |        const parent = first.parentElement;
        |        fields.forEach(function(name) {
        |          const field = document.querySelector("[data-textus-field='" + esc(name) + "']");
        |          if (field && field.parentElement === parent) parent.appendChild(field);
        |        });
        |      }
        |
        |      if (config.title) document.title = config.title;
        |      text("heading", config.heading);
        |      text("subtitle", config.subtitle);
        |      text("submit", config.submitLabel);
        |
        |      const controls = config.controls || {};
        |      Object.keys(controls).forEach(function(name) { applyControl(name, controls[name] || {}); });
        |      if (config.fields && config.fields.length) {
        |        const allowed = new Set(config.fields);
        |        document.querySelectorAll("[data-textus-field]").forEach(function(field) {
        |          const name = field.getAttribute("data-textus-field");
        |          if (allowed.has(name)) {
        |            showField(name);
        |          } else {
        |            const control = controls[name] || {};
        |            if (!fieldRequired(name) || (control.defaultValue !== undefined && control.defaultValue !== null)) {
        |              applyControl(name, control);
        |              hideField(name);
        |            }
        |          }
        |        });
        |        reorder(config.fields);
        |      }
        |    })();
        |  </script>
        |""".stripMargin
  }

  private def _page_customization_json(
    page: WebDescriptor.PageCustomization
  ): Json =
    Json.obj(
      "title" -> _json_option(page.title),
      "heading" -> _json_option(page.heading),
      "subtitle" -> _json_option(page.subtitle),
      "submitLabel" -> _json_option(page.submitLabel),
      "fields" -> Json.arr(page.fields.map(Json.fromString)*),
      "controls" -> Json.obj(page.controls.toVector.sortBy(_._1).map {
        case (name, control) => name -> _form_control_json(control)
      }*)
    )

  private def _form_control_json(
    control: WebDescriptor.FormControl
  ): Json = {
    val optional = Vector(
      control.label.map("label" -> Json.fromString(_)),
      control.help.map("help" -> Json.fromString(_)),
      control.placeholder.map("placeholder" -> Json.fromString(_)),
      control.defaultValue.map("defaultValue" -> Json.fromString(_))
    ).flatten
    Json.obj(
      (optional :+ ("hidden" -> Json.fromBoolean(control.hidden)))*
    )
  }

  private def _json_option(value: Option[String]): Json =
    value.map(Json.fromString).getOrElse(Json.Null)

  private def _insert_before(
    html: String,
    marker: String,
    insertion: String
  ): String = {
    val index = html.toLowerCase(java.util.Locale.ROOT).lastIndexOf(marker.toLowerCase(java.util.Locale.ROOT))
    if (index < 0)
      html + "\n" + insertion
    else
      html.substring(0, index) + insertion + html.substring(index)
  }

  private def _json(p: StaticFormAppRenderer.Page): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(p.body)
        .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`)))
    )

  private def _error_json_response(
    error: StructuredHttpError
  ): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.fromInt(error.status).getOrElse(HStatus.InternalServerError))
        .withEntity(error.envelopeJson)
        .withContentType(`Content-Type`(MediaType.application.json, Some(Charset.`UTF-8`)))
    )

  private def _error_yaml_response(
    error: StructuredHttpError
  ): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.fromInt(error.status).getOrElse(HStatus.InternalServerError))
        .withEntity(error.envelopeYaml)
        .withContentType(`Content-Type`(_yaml_media_type, Some(Charset.`UTF-8`)))
    )

  private def _structured_error_response(
    req: org.http4s.Request[IO],
    error: StructuredHttpError
  ): IO[HResponse[IO]] =
    if (_prefers_yaml(Some(req)))
      _error_yaml_response(error)
    else if (_prefers_plain_text(Some(req)))
      IO.pure(
        HResponse[IO](HStatus.fromInt(error.status).getOrElse(HStatus.InternalServerError))
          .withEntity(error.message)
          .withContentType(`Content-Type`(MediaType.text.plain, Some(Charset.`UTF-8`)))
      )
    else
      _error_json_response(error)

  private def _not_found_api_response(
    req: org.http4s.Request[IO],
    message: String,
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): IO[HResponse[IO]] =
    _structured_error_response(
      req,
      StructuredHttpError.fromMessage(
        message,
        HStatus.NotFound.code,
        req.uri.path.renderString,
        req.method.name,
        _operation_mode,
        component = component,
        service = service,
        operation = operation
      )
    )

  private def _yaml_media_type: MediaType =
    MediaType.parse("application/yaml").fold(_ => MediaType.text.plain, identity)

  private def _operation_mode: OperationMode =
    RuntimeConfig.from(engine.runtimeSubsystem.configuration).operationMode

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

  private def _to_form_record(
    req: org.http4s.Request[IO]
  ): IO[Record] =
    if (_is_multipart(req.headers.get[`Content-Type`]))
      _to_multipart_form_record(req)
    else
      _to_plain_form_record(req)

  private def _to_multipart_form_record(
    req: org.http4s.Request[IO]
  ): IO[Record] =
    _multipart_form_entries(req).map { values =>
      if (values.isEmpty) Record.empty else Record.create(values)
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
    req: org.http4s.Request[IO],
    pathOverride: Option[String] = None
  ): IO[HttpRequest] = {
    val method = req.method match {
      case org.http4s.Method.GET => HttpRequest.GET
      case org.http4s.Method.POST => HttpRequest.POST
      case org.http4s.Method.PUT => HttpRequest.PUT
      case org.http4s.Method.DELETE => HttpRequest.DELETE
      case _ => HttpRequest.GET
    }
    val query = Record.create(req.uri.query.params.toVector)
    val header = _request_header_record(req)
    val context = HttpContext(
      scheme = req.uri.scheme.map(_.value),
      authority = req.uri.authority.map(_.renderString),
      originalUri = Some(req.uri.renderString)
    )
    val path = pathOverride.getOrElse(req.uri.path.renderString)
    val contentTypeHeader = req.headers.get[`Content-Type`]

    if (_is_multipart(contentTypeHeader))
      _to_multipart_http_request(req, method, path, query, header, context)
    else
      _to_regular_http_request(req, method, path, query, header, context, contentTypeHeader)
  }

  private def _is_multipart(contentType: Option[`Content-Type`]): Boolean =
    contentType.exists { header =>
      header.mediaType.mainType.equalsIgnoreCase("multipart") &&
        header.mediaType.subType.equalsIgnoreCase("form-data")
    }

  private def _to_regular_http_request(
    req: org.http4s.Request[IO],
    method: HttpRequest.Method,
    path: String,
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
        path = path,
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
    path: String,
    query: Record,
    header: Record,
    context: HttpContext
  ): IO[HttpRequest] =
    _multipart_form_entries(req).map { values =>
      val form =
        if (values.isEmpty) Record.empty else Record.create(values)
      HttpRequest.fromPath(
        method = method,
        path = path,
        query = query,
        header = header,
        body = None,
        context = context,
        form = form
      )
    }

  private def _multipart_form_entries(
    req: org.http4s.Request[IO]
  ): IO[Vector[(String, Any)]] =
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
              if (_is_multipart_upload_part(name, part.filename)) {
                val body = Vector(name -> MimeBody(contentType, bag))
                val filename = part.filename.toVector.filter(_.nonEmpty).map(x => s"$name.filename" -> x)
                body ++ filename
              } else {
                Vector(name -> new String(bytes.toArray, StandardCharsets.UTF_8))
              }
            }
          case _ =>
            IO.pure(Vector.empty)
        }
      }.map(_.flatten)
    }

  private def _is_multipart_upload_part(
    name: String,
    filename: Option[String]
  ): Boolean =
    filename.exists(_.nonEmpty) ||
      _is_legacy_blob_upload_field(name) ||
      _is_image_attachment_file_field(name)

  private def _is_legacy_blob_upload_field(name: String): Boolean = {
    val prefix = "blob."
    if (!name.startsWith(prefix))
      false
    else {
      val suffix = name.drop(prefix.length)
      val parts = suffix.split("\\.").toVector
      parts match {
        case Vector(role) if role.nonEmpty =>
          true
        case Vector(role, index) if role.nonEmpty && index.forall(_.isDigit) =>
          true
        case _ =>
          false
      }
    }
  }

  private def _is_image_attachment_file_field(name: String): Boolean = {
    val prefix = "imageAttachments."
    if (!name.startsWith(prefix) || !name.endsWith(".file"))
      false
    else {
      val index = name.drop(prefix.length).stripSuffix(".file")
      index.nonEmpty && index.forall(_.isDigit)
    }
  }

  private def _to_http_response(
    res: HttpResponse
  ): IO[org.http4s.Response[IO]] =
    _to_http_response_with_metadata(res, None, None, None, None, RuntimeContext.ExecutionMetadata.empty)

  private def _to_http_execution_response(
    result: HttpExecutionResult,
    req: Option[org.http4s.Request[IO]],
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): IO[org.http4s.Response[IO]] =
    _to_http_response_with_metadata(result.response, req, component, service, operation, result.metadata)

  private def _to_http_response(
    res: HttpResponse,
    req: Option[org.http4s.Request[IO]],
    component: Option[String] = None,
    service: Option[String] = None,
    operation: Option[String] = None
  ): IO[org.http4s.Response[IO]] =
    _to_http_response_with_metadata(res, req, component, service, operation, RuntimeContext.ExecutionMetadata.empty)

  private def _to_http_response_with_metadata(
    res: HttpResponse,
    req: Option[org.http4s.Request[IO]],
    component: Option[String],
    service: Option[String],
    operation: Option[String],
    metadata: RuntimeContext.ExecutionMetadata
  ): IO[org.http4s.Response[IO]] = {
    val status = res.code match {
      case 200 => HStatus.Ok
      case 400 => HStatus.BadRequest
      case 401 => HStatus.Unauthorized
      case 403 => HStatus.Forbidden
      case 404 => HStatus.NotFound
      case _ => HStatus.InternalServerError
    }
    val mime = MediaType.parse(res.mime.value).fold(_ => MediaType.text.plain, identity)
    val charset: Option[org.http4s.Charset] =
      res.charset.map(c => org.http4s.Charset.fromNioCharset(c))
    val contentType = `Content-Type`(mime, charset)
    res.getBinary match {
      case Some(binary) if res.code < 400 =>
        return IO.pure(
          _with_response_headers(_with_job_id_header(HResponse[IO](status), metadata), res)
            .withBodyStream(fs2.io.readInputStream(IO(binary.openInputStream()), 8192, closeAfterUse = true))
            .withContentType(contentType)
        )
      case _ =>
        ()
    }
    val body = res.getString.getOrElse("")
    if (res.code >= 400 && _is_structured_error_body(res, body)) {
      val mime = MediaType.parse(res.mime.value).fold(_ => MediaType.application.json, identity)
      val charset: Option[org.http4s.Charset] =
        res.charset.map(c => org.http4s.Charset.fromNioCharset(c))
      IO.pure(
        _with_job_id_header(HResponse[IO](status), metadata)
          .withEntity(body)
          .withContentType(`Content-Type`(mime, charset))
      )
    } else if (res.code >= 400 && !_prefers_plain_text(req)) {
      val error = StructuredHttpError.fromMessage(
        if (body.trim.nonEmpty) body else s"HTTP ${res.code}",
        status.code,
        req.map(_.uri.path.renderString).getOrElse(""),
        req.map(_.method.name).getOrElse(""),
        _operation_mode,
        component = component,
        service = service,
        operation = operation
      )
      if (_prefers_yaml(req))
        _error_yaml_response(error)
      else
        _error_json_response(error)
    } else {
      IO.pure(
        _with_response_headers(_with_job_id_header(HResponse[IO](status), metadata), res)
          .withEntity(body)
          .withContentType(contentType)
      )
    }
  }

  private def _with_response_headers(
    response: HResponse[IO],
    res: HttpResponse
  ): HResponse[IO] =
    res.header.fields.foldLeft(response) { (z, field) =>
      z.putHeaders(Header.Raw(CIString(field.key), field.value.single.toString))
    }

  private def _with_job_id_header(
    response: HResponse[IO],
    metadata: RuntimeContext.ExecutionMetadata
  ): HResponse[IO] =
    metadata.responseJobId.orElse(metadata.debugJobId) match {
      case Some(jobid) if jobid.nonEmpty =>
        response.putHeaders(Header.Raw(CIString("X-Textus-Job-Id"), jobid))
      case _ =>
        response
    }

  private def _prefers_yaml(
    req: Option[org.http4s.Request[IO]]
  ): Boolean =
    _accept_header(req).exists { value =>
      val lower = value.toLowerCase(java.util.Locale.ROOT)
      lower.contains("application/yaml") || lower.contains("text/yaml") || lower.contains("application/x-yaml")
    }

  private def _prefers_plain_text(
    req: Option[org.http4s.Request[IO]]
  ): Boolean =
    _accept_header(req).exists { value =>
      val lower = value.toLowerCase(java.util.Locale.ROOT)
      lower.contains("text/plain") &&
        !lower.contains("application/json") &&
        !lower.contains("application/yaml") &&
        !lower.contains("text/yaml")
    }

  private def _accept_header(
    req: Option[org.http4s.Request[IO]]
  ): Option[String] =
    req.flatMap(_.headers.headers.find(_.name.toString.equalsIgnoreCase("Accept")).map(_.value))

  private def _is_structured_error_body(
    res: HttpResponse,
    body: String
  ): Boolean =
    res.mime.value.toLowerCase(java.util.Locale.ROOT).contains("json") &&
      body.contains("\"error\"")

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
    params.get(key).exists(_is_truthy)

  private def _is_truthy(value: String): Boolean =
    value.trim.toLowerCase match {
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
