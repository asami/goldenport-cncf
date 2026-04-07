package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ServiceContract[S](
  name: String,
  runtimeClass: Class[? <: S]
)

trait PortApi[-Req, S]:
  def resolve(req: Req): Consequence[ServiceContract[S]]

final case class VariationSelection(
  provider: Option[String] = None,
  mode: Option[String] = None,
  engine: Option[String] = None
)

trait PortVariationPoint[Req]:
  def current(req: Req)(using ExecutionContext): Consequence[VariationSelection]
  def inject(
    req: Req,
    selection: VariationSelection
  )(using ExecutionContext): Consequence[Req]

trait VariationPoint[Req] extends PortVariationPoint[Req]

trait ExtensionPoint[S]:
  def supports(
    contract: ServiceContract[S],
    variation: VariationSelection
  )(using ExecutionContext): Boolean

  def provide(
    contract: ServiceContract[S],
    variation: VariationSelection
  )(using ExecutionContext): Consequence[S]

final case class Port[Req, S](
  api: PortApi[Req, S],
  spi: Vector[ExtensionPoint[S]],
  variation: VariationPoint[Req]
)
