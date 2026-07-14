package org.goldenport.cncf.mcp

import io.circe.parser.parse
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version May. 18, 2026
 * @version Jul. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpJsonRpcAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "McpJsonRpcAdapter" should {
    "handle initialize request" in {
      Given("an MCP adapter and initialize request")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

      When("the request is handled")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      Then("the adapter returns the negotiated MCP protocol version")
      val c = json.hcursor
      c.get[String]("jsonrpc") shouldBe Right("2.0")
      c.downField("result").downField("protocolVersion").as[String].isRight shouldBe true
    }

    "handle tools/list request" in {
      Given("a subsystem with the admin system service declared MCP ready")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.components.find(_.name == "admin").foreach(_.withMcpReadyServices(Set("system")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x1","method":"tools/list","params":{}}"""

      When("the MCP tool catalog is requested")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val tools = json.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools are missing"))
      Then("only ready operations from the primary admin participant are listed")
      tools should not be empty
      val toolnames = tools.map(_.hcursor.get[String]("name").toOption.getOrElse(""))
      toolnames should contain ("admin.system.ping")
      all(toolnames) should startWith ("admin.system.")
    }

    "project integer and boolean parameter schemas" in {
      Given("a ready blob service with typed operation parameters")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.components.find(_.name == "blob").foreach(_.withMcpReadyServices(Set("blob")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x1b","method":"tools/list","params":{}}"""

      When("the MCP tool schemas are projected")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val tools = json.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools are missing"))
      val deleteblob = tools.find(_.hcursor.get[String]("name").contains("blob.blob.admin_delete_blob"))
        .getOrElse(fail("blob admin delete tool is missing"))
      val force = deleteblob.hcursor
        .downField("inputSchema")
        .downField("properties")
        .downField("force")
        .get[String]("type")
      Then("boolean and integer parameters retain their JSON schema types")
      force shouldBe Right("boolean")
      val listblob = tools.find(_.hcursor.get[String]("name").contains("blob.blob.admin_list_blobs"))
        .getOrElse(fail("blob admin list tool is missing"))
      val limit = listblob.hcursor
        .downField("inputSchema")
        .downField("properties")
        .downField("limit")
        .get[String]("type")
      limit shouldBe Right("integer")
    }

    "handle tools/call request through subsystem execution path" in {
      Given("a ready admin ping operation")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.components.find(_.name == "admin").foreach(_.withMcpReadyServices(Set("system")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2","method":"tools/call","params":{"name":"admin.system.ping","arguments":{}}}"""

      When("the tool is called through MCP JSON-RPC")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val content = json.hcursor.downField("result").downField("content").focus
        .flatMap(_.asArray)
        .getOrElse(fail("content is missing"))
      Then("the subsystem result is returned as MCP content")
      content should not be empty
    }

    "bind named MCP arguments as operation properties" in {
      Given("a ready blob operation with one required named property")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.components.find(_.name == "blob").foreach(_.withMcpReadyServices(Set("blob")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2-property","method":"tools/call","params":{"name":"blob.blob.admin_get_blob","arguments":{"id":"missing-for-test"}}}"""

      When("the MCP arguments are converted into a CNCF request")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val text = json.hcursor.downField("result").downField("content").downArray.get[String]("text")

      Then("the named property reaches generated operation request binding")
      text.toOption.getOrElse("") should not include "argument.missing"
    }

    "reject tools/call for an operation that is not MCP ready" in {
      Given("an admin operation without an MCP readiness declaration")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2b","method":"tools/call","params":{"name":"admin.system.ping","arguments":{}}}"""

      When("the unpublished operation is called through MCP")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      Then("the adapter rejects the unpublished tool")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }

    "require service-qualified operation readiness declarations" in {
      Given("an admin component with a bare operation readiness declaration")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val admin = subsystem.components.find(_.name == "admin").getOrElse(fail("admin component is missing"))
      admin.withMcpReadyOperations(Set("ping"))

      When("readiness is evaluated before and after service qualification")
      val bareisready = admin.isMcpReady("system", "ping")
      admin.withMcpReadyOperations(Set("system.ping"))
      val qualifiedisready = admin.isMcpReady("system", "ping")

      Then("only the service-qualified declaration publishes the operation")
      bareisready shouldBe false
      qualifiedisready shouldBe true
    }

    "apply component runtime configuration as a narrowing MCP policy" in {
      Given("a ready operation disabled by component runtime configuration")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val admin = subsystem.components.find(_.name == "admin").getOrElse(fail("admin component is missing"))
      admin.withMcpReadyServices(Set("system"))
      admin.withApplicationConfig(Component.ApplicationConfig(config = Some(Configuration(Map(
        "cncf.mcp.disabled-operations" -> ConfigurationValue.StringValue("system.ping")
      )))))
      val adapter = new McpJsonRpcAdapter(subsystem)

      When("the tool is listed and called")
      val listed = parse(adapter.handle("""{"jsonrpc":"2.0","id":"configured-list","method":"tools/list","params":{}}"""))
        .fold(error => fail(s"response is not valid JSON: ${error.getMessage}"), identity)
      val names = listed.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray).getOrElse(Vector.empty)
        .flatMap(_.hcursor.get[String]("name").toOption)
      val called = parse(adapter.handle(
        """{"jsonrpc":"2.0","id":"configured-call","method":"tools/call","params":{"name":"admin.system.ping","arguments":{}}}"""
      )).fold(error => fail(s"response is not valid JSON: ${error.getMessage}"), identity)

      Then("the runtime policy narrows both discovery and invocation")
      names should not contain "admin.system.ping"
      called.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }

    "return method not found for unknown method" in {
      Given("an MCP request with an unknown JSON-RPC method")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x3","method":"unknown.method","params":{}}"""

      When("the request is handled")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      Then("the adapter returns method-not-found")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32601)
    }

    "return invalid params when tools/call name is missing" in {
      Given("a tools/call request without a tool name")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x4","method":"tools/call","params":{"arguments":{}}}"""

      When("the malformed request is handled")
      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      Then("the adapter returns invalid params")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }
  }
}
