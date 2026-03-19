package org.goldenport.cncf.spec

import io.circe.parser.parse
import org.goldenport.cncf.mcp.McpProjector
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpProjectorSpec extends AnyWordSpec with Matchers {
  "McpProjector" should {
    "produce MCP tool projection output" in {
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      val json = parse(McpProjector.forSubsystem(subsystem)).fold(
        err => fail(s"MCP JSON parse failed: ${err.getMessage}"),
        identity
      )

      val top = json.hcursor
      top.get[String]("mcpVersion").isRight shouldBe true

      val tools = top.downField("tools").focus
        .flatMap(_.asArray)
        .getOrElse(fail("tools array is missing"))

      tools should not be empty
      val first = tools.head.hcursor
      first.get[String]("name").isRight shouldBe true
      first.get[String]("description").isRight shouldBe true
      first.downField("inputSchema").get[String]("type") shouldBe Right("object")
    }
  }
}
