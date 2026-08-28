package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.{CommandExecutionMode, CommandInterfaceMode}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{Capability, ExecutionContext, PrincipalId, RuntimeContext, SecurityLevel, SessionContext, SubjectKind}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.information.*
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.knowledge.{KnowledgeNode, KnowledgeNodeId, KnowledgeWorkingSetSnapshot}
import org.goldenport.cncf.mcp.McpProtocolRevision
import org.goldenport.cncf.protocol.HttpFailureTransportMetadata
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem, SubsystemUserMode}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.{Disposition, Interpretation}
import org.goldenport.datatype.{ContentType, MimeType}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.observation.{Cause, Descriptor, Observation, Phenomenon, Taxonomy}
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.http4s.{Header, MediaType, Method, Request as HRequest, Response as HResponse, Status as HStatus, Uri}
import org.http4s.headers.`Content-Type`
import io.circe.parser.parse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIStringSyntax

/*
 * @since   Apr. 24, 2026
 *  version Apr. 25, 2026
 *  version May. 25, 2026
 *  version Jun. 19, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class Http4sHttpServerDispatchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:subsystem-user-mode-http-dispatch, example:PM-53-01, rules:PM-53-01, phase:53")
  private val _e9 = afterWord(
    "in spec:action-execution-semantics, example:E9, rules:R5,R6,R13, phase:57.2, slice:AES-05A"
  )

  "Http4sHttpServer" must _in_phase53_spec {
    "HTTP operation, presentation, and protocol dispatch" which {
    "dispatch form-api submits through the runtime component name when the web selector uses artifact metadata" in {
      Given("the prerequisites for dispatch form-api submits through the runtime component name when the web selector uses artifact metadata")
      val root = Files.createTempDirectory("http4s-http-server-dispatch-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  textus-user-account.http.echo: public
          |form:
          |  textus-user-account.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val debug = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.DEBUG).getOrElse(fail("missing debug component"))
      debug.withArtifactMetadata(
        Component.ArtifactMetadata(
          sourceType = "spec",
          name = "debug-alias",
          version = "0.0.0",
          component = Some("textus-user-account")
        )
      )
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/textus-user-account/http/echo", "body=hello"),
          "textus-user-account",
          "http",
          "echo"
        )
        .unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("path: \"/org.goldenport.cncf.Debug/http/echo\"")
      body should include("method: \"POST\"")
    }

    "dispatch short, normalized-qualified, and exact canonical REST component paths through the same runtime route" in {
      Given("the default server subsystem")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the three admitted Admin WebPath selectors and one unsupported selector are requested")
      val short = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/rest/v1/admin/system/ping"))).unsafeRunSync()
      val normalized = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/rest/v1/org-goldenport-cncf-admin/system/ping"))).unsafeRunSync()
      val canonical = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/rest/v1/org.goldenport.cncf.Admin/system/ping"))).unsafeRunSync()
      val unsupported = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/rest/v1/org-goldenport-cncf-missing/system/ping"))).unsafeRunSync()

      Then("each admitted selector reaches the exact Admin route while the unsupported selector remains absent")
      short.status.code shouldBe 200
      normalized.status.code shouldBe 200
      canonical.status.code shouldBe 200
      unsupported.status.code shouldBe 404
    }

    "decode percent-encoded REST query parameters before operation dispatch" in {
      Given("the prerequisites for decode percent-encoded REST query parameters before operation dispatch")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/rest/v1/debug/http/echo?body=Knowledge%20Import%20Paper&url=https%3A%2F%2Fexample.test%2Fa%20b")
        ))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("body: \"Knowledge Import Paper\"")
      body should include ("url: \"https://example.test/a b\"")
      body should not include ("Knowledge%20Import%20Paper")
      body should not include ("https%3A%2F%2Fexample.test")
    }

    "decode a JSON object body into REST operation arguments" in {
      Given("a generated REST operation accepting the debug echo body field")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound
      val request = HRequest[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("/rest/v1/debug/http/echo")
      ).withEntity("{\"body\":\"JSON Review submission\"}")
        .withContentType(`Content-Type`.parse("application/json").toOption.get)

      When("the JSON client posts its generated operation envelope")
      val response = app.run(request).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the operation receives the named field without requiring form encoding")
      response.status.code shouldBe 200
      body should include ("name: \"body\"")
      body should include ("value: \"JSON Review submission\"")
    }

    "preserve authorization headers for empty REST GET operation requests" in {
      Given("the prerequisites for preserve authorization headers for empty REST GET operation requests")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/rest/v1/debug/http/echo?authorization=spoofed")
        ).putHeaders(org.http4s.Header.Raw(ci"Authorization", "Bearer authenticated-user")))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("value: \"Bearer authenticated-user\"")
      "value: \"Bearer authenticated-user\"".r.findAllIn(body).length shouldBe 1
    }

    "serve GET-backed HEAD responses without response bodies" in {
      Given("the prerequisites for serve GET-backed HEAD responses without response bodies")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("GET and HEAD requests are dispatched through the shared route surface")
      val getweb = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web"))).unsafeRunSync()
      val headweb = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web"))).unsafeRunSync()
      val headasset = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/assets/bootstrap.min.css"))).unsafeRunSync()
      val headmaterial = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/assets/textus-bootstrap-material.css"))).unsafeRunSync()
      val headicons = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/assets/textus-material-icons.svg"))).unsafeRunSync()
      val postonly = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/blob/admin/associations/attach"))).unsafeRunSync()
      val headmcp = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/mcp"))).unsafeRunSync()
      val headwebbody = headweb.body.compile.to(Array).unsafeRunSync().toVector
      val headassetbody = headasset.body.compile.to(Array).unsafeRunSync().toVector
      val headmaterialbody = headmaterial.body.compile.to(Array).unsafeRunSync().toVector
      val headiconsbody = headicons.body.compile.to(Array).unsafeRunSync().toVector
      val postonlybody = postonly.body.compile.to(Array).unsafeRunSync().toVector
      val headmcpbody = headmcp.body.compile.to(Array).unsafeRunSync().toVector

      Then("GET-backed HEAD responses retain metadata and omit response bodies")
      headweb.status shouldBe getweb.status
      headweb.contentType.map(_.mediaType) shouldBe getweb.contentType.map(_.mediaType)
      headwebbody shouldBe Vector.empty
      headasset.status.code shouldBe 200
      headasset.contentType.map(_.mediaType) shouldBe Some(MediaType.text.css)
      headassetbody shouldBe Vector.empty
      headmaterial.status.code shouldBe 200
      headmaterial.contentType.map(_.mediaType) shouldBe Some(MediaType.text.css)
      headmaterialbody shouldBe Vector.empty
      headicons.status.code shouldBe 200
      headicons.contentType.map(ct => s"${ct.mediaType.mainType}/${ct.mediaType.subType}") shouldBe Some("image/svg+xml")
      headiconsbody shouldBe Vector.empty
      postonly.status.code shouldBe 404
      postonlybody shouldBe Vector.empty
      headmcp.status.code shouldBe 404
      headmcpbody shouldBe Vector.empty
    }

    "keep web demo assist manifest disabled by default" in {
      Given("the prerequisites for keep web demo assist manifest disabled by default")
      val root = Files.createTempDirectory("http4s-http-server-demo-assist-disabled-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: protected
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?textus.demo.manifest=json&body=secret-input")
        ))
        .unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 404
      response.as[String].unsafeRunSync() should not include ("secret-input")
    }

    "serve safe web demo assist manifest when explicitly enabled" in {
      Given("the prerequisites for serve safe web demo assist manifest when explicitly enabled")
      val root = Files.createTempDirectory("http4s-http-server-demo-assist-enabled-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString),
            "cncf.web.demo-assist.enabled" ->
              ConfigurationValue.StringValue("true")
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo?textus.demo.manifest=json&body=secret-input")
        ))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val json = parse(body).getOrElse(fail(s"invalid manifest JSON: $body"))
      val entries = json.hcursor.downField("entries").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val entrykinds = entries.flatMap(_.hcursor.get[String]("kind").toOption).toSet
      val selectors = entries.flatMap(_.hcursor.get[String]("selector").toOption).toSet

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      response.contentType.map(_.mediaType) shouldBe Some(MediaType.application.json)
      json.hcursor.get[Int]("version") shouldBe Right(1)
      entrykinds should contain allOf ("page", "section", "form", "field", "action", "ux-profile")
      selectors should contain ("""[data-textus-page="static-form-operation"]""")
      selectors should contain ("""[data-textus-action="submit"]""")
      selectors should contain ("""[data-textus-action="operations"]""")
      body should not include ("secret-input")
      body should not include ("<html")
      body should not include ("type=\"hidden\"")
    }

    "download form result source as CSV attachment" in {
      Given("the prerequisites for download form result source as CSV attachment")
      val root = Files.createTempDirectory("http4s-http-server-download-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?body=hello&textus.download=true&textus.download.source=result.body&textus.download.format=csv&textus.download.filename=echo.csv")
        ))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      response.headers.get(ci"Content-Disposition").map(_.head.value) shouldBe Some("""attachment; filename="echo.csv"""")
      response.contentType.map(_.mediaType) shouldBe MediaType.parse("text/csv").toOption
      body should include ("body")
      body should include ("hello")
      body should include ("path")
      body should not include ("<html")
    }

    "download form result source as Excel attachment" in {
      Given("the prerequisites for download form result source as Excel attachment")
      val root = Files.createTempDirectory("http4s-http-server-download-xlsx-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?body=hello&textus.download=true&textus.download.source=result.body&textus.download.format=xlsx&textus.download.filename=echo.xlsx")
        ))
        .unsafeRunSync()
      val bytes = response.body.compile.to(Array).unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      response.headers.get(ci"Content-Disposition").map(_.head.value) shouldBe Some("""attachment; filename="echo.xlsx"""")
      response.contentType.map(_.mediaType) shouldBe MediaType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toOption
      bytes.length should be > 0
    }

    "not inject current-session principal attributes into form-api submits" in {
      Given("the prerequisites for not inject current-session principal attributes into form-api submits")
      val root = Files.createTempDirectory("http4s-http-server-dispatch-auth-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  textus-user-account.http.echo: public
          |form:
          |  textus-user-account.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString),
            SubsystemUserMode.CONFIGURATION_KEY ->
              ConfigurationValue.StringValue("multi-user")
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val debug = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.DEBUG).getOrElse(fail("missing debug component"))
      debug.withArtifactMetadata(
        Component.ArtifactMetadata(
          sourceType = "spec",
          name = "debug-alias",
          version = "0.0.0",
          component = Some("textus-user-account")
        )
      )
      subsystem.add(Vector(_auth_component))
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/textus-user-account/http/echo",
            "body=hello"
          ).putHeaders(
            org.http4s.Header.Raw(ci"x-textus-session", "session-1"),
            org.http4s.Header.Raw(ci"x-textus-debug-display", "always")
          ),
          "textus-user-account",
          "http",
          "echo"
        )
        .unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("x-textus-session")
      body should not include ("name: \"x-textus-session\"")
      body should not include ("name: \"x-textus-debug-display\"")
      body should not include ("x_textus_debug_display")
      body should not include ("principalId")
      body should not include ("principal_id")
      body should not include ("authenticated")
    }

    "treat empty multipart form-api submits as an empty form record" in {
      Given("the prerequisites for treat empty multipart form-api submits as an empty form record")
      val root = Files.createTempDirectory("http4s-http-server-empty-multipart-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val boundary = "----WebKitFormBoundaryEmptySpec"
      val request = HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/form-api/debug/http/echo"))
        .withEntity(s"--${boundary}--\r\n")
        .withContentType(`Content-Type`.parse(s"multipart/form-data; boundary=${boundary}").toOption.get)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._submit_operation_form_api(request, "debug", "http", "echo")
        .unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("path: \"/org.goldenport.cncf.Debug/http/echo\"")
      body should include("method: \"POST\"")
    }

    "enforce protected exposure before dispatching form submits" in {
      Given("the prerequisites for enforce protected exposure before dispatching form submits")
      val root = Files.createTempDirectory("http4s-http-server-protected-exposure-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: protected
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)

      When("protected HTML and API form submissions are dispatched")
      val htmlresponse = server
        ._submit_operation_form(
          _post_form_request("/form/debug/http/echo", "body=hello"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()
      val apiresponse = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/debug/http/echo", "body=hello"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()
      val htmlbody = htmlresponse.as[String].unsafeRunSync()
      val apibody = apiresponse.as[String].unsafeRunSync()

      Then("both protected submissions are rejected before form execution")
      htmlresponse.status.code shouldBe 403
      apiresponse.status.code shouldBe 403
      htmlbody should include ("Forbidden")
      apibody should include ("Forbidden")
    }

    "E9 execution response transport metadata" must _e9 {
    "return debug job id header for debug trace-job form-api requests" in {
      Given("the prerequisites for return debug job id header for debug trace-job form-api requests")
      val root = Files.createTempDirectory("http4s-http-server-debug-trace-job-spec")
      val web = root.resolve("web.yaml")
      Files.writeString(
        web,
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._submit_operation_form_api(
          _post_form_request(
            "/form-api/debug/http/echo?textus.debug.trace-job=true&textus.debug.calltree=true",
            "body=hello"
          ),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      response.headers.get(ci"X-Textus-Job-Id").map(_.head.value).flatMap(JobId.parse(_).toOption) should not be empty
      response.headers.get(ci"X-Textus-Execution-Mode").map(_.head.value) shouldBe Some("JobSync")
      response.headers.get(ci"X-Textus-Execution-Result").map(_.head.value) shouldBe Some("job-result")
    }

    "return explicit Direct headers for form-api requests without trace-job admission" in {
      Given("a public debug form-api operation without trace-job admission")
      val root = Files.createTempDirectory(
        Paths.get("target"),
        "http4s-http-server-direct-metadata-spec"
      )
      val web = root.resolve("web.yaml")
      try {
        Files.writeString(
          web,
          """expose:
            |  debug.http.echo: public
            |form:
            |  debug.http.echo:
            |    enabled: true
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(web.toString))),
          ConfigurationTrace.empty
        )
        val server = _server(DefaultSubsystemFactory.default(None, configuration))

        When("the form-api request is submitted without debug trace-job")
        val response = server
          ._submit_operation_form_api(_post_form_request("/form-api/debug/http/echo", "body=hello"), "debug", "http", "echo")
          .unsafeRunSync()

        Then("the transport declares Direct mode/result and does not emit a Job identifier")
        response.status.code shouldBe 200
        response.headers.get(ci"X-Textus-Execution-Mode").map(_.head.value) shouldBe Some("Sync")
        response.headers.get(ci"X-Textus-Execution-Result").map(_.head.value) shouldBe Some("direct")
        response.headers.get(ci"X-Textus-Job-Id") shouldBe None
      } finally {
        Files.deleteIfExists(web)
        Files.deleteIfExists(root)
      }
    }

    "replace stale case-insensitive headers with authoritative accepted Job metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; accepted Job metadata and a response carrying duplicate stale execution headers")
      val response = _with_stale_execution_headers(HResponse[IO](HStatus.Accepted))

      When("the shared execution-metadata projector decorates the accepted response")
      val projected = Http4sHttpServer._with_execution_metadata_headers(response, _accepted_execution_metadata, _accepted_execution_response)

      Then("exactly one authoritative Job, execution-mode, and execution-result header remains")
      projected.status shouldBe HStatus.Accepted
      projected.headers.get(ci"X-Textus-Job-Id").map(_.head.value) shouldBe Some("accepted-job-42")
      projected.headers.get(ci"X-Textus-Execution-Mode").map(_.head.value) shouldBe Some("JobAsync")
      projected.headers.get(ci"X-Textus-Execution-Result").map(_.head.value) shouldBe Some("accepted-job")
      projected.headers.headers.count(_.name == ci"X-Textus-Job-Id") shouldBe 1
      projected.headers.headers.count(_.name == ci"X-Textus-Execution-Mode") shouldBe 1
      projected.headers.headers.count(_.name == ci"X-Textus-Execution-Result") shouldBe 1
    }

    "preserve binary bytes while projecting accepted execution metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a binary response and accepted Job metadata")
      val response = _with_stale_execution_headers(
        HResponse[IO](HStatus.Ok)
          .withBodyStream(fs2.Stream.emits(Array[Byte](0x01, 0x02, 0x03)).covary[IO])
      )

      When("the shared execution-metadata projector decorates the binary response")
      val projected = Http4sHttpServer._with_execution_metadata_headers(response, _accepted_execution_metadata, _accepted_execution_response)

      Then("the status and binary body remain unchanged")
      projected.status shouldBe HStatus.Ok
      projected.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector(1.toByte, 2.toByte, 3.toByte)
    }

    "preserve BadRequest status while projecting accepted execution metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a BadRequest response and accepted Job metadata")
      val response = _with_stale_execution_headers(HResponse[IO](HStatus.BadRequest))

      When("the shared execution-metadata projector decorates the BadRequest response")
      val projected = Http4sHttpServer._with_execution_metadata_headers(response, _accepted_execution_metadata, _accepted_execution_response)

      Then("the status remains BadRequest and the Job header is authoritative")
      projected.status shouldBe HStatus.BadRequest
      projected.headers.get(ci"X-Textus-Job-Id").map(_.head.value) shouldBe Some("accepted-job-42")
    }

    "remove stale Job headers when explicit Direct metadata has no identifier" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a response with stale Job headers and explicit Direct metadata")
      val response = _with_stale_execution_headers(HResponse[IO](HStatus.Ok))
      val metadata = RuntimeContext.ExecutionMetadata.empty

      When("the shared execution-metadata projector decorates the direct response")
      val projected = Http4sHttpServer._with_execution_metadata_headers(
        response,
        metadata,
        Some(RuntimeContext.ExecutionResponseMetadata.direct)
      )

      Then("no Job header remains while direct execution headers are singular")
      projected.status shouldBe HStatus.Ok
      projected.headers.get(ci"X-Textus-Job-Id") shouldBe None
      projected.headers.headers.count(_.name == ci"X-Textus-Execution-Mode") shouldBe 1
      projected.headers.headers.count(_.name == ci"X-Textus-Execution-Result") shouldBe 1
    }

    "apply the kind-first Job header matrix for every explicit state" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R13; Example: E9; every response kind, identifier presence, and duplicate stale Job headers")
      val cases = Vector(
        ("direct", RuntimeContext.ExecutionResponseMetadata.direct, None, None),
        ("accepted-with-id", _accepted_execution_response.get, Some("accepted-job-42"), Some("accepted-job-42")),
        ("accepted-without-id", _accepted_execution_response.get, None, None),
        ("result-with-id", _job_result_execution_response, Some("result-job-42"), Some("result-job-42")),
        ("result-without-id", _job_result_execution_response, None, None)
      )

      When("each case is projected over a response carrying duplicate stale Job headers")
      cases.foreach { case (label, executionresponse, jobid, expectedjobid) =>
        val projected = Http4sHttpServer._with_execution_metadata_headers(
          _with_stale_execution_headers(HResponse[IO](HStatus.Ok)),
          RuntimeContext.ExecutionMetadata(responseJobId = jobid),
          Some(executionresponse)
        )

        Then(s"$label retains only its kind-authorized Job header outcome")
        projected.headers.get(ci"X-Textus-Job-Id").map(_.head.value) shouldBe expectedjobid
        projected.headers.headers.count(_.name == ci"X-Textus-Job-Id") shouldBe expectedjobid.fold(0)(_ => 1)
      }
    }

    "preserve a legacy Job header through the structured error rendering branch without metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a structured BadRequest response with a legacy Job header and no execution metadata")
      val server = _server(DefaultSubsystemFactory.default(None))
      val source = HttpResponse.Text(
        HttpStatus.BadRequest,
        ContentType(MimeType("application/json"), Some(StandardCharsets.UTF_8)),
        Bag.text("""{"error":"structured failure"}""", StandardCharsets.UTF_8)
      ).withHeader(Record.data("X-Textus-Job-Id" -> "legacy-structured-job"))

      When("the structured error rendering branch projects an absent execution response")
      val response = server._to_http_response_with_metadata(
        source,
        None,
        None,
        None,
        None,
        RuntimeContext.ExecutionMetadata.empty
      ).unsafeRunSync()

      Then("the source status and legacy Job header remain observable")
      response.status shouldBe HStatus.BadRequest
      response.headers.get(ci"X-Textus-Job-Id").map(_.head.value) shouldBe Some("legacy-structured-job")
    }

    "replace a legacy Job header through the synthesized error rendering branch with accepted metadata" in {
      Given("Spec: docs/spec/action-execution-semantics.md; Rules: R5,R6,R13; Example: E9; a synthesized BadRequest response with legacy headers and accepted Job metadata")
      val server = _server(DefaultSubsystemFactory.default(None))
      val source = HttpResponse.Text(
        HttpStatus.BadRequest,
        ContentType(MimeType("text/plain"), Some(StandardCharsets.UTF_8)),
        Bag.text("synthesized failure", StandardCharsets.UTF_8)
      ).withHeader(Record.data("X-Textus-Job-Id" -> "legacy-synthesized-job"))
      val metadata = RuntimeContext.ExecutionMetadata(
        responseJobId = Some("accepted-synthesized-job")
      )
      val executionresponse = Some(RuntimeContext.ExecutionResponseMetadata(
        admittedMode = CommandExecutionMode.JobAsync.toString,
        effectiveMode = CommandExecutionMode.JobAsync,
        interfaceMode = CommandInterfaceMode.Async,
        managedByJob = true,
        responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob
      ))

      When("the synthesized error rendering branch projects accepted Job metadata")
      val response = server._to_http_response_with_metadata(
        source,
        None,
        None,
        None,
        None,
        metadata,
        executionresponse
      ).unsafeRunSync()

      Then("the source status remains while authoritative metadata replaces the legacy Job header")
      response.status shouldBe HStatus.BadRequest
      response.headers.get(ci"X-Textus-Job-Id").map(_.head.value) shouldBe Some("accepted-synthesized-job")
      response.headers.get(ci"X-Textus-Execution-Mode").map(_.head.value) shouldBe Some("JobAsync")
      response.headers.get(ci"X-Textus-Execution-Result").map(_.head.value) shouldBe Some("accepted-job")
    }
    }

    "render unauthorized operation-result widgets as inline page errors" in {
      Given("the prerequisites for render unauthorized operation-result widgets as inline page errors")
      val root = Files.createTempDirectory("http4s-http-server-operation-result-forbidden-spec")
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """expose:
          |  debug.http.echo: protected
          |authorization:
          |  debug.http.echo:
          |    requireAuthenticated: true
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<textus:operation-result component="debug" service="http" operation="echo" body="hello"></textus:operation-result>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("__403.html"),
        """<section><h1>Debug app sign in required</h1></section>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._component_web_app(
          "debug",
          "debug-app",
          Vector.empty,
          Some(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app")))
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("Static Form operation-result operation is not authorized")
      body should not include "Debug app sign in required"
    }

    "render operation-result widgets without knowledge summary by default" in {
      Given("the prerequisites for render operation-result widgets without knowledge summary by default")
      val root = Files.createTempDirectory("http4s-http-server-operation-result-default-spec")
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """expose:
          |  debug.http.echo: enabled
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<textus:operation-result component="debug" service="http" operation="echo" body="hello"></textus:operation-result>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server
        ._component_web_app(
          "debug",
          "debug-app",
          Vector.empty,
          Some(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app")))
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should not include "KnowledgeSpace"
    }

    "render only trusted application status on a failing Static Form operation-result page" in {
      Given("a Static Form operation-result path whose dispatcher returns an admitted failure application status")
      val root = Files.createTempDirectory("http4s-http-server-operation-result-app-status-spec")
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """expose:
          |  debug.http.echo: public
          |form:
          |  debug.http.echo:
          |    enabled: true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<textus:operation-result component="debug" service="http" operation="echo">
          |  <textus-error-panel source="error"></textus-error-panel>
          |  <textus-result-view source="result.body"></textus-result-view>
          |</textus:operation-result>""".stripMargin,
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      var appstatus: Option[String] = Some(_web_app_status)
      val dispatcher = new WebOperationDispatcher {
        val targetName: String = "trusted-app-status-spec"

        def dispatch(request: org.goldenport.http.HttpRequest): HttpResponse = {
          val _ = request
          HttpFailureTransportMetadata.attach(
            HttpResponse.text(HttpStatus.BadRequest, _web_failure_message),
            _web_failure_conclusion(appstatus)
          )
        }
      }
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem, Some(dispatcher))
      val request = Some(HRequest[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/debug-app?error.appStatus=forged-status")
      ))

      When("the actual Static Form Web operation-result dispatch renders failures with and without application status")
      val withstatus = server._component_web_app("debug", "debug-app", Vector.empty, request).unsafeRunSync()
      val withstatusbody = withstatus.as[String].unsafeRunSync()
      appstatus = None
      val withoutstatus = server._component_web_app("debug", "debug-app", Vector.empty, request).unsafeRunSync()
      val withoutstatusbody = withoutstatus.as[String].unsafeRunSync()

      Then("the operation-result pages retain HTTP 200 and legacy failure text while exposing only the trusted status property")
      withstatus.status.code shouldBe 200
      withstatusbody should include ("error.appStatus")
      withstatusbody should include (_web_app_status_escaped)
      withstatusbody should not include _web_app_status
      withstatusbody should not include "forged-status"
      withstatusbody should include (_web_failure_message)
      withstatusbody should not include HttpFailureTransportMetadata.DETAIL_CODE_HEADER
      withstatusbody should not include HttpFailureTransportMetadata.APP_CODE_HEADER
      withstatusbody should not include HttpFailureTransportMetadata.APP_STATUS_HEADER
      withoutstatus.status.code shouldBe 200
      withoutstatusbody should include (_web_failure_message)
      withoutstatusbody should not include "error.appStatus"
      withoutstatusbody should not include _web_app_status
      withoutstatusbody should not include "forged-status"
    }

    "dispatch static Web app page aliases below the app root" in {
      Given("the prerequisites for dispatch static Web app page aliases below the app root")
      val root = Files.createTempDirectory("http4s-http-server-web-page-alias-spec")
      Files.createDirectories(root.resolve("debug-app"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  apps:
          |    - name: debug-app
          |      kind: static-form
          |  routes:
          |    - path: /web/debug-app
          |      kind: alias
          |      target:
          |        component: debug
          |        app: debug-app
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<section><h1>Debug Home</h1></section>""",
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("seed.html"),
        """<section><h1>Seed Page</h1></section>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app
        .run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app/seed")))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("Seed Page")
      body should not include ("Debug Home")
    }

    "inject subsystem theme into component static Web pages" in {
      Given("the prerequisites for inject subsystem theme into component static Web pages")
      val root = Files.createTempDirectory("http4s-http-server-theme-spec")
      Files.createDirectories(root.resolve("debug-app"))
      Files.createDirectories(root.resolve("debug-app").resolve("assets").resolve("fonts"))
      Files.createDirectories(root.resolve("assets"))
      Files.createDirectories(root.resolve("assets").resolve("fonts"))
      Files.writeString(
        root.resolve("web.yaml"),
        """web:
          |  assets:
          |    favicon: /web/assets/favicon.svg
          |  theme:
          |    name: brand
          |    css:
          |      - /web/assets/theme.css
          |    variables:
          |      primary: "#14532d"
          |  apps:
          |    - name: debug-app
          |      assets:
          |        favicon: /web/debug/debug-app/assets/favicon.ico
          |      theme:
          |        css:
          |          - /web/debug/debug-app/assets/app-theme.css
          |    - name: console
          |      kind: console
          |      theme:
          |        css:
          |          - /web/assets/console-theme.css
          |  pages:
          |    debug.debug-app:
          |      title: Branded Debug
          |      heading: Branded debug page
          |      subtitle: Customized by subsystem WebDescriptor.
          |      submitLabel: Continue
          |      fields:
          |        - email
          |      controls:
          |        email:
          |          label: Email address
          |          help: Shared account email.
          |          placeholder: user@example.test
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("debug-app").resolve("index.html"),
        """<!doctype html>
          |<html><head><title>Debug App</title></head><body data-textus-page="debug-app"><main>
          |  <h1 data-textus-role="heading">Debug</h1>
          |  <p data-textus-role="subtitle">Original.</p>
          |  <div data-textus-field="email"><label for="email">Email</label><input id="email" name="email" required><div class="form-text">Email help.</div></div>
          |  <div data-textus-field="phoneNumber"><label for="phoneNumber">Phone</label><input id="phoneNumber" name="phoneNumber"><div class="form-text">Phone help.</div></div>
          |  <button data-textus-role="submit">Submit</button>
          |</main></body></html>
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(root.resolve("assets").resolve("theme.css"), "body { color: var(--bs-primary); }", StandardCharsets.UTF_8)
      Files.writeString(root.resolve("assets").resolve("favicon.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>", StandardCharsets.UTF_8)
      Files.write(root.resolve("assets").resolve("fonts").resolve("brand.woff2"), Array[Byte](1, 2, 3))
      Files.write(root.resolve("debug-app").resolve("assets").resolve("favicon.ico"), Array[Byte](0, 0, 1, 0))
      Files.write(root.resolve("debug-app").resolve("assets").resolve("fonts").resolve("app.woff2"), Array[Byte](4, 5, 6))
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)

      When("the documented HTTP dispatch is exercised")
      val response = server._component_web_app("debug", "debug-app", Vector.empty).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val asset = server._web_global_asset("theme.css").unsafeRunSync()
      val nestedglobalasset = server._web_global_asset(Vector("fonts", "brand.woff2")).unsafeRunSync()
      val nestedappasset = server._web_app_asset("debug", "debug-app", Vector("fonts", "app.woff2")).unsafeRunSync()
      val favicon = server._favicon().unsafeRunSync()
      val generatedpage = server._static_form_app("console", Vector.empty).unsafeRunSync()
      val generatedbody = generatedpage.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("/web/assets/theme.css")
      body should include ("""rel="icon" href="/web/debug/debug-app/assets/favicon.ico"""")
      body should include ("/web/debug/debug-app/assets/app-theme.css")
      body should include ("data-textus-theme-vars=\"brand\"")
      body should include ("--bs-primary: #14532d")
      body should include ("textus-page-customization")
      body should include ("Branded debug page")
      body should include ("user@example.test")
      asset.status.code shouldBe 200
      asset.as[String].unsafeRunSync() should include ("var(--bs-primary)")
      nestedglobalasset.status.code shouldBe 200
      nestedappasset.status.code shouldBe 200
      favicon.status.code shouldBe 200
      favicon.as[String].unsafeRunSync() should include ("<svg")
      generatedpage.status.code shouldBe 200
      generatedbody should include ("""rel="icon" href="/web/assets/favicon.svg"""")
      generatedbody should include ("/web/assets/console-theme.css")
      body should include ("/web/assets/textus-form-debug.js")
      body should include ("/web/assets/textus-calltree.js")
      StaticFormAppAssets.textusFormDebugJs should include ("x-textus-debug-request-kind")
      StaticFormAppAssets.textusFormDebugJs should include ("x-textus-debug-display")
      StaticFormAppAssets.textusFormDebugJs should include ("function extractCallTree")
      StaticFormAppAssets.textusFormDebugJs should include ("data-textus-calltree")
      StaticFormAppAssets.textusFormDebugJs should include ("function shouldShowSuccess")
      StaticFormAppAssets.textusFormDebugJs should include ("function shouldInspect")
      StaticFormAppAssets.textusFormDebugJs should include ("function shouldRender")
      StaticFormAppAssets.textusFormDebugJs should include ("function isSensitiveKey")
      StaticFormAppAssets.textusFormDebugJs should include ("function redactText")
      StaticFormAppAssets.textusFormDebugJs should include ("url.searchParams.set")
      StaticFormAppAssets.textusFormDebugJs should include ("[redacted]")
      StaticFormAppAssets.textusFormDebugJs should include ("data-debug-events")
      StaticFormAppAssets.textusFormDebugJs should include ("Operation origin slot")
      StaticFormAppAssets.textusFormDebugJs should include ("data-debug-slot")
      StaticFormAppAssets.textusFormDebugJs should include ("Request label")
      StaticFormAppAssets.textusFormDebugJs should include ("Optional")
      StaticFormAppAssets.textusFormDebugJs should include ("Timestamp")
      StaticFormAppAssets.textusFormDebugJs should include ("sessionStorage")
      StaticFormAppAssets.textusFormDebugJs should include ("textus.form.debug.carryover.v1")
      StaticFormAppAssets.textusFormDebugJs should include ("function takeCarryover")
      StaticFormAppAssets.textusFormDebugJs should include ("function hasServerExecutionPanel")
      StaticFormAppAssets.textusFormDebugJs should include ("textus-execution-debug-panel")
      StaticFormAppAssets.textusFormDebugJs should include ("<details class=\"card border-secondary-subtle bg-body-tertiary\">")
      StaticFormAppAssets.textusFormDebugJs should not include ("<details class=\"card border-secondary-subtle bg-body-tertiary\" open>")
    }

    "serve only descriptor-declared component admin pages" in {
      Given("the prerequisites for serve only descriptor-declared component admin pages")
      val root = Files.createTempDirectory("http4s-http-server-admin-page-spec")
      Files.createDirectories(root.resolve("admin"))
      Files.writeString(
        root.resolve("web.yaml"),
        """admin:
          |  pages:
          |    - name: notifications
          |      label: Notification Admin
          |      href: /web/debug/admin/notifications
          |      permission: admin.entity.read
          |      component: debug
          |    - name: missing-template
          |      label: Missing Template
          |      href: /web/debug/admin/missing-template
          |      permission: admin.entity.read
          |      component: debug
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        root.resolve("admin").resolve("notifications.html"),
        """<section><h1>Notification Admin</h1></section>""",
        StandardCharsets.UTF_8
      )
      val configuration = ResolvedConfiguration(
        Configuration(
          Map(
            RuntimeConfig.webDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("declared, undeclared, and declared-but-templateless component admin routes are dispatched through the existing authorization checkpoint")
      val applicationadmin = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/admin"))).unsafeRunSync()
      val declared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/notifications"))).unsafeRunSync()
      val casealias = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/Notifications"))).unsafeRunSync()
      val underscorealias = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/notice_board"))).unsafeRunSync()
      val undeclared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/unknown"))).unsafeRunSync()
      val missingtemplate = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/missing-template"))).unsafeRunSync()
      val applicationadminbody = applicationadmin.as[String].unsafeRunSync()
      val declaredbody = declared.as[String].unsafeRunSync()

      Then("the admitted declaration succeeds while undeclared and missing-template names fail deterministically without another-page fallback")
      applicationadmin.status.code shouldBe 200
      applicationadminbody should include ("Application Admin")
      applicationadminbody should include ("Notification Admin")
      declared.status.code shouldBe 200
      declaredbody should include ("Notification Admin")
      casealias.status.code shouldBe 404
      underscorealias.status.code shouldBe 404
      undeclared.status.code shouldBe 404
      missingtemplate.status.code shouldBe 404
    }

    "dispatch system observability drill-down routes" in {
      Given("the prerequisites for dispatch system observability drill-down routes")
      RuntimeDashboardMetrics.recordValidation(
        "spec.operation",
        Some("ob04_format"),
        Some(Record.dataAuto(
          "diagnosticKey" -> "ob04_format",
          "taxonomy" -> "argument.invalid",
          "taxonomyCategory" -> "argument",
          "taxonomySymptom" -> "invalid",
          "causeKind" -> "format",
          "webStatus" -> 400,
          "statusText" -> "Bad Request",
          "detailCode" -> 1010401L
        ))
      )
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("observability summary, detail, and unknown routes are dispatched")
      val home = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability"))).unsafeRunSync()
      val metrics = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/metrics"))).unsafeRunSync()
      val diagnostics = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics"))).unsafeRunSync()
      val detail = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics/validation/ob04_format"))).unsafeRunSync()
      val unknown = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics/validation/missing"))).unsafeRunSync()
      val homebody = home.as[String].unsafeRunSync()
      val metricsbody = metrics.as[String].unsafeRunSync()
      val diagnosticsbody = diagnostics.as[String].unsafeRunSync()
      val detailbody = detail.as[String].unsafeRunSync()

      Then("the observability drill-down surface returns each documented response")
      home.status.code shouldBe 200
      homebody should include ("System Observability")
      metrics.status.code shouldBe 200
      metricsbody should include ("Observability Metrics")
      diagnostics.status.code shouldBe 200
      diagnosticsbody should include ("ob04_format")
      detail.status.code shouldBe 200
      detailbody should include ("argument.invalid")
      detailbody should include ("1010401")
      unknown.status.code shouldBe 404
    }

    "dispatch system knowledge admin routes" in {
      Given("the prerequisites for dispatch system knowledge admin routes")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.add(TestComponentFactory.create("knowledge_component", Protocol.empty))
      val component = subsystem.findComponent(org.goldenport.cncf.component.ComponentId("org.goldenport.cncf.test.KnowledgeComponent")).getOrElse(fail("knowledge component missing"))
      component.knowledgeSpace.replace(KnowledgeWorkingSetSnapshot(
        nodes = Vector(KnowledgeNode(KnowledgeNodeId("node-1"), "concept", Some("Node One")))
      ))(using ExecutionContext.test()) match {
        case Consequence.Success(_) => ()
        case Consequence.Failure(conclusion) => fail(conclusion.toString)
      }
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("knowledge index, component, node, and unknown routes are dispatched")
      val index = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge"))).unsafeRunSync()
      val componentpage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge-component"))).unsafeRunSync()
      val nodepage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge_component/nodes/node-1"))).unsafeRunSync()
      val unknowncomponent = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/missing"))).unsafeRunSync()
      val unknownnode = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge_component/nodes/missing"))).unsafeRunSync()
      val indexbody = index.as[String].unsafeRunSync()
      val componentbody = componentpage.as[String].unsafeRunSync()
      val nodebody = nodepage.as[String].unsafeRunSync()

      Then("the knowledge administration routes retain their declared status and content")
      index.status.code shouldBe 200
      indexbody should include ("System Knowledge")
      componentpage.status.code shouldBe 200
      componentbody should include ("node-1")
      nodepage.status.code shouldBe 200
      nodebody should include ("Node One")
      unknowncomponent.status.code shouldBe 404
      unknownnode.status.code shouldBe 404
    }

    "dispatch system information admin routes" in {
      Given("the prerequisites for dispatch system information admin routes")
      Given("a subsystem containing confirmed Information")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.add(TestComponentFactory.create("information_component", Protocol.empty))
      val component = subsystem.findComponent(org.goldenport.cncf.component.ComponentId("org.goldenport.cncf.test.InformationComponent")).getOrElse(fail("information component missing"))
      given ExecutionContext = component.logic.executionContext()
      val batch = component.informationSpace.registerInformation(
        "paper",
        Vector(Record.data("title" -> "Information Import", "authors" -> "Alice Example"))
      ) match {
        case Consequence.Success(batch) => batch
        case Consequence.Failure(conclusion) => fail(conclusion.toString)
      }
      val record = batch.headOption.getOrElse(fail("information record missing"))
      component.informationSpace.validateInformation(record.id)
      component.informationSpace.confirmInformation(record.id)
      When("the system Information index, component detail, and missing component routes are requested")
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val index = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information"))).unsafeRunSync()
      val componentpage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information/information-component"))).unsafeRunSync()
      val unknowncomponent = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information/missing"))).unsafeRunSync()

      Then("existing Information is rendered and an unknown component remains not found")
      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("System Information")
      componentpage.status.code shouldBe 200
      componentpage.as[String].unsafeRunSync() should include ("Information Import")
      unknowncomponent.status.code shouldBe 404
    }

    "dispatch app-facing TagSpace routes" in {
      Given("the prerequisites for dispatch app-facing TagSpace routes")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      When("the documented HTTP dispatch is exercised")
      val response = app.run(HRequest[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/tag/tags?tagSpace=information")
      ).putHeaders(org.http4s.Header.Raw(ci"x-textus-session", "forged-session"))).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      body should include ("Application TagSpace browser and editor")
      body should include ("TagSpace <span class=\"badge text-bg-secondary\">information</span>")
      body should include ("action=\"/web/tag/tags\"")
      body should include ("action=\"/web/tag/tags/create\"")
      body should include ("Log in to create, update, or move Tags")
      body should include ("disabled")
      body should not include ("action=\"/web/tag/tags/update\"")
      body should not include ("Raw tag tree")
    }

    "accept JSON-RPC MCP requests over POST" in {
      Given("the prerequisites for accept JSON-RPC MCP requests over POST")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound
      val request = HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/mcp"))
        .withEntity("""{"jsonrpc":"2.0","id":"tools","method":"tools/list","params":{}}""")
        .withContentType(`Content-Type`.parse("application/json").toOption.get)
        .putHeaders(org.http4s.Header.Raw(ci"MCP-Protocol-Version", McpProtocolRevision.PREFERRED.print))

      When("the documented HTTP dispatch is exercised")
      val response = app.run(request).unsafeRunSync()

      Then("the documented response contract holds")
      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include (""""id":"tools"""")
      body should include (""""tools"""")
    }

    "preserve MCP Streamable HTTP request and notification lifecycle outcomes" in {
      Given("an MCP HTTP route and the shared preferred protocol revision")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = _server(subsystem)
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound
      val protocolheader = org.http4s.Header.Raw(
        ci"MCP-Protocol-Version",
        McpProtocolRevision.PREFERRED.print
      )
      def _post_(body: String, headers: Vector[org.http4s.Header.Raw] = Vector.empty) = {
        val request = HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/mcp"))
          .withEntity(body)
          .withContentType(`Content-Type`.parse("application/json").toOption.get)
        app.run(request.withHeaders(org.http4s.Headers(request.headers.headers ++ headers))).unsafeRunSync()
      }

      When("initialize, initialized notification, and lifecycle failures are posted")
      val initialize = _post_(
        s"""{"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"${McpProtocolRevision.PREFERRED.print}"}}"""
      )
      val initialized = _post_(
        """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
        Vector(protocolheader)
      )
      val requestshapedinitialized = _post_(
        """{"jsonrpc":"2.0","id":"bad-init","method":"notifications/initialized"}""",
        Vector(protocolheader)
      )
      val missingheader = _post_(
        """{"jsonrpc":"2.0","id":"missing-header","method":"tools/list","params":{}}"""
      )
      val unsupportedheader = _post_(
        """{"jsonrpc":"2.0","id":"unsupported-header","method":"tools/list","params":{}}""",
        Vector(org.http4s.Header.Raw(ci"MCP-Protocol-Version", "2026-03-19"))
      )
      val duplicateheader = _post_(
        """{"jsonrpc":"2.0","id":"duplicate-header","method":"tools/list","params":{}}""",
        Vector(protocolheader, protocolheader)
      )
      val unknownnotification = _post_(
        """{"jsonrpc":"2.0","method":"notifications/unknown"}""",
        Vector(protocolheader)
      )

      Then("requests receive JSON while only the accepted notification receives an empty 202")
      initialize.status.code shouldBe 200
      initialize.contentType.map(_.mediaType) shouldBe Some(MediaType.application.json)
      parse(initialize.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("id").toOption) shouldBe Some("init")
      initialized.status.code shouldBe 202
      initialized.contentType shouldBe None
      initialized.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
      requestshapedinitialized.status.code shouldBe 200
      requestshapedinitialized.contentType.map(_.mediaType) shouldBe Some(MediaType.application.json)
      requestshapedinitialized.as[String].unsafeRunSync() should include ("bad-init")
      missingheader.status.code shouldBe 200
      missingheader.as[String].unsafeRunSync() should include ("MCP-Protocol-Version is required")
      unsupportedheader.status.code shouldBe 200
      unsupportedheader.as[String].unsafeRunSync() should not include "2026-03-19"
      duplicateheader.status.code shouldBe 200
      val duplicatebody = parse(duplicateheader.as[String].unsafeRunSync()).toOption
        .getOrElse(fail("duplicate-header response is not JSON"))
      duplicatebody.hcursor.get[String]("id") shouldBe Right("duplicate-header")
      duplicatebody.hcursor.downField("error").get[Int]("code") shouldBe Right(-32600)
      unknownnotification.status.code shouldBe 400
      unknownnotification.contentType shouldBe None
      unknownnotification.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
    }
    }
  }

  private val _accepted_execution_metadata = RuntimeContext.ExecutionMetadata(
    responseJobId = Some("accepted-job-42")
  )

  private val _accepted_execution_response = Some(RuntimeContext.ExecutionResponseMetadata(
    admittedMode = CommandExecutionMode.JobAsync.toString,
    effectiveMode = CommandExecutionMode.JobAsync,
    interfaceMode = CommandInterfaceMode.Async,
    managedByJob = true,
    responseKind = RuntimeContext.ExecutionResponseKind.AcceptedJob
  ))

  private val _job_result_execution_response = RuntimeContext.ExecutionResponseMetadata(
    admittedMode = CommandExecutionMode.JobSync.toString,
    effectiveMode = CommandExecutionMode.JobSync,
    interfaceMode = CommandInterfaceMode.Sync,
    managedByJob = true,
    responseKind = RuntimeContext.ExecutionResponseKind.JobResult
  )

  private val _stale_execution_headers = Vector(
    Header.Raw(ci"x-textus-job-id", "stale-job"),
    Header.Raw(ci"X-Textus-Job-Id", "stale-job-duplicate"),
    Header.Raw(ci"x-textus-execution-mode", "stale-mode"),
    Header.Raw(ci"X-Textus-Execution-Mode", "stale-mode-duplicate"),
    Header.Raw(ci"x-textus-execution-result", "stale-result"),
    Header.Raw(ci"X-Textus-Execution-Result", "stale-result-duplicate")
  )

  private def _with_stale_execution_headers(response: HResponse[IO]): HResponse[IO] =
    _stale_execution_headers.foldLeft(response)(_.putHeaders(_))

  private def _server(
    subsystem: Subsystem,
    operationdispatcheroption: Option[WebOperationDispatcher] = None
  ): Http4sHttpServer =
    HttpRuntimeBindingAdmissionFixture.server(
      new HttpExecutionEngine(subsystem),
      operationdispatcheroption
    )

  private def _web_failure_conclusion(appstatus: Option[String]): Conclusion =
    Conclusion(
      status = Conclusion.Status(appCode = Some(7404L), appStatus = appstatus),
      observation = Observation(
        phenomenon = Phenomenon.Failure,
        taxonomy = Taxonomy(Taxonomy.Category.Argument, Taxonomy.Symptom.Invalid),
        cause = Cause.create(Vector(
          Descriptor.Facet.Message(_web_failure_message),
          Descriptor.Facet.Reason("private-reason-must-not-be-projected")
        )),
        timestamp = Instant.EPOCH
      ),
      interpretation = Interpretation.domainFailure,
      disposition = Disposition.fix
    )

  private val _web_app_status = "project-identity-required<script>alert(1)</script>"
  private val _web_app_status_escaped = "project-identity-required&lt;script&gt;alert(1)&lt;/script&gt;"
  private val _web_failure_message = "Project identity is required."

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))

  private def _post_form_request(path: String, body: String): HRequest[IO] =
    HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString(path))
      .withEntity(body)
      .withContentType(`Content-Type`.parse("application/x-www-form-urlencoded").toOption.get)

  private def _auth_component = {
    given ExecutionContext = ExecutionContext.create()
    new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "org.goldenport.cncf.test.SessionProvider",
          ComponentId("org.goldenport.cncf.test.SessionProvider"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.SessionProvider")),
          Protocol.empty
        )
      override def authenticationProviders: Vector[AuthenticationProvider] =
        Vector(_session_provider)
    }.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = "session_provider",
        version = "0.0.0",
        component = Some("session_provider")
      )
    )
  }

  private def _session_provider = new AuthenticationProvider {
    def name: String = "session-provider"

    override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
      currentSession(request)

    override def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
      request.sessionId match {
        case Some("session-1") =>
          Consequence.success(Some(
            AuthenticationResult(
              principalId = PrincipalId("major-minor-entity-user_account-1776979781473-7iY24o3FsknA5ybooCutgj"),
              attributes = Map(
                "principal_id" -> "major-minor-entity-user_account-1776979781473-7iY24o3FsknA5ybooCutgj",
                "authenticated" -> "true",
                "role" -> "user"
              ),
              capabilities = Set(Capability("user")),
              level = SecurityLevel("user"),
              subjectKind = SubjectKind.User,
              session = Some(SessionContext(sessionId = Some("session-1")))
            )
          ))
        case _ =>
          Consequence.success(None)
      }
  }
}
