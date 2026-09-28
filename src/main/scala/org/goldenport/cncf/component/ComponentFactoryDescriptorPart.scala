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
private[component] trait ComponentFactoryDescriptorPart { self: ComponentFactory =>

  private[component] def _runtime_component_descriptors_for(
    component: Component
  ): Vector[ComponentDescriptor] = {
    val componentnames = Vector(
      Try(component.name).toOption,
      component.coreOption.map(_.name),
      component.coreOption.map(_.componentId.name)
    ).flatten.map(_normalize_entity_name).toSet
    _runtime_component_descriptors.filter { descriptor =>
      (
        Vector(descriptor.componentName, descriptor.name).flatten ++
        descriptor.componentlets.map(_.name)
      )
        .map(_normalize_entity_name)
        .exists(componentnames.contains)
    }
  }

  private[component] def _matches_entity_descriptor(
    descriptor: EntityRuntimeDescriptor,
    entityname: String
  ): Boolean = {
    val normalized = _normalize_entity_name(entityname)
    _normalize_entity_name(descriptor.entityName) == normalized ||
    _normalize_entity_name(descriptor.collectionId.name) == normalized
  }

  private[component] def _enrich_component_descriptors(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Unit = {
    val plansbyentity = plans.map(p => _normalize_entity_name(p.entityName) -> p).toMap
    val descriptors = component.componentDescriptors.map { descriptor =>
      descriptor.copy(
        entityRuntimeDescriptors = descriptor.entityRuntimeDescriptors.map { runtimedescriptor =>
          val effective = plansbyentity
            .get(_normalize_entity_name(runtimedescriptor.entityName))
            .map(_apply_entity_runtime_plan(runtimedescriptor, _))
            .getOrElse(runtimedescriptor)
          _enrich_entity_runtime_descriptor(component, effective)
        }
      )
    }
    if (descriptors.nonEmpty)
      component.withComponentDescriptors(descriptors)
  }

  private def _apply_entity_runtime_plan(
    descriptor: EntityRuntimeDescriptor,
    plan: EntityRuntimePlan[Any]
  ): EntityRuntimeDescriptor =
    descriptor.copy(
      memoryPolicy = plan.memoryPolicy,
      partitionStrategy = plan.partitionStrategy,
      maxPartitions = plan.maxPartitions,
      maxEntitiesPerPartition = plan.maxEntitiesPerPartition,
      workingSet = plan.workingSet.map(ws =>
        WorkingSetDescriptor(
          entityName = ws.entityName,
          entityIds = Vector.empty
        )
      ),
      workingSetPolicy = plan.workingSetPolicy,
      workingSetPolicySource = plan.workingSetPolicySource,
      concurrencyPolicy = Some(plan.concurrencyPolicy)
    )

  private def _enrich_entity_runtime_descriptor(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): EntityRuntimeDescriptor =
    _effective_entity_schema(component, descriptor) match {
      case Some(schema) => descriptor.withSchema(_with_shortid_schema(schema))
      case None => descriptor
    }

  private def _effective_entity_schema(
    component: Component,
    descriptor: EntityRuntimeDescriptor
  ): Option[Schema] =
    descriptor.schema.orElse {
      _generated_entity_module(component, descriptor.entityName)
        .flatMap(_extract_schema)
    }

  private def _with_shortid_schema(
    schema: Schema
  ): Schema = {
    val names = schema.columns.map(_.name.value)
    if (names.exists(NamingConventions.equivalentByNormalized(_, "shortid")))
      schema
    else {
      val column = _shortid_column
      val columns =
        names.indexWhere(NamingConventions.equivalentByNormalized(_, "id")) match {
          case -1 => schema.columns :+ column
          case n =>
            val (before, after) = schema.columns.splitAt(n + 1)
            before ++ (column +: after)
        }
      schema.copy(columns = columns)
    }
  }

  private def _shortid_column: Column =
    Column(
      baseContent = BaseContent.Builder("shortid").label("Short ID").build(),
      domain = ValueDomain(
        datatype = XString,
        multiplicity = Multiplicity.ZeroOne
      ),
      web = WebColumn(
        required = Some(false),
        system = true,
        readonly = true,
        help = Some("Entity-local identifier for Web UI when the entity kind is fixed. Use canonical id for external integration.")
      )
    )

  private[component] def _bootstrap_aggregates(
    component: Component,
    aggregatespace: AggregateSpace,
    entityspace: EntitySpace
  ): Unit = {
    val names = _aggregate_collection_names(component)
    val custombindings = component.factory.toVector.flatMap(_.aggregateCollectionBindings(component))
    names.foreach { name =>
      custombindings.find(_.aggregateName == name) match {
        case _ if aggregatespace.collectionOption[Any](name).isDefined =>
          ()
        case Some(binding) =>
          aggregatespace.register(name, binding.collection.asInstanceOf[AggregateCollection[Any]])
        case None =>
          _resolve_aggregate_definition(component, name) match {
            case Some(definition) =>
              aggregatespace.register(
                name,
                _default_aggregate_collection(component, entityspace, definition)
              )
            case _ =>
              _resolve_aggregate_entity_name(component, name).foreach { entityname =>
                val builder = _default_aggregate_builder(entityspace, entityname)
                val collection = new AggregateCollection[Any](builder)
                aggregatespace.register(name, collection)
              }
          }
      }
    }
  }

  private[component] def _bootstrap_views(
    component: Component,
    viewspace: ViewSpace,
    entityspace: EntitySpace
  ): Unit = {
    val names = _view_collection_names(component)
    names.foreach { name =>
      _resolve_view_definition(component, name).foreach { d =>
        if (viewspace.collectionOption[Any](name).isEmpty) {
          val builder = _default_view_builder(component, entityspace, d.entityName)
          val collection = new ViewCollection[Any](builder)
          val browser = _default_view_browser(component, entityspace, d.entityName, collection)
          viewspace.register(name, collection, browser)
          d.viewNames.distinct.foreach { viewname =>
            viewspace.registerView(name, viewname, _default_named_view_browser(component, entityspace, d.entityName, viewname, collection))
          }
          d.queries.distinctBy(_.name).foreach { q =>
            viewspace.registerView(name, q.name, _default_view_query_browser(component, entityspace, d.entityName, collection, q))
          }
        }
      }
    }
  }

  private def _aggregate_collection_names(
    component: Component
  ): Vector[String] = {
    val defs = component.aggregateDefinitions
    if (defs.nonEmpty) defs.map(_.name).distinct
    else _entity_collection_names(component)
  }

  private def _view_collection_names(
    component: Component
  ): Vector[String] = {
    val defs = component.viewDefinitions
    if (defs.nonEmpty) defs.map(_.name).distinct
    else _entity_collection_names(component)
  }

  private def _resolve_aggregate_entity_name(
    component: Component,
    name: String
  ): Option[String] = {
    val entityname = component.aggregateDefinitions.find(_.name == name).map(_.entityName).getOrElse(name)
    component.entitySpace.entityOption[Any](entityname).map(_ => entityname)
  }

  private def _resolve_aggregate_definition(
    component: Component,
    name: String
  ): Option[AggregateDefinition] =
    component.aggregateDefinitions.find(_.name == name)

  private def _resolve_view_definition(
    component: Component,
    name: String
  ): Option[ViewDefinition] = {
    val definition = component.viewDefinitions.find(_.name == name).getOrElse {
      ViewDefinition(name = name, entityName = name)
    }
    component.entitySpace.entityOption[Any](definition.entityName).map(_ => definition)
  }

  private def _default_aggregate_builder(
    entityspace: EntitySpace,
    entityname: String
  ): AggregateBuilder[Any] =
    new AggregateBuilder[Any] {
      def build(id: EntityId): Consequence[Any] =
        entityspace.entity[Any](entityname).resolve(id)
    }

  private def _default_aggregate_collection(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition
  ): AggregateCollection[Any] = {
    val builder = new ContextualAggregateBuilder[Any] {
      def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Any] =
        given ExecutionContext = ExecutionContext.withAggregateInternalRead(ctx, true)
        _build_default_aggregate(component, entityspace, definition, id)
    }
    val queryfn = new ContextualAggregateQuery[Any] {
      def query_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Vector[Any]] =
        given ExecutionContext = ExecutionContext.withAggregateInternalRead(ctx, true)
        _search_default_aggregates(component, entityspace, definition, q)
    }
    val countfn = new ContextualAggregateCount {
      def count_with_context(q: Query[?])(using ctx: ExecutionContext): Consequence[Int] =
        given ExecutionContext = ExecutionContext.withAggregateInternalRead(ctx, true)
        _count_default_aggregates(component, entityspace, definition, q)
    }
    new AggregateCollection[Any](
      builder = builder,
      queryfn = queryfn,
      countfn = Some(countfn),
      totalCountCapabilityValue = TotalCountCapability.Unsupported,
      totalCountCapabilityWithContextFn = Some(ctx => _entity_total_count_capability(component, definition.entityName)(using ctx))
    )
  }

  private def _default_view_builder(
    component: Component,
    entityspace: EntitySpace,
    entityname: String
  ): ViewBuilder[Any] =
    new ContextualViewBuilder[Any] {
      def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Any] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _load_view_source_entity(component, entityspace, entityname, id).flatMap(_entity_to_view(component, entityname, _))
      }
    }

  private[component] def _with_internal_materialization_read(
    ctx: ExecutionContext
  ): ExecutionContext =
    // View/Aggregate materialization reads source entities internally before
    // the resulting surface applies its own operation-level contract. The
    // existing flag is named after aggregates; keep using it until a dedicated
    // internal materialization scope is introduced.
    ExecutionContext.withAggregateInternalRead(ctx, true)

  private[component] def _entity_total_count_capability(
    component: Component,
    entityname: String
  )(using ctx: ExecutionContext): Consequence[TotalCountCapability] = {
    val cid = _bootstrap_collection_id(component, entityname)
    for {
      dscid <- ctx.entityStoreSpace.dataStoreCollection(cid)
      capability <- ctx.dataStoreSpace.totalCountCapability(dscid)
    } yield capability
  }

  private[component] def _load_view_source_entity(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Any] = {
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.resolve(id).recoverWith { case _ =>
          EntityStore.standard().load[Any](id).flatMap {
            case Some(s) => Consequence.success(s)
            case None => Consequence.entityNotFound(s"${_entity_class_name(entityname)} not found: ${id.value}")
          }
        }
      case None =>
        EntityStore.standard().load[Any](id).flatMap {
          case Some(s) => Consequence.success(s)
          case None => Consequence.entityNotFound(s"${_entity_class_name(entityname)} not found: ${id.value}")
        }
    }
  }

  private def _build_default_aggregate(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Any] =
    for {
      root <- _load_entity(component, entityspace, definition.entityName, id)
      aggregate <- _entity_to_aggregate(component, definition.entityName, root)
      joined <- definition.members.foldLeft(Consequence.success(aggregate)) { (z, member) =>
        z.flatMap(a => _attach_aggregate_member(component, entityspace, definition, member, root, a))
      }
    } yield joined

  private def _search_default_aggregates(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val sanitized = _sanitize_query(q)
    for {
      roots <- _search_entities(component, entityspace, definition.entityName, sanitized)
      aggregates <- roots.foldLeft(Consequence.success(Vector.empty[Any])) { (z, root) =>
        z.flatMap(xs => _build_default_aggregate(component, entityspace, definition, _entity_id(component, definition.entityName, root)).map(xs :+ _))
      }
    } yield aggregates
  }

  private def _count_default_aggregates(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Int] =
    _count_entities(component, entityspace, definition.entityName, _sanitize_count_query(q))

  private def _attach_aggregate_member(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootentity: Any,
    aggregate: Any
  )(using ctx: ExecutionContext): Consequence[Any] = {
    for {
      module <- Consequence.fromOption(
        _generated_aggregate_module(component, definition.entityName),
        s"Aggregate module not found for ${definition.entityName}"
      )
      entities <- _resolve_aggregate_member_entities(
        component,
        entityspace,
        definition,
        member,
        rootentity
      )
      aggregates <- entities.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
        z.flatMap(xs => _entity_to_aggregate(component, member.entityName, entity).map(xs :+ _))
      }
      attached <- _invoke_member_setter(aggregate, module, member.name, aggregates)
    } yield attached
  }

  private def _resolve_aggregate_member_entities(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootentity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    _aggregate_member_join_strategy(member) match {
      case "direct" =>
        _load_associated_entities(component, entityspace, definition, member, rootentity)
      case "reverse" =>
        _search_related_entities(component, entityspace, definition, member, rootentity)
      case "through" =>
        Consequence.notImplemented(s"Aggregate join strategy 'through' is not implemented for member ${member.name}")
      case s =>
        Consequence.argumentInvalid(s"Unsupported aggregate join strategy: $s")
    }
  }

  private def _aggregate_member_join_strategy(
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition
  ): String =
    member.join.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse {
      val relation = member.kind.getOrElse("composition")
      val boundary = member.boundary.getOrElse("internal")
      if (boundary == "external" && relation == "association")
        "direct"
      else
        "reverse"
    }

  private def _search_related_entities(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootentity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val rootid = _entity_id(component, definition.entityName, rootentity)
    val joinfieldname = member.joinFieldName.getOrElse(s"${definition.entityName}Id")
    _search_entities(
      component,
      entityspace,
      member.entityName,
      Query(Record.data(joinfieldname -> rootid.value))
    ).flatMap { xs =>
      if (xs.nonEmpty || _field_name_candidates(joinfieldname).tail.isEmpty)
        Consequence.success(xs)
      else
        _search_entities(
          component,
          entityspace,
          member.entityName,
          Query(Record.data(_field_name_candidates(joinfieldname).tail.head -> rootid.value))
        )
    }
  }

  private def _load_associated_entities(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootentity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val rootrecord = _entity_to_record(component, definition.entityName, rootentity)
    val joinfieldname = member.joinFieldName.getOrElse(member.name)
    val joinkeys = _field_name_candidates(joinfieldname)
    _record_get_entity_id(rootrecord, joinkeys).flatMap {
      case Some(id) =>
        _load_entity(component, entityspace, member.entityName, id).map(x => Vector(x))
      case None =>
        _record_get_entity_ids(rootrecord, joinkeys).flatMap { ids =>
          ids.foldLeft(Consequence.success(Vector.empty[Any])) { (z, id) =>
            z.flatMap(acc => _load_entity(component, entityspace, member.entityName, id).map(acc :+ _))
          }
        }
    }
  }

  private[component] def _field_name_candidates(name: String): List[String] = {
    val snake = _snake_case(name)
    List(name, snake).distinct
  }

  private[component] def _snake_case(name: String): String =
    Option(name).getOrElse("").zipWithIndex.foldLeft(new StringBuilder) { case (z, (c, i)) =>
      if (c.isUpper && i > 0)
        z.append('_').append(c.toLower)
      else
        z.append(c.toLower)
    }.toString

  private def _record_get_entity_id(
    record: Record,
    keys: List[String]
  ): Consequence[Option[EntityId]] =
    keys.foldLeft(Consequence.success(Option.empty[EntityId])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None => record.getAsC[EntityId](key)
      }
    }

  private def _record_get_entity_ids(
    record: Record,
    keys: List[String]
  ): Consequence[Vector[EntityId]] =
    keys.foldLeft(Consequence.success(Vector.empty[EntityId])) { (z, key) =>
      z.flatMap { xs =>
        if (xs.nonEmpty)
          Consequence.success(xs)
        else
          record.getVector(key) match {
            case Some(vs) =>
              vs.foldLeft(Consequence.success(Vector.empty[EntityId])) { (zz, x) =>
                zz.flatMap(acc => _associated_entity_id(x).map(acc :+ _))
              }
            case None =>
              record.getAny(key) match {
                case Some(value) => _associated_entity_id(value).map(x => Vector(x))
                case None => Consequence.success(Vector.empty)
              }
          }
      }
    }

  private def _associated_entity_id(
    value: Any
  ): Consequence[EntityId] =
    value match {
      case id: EntityId => Consequence.success(id)
      case s: String => EntityId.parse(s)
      case other => EntityId.parse(other.toString)
    }

}
