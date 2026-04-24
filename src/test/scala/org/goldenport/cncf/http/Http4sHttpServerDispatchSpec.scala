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
 * @version Apr. 24, 2026
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
            org.http4s.Header.Raw(ci"x-textus-session", "session-1")
          ),
          "textus-user-account",
          "http",
          "echo"
        )
        .unsafeRunSync()

      response.status.code shouldBe 200
      val body = response.as[String].unsafeRunSync()
      body should include("x-textus-session")
      body should not include ("principalId")
      body should not include ("principal_id")
      body should not include ("authenticated")
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
