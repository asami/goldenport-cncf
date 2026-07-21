package org.goldenport.cncf.component.builtin.tool

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.mcp.McpToolCatalog
import org.goldenport.cncf.resource.{InMemoryUrnResourceProvider, ResourceAccess, ResourceAccessTestProfile, ResourceContent, ResourceReference}
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, Subsystem}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ToolComponentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _time_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.time.now, phase:45, stage:MC-07")
  private val _decimal_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.decimal.calculate, phase:45, stage:MC-07")
  private val _resource_metadata =
    afterWord("in spec:mcp-client-boundary, tool:tool.resource.read, phase:45, stage:MC-07")

  "Builtin resource Operation" should {
    "read bounded text only through the execution-context ResourceAccess" must _resource_metadata {
      "when an admitted logical URN is supplied" in {
        Given("a runtime with one explicit in-memory URN provider")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val profile = ResourceAccessTestProfile(
          urnProviders = Vector(new InMemoryUrnResourceProvider(
            "example",
            Map("article:1" -> "bounded semantic content")
          ))
        )
        given ExecutionContext = ExecutionContext.withResourceAccessTestProfile(
          ExecutionContext.create(),
          profile
        )

        When("tool.resource.read resolves the logical reference")
        val record = subsystem.executeQueryOnlyWithMetadata(
          _request("resource", "read", "reference" -> "urn:example:article:1")
        ).map(_.response).flatMap(_record_response_c).toOption.get

        Then("the response exposes text and safe content metadata without provider identity")
        record.getString("text") shouldBe Some("bounded semantic content")
        record.getString("scheme") shouldBe Some("urn")
        record.getLong("byteSize") shouldBe Some(24L)
        record.asMap.keySet should not contain "provider"
        record.asMap.keySet should not contain "reference"
      }
    }

    "preserve reference parsing and provider admission as structured failures" must _resource_metadata {
      "when a relative reference and an unconfigured absolute reference are supplied" in {
        Given("the normal runtime without an arbitrary resource provider")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))

        When("both values cross the normal Operation boundary")
        val relative = subsystem.executeOperationResponse(
          _request("resource", "read", "reference" -> "relative/file.txt")
        )
        val unconfigured = subsystem.executeOperationResponse(
          _request("resource", "read", "reference" -> "urn:example:missing")
        )

        Then("neither request creates an independent filesystem or network path")
        relative.isSuccess shouldBe false
        unconfigured.isSuccess shouldBe false
      }
    }

    "reject content above the builtin tool projection limit" must _resource_metadata {
      "when an admitted provider returns more than one MiB" in {
        Given("a deterministic provider whose logical resource exceeds the tool response budget")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val access = new ResourceAccess {
          def read(reference: ResourceReference): Consequence[ResourceContent] =
            Consequence.success(ResourceContent(reference, Vector.fill(1024 * 1024 + 1)('a'.toByte)))
        }
        given ExecutionContext = ExecutionContext.withResourceAccess(ExecutionContext.create(), access)

        When("the resource crosses the builtin Operation projection boundary")
        val result = subsystem.executeQueryOnlyWithMetadata(
          _request("resource", "read", "reference" -> "urn:example:oversized")
        )

        Then("the normal structured limit failure is returned without text projection")
        result.isSuccess shouldBe false
      }
    }
  }

  "Builtin time Operation" should {
    "read one instant from the controlled runtime clock" must _time_metadata {
      "when the caller selects an admitted IANA timezone" in {
        Given("a controlled CNCF runtime clock and the normal builtin Operation route")
        val instant = Instant.parse("2026-07-21T01:02:03.456Z")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))

        When("tool.time.now is executed for Asia/Tokyo")
        val record = subsystem.executeQueryOnlyWithMetadata(
          _request("time", "now", "timezone" -> "Asia/Tokyo")
        ).map(_.response).flatMap(_record_response_c) match {
          case Consequence.Success(value) => value
          case other => fail(s"expected record response but got $other")
        }

        Then("all time fields derive from the same injected instant")
        record.getString("instant") shouldBe Some("2026-07-21T01:02:03.456Z")
        record.getString("timezone") shouldBe Some("Asia/Tokyo")
        record.getString("zonedDateTime") shouldBe Some("2026-07-21T10:02:03.456+09:00[Asia/Tokyo]")
      }
    }

    "reject a timezone outside the bounded IANA contract" must _time_metadata {
      "when an offset-shaped timezone is supplied" in {
        Given("a time Operation request with a non-region timezone")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))

        When("the request is parsed through normal Operation dispatch")
        val result = subsystem.executeOperationResponse(
          _request("time", "now", "timezone" -> "+09:00")
        )

        Then("the request fails structurally before ActionCall execution")
        result.isSuccess shouldBe false
      }
    }
  }

  "Builtin decimal Operation" should {
    "calculate exact bounded decimal values without floating-point conversion" must _decimal_metadata {
      "when bounded add, subtract, and multiply inputs are generated" in {
        Given("integer coefficients rendered as exact decimal strings")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val property = Prop.forAll(
          Gen.chooseNum(-1000000L, 1000000L),
          Gen.chooseNum(-1000000L, 1000000L),
          Gen.oneOf("add", "subtract", "multiply")
        ) { (left, right, operator) =>
          val expected = operator match {
            case "add" => BigDecimal(left) + BigDecimal(right)
            case "subtract" => BigDecimal(left) - BigDecimal(right)
            case "multiply" => BigDecimal(left) * BigDecimal(right)
          }
          val actual = _execute_record_c(
            subsystem,
            _request(
              "decimal",
              "calculate",
              "operator" -> operator,
              "left" -> s"${left}.00",
              "right" -> s"${right}.0"
            )
          ).toOption.flatMap(_.getString("value"))
          actual.contains(_decimal_text(expected))
        }

        When("the generated requests execute through ActionCall/UoW")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every result is the exact canonical plain-decimal value")
        checked.passed shouldBe true
      }
    }

    "reject expression and oversized decimal input" must _decimal_metadata {
      "when callers attempt an undeclared expression operator or oversized operand" in {
        Given("the closed deterministic decimal Operation contract")
        val subsystem = DefaultSubsystemFactory.default(Some("command"))

        When("an expression-shaped operator and oversized decimal are dispatched")
        val expression = subsystem.executeOperationResponse(
          _request("decimal", "calculate", "operator" -> "left + right", "left" -> "1", "right" -> "2")
        )
        val oversized = subsystem.executeOperationResponse(
          _request("decimal", "calculate", "operator" -> "add", "left" -> ("1" * 129), "right" -> "2")
        )

        Then("both requests fail as structured argument policy violations")
        expression.isSuccess shouldBe false
        oversized.isSuccess shouldBe false
      }
    }
  }

  "Builtin tool MCP projection" should {
    "publish the same normal Operations with typed string inputs" must _decimal_metadata {
      "when the default subsystem MCP catalog is projected" in {
        Given("the builtin tool component with MCP-ready resource, time, and decimal services")
        val subsystem = DefaultSubsystemFactory.default(Some("server"))

        When("the existing MCP server catalog projects normal Operations")
        val tools = McpToolCatalog.toolsForSubsystem(subsystem)
        val time = tools.find(_.name == "tool.time.now")
        val decimal = tools.find(_.name == "tool.decimal.calculate")
        val resource = tools.find(_.name == "tool.resource.read")

        Then("all identities are present without a separate MCP implementation")
        time should not be empty
        decimal should not be empty
        resource should not be empty
        resource.get.inputSchema.hcursor.downField("properties")
          .downField("reference").get[String]("type") shouldBe Right("string")
        val properties = decimal.get.inputSchema.hcursor.downField("properties")
        properties.downField("left").get[String]("type") shouldBe Right("string")
        properties.downField("right").get[String]("type") shouldBe Right("string")
      }
    }
  }

  private def _request(
    service: String,
    operation: String,
    properties: (String, Any)*
  ): Request =
    Request.of(
      component = ToolComponent.name,
      service = service,
      operation = operation,
      properties = properties.map { case (name, value) => Property(name, value, None) }.toList
    )

  private def _execute_record_c(subsystem: Subsystem, request: Request): Consequence[Record] =
    subsystem.executeOperationResponse(request).flatMap(_record_response_c)

  private def _record_response_c(response: OperationResponse): Consequence[Record] =
    response match {
      case OperationResponse.RecordResponse(record) => Consequence.success(record)
      case other => Consequence.operationInvalid(s"expected record response but got $other")
    }

  private def _decimal_text(value: BigDecimal): String =
    value.bigDecimal.stripTrailingZeros.toPlainString
}
