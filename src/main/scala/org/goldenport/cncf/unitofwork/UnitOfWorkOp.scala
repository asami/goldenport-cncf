package org.goldenport.cncf.unitofwork

import org.goldenport.http.HttpResponse
import org.goldenport.id.UniversalId
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.*
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.directive.*

/*
 * UnitOfWork operation algebra.
 *
 * This ADT defines the canonical execution operations interpreted
 * by UnitOfWork. Both declarative (Free/UoW) and direct execution
 * DSLs must construct these operations.
 *
 * This is the single source of truth for executable intents.
 *
 * @since   Jan. 10, 2026
 *  version Feb. 25, 2026
 *  version Mar. 24, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait UnitOfWorkOp[A]

object UnitOfWorkOp {

  // ------------------------------------------------------------
  // HTTP operations
  // ------------------------------------------------------------

  final case class HttpGet(
    path: String
  ) extends UnitOfWorkOp[HttpResponse]

  final case class HttpPost(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ) extends UnitOfWorkOp[HttpResponse]

  final case class HttpPut(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ) extends UnitOfWorkOp[HttpResponse]

  final case class ShellCommandExec(
    command: ShellCommand
  ) extends UnitOfWorkOp[ShellCommandResult]

  // ------------------------------------------------------------
  // DataStore operations
  // ------------------------------------------------------------
  final case class DataStoreLoad(
    id: UniversalId
  ) extends UnitOfWorkOp[Option[Record]]

  final case class DataStoreSave(
    id: UniversalId,
    record: Record
  ) extends UnitOfWorkOp[Unit]

  final case class DataStoreDelete(
    id: UniversalId
  ) extends UnitOfWorkOp[Unit]

  // ------------------------------------------------------------
  // EntityStore operations
  // ------------------------------------------------------------
  final case class EntityStoreCreate[T](
    entity: T,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[CreateResult[T]]

  final case class EntityStoreLoad[T](
    id: EntityId,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Option[T]]

  // Special-use direct path to EntityStoreSpace (bypasses EntitySpace/MemoryRealm).
  final case class EntityStoreLoadDirect[T](
    id: EntityId,
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[Option[T]]

  final case class EntityStoreSave[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  // Patch-oriented update route for cozy-generated update shapes (no id field in patch).
  final case class EntityStoreUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreDelete(
    id: EntityId,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreDeleteHard(
    id: EntityId
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreSearch[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[SearchResult[T]]

  // Special-use direct path to EntityStoreSpace (bypasses EntitySpace/MemoryRealm).
  final case class EntityStoreSearchDirect[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[SearchResult[T]]
}
