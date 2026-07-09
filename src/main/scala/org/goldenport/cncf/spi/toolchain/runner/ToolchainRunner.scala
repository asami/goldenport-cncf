package org.goldenport.cncf.spi.toolchain.runner

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiSelection, SpiSocket, SpiTraceMetadata, SpiTraceSupport}

/*
 * Provider-neutral toolchain runner SPI contract.
 *
 * Toolchain-capable components publish this SPI; consumer components depend on
 * this CNCF-owned protocol instead of a concrete Textus toolchain runner
 * generated operation API.
 *
 * @since   Jul.  3, 2026
 * @version Jul.  9, 2026
 * @author  ASAMI, Tomoharu
 */
trait ToolchainRunner {
  def convertSvgToPdf(req: ConvertSvgToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse]
  def convertSvgPagesToPdf(req: ConvertSvgPagesToPdfRequest)(using ExecutionContext): Consequence[ToolchainArtifactResponse]
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
  }

  private def _attributes(response: ToolchainArtifactResponse): Map[String, String] =
    Map(
      "result_type" -> "toolchain_artifact_response",
      "valid" -> response.valid.toString,
      "error_count" -> response.errorCount.toString,
      "warning_count" -> response.warningCount.toString,
      "page_count" -> response.pageCount.toString
    )
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
