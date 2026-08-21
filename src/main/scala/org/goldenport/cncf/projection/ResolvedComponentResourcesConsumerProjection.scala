package org.goldenport.cncf.projection

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentSubcomponentComposition}
import org.goldenport.cncf.component.repository._

/*
 * Read-only consumer projection for resolved component-resource evidence.
 *
 * @since   Aug. 22, 2026
 * @version Aug. 22, 2026
 * @author  ASAMI, Tomoharu
 */
enum ComponentResourceConsumer:
  case Help, Admin

final case class ResolvedComponentResourceConsumerView(
  componentId: ComponentId,
  logicalRelease: String,
  parentComponentId: Option[ComponentId],
  childRole: String,
  logicalResource: String,
  availability: ComponentResourceAvailability,
  integrity: ComponentResourceIntegrity,
  authorization: ComponentResourceAuthorization,
  sourceKind: ComponentResourceSourceKind,
  artifactCoordinate: String,
  sha256: String,
  logicalSource: String,
  resolutionStep: String,
  externalDeploymentRequired: Boolean
)

final case class ResolvedComponentResourcesConsumerInventory(
  consumer: ComponentResourceConsumer,
  subsystemIdentity: String,
  resources: Vector[ResolvedComponentResourceConsumerView]
)

final case class ResolvedComponentResourceConsumerAccess(
  consumer: ComponentResourceConsumer,
  disposition: ComponentResourceAccessDisposition,
  content: Option[Array[Byte]],
  diagnostic: ComponentResourceAccessDiagnostic
)

object ResolvedComponentResourcesConsumerProjection:
  def inventory(
    resources: ResolvedComponentResources,
    consumer: ComponentResourceConsumer,
    subsystemIdentity: String
  ): ResolvedComponentResourcesConsumerInventory =
    ResolvedComponentResourcesConsumerInventory(
      consumer,
      subsystemIdentity,
      resources.resources.map { resource =>
        ResolvedComponentResourceConsumerView(
          componentId = resource.logicalIdentity.componentId,
          logicalRelease = resource.logicalIdentity.logicalRelease,
          parentComponentId = resource.logicalIdentity.parentComponentId,
          childRole = resource.logicalIdentity.childRole,
          logicalResource = resource.logicalIdentity.logicalResource,
          availability = resource.availability,
          integrity = resource.integrity,
          authorization = resource.authorization,
          sourceKind = resource.provenance.sourceKind,
          artifactCoordinate = resource.provenance.artifactCoordinate,
          sha256 = resource.provenance.sha256,
          logicalSource = resource.provenance.logicalSource,
          resolutionStep = resource.provenance.resolutionStep,
          externalDeploymentRequired = resource.provenance.externalDeploymentRequired
        )
      }
    )

  def access(
    composition: ComponentSubcomponentComposition,
    request: ComponentResourceAccessRequest,
    consumer: ComponentResourceConsumer
  ): Consequence[ResolvedComponentResourceConsumerAccess] =
    ComponentResourceAuthorizationPolicy.authorizeC(composition, request).map { result =>
      ResolvedComponentResourceConsumerAccess(
        consumer,
        result.disposition,
        result.content.map(_.clone()),
        result.diagnostic
      )
    }
