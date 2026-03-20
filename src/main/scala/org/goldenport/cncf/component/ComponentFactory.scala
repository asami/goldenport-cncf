package org.goldenport.cncf.component

import java.nio.file.{Path, Paths}
import cats.effect.Ref
import cats.data.State
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.backend.collaborator.{Collaborator, CollaboratorFactory}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.component.repository.ComponentSource
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.aggregate.AggregateSpace
import org.goldenport.cncf.event.{ActionCallDispatcher, EventBus, EventReception, EntitySubscriptionLimit}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimePlan, EntitySpace, EntityStorage, PartitionedMemoryRealm, PartitionStrategy, WorkingSetDefinition, WorkingSetInitializer}
import org.goldenport.cncf.entity.view.ViewSpace
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.statemachine.{CollectionStateMachinePlanner, CollectionStateMachinePlannerProvider, CollectionTransitionRule, CollectionTransitionRuleProvider, TransitionTrigger, TransitionRule}

/*
 * @since   Jan. 30, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory(
  private val _component_repository_space: ComponentRepositorySpace = ComponentRepositorySpace(),
  private val _collaborators: CollaboratorFactory = CollaboratorFactory.empty
) {
  def discover(): Vector[Component] = {
    val cs = _component_repository_space.discover()
    val xs = cs
      .map(_initialize_special_component)
      .map(_bootstrap_collections)
    xs
  }

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
    component
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
      jobEngine = Some(component.jobEngine)
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
      collectionId = org.goldenport.cncf.datatype.EntityCollectionId("sys", "sys", name),
      plan = plan,
      persistent = _entity_persistent_any
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
      val descriptor = EntityDescriptor(
        collectionId = org.goldenport.cncf.datatype.EntityCollectionId("sys", "sys", name),
        plan = EntityRuntimePlan(
          entityName = name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 1,
          maxEntitiesPerPartition = 0
        ),
        persistent = _entity_persistent_any
      )

      val memoryRealm = new PartitionedMemoryRealm[Any](
        strategy = PartitionStrategy.byOrganizationMonthUTC,
        idOf = _entity_id_of_any
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
      component.core.protocol.services.services
        .map(_.name)
        .filterNot(_ == "meta")
        .filterNot(_ == "system")
        .toVector
    // Fallback collection when no service-derived names exist
    if (names.nonEmpty) names else Vector("default")
  }

  private def _bootstrap_aggregates(
    component: Component,
    aggregatespace: AggregateSpace,
    entityspace: EntitySpace
  ): Unit = {
    // entityspace is provided so aggregate/view builders can access entity collections
    // once metadata binding is implemented.
    // TODO: bind aggregate definitions when component metadata wiring is available.
    ()
  }

  private def _bootstrap_views(
    component: Component,
    viewspace: ViewSpace,
    entityspace: EntitySpace
  ): Unit = {
    // entityspace is provided so aggregate/view builders can access entity collections
    // once metadata binding is implemented.
    // TODO: bind view definitions when component metadata wiring is available.
    ()
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
  ): Vector[EntityRuntimePlan[Any]] =
    Vector.empty

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
        Vector.empty
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
    new ComponentFactory(space, collaborators)
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
    new ComponentFactory(space, collaborators)
  }

  def build(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: String
  ): Consequence[Vector[ComponentSource]] = ???
}
