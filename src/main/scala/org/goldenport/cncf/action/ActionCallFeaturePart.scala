package org.goldenport.cncf.action

import cats.free.Free
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
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.Program
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
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
 * @version Mar. 27, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext

  protected final def exec_pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  protected final def exec_from[A](c: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](c)

  protected final def exec_c[A](op: UnitOfWorkOp[A])(using uow: UnitOfWork): Consequence[A] =
    new UnitOfWorkInterpreter(uow).run(ConsequenceT.liftF(Free.liftF(op)))

  protected final def exec_or_throw[A](op: UnitOfWorkOp[A])(using uow: UnitOfWork): A =
    exec_c(op).TAKE

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
    Consequence.fromOption(
      action_property_string(name),
      s"Property not found: $name"
    )
}

trait ActionCallRepositoryPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def repo =
    component.map(_.aggregateSpace).getOrElse(Consequence.failUninitializedState.RAISE)

  protected final def aggregate_load[A](id: EntityId): ExecUowM[A] =
    exec_from(aggregate_load_c[A](id))

  // Aggregate-oriented access for application logic.
  // This returns the domain value object directly.
  protected final def aggregate_load_c[A](id: EntityId): Consequence[A] =
    component
      .map(_.aggregateSpace)
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolve[A](id)

  protected final def aggregate_load_or_throw[A](id: EntityId): A =
    aggregate_load_c[A](id).TAKE

  protected final def aggregate_load_option[A](targetid: EntityId): ExecUowM[Option[A]] =
    exec_from(aggregate_load_option_c[A](targetid))

  // Preserve transport/storage failures (e.g. I/O) as Failure.
  // Only "not found" is converted to Success(None).
  protected final def aggregate_load_option_c[A](
    targetid: EntityId
  ): Consequence[Option[A]] =
    component
      .map(_.aggregateSpace)
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolveOption[A](targetid)

  protected final def aggregate_load_option_or_throw[A](targetid: EntityId): Option[A] =
    aggregate_load_option_c[A](targetid).TAKE

  protected final def aggregate_load[A](
    collectionname: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from(aggregate_load_c[A](collectionname, id))

  protected final def aggregate_load_c[A](
    collectionname: String,
    id: EntityId
  ): Consequence[A] =
    component
      .map(_.aggregate[A](collectionname))
      .getOrElse(Consequence.failUninitializedState.RAISE)
      .resolve(id)

  protected final def aggregate_load_or_throw[A](
    collectionname: String,
    id: EntityId
  ): A =
    aggregate_load_c[A](collectionname, id).TAKE

  protected final def aggregate_search[A](
    collectionname: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from(aggregate_search_c[A](collectionname, q))

  protected final def aggregate_search_c[A](
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

  protected final def aggregate_search_or_throw[A](
    collectionname: String,
    q: Query[?]
  ): SearchResult[A] =
    aggregate_search_c[A](collectionname, q).TAKE
}

trait ActionCallBrowserPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def browser =
    component.map(_.viewSpace).getOrElse(Consequence.failUninitializedState.RAISE)

  protected final def view_load[A](
    collectionname: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from(view_load_c[A](collectionname, id))

  protected final def view_load_c[A](
    collectionname: String,
    id: EntityId
  ): Consequence[A] =
    browser.browser[A](collectionname).find(id)

  protected final def view_load_or_throw[A](
    collectionname: String,
    id: EntityId
  ): A =
    view_load_c[A](collectionname, id).TAKE

  protected final def view_load[A](
    collectionname: String,
    viewname: String,
    id: EntityId
  ): ExecUowM[A] =
    exec_from(view_load_c[A](collectionname, viewname, id))

  protected final def view_load_c[A](
    collectionname: String,
    viewname: String,
    id: EntityId
  ): Consequence[A] =
    browser.browser[A](collectionname, viewname).find(id)

  protected final def view_load_or_throw[A](
    collectionname: String,
    viewname: String,
    id: EntityId
  ): A =
    view_load_c[A](collectionname, viewname, id).TAKE

  protected final def view_search[A](
    collectionname: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from(view_search_c[A](collectionname, q))

  protected final def view_search_c[A](
    collectionname: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    browser.browser[A](collectionname).query(q).map(_to_search_result(q, _))

  protected final def view_search_or_throw[A](
    collectionname: String,
    q: Query[?]
  ): SearchResult[A] =
    view_search_c[A](collectionname, q).TAKE

  protected final def view_search[A](
    collectionname: String,
    viewname: String,
    q: Query[?]
  ): ExecUowM[SearchResult[A]] =
    exec_from(view_search_c[A](collectionname, viewname, q))

  protected final def view_search_c[A](
    collectionname: String,
    viewname: String,
    q: Query[?]
  ): Consequence[SearchResult[A]] =
    browser.browser[A](collectionname, viewname).query(q).map(_to_search_result(q, _))

  protected final def view_search_or_throw[A](
    collectionname: String,
    viewname: String,
    q: Query[?]
  ): SearchResult[A] =
    view_search_c[A](collectionname, viewname, q).TAKE

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

  protected final def http_get_c(
    path: String
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_get(path)
    exec_c(op)
  }

  protected final def http_get_or_throw(
    path: String
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_get(path)
    exec_or_throw(op)
  }

  protected final def http_post_c(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_post(path, body, headers)
    exec_c(op)
  }

  protected final def http_post_or_throw(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_post(path, body, headers)
    exec_or_throw(op)
  }

  protected final def http_put_c(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[HttpResponse] = {
    val op = _op_http_put(path, body, headers)
    exec_c(op)
  }

  protected final def http_put_or_throw(
    path: String,
    body: Option[String] = None,
    headers: Map[String, String] = Map.empty
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): HttpResponse = {
    val op = _op_http_put(path, body, headers)
    exec_or_throw(op)
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
    component.flatMap(_.entitySpace.entityOption[T](id.collection.name)) match {
      case Some(collection) =>
        println(s"[entity-load] collection=${id.collection.name} store=${collection.storage.storeRealm.values.size} memory=${collection.storage.memoryRealm.map(_.values.size).getOrElse(0)} id=${id.value}")
        exec_from(Consequence.success(collection.resolve(id).toOption))
      case None =>
        val op = UnitOfWorkOp.EntityStoreLoad(id, tc)
        ConsequenceT.liftF(Free.liftF(op))
    }
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

  protected final def entity_delete_hard(id: EntityId): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    component.flatMap(_.entitySpace.entityOption[T](query.collection.name)) match {
      case Some(collection) =>
        println(s"[entity-search] collection=${query.collection.name} store=${collection.storage.storeRealm.values.size} memory=${collection.storage.memoryRealm.map(_.values.size).getOrElse(0)} query=${query.query}")
        exec_from(collection.search(query)(using execution_context))
      case None =>
        val op = UnitOfWorkOp.EntityStoreSearch(query, tc)
        ConsequenceT.liftF(Free.liftF(op))
    }
  }

  protected final def entity_search[T](
    collection: EntityCollectionId,
    query: Query[?]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] =
    entity_search[T](EntityQuery(collection, query))

  protected final def entity_load_option_c[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Option[T]] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    exec_c(op)
  }

  protected final def entity_load_option_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Option[T] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    exec_or_throw(op)
  }

  protected final def entity_load_c[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[T] =
    entity_load_option_c(id).flatMap(x => Consequence.successOrEntityNotFound(x)(id))

  protected final def entity_load_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): T = {
    entity_load_c(id).TAKE
  }

  protected final def entity_save_c[T](
    entity: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreSave(entity, tc)
    exec_c(op)
  }

  protected final def entity_save_or_throw[T](
    entity: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreSave(entity, tc)
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
    changes: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(changes, tc)
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    changes: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdate(changes, tc)
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
    id: EntityId,
    patch: T
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(id, patch, tc)
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    id: EntityId,
    patch: T
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(id, patch, tc)
    exec_or_throw(op)
  }

  protected final def entity_delete_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDelete(id)
    exec_c(op)
  }

  protected final def entity_delete_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDelete(id)
    exec_or_throw(op)
  }

  protected final def entity_delete_hard_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    exec_c(op)
  }

  protected final def entity_delete_hard_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    exec_or_throw(op)
  }

  protected final def entity_search_c[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearch(query, tc)
    exec_c(op)
  }

  protected final def entity_search_or_throw[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): SearchResult[T] = {
    val op = UnitOfWorkOp.EntityStoreSearch(query, tc)
    exec_or_throw(op)
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

  protected final def store_load_c(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Option[Record]] = {
    val op = _op_store_load(id)
    exec_c(op)
  }

  protected final def store_load_or_throw(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Option[Record] = {
    val op = _op_store_load(id)
    exec_or_throw(op)
  }

  protected final def store_save_c(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_save(id, record)
    exec_c(op)
  }

  protected final def store_save_or_throw(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_save(id, record)
    exec_or_throw(op)
  }

  protected final def store_update_c(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_update(id, record)
    exec_c(op)
  }

  protected final def store_update_or_throw(
    id: UniversalId,
    record: Record
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_update(id, record)
    exec_or_throw(op)
  }

  protected final def store_delete_c(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Consequence[Unit] = {
    val op = _op_store_delete(id)
    exec_c(op)
  }

  protected final def store_delete_or_throw(
    id: UniversalId
  )(using uow: UnitOfWork, http: org.goldenport.cncf.http.HttpDriver): Unit = {
    val op = _op_store_delete(id)
    exec_or_throw(op)
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
  )(using uow: UnitOfWork): Consequence[ShellCommandResult] = {
    val op = _op_shell_command_exec(command)
    exec_c(op)
  }

  protected final def shell_exec_or_throw(
    command: ShellCommand
  )(using uow: UnitOfWork): ShellCommandResult = {
    val op = _op_shell_command_exec(command)
    exec_or_throw(op)
  }

  private def _op_shell_command_exec(
    command: ShellCommand
  ): UnitOfWorkOp[ShellCommandResult] =
    UnitOfWorkOp.ShellCommandExec(command)
}
