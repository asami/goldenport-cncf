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
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult, PublicPrincipalId, SessionId}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.http4s.{Header, Method, Request as HRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.typelevel.ci.CIStringSyntax

/*
 * @since   Apr. 23, 2026
 *  version May. 10, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class AuthenticationWebSessionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:subsystem-user-mode-web-session, example:PM-53-01, rules:PM-53-01, phase:53")

  "Http4s auth session flow" must _in_phase53_spec {
    "session authentication and transport normalization" which {
    "set subsystem-scoped cookie on successful login and expose the same session via x-textus-session" in {
      Given("a Subsystem with a login-capable session provider")
      val provider = new SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the built-in login endpoint receives Alice's credentials")
      val login = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()

      Then("the login response sets the Subsystem-scoped session cookie")
      login.status.code shouldBe 303
      val setcookie = _header(login, "Set-Cookie").getOrElse(fail("missing Set-Cookie"))
      setcookie should include (_cookie_name(subsystem))

      When("the resulting session identifier is presented to the session endpoint")
      val sessionid = provider.sessionIdFor("alice").getOrElse(fail("session not recorded"))
      val session = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(Header.Raw(ci"x-textus-session", sessionid))
      ).unsafeRunSync()

      Then("the session endpoint exposes the same authenticated public session")
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
      Given("an authenticated session with its Subsystem-scoped cookie")
      val provider = new SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val _ = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()
      val sessionid = provider.sessionIdFor("alice").getOrElse(fail("session not recorded"))

      When("the logout endpoint receives that cookie")
      val logout = server.routes(null).orNotFound.run(
        HRequest[IO](method = Method.POST, uri = Uri.unsafeFromString("/web/cwitter/logout"))
          .putHeaders(Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=${sessionid}"))
      ).unsafeRunSync()

      Then("the logout response clears the Subsystem-scoped cookie")
      logout.status.code shouldBe 303
      _header(logout, "Set-Cookie").getOrElse(fail("missing Set-Cookie")) should include ("Max-Age=0")

      When("the session endpoint is requested after logout")
      val session = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session")
      ).unsafeRunSync()

      Then("the follow-up session is anonymous")
      val json = parse(session.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[Boolean]("authenticated").toOption shouldBe Some(false)
      json.hcursor.get[String]("principalId").toOption shouldBe Some("anonymous")
    }

    "prefer x-textus-session over cookie when both are present" in {
      Given("distinct cookie and explicit header sessions")
      val provider = new SessionProvider(
        Map("alice" -> "secret", "bob" -> "secret"),
        Map(
          "cookie-session" -> "alice",
          "header-session" -> "bob"
        )
      )
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the session endpoint receives both transports")
      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(
          Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=cookie-session"),
          Header.Raw(ci"x-textus-session", "header-session")
        )
      ).unsafeRunSync()

      Then("the explicit header session is selected")
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[String]("principalId").toOption shouldBe Some("bob")
      json.hcursor.get[String]("sessionId").toOption shouldBe Some("header-session")
    }

    "return the same normalized summary from built-in auth.session" in {
      Given("a Subsystem with a persisted authenticated session")
      val provider = new SessionProvider(Map("alice" -> "secret"), Map("sess-1" -> "alice"))
      val subsystem = _subsystem(provider)

      When("the built-in auth.session operation receives the session header")
      val response = subsystem.executeOperationResponse(
        Request
          .of(component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH.name, service = "auth", operation = "session")
          .copy(
            properties = List(
              Property("x-textus-session", "sess-1", None)
            )
          )
      )

      Then("the operation emits the normalized public session summary")
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

    "summarize long internal principal ids with safe public identifiers" in {
      Given("a long internal principal with a configured public identifier")
      val internalid = "single-global-entity-user_account-1780527539701-7CtuvcCpxcWarnFB0XvpQm"
      val provider = new SessionProvider(
        Map.empty,
        Map("sess-long" -> internalid),
        publicprincipalids = Map(internalid -> "debug-user"),
        useraccountids = Map(internalid -> internalid)
      )
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the session is resolved through Web and built-in auth")
      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(Header.Raw(ci"x-textus-session", "sess-long"))
      ).unsafeRunSync()

      Then("the Web session publishes only the safe public identity")
      response.status.code shouldBe 200
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[String]("principalId").toOption shouldBe Some("debug-user")
      json.hcursor.downField("attributes").get[String]("userAccountId").toOption shouldBe None
      json.hcursor.downField("attributes").get[String]("user_account_id").toOption shouldBe None

      When("the same session is resolved through the built-in auth operation")
      val current = subsystem.executeOperationResponse(
        Request
          .of(component = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.AUTH.name, service = "auth", operation = "session")
          .copy(properties = List(Property("x-textus-session", "sess-long", None)))
      )
      Then("the built-in operation publishes the same safe identity")
      current shouldBe a[Consequence.Success[_]]
      current.toOption.get match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("principalId") shouldBe Some("debug-user")
          record.getRecord("attributes").flatMap(_.getString("userAccountId")) shouldBe None
        case other =>
          fail(s"unexpected response: $other")
      }
    }

    "ignore oversized cookie session ids before provider authentication" in {
      Given("an oversized session cookie")
      val oversized = "s" * (SessionId.LENGTH_MAX + 1)
      val provider = new SessionProvider(Map.empty, Map(oversized -> "alice"))
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the session endpoint receives the oversized cookie")
      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/session").putHeaders(
          Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=$oversized")
        )
      ).unsafeRunSync()

      Then("the request remains anonymous without authenticating the value")
      response.status.code shouldBe 200
      val json = parse(response.as[String].unsafeRunSync()).getOrElse(fail("invalid session json"))
      json.hcursor.get[Boolean]("authenticated").toOption shouldBe Some(false)
      json.hcursor.get[String]("principalId").toOption shouldBe Some("anonymous")
    }

    "define dedicated length limits for public auth identifiers" in {
      Given("identifier values at and beyond each public length boundary")
      When("the session and public-principal identifiers are parsed")
      val validsession = SessionId.option("s" * SessionId.LENGTH_MAX).map(_.value)
      val invalidsession = SessionId.option("s" * (SessionId.LENGTH_MAX + 1))
      val validprincipal = PublicPrincipalId.option("p" * PublicPrincipalId.LENGTH_MAX).map(_.value)
      val invalidprincipal = PublicPrincipalId.option("p" * (PublicPrincipalId.LENGTH_MAX + 1))
      Then("only values within their respective bounds are admitted")
      validsession shouldBe Some("s" * SessionId.LENGTH_MAX)
      invalidsession shouldBe None
      validprincipal shouldBe Some("p" * PublicPrincipalId.LENGTH_MAX)
      invalidprincipal shouldBe None
    }

    "delegate exact login alias routes to provider-owned UI when configured" in {
      Given("a Web descriptor with a provider-owned login alias")
      val provider = new SessionProvider(Map("alice" -> "secret"), Map.empty)
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
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("the exact alias route is requested")
      val response = server.routes(null).orNotFound.run(
        _get_request("/web/cwitter/login")
      ).unsafeRunSync()

      Then("the provider UI, rather than built-in login, is rendered")
      response.status.code shouldBe 200
      response.as[String].unsafeRunSync() should include ("Provider Sign In")
    }

    "honor returnTo on successful built-in login" in {
      Given("a built-in login request carrying a local returnTo target")
      val provider = new SessionProvider(Map("alice" -> "secret"), Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      When("valid credentials are posted to that login request")
      val login = server.routes(null).orNotFound.run(
        _post_form_request(
          "/web/cwitter/login?returnTo=/web/cwitter",
          "username=alice&password=secret"
        )
      ).unsafeRunSync()

      Then("the successful redirect honors the requested target")
      login.status.code shouldBe 303
      _header(login, "Location") shouldBe Some("/web/cwitter")
    }

    "normalize form-api session id from header, cookie, form, and query in that order" in {
      Given("the same candidate session id in header, cookie, form, and query")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val query = org.goldenport.record.Record.create(Vector("x-textus-session" -> "query-session"))
      val form = org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))
      val headerrequest = _get_request("/form-api/cwitter/timeline/create-post")
        .putHeaders(Header.Raw(ci"x-textus-session", "header-session"))
      val cookierequest = _get_request("/form-api/cwitter/timeline/create-post")
        .putHeaders(Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=cookie-session"))

      When("form-api session normalization examines each transport")
      val fromheader = server._session_id_(headerrequest, query, form)
      val fromcookie = server._session_id_(cookierequest, query, form)
      val fromform = server._session_id_(_get_request("/form-api/cwitter/timeline/create-post"), query, form)
      val fromquery = server._session_id_(_get_request("/form-api/cwitter/timeline/create-post"), query, org.goldenport.record.Record.empty)
      Then("the documented header, cookie, form, query precedence is applied")
      fromheader shouldBe Some("header-session")
      fromcookie shouldBe Some("cookie-session")
      fromform shouldBe Some("form-session")
      fromquery shouldBe Some("query-session")
    }

    "accept app-scoped Web session cookies for composed Web apps" in {
      Given("a composed Web app request with its app-scoped cookie")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))

      val request = _get_request("/web/notifications").putHeaders(
        Header.Raw(ci"Cookie", "textus-session-notifications=notification-session")
      )

      When("the server extracts its session id")
      val sessionid = server._session_id_(request)
      Then("the app-scoped session cookie is accepted")
      sessionid shouldBe Some("notification-session")
    }

    "ignore invalid long session cookies and use the next valid session candidate" in {
      Given("an invalid long cookie and a valid form session")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val stalesession = "s" * 72
      val request = _post_form_request("/form-api/cwitter/timeline/create-post", "body=hello")
        .putHeaders(Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=${stalesession}"))
      val form = org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))

      When("the server normalizes the request session")
      val sessionid = server._session_id_(request, org.goldenport.record.Record.empty, form)
      Then("the invalid cookie is skipped in favor of the valid candidate")
      sessionid shouldBe Some("form-session")
    }

    "promote fallback form-api session id into the auth header record" in {
      Given("a form-api request with only a form session value")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val request = _post_form_request("/form-api/cwitter/timeline/create-post", "body=hello")
      When("the authentication header record is assembled")
      val headers = server._request_header_record(
        request,
        org.goldenport.record.Record.empty,
        org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))
      )

      Then("the fallback value is promoted into the authentication header record")
      headers.getString("x-textus-session") shouldBe Some("form-session")
    }

    "replace invalid session headers with the next valid session candidate in header records" in {
      Given("an invalid header session and a valid form candidate")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val stalesession = "s" * 72
      val request = _post_form_request("/form-api/cwitter/timeline/create-post", "body=hello")
        .putHeaders(Header.Raw(ci"x-textus-session", stalesession))
      val form = org.goldenport.record.Record.create(Vector("x-textus-session" -> "form-session"))

      When("the authentication header record is assembled")
      val headers = server._request_header_record(request, org.goldenport.record.Record.empty, form)

      Then("the valid fallback replaces the invalid header value")
      headers.getString("x-textus-session") shouldBe Some("form-session")
    }

    "extract session cookies without copying raw Cookie header into header records" in {
      Given("a Web request carrying a session cookie")
      val provider = new SessionProvider(Map.empty, Map.empty)
      val subsystem = _subsystem(provider)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val request = _get_request("/web/cwitter/session").putHeaders(
        Header.Raw(ci"Cookie", s"${_cookie_name(subsystem)}=cookie-session")
      )

      When("the authentication header record is assembled")
      val headers = server._request_header_record(request)

      Then("only the normalized session value is retained")
      headers.getString("x-textus-session") shouldBe Some("cookie-session")
      headers.getString("Cookie") shouldBe None
      headers.getString("cookie") shouldBe None
    }
    }
  }

  private def _subsystem(
    provider: SessionProvider,
    webdescriptor: Option[Path] = None
  ) = {
    given ExecutionContext = ExecutionContext.create()
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "org.goldenport.cncf.test.SessionProvider",
          ComponentId("org.goldenport.cncf.test.SessionProvider"),
          ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.SessionProvider")),
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
        (webdescriptor.toVector.map(path =>
          RuntimeConfig.webDescriptorKey -> org.goldenport.configuration.ConfigurationValue.StringValue(path.toString)
        ) :+ (
          org.goldenport.cncf.subsystem.SubsystemUserMode.CONFIGURATION_KEY ->
            org.goldenport.configuration.ConfigurationValue.StringValue("multi-user")
        )).toMap
      ),
      ConfigurationTrace.empty
    )
    val subsystem = DefaultSubsystemFactory.default(None, configuration)
    subsystem.add(Vector(component))
    HttpRuntimeBindingAdmissionFixture.admit(subsystem)
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

  private final class SessionProvider(
    credentials: Map[String, String],
    seededsessions: Map[String, String],
    publicprincipalids: Map[String, String] = Map.empty,
    useraccountids: Map[String, String] = Map.empty
  ) extends AuthenticationProvider {
    private var _sessions: Map[String, String] = seededsessions

    def name: String = "session-provider"

    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
      request.sessionId match {
        case Some(sessionid) =>
          _sessions.get(sessionid) match {
            case Some(principalId) =>
              Consequence.success(Some(_result(principalId, sessionid)))
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
          Consequence.success(Some(_result(username, sessionid)))
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

    def sessionIdFor(principalId: String): Option[String] =
      _sessions.collectFirst {
        case (sessionid, principal) if principal == principalId => sessionid
      }

    private def _result(principalid: String, sessionid: String): AuthenticationResult =
      val publicid = publicprincipalids.getOrElse(principalid, principalid)
      AuthenticationResult(
        principalId = PrincipalId(principalid),
        attributes = Map(
          "publicPrincipalId" -> publicid,
          "login_name" -> publicid,
          "handle" -> publicid,
          "shortid" -> s"${publicid}-short",
          "email" -> s"${publicid}@example.com",
          "userAccountId" -> useraccountids.getOrElse(principalid, ""),
          "access_token" -> s"access-${publicid}",
          "refresh_token" -> s"refresh-${publicid}"
        ).filter(_._2.nonEmpty),
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
