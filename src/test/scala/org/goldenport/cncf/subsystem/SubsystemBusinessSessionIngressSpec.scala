package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext, ScopeKind, SessionContext, SubjectKind}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.goldenport.schema.{Multiplicity, ValueDomain, XString}
import org.goldenport.value.BaseContent
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class SubsystemBusinessSessionIngressSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SubsystemBusinessSessionIngressSpec.*

  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:fixed-profile-service-ingress, example:$example, rules:$rules, phase:sm-workflow-6")

  "Subsystem business session ingress" should {
    "E2 preserve declared property sessionId and the configured local subject" must _metadata("E2", "R4") {
      "when business session values cross the normal Request boundary" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4; Example: E2; a Fixed profile with a local subject and a declared business sessionId property")
        val fixture = _fixture()
        val values = Vector("business-property", "business-" + ("x" * 80))
        val requests = values.map(value => _request(properties = List(Property("sessionId", value, None))))

        When("the caller invokes the declared business operation with short and non-authentication-shaped long IDs")
        val results = requests.map(fixture.subsystem.executeWithMetadata)

        Then("the original business values reach the operation unchanged and the local principal remains authoritative")
        results.zip(values).foreach { (result, value) => _accepted(result, value, "configured-local") }
        fixture.observation.requests shouldBe requests

        When("bounded generated business IDs cross the same public boundary")
        val property = Prop.forAll(Gen.choose(1, 96)) { length =>
          val value = "b" * length
          val request = _request(properties = List(Property("sessionId", value, None)))
          val result = fixture.subsystem.executeWithMetadata(request)
          result.toOption.exists(_.response == OperationResponse.Scalar(_echo(value, "configured-local"))) &&
            fixture.observation.requests.lastOption.contains(request)
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)

        Then("every generated value remains business context without changing principal or Request")
        withClue(checked.toString) { checked.passed shouldBe true }
      }
    }

    "E3 reserve normalized snake-case argument session ownership" must _metadata("E3", "R4") {
      "when declared and supplied bare session names use accepted spelling variants" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4; Example: E3; a Fixed local-subject operation declaring argument session_id")
        val fixture = _fixture()
        val requests = Vector("session_id", "sessionId", "SESSION_ID", "Session-Id").map { name =>
          _request("argumentEcho", arguments = List(Argument(name, "business-argument")))
        }

        When("the public Request boundary receives canonical, case and separator variants")
        val results = requests.map(fixture.subsystem.executeWithMetadata)

        Then("the argument remains in each original Request and does not replace the configured local principal")
        results.foreach(result => _accepted(result, "business-argument", "configured-local"))
        fixture.observation.requests shouldBe requests
      }
    }

    "E4 preserve business session ownership through public Action execution" must _metadata("E4", "R4") {
      "when a business Action enters the same Fixed security boundary" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4; Example: E4; a Fixed local-subject operation declaring business sessionId")
        val fixture = _fixture()
        val request = _request(properties = List(Property("sessionId", "business-action", None)))
        val action = BusinessSessionAction(request, fixture.observation)

        When("the caller executes the Action through Subsystem.executeAction")
        val result = fixture.subsystem.executeAction(action)

        Then("the operation actually executes with its original business ID and the local principal")
        withClue(result.display) { result.isSuccess shouldBe true }
        result.toOption.get shouldBe OperationResponse.Scalar(_echo("business-action", "configured-local"))
        fixture.observation.requests shouldBe Vector(request)
      }
    }

    "E5 preserve GET query business session context through normal HTTP ingress" must _metadata("E5", "R4") {
      "when an HTTP caller supplies the declared business session query" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4; Example: E5; a Fixed local-subject component with its normal HTTP protocol handler")
        val fixture = _fixture()
        val request = _http_get("business-get")

        When("Subsystem.executeHttp receives the normal GET query")
        val response = fixture.subsystem.executeHttp(request)

        Then("HTTP 200 exposes the unchanged business ID and configured local principal")
        _http_accepted(response, "business-get", "configured-local")
        fixture.observation.requests.size shouldBe 1
        _business_value(fixture.observation.requests.head) shouldBe "business-get"
      }
    }

    "E6 preserve named JSON business session context through normal HTTP ingress" must _metadata("E6", "R4") {
      "when an HTTP caller submits a declared business session property" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4; Example: E6; a Fixed local-subject component with its normal HTTP protocol handler and the HTTP server-decoded JSON form")
        val fixture = _fixture()
        val payload = """{"sessionId":"business-post"}"""
        val form = new RecordDecoder().json(payload).toOption.get
        val request = HttpRequest.fromPath(
          method = HttpRequest.POST,
          path = _http_path,
          header = Record.data("Content-Type" -> "application/json"),
          form = form
        )

        When("the normal HTTP server-decoded named JSON POST form enters Subsystem.executeHttp")
        val response = fixture.subsystem.executeHttp(request)

        Then("HTTP 200 exposes the unchanged business ID and configured local principal")
        _http_accepted(response, "business-post", "configured-local")
        fixture.observation.requests.size shouldBe 1
        _business_value(fixture.observation.requests.head) shouldBe "business-post"
      }
    }

    "E7 retain explicit authentication evidence beside declared business sessionId" must _metadata("E7", "R1,R2,R3,R5") {
      "when no service provider accepts the reserved credential evidence" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R1,R2,R3,R5; Example: E7; a Fixed local-subject operation with declared business sessionId and no authentication provider")
        val fixture = _fixture()
        val credentials = Vector("authorization", "Authorization", "access_token", "ACCESS-TOKEN",
          "refreshToken", "federation.assertion", "x-textus-session", "X_Textus_Session",
          "x-cncf-session", "X-CNCF-SESSION", "textus.session", "cncf.session")
        val requests = credentials.map { name =>
          _request(properties = List(Property("sessionId", "business-reserved", None), Property(name, "explicit-auth", None)))
        }
        val httprequests = Vector("Authorization", "X-Textus-Session", "X-Cncf-Session").map { name =>
          _http_get("business-header", Record.data(name -> "explicit-auth"))
        }

        When("public Request and HTTP ingress receive business context alongside explicit credential fields and headers")
        val results = requests.map(fixture.subsystem.executeWithMetadata)
        val httpresults = httprequests.map(fixture.subsystem.executeHttp)

        Then("every invocation retains the ordinary Fixed security rejection and the business operation never runs")
        results.foreach { result =>
          _rejected(result)
          result.display should include("service authentication provider")
        }
        httpresults.foreach { response =>
          response.code should not be 200
          response.getString.getOrElse("") should include("service authentication provider")
        }
        fixture.observation.requests shouldBe empty
      }
    }

    "E8 retain undeclared bare session aliases as authentication input" must _metadata("E8", "R5") {
      "when an operation does not declare a business session parameter" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R5; Example: E8; a Fixed local-subject operation with an empty request declaration and no provider")
        val fixture = _fixture()
        val requests = Vector("sessionId", "session_id").map { name =>
          _request("undeclaredEcho", properties = List(Property(name, "undeclared-auth", None)))
        }

        When("public ingress receives the undeclared bare aliases")
        val results = requests.map(fixture.subsystem.executeWithMetadata)

        Then("both aliases retain ordinary authentication meaning and cannot reach the operation")
        results.foreach { result =>
          _rejected(result)
          result.display should include("service authentication provider")
        }
        Vector("sessionId", "session_id").foreach { name =>
          AuthenticationRequest(Map(name -> "undeclared-auth")).sessionId shouldBe Some("undeclared-auth")
        }
        fixture.observation.requests shouldBe empty
      }
    }

    "E9 require authentication evidence beyond a declared business session" must _metadata("E9", "R4,R5") {
      "when a protected Authenticated operation receives only business context" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4,R5; Example: E9; an Authenticated profile with a configured provider and a declared business sessionId")
        val provider = new RecordingProvider
        val fixture = _fixture(authenticated = true, providers = Vector(provider))
        val request = _request(properties = List(Property("sessionId", "business-only", None)))

        When("the caller supplies only business session context")
        val result = fixture.subsystem.executeWithMetadata(request)

        Then("ingress rejects missing authentication evidence without invoking the provider or protected operation")
        _rejected(result)
        result.display should include("requires authentication ingress evidence")
        provider.requests shouldBe empty
        fixture.observation.requests shouldBe empty
      }
    }

    "E10 retain provider session authentication beside a distinct business ID" must _metadata("E10", "R4,R5") {
      "when explicit session properties, headers and cookies authenticate the caller" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R4,R5; Example: E10; an Authenticated component whose provider accepts auth-session with principal provider-user and auth session provider-session")
        val provider = new RecordingProvider
        val fixture = _fixture(authenticated = true, providers = Vector(provider))
        val requests = Vector("x-textus-session", "x-cncf-session", "textus.session", "cncf.session").map { name =>
          _request(properties = List(Property("sessionId", "business-distinct", None), Property(name, "auth-session", None)))
        }
        val httprequests = Vector(
          Record.data("X-Textus-Session" -> "auth-session"),
          Record.data("X-Cncf-Session" -> "auth-session"),
          Record.data("Cookie" -> "textus-session-businessspec=auth-session")
        ).map(header => _http_get("business-distinct", header))

        When("the caller authenticates through namespaced properties and actual HTTP session headers or cookie")
        val results = requests.map(fixture.subsystem.executeWithMetadata)
        val httpresults = httprequests.map(fixture.subsystem.executeHttp)

        Then("the provider receives the auth ID while operations retain the distinct business ID and provider security context")
        results.foreach(result => _accepted(result, "business-distinct", "provider-user", "provider-session"))
        httpresults.foreach(response => _http_accepted(response, "business-distinct", "provider-user", "provider-session"))
        provider.requests.size shouldBe 7
        provider.requests.foreach { request =>
          request.sessionId shouldBe Some("auth-session")
          request.attributes.keys.map(AuthenticationRequest.normalizeToken) should not contain "sessionid"
        }
        fixture.observation.requests.size shouldBe 7
        fixture.observation.requests.foreach(request => _business_value(request) shouldBe "business-distinct")
        val domainrequests = requests.zipWithIndex.map { (request, index) =>
          if (index == 1) request else request.copy(properties = request.properties.take(1))
        }
        fixture.observation.requests.take(4) shouldBe domainrequests
      }
    }

    "E11 admit only provider Service credentials beside business context in Fixed mode" must _metadata("E11", "R1,R2,R3,R4,R5") {
      "when service, user and unmatched tokens enter public Subsystem execution" in {
        Given("Spec: docs/spec/fixed-profile-service-ingress.md; Rules: R1,R2,R3,R4,R5; Example: E11; a Fixed component with a local subject, a declared business sessionId and a provider resolving service-token and user-token")
        val provider = new RecordingProvider
        val fixture = _fixture(providers = Vector(provider))
        val requests = Vector("service-token", "user-token", "unmatched-token").map { token =>
          _request(properties = List(Property("sessionId", "business-service", None), Property("access_token", token, None)))
        }

        When("the caller invokes the business operation through the public Request boundary with each credential")
        val results = requests.map(fixture.subsystem.executeWithMetadata)

        Then("only the Service is admitted with its provider principal and unchanged business ID; user and unmatched tokens fail")
        _accepted(results.head, "business-service", "provider-service")
        _rejected(results(1))
        results(1).display should include("service authentication subject")
        _rejected(results(2))
        results(2).display should include("did not authenticate the fixed service")
        provider.requests.map(_.accessToken) shouldBe Vector(Some("service-token"), Some("user-token"), Some("unmatched-token"))
        provider.requests.foreach { request =>
          request.attributes.keys.map(AuthenticationRequest.normalizeToken) should not contain "sessionid"
        }
        fixture.observation.requests shouldBe Vector(requests.head)
      }
    }
  }

  private def _accepted(
    result: Consequence[Subsystem#ExecutionResult],
    business: String,
    principal: String,
    session: String = ""
  ): Unit = {
    withClue(result.display) { result.isSuccess shouldBe true }
    result.toOption.get.response shouldBe OperationResponse.Scalar(_echo(business, principal, session))
  }

  private def _rejected(result: Consequence[Subsystem#ExecutionResult]): Unit =
    withClue(result.display) { result shouldBe a[Consequence.Failure[_]] }

  private def _http_accepted(response: HttpResponse, business: String, principal: String, session: String = ""): Unit = {
    withClue(response.getString.getOrElse("")) { response.code shouldBe 200 }
    response.getString.getOrElse("") should include(_echo(business, principal, session))
  }

  private def _fixture(authenticated: Boolean = false, providers: Vector[AuthenticationProvider] = Vector.empty): Fixture = {
    val observation = new Observation
    val subsystem = Subsystem(
      name = "business-session-ingress",
      scopeContext = Some(ScopeContext(
        kind = ScopeKind.Subsystem,
        name = "business-session-ingress",
        parent = None,
        observabilitycontext = ExecutionContext.create().observability
      )),
      configuration = ResolvedConfiguration(Configuration(Map(
        SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue(if (authenticated) "multi-user" else "standalone")
      )), ConfigurationTrace.empty)
    )
    val configured = subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = "business-session-ingress",
      componentBindings = Vector(GenericSubsystemComponentBinding(_component_name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"),
        fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding(
          id = "configured-local",
          roles = Vector("user"),
          capabilities = Vector("user"),
          securityLevel = Some("user")
        )),
        providers = providers.map { provider =>
          GenericSubsystemAuthenticationProviderBinding(name = provider.name, component = _component_name, enabled = Some(true))
        }
      ))))
    ))
    val component = new Component() {
      override def authenticationProviders: Vector[AuthenticationProvider] = providers
    }
    val operations = Vector(
      BusinessSessionOperation("propertyEcho", Some(_parameter("sessionId", spec.ParameterDefinition.Kind.Property)), observation),
      BusinessSessionOperation("argumentEcho", Some(_parameter("session_id", spec.ParameterDefinition.Kind.Argument)), observation),
      BusinessSessionOperation("undeclaredEcho", None, observation)
    )
    val service = spec.ServiceDefinition(name = "business", operations = spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations)))
    val componentid = ComponentId(_component_name)
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)), handler = ProtocolHandler.default)
    )
    configured.add(Vector(component.initialize(ComponentInit(configured, core, ComponentOrigin.Main))))
    Fixture(RuntimeBindingAdmissionFixture.admit(configured), observation)
  }

  private def _parameter(name: String, kind: spec.ParameterDefinition.Kind): spec.ParameterDefinition =
    spec.ParameterDefinition(content = BaseContent.simple(name), kind = kind,
      domain = ValueDomain(datatype = XString, multiplicity = Multiplicity.ZeroOne))

  private def _request(
    operation: String = "propertyEcho",
    properties: List[Property] = Nil,
    arguments: List[Argument] = Nil
  ): Request =
    Request.of(component = _component_name, service = "business", operation = operation)
      .copy(properties = properties, arguments = arguments)

  private def _http_get(business: String, header: Record = Record.empty): HttpRequest =
    HttpRequest.fromPath(method = HttpRequest.GET, path = _http_path,
      query = Record.data("sessionId" -> business), header = header)
}

private object SubsystemBusinessSessionIngressSpec {
  private val _component_name = "org.goldenport.cncf.test.BusinessSession"
  private val _http_path = s"/$_component_name/business/propertyEcho"

  private final case class Fixture(subsystem: Subsystem, observation: Observation)

  private final class Observation {
    var requests: Vector[Request] = Vector.empty
  }

  private final class RecordingProvider extends AuthenticationProvider {
    override val name: String = "business-session-provider"
    var requests: Vector[AuthenticationRequest] = Vector.empty

    override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] = {
      requests :+= request
      val result = if (request.sessionId.contains("auth-session")) {
        Some(AuthenticationResult(PrincipalId("provider-user"), session = Some(SessionContext(sessionId = Some("provider-session")))))
      } else request.accessToken match {
        case Some("service-token") => Some(AuthenticationResult(PrincipalId("provider-service"), subjectKind = SubjectKind.Service))
        case Some("user-token") => Some(AuthenticationResult(PrincipalId("provider-user")))
        case _ => None
      }
      Consequence.success(result)
    }
  }

  private final case class BusinessSessionOperation(
    opname: String,
    parameter: Option[spec.ParameterDefinition],
    observation: Observation
  ) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(parameters = parameter.toList),
      response = spec.ResponseDefinition(result = List(XString))
    )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(BusinessSessionAction(req, observation))
  }

  private final case class BusinessSessionAction(request: Request, observation: Observation) extends Action {
    override def createCall(core: ActionCall.Core): ActionCall = BusinessSessionActionCall(core, observation)
  }

  private final case class BusinessSessionActionCall(core: ActionCall.Core, observation: Observation) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      observation.requests :+= request
      val security = executionContext.security
      Consequence.success(OperationResponse.Scalar(_echo(
        _business_value(request), security.principal.id.value, security.session.flatMap(_.sessionId).getOrElse("")
      )))
    }
  }

  private def _business_value(request: Request): String =
    request.properties.find(property => AuthenticationRequest.normalizeToken(property.name) == "sessionid")
      .map(_.value.toString)
      .orElse(request.arguments.find(argument => AuthenticationRequest.normalizeToken(argument.name) == "sessionid").map(_.value.toString))
      .getOrElse("")

  private def _echo(business: String, principal: String, session: String = ""): String =
    s"business=$business;principal=$principal;auth=$session"
}
