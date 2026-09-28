package org.goldenport.cncf.http

import scala.collection.mutable.ListBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.{ZipEntry, ZipOutputStream}
import cats.data.State
import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyVector
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{MediaType, Method, Request, Uri}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.http.{HttpContext, HttpRequest, HttpResponse}
import org.goldenport.http.HttpStatus
import org.goldenport.bag.Bag
import org.goldenport.datatype.{ContentType, MimeBody, MimeType}
import org.goldenport.value.BaseContent
import org.goldenport.protocol.{Argument, Property, Protocol, Request as GRequest}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, WebValidationHints, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentFactory, ComponentId, ComponentInstanceId, ComponentletDescriptor}
import org.goldenport.cncf.testutil.DevelopmentRuntimeManifestFixture
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, PrincipalId, RuntimeContext}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, QueryDirective, SearchResult, SearchableDataStore, TotalCountCapability}
import org.goldenport.cncf.entity.{
  EntityConcurrencyMetadata,
  EntityMutationAdapterDefaults,
  EntityPersistent,
  EntityRevisionBinding,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityRevisionTransport,
  EntityStoreSpace
}
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection, AggregateCommandDefinition, AggregateCreateDefinition, AggregateDefinition, AggregateMemberDefinition}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection, ViewDefinition, ViewQueryDefinition}
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationDefinition, CmlOperationField, CmlOperationImageBinding, CmlOperationUpdateField}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.information.*
import org.goldenport.cncf.knowledge.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{PrepareResult, TransactionContext}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class StaticFormAppRendererHttpDispatchSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "StaticFormAppRenderer" must _in_phase53_spec {
    "provide HTTP operation dispatch and response contracts" which {
    "execute aggregate create/update actions through an HTTP ingress-capable component" in {
      Given("the prerequisites for execute aggregate create/update actions through an HTTP ingress-capable component")
      val subsystem = _aggregate_http_fixture_subsystem()
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val createhtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/create-notice-aggregate", "title=hello"),
          "notice-board",
          "notice-aggregate",
          "create-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()
      When("execute aggregate create/update actions through an HTTP ingress-capable component is exercised")
      val updatehtml = server
        ._submit_operation_form(
          _post_form_request("/form/notice-board/notice-aggregate/approve-notice-aggregate", "id=notice_1&approved=true"),
          "notice-board",
          "notice-aggregate",
          "approve-notice-aggregate"
        )
        .flatMap(_.as[String])
        .unsafeRunSync()

      Then("the observable contract for execute aggregate create/update actions through an HTTP ingress-capable component holds")
      createhtml should include ("result.status")
      createhtml should include ("200")
      createhtml should include ("aggregate-created:hello")
      createhtml should not include ("HTTP ingress not configured")
      updatehtml should include ("result.status")
      updatehtml should include ("200")
      updatehtml should include ("aggregate-updated:notice_1")
      updatehtml should not include ("HTTP ingress not configured")
    }

    "build REST operation dispatch requests without executing local operation logic" in {
      Given("the prerequisites for build REST operation dispatch requests without executing local operation logic")
      val driver = new RecordingRestDriver
      val dispatcher = WebOperationDispatcher.Rest("http://app.example/base", driver)
      val request = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/notice-board/notice-aggregate/create-notice-aggregate",
        query = Record.data("page" -> "1"),
        header = Record.data("X-Trace-Id" -> "trace-1"),
        form = Record.data("title" -> "hello world")
      )

      When("build REST operation dispatch requests without executing local operation logic is exercised")
      val response = dispatcher.dispatch(request)

      Then("the observable contract for build REST operation dispatch requests without executing local operation logic holds")
      response.code shouldBe 200
      driver.calls should contain (
        RecordingRestDriver.Call(
          method = "POST",
          path = "http://app.example/base/notice-board/notice-aggregate/create-notice-aggregate?page=1",
          body = Some("title=hello+world"),
          headers = Map("X-Trace-Id" -> "trace-1")
        )
      )
    }

    "canonicalize only the first WebPath component segment before recording and remote REST dispatch" in {
      Given("a Web alias request with independent transport fields and a recording remote dispatcher")
      val subsystem = _form_type_fixture_subsystem()
      val driver = new RecordingRestDriver
      val recorder = new RecordingWebOperationDispatcher(
        WebOperationDispatcher.Rest("http://app.example/base", driver)
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem),
        operationDispatcherOption = Some(recorder)
      )
      val context = HttpContext(
        Some("https"),
        Some("web.example"),
        Some("https://web.example/notice-board/notice/post-secret-notice")
      )
      val request = HttpRequest.fromPath(
        method = HttpRequest.POST,
        path = "/notice-board/notice/post-secret-notice",
        query = Record.data("page" -> "1"),
        header = Record.data("X-Trace-Id" -> "trace-1"),
        form = Record.data("title" -> "hello world"),
        body = Some(Bag.text("body payload", StandardCharsets.UTF_8)),
        context = context
      )

      When("the Web operation dispatcher crossing is invoked with the presentation path")
      val response = server._dispatch_operation(
        "notice-board",
        "notice",
        "post-secret-notice",
        request
      )
      val canonical = recorder.requests.lastOption.getOrElse(fail("recorded request is missing"))

      Then("only the first segment becomes the dotted canonical ComponentId and the remote dispatcher receives that path")
      response.code shouldBe 200
      canonical.path.asString shouldBe "/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice"
      canonical.method shouldBe request.method
      canonical.query shouldBe request.query
      canonical.form shouldBe request.form
      canonical.header shouldBe request.header
      canonical.body.map(_.asStringUnsafe()) shouldBe request.body.map(_.asStringUnsafe())
      canonical.context shouldBe request.context
      driver.calls should contain (
        RecordingRestDriver.Call(
          method = "POST",
          path = "http://app.example/base/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice?page=1",
          body = Some("body payload"),
          headers = Map("X-Trace-Id" -> "trace-1")
        )
      )
    }

    "resolve a stable Web alias while retaining canonical Form API identity" in {
      Given("a stable admitted Web alias and a canonical component identity")
      val subsystem = _form_type_fixture_subsystem()
      val componentalias = "notice-board"
      val canonicalcomponentpath = "org-goldenport-cncf-test-notice-board"

      val definition = _renderer.renderOperationFormDefinition(
        subsystem,
        componentalias,
        "notice",
        "post_secret_notice"
      ).map(_.body).getOrElse(fail("operation form definition is missing"))
      When("the Web alias is resolved to its form definition")
      val json = parse(definition).getOrElse(fail("form definition JSON is invalid"))

      Then("the alias resolves without replacing canonical Component or Form API identity")
      json.hcursor.downField("selector").as[String].toOption shouldBe Some(s"${canonicalcomponentpath}.notice.post-secret-notice")
      json.hcursor.downField("submitPath").as[String].toOption shouldBe Some(s"/form-api/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("htmlPath").as[String].toOption shouldBe Some(s"/form/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(0).downField("path").as[String].toOption shouldBe Some(s"/form/${canonicalcomponentpath}/notice/post-secret-notice")
      json.hcursor.downField("actions").downN(1).downField("path").as[String].toOption shouldBe Some(s"/form-api/${canonicalcomponentpath}/notice/post-secret-notice")
    }

    "dispatch Form API POST to the canonical REST operation request" in {
      Given("the prerequisites for dispatch Form API POST to the canonical REST operation request")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      When("dispatch Form API POST to the canonical REST operation request is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice?dryRun=true",
            "body=hello&accessToken=abc&crud.origin.href=/web/notice-board"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for dispatch Form API POST to the canonical REST operation request holds")
      response.status.code shouldBe 200
      dispatcher.paths should contain ("/org.goldenport.cncf.test.NoticeBoard/notice/post-secret-notice")
      dispatcher.forms.last.getString("body") shouldBe Some("hello")
      dispatcher.forms.last.getString("accessToken") shouldBe Some("abc")
      dispatcher.forms.last.getString("crud.origin.href") shouldBe None
    }

    "reject duplicate canonical aliases before Form API operation dispatch" in {
      Given("the prerequisites for reject duplicate canonical aliases before Form API operation dispatch")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice",
            "body=hello&accessToken=abc&access_token=def"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("reject duplicate canonical aliases before Form API operation dispatch is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for reject duplicate canonical aliases before Form API operation dispatch holds")
      response.status.code shouldBe 400
      body should include ("Duplicate property aliases after canonical naming")
      body should include ("accessToken")
      dispatcher.forms shouldBe empty
    }

    "promote x-textus-session from form payload into Form API auth headers" in {
      Given("the prerequisites for promote x-textus-session from form payload into Form API auth headers")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      When("promote x-textus-session from form payload into Form API auth headers is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/notice-board/notice/post-secret-notice",
            "body=hello&x-textus-session=form-session"
          ),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for promote x-textus-session from form payload into Form API auth headers holds")
      response.status.code shouldBe 200
      dispatcher.headers.last.getString("x-textus-session") shouldBe Some("form-session")
    }

    "keep internal operations invisible from HTML and Form API surfaces" in {
      Given("the prerequisites for keep internal operations invisible from HTML and Form API surfaces")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Internal))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor)))

      val htmlform = _renderer.renderOperationForm(
        subsystem,
        "notice-board",
        "notice",
        "post-secret-notice",
        descriptor
      )
      When("keep internal operations invisible from HTML and Form API surfaces is exercised")
      val apiresponse = server
        ._operation_form_api_definition(
          _get_request("/form-api/notice-board/notice/post-secret-notice"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for keep internal operations invisible from HTML and Form API surfaces holds")
      descriptor.isFormEnabled(selector) shouldBe false
      htmlform shouldBe None
      apiresponse.status.code shouldBe 404
    }

    "return structured JSON error envelope from Form API failures" in {
      Given("the prerequisites for return structured JSON error envelope from Form API failures")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("bad request from operation", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )

      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice", "body=hello"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      When("return structured JSON error envelope from Form API failures is exercised")
      val json = parse(body).getOrElse(fail(body)).hcursor

      Then("the observable contract for return structured JSON error envelope from Form API failures holds")
      response.status.code shouldBe 400
      response.contentType.map(_.mediaType) shouldBe Some(MediaType.application.json)
      json.downField("error").get[String]("message") shouldBe Right("bad request from operation")
      json.downField("error").get[Int]("status") shouldBe Right(400)
      json.downField("error").get[String]("statusText") shouldBe Right("Bad Request")
      json.downField("error").downField("codeSource").succeeded shouldBe false
      json.downField("error").downField("code").succeeded shouldBe false
      json.downField("error").downField("debug").get[String]("path") shouldBe Right("/form-api/notice-board/notice/post-secret-notice")
    }

    "return structured YAML error envelope from Form API failures when requested" in {
      Given("the prerequisites for return structured YAML error envelope from Form API failures when requested")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new StaticWebOperationDispatcher(
        HttpResponse.Text(
          HttpStatus.BadRequest,
          ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
          Bag.text("bad request from operation", StandardCharsets.UTF_8)
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )
      val request = _post_form_request(
        "/form-api/notice-board/notice/post-secret-notice",
        "body=hello"
      ).putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Accept"), "application/yaml"))

      val response = server
        ._submit_operation_form_api(
          request,
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()
      When("return structured YAML error envelope from Form API failures when requested is exercised")
      val body = response.as[String].unsafeRunSync()

      Then("the observable contract for return structured YAML error envelope from Form API failures when requested holds")
      response.status.code shouldBe 400
      response.contentType.map(_.mediaType.toString).getOrElse("") should include ("application/yaml")
      body should include ("error:")
      body should include ("message: bad request from operation")
      body should include ("status: 400")
      body should include ("statusText: Bad Request")
    }

    "record minimal runtime hooks when Web dispatch crosses the operation adapter" in {
      Given("the prerequisites for record minimal runtime hooks when Web dispatch crosses the operation adapter")
      val subsystem = _form_type_fixture_subsystem()
      val selector = "notice-board.notice.post-secret-notice"
      val descriptor = WebDescriptor(expose = Map(selector -> WebDescriptor.Exposure.Protected))
      val dispatcher = new RecordingWebOperationDispatcher(
        new StaticWebOperationDispatcher(
          HttpResponse.Text(
            HttpStatus.Ok,
            ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
            Bag.text("posted", StandardCharsets.UTF_8)
          )
        )
      )
      val server = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(descriptor)),
        operationDispatcherOption = Some(dispatcher)
      )
      val beforehtml = RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total
      val beforedsl = RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total
      val beforeauthorization = RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total

      When("record minimal runtime hooks when Web dispatch crosses the operation adapter is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/notice-board/notice/post-secret-notice", "body=hello"),
          "notice-board",
          "notice",
          "post-secret-notice"
        )
        .unsafeRunSync()

      Then("the observable contract for record minimal runtime hooks when Web dispatch crosses the operation adapter holds")
      response.status.code shouldBe 200
      RuntimeDashboardMetrics.htmlSnapshot.summary.cumulative.total shouldBe (beforehtml + 1)
      RuntimeDashboardMetrics.dslChokepointSnapshot.summary.cumulative.total shouldBe (beforedsl + 1)
      RuntimeDashboardMetrics.authorizationDecisionSnapshot.summary.cumulative.total shouldBe (beforeauthorization + 1)
    }

    "render resolved Web Descriptor summary on component admin page" in {
      Given("the prerequisites for render resolved Web Descriptor summary on component admin page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        expose = Map(s"${componentpath}.service.operation" -> WebDescriptor.Exposure.Protected),
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentpath}/dashboard", "dashboard"))
      )

      When("render resolved Web Descriptor summary on component admin page is exercised")
      val html = _renderer.renderComponentAdmin(subsystem, component.name, descriptor).map(_.body).getOrElse(fail("component admin is missing"))

      Then("the observable contract for render resolved Web Descriptor summary on component admin page holds")
      html should include ("Web Descriptor")
      html should include ("configured")
      html should include (s"${componentpath}.service.operation")
      html should include ("protected")
      html should include ("component-dashboard")
    }

    "render system performance detail page" in {
      Given("the prerequisites for render system performance detail page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/system/dashboard", 200, 12L)
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/missing", 404, 34L)
      RuntimeDashboardMetrics.recordAuthorizationDecision(denied = true, Some("capability"))

      When("render system performance detail page is exercised")
      val html = _renderer.renderSystemPerformance(subsystem).body

      Then("the observable contract for render system performance detail page holds")
      html should include ("System Performance")
      html should include ("/web/assets/bootstrap.min.css")
      html should not include ("cdn.jsdelivr")
      html should include ("nav nav-pills")
      html should include ("card admin-card")
      html should include ("row g-3")
      html should include ("table-responsive")
      html should include ("admin-action-row")
      html should include ("id=\"html-requests\"")
      html should include ("id=\"recent-errors\"")
      html should include ("id=\"authorization\"")
      html should include ("id=\"jobs\"")
      html should include ("HTML request")
      html should include ("Latency")
      html should include ("Recent requests")
      html should include ("Recent errors")
      html should include ("HTTP 4xx/5xx entries shown as Dashboard recent failures")
      html should include ("ActionCall")
      html should include ("DSL Chokepoints")
      html should include ("Authorization")
      html should include ("Diagnostic")
      html should include ("capability")
      html should include ("Jobs")
      html should include ("Assembly warnings")
      html should include ("/web/system/admin/assembly/warnings")
      html should include ("/web/system/admin/assembly/report")
      html should include ("/form/admin/execution/history")
      html should include ("/form/admin/execution/calltree")
      html should include ("/web/system/dashboard")
      html should include ("/missing")
      html should include ("34 ms")
      html should include ("/web/system/dashboard")
      html should include ("/web/system/admin")
      html should include ("/web/system/admin/observability")
      html should include ("/web/system/admin/observability/metrics")
      html should include ("/web/system/admin/observability/diagnostics/authorization/capability")
      html should include ("/man/system")
      html should include ("/web/console")
    }

    "render structured observability metrics page" in {
      Given("the prerequisites for render structured observability metrics page")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/test", 200, 15L)
      RuntimeDashboardMetrics.recordHtmlRequest("GET", "/web/missing", 404, 25L)
      RuntimeDashboardMetrics.recordActionCall(error = false, Some(7L))
      RuntimeDashboardMetrics.recordBlobOperation(
        operation = "blob.content.get",
        error = true,
        diagnosticKey = Some("ob05_blob"),
        kind = Some("content"),
        sourceMode = Some("managed"),
        backend = Some("local-file")
      )
      RuntimeDashboardMetrics.recordDiagnosticPayloadExternalization("result", "stored", "local-file")
      subsystem.entityAccessMetrics.record(
        "entity.search",
        Record.dataAuto(
          "entity" -> "notice",
          "source" -> "datastore",
          "outcome" -> "success"
        )
      )

      val html = _renderer.renderSystemAdminObservabilityMetrics(subsystem).body
      val home = _renderer.renderSystemAdminObservability(subsystem).body
      When("render structured observability metrics page is exercised")
      val performance = _renderer.renderSystemPerformance(subsystem).body

      Then("the observable contract for render structured observability metrics page holds")
      html should include ("Observability Metrics")
      html should include ("web.request")
      html should include ("action.execution")
      html should include ("blob.operation")
      html should include ("diagnostic-payload.externalization")
      html should include ("entity-access")
      html should include ("payload_kind=result")
      html should include ("destination=local-file")
      html should include ("/web/system/admin/observability/diagnostics/blob/ob05_blob")
      html should include ("Raw metrics snapshot")
      home should include ("/web/system/admin/observability/metrics")
      performance should include ("/web/system/admin/observability/metrics")
    }

    "render structured observability drill-down pages" in {
      Given("the prerequisites for render structured observability drill-down pages")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val previous = Record.dataAuto(
        "diagnosticKey" -> "storage_missing",
        "taxonomy" -> "resource.not-found",
        "taxonomyCategory" -> "resource",
        "taxonomySymptom" -> "not-found",
        "webStatus" -> 404,
        "statusText" -> "Not Found",
        "detailcode" -> 1060401L
      )
      val diagnostic = Record.dataAuto(
        "diagnosticKey" -> "ob04_payload_missing",
        "taxonomy" -> "state.invalid",
        "taxonomyCategory" -> "state",
        "taxonomySymptom" -> "invalid",
        "causeKind" -> "inconsistency",
        "interpretation" -> "system-failure",
        "userAction" -> "escalation",
        "responsibility" -> "system-admin",
        "webStatus" -> 500,
        "statusText" -> "Internal Server Error",
        "detailcode" -> 1080501L,
        "appCode" -> 9001L,
        "appStatus" -> "blob.payload.missing",
        "previous" -> Vector(previous),
        "result" -> Record.dataAuto(
          "kind" -> "record",
          "inline" -> false,
          "size_bytes" -> 4096,
          "payload_href" -> "/web/system/admin/observability/payloads/payload-1.json"
        )
      )
      RuntimeDashboardMetrics.recordBlobOperation(
        operation = "blob.content.get",
        error = true,
        diagnosticKey = Some("ob04_payload_missing"),
        diagnosticRecord = Some(diagnostic),
        kind = Some("content"),
        sourceMode = Some("managed"),
        backend = Some("local-file")
      )

      val performance = _renderer.renderSystemPerformance(subsystem).body
      val home = _renderer.renderSystemAdminObservability(subsystem).body
      val diagnostics = _renderer.renderSystemAdminObservabilityDiagnostics().body
      When("render structured observability drill-down pages is exercised")
      val detail = _renderer
        .renderSystemAdminObservabilityDiagnostic("blob", "ob04_payload_missing")
        .map(_.body)
        .getOrElse(fail("diagnostic detail is missing"))

      Then("the observable contract for render structured observability drill-down pages holds")
      performance should include ("/web/system/admin/observability/diagnostics/blob/ob04_payload_missing")
      home should include ("System Observability")
      home should include ("Diagnostic Payload Externalization")
      diagnostics should include ("Observability Diagnostics")
      diagnostics should include ("ob04_payload_missing")
      detail should include ("Structured fields")
      detail should include ("state.invalid")
      detail should include ("Internal Server Error")
      detail should include ("1080501")
      detail should include ("blob.payload.missing")
      detail should include ("Source-error trace")
      detail should include ("storage_missing")
      detail should include ("/web/system/admin/observability/payloads/payload-1.json")
      detail should include ("Raw diagnostic record")
      _renderer.renderSystemAdminObservabilityDiagnostic("missing", "ob04_payload_missing") shouldBe None
      _renderer.renderSystemAdminObservabilityDiagnostic("blob", "missing") shouldBe None
    }

    "render document and console entry pages without inline operation execution" in {
      Given("the prerequisites for render document and console entry pages without inline operation execution")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      val manual = _renderer.render(subsystem, "document").map(_.body).getOrElse(fail("documents page is missing"))
      When("render document and console entry pages without inline operation execution is exercised")
      val console = _renderer.render(subsystem, "console").map(_.body).getOrElse(fail("console is missing"))

      Then("the observable contract for render document and console entry pages without inline operation execution holds")
      manual should include ("System Documents")
      manual should include ("/web/system/dashboard")
      manual should include ("/web/console")
      manual should include ("Generated Help")
      manual should include ("Component documents")
      console should include ("System Console")
      console should include ("/web/system/dashboard")
      console should include ("/man/system")
      console should include ("/form/")
      console should include ("Console links to operation forms")
      console should include ("does not execute operations inline")
      console should not include ("<form method=\"post\"")
    }

    "keep system documents and console available while filtering component app entries by WebDescriptor apps" in {
      Given("the prerequisites for keep system documents and console available while filtering component app entries by WebDescriptor apps")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("document", "/web/document", "document"))
      )

      val manual = _renderer.render(subsystem, "document", webDescriptor = descriptor)
      val console = _renderer.render(subsystem, "console", webDescriptor = descriptor)
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      When("keep system documents and console available while filtering component app entries by WebDescriptor apps is exercised")
      val componentforms = _renderer.render(subsystem, componentpath, webDescriptor = descriptor)

      Then("the observable contract for keep system documents and console available while filtering component app entries by WebDescriptor apps holds")
      manual.map(_.body).getOrElse(fail("documents page is missing")) should include ("System Documents")
      console.map(_.body).getOrElse(fail("console is missing")) should include ("System Console")
      componentforms shouldBe None
    }

    "allow component dashboard app entries by descriptor path" in {
      Given("the prerequisites for allow component dashboard app entries by descriptor path")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))
      val component = subsystem.components.headOption.getOrElse(fail("component is missing"))
      val componentpath = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(component.name)
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("component-dashboard", s"/web/${componentpath}/dashboard", "dashboard"))
      )

      When("allow component dashboard app entries by descriptor path is exercised")
      val page = _renderer.render(subsystem, componentpath, Vector("dashboard"), descriptor)

      Then("the observable contract for allow component dashboard app entries by descriptor path holds")
      page.map(_.body).getOrElse(fail("dashboard is missing")) should include (s"${component.name} Dashboard")
    }

    }
  }
}
