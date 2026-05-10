package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{Capability, ExecutionContext, PrincipalId, SecurityLevel, SessionContext, SubjectKind}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.http4s.{Method, Request as HRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIStringSyntax

/*
 * @since   Apr. 24, 2026
 *  version Apr. 25, 2026
 * @version May. 10, 2026
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

      val htmlResponse = server
        ._submit_operation_form(
          _post_form_request("/form/debug/http/echo", "body=hello"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()
      val apiResponse = server
        ._submit_operation_form_api(
          _post_form_request("/form-api/debug/http/echo", "body=hello"),
          "debug",
          "http",
          "echo"
        )
        .unsafeRunSync()

      htmlResponse.status.code shouldBe 403
      apiResponse.status.code shouldBe 403
      htmlResponse.as[String].unsafeRunSync() should include ("Forbidden")
      apiResponse.as[String].unsafeRunSync() should include ("Forbidden")
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

    "resolve operation-result forbidden templates by owning Web app" in {
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

      response.status.code shouldBe 403
      body should include ("Debug app sign in required")
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
      val nestedGlobalAsset = server._web_global_asset(Vector("fonts", "brand.woff2")).unsafeRunSync()
      val nestedAppAsset = server._web_app_asset("debug", "debug-app", Vector("fonts", "app.woff2")).unsafeRunSync()
      val favicon = server._favicon().unsafeRunSync()
      val generatedPage = server._static_form_app("console", Vector.empty).unsafeRunSync()
      val generatedBody = generatedPage.as[String].unsafeRunSync()

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
      nestedGlobalAsset.status.code shouldBe 200
      nestedAppAsset.status.code shouldBe 200
      favicon.status.code shouldBe 200
      favicon.as[String].unsafeRunSync() should include ("<svg")
      generatedPage.status.code shouldBe 200
      generatedBody should include ("""rel="icon" href="/web/assets/favicon.svg"""")
      generatedBody should include ("/web/assets/console-theme.css")
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

      val applicationAdmin = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/admin"))).unsafeRunSync()
      val declared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/notifications"))).unsafeRunSync()
      val undeclared = app.run(HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString("/web/debug/admin/unknown"))).unsafeRunSync()
      val applicationAdminBody = applicationAdmin.as[String].unsafeRunSync()

      applicationAdmin.status.code shouldBe 200
      applicationAdminBody should include ("Application Admin")
      applicationAdminBody should include ("Notification Admin")
      declared.status.code shouldBe 200
      declared.as[String].unsafeRunSync() should include ("Notification Admin")
      undeclared.status.code shouldBe 404
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
