package org.goldenport.cncf.security

import org.goldenport.cncf.entity.runtime.EntityKind

/*
 * Derived authorization defaults from entity usage and service operation
 * model. This is the coarse policy layer above raw ACL/relation settings.
 *
 * @since   Apr. 13, 2026
 *  version Apr. 13, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityAuthorizationProfile(
  accessMode: EntityAccessMode,
  relationRules: Vector[EntityAccessRelation] = Vector.empty
)

object EntityAuthorizationProfile {
  def derive(
    entityUsage: EntityUsageKind,
    operationModel: ServiceOperationModel,
    explicitRelations: Vector[EntityAccessRelation] = Vector.empty
  ): EntityAuthorizationProfile =
    derive(
      operationKind = entityUsage match
        case EntityUsageKind.Executable => EntityOperationKind.Task
        case _ => EntityOperationKind.Resource,
      applicationDomain = entityUsage match
        case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
        case _ => EntityApplicationDomain.Business,
      operationModel = operationModel,
      explicitRelations = explicitRelations
    )

  def derive(
    entityKind: EntityKind,
    applicationDomain: EntityApplicationDomain,
    operationModel: ServiceOperationModel,
    explicitRelations: Vector[EntityAccessRelation]
  ): EntityAuthorizationProfile =
    derive(
      operationKind = entityKind.legacyOperationKind,
      applicationDomain = applicationDomain,
      operationModel = operationModel,
      explicitRelations = explicitRelations
    )

  def derive(
    operationKind: EntityOperationKind,
    applicationDomain: EntityApplicationDomain,
    operationModel: ServiceOperationModel,
    explicitRelations: Vector[EntityAccessRelation]
  ): EntityAuthorizationProfile = {
    val mode = operationModel match
      case ServiceOperationModel.InternalService => EntityAccessMode.ServiceInternal
      case ServiceOperationModel.SystemTask => EntityAccessMode.System
      case _ => EntityAccessMode.UserPermission
    EntityAuthorizationProfile(mode, explicitRelations)
  }
}
