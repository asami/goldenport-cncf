package org.goldenport.cncf.knowledge

import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentCreate, EntityQuery, EntityRevisionCarrier, EntityRevisionRepresentation, EntitySearchScope, SimpleEntityStorageShapePolicy}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkAuthorization, UnitOfWorkOp}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Native Entity programs for immutable canonical materialization results.
 * The owning component registers this collection with a Detached revision binding
 * and supplies its normal application datastore/UoW. No private provider is opened.
 *
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeMaterializationRepository(
  val owner: ComponentId,
  val collectionId: EntityCollectionId
) {
  import KnowledgeMaterializationRepository.*

  /** The caller durably retains this identity before invoking create. */
  def reserveIdentity()(using ctx: ExecutionContext): Consequence[EntityId] =
    _admit().map(_ => ctx.idGeneration.entityId(collectionId))

  def create(value: KnowledgeMaterialization)(using ctx: ExecutionContext): ExecUowM[EntityRevisionCarrier[KnowledgeMaterialization]] =
    for {
      _ <- _from(KnowledgeMaterialization.validate(value))
      _ <- _from(_admit(Some(value.id)))
      _ <- _lift(UnitOfWorkOp.EntityStoreCreate(value, createPersistent,
        authorization = Some(_authorization("create", None))))
      result <- get(value.id)
    } yield result

  /** A missing or failed read is never permission to replay creation. */
  def get(id: EntityId)(using ctx: ExecutionContext): ExecUowM[EntityRevisionCarrier[KnowledgeMaterialization]] =
    for {
      _ <- _from(_admit(Some(id)))
      found <- _lift(UnitOfWorkOp.EntityStoreLoadDetached(id, persistent,
        authorization = Some(_authorization("read", Some(id)))))
      result <- _from(found.map(Consequence.success).getOrElse(Consequence.entityNotFound(id.print)))
    } yield EntityRevisionCarrier(result.entity.materialization, result.revision)

  /** Exact versioned forward source trace, after native row visibility filtering. */
  def findBySource(source: KnowledgeOwnerReference)(using ctx: ExecutionContext): ExecUowM[Vector[KnowledgeMaterialization]] =
    for {
      _ <- _from(KnowledgeMaterialization.validateReference(source))
      values <- _search()
    } yield values.filter(_.source == source)

  /** Reverse graph-to-result trace; get then resolves the external owner references. */
  def findByNode(id: KnowledgeNodeId)(using ctx: ExecutionContext): ExecUowM[Vector[KnowledgeMaterialization]] =
    _search().map(_.filter(_.snapshot.nodes.exists(_.id == id)))

  private def _search()(using ctx: ExecutionContext): ExecUowM[Vector[KnowledgeMaterialization]] =
    for {
      _ <- _from(_admit())
      result <- _lift(UnitOfWorkOp.EntityStoreSearch(
        EntityQuery[KnowledgeMaterializationEntity](collectionId, Query.plan(Record.empty), scope = EntitySearchScope.Store),
        persistent, authorization = Some(_authorization("search/list", None))))
    } yield result.data.map(_.materialization).sortBy(_.id.print)

  private def _admit(id: Option[EntityId] = None)(using ctx: ExecutionContext): Consequence[Unit] =
    if (!SecuritySubject.from(ctx.security).isAuthenticated)
      Consequence.securityAuthenticationRequired("Knowledge materialization requires an authenticated caller")
    else if (id.exists(value => value == null || value.collection != collectionId))
      Consequence.argumentInvalid("Knowledge materialization identity belongs to another collection")
    else ctx.entitySpace.entityOption(collectionId).flatMap(_.descriptor.revisionBinding) match {
      case Some(binding) if binding.representation == EntityRevisionRepresentation.Detached => Consequence.unit
      case _ => Consequence.configurationInvalid("Knowledge materialization requires its native Detached Entity collection binding")
    }

  private def _authorization(accesskind: String, id: Option[EntityId]): UnitOfWorkAuthorization =
    UnitOfWorkAuthorization(resourceFamily = "domain", resourceType = Some(collectionId.name),
      collectionName = Some(collectionId.name), targetId = id, accessKind = accesskind,
      sourceComponentName = Some(owner.name), targetComponentName = Some(owner.name), entityNames = Vector(collectionId.name))

  private def _from[A](value: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](value)

  private def _lift[A](operation: UnitOfWorkOp[A]): ExecUowM[A] =
    ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, A](operation))
}

object KnowledgeMaterializationRepository {
  /** Register this storage codec in the owning native Detached Entity runtime plan. */
  val persistent: EntityPersistent[KnowledgeMaterializationEntity] = new EntityPersistent[KnowledgeMaterializationEntity] {
    def id(value: KnowledgeMaterializationEntity): EntityId = value.materialization.id
    def toRecord(value: KnowledgeMaterializationEntity): Record = _record(value.materialization)
    override def securityAttributes(value: KnowledgeMaterializationEntity): Option[org.simplemodeling.model.value.SecurityAttributes] =
      value.securityAttributes
    def fromRecord(record: Record): Consequence[KnowledgeMaterializationEntity] = for {
      identity <- EntityId.createC(record)
      content <- record.getAsC[String]("content").flatMap(Consequence.successOrPropertyNotFound("content", _))
      value <- KnowledgeMaterializationCodec.decodeC(content)
      _ <- if (value.id == identity) Consequence.unit
        else Consequence.stateConflict("Knowledge materialization payload differs from its native Entity identity")
    } yield KnowledgeMaterializationEntity(value, SimpleEntityStorageShapePolicy.securityAttributesFromRecord(record))
  }

  val createPersistent: EntityPersistentCreate[KnowledgeMaterialization] = new EntityPersistentCreate[KnowledgeMaterialization] {
    def id(value: KnowledgeMaterialization): Option[EntityId] = Some(value.id)
    def collection(value: KnowledgeMaterialization): EntityCollectionId = value.id.collection
    def toRecord(value: KnowledgeMaterialization): Record = _record(value)
    override def toLogicalStoreRecord(value: KnowledgeMaterialization): Consequence[Record] =
      KnowledgeMaterializationCodec.encodeC(value).map(text => Record.dataAuto("id" -> value.id.value, "content" -> text))
  }

  private def _record(value: KnowledgeMaterialization): Record =
    Record.dataAuto("id" -> value.id.value, "content" -> KnowledgeMaterializationCodec.encodeC(value).TAKE)
}
