package org.goldenport.cncf.entity

import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.observation.Observation
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.directive.*
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.goldenport.record.Record
import org.simplemodeling.model.directive.Update
import org.goldenport.cncf.unitofwork.UnitOfWorkOp.*

/*
 * @since   Feb. 24, 2026
 *  version Feb. 25, 2026
 *  version Mar. 27, 2026
 *  version Apr. 13, 2026
 *  version Apr. 14, 2026
 *  version May. 11, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class EntityStoreSpace {
  private var _entity_stores: Vector[EntityStore] = Vector.empty

  def addEntityStore(es: EntityStore): EntityStoreSpace = {
    _entity_stores = _entity_stores :+ es
    this
  }

  def dataStoreCollection(id: EntityId): Consequence[DataStore.CollectionId] =
    dataStoreCollection(id.collection)

  def dataStoreCollection(id: EntityCollectionId): Consequence[DataStore.CollectionId] =
    Consequence.success(DataStore.CollectionId.EntityStore(id))

  def dataStoreEntryId(id: EntityId): Consequence[DataStore.EntryId] =
    Consequence(DataStore.EntryId(id))

  private def _by_collection(cid: EntityCollectionId): Consequence[EntityStore] =
    Consequence.successOrServiceProviderByKeyNotFound(
      _entity_stores.find(_.isAccept(cid))
    )("entitystore", cid.print)

  def create[T](op: EntityStoreCreate[T])(using
      ctx: ExecutionContext
  ): Consequence[CreateResult[T]] = {
    given EntityPersistentCreate[T] = op.tc
    val cid = op.tc.collection(op.entity)
    _with_calltree("space:entitystore:create", _entitystore_space_attributes("create", cid)) {
      for {
        entitystore <- _by_collection(cid)
        r <- entitystore.create(op.entity, _create_options(op))
      } yield r
    }
  }

  def claimOrLoad[C, P](
    op: EntityStoreClaimOrLoad[C, P]
  )(using ctx: ExecutionContext): Consequence[EntityStore.EntityClaimResult[C, P]] = {
    given EntityPersistentCreate[C] = op.create
    given EntityPersistent[P] = op.persisted
    val cid = op.create.collection(op.entity)
    _with_calltree(
      "space:entitystore:claim-or-load",
      _entitystore_space_attributes("claim-or-load", cid)
    ) {
      _by_collection(cid).flatMap(_.claimOrLoad(op.entity, op.options))
    }
  }

  private def _create_options[T](
    op: EntityStoreCreate[T]
  ): EntityCreateOptions =
    op.options

  private[cncf] def upsert[T](
      op: EntityStoreUpsertUnversioned[T]
  )(
    authorize: Option[Record] => Consequence[Unit]
  )(using ctx: ExecutionContext): Consequence[CreateResult[T]] =
    upsert(op)(authorize, (_: CreateResult[T]) => Consequence.unit)

  private[cncf] def upsert[T](
      op: EntityStoreUpsertUnversioned[T]
  )(
    authorize: Option[Record] => Consequence[Unit],
    @deprecatedName("onSaved", "0.5.1")
    onsaved: CreateResult[T] => Consequence[Unit]
  )(using ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    given EntityPersistentCreate[T] = op.tc
    _with_calltree(
      "space:entitystore:upsert",
      _entitystore_space_attributes("upsert", op.id.collection) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        result <- entitystore.upsert(op.entity, op.id, op.options)(authorize, onsaved)
      } yield result
    }
  }

  private[cncf] def importSeed[T](
    seed: EntityStoreSeed[T]
  )(using ctx: ExecutionContext, tc: EntityPersistent[T]): Consequence[Unit] =
    _with_calltree(
      "space:entitystore:import-seed",
      Map(
        "space"       -> "entitystore",
        "operation"   -> "import-seed",
        "entry_count" -> seed.entries.size.toString
      )
    ) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap { _ =>
          val createtc = new EntityPersistentCreate[T] {
            def id(e: T): Option[EntityId] = Some(tc.id(e))
            def toRecord(e: T): org.goldenport.record.Record = tc.toRecord(e)
            override def toStoreRecord(e: T): org.goldenport.record.Record = tc.toStoreRecord(e)
            def collection(e: T): EntityCollectionId = tc.id(e).collection
          }
          given EntityPersistentCreate[T] = createtc
          val id = tc.id(entry.entity)
          val cid = id.collection
          for {
            _ <- _by_collection(cid)
            dscid <- dataStoreCollection(cid)
            dsid <- dataStoreEntryId(id)
            ds <- ctx.dataStoreSpace.dataStore(dscid)
            source = tc.toStoreRecord(entry.entity)
            record = EntityConcurrencyMetadata.initializeForCreate(source)
            _ <- _with_calltree(
              "space:datastore:create",
              Map(
                "space"      -> "datastore",
                "operation"  -> "create",
                "collection" -> dscid.print,
                "entry_id"   -> dsid.print
              )
            ) {
              ds.create(dscid, dsid, record)
            }.recoverWith { case _ =>
              ds.load(dscid, dsid).flatMap { existing =>
                val preserved = existing match {
                  case Some(value) =>
                    EntityConcurrencyMetadata.preserveForMutation(
                      record,
                      value
                    )
                  case None =>
                    Consequence.success(record)
                }
                preserved.flatMap { value =>
                  _with_calltree(
                    "space:datastore:save",
                    Map(
                      "space"      -> "datastore",
                      "operation"  -> "save",
                      "collection" -> dscid.print,
                      "entry_id"   -> dsid.print
                    )
                  ) {
                    ds.save(dscid, dsid, value)
                  }
                }
              }
            }
          } yield ()
        }
      }
    }

  def load[T](op: EntityStoreLoad[T])(using ctx: ExecutionContext): Consequence[Option[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree(
      "space:entitystore:load",
      _entitystore_space_attributes("load", op.id.collection) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.load(op.id)
        validated <- _validate_loaded_collection(op.id, r, op.tc)
      } yield validated
    }
  }

  def loadSnapshot[T](
    id: EntityId,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[Option[EntitySnapshot[T]]] = {
    given EntityPersistent[T] = tc
    _with_calltree(
      "space:entitystore:load-snapshot",
      _entitystore_space_attributes("load-snapshot", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        snapshot <- entitystore.loadSnapshot(id)
        validated <- _validate_loaded_collection(
          id,
          snapshot,
          tc,
          (value: EntitySnapshot[T]) => value.entity
        )
      } yield validated
    }
  }

  def loadDetached[T](
    id: EntityId,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[Option[EntityRevisionCarrier[T]]] = {
    given EntityPersistent[T] = tc
    _with_calltree(
      "space:entitystore:load-detached",
      _entitystore_space_attributes("load-detached", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        carrier <- entitystore.loadDetached(id)
        validated <- _validate_loaded_collection(
          id,
          carrier,
          tc,
          (value: EntityRevisionCarrier[T]) => value.entity
        )
      } yield validated
    }
  }

  private def _validate_loaded_collection[T](
    requestedid: EntityId,
    entity: Option[T],
    persistent: EntityPersistent[T]
  ): Consequence[Option[T]] =
    _validate_loaded_collection(requestedid, entity, persistent, identity[T])

  private def _validate_loaded_collection[T, A](
    requestedid: EntityId,
    value: Option[A],
    persistent: EntityPersistent[T],
    entity: A => T
  ): Consequence[Option[A]] =
    value match {
      case Some(candidate) =>
        val producedcollection =
          persistent.id(entity(candidate)).collection
        if (producedcollection != requestedid.collection)
          Consequence.operationInvalid(
            "entity-persistent-collection",
            s"Entity codec produced collection '${producedcollection.print}' for requested collection '${requestedid.collection.print}'"
          )
        else
          Consequence.success(value)
      case _ =>
        Consequence.success(value)
    }

  def save[T](
      op: EntityStoreSave[T]
  )(using ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
    saveVersioned(
      op.entity,
      op.tc,
      op.expectedRevision,
      op.executionPolicy
    )

  private[cncf] def saveManaged[T](
    entity: T,
    persistent: EntityPersistent[T],
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  )(using ctx: ExecutionContext): Consequence[T] = {
    given EntityPersistent[T] = persistent
    val id = persistent.id(entity)
    _with_calltree(
      "space:entitystore:save-managed",
      _entitystore_space_attributes("save-managed", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        _ <- entitystore.saveManaged(entity, executionPolicy)
        authoritative <- entitystore
          .load(id)
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
        saved <- Consequence.successOrEntityNotFound(authoritative)(id)
          .recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      } yield saved
    }
  }

  private[cncf] def saveUnversioned[T](
      op: EntityStoreSaveUnversioned[T]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree(
      "space:entitystore:save-unversioned",
      _entitystore_space_attributes("save-unversioned", id.collection) + ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        r <- entitystore.save(op.entity)
      } yield r
    }
  }

  def saveVersioned[T](
    entity: T,
    persistent: EntityPersistent[T],
    expectedRevision: EntityRevision
  )(using
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    saveVersioned(
      entity,
      persistent,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def saveVersioned[T](
    entity: T,
    persistent: EntityPersistent[T],
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = {
    given EntityPersistent[T] = persistent
    val id = persistent.id(entity)
    _with_calltree(
      "space:entitystore:save-versioned",
      _entitystore_space_attributes("save-versioned", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        snapshot <- entitystore.save(
          entity,
          expectedRevision,
          executionPolicy
        )
      } yield snapshot
    }
  }

  def saveDetached[T](
    op: EntityStoreSaveDetached[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree(
      "space:entitystore:save-detached",
      _entitystore_space_attributes("save-detached", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        carrier <- entitystore.saveDetached(
          op.entity,
          op.expectedRevision,
          op.executionPolicy
        )
      } yield carrier
    }
  }

  def update[T](
      op: EntityStoreUpdate[T]
  )(using ctx: ExecutionContext): Consequence[EntitySnapshot[T]] =
    updateVersioned(
      op.entity,
      op.tc,
      op.expectedRevision,
      op.executionPolicy
    )

  private[cncf] def updateUnversioned[T](
      op: EntityStoreUpdateUnversioned[T]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree(
      "space:entitystore:update-unversioned",
      _entitystore_space_attributes("update-unversioned", id.collection) + ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        r <- entitystore.update(op.entity)
      } yield r
    }
  }

  def updateVersioned[T](
    entity: T,
    persistent: EntityPersistent[T],
    expectedRevision: EntityRevision
  )(using
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] =
    updateVersioned(
      entity,
      persistent,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def updateVersioned[T](
    entity: T,
    persistent: EntityPersistent[T],
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    ctx: ExecutionContext
  ): Consequence[EntitySnapshot[T]] = {
    given EntityPersistent[T] = persistent
    val id = persistent.id(entity)
    _with_calltree(
      "space:entitystore:update-versioned",
      _entitystore_space_attributes("update-versioned", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        snapshot <- entitystore.update(
          entity,
          expectedRevision,
          executionPolicy
        )
      } yield snapshot
    }
  }

  def updateDetached[T](
    op: EntityStoreUpdateDetached[T]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[T]] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree(
      "space:entitystore:update-detached",
      _entitystore_space_attributes("update-detached", id.collection) +
        ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        carrier <- entitystore.updateDetached(
          op.entity,
          op.expectedRevision,
          op.executionPolicy
        )
      } yield carrier
    }
  }

  def updateById[P](
      op: EntityStoreUpdateById[P]
  )(using ctx: ExecutionContext): Consequence[Record] = {
    given EntityPersistentUpdate[P] = op.tc
    _with_calltree(
      "space:entitystore:update-by-id",
      _entitystore_space_attributes(
        "update-by-id",
        op.id.collection
      ) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        record <- entitystore.updateByIdManaged(
          op.id,
          op.patch,
          op.executionPolicy
        )
      } yield record
    }
  }

  private[cncf] def updateByIdManagedAuthoritative[P](
    op: EntityStoreUpdateById[P],
    managedMutationBase: EntityStore.ManagedMutationBase
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityStore.ManagedRecordMutationResult] = {
    given EntityPersistentUpdate[P] = op.tc
    _with_calltree(
      "space:entitystore:update-by-id-authoritative",
      _entitystore_space_attributes(
        "update-by-id-authoritative",
        op.id.collection
      ) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        result <- entitystore.updateByIdManagedAuthoritative(
          op.id,
          op.patch,
          op.executionPolicy,
          managedMutationBase
        )
      } yield result
    }
  }

  def updateByIdObserved[P](
    op: EntityStoreUpdateByIdObserved[P]
  )(using ctx: ExecutionContext): Consequence[EntityRecordSnapshot] =
    updateByIdVersioned(
      op.id,
      op.patch,
      op.tc,
      Some(op.expectedRevision),
      op.executionPolicy
    )

  private[cncf] def updateByIdUnversioned[P](
      op: EntityStoreUpdateByIdUnversioned[P]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistentUpdate[P] = op.tc
    _with_calltree(
      "space:entitystore:update-by-id-unversioned",
      _entitystore_space_attributes(
        "update-by-id-unversioned",
        op.id.collection
      ) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        result <- entitystore.updateByIdUnversioned(op.id, op.patch)
      } yield result
    }
  }

  def updateByIdVersioned[P](
    id: EntityId,
    patch: P,
    persistent: EntityPersistentUpdate[P],
    expectedRevision: EntityRevision
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] =
    updateByIdVersioned(
      id,
      patch,
      persistent,
      Some(expectedRevision),
      EntityMutationExecutionPolicy.default
    )

  def updateByIdVersioned[P](
    id: EntityId,
    patch: P,
    persistent: EntityPersistentUpdate[P],
    expectedRevision: Option[EntityRevision],
    executionPolicy: EntityMutationExecutionPolicy
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRecordSnapshot] = {
    given EntityPersistentUpdate[P] = persistent
    _with_calltree(
      "space:entitystore:update-by-id-versioned",
      _entitystore_space_attributes(
        "update-by-id-versioned",
        id.collection
      ) + ("entity_id" -> id.print)
    ) {
      for {
        entitystore <- _by_collection(id.collection)
        snapshot <- entitystore.updateById(
          id,
          patch,
          expectedRevision,
          executionPolicy
        )
      } yield snapshot
    }
  }

  def updateByIdDetached[P](
    op: EntityStoreUpdateByIdDetached[P]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityRevisionCarrier[Record]] = {
    given EntityPersistentUpdate[P] = op.tc
    _with_calltree(
      "space:entitystore:update-by-id-detached",
      _entitystore_space_attributes(
        "update-by-id-detached",
        op.id.collection
      ) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        carrier <- entitystore.updateByIdDetached(
          op.id,
          op.patch,
          op.expectedRevision,
          op.executionPolicy
        )
      } yield carrier
    }
  }

  private[cncf] def conditionalTransition[R, P, S](
    command: EntityConditionalTransitionCommand[R, P, S]
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] =
    _with_calltree(
      "space:entitystore:conditional-transition",
      _entitystore_space_attributes(
        "conditional-transition",
        command.request.rootId.collection
      ) + ("entity_id" -> command.request.rootId.print)
    ) {
      _by_collection(command.request.rootId.collection)
        .flatMap(_.conditionalTransition(command))
    }

  def delete(op: EntityStoreDelete)(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree(
      "space:entitystore:delete",
      _entitystore_space_attributes("delete", op.id.collection) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.delete(op.id)
      } yield r
    }

  def restore(op: EntityStoreRestore)(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree(
      "space:entitystore:restore",
      _entitystore_space_attributes("restore", op.id.collection) +
        ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        result <- entitystore.restore(op.id)
      } yield result
    }

  def deleteHard(op: EntityStoreDeleteHard)(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree(
      "space:entitystore:delete-hard",
      _entitystore_space_attributes("delete-hard", op.id.collection) + ("entity_id" -> op.id.print)
    ) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.deleteHard(op.id)
      } yield r
    }

  def search[T](op: EntityStoreSearch[T])(using
      ctx: ExecutionContext
  ): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree_c(
      "space:entitystore:search",
      _entitystore_space_attributes("search", op.query.collection)
    ) {
      for {
        entitystore <- _by_collection(op.query.collection)
        r <- entitystore.search(op.query)
      } yield r
    }
  }

  def searchInternal[T](op: EntityStoreSearchInternal[T])(using
      ctx: ExecutionContext
  ): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree_c(
      "space:entitystore:search-internal",
      _entitystore_space_attributes("search-internal", op.query.collection)
    ) {
      for {
        entitystore <- _by_collection(op.query.collection)
        r <- entitystore.searchInternal(op.query)
      } yield r
    }
  }

  def uniqueValueExists[T](
    op: EntityStoreUniqueValueExists[T]
  )(using ctx: ExecutionContext): Consequence[Boolean] = {
    given EntityPersistent[T] = op.tc
    _with_calltree(
      "space:entitystore:unique-value-exists",
      _entitystore_space_attributes("unique-value-exists", op.collection) + ("field" -> op.fieldName)
    ) {
      for {
        entitystore <- _by_collection(op.collection)
        r <- entitystore.uniqueValueExists(
          op.collection,
          op.fieldName,
          op.value,
          op.excludeId,
          op.scope,
          op.includeEntityIdEntropy
        )
      } yield r
    }
  }

  def resolveIdentity[T](
    op: EntityStoreResolveIdentity[T]
  )(using ctx: ExecutionContext): Consequence[Option[EntityId]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree(
      "space:entitystore:resolve-identity",
      _entitystore_space_attributes("resolve-identity", op.collection)
    ) {
      for {
        entitystore <- _by_collection(op.collection)
        r <- entitystore.resolveIdentity(
          op.collection,
          op.value,
          op.fieldNames,
          op.includeEntityIdEntropy,
          op.scope
        )
      } yield r
    }
  }

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[?] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
          case failure: Consequence.Failure[?] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
          case other =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other)
            )
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _with_calltree_c[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            calltree.leave(
              Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result)
            )
            success
          case failure: Consequence.Failure[A] =>
            calltree.leave(
              Map("outcome" -> "failure") ++
                CallTreeValueSummary.failureAttributes(failure.conclusion)
            )
            failure
        }
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "exception",
            "exception_type" -> e.getClass.getName
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _entitystore_space_attributes(
    operation: String,
    collection: EntityCollectionId
  ): Map[String, String] =
    Map(
      "space" -> "entitystore",
      "operation" -> operation,
      "collection" -> collection.print
    )
}

object EntityStoreSpace {
  def create(conf: ResolvedConfiguration): EntityStoreSpace = {
    val ess = new EntityStoreSpace()
    val mes = EntityStore.standard()
    ess.addEntityStore(mes)
  }
}
