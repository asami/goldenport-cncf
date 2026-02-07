package org.goldenport.cncf.action

import cats.free.Free
import cats.Functor
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.http.HttpResponse
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.Program

/*
 * @since   Jan.  6, 2026
 *  version Jan. 21, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext

  protected final def exec_pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  protected final def exec_from[A](c: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](c)

  protected final def response_string(p: String): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Scalar(p))

  protected final def response_json(p: Json): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Json(p))

  protected final def response_yaml(p: String): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Yaml(p))
}

trait ActionCallHttpPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>

  // Declarative DSL (UoW / Free)
  protected final def http_get(path: String): ExecUowM[HttpResponse] = {
    val op = _op_http_get(path)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_post(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_post(path, body, headers)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def http_put(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_put(path, body, headers)
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Direct execution variants
  protected final def http_get_direct(
    path: String
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_get(path)
    uow.execute(op)
  }

  protected final def http_post_direct(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_post(path, body, headers)
    uow.execute(op)
  }

  // Private helpers to build UnitOfWorkOp
  private def _op_http_get(path: String): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpGet(path)

  private def _op_http_post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPost(path, body, headers)

  private def _op_http_put(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPut(path, body, headers)
}

trait ActionCallDataStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  // // Legacy direct datastore access (pre-UoW DSL)
  // protected final def ds_get(id: UniversalId): Option[DataStore.Record] = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.load(id)
  // }

  // protected final def ds_put(id: UniversalId, record: DataStore.Record): Unit = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.store(id, record)
  // }

  // protected final def ds_delete(id: UniversalId): Unit = {
  //   val datastore = execution_context.runtime.unitOfWork.datastore
  //   datastore.delete(id)
  // }

  // Declarative DSL (UoW / Free)
  protected final def store_load(id: UniversalId): ExecUowM[Option[Record]] = {
    val op = _op_store_load(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_save(id: UniversalId, record: Record): ExecUowM[Unit] = {
    val op = _op_store_save(id, record)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def store_delete(id: UniversalId): ExecUowM[Unit] = {
    val op = _op_store_delete(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Direct execution variants (immediate execution)
  protected final def store_load_direct(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Option[Record] = {
    val op = _op_store_load(id)
    uow.execute(op)
  }

  protected final def store_save_direct(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_save(id, record)
    uow.execute(op)
  }

  protected final def store_delete_direct(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_delete(id)
    uow.execute(op)
  }

  // Private helpers to build UnitOfWorkOp
  private def _op_store_load(id: UniversalId): UnitOfWorkOp[Option[Record]] = {
    // TODO: Implement DataStoreLoad operation
    UnitOfWorkOp.DataStoreLoad(id)
  }

  private def _op_store_save(id: UniversalId, record: Record): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreSave operation
    UnitOfWorkOp.DataStoreSave(id, record)
  }

  private def _op_store_delete(id: UniversalId): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreDelete operation
    UnitOfWorkOp.DataStoreDelete(id)
  }
}


trait ActionCallShellCommandPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>

  protected final def shell_exec(
    command: ShellCommand
  ): ExecUowM[ShellCommandResult] = {
    val op = _op_shell_command_exec(command)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def shell_exec_c(
    command: ShellCommand
  ): ExecUowM[Consequence[ShellCommandResult]] =
    Functor[ExecUowM].map(shell_exec(command))(result => Consequence.success(result))

  private def _op_shell_command_exec(
    command: ShellCommand
  ): UnitOfWorkOp[ShellCommandResult] =
    UnitOfWorkOp.ShellCommandExec(command)
}
