package org.goldenport.cncf.security

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.record.Record

/*
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
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
    accessMode: EntityAccessMode
  )

  final case class Application(
    entityNames: Vector[String]
  )

  final case class Environment(
    traceId: String,
    correlationId: Option[String]
  )

  def apply(
    record: Record,
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): EntityAuthorizationContext =
    EntityAuthorizationContext(
      subject = SecuritySubject.current,
      entity = record,
      operation = Operation(
        accessKind = authorization.accessKind,
        resourceFamily = authorization.resourceFamily,
        resourceType = authorization.resourceType,
        collectionName = authorization.collectionName,
        accessMode = authorization.accessMode
      ),
      application = Application(
        entityNames = authorization.entityNames
      ),
      environment = Environment(
        traceId = ctx.observability.traceId.value,
        correlationId = ctx.observability.correlationId.map(_.value)
      )
    )
}
