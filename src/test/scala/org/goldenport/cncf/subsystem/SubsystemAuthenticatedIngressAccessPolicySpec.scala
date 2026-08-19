package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext, ScopeKind}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Protocol, Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.value.BaseContent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  6, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemAuthenticatedIngressAccessPolicySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-pm-53-01-subsystem-user-mode, example:$example, rules:$rules, phase:53, slice:PM-53-01")

  "Subsystem authenticated ingress access policy" should {
    "E1 admit an unauthenticated public operation into normal execution" must _metadata("E1", "PM53-R5") {
    "when an authenticated-profile Subsystem receives a public operation request" in {
      Given("Spec: phase-53-pm-53-01-subsystem-user-mode; Rules: PM53-R5; Example: E1; an authenticated-profile Subsystem with a public operation declaration")
      val subsystem = _subsystem()

      When("an unauthenticated request invokes the public operation")
      val result = subsystem.executeWithMetadata(_request("publicPing"))

      Then("provider-aware ingress permits normal operation execution")
      result shouldBe a[Consequence.Success[_]]
    }
    }

    "E2 admit an unauthenticated anonymous-only operation into normal execution" must _metadata("E2", "PM53-R6") {
    "when an authenticated-profile Subsystem receives an anonymous-only operation request" in {
      Given("Spec: phase-53-pm-53-01-subsystem-user-mode; Rules: PM53-R6; Example: E2; an authenticated-profile Subsystem with an anonymous-only operation declaration")
      val subsystem = _subsystem()

      When("an unauthenticated request invokes the anonymous-only operation")
      val result = subsystem.executeWithMetadata(_request("anonymousRegister"))

      Then("anonymous ingress reaches the existing anonymous-only authorization")
      result shouldBe a[Consequence.Success[_]]
    }
    }

    "E3 leave anonymous-only authorization to reject authenticated callers downstream" must _metadata("E3", "PM53-R7") {
    "when an authenticated caller invokes an anonymous-only operation" in {
      Given("Spec: phase-53-pm-53-01-subsystem-user-mode; Rules: PM53-R7; Example: E3; an authenticated-profile Subsystem whose provider accepts one access token")
      val subsystem = _subsystem()

      When("the authenticated caller invokes the anonymous-only operation")
      val result = subsystem.executeWithMetadata(_request(
        "anonymousRegister",
        List(Property("access_token", "authenticated-token", None))
      ))

      Then("the routed call reaches and is rejected by anonymous-only authorization")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include("Anonymous user is required")
    }
    }

    "E4 retain strict authenticated ingress for protected and undeclared operations" must _metadata("E4", "PM53-R8") {
    "when unauthenticated requests invoke protected and undeclared operations" in {
      Given("Spec: phase-53-pm-53-01-subsystem-user-mode; Rules: PM53-R8; Example: E4; an authenticated-profile Subsystem with protected and undeclared operation declarations")
      val subsystem = _subsystem()

      When("unauthenticated requests invoke those operations")
      val protectedresult = subsystem.executeWithMetadata(_request("authenticatedOnly"))
      val undeclaredresult = subsystem.executeWithMetadata(_request("undeclared"))

      Then("both requests fail at the strict authenticated-profile ingress boundary")
      Vector(protectedresult, undeclaredresult).foreach { result =>
        result shouldBe a[Consequence.Failure[_]]
        result.display should include("Authenticated user profile requires authentication ingress evidence.")
      }
    }
    }
  }

  private def _subsystem(): Subsystem = {
    val subsystem = Subsystem(
      name = "authenticated-ingress-access-policy",
      scopeContext = Some(
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = "authenticated-ingress-access-policy",
          parent = None,
          observabilitycontext = ExecutionContext.create().observability
        )
      ),
      configuration = ResolvedConfiguration(
        Configuration(Map(
          SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user")
        )),
        ConfigurationTrace.empty
      )
    )
    val configured = subsystem.withDescriptor(
      GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "authenticated-ingress-access-policy",
        componentBindings = Vector(GenericSubsystemComponentBinding("org.goldenport.cncf.test.Domain")),
        security = Some(
          GenericSubsystemSecurityBinding(
            authentication = Some(
              GenericSubsystemAuthenticationBinding(
                convention = Some("enabled"),
                fallbackPrivilege = Some("disabled"),
                providers = Vector(
                  GenericSubsystemAuthenticationProviderBinding(
                    name = _authentication_provider.name,
                    component = "org.goldenport.cncf.test.Domain",
                    enabled = Some(true)
                  )
                )
              )
            )
          )
        )
      )
    )
    configured.add(Vector(_component(configured)))
    RuntimeBindingAdmissionFixture.admit(configured)
  }

  private def _component(subsystem: Subsystem): Component = {
    val operations = Vector(
      IngressAccessOperation("publicPing", "public"),
      IngressAccessOperation("anonymousRegister", "anonymous-only"),
      IngressAccessOperation("authenticatedOnly", "authenticated_only"),
      IngressAccessOperation("undeclared", "")
    )
    val service = spec.ServiceDefinition(
      name = "account",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations))
    )
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val component = new Component() {
      override def operationDefinitions: Vector[CmlOperationDefinition] =
        Vector(
          _operation_definition("publicPing", Some(" public ")),
          _operation_definition("anonymousRegister", Some("anonymous-only")),
          _operation_definition("authenticatedOnly", Some("authenticated_only")),
          _operation_definition("undeclared", None)
        )

      override def authenticationProviders: Vector[AuthenticationProvider] =
        Vector(_authentication_provider)
    }
    val componentid = ComponentId("org.goldenport.cncf.test.Domain")
    val core = Component.Core.create(
      name = componentid.name,
      componentId = componentid,
      instanceId = ComponentInstanceId.default(componentid),
      protocol = protocol
    )
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Main))
  }

  private def _operation_definition(
    name: String,
    policy: Option[String]
  ): CmlOperationDefinition =
    CmlOperationDefinition(
      name = name,
      kind = "COMMAND",
      inputType = "IngressRequest",
      outputType = "IngressResponse",
      inputValueKind = "COMMAND_VALUE",
      access = policy.map(CmlOperationAccess(_))
    )

  private def _request(operation: String, properties: List[Property] = Nil): Request =
    Request.of(component = "org.goldenport.cncf.test.Domain", service = "account", operation = operation).copy(
      properties = properties
    )

  private val _authentication_provider: AuthenticationProvider = new AuthenticationProvider {
    override val name: String = "ingress-provider"

    def authenticate(request: AuthenticationRequest)(using
        ExecutionContext
    ): Consequence[Option[AuthenticationResult]] =
      if (request.accessToken.contains("authenticated-token"))
        Consequence.success(Some(AuthenticationResult(PrincipalId("authenticated-user"))))
      else
        Consequence.success(None)
  }

  private final case class IngressAccessOperation(opname: String, responsebody: String)
    extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = opname,
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(IngressAccessAction(req, responsebody))
  }

  private final case class IngressAccessAction(request: Request, responsebody: String) extends Action {
    override def createCall(core: ActionCall.Core): ActionCall =
      IngressAccessActionCall(core, responsebody)
  }

  private final case class IngressAccessActionCall(core: ActionCall.Core, responsebody: String)
    extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(responsebody))
  }
}
