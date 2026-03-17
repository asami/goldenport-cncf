package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.datatype.EntityCollectionId
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{DataStore, QueryDirective, QueryLimit, QueryOrder, OrderDirection}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.model.statemachine.{Aliveness, PostStatus}

/*
 * @since   Apr. 11, 2025
 *  version Dec. 18, 2025
 *  version Jan. 10, 2026
 *  version Feb. 26, 2026
 * @version Mar. 11, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class EntityStore {
  def name: String
//  def serialize(entity: E): Consequence[Record]
//  def deserialize(record: Record): Consequence[E]
  def isAccept(cid: EntityCollectionId): Boolean = true

  def create[T](
    entity: T
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]]

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]]
  def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit]

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit]

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]]
}

object EntityStore {
  final val PROP_ID = "id"

  def noop() = NoopEntityStore()

  def standard(): EntityStore = StandardEntityStore()

  // final case class EntityId(
  //   major: String,
  //   minor: String,
  //   collection: CollectionId
  // ) extends UniversalId(major, minor, "entity", collection.name)

  // trait EntityInstance[T] {
  // }

  // def create[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
  //   ???
  // }

  // def load[T](store: EntityStore[T])(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
  //   ???
  // }

  // def search[T](store: EntityStore[T], directive: QueryDirective)(using instance: EntityInstance[T]): Consequence[SearchResult] = {
  //   ???
  // }

  // def store[T](store: EntityStore[T], id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def update[T](store: EntityStore[T], id: EntityId, changes: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
  //   ???
  // }

  // def delete[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
  //   ???
  // }
}

case class CreateResult[T](id: EntityId) {
  def toRecord: Record = Record.data(
    "id" -> id.print
  )
}
case class GetResult[T]()
case class UpdateResult[T]()
case class DeleteResult[T]()

class NoopEntityStore() extends EntityStore {
  def name: String = "noop"
  def create[T](entity: T)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = ???
  def load[T](id: EntityId)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = ???
  def save[T](entity: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = ???
  def update[T](changes: T)(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = ???
  def delete(id: EntityId)(using ctx: ExecutionContext): Consequence[Unit] = ???
  def search[T](query: EntityQuery[T])(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = ???
}

class StandardEntityStore(
) extends EntityStore {
  import EntityStore.*

  def name: String = "standard"

  def createId[T](entity: T)(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): EntityId =
    EntityId(ctx.major, ctx.minor, tc.collection(entity))

  def create[T](
    entity: T
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Consequence[CreateResult[T]] = {
    val id = tc.id(entity) getOrElse createId(entity)
    val rec = _complement_create_record(tc.toRecord(entity), id)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      _ <- ds.create(cid, dsid, rec)
    } yield CreateResult(id)
  }

  def load[T](
    id: EntityId
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Option[T]] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      o <- ds.load(cid, dsid)
      r <- o.traverse(tc.fromRecord)
    } yield r
  }

  def save[T](
    entity: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = {
    val id = tc.id(entity)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      existing <- ds.load(cid, dsid)
      rec = _complement_save_record(tc.toRecord(entity), id, existing)
      r <- ds.save(cid, dsid, rec)
    } yield r
  }

  def update[T](
    changes: T
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[Unit] = {
    val id = tc.id(changes)
    val rec = _complement_update_record(tc.toRecord(changes), id)
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      r <- ds.update(cid, dsid, rec)
    } yield r
  }

  def delete(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      r <- ds.delete(cid, dsid)
    } yield r
  }

  def search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T], ctx: ExecutionContext): Consequence[SearchResult[T]] = {
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(query.collection)
      raw <- ctx.dataStoreSpace.search(
        cid,
        QueryDirective(
          query = org.goldenport.cncf.datastore.Query.Empty,
          order = _to_datastore_order(query.query.sort),
          limit = QueryLimit.Unbounded
        )
      )
      decoded <- raw.records.toVector.traverse(tc.fromRecord)
      filtered = decoded.filter(x => EntityDirectiveQuery.matches(query.query, x))
      sorted = EntityDirectiveQuery.sortValues(filtered, query.query.sort)
      values = EntityDirectiveQuery.sliceValues(sorted, query.query.offset, query.query.limit)
    } yield SearchResult(
      query = query.query,
      data = values,
      totalCount = Some(filtered.size),
      offset = query.query.offset,
      limit = query.query.limit,
      fetchedCount = values.size
    )
  }

  private def _to_datastore_order(
    sort: Vector[EntityDirectiveQuery.SortKey]
  ): QueryOrder =
    sort.headOption match {
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Asc)) =>
        QueryOrder.By(path, OrderDirection.Asc)
      case Some(EntityDirectiveQuery.SortKey(path, EntityDirectiveQuery.SortDirection.Desc)) =>
        QueryOrder.By(path, OrderDirection.Desc)
      case None =>
        QueryOrder.None
    }

  private def _complement_create_record(
    record: Record,
    id: EntityId
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = None,
      includesCreationDefaults = true,
      includesStateDefaults = true
    )

  private def _complement_save_record(
    record: Record,
    id: EntityId,
    existing: Option[Record]
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = existing,
      includesCreationDefaults = true,
      includesStateDefaults = true
    )

  private def _complement_update_record(
    record: Record,
    id: EntityId
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = None,
      includesCreationDefaults = false,
      includesStateDefaults = false
    )

  private def _complement_record(
    record: Record,
    id: EntityId,
    existing: Option[Record],
    includesCreationDefaults: Boolean,
    includesStateDefaults: Boolean
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
    val principalid = ctx.security.principal.id.value
    val principal = Identifier(principalid)
    val defaults = Vector.newBuilder[(String, Any)]
    val existingmap = existing.map(_.asMap).getOrElse(Map.empty)

    def add_if_missing(key: String, value: => Option[Any]): Unit = {
      if (!record.keySet.contains(key))
        value.foreach(v => defaults += (key -> v))
    }

    def existing_value(key: String): Option[Any] = existingmap.get(key)

    if (includesCreationDefaults) {
      add_if_missing("id", existing_value("id").orElse(Some(id.print)))
      add_if_missing("name", existing_value("name").orElse(Some(principalid)))
      add_if_missing("createdAt", existing_value("createdAt").orElse(Some(now)))
      add_if_missing("createdBy", existing_value("createdBy").orElse(Some(principal)))
    }

    add_if_missing("updatedAt", Some(now))
    add_if_missing("updatedBy", Some(principal))
    if (includesStateDefaults) {
      add_if_missing("postStatus", existing_value("postStatus").orElse(Some(PostStatus.default)))
      add_if_missing("aliveness", existing_value("aliveness").orElse(Some(Aliveness.default)))
    }
    add_if_missing("traceId", Some(ctx.observability.traceId.value))
    add_if_missing("correlationId", ctx.observability.correlationId.map(_.value))

    val complement = Record.dataAuto(defaults.result()*)
    record ++ complement
  }
}
