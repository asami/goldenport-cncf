package org.goldenport.cncf.specification.SCENARIO

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class McpProjectionScenarioSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "MCP projection" should {
    "be reachable via command path" in {
      Given("a runtime subsystem and an MCP export request")
      val subsystem = RuntimeBindingAdmissionFixture.default()
      val req = Request(
        component = Some("org.goldenport.cncf.Specification"),
        service = Some("export"),
        operation = "mcp",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )

      When("the command request reaches the MCP projection")
      val result = subsystem.execute(req)

      Then("the projection exposes its protocol version and tools")
      result match {
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
