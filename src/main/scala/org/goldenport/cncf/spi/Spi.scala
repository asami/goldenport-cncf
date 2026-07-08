package org.goldenport.cncf.spi

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ExtensionPoint, Port, PortApi, ServiceContract, VariationPoint, VariationSelection}
import org.goldenport.cncf.context.ExecutionContext

/*
 * CNCF canonical SPI vocabulary.
 *
 * The current implementation intentionally aliases the pre-existing
 * component.Port binding vocabulary so existing Port-based providers remain
 * source compatible while new code can import org.goldenport.cncf.spi.*.
 *
 * @since   Jul.  2, 2026
 * @version Jul.  8, 2026
 * @author  ASAMI, Tomoharu
 */
type SpiContract[S] = ServiceContract[S]
object SpiContract {
  def apply[S](
    name: String,
    runtimeclass: Class[? <: S]
  ): SpiContract[S] =
    ServiceContract(name, runtimeclass)

  def unapply[S](p: SpiContract[S]): Option[(String, Class[? <: S])] =
    Some((p.name, p.runtimeClass))
}

type SpiPortApi[-Req, S] = PortApi[Req, S]
type SpiSelection = VariationSelection
object SpiSelection {
  def apply(
    provider: Option[String] = None,
    mode: Option[String] = None,
    engine: Option[String] = None
  ): SpiSelection =
    VariationSelection(provider, mode, engine)

  def unapply(p: SpiSelection): Option[(Option[String], Option[String], Option[String])] =
    Some((p.provider, p.mode, p.engine))
}

type SpiSelectionPoint[Req] = VariationPoint[Req]
type SpiProvider[S] = ExtensionPoint[S]
type SpiPort[Req, S] = Port[Req, S]
object SpiPort {
  def apply[Req, S](
    api: SpiPortApi[Req, S],
    providers: Vector[SpiProvider[S]],
    selectionpoint: SpiSelectionPoint[Req]
  ): SpiPort[Req, S] =
    Port(api, providers, selectionpoint)
}

trait SpiProviderComponent {
  def spiProviders: Vector[SpiProvider[?]]
}

trait SpiSocket[S] {
  def spiContract: SpiContract[S]
  def spiSelection: SpiSelection = SpiSelection()
  def isSpiInstalled: Boolean = false
  def installSpi(spi: S): Unit
}

final case class SpiSocketBinding[S](
  socket: SpiSocket[S],
  provider: SpiProvider[S],
  contract: SpiContract[S],
  selection: SpiSelection
) {
  def install()(using ExecutionContext): Consequence[Unit] =
    provider.provide(contract, selection).map { spi =>
      socket.installSpi(spi)
    }
}

final case class SpiProviderSelector(
  component: Option[String] = None,
  service: Option[String] = None
)

final case class SpiSocketSelector(
  component: Option[String] = None,
  contract: String
)

final case class SpiRuntimeBinding(
  socket: SpiSocketSelector,
  provider: SpiProviderSelector,
  selection: SpiSelection = SpiSelection()
)
