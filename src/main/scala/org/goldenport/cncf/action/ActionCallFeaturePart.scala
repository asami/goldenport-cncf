package org.goldenport.cncf.action

import cats.free.Free
import cats.Functor
import cats.syntax.flatMap.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.http.HttpResponse
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.Program
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult

/*
 * @since   Jan.  6, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 * @version Mar. 17, 2026
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

  protected final def action_property_string(name: String): Option[String] =
    action.properties.reverseIterator.collectFirst {
      case prop if prop.name.equalsIgnoreCase(name) =>
        Option(prop.value).map(_.toString.trim).getOrElse("")
    }.filter(_.nonEmpty)

  protected final def action_required_property_string(name: String): Consequence[String] =
    action_property_string(name)
      .map(Consequence.success)
      .getOrElse(Consequence.failure(s"Property not found: $name"))
}

trait ActionCallRepositoryPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def repo =
    component.map(_.aggregateSpace).getOrElse(Consequence.failUninitializedState.RAISE)

  // Aggregate-oriented access for application logic.
  // This returns the domain value object directly.
  protected final def aggregate_load[A](id: EntityId): Consequence[A] =
    component
      .map(_.aggregateSpace)
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolve[A](id)

  // Preserve transport/storage failures (e.g. I/O) as Failure.
  // Only "not found" is converted to Success(None).
  protected final def aggregate_load_option[A](
    targetid: EntityId
  ): Consequence[Option[A]] =
    component
      .map(_.aggregateSpace)
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolveOption[A](targetid)

  protected final def aggregate_load[A](
    collectionname: String,
    id: EntityId
  ): Consequence[A] =
    component
      .map(_.aggregate[A](collectionname))
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolve(id)

  protected final def aggregate_search[A](
    collectionname: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    component
      .map(_.aggregateSpace)
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .query[A](collectionname, q)
      .map { xs =>
        SearchResult(
          query = q,
          data = xs,
          totalCount = Some(xs.size),
          offset = q.offset,
          limit = q.limit,
          fetchedCount = xs.size
        )
      }
}

trait ActionCallBrowserPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def browser =
    component.map(_.viewSpace).getOrElse(Consequence.failUninitializedState.RAISE)

  protected final def view_load[A](
    collectionname: String,
    id: EntityId
  ): Consequence[A] =
    browser.browser[A](collectionname).find(id)

  protected final def view_load[A](
    collectionname: String,
    viewname: String,
    id: EntityId
  ): Consequence[A] =
    browser.browser[A](collectionname, viewname).find(id)

  protected final def view_search[A](
    collectionname: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    browser.browser[A](collectionname).query(q).map(_to_search_result(q, _))

  protected final def view_search[A](
    collectionname: String,
    viewname: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    browser.browser[A](collectionname, viewname).query(q).map(_to_search_result(q, _))

  private def _to_search_result[A](
    q: Query[?],
    xs: Vector[A]
  ): SearchResult[A] =
    SearchResult(
      query = q,
      data = xs,
      totalCount = Some(xs.size),
      offset = q.offset,
      limit = q.limit,
      fetchedCount = xs.size
    )
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

trait ActionCallEntityStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def entity_create[T](
    entity: T
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreCreate(entity, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_load_option[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    val op = UnitOfWorkOp.EntityStoreLoad(id, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_load[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[T] = {
    entity_load_option(id).flatMap { x =>
      val r = Consequence.successOrEntityNotFound(x)(id)
      exec_from(r)
    }
  }

  protected final def entity_save[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreSave(entity, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(changes, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Patch update with explicit target id.
  // This is intended for Update.PatchShape where id is excluded from patch object.
  protected final def entity_update[T](
    id: EntityId,
    patch: T
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(id, patch, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete(id: EntityId): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDelete(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearch(query, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    collection: EntityCollectionId,
    query: Query[?]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] =
    entity_search[T](EntityQuery(collection, query))

  // Direct execution variants (immediate execution)
  protected final def entity_load_option_direct[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Option[T] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    uow.execute(op)
  }

  protected final def entity_load_direct[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): T = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    uow.execute(op).getOrElse {
      throw new IllegalStateException(s"entity not found: $id")
    }
  }

  protected final def entity_save_direct[T](
    entity: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreSave(entity, tc)
    uow.execute(op)
  }

  protected final def entity_update_direct[T](
    changes: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdate(changes, tc)
    uow.execute(op)
  }

  protected final def entity_update_direct[T](
    id: EntityId,
    patch: T
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(id, patch, tc)
    uow.execute(op)
  }

  protected final def entity_delete_direct(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDelete(id)
    uow.execute(op)
  }

  protected final def entity_search_direct[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): SearchResult[T] = {
    val op = UnitOfWorkOp.EntityStoreSearch(query, tc)
    uow.execute(op)
  }
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

  protected final def store_update(id: UniversalId, record: Record): ExecUowM[Unit] = {
    val op = _op_store_update(id, record)
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

  protected final def store_update_direct(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_update(id, record)
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

  private def _op_store_update(id: UniversalId, record: Record): UnitOfWorkOp[Unit] = {
    // TODO: Implement DataStoreUpdate operation (currently routed to save op).
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
