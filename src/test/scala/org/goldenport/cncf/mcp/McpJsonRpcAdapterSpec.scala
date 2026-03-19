package org.goldenport.cncf.mcp

import io.circe.parser.parse
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpJsonRpcAdapterSpec extends AnyWordSpec with Matchers {
  "McpJsonRpcAdapter" should {
    "handle initialize request" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val c = json.hcursor
      c.get[String]("jsonrpc") shouldBe Right("2.0")
      c.downField("result").downField("protocolVersion").as[String].isRight shouldBe true
    }

    "handle tools/list request" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x1","method":"tools/list","params":{}}"""

      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val tools = json.hcursor.downField("result").downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools are missing"))
      tools should not be empty
    }

    "handle tools/call request through subsystem execution path" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw =
        """{"jsonrpc":"2.0","id":"x2","method":"tools/call","params":{"name":"admin.system.ping","arguments":{}}}"""

      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      val content = json.hcursor.downField("result").downField("content").focus
        .flatMap(_.asArray)
        .getOrElse(fail("content is missing"))
      content should not be empty
    }

    "return method not found for unknown method" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x3","method":"unknown.method","params":{}}"""

      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32601)
    }

    "return invalid params when tools/call name is missing" in {
      val subsystem = DefaultSubsystemFactory.default(Some("server"))
      val adapter = new McpJsonRpcAdapter(subsystem)
      val raw = """{"jsonrpc":"2.0","id":"x4","method":"tools/call","params":{"arguments":{}}}"""

      val json = parse(adapter.handle(raw)).fold(
        err => fail(s"response is not valid JSON: ${err.getMessage}"),
        identity
      )
      json.hcursor.downField("error").get[Int]("code") shouldBe Right(-32602)
    }
  }
}
