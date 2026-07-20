package org.goldenport.cncf.mcp.client

import scala.collection.mutable.ArrayBuffer

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ExtensionPoint, Port, ServiceContract, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned MCP client Port wiring.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpClientPortSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP client Port" should {
    "install one server-set-bound service and invoke only its typed catalog" in {
      Given("a runtime server set and deterministic fake transport ExtensionPoint")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val tool = _tool("catalog", "paper.search")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(tool)))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val component = new Component() {}

      When("the canonical Port binding installs the admitted MCP client service")
      val installed = registry.binding.install(component, McpClientRequirement(serverset.id))
      val service = installed.toOption.get.port.get[McpClientService].get
      val catalog = service.catalog
      val result = service.invoke(_call(tool.identity, "paper"))

      Then("the consumer sees typed catalog and result values without transport configuration")
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe Some(Vector("catalog/paper.search"))
      result.toOption.flatMap(_.structuredContent) shouldBe Some(McpValue.StringValue("found:paper"))
      fake.events.toVector shouldBe Vector(
        "initialize:catalog",
        "list:catalog",
        "call:catalog/paper.search"
      )
    }

    "reject infrastructure variation at the consumer Port boundary" in {
      Given("a valid runtime-owned registry and a caller-supplied provider selector")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(new _FakeTransport(Map(_server_id("catalog") -> Vector.empty)), Set(serverset.id))
      ).toOption.get

      When("the caller tries to override transport selection")
      val result = registry.binding.bind(
        McpClientRequirement(serverset.id),
        VariationSelection(provider = Some("caller-http"))
      )

      Then("binding fails before any consumer service is installed")
      result.isFaillure shouldBe true
    }

    "reject an unbound server set before transport invocation" in {
      Given("a registry containing one differently named server set")
      given ExecutionContext = ExecutionContext.create()
      val admitted = _server_set("admitted", "catalog")
      val requested = _server_set_id("missing")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector.empty))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(admitted),
        _transport_binding(fake, Set(admitted.id))
      ).toOption.get

      When("a component resolves a non-admitted server-set requirement")
      val result = registry.binding.bind(McpClientRequirement(requested))

      Then("the Port has no matching ExtensionPoint and transport stays untouched")
      result.isFaillure shouldBe true
      fake.events shouldBe empty
    }

    "reject a stale tool identity before callTool" in {
      Given("an admitted catalog and a call naming another tool")
      given ExecutionContext = ExecutionContext.create()
      val serverset = _server_set("research", "catalog")
      val fake = new _FakeTransport(Map(_server_id("catalog") -> Vector(_tool("catalog", "paper.search"))))
      val registry = McpClientRuntimeRegistry.createC(
        Vector(serverset),
        _transport_binding(fake, Set(serverset.id))
      ).toOption.get
      val service = registry.resolve(serverset.id).toOption.get

      When("the typed call carries a stale non-catalog tool identity")
      val result = service.invoke(_call(
        McpToolIdentity(_server_id("catalog"), _tool_name("paper.delete")),
        "paper"
      ))

      Then("catalog discovery occurs but the fake call boundary is not crossed")
      result.isFaillure shouldBe true
      fake.events.toVector shouldBe Vector("initialize:catalog", "list:catalog")
    }
  }

  private def _transport_binding(
    transport: McpClientTransport,
    admitted: Set[McpServerSetId]
  ): Component.Binding[McpClientTransportRequirement, McpClientTransport] =
    Component.Binding(Port(
      api = McpClientTransportPortApi,
      spi = Vector(new ExtensionPoint[McpClientTransport] {
        def supports(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Boolean =
          variation == VariationSelection() &&
            McpClientTransportPortApi.serverSetId(contract).exists(admitted.contains)

        def provide(
          contract: ServiceContract[McpClientTransport],
          variation: VariationSelection
        )(using ExecutionContext): Consequence[McpClientTransport] =
          Consequence.success(transport)
      }),
      variation = McpClientTransportSelectionPoint
    ))

  private final class _FakeTransport(
    catalogs: Map[McpServerId, Vector[McpClientTool]]
  ) extends McpClientTransport {
    val events = ArrayBuffer.empty[String]

    def initialize(server: McpClientServer)(using ExecutionContext): Consequence[Unit] = {
      events += s"initialize:${server.id.print}"
      Consequence.unit
    }

    def listTools(server: McpClientServer)(using ExecutionContext): Consequence[Vector[McpClientTool]] = {
      events += s"list:${server.id.print}"
      Consequence.success(catalogs.getOrElse(server.id, Vector.empty))
    }

    def callTool(
      server: McpClientServer,
      call: McpClientCall
    )(using ExecutionContext): Consequence[McpClientResult] = {
      events += s"call:${call.toolIdentity.print}"
      val query = call.arguments.fields.collectFirst {
        case (name, McpValue.StringValue(value)) if name.print == "query" => value
      }.getOrElse("")
      Consequence.success(McpClientResult(
        Vector.empty,
        Some(McpValue.StringValue(s"found:${query}"))
      ))
    }
  }

  private def _server_set(name: String, server: String): McpClientServerSet =
    McpClientServerSet.createC(
      _server_set_id(name),
      Vector(McpClientServer(_server_id(server)))
    ).toOption.get

  private def _server_set_id(value: String): McpServerSetId =
    McpServerSetId.parseC(value).toOption.get

  private def _server_id(value: String): McpServerId =
    McpServerId.parseC(value).toOption.get

  private def _tool_name(value: String): McpToolName =
    McpToolName.parseC(value).toOption.get

  private def _field_name(value: String): McpFieldName =
    McpFieldName.parseC(value).toOption.get

  private def _tool(server: String, name: String): McpClientTool =
    McpClientTool.createC(
      McpToolIdentity(_server_id(server), _tool_name(name)),
      McpInputSchema.objectC(Vector(
        McpInputField.createC(_field_name("query"), McpInputSchema.StringValue, required = true).toOption.get
      )).toOption.get
    ).toOption.get

  private def _call(identity: McpToolIdentity, query: String): McpClientCall =
    McpClientCall.createC(
      identity,
      McpValue.objectC(Vector(
        _field_name("query") -> McpValue.StringValue(query)
      )).toOption.get
    ).toOption.get
}
