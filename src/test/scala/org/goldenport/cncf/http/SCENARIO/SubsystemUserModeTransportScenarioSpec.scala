package org.goldenport.cncf.http.SCENARIO

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import cats.data.NonEmptyVector
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.goldenport.Consequence
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.http.{Http4sHttpServer, HttpExecutionEngine}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.http4s.{Header, Method, Request as HRequest, Uri}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * Command, REST, and Web identify the owning Subsystem before admission.
 * This is an end-to-end transport matrix: each ingress invokes the same
 * Component operation, which returns its received ExecutionContext principal.
 *
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemUserModeTransportScenarioSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-pm-53-01-subsystem-user-mode-transport, example:$example, rules:$rules, phase:53, slice:PM-53-01")

  "Subsystem user-mode transport admission" should {
    "E4 admit a fixed or authenticated principal before Command, REST, and Web invoke their Components" must _metadata("E4", "PM53-R4") {
    "when operation mode, Subsystem user mode, and transport vary independently" in {
      Given("spec:phase-53-pm-53-01-subsystem-user-mode-transport, example:E4, rules:PM53-R4")
      val operationmodes = Vector(OperationMode.Develop, OperationMode.Production)
      val usermodes = Vector(SubsystemUserMode.Standalone, SubsystemUserMode.MultiUser)
      val transports = Vector("command", "rest", "web")

      When("each ingress resolves the owning Subsystem's canonical user mode")
      val cells = for {
        operationmode <- operationmodes
        usermode <- usermodes
        transport <- transports
      } yield (operationmode, usermode, transport, _invoke(operationmode, usermode, transport))

      Then("OperationMode and transport do not replace fixed or authenticated Subsystem admission")
      cells.size shouldBe 12
      cells.foreach { case (_, usermode, transport, observed) =>
        withClue(s"mode=${usermode.name}, transport=$transport: ") {
          withClue(s"body=${observed.body}: ") { observed.status shouldBe 200 }
          observed.body should include (if (usermode == SubsystemUserMode.Standalone) "transport-fixed" else "transport-user")
        }
      }
    }
    }
  }

  private def _invoke(
    operationmode: OperationMode,
    mode: SubsystemUserMode,
    transport: String
  ): Observed = {
    val fixture = _fixture(operationmode, mode)
    transport match {
      case "command" =>
        val args =
          if (mode == SubsystemUserMode.MultiUser)
            Array("probe.identity.whoami", "--access_token", "mode-token")
          else
            Array("probe.identity.whoami")
        val response = new CncfRuntime().executeCommandResponse(fixture.subsystem, args).toOption.getOrElse(fail("command execution failed"))
        Observed(200, response.print)
      case "rest" =>
        val server = new Http4sHttpServer(new HttpExecutionEngine(fixture.subsystem))
        val request = _authenticated(HRequest[IO](
          method = Method.GET,
          uri = Uri.unsafeFromString("/rest/v1/probe/identity/whoami")
        ), mode)
        val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
        val body = response.as[String].unsafeRunSync()
        Observed(response.status.code, body)
      case "web" =>
        val root = Files.createTempDirectory("subsystem-user-mode-web-")
        try {
          Files.createDirectories(root.resolve("debug-app"))
          Files.writeString(
            root.resolve("web.yaml"),
            """expose:
              |  probe.identity.whoami: public
              |form:
              |  probe.identity.whoami:
              |    enabled: true
              |""".stripMargin,
            StandardCharsets.UTF_8
          )
          val webfixture = _fixture(operationmode, mode, Some(root.resolve("web.yaml").toString))
          val server = new Http4sHttpServer(new HttpExecutionEngine(webfixture.subsystem))
          val request = _authenticated(HRequest[IO](
            method = Method.POST,
            uri = Uri.unsafeFromString("/form-api/probe/identity/whoami")
          ), mode)
          val response = server.routes(null).orNotFound.run(request).unsafeRunSync()
          val body = response.as[String].unsafeRunSync()
          Observed(response.status.code, body)
        } finally {
          _delete_tree(root)
        }
      case other => fail(s"unexpected transport: $other")
    }
  }

  private def _authenticated(request: HRequest[IO], mode: SubsystemUserMode): HRequest[IO] =
    if (mode == SubsystemUserMode.MultiUser)
      request.putHeaders(Header.Raw(CIString("Authorization"), "Bearer mode-token"))
    else
      request

  private def _fixture(
    operationmode: OperationMode,
    mode: SubsystemUserMode,
    webdescriptor: Option[String] = None
  ): Fixture = {
    val values = Map.newBuilder[String, ConfigurationValue]
    values += RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(operationmode.name)
    values += SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue(mode.name)
    webdescriptor.foreach(path => values += RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(path))
    val subsystem = DefaultSubsystemFactory.default(Some("server"), ResolvedConfiguration(
      Configuration(values.result()),
      ConfigurationTrace.empty
    ))
    subsystem.add(_authentication_component(subsystem))
    val authentication = mode match {
      case SubsystemUserMode.Standalone => GenericSubsystemAuthenticationBinding(
        convention = Some("disabled"),
        fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding("transport-fixed")),
        providers = Vector(GenericSubsystemAuthenticationProviderBinding(
          name = "transport-provider",
          component = "transport-authentication",
          enabled = Some(true)
        ))
      )
      case SubsystemUserMode.MultiUser =>
        GenericSubsystemAuthenticationBinding(
          convention = Some("disabled"),
          fallbackPrivilege = Some("disabled"),
          providers = Vector(GenericSubsystemAuthenticationProviderBinding(
            name = "transport-provider",
            component = "transport-authentication",
            enabled = Some(true)
          ))
        )
    }
    subsystem.add(_probe_component(subsystem))
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("build.sbt").toAbsolutePath,
      subsystemName = mode.name,
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(authentication)))
    ))
    Fixture(subsystem)
  }

  private def _authentication_component(owner: Subsystem): Component = {
    val component = new Component {
      override val core: Component.Core = Component.Core.create(
        "TransportAuthentication",
        ComponentId("transport_authentication"),
        ComponentInstanceId.default(ComponentId("transport_authentication")),
        Protocol.empty
      )
      override def subsystem: Option[Subsystem] = Some(owner)
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(new AuthenticationProvider {
        override val name: String = "transport-provider"
        override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
          request.accessToken match {
            case Some("mode-token") =>
              Consequence.success(Some(AuthenticationResult(PrincipalId("transport-user"))))
            case _ => Consequence.success(None)
          }
      })
    }
    component.withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = "TransportAuthentication",
      version = "0.0.0",
      component = Some("transport-authentication")
    ))
  }

  private def _probe_component(owner: Subsystem): Component = {
    val id = ComponentId("probe")
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(Vector(
        spec.ServiceDefinition(
          name = "identity",
          operations = spec.OperationDefinitionGroup(NonEmptyVector.of(PrincipalOperation()))
        )
      )),
      handler = ProtocolHandler.default
    )
    new Component {}.initialize(ComponentInit(
      owner,
      Component.Core.create("probe", id, ComponentInstanceId.default(id), protocol),
      ComponentOrigin.Main
    ))
  }

  private def _delete_tree(root: java.nio.file.Path): Unit =
    Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))

  private final case class Fixture(subsystem: Subsystem)

  private final case class Observed(status: Int, body: String)

  private final case class PrincipalOperation() extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "whoami",
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(PrincipalAction(req))
  }

  private final case class PrincipalAction(request: Request) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall = PrincipalActionCall(core)
  }

  private final case class PrincipalActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(core.executionContext.security.principal.id.value))
  }
}
