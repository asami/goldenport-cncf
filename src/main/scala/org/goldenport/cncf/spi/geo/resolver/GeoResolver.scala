package org.goldenport.cncf.spi.geo.resolver

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{ComponentSelector, SpiContract, SpiSelection, SpiSocket, SpiTraceMetadata, SpiTraceSupport, StandardSpiSocketSet}

/*
 * Provider-neutral geographic resolver SPI contract.
 *
 * GeoResolver-capable components publish this SPI; consumer components depend
 * only on this CNCF-owned protocol and do not call Textus GeoResolver generated
 * operation classes directly.
 *
 * @since   Jul.  3, 2026
 * @version Jul. 11, 2026
 * @author  ASAMI, Tomoharu
 */
trait GeoResolver {
  def resolveRoute(req: GeoResolveRouteRequest)(using ExecutionContext): Consequence[GeoResolveRouteResponse]
  def buildMapContext(req: GeoBuildMapContextRequest)(using ExecutionContext): Consequence[GeoBuildMapContextResponse]
  def investigateLocation(req: GeoInvestigateLocationRequest)(using ExecutionContext): Consequence[GeoInvestigateLocationResponse]
  def researchLinearFeatureRoute(req: GeoResearchLinearFeatureRouteRequest)(using ExecutionContext): Consequence[GeoResearchLinearFeatureRouteResponse]
}

object GeoResolver {
  def traced(
    underlying: GeoResolver,
    metadata: SpiTraceMetadata
  ): GeoResolver =
    _TracedGeoResolver(underlying, metadata)

  private final case class _TracedGeoResolver(
    underlying: GeoResolver,
    base: SpiTraceMetadata
  ) extends GeoResolver {
    def resolveRoute(req: GeoResolveRouteRequest)(using ExecutionContext): Consequence[GeoResolveRouteResponse] =
      SpiTraceSupport.trace(base.withOperation("resolveRoute"), (x: GeoResolveRouteResponse) => _attributes(x))(underlying.resolveRoute(req))

    def buildMapContext(req: GeoBuildMapContextRequest)(using ExecutionContext): Consequence[GeoBuildMapContextResponse] =
      SpiTraceSupport.trace(base.withOperation("buildMapContext"), (x: GeoBuildMapContextResponse) => _attributes(x))(underlying.buildMapContext(req))

    def investigateLocation(req: GeoInvestigateLocationRequest)(using ExecutionContext): Consequence[GeoInvestigateLocationResponse] =
      SpiTraceSupport.trace(base.withOperation("investigateLocation"), (x: GeoInvestigateLocationResponse) => _attributes(x))(underlying.investigateLocation(req))

    def researchLinearFeatureRoute(req: GeoResearchLinearFeatureRouteRequest)(using ExecutionContext): Consequence[GeoResearchLinearFeatureRouteResponse] =
      SpiTraceSupport.trace(base.withOperation("researchLinearFeatureRoute"), (x: GeoResearchLinearFeatureRouteResponse) => _attributes(x))(underlying.researchLinearFeatureRoute(req))
  }

  private def _attributes(response: GeoResolveRouteResponse): Map[String, String] =
    _status_attributes("geo_resolve_route_response", response.valid, response.errorCount, response.warningCount)

  private def _attributes(response: GeoBuildMapContextResponse): Map[String, String] =
    _status_attributes("geo_build_map_context_response", response.valid, response.errorCount, response.warningCount) ++
      Map("layer_count" -> response.layerCount.toString, "landmark_count" -> response.landmarkCount.toString)

  private def _attributes(response: GeoInvestigateLocationResponse): Map[String, String] =
    _status_attributes("geo_investigate_location_response", response.valid, response.errorCount, response.warningCount) ++
      response.model.map("model" -> _).toMap

  private def _attributes(response: GeoResearchLinearFeatureRouteResponse): Map[String, String] =
    _status_attributes("geo_research_linear_feature_route_response", response.valid, response.errorCount, response.warningCount) ++
      response.model.map("model" -> _).toMap

  private def _status_attributes(
    resultType: String,
    valid: Boolean,
    errorCount: Int,
    warningCount: Int
  ): Map[String, String] =
    Map(
      "result_type" -> resultType,
      "valid" -> valid.toString,
      "error_count" -> errorCount.toString,
      "warning_count" -> warningCount.toString
    )
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

trait GeoResolverSocketSet extends StandardSpiSocketSet[GeoResolver] {
  def geoResolver(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[GeoResolver] =
    resolve(selector)

  override def spiContract: SpiContract[GeoResolver] =
    SpiContract("geo-resolver", classOf[GeoResolver])
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
