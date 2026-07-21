package org.goldenport.cncf.mcp.client

import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Codex MCP definition import.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class CodexMcpDefinitionImporterSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Codex MCP definition import" should {
    "normalize only selected URL definitions under a CNCF policy overlay" in {
      Given("Codex URL and stdio definitions plus an explicit CNCF allowlist and limits overlay")
      val source = _source(
        "Textus_SIE" -> Record.dataAuto(
          "url" -> "https://mcp.example.test/tools",
          "startup_timeout_sec" -> 10
        ),
        "local-shell" -> Record.dataAuto(
          "command" -> "/bin/echo",
          "args" -> Vector("unsafe")
        )
      )
      val policy = _policy("Textus_SIE", Vector("paper.search", "book.lookup"))

      When("the isolated adapter imports the selected definition")
      val result = CodexMcpDefinitionImporter.importC(source, policy)

      Then("only normalized CNCF client and Streamable HTTP policy values remain")
      result.toOption.map(_.serverSet.id.print) shouldBe Some("research")
      result.toOption.map(_.serverSet.servers.map(_.id.print)) shouldBe Some(Vector("textus-sie"))
      result.toOption.map(_.serverSet.servers.flatMap(_.admittedToolNames.map(_.print))) shouldBe
        Some(Vector("book.lookup", "paper.search"))
      result.toOption.map(_.transportConfig.servers.map(_.endpoint.toString)) shouldBe
        Some(Vector("https://mcp.example.test/tools"))
    }

    "reject selected stdio header credential and unsupported transport authority" in {
      Given("selected Codex definitions carrying infrastructure authority outside the CNCF overlay")
      val definitions = Vector(
        Record.dataAuto("command" -> "server", "args" -> Vector("--stdio")),
        Record.dataAuto("url" -> "https://mcp.example.test", "http_headers" -> Record.dataAuto("X-Key" -> "secret")),
        Record.dataAuto("url" -> "https://mcp.example.test", "bearer_token_env_var" -> "TOKEN"),
        Record.dataAuto("url" -> "https://mcp.example.test", "transport" -> "websocket")
      )

      When("each selected definition is imported")
      val results = definitions.map { definition =>
        CodexMcpDefinitionImporter.importC(
          _source("selected" -> definition),
          _policy("selected", Vector("safe.read"))
        )
      }

      Then("every unsafe definition fails before producing transport configuration")
      results.forall(_.isFaillure) shouldBe true
    }

    "require an exact non-empty CNCF tool policy independently of source metadata" in {
      Given("a source definition that advertises tools without a CNCF allowlist")

      When("the CNCF import policy is constructed without admitted tool names")
      val result = CodexMcpServerImportPolicy.createC("selected", Vector.empty)

      Then("policy construction fails before the source can publish any tool")
      result.isFaillure shouldBe true
    }

    "reject duplicate entries in the exact CNCF tool allowlist" in {
      Given("an operator policy that repeats the same admitted tool")

      When("the CNCF import policy is constructed")
      val result = CodexMcpServerImportPolicy.createC(
        "selected",
        Vector("safe.read", "safe.read")
      )

      Then("policy construction fails rather than silently normalizing operator input")
      result.isFaillure shouldBe true
    }

    "leave malformed unselected source definitions uninterpreted" in {
      Given("one selected URL definition and one malformed unselected definition")
      val source = Record.dataAuto(
        "mcp_servers" -> Record.dataAuto(
          "selected" -> Record.dataAuto("url" -> "https://mcp.example.test/tools"),
          "unselected" -> "not-a-definition-record"
        )
      )

      When("the importer reads only the operator-selected source")
      val result = CodexMcpDefinitionImporter.importC(
        source,
        _policy("selected", Vector("safe.read"))
      )

      Then("the malformed unselected value does not affect the imported server set")
      result.toOption.map(_.serverSet.servers.map(_.id.print)) shouldBe Some(Vector("selected"))
    }

    "reject source names that collide after deterministic logical normalization" in {
      Given("generated source-name separator variants that normalize to one server identity")
      val suffixes = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(16))
      val property = Prop.forAll(suffixes) { suffix =>
        val source = _source(
          s"alpha_$suffix" -> Record.dataAuto("url" -> "https://one.example.test/mcp"),
          s"alpha-$suffix" -> Record.dataAuto("url" -> "https://two.example.test/mcp")
        )
        val first = CodexMcpServerImportPolicy.createC(s"alpha_$suffix", Vector("first.read")).toOption.get
        val second = CodexMcpServerImportPolicy.createC(s"alpha-$suffix", Vector("second.read")).toOption.get
        val policy = CodexMcpImportPolicy.createC(
          McpServerSetId.parseC("collision").toOption.get,
          Vector(first, second),
          McpClientLimits.default
        ).toOption.get
        CodexMcpDefinitionImporter.importC(source, policy).isFaillure
      }

      When("normalization collision behavior is checked across generated names")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("every collision is rejected deterministically")
      checked.passed shouldBe true
    }
  }

  private def _source(
    definitions: (String, Record)*
  ): Record =
    Record.dataAuto("mcp_servers" -> Record.create(definitions))

  private def _policy(
    sourcename: String,
    tools: Vector[String]
  ): CodexMcpImportPolicy = {
    val server = CodexMcpServerImportPolicy.createC(sourcename, tools).toOption.get
    CodexMcpImportPolicy.createC(
      McpServerSetId.parseC("research").toOption.get,
      Vector(server),
      McpClientLimits.createC(15000L, 8, 4096L, 8192L, 2).toOption.get
    ).toOption.get
  }
}
