/*
 * @since   Jul.  3, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
package org.goldenport.cncf.spi.toolchain.runner

import java.net.URI
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{ComponentSelector, SpiContract, SpiSelection, SpiSocket, SpiTraceMetadata, SpiTraceSupport, StandardSpiSocketSet}

/*
 * Provider-neutral toolchain runner SPI contract.
 *
 * Toolchain-capable components publish this SPI; consumer components depend on
 * this CNCF-owned protocol instead of a concrete Textus toolchain runner
 * generated operation API.
 *
 * @since   Jul.  3, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
trait ToolchainRunner {
  def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse]
  def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse]
  def renderWebPage(req: RenderWebPageRequest)(using ExecutionContext): Consequence[RenderedWebPageResponse] =
    Consequence.notImplemented("ToolchainRunner.renderWebPage is not implemented by this provider")
}

object ToolchainRunner {
  def traced(
    underlying: ToolchainRunner,
    metadata: SpiTraceMetadata
  ): ToolchainRunner =
    _TracedToolchainRunner(underlying, metadata)

  private final case class _TracedToolchainRunner(
    underlying: ToolchainRunner,
    base: SpiTraceMetadata
  ) extends ToolchainRunner {
    def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      SpiTraceSupport.trace(base.withOperation("convertSvgToPdf"), _attributes)(underlying.convertSvgToPdf(req))

    def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse] =
      SpiTraceSupport.trace(base.withOperation("convertSvgPagesToPdf"), _attributes)(underlying.convertSvgPagesToPdf(req))

    override def renderWebPage(req: RenderWebPageRequest)(using ExecutionContext): Consequence[RenderedWebPageResponse] =
      SpiTraceSupport.trace(base.withOperation("renderWebPage"), _web_page_attributes)(underlying.renderWebPage(req))
  }

  private def _attributes(response: ToolchainArtifactResponse): Map[String, String] =
    Map(
      "result_type" -> "toolchain_artifact_response",
      "valid" -> response.valid.toString,
      "error_count" -> response.errorCount.toString,
      "warning_count" -> response.warningCount.toString,
      "page_count" -> response.pageCount.toString
    )

  private def _web_page_attributes(response: RenderedWebPageResponse): Map[String, String] =
    Map(
      "result_type" -> "rendered_web_page_response",
      "status" -> response.status.map(_.toString).getOrElse(""),
      "final_host" -> _host(response.finalUrl).getOrElse(""),
      "engine" -> response.engine,
      "browser" -> response.browser
    ).filter(_._2.nonEmpty)

  private def _host(url: String): Option[String] =
    try
      Option(URI.create(url).getHost).filter(_.nonEmpty)
    catch
      case _: IllegalArgumentException => None
}

trait ToolchainRunnerSocket extends SpiSocket[ToolchainRunner] {
  private var _toolchain_runner: Option[ToolchainRunner] = None

  def toolchainRunner: ToolchainRunner =
    _toolchain_runner.getOrElse(
      throw new IllegalStateException("Toolchain runner SPI is not installed.")
    )

  def withToolchainRunner(spi: ToolchainRunner): this.type = {
    _toolchain_runner = Some(spi)
    this
  }

  override def spiContract: SpiContract[ToolchainRunner] =
    SpiContract("toolchain-runner", classOf[ToolchainRunner])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _toolchain_runner.nonEmpty

  override def installSpi(spi: ToolchainRunner): Unit =
    withToolchainRunner(spi)
}

trait ToolchainRunnerSocketSet extends StandardSpiSocketSet[ToolchainRunner] {
  def toolchainRunner(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[ToolchainRunner] =
    resolve(selector)

  override def spiContract: SpiContract[ToolchainRunner] =
    SpiContract("toolchain-runner", classOf[ToolchainRunner])
}

final case class ConvertSvgToPdfRequest(
  svg: String,
  out: Option[String] = None,
  dockerImage: Option[String] = None,
  dockerCommand: Option[String] = None,
  workDir: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class ConvertSvgPagesToPdfRequest(
  svgFiles: Vector[String],
  out: Option[String] = None,
  dockerImage: Option[String] = None,
  dockerCommand: Option[String] = None,
  workDir: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class ToolchainArtifactResponse(
  valid: Boolean,
  errorCount: Int,
  warningCount: Int,
  message: String,
  out: Option[String],
  pageCount: Int,
  dockerImage: Option[String],
  metadata: Map[String, String] = Map.empty
)

final case class RenderWebPageRequest(
  url: String,
  waitUntil: Option[String] = None,
  waitForSelector: Option[String] = None,
  timeoutSeconds: Option[Int] = None,
  metadata: Map[String, String] = Map.empty,
  userAgent: Option[String] = None,
  browserProfile: Option[String] = None
)

final case class RenderedWebPageResponse(
  requestedUrl: String,
  finalUrl: String,
  status: Option[Int],
  title: Option[String],
  html: String,
  renderedAt: String,
  engine: String,
  browser: String,
  metadata: Map[String, String] = Map.empty
)
