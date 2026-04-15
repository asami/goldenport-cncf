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
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkAuthorization}
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.security.{AggregateAuthorization, EntityAbacCondition, EntityAccessMode, EntityAccessRelation, EntityApplicationDomain, EntityAuthorizationProfile, EntityOperationKind, EntityUsageKind, OperationAccessPolicy, ServiceOperationModel}
import org.goldenport.cncf.Program
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.entity.EntityCreateOptions
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.action.AggregateBehavior
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.configuration.ConfigurationValue

/*
 * @since   Jan.  6, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 30, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext

  protected final def component_name_option: Option[String] =
    component.flatMap(_.coreOption.map(_.name))

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

  protected final def resolve_aggregate_behavior(
  ): Consequence[AggregateBehavior[?]] =
    component.flatMap(_.factory).flatMap(_.create_aggregate_behavior(action, core)) match {
      case Some(behavior) => Consequence.success(behavior)
      case None => Consequence.operationNotFound(s"AggregateBehavior not found: ${action.name}")
    }

  protected final def invoke_aggregate_behavior[A](
    behavior: AggregateBehavior[A],
    target: A
  ): Consequence[OperationResponse] =
    behavior.run(target, executionContext)
}

trait ActionCallRepositoryPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def repo =
    component.map(_.aggregateSpace).getOrElse(Consequence.uninitializedState.RAISE)

  protected final def aggregate_load[A](id: EntityId): ExecUowM[A] =
    exec_from(aggregate_load_c[A](id))

  // Aggregate-oriented access for application logic.
  // This returns the domain value object directly.
  protected final def aggregate_load_c[A](id: EntityId): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "load",
      aggregateName = id.collection.name,
      targetId = Some(id)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(id.collection.name, id)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolve_with_context[A](id)(using execution_context)
        }
      } yield r
    }

  protected final def aggregate_authorize_type(
    aggregateName: String,
    accessKind: String
  ): ExecUowM[Unit] =
    exec_from(
      AggregateAuthorization.authorizeType(
        aggregateName = aggregateName,
        accessKind = accessKind,
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    )

  protected final def aggregate_authorize_instance(
    aggregateName: String,
    targetId: EntityId,
    accessKind: String,
    loadRecord: EntityId => Consequence[Option[Record]]
  ): ExecUowM[Unit] =
    exec_from(
      AggregateAuthorization.authorizeInstance(
        aggregateName = aggregateName,
        targetId = targetId,
        accessKind = accessKind,
        loadRecord = loadRecord,
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    )

  protected final def aggregate_authorize_command(
    aggregateName: String,
    targetId: Option[EntityId],
    commandName: String,
    loadRecord: EntityId => Consequence[Option[Record]]
  ): ExecUowM[Unit] =
    targetId match {
      case Some(id) =>
        aggregate_authorize_instance(
          aggregateName,
          id,
          s"command:$commandName",
          loadRecord
        )
      case None =>
        aggregate_authorize_type(aggregateName, s"create:$commandName")
    }

  private def _aggregate_operation_model: ServiceOperationModel = {
    val access = _aggregate_declared_access
    getFactory[org.goldenport.cncf.component.Component.Factory]
      .flatMap(_.service_operation_model(action, core))
      .orElse(access.flatMap(_.operationModel).map(ServiceOperationModel.parse))
      .getOrElse(ServiceOperationModel.default)
  }

  private def _aggregate_relation_rules: Vector[EntityAccessRelation] =
    _aggregate_declared_access.flatMap(_.relation).map(EntityAccessRelation.parseList).getOrElse(Vector.empty)

  private def _aggregate_natural_conditions: Vector[EntityAbacCondition] =
    _aggregate_declared_access.flatMap(_.condition).map(EntityAbacCondition.parseList).getOrElse(Vector.empty)

  private def _aggregate_declared_operation_definition =
    component.flatMap { c =>
      val actionname = _aggregate_normalize_name(action.name)
      c.operationDefinitions.find { op =>
        val opname = _aggregate_normalize_name(op.name)
        actionname == opname || actionname.endsWith(opname)
      }
    }

  private def _aggregate_declared_access =
    _aggregate_declared_operation_definition.flatMap(_.access)

  private def _aggregate_normalize_name(p: String): String =
    Option(p).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  protected final def aggregate_load_or_throw[A](id: EntityId): A =
    aggregate_load_c[A](id).TAKE

  protected final def aggregate_load_option[A](targetid: EntityId): ExecUowM[Option[A]] =
    exec_from(aggregate_load_option_c[A](targetid))

  // Preserve transport/storage failures (e.g. I/O) as Failure.
  // Only "not found" is converted to Success(None).
  protected final def aggregate_load_option_c[A](
    targetid: EntityId
  ): Consequence[Option[A]] =
    _aggregate_chokepoint[Option[A]](
      operation = "load",
      aggregateName = targetid.collection.name,
      targetId = Some(targetid)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(targetid.collection.name, targetid)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolveOption[A](targetid)(using execution_context)
        }
      } yield r
    }

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
    _aggregate_chokepoint[A](
      operation = "load",
      aggregateName = collectionname,
      targetId = Some(id)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_load(collectionname, id)
        }
        r <- _aggregate_phase(ctx, DslChokepointPhase.Resolve) {
          component
            .map(_.aggregate[A](collectionname))
            .getOrElse(Consequence.uninitializedState.RAISE)
            .resolve_with_context(id)(using execution_context)
        }
      } yield r
    }

  private def _aggregate_authorize_load(
    aggregateName: String,
    id: EntityId
  ): Consequence[Unit] =
    if (_aggregate_has_entity_collection(aggregateName))
      AggregateAuthorization.authorizeInstance(
        aggregateName = aggregateName,
        targetId = id,
        accessKind = "read",
        loadRecord = _aggregate_load_record(aggregateName),
        access = _aggregate_declared_access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        operationModel = _aggregate_operation_model,
        relationRules = _aggregate_relation_rules,
        naturalConditions = _aggregate_natural_conditions
      )(using execution_context)
    else
      Consequence.unit

  private def _aggregate_load_record(
    aggregateName: String
  )(id: EntityId): Consequence[Option[Record]] =
    component.flatMap(_aggregate_entity_collection_name(_, aggregateName)).
      flatMap(name => component.flatMap(_.entitySpace.entityOption[Any](name))) match {
        case Some(collection) => collection.resolve(id).map(x => Some(collection.descriptor.persistent.toRecord(x)))
        case None => Consequence.success(None)
      }

  private def _aggregate_entity_collection_name(
    component: org.goldenport.cncf.component.Component,
    aggregateName: String
  ): Option[String] =
    component.aggregateDefinitions.find(_.name == aggregateName).map(_.entityName).orElse(Some(aggregateName))

  private def _aggregate_has_entity_collection(
    aggregateName: String
  ): Boolean =
    component.flatMap(c => _aggregate_entity_collection_name(c, aggregateName).flatMap(c.entitySpace.entityOption[Any])).isDefined

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
    _aggregate_chokepoint[SearchResult[A]](
      operation = "search",
      aggregateName = collectionname
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_search(collectionname)
        }
        xs <- _aggregate_phase(ctx, DslChokepointPhase.Query) {
          component
            .map(_.aggregateSpace)
            .getOrElse(Consequence.uninitializedState.RAISE)
            .query_with_context[A](collectionname, q)(using execution_context)
        }
      } yield {
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

  private def _aggregate_authorize_search(
    aggregateName: String
  ): Consequence[Unit] =
    AggregateAuthorization.authorizeType(
      aggregateName = aggregateName,
      accessKind = "search",
      access = _aggregate_declared_access,
      sourceComponentName = component_name_option,
      targetComponentName = component_name_option,
      operationModel = _aggregate_operation_model,
      relationRules = _aggregate_relation_rules,
      naturalConditions = _aggregate_natural_conditions
    )(using execution_context)

  protected final def aggregate_search_or_throw[A](
    collectionname: String,
    q: Query[?]
  ): SearchResult[A] =
    aggregate_search_c[A](collectionname, q).TAKE

  private def _aggregate_put_record_authorized_c(
    entityName: String,
    record: Record
  ): Consequence[Unit] =
    component.flatMap(_.entitySpace.entityOption[Any](entityName)) match {
      case Some(collection) => collection.putRecord(record)
      case None => Consequence.argumentInvalid(s"$entityName entity collection is not available")
    }

  protected final def aggregate_create[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    commandName: String,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(aggregate_create_c(entityName, commandName, action))

  protected final def aggregate_create_c[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    commandName: String,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "create",
      aggregateName = entityName,
      commandName = Some(commandName)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_create(entityName, commandName)
        }
        aggregate <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          action
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          _aggregate_put_record_authorized_c(entityName, aggregate.toRecord())
        }
      } yield aggregate
    }

  protected final def aggregate_update[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    action: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(aggregate_update_c(entityName, targetId, commandName, action))

  protected final def aggregate_update_c[A <: org.goldenport.record.RecordPresentable](
    entityName: String,
    targetId: EntityId,
    commandName: String,
    action: => Consequence[A]
  ): Consequence[A] =
    _aggregate_chokepoint[A](
      operation = "update",
      aggregateName = entityName,
      targetId = Some(targetId),
      commandName = Some(commandName)
    ) { ctx =>
      for {
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Authorization) {
          _aggregate_authorize_update(entityName, targetId, commandName)
        }
        aggregate <- _aggregate_phase(ctx, DslChokepointPhase.Method) {
          action
        }
        _ <- _aggregate_phase(ctx, DslChokepointPhase.Persistence) {
          _aggregate_put_record_authorized_c(entityName, aggregate.toRecord())
        }
      } yield aggregate
    }

  private def _aggregate_chokepoint[A](
    operation: String,
    aggregateName: String,
    targetId: Option[EntityId] = None,
    commandName: Option[String] = None
  )(
    body: DslChokepointContext => Consequence[A]
  ): Consequence[A] = {
    given ExecutionContext = execution_context
    val ctx = DslChokepointContext(
      domain = "aggregate",
      operation = operation,
      componentName = component_name_option,
      resourceName = Some(aggregateName),
      targetId = targetId.map(_.toString),
      commandName = commandName
    )
    DslChokepointRunner.run(ctx)(body(ctx))
  }

  private def _aggregate_phase[A](
    context: DslChokepointContext,
    phase: DslChokepointPhase
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    given ExecutionContext = execution_context
    DslChokepointRunner.phase(context, phase)(body)
  }

  private def _aggregate_authorize_create(
    aggregateName: String,
    commandName: String
  ): Consequence[Unit] =
    AggregateAuthorization.authorizeType(
      aggregateName = aggregateName,
      accessKind = s"create:$commandName",
      access = _aggregate_declared_access,
      sourceComponentName = component_name_option,
      targetComponentName = component_name_option,
      operationModel = _aggregate_operation_model,
      relationRules = _aggregate_relation_rules,
      naturalConditions = _aggregate_natural_conditions
    )(using execution_context)

  private def _aggregate_authorize_update(
    aggregateName: String,
    targetId: EntityId,
    commandName: String
  ): Consequence[Unit] =
    AggregateAuthorization.authorizeInstance(
      aggregateName = aggregateName,
      targetId = targetId,
      accessKind = s"command:$commandName",
      loadRecord = _aggregate_load_record(aggregateName),
      access = _aggregate_declared_access,
      sourceComponentName = component_name_option,
      targetComponentName = component_name_option,
      operationModel = _aggregate_operation_model,
      relationRules = _aggregate_relation_rules,
      naturalConditions = _aggregate_natural_conditions
    )(using execution_context)
}

trait ActionCallBrowserPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def browser =
    component.map(_.viewSpace).getOrElse(Consequence.uninitializedState.RAISE)

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
  private def _emit_entity_access(
    name: String,
    attributes: Record
  ): Unit = {
    component.flatMap(_.subsystem).map(_.entityAccessMetrics).getOrElse(EntityAccessMetricsRegistry.shared).record(name, attributes)
    val _ = execution_context.observability.emitInfo(
      execution_context.cncfCore.scope,
      name,
      attributes
    )
  }

  private def _entity_load_attributes(
    id: EntityId,
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> id.collection.name,
    "id" -> id.value,
    "source" -> source,
    "outcome" -> outcome
  )

  private def _entity_search_attributes(
    query: EntityQuery[?],
    source: String,
    outcome: String
  ): Record = Record.dataAuto(
    "entity" -> query.collection.name,
    "source" -> source,
    "outcome" -> outcome,
    "query" -> query.query.toString
  )

  private def _is_entity_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val s = conclusion.show.toLowerCase
    s.contains("not found") || s.contains("notfound")
  }

  protected final def entity_create[T](
    entity: T
  )(using tc: EntityPersistentCreate[T]): ExecUowM[CreateResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreCreate(
      entity,
      tc,
      _entity_create_options(Some(tc.collection(entity).name)),
      _entity_uow_authorization(Some(tc.collection(entity).name), None, "create")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_load_option[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    _emit_entity_access("entity.load.start", _entity_load_attributes(id, "unknown", "start"))
    component.flatMap(_.entitySpace.entityOption[T](id.collection.name)) match {
      case Some(collection) =>
        _emit_entity_access("entity.load.try.entity-space", _entity_load_attributes(id, "entity-space", "try"))
        collection.resolve(id) match {
          case Consequence.Success(entity) =>
            _emit_entity_access("entity.load.hit.entity-space", _entity_load_attributes(id, "entity-space", "hit"))
            exec_from(_authorize_entity_load_hit(id, entity, tc))
          case Consequence.Failure(conclusion) if _is_entity_not_found(conclusion) =>
            _emit_entity_access("entity.load.fallback.entity-store", _entity_load_attributes(id, "entity-store", "fallback"))
            val op = UnitOfWorkOp.EntityStoreLoad(
              id,
              tc,
              _entity_uow_authorization(Some(id.collection.name), Some(id), "read")
            )
            ConsequenceT.liftF(Free.liftF(op))
          case Consequence.Failure(conclusion) =>
            exec_from(Consequence.Failure(conclusion))
        }
      case None =>
        _emit_entity_access("entity.load.fallback.entity-store", _entity_load_attributes(id, "entity-store", "fallback"))
        val op = UnitOfWorkOp.EntityStoreLoad(
          id,
          tc,
          _entity_uow_authorization(Some(id.collection.name), Some(id), "read")
        )
        ConsequenceT.liftF(Free.liftF(op))
    }
  }

  private def _authorize_entity_load_hit[T](
    id: EntityId,
    entity: T,
    tc: EntityPersistent[T]
  ): Consequence[Option[T]] = {
    given ExecutionContext = execution_context
    _entity_uow_authorization(Some(id.collection.name), Some(id), "read") match {
      case Some(authorization) =>
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          authorization,
          _ => Consequence.success(Some(tc.toRecord(entity)))
        ).map(_ => Some(entity))
      case None =>
        Consequence.success(Some(entity))
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
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      tc,
      _entity_uow_authorization(Some(tc.id(changes).collection.name), Some(tc.id(changes)), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Patch update with explicit target id.
  // This is intended for Update.PatchShape where id is excluded from patch object.
  protected final def entity_update[T](
    id: EntityId,
    patch: T
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      id,
      patch,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete(id: EntityId): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDelete(
      id,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "delete")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete_hard(id: EntityId): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(id)
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    _emit_entity_access("entity.search.start", _entity_search_attributes(query, "unknown", "start"))
    component.flatMap(_.entitySpace.entityOption[T](query.collection.name)) match {
      case Some(collection) =>
        if (_bypass_entity_space_resident_search) {
          _emit_entity_access("entity.search.bypass.entity-space", _entity_search_attributes(query, "entity-space", "bypass"))
          _entity_store_search(query, tc)
        } else {
          _emit_entity_access("entity.search.try.entity-space", _entity_search_attributes(query, "entity-space", "try"))
          val hasresident =
            collection.storage.storeRealm.values.nonEmpty ||
              collection.storage.memoryRealm.exists(_.values.nonEmpty)
          if (hasresident) {
            _emit_entity_access("entity.search.hit.entity-space", _entity_search_attributes(query, "entity-space", "hit"))
            val authorization = _entity_uow_authorization(Some(query.collection.name), None, "search/list")
            exec_from(
              collection.search(query)(using execution_context).flatMap { result =>
                authorization
                  .map(OperationAccessPolicy.filterVisibleSearchResult(_, result, tc)(using execution_context))
                  .getOrElse(Consequence.success(result))
              }
            )
          } else {
            _emit_entity_access("entity.search.fallback.entity-store", _entity_search_attributes(query, "entity-store", "fallback"))
            val op = UnitOfWorkOp.EntityStoreSearch(
              query,
              tc,
              _entity_uow_authorization(Some(query.collection.name), None, "search/list")
            )
            ConsequenceT.liftF(Free.liftF(op))
          }
        }
      case None =>
        _emit_entity_access("entity.search.fallback.entity-store", _entity_search_attributes(query, "entity-store", "fallback"))
        _entity_store_search(query, tc)
    }
  }

  private def _entity_store_search[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ): ExecUowM[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearch(
      query,
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  private def _bypass_entity_space_resident_search: Boolean =
    _bypass_all_resident_search ||
      _config_bool(
        "textus.entity.search.bypass-entity-space-resident",
        "cncf.entity.search.bypass-entity-space-resident"
      )

  private def _bypass_all_resident_search: Boolean =
    _config_bool(
      "textus.entity.search.bypass-resident",
      "cncf.entity.search.bypass-resident"
    )

  private def _config_bool(primary: String, compatibility: String): Boolean =
    _config_bool(primary) || _config_bool(compatibility)

  private def _config_bool(key: String): Boolean =
    component
      .flatMap(_.subsystem)
      .flatMap(_.configurationValue(key))
      .exists(_truthy)

  private def _truthy(value: ConfigurationValue): Boolean =
    _config_string(value).exists { x =>
      val v = x.trim.toLowerCase(java.util.Locale.ROOT)
      v == "true" || v == "yes" || v == "on" || v == "1"
    }

  private def _config_string(value: ConfigurationValue): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case _ => None
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
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_c(op)
  }

  protected final def entity_save_or_throw[T](
    entity: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      tc,
      _entity_uow_authorization(Some(tc.id(entity).collection.name), Some(tc.id(entity)), "update")
    )
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
    changes: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      tc,
      _entity_uow_authorization(Some(tc.id(changes).collection.name), Some(tc.id(changes)), "update")
    )
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    changes: T
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      tc,
      _entity_uow_authorization(Some(tc.id(changes).collection.name), Some(tc.id(changes)), "update")
    )
    exec_or_throw(op)
  }

  protected final def entity_update_c[T](
    id: EntityId,
    patch: T
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      id,
      patch,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_c(op)
  }

  protected final def entity_update_or_throw[T](
    id: EntityId,
    patch: T
  )(using uow: UnitOfWork, tc: EntityPersistentUpdate[T]): Unit = {
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      id,
      patch,
      tc,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "update")
    )
    exec_or_throw(op)
  }

  protected final def entity_delete_c(
    id: EntityId
  )(using uow: UnitOfWork): Consequence[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDelete(
      id,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "delete")
    )
    exec_c(op)
  }

  protected final def entity_delete_or_throw(
    id: EntityId
  )(using uow: UnitOfWork): Unit = {
    val op = UnitOfWorkOp.EntityStoreDelete(
      id,
      _entity_uow_authorization(Some(id.collection.name), Some(id), "delete")
    )
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
    val op = UnitOfWorkOp.EntityStoreSearch(
      query,
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    exec_c(op)
  }

  protected final def entity_search_or_throw[T](
    query: EntityQuery[T]
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): SearchResult[T] = {
    val op = UnitOfWorkOp.EntityStoreSearch(
      query,
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    exec_or_throw(op)
  }

  private def _declared_operation_definition =
    component.flatMap { c =>
      val actionname = _normalize_name(action.name)
      c.operationDefinitions.find { op =>
        val opname = _normalize_name(op.name)
        actionname == opname || actionname.endsWith(opname)
      }
    }

  private def _declared_access =
    _declared_operation_definition.flatMap(_.access)

  private def _declared_entities =
    _declared_operation_definition.map { op =>
      if (op.entityNames.nonEmpty) op.entityNames else op.entityName.toVector
    }.getOrElse(Vector.empty)

  private def _entity_uow_authorization(
    resourceType: Option[String],
    targetId: Option[EntityId],
    accessKind: String
  ): Option[UnitOfWorkAuthorization] = {
    val access = _declared_access
    val entitynames = _declared_entities
    val entityname = entitynames.headOption.orElse(resourceType).getOrElse("")
    val factory = getFactory[org.goldenport.cncf.component.Component.Factory]
    val runtimeentitydescriptor =
      component.flatMap(_.entityRuntimeDescriptor(entityname))
    val entityusage =
      factory
        .flatMap(_.entity_usage_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityoperationkind =
      factory
        .flatMap(_.entity_operation_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityOperationKind).map(EntityOperationKind.parse))
        .orElse(runtimeentitydescriptor.map(_.operationKind))
        .getOrElse(
          entityusage match
            case EntityUsageKind.Executable => EntityOperationKind.Task
            case _ => EntityOperationKind.default
        )
    val entityapplicationdomain =
      factory
        .flatMap(_.entity_application_domain(action, entityname, core))
        .orElse(access.flatMap(_.entityApplicationDomain).map(EntityApplicationDomain.parse))
        .orElse(runtimeentitydescriptor.map(_.applicationDomain))
        .getOrElse(
          entityusage match
            case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
            case _ => EntityApplicationDomain.default
        )
    val operationmodel =
      factory
        .flatMap(_.service_operation_model(action, core))
        .orElse(access.flatMap(_.operationModel).map(ServiceOperationModel.parse))
        .getOrElse(ServiceOperationModel.default)
    val explicitrelations =
      factory
        .map(_.entity_access_relations(action, entityname, accessKind, core))
        .getOrElse(Vector.empty) ++
      access.flatMap(_.relation).map(EntityAccessRelation.parseList).getOrElse(Vector.empty)
    val naturalconditions =
      access.flatMap(_.condition).map(EntityAbacCondition.parseList).getOrElse(Vector.empty)
    val derivedprofile = EntityAuthorizationProfile.derive(
      operationKind = entityoperationkind,
      applicationDomain = entityapplicationdomain,
      operationModel = operationmodel,
      explicitRelations = explicitrelations
    )
    val accessmode =
      factory
        .flatMap(_.entity_access_mode(action, entityname, accessKind, core))
        .orElse(access.flatMap(_.mode).map(EntityAccessMode.parse))
        .getOrElse(derivedprofile.accessMode)
    Some(
      UnitOfWorkAuthorization(
        resourceFamily = "domain",
        resourceType = entitynames.headOption.orElse(resourceType),
        collectionName = targetId.map(_.collection.name).orElse(resourceType),
        targetId = targetId,
        accessKind = accessKind,
        access = access,
        sourceComponentName = component_name_option,
        targetComponentName = component_name_option,
        entityNames = entitynames,
        accessMode = accessmode,
        operationModel = Some(operationmodel),
        entityOperationKind = Some(entityoperationkind),
        entityApplicationDomain = Some(entityapplicationdomain),
        relationRules = derivedprofile.relationRules,
        naturalConditions = naturalconditions
      )
    )
  }

  private def _entity_create_options(
    resourceType: Option[String]
  ): EntityCreateOptions = {
    val access = _declared_access
    val entitynames = _declared_entities
    val entityname = entitynames.headOption.orElse(resourceType).getOrElse("")
    val factory = getFactory[org.goldenport.cncf.component.Component.Factory]
    val runtimeentitydescriptor =
      component.flatMap(_.entityRuntimeDescriptor(entityname))
    val entityusage =
      factory
        .flatMap(_.entity_usage_kind(action, entityname, core))
        .orElse(access.flatMap(_.entityUsage).map(EntityUsageKind.parse))
        .orElse(runtimeentitydescriptor.map(_.usageKind))
        .getOrElse(EntityUsageKind.default)
    val entityapplicationdomain =
      factory
        .flatMap(_.entity_application_domain(action, entityname, core))
        .orElse(access.flatMap(_.entityApplicationDomain).map(EntityApplicationDomain.parse))
        .orElse(runtimeentitydescriptor.map(_.applicationDomain))
        .getOrElse(
          entityusage match
            case EntityUsageKind.PublicContent => EntityApplicationDomain.Cms
            case _ => EntityApplicationDomain.default
        )
    val profiles =
      if (
        entityapplicationdomain == EntityApplicationDomain.Cms ||
        entityusage == EntityUsageKind.PublicContent ||
        access.exists(_.policy.equalsIgnoreCase("public"))
      )
        Set("cms", "publication", "public-content", "public-read")
      else
        Set.empty[String]
    EntityCreateOptions(defaultProfiles = profiles)
  }

  private def _normalize_name(p: String): String =
    Option(p).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")
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
