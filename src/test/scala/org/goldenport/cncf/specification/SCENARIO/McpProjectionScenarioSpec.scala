package org.goldenport.cncf.specification.SCENARIO

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class McpProjectionScenarioSpec extends AnyWordSpec with Matchers {
  "MCP projection" should {
    "be reachable via command path" in {
      val subsystem = DefaultSubsystemFactory.default()
      val req = Request(
        component = Some("spec"),
        service = Some("export"),
        operation = "mcp",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )

      subsystem.execute(req) match {
        case Consequence.Success(res) =>
          res match {
            case Response.Scalar(value: String) =>
              value should include ("\"mcpVersion\"")
              value should include ("\"tools\"")
            case other =>
              fail(s"unexpected response: ${other.toString}")
          }
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }
  }
}
