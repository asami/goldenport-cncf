package org.goldenport.cncf.mcp.client

import java.nio.file.Files

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.io.RecordSourceLoader
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for CNCF-owned Codex MCP activation policy.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class CodexMcpRuntimeConfigurationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Codex MCP runtime configuration" should {
    "combine a generic TOML definition source with a separate CNCF policy overlay" in {
      Given("a Codex TOML source and adjacent CNCF YAML policy selecting one URL server")
      given ExecutionContext = ExecutionContext.create()
      val directory = Files.createTempDirectory("cncf-codex-mcp-policy")
      val source = directory.resolve("config.toml")
      Files.writeString(source,
        """[mcp_servers.research_service]
          |url = "https://mcp.example.test/tools"
          |
          |[mcp_servers.local_shell]
          |command = "unsafe"
          |""".stripMargin
      )
      val policyfile = directory.resolve("mcp-client.yaml")
      Files.writeString(policyfile,
        """source:
          |  kind: codex
          |  path: config.toml
          |serverSet:
          |  id: research
          |  limits:
          |    timeoutMillis: 12000
          |    maximumCalls: 3
          |    maximumInputBytes: 4096
          |    maximumOutputBytes: 8192
          |    maximumConcurrency: 1
          |  servers:
          |    - sourceName: research_service
          |      admittedTools:
          |        - paper.search
          |""".stripMargin
      )

      When("the runtime loads policy and source through generic Record loaders")
      val configurationresult = CodexMcpRuntimeConfiguration.loadC(policyfile)
      withClue(configurationresult.display) {
        configurationresult.isSuccess shouldBe true
      }
      val configuration = configurationresult.toOption.get
      val sourcerecord = RecordSourceLoader.load(configuration.definitionSource).toOption.get
      val assembly = CodexMcpRuntimeAssembly.createC(sourcerecord, configuration.policy)

      Then("only the CNCF-selected normalized server set is activated")
      configuration.definitionSource shouldBe source
      configuration.policy.limits.timeoutMillis shouldBe 12000L
      assembly.toOption.map(_.serverSetIds.map(_.print)) shouldBe Some(Vector("research"))
      assembly.toOption.foreach(_.close())
    }

    "reject missing tool policy and unsupported source kinds before reading definitions" in {
      Given("policy records without an exact allowlist and with a non-Codex source kind")
      val origin = Files.createTempFile("cncf-mcp-policy", ".yaml")
      val missingtools = org.goldenport.record.Record.dataAuto(
        "source" -> org.goldenport.record.Record.dataAuto("kind" -> "codex", "path" -> "config.toml"),
        "serverSet" -> org.goldenport.record.Record.dataAuto(
          "id" -> "research",
          "servers" -> Vector(org.goldenport.record.Record.dataAuto("sourceName" -> "selected"))
        )
      )
      val unsupported = missingtools.upsertSingle(
        "source",
        org.goldenport.record.Record.dataAuto(
          "kind" -> "native",
          "path" -> "config.toml"
        )
      )

      When("each CNCF policy is decoded")
      val missingresult = CodexMcpRuntimeConfiguration.decodeC(missingtools, origin)
      val unsupportedresult = CodexMcpRuntimeConfiguration.decodeC(unsupported, origin)

      Then("both fail before any external definition is loaded")
      missingresult.isFaillure shouldBe true
      unsupportedresult.isFaillure shouldBe true
    }

    "reject malformed limits instead of replacing them with defaults" in {
      Given("a CNCF MCP policy containing a fractional call limit")
      val origin = Files.createTempFile("cncf-mcp-policy", ".yaml")
      val policy = org.goldenport.record.Record.dataAuto(
        "source" -> org.goldenport.record.Record.dataAuto("kind" -> "codex", "path" -> "config.toml"),
        "serverSet" -> org.goldenport.record.Record.dataAuto(
          "id" -> "research",
          "limits" -> org.goldenport.record.Record.dataAuto("maximumCalls" -> BigDecimal("1.5")),
          "servers" -> Vector(org.goldenport.record.Record.dataAuto(
            "sourceName" -> "selected",
            "admittedTools" -> Vector("paper.search")
          ))
        )
      )

      When("the operator policy is decoded")
      val result = CodexMcpRuntimeConfiguration.decodeC(policy, origin)

      Then("configuration loading fails deterministically")
      result.isFaillure shouldBe true
    }
  }
}
