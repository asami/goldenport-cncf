package org.goldenport.cncf.spec

import io.circe.parser.parse
import org.goldenport.cncf.mcp.{McpProjector, McpProtocolRevision}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpProjectorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "McpProjector" should {
    "produce MCP tool projection output" in {
      Given("a command subsystem with the admin system service declared MCP ready")
      val subsystem = DefaultSubsystemFactory.default(Some("command"))
      subsystem.components.find(_.name == "admin").foreach(_.withMcpReadyServices(Set("system")))

      When("the subsystem is projected as MCP metadata")
      val json = parse(McpProjector.forSubsystem(subsystem)).fold(
        err => fail(s"MCP JSON parse failed: ${err.getMessage}"),
        identity
      )

      Then("the projection contains versioned, described, typed tools")
      val top = json.hcursor
      top.get[String]("mcpVersion") shouldBe Right(McpProtocolRevision.PREFERRED.print)

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
