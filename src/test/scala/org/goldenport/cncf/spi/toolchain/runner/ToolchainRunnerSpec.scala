/*
 * @since   Jul. 12, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
package org.goldenport.cncf.spi.toolchain.runner

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.SpiTraceMetadata
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ToolchainRunnerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ToolchainRunner.renderWebPage" should {
    "delegate through the traced provider without exposing rendered content" in {
      Given("a deterministic browser rendering provider and enabled CNCF CallTree")
      val html = "<html><body>confidential rendered content</body></html>"
      val requesturl = "https://museum.example/private/source?token=secret"
      val provider = ToolchainRunner.traced(
        BrowserRenderer(html),
        SpiTraceMetadata(
          contract = "toolchain-runner",
          operation = "",
          socketComponent = "textus-scraper",
          providerComponent = "textus-toolchain-runner"
        )
      )
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)

      When("the consumer requests one dynamic page render")
      val result = provider.renderWebPage(RenderWebPageRequest(requesturl, Some("networkidle")))

      Then("the provider response is preserved")
      result.toOption.map(_.html) shouldBe Some(html)

      And("CallTree records only safe browser metadata")
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().getOrElse(fail("calltree missing")).toRecord.print
      calltree should include("spi:toolchain-runner.renderWebPage")
      calltree should include("final_host=museum.example")
      calltree should include("engine=playwright")
      calltree should include("browser=chromium")
      calltree should not include html
      calltree should not include "token=secret"
    }

    "return a structured unsupported result from a provider without browser capability" in {
      Given("a legacy toolchain provider implementing only artifact conversion")
      val provider = ArtifactOnlyRunner()
      given ExecutionContext = ExecutionContext.create()

      When("dynamic rendering is requested")
      val result = provider.renderWebPage(RenderWebPageRequest("https://museum.example/"))

      Then("the default SPI operation fails deterministically")
      result shouldBe a[Consequence.Failure[_]]
      result.asInstanceOf[Consequence.Failure[_]].conclusion.display should include("not-implemented")
    }

    "preserve the positional metadata argument while adding browser controls" in {
      Given("a legacy source call that passes metadata as the fifth argument")
      val metadata = Map("trace" -> "legacy-positional")

      When("the request is constructed through the compatible public signature")
      val request = RenderWebPageRequest(
        "https://museum.example/",
        Some("load"),
        None,
        Some(30),
        metadata
      )

      Then("metadata keeps its original position and new controls retain defaults")
      request.metadata shouldBe metadata
      request.userAgent shouldBe None
      request.browserProfile shouldBe None
    }
  }

  private final case class BrowserRenderer(html: String) extends ToolchainRunner {
    def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.notImplemented("unused")

    def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.notImplemented("unused")

    override def renderWebPage(req: RenderWebPageRequest)(using ExecutionContext): Consequence[RenderedWebPageResponse] =
      Consequence.success(RenderedWebPageResponse(
        requestedUrl = req.url,
        finalUrl = "https://museum.example/rendered",
        status = Some(200),
        title = Some("Museum"),
        html = html,
        renderedAt = "2026-07-12T00:00:00Z",
        engine = "playwright",
        browser = "chromium"
      ))
  }

  private final case class ArtifactOnlyRunner() extends ToolchainRunner {
    def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.notImplemented("unused")

    def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      Consequence.notImplemented("unused")
  }
}
