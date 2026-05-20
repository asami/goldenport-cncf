package org.goldenport.cncf.http

/*
 * @since   May. 18, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*
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
import org.goldenport.cncf.blob.{BlobStoreFactory, BlobStorageRef}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.{JobId, JobQueryReadModel, JobStatus}
import org.goldenport.cncf.observability.{ConclusionDiagnostics, DiagnosticPayloadReferenceCodec, DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.cncf.mcp.McpJsonRpcAdapter
import org.goldenport.cncf.openapi.OpenApiProjector
import org.goldenport.cncf.security.{AuthenticationRequest, IngressSecurityResolver}
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
import org.goldenport.observation.{Cause, Descriptor}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 21, 2026
 *  version Mar. 29, 2026
 *  version Apr. 30, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class Http4sHttpServer(
  engine: HttpExecutionEngine,
  port: Int = Http4sHttpServer.defaultPort,
  operationDispatcherOption: Option[WebOperationDispatcher] = None
) extends HttpServer(engine) {
  private val _bind_host = Host.fromString("0.0.0.0").get
  private val _form_continuations = TrieMap.empty[String, Http4sHttpServer.FormContinuation]
  private val _application_job_seen_at = TrieMap.empty[(String, String), Instant]
  private val _operation_dispatcher =
    operationDispatcherOption.getOrElse(WebOperationDispatcher.create(engine))
  private val _mcp = new McpJsonRpcAdapter(engine.runtimeSubsystem)
  private val _runtime_config = RuntimeConfig.from(engine.runtimeSubsystem.configuration)
  private val _static_form_app_renderer =
    new StaticFormAppRenderer(_runtime_config.staticFormAppRendererConfig)
  private final case class WebTemplateComposition(
    html: String,
    appliedLayout: Boolean
  )
  private enum WebTemplatePartScope {
    case Default
    case ComponentContent
    case SubsystemShell
  }

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
      case GET -> Root / "favicon.ico" =>
        _favicon()
      case GET -> Root / "web" / "favicon.ico" =>
        _favicon()
      case GET -> Root / "web" / "assets" / "bootstrap.min.css" =>
        _bootstrap_css()
      case GET -> Root / "web" / "assets" / "bootstrap.bundle.min.js" =>
        _bootstrap_bundle_js()
      case GET -> Root / "web" / "assets" / "textus-widgets.css" =>
        _textus_widgets_css()
      case GET -> Root / "web" / "assets" / "textus-widgets.js" =>
        _textus_widgets_js()
      case GET -> Root / "web" / "assets" / "textus-calltree.js" =>
        _textus_calltree_js()
      case GET -> Root / "web" / "assets" / "textus-form-debug.js" =>
        _textus_form_debug_js()
      case req @ GET -> _ if _web_global_asset_path(req).nonEmpty =>
        _web_global_asset(_web_global_asset_path(req).get)
      case req @ GET -> Root / "web" / "blob" / "content" / id =>
        _blob_content(req, id)
      case req @ GET -> Root / "web" / "system" / "dashboard" =>
        if (_is_web_authorized("system", "dashboard", "index", Some(req), Some("admin.system.dashboard"))) _subsystem_dashboard() else _forbidden_web(req, Some("system"), Some("dashboard"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "dashboard" / "state" =>
        if (_is_web_authorized("system", "dashboard", "state", Some(req), Some("admin.system.dashboard"))) _dashboard_state(None) else _forbidden_web(req, Some("system"), Some("dashboard"), Some("state"))
      case req @ GET -> Root / "web" / "system" / "performance" =>
        if (_is_web_authorized("system", "performance", "index", Some(req), Some("admin.system.performance"))) _system_performance() else _forbidden_web(req, Some("system"), Some("performance"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "document" =>
        if (_is_web_authorized("system", "document", "index", Some(req), Some("admin.system.document"))) _system_document() else _forbidden_web(req, Some("system"), Some("document"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "document" / "specification" =>
        if (_is_web_authorized("system", "document", "specification", Some(req), Some("admin.system.document"))) _system_manual() else _forbidden_web(req, Some("system"), Some("document"), Some("specification"))
      case req @ GET -> Root / "web" / "system" / "document" / "specification" / "openapi.json" =>
        if (_is_web_authorized("system", "document", "openapi", Some(req), Some("admin.system.document"))) _system_manual_openapi() else _forbidden_web(req, Some("system"), Some("document"), Some("openapi"))
      case req @ GET -> Root / "web" / app / "login" =>
        _web_route_alias(req, Vector("web", app, "login")).flatMap {
          case Some(response) => IO.pure(response)
          case None => _login_page(req, app)
        }
      case req @ GET -> Root / "web" / app / "signup" =>
        _web_route_alias(req, Vector("web", app, "signup")).flatMap {
          case Some(response) => IO.pure(response)
          case None => _component_web_app_or_static_form_app(app, "signup", Some(req))
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
        if (_is_web_authorized("system", "admin", "index", Some(req), Some("admin.system.index"))) _system_admin() else _forbidden_web(req, Some("system"), Some("admin"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "descriptor" =>
        if (_is_web_authorized("system", "admin", "descriptor", Some(req), Some("admin.system.descriptor"))) _system_admin_descriptor() else _forbidden_web(req, Some("system"), Some("admin"), Some("descriptor"))
      case req @ GET -> Root / "web" / "system" / "admin" / "assembly" / "warnings" =>
        if (_is_web_authorized("system", "admin.assembly", "warnings", Some(req), Some("admin.system.assembly"))) _system_admin_assembly_warnings() else _forbidden_web(req, Some("system"), Some("admin.assembly"), Some("warnings"))
      case req @ GET -> Root / "web" / "system" / "admin" / "assembly" / "report" =>
        if (_is_web_authorized("system", "admin.assembly", "report", Some(req), Some("admin.system.assembly"))) _system_admin_assembly_report() else _forbidden_web(req, Some("system"), Some("admin.assembly"), Some("report"))
      case req @ GET -> Root / "web" / "system" / "admin" / "jobs" =>
        if (_is_web_authorized("system", "admin.jobs", "index", Some(req))) _system_admin_jobs() else _forbidden_web(req, Some("system"), Some("admin.jobs"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "jobs" / jobId =>
        if (_is_web_authorized("system", "admin.jobs", jobId, Some(req))) _system_admin_job(req, jobId) else _forbidden_web(req, Some("system"), Some("admin.jobs"), Some(jobId))
      case req @ GET -> Root / "web" / "system" / "admin" / "knowledge" =>
        if (_is_web_authorized("system", "admin.knowledge", "index", Some(req), Some("admin.system.knowledge"))) _system_admin_knowledge() else _forbidden_web(req, Some("system"), Some("admin.knowledge"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "knowledge" / component =>
        if (_is_web_authorized("system", "admin.knowledge", component, Some(req), Some("admin.system.knowledge"))) _system_admin_knowledge_component(component) else _forbidden_web(req, Some("system"), Some("admin.knowledge"), Some(component))
      case req @ GET -> Root / "web" / "system" / "admin" / "knowledge" / component / "nodes" / nodeId =>
        if (_is_web_authorized("system", "admin.knowledge", "node", Some(req), Some("admin.system.knowledge"))) _system_admin_knowledge_node(component, nodeId) else _forbidden_web(req, Some("system"), Some("admin.knowledge"), Some("node"))
      case req @ GET -> Root / "web" / "system" / "admin" / "information" =>
        if (_is_web_authorized("system", "admin.information", "index", Some(req), Some("admin.system.information"))) _system_admin_information() else _forbidden_web(req, Some("system"), Some("admin.information"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "information" / component =>
        if (_is_web_authorized("system", "admin.information", component, Some(req), Some("admin.system.information"))) _system_admin_information_component(component) else _forbidden_web(req, Some("system"), Some("admin.information"), Some(component))
      case req @ GET -> Root / "web" / "system" / "admin" / "observability" =>
        if (_is_web_authorized("system", "admin.observability", "index", Some(req), Some("admin.system.observability"))) _system_admin_observability() else _forbidden_web(req, Some("system"), Some("admin.observability"), Some("index"))
      case req @ GET -> Root / "web" / "system" / "admin" / "observability" / "metrics" =>
        if (_is_web_authorized("system", "admin.observability", "metrics", Some(req), Some("admin.system.observability"))) _system_admin_observability_metrics() else _forbidden_web(req, Some("system"), Some("admin.observability"), Some("metrics"))
      case req @ GET -> Root / "web" / "system" / "admin" / "observability" / "diagnostics" =>
        if (_is_web_authorized("system", "admin.observability", "diagnostics", Some(req), Some("admin.system.observability"))) _system_admin_observability_diagnostics() else _forbidden_web(req, Some("system"), Some("admin.observability"), Some("diagnostics"))
      case req @ GET -> Root / "web" / "system" / "admin" / "observability" / "diagnostics" / scope / diagnosticKey =>
        if (_is_web_authorized("system", "admin.observability", "diagnostics", Some(req), Some("admin.system.observability"))) _system_admin_observability_diagnostic(scope, diagnosticKey) else _forbidden_web(req, Some("system"), Some("admin.observability"), Some("diagnostics"))
      case req @ GET -> Root / "web" / "system" / "admin" / "observability" / "payloads" / id =>
        if (_is_web_authorized("system", "admin.observability", "payloads", Some(req), Some("admin.system.observability"))) _system_admin_observability_payload(id) else _forbidden_web(req, Some("system"), Some("admin.observability"), Some("payloads"))
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
      case req @ GET -> Root / "web" / "admin" =>
        if (_is_web_authorized("admin", "application", "index", Some(req), Some("admin.application.index"))) _application_admin() else _forbidden_web(req, Some("admin"), Some("application"), Some("index"))
      case req @ GET -> Root / "web" / "admin" / "associations" =>
        if (_is_web_authorized("admin", "associations", "index", Some(req), Some("admin.entity.read"))) _admin_associations(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("index"))
      case req @ POST -> Root / "web" / "admin" / "associations" / "attach" =>
        if (_is_web_authorized("admin", "associations", "attach", Some(req), Some("admin.entity.update"))) _admin_association_attach(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("attach"))
      case req @ POST -> Root / "web" / "admin" / "associations" / "detach" =>
        if (_is_web_authorized("admin", "associations", "detach", Some(req), Some("admin.entity.update"))) _admin_association_detach(req) else _forbidden_web(req, Some("admin"), Some("associations"), Some("detach"))
      case req @ GET -> Root / "web" / "admin" / "tags" =>
        if (_is_web_authorized("admin", "tags", "index", Some(req), Some("admin.entity.read"))) _admin_tags(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("index"))
      case req @ POST -> Root / "web" / "admin" / "tags" / "create" =>
        if (_is_web_authorized("admin", "tags", "create", Some(req), Some("admin.entity.update"))) _admin_tag_create(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("create"))
      case req @ POST -> Root / "web" / "admin" / "tags" / "update" =>
        if (_is_web_authorized("admin", "tags", "update", Some(req), Some("admin.entity.update"))) _admin_tag_update(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("update"))
      case req @ POST -> Root / "web" / "admin" / "tags" / "move" =>
        if (_is_web_authorized("admin", "tags", "move", Some(req), Some("admin.entity.update"))) _admin_tag_move(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("move"))
      case req @ POST -> Root / "web" / "admin" / "tags" / "attach" =>
        if (_is_web_authorized("admin", "tags", "attach", Some(req), Some("admin.entity.update"))) _admin_tag_attach(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("attach"))
      case req @ POST -> Root / "web" / "admin" / "tags" / "detach" =>
        if (_is_web_authorized("admin", "tags", "detach", Some(req), Some("admin.entity.update"))) _admin_tag_detach(req) else _forbidden_web(req, Some("admin"), Some("tags"), Some("detach"))
      case GET -> Root / "web" / app / "dashboard" / "state" =>
        _dashboard_state(Some(app))
      case req @ GET -> Root / "web" / app / "jobs" =>
        if (_is_web_authorized(app, "jobs", "index", Some(req))) _application_jobs(req, app) else _forbidden_web(req, Some(app), Some("jobs"), Some("index"))
      case req @ GET -> Root / "web" / app / "jobs" / jobId =>
        if (_is_web_authorized(app, "jobs", jobId, Some(req))) _application_job(req, app, jobId) else _forbidden_web(req, Some(app), Some("jobs"), Some(jobId))
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
      case req @ GET -> Root / "web" / app / "admin" / page =>
        engine.webDescriptor.adminPage(app, page) match {
          case Some(entry) =>
            if (_is_web_authorized(app, "admin.pages", entry.normalizedName, Some(req), Some(entry.effectivePermission)))
              _component_admin_page(app, entry)
            else
              _forbidden_web(req, Some(app), Some("admin.pages"), Some(entry.normalizedName))
          case None =>
            IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component admin page not found"))
        }
      case GET -> Root / "web" / app / "document" =>
        _component_document(app)
      case GET -> Root / "web" / app / "document" / "specification" =>
        _component_manual(app)
      case GET -> Root / "web" / app / "document" / "specification" / service =>
        _component_manual_service(app, service)
      case GET -> Root / "web" / app / "document" / "specification" / service / operation =>
        _component_manual_operation(app, service, operation)
      case GET -> Root / "web" / app / "document" / documentPath =>
        _component_document_asset(app, Vector(documentPath))
      case GET -> Root / "web" / app / "document" / documentPath / documentName =>
        _component_document_asset(app, Vector(documentPath, documentName))
      case req @ GET -> Root / "web" =>
        _web_route_alias(req, Vector("web")).flatMap {
          case Some(response) => IO.pure(response)
          case None =>
            if (_show_runtime_landing)
              _runtime_landing()
            else
              IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Web app route not found"))
        }
      case GET -> Root / "web" / "" =>
        IO.pure(_temporary_redirect("/web"))
      case req @ GET -> _ if _web_component_asset_path(req).nonEmpty =>
        val (component, webApp, assetPath) = _web_component_asset_path(req).get
        _web_app_asset(component, webApp, assetPath)
      case req @ GET -> _ if _web_alias_asset_path(req).nonEmpty =>
        val (app, assetPath) = _web_alias_asset_path(req).get
        _web_route_alias_asset(app, assetPath)
      case req @ GET -> Root / "web" / component / webApp / page =>
        _component_web_app(component, webApp, Vector(page), Some(req))
      case req @ GET -> Root / "web" / app =>
        _web_route_alias(req, Vector("web", app)).flatMap {
          case Some(response) => IO.pure(response)
          case None =>
            _component_default_web_app_redirect(app) match {
              case Some(response) => IO.pure(response)
              case None => _static_form_app(app, Vector.empty)
            }
        }
      case req @ GET -> Root / "web" / first / second =>
        _component_web_app_or_static_form_app(first, second, Some(req))
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

  private def _textus_calltree_js(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.textusCalltreeJs)
        .withContentType(`Content-Type`(MediaType.application.javascript, Some(Charset.`UTF-8`)))
    )

  private def _textus_form_debug_js(): IO[HResponse[IO]] =
    IO.pure(
      HResponse[IO](HStatus.Ok)
        .withEntity(StaticFormAppAssets.textusFormDebugJs)
        .withContentType(`Content-Type`(MediaType.application.javascript, Some(Charset.`UTF-8`)))
    )

  private def _subsystem_dashboard(): IO[HResponse[IO]] = {
    val p = _static_form_app_renderer.renderSubsystemDashboard(engine.runtimeSubsystem)
    _html(p)
  }

  private def _dashboard_state(
    componentName: Option[String]
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderDashboardState(engine.runtimeSubsystem, componentName) match {
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
    val p = _static_form_app_renderer.renderSystemPerformance(engine.runtimeSubsystem)
    _html(p)
  }

  private def _system_admin(): IO[HResponse[IO]] = {
    val p = _static_form_app_renderer.renderSystemAdmin(engine.runtimeSubsystem, engine.webDescriptor)
    _html(p)
  }

  private def _system_admin_descriptor(): IO[HResponse[IO]] = {
    val p = _static_form_app_renderer.renderSystemAdminDescriptor(engine.webDescriptor)
    _html(p)
  }

  private def _system_admin_assembly_warnings(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminAssemblyWarnings(engine.runtimeSubsystem))

  private def _system_admin_assembly_report(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminAssemblyReport(engine.runtimeSubsystem))

  private def _system_admin_jobs(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminJobs(engine.runtimeSubsystem))

  private def _system_admin_job(
    req: org.http4s.Request[IO],
    jobId: String
  ): IO[HResponse[IO]] =
    JobId.parse(jobId).toOption.flatMap(engine.runtimeSubsystem.jobEngine.query) match {
      case Some(model) =>
        _html(_static_form_app_renderer.renderSystemAdminJob(engine.runtimeSubsystem, model))
      case None =>
        _html_status(_static_form_app_renderer.renderSystemJobResult(jobId, HttpResponse.notFound(s"job not found: $jobId")), HStatus.NotFound)
    }

  private def _system_admin_knowledge(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminKnowledge(engine.runtimeSubsystem))

  private def _system_admin_knowledge_component(
    component: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderSystemAdminKnowledgeComponent(engine.runtimeSubsystem, component) match {
      case Some(page) =>
        _html(page)
      case None =>
        _web_error_response(
          Some("system"),
          HStatus.NotFound,
          s"knowledge component not found: $component",
          s"/web/system/admin/knowledge/$component"
        )
    }

  private def _system_admin_knowledge_node(
    component: String,
    nodeId: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderSystemAdminKnowledgeNode(engine.runtimeSubsystem, component, nodeId) match {
      case Some(page) =>
        _html(page)
      case None =>
        _web_error_response(
          Some("system"),
          HStatus.NotFound,
          s"knowledge node not found: $component/$nodeId",
          s"/web/system/admin/knowledge/$component/nodes/$nodeId"
        )
    }

  private def _system_admin_information(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminInformation(engine.runtimeSubsystem))

  private def _system_admin_information_component(
    component: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderSystemAdminInformationComponent(engine.runtimeSubsystem, component) match {
      case Some(page) =>
        _html(page)
      case None =>
        _web_error_response(
          Some("system"),
          HStatus.NotFound,
          s"information component not found: $component",
          s"/web/system/admin/information/$component"
        )
    }

  private def _system_admin_observability(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminObservability(engine.runtimeSubsystem))

  private def _system_admin_observability_metrics(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminObservabilityMetrics(engine.runtimeSubsystem))

  private def _system_admin_observability_diagnostics(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemAdminObservabilityDiagnostics())

  private def _system_admin_observability_diagnostic(
    scope: String,
    diagnosticKey: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderSystemAdminObservabilityDiagnostic(scope, diagnosticKey) match {
      case Some(page) =>
        _html(page)
      case None =>
        _web_error_response(
          Some("system"),
          HStatus.NotFound,
          s"diagnostic not found: $scope/$diagnosticKey",
          s"/web/system/admin/observability/diagnostics/$scope/$diagnosticKey"
        )
    }

  private def _system_admin_observability_payload(
    id: String
  ): IO[HResponse[IO]] = {
    val safe = id.matches("[A-Za-z0-9._-]+")
    val runtimeConfig = RuntimeConfig.from(engine.runtimeSubsystem.configuration)
    val config = runtimeConfig.diagnosticPayloadExternalizationConfig
    if (!safe)
      _web_error_response(Some("system"), HStatus.BadRequest, "invalid diagnostic payload id", s"/web/system/admin/observability/payloads/$id")
    else if (id.startsWith("blob-"))
      DiagnosticPayloadReferenceCodec.decodeBlobRef(id) match {
        case Some(ref) =>
          _system_admin_observability_blob_payload(id, ref, runtimeConfig)
        case None =>
          _web_error_response(Some("system"), HStatus.BadRequest, "invalid diagnostic blob payload id", s"/web/system/admin/observability/payloads/$id")
      }
    else
      _find_observability_payload(config.localRoot, id) match {
        case Some(path) =>
          IO.pure(
            HResponse[IO](HStatus.Ok)
              .withBodyStream(fs2.io.readInputStream(IO(Files.newInputStream(path)), 8192, closeAfterUse = true))
              .withContentType(`Content-Type`(MediaType.application.json))
          )
        case None =>
          _web_error_response(Some("system"), HStatus.NotFound, s"diagnostic payload not found: $id", s"/web/system/admin/observability/payloads/$id")
      }
  }

  private def _system_admin_observability_blob_payload(
    id: String,
    ref: BlobStorageRef,
    runtimeConfig: RuntimeConfig
  ): IO[HResponse[IO]] =
    BlobStoreFactory.create(runtimeConfig.blobStoreConfig).flatMap(_.get(ref)) match {
      case Consequence.Success(result) =>
        val mime = MediaType.parse(result.contentType.header).fold(_ => MediaType.application.`octet-stream`, identity)
        IO.pure(
          HResponse[IO](HStatus.Ok)
            .withBodyStream(fs2.io.readInputStream(IO(result.payload.openInputStream()), 8192, closeAfterUse = true))
            .withContentType(`Content-Type`(mime))
        )
      case Consequence.Failure(conclusion) =>
        _web_error_response(Some("system"), HStatus.NotFound, s"diagnostic blob payload not found: $id: ${conclusion.display}", s"/web/system/admin/observability/payloads/$id")
    }

  private def _find_observability_payload(
    root: Path,
    id: String
  ): Option[Path] = {
    val base = root.toAbsolutePath.normalize
    if (!Files.exists(base))
      None
    else {
      val direct = base.resolve(id).normalize
      if (direct.startsWith(base) && Files.isRegularFile(direct))
        Some(direct)
      else {
        val dirs = Files.newDirectoryStream(base)
        try {
          dirs.iterator().asScala
            .map(_.resolve(id).normalize)
            .find(path => path.startsWith(base) && Files.isRegularFile(path))
        } finally {
          dirs.close()
        }
      }
    }
  }

  private def _application_admin(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderApplicationAdmin(engine.runtimeSubsystem, engine.webDescriptor))

  private def _blob_admin(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderBlobAdmin(), Some("blob"))

  private def _blob_admin_blobs(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      _static_form_app_renderer.renderBlobAdminBlobs(engine.runtimeSubsystem, req.uri.query.params.toMap, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some("index")
    )

  private def _blob_admin_blob(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      _static_form_app_renderer.renderBlobAdminBlobDetail(engine.runtimeSubsystem, id, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some(id)
    )

  private def _blob_admin_blob_delete(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      _static_form_app_renderer.renderBlobAdminBlobDelete(engine.runtimeSubsystem, id, _blob_admin_request_properties(req)),
      Some("admin.blobs"),
      Some("delete")
    )

  private def _blob_admin_blob_delete_submit(req: HRequest[IO], id: String): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        _static_form_app_renderer.renderBlobAdminBlobDeleteResult(engine.runtimeSubsystem, id, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.blobs"),
        Some("delete")
      )
    } yield response

  private def _blob_admin_associations(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      _static_form_app_renderer.renderBlobAdminAssociations(engine.runtimeSubsystem, req.uri.query.params.toMap, _blob_admin_request_properties(req)),
      Some("admin.associations"),
      Some("index")
    )

  private def _blob_admin_association_attach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        _static_form_app_renderer.renderBlobAdminAssociationAttachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.associations"),
        Some("attach")
      )
    } yield response

  private def _blob_admin_association_detach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _blob_admin_page(
        req,
        _static_form_app_renderer.renderBlobAdminAssociationDetachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _blob_admin_request_properties(req)),
        Some("admin.associations"),
        Some("detach")
      )
    } yield response

  private def _admin_associations(req: HRequest[IO]): IO[HResponse[IO]] =
    _admin_page(
      req,
      _static_form_app_renderer.renderAdminAssociations(engine.runtimeSubsystem, req.uri.query.params.toMap, _admin_request_properties(req)),
      Some("associations"),
      Some("index")
    )

  private def _admin_association_attach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminAssociationAttachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("associations"),
        Some("attach")
      )
    } yield response

  private def _admin_association_detach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminAssociationDetachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("associations"),
        Some("detach")
      )
    } yield response

  private def _admin_tags(req: HRequest[IO]): IO[HResponse[IO]] =
    _admin_page(
      req,
      _static_form_app_renderer.renderAdminTags(engine.runtimeSubsystem, req.uri.query.params.toMap, _admin_request_properties(req)),
      Some("tags"),
      Some("index")
    )

  private def _admin_tag_create(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminTagCreateResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("tags"),
        Some("create")
      )
    } yield response

  private def _admin_tag_update(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminTagUpdateResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("tags"),
        Some("update")
      )
    } yield response

  private def _admin_tag_move(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminTagMoveResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("tags"),
        Some("move")
      )
    } yield response

  private def _admin_tag_attach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminTagAttachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("tags"),
        Some("attach")
      )
    } yield response

  private def _admin_tag_detach(req: HRequest[IO]): IO[HResponse[IO]] =
    for {
      form <- _to_form_record(req)
      response <- _admin_page(
        req,
        _static_form_app_renderer.renderAdminTagDetachResult(engine.runtimeSubsystem, form.asMap.map { case (k, v) => k -> v.toString }, _admin_request_properties(req)),
        Some("tags"),
        Some("detach")
      )
    } yield response

  private def _blob_admin_store(req: HRequest[IO]): IO[HResponse[IO]] =
    _blob_admin_page(
      req,
      _static_form_app_renderer.renderBlobAdminStore(engine.runtimeSubsystem, _blob_admin_request_properties(req)),
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
            RuntimeDashboardMetrics.recordBlobOperation("content", error = true, diagnosticKey = Some(ConclusionDiagnostics.unknown.diagnosticKey), diagnosticRecord = Some(ConclusionDiagnostics.unknown.toRecord))
            _web_error_response(Some("blob"), HStatus.InternalServerError, s"Blob content operation returned ${other.show}", req.uri.path.renderString)
        }
      case Consequence.Failure(conclusion) =>
        val status = HStatus.fromInt(conclusion.status.webCode.code).getOrElse(HStatus.InternalServerError)
        val classification = ConclusionDiagnostics.classify(conclusion)
        RuntimeDashboardMetrics.recordBlobOperation("content", error = true, diagnosticKey = Some(classification.diagnosticKey), diagnosticRecord = Some(classification.toRecord))
        _web_error_response(Some("blob"), conclusion, req.uri.path.renderString, req.method.name)
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
          diagnosticKey = if (response.code >= 400) Some(ConclusionDiagnostics.unknown.diagnosticKey) else None,
          diagnosticRecord = if (response.code >= 400) Some(Http4sHttpServer.fallbackHttpDiagnosticRecord(response.code)) else None
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
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some("blob"),
          service = service,
          operation = operation
        )
        _web_error_response(Some("blob"), error)
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
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some("admin"),
          service = service,
          operation = operation
        )
        _web_error_response(Some("admin"), error)
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
    _html(_static_form_app_renderer.renderSystemManual(engine.runtimeSubsystem))

  private def _system_document(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderSystemDocument(engine.runtimeSubsystem))

  private def _runtime_landing(): IO[HResponse[IO]] =
    _html(_static_form_app_renderer.renderRuntimeLanding(engine.runtimeSubsystem, engine.webDescriptor))

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
      _html(_static_form_app_renderer.renderSystemJobTicket(jobId))
    else
      _html_status(_static_form_app_renderer.renderSystemJobResult(jobId, res), HStatus.fromInt(res.code).getOrElse(HStatus.Forbidden))
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
    _html_status(_static_form_app_renderer.renderSystemJobResult(jobId, res), HStatus.fromInt(res.code).getOrElse(HStatus.Ok))
  }

  private def _application_jobs(
    req: org.http4s.Request[IO],
    app: String
  ): IO[HResponse[IO]] =
    _request_execution_context(req) match {
      case Consequence.Success(ctx) =>
        given ExecutionContext = ctx
        val jobs = _visible_application_jobs(app)
          .take(100)
        _session_id_(req).foreach { sessionId =>
          _application_job_seen_at.update((sessionId, NamingConventions.toNormalizedSegment(app)), Instant.now)
        }
        _html(_static_form_app_renderer.renderApplicationJobs(app, jobs), Some(app))
      case Consequence.Failure(conclusion) =>
        val error = StructuredHttpError.fromConclusion(
          conclusion,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some(app),
          service = Some("jobs"),
          operation = Some("index")
        )
        _html_status(_static_form_app_renderer.renderStructuredErrorPage(Some(app), error), _http_status(error))
    }

  private def _application_job(
    req: org.http4s.Request[IO],
    app: String,
    jobId: String
  ): IO[HResponse[IO]] =
    JobId.parse(jobId).toOption match {
      case Some(id) =>
        _request_execution_context(req) match {
          case Consequence.Success(ctx) =>
            given ExecutionContext = ctx
            engine.runtimeSubsystem.jobEngine.queryVisible(id).toOption.flatten.filter(_job_belongs_to_app(_, app)) match {
              case Some(model) =>
                _html(_static_form_app_renderer.renderApplicationJob(app, model), Some(app))
              case None =>
                _html_status(_static_form_app_renderer.renderStructuredErrorPage(Some(app), StructuredHttpError.fromMessage(
                  s"job not found: $jobId",
                  HStatus.NotFound.code,
                  req.uri.path.renderString,
                  req.method.name,
                  _operation_mode,
                  component = Some(app),
                  service = Some("jobs"),
                  operation = Some(jobId)
                )), HStatus.NotFound)
            }
          case Consequence.Failure(conclusion) =>
            val error = StructuredHttpError.fromConclusion(
              conclusion,
              req.uri.path.renderString,
              req.method.name,
              _operation_mode,
              component = Some(app),
              service = Some("jobs"),
              operation = Some(jobId)
            )
            _html_status(_static_form_app_renderer.renderStructuredErrorPage(Some(app), error), _http_status(error))
        }
      case None =>
        _html_status(_static_form_app_renderer.renderStructuredErrorPage(Some(app), StructuredHttpError.fromMessage(
          s"invalid job id: $jobId",
          HStatus.BadRequest.code,
          req.uri.path.renderString,
          req.method.name,
          _operation_mode,
          component = Some(app),
          service = Some("jobs"),
          operation = Some(jobId)
        )), HStatus.BadRequest)
    }

  private def _job_belongs_to_app(
    model: org.goldenport.cncf.job.JobQueryReadModel,
    app: String
  ): Boolean = {
    val expected = NamingConventions.toNormalizedSegment(app)
    val params = model.debug.parameters
    params.get("web.application-job").contains("true") &&
      params.get("web.app").exists(x => NamingConventions.toNormalizedSegment(x) == expected)
  }

  private def _component_manual(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentManual(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component specification not found"))
    }

  private def _component_document(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentDocument(
      engine.runtimeSubsystem,
      app,
      _component_document_entries(app)
    ) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component document not found"))
    }

  private def _component_manual_service(
    app: String,
    service: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentManualService(engine.runtimeSubsystem, app, service) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Service specification not found"))
    }

  private def _component_manual_operation(
    app: String,
    service: String,
    operation: String
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentManualOperation(engine.runtimeSubsystem, app, service, operation) match {
      case Some(p) => _html(p)
      case None => IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Operation specification not found"))
    }

  private def _component_document_asset(
    app: String,
    documentPath: Vector[String]
  ): IO[HResponse[IO]] =
    if (!_component_exists(app))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component document not found"))
    else if (!_safe_document_path(documentPath))
      IO.pure(HResponse[IO](HStatus.BadRequest).withEntity("Invalid component document path"))
    else
      _component_document_content(app, documentPath) match {
        case Some((content, mediaType)) =>
          _asset_response(content, mediaType)
        case None =>
          IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component document not found"))
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
      _web_app_asset_content(Some(componentName), webAppName, assetPath) match {
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
    req: org.http4s.Request[IO],
    path: Vector[String]
  ): IO[Option[HResponse[IO]]] =
    engine.webDescriptor.webRouteFor(path) match {
      case Some(route) =>
        engine.webDescriptor.appKind(route.target.normalizedApp).map(_.toLowerCase) match {
          case Some("static-form") if route.remainingPath.isEmpty =>
            if (_web_app_static_html_content(Some(route.target.normalizedComponent), route.target.normalizedApp, Vector.empty).nonEmpty)
              _component_web_app(
                route.target.normalizedComponent,
                route.target.normalizedApp,
                Vector.empty,
                Some(req)
              ).map(Some(_))
            else
              _static_form_app(route.target.normalizedApp, Vector.empty).map(Some(_))
          case Some("static-form") =>
            _component_web_app(
              route.target.normalizedComponent,
              route.target.normalizedApp,
              route.remainingPath,
              Some(req)
            ).map(Some(_))
          case _ =>
            _component_web_app(
              route.target.normalizedComponent,
              route.target.normalizedApp,
              route.remainingPath,
              Some(req)
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
    page: Vector[String],
    req: Option[org.http4s.Request[IO]] = None
  ): IO[HResponse[IO]] =
    if (!_component_exists(componentName))
      IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component Web app not found"))
    else
      _web_app_static_html_content(Some(componentName), webAppName, page) match {
        case Some(content) =>
          _web_operation_result_widget(content) match {
            case Some(widget) =>
              _web_operation_result_page(req, componentName, webAppName, page, widget)
            case None =>
              _web_app_static_page(Some(componentName), webAppName, page, content, req) match {
                case Consequence.Success(page) =>
                  _html_content(page.body, Some(webAppName), Some(componentName))
                case Consequence.Failure(conclusion) =>
                  _web_error_response(
                    Some(webAppName),
                    conclusion,
                    s"/web/${componentName}/${webAppName}${page.map(p => s"/${p}").mkString}"
                  )
              }
          }
        case None =>
          _web_error_response(
            Some(webAppName),
            HStatus.NotFound,
            "Component Web app page not found",
            s"/web/${componentName}/${webAppName}${page.map(p => s"/${p}").mkString}"
          )
      }

  private final case class WebOperationResultWidget(
    component: String,
    service: String,
    operation: String,
    values: Map[String, String],
    label: Option[String]
  )

  private def _web_operation_result_widget(
    content: String
  ): Option[WebOperationResultWidget] = {
    val widget = """(?s)<textus(?::operation-result|-operation-result)\b([^>]*)/?>(?:\s*</textus(?::operation-result|-operation-result)>)?""".r
    widget.findFirstMatchIn(content).flatMap { m =>
      val attrs = _web_widget_attrs(m.group(1))
      for {
        component <- attrs.get("component").orElse(attrs.get("app")).map(_.trim).filter(_.nonEmpty)
        service <- attrs.get("service").map(_.trim).filter(_.nonEmpty)
        operation <- attrs.get("operation").map(_.trim).filter(_.nonEmpty)
      } yield {
        val reserved = Set("component", "app", "service", "operation", "debug-label", "debugLabel")
        WebOperationResultWidget(
          component,
          service,
          operation,
          attrs.filterNot { case (key, _) => reserved.contains(key) },
          attrs.get("debug-label").orElse(attrs.get("debugLabel"))
        )
      }
    }
  }

  private def _web_widget_attrs(
    source: String
  ): Map[String, String] =
    """([A-Za-z0-9_.:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r.findAllMatchIn(source).map { m =>
      m.group(1) -> Option(m.group(2)).getOrElse(m.group(3))
    }.toMap

  private def _web_operation_result_page(
    req: Option[org.http4s.Request[IO]],
    ownerComponentName: String,
    webAppName: String,
    page: Vector[String],
    widget: WebOperationResultWidget
  ): IO[HResponse[IO]] =
    req match {
      case Some(request) =>
        _web_operation_result_page(request, ownerComponentName, webAppName, page, widget)
      case None =>
        _web_error_response(
          Some(webAppName),
          HStatus.InternalServerError,
          "Static Form operation-result requires request context",
          s"/web/${ownerComponentName}/${webAppName}${page.map(p => s"/${p}").mkString}"
        )
    }

  private def _web_operation_result_page(
    req: org.http4s.Request[IO],
    ownerComponentName: String,
    webAppName: String,
    page: Vector[String],
    widget: WebOperationResultWidget
  ): IO[HResponse[IO]] = {
    val app = widget.component
    val service = widget.service
    val operation = widget.operation
    if (!_is_form_enabled(app, service, operation))
      _web_error_response(Some(webAppName), HStatus.NotFound, "Static Form operation-result operation not found", req.uri.path.renderString)
    else if (!_is_web_authorized(app, service, operation, Some(req)))
      _forbidden_web(req, Some(webAppName), Some(service), Some(operation))
    else {
      val started = System.nanoTime()
      val queryValues = req.uri.query.params.toMap
      val values = widget.values ++ queryValues
      val form = Record.create(values.toVector)
      val query = Record.create(req.uri.query.params.toVector)
      val header = _web_operation_result_header(req, query, form, widget.label.getOrElse(s"${service}.${operation}"))
      val result = _dispatch_operation_result(
        app,
        service,
        operation,
        HttpRequest.fromPath(
          method = HttpRequest.POST,
          path = s"/${app}/${service}/${operation}",
          query = query,
          header = header,
          form = _operation_dispatch_form(form)
        )
      )
      val res = result.response
      val continuation = _create_form_continuation(app, service, operation, form, res, _form_chunk_size(form))
      val resultValues = values ++ _continuation_values(continuation)
      val properties = _form_result_properties(
        app,
        service,
        operation,
        result,
        resultValues,
        _form_page_view_context_values(req, app, service, operation, resultValues)
      )
      _prepared_form_result_template(app, service, operation, res.code, properties.page.values) match {
        case Consequence.Success(template) =>
          _html(_static_form_app_renderer.renderFormResult(properties, template), Some(webAppName)).map { response =>
            RuntimeDashboardMetrics.recordHtmlRequest(
              req.method.name,
              req.uri.path.renderString,
              res.code,
              (System.nanoTime() - started) / 1000000L
            )
            response
          }
        case Consequence.Failure(conclusion) =>
          _web_error_response(Some(webAppName), conclusion, req.uri.path.renderString, req.method.name)
      }
    }
  }

  private def _web_operation_result_header(
    req: org.http4s.Request[IO],
    query: Record,
    form: Record,
    label: String
  ): Record = {
    val base = _development_form_header_record(req, query, form)
    if (!_is_development_operation_mode(_operation_mode))
      base
    else {
      Record.create(
        _with_header_if_absent(
          _with_header_if_absent(
            _with_header_if_absent(base.asMap.toVector, "x-textus-debug-request-kind", "page-render"),
            "x-textus-debug-label",
            label
          ),
          "x-textus-debug-display",
          "always"
        )
      )
    }
  }

  private[http] def _component_web_app_or_static_form_app(
    first: String,
    second: String,
    req: Option[org.http4s.Request[IO]] = None
  ): IO[HResponse[IO]] =
    if (_component_exists(first) && _web_app_static_html_content(Some(first), second, Vector.empty).nonEmpty)
      _component_web_app(first, second, Vector.empty, req)
    else engine.webDescriptor.webRouteFor(Vector("web", first, second)) match {
      case Some(route) =>
        _component_web_app(route.target.normalizedComponent, route.target.normalizedApp, route.remainingPath, req)
      case None =>
      _static_form_app(first, Vector(second))
    }

  private def _component_default_web_app_redirect(
    componentName: String
  ): Option[HResponse[IO]] =
    if (!_component_exists(componentName))
      None
    else {
      val normalizedComponent = NamingConventions.toNormalizedSegment(componentName)
      engine.webDescriptor.routeAppForComponent(componentName)
        .filterNot(_ == normalizedComponent)
        .map(app => _temporary_redirect(s"/web/${app}"))
    }

  private def _component_admin(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdmin(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component admin not found"))
    }

  private def _component_admin_descriptor(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminDescriptor(engine.runtimeSubsystem, app, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component descriptor admin not found"))
    }

  private def _component_admin_page(
    app: String,
    entry: WebDescriptor.AdminPage
  ): IO[HResponse[IO]] =
    _component_web_app(app, "admin", Vector(entry.normalizedName))

  private def _component_admin_entities(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminEntities(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity admin not found"))
    }

  private def _component_admin_entity_type(req: HRequest[IO], app: String, entity: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminEntityType(engine.runtimeSubsystem, app, entity, _page_request(req), engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity type admin not found"))
    }

  private def _component_admin_entity_detail(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminEntityDetail(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor, req.uri.query.params.toMap) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity detail admin not found"))
    }

  private def _component_admin_entity_edit(req: HRequest[IO], app: String, entity: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminEntityEdit(engine.runtimeSubsystem, app, entity, id, values = req.uri.query.params.toMap, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity edit admin not found"))
    }

  private def _component_admin_entity_new(app: String, entity: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminEntityNew(engine.runtimeSubsystem, app, entity, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component entity new admin not found"))
    }

  private def _component_admin_data(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminData(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data admin not found"))
    }

  private def _component_admin_data_type(req: HRequest[IO], app: String, data: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminDataType(engine.runtimeSubsystem, app, data, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data type admin not found"))
    }

  private def _component_admin_data_detail(app: String, data: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminDataDetail(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data detail admin not found"))
    }

  private def _component_admin_data_edit(app: String, data: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminDataEdit(engine.runtimeSubsystem, app, data, id, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data edit admin not found"))
    }

  private def _component_admin_data_new(app: String, data: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminDataNew(engine.runtimeSubsystem, app, data, webDescriptor = engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component data new admin not found"))
    }

  private def _component_admin_views(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminViews(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view admin not found"))
    }

  private def _component_admin_view_detail(req: HRequest[IO], app: String, view: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminViewDetail(engine.runtimeSubsystem, app, view, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view detail admin not found"))
    }

  private def _component_admin_view_instance_detail(app: String, view: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminViewInstanceDetail(engine.runtimeSubsystem, app, view, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component view instance detail admin not found"))
    }

  private def _component_admin_aggregates(app: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminAggregates(engine.runtimeSubsystem, app) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate admin not found"))
    }

  private def _component_admin_aggregate_detail(req: HRequest[IO], app: String, aggregate: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminAggregateDetail(engine.runtimeSubsystem, app, aggregate, _page_request(req), engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate detail admin not found"))
    }

  private def _component_admin_aggregate_instance_detail(app: String, aggregate: String, id: String): IO[HResponse[IO]] =
    _static_form_app_renderer.renderComponentAdminAggregateInstanceDetail(engine.runtimeSubsystem, app, aggregate, id, engine.webDescriptor) match {
      case Some(p) => _html(p)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Component aggregate instance detail admin not found"))
    }

  private[http] def _static_form_app(
    app: String,
    page: Vector[String]
  ): IO[HResponse[IO]] =
    _static_form_app_renderer.render(engine.runtimeSubsystem, app, page, engine.webDescriptor) match {
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
    _static_form_app_renderer.renderFormIndex(engine.runtimeSubsystem, app, engine.webDescriptor) match {
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
    else _static_form_app_renderer.renderOperationForm(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor, req.uri.query.params.toMap) match {
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
      _static_form_app_renderer.renderOperationFormDefinition(engine.runtimeSubsystem, app, service, operation, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminEntityFormDefinition(engine.runtimeSubsystem, app, entity, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminEntityUpdateFormDefinition(engine.runtimeSubsystem, app, entity, id, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminDataFormDefinition(engine.runtimeSubsystem, app, data, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminDataUpdateFormDefinition(engine.runtimeSubsystem, app, data, id, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminViewFormDefinition(engine.runtimeSubsystem, app, view, engine.webDescriptor) match {
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
      _static_form_app_renderer.renderComponentAdminAggregateFormDefinition(engine.runtimeSubsystem, app, aggregate, engine.webDescriptor) match {
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
      validation = _static_form_app_renderer.validateOperationForm(
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
            val page = _static_form_app_renderer.renderOperationForm(
              engine.runtimeSubsystem,
              app,
              service,
              operation,
              engine.webDescriptor,
              _with_form_debug_panel_flag(pageValues),
              Some(result),
              _operation_mode,
              showExecutionDebugPanel = true
            ).getOrElse(_static_form_app_renderer.renderFormResult(
              _form_result_properties(app, service, operation, HttpResponse.Text(
                HttpStatus.BadRequest,
                ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
                Bag.text("Validation failed.", StandardCharsets.UTF_8)
              ), pageValues, _form_page_view_context_values(req, app, service, operation, pageValues))
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
    val query = Record.create(req.uri.query.params.toVector)
    val result = _dispatch_operation_result(
        app,
        service,
        operation,
        HttpRequest.fromPath(
          method = HttpRequest.POST,
          path = s"/${app}/${service}/${operation}",
          query = query,
          header = _development_form_header_record(req, query, form),
          form = _operation_dispatch_form(form)
        )
      )
    _annotate_application_job(result, app, service, operation)
    val res = result.response
    val continuation = _create_form_continuation(app, service, operation, form, res, _form_chunk_size(form))
    val resultValues = values ++ _continuation_values(continuation)
    val properties = _form_result_properties(
      app,
      service,
      operation,
      result,
      resultValues,
      _form_page_view_context_values(req, app, service, operation, resultValues)
    )
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
        header = _development_form_header_record(req, query, form)
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
        response <- _static_form_app_renderer.renderOperationFormValidation(
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
        _prepared_form_result_template(app, service, operation, res.code, values) match {
          case Consequence.Success(template) =>
            val page = _static_form_app_renderer.renderFormResult(
              _form_result_properties(
                app,
                service,
                operation,
                res,
                values,
                _form_page_view_context_values(req, app, service, operation, values)
              ),
              template
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
          case Consequence.Failure(conclusion) =>
            _web_error_response(Some(app), conclusion, req.uri.path.renderString, req.method.name)
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
          request.path.asString,
          request.method.name,
          _operation_mode,
          component = Some(app),
          service = Some(service),
          operation = Some(operation)
        )
        HttpExecutionResult(
            HttpResponse.Text(
            HttpStatus.fromInt(error.status).getOrElse(HttpStatus.InternalServerError),
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
      validation = _static_form_app_renderer.validateComponentAdminEntityForm(engine.runtimeSubsystem, app, entity, values, engine.webDescriptor, Some("detail"))
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_entity_validation_error_response(app, entity, Some(id), values, result)
        case _ =>
          val result = _dispatch_component_admin_entity_record("update", app, entity, record)
          val page = _static_form_app_renderer.renderComponentAdminEntityUpdateResult(app, entity, id, values, result.applied, result.message, result.response.code)
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
      validation = _static_form_app_renderer.validateComponentAdminEntityForm(engine.runtimeSubsystem, app, entity, values, engine.webDescriptor, Some("create"))
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_entity_validation_error_response(app, entity, None, values, result)
        case _ =>
          val result = _dispatch_component_admin_entity_record("create", app, entity, form)
          val page = _static_form_app_renderer.renderComponentAdminEntityCreateResult(app, entity, values, result.applied, result.message, result.response.code)
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
      validation = _static_form_app_renderer.validateComponentAdminDataForm(engine.runtimeSubsystem, app, data, values, engine.webDescriptor)
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_data_validation_error_response(app, data, Some(id), values, result)
        case _ =>
          val result = _dispatch_component_admin_data_record("update", app, data, record)
          val page = _static_form_app_renderer.renderComponentAdminDataUpdateResult(app, data, id, values, result.applied, result.message, result.response.code)
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
      validation = _static_form_app_renderer.validateComponentAdminDataForm(engine.runtimeSubsystem, app, data, values, engine.webDescriptor)
      html <- validation match {
        case Some(result) if !result.valid =>
          _admin_data_validation_error_response(app, data, None, values, result)
        case _ =>
          val result = _dispatch_component_admin_data_record("create", app, data, form)
          val page = _static_form_app_renderer.renderComponentAdminDataCreateResult(app, data, values, result.applied, result.message, result.response.code)
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
        _static_form_app_renderer.renderComponentAdminEntityEdit(
          engine.runtimeSubsystem,
          app,
          entity,
          recordId,
          values,
          engine.webDescriptor,
          Some(validation)
        )
      case None =>
        _static_form_app_renderer.renderComponentAdminEntityNew(
          engine.runtimeSubsystem,
          app,
          entity,
          values,
          engine.webDescriptor,
          Some(validation)
        )
    }
    _html_status(page.getOrElse(_static_form_app_renderer.renderSystemAdmin(engine.runtimeSubsystem)), HStatus.BadRequest)
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
        _static_form_app_renderer.renderComponentAdminDataEdit(
          engine.runtimeSubsystem,
          app,
          data,
          recordId,
          values,
          engine.webDescriptor,
          Some(validation)
        )
      case None =>
        _static_form_app_renderer.renderComponentAdminDataNew(
          engine.runtimeSubsystem,
          app,
          data,
          values,
          engine.webDescriptor,
          Some(validation)
        )
    }
    _html_status(page.getOrElse(_static_form_app_renderer.renderSystemAdmin(engine.runtimeSubsystem)), HStatus.BadRequest)
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
          _static_form_app_renderer.renderComponentAdminEntityEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values,
            engine.webDescriptor
          )
        case ("entities", "create", _) =>
          _static_form_app_renderer.renderComponentAdminEntityNew(
            engine.runtimeSubsystem,
            app,
            collection,
            values,
            engine.webDescriptor
          )
        case ("data", "update", Some(id)) =>
          _static_form_app_renderer.renderComponentAdminDataEdit(
            engine.runtimeSubsystem,
            app,
            collection,
            id,
            values,
            engine.webDescriptor
          )
        case ("data", "create", _) =>
          _static_form_app_renderer.renderComponentAdminDataNew(
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
    val result = _dispatch_operation_result(
      app,
      service,
      operation,
      HttpRequest.fromPath(
        method = HttpRequest.GET,
        path = s"/${app}/${service}/${operation}",
        query = query,
        header = _development_form_header_record(req, query)
      )
    )
    _annotate_application_job(result, app, service, operation)
    val res = result.response
    _prepared_form_result_template(app, service, operation, res.code, values) match {
      case Consequence.Success(template) =>
        val page = _static_form_app_renderer.renderFormResult(
          _form_result_properties(
            app,
            service,
            operation,
            result,
            values,
            _form_page_view_context_values(req, app, service, operation, values)
          ),
          template
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
      case Consequence.Failure(conclusion) =>
        _web_error_response(Some(app), conclusion, req.uri.path.renderString, req.method.name)
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
      _to_plain_form_record(req).flatMap { form =>
        val dispatchForm = Record.create(
          (_operation_dispatch_form(form).asMap + ("id" -> jobId)).toVector
        )
        val result = _dispatch_operation_result(
          "job_control",
          "job",
          "await_job_result",
          HttpRequest.fromPath(
            method = HttpRequest.POST,
            path = "/job_control/job/await_job_result",
            query = Record.empty,
            header = _development_form_header_record(req, form = form),
            form = dispatchForm
          )
        )
        val res = result.response
        _prepared_form_result_template(app, service, operation, res.code, values) match {
          case Consequence.Success(template) =>
            val page = _static_form_app_renderer.renderFormResult(
              _form_result_properties(
                app,
                service,
                operation,
                result,
                values,
                _form_page_view_context_values(req, app, service, operation, values)
              ),
              template
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
          case Consequence.Failure(conclusion) =>
            _web_error_response(Some(app), conclusion, req.uri.path.renderString, req.method.name)
        }
      }
    }

  private def _form_result_properties(
    app: String,
    service: String,
    operation: String,
    response: HttpResponse,
    values: Map[String, String],
    pageContextValues: Map[String, String]
  ): StaticFormAppRenderer.FormResultProperties =
    _form_result_properties(
      app,
      service,
      operation,
      HttpExecutionResult(response, RuntimeContext.ExecutionMetadata.empty),
      values,
      pageContextValues
    )

  private def _form_result_properties(
    app: String,
    service: String,
    operation: String,
    result: HttpExecutionResult,
    values: Map[String, String],
    pageContextValues: Map[String, String] = Map.empty
  ): StaticFormAppRenderer.FormResultProperties = {
    val debugValues =
      if (_is_development_operation_mode(_operation_mode))
        values + ("textus.debug.executionPanel" -> "true")
      else
        values
    val userValues = debugValues.filterNot { case (key, _) => key.startsWith("pageContext.") }
    val pageValues =
      userValues ++ StaticFormAppRenderer.defaultPageViewContextValues ++ pageContextValues
    StaticFormAppRenderer.FormResultProperties(
      StaticFormAppRenderer.FormPageProperties(app, service, operation, pageValues),
      result.response.code,
      result.response.mime.value,
      result.response.getString.getOrElse(""),
      _form_result_table_columns(app, service, operation),
      engine.webDescriptor.defaultView,
      _form_result_asset_completion_options(app, service, operation),
      result.metadata,
      _operation_mode,
      _form_result_field_confidentiality(app, service, operation)
    )
  }

  private def _form_result_field_confidentiality(
    app: String,
    service: String,
    operation: String
  ): Map[String, org.goldenport.schema.DataConfidentiality] =
    (for {
      component <- _component(app)
      serviceDefinition <- component.protocol.services.services.find(s => NamingConventions.equivalentByNormalized(s.name, service))
      operationDefinition <- serviceDefinition.operations.operations.find(o => NamingConventions.equivalentByNormalized(o.name, operation))
    } yield {
      val request = operationDefinition.specification.request.parameters.toVector.map { p =>
        p.name -> p.confidentiality
      }.toMap
      val response =
        org.goldenport.cncf.operation.OperationConfidentiality.response(
          component,
          operationDefinition.name,
          Some(operationDefinition)
        )
      request ++ response
    }).getOrElse(Map.empty)

  private def _with_form_debug_panel_flag(values: Map[String, String]): Map[String, String] =
    if (_is_development_operation_mode(_operation_mode))
      values + ("textus.debug.executionPanel" -> "true")
    else
      values

  private def _annotate_application_job(
    result: HttpExecutionResult,
    app: String,
    service: String,
    operation: String
  ): Unit = {
    val resultJobId = FormResultMetadata.fromHttpResponse(result.response).jobId
    result.metadata.responseJobId.filter(jobid => resultJobId.contains(jobid)).foreach { jobid =>
      JobId.parse(jobid).toOption.foreach { id =>
        engine.runtimeSubsystem.jobEngine.annotateJob(
          id,
          Map(
            "web.app" -> app,
            "web.service" -> service,
            "web.operation" -> operation,
            "web.application-job" -> "true"
          ),
          Vector(s"application job: ${app}.${service}.${operation}")
        )
      }
    }
  }

  private def _form_result_asset_completion_options(
    app: String,
    service: String,
    operation: String
  ): StaticFormAppLayout.AssetCompletionOptions = {
    val assets = engine.webDescriptor.resultAssets(app, service, operation)
    StaticFormAppLayout.AssetCompletionOptions(
      autoComplete = assets.autoComplete,
      declaredCss = assets.css,
      declaredJs = assets.js,
      favicon = assets.favicon
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
          _html(_static_form_app_renderer.renderOperationForm(
            engine.runtimeSubsystem,
            app,
            service,
            operation,
            engine.webDescriptor,
            _with_form_debug_panel_flag(_operation_form_values(form) ++ _error_values(response)),
            operationMode = _operation_mode,
            showExecutionDebugPanel = true
          ).getOrElse(_static_form_app_renderer.renderFormResult(properties)), Some(app))
        else
          _prepared_form_result_template(app, service, operation, response.code, properties.page.values) match {
            case Consequence.Success(template) =>
              _html(_static_form_app_renderer.renderFormResult(
                properties,
                template
              ), Some(app))
            case Consequence.Failure(conclusion) =>
              _web_error_response(Some(app), conclusion, s"/form/${app}/${service}/${operation}")
          }
    }
  }

  private[http] def _form_result_static_template(
    app: String,
    service: String,
    operation: String,
    status: Int,
    values: Map[String, String] = Map.empty
  ): Option[String] =
    _form_result_static_template_c(app, service, operation, status, values).toOption.flatten

  private def _form_result_static_template_c(
    app: String,
    service: String,
    operation: String,
    status: Int,
    values: Map[String, String] = Map.empty
  ): Consequence[Option[String]] = {
    val page = _form_result_template_page(service, operation, values)
    val webAppName = _form_result_web_app(app, page)
    val composeSubsystemArticle = _compose_subsystem_article(webAppName, page)
    val contentScope =
      if (composeSubsystemArticle) WebTemplatePartScope.ComponentContent else WebTemplatePartScope.Default
    val candidates = for {
      root <- _web_resource_roots(contentScope, Some(app))
      candidate <- _form_result_template_candidates(webAppName, service, operation, status, values)
      content <- root.readText(candidate).toVector
    } yield content
    candidates.headOption match {
      case Some(content) =>
        _compose_web_template(
          webAppName,
          page,
          content,
          _form_layout(app, service, operation),
          allowImplicitDefault = true,
          subsystemShell = composeSubsystemArticle,
          requireLayout = composeSubsystemArticle,
          componentName = Some(app)
        ).map(x => Some(x.html))
      case None =>
        Consequence.success(None)
    }
  }

  private[http] def _prepared_form_result_template(
    app: String,
    service: String,
    operation: String,
    status: Int,
    values: Map[String, String] = Map.empty
  ): Consequence[Option[String]] =
    _form_result_static_template_c(app, service, operation, status, values).flatMap {
      case Some(template) =>
        Consequence.success(Some(template))
      case None =>
        _form_descriptor(app, service, operation).flatMap(_.resultTemplate) match {
          case Some(template) =>
            val page = _form_result_template_page(service, operation, values)
            val webAppName = _form_result_web_app(app, page)
            val composeSubsystemArticle = _compose_subsystem_article(webAppName, page)
            _compose_web_template(
              webAppName,
              page,
              template,
              _form_layout(app, service, operation),
              allowImplicitDefault = true,
              subsystemShell = composeSubsystemArticle,
              requireLayout = composeSubsystemArticle,
              componentName = Some(app)
            ).map(x => Some(x.html))
          case None =>
            Consequence.success(None)
        }
    }

  private def _form_result_web_app(
    componentName: String,
    page: Vector[String]
  ): String =
    engine.webDescriptor.routeAppForComponentPage(componentName, page).getOrElse(componentName)

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

  private def _form_result_template_page(
    service: String,
    operation: String,
    values: Map[String, String]
  ): Vector[String] =
    _form_result_page(values)
      .map(Vector(_))
      .getOrElse(Vector(
        org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(service),
        org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(operation)
      ))

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

  private def _web_resource_roots(
    scope: WebTemplatePartScope,
    componentName: Option[String] = None
  ): Vector[WebResourceRoot] =
    scope match {
      case WebTemplatePartScope.Default => _web_resource_roots()
      case WebTemplatePartScope.ComponentContent => _component_content_web_roots(componentName)
      case WebTemplatePartScope.SubsystemShell => _subsystem_shell_web_roots()
    }

  private[http] def _component_content_web_roots(
    componentName: Option[String] = None
  ): Vector[WebResourceRoot] =
    componentName.map(_component_web_roots).getOrElse(_component_web_roots()) ++
      _web_descriptor_config_root().toVector ++
      _subsystem_descriptor_web_root().toVector

  private[http] def _subsystem_shell_web_roots(): Vector[WebResourceRoot] =
    _explicit_shell_owner_web_roots().getOrElse(
      _web_descriptor_config_root().toVector ++
        _subsystem_descriptor_web_root().toVector ++
        _single_component_web_root_for_deemed_subsystem()
    )

  private def _explicit_shell_owner_web_roots(): Option[Vector[WebResourceRoot]] =
    engine.webDescriptor.shellComponentName.map(_component_web_roots)

  private def _validate_explicit_shell_owner(): Consequence[Unit] =
    engine.webDescriptor.shellComponentName match {
      case Some(componentName) if _component_web_roots(componentName).isEmpty =>
        Consequence.resourceInvalid(
          s"Static Form subsystem shell component Web root not found: ${componentName}",
          Cause.Kind.Inconsistency,
          Seq(
            Descriptor.Facet.Component(componentName),
            Descriptor.Facet.State("subsystem-shell-owner-missing-web-root")
          )
        )
      case _ =>
        Consequence.success(())
    }

  private def _single_component_web_root_for_deemed_subsystem(): Vector[WebResourceRoot] = {
    val componentRoots = engine.runtimeSubsystem.components.map(_component_web_roots).filter(_.nonEmpty)
    componentRoots match {
      case Vector(roots) => roots
      case _ => Vector.empty
    }
  }

  private[http] def _component_web_roots(): Vector[WebResourceRoot] =
    engine.runtimeSubsystem.components
      .flatMap(_component_web_roots)

  private[http] def _component_web_roots(
    componentName: String
  ): Vector[WebResourceRoot] = {
    val normalized = NamingConventions.toNormalizedSegment(componentName)
    engine.runtimeSubsystem.components
      .filter(component => _component_matches(component, normalized))
      .flatMap(_component_web_roots)
  }

  private def _component_web_roots(
    component: org.goldenport.cncf.component.Component
  ): Vector[WebResourceRoot] =
    component.artifactMetadata.toVector
      .flatMap(_.archivePath)
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

  private def _component_matches(
    component: org.goldenport.cncf.component.Component,
    normalizedName: String
  ): Boolean = {
    def normalize(value: String): String =
      NamingConventions.toNormalizedSegment(value)
    normalize(component.name) == normalizedName ||
      component.artifactMetadata.toVector.exists { metadata =>
        normalize(metadata.name) == normalizedName ||
          metadata.component.exists(value => normalize(value) == normalizedName)
      }
  }

  private[http] def _web_app_asset_content(
    webAppName: String,
    assetName: String
  ): Option[(BinaryBag, MediaType)] =
    _web_app_asset_content(None, webAppName, Vector(assetName))

  private[http] def _web_app_asset_content(
    webAppName: String,
    assetPath: Vector[String]
  ): Option[(BinaryBag, MediaType)] =
    _web_app_asset_content(None, webAppName, assetPath)

  private[http] def _web_app_asset_content(
    componentName: Option[String],
    webAppName: String,
    assetPath: Vector[String]
  ): Option[(BinaryBag, MediaType)] =
    _web_app_asset_candidates(webAppName, assetPath).view.flatMap { path =>
      _component_content_web_roots(componentName).view.flatMap { root =>
        root.readBinary(path).map(_ -> _asset_media_type(assetPath.lastOption.getOrElse("")))
      }
    }.headOption

  private def _component_document_entries(
    componentName: String
  ): Vector[StaticFormAppRenderer.DocumentLink] =
    _component_document_candidates.view.flatMap { case (title, path) =>
      _component_document_content(componentName, path).map { _ =>
        StaticFormAppRenderer.DocumentLink(title, s"/web/${NamingConventions.toNormalizedSegment(componentName)}/document/${path.map(_escape_uri_path_segment).mkString("/")}")
      }
    }.toVector

  private def _component_document_content(
    componentName: String,
    documentPath: Vector[String]
  ): Option[(BinaryBag, MediaType)] =
    _component_document_storage_candidates(documentPath).view.flatMap { path =>
      _component_web_roots(componentName).view.flatMap { root =>
        root.readBinary(path).map(_ -> _asset_media_type(documentPath.lastOption.getOrElse("")))
      }
    }.headOption

  private def _component_document_candidates: Vector[(String, Vector[String])] =
    Vector(
      "User Guide" -> Vector("user-guide.html"),
      "User Guide" -> Vector("user-guide.md"),
      "User Guide" -> Vector("user-guide.pdf"),
      "Reference Manual" -> Vector("reference-manual.html"),
      "Reference Manual" -> Vector("reference-manual.md"),
      "Reference Manual" -> Vector("reference-manual.pdf"),
      "Packaged Specification" -> Vector("specification.html"),
      "Packaged Specification" -> Vector("specification.md"),
      "Packaged Specification" -> Vector("specification.pdf"),
      "README" -> Vector("README.md")
    )

  private def _component_document_storage_candidates(
    documentPath: Vector[String]
  ): Vector[Path] =
    Vector(
      _relative_path("documents" +: documentPath),
      _relative_path("docs" +: documentPath)
    )

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

  private[http] def _favicon(): IO[HResponse[IO]] =
    _favicon_content() match {
      case Some((content, mediaType)) =>
        _asset_response(content, mediaType)
      case None =>
        IO.pure(HResponse[IO](HStatus.NotFound).withEntity("Favicon not found"))
    }

  private[http] def _favicon_content(): Option[(BinaryBag, MediaType)] =
    engine.webDescriptor.assets.favicon
      .flatMap(_web_asset_href_content)
      .orElse(_web_global_asset_content(Vector("favicon.ico")))
      .orElse(_web_global_asset_content(Vector("favicon.svg")))
      .orElse(_web_global_asset_content(Vector("favicon.png")))

  private def _web_asset_href_content(
    href: String
  ): Option[(BinaryBag, MediaType)] = {
    val clean = href.trim.takeWhile(c => c != '?' && c != '#')
    val segments = clean.split("/").toVector.filter(_.nonEmpty)
    segments match {
      case Vector("web", "assets", tail*) if tail.nonEmpty =>
        _web_global_asset_content(tail.toVector)
      case Vector("web", component, app, "assets", tail*) if tail.nonEmpty =>
        _web_app_asset_content(Some(component), app, tail.toVector)
      case Vector("web", app, "assets", tail*) if tail.nonEmpty =>
        engine.webDescriptor.webRouteFor(Vector("web", app)) match {
          case Some(route) if route.remainingPath.isEmpty =>
            _web_app_asset_content(
              Some(route.target.normalizedComponent),
              route.target.normalizedApp,
              tail.toVector
            )
          case _ =>
            _web_app_asset_content(None, app, tail.toVector)
        }
      case _ =>
        None
    }
  }

  private[http] def _web_app_static_html_content(
    webAppName: String,
    page: Vector[String]
  ): Option[String] =
    _web_app_static_html_content(None, webAppName, page)

  private[http] def _web_app_static_html_content(
    componentName: Option[String],
    webAppName: String,
    page: Vector[String]
  ): Option[String] =
    if (!_safe_web_page_path(page))
      None
    else
      _web_app_static_html_candidates(webAppName, page).view.flatMap { path =>
        _component_content_web_roots(componentName).view.flatMap(_.readText(path))
      }.headOption

  private[http] def _web_app_static_page(
    componentName: Option[String],
    webAppName: String,
    page: Vector[String],
    content: String,
    req: org.http4s.Request[IO]
  ): Consequence[StaticFormAppRenderer.Page] =
    _web_app_static_page(componentName, webAppName, page, content, Some(req))

  private[http] def _web_app_static_page(
    componentName: Option[String],
    webAppName: String,
    page: Vector[String],
    content: String,
    req: Option[org.http4s.Request[IO]] = None
  ): Consequence[StaticFormAppRenderer.Page] = {
    val fullhtmldocument = _static_form_app_renderer.isHtmlDocumentTemplate(content)
    val composeSubsystemArticle =
      _compose_subsystem_article(webAppName, page) &&
        !fullhtmldocument
    _compose_web_template(
      webAppName,
      page,
      content,
      _static_page_layout(webAppName, page),
      allowImplicitDefault = !fullhtmldocument,
      subsystemShell = composeSubsystemArticle,
      requireLayout = composeSubsystemArticle,
      componentName = componentName
    ).map { composed =>
      val needsTemplateRendering =
        composed.appliedLayout ||
          _static_form_app_renderer.hasTextusMarkup(composed.html) ||
          _has_textus_include(composed.html) ||
          (!_static_form_app_renderer.isHtmlDocumentTemplate(composed.html) && _has_property_placeholder(composed.html))
      if (needsTemplateRendering)
        _static_form_app_renderer.renderStaticTemplate(
          webAppName,
          page,
          composed.html,
          _web_app_asset_completion(webAppName),
          _page_view_context(req, webAppName, page)
        )
      else
        StaticFormAppRenderer.Page(composed.html)
    }
  }

  private[http] def _web_app_static_page(
    webAppName: String,
    page: Vector[String],
    content: String
  ): Consequence[StaticFormAppRenderer.Page] =
    _web_app_static_page(None, webAppName, page, content)

  private def _page_view_context(
    req: Option[org.http4s.Request[IO]],
    webAppName: String,
    page: Vector[String]
  ): WebPageContext = {
    val sessionId = req.flatMap(_session_id_(_))
    val authenticated = req.exists(_is_page_context_authenticated)
    val jobCounts =
      if (authenticated)
        req.flatMap(_application_job_badge_counts(_, webAppName)).getOrElse(Http4sHttpServer.JobBadgeCounts.empty)
      else
        Http4sHttpServer.JobBadgeCounts.empty
    val base = WebPageContext(Map(
      "pageContext.session.authenticated" -> authenticated.toString,
      "pageContext.jobs.activeCount" -> jobCounts.active.toString,
      "pageContext.jobs.unconfirmedCount" -> jobCounts.unconfirmed.toString,
      "pageContext.jobs.visible" -> authenticated.toString,
      "pageContext.jobs.hidden" -> (if (authenticated) "" else "hidden"),
      "pageContext.jobs.activeBadgeHidden" -> (if (authenticated && jobCounts.active > 0) "" else "hidden"),
      "pageContext.jobs.unconfirmedBadgeHidden" -> (if (authenticated && jobCounts.unconfirmed > 0) "" else "hidden"),
      "pageContext.app" -> webAppName,
      "pageContext.page" -> (if (page.isEmpty) "index" else page.mkString("/"))
    ))
    base.merge(_page_context_from_providers(req, webAppName, page, sessionId, authenticated))
  }

  private def _is_page_context_authenticated(
    req: org.http4s.Request[IO]
  ): Boolean =
    _web_authorization_subject_from_session(req).exists(_.authenticated)

  private def _form_page_view_context_values(
    req: org.http4s.Request[IO],
    app: String,
    service: String,
    operation: String,
    values: Map[String, String]
  ): Map[String, String] = {
    val page = _form_result_template_page(service, operation, values)
    val webAppName = _form_result_web_app(app, page)
    _page_view_context(Some(req), webAppName, page).values
  }

  private def _page_context_from_providers(
    req: Option[org.http4s.Request[IO]],
    webAppName: String,
    page: Vector[String],
    sessionId: Option[String],
    authenticated: Boolean
  ): WebPageContext =
    req.flatMap { r =>
      _request_execution_context(r).toOption.map { ctx =>
        given ExecutionContext = ctx
        WebPageContextProviderRuntime.resolve(
          engine.runtimeSubsystem,
          WebPageContextRequest(
            app = webAppName,
            page = page,
            routePath = "/web/" + (webAppName +: page).mkString("/"),
            sessionId = sessionId,
            authenticated = authenticated
          )
        )
      }
    }.getOrElse(WebPageContext.empty)

  private def _application_job_badge_counts(
    req: org.http4s.Request[IO],
    app: String
  ): Option[Http4sHttpServer.JobBadgeCounts] =
    _session_id_(req).flatMap { sessionId =>
      _request_execution_context(req).toOption.map { ctx =>
        given ExecutionContext = ctx
        val seenAt = _application_job_seen_at.get((sessionId, NamingConventions.toNormalizedSegment(app)))
        val jobs = _visible_application_jobs(app)
        val active = jobs.count(_is_active_application_job)
        val unconfirmed = jobs.count(job => _is_terminal_application_job(job) && seenAt.forall(job.updatedAt.isAfter))
        Http4sHttpServer.JobBadgeCounts(active, unconfirmed)
      }
    }

  private def _visible_application_jobs(
    app: String
  )(using ExecutionContext): Vector[JobQueryReadModel] =
    engine.runtimeSubsystem.jobEngine
      .listJobs(limit = Int.MaxValue, persistentOnly = false)
      .filter(_job_belongs_to_app(_, app))
      .flatMap(model => engine.runtimeSubsystem.jobEngine.queryVisible(model.jobId).toOption.flatten)

  private def _is_active_application_job(
    model: JobQueryReadModel
  ): Boolean =
    model.status match {
      case JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended => true
      case _ => false
    }

  private def _is_terminal_application_job(
    model: JobQueryReadModel
  ): Boolean =
    model.status match {
      case JobStatus.Succeeded | JobStatus.Failed | JobStatus.Cancelled => true
      case _ => false
    }

  private def _compose_subsystem_article(
    webAppName: String,
    page: Vector[String]
  ): Boolean =
    engine.webDescriptor.appComposition(webAppName).isArticle &&
      engine.webDescriptor.staticPageMode(webAppName, page) == WebDescriptor.PageMode.Article

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

  private def _compose_web_template(
    webAppName: String,
    page: Vector[String],
    content: String,
    explicitLayout: Option[String],
    allowImplicitDefault: Boolean,
    subsystemShell: Boolean = false,
    requireLayout: Boolean = false,
    componentName: Option[String] = None
  ): Consequence[WebTemplateComposition] = {
    val normalizedLayout = explicitLayout.map(_.trim).filter(_.nonEmpty)
    val noLayout = normalizedLayout.exists(_.equalsIgnoreCase("none"))
    val layoutScope =
      if (subsystemShell) WebTemplatePartScope.SubsystemShell else WebTemplatePartScope.Default
    val contentScope =
      if (subsystemShell) WebTemplatePartScope.ComponentContent else WebTemplatePartScope.Default
    val withIncludes = _expand_template_partials(webAppName, page, content, contentScope, componentName)
    val layoutCandidates = _layout_candidates(normalizedLayout, allowImplicitDefault, subsystemShell)
    if (noLayout || (!requireLayout && _static_form_app_renderer.isHtmlDocumentTemplate(content) && layoutCandidates.isEmpty))
      Consequence.success(WebTemplateComposition(withIncludes, appliedLayout = false))
    else
      _validate_shell_owner_if_needed(subsystemShell).flatMap { _ =>
        _compose_with_layout_candidates(
          webAppName,
          page,
          withIncludes,
          layoutCandidates,
          explicitLayout = normalizedLayout.filterNot(_.equalsIgnoreCase("none")),
          layoutScope,
          componentName,
          requireLayout
        )
      }
  }

  private def _validate_shell_owner_if_needed(
    subsystemShell: Boolean
  ): Consequence[Unit] =
    if (subsystemShell) _validate_explicit_shell_owner() else Consequence.success(())

  private def _layout_candidates(
    normalizedLayout: Option[String],
    allowImplicitDefault: Boolean,
    subsystemShell: Boolean
  ): Vector[String] = {
    val explicit = normalizedLayout.filterNot(_.equalsIgnoreCase("none")).toVector
    val subsystemDefault =
      if (subsystemShell)
        engine.webDescriptor.shellLayoutName.toVector
      else
        Vector.empty
    val default =
      if (allowImplicitDefault && (subsystemShell || explicit.isEmpty))
        Vector("default")
      else
        Vector.empty
    (explicit ++ subsystemDefault ++ default).distinct
  }

  private def _compose_with_layout_candidates(
    webAppName: String,
    page: Vector[String],
    content: String,
    candidates: Vector[String],
    explicitLayout: Option[String],
    scope: WebTemplatePartScope,
    componentName: Option[String],
    requireLayout: Boolean
  ): Consequence[WebTemplateComposition] =
    candidates match {
      case Vector() =>
        Consequence.success(WebTemplateComposition(content, appliedLayout = false))
      case names =>
        val found = names.view.flatMap { name =>
          _layout_content(webAppName, name, scope, componentName).map(name -> _)
        }.headOption
        found match {
          case Some((name, layout)) =>
            _apply_layout(webAppName, page, name, layout, content, scope, componentName)
              .map(WebTemplateComposition(_, appliedLayout = true))
          case None =>
            explicitLayout match {
              case Some(name) if !scope.equals(WebTemplatePartScope.SubsystemShell) =>
                Consequence.resourceInvalid(
                  s"Static Form layout not found: ${name}",
                  Cause.Kind.Inconsistency,
                  Seq(
                    Descriptor.Facet.Name(name),
                    Descriptor.Facet.State("static-form-layout-not-found")
                  )
                )
              case _ if requireLayout =>
                val name = names.headOption.getOrElse("default")
                Consequence.resourceInvalid(
                  s"Static Form subsystem shell layout not found: ${name}",
                  Cause.Kind.Inconsistency,
                  Seq(
                    Descriptor.Facet.Name(name),
                    Descriptor.Facet.State("subsystem-shell-layout-not-found")
                  )
                )
              case _ =>
                Consequence.success(WebTemplateComposition(content, appliedLayout = false))
            }
        }
    }

  private def _apply_layout(
    webAppName: String,
    page: Vector[String],
    layoutName: String,
    layout: String,
    content: String,
    scope: WebTemplatePartScope,
    componentName: Option[String] = None
  ): Consequence[String] =
    if (!layout.contains("${content}"))
      Consequence.resourceInvalid(
        s"Static Form layout lacks $${content} slot: ${layoutName}",
        Cause.Kind.Inconsistency,
        Seq(
          Descriptor.Facet.Name(layoutName),
          Descriptor.Facet.Expected("${content}"),
          Descriptor.Facet.State("static-form-layout-missing-content-slot")
        )
      )
    else {
      val withContent = layout.replace("${content}", content)
      Consequence.success(_expand_template_partials(webAppName, page, withContent, scope, componentName))
    }

  private def _expand_template_partials(
    webAppName: String,
    page: Vector[String],
    template: String,
    scope: WebTemplatePartScope = WebTemplatePartScope.Default,
    componentName: Option[String] = None
  ): String = {
    @annotation.tailrec
    def loop(value: String, remaining: Int): String =
      if (remaining <= 0)
        value
      else {
        val next = _expand_textus_includes(webAppName, page, _expand_partial_placeholders(webAppName, page, value, scope, componentName), scope, componentName)
        if (next == value) next else loop(next, remaining - 1)
      }
    loop(template, 8)
  }

  private def _expand_partial_placeholders(
    webAppName: String,
    page: Vector[String],
    template: String,
    scope: WebTemplatePartScope,
    componentName: Option[String] = None
  ): String =
    """\$\{partial\.([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(_partial_content(webAppName, page, m.group(1), scope, componentName).getOrElse(""))
    )

  private def _expand_textus_includes(
    webAppName: String,
    page: Vector[String],
    template: String,
    scope: WebTemplatePartScope,
    componentName: Option[String] = None
  ): String = {
    val include = """<textus(?::include|-include)\b([^>]*)></textus(?::include|-include)>""".r
    include.replaceAllIn(template, m => {
      val attrs = _template_attrs(m.group(1))
      java.util.regex.Matcher.quoteReplacement(
        attrs.get("name").flatMap(_partial_content(webAppName, page, _, scope, componentName)).getOrElse("")
      )
    })
  }

  private def _has_textus_include(template: String): Boolean =
    """<textus(?::include|-include)\b""".r.findFirstIn(template).nonEmpty

  private def _has_property_placeholder(template: String): Boolean =
    """\$\{[A-Za-z0-9_.-]+\}""".r.findFirstIn(template).nonEmpty

  private def _layout_content(
    webAppName: String,
    name: String,
    scope: WebTemplatePartScope = WebTemplatePartScope.Default,
    componentName: Option[String] = None
  ): Option[String] =
    if (!_safe_template_part_name(name))
      None
    else
      _web_resource_roots(scope, componentName).view.flatMap { root =>
        _web_inf_layout_candidates(_template_part_app_name(webAppName, scope), name).view.flatMap(root.readText)
      }.headOption

  private def _partial_content(
    webAppName: String,
    page: Vector[String],
    name: String,
    scope: WebTemplatePartScope = WebTemplatePartScope.Default,
    componentName: Option[String] = None
  ): Option[String] =
    if (!_safe_template_part_name(name))
      None
    else
      _web_resource_roots(scope, componentName).view.flatMap { root =>
        _web_inf_partial_candidates(_template_part_app_name(webAppName, scope), page, name, scope).view.flatMap(root.readText)
      }.headOption

  private def _template_part_app_name(
    webAppName: String,
    scope: WebTemplatePartScope
  ): String =
    scope match {
      case WebTemplatePartScope.SubsystemShell =>
        engine.webDescriptor.shellAppName.getOrElse(webAppName)
      case _ =>
        webAppName
    }

  private def _web_inf_layout_candidates(
    webAppName: String,
    name: String
  ): Vector[Path] = {
    val webAppPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(webAppName)
    _flat_or_app_named_candidates(webAppName, Vector(
      Paths.get("WEB-INF", "layouts", s"${name}.html"),
      Paths.get(webAppPath, "WEB-INF", "layouts", s"${name}.html")
    ))
  }

  private def _web_inf_partial_candidates(
    webAppName: String,
    page: Vector[String],
    name: String,
    scope: WebTemplatePartScope
  ): Vector[Path] = {
    val webAppPath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(webAppName)
    val localPage = _web_inf_page_path(page)
    val localFlat = localPage.toVector.map { pagePath =>
      _relative_path(Vector("WEB-INF", "partials") ++ pagePath :+ s"${name}.html")
    }
    val localApp = localPage.toVector.map { pagePath =>
      _relative_path(webAppPath +: (Vector("WEB-INF", "partials") ++ pagePath :+ s"${name}.html"))
    }
    val globalFlat = Vector(Paths.get("WEB-INF", "partials", s"${name}.html"))
    val globalApp = Vector(Paths.get(webAppPath, "WEB-INF", "partials", s"${name}.html"))
    scope match {
      case WebTemplatePartScope.ComponentContent =>
        localApp ++ localFlat ++ globalApp ++ globalFlat
      case _ =>
        if (_is_flat_web_root_app(webAppName))
          localFlat ++ localApp ++ globalFlat ++ globalApp
        else
          localApp ++ localFlat ++ globalApp ++ globalFlat
    }
  }

  private def _web_inf_page_path(
    page: Vector[String]
  ): Option[Vector[String]] = {
    val normalized =
      if (page.isEmpty) Vector("index")
      else page.map(_normalize_web_page_segment).map(_.stripSuffix(".html"))
    Option.when(normalized.nonEmpty && normalized.forall(_safe_template_part_name))(normalized)
  }

  private def _static_page_layout(
    webAppName: String,
    page: Vector[String]
  ): Option[String] =
    engine.webDescriptor.staticPageCustomization(webAppName, page).flatMap(_.layout)
      .orElse(engine.webDescriptor.appLayout(webAppName))

  private def _form_layout(
    app: String,
    service: String,
    operation: String
  ): Option[String] =
    _form_descriptor(app, service, operation).flatMap(_.layout)
      .orElse(engine.webDescriptor.appLayout(app))

  private def _web_app_asset_completion(
    webAppName: String
  ): StaticFormAppLayout.AssetCompletionOptions = {
    val assets = engine.webDescriptor.assets.merge(engine.webDescriptor.appAssets(webAppName))
    StaticFormAppLayout.AssetCompletionOptions(
      declaredCss = assets.css,
      declaredJs = assets.js,
      favicon = assets.favicon
    )
  }

  private def _safe_template_part_name(
    value: String
  ): Boolean =
    value.nonEmpty && value.forall { c =>
      c.isLetterOrDigit || c == '-' || c == '_' || c == '.'
    }

  private def _template_attrs(
    text: String
  ): Map[String, String] = {
    val attr = """([A-Za-z0-9_.:-]+)\s*=\s*"([^"]*)"""".r
    attr.findAllMatchIn(text).map(m => m.group(1) -> m.group(2)).toMap
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
        !segment.contains("\\") &&
        org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(segment) != "web-inf"
    }

  private def _safe_document_path(
    page: Vector[String]
  ): Boolean =
    page.nonEmpty && page.forall { segment =>
      segment.nonEmpty &&
        !segment.contains("..") &&
        !segment.contains("/") &&
        !segment.contains("\\") &&
        org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(segment) != "web-inf"
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
      case x if x.endsWith(".md") || x.endsWith(".markdown") => _media_type("text/markdown")
      case x if x.endsWith(".pdf") => _media_type("application/pdf")
      case x if x.endsWith(".svg") => _media_type("image/svg+xml")
      case x if x.endsWith(".ico") => _media_type("image/x-icon")
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
    val formValues = _form_values(form)
    val resultValues = _result_values(response)
    val resultId = _redirect_result_id(response, formValues, resultValues)
    val values =
      formValues ++ Map(
        "component" -> app,
        "service" -> service,
        "operation" -> operation,
        "result.status" -> response.code.toString,
        "result.body" -> response.getString.getOrElse("")
      ) ++ resultValues ++ resultId.map("result.id" -> _).toMap
    """\$\{([A-Za-z0-9_.-]+)\}""".r.replaceAllIn(template, m =>
      java.util.regex.Matcher.quoteReplacement(values.getOrElse(m.group(1), ""))
    )
  }

  private def _redirect_result_id(
    response: HttpResponse,
    formValues: Map[String, String],
    resultValues: Map[String, String]
  ): Option[String] =
    resultValues.get("result.id") match {
      case Some(id) if _response_body_is_json(response) =>
        Some(id)
      case Some(id) =>
        Some(_normalize_redirect_result_id(id))
      case None =>
        formValues.get("id")
    }

  private def _normalize_redirect_result_id(id: String): String = {
    val firstline = id.linesIterator.take(1).toVector.headOption.getOrElse(id).trim
    firstline.split(":", 2).toList match {
      case _ :: suffix :: Nil => suffix.trim
      case _ => firstline
    }
  }

  private def _response_body_is_json(response: HttpResponse): Boolean =
    response.getString.exists(body => io.circe.parser.parse(body).toOption.exists(_.isObject))

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
    val returnto = _login_return_to(req.uri.query.params).getOrElse(s"/web/${_escape_path_segment(app)}")
    val hiddenreturnto =
      s"""<input type="hidden" name="returnTo" value="${_escape_html(returnto)}">"""
    _html(
      StaticFormAppRenderer.Page(
        s"""<!doctype html>
           |<html lang="en">
           |<head>
           |  <meta charset="utf-8">
           |  <meta name="viewport" content="width=device-width, initial-scale=1">
           |  <title>${_escape_html(app)} Login</title>
           |  <link href="/web/assets/bootstrap.min.css" rel="stylesheet">
           |</head>
           |<body class="bg-light">
           |  <main class="container py-5">
           |    <div class="row justify-content-center">
           |      <div class="col-12 col-md-6 col-lg-4">
           |        <div class="card shadow-sm">
           |          <div class="card-body">
           |            <h1 class="h4 mb-3">${_escape_html(app)} Login</h1>
           |            ${error.map(e => s"""<div class="alert alert-danger" role="alert">${_escape_html(e)}</div>""").getOrElse("")}
           |            <form method="post" action="/web/${_escape_path_segment(app)}/login">
           |              $hiddenreturnto
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
                  _see_other(_login_return_to(req.uri.query.params ++ formAttributes).getOrElse(s"/web/${app}"))
                    .addCookie(_session_cookie(sessionid))
                )
              case org.goldenport.Consequence.Failure(c) =>
                if (_has_exact_web_route_alias(Vector("web", app, "login")))
                  IO.pure(_see_other(_login_error_redirect(app, c.displayMessage, _login_return_to(req.uri.query.params ++ formAttributes))))
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
            _web_error_response(Some(app), c, s"/web/${app}/logout", req.method.name)
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
            _web_error_response(Some(app), c, s"/web/${app}/session", req.method.name)
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

  private def _has_exact_web_route_alias(
    path: Vector[String]
  ): Boolean =
    engine.webDescriptor.webRouteFor(path).isDefined

  private def _login_error_redirect(
    app: String,
    error: String,
    returnto: Option[String]
  ): String = {
    val base = Uri.unsafeFromString(s"/web/${_escape_path_segment(app)}/login")
      .withQueryParam("error", error)
    returnto.fold(base)(x => base.withQueryParam("returnTo", x)).renderString
  }

  private def _login_return_to(
    params: Map[String, String]
  ): Option[String] =
    params.get("returnTo")
      .map(_.trim)
      .filter(_non_empty_local_web_path)

  private def _non_empty_local_web_path(
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

  private def _request_execution_context(
    req: org.http4s.Request[IO]
  ): Consequence[ExecutionContext] = {
    val attributes = _request_header_record(req).asMap.map { case (key, value) =>
      key -> Option(value).map(_.toString).getOrElse("")
    }
    IngressSecurityResolver.resolve(ExecutionContext.create(), attributes).map(_.executionContext)
  }

  private def _development_form_header_record(
    req: org.http4s.Request[IO],
    query: Record = Record.empty,
    form: Record = Record.empty
  ): Record = {
    val base = _request_header_record(req, query, form)
    if (!_is_development_operation_mode(_operation_mode))
      base
    else {
      val pairs = _with_header_if_absent(
        _with_header_if_absent(base.asMap.toVector, "x-textus-debug-calltree", "true"),
        "x-textus-debug-save-calltree",
        "true"
      )
      Record.create(pairs)
    }
  }

  private def _with_header_if_absent(
    pairs: Vector[(String, Any)],
    key: String,
    value: String
  ): Vector[(String, Any)] =
    if (pairs.exists { case (k, _) => k.equalsIgnoreCase(key) })
      pairs
    else
      pairs :+ (key -> value)

  private def _is_development_operation_mode(
    mode: OperationMode
  ): Boolean =
    mode == OperationMode.Develop || mode == OperationMode.Test

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
        _session_cookie_names(req).collectFirst(Function.unlift { name =>
          req.cookies.collectFirst {
            case cookie if cookie.name.equalsIgnoreCase(name) => cookie.content
          }.map(_.trim).filter(_.nonEmpty)
        })
      }
      .orElse(form.getString("x-textus-session").map(_.trim).filter(_.nonEmpty))
      .orElse(query.getString("x-textus-session").map(_.trim).filter(_.nonEmpty))

  private def _session_cookie_names(req: org.http4s.Request[IO]): Vector[String] =
    (_session_cookie_name +: _web_app_session_cookie_name(req).toVector).distinct

  private def _session_cookie_name: String =
    s"textus-session-${NamingConventions.toNormalizedSegment(engine.runtimeSubsystem.name)}"

  private def _web_app_session_cookie_name(req: org.http4s.Request[IO]): Option[String] = {
    val segments = req.uri.path.segments.map(_.decoded()).toVector
    segments match {
      case Vector("web", app, _*) if app.nonEmpty =>
        Some(s"textus-session-${NamingConventions.toNormalizedSegment(app)}")
      case _ =>
        None
    }
  }

  private def _session_cookie(
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

  private def _escape_html(p: String): String =
    Option(p).getOrElse("")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")

  private def _escape_path_segment(p: String): String =
    java.net.URLEncoder.encode(Option(p).getOrElse(""), StandardCharsets.UTF_8)

  private def _escape_uri_path_segment(p: String): String =
    java.net.URLEncoder.encode(Option(p).getOrElse(""), StandardCharsets.UTF_8).replace("+", "%20")

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
          engine.webDescriptor.exposureOf(selector) match {
            case WebDescriptor.Exposure.Protected =>
              subject.normalized.authenticated ||
                (
                  runtimeConfig.operationMode != org.goldenport.cncf.config.OperationMode.Production &&
                    app != "debug"
                )
            case _ =>
              true
          }
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
        _web_authorization_subject_from_session(r).getOrElse {
          val subject = WebDescriptorAuthorization.Subject.fromHttp(r)
          if (!subject.anonymous || _session_id_(r).isEmpty)
            subject
          else
            subject.copy(anonymous = false)
        }
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
    _web_error_response(component, error)
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
    _web_error_response(app, error)
  }

  private[http] def _web_error_response(
    app: Option[String],
    conclusion: Conclusion,
    path: String,
    method: String = "GET"
  ): IO[HResponse[IO]] = {
    val error = StructuredHttpError.fromConclusion(
      conclusion,
      path,
      method,
      _operation_mode,
      component = app
    )
    _web_error_response(app, error)
  }

  private def _web_error_response(
    app: Option[String],
    error: StructuredHttpError
  ): IO[HResponse[IO]] = {
    val resolvedstatus = _http_status(error)
    _web_error_template(app, resolvedstatus.code) match {
      case Some(template) =>
        _html_status(
          _static_form_app_renderer.renderErrorTemplate(app, resolvedstatus.code, error.message, error.path, Some(error), template),
          resolvedstatus
        )
      case None =>
        _html_status(_static_form_app_renderer.renderStructuredErrorPage(app, error), resolvedstatus)
    }
  }

  private def _http_status(error: StructuredHttpError): HStatus =
    HStatus.fromInt(error.status).getOrElse(HStatus.InternalServerError)

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
    _static_form_app_renderer.isHiddenFormContextKey(key)

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
    val debugJs =
      if (_is_development_operation_mode(_operation_mode))
        Vector("/web/assets/textus-form-debug.js", "/web/assets/textus-calltree.js")
      else
        Vector.empty
    StaticFormAppLayout.completeDeclaredAssets(
      themed,
      StaticFormAppLayout.AssetCompletionOptions(
        declaredCss = baseAssets.css,
        declaredJs = baseAssets.js ++ debugJs,
        favicon = baseAssets.favicon
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
          .withEntity(_plain_structured_error_body(error))
          .withContentType(`Content-Type`(MediaType.text.plain, Some(Charset.`UTF-8`)))
      )
    else
      _error_json_response(error)

  private def _plain_structured_error_body(
    error: StructuredHttpError
  ): String =
    Vector(
      Some(error.message),
      Some(s"status: ${error.status}"),
      Some(s"statusText: ${error.statusText}"),
      error.detailCode.map(x => s"detailCode: ${x}"),
      error.appCode.map(x => s"appCode: ${x}"),
      error.appStatus.map(x => s"appStatus: ${x}")
    ).flatten.mkString("\n")

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
    val origin = _request_origin(req)
    val context = HttpContext(
      scheme = req.uri.scheme.map(_.value).orElse(origin.map(_._1)),
      authority = req.uri.authority.map(_.renderString).orElse(origin.map(_._2)),
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

  private def _request_origin(
    req: org.http4s.Request[IO]
  ): Option[(String, String)] = {
    val scheme =
      req.uri.scheme.map(_.value)
        .orElse(_header_value(req, "X-Forwarded-Proto").map(_.takeWhile(_ != ',').trim).filter(_.nonEmpty))
        .orElse(Some("http"))
    val authority =
      req.uri.authority.map(_.renderString)
        .orElse(_header_value(req, "X-Forwarded-Host").map(_.takeWhile(_ != ',').trim).filter(_.nonEmpty))
        .orElse(_header_value(req, "Host").map(_.trim).filter(_.nonEmpty))
    for {
      s <- scheme
      a <- authority
    } yield s -> a
  }

  private def _header_value(
    req: org.http4s.Request[IO],
    name: String
  ): Option[String] =
    req.headers.headers
      .find(_.name.toString.equalsIgnoreCase(name))
      .map(_.value)
      .map(_.trim)
      .filter(_.nonEmpty)

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
    req.body.compile.to(Array).flatMap { bytes =>
      if (_is_empty_multipart_body(bytes.toArray)) {
        IO.pure(Vector.empty)
      } else {
        val reread = req.withBodyStream(Stream.emits(bytes.toSeq).covary[IO])
        _multipart_form_entries_from_request(reread)
      }
    }

  private def _multipart_form_entries_from_request(
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

  private def _is_empty_multipart_body(
    bytes: Array[Byte]
  ): Boolean = {
    val text = new String(bytes, StandardCharsets.ISO_8859_1).trim
    text.isEmpty || text.matches("""(?s)^--[^\r\n]+--$""")
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
      pageSize = params.get("pageSize").flatMap(_.toIntOption).filter(_ > 0)
        .getOrElse(_static_form_app_renderer.config.defaultPageSize),
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

  private[http] def fallbackHttpDiagnosticRecord(
    status: Int
  ): Record =
    Record.data(
      "webStatus" -> status,
      "statusText" -> StructuredHttpError.statusText(status)
    ) ++ ConclusionDiagnostics.unknown.toRecord

  final case class JobBadgeCounts(
    active: Int,
    unconfirmed: Int
  )
  object JobBadgeCounts {
    val empty: JobBadgeCounts = JobBadgeCounts(0, 0)
  }

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
