package org.goldenport.cncf.display

import cats.data.NonEmptyVector
import java.nio.file.Path
import java.time.{Clock, Instant, ZoneOffset}
import java.util.Locale
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.*
import org.goldenport.cncf.context.*
import org.goldenport.cncf.operation.CmlOperationDefinition
import org.goldenport.cncf.operation.evaluation.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.http.{HttpContext, HttpPath, HttpRequest}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.{Field, Record}

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Companion-owned deterministic evidence; execution always uses the normal Subsystem. */
private[display] object PresentationContextRuntimeFixture {
  val instant: Instant = Instant.parse("2026-10-17T09:10:11.123456789Z")
  val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
  val emptyWire: String = "{\"version\":1,\"families\":[]}"
  val componentId: ComponentId = ComponentId("org.goldenport.cncf.test.PresentationRuntime")
  val instanceId: ComponentInstanceId = ComponentInstanceId.default(componentId)

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.display)
  }
  def flow(displayInstance: String = "server-display", correlation: String = "server-correlation",
    expiresAt: Instant = instant.plusSeconds(600)): PresentationContext.Flow =
    success(PresentationContext.Flow.createC(displayInstance, correlation, expiresAt))
  def wire(value: PresentationContext.Flow = flow(), variant: Option[String] = Some("client-variant"),
    locale: Option[String] = Some("ja-JP")): String =
    success(PresentationContextCodec.encodeC(success(PresentationContext.createC(locale, variant, Some(value)))))
  def property(value: Any): Property = Property("cncf.presentationContext", value, None)
  def withCandidate(request: Request, value: String = emptyWire): Request =
    request.copy(properties = request.properties :+ property(value))
  /** Models malformed JVM/provider interop; public safe factories cannot produce it. */
  def malformedResolution(binding: PresentationContextRuntime.Binding,
    variant: Option[String] = None, flow: Option[PresentationContext.Flow] = None): PresentationContextRuntime.Resolution = {
    val constructor = classOf[PresentationContextRuntime.Resolution].getDeclaredConstructors.head
    constructor.setAccessible(true)
    constructor.newInstance(binding, variant, flow).asInstanceOf[PresentationContextRuntime.Resolution]
  }
  def record(values: (String, Any)*): Record =
    Record(values.toVector.map { case (name, value) => Field(name, Field.Value.Single(value)) })
  def http(query: Record = Record.empty, form: Record = Record.empty, header: Record = Record.empty,
    uri: Option[String] = None): HttpRequest =
    HttpRequest(HttpPath.parse("/" + componentId.name + "/operation/evaluate.json"),
      HttpRequest.GET, query, form, record("X-Textus-Session" -> "session-one") ++ header,
      context = HttpContext(None, None, uri))

  def authenticated(ctx: ExecutionContext, principalId: String = "owner", sessionId: String = "session-one",
    subjectKind: SubjectKind = SubjectKind.User, expiresAt: Option[Instant] = None): ExecutionContext = {
    val security = SecurityContext(new Principal {
      val id: PrincipalId = PrincipalId(principalId)
      val attributes: Map[String, String] = Map("authenticated" -> "true", "access_token" -> "fixture")
    }, Set.empty, SecurityLevel("user"), subjectKind, Some(SessionContext(Some(sessionId), expiresAt = expiresAt)))
    val instance = ctx.asInstanceOf[ExecutionContext.Instance]
    instance.copy(core = instance.core.copy(clock = clock, locale = Locale.US),
      cncfCore = instance.cncfCore.copy(security = security))
  }

  def context(provider: PresentationContextResolver = PresentationContextResolver.disabled,
    identity: ComponentInstanceId = instanceId): ExecutionContext = {
    val component = new Component() {}
    val core = Component.Core.create(identity.componentId.name, identity.componentId, identity, Protocol.empty)
    val subsystem = new Subsystem("presentation-value", configuration =
      ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty), aliasResolver = AliasResolver.empty)
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Embed))
    val scope = Component.Context("presentation", ExecutionContext.create().scope, component, ComponentOrigin.Embed)
    authenticated(ExecutionContext.create()).withScope(ScopeContext.withPresentationContextResolver(scope, provider))
  }

  final class Provider(val events: ArrayBuffer[String] = ArrayBuffer.empty) extends PresentationContextResolver {
    val requests: ArrayBuffer[PresentationContextResolver.Request] = ArrayBuffer.empty
    val contexts: ArrayBuffer[ExecutionContext] = ArrayBuffer.empty
    var answer: (PresentationContextResolver.Request, ExecutionContext) => Consequence[PresentationContextResolver.Result] =
      (request, _) => {
        val references = request.continuation
        val value = flow(references.fold("server-display")(_.displayInstance),
          references.fold("server-correlation")(_.correlation), request.now.plusSeconds(600))
        Consequence.success(PresentationContextResolver.Result.Supported(success(
          PresentationContextRuntime.Resolution.createC(request.binding, Some("server-variant"), Some(value)))))
      }
    def resolve(request: PresentationContextResolver.Request)(using ctx: ExecutionContext):
      Consequence[PresentationContextResolver.Result] = {
      events += "provider"
      requests += request
      contexts += ctx
      answer(request, ctx)
    }
  }

  final class Evidence {
    val events: ArrayBuffer[String] = ArrayBuffer.empty
    val business: ArrayBuffer[ExecutionContext] = ArrayBuffer.empty
    val actions: ArrayBuffer[Action] = ArrayBuffer.empty
    val requests: ArrayBuffer[Request] = ArrayBuffer.empty
    val evaluations: ArrayBuffer[ExecutionContext] = ArrayBuffer.empty
    var nested: Option[ExecutionContext => Unit] = None
  }

  final class ProbeOperation(private val _evidence: Evidence, private val _denied: Boolean)
      extends spec.OperationDefinition with OperationAuthorizationProvider {
    val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = "evaluate", request = spec.RequestDefinition(), response = spec.ResponseDefinition.void)
    def operationAuthorization(policy: org.goldenport.cncf.config.RuntimeOperationSecurityPolicy): OperationAuthorizationRule = {
      _evidence.events += "authorize"
      OperationAuthorizationRule(deny = _denied)
    }
    def createOperationRequest(request: Request): Consequence[OperationRequest] = {
      _evidence.events += "request"
      _evidence.requests += request
      Consequence.success(new ProbeAction(request, _evidence))
    }
  }
  final class ProbeAction(val request: Request, private val _evidence: Evidence) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = {
      _evidence.actions += this
      new ProbeCall(core, _evidence)
    }
  }
  final class ProbeCall(val core: ActionCall.Core, private val _evidence: Evidence) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      _evidence.events += "business"
      _evidence.business += executionContext
      val nested = _evidence.nested
      _evidence.nested = None
      nested.foreach(_(executionContext))
      Consequence.success(OperationResponse.Scalar("ordinary"))
    }
  }
  final class ProbeComponent(private val _definitions: Vector[CmlOperationDefinition]) extends Component {
    override def operationDefinitions: Vector[CmlOperationDefinition] = _definitions
    override def authenticationProviders: Vector[AuthenticationProvider] = Vector(new AuthenticationProvider {
      val name: String = "presentation-test-auth"
      def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        Consequence.success(Some(AuthenticationResult(PrincipalId("owner"),
          session = Some(SessionContext(Some("session-one"))))))
    })
  }
  final class Fixture(val subsystem: Subsystem, val component: ProbeComponent,
    val provider: Provider, val evidence: Evidence) {
    def request: Request = Request.of(component.componentId.name, "operation", "evaluate")
    def context: ExecutionContext = authenticated(component.logic.executionContext())
    def action(request: Request): Action = new ProbeAction(request, evidence)
    def close(): Unit = Subsystem.shutdownOwned(subsystem)
  }

  def open(declared: Boolean = true, required: Boolean = false, denied: Boolean = false,
    evaluationAvailable: Boolean = true, componentName: String = componentId.name): Fixture = {
    val evidence = new Evidence
    val provider = new Provider(evidence.events)
    val evaluation = new OperationEvaluationResolver {
      def resolve(request: OperationEvaluationAdmissionRequest)(using ctx: ExecutionContext): Consequence[OperationEvaluationAdmission] = {
        evidence.events += "evaluation"
        evidence.evaluations += ctx
        if (!evaluationAvailable) Consequence.success(OperationEvaluationAdmission.unavailable())
        else {
          val correlation = success(ExperimentEvaluationCorrelation.createC(
            success(ExperimentReference.parseC("experiment")), Some(success(ExperimentArmReference.parseC("arm"))),
            Some(success(ExperimentRunReference.parseC("run"))), Some(success(CorpusRevisionReference.parseC("revision")))))
          val assignment = OperationEvaluationAssignment(success(OperationEvaluationName.parseC("admitted")))
          OperationEvaluationAdmission.admittedC(experiment = Some(correlation), assignment = Some(assignment))
        }
      }
    }
    val scope = ScopeContext(ScopeKind.Subsystem, "presentation-runtime", None, ExecutionContext.create().observability,
      operationevaluationresolveroption = Some(evaluation), presentationContextResolverOption = Some(provider))
    val configuration = ResolvedConfiguration(Configuration(Map(
      "textus.operation-mode" -> ConfigurationValue.StringValue("production"),
      "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("multi-user"))), ConfigurationTrace.empty)
    val subsystem = new Subsystem("presentation-runtime", scopeContext = Some(scope), configuration = configuration,
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command)
    val definition = CmlOperationDefinition(name = "evaluate", kind = "QUERY", inputType = "record", outputType = "record",
      inputValueKind = "record", evaluation = if (!declared) None else Some(CmlOperationEvaluationDeclaration(
        experiment = Some(CmlExperimentEvaluationDeclaration(eligible = true,
          purpose = success(OperationEvaluationName.parseC("presentation")),
          admission = if (required) EvaluationAdmissionRequirement.Required else EvaluationAdmissionRequirement.Optional)))))
    val component = new ProbeComponent(Vector(definition))
    val identity = ComponentId(componentName)
    val protocol = Protocol(spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition("operation",
      spec.OperationDefinitionGroup(NonEmptyVector.of(new ProbeOperation(evidence, denied)))))), ProtocolHandler.default)
    component.initialize(ComponentInit(subsystem,
      Component.Core.create(identity.name, identity, ComponentInstanceId.default(identity), protocol), ComponentOrigin.Main))
    subsystem.add(component)
    subsystem.withDescriptor(GenericSubsystemDescriptor(Path.of("<presentation-runtime>"), subsystemName = subsystem.name,
      componentBindings = Vector(GenericSubsystemComponentBinding(component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"), providers = Vector(
          GenericSubsystemAuthenticationProviderBinding("presentation-test-auth", component.name, enabled = Some(true))))))),
      operationAuthorization = Map((component.name + ".operation.evaluate") -> OperationAuthorizationRule(deny = denied))))
    RuntimeBindingAdmissionFixture.admit(subsystem)
    new Fixture(subsystem, component, provider, evidence)
  }
}
