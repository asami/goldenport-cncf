package org.goldenport.cncf.action

import cats.free.Free
import cats.syntax.flatMap.*
import cats.syntax.functor.*
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
import org.goldenport.cncf.entity.EntitySearchScope
import org.goldenport.cncf.entity.EntityIdentityScope
import org.goldenport.cncf.entity.EntityVisibilityScope
import org.goldenport.cncf.entity.EntityCreateOptions
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.blob.{ContentReferenceAttachResult, ContentReferenceContent, ContentReferenceNormalizeResult, ContentRenderResult, InlineImageAttachResult, InlineImageContent, InlineImageNormalizeResult, InlineImageOccurrence}
import org.goldenport.value.{ContentAttributes, ContentReferenceOccurrence}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.action.AggregateBehavior
import org.goldenport.cncf.information.{
  InformationConflict,
  InformationFieldEvent,
  InformationFieldState,
  Information,
  InformationId,
  InformationIdentityBinding,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpace,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{KnowledgeFrameId, KnowledgeWorkingSetSnapshot}
import org.goldenport.cncf.observability.{CallTreeValueSummary, DslChokepointContext, DslChokepointPhase, DslChokepointRunner}
import org.goldenport.configuration.ConfigurationValue

/*
 * @since   Jan.  6, 2026
 *  version Jan. 21, 2026
 *  version Feb. 25, 2026
 *  version Mar. 30, 2026
 *  version Apr. 29, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def execution_context: ExecutionContext =
    executionContext

  protected final def component_name_option: Option[String] =
    component.flatMap(_.coreOption.map(_.name))

  protected final def exec_pure[A](value: A): ExecUowM[A] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], A](value)

  protected final def exec_from[A](c: Consequence[A]): ExecUowM[A] =
    ConsequenceT.fromConsequence[[X] =>> Program[UnitOfWorkOp, X], A](c)

  protected final def exec_from_calltree[A](
    label: String,
    attributes: Map[String, String] = Map.empty
  )(
    c: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(consequence_with_calltree(label, attributes)(c))

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

  protected final def consequence_with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val calltree = execution_context.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "uow"))
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
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "failure",
            "error" -> Option(e.getMessage).getOrElse(e.getClass.getName)
          ))
          throw e
      }
    } else {
      body
    }
  }
}

trait ActionCallFeaturePart extends BehaviorFeaturePart { self: ActionCall.Core.Holder =>
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
    exec_from_calltree("uow:aggregate:load", _aggregate_calltree_attributes("load", id.collection.name) + ("entity_id" -> id.print)) {
      aggregate_load_c[A](id)
    }

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
    exec_from_calltree("uow:aggregate:load-option", _aggregate_calltree_attributes("load-option", targetid.collection.name) + ("entity_id" -> targetid.print)) {
      aggregate_load_option_c[A](targetid)
    }

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
    exec_from_calltree("uow:aggregate:load", _aggregate_calltree_attributes("load", collectionname) + ("entity_id" -> id.print)) {
      aggregate_load_c[A](collectionname, id)
    }

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
        case Some(collection) => _aggregate_load_record_from_collection(collection, id)
        case None => Consequence.success(None)
      }

  private def _aggregate_load_record_from_collection(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    id: EntityId
  ): Consequence[Option[Record]] = {
    val effectiveid = _canonical_aggregate_entity_id(collection, id)

    def _to_record_(entity: Any): Consequence[Record] =
      _aggregate_raw_record(effectiveid).map {
        case Some(record) =>
          collection.descriptor.persistent.authorizationRecord(entity, record)
        case None =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }.recover {
        case _ =>
          collection.descriptor.persistent.authorizationRecord(entity)
      }

    collection.resolve(effectiveid).flatMap(x => _to_record_(x).map(Some(_))).recoverWith {
      case conclusion if _is_aggregate_record_not_found(conclusion) =>
        given EntityPersistent[Any] =
          collection.descriptor.persistent.asInstanceOf[EntityPersistent[Any]]
        EntityStore.standard().load[Any](effectiveid)(using summon[EntityPersistent[Any]], execution_context).flatMap {
          case Some(entity) => _to_record_(entity).map(Some(_))
          case None => Consequence.success(None)
        }
      case conclusion =>
        Consequence.Failure(conclusion)
    }
  }

  private def _canonical_aggregate_entity_id(
    collection: org.goldenport.cncf.entity.runtime.EntityCollection[Any],
    id: EntityId
  ): EntityId = {
    val cid = collection.descriptor.collectionId
    if (id.collection == cid)
      id
    else
      EntityId(id.major, id.minor, cid, id.timestamp, id.entropy)
  }

  private def _aggregate_raw_record(
    id: EntityId
  ): Consequence[Option[Record]] =
    for {
      cid <- execution_context.entityStoreSpace.dataStoreCollection(id)
      dsid <- execution_context.entityStoreSpace.dataStoreEntryId(id)
      ds <- execution_context.dataStoreSpace.dataStore(cid)
      r <- ds.load(cid, dsid)(using execution_context)
    } yield r

  private def _is_aggregate_record_not_found(
    conclusion: org.goldenport.Conclusion
  ): Boolean = {
    val symptom = conclusion.observation.taxonomy.symptom
    val message = conclusion.show.toLowerCase
    symptom == org.goldenport.observation.Taxonomy.Symptom.NotFound ||
      message.contains("not found") ||
      message.contains("not-found") ||
      message.contains("notfound")
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
    exec_from_calltree("uow:aggregate:search", _aggregate_calltree_attributes("search", collectionname)) {
      aggregate_search_c[A](collectionname, q)
    }

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
    exec_from_calltree("uow:aggregate:create", _aggregate_calltree_attributes("create", entityName) + ("command" -> commandName)) {
      aggregate_create_c(entityName, commandName, action)
    }

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
    exec_from_calltree("uow:aggregate:update", _aggregate_calltree_attributes("update", entityName) + ("command" -> commandName, "entity_id" -> targetId.print)) {
      aggregate_update_c(entityName, targetId, commandName, action)
    }

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

  private def _aggregate_calltree_attributes(
    operation: String,
    aggregateName: String
  ): Map[String, String] =
    Map(
      "dsl" -> "uow",
      "operation" -> operation,
      "aggregate" -> aggregateName
    )

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
    consequence_with_calltree("uow:view:load", _view_calltree_attributes("load", collectionname) + ("entity_id" -> id.print)) {
      browser.browser[A](collectionname).find_with_context(id)(using execution_context)
    }

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
    consequence_with_calltree("uow:view:load", _view_calltree_attributes("load", collectionname, Some(viewname)) + ("entity_id" -> id.print)) {
      browser.browser[A](collectionname, viewname).find_with_context(id)(using execution_context)
    }

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
    consequence_with_calltree("uow:view:search", _view_calltree_attributes("search", collectionname)) {
      browser.browser[A](collectionname).query_with_context(q)(using execution_context).map(_to_search_result(q, _))
    }

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
    consequence_with_calltree("uow:view:search", _view_calltree_attributes("search", collectionname, Some(viewname))) {
      browser.browser[A](collectionname, viewname).query_with_context(q)(using execution_context).map(_to_search_result(q, _))
    }

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

  private def _view_calltree_attributes(
    operation: String,
    collectionname: String,
    viewname: Option[String] = None
  ): Map[String, String] =
    Map(
      "dsl" -> "uow",
      "operation" -> operation,
      "view" -> collectionname
    ) ++ viewname.map(x => Map("view_name" -> x)).getOrElse(Map.empty)
}

trait BehaviorHttpPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>

  // Declarative DSL (UoW / Free)
  protected final def http_get(
    path: String,
    headers: Map[String, String] = Map.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_get(path, headers)
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

  protected final def http_post_bag(
    path: String,
    body: Option[org.goldenport.bag.Bag] = None,
    headers: Map[String, String] = Map.empty
  ): ExecUowM[HttpResponse] = {
    val op = _op_http_post_bag(path, body, headers)
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
  private def _op_http_get(
    path: String,
    headers: Map[String, String] = Map.empty
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpGet(path, headers)

  private def _op_http_post(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPost(path, body, headers)

  private def _op_http_post_bag(
    path: String,
    body: Option[org.goldenport.bag.Bag],
    headers: Map[String, String]
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPostBag(path, body, headers)

  private def _op_http_put(
    path: String,
    body: Option[String],
    headers: Map[String, String]
  ): UnitOfWorkOp[HttpResponse] =
    UnitOfWorkOp.HttpPut(path, body, headers)
}

trait BehaviorInformationPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def information_space: ExecUowM[InformationSpace] =
    exec_from(_information_space)

  protected final def information_register(
    domain: String,
    records: Vector[Record]
  ): ExecUowM[Vector[Information]] =
    exec_from_calltree("uow:information:register", _information_attributes("register", domain) + ("record_count" -> records.size.toString)) {
      _information_space.flatMap(_.registerInformation(domain, records))
    }

  protected final def information_update(
    informationid: InformationId,
    workingdata: Record
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:update", _information_attributes("update", informationid)) {
      _information_space.flatMap(_.updateInformation(informationid, workingdata))
    }

  protected final def information_append_field_event(
    informationid: InformationId,
    event: InformationFieldEvent
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:field-event:append", _information_attributes("field-event-append", informationid) + ("field_path" -> event.fieldPath) + ("state" -> event.state.value) + ("source" -> event.source)) {
      _information_space.flatMap(_.appendFieldEvent(informationid, event))
    }

  protected final def information_append_field_events(
    informationid: InformationId,
    events: Vector[InformationFieldEvent]
  ): ExecUowM[Unit] =
    events.foldLeft(exec_pure(())) { (z, event) =>
      z.flatMap { _ =>
        information_append_field_event(informationid, event).map(_ => ())
      }
    }

  protected final def information_validate(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:validate", _information_attributes("validate", informationid)) {
      _information_space.flatMap(_.validateInformation(informationid))
    }

  protected final def information_confirm(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:confirm", _information_attributes("confirm", informationid)) {
      _information_space.flatMap(_.confirmInformation(informationid))
    }

  protected final def information_reject(
    informationid: InformationId,
    reason: String
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reject", _information_attributes("reject", informationid)) {
      _information_space.flatMap(_.rejectInformation(informationid, reason))
    }

  protected final def information_reopen(
    informationid: InformationId
  ): ExecUowM[Information] =
    exec_from_calltree("uow:information:reopen", _information_attributes("reopen", informationid)) {
      _information_space.flatMap(_.reopenInformation(informationid))
    }

  protected final def information_publish(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): ExecUowM[InformationPublicationStatus] =
    exec_from_calltree("uow:information:publish", _information_attributes("publish", informationid) + ("target" -> target)) {
      _information_space.flatMap(_.publishInformation(informationid, target, message, knowledgeframeid))
    }

  protected final def information_fail_publication(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): ExecUowM[InformationPublicationStatus] =
    exec_from_calltree("uow:information:publish-failure", _information_attributes("publish-failure", informationid) + ("target" -> target)) {
      _information_space.flatMap(_.failInformationPublication(informationid, target, message, knowledgeframeid))
    }

  protected final def information_add_resolution_candidate(
    informationid: InformationId,
    fieldpath: String,
    candidatelabel: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree("uow:information:candidate:add", _information_attributes("candidate-add", informationid) + ("field_path" -> fieldpath)) {
      _information_space.flatMap(_.addResolutionCandidate(informationid, fieldpath, candidatelabel, binding, confidence, evidence))
    }

  protected final def information_select_resolution_candidate(
    informationid: InformationId,
    candidatekey: String
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree("uow:information:candidate:select", _information_candidate_attributes("candidate-select", informationid, candidatekey)) {
      _information_space.flatMap(_.selectResolutionCandidate(informationid, candidatekey))
    }

  protected final def information_clear_resolution_candidate(
    informationid: InformationId,
    candidatekey: String
  ): ExecUowM[InformationResolutionCandidate] =
    exec_from_calltree("uow:information:candidate:clear", _information_candidate_attributes("candidate-clear", informationid, candidatekey)) {
      _information_space.flatMap(_.clearResolutionCandidate(informationid, candidatekey))
    }

  protected final def information_materialize(
    information: Information
  ): ExecUowM[KnowledgeWorkingSetSnapshot] =
    exec_from_calltree("uow:information:materialize", _information_attributes("materialize", information.id)) {
      Consequence.success(InformationSpace.materializeInformation(information))
    }

  protected final def information_option(
    informationid: InformationId
  ): ExecUowM[Option[Information]] =
    exec_from_calltree("uow:information:option", _information_attributes("option", informationid)) {
      _information_space.map(_.getInformation(informationid))
    }

  protected final def information_validation_issues(
    informationid: InformationId
  ): ExecUowM[Vector[InformationValidationIssue]] =
    exec_from_calltree("uow:information:validation-issues", _information_attributes("validation-issues", informationid)) {
      _information_space.map(_.validationIssues(informationid))
    }

  protected final def information_add_conflict(
    informationid: InformationId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  ): ExecUowM[InformationConflict] =
    exec_from_calltree("uow:information:conflict:record", _information_attributes("conflict-record", informationid) + ("field_path" -> fieldpath)) {
      _information_space.flatMap(_.recordConflict(informationid, fieldpath, informationvalue, rdfvalue, severity))
    }

  protected final def information_resolve_conflict(
    informationid: InformationId,
    conflictkey: String,
    decision: String
  ): ExecUowM[InformationConflict] =
    exec_from_calltree("uow:information:conflict:resolve", Map("operation" -> "conflict-resolve", "information_id" -> informationid.print, "conflict_key" -> conflictkey)) {
      _information_space.flatMap(_.resolveConflict(informationid, conflictkey, decision))
    }

  private def _information_space: Consequence[InformationSpace] =
    component match {
      case Some(component) => Consequence.success(component.informationSpace)
      case None => Consequence.serviceUnavailable("InformationSpace is unavailable: component is not bound.")
    }

  private def _information_attributes(
    operation: String,
    domain: String
  ): Map[String, String] =
    Map("operation" -> operation, "domain" -> domain)

  private def _information_attributes(
    operation: String,
    informationid: InformationId
  ): Map[String, String] =
    Map("operation" -> operation, "information_id" -> informationid.print)

  private def _information_candidate_attributes(
    operation: String,
    informationid: InformationId,
    candidatekey: String
  ): Map[String, String] =
    Map("operation" -> operation, "information_id" -> informationid.print, "candidate_key" -> candidatekey)
}

trait ActionCallHttpPart extends BehaviorHttpPart with ActionCallFeaturePart { self: ActionCall.Core.Holder =>
}

trait ProviderBehaviorFeaturePart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>
  protected final def provider_config_string(
    key: String,
    default: String
  ): String =
    executionContext.runtime.resolvedParameters.get(key)
      .map(_.value)
      .flatMap(_configuration_string)
      .getOrElse(default)

  protected final def provider_config_int(
    key: String,
    default: Int
  ): Int =
    executionContext.runtime.resolvedParameters.get(key)
      .map(_.value)
      .flatMap(_configuration_string)
      .flatMap(_.toIntOption)
      .getOrElse(default)

  protected final def provider_step[A](
    label: String,
    attributes: Map[String, String] = Map.empty
  )(
    body: => Consequence[A]
  ): ExecUowM[A] =
    exec_from(_provider_step_consequence(label, attributes)(body))

  private def _provider_step_consequence[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val calltree = execution_context.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "provider-step"))
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
        }
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map(
            "outcome" -> "failure",
            "error" -> Option(e.getMessage).getOrElse(e.getClass.getName)
          ))
          throw e
      }
    } else {
      body
    }
  }

  private def _configuration_string(
    value: ConfigurationValue
  ): Option[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Some(v)
      case ConfigurationValue.NumberValue(v) => Some(v.toString)
      case ConfigurationValue.BooleanValue(v) => Some(v.toString)
      case _ => None
    }
}

trait ActionCallBlobPart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  protected final def blob_normalize_inline_images(
    content: InlineImageContent
  ): ExecUowM[InlineImageNormalizeResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.BlobNormalizeInlineImages(content)))

  protected final def blob_attach_inline_images(
    sourceEntityId: String,
    occurrences: Vector[InlineImageOccurrence]
  ): ExecUowM[InlineImageAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.BlobAttachInlineImages(sourceEntityId, occurrences)))

  protected final def content_normalize_references(
    content: ContentReferenceContent
  ): ExecUowM[ContentReferenceNormalizeResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentNormalizeReferences(content)))

  protected final def content_attach_references(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[ContentReferenceAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentAttachReferences(sourceEntityId, references)))

  protected final def content_validate_references(
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[Unit] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentValidateReferences(references)))

  protected final def content_sync_inline_references(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ): ExecUowM[ContentReferenceAttachResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentSyncInlineReferences(sourceEntityId, references)))

  protected final def content_render_html(
    content: ContentAttributes
  ): ExecUowM[ContentRenderResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.ContentRenderHtml(content)))
}

trait ActionCallEntityStorePart extends ActionCallFeaturePart { self: ActionCall.Core.Holder =>
  private def _emit_entity_access(
    name: String,
    attributes: Record
  ): Unit = {
    component.flatMap(_.subsystem).map(_.entityAccessMetrics).getOrElse(EntityAccessMetricsRegistry.shared).record(name, attributes)
    val _ = execution_context.observability.emitDebug(
      execution_context.cncfCore.scope,
      name,
      attributes
    )
  }

  private def _calltree_metric_attributes(
    name: String,
    attributes: Record
  ): Map[String, String] =
    (Vector("metric" -> name) ++
      attributes.asMap.toVector
        .sortBy(_._1)
        .map { case (key, value) =>
          key -> _truncate_calltree_metric_text(_sanitize_calltree_metric_value(key, value).toString, 1000)
        }).toMap

  private def _sanitize_calltree_metric_value(
    key: String,
    value: Any
  ): Any =
    if (_is_sensitive_calltree_metric_key(key)) "***" else value

  private def _is_sensitive_calltree_metric_key(
    key: String
  ): Boolean = {
    val normalized = key.toLowerCase(java.util.Locale.ROOT)
    normalized.contains("password") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie")
  }

  private def _truncate_calltree_metric_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

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

  private def _entity_search_working_set_loading_attributes(
    query: EntityQuery[?],
    state: org.goldenport.cncf.entity.runtime.WorkingSetLoadState
  ): Record = Record.dataAuto(
    "entity" -> query.collection.name,
    "source" -> "entity-store",
    "outcome" -> "fallback",
    "reason" -> "working-set-loading",
    "workingSetState" -> state.label,
    "query" -> query.query.toString
  )

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

  private def _canonical_entity_id(
    id: EntityId
  ): EntityId =
    component
      .flatMap(_.entitySpace.entityOption[Any](id.collection.name))
      .map { collection =>
        val cid = collection.descriptor.collectionId
        if (id.collection == cid)
          id
        else
          EntityId(id.major, id.minor, cid, id.timestamp, id.entropy)
      }
      .getOrElse(id)

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
    val effectiveid = _canonical_entity_id(id)
    _emit_entity_access("entity.load.start", _entity_load_attributes(effectiveid, "unknown", "start"))
    val effectivetc = _effective_entity_persistent(effectiveid.collection, tc)
    if (!_working_set_enabled) {
      _emit_entity_access("entity.load.bypass.entity-space", _entity_load_attributes(effectiveid, "entity-space", "bypass"))
      val op = UnitOfWorkOp.EntityStoreLoad(
        effectiveid,
        effectivetc,
        _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "read"),
        _declared_visibility_scope
      )
      return ConsequenceT.liftF(Free.liftF(op))
    }
    component.flatMap(_.entitySpace.entityOption(effectiveid.collection).map(_.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[T]])) match {
      case Some(collection) =>
        _emit_entity_access("entity.load.try.entity-space", _entity_load_attributes(effectiveid, "entity-space", "try"))
        collection.resolve(effectiveid) match {
          case Consequence.Success(entity) =>
            _emit_entity_access("entity.load.hit.entity-space", _entity_load_attributes(effectiveid, "entity-space", "hit"))
            exec_from(_authorize_entity_load_hit(effectiveid, entity, effectivetc))
          case Consequence.Failure(conclusion) if _is_entity_not_found(conclusion) =>
            _emit_entity_access("entity.load.fallback.entity-store", _entity_load_attributes(effectiveid, "entity-store", "fallback"))
            val op = UnitOfWorkOp.EntityStoreLoad(
              effectiveid,
              effectivetc,
              _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "read"),
              _declared_visibility_scope
            )
            ConsequenceT.liftF(Free.liftF(op))
          case Consequence.Failure(conclusion) =>
            exec_from(Consequence.Failure(conclusion))
        }
      case None =>
        _emit_entity_access("entity.load.fallback.entity-store", _entity_load_attributes(effectiveid, "entity-store", "fallback"))
        val op = UnitOfWorkOp.EntityStoreLoad(
          effectiveid,
          effectivetc,
          _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "read"),
          _declared_visibility_scope
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
    if (!org.goldenport.cncf.entity.EntityAccessScopePolicy.visibilityRecordVisible(id.collection, tc.toRecord(entity), _declared_visibility_scope))
      return Consequence.success(None)
    _entity_uow_authorization(Some(id.collection.name), Some(id), "read") match {
      case Some(authorization) =>
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          authorization,
          _ => _entity_store_record(id).map {
            case Some(record) => Some(tc.authorizationRecord(entity, record))
            case None => Some(tc.authorizationRecord(entity))
          }
        ).map(_ => Some(entity))
      case None =>
        Consequence.success(Some(entity))
    }
  }

  private def _entity_store_record(
    id: EntityId
  ): Consequence[Option[Record]] = {
    given ExecutionContext = execution_context
    for {
      cid <- execution_context.entityStoreSpace.dataStoreCollection(id)
      dsid <- execution_context.entityStoreSpace.dataStoreEntryId(id)
      ds <- execution_context.dataStoreSpace.dataStore(cid)
      rec <- ds.load(cid, dsid)
    } yield rec
  }

  protected final def entity_load[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[T] = {
    entity_load_option(id).flatMap { x =>
      val r = Consequence.successOrEntityNotFound(x)(id)
      exec_from(r)
    }
  }

  protected final def entity_load_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[T] =
    entity_load_option_internal(id).flatMap { x =>
      exec_from(Consequence.successOrEntityNotFound(x)(id))
    }

  protected final def entity_load_option_internal[T](
    id: EntityId
  )(using tc: EntityPersistent[T]): ExecUowM[Option[T]] = {
    val effectiveid = _canonical_entity_id(id)
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreLoadDirect(
      effectiveid,
      _effective_entity_persistent(effectiveid.collection, tc)
    )))
  }

  protected final def entity_save[T](
    entity: T
  )(using tc: EntityPersistent[T]): ExecUowM[Unit] = {
    val effectivetc = _effective_entity_persistent(tc.id(entity).collection, tc)
    val op = UnitOfWorkOp.EntityStoreSave(
      entity,
      effectivetc,
      _entity_uow_authorization(Some(effectivetc.id(entity).collection.name), Some(effectivetc.id(entity)), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_update[T](
    changes: T
  )(using tc: EntityPersistent[T]): ExecUowM[Unit] = {
    val effectivetc = _effective_entity_persistent(tc.id(changes).collection, tc)
    val op = UnitOfWorkOp.EntityStoreUpdate(
      changes,
      effectivetc,
      _entity_uow_authorization(Some(effectivetc.id(changes).collection.name), Some(effectivetc.id(changes)), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  // Patch update with explicit target id.
  // This is intended for Update.PatchShape where id is excluded from patch object.
  protected final def entity_update[T](
    id: EntityId,
    patch: T
  )(using tc: EntityPersistentUpdate[T]): ExecUowM[Unit] = {
    val effectiveid = _canonical_entity_id(id)
    val op = UnitOfWorkOp.EntityStoreUpdateById(
      effectiveid,
      patch,
      tc,
      _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "update")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete(id: EntityId): ExecUowM[Unit] = {
    val effectiveid = _canonical_entity_id(id)
    val op = UnitOfWorkOp.EntityStoreDelete(
      effectiveid,
      _entity_uow_authorization(Some(effectiveid.collection.name), Some(effectiveid), "delete")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_delete_hard(id: EntityId): ExecUowM[Unit] = {
    val op = UnitOfWorkOp.EntityStoreDeleteHard(_canonical_entity_id(id))
    ConsequenceT.liftF(Free.liftF(op))
  }

  protected final def entity_search[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    val effectivequery = _with_declared_visibility(query)
    _emit_entity_access("entity.search.start", _entity_search_attributes(effectivequery, "unknown", "start"))
    val effectivetc = _effective_entity_persistent(effectivequery.collection, tc)
    if (effectivequery.scope == EntitySearchScope.Store)
      return _entity_store_search_direct(effectivequery, effectivetc)
    if (!_working_set_enabled) {
      _emit_entity_access("entity.search.bypass.entity-space", _entity_search_attributes(effectivequery, "entity-space", "bypass"))
      return _entity_store_search_direct(effectivequery, effectivetc)
    }
    component.flatMap(_.entitySpace.entityOption(effectivequery.collection).map(_.asInstanceOf[org.goldenport.cncf.entity.runtime.EntityCollection[T]])) match {
      case Some(collection) =>
        if (_bypass_entity_space_resident_search) {
          _emit_entity_access("entity.search.bypass.entity-space", _entity_search_attributes(effectivequery, "entity-space", "bypass"))
          _entity_store_search_direct(effectivequery, tc)
        } else {
          _emit_entity_access("entity.search.try.entity-space", _entity_search_attributes(effectivequery, "entity-space", "try"))
          if (collection.shouldFallbackToStoreForWorkingSet(effectivequery)) {
            val state = collection.workingSetStatus.state
            _emit_entity_access("entity.search.fallback.entity-store", _entity_search_attributes(effectivequery, "entity-store", "fallback"))
            if (collection.workingSetStatus.isInitializing)
              _emit_entity_access("entity.search.fallback.working-set-loading", _entity_search_working_set_loading_attributes(effectivequery, state))
            return _entity_store_search_direct(effectivequery, effectivetc)
          }
          val hasworkingsetpolicy =
            collection.descriptor.plan.workingSetPolicy match {
              case Some(org.goldenport.cncf.entity.runtime.WorkingSetPolicy.Disabled) | None => false
              case Some(_) => true
            }
          val hasresident =
            effectivequery.scope match {
              case EntitySearchScope.WorkingSet =>
                hasworkingsetpolicy && collection.workingSetSearchAvailable
              case EntitySearchScope.Store =>
                collection.storage.storeRealm.values.nonEmpty ||
                  collection.storage.memoryRealm.exists(_.values.nonEmpty)
            }
          if (hasresident) {
            _emit_entity_access("entity.search.hit.entity-space", _entity_search_attributes(effectivequery, "entity-space", "hit"))
            val authorization = _entity_uow_authorization(Some(effectivequery.collection.name), None, "search/list")
            exec_from(
              collection.search(effectivequery)(using execution_context).flatMap { result =>
                authorization
                  .map(OperationAccessPolicy.filterVisibleSearchResult(_, result, tc)(using execution_context))
                  .getOrElse(Consequence.success(result))
              }
            )
          } else {
            _emit_entity_access("entity.search.fallback.entity-store", _entity_search_attributes(effectivequery, "entity-store", "fallback"))
            _entity_store_search_direct(effectivequery, effectivetc)
          }
        }
      case None =>
        _emit_entity_access("entity.search.fallback.entity-store", _entity_search_attributes(effectivequery, "entity-store", "fallback"))
        _entity_store_search_direct(effectivequery, effectivetc)
    }
  }

  protected final def entity_search_internal[T](
    query: EntityQuery[T]
  )(using tc: EntityPersistent[T]): ExecUowM[SearchResult[T]] = {
    val effectivetc = _effective_entity_persistent(query.collection, tc)
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreSearchInternal(query, effectivetc)))
  }

  protected final def entity_unique_value_exists[T](
    collection: EntityCollectionId,
    fieldName: String,
    value: String,
    excludeId: Option[EntityId] = None,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext,
    includeEntityIdEntropy: Boolean = false
  )(using tc: EntityPersistent[T]): ExecUowM[Boolean] =
    ConsequenceT.liftF(
      Free.liftF(
        UnitOfWorkOp.EntityStoreUniqueValueExists(
          collection,
          fieldName,
          value,
          excludeId,
          scope,
          includeEntityIdEntropy,
          tc
        )
      )
    )

  protected final def entity_resolve_identity[T](
    collection: EntityCollectionId,
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean = true,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext
  )(using tc: EntityPersistent[T]): ExecUowM[Option[EntityId]] =
    ConsequenceT.liftF(
      Free.liftF(
        UnitOfWorkOp.EntityStoreResolveIdentity(
          collection,
          value,
          fieldNames,
          includeEntityIdEntropy,
          scope,
          tc
        )
      )
    )

  private def _entity_store_search[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ): ExecUowM[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearch(
      _with_declared_visibility(query),
      tc,
      _entity_uow_authorization(Some(query.collection.name), None, "search/list")
    )
    ConsequenceT.liftF(Free.liftF(op))
  }

  private def _entity_store_load_direct[T](
    id: EntityId,
    tc: EntityPersistent[T]
  ): ExecUowM[Option[T]] = {
    _emit_entity_access("entity.load.bypass.entity-space", _entity_load_attributes(id, "entity-space", "bypass"))
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, tc)
    ConsequenceT.liftF(Free.liftF(op))
  }

  private def _entity_store_search_direct[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ): ExecUowM[SearchResult[T]] = {
    val op = UnitOfWorkOp.EntityStoreSearchDirect(
      _with_declared_visibility(query),
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

  private def _working_set_enabled: Boolean =
    execution_context.framework.workingSetEnabled &&
      !org.goldenport.cncf.context.GlobalRuntimeContext.current.exists { global =>
        global.runtimeMode == RunMode.Command || global.runtimeMode == RunMode.Client
      }

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
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, _effective_entity_persistent(id.collection, tc))
    exec_c(op)
  }

  protected final def entity_load_option_or_throw[T](
    id: EntityId
  )(using uow: UnitOfWork, tc: EntityPersistent[T]): Option[T] = {
    val op = UnitOfWorkOp.EntityStoreLoadDirect(id, _effective_entity_persistent(id.collection, tc))
    exec_or_throw(op)
  }

  private def _effective_entity_persistent[T](
    collectionId: EntityCollectionId,
    fallback: EntityPersistent[T]
  ): EntityPersistent[T] =
    component
      .flatMap(_.entitySpace.entityOption(collectionId))
      .map(_.descriptor.persistent.asInstanceOf[EntityPersistent[T]])
      .getOrElse(fallback)

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
        val inputname = _normalize_name(op.inputType)
        actionname == opname ||
          actionname.endsWith(opname) ||
          (inputname.nonEmpty && actionname == inputname)
      }
    }

  private def _declared_access =
    _declared_operation_definition.flatMap(_.access)

  private def _declared_visibility_scope: Option[EntityVisibilityScope] =
    _declared_operation_definition.flatMap(_.visibility.flatMap(EntityVisibilityScope.parseOption))

  private def _with_declared_visibility[T](
    query: EntityQuery[T]
  ): EntityQuery[T] =
    if (query.visibilityScope.nonEmpty)
      query
    else
      query.copy(visibilityScope = _declared_visibility_scope)

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
        .orElse(runtimeentitydescriptor.map(_.effectiveOperationKind))
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
        visibilityScope = _declared_visibility_scope,
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
