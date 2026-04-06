package org.goldenport.cncf.unitofwork

import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.operation.CmlOperationAccess

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class UnitOfWorkAuthorization(
  resourceFamily: String,
  resourceType: Option[String] = None,
  collectionName: Option[String] = None,
  targetId: Option[EntityId] = None,
  accessKind: String,
  access: Option[CmlOperationAccess] = None,
  entityNames: Vector[String] = Vector.empty
)
