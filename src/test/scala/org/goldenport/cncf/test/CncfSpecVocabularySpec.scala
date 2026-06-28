package org.goldenport.cncf.test

import io.circe.Json
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 28, 2026
 * @version Jun. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class CncfSpecVocabularySpec extends AnyWordSpec with GivenWhenThen with CncfSpecVocabulary {
  "CncfSpecVocabulary" should {
    "provide CNCF operation and payload matchers" which {
      "describe operation definitions as CNCF operations" in {
        Given("a set of generated operation names")
        val names = Set("initialize", "callTool")

        When("the operation vocabulary checks for an exposed operation")
        val result = contain_operation("callTool").apply(names)

        Then("the matcher succeeds using operation-oriented wording")
        result.matches shouldBe true
      }

      "describe MCP tool advertisements" in {
        Given("an MCP tool list JSON payload")
        val payload = Json.obj("tools" -> Json.arr(Json.obj("name" -> Json.fromString("sie.status"))))

        When("the MCP vocabulary checks for an advertised tool")
        val result = advertise_mcp_tool("sie.status").apply(payload)

        Then("the matcher succeeds using MCP-oriented wording")
        result.matches shouldBe true
      }

      "describe Record and JSON response fields" in {
        Given("a CNCF operation Record and JSON response")
        val record = Record.dataAuto("knowledgeSpaceState" -> "frame_only", "termCount" -> 1)
        val json = Json.obj(
          "knowledgeSpaceState" -> Json.fromString("frame_only"),
          "termCount" -> Json.fromInt(1),
          "knowledgeCounts" -> Json.obj("nodeCount" -> Json.fromInt(1))
        )

        When("the response vocabulary checks typed fields")
        val recordstate = have_record_string_value("knowledgeSpaceState", "frame_only").apply(record)
        val recordcount = have_record_int_value("termCount", 1).apply(record)
        val recordminimum = have_record_int_at_least("termCount", 1).apply(record)
        val jsonstate = have_json_string_value("knowledgeSpaceState", "frame_only").apply(json)
        val jsoncount = have_json_int_value("termCount", 1).apply(json)
        val jsonnestedcount = have_json_nested_int("knowledgeCounts", "nodeCount").apply(json)

        Then("the matchers succeed using field-oriented wording")
        recordstate.matches shouldBe true
        recordcount.matches shouldBe true
        recordminimum.matches shouldBe true
        jsonstate.matches shouldBe true
        jsoncount.matches shouldBe true
        jsonnestedcount.matches shouldBe true
      }
    }
  }
}
