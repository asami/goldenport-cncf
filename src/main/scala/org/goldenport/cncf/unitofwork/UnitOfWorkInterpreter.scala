package org.goldenport.cncf.unitofwork

import java.nio.file.{Files, Path, Paths}
import cats.free.Free
import cats.~>
import scala.util.control.NonFatal
import org.goldenport.{Consequence, Conclusion, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.blob.{BlobInlineImageWorkflow, ContentReferenceWorkflow, ContentRenderWorkflow}
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.http.{HttpDriver, RuntimeDashboardMetrics}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.embedded.{EmbeddedDataStore, EmbeddedDataStoreRunner}
import org.goldenport.cncf.entity.*
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.observability.{CallTreeContext, CallTreeValueSummary, ConclusionDiagnostics}
import org.goldenport.process.ShellCommandExecutor
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.security.OperationAccessPolicy
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.processexecution.{ProcessExecutionDriver, ProcessExecutionHandle, ProcessExecutionResult, ProcessExecutionWorkArea, ResolvedProcessExecution}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.simplemodeling.model.directive.Update

/*
 * Interpreter for UnitOfWorkOp.
 *
 * This bridges declarative UoW programs (Free) and
 * concrete UnitOfWork execution.
 * @since   Jan. 10, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 29, 2026
 *  version Apr. 29, 2026
 *  version May. 11, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkInterpreter(uow: UnitOfWork) {
  given ExecutionContext = uow.executionContext

  private val _step: UnitOfWorkOp ~> Consequence =
    new (UnitOfWorkOp ~> Consequence) {
      def apply[A](op: UnitOfWorkOp[A]): Consequence[A] =
        _execute(op)
    }

  def run[R](program: ExecUowM[R]): Consequence[R] = {
    val result =
      try {
        program.value.foldMap(_step)
      } catch {
        case e: Throwable =>
          return _abort_failure_c(Conclusion.from(e))
      }
    result match {
      case Consequence.Success(inner) =>
        inner match {
          case Consequence.Success(value) =>
            uow.commit().map(_ => value)
          case Consequence.Failure(primary) =>
            _abort_failure_c(primary)
        }
      case Consequence.Failure(primary) =>
        _abort_failure_c(primary)
    }
  }

  private def _abort_failure_c[R](primary: Conclusion): Consequence[R] =
    uow.abort() match {
      case Consequence.Failure(cleanup) =>
        Consequence.Failure(cleanup ++ primary)
      case _ =>
        Consequence.Failure(primary)
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
      _with_calltree("uow:authorize") {
        _authorize(Some(authorization))
      }

    case UnitOfWorkOp.StageOperationEvaluationSupplemental(intent) =>
      _with_calltree(
        "uow:operation-evaluation:stage",
        Map("fact_kind" -> intent.fact.factKind.token)
      ) {
        uow.stageOperationEvaluationSupplementalC(intent)
      }

    case UnitOfWorkOp.HttpGet(path, headers, properties) =>
      _with_calltree("uow:http:get") {
        Consequence(_http_driver.get(path, headers, properties))
      }

    case UnitOfWorkOp.HttpPost(path, body, headers, properties) =>
      _with_calltree("uow:http:post") {
        Consequence(_http_driver.post(path, body, headers, properties))
      }

    case UnitOfWorkOp.HttpPostBag(path, body, headers, properties) =>
      _with_calltree("uow:http:post") {
        Consequence(_http_driver.postBag(path, body, headers, properties))
      }

    case UnitOfWorkOp.HttpPut(path, body, headers, properties) =>
      _with_calltree("uow:http:put") {
        Consequence(_http_driver.put(path, body, headers, properties))
      }

    case UnitOfWorkOp.DataStoreLoad(id) =>
      _with_calltree("uow:datastore:load") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreLoad")
      }

    case UnitOfWorkOp.DataStoreSave(id, record) =>
      _with_calltree("uow:datastore:save") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreSave")
      }

    case UnitOfWorkOp.DataStoreDelete(id) =>
      _with_calltree("uow:datastore:delete") {
        Consequence.dataStoreUnavailable("DataStore not wired: DataStoreDelete")
      }

    case UnitOfWorkOp.LocalDataDir(componentname) =>
      _with_calltree("uow:local-data:dir", Map("component" -> componentname)) {
        _local_data_dir(componentname)
      }

    case UnitOfWorkOp.EmbeddedDataStoreOpen(componentname, name, path) =>
      _with_calltree("uow:embedded-datastore:open", Map("component" -> componentname, "name" -> name)) {
        _embedded_datastore(componentname, name, path)
      }

    case UnitOfWorkOp.EmbeddedDataStoreRead(store, statement) =>
      _with_calltree("uow:embedded-datastore:read", Map("component" -> store.componentName, "name" -> store.name)) {
        EmbeddedDataStoreRunner.read(store, statement)
      }

    case UnitOfWorkOp.EmbeddedDataStoreUpdate(store, statement) =>
      _with_calltree("uow:embedded-datastore:update", Map("component" -> store.componentName, "name" -> store.name)) {
        EmbeddedDataStoreRunner.update(store, statement)
      }

    case UnitOfWorkOp.EmbeddedDataStoreMigrate(store, statements) =>
      _with_calltree("uow:embedded-datastore:migrate", Map("component" -> store.componentName, "name" -> store.name, "statements" -> statements.size.toString)) {
        EmbeddedDataStoreRunner.migrate(store, statements)
      }

    case m: (UnitOfWorkOp.EntityStoreCreate[t] @unchecked) =>
      _with_calltree("uow:entitystore:create") {
        _authorize(m.authorization).flatMap(_ =>
          _entity_store_space.create(m).flatMap { r =>
            _entity_space_put_record(r.id, r.record).map { _ =>
              _view_space_invalidate_all()
              r
            }
          }
        )
      }

    case m: (UnitOfWorkOp.EntityStoreClaimOrLoad[c, p] @unchecked) =>
      _with_calltree("uow:entitystore:claim-or-load") {
        _authorize(m.createAuthorization).flatMap { _ =>
          _entity_store_space.claimOrLoad(m).flatMap {
            case claimed: org.goldenport.cncf.entity.EntityStore.EntityClaimResult.Claimed[c] @unchecked =>
              _entity_space_put_record(claimed.id, claimed.created.record).map { _ =>
                _view_space_invalidate_all()
                claimed
              }
            case loaded: org.goldenport.cncf.entity.EntityStore.EntityClaimResult.Loaded[p] @unchecked =>
              val loadrecord = () => Consequence.success(Some(m.persisted.authorizationRecord(loaded.entity)))
              _authorize(m.loadAuthorization, Some(loadrecord)).map(_ => loaded)
          }
        }
      }

    case m: (UnitOfWorkOp.EntityStoreLoad[t] @unchecked) =>
      val op = _canonical_load_op(m)
      _with_calltree("uow:entityspace:load", _entity_calltree_attributes(op.id, "entity-space", realio = !_working_set_enabled)) {
        _authorize(op.authorization, Some(() => _load_record(op.id))).flatMap { _ =>
          val loaded =
            if (_working_set_enabled)
              _entity_space_load(op)
            else
              _entity_store_space.load(op)
          loaded.map(_.filter(entity =>
            org.goldenport.cncf.entity.EntityAccessScopePolicy.visibilityRecordVisible(
              op.id.collection,
              op.tc.toRecord(entity),
              op.visibilityScope
            )
          ))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreLoadDirect[t] @unchecked) =>
      val id = _canonical_entity_id(m.id)
      _with_calltree("uow:entitystore:load:direct", _entity_calltree_attributes(id, "entity-store", realio = true)) {
        _entity_store_space.load(UnitOfWorkOp.EntityStoreLoad(id, m.tc))
      }

    case m: (UnitOfWorkOp.EntityStoreSave[t] @unchecked) =>
      _with_calltree("uow:entitystore:save") {
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

    case m: (UnitOfWorkOp.EntityStoreUpsert[t] @unchecked) =>
      _with_calltree("uow:entitystore:upsert") {
        _entity_store_space.upsert(m)(
          authorize = { existing =>
            val authorization = if (existing.isDefined) m.updateAuthorization else m.createAuthorization
            val loadrecord = existing.map(_ => () => Consequence.success(existing))
            _authorize(authorization, loadrecord)
          },
          onsaved = { result =>
            _entity_space_put_record(result.id, result.record).map { _ =>
              _view_space_invalidate_all()
              ()
            }
          }
        )
      }

    case m: (UnitOfWorkOp.EntityStoreUpdate[t] @unchecked) =>
      _with_calltree("uow:entitystore:update") {
        val id = m.tc.id(m.entity)
        for {
          current <- _load_record(id)
          loadrecord = () => Consequence.success(current.orElse(Some(m.tc.authorizationRecord(m.entity))))
          _ <- _authorize(m.authorization, Some(loadrecord))
          _ <- current match {
            case Some(record) =>
              _transition_validation_hook.beforeUpdate[t](m.entity, m.tc, record, m.tc.toStoreRecord(m.entity))
            case None =>
              _transition_validation_hook.beforeUpdate[t](m.entity, m.tc)
          }
          r <- _entity_store_space.update(m)
        } yield {
          _entity_space_evict(id)
          _view_space_invalidate_all()
          r
        }
      }

    case m: (UnitOfWorkOp.EntityStoreUpdateById[t] @unchecked) =>
      _with_calltree("uow:entitystore:update:patch") {
        val id = _canonical_entity_id(m.id)
        val op = m.copy(id = id)
        for {
          current <- _load_record(op.id)
          _ <- _authorize(op.authorization, Some(() => Consequence.success(current)))
          _ <- current match {
            case Some(record) =>
              val changes = Update.toChangesRecord(op.tc.toStoreRecord(op.patch))
              val proposed = _overlay_record(record, changes)
              _transition_validation_hook.beforeUpdateById[t](op.id, op.patch, op.tc, record, proposed)
            case None =>
              _transition_validation_hook.beforeUpdateById[t](op.id, op.patch, op.tc)
          }
          r <- _entity_store_space.updateById(op)
        } yield {
          _entity_space_evict(op.id)
          _view_space_invalidate_all()
          r
        }
      }

    case m: UnitOfWorkOp.EntityStoreDelete =>
      _with_calltree("uow:entitystore:delete") {
        val id = _canonical_entity_id(m.id)
        val op = m.copy(id = id)
        _authorize(op.authorization, Some(() => _load_record(op.id))).flatMap(_ =>
          _entity_store_space.delete(op).map { r =>
            _entity_space_evict(op.id)
            _view_space_invalidate_all()
            r
          }
        )
      }

    case m: UnitOfWorkOp.EntityStoreDeleteHard =>
      _with_calltree("uow:entitystore:delete:hard") {
        val id = _canonical_entity_id(m.id)
        val op = m.copy(id = id)
        _entity_store_space.deleteHard(op).map { r =>
          _entity_space_evict(op.id)
          _view_space_invalidate_all()
          r
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearch[t] @unchecked) =>
      _with_calltree("uow:entityspace:search", _entity_search_calltree_attributes(m.query, "entity-space", realio = !_working_set_enabled)) {
        _authorize(m.authorization).flatMap { _ =>
          if (_working_set_enabled)
            _entity_space_search(m)
          else
            _entity_store_space.search(m).flatMap(_filter_search_result(m, _))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearchDirect[t] @unchecked) =>
      _with_calltree("uow:entitystore:search:direct", _entity_search_calltree_attributes(m.query, "entity-store", realio = true)) {
        val op = UnitOfWorkOp.EntityStoreSearch(m.query, m.tc, m.authorization)
        _authorize(m.authorization).flatMap { _ =>
          _entity_store_space.search(UnitOfWorkOp.EntityStoreSearch(m.query, m.tc)).flatMap(_filter_search_result(op, _))
        }
      }

    case m: (UnitOfWorkOp.EntityStoreSearchInternal[t] @unchecked) =>
      _with_calltree("uow:entitystore:search:internal", _entity_search_calltree_attributes(m.query, "entity-store", realio = true)) {
        _entity_store_space.searchInternal(m)
      }

    case m: (UnitOfWorkOp.EntityStoreUniqueValueExists[t] @unchecked) =>
      _with_calltree("uow:entitystore:unique-value-exists") {
        _entity_space_unique_value_exists(m).flatMap {
          case true => Consequence.success(true)
          case false => _entity_store_space.uniqueValueExists(m)
        }
      }

    case m: (UnitOfWorkOp.EntityStoreResolveIdentity[t] @unchecked) =>
      _with_calltree("uow:entitystore:resolve-identity") {
        _entity_space_resolve_identity(m).flatMap {
          case Some(id) => Consequence.success(Some(id))
          case None => _entity_store_space.resolveIdentity(m)
        }
      }

    case UnitOfWorkOp.BlobNormalizeInlineImages(content) =>
      _with_calltree("uow:blob:inline-image:normalize") {
        _component_required.flatMap { component =>
          BlobInlineImageWorkflow(component).normalize(content)
        }
      }

    case UnitOfWorkOp.BlobAttachInlineImages(source, occurrences) =>
      _with_calltree("uow:blob:inline-image:attach") {
        _component_required.flatMap { component =>
          BlobInlineImageWorkflow(component).attachInlineImages(source, occurrences).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentNormalizeReferences(content) =>
      _with_calltree("uow:content:references:normalize") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).normalize(content)
        }
      }

    case UnitOfWorkOp.ContentAttachReferences(source, references) =>
      _with_calltree("uow:content:references:attach") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).attachReferences(source, references).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentValidateReferences(references) =>
      _with_calltree("uow:content:references:validate") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).validateInlineReferences(references)
        }
      }

    case UnitOfWorkOp.ContentSyncInlineReferences(source, references) =>
      _with_calltree("uow:content:references:sync-inline") {
        _component_required.flatMap { component =>
          ContentReferenceWorkflow(component).syncInlineReferences(source, references).map { result =>
            _view_space_invalidate_all()
            result
          }
        }
      }

    case UnitOfWorkOp.ContentRenderHtml(content) =>
      _with_calltree("uow:content:render:html") {
        _component_required.flatMap { component =>
          ContentRenderWorkflow(component).renderHtml(content)
        }
      }

    case UnitOfWorkOp.ShellCommandExec(command) =>
      _with_calltree("uow:shell:exec") {
        _shell_command_executor.execute(command)
      }

    case UnitOfWorkOp.ProcessExec(execution) =>
      _with_process_execution_calltree(execution) {
        _execute_process_c(execution)
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

  private def _local_data_dir(
    componentname: String
  ): Consequence[Path] =
    try {
      val normalized = _normalized_local_data_component(componentname)
      val path = _configured_path(Vector(
        s"cncf.local-data.$normalized.dir",
        s"textus.local-data.$normalized.dir"
      )).getOrElse {
        val root = _configured_path(Vector(
          "cncf.local-data.root",
          "textus.local-data.root"
        )).getOrElse(Paths.get(System.getProperty("user.home"), ".cncf"))
        root.resolve(normalized)
      }.toAbsolutePath.normalize
      Files.createDirectories(path)
      Consequence.success(path)
    } catch {
      case e: Throwable => Consequence.Failure(Conclusion.from(e))
    }

  private def _embedded_datastore(
    componentname: String,
    name: String,
    path: Option[Path]
  ): Consequence[EmbeddedDataStore] =
    _local_data_dir(componentname).map { dir =>
      val normalizedcomponent = _normalized_local_data_component(componentname)
      val normalizedname = _normalized_embedded_datastore_name(name)
      val effectivepath =
        path
          .orElse(_configured_path(Vector(
            s"cncf.local-data.$normalizedcomponent.$normalizedname.path",
            s"textus.local-data.$normalizedcomponent.$normalizedname.path"
          )))
          .getOrElse(dir.resolve(s"$normalizedname.db"))
          .toAbsolutePath
          .normalize
      EmbeddedDataStore(normalizedcomponent, normalizedname, effectivepath)
    }

  private def _configured_path(
    keys: Vector[String]
  ): Option[Path] =
    keys.iterator.flatMap(_configuration_string).map(Paths.get(_)).find(_.toString.nonEmpty)

  private def _configuration_string(
    key: String
  ): Option[String] =
    _component_option
      .flatMap(_.subsystem)
      .flatMap(s => ConfigurationAccess.getString(s.configuration, key))
      .orElse(
        uow.executionContext.runtime.resolvedParameters.get(key)
          .flatMap(parameter => _configuration_value_string(parameter.value))
      )

  private def _configuration_value_string(
    value: ConfigurationValue
  ): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Option(v).map(_.trim).filter(_.nonEmpty)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }

  private def _normalized_local_data_component(
    value: String
  ): String = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '-' || c == '_' => c
      case _ => '-'
    }.mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-")
    if (normalized.nonEmpty) normalized else "default"
  }

  private def _normalized_embedded_datastore_name(
    value: String
  ): String = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit || c == '-' || c == '_' => c
      case _ => '-'
    }.mkString.replaceAll("-+", "-").stripPrefix("-").stripSuffix("-")
    if (normalized.nonEmpty) normalized else "main"
  }

  private def _canonical_load_op[T](
    op: UnitOfWorkOp.EntityStoreLoad[T]
  ): UnitOfWorkOp.EntityStoreLoad[T] = {
    val id = _canonical_entity_id(op.id)
    if (id == op.id) op else op.copy(id = id)
  }

  private def _canonical_entity_id(
    id: EntityId
  ): EntityId =
    _component_option
      .flatMap(_.entitySpace.entityOption[Any](id.collection.name))
      .map { collection =>
        val cid = collection.descriptor.collectionId
        if (id.collection == cid)
          id
        else
          EntityId(id.major, id.minor, cid, id.timestamp, id.entropy)
      }
      .getOrElse(id)

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
      collection.putScoped(entity)(using uow.executionContext)
    } catch {
      case e: IllegalStateException if e.getMessage != null && e.getMessage.contains("Entity must implement EntityPersistable") =>
        ()
    }

  private def _emit_working_set_loading_fallback(
    entityname: String,
    state: String
  ): Unit =
    EntityAccessMetricsRegistry.shared.record(
      "entity.search.fallback.working-set-loading",
      Record.dataAuto(
        "entity" -> entityname,
        "source" -> "entity-store",
        "outcome" -> "fallback",
        "reason" -> "working-set-loading",
        "workingSetState" -> state
      )
    )

  private def _emit_entity_search_fallback(
    entityname: String
  ): Unit =
    EntityAccessMetricsRegistry.shared.record(
      "entity.search.fallback.entity-store",
      Record.dataAuto(
        "entity" -> entityname,
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
      .foreach(_.putScoped(entity)(using uow.executionContext))
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
    } yield EntityConcurrencyMetadata
      .decodeEntity(r)(
        collection.descriptor.persistent.fromStoreRecord
      )
      .map(collection.putScoped(_)(using uow.executionContext))
      .recoverWith {
        case c if _is_not_implemented(c) => Consequence.unit
        case c => Consequence.Failure[Unit](c)
      }).getOrElse(Consequence.unit)
  }

  private def _view_space_invalidate_all(): Unit =
    _component_option.foreach(_.viewSpace.invalidateAll())

  private def _authorize(
    authorization: Option[UnitOfWorkAuthorization],
    loadrecord: Option[() => Consequence[Option[org.goldenport.record.Record]]] = None
  ): Consequence[Unit] =
    authorization.fold(Consequence.unit) { a =>
      val loader: EntityId => Consequence[Option[org.goldenport.record.Record]] = id =>
        loadrecord match {
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
    (for {
      cid <- uow.executionContext.entityStoreSpace.dataStoreCollection(id)
      dsid <- uow.executionContext.entityStoreSpace.dataStoreEntryId(id)
      ds <- uow.executionContext.dataStoreSpace.dataStore(cid)
      rec <- ds.load(cid, dsid)
    } yield rec).recoverWith {
      case conclusion =>
        if (_is_not_found(conclusion))
          Consequence.success(None)
        else
          Consequence.Failure(conclusion)
    }

  private def _overlay_record(base: Record, changes: Record): Record =
    changes.fields.foldLeft(base) { (z, field) =>
      z.upsertSingle(field.key, field.value.single)
    }

  private def _is_not_found(conclusion: Conclusion): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase(java.util.Locale.ROOT)
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

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
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
  }

  private def _is_not_implemented(
    conclusion: org.goldenport.Conclusion
  ): Boolean =
    conclusion.observation.taxonomy.symptom == org.goldenport.observation.Taxonomy.Symptom.NotImplemented

  private def _shell_command_executor: ShellCommandExecutor =
    uow.shellCommandExecutor

  private def _calltree_context: CallTreeContext =
    uow.executionContext.observability.callTreeContext

  private def _entity_calltree_attributes(
    id: EntityId,
    layer: String,
    realio: Boolean
  ): Map[String, String] =
    Map(
      "entity" -> id.collection.name,
      "id" -> id.value,
      "cache_layer" -> layer,
      "real_io" -> realio.toString,
      "working_set_enabled" -> _working_set_enabled.toString
    )

  private def _entity_search_calltree_attributes(
    query: EntityQuery[?],
    layer: String,
    realio: Boolean
  ): Map[String, String] =
    Map(
      "entity" -> query.collection.name,
      "cache_layer" -> layer,
      "real_io" -> realio.toString,
      "working_set_enabled" -> _working_set_enabled.toString,
      "query" -> _calltree_entity_query_json(query)
    )

  private def _calltree_entity_query_json(
    query: EntityQuery[?]
  ): String =
    _truncate_calltree_text(RecordEncoder.json(CallTreeValueSummary.recordSummary(Record.dataAuto(
      "collection" -> query.collection.name,
      "scope" -> query.scope.toString,
      "visibility_scope" -> query.visibilityScope.map(_.toString),
      "query" -> query.query.toRecord()
    ), includeInline = true)), 4000)

  private def _truncate_calltree_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String] = Map.empty
  )(body: => Consequence[A]): Consequence[A] = {
    val ctx = _calltree_context
    if (ctx.isEnabled) {
      ctx.enter(label, attributes ++ Map(
        "calltree_kind" -> "uow"
      ))
      try {
        val result = body
        result match {
          case success: Consequence.Success[A] =>
            ctx.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(success.result))
            success
          case failure: Consequence.Failure[A] =>
            ctx.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString,
              "error" -> failure.conclusion.display
            ))
            failure
        }
      } catch {
        case e: Throwable =>
          ctx.leave()
          throw e
      }
    } else {
      body
    }
  }

  private def _with_process_execution_calltree(
    execution: ResolvedProcessExecution
  )(
    body: => Consequence[ProcessExecutionResult]
  ): Consequence[ProcessExecutionResult] = {
    val ctx = _calltree_context
    if (ctx.isEnabled) {
      ctx.enter("uow:process-exec", Map(
        "calltree_kind" -> "uow",
        "process.capability" -> execution.request.capability.print,
        "process.program" -> execution.definition.safeProgramIdentity
      ))
      try {
        val result = body
        result match {
          case success: Consequence.Success[ProcessExecutionResult] =>
            ctx.leave(_process_execution_result_attributes(success.result) + ("outcome" -> "success"))
            success
          case failure: Consequence.Failure[ProcessExecutionResult] =>
            ctx.leave(Map(
              "outcome" -> "failure",
              "status" -> failure.conclusion.status.webCode.code.toString
            ))
            failure
        }
      } catch {
        case e: Throwable =>
          ctx.leave()
          throw e
      }
    } else {
      body
    }
  }

  private def _process_execution_result_attributes(
    result: ProcessExecutionResult
  ): Map[String, String] =
    Map(
      "process.termination" -> result.termination.toString,
      "process.elapsed_millis" -> result.elapsedMillis.toString,
      "process.stdout_bytes" -> result.stdout.byteCount.toString,
      "process.stderr_bytes" -> result.stderr.byteCount.toString,
      "process.artifact_count" -> result.artifacts.size.toString,
      "process.program" -> result.safeProgramIdentity
    )

  private def _execute_process_in_workspace_c(
    driver: ProcessExecutionDriver,
    execution: ResolvedProcessExecution,
    workspace: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionResult] = {
    var registration = Option.empty[UnitOfWorkResourceRegistration]
    try {
      for {
        _ <- workspace.materializeInputsC(execution)
        _ <- workspace.prepareOutputsC(execution)
        handle <- driver.startC(execution, workspace)
        result <- {
        val jobregistration = uow.executionContext.jobContext.cancellationScope.map(
          _.register(handle.cancelC)
        )
        try {
          uow.registerResourceC(_process_execution_resource(handle, workspace)).flatMap { value =>
            registration = Some(value)
            handle.awaitC.flatMap { result =>
              try {
                workspace.close()
                value.close()
                registration = None
                Consequence.success(result)
              } catch {
                case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
              }
            }
          }
        } finally {
          jobregistration.foreach(_.close())
        }
        }
      } yield result
    } finally {
      if (registration.isEmpty)
        workspace.close()
    }
  }

  private def _process_execution_resource(
    handle: ProcessExecutionHandle,
    workspace: ProcessExecutionWorkArea
  ): UnitOfWorkResource =
    new UnitOfWorkResource {
      def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
        val _ = termination
        val cancelled = _attempt_cleanup_c(handle.cancelC)
        val awaited = _attempt_cleanup_c(handle.awaitC.map(_ => ()))
        val closed = _attempt_cleanup_c {
          workspace.close()
          Consequence.unit
        }
        _combine_cleanup_c(Vector(cancelled, awaited, closed))
      }
    }

  private def _attempt_cleanup_c(
    cleanup: => Consequence[Unit]
  ): Consequence[Unit] =
    try {
      cleanup
    } catch {
      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
    }

  private def _combine_cleanup_c(
    results: Vector[Consequence[Unit]]
  ): Consequence[Unit] = {
    val failures = results.collect { case Consequence.Failure(conclusion) => conclusion }
    failures.reduceOption(_ ++ _).map(Consequence.Failure(_)).getOrElse(Consequence.unit)
  }

  private def _execute_process_c(
    execution: ResolvedProcessExecution
  ): Consequence[ProcessExecutionResult] = {
    val startednanos = System.nanoTime()
    var driver = Option.empty[ProcessExecutionDriver]
    val result = try {
      ProcessExecutionDriver.resolveC(uow.executionContext.cncfCore.scope).flatMap { resolved =>
        driver = Some(resolved)
        ProcessExecutionWorkArea.allocateC(uow.executionContext.cncfCore.scope.workAreaSpace).flatMap(
          _execute_process_in_workspace_c(resolved, execution, _)
        )
      }
    } catch {
      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
    }
    _record_process_execution(execution, driver, result, startednanos)
    result
  }

  private def _record_process_execution(
    execution: ResolvedProcessExecution,
    driver: Option[ProcessExecutionDriver],
    result: Consequence[ProcessExecutionResult],
    startednanos: Long
  ): Unit = {
    val elapsedmillis = math.max(0L, (System.nanoTime() - startednanos) / 1000000L)
    val driveridentity = driver.map(_.safeIdentity).getOrElse("unresolved")
    result match {
      case success: Consequence.Success[ProcessExecutionResult] =>
        RuntimeDashboardMetrics.recordProcessExecution(
          capability = execution.request.capability.print,
          driver = driveridentity,
          termination = Some(success.result.termination.toString),
          elapsedMillis = Some(elapsedmillis)
        )
      case failure: Consequence.Failure[ProcessExecutionResult] =>
        val diagnostic = ConclusionDiagnostics.classify(failure.conclusion)
        RuntimeDashboardMetrics.recordProcessExecution(
          capability = execution.request.capability.print,
          driver = driveridentity,
          error = true,
          diagnosticKey = Some(diagnostic.diagnosticKey),
          diagnosticRecord = Some(diagnostic.toRecord),
          elapsedMillis = Some(elapsedmillis)
        )
    }
  }
}
