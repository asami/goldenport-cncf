package org.goldenport.cncf.component

import java.nio.file.{Files, Path, Paths}
import cats.effect.Ref
import cats.data.State
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.ConfigurationAccess
import org.goldenport.cncf.backend.collaborator.{Collaborator, CollaboratorFactory}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.component.repository.ComponentSource
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent, EntityQuery, EntityStore}
import org.goldenport.cncf.entity.aggregate.{AggregateAssembler, AggregateBuilder, AggregateCollection, AggregateSpace, AggregateDefinition, ContextualAggregateBuilder, ContextualAggregateQuery}
import org.goldenport.cncf.event.{ActionCallDispatcher, EventBus, EventEngine, EventReception, EventStore, EntitySubscriptionLimit}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimeDescriptor, EntityRuntimePlan, EntitySpace, EntityStorage, PartitionedMemoryRealm, PartitionStrategy, WorkingSetDefinition, WorkingSetInitializer}
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.view.{Browser, ViewDefinition, ViewBuilder, ViewCollection, ViewSpace}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.statemachine.{CollectionStateMachinePlanner, CollectionStateMachinePlannerProvider, CollectionTransitionRule, CollectionTransitionRuleProvider, TransitionTrigger, TransitionRule}
import org.goldenport.cncf.naming.NamingConventions
import scala.util.Try

/*
 * @since   Jan. 30, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Mar. 31, 2026
 * @version Apr.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory(
  private val _component_repository_space: ComponentRepositorySpace = ComponentRepositorySpace(),
  private val _collaborators: CollaboratorFactory = CollaboratorFactory.empty,
  private val _runtime_entity_descriptors: Vector[EntityRuntimeDescriptor] = Vector.empty
) {
  def discover(): Vector[Component] = {
    val cs = _component_repository_space.discover()
    cs.map(bootstrap)
  }

  def bootstrap(component: Component): Component =
    _bootstrap_collections(_initialize_special_component(component))

  private def _initialize_special_component(p: Component): Component =
    p match {
      case m: CollaboratorComponent =>
        val entryOpt = _collaborators.resolve(m.core.name).orElse(_collaborators.entries.headOption)
        entryOpt match {
          case Some(entry) =>
            val collaboratorImpl = _wrapCollaborator(entry.collaborator)
            val init = CollaboratorComponentInit(CollaboratorComponent.Core(collaboratorImpl))
            m.initialize(init)
          case None =>
            m
        }
      case m => m
    }

  private def _wrapCollaborator(apiCollaborator: api.Collaborator): Collaborator = new Collaborator {
    private val delegate = Collaborator.Instance(Collaborator.Core(apiCollaborator))
    def execute(ctx: org.goldenport.cncf.context.ExecutionContext, request: org.goldenport.protocol.Request) =
      delegate.execute(ctx, request)
  }

  private def _bootstrap_collections(component: Component): Component = {
    val storesnapshot = scala.collection.concurrent.TrieMap.empty[EntityId, Any]
    // One EntitySpace per component. All collections in the component share it.
    val entityspace = component.entitySpace
    val aggregatespace = component.aggregateSpace
    val viewspace = component.viewSpace
    val plans = _default_entity_runtime_plans(component)
    val workingsetentities = _resolve_working_set_entity_names(component, plans)
    val _ = component.withWorkingSetEntityNames(workingsetentities)
    if (plans.nonEmpty)
      _bootstrap_entities_with_plan(component, plans, entityspace, storesnapshot)
    else
      _bootstrap_entities(component, entityspace, storesnapshot)
    _bootstrap_aggregates(component, aggregatespace, entityspace)
    _bootstrap_views(component, viewspace, entityspace)
    if (plans.nonEmpty)
      _initialize_working_sets_from_plan(plans, entityspace, storesnapshot)
    else
      _initialize_working_sets(component, entityspace, storesnapshot)
    _bootstrap_state_machine_planners(component, plans)
    _bootstrap_event_reception(component)
    component
  }

  private def _bootstrap_event_reception(
    component: Component
  ): Unit = {
    val store = component.subsystem.map(_.eventStore).getOrElse(EventStore.inMemory)
    val _ = component.withEventStore(store)
    component.jobEngine match {
      case m: org.goldenport.cncf.job.InMemoryJobEngine =>
        m.withEventStore(store)
      case _ =>
        ()
    }
    if (component.eventReceptionDefinitions.nonEmpty || component.eventSubscriptionDefinitions.nonEmpty) {
      val engine = EventEngine.noop(DataStore.noop(), eventstore = store)
      val bus = EventBus.default(engine)
      val reception = createEventReceptionWithOperationDispatcher(component, bus)
      component.withEventReception(reception)
    }
  }

  def createEventReceptionWithOperationDispatcher(
    component: Component,
    eventBus: EventBus,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit()
  ): EventReception =
    createEventReception(
      component = component,
      eventBus = eventBus,
      dispatcher = createOperationActionDispatcher(component),
      ingressSecurityResolver = ingressSecurityResolver,
      entitySubscriptionLimit = entitySubscriptionLimit
    )

  def createEventReception(
    component: Component,
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit()
  ): EventReception = {
    val reception = EventReception.default(
      eventBus = eventBus,
      dispatcher = dispatcher,
      ingressSecurityResolver = ingressSecurityResolver,
      entitySpace = Some(component.entitySpace),
      entitySubscriptionLimit = entitySubscriptionLimit,
      workingSetEntities = component.workingSetEntityNames,
      // EventReception can be created from lightweight test components
      // before Component.core initialization.
      jobEngine = Try(component.jobEngine).toOption
    )
    component.eventReceptionDefinitions.foreach(reception.register)
    component.eventSubscriptionDefinitions.foreach(reception.registerSubscription)
    reception
  }

  def createOperationActionDispatcher(
    component: Component
  ): ActionCallDispatcher =
    new OperationRequestActionDispatcher(ComponentLogic(component))

  private def _bootstrap_state_machine_planners(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Unit = {
    val provider = new CollectionStateMachinePlannerProvider(component.stateMachinePlannerProvider)
    val rules = _default_collection_transition_rules(component, plans)
    val saveRulesByCollection = rules.collect {
      case m if m.trigger == TransitionTrigger.Save => m
    }.groupBy(_.collectionName)
    val updateRulesByCollection = rules.collect {
      case m if m.trigger == TransitionTrigger.Update => m
    }.groupBy(_.collectionName)

    saveRulesByCollection.foreach { case (name, groupedRules) =>
      val planner = new CollectionStateMachinePlanner[Any](
        groupedRules.toVector.map(_to_transition_rule_any)
      )
      provider.registerSave(name, planner)
    }
    updateRulesByCollection.foreach { case (name, groupedRules) =>
      val planner = new CollectionStateMachinePlanner[Any](
        groupedRules.toVector.map(_to_transition_rule_any)
      )
      provider.registerUpdate(name, planner)
    }

    val _ = component.withStateMachinePlannerProvider(provider)
  }

  private def _to_transition_rule_any(p: CollectionTransitionRule[Any]): TransitionRule[Any] =
    TransitionRule[Any](
      eventName = p.eventName,
      priority = p.priority,
      declarationOrder = p.declarationOrder,
      guard = p.guard,
      plan = p.plan
    )

  private def _bootstrap_entities_with_plan(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    plans.foreach { plan =>
      _bootstrap_entity_plan(component, plan, entityspace, storesnapshot)
    }
  }

  private def _bootstrap_entity_plan(
    component: Component,
    plan: EntityRuntimePlan[Any],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val name = plan.entityName
    val storeRealm = _create_store_realm(name, storesnapshot)
    var storage = EntityStorage(storeRealm)
    // TODO: EntityDescriptor + EntityStorage is now the canonical wiring path.
    // When entity definitions are available, this bootstrap should only
    // construct descriptor/storage and avoid any legacy realm-based wiring.
    val descriptor = EntityDescriptor(
      collectionId = _bootstrap_collection_id(component, name),
      plan = plan,
      persistent = _bootstrap_entity_persistent(component, name)
    )

    plan.memoryPolicy match {
      case EntityMemoryPolicy.StoreOnly =>
        ()
      case EntityMemoryPolicy.LoadToMemory =>
        val memoryRealm = new PartitionedMemoryRealm[Any](
          strategy = plan.partitionStrategy,
          idOf = _entity_id_of_any,
          maxPartitions = plan.maxPartitions,
          maxEntitiesPerPartition = plan.maxEntitiesPerPartition
        )
        storage = storage.copy(memoryRealm = Some(memoryRealm))
    }

    val collection = new EntityCollection[Any](descriptor, storage)
    entityspace.registerEntity(name, collection)
  }

  // Temporary entity bootstrap using service names.
  // This is a minimal runtime bootstrap until Cozy entity metadata
  // is available. Later this should be replaced by entity definitions
  // derived from the Cozy model.
  private def _bootstrap_entities(
    component: Component,
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    _entity_collection_names(component).distinct.foreach { name =>
      val storeRealm = _create_store_realm(name, storesnapshot)
      var storage = EntityStorage(storeRealm)
      val legacymemoryplan = _legacy_memory_plan(name)
      val descriptor = EntityDescriptor(
        collectionId = _bootstrap_collection_id(component, name),
        plan = legacymemoryplan,
        persistent = _bootstrap_entity_persistent(component, name)
      )

      val memoryRealm = new PartitionedMemoryRealm[Any](
        strategy = legacymemoryplan.partitionStrategy,
        idOf = _entity_id_of_any,
        maxPartitions = legacymemoryplan.maxPartitions,
        maxEntitiesPerPartition = legacymemoryplan.maxEntitiesPerPartition
      )
      storage = storage.copy(memoryRealm = Some(memoryRealm))

      val collection = new EntityCollection[Any](descriptor, storage)
      entityspace.registerEntity(name, collection)
    }
  }

  private def _create_store_realm(
    name: String,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): EntityRealm[Any] = {
    given EntityPersistent[Any] = _entity_persistent_any
    val state = new _IdRef[EntityRealmState[Any]](EntityRealmState(Map.empty))
    new EntityRealm[Any](
      entityName = name,
      loader = EntityLoader[Any](id => _load_entity_from_store(storesnapshot, id)),
      state = state
    )
  }

  private def _load_entity_from_store(
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any],
    id: EntityId
  ): Option[Any] = {
    storesnapshot.get(id)
  }

  private def _entity_id_of_any(
    p: Any
  ): EntityId =
    p match {
      case m: EntityPersistable => m.id
      case _ => throw new IllegalStateException("Entity must implement EntityPersistable to be cached in memory realm")
    }

  private lazy val _entity_persistent_any: EntityPersistent[Any] =
    new EntityPersistent[Any] {
      def id(e: Any): EntityId =
        _entity_id_of_any(e)

      def toRecord(e: Any): Record =
        e match {
          case m: EntityPersistable => m.toRecord()
          case _ => Record.empty
        }

      def fromRecord(r: Record): Consequence[Any] =
        Consequence.failure("EntityPersistent[Any].fromRecord is not wired in bootstrap placeholder")
    }

  private final class _IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized {
      _value
    }

    def set(a: A): Unit = synchronized {
      _value = a
    }

    override def getAndSet(a: A): A = synchronized {
      val prev = _value
      _value = a
      prev
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = (next: A) => synchronized {
        if (_value == snapshot) {
          _value = next
          true
        } else {
          false
        }
      }
      (snapshot, setter)
    }

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, out) = f(_value)
      _value = next
      Some(out)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, out) = f(_value)
      _value = next
      out
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, out) = state.run(_value).value
      _value = next
      out
    }

    override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
      val (next, out) = state.run(_value).value
      _value = next
      Some(out)
    }
  }


  private def _entity_collection_names(
    component: Component
  ): Vector[String] = {
    val names =
      if (component.aggregateDefinitions.nonEmpty)
        component.aggregateDefinitions.map(_.entityName).toVector
      else if (component.viewDefinitions.nonEmpty)
        component.viewDefinitions.map(_.entityName).toVector
      else
        component.core.protocol.services.services
          .map(_.name)
          .filterNot(_ == "meta")
          .filterNot(_ == "system")
          .toVector
    // Fallback collection when no service-derived names exist
    if (names.nonEmpty) names.distinct else Vector("default")
  }

  private def _bootstrap_aggregates(
    component: Component,
    aggregatespace: AggregateSpace,
    entityspace: EntitySpace
  ): Unit = {
    val names = _aggregate_collection_names(component)
    val custombindings = component.factory.toVector.flatMap(_.aggregate_collection_bindings(component))
    names.foreach { name =>
      custombindings.find(_.aggregate_name == name) match {
        case Some(binding) =>
          aggregatespace.register(name, binding.collection.asInstanceOf[AggregateCollection[Any]])
        case None =>
          _resolve_aggregate_definition(component, name) match {
            case Some(definition) if definition.members.nonEmpty =>
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

  private def _bootstrap_views(
    component: Component,
    viewspace: ViewSpace,
    entityspace: EntitySpace
  ): Unit = {
    val names = _view_collection_names(component)
    names.foreach { name =>
      _resolve_view_definition(component, name).foreach { d =>
        val builder = _default_view_builder(component, entityspace, d.entityName)
        val collection = new ViewCollection[Any](builder)
        val browser = _default_view_browser(component, entityspace, d.entityName, collection)
        viewspace.register(name, collection, browser)
        d.viewNames.distinct.foreach { viewname =>
          viewspace.registerView(name, viewname, _default_named_view_browser(component, entityspace, d.entityName, viewname))
        }
        d.queries.distinctBy(_.name).foreach { q =>
          viewspace.registerView(name, q.name, _default_view_query_browser(component, entityspace, d.entityName, collection, q))
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
    new AggregateCollection[Any](builder = builder, queryfn = queryfn)
  }

  private def _default_view_builder(
    component: Component,
    entityspace: EntitySpace,
    entityname: String
  ): ViewBuilder[Any] =
    new ViewBuilder[Any] {
      def build(id: EntityId): Consequence[Any] =
        _load_view_source_entity(component, entityspace, entityname, id).flatMap(_entity_to_view(component, entityname, _))
    }

  private def _default_view_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    collection: ViewCollection[Any]
  ): Browser[Any] = {
    val queryfn = (q: Query[_]) =>
      _search_view_source_entities(component, entityspace, entityname, Query(_sanitize_query_record(_query_record(q)))).flatMap {
        _.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
          z.flatMap(xs => _entity_to_view(component, entityname, entity).map(xs :+ _))
        }
    }
    Browser.from(collection, queryfn)
  }

  private def _default_named_view_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    viewname: String
  ): Browser[Any] =
    new Browser[Any] {
      def find(id: EntityId): Consequence[Any] =
        _load_view_source_entity(component, entityspace, entityname, id).flatMap(_entity_to_view(component, entityname, Some(viewname), _))

      def query(q: Query[_]): Consequence[Vector[Any]] =
        _search_view_source_entities(component, entityspace, entityname, Query(_sanitize_query_record(_query_record(q)))).flatMap {
          _.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
            z.flatMap(xs => _entity_to_view(component, entityname, Some(viewname), entity).map(xs :+ _))
          }
        }
    }

  private def _default_view_query_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    collection: ViewCollection[Any],
    querydef: org.goldenport.cncf.entity.view.ViewQueryDefinition
  ): Browser[Any] = {
    val queryfn = (q: Query[_]) => {
      val source = _sanitize_query_record(_query_record(q))
      val filtered = _filter_view_query_record(source, querydef)
      _search_view_source_entities(component, entityspace, entityname, q).flatMap { entities =>
        val matched =
          if (filtered.asMap.isEmpty) entities
          else entities.filter(entity => _matches_view_query(entity, filtered))
        matched.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
          z.flatMap(xs => _entity_to_view(component, entityname, entity).map(xs :+ _))
        }
      }
    }
    Browser.from(collection, queryfn)
  }

  private def _load_view_source_entity(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    id: EntityId
  ): Consequence[Any] = {
    val ctx0 = ExecutionContext.create()
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.resolve(id).recoverWith { case _ =>
          EntityStore.standard().load[Any](id)(using summon[EntityPersistent[Any]], ctx0).flatMap {
            case Some(s) => Consequence.success(s)
            case None => Consequence.failure(s"${_entity_class_name(entityname)} not found: ${id.value}")
          }
        }
      case None =>
        EntityStore.standard().load[Any](id)(using summon[EntityPersistent[Any]], ctx0).flatMap {
          case Some(s) => Consequence.success(s)
          case None => Consequence.failure(s"${_entity_class_name(entityname)} not found: ${id.value}")
        }
    }
  }

  private def _search_view_source_entities(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    q: Query[?]
  ): Consequence[Vector[Any]] = {
    val ctx0 = ExecutionContext.create()
    val cid = _bootstrap_collection_id(component, entityname)
    val query = EntityQuery[Any](cid, q)
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    entityspace.entityOption[Any](entityname) match {
      case Some(collection) =>
        collection.search(query)(using ctx0).map(_.data).flatMap { xs =>
          if (xs.nonEmpty) Consequence.success(xs)
          else EntityStore.standard().search[Any](query)(using summon[EntityPersistent[Any]], ctx0).map(_.data)
        }.recoverWith { case _ =>
          EntityStore.standard().search[Any](query)(using summon[EntityPersistent[Any]], ctx0).map(_.data)
        }
      case None =>
        EntityStore.standard().search[Any](query)(using summon[EntityPersistent[Any]], ctx0).map(_.data)
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
    val sanitized = Query(_sanitize_query_record(_query_record(q)))
    for {
      roots <- _search_entities(component, entityspace, definition.entityName, sanitized)
      aggregates <- roots.foldLeft(Consequence.success(Vector.empty[Any])) { (z, root) =>
        z.flatMap(xs => _build_default_aggregate(component, entityspace, definition, _entity_id(component, definition.entityName, root)).map(xs :+ _))
      }
    } yield aggregates
  }

  private def _attach_aggregate_member(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootEntity: Any,
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
        rootEntity
      )
      aggregates <- entities.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
        z.flatMap(xs => _entity_to_aggregate(component, member.entityName, entity).map(xs :+ _))
      }
      attached <- _invoke_member_setter(aggregate, module, _aggregate_member_field_name(member.name), aggregates)
    } yield attached
  }

  private def _aggregate_member_field_name(name: String): String = {
    val parts = Option(name).getOrElse("").split("_").toVector.filter(_.nonEmpty)
    parts.headOption.fold(name) { head =>
      head + parts.drop(1).map(x => x.headOption.fold("")(_.toUpper.toString) + x.drop(1)).mkString
    }
  }

  private def _resolve_aggregate_member_entities(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootEntity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    _aggregate_member_join_strategy(member) match {
      case "direct" =>
        _load_associated_entities(component, entityspace, definition, member, rootEntity)
      case "reverse" =>
        _search_related_entities(component, entityspace, definition, member, rootEntity)
      case "through" =>
        Consequence.failure(s"Aggregate join strategy 'through' is not implemented for member ${member.name}")
      case s =>
        Consequence.failure(s"Unsupported aggregate join strategy: $s")
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
    rootEntity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val rootId = _entity_id(component, definition.entityName, rootEntity)
    val joinFieldName = member.joinFieldName.getOrElse(s"${definition.entityName}Id")
    _search_entities(
      component,
      entityspace,
      member.entityName,
      Query(Record.data(joinFieldName -> rootId.value))
    ).flatMap { xs =>
      if (xs.nonEmpty || _field_name_candidates(joinFieldName).tail.isEmpty)
        Consequence.success(xs)
      else
        _search_entities(
          component,
          entityspace,
          member.entityName,
          Query(Record.data(_field_name_candidates(joinFieldName).tail.head -> rootId.value))
        )
    }
  }

  private def _load_associated_entities(
    component: Component,
    entityspace: EntitySpace,
    definition: AggregateDefinition,
    member: org.goldenport.cncf.entity.aggregate.AggregateMemberDefinition,
    rootEntity: Any
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val rootRecord = _entity_to_record(component, definition.entityName, rootEntity)
    val joinFieldName = member.joinFieldName.getOrElse(member.name)
    val joinKeys = _field_name_candidates(joinFieldName)
    _record_get_entity_id(rootRecord, joinKeys).flatMap {
      case Some(id) =>
        _load_entity(component, entityspace, member.entityName, id).map(x => Vector(x))
      case None =>
        _record_get_entity_ids(rootRecord, joinKeys).flatMap { ids =>
          ids.foldLeft(Consequence.success(Vector.empty[Any])) { (z, id) =>
            z.flatMap(acc => _load_entity(component, entityspace, member.entityName, id).map(acc :+ _))
          }
        }
    }
  }

  private def _field_name_candidates(name: String): List[String] = {
    val snake = _snake_case(name)
    List(name, snake).distinct
  }

  private def _snake_case(name: String): String =
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

  private def _load_entity(
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
                case None => Consequence.failure(s"${_entity_class_name(entityname)} not found: ${id.value}")
              }
        }
      case None =>
        given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
        EntityStore.standard()
          .load[Any](id)
          .flatMap {
            case Some(s) => Consequence.success(s)
            case None => Consequence.failure(s"${_entity_class_name(entityname)} not found: ${id.value}")
          }
    }

  private def _search_entities(
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

  private def _entity_to_aggregate(
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
      aggregate <- _invoke_create_from_record(module, record)
    } yield aggregate

  private def _entity_to_view(
    component: Component,
    entityname: String,
    entity: Any
  ): Consequence[Any] =
    _entity_to_view(component, entityname, None, entity)

  private def _entity_to_view(
    component: Component,
    entityname: String,
    projectionName: Option[String],
    entity: Any
  ): Consequence[Any] =
    for {
      module <- Consequence.fromOption(
        _generated_view_module(component, entityname, projectionName),
        s"View module not found for ${entityname}"
      )
      record <- Consequence.fromTry(Try(_entity_to_record(component, entityname, entity)))
      view <- _invoke_create_from_record(module, record)
    } yield view

  private def _entity_to_record(
    component: Component,
    entityname: String,
    entity: Any
  ): Record = {
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    summon[EntityPersistent[Any]].toRecord(entity)
  }

  private def _entity_id(
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
        module.getClass.getMethods.iterator
          .filter(m => m.getName == "create" && m.getParameterCount == 1 && m.getParameterTypes.head == classOf[Record])
          .flatMap { m =>
            try Some(m.invoke(module, record))
            catch {
              case _: Throwable => None
            }
          }
          .toSeq
          .headOption match {
            case Some(c: Consequence[?]) => c.asInstanceOf[Consequence[Any]]
            case Some(value) => Consequence.success(value)
            case None => Consequence.failure(s"create(record) not found: ${module.getClass.getName}")
          }
    }

  private def _invoke_member_setter(
    aggregate: Any,
    module: AnyRef,
    memberName: String,
    members: Vector[Any]
  ): Consequence[Any] =
    module match {
      case m: AggregateAssembler[?] =>
        m.asInstanceOf[AggregateAssembler[Any]].attach_member(aggregate, memberName, members)
      case _ =>
        Consequence.failure(s"AggregateAssembler not found: ${module.getClass.getName}")
    }

  private def _initialize_working_sets(
    component: Component,
    entitySpace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val initializer = new WorkingSetInitializer(entitySpace)
    _default_working_sets(component).foreach { spec =>
      val entities = spec.entities.iterator.toVector
      _prime_store(entitySpace, storesnapshot, spec.entityName, entities)
      initializer.preload(spec.copy(entities = entities))
    }
  }

  private def _initialize_working_sets_from_plan(
    plans: Vector[EntityRuntimePlan[Any]],
    entitySpace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val initializer = new WorkingSetInitializer(entitySpace)
    plans.foreach { plan =>
      plan.workingSet.foreach { ws =>
        val entities = ws.entities.iterator.toVector
        _prime_store(entitySpace, storesnapshot, ws.entityName, entities)
        initializer.preload(ws.copy(entities = entities))
      }
    }
  }

  private def _prime_store[E](
    entitySpace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any],
    entityName: String,
    entities: IterableOnce[E]
  ): Unit = {
    entitySpace.entityOption[E](entityName).foreach { collection =>
      entities.foreach { entity =>
        val id = collection.descriptor.persistent.id(entity)
        storesnapshot.put(id, entity)
        collection.storage.storeRealm.put(entity)
      }
    }
  }

  private def _resolve_working_set_entity_names(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Set[String] =
    if (plans.nonEmpty)
      plans.flatMap(_.workingSet.map(_.entityName)).toSet
    else
      _default_working_sets(component).map(_.entityName).toSet

  private def _default_working_sets(
    component: Component
  ): Vector[WorkingSetDefinition[Any]] =
    Vector.empty

  private def _default_entity_runtime_plans(
    component: Component
  ): Vector[EntityRuntimePlan[Any]] = {
    val descriptors = _runtime_descriptors_for(component)
    if (descriptors.nonEmpty)
      descriptors.map(_.toPlan)
    else
      component match {
        case m: EntityRuntimePlanProvider =>
          m.entityRuntimePlans
        case _ =>
          component.factory match {
            case Some(m: EntityRuntimePlanProvider) =>
              m.entityRuntimePlans
            case _ if component.aggregateDefinitions.nonEmpty || component.viewDefinitions.nonEmpty =>
              _entity_collection_names(component).map(_legacy_memory_plan)
            case _ =>
              Vector.empty
          }
      }
  }

  private def _bootstrap_collection_id(
    component: Component,
    entityname: String
  ): EntityCollectionId =
    _runtime_descriptor_for(component, entityname)
      .map(_.collectionId)
      .orElse {
        _generated_entity_module(component, entityname)
          .flatMap(_extract_collection_id)
      }
      .getOrElse(EntityCollectionId("sys", "sys", entityname))

  private def _bootstrap_entity_persistent(
    component: Component,
    entityname: String
  ): EntityPersistent[Any] = {
    val module = _generated_entity_module(component, entityname)
    val persistent =
      module.flatMap(_extract_entity_persistent).orElse {
        _generated_entity_persistent_module(component, entityname).flatMap(_as_entity_persistent)
      }
    persistent.getOrElse(_entity_persistent_any)
  }

  private def _runtime_descriptors_for(
    component: Component
  ): Vector[EntityRuntimeDescriptor] = {
    val entities = _entity_collection_names(component).map(_.toLowerCase).toSet
    _runtime_entity_descriptors.filter { d =>
      val entity = d.entityName.trim.toLowerCase
      val collection = d.collectionId.name.trim.toLowerCase
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
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val classname = _entity_class_name(entityname)
    val candidates = packagename.toVector.flatMap { pkg =>
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

  private def _generated_entity_module(
    component: Component,
    entityname: String
  ): Option[AnyRef] = {
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val classname = _entity_class_name(entityname)
    val candidates = packagename.toVector.flatMap { pkg =>
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
    projectionName: Option[String] = None
  ): Option[AnyRef] = {
    val packagename = Option(component.getClass.getPackage).map(_.getName).filter(_.nonEmpty)
    val classname = _entity_class_name(entityname)
    val projectionToken = projectionName.map(_.trim).filter(_.nonEmpty).map(_snake_case)
    val candidates = packagename.toVector.flatMap { pkg =>
      projectionToken.toVector.flatMap { projection =>
        Vector(
          s"${pkg}.view.${projection}.${classname}$$"
        )
      } ++ Vector(
        s"${pkg}.view.${classname}$$",
        s"${pkg}.entity.view.${classname}$$"
      )
    }
    val loader = component.getClass.getClassLoader
    candidates.iterator.flatMap(name => _load_scala_module(loader, name)).toSeq.headOption
  }

  private def _generated_aggregate_module(
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

  private def _query_record(
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

  private def _sanitize_query_record(p: Record): Record = {
    val filtered = p.asMap.filterNot { case (k, _) =>
      k.startsWith("security.") ||
      k.startsWith("cncf.security.") ||
      k.startsWith("textus.") ||
      k.startsWith("cncf.")
    }
    Record.create(filtered)
  }

  private def _filter_view_query_record(
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

  private val _query_field_regex = "query\\.([A-Za-z0-9_]+)".r

  private def _matches_view_query(
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

  private def _entity_class_name(
    entityname: String
  ): String = {
    val normalized = NamingConventions.toNormalizedSegment(entityname)
    normalized
      .split("-")
      .toVector
      .filter(_.nonEmpty)
      .map(s => s.head.toUpper + s.drop(1))
      .mkString
  }

  private def _load_scala_module(
    loader: ClassLoader,
    className: String
  ): Option[AnyRef] = {
    val loaders = Vector(
      Option(loader),
      Option(Thread.currentThread.getContextClassLoader)
    ).flatten.distinct
    loaders.iterator.flatMap { cl =>
      try {
        val cls = Class.forName(className, true, cl)
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
    val fromNamedMethods =
      methods
        .filter(m => m.getParameterCount == 0 && m.getName.startsWith("given_EntityPersistent"))
        .flatMap(m => _invoke_zero_arg(module, m))
        .headOption
    val fromTypedMethods =
      methods
        .filter(m => m.getParameterCount == 0)
        .flatMap(m => _invoke_zero_arg(module, m))
        .find(x => _as_entity_persistent(x).nonEmpty)
    val fromNamedFields =
      fields
        .filter(_.getName.startsWith("given_EntityPersistent"))
        .flatMap(f => _read_field(module, f))
        .headOption
    val fromTypedFields =
      fields
        .flatMap(f => _read_field(module, f))
        .find(x => _as_entity_persistent(x).nonEmpty)
    fromNamedMethods.orElse(fromTypedMethods).orElse(fromNamedFields).orElse(fromTypedFields).flatMap(_as_entity_persistent)
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

  private def _as_entity_persistent(
    raw: Any
  ): Option[EntityPersistent[Any]] =
    raw match {
      case m: EntityPersistent[?] => Some(m.asInstanceOf[EntityPersistent[Any]])
      case m: AnyRef if _looks_like_entity_persistent(m) =>
        Some(new EntityPersistent[Any] {
          def id(e: Any): EntityId =
            _invoke_entity_persistent(m, "id", e).asInstanceOf[EntityId]

          def toRecord(e: Any): Record =
            _invoke_entity_persistent(m, "toRecord", e).asInstanceOf[Record]

          def fromRecord(r: Record): Consequence[Any] =
            _invoke_entity_persistent(m, "fromRecord", r).asInstanceOf[Consequence[Any]]
        })
      case _ => None
    }

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

  private def _legacy_memory_plan(
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
  private def _default_collection_transition_rules(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Vector[CollectionTransitionRule[Any]] =
    component match {
      case m: CollectionTransitionRuleProvider =>
        val _ = plans
        m.stateMachineTransitionRules
      case _ =>
        component.factory match {
          case Some(m: CollectionTransitionRuleProvider) =>
            val _ = plans
            m.stateMachineTransitionRules
          case _ =>
            Vector.empty
        }
    }

}

object ComponentFactory {
  // NEW
  def create(
    subsystem: Subsystem,
    collaborators: CollaboratorFactory,
    cwd: Path,
    c: ResolvedConfiguration
  ): ComponentFactory = {
    val space = _build_component_repository_space(subsystem, cwd, c)
    val descriptors = _resolve_entity_runtime_descriptors(cwd, c)
    new ComponentFactory(space, collaborators, descriptors)
  }

  private def _build_component_repository_space(
    subsystem: Subsystem,
    cwd: Path,
    c: ResolvedConfiguration
  ): ComponentRepositorySpace =
    ComponentRepositorySpace.create(subsystem, cwd, c)

  // CncfRuntime
  def create(
    subsystem: Subsystem,
    c: ResolvedConfiguration,
    collaborators: CollaboratorFactory,
    repositorySpecs: Vector[ComponentRepository.Specification]
  ): ComponentFactory = {
    val space = ComponentRepositorySpace.create(subsystem, c, repositorySpecs)
    val cwd = Paths.get("").toAbsolutePath.normalize
    val descriptors = _resolve_entity_runtime_descriptors(cwd, c)
    new ComponentFactory(space, collaborators, descriptors)
  }

  private val _component_descriptor_key = "cncf.component.descriptor"
  private val _component_descriptor_dir_key = "cncf.component.descriptor.dir"
  private val _component_descriptor_default_dir = "car.d"

  private def _resolve_entity_runtime_descriptors(
    cwd: Path,
    c: ResolvedConfiguration
  ): Vector[EntityRuntimeDescriptor] =
    _resolve_component_descriptors(cwd, c).flatMap(_.entityRuntimeDescriptors)

  private def _resolve_component_descriptors(
    cwd: Path,
    c: ResolvedConfiguration
  ): Vector[ComponentDescriptor] = {
    val explicit =
      _split_paths(ConfigurationAccess.getString(c, _component_descriptor_key)) ++
      _split_paths(ConfigurationAccess.getString(c, _component_descriptor_dir_key))
    val default = {
      val path = cwd.resolve(_component_descriptor_default_dir).normalize
      if (Files.exists(path)) Vector(path) else Vector.empty
    }
    (explicit.map(_normalize_path(cwd, _)) ++ default).distinct.flatMap { path =>
      ComponentDescriptorLoader.load(path) match {
        case Consequence.Success(xs) => xs
        case Consequence.Failure(_) => Vector.empty
      }
    }
  }

  private def _split_paths(value: Option[String]): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _normalize_path(cwd: Path, raw: String): Path = {
    val path = Paths.get(raw)
    if (path.isAbsolute) path.normalize else cwd.resolve(path).normalize
  }

  def build(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: String
  ): Consequence[Vector[ComponentSource]] = {
    val sources = Vector.newBuilder[ComponentSource]
    var error: Option[Throwable] = None
    classNames.foreach { className =>
      if (error.isEmpty) {
        try {
          val cls = Class.forName(className, false, loader).asSubclass(classOf[Component])
          sources += ComponentSource.ClassDef(cls, origin)
        } catch {
          case e: Throwable =>
            error = Some(e)
        }
      }
    }
    error match {
      case Some(e) => Consequence.failure(e)
      case None => Consequence.success(sources.result())
    }
  }
}
