package org.goldenport.cncf.component

import java.nio.file.{Files, Path, Paths}
import java.lang.reflect.InvocationTargetException
import cats.effect.Ref
import cats.data.State
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.backend.collaborator.{Collaborator, CollaboratorFactory}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.component.repository.ComponentSource
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.datastore.{DataStore, TotalCountCapability}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.value.NominalScalar
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityConcurrencyPolicy, EntityPersistable, EntityPersistent, EntityQuery, EntityRevisionBinding, EntityRevisionModelKind, EntityRevisionModelMetadata, EntityRevisionRepresentation, EntityStore}
import org.goldenport.cncf.entity.aggregate.{AggregateAssembler, AggregateBuilder, AggregateCollection, AggregateSpace, AggregateDefinition, ContextualAggregateBuilder, ContextualAggregateCount, ContextualAggregateQuery}
import org.goldenport.cncf.event.{ActionCallDispatcher, EventBus, EventReception, EventStore, EntitySubscriptionLimit}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimeDescriptor, EntityRuntimePlan, EntitySpace, EntityStorage, PartitionedMemoryRealm, PartitionStrategy, WorkingSetDefinition, WorkingSetDescriptor, WorkingSetInitializer, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.view.{Browser, ContextualBrowserCount, ContextualBrowserFind, ContextualBrowserQuery, ContextualViewBuilder, ViewDefinition, ViewBuilder, ViewCollection, ViewSpace}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.statemachine.{CmlStateMachineDefinitionProvider, CollectionStateMachinePlanner, CollectionStateMachinePlannerProvider, CollectionTransitionRule, CollectionTransitionRuleProvider, TransitionTrigger, TransitionRule}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.spi.SpiResolver
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, XString}
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.workflow.{ContinuationRuntimeSource, ContinuationSpiAdapter, IssuedWorkOrderPersistence, GeneratedProvidedApiAbi, GeneratedProvidedApiMetadataProvider, GeneratedWorkflowAbi, GeneratedWorkflowMetadataProvider, StateMachineProvidedApiDispatcher, StateMachineProvidedApiProgramSource, StateMachineProviderResolver, StateMachineProviderSource}
import org.simplemodeling.model.value.BaseContent
import scala.util.Try

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] trait ComponentFactoryReflectionPart { self: ComponentFactory =>

  private[component] def _load_entity(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Any] =
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.resolve(id).recoverWith {
          case _ =>
            given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
            EntityStore.standard()
              .load[Any](id)
              .flatMap {
                case Some(s) => Consequence.success(s)
                case None => Consequence.entityNotFound(s"${_entity_class_name(entityname)} not found: ${id.value}")
              }
        }
      case None =>
        given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
        EntityStore.standard()
          .load[Any](id)
          .flatMap {
            case Some(s) => Consequence.success(s)
            case None => Consequence.entityNotFound(s"${_entity_class_name(entityname)} not found: ${id.value}")
          }
    }

  private[component] def _search_entities(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val cid = _bootstrap_collection_id(component, entityname)
    val query = EntityQuery[Any](cid, q)
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.search(query).map(_.data).flatMap { xs =>
          if (xs.nonEmpty)
            Consequence.success(xs)
          else
            EntityStore.standard().search[Any](query).map(_.data)
        }.recoverWith { case _ =>
          EntityStore.standard().search[Any](query).map(_.data)
        }
      case None =>
        EntityStore.standard().search[Any](query).map(_.data)
    }
  }

  private[component] def _count_entities(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Int] = {
    val cid = _bootstrap_collection_id(component, entityname)
    val query = EntityQuery[Any](cid, _with_count_controls(_sanitize_query_record(_query_record(q)), q))
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.search(query).flatMap { result =>
          result.totalCount.map(Consequence.success).getOrElse {
            EntityStore.standard().search[Any](query).flatMap(_total_count_from_result)
          }
        }.recoverWith { case _ =>
          EntityStore.standard().search[Any](query).flatMap(_total_count_from_result)
        }
      case None =>
        EntityStore.standard().search[Any](query).flatMap(_total_count_from_result)
    }
  }

  private[component] def _total_count_from_result(
    result: SearchResult[Any]
  ): Consequence[Int] =
    result.totalCount
      .map(Consequence.success)
      .getOrElse(Consequence.operationInvalid("Total count was not returned by entity search"))

  private[component] def _entity_to_aggregate(
    component: Component,
    entityname: String,
    entity: Any
  ): Consequence[Any] =
    for {
      module <- Consequence.fromOption(
        _generated_aggregate_module(component, entityname),
        s"Aggregate module not found for ${entityname}"
      )
      record <- Consequence.fromTry(Try(_entity_to_record(component, entityname, entity)))
      aggregate <- component.factory.map(
        _.createAggregateFromRecord(entityname, record, _invoke_create_from_record(module, record))
      ).getOrElse(_invoke_create_from_record(module, record))
    } yield aggregate

  private[component] def _entity_to_view(
    component: Component,
    entityname: String,
    entity: Any
  ): Consequence[Any] =
    _entity_to_view(component, entityname, None, entity)

  private[component] def _entity_to_view(
    component: Component,
    entityname: String,
    projectionname: Option[String],
    entity: Any
  ): Consequence[Any] =
    for {
      module <- Consequence.fromOption(
        _generated_view_module(component, entityname, projectionname),
        s"View module not found for ${entityname}"
      )
      record <- Consequence.fromTry(Try(_entity_to_record(component, entityname, entity)))
      view <- _invoke_create_from_record(module, record)
    } yield view

  private[component] def _entity_to_record(
    component: Component,
    entityname: String,
    entity: Any
  ): Record = {
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    summon[EntityPersistent[Any]].toRecord(entity)
  }

  private[component] def _entity_id(
    component: Component,
    entityname: String,
    entity: Any
  ): EntityId = {
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    summon[EntityPersistent[Any]].id(entity)
  }

  private def _invoke_create_from_record(
    module: AnyRef,
    record: Record
  ): Consequence[Any] =
    module match {
      case m: AggregateAssembler[?] =>
        m.asInstanceOf[AggregateAssembler[Any]].create_from_record(record)
      case _ =>
        val methods = module.getClass.getMethods.iterator
          .filter { m =>
            val params = m.getParameterTypes
            (m.getName == "createC" || m.getName == "create") &&
            params.length == 1 &&
            params.head.isAssignableFrom(record.getClass)
          }
          .toSeq
          .sortBy(m => if (m.getName == "createC") 0 else 1)
        val results = methods.flatMap { m =>
          try Some(Right(m.invoke(module, record)))
          catch {
            case e: InvocationTargetException =>
              val cause = Option(e.getTargetException).getOrElse(e)
              Some(Left(s"${m.getName}(${m.getParameterTypes.head.getSimpleName}) failed: ${cause.getClass.getSimpleName}: ${cause.getMessage}"))
            case e: Throwable =>
              Some(Left(s"${m.getName}(${m.getParameterTypes.head.getSimpleName}) failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
          }
        }
        results.collectFirst { case Right(value) => value } match {
            case Some(c: Consequence[?]) => c.asInstanceOf[Consequence[Any]]
            case Some(value) => Consequence.success(value)
            case None =>
              val errors = results.collect { case Left(msg) => msg }
              if (methods.isEmpty)
                Consequence.operationNotFound(s"create(record):${module.getClass.getName}")
              else
                Consequence.argumentInvalid(errors.mkString("; "))
          }
    }

  private[component] def _invoke_member_setter(
    aggregate: Any,
    module: AnyRef,
    membername: String,
    members: Vector[Any]
  ): Consequence[Any] =
    module match {
      case m: AggregateAssembler[?] =>
        m.asInstanceOf[AggregateAssembler[Any]].attach_member(aggregate, membername, members)
      case _ =>
        Consequence.operationNotFound(s"AggregateAssembler:${module.getClass.getName}")
    }

  private[component] def _bootstrap_collection_id(
    component: Component,
    entityname: String
  ): EntityCollectionId =
    _runtime_descriptor_for(component, entityname)
      .map(_.collectionId)
      .orElse {
        _generated_entity_module(component, entityname)
          .flatMap(_extract_collection_id)
      }
      .getOrElse(EntityCollectionId("sys", "sys", _entity_collection_id_part(entityname)))

  private def _entity_collection_id_part(
    name: String
  ): String =
    NamingConventions.toNormalizedSegment(name).replace('-', '_')

  private[component] def _extract_schema(
    module: AnyRef
  ): Option[Schema] =
    Try(module.getClass.getMethod("schema").invoke(module)).toOption.collect {
      case s: Schema => s
    }

  private[component] def _bootstrap_entity_persistent(
    component: Component,
    entityname: String
  ): EntityPersistent[Any] = {
    val module = _generated_entity_module(component, entityname)
    val persistent =
      module.flatMap(_extract_entity_persistent).orElse {
        _generated_entity_persistent_module(component, entityname).flatMap(x => _as_entity_persistent(x, module))
      }
    persistent.getOrElse(_entity_persistent_any)
  }

  private[component] def _runtime_descriptors_for(
    component: Component
  ): Vector[EntityRuntimeDescriptor] = {
    val entities =
      (
        _entity_collection_names(component) ++
          _programmatic_entity_runtime_plans(component).map(_.entityName)
      ).map(_normalize_entity_name).toSet
    val owned =
      (
        _runtime_component_descriptors_for(component)
          .flatMap(_.entityRuntimeDescriptors) ++
          component.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
      ).distinct
    val descriptors =
      if (owned.nonEmpty) owned else _runtime_entity_descriptors
    descriptors.filter { d =>
      val entity = _normalize_entity_name(d.entityName)
      val collection = _normalize_entity_name(d.collectionId.name)
      entities.contains(entity) || entities.contains(collection)
    }
  }

  private def _runtime_descriptor_for(
    component: Component,
    entityname: String
  ): Option[EntityRuntimeDescriptor] = {
    val normalized = entityname.trim.toLowerCase
    _runtime_descriptors_for(component).find { d =>
      d.entityName.trim.toLowerCase == normalized || d.collectionId.name.trim.toLowerCase == normalized
    }
  }

  private def _generated_entity_persistent_module(
    component: Component,
    entityname: String
  ): Option[AnyRef] = {
    val packagenames = _generated_module_package_names(component)
    val classname = _entity_class_name(entityname)
    val candidates = packagenames.flatMap { pkg =>
      Vector(
        s"${pkg}.entity.${classname}$$given_EntityPersistent_${classname}$$",
        s"${pkg}.entity.read.${classname}$$given_EntityPersistent_${classname}$$",
        s"${pkg}.entity.aggregate.${classname}$$given_EntityPersistent_${classname}$$",
        s"${pkg}.view.${classname}$$given_EntityPersistent_${classname}$$",
        s"${pkg}.entity.view.${classname}$$given_EntityPersistent_${classname}$$",
        s"${pkg}.entity.operation.${classname}$$given_EntityPersistent_${classname}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private[component] def _generated_entity_module(
    component: Component,
    entityname: String
  ): Option[AnyRef] = {
    val packagenames = _generated_module_package_names(component)
    val classname = _entity_class_name(entityname)
    val candidates = packagenames.flatMap { pkg =>
      Vector(
        s"${pkg}.entity.${classname}$$",
        s"${pkg}.entity.aggregate.${classname}$$",
        s"${pkg}.entity.operation.${classname}$$",
        s"${pkg}.entity.read.${classname}$$",
        s"${pkg}.entity.view.${classname}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private def _generated_view_module(
    component: Component,
    entityname: String,
    projectionname: Option[String] = None
  ): Option[AnyRef] = {
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val classname = _entity_class_name(entityname)
    val projectiontoken = projectionname.map(_.trim).filter(_.nonEmpty).map(_snake_case)
    val candidates = packagename.toVector.flatMap { pkg =>
      projectiontoken match {
        case Some(projection) =>
          Vector(
            s"${pkg}.entity.view.${projection}.${classname}$$",
            s"${pkg}.view.${projection}.${classname}$$"
          )
        case None =>
          Vector(
            s"${pkg}.entity.view.${classname}$$",
            s"${pkg}.view.${classname}$$"
          )
      }
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private[component] def _generated_aggregate_module(
    component: Component,
    entityname: String
  ): Option[AnyRef] = {
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val classname = _entity_class_name(entityname)
    val candidates = packagename.toVector.flatMap { pkg =>
      Vector(
        s"${pkg}.entity.aggregate.${classname}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private[component] def _query_record(
    q: Query[?]
  ): Record =
    q.query match {
      case r: Record => r
      case p: Query.Plan[?] =>
        p.condition match {
          case r: Record => r
          case shape: Product => _query_shape_record(shape)
          case _ => Record.empty
        }
      case shape: Product => _query_shape_record(shape)
      case _ => Record.empty
    }

  private def _query_shape_record(
    shape: Product
  ): Record = {
    val fields = shape.productElementNames.zip(shape.productIterator).toVector.flatMap {
      case (name, cond: org.simplemodeling.model.directive.Condition[Any @unchecked]) =>
        cond match {
          case org.simplemodeling.model.directive.Condition.Any => None
          case other => Some(name -> other)
        }
      case (name, value) =>
        Some(name -> value)
    }
    Record.create(fields.toMap)
  }

  private[component] def _sanitize_query_record(p: Record): Record = {
    val filtered = p.asMap.filterNot { case (k, _) =>
      k == "view" ||
      k.startsWith("security.") ||
      k.startsWith("cncf.security.") ||
      k.startsWith("textus.") ||
      k.startsWith("cncf.")
    }
    Record.create(filtered)
  }

  private[component] def _sanitize_query(q: Query[?]): Query[?] =
    _with_query_controls(_sanitize_query_record(_query_record(q)), q)

  private[component] def _sanitize_count_query(q: Query[?]): Query[?] =
    _with_count_controls(_sanitize_query_record(_query_record(q)), q)

  private[component] def _with_query_controls(
    condition: Record,
    source: Query[?]
  ): Query[?] =
    Query.plan(
      condition,
      Query.whereOf(source),
      Query.sortOf(source),
      Query.limitOf(source),
      Query.offsetOf(source),
      Query.includeTotalOf(source)
    )

  private[component] def _with_count_controls(
    condition: Record,
    source: Query[?]
  ): Query[?] =
    Query.plan(
      condition,
      Query.whereOf(source),
      Query.sortOf(source),
      Some(0),
      Some(0),
      includeTotal = true
    )

  private[component] def _searchable_query_record(p: Record): Record =
    Record.create(
      p.asMap.flatMap {
        case (_, org.simplemodeling.model.directive.Condition.Any) =>
          None
        case (k, org.simplemodeling.model.directive.Condition.Is(expected)) =>
          Some(k -> _store_query_value(expected))
        case (k, org.simplemodeling.model.directive.Condition.In(candidates)) =>
          Some(k -> candidates.toVector.map(_store_query_value))
        case (k, v) =>
          Some(k -> _store_query_value(v))
      }
    )

  // Generated DATATYPE values are typed in component memory but scalar in the datastore.
  private def _store_query_value(value: Any): Any =
    value match {
      case m: NominalScalar => m.value
      case m: EntityId => m.print
      case Some(v) => _store_query_value(v)
      case xs: Vector[?] => xs.map(_store_query_value)
      case xs: Seq[?] => xs.map(_store_query_value)
      case other => other
    }

  private[component] def _filter_view_query_record(
    record: Record,
    querydef: org.goldenport.cncf.entity.view.ViewQueryDefinition
  ): Record = {
    val keys = _view_query_keys(querydef)
    if (keys.isEmpty)
      record
    else
      Record.create(record.asMap.filter { case (k, _) => keys.contains(k) || keys.contains(_snake_case(k)) })
  }

  private def _view_query_keys(
    querydef: org.goldenport.cncf.entity.view.ViewQueryDefinition
  ): Set[String] =
    querydef.expression.toVector.flatMap { expr =>
      _query_field_regex.findAllMatchIn(expr).map(_.group(1)).toVector
    }.flatMap(k => _field_name_candidates(k)).toSet

  private[component] def _matches_view_query(
    entity: Any,
    record: Record
  ): Boolean = {
    val exprs = record.asMap.toVector.flatMap {
      case (name, cond: org.simplemodeling.model.directive.Condition[Any @unchecked]) =>
        cond match {
          case org.simplemodeling.model.directive.Condition.Any => None
          case _ => Some(Query.FieldCondition(name, cond))
        }
      case (name, value) =>
        Some(Query.Eq(name, value))
    }
    exprs.forall(expr => Query.eval(expr, entity))
  }

  private[component] def _entity_class_name(
    entityname: String
  ): String = {
    val normalized = NamingConventions.toNormalizedSegment(entityname)
    normalized
      .split("-")
      .toVector
      .filter(_.nonEmpty)
      .map(s => s"${s.head.toUpper}${s.drop(1)}")
      .mkString
  }

  private[component] def _generated_module_package_names(
    component: Component
  ): Vector[String] = {
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val parentpackagename =
      packagename.flatMap { name =>
        if (name.endsWith(".impl")) Option(name.stripSuffix(".impl")).filter(_.nonEmpty)
        else None
      }
    Vector(
      packagename,
      parentpackagename,
      Some("org.goldenport.cncf.component")
    ).flatten.distinct
  }

  private def _load_scala_module(
    loader: ClassLoader,
    classname: String
  ): Option[AnyRef] = {
    val loaders = Vector(
      Option(loader),
      Option(Thread.currentThread.getContextClassLoader)
    ).flatten.distinct
    loaders.iterator.flatMap { cl =>
      try {
        val cls = Class.forName(classname, true, cl)
        val field = cls.getField("MODULE$")
        Option(field.get(null).asInstanceOf[AnyRef])
      } catch {
        case _: Throwable => None
      }
    }.toSeq.headOption
  }

  private def _extract_collection_id(
    module: AnyRef
  ): Option[EntityCollectionId] =
    module.getClass.getMethods.iterator
      .filter(m => m.getParameterCount == 0 && m.getName == "collectionId")
      .flatMap(m => _invoke_zero_arg(module, m).collect { case x: EntityCollectionId => x })
      .toSeq
      .headOption

  private def _extract_entity_persistent(
    module: AnyRef
  ): Option[EntityPersistent[Any]] = {
    val methods = module.getClass.getMethods.iterator.toVector
    val fields = module.getClass.getFields.iterator.toVector
    val fromnamedmethods =
      methods
        .filter(m => m.getParameterCount == 0 && m.getName.startsWith("given_EntityPersistent"))
        .flatMap(m => _invoke_zero_arg(module, m))
        .headOption
    val fromtypedmethods =
      methods
        .filter(m => m.getParameterCount == 0)
        .flatMap(m => _invoke_zero_arg(module, m))
        .find(x => _as_entity_persistent(x).nonEmpty)
    val fromnamedfields =
      fields
        .filter(_.getName.startsWith("given_EntityPersistent"))
        .flatMap(f => _read_field(module, f))
        .headOption
    val fromtypedfields =
      fields
        .flatMap(f => _read_field(module, f))
        .find(x => _as_entity_persistent(x).nonEmpty)
    fromnamedmethods.orElse(fromtypedmethods).orElse(fromnamedfields).orElse(fromtypedfields).flatMap(x => _as_entity_persistent(x, Some(module)))
  }

  private def _invoke_zero_arg(
    module: AnyRef,
    method: java.lang.reflect.Method
  ): Option[Any] =
    try {
      val target = if (java.lang.reflect.Modifier.isStatic(method.getModifiers)) null else module
      Option(method.invoke(target))
    } catch {
      case _: Throwable => None
    }

  private def _read_field(
    module: AnyRef,
    field: java.lang.reflect.Field
  ): Option[Any] =
    try {
      val target = if (java.lang.reflect.Modifier.isStatic(field.getModifiers)) null else module
      Option(field.get(target))
    } catch {
      case _: Throwable => None
    }

  private[component] def _as_entity_persistent(
    raw: Any,
    module: Option[AnyRef] = None
  ): Option[EntityPersistent[Any]] =
    raw match {
      case m: EntityPersistent[?] =>
        val bridge = m.asInstanceOf[EntityPersistent[Any]]
        val mapping = module.map(_entity_persistent_store_field_mapping).getOrElse(Map.empty)
        Some(_with_store_field_mapping(bridge, mapping))
      case m: AnyRef if _looks_like_entity_persistent(m) =>
        val mapping = module.map(_entity_persistent_store_field_mapping).getOrElse(Map.empty)
        Some(new EntityPersistent[Any] {
          def id(e: Any): EntityId =
            _invoke_entity_persistent(m, "id", e).asInstanceOf[EntityId]

          def toRecord(e: Any): Record =
            _invoke_entity_persistent(m, "toRecord", e).asInstanceOf[Record]

          def fromRecord(r: Record): Consequence[Any] =
            _invoke_entity_persistent(m, "fromRecord", r).asInstanceOf[Consequence[Any]]

          override def toStoreRecord(e: Any): Record =
            _invoke_entity_persistent_option(m, "toStoreRecord", e)
              .map(_.asInstanceOf[Record])
              .getOrElse(toRecord(e))

          override def fromStoreRecord(r: Record): Consequence[Any] =
            _invoke_entity_persistent_option(m, "fromStoreRecord", r)
              .map(_.asInstanceOf[Consequence[Any]])
              .getOrElse(fromRecord(r))

          override def storeFieldName(logicalName: String): String =
            mapping.getOrElse(logicalName, logicalName)
        })
      case _ => None
    }

  private def _with_store_field_mapping(
    bridge: EntityPersistent[Any],
    mapping: Map[String, String]
  ): EntityPersistent[Any] =
    if (mapping.isEmpty)
      bridge
    else
      new EntityPersistent[Any] {
        def id(e: Any): EntityId = bridge.id(e)
        def toRecord(e: Any): Record = bridge.toRecord(e)
        def fromRecord(r: Record): Consequence[Any] = bridge.fromRecord(r)
        override def toStoreRecord(e: Any): Record = bridge.toStoreRecord(e)
        override def fromStoreRecord(r: Record): Consequence[Any] = bridge.fromStoreRecord(r)
        override def storeFieldName(logicalName: String): String =
          mapping.getOrElse(logicalName, bridge.storeFieldName(logicalName))
      }

  private[component] def _entity_persistent_store_field_mapping(
    module: AnyRef
  ): Map[String, String] = {
    val values = _module_string_constants(module)
    val inputs = _module_string_lists(module)
    values.collect {
      case (propfield, logicalname) if propfield.startsWith("PROP_") =>
        val suffix = propfield.stripPrefix("PROP_")
        val inputkeys = inputs.getOrElse(s"INPUT_KEYS_$suffix", Nil)
        val physical = inputkeys.find(_ != logicalname).getOrElse(logicalname)
        logicalname -> physical
    }.toMap
  }

  private def _module_string_constants(
    module: AnyRef
  ): Map[String, String] =
    (_module_field_values(module) ++ _module_method_values(module)).collect {
      case (name, s: String) => name -> s
    }.toMap

  private def _module_string_lists(
    module: AnyRef
  ): Map[String, List[String]] =
    (_module_field_values(module) ++ _module_method_values(module)).flatMap {
      case (name, value) =>
        value match {
        case xs: Iterable[?] =>
          Some(name -> xs.iterator.collect { case s: String => s }.toList)
        case xs: Array[?] =>
          Some(name -> xs.iterator.collect { case s: String => s }.toList)
        case _ =>
          None
        }
    }.toMap

  private def _module_field_values(
    module: AnyRef
  ): Vector[(String, Any)] =
    module.getClass.getFields.iterator.toVector.flatMap { field =>
      _read_field(module, field).map(field.getName -> _)
    }

  private def _module_method_values(
    module: AnyRef
  ): Vector[(String, Any)] =
    module.getClass.getMethods.iterator.toVector
      .filter(m => m.getParameterCount == 0 && m.getDeclaringClass != classOf[Object])
      .flatMap(m => _invoke_zero_arg(module, m).map(m.getName -> _))

  private def _looks_like_entity_persistent(
    raw: AnyRef
  ): Boolean = {
    val methods = raw.getClass.getMethods.toVector
    methods.exists(m => m.getName == "id" && m.getParameterCount == 1) &&
    methods.exists(m => m.getName == "toRecord" && m.getParameterCount == 1) &&
    methods.exists(m => m.getName == "fromRecord" && m.getParameterCount == 1)
  }

  private def _invoke_entity_persistent(
    raw: AnyRef,
    name: String,
    arg: Any
  ): Any = {
    val method = raw.getClass.getMethods.toVector
      .find(m => m.getName == name && m.getParameterCount == 1)
      .getOrElse(throw new IllegalStateException(s"EntityPersistent bridge method not found: ${raw.getClass.getName}.${name}"))
    method.invoke(raw, arg.asInstanceOf[AnyRef])
  }

  private def _invoke_entity_persistent_option(
    raw: AnyRef,
    name: String,
    arg: Any
  ): Option[Any] =
    raw.getClass.getMethods.toVector
      .find(m => m.getName == name && m.getParameterCount == 1)
      .map(_.invoke(raw, arg.asInstanceOf[AnyRef]))

  private[component] def _legacy_memory_plan(
    entityname: String
  ): EntityRuntimePlan[Any] =
    EntityRuntimePlan(
      entityName = entityname,
      memoryPolicy = EntityMemoryPolicy.LoadToMemory,
      workingSet = None,
      partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
      maxPartitions = 64,
      maxEntitiesPerPartition = 10000
    )

  // Transitional metadata hook:
  // transition definitions are not yet bound from Cozy/simplemodeling state machine model.
  // This method is the canonical bootstrap entry point once metadata binding is available.
}
