package org.goldenport.cncf.mcp.client

import java.util.concurrent.atomic.AtomicInteger

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned Codex MCP activation.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class CodexMcpRuntimeAssemblySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Codex MCP runtime assembly" should {
    "install only the normalized admitted service into a consumer socket" in {
      Given("a decoded Codex source, CNCF policy overlay, and consumer-owned logical socket")
      given ExecutionContext = ExecutionContext.create()
      val source = _source(
        "Research_Service" -> Record.dataAuto("url" -> "https://mcp.example.test/tools"),
        "local-shell" -> Record.dataAuto("command" -> "unsafe")
      )
      val policy = _policy("Research_Service", Vector("paper.search"))
      val serversetid = McpServerSetId.parseC("research").toOption.get
      val socket = McpClientSocket.createC(Vector(McpClientRequirement(serversetid))).toOption.get
      val component = new Component() {}.withPort(Component.Port.input(socket))

      When("runtime assembly imports, activates, and installs the selected server set")
      val assembly = CodexMcpRuntimeAssembly.createC(source, policy).toOption.get
      val result = assembly.installC(component)

      Then("the consumer sees only its normalized provider-neutral service")
      result.toOption shouldBe Some(component)
      assembly.serverSetIds.map(_.print) shouldBe Vector("research")
      socket.isInstalled shouldBe true
      socket.service(serversetid).isSuccess shouldBe true
      assembly.close()
    }

    "reject unsafe selected definitions before allocating a transport exchange" in {
      Given("a selected stdio definition and a transport factory with observable allocation")
      given ExecutionContext = ExecutionContext.create()
      val allocations = new AtomicInteger(0)
      val factory = () => {
        allocations.incrementAndGet()
        new UnavailableExchange()
      }

      When("runtime activation evaluates the definition")
      val result = CodexMcpRuntimeAssembly.createC(
        _source("selected" -> Record.dataAuto("command" -> "unsafe")),
        _policy("selected", Vector("safe.read")),
        factory
      )

      Then("admission fails before transport resources exist")
      result.isFaillure shouldBe true
      allocations.get() shouldBe 0
    }

    "make installed services unavailable after runtime assembly closure" in {
      Given("an activated server set and an uninstalled consumer socket")
      given ExecutionContext = ExecutionContext.create()
      val assembly = CodexMcpRuntimeAssembly.createC(
        _source("selected" -> Record.dataAuto("url" -> "https://mcp.example.test/tools")),
        _policy("selected", Vector("safe.read"))
      ).toOption.get
      val socket = McpClientSocket.createC(Vector(
        McpClientRequirement(McpServerSetId.parseC("research").toOption.get)
      )).toOption.get
      val component = new Component() {}.withPort(Component.Port.input(socket))

      When("the runtime closes before installing the consumer")
      assembly.close()
      val result = assembly.installC(component)

      Then("the closed registry cannot expose a service")
      result.isFaillure shouldBe true
      socket.isInstalled shouldBe false
    }
  }

  private def _source(
    definitions: (String, Record)*
  ): Record =
    Record.dataAuto("mcp_servers" -> Record.create(definitions))

  private def _policy(
    sourcename: String,
    tools: Vector[String]
  ): CodexMcpImportPolicy = {
    val server = CodexMcpServerImportPolicy.createC(sourcename, tools).toOption.get
    CodexMcpImportPolicy.createC(
      McpServerSetId.parseC("research").toOption.get,
      Vector(server),
      McpClientLimits.default
    ).toOption.get
  }

  private final class UnavailableExchange extends McpStreamableHttpExchange {
    def execute(request: McpStreamableHttpRequest): Consequence[McpStreamableHttpResponse] =
      Consequence.serviceUnavailable("not used")
  }
}
