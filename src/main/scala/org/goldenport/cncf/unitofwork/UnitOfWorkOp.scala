package org.goldenport.cncf.unitofwork

import org.goldenport.cncf.datastore.DataStore
import org.goldenport.http.HttpResponse
import org.goldenport.id.UniversalId

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
 * @version Jan. 10, 2026
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
}
