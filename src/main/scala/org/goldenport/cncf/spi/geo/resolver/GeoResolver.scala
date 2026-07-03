package org.goldenport.cncf.spi.geo.resolver

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{SpiContract, SpiSelection, SpiSocket}

/*
 * Provider-neutral geographic resolver SPI contract.
 *
 * GeoResolver-capable components publish this SPI; consumer components depend
 * only on this CNCF-owned protocol and do not call Textus GeoResolver generated
 * operation classes directly.
 *
 * @since   Jul.  3, 2026
 * @version Jul.  3, 2026
 * @author  ASAMI, Tomoharu
 */
trait GeoResolver {
  def resolveRoute(req: GeoResolveRouteRequest)(using ExecutionContext): Consequence[GeoResolveRouteResponse]
  def buildMapContext(req: GeoBuildMapContextRequest)(using ExecutionContext): Consequence[GeoBuildMapContextResponse]
  def investigateLocation(req: GeoInvestigateLocationRequest)(using ExecutionContext): Consequence[GeoInvestigateLocationResponse]
  def researchLinearFeatureRoute(req: GeoResearchLinearFeatureRouteRequest)(using ExecutionContext): Consequence[GeoResearchLinearFeatureRouteResponse]
}

trait GeoResolverSocket extends SpiSocket[GeoResolver] {
  private var _geo_resolver: Option[GeoResolver] = None

  def geoResolver: GeoResolver =
    _geo_resolver.getOrElse(
      throw new IllegalStateException("GeoResolver SPI is not installed.")
    )

  def withGeoResolver(spi: GeoResolver): this.type = {
    _geo_resolver = Some(spi)
    this
  }

  override def spiContract: SpiContract[GeoResolver] =
    SpiContract("geo-resolver", classOf[GeoResolver])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _geo_resolver.nonEmpty

  override def installSpi(spi: GeoResolver): Unit =
    withGeoResolver(spi)
}

final case class GeoResolveRouteRequest(
  routeDsl: String,
  routeFormat: Option[String] = None,
  provider: Option[String] = None,
  area: Option[String] = None,
  apiKey: Option[String] = None,
  routeFallback: Option[String] = None,
  gazetteer: Option[String] = None,
  out: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoResolveRouteResponse(
  valid: Boolean,
  errorCount: Int,
  warningCount: Int,
  message: String,
  out: Option[String],
  unresolvedCount: Int,
  routeDsl: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoBuildMapContextRequest(
  routeDsl: String,
  routeFormat: Option[String] = None,
  provider: Option[String] = None,
  area: Option[String] = None,
  apiKey: Option[String] = None,
  width: Option[Int] = None,
  height: Option[Int] = None,
  landmarks: Option[String] = None,
  out: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoBuildMapContextResponse(
  valid: Boolean,
  errorCount: Int,
  warningCount: Int,
  message: String,
  out: Option[String],
  layerCount: Int,
  landmarkCount: Int,
  backgroundSource: String,
  mapContextDsl: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoInvestigateLocationRequest(
  name: String,
  area: Option[String] = None,
  expectedKind: Option[String] = None,
  currentLat: Option[Double] = None,
  currentLon: Option[Double] = None,
  issue: Option[String] = None,
  context: Option[String] = None,
  provider: Option[String] = None,
  mode: Option[String] = None,
  engine: Option[String] = None,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = None,
  out: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoInvestigateLocationResponse(
  valid: Boolean,
  errorCount: Int,
  warningCount: Int,
  message: String,
  out: Option[String],
  investigation: String,
  prompt: String,
  model: Option[String],
  metadata: Map[String, String] = Map.empty
)

final case class GeoResearchLinearFeatureRouteRequest(
  name: String,
  area: Option[String] = None,
  from: Option[String] = None,
  to: Option[String] = None,
  role: Option[String] = None,
  context: Option[String] = None,
  routeDsl: Option[String] = None,
  provider: Option[String] = None,
  mode: Option[String] = None,
  engine: Option[String] = None,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = None,
  out: Option[String] = None,
  metadata: Map[String, String] = Map.empty
)

final case class GeoResearchLinearFeatureRouteResponse(
  valid: Boolean,
  errorCount: Int,
  warningCount: Int,
  message: String,
  out: Option[String],
  routePatch: String,
  prompt: String,
  model: Option[String],
  metadata: Map[String, String] = Map.empty
)
