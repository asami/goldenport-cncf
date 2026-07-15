package org.goldenport.cncf.security

import java.time.Instant
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.record.Record

/*
 * @since   Apr. 13, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityAuthorizationContext(
  subject: SecuritySubject,
  entity: Record,
  operation: EntityAuthorizationContext.Operation,
  application: EntityAuthorizationContext.Application,
  environment: EntityAuthorizationContext.Environment
) {
  def accessKind: String = operation.accessKind
}

object EntityAuthorizationContext {
  final case class Operation(
    accessKind: String,
    resourceFamily: String,
    resourceType: Option[String],
    collectionName: Option[String],
    accessMode: EntityAccessMode,
    operationModel: Option[ServiceOperationModel],
    entityOperationKind: Option[EntityOperationKind],
    entityApplicationDomain: Option[EntityApplicationDomain]
  )

  final case class Application(
    entityNames: Vector[String]
  )

  final case class Environment(
    traceId: String,
    correlationId: Option[String],
    evaluatedAt: Option[Instant] = None
  )

  def apply(
    record: Record,
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): EntityAuthorizationContext =
    apply(record, authorization, ctx.clock.instant())

  def apply(
    record: Record,
    authorization: UnitOfWorkAuthorization,
    evaluatedat: Instant
  )(using ctx: ExecutionContext): EntityAuthorizationContext =
    EntityAuthorizationContext(
      subject = SecuritySubject.current,
      entity = record,
      operation = Operation(
        accessKind = authorization.accessKind,
        resourceFamily = authorization.resourceFamily,
        resourceType = authorization.resourceType,
        collectionName = authorization.collectionName,
        accessMode = authorization.accessMode,
        operationModel = authorization.operationModel,
        entityOperationKind = authorization.entityOperationKind,
        entityApplicationDomain = authorization.entityApplicationDomain
      ),
      application = Application(
        entityNames = authorization.entityNames
      ),
      environment = Environment(
        traceId = ctx.observability.traceId.value,
        correlationId = ctx.observability.correlationId.map(_.value),
        evaluatedAt = Some(evaluatedat)
      )
    )
}
