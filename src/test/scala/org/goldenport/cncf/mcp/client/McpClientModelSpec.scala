package org.goldenport.cncf.mcp.client

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the provider-neutral MCP client model.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class McpClientModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _logical_names =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(24))

  "MCP client model" should {
    "preserve logical identities without exposing infrastructure selectors" in {
      Given("arbitrary lowercase logical names and infrastructure-shaped invalid names")
      val property = Prop.forAll(_logical_names) { value =>
        McpServerSetId.parseC(value.toUpperCase(java.util.Locale.ROOT)).toOption.exists(_.print == value) &&
          McpServerId.parseC(value.toUpperCase(java.util.Locale.ROOT)).toOption.exists(_.print == value)
      }
      val invalid = Vector("", "https://mcp.example", "../server", "server set", "server_header")

      When("server-set and server identities cross the typed boundary")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      val rejected = invalid.map(McpServerSetId.parseC)

      Then("only bounded logical identity remains")
      checked.passed shouldBe true
      rejected.forall(_.isFaillure) shouldBe true
    }

    "normalize one admitted server set and tool catalog deterministically" in {
      Given("two admitted logical servers and tools declared in reverse order")
      val alpha = _server_id("alpha")
      val beta = _server_id("beta")
      val serverset = McpClientServerSet.createC(
        _server_set_id("research"),
        Vector(McpClientServer(beta), McpClientServer(alpha))
      ).toOption.get
      val tools = Vector(
        _tool(beta, "search.web"),
        _tool(alpha, "library.lookup")
      )

      When("the runtime publishes the admitted provider-neutral catalog")
      val catalog = McpClientCatalog.createC(serverset, tools)

      Then("server and tool identities are stable and sorted without endpoint or transport data")
      catalog.toOption.map(_.serverSet.servers.map(_.id.print)) shouldBe Some(Vector("alpha", "beta"))
      catalog.toOption.map(_.tools.map(_.identity.print)) shouldBe
        Some(Vector("alpha/library.lookup", "beta/search.web"))
    }

    "reject duplicate and foreign tool identities before transport execution" in {
      Given("one admitted server plus duplicate and foreign tool entries")
      val admitted = _server_id("admitted")
      val foreign = _server_id("foreign")
      val serverset = McpClientServerSet.createC(
        _server_set_id("safe"),
        Vector(McpClientServer(admitted))
      ).toOption.get
      val tool = _tool(admitted, "catalog.read")

      When("invalid catalogs are normalized")
      val duplicate = McpClientCatalog.createC(serverset, Vector(tool, tool))
      val outside = McpClientCatalog.createC(serverset, Vector(_tool(foreign, "catalog.read")))

      Then("both fail as structured admission values before a client Port can use them")
      duplicate.isFaillure shouldBe true
      outside.isFaillure shouldBe true
    }

    "represent recursive input and invocation values without JSON wire records" in {
      Given("a nested typed schema and provider-neutral call arguments")
      val queryname = _field_name("query")
      val tagsname = _field_name("tags")
      val fields = Vector(
        McpInputField.createC(queryname, McpInputSchema.StringValue, required = true).toOption.get,
        McpInputField.createC(
          tagsname,
          McpInputSchema.array(McpInputSchema.StringValue)
        ).toOption.get
      )
      val schema = McpInputSchema.objectC(fields)
      val arguments = McpValue.objectC(Vector(
        queryname -> McpValue.StringValue("semantic runtime"),
        tagsname -> McpValue.ArrayValue(Vector(McpValue.StringValue("cncf")))
      ))

      When("a typed call is built from admitted identity and object arguments")
      val call = arguments.flatMap(x => McpClientCall.createC(
        McpToolIdentity(_server_id("search"), _tool_name("query")),
        x
      ))

      Then("schema and values retain their kinds and deterministic field order")
      schema.toOption.map(_.kind) shouldBe Some(McpValueKind.ObjectValue)
      call.toOption.map(_.arguments.fields.map(_._1.print)) shouldBe Some(Vector("query", "tags"))
      call.toOption.flatMap(_.arguments.get(queryname)).map(_.kind) shouldBe Some(McpValueKind.StringValue)
    }

    "admit only positive bounded execution limits for every generated valid tuple" in {
      Given("arbitrary positive timeout call byte and concurrency limits")
      val positive = Gen.chooseNum(1, 1000000)
      val property = Prop.forAll(positive, positive, positive, positive, positive) {
        (timeout, calls, inputbytes, outputbytes, concurrency) =>
          McpClientLimits.createC(timeout.toLong, calls, inputbytes.toLong, outputbytes.toLong, concurrency)
            .toOption
            .exists(x =>
              x.timeoutMillis == timeout.toLong &&
                x.maximumCalls == calls &&
                x.maximumInputBytes == inputbytes.toLong &&
                x.maximumOutputBytes == outputbytes.toLong &&
                x.maximumConcurrency == concurrency
            )
      }

      When("the runtime validates generated and zero-valued limits")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(64), property)
      val rejected = McpClientLimits.createC(0L, 1, 1L, 1L, 1)

      Then("every positive tuple survives exactly and a non-positive member fails")
      checked.passed shouldBe true
      rejected.isFaillure shouldBe true
    }

    "keep diagnostic metadata bounded and pre-redacted" in {
      Given("one logical reason plus safe and raw multiline diagnostic summaries")
      val reason = McpClientDiagnosticReason.parseC("transport.unavailable")
      val safe = McpClientDiagnosticSummary.fromSafeTextC("remote transport unavailable")
      val raw = McpClientDiagnosticSummary.fromSafeTextC("Authorization: secret\nraw body")

      When("diagnostic metadata is admitted")
      val diagnostic = for {
        r <- reason
        s <- safe
      } yield McpClientDiagnostic(McpClientDiagnosticKind.Transport, r, Some(s))

      Then("safe classification remains typed and raw transport text is rejected")
      diagnostic.toOption.map(_.kind) shouldBe Some(McpClientDiagnosticKind.Transport)
      diagnostic.toOption.map(_.reason.print) shouldBe Some("transport.unavailable")
      raw.isFaillure shouldBe true
    }
  }

  private def _server_set_id(value: String): McpServerSetId =
    McpServerSetId.parseC(value).toOption.get

  private def _server_id(value: String): McpServerId =
    McpServerId.parseC(value).toOption.get

  private def _tool_name(value: String): McpToolName =
    McpToolName.parseC(value).toOption.get

  private def _field_name(value: String): McpFieldName =
    McpFieldName.parseC(value).toOption.get

  private def _tool(
    serverid: McpServerId,
    name: String
  ): McpClientTool =
    McpClientTool.createC(
      McpToolIdentity(serverid, _tool_name(name)),
      McpInputSchema.objectC(Vector.empty).toOption.get
    ).toOption.get
}
