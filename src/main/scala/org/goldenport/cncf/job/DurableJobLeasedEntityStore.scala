package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * Transparent overload delegation. Each effect retains its exact native result.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobLeasedEntityStore(
  delegate: EntityStore,
  lease: DurableJobRuntimeLease,
  onfault: () => Unit
) extends EntityStore {
  def name: String = delegate.name
  override def isAccept(cid: EntityCollectionId): Boolean = delegate.isAccept(cid)

  private def _use_c[A](body: => Consequence[A]): Consequence[A] =
    try {
      val result = lease.use(body)
      result match { case Consequence.Failure(_) => onfault(); case _ => () }
      result
    } catch {
      case exception: InterruptedException => onfault(); Thread.currentThread().interrupt(); throw exception
      case NonFatal(exception) => onfault(); throw exception
    }

  override def create[T](entity: T, options: EntityCreateOptions = EntityCreateOptions.default)
    (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _use_c(delegate.create(entity, options))
  override def claimOrLoad[C, P](entity: C, options: EntityCreateOptions = EntityCreateOptions.default)
    (using createTc: EntityPersistentCreate[C], persisted: EntityPersistent[P], ctx: ExecutionContext): Consequence[EntityStore.EntityClaimResult[C, P]] =
    _use_c(delegate.claimOrLoad[C, P](entity, options))
  override private[cncf] def upsert[T](entity: T, id: EntityId, options: EntityCreateOptions = EntityCreateOptions.default)
    (authorize: Option[Record] => Consequence[Unit])(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _use_c(delegate.upsert(entity, id, options)(authorize))
  override private[cncf] def upsert[T](entity: T, id: EntityId, options: EntityCreateOptions)
    (authorize: Option[Record] => Consequence[Unit], onsaved: CreateResult[T] => Consequence[Unit])
    (using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _use_c(delegate.upsert(entity, id, options)(authorize, onsaved))
  override private[cncf] def upsertVersioned[T](entity: T, id: EntityId, options: EntityCreateOptions, policy: EntityUpsertPolicy)
    (authorize: Option[Record] => Consequence[Unit])(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] =
    _use_c(delegate.upsertVersioned(entity, id, options, policy)(authorize))
  override def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] =
    _use_c(delegate.load[T](id))
  override def loadSnapshot[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntitySnapshot[T]]] =
    _use_c(delegate.loadSnapshot[T](id))
  override def loadDetached[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] =
    _use_c(delegate.loadDetached[T](id))
  override private[cncf] def save[T](entity: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
    _use_c(delegate.save(entity))
  override private[cncf] def saveManaged[T](entity: T, executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = _use_c(delegate.saveManaged(entity, executionPolicy))
  override def save[T](entity: T, expectedRevision: EntityRevision)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] = _use_c(delegate.save(entity, expectedRevision))
  override def save[T](entity: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
    _use_c(delegate.save(entity, expectedRevision, executionPolicy))
  override def saveDetached[T](entity: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] =
    _use_c(delegate.saveDetached(entity, expectedRevision, executionPolicy))
  override private[cncf] def update[T](changes: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] =
    _use_c(delegate.update(changes))
  override def update[T](changes: T, expectedRevision: EntityRevision)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] = _use_c(delegate.update(changes, expectedRevision))
  override def update[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
    _use_c(delegate.update(changes, expectedRevision, executionPolicy))
  override def updateDetached[T](changes: T, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[T]] =
    _use_c(delegate.updateDetached(changes, expectedRevision, executionPolicy))
  override def updateById[P](id: EntityId, patch: P, expectedRevision: EntityRevision)
    (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
    _use_c(delegate.updateById(id, patch, expectedRevision))
  override def updateById[P](id: EntityId, patch: P, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
    _use_c(delegate.updateById(id, patch, expectedRevision, executionPolicy))
  override private[cncf] def updateByIdManaged[P](id: EntityId, patch: P, executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[Record] =
    _use_c(delegate.updateByIdManaged(id, patch, executionPolicy))
  override private[cncf] def updateByIdManagedAuthoritative[P](id: EntityId, patch: P, executionPolicy: EntityMutationExecutionPolicy,
    managedMutationBase: EntityStore.ManagedMutationBase)(using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityStore.ManagedRecordMutationResult] =
    _use_c(delegate.updateByIdManagedAuthoritative(id, patch, executionPolicy, managedMutationBase))
  override def updateByIdDetached[P](id: EntityId, patch: P, expectedRevision: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy)
    (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[EntityRevisionCarrier[Record]] =
    _use_c(delegate.updateByIdDetached(id, patch, expectedRevision, executionPolicy))
  override private[cncf] def updateByIdUnversioned[P](id: EntityId, patch: P)
    (using tc: EntityPersistentUpdate[P], ctx: ExecutionContext): Consequence[Unit] = _use_c(delegate.updateByIdUnversioned(id, patch))
  override private[cncf] def conditionalTransition[R, P, S](command: EntityConditionalTransitionCommand[R, P, S])
    (using ctx: ExecutionContext): Consequence[EntityConditionalTransitionExecutionResult[R, S]] = _use_c(delegate.conditionalTransition(command))
  override def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = _use_c(delegate.delete(id))
  override def restore(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = _use_c(delegate.restore(id))
  override def deleteHard(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = _use_c(delegate.deleteHard(id))
  override def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
    _use_c(delegate.search(query))
  override def searchInternal[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] =
    _use_c(delegate.searchInternal(query))
  override def uniqueValueExists[T](collection: EntityCollectionId, fieldname: String, value: String, excludeid: Option[EntityId],
    scope: EntityIdentityScope, includeentityidentropy: Boolean)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Boolean] =
    _use_c(delegate.uniqueValueExists[T](collection, fieldname, value, excludeid, scope, includeentityidentropy))
  override def resolveIdentity[T](collection: EntityCollectionId, value: String, fieldnames: Vector[String],
    includeentityidentropy: Boolean, scope: EntityIdentityScope)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[EntityId]] =
    _use_c(delegate.resolveIdentity[T](collection, value, fieldnames, includeentityidentropy, scope))
}
