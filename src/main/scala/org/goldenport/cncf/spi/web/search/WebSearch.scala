package org.goldenport.cncf.spi.web.search

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiSelection, SpiSocket}

/*
 * Provider-neutral Web search SPI contract.
 *
 * Search provider selection and credentials remain runtime-owned. Consumers
 * submit only a bounded logical query and result-count request.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
trait WebSearch {
  def search(req: WebSearchRequest)(using ExecutionContext): Consequence[WebSearchResponse]
}

trait WebSearchSocket extends SpiSocket[WebSearch] {
  private var _web_search: Option[WebSearch] = None

  def webSearchC: Consequence[WebSearch] =
    _web_search match {
      case Some(search) => Consequence.success(search)
      case None => Consequence.serviceUnavailable("Web search SPI is not installed")
    }

  def withWebSearch(spi: WebSearch): this.type = {
    _web_search = Some(spi)
    this
  }

  override def spiContract: SpiContract[WebSearch] =
    SpiContract("web-search", classOf[WebSearch])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _web_search.nonEmpty

  override def installSpi(spi: WebSearch): Unit =
    withWebSearch(spi)
}

final case class WebSearchRequest(
  query: String,
  limit: Int
)

final case class WebSearchResponse(
  items: Vector[WebSearchItem]
)

final case class WebSearchItem(
  title: String,
  url: String,
  snippet: Option[String] = None
)
