package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.observation.Observation
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.directive.*
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.directive.Update
import org.goldenport.cncf.unitofwork.UnitOfWorkOp.*

/*
 * @since   Feb. 24, 2026
 *  version Feb. 25, 2026
 *  version Mar. 27, 2026
 *  version Apr. 13, 2026
 *  version Apr. 14, 2026
 * @version May. 11, 2026
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

  def create[T](op: EntityStoreCreate[T])(using ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    given EntityPersistentCreate[T] = op.tc
    val cid = op.tc.collection(op.entity)
    _with_calltree("space:entitystore:create", _entitystore_space_attributes("create", cid)) {
      for {
        entitystore <- _by_collection(cid)
        r <- entitystore.create(op.entity, _create_options(op))
      } yield r
    }
  }

  private def _create_options[T](
    op: EntityStoreCreate[T]
  ): EntityCreateOptions =
    op.options

  def importSeed[T](
    seed: EntityStoreSeed[T]
  )(using ctx: ExecutionContext, tc: EntityPersistent[T]): Consequence[Unit] =
    _with_calltree("space:entitystore:import-seed", Map("space" -> "entitystore", "operation" -> "import-seed", "entry_count" -> seed.entries.size.toString)) {
      seed.entries.foldLeft(Consequence.unit) { (z, entry) =>
        z.flatMap { _ =>
          val createTc = new EntityPersistentCreate[T] {
            def id(e: T): Option[EntityId] = Some(tc.id(e))
            def toRecord(e: T): org.goldenport.record.Record = tc.toRecord(e)
            override def toStoreRecord(e: T): org.goldenport.record.Record = tc.toStoreRecord(e)
            def collection(e: T): EntityCollectionId = tc.id(e).collection
          }
          given EntityPersistentCreate[T] = createTc
          val id = tc.id(entry.entity)
          val cid = id.collection
          for {
            _ <- _by_collection(cid)
            dscid <- dataStoreCollection(cid)
            dsid <- dataStoreEntryId(id)
            ds <- ctx.dataStoreSpace.dataStore(dscid)
            record = tc.toStoreRecord(entry.entity)
            _ <- _with_calltree("space:datastore:create", Map("space" -> "datastore", "operation" -> "create", "collection" -> dscid.print, "entry_id" -> dsid.print)) {
              ds.create(dscid, dsid, record)
            }.recoverWith { case _ =>
              _with_calltree("space:datastore:save", Map("space" -> "datastore", "operation" -> "save", "collection" -> dscid.print, "entry_id" -> dsid.print)) {
                ds.save(dscid, dsid, record)
              }
            }
          } yield ()
        }
      }
    }

  def load[T](op: EntityStoreLoad[T])(using ctx: ExecutionContext): Consequence[Option[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree("space:entitystore:load", _entitystore_space_attributes("load", op.id.collection) + ("entity_id" -> op.id.print)) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.load(op.id)
      } yield r
    }
  }

  def save[T](op: EntityStoreSave[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree("space:entitystore:save", _entitystore_space_attributes("save", id.collection) + ("entity_id" -> id.print)) {
      for {
        entitystore <- _by_collection(id.collection)
        r <- entitystore.save(op.entity)
      } yield r
    }
  }

  def update[T](op: EntityStoreUpdate[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    given EntityPersistent[T] = op.tc
    val id = op.tc.id(op.entity)
    _with_calltree("space:entitystore:update", _entitystore_space_attributes("update", id.collection) + ("entity_id" -> id.print)) {
      for {
        entitystore <- _by_collection(id.collection)
        r <- entitystore.update(op.entity)
      } yield r
    }
  }

  def updateById[T](op: EntityStoreUpdateById[T])(using ctx: ExecutionContext): Consequence[Unit] = {
    _with_calltree("space:entitystore:update-by-id", _entitystore_space_attributes("update-by-id", op.id.collection) + ("entity_id" -> op.id.print)) {
      val changes = Update.toChangesRecord(op.tc.toStoreRecord(op.patch))
      if (changes.isEmpty)
        Consequence.unit
      else
        for {
          cid <- dataStoreCollection(op.id)
          dsid <- dataStoreEntryId(op.id)
          ds <- ctx.dataStoreSpace.dataStore(cid)
          r <- _with_calltree("space:datastore:update", Map("space" -> "datastore", "operation" -> "update", "collection" -> cid.print, "entry_id" -> dsid.print)) {
            ds.update(cid, dsid, changes)
          }
        } yield r
    }
  }

  def delete(op: EntityStoreDelete)(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:entitystore:delete", _entitystore_space_attributes("delete", op.id.collection) + ("entity_id" -> op.id.print)) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.delete(op.id)
      } yield r
    }

  def deleteHard(op: EntityStoreDeleteHard)(using ctx: ExecutionContext): Consequence[Unit] =
    _with_calltree("space:entitystore:delete-hard", _entitystore_space_attributes("delete-hard", op.id.collection) + ("entity_id" -> op.id.print)) {
      for {
        entitystore <- _by_collection(op.id.collection)
        r <- entitystore.deleteHard(op.id)
      } yield r
    }

  def search[T](op: EntityStoreSearch[T])(using ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree_c("space:entitystore:search", _entitystore_space_attributes("search", op.query.collection)) {
      for {
        entitystore <- _by_collection(op.query.collection)
        r <- entitystore.search(op.query)
      } yield r
    }
  }

  def searchInternal[T](op: EntityStoreSearchInternal[T])(using ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    given EntityPersistent[T] = op.tc
    _with_calltree_c("space:entitystore:search-internal", _entitystore_space_attributes("search-internal", op.query.collection)) {
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
    _with_calltree("space:entitystore:unique-value-exists", _entitystore_space_attributes("unique-value-exists", op.collection) + ("field" -> op.fieldName)) {
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
    _with_calltree("space:entitystore:resolve-identity", _entitystore_space_attributes("resolve-identity", op.collection)) {
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
          case success: Consequence.Success[A] =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
          case other =>
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(other))
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave()
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
            calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
            success
          case failure: Consequence.Failure[A] =>
            calltree.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
            failure
        }
      } catch {
        case e: Throwable =>
          calltree.leave()
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
