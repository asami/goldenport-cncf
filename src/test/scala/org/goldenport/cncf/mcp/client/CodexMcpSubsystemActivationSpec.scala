package org.goldenport.cncf.mcp.client

import java.nio.file.{Files, Path}

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.context.{ScopeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.GenericSubsystemFactory
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for subsystem-owned MCP client activation.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class CodexMcpSubsystemActivationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Subsystem MCP client activation" should {
    "activate configured policy during generic subsystem startup" in {
      Given("a generic subsystem runtime configuration selecting an operator policy")
      val policy = _policy_file()
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          RuntimeConfig.MCP_CLIENT_POLICY_KEY -> ConfigurationValue.StringValue(policy.toString)
        )),
        ConfigurationTrace.empty
      )
      val context = ScopeContext(
        kind = ScopeKind.Subsystem,
        name = "mcp_runtime_factory",
        parent = None,
        observabilityContext = ExecutionContext.create().observability
      )

      When("the generic subsystem factory completes component assembly")
      val subsystem = GenericSubsystemFactory.defaultWithScope(
        subsystemName = "mcp_runtime_factory",
        context = context,
        mode = Some(RunMode.Command),
        configuration = configuration,
        aliasResolver = AliasResolver.empty
      )

      Then("the subsystem owns the admitted MCP runtime until shutdown")
      subsystem.mcpClientServerSetIds.map(_.print) shouldBe Vector("research")
      subsystem.shutdownC().isSuccess shouldBe true
    }

    "install admitted services into existing and subsequently added consumer sockets" in {
      Given("an operator policy, one existing consumer, and one later consumer")
      given ExecutionContext = ExecutionContext.create()
      val policy = _policy_file()
      val serverSetId = McpServerSetId.parseC("research").toOption.get
      val firstSocket = _socket(serverSetId)
      val secondSocket = _socket(serverSetId)
      val subsystem = TestComponentFactory.emptySubsystem("mcp-client-activation")
      subsystem.add(_component("first_consumer", firstSocket))

      When("the subsystem activates the policy and later adds another component")
      val activation = subsystem.activateCodexMcpClientRuntimeC(policy)
      subsystem.add(_component("second_consumer", secondSocket))

      Then("both consumer-owned sockets receive the same normalized service")
      activation.isSuccess shouldBe true
      subsystem.mcpClientServerSetIds.map(_.print) shouldBe Vector("research")
      firstSocket.service(serverSetId).isSuccess shouldBe true
      secondSocket.service(serverSetId).isSuccess shouldBe true

      And("subsystem shutdown closes the installed service lifecycle")
      val service = firstSocket.service(serverSetId).toOption.get
      subsystem.shutdownC().isSuccess shouldBe true
      service.withInvocation(_ => Consequence.unit).isFaillure shouldBe true
    }

    "leave no active server set when operator policy activation fails" in {
      Given("a policy that selects an unsupported local process definition")
      given ExecutionContext = ExecutionContext.create()
      val directory = Files.createTempDirectory("cncf-mcp-subsystem-invalid")
      Files.writeString(directory.resolve("config.toml"),
        """[mcp_servers.local]
          |command = "unsafe"
          |""".stripMargin
      )
      val policy = directory.resolve("mcp-client.yaml")
      Files.writeString(policy,
        """source:
          |  kind: codex
          |  path: config.toml
          |serverSet:
          |  id: local
          |  servers:
          |    - sourceName: local
          |      admittedTools:
          |        - unsafe.run
          |""".stripMargin
      )
      val subsystem = TestComponentFactory.emptySubsystem("mcp-client-invalid")

      When("the subsystem attempts activation")
      val result = subsystem.activateCodexMcpClientRuntimeC(policy)

      Then("activation fails before a runtime resource is retained")
      result.isFaillure shouldBe true
      subsystem.mcpClientServerSetIds shouldBe Vector.empty
      subsystem.shutdownC().isSuccess shouldBe true
    }

    "publish no socket service when any component requirement is unavailable" in {
      Given("one admitted consumer and one consumer requiring an absent server set")
      given ExecutionContext = ExecutionContext.create()
      val researchId = McpServerSetId.parseC("research").toOption.get
      val absentId = McpServerSetId.parseC("absent").toOption.get
      val admittedSocket = _socket(researchId)
      val unavailableSocket = _socket(absentId)
      val subsystem = TestComponentFactory.emptySubsystem("mcp-client-atomic-install")
      subsystem.add(Vector(
        _component("admitted_consumer", admittedSocket),
        _component("unavailable_consumer", unavailableSocket)
      ))

      When("runtime activation resolves all consumer requirements as one batch")
      val result = subsystem.activateCodexMcpClientRuntimeC(_policy_file())

      Then("the failure leaves every consumer socket uninstalled")
      result.isFaillure shouldBe true
      admittedSocket.isInstalled shouldBe false
      unavailableSocket.isInstalled shouldBe false
      subsystem.mcpClientServerSetIds shouldBe Vector.empty
      subsystem.shutdownC().isSuccess shouldBe true
    }
  }

  private def _policy_file(): Path = {
    val directory = Files.createTempDirectory("cncf-mcp-subsystem")
    Files.writeString(directory.resolve("config.toml"),
      """[mcp_servers.research_service]
        |url = "https://mcp.example.test/tools"
        |""".stripMargin
    )
    val policy = directory.resolve("mcp-client.yaml")
    Files.writeString(policy,
      """source:
        |  kind: codex
        |  path: config.toml
        |serverSet:
        |  id: research
        |  servers:
        |    - sourceName: research_service
        |      admittedTools:
        |        - paper.search
        |""".stripMargin
    )
    policy
  }

  private def _socket(serverSetId: McpServerSetId): McpClientSocket =
    McpClientSocket.createC(Vector(McpClientRequirement(serverSetId))).toOption.get

  private def _component(
    name: String,
    socket: McpClientSocket
  ): Component =
    TestComponentFactory
      .create(name, Protocol.empty)
      .withPort(Component.Port.input(socket))
}
