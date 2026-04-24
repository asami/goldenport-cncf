package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, SessionContext}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.http4s.{Header, Method, Request as HRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIStringSyntax

/*
 * @since   Apr. 23, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class AuthenticationWebSessionSpec extends AnyWordSpec with Matchers {
  "Http4s auth session flow" should {
    "set subsystem-scoped cookie on successful login and expose the same session via x-textus-session" in {
      val provider = new _SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val login = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()

      login.status.code shouldBe 303
      val setcookie = _header(login, "Set-Cookie").getOrElse(fail("missing Set-Cookie"))
      setcookie should include (_cookie_name(subsystem))

      val sessionid = provider.sessionIdFor("alice").getOrElse(fail("session not recorded"))
      val session = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(Header.Raw(ci"x-textus-session", sessionid))
      ).unsafeRunSync()

      session.status.code shouldBe 200
      val json = parse(session.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[String]("principalId").toOption shouldBe Some("alice")
      json.hcursor.get[String]("sessionId").toOption shouldBe Some(sessionid)
      json.hcursor.get[Boolean]("authenticated").toOption shouldBe Some(true)
      val attrs = json.hcursor.downField("attributes")
      attrs.get[String]("login_name").toOption shouldBe Some("alice")
      attrs.get[String]("handle").toOption shouldBe Some("alice")
      attrs.get[String]("shortid").toOption shouldBe Some("alice-short")
      attrs.get[String]("email").toOption shouldBe Some("alice@example.com")
      attrs.get[String]("access_token").toOption shouldBe None
      attrs.get[String]("refresh_token").toOption shouldBe None
    }

    "clear cookie on logout and return anonymous current-session afterwards" in {
      val provider = new _SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val _ = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()
      val sessionid = provider.sessionIdFor("alice").getOrElse(fail("session not recorded"))

      val logout = server.routes(null).orNotFound.run(
        HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/web/cwitter/logout"))
          .putHeaders(Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=${sessionid}"))
      ).unsafeRunSync()

      logout.status.code shouldBe 303
      _header(logout, "Set-Cookie").getOrElse(fail("missing Set-Cookie")) should include ("Max-Age=0")

      val session = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session")
      ).unsafeRunSync()

      val json = parse(session.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[Boolean]("authenticated").toOption shouldBe Some(false)
      json.hcursor.get[String]("principalId").toOption shouldBe Some("anonymous")
    }

    "prefer x-textus-session over cookie when both are present" in {
      val provider = new _SessionProvider(
        Map("alice" -> "secret", "bob" -> "secret"),
        Map(
          "cookie-session" -> "alice",
          "header-session" -> "bob"
        )
      )
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(
          Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=cookie-session"),
          Header.Raw(ci"x-textus-session", "header-session")
        )
      ).unsafeRunSync()

      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[String]("principalId").toOption shouldBe Some("bob")
      json.hcursor.get[String]("sessionId").toOption shouldBe Some("header-session")
    }

    "return the same normalized summary from built-in auth.session" in {
      val provider = new _SessionProvider(Map("alice" -> "secret"), Map("sess-1" -> "alice"))
      val subsystem = _subsystem(provider)

      val response = subsystem.executeOperationResponse(
        Request
          .of(component = "auth", service = "auth", operation = "session")
          .copy(
            properties = List(
              Property("x-textus-session", "sess-1", None)
            )
          )
      )

      response shouldBe a[Consequence.Success[_]]
      response.toOption.get match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("principalId") shouldBe Some("alice")
          record.getString("sessionId") shouldBe Some("sess-1")
          record.getRecord("attributes").flatMap(_.getString("login_name")) shouldBe Some("alice")
          record.getRecord("attributes").flatMap(_.getString("handle")) shouldBe Some("alice")
          record.getRecord("attributes").flatMap(_.getString("shortid")) shouldBe Some("alice-short")
          record.getRecord("attributes").flatMap(_.getString("access_token")) shouldBe None
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "delegate exact login alias routes to provider-owned UI when configured" in {
      val provider = new _SessionProvider(Map("alice" -> "secret"), Map.empty)
      val webroot = _web_root(
        """routes:
          |  - path: /web/cwitter/login
          |    target:
          |      component: session_provider
          |      app: signin
          |""".stripMargin,
        Vector(
          "signin/index.html" -> "<!doctype html><html><body><h1>Provider Sign In</h1></body></html>"
        )
      )
      val subsystem = _subsystem(provider, Some(webroot.resolve("web.yaml")))
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/login")
      ).unsafeRunSync()

      response.status.code shouldBe 200
      response.as[String].unsafeRunSync() should include ("Provider Sign In")
    }

    "honor returnTo on successful built-in login" in {
      val provider = new _SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val login = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login?returnTo=/web/cwitter",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()

      login.status.code shouldBe 303
      _header(login, "Location") shouldBe Some("/web/cwitter")
    }

    "normalize form-api session id from header, cookie, form, and query in that order" in {
      val provider = new _SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))

      val query = org.goldenport.record.Record.create(Vector("x-textus-session" -> "query-session"))
      val form = org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))
      val headerRequest = _get_request("/form-api/cwitter/timeline/create-post")
        .putHeaders(Header.Raw(ci"x-textus-session", "header-session"))
      val cookieRequest = _get_request("/form-api/cwitter/timeline/create-post")
        .putHeaders(Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=cookie-session"))

      server._session_id_(headerRequest, query, form) shouldBe Some("header-session")
      server._session_id_(cookieRequest, query, form) shouldBe Some("cookie-session")
      server._session_id_(_get_request("/form-api/cwitter/timeline/create-post"), query, form) shouldBe Some("form-session")
      server._session_id_(_get_request("/form-api/cwitter/timeline/create-post"), query, org.goldenport.record.Record.empty) shouldBe Some("query-session")
    }

    "promote fallback form-api session id into the auth header record" in {
      val provider = new _SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
      val request = _post_form_request("/form-api/cwitter/timeline/create-post", "body=hello")
      val headers = server._request_header_record(
        request,
        org.goldenport.record.Record.empty,
        org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))
      )

      headers.getString("x-textus-session") shouldBe Some("form-session")
    }
  }

  private def _subsystem(
    provider: _SessionProvider,
    webDescriptor: Option[Path] = None
  ) = {
    given ExecutionContext = ExecutionContext.create()
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "session_provider",
          ComponentId("session_provider"),
          ComponentInstanceId.default(ComponentId("session_provider")),
          Protocol.empty
        )
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(provider)
    }.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = "session_provider",
        version = "0.0.0",
        component = Some("session_provider")
      )
    )
    val configuration = ResolvedConfiguration(
      Configuration(
        webDescriptor.toVector.map(path =>
          RuntimeConfig.WebDescriptorKey -> org.goldenport.configuration.ConfigurationValue.StringValue(path.toString)
        ).toMap
      ),
      ConfigurationTrace.empty
    )
    val subsystem = DefaultSubsystemFactory.default(None, configuration)
    subsystem.add(Vector(component))
    subsystem
  }

  private def _post_form_request(path: String, body: String): HRequest[IO] =
    HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString(path))
      .withEntity(body)
      .withContentType(`Content-Type`.parse("application/x-www-form-urlencoded").toOption.get)

  private def _get_request(path: String): HRequest[IO] =
    HRequest[IO](method = Method.GET, uri = Uri.unsafeFromString(path))

  private def _header(
    response: org.http4s.Response[IO],
    name: String
  ): Option[String] =
    response.headers.headers.collectFirst {
      case h if h.name.toString.equalsIgnoreCase(name) => h.value
    }

  private def _cookie_name(
    subsystem: org.goldenport.cncf.subsystem.Subsystem
  ): String =
    s"textus-session-${org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(subsystem.name)}"

  private final class _SessionProvider(
    credentials: Map[String, String],
    seededSessions: Map[String, String]
  ) extends AuthenticationProvider {
    private var _sessions: Map[String, String] = seededSessions

    def name: String = "session-provider"

    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
      request.sessionId match {
        case Some(sessionid) =>
          _sessions.get(sessionid) match {
            case Some(principalid) =>
              Consequence.success(Some(_result_(principalid, sessionid)))
            case None =>
              Consequence.argumentInvalid("invalid session")
          }
        case None =>
          Consequence.success(None)
      }

    override def login(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] = {
      val username = request.attribute("username").getOrElse("")
      val password = request.attribute("password").getOrElse("")
      credentials.get(username) match {
        case Some(expected) if expected == password =>
          val sessionid = s"session-${username}"
          _sessions = _sessions.updated(sessionid, username)
          Consequence.success(Some(_result_(username, sessionid)))
        case _ =>
          Consequence.argumentInvalid("invalid credentials")
      }
    }

    override def logout(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[SessionContext]] =
      request.sessionId match {
        case Some(sessionid) if _sessions.contains(sessionid) =>
          _sessions = _sessions.removed(sessionid)
          Consequence.success(Some(SessionContext(sessionId = Some(sessionid))))
        case Some(_) =>
          Consequence.argumentInvalid("invalid session")
        case None =>
          Consequence.success(None)
      }

    def sessionIdFor(principalid: String): Option[String] =
      _sessions.collectFirst {
        case (sessionid, principal) if principal == principalid => sessionid
      }

    private def _result_(principalid: String, sessionid: String): AuthenticationResult =
      AuthenticationResult(
        principalId = PrincipalId(principalid),
        attributes = Map(
          "login_name" -> principalid,
          "handle" -> principalid,
          "shortid" -> s"${principalid}-short",
          "email" -> s"${principalid}@example.com",
          "access_token" -> s"access-${principalid}",
          "refresh_token" -> s"refresh-${principalid}"
        ),
        session = Some(SessionContext(sessionId = Some(sessionid)))
      )
  }

  private def _web_root(
    descriptor: String,
    files: Vector[(String, String)]
  ): Path = {
    val root = Files.createTempDirectory("cncf-auth-web-")
    Files.writeString(root.resolve("web.yaml"), descriptor, StandardCharsets.UTF_8)
    files.foreach { case (relative, content) =>
      val path = root.resolve(relative)
      Option(path.getParent).foreach(parent => Files.createDirectories(parent))
      Files.writeString(path, content, StandardCharsets.UTF_8)
    }
    root
  }
}
