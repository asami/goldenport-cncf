package org.goldenport.cncf.mcp

import java.time.Instant

import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.conclusion.{Disposition, Interpretation}
import org.goldenport.observation.{Cause, Descriptor, Observation, Phenomenon, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version May. 18, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpJsonRpcAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "McpJsonRpcAdapter" should {
    "protocol lifecycle" which {
    "negotiate every shared supported initialize revision exactly" in {
      Given("an MCP adapter and each shared supported protocol revision")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)

      When("each exact revision is requested")
      val negotiated = McpProtocolRevision.SUPPORTED.map { revision =>
        val raw = s"""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"${revision.print}"}}"""
        val json = _response_json(adapter.handle(raw, None))
        json.hcursor.downField("result").get[String]("protocolVersion")
      }

      Then("the server echoes every accepted revision without inventing another version")
      negotiated shouldBe McpProtocolRevision.SUPPORTED.map(x => Right(x.print))
    }

    "reject missing malformed non-string and unsupported initialize revisions" in {
      Given("initialize requests violating each revision boundary")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val params = Vector(
        "{}",
        "{\"protocolVersion\":25}",
        "{\"protocolVersion\":\"2025-1\"}",
        "{\"protocolVersion\":\" 2025-11-25 \"}",
        "{\"protocolVersion\":\"2026-03-19\"}"
      )

      When("the adapter validates each request")
      val errors = params.map { value =>
        val raw = s"""{"jsonrpc":"2.0","id":"revision","method":"initialize","params":$value}"""
        _response_json(adapter.handle(raw, None)).hcursor.downField("error")
      }

      Then("every invalid revision fails as bounded invalid params")
      all(errors.map(_.get[Int]("code"))) shouldBe Right(-32602)
      all(errors.map(_.get[String]("message").toOption.getOrElse(""))) should not include "2026-03-19"
    }

    "distinguish request responses from accepted initialized notifications" in {
      Given("an MCP adapter with one initialized notification and one normal request")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val notification =
        """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
      val request =
        """{"jsonrpc":"2.0","id":"list","method":"tools/list","params":{}}"""

      When("both messages carry the negotiated protocol revision")
      val notificationoutcome = adapter.handle(notification, _protocol_header)
      val requestoutcome = adapter.handle(request, _protocol_header)

      Then("the notification has no response body while the request has a typed JSON response")
      notificationoutcome shouldBe McpJsonRpcOutcome.AcceptedNotification
      requestoutcome shouldBe a[McpJsonRpcOutcome.Response]
      _response_json(requestoutcome).hcursor.get[String]("id") shouldBe Right("list")
    }

    "reject request-shaped initialized messages and unsupported notifications without notification responses" in {
      Given("request-shaped initialized, unknown notification, and missing-header messages")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val requestshaped =
        """{"jsonrpc":"2.0","id":null,"method":"notifications/initialized"}"""
      val unknownnotification =
        """{"jsonrpc":"2.0","method":"notifications/unknown"}"""
      val initializedwithoutheader =
        """{"jsonrpc":"2.0","method":"notifications/initialized"}"""

      When("the adapter applies request and notification lifecycle rules")
      val requestoutcome = adapter.handle(requestshaped, _protocol_header)
      val unknownoutcome = adapter.handle(unknownnotification, _protocol_header)
      val missingheaderoutcome = adapter.handle(initializedwithoutheader, None)

      Then("only the request-shaped failure has a JSON-RPC error body")
      requestoutcome shouldBe a[McpJsonRpcOutcome.ProtocolFailure]
      _response_json(requestoutcome).hcursor.downField("error").get[Int]("code") shouldBe Right(-32600)
      unknownoutcome shouldBe McpJsonRpcOutcome.ProtocolFailure(None)
      missingheaderoutcome shouldBe McpJsonRpcOutcome.ProtocolFailure(None)
    }

    "require a supported protocol revision on post-initialize requests" in {
      Given("a tools/list request with missing and unsupported protocol headers")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val request =
        """{"jsonrpc":"2.0","id":"header","method":"tools/list","params":{}}"""

      When("the adapter validates both lifecycle headers")
      val missing = _response_json(adapter.handle(request, None))
      val unsupported = _response_json(adapter.handle(request, Some("2026-03-19")))

      Then("both requests fail with bounded invalid-request diagnostics")
      missing.hcursor.downField("error").get[Int]("code") shouldBe Right(-32600)
      unsupported.hcursor.downField("error").get[Int]("code") shouldBe Right(-32600)
      missing.noSpaces should not include "2025-11-25"
      unsupported.noSpaces should not include "2026-03-19"
    }
    }

    "catalog and publication" which {
    "handle tools/list request" in {
      Given("a subsystem with the admin system service declared MCP ready")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).foreach(_.withMcpReadyServices(Set("system")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x1","method":"tools/list","params":{}}"""

      When("the MCP tool catalog is requested")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      val tools = json.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools are missing"))
      Then("ready operations from the primary admin and builtin tool participants are listed")
      tools should not be empty
      val toolnames = tools.map(_.hcursor.get[String]("name").toOption.getOrElse(""))
      toolnames should contain ("org.goldenport.cncf.Admin.system.ping")
      toolnames should contain ("org.goldenport.cncf.Tool.resource.read")
      toolnames should contain ("org.goldenport.cncf.Tool.time.now")
      toolnames should contain ("org.goldenport.cncf.Tool.decimal.calculate")
      all(toolnames.map(name => name.startsWith("org.goldenport.cncf.Admin.system.") || name.startsWith("org.goldenport.cncf.Tool."))) shouldBe true
    }

    "project integer and boolean parameter schemas" in {
      Given("a ready blob service with typed operation parameters")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).foreach(_.withMcpReadyServices(Set("blob")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x1b","method":"tools/list","params":{}}"""

      When("the MCP tool schemas are projected")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      val tools = json.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools are missing"))
      val deleteblob = tools.find(_.hcursor.get[String]("name").contains("org.goldenport.cncf.Blob.blob.admin_delete_blob"))
        .getOrElse(fail("blob admin delete tool is missing"))
      val force = deleteblob.hcursor
        .downField("inputSchema")
        .downField("properties")
        .downField("force")
        .get[String]("type")
      Then("boolean and integer parameters retain their JSON schema types")
      force shouldBe Right("boolean")
      val listblob = tools.find(_.hcursor.get[String]("name").contains("org.goldenport.cncf.Blob.blob.admin_list_blobs"))
        .getOrElse(fail("blob admin list tool is missing"))
      val limit = listblob.hcursor
        .downField("inputSchema")
        .downField("properties")
        .downField("limit")
        .get[String]("type")
      limit shouldBe Right("integer")
    }
    }

    "invocation and policy" which {
    "handle tools/call request through subsystem execution path" in {
      Given("a ready admin ping operation")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).foreach(_.withMcpReadyServices(Set("system")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2","method":"tools/call","params":{"name":"org.goldenport.cncf.Admin.system.ping","arguments":{}}}"""

      When("the tool is called through MCP JSON-RPC")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      val content = json.hcursor.downField("result").downField("content").focus
        .flatMap(_.asArray)
        .getOrElse(fail("content is missing"))
      Then("the subsystem result is returned as MCP content")
      content should not be empty
    }

    "bind named MCP arguments as operation properties" in {
      Given("a ready blob operation with one required named property")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).foreach(_.withMcpReadyServices(Set("blob")))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2-property","method":"tools/call","params":{"name":"org.goldenport.cncf.Blob.blob.admin_get_blob","arguments":{"id":"missing-for-test"}}}"""

      When("the MCP arguments are converted into a CNCF request")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      val text = json.hcursor.downField("result").downField("content").downArray.get[String]("text")

      Then("the named property reaches generated operation request binding")
      text.toOption.getOrElse("") should not include "argument.missing"
    }

    "reject tools/call for an operation that is not MCP ready" in {
      Given("an admin operation without an MCP readiness declaration")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2b","method":"tools/call","params":{"name":"org.goldenport.cncf.Admin.system.ping","arguments":{}}}"""

      When("the unpublished operation is called through MCP")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      Then("the adapter rejects the unpublished tool")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }

    "require service-qualified operation readiness declarations" in {
      Given("an admin component with a bare operation readiness declaration")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component is missing"))
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
      val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).getOrElse(fail("admin component is missing"))
      admin.withMcpReadyServices(Set("system"))
      admin.withApplicationConfig(Component.ApplicationConfig(config = Some(Configuration(Map(
        "cncf.mcp.disabled-operations" -> ConfigurationValue.StringValue("system.ping")
      )))))
      val adapter = new McpJsonRpcAdapter(subsystem)

      When("the tool is listed and called")
      val listed = _response_json(adapter.handle(
        """{"jsonrpc":"2.0","id":"configured-list","method":"tools/list","params":{}}""",
        _protocol_header
      ))
      val names = listed.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray).getOrElse(Vector.empty)
        .flatMap(_.hcursor.get[String]("name").toOption)
      val called = _response_json(adapter.handle(
        """{"jsonrpc":"2.0","id":"configured-call","method":"tools/call","params":{"name":"org.goldenport.cncf.Admin.system.ping","arguments":{}}}""",
        _protocol_header
      ))

      Then("the runtime policy narrows both discovery and invocation")
      names should not contain "org.goldenport.cncf.Admin.system.ping"
      called.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }
    }

    "protocol failures" which {
    "return method not found for unknown method" in {
      Given("an MCP request with an unknown JSON-RPC method")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x3","method":"unknown.method","params":{}}"""

      When("the request is handled")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      Then("the adapter returns method-not-found")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32601)
    }

    "return invalid params when tools/call name is missing" in {
      Given("a tools/call request without a tool name")
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x4","method":"tools/call","params":{"arguments":{}}}"""

      When("the malformed request is handled")
      val json = _response_json(adapter.handle(raw, _protocol_header))
      Then("the adapter returns invalid params")
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }
    }

    "tool-failure projection" which {
    "project only an explicitly present application status into a legacy-compatible tool failure" in {
      Given("tool failures with nonempty, empty, and absent explicit application status")
      val nonempty = _tool_failure_result(Some(_app_status))
      val empty = _tool_failure_result(Some(""))
      val absent = _tool_failure_result(None)

      When("the MCP failure projection is formed")
      val projected = Vector(nonempty, empty, absent)

      Then("each result preserves one legacy text block while exposing only the allowed status projection")
      projected.foreach { case (result, conclusion) =>
        result.hcursor.get[Boolean]("isError") shouldBe Right(true)
        result.hcursor.downField("content").focus.flatMap(_.asArray) shouldBe Some(Vector(
          Json.obj(
            "type" -> Json.fromString("text"),
            "text" -> Json.fromString(conclusion.show)
          )
        ))
        result.noSpaces should not include _reason
        result.noSpaces should not include "appCode"
        result.noSpaces should not include "detailCode"
      }
      nonempty._1.hcursor.downField("structuredContent").downField("error").get[String]("appStatus") shouldBe Right(_app_status)
      empty._1.hcursor.downField("structuredContent").downField("error").get[String]("appStatus") shouldBe Right("")
      absent._1.hcursor.downField("structuredContent").succeeded shouldBe false
      nonempty._1.hcursor.downField("structuredContent").focus shouldBe Some(Json.obj(
        "error" -> Json.obj("appStatus" -> Json.fromString(_app_status))
      ))
      empty._1.hcursor.downField("structuredContent").focus shouldBe Some(Json.obj(
        "error" -> Json.obj("appStatus" -> Json.fromString(""))
      ))
    }
    }
  }

  private val _protocol_header = Some(McpProtocolRevision.PREFERRED.print)

  private def _response_json(outcome: McpJsonRpcOutcome): Json =
    outcome.responseBody.getOrElse(fail("MCP outcome has no JSON response body"))

  private def _tool_failure_result(
    appstatus: Option[String]
  ): (Json, Conclusion) = {
    val conclusion = Conclusion(
      status = Conclusion.Status(appCode = Some(7404L), appStatus = appstatus),
      observation = Observation(
        phenomenon = Phenomenon.Failure,
        taxonomy = Taxonomy(Taxonomy.Category.Argument, Taxonomy.Symptom.Invalid),
        cause = Cause.create(Vector(Descriptor.Facet.Message(_message), Descriptor.Facet.Reason(_reason))),
        timestamp = Instant.EPOCH
      ),
      interpretation = Interpretation.domainFailure,
      disposition = Disposition.fix
    )
    McpToolFailureProjection.result(conclusion) -> conclusion
  }

  private val _app_status = "project-identity-required"
  private val _message = "Project identity is required."
  private val _reason = "private-reason-must-not-be-projected"
}
