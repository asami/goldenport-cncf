package org.goldenport.cncf.unitofwork

import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.{EntityAbacCondition, EntityAccessMode, EntityAccessRelation, EntityApplicationDomain, EntityOperationKind, ServiceOperationModel}

/*
 * @since   Apr.  7, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class UnitOfWorkAuthorization(
  resourceFamily: String,
  resourceType: Option[String] = None,
  collectionName: Option[String] = None,
  targetId: Option[EntityId] = None,
  accessKind: String,
  access: Option[CmlOperationAccess] = None,
  sourceComponentName: Option[String] = None,
  targetComponentName: Option[String] = None,
  entityNames: Vector[String] = Vector.empty,
  accessMode: EntityAccessMode = EntityAccessMode.UserPermission,
  operationModel: Option[ServiceOperationModel] = None,
  entityOperationKind: Option[EntityOperationKind] = None,
  entityApplicationDomain: Option[EntityApplicationDomain] = None,
  relationRules: Vector[EntityAccessRelation] = Vector.empty,
  naturalConditions: Vector[EntityAbacCondition] = Vector.empty
)
