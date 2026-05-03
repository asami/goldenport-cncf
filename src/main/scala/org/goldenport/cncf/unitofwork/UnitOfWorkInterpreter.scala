package org.goldenport.cncf.unitofwork

import cats.free.Free
import cats.~>
import org.goldenport.{Consequence, Conclusion, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.blob.{BlobInlineImageWorkflow, ContentReferenceWorkflow, ContentRenderWorkflow}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.observability.CallTreeContext
import org.goldenport.process.ShellCommandExecutor
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.security.OperationAccessPolicy
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.record.Record

/*
 * Interpreter for UnitOfWorkOp.
 *
 * This bridges declarative UoW programs (Free) and
 * concrete UnitOfWork execution.
 */
/*
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 29, 2026
 *  version Apr. 29, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkInterpreter(uow: UnitOfWork) {
  given ExecutionContext = uow.executionContext

  private val step: UnitOfWorkOp ~> Consequence =
    new (UnitOfWorkOp ~> Consequence) {
      def apply[A](op: UnitOfWorkOp[A]): Consequence[A] =
        _execute(op)
    }

  def run[R](program: ExecUowM[R]): Consequence[R] = {
    val result =
      try {
        program.value.foldMap(step)
      } catch {
        case e: Throwable =>
          uow.abort()
          return Consequence.Failure(Conclusion.from(e))
      }
    result match {
      case Consequence.Success(inner) =>
        inner match {
          case Consequence.Success(value) =>
            uow.commit().map(_ => value)
          case failure: Consequence.Failure[_] =>
            uow.abort()
            failure.asInstanceOf[Consequence[R]]
        }
      case failure: Consequence.Failure[_] =>
        uow.abort()
        failure.asInstanceOf[Consequence[R]]
    }
  }

  // def this(uow: UnitOfWork, http: HttpDriver) = {
  //   this(uow.withHttpDriver(Some(http)))
  // }

  def execute[A](op: UnitOfWorkOp[A]): A =
    run(ConsequenceT.liftF(Free.liftF(op))).TAKE

  def interpret[A](op: UnitOfWorkOp[A]): Consequence[A] =
    _execute(op)

  private def _execute[A](op: UnitOfWorkOp[A]): Consequence[A] = op match {
    case UnitOfWorkOp.Authorize(authorization) =>
      withCallTree("uow:authorize") {
        _authorize(Some(authorization))
      }

    case UnitOfWorkOp.HttpGet(path) =>
      withCallTree("uow:http:get") {
        Consequence(_http_driver.get(path))
      }

    case UnitOfWorkOp.HttpPost(path, body, headers) =>
      withCallTree("uow:http:post") {
        Consequence(_http_driver.post(path, body, headers))
      }

    case UnitOfWorkOp.HttpPostBag(path, body, headers) =>
      withCallTree("uow:http:post") {
        Consequence(_http_driver.postBag(path, body, headers))
      }

    case UnitOfWorkOp.HttpPut(path, body, headers) =>
      withCallTree("uow:http:put") {
        Consequence(_http_driver.put(path, body, headers))
      }

    case UnitOfWorkOp.DataStoreLoad(id) =>
      withCallTree("uow:datastore:load") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreLoad")
      }

    case UnitOfWorkOp.DataStoreSave(id, record) =>
      withCallTree("uow:datastore:save") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreSave")
      }

    case UnitOfWorkOp.DataStoreDelete(id) =>
      withCallTree("uow:datastore:delete") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreDelete")
      }

    case m: (UnitOfWorkOp.EntityStoreCreate[t] @unchecked) =>
      withCallTree("uow:entitystore:create") {
        _authorize(m.authorization).flatMap(_ =>
          _entity_store_space.create(m).flatMap { r =>
            _entity_space_put_record(r.id, r.record).map { _ =>
              _view_space_invalidate_all()
              r
            }
          }
        )
      }

    case m: (UnitOfWorkOp.EntityStoreLoad[t] @unchecked) =>
      withCallTree("uow:entityspace:load") {
        _authorize(m.authorization, Some(() => _load_record(m.id))).flatMap { _ =>
          val loaded =
            if (_working_set_enabled)
              _entity_space_load(m)
            else
              _entity_store_space.load(m)
          loaded.map(_.filter(entity =>
            org.goldenport.cncf.entity.EntityAccessScopePolicy.visibilityRecordVisible(
              m.id.collection,
              m.tc.toRecord(entity),
              m.visibilityScope
            )
          ))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreLoadDirect[t] @unchecked) =>
      withCallTree("uow:entitystore:load:direct") {
        _entity_store_space.load(UnitOfWorkOp.EntityStoreLoad(m.id, m.tc))
      }

    case m: (UnitOfWorkOp.EntityStoreSave[t] @unchecked) =>
      withCallTree("uow:entitystore:save") {
        val loadrecord = () =>
          _load_record(m.tc.id(m.entity)).map(_.orElse(Some(m.tc.authorizationRecord(m.entity))))
        _authorize(m.authorization, Some(loadrecord)).flatMap(_ =>
          _transition_validation_hook
            .beforeSave[t](m.entity, m.tc)
            .flatMap(_ => _entity_store_space.save(m))
            .map { r =>
              _entity_space_evict(m.tc.id(m.entity))
              _view_space_invalidate_all()
              r
            }
        )
      }

    case m: (UnitOfWorkOp.EntityStoreUpdate[t] @unchecked) =>
      withCallTree("uow:entitystore:update") {
        val loadrecord = () =>
          _load_record(m.tc.id(m.entity)).map(_.orElse(Some(m.tc.authorizationRecord(m.entity))))
        _authorize(m.authorization, Some(loadrecord)).flatMap(_ =>
          _transition_validation_hook
            .beforeUpdate[t](m.entity, m.tc)
            .flatMap(_ => _entity_store_space.update(m))
            .map { r =>
              _entity_space_evict(m.tc.id(m.entity))
              _view_space_invalidate_all()
              r
            }
        )
      }

    case m: (UnitOfWorkOp.EntityStoreUpdateById[t] @unchecked) =>
      withCallTree("uow:entitystore:update:patch") {
        _authorize(m.authorization, Some(() => _load_record(m.id))).flatMap(_ =>
          _transition_validation_hook
            .beforeUpdateById[t](m.id, m.patch, m.tc)
            .flatMap(_ => _entity_store_space.updateById(m))
            .map { r =>
              _view_space_invalidate_all()
              r
            }
        )
      }

    case m: UnitOfWorkOp.EntityStoreDelete =>
      withCallTree("uow:entitystore:delete") {
        _authorize(m.authorization, Some(() => _load_record(m.id))).flatMap(_ =>
          _entity_store_space.delete(m).map { r =>
            _entity_space_evict(m.id)
            _view_space_invalidate_all()
            r
          }
        )
      }

    case m: UnitOfWorkOp.EntityStoreDeleteHard =>
      withCallTree("uow:entitystore:delete:hard") {
        _entity_store_space.deleteHard(m).map { r =>
          _entity_space_evict(m.id)
          _view_space_invalidate_all()
          r
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearch[t] @unchecked) =>
      withCallTree("uow:entityspace:search") {
        _authorize(m.authorization).flatMap { _ =>
          if (_working_set_enabled)
            _entity_space_search(m)
          else
            _entity_store_space.search(m).flatMap(_filter_search_result(m, _))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearchDirect[t] @unchecked) =>
      withCallTree("uow:entitystore:search:direct") {
        val op = UnitOfWorkOp.EntityStoreSearch(m.query, m.tc, m.authorization)
        _authorize(m.authorization).flatMap { _ =>
          _entity_store_space.search(UnitOfWorkOp.EntityStoreSearch(m.query, m.tc)).flatMap(_filter_search_result(op, _))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearchInternal[t] @unchecked) =>
      withCallTree("uow:entitystore:search:internal") {
        _entity_store_space.searchInternal(m)
      }

    case m: (UnitOfWorkOp.EntityStoreUniqueValueExists[t] @unchecked) =>
      withCallTree("uow:entitystore:unique-value-exists") {
        _entity_space_unique_value_exists(m).flatMap {
          case true => Consequence.success(true)
          case false => _entity_store_space.uniqueValueExists(m)
        }
      }

    case m: (UnitOfWorkOp.EntityStoreResolveIdentity[t] @unchecked) =>
      withCallTree("uow:entitystore:resolve-identity") {
        _entity_space_resolve_identity(m).flatMap {
          case Some(id) => Consequence.success(Some(id))
          case None => _entity_store_space.resolveIdentity(m)
        }
      }

    case UnitOfWorkOp.BlobNormalizeInlineImages(content) =>
      withCallTree("uow:blob:inline-image:normalize") {
        _component_required.flatMap { component =>
          BlobInlineImageWorkflow(component).normalize(content)
        }
      }

    case UnitOfWorkOp.BlobAttachInlineImages(source, occurrences) =>
      withCallTree("uow:blob:inline-image:attach") {
        _component_required.flatMap { component =>
          BlobInlineImageWorkflow(component).attachInlineImages(source, occurrences).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentNormalizeReferences(content) =>
      withCallTree("uow:content:references:normalize") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).normalize(content)
        }
      }

    case UnitOfWorkOp.ContentAttachReferences(source, references) =>
      withCallTree("uow:content:references:attach") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).attachReferences(source, references).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentValidateReferences(references) =>
      withCallTree("uow:content:references:validate") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).validateInlineReferences(references)
        }
      }

    case UnitOfWorkOp.ContentSyncInlineReferences(source, references) =>
      withCallTree("uow:content:references:sync-inline") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).syncInlineReferences(source, references).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentRenderHtml(content) =>
      withCallTree("uow:content:render:html") {
        _component_required.flatMap { component =>
          ContentRenderWorkflow(component).renderHtml(content)
        }
      }

    case UnitOfWorkOp.ShellCommandExec(command) =>
      withCallTree("uow:shell:exec") {
        _shell_command_executor.execute(command)
      }
  }

  // private def _http_driver_(): HttpDriver =
  //   uow.http_driver.getOrElse {
  //     throw new IllegalStateException("http driver not configured")
  //   }

  private def _http_driver: HttpDriver = uow.httpDriver

  private def _transition_validation_hook: TransitionValidationHook =
    uow.executionContext.runtime.transitionValidationHook

  private def _data_store_space: DataStoreSpace = uow.executionContext.dataStoreSpace

  private def _entity_store_space: EntityStoreSpace = uow.executionContext.entityStoreSpace

  private def _entity_space_load[T](
    op: UnitOfWorkOp.EntityStoreLoad[T]
  ): Consequence[Option[T]] = {
    val name = op.id.collection.name
    _component_option
      .flatMap(_.entitySpace.entityOption[T](name)) match {
      case Some(collection) =>
        collection.resolveScoped(op.id) match {
          case Consequence.Success(entity) =>
            Consequence.success(Some(entity))
          case Consequence.Failure(conclusion) if _is_entity_not_found(conclusion) =>
            _entity_store_space.load(op).map { loaded =>
              loaded.foreach(_entity_space_put_loaded(collection, _))
              loaded
            }
          case Consequence.Failure(conclusion) =>
            Consequence.Failure(conclusion)
        }
      case None =>
        _entity_store_space.load(op)
    }
  }

  private def _entity_space_search[T](
    op: UnitOfWorkOp.EntityStoreSearch[T]
  ): Consequence[SearchResult[T]] = {
    val name = op.query.collection.name
    _component_option
      .flatMap(_.entitySpace.entityOption[T](name)) match {
      case Some(collection) =>
        if (op.query.scope == EntitySearchScope.WorkingSet && !collection.hasEffectiveWorkingSetPolicy) {
          _emit_entity_search_fallback(op.query.collection.name)
          return _entity_store_space.search(op).flatMap(_filter_search_result(op, _))
        }
        if (collection.shouldFallbackToStoreForWorkingSet(op.query)) {
          _emit_entity_search_fallback(op.query.collection.name)
          if (collection.workingSetStatus.isInitializing)
            _emit_working_set_loading_fallback(op.query.collection.name, collection.workingSetStatus.state.label)
          return _entity_store_space.search(op).flatMap(_filter_search_result(op, _))
        }
        collection.search(op.query).flatMap { result =>
          if (result.data.nonEmpty || collection.storage.storeRealm.values.nonEmpty || collection.storage.memoryRealm.exists(_.values.nonEmpty))
            _filter_search_result(op, result)
          else
            _entity_store_space.search(op).flatMap { loaded =>
              _filter_search_result(op, loaded).map { visible =>
                visible.data.foreach(_entity_space_put_loaded(collection, _))
                visible
              }
            }
        }
      case None =>
        _entity_store_space.search(op).flatMap(_filter_search_result(op, _))
    }
  }

  private def _entity_space_unique_value_exists[T](
    op: UnitOfWorkOp.EntityStoreUniqueValueExists[T]
  ): Consequence[Boolean] =
    Consequence.success(
      _component_option.exists { component =>
        component.entitySpace.uniqueValueExists[T](
          op.collection,
          op.fieldName,
          op.value,
          op.excludeId,
          op.scope,
          op.includeEntityIdEntropy
        )
      }
    )

  private def _entity_space_resolve_identity[T](
    op: UnitOfWorkOp.EntityStoreResolveIdentity[T]
  ): Consequence[Option[EntityId]] =
    Consequence.success(
      _component_option.flatMap { component =>
        component.entitySpace.resolveIdentity[T](
          op.collection,
          op.value,
          op.fieldNames,
          op.includeEntityIdEntropy,
          op.scope
        )
      }
    )

  private def _working_set_enabled: Boolean =
    uow.executionContext.framework.workingSetEnabled &&
      !org.goldenport.cncf.context.GlobalRuntimeContext.current.exists { global =>
        global.runtimeMode == org.goldenport.cncf.cli.RunMode.Command ||
          global.runtimeMode == org.goldenport.cncf.cli.RunMode.Client
      }

  private def _entity_space_put_loaded[T](
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[T],
    entity: T
  ): Unit =
    try {
      collection.put(entity)
    } catch {
      case e: IllegalStateException if e.getMessage != null && e.getMessage.contains("Entity must implement EntityPersistable") =>
        ()
    }

  private def _emit_working_set_loading_fallback(
    entityName: String,
    state: String
  ): Unit =
    EntityAccessMetricsRegistry.shared.record(
      "entity.search.fallback.working-set-loading",
      Record.dataAuto(
        "entity" -> entityName,
        "source" -> "entity-store",
        "outcome" -> "fallback",
        "reason" -> "working-set-loading",
        "workingSetState" -> state
      )
    )

  private def _emit_entity_search_fallback(
    entityName: String
  ): Unit =
    EntityAccessMetricsRegistry.shared.record(
      "entity.search.fallback.entity-store",
      Record.dataAuto(
        "entity" -> entityName,
        "source" -> "entity-store",
        "outcome" -> "fallback"
      )
    )

  private def _entity_space_evict(
    id: EntityId
  ): Unit = {
    val name = id.collection.name
    _component_option
      .flatMap { component =>
        component.entitySpace.entityOption[Any](name).orElse(
          component.entitySpace.entityOption(id.collection).map(_.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[Any]])
        )
      }
      .foreach(_.evict(id))
  }

  private def _entity_space_put[T](
    entity: T,
    tc: org.goldenport.cncf.entity.EntityPersistent[T]
  ): Unit = {
    val id = tc.id(entity)
    val name = id.collection.name
    _component_option
      .flatMap { component =>
        component.entitySpace.entityOption[T](name).orElse(
          component.entitySpace.entityOption(id.collection).map(_.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[T]])
        )
      }
      .foreach(_.put(entity))
  }

  private def _entity_space_put_record(
    id: EntityId,
    record: Option[org.goldenport.record.Record]
  ): Consequence[Unit] = {
    val name = id.collection.name
    (for {
      r <- record
      collection <- _component_option.flatMap { component =>
        component.entitySpace.entityOption[Any](name).orElse(
          component.entitySpace.entityOption(id.collection).map(_.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[Any]])
        )
      }
      if collection.storage.memoryRealm.isDefined
    } yield collection.putRecord(r).recoverWith {
      case c if _is_not_implemented(c) => Consequence.unit
      case c => Consequence.Failure[Unit](c)
    }).getOrElse(Consequence.unit)
  }

  private def _view_space_invalidate_all(): Unit =
    _component_option.foreach(_.viewSpace.invalidateAll())

  private def _authorize(
    authorization: Option[UnitOfWorkAuthorization],
    loadRecord: Option[() => Consequence[Option[org.goldenport.record.Record]]] = None
  ): Consequence[Unit] =
    authorization.fold(Consequence.unit) { a =>
      val loader: EntityId => Consequence[Option[org.goldenport.record.Record]] = id =>
        loadRecord match {
          case Some(f) => f()
          case None => _load_record(id)
        }
      OperationAccessPolicy.authorizeUnitOfWorkDefault(a, loader).flatMap { _ =>
        _component_option
          .flatMap(_.factory)
          .flatMap(_.authorize_unit_of_work(a, uow))
          .getOrElse(Consequence.unit)
      }
    }

  private def _load_record(
    id: EntityId
  ): Consequence[Option[org.goldenport.record.Record]] =
    for {
      cid <- uow.executionContext.entityStoreSpace.dataStoreCollection(id)
      dsid <- uow.executionContext.entityStoreSpace.dataStoreEntryId(id)
      ds <- uow.executionContext.dataStoreSpace.dataStore(cid)
      rec <- ds.load(cid, dsid)
    } yield rec

  private def _filter_search_result[T](
    op: UnitOfWorkOp.EntityStoreSearch[T],
    result: SearchResult[T]
  ): Consequence[SearchResult[T]] =
    op.authorization.fold(Consequence.success(result)) { auth =>
      OperationAccessPolicy.filterVisibleSearchResult(auth, result, op.tc)
    }

  private def _component_option: Option[Component] = {
    @annotation.tailrec
    def go(scope: org.goldenport.cncf.context.ScopeContext): Option[Component] =
      scope match {
        case m: Component.Context => Some(m.component)
        case _ =>
          scope.parent match {
            case Some(p) => go(p)
            case None => None
          }
      }
    go(uow.executionContext.cncfCore.scope)
  }

  private def _component_required: Consequence[Component] =
    _component_option
      .map(Consequence.success)
      .getOrElse(Consequence.serviceUnavailable("component context is required for blob inline image operations"))

  private def _is_entity_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase
    symptom == org.goldenport.provisional.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

  private def _is_not_implemented(
    conclusion: org.goldenport.Conclusion
  ): Boolean =
    conclusion.show.contains("NotImplemented")

  private def _shell_command_executor: ShellCommandExecutor =
    uow.shellCommandExecutor

  private def callTreeContext: CallTreeContext =
    uow.executionContext.observability.callTreeContext

  private def withCallTree[A](label: String)(body: => Consequence[A]): Consequence[A] = {
    val ctx = callTreeContext
    ctx.enter(label)
    try {
      body
    } finally {
      ctx.leave()
    }
  }
}
