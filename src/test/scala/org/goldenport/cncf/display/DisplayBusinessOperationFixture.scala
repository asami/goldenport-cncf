package org.goldenport.cncf.display

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture, TypedDisplayRuntimeFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId}
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult, OperationAuthorizationRule}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem, SubsystemUserMode}
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, TestComponentFactory}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, OperationDefinitionGroup, ParameterDefinition, RequestDefinition, ResponseDefinition, ServiceDefinition, ServiceDefinitionGroup}
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XBoolean, XString}
import org.goldenport.value.BaseContent
import org.http4s.{Method, Request as HttpRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
/** Development-only metadata consumers. No Business mutation/reload acceptance is claimed. */
object DisplayBusinessOperationFixture {
  import DisplayBusinessOperation.*
  import GeneratedLogicalUiAbi.*
  val resourceType: String = "collecting-reference"
  val authenticatedSessionId: String = "business-descriptor-session"
  private val _authentication_provider: AuthenticationProvider = new AuthenticationProvider {
    override val name: String = "business-descriptor-auth"
    def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
      if (request.sessionId.contains(authenticatedSessionId))
        Consequence.success(Some(AuthenticationResult(PrincipalId("business-descriptor-user"))))
      else
        Consequence.success(None)
  }
  val resources: Vector[DisplayService.ResourceBinding] = DisplayServiceReadFixture.resourceBindings
  val parameters: List[ParameterDefinition] = List(
    ParameterDefinition(BaseContent.simple("resource"), ParameterDefinition.Kind.Argument, ValueDomain(XString, Multiplicity.One)),
    ParameterDefinition(BaseContent.simple("comment"), ParameterDefinition.Kind.Property, ValueDomain(XString, Multiplicity.ZeroOne),
      default = ParameterDefinition.Default.Value("private-default")),
    ParameterDefinition(BaseContent.simple("notify"), ParameterDefinition.Kind.Switch, ValueDomain(XBoolean, Multiplicity.ZeroMore)))
  val expectedParameters: Vector[InputParameter] = Vector(
    InputParameter("resource", ParameterDefinition.Kind.Argument, "string", "1"),
    InputParameter("comment", ParameterDefinition.Kind.Property, "string", "?"),
    InputParameter("notify", ParameterDefinition.Kind.Switch, "boolean", "*"))

  def definitions(size: Int = 2, suffix: String = "日本 Ω"): Vector[Definition] = {
    val original = TypedDisplayProjectionFixture.definitions
    val actions = Vector.tabulate(size)(index => ActionDefinition("review:" + index, "Review " + suffix + index, "business:shared"))
    Vector(original.head.copy(screen = original.head.screen.asInstanceOf[ListDefinition].copy(
      actions = actions, itemActions = actions)), original(1))
  }
  def bindings(size: Int = 2): Vector[Binding] =
    Vector.tabulate(size)(index => Binding("collecting-resources", ActionScope.Screen, "review:" + index, "business", "review")) ++
      Vector.tabulate(size)(index => Binding("collecting-resources", ActionScope.Item, "review:" + index, "business", "review")) ++
      Vector(Binding("capture-detail", ActionScope.Screen, "request-review", "business", "review"))

  final class TargetOperation(parameterDefinitions: List[ParameterDefinition] = parameters, operationName: String = "review")
      extends OperationDefinition {
    private var _requests = 0
    private var _calls = 0
    private var _executions = 0
    def requests: Int = _requests
    def calls: Int = _calls
    def executions: Int = _executions
    val specification: OperationDefinition.Specification = OperationDefinition.Specification(name = operationName,
      request = RequestDefinition(parameterDefinitions), response = ResponseDefinition(result = List(DataType.Named("OperationResponse"))))
    def createOperationRequest(request: Request): Consequence[OperationRequest] = {
      _requests += 1
      val original = request
      Consequence.success(new QueryAction {
        val request: Request = original
        def createCall(core: ActionCall.Core): ActionCall = {
          _calls += 1
          val originalcore = core
          new ProcedureActionCall {
            val core: ActionCall.Core = originalcore
            def execute(): Consequence[OperationResponse] = {
              _executions += 1
              Consequence.success(OperationResponse.Json(io.circe.Json.obj("business" -> io.circe.Json.True)))
            }
          }
        }
      })
    }
  }

  def protocol(operations: Vector[OperationDefinition], serviceName: String = "business"): Protocol =
    Protocol(ServiceDefinitionGroup(Vector(ServiceDefinition(name = serviceName,
      operations = OperationDefinitionGroup.create(operations)))), ProtocolHandler.default)

  class SourceComponent(declarations: Vector[Definition] = definitions()) extends Component()
      with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
    private val _definitions = declarations
    val row: TypedDisplayProjectionFixture.Capture = TypedDisplayProjectionFixture.captureC(1, 7).TAKE
    val browser: TypedDisplayRuntimeFixture.SourceBrowser[TypedDisplayProjectionFixture.Capture] =
      TypedDisplayRuntimeFixture.captureBrowser(Vector(row))
    def generatedLogicalUiDefinitions: Vector[Definition] = _definitions
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = resources
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      TypedDisplayRuntimeFixture.register(component.viewSpace, "business-source", browser)
      val list = _definitions.head.screen.asInstanceOf[ListDefinition]
      val detail = _definitions(1).screen.asInstanceOf[DetailDefinition]
      val itemrow = TypedDisplayProjectionFixture.listRow.copy(actions = list.itemActions.map(_.bindingId).distinct.map(binding =>
        DisplayProjection.ActionBinding[TypedDisplayProjectionFixture.Capture](binding,
          _ => Consequence.success(DisplayModel.ActionAvailability.Enabled))))
      val detailrow = TypedDisplayProjectionFixture.detailRow.copy(actions = detail.actions.map(_.bindingId).distinct.map(binding =>
        DisplayProjection.ActionBinding[TypedDisplayProjectionFixture.Capture](binding,
          _ => Consequence.success(DisplayModel.ActionAvailability.Enabled))))
      Vector(DisplayRuntime.list("collecting-resources", "ess.reference", browser, itemrow,
        list.actions.map(_.bindingId).distinct.map(binding => DisplayProjection.ActionBinding[Vector[TypedDisplayProjectionFixture.Capture]](
          binding, _ => Consequence.success(DisplayModel.ActionAvailability.Enabled)))),
        DisplayRuntime.detail("capture-detail", "ess.reference", browser, detailrow))
    }
  }
  final class BusinessComponent(readBindings: Component => Vector[Binding] = _ => bindings(),
    declarations: Vector[Definition] = definitions(), authenticationEnabled: Boolean = false)
      extends SourceComponent(declarations) with DisplayBusinessOperationBindingProvider {
    private val _read_bindings = readBindings
    private val _authentication_enabled = authenticationEnabled
    private var _reads = 0
    override def authenticationProviders: Vector[AuthenticationProvider] =
      if (_authentication_enabled) Vector(_authentication_provider) else Vector.empty
    def reads: Int = _reads
    def displayBusinessOperationBindings(component: Component): Vector[Binding] = {
      _reads += 1
      _read_bindings(component)
    }
  }
  final class BindingFactory(readBindings: Component => Vector[Binding]) extends Component.Factory with DisplayBusinessOperationBindingProvider {
    private val _read_bindings = readBindings
    private var _reads = 0
    def reads: Int = _reads
    def displayBusinessOperationBindings(component: Component): Vector[Binding] = { _reads += 1; _read_bindings(component) }
    override protected def create_Component(params: ComponentCreate): Component = throw new UnsupportedOperationException("fixture initialization only")
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = throw new UnsupportedOperationException("fixture initialization only")
  }
  def initialize(component: Component, subsystem: Subsystem, original: Protocol,
    factory: Option[Component.Factory] = None, name: String = "BusinessDescriptor"): Unit = {
    DisplayServiceReadFixture.initialize(component, subsystem, name = name, protocol = original, factory = factory)
    ()
  }
  def withSubsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("business-descriptor-admission")
    try body(subsystem) finally subsystem.shutdown()
  }
  def registry(component: Component, declarations: Vector[Definition] = definitions(),
    selected: Vector[Binding] = bindings()): Consequence[Registry] =
    DisplayBusinessOperation._bind_c(component, GeneratedLogicalUiAbi.admitC(declarations).TAKE, resources, selected)
  def operation(component: Component): OperationDefinition =
    component.protocol.services.services.find(_.name == "display").get.operations.operations.toVector
      .find(_.name == "describeBusiness").get
  def request(component: Component, screenId: String = "capture-detail"): Request =
    Request.of(component = component.name, service = "display", operation = "describeBusiness",
      properties = List(Property("resourceType", resourceType, None), Property("screenId", screenId, None)))
  def json(response: Consequence[OperationResponse]): io.circe.Json = response.TAKE match {
    case OperationResponse.Json(value) => value
    case _ => throw new IllegalStateException("expected JSON metadata")
  }

  final case class Session(subsystem: Subsystem, component: BusinessComponent, target: TargetOperation) {
    def execute(request: Request): Consequence[OperationResponse] = subsystem.executeOperationResponse(request)
    def http(screenId: String = "capture-detail", extra: Vector[(String, String)] = Vector.empty): DisplayServiceReadFixture.HttpResult = {
      val pairs = Vector("resourceType" -> resourceType, "screenId" -> screenId, "textus.output.shape" -> "envelope") ++ extra
      val uri = Uri.unsafeFromString(s"/rest/v1/${component.name}/display/describeBusiness.json")
        .withMultiValueQueryParams(pairs.groupMap(_._1)(_._2))
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val response = DisplayServiceHttpRouteFixture.app(server).run(HttpRequest[IO](Method.GET, uri)).unsafeRunSync()
      val bytes = response.body.compile.toVector.unsafeRunSync()
      DisplayServiceReadFixture.HttpResult(response.status.code,
        response.headers.get[`Content-Type`].map(value => s"${value.mediaType.mainType}/${value.mediaType.subType}"),
        new String(bytes.toArray, StandardCharsets.UTF_8), bytes)
    }
    def close(): Unit = subsystem.shutdown()
  }
  def open(production: Boolean = false, authenticated: Boolean = false): Session = {
    val configuration = Map(RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(
      if (production) OperationMode.Production.name else OperationMode.Test.name)) ++
      (if (authenticated) Map(SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")) else Map.empty)
    val subsystem = RuntimeBindingAdmissionFixture.admit(TestComponentFactory.subsystemWithConfig(
      configuration, name = "business-descriptor"))
    val component = new BusinessComponent(authenticationEnabled = authenticated)
    val target = new TargetOperation()
    initialize(component, subsystem, protocol(Vector(target)))
    new ComponentFactory().bootstrapC(component).TAKE
    if (production) {
      subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<business-descriptor-fixture>"),
        subsystemName = subsystem.name,
        componentBindings = if (authenticated) Vector(GenericSubsystemComponentBinding(component.name)) else Vector.empty,
        security = if (authenticated) Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(
            convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
            providers = Vector(GenericSubsystemAuthenticationProviderBinding(
              name = _authentication_provider.name, component = component.name, enabled = Some(true))))))) else None,
        operationAuthorization = Map(
          (component.name + ".display.describeBusiness") -> OperationAuthorizationRule(requireAuthenticated = true))))
    }
    subsystem.add(Vector(component))
    if (production) RuntimeBindingAdmissionFixture.admit(subsystem)
    Session(subsystem, component, target)
  }

  trait Vocabulary extends Matchers with GivenWhenThen { self: org.scalatest.Informing =>
    def expectFailure(result: Consequence[?], code: String, path: String): Unit = result match {
      case failure: Consequence.Failure[?] =>
        failure.conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.argumentInvalid
        failure.conclusion.observation.cause.getException match {
          case Some(carrier: DescriptorException) =>
            carrier.diagnostic.code shouldBe "DSP96_BUSINESS_" + code
            carrier.diagnostic.path shouldBe path
            carrier.diagnostic.reason should not be empty
          case _ => fail("expected typed Business descriptor diagnostic")
        }
      case _ => fail("expected rejection")
    }
    def idle(component: SourceComponent, target: TargetOperation): Unit = {
      target.requests shouldBe 0
      target.calls shouldBe 0
      target.executions shouldBe 0
      component.browser.findCalls shouldBe empty
      component.browser.queryCalls shouldBe empty
      component.browser.contextFreeCalls shouldBe 0
      component.browser.countCalls shouldBe 0
    }
  }
}
