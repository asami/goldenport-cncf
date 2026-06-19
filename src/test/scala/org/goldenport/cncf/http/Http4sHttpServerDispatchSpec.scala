package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{Capability, ExecutionContext, PrincipalId, SecurityLevel, SessionContext, SubjectKind}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.information.*
import org.goldenport.cncf.knowledge.{KnowledgeNode, KnowledgeNodeId, KnowledgeWorkingSetSnapshot}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.http4s.{MediaType, Method, Request as HRequest, Uri}
import org.http4s.headers.`Content-Type`
import io.circe.parser.parse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIStringSyntax

/*
 * @since   Apr. 24, 2026
 *  version Apr. 25, 2026
 *  version May. 25, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class Http4sHttpServerDispatchSpec extends AnyWordSpec with Matchers {

  "Http4sHttpServer" should {
    "dispatch form-api submits through the runtime component name when the web selector uses artifact metadata" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val debug = subsystem.findComponent("debug").getOrElse(fail("missing debug component"))
      debug.withArtifactMetadata(
        Component.ArtifactMetadata(
          sourceType = "spec",
          name = "debug-alias",
          version = "0.0.0",
          component = Some("textus-user-account")
        )
      )
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/textus-user-account/http/echo", "body=hello"),
          "textus-user-account",
          "http",
          "echo"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("path: \"/debug/http/echo\"")
      body should include("method: \"POST\"")
    }

    "decode percent-encoded REST query parameters before operation dispatch" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/rest/v1/debug/http/echo?body=Knowledge%20Import%20Paper&url=https%3A%2F%2Fexample.test%2Fa%20b")
        ))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      body should include ("body: \"Knowledge Import Paper\"")
      body should include ("url: \"https://example.test/a b\"")
      body should not include ("Knowledge%20Import%20Paper")
      body should not include ("https%3A%2F%2Fexample.test")
    }

    "serve GET-backed HEAD responses without response bodies" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val getweb = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web"))).unsafeRunSync()
      val headweb = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web"))).unsafeRunSync()
      val headasset = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/assets/bootstrap.min.css"))).unsafeRunSync()
      val postonly = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/web/blob/admin/associations/attach"))).unsafeRunSync()
      val headmcp = app.run(HRequest[IO](method = Method.HEAD, uri = Uri.unsafeFromString("/mcp"))).unsafeRunSync()

      headweb.status shouldBe getweb.status
      headweb.contentType.map(_.mediaType) shouldBe getweb.contentType.map(_.mediaType)
      headweb.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
      headasset.status.code shouldBe 200
      headasset.contentType.map(_.mediaType) shouldBe Some(MediaType.text.css)
      headasset.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
      postonly.status.code shouldBe 404
      postonly.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
      headmcp.status.code shouldBe 404
      headmcp.body.compile.to(Array).unsafeRunSync().toVector shouldBe Vector.empty
    }

    "keep web demo assist manifest disabled by default" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?textus.demo.manifest=json&body=secret-input")
        ))
        .unsafeRunSync()

      response.status.code shouldBe 404
      response.as[String].unsafeRunSync() should not include ("secret-input")
    }

    "serve safe web demo assist manifest when explicitly enabled" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString),
            "cncf.web.demo-assist.enabled" ->
              ConfigurationValue.StringValue("true")
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?body=hello&textus.download=true&textus.download.source=result.body&textus.download.format=csv&textus.download.filename=echo.csv")
        ))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      response.headers.get(ci"Content-Disposition").map(_.head.value) shouldBe Some("""attachment; filename="echo.csv"""")
      response.contentType.map(_.mediaType) shouldBe MediaType.parse("text/csv").toOption
      body should include ("body")
      body should include ("hello")
      body should include ("path")
      body should not include ("<html")
    }

    "download form result source as Excel attachment" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app
        .run(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/form/debug/http/echo/result?body=hello&textus.download=true&textus.download.source=result.body&textus.download.format=xlsx&textus.download.filename=echo.xlsx")
        ))
        .unsafeRunSync()
      val bytes = response.body.compile.to(Array).unsafeRunSync()

      response.status.code shouldBe 200
      response.headers.get(ci"Content-Disposition").map(_.head.value) shouldBe Some("""attachment; filename="echo.xlsx"""")
      response.contentType.map(_.mediaType) shouldBe MediaType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toOption
      bytes.length should be > 0
    }

    "not inject current-session principal attributes into form-api submits" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val debug = subsystem.findComponent("debug").getOrElse(fail("missing debug component"))
      debug.withArtifactMetadata(
        Component.ArtifactMetadata(
          sourceType = "spec",
          name = "debug-alias",
          version = "0.0.0",
          component = Some("textus-user-account")
        )
      )
      subsystem.add(Vector(_auth_component))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val boundary = "----WebKitFormBoundaryEmptySpec"
      val request = HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/form-api/debug/http/echo"))
        .withEntity(s"--${boundary}--\r\n")
        .withContentType(`Content-Type`.parse(s"multipart/form-data; boundary=${boundary}").toOption.get)

      val response = server
        ._submit_operation_form_api(request, "debug", "http", "echo")
        .unsafeRunSync()

      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("path: \"/debug/http/echo\"")
      body should include("method: \"POST\"")
    }

    "enforce protected exposure before dispatching form submits" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

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

      htmlresponse.status.code shouldBe 403
      apiresponse.status.code shouldBe 403
      htmlresponse.as[String].unsafeRunSync() should include ("Forbidden")
      apiresponse.as[String].unsafeRunSync() should include ("Forbidden")
    }

    "return debug job id header for debug trace-job form-api requests" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(web.toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

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

      response.status.code shouldBe 200
      response.headers.get(ci"X-Textus-Job-Id").map(_.head.value).getOrElse("") should include ("cncf-job-job")
    }

    "render unauthorized operation-result widgets as inline page errors" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_web_app(
          "debug",
          "debug-app",
          Vector.empty,
          Some(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app")))
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      body should include ("Static Form operation-result operation is not authorized")
      body should not include "Debug app sign in required"
    }

    "render operation-result widgets without knowledge summary by default" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server
        ._component_web_app(
          "debug",
          "debug-app",
          Vector.empty,
          Some(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app")))
        )
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      body should not include "KnowledgeSpace"
    }

    "dispatch static Web app page aliases below the app root" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app
        .run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug-app/seed")))
        .unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

      response.status.code shouldBe 200
      body should include ("Seed Page")
      body should not include ("Debug Home")
    }

    "inject subsystem theme into component static Web pages" in {
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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server._component_web_app("debug", "debug-app", Vector.empty).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()
      val asset = server._web_global_asset("theme.css").unsafeRunSync()
      val nestedglobalasset = server._web_global_asset(Vector("fonts", "brand.woff2")).unsafeRunSync()
      val nestedappasset = server._web_app_asset("debug", "debug-app", Vector("fonts", "app.woff2")).unsafeRunSync()
      val favicon = server._favicon().unsafeRunSync()
      val generatedpage = server._static_form_app("console", Vector.empty).unsafeRunSync()
      val generatedbody = generatedpage.as[String].unsafeRunSync()

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
            RuntimeConfig.WebDescriptorKey ->
              ConfigurationValue.StringValue(root.resolve("web.yaml").toString)
          )
        ),
        ConfigurationTrace.empty
      )
      val subsystem = DefaultSubsystemFactory.default(None, configuration)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val applicationadmin = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/admin"))).unsafeRunSync()
      val declared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/notifications"))).unsafeRunSync()
      val undeclared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/unknown"))).unsafeRunSync()
      val applicationadminbody = applicationadmin.as[String].unsafeRunSync()

      applicationadmin.status.code shouldBe 200
      applicationadminbody should include ("Application Admin")
      applicationadminbody should include ("Notification Admin")
      declared.status.code shouldBe 200
      declared.as[String].unsafeRunSync() should include ("Notification Admin")
      undeclared.status.code shouldBe 404
    }

    "dispatch system observability drill-down routes" in {
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val home = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability"))).unsafeRunSync()
      val metrics = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/metrics"))).unsafeRunSync()
      val diagnostics = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics"))).unsafeRunSync()
      val detail = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics/validation/ob04_format"))).unsafeRunSync()
      val unknown = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/observability/diagnostics/validation/missing"))).unsafeRunSync()

      home.status.code shouldBe 200
      home.as[String].unsafeRunSync() should include ("System Observability")
      metrics.status.code shouldBe 200
      metrics.as[String].unsafeRunSync() should include ("Observability Metrics")
      diagnostics.status.code shouldBe 200
      diagnostics.as[String].unsafeRunSync() should include ("ob04_format")
      detail.status.code shouldBe 200
      val detailbody = detail.as[String].unsafeRunSync()
      detailbody should include ("argument.invalid")
      detailbody should include ("1010401")
      unknown.status.code shouldBe 404
    }

    "dispatch system knowledge admin routes" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.add(TestComponentFactory.create("knowledge_component", Protocol.empty))
      val component = subsystem.findComponent("knowledge_component").getOrElse(fail("knowledge component missing"))
      component.knowledgeSpace.replace(KnowledgeWorkingSetSnapshot(
        nodes = Vector(KnowledgeNode(KnowledgeNodeId("node-1"), "concept", Some("Node One")))
      )) match {
        case Consequence.Success(_) => ()
        case Consequence.Failure(conclusion) => fail(conclusion.toString)
      }
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val index = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge"))).unsafeRunSync()
      val componentpage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge-component"))).unsafeRunSync()
      val nodepage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge_component/nodes/node-1"))).unsafeRunSync()
      val unknowncomponent = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/missing"))).unsafeRunSync()
      val unknownnode = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/knowledge/knowledge_component/nodes/missing"))).unsafeRunSync()

      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("System Knowledge")
      componentpage.status.code shouldBe 200
      componentpage.as[String].unsafeRunSync() should include ("node-1")
      nodepage.status.code shouldBe 200
      nodepage.as[String].unsafeRunSync() should include ("Node One")
      unknowncomponent.status.code shouldBe 404
      unknownnode.status.code shouldBe 404
    }

    "dispatch system information admin routes" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.add(TestComponentFactory.create("information_component", Protocol.empty))
      val component = subsystem.findComponent("information_component").getOrElse(fail("information component missing"))
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
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val index = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information"))).unsafeRunSync()
      val componentpage = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information/information-component"))).unsafeRunSync()
      val unknowncomponent = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/system/admin/information/missing"))).unsafeRunSync()

      index.status.code shouldBe 200
      index.as[String].unsafeRunSync() should include ("System Information")
      componentpage.status.code shouldBe 200
      componentpage.as[String].unsafeRunSync() should include ("Information Import")
      unknowncomponent.status.code shouldBe 404
    }

    "dispatch app-facing TagSpace routes" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound

      val response = app.run(HRequest[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString("/web/tag/tags?tagSpace=information")
      ).putHeaders(org.http4s.Header.Raw(ci"x-textus-session", "forged-session"))).unsafeRunSync()
      val body = response.as[String].unsafeRunSync()

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
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val app = server.routes(null.asInstanceOf[org.http4s.server.websocket.WebSocketBuilder2[IO]]).orNotFound
      val request = HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/mcp"))
        .withEntity("""{"jsonrpc":"2.0","id":"tools","method":"tools/list","params":{}}""")
        .withContentType(`Content-Type`.parse("application/json").toOption.get)

      val response = app.run(request).unsafeRunSync()

      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include (""""id":"tools"""")
      body should include (""""tools"""")
    }
  }

  private def _post_form_request(path: String, body: String): HRequest[IO] =
    HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString(path))
      .withEntity(body)
      .withContentType(`Content-Type`.parse("application/x-www-form-urlencoded").toOption.get)

  private def _auth_component = {
    given ExecutionContext = ExecutionContext.create()
    new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "session_provider",
          ComponentId("session_provider"),
          ComponentInstanceId.default(ComponentId("session_provider")),
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
