package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
object AggregateAuthorization {
  final case class Request(
    aggregateName: String,
    targetId: Option[EntityId],
    accessKind: String,
    access: Option[CmlOperationAccess] = None,
    sourceComponentName: Option[String] = None,
    targetComponentName: Option[String] = None,
    operationModel: ServiceOperationModel = ServiceOperationModel.default,
    relationRules: Vector[EntityAccessRelation] = Vector.empty,
    naturalConditions: Vector[EntityAbacCondition] = Vector.empty,
    accessMode: Option[EntityAccessMode] = None
  ) {
    def toUnitOfWorkAuthorization: UnitOfWorkAuthorization = {
      val derivedprofile = EntityAuthorizationProfile.derive(
        operationKind = EntityOperationKind.Task,
        applicationDomain = EntityApplicationDomain.default,
        operationModel = operationModel,
        explicitRelations = relationRules
      )
      UnitOfWorkAuthorization(
        resourceFamily = "aggregate",
        resourceType = Some(aggregateName),
        collectionName = targetId.map(_.collection.name).orElse(Some(aggregateName)),
        targetId = targetId,
        accessKind = accessKind,
        access = access,
        sourceComponentName = sourceComponentName,
        targetComponentName = targetComponentName,
        entityNames = Vector(aggregateName),
        accessMode = accessMode.getOrElse(derivedprofile.accessMode),
        operationModel = Some(operationModel),
        entityOperationKind = Some(EntityOperationKind.Task),
        entityApplicationDomain = Some(EntityApplicationDomain.default),
        relationRules = derivedprofile.relationRules,
        naturalConditions = naturalConditions
      )
    }
  }

  def authorize(
    request: Request,
    loadRecord: EntityId => Consequence[Option[Record]] = _ => Consequence.success(None)
  )(using ctx: ExecutionContext): Consequence[Unit] =
    OperationAccessPolicy.authorizeUnitOfWorkDefault(
      request.toUnitOfWorkAuthorization,
      loadRecord
    )

  def authorizeType(
    aggregateName: String,
    accessKind: String,
    access: Option[CmlOperationAccess] = None,
    sourceComponentName: Option[String] = None,
    targetComponentName: Option[String] = None,
    operationModel: ServiceOperationModel = ServiceOperationModel.default,
    relationRules: Vector[EntityAccessRelation] = Vector.empty,
    naturalConditions: Vector[EntityAbacCondition] = Vector.empty,
    accessMode: Option[EntityAccessMode] = None
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorize(
      Request(
        aggregateName = aggregateName,
        targetId = None,
        accessKind = accessKind,
        access = access,
        sourceComponentName = sourceComponentName,
        targetComponentName = targetComponentName,
        operationModel = operationModel,
        relationRules = relationRules,
        naturalConditions = naturalConditions,
        accessMode = accessMode
      )
    )

  def authorizeInstance(
    aggregateName: String,
    targetId: EntityId,
    accessKind: String,
    loadRecord: EntityId => Consequence[Option[Record]],
    access: Option[CmlOperationAccess] = None,
    sourceComponentName: Option[String] = None,
    targetComponentName: Option[String] = None,
    operationModel: ServiceOperationModel = ServiceOperationModel.default,
    relationRules: Vector[EntityAccessRelation] = Vector.empty,
    naturalConditions: Vector[EntityAbacCondition] = Vector.empty,
    accessMode: Option[EntityAccessMode] = None
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorize(
      Request(
        aggregateName = aggregateName,
        targetId = Some(targetId),
        accessKind = accessKind,
        access = access,
        sourceComponentName = sourceComponentName,
        targetComponentName = targetComponentName,
        operationModel = operationModel,
        relationRules = relationRules,
        naturalConditions = naturalConditions,
        accessMode = accessMode
      ),
      loadRecord
    )

  def authorizeCommand(
    aggregateName: String,
    targetId: Option[EntityId],
    commandName: String,
    loadRecord: EntityId => Consequence[Option[Record]],
    access: Option[CmlOperationAccess] = None,
    sourceComponentName: Option[String] = None,
    targetComponentName: Option[String] = None,
    operationModel: ServiceOperationModel = ServiceOperationModel.default,
    relationRules: Vector[EntityAccessRelation] = Vector.empty,
    naturalConditions: Vector[EntityAbacCondition] = Vector.empty,
    accessMode: Option[EntityAccessMode] = None
  )(using ctx: ExecutionContext): Consequence[Unit] =
    targetId match {
      case Some(id) =>
        authorizeInstance(
          aggregateName = aggregateName,
          targetId = id,
          accessKind = s"command:$commandName",
          loadRecord = loadRecord,
          access = access,
          sourceComponentName = sourceComponentName,
          targetComponentName = targetComponentName,
          operationModel = operationModel,
          relationRules = relationRules,
          naturalConditions = naturalConditions,
          accessMode = accessMode
        )
      case None =>
        authorizeType(
          aggregateName = aggregateName,
          accessKind = s"create:$commandName",
          access = access,
          sourceComponentName = sourceComponentName,
          targetComponentName = targetComponentName,
          operationModel = operationModel,
          relationRules = relationRules,
          naturalConditions = naturalConditions,
          accessMode = accessMode
        )
    }
}
